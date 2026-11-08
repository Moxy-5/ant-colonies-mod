package com.antcolonies.savanna.worldgen;

import java.util.List;
import java.util.Optional;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.MatabeleColonies;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A Macrotermes mound in new land (M8 step 1b, {@code docs/DESIGN.md}, "World generation, details"): living colonies
 * with dead mounds among them, 40 to 80 blocks apart at random, mostly round 60, bigger mounds farther apart, and not
 * every place holding one, by the ground (M8 step 3F, F1: {@link MoundPlaces}). The structure set
 * (data/antcolonies/worldgen/structure_set/termite_mounds.json) offers every chunk; {@link MoundPlaces} says which are
 * places and which places hold a mound.
 *
 * <p>World generation only picks what stands there ({@link Pick}) and where; the colony, its nest and its mound are put
 * in on the server once the land round it is loaded ({@code colony.NewLandMounds}), with the same code the termites
 * build with, so a mound of new land is a mound like any other. The numbers are in the structure's file
 * (data/antcolonies/worldgen/structure/termite_mound.json), all my picks, to confirm.
 */
public class TermiteMoundStructure extends Structure {
    /** The mounds' structure set: giant trees of new land keep clear of its places ({@link #crowds}). */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_mounds"));
    /** A tree of new land stands no nearer than this to a mound's middle (a full mound and the soil washed round it reach about 13). */
    public static final int CLEAR = 16;

    public static final MapCodec<TermiteMoundStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Numbers.CODEC.fieldOf("numbers").forGetter(TermiteMoundStructure::numbers)).apply(i, TermiteMoundStructure::new));

    /** One band of mound stages (1 to the full 32) and how often a mound of new land is in it. */
    public record Band(int from, int to, int weight) {
        public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("from").forGetter(Band::from),
                Codec.INT.fieldOf("to").forGetter(Band::to),
                Codec.INT.fieldOf("weight").forGetter(Band::weight)
        ).apply(i, Band::new));

        /** The living colonies' stages (the user, 2026-10-24): giant cathedrals rare, most mounds small. */
        static final List<Band> LIVING = List.of(new Band(4, 9, 40), new Band(10, 14, 28), new Band(15, 19, 17),
                new Band(20, 24, 10), new Band(25, 32, 5));
        /** The dead mounds' stages: tilted bigger, as big mounds stand for decades and small dead ones wash away fast. */
        static final List<Band> DEAD = List.of(new Band(4, 9, 20), new Band(10, 14, 25), new Band(15, 19, 25),
                new Band(20, 24, 18), new Band(25, 32, 12));
    }

    /** The mound's full height in stages (the Macrotermes species file's {@code mound.max_height}). */
    public static final int FULL_STAGE = 32;

    /**
     * The real spreads, from the structure's file. {@code deadShare}: the dice for a dead mound (one too near another dead
     * mound lives instead, so fewer end dead: {@link #pickAt}). {@code deadApart}: how near (blocks, middle to middle) two
     * dead mounds of new land may stand; nearer, only the higher-ranked stays dead (the user, 2026-11-07: dead mounds came
     * in clumps of 2 or 3). {@code livingStages}, {@code deadSizes}: bands of mound stage with their weights, for living colonies and for the
     * size dead mounds had stood to. {@code deadStages}: weights of freshly dead (days 0 to 3, the spires standing,
     * Xylaria on the comb), wearing (3 to 12), slumping (12 to 30) and old grassy humps (35 to 200). {@code humpWoods}:
     * of the old humps, how many have trees or a thicket on them. {@code burrows}: of the old humps, how many an aardvark
     * has dug into. {@code humpGrass}: whether old humps carry the greener grass of real mound islands.
     */
    public record Numbers(double deadShare, int deadApart, List<Band> livingStages, List<Band> deadSizes, List<Integer> deadStages,
            double humpWoods, double burrows, boolean humpGrass, MoundPlaces.Numbers places, Matabele matabele) {
        public static final Codec<Numbers> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("dead_share", 0.75).forGetter(Numbers::deadShare),
                Codec.intRange(0, 256).optionalFieldOf("dead_apart", 65).forGetter(Numbers::deadApart),
                Band.CODEC.listOf().optionalFieldOf("living_stages", Band.LIVING).forGetter(Numbers::livingStages),
                Band.CODEC.listOf().optionalFieldOf("dead_sizes", Band.DEAD).forGetter(Numbers::deadSizes),
                Codec.INT.listOf().optionalFieldOf("dead_stages", List.of(30, 60, 6, 4)).forGetter(Numbers::deadStages),
                Codec.DOUBLE.optionalFieldOf("hump_woods", 0.5).forGetter(Numbers::humpWoods),
                Codec.DOUBLE.optionalFieldOf("aardvark_burrows", 0.4).forGetter(Numbers::burrows),
                Codec.BOOL.optionalFieldOf("hump_grass", true).forGetter(Numbers::humpGrass),
                MoundPlaces.Numbers.CODEC.optionalFieldOf("places", MoundPlaces.Numbers.DEFAULT).forGetter(Numbers::places),
                Matabele.CODEC.optionalFieldOf("matabele", Matabele.DEFAULT).forGetter(Numbers::matabele)
        ).apply(i, Numbers::new));

        public static final Numbers DEFAULT = new Numbers(0.75, 65, Band.LIVING, Band.DEAD, List.of(30, 60, 6, 4), 0.5, 0.4, true,
                MoundPlaces.Numbers.DEFAULT, Matabele.DEFAULT);
    }

    /**
     * The Matabele colonies of new land's dead mounds, one place for both numbers (the commonness plan, 2026-11-06,
     * {@code docs/plans/matabele-commonness.md}; before, the reach lived twice in the code and could drift apart).
     * {@code share}: the dice for a dead mound with termites in reach holding a colony. {@code livingNear}: how far
     * (blocks) a living termite mound's foot may stand for a dead one to get Matabele; a dead mound with none that near
     * never holds them (the user, 2026-11-07: "dead mounds not by termite mounds never have Matabele"), checked when the
     * mound is put ({@code NewLandMounds.livingMoundNear}).
     */
    public record Matabele(double share, int livingNear) {
        public static final Codec<Matabele> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, 1).optionalFieldOf("share", 0.75).forGetter(Matabele::share),
                Codec.intRange(1, 256).optionalFieldOf("living_near", 100).forGetter(Matabele::livingNear)
        ).apply(i, Matabele::new));

        public static final Matabele DEFAULT = new Matabele(0.75, 100);
    }

    /** The mound numbers of this world's data (the defaults if the structure is missing). */
    public static Numbers numbersOf(net.minecraft.core.RegistryAccess registries) {
        return registries.registryOrThrow(Registries.STRUCTURE).get(MoundPlaces.STRUCTURE) instanceof TermiteMoundStructure mounds
                ? mounds.numbers() : Numbers.DEFAULT;
    }

    /** Sizes of mound (and colony). */
    public static final int FULL = 0, HALF = 1, YOUNG = 2;
    /** What grows on an old hump. */
    public static final int NO_WOODS = 0, THICKET = 1, GROWN_TREE = 2, YOUNG_BAOBAB = 3;

    /**
     * What stands at a mound's place: living or dead; its size ({@link #FULL}, {@link #HALF}, {@link #YOUNG}); its
     * share of the full mound's height (0 to 1); for a living colony its age in days, for a dead mound the days since it
     * died; what grows on it once it is an old hump; whether an aardvark has dug into it; green grass on the hump; and
     * its own seed (its galleries, trees and burrow).
     */
    public record Pick(boolean living, int size, double height, double days, int woods, boolean burrow, boolean grass, long seed) {
        /** The mound's stage, 1 to {@link #FULL_STAGE}. */
        public int stage() {
            return Math.max(1, Math.min(FULL_STAGE, (int) Math.round(height * FULL_STAGE)));
        }

        public boolean isOldHump() {
            return !living && days >= 35;
        }
    }

    private final Numbers numbers;

    public TermiteMoundStructure(StructureSettings settings, Numbers numbers) {
        super(settings);
        this.numbers = numbers;
    }

    public Numbers numbers() {
        return numbers;
    }

    @Override
    public StructureType<?> type() {
        return SavannaStructures.TERMITE_MOUND.get();
    }

    private static int weighted(List<Integer> weights, RandomSource random) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        int roll = random.nextInt(Math.max(1, total));
        for (int i = 0; i < weights.size(); i++) {
            roll -= Math.max(0, weights.get(i));
            if (roll < 0) {
                return i;
            }
        }
        return 0;
    }

    private static double between(RandomSource random, double from, double to) {
        return Math.round((from + random.nextDouble() * (to - from)) * 100) / 100.0;
    }

    private static int stageFrom(List<Band> bands, RandomSource random) {
        Band band = bands.get(weighted(bands.stream().map(Band::weight).toList(), random));
        int from = Math.max(1, Math.min(band.from(), band.to())), to = Math.min(FULL_STAGE, Math.max(band.from(), band.to()));
        return from + random.nextInt(to - from + 1);
    }

    /**
     * The stage at quantile {@code q} (0 to 1) of the weighted bands, each stage of a band equally likely: the same
     * spread as {@link #stageFrom}, drawn in order of size (F1: a place's size quantile, big territories big mounds).
     */
    static int stageAt(List<Band> bands, double q) {
        double total = 0;
        for (Band band : bands) {
            total += Math.max(0, band.weight());
        }
        double acc = 0;
        int last = 1;
        for (Band band : bands) {
            int from = Math.max(1, Math.min(band.from(), band.to())), to = Math.min(FULL_STAGE, Math.max(band.from(), band.to()));
            double each = Math.max(0, band.weight()) / total / (to - from + 1);
            for (int stage = from; stage <= to; stage++) {
                acc += each;
                last = stage;
                if (q < acc) {
                    return stage;
                }
            }
        }
        return last;
    }

    /** Full, half grown or young, by stage: the names the command and the log use (and young colonies keep their deep levels shut). */
    public static int sizeOf(int stage) {
        return stage >= 20 ? FULL : stage >= 10 ? HALF : YOUNG;
    }

    /**
     * A living colony's age by its mound's stage (my pick, to confirm): real Macrotermes mounds show after a year or two
     * and reach full size after decades, the growth slowing as it goes; here about 15 days at stage 4, 80 at 15, 260 at
     * the full 32, give or take 15 percent.
     */
    public static double ageFor(int stage, RandomSource random) {
        return between(random, 0.85, 1.15) * (5 + Math.pow(stage, 1.6));
    }

    /**
     * The dice for one mound, the real spreads. Living, by the stage bands (giant cathedrals rare, most small, the user's
     * table); the colony's age follows its stage. Dead: the size it had stood to by its own bands (tilted bigger), at
     * every stage of the wear. Stages 1 to 3 never: a first-year colony has no mound to see.
     */
    public static Pick pick(Numbers numbers, RandomSource random) {
        long seed = random.nextLong() & 0xFFFFFFFFL;
        boolean living = random.nextDouble() >= numbers.deadShare();
        int stage = stageFrom(living ? numbers.livingStages() : numbers.deadSizes(), random);
        return pick(numbers, random, seed, living, stage);
    }

    /**
     * The pick of a place of the pattern (F1), fixed by the world's seed and the place alone, so the server can work it
     * out again for a place not made yet (F4): living or dead by the place's dice, the stage at its size quantile
     * ({@code quantile}: its territory, leaning bigger next to empty places), the rest by its own dice as {@link #pick}.
     *
     * <p><b>Dead mounds spread out (the user, 2026-11-07).</b> A mound dead by its dice stays dead only if no other dead
     * mound stands within {@link Numbers#deadApart} of it ({@link MoundPlaces#staysDead}: of two too near, the
     * higher-ranked stays dead and the other lives). Each mound used to flip its own coin, and coins clump: with 1 mound in
     * 4 dead, about 3 dead mounds in 4 had a dead neighbour, so the user saw 2 or 3 dead together after 10 living. Real:
     * Macrotermes mound fields are spread evenly by neighbours fighting over the ground, and a colony that dies leaves
     * living neighbours round its mound. A dead mound left with no living one within {@link Matabele#livingNear} lives
     * instead (BUGS 104, 162: on Minecraft's rolling savanna nearly half the dead mounds would otherwise stand alone, empty),
     * so every dead mound of new land has termites in reach and its Matabele dice alone say whether it holds a colony (3 in
     * 4). Matabele never settle a dead mound with no living one near (the check when the mound is put,
     * {@code NewLandMounds.livingMoundNear}, for mounds of the commands and old worlds).
     */
    public static Pick pickAt(Numbers numbers, MoundPlaces.Land land, MoundPlaces.Place place, double quantile) {
        RandomSource random = RandomSource.create(place.seed(land.seed()));
        long seed = random.nextLong() & 0xFFFFFFFFL;
        boolean living = MoundPlaces.living(land.seed(), place, numbers.deadShare());
        if (!living) {
            BlockPos foot = MoundPlaces.holds(land, place, numbers.places());
            living = foot != null && !MoundPlaces.staysDead(land, place, foot, numbers.places(), numbers.deadShare(), numbers.deadApart(),
                    numbers.matabele().livingNear());
        }
        int stage = stageAt(living ? numbers.livingStages() : numbers.deadSizes(), quantile);
        return pick(numbers, random, seed, living, stage);
    }

    private static Pick pick(Numbers numbers, RandomSource random, long seed, boolean living, int stage) {
        double height = (double) stage / FULL_STAGE;
        int size = sizeOf(stage);
        if (living) {
            return new Pick(true, size, height, ageFor(stage, random), NO_WOODS, false, false, seed);
        }
        int wear = Math.min(3, weighted(numbers.deadStages(), random));
        double days = switch (wear) {
            case 0 -> between(random, 0.2, 3);
            case 1 -> between(random, 3, 12);
            case 2 -> between(random, 12, 30);
            default -> between(random, 35, 200);
        };
        int woods = NO_WOODS;
        if (days >= 35 && random.nextDouble() < numbers.humpWoods()) {
            double r = random.nextDouble();
            woods = r < 0.65 ? THICKET : r < 0.92 ? GROWN_TREE : YOUNG_BAOBAB;
        }
        boolean burrow = days >= 35 && random.nextDouble() < numbers.burrows();
        return new Pick(false, size, height, days, woods, burrow, days >= 35 && numbers.humpGrass(), seed);
    }

    /**
     * Where in its chunk a mound's middle lies: fixed by the world's seed and the chunk alone, so a giant tree of new land
     * can work out where the mounds go without making them ({@link #crowds}).
     */
    public static BlockPos middle(long worldSeed, ChunkPos chunk) {
        RandomSource random = RandomSource.create(worldSeed ^ (chunk.x * 341873128712L + chunk.z * 132897987541L) ^ 0x7E5A11L);
        return new BlockPos(chunk.getMinBlockX() + 3 + random.nextInt(10), 0, chunk.getMinBlockZ() + 3 + random.nextInt(10));
    }

    /**
     * Whether a tree of new land with this box (its crown and the wood lying round it) would stand on a mound's place:
     * then it is not grown there (the mound has the place; the trees on old humps are put there on purpose). Only a
     * place that really holds a mound counts ({@link MoundPlaces#holds}): a place the pattern offers but the ground leaves
     * empty is free for trees (the user's report, 2026-10-24: far fewer trees).
     */
    public static boolean crowds(GenerationContext context, BoundingBox box) {
        StructureSet set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(SET);
        if (set == null || set.structures().isEmpty() || !(set.structures().get(0).structure().value() instanceof TermiteMoundStructure mounds)) {
            return false;
        }
        MoundPlaces.Numbers places = mounds.numbers().places();
        MoundPlaces.Land land = land(context, mounds);
        for (int cx = (box.minX() - CLEAR) >> 4; cx <= (box.maxX() + CLEAR) >> 4; cx++) {
            for (int cz = (box.minZ() - CLEAR) >> 4; cz <= (box.maxZ() + CLEAR) >> 4; cz++) {
                MoundPlaces.Place place = MoundPlaces.candidate(context.seed(), cx, cz, places);
                BlockPos middle = place.middle();
                if (middle.getX() >= box.minX() - CLEAR && middle.getX() <= box.maxX() + CLEAR
                        && middle.getZ() >= box.minZ() - CLEAR && middle.getZ() <= box.maxZ() + CLEAR
                        && MoundPlaces.isPlace(context.seed(), cx, cz, places) && MoundPlaces.holds(land, place, places) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MoundPlaces.Land land(GenerationContext context, TermiteMoundStructure mounds) {
        return new MoundPlaces.Land(context.seed(), context.chunkGenerator(), context.heightAccessor(), context.randomState(), mounds.biomes());
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        MoundPlaces.Numbers places = numbers.places();
        ChunkPos chunk = context.chunkPos();
        if (!MoundPlaces.isPlace(context.seed(), chunk.x, chunk.z, places)) {
            return Optional.empty();
        }
        MoundPlaces.Place place = MoundPlaces.candidate(context.seed(), chunk.x, chunk.z, places);
        MoundPlaces.Land land = land(context, this);
        BlockPos foot = MoundPlaces.holds(land, place, places);
        if (foot == null) {
            return Optional.empty();
        }
        Pick pick = pickAt(numbers, land, place, MoundPlaces.sizeQuantile(land, place, places));
        BoundingBox box = new BoundingBox(foot);
        return Optional.of(new GenerationStub(foot, builder -> builder.addPiece(new TermiteMoundPiece(pick, foot, box))));
    }
}
