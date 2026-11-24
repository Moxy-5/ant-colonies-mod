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
 *
 * <p>Bullfrog spawn too (the river food web's step 2, 2026-11-23): a frog's floating egg mat always lies in water, so
 * tilapia and crabs both reach it, one egg a bite with a puff of dark egg bits; its guarding father lunges at them
 * ({@code BullfrogEntity#raidsEggs}), so a guarded mat keeps most of its eggs and an orphan one may lose them all.
 */
public final class EggMeals {
    /** Snail eggs a bite takes (my pick: a clutch of 2 to 4 lasts a crab one pick or two). */
    static final int SNAIL_EGGS_A_BITE = 1;
    private static final org.joml.Vector3f PEARL = new org.joml.Vector3f(0.95F, 0.88F, 0.91F);
    /** Frog eggs: black dots in clear jelly. */
    private static final org.joml.Vector3f SPAWN = new org.joml.Vector3f(0.16F, 0.15F, 0.14F);

    private EggMeals() {
    }

    /** Whether a fish can eat eggs here: catfish eggs on a mat, or a snail clutch under water. */
    public static boolean forFish(BlockState state) {
        return AlgaeBlock.hasEggs(state) || state.getBlock() instanceof SnailEggsBlock && state.getValue(SnailEggsBlock.WATERLOGGED)
                || isFrogEggs(state);
    }

    /** Whether a crab can eat eggs here: catfish eggs on a mat, or any snail clutch. */
    public static boolean forCrab(BlockState state) {
        return AlgaeBlock.hasEggs(state) || state.getBlock() instanceof SnailEggsBlock || isFrogEggs(state);
    }

    /** Whether this block is a bullfrog's egg mat. */
    static boolean isFrogEggs(BlockState state) {
        return state.getBlock() instanceof com.antcolonies.savanna.block.FrogEggsBlock;
    }

    /** Whether this block is a clutch of eggs (a snail's or a frog's: picked at as meat, not grazed). */
    static boolean isSnailEggs(BlockState state) {
        return state.getBlock() instanceof SnailEggsBlock || isFrogEggs(state);
    }

    /**
     * One bite of the food here: a snail clutch loses an egg with a pearly puff; a mat (with catfish eggs or without)
     * is grazed ({@link AlgaeBlock#graze}). Whether there was anything to take.
     */
    public static boolean bite(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.antcolonies.savanna.block.HippoGrassBlock) {
            return level instanceof ServerLevel server && com.antcolonies.savanna.block.HippoGrassBlock.bitten(server, pos);   // the food web's step 3
        }
        if (isSnailEggs(state)) {
            if (!(level instanceof ServerLevel server)) {
                return false;
            }
            boolean frog = isFrogEggs(state);
            server.sendParticles(new DustParticleOptions(frog ? SPAWN : PEARL, 0.6F), pos.getX() + 0.5, pos.getY() + (frog ? 0.85 : 0.3), pos.getZ() + 0.5,
                    5, 0.15, 0.1, 0.15, 0.01);
            return EggClutchBlock.eat(server, pos, SNAIL_EGGS_A_BITE);
        }
        return AlgaeBlock.graze(level, pos);
    }
}
