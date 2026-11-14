package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A bunch of sycamore figs hanging under a fig's limbs (floodplain trees steps 3 and 4b): it ripens in stages, a bare
 * spur, green, yellow, ripe red, and drops its figs to the ground ({@link HangingFruit}); picked by hand like sweet
 * berries, moved on a stage by bone meal. It hangs from the block above and drops when that is gone.
 */
public class FigBunchBlock extends HangingPodsBlock implements BonemealableBlock {
    public static final MapCodec<FigBunchBlock> CODEC = simpleCodec(FigBunchBlock::new);
    /** A ripe bunch gives 2 to 4 figs, picked or fallen (my pick). */
    private static final int RIPE_MIN = 2, RIPE_MAX = 4;

    public FigBunchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HangingFruit.STAGE, HangingFruit.RIPE).setValue(HangingFruit.PHASE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HangingFruit.STAGE, HangingFruit.PHASE);
    }

    /** A bunch hung by hand fruits on its own place's timetable (a tree's own bunches are given their tree's). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HangingFruit.PHASE, HangingFruit.phaseOf(context.getClickedPos().asLong()));
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
