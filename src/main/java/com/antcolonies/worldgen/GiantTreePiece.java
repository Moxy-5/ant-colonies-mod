package com.antcolonies.worldgen;

import com.antcolonies.registry.ModStructures;
import com.antcolonies.tree.TreeStage;

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
        super(ModStructures.GIANT_TREE_PIECE.get(), 0, box);
        this.pick = pick;
        this.foot = foot;
    }

    public GiantTreePiece(CompoundTag tag) {
        super(ModStructures.GIANT_TREE_PIECE.get(), tag);
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

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        TreePlacer.place(level, TreePlacer.placed(pick.kind(), pick.seed(), pick.age(), pick.stage()), foot, box, Block.UPDATE_CLIENTS);
        // The tree goes into the tree book (M7.5 step 4), so it grows from here (once, whichever of its chunks is first).
        com.antcolonies.tree.Forest.queue(level.getLevel(), pick.kind(), foot, pick.seed(), pick.age(), pick.stage());
    }
}
