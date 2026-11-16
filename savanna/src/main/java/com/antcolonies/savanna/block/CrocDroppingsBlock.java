package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.antcolonies.savanna.registry.SavannaBlocks;

/**
 * Crocodile droppings (M9 river life, 2026-11-15; look C, the crumbly patch, picked by the user in
 * {@code python tools/model_viewer.py droppings}): a small low patch of chalky crumbs left where a crocodile basked on
 * land (real: a crocodile's stomach dissolves bones, so its dung dries chalk white with calcium). Looks only: nothing eats
 * it (generalized, fun first).
 *
 * <p>{@link #AGE}: 0 fresh (pale grey with darker streaks), 1 dried (chalk white), 2 crumbling; it ages on random ticks
 * and crumbles away in about 3 game days (my pick). Never in water; broken by hand at once, dropping nothing.
 */
public class CrocDroppingsBlock extends Block {
    public static final MapCodec<CrocDroppingsBlock> CODEC = simpleCodec(CrocDroppingsBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);
    /** The chance a random tick ages it a step: 3 steps in about 3 game days (a block gets ~17 random ticks a day). */
    static final float AGE_CHANCE = 1 / 17f;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 1.5, 12);

    public CrocDroppingsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(pos).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < AGE_CHANCE) {
            int age = state.getValue(AGE);
            level.setBlock(pos, age < 2 ? state.setValue(AGE, age + 1) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Leaves fresh droppings at {@code pos} (or the cell over it, for a crocodile lying on a slab or path), if it is open
     * dry ground. Whether it did.
     */
    public static boolean drop(ServerLevel level, BlockPos pos) {
        BlockState fresh = SavannaBlocks.CROC_DROPPINGS.get().defaultBlockState();
        for (BlockPos at : new BlockPos[] {pos, pos.above()}) {
            if (level.isLoaded(at) && level.getBlockState(at).isAir() && fresh.canSurvive(level, at)) {
                level.setBlock(at, fresh, Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }
}
