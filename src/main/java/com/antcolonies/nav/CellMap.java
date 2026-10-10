package com.antcolonies.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * What the route planner needs to know about the world, one block-sized "cell" at a time.
 * Kept separate from the real game world so the planner can be tested on its own.
 */
public interface CellMap {
    /** True if a tiny insect can be inside this cell: no solid block, no water, lava, fire or web. Unloaded = false. */
    boolean isOpen(BlockPos pos);

    /** True if the block at {@code pos} has a solid surface on its {@code face} side (something to stand or cling on). */
    boolean isSolidFace(BlockPos pos, Direction face);
}
