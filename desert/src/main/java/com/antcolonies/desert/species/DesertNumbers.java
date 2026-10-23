package com.antcolonies.desert.species;



import com.antcolonies.species.SpeciesSection;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The desert's own sections of the species files (Project Apex part 3, T1, {@code docs/plans/apex-termites.md}): the
 * red harvester ant's door, patrol, trunk trails and night soil. Until T1 they were read by the framework's
 * {@code SpeciesProfile}; now the desert mod reads them itself ({@link SpeciesSection}), the species file unchanged. A
 * part asks {@code colony.profile().section(DesertNumbers.DOOR)}.
 */
public final class DesertNumbers {
    public static final SpeciesSection<DoorNumbers> DOOR = SpeciesSection.signUp("food.door", DoorNumbers.CODEC);
    public static final SpeciesSection<PatrolNumbers> PATROL = SpeciesSection.signUp("food.patrol", PatrolNumbers.CODEC);
    public static final SpeciesSection<TrunkNumbers> TRUNK = SpeciesSection.signUp("food.trails.trunk", TrunkNumbers.CODEC);
    public static final SpeciesSection<NightSoilNumbers> NIGHT_SOIL = SpeciesSection.signUp("nest.night_soil", NightSoilNumbers.CODEC);

    private DesertNumbers() {
    }

    /** Signs the sections up (at start, before the species files load; reading a section signs them up too). */
    public static void signUp() {
    }

    /**
     * The door (brain step 4b; Deborah Gordon's rule for red harvester ants): no ant is in charge of how many go out.
     * Foragers that are not out wait inside by the entrance; a forager (or a patroller) coming home touches the waiting
     * ants it passes within {@code touchReach} blocks, and one that has had {@code touchesNeeded} touches within
     * {@code touchMemoryTicks} goes out (Greene and Gordon: about one contact every 10 seconds). A forager home with
     * nothing does not count. While the colony has fewer than {@code smallColonyForagers} foragers one touch is enough,
     * a homecomer reaches every waiting ant by the entrance (a tiny nest), and a forager's own seed counts as its touch,
     * so a young colony is never slower than before. For the foragers that are numbers, each homecomer touches
     * {@code touchesPerReturn} of the waiting ones. My picks, to confirm.
     */
    public record DoorNumbers(int touchesNeeded, int touchMemoryTicks, double touchReach, int smallColonyForagers, int touchesPerReturn) {
        public static final Codec<DoorNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("touches_needed", 3).forGetter(DoorNumbers::touchesNeeded),
                Codec.INT.optionalFieldOf("touch_memory_ticks", 600).forGetter(DoorNumbers::touchMemoryTicks),
                Codec.DOUBLE.optionalFieldOf("touch_reach", 2.0).forGetter(DoorNumbers::touchReach),
                Codec.INT.optionalFieldOf("small_colony_foragers", 10).forGetter(DoorNumbers::smallColonyForagers),
                Codec.INT.optionalFieldOf("touches_per_return", 3).forGetter(DoorNumbers::touchesPerReturn)
        ).apply(i, DoorNumbers::new));

        /** How many touches a waiting forager needs to go out, in a colony with this many foragers. */
        public int touchesFor(int foragers) {
            return foragers < smallColonyForagers ? 1 : Math.max(1, touchesNeeded);
        }
    }

    /**
     * Patrollers (brain step 4b; real <i>Pogonomyrmex barbatus</i>, Gordon; Greene and Gordon 2007): each time the day
     * clock opens (at dawn, after the midday rest, after rain) a few workers taken from the builders go out first, over
     * and round the hill and then about {@code reach} blocks out along a trunk trail (or in a new direction) and back.
     * Foraging starts only once one is back, and only in the directions they came back from. A dawn patrol is
     * {@code share} of the workers (at least {@code least}); a later one {@code laterRounds} of that. A patroller hurt, or
     * with a hostile mob within {@code hostileWithin} blocks, turns back: its direction stays shut for the day,
     * {@code extraOnDanger} times as many again come out to patrol the hill, and the foragers allowed out are
     * {@code foragersAfterDanger} of the forager share for the rest of the day. A patroller meeting another colony's ants
     * turns back and shuts that direction for the day. The share and the later rounds are the user's; the rest my picks.
     */
    public record PatrolNumbers(float share, int least, int reach, float laterRounds, double hostileWithin, float extraOnDanger,
            float foragersAfterDanger) {
        public static final Codec<PatrolNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("share", 0.16F).forGetter(PatrolNumbers::share),
                Codec.INT.optionalFieldOf("least", 1).forGetter(PatrolNumbers::least),
                Codec.INT.optionalFieldOf("reach", 10).forGetter(PatrolNumbers::reach),
                Codec.FLOAT.optionalFieldOf("later_rounds", 0.5F).forGetter(PatrolNumbers::laterRounds),
                Codec.DOUBLE.optionalFieldOf("hostile_within", 4.0).forGetter(PatrolNumbers::hostileWithin),
                Codec.FLOAT.optionalFieldOf("extra_on_danger", 1.0F).forGetter(PatrolNumbers::extraOnDanger),
                Codec.FLOAT.optionalFieldOf("foragers_after_danger", 0.5F).forGetter(PatrolNumbers::foragersAfterDanger)
        ).apply(i, PatrolNumbers::new));

        /** How many patrol at dawn in a colony of this many workers (2 at 10, 4 at 25, 8 at 50, 16 at 100). */
        public int dawnPatrol(int workers) {
            return workers <= 0 ? 0 : Math.min(workers, Math.max(least, Math.round(workers * share)));
        }

        /** How many patrol after the midday rest or after rain: {@code laterRounds} of the dawn patrol. */
        public int laterPatrol(int workers) {
            int dawn = dawnPatrol(workers);
            return dawn <= 0 ? 0 : Math.min(dawn, Math.max(least, Math.round(dawn * laterRounds)));
        }
    }

    /**
     * Lasting trunk trails (real red harvester ants, <i>Pogonomyrmex barbatus</i>: cleared paths running straight out
     * from the nest disc, used for years). Each starts {@code start} blocks from the nest's middle (the hill's edge). The
     * colony keeps 1 trail while young, and more as it grows: up to {@code most} at {@code fullAtWorkers} workers. A
     * forager going for food within {@code spreadDegrees} of a trail's line walks out along it and leaves it at the
     * spot nearest its food; a direction with no trail gets a new one once {@code newTrailTrips} trips have gone that
     * way within a day (while the colony may have more). A block of the trail walked by {@code bareAfterTrips} trips
     * wears bare: the plants on it are cut and the ground turns to {@code bareGround}. A faded trail's bare ground
     * grows back to grass about {@code regrowTicks} later (a bit sooner or later for each block).
     * <p>Long trails (the user's decisions of 2026-10-17, {@code docs/plans/long-trails.md}): a trail reaches out as far as
     * its trips go (its tip), at most {@code mostLength} blocks from the nest's middle, and only as far as its traffic
     * holds: {@code blocksPerDailyTrip} blocks for each trip a day it gets (real trails live by their traffic; a long one
     * needs more trips, so a small colony keeps short trails).
     */
    public record TrunkNumbers(int start, int most, int fullAtWorkers, int spreadDegrees, int newTrailTrips, int bareAfterTrips,
            String bareGround, int regrowTicks, int mostLength, double blocksPerDailyTrip) {
        public static final Codec<TrunkNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("start", 4).forGetter(TrunkNumbers::start),
                Codec.INT.optionalFieldOf("most", 8).forGetter(TrunkNumbers::most),
                Codec.INT.optionalFieldOf("full_at_workers", 1500).forGetter(TrunkNumbers::fullAtWorkers),
                Codec.INT.optionalFieldOf("spread_degrees", 35).forGetter(TrunkNumbers::spreadDegrees),
                Codec.INT.optionalFieldOf("new_trail_trips", 8).forGetter(TrunkNumbers::newTrailTrips),
                Codec.INT.optionalFieldOf("bare_after_trips", 60).forGetter(TrunkNumbers::bareAfterTrips),
                Codec.STRING.optionalFieldOf("bare_ground", "minecraft:coarse_dirt").forGetter(TrunkNumbers::bareGround),
                Codec.INT.optionalFieldOf("regrow_ticks", 48000).forGetter(TrunkNumbers::regrowTicks),
                Codec.INT.optionalFieldOf("most_length", 96).forGetter(TrunkNumbers::mostLength),
                Codec.DOUBLE.optionalFieldOf("blocks_per_daily_trip", 6.0).forGetter(TrunkNumbers::blocksPerDailyTrip)
        ).apply(i, TrunkNumbers::new));

        /**
         * How far out (blocks from the nest's middle) a trail of this {@code strength} can reach: {@code blocksPerDailyTrip}
         * for each trip a day it gets past its start, at most {@code mostLength}. A trail's strength halves every
         * {@code halfLifeTicks} unused and each trip adds 1, so a trail walked {@code r} times a day sits near a strength
         * of {@code r * halfLife / (day * ln 2)}: its trips a day are its strength times {@code ln 2 * day / halfLife}.
         */
        public double holds(double strength, int halfLifeTicks) {
            double tripsADay = Math.max(0, strength) * Math.log(2) * 24000.0 / Math.max(1, halfLifeTicks);
            return Math.min(mostLength, start + blocksPerDailyTrip * tripsADay);
        }

        /** How many trunk trails a colony of this many workers keeps: 1 while young, up to {@code most}. */
        public int trailsFor(int workers) {
            double share = Math.min(1.0, Math.max(0, workers) / (double) Math.max(1, fullAtWorkers));
            return Math.max(1, Math.min(most, 1 + (int) Math.floor((most - 1) * share)));
        }

        /** The block bare ground turns into (coarse dirt if the name is unknown). */
        public BlockState bareGroundState() {
            ResourceLocation id = ResourceLocation.tryParse(bareGround);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            return (block == null ? net.minecraft.world.level.block.Blocks.COARSE_DIRT : block).defaultBlockState();
        }
    }

    /**
     * The night soil (a species with a hill): soil dug while the surface is shut waits in loose piles on the floors near
     * the top of the nest, {@code layersPerCell} layers a cell at most (2 pixels each), and is carried up first thing once
     * the surface opens. Diggers rest while the surface is shut once {@code perBuilder} pellets wait for each builder (more
     * than they could carry out in a morning). My picks, to confirm.
     */
    public record NightSoilNumbers(int layersPerCell, int perBuilder) {
        public static final NightSoilNumbers DEFAULT = new NightSoilNumbers(4, 12);
        public static final Codec<NightSoilNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("layers_per_cell", DEFAULT.layersPerCell()).forGetter(NightSoilNumbers::layersPerCell),
                Codec.INT.optionalFieldOf("per_builder", DEFAULT.perBuilder()).forGetter(NightSoilNumbers::perBuilder)
        ).apply(i, NightSoilNumbers::new));
    }
}
