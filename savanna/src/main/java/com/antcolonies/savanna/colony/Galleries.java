package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.TunnelRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.species.SavannaNumbers;

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
 *
 * <p>Apex part 3, T2: this class is the hub other code asks (the colony's {@link ColonyHooks.Underground}); the work is
 * done by four parts, one job each: the web's shape and growth ({@link GalleryWeb}), the holes along it and their turns
 * ({@link HoleTurns}), the upkeep (filling in, walling off, {@link GalleryUpkeep}) and the catch-up after coming back
 * ({@link GalleryCatchUp}). The save keeps its names.
 */
public class Galleries implements ColonyHooks.Underground {
    /** How often (ticks) the arms, the holes and the turns are looked at. */
    private static final int TICKS = 20;

    /** The numbers when the species file has none (only a test switching it on for a species without them). */
    private static final SavannaNumbers.GalleryNumbers DEFAULT = new SavannaNumbers.GalleryNumbers(true, 16, 4, 6, 0.5, 6, 2);

    final Colony colony;
    /** Told when the arms or the holes change (the nest's list of tunnels is out of date). */
    final Runnable changed;
    /** The four parts (see the class comment). */
    final GalleryWeb web;
    final HoleTurns turns;
    final GalleryUpkeep upkeep;
    final GalleryCatchUp catchUp;
    private long tickedAt = Long.MIN_VALUE;

    /** Tests only: the switch, whatever the species file says (null: as the file says). */
    @Nullable
    private Boolean switchForTests;

    Galleries(Colony colony) {
        this.colony = colony;
        this.changed = () -> colony.nest().waysChanged();
        this.web = new GalleryWeb(this);
        this.turns = new HoleTurns(this);
        this.upkeep = new GalleryUpkeep(this);
        this.catchUp = new GalleryCatchUp(this);
    }

    /** The split, step S2: this colony's galleries, or null (its species lists none: {@code "galleries"} in its parts). */
    @Nullable
    public static Galleries of(Colony colony) {
        return colony.part(SavannaParts.GALLERIES).orElse(null);
    }

    /** Whether the switch is on for this colony (the species file's {@code nest.galleries.enabled}). */
    @Override
    public boolean isOn() {
        if (switchForTests != null) {
            return switchForTests;
        }
        return colony.profile().section(SavannaNumbers.GALLERIES).map(SavannaNumbers.GalleryNumbers::enabled).orElse(false);
    }

    /** Tests only: switch it on or off for this colony whatever the species file says (null: as the file says). */
    public void setSwitchForTests(@Nullable Boolean on) {
        switchForTests = on;
        changed.run();
    }

    SavannaNumbers.GalleryNumbers numbers() {
        return colony.profile().section(SavannaNumbers.GALLERIES).orElse(DEFAULT);
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
        web.arms.stream().filter(arm -> ways.isLiveNewHole(arm.hole())).forEach(ordered::add);
        web.arms.stream().filter(arm -> !ways.isLiveNewHole(arm.hole())).forEach(ordered::add);
        return ordered;
    }

    /** Whether this hole is the end of one of the galleries' pieces (an arm, a piece grown from a tip, a piece out to food). */
    @Override
    public boolean hasPiece(BlockPos hole) {
        return isOn() && pieceAt(hole) != null;
    }

    /** Whether this hole is the end of one of the first arms (they never fill in). */
    public boolean isFirstArm(BlockPos hole) {
        return web.base.contains(hole);
    }

    /** The holes at the ends of every piece (for the tests). */
    public List<BlockPos> pieceHoles() {
        return web.arms.stream().map(NestPlan.ForagingTunnel::hole).toList();
    }

    /** The piece ending at this hole, or null. */
    @Nullable
    public NestPlan.ForagingTunnel pieceAt(BlockPos hole) {
        return web.pieceAt(hole);
    }

    /** Whether no other piece grows from this one. */
    public boolean isLeaf(BlockPos hole) {
        return web.isLeaf(hole);
    }

    /** The piece this one grew from, or null (a first arm, or a piece off the nest's own tunnels). */
    @Nullable
    public BlockPos parentOf(BlockPos hole) {
        return web.parentOf(hole);
    }

    /** See {@link GalleryWeb#digCells}. */
    @Override
    public List<BlockPos> digCells(NestPlan.ForagingTunnel tunnel) {
        return web.digCells(tunnel);
    }

    /** How many blocks long the galleries are in all (the arms' own blocks). */
    public int length() {
        return web.length();
    }

    /** How long the galleries may be with the colony's workers now (never shorter than the first arms). */
    public int lengthAllowed() {
        return web.lengthAllowed();
    }

    /** How many tips are growing (for the tests and the colony list). */
    public int tipCount() {
        return web.tips.size();
    }

    /** Whether the first arms are planned (for the tests). */
    public boolean armsPlanned() {
        return web.armsPlanned;
    }

    /** Tests only: the farthest a tip may grow (null: the species file's reach), to keep a web inside a test's arena. */
    public void setReachForTests(@Nullable Integer reach) {
        web.reachForTests = reach;
    }

    /** How far (blocks, sideways from the middle) the farthest hole of the galleries is; 0 while off or before there are any. */
    public int reach() {
        return web.reach();
    }

    /** See {@link GalleryWeb#scanSpots}. */
    @Override
    public List<int[]> scanSpots(Level level) {
        return web.scanSpots(level);
    }

    /** The colony's range with the galleries: the farthest hole and the graze round it (0: no galleries yet). */
    @Override
    public int range() {
        return web.range();
    }

    /** The cell of this colony's galleries (their own blocks) nearest {@code pos} within {@code within} blocks, or null. */
    @Nullable
    public BlockPos galleryCellNear(BlockPos pos, double within) {
        return web.galleryCellNear(pos, within);
    }

    /** How far from the middle food may lie for a piece to be dug out to it (see {@link GalleryWeb#foodReach}). */
    @Override
    public int foodReach() {
        return web.foodReach();
    }

    /** See {@link GalleryWeb#pieceTo}. */
    @Override
    @Nullable
    public ColonyHooks.GalleryPiece pieceTo(ServerLevel level, BlockPos food) {
        return web.pieceTo(level, food);
    }

    /** See {@link GalleryWeb#affords}. */
    @Override
    public boolean affords(ServerLevel level, ColonyHooks.GalleryPiece piece, BlockPos food) {
        return web.affords(level, piece, food);
    }

    /** See {@link GalleryWeb#addFoodPiece}. */
    @Override
    public void addFoodPiece(ServerLevel level, ColonyHooks.GalleryPiece piece) {
        web.addFoodPiece(level, piece);
    }

    /** The holes along the tunnels (none while switched off). */
    @Override
    public List<NestPlan.ForagingTunnel> holesAlong() {
        return isOn() ? turns.holes : List.of();
    }

    /** Whether this hole may be opened now (see {@link HoleTurns#isInTurn}). */
    @Override
    public boolean isInTurn(BlockPos hole) {
        return turns.isInTurn(hole);
    }

    /** The holes whose turn it is (for the tests and the colony list). */
    public Set<BlockPos> tonight() {
        return Set.copyOf(turns.tonight);
    }

    /** When this hole last had its turn, or {@link Colony#NOT_SET}. For the tests. */
    public long lastTurn(BlockPos hole) {
        return turns.lastTurn(hole);
    }

    /** The holes open to the foragers this foraging time (see {@link HoleTurns#holesInTurn}). */
    @Override
    public List<BlockPos> holesInTurn(Level level) {
        return turns.holesInTurn(level);
    }

    /** A new hole in place of one left for good (see {@link HoleTurns#openInstead}). */
    HoleTurns.Instead openInstead(ServerLevel level, NestPlan.ForagingTunnel left, BlockPos spot, double from) {
        return turns.openInstead(level, left, spot, from);
    }

    /** How many blocks of filled-in gallery only growth toward food may use again. */
    public int filledLength() {
        return upkeep.filledLength;
    }

    /** How many pieces were filled in so far. */
    public int fills() {
        return upkeep.fills;
    }

    /** When the piece ending at this hole last led to food, or {@link Colony#NOT_SET}. */
    public long lastUsed(BlockPos hole) {
        return upkeep.lastUsed(hole);
    }

    /** Tests only: every piece's last use moved this many ticks back, as if that long had gone by unused. */
    public void ageForTests(long ticks) {
        upkeep.ageForTests(ticks);
    }

    /** The holes the brain remembers as dug (it picks the night's holes from these while away). For tests. */
    public Set<BlockPos> dugHolesForTests() {
        return upkeep.dugHolesForTests();
    }

    /** Step 4: the numbers brought food in by this hole now (the piece it lies on counts as used). */
    @Override
    public void noteForaged(BlockPos hole, long now) {
        upkeep.noteForaged(hole, now);
    }

    /** How many gallery pieces were walled off against neighbours (for the tests and the list). */
    @Override
    public int walledOff() {
        return upkeep.walledOff();
    }

    /** Step 4: the colony is back (see {@link GalleryCatchUp#cameBack}). */
    @Override
    public void cameBack(ServerLevel level) {
        catchUp.cameBack(level);
    }

    /** Step 4: on return, the builder numbers' saved time grows the galleries (see {@link GalleryCatchUp#growNow}). */
    @Override
    public int[] growNow(ServerLevel level) {
        return catchUp.growNow(level);
    }

    @Override
    public int grazeRadius() {
        return numbers().foodRadius();
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
        web.adoptFoodTunnels(level);
        if (!web.armsPlanned) {
            web.planArms(level, own);
        }
        if (web.armsPlanned) {
            web.listenForWood(level);
            web.growTips(level);
        }
        turns.layOutHoles(level, own);
        if (upkeep.isTime(now)) {
            upkeep.upkeep(level);
        }
        turns.round(level, false);
    }

    /**
     * Step 4, once a second while away (from {@link AwayLife#tickAway}): the night's holes are picked when a foraging
     * time starts, as when here, from what the brain remembers (the holes last seen dug, the food it knows near them),
     * so the numbers graze round them and the night's holes stay on record. Never reads or changes a block.
     */
    void tickAway(ServerLevel level) {
        if (!isOn() || colony.isFounding() || upkeep.dugHoles.isEmpty()) {
            return;
        }
        turns.round(level, true);
    }

    // --- the colony list, saving -----------------------------------------------------------------------------------

    /** For the colony list: " | galleries: 4 arms, 12 holes, 3 open in turn". Empty while switched off. */
    public String describe(Level level) {
        if (!isOn()) {
            return "";
        }
        FoodWays ways = colony.nest().foodWays();
        long toFood = web.arms.stream().filter(arm -> ways.isLiveNewHole(arm.hole())).count();
        return " | galleries: " + web.arms.size() + " pieces (" + toFood + " out to food), " + web.tips.size() + " tips, " + length() + " of "
                + lengthAllowed() + " blocks, reach " + reach() + ", " + colony.foragingHoles().tunnels(level).size() + " holes, "
                + turns.tonight.size() + " in turn" + (turns.inRound ? "" : " (last time)")
                + (upkeep.fills > 0 ? ", " + upkeep.fills + " filled in (" + upkeep.filledBlocks + " blocks)" : "");
    }

    void save(CompoundTag tag) {
        CompoundTag out = new CompoundTag();
        web.save(out);
        turns.save(out);
        upkeep.save(out);
        tag.put("galleries", out);
    }

    void load(CompoundTag tag) {
        CompoundTag in = tag.getCompound("galleries");
        web.load(in);
        turns.load(in);
        upkeep.load(in);
        web.repairSharedHoles();
        changed.run();
    }

    static ListTag saveTunnels(List<NestPlan.ForagingTunnel> tunnels) {
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

    static List<NestPlan.ForagingTunnel> loadTunnels(ListTag list) {
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

    static boolean nearAny(BlockPos pos, List<BlockPos> others, double within) {
        for (BlockPos other : others) {
            if (flat(pos, other) < within) {
                return true;
            }
        }
        return false;
    }

    static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
