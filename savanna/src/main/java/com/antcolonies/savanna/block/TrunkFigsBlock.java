package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Figs growing straight on a sycamore fig's trunk (floodplain trees step 3): a sycamore fig bears its figs in clusters on
 * the bark of the trunk and the thick limbs, not out among the leaves (cauliflory), as in the model viewer. A handful of
 * figs in the air beside the bark, {@link #FACING} the way they look out; they drop when the wood behind them is gone.
 * They ripen in stages like a bunch, bare bumps, green, yellow, ripe red, and drop to the ground ({@link HangingFruit},
 * step 4b), picked by hand or moved on by bone meal.
 */
public class TrunkFigsBlock extends HorizontalDirectionalBlock implements BonemealableBlock {
    public static final MapCodec<TrunkFigsBlock> CODEC = simpleCodec(TrunkFigsBlock::new);
    private static final VoxelShape NORTH = box(2, 2, 9, 14, 14, 16);
    private static final VoxelShape SOUTH = box(2, 2, 0, 14, 14, 7);
    private static final VoxelShape EAST = box(0, 2, 2, 7, 14, 14);
    private static final VoxelShape WEST = box(9, 2, 2, 16, 14, 14);
    /** A ripe cluster on the bark gives 1 to 3 figs (my pick). */
    private static final int RIPE_MIN = 1, RIPE_MAX = 3;

    public TrunkFigsBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HangingFruit.STAGE, HangingFruit.RIPE)
                .setValue(HangingFruit.PHASE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HangingFruit.STAGE, HangingFruit.PHASE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    /** The fig wood the figs grow on: a log, wood or root step of a sycamore fig. */
    public static boolean holdsOn(BlockState behind) {
        return behind.getBlock() instanceof FigWoodBlock || behind.getBlock() instanceof FigRootBlock;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return holdsOn(level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** A player sets them on the side of a fig trunk they click. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face)
                .setValue(HangingFruit.PHASE, HangingFruit.phaseOf(context.getClickedPos().asLong()));
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        HangingFruit.randomTick(HangingFruit.Fruit.FIG, RIPE_MIN, RIPE_MAX, state, level, pos, random);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return HangingFruit.pick(HangingFruit.Fruit.FIG, RIPE_MIN, RIPE_MAX, state, level, pos, player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        return HangingFruit.useItemOn(stack, state);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(HangingFruit.STAGE) < HangingFruit.RIPE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        HangingFruit.bonemeal(level, pos, state);
    }
}
