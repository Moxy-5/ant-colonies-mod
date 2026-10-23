package com.antcolonies.savanna.block;

import com.antcolonies.block.EatenInStages;
import com.antcolonies.block.FoodCast;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A mud cast (the user's choice, 2026-10-14): what is left of a block of dead wood the termites have eaten away. Real
 * termites plaster mud over a log as they eat it from inside, and an old termite-eaten log ends up as a log-shaped crust
 * of mud full of tunnels. In the game it is a termite tunnel block ({@link TermiteTunnelBlock}, the same channel and the
 * same look) standing where the wood was: its channel opens toward the feeding gallery or tunnel it was eaten from and
 * joins the casts beside it, so the termites eat on along the log and up the stump from inside, under cover.
 *
 * <p>It is not part of the mound: once no wood is left on the casts joined to it (the whole log is eaten), nobody needs
 * it and it slowly crumbles away, a little at a time.
 */
public class MudCastBlock extends TermiteTunnelBlock implements FoodCast {
    /** On each random tick a cast nobody needs crumbles away with a chance of 1 in this (my pick: a few in-game days). */
    private static final int CRUMBLE_CHANCE = 12;
    /** At most this many joined casts are looked at for wood (a fallen tree is about 8 blocks). */
    private static final int MAX_GROUP = 64;

    public MudCastBlock(Properties properties) {
        super(properties);
    }

    /** Whether this block is a mud cast. */
    public static boolean isCast(BlockState state) {
        return state.getBlock() instanceof MudCastBlock;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(CRUMBLE_CHANCE) == 0 && !isNeeded(level, pos)) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.removeBlock(pos, false);
            return;
        }
        if (state.getValue(MoundSoil.AGE) < MoundSoil.OLDEST) {
            super.randomTick(state, level, pos, random);
        }
    }

    /** Whether some cast joined to this one (side by side) still touches wood the insects are eating. */
    private static boolean isNeeded(LevelReader level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos cast = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = cast.relative(side);
                if (!level.hasChunkAt(next)) {
                    return true;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof EatenInStages) {
                    return true;
                }
                if (isCast(state) && seen.size() < MAX_GROUP && seen.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
        return false;
    }
}
