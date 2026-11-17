package com.antcolonies.savanna.block;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * River sand (sand banks, 2026-11-16, {@code docs/plans/sand-banks.md}; art {@code tools/sand_art.py}, the user's tones):
 * the sand bars on the inside of the savanna rivers' sharp bends, where the crocodiles bask. A river drops its heavy load
 * first, so the sand changes with how far inland: red-ochre and gritty far inland, golden mid inland, pale and fine near
 * the coast. Each tone comes as dry sand, wet sand (the dark band at the waterline), rippled sand (the bed under shallow
 * water) and a crocodile slide (a polished groove a crocodile's belly drags down the bank, running along its
 * {@link Slide#AXIS}, wet or dry). Falls like Minecraft's sand.
 */
public final class RiverSand {
    private RiverSand() {
    }

    /** One tone's blocks. */
    public record Tone(String name, DeferredBlock<ColoredFallingBlock> dry, DeferredBlock<ColoredFallingBlock> wet,
                       DeferredBlock<ColoredFallingBlock> rippled, DeferredBlock<Slide> slide) {
        public List<DeferredBlock<? extends Block>> all() {
            return List.of(dry, wet, rippled, slide);
        }
    }

    /** Registers a tone's four blocks: {@code <name>_river_sand}, {@code wet_..}, {@code rippled_..}, {@code <name>_crocodile_slide}. */
    public static Tone register(DeferredRegister.Blocks blocks, String name, MapColor dryColour, MapColor wetColour, int dust, int wetDust) {
        return new Tone(name,
                blocks.registerBlock(name + "_river_sand", p -> new ColoredFallingBlock(new ColorRGBA(dust), p), props(dryColour, SoundType.SAND)),
                blocks.registerBlock("wet_" + name + "_river_sand", p -> new ColoredFallingBlock(new ColorRGBA(wetDust), p), props(wetColour, SoundType.SAND)),
                blocks.registerBlock("rippled_" + name + "_river_sand", p -> new ColoredFallingBlock(new ColorRGBA(wetDust), p), props(wetColour, SoundType.SAND)),
                blocks.registerBlock(name + "_crocodile_slide", Slide::new, props(dryColour, SoundType.SAND)));
    }

    private static BlockBehaviour.Properties props(MapColor colour, SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(0.5F).sound(sound);
    }

    /**
     * A crocodile slide: river sand with a smooth groove down it, running along {@link #AXIS} (down the bank toward the
     * water), wet near the water ({@link #WET}).
     */
    public static class Slide extends FallingBlock {
        public static final MapCodec<Slide> CODEC = simpleCodec(Slide::new);
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final BooleanProperty WET = BooleanProperty.create("wet");

        public Slide(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(WET, false));
        }

        @Override
        protected MapCodec<? extends FallingBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS, WET);
        }

        @Override
        @Nullable
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
        }

        @Override
        public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
            return state.getMapColor(level, pos).col;
        }
    }
}
