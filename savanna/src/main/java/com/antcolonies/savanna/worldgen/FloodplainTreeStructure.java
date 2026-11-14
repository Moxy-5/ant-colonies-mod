package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.antcolonies.savanna.tree.DoumShape;
import com.antcolonies.savanna.tree.FigShape;
import com.antcolonies.savanna.tree.TreeLands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The floodplain's own trees in new land (floodplain trees step 7, 2026-11-14, {@code docs/plans/floodplain-trees.md}):
 * {@code fig}, a sycamore fig standing alone, or {@code doum}, a grove of 2 to 5 doum palms. Only on the floodplain's
 * greener ground ({@link TreeLands#mayStand}), on dry ground at least a block over the water, never on a slope, never
 * crowding a termite mound. Real: along African savanna rivers the sycamore fig stands mostly alone, big trees scattered
 * along the bank; the doum palm grows in loose groves (its nuts spread by elephants and floods), young palms at the edge.
 * Spacing (my picks, from the measured floodplain, a strip about 20 blocks wide each side of a river): a fig's place one
 * per 48 by 48 blocks of floodplain and never two big figs (mature, grown or dead) within {@link #BIG_FIG_APART}; a grove's
 * place one per 48 by 48, its palms 8 to 12 apart within 12 of its middle; a palm never stands under a fig's crown. The
 * floodplain's trees and the dry trees never share ground (the dry trees keep off it, step 2), so they have their own sets.
 * Dead 1 in 8 (the user). These trees do not grow or die in play yet (step 8): they are not in the tree book.
 */
public class FloodplainTreeStructure extends Structure {
    public static final MapCodec<FloodplainTreeStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(FloodplainTreeStructure::kind)).apply(i, FloodplainTreeStructure::new));

    /** The figs' structure set, read to keep big figs apart and palms out from under figs. */
    static final ResourceKey<StructureSet> FIGS = ResourceKey.create(Registries.STRUCTURE_SET,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "floodplain_figs"));
    /** Dead: 1 in 8 (the user). */
    public static final int DEAD_ONE_IN = 8;
    /** Two big figs (mature, grown or dead) never stand nearer than this, trunk to trunk (the plan). */
    public static final int BIG_FIG_APART = 40;
    /** A palm's trunk keeps this far out past a fig's crown edge (my pick: crowns may touch, a stem never under a fig). */
    static final int PALM_PAST_FIG = 3;
    /** The steepest a foot may be (blocks up and down round the trunk): the floodplain is flat (my pick). */
    static final int MAX_UNEVEN = 2;
    /** A grove's palms: 2 to 5, each 8 to 12 from the nearest before it, within this of the grove's middle (my picks). */
    public static final int GROVE_MOST = 5, PALM_APART = 8, PALM_APART_MOST = 12, GROVE_REACH = 12;

    private final String kind;

    public FloodplainTreeStructure(StructureSettings settings, String kind) {
        super(settings);
        this.kind = kind;
    }

    public String kind() {
        return kind;
    }

    @Override
    public StructureType<?> type() {
        return SavannaStructures.FLOODPLAIN_TREE.get();
    }

    /** One tree: {@code fig} or {@code doum}, the viewer's seed, its age by the viewer's name, and its foot. */
    public record Tree(String kind, int seed, String age, BlockPos foot) {
    }

    // ---------------------------------------------------------------- the dice

    /**
     * A fig's place in a chunk, as the dice give it (the same answer every time it is asked, so neighbours can be worked
     * out): its spot, the viewer's seed and age. Ages (my picks): dead 1 in 8 (just died 3, breaking up 3, fallen 2);
     * of the living half young (seedling 1, sapling 2, young 3), half big (mature 85, grown 15, as the dry trees' giants).
     */
    record FigPlan(int x, int z, int seed, FigShape.Age age, FigShape.Age ifYoung) {
        boolean big() {
            return big(age);
        }

        static boolean big(FigShape.Age age) {
            return age.ordinal() >= FigShape.Age.MATURE.ordinal();
        }
    }

    static FigPlan figPlan(long worldSeed, ChunkPos chunk) {
        WorldgenRandom random = random(worldSeed, chunk);
        int x = chunk.getMinBlockX() + random.nextInt(16), z = chunk.getMinBlockZ() + random.nextInt(16);
        int seed = 1 + random.nextInt(1_000_000);
        FigShape.Age young = pick(random, new FigShape.Age[] {FigShape.Age.SEEDLING, FigShape.Age.SAPLING, FigShape.Age.YOUNG}, 1, 2, 3);
        FigShape.Age age;
        if (random.nextInt(DEAD_ONE_IN) == 0) {
            age = pick(random, new FigShape.Age[] {FigShape.Age.JUST_DIED, FigShape.Age.BREAKING_UP, FigShape.Age.FALLEN}, 3, 3, 2);
        } else if (random.nextBoolean()) {
            age = young;
        } else {
            age = random.nextInt(100) < 15 ? FigShape.Age.GROWN : FigShape.Age.MATURE;
        }
        return new FigPlan(x, z, seed, age, young);
    }

    /**
     * How far a fig's wood and leaves reach from its trunk, by age (measured on the viewer's 40 exported figs, the
     * widest of 5 seeds).
     */
    static int figReach(FigShape.Age age) {
        return switch (age) {
            case SEEDLING -> 0;
            case SAPLING -> 5;
            case YOUNG -> 10;
            case MATURE -> 17;
            case GROWN -> 24;
            case JUST_DIED -> 21;
            case BREAKING_UP -> 16;
            case FALLEN -> 18;
        };
    }

    /** Ages of a grove's palms (my picks): the first two its heart, the rest the heart or the young edge; dead 1 in 8. */
    static DoumShape.Age palmAge(WorldgenRandom random, int index) {
        DoumShape.Age[] heart = {DoumShape.Age.FORK, DoumShape.Age.CLUMP, DoumShape.Age.GROWN};
        DoumShape.Age[] edge = {DoumShape.Age.SEEDLING, DoumShape.Age.ROSETTE, DoumShape.Age.STEM, DoumShape.Age.FORK};
        DoumShape.Age age = index < 2 || random.nextBoolean() ? pick(random, heart, 3, 4, 3) : pick(random, edge, 2, 3, 3, 2);
        if (random.nextInt(DEAD_ONE_IN) == 0) {
            age = pick(random, new DoumShape.Age[] {DoumShape.Age.JUST_DIED, DoumShape.Age.SNAGS, DoumShape.Age.LAST_SNAG}, 3, 3, 2);
        }
        return age;
    }

    /** How far a palm's stems reach from its middle, by age (the viewer's palms, the widest of 5 seeds, plus a crown). */
    static int palmReach(DoumShape.Age age) {
        return switch (age) {
            case SEEDLING, ROSETTE -> 1;
            case STEM -> 5;
            case FORK -> 7;
            case CLUMP -> 12;
            case GROWN, JUST_DIED -> 13;
            case SNAGS -> 10;
            case LAST_SNAG -> 14;
        };
    }

    @SafeVarargs
    private static <T> T pick(WorldgenRandom random, T[] things, int... weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < things.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return things[i];
            }
        }
        return things[things.length - 1];
    }

    /** The same dice a structure's chunk gets (Minecraft's own seeding for a structure start). */
    private static WorldgenRandom random(long worldSeed, ChunkPos chunk) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(worldSeed, chunk.x, chunk.z);
        return random;
    }

    // ---------------------------------------------------------------- planning

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        long start = System.nanoTime();
        try {
            return kind.equals("fig") ? planFig(context) : planGrove(context);
        } finally {
            com.antcolonies.debug.Perf.newLand(com.antcolonies.debug.Perf.NewLand.PLANNING, System.nanoTime() - start);
        }
    }

    private Optional<GenerationStub> planFig(GenerationContext context) {
        FigPlan plan = figPlan(context.seed(), context.chunkPos());
        FigShape.Age age = plan.age();
        if (FigPlan.big(age) && bigFigNear(context, plan)) {
            age = plan.ifYoung(); // a big fig near already: here a young one comes up instead
        }
        BlockPos foot = foot(context, "fig", plan.x(), plan.z(), FigPlan.big(age) ? 3 : 1);
        if (foot == null) {
            return Optional.empty();
        }
        Tree tree = new Tree("fig", plan.seed(), age.id, foot);
        BoundingBox box = FloodplainTreePiece.shape(tree).box(foot);
        if (TermiteMoundStructure.crowds(context, box)) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(foot, builder -> builder.addPiece(new FloodplainTreePiece(tree, box))));
    }

    /** Whether a fig place before this one (lower in the figs' grid) holds a big fig within {@link #BIG_FIG_APART}. */
    private static boolean bigFigNear(GenerationContext context, FigPlan plan) {
        for (FigPlan other : figsNear(context, plan.x(), plan.z(), BIG_FIG_APART)) {
            if (other.x() == plan.x() && other.z() == plan.z()) {
                continue;
            }
            boolean before = other.x() < plan.x() || (other.x() == plan.x() && other.z() < plan.z());
            if (before && other.big() && dist(other.x(), other.z(), plan.x(), plan.z()) < BIG_FIG_APART
                    && foot(context, "fig", other.x(), other.z(), 3) != null) {
                return true;
            }
        }
        return false;
    }

    /** Every fig place whose spot may lie within {@code reach} of (x, z), as the figs' grid puts them (whether or not one stands). */
    private static List<FigPlan> figsNear(GenerationContext context, int x, int z, int reach) {
        List<FigPlan> out = new ArrayList<>();
        StructureSet set = context.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(FIGS);
        if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement grid)) {
            return out;
        }
        int s = grid.spacing();
        for (int rx = Math.floorDiv((x - reach) >> 4, s); rx <= Math.floorDiv((x + reach) >> 4, s); rx++) {
            for (int rz = Math.floorDiv((z - reach) >> 4, s); rz <= Math.floorDiv((z + reach) >> 4, s); rz++) {
                ChunkPos chunk = grid.getPotentialStructureChunk(context.seed(), rx * s, rz * s);
                out.add(figPlan(context.seed(), chunk));
            }
        }
        return out;
    }

    private Optional<GenerationStub> planGrove(GenerationContext context) {
        WorldgenRandom random = random(context.seed(), context.chunkPos());
        int mx = context.chunkPos().getMinBlockX() + random.nextInt(16), mz = context.chunkPos().getMinBlockZ() + random.nextInt(16);
        int count = 2 + random.nextInt(GROVE_MOST - 1);
        List<Tree> palms = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();
        int[] xs = new int[GROVE_MOST], zs = new int[GROVE_MOST];
        for (int i = 0; i < count; i++) {
            int seed = 1 + random.nextInt(1_000_000);
            DoumShape.Age age = palmAge(random, i);
            boolean stood = false;
            // the first at the grove's middle, each next 8 to 12 from one already there (3 tries to find floodplain)
            for (int tries = 0; tries < 3; tries++) {
                int x = mx, z = mz;
                if (i > 0) {
                    int from = random.nextInt(i);
                    double a = random.nextDouble() * Math.PI * 2, d = PALM_APART + random.nextInt(PALM_APART_MOST - PALM_APART + 1);
                    x = xs[from] + (int) Math.round(Math.cos(a) * d);
                    z = zs[from] + (int) Math.round(Math.sin(a) * d);
                }
                if (dist(x, z, mx, mz) > GROVE_REACH || tooNearPalm(xs, zs, i, x, z) || underFig(context, x, z, palmReach(age))) {
                    continue;
                }
                BlockPos foot = foot(context, "doum", x, z, age.ordinal() >= DoumShape.Age.STEM.ordinal() ? 1 : 0);
                if (foot == null) {
                    continue;
                }
                Tree tree = new Tree("doum", seed, age.id, foot);
                BoundingBox box = FloodplainTreePiece.shape(tree).box(foot);
                if (TermiteMoundStructure.crowds(context, box)) {
                    continue;
                }
                xs[i] = x;
                zs[i] = z;
                palms.add(tree);
                boxes.add(box);
                stood = true;
                break;
            }
            if (!stood) {
                xs[i] = Integer.MIN_VALUE / 2; // no palm here: none measured against it
                zs[i] = Integer.MIN_VALUE / 2;
            }
        }
        if (palms.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(palms.get(0).foot(), builder -> {
            for (int i = 0; i < palms.size(); i++) {
                builder.addPiece(new FloodplainTreePiece(palms.get(i), boxes.get(i)));
            }
        }));
    }

    private static boolean tooNearPalm(int[] xs, int[] zs, int n, int x, int z) {
        for (int j = 0; j < n; j++) {
            if (dist(xs[j], zs[j], x, z) < PALM_APART) {
                return true;
            }
        }
        return false;
    }

    /** Whether a palm reaching {@code reach} at (x, z) would stand under a fig that stands there (its crown plus a little). */
    private static boolean underFig(GenerationContext context, int x, int z, int reach) {
        int most = figReach(FigShape.Age.GROWN) + reach + PALM_PAST_FIG;
        for (FigPlan fig : figsNear(context, x, z, most)) {
            if (dist(fig.x(), fig.z(), x, z) < figReach(fig.age()) + reach + PALM_PAST_FIG
                    && foot(context, "fig", fig.x(), fig.z(), 1) != null) {
                return true; // (the fig's own age before a nearer big fig makes it young: a little more room, never less)
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the ground

    /**
     * A tree's foot at (x, z): the lowest ground round its trunk (the trunk sits in the ground), on the floodplain, at
     * least a block over the water (never the low strip flush with the river), never in water or on a slope; else null.
     */
    static BlockPos foot(GenerationContext context, String kind, int x, int z, int reach) {
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int[][] around = {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
        if (reach > 0) {
            GroundHeights.warm(context.chunkGenerator(), context.heightAccessor(), context.randomState(),
                    new int[] {x, x + reach, x - reach, x, x}, new int[] {z, z, z, z + reach, z - reach});
        }
        for (int[] o : around) {
            int surface = height(context, x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG);
            int floor = height(context, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
            if (surface != floor) {
                return null;
            }
            lowest = Math.min(lowest, surface);
            highest = Math.max(highest, surface);
            if (reach == 0) {
                break;
            }
        }
        if (highest - lowest > MAX_UNEVEN || lowest <= context.chunkGenerator().getSeaLevel()) {
            return null;
        }
        boolean land = TreeLands.mayStand(kind, context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(lowest),
                QuartPos.fromBlock(z), context.randomState().sampler()));
        return land ? new BlockPos(x, lowest, z) : null;
    }

    private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
        return GroundHeights.at(context.chunkGenerator(), x, z, type, context.heightAccessor(), context.randomState());
    }

    static double dist(int x0, int z0, int x1, int z1) {
        return Mth.length(x1 - x0, z1 - z0);
    }
}
