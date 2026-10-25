package com.antcolonies.savanna.block;

import com.antcolonies.block.SeedPileBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The heap of termite leftovers beside a Matabele nest's entrance (M8 step 1e): head capsules, legs and wings of the
 * eaten termites, and the whole dead ones that rotted in the pile, carried out of the nest (real Megaponera nests are
 * known by such middens of termite remains at the entrance). A layer for each load carried out. In the open it weathers
 * away, about a layer every 2 days (my pick): a block gets a random tick about every 1,365 ticks, so 1 in 35 of them.
 */
public class TermiteRemainsBlock extends SeedPileBlock {
    /** One random tick in this many takes a layer away (about one every 48,000 ticks, 2 days). */
    private static final int WEATHER_ONE_IN = 35;

    public TermiteRemainsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(WEATHER_ONE_IN) == 0) {
            level.setBlock(pos, oneLayerLess(state), 3);
        }
    }
}
