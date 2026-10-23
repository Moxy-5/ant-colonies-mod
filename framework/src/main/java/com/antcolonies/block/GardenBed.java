package com.antcolonies.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A bed of the nest's food garden that eggs and young are laid on, where the young find their food (the termites' fungus
 * comb, {@link FungusCombBlock}). Step 9c: shared colony code asks about beds through this, never naming the block.
 */
public interface GardenBed {
    /** Whether this block is a garden bed. */
    static boolean isBed(BlockState state) {
        return state.getBlock() instanceof GardenBed;
    }

    /** How high (in blocks) the top of the bed is over the floor of its cell: where an egg or a young one lies on it. */
    double restingHeight(BlockState state);
}
