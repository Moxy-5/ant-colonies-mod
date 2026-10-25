package com.antcolonies.species;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.block.PlugBlock;
import com.antcolonies.colony.NestPlan;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A species profile: the numbers one species' colony runs on, read from {@code data/<namespace>/species/<name>.json}
 * (see {@link SpeciesProfiles}). The shared colony code asks the colony's profile instead of using fixed numbers, so a
 * new species (termites) only needs its own file. Times are in ticks (20 ticks = 1 second, a day = 24000 ticks).
 *
 * <p>The profile reads the shared numbers only; each species mod reads its own sections of the same file (Project
 * Apex part 3, T1: the termite mound, the harvester's door, ...) through {@link SpeciesSection}, held here by section
 * ({@link #section}). {@code gardenRooms} is the shared view of a fungus garden's rooms (the nest layout digs them);
 * the savanna mod reads the rest of the {@code garden} object.
 *
 * <p>Not in the profile yet (still in code): the room sizes and the spiral ({@code NestPlan}), the hill
 * ({@code Hill}), the castes' size, health and speed (fixed when the game starts), and the diggable blocks tag (the food
 * the workers gather is in {@link FoodNumbers}).
 */
public record SpeciesProfile(ColonyNumbers colony, WorkerNumbers worker, QueenNumbers queen, BroodNumbers brood,
        FoodNumbers food, RefuseNumbers refuse, NestNumbers nest, Optional<Founding> founding, Optional<GardenRooms> gardenRooms,
        Optional<Laying> laying, Optional<BodyLanguage> bodyLanguage, List<String> parts, Map<SpeciesSection<?>, Object> sections) {

    public SpeciesProfile {
        sections = Map.copyOf(sections);
    }

    /** The shared numbers as read, with no species sections yet ({@link #withSection}). */
    public SpeciesProfile(ColonyNumbers colony, WorkerNumbers worker, QueenNumbers queen, BroodNumbers brood, FoodNumbers food,
            RefuseNumbers refuse, NestNumbers nest, Optional<Founding> founding, Optional<GardenRooms> gardenRooms, Optional<Laying> laying,
            Optional<BodyLanguage> bodyLanguage, List<String> parts) {
        this(colony, worker, queen, brood, food, refuse, nest, founding, gardenRooms, laying, bodyLanguage, parts, Map.of());
    }

    /** The numbers without a parts list (only the parts every colony has; see {@code colony.ColonyParts}), a garden or body language. */
    public SpeciesProfile(ColonyNumbers colony, WorkerNumbers worker, QueenNumbers queen, BroodNumbers brood, FoodNumbers food,
            RefuseNumbers refuse, NestNumbers nest, Optional<Founding> founding, Optional<Laying> laying) {
        this(colony, worker, queen, brood, food, refuse, nest, founding, Optional.empty(), laying, Optional.empty(), List.of());
    }

    /** The same numbers with this parts list. */
    public SpeciesProfile withParts(List<String> parts) {
        return new SpeciesProfile(colony, worker, queen, brood, food, refuse, nest, founding, gardenRooms, laying, bodyLanguage,
                List.copyOf(parts), sections);
    }

    /** A species mod's own section of the file (Apex part 3, T1), or empty if the file has none. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> section(SpeciesSection<T> section) {
        return Optional.ofNullable((T) sections.get(section));
    }

    /** The same numbers with this section as given (built-in numbers and tests). */
    public <T> SpeciesProfile withSection(SpeciesSection<T> section, T numbers) {
        Map<SpeciesSection<?>, Object> more = new LinkedHashMap<>(sections);
        more.put(section, numbers);
        return new SpeciesProfile(colony, worker, queen, brood, food, refuse, nest, founding, gardenRooms, laying, bodyLanguage, parts, more);
    }

    /**
     * Reads a species file: the shared numbers ({@link #CODEC}), then every section a species mod signed up
     * ({@link SpeciesSection#all}). A broken section makes the whole file broken, as a broken shared number does.
     */
    public static DataResult<SpeciesProfile> read(JsonElement file) {
        DataResult<SpeciesProfile> result = CODEC.parse(JsonOps.INSTANCE, file);
        for (SpeciesSection<?> section : SpeciesSection.all()) {
            result = result.flatMap(profile -> addSection(profile, section, file));
        }
        return result;
    }

    private static <T> DataResult<SpeciesProfile> addSection(SpeciesProfile profile, SpeciesSection<T> section, JsonElement file) {
        return section.read(file).map(found -> found.map(numbers -> profile.withSection(section, numbers)).orElse(profile));
    }

    /**
     * The shared view of a fungus garden (the {@code garden} object; termites): how many small garden rooms the nest has
     * round the royal chamber ({@code NestPlan.gardens}), at most {@code rooms}; the workers dig the first as soon as
     * the colony has workers and one more for every {@code workersPerRoom} workers. The savanna mod reads the garden's
     * other numbers (the nodules, the comb, the mushrooms).
     */
    public record GardenRooms(int rooms, int workersPerRoom) {
        public static final Codec<GardenRooms> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("rooms").forGetter(GardenRooms::rooms),
                Codec.INT.fieldOf("workers_per_room").forGetter(GardenRooms::workersPerRoom)
        ).apply(i, GardenRooms::new));

        /** How many garden rooms a colony with this many workers wants (none while it has no workers). */
        public int roomsFor(int workers) {
            if (workers <= 0) {
                return 0;
            }
            return Math.min(rooms, 1 + workers / Math.max(1, workersPerRoom));
        }
    }

    /**
     * The whole colony. {@code maxWorkers}: young grow into workers only while there are fewer. {@code nursesFrom}: from
     * how many workers on there is one nurse, two nurses, and so on (one number per nurse, smallest first).
     */
    public record ColonyNumbers(int maxWorkers, List<Integer> nursesFrom, int realCap, int crew, int watchRadius, int swapsPerSecond,
            Optional<DangerNumbers> danger, Optional<LostNumbers> lost, Optional<NeighbourNumbers> neighbours) {
        public static final Codec<ColonyNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_workers").forGetter(ColonyNumbers::maxWorkers),
                Codec.INT.listOf().fieldOf("nurses_from_workers").forGetter(ColonyNumbers::nursesFrom),
                Codec.INT.optionalFieldOf("real_cap", 50).forGetter(ColonyNumbers::realCap),
                Codec.INT.optionalFieldOf("crew", 10).forGetter(ColonyNumbers::crew),
                Codec.INT.optionalFieldOf("watch_radius", 48).forGetter(ColonyNumbers::watchRadius),
                Codec.INT.optionalFieldOf("swaps_per_second", 2).forGetter(ColonyNumbers::swapsPerSecond),
                DangerNumbers.CODEC.optionalFieldOf("danger").forGetter(ColonyNumbers::danger),
                LostNumbers.CODEC.optionalFieldOf("lost").forGetter(ColonyNumbers::lost),
                NeighbourNumbers.CODEC.optionalFieldOf("neighbours").forGetter(ColonyNumbers::neighbours)
        ).apply(i, ColonyNumbers::new));

        public ColonyNumbers(int maxWorkers, List<Integer> nursesFrom, int realCap, int crew, int watchRadius, int swapsPerSecond,
                Optional<DangerNumbers> danger) {
            this(maxWorkers, nursesFrom, realCap, crew, watchRadius, swapsPerSecond, danger, Optional.empty(), Optional.empty());
        }

        public ColonyNumbers(int maxWorkers, List<Integer> nursesFrom, int realCap, int crew, int watchRadius, int swapsPerSecond) {
            this(maxWorkers, nursesFrom, realCap, crew, watchRadius, swapsPerSecond, Optional.empty(), Optional.empty(), Optional.empty());
        }

        /**
         * With the brain's numbers at their defaults (see {@code Population}): at most {@code realCap} real mobs while a
         * player is near, a crew of {@code crew} real workers otherwise, a player counts as near within
         * {@code watchRadius} blocks of the nest, and {@code swapsPerSecond} insects come out of or go back into the
         * numbers each second.
         */
        public ColonyNumbers(int maxWorkers, List<Integer> nursesFrom) {
            this(maxWorkers, nursesFrom, 50, 10, 48, 2);
        }

        /** How many of {@code workers} workers are nurses. */
        public int nurses(int workers) {
            int nurses = 0;
            for (int from : nursesFrom) {
                if (workers >= from) {
                    nurses++;
                }
            }
            return nurses;
        }
    }

    /**
     * Harm out of sight (brain step 4g, step 4; the user's decisions of 2026-10-18): a number's trip that still goes near
     * remembered danger can be lost. Its chance is {@code perPoint} for each point of danger there, at most {@code most}; in
     * loaded land a remembered place counts only while a hostile is really there (nobody dies of a mere memory), in
     * unloaded land the memory decides. Who can be lost: patrol numbers checking a way through danger ({@code patrollers})
     * and forager numbers at food near danger ({@code foragers}: termites, whose foragers are not kept away from where a
     * hostile was only seen). A lost member only counts in the danger memory (as a death) if its nestmates find the body:
     * certain within {@code foundWithin} blocks of the nest's middle, {@code foundDropPerBlock} less likely each block
     * farther out (the user's idea). My picks, to confirm: 10% a point, at most 50%; found for sure within 6, 3% less a block.
     */
    public record LostNumbers(double perPoint, double most, boolean patrollers, boolean foragers, double foundWithin, double foundDropPerBlock) {
        public static final Codec<LostNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("per_point", 0.1).forGetter(LostNumbers::perPoint),
                Codec.DOUBLE.optionalFieldOf("most", 0.5).forGetter(LostNumbers::most),
                Codec.BOOL.optionalFieldOf("patrollers", false).forGetter(LostNumbers::patrollers),
                Codec.BOOL.optionalFieldOf("foragers", false).forGetter(LostNumbers::foragers),
                Codec.DOUBLE.optionalFieldOf("found_within", 6.0).forGetter(LostNumbers::foundWithin),
                Codec.DOUBLE.optionalFieldOf("found_drop_per_block", 0.03).forGetter(LostNumbers::foundDropPerBlock)
        ).apply(i, LostNumbers::new));

        /** The chance a body lying {@code fromNest} blocks (sideways) from the nest's middle is found by nestmates. */
        public double foundChance(double fromNest) {
            return fromNest <= foundWithin ? 1.0 : Math.max(0.0, 1.0 - foundDropPerBlock * (fromNest - foundWithin));
        }
    }

    /**
     * Neighbours (brain step 4g, step 5, {@code colony.Neighbours}; the user's decisions of 2026-10-17 and 2026-10-18). A
     * member within {@code within} blocks of an insect of another colony of its species, or one standing on another such
     * colony's trunk trail, records a meeting there (the same insect or trail again only after {@code meetEveryTicks});
     * meetings within {@code mergeWithin} blocks are one place, each adds 1; a place's strength halves every
     * {@code halfLifeTicks} and below {@code forgetBelow} it is forgotten. The colony yields to a place once its strength
     * reaches the meetings needed for its age: {@code youngMeetings} at founding, falling evenly to 1 at
     * {@code oldAtDays} game days old (Gordon and Kulig: old colonies turn away, young ones push on). Yielding: no
     * foraging, trail growth or new trail past the place within {@code turnDegrees} of its direction from the nest
     * (harvester ants); a gallery piece reaching within {@code within} of the neighbour's galleries is walled off with soil
     * and no tip grows that way ({@code wallGalleries}, termites). A meeting is not danger: nobody is hurt.
     */
    public record NeighbourNumbers(double within, int meetEveryTicks, double mergeWithin, int halfLifeTicks, double forgetBelow,
            double youngMeetings, double oldAtDays, double turnDegrees, boolean wallGalleries) {
        public static final Codec<NeighbourNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("within", 3.0).forGetter(NeighbourNumbers::within),
                Codec.INT.optionalFieldOf("meet_every_ticks", 1200).forGetter(NeighbourNumbers::meetEveryTicks),
                Codec.DOUBLE.optionalFieldOf("merge_within", 4.0).forGetter(NeighbourNumbers::mergeWithin),
                Codec.INT.optionalFieldOf("half_life_ticks", 48000).forGetter(NeighbourNumbers::halfLifeTicks),
                Codec.DOUBLE.optionalFieldOf("forget_below", 0.5).forGetter(NeighbourNumbers::forgetBelow),
                Codec.DOUBLE.optionalFieldOf("young_meetings", 4.0).forGetter(NeighbourNumbers::youngMeetings),
                Codec.DOUBLE.optionalFieldOf("old_at_days", 8.0).forGetter(NeighbourNumbers::oldAtDays),
                Codec.DOUBLE.optionalFieldOf("turn_degrees", 30.0).forGetter(NeighbourNumbers::turnDegrees),
                Codec.BOOL.optionalFieldOf("wall_galleries", false).forGetter(NeighbourNumbers::wallGalleries)
        ).apply(i, NeighbourNumbers::new));

        /** The meetings a colony this many game days old needs at one place before it yields there. */
        public double needed(double ageDays) {
            double old = Math.max(0, Math.min(1, ageDays / Math.max(0.001, oldAtDays)));
            return youngMeetings - (youngMeetings - 1) * old;
        }
    }

    /**
     * The danger memory (brain step 4g, {@code colony.DangerMap}; the user's decisions of 2026-10-17). A member hurt by a
     * living thing adds {@code hurt} to the place, one killed {@code killed}, a hostile mob seen within {@code seenWithin}
     * blocks of a member on the surface {@code seen} (the same mob again only after {@code seenEveryTicks}); places within
     * {@code mergeWithin} blocks are one, a place holds at most {@code most}. The strength halves every
     * {@code halfLifeTicks}; below {@code forgetBelow} the place is forgotten. Ground within {@code reach} blocks of a place
     * is dangerous while the strengths there add up to {@code dangerousFrom}; the nest's own ground (within {@code home}
     * blocks of its middle) never is. {@code keepAway}: the foragers leave dangerous ground and the trails through it alone
     * (harvester ants). {@code closeHoles}: the colony plugs and closes its foraging holes where its members were attacked
     * (termites, step 3: every hole within {@code reach} of an attack, and every hole whose gallery out from the nest runs
     * that near one, until the danger fades); a place attacked again and again (at least 2 separate attacks, BUGS 65: one
     * attack's hurts and deaths come less than {@code seenEveryTicks} apart) and once as strong as {@code leaveFrom} is left for good: its
     * holes within {@code mergeWithin} are never opened again, and a new hole is opened on the same gallery at least
     * {@code newHoleFrom} blocks from it. My picks, to confirm: a death 4 (kept away from about 2 days, remembered 3), a
     * hurt 2, a sighting 1, halving every day; a hole left after 6 (two deaths, or a death and a hurt) over 2 attacks, its new hole 7 out. Each member counts once an
     * attack, at its worst wound (BUGS 65).
     */
    public record DangerNumbers(double hurt, double killed, double seen, double seenWithin, int seenEveryTicks, double mergeWithin,
            double reach, int halfLifeTicks, double forgetBelow, double dangerousFrom, double most, double home, boolean keepAway,
            boolean closeHoles, double leaveFrom, double newHoleFrom) {
        public static final Codec<DangerNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("hurt", 2.0).forGetter(DangerNumbers::hurt),
                Codec.DOUBLE.optionalFieldOf("killed", 4.0).forGetter(DangerNumbers::killed),
                Codec.DOUBLE.optionalFieldOf("seen", 1.0).forGetter(DangerNumbers::seen),
                Codec.DOUBLE.optionalFieldOf("seen_within", 4.0).forGetter(DangerNumbers::seenWithin),
                Codec.INT.optionalFieldOf("seen_every_ticks", 1200).forGetter(DangerNumbers::seenEveryTicks),
                Codec.DOUBLE.optionalFieldOf("merge_within", 4.0).forGetter(DangerNumbers::mergeWithin),
                Codec.DOUBLE.optionalFieldOf("reach", 6.0).forGetter(DangerNumbers::reach),
                Codec.INT.optionalFieldOf("half_life_ticks", 24000).forGetter(DangerNumbers::halfLifeTicks),
                Codec.DOUBLE.optionalFieldOf("forget_below", 0.5).forGetter(DangerNumbers::forgetBelow),
                Codec.DOUBLE.optionalFieldOf("dangerous_from", 1.0).forGetter(DangerNumbers::dangerousFrom),
                Codec.DOUBLE.optionalFieldOf("most", 12.0).forGetter(DangerNumbers::most),
                Codec.DOUBLE.optionalFieldOf("home", 6.0).forGetter(DangerNumbers::home),
                Codec.BOOL.optionalFieldOf("keep_away", false).forGetter(DangerNumbers::keepAway),
                Codec.BOOL.optionalFieldOf("close_holes", false).forGetter(DangerNumbers::closeHoles),
                Codec.DOUBLE.optionalFieldOf("leave_from", 6.0).forGetter(DangerNumbers::leaveFrom),
                Codec.DOUBLE.optionalFieldOf("new_hole_from", 7.0).forGetter(DangerNumbers::newHoleFrom)
        ).apply(i, DangerNumbers::new));
    }

    /**
     * A worker eats once every {@code mealIntervalTicks}; without food it slows down, then starves. {@code eats}: whether
     * the workers eat at all. {@code diet}: what they eat by age (see {@link WorkerDiet}). {@code restNearEntrance}: above
     * 0, a worker with nothing to do goes inside and rests within that many blocks of the nest's entrance (real red
     * harvester ants: the spare workers wait in the upper chambers by the entrance until they are needed, they do not
     * stroll about outside); 0, it strolls about wherever it is. {@code restInRooms}: a worker that stays in, with nothing
     * to do, goes down into the nest's rooms and strolls only in them, never resting in the shafts and tunnels between
     * (real Megaponera: the colony crowds into its chamber with the queen and the brood).
     */
    public record WorkerNumbers(int mealIntervalTicks, int slowAfterTicks, int starveAfterTicks, int nurseUntilTicks, int builderUntilTicks,
            int lifespanTicks, boolean eats, WorkerDiet diet, int restNearEntrance, boolean restInRooms) {
        public static final Codec<WorkerNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("meal_interval_ticks").forGetter(WorkerNumbers::mealIntervalTicks),
                Codec.INT.fieldOf("slow_after_ticks").forGetter(WorkerNumbers::slowAfterTicks),
                Codec.INT.fieldOf("starve_after_ticks").forGetter(WorkerNumbers::starveAfterTicks),
                Codec.INT.optionalFieldOf("nurse_until_ticks", 72000).forGetter(WorkerNumbers::nurseUntilTicks),
                Codec.INT.optionalFieldOf("builder_until_ticks", 192000).forGetter(WorkerNumbers::builderUntilTicks),
                Codec.INT.optionalFieldOf("lifespan_ticks", 0).forGetter(WorkerNumbers::lifespanTicks),
                Codec.BOOL.optionalFieldOf("eats", true).forGetter(WorkerNumbers::eats),
                WorkerDiet.CODEC.optionalFieldOf("diet", WorkerDiet.STORE_ONLY).forGetter(WorkerNumbers::diet),
                Codec.INT.optionalFieldOf("rest_near_entrance", 0).forGetter(WorkerNumbers::restNearEntrance),
                Codec.BOOL.optionalFieldOf("rest_in_rooms", false).forGetter(WorkerNumbers::restInRooms)
        ).apply(i, WorkerNumbers::new));

        public WorkerNumbers(int mealIntervalTicks, int slowAfterTicks, int starveAfterTicks, int nurseUntilTicks, int builderUntilTicks,
                int lifespanTicks, boolean eats, WorkerDiet diet) {
            this(mealIntervalTicks, slowAfterTicks, starveAfterTicks, nurseUntilTicks, builderUntilTicks, lifespanTicks, eats, diet, 0, false);
        }

        public WorkerNumbers(int mealIntervalTicks, int slowAfterTicks, int starveAfterTicks, int nurseUntilTicks, int builderUntilTicks) {
            this(mealIntervalTicks, slowAfterTicks, starveAfterTicks, nurseUntilTicks, builderUntilTicks, 0);
        }

        public WorkerNumbers(int mealIntervalTicks, int slowAfterTicks, int starveAfterTicks, int nurseUntilTicks, int builderUntilTicks,
                int lifespanTicks) {
            this(mealIntervalTicks, slowAfterTicks, starveAfterTicks, nurseUntilTicks, builderUntilTicks, lifespanTicks, true, WorkerDiet.STORE_ONLY);
        }

        /**
         * How long (in ticks since it grew up) this worker lives before it dies of old age: the profile's
         * {@code lifespan_ticks}, 10% more or less for each worker ({@code share} 0 to 1, fixed per worker), so they do not
         * all die on the same day. 0: workers do not die of old age.
         */
        public long lifespanFor(double share) {
            return lifespanTicks <= 0 ? 0 : Math.round(lifespanTicks * (0.9 + 0.2 * share));
        }

        /** The job a worker of this age (in ticks since it grew up) usually does: nurse, then builder, then forager. */
        public com.antcolonies.colony.ColonyRole roleForAge(long age) {
            return age < nurseUntilTicks ? com.antcolonies.colony.ColonyRole.NURSE
                    : age < builderUntilTicks ? com.antcolonies.colony.ColonyRole.BUILDER : com.antcolonies.colony.ColonyRole.FORAGER;
        }
    }

    /** Where a worker's meal comes from: a piece of the food store (a seed, a layer of comb) or of the nurses' food (bread, a nodule). */
    public enum MealFood {
        STORE, NURSE_FOOD;

        public static final Codec<MealFood> CODEC = Codec.STRING.xmap(name -> "nurse_food".equals(name) ? NURSE_FOOD : STORE,
                food -> food == NURSE_FOOD ? "nurse_food" : "store");

        public MealFood other() {
            return this == STORE ? NURSE_FOOD : STORE;
        }
    }

    /**
     * What the workers eat (species-neutral). {@code young}: the meal of a worker of nurse age, {@code older}: of the
     * builders and foragers (Macrotermes: the young eat fungus nodules, the older ones old comb; the harvester ant: all a
     * seed from the store). A worker whose own food is not there eats the other kind. {@code workersPerPiece}: how many
     * meals one piece makes (a layer of comb feeds 4 termites, a seed one ant). {@code hungryFromFirstFood}: the workers'
     * hunger clocks only start once the colony's store has held its first piece (a young termite colony has no garden
     * yet: its first workers live on what the royal pair gave them). {@code mouthToMouth}: the nurses pass the nurses'
     * food on mouth to mouth (trophallaxis: Macrotermes nurses eat the nodules and feed the queen, king, soldiers and
     * nymphs from their crop, head to head) instead of carrying it in their jaws (the harvester ant lays bread on a larva).
     */
    public record WorkerDiet(MealFood young, MealFood older, int workersPerPiece, boolean hungryFromFirstFood, boolean mouthToMouth) {
        public static final WorkerDiet STORE_ONLY = new WorkerDiet(MealFood.STORE, MealFood.STORE, 1, false, false);
        public static final Codec<WorkerDiet> CODEC = RecordCodecBuilder.create(i -> i.group(
                MealFood.CODEC.optionalFieldOf("young", MealFood.STORE).forGetter(WorkerDiet::young),
                MealFood.CODEC.optionalFieldOf("older", MealFood.STORE).forGetter(WorkerDiet::older),
                Codec.INT.optionalFieldOf("workers_per_piece", 1).forGetter(WorkerDiet::workersPerPiece),
                Codec.BOOL.optionalFieldOf("hungry_from_first_food", false).forGetter(WorkerDiet::hungryFromFirstFood),
                Codec.BOOL.optionalFieldOf("mouth_to_mouth", false).forGetter(WorkerDiet::mouthToMouth)
        ).apply(i, WorkerDiet::new));

        /** What a worker usually eats in this job by age. */
        public MealFood forRole(com.antcolonies.colony.ColonyRole role) {
            return role == com.antcolonies.colony.ColonyRole.NURSE ? young : older;
        }
    }

    /**
     * The queen. {@code meal}: pieces of food for one egg, which she carries {@code incubationTicks}. {@code richMeal}:
     * pieces of rich food that start a burst of {@code burstEggs} eggs, one every {@code burstIntervalTicks}.
     * {@code starveTicks}: she dies after this long without food while she wants some.
     */
    public record QueenNumbers(int meal, int richMeal, int burstEggs, int burstIntervalTicks, int incubationTicks, long starveTicks) {
        public static final Codec<QueenNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("meal").forGetter(QueenNumbers::meal),
                Codec.INT.fieldOf("rich_meal").forGetter(QueenNumbers::richMeal),
                Codec.INT.fieldOf("burst_eggs").forGetter(QueenNumbers::burstEggs),
                Codec.INT.fieldOf("burst_interval_ticks").forGetter(QueenNumbers::burstIntervalTicks),
                Codec.INT.fieldOf("incubation_ticks").forGetter(QueenNumbers::incubationTicks),
                Codec.LONG.fieldOf("starve_ticks").forGetter(QueenNumbers::starveTicks)
        ).apply(i, QueenNumbers::new));
    }

    /**
     * The young. {@code maxYoung}: eggs and larvae together (the queen is not fed while there are that many). An egg
     * hatches {@code hatchTicks} after it is put in its place; a larva needs {@code larvaMeal} pieces of food and dies
     * after {@code larvaStarveTicks} without.
     */
    public record BroodNumbers(int maxYoung, int hatchTicks, int larvaMeal, long larvaStarveTicks) {
        public static final Codec<BroodNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_young").forGetter(BroodNumbers::maxYoung),
                Codec.INT.fieldOf("hatch_ticks").forGetter(BroodNumbers::hatchTicks),
                Codec.INT.fieldOf("larva_meal").forGetter(BroodNumbers::larvaMeal),
                Codec.LONG.fieldOf("larva_starve_ticks").forGetter(BroodNumbers::larvaStarveTicks)
        ).apply(i, BroodNumbers::new));
    }

    /**
     * Food. {@code radius}: how far (sideways, in blocks) from the entrance the workers look for food. {@code items}:
     * the item tag of the food they pick up off the ground (seeds, sticks), one item at a time. {@code plants}: plants
     * they cut pieces off where they grow (a tuft of grass, a dead bush, a hay bale), see {@link PlantFood}; every
     * piece is a {@code piece} item. The colony keeps its food in the food store as heaps of the {@code pile} block
     * (a {@link SeedPileBlock}), one layer per piece. Bread, for species that mill it: {@code millTicks} to mill one
     * seed into bread, and {@code breadStock} pieces the nurses keep ready for the queen; a species without bread leaves
     * both out. {@code forageAt}: when the foragers go out, any of "day", "night" and "rain" (optional, all three:
     * always); outside those times the workers stay inside (see {@code Colony.isForagingTime}). {@code findRadius}
     * (optional, 0: the colony knows every plant in its area at once): a plant is only gone for once a worker has
     * been within this many blocks of it and brought the news home, and foragers with nothing good to go for scout
     * the area (see {@code FoodMap}). {@code trails} (optional, the defaults of {@link Trails}): how the colony's
     * foraging trails get stronger with use and fade unused. {@code dayClock} (optional): the foragers' daily round.
     * {@code search} (optional): each forager searches alone and remembers its own spot (see {@link SearchNumbers}).
     * A species' own food numbers are read by its own mod (Apex part 3, T1, {@link SpeciesSection}): the savanna's
     * grass growing back ({@code regrowth}), the desert's door and patrol ({@code door}, {@code patrol}).
     */
    public record FoodNumbers(int radius, String items, List<PlantFood> plants, String piece, String pile, int millTicks, int breadStock,
            List<String> forageAt, int findRadius, Trails trails, Optional<DayClock> dayClock, Optional<SearchNumbers> search) {
        /** Foraging at any time: day, night and in the rain. */
        public static final List<String> ALWAYS = List.of("day", "night", "rain");

        public FoodNumbers(int radius, String items, List<PlantFood> plants, String piece, String pile, int millTicks, int breadStock,
                List<String> forageAt, int findRadius, Trails trails, Optional<DayClock> dayClock) {
            this(radius, items, plants, piece, pile, millTicks, breadStock, forageAt, findRadius, trails, dayClock, Optional.empty());
        }

        public FoodNumbers(int radius, String items, List<PlantFood> plants, String piece, String pile, int millTicks, int breadStock,
                List<String> forageAt, int findRadius, Trails trails) {
            this(radius, items, plants, piece, pile, millTicks, breadStock, forageAt, findRadius, trails, Optional.empty());
        }

        public FoodNumbers(int radius, String items, List<PlantFood> plants, String piece, String pile, int millTicks, int breadStock,
                List<String> forageAt, int findRadius) {
            this(radius, items, plants, piece, pile, millTicks, breadStock, forageAt, findRadius, Trails.DEFAULT);
        }

        public static final Codec<FoodNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("radius").forGetter(FoodNumbers::radius),
                Codec.STRING.fieldOf("items").forGetter(FoodNumbers::items),
                PlantFood.CODEC.listOf().fieldOf("plants").forGetter(FoodNumbers::plants),
                Codec.STRING.fieldOf("piece").forGetter(FoodNumbers::piece),
                Codec.STRING.fieldOf("pile").forGetter(FoodNumbers::pile),
                Codec.INT.optionalFieldOf("mill_ticks", 0).forGetter(FoodNumbers::millTicks),
                Codec.INT.optionalFieldOf("bread_stock", 0).forGetter(FoodNumbers::breadStock),
                Codec.STRING.listOf().optionalFieldOf("forage_at", ALWAYS).forGetter(FoodNumbers::forageAt),
                Codec.INT.optionalFieldOf("find_radius", 0).forGetter(FoodNumbers::findRadius),
                Trails.CODEC.optionalFieldOf("trails", Trails.DEFAULT).forGetter(FoodNumbers::trails),
                DayClock.CODEC.optionalFieldOf("day_clock").forGetter(FoodNumbers::dayClock),
                SearchNumbers.CODEC.optionalFieldOf("search").forGetter(FoodNumbers::search)
        ).apply(i, FoodNumbers::new));

        /**
         * Whether the foragers go out now: in the rain if "rain" is listed (in a dry biome such as the savanna no rain
         * falls, but the sky clouds over: an overcast day), otherwise by day or by night as listed.
         */
        public boolean foragesNow(boolean day, boolean raining) {
            return raining && forageAt.contains("rain") || forageAt.contains(day ? "day" : "night");
        }

        /** Whether the nurses mill the food into bread (the harvester ant's seeds). */
        public boolean makesBread() {
            return breadStock > 0;
        }

        /** The food items picked up off the ground (the seeds tag if the name is broken). */
        public TagKey<Item> itemTag() {
            ResourceLocation id = ResourceLocation.tryParse(items);
            return TagKey.create(Registries.ITEM, id != null ? id
                    : ResourceLocation.fromNamespaceAndPath(com.antcolonies.ColonyFrameworkMod.MODID, "seeds"));
        }

        /** What a piece cut off a plant is (a wheat seed if the name is unknown). */
        public Item pieceItem() {
            ResourceLocation id = ResourceLocation.tryParse(piece);
            return id == null ? Items.WHEAT_SEEDS : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.WHEAT_SEEDS);
        }

        /**
         * The heap block of the food store. A name that is no heap block stops the species files loading
         * ({@link #checkPile}), so this is always one in the game.
         */
        public SeedPileBlock pileBlock() {
            ResourceLocation id = ResourceLocation.tryParse(pile);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block instanceof SeedPileBlock heap) {
                return heap;
            }
            throw new IllegalStateException("The food store's heap block \"" + pile + "\" is not a heap block");
        }

        /**
         * Checks the heap block when the species files load (the split, step S3: before, an unknown name quietly fell
         * back to the harvester's seed pile), so a wrong name stops the loading with a clear message.
         */
        public void checkPile(ResourceLocation species) {
            ResourceLocation id = ResourceLocation.tryParse(pile);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (!(block instanceof SeedPileBlock)) {
                throw new IllegalStateException("The species file " + species + " names \"" + pile + "\" as its food store's \"pile\", which is no heap block");
            }
        }

        /** The plant this block is as food, or null if it is none. */
        @Nullable
        public PlantFood plantFor(BlockState state) {
            for (PlantFood plant : plants) {
                if (plant.matches(state)) {
                    return plant;
                }
            }
            return null;
        }
    }

    /**
     * A plant the workers cut food off: {@code block} is a block id ("minecraft:dead_bush") or a block tag
     * ("#minecraft:leaves"). One plant gives {@code pieces} pieces and is then used up (it breaks and drops nothing).
     * {@code onlyDecaying}: only dying leaves count (cut off from their tree, about to fall), never living ones.
     * {@code foragers}: how many workers may cut at it at the same time (a big plant like a hay bale feeds several).
     * {@code state} (optional): block state values the plant must have, for example {@code {"growth": "dry"}} so
     * termites only take Dry Grass once it has dried; a block without one of these properties never matches.
     */
    /**
     * The colony's foraging trails (brain part 4, step 4a; species-neutral). Every trip along a trail makes it one
     * stronger, up to {@code mostStrength}; unused, its strength halves every {@code halfLifeTicks}, and below
     * {@code fadeBelow} the trail has faded and is forgotten. So a trail used every day lasts, and one nobody uses
     * fades over several days. (A species' own trail numbers, such as the harvester's lasting trunk trails, are read
     * by its own mod: Apex part 3, T1.)
     */
    public record Trails(int halfLifeTicks, double mostStrength, double fadeBelow) {
        public static final Trails DEFAULT = new Trails(24000, 64.0, 1.0);
        public static final Codec<Trails> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("half_life_ticks", 24000).forGetter(Trails::halfLifeTicks),
                Codec.DOUBLE.optionalFieldOf("most_strength", 64.0).forGetter(Trails::mostStrength),
                Codec.DOUBLE.optionalFieldOf("fade_below", 1.0).forGetter(Trails::fadeBelow)
        ).apply(i, Trails::new));

        /** A strength of {@code strength} at some time, {@code ticks} later with no use. */
        public double decayed(double strength, long ticks) {
            return ticks <= 0 ? strength : strength * Math.pow(0.5, ticks / (double) Math.max(1, halfLifeTicks));
        }
    }

    /**
     * A day forager's daily round (real red harvester ants, <i>Pogonomyrmex barbatus</i>: out in the morning once the
     * patrollers are back, in again when the ground gets too hot to walk on, out for an afternoon round, in before
     * dusk; never at night and never in the rain). Times are Minecraft day times (0 is sunrise at 6:00, 1000 ticks an
     * hour). The foragers are out from {@code outFrom} to {@code inBy}, except for a midday rest centred on
     * {@code restMiddle}. The rest's length goes by the biome's heat (its base temperature): none at {@code restFromHeat}
     * or cooler, {@code restMost} ticks at {@code restFullHeat} or hotter, in between in proportion. With
     * {@code notInRain} they stay in while rain falls on the nest (a dry biome such as the badlands gets no rain).
     */
    public record DayClock(int outFrom, int inBy, int restMiddle, int restMost, float restFromHeat, float restFullHeat, boolean notInRain) {
        public static final Codec<DayClock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("out_from", 0).forGetter(DayClock::outFrom),
                Codec.INT.optionalFieldOf("in_by", 11000).forGetter(DayClock::inBy),
                Codec.INT.optionalFieldOf("rest_middle", 7000).forGetter(DayClock::restMiddle),
                Codec.INT.optionalFieldOf("rest_most", 4000).forGetter(DayClock::restMost),
                Codec.FLOAT.optionalFieldOf("rest_from_heat", 0.5F).forGetter(DayClock::restFromHeat),
                Codec.FLOAT.optionalFieldOf("rest_full_heat", 2.0F).forGetter(DayClock::restFullHeat),
                Codec.BOOL.optionalFieldOf("not_in_rain", true).forGetter(DayClock::notInRain)
        ).apply(i, DayClock::new));

        /** How long the midday rest is (in ticks) in a biome this hot. */
        public int restTicks(float heat) {
            float share = (heat - restFromHeat) / Math.max(0.01F, restFullHeat - restFromHeat);
            return Math.round(restMost * Math.max(0.0F, Math.min(1.0F, share)));
        }

        /** The day time the midday rest begins (it ends {@link #restTicks} later). */
        public int restStart(float heat) {
            return restMiddle - restTicks(heat) / 2;
        }

        /** Whether the foragers are out at this day time ({@code dayTime} as Level#getDayTime) in a biome this hot. */
        public boolean outAt(long dayTime, float heat, boolean raining) {
            if (raining && notInRain) {
                return false;
            }
            long time = Math.floorMod(dayTime, 24000L);
            if (time < outFrom || time >= inBy) {
                return false;
            }
            int start = restStart(heat);
            return time < start || time >= start + restTicks(heat);
        }

        /** "6:00", "13:24": a day time as a clock time. */
        public static String clock(long dayTime) {
            long minutes = (Math.floorMod(dayTime, 24000L) * 60 / 1000 + 6 * 60) % (24 * 60);
            return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
        }
    }

    /**
     * Searching alone with site fidelity (brain step 4c; real red harvester ants, <i>Pogonomyrmex barbatus</i>: no
     * forager tells the others of a seed; each walks out along a trunk trail, searches alone in a meandering walk and
     * goes back to where it last found food). {@code alone}: what a worker finds (within {@code food.find_radius}) is its
     * own, never told to the colony. A searcher looks within {@code wander} blocks of its search centre; a trip to its
     * own spot that finds nothing in {@code spotTicks} counts as empty, and {@code emptyToDrop} empties drop the spot.
     * It searches until it finds food (or the foraging time ends), giving up after {@code giveUpTicks}. Every
     * {@code driftTicks} without a find its search centre moves {@code driftBlocks} farther out, never more than
     * {@code stretch} blocks past the colony's foraging range (the range stretches from the ants' own failures).
     */
    public record SearchNumbers(boolean alone, int wander, int spotTicks, int emptyToDrop, int giveUpTicks, int driftTicks, int driftBlocks,
            int stretch, Optional<RecruitNumbers> recruit) {
        public SearchNumbers(boolean alone, int wander, int spotTicks, int emptyToDrop, int giveUpTicks, int driftTicks, int driftBlocks,
                int stretch) {
            this(alone, wander, spotTicks, emptyToDrop, giveUpTicks, driftTicks, driftBlocks, stretch, Optional.empty());
        }

        public static final Codec<SearchNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("alone", true).forGetter(SearchNumbers::alone),
                Codec.INT.optionalFieldOf("wander", 4).forGetter(SearchNumbers::wander),
                Codec.INT.optionalFieldOf("spot_ticks", 1200).forGetter(SearchNumbers::spotTicks),
                Codec.INT.optionalFieldOf("empty_to_drop", 2).forGetter(SearchNumbers::emptyToDrop),
                Codec.INT.optionalFieldOf("give_up_ticks", 6000).forGetter(SearchNumbers::giveUpTicks),
                Codec.INT.optionalFieldOf("drift_ticks", 1200).forGetter(SearchNumbers::driftTicks),
                Codec.INT.optionalFieldOf("drift_blocks", 3).forGetter(SearchNumbers::driftBlocks),
                Codec.INT.optionalFieldOf("stretch", 8).forGetter(SearchNumbers::stretch),
                RecruitNumbers.CODEC.optionalFieldOf("recruit").forGetter(SearchNumbers::recruit)
        ).apply(i, SearchNumbers::new));

        /**
         * How far out (blocks from the nest's middle) a search centre that started {@code from} blocks out has drifted
         * after {@code searched} ticks without a find: {@code driftBlocks} more every {@code driftTicks}, never past
         * {@code range} plus the stretch (and never in from where it started).
         */
        public double drifted(double from, long searched, int range) {
            double out = from + driftBlocks * Math.max(0, searched / Math.max(1, driftTicks));
            return Math.max(from, Math.min(out, range + stretch));
        }
    }

    /**
     * Recruiting to rich patches (long trails step 2; the user's decisions of 2026-10-17; real <i>Pogonomyrmex barbatus</i>
     * and <i>P. rugosus</i>: a forager that finds a dense patch of seed drags its sting tip on the way home, laying a
     * poison-gland scent line, and in the nest it runs about touching nestmates, which follow the line out). A find is a
     * rich patch when the food within {@code patchRadius} blocks of it (the pieces left on the {@code plants} named here,
     * all food plants if none are named, plus the seeds lying there) is at least {@link #needed} for its distance home:
     * {@code richPieces} at {@code atBlocks} blocks or nearer, growing with the distance to the power
     * {@code distancePower} (value against distance: a long trip only for a big prize). The finder calls out one
     * nestmate for every {@code piecesPerRecruit} pieces in the patch, at least 1 and at most {@code mostRecruits}
     * (a richer find, a stronger recruitment). The scent line halves in strength every {@code halfLifeTicks} unless a
     * returning recruit lays it again. Numbers are my picks, to confirm.
     */
    public record RecruitNumbers(int patchRadius, int richPieces, int atBlocks, double distancePower, int piecesPerRecruit, int mostRecruits,
            int halfLifeTicks, List<String> plants) {
        public static final Codec<RecruitNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("patch_radius", 4).forGetter(RecruitNumbers::patchRadius),
                Codec.INT.optionalFieldOf("rich_pieces", 24).forGetter(RecruitNumbers::richPieces),
                Codec.INT.optionalFieldOf("at_blocks", 20).forGetter(RecruitNumbers::atBlocks),
                Codec.DOUBLE.optionalFieldOf("distance_power", 0.62).forGetter(RecruitNumbers::distancePower),
                Codec.INT.optionalFieldOf("pieces_per_recruit", 16).forGetter(RecruitNumbers::piecesPerRecruit),
                Codec.INT.optionalFieldOf("most_recruits", 8).forGetter(RecruitNumbers::mostRecruits),
                Codec.INT.optionalFieldOf("half_life_ticks", 3600).forGetter(RecruitNumbers::halfLifeTicks),
                Codec.STRING.listOf().optionalFieldOf("plants", List.of()).forGetter(RecruitNumbers::plants)
        ).apply(i, RecruitNumbers::new));

        /** The food a patch this far (blocks) from the nest's middle needs to be worth recruiting to (24 at 20, about 48 at 60, 64 at 96). */
        public double needed(double distance) {
            return richPieces * Math.pow(Math.max(1.0, distance / Math.max(1, atBlocks)), distancePower);
        }

        /** How many nestmates a find with this much food in its patch calls out (1 per {@code piecesPerRecruit}, 1 to {@code mostRecruits}). */
        public int recruitsFor(int food) {
            return Math.max(1, Math.min(mostRecruits, food / Math.max(1, piecesPerRecruit)));
        }

        /** Whether this food plant (its {@code block} as the profile names it) counts toward a rich patch. */
        public boolean counts(PlantFood plant) {
            return plants.isEmpty() || plants.contains(plant.block());
        }
    }

    public record PlantFood(String block, int pieces, boolean onlyDecaying, int foragers, Map<String, String> state, boolean rich) {
        public static final Codec<PlantFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(PlantFood::block),
                Codec.INT.fieldOf("pieces").forGetter(PlantFood::pieces),
                Codec.BOOL.optionalFieldOf("only_decaying", false).forGetter(PlantFood::onlyDecaying),
                Codec.INT.optionalFieldOf("foragers", 1).forGetter(PlantFood::foragers),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("state", Map.of()).forGetter(PlantFood::state),
                Codec.BOOL.optionalFieldOf("rich", false).forGetter(PlantFood::rich)
        ).apply(i, PlantFood::new));

        public PlantFood(String block, int pieces, boolean onlyDecaying, int foragers) {
            this(block, pieces, onlyDecaying, foragers, Map.of(), false);
        }

        /** Whether this block is this plant (for dying leaves only, is dying; with {@code state}, has those values). */
        public boolean matches(BlockState state) {
            if (onlyDecaying && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.hasProperty(LeavesBlock.DISTANCE)
                    && !state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE)) {
                return false;
            }
            if (!hasValues(state)) {
                return false;
            }
            if (block.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(block.substring(1));
                return id != null && state.is(TagKey.create(Registries.BLOCK, id));
            }
            ResourceLocation id = ResourceLocation.tryParse(block);
            return id != null && BuiltInRegistries.BLOCK.getOptional(id).map(state::is).orElse(false);
        }

        /** Whether the block has every value named in {@code state} ("growth" = "dry"). */
        private boolean hasValues(BlockState blockState) {
            for (Map.Entry<String, String> wanted : state.entrySet()) {
                Property<?> property = blockState.getBlock().getStateDefinition().getProperty(wanted.getKey());
                if (property == null || !valueName(blockState, property).equals(wanted.getValue())) {
                    return false;
                }
            }
            return true;
        }

        private static <T extends Comparable<T>> String valueName(BlockState blockState, Property<T> property) {
            return property.getName(blockState.getValue(property));
        }
    }

    /**
     * Dead nestmates: a body nobody deals with crumbles after {@code corpseDecayTicks}. Ants carry their dead to the
     * refuse chamber. With {@code eatFreshTicks} (termites) there is no refuse chamber: a body younger than that is carried
     * into the fungus garden and eaten (it becomes a layer of comb, like a piece of forage), an older one is walled up with
     * soil where it lies, as real termites do.
     */
    public record RefuseNumbers(int corpseDecayTicks, Optional<Integer> eatFreshTicks) {
        public static final Codec<RefuseNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("corpse_decay_ticks").forGetter(RefuseNumbers::corpseDecayTicks),
                Codec.INT.optionalFieldOf("eat_fresh_ticks").forGetter(RefuseNumbers::eatFreshTicks)
        ).apply(i, RefuseNumbers::new));

        public RefuseNumbers(int corpseDecayTicks) {
            this(corpseDecayTicks, Optional.empty());
        }

        /** Whether this species eats its fresh dead and buries the old ones (instead of using a refuse chamber). */
        public boolean eatsOrBuries() {
            return eatFreshTicks.isPresent();
        }
    }

    /**
     * When a level of the nest (a room with its stair and tunnel, top to bottom) may be dug: once the colony has at least
     * {@code workers} workers and {@code food} pieces of stored food (nest stages: a young colony has a small nest that
     * grows deeper as the colony grows, like a real one).
     */
    public record LevelUnlock(int workers, int food) {
        public static final Codec<LevelUnlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("workers").forGetter(LevelUnlock::workers),
                Codec.INT.optionalFieldOf("food", 0).forGetter(LevelUnlock::food)
        ).apply(i, LevelUnlock::new));
    }

    /**
     * The nest. {@code maxLevels}: how many levels (rooms stacked below each other) the nest may have, 1 to 4.
     * {@code sealWhileFounding}: while the colony has no workers yet, the founders close the entrance from inside.
     * {@code foundingChamber}: the queen first digs a small founding chamber of her own just under the surface (see
     * {@code NestPlan.foundingChamber}), her home until the workers have dug her deep room (optional, default false).
     * {@code sideRooms}: more rooms on the top level, beside the shaft's foot, dug by the workers once the colony is
     * founded (see {@code NestPlan.sideRooms}; names of room kinds, such as "food" for a food store; optional, none).
     * {@code foragingHoles}: how many tunnels run from the shaft out under the ground to a foraging hole, the workers'
     * way out to forage (see {@code NestPlan.foragingTunnels}; 0 to 2, optional, none), and {@code foragingHoleDistance}
     * how far out (in blocks) the holes are (optional, 8). {@code plugBlock}: the block the workers pack into a hole to
     * close it (the entrance, a foraging hole, an opening in the nest walls; optional, Burrowed Dirt).
     * {@code crustWalls}: the walls of the nest's galleries are thin crusts of mud ({@code MudCrustBlock}) plastered over
     * every side that would open to the outside, before it is dug through ("wall first"; termites; optional, false). A
     * whole missing block is still packed full with the plug block. {@code sheeting}: covered runways of mud over the
     * ground from a hole to rich food near it (termites; optional, none; see {@link Sheeting}). {@code roomSizes}: how
     * big each kind of room is ({@code room_sizes}: {@code queen}, {@code nursery}, {@code food}, {@code refuse},
     * {@code founding}, each {@code {"length": n, "width": n}}, 1 to 7; optional, see {@link NestPlan.RoomSizes#DEFAULT});
     * only new nests use a change, a nest keeps the sizes it was planned with.
     */
    public record NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
            int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks, Optional<NewHoles> newHoles,
            boolean crustWalls, Optional<Sheeting> sheeting, NestPlan.RoomSizes roomSizes, boolean dug) {
        public static final Codec<NestNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_levels").forGetter(NestNumbers::maxLevels),
                Codec.BOOL.fieldOf("seal_while_founding").forGetter(NestNumbers::sealWhileFounding),
                Codec.BOOL.optionalFieldOf("founding_chamber", false).forGetter(NestNumbers::foundingChamber),
                Codec.STRING.listOf().optionalFieldOf("side_rooms", List.of()).forGetter(NestNumbers::sideRooms),
                Codec.INT.optionalFieldOf("foraging_holes", 0).forGetter(NestNumbers::foragingHoles),
                Codec.INT.optionalFieldOf("foraging_hole_distance", 8).forGetter(NestNumbers::foragingHoleDistance),
                Codec.STRING.optionalFieldOf("plug_block", "minecraft:dirt").forGetter(NestNumbers::plugBlock),
                LevelUnlock.CODEC.listOf().optionalFieldOf("level_unlocks", List.of()).forGetter(NestNumbers::levelUnlocks),
                NewHoles.CODEC.optionalFieldOf("new_holes").forGetter(NestNumbers::newHoles),
                Codec.BOOL.optionalFieldOf("crust_walls", false).forGetter(NestNumbers::crustWalls),
                Sheeting.CODEC.optionalFieldOf("sheeting").forGetter(NestNumbers::sheeting),
                NestPlan.RoomSizes.CODEC.optionalFieldOf("room_sizes", NestPlan.RoomSizes.DEFAULT).forGetter(NestNumbers::roomSizes),
                Codec.BOOL.optionalFieldOf("dug", true).forGetter(NestNumbers::dug)
        ).apply(i, NestNumbers::new));

        /**
         * {@code dug} false: the colony digs no nest plan at all (live or while away), for a species whose nest is made
         * another way (the Matabele ant's shallow nest, its own step).
         */
        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks, Optional<NewHoles> newHoles,
                boolean crustWalls, Optional<Sheeting> sheeting, NestPlan.RoomSizes roomSizes) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, levelUnlocks, newHoles,
                    crustWalls, sheeting, roomSizes, true);
        }

        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks, Optional<NewHoles> newHoles,
                boolean crustWalls, Optional<Sheeting> sheeting) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, levelUnlocks, newHoles,
                    crustWalls, sheeting, NestPlan.RoomSizes.DEFAULT);
        }

        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks, Optional<NewHoles> newHoles,
                boolean crustWalls) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, levelUnlocks, newHoles,
                    crustWalls, Optional.empty());
        }

        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks, Optional<NewHoles> newHoles) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, levelUnlocks, newHoles, false);
        }

        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, List.of(), Optional.empty());
        }

        public NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
                int foragingHoles, int foragingHoleDistance, String plugBlock, List<LevelUnlock> levelUnlocks) {
            this(maxLevels, sealWhileFounding, foundingChamber, sideRooms, foragingHoles, foragingHoleDistance, plugBlock, levelUnlocks, Optional.empty());
        }

        /**
         * How many levels of the nest the workers may dig with this many workers and this much stored food: the levels
         * whose {@code level_unlocks} entry (and every one before it) the colony has reached. Without the list, all of them.
         */
        public int levelsFor(int workers, int food) {
            if (levelUnlocks.isEmpty()) {
                return maxLevels;
            }
            int levels = 0;
            while (levels < levelUnlocks.size() && workers >= levelUnlocks.get(levels).workers() && food >= levelUnlocks.get(levels).food()) {
                levels++;
            }
            return levels;
        }

        /**
         * What a hole is packed with: the plug block (dirt if the name is unknown), in the state it is packed in
         * ({@link PlugBlock}: mound soil goes in finished and fresh, so it sets and hardens with age like the mound).
         */
        public BlockState plugState() {
            ResourceLocation id = ResourceLocation.tryParse(plugBlock);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block instanceof PlugBlock plug) {
                return plug.packedState();
            }
            return block == null || block.defaultBlockState().isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : block.defaultBlockState();
        }

        /**
         * Whether this block is a plug the workers packed: the plug block, or one of {@link com.antcolonies.registry.ModTags#OLD_PLUGS}
         * (Burrowed Dirt, the plug of older saves).
         */
        public boolean isPlug(BlockState state) {
            return state.is(plugState().getBlock()) || state.is(com.antcolonies.registry.ModTags.OLD_PLUGS);
        }
    }

    /**
     * Mud sheeting (termites; the user's plan of 2026-10-14, my picks for the numbers, to confirm): real Macrotermes
     * plaster a covered runway of soil over the ground from a hole to food near it and over the food, and forage under
     * it unseen, by day too. When the colony knows rich food (a fallen branch, a fallen tree, a hay bale) that is near a
     * hole (no new hole is dug for it), the workers build a runway of mud crust from the hole to it, at most
     * {@code maxLength} blocks long; at most {@code max} runways at a time. Far food gets a new hole instead.
     */

    public record Sheeting(int max, int maxLength) {
        public static final Codec<Sheeting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("max", 3).forGetter(Sheeting::max),
                Codec.INT.optionalFieldOf("max_length", 12).forGetter(Sheeting::maxLength)
        ).apply(i, Sheeting::new));
    }

    /**
     * New foraging holes near rich food (termites; the user's decision, 2026-10-14): when the colony knows a rich plant
     * (a plant whose entry says {@code rich}) more than {@code minDistance} blocks from every hole, the workers dig a
     * tunnel underground out to it and open a hole beside it. The tunnels reach out from the nest up to
     * {@link #reach} blocks, growing with the colony from {@code reachMin} (a young colony) to {@code reachMax} (at
     * {@code reachFullAtWorkers} workers); at most {@code max} new holes at a time (my picks).
     */
    public record NewHoles(int reachMin, int reachMax, int reachFullAtWorkers, int minDistance, int max) {
        public static final Codec<NewHoles> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("reach_min", 10).forGetter(NewHoles::reachMin),
                Codec.INT.optionalFieldOf("reach_max", 40).forGetter(NewHoles::reachMax),
                Codec.INT.optionalFieldOf("reach_full_at_workers", 500).forGetter(NewHoles::reachFullAtWorkers),
                Codec.INT.optionalFieldOf("min_distance", 8).forGetter(NewHoles::minDistance),
                Codec.INT.optionalFieldOf("max", 6).forGetter(NewHoles::max)
        ).apply(i, NewHoles::new));

        /** How far (blocks, sideways from the nest's middle) the tunnels reach with this many workers. */
        public int reach(int workers) {
            if (reachFullAtWorkers <= 0 || workers >= reachFullAtWorkers) {
                return reachMax;
            }
            return reachMin + (int) Math.round((reachMax - reachMin) * (double) Math.max(0, workers) / reachFullAtWorkers);
        }
    }

    /**
     * A founding batch: after the founders have sealed themselves in, the queen lays {@code eggs} eggs, one every
     * {@code eggIntervalTicks}, and the founders (the queen, and the king if the species has one) feed the young on
     * their own body fat (one piece every {@code feedIntervalTicks}), so the first workers need no food from outside.
     * {@code siteSearchRadius}: a founding queen landed on uneven ground first walks up to this many blocks to the most
     * level spot near her (real harvester ant queens look for flat open ground); 0, she founds where she lands (the
     * termites' royal pair, whose mound fills in round the slope). {@code maxUnevenness}: how many blocks higher or
     * lower than her spot the ground round it may be for it to count as level (both optional, default 0 and 1).
     */
    public record Founding(int eggs, int eggIntervalTicks, int feedIntervalTicks, int siteSearchRadius, int maxUnevenness, int secondBatchEggs) {
        public static final Codec<Founding> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("eggs").forGetter(Founding::eggs),
                Codec.INT.fieldOf("egg_interval_ticks").forGetter(Founding::eggIntervalTicks),
                Codec.INT.fieldOf("feed_interval_ticks").forGetter(Founding::feedIntervalTicks),
                Codec.INT.optionalFieldOf("site_search_radius", 0).forGetter(Founding::siteSearchRadius),
                Codec.INT.optionalFieldOf("max_unevenness", 1).forGetter(Founding::maxUnevenness),
                Codec.INT.optionalFieldOf("second_batch_eggs", 0).forGetter(Founding::secondBatchEggs)
        ).apply(i, Founding::new));

        public Founding(int eggs, int eggIntervalTicks, int feedIntervalTicks) {
            this(eggs, eggIntervalTicks, feedIntervalTicks, 0, 1, 0);
        }
    }

    /**
     * A queen who lays a steady stream of eggs (termites) instead of one egg per meal: as long as she has at least
     * {@code foodPerEgg} pieces of food in her (the queen's {@code meal} is how much she holds), she lays an egg every
     * {@code eggIntervalTicks} while she is small, faster as she swells, every {@code eggIntervalTicksFullSize} once she
     * is full grown; each egg uses up {@code foodPerEgg}. She stops while the nursery is full of young.
     */
    public record Laying(int eggIntervalTicks, int eggIntervalTicksFullSize, int foodPerEgg) {
        public static final Codec<Laying> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("egg_interval_ticks").forGetter(Laying::eggIntervalTicks),
                Codec.INT.fieldOf("egg_interval_ticks_full_size").forGetter(Laying::eggIntervalTicksFullSize),
                Codec.INT.fieldOf("food_per_egg").forGetter(Laying::foodPerEgg)
        ).apply(i, Laying::new));

        /** How long (in ticks) between two eggs for a queen of this size (0 = just founded, 1 = full grown). */
        public int intervalFor(float size) {
            float clamped = Math.max(0.0F, Math.min(1.0F, size));
            return Math.round(eggIntervalTicks + (eggIntervalTicksFullSize - eggIntervalTicks) * clamped);
        }
    }

    /**
     * How the insects' bodies behave (the animation engine, 2026-10-16; species-neutral, every number my pick, to
     * confirm). {@code naps} (optional, none for a species that does not nap): see {@link Naps}. {@code hot} (optional):
     * see {@link Heat}. {@code traits} (optional, everyone in the middle without): see {@link Traits}. {@code notice}
     * (optional, none for a species that never notices a player): see {@link Notice}. {@code groom} (optional, none for
     * a species whose model has no cleaning clips yet): see {@link Groom}.
     */
    public record BodyLanguage(Optional<Naps> naps, Optional<Heat> heat, Optional<Traits> traits, Optional<Notice> notice,
                               Optional<Groom> groom) {
        public static final Codec<BodyLanguage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Naps.CODEC.optionalFieldOf("naps").forGetter(BodyLanguage::naps),
                Heat.CODEC.optionalFieldOf("hot").forGetter(BodyLanguage::heat),
                Traits.CODEC.optionalFieldOf("traits").forGetter(BodyLanguage::traits),
                Notice.CODEC.optionalFieldOf("notice").forGetter(BodyLanguage::notice),
                Groom.CODEC.optionalFieldOf("groom").forGetter(BodyLanguage::groom)
        ).apply(i, BodyLanguage::new));
    }

    /**
     * Cleaning the antennae (real ants groom often, and the antennae most of all: each is drawn through the comb on the
     * front leg of its side, the strigil, then the comb is cleaned in the jaws; Farish 1972, Hackmann et al. 2015). An
     * insect standing about with nothing to do starts with {@code chancePerSecond} each second (times 0.5 to 1.5 by its
     * restlessness); {@code bothShare} of the time it cleans both, one after the other, otherwise one side.
     */
    public record Groom(float chancePerSecond, float bothShare) {
        public static final Codec<Groom> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("chance_per_second", 0.05F).forGetter(Groom::chancePerSecond),
                Codec.FLOAT.optionalFieldOf("both_share", 0.4F).forGetter(Groom::bothShare)
        ).apply(i, Groom::new));
    }

    /**
     * Personality (the animation engine, 2026-10-16). Workers of one colony really differ, each its whole life, in how
     * bold, how curious and how restless they are (animal personality studies in ants, such as Temnothorax and Myrmica),
     * and older ants (the foragers) are bolder than young ones (the nurses). Each real insect draws its three traits,
     * 0 to 1, from these ranges (from, to) when it first needs them; {@code olderBolder} is how much bolder a worker at
     * the end of its time as a nurse and after is than one just grown up (half of it either way from the drawn value).
     */
    public record Traits(List<Float> boldness, List<Float> curiosity, List<Float> restlessness, float olderBolder) {
        public static final Codec<Traits> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.listOf().optionalFieldOf("boldness", List.of(0.0F, 1.0F)).forGetter(Traits::boldness),
                Codec.FLOAT.listOf().optionalFieldOf("curiosity", List.of(0.0F, 1.0F)).forGetter(Traits::curiosity),
                Codec.FLOAT.listOf().optionalFieldOf("restlessness", List.of(0.0F, 1.0F)).forGetter(Traits::restlessness),
                Codec.FLOAT.optionalFieldOf("older_bolder", 0.3F).forGetter(Traits::olderBolder)
        ).apply(i, Traits::new));

        /** Everyone in the middle (a species without traits in its file). */
        public static final Traits EVEN = new Traits(List.of(0.5F), List.of(0.5F), List.of(0.5F), 0.0F);

        /** A value somewhere in this range ({@code share} 0 to 1). */
        public static float draw(List<Float> range, double share) {
            if (range.isEmpty()) {
                return 0.5F;
            }
            float from = range.get(0);
            float to = range.size() > 1 ? range.get(1) : from;
            return from + (to - from) * (float) Math.max(0.0, Math.min(1.0, share));
        }
    }

    /**
     * Noticing a player (the animation engine, 2026-10-16; the user's decisions: movement only, and what the insect does
     * depends on its personality). {@code sense}: {@code sight} (harvester ants have fairly good eyes and react to
     * movement: an ant within {@code range} blocks of a player who moves, in plain view, stops, lifts its forebody, points
     * its antennae and turns its head to follow; a still player is soon forgotten) or {@code vibration} (Macrotermes
     * workers are blind and feel the ground shake: a player walking within {@code range}, or sprinting, jumping or
     * landing within {@code shakeRange}, makes them freeze with antennae raised, never turning to look). How often an
     * insect notices goes with its curiosity ({@code chance} a second for the most curious, a fifth of it for the least),
     * and so does how long it looks ({@code lookTicks}, from, to). A bold one (boldness from {@code boldFrom}) then walks
     * to about {@code comeWithin} blocks of the player; a shy one (below {@code shyBelow}) backs off {@code backOff}
     * blocks, jaws open. Interest fades once the player has stood still for {@code forgetTicks}; then it does not notice
     * again for {@code restTicks} (it gets used to a harmless thing). Only an insect with nothing to do: never one at
     * work, carrying, asleep or in an alarm.
     */
    public record Notice(String sense, float range, float shakeRange, float chance, List<Integer> lookTicks, float boldFrom,
            float shyBelow, float comeWithin, int backOff, int forgetTicks, int restTicks) {
        public static final Codec<Notice> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("sense", "sight").forGetter(Notice::sense),
                Codec.FLOAT.optionalFieldOf("range", 6.0F).forGetter(Notice::range),
                Codec.FLOAT.optionalFieldOf("shake_range", 6.0F).forGetter(Notice::shakeRange),
                Codec.FLOAT.optionalFieldOf("chance", 0.8F).forGetter(Notice::chance),
                Codec.INT.listOf().optionalFieldOf("look_ticks", List.of(60, 200)).forGetter(Notice::lookTicks),
                Codec.FLOAT.optionalFieldOf("bold_from", 0.7F).forGetter(Notice::boldFrom),
                Codec.FLOAT.optionalFieldOf("shy_below", 0.3F).forGetter(Notice::shyBelow),
                Codec.FLOAT.optionalFieldOf("come_within", 1.0F).forGetter(Notice::comeWithin),
                Codec.INT.optionalFieldOf("back_off", 2).forGetter(Notice::backOff),
                Codec.INT.optionalFieldOf("forget_ticks", 60).forGetter(Notice::forgetTicks),
                Codec.INT.optionalFieldOf("rest_ticks", 600).forGetter(Notice::restTicks)
        ).apply(i, Notice::new));

        /** Whether this species feels the ground shake instead of seeing (it freezes, it never turns to look). */
        public boolean byVibration() {
            return "vibration".equals(sense);
        }

        /**
         * The chance that an insect this curious (0 to 1) notices a moving player when it can: by sight it goes with its
         * curiosity; a shake is felt by all alike (freezing is a reflex).
         */
        public float chanceFor(float curiosity) {
            return byVibration() ? chance : chance * (0.2F + 0.8F * Math.max(0.0F, Math.min(1.0F, curiosity)));
        }

        /** How long (ticks) an insect this curious looks before it goes on. */
        public int lookTicksFor(float curiosity) {
            if (lookTicks.isEmpty()) {
                return 0;
            }
            int from = lookTicks.get(0);
            int to = lookTicks.size() > 1 ? lookTicks.get(1) : from;
            return from + Math.round((to - from) * Math.max(0.0F, Math.min(1.0F, curiosity)));
        }
    }

    /**
     * Short naps (real ants sleep in many short naps: Cassill et al. 2009, fire ants, workers about 250 naps a day of
     * about a minute each, queens about 90 of about 6 minutes; with twitches of the antennae now and then, "rapid
     * antennal movement"). A worker with nothing to do inside the nest (or anywhere, while it has no colony) falls
     * asleep with {@code chancePerSecond} each second; a nap lasts {@code workerTicks} (from, to), a queen's
     * {@code queenTicks}. Any job, being hurt or the alarm wakes it.
     */
    public record Naps(float chancePerSecond, List<Integer> workerTicks, List<Integer> queenTicks) {
        public static final Codec<Naps> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("chance_per_second", 0.033F).forGetter(Naps::chancePerSecond),
                Codec.INT.listOf().optionalFieldOf("worker_ticks", List.of(800, 1600)).forGetter(Naps::workerTicks),
                Codec.INT.listOf().optionalFieldOf("queen_ticks", List.of(3600, 7200)).forGetter(Naps::queenTicks)
        ).apply(i, Naps::new));

        /** How long one nap lasts (ticks), somewhere in the caste's range; {@code share} is 0 to 1. */
        public int ticksFor(boolean queen, double share) {
            List<Integer> range = queen ? queenTicks : workerTicks;
            if (range.isEmpty()) {
                return 0;
            }
            int from = range.get(0);
            int to = range.size() > 1 ? range.get(1) : from;
            return from + (int) Math.round((to - from) * Math.max(0.0, Math.min(1.0, share)));
        }
    }

    /**
     * Hot ground (real desert and harvester ants: on soil too hot to touch they stand and walk tall on straight legs, the
     * gaster raised, the antennae held up, and they take quick short trips). Outside under the open sky in a biome at
     * least {@code fromHeat} hot (its base temperature: desert, badlands and savanna 2.0, plains 0.8), in the hottest
     * hours: from {@code aroundRestTicks} before the day clock's midday rest until as long after it (the rest is when the
     * ground is too hot even for them; a species without a day clock: the same hours as the harvester ant's).
     */
    public record Heat(float fromHeat, int aroundRestTicks) {
        public static final Codec<Heat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("from_heat", 1.5F).forGetter(Heat::fromHeat),
                Codec.INT.optionalFieldOf("around_rest_ticks", 2000).forGetter(Heat::aroundRestTicks)
        ).apply(i, Heat::new));

        /** Whether the ground is hot at this day time in a biome this hot, with this day clock (empty: the usual hours). */
        public boolean hotAt(long dayTime, float biomeHeat, Optional<DayClock> clock) {
            if (biomeHeat < fromHeat) {
                return false;
            }
            DayClock day = clock.orElse(USUAL_DAY);
            long time = Math.floorMod(dayTime, 24000L);
            int rest = Math.max(day.restTicks(biomeHeat), 1);
            int start = day.restStart(biomeHeat);
            return time >= start - aroundRestTicks && time < start + rest + aroundRestTicks;
        }

        /** The harvester ant's day clock as built into the code (for the hot hours of a species without one). */
        private static final DayClock USUAL_DAY = new DayClock(0, 11000, 7000, 4000, 0.5F, 2.0F, true);
    }

    public static final Codec<SpeciesProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ColonyNumbers.CODEC.fieldOf("colony").forGetter(SpeciesProfile::colony),
            WorkerNumbers.CODEC.fieldOf("worker").forGetter(SpeciesProfile::worker),
            QueenNumbers.CODEC.fieldOf("queen").forGetter(SpeciesProfile::queen),
            BroodNumbers.CODEC.fieldOf("brood").forGetter(SpeciesProfile::brood),
            FoodNumbers.CODEC.fieldOf("food").forGetter(SpeciesProfile::food),
            RefuseNumbers.CODEC.fieldOf("refuse").forGetter(SpeciesProfile::refuse),
            NestNumbers.CODEC.fieldOf("nest").forGetter(SpeciesProfile::nest),
            Founding.CODEC.optionalFieldOf("founding").forGetter(SpeciesProfile::founding),
            GardenRooms.CODEC.optionalFieldOf("garden").forGetter(SpeciesProfile::gardenRooms),
            Laying.CODEC.optionalFieldOf("laying").forGetter(SpeciesProfile::laying),
            BodyLanguage.CODEC.optionalFieldOf("body_language").forGetter(SpeciesProfile::bodyLanguage),
            Codec.STRING.listOf().optionalFieldOf("parts", List.of()).forGetter(SpeciesProfile::parts)
    ).apply(i, SpeciesProfile::new));
}
