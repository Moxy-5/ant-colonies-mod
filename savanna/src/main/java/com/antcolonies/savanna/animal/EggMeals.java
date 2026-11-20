package com.antcolonies.savanna.animal;

import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.block.SnailEggsBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Eaten eggs (M9, animal life cycle step 3, 2026-11-19; the plan's food chain, fun first): tilapia and crabs eat
 * catfish eggs off the algae and nibble snail clutches, so few hatch. A grazer that finds eggs near takes them before
 * plain algae (a treat). A tilapia only reaches eggs under water (a snail's clutch just over the waterline is out of
 * its reach, as real apple snails lay them); a crab picks at any it can walk to. Each bite takes a few eggs with a puff
 * of egg bits; a clutch eaten to nothing is gone ({@link EggClutchBlock#eat}). Eggs are a snack: they feed a grazer
 * like any bite of algae, nothing more.
 */
public final class EggMeals {
    /** Snail eggs a bite takes (my pick: a clutch of 2 to 4 lasts a crab one pick or two). */
    static final int SNAIL_EGGS_A_BITE = 1;
    private static final org.joml.Vector3f PEARL = new org.joml.Vector3f(0.95F, 0.88F, 0.91F);

    private EggMeals() {
    }

    /** Whether a fish can eat eggs here: catfish eggs on a mat, or a snail clutch under water. */
    public static boolean forFish(BlockState state) {
        return AlgaeBlock.hasEggs(state) || state.getBlock() instanceof SnailEggsBlock && state.getValue(SnailEggsBlock.WATERLOGGED);
    }

    /** Whether a crab can eat eggs here: catfish eggs on a mat, or any snail clutch. */
    public static boolean forCrab(BlockState state) {
        return AlgaeBlock.hasEggs(state) || state.getBlock() instanceof SnailEggsBlock;
    }

    /** Whether this block is a snail clutch. */
    static boolean isSnailEggs(BlockState state) {
        return state.getBlock() instanceof SnailEggsBlock;
    }

    /**
     * One bite of the food here: a snail clutch loses an egg with a pearly puff; a mat (with catfish eggs or without)
     * is grazed ({@link AlgaeBlock#graze}). Whether there was anything to take.
     */
    public static boolean bite(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isSnailEggs(state)) {
            if (!(level instanceof ServerLevel server)) {
                return false;
            }
            server.sendParticles(new DustParticleOptions(PEARL, 0.6F), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                    5, 0.15, 0.1, 0.15, 0.01);
            return EggClutchBlock.eat(server, pos, SNAIL_EGGS_A_BITE);
        }
        return AlgaeBlock.graze(level, pos);
    }
}
