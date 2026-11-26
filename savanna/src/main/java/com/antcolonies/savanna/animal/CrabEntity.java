package com.antcolonies.savanna.animal;

import com.antcolonies.entity.body.SteadyController;
import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.ClutchClock;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.animal.SeekMateGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The river crab (M9 river life, the clean-up crew, 2026-11-15; the user's picture, model {@code tools/crab_models.py
 * game}). Generalized and fun first (the animals' rule). First step, all my picks:
 * <ul>
 * <li>It walks sideways over the river bed and out onto the banks (never far from the water), breathing in both.</li>
 * <li>Hungry, it walks to remains, a scraps film or algae near it, in that order, and picks at it: each claw in turn
 * pinches a bit off and carries it to its mouth, which opens ({@link CrabPickGoal}). It never starves.</li>
 * <li>It scuttles away from a player or a crocodile that comes near; with a player right on top of it, or hit, it rears
 * up with its claws raised and pinches (half a heart; {@link CrabThreatGoal}).</li>
 * <li>Now and then it hunts a river snail near it instead (2026-11-16, the user: "Yes, crabs eat snails"): it grabs the
 * snail, its claws squeeze the shell till it cracks, it picks the snail out into its mouth and leaves the broken shell
 * on the bed ({@link CrabPickGoal}, {@link com.antcolonies.savanna.block.CrabShellBlock}).</li>
 * </ul>
 * <li>Breeding (animal life cycle step 3, 2026-11-19; its life file; the looks the user confirmed in the life viewer):
 * males have big claws (the right one much bigger) and a narrow belly flap, females small claws and a broad flap. Two
 * ready crabs of the two sexes walk together ({@link SeekMateGoal}) and court face to face for a while, he holding her
 * with his claws round the front of her shell, she still with hers folded in ({@link #courting}). Then she carries an
 * orange egg mass under her flap for the hatching days (the kit's carrying care, {@code carry_eggs}), and lets out 4 to
 * 8 tiny crabs that ride on her back a few seconds and scatter (no larvae, as real freshwater crabs). They grow up in a
 * few days.</li>
 * Not yet: its burrow (broken shells to gather outside it; a female brooding in it), catfish eating it.
 */
public class CrabEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips and the look. */
    public static final byte CALM = 0, PICK_ALGAE = 1, PICK_MEAT = 2, THREAT = 3, CRACK = 4, COURT = 5;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(CrabEntity.class, EntityDataSerializers.BYTE);
    /** A female carrying eggs under her flap (drawn with the egg mass). */
    private static final EntityDataAccessor<Boolean> DATA_BERRIED = SynchedEntityData.defineId(CrabEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * How far apart a courting pair stand, middle to middle, at size 1 (blocks): his claws' pinches then close just past
     * her folded claws, round the front of her shell ({@code crab_models.py} court and court_female; my pick), and their
     * boxes (0.6 across) do not touch, so they are not shoved apart.
     */
    static final double COURT_APART = 0.65;
    /** How long a hatched baby rides on its mother's back before it scatters (ticks; 3 to 5 s, my pick). */
    static final int RIDE_TICKS = 60, RIDE_MORE = 40;
    /** The most babies on her back at once. */
    static final int MOST_RIDERS = 8;

    /** The model is made 0.82 blocks wide with its legs; drawn as made. */
    public static final float MODEL_SCALE = 1.0F;
    /** How far from the water it last stood in it may wander (blocks). */
    static final int NEAR_WATER = 8;
    /** Kept while a player is within this many blocks (as the fish are), gone past the group's 128. */
    static final double KEEP_NEAR = 96.0;
    /** How far off it is still drawn (blocks; Minecraft's own rule, by the box's size, gave about 20). */
    static final double DRAW_DISTANCE = 48.0;
    /** The walk clip's stride is made for this speed (blocks a tick); it plays faster or slower by the crab's speed. */
    static final double WALK_CLIP_SPEED = 0.06;

    /** Ticks until it is hungry again (0: hungry). */
    int hungryIn;
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(CrabEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    /** A female's eggs under her flap, and when she got them (game time). */
    private int eggs;
    private long eggsSince;
    /** A baby riding its mother: ticks until it scatters. */
    private int rideLeft;
    /** The body's turn against its heading (degrees): 90 to one side while it walks or stands, 0 facing what it picks at or threatens. Drawing only. */
    private float sideTurn, sideTurnO;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CrabEntity(EntityType<? extends CrabEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        setPathfindingMalus(PathType.WATER, 0.0F);        // the bed is its ground: it walks under water as on land
        hungryIn = random.nextInt(600);
        sideTurn = sideTurnO = side() * 90.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CrabThreatGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, CrocodileEntity.class, 6.0F, 1.2, 1.5));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, MonitorEntity.class, 5.0F, 1.2, 1.5));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.2, 1.5, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
        goalSelector.addGoal(4, new CrabPickGoal(this));
        // never stopped by Minecraft's "nothing happened for 5 s and no player within 32" rule (docs/BUGS.md 216)
        // above its strolls (a stroll of the same rank would block it, and it looks only once in 2 s), below eating
        goalSelector.addGoal(4, new SeekMateGoal<>(this, 0.6));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 100, false));
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_DOING, CALM);
        builder.define(DATA_BERRIED, false);
    }

    /** Whether she carries eggs under her flap (on the client as the server told it). */
    public boolean isBerried() {
        return entityData.get(DATA_BERRIED);
    }

    /** Eggs under her flap (server). */
    public int eggsUnderFlap() {
        return eggs;
    }

    /** For tests: her eggs have been under her flap this many ticks already. */
    public void ageEggs(long ticks) {
        eggsSince = level().getGameTime() - ticks;
    }

    /** Whether it is a baby riding on its mother's back. */
    public boolean ridesMother() {
        return getVehicle() instanceof CrabEntity && rideLeft > 0;
    }

    /**
     * The pair court face to face, still (animal life cycle step 3): each faces the other and plays its part of the
     * court clip (he holds out his claws round her, she folds hers in), and he steps in or back to stand
     * {@link #COURT_APART} from her, walking backwards or forwards as a crab can (a small push, never turning away).
     */
    @Override
    public void courting(Mob mate, int ticks) {
        face(mate.getX(), mate.getZ());
        setDoing(COURT);
        if (!life.isMale()) {
            return;
        }
        double apart = COURT_APART * (getScale() + mate.getScale()) / 2;
        double dx = getX() - mate.getX(), dz = getZ() - mate.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 0.01 && Math.abs(d - apart) > 0.04) {
            double push = Mth.clamp((apart - d) * 0.15, -0.04, 0.04) / d;
            setDeltaMovement(getDeltaMovement().add(dx * push, 0, dz * push));
        }
    }

    /** She takes the eggs under her flap (her clutch from the life file); he goes on his way. */
    @Override
    public void mated(Mob mate) {
        setDoing(CALM);
        if (life.isMale()) {
            return;
        }
        eggs = life.numbers().clutchMin() + random.nextInt(life.numbers().clutchMax() - life.numbers().clutchMin() + 1);
        eggsSince = level().getGameTime();
        entityData.set(DATA_BERRIED, true);
    }

    /**
     * A mother's eggs (server, once a second): after the hatching days, with room for crabs here, they hatch into babies
     * on her back ({@link #letOutBabies}); with no room for the kit's 10 days they are lost (the kit's clutch clock; she
     * is near a player by being in the world at all, so she always counts as watched).
     */
    private void eggsTick() {
        if (eggs <= 0 || tickCount % 20 != 0) {
            return;
        }
        long hatch = life.numbers().hatchTicks();
        // (the count of crabs round her only once the eggs are due: it was asked every second of their 2 days, review R10-18)
        boolean room = level().getGameTime() - eggsSince >= hatch && RoomToBreed.here(this, life.numbers().localLimit());
        switch (ClutchClock.decide(level().getGameTime(), eggsSince, hatch, true, room)) {
            case HATCH -> letOutBabies();
            case SHELLS -> {
                eggs = 0;
                entityData.set(DATA_BERRIED, false);
            }
            case WAIT -> {
            }
        }
    }

    /** Her eggs hatch: each becomes a tiny crab riding on her back for a few seconds. */
    private void letOutBabies() {
        // only as many as the local limit has room for; the rest fail (code review 2026-11-21)
        int count = level() instanceof net.minecraft.server.level.ServerLevel server
                ? Math.min(eggs, RoomToBreed.left(server, this, life.numbers().localLimit())) : 0;
        eggs = 0;
        entityData.set(DATA_BERRIED, false);
        for (int i = 0; i < count; i++) {
            CrabEntity baby = com.antcolonies.savanna.registry.SavannaEntities.CRAB.get().create(level());
            if (baby == null) {
                break;
            }
            baby.moveTo(getX(), getY() + getBbHeight(), getZ(), random.nextFloat() * 360, 0);
            baby.life.setUp(true, random);
            baby.life.setAge(0);
            baby.rideLeft = RIDE_TICKS + random.nextInt(RIDE_MORE);
            if (level().addFreshEntity(baby) && !baby.startRiding(this, true)) {
                baby.rideLeft = 0;
            }
        }
    }

    @Override
    protected boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return passenger instanceof CrabEntity baby && baby.life.isYoung() && getPassengers().size() < MOST_RIDERS;
    }

    /** Babies on her back never steer her. */
    @Override
    @Nullable
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return null;
    }

    /** Her babies keep their hold in the water (she walks on the bed). */
    @Override
    public boolean dismountsUnderwater() {
        return false;
    }

    /** Her babies sit round the top of her shell, spaced evenly, turning with her. */
    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, MoveFunction move) {
        java.util.List<net.minecraft.world.entity.Entity> riders = getPassengers();
        int i = Math.max(0, riders.indexOf(passenger));
        double a = Math.toRadians(yBodyRot) + i * Math.PI * 2 / Math.max(1, riders.size()) + 0.4;
        double r = 0.13 * getScale();
        move.accept(passenger, getX() + Math.cos(a) * r, getY() + 0.27 * getScale(), getZ() + Math.sin(a) * r);
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        entityData.set(DATA_DOING, doing);
    }

    /** Which side leads when it walks sideways: its own, for good (1 or -1). */
    private int side() {
        return (getId() & 1) == 0 ? 1 : -1;
    }

    /** The body's turn against its heading (degrees), for the drawing. */
    public float sideTurn(float partialTick) {
        return Mth.lerp(partialTick, sideTurnO, sideTurn);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            sideTurnO = sideTurn;
            sideTurn = Mth.approach(sideTurn, doing() == CALM ? side() * 90.0F : 0.0F, 15.0F);
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (doing() == COURT && life.partner() == null) {
            setDoing(CALM);            // the courtship is over (mated or broken off)
        }
        eggsTick();
        if (rideLeft > 0 && --rideLeft == 0 && isPassenger()) {
            stopRiding();              // off her back and away
            float a = random.nextFloat() * Mth.TWO_PI;
            setDeltaMovement(Mth.cos(a) * 0.12, 0.15, Mth.sin(a) * 0.12);
        }
        if (tickCount % 20 == 0 && isInWater()) {
            restrictTo(blockPosition(), NEAR_WATER);      // its strolls and flights stay near the water it was last in
        }
    }

    /** Faces a place at once, body and all (it has no head to turn). */
    void face(double x, double z) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        yaw = Mth.approachDegrees(getYRot(), yaw, 25.0F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /**
     * Its spawn spot is water (as the crocodile's, BUGS 218): only blocks and other mobs stand in its way. A land mob's
     * own check refuses any spot with water in its box, so the world would never spawn a crab on the bed.
     */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Always wanders, as the river fish do (BUGS 216: Minecraft stops wandering with only a spectator near). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** It breathes in water as on land (NeoForge's socket; Minecraft's own breathing check cannot be overridden). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        RiverBed.settle(this, level, spawnType);
        life.setUp(false, level.getRandom());
        refresh();
        return out;
    }

    private void refresh() {
        life.apply();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
        if (eggs > 0) {
            tag.putInt("Eggs", eggs);
            tag.putLong("EggsSince", eggsSince);
        }
        if (rideLeft > 0) {
            tag.putInt("RideLeft", rideLeft);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        hungryIn = tag.getInt("HungryIn");
        eggs = tag.getInt("Eggs");
        eggsSince = tag.getLong("EggsSince");
        rideLeft = tag.getInt("RideLeft");
        entityData.set(DATA_BERRIED, eggs > 0);
        refresh();
    }

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /**
     * Where it may spawn: in the water over a shallow bed of the savanna's rivers, pans and oxbows, at most 4 blocks of
     * water over the bed (the shallows by the banks), in the fish's height band; the world's own spawns are set down on the
     * bed ({@link RiverBed}, BUGS 224).
     */
    public static boolean canSpawn(EntityType<CrabEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return RiverBed.maySpawnAt(level, pos);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation PICK = RawAnimation.begin().thenLoop("pick");
    private static final RawAnimation THREAT_CLIP = RawAnimation.begin().thenLoop("threat");
    private static final RawAnimation CRACK_CLIP = RawAnimation.begin().thenPlayAndHold("crack");
    private static final RawAnimation COURT_CLIP = RawAnimation.begin().thenLoop("court");
    private static final RawAnimation COURT_FEMALE_CLIP = RawAnimation.begin().thenLoop("court_female");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<CrabEntity> state) {
        AnimationController<CrabEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        byte doing = doing();
        if (doing == THREAT) {
            return state.setAndContinue(THREAT_CLIP);
        }
        if (doing == CRACK) {
            return state.setAndContinue(CRACK_CLIP);
        }
        if (doing == COURT) {
            return state.setAndContinue(life.isMale() ? COURT_CLIP : COURT_FEMALE_CLIP);
        }
        if (doing == PICK_ALGAE || doing == PICK_MEAT) {
            return state.setAndContinue(PICK);
        }
        double dx = getX() - xo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed > 0.004) {
            player.setAnimationSpeed(Mth.clamp(speed / WALK_CLIP_SPEED, 0.5, 2.5));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
