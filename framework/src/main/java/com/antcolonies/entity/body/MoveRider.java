package com.antcolonies.entity.body;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.animation.AnimationController;

/**
 * Rides an animal through its block moves ({@link BlockMoves}): everything round the shared move player that the Nile
 * monitor and the hippo each wrote for themselves (2026-11-19 to 21), written once (2026-11-22, the otter first; the
 * monitor and the hippo keep their own until they are moved over). Species-neutral: an animal says which of its moves
 * fits which kind of edge ({@link Edge}), how wide its feet stand and how deep it floats; this finds the edge on its
 * route, checks the room, starts the move, carries its box along the move each tick, ends it, and works out where the
 * model is drawn and the splashes.
 * <p>
 * Each kind of edge is one of Minecraft's fixed shapes: a block up or down on land, into water off a bank level with
 * the water's top (walking, or running and in a hurry), off a bank one or two blocks over it, out onto a bank level
 * with the water's top or a block over it. An edge the animal has no move for is left to Minecraft's own step, fall
 * and hop.
 */
public final class MoveRider {
    /** The kinds of edge a move is made for. */
    public enum Edge {
        UP, DOWN, IN, RUN_IN, DROP, DROP2, OUT, OUT_UP
    }

    /** What an animal tells the rider: its moves, its synced slot for the move, which move fits which edge, its build. */
    public static final class Setup {
        final BlockMoves moves;
        final EntityDataAccessor<CompoundTag> data;
        final int[] edges = new int[Edge.values().length];
        double modelScale = 1.0, feetOutPx = 4.0, afloat = 0.5, run = 0.08, smallest = 0.0;

        public Setup(BlockMoves moves, EntityDataAccessor<CompoundTag> data) {
            this.moves = moves;
            this.data = data;
            java.util.Arrays.fill(edges, -1);
        }

        /** The move (by its name in the move file) played at this kind of edge. */
        public Setup edge(Edge edge, String move) {
            int i = moves.index(move);
            if (i < 0) {
                throw new IllegalArgumentException("no move " + move);
            }
            edges[edge.ordinal()] = i;
            return this;
        }

        /** The model's drawn scale at size 1. */
        public Setup modelScale(double scale) {
            modelScale = scale;
            return this;
        }

        /** How far out from its middle line its feet stand (model pixels). */
        public Setup feetOut(double px) {
            feetOutPx = px;
            return this;
        }

        /** How far under the water's surface its feet hang afloat (blocks at size 1). */
        public Setup afloat(double blocks) {
            afloat = blocks;
            return this;
        }

        /** Faster than this it is running (blocks a tick at size 1): into water it takes the running move. */
        public Setup run(double blocksPerTick) {
            run = blocksPerTick;
            return this;
        }

        /** Smaller than this (its size) it plays no moves: its young step the old way. */
        public Setup smallest(double size) {
            smallest = size;
            return this;
        }
    }

    private final Mob mob;
    private final Setup s;
    @Nullable
    private BlockMoves.Play play;
    /** Drawing only: the time its move is drawn at, for its clip and its place ({@link MoveClock}). */
    private final MoveClock drawClock = new MoveClock();
    @Nullable
    private CompoundTag read;
    private int movedTick = -100;
    private double stride;
    /** Set by the animal each tick: in a hurry (fleeing, chasing, playing), it takes the running move into the water. */
    public boolean hurry;
    /** The last move ended and the game tick it ended (server). */
    @Nullable
    private Edge ended;
    private long endedAt = Long.MIN_VALUE;
    /** Drawing only: drawn on a move, how far the drawing is still shifted after a hand over, where drawn and when. */
    private boolean drawnOn;
    private double shiftX, shiftY, shiftZ;
    public double drawnX, drawnY, drawnZ;
    public long drawnNanos;
    private int wetUntil;
    private double lastPathY = Double.NaN;
    /** How quickly the drawing slides from its box onto a move's path, or back (seconds; the monitor's). */
    private static final double HAND_OVER = 0.12;

    public MoveRider(Mob mob, Setup setup) {
        this.mob = mob;
        this.s = setup;
    }

    public static void define(SynchedEntityData.Builder builder, EntityDataAccessor<CompoundTag> data) {
        builder.define(data, new CompoundTag());
    }

    public BlockMoves moves() {
        return s.moves;
    }

    // --- asking -----------------------------------------------------------------------------------------------------

    /** Its move now, or null (both sides). */
    @Nullable
    public BlockMoves.Play play() {
        CompoundTag tag = mob.getEntityData().get(s.data);
        if (tag != read) {
            read = tag;
            play = BlockMoves.Play.of(tag);
        }
        return play;
    }

    /** Whether a move is on and past turning square on the spot (its clip plays). */
    public boolean clipOn() {
        BlockMoves.Play p = play();
        return p != null && !p.turning(mob.level().getGameTime());
    }

    /** The kind of edge of a move played, or null. */
    @Nullable
    public Edge edge(@Nullable BlockMoves.Play p) {
        if (p == null) {
            return null;
        }
        for (Edge e : Edge.values()) {
            if (s.edges[e.ordinal()] == p.move()) {
                return e;
            }
        }
        return null;
    }

    /** The last move that ended (server), if it ended within this many ticks; else null. */
    @Nullable
    public Edge endedWithin(int ticks) {
        return mob.level().getGameTime() - endedAt <= ticks ? ended : null;
    }

    /** The clip's name of the move played now, or null. */
    @Nullable
    public String clip() {
        BlockMoves.Play p = play();
        return p == null ? null : s.moves.clip(p);
    }

    /** The body clip player's clock ({@link SteadyController.Clock}): a move's clip plays at the move's own time since its start. */
    public double clock(String clip, double tick) {
        BlockMoves.Play p = play();
        if (p == null || !clip.equals(s.moves.clip(p))) {
            return -1.0;
        }
        return s.moves.drawnTicks(p, drawClock, mob.level().getGameTime(), tick);
    }

    /** Its eased step (blocks a tick), noted each tick on both sides ({@link #noteMoved}). */
    public double stride() {
        return stride;
    }

    /** Whether it moved within this many ticks. */
    public boolean movedWithin(int ticks) {
        return mob.tickCount - movedTick <= ticks;
    }

    /** Call once a tick, both sides, after the game moved it: notes how far it went. */
    public void noteMoved() {
        double dx = mob.getX() - mob.xo, dz = mob.getZ() - mob.zo, moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 0.004) {
            movedTick = mob.tickCount;
            stride += (moved - stride) * 0.5;
        }
    }

    // --- playing (server) -------------------------------------------------------------------------------------------

    /**
     * Call from the animal's {@code travel}: carries it on along its move, or starts one when its route calls for it
     * and {@code mayStart}. True when the move placed it this tick (the animal moves no further itself).
     */
    public boolean travel(boolean mayStart) {
        if (mob.level().isClientSide || !mob.isControlledByLocalInstance()) {
            return false;
        }
        BlockMoves.Play p = play();
        if (p != null) {
            return stepOn(p);
        }
        if (mayStart && start()) {
            return stepOn(play());
        }
        return false;
    }

    /** A hurt (or anything else) cuts a move short: set down where it is. */
    public void cut() {
        if (play() != null) {
            BlockMoves.cut(mob);
            mob.getEntityData().set(s.data, new CompoundTag());
        }
    }

    private boolean stepOn(BlockMoves.Play p) {
        BlockMoves.Move m = s.moves.move(p.move());
        BlockMoves.Step step = s.moves.step(mob, p, s.modelScale * mob.getScale() / 16.0, !hillAhead());
        if (step == BlockMoves.Step.ON) {
            return true;
        }
        mob.getEntityData().set(s.data, new CompoundTag());
        if (step != BlockMoves.Step.CUT) {
            ended = edge(p);
            endedAt = mob.level().getGameTime();
        }
        if (step == BlockMoves.Step.GLIDE) {
            return true;
        }
        if (!mob.getNavigation().isDone()) {
            // its route made before the move (in the water it may end at the water's edge) made afresh from here
            mob.getNavigation().recomputePath();
        }
        return false;
    }

    /** Whether its route goes on up or down another block within the next few nodes (a hill: no rest between the moves). */
    private boolean hillAhead() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(mob.getY() + 1.0E-3);
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 4); i++) {
            if (path.getNode(i).y != floor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Starts a move when its route ahead calls for one and it has come to the move's start, near enough square to the
     * step's face, the edge or the bank, with room: on land a block up or down, or into water off a bank; afloat at its
     * resting depth, out onto a bank. Not on a passenger, a dying animal, one just hurt or one smaller than its moves.
     */
    private boolean start() {
        if (mob.isPassenger() || mob.isDeadOrDying() || mob.hurtTime > 0 || mob.getScale() < s.smallest) {
            return false;
        }
        boolean afloat = mob.isInWater() && !mob.onGround();
        if (!afloat && (!mob.onGround() || mob.isInWater())) {
            return false;
        }
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            // afloat at a bank its route ended short of (Minecraft's routes stop at the water's edge): out straight ahead
            return afloat && headedForLand() && outAhead();
        }
        int floor = Mth.floor(mob.getY() + 1.0E-3);
        int last = Math.min(path.getNodeCount() - 2, path.getNextNodeIndex() + 3);
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= last; i++) {
            Node a = path.getNode(i), b = path.getNode(i + 1);
            int dx = b.x - a.x, dz = b.z - a.z;
            if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && (b.y != a.y || !afloat
                    && mob.level().getFluidState(new BlockPos(b.x, a.y - 1, b.z)).is(FluidTags.WATER))) {
                // Minecraft's routes cut a step's or a bank's corner on the slant: read it as the square step along the
                // way it faces most (the otter's tests, 2026-11-22: coming back down a step it never hopped, and now
                // and then it walked into a pool the old way)
                float face = mob.yBodyRot * Mth.DEG_TO_RAD;
                boolean alongX = Math.abs(Mth.sin(face)) >= Math.abs(Mth.cos(face));
                b = alongX ? new Node(a.x + dx, b.y, a.z) : new Node(a.x, b.y, a.z + dz);
                dx = alongX ? dx : 0;
                dz = alongX ? 0 : dz;
            }
            if (Math.abs(dx) + Math.abs(dz) != 1) {
                continue;
            }
            Edge edge = afloat ? outOf(b) : a.y == floor ? intoWater(b, floor) : null;
            if (afloat && edge == null) {
                if (dryStand(b)) {
                    return false;            // a bank it has no move for: the old hop
                }
                continue;
            }
            if (edge == null) {
                if (Math.abs(b.y - a.y) != 1 || a.y != floor) {
                    continue;
                }
                edge = b.y < a.y ? Edge.DOWN : Edge.UP;
            }
            int move = s.edges[edge.ordinal()];
            if (move < 0) {
                return false;
            }
            BlockMoves.Play p = at(a, b, dx, dz, edge, move);
            if (p != null) {
                mob.getEntityData().set(s.data, p.save());
                return true;
            }
            return false;
        }
        return afloat && (outPastRoute(path) || headedForLand() && outAhead());
    }

    /** Whether where it was last sent is dry land near by (within 10 blocks). */
    private boolean headedForLand() {
        BlockPos to = mob.getNavigation().getTargetPos();
        return to != null && to.distSqr(mob.blockPosition()) < 100.0 && mob.level().getFluidState(to).isEmpty()
                && mob.level().getFluidState(to.below()).isEmpty();
    }

    /**
     * Afloat, a bank straight ahead (the way it faces most, within 3 blocks) level with the water's top or a block over
     * it: out onto it (the otter's film, 2026-11-22: it swam up to the bank, its route ended at the water's edge, and it
     * stayed there with its chin on the bank).
     */
    private boolean outAhead() {
        int top = waterTop();
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        float yaw = mob.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        boolean alongX = Math.abs(fx) >= Math.abs(fz);
        int sign = (int) Math.signum(alongX ? fx : fz);
        int dx = alongX ? sign : 0, dz = alongX ? 0 : sign;
        int bx = Mth.floor(mob.getX()), bz = Mth.floor(mob.getZ());
        for (int k = 1; k <= 3; k++) {
            int cx = bx + dx * k, cz = bz + dz * k;
            for (int y = top; y <= top + 1; y++) {
                Node b = new Node(cx, y, cz);
                Edge edge = outOf(b);
                if (edge != null) {
                    int move = s.edges[edge.ordinal()];
                    BlockMoves.Play p = move < 0 ? null : at(new Node(cx - dx, y, cz - dz), b, dx, dz, edge, move);
                    if (p != null) {
                        mob.getEntityData().set(s.data, p.save());
                        return true;
                    }
                    return false;
                }
            }
            if (!mob.level().getFluidState(new BlockPos(cx, top - 1, cz)).is(FluidTags.WATER)) {
                return false;                       // something else ahead: no bank to climb
            }
        }
        return false;
    }

    /**
     * Afloat, a route that ends at the water's edge short of its goal (Minecraft's route maker cannot step up out of the
     * water onto a bank a block or more over it): the bank square past its last node, toward the goal (the monitor's lesson).
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
            Edge edge = outOf(b);
            if (edge != null) {
                int move = s.edges[edge.ordinal()];
                BlockMoves.Play p = move < 0 ? null : at(a, b, dx, dz, edge, move);
                if (p != null) {
                    mob.getEntityData().set(s.data, p.save());
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /** The kind of edge into the water at node b's column from a bank whose top is at {@code floor}; null if b is not water. */
    @Nullable
    private Edge intoWater(Node b, int floor) {
        for (int down = 1; down <= 3; down++) {
            BlockPos at = new BlockPos(b.x, floor - down, b.z);
            FluidState f = mob.level().getFluidState(at);
            if (f.is(FluidTags.WATER)) {
                if (!mob.level().getFluidState(at.above()).isEmpty() || f.getAmount() < 8) {
                    return null;             // a slope of flowing water, or water over its block: not a bank's edge
                }
                if (down == 1) {
                    boolean running = movedWithin(2) && stride > s.run * mob.getScale();
                    return (running || hurry) && s.edges[Edge.RUN_IN.ordinal()] >= 0 ? Edge.RUN_IN : Edge.IN;
                }
                return down == 2 ? Edge.DROP : Edge.DROP2;
            }
            if (!open(at)) {
                return null;
            }
        }
        return null;
    }

    /** Out of the water onto node b: a bank level with the water's block top, or a block over it; null else. */
    @Nullable
    private Edge outOf(Node b) {
        int top = waterTop();
        if (top == Integer.MIN_VALUE || !dryStand(b)) {
            return null;
        }
        return b.y == top ? Edge.OUT : b.y == top + 1 ? Edge.OUT_UP : null;
    }

    /** The top of the water's block it floats in (its upper face), or MIN_VALUE if not in still water. */
    private int waterTop() {
        BlockPos.MutableBlockPos p = BlockPos.containing(mob.getX(), mob.getY() + 0.3, mob.getZ()).mutable();
        if (!mob.level().getFluidState(p).is(FluidTags.WATER)) {
            return Integer.MIN_VALUE;
        }
        while (mob.level().getFluidState(p.above()).is(FluidTags.WATER)) {
            p.move(0, 1, 0);
        }
        return mob.level().getFluidState(p).getAmount() < 8 ? Integer.MIN_VALUE : p.getY() + 1;
    }

    /** Whether node b stands on a whole dry block with open air over it. */
    private boolean dryStand(Node b) {
        BlockPos at = new BlockPos(b.x, b.y, b.z);
        return dryOpen(at) && full(at.below());
    }

    /**
     * The move from node a to b, if it is at the move's start and there is room; else null. Its height is the top it
     * leaves (going down or in) or the lower level (up, or out: the water's block's top). Coming at the edge more than
     * {@link BlockMoves#SQUARE} degrees off square it first turns square on the spot.
     */
    @Nullable
    private BlockMoves.Play at(Node a, Node b, int dx, int dz, Edge edge, int move) {
        BlockMoves.Move m = s.moves.move(move);
        double scale = s.modelScale * mob.getScale(), back = m.aheadPx * scale / 16.0;
        int sign = dx + dz;
        double face = dx != 0 ? (dx > 0 ? a.x + 1 : a.x) : (dz > 0 ? a.z + 1 : a.z);
        double dist = (face - (dx != 0 ? mob.getX() : mob.getZ())) * sign;
        // not there yet, or come right up against the face (the old step); come too close (a slanted route, or swimming
        // right up to a bank) it is set back to the move's start, its drawing sliding over (the otter's tests and film,
        // 2026-11-22: it missed the climb out, bumped the bank and hopped out the old way, or stayed with its chin on it)
        if (dist > back + 0.1 || dist < Math.max(back - 1.2, 0.2)) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        int turn = BlockMoves.turnFor(mob.yBodyRot, yaw);
        if (turn < 0) {
            return null;
        }
        int level = m.startsAfloat ? waterTop() : Mth.floor(mob.getY() + 1.0E-3);
        if (m.startsAfloat) {
            // afloat at its resting depth, not diving or still sinking in
            double want = level - 1.0 / 9.0 - s.afloat * mob.getScale();
            if (Math.abs(mob.getY() - want) > 0.25) {
                return null;
            }
        }
        double side = dx != 0 ? mob.getZ() : mob.getX(), middle = (dx != 0 ? b.z : b.x) + 0.5;
        for (double across : new double[] {side, middle}) {
            if (Math.abs(across - side) <= 0.5 && room(edge, m, level, dx != 0, face, sign, across, scale)) {
                double along = face - back * sign;
                return s.moves.start(mob, move, dx != 0 ? along : across, m.startsAfloat ? level : mob.getY(),
                        dx != 0 ? across : along, yaw, turn);
            }
        }
        return null;
    }

    private boolean room(Edge edge, BlockMoves.Move m, int level, boolean alongX, double face, int sign, double across, double scale) {
        return switch (edge) {
            case UP -> roomUp(level, alongX, face, sign, across, scale);
            case DOWN -> roomDown(level, alongX, face, sign, across, scale);
            case OUT -> roomOut(level, alongX, face, sign, across, scale);
            case OUT_UP -> roomOut(level + 1, alongX, face, sign, across, scale);
            default -> roomIn(edge, m, level, alongX, face, sign, across, scale);
        };
    }

    /** A whole block under both feet for the two blocks past the face, open over it, and room for the body over the step and in front. */
    private boolean roomUp(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = s.feetOutPx * scale / 16.0;
        for (double side : new double[] {across - out, across + out}) {
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos step = cell(alongX, face + ahead * sign, side, y);
                if (!full(step) || !open(step.above())) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {-0.5, 0.5, 1.5}) {
            if (!open(cell(alongX, face + ahead * sign, across, y + 1))) {
                return false;
            }
        }
        return true;
    }

    /** The top it leaves whole, and a whole dry block a block lower under both feet for two blocks past the edge, open over it. */
    private boolean roomDown(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = s.feetOutPx * scale / 16.0;
        for (double side : new double[] {across - out, across + out}) {
            if (!full(cell(alongX, face - 0.5 * sign, side, y - 1))) {
                return false;
            }
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos low = cell(alongX, face + ahead * sign, side, y - 2);
                if (!full(low) || !dryOpen(low.above()) || !dryOpen(low.above(2))) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {0.5, 1.5}) {
            if (!dryOpen(cell(alongX, face + ahead * sign, across, y - 1)) || !open(cell(alongX, face + ahead * sign, across, y))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Into the water off the bank at {@code face} (its top at {@code y}): the bank whole and dry under both feet back to
     * where the move starts, and past the edge as far as the move goes, still water at the water's top for the edge (level
     * with the bank, or one or two blocks lower), deep enough for the lowest the move goes, with open air over it.
     */
    private boolean roomIn(Edge edge, BlockMoves.Move m, int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = s.feetOutPx * scale / 16.0, pxToBlocks = scale / 16.0;
        int drop = edge == Edge.DROP ? 1 : edge == Edge.DROP2 ? 2 : 0;
        int size = s.moves.nearest(mob.getScale());
        double back = m.aheadPx * pxToBlocks;
        double past = Math.max(1.5, m.reach(size) * pxToBlocks - back + 0.5);
        int bottom = Mth.floor(y + m.lowest(size) + 0.05);
        int top = y - 1 - drop;
        for (double side : new double[] {across - out, across, across + out}) {
            for (double behind = 0.5; behind < back + 0.5; behind += 1.0) {
                BlockPos bank = cell(alongX, face - behind * sign, side, y - 1);
                if (!full(bank) || !dryOpen(bank.above())) {
                    return false;
                }
            }
            for (double ahead = 0.5; ahead <= past; ahead += 1.0) {
                BlockPos water = cell(alongX, face + ahead * sign, side, top);
                for (int yy = Math.min(bottom, top); yy <= top; yy++) {
                    BlockPos w = new BlockPos(water.getX(), yy, water.getZ());
                    FluidState f = mob.level().getFluidState(w);
                    if (!f.is(FluidTags.WATER) || f.getAmount() < 8 || !open(w)) {
                        return false;
                    }
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

    /** Out onto the bank at {@code face} (its top at {@code y}): a whole dry block under both feet for two blocks past the face, open over it and over the water it rises from. */
    private boolean roomOut(int y, boolean alongX, double face, int sign, double across, double scale) {
        double out = s.feetOutPx * scale / 16.0;
        for (double side : new double[] {across - out, across + out}) {
            for (double ahead : new double[] {0.5, 1.5}) {
                BlockPos top = cell(alongX, face + ahead * sign, side, y - 1);
                if (!full(top) || !dryOpen(top.above())) {
                    return false;
                }
            }
        }
        for (double ahead : new double[] {-0.5, 0.5, 1.5}) {
            if (!open(cell(alongX, face + ahead * sign, across, y))) {
                return false;
            }
        }
        for (double ahead : new double[] {-1.5, -0.5}) {
            if (!open(cell(alongX, face + ahead * sign, across, y - 1))) {
                return false;
            }
        }
        return true;
    }

    private boolean full(BlockPos pos) {
        return mob.level().getBlockState(pos).isCollisionShapeFullBlock(mob.level(), pos);
    }

    private boolean open(BlockPos pos) {
        return mob.level().getBlockState(pos).getCollisionShape(mob.level(), pos).isEmpty();
    }

    private boolean dryOpen(BlockPos pos) {
        return open(pos) && mob.level().getFluidState(pos).isEmpty();
    }

    private static BlockPos cell(boolean alongX, double along, double across, int y) {
        return alongX ? new BlockPos(Mth.floor(along), y, Mth.floor(across)) : new BlockPos(Mth.floor(across), y, Mth.floor(along));
    }

    // --- drawing (client) -------------------------------------------------------------------------------------------

    /**
     * Drawing only: how far to move the model's root bone (model pixels, x already turned the way GeckoLib draws it) so
     * it is drawn on its move's path (where the move started and how far into the clip it is), not at its box (which
     * steps up all at once at the face), sliding over ({@link #HAND_OVER}) where the drawing hands over between the two.
     *
     * @param x, y, z where its box is drawn this frame
     */
    public Vector3d drawnShift(double x, double y, double z, float yaw, float pt) {
        double scale = s.modelScale * mob.getScale();
        BlockMoves.Play p = play();
        double wx = x, wy = y, wz = z;
        if (p != null) {
            Vec3 at = s.moves.drawnAt(p, s.moves.drawnTicks(p, drawClock, mob.level().getGameTime(), pt), scale / 16.0);
            wx = at.x;
            wy = at.y;
            wz = at.z;
        }
        long now = DrawClock.nanos(mob, pt);              // game time (the Motion Studio, phase 2, step 4)
        boolean on = p != null;
        if (drawnNanos == 0) {
            shiftX = shiftY = shiftZ = 0;
        } else if (on != drawnOn) {
            shiftX = drawnX - wx;
            shiftY = drawnY - wy;
            shiftZ = drawnZ - wz;
        }
        double dt = drawnNanos == 0 ? 1.0 : (now - drawnNanos) / 1.0E9;
        double keep = dt <= 0 ? 1.0 : dt > 0.5 ? 0.0 : Math.exp(-dt / HAND_OVER);
        shiftX *= keep;
        shiftY *= keep;
        shiftZ *= keep;
        drawnOn = on;
        drawnNanos = now;
        drawnX = wx + shiftX;
        drawnY = wy + shiftY;
        drawnZ = wz + shiftZ;
        Vector3d m = new Vector3d(drawnX - x, drawnY - y, drawnZ - z).rotateY(-Math.toRadians(180.0 - yaw)).div(scale);
        return new Vector3d(-m.x * 16.0, m.y * 16.0, m.z * 16.0);
    }

    /** Drawing only: far off it is not drawn on its path (the next time it is drawn there is no hand over). */
    public void notDrawn() {
        drawnNanos = 0;
    }

    /**
     * Drawing only (client tick): going into the water a splash where its front meets the water (at the move's wet tick
     * off a level bank; off a high bank as its path goes under), coming out drops running off it for a while.
     *
     * @param ahead how far ahead of its middle its front meets the water (blocks at size 1)
     * @param length its body's length (blocks at size 1), for where the drops fall
     */
    public void wetLooks(double ahead, double length) {
        BlockMoves.Play p = play();
        double size = mob.getScale();
        if (p != null) {
            BlockMoves.Move m = s.moves.move(p.move());
            double t = s.moves.ticks(p, mob.level().getGameTime());
            Edge edge = edge(p);
            if (m.endsAfloat) {
                double pathY = p.y() + m.rise(p.size(), t);
                double x = p.x() + p.dirX() * (m.along(p.size(), t) * s.modelScale * size / 16.0 + ahead * size);
                double z = p.z() + p.dirZ() * (m.along(p.size(), t) * s.modelScale * size / 16.0 + ahead * size);
                double surface = surfaceUnder(x, p.y() + 0.5, z);
                boolean level = edge == Edge.IN || edge == Edge.RUN_IN;
                boolean now = level ? t >= m.wetTick && t < m.wetTick + 3
                        : !Double.isNaN(lastPathY) && !Double.isNaN(surface) && lastPathY >= surface - 0.3 && pathY < surface - 0.3;
                lastPathY = pathY;
                if (now && !Double.isNaN(surface)) {
                    int n = edge == Edge.IN ? 4 : 10;
                    for (int i = 0; i < n; i++) {
                        mob.level().addParticle(ParticleTypes.SPLASH, x + (mob.getRandom().nextDouble() - 0.5) * 0.9 * size, surface + 0.05,
                                z + (mob.getRandom().nextDouble() - 0.5) * 0.9 * size, 0.0, 0.12, 0.0);
                    }
                    if (!level || t < m.wetTick + 1) {
                        mob.level().playLocalSound(x, surface, z, SoundEvents.GENERIC_SPLASH, mob.getSoundSource(),
                                edge == Edge.IN ? 0.25F : 0.55F, 1.1F + (mob.getRandom().nextFloat() - 0.5F) * 0.3F, false);
                    }
                }
            } else if (m.startsAfloat && t >= m.wetTick) {
                wetUntil = mob.tickCount + 50;
            }
        } else {
            lastPathY = Double.NaN;
        }
        if (mob.tickCount < wetUntil && !mob.isInWater() && mob.getRandom().nextInt(3) > 0) {
            float yaw = mob.yBodyRot * Mth.DEG_TO_RAD;
            double along = (mob.getRandom().nextDouble() - 0.5) * length * size;
            double y = (p != null ? drawnY : mob.getY()) + (0.15 + 0.2 * mob.getRandom().nextDouble()) * size;
            double x = (p != null ? drawnX : mob.getX()) - Mth.sin(yaw) * along + (mob.getRandom().nextDouble() - 0.5) * 0.3 * size;
            double z = (p != null ? drawnZ : mob.getZ()) + Mth.cos(yaw) * along + (mob.getRandom().nextDouble() - 0.5) * 0.3 * size;
            mob.level().addParticle(ParticleTypes.DRIPPING_WATER, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** Drawing only: stops the drops (it shook itself dry). */
    public void dry() {
        wetUntil = 0;
    }

    /** The water's surface in the column at (x, z) from height y down a few blocks; NaN if no water. */
    private double surfaceUnder(double x, double y, double z) {
        BlockPos.MutableBlockPos p = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 5; i++, p.move(0, -1, 0)) {
            FluidState f = mob.level().getFluidState(p);
            if (f.is(FluidTags.WATER)) {
                return p.getY() + f.getHeight(mob.level(), p);
            }
        }
        return Double.NaN;
    }
}
