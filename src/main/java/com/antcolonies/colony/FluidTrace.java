package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * Follows water or lava back to where it comes from. Running fluid is fed by fluid that is higher up or
 * "fuller" (a flowing block holds less than the block it flows from, and falling fluid comes from the block
 * above it), so from any piece of a stream the way upstream always leads to its source blocks. Nothing else is
 * followed: a pool that only touches the stream, or the far side of a lake, is not part of what feeds it.
 *
 * <p>Nothing here is specific to ants.
 */
final class FluidTrace {
    /** The trace stays within this many blocks sideways of the centre, 40 above it and 12 below it. */
    private static final int RANGE = 24;
    private static final int RANGE_UP = 40;
    private static final int RANGE_DOWN = 12;
    private static final int LIMIT = 800;

    /** The source blocks that feed the traced fluid, nearest to where the trace started first. */
    final List<BlockPos> sources = new ArrayList<>();
    /** The running (non-source) pieces of the stream, nearest to where the trace started first. */
    final List<BlockPos> stream = new ArrayList<>();
    /** Every block of fluid the trace went through. */
    final Set<BlockPos> visited = new HashSet<>();
    /** True when the trace stopped early (too big or too far), so there may be more upstream than it found. */
    boolean truncated;

    private FluidTrace() {
    }

    /** Traces from the given pieces of fluid up to their sources. {@code center} only limits how far the search goes. */
    static FluidTrace upstream(Level level, Collection<BlockPos> starts, BlockPos center) {
        FluidTrace trace = new FluidTrace();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : starts) {
            if (!level.getFluidState(start).isEmpty() && trace.visited.add(start.immutable())) {
                queue.add(start.immutable());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            FluidState here = level.getFluidState(pos);
            if (here.isSource()) {
                trace.sources.add(pos);
                continue; // a source is where it starts: nothing feeds it
            }
            trace.stream.add(pos);
            if (trace.visited.size() >= LIMIT) {
                trace.truncated = true;
                continue;
            }
            // Falling fluid is fed from the block above.
            BlockPos above = pos.above();
            if (feeds(level, above, here, false) && inRange(above, center) && trace.visited.add(above)) {
                queue.add(above);
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                if (feeds(level, next, here, true)) {
                    if (!inRange(next, center)) {
                        trace.truncated = true;
                    } else if (trace.visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return trace;
    }

    /** True if the fluid at {@code from} flows into {@code to}'s fluid ({@code here}). */
    private static boolean feeds(Level level, BlockPos from, FluidState here, boolean sideways) {
        if (!level.isLoaded(from)) {
            return false;
        }
        FluidState other = level.getFluidState(from);
        if (other.isEmpty() || !other.getType().isSame(here.getType())) {
            return false;
        }
        return !sideways || other.getAmount() > here.getAmount();
    }

    private static boolean inRange(BlockPos pos, BlockPos center) {
        return Math.abs(pos.getX() - center.getX()) <= RANGE && Math.abs(pos.getZ() - center.getZ()) <= RANGE
                && pos.getY() - center.getY() <= RANGE_UP && center.getY() - pos.getY() <= RANGE_DOWN;
    }

    /** True if the trace went through any of these blocks. */
    boolean touchedAny(Collection<BlockPos> cells) {
        for (BlockPos cell : cells) {
            if (visited.contains(cell)) {
                return true;
            }
        }
        return false;
    }
}
