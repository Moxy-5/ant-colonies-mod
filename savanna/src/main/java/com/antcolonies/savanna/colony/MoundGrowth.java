package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.TermiteSpireBlock;
import com.antcolonies.savanna.colony.MoundPlan.Part;
import com.antcolonies.savanna.colony.MoundPlan.Shape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * How termites build their cathedral mound from the inside (tunnel plan step 4, the user's design), as pure geometry:
 * the rules every termite follows, a pretend mound to try them on, and the tunnel network every stage needs.
 *
 * <p><b>The rules</b> (the termites in the game follow the same ones, see {@link MoundReach}):
 * <ul>
 * <li>A termite works from where it can get to from the nest: the mound's shaft (and the nest under it), and the
 * channels of the tunnel blocks, hollow spire pieces and chimney mouths joined to them.</li>
 * <li>From the shaft it reaches the 26 cells round it. From a channel it reaches the block right beside it, through a
 * window it bites in the channel's side and plugs again (a spire piece only has one on top, a mouth none); or a block
 * diagonally beside it, poking out of the window into an empty cell outside the plan (where nothing will ever be built
 * during the stage, so the way stays open).</li>
 * <li>Soil goes on something solid (set soil, a tunnel block, the ground), or is built out from soil beside it like
 * an arch. A ramp needs solid soil under it; a spire piece or a mouth something under it.</li>
 * <li>A tunnel is carved into soil from a channel or the shaft right beside it, so it joins up at once.</li>
 * <li>As the mound grows its pieces change: a piece that should now be another one (a thicker spire piece, a ramp
 * facing another way, a mouth that is now a spire piece) is reshaped; a piece where soil or a tunnel goes is filled up
 * into it where it stands (the user's request: never cleared first, so nothing above it is left hanging and the
 * channel up a spire is never cut while it is reshaped into a tunnel); soil
 * where the shaft must now be hollow is dug out. A piece where nothing goes any more (an old spire top the spire has
 * grown away from) crumbles by itself when the mound reaches its next stage ({@link #crumblesAway}).</li>
 * </ul>
 * Building never takes a way in away from anything (a block is only ever worked on through the window right beside it,
 * past a cell nothing is built in, or from the open shaft), so the order the termites pick does not matter (the tests
 * try many).
 *
 * <p><b>The tunnels</b> ({@link #grow}): the mound is grown in a pretend world stage by stage, the way the termites
 * would, and a tunnel is carved only where they would otherwise get stuck: the cheapest path of soil (through the inside
 * rather than the outer skin) from where they can already go to a spot from which the stuck block can be reached. After
 * every stage the same is done for every block on the outside of the mound that could not be reached from inside if it
 * were broken: so every outside block is next to a channel (the user's rule), and the termites can always mend it. A
 * tunnel, once carved, stays. The plan of a stage marks every soil cell that is a tunnel by then as {@link Part#TUNNEL}.
 */
public final class MoundGrowth {
    /** What stands in a cell of the pretend mound (nothing = empty). */
    enum Kind {
        SOIL, TUNNEL, RAMP, SPIRE, TIP, MOUTH;

        boolean isPiece() {
            return this == RAMP || this == SPIRE || this == TIP || this == MOUTH;
        }
    }

    /** The kinds of work on a cell of the mound. */
    public enum Job {
        /** A block of soil (layer by layer in the game). */
        SOIL,
        /** Carving soil into a tunnel block. */
        CARVE,
        /** A ramp, a spire piece or a chimney mouth. */
        PIECE,
        /** Reshaping a piece into the one the plan has there now. */
        RESHAPE,
        /** Clearing a piece away, or digging out soil where the shaft must now be hollow. */
        CLEAR
    }

    /** How deep the nest's shaft goes under the entrance in the pretend world (the termites come up it). */
    private static final int NEST_DEPTH = 3;
    /** Carving through the mound's outer skin costs this much more than through its inside (tunnels keep under the skin). */
    private static final int SKIN_COST = 2;

    private MoundGrowth() {
    }

    /** A cell the termites could not build (or carve, reshape, clear), and why. */
    public record Stuck(BlockPos cell, @Nullable Part part, String why) {
        @Override
        public String toString() {
            return cell.toShortString() + " (" + part + ": " + why + ")";
        }
    }

    /**
     * Every stage of the mound, with its tunnels: index {@code h} is stage {@code h} (index 0 is unused), from the shape
     * of each stage ({@code stage}). If the termites would get stuck somewhere even with tunnels (the shape itself
     * cannot be built that way), that is logged and the stage is kept as far as it goes (the termites in the game then
     * fall back on a longer reach); the tests make sure it never happens ({@link #problems}).
     */
    public static List<Shape> grow(int stages, java.util.function.IntFunction<Shape> stage) {
        PretendMound world = new PretendMound();
        List<Shape> grown = new ArrayList<>();
        grown.add(null);
        for (int h = 1; h <= stages; h++) {
            Shape plan = stage.apply(h);
            List<Stuck> stuck = world.build(plan, true, null);
            if (!stuck.isEmpty()) {
                SavannaMod.LOGGER.error("[mound] stage {} of a mound cannot be built from inside: {}", h, stuck);
            }
            world.tunnelToTheOutside(plan);
            grown.add(withTunnels(plan, world));
        }
        return grown;
    }

    /** The same growing, returning what got stuck at each stage instead of the stages (for tests): empty if nothing. */
    public static List<String> problems(int stages, java.util.function.IntFunction<Shape> stage) {
        PretendMound world = new PretendMound();
        List<String> problems = new ArrayList<>();
        for (int h = 1; h <= stages; h++) {
            Shape plan = stage.apply(h);
            List<Stuck> stuck = world.build(plan, true, null);
            if (!stuck.isEmpty()) {
                problems.add("stage " + h + ": " + stuck);
            }
            world.tunnelToTheOutside(plan);
            List<BlockPos> outside = world.unreachableOutside(plan);
            if (!outside.isEmpty()) {
                problems.add("stage " + h + ", outside blocks no termite inside could mend: " + outside);
            }
        }
        return problems;
    }

    /** For working out a stuck cell: the plan, what stands and what can be got to round it, row by row (for tests). */
    public static String around(int stages, java.util.function.IntFunction<Shape> stage, int atStage, BlockPos cell) {
        PretendMound world = new PretendMound();
        for (int h = 1; h <= atStage; h++) {
            Shape plan = stage.apply(h);
            world.build(plan, true, null);
            if (h < atStage) {
                world.tunnelToTheOutside(plan);
            }
        }
        Set<BlockPos> reachable = world.reachable();
        StringBuilder text = new StringBuilder("around " + cell.toShortString() + " at stage " + atStage + " (plan/world/reachable):");
        for (int dy = 1; dy >= -1; dy--) {
            text.append(System.lineSeparator()).append(" y+").append(dy).append(':');
            for (int dz = -2; dz <= 2; dz++) {
                text.append(System.lineSeparator()).append("   ");
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos pos = cell.offset(dx, dy, dz);
                    Part part = world.parts.get(pos);
                    Kind kind = world.kind(pos);
                    text.append(String.format("%-7s/%-6s%s ", part == null ? "-" : part.name(), kind == null ? (world.isGround(pos) ? "ground" : "-") : kind.name(),
                            reachable.contains(pos) ? "*" : " "));
                }
            }
        }
        return text.toString();
    }

    /** The plan with every soil cell that is a tunnel in the pretend world marked as one. */
    private static Shape withTunnels(Shape plan, PretendMound world) {
        Map<BlockPos, Part> parts = new LinkedHashMap<>();
        plan.parts().forEach((pos, part) -> parts.put(pos, part == Part.WALL && world.kind(pos) == Kind.TUNNEL ? Part.TUNNEL : part));
        return new Shape(parts, plan.thickness(), plan.facing(), plan.corner(), plan.age());
    }

    /**
     * Builds the plans one after the other in a fresh pretend world by the rules alone, never carving a tunnel a plan
     * does not have: the cells it could not build (empty if it built them all). {@code order}: the jobs of every pass
     * in a random order (else from the bottom up). For tests.
     */
    public static List<Stuck> check(List<Shape> plans, @Nullable Random order) {
        PretendMound world = new PretendMound();
        List<Stuck> stuck = List.of();
        for (Shape plan : plans) {
            stuck = world.build(plan, false, order);
            if (!stuck.isEmpty()) {
                return stuck;
            }
        }
        return stuck;
    }

    /**
     * Like {@link #check}, but with surprises, as the game moves on from a stage (see {@code Mound.getStage}): at every
     * stage about {@code share} of the cells cannot be worked on at all (a player stands there, the ground is odd), so
     * the stage is built only as far as it goes without them and the next one is taken up; the last plan is built to the
     * end with no surprises. For tests.
     */
    public static List<Stuck> checkWithSurprises(List<Shape> plans, Random random, double share) {
        PretendMound world = new PretendMound();
        for (int i = 0; i < plans.size() - 1; i++) {
            Set<BlockPos> blocked = new HashSet<>();
            List<BlockPos> cells = new ArrayList<>(plans.get(i).parts().keySet());
            cells.sort(Comparator.naturalOrder());
            for (BlockPos cell : cells) {
                if (random.nextDouble() < share) {
                    blocked.add(cell);
                }
            }
            world.blocked = blocked;
            world.build(plans.get(i), false, null);
        }
        world.blocked = Set.of();
        return world.build(plans.get(plans.size() - 1), false, random);
    }

    /**
     * Outside blocks of the last of the plans, built one after the other, that a termite could not get to from inside
     * (the shaft or a channel) if the block were broken: for tests (there must be none).
     */
    public static List<BlockPos> unreachableOutside(List<Shape> plans) {
        PretendMound world = new PretendMound();
        for (Shape plan : plans) {
            world.build(plan, false, null);
        }
        return world.unreachableOutside(plans.get(plans.size() - 1));
    }

    // ---- the rules, shared with the game (MoundReach) ----

    /**
     * Whether a piece (a ramp, a spire piece, a mouth) left from an earlier stage crumbles away when the mound reaches a
     * stage that has {@code now} in its cell: when the plan has nothing there any more. (My pick: the termites could not
     * reach such a piece from inside, since it stands outside the new shape, and it would stay there for ever.)
     */
    public static boolean crumblesAway(@Nullable Part now) {
        return now == null;
    }

    /** Whether a channel block of this kind can open toward {@code side} (see the channel blocks). */
    static boolean canJoin(Kind kind, Direction side) {
        return switch (kind) {
            case TUNNEL -> true;
            case SPIRE -> side.getAxis() == Direction.Axis.Y;
            case MOUTH -> side == Direction.DOWN;
            default -> false;
        };
    }

    /** Whether a termite in a channel of this kind can bite a window toward {@code side} (a tunnel: any; a spire piece: up). */
    static boolean canOpenWindow(Kind kind, Direction side) {
        return kind == Kind.TUNNEL || kind == Kind.SPIRE && side == Direction.UP;
    }

    /** The side of {@code from} facing {@code to}, if they are side by side; else null. */
    @Nullable
    static Direction sideToward(BlockPos from, BlockPos to) {
        BlockPos d = to.subtract(from);
        if (Math.abs(d.getX()) + Math.abs(d.getY()) + Math.abs(d.getZ()) != 1) {
            return null;
        }
        return Direction.getNearest(d.getX(), d.getY(), d.getZ());
    }

    /**
     * For a block diagonally beside a channel (two steps, not a corner): the side of the channel to bite the window in,
     * so the termite pokes out into a cell between them that {@code staysEmpty} (empty, and outside the plan); or null.
     */
    @Nullable
    static Direction windowPast(BlockPos from, BlockPos target, Kind kind, java.util.function.Predicate<BlockPos> staysEmpty) {
        BlockPos d = target.subtract(from);
        if (Math.abs(d.getX()) + Math.abs(d.getY()) + Math.abs(d.getZ()) != 2) {
            return null;
        }
        for (Direction side : Direction.values()) {
            BlockPos window = from.relative(side);
            if (sideToward(window, target) != null && canOpenWindow(kind, side) && staysEmpty.test(window)) {
                return side;
            }
        }
        return null;
    }

    /** Whether two cells are within the 26 cells round each other. */
    static boolean isNear(BlockPos a, BlockPos b) {
        return !a.equals(b) && Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    // ---- the pretend mound ----

    /**
     * A mound being built, without the game: what stands in each cell (the ground is solid under y = 0, but for the
     * nest's shaft under the entrance), and for a piece which one (a spire piece's thickness, a ramp's high side).
     */
    static final class PretendMound {
        private final Map<BlockPos, Kind> cells = new HashMap<>();
        private final Map<BlockPos, Integer> variants = new HashMap<>();
        /** The plan being built now. */
        private Map<BlockPos, Part> parts = Map.of();
        private Shape plan;
        /** Cells that cannot be worked on now (surprises, for tests). */
        private Set<BlockPos> blocked = Set.of();

        Kind kind(BlockPos pos) {
            return cells.get(pos);
        }

        boolean isGround(BlockPos pos) {
            return pos.getY() < 0 && !(pos.getX() == 0 && pos.getZ() == 0 && pos.getY() >= -NEST_DEPTH);
        }

        boolean isEmpty(BlockPos pos) {
            return !cells.containsKey(pos) && !isGround(pos);
        }

        /** Something solid with a flat top to build on: soil, a tunnel block, the ground. */
        boolean isSturdy(BlockPos pos) {
            Kind kind = cells.get(pos);
            return kind == Kind.SOIL || kind == Kind.TUNNEL || isGround(pos);
        }

        boolean isSoil(BlockPos pos) {
            Kind kind = cells.get(pos);
            return kind == Kind.SOIL || kind == Kind.TUNNEL;
        }

        boolean isChannel(BlockPos pos) {
            Kind kind = cells.get(pos);
            return kind == Kind.TUNNEL || kind == Kind.SPIRE || kind == Kind.MOUTH;
        }

        /** Hollow inside the mound: the shaft (an empty cell the plan keeps open) or the nest's shaft under it. */
        boolean isShaft(BlockPos pos) {
            return isEmpty(pos) && (parts.get(pos) == Part.OPEN || pos.getY() < 0);
        }

        /** Whether two channel cells side by side are joined (both can open toward each other). */
        boolean joined(BlockPos a, BlockPos b) {
            Direction side = sideToward(a, b);
            return side != null && isChannel(a) && isChannel(b) && canJoin(cells.get(a), side) && canJoin(cells.get(b), side.getOpposite());
        }

        /**
         * Every cell a termite can get to from the nest now: the entrance and the nest's shaft, the mound's shaft cells
         * it can hold on in (something built round them), and the channels joined to them (a tunnel block opens into the
         * shaft beside it).
         */
        Set<BlockPos> reachable() {
            Set<BlockPos> seen = new HashSet<>();
            List<BlockPos> queue = new ArrayList<>();
            for (int y = 0; y >= -NEST_DEPTH; y--) {
                BlockPos nest = new BlockPos(0, y, 0);
                if (isEmpty(nest) && seen.add(nest)) {
                    queue.add(nest);
                }
            }
            for (int i = 0; i < queue.size(); i++) {
                BlockPos at = queue.get(i);
                boolean inChannel = isChannel(at);
                for (Direction side : Direction.values()) {
                    BlockPos next = at.relative(side);
                    if (seen.contains(next)) {
                        continue;
                    }
                    boolean ok;
                    if (inChannel) {
                        ok = joined(at, next) || cells.get(at) == Kind.TUNNEL && isShaft(next) && isHoldable(next);
                    } else {
                        ok = isShaft(next) && isHoldable(next) || cells.get(next) == Kind.TUNNEL;
                    }
                    if (ok) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
            return seen;
        }

        /** An empty cell with something built (or the ground) beside, over or under it to hold on to. */
        private boolean isHoldable(BlockPos pos) {
            if (pos.getY() <= 0 && pos.getX() == 0 && pos.getZ() == 0) {
                return true; // the entrance and the nest's shaft
            }
            for (Direction side : Direction.values()) {
                BlockPos beside = pos.relative(side);
                if (cells.containsKey(beside) || isGround(beside)) {
                    return true;
                }
            }
            return false;
        }

        /** The piece the plan wants in a cell: its kind and which one (spire thickness, ramp high side). */
        private Kind plannedKind(BlockPos pos, Part part) {
            return switch (part) {
                case RAMP -> Kind.RAMP;
                case MOUTH -> Kind.MOUTH;
                default -> plan.thickness().getOrDefault(pos, 0) >= TermiteSpireBlock.TIP ? Kind.TIP : Kind.SPIRE;
            };
        }

        private int plannedVariant(BlockPos pos, Part part) {
            return switch (part) {
                case RAMP -> plan.facing().containsKey(pos) ? plan.facing().get(pos).get3DDataValue() : 0;
                case SPIRE -> plan.thickness().getOrDefault(pos, 0);
                default -> 0;
            };
        }

        /** What has to be done in a cell now, or null if nothing (done, or something it cannot change stands there). */
        @Nullable
        Job jobAt(BlockPos pos) {
            Part part = parts.get(pos);
            Kind kind = cells.get(pos);
            if (part == null) {
                return null; // left from an earlier stage: a piece crumbles when the stage begins, soil stays
            }
            return switch (part) {
                case OPEN -> kind != null ? Job.CLEAR : null;
                case WALL -> isEmpty(pos) ? Job.SOIL : kind != null && kind.isPiece() ? Job.RESHAPE : null;
                case TUNNEL -> isEmpty(pos) ? Job.SOIL : kind == Kind.SOIL ? Job.CARVE : kind != null && kind.isPiece() ? Job.RESHAPE : null;
                case RAMP, SPIRE, MOUTH -> isEmpty(pos) ? Job.PIECE
                        : kind != null && kind.isPiece() && (kind != plannedKind(pos, part) || variants.getOrDefault(pos, 0) != plannedVariant(pos, part))
                        ? Job.RESHAPE : null;
            };
        }

        private static boolean isSoilPart(@Nullable Part part) {
            return part == Part.WALL || part == Part.TUNNEL;
        }

        /** Whether a job has what it needs under it (soil, a piece): see the rules. */
        boolean hasSupport(BlockPos pos, Job job) {
            if (job == Job.SOIL) {
                if (isSturdy(pos.below())) {
                    return true;
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (isSoil(pos.relative(side))) {
                        return true;
                    }
                }
                return false;
            }
            if (job == Job.RESHAPE && isSoilPart(parts.get(pos))) {
                return true; // a piece filled up into soil or a tunnel block where it stands: it already stands
            }
            if (job == Job.PIECE || job == Job.RESHAPE) {
                return parts.get(pos) == Part.RAMP ? isSturdy(pos.below()) : !isEmpty(pos.below());
            }
            return true;
        }

        /** Whether a termite at {@code from} (a cell it can get to) can do the job on {@code target} (see the rules). */
        boolean canWorkFrom(BlockPos from, BlockPos target, Job job) {
            if (!isNear(from, target)) {
                return false;
            }
            Direction side = sideToward(from, target);
            if (job == Job.CARVE) {
                return side != null && (!isChannel(from) || canJoin(cells.get(from), side));
            }
            if (!isChannel(from)) {
                return true; // from the shaft: the 26 cells round it
            }
            if (job == Job.CLEAR && isSoil(target)) {
                return side != null && cells.get(from) == Kind.TUNNEL; // digging out soil into the shaft from a tunnel beside it
            }
            if (side != null) {
                return canOpenWindow(cells.get(from), side);
            }
            return windowPast(from, target, cells.get(from), pos -> isEmpty(pos) && !parts.containsKey(pos)) != null;
        }

        boolean canDo(BlockPos pos, Job job, Set<BlockPos> reachable) {
            if (blocked.contains(pos) || !hasSupport(pos, job)) {
                return false;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos from = pos.offset(dx, dy, dz);
                        if (reachable.contains(from) && canWorkFrom(from, pos, job)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        void apply(BlockPos pos, Job job) {
            switch (job) {
                case CLEAR -> {
                    cells.remove(pos);
                    variants.remove(pos);
                }
                case SOIL -> cells.put(pos, Kind.SOIL);
                case CARVE -> cells.put(pos, Kind.TUNNEL);
                case PIECE, RESHAPE -> {
                    Part part = parts.get(pos);
                    if (part == Part.WALL || part == Part.TUNNEL) {
                        cells.put(pos, part == Part.WALL ? Kind.SOIL : Kind.TUNNEL); // a piece filled up where it stands
                        variants.remove(pos);
                        return;
                    }
                    cells.put(pos, plannedKind(pos, part));
                    variants.put(pos, plannedVariant(pos, part));
                }
            }
        }

        private void take(Shape plan) {
            this.plan = plan;
            this.parts = plan.parts();
        }

        /** A new stage begins: the pieces of earlier stages where the plan has nothing now crumble away. */
        private void begin(Shape plan) {
            take(plan);
            cells.entrySet().removeIf(entry -> entry.getValue().isPiece() && crumblesAway(parts.get(entry.getKey())));
            variants.keySet().retainAll(cells.keySet());
        }

        /** Every cell with work to see to: the plan's, and pieces left from earlier stages; from the bottom up. */
        private List<BlockPos> jobCells() {
            Set<BlockPos> all = new HashSet<>(parts.keySet());
            cells.forEach((pos, kind) -> {
                if (kind.isPiece()) {
                    all.add(pos);
                }
            });
            List<BlockPos> inOrder = new ArrayList<>(all);
            inOrder.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparingInt(pos -> pos.getX() * pos.getX() + pos.getZ() * pos.getZ())
                    .thenComparing(Comparator.naturalOrder()));
            return inOrder;
        }

        /**
         * Builds the plan as far as the rules allow, in passes (every job that can be done, then again). When nothing
         * more can be done and {@code carve} is on, carves the cheapest tunnel that lets the termites reach the lowest
         * job they are stuck on, and goes on. {@code order}: the jobs of a pass in a random order instead of from the
         * bottom up (for tests). Returns the jobs left that could not be done (empty when it is all built).
         */
        List<Stuck> build(Shape plan, boolean carve, @Nullable Random order) {
            begin(plan);
            while (true) {
                List<BlockPos> pass = jobCells();
                if (order != null) {
                    java.util.Collections.shuffle(pass, order);
                }
                boolean progress = false;
                Set<BlockPos> reachable = reachable();
                for (BlockPos pos : pass) {
                    Job job = jobAt(pos);
                    if (job != null && canDo(pos, job, reachable)) {
                        apply(pos, job);
                        progress = true;
                        if (job == Job.CARVE || job == Job.CLEAR || job == Job.RESHAPE) {
                            reachable = reachable();
                        }
                    }
                }
                if (progress) {
                    continue;
                }
                List<Stuck> left = new ArrayList<>();
                for (BlockPos pos : jobCells()) {
                    Job job = jobAt(pos);
                    if (job != null) {
                        left.add(new Stuck(pos, parts.get(pos), hasSupport(pos, job) ? "no termite can get to it to " + job.name().toLowerCase()
                                : "nothing to " + job.name().toLowerCase() + " it on"));
                    }
                }
                if (left.isEmpty() || !carve || !carveToward(left, reachable)) {
                    return left;
                }
            }
        }

        /** Carves toward the lowest stuck job that has what it needs under it. False if there is none it can carve to. */
        private boolean carveToward(List<Stuck> left, Set<BlockPos> reachable) {
            for (Stuck stuck : left) {
                Job job = jobAt(stuck.cell());
                if (job != null && hasSupport(stuck.cell(), job) && carveTo(stuck.cell(), job, reachable)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Finds the cheapest chain of soil to carve (soil the plan keeps as soil; carving through the outer skin costs
         * more; channels already there cost nothing) from the reachable cells to one from which the job on
         * {@code target} can be done, and carves it. False if there is none.
         */
        boolean carveTo(BlockPos target, Job job, Set<BlockPos> reachable) {
            Map<BlockPos, Integer> cost = new HashMap<>();
            Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
            PriorityQueue<BlockPos> open = new PriorityQueue<>(Comparator.comparingInt((BlockPos pos) -> cost.get(pos))
                    .thenComparingInt(pos -> pos.distManhattan(target)).thenComparing(Comparator.naturalOrder()));
            List<BlockPos> starts = new ArrayList<>(reachable);
            starts.sort(Comparator.naturalOrder());
            for (BlockPos start : starts) {
                cost.put(start, 0);
                open.add(start);
            }
            Set<BlockPos> done = new HashSet<>();
            while (!open.isEmpty()) {
                BlockPos at = open.poll();
                if (!done.add(at)) {
                    continue;
                }
                if (!reachable.contains(at) && wouldWorkFrom(at, target, job)) {
                    for (BlockPos pos = at; pos != null && !reachable.contains(pos); pos = cameFrom.get(pos)) {
                        if (cells.get(pos) == Kind.SOIL) {
                            cells.put(pos, Kind.TUNNEL);
                        }
                    }
                    return true;
                }
                for (Direction side : Direction.values()) {
                    BlockPos next = at.relative(side);
                    if (done.contains(next) || reachable.contains(next) || next.equals(target)) {
                        continue;
                    }
                    int step;
                    if (cells.get(next) == Kind.SOIL && parts.get(next) == Part.WALL && joinsOnward(at, side)) {
                        step = 1 + (isSkin(next) ? SKIN_COST : 0);
                    } else if (isChannel(next) && (joined(at, next) || cells.get(at) == Kind.SOIL && canJoin(cells.get(next), side.getOpposite()))) {
                        step = 0;
                    } else {
                        continue;
                    }
                    int c = cost.get(at) + step;
                    Integer known = cost.get(next);
                    if (known == null || c < known) {
                        cost.put(next, c);
                        cameFrom.put(next, at);
                        open.add(next);
                    }
                }
            }
            return false;
        }

        /** Whether a tunnel carved on the {@code side} of {@code at} would join what is at {@code at} (soil on the chain: yes). */
        private boolean joinsOnward(BlockPos at, Direction side) {
            return cells.get(at) == Kind.SOIL || !isChannel(at) || canJoin(cells.get(at), side);
        }

        /** Whether a cell touches the open air outside the mound (not the shaft). */
        private boolean isSkin(BlockPos pos) {
            for (Direction side : Direction.values()) {
                BlockPos beside = pos.relative(side);
                if (isEmpty(beside) && !isShaft(beside) && beside.getY() >= 0) {
                    return true;
                }
            }
            return false;
        }

        /** Whether a termite at {@code at}, once it is a tunnel joined up (or the channel there now), could do the job. */
        private boolean wouldWorkFrom(BlockPos at, BlockPos target, Job job) {
            Kind was = cells.get(at);
            if (was == Kind.SOIL) {
                cells.put(at, Kind.TUNNEL);
            }
            boolean can = canWorkFrom(at, target, job);
            cells.put(at, was);
            return can;
        }

        /**
         * After a stage is built: carves tunnels until every block on the outside of the mound could be reached from
         * inside if it were broken (the user's rule: every outside block is next to a channel; and so the termites can
         * always mend it).
         */
        void tunnelToTheOutside(Shape plan) {
            take(plan);
            for (int round = 0; round < 2000; round++) {
                List<BlockPos> missing = unreachableOutside(plan);
                if (missing.isEmpty()) {
                    return;
                }
                Set<BlockPos> reachable = reachable();
                boolean carved = false;
                for (BlockPos pos : missing) {
                    Kind kind = cells.remove(pos); // as if it were broken
                    Integer variant = variants.remove(pos);
                    carved = carveTo(pos, jobFor(parts.get(pos)), reachable);
                    cells.put(pos, kind);
                    if (variant != null) {
                        variants.put(pos, variant);
                    }
                    if (carved) {
                        break;
                    }
                }
                if (!carved) {
                    return; // nothing more can be done for the rest (left for the tests to report)
                }
            }
        }

        private static Job jobFor(Part part) {
            return part == Part.RAMP || part == Part.SPIRE || part == Part.MOUTH ? Job.PIECE : Job.SOIL;
        }

        /** Outside blocks of the plan (beside open air outside) a termite inside could not get to if they were broken. */
        List<BlockPos> unreachableOutside(Shape plan) {
            take(plan);
            Set<BlockPos> reachable = reachable();
            List<BlockPos> missing = new ArrayList<>();
            for (BlockPos pos : jobCells()) {
                Part part = parts.get(pos);
                if (part == null || part == Part.OPEN || isEmpty(pos) || !isSkin(pos)) {
                    continue;
                }
                Kind kind = cells.remove(pos); // as if it were broken
                Job job = jobFor(part);
                boolean can = false;
                for (int dx = -1; dx <= 1 && !can; dx++) {
                    for (int dy = -1; dy <= 1 && !can; dy++) {
                        for (int dz = -1; dz <= 1 && !can; dz++) {
                            BlockPos from = pos.offset(dx, dy, dz);
                            can = reachable.contains(from) && canWorkFrom(from, pos, job);
                        }
                    }
                }
                cells.put(pos, kind);
                if (!can) {
                    missing.add(pos);
                }
            }
            return missing;
        }
    }
}
