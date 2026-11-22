package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.savanna.SavannaMod;
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
 * New land's pans (oxbows and pans rework step 4, 2026-11-22, {@code docs/plans/oxbows-and-pans.md}): shallow rain-filled
 * hollows on the open savanna, often far from any river, kept bare round the edge by the animals that come to drink and
 * wallow. The shapes are the model viewer's ({@code tools/oxbow_viewer.template.html}, {@code panShape}): an oval up to
 * 1.45 times as long as wide, turned any way, its edge wavy, 2 in 5 with a second smaller bowl run into the first (pans
 * that grew together). {@link #ACROSS_MIN} to {@link #ACROSS_MAX} across at the widest bowl, at random (my pick: the
 * user's 16 to 32 for the oxbows, in place of the plan's 16 to 40, so a pan and its ring fit the 3 by 3 chunks a feature
 * may write). 2 deep in the middle, 1 toward the edge, 3 in the very middle of the biggest (28 across or more); a bed of
 * mud and wet silt; a wet shore of the land's savanna mud flush with the water; a bare trampled ring 2 to 7 wide (coarse
 * dirt, dirt, drying mud), its inner half flush, its outer half a block up; short grazed grass for 6 more; a wallow of soupy
 * mud at one end ({@link #wallow}, savanna mud step 3). The water stands at the lowest ground round it: ground up to 3
 * blocks uneven is cut down to it, and the land past the ring slopes up a block a block. Stays wet for now; filling and
 * drying wait for the seasons. Water colour: the savanna's still water for now (the clay-brown pan water needs a small pan
 * biome, not built).
 *
 * <p>On a pan site ({@link PanSites}) the chunk tries {@link #TRIES} spots round its middle. A pan goes only on soft open
 * ground (nothing but plants), never within {@link #FROM_WATER} blocks of sea-level water, no termite mound within
 * {@link OxbowFeature#MOUND_CLEAR} blocks of its ring, no tree's foot within {@link OxbowFeature#TREE_CLEAR}; never in a
 * quiet savanna. Numbers my picks unless marked.
 */
public class PanFeature extends Feature<NoneFeatureConfiguration> {
    /** Across at the widest bowl, picked at random between these. */
    public static final int ACROSS_MIN = 16, ACROSS_MAX = 32;
    /** The most the ground under a pan and its ring may rise and fall (the plan's pick). */
    static final int UNEVEN = 3;
    /** No sea-level water (a river, a lake) this close. */
    static final int FROM_WATER = 8;
    /** Short grazed grass this far past the ring. */
    static final int GRAZED = 6;
    static final int TRIES = 6;
    /** How far round a pan's wallow reaches from the shore spot it is centred on (blocks; 4 to 6 across). */
    static final double WALLOW_REACH = 2.6;

    /** One bowl of a pan: its middle, size, stretch, turn and wavy edge. */
    record Lobe(double x, double z, double r, double sx, double ang, double p1, double p2, double wob) {
    }

    /** What was laid. */
    public record Laid(BlockPos middle, int across, boolean twoBowls, int deepest, int ring, int water) {
    }

    public PanFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        var source = level.getLevel().getChunkSource();
        var sampler = source.randomState().sampler();
        if (!PanSites.site(source.getGenerator().getBiomeSource(), sampler, level.getSeed(), x0 >> 4, z0 >> 4)) {
            return false;
        }
        int sea = level.getSeaLevel() - 1;
        if (!BigSavanna.lives(level.getLevel(), level.getBiome(new BlockPos(x0 + 8, sea, z0 + 8)), x0 + 8, z0 + 8)) {
            return false;
        }
        RandomSource random = context.random();
        int[] bounds = {x0 - 16, z0 - 16, x0 + 31, z0 + 31};
        SavannaMudBlock.Tone tone = MudSites.landTone(sampler, x0 + 8, z0 + 8);
        OxbowFeature.Guard guard = (minX, minZ, maxX, maxZ, cells) -> OxbowFeature.nearMound(level, minX, minZ, maxX, maxZ)
                || OxbowFeature.nearTree(level, bounds, cells);
        Map<String, Integer> why = new java.util.TreeMap<>();
        for (int t = 0; t < TRIES; t++) {
            WHY.remove();
            int px = x0 + 4 + random.nextInt(9), pz = z0 + 4 + random.nextInt(9);
            // the land's own ground there, from before any tree or plant (the lakes come first)
            int lookFrom = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, px, pz) + UNEVEN + 4;
            Laid laid = lay(level, random, px, pz, bounds, sea, lookFrom, 0, tone, guard);
            if (laid != null) {
                SavannaMod.LOGGER.info("[world pans] pan at {} {}: {} across{}, {} deep, ring {}, {} water blocks", laid.middle().getX(),
                        laid.middle().getZ(), laid.across(), laid.twoBowls() ? " (two bowls)" : "", laid.deepest(), laid.ring(), laid.water());
                return true;
            }
            why.merge(String.valueOf(WHY.get()), 1, Integer::sum);
        }
        SavannaMod.LOGGER.info("[world pans] site at {} {}: no room {}", x0 + 8, z0 + 8, why);
        return false;
    }

    /** Why the last try on this thread laid nothing (for the log). */
    private static final ThreadLocal<String> WHY = new ThreadLocal<>();

    /** Why the last try on this thread laid nothing (for tests). */
    public static String lastWhy() {
        return WHY.get();
    }

    private static Laid no(String why) {
        WHY.set(why);
        return null;
    }

    /**
     * Lays a pan round {@code cx, cz} if it fits, everything read and written inside {@code bounds} ({minX, minZ, maxX,
     * maxZ}); {@code sea} the sea-level water's top; every column's ground read down from {@code lookFrom} (a few blocks over
     * the middle's ground: never from a column's own top, which may be a tree's crown or a test's barrier roof). {@code across} 0 picks at random; {@code guard} null keeps clear of
     * nothing more. Returns what was laid, or null (nothing changed).
     */
    public static Laid lay(WorldGenLevel level, RandomSource random, int cx, int cz, int[] bounds, int sea, int lookFrom, int across,
            SavannaMudBlock.Tone tone, OxbowFeature.Guard guard) {
        int want = across > 0 ? across : ACROSS_MIN + random.nextInt(ACROSS_MAX - ACROSS_MIN + 1);
        double r = want / 2.0;
        List<Lobe> lobes = new ArrayList<>();
        lobes.add(new Lobe(0, 0, r, 1 + 0.45 * random.nextDouble(), random.nextDouble() * Math.PI, random.nextDouble() * 6.28,
                random.nextDouble() * 6.28, 0.04 + 0.08 * random.nextDouble()));
        if (random.nextDouble() < 0.4) {
            double a = random.nextDouble() * 6.28, rr = r * (0.45 + 0.25 * random.nextDouble());
            lobes.add(new Lobe(Math.cos(a) * r * 0.8, Math.sin(a) * r * 0.8, rr, 1 + 0.3 * random.nextDouble(), random.nextDouble() * Math.PI,
                    random.nextDouble() * 6.28, random.nextDouble() * 6.28, 0.06));
        }
        int ring = (int) Math.round(2 + r / 8 + 2 * random.nextDouble());
        // the columns: water (with its depth), shore, ring, and the land past it (out = blocks past the water's edge)
        Map<Long, Integer> water = new HashMap<>();
        Map<Long, Double> shore = new HashMap<>(), trampled = new HashMap<>(), past = new HashMap<>();
        int reach = (int) Math.ceil(r * 2.2 + ring + GRAZED + 2);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Double.MAX_VALUE;
                int depth = 1;
                for (Lobe l : lobes) {
                    double ex = dx - l.x(), ez = dz - l.z(), c = Math.cos(l.ang()), s = Math.sin(l.ang()), k = Math.sqrt(l.sx());
                    double u = (ex * c + ez * s) / k, v = (-ex * s + ez * c) * k, a = Math.atan2(v, u), rr = Math.hypot(u, v);
                    double edge = l.r() * (1 + l.wob() * Math.sin(3 * a + l.p1()) + l.wob() * 0.6 * Math.sin(5 * a + l.p2()));
                    d = Math.min(d, rr - edge);
                    if (rr < edge * 0.55) {
                        depth = Math.max(depth, 2);
                    }
                    if (rr < edge * 0.25 && l.r() >= 14) {
                        depth = 3; // only the biggest pans are 3 deep in the very middle
                    }
                }
                long key = BlockPos.asLong(x, 0, z);
                if (d < 0) {
                    water.put(key, depth);
                } else if (d < 1.5) {
                    shore.put(key, d);
                } else if (d < ring + 1.5) {
                    trampled.put(key, d);
                } else if (d < ring + 1.5 + GRAZED) {
                    past.put(key, d - ring - 1.5);
                }
            }
        }
        // inside the chunks it may touch, a tree's foot's width to spare
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        List<Long> footprint = new ArrayList<>(water.keySet());
        footprint.addAll(shore.keySet());
        footprint.addAll(trampled.keySet());
        for (long key : footprint) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        int margin = OxbowFeature.TREE_CLEAR;
        if (minX < bounds[0] + margin || maxX > bounds[2] - margin || minZ < bounds[1] + margin || maxZ > bounds[3] - margin) {
            return no("past the chunks it may touch");
        }
        // no river or lake near
        int size = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]) + 1;
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, bounds[0], bounds[1], size, sea);
        for (int x = minX - FROM_WATER; x <= maxX + FROM_WATER; x++) {
            for (int z = minZ - FROM_WATER; z <= maxZ + FROM_WATER; z++) {
                if (waters.isWater(x, z)) {
                    return no("water near, at " + x + " " + z + " (the pan from " + minX + " " + minZ + " to " + maxX + " " + maxZ + ")");
                }
            }
        }
        // soft open ground, uneven by no more than UNEVEN, above the sea
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (long key : footprint) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            int g = RiverBankFeature.ground(level, x, z, lookFrom);
            if (!RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z)))) {
                return no("not soft ground: " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, g, z)).getBlock()).getPath());
            }
            if (!clearAbove(level, x, g, z)) {
                return no("something on the ground");
            }
            low = Math.min(low, g);
            high = Math.max(high, g);
            if (high - low > UNEVEN) {
                return no("not level");
            }
        }
        if (low <= sea) {
            return no("too low");
        }
        if (guard != null && guard.refuses(minX, minZ, maxX, maxZ, footprint)) {
            return no("a termite mound or tree");
        }
        // lay it
        BlockState mud = SavannaBlocks.RIVER_MUD.get().defaultBlockState(), wetSilt = SavannaBlocks.WET_SILT.get().defaultBlockState();
        BlockState waterState = Blocks.WATER.defaultBlockState();
        BlockState shoreMud = SavannaBlocks.SAVANNA_MUD.get().with(SavannaMudBlock.Wetness.STICKY, 1, tone);
        BlockState dryMud = SavannaBlocks.SAVANNA_MUD.get().with(SavannaMudBlock.Wetness.DRYING, 1, tone);
        int deepest = 0, waterBlocks = 0;
        for (Map.Entry<Long, Integer> e : water.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey()), depth = e.getValue();
            clear(level, x, low, z);
            for (int k = 0; k < depth; k++) {
                level.setBlock(new BlockPos(x, low - k, z), waterState, Block.UPDATE_CLIENTS);
            }
            level.setBlock(new BlockPos(x, low - depth, z), RiverBankFeature.hash(x, z) < 0.5 ? mud : wetSilt, Block.UPDATE_CLIENTS);
            deepest = Math.max(deepest, depth);
            waterBlocks += depth;
        }
        for (long key : shore.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            clear(level, x, low, z);
            level.setBlock(new BlockPos(x, low, z), shoreMud, Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<Long, Double> e : trampled.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            int g = RiverBankFeature.ground(level, x, z, low + UNEVEN + 4);
            int top = e.getValue() < ring / 2.0 + 1.5 ? low : Math.min(g, low + 1);
            double k = RiverBankFeature.hash(x + 4, z);
            BlockState kind = k < 0.4 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 0.7 ? Blocks.DIRT.defaultBlockState() : dryMud;
            clear(level, x, top, z);
            level.setBlock(new BlockPos(x, top, z), kind, Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<Long, Double> e : past.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            if (x < bounds[0] || x > bounds[2] || z < bounds[1] || z > bounds[3]) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, low + UNEVEN + 4);
            if (!clearAbove(level, x, g, z)) {
                continue; // a tree, a log: left as it is
            }
            // the land past the ring slopes up a block a block, its grass grazed short
            int most = low + 1 + (int) Math.floor(e.getValue());
            if (g > most && RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z)))) {
                clear(level, x, most, z);
                level.setBlock(new BlockPos(x, most, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                g = most;
            }
            BlockPos over = new BlockPos(x, g + 1, z);
            BlockState plant = level.getBlockState(over);
            if (!plant.isAir() && plant.getFluidState().isEmpty() && RiverBankFeature.hash(x, z + 5) >= 0.03) {
                clear(level, x, g, z);
            }
        }
        wallow(level, random, water, shore, low, tone);
        // the water held: every side of it solid (a cave under the land would drain it)
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (Map.Entry<Long, Integer> e : water.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            for (int k = 0; k < e.getValue(); k++) {
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Integer theirs = water.get(BlockPos.asLong(x + side[0], 0, z + side[1]));
                    if (theirs != null && theirs > k) {
                        continue;
                    }
                    BlockPos at = new BlockPos(x + side[0], low - k, z + side[1]);
                    if (!level.getBlockState(at).isSolid()) {
                        level.setBlock(at, dirt, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return new Laid(new BlockPos(cx, low, cz), want, lobes.size() > 1, deepest, ring, waterBlocks);
    }

    /**
     * A wallow at one end of a pan (savanna mud, 2026-11-21, {@code docs/plans/savanna-mud.md}, section 5.1): soupy savanna
     * mud round a shore spot picked at random, on the shore and the ring outside it, soupy by the water and sticky at its
     * edge. The pan's water keeps it wet.
     */
    private static void wallow(WorldGenLevel level, RandomSource random, Map<Long, Integer> water, Map<Long, Double> shore, int low,
            SavannaMudBlock.Tone tone) {
        if (shore.isEmpty()) {
            return;
        }
        Long[] spots = shore.keySet().toArray(new Long[0]);
        java.util.Arrays.sort(spots);
        long spot = spots[random.nextInt(spots.length)];
        int sx = BlockPos.getX(spot), sz = BlockPos.getZ(spot);
        int reach = (int) Math.ceil(WALLOW_REACH);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = sx + dx, z = sz + dz;
                if (d > WALLOW_REACH || water.containsKey(BlockPos.asLong(x, 0, z))) {
                    continue;
                }
                int g = RiverBankFeature.ground(level, x, z, low + 4);
                if (g < low || g > low + 1 || !RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) && !(level.getBlockState(new BlockPos(x, g, z)).getBlock() instanceof SavannaMudBlock)
                        || !clearAbove(level, x, g, z)) {
                    continue;
                }
                clear(level, x, low, z);
                int mudDepth = d < 1.0 ? 3 : d < 2.0 ? 2 : 1;
                SavannaMudBlock.Wetness wetness = mudDepth == 1 ? SavannaMudBlock.Wetness.STICKY : SavannaMudBlock.Wetness.SOUPY;
                level.setBlock(new BlockPos(x, low, z), SavannaBlocks.SAVANNA_MUD.get().with(wetness, mudDepth, tone), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A plant a pan may clear: grass, flowers, bushes (Minecraft counts flowers as not replaceable); never a doum palm's part. */
    private static boolean plant(BlockState s) {
        return s.getFluidState().isEmpty() && !(s.getBlock() instanceof com.antcolonies.savanna.block.DoumPiece)
                && (s.canBeReplaced() || s.getBlock() instanceof net.minecraft.world.level.block.BushBlock);
    }

    /** Only plants (or nothing) stand on the ground here, up to 3 blocks over it. */
    private static boolean clearAbove(WorldGenLevel level, int x, int g, int z) {
        for (int y = g + 1; y <= g + 3; y++) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && !plant(s)) {
                return false;
            }
        }
        return true;
    }

    /** Takes the plants and the ground over {@code y} away, up to the column's top plus a few blocks. */
    private static void clear(WorldGenLevel level, int x, int y, int z) {
        int g = RiverBankFeature.ground(level, x, z, y + UNEVEN + 4);
        for (int k = y + 1; k <= Math.max(g, y) + 3; k++) {
            BlockPos at = new BlockPos(x, k, z);
            if (!level.getBlockState(at).isAir()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
