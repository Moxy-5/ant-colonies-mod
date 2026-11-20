package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.registry.SavannaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The African bullfrog (M9 river life, prey plan step 3, 2026-11-20; the user's pick and picture; animals are fun first,
 * inspired by the real frog's big character): a huge round frog of the pans, oxbows and slow river edges, males 0.8
 * blocks long and females 0.65 (the user's sizes), each 0.9 to 1.1 of that.
 *
 * <p>Its day ({@link BullfrogGoals}; every number my pick unless marked):
 * <ul>
 * <li>An ambush eater: it sits still and lunges with a huge gape at small things passing within about 1.5 blocks of its
 * mouth (barbs, snails, baby crabs), then is fed a while; it never starves.</li>
 * <li>Its tongue (the user, 2026-11-20): flies of a cloud that sinks within 2 blocks of its mouth, in front of it and
 * above it along a clear line, it flicks at with a long tongue (drawn as a rope by its renderer); one flick in four
 * misses; a few flies fill it.</li>
 * <li>Bold, not shy: a player coming near makes it puff up; one who keeps coming gets a barking lunge and a bite (1
 * heart). From a monitor or a grown crocodile it leaps into the water (Minecraft's own long-jump aim, so the arc is
 * clear).</li>
 * <li>In the hottest hours of a dry day it sits half dug into the ground at the water's edge (only its back and eyes
 * out), and comes out when a danger comes near.</li>
 * <li>Males call from dusk and in rain, a deep lowing whoop (the throat swelling), and others answer.</li>
 * <li>On land it hops ({@link BullfrogMoveControl}); in the water it swims and floats at the surface.</li>
 * </ul>
 * Breeding (calling and wrestling, the tadpole school guarded by the father) is the plan's step 4.
 */
public class BullfrogEntity extends Animal implements GeoEntity {
    /** What it is doing to be seen (synced). */
    public static final byte NONE = 0, HOP = 1, LEAP = 2, LUNGE = 3, TONGUE = 4, PUFF = 5, CALL = 6, DIG_IN = 7, DUG = 8, DIG_OUT = 9;
    /** A female's length against a male's (the user's 0.65 against 0.8). */
    static final float FEMALE_SIZE = 0.65F / 0.8F;
    /** The model's mouth, against the frog's feet and middle (blocks at a male's size: the snout 0.4 ahead, 0.19 up). */
    static final double MOUTH_AHEAD = 0.4, MOUTH_UP = 0.19;
    /** How long a meal lasts (ticks; 4 to 8 minutes), and the rest after a few flies (1 to 2 minutes). */
    static final int FED_MIN = 4800, FED_MORE = 4800, FLIES_FILL = 3, FLY_REST_MIN = 1200, FLY_REST_MORE = 1200;
    /** A hop: the kick's push up and forward (blocks a tick, forward at a male's size) and the clip's tick of the kick. */
    static final double HOP_UP = 0.24, HOP_UP_STEP = 0.45, HOP_FORWARD = 0.13;
    static final int HOP_KICK = 2;
    /** The hop clip's flight (seconds, at speed 1: from 0.12 to 0.6 of its 0.8 s) and the real flight (ticks). */
    static final double HOP_CLIP_FLIGHT = 0.384, HOP_FLIGHT_TICKS = 7.0;

    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.BYTE);
    /** Counts every action started, so a clip that plays once restarts for the next (a second hop, a second flick). */
    private static final EntityDataAccessor<Integer> DATA_ACTION_ID = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MALE = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.BOOLEAN);
    /** Where its tongue reaches to (world), while it flicks. */
    private static final EntityDataAccessor<Vector3f> DATA_TONGUE_AT = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.VECTOR3);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks until it is hungry again, and flies eaten since its last rest. */
    int hungryIn;
    int fliesEaten;
    /** Ticks into the action now under way (server), and until its next hop may start. */
    int actionTicks;
    private int hopCooldown;
    private boolean hopUp;
    /** Ticks until a male calls again (night and rain only). */
    int callIn;
    // client: the action last seen (to restart its clip) and when it began
    private int seenActionId = -1;
    private int clientActionStart;

    public BullfrogEntity(EntityType<? extends BullfrogEntity> type, Level level) {
        super(type, level);
        moveControl = new BullfrogMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0.0F);
        hungryIn = 200 + level.random.nextInt(1200);
        callIn = 200 + level.random.nextInt(600);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        BullfrogGoals.register(this, goalSelector);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_ACTION_ID, 0);
        builder.define(DATA_MALE, true);
        builder.define(DATA_TONGUE_AT, new Vector3f());
    }

    // --- what it is ----------------------------------------------------------------------------------------------------

    public boolean isMale() {
        return entityData.get(DATA_MALE);
    }

    /** Makes it a male or a female of its own length (0.9 to 1.1 of its sex's usual), drawn and boxed through SCALE. */
    public void setUp(boolean male, float own) {
        entityData.set(DATA_MALE, male);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue((male ? 1.0F : FEMALE_SIZE) * own);
        }
    }

    public boolean isHungry() {
        return hungryIn <= 0;
    }

    /** For tests: hungry now. */
    public void makeHungry() {
        hungryIn = 0;
    }

    // --- what it is doing ----------------------------------------------------------------------------------------------

    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    /** Starts an action (its clip plays from the start, even the same one again). */
    void act(byte action) {
        entityData.set(DATA_ACTION, action);
        entityData.set(DATA_ACTION_ID, entityData.get(DATA_ACTION_ID) + 1);
        actionTicks = 0;
    }

    /** Back to sitting (or swimming). */
    void stopActing() {
        if (action() != NONE) {
            entityData.set(DATA_ACTION, NONE);
        }
        actionTicks = 0;
    }

    /** Busy with something its legs must not walk out of (a lunge, a flick, a call, digging; a hop in the air is the legs' own). */
    public boolean busy() {
        byte a = action();
        return a != NONE && a != HOP && a != PUFF;
    }

    public boolean isDug() {
        byte a = action();
        return a == DIG_IN || a == DUG || a == DIG_OUT;
    }

    /** Whether a new hop may start: on the ground, not busy, rested from the last. */
    boolean mayHop() {
        return onGround() && !busy() && action() != HOP && hopCooldown <= 0;
    }

    /** A hop toward where it faces (its legs ask; up a block if the way climbs). */
    void startHop(boolean up) {
        hopUp = up;
        act(HOP);
    }

    /** Where its tongue reaches to now (client: for the rope), while it flicks. */
    public Vec3 tongueAt() {
        Vector3f v = entityData.get(DATA_TONGUE_AT);
        return new Vec3(v.x(), v.y(), v.z());
    }

    void setTongueAt(Vec3 at) {
        entityData.set(DATA_TONGUE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
    }

    /** Ticks since the action now under way began, as the players' computers see it (for the tongue's rope). */
    public float clientActionAge(float partialTick) {
        return tickCount - clientActionStart + partialTick;
    }

    /** Its mouth: just in front of its snout, at its lip line (blocks, world). */
    public Vec3 mouth() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double s = getScale();
        return new Vec3(getX() - Mth.sin(yaw) * MOUTH_AHEAD * s, getY() + MOUTH_UP * s, getZ() + Mth.cos(yaw) * MOUTH_AHEAD * s);
    }

    /** A meal from a lunge: fed for 4 to 8 minutes. */
    public void ateMeal() {
        hungryIn = FED_MIN + random.nextInt(FED_MORE);
        fliesEaten = 0;
    }

    /** A fly: a few fill it for a minute or two. */
    void ateFly() {
        if (++fliesEaten >= FLIES_FILL) {
            hungryIn = FLY_REST_MIN + random.nextInt(FLY_REST_MORE);
            fliesEaten = 0;
        }
    }

    // --- its tick ------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int id = entityData.get(DATA_ACTION_ID);
            if (id != seenActionId) {
                seenActionId = id;
                clientActionStart = tickCount;
            }
            return;
        }
        filmTick();
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (callIn > 0) {
            callIn--;
        }
        if (hopCooldown > 0) {
            hopCooldown--;
        }
        actionTicks++;
        byte a = action();
        if (a == HOP) {
            hopTick();
        } else if (a == LEAP && (actionTicks > 4 && (onGround() || isInWater()) || actionTicks > 60)) {
            stopActing();
            setDiscardFriction(false);
        } else if (a == DIG_OUT && actionTicks >= BullfrogGoals.DIG_OUT_TICKS) {
            stopActing();       // out of the ground: the clip is over
        }
        floatTick();
    }

    /**
     * The film tool's hook ({@code tools/film/bullfrog_frames.film}): a frog named {@code film-act:tongue} (held still
     * with NoAI) flicks its tongue every second at a spot 1.4 blocks ahead and 1.2 up, the real action, so the clip and
     * the drawn rope are filmed together as players see them.
     */
    private void filmTick() {
        Component name = getCustomName();
        if (name != null && name.getString().equals("film-act:tongue") && (action() != TONGUE || actionTicks >= 20)) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;       // a frog held still never turns its body to its facing
            double ahead = MOUTH_AHEAD * getScale() + 1.4;
            setTongueAt(position().add(-Mth.sin(yaw) * ahead, MOUTH_UP * getScale() + 1.2, Mth.cos(yaw) * ahead));
            act(TONGUE);
        }
    }

    /** The hop: the kick at its tick, then the flight; done on landing. */
    private void hopTick() {
        if (actionTicks == HOP_KICK) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double forward = HOP_FORWARD * getScale() * (hopUp ? 0.8 : 1.0);
            setDeltaMovement(-Mth.sin(yaw) * forward, hopUp ? HOP_UP_STEP : HOP_UP, Mth.cos(yaw) * forward);
            hasImpulse = true;
        } else if (actionTicks > HOP_KICK + 2 && (onGround() || isInWater()) || actionTicks > 40) {
            stopActing();
            hopCooldown = 4 + random.nextInt(10);
        }
    }

    /** In the water, still: it floats with its eyes and back out (a frog's lungs), bobbing a little. */
    private void floatTick() {
        if (!isInWater() || action() == LEAP || !getNavigation().isDone()) {
            return;
        }
        double under = getFluidHeight(FluidTags.WATER);
        Vec3 v = getDeltaMovement();
        if (under > getBbHeight() * 0.6) {
            setDeltaMovement(v.x * 0.9, Math.max(v.y, 0.025), v.z * 0.9);
        } else if (under < getBbHeight() * 0.35) {
            setDeltaMovement(v.x * 0.9, Math.min(v.y, -0.01), v.z * 0.9);
        } else {
            setDeltaMovement(v.x * 0.85, v.y * 0.5, v.z * 0.85);
        }
    }

    /** No fall hurts it from its own leaps (it never drowns: it is in the game's can-breathe-under-water tag). */
    @Override
    protected int calculateFallDamage(float distance, float multiplier) {
        return super.calculateFallDamage(distance - 3.0F, multiplier);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getMaxHeadYRot() {
        return 20;
    }

    // --- spawning ------------------------------------------------------------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        RandomSource r = level.getRandom();
        setUp(r.nextBoolean(), 0.9F + r.nextFloat() * 0.2F);
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            // drawn anywhere in its column: set down on the top (on the bank, or afloat on the water)
            int top = surface(level, getX(), getZ());
            boolean water = level.getFluidState(BlockPos.containing(getX(), top - 1, getZ())).is(FluidTags.WATER);
            setPos(getX(), water ? top - 0.4 : top, getZ());
        }
        return out;
    }

    static int surface(LevelReader level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** How deep the water a bullfrog lives by may be (blocks: pans, edges, shallows), and how far up the bank it spawns. */
    static final int SHALLOW = 3, BANK = 2;

    /**
     * Where it may spawn by itself: at the edges of shallow water (1 to {@link #SHALLOW} deep) and on the bank within
     * {@link #BANK} blocks of it, in the river animals' band of heights. Minecraft's spawner draws one height in the
     * column: any spot in the water there or the free block over the top passes ({@link #finalizeSpawn} sets it down).
     */
    public static boolean canSpawn(EntityType<BullfrogEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + 7) {
            return false;
        }
        int top = surface(level, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (pos.getY() > top || pos.getY() < top && !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        for (int dx = -BANK; dx <= BANK; dx++) {
            for (int dz = -BANK; dz <= BANK; dz++) {
                if (FlyCloudEntity.shallowWater(level, pos.getX() + dx, pos.getZ() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The world's own rules (light, grass) do not apply: its spawn rule has said where it may be. */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    /** It may spawn in water as on land (the water animals' answer, BUGS 218): only other mobs in the way stop it. */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Within this many blocks of a player it is never removed (as the river fish). */
    static final double KEEP_NEAR = 96.0;

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR && !hasCustomName();
    }

    // --- not a farm animal ---------------------------------------------------------------------------------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    // --- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return null;        // its voice is the call (BullfrogGoals), not a random noise
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SavannaSounds.BULLFROG_BARK.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FROG_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return (isMale() ? 0.9F : 1.1F) + (random.nextFloat() - 0.5F) * 0.1F;
    }

    // --- saving --------------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Male", isMale());
        tag.putInt("HungryIn", hungryIn);
        byte a = action();
        if (a == DUG || a == DIG_IN) {
            tag.putBoolean("Dug", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_MALE, !tag.contains("Male") || tag.getBoolean("Male"));
        hungryIn = tag.getInt("HungryIn");
        if (tag.getBoolean("Dug")) {
            entityData.set(DATA_ACTION, DUG);
        }
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation HOP_CLIP = RawAnimation.begin().thenPlay("hop");
    private static final RawAnimation LEAP_CLIP = RawAnimation.begin().thenPlayAndHold("leap");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation CALL_CLIP = RawAnimation.begin().thenPlay("call");
    private static final RawAnimation PUFF_CLIP = RawAnimation.begin().thenLoop("puff");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation TONGUE_CLIP = RawAnimation.begin().thenPlay("tongue");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation DIG_IN_CLIP = RawAnimation.begin().thenPlayAndHold("dig_in");
    private static final RawAnimation DUG_CLIP = RawAnimation.begin().thenLoop("dug");
    private static final RawAnimation DIG_OUT_CLIP = RawAnimation.begin().thenPlay("dig_out");
    /** The walk clip's planted feet match this pace (blocks a tick at a male's size: 5.1 model pixels a second). */
    static final double WALK_CLIP_PACE = 0.10 / 20.0;
    /** The swim clip's kick is made for this pace (blocks a tick at a male's size; my pick). */
    static final double SWIM_CLIP_PACE = 0.05;

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();
    private int animatedActionId = -1;

    private static final RawAnimation BLINK_CLIP = RawAnimation.begin().thenPlay("blink");
    /** The blink clip's length (ticks, its 0.3 s), and the wait between blinks (ticks: 4 to 10 s; my picks). */
    static final int BLINK_TICKS = 6, BLINK_GAP_MIN = 80, BLINK_GAP_MORE = 120;
    // client: when the last blink began and the next is due (its own ticks)
    private int blinkStart = -100, blinkNext = -1;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
        controllers.add(new AnimationController<>(this, "blink", 0, this::blink));
    }

    /**
     * The blink (the polish's step 2, 2026-11-20): the lower lids rise over the eyes every 4 to 10 seconds, a layer of
     * its own over whatever the body is doing (the blink clip moves only the lids), so it blinks sitting, puffed up,
     * floating, calling or dug in alike. None for a frog being filmed in a set clip, or a dead one.
     */
    private PlayState blink(AnimationState<BullfrogEntity> state) {
        Component name = getCustomName();
        if (isDeadOrDying() || name != null && name.getString().startsWith("film")) {
            return PlayState.STOP;
        }
        if (blinkNext < 0) {
            blinkNext = tickCount + BLINK_GAP_MIN + random.nextInt(BLINK_GAP_MORE);
        } else if (tickCount >= blinkNext) {
            blinkStart = tickCount;
            blinkNext = tickCount + BLINK_GAP_MIN + random.nextInt(BLINK_GAP_MORE);
            state.getController().forceAnimationReset();
        }
        if (tickCount - blinkStart > BLINK_TICKS + 1) {
            return PlayState.STOP;
        }
        return state.setAndContinue(BLINK_CLIP);
    }

    private PlayState animate(AnimationState<BullfrogEntity> state) {
        AnimationController<BullfrogEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        Component name = getCustomName();
        if (name != null && name.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(name.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        int id = entityData.get(DATA_ACTION_ID);
        if (id != animatedActionId) {
            animatedActionId = id;
            player.forceAnimationReset();       // a new action: its clip from the start, even the same clip again
        }
        double size = Math.max(0.3, getScale());
        double dx = getX() - xo, dz = getZ() - zo, dy = getY() - yo;
        double speed = Math.sqrt(dx * dx + dz * dz + (isInWater() ? dy * dy : 0));
        switch (action()) {
            case HOP -> {
                player.setAnimationSpeed(HOP_CLIP_FLIGHT * 20.0 / HOP_FLIGHT_TICKS);
                return state.setAndContinue(HOP_CLIP);
            }
            case LEAP -> {
                return state.setAndContinue(LEAP_CLIP);
            }
            case LUNGE -> {
                return state.setAndContinue(ATTACK);
            }
            case TONGUE -> {
                return state.setAndContinue(TONGUE_CLIP);
            }
            case PUFF -> {
                return state.setAndContinue(PUFF_CLIP);
            }
            case CALL -> {
                return state.setAndContinue(CALL_CLIP);
            }
            case DIG_IN -> {
                return state.setAndContinue(DIG_IN_CLIP);
            }
            case DUG -> {
                return state.setAndContinue(DUG_CLIP);
            }
            case DIG_OUT -> {
                return state.setAndContinue(DIG_OUT_CLIP);
            }
            default -> { }
        }
        if (isInWater()) {
            if (speed > 0.01) {
                player.setAnimationSpeed(Mth.clamp(speed / (SWIM_CLIP_PACE * size), 0.5, 2.0));
                return state.setAndContinue(SWIM);
            }
            return state.setAndContinue(FLOAT);
        }
        if (speed > 0.002 && onGround()) {
            player.setAnimationSpeed(Mth.clamp(speed / (WALK_CLIP_PACE * size), 0.5, 3.0));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
