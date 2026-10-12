package com.antcolonies.nav;

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
}
