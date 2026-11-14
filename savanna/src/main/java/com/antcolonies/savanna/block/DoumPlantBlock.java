package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A young doum palm on the ground (floodplain trees step 5): the seedling (a single folded fan leaf up from the nut) or
 * the stemless rosette (fans straight out of the ground; a doum spends years so before its stem rises). Drawn from the
 * viewer's own recipe ({@link DoumLook}); it does not grow yet (growing in play is step 8).
 */
public class DoumPlantBlock extends BushBlock implements EntityBlock, DoumPiece {
    public static final MapCodec<DoumPlantBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Piece.CODEC.fieldOf("piece").forGetter(DoumPlantBlock::piece), propertiesCodec()).apply(i, DoumPlantBlock::new));
    private static final VoxelShape SEEDLING = box(4, 0, 4, 12, 12, 12), ROSETTE = box(1, 0, 1, 15, 14, 15);

    private final Piece piece;

    public DoumPlantBlock(Piece piece, Properties properties) {
        super(properties);
        this.piece = piece;
    }

    @Override
    public Piece piece() {
        return piece;
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return piece == Piece.SEEDLING ? SEEDLING : ROSETTE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoumBlockEntity(pos, state);
    }
}
