package com.antcolonies.nav;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block whose sides an insect climbs although they are not full faces: a square column, centred in the block, that
 * may narrow with height (a termite spire piece, the lower part of a chimney mouth). The route planner sees its sides
 * as walls; the legs hug the real column, pressed against its narrower sides and turning round its narrower edges
 * ({@link AntNavigator}). Its top is not something to stand on. Species-neutral: any block can be a climb shape.
 */
public interface ClimbShape {
    /** Whether its sides can be climbed at all (a spire's needle tip cannot). */
    boolean isClimbable(BlockState state);

    /** How wide (in blocks, more than 0 and up to 1) the column is at height {@code y} (0 to 1) within the block. */
    double widthAt(BlockState state, double y);
}
