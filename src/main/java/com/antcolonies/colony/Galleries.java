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
import net.minecraft.util.RandomSource;
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
 * <li><b>Step 2a, the galleries grow tip by tip</b> (the user's decisions of 2026-10-17): the arms' ends are the first
 * tips. Once everything planned is dug, the shortest tip grows by a piece of about {@code piece_length} blocks,
 * wandering a little and away from the other tips (the web spreads round the mound like spokes); after
 * {@code fork_min} to {@code fork_max} blocks of growth a tip forks in two. The galleries in all may be as long as the
 * colony's workers allow ({@code workers_per_block}; the arms are the least), no tip past {@code reach} blocks from the
 * middle. A tip with no route on 3 tries stops (a dead end). Each piece is a gallery with a hole at its end, and holes
 * are laid out along it as along the rest; the farthest hole sets the colony's range ({@link #range}).</li>
 * </ul>
 */
public class Galleries {
    /** How often (ticks) the arms, the holes and the turns are looked at. */
    private static final int TICKS = 20;
    /** A remembered trail counts for its hole while known food lies this near (blocks) its patch. */
    private static final double PATCH_FOOD = 4.0;
    /** The numbers when the species file has none (only a test switching it on for a species without them). */
    private static final SpeciesProfile.GalleryNumbers DEFAULT = new SpeciesProfile.GalleryNumbers(true, 16, 4, 6, 0.5, 6, 2);

    /** A tip with no route found this many times stops growing. */
    private static final int TIP_TRIES = 3;
    /** How many ways a piece may take are tried (the one farthest from the other tips is taken). */
    private static final int WAYS_TRIED = 4;
    /** The most a piece may turn away from straight out from the middle (degrees): galleries run outward. */
    private static final double MOST_OFF_OUTWARD = 60.0;

    /**
     * A growing tip: the gallery that ends there ({@code way}, from the nest's shaft to the tip, its hole at the tip),
     * the way it grows (degrees: 0 east, 90 south), the blocks grown since it last forked and when it forks next.
     */
    record Tip(NestPlan.ForagingTunnel way, double bearing, int sinceFork, int forkAt) {
        BlockPos end() {
            return way.tunnel().get(way.tunnel().size() - 1);
        }
    }

    private final Colony colony;
    /** Told when the arms or the holes change (the nest's list of tunnels is out of date). */
    private final Runnable changed;
    /** The arms out from the nest's own tunnels, each with the part of the tunnel it leaves from. Saved. */
    private List<NestPlan.ForagingTunnel> arms = List.of();
    private boolean armsPlanned;
    /** Where each arm's own blocks start in its tunnel (the blocks before are its parent's), by the arm's hole. Saved. */
    private final Map<BlockPos, Integer> newFrom = new HashMap<>();
    /** The growing tips (step 2a). Saved. */
    private final List<Tip> tips = new ArrayList<>();
    private boolean tipsMade;
    /** How long the first arms were (the galleries are never held shorter). Saved. */
    private int baseLength;
    /** Routes not found from each tip (by its hole); {@link #TIP_TRIES} and the tip stops. Not saved. */
    private final Map<BlockPos, Integer> tipFails = new HashMap<>();
    /** The holes along the tunnels (looked up by the digging). */
    private final Set<BlockPos> holesAlongSet = new HashSet<>();
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
    @Nullable
    private Integer reachForTests;

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

    SpeciesProfile.GalleryNumbers numbers() {
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
        if (armsPlanned) {
            growTips(level);
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
        for (NestPlan.ForagingTunnel arm : planned) {
            newFrom.put(arm.hole(), leadInLength(own, arm));
        }
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
        if (!isOn()) {
            return tunnel.tunnel();
        }
        if (holesAlongSet.contains(tunnel.hole())) {
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
        return Math.max(baseLength, numbers().lengthFor(colony.getCount(com.antcolonies.entity.WorkerAntEntity.CASTE)));
    }

    /** How many tips are growing (for the tests and the colony list). */
    public int tipCount() {
        return tips.size();
    }

    /** Tests only: the farthest a tip may grow (null: the species file's reach), to keep a web inside a test's arena. */
    public void setReachForTests(@Nullable Integer reach) {
        reachForTests = reach;
    }

    private int tipReach() {
        return reachForTests != null ? reachForTests : numbers().reach();
    }

    /** How far (blocks, sideways from the middle) the farthest hole of the galleries is; 0 while off or before there are any. */
    public int reach() {
        if (!isOn()) {
            return 0;
        }
        BlockPos center = colony.getCenter();
        double far = 0;
        for (NestPlan.ForagingTunnel arm : arms) {
            far = Math.max(far, flat(arm.hole(), center));
        }
        for (NestPlan.ForagingTunnel hole : holes) {
            far = Math.max(far, flat(hole.hole(), center));
        }
        return (int) Math.round(far);
    }

    /** The colony's range with the galleries: the farthest hole and the graze round it (0: no galleries yet). */
    public int range() {
        int reach = reach();
        return reach == 0 ? 0 : reach + numbers().foodRadius();
    }

    /** Once everything planned is dug and the colony may have more: the shortest tip grows by a piece (or forks). */
    private void growTips(ServerLevel level) {
        SpeciesProfile.GalleryNumbers numbers = numbers();
        BlockPos center = colony.getCenter();
        if (!tipsMade) {
            tips.clear();
            for (NestPlan.ForagingTunnel arm : arms) {
                tips.add(new Tip(arm, bearing(center, arm.hole()), 0, forkAt(numbers, colony.random())));
            }
            baseLength = length();
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
        List<Tip> order = new ArrayList<>(tips);
        shuffle(order, colony.random());
        Set<BlockPos> stopped = new HashSet<>();
        tipFails.forEach((hole, fails) -> {
            if (fails >= TIP_TRIES) {
                stopped.add(hole);
            }
        });
        Tip tip = pickTip(order, center, tipReach(), stopped);
        if (tip == null) {
            return;
        }
        int around = numbers.pieceLength() + TunnelRoute.HOLE_REACH + 2;
        BlockPos end = tip.end();
        if (!level.isLoaded(end.offset(around, 0, around)) || !level.isLoaded(end.offset(-around, 0, -around))
                || !level.isLoaded(end.offset(around, 0, -around)) || !level.isLoaded(end.offset(-around, 0, around))) {
            return; // (later, once the ground out there is loaded)
        }
        int keepOut = colony.profile().nest().foragingHoleDistance() + 1;
        TunnelRoute.Ground ground = new GroundReader(level, colony, colony.nest().nestCells(level));
        if (reachForTests != null) {
            numbers = new SpeciesProfile.GalleryNumbers(numbers.enabled(), numbers.armLength(), numbers.holeSpacing(), numbers.foragersPerHole(),
                    numbers.rainMore(), numbers.foodRadius(), numbers.explore(), numbers.workersPerBlock(), numbers.maxLength(), reachForTests,
                    numbers.pieceLength(), numbers.forkMin(), numbers.forkMax(), numbers.forkAngle(), numbers.wander(), numbers.hiddenTrip());
        }
        List<Tip> grown = grow(tip, tips, ground, center, keepOut, numbers, colony.random());
        if (grown.isEmpty()) {
            int fails = tipFails.merge(tip.way().hole(), 1, Integer::sum);
            if (fails >= TIP_TRIES) {
                tips.remove(tip); // a dead end: stone, water, a cliff or the edge of the reach all round
                ColonyManager.get(level).markChanged();
                AntColoniesMod.LOGGER.debug("[galleries] the tip at {} stops: no way on", end.toShortString());
            }
            return;
        }
        tips.remove(tip);
        tips.addAll(grown);
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(arms);
        for (Tip piece : grown) {
            all.add(piece.way());
            newFrom.put(piece.way().hole(), tip.way().tunnel().size());
        }
        arms = List.copyOf(all);
        changed.run();
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.debug("[galleries] the tip at {} grows{}: {} blocks of {} allowed", end.toShortString(),
                grown.size() > 1 ? " and forks" : "", length(), lengthAllowed());
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
            double distance = flat(tip.way().hole(), center);
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
            SpeciesProfile.GalleryNumbers numbers, RandomSource random) {
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
                if (flat(at, center) > numbers.reach() + TunnelRoute.HOLE_REACH) {
                    continue;
                }
                double clear = Double.MAX_VALUE;
                for (BlockPos other : others) {
                    clear = Math.min(clear, flat(at, other));
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
            if (plan == null || plan.cells().isEmpty() || flat(plan.hole(), center) > numbers.reach()) {
                continue;
            }
            List<BlockPos> cells = new ArrayList<>(tip.way().tunnel());
            cells.addAll(plan.cells());
            BlockPos last = plan.cells().get(plan.cells().size() - 1);
            double grewWay = flat(last, end) < 1 ? targetWay : bearing(end, last);
            NestPlan.ForagingTunnel piece = new NestPlan.ForagingTunnel(List.copyOf(cells), plan.hole(), sideOf(grewWay));
            grown.add(new Tip(piece, grewWay, fork ? 0 : tip.sinceFork() + plan.cells().size(),
                    fork ? forkAt(numbers, random) : tip.forkAt()));
            others.add(plan.hole());
        }
        return grown;
    }

    /** After how many blocks of growth a tip forks: {@code fork_min} to {@code fork_max}. */
    private static int forkAt(SpeciesProfile.GalleryNumbers numbers, RandomSource random) {
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
            fresh.forEach(hole -> holesAlongSet.add(hole.hole()));
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
        return " | galleries: " + arms.size() + " pieces, " + tips.size() + " tips, " + length() + " of " + lengthAllowed()
                + " blocks, reach " + reach() + ", " + colony.foragingHoles().tunnels(level).size() + " holes, "
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
            tipList.add(entry);
        }
        out.put("tips", tipList);
        out.putBoolean("tipsMade", tipsMade);
        out.putInt("baseLength", baseLength);
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
        holesAlongSet.clear();
        holes.forEach(hole -> holesAlongSet.add(hole.hole()));
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
                    new Tip(arm, entry.getDouble("bearing"), entry.getInt("sinceFork"), entry.getInt("forkAt"))));
        }
        tipsMade = in.getBoolean("tipsMade");
        baseLength = in.getInt("baseLength");
        tipFails.clear();
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
