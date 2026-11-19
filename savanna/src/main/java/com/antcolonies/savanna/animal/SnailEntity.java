package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.SeekMateGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
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
 * The river snail (M9 river life, the clean-up crew, 2026-11-16): the user's pick of four, A "Garden spiral" (model
 * {@code tools/snail_models.py game}); cute first, no realism (the user's words). All my picks:
 * <ul>
 * <li>It creeps very slowly over the bed of the savanna's shallow water, never far from it.</li>
 * <li>Hungry, it creeps to the nearest algae and rasps at it with its head down, one bite a meal ({@link SnailGrazeGoal});
 * it never starves.</li>
 * <li>A player, a crab or a crocodile coming close, or a hit, and it pulls everything into its shell and sits tight,
 * taking half the harm while in (the shell); a few seconds after the danger is gone it peeks out, eyes first, and
 * carries on ({@link SnailHideGoal}).</li>
 * <li>A hungry crab may hunt it (hiding does not save it): it grabs it, cracks the shell, eats it and leaves the broken
 * shell on the bed ({@link CrabPickGoal}).</li>
 * <li>Breeding (animal life cycle step 2, 2026-11-19; its life file): every snail is both male and female, so any two
 * ready snails creep together ({@link SeekMateGoal}), touch heads for a while, and both lay a pearly clutch at the
 * waterline ({@link SnailLayGoal}); the eggs hatch into baby snails a quarter of the size, which grow up in a few
 * days.</li>
 * </ul>
 */
public class SnailEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, GRAZE = 1, HIDDEN = 2, PEEK = 3;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(SnailEntity.class, EntityDataSerializers.BYTE);

    /** The model is made 0.45 blocks long; drawn at 0.4 (the viewer's size the user picked it at). */
    public static final float MODEL_SCALE = 0.88F;
    /** How far from the water it last stood in it may wander (blocks). */
    static final int NEAR_WATER = 3;
    /** Kept while a player is within this many blocks (as the fish are), gone past the group's 128. */
    static final double KEEP_NEAR = 96.0;
    /** How far off it is still drawn (blocks). */
    static final double DRAW_DISTANCE = 32.0;
    /** The crawl clip's ripple is made for this speed (blocks a tick); it plays faster or slower by the snail's speed. */
    static final double CRAWL_CLIP_SPEED = 0.012;

    /** Ticks until it is hungry again (0: hungry). */
    int hungryIn;
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(SnailEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    /** Eggs it is to lay after mating (0: none). */
    private int eggsToLay;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SnailEntity(EntityType<? extends SnailEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        setPathfindingMalus(PathType.WATER, 0.0F);        // the bed is its ground
        hungryIn = random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 3.0).add(Attributes.MOVEMENT_SPEED, 0.05)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new SnailHideGoal(this));
        goalSelector.addGoal(2, new SnailLayGoal(this));
        goalSelector.addGoal(2, new SnailGrazeGoal(this));
        // never stopped by Minecraft's "nothing happened for 5 s and no player within 32" rule (docs/BUGS.md 216)
        goalSelector.addGoal(3, new SeekMateGoal<>(this, 1.0));
        goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0, 160, false));
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    /** Eggs it is to lay (0: none). */
    public int eggsToLay() {
        return eggsToLay;
    }

    void laidEggs() {
        eggsToLay = 0;
    }

    /** The pair touch heads, still, face to face. */
    @Override
    public void courting(Mob mate, int ticks) {
        face(mate.getX(), mate.getZ());
        setDoing(CALM);
    }

    /** Both of the pair lay (every snail is both male and female). */
    @Override
    public void mated(Mob mate) {
        eggsToLay = life.numbers().clutchMin() + random.nextInt(life.numbers().clutchMax() - life.numbers().clutchMin() + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_DOING, CALM);
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        entityData.set(DATA_DOING, doing);
    }

    public boolean isHidden() {
        return doing() == HIDDEN;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (tickCount % 20 == 0 && isInWater()) {
            restrictTo(blockPosition(), NEAR_WATER);
        }
    }

    /** Pulled in, the shell takes half the harm. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, isHidden() ? amount * 0.5F : amount);
    }

    /** Faces a place, body and all. */
    void face(double x, double z) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.002) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        yaw = Mth.approachDegrees(getYRot(), yaw, 10.0F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Its spawn spot is water (as the crab's, BUGS 218). */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Always wanders (BUGS 216). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

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
        if (eggsToLay > 0) {
            tag.putInt("EggsToLay", eggsToLay);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        hungryIn = tag.getInt("HungryIn");
        eggsToLay = tag.getInt("EggsToLay");
        refresh();
    }

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /** Where it may spawn: as the crab, in the water over a shallow bed, set down on the bed ({@link RiverBed}, BUGS 224). */
    public static boolean canSpawn(EntityType<SnailEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return RiverBed.maySpawnAt(level, pos);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("crawl");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation HIDE = RawAnimation.begin().thenPlayAndHold("hide");
    private static final RawAnimation PEEK_CLIP = RawAnimation.begin().thenPlay("peek").thenLoop("idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<SnailEntity> state) {
        AnimationController<SnailEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        switch (doing()) {
            case HIDDEN -> {
                return state.setAndContinue(HIDE);
            }
            case PEEK -> {
                return state.setAndContinue(PEEK_CLIP);
            }
            case GRAZE -> {
                return state.setAndContinue(GRAZE_CLIP);
            }
            default -> { }
        }
        double dx = getX() - xo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed > 0.002) {
            player.setAnimationSpeed(Mth.clamp(speed / CRAWL_CLIP_SPEED, 0.5, 3.0));
            return state.setAndContinue(CRAWL);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
