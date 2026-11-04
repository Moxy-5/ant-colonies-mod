package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.registry.SavannaStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One giant savanna tree in new land (see {@link GiantTreeStructure}): saved as its record (kind, seed, age, stage and
 * foot), so each chunk it crosses works out the same tree and puts in its own part of it ({@link TreePlacer}).
 */
public class GiantTreePiece extends StructurePiece {
    private final GiantTreeStructure.Pick pick;
    private final BlockPos foot;

    public GiantTreePiece(GiantTreeStructure.Pick pick, BlockPos foot, BoundingBox box) {
        super(SavannaStructures.GIANT_TREE_PIECE.get(), 0, box);
        this.pick = pick;
        this.foot = foot;
    }

    public GiantTreePiece(CompoundTag tag) {
        super(SavannaStructures.GIANT_TREE_PIECE.get(), tag);
        pick = new GiantTreeStructure.Pick(tag.getString("kind"), tag.getLong("seed"), tag.getDouble("age"), stage(tag.getString("stage")));
        foot = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    private static TreeStage stage(String id) {
        for (TreeStage s : TreeStage.values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return TreeStage.LIVING;
    }

    public GiantTreeStructure.Pick pick() {
        return pick;
    }

    public BlockPos foot() {
        return foot;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("kind", pick.kind());
        tag.putLong("seed", pick.seed());
        tag.putDouble("age", pick.age());
        tag.putString("stage", pick.stage().id);
        tag.putInt("x", foot.getX());
        tag.putInt("y", foot.getY());
        tag.putInt("z", foot.getZ());
    }

    /**
     * Trees with something lying (a fallen log, broken limbs round a dead one) fitted to their land, kept while their
     * chunks are put in (each chunk would work it out the same). Enough are kept for every tree being made round a
     * player (was 16, docs/plans/savanna-loading.md).
     */
    private static final java.util.Map<String, TreePlacer.Placed> FITTED = kept(256);
    /** The dead wood round each tree fitted to its land: worked out when the tree is planned, kept for its chunks. */
    private static final java.util.Map<String, TreePlacer.Placed> LITTER = kept(512);

    private static java.util.Map<String, TreePlacer.Placed> kept(int most) {
        return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(most * 2, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, TreePlacer.Placed> eldest) {
                return size() > most;
            }
        });
    }

    private static String key(GiantTreeStructure.Pick pick, BlockPos foot) {
        return pick.kind() + "|" + pick.seed() + "|" + pick.age() + "|" + pick.stage().id + "|" + foot.asLong();
    }

    /**
     * A dead tree in new land with what lies on the ground fitted to it (see {@link TreePlacer#rest}). The land's height comes from the
     * world's shape itself, not from blocks, so every chunk of the tree gets the same answer even where the chunks next
     * to it are not made yet.
     */
    private TreePlacer.Placed lying(WorldGenLevel level, ChunkGenerator generator, TreePlacer.Placed tree) {
        return FITTED.computeIfAbsent(key(pick, foot), k -> TreePlacer.rest(tree, foot, ground(level, generator)));
    }

    /** Step 6c: a young tree of new land that is instead a dead young tree lying in the grass (no tree in the book). */
    public static boolean fallenYoung(GiantTreeStructure.Pick pick) {
        return pick.stage() == com.antcolonies.savanna.tree.TreeStage.FALLEN && pick.age() < com.antcolonies.savanna.tree.TreeLife.YOUNG_END;
    }

    /**
     * Step 6c: the dead wood lying round a tree of new land (see {@link com.antcolonies.savanna.tree.Litter}), laid on the land
     * given by {@code ground}: under a living tree its fallen branches and maybe a giant limb; a fallen young tree is all
     * litter; a dead tree has its own fallen pieces already (nothing more).
     */
    public static TreePlacer.Placed litter(GiantTreeStructure.Pick pick, BlockPos foot, TreePlacer.Ground ground) {
        com.antcolonies.savanna.tree.TreeVoxels v;
        if (fallenYoung(pick)) {
            v = com.antcolonies.savanna.tree.Litter.fallenYoung(pick.seed());
        } else if (pick.stage() == com.antcolonies.savanna.tree.TreeStage.LIVING) {
            com.antcolonies.savanna.tree.TreeKind kind = TreePlacer.kind(pick.kind());
            TreePlacer.Placed tree = TreePlacer.placed(pick.kind(), pick.seed(), pick.age(), pick.stage());
            double reach = Math.max(Math.max(-tree.minX(), tree.maxX()), Math.max(-tree.minZ(), tree.maxZ()));
            v = com.antcolonies.savanna.tree.Litter.underTree(pick.seed(), pick.age(), reach, kind.trunk().radius(), kind.isBaobab());
        } else {
            v = new com.antcolonies.savanna.tree.TreeVoxels();
        }
        return TreePlacer.rest(TreePlacer.litter(v), foot, ground);
    }

    /**
     * {@link #litter} for a tree of new land, on the land as the world's shape makes it: worked out once when the tree is
     * planned and kept for its chunks (docs/plans/savanna-loading.md; every chunk would work it out the same).
     */
    static TreePlacer.Placed newLandLitter(GiantTreeStructure.Pick pick, BlockPos foot, TreePlacer.Ground ground) {
        String key = key(pick, foot);
        TreePlacer.Placed kept = LITTER.get(key);
        if (kept == null) {
            kept = litter(pick, foot, ground);
            LITTER.put(key, kept);
        }
        return kept;
    }

    /** The land's height as the world's shape makes it (the same answer for every chunk, made yet or not). */
    private static TreePlacer.Ground ground(WorldGenLevel level, ChunkGenerator generator) {
        net.minecraft.world.level.levelgen.RandomState random = level.getLevel().getChunkSource().randomState();
        return (x, z) -> GroundHeights.at(generator, x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        long start = System.nanoTime();
        try {
            putDown(level, generator, box);
        } finally {
            com.antcolonies.debug.Perf.newLand(com.antcolonies.debug.Perf.NewLand.PLACING, System.nanoTime() - start);
        }
    }

    private void putDown(WorldGenLevel level, ChunkGenerator generator, BoundingBox box) {
        TreePlacer.Placed litter = newLandLitter(pick, foot, ground(level, generator));
        if (fallenYoung(pick)) {
            TreePlacer.place(level, litter, foot, box, Block.UPDATE_CLIENTS);
            return; // only dead wood: nothing for the tree book
        }
        TreePlacer.Placed tree = TreePlacer.placed(pick.kind(), pick.seed(), pick.age(), pick.stage());
        if (tree.lying() != null) {
            tree = lying(level, generator, tree);
        }
        TreePlacer.place(level, tree, foot, box, Block.UPDATE_CLIENTS);
        TreePlacer.place(level, litter, foot, box, Block.UPDATE_CLIENTS);
        // The tree goes into the tree book (M7.5 step 4), so it grows from here (once, whichever of its chunks is first).
        com.antcolonies.savanna.tree.Forest.queue(level.getLevel(), pick.kind(), foot, pick.seed(), pick.age(), pick.stage());
    }
}
