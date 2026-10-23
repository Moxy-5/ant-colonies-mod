package com.antcolonies.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block of thin plates, one on any of its six faces, each walling off the cell on that side (the termites' mud crust,
 * {@link MudCrustBlock}). Step 9c: shared colony code asks about walls through this, never naming the species' block;
 * which block a colony plasters with comes from its {@code ColonyHooks.CRUST} hook.
 */
public interface FacedWall {
    /** Whether this block is a wall of plates. */
    static boolean isWall(BlockState state) {
        return state.getBlock() instanceof FacedWall;
    }

    /** Whether this block is a wall of plates with a plate on that face. */
    static boolean hasFace(BlockState state, Direction face) {
        return isWall(state) && state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face));
    }

    /** The wall with that face's plate taken off (it may then have none left, {@link #hasNoFace}). */
    static BlockState withoutFace(BlockState state, Direction face) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(face), false);
    }

    /** Whether a wall of plates has no plate left. */
    static boolean hasNoFace(BlockState state) {
        return PipeBlock.PROPERTY_BY_DIRECTION.values().stream().noneMatch(state::getValue);
    }

    /** The wall with one more plate (a fresh wall of this block if it was not one yet). */
    BlockState withFace(BlockState state, Direction face);
}
