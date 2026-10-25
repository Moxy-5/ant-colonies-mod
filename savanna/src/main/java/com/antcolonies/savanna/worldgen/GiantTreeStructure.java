package com.antcolonies.savanna.worldgen;

import java.util.Optional;

import com.antcolonies.savanna.tree.Litter;
import com.antcolonies.savanna.tree.TreeKind;
import com.antcolonies.savanna.tree.TreeShapes;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A giant savanna tree in new land (M7.5 step 3): one tree, which may span several chunks (too big for Minecraft's
 * normal tree features). Two of these are placed in savannas (data/antcolonies/worldgen/structure/): the <b>big</b>
 * trees, grown, ancient and dead giants, spaced like the scattered big trees of a real savanna, their crowns rarely
 * touching; and the <b>young</b> ones, seedlings and young thorny trees, many more, kept out from under the giants. A
 * real savanna's trees by age: many young, fewer grown, a few ancient giants, and dead ones among them (a dead acacia
 * stands, sheds and lies for years; a dead baobab slumps into fibre within months, so it is mostly found as a heap).
 * The numbers are my picks, to confirm.
 *
 * @param size {@code big} or {@code young}
 */
public class GiantTreeStructure extends Structure {
    public static final MapCodec<GiantTreeStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("size").forGetter(GiantTreeStructure::size)).apply(i, GiantTreeStructure::new));

    /** Kinds by how common they are (my picks): umbrella thorn the common one, camel thorn, a rare baobab. */
    static final String[] KINDS = {"umbrella_thorn", "camel_thorn", "baobab"};
    static final int[] KIND_WEIGHTS = {6, 3, 1};
    /** Of the big trees: how many are dead (my pick). */
    static final double DEAD_SHARE = 0.28;
    /** Of the living big trees: how many are ancient giants (my pick). */
    static final double ANCIENT_SHARE = 0.15;
    /** The steepest a foot may be (blocks up and down under the trunk) before no tree stands there. */
    static final int MAX_UNEVEN = 6;
    /**
     * A tree lying fallen in new land lies on fairly flat ground (the user's wish 2026-10-19, a home for the insects): the
     * land under its log may be at most this far above or below its foot, or another fallen tree is tried (my pick).
     */
    static final int FALLEN_UNEVEN = 3;
    /** How many fallen trees are tried before a standing dead one takes the place. */
    static final int FALLEN_TRIES = 4;
    /**
     * A tree whose place a termite mound has (its crown or its wood would reach within {@link TermiteMoundStructure#CLEAR}
     * of the mound) tries this many places in all, the others up to {@link #MOVE} blocks from its chunk's middle, before
     * it gives up (the user's report, 2026-10-24: far fewer trees since the mounds came; most were simply dropped).
     */
    static final int SPOTS = 8;
    static final int MOVE = 24;

    private final String size;

    public GiantTreeStructure(StructureSettings settings, String size) {
        super(settings);
        this.size = size;
    }

    public String size() {
        return size;
    }

    @Override
    public StructureType<?> type() {
        return SavannaStructures.GIANT_TREE.get();
    }

    /** One tree: its kind, its own seed, its age and its stage. */
    public record Pick(String kind, long seed, double age, TreeStage stage) {
    }

    /** What tree stands at a new place, by the dice: the real spread of ages and stages (my picks). */
    public static Pick pick(String size, RandomSource random) {
        int roll = random.nextInt(KIND_WEIGHTS[0] + KIND_WEIGHTS[1] + KIND_WEIGHTS[2]);
        String kind = roll < KIND_WEIGHTS[0] ? KINDS[0] : roll < KIND_WEIGHTS[0] + KIND_WEIGHTS[1] ? KINDS[1] : KINDS[2];
        long seed = random.nextLong() & 0xFFFFFFFFL;
        boolean baobab = kind.equals("baobab");
        if (size.equals("young")) {
            // Many more small ones than big: seedlings and young trees, most of them small (a reverse-J spread).
            double u = random.nextDouble();
            if (!baobab && random.nextDouble() < Litter.FALLEN_YOUNG_SHARE) {
                // Step 6c: a dead young acacia knocked over in the grass (a young baobab's soft wood rots away in months).
                return new Pick(kind, seed, round(0.2 + 0.25 * u), TreeStage.FALLEN);
            }
            return new Pick(kind, seed, round(0.03 + 0.42 * u * u), TreeStage.LIVING);
        }
        if (random.nextDouble() < DEAD_SHARE) {
            double age = round(0.6 + random.nextDouble() * (TreeShapes.ANCIENT - 0.6));
            double r = random.nextDouble();
            TreeStage stage = baobab ? (r < 0.2 ? TreeStage.SLUMP : r < 0.65 ? TreeStage.HEAP : TreeStage.HEAP_DRY)
                    : r < 0.25 ? TreeStage.SNAG : r < 0.5 ? TreeStage.MUD : r < 0.75 ? TreeStage.SHED : TreeStage.FALLEN;
            return new Pick(kind, seed, age, stage);
        }
        double age = random.nextDouble() < ANCIENT_SHARE ? 1 + random.nextDouble() * (TreeShapes.ANCIENT - 1) : 0.6 + random.nextDouble() * 0.4;
        return new Pick(kind, seed, round(age), TreeStage.LIVING);
    }

    /** Ages to the hundredth: a tree is the same tree for every chunk, and few different ones are worked out. */
    private static double round(double age) {
        return Math.round(age * 100) / 100.0;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        long start = System.nanoTime();
        try {
            return plan(context);
        } finally {
            com.antcolonies.debug.Perf.newLand(com.antcolonies.debug.Perf.NewLand.PLANNING, System.nanoTime() - start);
        }
    }

    private Optional<GenerationStub> plan(GenerationContext context) {
        RandomSource random = context.random();
        int x = context.chunkPos().getMinBlockX() + random.nextInt(16), z = context.chunkPos().getMinBlockZ() + random.nextInt(16);
        Pick pick = pick(size, random);
        TreeKind kind = TreePlacer.kind(pick.kind());
        // Its foot: the lowest ground round the trunk (so the trunk sits in the ground, not over it); never in water.
        int reach = pick.age() < 0.45 ? 0 : (int) Math.ceil(kind.trunk().radius() * (kind.isBaobab() ? 1 : 1.5));
        for (int spot = 0; spot < SPOTS; spot++) {
            if (spot > 0) {
                x = context.chunkPos().getMiddleBlockX() + random.nextInt(2 * MOVE + 1) - MOVE;
                z = context.chunkPos().getMiddleBlockZ() + random.nextInt(2 * MOVE + 1) - MOVE;
            }
            BlockPos foot = foot(context, x, z, reach);
            if (foot == null) {
                if (spot == 0) {
                    return Optional.empty(); // water or a steep slope where it was meant to be: no tree, as before the mounds
                }
                continue;
            }
            Pick chosen = pick.stage() == TreeStage.FALLEN && !GiantTreePiece.fallenYoung(pick) ? flatFall(context, foot, pick, random) : pick;
            TreePlacer.Placed tree = GiantTreePiece.fallenYoung(chosen) ? null : TreePlacer.placed(chosen.kind(), chosen.seed(), chosen.age(), chosen.stage());
            // Step 6c: the dead wood lying round it (on the land as the world's shape makes it) widens the piece to take it in.
            TreePlacer.Placed litter = GiantTreePiece.litter(chosen, foot, (lx, lz) -> height(context, lx, lz, Heightmap.Types.WORLD_SURFACE_WG));
            net.minecraft.world.level.levelgen.structure.BoundingBox box = tree != null ? tree.box(foot) : litter.box(foot);
            if (tree != null && litter.size() > 0) {
                box = box.encapsulate(litter.box(foot));
            }
            if (TermiteMoundStructure.crowds(context, box)) {
                continue; // M8 step 1b: a termite mound has this place (trees on old humps are put there on purpose)
            }
            net.minecraft.world.level.levelgen.structure.BoundingBox pieceBox = box;
            return Optional.of(new GenerationStub(foot, builder -> builder.addPiece(new GiantTreePiece(chosen, foot, pieceBox))));
        }
        return Optional.empty();
    }

    /** A tree's foot at (x, z): the lowest ground round the trunk (so the trunk sits in the ground, not over it); null in water or on a steep slope. */
    private static BlockPos foot(GenerationContext context, int x, int z, int reach) {
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int[][] around = {{0, 0}, {reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}};
        for (int[] o : around) {
            int surface = height(context, x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG);
            int floor = height(context, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
            if (surface != floor) {
                return null;
            }
            lowest = Math.min(lowest, surface);
            highest = Math.max(highest, surface);
        }
        return highest - lowest > MAX_UNEVEN ? null : new BlockPos(x, lowest, z);
    }

    /**
     * A fallen tree whose log lies on fairly flat ground: its own, or another of the same kind and age (another fall's
     * way), or, if none of {@link #FALLEN_TRIES} is, the tree still standing, shed of its limbs.
     */
    private static Pick flatFall(GenerationContext context, BlockPos foot, Pick pick, RandomSource random) {
        for (int i = 0; i < FALLEN_TRIES; i++) {
            Pick p = i == 0 ? pick : new Pick(pick.kind(), random.nextLong() & 0xFFFFFFFFL, pick.age(), TreeStage.FALLEN);
            TreePlacer.Placed tree = TreePlacer.placed(p.kind(), p.seed(), p.age(), p.stage());
            if (flatUnder(context, foot, tree)) {
                return p;
            }
        }
        return new Pick(pick.kind(), pick.seed(), pick.age(), TreeStage.SHED);
    }

    /** Whether the land under a fallen tree's log (every second column of it) is within {@link #FALLEN_UNEVEN} of its foot. */
    private static boolean flatUnder(GenerationContext context, BlockPos foot, TreePlacer.Placed tree) {
        if (tree.lying() == null) {
            return true;
        }
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (int i = 0; i < tree.size(); i++) {
            int x = tree.xs()[i], z = tree.zs()[i];
            if (tree.lying().slice()[i] < 0 || ((x + z) & 1) != 0 || !seen.add(BlockPos.asLong(x, 0, z))) {
                continue;
            }
            int h = height(context, foot.getX() + x, foot.getZ() + z, Heightmap.Types.WORLD_SURFACE_WG);
            if (Math.abs(h - foot.getY()) > FALLEN_UNEVEN) {
                return false;
            }
        }
        return true;
    }

    private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getFirstFreeHeight(x, z, type, context.heightAccessor(), context.randomState());
    }
}
