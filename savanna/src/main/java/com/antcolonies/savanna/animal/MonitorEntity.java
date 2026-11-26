package com.antcolonies.savanna.animal;

import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.entity.body.SteadyController;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.SeekMateGoal;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.entity.body.TailDrag;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.CrabShellBlock;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
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
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
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
 * The Nile monitor (M9 river life, into the game 2026-11-17, the user: "add him now"; model {@code tools/monitor_models.py
 * game}). Generalized and fun first (the animals' rule); every number my pick. A bank animal, where the crocodile is a
 * water one:
 * <ul>
 * <li>It walks the banks near the water, swims across or floats at the surface with its head and back out, and never
 * strays far from the water it was last in.</li>
 * <li>By day, dry, it lies flat on the bank in the sun 1 to 3 minutes; at night it lies still and rests.</li>
 * <li>Hungry, it hunts what the river leaves it: a crab (swallowed whole), a snail (crushed, the broken shell left on the
 * bed) or remains (three bites), whichever is nearest within {@link #HUNT_REACH} blocks ({@link HuntGoal}).</li>
 * <li>A survival player coming near on land makes it stand tall and hiss; coming right up to it, it bites (1.5 hearts) and
 * runs; hit, it bites back if the hitter is at its jaws, then runs for the water and dives ({@link ThreatGoal},
 * {@link FleeGoal}). It keeps away from crocodiles.</li>
 * </ul>
 * Not yet: raiding crocodile nests and its own eggs (they come with the crocodile's nests), burrows.
 * <p>
 * Climbing a block is a move of its own (the Animal Maker Engine step 2, stage B, 2026-11-19; plan
 * {@code docs/plans/engine-step2-climb.md}): walking up to a one-block step on its route it stops short of it, looks up
 * over the edge, rears up, sets its forefeet on top, hauls itself up with its hind feet on the step's face and stands on
 * top. The game moves it along the move's own path ({@link #MOVES}, the shared {@link BlockMoves} player), its box
 * here and its drawing on each player's screen from the same start, so the clip and its place never drift apart.
 * Climbing down a block is the same kind of move (stage C, 2026-11-19): it stops with its fore claws at the edge, looks
 * down over it, reaches its forefeet down the face to the lower ground, tips its chest down while its hind legs brake on
 * top, then its hips go over and its hind feet step down the face; the tail slides off the edge last.
 */
public class MonitorEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, BASK = 1, HISS = 2, BITE = 3, EAT = 4, WATCH = 5, BITE_PLAYER = 6;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(MonitorEntity.class, EntityDataSerializers.BYTE);
    /** Its move now ({@link BlockMoves.Play}), empty when not on one. */
    private static final EntityDataAccessor<CompoundTag> DATA_CLIMB = SynchedEntityData.defineId(MonitorEntity.class,
            EntityDataSerializers.COMPOUND_TAG);

    /**
     * The model is made 6 blocks long, nose to tail tip (the user's length "for now"); drawn and boxed at this share of
     * that. 0.6 would make it 3.6 blocks.
     */
    public static final float MODEL_SCALE = 0.75F;
    /** How far its jaws reach ahead of its middle (blocks, at size 1): the snout's tip is 3 blocks out on a 6-block monitor. */
    static final double JAWS_AHEAD = 2.9 * MODEL_SCALE;
    /** How far it looks for food (blocks). */
    static final double HUNT_REACH = 12.0;
    /** How near a survival player makes it hiss (blocks from its jaws; crouching, nearer). */
    static final double HISS_REACH = 4.0, HISS_REACH_CROUCHING = 2.0;
    /** How near a player at its jaws gets bitten (blocks from the snout's tip). */
    static final double BITE_REACH = 1.3 * MODEL_SCALE;
    /** How hard it bites (1.5 hearts). */
    static final float BITE_DAMAGE = 3.0F;
    /** How far from the water it last swam in it may wander (blocks). */
    static final int NEAR_WATER = 16;
    /** Kept while a player is within this many blocks (as the river animals are), gone past the group's 128. */
    static final double KEEP_NEAR = 96.0;
    static final double DRAW_DISTANCE = 64.0;
    /** No world-spawned monitor within this many blocks of another: each its own stretch of river (80; 48 until the user asked for rarer, 2026-11-17). */
    public static final int SPAWN_APART = 80;
    /** How far from a bank the world may spawn one in the water (blocks). */
    public static final int BANK_REACH = 3;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): head and back out. */
    static final double AFLOAT = 0.75 * MODEL_SCALE;
    /** Afloat, its tail's middle line lies this far under the surface (model pixels; the swim's straight tail; TAIL_FLOAT in tools/monitor_models.py). */
    public static final double TAIL_FLOAT = 1.0;
    /** Startled in the water, it sinks out of sight to this depth (blocks). */
    static final double HIDE_DEPTH = 1.6 * MODEL_SCALE;
    static final int STARTLED_TICKS = 300;
    /** The walk clip's stride (0.86 blocks a 1.2 s step) is made for this speed (blocks a tick, at size 1). */
    static final double WALK_CLIP_SPEED = 0.036 * MODEL_SCALE;
    /** Ticks into the bite clip its jaws snap shut (0.28 s). */
    static final int SNAP_TICK = 6, BITE_TICKS = 14, EAT_TICKS = 40;
    /** The bite at a player ({@code bite_player}, 1.4 s): the jaws clamp shut at 0.42 s, then the head shake. */
    static final int SNAP_PLAYER_TICK = 8, BITE_PLAYER_TICKS = 28;
    /** A player's bite reaches a little farther: the lunge throws the head forward (blocks). */
    static final double BITE_PLAYER_REACH = BITE_REACH + 0.4;
    /** Curious (2026-11-17, the user's ask): a survival player within WATCH_REACH blocks is watched, and trailed back to TRAIL_NEAR if farther than TRAIL_FAR. */
    static final double WATCH_REACH = 14.0, TRAIL_FAR = 8.0, TRAIL_NEAR = 6.0;

    int hungryIn;
    int sunIn;
    /** A hunt is on: the hunt steers it in the water, not the float at the surface. */
    boolean hunting;
    /** A hunt that ends with nothing caught (given up, the prey gone, startled): it hunts again after this many ticks (a minute, as the catfish). */
    static final int REST_AFTER_GIVING_UP = 1200;
    int startled;
    /** Ticks before it may hiss again. */
    int hissWait;
    /** Ticks before it takes an interest in a player again. */
    int watchWait;
    /** Where its head and eyes are turned (degrees from its heading) and when last drawn: drawing only (MonitorModel). */
    public double headAim, eyeAim;
    public long lookNanos;
    /**
     * The joints along its middle line the body bends at to lie on what is under it ({@link GroundBend}, as the
     * crocodile's; model z, pixels, from {@code tools/monitor_models.py}; 2026-11-18, the user's ask: over a step or a
     * bank's edge the body curves instead of tipping like a plank, most of all going into the river): the anchor at the
     * body's middle, where chest and hips turn; forward through the chest, the two neck bones and the head to the snout
     * tip; back through the hips and the twelve tail bones to the tail tip.
     */
    public static final double[] FRONT_JOINTS = {-13, -17.5, -22, -26, -30, -34, -47},
            REAR_JOINTS = {-13, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48};
    /** The bones of those pieces, in the same order: front chain, then rear chain. */
    public static final String[] BENT_BONES = {"waist", "ribs", "chest", "neck1", "neck2", "head", "hips", "tail1", "tail2", "tail3", "tail4",
            "tail5", "tail6", "tail7", "tail8", "tail9", "tail10", "tail11", "tail12"};
    /** Where each leg pair's feet stand along the body (model z, pixels): the forelegs, then the hind legs. */
    public static final double[] LEG_PAIRS = {-25, -3};
    /** How much each leg pair stands now (1) or is folded back along the body as it swims (0), eased; drawing only (MonitorModel). */
    public final double[] legStand = {1, 1};
    public long legNanos;
    /** How many ticks still it may stand before its walk stops (my pick: 0.15 s); the last tick it moved; how far a tick, eased. */
    static final int WALK_HOLD = 3;
    private int movedTick = -100;
    private double stride;
    /**
     * Its tail dragged behind it (the Animal Maker Engine step 2, stage A, 2026-11-19; {@link TailDrag}); drawing only,
     * made by {@code MonitorModel} on first draw (it reads the model's rest pose).
     */
    @Nullable
    public TailDrag tail;
    /**
     * The least each tail joint lies over the ground, base to tip (model pixels): the tail's own thickness there and a
     * little, as the model's lying tail has it ({@code tail_radius} and {@code tail_height} in {@code tools/monitor_models.py},
     * 4.6 px at the base to 0.6 at the tip, 0.25 clear; kept in step with it by hand).
     */
    public static final double[] TAIL_LOWEST = new double[12];

    static {
        for (int i = 0; i < TAIL_LOWEST.length; i++) {
            TAIL_LOWEST[i] = 4.6 + (0.6 - 4.6) * (i + 1) / TAIL_LOWEST.length + 0.25;
        }
    }
    /**
     * Both leg pairs are folded this far (stand under this) before the swim clip plays (2026-11-18, the user's report: going
     * into the water the hind legs swung up over the back for a moment). Until then the walk or idle clip plays and the
     * model folds each pair itself as its feet lose the ground, so the clip never switches under a leg still on the bank.
     */
    public static final double FOLDED = 0.05;
    /**
     * The front half is three pieces (waist, ribs, chest: the first {@link #TRUNK_PIECES} of {@link #BENT_BONES}), each
     * bending at most {@link #TRUNK_BEND} degrees against the one behind (my picks, 2026-11-18, the user's reports: one
     * chest bending far made a peak in the back going down a bank, held to a small bend it looked rigid), so the body
     * curves; the neck and head take the rest of a dip.
     */
    public static final int TRUNK_PIECES = 3;
    public static final double TRUNK_BEND = 15.0;
    private final GroundBend bend = new GroundBend(FRONT_JOINTS, REAR_JOINTS, TRUNK_PIECES, TRUNK_BEND);
    /** Who just hurt it, to bite back if at its jaws. */
    @Nullable
    LivingEntity biteBack;
    /** What startled it last (to run from where there is no water). */
    @Nullable
    Entity scare;
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(MonitorEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Its moves made once with their clips (the Animal Maker Engine): the climbs up and down a block (step 2, stages B
     * and C, 2026-11-19), going into and out of the water (step 4), played by the shared player ({@link BlockMoves},
     * step B, 2026-11-20) from its move file ({@code assets/antcolonies/block_moves/monitor.json}, written by
     * {@code python tools/monitor_models.py game} with the clips). Which move where, and the room for it, is the monitor's
     * own (below); its moves by name: up and down a block; into the water off a level bank, off a bank one or two blocks
     * over it, running; out onto a level bank, onto a bank a block up.
     */
    public static final BlockMoves MOVES = BlockMoves.of(MonitorEntity.class, SavannaMod.MODID, "monitor");
    public static final int UP = MOVES.index("up"), DOWN = MOVES.index("down"), IN = MOVES.index("in"), DROP = MOVES.index("drop"),
            DROP2 = MOVES.index("drop2"), DASH = MOVES.index("dash"), OUT = MOVES.index("out"), OUT_UP = MOVES.index("out_up");

    /** The move last read from the synced data, and that data (so it is read once per change). */
    @Nullable
    private BlockMoves.Play climb;
    @Nullable
    private CompoundTag climbRead;
    /** How far out from its middle line its feet stand (model pixels; my pick). */
    static final double CLIMB_FEET_OUT = 12.0;
    /** Faster than this it runs into the water (the dash) rather than walking in (blocks a tick; a stroll is 0.031, a run 0.077). */
    static final double DASH_SPEED = 0.05;
    /** The body's clip player (the climb's clock and clip time are read from it by the model). */
    @Nullable
    public SteadyController<MonitorEntity> bodyClips;
    /** Drawing only (MonitorModel): whether it was drawn climbing, how far its drawing is still shifted from its place after a hand over (blocks), where it was drawn and when. */
    public boolean drawnClimbing;
    public double shiftX, shiftY, shiftZ, drawnX, drawnY, drawnZ;
    public long drawnNanos;

    /**
     * Its hit boxes along the drawn body ({@link com.antcolonies.animal.HitChain}, 2026-11-22; my picks measured on its
     * model): its position is at the back of its hips, so the head's tip is 2.25 ahead and the tail's 2.25 behind
     * (model pixels times MODEL_SCALE); the head, the neck, the chest, the waist and four beads down the tail: its
     * walking box is only the hips of a body 4.5 blocks long, so a swing or an arrow at what you see lands.
     */
    private final com.antcolonies.animal.HitChain chain = new com.antcolonies.animal.HitChain(this,
            new com.antcolonies.animal.HitChain.Link("head", 1.95, 0.1, 0.5F, 0.4F),
            new com.antcolonies.animal.HitChain.Link("neck", 1.45, 0.1, 0.45F, 0.4F),
            new com.antcolonies.animal.HitChain.Link("chest", 0.95, 0.05, 0.6F, 0.5F),
            new com.antcolonies.animal.HitChain.Link("waist", 0.45, 0, 0.6F, 0.5F),
            new com.antcolonies.animal.HitChain.Link("tail1", -0.6, 0, 0.45F, 0.35F),
            new com.antcolonies.animal.HitChain.Link("tail2", -1.1, 0, 0.4F, 0.3F),
            new com.antcolonies.animal.HitChain.Link("tail3", -1.55, 0, 0.35F, 0.28F),
            new com.antcolonies.animal.HitChain.Link("tail4", -1.98, 0, 0.3F, 0.25F));

    public MonitorEntity(EntityType<? extends MonitorEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.6F, false);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        hungryIn = random.nextInt(1200);
        sunIn = random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeGoal(this));
        goalSelector.addGoal(1, new ThreatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, CrocodileEntity.class, 8.0F, 1.0, 1.5));
        goalSelector.addGoal(2, new HuntGoal(this));
        goalSelector.addGoal(3, new WatchGoal(this));
        goalSelector.addGoal(4, new BaskGoal(this));
        // never stopped by Minecraft's "nothing happened for 5 s and no player within 32" rule (docs/BUGS.md 216)
        goalSelector.addGoal(5, new SeekMateGoal<>(this, 0.8));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8, 100, false));
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
        builder.define(DATA_CLIMB, new CompoundTag());
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        entityData.set(DATA_DOING, doing);
    }

    /** Where its jaws close: the snout's tip, a little over the ground. */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * life.ownSize();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + 0.5 * getScale(), getZ() + Mth.cos(yaw) * ahead);
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

    /** Whether its jaws are at an entity (its box within reach of the snout's tip). */
    boolean atJaws(Entity e, double reach) {
        return e.getBoundingBox().inflate(reach).contains(jawPoint());
    }

    public void startle(@Nullable Entity by) {
        startled = STARTLED_TICKS;
        scare = by;
        sunIn = Math.max(sunIn, 600 + random.nextInt(600));
        if (doing() == BASK || doing() == HISS || doing() == WATCH) {
            setDoing(CALM);
        }
    }

    public boolean isStartled() {
        return startled > 0;
    }

    /** Its move now, or null (both sides). */
    @Nullable
    public BlockMoves.Play climb() {
        CompoundTag tag = entityData.get(DATA_CLIMB);
        if (tag != climbRead) {
            climbRead = tag;
            climb = BlockMoves.Play.of(tag);
        }
        return climb;
    }

    /** Whether a move is on and past turning square on the spot (its clip plays). */
    public boolean moveClipOn() {
        BlockMoves.Play c = climb();
        return c != null && !c.turning(level().getGameTime());
    }

    /**
     * Drawing only: how far into its climb clip it is drawn now (ticks of the clip), 0 while the clip eases in, or below
     * 0 when it is not climbing or the clip is not on yet.
     */
    public double climbClipTicks() {
        BlockMoves.Play c = climb();
        if (c == null || bodyClips == null || bodyClips.getCurrentAnimation() == null
                || !bodyClips.getCurrentAnimation().animation().name().equals(MOVES.clip(c))) {
            return -1.0;
        }
        // easing in, nothing played yet; held on its last frame (GeckoLib's "paused"), still its clip time
        return bodyClips.getAnimationState() == AnimationController.State.TRANSITIONING ? 0.0 : bodyClips.clipTicks();
    }

    /** The body clip player's clock: the climb clip plays at the climb's own time since its start ({@link SteadyController.Clock}). */
    private double climbClock(String clip, double tick) {
        BlockMoves.Play c = climb();
        if (c == null || !clip.equals(MOVES.clip(c))) {
            return -1.0;
        }
        return Math.max(0.0, MOVES.ticks(c, level().getGameTime() + (tick - Math.floor(tick))));
    }

    /**
     * Starts a move when its route ahead calls for one and it has come to the move's start, heading near enough at the
     * step's face, the edge or the bank's face, with room for it: on land, a whole block up or down (a climb), or into
     * water off a bank level with it or one or two blocks over it (running, the dash off a level bank); afloat, out onto
     * a bank level with the water or a block over it. Calm or watching only (running away is calm too: the flee goal
     * only sets its route), and not just hurt (a move cut short by a hurt is not started again at once). Else it goes
     * the old way (Minecraft's own step, its fall, its hop out of the water).
     */
    private boolean startMove() {
        if (isPassenger() || isDeadOrDying() || hurtTime > 0 || doing() != CALM && doing() != WATCH) {
            return false;
        }
        boolean afloat = isInWater() && !onGround();
        if (!afloat && (!onGround() || isInWater())) {
            return false;
        }
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        int last = Math.min(path.getNodeCount() - 2, path.getNextNodeIndex() + 3);
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= last; i++) {
            Node a = path.getNode(i), b = path.getNode(i + 1);
            int dx = b.x - a.x, dz = b.z - a.z;
            if (Math.abs(dx) + Math.abs(dz) != 1) {
                continue;
            }
            int move = afloat ? outOf(a, b) : a.y == floor ? intoWater(b, floor) : -1;
            if (afloat && move < 0) {
                if (dryStand(b)) {
                    return false;            // a bank it has no move for: the old hop
                }
                continue;
            }
            if (move < 0 && !afloat) {
                if (Math.abs(b.y - a.y) != 1 || a.y != floor) {
                    continue;
                }
                move = b.y < a.y ? DOWN : UP;
            }
            BlockMoves.Play c = climbAt(a, b, dx, dz, move);
            if (c != null) {
                entityData.set(DATA_CLIMB, c.save());
                return true;
            }
            return false;
        }
        return afloat && outPastRoute(path);
    }

    /**
     * Afloat, a route that ends at the water's edge short of its goal (Minecraft's route maker cannot step up out of
     * the water onto a bank a block or more above it: the route stopped at the last water square and the climb out
     * never started): the bank square past its last node, toward the goal, read as the next node.
     */
    private boolean outPastRoute(Path path) {
        if (path.getNodeCount() < 2 || path.getNextNodeIndex() < path.getNodeCount() - 4) {
            return false;
        }
        Node a = path.getEndNode();
        BlockPos goal = path.getTarget();
        int gx = goal.getX() - a.x, gz = goal.getZ() - a.z;
        if (gx == 0 && gz == 0) {
            return false;
        }
        int dx = Math.abs(gx) >= Math.abs(gz) ? Integer.signum(gx) : 0, dz = dx == 0 ? Integer.signum(gz) : 0;
        int top = waterTop();
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        for (int y = top; y <= top + 1; y++) {
            Node b = new Node(a.x + dx, y, a.z + dz);
            int move = outOf(a, b);
            if (move >= 0) {
                BlockMoves.Play c = climbAt(a, b, dx, dz, move);
                if (c != null) {
                    entityData.set(DATA_CLIMB, c.save());
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /** The way into the water at the column of node b, from a bank whose top is at {@code floor}: -1 if b is not water. */
    private int intoWater(Node b, int floor) {
        for (int down = 1; down <= 3; down++) {
            BlockPos at = new BlockPos(b.x, floor - down, b.z);
            if (level().getFluidState(at).is(FluidTags.WATER)) {
                if (!level().getFluidState(at.above()).isEmpty() || level().getFluidState(at).getAmount() < 8) {
                    return -1;               // a slope of flowing water, or water over its block: not a bank's edge
                }
                if (down == 1) {
                    // (its eased step from tick(): here, before this tick's move, getX() - xo was always 0 and a
                    // running monitor stopped at the edge and walked in)
                    boolean running = tickCount - movedTick <= 2 && stride > DASH_SPEED * getScale();
                    return running ? DASH : IN;
                }
                return down == 2 ? DROP : DROP2;
            }
            if (!open(at)) {
                return -1;
            }
        }
        return -1;
    }

    /** Out of the water from node a (afloat) onto node b: onto a bank level with the water's block, or a block over it; -1 else. */
    private int outOf(Node a, Node b) {
        int top = waterTop();
        if (top == Integer.MIN_VALUE || !dryStand(b)) {
            return -1;
        }
        return b.y == top ? OUT : b.y == top + 1 ? OUT_UP : -1;
    }

    /** The top of the water's block it floats in (the height of its upper face), or MIN_VALUE if not in still water. */
    private int waterTop() {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY() + 0.3, getZ()).mutable();
        if (!level().getFluidState(p).is(FluidTags.WATER)) {
            return Integer.MIN_VALUE;
        }
        while (level().getFluidState(p.above()).is(FluidTags.WATER)) {
            p.move(0, 1, 0);
        }
        return level().getFluidState(p).getAmount() < 8 ? Integer.MIN_VALUE : p.getY() + 1;
    }

    /** Whether node b stands on a whole dry block with open air over it. */
    private boolean dryStand(Node b) {
        BlockPos at = new BlockPos(b.x, b.y, b.z);
        return dryOpen(at) && level().getBlockState(at.below()).isCollisionShapeFullBlock(level(), at.below());
    }

    /**
     * The move from node a to b ahead of it, if it is at its start and there is room; else null. Its height is the bank's
     * top (going down, in or along level water) or the lower level (going up, or out: the water's block's top). Coming at
     * the edge more than {@link BlockMoves#SQUARE} degrees off square it first turns square on the spot (step B,
     * 2026-11-20; before, more than 40 degrees off it stepped up the old way).
     */
    @Nullable
    private BlockMoves.Play climbAt(Node a, Node b, int dx, int dz, int move) {
        double scale = MODEL_SCALE * getScale(), back = MOVES.move(move).aheadPx * scale / 16.0;
        int sign = dx + dz;
        double face = dx != 0 ? (dx > 0 ? a.x + 1 : a.x) : (dz > 0 ? a.z + 1 : a.z);
        double dist = (face - (dx != 0 ? getX() : getZ())) * sign;
        // not there yet, or come too close to fit the move (a route turning up the step right at it: the old step)
        if (dist > back + 0.1 || dist < back - 0.4) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        int turn = BlockMoves.turnFor(yBodyRot, yaw);
        if (turn < 0) {
            return null;
        }
        boolean afloat = MOVES.move(move).startsAfloat;
        int level = afloat ? waterTop() : Mth.floor(getY() + 1.0E-3);
        if (afloat) {
            // afloat at its resting depth (the float holds it there), not diving or still sinking in
            double want = level - 1.0 / 9.0 - AFLOAT * getScale();
            if (Math.abs(getY() - want) > 0.2) {
                return null;
            }
        }
        // its feet on the top (or the lower ground, or the water): where it goes, else slid sideways to the middle of
        // the column (half a block at most)
        double side = dx != 0 ? getZ() : getX(), middle = (dx != 0 ? b.z : b.x) + 0.5;
        for (double across : new double[] {side, middle}) {
            if (Math.abs(across - side) <= 0.5 && room(move, level, dx != 0, face, sign, across, scale)) {
                double along = face - back * sign;
                return MOVES.start(this, move, dx != 0 ? along : across, afloat ? level : getY(), dx != 0 ? across : along, yaw, turn);
            }
        }
        return null;
    }

    /** Whether the move has room where it goes (see the moves' own checks). */
    private boolean room(int move, int level, boolean alongX, double face, int sign, double across, double scale) {
        if (move == UP) {
            return roomToClimb(level, alongX, face, sign, across, scale);
        }
        if (move == DOWN) {
            return roomToClimbDown(level, alongX, face, sign, across, scale);
        }
        if (move == OUT || move == OUT_UP) {
            return roomToLeave(move == OUT_UP ? level + 1 : level, alongX, face, sign, across, scale);
        }
        return roomToEnter(move, level, alongX, face, sign, across, scale);
    }

    /**
     * Whether a step whose face lies across the climb at {@code face} (along x if {@code alongX}) has a whole block under
     * both feet for the two blocks past the face, with room over it, and room for the body over the step and in front of it.
     */
    private boolean roomToClimb(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = CLIMB_FEET_OUT * scale / 16.0;
        for (double side : new double[] {across - out, across, across + out}) {     // (both feet and the middle line the move itself relies on: review R9-37)
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos step = climbCell(alongX, face + ahead * sign, side, y);
                if (!level().getBlockState(step).isCollisionShapeFullBlock(level(), step) || !open(step.above())) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {-0.5, 0.5, 1.5, 2.5}) {
            if (!open(climbCell(alongX, face + ahead * sign, across, y + 1))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the edge at {@code face} (its top at {@code y}) drops one block onto a whole block under both feet for the
     * two blocks past it, dry and open, with room for the body going down and on along the lower ground; and the top it
     * walks off is a whole block.
     */
    private boolean roomToClimbDown(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = CLIMB_FEET_OUT * scale / 16.0;
        for (double side : new double[] {across - out, across, across + out}) {     // (both feet and the middle line the move itself relies on: review R9-37)
            BlockPos top = climbCell(alongX, face - 0.5 * sign, side, y - 1);
            if (!level().getBlockState(top).isCollisionShapeFullBlock(level(), top)) {
                return false;
            }
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos low = climbCell(alongX, face + ahead * sign, side, y - 2);
                if (!level().getBlockState(low).isCollisionShapeFullBlock(level(), low) || !dryOpen(low.above())
                        || !dryOpen(low.above(2))) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {0.5, 1.5, 2.5}) {
            if (!dryOpen(climbCell(alongX, face + ahead * sign, across, y - 1)) || !open(climbCell(alongX, face + ahead * sign, across, y))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Going into the water off the bank at {@code face} (its top at {@code y}): the bank under both forefeet a whole
     * block (walking in, all the way back to where the move starts), and past the edge for two blocks, under both feet and the middle, still water at its height for the move
     * (the water's block's top level with the bank, or one or two blocks lower) with open air over it up to over the bank.
     */
    private boolean roomToEnter(int move, int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = CLIMB_FEET_OUT * scale / 16.0;
        int drop = move == DROP ? 1 : move == DROP2 ? 2 : 0;
        // walking in, it walks on along the bank from its start, about two blocks back from the edge (2026-11-22):
        // the bank level and whole for all of it; the other moves start at the edge
        double back = move == IN ? MOVES.move(move).aheadPx * scale / 16.0 : 0.5;
        for (double side : new double[] {across - out, across, across + out}) {
            for (double behind = 0.5; behind < back + 0.5; behind += 1.0) {
                BlockPos top = climbCell(alongX, face - behind * sign, side, y - 1);
                if (!level().getBlockState(top).isCollisionShapeFullBlock(level(), top) || !dryOpen(top.above())) {
                    return false;
                }
            }
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos water = climbCell(alongX, face + ahead * sign, side, y - 1 - drop);
                if (!level().getFluidState(water).is(FluidTags.WATER) || level().getFluidState(water).getAmount() < 8
                        || !open(water)) {
                    return false;
                }
                for (int up = 1; up <= drop + 1; up++) {
                    if (!dryOpen(water.above(up))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Coming out onto the bank at {@code face} (its top at {@code y}): a whole dry block under both feet for the two
     * blocks past the face with room over it, and open over the water where the body rises against the face.
     */
    private boolean roomToLeave(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = CLIMB_FEET_OUT * scale / 16.0;
        for (double side : new double[] {across - out, across, across + out}) {     // (both feet and the middle line the move itself relies on: review R9-37)
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos top = climbCell(alongX, face + ahead * sign, side, y - 1);
                if (!level().getBlockState(top).isCollisionShapeFullBlock(level(), top) || !dryOpen(top.above())) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {-0.5, 0.5, 1.5, 2.5}) {
            if (!open(climbCell(alongX, face + ahead * sign, across, y)) || !open(climbCell(alongX, face + ahead * sign, across, y + 1))) {
                return false;
            }
        }
        // and nothing over the water it rears up out of (a bank a block up: the layer level with the bank's block)
        for (double ahead : new double[] {-1.5, -0.5}) {
            if (!open(climbCell(alongX, face + ahead * sign, across, y - 1))) {
                return false;
            }
        }
        return true;
    }

    private boolean dryOpen(BlockPos pos) {
        return open(pos) && level().getFluidState(pos).isEmpty();
    }

    private static BlockPos climbCell(boolean alongX, double along, double across, int y) {
        return alongX ? new BlockPos(Mth.floor(along), y, Mth.floor(across)) : new BlockPos(Mth.floor(across), y, Mth.floor(along));
    }

    private boolean open(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    /**
     * Moves it on along its move this tick ({@link BlockMoves#step}: its box along the move's path). Ends it once done
     * (ending afloat, it glides on at the move's last speed), or cut short if it dies, a climb lands in water, the bank
     * or step goes from under it or a block is put in its way; after a move ending on land it stands a moment, unless
     * its route goes straight on up or down another step (a hill).
     */
    private boolean climbOn(BlockMoves.Play c) {
        BlockMoves.Move m = MOVES.move(c.move());
        BlockMoves.Step step = MOVES.step(this, c, MODEL_SCALE * getScale() / 16.0, !hillAhead());
        if (step == BlockMoves.Step.ON) {
            return true;
        }
        entityData.set(DATA_CLIMB, new CompoundTag());
        if (step == BlockMoves.Step.GLIDE) {
            return true;
        }
        if (m.startsAfloat && !getNavigation().isDone()) {
            // out on the bank, its route made in the water (which may end at the water's edge: outPastRoute) made
            // afresh from here: followed on, it walked back to that last water square and fell in (the course, 2026-11-19)
            getNavigation().recomputePath();
        }
        return false;
    }

    /** Whether its route goes on up or down another block within the next few nodes (a hill: no rest between the moves). */
    private boolean hillAhead() {
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 4); i++) {
            if (path.getNode(i).y != floor) {
                return true;
            }
        }
        return false;
    }

    /** A hurt cuts a move short (step B, 2026-11-20; my pick): set down where it is, and its flight or bite back takes over. */
    private void cutMove() {
        if (climb() != null) {
            BlockMoves.cut(this);
            entityData.set(DATA_CLIMB, new CompoundTag());
        }
    }

    /** In water it swims where it points, weightless; against a bank with room above it, up and out (as the crocodile). */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && !level().isClientSide) {
            BlockMoves.Play c = climb();
            if (c != null ? climbOn(c) : startMove()) {
                if (c == null) {
                    climbOn(climb());
                }
                return;
            }
        }
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), travelVector);
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
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
        chain.place();
        // its walk, for the clips (both sides): the last tick it moved and how far a tick, eased (BUGS 234)
        double dx = getX() - xo, dz = getZ() - zo, moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 0.004) {
            movedTick = tickCount;
            stride += (moved - stride) * 0.5;
        }
        if (level().isClientSide) {
            wetLooks();
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (sunIn > 0) {
            sunIn--;
        }
        if (hissWait > 0) {
            hissWait--;
        }
        if (watchWait > 0) {
            watchWait--;
        }
        if (startled > 0 && --startled == 0) {
            scare = null;
        }
        if (tickCount % 20 == 0 && isInWater()) {
            restrictTo(blockPosition(), NEAR_WATER);       // its strolls stay near the water it last swam in
        }
        // in the water it hangs at the surface, head and back out, resting or swimming somewhere (2026-11-19: swimming,
        // its route's nodes pulled it under after going in); startled, it dives out of sight; hunting, the hunt steers it
        if (isInWater() && !hunting && climb() == null) {
            double depth = getFluidHeight(FluidTags.WATER) - (startled > 0 ? HIDE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, -0.02, 0.03) + v.y * 0.5, v.z);
        }
    }

    /** Drawing only: until when the water runs off it after coming out (its tick). */
    private int wetUntil;

    /**
     * Drawing only (2026-11-19, my picks, fun first): going into the water a splash where its chest meets the water (the
     * move's wet tick, {@link BlockMoves.Move#wetTick}), a bigger one dropping or running in; coming out, from the moment
     * its hips leave the water, drops running off its belly and tail for two and a half seconds.
     */
    private void wetLooks() {
        BlockMoves.Play c = climb();
        int m = c == null ? -1 : c.move();
        double size = getScale();
        if (m >= 0 && MOVES.move(m).wet()) {
            BlockMoves.Move move = MOVES.move(m);
            double t = MOVES.ticks(c, level().getGameTime()), wet = move.wetTick;
            if (move.endsAfloat && t >= wet && t < wet + 3) {
                double ahead = (m == DASH ? 1.4 : 1.1) * size;
                double x = drawnX(c) + c.dirX() * ahead, z = drawnZ(c) + c.dirZ() * ahead;
                double surface = surfaceUnder(x, c.y() + 0.5, z);
                if (!Double.isNaN(surface)) {
                    int n = m == IN ? 4 : 9;
                    for (int i = 0; i < n; i++) {
                        level().addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.9 * size, surface + 0.05,
                                z + (random.nextDouble() - 0.5) * 0.9 * size, 0.0, 0.1, 0.0);
                    }
                    if (t < wet + 1) {
                        level().playLocalSound(x, surface, z, SoundEvents.GENERIC_SPLASH, getSoundSource(),
                                m == IN ? 0.25F : 0.5F, 1.0F + (random.nextFloat() - 0.5F) * 0.3F, false);
                    }
                }
            } else if (move.startsAfloat && t >= wet) {
                wetUntil = tickCount + 50;
            }
        }
        if (tickCount < wetUntil && !isInWater() && random.nextInt(3) > 0) {
            // somewhere along its body or tail, under its belly line
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double along = (random.nextDouble() * 4.0 - 2.0) * size * MODEL_SCALE / 0.75;
            double y = (c != null ? drawnY : getY()) + (0.2 + 0.2 * random.nextDouble()) * size;
            double x = (c != null ? drawnX : getX()) - Mth.sin(yaw) * along + (random.nextDouble() - 0.5) * 0.4 * size;
            double z = (c != null ? drawnZ : getZ()) + Mth.cos(yaw) * along + (random.nextDouble() - 0.5) * 0.4 * size;
            level().addParticle(ParticleTypes.DRIPPING_WATER, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    private double drawnX(BlockMoves.Play c) {
        return drawnNanos != 0 ? drawnX : getX();
    }

    private double drawnZ(BlockMoves.Play c) {
        return drawnNanos != 0 ? drawnZ : getZ();
    }

    /** The water's surface in the column at (x, z) from height y down a few blocks; NaN if no water. */
    private double surfaceUnder(double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 5; i++, p.move(0, -1, 0)) {
            net.minecraft.world.level.material.FluidState f = level().getFluidState(p);
            if (f.is(FluidTags.WATER)) {
                return p.getY() + f.getHeight(level(), p);
            }
        }
        return Double.NaN;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            cutMove();
        }
        if (hurt && !level().isClientSide && isAlive()) {
            Entity by = source.getEntity();
            if (by instanceof LivingEntity living && !(living instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))
                    && random.nextFloat() < 0.6F) {
                biteBack = living;
            }
            startle(by);
        }
        return hurt;
    }

    /** Bites: the clip, and the bite counts when the jaws shut (the goals call {@link #snapAt} then). */
    void biteAt(LivingEntity target) {
        double reach = target instanceof Player ? BITE_PLAYER_REACH : BITE_REACH;
        if (target.isAlive() && atJaws(target, reach) && target.hurt(damageSources().mobAttack(this), BITE_DAMAGE)) {
            playSound(SoundEvents.FOX_BITE, 1.0F, 0.7F);
        }
    }

    /** The bite's clip for this target: a player gets the lunge, clamp and head shake ({@code bite_player}). */
    static byte biteKind(Entity target) {
        return target instanceof Player ? BITE_PLAYER : BITE;
    }

    static int biteTicks(byte kind) {
        return kind == BITE_PLAYER ? BITE_PLAYER_TICKS : BITE_TICKS;
    }

    static int snapTick(byte kind) {
        return kind == BITE_PLAYER ? SNAP_PLAYER_TICK : SNAP_TICK;
    }

    /** Biting, either kind. */
    public boolean isBiting() {
        return doing() == BITE || doing() == BITE_PLAYER;
    }

    /** It breathes in water as on land (NeoForge's socket). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Its spawn spot is water by a bank (as the crocodile's, BUGS 218): only blocks and mobs stand in its way. */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
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
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            BlockPos bank = bankNear(level, blockPosition(), BANK_REACH);      // spawned in the water by a bank: onto the bank
            if (bank != null) {
                moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            }
        }
        life.setUp(false, level.getRandom());
        refresh();
        return out;
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** Its body's lie on what is under it, worked out and drawn by its model only. */
    public GroundBend bend() {
        return bend;
    }

    /** How deep its feet hang under the surface when it rests afloat (blocks; the drawing's guess: the client does not know if it was startled). */
    public double floatDepth() {
        return AFLOAT * getScale();
    }

    private void refresh() {
        life.apply();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
        tag.putInt("SunIn", sunIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        hungryIn = tag.getInt("HungryIn");
        sunIn = tag.getInt("SunIn");
        refresh();
    }

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /** Fed and sunned for a long while (for tests whose monitor must only walk where it is sent: a bask or a hunt
     *  starting at its random times took the climb tests' monitor off its route, BUGS 255, 277). */
    public void calmForTests() {
        hungryIn = 100_000;
        sunIn = 100_000;
    }

    /** Wants the sun now (for tests and the film camera). */
    public void wantSun() {
        sunIn = 0;
    }

    /**
     * Where it may spawn: in the water of the savanna's rivers, pans and oxbows within {@link #BANK_REACH} blocks of a bank
     * it can climb onto, in the river animals' height band, and (spawned by the world) no other monitor within
     * {@link #SPAWN_APART} blocks. Any height in the water passes (Minecraft's spawner keeps one random height for a try);
     * the world's spawns are set on the bank after ({@link #finalizeSpawn}).
     */
    public static boolean canSpawn(EntityType<MonitorEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 8 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)
                || bankNear(level, pos, BANK_REACH) == null) {
            return false;
        }
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION
                || level.getEntitiesOfClass(MonitorEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(SPAWN_APART)).isEmpty();
    }

    /**
     * The nearest dry bank to stand on within reach (sideways) of a spot in the water: the top of a dry column, at most a
     * few blocks over the water's surface level; null if none.
     */
    @Nullable
    static BlockPos bankNear(LevelAccessor level, BlockPos pos, int reach) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = pos.getX() + dx, z = pos.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < sea - 1 || y > sea + 4) {
                    continue;
                }
                BlockPos stand = new BlockPos(x, y, z), under = stand.below();
                if (!level.getFluidState(under).isEmpty() || !level.getFluidState(stand).isEmpty()
                        || !level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) {
                    continue;
                }
                int d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = stand;
                }
            }
        }
        return best;
    }

    /** The nearest water to run into, aimed two blocks past the edge where that is water too; null if none within reach. */
    @Nullable
    BlockPos waterAim(int reach) {
        Level level = level();
        BlockPos water = BlockPos.findClosestMatch(blockPosition(), reach, 4,
                p -> level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.below()).is(FluidTags.WATER)).orElse(null);
        if (water == null) {
            return null;
        }
        BlockPos from = blockPosition();
        int dx = Integer.signum(water.getX() - from.getX()), dz = Integer.signum(water.getZ() - from.getZ());
        for (int far = 2; far >= 1; far--) {
            BlockPos in = water.offset(dx * far, 0, dz * far);
            if (level.getFluidState(in).is(FluidTags.WATER)) {
                return in;
            }
        }
        return water;
    }

    // --- the goals -----------------------------------------------------------------------------------------------------

    /**
     * Startled (hit, pressed by a player, its hiss ignored): it bites back once if the hitter is at its jaws, then runs for
     * the nearest water and dives, staying under a while; with no water near it runs away from what scared it.
     */
    static class FleeGoal extends Goal {
        private final MonitorEntity m;
        private BlockPos water;
        private int biting, left;
        private byte kind = BITE;

        FleeGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return m.startled > 0 && (!m.isInWater() || m.biteBack != null);
        }

        @Override
        public void start() {
            left = 400;
            biting = 0;
            water = null;
            LivingEntity foe = m.biteBack;
            m.biteBack = null;
            if (foe != null && foe.isAlive() && m.atJaws(foe, BITE_REACH + 0.5)) {
                kind = biteKind(foe);
                biting = biteTicks(kind);
                m.setDoing(kind);
                m.getNavigation().stop();
                m.biteTarget = foe;
            } else {
                run();
            }
        }

        private LivingEntity targetOrNull() {
            return m.biteTarget;
        }

        private void run() {
            m.setDoing(CALM);
            water = m.isInWater() ? null : m.waterAim(16);
            if (water != null) {
                m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.7);
            } else {
                // away from what scared it; hurt by nothing it can see (a fall, a cactus), it still bolts somewhere
                // (BUGS 296: it stood frozen the whole 15 s it was startled)
                Vec3 away = m.scare != null ? DefaultRandomPos.getPosAway(m, 12, 4, m.scare.position()) : DefaultRandomPos.getPos(m, 12, 4);
                if (away != null) {
                    m.getNavigation().moveTo(away.x, away.y, away.z, 1.7);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && (biting > 0 || m.startled > 0 && !m.isInWater());
        }

        @Override
        public void tick() {
            left--;
            if (biting > 0) {
                LivingEntity foe = targetOrNull();
                if (foe != null) {
                    m.face(foe.getX(), foe.getZ(), 30.0F);
                }
                if (biteTicks(kind) - biting == snapTick(kind) && foe != null) {
                    m.biteAt(foe);
                }
                if (--biting == 0) {
                    m.biteTarget = null;
                    run();
                }
                return;
            }
            if (m.getNavigation().isDone() && left % 20 == 0) {
                run();
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.biteTarget = null;
            if (m.isBiting()) {
                m.setDoing(CALM);
            }
        }
    }

    /** Who the bite in the flee goal is aimed at. */
    @Nullable
    LivingEntity biteTarget;

    /**
     * A survival player coming near on land: it stops, turns to face them and stands tall hissing, mouth open; one that
     * comes right up to its jaws gets bitten and it runs; if they stay near past the hiss, it runs too.
     */
    static class ThreatGoal extends Goal {
        private final MonitorEntity m;
        private Player player;
        private int left, biting;

        ThreatGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Nullable
        private Player near() {
            Vec3 jaws = m.jawPoint();
            for (Player p : m.level().players()) {
                if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) || !p.isAlive()) {
                    continue;
                }
                double reach = p.isCrouching() ? HISS_REACH_CROUCHING : HISS_REACH;
                double fromBody = p.distanceTo(m);
                if (p.position().distanceTo(jaws) < reach + 0.5 || fromBody < reach) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public boolean canUse() {
            if (m.startled > 0 || m.isInWater() || m.hissWait > 0 || m.getRandom().nextInt(3) != 0) {   // the goals are looked at every other tick
                return false;
            }
            player = near();
            return player != null;
        }

        @Override
        public void start() {
            left = 60;
            biting = 0;
            m.getNavigation().stop();
            m.setDoing(HISS);
            m.playSound(SoundEvents.CAT_HISS, 1.2F, 0.6F);
        }

        @Override
        public boolean canContinueToUse() {
            return biting > 0 || left > 0 && m.startled == 0 && player.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player);
        }

        @Override
        public void tick() {
            if (biting > 0) {
                m.face(player.getX(), player.getZ(), 30.0F);
                if (BITE_PLAYER_TICKS - biting == SNAP_PLAYER_TICK) {
                    m.biteAt(player);
                }
                if (--biting == 0) {
                    m.startle(player);
                }
                return;
            }
            left--;
            m.face(player.getX(), player.getZ(), 12.0F);
            if (left % 30 == 0) {
                m.playSound(SoundEvents.CAT_HISS, 1.0F, 0.55F);
            }
            if (m.atJaws(player, BITE_REACH)) {          // right at its jaws: it lunges, clamps on and shakes its head
                biting = BITE_PLAYER_TICKS;
                m.setDoing(BITE_PLAYER);
            } else if (left == 0) {
                if (near() == player) {                  // still near after the hiss: it gives up and runs
                    m.startle(player);
                } else {
                    left = 0;
                }
            } else if (left % 10 == 0 && near() != player) {
                left = 0;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.hissWait = 100;
            if (m.doing() == HISS || m.isBiting()) {
                m.setDoing(CALM);
            }
        }
    }

    /**
     * Hungry: goes to the nearest crab, snail, bullfrog (2026-11-20, the prey plan; it leaps into the water from the
     * monitor) or remains within {@link #HUNT_REACH} blocks and, with it at its jaws, bites; a crab or a frog is swallowed whole, a snail crushed (its broken shell left), remains take three bites. Then it eats, and is
     * fed 5 to 10 minutes. Swimming, it dives to prey on the bed under it (2026-11-19, BUGS 230: it used to hang at the
     * surface over it). It gives up a chase after 20 s and, with nothing caught, rests a minute before it hunts again.
     */
    static class HuntGoal extends Goal {
        private final MonitorEntity m;
        private Entity prey;
        private int left, biting, eating, bites, repath;

        HuntGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (m.isFed() || m.startled > 0 || m.getRandom().nextInt(10) != 0) {   // about once a second (goals are looked at every other tick, odd or even by the id)
                return false;
            }
            prey = nearest();
            return prey != null;
        }

        @Nullable
        private Entity nearest() {
            List<Entity> near = m.level().getEntities(m, m.getBoundingBox().inflate(HUNT_REACH, 4.0, HUNT_REACH), e -> e.isAlive()
                    && (e instanceof CrabEntity || e instanceof SnailEntity || e instanceof BullfrogEntity f && !f.hasCustomName()
                    || e instanceof RemainsEntity r && r.bitesLeft() > 0));
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : near) {
                double d = e.distanceToSqr(m);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        @Override
        public void start() {
            left = 400;
            biting = eating = bites = repath = 0;
            m.setDoing(CALM);
            m.hunting = true;
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && m.startled == 0 && (biting > 0 || eating > 0 || prey != null && prey.isAlive() && !m.isFed());
        }

        @Override
        public void tick() {
            if (eating > 0) {
                if (--eating == 0) {
                    m.setDoing(CALM);
                    if (prey instanceof RemainsEntity r && r.isAlive() && r.bitesLeft() > 0 && bites < 3) {
                        return;                           // another bite of the remains
                    }
                    m.hungryIn = 6000 + m.getRandom().nextInt(6000);
                    prey = null;
                }
                return;
            }
            if (biting > 0) {
                m.face(prey.getX(), prey.getZ(), 30.0F);
                if (BITE_TICKS - biting == SNAP_TICK) {
                    snap();
                }
                if (--biting == 0) {
                    eating = EAT_TICKS;
                    m.setDoing(EAT);
                }
                return;
            }
            if (prey == null || !prey.isAlive()) {
                return;
            }
            left--;                                       // only the chase counts against its 20 s, not biting and eating
            if (m.atJaws(prey, 0.5)) {
                m.getNavigation().stop();
                biting = BITE_TICKS;
                m.setDoing(BITE);
                return;
            }
            // close: walk the last bit with its head turned onto the prey (its jaws are 3 blocks ahead of its middle)
            double d = Math.sqrt(m.distanceToSqr(prey.getX(), m.getY(), prey.getZ()));
            if (d < JAWS_AHEAD * m.ownSize() + 1.5) {
                m.face(prey.getX(), prey.getZ(), 15.0F);
                if (m.isInWater()) {                      // swimming: it steers its jaws onto the prey, diving to the bed if it lies there
                    m.getNavigation().stop();
                    Vec3 to = new Vec3(prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ()).subtract(m.jawPoint()).scale(0.1);
                    double most = 0.08;
                    m.setDeltaMovement(to.lengthSqr() > most * most ? to.normalize().scale(most) : to);
                } else if (d < JAWS_AHEAD * m.ownSize() - 0.2) {      // too close for its jaws: back off a little
                    float yaw = m.getYRot() * Mth.DEG_TO_RAD;
                    m.setDeltaMovement(m.getDeltaMovement().add(Mth.sin(yaw) * 0.05, 0, -Mth.cos(yaw) * 0.05));
                } else {
                    m.getNavigation().stop();
                    float yaw = m.getYRot() * Mth.DEG_TO_RAD;
                    m.setDeltaMovement(m.getDeltaMovement().add(-Mth.sin(yaw) * 0.04, 0, Mth.cos(yaw) * 0.04));
                }
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                m.getNavigation().moveTo(prey, 1.1);
            }
        }

        private void snap() {
            if (!(m.level() instanceof ServerLevel server) || prey == null || !prey.isAlive() || !m.atJaws(prey, 0.8)) {
                return;                                   // it got away: the jaws shut on nothing
            }
            Vec3 at = prey.position();
            if (prey instanceof RemainsEntity r) {
                if (r.bite(m)) {
                    bites++;
                }
                m.playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.8F);
            } else if (prey instanceof SnailEntity) {
                prey.discard();
                CrabShellBlock.leave(server, at);
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SavannaItems.CRAB_SHELL.get())),
                        at.x, at.y + 0.2, at.z, 10, 0.15, 0.1, 0.15, 0.05);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL, 1.0F, 0.8F);
                bites = 3;
            } else {
                prey.discard();                           // a crab or a bullfrog (2026-11-20), swallowed whole
                m.playSound(SoundEvents.FOX_BITE, 1.0F, 0.8F);
                bites = 3;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            prey = null;
            m.hunting = false;
            if (bites == 0) {
                m.hungryIn = REST_AFTER_GIVING_UP;        // caught nothing: it rests a minute, so it never stays on prey it cannot reach
            }
            if (m.doing() == BITE || m.doing() == EAT) {
                m.setDoing(CALM);
            }
        }
    }

    /**
     * Curious (2026-11-17, the user's ask; fun first): a survival player on land within {@link #WATCH_REACH} blocks, but not
     * near enough to hiss at, is watched: it stops, turns to face them and flicks its tongue, and if they walk off past
     * {@link #TRAIL_FAR} it follows slowly back to about {@link #TRAIL_NEAR}, never out of its stretch of bank. After 15 to
     * 30 s it loses interest for 20 to 50 s. Coming close still makes it hiss and bite ({@link ThreatGoal}); a basking
     * monitor only turns its head (the model).
     */
    static class WatchGoal extends Goal {
        private final MonitorEntity m;
        private Player player;
        private int left, repath;

        WatchGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean watchable(Player p) {
            return p != null && p.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && p.distanceTo(m) < WATCH_REACH;
        }

        @Override
        public boolean canUse() {
            if (m.startled > 0 || m.isInWater() || !m.onGround() || m.watchWait > 0 || m.doing() != CALM
                    || m.getRandom().nextInt(10) != 0) {
                return false;
            }
            player = null;
            double best = Double.MAX_VALUE;
            for (Player p : m.level().players()) {
                double d = p.distanceTo(m);
                if (watchable(p) && d > HISS_REACH + 1.5 && d < best) {
                    best = d;
                    player = p;
                }
            }
            return player != null;
        }

        @Override
        public void start() {
            left = 300 + m.getRandom().nextInt(300);
            repath = 0;
            m.getNavigation().stop();
            m.setDoing(WATCH);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && m.startled == 0 && !m.isInWater() && m.doing() == WATCH && watchable(player);
        }

        @Override
        public void tick() {
            left--;
            double d = player.distanceTo(m);
            if (d > TRAIL_FAR) {
                if (--repath <= 0) {
                    repath = 20;
                    Vec3 to = player.position().add(m.position().subtract(player.position()).normalize().scale(TRAIL_NEAR));
                    if (m.isWithinRestriction(BlockPos.containing(to))) {
                        m.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
                    }
                }
            } else if (d < TRAIL_NEAR + 0.5) {
                m.getNavigation().stop();
            }
            if (m.getNavigation().isDone()) {
                m.face(player.getX(), player.getZ(), 6.0F);
            }
            m.getLookControl().setLookAt(player, 20.0F, 20.0F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.watchWait = 400 + m.getRandom().nextInt(600);
            m.getNavigation().stop();
            if (m.doing() == WATCH) {
                m.setDoing(CALM);
            }
            player = null;
        }
    }

    /** By day, dry, on a bank under the sky: lies flat in the sun 1 to 3 minutes; at night on land it lies still till dawn. */
    static class BaskGoal extends Goal {
        private final MonitorEntity m;
        private int left;
        private boolean night;

        BaskGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean sunTime() {
            return m.sunIn <= 0 && m.level().isDay() && !m.level().isRaining();
        }

        @Override
        public boolean canUse() {
            if (m.isInWater() || !m.onGround() || m.startled > 0 || !m.getNavigation().isDone() && !m.level().isDay()) {
                return false;
            }
            if (!m.level().isDay()) {
                night = true;
                return m.getRandom().nextInt(40) == 0;
            }
            night = false;
            return sunTime() && m.level().canSeeSky(m.blockPosition());
        }

        @Override
        public void start() {
            left = night ? 24000 : 1200 + m.getRandom().nextInt(2400);
            m.getNavigation().stop();
            m.setDoing(BASK);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !m.isInWater() && m.startled == 0 && (night ? !m.level().isDay() : !m.level().isRaining());
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            if (m.doing() == BASK) {
                m.setDoing(CALM);
            }
            if (!night) {
                m.sunIn = 2400 + m.getRandom().nextInt(2400);    // about 2 to 4 minutes before it basks again
            }
        }
    }

    // --- animations ----------------------------------------------------------------------------------------------------
    /** Clips named by the film camera ({@code film:<clip>}, a still one; step C, 2026-11-20). */
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation BASK_CLIP = RawAnimation.begin().thenLoop("bask");
    private static final RawAnimation HISS_CLIP = RawAnimation.begin().thenLoop("hiss");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation BITE_PLAYER_CLIP = RawAnimation.begin().thenPlayAndHold("bite_player");
    private static final RawAnimation EAT_CLIP = RawAnimation.begin().thenPlayAndHold("eat");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation TONGUE = RawAnimation.begin().thenLoop("tongue");
    /** Every move's clip, by its name ({@link #MOVES}; made as first played). */
    private static final java.util.Map<String, RawAnimation> MOVE_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        bodyClips = new SteadyController<>(this, "body", MOVES.lead(), this::animate).clock(MonitorEntity::climbClock);
        bodyClips.setCustomInstructionKeyframeHandler(note -> {
        });         // the moves' clips' notes (their path) are for the tools only
        controllers.add(bodyClips);
        controllers.add(new AnimationController<>(this, "tongue", 0, this::tongue));
    }

    /**
     * Whether the swim clip plays (drawing only): afloat, with both leg pairs folded ({@link #FOLDED}). The model eases
     * {@link #legStand} by each pair's own footing, and sets it straight from "afloat" when drawn too far to bend.
     */
    public boolean swimClip() {
        return isInWater() && !onGround() && legStand[0] < FOLDED && legStand[1] < FOLDED;
    }

    /**
     * Whether it walks (drawing only): it moved within the last {@link #WALK_HOLD} ticks. One tick still is not enough
     * to stop: on a player's screen its place comes from the server in steps, and a late one left it standing for a
     * tick now and then, which flicked the walk to idle and back, the walk starting over (BUGS 234).
     */
    public boolean walking() {
        return tickCount - movedTick <= WALK_HOLD;
    }

    private PlayState animate(AnimationState<MonitorEntity> state) {
        AnimationController<MonitorEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {     // the film camera's still look (game_compare): a
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5),   // clip it lacks
                    c -> RawAnimation.begin().thenLoop(c)));                                        // draws its rest pose
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        BlockMoves.Play c = climb();
        if (c != null && !c.turning(level().getGameTime())) {     // a move wins over everything else it does
            return state.setAndContinue(MOVE_CLIPS.computeIfAbsent(MOVES.clip(c), n -> RawAnimation.begin().thenPlayAndHold(n)));
        }
        if (c != null) {                        // turning square to the edge on the spot first: its feet step round
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(isInWater() ? SWIM : WALK);
        }
        switch (doing()) {
            case BASK:
                return state.setAndContinue(BASK_CLIP);
            case HISS:
                return state.setAndContinue(HISS_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case BITE_PLAYER:
                return state.setAndContinue(BITE_PLAYER_CLIP);
            case EAT:
                return state.setAndContinue(EAT_CLIP);
            default:
                break;
        }
        double dx = getX() - xo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (swimClip()) {
            player.setAnimationSpeed(Mth.clamp(0.6 + speed / 0.08, 0.6, 2.0));
            return state.setAndContinue(SWIM);
        }
        if (walking()) {
            player.setAnimationSpeed(Mth.clamp(stride / (WALK_CLIP_SPEED * getScale()), 0.5, 3.0));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    /** Walking on land, or watching a player, it flicks its tongue now and then (two flicks in each of its own 5-second windows). */
    private PlayState tongue(AnimationState<MonitorEntity> state) {
        boolean window = (tickCount + getId() * 37) % 100 < 40;
        if (window && (walking() && doing() == CALM || doing() == WATCH) && !isInWater() && !isDeadOrDying() && climb() == null) {
            return state.setAndContinue(TONGUE);
        }
        state.getController().forceAnimationReset();
        return PlayState.STOP;
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
