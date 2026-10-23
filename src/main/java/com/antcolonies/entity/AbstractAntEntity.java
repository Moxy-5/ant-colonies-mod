package com.antcolonies.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyQuestions;
import com.antcolonies.nav.AntMoveControl;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.debug.Perf;
import com.antcolonies.entity.body.AntAnimationController;
import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.body.EdgeOver;
import com.antcolonies.entity.body.Spine;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.GroomGoal;
import com.antcolonies.entity.goal.NapGoal;
import com.antcolonies.entity.goal.NoticeGoal;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.Doorway;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Slope;
import com.antcolonies.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared base for every colony insect: never despawns, doesn't push, plays idle/walk animations. */
public abstract class AbstractAntEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    /**
     * The walks for each speed (2026-10-16): real ants walk faster mostly by taking longer strides, so a slow ant takes
     * short steps and a fast one long ones. Slowest first; each clip says the speeds it is for (its "speeds" note), and
     * the body player changes between them in step ({@code AntAnimationController.gearFor}). Every model's file has
     * them (the termites' since 2026-10-18, made by tools/termite_models.py).
     */
    private static final RawAnimation WALK_SLOW = RawAnimation.begin().thenLoop("walk_slow");
    private static final RawAnimation WALK_FAST = RawAnimation.begin().thenLoop("walk_fast");
    private static final List<RawAnimation> WALKS = List.of(WALK_SLOW, WALK, WALK_FAST);
    /** Stepping round to turn where it stands (to the right, to the left; see {@link #measureWalk}). Ants and termites. */
    private static final RawAnimation TURN_R = RawAnimation.begin().thenLoop("turn_r");
    private static final RawAnimation TURN_L = RawAnimation.begin().thenLoop("turn_l");
    /** Asleep: still, and now and then a short bout of antenna twitches (see {@link #napTicks}). Ants only. */
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation SLEEP_TWITCH = RawAnimation.begin().thenLoop("sleep_twitch");
    /** On hot ground: standing and walking tall on straight legs, the gaster up. Ants only. */
    private static final RawAnimation IDLE_HOT = RawAnimation.begin().thenLoop("idle_hot");
    private static final RawAnimation WALK_HOT = RawAnimation.begin().thenLoop("walk_hot");
    /** Ticks to blend into a nap and out of it (slow, as a real ant settles; other changes take 3). */
    private static final int SETTLE_TICKS = 20;
    /**
     * Noticing a player (see {@code NoticeGoal}): standing taller, forebody up, antennae pointed and flicking, the head
     * cocked one way and the other (look), or the same with the jaws open (look_wary, a shy ant). Ants only.
     */
    private static final RawAnimation LOOK = RawAnimation.begin().thenLoop("look");
    private static final RawAnimation LOOK_WARY = RawAnimation.begin().thenLoop("look_wary");
    /** A termite frozen at a shake of the ground: antennae raised and quivering. Termites only. */
    private static final RawAnimation FREEZE = RawAnimation.begin().thenLoop("freeze");
    /** Ticks to blend into a look and out of it. */
    private static final int LOOK_SETTLE_TICKS = 6;
    /**
     * Cleaning an antenna with the comb on the front leg (see {@code GroomGoal}), the left, the right, or both one after
     * the other. Played once at the speed they were made; the last frame (the standing pose) is held. Ants only.
     */
    private static final RawAnimation GROOM_L = RawAnimation.begin().thenPlayAndHold("groom_antenna_l");
    private static final RawAnimation GROOM_R = RawAnimation.begin().thenPlayAndHold("groom_antenna_r");
    private static final RawAnimation GROOM_BOTH = RawAnimation.begin().thenPlayAndHold("groom_antenna_both");
    /**
     * Digging (see {@code DigGoal}): the forebody and head down, the jaws biting, the front legs raking the loose soil
     * back by turns, the antennae tapping the face it digs. Ants only.
     */
    private static final RawAnimation DIG = RawAnimation.begin().thenLoop("dig");
    /**
     * Carrying something in the jaws (played by the head player only, over the walk or the stand): the head held up, the
     * jaws closed on the load, the antennae high. Ants only.
     */
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop("carry");
    /** How long the cleaning clips last (ticks; worker_ant.animation.json, made by tools/ant_models.py). */
    public static final int GROOM_ONE_TICKS = 52;
    public static final int GROOM_BOTH_TICKS = 104;
    /** Ticks to blend into a cleaning clip and out of it (my pick, to confirm). */
    public static final int GROOM_SETTLE_TICKS = 4;
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
     * bit 2 = inside a channel (a termite tunnel: drawn in the channel's middle, not pressed against a wall), bit 3 =
     * asleep (see {@code NapGoal}), bit 4 = on hot ground (see {@link #checkHeat}).
     */
    private static final EntityDataAccessor<Byte> DATA_POSE_FLAGS =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    /**
     * What it is noticing (see {@code NoticeGoal}): {@link #NOTICE_NONE}, {@link #NOTICE_LOOK} (looking at a player: the
     * look clips, the head turned toward it), {@link #NOTICE_WARY} (the same, jaws open, a shy one) or
     * {@link #NOTICE_FREEZE} (a termite frozen at a shake of the ground). Synced for the clips and the head look.
     */
    private static final EntityDataAccessor<Byte> DATA_NOTICE =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    public static final byte NOTICE_NONE = 0;
    public static final byte NOTICE_LOOK = 1;
    public static final byte NOTICE_WARY = 2;
    public static final byte NOTICE_FREEZE = 3;
    /**
     * A short piece of body work it is doing, for the client's clips: {@link #ACTIVITY_NONE}, cleaning its left, right
     * or both antennae ({@link #ACTIVITY_GROOM_L}, {@link #ACTIVITY_GROOM_R}, {@link #ACTIVITY_GROOM_BOTH}; see
     * {@code GroomGoal}) or biting soil loose ({@link #ACTIVITY_DIG}). Synced.
     */
    private static final EntityDataAccessor<Byte> DATA_ACTIVITY =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    public static final byte ACTIVITY_NONE = 0;
    public static final byte ACTIVITY_GROOM_L = 1;
    public static final byte ACTIVITY_GROOM_R = 2;
    public static final byte ACTIVITY_GROOM_BOTH = 3;
    public static final byte ACTIVITY_DIG = 4;
    /** How pale a young worker is drawn, 0 (grown dark) to 100 (just grown up). Synced for the renderer. */
    private static final EntityDataAccessor<Byte> DATA_YOUTH =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    /** {@link #grownUpAt} before it is known (a worker from a spawn egg or an older save: its colony gives it an age). */
    public static final long AGE_NOT_SET = Long.MIN_VALUE;
    private static final int PEEKING = 1;
    private static final int TURNING_SLOWLY = 2;
    private static final int IN_CHANNEL = 4;
    private static final int ASLEEP = 8;
    private static final int ON_HOT_GROUND = 16;

    /** The soil pellet held in the mandibles (empty = carrying nothing). Synced so the renderer can draw it. */
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRIED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

    /** The seed held in the mandibles (empty = none). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_SEED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.ITEM_STACK);

    /**
     * True while the nurse food it carries is in its crop, not its jaws (it ate it to pass it on mouth to mouth): the
     * renderer draws nothing in the jaws. Synced. Saved.
     */
    private static final EntityDataAccessor<Boolean> DATA_IN_CROP =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

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
    /** The navigator's exact motion on the last tick it held this insect to a surface (server). */
    private Vec3 lastClimbMotion = Vec3.ZERO;

    // Client only: how the body is drawn, turned smoothly toward the synced surface and heading (see AntRenderer).
    // While on a wall (or tipping onto or off one) the body is drawn by these three angles instead of the body yaw:
    // the yaw of the wall it faces, the tilt (0 = lying on the floor, 90 = upright on the wall) and the heading within it.
    private boolean wallPose;
    private float bodyTilt;
    private float bodyTiltO;
    /** Client: going over an outside edge, where the drawn feet are put (see {@link #getEdgeShift}); null otherwise. */
    @Nullable
    private Vec3 edgeShift;
    @Nullable
    private Vec3 edgeShiftO;
    /** Client: what is left of the edge pose's shift while it fades out (see {@link #fadeEdge}). */
    private Vec3 edgeLeft = Vec3.ZERO;
    private boolean edgeFading;
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
        builder.define(DATA_NOTICE, NOTICE_NONE);
        builder.define(DATA_ACTIVITY, ACTIVITY_NONE);
        builder.define(DATA_YOUTH, (byte) 0);
        builder.define(DATA_CARRIED, Optional.empty());
        builder.define(DATA_CARRIED_SEED, ItemStack.EMPTY);
        builder.define(DATA_CARRIED_EGG, false);
        builder.define(DATA_IN_CROP, false);
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
        if (seed.isEmpty()) {
            entityData.set(DATA_IN_CROP, false);
        }
    }

    /**
     * The nurse ate this piece of nurse food to pass it on mouth to mouth: it counts as carried (the same jobs follow it
     * through), but it is in the crop, so nothing shows in the jaws.
     */
    public void swallowToPassOn(ItemStack food) {
        entityData.set(DATA_CARRIED_SEED, food);
        entityData.set(DATA_IN_CROP, !food.isEmpty());
    }

    /** Whether what it carries is in its crop (to pass on mouth to mouth), not in its jaws. */
    public boolean isFoodInCrop() {
        return entityData.get(DATA_IN_CROP) && !getCarriedSeed().isEmpty();
    }

    /**
     * Where its head is (its mouth, for mouth-to-mouth feeding): half its body length in front of its middle. A swollen
     * termite queen's head is well in front of her middle.
     */
    public net.minecraft.world.phys.Vec3 headPoint() {
        net.minecraft.world.phys.Vec3 forward = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, yBodyRot);
        return position().add(forward.scale(getBbWidth() * 0.5));
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

    /**
     * The game time of its last meal, for the colony's numbers when it goes into them ({@code Colony.NOT_SET}: it does
     * not eat, or has not started its clock yet). Overridden by the insects that eat.
     */
    public long getLastMeal() {
        return com.antcolonies.colony.Colony.NOT_SET;
    }

    /** Coming out of the numbers: when it last ate inside (only for the insects that eat). */
    public void setLastMeal(long gameTime) {
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
        if (isFoodInCrop()) {
            setCarriedSeed(ItemStack.EMPTY); // (eaten: it dies with it)
        } else if (isCarryingBread()) {
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

    /**
     * Whether a goal that looks for work looks now. Goals look only one time in {@code oneIn}, so the ants don't all
     * think on the same tick; but in its last {@link #LAST_LOOK_SECONDS} before it counts as idle, an ant looks every
     * time. Otherwise a builder with blocks still to dig could miss its work by chance for 5 seconds (about 1 pause in
     * 13), count as idle and go out on a forager's search of up to 5 minutes, leaving the nest undug (found by the hill
     * test on a step, 2026-10-16, see docs/BUGS.md 31).
     */
    public boolean looksForWork(int oneIn) {
        return idleSeconds >= IDLE_SECONDS - LAST_LOOK_SECONDS && idleSeconds < IDLE_SECONDS || getRandom().nextInt(oneIn) == 0;
    }

    /** How long (in seconds) before it counts as idle an ant looks for its own work at every chance (see {@link #looksForWork}). */
    private static final int LAST_LOOK_SECONDS = 2;

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
                // The colony news (colony parts step 4): the alarm, a patroller turning back, the danger memory and a
                // forager hurt near its spot each hear it, if the colony has them (their rows in ColonyParts).
                colony.news().tell(serverLevel, ColonyNews.MEMBER_HURT, new ColonyNews.MemberHurt(this, blockPosition(), attacker, isDeadOrDying()));
            }
        }
        return hurt;
    }

    /**
     * Once a second: a member out on the surface (under the open sky, outside the nest) that sees a hostile mob close by
     * (a monster, or any mob hunting insects, such as a chicken after termites) tells the danger memory (brain step 4g).
     */
    private void lookForHostiles(ServerLevel level) {
        Colony colony = getColonyId() == null ? null : getColony();
        if (colony == null || colony.danger().numbers() == null || !level.canSeeSky(blockPosition())
                || colony.nest().isInside(level, blockPosition())) {
            return;
        }
        double within = colony.danger().numbers().seenWithin();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(within),
                mob -> mob.isAlive() && !(mob instanceof AbstractAntEntity)
                        && (mob instanceof net.minecraft.world.entity.monster.Enemy || mob.getTarget() instanceof AbstractAntEntity))) {
            colony.danger().seen(level, blockPosition(), mob.getUUID(), mob.getType().toShortString());
        }
    }

    /**
     * Once a second (brain step 4g, step 5): a member out on the surface that comes within {@code colony.neighbours.within}
     * blocks of an insect of another colony of its species, or stands on such a colony's trunk trail, tells its colony of
     * the meeting ({@code colony.Neighbours}; real harvester ant foragers meeting a neighbour's). Not danger: nobody is hurt.
     */
    private void lookForNeighbours(ServerLevel level) {
        Colony colony = getColonyId() == null ? null : getColony();
        if (colony == null || colony.neighbours().numbers() == null || !level.canSeeSky(blockPosition())
                || colony.nest().isInside(level, blockPosition())) {
            return;
        }
        double within = colony.neighbours().numbers().within();
        for (AbstractAntEntity other : level.getEntitiesOfClass(AbstractAntEntity.class, getBoundingBox().inflate(within),
                other -> other.isAlive() && other.getColonyId() != null && !other.getColonyId().equals(getColonyId())
                        && other.distanceTo(this) <= within)) {
            Colony theirs = other.getColony();
            if (theirs != null && theirs.getSpecies().equals(colony.getSpecies()) && colony.neighbours().canMeet(theirs)) {
                colony.news().tell(level, ColonyNews.NEIGHBOUR_MET, new ColonyNews.NeighbourMet(blockPosition(), theirs.getId(), other.getUUID()));
            }
        }
        for (Colony theirs : ColonyManager.get(level).getColonies()) {
            if (theirs != colony && theirs.getSpecies().equals(colony.getSpecies()) && colony.neighbours().canMeet(theirs)
                    && theirs.questions().ask(level, ColonyQuestions.ON_TRAIL, blockPosition())) {
                UUID trail = new UUID(theirs.getId().getMostSignificantBits() ^ getUUID().getMostSignificantBits(),
                        theirs.getId().getLeastSignificantBits() ^ getUUID().getLeastSignificantBits());
                colony.news().tell(level, ColonyNews.NEIGHBOUR_MET, new ColonyNews.NeighbourMet(blockPosition(), theirs.getId(), trail)); // (stepped onto their trail)
            }
        }
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

    /** The species this insect is (for its profile while it has no colony). */
    protected abstract ResourceLocation species();

    /** Server: this insect's species profile (its colony's, or its species' own while it has none). */
    public SpeciesProfile profile() {
        Colony colony = getColony();
        return colony != null ? colony.profile() : SpeciesProfiles.get(species());
    }

    /** Server: whether it is having a nap (set by {@code NapGoal}; not saved: after a reload the nap is over). */
    private boolean asleep;
    /** Server: whether it stands on hot ground (see {@link #checkHeat}). */
    private boolean onHotGround;

    /** Tests only: the chance a second it falls asleep when it may (below 0: its profile's). */
    private float napChanceForTests = -1.0F;

    /** Tests only: gives it this chance a second to fall asleep when it may (below 0 for its profile's). */
    public void setNapChanceForTests(float chance) {
        napChanceForTests = chance;
    }

    /** The chance a second it falls asleep when it may, or below 0 for its profile's (see {@code NapGoal}). */
    public float napChanceForTests() {
        return napChanceForTests;
    }

    /** Tests only: the chance it notices a moving player it can tell is there (below 0: its profile's and its curiosity). */
    private float noticeChanceForTests = -1.0F;

    /** Tests only: gives it this chance to notice a moving player (below 0 for its profile's). */
    public void setNoticeChanceForTests(float chance) {
        noticeChanceForTests = chance;
    }

    /** The chance it notices a moving player, or below 0 for its profile's (see {@code NoticeGoal}). */
    public float noticeChanceForTests() {
        return noticeChanceForTests;
    }

    /** Tests only: the only players it may notice (null: the players in its level). */
    @Nullable
    private List<? extends Player> playersForTests;

    /** Tests only: lets it notice only these players (mock players, not in the level), or null for the level's. */
    public void setPlayersForTests(@Nullable List<? extends Player> players) {
        playersForTests = players;
    }

    /** The players it may notice: its level's, or the test's (see {@code NoticeGoal}). */
    public List<? extends Player> playersToNotice() {
        return playersForTests != null ? playersForTests : level().players();
    }

    /** Server: falls asleep or wakes up (see {@code NapGoal}). */
    public void setAsleep(boolean asleep) {
        this.asleep = asleep;
    }

    /** Whether it is having a nap (synced, for the sleeping clips). */
    public boolean isAsleep() {
        return (entityData.get(DATA_POSE_FLAGS) & ASLEEP) != 0;
    }

    /** Whether it stands on ground too hot to touch (synced: it stands and walks tall, the gaster up). */
    public boolean isOnHotGround() {
        return (entityData.get(DATA_POSE_FLAGS) & ON_HOT_GROUND) != 0;
    }

    /** What it is noticing now ({@link #NOTICE_NONE}, {@link #NOTICE_LOOK}, {@link #NOTICE_WARY} or {@link #NOTICE_FREEZE}; synced). */
    public byte getNoticing() {
        return entityData.get(DATA_NOTICE);
    }

    /** Server: starts or stops noticing something (see {@code NoticeGoal}). */
    public void setNoticing(byte what) {
        entityData.set(DATA_NOTICE, what);
    }

    /** The piece of body work it is doing now ({@link #ACTIVITY_NONE}, a cleaning or digging; synced). */
    public byte getActivity() {
        return entityData.get(DATA_ACTIVITY);
    }

    /** Server: starts or stops a piece of body work (see {@code GroomGoal}, {@code DigGoal}). */
    public void setActivity(byte what) {
        entityData.set(DATA_ACTIVITY, what);
    }

    /** Tests only: the chance a second it starts cleaning its antennae when it may (below 0: its profile's). */
    private float groomChanceForTests = -1.0F;

    /** Tests only: gives it this chance a second to start cleaning its antennae (below 0 for its profile's). */
    public void setGroomChanceForTests(float chance) {
        groomChanceForTests = chance;
    }

    /** The chance a second it starts cleaning its antennae, or below 0 for its profile's (see {@code GroomGoal}). */
    public float groomChanceForTests() {
        return groomChanceForTests;
    }

    // --- personality (the animation engine, 2026-10-16) -------------------------------------------------------------
    /**
     * Server: its personality, each trait 0 to 1 (see {@link SpeciesProfile.Traits}): drawn from its profile the first
     * time it is asked for, then its own for life. Saved. An insect that goes into its colony's numbers loses it; a new
     * real one draws its own (the numbers carry no individuals).
     */
    private float boldness = Float.NaN;
    private float curiosity = Float.NaN;
    private float restlessness = Float.NaN;

    private SpeciesProfile.Traits traits() {
        return profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::traits).orElse(SpeciesProfile.Traits.EVEN);
    }

    private void drawTraits() {
        if (!Float.isNaN(boldness)) {
            return;
        }
        SpeciesProfile.Traits traits = traits();
        boldness = SpeciesProfile.Traits.draw(traits.boldness(), getRandom().nextDouble());
        curiosity = SpeciesProfile.Traits.draw(traits.curiosity(), getRandom().nextDouble());
        restlessness = SpeciesProfile.Traits.draw(traits.restlessness(), getRandom().nextDouble());
    }

    /**
     * Server: how bold it is now (0 to 1): its own boldness, and bolder as it grows older (real foragers, the older
     * workers, are bolder than the young nurses).
     */
    public float boldness() {
        drawTraits();
        return Mth.clamp(boldness + traits().olderBolder() * (0.5F - getYouth()), 0.0F, 1.0F);
    }

    /** Server: how curious it is (0 to 1): how often it stops to look at something new, and for how long. */
    public float curiosity() {
        drawTraits();
        return curiosity;
    }

    /** Server: how restless it is (0 to 1): restless ones nap less and fidget and groom more. */
    public float restlessness() {
        drawTraits();
        return restlessness;
    }

    /** Tests only: gives it this personality. */
    public void setTraitsForTests(float boldness, float curiosity, float restlessness) {
        this.boldness = boldness;
        this.curiosity = curiosity;
        this.restlessness = restlessness;
    }

    /**
     * Server, once a second: whether the ground under it is hot (its profile's {@code body_language.hot}: outside on a
     * floor under the open sky, no rain, in a hot biome, in the hottest hours of the day).
     */
    private void checkHeat(ServerLevel level) {
        SpeciesProfile.Heat heat = profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::heat).orElse(null);
        if (heat == null || isClimbing() || !onGround()) {
            onHotGround = false;
            return;
        }
        Colony colony = getColony();
        BlockPos at = blockPosition();
        onHotGround = level.canSeeSky(at) && !level.isRainingAt(at) && (colony == null || !colony.nest().isInside(level, at))
                && heat.hotAt(colony != null ? colony.timeOfDay(level) : level.getDayTime(), level.getBiome(at).value().getBaseTemperature(),
                        profile().food().dayClock());
    }

    /**
     * Whether this insect may work outside its nest and mound now. Always, unless its colony keeps a schedule (termites:
     * out only at night and in the rain) and it is not foraging time: then it stays inside, out of the sun. A species
     * whose foragers keep a day clock ({@code food.day_clock}, the harvester ant) keeps it for all its workers' surface
     * work too (the user's decision, brain step 4b: in at night, in the rain and in the midday rest); not the queen.
     */
    public boolean mayGoOut() {
        Colony colony = getColony();
        if (colony == null) {
            return true;
        }
        if (colony.keepsASchedule()) {
            return colony.isForagingTime(level());
        }
        return !isColonyWorker() || colony.profile().food().dayClock().isEmpty() || colony.isForagingTime(level());
    }

    /**
     * Whether this insect, with nothing to do, rests inside its nest near the entrance instead of strolling about: a
     * worker of a species with {@code worker.rest_near_entrance} (real red harvester ants keep their spare workers in the
     * upper chambers, see {@code SpeciesProfile.WorkerNumbers}). The blocks it rests within, or 0 if it strolls anywhere.
     */
    public int restsNearEntrance() {
        Colony colony = getColony();
        return colony == null || !hasColonyRole() ? 0 : colony.profile().worker().restNearEntrance();
    }

    /** Whether this cell may be the end of a walk now: anywhere while it may go out (see {@link #mayGoOut}), otherwise only inside. */
    public boolean mayBeAt(BlockPos cell) {
        Colony colony = getColony();
        return mayGoOut() || colony == null || colony.nest().isInside(level(), cell);
    }

    /**
     * The hidden ways this insect's legs may travel unseen (its colony's closed galleries, termites underground step 2b,
     * {@code colony.HiddenTrips}), or null: none, or an insect its colony does not count (a queen, a king).
     */
    @Nullable
    public com.antcolonies.nav.HiddenWays hiddenWays() {
        if (nestCaste() == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Colony colony = getColony();
        ColonyHooks.HiddenAway hidden = colony == null ? null : colony.hooks().first(ColonyHooks.HIDDEN_AWAY);
        return hidden == null ? null : hidden.ways(serverLevel);
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
                    || wrapped.getGoal() instanceof RandomLookAroundGoal || wrapped.getGoal() instanceof NapGoal
                    || wrapped.getGoal() instanceof NoticeGoal || wrapped.getGoal() instanceof GroomGoal));
            idleSeconds = busy ? 0 : idleSeconds + 1;
            updateYouth();
            if (level() instanceof ServerLevel serverLevel) {
                checkHeat(serverLevel);
                lookForHostiles(serverLevel);
                lookForNeighbours(serverLevel);
            }
        }
        // A look round every few ticks: a forager home with a seed touches the ants waiting inside by the entrance as it
        // walks in (Gordon's rule, the harvester's door), one home from a rich patch lays its scent line, then calls (its
        // recruits).
        if (tickCount % 5 == getId() % 5 && getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            Colony colony = getColony();
            if (colony != null) {
                colony.news().tell(serverLevel, ColonyNews.MEMBER_ROUND, new ColonyNews.MemberRound(this));
            }
        }
        if (navigator.isAttached()) {
            // Holding a wall or going round an edge: the navigator decides the exact motion along the surface.
            setDeltaMovement(navigator.getClimbMotion());
            lastClimbMotion = navigator.getClimbMotion();
        } else if (wasAttached) {
            // Just let go: no leftover ladder push. Let go onto a floor (at a wall's foot, over its top) it walks on at
            // the pace it had along the floor, standing on it from this tick: from a standstill, and a tick in the air
            // before the game noticed the floor, it crept and then dashed (docs/BUGS.md 53).
            if (navigator.getSurface() == Direction.DOWN && !level().noCollision(this, getBoundingBox().move(0.0, -0.01, 0.0))) {
                setDeltaMovement(lastClimbMotion.x, -0.0784, lastClimbMotion.z);
                setOnGround(true);
            } else {
                setDeltaMovement(Vec3.ZERO);
            }
        }
        wasAttached = navigator.isAttached();
    }

    /** The game time of this insect's last server tick (not saved: after a load it has always just come back). */
    private long lastServerTick = Long.MIN_VALUE;

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel && nestCaste() != null && backFromFrozen(serverLevel)) {
            return; // it was frozen in unloaded land and is one of its colony's numbers now: it has left the world
        }
        boolean timed = !level().isClientSide && Perf.begin(Perf.Part.INSECTS);
        try {
            tickBody();
        } finally {
            if (timed) {
                Perf.end();
            }
        }
    }

    /**
     * Whether this insect has just come back from being frozen (its chunk went out of range) and has left the world
     * because its colony counts it among the numbers now ({@code colony.AwayLife}). Asked on the first tick after any gap,
     * and once a second besides.
     */
    private boolean backFromFrozen(ServerLevel level) {
        long now = level.getGameTime();
        boolean gap = now != lastServerTick + 1;
        lastServerTick = now;
        return (gap || tickCount % 20 == 0) && com.antcolonies.colony.AwayLife.backFromFrozen(level, this, getColonyId());
    }

    private void tickBody() {
        super.tick();
        // Only the route's climbing stretches make the ant climb, so ants never wander up walls by themselves.
        if (!level().isClientSide) {
            entityData.set(DATA_SURFACE, (byte) (navigator.getSurface().get3DDataValue() | navigator.getCeilingRef().get3DDataValue() << 3));
            entityData.set(DATA_HEADING, navigator.getHeading());
            entityData.set(DATA_POSE_FLAGS, (byte) ((navigator.isPeeking() ? PEEKING : 0) | (navigator.isTurningSlowly() ? TURNING_SLOWLY : 0)
                    | (navigator.isInChannel() ? IN_CHANNEL : 0) | (asleep ? ASLEEP : 0) | (onHotGround ? ON_HOT_GROUND : 0)));
        } else {
            turnBody();
            tiltOnSlope();
            measureWalk();
            tickNap();
            tickLookWeight();
            tickWaist();
            tickSteam();
        }
    }

    /**
     * Client: steam off an ant on hot ground (the user's choice, 2026-10-15, and the mod's one knowingly unrealistic
     * touch, so a player sees the heat at a glance): now and then a wisp of white smoke rises off its back. About one
     * every 30 ticks (my pick, to confirm). Not near a sleeping ant (it rests inside, out of the heat).
     */
    private void tickSteam() {
        if (!isOnHotGround() || isAsleep() || random.nextInt(30) != 0) {
            return;
        }
        level().addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE,
                getX() + (random.nextDouble() - 0.5) * 0.2, getY() + getBbHeight(), getZ() + (random.nextDouble() - 0.5) * 0.2,
                0.0, 0.015, 0.0);
    }

    /**
     * Client: the edge curve (the animation engine, step 9, 2026-10-16; by distance since the review of the same day;
     * every body's own spine since 2026-10-18). Going over an edge, onto a wall or a ceiling, up a ramp or round a turn,
     * the front turns first and the rear follows the same path ("follow the leader", {@link Spine}): the front's turns
     * this tick are handed to the spine, which bends each piece behind the thorax (the ant's waist and gaster, the
     * termite's abdomen) the way the path lay under it. Where the pieces lie, the tightest curve and how far each bends
     * at most come from the body's plan ({@link BodyPlans}; the worker's numbers without one). My picks, to confirm: the
     * worker at most 60 degrees up or down and 25 to the side.
     */
    private final Spine spine = new Spine();
    /** Across an edge or a corner now: the tilt of the surface its hind feet still stand on (as the body's); else NaN. */
    private float edgeFromTilt = Float.NaN;

    private void tickWaist() {
        // On a wall the body tips about its own side-to-side line only when the head points up or down the wall (the
        // tilt turns it about the wall's level line, the heading turns it within the wall).
        float tipped = (bodyTilt - bodyTiltO) * Mth.cos(bodyHeading * Mth.DEG_TO_RAD);
        // Turning right is a growing body yaw on a floor; on a wall the heading grows turning left.
        float turned = wallPose ? -Mth.wrapDegrees(bodyHeading - bodyHeadingO) : Mth.wrapDegrees(yBodyRot - yBodyRotO);
        // Across an edge the thorax leans from the old surface to the new one; the rear behind the hind feet follows the
        // ground they walked over, the old surface until they are over the edge.
        float lean = Float.isNaN(edgeFromTilt) ? 0.0F
                : (bodyTilt - edgeFromTilt) * Mth.cos(bodyHeading * Mth.DEG_TO_RAD);
        spine.tick(tipped + (slopePitch - slopePitchO), lean, turned, moved, drawScale / 16.0, tickCount <= 2, rear, climb.tightestCurve(), climb.hindFeet());
    }

    /** Client: how far the front has tipped up past the rear (degrees, the first piece's bend; see {@link #tickWaist}). */
    public float getWaistPitch(float partialTick) {
        return spine.pitch(0, partialTick);
    }

    /** Client: how far the piece in front of this spine piece has tipped up past it (degrees, its bend). */
    public float getSpinePitch(int piece, float partialTick) {
        return spine.pitch(piece, partialTick);
    }

    /** Client: how many pieces its spine bends (for the in-game camera's log). */
    public int getSpinePieces() {
        return spine.pieces();
    }

    /** Client: how far the piece in front of this spine piece has turned right past it (degrees, its bend). */
    public float getSpineYaw(int piece, float partialTick) {
        return spine.yaw(piece, partialTick);
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

    // The body turns after the head smoothly, pivoting on its legs (the game's own rule snapped it round in jumps).
    @Override
    protected net.minecraft.world.entity.ai.control.BodyRotationControl createBodyControl() {
        return new com.antcolonies.entity.body.BodyTurn(this);
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
     * within the old one. Going over an outside edge (the top of a wall, the brink of a drop) the body is drawn across
     * the edge instead, from where it is ({@link EdgeOver}; 2026-10-16), and the usual turning takes over after.
     */
    private void turnBody() {
        bodyTiltO = bodyTilt;
        wallYawO = wallYaw;
        bodyHeadingO = bodyHeading;
        edgeShiftO = edgeShift;
        Direction surface = getSurface();
        float turn = isTurningSlowly() ? BODY_TURN_SPEED / 2.0F : BODY_TURN_SPEED;
        EdgeOver.Pose edge = overAnEdge();
        edgeFromTilt = edge != null ? edge.fromTilt() : Float.NaN;
        if (edge != null) {
            goOverEdge(edge, turn);
            return;
        }
        Vec3 lastShift = edgeShift;
        turnToSurface(surface, turn);
        fadeEdge(lastShift);
    }

    /** Client: the usual turning of the drawn body toward the synced surface and heading (see {@link #turnBody}). */
    private void turnToSurface(Direction surface, float turn) {
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
            // Tipping back onto the floor, going on the way the head pointed on the wall (2026-10-16, seen in the
            // climbing film): on a wall the game's yaw faces the wall, so one coming down head first would land facing
            // back into it and swing round as it walked off. This side's yaw is put where the drawn body goes on; the
            // game's own turns from there as usual.
            float onward = wallYaw - bodyHeading;
            setYRot(onward);
            yRotO = onward;
            yBodyRot = onward;
            yBodyRotO = onward;
            yHeadRot = onward;
            yHeadRotO = onward;
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

    /**
     * Client: the pose across an outside edge it is going over now, or round the inside corner at a wall's foot (see
     * {@link EdgeOver}), or null. Not in a channel,
     * over a doorway's rim (those tip over their own way) or peeking out of one.
     */
    @Nullable
    private EdgeOver.Pose overAnEdge() {
        if (tickCount < 3 || isInChannel() || isTurningSlowly() || isPeeking()) {
            return null;
        }
        EdgeOver.Pose edge = EdgeOver.find(level(), position(), getBbWidth(), getBbHeight(), getSurface(),
                entityData.get(DATA_HEADING), getYRot(), climb, drawScale / 16.0, drawLift, drawIntoWall);
        if (edge != null) {
            return edge;
        }
        // or round an inside corner, the foot of a wall (see EdgeOver.findCorner)
        double dx = getX() - xo;
        double dz = getZ() - zo;
        return EdgeOver.findCorner(level(), position(), getBbWidth(), getBbHeight(), getSurface(),
                entityData.get(DATA_HEADING), getYRot(), climb, drawScale / 16.0, drawLift, drawIntoWall,
                wallPose ? Direction.fromYRot(wallYaw) : null, bodyHeading, dx * dx + dz * dz > 1.0E-5);
    }

    /** Client: draws the body across the edge, as {@link EdgeOver} has it. */
    private void goOverEdge(EdgeOver.Pose edge, float turn) {
        float yaw = edge.wall().toYRot();
        if (!wallPose) {
            // From the floor (the brink of a drop ahead): begin from how the body lay there.
            wallPose = true;
            wallYaw = yaw;
            bodyHeading = Mth.wrapDegrees(yaw - floorYaw);
            wallYawO = wallYaw;
            bodyHeadingO = bodyHeading;
            bodyTiltO = edge.tilt();
        } else {
            // Another wall (lying flat on top of a thin wall, now over its far side): the same look, measured from it.
            float shift = Mth.wrapDegrees(yaw - wallYaw);
            wallYaw += shift;
            wallYawO += shift;
            bodyHeading += shift;
            bodyHeadingO += shift;
        }
        bodyHeading = approach(bodyHeading, edge.heading(), turn);
        bodyTilt = edge.tilt();
        edgeShift = edge.shift();
        edgeFading = false;
    }

    /**
     * Client: after the edge pose, the usual pose takes over. Leaving it at either end of the edge they agree; leaving it
     * part way (it turned away from the edge, or the navigator put it on a doorway's rim) what is left of the edge pose's
     * shift fades out over a few ticks, so the body never jumps.
     */
    private void fadeEdge(@Nullable Vec3 lastShift) {
        if (lastShift == null) {
            return;
        }
        Vec3 usual = usualDrawShift();
        if (usual == null) {
            edgeShift = null;
            edgeFading = false;
            return;
        }
        edgeLeft = edgeFading ? edgeLeft.scale(0.5) : lastShift.subtract(usual);
        edgeFading = true;
        if (edgeLeft.lengthSqr() < 1.0E-5) {
            edgeShift = null;
            edgeFading = false;
        } else {
            edgeShift = usual.add(edgeLeft);
        }
    }

    /**
     * Client: where the usual pose draws the middle of the feet, from the insect's position (blocks, world axes): on a
     * wall lifted up it and pushed in toward it as far as it has tipped up (see {@code AntRenderer}); null where that is
     * not worked out here (on a ceiling, in a channel).
     */
    @Nullable
    private Vec3 usualDrawShift() {
        if (!wallPose) {
            return Vec3.ZERO;
        }
        if (bodyTilt > 90.0F || isInChannel()) {
            return null;
        }
        double share = bodyTilt / 90.0;
        double yaw = wallYaw * Mth.DEG_TO_RAD;
        double into = drawIntoWall * share;
        return new Vec3(-Math.sin(yaw) * into, drawLift * share, Math.cos(yaw) * into);
    }

    /**
     * Client: where the middle of the drawn body's feet is put from its position while it goes over an outside edge
     * (blocks, world axes; see {@link EdgeOver}), or null in the usual pose.
     */
    @Nullable
    public Vec3 getEdgeShift(float partialTick) {
        if (edgeShift == null) {
            return null;
        }
        return edgeShiftO == null ? edgeShift : edgeShiftO.lerp(edgeShift, partialTick);
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
        tag.putBoolean("FoodInCrop", isFoodInCrop());
        tag.putBoolean("CarryingCorpse", isCarryingCorpse());
        tag.putBoolean("SeedForBread", seedForBread);
        if (!Float.isNaN(boldness)) {
            tag.putFloat("Boldness", boldness);
            tag.putFloat("Curiosity", curiosity);
            tag.putFloat("Restlessness", restlessness);
        }
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
        entityData.set(DATA_IN_CROP, tag.getBoolean("FoodInCrop") && !getCarriedSeed().isEmpty());
        setCarryingCorpse(tag.getBoolean("CarryingCorpse"));
        seedForBread = tag.getBoolean("SeedForBread");
        if (tag.contains("Boldness")) {
            boldness = tag.getFloat("Boldness");
            curiosity = tag.getFloat("Curiosity");
            restlessness = tag.getFloat("Restlessness");
        }
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

    /**
     * The insect's three animation players (see {@link AntAnimationController}), each moving its own bones: the body and
     * legs at the pace of its steps, the head and antennae at the insect's own pace, and the little extras (none yet:
     * added last, so it would win on the bones its clip moves). Whole-body work such as cleaning the antennae is played
     * by both the body and the head player at once, at the speed it was made.
     */
    public static final String BODY_PLAYER = "body";
    public static final String HEAD_PLAYER = "head";
    public static final String EXTRA_PLAYER = "extra";

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AntAnimationController<>(this, BODY_PLAYER, 3, this::animateBody, AntAnimationController.BODY_BONES));
        controllers.add(new AntAnimationController<>(this, HEAD_PLAYER, 3, this::animateHead, AntAnimationController.HEAD_BONES));
        controllers.add(new AntAnimationController<>(this, EXTRA_PLAYER, 3, state -> PlayState.STOP, AntAnimationController.ALL_BONES));
    }

    /** Clips asked for by the in-game camera, by name (made once each). */
    private static final Map<String, RawAnimation> FILM_CLIPS = new HashMap<>();

    /**
     * For the in-game camera ({@code client/FilmDirector}, development only): an insect named {@code film:<clip>} plays
     * only that clip, looping, so the camera can film any animation on a still insect. Null for every other insect.
     */
    @Nullable
    private RawAnimation filmClip() {
        Component name = getCustomName();
        if (name == null) {
            return null;
        }
        String text = name.getString();
        return text.startsWith("film:") ? FILM_CLIPS.computeIfAbsent(text.substring(5), c -> RawAnimation.begin().thenLoop(c)) : null;
    }

    /** For the in-game camera's walker (development only): takes away every goal, so only the camera tells it where to go. */
    public void forgetGoalsForFilm() {
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
    }

    /** For the in-game camera's log (development only): the drawn body this tick, on one line. */
    public String filmReport() {
        return String.format(java.util.Locale.ROOT,
                "pos %.3f %.3f %.3f surface %s yaw %.1f heading %.1f tilt %.1f walk %.4f moved %.4f turn %.2f turning %b waist %.1f %.1f front %.1f %.1f",
                getX(), getY(), getZ(), getSurface(), yBodyRot, bodyHeading, bodyTilt, walkSpeed, moved, turnSpeed, turning,
                spine.pitch(0, 1.0F), spine.yaw(0, 1.0F), spine.frontPitch(), spine.frontYaw())
                + (edgeShift == null ? " edge -" : String.format(java.util.Locale.ROOT, " edge %.3f %.3f %.3f", edgeShift.x, edgeShift.y, edgeShift.z));
    }

    /** An animation this insect plays instead of walking or standing (a soldier's head bang), or null. */
    @Nullable
    protected RawAnimation specialAnimation() {
        return null;
    }

    /**
     * A clip both the body and the head play whole, at the speed it was made, instead of their usual ones: the camera's
     * clip, a special one (a soldier's head bang) or the look round out of a doorway's mouth. Null for none.
     */
    @Nullable
    private RawAnimation wholeBodyClip() {
        RawAnimation film = filmClip();
        if (film != null) {
            return film;
        }
        RawAnimation special = specialAnimation();
        if (special != null) {
            return special;
        }
        return isPeeking() ? PEEK : null;
    }

    /**
     * The body player: the walk with its legs in step with the ground (the walk for its speed; on hot ground the tall
     * walk), stepping round to turn, asleep, or standing about at the insect's own pace.
     */
    private PlayState animateBody(AnimationState<AbstractAntEntity> state) {
        AntAnimationController<?> player = (AntAnimationController<?>) state.getController();
        RawAnimation whole = wholeBodyClip();
        if (whole != null) {
            player.transitionLength(3);
            player.playAt(1.0);
            return state.setAndContinue(whole);
        }
        turnReach = player.turnReach(TURN_R);
        RawAnimation clip = usualClip();
        if (clip == WALK) {
            clip = player.gearFor(WALKS, WALK, walkSpeed);
        }
        settleInto(player, clip);
        if (WALKS.contains(clip) || clip == WALK_HOT) {
            player.keepPace(walkSpeed, walkRateWithoutStride());
        } else if (clip == TURN_R || clip == TURN_L) {
            player.keepTurn(turnSpeed);
        } else if (isMadeSpeed(clip)) {
            player.playAt(1.0);
        } else {
            player.playAt(ownPace(0));
        }
        return state.setAndContinue(clip);
    }

    /**
     * The head player: the same clip as the body (its head, mandibles and antennae), but always at the insect's own pace,
     * so the antennae sweep at their own speed however fast the legs go.
     */
    private PlayState animateHead(AnimationState<AbstractAntEntity> state) {
        AntAnimationController<?> player = (AntAnimationController<?>) state.getController();
        RawAnimation whole = wholeBodyClip();
        if (whole != null) {
            player.transitionLength(3);
            player.playAt(1.0);
            return state.setAndContinue(whole);
        }
        RawAnimation clip = activityClip();
        if (clip == null) {
            clip = noticingClip(true);
        }
        if (clip == null && hasWorkClips() && hasFullMandibles() && !isAsleep()) {
            clip = CARRY;
        }
        if (clip == null) {
            clip = usualClip();
        }
        settleInto(player, clip);
        player.playAt(isMadeSpeed(clip) ? 1.0 : ownPace(1));
        return state.setAndContinue(clip);
    }

    /**
     * What the body and the head play when nothing special is going on: walking or standing, hot or not, asleep,
     * stepping round to turn, or standing still noticing something.
     */
    private RawAnimation usualClip() {
        if (isAsleep()) {
            return twitching ? SLEEP_TWITCH : SLEEP;
        }
        RawAnimation work = activityClip();
        if (work != null) {
            return work;
        }
        if (turning) {
            return turnSpeed > 0.0 ? TURN_R : TURN_L;
        }
        RawAnimation noticing = walking ? null : noticingClip(false);
        if (noticing != null) {
            return noticing;
        }
        if (isOnHotGround()) {
            return walking ? WALK_HOT : IDLE_HOT;
        }
        return walking ? WALK : IDLE;
    }

    /**
     * The clip for what it is noticing, or null for none. The head keeps it while the legs walk ({@code head}: a bold ant
     * walking closer with its antennae out, a shy one backing off with its jaws open).
     */
    @Nullable
    private RawAnimation noticingClip(boolean head) {
        return switch (getNoticing()) {
            case NOTICE_LOOK -> LOOK;
            case NOTICE_WARY -> LOOK_WARY;
            case NOTICE_FREEZE -> head || !walking ? FREEZE : null;
            default -> null;
        };
    }

    /** The clip for the body work it is doing (cleaning an antenna, digging), or null for none (or no such clips). */
    @Nullable
    private RawAnimation activityClip() {
        if (!hasWorkClips()) {
            return null;
        }
        return switch (getActivity()) {
            case ACTIVITY_GROOM_L -> GROOM_L;
            case ACTIVITY_GROOM_R -> GROOM_R;
            case ACTIVITY_GROOM_BOTH -> GROOM_BOTH;
            case ACTIVITY_DIG -> DIG;
            default -> null;
        };
    }

    /**
     * Whether its animation file has the work clips (cleaning, digging, carrying): the ant model's does (made by
     * tools/ant_models.py); the termites' do not yet, so they keep their usual clips while they work. (Turning on the
     * spot goes by its own clip's reach note, {@link #turnReach}.)
     */
    protected boolean hasWorkClips() {
        return false;
    }

    /** Whether this clip plays at the speed it was made (a piece of work played once), not at the insect's own pace. */
    private static boolean isMadeSpeed(RawAnimation clip) {
        return clip == GROOM_L || clip == GROOM_R || clip == GROOM_BOTH;
    }

    /**
     * Into a nap and out of it the pose blends slowly (a real ant settles down and gets up), into a look and out of it a
     * little faster; otherwise quickly.
     */
    private static void settleInto(AntAnimationController<?> player, RawAnimation clip) {
        RawAnimation now = player.getCurrentRawAnimation();
        boolean napping = clip == SLEEP || clip == SLEEP_TWITCH || now == SLEEP || now == SLEEP_TWITCH;
        boolean looking = clip == LOOK || clip == LOOK_WARY || clip == FREEZE || now == LOOK || now == LOOK_WARY || now == FREEZE;
        boolean working = isMadeSpeed(clip) || isMadeSpeed(now);
        player.transitionLength(napping ? SETTLE_TICKS : looking ? LOOK_SETTLE_TICKS : working ? GROOM_SETTLE_TICKS : 3);
    }

    /**
     * Client: how much the head is turned toward what it looks at (0 to 1; see {@code client.InsectModel}): it eases in
     * when the insect starts looking at a player and out when it stops, so the head never snaps.
     */
    private float lookWeight;
    private float lookWeightO;

    private void tickLookWeight() {
        lookWeightO = lookWeight;
        byte noticing = getNoticing();
        boolean turnsHead = (noticing == NOTICE_LOOK || noticing == NOTICE_WARY) && !isClimbing();
        lookWeight = approach(lookWeight, turnsHead ? 1.0F : 0.0F, 0.2F);
    }

    /** Client: how much the head turns toward what it looks at, this frame (0 to 1). */
    public float getLookWeight(float partialTick) {
        return Mth.lerp(partialTick, lookWeightO, lookWeight);
    }

    /**
     * Client: a nap is lying still, then a short bout of antenna twitches (the "rapid antennal movement" of sleeping ants,
     * Cassill et al. 2009), by turns: still stretches of 15 to 25 seconds, twitch bouts of 6 (one play of the twitch
     * clip; my picks, to confirm). The ticks left of the present stretch, whether it is a twitch bout, and whether a nap
     * is under way.
     */
    private int napTicks;
    private boolean twitching;
    private boolean dozing;
    private static final int TWITCH_TICKS = 120;

    private void tickNap() {
        if (!isAsleep()) {
            dozing = false;
            twitching = false;
            return;
        }
        if (!dozing) {
            dozing = true;
            twitching = false;
            napTicks = 300 + getRandom().nextInt(201);
        } else if (--napTicks <= 0) {
            twitching = !twitching;
            napTicks = twitching ? TWITCH_TICKS : 300 + getRandom().nextInt(201);
        }
    }

    /**
     * How fast a walk without a stride note plays (today's termite walks, not made by the motion library yet): the old
     * guess from the speed, so their legs roughly keep up.
     */
    private double walkRateWithoutStride() {
        if (isClimbing()) {
            // The legs keep pace with the body: slower when it climbs slowly (poking its head out of a chimney).
            return Mth.clamp(walkSpeed / CLIMB_STEP * 1.5, 0.4, 1.5);
        }
        return Math.max(0.6, Math.min(walkSpeed * 4.0, 1.0) * 6.0);
    }

    /**
     * This insect's own pace for what is not tied to its steps (standing about, the head and antennae): 0.88 to 1.12
     * times as made, the same all its life (from its id; {@code which} picks one of several), so no two insects move in
     * step (my pick, to confirm).
     */
    private double ownPace(int which) {
        long bits = getUUID().getMostSignificantBits() >>> (which * 16) & 0xFFFF;
        return 0.88 + 0.24 * bits / 65535.0;
    }

    /** Client: how far it moves a tick (blocks; along a wall too while climbing), smoothed over a few ticks. */
    private double walkSpeed;
    /** Client: whether it is walking (its legs step), from {@link #walkSpeed}, with a gap so it never flickers between. */
    private boolean walking;
    /** Client: how big the renderer draws it (its scale), for the stride (set by {@code AntRenderer}). */
    private float drawScale = 1.0F;
    /** Client: the renderer's climbing lift and push into a wall (blocks; set by {@code AntRenderer}). */
    private double drawLift;
    private double drawIntoWall;
    /** Client: its body's climbing numbers and its spine's pieces (its body plan's; set by {@code AntRenderer}). */
    private BodyPlans.Climb climb = BodyPlans.WORKER_CLIMB;
    private List<BodyPlans.Piece> rear = List.of(BodyPlans.WORKER_WAIST);
    /** Walking starts above this speed and stops below the lower one (blocks a tick; my picks, to confirm). */
    private static final double START_WALKING = 0.008;
    private static final double STOP_WALKING = 0.004;
    /** Client: how far it moved this tick (blocks; along a wall too while climbing). */
    private double moved;
    /** Client: how fast the drawn body turns (degrees a tick, positive to the right), smoothed like {@link #walkSpeed}. */
    private double turnSpeed;
    /**
     * Client: whether its legs step round to turn where it stands (2026-10-16, the review's third fix): real ants step
     * to turn, their feet never skate round. It steps when the body turns faster than {@link #START_TURNING} (until it
     * is below {@link #STOP_TURNING}) and the turn moves the feet more than walking does (curving while walking, the walk
     * steps). Not on hot ground (the tall stance has no turning clip). Any model whose file has the turning clips (the
     * ant's, and the termites' since 2026-10-18).
     */
    private boolean turning;
    /** Degrees a tick (40 and 20 a second; my picks, to confirm). */
    private static final double START_TURNING = 2.0;
    private static final double STOP_TURNING = 1.0;
    /**
     * Client: how far the outer feet of its model stand from the body's middle while turning (model units), from the
     * turning clip's {@code reach} note (each model's own legs); NaN until the model is known, or if it has no turning clip.
     */
    private double turnReach = Double.NaN;

    /**
     * Client, once a tick: how fast it moves, for the legs (see {@link AntAnimationController}). On a floor only its
     * movement over the ground counts (a falling ant does not walk); on a wall or a ceiling its movement along it.
     */
    private void measureWalk() {
        double dx = getX() - xo;
        double dy = isClimbing() ? getY() - yo : 0.0;
        double dz = getZ() - zo;
        moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        walkSpeed += (moved - walkSpeed) * 0.4;
        walking = walkSpeed > (walking ? STOP_WALKING : START_WALKING);
        // Turning: the drawn body's turn this tick (right is a growing body yaw on a floor; on a wall the heading grows
        // turning left). Its feet step round when the turn would move them over the ground faster than the walk does.
        double turned = wallPose ? -Mth.wrapDegrees(bodyHeading - bodyHeadingO) : Mth.wrapDegrees(yBodyRot - yBodyRotO);
        turnSpeed += (turned - turnSpeed) * 0.4;
        double feet = Math.abs(turnSpeed) * Mth.DEG_TO_RAD * turnReach / 16.0 * drawScale;
        turning = !Double.isNaN(turnReach) && !isOnHotGround() && feet > walkSpeed
                && Math.abs(turnSpeed) > (turning ? STOP_TURNING : START_TURNING);
    }

    /** Client: how big the renderer draws it (1 = the model's own size). */
    public float getDrawScale() {
        return drawScale;
    }

    /** Client: set by the renderer each frame (its scale), so the stride is known in blocks. */
    public void setDrawScale(float scale) {
        drawScale = scale;
    }

    /**
     * Client: set by the renderer each frame, how far its climbing pose lifts the drawn body up a wall and pushes it in
     * toward it (blocks), for drawing it over an edge ({@link EdgeOver}).
     */
    public void setClimbPose(double lift, double intoWall) {
        drawLift = lift;
        drawIntoWall = intoWall;
    }

    /**
     * Client: set by the renderer each frame, the body plan of the model it is drawn with (null: none, the worker's
     * numbers), for the waist's bend and for drawing it over an edge.
     */
    public void setBodyPlan(@Nullable BodyPlans.Plan plan) {
        climb = plan != null ? plan.climb() : BodyPlans.WORKER_CLIMB;
        rear = plan != null && !plan.spine().isEmpty() ? plan.spine() : List.of(BodyPlans.WORKER_WAIST);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
