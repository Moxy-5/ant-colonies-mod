package com.antcolonies.savanna.colony;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.ForagingTrails;
import com.antcolonies.colony.GroundReader;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.TunnelRoute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The holes along the galleries and their turns (Apex part 3, T2, one of the four parts of {@link Galleries}): the holes
 * laid out along every dug tunnel, the ones picked for each foraging time (near food first, a few rested ones to explore,
 * the far ones from the outer half of the web), and a new hole in place of one left after attacks (brain step 4g).
 */
final class HoleTurns {
    /** A remembered trail counts for its hole while known food lies this near (blocks) its patch. */
    private static final double PATCH_FOOD = 4.0;

    /** The holes along the tunnels (looked up by the digging). */
    final Set<BlockPos> holesAlongSet = new HashSet<>();

    /** The holes along the tunnels, each with the tunnel up to the block under it. Saved. */
    final List<NestPlan.ForagingTunnel> holes = new ArrayList<>();

    /** The tunnels (by the hole at their end) whose holes along them are laid out. Saved. */
    final Set<BlockPos> laidOut = new HashSet<>();

    /** When each hole last had its turn (game time). Saved. */
    final Map<BlockPos, Long> lastTurn = new HashMap<>();

    /** The holes whose turn it is this foraging time. Saved. */
    final Set<BlockPos> tonight = new LinkedHashSet<>();

    /** Whether a foraging time is going on (its holes are picked). Saved. */
    boolean inRound;

    /** What came of looking for a new hole in place of a left one (brain step 4g, {@link #openInstead}). */
    enum Instead {
        OPENED, NO_ROOM, NOT_LOADED
    }

    /** A new hole is never laid out closer than this (blocks, sideways) to another hole. */
    static final double BESIDE = 2.0;

    final Galleries g;
    private final Colony colony;

    HoleTurns(Galleries g) {
        this.g = g;
        this.colony = g.colony;
    }

    /**
     * Whether this hole may be opened now: switched off, every hole; on, only the holes whose turn it is, and a hole a
     * runway of sheeting leaves from (its builders go out by it; once finished it stays open under the runway), and the
     * hole of a tunnel dug out to rich food while that food lasts (the colony dug it for that food: it is opened every
     * foraging time, also when the tunnel is finished after the night's turns were picked).
     */
    public boolean isInTurn(BlockPos hole) {
        FoodWays ways = colony.nest().foodWays();
        return !g.isOn() || tonight.contains(hole) || ways.runwayAt(hole) != null || ways.isLiveNewHole(hole);
    }

    /** The holes whose turn it is (for the tests and the colony list). */
    public Set<BlockPos> tonight() {
        return Set.copyOf(tonight);
    }

    /** When this hole last had its turn, or {@link Colony#NOT_SET}. For the tests. */
    public long lastTurn(BlockPos hole) {
        return lastTurn.getOrDefault(hole, Colony.NOT_SET);
    }

    /** A foraging time starting picks its holes; one ending is noted. */
    void round(ServerLevel level, boolean away) {
        boolean foraging = colony.isForagingTime(level);
        if (foraging && !inRound) {
            inRound = true;
            pickTurns(level, away);
            ColonyManager.get(level).markChanged();
        } else if (!foraging && inRound) {
            inRound = false;
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * The holes open to the foragers this foraging time: the night's turns, the holes runways leave from, and the holes of
     * pieces out to rich food whose food lasts (see {@link #isInTurn}); never a hole closed for danger (brain step 4g,
     * {@link ClosedHoles}). The numbers graze only round these (step 4).
     */
    public List<BlockPos> holesInTurn(Level level) {
        List<BlockPos> list = new ArrayList<>(tonight);
        FoodWays ways = colony.nest().foodWays();
        for (FoodWays.Runway runway : ways.getRunways()) {
            list.add(runway.hole());
        }
        for (FoodWays.NewTunnel tunnel : ways.getNewTunnels()) {
            if (ways.isLiveNewHole(tunnel.tunnel().hole())) {
                list.add(tunnel.tunnel().hole());
            }
        }
        list.removeIf(hole -> colony.isHoleKeptShut(level, hole));
        return list;
    }

    // --- the holes along the tunnels -----------------------------------------------------------------------------

    /** Holes along every dug tunnel not laid out yet (the nest's own, the arms, the new tunnels to rich food). */
    void layOutHoles(ServerLevel level, List<NestPlan.ForagingTunnel> own) {
        List<NestPlan.ForagingTunnel> bases = new ArrayList<>(own);
        bases.addAll(g.web.arms);
        colony.nest().foodWays().getNewTunnels().forEach(tunnel -> bases.add(tunnel.tunnel()));
        List<NestPlan.ForagingTunnel> fresh = new ArrayList<>();
        for (NestPlan.ForagingTunnel base : bases) {
            if (laidOut.contains(base.hole()) || !ForagingHoles.isDug(level, base) || !allLoaded(level, base)) {
                continue;
            }
            fresh.addAll(holesAlong(level, base));
            laidOut.add(base.hole().immutable());
        }
        if (!fresh.isEmpty()) {
            holes.addAll(fresh);
            fresh.forEach(hole -> holesAlongSet.add(hole.hole()));
            g.changed.run();
            ColonyManager.get(level).markChanged();
        }
    }

    /** The holes along one dug tunnel (see {@link #holesAlong(NestPlan.ForagingTunnel, TunnelRoute.Ground, BlockPos, int, int, List)}). */
    private List<NestPlan.ForagingTunnel> holesAlong(ServerLevel level, NestPlan.ForagingTunnel base) {
        List<BlockPos> taken = new ArrayList<>();
        taken.add(colony.nest().getEntrance());
        colony.foragingHoles().tunnels(level).forEach(tunnel -> taken.add(tunnel.hole()));
        List<NestPlan.ForagingTunnel> found = holesAlong(base, new GroundReader(level, colony, colony.nest().nestCells(level)),
                colony.getCenter(), colony.profile().nest().foragingHoleDistance(), g.numbers().holeSpacing(), taken);
        if (!found.isEmpty()) {
            SavannaMod.LOGGER.debug("[galleries] {} holes along the tunnel to {}", found.size(), base.hole().toShortString());
        }
        return found;
    }

    /**
     * The holes along one tunnel, from its far end in: over a tunnel block whose roof is the top block of the ground (a
     * hole opens where it may), at least {@code nearest} blocks (sideways) from the middle, and {@code spacing} from
     * every hole in {@code taken} (the new ones are added to it). Each comes with the tunnel up to the block under it.
     * Pure (the gallery proof checks it on made-up landscapes).
     */
    static List<NestPlan.ForagingTunnel> holesAlong(NestPlan.ForagingTunnel base, TunnelRoute.Ground ground, BlockPos center,
            int nearest, int spacing, List<BlockPos> taken) {
        List<BlockPos> cells = base.tunnel();
        List<NestPlan.ForagingTunnel> found = new ArrayList<>();
        for (int i = cells.size() - 2; i >= 0; i--) {
            BlockPos cell = cells.get(i);
            BlockPos hole = cell.above();
            Integer top = ground.groundTop(hole.getX(), hole.getZ());
            if (cells.contains(hole) || top == null || top != hole.getY() || Galleries.flat(hole, center) < nearest
                    || Galleries.nearAny(hole, taken, spacing) || !ground.mayOpenHole(hole)) {
                continue;
            }
            found.add(new NestPlan.ForagingTunnel(List.copyOf(cells.subList(0, i + 1)), hole.immutable(), base.side()));
            taken.add(hole.immutable());
        }
        return found;
    }

    /**
     * Brain step 4g, step 3: the hole at the end of {@code left} was left for good (attacked again and again at
     * {@code spot}). A new hole is laid out as near the spot as may be but at least {@code from} blocks from it (sideways),
     * by the rules of the holes along the tunnels, never beside another hole: on a gallery that runs under the left hole
     * if one has room, otherwise on the nearest other gallery (a hole near the mound has no room on its own short piece).
     * Real Macrotermes give up a raided foraging site and open new holes along the galleries round it.
     */
    Instead openInstead(ServerLevel level, NestPlan.ForagingTunnel left, BlockPos spot, double from) {
        if (!g.isOn()) {
            return Instead.NO_ROOM;
        }
        BlockPos under = left.tunnel().get(left.tunnel().size() - 1);
        List<NestPlan.ForagingTunnel> bases = new ArrayList<>(colony.nest().ownForagingTunnels(level));
        bases.addAll(g.web.arms);
        colony.nest().foodWays().getNewTunnels().forEach(tunnel -> bases.add(tunnel.tunnel()));
        List<BlockPos> taken = new ArrayList<>();
        taken.add(colony.nest().getEntrance());
        colony.foragingHoles().tunnels(level).forEach(tunnel -> taken.add(tunnel.hole()));
        GroundReader ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        NestPlan.ForagingTunnel found = null;
        boolean unloaded = false;
        for (boolean same : new boolean[] {true, false}) { // the left hole's own galleries first
            for (NestPlan.ForagingTunnel base : bases) {
                if (base.tunnel().contains(under) != same) {
                    continue;
                }
                if (!allLoaded(level, base)) {
                    unloaded = true;
                    continue;
                }
                NestPlan.ForagingTunnel one = holeInstead(base, ground, colony.getCenter(), colony.profile().nest().foragingHoleDistance(), spot,
                        from, taken, cell -> colony.danger().isAttacked(level, cell));
                if (one != null && (found == null || Galleries.flat(one.hole(), spot) < Galleries.flat(found.hole(), spot))) {
                    found = one;
                }
            }
            if (found != null) {
                break;
            }
        }
        if (found == null && unloaded) {
            return Instead.NOT_LOADED;
        }
        if (found == null) {
            SavannaMod.LOGGER.info("[galleries] no room for a new hole in place of the one at {}", left.hole().toShortString());
            return Instead.NO_ROOM;
        }
        holes.add(found);
        holesAlongSet.add(found.hole());
        g.changed.run();
        ColonyManager.get(level).markChanged();
        SavannaMod.LOGGER.info("[galleries] a new hole at {} in place of the one left at {}, {} blocks from the attack",
                found.hole().toShortString(), left.hole().toShortString(), Math.round(Galleries.flat(found.hole(), spot)));
        return Instead.OPENED;
    }

    /**
     * A new hole along {@code base} in place of one left: over a tunnel block whose roof is the top block of the ground
     * (where a hole may open), at least {@code nearest} blocks (sideways) from the middle, at least {@code from} from
     * {@code spot} and the nearest such to it, not within {@link #BESIDE} of any hole in {@code taken}, and reached
     * without passing a {@code sealed} block (the gallery is sealed where the colony was attacked: a hole past it would
     * be closed at once). With the tunnel up to the block under it. Null if there is none. Pure (the gallery proof checks
     * it on made-up landscapes).
     */
    @Nullable
    static NestPlan.ForagingTunnel holeInstead(NestPlan.ForagingTunnel base, TunnelRoute.Ground ground, BlockPos center, int nearest,
            BlockPos spot, double from, List<BlockPos> taken, java.util.function.Predicate<BlockPos> sealed) {
        List<BlockPos> cells = base.tunnel();
        NestPlan.ForagingTunnel best = null;
        double bestOff = Double.MAX_VALUE;
        for (int i = 0; i < cells.size() && !sealed.test(cells.get(i)); i++) {
            BlockPos hole = cells.get(i).above();
            double off = Galleries.flat(hole, spot);
            if (off < from || off >= bestOff || cells.contains(hole) || Galleries.flat(hole, center) < nearest || Galleries.nearAny(hole, taken, BESIDE)
                    || sealed.test(hole)) {
                continue;
            }
            Integer top = ground.groundTop(hole.getX(), hole.getZ());
            if (top == null || top != hole.getY() || !ground.mayOpenHole(hole)) {
                continue;
            }
            best = new NestPlan.ForagingTunnel(List.copyOf(cells.subList(0, i + 1)), hole.immutable(), base.side());
            bestOff = off;
        }
        return best;
    }

    static boolean allLoaded(Level level, NestPlan.ForagingTunnel tunnel) {
        for (BlockPos cell : tunnel.tunnel()) {
            if (!level.isLoaded(cell)) {
                return false;
            }
        }
        return level.isLoaded(tunnel.hole());
    }

    // --- turns -----------------------------------------------------------------------------------------------------

    /**
     * A foraging time starts: the holes for it (see the class comment). {@code away}: picked from what the brain
     * remembers (the holes last seen dug, the food it knows), and the night's holes find the food the brain last saw
     * round them (step 4: a forager would have come across it there).
     */
    private void pickTurns(ServerLevel level, boolean away) {
        tonight.clear();
        long now = level.getGameTime();
        FoodWays ways = colony.nest().foodWays();
        List<BlockPos> candidates = new ArrayList<>();
        if (away) {
            for (BlockPos hole : g.upkeep.dugHoles) {
                if (!ways.isSpent(hole) && ways.runwayAt(hole) == null && !colony.isHoleKeptShut(level, hole)) {
                    candidates.add(hole);
                }
            }
            candidates.sort(Comparator.comparingLong(BlockPos::asLong)); // (the same order every time: the colony's own dice decide)
        } else {
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                BlockPos hole = tunnel.hole();
                if (level.isLoaded(hole) && ForagingHoles.isDug(level, tunnel) && !ways.isSpent(hole) && ways.runwayAt(hole) == null
                        && !candidates.contains(hole)) {
                    g.upkeep.dugHoles.add(hole.immutable());
                    if (!colony.isHoleKeptShut(level, hole)) { // (brain step 4g: an attacked hole has no turn)
                        candidates.add(hole);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        SavannaNumbers.GalleryNumbers numbers = g.numbers();
        int foragers = colony.foragersNow(level);
        boolean rain = colony.rainsNow(level);
        int open = Math.min(candidates.size(), numbers.holesFor(foragers, rain));
        Map<BlockPos, Double> food = away ? rememberedFoodNear(level, candidates, numbers.foodRadius())
                : foodNear(level, candidates, numbers.foodRadius());
        List<BlockPos> fed = candidates.stream().filter(hole -> food.getOrDefault(hole, 0.0) > 0)
                .sorted(Comparator.comparingDouble((BlockPos hole) -> -food.get(hole))).toList();
        // Rested longest first; holes never opened before first of all, in a shuffled order (the colony's own dice).
        List<BlockPos> rested = new ArrayList<>(candidates);
        for (int i = rested.size() - 1; i > 0; i--) {
            int j = colony.random().nextInt(i + 1);
            BlockPos swap = rested.get(i);
            rested.set(i, rested.get(j));
            rested.set(j, swap);
        }
        rested.sort(Comparator.comparingLong(hole -> lastTurn.getOrDefault(hole, Long.MIN_VALUE)));
        int fedTake = Math.min(open - numbers.exploreOf(open), fed.size());
        for (int i = 0; i < fedTake; i++) {
            tonight.add(fed.get(i));
        }
        // The far holes (the user's idea, step 4): the holes opened to explore come from the outer half of the web first,
        // so the termites probe new ground at its edge (real new holes probe new ground; what Matabele scouts find).
        double outer = g.web.reach() / 2.0;
        int explore = Math.min(open - tonight.size(), numbers.exploreOf(open));
        BlockPos center = colony.getCenter();
        for (BlockPos hole : rested) {
            if (explore <= 0) {
                break;
            }
            if (Galleries.flat(hole, center) >= outer && tonight.add(hole)) {
                explore--;
            }
        }
        for (BlockPos hole : rested) {
            if (tonight.size() >= open) {
                break;
            }
            tonight.add(hole);
        }
        tonight.forEach(hole -> lastTurn.put(hole, now));
        if (away) {
            colony.away().note(level, AwayReport.What.FORAGING_NIGHTS, 1);
            colony.away().note(level, AwayReport.What.HOLES_OPENED, tonight.size());
            colony.away().note(level, AwayReport.What.FOUND_FROM_HOLES, colony.field().findRound(level, tonight, numbers.foodRadius()));
        }
        SavannaMod.LOGGER.debug("[galleries] foraging starts: {} of {} holes open ({} foragers{}), {} near food: {}", tonight.size(),
                candidates.size(), foragers, rain ? ", rain" : "", fedTake, tonight.stream().map(BlockPos::toShortString).toList());
    }

    /** As {@link #foodNear}, from the plants the brain remembers (while away: no block is read). */
    private Map<BlockPos, Double> rememberedFoodNear(ServerLevel level, List<BlockPos> holes, int radius) {
        List<BlockPos> plants = colony.field().knownPlants();
        Map<BlockPos, Double> food = new HashMap<>();
        for (BlockPos hole : holes) {
            double count = 0;
            for (BlockPos plant : plants) {
                count += Galleries.flat(plant, hole) <= radius && Math.abs(plant.getY() - hole.getY()) <= radius ? 1 : 0;
            }
            food.put(hole, count);
        }
        for (ForagingTrails.Remembered trail : colony.trails().remembered(level)) {
            BlockPos hole = trail.exit().below();
            if (food.containsKey(hole) && Galleries.nearAny(trail.patch(), plants, PATCH_FOOD)) {
                food.merge(hole, trail.strength(), Double::sum);
            }
        }
        return food;
    }

    /**
     * How much food each hole leads to: the plants the colony knows within {@code radius} blocks of it, and the strength
     * of its remembered trail (see {@link ForagingTrails#remembered}) while known food is still there at its end.
     */
    Map<BlockPos, Double> foodNear(ServerLevel level, List<BlockPos> holes, int radius) {
        List<BlockPos> plants = colony.food().plants(level).stream().filter(plant -> colony.foodMap().knows(plant, null)).toList();
        Map<BlockPos, Double> food = new HashMap<>();
        for (BlockPos hole : holes) {
            double count = 0;
            for (BlockPos plant : plants) {
                count += Galleries.flat(plant, hole) <= radius && Math.abs(plant.getY() - hole.getY()) <= radius ? 1 : 0;
            }
            food.put(hole, count);
        }
        for (ForagingTrails.Remembered trail : colony.trails().remembered(level)) {
            BlockPos hole = trail.exit().below();
            if (food.containsKey(hole) && Galleries.nearAny(trail.patch(), plants, PATCH_FOOD)) {
                food.merge(hole, trail.strength(), Double::sum);
            }
        }
        return food;
    }

    /** The holes' part of the galleries' save (the names as before the split). */
    void save(CompoundTag out) {
        out.put("holes", Galleries.saveTunnels(holes));
        out.putLongArray("laidOut", laidOut.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag turns = new ListTag();
        lastTurn.forEach((hole, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("at", at);
            turns.add(entry);
        });
        out.put("lastTurn", turns);
        out.putLongArray("tonight", tonight.stream().mapToLong(BlockPos::asLong).toArray());
        out.putBoolean("inRound", inRound);
    }

    void load(CompoundTag in) {
        holes.clear();
        holes.addAll(Galleries.loadTunnels(in.getList("holes", Tag.TAG_COMPOUND)));
        laidOut.clear();
        for (long hole : in.getLongArray("laidOut")) {
            laidOut.add(BlockPos.of(hole));
        }
        lastTurn.clear();
        for (Tag element : in.getList("lastTurn", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            lastTurn.put(BlockPos.of(entry.getLong("hole")), entry.getLong("at"));
        }
        tonight.clear();
        for (long hole : in.getLongArray("tonight")) {
            tonight.add(BlockPos.of(hole));
        }
        inRound = in.getBoolean("inRound");
        holesAlongSet.clear();
        holes.forEach(hole -> holesAlongSet.add(hole.hole()));
    }
}
