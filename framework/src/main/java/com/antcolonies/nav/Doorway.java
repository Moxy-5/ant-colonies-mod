package com.antcolonies.nav;

import net.minecraft.world.level.BlockGetter;

/**
 * A block that some insects pass through (a termite chimney block for termites), with an opening on top they come out
 * of and go in by: the mouth of a chimney spire. When a route leaves such a block over its top, or goes in over its
 * edge, the legs use the mouth instead of the block's plain top: the insect climbs up inside to the mouth, looks out,
 * climbs over the rim and down the outside (going in: the same the other way round). Whether an insect may pass is up
 * to the block's collision shape for that insect (see {@link LevelCellMap}).
 */
public interface Doorway {
    /** How high above the bottom of its block the rim of the mouth is (in blocks; more than 1 for a spire on top). */
    double mouthHeight();

    /** How far the mouth's rim is from the middle of the block, sideways (in blocks). */
    double mouthRadius();

    /**
     * Step costs that rule out coming out of a doorway's mouth (over the top of a doorway block) and going in by it
     * (over the edge into one): for a walk on which the insect should take another way, such as a termite that leaves
     * by a foraging hole instead of a chimney. Walking through a doorway sideways (an entrance at the foot of a mound)
     * is not affected.
     */
    static AntPathFinder.StepCosts avoidMouths(BlockGetter level) {
        return (from, to, kind) -> kind == StepKind.OUT_OF_MOUTH || kind == StepKind.INTO_MOUTH
                || kind == StepKind.OVER_TOP && level.getBlockState(from.cell()).getBlock() instanceof Doorway
                || kind == StepKind.OVER_EDGE && level.getBlockState(to.cell()).getBlock() instanceof Doorway
                ? Double.POSITIVE_INFINITY : 0.0;
    }
}
