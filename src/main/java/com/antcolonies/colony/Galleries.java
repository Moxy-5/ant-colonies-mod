package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Termites underground (the user's plan of 2026-10-17, {@code docs/plans/termites-underground.md}), behind the species
 * file's switch ({@code nest.galleries.enabled}). Step 1, holes take turns, as real Macrotermes do: many small holes
 * along the galleries just under the savanna, only some opened on a night (near food found before, plus a few new ones
 * to explore), each plugged again before dawn, so the grazing spreads over the whole ground round the nest.
 *
 * <ul>
 * <li><b>The arms:</b> once the nest's own 2 foraging tunnels are dug, 4 arms reach out from them to about
 * {@code arm_length} blocks from the middle: the 2 side tunnels go on straight out, and 2 more branch off them past the
 * mound and run round to the front and the back (the user's pick: real galleries leave the nest every way, and the
 * nest's rooms lie ahead and behind, so the branches keep clear of them). Planned by {@link TunnelRoute}, dug like the
 * rest of the nest.</li>
 * <li><b>Holes along the tunnels:</b> once a tunnel is dug (the arms, the nest's own and the new tunnels to rich food),
 * holes are laid out along it about {@code hole_spacing} blocks apart, each bitten up through the tunnel's roof (the
 * top block of the ground over it), never under the mound. Each is a {@link NestPlan.ForagingTunnel} whose tunnel ends
 * under it, so every part that uses the holes (opening and plugging, the alarm, the sentries, runways, the numbers
 * foraging) works with them as it does with the others. Holes stay usable for ever (the user's decision).</li>
 * <li><b>Turns:</b> when a foraging time starts, the holes for it are picked: one per {@code foragers_per_hole}
 * foragers, more in the rain; the holes near food the colony knows (or with a remembered trail to food still there)
 * first, and {@code explore} go to the holes rested longest. Only those may be opened ({@link #isInTurn}); a hole with
 * a finished runway over it is not counted (it stays open under its runway).</li>
 * </ul>
 */
public class Galleries {
    /** How often (ticks) the arms, the holes and the turns are looked at. */
    private static final int TICKS = 20;
    /** A remembered trail counts for its hole while known food lies this near (blocks) its patch. */
    private static final double PATCH_FOOD = 4.0;
    /** The numbers when the species file has none (only a test switching it on for a species without them). */
    private static final SpeciesProfile.GalleryNumbers DEFAULT = new SpeciesProfile.GalleryNumbers(true, 16, 4, 6, 0.5, 6, 2);

    private final Colony colony;
    /** Told when the arms or the holes change (the nest's list of tunnels is out of date). */
    private final Runnable changed;
    /** The arms out from the nest's own tunnels, each with the part of the tunnel it leaves from. Saved. */
    private List<NestPlan.ForagingTunnel> arms = List.of();
    private boolean armsPlanned;
    /** The holes along the tunnels, each with the tunnel up to the block under it. Saved. */
    private final List<NestPlan.ForagingTunnel> holes = new ArrayList<>();
    /** The tunnels (by the hole at their end) whose holes along them are laid out. Saved. */
    private final Set<BlockPos> laidOut = new HashSet<>();
    /** When each hole last had its turn (game time). Saved. */
    private final Map<BlockPos, Long> lastTurn = new HashMap<>();
    /** The holes whose turn it is this foraging time. Saved. */
    private final Set<BlockPos> tonight = new LinkedHashSet<>();
    /** Whether a foraging time is going on (its holes are picked). Saved. */
    private boolean inRound;
    private long tickedAt = Long.MIN_VALUE;
    /** Tests only: the switch, whatever the species file says (null: as the file says). */
    @Nullable
    private Boolean switchForTests;

    Galleries(Colony colony, Runnable changed) {
        this.colony = colony;
        this.changed = changed;
    }

    /** Whether the switch is on for this colony (the species file's {@code nest.galleries.enabled}). */
    public boolean isOn() {
        if (switchForTests != null) {
            return switchForTests;
        }
        return colony.profile().nest().galleries().map(SpeciesProfile.GalleryNumbers::enabled).orElse(false);
    }

    /** Tests only: switch it on or off for this colony whatever the species file says (null: as the file says). */
    public void setSwitchForTests(@Nullable Boolean on) {
        switchForTests = on;
        changed.run();
    }

    private SpeciesProfile.GalleryNumbers numbers() {
        return colony.profile().nest().galleries().orElse(DEFAULT);
    }

    /** The arms (none while switched off). */
    List<NestPlan.ForagingTunnel> arms() {
        return isOn() ? arms : List.of();
    }

    /** The holes along the tunnels (none while switched off). */
    List<NestPlan.ForagingTunnel> holesAlong() {
        return isOn() ? holes : List.of();
    }

    /**
     * Whether this hole may be opened now: switched off, every hole; on, only the holes whose turn it is, and a hole a
     * runway of sheeting leaves from (its builders go out by it; once finished it stays open under the runway), and the
     * hole of a tunnel dug out to rich food while that food lasts (the colony dug it for that food: it is opened every
     * foraging time, also when the tunnel is finished after the night's turns were picked).
     */
    public boolean isInTurn(BlockPos hole) {
        FoodWays ways = colony.nest().foodWays();
        return !isOn() || tonight.contains(hole) || ways.runwayAt(hole) != null || ways.isLiveNewHole(hole);
    }

    /** The holes whose turn it is (for the tests and the colony list). */
    public Set<BlockPos> tonight() {
        return Set.copyOf(tonight);
    }

    /** When this hole last had its turn, or {@link Colony#NOT_SET}. For the tests. */
    public long lastTurn(BlockPos hole) {
        return lastTurn.getOrDefault(hole, Colony.NOT_SET);
    }

    /** Once a second (from {@link Colony#tick}): plan the arms, lay out holes along dug tunnels, pick the holes for a foraging time. */
    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (!isOn() || colony.isFounding() || (tickedAt != Long.MIN_VALUE && now - tickedAt < TICKS && now >= tickedAt)) {
            return;
        }
        tickedAt = now;
        List<NestPlan.ForagingTunnel> own = colony.nest().ownForagingTunnels(level);
        if (own.isEmpty()) {
            return;
        }
        if (!armsPlanned) {
            planArms(level, own);
        }
        layOutHoles(level, own);
        boolean foraging = colony.isForagingTime(level);
        if (foraging && !inRound) {
            inRound = true;
            pickTurns(level);
            ColonyManager.get(level).markChanged();
        } else if (!foraging && inRound) {
            inRound = false;
            ColonyManager.get(level).markChanged();
        }
    }

    // --- the arms ------------------------------------------------------------------------------------------------

    /** Once the nest's own tunnels are dug: the 4 arms out from them (an arm with no route is left out). */
    private void planArms(ServerLevel level, List<NestPlan.ForagingTunnel> own) {
        for (NestPlan.ForagingTunnel tunnel : own) {
            if (!ForagingHoles.isDug(level, tunnel)) {
                return;
            }
        }
        SpeciesProfile.GalleryNumbers numbers = numbers();
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
        arms = List.copyOf(planned);
        armsPlanned = true;
        changed.run();
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
                AntColoniesMod.LOGGER.debug("[galleries] no route for the arm to the {}", way);
                continue;
            }
            List<BlockPos> cells = new ArrayList<>(leadIn(from, plan.cells().get(0)));
            cells.addAll(plan.cells());
            planned.add(new NestPlan.ForagingTunnel(List.copyOf(cells), plan.hole(), way));
            AntColoniesMod.LOGGER.debug("[galleries] an arm to the {}: {} new blocks, hole at {}", way, plan.cells().size(), plan.hole().toShortString());
        }
        return planned;
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
            @Override
            public Integer groundTop(int x, int z) {
                return ground.groundTop(x, z);
            }

            @Override
            public boolean mayDig(BlockPos cell) {
                return flat(cell, center) >= radius && ground.mayDig(cell);
            }

            @Override
            public boolean mayOpenHole(BlockPos hole) {
                return flat(hole, center) >= radius && ground.mayOpenHole(hole);
            }

            @Override
            public boolean isDug(BlockPos cell) {
                return ground.isDug(cell);
            }
        };
    }

    // --- the holes along the tunnels -----------------------------------------------------------------------------

    /** Holes along every dug tunnel not laid out yet (the nest's own, the arms, the new tunnels to rich food). */
    private void layOutHoles(ServerLevel level, List<NestPlan.ForagingTunnel> own) {
        List<NestPlan.ForagingTunnel> bases = new ArrayList<>(own);
        bases.addAll(arms);
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
            changed.run();
            ColonyManager.get(level).markChanged();
        }
    }

    /** The holes along one dug tunnel (see {@link #holesAlong(NestPlan.ForagingTunnel, TunnelRoute.Ground, BlockPos, int, int, List)}). */
    private List<NestPlan.ForagingTunnel> holesAlong(ServerLevel level, NestPlan.ForagingTunnel base) {
        List<BlockPos> taken = new ArrayList<>();
        taken.add(colony.nest().getEntrance());
        colony.foragingHoles().tunnels(level).forEach(tunnel -> taken.add(tunnel.hole()));
        List<NestPlan.ForagingTunnel> found = holesAlong(base, new GroundReader(level, colony, colony.nest().nestCells(level)),
                colony.getCenter(), colony.profile().nest().foragingHoleDistance(), numbers().holeSpacing(), taken);
        if (!found.isEmpty()) {
            AntColoniesMod.LOGGER.debug("[galleries] {} holes along the tunnel to {}", found.size(), base.hole().toShortString());
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
            if (cells.contains(hole) || top == null || top != hole.getY() || flat(hole, center) < nearest
                    || nearAny(hole, taken, spacing) || !ground.mayOpenHole(hole)) {
                continue;
            }
            found.add(new NestPlan.ForagingTunnel(List.copyOf(cells.subList(0, i + 1)), hole.immutable(), base.side()));
            taken.add(hole.immutable());
        }
        return found;
    }

    private static boolean allLoaded(Level level, NestPlan.ForagingTunnel tunnel) {
        for (BlockPos cell : tunnel.tunnel()) {
            if (!level.isLoaded(cell)) {
                return false;
            }
        }
        return level.isLoaded(tunnel.hole());
    }

    // --- turns -----------------------------------------------------------------------------------------------------

    /** A foraging time starts: the holes for it (see the class comment). */
    private void pickTurns(ServerLevel level) {
        tonight.clear();
        long now = level.getGameTime();
        List<BlockPos> candidates = new ArrayList<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            BlockPos hole = tunnel.hole();
            if (level.isLoaded(hole) && ForagingHoles.isDug(level, tunnel) && !colony.nest().foodWays().isSpent(hole)
                    && colony.nest().foodWays().runwayAt(hole) == null && !candidates.contains(hole)) {
                candidates.add(hole);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        SpeciesProfile.GalleryNumbers numbers = numbers();
        int foragers = colony.foragersNow(level);
        boolean rain = colony.rainsNow(level);
        int open = Math.min(candidates.size(), numbers.holesFor(foragers, rain));
        Map<BlockPos, Double> food = foodNear(level, candidates, numbers.foodRadius());
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
        for (BlockPos hole : rested) {
            if (tonight.size() >= open) {
                break;
            }
            tonight.add(hole);
        }
        tonight.forEach(hole -> lastTurn.put(hole, now));
        AntColoniesMod.LOGGER.debug("[galleries] foraging starts: {} of {} holes open ({} foragers{}), {} near food: {}", tonight.size(),
                candidates.size(), foragers, rain ? ", rain" : "", fedTake, tonight.stream().map(BlockPos::toShortString).toList());
    }

    /**
     * How much food each hole leads to: the plants the colony knows within {@code radius} blocks of it, and the strength
     * of its remembered trail (see {@link ForagingTrails#remembered}) while known food is still there at its end.
     */
    private Map<BlockPos, Double> foodNear(ServerLevel level, List<BlockPos> holes, int radius) {
        List<BlockPos> plants = colony.food().plants(level).stream().filter(plant -> colony.foodMap().knows(plant, null)).toList();
        Map<BlockPos, Double> food = new HashMap<>();
        for (BlockPos hole : holes) {
            double count = 0;
            for (BlockPos plant : plants) {
                count += flat(plant, hole) <= radius && Math.abs(plant.getY() - hole.getY()) <= radius ? 1 : 0;
            }
            food.put(hole, count);
        }
        for (ForagingTrails.Remembered trail : colony.trails().remembered(level)) {
            BlockPos hole = trail.exit().below();
            if (food.containsKey(hole) && nearAny(trail.patch(), plants, PATCH_FOOD)) {
                food.merge(hole, trail.strength(), Double::sum);
            }
        }
        return food;
    }

    // --- the colony list, saving -----------------------------------------------------------------------------------

    /** For the colony list: " | galleries: 4 arms, 12 holes, 3 open in turn". Empty while switched off. */
    public String describe(Level level) {
        if (!isOn()) {
            return "";
        }
        return " | galleries: " + arms.size() + " arms, " + colony.foragingHoles().tunnels(level).size() + " holes, "
                + tonight.size() + " in turn" + (inRound ? "" : " (last time)");
    }

    void save(CompoundTag tag) {
        CompoundTag out = new CompoundTag();
        out.putBoolean("armsPlanned", armsPlanned);
        out.put("arms", saveTunnels(arms));
        out.put("holes", saveTunnels(holes));
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
        tag.put("galleries", out);
    }

    void load(CompoundTag tag) {
        CompoundTag in = tag.getCompound("galleries");
        armsPlanned = in.getBoolean("armsPlanned");
        arms = loadTunnels(in.getList("arms", Tag.TAG_COMPOUND));
        holes.clear();
        holes.addAll(loadTunnels(in.getList("holes", Tag.TAG_COMPOUND)));
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
        changed.run();
    }

    private static ListTag saveTunnels(List<NestPlan.ForagingTunnel> tunnels) {
        ListTag list = new ListTag();
        for (NestPlan.ForagingTunnel tunnel : tunnels) {
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("cells", tunnel.tunnel().stream().mapToLong(BlockPos::asLong).toArray());
            entry.putLong("hole", tunnel.hole().asLong());
            entry.putInt("side", tunnel.side().get3DDataValue());
            list.add(entry);
        }
        return list;
    }

    private static List<NestPlan.ForagingTunnel> loadTunnels(ListTag list) {
        List<NestPlan.ForagingTunnel> tunnels = new ArrayList<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            List<BlockPos> cells = new ArrayList<>();
            for (long cell : entry.getLongArray("cells")) {
                cells.add(BlockPos.of(cell));
            }
            if (!cells.isEmpty()) {
                tunnels.add(new NestPlan.ForagingTunnel(List.copyOf(cells), BlockPos.of(entry.getLong("hole")),
                        Direction.from3DDataValue(entry.getInt("side"))));
            }
        }
        return List.copyOf(tunnels);
    }

    private static boolean nearAny(BlockPos pos, List<BlockPos> others, double within) {
        for (BlockPos other : others) {
            if (flat(pos, other) < within) {
                return true;
            }
        }
        return false;
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
