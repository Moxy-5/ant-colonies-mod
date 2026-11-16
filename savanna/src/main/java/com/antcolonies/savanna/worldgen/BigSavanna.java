package com.antcolonies.savanna.worldgen;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The gate (the user, 2026-11-16: "a biome too small to harbor an ecosystem would never begin to form one";
 * {@code docs/plans/big-savannas-big-rivers.md}, step 2c): the one place that says whether a piece of savanna is big
 * enough to live in. A savanna too small (the thin climate slivers and specks left after one recipe everywhere, mostly
 * under 100 blocks across, a few 300 to 500) stays savanna grass but gets none of the ecosystem: no termite mounds (so no
 * Matabele, who live in dead mounds near living ones), no giant or floodplain trees or seedlings, no algae, old remains or
 * river animals. The biomes themselves are untouched: nothing here changes what Minecraft answers for "what biome is this".
 *
 * <p>The rule, one number: a place <b>lives</b> if there is at least as much savanna within {@link #REACH} blocks of it
 * as a round savanna {@link #ROOM} blocks across holds. So the inside of a big savanna lives, and its edges, arms and the
 * frayed bits just off its edge (the big one's ground is in their reach, and its animals would walk there anyway); a
 * lonely speck or strip does not, however it is shaped; lakes, river mouths and a stretch of coast inside a savanna do
 * not matter. The very tips of a big savanna's thin points go quiet. (A first try, "savanna all round at 200 and 400
 * blocks", silenced 39 percent of the big savannas' ground: real savannas are lobed and run along coasts.) The land is
 * counted on a grid: cells of {@link #CELL} blocks, {@link #LOOKS} looks a cell. Real: small habitat islands hold few
 * or none of the mainland's species (island biogeography), and a Macrotermes and Megaponera community needs room for
 * many mounds.
 *
 * <p>Read from the biome noise (no chunks), the way the spawn search reads it ({@link SavannaSpawn#savannaAt}: a savanna's
 * own river counts as savanna). Answers are kept per cell for the world being asked about, with a cap (the memory leak
 * lesson, BUGS 222); a cell's answer never depends on who asks first, so every chunk agrees. Numbers my picks.
 */
public final class BigSavanna {
    public static final int CELL = 128, REACH = 640, ROOM = 800;
    /** Looks a cell, a side (2: four looks, 64 blocks apart). */
    private static final int LOOKS = 2;
    /** How many looks within reach must be savanna: the looks a round savanna {@link #ROOM} across covers. */
    static final int ENOUGH = (int) (Math.PI * ROOM * ROOM / 4 / ((double) CELL * CELL / (LOOKS * LOOKS)));
    private static final int MOST = 200_000;

    /** One world's answers (the biome source and the climate reader it was asked with, by identity). */
    private record Store(BiomeSource biomes, Climate.Sampler sampler, ConcurrentHashMap<Long, Byte> savanna, ConcurrentHashMap<Long, Boolean> lives) {
    }

    private static volatile Store store;

    private BigSavanna() {
    }

    /** Whether the savanna at (x, z) is big enough to live in. Ask only where the biome is one of the savanna's own. */
    public static boolean livesAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        Store s = store;
        if (s == null || s.biomes != biomes || s.sampler != sampler) {
            s = new Store(biomes, sampler, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            store = s;
        }
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        long key = key(cx, cz);
        Boolean known = s.lives.get(key);
        if (known != null) {
            return known;
        }
        if (s.lives.size() > MOST) {
            s.lives.clear();
        }
        if (s.savanna.size() > MOST) {
            s.savanna.clear();
        }
        int cells = REACH / CELL, found = 0;
        boolean lives = false;
        look:
        for (int ring = 0; ring <= cells; ring++) { // the nearest cells first: inside a big savanna a few rings are enough
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) == ring && i * i + j * j <= cells * cells) {
                        found += savannaIn(s, cx + i, cz + j);
                        if (found >= ENOUGH) {
                            lives = true;
                            break look;
                        }
                    }
                }
            }
        }
        s.lives.put(key, lives);
        return lives;
    }

    /**
     * The same for a place in the world, for things that are not only in savanna by their own rules (a spawn check, a
     * feature): a place whose biome is none of the savanna's, or is savanna only by hand, is not ours to judge and "lives". {@code here}: the biome at
     * the place, from the asker's own view of the world (never read from unfinished chunks here).
     */
    public static boolean lives(ServerLevel level, Holder<Biome> here, int x, int z) {
        if (!here.is(RiverBankFeature.WATERS)) {
            return true;
        }
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        // savanna put there by hand (/fillbiome, a game test's arena) where the land's own biome is another: not ours to judge
        return livesAt(biomes, sampler, x, z) || !SavannaSpawn.savannaAt(biomes, sampler, x, z);
    }

    /** How many of a cell's looks are savanna (0 to {@link #LOOKS} squared). */
    private static int savannaIn(Store s, int cx, int cz) {
        long key = key(cx, cz);
        Byte known = s.savanna.get(key);
        if (known == null) {
            int count = 0, step = CELL / LOOKS;
            for (int i = 0; i < LOOKS; i++) {
                for (int j = 0; j < LOOKS; j++) {
                    if (SavannaSpawn.savannaAt(s.biomes, s.sampler, cx * CELL + step / 2 + i * step, cz * CELL + step / 2 + j * step)) {
                        count++;
                    }
                }
            }
            known = (byte) count;
            s.savanna.put(key, known);
        }
        return known;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}
