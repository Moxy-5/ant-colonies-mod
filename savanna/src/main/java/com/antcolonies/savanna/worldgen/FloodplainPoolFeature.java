package com.antcolonies.savanna.worldgen;

import java.util.HashMap;
import java.util.Map;

import com.antcolonies.savanna.block.SavannaMudBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Still water on the savanna floodplain (2026-11-07, {@code docs/plans/savanna-rivers.md}, step 4), placed on purpose on
 * flat floodplain ground, never near the river (it is its own water, a block up on the plain). Since 2026-11-22 only the
 * pans (the oxbows are {@link OxbowFeature}, 16 to 32 across; the pans' own rework is step 4 of
 * {@code docs/plans/oxbows-and-pans.md}):
 * <ul>
 * <li><b>pans</b>: shallow rough ovals 5 to 12 across that fill with rain and flood water, kept bare and muddy by
 * animals drinking and wallowing; 1 deep with a mud bed and a thin ring of mud round the water, and a wallow of savanna
 * mud at one end ({@link #panWallow}, 2026-11-21).</li>
 * </ul>
 * The water takes the floodplain's murky green ({@code antcolonies:savanna_floodplain}). Full all year for now; drying
 * and refilling with the seasons later, from the one shared season source. A pool skips any spot where something other
 * than plants stands on the ground (a mound, a log, a tree from a chunk already decorated) or the ground is not level.
 */
public class FloodplainPoolFeature extends Feature<NoneFeatureConfiguration> {
    /** No pool within this many blocks of the river's water. */
    static final int FROM_RIVER = 8;
    public FloodplainPoolFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        return place(level, context.origin(), context.random(), level.getSeaLevel() - 1);
    }

    /**
     * Puts a pan round {@code o}, if it fits: level ground above water whose surface is at
     * {@code sea} (the river's, sea level in the world), none of it within {@link #FROM_RIVER} blocks.
     */
    public static boolean place(WorldGenLevel level, BlockPos o, RandomSource random, int sea) {
        Map<Long, Integer> depth = pan(o.getX(), o.getZ(), random);
        // the rim: every cell beside the water
        Map<Long, Boolean> rim = new HashMap<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : depth.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long side = BlockPos.asLong(x + dx, 0, z + dz);
                    if (!depth.containsKey(side)) {
                        rim.put(side, true);
                    }
                }
            }
        }
        if (depth.isEmpty() || maxX - minX > 28 || maxZ - minZ > 28) {
            return false;
        }
        // level ground: every cell's top within a block, the lowest above the river's water
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (long key : concat(depth, rim)) {
            int g = RiverBankFeature.ground(level, BlockPos.getX(key), BlockPos.getZ(key), sea + 6);
            low = Math.min(low, g);
            high = Math.max(high, g);
        }
        if (high - low > 1 || low <= sea || low > sea + 4) {
            return false;
        }
        for (long key : concat(depth, rim)) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            int g = RiverBankFeature.ground(level, x, z, sea + 6);
            if (!RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
                return false;
            }
        }
        // not near the river (its own water stays its own) nor any water already there
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, minX - FROM_RIVER - 1, minZ - FROM_RIVER - 1,
                Math.max(maxX - minX, maxZ - minZ) + 2 * FROM_RIVER + 3, sea);
        for (int x = minX - FROM_RIVER; x <= maxX + FROM_RIVER; x++) {
            for (int z = minZ - FROM_RIVER; z <= maxZ + FROM_RIVER; z++) {
                if (waters.isWater(x, z)) {
                    return false;
                }
            }
        }
        BlockState mud = SavannaBlocks.RIVER_MUD.get().defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        for (Map.Entry<Long, Integer> e : depth.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            clear(level, x, low, z);
            for (int d = 0; d < e.getValue(); d++) {
                level.setBlock(new BlockPos(x, low - d, z), water, Block.UPDATE_CLIENTS);
            }
            level.setBlock(new BlockPos(x, low - e.getValue(), z), mud, Block.UPDATE_CLIENTS);
        }
        for (long key : rim.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            // a thin, nearly whole ring of trampled mud
            if (random.nextDouble() < 0.8) {
                clear(level, x, low, z);
                level.setBlock(new BlockPos(x, low, z), mud, Block.UPDATE_CLIENTS);
            } else {
                // the rim keeps its grass, cut to the water's level so nothing stands a block over it
                int g = RiverBankFeature.ground(level, x, z, low + 4);
                if (g > low) {
                    clear(level, x, low, z);
                    level.setBlock(new BlockPos(x, low, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        panWallow(level, random, depth, rim, low);
        return true;
    }

    /** How far round a pan's wallow reaches from the rim spot it is centred on (blocks; my pick: 4 to 6 across). */
    static final double WALLOW_REACH = 2.6;

    /**
     * A wallow at one end of a pan (savanna mud, 2026-11-21, {@code docs/plans/savanna-mud.md}, section 5.1): olive savanna
     * mud round a rim spot picked at random, on the rim and the ring outside it, soupy by the water and sticky at its
     * edge. The pan's water keeps it wet. Ground of the outer ring a block high is cut to the rim's level; anything but a
     * plant there leaves that cell out.
     */
    private static void panWallow(WorldGenLevel level, RandomSource random, Map<Long, Integer> depth, Map<Long, Boolean> rim, int low) {
        if (rim.isEmpty()) {
            return;
        }
        Long[] spots = rim.keySet().toArray(new Long[0]);
        java.util.Arrays.sort(spots);
        long spot = spots[random.nextInt(spots.length)];
        int sx = BlockPos.getX(spot), sz = BlockPos.getZ(spot);
        int reach = (int) Math.ceil(WALLOW_REACH);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = sx + dx, z = sz + dz;
                long key = BlockPos.asLong(x, 0, z);
                if (d > WALLOW_REACH || depth.containsKey(key)) {
                    continue;
                }
                if (!rim.containsKey(key)) {
                    // the ring outside the rim: soil at the rim's level or a block over it, nothing but plants above
                    int g = RiverBankFeature.ground(level, x, z, low + 4);
                    if (g < low || g > low + 1 || !RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
                        continue;
                    }
                    clear(level, x, low, z);
                }
                int mudDepth = d < 1.0 ? 3 : d < 2.0 ? 2 : 1;
                SavannaMudBlock.Wetness wetness = mudDepth == 1 ? SavannaMudBlock.Wetness.STICKY : SavannaMudBlock.Wetness.SOUPY;
                level.setBlock(new BlockPos(x, low, z), SavannaBlocks.SAVANNA_MUD.get().with(wetness, mudDepth, SavannaMudBlock.Tone.OLIVE),
                        Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A rough oval, its edge wavy. */
    private static Map<Long, Integer> pan(int cx, int cz, RandomSource random) {
        Map<Long, Integer> cells = new HashMap<>();
        double a = 2.5 + random.nextDouble() * 3.5, b = 2.5 + random.nextDouble() * 3.5, turn = random.nextDouble() * Math.PI;
        double p1 = random.nextDouble() * 6.3, p2 = random.nextDouble() * 6.3;
        int reach = (int) Math.ceil(Math.max(a, b) * 1.2) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double u = dx * Math.cos(turn) + dz * Math.sin(turn), v = -dx * Math.sin(turn) + dz * Math.cos(turn);
                double ang = Math.atan2(v, u);
                double edge = 1 + 0.12 * Math.sin(3 * ang + p1) + 0.08 * Math.sin(5 * ang + p2);
                if ((u * u) / (a * a) + (v * v) / (b * b) < edge * edge) {
                    cells.put(BlockPos.asLong(cx + dx, 0, cz + dz), 1);
                }
            }
        }
        return cells;
    }

    /** Only plants (or nothing) stand on the ground here, up to 3 blocks over it. */
    private static boolean clearAbove(WorldGenLevel level, int x, int g, int z) {
        for (int y = g + 1; y <= g + 3; y++) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty())) {
                return false;
            }
        }
        return true;
    }

    /** Takes the plants and the ground over {@code y} away, up to the column's top plus a few blocks. */
    private static void clear(WorldGenLevel level, int x, int y, int z) {
        int g = RiverBankFeature.ground(level, x, z, y + 4);
        for (int k = y + 1; k <= Math.max(g, y) + 3; k++) {
            BlockPos at = new BlockPos(x, k, z);
            if (!level.getBlockState(at).isAir()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static Iterable<Long> concat(Map<Long, Integer> a, Map<Long, Boolean> b) {
        java.util.List<Long> all = new java.util.ArrayList<>(a.keySet());
        all.addAll(b.keySet());
        return all;
    }
}
