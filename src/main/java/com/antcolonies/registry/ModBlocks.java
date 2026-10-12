package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.block.TermiteHoleBlock;
import com.antcolonies.block.TermiteMoundBlock;

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

    /** Darker soil under the topsoil, laid by world generation (see DeepSoilFeature). Diggable by ants. */
    public static final DeferredBlock<Block> DEEP_SOIL = BLOCKS.registerSimpleBlock("deep_soil",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).mapColor(MapColor.COLOR_BROWN).strength(0.8F));

    /** A heap of seeds in the food store: one layer per seed an ant delivers. */
    public static final DeferredBlock<SeedPileBlock> SEED_PILE = BLOCKS.registerBlock("seed_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.2F).sound(SoundType.SAND)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /** A heap of ant bread (milled seeds) in the nursery: one layer per bread. Same block as the seed pile, other look. */
    public static final DeferredBlock<SeedPileBlock> BREAD_PILE = BLOCKS.registerBlock("bread_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.2F).sound(SoundType.SAND)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /**
     * A heap of forage (pieces of dead grass, leaves and twigs) in the termites' food store: one layer per piece a
     * termite brings in. Same block as the seed pile, other look.
     */
    public static final DeferredBlock<SeedPileBlock> FORAGE_PILE = BLOCKS.registerBlock("forage_pile", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.2F).sound(SoundType.GRASS)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /** Termite mound soil: built up in layers, then dries and hardens with age (see TermiteMoundBlock). */
    public static final DeferredBlock<TermiteMoundBlock> TERMITE_MOUND = BLOCKS.registerBlock("termite_mound", TermiteMoundBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F, 3.0F).sound(SoundType.MUD)
                    .randomTicks());

    /** The termites' chimney crown: solid for everyone but termites (see TermiteHoleBlock). */
    public static final DeferredBlock<TermiteHoleBlock> TERMITE_HOLE = BLOCKS.registerBlock("termite_hole", TermiteHoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(1.5F, 3.0F).sound(SoundType.MUD_BRICKS)
                    .noOcclusion().isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
}
