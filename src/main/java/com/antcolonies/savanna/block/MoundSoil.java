package com.antcolonies.savanna.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * How finished termite mound soil dries and hardens with age, shared by every block made of it: the plain mound block
 * ({@link TermiteMoundBlock}) and the cathedral pieces (spires, the chimney mouth, ramps). Fresh soil is dark and damp
 * and breaks like dirt; it sets (red-orange), dries (tan, needs a pickaxe) and gets old (pale, sun-baked, like stone).
 */
public final class MoundSoil {
    public static final int OLDEST = 3;
    /** How dry and hard the soil is: 0 fresh, 1 setting, 2 dry, 3 old. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, OLDEST);

    /** How hard the soil is at each age (dirt is 0.5, clay 0.6, stone 1.5). */
    private static final float[] HARDNESS = {0.6F, 1.0F, 1.5F, 2.5F};
    /**
     * On each random tick the soil grows one age older with a chance of 1 in this (a block gets a random tick about
     * once a minute at the normal tick speed): setting after about 20 minutes, dry after about 50 more, old after about
     * 2.5 hours more.
     */
    private static final int[] AGE_CHANCE = {20, 45, 130};

    private MoundSoil() {
    }

    public static float hardness(int age) {
        return HARDNESS[age];
    }

    /** Dry and old soil breaks like stone (needs a pickaxe), fresh and setting soil like dirt (a shovel helps). */
    public static boolean isStoneHard(int age) {
        return age >= 2;
    }

    /** One random tick of drying for a block with the {@link #AGE} property. */
    public static void age(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < OLDEST && random.nextInt(AGE_CHANCE[age]) == 0) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    /** Breaking speed worked out as for dirt or stone (by age), with the given hardness. */
    public static float destroyProgress(boolean stoneHard, float hardness, Player player, BlockPos pos) {
        BlockState feel = stoneHard ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        int divisor = player.hasCorrectToolForDrops(feel) ? 30 : 100;
        return player.getDigSpeed(feel, pos) / hardness / divisor;
    }

    public static SoundType sound(int age) {
        return age == 0 ? SoundType.MUD : age == 1 ? SoundType.PACKED_MUD : SoundType.MUD_BRICKS;
    }
}
