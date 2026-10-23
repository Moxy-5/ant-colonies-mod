package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.ColonyQuestions;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.GroundReader;
import com.antcolonies.colony.Neighbours;
import com.antcolonies.colony.Nest;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.TunnelRoute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.species.SpeciesProfile;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The galleries' web (Apex part 3, T2, one of the four parts of {@link Galleries}): its shape and growth. The 4 arms out
 * from the nest's own tunnels, the tips growing piece by piece and forking (step 2a), growing toward food first (step 3:
 * pieces out to rich food, grass pulling a tip), keeping clear of where the colony was attacked and of neighbours'
 * galleries (brain step 4g). The other parts read the web: the holes along it ({@link HoleTurns}), its upkeep
 * ({@link GalleryUpkeep}), the catch-up on return ({@link GalleryCatchUp}).
 */
final class GalleryWeb {
    /** A tip with no route found this many times stops growing. */
    private static final int TIP_TRIES = 3;

    /** How many ways a piece may take are tried (the one farthest from the other tips is taken). */
    private static final int WAYS_TRIED = 4;

    /** The most a piece may turn away from straight out from the middle (degrees): galleries run outward. */
    private static final double MOST_OFF_OUTWARD = 60.0;

    /**
     * A growing tip: the gallery that ends there ({@code way}, from the nest's shaft to the tip, its hole at the tip),
     * the way it grows (degrees: 0 east, 90 south), the blocks grown since it last forked and when it forks next.
     * {@code pulled}: it grew toward food (step 3), so it stops while known food lies round its hole.
     */
    record Tip(NestPlan.ForagingTunnel way, double bearing, int sinceFork, int forkAt, boolean pulled) {
        Tip(NestPlan.ForagingTunnel way, double bearing, int sinceFork, int forkAt) {
            this(way, bearing, sinceFork, forkAt, false);
        }

        Tip asPulled() {
            return new Tip(way, bearing, sinceFork, forkAt, true);
        }

        BlockPos end() {
            return way.tunnel().get(way.tunnel().size() - 1);
        }
    }

    /** The arms out from the nest's own tunnels, each with the part of the tunnel it leaves from. Saved. */
    List<NestPlan.ForagingTunnel> arms = List.of();

    boolean armsPlanned;

    /** Where each arm's own blocks start in its tunnel (the blocks before are its parent's), by the arm's hole. Saved. */
    final Map<BlockPos, Integer> newFrom = new HashMap<>();

    /** The growing tips (step 2a). Saved. */
    final List<Tip> tips = new ArrayList<>();

    boolean tipsMade;

    /** How long the first arms were (the galleries are never held shorter). Saved. */
    private int baseLength;

    /** Routes not found from each tip (by its hole); {@link #TIP_TRIES} and the tip stops. Not saved. */
    final Map<BlockPos, Integer> tipFails = new HashMap<>();

    /** Listens for big dead wood round the holes and tips (not saved: it listens again soon after a load). */
    private final DeadWoodCall woodCall;

    /** The holes of the first arms (they never fill in). Saved. */
    final Set<BlockPos> base = new HashSet<>();

    /** Each piece's parent (by their holes; none for the first arms and pieces off the nest's own tunnels). Saved. */
    final Map<BlockPos, BlockPos> parents = new HashMap<>();

    @Nullable
    Integer reachForTests;

    final Galleries g;
    private final Colony colony;

    GalleryWeb(Galleries g) {
        this.g = g;
        this.colony = g.colony;
        this.woodCall = new DeadWoodCall(colony);
    }

    /** The piece ending at this hole, or null. */
    @Nullable
    public NestPlan.ForagingTunnel pieceAt(BlockPos hole) {
        for (NestPlan.ForagingTunnel arm : arms) {
            if (arm.hole().equals(hole)) {
                return arm;
            }
        }
        return null;
    }

    /** Whether no other piece grows from this one. */
    public boolean isLeaf(BlockPos hole) {
        return !parents.containsValue(hole);
    }

    /** The piece this one grew from, or null (a first arm, or a piece off the nest's own tunnels). */
    @Nullable
    public BlockPos parentOf(BlockPos hole) {
        return parents.get(hole);
    }

    // --- the arms ------------------------------------------------------------------------------------------------

    /** Once the nest's own tunnels are dug: the 4 arms out from them (an arm with no route is left out). */
    void planArms(ServerLevel level, List<NestPlan.ForagingTunnel> own) {
        for (NestPlan.ForagingTunnel tunnel : own) {
            if (!ForagingHoles.isDug(level, tunnel)) {
                return;
            }
        }
        SavannaNumbers.GalleryNumbers numbers = g.numbers();
        BlockPos center = colony.getCenter();
        Direction front = own.get(0).side().getClockWise(); // (the first side tunnel runs to the left of the rooms)
        List<Direction> ways = new ArrayList<>();
        own.forEach(tunnel -> ways.add(tunnel.side()));
        ways.add(front);
        ways.add(front.getOpposite());
        for (Direction way : ways) {
            if (!level.isLoaded(center.relative(way, numbers.armLength() + 2))) {
                return; // (later, once the ground out there is loaded)
            }
        }
        int keepOut = colony.profile().nest().foragingHoleDistance() + 1;
        TunnelRoute.Ground ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        List<NestPlan.ForagingTunnel> planned = planArms(own, ways, ground, center, numbers.armLength(), keepOut);
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(planned);
        all.addAll(arms); // (pieces out to food planned before the arms)
        arms = List.copyOf(all);
        for (NestPlan.ForagingTunnel arm : planned) {
            newFrom.put(arm.hole(), leadInLength(own, arm));
            base.add(arm.hole().immutable());
            g.upkeep.lastUsed.put(arm.hole().immutable(), level.getGameTime());
        }
        armsPlanned = true;
        g.changed.run();
        ColonyManager.get(level).markChanged();
    }

    /**
     * The arms out from the nest's own tunnels {@code own} toward {@code ways}, about {@code armLength} blocks from the
     * middle, none dug within {@code keepOut} blocks of it (the mound and the rooms); an arm with no route is left out.
     * Pure route planning (the gallery proof checks it on made-up landscapes).
     */
    static List<NestPlan.ForagingTunnel> planArms(List<NestPlan.ForagingTunnel> own, List<Direction> ways, TunnelRoute.Ground ground,
            BlockPos center, int armLength, int keepOut) {
        TunnelRoute.Ground outside = keepOutOf(ground, center, keepOut);
        List<NestPlan.ForagingTunnel> planned = new ArrayList<>();
        for (Direction way : ways) {
            List<NestPlan.ForagingTunnel> from = own.stream().filter(tunnel -> tunnel.side() == way).toList();
            if (from.isEmpty()) {
                from = own; // the front and back arms branch off whichever side tunnel gives the shorter way
            }
            List<BlockPos> starts = new ArrayList<>();
            from.forEach(tunnel -> starts.addAll(tunnel.tunnel()));
            TunnelRoute.Plan plan = TunnelRoute.plan(starts, center.relative(way, armLength), outside, armLength * 2);
            if (plan == null || plan.cells().isEmpty()) {
                SavannaMod.LOGGER.debug("[galleries] no route for the arm to the {}", way);
                continue;
            }
            List<BlockPos> cells = new ArrayList<>(leadIn(from, plan.cells().get(0)));
            cells.addAll(plan.cells());
            planned.add(new NestPlan.ForagingTunnel(List.copyOf(cells), plan.hole(), way));
            SavannaMod.LOGGER.debug("[galleries] an arm to the {}: {} new blocks, hole at {}", way, plan.cells().size(), plan.hole().toShortString());
        }
        return planned;
    }

    /** How many of an arm's first blocks are a nest tunnel's (its own blocks start after them). */
    private static int leadInLength(List<NestPlan.ForagingTunnel> own, NestPlan.ForagingTunnel arm) {
        Set<BlockPos> nest = new HashSet<>();
        own.forEach(tunnel -> nest.addAll(tunnel.tunnel()));
        int i = 0;
        while (i < arm.tunnel().size() && nest.contains(arm.tunnel().get(i))) {
            i++;
        }
        return i;
    }

    /** The part of a nest tunnel an arm leaves from: its blocks from the shaft up to the one {@code first} touches. */
    private static List<BlockPos> leadIn(List<NestPlan.ForagingTunnel> from, BlockPos first) {
        for (NestPlan.ForagingTunnel tunnel : from) {
            List<BlockPos> cells = tunnel.tunnel();
            for (int i = cells.size() - 1; i >= 0; i--) {
                if (cells.get(i).distManhattan(first) == 1) {
                    return cells.subList(0, i + 1);
                }
            }
        }
        return List.of();
    }

    /** The ground, with no digging within {@code radius} blocks (sideways) of the nest's middle: the mound and the rooms. */
    static TunnelRoute.Ground keepOutOf(TunnelRoute.Ground ground, BlockPos center, int radius) {
        return new TunnelRoute.Ground() {
            public Integer groundTop(int x, int z) {
                return ground.groundTop(x, z);
            }

            public boolean mayDig(BlockPos cell) {
                return Galleries.flat(cell, center) >= radius && ground.mayDig(cell);
            }

            public boolean mayOpenHole(BlockPos hole) {
                return Galleries.flat(hole, center) >= radius && ground.mayOpenHole(hole);
            }

            public boolean isDug(BlockPos cell) {
                return ground.isDug(cell);
            }
        };
    }

    // --- step 2a: the tips grow -------------------------------------------------------------------------------------

    /** An arm's own blocks (not its parent's), the ones it digs. */
    List<BlockPos> ownCells(NestPlan.ForagingTunnel arm) {
        int from = Math.min(arm.tunnel().size(), newFrom.getOrDefault(arm.hole(), 0));
        return arm.tunnel().subList(from, arm.tunnel().size());
    }

    /**
     * The blocks the diggers look at for this foraging tunnel: an arm's own blocks (its parent comes before it in the
     * list), none for a hole along a tunnel (its blocks are the tunnel's), every block for the rest.
     */
    public List<BlockPos> digCells(NestPlan.ForagingTunnel tunnel) {
        if (!g.isOn()) {
            return tunnel.tunnel();
        }
        if (g.turns.holesAlongSet.contains(tunnel.hole())) {
            return List.of();
        }
        return newFrom.containsKey(tunnel.hole()) ? ownCells(tunnel) : tunnel.tunnel();
    }

    /** How many blocks long the galleries are in all (the arms' own blocks). */
    public int length() {
        int length = 0;
        for (NestPlan.ForagingTunnel arm : arms) {
            length += ownCells(arm).size();
        }
        return length;
    }

    /** How long the galleries may be with the colony's workers now (never shorter than the first arms). */
    public int lengthAllowed() {
        return Math.max(baseLength, g.numbers().lengthFor(colony.getCount(Caste.WORKER)));
    }

    /** How many tips are growing (for the tests and the colony list). */
    public int tipCount() {
        return tips.size();
    }

    /** Whether the first arms are planned (for the tests). */
    public boolean armsPlanned() {
        return armsPlanned;
    }

    /** Tests only: the farthest a tip may grow (null: the species file's reach), to keep a web inside a test's arena. */
    public void setReachForTests(@Nullable Integer reach) {
        reachForTests = reach;
    }

    int tipReach() {
        return reachForTests != null ? reachForTests : g.numbers().reach();
    }

    /** How far (blocks, sideways from the middle) the farthest hole of the galleries is; 0 while off or before there are any. */
    public int reach() {
        if (!g.isOn()) {
            return 0;
        }
        BlockPos center = colony.getCenter();
        double far = 0;
        for (NestPlan.ForagingTunnel arm : arms) {
            far = Math.max(far, Galleries.flat(arm.hole(), center));
        }
        for (NestPlan.ForagingTunnel hole : g.turns.holes) {
            far = Math.max(far, Galleries.flat(hole.hole(), center));
        }
        return (int) Math.round(far);
    }

    /**
     * Step 4, the plant scan along the galleries: where food matters to the colony past the nest's own ground, each as
     * {x, z, reach}: round every hole (the graze), round every growing tip (the grass that may pull it) and round every
     * plant the colony knows (rich food a piece may be dug to).
     */
    public List<int[]> scanSpots(Level level) {
        List<int[]> spots = new ArrayList<>();
        if (!g.isOn()) {
            return spots;
        }
        int graze = g.numbers().foodRadius() + 1;
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            spots.add(new int[] {tunnel.hole().getX(), tunnel.hole().getZ(), graze});
        }
        for (Tip tip : tips) {
            spots.add(new int[] {tip.end().getX(), tip.end().getZ(), g.numbers().pullReach() + 1});
        }
        for (BlockPos plant : colony.foodMap().knownPlants()) {
            spots.add(new int[] {plant.getX(), plant.getZ(), 1});
        }
        return spots;
    }

    /** The colony's range with the galleries: the farthest hole and the graze round it (0: no galleries yet). */
    public int range() {
        int reach = reach();
        return reach == 0 ? 0 : reach + g.numbers().foodRadius();
    }

    /**
     * Big dead wood calls (the user's pick, 2026-10-19, see {@link DeadWoodCall}): round the dug holes and the growing
     * tips, dead wood big enough is sensed and the colony learns it, so a piece goes out to it before the tips spread.
     */
    void listenForWood(ServerLevel level) {
        SavannaNumbers.WoodCall call = g.numbers().woodCall();
        if (call.farthest() <= 0) {
            return;
        }
        List<BlockPos> from = new ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            if (ForagingHoles.isDug(level, tunnel) && !colony.isHoleKeptShut(level, tunnel.hole())) {
                from.add(tunnel.hole());
            }
        }
        for (Tip tip : tips) {
            from.add(tip.end());
        }
        if (!woodCall.listen(level, from, call).isEmpty()) {
            colony.food().rescanPlantsSoon();
            ColonyManager.get(level).markChanged();
        }
    }

    /** Once everything planned is dug and the colony may have more: the shortest tip grows by a piece (or forks). */
    void growTips(ServerLevel level) {
        SavannaNumbers.GalleryNumbers numbers = g.numbers();
        BlockPos center = colony.getCenter();
        if (!tipsMade) {
            tips.clear();
            for (NestPlan.ForagingTunnel arm : arms) {
                tips.add(new Tip(arm, bearing(center, arm.hole()), 0, forkAt(numbers, colony.random()),
                        colony.nest().foodWays().isLiveNewHole(arm.hole())));
            }
            for (NestPlan.ForagingTunnel arm : arms) {
                baseLength += base.contains(arm.hole()) ? ownCells(arm).size() : 0;
            }
            tipsMade = true;
            ColonyManager.get(level).markChanged();
        }
        if (tips.isEmpty() || length() >= lengthAllowed()) {
            return;
        }
        for (NestPlan.ForagingTunnel arm : arms) {
            for (BlockPos cell : ownCells(arm)) {
                if (!level.isLoaded(cell) || !Nest.isDugOut(level, cell)) {
                    return; // one piece at a time: the last ones are dug first
                }
            }
        }
        Set<BlockPos> stopped = new HashSet<>();
        tipFails.forEach((hole, fails) -> {
            if (fails >= TIP_TRIES) {
                stopped.add(hole);
            }
        });
        stopped.addAll(atFood(level));
        for (Tip tip : tips) {
            if (colony.isHoleKeptShut(level, tip.way().hole())) {
                stopped.add(tip.way().hole()); // (brain step 4g: no tip grows on from where the colony was attacked)
            }
            if (yieldsAhead(level, tip)) {
                stopped.add(tip.way().hole()); // (step 5: nor toward a neighbour the colony yields to)
            }
        }
        if (colony.foragingHoles().isFoodWaiting()) {
            return; // food leads: rich food waits for a piece the workers do not allow yet
        }
        if (reachForTests != null) {
            numbers = numbers.withReach(reachForTests);
        }
        // Food leads (step 3): known grass just past a tip pulls it first; the even spreading only when nothing pulls.
        if (pullTowardGrass(level, numbers, center, stopped)) {
            return;
        }
        if (length() + g.upkeep.filledLength >= lengthAllowed()) {
            return; // the length filled-in branches gave back is for growth toward food only
        }
        List<Tip> order = new ArrayList<>(tips);
        shuffle(order, colony.random());
        Set<BlockPos> spreadStopped = new HashSet<>(stopped);
        spreadStopped.addAll(g.upkeep.backfilled); // (a way that led nowhere is not explored again)
        long now = level.getGameTime();
        for (Tip each : tips) {
            if (g.upkeep.isDue(each.way().hole(), now)) {
                // (nor one due to be filled in: it led nowhere for fill_days days; just back, the spreading would
                // otherwise lead on from it before it is filled, and it could never fill, docs/BUGS.md 59)
                spreadStopped.add(each.way().hole());
            }
        }
        Tip tip = pickTip(order, center, tipReach(), spreadStopped);
        if (tip == null || !loadedAround(level, tip.end(), numbers)) {
            return; // (later, once the ground out there is loaded)
        }
        int keepOut = colony.profile().nest().foragingHoleDistance() + 1;
        TunnelRoute.Ground ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        List<Tip> grown = grow(tip, tips, ground, center, keepOut, numbers, colony.random());
        List<Tip> clear = clearOfNeighbours(level, tip, clearOfAttacks(level, tip, grown));
        if (clear.isEmpty() && !grown.isEmpty()) {
            return; // the way on leads toward where the colony was attacked, or a neighbour it yields to: not now (no dead end: it may grow there later)
        }
        addGrown(level, tip, clear, false);
    }

    /**
     * Brain step 4g: the pieces {@code grown} from {@code tip} whose new blocks and hole all lie clear of where the colony
     * was attacked: no gallery grows toward an attack while it is remembered (asked of every part, colony parts step 7,
     * {@link ColonyQuestions#NO_GALLERY_THROUGH}: the termites' closed holes answer it).
     */
    private List<Tip> clearOfAttacks(ServerLevel level, Tip tip, List<Tip> grown) {
        ColonyQuestions questions = colony.questions();
        if (grown.isEmpty() || colony.danger().isEmpty() || !questions.answers(ColonyQuestions.NO_GALLERY_THROUGH)) {
            return grown;
        }
        List<Tip> clear = new ArrayList<>();
        for (Tip piece : grown) {
            List<BlockPos> cells = piece.way().tunnel();
            boolean near = questions.ask(level, ColonyQuestions.NO_GALLERY_THROUGH, piece.way().hole());
            for (int i = tip.way().tunnel().size(); i < cells.size() && !near; i++) {
                near = questions.ask(level, ColonyQuestions.NO_GALLERY_THROUGH, cells.get(i));
            }
            if (!near) {
                clear.add(piece);
            }
        }
        return clear;
    }

    // --- brain step 4g, step 5: neighbours ------------------------------------------------------------------------------

    /** The other colonies of this species with galleries whose web may come within reach of this one's. */
    List<Colony> neighbourColonies(ServerLevel level) {
        List<Colony> near = new ArrayList<>();
        SpeciesProfile.NeighbourNumbers numbers = colony.neighbours().numbers();
        if (numbers == null) {
            return near;
        }
        double most = 2.0 * (g.numbers().reach() + g.numbers().pieceLength()) + numbers.within() + 8;
        for (Colony other : ColonyManager.get(level).getColonies()) {
            if (other != colony && other.getSpecies().equals(colony.getSpecies()) && Galleries.of(other) != null && Galleries.of(other).isOn()
                    && colony.neighbours().canMeet(other) && other.getCenter().distSqr(colony.getCenter()) <= most * most) {
                near.add(other);
            }
        }
        return near;
    }

    /** Whether the colony yields to a neighbour at this tip's end or a piece's length ahead of it, the way it grows. */
    private boolean yieldsAhead(Level level, Tip tip) {
        Neighbours neighbours = colony.neighbours();
        if (neighbours.isEmpty()) {
            return false;
        }
        BlockPos end = tip.end();
        double ahead = g.numbers().pieceLength() + TunnelRoute.HOLE_REACH;
        double way = Math.toRadians(tip.bearing());
        BlockPos next = BlockPos.containing(end.getX() + Math.cos(way) * ahead, end.getY(), end.getZ() + Math.sin(way) * ahead);
        return neighbours.yieldsAt(level, end) || neighbours.yieldsAt(level, next);
    }

    /** Of {@code cells}, the one that comes closest to {@code other}'s galleries within {@code within}, and their cell: {ours, theirs}; or null. */
    @Nullable
    static BlockPos[] closestTo(Colony other, List<BlockPos> cells, double within) {
        BlockPos[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : cells) {
            BlockPos theirs = Galleries.of(other).galleryCellNear(cell, within);
            if (theirs != null && theirs.distSqr(cell) < bestDistance) {
                best = new BlockPos[] {cell, theirs};
                bestDistance = theirs.distSqr(cell);
            }
        }
        return best;
    }

    /** The cell of this colony's galleries (their own blocks) nearest {@code pos} within {@code within} blocks, or null. */
    @Nullable
    public BlockPos galleryCellNear(BlockPos pos, double within) {
        BlockPos best = null;
        double bestDistance = within * within;
        for (NestPlan.ForagingTunnel arm : arms) {
            for (BlockPos cell : ownCells(arm)) {
                double distance = cell.distSqr(pos);
                if (distance <= bestDistance) {
                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * A meeting of the galleries (real neighbouring Macrotermes sense each other's tunnels through the soil): this
     * colony's cell {@code ours} lies within reach of the other colony's cell {@code theirs}. Both colonies remember it,
     * once a minute per piece. Returns whether this colony now yields there.
     */
    boolean galleriesMet(ServerLevel level, Colony other, BlockPos ours, BlockPos theirs, BlockPos piece) {
        java.util.UUID mine = colony.getId();
        java.util.UUID key = new java.util.UUID(other.getId().getMostSignificantBits() ^ piece.asLong(), other.getId().getLeastSignificantBits());
        colony.news().tell(level, ColonyNews.NEIGHBOUR_MET, new ColonyNews.NeighbourMet(ours, other.getId(), key));
        other.news().tell(level, ColonyNews.NEIGHBOUR_MET, new ColonyNews.NeighbourMet(theirs, mine,
                new java.util.UUID(mine.getMostSignificantBits() ^ piece.asLong(), mine.getLeastSignificantBits())));
        return colony.neighbours().yieldsAt(level, ours);
    }

    /**
     * Step 5: the pieces {@code grown} from {@code tip} that keep clear of the neighbours' galleries. A piece whose new
     * blocks come within {@code colony.neighbours.within} of another colony's gallery is a meeting; it is not dug if this
     * colony yields there (an old one, or a young one after several meetings) or if it would touch the other gallery (the
     * webs never join).
     */
    private List<Tip> clearOfNeighbours(ServerLevel level, Tip tip, List<Tip> grown) {
        SpeciesProfile.NeighbourNumbers numbers = colony.neighbours().numbers();
        if (grown.isEmpty() || numbers == null || !numbers.wallGalleries()) {
            return grown;
        }
        List<Colony> others = neighbourColonies(level);
        if (others.isEmpty()) {
            return grown;
        }
        List<Tip> clear = new ArrayList<>();
        for (Tip piece : grown) {
            List<BlockPos> cells = new ArrayList<>(piece.way().tunnel().subList(Math.min(tip.way().tunnel().size(), piece.way().tunnel().size()),
                    piece.way().tunnel().size()));
            cells.add(piece.way().hole());
            boolean ok = true;
            for (Colony other : others) {
                BlockPos[] closest = closestTo(other, cells, numbers.within());
                if (closest != null && (galleriesMet(level, other, closest[0], closest[1], tip.way().hole()) || closest[0].distSqr(closest[1]) <= 2)) {
                    ok = false; // (one meeting per piece and neighbour, where they come closest)
                }
            }
            if (ok) {
                clear.add(piece);
            }
        }
        if (clear.isEmpty()) {
            tipFails.merge(tip.way().hole(), 1, Integer::sum); // (tried that way enough: the tip waits, as at a dead end)
        }
        return clear;
    }

    /** Whether the ground round a tip's end is loaded as far as its next piece may go. */
    private static boolean loadedAround(Level level, BlockPos end, SavannaNumbers.GalleryNumbers numbers) {
        int around = numbers.pieceLength() + TunnelRoute.HOLE_REACH + 2;
        return level.isLoaded(end.offset(around, 0, around)) && level.isLoaded(end.offset(-around, 0, -around))
                && level.isLoaded(end.offset(around, 0, -around)) && level.isLoaded(end.offset(-around, 0, around));
    }

    /**
     * The pieces {@code grown} from {@code tip} join the web (they take its place as tips; {@code pulled}: grown toward
     * food). None grown: the tip failed once more, and stops after {@link #TIP_TRIES}.
     */
    private void addGrown(ServerLevel level, Tip tip, List<Tip> grown, boolean pulled) {
        if (grown.isEmpty()) {
            int fails = tipFails.merge(tip.way().hole(), 1, Integer::sum);
            if (fails >= TIP_TRIES) {
                tips.remove(tip); // a dead end: stone, water, a cliff or the edge of the reach all round
                ColonyManager.get(level).markChanged();
                SavannaMod.LOGGER.debug("[galleries] the tip at {} stops: no way on", tip.end().toShortString());
            }
            return;
        }
        grown = grown.stream().filter(piece -> !takenHole(piece.way().hole())).toList(); // (never two pieces at one hole)
        if (grown.isEmpty()) {
            tipFails.merge(tip.way().hole(), 1, Integer::sum);
            return;
        }
        tips.remove(tip);
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(arms);
        for (Tip piece : grown) {
            tips.add(pulled ? piece.asPulled() : piece);
            all.add(piece.way());
            BlockPos hole = piece.way().hole().immutable();
            newFrom.put(hole, tip.way().tunnel().size());
            parents.put(hole, tip.way().hole().immutable());
            g.upkeep.lastUsed.put(hole, level.getGameTime());
            if (pulled) {
                g.upkeep.filledLength = Math.max(0, g.upkeep.filledLength - (piece.way().tunnel().size() - tip.way().tunnel().size()));
            }
        }
        arms = List.copyOf(all);
        g.changed.run();
        ColonyManager.get(level).markChanged();
        SavannaMod.LOGGER.debug("[galleries] the tip at {} grows{}{}: {} blocks of {} allowed", tip.end().toShortString(),
                pulled ? " toward food" : "", grown.size() > 1 ? " and forks" : "", length(), lengthAllowed());
    }

    /**
     * The tips that stay where they are for now, at their food (step 3: a tip stops at the food while it lasts): one at a
     * feeding spot whose food is not gone, and one grown toward grass while known food lies round its hole.
     */
    private Set<BlockPos> atFood(ServerLevel level) {
        Set<BlockPos> at = new HashSet<>();
        List<BlockPos> pulledHoles = new ArrayList<>();
        for (Tip tip : tips) {
            BlockPos hole = tip.way().hole();
            if (colony.nest().foodWays().isLiveNewHole(hole)) {
                at.add(hole);
            } else if (tip.pulled()) {
                pulledHoles.add(hole);
            }
        }
        if (!pulledHoles.isEmpty()) {
            g.turns.foodNear(level, pulledHoles, g.numbers().foodRadius()).forEach((hole, food) -> {
                if (food > 0) {
                    at.add(hole);
                }
            });
        }
        return at;
    }

    /**
     * Food leads (step 3): the tip with the most known grass (by its pieces) lying within {@code pull_reach} blocks past
     * its hole, farther out than the hole and not yet grazed from any hole (beyond the graze, {@code food_radius}),
     * grows a piece straight toward that grass (no wander; a fork is still made when due, as real tunnels branch round
     * found food). It may use the length filled-in branches gave back. False if no grass pulls.
     */
    private boolean pullTowardGrass(ServerLevel level, SavannaNumbers.GalleryNumbers numbers, BlockPos center, Set<BlockPos> stopped) {
        List<BlockPos> grass = new ArrayList<>();
        for (BlockPos plant : colony.food().plants(level)) {
            SpeciesProfile.PlantFood food = colony.profile().food().plantFor(level.getBlockState(plant));
            if (food != null && !food.rich() && colony.foodMap().knows(plant, null) && !colony.keepsOff(level, plant)) {
                grass.add(plant); // (grass where the colony was attacked pulls no tip: brain step 4g)
            }
        }
        if (grass.isEmpty()) {
            return false;
        }
        List<BlockPos> holes = new ArrayList<>();
        holes.add(colony.nest().getEntrance());
        colony.foragingHoles().tunnels(level).forEach(tunnel -> holes.add(tunnel.hole()));
        grass.removeIf(plant -> Galleries.nearAny(plant, holes, numbers.foodRadius() + 0.5));
        if (grass.isEmpty()) {
            return false;
        }
        Tip best = null;
        double bestWorth = 0;
        double aimX = 0;
        double aimZ = 0;
        for (Tip tip : tips) {
            BlockPos hole = tip.way().hole();
            double out = Galleries.flat(hole, center);
            if (stopped.contains(hole) || out >= tipReach() - 1) {
                continue;
            }
            double worth = 0;
            double x = 0;
            double z = 0;
            for (BlockPos plant : grass) {
                if (Galleries.flat(plant, hole) <= numbers.pullReach() && Galleries.flat(plant, center) > out + 1) {
                    SpeciesProfile.PlantFood food = colony.profile().food().plantFor(level.getBlockState(plant));
                    double pieces = food == null ? 1 : Math.max(1, food.pieces());
                    worth += pieces;
                    x += plant.getX() * pieces;
                    z += plant.getZ() * pieces;
                }
            }
            if (worth > bestWorth) {
                best = tip;
                bestWorth = worth;
                aimX = x / worth;
                aimZ = z / worth;
            }
        }
        if (best == null || !loadedAround(level, best.end(), numbers)) {
            return false;
        }
        BlockPos end = best.end();
        double toward = Math.toDegrees(Math.atan2(aimZ - end.getZ(), aimX - end.getX()));
        Tip aimed = new Tip(best.way(), toward, best.sinceFork(), best.forkAt(), best.pulled());
        int keepOut = colony.profile().nest().foragingHoleDistance() + 1;
        TunnelRoute.Ground ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        List<Tip> grown = grow(aimed, tips, ground, center, keepOut, numbers.withWander(0), colony.random());
        SavannaMod.LOGGER.debug("[galleries] grass worth {} past the tip at {} pulls it toward {}, {}", Math.round(bestWorth),
                end.toShortString(), Math.round(aimX), Math.round(aimZ));
        tips.set(tips.indexOf(best), aimed);
        List<Tip> clear = clearOfNeighbours(level, aimed, clearOfAttacks(level, aimed, grown));
        if (clear.isEmpty() && !grown.isEmpty()) {
            return true; // (the way to the grass runs toward an attack, or a neighbour the colony yields to: not now)
        }
        addGrown(level, aimed, clear, true);
        return true;
    }

    // --- step 3: pieces out to rich food --------------------------------------------------------------------------

    /** How far from the middle (blocks, sideways) food may lie for a piece to be dug out to it: the tips' reach. */
    public int foodReach() {
        return tipReach();
    }

    /**
     * A piece of gallery out to rich food at {@code food} (step 3; {@link ForagingHoles} picks the food, checks the
     * workers allow it ({@link #affords}) and plans its feeding gallery), from the nearest dug gallery, or null: no route.
     */
    @Nullable
    public ColonyHooks.GalleryPiece pieceTo(ServerLevel level, BlockPos food) {
        List<NestPlan.ForagingTunnel> from = new ArrayList<>(colony.nest().ownForagingTunnels(level));
        from.addAll(arms);
        List<NestPlan.ForagingTunnel> dug = new ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : from) {
            if (ForagingHoles.isDug(level, tunnel)) {
                dug.add(tunnel);
            }
        }
        if (dug.isEmpty()) {
            return null;
        }
        int keepOut = colony.profile().nest().foragingHoleDistance() + 1;
        TunnelRoute.Ground ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        ColonyHooks.GalleryPiece piece = planPiece(dug, food, ground, colony.getCenter(), keepOut, tipReach() + 16);
        return piece == null || takenHole(piece.tunnel().hole()) ? null : piece; // (never two pieces ending at one hole)
    }

    /** Whether a piece, a hole along a tunnel or one of the nest's own tunnels already ends at this hole. */
    private boolean takenHole(BlockPos hole) {
        return pieceAt(hole) != null || g.turns.holesAlongSet.contains(hole);
    }

    /**
     * Whether the workers allow this piece out to {@code food} now: the galleries with it no longer than they allow and
     * one piece's length over (the length filled-in branches gave back may be used for food); for big dead wood (see
     * {@link DeadWoodCall}) up to the call's {@code over_limit} more (the user's pick, 2026-10-19: a big find gets the
     * diggers; the even spreading waits until the workers catch up, {@link #growTips}).
     */
    public boolean affords(ServerLevel level, ColonyHooks.GalleryPiece piece, BlockPos food) {
        int fresh = piece.tunnel().tunnel().size() - piece.from();
        int allowed = lengthAllowed() + g.numbers().pieceLength();
        if (length() + fresh <= allowed) {
            return true;
        }
        SavannaNumbers.WoodCall call = g.numbers().woodCall();
        if (call.overLimit() > 0 && length() + fresh <= allowed + call.overLimit() && woodCall.isBig(level, food, call)) {
            SavannaMod.LOGGER.debug("[galleries] the way to the big dead wood at {} is {} blocks: past what the workers allow ({} of {}), dug all the same",
                    food.toShortString(), fresh, length(), lengthAllowed());
            return true;
        }
        SavannaMod.LOGGER.debug("[galleries] the way to the food at {} is {} blocks: more than the workers allow now ({} of {})",
                piece.tunnel().hole().toShortString(), fresh, length(), lengthAllowed());
        return false;
    }

    /**
     * A piece out to {@code food} from the nearest of the dug tunnels {@code from} (the nest's own, the arms, any piece),
     * kept out of {@code keepOut} blocks round the middle (the mound): its tunnel from the shaft, with the part of the
     * tunnel it leaves from before its own blocks. Null if there is no route. Pure (the gallery proof checks it).
     */
    @Nullable
    static ColonyHooks.GalleryPiece planPiece(List<NestPlan.ForagingTunnel> from, BlockPos food, TunnelRoute.Ground ground, BlockPos center, int keepOut,
            int maxLength) {
        List<BlockPos> starts = new ArrayList<>();
        from.forEach(tunnel -> starts.addAll(tunnel.tunnel()));
        TunnelRoute.Plan plan = TunnelRoute.plan(starts, food, keepOutOf(ground, center, keepOut), maxLength);
        if (plan == null || plan.cells().isEmpty()) {
            return null;
        }
        BlockPos first = plan.cells().get(0);
        NestPlan.ForagingTunnel source = null;
        List<BlockPos> lead = List.of();
        for (NestPlan.ForagingTunnel tunnel : from) {
            List<BlockPos> prefix = leadIn(List.of(tunnel), first);
            if (!prefix.isEmpty() && (source == null || prefix.size() < lead.size())) {
                source = tunnel;
                lead = prefix;
            }
        }
        List<BlockPos> cells = new ArrayList<>(lead);
        cells.addAll(plan.cells());
        Direction side = Direction.getNearest(plan.hole().getX() - center.getX(), 0, plan.hole().getZ() - center.getZ());
        BlockPos parent = source == null ? null : source.hole();
        return new ColonyHooks.GalleryPiece(new NestPlan.ForagingTunnel(List.copyOf(cells), plan.hole(), side), lead.size(), parent);
    }

    /**
     * A piece out to rich food joins the web (its feeding spot is kept by {@link FoodWays}): a tip at its food. A piece
     * leaving from the end of a tip takes that tip's place. Uses the length filled-in branches gave back first.
     */
    public void addFoodPiece(ServerLevel level, ColonyHooks.GalleryPiece piece) {
        BlockPos hole = piece.tunnel().hole().immutable();
        BlockPos parent = piece.parent() != null && pieceAt(piece.parent()) != null ? piece.parent().immutable() : null;
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(arms);
        all.add(piece.tunnel());
        arms = List.copyOf(all);
        newFrom.put(hole, piece.from());
        if (parent != null) {
            parents.put(hole, parent);
            NestPlan.ForagingTunnel of = pieceAt(parent);
            if (of != null && piece.from() >= of.tunnel().size()) {
                tips.removeIf(tip -> tip.way().hole().equals(parent)); // grown on from the tip's end
            }
        }
        g.upkeep.lastUsed.put(hole, level.getGameTime());
        if (tipsMade) {
            tips.add(new Tip(piece.tunnel(), bearing(colony.getCenter(), hole), 0, forkAt(g.numbers(), colony.random()), true));
        }
        g.upkeep.filledLength = Math.max(0, g.upkeep.filledLength - (piece.tunnel().tunnel().size() - piece.from()));
        g.changed.run();
        ColonyManager.get(level).markChanged();
        SavannaMod.LOGGER.debug("[galleries] a piece of {} blocks out to food, hole at {}{}", piece.tunnel().tunnel().size() - piece.from(),
                hole.toShortString(), parent == null ? " (off the nest's own tunnel)" : " (from the piece to " + parent.toShortString() + ")");
    }

    /**
     * The tunnels dug out to rich food before step 3 (or while the switch was off) join the web as pieces: their
     * feeding spots stay as they are (the user's decision: one system).
     */
    void adoptFoodTunnels(ServerLevel level) {
        boolean adopted = false;
        for (FoodWays.NewTunnel tunnel : colony.nest().foodWays().getNewTunnels()) {
            BlockPos hole = tunnel.tunnel().hole();
            if (pieceAt(hole) != null) {
                continue;
            }
            List<NestPlan.ForagingTunnel> all = new ArrayList<>(arms);
            all.add(tunnel.tunnel());
            arms = List.copyOf(all);
            newFrom.put(hole.immutable(), 0);
            BlockPos first = tunnel.tunnel().tunnel().get(0);
            for (NestPlan.ForagingTunnel arm : arms) {
                if (!arm.hole().equals(hole) && !leadIn(List.of(arm), first).isEmpty()) {
                    parents.put(hole.immutable(), arm.hole().immutable());
                    break;
                }
            }
            g.upkeep.lastUsed.put(hole.immutable(), level.getGameTime());
            if (tipsMade) {
                tips.add(new Tip(tunnel.tunnel(), bearing(colony.getCenter(), hole), 0, forkAt(g.numbers(), colony.random()), true));
            }
            adopted = true;
            SavannaMod.LOGGER.debug("[galleries] the tunnel to the food at {} joins the web", tunnel.food().toShortString());
        }
        if (adopted) {
            g.changed.run();
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * The tip that grows next: the shortest (nearest the middle, sideways) of those still short of {@code reach} and not
     * {@code stopped}; the first in the list on a near tie (the caller shuffles it). Null if none may grow.
     */
    @Nullable
    static Tip pickTip(List<Tip> tips, BlockPos center, int reach, Set<BlockPos> stopped) {
        Tip best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Tip tip : tips) {
            double distance = Galleries.flat(tip.way().hole(), center);
            if (distance < reach - 1 && distance < bestDistance - 0.5 && !stopped.contains(tip.way().hole())) {
                best = tip;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * One piece of growth from {@code tip} (two when it is time to fork): each way wanders up to {@code wander} degrees,
     * of a few tried the one farthest from the other tips is taken, never more than {@link #MOST_OFF_OUTWARD} off
     * straight out; planned by {@link TunnelRoute} round the mound ({@code keepOut}) and kept within the reach. Each new
     * tip's gallery is the old one with the piece on its end. Empty if no way on was found. Pure (the gallery proof
     * grows whole webs with it on made-up landscapes).
     */
    static List<Tip> grow(Tip tip, List<Tip> tips, TunnelRoute.Ground ground, BlockPos center, int keepOut,
            SavannaNumbers.GalleryNumbers numbers, RandomSource random) {
        TunnelRoute.Ground outside = keepOutOf(ground, center, keepOut);
        boolean fork = tip.sinceFork() + numbers.pieceLength() >= tip.forkAt();
        double[] ways = fork ? new double[] {tip.bearing() - numbers.forkAngle(), tip.bearing() + numbers.forkAngle()}
                : new double[] {tip.bearing()};
        BlockPos end = tip.end();
        double outward = bearing(center, end);
        List<BlockPos> others = new ArrayList<>();
        for (Tip other : tips) {
            if (other != tip) {
                others.add(other.way().hole());
            }
        }
        List<Tip> grown = new ArrayList<>();
        for (double way : ways) {
            BlockPos target = null;
            double targetWay = way;
            double clearest = -1;
            for (int i = 0; i < WAYS_TRIED; i++) {
                double tried = way + (random.nextDouble() * 2 - 1) * numbers.wander();
                double off = wrap(tried - outward);
                if (Math.abs(off) > MOST_OFF_OUTWARD) {
                    tried = outward + Math.signum(off) * MOST_OFF_OUTWARD;
                }
                // (aimed past the piece's length by the hole's reach: the route stops as soon as its hole is near the aim)
                int aim = numbers.pieceLength() + TunnelRoute.HOLE_REACH;
                BlockPos at = end.offset((int) Math.round(Math.cos(Math.toRadians(tried)) * aim), 0,
                        (int) Math.round(Math.sin(Math.toRadians(tried)) * aim));
                if (Galleries.flat(at, center) > numbers.reach() + TunnelRoute.HOLE_REACH) {
                    continue;
                }
                double clear = Double.MAX_VALUE;
                for (BlockPos other : others) {
                    clear = Math.min(clear, Galleries.flat(at, other));
                }
                if (clear > clearest) {
                    clearest = clear;
                    target = at;
                    targetWay = tried;
                }
            }
            if (target == null) {
                continue;
            }
            TunnelRoute.Plan plan = TunnelRoute.plan(List.of(end), target, outside, numbers.pieceLength() * 3);
            if (plan == null || plan.cells().isEmpty() || Galleries.flat(plan.hole(), center) > numbers.reach()) {
                continue;
            }
            if (plan.hole().equals(tip.way().hole()) || others.contains(plan.hole())) {
                continue; // (a hole the web has already: two pieces may never end at one hole)
            }
            List<BlockPos> cells = new ArrayList<>(tip.way().tunnel());
            cells.addAll(plan.cells());
            BlockPos last = plan.cells().get(plan.cells().size() - 1);
            double grewWay = Galleries.flat(last, end) < 1 ? targetWay : bearing(end, last);
            NestPlan.ForagingTunnel piece = new NestPlan.ForagingTunnel(List.copyOf(cells), plan.hole(), sideOf(grewWay));
            grown.add(new Tip(piece, grewWay, fork ? 0 : tip.sinceFork() + plan.cells().size(),
                    fork ? forkAt(numbers, random) : tip.forkAt()));
            others.add(plan.hole());
        }
        return grown;
    }

    /** After how many blocks of growth a tip forks: {@code fork_min} to {@code fork_max}. */
    static int forkAt(SavannaNumbers.GalleryNumbers numbers, RandomSource random) {
        return numbers.forkMin() + random.nextInt(Math.max(1, numbers.forkMax() - numbers.forkMin() + 1));
    }

    /** The way from {@code from} to {@code to}, in degrees (0 east, 90 south). */
    static double bearing(BlockPos from, BlockPos to) {
        return Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
    }

    /** An angle brought into -180 to 180 degrees. */
    private static double wrap(double degrees) {
        double d = degrees % 360.0;
        return d > 180 ? d - 360 : d < -180 ? d + 360 : d;
    }

    /** The side (north, south, east, west) nearest a way in degrees. */
    private static Direction sideOf(double degrees) {
        return Direction.getNearest((float) Math.cos(Math.toRadians(degrees)), 0.0F, (float) Math.sin(Math.toRadians(degrees)));
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }

    /**
     * A save from before the fix of 2026-10-17 may hold two pieces ending at one hole (a tip grew a piece whose hole was
     * its own): the longer one is kept (it holds the other's blocks), counted from the piece it really grew from.
     */
    void repairSharedHoles() {
        Map<BlockPos, NestPlan.ForagingTunnel> longest = new HashMap<>();
        boolean any = false;
        for (NestPlan.ForagingTunnel arm : arms) {
            NestPlan.ForagingTunnel other = longest.get(arm.hole());
            if (other != null) {
                any = true;
            }
            if (other == null || arm.tunnel().size() > other.tunnel().size()) {
                longest.put(arm.hole(), arm);
            }
        }
        if (!any) {
            return;
        }
        List<NestPlan.ForagingTunnel> kept = new ArrayList<>();
        for (NestPlan.ForagingTunnel arm : arms) {
            if (longest.get(arm.hole()) == arm && !kept.contains(arm)) {
                kept.add(arm);
            }
        }
        for (NestPlan.ForagingTunnel arm : kept) {
            if (!parents.containsKey(arm.hole()) || !arm.hole().equals(parents.get(arm.hole()))) {
                continue;
            }
            // Its parent is itself: the real one is the longest other piece whose whole tunnel it starts with.
            NestPlan.ForagingTunnel parent = null;
            for (NestPlan.ForagingTunnel other : kept) {
                if (other != arm && other.tunnel().size() < arm.tunnel().size()
                        && arm.tunnel().subList(0, other.tunnel().size()).equals(other.tunnel())
                        && (parent == null || other.tunnel().size() > parent.tunnel().size())) {
                    parent = other;
                }
            }
            if (parent != null) {
                parents.put(arm.hole(), parent.hole());
                newFrom.put(arm.hole(), parent.tunnel().size());
            } else {
                parents.remove(arm.hole());
            }
        }
        arms = List.copyOf(kept);
        tips.removeIf(tip -> !kept.contains(tip.way()));
        SavannaMod.LOGGER.info("[galleries] repaired pieces sharing a hole: {} pieces now", arms.size());
    }

    /**
     * A save from before step 3: each piece's parent is the piece whose whole tunnel its own starts with, and the first
     * arms are the ones with none (the pieces' last use is counted from now, see {@link GalleryUpkeep#upkeep}).
     */
    void deriveOlderSave() {
        for (NestPlan.ForagingTunnel arm : arms) {
            int from = Math.min(arm.tunnel().size(), newFrom.getOrDefault(arm.hole(), 0));
            for (NestPlan.ForagingTunnel other : arms) {
                if (other != arm && other.tunnel().size() == from && arm.tunnel().subList(0, from).equals(other.tunnel())) {
                    parents.put(arm.hole(), other.hole());
                    break;
                }
            }
        }
        FoodWays ways = colony.nest().foodWays();
        for (NestPlan.ForagingTunnel arm : arms) {
            if (!parents.containsKey(arm.hole()) && ways.newTunnelAt(arm.hole()) == null) {
                base.add(arm.hole());
            }
        }
    }

    /** The web's part of the galleries' save (the names as before the split). */
    void save(CompoundTag out) {
        out.putBoolean("armsPlanned", armsPlanned);
        out.put("arms", Galleries.saveTunnels(arms));
        ListTag starts = new ListTag();
        newFrom.forEach((hole, from) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putInt("from", from);
            starts.add(entry);
        });
        out.put("newFrom", starts);
        ListTag tipList = new ListTag();
        for (Tip tip : tips) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", tip.way().hole().asLong());
            entry.putDouble("bearing", tip.bearing());
            entry.putInt("sinceFork", tip.sinceFork());
            entry.putInt("forkAt", tip.forkAt());
            entry.putBoolean("pulled", tip.pulled());
            tipList.add(entry);
        }
        out.put("tips", tipList);
        out.putBoolean("tipsMade", tipsMade);
        out.putInt("baseLength", baseLength);
        out.putLongArray("base", base.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag parentList = new ListTag();
        parents.forEach((hole, parent) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("parent", parent.asLong());
            parentList.add(entry);
        });
        out.put("parents", parentList);
    }

    void load(CompoundTag in) {
        armsPlanned = in.getBoolean("armsPlanned");
        arms = Galleries.loadTunnels(in.getList("arms", Tag.TAG_COMPOUND));
        newFrom.clear();
        for (Tag element : in.getList("newFrom", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            newFrom.put(BlockPos.of(entry.getLong("hole")), entry.getInt("from"));
        }
        tips.clear();
        for (Tag element : in.getList("tips", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            BlockPos hole = BlockPos.of(entry.getLong("hole"));
            arms.stream().filter(arm -> arm.hole().equals(hole)).findFirst().ifPresent(arm -> tips.add(
                    new Tip(arm, entry.getDouble("bearing"), entry.getInt("sinceFork"), entry.getInt("forkAt"), entry.getBoolean("pulled"))));
        }
        tipsMade = in.getBoolean("tipsMade");
        baseLength = in.getInt("baseLength");
        parents.clear();
        for (Tag element : in.getList("parents", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            parents.put(BlockPos.of(entry.getLong("hole")), BlockPos.of(entry.getLong("parent")));
        }
        base.clear();
        if (in.contains("base")) {
            for (long hole : in.getLongArray("base")) {
                base.add(BlockPos.of(hole));
            }
        } else {
            deriveOlderSave();
        }
        tipFails.clear();
    }
}
