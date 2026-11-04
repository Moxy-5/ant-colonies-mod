package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * M8 step 3, the Matabele raids ({@code docs/plans/m8-matabele.md}). Step 3a, the scouts: real Megaponera send lone
 * scouts out to search for termites feeding under cover; a scout that finds them looks the place over and runs home
 * laying a scent line, which the raid column follows out (step 3b). Here the colony keeps who is out scouting, what the
 * scouts have found (the good spots, each with the scent line home, rechecked first by later scouts and forgotten once
 * found empty twice or after a few days: the termites plug their holes and move their feeding away), which slices of
 * the land round the nest were searched empty lately, and how far out the scouts search (the range grows as the near
 * land comes up empty, up to the real ant's foraging range). Scouting is a job a few majors keep (real Megaponera
 * scouts are a small set of workers who scout again and again): the colony remembers its scouts and sends them first.
 * Each find remembers the scout who found it, who leads the raid there (step 3b). The scout's walk itself is
 * {@code MatabeleScoutGoal}.
 * Numbers: the species file's {@code raids.scouting} ({@link SavannaNumbers.ScoutNumbers}).
 */
public class Raids {
    /** A good spot a scout found: where, how much termite it smelt there, when found and last checked, empty checks, by whom. */
    public static final class Find {
        private final BlockPos at;
        private int worth;
        private final long foundAt;
        private long checkedAt;
        private int misses;
        /** The scent line home, the nest end first. */
        private List<BlockPos> trail;
        /** The scout who last found it (it leads the raid there, step 3b), or null. */
        @Nullable
        private UUID scout;

        Find(BlockPos at, int worth, long foundAt, long checkedAt, int misses, List<BlockPos> trail, @Nullable UUID scout) {
            this.at = at;
            this.worth = worth;
            this.foundAt = foundAt;
            this.checkedAt = checkedAt;
            this.misses = misses;
            this.trail = trail;
            this.scout = scout;
        }

        public BlockPos at() {
            return at;
        }

        public int worth() {
            return worth;
        }

        public long checkedAt() {
            return checkedAt;
        }

        public List<BlockPos> trail() {
            return trail;
        }

        @Nullable
        public UUID scout() {
            return scout;
        }
    }

    /** What a scout does on a trip: recheck a remembered spot ({@code find}), or search a slice ({@code slice}, its middle {@code angle}). */
    public record Trip(@Nullable Find find, int slice, double angle, int range) {
    }

    /** A remembered spot is not rechecked again for this long after a check (ticks: another scout just looked). */
    private static final long RECHECK_AFTER = 2000;

    private final Colony colony;
    private final List<Find> finds = new ArrayList<>();
    /** Slices of the land round the nest searched empty, and when. */
    private final Map<Integer, Long> searched = new HashMap<>();
    /** How far out the scouts search now (blocks from the nest's middle; 0 before the first trip). */
    private int range;
    /** The scouts out now (not saved: after a reload they are just ants again). */
    private final Set<UUID> scouts = new HashSet<>();
    /** Ants sent out by the scout command, whatever the hour or hunger (not saved). */
    private final Set<UUID> sent = new HashSet<>();
    /** The colony's scouts, the ones who scouted before, oldest first (saved; at most twice {@code most_scouts}). */
    private final Set<UUID> veterans = new LinkedHashSet<>();
    /** Since when a scout's place has stood open (game time, -1 none): a new ant takes it if no old scout comes soon. */
    private long openSince = -1;

    public Raids(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    public SavannaNumbers.ScoutNumbers numbers() {
        return colony.profile().section(SavannaNumbers.SCOUTING).orElse(null);
    }

    void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        scouts.removeIf(id -> !(level.getEntity(id) instanceof AbstractAntEntity ant) || !ant.isAlive());
        sent.removeIf(id -> !(level.getEntity(id) instanceof AbstractAntEntity ant) || !ant.isAlive());
        finds.removeIf(find -> now - find.checkedAt > numbers.spotFadeTicks());
        veterans.removeIf(id -> level.getEntity(id) instanceof AbstractAntEntity ant && !ant.isAlive());
        searched.values().removeIf(when -> now - when > numbers.sliceFadeTicks());
    }

    /** How many slices the land round the nest is searched in. */
    private int slices(SavannaNumbers.ScoutNumbers numbers) {
        return Math.max(1, 360 / Math.max(1, numbers.sliceDegrees()));
    }

    /** The search range now (blocks). */
    public int range() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return range > 0 || numbers == null ? range : numbers.firstRange();
    }

    /**
     * Whether the colony wants food: its prey pile holds less than a day's food (a layer about 10 termites, one termite
     * per ant a day, as the pile is eaten).
     */
    public boolean isHungry() {
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        return pile == null || pile.edible() * 10 < Math.max(1, colony.getCount(Caste.WORKER));
    }

    /** Whether it is the raid hours at the nest and dry. */
    public boolean isScoutingTime(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        return numbers != null && numbers.isRaidHour(colony.timeOfDay(level)) && !colony.rainsNow(level);
    }

    /** How many scouts the colony sends at once: one per {@code workers_per_scout} workers, at least 1, at most {@code most_scouts}. */
    public int scoutsWanted() {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        return Math.max(1, Math.min(numbers.mostScouts(), colony.getCount(Caste.WORKER) / Math.max(1, numbers.workersPerScout())));
    }

    /**
     * Whether this ant may set out scouting now: one the scout command sent; otherwise in the raid hours, dry, the
     * colony hungry and short of scouts, and the ant fit (a major, whole, not fighting or hungry). The colony's old
     * scouts go first: a new ant takes an open place only once no fit old scout is about, or none came for
     * {@link #VETERAN_WAIT} ticks.
     */
    public boolean mayScout(ServerLevel level, MatabeleEntity ant) {
        if (scouts.contains(ant.getUUID())) {
            return true;
        }
        if (!fit(ant)) {
            return false;
        }
        if (sent.contains(ant.getUUID())) {
            return true;
        }
        if (scouts.size() >= scoutsWanted() || !isScoutingTime(level) || !isHungry()) {
            return false;
        }
        if (veterans.contains(ant.getUUID())) {
            return true;
        }
        long now = level.getGameTime();
        if (openSince < 0 || now - openSince > VETERAN_WAIT * 4) {
            openSince = now; // (a fresh opening: the old scouts get their chance first)
        }
        return now - openSince >= VETERAN_WAIT || !veteranReady(level);
    }

    /** How long (ticks) a new ant waits for one of the colony's old scouts to take an open scout's place. */
    private static final long VETERAN_WAIT = 200;

    /** Whether one of the colony's old scouts is here, fit and not out already. */
    private boolean veteranReady(ServerLevel level) {
        for (UUID id : veterans) {
            if (!scouts.contains(id) && level.getEntity(id) instanceof MatabeleEntity old && fit(old)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this ant is one of the colony's scouts (it scouted before). */
    public boolean isVeteran(UUID ant) {
        return veterans.contains(ant);
    }

    /** Whether this ant is fit to scout: a major (the scouts in Longhurst and Howse 1979; my reading), whole, not busy fighting, not hungry. */
    private static boolean fit(MatabeleEntity ant) {
        return ant.isAlive() && ant.hasMajorBody() && !ant.isCrippled() && !ant.clamp().isOn() && !ant.isDowned()
                && ant.getFightSpot() == null && !ant.isHungry();
    }

    /** It sets out (counted as a scout until {@link #back}), and is one of the colony's scouts from now on. */
    public void setOut(MatabeleEntity ant) {
        scouts.add(ant.getUUID());
        openSince = -1;
        veterans.remove(ant.getUUID());
        veterans.add(ant.getUUID()); // (to the end: the most recent scout)
        SavannaNumbers.ScoutNumbers numbers = numbers();
        int most = 2 * Math.max(1, numbers == null ? 3 : numbers.mostScouts());
        while (veterans.size() > most) {
            veterans.remove(veterans.iterator().next()); // (the longest unused leaves the job)
        }
    }

    /** It is back, or gave up. */
    public void back(MatabeleEntity ant) {
        scouts.remove(ant.getUUID());
        sent.remove(ant.getUUID());
    }

    /** Whether the scout command sent this ant out (it keeps no hours). */
    public boolean wasSent(UUID ant) {
        return sent.contains(ant);
    }

    /** Whether this ant is out scouting now. */
    public boolean isScouting(UUID ant) {
        return scouts.contains(ant);
    }

    /**
     * What this scout's trip is: the best remembered spot not checked lately (rechecked first: real scouts return to
     * their colony's good spots); otherwise a slice not searched lately, by the colony's dice; with every slice searched
     * empty the range grows ({@code range_step}, up to {@code most_range}) and every slice is fresh again.
     */
    @Nullable
    public Trip plan(ServerLevel level) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        if (numbers == null) {
            return null;
        }
        long now = level.getGameTime();
        int slices = slices(numbers);
        Find best = finds.stream().filter(find -> now - find.checkedAt >= RECHECK_AFTER)
                .max(Comparator.comparingInt(Find::worth)).orElse(null);
        if (best != null) {
            best.checkedAt = now; // (no second scout to it at once)
            return new Trip(best, sliceOf(best.at, slices), angleOf(best.at), range());
        }
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < slices; i++) {
            if (!searched.containsKey(i)) {
                free.add(i);
            }
        }
        if (free.isEmpty()) {
            if (range() < numbers.mostRange()) {
                range = Math.min(numbers.mostRange(), range() + numbers.rangeStep());
                searched.clear();
                for (int i = 0; i < slices; i++) {
                    free.add(i);
                }
            } else {
                free.add(searched.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0));
            }
        }
        int slice = free.get(colony.random().nextInt(free.size()));
        searched.put(slice, now); // (taken: no two scouts in one slice; found empty, it stays marked)
        double width = Math.toRadians(360.0 / slices);
        return new Trip(null, slice, (slice + 0.5) * width, range());
    }

    private int sliceOf(BlockPos at, int slices) {
        double angle = angleOf(at);
        return Math.floorMod((int) Math.floor(angle / (Math.PI * 2 / slices)), slices);
    }

    private double angleOf(BlockPos at) {
        double angle = Math.atan2(at.getZ() - colony.getCenter().getZ(), at.getX() - colony.getCenter().getX());
        return angle < 0 ? angle + Math.PI * 2 : angle;
    }

    /**
     * A scout is home from a find: the spot remembered (a known one refreshed), with its scent line home (nest end
     * first), how much termite it smelt there and who found it. Its slice no longer counts as searched empty.
     */
    public void found(ServerLevel level, BlockPos at, int worth, List<BlockPos> trail, @Nullable UUID scout) {
        long now = level.getGameTime();
        SavannaNumbers.ScoutNumbers numbers = numbers();
        Find known = finds.stream().filter(find -> find.at.closerThan(at, numbers == null ? 5 : numbers.smell() * 2)).findFirst().orElse(null);
        if (known != null) {
            known.worth = worth;
            known.checkedAt = now;
            known.misses = 0;
            known.trail = List.copyOf(trail);
            known.scout = scout;
        } else {
            finds.add(new Find(at.immutable(), worth, now, now, 0, List.copyOf(trail), scout));
        }
        if (numbers != null) {
            searched.remove(sliceOf(at, slices(numbers)));
        }
    }

    /** A remembered spot was checked and nothing smelt there: forgotten after {@code spot_misses} such checks. */
    public void foundEmpty(ServerLevel level, Find find) {
        SavannaNumbers.ScoutNumbers numbers = numbers();
        find.checkedAt = level.getGameTime();
        if (++find.misses >= (numbers == null ? 2 : numbers.spotMisses())) {
            finds.remove(find);
        }
    }

    /** The good spots known now, best first. */
    public List<Find> finds() {
        return finds.stream().sorted(Comparator.comparingInt(Find::worth).reversed()).toList();
    }

    /**
     * The scout command: the fittest real major nearest the entrance sets out now, whatever the hour or the colony's
     * hunger; null if none is fit.
     */
    @Nullable
    public MatabeleEntity sendNow(ServerLevel level) {
        BlockPos entrance = colony.getCenter();
        MatabeleEntity ant = colony.population().realMembers(level).stream()
                .filter(member -> member instanceof MatabeleEntity matabele && fit(matabele) && !scouts.contains(member.getUUID()))
                .map(member -> (MatabeleEntity) member)
                .min(Comparator.comparingDouble(member -> member.blockPosition().distSqr(entrance))).orElse(null);
        if (ant != null) {
            sent.add(ant.getUUID());
        }
        return ant;
    }

    /** The colony list's line: scouts out, the good spots known, how far they search. */
    public String describe(ServerLevel level) {
        StringBuilder line = new StringBuilder(", raids: ").append(scouts.size()).append(" scout(s) out, search range ")
                .append(range()).append(" blocks, ").append(finds.size()).append(" good spot(s)");
        List<Find> best = finds();
        for (int i = 0; i < Math.min(3, best.size()); i++) {
            Find find = best.get(i);
            int away = (int) Math.round(Math.hypot(find.at.getX() - colony.getCenter().getX(), find.at.getZ() - colony.getCenter().getZ()));
            line.append(i == 0 ? " (" : "; ").append(find.at.toShortString()).append(", ").append(away).append(" out, worth ")
                    .append(find.worth).append(", line of ").append(find.trail.size());
        }
        if (!best.isEmpty()) {
            line.append(")");
        }
        return line.toString();
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Find find : finds) {
            CompoundTag one = new CompoundTag();
            one.put("at", NbtUtils.writeBlockPos(find.at));
            one.putInt("worth", find.worth);
            one.putLong("foundAt", find.foundAt);
            one.putLong("checkedAt", find.checkedAt);
            one.putInt("misses", find.misses);
            one.put("trail", new LongArrayTag(find.trail.stream().mapToLong(BlockPos::asLong).toArray()));
            if (find.scout != null) {
                one.putUUID("scout", find.scout);
            }
            list.add(one);
        }
        tag.put("raidFinds", list);
        CompoundTag slices = new CompoundTag();
        searched.forEach((slice, when) -> slices.putLong(Integer.toString(slice), when));
        tag.put("raidSearched", slices);
        tag.putInt("raidRange", range);
        ListTag old = new ListTag();
        veterans.forEach(id -> old.add(NbtUtils.createUUID(id)));
        tag.put("raidScouts", old);
    }

    void load(CompoundTag tag) {
        finds.clear();
        for (Tag t : tag.getList("raidFinds", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            BlockPos at = NbtUtils.readBlockPos(one, "at").orElse(null);
            if (at == null) {
                continue;
            }
            List<BlockPos> trail = new ArrayList<>();
            for (long point : one.getLongArray("trail")) {
                trail.add(BlockPos.of(point));
            }
            finds.add(new Find(at, one.getInt("worth"), one.getLong("foundAt"), one.getLong("checkedAt"), one.getInt("misses"), trail,
                    one.hasUUID("scout") ? one.getUUID("scout") : null));
        }
        searched.clear();
        CompoundTag slices = tag.getCompound("raidSearched");
        for (String key : slices.getAllKeys()) {
            try {
                searched.put(Integer.parseInt(key), slices.getLong(key));
            } catch (NumberFormatException ignored) {
                // (an odd key: skipped)
            }
        }
        range = tag.getInt("raidRange");
        veterans.clear();
        for (Tag t : tag.getList("raidScouts", Tag.TAG_INT_ARRAY)) {
            veterans.add(NbtUtils.loadUUID(t));
        }
    }

    void fillForTests(ServerLevel level) {
        BlockPos at = colony.getCenter().offset(20, 0, 6);
        finds.add(new Find(at, 14, 100, 140, 1, List.of(colony.getCenter(), colony.getCenter().east(2), at.west(2)),
                new UUID(7, 11)));
        searched.put(2, 120L);
        range = 48;
        veterans.add(new UUID(7, 11));
        veterans.add(new UUID(7, 12));
    }

    /** Whether a termite there counts as feeding (out of its mound): not within its colony's mound and a little round it. */
    public static boolean outOfMound(ServerLevel level, BlockPos at, List<MoundAt> mounds) {
        for (MoundAt mound : mounds) {
            if (mound.center.closerThan(at, mound.reach + 2.0)) {
                return false;
            }
        }
        return true;
    }

    /** A living termite mound near: its middle and how far its foot reaches (blocks). */
    public record MoundAt(BlockPos center, int reach) {
    }

    /** The living termite colonies within {@code within} blocks of {@code at}, with their mounds. */
    public static List<Colony> termiteColonies(ServerLevel level, BlockPos at, double within) {
        List<Colony> near = new ArrayList<>();
        for (Colony other : com.antcolonies.colony.ColonyManager.get(level).getColonies()) {
            if (SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(at, within)) {
                near.add(other);
            }
        }
        return near;
    }
}
