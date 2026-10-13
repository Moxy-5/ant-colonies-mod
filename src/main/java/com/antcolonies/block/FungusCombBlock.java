package com.antcolonies.block;

import com.antcolonies.nav.Slope;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Fungus comb: the sponge-like mat termites build in their garden rooms out of the forage they bring in (one piece = one
 * layer, two pixels high, at most {@link #MAX_COMB_LAYERS}), on which their fungus grows. White fungus nodules sprout on
 * top of it ({@link #NODULES}, up to {@link #MAX_NODULES}); each one uses up a layer of the comb (see
 * {@code colony.FungusGarden}), so the comb shrinks and the termites build it up again from new forage. The nurses pick
 * the nodules to feed the queen and the nymphs. Unlike the seed and bread heaps it is solid: termites walk on it (a flat
 * {@link Slope} for their legs) and the eggs and nymphs lie on top of it.
 */
public class FungusCombBlock extends SeedPileBlock implements Slope {
    /** The comb is at most this many layers high (half a block), so a termite still fits on it in a room one block tall. */
    public static final int MAX_COMB_LAYERS = 4;
    public static final int MAX_NODULES = 3;
    public static final IntegerProperty NODULES = IntegerProperty.create("nodules", 0, MAX_NODULES);

    public FungusCombBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NODULES, 0));
    }

    @Override
    public int maxLayers() {
        return MAX_COMB_LAYERS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NODULES);
    }

    /** How high (in blocks) the top of the comb is over the floor: where an egg or a nymph lies on it. */
    public static double topOf(BlockState state) {
        return state.getValue(LAYERS) * 2.0 / 16.0;
    }

    @Override
    public int surfaceHeight(BlockState state, int u, int w) {
        return state.getValue(LAYERS) * 2;
    }
}
