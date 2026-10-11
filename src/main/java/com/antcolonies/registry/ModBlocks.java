package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.SeedPileBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AntColoniesMod.MODID);

    /** Dark packed soil that ants build their mounds from. Behaves like normal dirt. */
    public static final DeferredBlock<Block> BURROWED_DIRT =
            BLOCKS.registerSimpleBlock("burrowed_dirt", BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT));

    /** A heap of seeds in the food store: one layer per seed an ant delivers. */
    public static final DeferredBlock<SeedPileBlock> SEED_PILE = BLOCKS.registerBlock("seed_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.2F).sound(SoundType.SAND)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /** A heap of ant bread (milled seeds) in the nursery: one layer per bread. Same block as the seed pile, other look. */
    public static final DeferredBlock<SeedPileBlock> BREAD_PILE = BLOCKS.registerBlock("bread_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.2F).sound(SoundType.SAND)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));
}
