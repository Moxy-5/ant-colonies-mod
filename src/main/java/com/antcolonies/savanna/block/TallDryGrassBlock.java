package com.antcolonies.block;

import static com.antcolonies.block.DryGrassBlock.GROWTH;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Tall Dry Grass, two blocks high, with seed heads on top: savanna grass grown to its full height (real savanna grasses
 * stand one to two metres tall). It dries like {@link DryGrassBlock} (green, drying, dry), both halves together, and
 * termites only take it dry; it holds twice the food of a short tuft. Bone meal on a short tuft grows one.
 */
public class TallDryGrassBlock extends DoublePlantBlock {
    public static final MapCodec<TallDryGrassBlock> CODEC = simpleCodec(TallDryGrassBlock::new);

    public TallDryGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(GROWTH, DryGrassBlock.Growth.DRY));
    }

    @Override
    public MapCodec<? extends DoublePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GROWTH);
    }

    // The top half is as dry as the bottom half it was placed with (vanilla would place a default top half).
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos above = pos.above();
        level.setBlock(above, copyWaterloggedFrom(level, above, state.setValue(HALF, DoubleBlockHalf.UPPER)), 3);
    }

    // Only the bottom half ticks, and dries both halves.
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(GROWTH) != DryGrassBlock.Growth.DRY;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(DryGrassBlock.DRY_CHANCE) != 0) {
            return;
        }
        DryGrassBlock.Growth growth = state.getValue(GROWTH).next();
        level.setBlock(pos, state.setValue(GROWTH, growth), 3);
        BlockState top = level.getBlockState(pos.above());
        if (top.is(this)) {
            level.setBlock(pos.above(), top.setValue(GROWTH, growth), 3);
        }
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return DryGrassBlock.flammability(state);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return DryGrassBlock.fireSpread(state);
    }
}
