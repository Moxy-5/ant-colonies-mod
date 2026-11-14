package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ripe fruit lying on the ground under a floodplain tree (floodplain trees steps 4b and 6b): fallen figs or doum nuts, 1
 * to {@link #MOST} in a little heap. Fresh, then old ({@link #OLD}: figs brown and rotting, nuts dull and weathered),
 * then gone, by random ticks ({@link HangingFruit}): figs about a day each (soft fruit rots fast), nuts about 10 (hard
 * nuts last). Nothing to stand on (a player and the insects walk through it, as grass). Broken: fresh figs give a fig
 * each, rotten ones nothing; nuts give a nut each at any age (the loot tables).
 */
public class FallenFruitBlock extends Block {
    public static final MapCodec<FallenFruitBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            HangingFruit.Fruit.CODEC.fieldOf("fruit").forGetter(b -> b.fruit), propertiesCodec()).apply(i, FallenFruitBlock::new));
    public static final int MOST = 3;
    public static final IntegerProperty FRUITS = IntegerProperty.create("fruits", 1, MOST);
    public static final BooleanProperty OLD = BooleanProperty.create("old");
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 3, 14);

    private final HangingFruit.Fruit fruit;

    public FallenFruitBlock(HangingFruit.Fruit fruit, Properties properties) {
        super(properties);
        this.fruit = fruit;
        registerDefaultState(defaultBlockState().setValue(FRUITS, 1).setValue(OLD, false));
    }

    public HangingFruit.Fruit fruit() {
        return fruit;
    }

    /** This heap with {@code count} more fruit (up to {@link #MOST}), fresh again on top. */
    public static BlockState more(BlockState heap, int count) {
        return heap.setValue(FRUITS, Math.max(1, Math.min(MOST, heap.getValue(FRUITS) + count))).setValue(OLD, false);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FRUITS, OLD);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextDouble() >= HangingFruit.chance(level, fruit.lyingDays)) {
            return;
        }
        if (state.getValue(OLD)) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(OLD, true), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Laid by hand: on top of a heap of its kind it adds one. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        return here.is(this) ? more(here, 1) : defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && state.getValue(FRUITS) < MOST
                || super.canBeReplaced(state, context);
    }
}
