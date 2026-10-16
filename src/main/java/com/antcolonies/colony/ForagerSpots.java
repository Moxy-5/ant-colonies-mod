package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;


import org.jetbrains.annotations.Nullable;

import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Brain step 4c: site fidelity. Real red harvester ants (<i>Pogonomyrmex barbatus</i>) do not tell nestmates of a
 * seed; each forager searches alone and goes back, trip after trip and day after day, to where it last found food
 * (Hölldobler 1976). Only for species whose profile has {@code food.search}.
 *
 * <p>Each real forager has its <b>spot</b> (where it last cut or picked up food) and how many trips since found nothing
 * there; {@code empty_to_drop} empty trips and the spot is dropped. A forager that came home empty searches the same
 * way next time, starting as far out as its last search drifted ({@link #leadOf}; the range stretches from the ants'
 * own failures, the user's decision). The forager numbers keep their spots in a pool, each held by some of them ({@link Held}). The
 * golden rule of the numbers: a real forager going into the numbers puts its spot in the pool, one coming out takes a
 * spot from it, so a spot is never doubled or lost; a dead forager's spot is lost with it. All saved.
 */
public class ForagerSpots {
    /** A real forager's spot: where it last found food, and the trips since that found nothing there. */
    public record Spot(BlockPos pos, int empties) {
    }

    /** Where a forager that came home empty searches next: the same way ({@code angle}, radians), {@code out} blocks out. */
    public record Lead(double angle, int out) {
    }

    /** A spot of the numbers: where, how many forager numbers hold it, and the trips since that found nothing there. */
    public static final class Held {
        final BlockPos pos;
        int holders;
        int empties;

        Held(BlockPos pos, int holders, int empties) {
            this.pos = pos;
            this.holders = holders;
            this.empties = empties;
        }

        public BlockPos pos() {
            return pos;
        }

        public int holders() {
            return holders;
        }
    }

    /** The species' search numbers (from the colony's profile). */
    private final Supplier<Optional<SpeciesProfile.SearchNumbers>> numbers;
    /** Each real forager's spot. Saved. */
    private final Map<UUID, Spot> spots = new HashMap<>();
    /** Where a real forager searches next after an empty trip. Saved. */
    private final Map<UUID, Lead> leads = new HashMap<>();
    /** The numbers' spots. Saved. */
    private final List<Held> held = new ArrayList<>();
    /** How far out the numbers start their next new search, after searching numbers found nothing. Saved. */
    private int numbersStartOut;
    /** The real foragers searching right now (for the colony list). Not saved. */
    private final Set<UUID> searching = new HashSet<>();
    /** Searches that came home empty since the colony was loaded (for the colony list). Not saved. */
    private int emptyTrips;

    ForagerSpots(Colony colony) {
        this(() -> colony.profile().food().search());
    }

    /** With these search numbers (the unit tests). */
    ForagerSpots(Supplier<Optional<SpeciesProfile.SearchNumbers>> numbers) {
        this.numbers = numbers;
    }

    /** The species' search numbers, if its foragers search alone with site fidelity. */
    public Optional<SpeciesProfile.SearchNumbers> settings() {
        return numbers.get();
    }

    /** Whether this colony's foragers search alone and remember their own spots. */
    public boolean isOn() {
        return settings().isPresent();
    }

    // --- the real foragers ------------------------------------------------------------------------------------------

    /** This forager's spot, or null if it has none. */
    @Nullable
    public BlockPos spotOf(UUID ant) {
        Spot spot = spots.get(ant);
        return spot == null ? null : spot.pos();
    }

    /** Where this forager searches next after an empty trip (the same way, farther out), or null: where it likes. */
    @Nullable
    public Lead leadOf(UUID ant) {
        return leads.get(ant);
    }

    /** The forager found food here (it cut a piece off a plant or picked up a seed): this is its spot now. */
    public void found(UUID ant, BlockPos where) {
        if (!isOn()) {
            return;
        }
        spots.put(ant, new Spot(where.immutable(), 0));
        leads.remove(ant);
    }

    /**
     * A trip to its own spot found nothing there in time: one empty trip more; after {@code empty_to_drop} of them the
     * spot is dropped (it searches elsewhere from now on).
     */
    public void emptyAtSpot(UUID ant) {
        Spot spot = spots.get(ant);
        int drop = settings().map(SpeciesProfile.SearchNumbers::emptyToDrop).orElse(2);
        if (spot == null) {
            return;
        }
        if (spot.empties() + 1 >= drop) {
            spots.remove(ant);
        } else {
            spots.put(ant, new Spot(spot.pos(), spot.empties() + 1));
        }
    }

    /**
     * The forager came home empty from searching {@code angle} (radians from the nest's middle): next time it searches
     * the same way from {@code out} blocks out (as far as this one drifted), unless that is already the farthest it may
     * go ({@code most}: the range plus the stretch), when it tries another way.
     */
    public void cameHomeEmpty(UUID ant, double angle, int out, int most) {
        if (out >= most) {
            leads.remove(ant);
        } else {
            leads.put(ant, new Lead(angle, out));
        }
        emptyTrips++;
    }

    /** Whether this real forager is searching right now (set by the searching goal, for the colony list). */
    public void setSearching(UUID ant, boolean now) {
        if (now) {
            searching.add(ant);
        } else {
            searching.remove(ant);
        }
    }

    /** Whether this real forager is searching right now. */
    public boolean isSearching(UUID ant) {
        return searching.contains(ant);
    }

    /** A forager died: its spot is lost with it. */
    public void died(UUID ant) {
        spots.remove(ant);
        leads.remove(ant);
        searching.remove(ant);
    }

    // --- the numbers ------------------------------------------------------------------------------------------------

    /** A real forager went into the numbers (see {@link Population}, {@link AwayLife}): its spot goes into the pool. */
    void intoNumbers(UUID ant) {
        searching.remove(ant);
        leads.remove(ant);
        Spot spot = spots.remove(ant);
        if (spot != null) {
            addHeld(spot.pos(), 1, spot.empties());
        }
    }

    /** A worker came out of the numbers as a real mob: it takes a spot from the pool (the one most hold), if any. */
    void outOfNumbers(UUID ant) {
        Held most = null;
        for (Held spot : held) {
            if (most == null || spot.holders > most.holders) {
                most = spot;
            }
        }
        if (most == null) {
            return;
        }
        spots.put(ant, new Spot(most.pos, most.empties));
        if (--most.holders <= 0) {
            held.remove(most);
        }
    }

    /** The numbers' spots (for {@link FieldLife}). */
    List<Held> held() {
        return held;
    }

    /** How many forager numbers hold a spot. */
    int holders() {
        int count = 0;
        for (Held spot : held) {
            count += spot.holders;
        }
        return count;
    }

    /** {@code count} forager numbers found food here: they hold this spot now. */
    void addHeld(BlockPos pos, int count, int empties) {
        for (Held spot : held) {
            if (spot.pos.equals(pos)) {
                spot.holders += count;
                spot.empties = Math.min(spot.empties, empties);
                return;
            }
        }
        held.add(new Held(pos.immutable(), count, empties));
    }

    /**
     * Nothing was left near this spot of the numbers on a trip: one empty trip more; after {@code empty_to_drop} the
     * spot is dropped and its holders search again. Returns how many holders were let go.
     */
    int emptyHeld(Held spot) {
        int drop = settings().map(SpeciesProfile.SearchNumbers::emptyToDrop).orElse(2);
        if (++spot.empties >= drop) {
            held.remove(spot);
            return spot.holders;
        }
        return 0;
    }

    /** The numbers' spots are held by no more than {@code foragers} forager numbers (some died or came out as real ones). */
    void trimHeld(int foragers) {
        int over = holders() - Math.max(0, foragers);
        while (over > 0 && !held.isEmpty()) {
            Held most = held.get(0);
            for (Held spot : held) {
                if (spot.holders > most.holders) {
                    most = spot;
                }
            }
            int off = Math.min(over, most.holders);
            most.holders -= off;
            over -= off;
            if (most.holders <= 0) {
                held.remove(most);
            }
        }
    }

    /** How far out the searching numbers search at most (the band their failed searches pushed out to); 0: the inner half of the range. */
    int numbersStartOut() {
        return numbersStartOut;
    }

    void setNumbersStartOut(int out) {
        numbersStartOut = Math.max(0, out);
    }

    /** The numbers came home empty from a search (counted for the colony list). */
    void numbersCameHomeEmpty() {
        emptyTrips++;
    }

    // --- the list and saving ----------------------------------------------------------------------------------------

    /** For the colony list: " | searching 3, spots 12 (5 held by numbers), 1 home empty", or empty for a species without it. */
    public String describe() {
        if (!isOn()) {
            return "";
        }
        return " | searching " + searching.size() + ", spots " + (spots.size() + holders()) + " (" + holders() + " held by numbers)"
                + (emptyTrips > 0 ? ", " + emptyTrips + " home empty" : "");
    }

    /** How many real foragers have a spot. For tests. */
    public int spotCount() {
        return spots.size();
    }

    /** How many forager numbers hold a spot. For tests. */
    public int heldByNumbers() {
        return holders();
    }

    /** Gives this forager a spot, as if it had found food there. For the game tests. */
    public void setSpotForTests(UUID ant, BlockPos where, int empties) {
        spots.put(ant, new Spot(where.immutable(), empties));
    }

    void save(CompoundTag tag) {
        ListTag real = new ListTag();
        for (Map.Entry<UUID, Spot> entry : spots.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("ant", entry.getKey());
            one.putLong("pos", entry.getValue().pos().asLong());
            one.putInt("empties", entry.getValue().empties());
            real.add(one);
        }
        tag.put("foragerSpots", real);
        ListTag out = new ListTag();
        for (Map.Entry<UUID, Lead> entry : leads.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putUUID("ant", entry.getKey());
            one.putDouble("angle", entry.getValue().angle());
            one.putInt("out", entry.getValue().out());
            out.add(one);
        }
        tag.put("foragerLeads", out);
        ListTag numbers = new ListTag();
        for (Held spot : held) {
            CompoundTag one = new CompoundTag();
            one.putLong("pos", spot.pos.asLong());
            one.putInt("holders", spot.holders);
            one.putInt("empties", spot.empties);
            numbers.add(one);
        }
        tag.put("numberSpots", numbers);
        tag.putInt("numbersStartOut", numbersStartOut);
    }

    void load(CompoundTag tag) {
        spots.clear();
        leads.clear();
        held.clear();
        for (Tag one : tag.getList("foragerSpots", Tag.TAG_COMPOUND)) {
            CompoundTag spot = (CompoundTag) one;
            if (spot.hasUUID("ant")) {
                spots.put(spot.getUUID("ant"), new Spot(BlockPos.of(spot.getLong("pos")), spot.getInt("empties")));
            }
        }
        for (Tag one : tag.getList("foragerLeads", Tag.TAG_COMPOUND)) {
            CompoundTag out = (CompoundTag) one;
            if (out.hasUUID("ant")) {
                leads.put(out.getUUID("ant"), new Lead(out.getDouble("angle"), out.getInt("out")));
            }
        }

        for (Tag one : tag.getList("numberSpots", Tag.TAG_COMPOUND)) {
            CompoundTag spot = (CompoundTag) one;
            if (spot.getInt("holders") > 0) {
                held.add(new Held(BlockPos.of(spot.getLong("pos")), spot.getInt("holders"), spot.getInt("empties")));
            }
        }
        numbersStartOut = tag.getInt("numbersStartOut");
    }
}
