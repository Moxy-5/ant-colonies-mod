package com.antcolonies.nav;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Ground insects walk on only when they must (savanna mud, 2026-11-21, {@code docs/plans/savanna-mud.md}, step 2): a block
 * whose surface costs a route extra to hold, as wet mud does. Real ants and termites keep off wet mud and water and go
 * round it; an ant on it, or with no other way, still crosses, so a cost (never a wall) keeps none stranded. Species-
 * neutral: the block says what it costs ({@link LevelCellMap#footCost}).
 */
public interface SoftGround {
    /** The extra cost of one foothold on this block (0: plain ground). One block of walking costs about 1. */
    double insectCost(BlockState state);
}
