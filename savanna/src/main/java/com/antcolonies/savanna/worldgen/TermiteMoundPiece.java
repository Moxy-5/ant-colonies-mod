package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.registry.SavannaStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One termite mound's place in new land (see {@link TermiteMoundStructure}): saved as its {@link TermiteMoundStructure.Pick}
 * and its middle. It puts no blocks in itself: when its chunk is made it hands the place to the server
 * ({@link NewLandMounds#queue}), which puts the colony, nest and mound in once the land round it is loaded.
 */
public class TermiteMoundPiece extends StructurePiece {
    private final TermiteMoundStructure.Pick pick;
    private final BlockPos middle;

    public TermiteMoundPiece(TermiteMoundStructure.Pick pick, BlockPos middle, BoundingBox box) {
        super(SavannaStructures.TERMITE_MOUND_PIECE.get(), 0, box);
        this.pick = pick;
        this.middle = middle;
    }

    public TermiteMoundPiece(CompoundTag tag) {
        super(SavannaStructures.TERMITE_MOUND_PIECE.get(), tag);
        pick = load(tag);
        middle = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public static TermiteMoundStructure.Pick load(CompoundTag tag) {
        return new TermiteMoundStructure.Pick(tag.getBoolean("living"), tag.getInt("size"), tag.getDouble("height"), tag.getDouble("days"),
                tag.getInt("woods"), tag.getBoolean("burrow"), tag.getBoolean("grass"), tag.getLong("seed"));
    }

    public static void save(TermiteMoundStructure.Pick pick, CompoundTag tag) {
        tag.putBoolean("living", pick.living());
        tag.putInt("size", pick.size());
        tag.putDouble("height", pick.height());
        tag.putDouble("days", pick.days());
        tag.putInt("woods", pick.woods());
        tag.putBoolean("burrow", pick.burrow());
        tag.putBoolean("grass", pick.grass());
        tag.putLong("seed", pick.seed());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        save(pick, tag);
        tag.putInt("x", middle.getX());
        tag.putInt("y", middle.getY());
        tag.putInt("z", middle.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (box.isInside(middle)) {
            NewLandMounds.queue(level.getLevel(), middle, pick);
        }
    }
}
