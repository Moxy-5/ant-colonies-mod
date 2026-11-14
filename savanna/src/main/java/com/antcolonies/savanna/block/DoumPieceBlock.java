package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A doum palm's crown or a bunch of its nuts (floodplain trees step 5): one block that draws the whole sculpted piece,
 * about 1,500 small boxes for a crown, reaching up to 6 blocks out of it ({@link DoumLook}, the client's DoumModel). A
 * crown sits on top of its stem, one of the four blocks over the stem's 2 by 2 top; the cap the fronds come out of is
 * the only solid part (the fronds themselves can be walked through, as a leaf can't hold a player). A bunch is ripe
 * brown or still green ({@link #RIPE}); dropping and ripening in stages are step 6b.
 */
public class DoumPieceBlock extends Block implements EntityBlock, DoumPiece {
    public static final MapCodec<DoumPieceBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Piece.CODEC.fieldOf("piece").forGetter(DoumPieceBlock::piece), propertiesCodec()).apply(i, DoumPieceBlock::new));
    /** A bunch's nuts ripe (chestnut to orange-brown) or still green. */
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private static final VoxelShape CAP = box(0, 0, 0, 16, 6, 16), CROWN_OUTLINE = box(0, 0, 0, 16, 11, 16),
            BUNCH = box(4, 0, 4, 12, 16, 12), FRONDS = box(0, 0, 0, 16, 2, 16);

    private final Piece piece;

    public DoumPieceBlock(Piece piece, Properties properties) {
        super(properties);
        this.piece = piece;
        if (piece == Piece.BUNCH) {
            registerDefaultState(defaultBlockState().setValue(RIPE, true));
        }
    }

    @Override
    public Piece piece() {
        return piece;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Called from Block's constructor, before piece is set: every DoumPieceBlock has the property; only the bunch
        // shows it (the crown's blockstate file names one model for both).
        builder.add(RIPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return piece == Piece.CROWN ? CROWN_OUTLINE : piece == Piece.FRONDS ? FRONDS : BUNCH;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return piece == Piece.CROWN ? CAP : Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoumBlockEntity(pos, state);
    }
}
