package com.antcolonies.savanna.animal;

import com.antcolonies.entity.body.SteadyController;
import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Carried;
import com.antcolonies.animal.GuardPlaceGoal;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.LifeStage;
import com.antcolonies.animal.SeekMateGoal;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
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
public class CrocodileEntity extends PathfinderMob implements GeoEntity, HasLife {
    private static final EntityDataAccessor<Boolean> DATA_BASKING = SynchedEntityData.defineId(CrocodileEntity.class, EntityDataSerializers.BOOLEAN);
    /** What its jaws are doing in a hunt: {@link #JAWS_NONE}, gaping in the lunge, clamped on its prey, or tearing at a kill. */
    private static final EntityDataAccessor<Byte> DATA_JAWS = SynchedEntityData.defineId(CrocodileEntity.class, EntityDataSerializers.BYTE);
    /** The game tick a death roll began (cut to an int), or {@link #NO_ROLL}. */
    private static final EntityDataAccessor<Integer> DATA_ROLL = SynchedEntityData.defineId(CrocodileEntity.class, EntityDataSerializers.INT);
    /** What it shows in its breeding ({@link CrocodileBreeding}): courting, hissing at an intruder, digging, carrying young. */
    private static final EntityDataAccessor<Byte> DATA_SHOW = SynchedEntityData.defineId(CrocodileEntity.class, EntityDataSerializers.BYTE);
    public static final byte SHOW_NONE = 0, SHOW_SLAP = 1, SHOW_RUMBLE = 2, SHOW_SNOUT_UP = 3, SHOW_ROLL = 4, SHOW_HISS = 5, SHOW_DIG = 6, SHOW_CARRY = 7;
    public static final byte JAWS_NONE = 0, JAWS_GAPE = 1, JAWS_HOLD = 2, JAWS_EAT = 3, JAWS_DRAG = 4, JAWS_SNAP_LEFT = 5, JAWS_SNAP_RIGHT = 6, JAWS_SHAKE = 7;
    static final int NO_ROLL = Integer.MIN_VALUE;

    /**
     * The hunt (2026-11-15, the user's ask, my picks; fun first, from what real crocodiles do): a hungry crocodile in the
     * water watches for an animal or a player at the water's edge within {@link #HUNT_REACH} blocks, creeps up with only
     * its eyes out, and from {@link #LUNGE_REACH} blocks lunges and grabs it ({@link HuntGoal}).
     */
    static final double HUNT_REACH = 14.0, LUNGE_REACH = 3.5;
    /**
     * Big prey on the bank (2026-11-15, the user's asks; my picks): from this far it bursts out of the water and rushes
     * it at this pace, striking from this near; then it hauls the animal back to the water, heave by heave.
     */
    static final double CHARGE_REACH = 6.0, CHARGE_PACE = 1.7, STRIKE_REACH = 1.4;
    /**
     * One big animal in four slips free while it is hauled down the bank (the user's number and pick, 2026-11-15, in
     * place of a strike that misses): after 1 to 4 heaves (or at the water's edge, if it is there sooner) it tears loose and bolts, and the crocodile goes back into the water.
     */
    static final float SLIP_SHARE = 0.25F;
    /**
     * The tug of war on the bank: one heave every this many ticks. Of 20 heaves about 12 gain ground (this far for a
     * cow-sized animal, less for a heavier one), 5 gain nothing (the animal digs in), 3 lose ground (it drags the
     * crocodile back up the bank). Once the animal is in the water the crocodile has the better of it and pulls steadily.
     */
    public static final int HEAVE_TICKS = 16;
    static final double HEAVE_GAIN = 0.75, HEAVE_LOST = 0.3, HAUL_AFLOAT = 0.07;
    /** How hard the grab bites, and how hard each death roll tears at an animal and at a player (hearts x 2; my picks: a player lasts about 4 rolls). */
    static final float BITE = 2.0F, ROLL_HURT = 6.0F, ROLL_HURT_PLAYER = 4.0F;
    /** How much its prey must hurt it to make it let go (my pick: a few good hits; a player can fight free). */
    static final float ESCAPE_HURT = 8.0F;
    /** One death roll: this many ticks, this many full turns, then a pause under the water before the next (my picks: 3 turns in 2 s). */
    public static final int ROLL_TICKS = 40, ROLL_TURNS = 3, ROLL_PAUSE = 30;
    /** How high its spine is, the line it rolls round (blocks, at size 1: the body bone's pivot, 6 model pixels). */
    public static final double ROLL_AXIS = 6.0 * CrocodileEntity.MODEL_SCALE / 16.0;
    /**
     * How far ahead of its position its jaws reach (blocks, at size 1: the snout's tip, 40 model pixels; the position is at
     * the front of its hips, model z 0). Was 20 pixels from the first crocodile on, so it bit from the back of its head
     * (BUGS 283, 2026-11-22).
     */
    public static final double JAWS_AHEAD = 40.0 * CrocodileEntity.MODEL_SCALE / 16.0;
    /** Where it holds what it carries: between its teeth, the model's {@code carried} socket (30 model pixels ahead). */
    public static final double HOLD_AHEAD = 30.0 * CrocodileEntity.MODEL_SCALE / 16.0;
    /** How long it stays fed after a big kill and after something small (ticks; my picks: a day, half a day). */
    static final int FED_BIG = 24000, FED_SMALL = 12000;
    /**
     * Hunting fish (the crocodile's life step 4, 2026-11-15, the user's ask; my picks, fun first): wild tilapia and
     * catfish in the water are prey too, tilapia swallowed whole, a catfish torn in two (the front half left as remains). A tilapia feeds it 5 minutes, a catfish half a day (as
     * anything small). With the river fish group refilling to 60 round a player and crocodiles rare, the river is not
     * fished out.
     */
    static final int FED_TILAPIA = 6000;
    /**
     * The young's first meals (2026-11-20, the barb's plan; my picks): a hatchling snaps at a barb passing within this
     * many blocks of it (it keeps to its crèche shallows), a teen hunts barbs with the other fish; a grown crocodile
     * ignores barbs (too small to bother). A barb feeds a young one as anything small does (10 minutes).
     */
    static final double YOUNG_HUNT_REACH = 4.0;
    /** How long it whips a catfish from side to side before it tears in two (ticks: 2 seconds, six shakes; my pick). */
    static final int TEAR_TICKS = 40;
    /** With a catfish and a tilapia both in reach, the chance it goes for the catfish, the bigger meal (the user, 2026-11-15). */
    static final float PREFERS_CATFISH = 0.6F;
    /**
     * The sideways snap at a fish beside its head: the head and neck swing round from the shoulders (the neck's joint, this
     * far ahead of its position; was 6 pixels behind it, BUGS 283) by this much, the snout's tip this far from the shoulders, and the jaws slam shut on this tick of the
     * snap's {@link #SNAP_TICKS} (the snap_left and snap_right clips: shut at 0.2 s). A fish more than this far off its
     * nose (degrees) is snapped at sideways; nearer the nose it lunges straight at it.
     */
    static final double NECK_AHEAD = 14.0 * CrocodileEntity.MODEL_SCALE / 16.0, HEAD_REACH = 26.0 * CrocodileEntity.MODEL_SCALE / 16.0, SNAP_SWING = 54.0, SNAP_FROM_NOSE = 25.0;
    static final int SNAP_TICKS = 10, SNAP_CATCH = 4;
    /** How often the fish darts away just as the jaws come (my pick: about 1 strike in 3 misses), and how many misses before it gives up a while. */
    static final float FISH_DODGE = 0.35F;
    static final int FISH_MISSES = 3;
    /**
     * Crowding round a school (real crocodiles gather where fish run): a strike at a fish calls every hungry crocodile in
     * the water within this many blocks to the place for 15 s; they hunt the fish round it (within {@link #SCHOOL_REACH}).
     */
    static final double CALL_REACH = 24.0, SCHOOL_REACH = 10.0;
    static final int CALL_TICKS = 300;

    /** How deep in the water it hangs when resting: the water this far up its box, so the brows and back are out (blocks, at the usual size). */
    private static final double AFLOAT = 0.462 * CrocodileEntity.MODEL_SCALE;
    /** How deep it sinks while startled in the water, out of sight (blocks, at the usual size; my pick). */
    private static final double HIDE_DEPTH = 1.54 * CrocodileEntity.MODEL_SCALE;
    /** How long a fright lasts (ticks; my pick, 5 s). */
    static final int STARTLED_TICKS = 100;
    /** How near a player may come before it flees, and one creeping up crouched (blocks; my picks). */
    static final double WARY_REACH = 6.0, SNEAK_REACH = 2.5;
    /** How often, wanting the sun, it rests half out on the bank in place of climbing out (my pick). */
    static final float HALF_OUT_SHARE = 0.4F;
    /** The rush to the water and the lunge in ({@link PlungeGoal}; my picks: about 3 blocks through the air). */
    static final double RUSH = 1.9, LUNGE_FORWARD = 0.45, LUNGE_UP = 0.2;
    /**
     * How the model is drawn: 6.75 blocks long as made (snout to tail tip, 108 pixels), drawn so a usual crocodile is
     * 7.5 blocks long (the user, 2026-11-17; was 6.1, and 5 before the longer tail of 2026-11-15). Every distance on the
     * body (jaws, head, roll, how deep it floats, the walk's stride) is worked out from this, so it is the one size knob.
     */
    public static final float MODEL_SCALE = 7.5F / 6.75F;
    /** How long it gapes facing down a bull shark that warned it (ticks: about the shark's own warning; my pick). */
    static final int SHARK_STANDOFF = 50;
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
    /**
     * How far off something catches its eye (2026-11-15, the user's ask; my picks): a player within 16 blocks (6 crouched),
     * any other animal stirring within 10; it keeps its eyes on one for 2 to 5 s before it looks round for another.
     */
    static final double EYE_REACH = 16.0, EYE_REACH_SNEAK = 6.0, STIR_REACH = 10.0;
    /**
     * Mostly solitary, basking together (2026-11-15, the user's ask): a crocodile spawns only this many blocks or more
     * from another (each keeps its own stretch of river), but wanting the sun it joins crocodiles already basking within
     * {@link #JOIN_REACH} blocks, lying beside one {@link #SIDE_BY_SIDE} blocks off and facing the same way, so the
     * favourite banks gather rows of them, as real Nile crocodiles do (my picks).
     */
    public static final double SPAWN_APART = 24.0, JOIN_REACH = 48.0, SIDE_BY_SIDE = 1.87 * MODEL_SCALE;
    /**
     * Sand to bask on (sand banks, 2026-11-16, {@code docs/plans/sand-banks.md}): with no company basking near, a
     * crocodile wanting the sun looks for a river sand bank within this many blocks before it takes the nearest bank, so
     * the sand bars on the inside of the bends gather two or three of them, as real Nile crocodiles crowd the sand bars.
     * It looks at most once a minute ({@link #SAND_LOOK_WAIT}, ticks) and keeps going back to the sand it found (my picks).
     */
    public static final int SAND_REACH = 64, SAND_LOOK_WAIT = 1200;
    /** Its eyes and head turned toward what caught its eye, eased (degrees off straight ahead, its right positive); drawn only (the model sets them). */
    public double eyeAim, headAim;
    public long eyeNanos;
    /** Its blinks (2026-11-17, the user's ask), drawn only: when the last blink began and the next is due (nano time), and whether its eyes were under water last frame (it blinks as they go under). */
    public long blinkStart, blinkNext;
    public boolean eyesWereUnder;
    /** What its eyes are on (worked out where it is drawn: the client only), and how many ticks they stay on it at least. */
    @Nullable
    private Entity eyesOn;
    private int eyesHeld;
    /** With nothing to watch, where its eyes wander (degrees off straight ahead) and how many ticks till they move again. */
    private float glance;
    private int glanceIn;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The body lying on what is under it (worked out where it is drawn, each frame). */
    private final GroundBend bend = new GroundBend(FRONT_JOINTS, REAR_JOINTS);
    /** Its own length against the usual crocodile. */
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(CrocodileEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    /** Ticks until it next wants the sun (while it does not, a crocodile on land goes back to the water). */
    public int sunIn;
    /** Ticks it is still startled for: on land it rushes for the water and lunges in, in the water it sinks out of sight. */
    public int startled;
    /** Whether this time it wants the sun it rests half out, head and chest on the bank and its back half afloat, in place of climbing out. */
    boolean restHalfOut;
    /** Ticks until it is hungry again (0: hungry), and ticks before it hunts again after a miss or an escape. */
    public int hungerIn;
    int huntWait;
    /** What its jaws hold now (the hunt's prey, riding it so it is carried along), and how much that hold has cost it. */
    @Nullable
    private LivingEntity held;
    private float heldHurt;
    /** The height its held prey is carried at, eased so the prey slides down a bank's edge and does not jump (worked out on both sides). */
    private double heldY = Double.NaN;
    /** Whether it is in a hunt now (a player near does not frighten it off the bank then). */
    boolean hunting;
    /** Where a nearby crocodile struck at fish (it heads there to hunt them too), and for how many ticks more. */
    @Nullable
    Vec3 schoolAt;
    int schoolFor;
    /** The spot beside a basking crocodile it is going to lie at, and the way it will face there (null: none). */
    @Nullable
    Vec3 joinAt;
    float joinFace;
    /** The spot on a river sand bank it goes to bask at (null: none known), and ticks before it may look for sand again. */
    @Nullable
    public BlockPos sandAt;
    int sandLookIn;
    /** For tests and films: 1 its hauled prey never slips free, -1 it always does, 0 the dice ({@link #SLIP_SHARE}). */
    public byte strikeLuck;
    /** For tests: how many animals have got away from it (slipped free of the haul, or a strike that fell short). */
    public int misses;
    /**
     * Its breeding (animal life cycle step 4, {@link CrocodileBreeding}): a female ripe with eggs to lay; her nest; the
     * nest her young hatched from (while she fetches them); the crèche in the shallows and when her care ends (a young one
     * keeps these too); a young one's mother and how long it still waits in the nest for her; the young in her mouth.
     */
    public boolean gravid;
    @Nullable
    BlockPos nestAt, hatchSite;
    @Nullable
    Vec3 crecheAt;
    long crecheUntil;
    @Nullable
    java.util.UUID motherId;
    int waitForMother;
    final Carried carried = new Carried();
    /** For tests: how many times it has bitten something it saw off its nest or crèche. */
    public int guardBites;
    /** Ticks it still gapes at a rival male. */
    private int gapeFor;

    /**
     * Its hit boxes along the drawn body ({@link com.antcolonies.animal.HitChain}, 2026-11-22; my picks measured on its
     * model): its position is at the front of its hips, so the snout's tip is 2.78 ahead and the tail's 4.72 behind
     * (model pixels times MODEL_SCALE); the snout, the head, the chest, the hips and six beads down the tail, thinning
     * toward its tip: its walking box is only the middle of a body 7.5 blocks long, so a swing or an arrow at what you
     * see lands.
     */
    private final com.antcolonies.animal.HitChain chain = new com.antcolonies.animal.HitChain(this,
            new com.antcolonies.animal.HitChain.Link("snout", 2.45, 0.05, 0.55F, 0.4F),
            new com.antcolonies.animal.HitChain.Link("head", 1.75, 0.1, 0.75F, 0.5F),
            new com.antcolonies.animal.HitChain.Link("chest", 0.75, 0, 0.85F, 0.6F),
            new com.antcolonies.animal.HitChain.Link("hips", -0.75, 0, 0.85F, 0.6F),
            new com.antcolonies.animal.HitChain.Link("tail1", -1.5, 0, 0.75F, 0.45F),
            new com.antcolonies.animal.HitChain.Link("tail2", -2.2, 0, 0.65F, 0.4F),
            new com.antcolonies.animal.HitChain.Link("tail3", -2.85, 0, 0.58F, 0.36F),
            new com.antcolonies.animal.HitChain.Link("tail4", -3.42, 0, 0.5F, 0.32F),
            new com.antcolonies.animal.HitChain.Link("tail5", -3.93, 0, 0.45F, 0.28F),
            new com.antcolonies.animal.HitChain.Link("tail6", -4.4, 0, 0.4F, 0.25F));

    public CrocodileEntity(EntityType<? extends CrocodileEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.55F, false);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        sunIn = level.random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, Math.max(JOIN_REACH, SAND_REACH)); // routes as far as its company or the sand
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(-1, new CrocodileBreeding.ChaseOffGoal(this));
        goalSelector.addGoal(0, new GuardPlaceGoal(this, this::guardPlace, CrocodileBreeding.GUARD_STAY, CrocodileBreeding.GUARD_SEE_OFF,
                e -> CrocodileBreeding.intruder(this, e), 1.0));
        goalSelector.addGoal(0, new CrocodileBreeding.HeldForMateGoal(this));
        goalSelector.addGoal(0, new CrocodileBreeding.WaitForMotherGoal(this));
        goalSelector.addGoal(0, new PlungeGoal(this));
        // hippo bulls see off crocodiles that come right up to them (2026-11-21, "River zones"): it swims clear (BUGS 272),
        // over its plunge: bitten on a bank, a plunge into the nearest water took it straight back beside the bull
        goalSelector.addGoal(-1, new KeepClearGoal(this));
        goalSelector.addGoal(1, new CrocodileBreeding.MotherGoal(this));
        goalSelector.addGoal(1, new SeekMateGoal<>(this, 1.0));      // over the sun and the wandering: a courtship is not left for a bask
        goalSelector.addGoal(1, new BaskGoal(this));
        goalSelector.addGoal(1, new HuntGoal(this));
        goalSelector.addGoal(0, new CrocFlySnapGoal(this));        // the young snap up landed flies (the river food web, 2026-11-23; over a bask and the crèche, never over a hunt)
        goalSelector.addGoal(2, new ToWaterGoal(this));
        goalSelector.addGoal(3, new HalfOutGoal(this));
        goalSelector.addGoal(3, new AshoreGoal(this));
        goalSelector.addGoal(2, new GuardPlaceGoal(this, this::crecheSpot, CrocodileBreeding.CRECHE_STAY, 0.0, e -> false, 1.0));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 60));
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_BASKING, false);
        builder.define(DATA_JAWS, JAWS_NONE);
        builder.define(DATA_ROLL, NO_ROLL);
        builder.define(DATA_SHOW, SHOW_NONE);
    }

    /** What it shows now in its breeding ({@link #SHOW_NONE}: nothing). */
    public byte show() {
        return entityData.get(DATA_SHOW);
    }

    void setShow(byte show) {
        if (show() != show) {
            entityData.set(DATA_SHOW, show);
        }
    }

    /** Gapes at a rival male a moment. */
    void gape(int ticks) {
        if (held == null && !hunting) {
            setJaws(JAWS_GAPE);
            gapeFor = ticks;
        }
    }

    // --- breeding (animal life cycle step 4, CrocodileBreeding) -----------------------------------------------------------

    /** A male goes looking for a mate in the water; a female waits there for one (and not while she has eggs or young to see to). */
    @Override
    public boolean seeksMate() {
        return life.isMale() && isInWater() && startled <= 0;
    }

    @Override
    public boolean takesMate() {
        return isInWater() && (life.isMale() || !busyMother());
    }

    @Override
    public void courting(net.minecraft.world.entity.Mob mate, int ticks) {
        CrocodileBreeding.court(this, mate, ticks);
    }

    @Override
    public void mated(net.minecraft.world.entity.Mob mate) {
        if (!life.isMale()) {
            gravid = true;
        }
    }

    /** Her nest, if she has one. */
    @Nullable
    public BlockPos nestAt() {
        return nestAt;
    }

    /** For tests: the crèche she keeps her young in, or a young one keeps to (null: none now). */
    @Nullable
    public Vec3 crecheAt() {
        return crecheAt != null && level().getGameTime() < crecheUntil ? crecheAt : null;
    }

    /** For tests: how many young she carries in her mouth now. */
    public int carrying() {
        return carried.count();
    }

    /** For tests: this young one's mother. */
    @Nullable
    public java.util.UUID motherId() {
        return motherId;
    }

    /**
     * Whether she has work at her nest now (eggs to lay, a nest to cover or dig open, young to carry or fetch): she does
     * not lie down to bask then.
     */
    boolean motherWork() {
        if (gravid || !carried.isEmpty() || hatchSite != null) {
            return true;
        }
        if (nestAt == null) {
            return false;
        }
        var state = level().getBlockState(nestAt);
        return state.getBlock() instanceof com.antcolonies.savanna.block.CrocodileNestBlock
                && (state.getValue(com.antcolonies.savanna.block.CrocodileNestBlock.OPEN) || com.antcolonies.savanna.block.CrocodileNestBlock.hatching(state));
    }

    /** She guards a nest here (one laid by a test, or by new land's nesting beaches, {@code worldgen/CrocodileNestFeature}). */
    public void guardNest(BlockPos nest) {
        nestAt = nest;
        gravid = false;
    }

    /** Whether she has eggs to lay, a nest, young in her mouth or a crèche to see to. */
    boolean busyMother() {
        return gravid || nestAt != null || !carried.isEmpty() || hatchSite != null || guardPlace() != null;
    }

    /** Just hatched from a mother's nest: it waits in the nest for her (none: it makes for the water alone). */
    public void hatchedBy(@Nullable CrocodileEntity mother) {
        if (mother != null) {
            motherId = mother.getUUID();
            waitForMother = CrocodileBreeding.YOUNG_WAIT;
        }
    }

    /**
     * Hatchlings in her mouth come out where she is if she is killed or taken out of the world (as the tilapia's fry do),
     * on their own from then on; never on a plain unload, which saves them with her (code review 2026-11-21: they were
     * deleted with her).
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && !carried.isEmpty() && level() instanceof net.minecraft.server.level.ServerLevel server
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            Vec3 at = server.getFluidState(BlockPos.containing(jawPoint())).is(FluidTags.WATER) ? jawPoint() : position().add(0, 0.2, 0);
            for (Entity e : carried.letOut(server, at)) {
                if (e instanceof CrocodileEntity young) {
                    young.waitForMother = 0;
                }
            }
        }
        super.remove(reason);
    }

    /** Let out of its mother's mouth into her crèche: it keeps near it while she cares for it. */
    void joinCreche(CrocodileEntity mother) {
        motherId = mother.getUUID();
        crecheAt = mother.crecheAt;
        crecheUntil = mother.crecheUntil;
        waitForMother = 0;
    }

    /** What a grown female guards now: her nest, else her crèche while her care lasts (null: nothing). */
    @Nullable
    public BlockPos guardPlace() {
        if (life.isMale() || !life.stage().grown()) {
            return null;
        }
        if (nestAt != null) {
            return nestAt;
        }
        return crecheAt != null && level().getGameTime() < crecheUntil ? BlockPos.containing(crecheAt) : null;
    }

    /** A young one's resting spot in the reeds by its crèche, the crèche it was found for, and when it looks again. */
    @Nullable
    private BlockPos crecheRest;
    @Nullable
    private Vec3 crecheRestFor;
    private long crecheRestLook;
    /** How often a young one looks again for reeds by its crèche (ticks: a reed bed broken or grown since). */
    static final int CRECHE_REST_LOOK = 600;

    /**
     * The spot a young one keeps to while its mother cares for it (null: none): the crèche, or cover by it (the food
     * web's step 4, 2026-11-23): with reeds or a hippo grass mat within her see-off reach of the crèche
     * ({@link CrocodileBreeding#GUARD_SEE_OFF}, so they stay where she guards them) the young rest among the stalks.
     * Nests are on sand banks, where no reeds grow, so this happens only where a bank and a reed bed meet.
     */
    @Nullable
    private BlockPos crecheSpot() {
        if (!life.isYoung() || crecheAt == null || level().getGameTime() >= crecheUntil) {
            return null;
        }
        BlockPos at = BlockPos.containing(crecheAt);
        if (crecheRestFor != crecheAt || level().getGameTime() >= crecheRestLook) {
            crecheRestFor = crecheAt;
            crecheRestLook = level().getGameTime() + CRECHE_REST_LOOK;
            crecheRest = RiverCover.nearest(level(), at, (int) CrocodileBreeding.GUARD_SEE_OFF);
        }
        return crecheRest != null ? crecheRest : at;
    }

    /** For tests: a young one keeps to a crèche here for this long (ticks), as if let out of its mother's mouth there. */
    public void keepCreche(Vec3 at, int ticks) {
        crecheAt = at;
        crecheUntil = level().getGameTime() + ticks;
    }

    /** For tests: where a young one rests by its crèche (the crèche itself with no reeds near; null: no crèche). */
    @Nullable
    public BlockPos crecheRestSpot() {
        return crecheSpot();
    }

    /**
     * Its walking pace by its size: a crocodile under 0.7 of the usual size (the young and the teens) walks and swims
     * slower, by the root of how much smaller it is (a hatchling about a third as fast); grown ones are as before. A
     * mob's speed setting counts twice in Minecraft (its move control sets both the push and the stride from it), so its
     * pace goes as the setting squared: the setting is the root of the pace (2026-11-20: set to the pace itself, a
     * hatchling crawled at a tenth of a grown one's pace and was drawn standing while it slid along).
     */
    /** Growing up (its life's clock is counted by the kit, {@code AnimalLife.tickEvery}). */
    @Override
    public void grew() {
        sizeChanged();
    }

    private void sizeChanged() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.3 * Math.sqrt(sizePace()));
        }
    }

    /** How grown its body is: its size against the smallest grown one (0.7 of the usual size), at most 1. */
    public double growth() {
        return Math.min(1.0, getScale() / 0.7);
    }

    /** How fast it moves against a grown crocodile (1 at 0.7 of the usual size and over). */
    double sizePace() {
        return Math.sqrt(growth());
    }

    /** Whether it is sunbathing: lying on the bank with its head raised and its mouth open. */
    public boolean isBasking() {
        return entityData.get(DATA_BASKING);
    }

    void setBasking(boolean basking) {
        entityData.set(DATA_BASKING, basking);
    }

    public byte jaws() {
        return entityData.get(DATA_JAWS);
    }

    void setJaws(byte jaws) {
        entityData.set(DATA_JAWS, jaws);
    }

    /**
     * How far round its spine it has turned in the death roll now (degrees; 0 when not rolling): three full turns in
     * two seconds, gathering speed and slowing to a stop, so it always ends the right way up. Read where it is drawn
     * (the model, its prey, and a held player's camera all turn by it).
     */
    public float rollAngle(float partialTick) {
        int start = entityData.get(DATA_ROLL);
        if (start == NO_ROLL) {
            return 0.0F;
        }
        float t = ((int) level().getGameTime() - start + partialTick) / ROLL_TICKS;
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float eased = t * t * (3.0F - 2.0F * t);
        return 360.0F * ROLL_TURNS * eased;
    }

    void startRoll() {
        entityData.set(DATA_ROLL, (int) level().getGameTime());
    }

    void endRoll() {
        entityData.set(DATA_ROLL, NO_ROLL);
    }

    public boolean isHungry() {
        return hungerIn <= 0;
    }

    /** What its jaws hold now, if anything. */
    @Nullable
    public LivingEntity held() {
        return held;
    }

    /** For tests and films: hungry now, and ready to hunt at once. */
    public void makeHungry() {
        hungerIn = 0;
        huntWait = 0;
    }

    /** For tests: hungry but not hunting for this many ticks (it still answers another's call to a school). */
    public void holdOffHunting(int ticks) {
        hungerIn = 0;
        huntWait = ticks;
    }

    /** For tests: whether another crocodile's strike at fish has called it to the school. */
    public boolean calledToSchool() {
        return schoolAt != null;
    }

    /** For tests: whether it holds its jaws on this (the dismount check reads it too). */
    public boolean holds(Entity entity) {
        return held != null && held == entity;
    }

    /** Clamps its jaws on its prey: the prey rides it from now on, carried in its mouth wherever it goes. */
    boolean grab(LivingEntity prey) {
        if (!prey.startRiding(this, true)) {
            return false;
        }
        held = prey;
        heldHurt = 0.0F;
        setJaws(JAWS_HOLD);
        BloodMist.puff(prey);       // seized in the water: a red cloud round the jaws (2026-11-23)
        return true;
    }

    /** Lets go of what it holds (the hold is over: escaped, eaten, or it was startled off). */
    void release() {
        LivingEntity was = held;
        held = null;
        endRoll();
        if (was != null && was.getVehicle() == this) {
            was.stopRiding();
        }
    }

    /** Nobody steers a crocodile: an animal in its jaws rides it but must not drive it (Minecraft lets a mob steer the mob it rides). */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    /**
     * What it holds is not sitting on it: a held player is drawn standing, not in the riding seat, so it can be laid
     * along the jaws ({@code CrocodileRollClient}; the user's ask, 2026-11-15, for a server with friends).
     */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** A player in its jaws can hit it (Minecraft keeps a rider from hitting what it rides). */
    @Override
    public boolean canRiderInteract() {
        return true;
    }

    /**
     * Something small is held crosswise in its jaws, between its teeth ({@link #HOLD_AHEAD}). A big animal is held by the
     * head, facing the crocodile, as it was seized and as it is hauled in. Afloat the prey's middle is at the height of
     * the crocodile's spine (so the roll turns prey and crocodile round one line); on the bank it stands on the ground
     * under it, and slides down the edge into the water as it is pulled over.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        boolean big = passenger instanceof LivingEntity l && !isSmall(l);
        double ahead = HOLD_AHEAD * getScale() + passenger.getBbWidth() * (big ? 0.6 : 0.3);
        double px = getX() - Mth.sin(yaw) * ahead, pz = getZ() + Mth.cos(yaw) * ahead;
        double want = Math.max(getY() + ROLL_AXIS * getScale() - passenger.getBbHeight() * 0.5, floorAt(px, pz));
        heldY = Double.isNaN(heldY) || Math.abs(want - heldY) > 3.0 ? want : heldY + Mth.clamp(want - heldY, -0.2, 0.2);
        callback.accept(passenger, px, heldY, pz);
        if (!(passenger instanceof Player) && passenger instanceof LivingEntity living) {
            float across = yBodyRot + (big ? 180.0F : 90.0F);
            living.setYRot(across);
            living.yBodyRot = across;
            living.yHeadRot = across;
        }
    }

    /** The top of the ground at this spot, looked for from a block over its feet to two under (far below if there is none: open water). */
    private double floorAt(double x, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Mth.floor(getY() + 1.0);
        for (int y = top; y >= top - 3; y--) {
            p.set(Mth.floor(x), y, Mth.floor(z));
            var shape = level().getBlockState(p).getCollisionShape(level(), p);
            if (!shape.isEmpty()) {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return -1.0E9;
    }

    /** Where its jaws close (the snout's tip, at the height of its mouth). */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + ROLL_AXIS * getScale(), getZ() + Mth.cos(yaw) * ahead);
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
        return life.ownSize();
    }

    /** Its length snout to tail tip (blocks): 7.5 at the usual size. */
    public float bodyLength() {
        return MODEL_SCALE * 6.75F * getScale();
    }

    /**
     * A bull shark's hunched warning turned on it in the water (2026-11-23): the longer of the two wins the standoff. A
     * crocodile at least as long as the shark (most males) turns to face it and gapes, hissing, holding its ground; a
     * shorter one (most females) gives way, sinking out of sight and keeping clear a while (its startle).
     */
    public void answerShark(BullSharkEntity shark) {
        if (bodyLength() >= shark.length()) {
            if (held != null || hunting) {
                return;                 // busy with prey: it pays the shark no mind
            }
            getNavigation().stop();
            float face = (float) (Mth.atan2(shark.getZ() - getZ(), shark.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(face);
            yBodyRot = face;
            yHeadRot = face;
            gape(SHARK_STANDOFF);
            level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.CAT_HISS, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.45F);
        } else {
            startle();
        }
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
        life.apply();
        sizeChanged();
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
            // against a bank or a ledge under the water with room above it: up and over, as every swimming mob of
            // Minecraft's climbs out. Room means no block in the way, water allowed: Minecraft's own isFree also refuses
            // water, so a crocodile afloat against a shelf a block under the surface never rose onto it (BUGS 273)
            if (horizontalCollision && level().noCollision(this, getBoundingBox().move(v.x, v.y + 0.6 - getY() + before, v.z))) {
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
        chain.place();
        if (!isVehicle()) {
            heldY = Double.NaN;
        }
        if (level().isClientSide) {
            for (Entity rider : getPassengers()) {
                if (rider instanceof Player player) {
                    // a held player flails: its arms and legs swing as hard as Minecraft's own walk lets them, about 3 times a second
                    player.walkAnimation.update(1.4F, 1.0F);
                }
            }
            lookAround();
            return;
        }
        breedingTick();
        if (hungerIn > 0) {
            hungerIn--;
        }
        if (huntWait > 0) {
            huntWait--;
        }
        if (sandLookIn > 0) {
            sandLookIn--;
        }
        if (schoolFor > 0 && --schoolFor == 0) {
            schoolAt = null;
        }
        if (held != null && (held.isRemoved() || held.getVehicle() != this)) {
            held = null;
            endRoll();
        }
        if (held == null && isVehicle()) {      // nothing it rides along unless it is holding it (a player saved riding it, say)
            ejectPassengers();
        }
        if (sunIn > 0 && isInWater() && --sunIn == 0) {
            restHalfOut = random.nextFloat() < HALF_OUT_SHARE;
        }
        if (startled > 0) {
            startled--;
        } else if (tickCount % 10 == 0 && !hunting && guardPlace() == null && (!isInWater() || isBasking()) && playerTooNear()) {
            startle();
        }
        // resting in the water it rises to hang at the surface, eyes and back out (startled, it sinks out of sight)
        if (isInWater() && (getNavigation().isDone() || held != null)) {
            // holding its prey it keeps to the surface, where it drags and rolls it, splashing (and a held player sees it)
            double depth = getFluidHeight(FluidTags.WATER) - (startled > 0 && held == null ? HIDE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, -0.02, held != null ? 0.1 : 0.03) + v.y * 0.5, v.z);
        }
    }

    /**
     * Its breeding's tick: a courtship's look ends with the courtship, a gape at a rival ends, a hatchling stops waiting
     * in the nest when its time is up or its mother is gone, and a crèche whose care is over is let go.
     */
    private void breedingTick() {
        byte show = show();
        // (a still one, NoAI, keeps the look a film gave it)
        if ((show == SHOW_SLAP || show == SHOW_RUMBLE || show == SHOW_SNOUT_UP || show == SHOW_ROLL) && life.partner() == null && !isNoAi()) {
            setShow(SHOW_NONE);
        }
        if (gapeFor > 0 && --gapeFor == 0 && jaws() == JAWS_GAPE && held == null) {
            setJaws(JAWS_NONE);
        }
        if (waitForMother > 0) {
            waitForMother--;
            if (tickCount % 20 == 0 && mother() == null) {
                waitForMother = 0;
            }
        }
        if (crecheAt != null && level().getGameTime() >= crecheUntil) {
            crecheAt = null;
        }
    }

    /** This young one's mother, if she is near (within 32 blocks). */
    @Nullable
    CrocodileEntity mother() {
        if (motherId == null || !(level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(level.getEntity(motherId) instanceof CrocodileEntity mother) || !mother.isAlive() || mother.distanceToSqr(this) > 32 * 32) {
            return null;
        }
        return mother;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            if (guardPlace() != null && held == null) {
                setTarget(attacker);        // a mother hit at her nest or crèche turns on what hit her, she does not flee
                return true;
            }
            CrocodileEntity mother = life.isYoung() ? mother() : null;
            if (mother != null && mother.guardPlace() != null) {
                mother.setTarget(attacker); // a young one hurt: its mother comes at what hurt it
            }
        }
        if (hurt && held != null) {
            // its prey fighting back, or anything hurting it enough, makes it let go and sink away (a player can fight free)
            heldHurt += amount;
            if (heldHurt >= ESCAPE_HURT) {
                release();
                startle();
                huntWait = 600;
                return true;
            }
        }
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

    /**
     * Where its eyes go (drawn only, on the client; 2026-11-15, the user's ask): a crocodile lies still but watches,
     * its eyes jumping to whatever moves near it. A player catches its eye first (a spectator too: only its eyes follow,
     * it does not flee one), then any other animal stirring near; it keeps its eyes on one a few seconds before it looks
     * round again, and with nothing to watch its eyes wander now and then.
     */
    private void lookAround() {
        if (eyesHeld > 0) {
            eyesHeld--;
        }
        if (tickCount % 5 != 0) {
            return;
        }
        if (eyesOn != null && (eyesOn.isRemoved() || !eyesOn.isAlive() || !catchesEye(eyesOn, false))) {
            eyesOn = null;
            eyesHeld = 0;
        }
        if (eyesOn == null || eyesHeld == 0) {
            Entity best = null;
            double bestScore = Double.MAX_VALUE;
            for (Entity seen : level().getEntities(this, getBoundingBox().inflate(EYE_REACH), e -> e instanceof LivingEntity)) {
                if (!catchesEye(seen, true) && seen != eyesOn) {
                    continue;
                }
                double score = distanceTo(seen) * (seen instanceof Player ? 0.5 : 1.0) * (seen == eyesOn ? 0.7 : 1.0);
                if (score < bestScore) {
                    bestScore = score;
                    best = seen;
                }
            }
            eyesOn = best;
            eyesHeld = 40 + random.nextInt(60);
        }
        if (eyesOn == null && (glanceIn -= 5) <= 0) {
            glance = random.nextFloat() < 0.4F ? 0.0F : (random.nextFloat() * 2.0F - 1.0F) * 80.0F;
            glanceIn = 40 + random.nextInt(80);
        }
    }

    /** Whether this catches its eye: a player within reach, or another animal within reach that is moving (or that its eyes are already on). */
    private boolean catchesEye(Entity seen, boolean starting) {
        if (seen instanceof CrocodileEntity) {
            return false;
        }
        double d2 = distanceToSqr(seen);
        if (seen instanceof Player player) {
            double reach = player.isCrouching() ? EYE_REACH_SNEAK : EYE_REACH;
            return (player.isSpectator() || !player.isInvisible()) && d2 < reach * reach;
        }
        if (d2 >= STIR_REACH * STIR_REACH) {
            return false;
        }
        double dx = seen.getX() - seen.xo, dy = seen.getY() - seen.yo, dz = seen.getZ() - seen.zo;
        return !starting || dx * dx + dy * dy + dz * dz > 0.0004;
    }

    /** What its eyes are on now (null: nothing, they wander, {@link #glance()}); the client only. */
    @Nullable
    public Entity eyesOn() {
        return eyesOn;
    }

    /** Where its eyes wander with nothing to watch (degrees off straight ahead, its right positive). */
    public float glance() {
        return glance;
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
        life.setUp(false, level.getRandom());
        hungerIn = level.getRandom().nextInt(2400);     // hungry within 2 minutes or so (my pick)
        refresh();
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (gravid) {
            tag.putBoolean("Gravid", true);
        }
        if (nestAt != null) {
            tag.putLong("NestAt", nestAt.asLong());
        }
        if (crecheAt != null) {
            tag.putDouble("CrecheX", crecheAt.x);
            tag.putDouble("CrecheY", crecheAt.y);
            tag.putDouble("CrecheZ", crecheAt.z);
            tag.putLong("CrecheUntil", crecheUntil);
        }
        if (motherId != null) {
            tag.putUUID("Mother", motherId);
        }
        carried.save(tag, "CarriedYoung");
        tag.putInt("SunIn", sunIn);
        tag.putBoolean("HalfOut", restHalfOut);
        tag.putInt("HungerIn", hungerIn);
        if (strikeLuck != 0) {
            tag.putByte("StrikeLuck", strikeLuck);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        gravid = tag.getBoolean("Gravid");
        nestAt = tag.contains("NestAt") ? BlockPos.of(tag.getLong("NestAt")) : null;
        crecheAt = tag.contains("CrecheX") ? new Vec3(tag.getDouble("CrecheX"), tag.getDouble("CrecheY"), tag.getDouble("CrecheZ")) : null;
        crecheUntil = tag.getLong("CrecheUntil");
        motherId = tag.hasUUID("Mother") ? tag.getUUID("Mother") : null;
        carried.load(tag, "CarriedYoung");
        sizeChanged();
        if (tag.contains("SunIn")) {
            sunIn = tag.getInt("SunIn");
        }
        restHalfOut = tag.getBoolean("HalfOut");
        hungerIn = tag.getInt("HungerIn");
        strikeLuck = tag.getByte("StrikeLuck");
        if (isNoAi() && tag.getBoolean("Basking")) {     // for films and the summon command: a still crocodile basking
            setBasking(true);
        }
        if (isNoAi() && tag.contains("Show")) {          // for films: a still crocodile showing a breeding move (SHOW_ numbers)
            setShow(tag.getByte("Show"));
        }
    }

    /** Kept near a player like the river fish ({@link TilapiaEntity#KEEP_NEAR}). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // a mother with eggs, a nest or young to see to stays (her story would be lost)
        return !busyMother() && distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    /**
     * Its spawn spot may be water (BUGS 218): only blocks and other mobs stand in its way, as for Minecraft's water
     * animals. A land mob's own check refuses any spot with water in its box, so the world never spawned a crocodile in
     * the river (spawn eggs and commands skip this check).
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = 96.0 * getViewScale();
        return distanceSqr < reach * reach;
    }

    /**
     * Where it may spawn: the river fish's waters, with a block of water under it, and (spawned by the world, not an egg
     * or a command) no other crocodile within {@link #SPAWN_APART} blocks: each its own stretch of river.
     */
    public static boolean canSpawn(EntityType<CrocodileEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 6 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)
                || !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return false;
        }
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION || farFromOthers(level, pos);
    }

    /** Whether no crocodile is within {@link #SPAWN_APART} blocks of this spot (where the world may spawn one). */
    public static boolean farFromOthers(LevelAccessor level, BlockPos pos) {
        return level.getEntitiesOfClass(CrocodileEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(SPAWN_APART)).isEmpty();
    }

    /**
     * A spot to bask beside crocodiles already basking on land within {@link #JOIN_REACH} blocks: {@link #SIDE_BY_SIDE}
     * blocks to either side of one (then twice that, the row growing), on dry bank, with no other crocodile lying there
     * or heading there. Sets {@link #joinAt} and {@link #joinFace} (the basker's heading) and says whether it found one.
     */
    boolean findBaskingCompany() {
        joinAt = null;
        Level level = level();
        java.util.List<CrocodileEntity> near = level.getEntitiesOfClass(CrocodileEntity.class,
                getBoundingBox().inflate(JOIN_REACH, 8.0, JOIN_REACH), c -> c != this && c.isAlive() && c.isBasking() && !c.isInWater());
        if (near.isEmpty()) {
            return false;
        }
        near.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
        java.util.List<CrocodileEntity> all = level.getEntitiesOfClass(CrocodileEntity.class,
                getBoundingBox().inflate(JOIN_REACH + 4.0, 10.0, JOIN_REACH + 4.0), c -> c != this);
        for (CrocodileEntity basker : near) {
            float yaw = basker.getYRot() * Mth.DEG_TO_RAD;
            double sideX = Mth.cos(yaw), sideZ = Mth.sin(yaw);
            for (int k : new int[] {1, -1, 2, -2}) {
                double x = basker.getX() + sideX * SIDE_BY_SIDE * k, z = basker.getZ() + sideZ * SIDE_BY_SIDE * k;
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos at = BlockPos.containing(x, basker.getY() + 0.1 + dy, z);
                    if (!isBank(level, at) || level.getFluidState(at.below()).is(FluidTags.WATER) || taken(all, x, z)) {
                        continue;
                    }
                    joinAt = new Vec3(x, at.getY(), z);
                    joinFace = basker.getYRot();
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a crocodile lies at (x, z), or is heading there to lie, within about a block and a third. */
    private static boolean taken(java.util.List<CrocodileEntity> crocs, double x, double z) {
        for (CrocodileEntity c : crocs) {
            double dx = c.getX() - x, dz = c.getZ() - z;
            if (dx * dx + dz * dz < 1.8) {
                return true;
            }
            if (c.joinAt != null) {
                dx = c.joinAt.x - x;
                dz = c.joinAt.z - z;
                if (dx * dx + dz * dz < 1.8) {
                    return true;
                }
            }
            if (c.sandAt != null) {
                dx = c.sandAt.getX() + 0.5 - x;
                dz = c.sandAt.getZ() + 0.5 - z;
                if (dx * dx + dz * dz < 1.8) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A spot to bask on a river sand bank within {@link #SAND_REACH} blocks ({@link #sandAt}): the one it found before
     * while it is still free, else (at most once a minute) the nearest dry sand spot a block or two back from the water
     * that no other crocodile lies at or is heading to, looked for every other column round the water's surface, in
     * loaded land only. Says whether it has one.
     */
    boolean findSandBank() {
        Level level = level();
        java.util.List<CrocodileEntity> all = level.getEntitiesOfClass(CrocodileEntity.class,
                getBoundingBox().inflate(SAND_REACH + 4.0, 12.0, SAND_REACH + 4.0), c -> c != this);
        if (sandAt != null && level.isLoaded(sandAt) && sandBank(level, sandAt) && !taken(all, sandAt.getX() + 0.5, sandAt.getZ() + 0.5)) {
            return true;
        }
        sandAt = null;
        if (sandLookIn > 0) {
            return false;
        }
        sandLookIn = SAND_LOOK_WAIT;
        BlockPos at = blockPosition();
        for (int up = 0; up < 16 && level.getFluidState(at.above()).is(FluidTags.WATER); up++) {
            at = at.above();        // the water's surface over it
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int best = Integer.MAX_VALUE;
        for (int dx = -SAND_REACH; dx <= SAND_REACH; dx += 2) {
            for (int dz = -SAND_REACH; dz <= SAND_REACH; dz += 2) {
                int d = dx * dx + dz * dz;
                if (d > SAND_REACH * SAND_REACH || d >= best) {
                    continue;
                }
                for (int dy = 0; dy <= 3; dy++) {   // a bank flush with the water or a block or two over it
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        break;
                    }
                    if (sandBank(level, p) && !taken(all, p.getX() + 0.5, p.getZ() + 0.5)) {
                        best = d;
                        sandAt = p.immutable();
                        break;
                    }
                }
            }
        }
        return sandAt != null;
    }

    /** Whether a crocodile could lie at this spot on a river sand bank: dry sand under it, water near but not right beside it. */
    static boolean sandBank(Level level, BlockPos pos) {
        return isRiverSand(level.getBlockState(pos.below())) && isBank(level, pos)
                && AshoreGoal.waterBeside(level, pos, 3) && !AshoreGoal.waterBeside(level, pos, 1);
    }

    private static java.util.Set<net.minecraft.world.level.block.Block> riverSand;

    /** River sand a crocodile lies on: dry or wet sand or a crocodile slide, of any tone. */
    public static boolean isRiverSand(net.minecraft.world.level.block.state.BlockState state) {
        if (riverSand == null) {
            java.util.Set<net.minecraft.world.level.block.Block> set = new java.util.HashSet<>();
            for (var tone : java.util.List.of(com.antcolonies.savanna.registry.SavannaBlocks.RED_SAND,
                    com.antcolonies.savanna.registry.SavannaBlocks.GOLDEN_SAND, com.antcolonies.savanna.registry.SavannaBlocks.PALE_SAND)) {
                set.add(tone.dry().get());
                set.add(tone.wet().get());
                set.add(tone.slide().get());
            }
            riverSand = set;
        }
        return riverSand.contains(state.getBlock());
    }

    /** A dry place to lie: solid ground with air over it, under the open sky. */
    static boolean isBank(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolidRender(level, pos.below()) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() && level.canSeeSky(pos);
    }

    /** Lies in the sun on the bank, its head raised and its mouth open, for 1 to 3 minutes (my pick). */
    /** The chance a bask on land ends with droppings left by the tail (2026-11-15, my pick). */
    static final float DROPPINGS_CHANCE = 0.25F;

    static class BaskGoal extends Goal {
        private final CrocodileEntity croc;
        private int left;
        /** Ticks it still shuffles onto its spot beside its company and turns to face their way (0: lying still). */
        private int settling;

        BaskGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return croc.sunTime() && croc.onGround() && !croc.isInWater() && croc.getNavigation().isDone()
                    && isBank(croc.level(), croc.blockPosition()) && !croc.motherWork();
        }

        @Override
        public void start() {
            left = 1200 + croc.getRandom().nextInt(2400);
            croc.getNavigation().stop();
            croc.setBasking(true);
            settling = croc.joinAt != null && croc.joinAt.distanceToSqr(croc.position()) < 9.0 ? 40 : 0;
            if (settling == 0) {
                croc.joinAt = null;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && croc.sunTime() && !croc.isInWater() && (croc.tickCount % 20 != 0 || !croc.motherWork());
        }

        @Override
        public void tick() {
            left--;
            if (settling > 0 && croc.joinAt != null) {
                settling--;
                double dx = croc.joinAt.x - croc.getX(), dz = croc.joinAt.z - croc.getZ();
                Vec3 v = croc.getDeltaMovement();
                croc.setDeltaMovement(Mth.clamp(dx * 0.15, -0.08, 0.08), v.y, Mth.clamp(dz * 0.15, -0.08, 0.08));
                float yaw = Mth.approachDegrees(croc.getYRot(), croc.joinFace, 6.0F);
                croc.setYRot(yaw);
                croc.yBodyRot = yaw;
                if (settling == 0) {
                    croc.joinAt = null;
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.joinAt = null;
            croc.setBasking(false);
            if (!croc.isInWater() && croc.getRandom().nextFloat() < DROPPINGS_CHANCE
                    && croc.level() instanceof net.minecraft.server.level.ServerLevel level) {
                // Droppings by its tail where it lay (my pick: 1 bask on land in 4).
                float yaw = croc.getYRot() * Mth.DEG_TO_RAD;
                double back = 2.2 * croc.getScale();
                com.antcolonies.savanna.block.CrocDroppingsBlock.drop(level,
                        net.minecraft.core.BlockPos.containing(croc.getX() + Mth.sin(yaw) * back, croc.getY() + 0.1, croc.getZ() - Mth.cos(yaw) * back));
            }
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
     * Keeping clear of hippos (2026-11-21, BUGS 272): a hippo within {@link #NEAR} blocks, or one charging within
     * {@link #CHARGING} (a bull seeing a crocodile off), and it heads {@link #AWAY} blocks off, straight away from the
     * hippo or a little to one side, to water or out onto a bank, until it is {@link #CLEAR} off. (Minecraft's
     * own avoid goal looked for a land spot to run to and often found none in the river, so the crocodile stayed by the
     * pod and was bitten again and again.) Pods let crocodiles rest close by in real rivers, so it keeps only this far.
     */
    static class KeepClearGoal extends Goal {
        static final double NEAR = 7.0, CHARGING = 14.0, CLEAR = 10.0;
        static final int AWAY = 12;
        private final CrocodileEntity croc;
        @Nullable
        private HippoEntity hippo;
        @Nullable
        private BlockPos to;
        private int wait, left;

        KeepClearGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--wait > 0) {
                return false;
            }
            wait = 10;
            hippo = null;
            for (HippoEntity h : croc.level().getEntitiesOfClass(HippoEntity.class, croc.getBoundingBox().inflate(CHARGING))) {
                double d = h.distanceTo(croc);
                if (h.isAlive() && (d < NEAR * h.getScale() || h.charging() && d < CHARGING) && (hippo == null || d < hippo.distanceTo(croc))) {
                    hippo = h;
                }
            }
            if (hippo == null) {
                return false;
            }
            to = away(hippo);
            return to != null;
        }

        /** A spot {@link #AWAY} blocks off, away from the hippo (water where it swims, else ground), or null if none. */
        @Nullable
        private BlockPos away(HippoEntity from) {
            Level level = croc.level();
            double dx = croc.getX() - from.getX(), dz = croc.getZ() - from.getZ(), l = Math.hypot(dx, dz);
            double base = l < 1.0E-3 ? croc.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);
            for (double turn : new double[] {0, 0.6, -0.6, 1.2, -1.2}) {
                for (int far : new int[] {AWAY, AWAY - 4}) {
                    int x = Mth.floor(croc.getX() + Math.cos(base + turn) * far), z = Mth.floor(croc.getZ() + Math.sin(base + turn) * far);
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos at = new BlockPos(x, top, z);
                    if (Math.abs(top - croc.getBlockY()) > 4) {
                        continue;
                    }
                    if (level.getFluidState(at).is(FluidTags.WATER)) {
                        return at;
                    }
                    if (level.getBlockState(at).isFaceSturdy(level, at, net.minecraft.core.Direction.UP)) {     // out onto a bank, as real ones do
                        return at.above();
                    }
                }
            }
            return null;
        }

        @Override
        public void start() {
            left = 200;
            croc.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && hippo != null && hippo.isAlive() && hippo.distanceTo(croc) < CLEAR * hippo.getScale() + (hippo.charging() ? 4 : 0);
        }

        @Override
        public void tick() {
            if (--left % 20 == 0 && croc.getNavigation().isDone()) {
                to = away(hippo);
                if (to != null) {
                    croc.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
                }
            }
        }

        @Override
        public void stop() {
            croc.getNavigation().stop();
            hippo = null;
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
            if (croc.findBaskingCompany()) {
                croc.restHalfOut = false;       // others bask on the bank near: it climbs out to lie beside them
                return false;
            }
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
            if (croc.findBaskingCompany()) {
                return croc.getNavigation().moveTo(croc.joinAt.x, croc.joinAt.y, croc.joinAt.z, 1.0);
            }
            if (croc.findSandBank()) {
                var path = croc.getNavigation().createPath(croc.sandAt, 0);
                if (path != null && path.canReach()) {
                    return croc.getNavigation().moveTo(path, 1.0);
                }
                croc.sandAt = null;     // no way there: the nearest bank, and a fresh look in a minute
            }
            Level level = croc.level();
            Optional<BlockPos> bank = BlockPos.findClosestMatch(croc.blockPosition(), 12, 3, p -> isBank(level, p)
                    && !level.getFluidState(p.below()).is(FluidTags.WATER) && waterBeside(level, p, 3) && !waterBeside(level, p, 1));
            return bank.isPresent() && croc.getNavigation().moveTo(bank.get().getX() + 0.5, bank.get().getY(), bank.get().getZ() + 0.5, 1.0);
        }

        /** Whether there is water within this many blocks at the spot's level or one below. */
        static boolean waterBeside(Level level, BlockPos pos, int reach) {
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

    /**
     * The hunt (2026-11-15, the user's ask; fun first, from what real crocodiles do; every number my pick). A hungry
     * crocodile in the water picks the nearest animal or player (not creative or a spectator; no tame pets, nothing being
     * ridden) at the water's edge or swimming within {@link #HUNT_REACH} blocks, and creeps up to the water nearest it with
     * only its eyes out. From {@link #LUNGE_REACH} blocks it lunges with its mouth wide and, if its jaws reach, grabs;
     * a big animal up on the bank it rushes out of the water at ({@link #CHARGE_REACH}) and strikes from close. One
     * hauled animal in four slips free on the bank ({@link #SLIP_SHARE}) and the crocodile goes back into the water.
     * Something small (a chicken, a rabbit, a calf) it shakes and swallows whole. Anything bigger it hauls back into the
     * water, a tug of war heave by heave while the animal digs in ({@code haul}), swims it out and death-rolls, spinning round its spine with the prey in its jaws, again and again with pauses under the
     * water, until the prey dies (or fights free, {@link #ESCAPE_HURT}); then it tears at the kill a few seconds (what the
     * animal drops is left in the water) and is fed for a day. A miss or an escape: it waits before it hunts again.
     */
    static class HuntGoal extends Goal {
        private enum Phase { STALK, CHARGE, LUNGE, SNAP, DRAG, ROLL, SWALLOW, EAT, DONE }

        private final CrocodileEntity croc;
        private LivingEntity prey;
        private Phase phase = Phase.DONE;
        private int left, t, rolls, look;
        private boolean small;
        /** Hunting a fish (swallowed alive, struck at with snaps and lunges, tried again after a miss), and how many it has missed. */
        private boolean fish;
        private int fishMisses;
        /** Which way the snap now under way swings. */
        private boolean snapRight;
        /** How far off it may stalk this prey before giving up (farther when called to a school). */
        private double reach;
        @Nullable
        private BlockPos out;
        /** Hauling a big animal off the bank backward, and how far this heave pulls toward the water (blocks; under 0: the animal pulls it back). */
        private boolean hauling;
        private double pull;
        private int hauled;
        /** The heave at which this animal slips free (0: it does not). */
        private int slipAt;

        HuntGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            boolean called = croc.schoolAt != null;
            boolean hatchling = croc.life().isYoung();
            if (!croc.isHungry() || croc.huntWait > 0 || !croc.isInWater() || croc.startled > 0 || croc.held != null || ++look % 10 != 0 && !called
                    || croc.isPassenger() || croc.guardPlace() != null || !croc.carried.isEmpty() || croc.life().partner() != null) {
                return false;       // a mother guarding or carrying, a hatchling in her mouth, and a courting pair do not hunt
            }
            prey = null;
            double best = Double.MAX_VALUE, bestCat = Double.MAX_VALUE, bestTilapia = Double.MAX_VALUE;
            LivingEntity cat = null, tilapia = null;
            // called to a school: the fish round where the other struck; else whatever is near it
            Vec3 from = called ? croc.schoolAt : croc.position();
            double around = hatchling ? YOUNG_HUNT_REACH : called ? SCHOOL_REACH : HUNT_REACH;
            for (LivingEntity seen : croc.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, from).inflate(around, 6.0, around),
                    e -> isPrey(croc, e) && (!called || isFish(e)) && !RiverCover.hiddenFrom(croc, e))) {      // (a fish still in the reeds unseen)
                double d = seen.distanceToSqr(from);
                if (d >= around * around) {
                    continue;
                }
                if (d < best) {
                    best = d;
                    prey = seen;
                }
                if (seen instanceof CatfishEntity && d < bestCat) {
                    bestCat = d;
                    cat = seen;
                } else if ((seen instanceof TilapiaEntity || seen instanceof DistichodusEntity) && d < bestTilapia) {
                    bestTilapia = d;
                    tilapia = seen;
                }
            }
            // a catfish is the bigger meal (the user, 2026-11-15): with both near, it goes for the nearest catfish
            // 3 times in 5, else the nearest tilapia, however near either is
            if (prey != null && isFish(prey) && cat != null && tilapia != null) {
                prey = croc.getRandom().nextFloat() < PREFERS_CATFISH ? cat : tilapia;
            }
            if (called) {
                croc.schoolAt = null;       // answered (or nothing left there)
                croc.schoolFor = 0;
            }
            reach = hatchling ? YOUNG_HUNT_REACH : called ? CALL_REACH + SCHOOL_REACH : HUNT_REACH;
            return prey != null;
        }

        @Override
        public void start() {
            phase = Phase.STALK;
            left = 600;
            t = 0;
            rolls = 0;
            small = isSmall(prey);
            fish = isFish(prey);
            fishMisses = 0;
            hauling = false;
            croc.hunting = true;
            croc.setBasking(false);
            approach();
        }

        @Override
        public boolean canContinueToUse() {
            return phase != Phase.DONE && croc.isAlive() && (croc.startled <= 0 || croc.held != null);
        }

        @Override
        public boolean isInterruptable() {
            return croc.held == null;      // a hunt with prey in its jaws runs to its end (or the prey's escape)
        }

        @Override
        public void tick() {
            switch (phase) {
                case STALK -> stalk();
                case CHARGE -> charge();
                case LUNGE -> {
                    t++;
                    face(prey);
                    if (fish) {         // shooting through the water at a fish, it keeps on down to it
                        double dy = prey.getY() + prey.getBbHeight() * 0.5 - croc.jawPoint().y;
                        if (dy < -0.3) {
                            dive(dy);
                        }
                    }
                    if (prey.isAlive() && prey.getBoundingBox().inflate(0.7, 0.9, 0.7).contains(croc.jawPoint())) {
                        grab();
                    } else if (t > (fish ? 10 : 15)) {
                        if (fish) {
                            missedFish();
                        } else {
                            miss(false);
                        }
                    }
                }
                case SNAP -> snap();
                case DRAG -> {
                    t++;
                    if (croc.held == null) {
                        giveUp(600);
                    } else if (hauling) {
                        haul();
                    } else {
                        boolean there = out == null ? deepEnough()
                                : croc.isInWater() && Math.abs(croc.getX() - out.getX() - 0.5) < 1.2 && Math.abs(croc.getZ() - out.getZ() - 0.5) < 1.2;
                        if (there || t > 160) {
                            croc.getNavigation().stop();
                            phase = Phase.ROLL;
                            t = 0;
                            startRoll();
                        } else if (out != null && (croc.getNavigation().isDone() || t % 20 == 1)) {
                            croc.getNavigation().moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 1.0);
                        }
                    }
                }
                case ROLL -> roll();
                case SWALLOW -> {
                    t++;
                    if (prey instanceof CatfishEntity) {
                        tear();
                    } else if (t == 20) {
                        croc.setJaws(JAWS_EAT);
                    }
                    if (t >= 40) {
                        if (croc.level() instanceof net.minecraft.server.level.ServerLevel level) {
                            com.antcolonies.savanna.block.ScrapsBlock.leave(level, prey.position());   // flecks sinking (in water only)
                        }
                        croc.release();
                        prey.discard();         // swallowed whole
                        eat(prey instanceof TilapiaEntity || prey instanceof DistichodusEntity ? FED_TILAPIA : FED_SMALL, 30);
                    }
                }
                case EAT -> {
                    if (++t >= left) {
                        phase = Phase.DONE;
                    }
                }
                default -> { }
            }
        }

        /** Creeping up through the water to the water nearest the prey, slowly (faster after a swimmer), then the lunge. */
        private void stalk() {
            left--;
            if (!prey.isAlive() || prey.isRemoved() || croc.distanceTo(prey) > reach + 4 || !atWater(croc.level(), prey, 5)
                    || prey.isPassenger() || left <= 0 || fish && (!prey.isInWater() || RiverCover.hiddenFrom(croc, prey))) {
                giveUp(200);       // (a fish hiding in the reeds: lost)
                return;
            }
            if (fish) {
                stalkFish();
                return;
            }
            Vec3 jaws = croc.jawPoint();
            double dx = prey.getX() - jaws.x, dz = prey.getZ() - jaws.z, dy = prey.getY() - croc.getY();
            double flat = Math.sqrt(dx * dx + dz * dz) - prey.getBbWidth() * 0.5;
            if (!small && !prey.isInWater() && flat < CHARGE_REACH && Math.abs(dy) < 2.5) {
                // a big animal up on the bank: out of the water and at it (a lunge from the water would only reach its feet)
                phase = Phase.CHARGE;
                t = 0;
                croc.playSound(net.minecraft.sounds.SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.8F);
                croc.getNavigation().moveTo(prey, CHARGE_PACE);
            } else if ((small || prey.isInWater()) && flat < LUNGE_REACH && Math.abs(dy) < 2.5) {
                lunge();
            } else if (left % 10 == 0 || croc.getNavigation().isDone()) {
                approach();
            }
        }

        /** The rush up the bank at a big animal: it runs at it and strikes once its jaws are near; an animal that gets away from the water is left. */
        private void charge() {
            t++;
            if (!prey.isAlive() || prey.isRemoved() || prey.isPassenger() || t > 80 || !atWater(croc.level(), prey, 6)) {
                backOff();
                return;
            }
            Vec3 jaws = croc.jawPoint();
            double dx = prey.getX() - jaws.x, dz = prey.getZ() - jaws.z, dy = prey.getY() - croc.getY();
            if (Math.sqrt(dx * dx + dz * dz) < STRIKE_REACH + prey.getBbWidth() * 0.5 && Math.abs(dy) < 1.5) {
                lunge();
            } else if (t % 5 == 0 || croc.getNavigation().isDone()) {
                croc.getNavigation().moveTo(prey, CHARGE_PACE);
            }
        }

        /**
         * The prey got away: a strike that fell short ({@code slipped} false), or an animal that tore itself out of the
         * jaws while it was hauled down the bank (it is thrown back a step from the crocodile and knows what had it, so
         * it bolts). The crocodile turns back to the water and does not hunt again for half a minute.
         */
        private void miss(boolean slipped) {
            croc.misses++;
            croc.playSound(net.minecraft.sounds.SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 1.1F);
            if (slipped) {
                croc.release();
                hauling = false;
                prey.knockback(0.8, croc.getX() - prey.getX(), croc.getZ() - prey.getZ());
                prey.hurtMarked = true;
                prey.setLastHurtByMob(croc);
            }
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x * 0.2, Math.min(v.y, 0.0), v.z * 0.2);
            backOff();
        }

        /** The hunt is off: back into the water (not staying out to bask where its prey just got away), no hunting for half a minute. */
        private void backOff() {
            croc.setJaws(JAWS_NONE);
            croc.sunIn = Math.max(croc.sunIn, 600);
            giveUp(600);
        }

        /**
         * The tug of war (2026-11-15, the user's ask: a cow is heavy, there should be a struggle). The crocodile has the
         * animal by the head and backs toward the water, its tail to the river, in heaves: each heave it throws itself
         * back and the animal, dug in and leaning away, is dragged a little way after it; some heaves gain nothing, and
         * now and then the animal hauls the crocodile back up the bank. Once the animal is in the water it has no footing
         * and is pulled steadily, and when the crocodile is out of its depth too the haul is over: it swims its prey out to roll it.
         */
        private void haul() {
            hauled++;
            BlockPos aim = out != null ? out : waterAim(croc, 16);
            double dx = 0, dz = 0, len = 0;
            if (aim != null) {
                dx = aim.getX() + 0.5 - croc.getX();
                dz = aim.getZ() + 0.5 - croc.getZ();
                len = Math.sqrt(dx * dx + dz * dz);
            }
            if (slipAt > 0 && prey.isInWater()) {
                miss(true);     // one that was to slip free and is at the water already (seized right at the edge): it tears loose there
                return;
            }
            if (len < 0.5 || hauled > 600 || prey.isInWater() && deepEnough()) {
                hauling = false;
                t = 0;
                croc.setJaws(JAWS_HOLD);
                return;
            }
            dx /= len;
            dz /= len;
            // its tail to the water, its head to its prey: it turns that way as it pulls, swinging the animal round
            float face = Mth.approachDegrees(croc.getYRot(), (float) (Mth.atan2(-dz, -dx) * Mth.RAD_TO_DEG) - 90.0F, 4.0F);
            croc.setYRot(face);
            croc.yBodyRot = face;
            croc.yHeadRot = face;
            croc.getNavigation().stop();
            double speed;
            // the struggle lasts as long as the animal has its feet on the bank, whether the crocodile's own back half is
            // still in the water or not (the first film: half out, it pulled the cow in with no fight at all)
            boolean afloat = prey.isInWater();
            if (afloat) {
                speed = HAUL_AFLOAT;
            } else {
                int beat = hauled % HEAVE_TICKS;
                if (beat == 1 && slipAt > 0 && hauled / HEAVE_TICKS >= slipAt) {
                    miss(true);         // it wrenches its head out of the jaws as the crocodile gathers itself for the next heave
                    return;
                }
                if (beat == 1) {
                    float dice = croc.getRandom().nextFloat();
                    // a heavier animal gives less ground (a cow 0.7 of the gain, a horse half, a pig all of it)
                    double weight = Mth.clamp(0.9 / (prey.getBbWidth() * prey.getBbHeight()), 0.5, 1.2);
                    pull = dice < 0.6F ? HEAVE_GAIN * weight : dice < 0.85F ? 0.04 : -HEAVE_LOST;
                    croc.playSound(pull < 0 ? net.minecraft.sounds.SoundEvents.RAVAGER_STEP : net.minecraft.sounds.SoundEvents.HORSE_STEP,
                            0.9F, 0.6F);
                    if (croc.level() instanceof net.minecraft.server.level.ServerLevel server) {      // dirt kicked up under the animal's feet
                        BlockPos under = BlockPos.containing(prey.getX(), prey.getY() - 0.2, prey.getZ());
                        var ground = croc.level().getBlockState(under);
                        if (!ground.isAir()) {
                            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground),
                                    prey.getX(), prey.getY() + 0.1, prey.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
                        }
                    }
                }
                // the heave: a hard pull in the first part of each beat (the drag clip's recoil), then bracing
                speed = beat >= 2 && beat < 10 ? pull * Math.sin(Math.PI * (beat - 2) / 8.0) * Math.PI / 16.0 : 0.0;
            }
            croc.setJaws(afloat ? JAWS_HOLD : JAWS_DRAG);
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(dx * speed, v.y, dz * speed);
        }

        /**
         * After a fish: it swims at it, and once the fish is within reach of its swinging head it strikes: a fish near its
         * nose with a straight lunge, one beside its head with a sideways snap. Every strike scatters the school and
         * calls the crocodiles round to it.
         */
        private void stalkFish() {
            Vec3 pivot = shoulders();
            Vec3 at = prey.position().add(0, prey.getBbHeight() * 0.5, 0);
            double dx = at.x - pivot.x, dz = at.z - pivot.z, dy = at.y - pivot.y;
            double flat = Math.sqrt(dx * dx + dz * dz);
            double off = offNose(dx, dz);
            double headReach = HEAD_REACH * croc.getScale() + prey.getBbWidth() * 0.5 + 0.3;
            if (dy < -0.6 && flat < headReach + 3.0) {
                dive(dy);       // a fish below it: it sinks down to it, as it does to hide, before it strikes
            }
            if (Math.abs(dy) < 1.2 && Math.abs(off) >= SNAP_FROM_NOSE && Math.abs(off) <= 110.0 && flat < headReach) {
                startSnap(off > 0);
            } else if (Math.abs(dy) < 1.5 && Math.abs(off) < SNAP_FROM_NOSE && flat < headReach + 1.2) {
                strikeAtFish();
                lunge();
            } else if (left % 10 == 0 || croc.getNavigation().isDone()) {
                croc.getNavigation().moveTo(prey, 1.3);
            }
        }

        /** It sinks toward a fish lower in the water (its body floats, so it must swim down; never faster than it sinks to hide). */
        private void dive(double dy) {
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x, Math.min(v.y, Mth.clamp(dy * 0.08, -0.12, -0.03)), v.z);
        }

        /** Where its head swings from: its shoulders (the neck's joint), ahead of its position, at the height of its spine. */
        private Vec3 shoulders() {
            float yaw = croc.yBodyRot * Mth.DEG_TO_RAD;
            double ahead = NECK_AHEAD * croc.getScale();
            return new Vec3(croc.getX() - Mth.sin(yaw) * ahead, croc.getY() + ROLL_AXIS * croc.getScale(), croc.getZ() + Mth.cos(yaw) * ahead);
        }

        /**
         * How far off its nose this way lies (degrees; its right positive, its left negative). Facing yaw 0 is facing +z,
         * and its right hand is then -x; a direction's yaw grows turning to the right.
         */
        private double offNose(double dx, double dz) {
            double want = Mth.atan2(dz, dx) * Mth.RAD_TO_DEG - 90.0;
            return Mth.wrapDegrees(want - croc.yBodyRot);
        }

        /** The snap begins: it stops, the head swings to that side with the jaws thrown open, and the school scatters. */
        private void startSnap(boolean right) {
            croc.getNavigation().stop();
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
            croc.setJaws(right ? JAWS_SNAP_RIGHT : JAWS_SNAP_LEFT);
            snapRight = right;
            strikeAtFish();
            phase = Phase.SNAP;
            t = 0;
        }

        /** The jaws slam shut at {@link #SNAP_CATCH}: the fish is caught if it is still along the line of the swung head. */
        private void snap() {
            t++;
            if (t == SNAP_CATCH) {
                croc.playSound(net.minecraft.sounds.SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 1.2F);
                float yaw = (croc.yBodyRot + (float) (snapRight ? SNAP_SWING : -SNAP_SWING)) * Mth.DEG_TO_RAD;
                Vec3 from = shoulders();
                double len = HEAD_REACH * croc.getScale();
                Vec3 tip = from.add(-Mth.sin(yaw) * len, 0, Mth.cos(yaw) * len);
                Vec3 at = prey.position().add(0, prey.getBbHeight() * 0.5, 0);
                if (prey.isAlive() && !prey.isRemoved() && nearLine(at, from, tip) < 0.9 + prey.getBbWidth() * 0.5) {
                    grab();
                    return;
                }
            }
            if (t >= SNAP_TICKS) {
                missedFish();
            }
        }

        /** How far a point lies from the line between two others (blocks). */
        private static double nearLine(Vec3 p, Vec3 a, Vec3 b) {
            Vec3 ab = b.subtract(a);
            double k = Mth.clamp(p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr()), 0.0, 1.0);
            return p.distanceTo(a.add(ab.scale(k)));
        }

        /**
         * Every strike at a fish: the school round it scatters, the fish itself now and then darts off just as the jaws
         * come ({@link #FISH_DODGE}), and the hungry crocodiles round about head for the place.
         */
        private void strikeAtFish() {
            HuntTilapiaGoal.scatter(croc, prey);
            boolean dodges = croc.strikeLuck < 0 || croc.strikeLuck == 0 && croc.getRandom().nextFloat() < FISH_DODGE;
            if (dodges) {
                Vec3 away = prey.position().subtract(croc.jawPoint());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                prey.setDeltaMovement(prey.getDeltaMovement().add(away.scale(0.6)));
                prey.hurtMarked = true;
            }
            float size = Math.min(1.0F, croc.getScale());
            if (croc.level() instanceof net.minecraft.server.level.ServerLevel server) {
                Vec3 at = croc.jawPoint();
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x, croc.getY() + croc.getFluidHeight(FluidTags.WATER),
                        at.z, Math.max(3, (int) (12 * size)), 0.6 * size, 0.1, 0.6 * size, 0.2);
            }
            croc.playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, 0.8F * size, 1.1F + (1.0F - size));
            if (!croc.life().isYoung()) {
                callToSchool(croc, prey.position());       // a hatchling's strike calls no one
            }
        }

        /** A fish got away: it swims after it again, or after a few misses gives up a while. */
        private void missedFish() {
            croc.misses++;
            croc.setJaws(JAWS_NONE);
            if (++fishMisses >= FISH_MISSES) {
                giveUp(400);
                return;
            }
            phase = Phase.STALK;
            t = 0;
            left = Math.max(left, 100);
        }

        private void approach() {
            Level level = croc.level();
            BlockPos at = BlockPos.findClosestMatch(prey.blockPosition(), 3, 2, p -> level.getFluidState(p).is(FluidTags.WATER)).orElse(prey.blockPosition());
            croc.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, prey.isInWater() ? 1.3 : 0.8);
        }

        /** Bursts out at the prey, mouth wide: a push toward it and a little up, out of the water. */
        private void lunge() {
            Vec3 to = prey.position().add(0, prey.getBbHeight() * 0.3, 0).subtract(croc.position());
            double h = Math.max(0.1, Math.sqrt(to.x * to.x + to.z * to.z));
            face(prey);
            croc.getNavigation().stop();
            double v = Mth.clamp(h * 0.14, 0.3, 0.6) * Math.min(1.0, 0.4 + croc.getScale());     // a young one's lunge is shorter
            // at a fish it shoots through the water at it; at anything else it bursts up out of the water
            double up = fish ? Mth.clamp(to.y * 0.15, -0.15, 0.15) : Mth.clamp(to.y * 0.12 + 0.18, 0.1, 0.45);
            croc.setDeltaMovement(to.x / h * v, up, to.z / h * v);
            croc.hasImpulse = true;
            croc.setJaws(JAWS_GAPE);
            croc.playSound(net.minecraft.sounds.SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.8F);
            phase = Phase.LUNGE;
            t = 0;
        }

        private void grab() {
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x * 0.2, Math.min(v.y, 0.0), v.z * 0.2);
            croc.playSound(net.minecraft.sounds.SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.7F);
            if (!fish) {        // a fish is swallowed alive
                prey.hurt(croc.damageSources().mobAttack(croc), BITE);
            }
            if (!prey.isAlive()) {
                eat(small ? FED_SMALL : FED_BIG, 80);
                return;
            }
            boolean ashore = !prey.isInWater();
            if (!croc.grab(prey)) {
                croc.setJaws(JAWS_NONE);
                giveUp(200);
                return;
            }
            phase = small ? Phase.SWALLOW : Phase.DRAG;
            t = 0;
            if (!small) {
                out = openWater(croc, 12);
                hauling = ashore;       // seized on the bank: hauled back to the water; seized swimming: carried straight out
                hauled = 0;
                pull = 0.0;
                boolean slips = croc.strikeLuck < 0 || croc.strikeLuck == 0 && croc.getRandom().nextFloat() < SLIP_SHARE;
                slipAt = ashore && slips ? 1 + croc.getRandom().nextInt(4) : 0;
            }
        }

        /** Roll after roll, a pause between each, until the prey is dead (or after 12 rolls it lets go). */
        private void roll() {
            if (croc.held == null) {
                if (prey.isAlive()) {
                    giveUp(600);        // it got away
                } else {
                    eat(FED_BIG, 80);
                }
                return;
            }
            if (!prey.isAlive()) {
                eat(FED_BIG, 80);
                return;
            }
            croc.getNavigation().stop();
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
            t++;
            if (t <= ROLL_TICKS && t % 3 == 0 && croc.level() instanceof net.minecraft.server.level.ServerLevel server && croc.isInWater()) {
                Vec3 at = croc.jawPoint();
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x, croc.getY() + croc.getFluidHeight(FluidTags.WATER),
                        at.z, 18, 1.0, 0.2, 1.0, 0.3);
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, at.x, croc.getY(), at.z, 6, 0.8, 0.3, 0.8, 0.1);
            }
            if (t == ROLL_TICKS / 2 || t == ROLL_TICKS) {
                float hurt = (prey instanceof Player ? ROLL_HURT_PLAYER : ROLL_HURT) / 2.0F;
                prey.invulnerableTime = 0;
                prey.hurt(croc.damageSources().mobAttack(croc), hurt);
            }
            if (t == ROLL_TICKS) {
                croc.endRoll();
            }
            if (t >= ROLL_TICKS + ROLL_PAUSE) {
                if (++rolls >= 12) {
                    croc.release();
                    giveUp(600);
                    return;
                }
                t = 0;
                startRoll();
            }
        }

        private void startRoll() {
            croc.startRoll();
            croc.playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, 1.0F, 0.7F);
        }

        /**
         * A catfish is too long to swallow whole (2026-11-15, remains; the user's plan): held across the middle, it is
         * whipped from side to side until it tears in two ({@link #TEAR_TICKS}); the crocodile gulps down the tail half
         * and the front half falls where it was, its torn end at the snout, as remains ({@link RemainsEntity}).
         */
        private void tear() {
            if (t == 1) {
                croc.setJaws(JAWS_SHAKE);
            }
            if (t % 7 == 3) {
                croc.playSound(net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, 0.5F, 1.3F);
            }
            if (t >= TEAR_TICKS && croc.level() instanceof net.minecraft.server.level.ServerLevel level) {
                RemainsEntity left = RemainsEntity.leave(level, RemainsEntity.Kind.CATFISH_TORN, prey.position(),
                        prey.getYRot(), prey.getScale());
                if (left != null) {
                    left.setDeltaMovement((croc.getRandom().nextDouble() - 0.5) * 0.08, 0.05, (croc.getRandom().nextDouble() - 0.5) * 0.08);
                    left.bite(croc);        // the tear, with its puff of meat bits
                    left.setBitesLeft(RemainsEntity.Kind.CATFISH_TORN.bites);
                }
                croc.playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH, 0.9F, 0.8F);
                com.antcolonies.savanna.block.ScrapsBlock.leave(level, prey.position());
                croc.release();
                prey.discard();         // the tail half swallowed
                eat(FED_SMALL, 50);
            }
        }

        /** Tears at its kill (or gulps down the small one) a few seconds, then it is fed. */
        private void eat(int fed, int ticks) {
            croc.release();
            croc.setJaws(JAWS_EAT);
            croc.hungerIn = fed;
            phase = Phase.EAT;
            t = 0;
            left = ticks;
        }

        private void giveUp(int wait) {
            croc.huntWait = wait;
            phase = Phase.DONE;
        }

        private boolean deepEnough() {
            return croc.isInWater() && croc.getFluidHeight(FluidTags.WATER) > 0.6 * croc.getScale();
        }

        private void face(Entity target) {
            double dx = target.getX() - croc.getX(), dz = target.getZ() - croc.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                return;
            }
            float face = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            croc.setYRot(face);
            croc.yBodyRot = face;
            croc.yHeadRot = face;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.release();
            croc.setJaws(JAWS_NONE);
            croc.getNavigation().stop();
            croc.hunting = false;
            hauling = false;
            prey = null;
            phase = Phase.DONE;
        }
    }

    /**
     * Whether a crocodile hunts this: an animal (no tame pets) or a player (not in creative, not a spectator), not riding
     * or ridden, in the water or within 4 blocks of it (2 until the crocodile came out onto the bank after big prey, 2026-11-15).
     */
    static boolean isPrey(CrocodileEntity croc, LivingEntity e) {
        if (!e.isAlive() || e.isPassenger() || e.isVehicle()) {
            return false;
        }
        if (e instanceof BarbEntity barb) {
            // the young's food (a hatchling's only prey); a grown crocodile ignores barbs
            return !croc.life().stage().grown() && barb.isInWater() && isFish(e);
        }
        if (e instanceof TadpoleEntity tadpole) {
            // the young's food too (2026-11-23, the river tie-up), as a barb
            return !croc.life().stage().grown() && tadpole.isInWater() && !tadpole.hasCustomName();
        }
        if (croc.life().isYoung()) {
            return false;           // a hatchling hunts barbs and tadpoles only
        }
        if (!croc.life().stage().grown()) {
            // a teen hunts fish (animal life cycle step 4) and bullfrogs in the water (2026-11-23, the river tie-up: frogs
            // are a big share of young crocodiles' food); a hatchling (above) is too small for a grown frog
            return e.isInWater() && (isFish(e) || e instanceof BullfrogEntity frog && !frog.hasCustomName());
        }
        if (e instanceof CrocodileEntity young) {
            // a grown crocodile eats another's hatchlings in the water (real crocodiles do; their mother guards them)
            return young.life().isYoung() && !croc.getUUID().equals(young.motherId) && young.isInWater();
        }
        if (e instanceof Player player) {
            if (player.isCreative() || player.isSpectator() || peaceful(croc.level())) {
                return false;
            }
        } else if (isFish(e)) {
            return e.isInWater();       // a fish, in the water (never one the player brought in a bucket or named)
        } else if (!(e instanceof Animal) || playersOwn(e)) {
            return false;
        }
        return atWater(croc.level(), e, 4);
    }

    /**
     * The player's own animal (review R9-5), which no river hunter takes: tamed or otherwise owned (a wolf, a cat, and a
     * tamed horse, donkey, mule, llama or camel, which Minecraft keeps as a different kind of "tame"), named with a name
     * tag, on a lead, or saddled.
     */
    public static boolean playersOwn(LivingEntity e) {
        return e.hasCustomName() || e instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerUUID() != null
                || e instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed()
                || e instanceof net.minecraft.world.entity.Saddleable ridden && ridden.isSaddled();
    }

    /**
     * Peaceful (review R9-6; my pick): the crocodile does not hunt players and a nest mother only hisses. Its grab is no
     * damage, so Minecraft's own Peaceful rule did not stop it: a player was held through twelve rolls unhurt.
     */
    static boolean peaceful(net.minecraft.world.level.Level level) {
        return level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
    }

    /**
     * Open water to roll its prey in: the nearest water block within this reach (with water under it) with water all
     * round it for 5 blocks (its tail trails about that far behind its middle), so its whole body is afloat; else with 3;
     * null if there is none.
     */
    @Nullable
    static BlockPos openWater(CrocodileEntity croc, int reach) {
        BlockPos wide = openWater(croc, reach, 5);
        return wide != null ? wide : openWater(croc, reach, 3);
    }

    @Nullable
    private static BlockPos openWater(CrocodileEntity croc, int reach, int round) {
        Level level = croc.level();
        // the water's top: where it floats, or a block down when it lies on the bank (pulled out by its lunge)
        int y = croc.isInWater() ? Mth.floor(croc.getY() + croc.getFluidHeight(FluidTags.WATER) - 0.01) : Mth.floor(croc.getY() + 0.01) - 1;
        BlockPos from = BlockPos.containing(croc.getX(), y, croc.getZ());
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-reach, -1, -reach), from.offset(reach, 1, reach))) {
            double d = p.distSqr(from);
            if (d >= bestD || d > reach * reach || !level.getFluidState(p).is(FluidTags.WATER) || !level.getFluidState(p.below()).is(FluidTags.WATER)) {
                continue;
            }
            boolean open = true;
            for (int dx = -round; dx <= round && open; dx++) {
                for (int dz = -round; dz <= round && open; dz++) {
                    open = level.getFluidState(p.offset(dx, 0, dz)).is(FluidTags.WATER);
                }
            }
            if (open) {
                best = p.immutable();
                bestD = d;
            }
        }
        return best;
    }

    /** Small enough to swallow whole (a chicken, a rabbit, a calf, any river fish; never a player). */
    static boolean isSmall(LivingEntity e) {
        return e instanceof net.minecraft.world.entity.animal.AbstractFish || !(e instanceof Player) && e.getBbWidth() < 0.75F && e.getBbHeight() < 0.95F;
    }

    /**
     * A wild river fish it hunts: a tilapia, a catfish, a barb, a tadpole or a distichodus (hunted the fish's way), not
     * from a bucket and not named (the player's own fish). The bull shark hunts by this too.
     */
    static boolean isFish(LivingEntity e) {
        return (e instanceof TilapiaEntity || e instanceof CatfishEntity || e instanceof BarbEntity || e instanceof TadpoleEntity
                || e instanceof DistichodusEntity) && !((net.minecraft.world.entity.animal.AbstractFish) e).fromBucket()
                && !e.hasCustomName();
    }

    /** A strike at fish here: every other hungry crocodile in the water within {@link #CALL_REACH} heads for the place to hunt them too. */
    static void callToSchool(CrocodileEntity striker, Vec3 at) {
        for (CrocodileEntity other : striker.level().getEntitiesOfClass(CrocodileEntity.class, striker.getBoundingBox().inflate(CALL_REACH, 8.0, CALL_REACH))) {
            if (other != striker && other.isHungry() && other.isInWater() && other.held == null && !other.hunting && !other.life().isYoung()) {
                other.schoolAt = at;
                other.schoolFor = CALL_TICKS;
            }
        }
    }

    /** Whether it is in the water, or has water within this many blocks at its feet's level or one under. */
    static boolean atWater(Level level, Entity e, int reach) {
        if (e.isInWater()) {
            return true;
        }
        BlockPos feet = e.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-reach, -1, -reach), feet.offset(reach, 0, reach))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // --- animations --------------------------------------------------------------------------------------------------
    /** Clips named by the film camera ({@code film:<clip>}, a still one; step C, 2026-11-20). */
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    /** The hatchlings' own moves (2026-11-20, tools/crocodile_models.py young_clips); the teens bound too. */
    private static final RawAnimation YOUNG_IDLE = RawAnimation.begin().thenLoop("young_idle");
    private static final RawAnimation YOUNG_WALK = RawAnimation.begin().thenLoop("young_walk");
    private static final RawAnimation YOUNG_RUN = RawAnimation.begin().thenLoop("young_run");
    private static final RawAnimation YOUNG_SWIM = RawAnimation.begin().thenLoop("young_swim");
    private static final RawAnimation YOUNG_FLOAT = RawAnimation.begin().thenLoop("young_float");
    private static final RawAnimation BASK = RawAnimation.begin().thenLoop("bask");
    /** Basking on land: first the flop from its walk onto its belly, then the bask (the crocodile's life step 3). */
    private static final RawAnimation FLOP_BASK = RawAnimation.begin().thenPlay("flop").thenLoop("bask");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation GAPE = RawAnimation.begin().thenPlayAndHold("lunge");
    private static final RawAnimation HOLD = RawAnimation.begin().thenLoop("hold");
    private static final RawAnimation EAT = RawAnimation.begin().thenLoop("eat");
    private static final RawAnimation DRAG = RawAnimation.begin().thenLoop("drag");
    private static final RawAnimation SNAP_LEFT = RawAnimation.begin().thenPlayAndHold("snap_left");
    private static final RawAnimation SNAP_RIGHT = RawAnimation.begin().thenPlayAndHold("snap_right");
    private static final RawAnimation SHAKE = RawAnimation.begin().thenLoop("shake");
    /** Its breeding (animal life cycle step 4): the male's head slap (once, then held) and rumble, the female's raised snout, the pair's roll, a mother's hiss and her digging. */
    private static final RawAnimation HEAD_SLAP = RawAnimation.begin().thenPlayAndHold("head_slap");
    private static final RawAnimation RUMBLE = RawAnimation.begin().thenLoop("rumble");
    private static final RawAnimation SNOUT_UP = RawAnimation.begin().thenLoop("snout_up");
    private static final RawAnimation COURT_ROLL = RawAnimation.begin().thenLoop("court_roll");
    private static final RawAnimation HISS = RawAnimation.begin().thenLoop("hiss");
    private static final RawAnimation DIG = RawAnimation.begin().thenLoop("dig");
    /** Drawn only: when it last walked on land, and whether the bask now drawn began with the flop. */
    private int walkedAt = -100;
    private boolean drawnBasking, flopNow;
    /** The speeds the walk and swim clips are made for (blocks a tick); they play faster or slower by the real speed. */
    static final double WALK_CLIP_SPEED = 0.066 * MODEL_SCALE, SWIM_CLIP_SPEED = 0.088 * MODEL_SCALE;
    /**
     * The young's clips' speeds (at the usual size, as above; my picks for the look, 2026-11-20): a hatchling scurries
     * about 4.5 strides a second at its own walking pace (its feet slip a little: a stride without any slip would be a
     * blur at half a block long, and the game keeps its feet on the ground anyway) and bounds about 3.5 times a second
     * running; a teen bounding goes slower, by its size.
     */
    static final double YOUNG_WALK_CLIP_SPEED = 0.0535 * MODEL_SCALE, YOUNG_RUN_CLIP_SPEED = 0.26 * MODEL_SCALE;
    /** A grown crocodile's walk on the measuring course (blocks a tick); a young one runs past 1.5 times its own share of it. */
    private static final double GROWN_WALK = 0.059;
    /** Drawn only: whether a young one is drawn bounding now (from 1.5 times its walking pace, back to walking under 1.25). */
    private boolean drawnRunning;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 8, this::animate));
    }

    private PlayState animate(AnimationState<CrocodileEntity> state) {
        AnimationController<CrocodileEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {     // the film camera's still look (game_compare): a
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5),   // clip it lacks
                    c -> RawAnimation.begin().thenLoop(c)));                                        // draws its rest pose
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        byte jaws = jaws();
        if (jaws != JAWS_NONE) {
            drawnBasking = false;
            return state.setAndContinue(jaws == JAWS_GAPE ? GAPE : jaws == JAWS_HOLD ? HOLD : jaws == JAWS_DRAG ? DRAG
                    : jaws == JAWS_SNAP_LEFT ? SNAP_LEFT : jaws == JAWS_SNAP_RIGHT ? SNAP_RIGHT : jaws == JAWS_SHAKE ? SHAKE : EAT);
        }
        byte show = show();
        if (show != SHOW_NONE && show != SHOW_CARRY) {
            drawnBasking = false;
            return state.setAndContinue(switch (show) {
                case SHOW_SLAP -> HEAD_SLAP;
                case SHOW_RUMBLE -> RUMBLE;
                case SHOW_SNOUT_UP -> SNOUT_UP;
                case SHOW_ROLL -> COURT_ROLL;
                case SHOW_HISS -> HISS;
                default -> DIG;
            });
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        // the clips' strides are made for a crocodile of the usual size (scale 1): a smaller one's legs and tail beat
        // faster by how much shorter they are, and its quickest beat (the cap) and its stillness go by its own pace
        double clipScale = Math.max(0.03, getScale()), pace = sizePace();
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
        LifeStage stage = life.stage();
        boolean hatchling = stage == LifeStage.YOUNG;
        if (isInWater()) {
            drawnRunning = false;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed < 0.012 * pace) {
                return state.setAndContinue(hatchling ? YOUNG_FLOAT : FLOAT);
            }
            player.setAnimationSpeed(Mth.clamp(speed / (SWIM_CLIP_SPEED * clipScale), 0.5, 2.5 / pace));
            return state.setAndContinue(hatchling ? YOUNG_SWIM : SWIM);
        }
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < 0.005 * pace) {
            drawnRunning = false;
            return state.setAndContinue(hatchling ? YOUNG_IDLE : IDLE);
        }
        if (hatchling || stage == LifeStage.TEEN) {
            double walking = GROWN_WALK * pace;
            drawnRunning = speed > walking * (drawnRunning ? 1.25 : 1.5);
            if (drawnRunning) {
                player.setAnimationSpeed(Mth.clamp(speed / (YOUNG_RUN_CLIP_SPEED * clipScale), 0.5, 2.5 / pace));
                return state.setAndContinue(YOUNG_RUN);
            }
        }
        if (hatchling) {
            player.setAnimationSpeed(Mth.clamp(speed / (YOUNG_WALK_CLIP_SPEED * clipScale), 0.5, 2.5 / pace));
            return state.setAndContinue(YOUNG_WALK);
        }
        player.setAnimationSpeed(Mth.clamp(speed / (WALK_CLIP_SPEED * clipScale), 0.5, 2.5 / pace));
        return state.setAndContinue(WALK);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (chain != null) {                              // null while Entity's own constructor runs
            chain.setIds(id);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return chain.parts();
    }
}
