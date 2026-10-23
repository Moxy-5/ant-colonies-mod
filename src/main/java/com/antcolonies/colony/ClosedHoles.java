package com.antcolonies.colony;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Brain step 4g, step 3: termites plug and close (the user's decisions of 2026-10-17 in {@code docs/DESIGN.md}, "4g
 * decisions"; plan {@code docs/plans/4g-danger-neighbours.md}). Real Macrotermes attacked at a foraging site (by Matabele
 * ants, an aardvark, a person) pack the holes there with soil from inside and seal the gallery behind them, and forage
 * elsewhere for a while; a site raided again and again is given up, and new holes are opened farther along.
 *
 * <ul>
 * <li><b>Closed:</b> a foraging hole within {@code reach} blocks of a place where members were attacked (hurt or killed
 * by a living thing, not only a hostile seen: {@link DangerMap#isAttacked}), or one whose gallery out from the nest runs
 * that near one (the gallery is sealed there, so everything beyond it is cut off). A closed hole is plugged at once,
 * whatever the hour ({@link ForagingHoles#wantsPlugged}), never bitten open, no way out, has no turn
 * ({@link Galleries#pickTurns}) and draws no growing tip; no hidden trip runs through the gallery near the attack (the
 * insects walk it for real); the food round it is not fetched ({@link Colony#keepsOff}). Once the memory fades it is a
 * hole like any other: it opens on its turn, as a rested one does.</li>
 * <li><b>Left:</b> with the galleries on, a hole within {@code merge_within} blocks of a place once as strong as
 * {@code leave_from} (attacked again and again) is left for good: never opened again. The galleries open a new hole on
 * a gallery under it (or the nearest other) at least {@code new_hole_from} blocks from the place ({@link Galleries#openInstead}), if there is
 * room. (With the galleries off the colony has only its 2 holes: they are closed while the danger lasts, never left.)</li>
 * </ul>
 * Species with {@code colony.danger.close_holes}. The closed holes are worked out again once a second, and at once when
 * the memory changes; not saved (they follow from the memory). The left holes are saved.
 */
public class ClosedHoles {
    /** How often (ticks) the closed holes are worked out again (the danger fades slowly). */
    private static final int TICKS = 20;

    private final Colony colony;
    /** The holes closed now (worked out from the memory). */
    private Set<BlockPos> closed = Set.of();
    /** The gallery cells running near an attack now (no hidden trip through them). */
    private Set<BlockPos> closedCells = Set.of();
    private long workedOutAt = Long.MIN_VALUE;
    private int workedOutVersion = -1;
    @Nullable
    private List<NestPlan.ForagingTunnel> workedOutFor;
    /** The holes left for good, each with the place it was left for. Saved. */
    private final Map<BlockPos, BlockPos> left = new HashMap<>();
    /** Holes left whose new hole could not be looked for yet (their gallery not loaded). Saved. */
    private final Set<BlockPos> waiting = new HashSet<>();
    /** How many new holes were opened in place of left ones (for the list). Saved. */
    private int opened;

    ClosedHoles(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private SpeciesProfile.DangerNumbers numbers() {
        return colony.danger().numbers();
    }

    /** Whether this colony closes its holes where it was attacked (the species file's {@code colony.danger.close_holes}). */
    public boolean isOn() {
        SpeciesProfile.DangerNumbers numbers = numbers();
        return numbers != null && numbers.closeHoles();
    }

    /**
     * The closed holes' answer to "keep off?" ({@link ColonyQuestions#KEEP_OFF}, colony parts step 6): no food is fetched
     * where members were attacked, from outside or from inside (the rule was the danger map's until step 6).
     */
    public boolean keepsOff(Level level, BlockPos pos) {
        return isOn() && colony.danger().isAttacked(level, pos);
    }

    /**
     * The closed holes' answer to "may no gallery grow through here?" ({@link ColonyQuestions#NO_GALLERY_THROUGH}, step 7):
     * not toward where members were attacked, while it is remembered.
     */
    public boolean keepsGalleriesOff(Level level, BlockPos cell) {
        return isOn() && !colony.danger().isEmpty() && colony.danger().isAttacked(level, cell);
    }

    /** Whether this foraging hole is closed now: near an attack, its gallery running near one, or left for good. */
    public boolean isClosed(Level level, BlockPos hole) {
        if (!isOn()) {
            return false;
        }
        if (left.containsKey(hole)) {
            return true;
        }
        workOut(level);
        return closed.contains(hole);
    }

    /** Whether this hole was left for good. */
    public boolean isLeft(BlockPos hole) {
        return left.containsKey(hole);
    }

    /** Whether any hole is closed now (a quick check before looking at each). */
    public boolean anyClosed(Level level) {
        if (!isOn()) {
            return false;
        }
        workOut(level);
        return !closed.isEmpty() || !left.isEmpty();
    }

    /** Whether this gallery cell runs near an attack now (as last worked out): no hidden trip goes through it. */
    public boolean closesCell(BlockPos cell) {
        return closedCells.contains(cell);
    }

    /** The holes closed now and the holes left (for the tests and the list). */
    public Set<BlockPos> closedNow(Level level) {
        if (!isOn()) {
            return Set.of();
        }
        workOut(level);
        Set<BlockPos> all = new HashSet<>(closed);
        all.addAll(left.keySet());
        return all;
    }

    /** The holes left for good (for the tests). */
    public Set<BlockPos> left() {
        return Set.copyOf(left.keySet());
    }

    /** How many new holes were opened in place of left ones. */
    public int opened() {
        return opened;
    }

    /** Works the closed holes out again once a second, or at once when the memory or the holes changed. */
    private void workOut(Level level) {
        DangerMap danger = colony.danger();
        List<NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
        long now = level.getGameTime();
        if (workedOutVersion == danger.version() && workedOutFor == tunnels && now - workedOutAt < TICKS && now >= workedOutAt) {
            return;
        }
        workedOutAt = now;
        workedOutVersion = danger.version();
        workedOutFor = tunnels;
        if (danger.isEmpty()) {
            closed = Set.of();
            closedCells = Set.of();
            return;
        }
        Map<BlockPos, Boolean> near = new HashMap<>(); // (the tunnels share their first blocks: each cell looked at once)
        Set<BlockPos> holes = new HashSet<>();
        Set<BlockPos> cells = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : tunnels) {
            boolean shut = danger.isAttacked(level, tunnel.hole());
            for (BlockPos cell : tunnel.tunnel()) {
                if (near.computeIfAbsent(cell, c -> danger.isAttacked(level, c))) {
                    shut = true;
                    cells.add(cell);
                }
            }
            if (shut) {
                holes.add(tunnel.hole());
            }
        }
        if (!holes.equals(closed)) {
            AntColoniesMod.LOGGER.debug("[danger] colony {}: {} holes closed for danger", colony.getId(), holes.size());
        }
        closed = holes;
        closedCells = cells;
    }

    /** Once a second (from {@link Colony#tick}): the closed holes worked out; holes by a place hit again and again left for good. */
    void tick(ServerLevel level) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null || !numbers.closeHoles()) {
            return;
        }
        workOut(level);
        Galleries galleries = Galleries.of(colony);
        if (galleries == null || !galleries.isOn()) {
            return;
        }
        for (DangerMap.Place place : colony.danger().hitHard()) {
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                BlockPos hole = tunnel.hole();
                if (!left.containsKey(hole) && flat(hole, place.pos()) <= numbers.mergeWithin()) {
                    left.put(hole.immutable(), place.pos().immutable());
                    waiting.add(hole.immutable());
                    ColonyManager.get(level).markChanged();
                    AntColoniesMod.LOGGER.info("[danger] colony {}: the hole at {} is left for good (attacked again and again at {})",
                            colony.getId(), hole.toShortString(), place.pos().toShortString());
                }
            }
        }
        if (waiting.isEmpty()) {
            return;
        }
        for (BlockPos hole : List.copyOf(waiting)) {
            NestPlan.ForagingTunnel tunnel = null;
            for (NestPlan.ForagingTunnel one : colony.foragingHoles().tunnels(level)) {
                if (one.hole().equals(hole)) {
                    tunnel = one;
                }
            }
            if (tunnel == null) {
                waiting.remove(hole); // (its gallery was filled in meanwhile)
                continue;
            }
            Galleries.Instead instead = galleries.openInstead(level, tunnel, left.get(hole), numbers.newHoleFrom());
            if (instead == Galleries.Instead.NOT_LOADED) {
                continue;
            }
            waiting.remove(hole);
            if (instead == Galleries.Instead.OPENED) {
                opened++;
            }
            ColonyManager.get(level).markChanged();
        }
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** For the colony list: " | 3 holes closed for danger, 1 left (1 new hole instead)", or empty. */
    public String describe(Level level) {
        if (!isOn()) {
            return "";
        }
        workOut(level);
        if (closed.isEmpty() && left.isEmpty()) {
            return "";
        }
        long shut = closed.stream().filter(hole -> !left.containsKey(hole)).count();
        return " | " + shut + (shut == 1 ? " hole" : " holes") + " closed for danger"
                + (left.isEmpty() ? "" : ", " + left.size() + " left (" + opened + (opened == 1 ? " new hole" : " new holes") + " instead)");
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayTests.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        left.put(new BlockPos(8, 60, 0), new BlockPos(9, 60, 1));
        waiting.add(new BlockPos(0, 60, 8));
        opened = 1;
    }

    void save(CompoundTag tag) {
        CompoundTag out = new CompoundTag();
        long[] holes = new long[left.size()];
        long[] places = new long[left.size()];
        int i = 0;
        for (Map.Entry<BlockPos, BlockPos> entry : left.entrySet()) {
            holes[i] = entry.getKey().asLong();
            places[i++] = entry.getValue().asLong();
        }
        out.putLongArray("holes", holes);
        out.putLongArray("places", places);
        out.putLongArray("waiting", waiting.stream().mapToLong(BlockPos::asLong).toArray());
        out.putInt("opened", opened);
        tag.put("closedHoles", out);
    }

    void load(CompoundTag tag) {
        left.clear();
        waiting.clear();
        CompoundTag in = tag.getCompound("closedHoles");
        long[] holes = in.getLongArray("holes");
        long[] places = in.getLongArray("places");
        for (int i = 0; i < holes.length && i < places.length; i++) {
            left.put(BlockPos.of(holes[i]), BlockPos.of(places[i]));
        }
        for (long hole : in.getLongArray("waiting")) {
            waiting.add(BlockPos.of(hole));
        }
        opened = in.getInt("opened");
    }
}
