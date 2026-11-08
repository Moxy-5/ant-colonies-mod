package com.antcolonies.savanna.worldgen;

import java.util.HashMap;
import java.util.Map;

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
 * flat floodplain ground, never near the river (it is its own water, a block up on the plain):
 * <ul>
 * <li><b>oxbows</b> ({@code oxbow} true): a river bend cut off and left as a crescent lake when the river took a shorter
 * way; 3 to 5 wide, 1 deep, 2 down its middle, a little trampled mud at the ends;</li>
 * <li><b>pans</b>: shallow rough ovals 5 to 12 across that fill with rain and flood water, kept bare and muddy by
 * animals drinking and wallowing; 1 deep with a mud bed and a thin ring of mud round the water.</li>
 * </ul>
 * The water takes the floodplain's murky green ({@code antcolonies:savanna_floodplain}). Full all year for now; drying
 * and refilling with the seasons later, from the one shared season source. A pool skips any spot where something other
 * than plants stands on the ground (a mound, a log, a tree from a chunk already decorated) or the ground is not level.
 */
public class FloodplainPoolFeature extends Feature<NoneFeatureConfiguration> {
    /** No pool within this many blocks of the river's water. */
    static final int FROM_RIVER = 8;
    private final boolean oxbow;

    public FloodplainPoolFeature(Codec<NoneFeatureConfiguration> codec, boolean oxbow) {
        super(codec);
        this.oxbow = oxbow;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        return place(level, context.origin(), context.random(), oxbow, level.getSeaLevel() - 1);
    }

    /**
     * Puts a pool (an oxbow or a pan) round {@code o}, if it fits: level ground above water whose surface is at
     * {@code sea} (the river's, sea level in the world), none of it within {@link #FROM_RIVER} blocks.
     */
    public static boolean place(WorldGenLevel level, BlockPos o, RandomSource random, boolean oxbow, int sea) {
        Map<Long, Integer> depth = oxbow ? oxbow(o.getX(), o.getZ(), random) : pan(o.getX(), o.getZ(), random);
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
            // pans: a thin, nearly whole ring of trampled mud; oxbows: mud only here and there, more at the ends
            double chance = oxbow ? 0.3 : 0.8;
            if (random.nextDouble() < chance) {
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
        return true;
    }

    /** A crescent: part of a ring, thinning to points at its ends, 2 deep down the middle of its middle part. */
    private static Map<Long, Integer> oxbow(int cx, int cz, RandomSource random) {
        Map<Long, Integer> cells = new HashMap<>();
        double radius = 4 + random.nextDouble() * 4, width = 3 + random.nextDouble() * 2;
        double span = Math.toRadians(180 + random.nextInt(80)), start = random.nextDouble() * 2 * Math.PI;
        int reach = (int) Math.ceil(radius + width);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                double a = Math.atan2(dz, dx) - start;
                a = ((a % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
                if (a > span) {
                    continue;
                }
                double along = a / span; // 0 to 1 from end to end
                double half = width / 2 * Math.sqrt(Math.sin(Math.PI * along));
                double off = Math.abs(r - radius);
                if (off < half) {
                    cells.put(BlockPos.asLong(cx + dx, 0, cz + dz), off < half * 0.45 && along > 0.25 && along < 0.75 ? 2 : 1);
                }
            }
        }
        return cells;
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
