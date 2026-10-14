package com.antcolonies.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A food plant that shows how much of it has been eaten (a fallen branch, dead wood): as foragers cut pieces off it,
 * the colony's food store asks it how it looks now. Species-neutral: any colony cutting at it uses this.
 */
public interface EatenInStages {
    /** This block once {@code cut} of its {@code pieces} have been eaten (the last piece breaks it instead). */
    BlockState eatenTo(BlockState state, int cut, int pieces);

    /**
     * How many of its {@code pieces} this block already looks eaten by, so a colony that never cut at it before (or
     * forgot) does not count a half-eaten block as whole.
     */
    int eatenAlready(BlockState state, int pieces);
}
