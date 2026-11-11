package com.antcolonies.savanna.worldgen;

import java.util.Map;
import java.util.WeakHashMap;

import com.antcolonies.debug.Perf;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
        return workOut(generator, x, z, type, heights, random);
    }

    /**
     * Several columns worked out side by side on the world builder's spare threads before they are asked for one by one
     * (loading time, 2026-11-10): a tree's foot or a mound's place asks for 5 to 13 columns in a row, each about 2 ms, on
     * the one lane Minecraft makes structures on; the columns do not depend on each other, so together they take about
     * as long as one. The same answers as asking one by one.
     */
    public static void warm(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random, int[] xs, int[] zs) {
        java.util.stream.IntStream.range(0, xs.length).parallel()
                .forEach(i -> at(generator, xs[i], zs[i], Heightmap.Types.WORLD_SURFACE_WG, heights, random));
    }

    /** The land's surface as the world's shape makes it, for fitting trees and fallen wood (columns warmed side by side). */
    public static TreePlacer.Ground ground(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
        return new TreePlacer.Ground() {
            @Override
            public int freeY(int x, int z) {
                return at(generator, x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
            }

            @Override
            public void warm(int[] xs, int[] zs) {
                GroundHeights.warm(generator, heights, random, xs, zs);
            }
        };
    }

    private static int workOut(ChunkGenerator generator, int x, int z, Heightmap.Types type, LevelHeightAccessor heights, RandomState random) {
        long start = System.nanoTime();
        int i = (x & 15) << 4 | (z & 15);
        int h;
        boolean surface = type == Heightmap.Types.WORLD_SURFACE_WG, floor = type == Heightmap.Types.OCEAN_FLOOR_WG;
        if ((surface || floor) && generator instanceof NoiseBasedChunkGenerator) {
            // Both kinds of height from one look down the column: our trees and mounds always ask for the two together
            // (dry ground is where they agree), and each asked alone works the whole column's shape out again. The same
            // rule as Minecraft's own (the first block from the top that counts for this kind, plus one).
            NoiseColumn column = generator.getBaseColumn(x, z, heights, random);
            int top = first(column, heights, Heightmap.Types.WORLD_SURFACE_WG);
            int bed = first(column, heights, Heightmap.Types.OCEAN_FLOOR_WG);
            page(random, Heightmap.Types.WORLD_SURFACE_WG, x >> 4, z >> 4)[i] = top;
            page(random, Heightmap.Types.OCEAN_FLOOR_WG, x >> 4, z >> 4)[i] = bed;
            h = surface ? top : bed;
        } else {
            h = generator.getFirstFreeHeight(x, z, type, heights, random);
            page(random, type, x >> 4, z >> 4)[i] = h; // (an int is written whole: another thread sees the height or UNKNOWN, never half of one)
        }
        Perf.newLand(Perf.NewLand.GROUND, System.nanoTime() - start);
        return h;
    }

    private static int first(NoiseColumn column, LevelHeightAccessor heights, Heightmap.Types type) {
        var counts = type.isOpaque();
        for (int y = heights.getMaxBuildHeight() - 1; y >= heights.getMinBuildHeight(); y--) {
            if (counts.test(column.getBlock(y))) {
                return y + 1;
            }
        }
        return heights.getMinBuildHeight();
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
                Perf.newLand(Perf.NewLand.GROUND_CHUNKS, 0);
                if (pages.size() > PAGES) {
                    pages.removeFirst();
                }
            }
            return page;
        }
    }
}
