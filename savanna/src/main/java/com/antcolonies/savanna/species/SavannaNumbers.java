package com.antcolonies.savanna.species;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import com.antcolonies.species.SpeciesProfile.PlantFood;
import com.antcolonies.species.SpeciesSection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The savanna's own sections of the species files (Project Apex part 3, T1, {@code docs/plans/apex-termites.md}): the
 * termites' mound, fungus garden, soldiers, alarm, swelling queen, galleries and grass growing back. Until T1 they were
 * read by the framework's {@code SpeciesProfile}; now the savanna mod reads them itself ({@link SpeciesSection}), so
 * the species files look the same and a new savanna species (the Matabele ant) needs no edit in the framework. A part
 * asks {@code colony.profile().section(SavannaNumbers.MOUND)}.
 */
public final class SavannaNumbers {
    public static final SpeciesSection<QueenBody> QUEEN_BODY = SpeciesSection.signUp("queen_body", QueenBody.CODEC);
    public static final SpeciesSection<MoundNumbers> MOUND = SpeciesSection.signUp("mound", MoundNumbers.CODEC);
    public static final SpeciesSection<GardenNumbers> GARDEN = SpeciesSection.signUp("garden", GardenNumbers.CODEC);
    public static final SpeciesSection<SoldierNumbers> SOLDIERS = SpeciesSection.signUp("soldiers", SoldierNumbers.CODEC);
    public static final SpeciesSection<AlarmNumbers> ALARM = SpeciesSection.signUp("alarm", AlarmNumbers.CODEC);
    public static final SpeciesSection<RaidNumbers> RAID = SpeciesSection.signUp("alarm.raid", RaidNumbers.CODEC);
    public static final SpeciesSection<GalleryNumbers> GALLERIES = SpeciesSection.signUp("nest.galleries", GalleryNumbers.CODEC);
    public static final SpeciesSection<Regrowth> REGROWTH = SpeciesSection.signUp("food.regrowth", Regrowth.CODEC);
    public static final SpeciesSection<WorkerSizes> WORKER_SIZES = SpeciesSection.signUp("worker_sizes", WorkerSizes.CODEC);
    public static final SpeciesSection<ScoutNumbers> SCOUTING = SpeciesSection.signUp("raids.scouting", ScoutNumbers.CODEC);
    public static final SpeciesSection<ColumnNumbers> COLUMN = SpeciesSection.signUp("raids.column", ColumnNumbers.CODEC);
    public static final SpeciesSection<CarryNumbers> CARRY = SpeciesSection.signUp("raids.carry", CarryNumbers.CODEC);
    public static final SpeciesSection<TrailNumbers> TRAILS = SpeciesSection.signUp("raids.trails", TrailNumbers.CODEC);
    public static final SpeciesSection<ChooseNumbers> CHOOSING = SpeciesSection.signUp("raids.choosing", ChooseNumbers.CODEC);
    public static final SpeciesSection<MoundClueNumbers> MOUND_CLUE = SpeciesSection.signUp("raids.mound_clue", MoundClueNumbers.CODEC);
    public static final SpeciesSection<SearchNumbers> SEARCH = SpeciesSection.signUp("raids.search", SearchNumbers.CODEC);
    public static final SpeciesSection<HoursSoundNumbers> HOURS_SOUND = SpeciesSection.signUp("raids.hours_and_sound", HoursSoundNumbers.CODEC);
    public static final SpeciesSection<AwayRaidNumbers> AWAY_RAIDS = SpeciesSection.signUp("raids.away", AwayRaidNumbers.CODEC);
    public static final SpeciesSection<CatchNumbers> CATCH = SpeciesSection.signUp("raids.catch", CatchNumbers.CODEC);

    private SavannaNumbers() {
    }

    /** Signs the sections up (at start, before the species files load; reading a section signs them up too). */
    public static void signUp() {
    }

    /**
     * M8 step 1c: workers of one caste on a continuous size spread (the Matabele ant: majors, minors and every size
     * between). Each new worker rolls its length in mm from {@code groups} (each a share of the workers and a triangle
     * from its shortest through its commonest to its longest length) and is drawn with the major's model (made for
     * {@code majorModelMm}) or the minor's ({@code minorModelMm}), whichever is nearer by ratio, scaled to its length.
     */
    public record WorkerSizes(float majorModelMm, float minorModelMm, List<SizeGroup> groups) {
        public static final Codec<WorkerSizes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("major_model_mm").forGetter(WorkerSizes::majorModelMm),
                Codec.FLOAT.fieldOf("minor_model_mm").forGetter(WorkerSizes::minorModelMm),
                SizeGroup.CODEC.listOf().fieldOf("groups").forGetter(WorkerSizes::groups)
        ).apply(i, WorkerSizes::new));

        /** A new worker's length in mm, by the dice. */
        public float roll(net.minecraft.util.RandomSource random) {
            double total = 0.0;
            for (SizeGroup group : groups) {
                total += group.share();
            }
            double pick = random.nextDouble() * total;
            for (SizeGroup group : groups) {
                pick -= group.share();
                if (pick < 0.0 || group == groups.get(groups.size() - 1)) {
                    return group.roll(random);
                }
            }
            return majorModelMm;
        }

        /** Whether a worker this long is drawn with the major's model (else the minor's): the nearer by ratio. */
        public boolean majorModelFor(float lengthMm) {
            return lengthMm * lengthMm >= majorModelMm * minorModelMm;
        }

        /** A new worker's length for the given model (rolled until it is one that model draws; the model's own if none in 64 rolls). */
        public float rollFor(boolean majorModel, net.minecraft.util.RandomSource random) {
            for (int i = 0; i < 64; i++) {
                float length = roll(random);
                if (majorModelFor(length) == majorModel) {
                    return length;
                }
            }
            return majorModel ? majorModelMm : minorModelMm;
        }

        /** How much bigger than its model a worker this long is drawn (and its box). */
        public float scaleFor(float lengthMm) {
            return lengthMm / (majorModelFor(lengthMm) ? majorModelMm : minorModelMm);
        }

        /** The shortest and the longest any worker can be. */
        public float shortest() {
            return (float) groups.stream().mapToDouble(SizeGroup::shortestMm).min().orElse(minorModelMm);
        }

        public float longest() {
            return (float) groups.stream().mapToDouble(SizeGroup::longestMm).max().orElse(majorModelMm);
        }
    }

    /** One group of a size spread: {@code share} of the workers, lengths in a triangle from shortest through commonest to longest. */
    public record SizeGroup(float share, float shortestMm, float commonestMm, float longestMm) {
        public static final Codec<SizeGroup> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("share").forGetter(SizeGroup::share),
                Codec.FLOAT.fieldOf("shortest_mm").forGetter(SizeGroup::shortestMm),
                Codec.FLOAT.fieldOf("commonest_mm").forGetter(SizeGroup::commonestMm),
                Codec.FLOAT.fieldOf("longest_mm").forGetter(SizeGroup::longestMm)
        ).apply(i, SizeGroup::new));

        /** A length from the triangle. */
        public float roll(net.minecraft.util.RandomSource random) {
            double a = shortestMm, c = commonestMm, b = longestMm;
            if (b <= a) {
                return shortestMm;
            }
            double u = random.nextDouble();
            double split = (c - a) / (b - a);
            double length = u < split ? a + Math.sqrt(u * (b - a) * (c - a)) : b - Math.sqrt((1.0 - u) * (b - a) * (b - c));
            return (float) length;
        }
    }

    /**
     * A queen that swells as her colony grows (termites). Her size goes from 0 (just founded) to 1 (full grown, at
     * {@code fullSizeAtMembers} colony members besides the king); she grows by at most {@code growthPerDay} each day
     * and never shrinks. From {@code tooBigToWalkAt} on she can no longer walk.
     */
    public record QueenBody(int fullSizeAtMembers, float growthPerDay, float tooBigToWalkAt) {
        public static final Codec<QueenBody> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("full_size_at_members").forGetter(QueenBody::fullSizeAtMembers),
                Codec.FLOAT.fieldOf("growth_per_day").forGetter(QueenBody::growthPerDay),
                Codec.FLOAT.fieldOf("too_big_to_walk_at").forGetter(QueenBody::tooBigToWalkAt)
        ).apply(i, QueenBody::new));
    }

    /**
     * A cathedral mound built of termite mound soil (termites; species without it build the ant hill instead). It grows
     * in {@code maxHeight} stages ({@code MoundPlan}) and is about {@code maxHeight} blocks tall when full-grown. How big
     * the colony lets it get grows with time: every day it may have {@code blocksPerDay} more blocks, plus
     * {@code blocksPerDayPerWorker} for each worker (so an old small colony still adds to it slowly, and a big one builds
     * fast). The soil comes from galleries dug under it: {@code galleryLevels} levels, reaching up to
     * {@code galleryRadius} blocks out from the shaft. {@code chimneyTripChance}: the share of walks (0 to 1) on which a
     * termite may come out of or go in by a chimney mouth although there is another way (a foraging hole); on the
     * others it only uses a mouth when there is no other way (optional, 1: any walk).
     */
    public record MoundNumbers(int maxHeight, float blocksPerDay, float blocksPerDayPerWorker, int galleryLevels,
            int galleryRadius, float chimneyTripChance) {
        /** The numbers without a chimney trip chance (any walk may use a chimney mouth). */
        public MoundNumbers(int maxHeight, float blocksPerDay, float blocksPerDayPerWorker, int galleryLevels, int galleryRadius) {
            this(maxHeight, blocksPerDay, blocksPerDayPerWorker, galleryLevels, galleryRadius, 1.0F);
        }

        public static final Codec<MoundNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_height").forGetter(MoundNumbers::maxHeight),
                Codec.FLOAT.fieldOf("blocks_per_day").forGetter(MoundNumbers::blocksPerDay),
                Codec.FLOAT.fieldOf("blocks_per_day_per_worker").forGetter(MoundNumbers::blocksPerDayPerWorker),
                Codec.INT.fieldOf("gallery_levels").forGetter(MoundNumbers::galleryLevels),
                Codec.INT.fieldOf("gallery_radius").forGetter(MoundNumbers::galleryRadius),
                Codec.FLOAT.optionalFieldOf("chimney_trip_chance", 1.0F).forGetter(MoundNumbers::chimneyTripChance)
        ).apply(i, MoundNumbers::new));
    }

    /**
     * A fungus garden (termites): small garden rooms round the royal chamber ({@code NestPlan.gardens}), at most
     * {@code rooms} of them; the workers dig the first as soon as the colony has workers and one more for every
     * {@code workersPerRoom} workers. The food the foragers bring in is built into fungus comb there (the profile's food
     * {@code pile}), and on every comb block a white nodule grows about every {@code noduleTicks} (while it has a ripe
     * layer and room for one); the nurses feed the nodules to the queen and the young instead of bread.
     * <p>
     * The comb ages like the real conveyor: a layer goes on top fresh, ripens after about {@code ripenTicks}, turns old
     * when a nodule grows from it or after about {@code oldTicks} by itself, and an old layer nobody eats crumbles away
     * after about {@code crumbleTicks} (see {@code block.FungusCombBlock}).
     * <p>
     * Termitomyces mushrooms (step 5c): when a rain ends, a colony whose gardens hold at least {@code mushroomCombLayers}
     * layers of comb (a healthy garden) sends up one mushroom for every {@code combLayersPerMushroom} layers, at most
     * {@code maxMushrooms}, on the ground and the mound's lower slopes within {@code mushroomRadius} blocks of the
     * entrance; each one is a rare giant with the chance {@code giantMushroomChance} (see {@code colony.Mushrooms}).
     */
    public record GardenNumbers(int rooms, int workersPerRoom, int noduleTicks, int mushroomCombLayers, int combLayersPerMushroom,
            int maxMushrooms, int mushroomRadius, float giantMushroomChance, int ripenTicks, int oldTicks, int crumbleTicks) {
        /** The numbers with the default mushrooms and comb ages. */
        public GardenNumbers(int rooms, int workersPerRoom, int noduleTicks) {
            this(rooms, workersPerRoom, noduleTicks, 8, 6, 8, 12, 0.04F, 12000, 48000, 72000);
        }

        public static final Codec<GardenNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("rooms").forGetter(GardenNumbers::rooms),
                Codec.INT.fieldOf("workers_per_room").forGetter(GardenNumbers::workersPerRoom),
                Codec.INT.fieldOf("nodule_ticks").forGetter(GardenNumbers::noduleTicks),
                Codec.INT.optionalFieldOf("mushroom_comb_layers", 8).forGetter(GardenNumbers::mushroomCombLayers),
                Codec.INT.optionalFieldOf("comb_layers_per_mushroom", 6).forGetter(GardenNumbers::combLayersPerMushroom),
                Codec.INT.optionalFieldOf("max_mushrooms", 8).forGetter(GardenNumbers::maxMushrooms),
                Codec.INT.optionalFieldOf("mushroom_radius", 12).forGetter(GardenNumbers::mushroomRadius),
                Codec.FLOAT.optionalFieldOf("giant_mushroom_chance", 0.04F).forGetter(GardenNumbers::giantMushroomChance),
                Codec.INT.optionalFieldOf("ripen_ticks", 12000).forGetter(GardenNumbers::ripenTicks),
                Codec.INT.optionalFieldOf("old_ticks", 48000).forGetter(GardenNumbers::oldTicks),
                Codec.INT.optionalFieldOf("crumble_ticks", 72000).forGetter(GardenNumbers::crumbleTicks)
        ).apply(i, GardenNumbers::new));

        /** How many mushrooms come up after a rain from gardens holding this many layers of comb (none below a healthy garden). */
        public int mushroomsFor(int combLayers) {
            if (combLayers < mushroomCombLayers) {
                return 0;
            }
            return Math.max(1, Math.min(maxMushrooms, combLayers / Math.max(1, combLayersPerMushroom)));
        }

        /** How many garden rooms a colony with this many workers wants (none while it has no workers). */
        public int roomsFor(int workers) {
            if (workers <= 0) {
                return 0;
            }
            return Math.min(rooms, 1 + workers / Math.max(1, workersPerRoom));
        }
    }

    /**
     * Soldiers (termites: minor and major soldiers; a species without them has none). The colony wants about
     * {@code perWorker} soldiers for every worker (0.1: about 1 termite in 10 is a soldier), {@code minorsPerMajor}
     * minors to every major. When it has fewer than it wants, a new nymph grows into a soldier instead of a worker:
     * a minor after {@code minorMeal} pieces of food, a major after {@code majorMeal} (a bigger meal). Soldiers cannot
     * feed themselves (their jaws are too big): they want a piece from a nurse every {@code mealIntervalTicks}, and one
     * that has gone {@code slowAfterTicks} without moves at half speed. At foraging time the minors go out: one beside
     * each open way out, and a sentry line along the foragers' trail, one every {@code sentrySpacing} blocks on
     * alternate sides; when the foraging is over they wait at their posts until the workers are in, at most
     * {@code escortHomeTicks}, then go in last.
     */
    public record SoldierNumbers(float perWorker, int minorsPerMajor, int minorMeal, int majorMeal, int mealIntervalTicks, int slowAfterTicks,
            int sentrySpacing, int escortHomeTicks) {
        public static final Codec<SoldierNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("per_worker").forGetter(SoldierNumbers::perWorker),
                Codec.INT.fieldOf("minors_per_major").forGetter(SoldierNumbers::minorsPerMajor),
                Codec.INT.fieldOf("minor_meal").forGetter(SoldierNumbers::minorMeal),
                Codec.INT.fieldOf("major_meal").forGetter(SoldierNumbers::majorMeal),
                Codec.INT.optionalFieldOf("meal_interval_ticks", 24000).forGetter(SoldierNumbers::mealIntervalTicks),
                Codec.INT.optionalFieldOf("slow_after_ticks", 48000).forGetter(SoldierNumbers::slowAfterTicks),
                Codec.INT.optionalFieldOf("sentry_spacing", 3).forGetter(SoldierNumbers::sentrySpacing),
                Codec.INT.optionalFieldOf("escort_home_ticks", 300).forGetter(SoldierNumbers::escortHomeTicks)
        ).apply(i, SoldierNumbers::new));

        /** How many minor soldiers a colony with this many workers wants (rounded; 0.5 rounds up). */
        public int wantedMinors(int workers) {
            return roundHalfUp(workers * perWorker * minorsPerMajor / (minorsPerMajor + 1.0));
        }

        /** How many major soldiers a colony with this many workers wants (rounded; 0.5 rounds up). */
        public int wantedMajors(int workers) {
            return roundHalfUp(workers * perWorker / (minorsPerMajor + 1.0));
        }

        private static int roundHalfUp(double value) {
            return Math.max(0, (int) Math.floor(value + 0.5 + 1.0E-6));
        }
    }

    /**
     * The alarm (species that raise one; see {@code colony.Alarm}). {@code calmTicks}: how long each level (1 footsteps,
     * 2 an enemy at the foragers, 3 a break-in, 4 repeated break-ins) lasts after the last thing that raised it before it
     * drops a level. {@code breakInsForLevel4} break-ins within {@code breakInWindowTicks} make it level 4; after a
     * level 4 the colony wants {@code soldierBoost} times as many soldiers for {@code boostTicks}. A soldier feels the
     * drumming of another within {@code feelRadius} blocks (through the walls).
     */
    public record AlarmNumbers(List<Integer> calmTicks, int breakInsForLevel4, int breakInWindowTicks, float soldierBoost, int boostTicks,
            int feelRadius) {
        public static final Codec<AlarmNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().optionalFieldOf("calm_ticks", List.of(600, 2400, 3600, 24000)).forGetter(AlarmNumbers::calmTicks),
                Codec.INT.optionalFieldOf("break_ins_for_level_4", 3).forGetter(AlarmNumbers::breakInsForLevel4),
                Codec.INT.optionalFieldOf("break_in_window_ticks", 6000).forGetter(AlarmNumbers::breakInWindowTicks),
                Codec.FLOAT.optionalFieldOf("soldier_boost", 2.0F).forGetter(AlarmNumbers::soldierBoost),
                Codec.INT.optionalFieldOf("boost_ticks", 72000).forGetter(AlarmNumbers::boostTicks),
                Codec.INT.optionalFieldOf("feel_radius", 6).forGetter(AlarmNumbers::feelRadius)
        ).apply(i, AlarmNumbers::new));

        /** How long (ticks) this level (1 to 4) lasts after the last thing that raised it. */
        public int calmTicksFor(int level) {
            if (calmTicks.isEmpty()) {
                return 600;
            }
            return calmTicks.get(Math.max(0, Math.min(calmTicks.size() - 1, level - 1)));
        }
    }

    /**
     * M8 step 2f, a raid breaking into a gallery ({@code colony.RaidDefence}; real Macrotermes: the soldiers drum, the
     * workers further in go deeper and plug the gallery behind them, shutting the soldiers out): the drumming reaches
     * {@code drumReach} blocks from the break; the plug goes {@code plugBack} blocks back along the gallery toward the nest,
     * packed {@code plugAfterTicks} after the break (sooner never: the workers need the time to get behind it), or by
     * {@code plugGiveUpTicks} if the worker meant to pack it has not got there; the shut-out soldiers wait by the seal
     * once no foe is within {@code quietReach} of them; the seal is bitten open again in the foraging time (at night)
     * once no foe has been within {@code drumReach} of the break for {@code overAfterTicks}. My picks, to confirm.
     */
    public record RaidNumbers(int drumReach, int plugBack, int plugAfterTicks, int plugGiveUpTicks, int quietReach, int overAfterTicks) {
        public static final Codec<RaidNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("drum_reach", 16).forGetter(RaidNumbers::drumReach),
                Codec.INT.optionalFieldOf("plug_back", 4).forGetter(RaidNumbers::plugBack),
                Codec.INT.optionalFieldOf("plug_after_ticks", 40).forGetter(RaidNumbers::plugAfterTicks),
                Codec.INT.optionalFieldOf("plug_give_up_ticks", 200).forGetter(RaidNumbers::plugGiveUpTicks),
                Codec.INT.optionalFieldOf("quiet_reach", 8).forGetter(RaidNumbers::quietReach),
                Codec.INT.optionalFieldOf("over_after_ticks", 600).forGetter(RaidNumbers::overAfterTicks)
        ).apply(i, RaidNumbers::new));
    }

    /**
     * Termites underground (the user's plan of 2026-10-17, {@code docs/plans/termites-underground.md}), behind a switch:
     * {@code enabled} turns it on (off: the 2 foraging holes of before, both open every foraging time). Step 1, holes
     * take turns (real Macrotermes: many small holes along the galleries, only some opened on a night, near food found
     * before plus a few new ones to explore): the nest's tunnels reach out in 4 arms {@code armLength} blocks from the
     * middle (the 2 side tunnels go on out, 2 more branch off them past the mound round to the front and back); holes
     * lie about {@code holeSpacing} blocks apart along every tunnel; on each foraging time one hole is open for every
     * {@code foragersPerHole} foragers (at least 1), {@code rainMore} times as many again in the rain; the holes with
     * food the colony knows within {@code foodRadius} blocks (or a remembered trail to food still there) open first, and
     * {@code explore} of them (none if only 1 opens, 1 if 2 or 3) go to the holes rested longest. Step 3, growing toward
     * food: known grass up to {@code pullReach} blocks past a tip pulls it; a far branch that led to no food for
     * {@code fillDays} game days is packed with soil. M8 step 3F: pieces out to small food are cheaper ({@code smallFood}).
     * My picks for the numbers, to confirm.
     */
    public record GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore,
            int workersPerBlock, int maxLength, int reach, int pieceLength, int forkMin, int forkMax, int forkAngle, int wander, int hiddenTrip,
            int fillDays, int pullReach, WoodCall woodCall, SmallFood smallFood) {
        /** The first 16 numbers (a codec group holds at most 16), the step 3 ones at their defaults. */
        private static final com.mojang.serialization.MapCodec<GalleryNumbers> STEPS_1_TO_2 = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(GalleryNumbers::enabled),
                Codec.INT.optionalFieldOf("arm_length", 16).forGetter(GalleryNumbers::armLength),
                Codec.INT.optionalFieldOf("hole_spacing", 4).forGetter(GalleryNumbers::holeSpacing),
                Codec.INT.optionalFieldOf("foragers_per_hole", 6).forGetter(GalleryNumbers::foragersPerHole),
                Codec.DOUBLE.optionalFieldOf("rain_more", 0.5).forGetter(GalleryNumbers::rainMore),
                Codec.INT.optionalFieldOf("food_radius", 6).forGetter(GalleryNumbers::foodRadius),
                Codec.INT.optionalFieldOf("explore", 2).forGetter(GalleryNumbers::explore),
                Codec.INT.optionalFieldOf("workers_per_block", 4).forGetter(GalleryNumbers::workersPerBlock),
                Codec.INT.optionalFieldOf("max_length", 600).forGetter(GalleryNumbers::maxLength),
                Codec.INT.optionalFieldOf("reach", 50).forGetter(GalleryNumbers::reach),
                Codec.INT.optionalFieldOf("piece_length", 4).forGetter(GalleryNumbers::pieceLength),
                Codec.INT.optionalFieldOf("fork_min", 8).forGetter(GalleryNumbers::forkMin),
                Codec.INT.optionalFieldOf("fork_max", 12).forGetter(GalleryNumbers::forkMax),
                Codec.INT.optionalFieldOf("fork_angle", 30).forGetter(GalleryNumbers::forkAngle),
                Codec.INT.optionalFieldOf("wander", 20).forGetter(GalleryNumbers::wander),
                Codec.INT.optionalFieldOf("hidden_trip", 12).forGetter(GalleryNumbers::hiddenTrip)
        ).apply(i, GalleryNumbers::new));
        public static final Codec<GalleryNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                STEPS_1_TO_2.forGetter(numbers -> numbers),
                Codec.INT.optionalFieldOf("fill_days", 10).forGetter(GalleryNumbers::fillDays),
                Codec.INT.optionalFieldOf("pull_reach", 12).forGetter(GalleryNumbers::pullReach),
                WoodCall.CODEC.optionalFieldOf("wood_call", WoodCall.DEFAULT).forGetter(GalleryNumbers::woodCall),
                SmallFood.CODEC.optionalFieldOf("small_food", SmallFood.DEFAULT).forGetter(GalleryNumbers::smallFood)
        ).apply(i, (numbers, fillDays, pullReach, woodCall, smallFood) -> numbers.withStep3(fillDays, pullReach, woodCall, smallFood)));

        /** Step 1's numbers only (growing with the step 2 and 3 defaults). */
        public GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore) {
            this(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, 4, 600, 50, 4, 8, 12, 30, 20, 12);
        }

        /** Steps 1 and 2's numbers (step 3's at their defaults). */
        public GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore,
                int workersPerBlock, int maxLength, int reach, int pieceLength, int forkMin, int forkMax, int forkAngle, int wander, int hiddenTrip) {
            this(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength, reach, pieceLength,
                    forkMin, forkMax, forkAngle, wander, hiddenTrip, 10, 12, WoodCall.DEFAULT, SmallFood.DEFAULT);
        }

        GalleryNumbers withStep3(int fillDays, int pullReach, WoodCall woodCall, SmallFood smallFood) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall, smallFood);
        }

        /** The same numbers with another reach (tests keep a web inside their arena). */
        public GalleryNumbers withReach(int reach) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall, smallFood);
        }

        /** The same numbers with another wander (a tip pulled toward food goes straight for it). */
        public GalleryNumbers withWander(int wander) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall, smallFood);
        }

        /** How many blocks of gallery a colony with this many workers may have in all, at most max_length (0 when it does not grow). */
        public int lengthFor(int workers) {
            return workersPerBlock <= 0 ? 0 : Math.min(maxLength, workers / workersPerBlock);
        }

        /** How many holes open on a foraging time with this many foragers, in the rain or not. */
        public int holesFor(int foragers, boolean raining) {
            int holes = Math.max(1, (foragers + foragersPerHole - 1) / Math.max(1, foragersPerHole));
            return raining ? (int) Math.ceil(holes * (1 + rainMore)) : holes;
        }

        /** How many of {@code open} holes go to exploring (the rest to holes near known food): none of 1, 1 of 2 or 3. */
        public int exploreOf(int open) {
            return open <= 1 ? 0 : Math.min(open, open <= 3 ? 1 : explore);
        }
    }

    /**
     * M8 step 3F, F3 (my picks, to confirm): a gallery piece out to small food (a fallen branch, food of at most
     * {@code pieces} pieces) counts only {@code share} of its length against what the workers allow, as a feeding gallery
     * to a branch is short-lived and shallow (real Macrotermes feed at dozens of small places at once, mostly on sticks
     * and branches); the colony's spot count by its workers (the new holes' {@code per_workers}) limits them instead.
     */
    public record SmallFood(int pieces, double share) {
        public static final Codec<SmallFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("pieces", 8).forGetter(SmallFood::pieces),
                Codec.DOUBLE.optionalFieldOf("share", 0.0).forGetter(SmallFood::share)
        ).apply(i, SmallFood::new));

        public static final SmallFood DEFAULT = new SmallFood(8, 0.0);

        /** Whether food of this many pieces is small. */
        public boolean isSmall(int foodPieces) {
            return foodPieces > 0 && foodPieces <= pieces;
        }
    }

    /**
     * Big dead wood calls the galleries (the user's pick, 2026-10-19): real termites sense rotting wood through the soil
     * from afar (the smell of the wood and its fungi; they even judge a log's size from how it vibrates). Dead wood of at
     * least {@code minBlocks} blocks together (a standing dead giant, a fallen giant) is noticed from {@code reach} blocks
     * past any hole or tip, and from {@code bigReach} once it is {@code bigBlocks} blocks or more; the colony then knows
     * it, and a gallery piece or a runway goes to it. A big find gets the diggers (the user's pick, 2026-10-19): the way
     * to such wood may take the galleries up to {@code overLimit} blocks past the length the workers allow (the even
     * spreading waits until the workers catch up). {@code reach} 0: off. My numbers, to confirm.
     */
    public record WoodCall(int minBlocks, int reach, int bigBlocks, int bigReach, int overLimit) {
        public static final WoodCall DEFAULT = new WoodCall(20, 0, 100, 0, 0);
        public static final Codec<WoodCall> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("min_blocks", 20).forGetter(WoodCall::minBlocks),
                Codec.INT.optionalFieldOf("reach", 16).forGetter(WoodCall::reach),
                Codec.INT.optionalFieldOf("big_blocks", 100).forGetter(WoodCall::bigBlocks),
                Codec.INT.optionalFieldOf("big_reach", 20).forGetter(WoodCall::bigReach),
                Codec.INT.optionalFieldOf("over_limit", 32).forGetter(WoodCall::overLimit)
        ).apply(i, WoodCall::new));

        /** How far wood of this many blocks is noticed from (0: too small, or off). */
        public int reachFor(int blocks) {
            if (reach <= 0 || blocks < minBlocks) {
                return 0;
            }
            double big = bigBlocks <= minBlocks ? 1.0 : Math.min(1.0, (blocks - minBlocks) / (double) (bigBlocks - minBlocks));
            return (int) Math.round(reach + (bigReach - reach) * big);
        }

        /** The farthest any wood is noticed from. */
        public int farthest() {
            return reach <= 0 ? 0 : Math.max(reach, bigReach);
        }
    }

    /**
     * Grass that grows back after it is eaten (the user's plan of 2026-10-14, "bare patches"): a tuft of one of the
     * {@code plants} eaten away grows back as {@code growsBack} (with the values in {@code state}: green) about
     * {@code ticks} later, from the roots left in the ground. A tuft eaten within {@code bareRadius} blocks (sideways) of
     * a foraging hole leaves the grass block under it bare ({@code bareGround}, coarse dirt) until it grows back, so the
     * ground round a busy hole is a patch of bare soil, as round real Macrotermes foraging holes; the rest of the
     * savanna is only grazed.
     */
    public record Regrowth(List<String> plants, String growsBack, Map<String, String> state, int ticks, int bareRadius, String bareGround) {
        public static final Codec<Regrowth> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("plants").forGetter(Regrowth::plants),
                Codec.STRING.fieldOf("grows_back").forGetter(Regrowth::growsBack),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("state", Map.of()).forGetter(Regrowth::state),
                Codec.INT.fieldOf("ticks").forGetter(Regrowth::ticks),
                Codec.INT.optionalFieldOf("bare_radius", 0).forGetter(Regrowth::bareRadius),
                Codec.STRING.optionalFieldOf("bare_ground", "minecraft:coarse_dirt").forGetter(Regrowth::bareGround)
        ).apply(i, Regrowth::new));

        /** Whether this eaten plant grows back. */
        public boolean regrows(BlockState state) {
            for (String plant : plants) {
                if (new PlantFood(plant, 1, false, 1).matches(state)) {
                    return true;
                }
            }
            return false;
        }

        /** The young tuft that grows back, or null if the block is unknown. */
        @Nullable
        public BlockState growsBackState() {
            ResourceLocation id = ResourceLocation.tryParse(growsBack);
            BlockState young = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).orElse(null);
            if (young == null) {
                return null;
            }
            for (Map.Entry<String, String> value : state.entrySet()) {
                Property<?> property = young.getBlock().getStateDefinition().getProperty(value.getKey());
                if (property != null) {
                    young = withValue(young, property, value.getValue());
                }
            }
            return young;
        }

        /** The bare ground left round a hole, or null if the block is unknown. */
        @Nullable
        public BlockState bareGroundState() {
            ResourceLocation id = ResourceLocation.tryParse(bareGround);
            return id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).map(Block::defaultBlockState).orElse(null);
        }

        private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String name) {
            return property.getValue(name).map(value -> state.setValue(property, value)).orElse(state);
        }
    }

    /**
     * M8 step 3a, the raid scouts ({@code colony.Raids}; real Megaponera: lone scouts search for termites feeding under
     * cover, then run home laying a scent line). They search in the {@code scoutHours} (clock hours, each a from and a
     * to: all day, 6:00 to 19:30, the user's pick 2026-11-03, so a colony finds its termites sooner; night scouting in
     * the hot season waits for the shared season source), never in the rain, while the prey pile holds less than a day's
     * food; a raid only leaves in the raid {@code hours} (the mornings and evenings, Yusuf 2026), so a find made between
     * them is kept till the next raid hours:
     * one scout for every {@code workersPerScout} workers, at least 1, at most {@code mostScouts}, majors only. A scout
     * checks the colony's remembered good spots first, else searches the land in its own direction (scouts search like
     * real ants, {@link SearchNumbers}): each scout's reach starts at {@code firstRange}, grows {@code rangeStep} after
     * each trip it really searched and came home empty, up to {@code mostRange} (the real ant's foraging range of about
     * 95 m; a block a metre for distances), and moves to just past its find after a find. It smells termites (out of
     * their mound, or in their feeding galleries and runways) within {@code smell} blocks, looks the find over
     * {@code inspectTicks}, and goes home; it gives up after {@code tripTicks}. A good spot is remembered
     * {@code spotFadeTicks}, and forgotten after {@code spotMisses} empty checks. R8's balance pass: a settled colony's
     * (new land's) scouts start at {@code settledRange} (the whole range: they have searched this land for months), not
     * {@code firstRange} (a colony new to the place). Every number my pick, to confirm.
     */
    public record ScoutNumbers(List<List<Double>> hours, int workersPerScout, int mostScouts, int firstRange, int rangeStep,
                               int mostRange, int smell, int inspectTicks, int tripTicks, int spotFadeTicks, int spotMisses,
                               List<List<Double>> scoutHours, int settledRange) {
        public static final Codec<ScoutNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.listOf().listOf().optionalFieldOf("hours", List.of(List.of(6.0, 11.0), List.of(16.0, 19.0))).forGetter(ScoutNumbers::hours),
                Codec.INT.optionalFieldOf("workers_per_scout", 50).forGetter(ScoutNumbers::workersPerScout),
                Codec.INT.optionalFieldOf("most_scouts", 5).forGetter(ScoutNumbers::mostScouts),
                Codec.INT.optionalFieldOf("first_range", 32).forGetter(ScoutNumbers::firstRange),
                Codec.INT.optionalFieldOf("range_step", 8).forGetter(ScoutNumbers::rangeStep),
                Codec.INT.optionalFieldOf("most_range", 96).forGetter(ScoutNumbers::mostRange),
                Codec.INT.optionalFieldOf("smell", 5).forGetter(ScoutNumbers::smell),
                Codec.INT.optionalFieldOf("inspect_ticks", 40).forGetter(ScoutNumbers::inspectTicks),
                Codec.INT.optionalFieldOf("trip_ticks", 2400).forGetter(ScoutNumbers::tripTicks),
                Codec.INT.optionalFieldOf("spot_fade_ticks", 72000).forGetter(ScoutNumbers::spotFadeTicks),
                Codec.INT.optionalFieldOf("spot_misses", 2).forGetter(ScoutNumbers::spotMisses),
                Codec.DOUBLE.listOf().listOf().optionalFieldOf("scout_hours", List.of(List.of(6.0, 19.5))).forGetter(ScoutNumbers::scoutHours),
                Codec.INT.optionalFieldOf("settled_range", 96).forGetter(ScoutNumbers::settledRange)
        ).apply(i, ScoutNumbers::new));

        /** Whether this time of day ({@code Level#getDayTime}'s scale: 0 is 6:00) is in the raid hours. */
        public boolean isRaidHour(long dayTime) {
            return inHours(hours, dayTime);
        }

        /** Whether this time of day is in the scouting hours. */
        public boolean isScoutHour(long dayTime) {
            return inHours(scoutHours, dayTime);
        }

        /** The earliest hour a raid may leave (the morning's first raid hour; 6.0 if none). */
        public double firstRaidHour() {
            return hours.stream().filter(span -> span.size() == 2).mapToDouble(span -> span.get(0)).min().orElse(6.0);
        }

        /** The clock hour of this time of day ({@code Level#getDayTime}'s scale: 0 is 6:00). */
        public static double hourOf(long dayTime) {
            return (6.0 + Math.floorMod(dayTime, 24000L) / 1000.0) % 24.0;
        }

        private static boolean inHours(List<List<Double>> spans, long dayTime) {
            double hour = hourOf(dayTime);
            for (List<Double> span : spans) {
                if (span.size() == 2 && hour >= span.get(0) && hour < span.get(1)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Raid rework R7, hours and sound ({@code colony.Raids}, {@code colony.RaidColumn}). Raids end by dusk (the user's
     * answer, 2026-11-04): none leaves after the evening raid hours end (19:00 in {@code raids.scouting.hours}), and a raid
     * still out at {@code dusk} (19:30) stops where it is, picks up what it stung and walks home; until the morning's first
     * raid hour it stays dusk (a find made meanwhile waits for the morning). Rare night raids in the hot season wait for
     * the shared season source (Serene Seasons). The attack ends once no termite has been about the spot for
     * {@code quietTicks} (real raids: the attack at the feeding site lasts about 10 to 20 minutes, Yusuf 2026; the
     * termites stung, fled or sealed in). The column squeaks at a player within {@code squeakHear} blocks of a raider,
     * louder and more often the nearer (from {@code squeakQuiet} at that distance every {@code squeakEveryFar} ticks to
     * {@code squeakLoud} at {@code squeakLoudAt} blocks every {@code squeakEveryNear}; Hoelldobler et al. 1994: the
     * columns' stridulation is no message between the ants but a warning to big animals). A column marching into land
     * not ticking within {@code edgeLook} blocks ahead of its front turns home at that edge (until drawn-only ants, M8 step
     * 4). Every number my pick, to confirm.
     */
    public record HoursSoundNumbers(double dusk, int quietTicks, double squeakHear, double squeakLoudAt, double squeakQuiet,
                                    double squeakLoud, int squeakEveryFar, int squeakEveryNear, double edgeLook) {
        public static final HoursSoundNumbers DEFAULT = new HoursSoundNumbers(19.5, 600, 12.0, 2.0, 0.25, 1.0, 60, 8, 8.0);
        public static final Codec<HoursSoundNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("dusk", DEFAULT.dusk()).forGetter(HoursSoundNumbers::dusk),
                Codec.INT.optionalFieldOf("quiet_ticks", DEFAULT.quietTicks()).forGetter(HoursSoundNumbers::quietTicks),
                Codec.DOUBLE.optionalFieldOf("squeak_hear", DEFAULT.squeakHear()).forGetter(HoursSoundNumbers::squeakHear),
                Codec.DOUBLE.optionalFieldOf("squeak_loud_at", DEFAULT.squeakLoudAt()).forGetter(HoursSoundNumbers::squeakLoudAt),
                Codec.DOUBLE.optionalFieldOf("squeak_quiet", DEFAULT.squeakQuiet()).forGetter(HoursSoundNumbers::squeakQuiet),
                Codec.DOUBLE.optionalFieldOf("squeak_loud", DEFAULT.squeakLoud()).forGetter(HoursSoundNumbers::squeakLoud),
                Codec.INT.optionalFieldOf("squeak_every_far", DEFAULT.squeakEveryFar()).forGetter(HoursSoundNumbers::squeakEveryFar),
                Codec.INT.optionalFieldOf("squeak_every_near", DEFAULT.squeakEveryNear()).forGetter(HoursSoundNumbers::squeakEveryNear),
                Codec.DOUBLE.optionalFieldOf("edge_look", DEFAULT.edgeLook()).forGetter(HoursSoundNumbers::edgeLook)
        ).apply(i, HoursSoundNumbers::new));

        /** Whether this time of day is dusk for the raids: from {@code dusk} till the morning's first raid hour. */
        public boolean isDusk(long dayTime, ScoutNumbers scouting) {
            double hour = ScoutNumbers.hourOf(dayTime);
            return hour >= dusk || hour < scouting.firstRaidHour();
        }

        /** How near a player is, 0 at {@code squeakHear} to 1 at {@code squeakLoudAt} or nearer. */
        private double nearness(double distance) {
            return Math.max(0.0, Math.min(1.0, (squeakHear - distance) / Math.max(0.1, squeakHear - squeakLoudAt)));
        }

        /** How loud the column's squeak is for a player this far from the nearest raider (0 past {@code squeakHear}). */
        public float loudnessAt(double distance) {
            return distance > squeakHear ? 0.0F : (float) (squeakQuiet + (squeakLoud - squeakQuiet) * nearness(distance));
        }

        /** How many ticks between the column's squeaks at a player this far off. */
        public int everyAt(double distance) {
            return (int) Math.round(squeakEveryFar + (squeakEveryNear - squeakEveryFar) * nearness(distance));
        }
    }

    /**
     * M8 step 3f, raids out of sight ({@code colony.RaidsAway}): a colony away (in land not loaded, or loaded but far from
     * every player) raids as numbers by the same rules as the real column (the raid hours, hunger, the find's worth, the
     * raid's size), the time from the distance: the column walks out at {@code pace} blocks a second (the march in surges,
     * slower than one ant) after mustering at the entrance ({@code column.form_ticks}), attacks {@code fightTicks}, regroups
     * and walks home laden ({@code carry.laden_pace}). It brings home {@code raids.catch.per_raider} termites a raider
     * ({@link CatchNumbers}), never more than {@code mostTakeShare} of the real termites the termite colony's workers
     * inside stand for, taken from its numbers (the workers feeding under the mud at the spot; M8 step 3d). A share
     * {@code lostShare} of the raiders dies, {@code hurtShare} comes home hurt. At most {@code mostRaidsADay} raids a day
     * (1 to 4, Yusuf 2026). A raid leaves only if its attack starts before dusk. A spot is the termite colony's within
     * {@code termiteReach} blocks of its mound's middle. No raid out of sight to a spot a player is within
     * {@code playerNear} blocks of (that one needs real ants). Out of sight the scouts are numbers too: each scouting hour
     * each scout the colony would send finds, with {@code scoutFindChance}, one of the termites' feeding places it does
     * not know yet within its reach (the termite colony's ways out to food), scored {@code scoutWorthLeast} to
     * {@code scoutWorthMost}. Every number my pick, to confirm.
     */
    public record AwayRaidNumbers(double pace, int fightTicks, double mostTakeShare, double lostShare,
                                  double hurtShare, int mostRaidsADay, int termiteReach, int playerNear, double scoutFindChance,
                                  int scoutWorthLeast, int scoutWorthMost) {
        public static final AwayRaidNumbers DEFAULT = new AwayRaidNumbers(1.5, 1200, 0.1, 0.01, 0.03, 4, 80, 64, 0.25, 6, 18);
        public static final Codec<AwayRaidNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("pace", DEFAULT.pace()).forGetter(AwayRaidNumbers::pace),
                Codec.INT.optionalFieldOf("fight_ticks", DEFAULT.fightTicks()).forGetter(AwayRaidNumbers::fightTicks),
                Codec.DOUBLE.optionalFieldOf("most_take_share", DEFAULT.mostTakeShare()).forGetter(AwayRaidNumbers::mostTakeShare),
                Codec.DOUBLE.optionalFieldOf("lost_share", DEFAULT.lostShare()).forGetter(AwayRaidNumbers::lostShare),
                Codec.DOUBLE.optionalFieldOf("hurt_share", DEFAULT.hurtShare()).forGetter(AwayRaidNumbers::hurtShare),
                Codec.INT.optionalFieldOf("most_raids_a_day", DEFAULT.mostRaidsADay()).forGetter(AwayRaidNumbers::mostRaidsADay),
                Codec.INT.optionalFieldOf("termite_reach", DEFAULT.termiteReach()).forGetter(AwayRaidNumbers::termiteReach),
                Codec.INT.optionalFieldOf("player_near", DEFAULT.playerNear()).forGetter(AwayRaidNumbers::playerNear),
                Codec.DOUBLE.optionalFieldOf("scout_find_chance", DEFAULT.scoutFindChance()).forGetter(AwayRaidNumbers::scoutFindChance),
                Codec.INT.optionalFieldOf("scout_worth_least", DEFAULT.scoutWorthLeast()).forGetter(AwayRaidNumbers::scoutWorthLeast),
                Codec.INT.optionalFieldOf("scout_worth_most", DEFAULT.scoutWorthMost()).forGetter(AwayRaidNumbers::scoutWorthMost)
        ).apply(i, AwayRaidNumbers::new));
    }

    /**
     * M8 step 3d, the termites' losses ({@code colony.RaidCatch}; the user's "count stands for many", 2026-11-06): real
     * raids carry home about 200 to 1,000 termites with 150 to 550 ants, a few termites an ant (majors 1 to 12, minors 1 to
     * 3; Yusuf 2026, Table 1). The raiders that go as numbers (and every raider of a raid out of sight) each bring home
     * about {@code perRaider} termites (a quarter more or less, by chance) from the termites feeding under the mud there,
     * who are numbers in the termite colony's galleries; a raid brings home at most {@code mostCatch} so; no raid is more
     * than {@code mostRaid} ants (real raiding parties 20 to 500 or so). The termite colony's headcount loses one in
     * {@code colony.stands_for} of them ({@code Population.loseToHunters}). Every number my pick, to confirm.
     */
    public record CatchNumbers(double perRaider, int mostCatch, int mostRaid) {
        public static final CatchNumbers DEFAULT = new CatchNumbers(1.5, 1000, 600);
        public static final Codec<CatchNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("per_raider", DEFAULT.perRaider()).forGetter(CatchNumbers::perRaider),
                Codec.INT.optionalFieldOf("most_catch", DEFAULT.mostCatch()).forGetter(CatchNumbers::mostCatch),
                Codec.INT.optionalFieldOf("most_raid", DEFAULT.mostRaid()).forGetter(CatchNumbers::mostRaid)
        ).apply(i, CatchNumbers::new));
    }

    /**
     * M8 step 3b, the raid column ({@code colony.RaidColumn}; real Megaponera: the scout home from a find recruits a raid
     * in proportion to the termites it found, Frank et al. 2018, and leads it out along its scent line). The raid's size
     * (M8 step 3d): the share of the colony's workers it can spare, from {@code leastShare} for a fed colony to
     * {@code mostShare} for a starving one (30 to 75 percent, Yusuf 2026), all of it for a find worth {@code richWorth} or
     * more (a by-day feeding place, or two termites seen), a part for a poorer one, at least {@code leastRaid}, at most
     * {@code raids.catch.most_raid}; at most {@code mostReal} of them walk as real ants, the rest are numbers till step 4. A raider
     * still in the nest {@code formTicks} after the column left stays home. On the march (the ribbon, the user's pick of
     * 2026-11-07) the rows keep {@code spacing} blocks apart, {@code abreast} side by side and one fewer by turns. The column stops {@code stopShort} blocks short of the find and bunches up (at
     * most {@code gatherTicks}), then attacks all at once, fighting at most {@code fightTicks}; after the fight it
     * regroups there (at most {@code regroupTicks}) and walks home, everyone home or let go after {@code homeTicks}.
     * Every number my pick, to confirm.
     */
    public record ColumnNumbers(double richWorth, int leastRaid, double leastShare, double mostShare, int mostReal, int formTicks,
                                double spacing, int abreast, int stopShort,
                                int gatherTicks, int fightTicks, int regroupTicks, int homeTicks) {
        public static final Codec<ColumnNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("rich_worth", 6.0).forGetter(ColumnNumbers::richWorth),
                Codec.INT.optionalFieldOf("least_raid", 10).forGetter(ColumnNumbers::leastRaid),
                Codec.DOUBLE.optionalFieldOf("least_share", 0.3).forGetter(ColumnNumbers::leastShare),
                Codec.DOUBLE.optionalFieldOf("most_share", 0.75).forGetter(ColumnNumbers::mostShare),
                Codec.INT.optionalFieldOf("most_real", 50).forGetter(ColumnNumbers::mostReal),
                Codec.INT.optionalFieldOf("form_ticks", 600).forGetter(ColumnNumbers::formTicks),
                Codec.DOUBLE.optionalFieldOf("spacing", 0.6).forGetter(ColumnNumbers::spacing),
                Codec.INT.optionalFieldOf("abreast", 3).forGetter(ColumnNumbers::abreast),
                Codec.INT.optionalFieldOf("stop_short", 4).forGetter(ColumnNumbers::stopShort),
                Codec.INT.optionalFieldOf("gather_ticks", 300).forGetter(ColumnNumbers::gatherTicks),
                Codec.INT.optionalFieldOf("fight_ticks", 2400).forGetter(ColumnNumbers::fightTicks),
                Codec.INT.optionalFieldOf("regroup_ticks", 400).forGetter(ColumnNumbers::regroupTicks),
                Codec.INT.optionalFieldOf("home_ticks", 3600).forGetter(ColumnNumbers::homeTicks)
        ).apply(i, ColumnNumbers::new));
    }
    /**
     * M8 step 3c, home with the prey ({@code colony.RaidColumn}, {@code entity.goal.BringPreyGoal}; real Megaponera: after
     * the fight the raiders gather the stung termites, a major carrying a bundle of up to about 12 in its jaws, a minor 1 to
     * 3, Yusuf 2026, and walk home as a column). How many a worker can hold grows with its length: a major from
     * {@code leastMajor} (the shortest drawn with the major's model) to {@code mostMajor} (the longest), a minor from
     * {@code leastMinor} to {@code mostMinor}. After the fight the raiders pick up the paralysed termites lying within
     * {@code collectReach} blocks of the find, workers first (at most {@code collectTicks}), each pick-up a pause of
     * {@code pickTicks}. A raid that found nothing spreads out round the spot up to {@code spreadReach} blocks for
     * {@code spreadTicks}, any raider seeing a termite within {@code spreadSee} blocks going for it, then collects what it
     * stung. Laden, the column walks home at {@code ladenPace} of its pace. At home the carriers lay their termites on the
     * prey pile, {@code termitesPerLayer} to a layer (a carrier that finds no way to the pile in {@code bringTicks} has them
     * laid there out of sight). A raider that leaves the column hurt (crippled, or a termite soldier clamped on a leg)
     * follows the raid's trail home by itself, for at most {@code limpTicks}. Every number my pick, to confirm, but the
     * loads (real).
     */
    public record CarryNumbers(int leastMajor, int mostMajor, int leastMinor, int mostMinor, double collectReach, int collectTicks,
                               int pickTicks, int spreadTicks, double spreadReach, double spreadSee, double ladenPace,
                               int termitesPerLayer, int bringTicks, int limpTicks) {
        public static final Codec<CarryNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("least_major", 4).forGetter(CarryNumbers::leastMajor),
                Codec.INT.optionalFieldOf("most_major", 10).forGetter(CarryNumbers::mostMajor),
                Codec.INT.optionalFieldOf("least_minor", 1).forGetter(CarryNumbers::leastMinor),
                Codec.INT.optionalFieldOf("most_minor", 3).forGetter(CarryNumbers::mostMinor),
                Codec.DOUBLE.optionalFieldOf("collect_reach", 10.0).forGetter(CarryNumbers::collectReach),
                Codec.INT.optionalFieldOf("collect_ticks", 1200).forGetter(CarryNumbers::collectTicks),
                Codec.INT.optionalFieldOf("pick_ticks", 10).forGetter(CarryNumbers::pickTicks),
                Codec.INT.optionalFieldOf("spread_ticks", 600).forGetter(CarryNumbers::spreadTicks),
                Codec.DOUBLE.optionalFieldOf("spread_reach", 8.0).forGetter(CarryNumbers::spreadReach),
                Codec.DOUBLE.optionalFieldOf("spread_see", 5.0).forGetter(CarryNumbers::spreadSee),
                Codec.DOUBLE.optionalFieldOf("laden_pace", 0.85).forGetter(CarryNumbers::ladenPace),
                Codec.INT.optionalFieldOf("termites_per_layer", 10).forGetter(CarryNumbers::termitesPerLayer),
                Codec.INT.optionalFieldOf("bring_ticks", 2400).forGetter(CarryNumbers::bringTicks),
                Codec.INT.optionalFieldOf("limp_ticks", 6000).forGetter(CarryNumbers::limpTicks)
        ).apply(i, CarryNumbers::new));

        /** How many termites a worker this long can hold: by its length along its model's part of the size spread. */
        public int loadFor(boolean majorModel, float lengthMm, WorkerSizes sizes) {
            float split = (float) Math.sqrt(sizes.majorModelMm() * sizes.minorModelMm());
            float from = majorModel ? split : sizes.shortest();
            float to = majorModel ? sizes.longest() : split;
            double t = to <= from ? 1.0 : Math.max(0.0, Math.min(1.0, (lengthMm - from) / (to - from)));
            int least = majorModel ? leastMajor : leastMinor;
            int most = majorModel ? mostMajor : mostMinor;
            return Math.max(1, (int) Math.round(least + (most - least) * t));
        }
    }

    /**
     * M8 raid rework R4, the scent trails ({@code colony.ScentTrails}; real Megaponera lay their trail from the poison
     * gland, every raider on the way out, and it lasts up to about 29 hours, Hoelldobler et al. 1994). A trail laid by a
     * raid column lasts {@code lastingTicks} after it was last walked; a lone scout's line, weaker, {@code scoutLastingTicks}.
     * A trail broken (a block dug, put or flooded on it) stops the column at the gap: the front ants cast about at least
     * {@code castTicks}, and {@code castPerBlock} more for each block of the gap; the far side within {@code castReach}
     * blocks is picked up and the gap mended; a wider gap, after {@code castGiveTicks}, is crossed only if a former scout
     * walks in the column (it leads on from memory, a hypothesis in AntWiki), else the raid turns home. Every number my
     * pick, to confirm, but the 29 hours (real; a game day counted as a real day).
     */
    public record TrailNumbers(int lastingTicks, int scoutLastingTicks, double castReach, int castTicks, int castPerBlock, int castGiveTicks) {
        public static final TrailNumbers DEFAULT = new TrailNumbers(29000, 12000, 5.0, 60, 20, 240);
        public static final Codec<TrailNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("lasting_ticks", DEFAULT.lastingTicks()).forGetter(TrailNumbers::lastingTicks),
                Codec.INT.optionalFieldOf("scout_lasting_ticks", DEFAULT.scoutLastingTicks()).forGetter(TrailNumbers::scoutLastingTicks),
                Codec.DOUBLE.optionalFieldOf("cast_reach", DEFAULT.castReach()).forGetter(TrailNumbers::castReach),
                Codec.INT.optionalFieldOf("cast_ticks", DEFAULT.castTicks()).forGetter(TrailNumbers::castTicks),
                Codec.INT.optionalFieldOf("cast_per_block", DEFAULT.castPerBlock()).forGetter(TrailNumbers::castPerBlock),
                Codec.INT.optionalFieldOf("cast_give_ticks", DEFAULT.castGiveTicks()).forGetter(TrailNumbers::castGiveTicks)
        ).apply(i, TrailNumbers::new));
    }

    /**
     * Raid rework R5, scouts that choose (Frank and Linsenmair 2017: Megaponera raid rich termite sites over the whole
     * range but poor ones only near the nest; the raid's size follows the termites the scout found). A find is taken only
     * if its worth (3 for each termite counted there) reaches {@code leastWorth} plus {@code worthPerBlock} for each block
     * it lies from the nest, times a hunger factor from {@code hungryEase} (starving: the colony takes poorer finds) to 1
     * (nearly fed). A feeding gallery or runway fed at by day, with its termites inside out of sight, counts as
     * {@code byDayTermites} termites. The scout looks a find over from {@code inspectFrom} blocks (just outside the
     * termites' feel), counting all the while. A nearly fed colony sends {@code fedScouts} of its scouts (rounded up),
     * a starving one all. Every number my pick, to confirm.
     */
    public record ChooseNumbers(double leastWorth, double worthPerBlock, double hungryEase, int byDayTermites, double inspectFrom,
                                double fedScouts) {
        public static final ChooseNumbers DEFAULT = new ChooseNumbers(3.0, 0.1, 0.5, 2, 3.5, 0.5);
        public static final Codec<ChooseNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("least_worth", DEFAULT.leastWorth()).forGetter(ChooseNumbers::leastWorth),
                Codec.DOUBLE.optionalFieldOf("worth_per_block", DEFAULT.worthPerBlock()).forGetter(ChooseNumbers::worthPerBlock),
                Codec.DOUBLE.optionalFieldOf("hungry_ease", DEFAULT.hungryEase()).forGetter(ChooseNumbers::hungryEase),
                Codec.INT.optionalFieldOf("by_day_termites", DEFAULT.byDayTermites()).forGetter(ChooseNumbers::byDayTermites),
                Codec.DOUBLE.optionalFieldOf("inspect_from", DEFAULT.inspectFrom()).forGetter(ChooseNumbers::inspectFrom),
                Codec.DOUBLE.optionalFieldOf("fed_scouts", DEFAULT.fedScouts()).forGetter(ChooseNumbers::fedScouts)
        ).apply(i, ChooseNumbers::new));

        /** The least worth a find this far from the nest (blocks, flat) must have, with the colony this well fed (0 to 1). */
        public double need(double distance, double fed) {
            double ease = hungryEase + (1.0 - hungryEase) * Math.max(0.0, Math.min(1.0, fed));
            return (leastWorth + distance * worthPerBlock) * ease;
        }
    }

    /**
     * Raid rework R6, the mound as a clue (the user's idea, 2026-11-03; {@code colony.Raids}, {@code MatabeleScoutGoal}): a
     * scout that comes within {@code notice} blocks of a living termite mound's foot searches round it in rings
     * {@code ring_step} apart, out to {@code past_foot} blocks past the foot (once a trip, its trip {@code extra_ticks}
     * longer); a mound ringed with nothing found is left {@code fade_ticks}. A gallery of termites going to food smelt
     * on the way is followed out to its end, a point every {@code follow_every} blocks along it.
     */
    public record MoundClueNumbers(int notice, int pastFoot, int ringStep, int extraTicks, int fadeTicks, int followEvery) {
        public static final MoundClueNumbers DEFAULT = new MoundClueNumbers(16, 30, 10, 2400, 24000, 4);
        public static final Codec<MoundClueNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("notice", DEFAULT.notice()).forGetter(MoundClueNumbers::notice),
                Codec.INT.optionalFieldOf("past_foot", DEFAULT.pastFoot()).forGetter(MoundClueNumbers::pastFoot),
                Codec.INT.optionalFieldOf("ring_step", DEFAULT.ringStep()).forGetter(MoundClueNumbers::ringStep),
                Codec.INT.optionalFieldOf("extra_ticks", DEFAULT.extraTicks()).forGetter(MoundClueNumbers::extraTicks),
                Codec.INT.optionalFieldOf("fade_ticks", DEFAULT.fadeTicks()).forGetter(MoundClueNumbers::fadeTicks),
                Codec.INT.optionalFieldOf("follow_every", DEFAULT.followEvery()).forGetter(MoundClueNumbers::followEvery)
        ).apply(i, MoundClueNumbers::new));
    }

    /**
     * Raid scouts search like real ants ({@code MatabeleScoutGoal}, {@code colony.Raids}; 2026-11-04, in place of R5's
     * slices and bands). Each scout keeps its own favourite direction (a new scout gets one at random, the best spread of
     * {@code spreadTries} tries from its colony's other scouts' directions) and goes back that way trip after trip (route
     * and site fidelity, well known in ponerine ants: Fresneau 1985, Pachycondyla apicalis, each forager keeping its own
     * sector). Out: it walks fairly straight along its direction, each hop turned a random {@code outWiggle} degrees or
     * so, to where its search starts: a share of its reach, {@code outNear + (outFar - outNear) * u^outBias} for an even
     * random u (usually near, now and then far). Search: a correlated random walk (Bell 1990, insect search paths), steps
     * of {@code step} blocks each turned by a random {@code turn} degrees or so, kept round where it started searching (a
     * soft pull back past {@code stayShare} of that distance, at least {@code stayLeast} blocks) and never past its reach
     * from the nest. Area-restricted search ({@link AreaSearch}) where it smells termites within {@code scent} blocks.
     * Home: the straight way (path integration, Wehner 2003; known in many ants, assumed for Megaponera). Memory: a trip
     * counts as searched empty only if it searched at least {@code searchedShare} of its trip time; then the scout's reach
     * grows ({@code raids.scouting.range_step}) and its direction drifts a random {@code drift} degrees or so, or, its
     * reach already the most, swings a random {@code swing} degrees or so; after a find its direction turns to the find
     * and its reach becomes the find's distance plus {@code findMargin}. Every number my pick, to confirm.
     */
    public record SearchNumbers(double outNear, double outFar, double outBias, double outWiggle, double step, double turn,
                                double stayShare, double stayLeast, double scent, AreaSearch ars, double searchedShare, double drift,
                                double swing, int findMargin, int spreadTries) {
        public static final SearchNumbers DEFAULT = new SearchNumbers(0.25, 0.85, 2.0, 10.0, 4.0, 35.0, 0.5, 8.0, 12.0,
                AreaSearch.DEFAULT, 0.5, 20.0, 60.0, 8, 8);
        public static final Codec<SearchNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("out_near", DEFAULT.outNear()).forGetter(SearchNumbers::outNear),
                Codec.DOUBLE.optionalFieldOf("out_far", DEFAULT.outFar()).forGetter(SearchNumbers::outFar),
                Codec.DOUBLE.optionalFieldOf("out_bias", DEFAULT.outBias()).forGetter(SearchNumbers::outBias),
                Codec.DOUBLE.optionalFieldOf("out_wiggle", DEFAULT.outWiggle()).forGetter(SearchNumbers::outWiggle),
                Codec.DOUBLE.optionalFieldOf("step", DEFAULT.step()).forGetter(SearchNumbers::step),
                Codec.DOUBLE.optionalFieldOf("turn", DEFAULT.turn()).forGetter(SearchNumbers::turn),
                Codec.DOUBLE.optionalFieldOf("stay_share", DEFAULT.stayShare()).forGetter(SearchNumbers::stayShare),
                Codec.DOUBLE.optionalFieldOf("stay_least", DEFAULT.stayLeast()).forGetter(SearchNumbers::stayLeast),
                Codec.DOUBLE.optionalFieldOf("scent", DEFAULT.scent()).forGetter(SearchNumbers::scent),
                AreaSearch.CODEC.optionalFieldOf("area_search", AreaSearch.DEFAULT).forGetter(SearchNumbers::ars),
                Codec.DOUBLE.optionalFieldOf("searched_share", DEFAULT.searchedShare()).forGetter(SearchNumbers::searchedShare),
                Codec.DOUBLE.optionalFieldOf("drift", DEFAULT.drift()).forGetter(SearchNumbers::drift),
                Codec.DOUBLE.optionalFieldOf("swing", DEFAULT.swing()).forGetter(SearchNumbers::swing),
                Codec.INT.optionalFieldOf("find_margin", DEFAULT.findMargin()).forGetter(SearchNumbers::findMargin),
                Codec.INT.optionalFieldOf("spread_tries", DEFAULT.spreadTries()).forGetter(SearchNumbers::spreadTries)
        ).apply(i, SearchNumbers::new));

        /** Where a trip's search starts, as a share of the scout's reach, for an even random {@code u} (0 to 1). */
        public double outShare(double u) {
            return outNear + (outFar - outNear) * Math.pow(Math.max(0.0, Math.min(1.0, u)), outBias);
        }
    }

    /**
     * Area-restricted search (Bell 1990: a searching insect that meets a cue of food slows down and turns more, so it
     * stays in the good patch): within {@code raids.search.scent} blocks of termites feeding, a feeding gallery or
     * runway, or a tunnel of termites going to food, a scout walks at {@code pace} of its pace, takes steps of
     * {@code step} blocks each turned a random {@code turn} degrees or so, leaning {@code toward} of the way toward the
     * smell, for {@code ticks} after it last smelt it. Every number my pick, to confirm.
     */
    public record AreaSearch(double step, double turn, double pace, double toward, int ticks) {
        public static final AreaSearch DEFAULT = new AreaSearch(2.0, 75.0, 0.7, 0.3, 400);
        public static final Codec<AreaSearch> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("step", DEFAULT.step()).forGetter(AreaSearch::step),
                Codec.DOUBLE.optionalFieldOf("turn", DEFAULT.turn()).forGetter(AreaSearch::turn),
                Codec.DOUBLE.optionalFieldOf("pace", DEFAULT.pace()).forGetter(AreaSearch::pace),
                Codec.DOUBLE.optionalFieldOf("toward", DEFAULT.toward()).forGetter(AreaSearch::toward),
                Codec.INT.optionalFieldOf("ticks", DEFAULT.ticks()).forGetter(AreaSearch::ticks)
        ).apply(i, AreaSearch::new));
    }
}
