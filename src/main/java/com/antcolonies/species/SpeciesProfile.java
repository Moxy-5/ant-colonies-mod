package com.antcolonies.species;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.registry.ModBlocks;
import com.mojang.serialization.Codec;
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

/**
 * A species profile: the numbers one species' colony runs on, read from {@code data/<namespace>/species/<name>.json}
 * (see {@link SpeciesProfiles}). The shared colony code asks the colony's profile instead of using fixed numbers, so a
 * new species (termites) only needs its own file. Times are in ticks (20 ticks = 1 second, a day = 24000 ticks).
 *
 * <p>Not in the profile yet (still in code): the room sizes and the spiral ({@code NestPlan}), the hill
 * ({@code Hill}), the castes' size, health and speed (fixed when the game starts), and the diggable blocks tag (the food
 * the workers gather is in {@link FoodNumbers}).
 */
public record SpeciesProfile(ColonyNumbers colony, WorkerNumbers worker, QueenNumbers queen, BroodNumbers brood,
        FoodNumbers food, RefuseNumbers refuse, NestNumbers nest, Optional<QueenBody> queenBody, Optional<Founding> founding,
        Optional<MoundNumbers> mound, Optional<GardenNumbers> garden, Optional<Laying> laying) {

    /** The numbers without a fungus garden and without a steady stream of eggs (the queen lays one egg per meal). */
    public SpeciesProfile(ColonyNumbers colony, WorkerNumbers worker, QueenNumbers queen, BroodNumbers brood, FoodNumbers food,
            RefuseNumbers refuse, NestNumbers nest, Optional<QueenBody> queenBody, Optional<Founding> founding,
            Optional<MoundNumbers> mound) {
        this(colony, worker, queen, brood, food, refuse, nest, queenBody, founding, mound, Optional.empty(), Optional.empty());
    }

    /**
     * The whole colony. {@code maxWorkers}: young grow into workers only while there are fewer. {@code nursesFrom}: from
     * how many workers on there is one nurse, two nurses, and so on (one number per nurse, smallest first).
     */
    public record ColonyNumbers(int maxWorkers, List<Integer> nursesFrom) {
        public static final Codec<ColonyNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_workers").forGetter(ColonyNumbers::maxWorkers),
                Codec.INT.listOf().fieldOf("nurses_from_workers").forGetter(ColonyNumbers::nursesFrom)
        ).apply(i, ColonyNumbers::new));

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

    /** A worker eats once every {@code mealIntervalTicks}; without food it slows down, then starves. */
    public record WorkerNumbers(int mealIntervalTicks, int slowAfterTicks, int starveAfterTicks) {
        public static final Codec<WorkerNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("meal_interval_ticks").forGetter(WorkerNumbers::mealIntervalTicks),
                Codec.INT.fieldOf("slow_after_ticks").forGetter(WorkerNumbers::slowAfterTicks),
                Codec.INT.fieldOf("starve_after_ticks").forGetter(WorkerNumbers::starveAfterTicks)
        ).apply(i, WorkerNumbers::new));
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
     * always); outside those times the workers stay inside (see {@code Colony.isForagingTime}).
     */
    public record FoodNumbers(int radius, String items, List<PlantFood> plants, String piece, String pile, int millTicks, int breadStock,
            List<String> forageAt) {
        /** Foraging at any time: day, night and in the rain. */
        public static final List<String> ALWAYS = List.of("day", "night", "rain");

        public static final Codec<FoodNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("radius").forGetter(FoodNumbers::radius),
                Codec.STRING.fieldOf("items").forGetter(FoodNumbers::items),
                PlantFood.CODEC.listOf().fieldOf("plants").forGetter(FoodNumbers::plants),
                Codec.STRING.fieldOf("piece").forGetter(FoodNumbers::piece),
                Codec.STRING.fieldOf("pile").forGetter(FoodNumbers::pile),
                Codec.INT.optionalFieldOf("mill_ticks", 0).forGetter(FoodNumbers::millTicks),
                Codec.INT.optionalFieldOf("bread_stock", 0).forGetter(FoodNumbers::breadStock),
                Codec.STRING.listOf().optionalFieldOf("forage_at", ALWAYS).forGetter(FoodNumbers::forageAt)
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
                    : ResourceLocation.fromNamespaceAndPath(com.antcolonies.AntColoniesMod.MODID, "seeds"));
        }

        /** What a piece cut off a plant is (a wheat seed if the name is unknown). */
        public Item pieceItem() {
            ResourceLocation id = ResourceLocation.tryParse(piece);
            return id == null ? Items.WHEAT_SEEDS : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.WHEAT_SEEDS);
        }

        /** The heap block of the food store (the seed pile if the name is unknown or not a heap block). */
        public SeedPileBlock pileBlock() {
            ResourceLocation id = ResourceLocation.tryParse(pile);
            Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            return block instanceof SeedPileBlock heap ? heap : ModBlocks.SEED_PILE.get();
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
     */
    public record PlantFood(String block, int pieces, boolean onlyDecaying, int foragers) {
        public static final Codec<PlantFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(PlantFood::block),
                Codec.INT.fieldOf("pieces").forGetter(PlantFood::pieces),
                Codec.BOOL.optionalFieldOf("only_decaying", false).forGetter(PlantFood::onlyDecaying),
                Codec.INT.optionalFieldOf("foragers", 1).forGetter(PlantFood::foragers)
        ).apply(i, PlantFood::new));

        /** Whether this block is this plant (and, for dying leaves only, is dying). */
        public boolean matches(BlockState state) {
            if (onlyDecaying && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.hasProperty(LeavesBlock.DISTANCE)
                    && !state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE)) {
                return false;
            }
            if (block.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(block.substring(1));
                return id != null && state.is(TagKey.create(Registries.BLOCK, id));
            }
            ResourceLocation id = ResourceLocation.tryParse(block);
            return id != null && BuiltInRegistries.BLOCK.getOptional(id).map(state::is).orElse(false);
        }
    }

    /** Dead nestmates: a body nobody carries to the refuse chamber crumbles after {@code corpseDecayTicks}. */
    public record RefuseNumbers(int corpseDecayTicks) {
        public static final Codec<RefuseNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("corpse_decay_ticks").forGetter(RefuseNumbers::corpseDecayTicks)
        ).apply(i, RefuseNumbers::new));
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
     * how far out (in blocks) the holes are (optional, 8).
     */
    public record NestNumbers(int maxLevels, boolean sealWhileFounding, boolean foundingChamber, List<String> sideRooms,
            int foragingHoles, int foragingHoleDistance) {
        public static final Codec<NestNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_levels").forGetter(NestNumbers::maxLevels),
                Codec.BOOL.fieldOf("seal_while_founding").forGetter(NestNumbers::sealWhileFounding),
                Codec.BOOL.optionalFieldOf("founding_chamber", false).forGetter(NestNumbers::foundingChamber),
                Codec.STRING.listOf().optionalFieldOf("side_rooms", List.of()).forGetter(NestNumbers::sideRooms),
                Codec.INT.optionalFieldOf("foraging_holes", 0).forGetter(NestNumbers::foragingHoles),
                Codec.INT.optionalFieldOf("foraging_hole_distance", 8).forGetter(NestNumbers::foragingHoleDistance)
        ).apply(i, NestNumbers::new));
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
     * A founding batch: after the founders have sealed themselves in, the queen lays {@code eggs} eggs, one every
     * {@code eggIntervalTicks}, and the founders (the queen, and the king if the species has one) feed the young on
     * their own body fat (one piece every {@code feedIntervalTicks}), so the first workers need no food from outside.
     */
    public record Founding(int eggs, int eggIntervalTicks, int feedIntervalTicks) {
        public static final Codec<Founding> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("eggs").forGetter(Founding::eggs),
                Codec.INT.fieldOf("egg_interval_ticks").forGetter(Founding::eggIntervalTicks),
                Codec.INT.fieldOf("feed_interval_ticks").forGetter(Founding::feedIntervalTicks)
        ).apply(i, Founding::new));
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
     * {@code pile}), and on every comb block a white nodule grows about every {@code noduleTicks} (while it has a layer
     * to spare and room for one); the nurses feed the nodules to the queen and the young instead of bread.
     */
    public record GardenNumbers(int rooms, int workersPerRoom, int noduleTicks) {
        public static final Codec<GardenNumbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("rooms").forGetter(GardenNumbers::rooms),
                Codec.INT.fieldOf("workers_per_room").forGetter(GardenNumbers::workersPerRoom),
                Codec.INT.fieldOf("nodule_ticks").forGetter(GardenNumbers::noduleTicks)
        ).apply(i, GardenNumbers::new));

        /** How many garden rooms a colony with this many workers wants (none while it has no workers). */
        public int roomsFor(int workers) {
            if (workers <= 0) {
                return 0;
            }
            return Math.min(rooms, 1 + workers / Math.max(1, workersPerRoom));
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

    public static final Codec<SpeciesProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ColonyNumbers.CODEC.fieldOf("colony").forGetter(SpeciesProfile::colony),
            WorkerNumbers.CODEC.fieldOf("worker").forGetter(SpeciesProfile::worker),
            QueenNumbers.CODEC.fieldOf("queen").forGetter(SpeciesProfile::queen),
            BroodNumbers.CODEC.fieldOf("brood").forGetter(SpeciesProfile::brood),
            FoodNumbers.CODEC.fieldOf("food").forGetter(SpeciesProfile::food),
            RefuseNumbers.CODEC.fieldOf("refuse").forGetter(SpeciesProfile::refuse),
            NestNumbers.CODEC.fieldOf("nest").forGetter(SpeciesProfile::nest),
            QueenBody.CODEC.optionalFieldOf("queen_body").forGetter(SpeciesProfile::queenBody),
            Founding.CODEC.optionalFieldOf("founding").forGetter(SpeciesProfile::founding),
            MoundNumbers.CODEC.optionalFieldOf("mound").forGetter(SpeciesProfile::mound),
            GardenNumbers.CODEC.optionalFieldOf("garden").forGetter(SpeciesProfile::garden),
            Laying.CODEC.optionalFieldOf("laying").forGetter(SpeciesProfile::laying)
    ).apply(i, SpeciesProfile::new));

    /**
     * The red harvester ant's numbers as built into the code: used only when its file is missing or broken (an error is
     * logged then). The real numbers are in {@code data/antcolonies/species/red_harvester_ant.json}.
     */
    public static final SpeciesProfile RED_HARVESTER_ANT = new SpeciesProfile(
            new ColonyNumbers(20, List.of(3, 8, 12, 20)),
            new WorkerNumbers(24000, 36000, 72000),
            new QueenNumbers(20, 5, 5, 1200, 3600, 120000L),
            new BroodNumbers(10, 1200, 5, 48000L),
            new FoodNumbers(24, "antcolonies:seeds", List.of(new PlantFood("minecraft:short_grass", 10, false, 1)),
                    "minecraft:wheat_seeds", "antcolonies:seed_pile", 60, 20, FoodNumbers.ALWAYS),
            new RefuseNumbers(6000),
            new NestNumbers(4, true, true, List.of(), 0, 8),
            Optional.empty(),
            Optional.of(new Founding(3, 400, 200)),
            Optional.empty());
}
