package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A doum palm's crown or a bunch of its nuts (floodplain trees step 5): one block that draws the whole sculpted piece,
 * about 1,500 small boxes for a crown, reaching up to 6 blocks out of it ({@link DoumLook}, the client's DoumModel). A
 * crown sits on top of its stem, one of the four blocks over the stem's 2 by 2 top; the cap the fronds come out of is
 * the only solid part (the fronds themselves can be walked through, as a leaf can't hold a player). A bunch ripens in
 * stages, a bare stalk, green, half-ripe, ripe, and drops its nuts ({@link HangingFruit}, step 6b); picked by hand or
 * moved on by bone meal.
 */
public class DoumPieceBlock extends Block implements EntityBlock, DoumPiece, BonemealableBlock {
    public static final MapCodec<DoumPieceBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Piece.CODEC.fieldOf("piece").forGetter(DoumPieceBlock::piece), propertiesCodec()).apply(i, DoumPieceBlock::new));
    /** A ripe bunch gives 1 to 3 nuts, picked or fallen (my pick). */
    private static final int RIPE_MIN = 1, RIPE_MAX = 3;
    private static final VoxelShape CAP = box(0, 0, 0, 16, 6, 16), CROWN_OUTLINE = box(0, 0, 0, 16, 11, 16),
            BUNCH = box(4, 0, 4, 12, 16, 12), FRONDS = box(0, 0, 0, 16, 2, 16);

    private final Piece piece;

    public DoumPieceBlock(Piece piece, Properties properties) {
        super(properties);
        this.piece = piece;
        registerDefaultState(defaultBlockState().setValue(HangingFruit.STAGE, HangingFruit.RIPE).setValue(HangingFruit.PHASE, 0));
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
        // Called from Block's constructor, before piece is set: every DoumPieceBlock has the properties; only the bunch
        // uses them (the crown's blockstate file names one model for all).
        builder.add(HangingFruit.STAGE, HangingFruit.PHASE);
    }

    /** A bunch put by hand fruits on its own place's timetable (a palm's own bunches are given their palm's). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HangingFruit.PHASE, HangingFruit.phaseOf(context.getClickedPos().asLong()));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return piece == Piece.BUNCH;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (piece == Piece.BUNCH) {
            HangingFruit.randomTick(HangingFruit.Fruit.DOUM, RIPE_MIN, RIPE_MAX, state, level, pos, random);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return piece == Piece.BUNCH ? HangingFruit.pick(HangingFruit.Fruit.DOUM, RIPE_MIN, RIPE_MAX, state, level, pos, player)
                : InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        return piece == Piece.BUNCH ? HangingFruit.useItemOn(stack, state) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return piece == Piece.BUNCH && state.getValue(HangingFruit.STAGE) < HangingFruit.RIPE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        HangingFruit.bonemeal(level, pos, state);
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
