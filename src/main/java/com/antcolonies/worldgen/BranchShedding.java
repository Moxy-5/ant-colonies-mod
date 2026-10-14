package com.antcolonies.worldgen;

import com.antcolonies.block.FallenBranchBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Acacias shed dead branches, as real ones do all the time: the food termites keep coming back to. Every
 * {@link #EVERY_TICKS} ticks, round each player, one spot within {@link #RANGE} blocks is looked at (like Minecraft's
 * random ticks, but per player, so the cost stays tiny); if it is under the canopy of a natural acacia (leaves not placed
 * by a player), a branch drops to the ground near it, unless {@link #MAX_UNDER_TREE} already lie there. With a savanna's
 * trees that is about one branch per tree every day or two (my pick, to confirm). Only where the player is, since
 * Minecraft only runs loaded land.
 */
public final class BranchShedding {
    static final int EVERY_TICKS = 100;
    static final int RANGE = 48;
    /** How many branches may lie round one spot under a tree (within {@link #CROWD_RADIUS}) before no more drop there. */
    static final int MAX_UNDER_TREE = 2;
    private static final int CROWD_RADIUS = 6;

    private BranchShedding() {
    }

    /** Called every tick from the level tick: now and then, a branch may fall near each player. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % EVERY_TICKS != 0) {
            return;
        }
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos at = player.blockPosition().offset(random.nextInt(RANGE * 2 + 1) - RANGE, 0, random.nextInt(RANGE * 2 + 1) - RANGE);
            tryShed(level, at.getX(), at.getZ(), random);
        }
    }

    /**
     * If the column at x, z is under a natural acacia's leaves, drops a branch on the ground near it (unless enough lie
     * there already). True if one fell. Public for the tests.
     */
    public static boolean tryShed(ServerLevel level, int x, int z, RandomSource random) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return false;
        }
        BlockPos canopy = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
        BlockState leaves = level.getBlockState(canopy);
        if (!leaves.is(Blocks.ACACIA_LEAVES) || leaves.getValue(LeavesBlock.PERSISTENT)) {
            return false;
        }
        int gx = x + random.nextInt(5) - 2;
        int gz = z + random.nextInt(5) - 2;
        BlockPos ground = new BlockPos(gx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz), gz);
        if (ground.getY() >= canopy.getY() || branchesNear(level, ground) >= MAX_UNDER_TREE) {
            return false;
        }
        if (!FallenBranchFeature.placeBranch(level, ground, random, 3)) {
            return false;
        }
        level.playSound(null, ground, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F + random.nextFloat() * 0.2F);
        return true;
    }

    /** How many branches lie round this spot (counted by their thick ends). */
    private static int branchesNear(ServerLevel level, BlockPos ground) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-CROWD_RADIUS, -2, -CROWD_RADIUS), ground.offset(CROWD_RADIUS, 2, CROWD_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FallenBranchBlock && state.getValue(FallenBranchBlock.PART) == FallenBranchBlock.Part.BASE) {
                count++;
            }
        }
        return count;
    }
}
