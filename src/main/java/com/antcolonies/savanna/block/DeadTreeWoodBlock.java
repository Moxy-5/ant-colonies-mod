package com.antcolonies.savanna.block;

import com.antcolonies.block.EatenInStages;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The wood of a dead giant tree (M7.5 step 6d, user, 2026-10-19): bleached silver logs and wood, standing or lying.
 * Termites eat it like the old Dead Wood ({@link DeadWoodBlock}): from below or from a gallery beside it, under mud,
 * in the same 4 {@link DeadWoodBlock#EATEN} stages, mud spreading over the silver and the core hollowed out, the last
 * piece leaving a mud cast. Unlike the old Dead Wood it never burns away in a fire (it chars, {@code tree/TreeFire}),
 * so its fire numbers stay the tree's own ({@code TreeBlocks.setFlammable}).
 */
public class DeadTreeWoodBlock extends RotatedPillarBlock implements EatenInStages {
    public static final MapCodec<DeadTreeWoodBlock> CODEC = simpleCodec(DeadTreeWoodBlock::new);

    public DeadTreeWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DeadWoodBlock.EATEN, 0));
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DeadWoodBlock.EATEN);
    }

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return stage(state, cut, pieces);
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return already(state, pieces);
    }

    /** The state eaten {@code cut} of {@code pieces} (shared by the blocks with {@link DeadWoodBlock#EATEN}). */
    static BlockState stage(BlockState state, int cut, int pieces) {
        return state.setValue(DeadWoodBlock.EATEN, Math.min(DeadWoodBlock.MAX_EATEN, cut * (DeadWoodBlock.MAX_EATEN + 1) / Math.max(1, pieces)));
    }

    /** How many of {@code pieces} a block with {@link DeadWoodBlock#EATEN} looks eaten by. */
    static int already(BlockState state, int pieces) {
        return state.getValue(DeadWoodBlock.EATEN) * pieces / (DeadWoodBlock.MAX_EATEN + 1);
    }

    /** Whether termites have started on this block (a dead tree's or the old Dead Wood): the tree book leaves it alone. */
    public static boolean isEaten(BlockState state) {
        return state.hasProperty(DeadWoodBlock.EATEN) && state.getValue(DeadWoodBlock.EATEN) > 0;
    }
}
