package com.antcolonies.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block a species packs its holes with that goes in in a state of its own (the termites' mound soil goes in finished
 * and fresh, so it sets and hardens like the mound); see the species file's {@code nest.plug_block}. The split, step
 * S3: the shared species numbers name no termite block.
 */
public interface PlugBlock {
    /** The block as it is packed into a hole. */
    BlockState packedState();
}
