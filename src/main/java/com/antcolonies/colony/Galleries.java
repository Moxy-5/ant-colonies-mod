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
 * <li><b>Step 3, growing toward food</b> (the user's decisions of 2026-10-17, all the real answers): food leads. Rich food
 * (a branch, a fallen tree, hay) the colony knows far from every hole gets a gallery piece straight to it from the nearest
 * dug gallery ({@link #pieceTo}, planned by {@link ForagingHoles} with its feeding gallery as before: one system, the
 * feeding spot stays on record in {@link FoodWays}); known grass just past a tip pulls that tip toward it
 * ({@code pull_reach}). Only then do the tips spread evenly. A tip stops at its food while the food lasts and may grow on
 * once it is eaten. Food spends the same length budget, and may use the length that filled-in branches gave back
 * (spreading may not).</li>
 * <li><b>Filling in:</b> a far piece of gallery is used while it leads to food (known food near one of its holes, a
 * feeding spot, a runway, a remembered trail; a piece beyond it that is used counts for it too). One that led to no food
 * for {@code fill_days} game days (counted while away too: game time runs on) is packed with soil, as real termites
 * backfill galleries that lead nowhere, from the far end back to its fork (a piece at a time, the last one first), once
 * nobody is in it, no traveller is on it, its holes are shut and no player can see it; its holes leave the web for good.
 * The 4 arms never fill in. The piece it forked from no longer spreads at random (that way led nowhere), only toward food.</li>
 * </ul>
 */
public class Galleries implements ColonyHooks.Underground {
    /** How often (ticks) the arms, the holes and the turns are looked at. */
    private static final int TICKS = 20;
    /** A remembered trail counts for its hole while known food lies this near (blocks) its patch. */
    private static final double PATCH_FOOD = 4.0;
    /** The numbers when the species file has none (only a test switching it on for a species without them). */
    private static final SpeciesProfile.GalleryNumbers DEFAULT = new SpeciesProfile.GalleryNumbers(true, 16, 4, 6, 0.5, 6, 2);

    /** How often (ticks) the pieces' use is looked at and unused far branches filled in. */
    private static final int UPKEEP_TICKS = 200;
    /** Game ticks in a day. */
    private static final long DAY = 24000L;

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
    /** Listens for big dead wood round the holes and tips (not saved: it listens again soon after a load). */
    private final DeadWoodCall woodCall;
    /** The holes of the first arms (they never fill in). Saved. */
    private final Set<BlockPos> base = new HashSet<>();
    /** Each piece's parent (by their holes; none for the first arms and pieces off the nest's own tunnels). Saved. */
    private final Map<BlockPos, BlockPos> parents = new HashMap<>();
    /** When each piece last led to food (game time, by its hole). Saved. */
    private final Map<BlockPos, Long> lastUsed = new HashMap<>();
    /** Blocks of gallery filled in and not grown again since: only growth toward food may use them. Saved. */
    private int filledLength;
    /** How many pieces were walled off against neighbours (step 5). Saved. */
    private int walledOff;
    /** How many pieces were filled in, and how many blocks (for the colony list). Saved. */
    private int fills;
    private int filledBlocks;
    private long upkeptAt = Long.MIN_VALUE;
    /**
     * The pieces whose branch beyond was filled in: that way led nowhere, so they no longer spread at random (real
     * termites do not dig again where they backfilled); food may still draw them on. Saved.
     */
    private final Set<BlockPos> backfilled = new HashSet<>();
    /**
     * Step 4: the holes last seen dug (looked at while here), from which the night's holes are picked while away (the
     * brain never reads unloaded land). Saved.
     */
    private final Set<BlockPos> dugHoles = new HashSet<>();
    /** Step 4: when the numbers last brought food in by each hole (a piece they forage from counts as used). Saved. */
    private final Map<BlockPos, Long> foragedFrom = new HashMap<>();
    /** Step 4: until when (game time) every branch due is filled in at once, just after coming back. Not saved. */
    private long catchUpUntil = Long.MIN_VALUE;
    /** How long (ticks) after coming back the branches due are filled in at once (then one per upkeep again). */
    private static final int CATCH_UP_TICKS = 600;
    /** At most this many branches are filled in a second while catching up. */
    private static final int CATCH_UP_FILLS = 20;
    /** Tests only: the switch, whatever the species file says (null: as the file says). */
    @Nullable
    private Boolean switchForTests;
    @Nullable
    private Integer reachForTests;

    Galleries(Colony colony) {
        this.colony = colony;
        this.changed = () -> colony.nest().waysChanged();
        this.woodCall = new DeadWoodCall(colony);
    }

    /** The split, step S2: this colony's galleries, or null (its species lists none: {@code "galleries"} in its parts). */
    @Nullable
    public static Galleries of(Colony colony) {
        return colony.part(ColonyParts.GALLERIES).orElse(null);
    }

    /** Whether the switch is on for this colony (the species file's {@code nest.galleries.enabled}). */
    @Override
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

    /**
     * The arms and every piece grown from them (none while switched off). The pieces out to food whose food lasts come
     * first: the diggers take the tunnels in this order, so the way to found food is dug before the rest.
     */
    @Override
    public List<NestPlan.ForagingTunnel> arms() {
        if (!isOn()) {
            return List.of();
        }
        FoodWays ways = colony.nest().foodWays();
        List<NestPlan.ForagingTunnel> ordered = new ArrayList<>();
        arms.stream().filter(arm -> ways.isLiveNewHole(arm.hole())).forEach(ordered::add);
        arms.stream().filter(arm -> !ways.isLiveNewHole(arm.hole())).forEach(ordered::add);
        return ordered;
    }

    /** Whether this hole is the end of one of the galleries' pieces (an arm, a piece grown from a tip, a piece out to food). */
    @Override
    public boolean hasPiece(BlockPos hole) {
        return isOn() && pieceAt(hole) != null;
    }

    /** Whether this hole is the end of one of the first arms (they never fill in). */
    public boolean isFirstArm(BlockPos hole) {
        return base.contains(hole);
    }

    /** The holes at the ends of every piece (for the tests). */
    public List<BlockPos> pieceHoles() {
        return arms.stream().map(NestPlan.ForagingTunnel::hole).toList();
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

    /** How many blocks of filled-in gallery only growth toward food may use again. */
    public int filledLength() {
        return filledLength;
    }

    /** How many pieces were filled in so far. */
    public int fills() {
        return fills;
    }

    /** When the piece ending at this hole last led to food, or {@link Colony#NOT_SET}. */
    public long lastUsed(BlockPos hole) {
        return lastUsed.getOrDefault(hole, Colony.NOT_SET);
    }

    /** Tests only: every piece's last use moved this many ticks back, as if that long had gone by unused. */
    public void ageForTests(long ticks) {
        lastUsed.replaceAll((hole, at) -> at - ticks);
        foragedFrom.replaceAll((hole, at) -> at - ticks);
        upkeptAt = Long.MIN_VALUE;
    }

    /** The holes the brain remembers as dug (it picks the night's holes from these while away). For tests. */
    public Set<BlockPos> dugHolesForTests() {
        return java.util.Collections.unmodifiableSet(dugHoles);
    }

    /** The holes along the tunnels (none while switched off). */
    @Override
    public List<NestPlan.ForagingTunnel> holesAlong() {
        return isOn() ? holes : List.of();
    }

    /**
     * Whether this hole may be opened now: switched off, every hole; on, only the holes whose turn it is, and a hole a
     * runway of sheeting leaves from (its builders go out by it; once finished it stays open under the runway), and the
     * hole of a tunnel dug out to rich food while that food lasts (the colony dug it for that food: it is opened every
     * foraging time, also when the tunnel is finished after the night's turns were picked).
     */
    @Override
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
        adoptFoodTunnels(level);
        if (!armsPlanned) {
            planArms(level, own);
        }
        if (armsPlanned) {
            listenForWood(level);
            growTips(level);
        }
        layOutHoles(level, own);
        if (upkeptAt == Long.MIN_VALUE || now - upkeptAt >= UPKEEP_TICKS || now < upkeptAt || now < catchUpUntil) {
            upkeptAt = now;
            upkeep(level);
        }
        round(level, false);
    }

    /**
     * Step 4, once a second while away (from {@link AwayLife#tickAway}): the night's holes are picked when a foraging
     * time starts, as when here, from what the brain remembers (the holes last seen dug, the food it knows near them),
     * so the numbers graze round them and the night's holes stay on record. Never reads or changes a block.
     */
    void tickAway(ServerLevel level) {
        if (!isOn() || colony.isFounding() || dugHoles.isEmpty()) {
            return;
        }
        round(level, true);
    }

    /** A foraging time starting picks its holes; one ending is noted. */
    private void round(ServerLevel level, boolean away) {
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
     * Step 4: the colony is back (from {@link AwayLife#settle}): for a while every branch due to fill in (10 days unused,
     * counted while away) is filled in as soon as it may be, as the real termites would have done meanwhile.
     */
    @Override
    public void cameBack(ServerLevel level) {
        if (isOn()) {
            catchUpUntil = level.getGameTime() + CATCH_UP_TICKS;
            upkeptAt = Long.MIN_VALUE;
        }
    }

    /**
     * The holes open to the foragers this foraging time: the night's turns, the holes runways leave from, and the holes of
     * pieces out to rich food whose food lasts (see {@link #isInTurn}); never a hole closed for danger (brain step 4g,
     * {@link ClosedHoles}). The numbers graze only round these (step 4).
     */
    @Override
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

    /** Step 4: the numbers brought food in by this hole now (the piece it lies on counts as used). */
    @Override
    public void noteForaged(BlockPos hole, long now) {
        foragedFrom.put(hole.immutable(), now);
    }

    @Override
    public int grazeRadius() {
        return numbers().foodRadius();
    }

    /**
     * Step 4: on return, the builder numbers' saved time grows the galleries ({@link BuildLife}): one step of growth now,
     * whatever the clock, by the same rules as here (the arms, the holes along dug tunnels, rich food first, then grass
     * pulling a tip, then the even spreading; within the workers' budget and the reach). Returns the pieces planned, of
     * them those toward food, and the blocks of gallery they add ({0, 0, 0}: nothing may grow now).
     */
    @Override
    public int[] growNow(ServerLevel level) {
        int[] none = {0, 0, 0};
        if (!isOn() || colony.isFounding()) {
            return none;
        }
        List<NestPlan.ForagingTunnel> own = colony.nest().ownForagingTunnels(level);
        if (own.isEmpty()) {
            return none;
        }
        Set<BlockPos> before = new HashSet<>();
        arms.forEach(arm -> before.add(arm.hole()));
        int length = length();
        adoptFoodTunnels(level);
        if (!armsPlanned) {
            planArms(level, own);
        }
        layOutHoles(level, own);
        if (armsPlanned) {
            colony.foragingHoles().checkNewHolesNow(level); // food first
            if (arms.size() == before.size()) {
                growTips(level);
            }
        }
        int pieces = 0;
        int toFood = 0;
        FoodWays ways = colony.nest().foodWays();
        for (NestPlan.ForagingTunnel arm : arms) {
            if (!before.contains(arm.hole())) {
                pieces++;
                boolean pulled = tips.stream().anyMatch(tip -> tip.pulled() && tip.way().hole().equals(arm.hole()));
                toFood += ways.isLiveNewHole(arm.hole()) || pulled ? 1 : 0;
            }
        }
        return new int[] {pieces, toFood, Math.max(0, length() - length)};
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
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(planned);
        all.addAll(arms); // (pieces out to food planned before the arms)
        arms = List.copyOf(all);
        for (NestPlan.ForagingTunnel arm : planned) {
            newFrom.put(arm.hole(), leadInLength(own, arm));
            base.add(arm.hole().immutable());
            lastUsed.put(arm.hole().immutable(), level.getGameTime());
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
    @Override
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
        return Math.max(baseLength, numbers().lengthFor(colony.getCount(Caste.WORKER)));
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

    /**
     * Step 4, the plant scan along the galleries: where food matters to the colony past the nest's own ground, each as
     * {x, z, reach}: round every hole (the graze), round every growing tip (the grass that may pull it) and round every
     * plant the colony knows (rich food a piece may be dug to).
     */
    @Override
    public List<int[]> scanSpots(Level level) {
        List<int[]> spots = new ArrayList<>();
        if (!isOn()) {
            return spots;
        }
        int graze = numbers().foodRadius() + 1;
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            spots.add(new int[] {tunnel.hole().getX(), tunnel.hole().getZ(), graze});
        }
        for (Tip tip : tips) {
            spots.add(new int[] {tip.end().getX(), tip.end().getZ(), numbers().pullReach() + 1});
        }
        for (BlockPos plant : colony.foodMap().knownPlants()) {
            spots.add(new int[] {plant.getX(), plant.getZ(), 1});
        }
        return spots;
    }

    /** The colony's range with the galleries: the farthest hole and the graze round it (0: no galleries yet). */
    @Override
    public int range() {
        int reach = reach();
        return reach == 0 ? 0 : reach + numbers().foodRadius();
    }

    /**
     * Big dead wood calls (the user's pick, 2026-10-19, see {@link DeadWoodCall}): round the dug holes and the growing
     * tips, dead wood big enough is sensed and the colony learns it, so a piece goes out to it before the tips spread.
     */
    private void listenForWood(ServerLevel level) {
        SpeciesProfile.WoodCall call = numbers().woodCall();
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
    private void growTips(ServerLevel level) {
        SpeciesProfile.GalleryNumbers numbers = numbers();
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
        if (length() + filledLength >= lengthAllowed()) {
            return; // the length filled-in branches gave back is for growth toward food only
        }
        List<Tip> order = new ArrayList<>(tips);
        shuffle(order, colony.random());
        Set<BlockPos> spreadStopped = new HashSet<>(stopped);
        spreadStopped.addAll(backfilled); // (a way that led nowhere is not explored again)
        long now = level.getGameTime();
        for (Tip each : tips) {
            if (isDue(each.way().hole(), now)) {
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
    private List<Colony> neighbourColonies(ServerLevel level) {
        List<Colony> near = new ArrayList<>();
        SpeciesProfile.NeighbourNumbers numbers = colony.neighbours().numbers();
        if (numbers == null) {
            return near;
        }
        double most = 2.0 * (numbers().reach() + numbers().pieceLength()) + numbers.within() + 8;
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
        double ahead = numbers().pieceLength() + TunnelRoute.HOLE_REACH;
        double way = Math.toRadians(tip.bearing());
        BlockPos next = BlockPos.containing(end.getX() + Math.cos(way) * ahead, end.getY(), end.getZ() + Math.sin(way) * ahead);
        return neighbours.yieldsAt(level, end) || neighbours.yieldsAt(level, next);
    }

    /** Of {@code cells}, the one that comes closest to {@code other}'s galleries within {@code within}, and their cell: {ours, theirs}; or null. */
    @Nullable
    private static BlockPos[] closestTo(Colony other, List<BlockPos> cells, double within) {
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
    private boolean galleriesMet(ServerLevel level, Colony other, BlockPos ours, BlockPos theirs, BlockPos piece) {
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

    /**
     * Step 5, in the upkeep: a piece at the web's edge within a piece's length of another colony's gallery is a meeting
     * (once a minute; real termites sense a neighbour's tunnels through the soil, so a young colony stopped short of
     * them keeps meeting them until it yields); a piece (not one of the
     * 4 arms, nothing growing from it) whose tip this colony yields at is walled off: packed with soil back to its fork,
     * as real neighbouring Macrotermes seal their galleries where they come close, by the same rules as filling in (out
     * of sight, nobody inside). Returns whether a piece was walled off.
     */
    private boolean wallOffNeighbours(ServerLevel level, Map<BlockPos, BlockPos> holeOf) {
        SpeciesProfile.NeighbourNumbers numbers = colony.neighbours().numbers();
        if (numbers == null || !numbers.wallGalleries()) {
            return false;
        }
        List<Colony> others = neighbourColonies(level);
        if (others.isEmpty()) {
            return false;
        }
        for (NestPlan.ForagingTunnel arm : List.copyOf(arms)) {
            if (!isLeaf(arm.hole())) {
                continue;
            }
            List<BlockPos> own = ownCells(arm);
            // (a tip stopped short of the neighbour still senses its tunnels through the soil, as far as its next piece would reach)
            double sense = numbers().pieceLength() + TunnelRoute.HOLE_REACH + numbers.within();
            for (Colony other : others) {
                BlockPos[] closest = closestTo(other, own, sense);
                if (closest == null || !galleriesMet(level, other, closest[0], closest[1], arm.hole())) {
                    continue;
                }
                BlockPos theirs = closest[1];
                if (base.contains(arm.hole()) || colony.nest().foodWays().isLiveNewHole(arm.hole())) {
                    continue; // (the 4 arms and a way to food in use are only stopped, never filled)
                }
                if (fillIn(level, arm, holeOf)) {
                    walledOff++;
                    AntColoniesMod.LOGGER.info("[neighbours] colony {}: the gallery to {} is walled off with soil (colony {}'s gallery at {})",
                            colony.getId(), arm.hole().toShortString(), other.getId(), theirs.toShortString());
                    return true;
                }
            }
        }
        return false;
    }

    /** How many gallery pieces were walled off against neighbours (for the tests and the list). */
    @Override
    public int walledOff() {
        return walledOff;
    }

    /** Whether the ground round a tip's end is loaded as far as its next piece may go. */
    private static boolean loadedAround(Level level, BlockPos end, SpeciesProfile.GalleryNumbers numbers) {
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
                AntColoniesMod.LOGGER.debug("[galleries] the tip at {} stops: no way on", tip.end().toShortString());
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
            lastUsed.put(hole, level.getGameTime());
            if (pulled) {
                filledLength = Math.max(0, filledLength - (piece.way().tunnel().size() - tip.way().tunnel().size()));
            }
        }
        arms = List.copyOf(all);
        changed.run();
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.debug("[galleries] the tip at {} grows{}{}: {} blocks of {} allowed", tip.end().toShortString(),
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
            foodNear(level, pulledHoles, numbers().foodRadius()).forEach((hole, food) -> {
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
    private boolean pullTowardGrass(ServerLevel level, SpeciesProfile.GalleryNumbers numbers, BlockPos center, Set<BlockPos> stopped) {
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
        grass.removeIf(plant -> nearAny(plant, holes, numbers.foodRadius() + 0.5));
        if (grass.isEmpty()) {
            return false;
        }
        Tip best = null;
        double bestWorth = 0;
        double aimX = 0;
        double aimZ = 0;
        for (Tip tip : tips) {
            BlockPos hole = tip.way().hole();
            double out = flat(hole, center);
            if (stopped.contains(hole) || out >= tipReach() - 1) {
                continue;
            }
            double worth = 0;
            double x = 0;
            double z = 0;
            for (BlockPos plant : grass) {
                if (flat(plant, hole) <= numbers.pullReach() && flat(plant, center) > out + 1) {
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
        AntColoniesMod.LOGGER.debug("[galleries] grass worth {} past the tip at {} pulls it toward {}, {}", Math.round(bestWorth),
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
    @Override
    public int foodReach() {
        return tipReach();
    }

    /**
     * A piece of gallery out to rich food at {@code food} (step 3; {@link ForagingHoles} picks the food, checks the
     * workers allow it ({@link #affords}) and plans its feeding gallery), from the nearest dug gallery, or null: no route.
     */
    @Nullable
    @Override
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
        return pieceAt(hole) != null || holesAlongSet.contains(hole);
    }

    /**
     * Whether the workers allow this piece out to {@code food} now: the galleries with it no longer than they allow and
     * one piece's length over (the length filled-in branches gave back may be used for food); for big dead wood (see
     * {@link DeadWoodCall}) up to the call's {@code over_limit} more (the user's pick, 2026-10-19: a big find gets the
     * diggers; the even spreading waits until the workers catch up, {@link #growTips}).
     */
    @Override
    public boolean affords(ServerLevel level, ColonyHooks.GalleryPiece piece, BlockPos food) {
        int fresh = piece.tunnel().tunnel().size() - piece.from();
        int allowed = lengthAllowed() + numbers().pieceLength();
        if (length() + fresh <= allowed) {
            return true;
        }
        SpeciesProfile.WoodCall call = numbers().woodCall();
        if (call.overLimit() > 0 && length() + fresh <= allowed + call.overLimit() && woodCall.isBig(level, food, call)) {
            AntColoniesMod.LOGGER.debug("[galleries] the way to the big dead wood at {} is {} blocks: past what the workers allow ({} of {}), dug all the same",
                    food.toShortString(), fresh, length(), lengthAllowed());
            return true;
        }
        AntColoniesMod.LOGGER.debug("[galleries] the way to the food at {} is {} blocks: more than the workers allow now ({} of {})",
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
    @Override
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
        lastUsed.put(hole, level.getGameTime());
        if (tipsMade) {
            tips.add(new Tip(piece.tunnel(), bearing(colony.getCenter(), hole), 0, forkAt(numbers(), colony.random()), true));
        }
        filledLength = Math.max(0, filledLength - (piece.tunnel().tunnel().size() - piece.from()));
        changed.run();
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.debug("[galleries] a piece of {} blocks out to food, hole at {}{}", piece.tunnel().tunnel().size() - piece.from(),
                hole.toShortString(), parent == null ? " (off the nest's own tunnel)" : " (from the piece to " + parent.toShortString() + ")");
    }

    /**
     * The tunnels dug out to rich food before step 3 (or while the switch was off) join the web as pieces: their
     * feeding spots stay as they are (the user's decision: one system).
     */
    private void adoptFoodTunnels(ServerLevel level) {
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
            lastUsed.put(hole.immutable(), level.getGameTime());
            if (tipsMade) {
                tips.add(new Tip(tunnel.tunnel(), bearing(colony.getCenter(), hole), 0, forkAt(numbers(), colony.random()), true));
            }
            adopted = true;
            AntColoniesMod.LOGGER.debug("[galleries] the tunnel to the food at {} joins the web", tunnel.food().toShortString());
        }
        if (adopted) {
            changed.run();
            ColonyManager.get(level).markChanged();
        }
    }

    // --- step 3: using and filling in -------------------------------------------------------------------------------

    /**
     * Every few seconds: each piece that leads to food now (a feeding spot whose food lasts, a runway from one of its
     * holes, known food or a remembered trail to food at one of its holes) is used, and so is every piece it grew from;
     * then one far piece unused for {@code fill_days} days is filled in, if it may be now.
     */
    private void upkeep(ServerLevel level) {
        long now = level.getGameTime();
        FoodWays ways = colony.nest().foodWays();
        Map<BlockPos, BlockPos> holeOf = new HashMap<>(); // each hole (the ends and the holes along) to its piece
        for (NestPlan.ForagingTunnel arm : arms) {
            holeOf.put(arm.hole(), arm.hole());
            lastUsed.putIfAbsent(arm.hole().immutable(), now); // (an older save: counted from now)
            Long at = lastUsed.get(arm.hole());
            if (at > now) {
                lastUsed.put(arm.hole().immutable(), now); // (the game's clock was set back)
            }
        }
        Map<BlockPos, BlockPos> cellOf = new HashMap<>();
        for (NestPlan.ForagingTunnel arm : arms) {
            ownCells(arm).forEach(cell -> cellOf.putIfAbsent(cell, arm.hole()));
        }
        for (NestPlan.ForagingTunnel along : holes) {
            BlockPos piece = cellOf.get(along.hole().below());
            if (piece != null) {
                holeOf.put(along.hole(), piece);
            }
        }
        Map<BlockPos, Double> food = foodNear(level, new ArrayList<>(holeOf.keySet()), numbers().foodRadius());
        Set<BlockPos> used = new HashSet<>();
        holeOf.forEach((hole, piece) -> {
            if (ways.isLiveNewHole(hole) || ways.runwayAt(hole) != null || food.getOrDefault(hole, 0.0) > 0) {
                used.add(piece);
            }
        });
        for (BlockPos piece : used) {
            markUsed(piece, now);
        }
        // Step 4: a piece the numbers foraged from (while away too) was in use then.
        foragedFrom.keySet().removeIf(hole -> !holeOf.containsKey(hole));
        foragedFrom.forEach((hole, at) -> markUsed(holeOf.get(hole), Math.min(at, now)));
        rememberDugHoles(level);
        if (wallOffNeighbours(level, holeOf)) {
            return; // (one piece at a time)
        }
        if (numbers().fillDays() <= 0) {
            return;
        }
        List<NestPlan.ForagingTunnel> due = new ArrayList<>();
        for (NestPlan.ForagingTunnel arm : arms) {
            if (isDue(arm.hole(), now)) {
                due.add(arm);
            }
        }
        // The farthest first: the web is filled in from its far ends back.
        BlockPos center = colony.getCenter();
        due.sort(Comparator.comparingDouble((NestPlan.ForagingTunnel arm) -> -flat(arm.hole(), center)));
        boolean catchingUp = now < catchUpUntil;
        int filled = 0;
        for (NestPlan.ForagingTunnel piece : due) {
            if (pieceAt(piece.hole()) == null) {
                continue; // (filled in just now)
            }
            int blocks = filledBlocks;
            if (fillIn(level, piece, holeOf)) {
                if (!catchingUp) {
                    return; // one piece at a time
                }
                // Just back (step 4): every branch due is filled in at once, as it would have been while away.
                colony.away().report().add(AwayReport.What.FILLED_IN, 1);
                colony.away().report().add(AwayReport.What.FILLED_BLOCKS, filledBlocks - blocks);
                if (++filled >= CATCH_UP_FILLS) {
                    return;
                }
            }
        }
    }

    /**
     * Whether the piece ending at this hole is due to be filled in: a far piece no other grows from, not one of the 4
     * arms nor a tunnel out to food that lasts, that led to no food for {@code fill_days} days.
     */
    private boolean isDue(BlockPos hole, long now) {
        Long at = lastUsed.get(hole);
        return numbers().fillDays() > 0 && at != null && pieceAt(hole) != null && !base.contains(hole) && isLeaf(hole)
                && !colony.nest().foodWays().isLiveNewHole(hole) && now - at >= numbers().fillDays() * DAY;
    }

    /** The piece ending at this hole and every piece it grew from were in use at {@code at}. */
    private void markUsed(@Nullable BlockPos piece, long at) {
        for (int up = 0; piece != null && up < 1000; up++) {
            if (lastUsed.getOrDefault(piece, Long.MIN_VALUE) < at) {
                lastUsed.put(piece.immutable(), at);
            }
            piece = parents.get(piece);
        }
    }

    /** The holes whose tunnels are dug, as seen now (loaded ones only), for picking the night's holes while away. */
    private void rememberDugHoles(ServerLevel level) {
        Set<BlockPos> all = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            BlockPos hole = tunnel.hole();
            all.add(hole);
            if (allLoaded(level, tunnel)) {
                if (ForagingHoles.isDug(level, tunnel)) {
                    dugHoles.add(hole.immutable());
                } else {
                    dugHoles.remove(hole);
                }
            }
        }
        dugHoles.retainAll(all);
    }

    /**
     * Packs one unused far piece with soil and takes it out of the web for good (step 3, the user's decision: real
     * termites backfill galleries that lead nowhere), if it may be now: every block loaded, its holes shut, nobody in it
     * or travelling unseen through it, and no player able to see any of it (it is done out of sight, all at once).
     */
    private boolean fillIn(ServerLevel level, NestPlan.ForagingTunnel piece, Map<BlockPos, BlockPos> holeOf) {
        BlockPos end = piece.hole();
        List<BlockPos> holesOfPiece = new ArrayList<>();
        holeOf.forEach((hole, of) -> {
            if (of.equals(end)) {
                holesOfPiece.add(hole);
            }
        });
        List<BlockPos> cells = new ArrayList<>(ownCells(piece));
        FoodWays.NewTunnel toFood = colony.nest().foodWays().newTunnelAt(end);
        if (toFood != null && toFood.gallery() != null) {
            cells.addAll(toFood.gallery());
        }
        // Blocks another tunnel (the nest's own, another piece, another feeding gallery) runs through stay open.
        Set<BlockPos> others = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.nest().ownForagingTunnels(level)) {
            others.addAll(tunnel.tunnel());
        }
        for (NestPlan.ForagingTunnel arm : arms) {
            if (!arm.hole().equals(end)) {
                others.addAll(ownCells(arm));
            }
        }
        for (FoodWays.NewTunnel tunnel : colony.nest().foodWays().getNewTunnels()) {
            if (!tunnel.tunnel().hole().equals(end)) {
                others.addAll(tunnel.tunnel().tunnel());
                if (tunnel.gallery() != null) {
                    others.addAll(tunnel.gallery());
                }
            }
        }
        cells.removeIf(others::contains);
        if (cells.isEmpty()) {
            return false;
        }
        for (BlockPos cell : cells) {
            if (!level.isLoaded(cell)) {
                return false;
            }
        }
        for (BlockPos hole : holesOfPiece) {
            if (!level.isLoaded(hole) || ForagingHoles.isOpen(level, hole)) {
                return false; // (shut tonight, or once the foragers are in)
            }
        }
        Set<BlockPos> inside = new HashSet<>(cells);
        inside.addAll(holesOfPiece);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : inside) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1);
        for (net.minecraft.world.entity.LivingEntity mob : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                net.minecraft.world.entity.LivingEntity::isAlive)) {
            BlockPos at = mob.blockPosition();
            if (inside.contains(at) || inside.contains(at.above()) || inside.contains(at.below())) {
                return false; // never wall an insect in (docs/BUGS.md 8, 9, 24, 42)
            }
        }
        for (ColonyHooks.HiddenAway away : colony.hooks().all(ColonyHooks.HIDDEN_AWAY)) {
            if (away.anyWalksThrough(inside)) {
                return false; // (an insect on a hidden trip walks through there)
            }
        }
        List<net.minecraft.world.phys.Vec3> eyes = colony.population().viewersInSight(level);
        if (!eyes.isEmpty()) {
            for (BlockPos cell : inside) {
                if (!Population.outOfSight(level, cell, eyes)) {
                    return false;
                }
            }
        }
        int filled = 0;
        for (BlockPos cell : cells) {
            if (Nest.isDugOut(level, cell)) {
                level.setBlockAndUpdate(cell, soilFor(level, cell));
                filled++;
            }
        }
        int own = ownCellsCount(piece);
        removePiece(level, piece, holesOfPiece);
        filledLength += own;
        fills++;
        filledBlocks += filled;
        AntColoniesMod.LOGGER.debug("[galleries] the piece to {} led to no food for {} days: filled in ({} blocks packed with soil)",
                end.toShortString(), numbers().fillDays(), filled);
        return true;
    }

    /** The soil a filled-in block is packed with: the ground round it where that is soil the termites dig, else dirt. */
    private static net.minecraft.world.level.block.state.BlockState soilFor(ServerLevel level, BlockPos cell) {
        for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos next = cell.relative(side);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(next);
            if (state.is(com.antcolonies.registry.ModTags.DIGGABLE) && state.isCollisionShapeFullBlock(level, next) && !state.hasBlockEntity()) {
                return state.getBlock() == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                        ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : state.getBlock().defaultBlockState();
            }
        }
        return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
    }

    private int ownCellsCount(NestPlan.ForagingTunnel piece) {
        return piece.tunnel().size() - Math.min(piece.tunnel().size(), newFrom.getOrDefault(piece.hole(), 0));
    }

    /** A filled-in piece leaves the web with its holes (they are never opened again); the piece it grew from may grow on. */
    private void removePiece(ServerLevel level, NestPlan.ForagingTunnel piece, List<BlockPos> holesOfPiece) {
        BlockPos end = piece.hole();
        List<NestPlan.ForagingTunnel> all = new ArrayList<>(arms);
        all.remove(piece);
        arms = List.copyOf(all);
        newFrom.remove(end);
        lastUsed.remove(end);
        tipFails.remove(end);
        tips.removeIf(tip -> tip.way().hole().equals(end));
        laidOut.remove(end);
        holes.removeIf(along -> holesOfPiece.contains(along.hole()));
        for (BlockPos hole : holesOfPiece) {
            holesAlongSet.remove(hole);
            lastTurn.remove(hole);
            tonight.remove(hole);
        }
        colony.nest().foodWays().dropNewTunnel(end);
        BlockPos parent = parents.remove(end);
        if (parent != null && tipsMade && isLeaf(parent) && tips.stream().noneMatch(tip -> tip.way().hole().equals(parent))) {
            NestPlan.ForagingTunnel of = pieceAt(parent);
            if (of != null) {
                tips.add(new Tip(of, bearing(colony.getCenter(), parent), 0, forkAt(numbers(), colony.random())));
            }
        }
        if (parent != null) {
            backfilled.add(parent);
        }
        backfilled.remove(end);
        changed.run();
        ColonyManager.get(level).markChanged();
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
            if (plan.hole().equals(tip.way().hole()) || others.contains(plan.hole())) {
                continue; // (a hole the web has already: two pieces may never end at one hole)
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

    /** What came of looking for a new hole in place of a left one (brain step 4g, {@link #openInstead}). */
    enum Instead {
        OPENED, NO_ROOM, NOT_LOADED
    }

    /** A new hole is never laid out closer than this (blocks, sideways) to another hole. */
    private static final double BESIDE = 2.0;

    /**
     * Brain step 4g, step 3: the hole at the end of {@code left} was left for good (attacked again and again at
     * {@code spot}). A new hole is laid out as near the spot as may be but at least {@code from} blocks from it (sideways),
     * by the rules of the holes along the tunnels, never beside another hole: on a gallery that runs under the left hole
     * if one has room, otherwise on the nearest other gallery (a hole near the mound has no room on its own short piece).
     * Real Macrotermes give up a raided foraging site and open new holes along the galleries round it.
     */
    Instead openInstead(ServerLevel level, NestPlan.ForagingTunnel left, BlockPos spot, double from) {
        if (!isOn()) {
            return Instead.NO_ROOM;
        }
        BlockPos under = left.tunnel().get(left.tunnel().size() - 1);
        List<NestPlan.ForagingTunnel> bases = new ArrayList<>(colony.nest().ownForagingTunnels(level));
        bases.addAll(arms);
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
                if (one != null && (found == null || flat(one.hole(), spot) < flat(found.hole(), spot))) {
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
            AntColoniesMod.LOGGER.info("[galleries] no room for a new hole in place of the one at {}", left.hole().toShortString());
            return Instead.NO_ROOM;
        }
        holes.add(found);
        holesAlongSet.add(found.hole());
        changed.run();
        ColonyManager.get(level).markChanged();
        AntColoniesMod.LOGGER.info("[galleries] a new hole at {} in place of the one left at {}, {} blocks from the attack",
                found.hole().toShortString(), left.hole().toShortString(), Math.round(flat(found.hole(), spot)));
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
            double off = flat(hole, spot);
            if (off < from || off >= bestOff || cells.contains(hole) || flat(hole, center) < nearest || nearAny(hole, taken, BESIDE)
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

    private static boolean allLoaded(Level level, NestPlan.ForagingTunnel tunnel) {
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
            for (BlockPos hole : dugHoles) {
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
                    dugHoles.add(hole.immutable());
                    if (!colony.isHoleKeptShut(level, hole)) { // (brain step 4g: an attacked hole has no turn)
                        candidates.add(hole);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        SpeciesProfile.GalleryNumbers numbers = numbers();
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
        double outer = reach() / 2.0;
        int explore = Math.min(open - tonight.size(), numbers.exploreOf(open));
        BlockPos center = colony.getCenter();
        for (BlockPos hole : rested) {
            if (explore <= 0) {
                break;
            }
            if (flat(hole, center) >= outer && tonight.add(hole)) {
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
        AntColoniesMod.LOGGER.debug("[galleries] foraging starts: {} of {} holes open ({} foragers{}), {} near food: {}", tonight.size(),
                candidates.size(), foragers, rain ? ", rain" : "", fedTake, tonight.stream().map(BlockPos::toShortString).toList());
    }

    /** As {@link #foodNear}, from the plants the brain remembers (while away: no block is read). */
    private Map<BlockPos, Double> rememberedFoodNear(ServerLevel level, List<BlockPos> holes, int radius) {
        List<BlockPos> plants = colony.field().knownPlants();
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
        FoodWays ways = colony.nest().foodWays();
        long toFood = arms.stream().filter(arm -> ways.isLiveNewHole(arm.hole())).count();
        return " | galleries: " + arms.size() + " pieces (" + toFood + " out to food), " + tips.size() + " tips, " + length() + " of "
                + lengthAllowed() + " blocks, reach " + reach() + ", " + colony.foragingHoles().tunnels(level).size() + " holes, "
                + tonight.size() + " in turn" + (inRound ? "" : " (last time)")
                + (fills > 0 ? ", " + fills + " filled in (" + filledBlocks + " blocks)" : "");
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
        ListTag usedList = new ListTag();
        lastUsed.forEach((hole, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("at", at);
            usedList.add(entry);
        });
        out.put("lastUsed", usedList);
        out.putInt("filledLength", filledLength);
        out.putInt("fills", fills);
        out.putInt("walledOff", walledOff);
        out.putInt("filledBlocks", filledBlocks);
        out.putLongArray("backfilled", backfilled.stream().mapToLong(BlockPos::asLong).toArray());
        out.putLongArray("dugHoles", dugHoles.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag foraged = new ListTag();
        foragedFrom.forEach((hole, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("hole", hole.asLong());
            entry.putLong("at", at);
            foraged.add(entry);
        });
        out.put("foragedFrom", foraged);
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
        lastUsed.clear();
        for (Tag element : in.getList("lastUsed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            lastUsed.put(BlockPos.of(entry.getLong("hole")), entry.getLong("at"));
        }
        filledLength = in.getInt("filledLength");
        fills = in.getInt("fills");
        walledOff = in.getInt("walledOff");
        filledBlocks = in.getInt("filledBlocks");
        backfilled.clear();
        for (long hole : in.getLongArray("backfilled")) {
            backfilled.add(BlockPos.of(hole));
        }
        dugHoles.clear();
        for (long hole : in.getLongArray("dugHoles")) {
            dugHoles.add(BlockPos.of(hole));
        }
        foragedFrom.clear();
        for (Tag element : in.getList("foragedFrom", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            foragedFrom.put(BlockPos.of(entry.getLong("hole")), entry.getLong("at"));
        }
        upkeptAt = Long.MIN_VALUE;
        tipFails.clear();
        repairSharedHoles();
        changed.run();
    }

    /**
     * A save from before the fix of 2026-10-17 may hold two pieces ending at one hole (a tip grew a piece whose hole was
     * its own): the longer one is kept (it holds the other's blocks), counted from the piece it really grew from.
     */
    private void repairSharedHoles() {
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
        AntColoniesMod.LOGGER.info("[galleries] repaired pieces sharing a hole: {} pieces now", arms.size());
    }

    /**
     * A save from before step 3: each piece's parent is the piece whose whole tunnel its own starts with, and the first
     * arms are the ones with none (the pieces' last use is counted from now, see {@link #upkeep}).
     */
    private void deriveOlderSave() {
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
