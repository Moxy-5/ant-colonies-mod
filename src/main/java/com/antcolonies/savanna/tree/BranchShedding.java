package com.antcolonies.tree;

import java.util.Random;

import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.TreeBranchBlock;
import com.antcolonies.block.TreeLeavesBlock;
import com.antcolonies.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * M7.5 step 6b (user, 2026-10-19): grown giant trees shed dead branches, the food termites keep coming back to. Real
 * acacias drop their shaded-out dead inner branches all the time; old trees carry and drop the most dead wood, and most
 * of it comes down in wind and storms. So each day a grown tree drops a branch with a chance from {@link #JUST_GROWN}
 * (about every 2 to 3 days) to {@link #ANCIENT} (about daily), and a thunderstorm over it shakes more down; it lands
 * under its own crown, straight down from a leaf or thin branch, unless {@link #MAX_UNDER_TREE} already lie there
 * (termites and rot take them). Young trees only drop their twigs as they grow (step 4). Called by {@link Forest}
 * (the days a tree lives, caught up on return too, and each second of a thunderstorm). This replaced the old shedding of
 * Minecraft's acacias (gone from new savannas).
 */
public final class BranchShedding {
    /** The chance a day of a just-grown tree dropping a branch (my pick: about every 2 to 3 days). */
    static final double JUST_GROWN = 0.4;
    /** The chance a day of an ancient tree dropping a branch (about daily). */
    static final double ANCIENT = 1.0;
    /** The chance each second of a thunderstorm over a grown tree (my pick: about 1 to 2 more branches a storm). */
    static final int STORM_ONE_IN = 400;
    /** At most this many fallen branches lie under one tree's crown before no more drop there. */
    public static final int MAX_UNDER_TREE = 3;
    /** How far above and below the foot fallen branches are counted (the crown's ground may slope). */
    private static final int COUNT_DEPTH = 6;

    private BranchShedding() {
    }

    /** The chance a day of a living tree this old dropping a branch (none before it is grown). */
    public static double chance(double age) {
        if (age < TreeShapes.GROWN) {
            return 0;
        }
        return JUST_GROWN + (ANCIENT - JUST_GROWN) * TreeLife.old(age);
    }

    /** Whether a thunderstorm shakes a branch off a grown tree this second. */
    static boolean stormShakes(ServerLevel level, double age, BlockPos foot) {
        return age >= TreeShapes.GROWN && level.isThundering() && level.getBiome(foot).value().hasPrecipitation()
                && level.getRandom().nextInt(STORM_ONE_IN) == 0;
    }

    /**
     * Drops one dead branch from the tree standing as {@code now} at {@code foot}: under a leaf or thin branch of its
     * crown picked by the dice, onto the ground under it, unless enough lie under the crown already. True if one fell.
     * {@code loud}: with the crack of the branch (a player may be near).
     */
    public static boolean shed(ServerLevel level, BlockPos foot, TreePlacer.Placed now, Random dice, boolean loud) {
        if (now == null || now.size() == 0 || lyingUnder(level, foot, now) >= MAX_UNDER_TREE) {
            return false;
        }
        for (int tries = 0; tries < 8; tries++) {
            int i = dice.nextInt(now.size());
            BlockState state = now.states()[i];
            if (!(state.getBlock() instanceof TreeLeavesBlock) && !(state.getBlock() instanceof TreeBranchBlock)) {
                continue;
            }
            BlockPos from = foot.offset(now.xs()[i], now.ys()[i], now.zs()[i]);
            // Start just under the crown block: the branch falls through the air to the ground below.
            if (!level.isLoaded(from) || !TreePlacer.isOpen(level.getBlockState(from.below()))) {
                continue;
            }
            if (Forest.dropBranch(level, from.below(), dice)) {
                if (loud) {
                    level.playSound(null, from, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F + dice.nextFloat() * 0.2F);
                }
                return true;
            }
        }
        return false;
    }

    /** How many fallen branches lie under this tree's crown (counted by their thick ends). Public for the tests. */
    public static int lyingUnder(ServerLevel level, BlockPos foot, TreePlacer.Placed now) {
        int count = 0;
        BlockPos lo = foot.offset(now.minX(), -COUNT_DEPTH, now.minZ()), hi = foot.offset(now.maxX(), COUNT_DEPTH, now.maxZ());
        for (BlockPos pos : BlockPos.betweenClosed(lo, hi)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FallenBranchBlock && state.getValue(FallenBranchBlock.PART) == FallenBranchBlock.Part.BASE) {
                count++;
            }
        }
        return count;
    }
}
