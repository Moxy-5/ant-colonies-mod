package com.antcolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/**
 * Is this land really made? (savanna loading, round 2, step L4, {@code docs/plans/savanna-loading-2.md}.)
 *
 * <p>Minecraft's own check ({@code hasChunk}, {@code hasChunkAt}, {@code isLoaded}) says yes as soon as a chunk is
 * <i>wanted</i> at full load (a player's view reaches it), even while it is still being made on the world-making
 * threads. Code that then reads or writes a block there makes the server wait, frozen, until that chunk is finished:
 * with a player flying over new land, seconds (the flight run found stalls of 0.5 to 3.7 s putting a termite colony in,
 * 2 s scanning a new mound's dead wood, 2.3 and 5 s in the giant trees' update). This asks whether the chunk is finished
 * and in memory now, which never waits: what is not ready yet is simply looked at again a moment later.
 *
 * <p>On the server thread only; anywhere else (the client, world-making regions, other threads) it is Minecraft's check.
 */
public final class LandReady {
    private LandReady() {
    }

    /** Whether the chunk holding block column (x, z) is made and loaded now. */
    public static boolean at(LevelReader level, int x, int z) {
        return chunk(level, x >> 4, z >> 4);
    }

    public static boolean at(LevelReader level, BlockPos pos) {
        return at(level, pos.getX(), pos.getZ());
    }

    /** Whether chunk (cx, cz) is made and loaded now. */
    public static boolean chunk(LevelReader level, int cx, int cz) {
        if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
            return server.getChunkSource().getChunkNow(cx, cz) != null;
        }
        return level.hasChunk(cx, cz);
    }

    /** Whether every chunk touching the block square from (x0, z0) to (x1, z1) is made and loaded now. */
    public static boolean box(LevelReader level, int x0, int z0, int x1, int z1) {
        for (int cx = Math.min(x0, x1) >> 4; cx <= Math.max(x0, x1) >> 4; cx++) {
            for (int cz = Math.min(z0, z1) >> 4; cz <= Math.max(z0, z1) >> 4; cz++) {
                if (!chunk(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether every chunk within {@code round} blocks (a square) of {@code middle} is made and loaded now. */
    public static boolean round(LevelReader level, BlockPos middle, int round) {
        return box(level, middle.getX() - round, middle.getZ() - round, middle.getX() + round, middle.getZ() + round);
    }
}
