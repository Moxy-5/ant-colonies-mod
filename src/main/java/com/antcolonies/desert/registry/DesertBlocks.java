package com.antcolonies.desert.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.desert.block.GramaGrassBlock;
import com.antcolonies.block.SeedPileBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The harvester ant's blocks: its soil, its seed, bread and loose soil heaps, and the grama grass (the split, step S4). */
public class DesertBlocks {
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
     * Loose soil near the top of the nest: the night soil (pellets dug while the surface was shut), waiting to be carried
     * up onto the hill in the morning. One layer per pellet. Same block as the seed pile (nothing collides with it), other look.
     */
    public static final DeferredBlock<SeedPileBlock> LOOSE_SOIL = BLOCKS.registerBlock("loose_soil", SeedPileBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.2F).sound(SoundType.GRAVEL)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY));

    /** Blue grama, the shortgrass prairie's seed grass, in meadows on the plains (see GramaGrassBlock). */
    public static final DeferredBlock<GramaGrassBlock> BLUE_GRAMA = BLOCKS.register("blue_grama",
            () -> new GramaGrassBlock(grassProperties().mapColor(MapColor.PLANT), false, () -> DesertItems.BLUE_GRAMA_SEEDS.get()));

    /** Black grama, the desert grassland's seed grass, in bunches in the badlands, on sand too (see GramaGrassBlock). */
    public static final DeferredBlock<GramaGrassBlock> BLACK_GRAMA = BLOCKS.register("black_grama",
            () -> new GramaGrassBlock(grassProperties().mapColor(MapColor.COLOR_GRAY), true, () -> DesertItems.BLACK_GRAMA_SEEDS.get()));


    private static BlockBehaviour.Properties grassProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).replaceable().noCollission().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava().pushReaction(PushReaction.DESTROY).randomTicks();
    }
}
