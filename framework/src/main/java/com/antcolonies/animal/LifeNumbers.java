package com.antcolonies.animal;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.antcolonies.season.Seasons.Season;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * An animal's life numbers, read from its life file ({@code data/<mod>/animal_life/<animal>.json}, the animal life kit,
 * {@code docs/plans/animal-life-cycle.md}): numbers the user can change without code. Sizes are shares of the animal's
 * usual size (the size its model is drawn at); days are game days (24,000 ticks). Every number has a default, so a file
 * names only what it changes. Breeding is off unless a file says {@code "breeds": true} (step 1 of the plan: the kit
 * changes nothing in-game).
 *
 * @param twoSexes       males and females ({@code "sexes": "two"}), or every animal both ({@code "both"}, as snails)
 * @param maleShare      how many are born male
 * @param maleSize       a grown male's own size, smallest and largest (each animal rolls one between)
 * @param femaleSize     a grown female's
 * @param maxSize        the largest own size a saved animal may load with (its box must stay what its legs can walk)
 * @param youngSize      the size at hatching, a share of the grown size (1 with no young stage)
 * @param youngDays      days young (0: born grown, no young stage)
 * @param teenSize       the size a teen starts at (a share of grown)
 * @param teenDays       days a teen (0: no teen stage, the young grow straight up)
 * @param youngAtSpawn   how many of the world's own spawns are young
 * @param spawnYoungDays how old the world's own young spawns are, youngest and oldest (days; null: in the first third
 *                       of the young days)
 * @param breeds         whether it breeds at all (off until its own step of the plan)
 * @param breedEveryDays days after breeding before it is ready again
 * @param clutchMin      fewest eggs or young a breeding gives
 * @param clutchMax      most
 * @param hatchDays      days from laying to hatching
 * @param seasons        the seasons it breeds in ({@link com.antcolonies.season.Seasons})
 * @param weather        the weather it breeds in
 * @param time           the time of day it breeds at (by the shared day clock, {@link com.antcolonies.colony.Colony#dayTimeAt})
 * @param mateReach      how far it looks for a mate (blocks)
 * @param localLimit     it breeds only while fewer of its own kind than this live within {@link RoomToBreed#REACH}
 * @param care           what a parent does for its eggs or young
 * @param careDays       days a parent keeps caring for its young after they hatch (the tilapia's fry round her head)
 * @param courtshipTicks how long the pair court before mating
 * @param courtshipClip  the courtship clip each plays (empty: the species' own hook decides)
 */
public record LifeNumbers(boolean twoSexes, float maleShare, float[] maleSize, float[] femaleSize, float maxSize,
                          float youngSize, double youngDays, float teenSize, double teenDays, float youngAtSpawn,
                          double[] spawnYoungDays, boolean breeds, double breedEveryDays, int clutchMin, int clutchMax, double hatchDays,
                          Set<Season> seasons, Weather weather, Time time, double mateReach, int localLimit, Care care,
                          double careDays, int courtshipTicks, String courtshipClip) {

    public static final int DAY = 24000;

    /** The numbers of an animal with no life file: two sexes, born grown, 0.85 to 1.15 of its size, no breeding. */
    public static final LifeNumbers DEFAULT = new LifeNumbers(true, 0.5F, new float[] {0.85F, 1.15F},
            new float[] {0.85F, 1.15F}, 1.5F, 1.0F, 0, 1.0F, 0, 0, null, false, 10, 1, 1, 2, EnumSet.of(Season.RAINS),
            Weather.ANY, Time.ANY, 16, 4, Care.NONE, 0, 100, "");

    /** The weather it breeds in. */
    public enum Weather {
        ANY, RAIN, DRY;

        static Weather fromFile(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** The time of day it breeds at (the river catfish spawn at night, step 3 of the plan). */
    public enum Time {
        ANY, DAY, NIGHT;

        static Time fromFile(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** What a parent does for its eggs or young (the kit's care hooks: {@link GuardPlaceGoal}, {@link Carried}). */
    public enum Care {
        /** Nothing: the eggs are left (catfish, snail). */
        NONE,
        /** Stays near the nest and sees off what comes close (crocodile). */
        GUARD,
        /** Carries the young (the tilapia's mouth, the crocodile's jaws). */
        CARRY_YOUNG,
        /** Carries the eggs (the crab's flap). */
        CARRY_EGGS;

        static Care fromFile(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Ticks young. */
    public int youngTicks() {
        return (int) Math.round(youngDays * DAY);
    }

    /** Ticks a teen. */
    public int teenTicks() {
        return (int) Math.round(teenDays * DAY);
    }

    /** The age (ticks since hatching) at which it is grown. */
    public int grownAge() {
        return youngTicks() + teenTicks();
    }

    /** Its stage at this age (old is not an age: {@link AnimalLife} keeps it). */
    public LifeStage stageAt(int age) {
        if (age < youngTicks()) {
            return LifeStage.YOUNG;
        }
        return age < grownAge() ? LifeStage.TEEN : LifeStage.GROWN;
    }

    /**
     * Its size at this age as a share of its grown size: from {@link #youngSize} at hatching up to the teen's start (or
     * to 1 with no teen) through the young days, then to 1 through the teen days.
     */
    public float grownShare(int age) {
        int young = youngTicks();
        int teen = teenTicks();
        if (age < young) {
            float end = teen > 0 ? teenSize : 1.0F;
            return youngSize + (end - youngSize) * age / (float) young;
        }
        if (age < young + teen) {
            return teenSize + (1.0F - teenSize) * (age - young) / (float) teen;
        }
        return 1.0F;
    }

    /** The smallest and largest grown own size of this sex. */
    public float[] sizeOf(Sex sex) {
        return sex == Sex.FEMALE ? femaleSize : maleSize;
    }

    /** Ticks after breeding before it is ready again. */
    public int breedEveryTicks() {
        return (int) Math.round(breedEveryDays * DAY);
    }

    /** Ticks a parent cares for its young after they hatch. */
    public int careTicks() {
        return (int) Math.round(careDays * DAY);
    }

    /**
     * The age (ticks) of a young one the world spawns: between its file's {@code young_at_spawn_days}, or else
     * somewhere in the first third of its young days.
     */
    public int spawnYoungAge(net.minecraft.util.RandomSource random) {
        int young = youngTicks();
        if (young <= 0) {
            return grownAge();
        }
        if (spawnYoungDays == null) {
            return random.nextInt(Math.max(1, young / 3));
        }
        double days = spawnYoungDays[0] + (spawnYoungDays[1] - spawnYoungDays[0]) * random.nextDouble();
        return Math.min(young - 1, (int) Math.round(days * DAY));
    }

    /** Ticks from laying to hatching. */
    public int hatchTicks() {
        return (int) Math.round(hatchDays * DAY);
    }

    /** The numbers in a life file, each missing one taken from {@link #DEFAULT}. Throws on a wrong word or number. */
    public static LifeNumbers read(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        LifeNumbers d = DEFAULT;
        boolean two = !o.has("sexes") || switch (o.get("sexes").getAsString()) {
            case "two" -> true;
            case "both" -> false;
            default -> throw new IllegalArgumentException("sexes must be \"two\" or \"both\", not " + o.get("sexes"));
        };
        float[] size = o.has("size") ? range(o.get("size")) : null;
        float[] male = o.has("male_size") ? range(o.get("male_size")) : size != null ? size : d.maleSize;
        float[] female = o.has("female_size") ? range(o.get("female_size")) : size != null ? size : d.femaleSize;
        float[] clutch = o.has("clutch") ? range(o.get("clutch")) : new float[] {d.clutchMin, d.clutchMax};
        Set<Season> seasons = d.seasons;
        if (o.has("seasons")) {
            seasons = EnumSet.noneOf(Season.class);
            for (JsonElement e : o.getAsJsonArray("seasons")) {
                Season season = Season.fromFile(e.getAsString());
                if (season == null) {
                    throw new IllegalArgumentException("no season called " + e);
                }
                seasons.add(season);
            }
        }
        LifeNumbers out = new LifeNumbers(two, f(o, "male_share", d.maleShare), male, female, f(o, "max_size", d.maxSize),
                f(o, "young_size", d.youngSize), dbl(o, "young_days", d.youngDays), f(o, "teen_size", d.teenSize),
                dbl(o, "teen_days", d.teenDays), f(o, "young_at_spawn", d.youngAtSpawn),
                o.has("young_at_spawn_days") ? days(o.get("young_at_spawn_days")) : d.spawnYoungDays,
                o.has("breeds") ? o.get("breeds").getAsBoolean() : d.breeds, dbl(o, "breed_every_days", d.breedEveryDays),
                Math.round(clutch[0]), Math.round(clutch[1]), dbl(o, "hatch_days", d.hatchDays), Set.copyOf(seasons),
                o.has("weather") ? Weather.fromFile(o.get("weather").getAsString()) : d.weather,
                o.has("time") ? Time.fromFile(o.get("time").getAsString()) : d.time,
                dbl(o, "mate_reach", d.mateReach), o.has("local_limit") ? o.get("local_limit").getAsInt() : d.localLimit,
                o.has("care") ? Care.fromFile(o.get("care").getAsString()) : d.care, dbl(o, "care_days", d.careDays),
                o.has("courtship_ticks") ? o.get("courtship_ticks").getAsInt() : d.courtshipTicks,
                o.has("courtship_clip") ? o.get("courtship_clip").getAsString() : d.courtshipClip);
        out.check();
        return out;
    }

    private void check() {
        if (maleSize[0] > maleSize[1] || femaleSize[0] > femaleSize[1] || clutchMin > clutchMax) {
            throw new IllegalArgumentException("a range's smallest is bigger than its largest");
        }
        if (youngSize <= 0 || youngSize > 1 || teenSize <= 0 || teenSize > 1 || youngDays < 0 || teenDays < 0) {
            throw new IllegalArgumentException("young and teen sizes must be over 0 and at most 1, days 0 or more");
        }
    }

    private static double[] days(JsonElement e) {
        float[] r = range(e);
        if (r[0] < 0 || r[0] > r[1]) {
            throw new IllegalArgumentException("young_at_spawn_days must be two days, smallest first: " + e);
        }
        return new double[] {r[0], r[1]};
    }

    private static float[] range(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 2) {
            throw new IllegalArgumentException("a range is two numbers, smallest and largest: " + e);
        }
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()};
    }

    private static float f(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static double dbl(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }
}
