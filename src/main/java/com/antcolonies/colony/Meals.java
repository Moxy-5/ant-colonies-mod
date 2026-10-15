package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The meals of the colony's members that are numbers (the brain, part 2, step 2b). The user's decision (2026-10-14):
 * they eat whether a player is near or not, by the same rules as the real ones. Kept as counts by when they last ate (to
 * the minute), next to their ages in {@link Population} (a number's age and its hunger are kept apart: two lists, not
 * one per insect). Once a second, the ones whose meal is due eat, the hungriest first, as long as there is food:
 * workers a piece from the food store (a seed: they eat their seeds raw, like the real workers), soldiers a piece of
 * the nurses' food (a fungus nodule). A worker number gone the profile's starve time without a meal dies and leaves its
 * body; a soldier only gets slow without food, so as a number nothing happens to it.
 *
 * <p>Species-neutral: any caste with a meal in its profile (the workers when {@code worker.eats}, the soldiers).
 */
class Meals {
    private final Colony colony;
    /** Caste -> (the minute they last ate -> how many). Saved. */
    private final Map<String, TreeMap<Long, Integer>> lastAte = new LinkedHashMap<>();

    /** What one caste eats: how often, how long until it starves (0: never), and whether from the store or the nurses' food. */
    record Diet(long interval, long starveAfter, boolean fromStore) {
    }

    Meals(Colony colony) {
        this.colony = colony;
    }

    /** What this caste eats, or null if it does not eat (termite workers, so far). */
    @Nullable
    Diet dietOf(String caste) {
        if (WorkerAntEntity.CASTE.equals(caste)) {
            var worker = colony.profile().worker();
            return worker.eats() && worker.mealIntervalTicks() > 0 ? new Diet(worker.mealIntervalTicks(), worker.starveAfterTicks(), true) : null;
        }
        var soldiers = colony.profile().soldiers().orElse(null);
        for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
            if (rank.caste().equals(caste) && soldiers != null && soldiers.mealIntervalTicks() > 0) {
                return new Diet(soldiers.mealIntervalTicks(), 0, false);
            }
        }
        return null;
    }

    private static long minute(long time) {
        return Math.floorDiv(time, Population.AGE_STEP) * Population.AGE_STEP;
    }

    /** One more number of this caste, which last ate at {@code ateAt} ({@link Colony#NOT_SET}: now). */
    void add(String caste, long ateAt, long now) {
        if (dietOf(caste) == null) {
            return;
        }
        lastAte.computeIfAbsent(caste, c -> new TreeMap<>()).merge(minute(ateAt == Colony.NOT_SET ? now : ateAt), 1, Integer::sum);
    }

    /**
     * One number of this caste comes out as a real mob: when it last ate (picked at random, more likely where there are
     * more), or {@code now} if none is known.
     */
    long take(String caste, RandomSource random, long now) {
        TreeMap<Long, Integer> times = lastAte.get(caste);
        if (times == null || times.isEmpty()) {
            return now;
        }
        long minute = pick(times, random);
        remove(times, minute, 1);
        return minute + random.nextInt((int) Population.AGE_STEP);
    }

    private static long pick(TreeMap<Long, Integer> times, RandomSource random) {
        int total = times.values().stream().mapToInt(Integer::intValue).sum();
        int pick = random.nextInt(Math.max(1, total));
        for (Map.Entry<Long, Integer> entry : times.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return times.firstKey();
    }

    private static void remove(TreeMap<Long, Integer> times, long minute, int count) {
        int left = times.getOrDefault(minute, 0) - count;
        if (left > 0) {
            times.put(minute, left);
        } else {
            times.remove(minute);
        }
    }

    private static int total(@Nullable TreeMap<Long, Integer> times) {
        return times == null ? 0 : times.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * The meal lists follow the headcount inside: numbers that joined without a known meal (hatched into the numbers,
     * the population command) ate just now; ones that left without saying so (old age) are taken at random.
     */
    void keepUp(ServerLevel level, Population population) {
        long now = level.getGameTime();
        lastAte.keySet().removeIf(caste -> dietOf(caste) == null);
        for (String caste : population.castesInside()) {
            if (dietOf(caste) == null) {
                continue;
            }
            TreeMap<Long, Integer> times = lastAte.computeIfAbsent(caste, c -> new TreeMap<>());
            int have = total(times);
            int want = population.inside(caste);
            if (have < want) {
                times.merge(minute(now), want - have, Integer::sum);
            }
            for (int extra = have - want; extra > 0 && !times.isEmpty(); extra--) {
                remove(times, pick(times, level.getRandom()), 1);
            }
        }
        lastAte.forEach((caste, times) -> {
            if (population.inside(caste) == 0) {
                times.clear();
            }
        });
    }

    /**
     * Once a second, here or away: the numbers whose meal is due eat, the hungriest first, while there is food; worker
     * numbers that have gone their starve time without food die ({@link Population#starved}). With the colony's hunger
     * off (the game tests), they are all fed.
     */
    void tick(ServerLevel level, Population population) {
        keepUp(level, population);
        long now = level.getGameTime();
        for (Map.Entry<String, TreeMap<Long, Integer>> entry : lastAte.entrySet()) {
            String caste = entry.getKey();
            TreeMap<Long, Integer> times = entry.getValue();
            Diet diet = dietOf(caste);
            if (diet == null || times.isEmpty()) {
                continue;
            }
            if (!colony.isHungerOn()) {
                int all = total(times);
                times.clear();
                times.put(minute(now), all);
                continue;
            }
            int fed = 0;
            boolean food = true;
            for (long minute : new ArrayList<>(times.keySet())) {
                if (now - minute < diet.interval()) {
                    break; // the rest ate later
                }
                int count = times.get(minute);
                int ate = 0;
                while (food && ate < count) {
                    food = diet.fromStore() ? colony.food().eatForNumbers() : colony.life().takeNurseFood(level);
                    if (food) {
                        ate++;
                    }
                }
                remove(times, minute, ate);
                fed += ate;
                if (!food) {
                    break;
                }
            }
            if (fed > 0) {
                times.merge(minute(now), fed, Integer::sum);
            }
            if (diet.starveAfter() > 0) {
                int starved = 0;
                for (long minute : new ArrayList<>(times.keySet())) {
                    if (now - minute < diet.starveAfter()) {
                        break;
                    }
                    starved += times.remove(minute);
                }
                if (starved > 0) {
                    population.starved(level, caste, starved);
                }
            }
        }
    }

    /** How many numbers of all castes have gone longer than a meal without one (for the colony list). */
    int hungry(long now) {
        int hungry = 0;
        for (Map.Entry<String, TreeMap<Long, Integer>> entry : lastAte.entrySet()) {
            Diet diet = dietOf(entry.getKey());
            if (diet == null) {
                continue;
            }
            for (Map.Entry<Long, Integer> group : entry.getValue().entrySet()) {
                if (now - group.getKey() >= diet.interval()) {
                    hungry += group.getValue();
                }
            }
        }
        return hungry;
    }

    /** Tests only: every number of this caste last ate at this game time. */
    void setLastAteForTests(String caste, long time) {
        TreeMap<Long, Integer> times = lastAte.get(caste);
        if (times != null) {
            int all = total(times);
            times.clear();
            times.put(minute(time), all);
        }
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        lastAte.forEach((caste, times) -> times.forEach((minute, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("caste", caste);
            entry.putLong("ateAt", minute);
            entry.putInt("count", count);
            list.add(entry);
        }));
        tag.put("mealsInside", list);
    }

    void load(CompoundTag tag) {
        lastAte.clear();
        ListTag list = tag.getList("mealsInside", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            lastAte.computeIfAbsent(entry.getString("caste"), c -> new TreeMap<>()).merge(entry.getLong("ateAt"), entry.getInt("count"), Integer::sum);
        }
    }
}
