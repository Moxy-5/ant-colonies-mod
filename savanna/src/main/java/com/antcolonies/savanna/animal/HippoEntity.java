package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
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
 * The hippopotamus (M9 savanna life, into the game 2026-11-21, the user: "put the hippo in game"; model, clips and moves
 * {@code tools/hippo_models.py game}, v8 approved by the user 2026-11-21). An animal: generalized and fun first, every
 * number my pick, real habits used where they make the river look right ({@code docs/DESIGN.md}, "River zones"):
 * <ul>
 * <li>Pods live in the pools of the near and mid-inland rivers, a lone bull or a bull with one or two cows, placed with new
 * land and kept for good ({@code worldgen/HippoPodFeature}, the user 2026-11-21; never spawned by the world's spawner,
 * BUGS 271). The pod shares the bull's pool and packs into it. By day each rests in its pool, afloat with only its eyes, ears
 * and back out where the water is deep, standing on the bed where it is shallow, and now and then wanders the pool,
 * bounding along the bed under the water ({@code underwater_walk}); it yawns its great territorial yawn now and then,
 * and flicks its ears coming up.</li>
 * <li>At dusk it walks out of the water and grazes the land near its pool all night ({@code graze}), eating the grass
 * plants in front of it, flicking its dung about now and then; at dawn it walks back to its pool.</li>
 * <li>The most dangerous animal in the river: a survival player coming near gets a yawn as a warning; coming nearer,
 * or staying, or hitting it, it charges and bites hard (5 hearts). Grown crocodiles are put up with, as real pods let
 * them bask close by: only a bull minds one coming right up to him, yawning first, then a short bluff charge that ends
 * once it is off, one bite at most (BUGS 272); crocodiles swim clear of a hippo.</li>
 * <li>Climbing up or down a block and walking out of the shallows onto a bank are moves of its own (the Animal Maker
 * Engine, made once with their clips, played by the shared {@link BlockMoves} as the monitor's): it stops short of the
 * step, rises and sets its forefeet on top before its body touches it, then heaves its hind legs up.</li>
 * </ul>
 * Not yet: calves and breeding, hippo paths and short "hippo lawns", resting on the banks by day.
 */
public class HippoEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, YAWN = 1, BITE = 2, GRAZE = 3, EAR_FLICK = 4, DUNG = 5, REST = 6;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(HippoEntity.class, EntityDataSerializers.BYTE);
    /** Its move now ({@link BlockMoves.Play}), empty when not on one. */
    private static final EntityDataAccessor<CompoundTag> DATA_CLIMB = SynchedEntityData.defineId(HippoEntity.class,
            EntityDataSerializers.COMPOUND_TAG);
    /** Whether it charges now (the charge clip, drawing only). */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(HippoEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(HippoEntity.class);

    /** The model is made at a bull's size (5 blocks long nose to tail, 2 tall at the back; 16 px a block); drawn at this share. */
    public static final float MODEL_SCALE = 1.0F;
    /** How far its jaws reach ahead of its middle (blocks, at size 1): the snout's front is 2.5 blocks out. */
    static final double JAWS_AHEAD = 2.3;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): the float clip's waterline (21.5 px). */
    static final double AFLOAT = 21.5 / 16.0;
    /** The walk clip is made for this speed (blocks a tick, at size 1: 0.434 blocks a second), the charge for this (1.395). */
    static final double WALK_CLIP_SPEED = 0.434 / 20.0, CHARGE_CLIP_SPEED = 1.395 / 20.0;
    /** Faster than this (blocks a tick, at size 1) its run is drawn with the charge clip. */
    static final double CHARGE_DRAWN = 0.05;
    /** Its strolling and charging speeds (the move control's share of its speed: about 0.5 and 4 blocks a second). */
    static final double STROLL = 0.8, CHARGE_SPEED = 6.0;
    /** The clips' lengths and moments (ticks): the bite's crushing hit, the yawn, the ear flick, the dung flick, a graze loop. */
    static final int BITE_TICKS = 15, BITE_HIT = 8, YAWN_TICKS = 52, EAR_TICKS = 22, DUNG_TICKS = 32, GRAZE_LOOP = 48;
    /** How hard it bites (5 hearts) and how far its bite reaches past its snout (blocks). */
    static final float BITE_DAMAGE = 10.0F;
    static final double BITE_REACH = 0.9;
    /** A survival player this near gets the warning yawn, this near (or staying) the charge (blocks from its middle). */
    static final double WARN_REACH = 9.0, CHARGE_REACH = 5.5;
    /** A grown crocodile this near a bull (blocks from his middle: about 3 from his snout) gets his yawn, and charged if it stays; he gives up once it is this far. */
    static final double CROC_REACH = 5.5, CROC_GIVE_UP = 8.0;
    /** After biting a crocodile a bull lets crocodiles be this long (ticks: a minute), whether or not it got away (BUGS 273). */
    static final int CROC_CALM = 1200;
    /** A charge gives up when its target is this far, or after this many ticks; then it waits this long before another. */
    static final double CHARGE_GIVE_UP = 16.0;
    static final int CHARGE_TICKS = 240, CHARGE_REST = 200, WARN_AGAIN = 200;
    /** How far from its pool it grazes at night (blocks), and how near it wanders in its pool by day (a tight pod, open water round it). */
    static final int GRAZE_NEAR = 6, GRAZE_FAR = 24, POOL_WANDER = 5;
    /** Water at least this deep (blocks) is a pool it may rest in. */
    public static final int POOL_DEPTH = 2;
    /** Drawn this far off (blocks): pods are few and big, seen across the river. */
    static final double DRAW_DISTANCE = 128.0;

    /**
     * Its moves made once with their clips (the Animal Maker Engine): up and down a block, and out of the shallows onto a
     * bank a block up, from its move file ({@code assets/antcolonies/block_moves/hippo.json}, written by
     * {@code python tools/hippo_models.py game}). All three may start or end in shallow water ({@link BlockMoves.Move#wades}):
     * down off a bank into the shallows, out of them onto a bank.
     */
    public static final BlockMoves MOVES = BlockMoves.of(HippoEntity.class, SavannaMod.MODID, "hippo");
    public static final int UP = MOVES.index("up"), DOWN = MOVES.index("down"), OUT = MOVES.index("out");
    /** How far out from its middle line its feet stand (model pixels, the leg sockets) and its body's half width. */
    static final double FEET_OUT = 9.6, BODY_HALF = 15.0;

    /** The pool it lives in (by day it rests there, at night it grazes round it), or null until it finds one. */
    @Nullable
    BlockPos pool;
    /** Who it is angry at (hurt it, or ignored its warning), and until when (its tick). */
    @Nullable
    LivingEntity angryAt;
    int angryUntil;
    /** Ticks before it may charge again, or yawn a warning at the same player, or mind a crocodile again. */
    int chargeWait, warnWait, crocWait;
    /** The game tick its doing started (for its clips' lengths and moments). */
    int doingSince;
    /** The last tick it moved, and how far a tick, eased (for the clips). */
    private int movedTick = -100;
    private double stride;
    static final int WALK_HOLD = 3;
    /** It was afloat last tick (coming up from the bed it flicks its ears). */
    private boolean wasAfloat;
    @Nullable
    private BlockMoves.Play climb;
    @Nullable
    private CompoundTag climbRead;
    /** The body's clip player (the move's clock is read from it by the model). */
    @Nullable
    public SteadyController<HippoEntity> bodyClips;
    /** Drawing only (HippoModel): whether it was drawn on a move, how far its drawing is still shifted from its place after a hand over (blocks), where it was drawn and when. */
    public boolean drawnClimbing;
    public double shiftX, shiftY, shiftZ, drawnX, drawnY, drawnZ;
    public long drawnNanos;
    /** Where its head is turned (degrees from its heading) and when last drawn: drawing only (HippoModel). */
    public double headAim;
    public long lookNanos;
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HippoEntity(EntityType<? extends HippoEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
        getNavigation().setCanFloat(false);           // heavy: it walks the bed, never swims the surface on a route
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TerritoryGoal(this));
        goalSelector.addGoal(1, new HomeGoal(this));
        goalSelector.addGoal(2, new GrazeGoal(this));
        goalSelector.addGoal(3, new PoolGoal(this));
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
        builder.define(DATA_CHARGING, false);
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

    /** Ticks since its doing started. */
    int doingFor() {
        return tickCount - doingSince;
    }

    public boolean charging() {
        return entityData.get(DATA_CHARGING);
    }

    void setCharging(boolean on) {
        entityData.set(DATA_CHARGING, on);
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** Where its jaws close: the snout's front, a little over the ground. */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + 0.7 * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** Whether an entity is at its jaws (its box within reach of the snout). */
    boolean atJaws(Entity e) {
        return e.getBoundingBox().inflate(BITE_REACH * getScale()).contains(jawPoint())
                || e.distanceToSqr(jawPoint()) < Mth.square(BITE_REACH * getScale() + e.getBbWidth() * 0.5);
    }

    /** Faces a place (a little at a time). */
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

    /** Afloat: in water and off the bed. */
    public boolean afloat() {
        return isInWater() && !onGround();
    }

    /** The surface of the water over its feet (blocks), or NaN if it is not in water. */
    double surface() {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY() + 0.2, getZ()).mutable();
        if (!level().getFluidState(p).is(FluidTags.WATER)) {
            return Double.NaN;
        }
        while (level().getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < getY() + 16) {
            p.move(0, 1, 0);
        }
        return p.getY() + level().getFluidState(p).getHeight(level(), p);
    }

    // --- moves (the Animal Maker Engine, as the monitor's) ----------------------------------------------------------------

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

    /** Drawing only: how far into its move's clip it is drawn now (ticks of the clip), 0 while easing in, below 0 when not on one. */
    public double climbClipTicks() {
        BlockMoves.Play c = climb();
        if (c == null || bodyClips == null || bodyClips.getCurrentAnimation() == null
                || !bodyClips.getCurrentAnimation().animation().name().equals(MOVES.clip(c))) {
            return -1.0;
        }
        return bodyClips.getAnimationState() == AnimationController.State.TRANSITIONING ? 0.0 : bodyClips.clipTicks();
    }

    /** The body clip player's clock: a move's clip plays at the move's own time since its start ({@link SteadyController.Clock}). */
    private double climbClock(String clip, double tick) {
        BlockMoves.Play c = climb();
        if (c == null || !clip.equals(MOVES.clip(c))) {
            return -1.0;
        }
        return Math.max(0.0, MOVES.ticks(c, level().getGameTime() + (tick - Math.floor(tick))));
    }

    /**
     * Starts a move when its route goes a block up or down within its next few squares and it has come to the move's
     * start: the step's face or the edge that far ahead of its middle (its box is two blocks wide, so the face is read
     * from the ground ahead of it, not from the route's squares), heading near enough at it, with room. Up out of the
     * shallows onto a dry bank is the out move; up a step under the water, or a bank too high, is left to Minecraft's
     * own step. Calm only (a charge runs straight at its target, the old way), and not just hurt.
     */
    private boolean startMove() {
        if (isPassenger() || isDeadOrDying() || hurtTime > 0 || doing() != CALM || charging() || !onGround()) {
            return false;
        }
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        int last = Math.min(path.getNodeCount() - 2, path.getNextNodeIndex() + 4);
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= last; i++) {
            Node a = path.getNode(i), b = path.getNode(i + 1);
            if (b.y == a.y) {
                continue;
            }
            int dx = Integer.signum(b.x - a.x), dz = Integer.signum(b.z - a.z);
            if (Math.abs(b.y - a.y) != 1 || Math.abs(dx) + Math.abs(dz) != 1 || a.y != floor) {
                return false;
            }
            boolean up = b.y > a.y;
            boolean alongX = dx != 0;
            int sign = dx + dz;
            double face = faceAhead(alongX, sign, floor, up);
            if (Double.isNaN(face)) {
                return false;
            }
            int move = up ? (isInWater() ? OUT : UP) : DOWN;
            BlockMoves.Play c = moveAt(alongX, sign, face, floor, move);
            if (c != null) {
                entityData.set(DATA_CLIMB, c.save());
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * The step's face (going up) or the edge (going down) ahead of its middle along its way, read from the ground under
     * its middle line within 4 blocks: the first column a block higher (its block at its feet whole), or the first whose
     * ground is gone (going down). NaN if there is none.
     */
    private double faceAhead(boolean alongX, int sign, int floor, boolean up) {
        double along = alongX ? getX() : getZ(), across = alongX ? getZ() : getX();
        int from = Mth.floor(along);
        for (int k = 0; k <= 4; k++) {
            int col = from + sign * k;
            BlockPos at = alongX ? new BlockPos(col, floor, Mth.floor(across)) : new BlockPos(Mth.floor(across), floor, col);
            boolean edge = up ? full(at) : !full(at.below());
            if (edge) {
                return sign > 0 ? col : col + 1;
            }
        }
        return Double.NaN;
    }

    /**
     * The move toward the face (or edge) at {@code face}, if its middle has come to the move's start and there is room;
     * else null. Coming at it more than {@link BlockMoves#SQUARE} degrees off square it first turns square on the spot.
     */
    @Nullable
    private BlockMoves.Play moveAt(boolean alongX, int sign, double face, int floor, int move) {
        double scale = MODEL_SCALE * getScale(), back = MOVES.move(move).aheadPx * scale / 16.0;
        double dist = (face - (alongX ? getX() : getZ())) * sign;
        // not there yet, or come too close to fit the move (a route turning up the step right at it: the old step)
        if (dist > back + 0.1 || dist < back - 0.5) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(alongX ? 0 : sign, alongX ? sign : 0)) - 90.0F;
        int turn = BlockMoves.turnFor(yBodyRot, yaw);
        if (turn < 0) {
            return null;
        }
        double across = alongX ? getZ() : getX();
        boolean room = move == DOWN ? roomDown(alongX, sign, face, floor, across, scale)
                : roomUp(alongX, sign, face, floor, across, scale, move == OUT);
        if (!room) {
            return null;
        }
        double along = face - back * sign;
        return MOVES.start(this, move, alongX ? along : across, getY(), alongX ? across : along, yaw, turn);
    }

    /**
     * Room to climb a block (or out of the shallows): a whole block under both feet lines for three blocks past the face,
     * dry and open over it (two blocks: its body), and open over the lower ground in front of the face where it rears.
     * Out of the shallows the water's surface is no higher than the bank's top (a bank, not a step under the water).
     */
    private boolean roomUp(boolean alongX, int sign, double face, int y, double across, double scale, boolean out) {
        double feet = FEET_OUT * scale / 16.0, half = BODY_HALF * scale / 16.0;
        for (double side : new double[] {across - feet, across + feet}) {
            for (double ahead : new double[] {0.5, 1.5, 2.5}) {
                BlockPos step = cell(alongX, face + ahead * sign, side, y);
                if (!full(step) || !dryOpen(step.above()) || !dryOpen(step.above(2))) {
                    return false;
                }
            }
        }
        for (double side : new double[] {across - half, across, across + half}) {
            for (double ahead : new double[] {-2.5, -1.5, -0.5}) {
                BlockPos low = cell(alongX, face + ahead * sign, side, y);
                if (!open(low) || !open(low.above()) || !open(low.above(2))) {
                    return false;
                }
            }
            for (double ahead : new double[] {0.5, 1.5, 2.5, 3.5}) {
                BlockPos top = cell(alongX, face + ahead * sign, side, y + 1);
                if (!dryOpen(top) || !dryOpen(top.above())) {
                    return false;
                }
            }
        }
        if (out) {
            // wading: the water round its legs at most a block deep (its bed one block under the bank's top)
            BlockPos at = BlockPos.containing(getX(), y + 1, getZ());
            return !level().getFluidState(at).is(FluidTags.WATER);
        }
        return !isInWater();
    }

    /**
     * Room to climb down a block: the top it leaves whole under both feet lines, and past the edge for three blocks a
     * whole block a block lower under both feet, open (the shallows' water no deeper than that block: wading in) and open
     * over it for its body.
     */
    private boolean roomDown(boolean alongX, int sign, double face, int y, double across, double scale) {
        double feet = FEET_OUT * scale / 16.0, half = BODY_HALF * scale / 16.0;
        for (double side : new double[] {across - feet, across + feet}) {
            if (!full(cell(alongX, face - 0.5 * sign, side, y - 1))) {
                return false;
            }
            for (double ahead : new double[] {0.5, 1.5, 2.5}) {
                BlockPos low = cell(alongX, face + ahead * sign, side, y - 2);
                if (!full(low) || !open(low.above()) || !dryOpen(low.above(2))) {
                    return false;
                }
            }
        }
        for (double side : new double[] {across - half, across, across + half}) {
            for (double ahead : new double[] {0.5, 1.5, 2.5, 3.5}) {
                BlockPos at = cell(alongX, face + ahead * sign, side, y - 1);
                if (!open(at) || !dryOpen(at.above()) || !dryOpen(at.above(2))) {
                    return false;
                }
            }
        }
        return !isInWater();
    }

    private boolean full(BlockPos pos) {
        return level().getBlockState(pos).isCollisionShapeFullBlock(level(), pos);
    }

    private boolean open(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    private boolean dryOpen(BlockPos pos) {
        return open(pos) && level().getFluidState(pos).isEmpty();
    }

    private static BlockPos cell(boolean alongX, double along, double across, int y) {
        return alongX ? new BlockPos(Mth.floor(along), y, Mth.floor(across)) : new BlockPos(Mth.floor(across), y, Mth.floor(along));
    }

    /**
     * Moves it on along its move this tick ({@link BlockMoves#step}). Ends it once done or cut short (dead, the step gone,
     * a block put in its way); after a move it stands a moment unless its route goes straight on up or down another.
     */
    private boolean climbOn(BlockMoves.Play c) {
        BlockMoves.Step step = MOVES.step(this, c, MODEL_SCALE * getScale() / 16.0, !hillAhead());
        if (step == BlockMoves.Step.ON) {
            return true;
        }
        entityData.set(DATA_CLIMB, new CompoundTag());
        if (!getNavigation().isDone()) {
            getNavigation().recomputePath();
        }
        return false;
    }

    /** Whether its route goes on up or down another block within the next few squares (a hill: no rest between the moves). */
    private boolean hillAhead() {
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++) {
            if (path.getNode(i).y != floor) {
                return true;
            }
        }
        return false;
    }

    /** A hurt cuts a move short: set down where it is, and its anger takes over. */
    private void cutMove() {
        if (climb() != null) {
            BlockMoves.cut(this);
            entityData.set(DATA_CLIMB, new CompoundTag());
        }
    }

    /**
     * On a move the move places it. In water it walks the bed at its full pace (not Minecraft's slow wading), bounding
     * up a step of the bed; still, it rises to float with its eyes and back out where the water is deep enough
     * ({@link #tick}).
     */
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
            moveRelative(getSpeed() * 0.9F, travelVector);
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement();
            v = new Vec3(v.x * 0.6, v.y, v.z * 0.6);
            if (horizontalCollision && isFree(v.x, v.y + 1.1 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.35, v.z);          // up a step of the bed
            }
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();
        double dx = getX() - xo, dz = getZ() - zo, moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 0.004) {
            movedTick = tickCount;
            stride += (moved - stride) * 0.5;
        }
        if (level().isClientSide) {
            splashes();
            return;
        }
        if (chargeWait > 0) {
            chargeWait--;
        }
        if (warnWait > 0) {
            warnWait--;
        }
        if (crocWait > 0) {
            crocWait--;
        }
        if (angryAt != null && (tickCount > angryUntil || !angryAt.isAlive())) {
            angryAt = null;
        }
        // a doing's clip played out: back to calm
        byte d = doing();
        if (d == YAWN && doingFor() >= YAWN_TICKS || d == EAR_FLICK && doingFor() >= EAR_TICKS || d == DUNG && doingFor() >= DUNG_TICKS
                || d == BITE && doingFor() >= BITE_TICKS) {
            setDoing(CALM);
        }
        // in the water: walking, it sinks to the bed and bounds along it; still, it rises to float with its eyes, ears
        // and back out where the water is deep enough, and stands on the bed where it is not
        if (isInWater() && climb() == null) {
            double surface = surface();
            boolean going = !getNavigation().isDone() || charging() || walking();
            Vec3 v = getDeltaMovement();
            if (going || Double.isNaN(surface)) {
                setDeltaMovement(v.x, Math.min(v.y, 0.0) - 0.03, v.z);
            } else {
                double want = surface - AFLOAT * getScale();
                setDeltaMovement(v.x, Mth.clamp((want - getY()) * 0.1, -0.03, 0.03) + v.y * 0.5, v.z);
            }
            boolean nowAfloat = afloat() && !going;
            if (nowAfloat && !wasAfloat && doing() == CALM && random.nextInt(2) == 0) {
                setDoing(EAR_FLICK);                    // come up: the ears spin the water off
            }
            wasAfloat = nowAfloat;
        } else {
            wasAfloat = false;
        }
    }

    /** Drawing only: a splash where it goes into the water off a move, and as it surfaces. */
    private void splashes() {
        if (isInWater() && walking() && stride > 0.04 * getScale() && random.nextInt(3) == 0) {
            double s = surface();
            if (!Double.isNaN(s) && s - getY() < 1.4 * getScale()) {
                float yaw = yBodyRot * Mth.DEG_TO_RAD;
                double ahead = 1.6 * getScale();
                level().addParticle(ParticleTypes.SPLASH, getX() - Mth.sin(yaw) * ahead + (random.nextDouble() - 0.5) * 1.6,
                        s + 0.05, getZ() + Mth.cos(yaw) * ahead + (random.nextDouble() - 0.5) * 1.6, 0.0, 0.1, 0.0);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            cutMove();
            if (isAlive() && source.getEntity() instanceof LivingEntity by && by != this
                    && !(by instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))) {
                angryAt = by;                           // whoever hurt it is charged at once
                angryUntil = tickCount + 400;
                chargeWait = 0;
            }
        }
        return hurt;
    }

    /** Its bite lands (the crushing hit of the bite clip): hard, and it shoves the target away. */
    void biteHits(LivingEntity target) {
        if (target.isAlive() && atJaws(target) && target.hurt(damageSources().mobAttack(this), BITE_DAMAGE)) {
            playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 0.8F);
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            target.knockback(1.2, Mth.sin(yaw), -Mth.cos(yaw));
        }
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;          // it holds its breath for minutes; in the game it comes up when still
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.9F;
    }

    /** Its spawn spot is its pool's water: only blocks and mobs stand in its way. */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Kept for good, as Minecraft's villagers: the pod a player left is there when they come back (BUGS 271). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Drawn whenever any of its body is in view: its box is only the barrel of its body, so its head and rump (2.5 blocks
     * out from its middle) blinked away at the screen's edge with the box (BUGS 271).
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double s = getScale();
        return getBoundingBox().inflate(1.7 * s, 0.4 * s, 1.7 * s);
    }

    /** Joins a pod resting in the pool at {@code pool} (new land's pods: the bull's spot). */
    public void joinPod(BlockPos pool) {
        this.pool = pool.immutable();
    }

    /** Its pool (for tests). */
    @Nullable
    public BlockPos pool() {
        return pool;
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
            // spawned somewhere in its pool's water: set down on the bed under it
            BlockPos.MutableBlockPos p = blockPosition().mutable();
            while (p.getY() > level.getMinBuildHeight() && !level.getBlockState(p.below()).isFaceSturdy(level, p.below(),
                    net.minecraft.core.Direction.UP)) {
                p.move(0, -1, 0);
            }
            moveTo(getX(), p.getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
        }
        life.setUp(false, level.getRandom());
        life.apply();
        if (pool == null && isInWater()) {
            pool = blockPosition();
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (pool != null) {
            tag.put("Pool", NbtUtils.writeBlockPos(pool));
        }
        if (led) {
            tag.putBoolean("Led", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        pool = NbtUtils.readBlockPos(tag, "Pool").orElse(null);
        led = tag.getBoolean("Led");             // the film camera's walkers and game tests ("Led:1b")
        life.apply();
    }

    /** How deep the water is in the column at pos (blocks of water from the surface down to the bed). */
    static int depthAt(LevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < pos.getY() + 16) {
            p.move(0, 1, 0);
        }
        int depth = 0;
        while (level.getFluidState(p).is(FluidTags.WATER) && depth < 16) {
            depth++;
            p.move(0, -1, 0);
        }
        return depth;
    }

    /** A pool spot near a place: water at least {@link #POOL_DEPTH} deep (its bed to stand on), else any water; null if none within reach. */
    @Nullable
    BlockPos poolNear(BlockPos from, int reach) {
        Level level = level();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            int x = from.getX() + random.nextInt(2 * reach + 1) - reach, z = from.getZ() + random.nextInt(2 * reach + 1) - reach;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            BlockPos at = new BlockPos(x, top, z);
            if (!level.getFluidState(at).is(FluidTags.WATER)) {
                continue;
            }
            int depth = depthAt(level, at);
            BlockPos bed = at.below(depth - 1);
            double d = bed.distSqr(from) - (depth >= POOL_DEPTH ? 400 : 0);
            if (d < bestD) {
                bestD = d;
                best = bed;
            }
        }
        return best;
    }

    /** A grass spot to graze on land, between {@link #GRAZE_NEAR} and {@link #GRAZE_FAR} blocks from its pool; null if none found. */
    @Nullable
    BlockPos grassNear(BlockPos home) {
        Level level = level();
        for (int i = 0; i < 30; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = GRAZE_NEAR + random.nextDouble() * (GRAZE_FAR - GRAZE_NEAR);
            int x = home.getX() + Mth.floor(Math.cos(a) * r), z = home.getZ() + Mth.floor(Math.sin(a) * r);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos stand = new BlockPos(x, y, z);
            if (Math.abs(y - home.getY()) > 8 || !level.getFluidState(stand).isEmpty() || !level.getFluidState(stand.below()).isEmpty()) {
                continue;
            }
            BlockState ground = level.getBlockState(stand.below());
            if (ground.is(Blocks.GRASS_BLOCK) || ground.is(BlockTags.DIRT)) {
                return stand;
            }
        }
        return null;
    }

    /** Eats a grass plant at its jaws, if any (the plant is gone; the grass block under it stays). */
    void cropGrass() {
        Vec3 jaw = jawPoint();
        BlockPos at = BlockPos.containing(jaw.x, getY() + 0.2, jaw.z);
        for (BlockPos p : List.of(at, at.above())) {
            BlockState s = level().getBlockState(p);
            if (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)) {
                level().destroyBlock(p, false, this);
                return;
            }
        }
    }

    /**
     * Led by a game test or the film camera: its own day and night goals (home, graze, pool) are off, so only where it is
     * sent and its territory move it.
     */
    public boolean led;
    /** For tests: night or day held whatever the clock (null: the clock). */
    @Nullable
    public Boolean heldNight;

    /** Where it was last sent and whether a route was found (for tests' messages). */
    @Nullable
    BlockPos sentTo;
    boolean routeFound;

    /** Sends it somewhere on its own legs (its goals), noting where for tests' messages. */
    boolean sendTo(BlockPos to, double speed) {
        sentTo = to;
        routeFound = getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, speed);
        return routeFound;
    }

    /** What it is about, for tests' messages. */
    public String about() {
        Path p = getNavigation().getPath();
        return "pool " + pool + ", sent to " + sentTo + " (route " + routeFound + "), route now "
                + (p == null ? "none" : p.getNextNodeIndex() + "/" + p.getNodeCount() + " to " + p.getTarget() + (p.canReach() ? "" : " (can't reach)"))
                + ", on ground " + onGround() + ", afloat " + afloat() + ", night " + night();
    }

    /** Night: when hippos graze (the shared day clock, as the crocodile's and monitor's). */
    boolean night() {
        return heldNight != null ? heldNight : !level().isDay();
    }

    // --- the goals -----------------------------------------------------------------------------------------------------

    /**
     * Its territory: a survival player coming within {@link #WARN_REACH} blocks gets a yawn as a warning (facing them);
     * still within {@link #CHARGE_REACH} after it, or coming nearer, or whoever hurt it, is charged: it runs at them and
     * bites (the crushing hit at the clip's {@link #BITE_HIT}) until they are {@link #CHARGE_GIVE_UP} blocks off or
     * {@link #CHARGE_TICKS} have passed; then it rests a while before another. A grown crocodile is minded by bulls only,
     * and only within {@link #CROC_REACH}: the yawn first, then, if it stays, a bluff charge that ends once it is
     * {@link #CROC_GIVE_UP} off, with one bite at most, after which he lets crocodiles be for {@link #CROC_CALM} ticks
     * even if one could not get away (real pods let crocodiles bask beside them; BUGS 272, 273).
     */
    static class TerritoryGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private LivingEntity target;
        private boolean charge, bitten;
        private int ticks, repath;

        TerritoryGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (h.climb() != null || h.doing() == REST) {
                return false;
            }
            if (h.angryAt != null && h.angryAt.isAlive()) {
                target = h.angryAt;
                charge = true;
                return true;
            }
            if (h.chargeWait > 0 || h.getRandom().nextInt(3) != 0) {
                return false;
            }
            LivingEntity p = h.life().isMale() && h.crocWait == 0 ? nearestCroc() : null;
            if (p == null) {
                p = h.level().getNearestPlayer(h.getX(), h.getY(), h.getZ(), WARN_REACH * h.getScale(),
                        e -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(e));
            }
            if (p == null) {
                return false;
            }
            target = p;
            charge = p.distanceTo(h) < chargeReach(p) && h.warnWait > 0;
            if (!charge && h.warnWait > 0) {
                return false;
            }
            return true;
        }

        @Nullable
        private LivingEntity nearestCroc() {
            double reach = CROC_REACH * h.getScale();
            CrocodileEntity best = null;
            for (CrocodileEntity c : h.level().getEntitiesOfClass(CrocodileEntity.class, h.getBoundingBox().inflate(reach))) {
                if (c.isAlive() && c.getScale() > 0.5F && c.distanceTo(h) < reach && (best == null || c.distanceTo(h) < best.distanceTo(h))) {
                    best = c;
                }
            }
            return best;
        }

        /** Within this it charges a target that stayed after the warning (a crocodile: right up to him). */
        private double chargeReach(LivingEntity t) {
            return (t instanceof CrocodileEntity ? CROC_REACH : CHARGE_REACH) * h.getScale();
        }

        /** Past this a charge gives up (a crocodile: once it has backed off). */
        private double giveUp(LivingEntity t) {
            return (t instanceof CrocodileEntity ? CROC_GIVE_UP : CHARGE_GIVE_UP) * h.getScale();
        }

        @Override
        public void start() {
            ticks = 0;
            repath = 0;
            bitten = false;
            h.getNavigation().stop();
            if (!charge) {
                h.setDoing(YAWN);                   // the warning: the great yawn, facing them
                h.playSound(SoundEvents.RAVAGER_ROAR, 0.7F, 1.4F);
                h.warnWait = WARN_AGAIN;
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || !target.isAlive() || h.isDeadOrDying()
                    || target instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
                return false;
            }
            if (!charge) {
                return h.doing() == YAWN || ticks < YAWN_TICKS;
            }
            if (bitten && target instanceof CrocodileEntity && h.doing() != BITE) {
                return false;                           // one bite sees a crocodile off
            }
            return ticks < CHARGE_TICKS && target.distanceTo(h) < giveUp(target);
        }

        @Override
        public void tick() {
            ticks++;
            if (!charge) {
                h.face(target.getX(), target.getZ(), 6.0F);
                h.getLookControl().setLookAt(target);
                if (ticks >= YAWN_TICKS - 2 && target.distanceTo(h) < chargeReach(target)) {
                    charge = true;                  // stayed near after the warning
                    ticks = 0;
                    h.setDoing(CALM);
                }
                return;
            }
            h.getLookControl().setLookAt(target);
            if (h.doing() == BITE) {
                h.face(target.getX(), target.getZ(), 10.0F);
                if (h.doingFor() == BITE_HIT) {
                    h.biteHits(target);
                    bitten = true;
                }
                return;
            }
            h.setCharging(true);
            if (h.atJaws(target)) {
                h.getNavigation().stop();
                h.setDoing(BITE);
                h.playSound(SoundEvents.RAVAGER_ROAR, 0.5F, 1.8F);
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                h.getNavigation().moveTo(target, CHARGE_SPEED);
            }
        }

        @Override
        public void stop() {
            h.setCharging(false);
            if (h.doing() == YAWN || h.doing() == BITE) {
                h.setDoing(CALM);
            }
            if (bitten && target instanceof CrocodileEntity) {
                h.crocWait = CROC_CALM;                 // one bite, then he lets crocodiles be a while
            }
            if (charge) {
                h.chargeWait = CHARGE_REST;
                if (h.angryAt == target) {
                    h.angryAt = null;
                }
            }
            h.getNavigation().stop();
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** By day out of the water (dawn after the night's grazing): back to its pool, or the nearest water deep enough. */
    static class HomeGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private BlockPos to;
        private int ticks;

        HomeGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (h.led || h.night() || h.isInWater() || h.climb() != null || h.getRandom().nextInt(10) != 0) {
                return false;
            }
            BlockPos from = h.pool != null ? h.pool : h.blockPosition();
            to = h.poolNear(from, h.pool != null ? 6 : 24);
            return to != null;
        }

        @Override
        public void start() {
            ticks = 0;
            h.setDoing(CALM);
            h.sendTo(to, STROLL * 1.2);
        }

        @Override
        public boolean canContinueToUse() {
            return !h.isInWater() && ticks < 1200 && (!h.getNavigation().isDone() || h.climb() != null);
        }

        @Override
        public void tick() {
            if (++ticks % 100 == 0 && h.getNavigation().isDone() && h.climb() == null) {
                h.sendTo(to, STROLL * 1.2);
            }
        }
    }

    /**
     * At night: out of the water to graze the land round its pool. It walks to a grass spot {@link #GRAZE_NEAR} to
     * {@link #GRAZE_FAR} blocks off, crops there for 20 to 40 seconds (a grass plant at its jaws eaten each loop or two),
     * now and then flicking its dung, then moves on to another; at dawn the home goal takes it back.
     */
    static class GrazeGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private BlockPos spot;
        private int left, ticks;

        GrazeGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (h.led || !h.night() || h.climb() != null || h.getRandom().nextInt(10) != 0) {
                return false;
            }
            if (h.pool == null && h.isInWater()) {
                h.pool = h.blockPosition();
            }
            spot = h.grassNear(h.pool != null ? h.pool : h.blockPosition());
            return spot != null;
        }

        @Override
        public void start() {
            left = 400 + h.getRandom().nextInt(400);
            ticks = 0;
            h.setDoing(CALM);
            h.sendTo(spot, STROLL);
        }

        @Override
        public boolean canContinueToUse() {
            return h.night() && left > 0 && ticks < 1600;
        }

        @Override
        public void tick() {
            ticks++;
            boolean there = h.getNavigation().isDone() && h.climb() == null && !h.isInWater();
            if (!there) {
                if (ticks % 100 == 0 && h.getNavigation().isDone() && h.climb() == null) {
                    h.sendTo(spot, STROLL);
                }
                return;
            }
            left--;
            if (h.doing() == CALM) {
                if (h.getRandom().nextInt(600) == 0) {
                    h.setDoing(DUNG);
                } else {
                    h.setDoing(GRAZE);
                }
            }
            if (h.doing() == GRAZE && h.doingFor() > 0 && h.doingFor() % GRAZE_LOOP == GRAZE_LOOP / 2 && h.getRandom().nextBoolean()) {
                h.cropGrass();
            }
        }

        @Override
        public void stop() {
            if (h.doing() == GRAZE) {
                h.setDoing(CALM);
            }
        }
    }

    /**
     * By day in the water: it rests afloat (or on the bed in the shallows), and every half minute to a minute and a half
     * wanders to another spot of its pool along the bed, within {@link #POOL_WANDER} of its middle, so the pod stays
     * packed together; now and then it yawns. Its pool moves only when the pool itself is gone (the pod's members
     * drifting each to its own pool spread a pod over half a river, BUGS 272).
     */
    static class PoolGoal extends Goal {
        private final HippoEntity h;
        private int restFor;

        PoolGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !h.led && !h.night() && h.isInWater() && h.climb() == null;
        }

        @Override
        public void start() {
            restFor = 200 + h.getRandom().nextInt(600);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (!h.getNavigation().isDone()) {
                return;
            }
            if (h.doing() == CALM && --restFor <= 0) {
                if (h.pool == null || h.poolGone()) {
                    h.pool = h.blockPosition();         // its pool dried or filled in: here is its pool now
                }
                if (h.getRandom().nextInt(4) == 0) {
                    h.setDoing(YAWN);
                    h.playSound(SoundEvents.RAVAGER_AMBIENT, 0.6F, 0.6F);
                } else {
                    BlockPos to = h.poolNear(h.pool, POOL_WANDER);
                    if (to != null) {
                        h.sendTo(to, STROLL);
                    }
                }
                restFor = 200 + h.getRandom().nextInt(1200);
            }
        }
    }

    /** Whether its pool is gone: no water left at it. */
    boolean poolGone() {
        return pool != null && !level().getFluidState(pool).is(FluidTags.WATER) && !level().getFluidState(pool.above()).is(FluidTags.WATER);
    }

    /** How deep the water is where it stands (blocks). */
    int depthHere() {
        return depthAt(level(), BlockPos.containing(getX(), getY() + 0.2, getZ()));
    }

    // --- clips -----------------------------------------------------------------------------------------------------------

    /** Clips named by the film camera ({@code film:<clip>}, a still one). */
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("charge");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation BED_WALK = RawAnimation.begin().thenLoop("underwater_walk");
    private static final RawAnimation YAWN_CLIP = RawAnimation.begin().thenPlayAndHold("yawn");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation EAR_CLIP = RawAnimation.begin().thenPlayAndHold("ear_flick");
    private static final RawAnimation DUNG_CLIP = RawAnimation.begin().thenPlayAndHold("dung_flick");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation REST_CLIP = RawAnimation.begin().thenPlay("rest_down").thenLoop("rest");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** Every move's clip, by its name ({@link #MOVES}; made as first played). */
    private static final java.util.Map<String, RawAnimation> MOVE_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        bodyClips = new SteadyController<>(this, "body", MOVES.lead(), this::animate).clock(HippoEntity::climbClock);
        bodyClips.setCustomInstructionKeyframeHandler(note -> {
        });         // the moves' clips' notes (their path) are for the tools only
        controllers.add(bodyClips);
    }

    /** Whether it walks (drawing only): it moved within the last {@link #WALK_HOLD} ticks. */
    public boolean walking() {
        return tickCount - movedTick <= WALK_HOLD;
    }

    /** Whether the water is over its back where it stands (drawing only: it bounds along the bed, not wades). */
    private boolean underWater() {
        double s = surface();
        return !Double.isNaN(s) && s - getY() > 1.5 * getScale();
    }

    private PlayState animate(AnimationState<HippoEntity> state) {
        AnimationController<HippoEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        BlockMoves.Play c = climb();
        if (c != null && !c.turning(level().getGameTime())) {     // a move wins over everything else it does
            return state.setAndContinue(MOVE_CLIPS.computeIfAbsent(MOVES.clip(c), n -> RawAnimation.begin().thenPlayAndHold(n)));
        }
        if (c != null) {                        // turning square to the edge on the spot first: its feet step round
            return state.setAndContinue(WALK);
        }
        switch (doing()) {
            case YAWN:
                return state.setAndContinue(YAWN_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case EAR_FLICK:
                return state.setAndContinue(EAR_CLIP);
            case DUNG:
                return state.setAndContinue(DUNG_CLIP);
            case GRAZE:
                return state.setAndContinue(GRAZE_CLIP);
            case REST:
                return state.setAndContinue(REST_CLIP);
            default:
                break;
        }
        double size = getScale();
        if (walking() && isInWater() && underWater()) {
            player.setAnimationSpeed(Mth.clamp(stride / (0.025 * size), 0.6, 2.0));
            return state.setAndContinue(BED_WALK);
        }
        if (afloat() && !walking()) {
            return state.setAndContinue(FLOAT);
        }
        if (walking()) {
            if (charging() && stride > CHARGE_DRAWN * size) {
                player.setAnimationSpeed(Mth.clamp(stride / (CHARGE_CLIP_SPEED * size), 0.6, 3.0));
                return state.setAndContinue(CHARGE);
            }
            player.setAnimationSpeed(Mth.clamp(stride / (WALK_CLIP_SPEED * size), 0.5, 3.0));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
