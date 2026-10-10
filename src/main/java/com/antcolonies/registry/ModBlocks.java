package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AntColoniesMod.MODID);

    /** Dark packed soil that ants build their mounds from. Behaves like normal dirt. */
    public static final DeferredBlock<Block> BURROWED_DIRT =
            BLOCKS.registerSimpleBlock("burrowed_dirt", BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));
}
