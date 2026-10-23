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
    public static final SpeciesSection<GalleryNumbers> GALLERIES = SpeciesSection.signUp("nest.galleries", GalleryNumbers.CODEC);
    public static final SpeciesSection<Regrowth> REGROWTH = SpeciesSection.signUp("food.regrowth", Regrowth.CODEC);

    private SavannaNumbers() {
    }

    /** Signs the sections up (at start, before the species files load; reading a section signs them up too). */
    public static void signUp() {
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
     * {@code fillDays} game days is packed with soil. My picks for the numbers, to confirm.
     */
    public record GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore,
            int workersPerBlock, int maxLength, int reach, int pieceLength, int forkMin, int forkMax, int forkAngle, int wander, int hiddenTrip,
            int fillDays, int pullReach, WoodCall woodCall) {
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
                WoodCall.CODEC.optionalFieldOf("wood_call", WoodCall.DEFAULT).forGetter(GalleryNumbers::woodCall)
        ).apply(i, (numbers, fillDays, pullReach, woodCall) -> numbers.withStep3(fillDays, pullReach, woodCall)));

        /** Step 1's numbers only (growing with the step 2 and 3 defaults). */
        public GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore) {
            this(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, 4, 600, 50, 4, 8, 12, 30, 20, 12);
        }

        /** Steps 1 and 2's numbers (step 3's at their defaults). */
        public GalleryNumbers(boolean enabled, int armLength, int holeSpacing, int foragersPerHole, double rainMore, int foodRadius, int explore,
                int workersPerBlock, int maxLength, int reach, int pieceLength, int forkMin, int forkMax, int forkAngle, int wander, int hiddenTrip) {
            this(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength, reach, pieceLength,
                    forkMin, forkMax, forkAngle, wander, hiddenTrip, 10, 12, WoodCall.DEFAULT);
        }

        GalleryNumbers withStep3(int fillDays, int pullReach, WoodCall woodCall) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall);
        }

        /** The same numbers with another reach (tests keep a web inside their arena). */
        public GalleryNumbers withReach(int reach) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall);
        }

        /** The same numbers with another wander (a tip pulled toward food goes straight for it). */
        public GalleryNumbers withWander(int wander) {
            return new GalleryNumbers(enabled, armLength, holeSpacing, foragersPerHole, rainMore, foodRadius, explore, workersPerBlock, maxLength,
                    reach, pieceLength, forkMin, forkMax, forkAngle, wander, hiddenTrip, fillDays, pullReach, woodCall);
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
}
