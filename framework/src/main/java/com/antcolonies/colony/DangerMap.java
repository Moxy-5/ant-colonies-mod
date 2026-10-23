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

/**
 * Brain step 4g: the danger memory (the user's decisions of 2026-10-17 in {@code docs/DESIGN.md}, "4g decisions"; plan
 * {@code docs/plans/4g-danger-neighbours.md}). The colony remembers the places where its insects were hurt or killed by a
 * living thing (a mob or a player) and where hostile mobs were seen close by. Each place has a strength: a hurt, a death
 * and a sighting each add to it ({@code colony.danger} in the species file), places close together are one, and the
 * strength halves every game day (counted by the clock, so it fades while away too); once it is weak enough the place
 * is forgotten. So one death is kept away from for about 2 days and remembered about 3, and more deaths at one place
 * make it last longer (real colonies drift back once the danger is gone).
 *
 * <p>What the colony does about it is the species' own: harvester ants ({@code keep_away}) leave plants, spots, searches,
 * recruits and trunk trails through a dangerous place alone ({@link #keepsOff}, with {@link Colony#keepsOff}; real <i>Pogonomyrmex</i> keep the memory
 * in the foragers and their trails: an attacked trail loses its traffic and fades). The nest's own ground is never
 * kept away from (a colony cannot leave its home; defending it comes with the fights, M8). Fall damage, fire, drowning
 * and starving are not danger here (hazards are their own later feature). Saved.
 */
public class DangerMap {
    /** One remembered place: where, its strength at the game time {@code at}, and what it was last (a mob kind or "player"). */
    public static final class Place {
        BlockPos pos;
        double strength;
        long at;
        String what;
        /** Killed members here, in all (for the colony list). */
        int deaths;
        /** Members hurt here and not killed, in all: with {@link #deaths}, whether anyone was attacked here (not only a hostile seen). */
        int hurts;
        /** The most the strength has been (a place once hit hard enough is left for good: termites, step 3). */
        double peak;

        Place(BlockPos pos, double strength, long at, String what, int deaths, int hurts, double peak) {
            this.pos = pos;
            this.strength = strength;
            this.at = at;
            this.what = what;
            this.deaths = deaths;
            this.hurts = hurts;
            this.peak = peak;
        }

        public BlockPos pos() {
            return pos;
        }

        public String what() {
            return what;
        }

        public int deaths() {
            return deaths;
        }

        /** Whether a member was hurt or killed here (not only a hostile seen). */
        public boolean attacked() {
            return deaths + hurts > 0;
        }

        public double peak() {
            return peak;
        }
    }

    /** How sightings are counted: once a mob has been seen it adds again only after this long (ticks). */
    private final Map<UUID, Long> seenAt = new HashMap<>();
    private final Colony colony;
    private final List<Place> places = new ArrayList<>();
    /** Places forgotten since the colony was loaded (for the list). */
    private int forgotten;
    /** Worker numbers lost out of sight since the colony was loaded (step 4, for the list). */
    private int lostOutOfSight;
    /** Counts every place added to, made or forgotten (the closed holes are worked out again when it changes). */
    private int version;

    DangerMap(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    public SpeciesProfile.DangerNumbers numbers() {
        return colony.profile().colony().danger().orElse(null);
    }

    // --- what happened ------------------------------------------------------------------------------------------------

    /** A member was hurt at {@code where} by {@code what} (a living thing not of its colony); {@code killed}: it died of it. */
    public void hurt(ServerLevel level, BlockPos where, String what, boolean killed) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        add(level, where, killed ? numbers.killed() : numbers.hurt(), what, killed, true, killed ? "a member killed" : "a member hurt");
    }

    /** A member out on the surface saw a hostile mob ({@code mob}) close by at {@code where}: counted once per {@code seen_every_ticks}. */
    public void seen(ServerLevel level, BlockPos where, UUID mob, String what) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        Long last = seenAt.get(mob);
        if (last != null && now - last < numbers.seenEveryTicks() && now >= last) {
            return;
        }
        seenAt.put(mob, now);
        if (seenAt.size() > 256) {
            seenAt.values().removeIf(time -> now - time >= numbers.seenEveryTicks() || time > now);
        }
        add(level, where, numbers.seen(), what, false, false, "a hostile seen");
    }

    private void add(ServerLevel level, BlockPos where, double amount, String what, boolean killed, boolean attack, String happened) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        long now = level.getGameTime();
        Place nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Place place : places) {
            double distance = flat(place.pos, where);
            if (distance <= numbers.mergeWithin() && distance < nearestDistance) {
                nearest = place;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            nearest = new Place(where.immutable(), 0, now, what, 0, 0, 0);
            places.add(nearest);
        }
        nearest.strength = Math.min(numbers.most(), strength(nearest, now) + amount);
        nearest.at = now;
        nearest.what = what;
        nearest.peak = Math.max(nearest.peak, nearest.strength);
        if (killed) {
            nearest.deaths++;
        } else if (attack) {
            nearest.hurts++;
        }
        version++;
        ColonyFrameworkMod.LOGGER.info("[danger] colony {}: {} at {} ({}), strength now {}", colony.getId(), happened, where.toShortString(), what,
                String.format("%.1f", nearest.strength));
        ColonyManager.get(level).markChanged();
    }

    // --- what the colony asks ------------------------------------------------------------------------------------------

    /** This place's strength now (it halves every {@code half_life_ticks}). */
    private double strength(Place place, long now) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        long ticks = now - place.at;
        return numbers == null ? 0 : ticks <= 0 ? place.strength : place.strength * Math.pow(0.5, ticks / (double) Math.max(1, numbers.halfLifeTicks()));
    }

    /** The danger at {@code pos} now: the strength of every remembered place within {@code reach} blocks of it, summed. */
    public double dangerAt(Level level, BlockPos pos) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null || places.isEmpty()) {
            return 0;
        }
        long now = level.getGameTime();
        double sum = 0;
        for (Place place : places) {
            if (flat(place.pos, pos) <= numbers.reach() && !atHome(place.pos)) {
                sum += strength(place, now);
            }
        }
        return sum;
    }

    /** Whether {@code pos} is a dangerous place now (its danger at least {@code dangerous_from}); the nest's own ground never is. */
    public boolean isDangerous(Level level, BlockPos pos) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        return numbers != null && !places.isEmpty() && dangerAt(level, pos) >= numbers.dangerousFrom();
    }

    /**
     * Whether {@code pos} lies where members were attacked (hurt or killed, not only a hostile seen): the strength of the
     * places within {@code reach} where someone was attacked adds up to {@code dangerous_from}. The nest's own ground never
     * does. What the termites close their holes for (step 3, {@link ClosedHoles}).
     */
    public boolean isAttacked(Level level, BlockPos pos) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null || places.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        double sum = 0;
        for (Place place : places) {
            if (place.attacked() && flat(place.pos, pos) <= numbers.reach() && !atHome(place.pos)) {
                sum += strength(place, now);
            }
        }
        return sum >= numbers.dangerousFrom();
    }

    /** The places where someone was attacked that were once as strong as {@code leave_from} (left for good: termites, step 3). */
    public List<Place> hitHard() {
        SpeciesProfile.DangerNumbers numbers = numbers();
        List<Place> hard = new ArrayList<>();
        if (numbers != null) {
            for (Place place : places) {
                if (place.attacked() && place.peak >= numbers.leaveFrom() && !atHome(place.pos)) {
                    hard.add(place);
                }
            }
        }
        return hard;
    }

    /** Changes each time a place is added to, made or forgotten (for the caches built on the memory). */
    public int version() {
        return version;
    }

    /**
     * The danger map's answer to "keep off?" ({@link ColonyQuestions#KEEP_OFF}, asked by {@link Colony#keepsOff}): in a
     * species with {@code keep_away}, {@code pos} is a dangerous place now. The other parts give their own answers: the
     * neighbours (a neighbour yielded to), the termites' closed holes (where members were attacked), the harvester's
     * trunk trails (the trail out there passes danger).
     */
    public boolean keepsOff(Level level, BlockPos pos) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        return numbers != null && !places.isEmpty() && numbers.keepAway() && isDangerous(level, pos);
    }

    // --- harm out of sight (step 4) ------------------------------------------------------------------------------------

    @Nullable
    public SpeciesProfile.LostNumbers lostNumbers() {
        return colony.profile().colony().lost().orElse(null);
    }

    /**
     * The chance a number's trip near {@code pos} is lost (step 4, the user's decisions of 2026-10-18): {@code per_point}
     * for each point of danger within {@code reach}, at most {@code most}. In loaded land a remembered place counts only
     * while a hostile is really there out of sight (nobody dies of a mere memory); in unloaded land the memory decides.
     * {@code strongest} gets the strongest such place (where a lost member's body lies); may be null.
     */
    public double lossChance(ServerLevel level, BlockPos pos, @Nullable Place[] strongest) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        SpeciesProfile.LostNumbers lost = lostNumbers();
        if (numbers == null || lost == null || places.isEmpty()) {
            return 0;
        }
        long now = level.getGameTime();
        double sum = 0;
        double most = 0;
        for (Place place : places) {
            if (flat(place.pos, pos) > numbers.reach() || atHome(place.pos)) {
                continue;
            }
            if (level.isLoaded(place.pos) && !hostileAt(level, place)) {
                continue;
            }
            double strength = strength(place, now);
            sum += strength;
            if (strongest != null && strength > most) {
                most = strength;
                strongest[0] = place;
            }
        }
        return Math.min(lost.most(), sum * lost.perPoint());
    }

    /** Whether a hostile (a monster, a mob hunting insects, or a player where a player did harm) is really near this place now. */
    private boolean hostileAt(ServerLevel level, Place place) {
        double reach = numbers().reach();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(place.pos).inflate(reach, 6, reach);
        if ("player".equals(place.what) && !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box,
                player -> player.isAlive() && !player.isSpectator()).isEmpty()) {
            return true;
        }
        return !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                mob -> mob.isAlive() && !(mob instanceof com.antcolonies.entity.AbstractAntEntity)
                        && (mob instanceof net.minecraft.world.entity.monster.Enemy
                                || mob.getTarget() instanceof com.antcolonies.entity.AbstractAntEntity
                                || mob.getType().toShortString().equals(place.what))).isEmpty();
    }

    /**
     * A worker number on a trip is lost at this dangerous place (step 4): it dies out of sight, its body lies there and
     * may be found ({@link #bodyLost}). False if there was no worker number to lose.
     */
    public boolean lose(ServerLevel level, Place where) {
        if (!colony.population().lostOutside(level)) {
            return false;
        }
        lostOutOfSight++;
        boolean found = bodyLost(level, where.pos, where.what);
        ColonyFrameworkMod.LOGGER.info("[danger] colony {}: a worker lost out of sight at {} ({}), body {}", colony.getId(),
                where.pos.toShortString(), where.what, found ? "found" : "not found");
        return true;
    }

    /**
     * A number was lost at {@code where} (step 4): the colony remembers it as a death there only if nestmates find the
     * body (the user's idea): certain near the nest, less likely each block farther out ({@code found_within},
     * {@code found_drop_per_block}). True if it was found.
     */
    public boolean bodyLost(ServerLevel level, BlockPos where, String what) {
        SpeciesProfile.LostNumbers lost = lostNumbers();
        if (lost == null || numbers() == null) {
            return false;
        }
        boolean found = colony.random().nextDouble() < lost.foundChance(flat(where, colony.getCenter()));
        if (found) {
            colony.away().note(level, AwayReport.What.BODIES_FOUND, 1);
            add(level, where, numbers().killed(), what, true, true, "a lost member's body found");
        }
        return found;
    }

    /** Worker numbers lost out of sight since the colony was loaded (step 4). */
    public int lostOutOfSight() {
        return lostOutOfSight;
    }

    /** Whether any place is remembered at all (a quick check before the costly ones). */
    public boolean isEmpty() {
        return places.isEmpty();
    }

    /** Whether this spot is on the nest's own ground (the hill and round it: never kept away from). */
    private boolean atHome(BlockPos pos) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        return numbers != null && flat(pos, colony.getCenter()) <= numbers.home();
    }

    /** Once a second: faded places are forgotten. */
    void tick(ServerLevel level) {
        SpeciesProfile.DangerNumbers numbers = numbers();
        if (numbers == null || places.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Place> it = places.iterator(); it.hasNext(); ) {
            Place place = it.next();
            if (strength(place, now) < numbers.forgetBelow() || place.at > now + 24000L) {
                it.remove();
                forgotten++;
                version++;
                colony.away().note(level, AwayReport.What.DANGER_FORGOTTEN, 1);
                ColonyFrameworkMod.LOGGER.info("[danger] colony {}: the danger at {} is forgotten", colony.getId(), place.pos.toShortString());
                ColonyManager.get(level).markChanged();
            }
        }
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- the list, tests and saving ------------------------------------------------------------------------------------

    /** The remembered places. */
    public List<Place> places() {
        return places;
    }

    /** A place's strength now. */
    public double strengthNow(Level level, Place place) {
        return strength(place, level.getGameTime());
    }

    /** Game tests only: as if {@code ticks} had passed for every place. */
    public void ageForTests(long ticks) {
        for (Place place : places) {
            place.at -= ticks;
        }
        version++;
    }

    /** For the colony list: " | danger: 2 places (strongest 3.4 at 10, 64, -5: zombie, 1 killed), 1 forgotten", or empty. */
    public String describe(Level level) {
        if (numbers() == null || places.isEmpty() && forgotten == 0 && lostOutOfSight == 0) {
            return "";
        }
        String lost = lostOutOfSight > 0 ? ", " + lostOutOfSight + " lost out of sight" : "";
        if (places.isEmpty()) {
            return " | danger: none remembered, " + forgotten + " forgotten" + lost;
        }
        Place strongest = places.get(0);
        long now = level.getGameTime();
        for (Place place : places) {
            if (strength(place, now) > strength(strongest, now)) {
                strongest = place;
            }
        }
        return " | danger: " + places.size() + (places.size() == 1 ? " place" : " places") + " (strongest "
                + String.format("%.1f", strength(strongest, now)) + " at " + strongest.pos.toShortString() + ": " + strongest.what
                + (strongest.deaths > 0 ? ", " + strongest.deaths + " killed" : "") + (atHome(strongest.pos) ? ", the nest's own ground" : "") + ")"
                + (forgotten > 0 ? ", " + forgotten + " forgotten" : "") + lost;
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayKit.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        places.add(new Place(new BlockPos(6, 60, -4), 1.5, level.getGameTime(), "minecraft:spider", 1, 2, 1.5));
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Place place : places) {
            CompoundTag one = new CompoundTag();
            one.putLong("pos", place.pos.asLong());
            one.putDouble("strength", place.strength);
            one.putLong("at", place.at);
            one.putString("what", place.what);
            one.putInt("deaths", place.deaths);
            one.putInt("hurts", place.hurts);
            one.putDouble("peak", place.peak);
            list.add(one);
        }
        tag.put("danger", list);
    }

    void load(CompoundTag tag) {
        places.clear();
        for (Tag entry : tag.getList("danger", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) entry;
            places.add(new Place(BlockPos.of(one.getLong("pos")), one.getDouble("strength"), one.getLong("at"), one.getString("what"),
                    one.getInt("deaths"), one.getInt("hurts"), one.contains("peak") ? one.getDouble("peak") : one.getDouble("strength")));
        }
    }
}
