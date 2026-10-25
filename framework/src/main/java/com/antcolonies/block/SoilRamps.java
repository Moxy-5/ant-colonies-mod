package com.antcolonies.block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The soil ramps ({@link SoilRampBlock}), one for each soil the ants dig ({@code colonyframework:diggable}), and which
 * ramp a block of soil is carved into. Grass and its roots carve into a dirt ramp (a carved slope shows the soil under
 * the turf). A species mod with a soil of its own (the desert's deep soil) signs up its ramp with {@link #signUp}.
 */
public final class SoilRamps {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColonyFrameworkMod.MODID);

    public static final DeferredBlock<SoilRampBlock> DIRT = ramp("dirt_ramp", Blocks.DIRT);
    public static final DeferredBlock<SoilRampBlock> COARSE_DIRT = ramp("coarse_dirt_ramp", Blocks.COARSE_DIRT);
    public static final DeferredBlock<SoilRampBlock> ROOTED_DIRT = ramp("rooted_dirt_ramp", Blocks.ROOTED_DIRT);
    public static final DeferredBlock<SoilRampBlock> CLAY = ramp("clay_ramp", Blocks.CLAY);
    public static final DeferredBlock<SoilRampBlock> MUD = ramp("mud_ramp", Blocks.MUD);
    public static final DeferredBlock<SoilRampBlock> SAND = ramp("sand_ramp", Blocks.SAND);
    public static final DeferredBlock<SoilRampBlock> RED_SAND = ramp("red_sand_ramp", Blocks.RED_SAND);

    private static final Map<Block, Supplier<? extends Block>> BY_SOIL = new HashMap<>();

    static {
        signUp(Blocks.DIRT, DIRT);
        signUp(Blocks.GRASS_BLOCK, DIRT);
        signUp(Blocks.COARSE_DIRT, COARSE_DIRT);
        signUp(Blocks.ROOTED_DIRT, ROOTED_DIRT);
        signUp(Blocks.CLAY, CLAY);
        signUp(Blocks.MUD, MUD);
        signUp(Blocks.SAND, SAND);
        signUp(Blocks.RED_SAND, RED_SAND);
    }

    private SoilRamps() {
    }

    private static DeferredBlock<SoilRampBlock> ramp(String name, Block soil) {
        // As hard as its soil and sounding like it; it never falls (a carved sand slope holds, packed by feet).
        return BLOCKS.registerBlock(name, SoilRampBlock::new, BlockBehaviour.Properties.ofFullCopy(soil));
    }

    /** {@code soil} is carved into {@code ramp} (a block whose state has {@link SoilRampBlock#FACING}). */
    public static void signUp(Block soil, Supplier<? extends Block> ramp) {
        BY_SOIL.put(soil, ramp);
    }

    /** The ramp {@code soil} is carved into, rising toward {@code highSide}; null for a block with no ramp (it is dug out whole). */
    @Nullable
    public static BlockState carved(BlockState soil, Direction highSide) {
        Supplier<? extends Block> ramp = BY_SOIL.get(soil.getBlock());
        return ramp == null ? null : ramp.get().defaultBlockState().setValue(SoilRampBlock.FACING, highSide);
    }
}
