package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Brain step 4g, step 5: neighbours (the user's decisions in {@code docs/DESIGN.md}, "4g decisions"; plan
 * {@code docs/plans/4g-danger-neighbours.md}). Real red harvester ant colonies keep apart without fighting most of the
 * time (Gordon and Kulig 1996): where foragers of two neighbouring colonies meet, the next days the colony forages less
 * that way, and old colonies (5 years and more) turn away after fewer meetings than young ones, which keep pushing on.
 * Neighbouring Macrotermes colonies wall off their galleries with soil where they come close.
 *
 * <p>The colony remembers the places where its insects met insects of another colony of its species (within
 * {@code within} blocks), or walked on another colony's trunk trail, and where its galleries came close to another
 * colony's (termites, {@link ColonyHooks.Underground}). Each meeting adds 1 to its place; places close together are one; the strength
 * halves every {@code half_life_ticks} (by the clock, so it fades while away too) and is forgotten once weak. A colony
 * <b>yields</b> to a place once its strength reaches the meetings its age needs ({@link SpeciesProfile.NeighbourNumbers#needed}),
 * and keeps yielding until it has faded to half that:
 * nothing past the place within {@code turn_degrees} of its direction from the nest is foraged ({@link #yieldsAt}), no
 * trail grows past it or is laid that way ({@link #turnsFrom}). A meeting is not danger: nobody is hurt, the numbers come
 * to no harm. No fighting yet (M8). The colony's age is {@link Colony#ageDays}. Saved.
 */
public class Neighbours {
    /** One place where the colony met a neighbour: where, its strength at {@code at}, and the neighbour colony last met there. */
    public static final class Meeting {
        BlockPos pos;
        double strength;
        long at;
        UUID other;
        int count;
        /** Whether the colony has turned away here (it stays away until the place fades to half what it needed). */
        boolean yielded;

        Meeting(BlockPos pos, double strength, long at, UUID other, int count, boolean yielded) {
            this.pos = pos;
            this.strength = strength;
            this.at = at;
            this.other = other;
            this.count = count;
            this.yielded = yielded;
        }

        public BlockPos pos() {
            return pos;
        }

        public UUID other() {
            return other;
        }

        /** How many meetings were counted here in all. */
        public int count() {
            return count;
        }
    }

    private final Colony colony;
    private final List<Meeting> meetings = new ArrayList<>();
    /** When each insect (or trail, or gallery) of a neighbour was last counted, so one meeting is not counted every second. */
    private final Map<UUID, Long> metAt = new HashMap<>();
    /** Places forgotten since the colony was loaded (for the list). */
    private int forgotten;
    /** In a game test, the colony's own arena: colonies outside it are not neighbours (tests run side by side). */
    @Nullable
    private AABB testFence;

    Neighbours(Colony colony) {
        this.colony = colony;
    }

    /** Game tests only: colonies whose middle lies outside {@code area} are never met (null: no fence). */
    public void setTestFence(@Nullable AABB area) {
        testFence = area;
    }

    /** Whether {@code other} may be met at all (always, outside the game tests). */
    public boolean canMeet(Colony other) {
        return testFence == null || testFence.contains(other.getCenter().getCenter());
    }

    @Nullable
    public SpeciesProfile.NeighbourNumbers numbers() {
        return colony.profile().colony().neighbours().orElse(null);
    }

    // --- what happened ------------------------------------------------------------------------------------------------

    /**
     * A member met colony {@code other} at {@code where}; {@code who} is what was met (an insect, a trail, a gallery):
     * the same one again counts only after {@code meet_every_ticks}. Returns whether it was counted.
     */
    public boolean met(ServerLevel level, BlockPos where, UUID other, UUID who) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        if (numbers == null || other.equals(colony.getId())) {
            return false;
        }
        long now = level.getGameTime();
        Long last = metAt.get(who);
        if (last != null && now - last < numbers.meetEveryTicks() && now >= last) {
            return false;
        }
        metAt.put(who, now);
        if (metAt.size() > 256) {
            metAt.values().removeIf(time -> now - time >= numbers.meetEveryTicks() || time > now);
        }
        Meeting nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Meeting meeting : meetings) {
            double distance = flat(meeting.pos, where);
            if (distance <= numbers.mergeWithin() && distance < nearestDistance) {
                nearest = meeting;
                nearestDistance = distance;
            }
        }
        boolean yielded = nearest != null && yields(nearest, now, numbers);
        if (nearest == null) {
            nearest = new Meeting(where.immutable(), 0, now, other, 0, false);
            meetings.add(nearest);
        }
        nearest.strength = strength(nearest, now, numbers) + 1.0;
        nearest.at = now;
        nearest.other = other;
        nearest.count++;
        ColonyFrameworkMod.LOGGER.info("[neighbours] colony {} (day {}): met colony {} at {}, strength now {} of {} needed{}", colony.getId(),
                String.format("%.1f", colony.ageDays(level)), other, where.toShortString(), String.format("%.1f", nearest.strength),
                String.format("%.1f", needed(level)), !yielded && yields(nearest, now, numbers) ? ": it yields that way" : "");
        ColonyManager.get(level).markChanged();
        return true;
    }

    // --- what the colony asks ------------------------------------------------------------------------------------------

    private static double strength(Meeting meeting, long now, SpeciesProfile.NeighbourNumbers numbers) {
        long ticks = now - meeting.at;
        return ticks <= 0 ? meeting.strength : meeting.strength * Math.pow(0.5, ticks / (double) Math.max(1, numbers.halfLifeTicks()));
    }

    /** The meetings this colony needs at one place before it yields there, at its age now. */
    public double needed(Level level) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        return numbers == null ? Double.MAX_VALUE : numbers.needed(colony.ageDays(level));
    }

    /**
     * Whether the colony yields to this place now: it turns away once the place holds the meetings its age needs (a little
     * slack: four meetings a few minutes apart have faded a hair below 4), and stays away until the place has faded to
     * half that (one meeting for an old colony: about 2 days, then it is forgotten; real colonies drift back after a while).
     */
    private boolean yields(Meeting meeting, long now, SpeciesProfile.NeighbourNumbers numbers) {
        double strength = strength(meeting, now, numbers);
        double needed = numbers.needed(colony.ageDaysAt(now));
        if (!meeting.yielded && strength >= needed - 0.05) {
            meeting.yielded = true;
        }
        return meeting.yielded && strength >= needed * 0.5 - 1e-9;
    }

    /** The places the colony yields to now. */
    public List<Meeting> yielding(Level level) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        List<Meeting> list = new ArrayList<>();
        if (numbers != null) {
            long now = level.getGameTime();
            for (Meeting meeting : meetings) {
                if (yields(meeting, now, numbers)) {
                    list.add(meeting);
                }
            }
        }
        return list;
    }

    /**
     * Whether the colony keeps off {@code pos} for a neighbour: it lies within {@code turn_degrees} of the direction (from
     * the nest's middle) of a place the colony yields to, and as far out as that place, or nearly ({@code within} short).
     */
    public boolean yieldsAt(Level level, BlockPos pos) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        if (numbers == null || meetings.isEmpty()) {
            return false;
        }
        BlockPos center = colony.getCenter();
        double out = flat(pos, center);
        double angle = angleOf(pos);
        double turn = Math.toRadians(numbers.turnDegrees());
        for (Meeting meeting : yielding(level)) {
            if (difference(angleOf(meeting.pos), angle) <= turn && out >= flat(meeting.pos, center) - numbers.within()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the colony lays no new trail and sends no search this way ({@code angle}: radians, 0 east, PI/2 south). */
    public boolean turnsFrom(Level level, double angle) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        if (numbers == null || meetings.isEmpty()) {
            return false;
        }
        double turn = Math.toRadians(numbers.turnDegrees());
        for (Meeting meeting : yielding(level)) {
            if (difference(angleOf(meeting.pos), angle) <= turn) {
                return true;
            }
        }
        return false;
    }

    /** Whether any place is remembered at all. */
    public boolean isEmpty() {
        return meetings.isEmpty();
    }

    /** The direction of {@code pos} from the nest's middle (radians, 0 east, PI/2 south), as the trunk trails count it. */
    private double angleOf(BlockPos pos) {
        return Math.atan2(pos.getZ() - colony.getCenter().getZ(), pos.getX() - colony.getCenter().getX());
    }

    private static double difference(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Once a second: faded places are forgotten. */
    void tick(ServerLevel level) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        if (numbers == null || meetings.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Meeting> it = meetings.iterator(); it.hasNext(); ) {
            Meeting meeting = it.next();
            if (strength(meeting, now, numbers) < numbers.forgetBelow() || meeting.at > now + 24000L) {
                it.remove();
                forgotten++;
                ColonyFrameworkMod.LOGGER.info("[neighbours] colony {}: the meeting place at {} is forgotten", colony.getId(), meeting.pos.toShortString());
                ColonyManager.get(level).markChanged();
            }
        }
    }

    // --- the list, tests and saving ------------------------------------------------------------------------------------

    /** The remembered places. */
    public List<Meeting> meetings() {
        return meetings;
    }

    /** A place's strength now. */
    public double strengthNow(Level level, Meeting meeting) {
        SpeciesProfile.NeighbourNumbers numbers = numbers();
        return numbers == null ? 0 : strength(meeting, level.getGameTime(), numbers);
    }

    /** Game tests only: as if {@code ticks} had passed for every place (and for every insect last met). */
    public void ageForTests(long ticks) {
        for (Meeting meeting : meetings) {
            meeting.at -= ticks;
        }
        metAt.replaceAll((who, at) -> at - ticks);
    }

    /** For the colony list: " | neighbours: 2 places (yields to 1; 3 met at 10, 64, -5), needs 2.5 at day 4.0", or empty. */
    public String describe(Level level) {
        if (numbers() == null || meetings.isEmpty() && forgotten == 0) {
            return "";
        }
        String age = ", needs " + String.format("%.1f", needed(level)) + " at day " + String.format("%.1f", colony.ageDays(level));
        if (meetings.isEmpty()) {
            return " | neighbours: none remembered, " + forgotten + " forgotten" + age;
        }
        Meeting strongest = meetings.get(0);
        for (Meeting meeting : meetings) {
            if (strengthNow(level, meeting) > strengthNow(level, strongest)) {
                strongest = meeting;
            }
        }
        return " | neighbours: " + meetings.size() + (meetings.size() == 1 ? " place" : " places") + " (yields to " + yielding(level).size()
                + "; strongest " + String.format("%.1f", strengthNow(level, strongest)) + " at " + strongest.pos.toShortString() + ")"
                + (forgotten > 0 ? ", " + forgotten + " forgotten" : "") + walled() + age;
    }

    private String walled() {
        ColonyHooks.Underground web = colony.hooks().first(ColonyHooks.UNDERGROUND); // (switched on or not, as before)
        int walled = web == null ? 0 : web.walledOff();
        return walled > 0 ? ", " + walled + (walled == 1 ? " gallery" : " galleries") + " walled off" : "";
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayKit.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        meetings.add(new Meeting(new BlockPos(-10, 60, 3), 1.0, level.getGameTime(), new UUID(2, 2), 2, true));
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Meeting meeting : meetings) {
            CompoundTag one = new CompoundTag();
            one.putLong("pos", meeting.pos.asLong());
            one.putDouble("strength", meeting.strength);
            one.putLong("at", meeting.at);
            one.putUUID("other", meeting.other);
            one.putInt("count", meeting.count);
            one.putBoolean("yielded", meeting.yielded);
            list.add(one);
        }
        tag.put("neighbours", list);
    }

    void load(CompoundTag tag) {
        meetings.clear();
        for (Tag entry : tag.getList("neighbours", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            meetings.add(new Meeting(BlockPos.of(one.getLong("pos")), one.getDouble("strength"), one.getLong("at"),
                    one.hasUUID("other") ? one.getUUID("other") : new UUID(0, 0), one.getInt("count"), one.getBoolean("yielded")));
        }
    }
}
