package com.antcolonies.nav;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block an insect walks on as a slope (a termite mound's ramp): it stands in the block's own cell, on its surface,
 * which rises in small steps the normal walking climbs. The route planner treats the cell as a floor; walls and
 * ceilings in it are not used. A walk onto or off it is only allowed where the surfaces meet without a step taller
 * than half a block ({@link AntPathFinder}). Species-neutral: any block can be a slope.
 */
public interface Slope {
    /** Pixels in a block (the heights are in sixteenths of a block). */
    int PIXELS = 16;
    /** The block is split into this many columns each way (2 pixels each) for its heights. */
    int COLUMNS = 8;

    /** How high (in pixels, 0 to 16) the surface is over column (u, w) of the 8 x 8 (u along east, w along south). */
    int surfaceHeight(BlockState state, int u, int w);
}
