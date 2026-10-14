package com.antcolonies.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.AntMoveControl;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.debug.Perf;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.Doorway;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Slope;
import com.antcolonies.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared base for every colony insect: never despawns, doesn't push, plays idle/walk animations. */
public abstract class AbstractAntEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    /** The look round with the head out of a chimney's mouth (termites; see DoorwayMotion). */
    private static final RawAnimation PEEK = RawAnimation.begin().thenPlay("peek");
    /** How far (blocks a tick) a worker climbs at its normal speed: its legs then step at 1.5 times the walk animation. */
    private static final double CLIMB_STEP = 0.05;

    /**
     * The surface the body is on (a {@link Direction} 3D id in the low 3 bits: 0 = DOWN = a floor, 1 = UP = a ceiling,
     * 2 to 5 = the wall on that side; on a ceiling the high bits hold the wall its heading is measured from), and where
     * the head points (degrees: on a wall 0 up, 180 down, +90 to its left; on a ceiling see
     * {@link AntNavigator#getCeilingRef}). Synced so the renderer can lay the body on the surface (see
     * {@link #getBodyTilt} and the other body angles).
     */
    private static final EntityDataAccessor<Byte> DATA_SURFACE =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEADING =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.FLOAT);
    /** How fast the drawn body turns (degrees per tick) onto a new surface, round a corner or to a new heading. */
    private static final float BODY_TURN_SPEED = 30.0F;
    /** How fast (degrees a tick) the drawn body tilts to the slope it walks on. */
    private static final float SLOPE_TILT_SPEED = 9.0F;
    /**
     * What the body is doing besides holding its surface, for the client: bit 0 = looking round with its head out of a
     * doorway's mouth (plays the "peek" animation), bit 1 = tipping over to its new surface slowly (over a mouth's rim),
     * bit 2 = inside a channel (a termite tunnel: drawn in the channel's middle, not pressed against a wall).
     */
    private static final EntityDataAccessor<Byte> DATA_POSE_FLAGS =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    /** How pale a young worker is drawn, 0 (grown dark) to 100 (just grown up). Synced for the renderer. */
    private static final EntityDataAccessor<Byte> DATA_YOUTH =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    /** {@link #grownUpAt} before it is known (a worker from a spawn egg or an older save: its colony gives it an age). */
    public static final long AGE_NOT_SET = Long.MIN_VALUE;
    private static final int PEEKING = 1;
    private static final int TURNING_SLOWLY = 2;
    private static final int IN_CHANNEL = 4;

    /** The soil pellet held in the mandibles (empty = carrying nothing). Synced so the renderer can draw it. */
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRIED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

    /** The seed held in the mandibles (empty = none). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_SEED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.ITEM_STACK);

    /** True while the ant holds an egg in its mandibles (carrying it to the nursery). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_EGG =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    /** True while the ant holds a dead nestmate in its mandibles (carrying it to the refuse chamber). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_CORPSE =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final AntNavigator navigator = new AntNavigator(this);

    /** Which colony this ant belongs to (null = none). Saved with the ant. */
    @Nullable
    private UUID colonyId;
    /** The game time this worker grew up (came out of its egg or nymph stage); its age decides its usual job. Saved. */
    private long grownUpAt = AGE_NOT_SET;

    private boolean seedForBread;
    /** How many seconds in a row the ant has had nothing to do. Server side only, not saved. */
    private int idleSeconds;
    /** Whether the navigator set the motion last tick (so the leftover ladder push can be cleared when it lets go). */
    private boolean wasAttached;

    // Client only: how the body is drawn, turned smoothly toward the synced surface and heading (see AntRenderer).
    // While on a wall (or tipping onto or off one) the body is drawn by these three angles instead of the body yaw:
    // the yaw of the wall it faces, the tilt (0 = lying on the floor, 90 = upright on the wall) and the heading within it.
    private boolean wallPose;
    private float bodyTilt;
    private float bodyTiltO;
    /** Client: how the drawn body tilts on a slope (a ramp): head up (pitch) and right side up (roll), in degrees. */
    private float slopePitch;
    private float slopePitchO;
    private float slopeRoll;
    private float slopeRollO;
    private float wallYaw;
    private float wallYawO;
    private float bodyHeading;
    private float bodyHeadingO;
    /** The way the body faced when it last lay on a floor (drawn by its body yaw), to tip up onto a wall from. */
    private float floorYaw;

    protected AbstractAntEntity(EntityType<? extends AbstractAntEntity> type, Level level) {
        super(type, level);
        this.moveControl = new AntMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SURFACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(DATA_HEADING, 0.0F);
        builder.define(DATA_POSE_FLAGS, (byte) 0);
        builder.define(DATA_YOUTH, (byte) 0);
        builder.define(DATA_CARRIED, Optional.empty());
        builder.define(DATA_CARRIED_SEED, ItemStack.EMPTY);
        builder.define(DATA_CARRIED_EGG, false);
        builder.define(DATA_CARRIED_CORPSE, false);
    }

    /** The block type of the pellet this ant carries, or null if its mandibles are empty. */
    @Nullable
    public BlockState getCarriedBlock() {
        return entityData.get(DATA_CARRIED).orElse(null);
    }

    /** Whether this ant can carry soil out and pack it into place. A queen can only dig: the soil stays where it falls. */
    public boolean canPlaceBlocks() {
        return true;
    }

    public boolean isCarrying() {
        return entityData.get(DATA_CARRIED).isPresent();
    }

    public void setCarriedBlock(@Nullable BlockState state) {
        entityData.set(DATA_CARRIED, Optional.ofNullable(state));
    }

    /** What this ant carries in its mandibles besides soil and eggs: a seed, or a piece of ant bread (empty if none). */
    public ItemStack getCarriedSeed() {
        return entityData.get(DATA_CARRIED_SEED);
    }

    /** Whether the ant holds a seed (on its way to the food store or the mill). */
    public boolean isCarryingSeed() {
        return !getCarriedSeed().isEmpty() && !isCarryingBread();
    }

    /**
     * Whether the ant holds the nurses' food for the queen and the young: a piece of ant bread, plain or rich, or a
     * termite's fungus nodule (on its way to the queen or a larva or nymph).
     */
    public boolean isCarryingBread() {
        return getCarriedSeed().is(ModItems.ANT_BREAD.get()) || isCarryingRichBread() || getCarriedSeed().is(ModItems.FUNGUS_NODULE.get());
    }

    /** Whether the piece of bread the ant holds is rich ant bread. */
    public boolean isCarryingRichBread() {
        return getCarriedSeed().is(ModItems.RICH_ANT_BREAD.get());
    }

    public void setCarriedSeed(ItemStack seed) {
        entityData.set(DATA_CARRIED_SEED, seed);
    }

    /** Whether this ant holds an egg in its mandibles (a worker bringing it to the nursery). */
    public boolean isCarryingEgg() {
        return entityData.get(DATA_CARRIED_EGG);
    }

    public void setCarryingEgg(boolean carrying) {
        entityData.set(DATA_CARRIED_EGG, carrying);
    }

    /** Whether this ant holds a dead nestmate in its mandibles (a worker bringing it to the refuse chamber). */
    public boolean isCarryingCorpse() {
        return entityData.get(DATA_CARRIED_CORPSE);
    }

    public void setCarryingCorpse(boolean carrying) {
        entityData.set(DATA_CARRIED_CORPSE, carrying);
    }

    /** The seed in the mandibles is for the mill (it becomes bread), not for the food store. Server side only, saved. */
    public boolean isSeedForBread() {
        return seedForBread;
    }

    public void setSeedForBread(boolean forBread) {
        this.seedForBread = forBread;
    }

    /** Whether the mandibles are full (a soil pellet, a seed, bread, an egg or a dead ant), so the ant can't pick up anything else. */
    public boolean hasFullMandibles() {
        return isCarrying() || !getCarriedSeed().isEmpty() || isCarryingEgg() || isCarryingCorpse();
    }

    /** Whether this caste leaves a body when it dies (workers and soldiers; not the queen and king, whose colony ends with them). */
    protected boolean leavesBody() {
        return false;
    }

    /**
     * A dead worker or soldier leaves its body where it died (once the death animation is over, so also after
     * {@code /kill}), for its nestmates to deal with. Not when a chicken killed it: the chicken has eaten it.
     */
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.KILLED && leavesBody() && !(getLastHurtByMob() instanceof net.minecraft.world.entity.animal.Chicken)
                && level() instanceof ServerLevel serverLevel) {
            Colony colony = getColonyId() == null ? null : ColonyManager.get(serverLevel).getColony(getColonyId());
            if (colony != null) {
                colony.refuse().spawnCorpse(serverLevel, getX(), getY(), getZ());
            } else {
                // One that belongs to no colony (put down with a spawn egg far from a queen) still leaves a body; with
                // no nestmates to deal with it, it crumbles to dust after a while.
                AntCorpseEntity corpse = new AntCorpseEntity(bodyType(), serverLevel);
                corpse.moveTo(getX(), getY(), getZ(), getRandom().nextFloat() * 360.0F, 0.0F);
                serverLevel.addFreshEntity(corpse);
            }
        }
        super.remove(reason);
    }

    /**
     * The caste this insect is counted under in its colony, when it is one the colony may turn into numbers inside the
     * nest and back (workers and soldiers, see {@code colony.Population}); null for the queen and king, who are always real.
     */
    @Nullable
    public String nestCaste() {
        return null;
    }

    /**
     * It comes out of its colony's numbers as a real mob (it is counted already, so the headcount stays): it belongs to
     * the colony at once, at this age ({@link #getGrownUpAt}), fed (it ate inside).
     */
    public void comeOutOfNest(Colony colony, long grownUpAt) {
        setColonyId(colony.getId());
        setGrownUpAt(grownUpAt);
    }

    /** Whether it may go back into its colony's numbers now: not while it holds anything or is after an enemy. */
    public boolean mayGoIntoNest() {
        return isAlive() && !hasFullMandibles() && getTarget() == null && !isPassenger();
    }

    /** The kind of body this insect leaves (a dead ant; termites override it). */
    protected net.minecraft.world.entity.EntityType<AntCorpseEntity> bodyType() {
        return com.antcolonies.registry.ModEntities.ANT_CORPSE.get();
    }

    /**
     * A dying ant lets go of what it holds, so another can pick it up: an egg or a dead ant falls where it died, a
     * piece of nurse food (bread, a fungus nodule) is put down as a heap if there is a floor, a seed or a piece of forage
     * drops as an item. A soil pellet is just lost.
     */
    protected void dropLoadOnDeath(ServerLevel level, Colony colony) {
        if (isCarryingEgg()) {
            setCarryingEgg(false);
            colony.brood().spawnEgg(level, getX(), getY(), getZ());
        }
        if (isCarryingCorpse()) {
            setCarryingCorpse(false);
            colony.refuse().spawnCorpse(level, getX(), getY(), getZ());
        }
        if (isCarryingBread()) {
            colony.nurseFood().putDownWhereItStands(level, this);
            setCarriedSeed(ItemStack.EMPTY);
        } else if (!getCarriedSeed().isEmpty()) {
            spawnAtLocation(getCarriedSeed());
            setCarriedSeed(ItemStack.EMPTY);
        }
    }

    /** How long (in seconds) an ant must have nothing to do before it helps with work that is not its role's. */
    public static final int IDLE_SECONDS = 5;

    /** Nothing to do for a few seconds: time to help with other work. */
    public boolean isIdle() {
        return idleSeconds >= IDLE_SECONDS;
    }

    /** Whether this insect is a worker that gets a role from its colony (builder, forager, nurse). Queens and kings don't. */
    protected boolean hasColonyRole() {
        return false;
    }

    /** Whether this insect is one of the colony's workers (one that gets a role: not a queen, king or soldier). */
    public final boolean isColonyWorker() {
        return hasColonyRole();
    }

    /**
     * Whether this ant takes up work of this role now: its own role's work always, the others' only when it is idle.
     * Everything goes for insects without roles, and for a worker without a colony.
     */
    public boolean mayDo(ColonyRole role) {
        return !hasColonyRole() || isIdle() || getColonyId() == null || hasRole(role) || !(level() instanceof ServerLevel serverLevel)
                || ColonyManager.get(serverLevel).getColony(getColonyId()) == null;
    }

    /** The game time this worker grew up, or {@link #AGE_NOT_SET}. */
    public long getGrownUpAt() {
        return grownUpAt;
    }

    /** Sets when this worker grew up: now for one just out of the brood, earlier for one given an age by its colony. */
    public void setGrownUpAt(long gameTime) {
        grownUpAt = gameTime;
        updateYouth();
    }

    /** How long ago (in ticks) this worker grew up, or 0 if not known yet. */
    public long getAgeTicks() {
        return grownUpAt == AGE_NOT_SET ? 0 : Math.max(0, level().getGameTime() - grownUpAt);
    }

    /** How pale the body is drawn: 1 just grown up, fading to 0 by the end of its time as a nurse. */
    public float getYouth() {
        return entityData.get(DATA_YOUTH) / 100.0F;
    }

    private void updateYouth() {
        if (level().isClientSide || !hasColonyRole() || grownUpAt == AGE_NOT_SET || getColonyId() == null
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
        if (colony == null) {
            return;
        }
        int nurseUntil = Math.max(1, colony.profile().worker().nurseUntilTicks());
        float youth = 1.0F - (float) getAgeTicks() / nurseUntil;
        entityData.set(DATA_YOUTH, (byte) Math.round(Math.max(0.0F, Math.min(1.0F, youth)) * 100.0F));
        // Old age: a worker lives the profile's lifespan (a little more or less for each), then dies and leaves its body.
        // In a living colony the queen's eggs replace it; in a queenless one nobody does, so the colony dwindles.
        long lifespan = colony.profile().worker().lifespanFor((getUUID().getLeastSignificantBits() >>> 11) * 0x1.0p-53);
        if (lifespan > 0 && getAgeTicks() >= lifespan && isAlive()) {
            kill();
        }
    }

    /** Whether this ant's own role is {@code role}. */
    public boolean hasRole(ColonyRole role) {
        if (!hasColonyRole() || getColonyId() == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
        return colony != null && colony.roleOf(serverLevel, this) == role;
    }

    /**
     * Hurt by something that is not of its own colony (a chicken pecking, a player, an ant of another colony): the
     * colony's alarm goes up (level 2 at the attacker; only species that raise one, see {@code colony.Alarm}).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel serverLevel && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker
                && !(attacker instanceof AbstractAntEntity insect && getColonyId() != null && getColonyId().equals(insect.getColonyId()))) {
            Colony colony = getColony();
            if (colony != null) {
                colony.alarm().memberHurt(serverLevel, attacker);
            }
        }
        return hurt;
    }

    /** The legs: plans routes (walk, climb, drop) and walks them. Goals only say where to go. */
    public AntNavigator getNavigator() {
        return navigator;
    }

    /** The colony this insect belongs to, if it has one and it still exists (server only). */
    @Nullable
    public Colony getColony() {
        return getColonyId() != null && level() instanceof ServerLevel serverLevel ? ColonyManager.get(serverLevel).getColony(getColonyId()) : null;
    }

    /**
     * Whether this insect may work outside its nest and mound now. Always, unless its colony keeps a schedule (termites:
     * out only at night and in the rain) and it is not foraging time: then it stays inside, out of the sun.
     */
    public boolean mayGoOut() {
        Colony colony = getColony();
        return colony == null || !colony.keepsASchedule() || colony.isForagingTime(level());
    }

    /** Whether this cell may be the end of a walk now: anywhere while it may go out (see {@link #mayGoOut}), otherwise only inside. */
    public boolean mayBeAt(BlockPos cell) {
        Colony colony = getColony();
        return mayGoOut() || colony == null || colony.nest().isInside(level(), cell);
    }

    /** Tests only: the habits every walk gets (null: the usual ones, see {@link #walkHabits}). */
    @Nullable
    private AntPathFinder.StepCosts habitsForTests;

    /**
     * How this insect would like to walk this time (asked by the legs once for each new walk; they give way when no
     * route keeps to them). A termite comes out of or goes in by a chimney's mouth only on some walks (the profile's
     * {@code mound.chimney_trip_chance}, about 1 in 10): on the others it takes another way, such as a foraging hole,
     * if there is one.
     */
    public AntPathFinder.StepCosts walkHabits() {
        if (habitsForTests != null) {
            return habitsForTests;
        }
        Colony colony = getColony();
        float chance = colony == null ? 1.0F : colony.profile().mound().map(SpeciesProfile.MoundNumbers::chimneyTripChance).orElse(1.0F);
        return chance >= 1.0F || getRandom().nextFloat() < chance ? AntPathFinder.StepCosts.NONE : Doorway.avoidMouths(level());
    }

    /** Tests only: gives every walk these habits (null for the usual ones). */
    public void setWalkHabitsForTests(@Nullable AntPathFinder.StepCosts habits) {
        habitsForTests = habits;
    }

    // Runs every tick, just before the move control: keeps the ant walking its planned route.
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        boolean timed = Perf.begin(Perf.Part.WALKING);
        try {
            navigator.tick();
        } finally {
            if (timed) {
                Perf.end();
            }
        }
        // Once a second (staggered): is the ant doing anything, or only wandering and looking around?
        if (tickCount % 20 == getId() % 20) {
            boolean busy = goalSelector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.isRunning()
                    && !(wrapped.getGoal() instanceof FloatGoal || wrapped.getGoal() instanceof AntWanderGoal
                    || wrapped.getGoal() instanceof RandomLookAroundGoal));
            idleSeconds = busy ? 0 : idleSeconds + 1;
            updateYouth();
        }
        if (navigator.isAttached()) {
            // Holding a wall or going round an edge: the navigator decides the exact motion along the surface.
            setDeltaMovement(navigator.getClimbMotion());
        } else if (wasAttached) {
            setDeltaMovement(Vec3.ZERO); // just let go of the wall: no leftover ladder push
        }
        wasAttached = navigator.isAttached();
    }

    @Override
    public void tick() {
        boolean timed = !level().isClientSide && Perf.begin(Perf.Part.INSECTS);
        try {
            tickBody();
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    private void tickBody() {
        super.tick();
        // Only the route's climbing stretches make the ant climb, so ants never wander up walls by themselves.
        if (!level().isClientSide) {
            entityData.set(DATA_SURFACE, (byte) (navigator.getSurface().get3DDataValue() | navigator.getCeilingRef().get3DDataValue() << 3));
            entityData.set(DATA_HEADING, navigator.getHeading());
            entityData.set(DATA_POSE_FLAGS, (byte) ((navigator.isPeeking() ? PEEKING : 0) | (navigator.isTurningSlowly() ? TURNING_SLOWLY : 0)
                    | (navigator.isInChannel() ? IN_CHANNEL : 0)));
        } else {
            turnBody();
            tiltOnSlope();
        }
    }

    /**
     * Client: tilts the drawn body toward the slope it stands on (a ramp, {@link Slope}): head up walking uphill, head
     * down walking down, leaning sideways across it; flat anywhere else. The slope is read from the ramp's own surface
     * under the body's middle, so corners tilt the right way too.
     */
    private void tiltOnSlope() {
        slopePitchO = slopePitch;
        slopeRollO = slopeRoll;
        float pitch = 0.0F;
        float roll = 0.0F;
        double[] rise = wallPose || getSurface() != Direction.DOWN ? null : slopeUnder();
        if (rise != null) {
            double yaw = Math.toRadians(yBodyRot);
            double forwardX = -Math.sin(yaw);
            double forwardZ = Math.cos(yaw);
            pitch = (float) Math.toDegrees(Math.atan(rise[0] * forwardX + rise[1] * forwardZ));
            roll = (float) Math.toDegrees(Math.atan(-rise[0] * forwardZ + rise[1] * forwardX));
        }
        slopePitch = approach(slopePitch, pitch, SLOPE_TILT_SPEED);
        slopeRoll = approach(slopeRoll, roll, SLOPE_TILT_SPEED);
    }

    /**
     * How steeply the slope under the body's middle rises toward east and toward south (height per distance), or null
     * if it stands on no slope.
     */
    @Nullable
    private double[] slopeUnder() {
        BlockPos pos = blockPosition();
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof Slope) && getY() - pos.getY() < 0.1) {
            pos = pos.below();
            state = level().getBlockState(pos);
        }
        if (!(state.getBlock() instanceof Slope slope)) {
            return null;
        }
        int last = Slope.COLUMNS - 1;
        int u = Mth.clamp(Mth.floor((getX() - pos.getX()) * Slope.COLUMNS), 0, last);
        int w = Mth.clamp(Mth.floor((getZ() - pos.getZ()) * Slope.COLUMNS), 0, last);
        int u0 = Math.max(0, u - 1);
        int u1 = Math.min(last, u + 1);
        int w0 = Math.max(0, w - 1);
        int w1 = Math.min(last, w + 1);
        double columnPixels = (double) Slope.PIXELS / Slope.COLUMNS;
        double east = (slope.surfaceHeight(state, u1, w) - slope.surfaceHeight(state, u0, w)) / ((u1 - u0) * columnPixels);
        double south = (slope.surfaceHeight(state, u, w1) - slope.surfaceHeight(state, u, w0)) / ((w1 - w0) * columnPixels);
        return new double[] {east, south};
    }

    /** Client: how far the drawn body tilts head up on a slope (degrees; negative = head down). */
    public float getSlopePitch(float partialTick) {
        return Mth.lerp(partialTick, slopePitchO, slopePitch);
    }

    /** Client: how far the drawn body leans right side up on a slope (degrees). */
    public float getSlopeRoll(float partialTick) {
        return Mth.lerp(partialTick, slopeRollO, slopeRoll);
    }

    /** The surface the body is on: {@link Direction#DOWN} = a floor, {@link Direction#UP} = a ceiling, a horizontal direction = the wall on that side. */
    public Direction getSurface() {
        return Direction.from3DDataValue(entityData.get(DATA_SURFACE) & 7);
    }

    /** On a ceiling: the wall the head's direction is measured from (see {@link AntNavigator#getCeilingRef}). */
    private Direction getCeilingRef() {
        Direction ref = Direction.from3DDataValue(entityData.get(DATA_SURFACE) >> 3 & 7);
        return ref.getAxis().isHorizontal() ? ref : Direction.NORTH;
    }

    /** True while it holds still looking round with its head out of a doorway's mouth (see DoorwayMotion). */
    public boolean isPeeking() {
        return (entityData.get(DATA_POSE_FLAGS) & PEEKING) != 0;
    }

    /** Client: true while it is inside a channel (a termite tunnel): the body is drawn in the channel's middle. */
    public boolean isInChannel() {
        return (entityData.get(DATA_POSE_FLAGS) & IN_CHANNEL) != 0;
    }

    /** True while the drawn body tips over to its new surface slowly (over the rim of a doorway's mouth). */
    private boolean isTurningSlowly() {
        return (entityData.get(DATA_POSE_FLAGS) & TURNING_SLOWLY) != 0;
    }

    /** True while the body is on a wall or a ceiling. */
    public boolean isClimbing() {
        return getSurface() != Direction.DOWN;
    }

    // Tells the game this entity is holding on to something (no falling while the navigator moves it along a surface).
    @Override
    public boolean onClimbable() {
        return isClimbing() || (!level().isClientSide && navigator.isAttached());
    }

    /**
     * Client: turns the drawn body a little toward the synced surface and heading, so it never snaps round. The tilt is
     * 0 on a floor, 90 upright on a wall and 180 upside down on a ceiling: going from a wall onto the ceiling the body
     * just tips on over, so it keeps the yaw of the wall it came up (on a ceiling the yaw and the heading both turn the
     * body round the vertical, so one can stand in for the other). Onto a wall facing the other way (over the top of a
     * thin wall, such as a chimney's rim) it first lies flat and then tips up onto the new wall, never spinning round
     * within the old one.
     */
    private void turnBody() {
        bodyTiltO = bodyTilt;
        wallYawO = wallYaw;
        bodyHeadingO = bodyHeading;
        Direction surface = getSurface();
        float turn = isTurningSlowly() ? BODY_TURN_SPEED / 2.0F : BODY_TURN_SPEED;
        if (surface != Direction.DOWN) {
            Direction wall = surface == Direction.UP ? getCeilingRef() : surface;
            float targetTilt = surface == Direction.UP ? 180.0F : 90.0F;
            if (!wallPose) {
                // Tipping up from the floor: start from how the body lay there (its body yaw of the last tick; this
                // tick's has already begun to turn toward the wall).
                wallPose = true;
                wallYaw = wall.toYRot();
                bodyHeading = Mth.wrapDegrees(wallYaw - floorYaw);
                bodyTilt = 0.0F;
                if (tickCount < 3) {
                    // Just loaded (or spawned) already on a wall or a ceiling: no tipping up, it is simply there.
                    bodyTilt = targetTilt;
                    bodyHeading = entityData.get(DATA_HEADING);
                }
                wallYawO = wallYaw;
                bodyHeadingO = bodyHeading;
                bodyTiltO = bodyTilt;
            }
            if (surface != Direction.UP && bodyTilt <= 90.0F && Math.abs(Mth.wrapDegrees(wall.toYRot() - wallYaw)) > 90.0F) {
                // Onto a wall facing the other way: lie flat first. Once it lies flat (this tick and the last), the
                // same pose is put as lying against the new wall (wall yaw and heading turned by the same amount), and
                // from the next tick it tips up onto it.
                if (bodyTilt <= 0.0F && bodyTiltO <= 0.0F) {
                    float shift = Mth.wrapDegrees(wall.toYRot() - wallYaw);
                    wallYaw += shift;
                    wallYawO += shift;
                    bodyHeading += shift;
                    bodyHeadingO += shift;
                } else {
                    bodyTilt = Math.max(0.0F, bodyTilt - turn);
                }
                return;
            }
            if (surface == Direction.UP) {
                // Keep the yaw it has; turn the heading so the head points the synced way.
                bodyHeading = approach(bodyHeading, entityData.get(DATA_HEADING) + Mth.wrapDegrees(wall.toYRot() - wallYaw), turn);
            } else {
                if (bodyTilt > 90.0F && Mth.wrapDegrees(wall.toYRot() - wallYaw) != 0.0F) {
                    // Coming off the ceiling onto another wall: take that wall's yaw, and turn the heading back by as
                    // much, which looks the same while the body is upside down.
                    float shift = Mth.wrapDegrees(wall.toYRot() - wallYaw);
                    wallYaw += shift;
                    wallYawO += shift;
                    bodyHeading -= shift;
                    bodyHeadingO -= shift;
                }
                wallYaw = approach(wallYaw, wall.toYRot(), turn);
                bodyHeading = approach(bodyHeading, entityData.get(DATA_HEADING), turn);
            }
            bodyTilt = bodyTilt < targetTilt ? Math.min(targetTilt, bodyTilt + turn)
                    : Math.max(targetTilt, bodyTilt - turn);
        } else if (wallPose) {
            // Tipping back onto the floor, ending up facing the body yaw.
            bodyTilt = Math.max(0.0F, bodyTilt - turn);
            bodyHeading = approach(bodyHeading, wallYaw - yBodyRot, turn);
            if (bodyTilt <= 0.0F && Math.abs(Mth.wrapDegrees(bodyHeading - (wallYaw - yBodyRot))) < 1.0F) {
                wallPose = false;
            }
        }
        if (!wallPose) {
            floorYaw = yBodyRot;
        }
    }

    private static float approach(float from, float to, float speed) {
        return from + Mth.clamp(Mth.wrapDegrees(to - from), -speed, speed);
    }

    /** Client: whether the body is drawn by the wall angles below (on a wall, or tipping onto or off one). */
    public boolean isInWallPose() {
        return wallPose;
    }

    /** Client: how far the body is tipped up onto the wall, 0 (floor) to 90 (upright on the wall) to 180 (upside down on a ceiling). */
    public float getBodyTilt(float partialTick) {
        return Mth.lerp(partialTick, bodyTiltO, bodyTilt);
    }

    /** Client: the yaw of the wall the body faces. */
    public float getWallYaw(float partialTick) {
        return Mth.lerp(partialTick, wallYawO, wallYaw);
    }

    /** Client: where the head points within the wall (0 up, 180 down, +90 to its left). */
    public float getBodyHeading(float partialTick) {
        return Mth.lerp(partialTick, bodyHeadingO, bodyHeading);
    }

    // Ants are light enough to survive any fall (needed for the deep shaft).
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    protected void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (colonyId != null) {
            tag.putUUID("ColonyId", colonyId);
        }
        if (grownUpAt != AGE_NOT_SET) {
            tag.putLong("GrownUpAt", grownUpAt);
        }
        // What the ant holds in its mandibles, so nothing is lost when the world is saved and loaded again: the job that
        // carries it picks it up again after the reload (each one carries on when the ant already holds its load).
        BlockState pellet = getCarriedBlock();
        if (pellet != null) {
            tag.put("CarriedBlock", NbtUtils.writeBlockState(pellet));
        }
        if (!getCarriedSeed().isEmpty()) {
            tag.put("CarriedSeed", getCarriedSeed().save(registryAccess()));
        }
        tag.putBoolean("CarryingEgg", isCarryingEgg());
        tag.putBoolean("CarryingCorpse", isCarryingCorpse());
        tag.putBoolean("SeedForBread", seedForBread);
        // The wall or ceiling it holds on to (or the channel it is in), so after a reload it is still there instead of
        // dropping to the bottom.
        Foothold hold = navigator.getHold();
        if (hold != null) {
            tag.putLong("HoldCell", hold.cell().asLong());
            tag.putByte("HoldSurface", (byte) hold.surface().get3DDataValue());
            tag.putBoolean("HoldChannel", hold.isChannel());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ColonyId")) {
            colonyId = tag.getUUID("ColonyId");
        }
        grownUpAt = tag.contains("GrownUpAt") ? tag.getLong("GrownUpAt") : AGE_NOT_SET;
        if (tag.contains("CarriedBlock")) {
            setCarriedBlock(NbtUtils.readBlockState(registryAccess().lookupOrThrow(Registries.BLOCK), tag.getCompound("CarriedBlock")));
        }
        if (tag.contains("CarriedSeed")) {
            setCarriedSeed(ItemStack.parse(registryAccess(), tag.getCompound("CarriedSeed")).orElse(ItemStack.EMPTY));
        }
        setCarryingEgg(tag.getBoolean("CarryingEgg"));
        setCarryingCorpse(tag.getBoolean("CarryingCorpse"));
        seedForBread = tag.getBoolean("SeedForBread");
        if (tag.contains("HoldCell") && tag.contains("HoldSurface")) {
            navigator.restoreHold(new Foothold(BlockPos.of(tag.getLong("HoldCell")), Direction.from3DDataValue(tag.getByte("HoldSurface")),
                    tag.getBoolean("HoldChannel")));
        }
    }

    // Colony members never despawn.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // Colony members don't push each other.
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate));
    }

    /** An animation this insect plays instead of walking or standing (a soldier's head bang), or null. */
    @Nullable
    protected RawAnimation specialAnimation() {
        return null;
    }

    private PlayState animate(AnimationState<AbstractAntEntity> state) {
        RawAnimation special = specialAnimation();
        if (special != null) {
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(special);
        }
        if (isPeeking()) {
            state.getController().setAnimationSpeed(1.0);
            return state.setAndContinue(PEEK);
        }
        // Moving on a wall isn't "horizontal" movement, so check climbing separately (an ant hanging still does not move its legs).
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        double moved = dx * dx + dy * dy + dz * dz;
        if (isClimbing() && moved > 1.0E-4) {
            // The legs keep pace with the body: slower when it climbs slowly (poking its head out of a chimney).
            state.getController().setAnimationSpeed(Mth.clamp(Math.sqrt(moved) / CLIMB_STEP * 1.5, 0.4, 1.5));
            return state.setAndContinue(WALK);
        }
        if (state.isMoving()) {
            // Legs move faster when the ant moves faster, so they don't slide.
            state.getController().setAnimationSpeed(Math.max(0.6, state.getLimbSwingAmount() * 6.0));
            return state.setAndContinue(WALK);
        }
        state.getController().setAnimationSpeed(1.0);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
