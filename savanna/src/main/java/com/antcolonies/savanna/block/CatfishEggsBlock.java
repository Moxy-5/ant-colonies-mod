package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.savanna.registry.SavannaBlockEntities;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A river catfish's eggs on an algae mat (M9, animal life cycle step 3, 2026-11-19; the look the user confirmed in the
 * life viewer, {@code tools/breeding_art.py}): after a rainy night's spawning in the shallows the female scatters her
 * sticky pale-yellow eggs over the algae mats, a few to a mat ({@code CatfishScatterEggsGoal}). This block is the mat
 * with its eggs: it keeps the mat's {@link AlgaeBlock#GROWTH} and {@link AlgaeBlock#STRANDS} (drawn as the same mat
 * with the eggs on it) and turns back into that mat when the eggs hatch, are all eaten, or are long gone. It always
 * holds water, as the mat does.
 *
 * <p>The kit's clutch ({@link EggClutchBlock}): after the catfish's hatching days, with a watcher near and room for
 * catfish, each egg left hatches into a tiny dark catfish that hides in the shallows. Tilapia and crabs eat the eggs off
 * the mat (a strand bite with an egg puff, {@link AlgaeBlock#graze}), so few hatch (the plan's food chain). No care.
 */
public class CatfishEggsBlock extends EggClutchBlock implements LiquidBlockContainer {
    public static final MapCodec<CatfishEggsBlock> CODEC = simpleCodec(CatfishEggsBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public CatfishEggsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AlgaeBlock.GROWTH, AlgaeBlock.THICK).setValue(AlgaeBlock.STRANDS, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AlgaeBlock.GROWTH, AlgaeBlock.STRANDS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EggClutchBlockEntity(SavannaBlockEntities.EGG_CLUTCH.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return canSurvive(state, level, pos) ? state : Blocks.WATER.defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /** The mat under the eggs, as an algae block (its growth and strands). */
    public static BlockState mat(BlockState eggs) {
        return SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, eggs.getValue(AlgaeBlock.GROWTH))
                .setValue(AlgaeBlock.STRANDS, eggs.getValue(AlgaeBlock.STRANDS));
    }

    /** Hatched, eaten or long gone: the mat is left as it was, without its eggs. */
    @Override
    protected void toShells(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, mat(state), Block.UPDATE_ALL);
    }

    /**
     * Lays {@code eggs} of this kind on the algae mat here (a thin, thick or lush one; never a bare one). False if there
     * is no such mat or it already has eggs.
     */
    public static boolean lay(ServerLevel level, BlockPos pos, EntityType<?> kind, int eggs) {
        BlockState there = level.getBlockState(pos);
        if (!there.is(SavannaBlocks.ALGAE.get()) || there.getValue(AlgaeBlock.GROWTH) == AlgaeBlock.BARE) {
            return false;
        }
        level.setBlock(pos, SavannaBlocks.CATFISH_EGGS.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, there.getValue(AlgaeBlock.GROWTH))
                .setValue(AlgaeBlock.STRANDS, there.getValue(AlgaeBlock.STRANDS)), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch) {
            clutch.lay(kind, eggs, level.getGameTime());
            return true;
        }
        return false;
    }
}
