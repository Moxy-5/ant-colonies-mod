package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nullable;

import com.antcolonies.entity.body.GroundBend;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.player.Player;
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
    /** How deep it sinks while startled in the water, out of sight (blocks, at the usual size; my pick). */
    private static final double HIDE_DEPTH = 1.4;
    /** How long a fright lasts (ticks; my pick, 5 s). */
    static final int STARTLED_TICKS = 100;
    /** How near a player may come before it flees, and one creeping up crouched (blocks; my picks). */
    static final double WARY_REACH = 6.0, SNEAK_REACH = 2.5;
    /** How often, wanting the sun, it rests half out on the bank in place of climbing out (my pick). */
    static final float HALF_OUT_SHARE = 0.4F;
    /** The rush to the water and the lunge in ({@link PlungeGoal}; my picks: about 3 blocks through the air). */
    static final double RUSH = 1.9, LUNGE_FORWARD = 0.45, LUNGE_UP = 0.2;
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
    /** Ticks it is still startled for: on land it rushes for the water and lunges in, in the water it sinks out of sight. */
    public int startled;
    /** Whether this time it wants the sun it rests half out, head and chest on the bank and its back half afloat, in place of climbing out. */
    boolean restHalfOut;

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
        goalSelector.addGoal(0, new PlungeGoal(this));
        goalSelector.addGoal(1, new BaskGoal(this));
        goalSelector.addGoal(2, new ToWaterGoal(this));
        goalSelector.addGoal(3, new HalfOutGoal(this));
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
        if (sunIn > 0 && isInWater() && --sunIn == 0) {
            restHalfOut = random.nextFloat() < HALF_OUT_SHARE;
        }
        if (startled > 0) {
            startled--;
        } else if (tickCount % 10 == 0 && (!isInWater() || isBasking()) && playerTooNear()) {
            startle();
        }
        // resting in the water it rises to hang at the surface, eyes and back out (startled, it sinks out of sight)
        if (isInWater() && getNavigation().isDone()) {
            double depth = getFluidHeight(FluidTags.WATER) - (startled > 0 ? HIDE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, -0.02, 0.03) + v.y * 0.5, v.z);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && (!isInWater() || isBasking())) {
            startle();
        }
        return hurt;
    }

    /**
     * Startled (hit, or a player too near; the crocodile's life step 3): on the bank it rushes for the water and lunges
     * in ({@link PlungeGoal}); resting half out it slips back; in the water it sinks out of sight a few seconds; and it
     * keeps to the water at least half a minute.
     */
    public void startle() {
        startled = STARTLED_TICKS;
        sunIn = Math.max(sunIn, 600 + random.nextInt(600));
        setBasking(false);          // the bask ends at once, mouth shut, not a tick later when its goal stops
    }

    /** Whether a player (not a spectator) is within reach: 6 blocks, or 2.5 for one creeping up crouched (my picks). */
    private boolean playerTooNear() {
        for (Player player : level().players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double reach = player.isCrouching() ? SNEAK_REACH : WARY_REACH;
            if (player.distanceToSqr(this) < reach * reach) {
                return true;
            }
        }
        return false;
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
        tag.putBoolean("HalfOut", restHalfOut);
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
        restHalfOut = tag.getBoolean("HalfOut");
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
            water = waterAim(croc, 16);
            return water != null;
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

    /**
     * The nearest water to slide into, aimed two blocks past the edge where that is water too: Minecraft ends a route
     * within a block of its aim, which at the very edge is still dry land (seen in the first films: it stopped with its
     * nose over the water). Null if none within reach.
     */
    @Nullable
    static BlockPos waterAim(CrocodileEntity croc, int reach) {
        Level level = croc.level();
        BlockPos water = BlockPos.findClosestMatch(croc.blockPosition(), reach, 4,
                p -> level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.below()).is(FluidTags.WATER)).orElse(null);
        if (water == null) {
            return null;
        }
        BlockPos from = croc.blockPosition();
        int dx = Integer.signum(water.getX() - from.getX()), dz = Integer.signum(water.getZ() - from.getZ());
        for (int far = 2; far >= 1; far--) {
            BlockPos in = water.offset(dx * far, 0, dz * far);
            if (level.getFluidState(in).is(FluidTags.WATER)) {
                return in;
            }
        }
        return water;
    }

    /**
     * Startled on land (the crocodile's life step 3, 2026-11-15): it rushes for the nearest water at nearly twice its
     * walk and, a stride from the edge, lunges in belly first with a push forward and a little up. The body's bend tips
     * the nose down into the water as it goes over the edge.
     */
    static class PlungeGoal extends Goal {
        private final CrocodileEntity croc;
        private BlockPos water;
        private int left;
        /** Ticks since the lunge, 0 before it; a lunge that lands back on the bank lets it try again. */
        private int sinceLunge;

        PlungeGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (croc.startled <= 0 || croc.isInWater()) {
                return false;
            }
            water = waterAim(croc, 12);
            return water != null;
        }

        @Override
        public void start() {
            left = 200;
            sinceLunge = 0;
            croc.setBasking(false);
            croc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, RUSH);
        }

        @Override
        public boolean canContinueToUse() {
            return !croc.isInWater() && left > 0;
        }

        @Override
        public void tick() {
            left--;
            if (sinceLunge > 0 && (++sinceLunge < 6 || !croc.onGround())) {
                return;         // in the air (a lunge that landed short, still on the bank, tries again)
            }
            sinceLunge = 0;
            // toward the water it is making for, not where its body points: lying along the edge, its nose may
            // only graze the water at a slant, and a lunge that way lands it back on the bank (seen in the test)
            double tx = water.getX() + 0.5 - croc.getX(), tz = water.getZ() + 0.5 - croc.getZ();
            double len = Math.sqrt(tx * tx + tz * tz);
            if (len < 1.0E-3) {
                return;
            }
            double fx = tx / len, fz = tz / len;
            if (croc.onGround() && waterAhead(croc, fx, fz)) {
                float face = (float) (Mth.atan2(fz, fx) * Mth.RAD_TO_DEG) - 90.0F;
                croc.setYRot(face);
                croc.yBodyRot = face;
                croc.yHeadRot = face;
                croc.getNavigation().stop();
                croc.setDeltaMovement(fx * LUNGE_FORWARD, LUNGE_UP, fz * LUNGE_FORWARD);
                croc.hasImpulse = true;
                sinceLunge = 1;
            } else if (croc.getNavigation().isDone() && left % 10 == 0) {
                croc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, RUSH);
            }
        }

        /** Whether the water's edge is within a stride in front of it (water at its feet's level, or open water a block under). */
        private static boolean waterAhead(CrocodileEntity croc, double fx, double fz) {
            Level level = croc.level();
            for (double a = 0.6; a <= 1.61; a += 0.5) {
                BlockPos p = BlockPos.containing(croc.getX() + fx * a, croc.getY() + 0.1, croc.getZ() + fz * a);
                if (level.getFluidState(p).is(FluidTags.WATER) || level.getFluidState(p.below()).is(FluidTags.WATER)
                        && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                    return true;
                }
            }
            return false;
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

    /**
     * In the water and wanting the sun, sometimes ({@link #HALF_OUT_SHARE}): it swims up to the bank and rests with its
     * head and chest on it, the forelegs planted and its back half afloat, mouth open, 30 to 90 seconds (my picks), as
     * real crocodiles often lie at the water's edge. The pose comes from the body lying on what is under it and the legs
     * standing where they find a footing; this goal only brings it to the edge, nose to the bank, and holds it there.
     * With no fitting edge near, it climbs out as usual.
     */
    static class HalfOutGoal extends Goal {
        private final CrocodileEntity croc;
        private Vec3 rest;
        private float face;
        private int wait, left;
        private boolean arrived;

        HalfOutGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!croc.isInWater() || !croc.sunTime() || !croc.restHalfOut || croc.startled > 0 || --wait > 0) {
                return false;
            }
            wait = 100;
            if (!findEdge()) {
                croc.restHalfOut = false;       // no edge to lie at here: it climbs out as usual
                return false;
            }
            return true;
        }

        /**
         * The nearest edge: a surface water cell with water under it and two more water cells behind it (room for the body
         * and tail), with dry bank right in front of it at the water's level or a block above. It rests with its box just
         * clear of the bank, so its shoulders and forelegs reach over it.
         */
        private boolean findEdge() {
            Level level = croc.level();
            // searched round the surface over it, not round itself: sunk out of sight after a fright, or on the bottom
            // of a deep river, a search round its own depth never reached the surface and found no edge (seen in the test)
            BlockPos here = croc.blockPosition();
            BlockPos at = here;
            while (level.getFluidState(at.above()).is(FluidTags.WATER) && at.getY() - here.getY() < 16) {
                at = at.above();
            }
            double best = Double.MAX_VALUE;
            rest = null;
            for (BlockPos w : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 2, 12))) {
                if (!level.getFluidState(w).is(FluidTags.WATER) || level.getFluidState(w.above()).is(FluidTags.WATER)
                        || !level.getFluidState(w.below()).is(FluidTags.WATER)) {
                    continue;
                }
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    if (!level.getFluidState(w.relative(dir.getOpposite())).is(FluidTags.WATER)
                            || !level.getFluidState(w.relative(dir.getOpposite(), 2)).is(FluidTags.WATER)) {
                        continue;
                    }
                    BlockPos bank = w.above().relative(dir);
                    if (!isBank(level, bank) && !isBank(level, bank.above())) {
                        continue;
                    }
                    double d = w.distSqr(at);
                    if (d < best) {
                        best = d;
                        double ahead = 0.5 - 0.45 * croc.getScale() - 0.1;
                        rest = new Vec3(w.getX() + 0.5 + dir.getStepX() * ahead, w.getY(), w.getZ() + 0.5 + dir.getStepZ() * ahead);
                        face = dir.toYRot();
                    }
                }
            }
            return rest != null;
        }

        @Override
        public void start() {
            arrived = false;
            left = 400;
            croc.getNavigation().moveTo(rest.x, rest.y, rest.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && croc.isInWater() && croc.sunTime() && croc.startled <= 0;
        }

        @Override
        public void tick() {
            left--;
            double dx = rest.x - croc.getX(), dz = rest.z - croc.getZ();
            double far = Math.sqrt(dx * dx + dz * dz);
            if (!arrived) {
                if (far < 0.4 || croc.getNavigation().isDone() && far < 1.5) {
                    arrived = true;
                    croc.getNavigation().stop();
                    left = 600 + croc.getRandom().nextInt(1200);
                    croc.setBasking(true);
                } else if (croc.getNavigation().isDone() && left % 20 == 0) {
                    croc.getNavigation().moveTo(rest.x, rest.y, rest.z, 1.0);
                }
                return;
            }
            // held there nose to the bank, drifting gently onto its spot
            croc.setYRot(face);
            croc.yBodyRot = face;
            croc.yHeadRot = face;
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(dx * 0.1, v.y, dz * 0.1);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.getNavigation().stop();
            croc.restHalfOut = false;
            if (arrived) {
                croc.setBasking(false);
                if (croc.startled > 0) {            // slips back off the bank into the water
                    float yaw = face * Mth.DEG_TO_RAD;
                    croc.setDeltaMovement(Mth.sin(yaw) * 0.3, -0.1, -Mth.cos(yaw) * 0.3);
                } else if (croc.sunIn <= 0) {
                    croc.sunIn = 1200 + croc.getRandom().nextInt(2400);      // in the water 1 to 3 minutes
                }
            }
        }
    }

    /** For tests and films: this time it wants the sun it rests half out on the bank. */
    public void wantSunHalfOut() {
        sunIn = 0;
        restHalfOut = true;
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
            if (!croc.isInWater() || !croc.sunTime() || croc.restHalfOut || --wait > 0) {
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
    /** Basking on land: first the flop from its walk onto its belly, then the bask (the crocodile's life step 3). */
    private static final RawAnimation FLOP_BASK = RawAnimation.begin().thenPlay("flop").thenLoop("bask");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** Drawn only: when it last walked on land, and whether the bask now drawn began with the flop. */
    private int walkedAt = -100;
    private boolean drawnBasking, flopNow;
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
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        if (isBasking()) {
            if (!drawnBasking) {        // it flops down only if it walked there (not lying down where it already lay, nor afloat)
                drawnBasking = true;
                flopNow = !isInWater() && tickCount - walkedAt < 20;
            }
            return state.setAndContinue(flopNow ? FLOP_BASK : BASK);
        }
        drawnBasking = false;
        if (!isInWater() && dx * dx + dz * dz > 0.02 * 0.02) {
            walkedAt = tickCount;
        }
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
