package com.antcolonies.nav;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block with a narrow channel through it that small insects crawl along (a termite tunnel, a hollow spire piece, a
 * chimney mouth's tube): the cell is solid, but an insect that fits lies in the channel's middle and crawls out of the
 * block by the sides the channel opens out of. Channels in blocks side by side join where both open toward each other.
 * The route planner sees such a cell as a channel foothold ({@link Foothold#channel}), and the legs move the body along
 * the channel's middle exactly ({@link AntNavigator}). Species-neutral: who fits is up to the block.
 */
public interface Channel {
    /** Where the channel's floor is, above the bottom of the block (in blocks): an insect in it lies with its feet there. */
    double FLOOR = 5.0 / 16.0;

    /** Whether this insect fits through the channel (a termite worker does; a soldier, an ant or a player does not). */
    boolean fits(@Nullable Entity insect);

    /**
     * Whether this block's channel can ever open out of {@code side} (a tunnel: any side; a spire piece: up and down).
     * A channel block beside it opens toward it only where it can.
     */
    boolean canJoin(BlockState state, Direction side);

    /** The sides the channel opens out of now, as a mask (bit {@code Direction.get3DDataValue()}); 0 = none. */
    int openSides(BlockGetter level, BlockPos pos, BlockState state);

    /** Whether the block at {@code pos} is a channel block that can open toward {@code side}. */
    static boolean joinsToward(BlockGetter level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof Channel channel && channel.canJoin(state, side);
    }

    /** Whether a mask of sides holds {@code side}. */
    static boolean has(int sides, Direction side) {
        return (sides & (1 << side.get3DDataValue())) != 0;
    }
}
