package com.antcolonies.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * What is left of a block of food eaten from inside, which the eaters keep eating along from (the termites' mud cast,
 * {@code MudCastBlock}): the shared food store treats the food over or beside one as in reach from the nest (the split,
 * step S3, so the food store names no termite block).
 */
public interface FoodCast {
    /** Whether this block is a cast left of eaten food. */
    static boolean isCast(BlockState state) {
        return state.getBlock() instanceof FoodCast;
    }
}
