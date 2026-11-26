package com.antcolonies.nav;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A block that some insects pass through (a termite chimney block for termites), with an opening on top they come out
 * of and go in by: the mouth of a chimney spire. When a route leaves such a block over its top, or goes in over its
 * edge, the legs use the mouth instead of the block's plain top: the insect climbs up inside to the mouth, looks out,
 * climbs over the rim and down the outside (going in: the same the other way round). Whether a block is a doorway
 * for an insect is judged in one place, {@link #at}.
 */
public interface Doorway {
    /** How high above the bottom of its block the rim of the mouth is (in blocks; more than 1 for a spire on top). */
    double mouthHeight();

    /** How far the mouth's rim is from the middle of the block, sideways (in blocks). */
    double mouthRadius();

    /**
     * The one test of "is the block in this cell a doorway for this insect" (review R2-42: there were four, which
     * agreed for the termites' blocks only by luck; docs/BUGS.md 6e was two of them disagreeing for a tick). A doorway
     * with a channel (a chimney mouth's tube) is one for the insects that fit its channel; one without (a block walked
     * through whole) for the insects its collision shape lets through. Null if it is none for this insect. The route
     * planner, the legs and {@link #avoidMouths} all ask this (through {@link CellMap#doorway}, which also checks the
     * land is loaded).
     */
    @Nullable
    static Doorway at(BlockGetter level, BlockPos cell, @Nullable Entity insect) {
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof Doorway door)) {
            return null;
        }
        if (door instanceof Channel channel) {
            return channel.fits(insect) ? door : null;
        }
        CollisionContext context = insect == null ? CollisionContext.empty() : CollisionContext.of(insect);
        return state.getCollisionShape(level, cell, context).isEmpty() ? door : null;
    }

    /**
     * Step costs that rule out coming out of a doorway's mouth (over the top of a doorway block) and going in by it
     * (over the edge into one): for a walk on which the insect should take another way, such as a termite that leaves
     * by a foraging hole instead of a chimney. Walking through a doorway sideways (an entrance at the foot of a mound)
     * is not affected.
     */
    static AntPathFinder.StepCosts avoidMouths(BlockGetter level, @Nullable Entity insect) {
        return (from, to, kind) -> kind == StepKind.OUT_OF_MOUTH || kind == StepKind.INTO_MOUTH
                || kind == StepKind.OVER_TOP && at(level, from.cell(), insect) != null
                || kind == StepKind.OVER_EDGE && at(level, to.cell(), insect) != null
                ? Double.POSITIVE_INFINITY : 0.0;
    }
}
