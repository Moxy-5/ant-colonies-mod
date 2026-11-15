package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nullable;

import com.antcolonies.entity.body.GroundBend;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
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
 * The Nile crocodile (M9 river life, 2026-11-15; a generalized crocodile, fun first, its model from the user's picture):
 * the river's top hunter to be. This first step is the animal itself: it swims the river with its tail, hangs at the
 * surface with its eyes and back out when it rests, and by day climbs out onto the bank to bask in the sun, propped on
 * its forelegs with its head raised and its mouth held open, then slides back into the water (and at dusk or in rain).
 * Hunting, the ambush, the death roll and nests come in later steps ({@code docs/DESIGN.md}, "The crocodile"); for now
 * it harms nothing.
 *
 * <p>About 5 blocks long (my pick, to confirm), each crocodile 0.8 to 1.1 of that, drawn and boxed through
 * {@link Attributes#SCALE}. Its box is under a block wide at every size, so its routes use 1-block squares (the
 * catfish's lesson). It breathes under water (my pick: a crocodile holding its breath for an hour is not worth a rule).
 */
public class CrocodileEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_BASKING = SynchedEntityData.defineId(CrocodileEntity.class, EntityDataSerializers.BOOLEAN);

    /** How deep in the water it hangs when resting: the water this far up its box, so the brows and back are out (blocks, at the usual size). */
    private static final double AFLOAT = 0.42;
    /**
     * How the model is drawn: 6.75 blocks long as made (snout to tail tip, 108 pixels), drawn at 10/11 of that, so a usual
     * crocodile is about 6.1 blocks long (5 until the user asked for a longer tail, 2026-11-15; the body kept its size).
     */
    public static final float MODEL_SCALE = 5.0F / 5.5F;
    /**
     * The joints along its middle line the body bends at to lie on what is under it ({@link GroundBend}; model z,
     * pixels, from {@code tools/crocodile_models.py}): the anchor at the shoulders, where the neck and the chest hang
     * from the body; forward to the snout (the neck bone); back through the hips and the five tail bones to the tip.
     */
    public static final double[] FRONT_JOINTS = {-14, -40}, REAR_JOINTS = {-14, 0, 12, 24, 36, 48, 60, 68};
    /** The bones of those pieces, in the same order: front chain, then rear chain. */
    /** Where each leg pair stands along the body (model z, pixels): the forelegs, then the hind legs (the crocodile's life step 2). */
    public static final double[] LEG_PAIRS = {-11, 7};
    /** How much each leg pair stands now (1) or is folded back along the body (0), eased; drawn only (the model sets it). */
    public final double[] legStand = {1, 1};
    public long legNanos;
    public static final String[] BENT_BONES = {"neck", "chest", "hips", "tail1", "tail2", "tail3", "tail4", "tail5"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The body lying on what is under it (worked out where it is drawn, each frame). */
    private final GroundBend bend = new GroundBend(FRONT_JOINTS, REAR_JOINTS);
    /** Its own length against the usual crocodile. */
    private float ownSize = 1.0F;
    /** Ticks until it next wants the sun (while it does not, a crocodile on land goes back to the water). */
    public int sunIn;

    public CrocodileEntity(EntityType<? extends CrocodileEntity> type, Level level) {
        super(type, level);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.55F, false);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        sunIn = level.random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new BaskGoal(this));
        goalSelector.addGoal(2, new ToWaterGoal(this));
        goalSelector.addGoal(3, new AshoreGoal(this));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 60));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BASKING, false);
    }

    /** Whether it is sunbathing: lying on the bank with its head raised and its mouth open. */
    public boolean isBasking() {
        return entityData.get(DATA_BASKING);
    }

    void setBasking(boolean basking) {
        entityData.set(DATA_BASKING, basking);
    }

    /** For tests: it wants the sun now. */
    public void wantSun() {
        sunIn = 0;
    }

    /** Whether it is a time to lie in the sun: day, dry, and it has been in the water long enough. */
    boolean sunTime() {
        return sunIn <= 0 && level().isDay() && !level().isRaining();
    }

    public float ownSize() {
        return ownSize;
    }

    /** Its body's bend over what is under it (for the model). */
    public GroundBend bend() {
        return bend;
    }

    /** How deep under the surface its feet hang when it rests afloat (blocks). */
    public double floatDepth() {
        return AFLOAT * getScale();
    }

    private void refresh() {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - ownSize) > 0.005) {
            scale.setBaseValue(ownSize);
        }
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** In water it swims where it points, weightless (as the axolotl does); on land it walks. */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), travelVector);
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
            // against a bank with room above it: up and out, as every swimming mob of Minecraft's climbs out
            if (horizontalCollision && isFree(v.x, v.y + 0.6 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.3, v.z);
            }
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (sunIn > 0 && isInWater()) {
            sunIn--;
        }
        // resting in the water it rises to hang at the surface, eyes and back out
        if (isInWater() && getNavigation().isDone()) {
            double depth = getFluidHeight(FluidTags.WATER) - AFLOAT * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, -0.02, 0.03) + v.y * 0.5, v.z);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isBasking()) {
            sunIn = 600;        // startled off the bank
        }
        return hurt;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        ownSize = 0.8F + 0.3F * level.getRandom().nextFloat();
        refresh();
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("OwnSize", ownSize);
        tag.putInt("SunIn", sunIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("OwnSize")) {
            ownSize = Mth.clamp(tag.getFloat("OwnSize"), 0.5F, 1.1F);
            refresh();
        }
        if (tag.contains("SunIn")) {
            sunIn = tag.getInt("SunIn");
        }
        if (isNoAi() && tag.getBoolean("Basking")) {     // for films and the summon command: a still crocodile basking
            setBasking(true);
        }
    }

    /** Kept near a player like the river fish ({@link TilapiaEntity#KEEP_NEAR}). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    /** Always wanders, as the river fish do (BUGS 216: Minecraft stops wandering with only a spectator near). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = 96.0 * getViewScale();
        return distanceSqr < reach * reach;
    }

    /** Where it may spawn: the river fish's waters, with a block of water under it. */
    public static boolean canSpawn(EntityType<CrocodileEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 6 && pos.getY() <= sea + 6 && level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    /** A dry place to lie: solid ground with air over it, under the open sky. */
    static boolean isBank(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolidRender(level, pos.below()) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && level.canSeeSky(pos);
    }

    /** Lies in the sun on the bank, its head raised and its mouth open, for 1 to 3 minutes (my pick). */
    static class BaskGoal extends Goal {
        private final CrocodileEntity croc;
        private int left;

        BaskGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return croc.sunTime() && croc.onGround() && !croc.isInWater() && croc.getNavigation().isDone()
                    && isBank(croc.level(), croc.blockPosition());
        }

        @Override
        public void start() {
            left = 1200 + croc.getRandom().nextInt(2400);
            croc.getNavigation().stop();
            croc.setBasking(true);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && croc.sunTime() && !croc.isInWater();
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.setBasking(false);
            if (croc.sunIn <= 0) {
                croc.sunIn = 1200 + croc.getRandom().nextInt(2400);      // back in the water 1 to 3 minutes
            }
        }
    }

    /**
     * On land with no wish for the sun (basked enough, dusk, rain, startled): walks to the nearest water and slides in.
     * It keeps at it until it is in (a route that ends short is asked for again).
     */
    static class ToWaterGoal extends Goal {
        private final CrocodileEntity croc;
        private int wait, left;
        private BlockPos water;

        ToWaterGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (croc.isInWater() || croc.sunTime() || --wait > 0) {
                return false;
            }
            wait = 40;
            Level level = croc.level();
            water = BlockPos.findClosestMatch(croc.blockPosition(), 16, 4,
                    p -> level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.below()).is(FluidTags.WATER)).orElse(null);
            if (water == null) {
                return false;
            }
            // aim two blocks past the edge where that is water too: Minecraft ends a route within a block of its aim,
            // which at the very edge is still dry land (seen in the first films: it stopped with its nose over the water)
            BlockPos from = croc.blockPosition();
            int dx = Integer.signum(water.getX() - from.getX()), dz = Integer.signum(water.getZ() - from.getZ());
            for (int far = 2; far >= 1; far--) {
                BlockPos in = water.offset(dx * far, 0, dz * far);
                if (level.getFluidState(in).is(FluidTags.WATER)) {
                    water = in;
                    break;
                }
            }
            return true;
        }

        @Override
        public void start() {
            left = 600;
            croc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !croc.isInWater() && left > 0;
        }

        @Override
        public void tick() {
            left--;
            if (croc.getNavigation().isDone() && left % 20 == 0) {
                croc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.getNavigation().stop();
        }
    }

    /** In the water and wanting the sun: swims to a dry spot of bank near it and climbs out. */
    static class AshoreGoal extends Goal {
        private final CrocodileEntity croc;
        private int wait;

        AshoreGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!croc.isInWater() || !croc.sunTime() || --wait > 0) {
                return false;
            }
            wait = 100;
            Level level = croc.level();
            Optional<BlockPos> bank = BlockPos.findClosestMatch(croc.blockPosition(), 12, 3, p -> isBank(level, p)
                    && !level.getFluidState(p.below()).is(FluidTags.WATER) && waterBeside(level, p, 3) && !waterBeside(level, p, 1));
            return bank.isPresent() && croc.getNavigation().moveTo(bank.get().getX() + 0.5, bank.get().getY(), bank.get().getZ() + 0.5, 1.0);
        }

        /** Whether there is water within this many blocks at the spot's level or one below. */
        private static boolean waterBeside(Level level, BlockPos pos, int reach) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-reach, -1, -reach), pos.offset(reach, 0, reach))) {
                if (level.getFluidState(p).is(FluidTags.WATER)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return croc.sunTime() && !croc.getNavigation().isDone();
        }
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation BASK = RawAnimation.begin().thenLoop("bask");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The speeds the walk and swim clips are made for (blocks a tick); they play faster or slower by the real speed. */
    static final double WALK_CLIP_SPEED = 0.06, SWIM_CLIP_SPEED = 0.08;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 8, this::animate));
    }

    private PlayState animate(AnimationState<CrocodileEntity> state) {
        AnimationController<CrocodileEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (isBasking()) {
            return state.setAndContinue(BASK);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        if (isInWater()) {
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < 0.012) {
                return state.setAndContinue(FLOAT);
            }
            player.setAnimationSpeed(Mth.clamp(speed / SWIM_CLIP_SPEED, 0.5, 2.5));
            return state.setAndContinue(SWIM);
        }
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < 0.005) {
            return state.setAndContinue(IDLE);
        }
        player.setAnimationSpeed(Mth.clamp(speed / WALK_CLIP_SPEED, 0.5, 2.5));
        return state.setAndContinue(WALK);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
