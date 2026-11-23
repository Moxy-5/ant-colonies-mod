package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Attention;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.HitChain;
import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.entity.body.MoveRider;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
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
 * The African clawless otter (M9 savanna life, into the game 2026-11-22, the user: "I want the otter's animations to be
 * really special ... one of the players' favorites"; model and clips {@code tools/otter_models.py game}, v6 approved by
 * the user 2026-11-21; plan {@code docs/plans/otter-and-bull-shark.md}). An animal: generalized and fun first, every
 * number my pick, real habits used where they make it better:
 * <ul>
 * <li>Families of two to four live along the rivers and oxbows: strolling and bounding on the banks, swimming at the
 * surface with head and back out, diving and swimming underwater (about 20 seconds a breath), resting flat on a bank in
 * the heat of the day.</li>
 * <li>Its block moves (the Animal Maker Engine, played by the shared {@link MoveRider}): a quick cute scramble up a block,
 * a neat hop down, a calm slide into the water off a level bank, a leaping dive when in a hurry (or off a bank a block up),
 * and a quick climb out onto a bank level with the water or a block over it. Out of the water it shakes itself dry.</li>
 * <li>Hurt, or a crocodile or a hippo near, it runs for the water and dives away, or gets out if the danger is in it.</li>
 * <li>Pups follow the grown otters of their family.</li>
 * </ul>
 * Not yet (later steps of the plan): hunting crabs, fish and frogs, play-wrestling, biting back, the pet.
 */
public class OtterEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, SHAKE = 1, REST = 2, REST_UP = 3, FORAGE = 4, BITE = 5, EAT = 6, SKID = 7, HOP = 8,
            PLAY_UP = 9, PLAY = 10, PLAY_DOWN = 11, GULP = 12, SIDE_L = 13, SIDE_R = 14, POUNCE = 15, PORPOISE = 16,
            ZIG_L = 17, ZIG_R = 18;
    /**
     * A small fish swallowed at once ({@link HeldCatch#swallowed}; the gulp clip, on the head's own layer): it takes the
     * fish as its jaws snap shut, and the gulp is over at the end (ticks), and such a fish fills it for this part of a
     * catch carried to the bank (my pick: a barb is a mouthful, so it hunts again sooner).
     */
    static final int GULP_TAKE = 6, GULP_TICKS = 16;
    static final double SMALL_MEAL = 1.0 / 3.0;
    /**
     * Play ({@link Bout}): rearing up and dropping down take this long (ticks: the play_up and play_down clips), rearing
     * face to face their noses reach this far ahead of their middles (blocks at size 1: 13.5 px, measured on the
     * play_rear clip), a playful otter tries to start a bout every so often, and waits longer after one (my picks).
     */
    static final int PLAY_UP_TICKS = 12, PLAY_DOWN_TICKS = 12, PLAY_EVERY = 200, PLAY_AFTER = 600;
    public static final double REAR_REACH = 13.5 / 16.0;
    /**
     * Where they meet without one body inside the other (measured on the clips, 2026-11-23, after the user saw them
     * clip): the nose 24.3 px ahead of the middle at the bite's reach and 21.0 px standing (the nose poke stands their
     * middles the two apart, so the noses just touch), and an otter 7.2 px to either side lying down (resting together
     * their middles are two of those apart, flank to flank).
     */
    public static final double NOSE_BITE = 24.3 / 16.0, NOSE_STAND = 21.0 / 16.0, REST_BESIDE = 14.4 / 16.0;
    /**
     * How it goes on land (the skitter, 2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 6b): the even
     * walk, or the quick choppy scurry and the slow nose-down sniff of its burst-and-stop wandering ({@link SkitterGoal}).
     */
    public static final byte EVEN = 0, SCURRY = 1, SNIFF = 2;
    private static final EntityDataAccessor<Byte> DATA_GAIT = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    /** The skid stop's and the hop turn's lengths (ticks), and the hop's feet off the ground (its ticks 3 to 7: it turns then). */
    static final int SKID_TICKS = 8, HOP_TICKS = 10, HOP_UP = 3, HOP_DOWN = 7;
    /**
     * The war dance (2026-11-23; plan {@code docs/plans/otter-play-and-fish.md} 6f; the user: "make sure they jump side to
     * side like real otters and weasels"): a hop to its left or right ({@link #SIDE_L}, {@link #SIDE_R}), humped, bent and
     * open mouthed, the same length as the hop turn, carried this far sideways (blocks at size 1) while its feet are up
     * (ticks {@link #HOP_UP} to {@link #HOP_DOWN}); a dance is 2 to 4 of them, left and right in turn. Numbers my picks.
     */
    static final double SIDE_GO = 0.7;
    /**
     * Chase and tag (2026-11-23; plan {@code docs/plans/otter-play-and-fish.md} 6e step 4): the pounce ({@link #POUNCE},
     * the pounce_tag clip, 0.85 s) has its feet up on ticks {@link #POUNCE_UP} to {@link #POUNCE_DOWN} (the game carries it
     * then), nips on tick 9 and lets go on {@link #POUNCE_LET_GO}; it lands {@link #POUNCE_BEHIND} blocks (at size 1,
     * measured on the clip: 21 px) behind the runner's middle, its paws on the runner's rump, and springs from at most
     * {@link #POUNCE_FROM} blocks off (a leap of about 1.3 blocks; the clip's reach 0.6 to 2). The porpoise
     * ({@link #PORPOISE}, 1.0 s) is out of the water from tick {@link #PORPOISE_OUT} to {@link #PORPOISE_IN}, splashing at
     * both. Numbers from the clips; the rest my picks.
     */
    static final int POUNCE_TICKS = 17, POUNCE_UP = 4, POUNCE_DOWN = 8, POUNCE_LET_GO = 13;
    static final int PORPOISE_TICKS = 20, PORPOISE_OUT = 4, PORPOISE_IN = 15;
    public static final double POUNCE_BEHIND = 21.0 / 16.0, POUNCE_FROM = 2.6;
    /**
     * The land chase, take two (2026-11-23, plan 6g; the user: "fast like a real chase ... leaping while skedaddling side
     * to side"): the runner zigzags in leaps ({@link #ZIG_L}, {@link #ZIG_R}, the zig_left and zig_right clips, 0.4 s),
     * each carried {@link #ZIG_GO} blocks on and {@link #ZIG_SIDE} aside (at size 1: about 6 blocks a second along its
     * path, the user's speed), the aside part while its feet are up (from tick {@link #ZIG_UP}); along an S near where
     * the game started (within {@link #RUN_ROOM} blocks), for {@link #RUN_MOST} ticks at most. The chaser waits
     * {@link #CHASE_WAIT} ticks, then runs straight at it at {@link #CHASE_FROM} blocks a second, rising to
     * {@link #CHASE_MID} over {@link #CHASE_RAMP} ticks and to {@link #CHASE_TOP} over as many again (the user's numbers).
     */
    static final int ZIG_TICKS = 8, ZIG_UP = 3, RUN_MOST = 240, CHASE_WAIT = 16, CHASE_RAMP = 80;
    public static final double ZIG_GO = 2.2, ZIG_SIDE = 0.9, RUN_ROOM = 10.0;
    public static final double CHASE_FROM = 5.5, CHASE_MID = 6.0, CHASE_TOP = 6.5;
    /**
     * The skitter's speeds (shares of its speed attribute; my picks, measured in {@code RiverLifeTests}: about 1.6 and
     * 0.6 blocks a second, the clips' own strides) and the clips' made speeds (blocks a tick at size 1).
     */
    public static final double SCURRY_GO = 0.66, SNIFF_GO = 0.40;
    static final double SCURRY_CLIP_SPEED = 1.567 / 20.0, SNIFF_CLIP_SPEED = 0.593 / 20.0;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_MOVE = SynchedEntityData.defineId(OtterEntity.class,
            EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(OtterEntity.class);
    /**
     * Its mood (2026-11-22, the faces; plan {@code docs/plans/otter-play-and-fish.md}, section 6a): one value, worked
     * out in one place ({@link #workOutMood}), read by its face, its tail and its standing stance. In order of how
     * urgent it is: a more urgent mood comes at once, a calmer one only after {@link #MOOD_HOLD} ticks.
     */
    public static final String[] MOODS = {"calm", "playful", "curious", "content", "wary", "cross", "scared"};
    public static final byte CALM_MOOD = 0, PLAYFUL = 1, CURIOUS = 2, CONTENT = 3, WARY = 4, CROSS = 5, SCARED = 6;
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    /** The angle its catch was caught at (the fish's heading against its own, degrees, + to its right; {@link HeldCatch}). */
    private static final EntityDataAccessor<Float> DATA_CAUGHT_AT = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.FLOAT);
    /** A calmer mood waits this long (ticks: 3 s) after the last change, so the face does not flicker (my pick). */
    static final int MOOD_HOLD = 60;
    /** A player sprinting within this many blocks, or standing (not crouching) within the second, makes it wary (my picks). */
    static final double SPRINT_WARY = 10.0, TOO_CLOSE = 3.5;
    /** Another otter this near (blocks), fed and safe in the day, it is playful (my pick; play itself comes later). */
    static final double PLAYMATE_NEAR = 6.0;
    /** Hit within this long (ticks: 5 s) and not running, it is cross (my pick). */
    static final int CROSS_FOR = 100;

    /** The model is made at a grown otter's size (2.6 blocks nose to tail tip; 16 px a block); drawn at this share. */
    public static final float MODEL_SCALE = 1.0F;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): the swim clip's waterline (11.3 px). */
    public static final double AFLOAT = 11.3 / 16.0;
    /** Diving, it swims this far under the surface (blocks, at size 1; the bed stops it in shallower water). */
    static final double DIVE_DEPTH = 1.9;
    /** The clips' made speeds (blocks a tick, at size 1): the walk 1.12 blocks a second, the bound (and the play bound) 2.5, the swims (my measure of the clips' strokes). */
    static final double WALK_CLIP_SPEED = 1.123 / 20.0, BOUND_CLIP_SPEED = 2.5 / 20.0, SWIM_CLIP_SPEED = 0.07, DIVE_CLIP_SPEED = 0.11;
    /** Faster than this (blocks a tick, at size 1) it is drawn bounding, not walking. */
    static final double BOUND_DRAWN = 0.075;
    /** Its speeds (shares of its speed attribute: about 1.1, 2.4 and 5 blocks a second on land, faster in the water). */
    public static final double STROLL = 0.55, BOUND = 0.8, DASH = 1.15;
    /** The clips' lengths (ticks): shaking dry, lying down and getting up from a rest. */
    static final int SHAKE_TICKS = 22, REST_UP_TICKS = 17;
    /** The longest it stays under (ticks: 20 s), and how long it then breathes at the surface before diving again. */
    static final int DIVE_MOST = 400, BREATHE = 80;
    /** It shakes once out of the water after this long in it (ticks). */
    static final int SOAKED = 30;
    /** Its strolls stay within this many blocks of the water it last swam in. */
    static final int NEAR_WATER = 14;
    /** A crocodile or a hippo this near (blocks) sends it away. */
    static final double DANGER_NEAR = 9.0;
    /**
     * The hunt (step 2, my picks): after a meal it is full for 5 to 7.5 minutes; hungry, it looks for prey within
     * {@link #HUNT_REACH} blocks; it feels under the stones for a crab this long before it bites (ticks), the bite's
     * snap lands on the clip's tick 6, and it eats on the bank this long (two rounds of the eat clip's crunches).
     */
    public static final int FULL_TICKS = 6000;
    static final int FULL_MORE = 3000, FEEL_TICKS = 36, BITE_TICKS = 12, BITE_HIT = 6, EAT_TICKS = 64;
    static final double HUNT_REACH = 12.0;
    /** How far ahead of its middle its jaws close, and how high (blocks at size 1: the model's carry socket, 20.9 px ahead, 7.9 up). */
    static final double JAWS_AHEAD = 1.31, JAWS_UP = 0.49;
    /** A hunt with no catch gives up after this long (ticks: 20 s). */
    static final int HUNT_GIVE_UP = 400;

    /**
     * Its moves (the Animal Maker Engine, {@code assets/antcolonies/block_moves/otter.json} written by
     * {@code python tools/otter_models.py game}), played by the shared {@link MoveRider}; pups (under 0.8) step the old way.
     */
    public static final BlockMoves MOVES = BlockMoves.of(OtterEntity.class, SavannaMod.MODID, "otter");
    private final MoveRider rider = new MoveRider(this, new MoveRider.Setup(MOVES, DATA_MOVE)
            .edge(MoveRider.Edge.UP, "up").edge(MoveRider.Edge.DOWN, "down")
            .edge(MoveRider.Edge.IN, "in").edge(MoveRider.Edge.RUN_IN, "dive").edge(MoveRider.Edge.DROP, "dive")
            .edge(MoveRider.Edge.OUT, "out").edge(MoveRider.Edge.OUT_UP, "out_up")
            .modelScale(MODEL_SCALE).feetOut(3.5).afloat(AFLOAT).run(BOUND_DRAWN).smallest(0.8));

    /** The game tick its doing started (for its clips' lengths). */
    int doingSince;
    /** The play bout it is in, or null; when it next tries to start one; ticks left to lie down after one. */
    @Nullable
    Bout bout;
    int playTryAt, restNext;
    /** Resting with a playmate after a bout: the spot beside it to lie down on, and the way to face; null otherwise. */
    @Nullable
    Vec3 restBeside;
    float restYaw;
    /** When it may porpoise next in a swimming chase (a game tick). */
    int porpoiseAt;
    /** Ticks under water this dive, and ticks before it may dive again. */
    int diveFor, breatheFor;
    /** Ticks it has pressed against a bank in the water. */
    private int pressedFor;
    /** Ticks it has been in the water (to shake once out). */
    int soakedFor;
    /** Ticks before it is hungry again. */
    int hungryIn;
    /** Ticks left to go straight at the move control's wanted spot with no route (the hunt's last few blocks). */
    int straightFor;
    /** What its jaws hold now (its catch, riding it so it is carried along), or null. */
    @Nullable
    LivingEntity held;
    /** Going into the water for fun with the leaping dive (set by its swim about). */
    boolean leapForFun;
    /** What it runs from now, and until when (its tick). */
    @Nullable
    Entity scare;
    int scaredUntil;
    /** The body's clip player. */
    @Nullable
    public SteadyController<OtterEntity> bodyClips;
    /** Where its eyes and head go (drawn only): a player within 14 blocks (5 crouching), a crocodile or hippo within 16, anything else stirring within 8. */
    public final Attention attention = new Attention(16.0, OtterEntity::catchesEye);
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Its hit boxes along the drawn body ({@link HitChain}; my picks measured on its model: the snout's front 1.3 ahead of
     * its middle, the tail tip 1.3 behind): its walking box is only the middle of a body 2.6 blocks long.
     */
    private final HitChain chain = new HitChain(this,
            new HitChain.Link("head", 0.95, 0.2, 0.45F, 0.5F),
            new HitChain.Link("rump", -0.55, 0.0, 0.55F, 0.6F),
            new HitChain.Link("tail", -1.0, 0.1, 0.35F, 0.3F));

    private static double catchesEye(Entity seen, double distance, boolean moving) {
        if (seen instanceof OtterEntity) {
            return 0;
        }
        if (seen instanceof Player player) {
            return Attention.playerInReach(player, distance, 14.0, 5.0) ? 0.5 : 0;
        }
        if (seen instanceof CrocodileEntity || seen instanceof HippoEntity) {
            return 0.35;
        }
        return moving && distance < 8.0 ? 1.0 : 0;
    }

    public OtterEntity(EntityType<? extends OtterEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new OtterMoveControl(this);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeGoal(this));
        goalSelector.addGoal(1, new PlayGoal(this));
        goalSelector.addGoal(1, new FollowFamilyGoal(this));
        goalSelector.addGoal(2, new HuntGoal(this));
        goalSelector.addGoal(2, new RestGoal(this));
        goalSelector.addGoal(3, new SwimAboutGoal(this));
        goalSelector.addGoal(4, new SkitterGoal(this));
        goalSelector.addGoal(4, new RandomStrollGoal(this, STROLL, 100, false) {
            @Override
            public boolean canUse() {
                return !skittery() && super.canUse();      // curious or playful, it skitters instead
            }
        });
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        MoveRider.define(builder, DATA_MOVE);
        builder.define(DATA_DOING, CALM);
        builder.define(DATA_DIVING, false);
        builder.define(DATA_MOOD, CALM_MOOD);
        builder.define(DATA_GAIT, EVEN);
        builder.define(DATA_CAUGHT_AT, 90.0F);
    }

    public MoveRider rider() {
        return rider;
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        if (doing != doing()) {
            doingSince = tickCount;
        }
        entityData.set(DATA_DOING, doing);
    }

    int doingFor() {
        return tickCount - doingSince;
    }

    public boolean diving() {
        return entityData.get(DATA_DIVING);
    }

    void setDiving(boolean on) {
        if (on && breatheFor > 0) {
            return;
        }
        if (!on && diving()) {
            breatheFor = BREATHE;
            diveFor = 0;
        }
        entityData.set(DATA_DIVING, on);
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** How it goes on land now ({@link #EVEN}, {@link #SCURRY} or {@link #SNIFF}). */
    public byte gait() {
        return entityData.get(DATA_GAIT);
    }

    void setGait(byte gait) {
        entityData.set(DATA_GAIT, gait);
    }

    /**
     * In the mood to play (section 1): fed (a pup always), calm and safe, not carrying anything, not still to shake
     * itself dry, in the day or at dusk. Its own rule, not the mood: a player near makes it curious, and play must
     * still happen in front of the player.
     */
    boolean playful() {
        return (isFed() || life.isYoung()) && couldAnswer() && (isInWater() || soakedFor < SOAKED)
                && dayTime() < 13000L;
    }

    /** The time of day (0 to 24000) from the one shared day clock (a game test's own clock too), never the world's own. */
    long dayTime() {
        return com.antcolonies.colony.Colony.dayTimeAt(level(), blockPosition()) % 24000L;
    }

    /** Free to answer a pup's invitation: calm, safe, hands free, not on a move. */
    boolean couldAnswer() {
        return doing() == CALM && !scared() && !isCross() && held == null && rider.play() == null && !isDeadOrDying();
    }

    /** Hit within {@link #CROSS_FOR} ticks. */
    boolean isCross() {
        return getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < CROSS_FOR;
    }

    /** Why it would or would not play now (for tests' failure messages). */
    public String playState() {
        return "playful " + playful() + " (fed " + isFed() + ", doing " + doing() + ", scared " + scared() + ", cross " + isCross()
                + ", held " + (held != null) + ", move " + (rider.play() != null) + ", soaked " + soakedFor + ", day " + dayTime()
                + ", water " + isInWater() + ", bout " + (bout == null ? "none" : bout.stage + " rounds " + bout.rounds) + ") next try in " + (playTryAt - tickCount) + ", mood " + mood();
    }

    /** Hops this many more times in its war dance (left and right in turn), the next one to its left if true. */
    int danceLeft;
    boolean danceToLeft;
    /** The heading it keeps through a side hop (Minecraft turns a moving mob's body to where it goes: filmed, it swung round). */
    float hopYaw;

    /** Starts a war dance of 2 to 4 side hops (from standing). */
    void startDance() {
        danceLeft = 2 + random.nextInt(3);
        danceToLeft = random.nextBoolean();
        nextHop();
    }

    /** Still in its war dance (a hop now, or more to come; the otter's own tick takes the next one). */
    boolean dancing() {
        byte d = doing();
        return d == SIDE_L || d == SIDE_R || danceLeft > 0;
    }

    private void nextHop() {
        getNavigation().stop();
        hopYaw = getYRot();
        setDoing(danceToLeft ? SIDE_L : SIDE_R);
        danceToLeft = !danceToLeft;
        danceLeft--;
    }

    /** In a play bout now (for tests). */
    public boolean inBout() {
        return bout != null;
    }

    /** Full now (for tests and the film camera), as after a meal. */
    public void makeFed() {
        hungryIn = FULL_TICKS;
    }

    /** In the mood to skitter about (curious or playful: the burst-and-stop wandering instead of the even stroll). */
    boolean skittery() {
        byte m = mood();
        return m == CURIOUS || m == PLAYFUL;
    }

    /** Its mood now (one of {@link #MOODS}). */
    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    /** The game tick its mood last changed. */
    private int moodSince;

    /**
     * Its mood from what it is doing and what is near (the one place that judges it; every 5 ticks on the server).
     * A custom name "mood:wary" (a name tag) or a tag "mood.wary" (the film camera's walker, named already) holds a
     * mood for looking at it.
     */
    byte workOutMood() {
        net.minecraft.network.chat.Component name = getCustomName();
        for (byte i = 0; i < MOODS.length; i++) {
            if (name != null && name.getString().equals("mood:" + MOODS[i]) || getTags().contains("mood." + MOODS[i])) {
                return i;
            }
        }
        if (scared()) {
            return SCARED;
        }
        if (isCross()) {
            return CROSS;
        }
        if (bout != null) {
            return PLAYFUL;                                  // the open-mouthed play face
        }
        byte d = doing();
        if (d == EAT || d == REST || d == REST_UP || d == SHAKE) {
            return CONTENT;
        }
        Player p = level().getNearestPlayer(this, 14.0);
        if (p != null && !p.isSpectator() && !p.isInvisible()) {
            double dist = distanceTo(p);
            if (p.isSprinting() && dist < SPRINT_WARY || !p.isCrouching() && dist < TOO_CLOSE) {
                return WARY;
            }
            if (Attention.playerInReach(p, dist, 14.0, 5.0)) {
                return CURIOUS;
            }
        }
        if (isFed() && dayTime() < 12500L && !level().getEntitiesOfClass(OtterEntity.class,
                getBoundingBox().inflate(PLAYMATE_NEAR), o -> o != this && o.isAlive()).isEmpty()) {
            return PLAYFUL;
        }
        return CALM_MOOD;
    }

    private void updateMood() {
        byte now = mood(), want = workOutMood();
        if (want != now && (want > now || tickCount - moodSince >= MOOD_HOLD)) {
            entityData.set(DATA_MOOD, want);
            moodSince = tickCount;
        }
    }

    /** Standing still on land doing nothing else (its mood's standing stance plays then; drawing too). */
    public boolean standing() {
        return !going() && !swimming() && doing() == CALM && rider.play() == null && !isDeadOrDying();
    }

    /** Whether it is scared of something now (fleeing). */
    public boolean scared() {
        return scare != null && tickCount < scaredUntil;
    }

    void scare(@Nullable Entity by, int ticks) {
        scare = by;
        scaredUntil = tickCount + ticks;
        if (doing() == REST) {
            setDoing(REST_UP);
        } else if (doing() == SKID || doing() == HOP || doing() == POUNCE || doing() == ZIG_L || doing() == ZIG_R) {
            setDoing(CALM);                                  // off at once
        }
        setGait(EVEN);
    }

    /**
     * The speed setting (a share of its speed attribute, as routes take) that runs it this many blocks a second on
     * level land. Minecraft squares it on land: a mob's walk input is its speed, and its push each tick is its speed
     * times that input, kept up against ground drag (0.6 x 0.91 a tick), so it runs (attribute x setting)^2 / 0.454
     * blocks a tick (worked out 2026-11-23 from LivingEntity.travel; the dash, 1.15, gives 5.2 b/s, measured 5.0 round
     * bends).
     */
    static double speedFor(OtterEntity o, double blocksPerSecond) {
        return Math.sqrt(blocksPerSecond / 20.0 * 0.454) / o.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /**
     * Keeps otters out of each other's bodies (2026-11-23; the user: "they still consistently clip into each other ...
     * it's just the hitboxes"). Minecraft keeps mobs apart by their walking boxes only, and an otter's is the middle 0.75
     * of a body 2.6 long, so one walked its head through the other's flank. Here the body is its middle and the head and
     * rump beads of its {@link HitChain} (the tail left free), each a circle its bead's width across; where two otters'
     * circles overlap, each eases itself away by a share of the overlap a tick, the way Minecraft nudges mobs. Left out:
     * touching on purpose in a bout (rearing, the poke, the pounce), and a resting otter is not pushed (the one coming
     * close gives way). Resting flank to flank (middles 0.9 apart) and noses meeting at the poke do not overlap. My picks.
     */
    private void keepApart() {
        if (isPassenger() || isVehicle() || rider.play() != null || doing() == REST) {
            return;
        }
        Vec3 v = getDeltaMovement();
        double ax = 0.0, az = 0.0;
        for (OtterEntity o : level().getEntitiesOfClass(OtterEntity.class, getBoundingBox().inflate(3.0 * getScale()),
                o -> o != this && o.isAlive() && !o.isPassenger() && !o.isVehicle())) {
            if (bout != null && bout == o.bout && (touching(doing()) || touching(o.doing())) || Math.abs(o.getY() - getY()) > 1.0) {
                continue;
            }
            double share = o.doing() == REST ? 1.0 : 0.5;          // a resting one does not give way: this one does all of it
            double px = 0.0, pz = 0.0;
            for (int i = -1; i < 2; i++) {
                for (int j = -1; j < 2; j++) {
                    Entity a = i < 0 ? this : chain.parts()[i], b = j < 0 ? o : o.chain.parts()[j];
                    double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                    double over = 0.5 * (a.getBbWidth() + b.getBbWidth()) - d;
                    if (over > 0.0) {
                        if (d < 1.0E-3) {
                            dx = getX() - o.getX() + 0.01;
                            dz = getZ() - o.getZ();
                            d = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
                        }
                        px += dx / d * over * share;
                        pz += dz / d * over * share;
                    }
                }
            }
            double len = Math.sqrt(px * px + pz * pz);
            if (len < 1.0E-4) {
                continue;
            }
            // whichever of the two is going: both step out of its line, each to the side it is already on (a push along
            // the line only stalls the one going and shoves the other ahead of it)
            Vec3 go = v.horizontalDistanceSqr() >= o.getDeltaMovement().horizontalDistanceSqr() ? v : o.getDeltaMovement();
            double speed = go.horizontalDistance();
            if (speed > 0.02) {
                double lx = -go.z / speed, lz = go.x / speed;
                double at = lx * (getX() - o.getX()) + lz * (getZ() - o.getZ());
                double side = (Math.abs(at) > 0.05 ? at > 0.0 : getId() < o.getId()) ? 1.5 : -1.5;   // (dead in line: opposite ways)
                px += lx * len * side;
                pz += lz * len * side;
            }
            ax += px;
            az += pz;
        }
        double len = Math.sqrt(ax * ax + az * az);
        if (len < 1.0E-4) {
            return;
        }
        double push = Math.min(len * 0.6, 0.2);                    // a share of the overlap a tick, at most 4 b/s
        setDeltaMovement(v.add(ax / len * push, 0.0, az / len * push));
    }

    /** How deep its body goes into another otter's (blocks: the deepest overlap of their middle, head and rump circles). */
    public double bodyOverlap(OtterEntity o) {
        double most = 0.0;
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                Entity a = i < 0 ? this : chain.parts()[i], b = j < 0 ? o : o.chain.parts()[j];
                double d = Math.sqrt((a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getZ() - b.getZ()) * (a.getZ() - b.getZ()));
                most = Math.max(most, 0.5 * (a.getBbWidth() + b.getBbWidth()) - d);
            }
        }
        return most;
    }

    /** Touching on purpose in a bout: rearing face to face, the poke and the nip, the pounce. */
    private static boolean touching(byte d) {
        return d == PLAY_UP || d == PLAY || d == PLAY_DOWN || d == BITE || d == POUNCE;
    }

    /** Faces a place, body and head (a little at a time). */
    void face(double x, double z, float step) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, step);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Afloat or under: in water and off the bed. */
    public boolean swimming() {
        return isInWater() && !onGround();
    }

    // --- moving ------------------------------------------------------------------------------------------------------

    /** In water it swims where it points, weightless; on a move the move places it. */
    @Override
    public void travel(Vec3 travelVector) {
        if (rider.travel(doing() == CALM && !isDeadOrDying())) {
            return;
        }
        if (isControlledByLocalInstance() && isInWater()) {
            // at the surface it stays at its float depth (its tick holds it there): the swim steering's climb toward a
            // bank's top lifted it off it, so it bumped the bank and hopped out the old way instead of climbing out
            moveRelative(getSpeed(), diving() ? travelVector : new Vec3(travelVector.x, 0.0, travelVector.z));
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
            // against a bank it has no move for, after a second, Minecraft's hop out (sooner, the hop lifted it off its
            // float depth so its own climb out never started)
            pressedFor = horizontalCollision ? pressedFor + 1 : 0;
            if (pressedFor > 20 && isFree(v.x, v.y + 0.6 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.3, v.z);
            }
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide && isVehicle()) {
            for (String tag : getTags()) {                  // the film camera's catch angle: "caught.<degrees>"
                if (tag.startsWith("caught.")) {
                    entityData.set(DATA_CAUGHT_AT, Float.parseFloat(tag.substring(7)));
                }
                // and what it does with it ("doing.eat", "doing.gulp"), so a film runs the real eating and gulping
                // drawing, not only their clips (a "film:" name plays a clip but leaves it calm)
                byte filmed = "doing.eat".equals(tag) ? EAT : "doing.gulp".equals(tag) ? GULP : CALM;
                if (filmed != CALM && doing() != filmed) {
                    setDoing(filmed);
                }
            }
        }
        super.tick();
        chain.place();
        rider.noteMoved();
        if (level().isClientSide) {
            if (doing() == SIDE_L || doing() == SIDE_R || doing() == ZIG_L || doing() == ZIG_R) {
                yBodyRot = getYRot();                        // the drawn body keeps its heading through a side hop or a zigzag leap
            }
            attention.tick(this);
            rider.wetLooks(0.9, 2.2);
            shakeLooks();
            playLooks();
            return;
        }
        life.tick();
        keepApart();
        if (tickCount % 5 == 0) {
            updateMood();
        }
        if (breatheFor > 0) {
            breatheFor--;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (held != null && (!held.isAlive() || held.getVehicle() != this)) {
            release();
        }
        if (scare != null && (tickCount >= scaredUntil || !scare.isAlive())) {
            scare = null;
        }
        if (isInWater()) {
            leapForFun = false;
        }
        rider.hurry = scared() || leapForFun;
        byte d = doing();
        if (d == SHAKE && doingFor() >= SHAKE_TICKS || d == REST_UP && doingFor() >= REST_UP_TICKS
                || d == BITE && doingFor() >= BITE_TICKS || d == SKID && doingFor() >= SKID_TICKS
                || (d == HOP || d == SIDE_L || d == SIDE_R) && doingFor() >= HOP_TICKS || d == PLAY_DOWN && doingFor() >= PLAY_DOWN_TICKS
                || d == GULP && doingFor() >= GULP_TICKS || d == POUNCE && doingFor() >= POUNCE_TICKS
                || d == PORPOISE && (doingFor() >= PORPOISE_TICKS || !isInWater())
                || (d == ZIG_L || d == ZIG_R) && doingFor() >= ZIG_TICKS + 2) {
            setDoing(CALM);
        } else if ((d == PLAY_UP || d == PLAY) && bout == null) {
            setDoing(PLAY_DOWN);                             // a bout broken off: down at once
        }
        if (danceLeft > 0 && doing() == CALM) {
            if (onGround() && !isInWater() && !scared()) {
                nextHop();                                   // the war dance's next hop, the other way
            } else {
                danceLeft = 0;
            }
        }
        if (getTags().contains("dance.now") && doing() == CALM && danceLeft == 0 && tickCount % 40 == 0) {
            startDance();                                    // (the film camera's tag: dance now and then)
        }
        if (doing() == SIDE_L || doing() == SIDE_R) {
            setYRot(hopYaw);                                 // faces the same way all through the hop
            yBodyRot = hopYaw;
            yHeadRot = hopYaw;
        }
        if ((d == SIDE_L || d == SIDE_R) && doingFor() >= HOP_UP && doingFor() < HOP_DOWN) {
            float yaw = hopYaw * Mth.DEG_TO_RAD;             // carried to its left (+) or right while its feet are up
            double step = (d == SIDE_L ? 1.0 : -1.0) * SIDE_GO * getScale() / (HOP_DOWN - HOP_UP);
            move(MoverType.SELF, new Vec3(Mth.cos(yaw) * step, 0.0, Mth.sin(yaw) * step));
        }
        if (d == PORPOISE && (doingFor() == PORPOISE_OUT || doingFor() == PORPOISE_IN)) {
            porpoiseSplash(doingFor() == PORPOISE_OUT ? 0.6 : 0.3);
        }
        if (restNext > 0) {
            restNext--;
        }
        if (isInWater()) {
            soakedFor = Math.min(soakedFor + 1, 1000);
            if (tickCount % 20 == 0) {
                restrictTo(blockPosition(), NEAR_WATER);     // its strolls stay near the water it last swam in
            }
            if (d == REST) {
                setDoing(REST_UP);
            }
        } else if (soakedFor >= SOAKED && onGround() && rider.play() == null && doing() == CALM && !scared()) {
            setDoing(SHAKE);                                 // out of the water: a good shake from nose to tail
            getNavigation().stop();
            soakedFor = 0;
        } else if (!isInWater() && rider.play() == null && soakedFor > 0 && soakedFor < SOAKED) {
            soakedFor = 0;
        }
        // in the water it hangs at the surface, head and back out, or dives and swims under
        if (isInWater() && rider.play() == null) {
            if (diving() && ++diveFor > DIVE_MOST) {
                setDiving(false);                            // up for air
            }
            double depth = getFluidHeight(FluidTags.WATER) - (diving() ? DIVE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, diving() ? -0.04 : -0.02, 0.04) + v.y * 0.5, v.z);
        } else if (!isInWater() && diving()) {
            setDiving(false);
        }
    }

    /**
     * A porpoise leap's splash, out of the water and back in, this far ahead of its middle (blocks at size 1: it leaves
     * nose first ahead and comes down a little behind that, as it swims on under the arc; my picks).
     */
    private void porpoiseSplash(double ahead) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        double size = getScale();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double x = getX() - Mth.sin(yaw) * ahead * size, z = getZ() + Mth.cos(yaw) * ahead * size;
        double y = getY() + getFluidHeight(FluidTags.WATER);
        server.sendParticles(ParticleTypes.SPLASH, x, y + 0.05, z, 16, 0.25 * size, 0.05, 0.25 * size, 0.25);
        server.sendParticles(ParticleTypes.BUBBLE, x, y - 0.2, z, 6, 0.2 * size, 0.1, 0.2 * size, 0.05);
        playSound(SoundEvents.GENERIC_SPLASH, 0.3F, 1.5F + random.nextFloat() * 0.3F);
    }

    /**
     * Drawing only: rearing and batting in the shallows, a splash where its paws come down, every 0.3 s (the play_rear
     * clip's beats, the paws taking turns), and now and then the sound of it.
     */
    private void playLooks() {
        if (doing() != PLAY || !isInWater() || tickCount % 6 != 0) {
            return;
        }
        double size = getScale();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double side = (tickCount % 12 == 0 ? 0.2 : -0.2) * size, ahead = REAR_REACH * 0.9 * size;
        double x = getX() - Mth.sin(yaw) * ahead + Mth.cos(yaw) * side, z = getZ() + Mth.cos(yaw) * ahead + Mth.sin(yaw) * side;
        double y = getY() + getFluidHeight(FluidTags.WATER);
        for (int i = 0; i < 7; i++) {
            // (a splash given no upward speed but a sideways one pops up off the surface: Minecraft's splash particle)
            double a = random.nextDouble() * Math.PI * 2.0, v = 0.04 + random.nextDouble() * 0.06;
            level().addParticle(ParticleTypes.SPLASH, x + Math.cos(a) * 0.15, y + 0.02, z + Math.sin(a) * 0.15,
                    Math.cos(a) * v, 0.0, Math.sin(a) * v);
        }
        for (int i = 0; i < 4; i++) {                        // white wake on the surface (the drops alone hardly show on water)
            double a = random.nextDouble() * Math.PI * 2.0;
            level().addParticle(ParticleTypes.FISHING, x + Math.cos(a) * 0.2, y + 0.02, z + Math.sin(a) * 0.2,
                    Math.cos(a) * 0.03, 0.01, Math.sin(a) * 0.03);
        }
        if (tickCount % 24 == 0) {
            level().playLocalSound(x, y, z, SoundEvents.GENERIC_SPLASH, getSoundSource(), 0.15F, 1.4F + random.nextFloat() * 0.3F, false);
        }
    }

    /** Drawing only: water flung off in a spray as it shakes, from the head back to the tail (the clip's ticks 2 to 18). */
    private void shakeLooks() {
        if (doing() != SHAKE) {
            return;
        }
        int t = doingFor();
        if (t < 2 || t > 18) {
            return;
        }
        if (t == 2) {
            rider.dry();
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.WOLF_SHAKE, getSoundSource(), 0.5F,
                    1.5F + (random.nextFloat() - 0.5F) * 0.2F, false);
        }
        double size = getScale();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double along = (0.9 - (t - 2) / 16.0 * 1.9) * size;        // the wave runs from the head to the tail
        double cx = getX() - Mth.sin(yaw) * along, cz = getZ() + Mth.cos(yaw) * along;
        for (int i = 0; i < 3; i++) {
            double side = (random.nextBoolean() ? 1 : -1) * (0.25 + random.nextDouble() * 0.2) * size;
            double sx = Mth.cos(yaw) * side, sz = Mth.sin(yaw) * side;
            level().addParticle(ParticleTypes.SPLASH, cx + sx, getY() + 0.45 * size, cz + sz, sx * 0.4, 0.15, sz * 0.4);
        }
    }

    // --- life, saving, spawning --------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            rider.cut();
            release();                                   // a fright and its catch is dropped
            if (isAlive()) {
                scare(source.getEntity(), 200);
                // its family hears it and runs too
                for (OtterEntity mate : level().getEntitiesOfClass(OtterEntity.class, getBoundingBox().inflate(12.0), o -> o != this)) {
                    mate.scare(source.getEntity(), 160);
                }
            }
        }
        return hurt;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        boolean world = spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
        if (world) {
            BlockPos bank = MonitorEntity.bankNear(level, blockPosition(), BANK_REACH);    // spawned in the water by a bank: onto the bank
            if (bank != null) {
                moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            }
        }
        life.setUp(world && life.spawnsYoung(level.getRandom()), level.getRandom());
        return out;
    }

    /** How far from a bank (sideways) the world may spawn it in the water. */
    public static final int BANK_REACH = 6;

    /**
     * Where the world may spawn a family: in the water of the savanna's rivers and oxbows within {@link #BANK_REACH} of a
     * bank to stand on, in the river animals' height band. Any height in the water passes (Minecraft's spawner keeps one
     * random height for a try); the world's spawns are set on the bank after ({@link #finalizeSpawn}).
     */
    public static boolean canSpawn(EntityType<OtterEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 8 && pos.getY() <= sea + 6 && level.getFluidState(pos).is(FluidTags.WATER)
                && MonitorEntity.bankNear(level, pos, BANK_REACH) != null;
    }

    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        life.apply();
        hungryIn = tag.getInt("HungryIn");
    }

    // --- the catch ---------------------------------------------------------------------------------------------------

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /** What it holds in its jaws now, or null (for tests). */
    @Nullable
    public LivingEntity held() {
        return held;
    }

    /** What it hunts: crabs, barbs, tilapia and bullfrogs. */
    static boolean isPrey(Entity e) {
        return e instanceof CrabEntity || e instanceof BarbEntity || e instanceof TilapiaEntity || e instanceof BullfrogEntity;
    }

    /** Where its jaws close: the snout's front (its carry socket). */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + JAWS_UP * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** Whether something is at its jaws (its box within reach of the snout). */
    boolean atJaws(Entity e, double reach) {
        return e.getBoundingBox().inflate(reach).contains(jawPoint());
    }

    /**
     * Its jaws close on its catch: the prey rides it from now on, held crosswise between its teeth, still (its own will
     * off) and kept from drowning or dying in the air while it is carried, until it is eaten.
     */
    boolean grab(LivingEntity prey) {
        if (!prey.startRiding(this, true)) {
            return false;
        }
        held = prey;
        entityData.set(DATA_CAUGHT_AT, Mth.wrapDegrees(prey.getYRot() - yBodyRot));
        if (prey instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
        }
        prey.setInvulnerable(true);
        playSound(SoundEvents.FOX_BITE, 0.6F, 1.4F);
        return true;
    }

    /** How long it stays full yet (ticks; for tests). */
    public int fullFor() {
        return hungryIn;
    }

    /** The angle its catch was caught at (degrees, + to its right; drawing). */
    public float caughtAt() {
        return entityData.get(DATA_CAUGHT_AT);
    }

    /** Lets go of its catch (scared off, or the hunt is over). */
    void release() {
        LivingEntity was = held;
        held = null;
        if (was != null) {
            if (was.getVehicle() == this) {
                was.stopRiding();
            }
            if (was instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(false);
            }
            was.setInvulnerable(false);
        }
    }

    /** Eats its catch: gone (no drops), and it is full (a small fish swallowed at once: for a while). */
    void eatCatch() {
        eatCatch(false);
    }

    void eatCatch(boolean small) {
        LivingEntity was = held;
        held = null;
        if (was != null) {
            was.stopRiding();
            was.discard();
        }
        int full = FULL_TICKS + random.nextInt(FULL_MORE);
        hungryIn = small ? (int) (full * SMALL_MEAL) : full;
        heal(small ? 1.0F : 4.0F);
    }

    /** Nobody steers an otter: a catch in its jaws rides it but must not drive it. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Its catch held crosswise between its teeth, at its snout's front. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Vec3 jaws = jawPoint();
        double down = doing() == EAT ? 0.25 * getScale() : 0.0;        // the head down over the catch as it eats
        callback.accept(passenger, jaws.x, jaws.y - passenger.getBbHeight() * 0.5 - down, jaws.z);
        if (passenger instanceof LivingEntity living) {
            float across = yBodyRot + 90.0F;
            living.setYRot(across);
            living.yBodyRot = across;
            living.yHeadRot = across;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            release();
        }
        super.remove(reason);
    }

    /** Its own dives decide its breath (up after 20 s at most, DIVE_MOST): Minecraft's air would drown it afloat, since
     *  its eye point sits just under the surface at its float depth (BUGS 288). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;                  // its chirps and squeaks (a fox's, pitched up)
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (life.isYoung() ? 1.7F : 1.35F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    /**
     * Minecraft's smooth swimming control stands still once its route is done; this one also goes straight at a wanted
     * spot with no route while {@code straightFor} lasts (the hunt's last few blocks at its prey, on land or swimming).
     */
    static class OtterMoveControl extends SmoothSwimmingMoveControl {
        private final OtterEntity otter;

        OtterMoveControl(OtterEntity otter) {
            super(otter, 85, 10, 0.3F, 1.0F, false);
            this.otter = otter;
        }

        @Override
        public void tick() {
            if (otter.straightFor <= 0 || operation != Operation.MOVE_TO || !otter.getNavigation().isDone()) {
                super.tick();
                return;
            }
            otter.straightFor--;
            double dx = wantedX - otter.getX(), dy = wantedY - otter.getY(), dz = wantedZ - otter.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                otter.setZza(0.0F);
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            otter.setYRot(rotlerp(otter.getYRot(), yaw, 85.0F));
            otter.yBodyRot = otter.getYRot();
            otter.yHeadRot = otter.getYRot();
            float speed = (float) (speedModifier * otter.getAttributeValue(Attributes.MOVEMENT_SPEED));
            if (otter.isInWater()) {
                speed *= 0.3F;
                otter.setSpeed(speed);
                float pitch = Mth.clamp(-(float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -10.0F, 10.0F);
                otter.setXRot(rotlerp(otter.getXRot(), pitch, 5.0F));
                otter.zza = Mth.cos(otter.getXRot() * Mth.DEG_TO_RAD) * speed;
                otter.yya = -Mth.sin(otter.getXRot() * Mth.DEG_TO_RAD) * speed;
            } else {
                otter.setSpeed(speed);
            }
        }
    }

    // --- goals -------------------------------------------------------------------------------------------------------

    /** A random spot within reach that is water open over its top (its column's top water block), or null. */
    @Nullable
    static Vec3 waterSpot(OtterEntity otter, double reach, @Nullable Vec3 awayFrom) {
        RandomSource r = otter.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = reach * (0.4 + 0.6 * r.nextDouble());
            double x = otter.getX() + Math.cos(a) * d, z = otter.getZ() + Math.sin(a) * d;
            if (awayFrom != null && (x - otter.getX()) * (awayFrom.x - otter.getX()) + (z - otter.getZ()) * (awayFrom.z - otter.getZ()) > 0) {
                continue;                                   // toward the danger: no
            }
            BlockPos.MutableBlockPos p = BlockPos.containing(x, otter.getY() + 3, z).mutable();
            for (int k = 0; k < 8; k++, p.move(0, -1, 0)) {
                if (otter.level().getFluidState(p).is(FluidTags.WATER)) {
                    if (otter.level().getFluidState(p.above()).isEmpty() && otter.level().getBlockState(p.above()).isAir()) {
                        return new Vec3(x, p.getY() + 0.2, z);
                    }
                    break;
                }
                if (!otter.level().getBlockState(p).getCollisionShape(otter.level(), p).isEmpty()) {
                    break;
                }
            }
        }
        return null;
    }

    /** A random dry spot on land within reach, near the water, away from a danger if one is given; or null. */
    @Nullable
    static Vec3 landSpot(OtterEntity otter, double reach, @Nullable Vec3 awayFrom) {
        RandomSource r = otter.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = reach * (0.4 + 0.6 * r.nextDouble());
            double x = otter.getX() + Math.cos(a) * d, z = otter.getZ() + Math.sin(a) * d;
            if (awayFrom != null && (x - otter.getX()) * (awayFrom.x - otter.getX()) + (z - otter.getZ()) * (awayFrom.z - otter.getZ()) > 0) {
                continue;
            }
            int y = otter.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            BlockPos at = new BlockPos(Mth.floor(x), y, Mth.floor(z));
            if (Math.abs(y - otter.getY()) <= 3 && roomyGround(otter.level(), at)) {
                return Vec3.atBottomCenterOf(at);
            }
        }
        return null;
    }

    /**
     * The nearest bank it can haul out on, looking out over the water 16 ways from itself: the first dry column each way
     * at the water's top or a block over it, with dry level ground 3 by 3 just past its edge (the spot returned, a
     * block in from the edge). Null if none within reach. (A real otter swims to the nearest bank it can climb out on;
     * a random dry spot could lie behind a one-block wall it cannot get over: the hunt test, 2026-11-22.)
     */
    @Nullable
    static Vec3 nearestBank(OtterEntity otter, double reach) {
        Level level = otter.level();
        BlockPos.MutableBlockPos w = otter.blockPosition().mutable();
        while (level.getFluidState(w.above()).is(FluidTags.WATER) && w.getY() < otter.getY() + 8) {
            w.move(0, 1, 0);
        }
        if (!level.getFluidState(w).is(FluidTags.WATER)) {
            return null;
        }
        int top = w.getY();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0, dx = Math.cos(a), dz = Math.sin(a);
            for (double d = 0.5; d <= reach; d += 0.5) {
                BlockPos col = BlockPos.containing(otter.getX() + dx * d, top, otter.getZ() + dz * d);
                if (level.getFluidState(col).is(FluidTags.WATER)) {
                    continue;
                }
                BlockPos in = BlockPos.containing(otter.getX() + dx * (d + 1.0), top, otter.getZ() + dz * (d + 1.0));
                for (int up = 1; up <= 2; up++) {
                    if (roomyGround(level, in.above(up)) && d < bestD) {
                        bestD = d;
                        best = Vec3.atBottomCenterOf(in.above(up));
                    }
                }
                break;                                      // the first dry thing that way: a bank or not
            }
        }
        return best;
    }

    /**
     * Dry level ground 3 by 3 round a spot (a real bank, not a one-block strip it could not climb out onto: its climb
     * out needs two blocks of ground ahead; the hunt test, 2026-11-22, an otter stuck by a one-wide wall with its fish).
     */
    static boolean roomyGround(Level level, BlockPos at) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty()
                        || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The nearest danger it sees (a crocodile or a hippo within {@link #DANGER_NEAR} blocks), or null. */
    @Nullable
    LivingEntity dangerNear() {
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DANGER_NEAR),
                e -> e.isAlive() && (e instanceof CrocodileEntity || e instanceof HippoEntity));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : near) {
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Hurt, or a crocodile or a hippo near: away fast. On land with the danger on land it makes for the water (taking the
     * leaping dive in) and swims away under; with the danger in the water it gets out and runs along the bank.
     */
    static class FleeGoal extends Goal {
        private final OtterEntity otter;
        private int left;

        FleeGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!otter.scared() && otter.random.nextInt(5) == 0) {
                LivingEntity danger = otter.dangerNear();
                if (danger != null) {
                    otter.scare(danger, 160);
                }
            }
            return otter.scared();
        }

        @Override
        public void start() {
            left = 0;
            route();
        }

        private void route() {
            Entity from = otter.scare;
            Vec3 away = from == null ? null : from.position();
            boolean dangerInWater = from != null && from.isInWater();
            Vec3 to = dangerInWater ? landSpot(otter, 12.0, away) : waterSpot(otter, 14.0, away);
            if (to == null) {
                to = dangerInWater ? waterSpot(otter, 14.0, away) : landSpot(otter, 12.0, away);
            }
            if (to != null) {
                otter.getNavigation().moveTo(to.x, to.y, to.z, DASH);
            }
            left = 60;
        }

        @Override
        public boolean canContinueToUse() {
            return otter.scared();
        }

        @Override
        public void tick() {
            if (otter.isInWater() && otter.scare != null && !otter.scare.isInWater()) {
                otter.setDiving(true);                       // away under the water from a danger on land
            }
            if (--left <= 0 || otter.getNavigation().isDone()) {
                route();
            }
        }

        @Override
        public void stop() {
            otter.getNavigation().stop();
        }
    }

    /** Pups keep near the grown otters of their family: following the nearest within 20 blocks once more than 3 off. */
    static class FollowFamilyGoal extends Goal {
        private final OtterEntity otter;
        @Nullable
        private OtterEntity parent;
        private int again;

        FollowFamilyGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!otter.life.isYoung() || otter.doing() != CALM || otter.bout != null) {
                return false;
            }
            parent = null;
            double best = 20.0 * 20.0;
            for (OtterEntity o : otter.level().getEntitiesOfClass(OtterEntity.class, otter.getBoundingBox().inflate(20.0),
                    o -> o != otter && !o.life.isYoung())) {
                double d = o.distanceToSqr(otter);
                if (d < best) {
                    best = d;
                    parent = o;
                }
            }
            return parent != null && best > 9.0;
        }

        @Override
        public boolean canContinueToUse() {
            return parent != null && parent.isAlive() && otter.distanceToSqr(parent) > 4.0 && otter.doing() == CALM
                    && otter.bout == null;
        }

        @Override
        public void start() {
            again = 0;
        }

        @Override
        public void tick() {
            if (--again <= 0 && parent != null) {
                again = 10;
                double speed = otter.distanceToSqr(parent) > 64.0 ? DASH : BOUND;
                otter.getNavigation().moveTo(parent, speed);
                if (parent.diving() && otter.isInWater()) {
                    otter.setDiving(true);
                } else if (!parent.diving() && otter.diving()) {
                    otter.setDiving(false);
                }
            }
        }

        @Override
        public void stop() {
            otter.getNavigation().stop();
        }
    }

    /**
     * The hunt (step 2, 2026-11-22; my picks, the real clawless otter's ways where they make it better): hungry and
     * grown, it picks the nearest prey within {@link #HUNT_REACH} blocks. A crab it wades up to and feels for under
     * the stones with its clever front paws ({@code forage_feel}) before it bites; a fish it dives after and chases
     * under the water, snapping when its snout is there; a bullfrog it pounces on. The catch is held crosswise in its
     * jaws and carried to the nearest bank, where it climbs out (and shakes itself dry), lies down to it and eats it with
     * a crunch and a wag of its tail. Then it is full for a good while, so a family never clears a river.
     */
    static class HuntGoal extends Goal {
        private final OtterEntity otter;
        @Nullable
        private LivingEntity prey;
        private int left, again;
        @Nullable
        private Vec3 bank;

        HuntGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (otter.isFed() || otter.life.isYoung() || otter.doing() != CALM || otter.scared() || otter.held != null
                    || otter.rider.play() != null || otter.random.nextInt(5) != 0) {
                // (a look round about every 10 ticks: Minecraft checks goals every other tick, odd or even by the
                // creature's id, so "tickCount % 10" never came round for half the otters)
                return false;
            }
            prey = null;
            double best = HUNT_REACH * HUNT_REACH;
            for (LivingEntity e : otter.level().getEntitiesOfClass(LivingEntity.class, otter.getBoundingBox().inflate(HUNT_REACH),
                    e -> e.isAlive() && isPrey(e) && !e.isPassenger())) {
                double d = e.distanceToSqr(otter) * (e instanceof CrabEntity ? 0.6 : 1.0);     // crabs first: its favourite
                if (d < best) {
                    best = d;
                    prey = e;
                }
            }
            return prey != null;
        }

        @Override
        public void start() {
            left = HUNT_GIVE_UP;
            again = 0;
            bank = null;
        }

        @Override
        public boolean canContinueToUse() {
            if (otter.scared() || otter.isDeadOrDying()) {
                return false;
            }
            if (otter.held != null) {
                return true;                                // carrying it to the bank and eating it
            }
            return prey != null && prey.isAlive() && !prey.isPassenger() && --left > 0 && otter.doing() != EAT;
        }

        @Override
        public void tick() {
            if (otter.held != null) {
                carry();
                return;
            }
            if (prey == null) {
                return;
            }
            byte d = otter.doing();
            if (d == FORAGE) {
                otter.face(prey.getX(), prey.getZ(), 20.0F);
                otter.getLookControl().setLookAt(prey);
                if (otter.doingFor() >= FEEL_TICKS) {
                    otter.setDoing(BITE);
                }
                return;
            }
            if (d == BITE) {
                otter.face(prey.getX(), prey.getZ(), 30.0F);
                if (otter.doingFor() == BITE_HIT) {
                    if (otter.atJaws(prey, reach(prey) + 0.3)) {        // a little slack: it moved while felt for
                        if (otter.grab(prey) && HeldCatch.swallowed(prey, otter.random)) {
                            otter.setDoing(GULP);                        // a small fish: down in one
                        }
                        otter.getNavigation().stop();
                    }
                }
                return;
            }
            if (d != CALM || otter.rider.play() != null) {
                return;
            }
            boolean crab = prey instanceof CrabEntity;
            boolean preyUnder = prey.isInWater() && prey.getY() < surfaceOver(prey) - 0.6;
            if (otter.isInWater()) {
                otter.setDiving(preyUnder);                 // down after it, or up at the surface
            }
            if (crab && otter.atJaws(prey, reach(prey))) {
                otter.getNavigation().stop();
                otter.setDoing(FORAGE);                     // its paws feel under the stones round the crab
                return;
            }
            if (!crab && otter.atJaws(prey, reach(prey))) {
                otter.getNavigation().stop();
                otter.setDoing(BITE);
                return;
            }
            if (otter.distanceToSqr(prey) < 3.5 * 3.5) {
                // the last few blocks straight at it, facing it (a route ends a block or two off, facing anywhere)
                otter.getNavigation().stop();
                otter.face(prey.getX(), prey.getZ(), 20.0F);
                otter.straightFor = 3;
                otter.getMoveControl().setWantedPosition(prey.getX(), otter.isInWater() ? prey.getY() : otter.getY(), prey.getZ(),
                        crab ? STROLL : DASH);
                return;
            }
            if (--again <= 0) {
                again = 8;
                otter.getNavigation().moveTo(prey, crab ? BOUND : DASH);
            }
        }

        /**
         * How near its jaws must be to the prey's box to feel for it or snap (one reach for both, the bite a little
         * more: the hunt test, 2026-11-22, an otter feeling and biting forever at a crab a block down that was "near"
         * for the feeling but out of its bite).
         */
        private static double reach(Entity prey) {
            return prey instanceof CrabEntity ? 0.7 : 0.4;
        }

        /** With its catch: to the nearest bank, out, and eat it lying on the bank (a small one gulped down where it is). */
        private void carry() {
            if (otter.doing() == GULP) {
                if (otter.doingFor() >= GULP_TAKE) {
                    otter.eatCatch(true);
                }
                return;
            }
            if (otter.doing() == EAT) {
                if (otter.doingFor() >= EAT_TICKS) {
                    otter.eatCatch();
                    otter.setDoing(CALM);
                }
                return;
            }
            otter.setDiving(false);
            if (!otter.isInWater() && otter.onGround() && otter.rider.play() == null && otter.doing() == CALM) {
                otter.getNavigation().stop();
                otter.setDoing(EAT);
                return;
            }
            if (otter.isInWater() && (bank == null || otter.getNavigation().isDone())) {
                bank = nearestBank(otter, 12.0);
                if (bank == null) {
                    bank = landSpot(otter, 10.0, null);
                }
                if (bank != null) {
                    otter.getNavigation().moveTo(bank.x, bank.y, bank.z, BOUND);
                }
            }
        }

        private static double surfaceOver(Entity e) {
            BlockPos.MutableBlockPos p = e.blockPosition().mutable();
            while (e.level().getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < e.getY() + 16) {
                p.move(0, 1, 0);
            }
            return p.getY() + e.level().getFluidState(p).getHeight(e.level(), p);
        }

        @Override
        public void stop() {
            byte d = otter.doing();
            if (d == FORAGE || d == BITE || d == EAT) {
                otter.setDoing(CALM);
            }
            if (otter.held != null) {
                otter.release();
            }
            otter.setDiving(false);
            otter.getNavigation().stop();
            prey = null;
        }
    }

    /**
     * Rests flat on a dry bank now and then, most in the heat of the day (real clawless otters rest through midday and
     * are about in the mornings and evenings): 20 to 60 seconds, eyes shut, unless a player comes near without crouching,
     * it is hurt or a danger shows up.
     */
    static class RestGoal extends Goal {
        private final OtterEntity otter;
        private int left;

        RestGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (otter.doing() != CALM || otter.isInWater() || !otter.onGround() || otter.soakedFor > 0 || otter.scared()
                    || otter.rider.play() != null) {
                return false;
            }
            if (otter.restNext > 0 && !playerNear()) {        // a short rest after a bout of play
                shortOne = true;
                return true;
            }
            if (!otter.isFed() && !otter.life.isYoung()) {
                return false;
            }
            long day = otter.dayTime();
            int chance = day > 4000 && day < 9000 ? 300 : 1500;
            return otter.random.nextInt(chance) == 0 && !playerNear();
        }

        private boolean shortOne;

        private boolean playerNear() {
            Player p = otter.level().getNearestPlayer(otter, 5.0);
            return p != null && !p.isCrouching() && !p.isSpectator() && !p.isCreative();
        }

        /** Resting beside its playmate: the spot to walk to first, and ticks left to get there and turn its way (or 0). */
        @Nullable
        private Vec3 settleTo;
        private int settle;

        @Override
        public void start() {
            otter.getNavigation().stop();
            left = shortOne ? 200 + otter.random.nextInt(200) : 400 + otter.random.nextInt(800);
            shortOne = false;
            otter.restNext = 0;
            settleTo = otter.restBeside;
            otter.restBeside = null;
            settle = settleTo != null ? 60 : 0;
            if (settleTo != null) {
                otter.getMoveControl().setWantedPosition(settleTo.x, settleTo.y, settleTo.z, STROLL);
                otter.straightFor = 60;
            } else {
                otter.setDoing(REST);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (settle <= 0) {
                return;
            }
            settle--;
            Vec3 to = settleTo;
            if (to == null || otter.position().subtract(to).horizontalDistanceSqr() < 0.02 || otter.horizontalCollision) {
                otter.straightFor = 0;
                otter.getMoveControl().setWantedPosition(otter.getX(), otter.getY(), otter.getZ(), 0.0);
                otter.setZza(0.0F);
                float yaw = otter.restYaw * Mth.DEG_TO_RAD;         // turns its playmate's way, then lies down
                otter.face(otter.getX() - Mth.sin(yaw) * 4.0, otter.getZ() + Mth.cos(yaw) * 4.0, 25.0F);
                if (Math.abs(Mth.wrapDegrees(otter.getYRot() - otter.restYaw)) < 8.0F || settle <= 0) {
                    settle = 0;
                    otter.setDoing(REST);
                }
            } else if (settle <= 0) {
                otter.straightFor = 0;
                otter.setDoing(REST);                              // (got as near as it could)
            } else {
                otter.face(to.x, to.z, 30.0F);
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (settle > 0) {
                return !otter.scared() && !otter.isInWater() && !playerNear();
            }
            return otter.doing() == REST && --left > 0 && !otter.scared() && !otter.isInWater() && !playerNear();
        }

        @Override
        public void stop() {
            if (otter.doing() == REST) {
                otter.setDoing(REST_UP);
            }
        }
    }

    /**
     * Swims about: from the water (or the bank near it) to another spot in it, at a bound's pace, diving down on the way
     * one time in three; now and then, after a while in the water, out onto a bank again. Going in from a bank it slides in
     * calmly, or one time in three takes the leaping dive for the fun of it.
     */
    /**
     * Play (2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 1): two otters' bout, run by the one who
     * started it. It bounds over and pokes the other with its nose (a play bite, no harm); they rear up face to face
     * ({@code play_up}, {@code play_rear}), noses nearly touching and paws batting, for a round, drop down, one breaks off
     * at a bound and the other chases it, and they rear again where they meet: 3 to 6 rounds. In water deeper than a
     * block they chase each other swimming and diving instead. It ends when the rounds are done or either is hurt, scared
     * or busy; then a third of the time they lie down for a short rest. A mother answering her pup when she is not in the
     * mood herself (or one time in three when she is) gives it one round only. Numbers my picks.
     * <p>
     * Chase and tag (2026-11-23, plan 6e step 4): after the first round, on land or in the shallows, half the time they
     * play tag instead of more rearing (a tag {@code play.tag} on either always picks it, for films and tests): one nips
     * the other and bounds off in loops (a quarter of a circle 3 to 5 blocks across at a time), the other gives it a short
     * head start and chases it a little faster; after 2.5 seconds or more, with the chaser close, the runner skid-stops
     * and lets itself be caught (as real playing otters do), the chaser pounces with its paws on the runner's rump and
     * nips it; the runner hops out from under the paws, turning round to it, and the tagger bounds off: it is the runner
     * now. 3 to 6 tags. In deep water the swimming chase porpoises now and then. Numbers my picks.
     */
    static final class Bout {
        private enum Stage { INVITE, DANCE, STEP_IN, POKE, SPACE, UP, REAR, DOWN, CHASE, SWIM, NIP, RUN, CATCH, POUNCE }
        final OtterEntity lead, mate;
        private int rounds, left, again;
        private Stage stage = Stage.INVITE;
        private OtterEntity runner;
        private double toX, toZ;
        /** Tag: not picked yet (0), rearing rounds (1) or tag (2). */
        private int game;
        /**
         * Tag's run: where the game started (it keeps near), the bend of the S it heads for, its heading (the run's line,
         * degrees), the way the next bend turns (+1 or -1), ticks run, ticks chased (the chaser's speed ramp), whether
         * this leap lands a block up, and how far to the side it goes (0 when the side is blocked: straight on).
         */
        private double homeX, homeZ, wayX, wayZ, leapSide;
        private float runYaw;
        private int turnWay, runFor, chaseFor;
        private boolean leapUp;
        /** Tag's pounce: whether the runner has hopped out from under, and its hop's whole turn (degrees). */
        private boolean hopped;
        private float hopTurn;

        Bout(OtterEntity lead, OtterEntity mate, int rounds) {
            this.lead = lead;
            this.mate = mate;
            this.rounds = rounds;
            this.runner = lead;
            left = 120;
            lead.bout = this;
            mate.bout = this;
            mate.getNavigation().stop();
        }

        private OtterEntity other(OtterEntity o) {
            return o == lead ? mate : lead;
        }

        /** How far apart their middles stand rearing face to face: their noses nearly touch. */
        double gap() {
            return REAR_REACH * (lead.getScale() + mate.getScale());
        }

        /** Still on: both here, near, not hurt, scared, carrying, resting or eating. */
        boolean on() {
            for (OtterEntity o : new OtterEntity[] {lead, mate}) {
                byte d = o.doing();
                if (!o.isAlive() || o.isRemoved() || o.bout != this || o.scared() || o.held != null || o.isCross()
                        || d == SHAKE || d == REST || d == REST_UP || d == FORAGE || d == EAT) {
                    return false;
                }
            }
            return lead.distanceToSqr(mate) < 16.0 * 16.0;
        }

        void tick() {
            if (!on()) {
                end(false);
                return;
            }
            left--;
            switch (stage) {
                case INVITE -> {
                    mate.face(lead.getX(), lead.getZ(), 15.0F);
                    if (lead.distanceTo(mate) < pokeGap() + 0.5) {
                        lead.getNavigation().stop();
                        if (lead.onGround() && !lead.isInWater()) {
                            lead.startDance();                     // the war dance first, hopping side to side: play?
                            stage = Stage.DANCE;
                            left = 60;
                        } else {
                            stepIn();
                        }
                    } else if (--again <= 0) {
                        again = 10;
                        lead.getNavigation().moveTo(mate, BOUND);
                    }
                    if (left <= 0) {
                        end(false);
                    }
                }
                case DANCE -> {
                    mate.face(lead.getX(), lead.getZ(), 15.0F);  // (the dancer keeps its heading through its hops)
                    if (!lead.dancing() || left <= 0) {
                        lead.danceLeft = 0;
                        if (lead.distanceTo(mate) < pokeGap() + 1.5) {
                            stepIn();
                        } else {
                            stage = Stage.INVITE;                  // danced off a little: bound back in and poke
                            left = 60;
                            again = 0;
                        }
                    }
                }
                case STEP_IN -> {
                    mate.face(lead.getX(), lead.getZ(), 20.0F);
                    double dx = toX - lead.getX(), dz = toZ - lead.getZ();
                    if (dx * dx + dz * dz < 0.03 || left <= 0 || lead.horizontalCollision) {
                        halt(lead);
                        lead.straightFor = 0;
                        poke();
                    } else {
                        lead.face(toX, toZ, 30.0F);
                    }
                }
                case POKE -> {
                    lead.face(mate.getX(), mate.getZ(), 20.0F);
                    mate.face(lead.getX(), lead.getZ(), 20.0F);
                    if (left <= 0) {
                        round();
                    }
                }
                case SPACE -> {
                    lead.face(mate.getX(), mate.getZ(), 20.0F);
                    double dx = toX - mate.getX(), dz = toZ - mate.getZ();
                    if (dx * dx + dz * dz < 0.03 || left <= 0 || mate.horizontalCollision) {
                        mate.straightFor = 0;
                        mate.getNavigation().stop();
                        mate.getMoveControl().setWantedPosition(mate.getX(), mate.getY(), mate.getZ(), 0.0);
                        mate.setZza(0.0F);
                        lead.setDoing(PLAY_UP);
                        mate.setDoing(PLAY_UP);
                        stage = Stage.UP;
                        left = PLAY_UP_TICKS;
                    } else {
                        mate.face(toX, toZ, 30.0F);
                    }
                }
                case UP, REAR -> {
                    lead.face(mate.getX(), mate.getZ(), 6.0F);
                    mate.face(lead.getX(), lead.getZ(), 6.0F);
                    if (left <= 0 && stage == Stage.UP) {
                        lead.setDoing(PLAY);
                        mate.setDoing(PLAY);
                        stage = Stage.REAR;
                        left = 36 + lead.random.nextInt(37);       // one or two rounds of batting (the clip loops in 1.8 s)
                    } else if (left <= 0) {
                        rounds--;
                        lead.setDoing(PLAY_DOWN);
                        mate.setDoing(PLAY_DOWN);
                        stage = Stage.DOWN;
                        left = PLAY_DOWN_TICKS;
                    }
                }
                case DOWN -> {
                    if (left <= 0) {
                        if (rounds <= 0) {
                            end(true);
                        } else if (tagGame()) {
                            nip();
                        } else {
                            chase();
                        }
                    }
                }
                case NIP -> {
                    OtterEntity chaser = other(runner);
                    runner.face(chaser.getX(), chaser.getZ(), 20.0F);
                    chaser.face(runner.getX(), runner.getZ(), 20.0F);
                    if (left <= 0) {
                        run();
                    }
                }
                case RUN -> {
                    OtterEntity chaser = other(runner);
                    runFor++;
                    boolean going = true;
                    byte d = runner.doing();
                    if (d == HOP && runner.doingFor() < HOP_TICKS) {
                        hopTick(runner, 0.0);                      // turning round to bolt
                    } else if (d != ZIG_L && d != ZIG_R || runner.doingFor() >= ZIG_TICKS) {
                        going = leap();                            // the next leap, to the other side
                    }
                    if (going) {
                        going = carry();
                    }
                    chasing(chaser);
                    if (!going || runFor >= 50 && chaser.distanceTo(runner) < POUNCE_FROM * chaser.getScale() + 1.0 || left <= 0) {
                        letCatch();                                // close behind it, or nowhere to go: it lets itself be caught
                    }
                }
                case CATCH -> {
                    OtterEntity chaser = other(runner);
                    runner.getNavigation().stop();
                    if (chaser.distanceTo(runner) < POUNCE_FROM * chaser.getScale()) {
                        pounce();
                    } else {
                        chasing(chaser);
                    }
                    if (left <= 0) {
                        end(true);
                    }
                }
                case POUNCE -> pouncing();
                case CHASE, SWIM -> {
                    OtterEntity chaser = other(runner);
                    if (--again <= 0) {
                        again = 8;
                        chaser.getNavigation().moveTo(runner, chaser.distanceToSqr(runner) > 36.0 ? DASH : BOUND);
                        if (chaser.isInWater() && chaser.doing() != PORPOISE) {
                            chaser.setDiving(runner.diving());
                        }
                    }
                    if (stage == Stage.SWIM) {
                        porpoise(runner);
                        porpoise(chaser);
                    }
                    // caught only after the runner has had a start (they set off nose to nose: counted at once, a
                    // swimming chase's rounds all ran out in its first ticks, BUGS 297)
                    boolean caught = ++runFor > 30 && chaser.distanceTo(runner) < gap() + 1.0;
                    if (stage == Stage.SWIM && (caught || left <= 0)) {
                        rounds--;                                  // tagged: the other one runs now
                        runner.setDiving(false);
                        chaser.setDiving(false);
                        if (rounds <= 0) {
                            end(true);
                        } else {
                            runner = chaser;
                            round();
                        }
                    } else if (stage == Stage.CHASE && (runner.getNavigation().isDone() && caught || left <= 0)) {
                        round();
                    }
                }
            }
        }

        /** Tag or more rearing, picked once, after the first round: tag only where both can stand. */
        private boolean tagGame() {
            if (game == 0) {
                boolean forced = lead.getTags().contains("play.tag") || mate.getTags().contains("play.tag");
                game = canRear(lead) && canRear(mate) && (forced || lead.random.nextBoolean()) ? 2 : 1;
                if (game == 2) {
                    rounds = 3 + lead.random.nextInt(4);           // tags
                    homeX = 0.5 * (lead.getX() + mate.getX());     // the game keeps near here
                    homeZ = 0.5 * (lead.getZ() + mate.getZ());
                }
            }
            return game == 2;
        }

        /** Tag's start: one nips the other (a play bite), then bounds off. */
        private void nip() {
            runner = lead.random.nextBoolean() ? lead : mate;
            runner.getNavigation().stop();
            runner.setDoing(BITE);
            stage = Stage.NIP;
            left = BITE_TICKS;
        }

        /**
         * The runner turns round with a hop and bolts, away from the chaser, which gives it a head start (it watches for
         * {@link #CHASE_WAIT} ticks); then it zigzags off in leaps along an S near where the game started.
         */
        private void run() {
            OtterEntity chaser = other(runner);
            runYaw = (float) Math.toDegrees(Math.atan2(runner.getZ() - chaser.getZ(), runner.getX() - chaser.getX())) - 90.0F;
            turnWay = runner.random.nextBoolean() ? 1 : -1;
            pickWay();
            halt(runner);
            hopTurn = Mth.wrapDegrees(runYaw - runner.getYRot());
            runner.setDoing(HOP);
            runner.doingSince = runner.tickCount;
            runFor = 0;
            chaseFor = 0;
            stage = Stage.RUN;
            left = RUN_MOST;
            again = CHASE_WAIT;
        }

        /**
         * The next bend of the S: 5 to 8 blocks on, turned 50 to 100 degrees off its heading, to the other side from the
         * last bend, within {@link #RUN_ROOM} blocks of where the game started and on footing; else back toward there.
         */
        private void pickWay() {
            double s = runner.getScale();
            for (int k = 0; k < 6; k++, turnWay = -turnWay) {
                float yaw = (runYaw + turnWay * (50.0F + runner.random.nextFloat() * 50.0F)) * Mth.DEG_TO_RAD;
                double d = (5.0 + runner.random.nextDouble() * 3.0) * s;
                double x = runner.getX() - Mth.sin(yaw) * d, z = runner.getZ() + Mth.cos(yaw) * d;
                if ((x - homeX) * (x - homeX) + (z - homeZ) * (z - homeZ) < RUN_ROOM * RUN_ROOM * s * s
                        && footing(runner.level(), x, runner.getY(), z) != null) {
                    wayX = x;
                    wayZ = z;
                    turnWay = -turnWay;
                    return;
                }
            }
            wayX = homeX;
            wayZ = homeZ;
        }

        /**
         * One zigzag leap: its heading turns toward the next bend of the S (30 degrees a leap at most), and it is flung to
         * the other side from the last leap ({@link #ZIG_GO} on and {@link #ZIG_SIDE} aside); straight on if that side has
         * no footing; false if straight on has none either.
         */
        private boolean leap() {
            double s = runner.getScale();
            if ((wayX - runner.getX()) * (wayX - runner.getX()) + (wayZ - runner.getZ()) * (wayZ - runner.getZ()) < 4.0 * s * s) {
                pickWay();
            }
            float want = (float) Math.toDegrees(Math.atan2(wayZ - runner.getZ(), wayX - runner.getX())) - 90.0F;
            runYaw = Mth.approachDegrees(runYaw, want, 30.0F);
            boolean toLeft = runner.doing() != ZIG_L;
            float yaw = runYaw * Mth.DEG_TO_RAD;
            for (double side : new double[] {toLeft ? 1.0 : -1.0, 0.0}) {
                double x = runner.getX() + (-Mth.sin(yaw) * ZIG_GO + Mth.cos(yaw) * side * ZIG_SIDE) * s;
                double z = runner.getZ() + (Mth.cos(yaw) * ZIG_GO + Mth.sin(yaw) * side * ZIG_SIDE) * s;
                Double y = footing(runner.level(), x, runner.getY(), z);
                if (y != null) {
                    leapSide = side;
                    leapUp = y > runner.getY() + 0.5;
                    runner.setDoing(toLeft ? ZIG_L : ZIG_R);
                    runner.doingSince = runner.tickCount;
                    return true;
                }
            }
            return false;
        }

        /**
         * Carries the leap this tick: on at an even pace over the whole leap, aside while its feet are up (from
         * {@link #ZIG_UP}), lifted early when it lands a block up; its heading held on the run's line. False if it hit
         * something.
         */
        private boolean carry() {
            byte d = runner.doing();
            if (d != ZIG_L && d != ZIG_R) {
                return true;
            }
            int t = runner.doingFor();
            double s = runner.getScale();
            float yaw = runYaw * Mth.DEG_TO_RAD;
            double on = ZIG_GO * s / ZIG_TICKS;
            double aside = t >= ZIG_UP ? leapSide * ZIG_SIDE * s / (ZIG_TICKS - ZIG_UP) : 0.0;
            runner.move(MoverType.SELF, new Vec3(-Mth.sin(yaw) * on + Mth.cos(yaw) * aside, leapUp && t < 4 ? 0.3 : 0.0,
                    Mth.cos(yaw) * on + Mth.sin(yaw) * aside));
            runner.setYRot(runYaw);
            runner.yBodyRot = runYaw;
            runner.yHeadRot = runYaw;
            return !runner.horizontalCollision;
        }

        /**
         * The chaser runs straight at the runner (it cuts the corners of its zigzag) once its head start is over, speeding
         * up: {@link #CHASE_FROM} blocks a second, rising to {@link #CHASE_MID} over {@link #CHASE_RAMP} ticks and to
         * {@link #CHASE_TOP} over as many again (the user's numbers, 2026-11-23), times its size.
         */
        private void chasing(OtterEntity chaser) {
            if (again > 0) {
                again--;
                return;
            }
            chaseFor++;
            if (chaseFor % 4 == 1) {
                double ramp = Math.min(chaseFor / (double) CHASE_RAMP, 2.0);
                double bps = ramp < 1.0 ? Mth.lerp(ramp, CHASE_FROM, CHASE_MID) : Mth.lerp(ramp - 1.0, CHASE_MID, CHASE_TOP);
                chaser.getNavigation().moveTo(runner, speedFor(chaser, bps * chaser.getScale()));
            }
        }

        /** Stands it still where it is: no route, no wanted spot, no walking on. */
        private static void halt(OtterEntity o) {
            o.getNavigation().stop();
            o.getMoveControl().setWantedPosition(o.getX(), o.getY(), o.getZ(), 0.0);
            o.setZza(0.0F);
        }

        /** A hop turn's feet-up ticks: turned by {@link #hopTurn} in all, and carried this far forward (blocks at size 1). */
        private void hopTick(OtterEntity o, double forward) {
            int h = o.doingFor();
            if (o.doing() != HOP || h < HOP_UP || h >= HOP_DOWN) {
                return;
            }
            float yaw = o.getYRot();
            double step = forward * o.getScale() / (HOP_DOWN - HOP_UP);
            if (step > 0.0) {
                o.move(MoverType.SELF, new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD) * step, 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD) * step));
            }
            yaw += hopTurn / (HOP_DOWN - HOP_UP);
            o.setYRot(yaw);
            o.yBodyRot = yaw;
            o.yHeadRot = yaw;
        }

        /** The runner skid-stops: it lets itself be caught. */
        private void letCatch() {
            halt(runner);
            runner.setDoing(SKID);
            stage = Stage.CATCH;
            left = 60;
        }

        /** Close behind it: the chaser springs. */
        private void pounce() {
            OtterEntity chaser = other(runner);
            halt(chaser);
            chaser.setDeltaMovement(0.0, chaser.getDeltaMovement().y, 0.0);
            chaser.setDoing(POUNCE);
            hopped = false;
            stage = Stage.POUNCE;
            left = POUNCE_TICKS + HOP_TICKS + 10;
        }

        /**
         * The pounce: the chaser faces the runner as it gathers, is carried onto the spot behind it while its feet are up
         * (its paws come down on the runner's rump), nips, and lets go; the runner then hops out from under, turning round
         * to it; then the tagger is the runner.
         */
        private void pouncing() {
            OtterEntity chaser = other(runner);
            int t = chaser.doingFor();
            if (chaser.doing() == POUNCE) {
                chaser.face(runner.getX(), runner.getZ(), 25.0F);
                if (t >= POUNCE_UP && t < POUNCE_DOWN) {
                    double dx = chaser.getX() - runner.getX(), dz = chaser.getZ() - runner.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 1.0E-3) {
                        double back = POUNCE_BEHIND * chaser.getScale(), share = 1.0 / (POUNCE_DOWN - t);
                        double tx = runner.getX() + dx / d * back, tz = runner.getZ() + dz / d * back;
                        chaser.move(MoverType.SELF, new Vec3((tx - chaser.getX()) * share, 0.0, (tz - chaser.getZ()) * share));
                    }
                }
                if (t >= POUNCE_LET_GO && !hopped) {
                    hopped = true;
                    float toward = (float) Math.toDegrees(Math.atan2(chaser.getZ() - runner.getZ(), chaser.getX() - runner.getX())) - 90.0F;
                    hopTurn = Mth.clamp(Mth.wrapDegrees(toward - runner.getYRot()), -150.0F, 150.0F);
                    runner.setDoing(HOP);
                }
            }
            if (hopped) {
                hopTick(runner, 0.35);                             // out from under the paws, turning round to the tagger
            }
            if (hopped && chaser.doing() != POUNCE && runner.doing() != HOP || left <= 0) {
                rounds--;                                          // tagged
                if (rounds <= 0) {
                    end(true);
                } else {
                    runner = chaser;                               // the tagger runs now
                    run();
                }
            }
        }

        /** In a swimming chase, going in deep water at the surface, now and then a porpoise leap (every 1.5 to 3 s). */
        private static void porpoise(OtterEntity o) {
            if (o.doing() == CALM && o.swimming() && !o.diving() && !o.getNavigation().isDone() && o.tickCount >= o.porpoiseAt
                    && waterDepth(o) >= 2) {
                o.setDoing(PORPOISE);
                o.porpoiseAt = o.tickCount + PORPOISE_TICKS + 30 + o.random.nextInt(31);
            }
        }

        /**
         * How far apart their middles stand for the nose poke so their noses just touch (no head inside a head): the
         * poker's nose at the bite's reach, the other's standing (measured on the clips, 2026-11-23).
         */
        double pokeGap() {
            return NOSE_BITE * lead.getScale() + NOSE_STAND * mate.getScale();
        }

        /** The poker steps in (or back) to the spot in front of the other's face where their noses will meet. */
        private void stepIn() {
            double dx = lead.getX() - mate.getX(), dz = lead.getZ() - mate.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0E-3) {
                dx = 1.0;
                d = 1.0;
            }
            toX = mate.getX() + dx / d * pokeGap();
            toZ = mate.getZ() + dz / d * pokeGap();
            lead.getNavigation().stop();
            lead.getMoveControl().setWantedPosition(toX, lead.getY(), toZ, STROLL);
            lead.straightFor = 30;
            stage = Stage.STEP_IN;
            left = 30;
        }

        /** The nose poke (a play bite): come and play. */
        private void poke() {
            lead.getNavigation().stop();
            lead.setDoing(BITE);
            stage = Stage.POKE;
            left = BITE_TICKS;
        }

        /** The next round: rearing face to face where they can stand, a swimming chase where they cannot. */
        private void round() {
            lead.getNavigation().stop();
            mate.getNavigation().stop();
            if (!canRear(lead) || !canRear(mate)) {
                swim();
                return;
            }
            // the mate steps to the gap from the lead, on the line between them
            double dx = mate.getX() - lead.getX(), dz = mate.getZ() - lead.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0E-3) {
                dx = 1.0;
                d = 1.0;
            }
            toX = lead.getX() + dx / d * gap();
            toZ = lead.getZ() + dz / d * gap();
            mate.getMoveControl().setWantedPosition(toX, mate.getY(), toZ, STROLL);
            mate.straightFor = 30;
            stage = Stage.SPACE;
            left = 30;
        }

        /** One breaks off at a bound, 2 to 5 blocks, and the other chases it. */
        private void chase() {
            runner = lead.random.nextBoolean() ? lead : mate;
            Vec3 to = runner.isInWater() ? waterSpot(runner, 5.0, null) : landSpot(runner, 5.0, null);
            if (to == null || !runner.getNavigation().moveTo(to.x, to.y, to.z, BOUND)) {
                round();
                return;
            }
            stage = Stage.CHASE;
            runFor = 0;
            left = 100;
            again = 6;                                             // the chaser gives it a head start
        }

        /** A swimming chase in deep water, the runner diving one time in three. */
        private void swim() {
            Vec3 to = waterSpot(runner, 7.0, null);
            if (to == null || !runner.getNavigation().moveTo(to.x, to.y, to.z, BOUND)) {
                end(true);
                return;
            }
            runner.setDiving(runner.random.nextInt(3) == 0);
            lead.porpoiseAt = lead.tickCount + 20 + lead.random.nextInt(30);
            mate.porpoiseAt = mate.tickCount + 20 + mate.random.nextInt(30);
            stage = Stage.SWIM;
            runFor = 0;
            left = 120;
            again = 6;
        }

        /** Over: both drop down, and a third of the time, on land, they lie down for a short rest. */
        void end(boolean done) {
            boolean rest = done && (lead.random.nextInt(3) == 0 || lead.getTags().contains("play.rest"));
            for (OtterEntity o : new OtterEntity[] {lead, mate}) {
                if (o.bout != this) {
                    continue;
                }
                o.bout = null;
                o.getNavigation().stop();
                o.straightFor = 0;
                o.setDiving(false);
                o.playTryAt = o.tickCount + PLAY_AFTER;
                if (o.doing() == PLAY_UP || o.doing() == PLAY) {
                    o.setDoing(PLAY_DOWN);
                }
                if (rest && !o.isInWater()) {
                    o.restNext = 100;
                }
            }
            if (rest && mate.restNext > 0) {
                // they rest together, flank to flank the same way round (as real otters do), not one inside the other
                mate.restBeside = besideSpot(lead, mate);
                mate.restYaw = lead.getYRot();
                if (mate.restBeside == null) {
                    mate.restNext = 0;
                }
            }
        }

        /** The spot beside the lead, flank to flank, on the side nearer the mate first, on footing; or null. */
        @Nullable
        private static Vec3 besideSpot(OtterEntity lead, OtterEntity mate) {
            float yaw = lead.getYRot() * Mth.DEG_TO_RAD;
            double lx = Mth.cos(yaw), lz = Mth.sin(yaw);       // the lead's left
            double toward = (mate.getX() - lead.getX()) * lx + (mate.getZ() - lead.getZ()) * lz;
            double apart = REST_BESIDE * 0.5 * (lead.getScale() + mate.getScale());
            for (double side : toward >= 0 ? new double[] {1.0, -1.0} : new double[] {-1.0, 1.0}) {
                double x = lead.getX() + lx * side * apart, z = lead.getZ() + lz * side * apart;
                Double y = footing(lead.level(), x, lead.getY(), z);
                if (y != null) {
                    return new Vec3(x, y, z);
                }
            }
            return null;
        }
    }

    /** Where it can rear up: on land, or in water a block deep or less (its feet on the bed, the water hiding them). */
    static boolean canRear(OtterEntity o) {
        return o.isInWater() ? waterDepth(o) <= 1 : o.onGround();
    }

    /**
     * Where it can stand at this spot, a block up or down from y: its feet's height, or null (no room, no ground, or
     * water over a block deep).
     */
    @Nullable
    static Double footing(Level level, double x, double y, double z) {
        for (int dy : new int[] {0, 1, -1}) {
            BlockPos p = BlockPos.containing(x, y + 0.1 + dy, z);
            BlockPos below = p.below();
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getFluidState(p.above()).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                return (double) p.getY();
            }
        }
        return null;
    }

    /** How deep the water it is in is (blocks, up to 3). */
    static int waterDepth(OtterEntity o) {
        Level level = o.level();
        BlockPos.MutableBlockPos p = o.blockPosition().mutable();
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < o.getY() + 4) {
            p.move(0, 1, 0);
        }
        int deep = 0;
        while (level.getFluidState(p).is(FluidTags.WATER) && deep < 3) {
            deep++;
            p.move(0, -1, 0);
        }
        return deep;
    }

    /**
     * Starts and plays bouts (section 1): every {@link #PLAY_EVERY} ticks a playful otter with a playmate within
     * {@link #PLAYMATE_NEAR} blocks has 1 in 4 to start one (a pup 1 in 2, and its mother always counts). The one invited
     * joins through its own goal, which holds its legs while the starter's bout moves it.
     */
    static class PlayGoal extends Goal {
        private final OtterEntity otter;

        PlayGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (otter.bout != null) {
                return true;
            }
            if (otter.tickCount < otter.playTryAt || !otter.playful()) {
                return false;
            }
            otter.playTryAt = otter.tickCount + PLAY_EVERY;
            boolean pup = otter.life.isYoung();
            if (otter.random.nextInt(pup ? 2 : 4) != 0 && !otter.getTags().contains("play.now")) {   // (the film camera's tag: at once)
                return false;
            }
            OtterEntity mate = null;
            double best = PLAYMATE_NEAR * PLAYMATE_NEAR;
            for (OtterEntity o : otter.level().getEntitiesOfClass(OtterEntity.class, otter.getBoundingBox().inflate(PLAYMATE_NEAR),
                    o -> o != otter && o.bout == null && (o.playful() || pup && !o.life.isYoung() && o.couldAnswer()))) {
                double d = o.distanceToSqr(otter);
                if (d < best) {
                    best = d;
                    mate = o;
                }
            }
            if (mate == null) {
                return false;
            }
            boolean tiredMother = pup && !mate.life.isYoung() && (!mate.playful() || otter.random.nextInt(3) == 0);
            new Bout(otter, mate, tiredMother ? 1 : 3 + otter.random.nextInt(4));
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return otter.bout != null;
        }

        @Override
        public void tick() {
            Bout b = otter.bout;
            if (b != null && b.lead == otter) {
                b.tick();
            }
        }

        @Override
        public void stop() {
            if (otter.bout != null) {
                otter.bout.end(false);
            }
        }
    }

    /**
     * The skitter (2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 6b): curious or playful on land,
     * a real otter does not stroll evenly but scurries in short bursts. A dash of 1 to 3 blocks, a skid stop with the head
     * coming up, then a look about (standing, its mood's stance) or a short nose-down sniff, then a hop round to a new
     * heading 30 to 90 degrees off (a smaller turn now and then without the hop), and the next dash; 3 to 6 bursts. All
     * numbers my picks. It keeps within the water it last swam in, as the stroll does.
     */
    static class SkitterGoal extends Goal {
        private enum Phase { DASH, SKID, LOOK, SNIFF, HOP, DANCE }
        private final OtterEntity otter;
        private Phase phase = Phase.DASH;
        private int left, bursts;
        /** The hop's whole turn (degrees), shared out over its feet-off-the-ground ticks. */
        private float turn;
        /** Where its dash or sniff goes. */
        private double toX, toZ;

        SkitterGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean free() {
            return !otter.isInWater() && otter.onGround() && !otter.scared() && otter.rider.play() == null
                    && otter.held == null && !otter.isDeadOrDying();
        }

        @Override
        public boolean canUse() {
            return otter.skittery() && otter.doing() == CALM && free() && otter.random.nextInt(20) == 0;
        }

        /** Every tick (Minecraft steps a goal every other tick by default: its pauses ran twice as long, the hop half turned). */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            bursts = 3 + otter.random.nextInt(4);
            if (!dash(otter.random.nextInt(3) == 0 ? (otter.random.nextFloat() - 0.5F) * 120.0F : 0.0F)) {
                hop();
            }
        }

        @Override
        public boolean canContinueToUse() {
            byte d = otter.doing();
            return bursts > 0 && free() && (d == CALM || d == SKID || d == HOP || d == SIDE_L || d == SIDE_R);
        }

        @Override
        public void stop() {
            halt();
            if (otter.doing() == SKID || otter.doing() == HOP || otter.doing() == SIDE_L || otter.doing() == SIDE_R) {
                otter.setDoing(CALM);
            }
            otter.danceLeft = 0;
        }

        /** A dash 1 to 3 blocks, this many degrees off its heading; false if there is no way there. */
        private boolean dash(float off) {
            for (int i = 0; i < 6; i++) {
                float wander = i == 0 ? 0.0F : (otter.random.nextFloat() - 0.5F) * 40.0F * i;
                float yaw = (otter.getYRot() + off + wander) * Mth.DEG_TO_RAD;
                double d = 1.2 + otter.random.nextDouble() * 1.8;
                double x = otter.getX() - Mth.sin(yaw) * d, z = otter.getZ() + Mth.cos(yaw) * d;
                if (goStraight(x, z, SCURRY_GO, 40)) {
                    otter.setGait(SCURRY);
                    phase = Phase.DASH;
                    return true;
                }
            }
            return false;
        }

        /**
         * Goes straight at a spot this near, no route (a route of a block or two counts as done before it takes a step: the
         * test, 2026-11-22), if the way is level dry ground in reach of its water; for at most this many ticks.
         */
        private boolean goStraight(double x, double z, double speed, int ticks) {
            Level level = otter.level();
            for (double f = 0.34; f <= 1.0; f += 0.33) {
                BlockPos at = BlockPos.containing(Mth.lerp(f, otter.getX(), x), otter.getY(), Mth.lerp(f, otter.getZ(), z));
                if (otter.hasRestriction() && !otter.isWithinRestriction(at) || !level.getFluidState(at).isEmpty()
                        || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                        || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
            otter.getNavigation().stop();
            otter.getMoveControl().setWantedPosition(x, otter.getY(), z, speed);
            otter.straightFor = ticks;
            left = ticks;
            toX = x;
            toZ = z;
            return true;
        }

        /** Got there, or out of time, or against something. */
        private boolean arrived() {
            double dx = toX - otter.getX(), dz = toZ - otter.getZ();
            return dx * dx + dz * dz < 0.09 || --left <= 0 || otter.horizontalCollision;
        }

        private void halt() {
            otter.straightFor = 0;
            otter.getNavigation().stop();
            otter.getMoveControl().setWantedPosition(otter.getX(), otter.getY(), otter.getZ(), 0.0);
            otter.setZza(0.0F);
            otter.setGait(EVEN);
        }

        /** Turns to a new heading with a little hop. */
        private void hop() {
            turn = (otter.random.nextBoolean() ? 1 : -1) * (30.0F + otter.random.nextFloat() * 60.0F);
            halt();
            otter.setDoing(HOP);
            phase = Phase.HOP;
            bursts--;
        }

        /** A short slow sniff along, nose down, a block or so ahead; false if there is no way. */
        private boolean sniff() {
            float yaw = (otter.getYRot() + (otter.random.nextFloat() - 0.5F) * 60.0F) * Mth.DEG_TO_RAD;
            double d = 1.0 + otter.random.nextDouble() * 1.0;
            double x = otter.getX() - Mth.sin(yaw) * d, z = otter.getZ() + Mth.cos(yaw) * d;
            if (!goStraight(x, z, SNIFF_GO, 60)) {
                return false;
            }
            otter.setGait(SNIFF);
            return true;
        }

        @Override
        public void tick() {
            switch (phase) {
                case DASH -> {
                    if (arrived()) {
                        halt();
                        otter.setDoing(SKID);                    // braced on its front paws, head coming up
                        phase = Phase.SKID;
                    }
                }
                case SKID -> {
                    if (otter.doing() == CALM) {
                        if (otter.random.nextFloat() < 0.45F && sniff()) {
                            phase = Phase.SNIFF;
                        } else {
                            phase = Phase.LOOK;                  // stands and looks about: its mood's stance
                            left = 10 + otter.random.nextInt(31);
                        }
                    }
                }
                case LOOK, SNIFF -> {
                    if (phase == Phase.SNIFF ? arrived() : --left <= 0) {
                        halt();
                        if (otter.mood() == PLAYFUL && otter.random.nextInt(3) == 0) {
                            otter.startDance();                  // playful: the war dance, hopping side to side
                            phase = Phase.DANCE;
                            bursts--;
                        } else if (otter.random.nextInt(3) == 0) {
                            bursts--;                            // a small turn without the hop: the dash turns it
                            if (bursts > 0 && !dash((otter.random.nextFloat() - 0.5F) * 50.0F)) {
                                hop();
                            }
                        } else {
                            hop();
                        }
                    }
                }
                case DANCE -> {
                    if (!otter.dancing() && bursts > 0 && !dash((otter.random.nextFloat() - 0.5F) * 90.0F)) {
                        hop();
                    }
                }
                case HOP -> {
                    int t = otter.doingFor();
                    if (otter.doing() == HOP && t >= HOP_UP && t < HOP_DOWN) {
                        float yaw = otter.getYRot() + turn / (HOP_DOWN - HOP_UP);   // turned while its feet are up
                        otter.setYRot(yaw);
                        otter.yBodyRot = yaw;
                        otter.yHeadRot = yaw;
                    } else if (otter.doing() == CALM && bursts > 0 && !dash(0.0F)) {
                        hop();
                    }
                }
            }
        }
    }

    static class SwimAboutGoal extends Goal {
        private final OtterEntity otter;
        private boolean dive, out;
        private int left;

        SwimAboutGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (otter.doing() != CALM || otter.scared() || otter.rider.play() != null) {
                return false;
            }
            int chance = otter.isInWater() ? 40 : 160;
            if (otter.random.nextInt(chance) != 0) {
                return false;
            }
            out = otter.isInWater() && otter.soakedFor > 600 && otter.random.nextInt(3) == 0;
            Vec3 to = out ? landSpot(otter, 10.0, null) : waterSpot(otter, 10.0, null);
            if (to == null) {
                return false;
            }
            dive = !out && otter.random.nextInt(3) == 0;
            return otter.getNavigation().moveTo(to.x, to.y, to.z, BOUND);
        }

        @Override
        public void start() {
            left = 300;
            // going in from a bank, one time in three the leaping dive, for the fun of it
            otter.leapForFun = !otter.isInWater() && otter.random.nextInt(3) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !otter.getNavigation().isDone() && --left > 0 && otter.doing() == CALM && !otter.scared();
        }

        @Override
        public void tick() {
            if (dive && otter.isInWater() && otter.rider.play() == null) {
                otter.setDiving(true);
            }
        }

        @Override
        public void stop() {
            if (dive) {
                otter.setDiving(false);
            }
            otter.leapForFun = false;
            otter.getNavigation().stop();
        }
    }

    // --- clips -------------------------------------------------------------------------------------------------------

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, RawAnimation> MOVE_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, RawAnimation> STANCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation BOUND_CLIP = RawAnimation.begin().thenLoop("bound");
    private static final RawAnimation PLAY_BOUND_CLIP = RawAnimation.begin().thenLoop("play_bound");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation DIVE_SWIM = RawAnimation.begin().thenLoop("dive_swim");
    private static final RawAnimation SHAKE_CLIP = RawAnimation.begin().thenPlayAndHold("shake_dry");
    private static final RawAnimation REST_CLIP = RawAnimation.begin().thenPlay("rest_down").thenLoop("rest");
    private static final RawAnimation REST_UP_CLIP = RawAnimation.begin().thenPlayAndHold("rest_up");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation FORAGE_CLIP = RawAnimation.begin().thenLoop("forage_feel");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation EAT_CLIP = RawAnimation.begin().thenLoop("eat");
    private static final RawAnimation SCURRY_CLIP = RawAnimation.begin().thenLoop("scurry");
    private static final RawAnimation SNIFF_CLIP = RawAnimation.begin().thenLoop("sniff_walk");
    private static final RawAnimation SKID_CLIP = RawAnimation.begin().thenPlayAndHold("skid_stop");
    private static final RawAnimation HOP_CLIP = RawAnimation.begin().thenPlayAndHold("hop_turn");
    private static final RawAnimation SIDE_L_CLIP = RawAnimation.begin().thenPlayAndHold("side_hop_left");
    private static final RawAnimation SIDE_R_CLIP = RawAnimation.begin().thenPlayAndHold("side_hop_right");
    private static final RawAnimation PLAY_UP_CLIP = RawAnimation.begin().thenPlayAndHold("play_up");
    private static final RawAnimation PLAY_CLIP = RawAnimation.begin().thenLoop("play_rear");
    private static final RawAnimation PLAY_DOWN_CLIP = RawAnimation.begin().thenPlayAndHold("play_down");
    private static final RawAnimation GULP_CLIP = RawAnimation.begin().thenPlayAndHold("gulp");
    private static final RawAnimation POUNCE_CLIP = RawAnimation.begin().thenPlayAndHold("pounce_tag");
    private static final RawAnimation PORPOISE_CLIP = RawAnimation.begin().thenPlayAndHold("porpoise");
    private static final RawAnimation ZIG_L_CLIP = RawAnimation.begin().thenPlayAndHold("zig_left");
    private static final RawAnimation ZIG_R_CLIP = RawAnimation.begin().thenPlayAndHold("zig_right");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        bodyClips = new SteadyController<>(this, "body", MOVES.lead(), this::animate).clock((otter, clip, tick) -> otter.rider.clock(clip, tick));
        bodyClips.setCustomInstructionKeyframeHandler(note -> {
        });         // the moves' clips' notes (their path) are for the tools only
        controllers.add(bodyClips);
        // the head's own layer (2026-11-22): the gulp, over whatever the body is doing (it keys only the neck, head,
        // jaw and tongue; the later controller's keys win), nothing the rest of the time
        controllers.add(new AnimationController<>(this, "head", 2, state -> {
            if (doing() != GULP || isDeadOrDying()) {
                state.getController().forceAnimationReset();
                return PlayState.STOP;
            }
            return state.setAndContinue(GULP_CLIP);
        }));
    }

    /** Whether it walks or swims (drawing only): it moved within the last few ticks. */
    public boolean going() {
        return rider.movedWithin(3);
    }

    private PlayState animate(AnimationState<OtterEntity> state) {
        AnimationController<OtterEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        BlockMoves.Play c = rider.play();
        if (c != null && !c.turning(level().getGameTime())) {     // a move wins over everything else it does
            return state.setAndContinue(MOVE_CLIPS.computeIfAbsent(MOVES.clip(c), n -> RawAnimation.begin().thenPlayAndHold(n)));
        }
        if (c != null) {                        // turning square to the edge on the spot first: its feet step round
            return state.setAndContinue(WALK);
        }
        switch (doing()) {
            case SHAKE:
                return state.setAndContinue(SHAKE_CLIP);
            case REST:
                return state.setAndContinue(REST_CLIP);
            case REST_UP:
                return state.setAndContinue(REST_UP_CLIP);
            case FORAGE:
                return state.setAndContinue(FORAGE_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case EAT:
                return state.setAndContinue(EAT_CLIP);
            case SKID:
                return state.setAndContinue(SKID_CLIP);
            case HOP:
                return state.setAndContinue(HOP_CLIP);
            case SIDE_L:
                return state.setAndContinue(SIDE_L_CLIP);
            case SIDE_R:
                return state.setAndContinue(SIDE_R_CLIP);
            case PLAY_UP:
                return state.setAndContinue(PLAY_UP_CLIP);
            case PLAY:
                return state.setAndContinue(PLAY_CLIP);
            case PLAY_DOWN:
                return state.setAndContinue(PLAY_DOWN_CLIP);
            case POUNCE:
                return state.setAndContinue(POUNCE_CLIP);
            case PORPOISE:
                return state.setAndContinue(PORPOISE_CLIP);
            case ZIG_L:
                return state.setAndContinue(ZIG_L_CLIP);
            case ZIG_R:
                return state.setAndContinue(ZIG_R_CLIP);
            default:
                break;
        }
        double size = getScale(), stride = rider.stride();
        if (swimming()) {
            if (diving()) {
                player.setAnimationSpeed(going() ? Mth.clamp(stride / (DIVE_CLIP_SPEED * size), 0.7, 2.2) : 0.6);
                return state.setAndContinue(DIVE_SWIM);
            }
            player.setAnimationSpeed(going() ? Mth.clamp(stride / (SWIM_CLIP_SPEED * size), 0.6, 2.2) : 0.5);
            return state.setAndContinue(SWIM);
        }
        if (going()) {
            byte gait = gait();
            if (gait == SCURRY) {
                player.setAnimationSpeed(Mth.clamp(stride / (SCURRY_CLIP_SPEED * size), 0.5, 2.0));
                return state.setAndContinue(SCURRY_CLIP);
            }
            if (gait == SNIFF) {
                player.setAnimationSpeed(Mth.clamp(stride / (SNIFF_CLIP_SPEED * size), 0.5, 2.0));
                return state.setAndContinue(SNIFF_CLIP);
            }
            if (stride > BOUND_DRAWN * size) {
                // the noodle bound (plan otter-play-and-fish.md 6f): its clip follows the real speed so the feet hold the
                // ground, but never slower than 0.7 (a stride in 0.57 s: slower, the hump reads as a crawl); playing, the
                // bouncy one
                player.setAnimationSpeed(Mth.clamp(stride / (BOUND_CLIP_SPEED * size), 0.7, 2.6));
                return state.setAndContinue(mood() == PLAYFUL ? PLAY_BOUND_CLIP : BOUND_CLIP);   // (in a bout it is playful)
            }
            player.setAnimationSpeed(Mth.clamp(stride / (WALK_CLIP_SPEED * size), 0.5, 2.0));
            return state.setAndContinue(WALK);
        }
        byte m = mood();
        if (m != CALM_MOOD && !isInWater()) {      // its mood's stance and tail (tools/otter_models.py stance_clips)
            return state.setAndContinue(STANCES.computeIfAbsent(MOODS[m], n -> RawAnimation.begin().thenLoop("stand_" + n)));
        }
        return state.setAndContinue(IDLE);
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

    /** Ticks it has been in the water, for tests. */
    public int soakedFor() {
        return soakedFor;
    }
}
