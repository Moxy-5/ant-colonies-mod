package com.antcolonies.savanna.worldgen;

import java.util.Map;
import java.util.WeakHashMap;

import com.antcolonies.debug.Perf;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The ground's height in land not made yet, remembered (docs/plans/savanna-loading.md, step 2). Before a chunk is made
 * there are no blocks to look at, so Minecraft works the height out from the world's shape each time it is asked
 * ({@link ChunkGenerator#getFirstFreeHeight}), which is slow, and our giant trees and mound places ask for the same
 * columns again and again: every place a tree tries, every fallen log fitted, every chunk a tree crosses. The answer
 * comes from the world's seed alone and never changes before the chunk is made, so each column is worked out once here
 * and kept, for every tree, place and chunk alike (the same answer as before, only sooner).
 *
 * <p>Kept in pages of one chunk's 16 by 16 columns, the most recently used {@link #PAGES} of them (about 4 MB), for each
 * world (its {@link RandomState}).
 */
public final class GroundHeights {
    /** Chunk pages kept per world and kind of height: 4,096 is a square of 64 by 64 chunks, more than a player's view. */
    static final int PAGES = 4096;
    private static final int UNKNOWN = Integer.MIN_VALUE;

    /** Per world: one page map for each kind of height asked for. */
    private static final Map<RandomState, Pages[]> WORLDS = new WeakHashMap<>();

    private static final class Pages extends Long2ObjectLinkedOpenHashMap<int[]> {
    }

    private GroundHeights() {
    }

    /** The first free height at (x, z) for this kind of height, as {@link ChunkGenerator#getFirstFreeHeight} gives it. */
    public static int at(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor heights, RandomState random) {
        int[] page = page(random, type, x >> 4, z >> 4);
        int i = (x & 15) << 4 | (z & 15);
        int h = page[i];
        if (h != UNKNOWN) {
            Perf.newLand(Perf.NewLand.GROUND_KEPT, 0);
            return h;
        }
        long start = System.nanoTime();
        h = generator.getFirstFreeHeight(x, z, type, heights, random);
        Perf.newLand(Perf.NewLand.GROUND, System.nanoTime() - start);
        page[i] = h; // (an int is written whole: another thread sees the height or UNKNOWN, never half of one)
        return h;
    }

    private static int[] page(RandomState random, Heightmap.Types type, int cx, int cz) {
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        synchronized (WORLDS) {
            Pages[] kinds = WORLDS.computeIfAbsent(random, r -> new Pages[Heightmap.Types.values().length]);
            Pages pages = kinds[type.ordinal()];
            if (pages == null) {
                pages = kinds[type.ordinal()] = new Pages();
            }
            int[] page = pages.getAndMoveToLast(key);
            if (page == null) {
                page = new int[256];
                java.util.Arrays.fill(page, UNKNOWN);
                pages.putAndMoveToLast(key, page);
                if (pages.size() > PAGES) {
                    pages.removeFirst();
                }
            }
            return page;
        }
    }
}
