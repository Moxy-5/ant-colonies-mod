package com.antcolonies.savanna.worldgen;

import java.util.HashMap;
import java.util.Map;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * New land's oxbow lakes (oxbows and pans rework step 3, 2026-11-22, {@code docs/plans/oxbows-and-pans.md}): a river bend
 * cut off when the river took a shortcut, left as a crescent lake beside the river with its two ends pointing back at it
 * and a neck of floodplain between. The shapes are the model viewer's, which the user liked
 * ({@code tools/oxbow_viewer.template.html}: every one its own, 100 to 170 degrees round, squashed or drawn out, turned a
 * little, wavy banks, one arm wider), at the size the user set: <b>16 to 32 blocks across, at random</b> (the user,
 * 2026-11-22). Four ages (my picks): fresh 3 in 10 (open water 4 deep, steep banks a block over the water), silting 4 in 10
 * (3 deep in the middle, its ends dried to mud flats), old 2 in 10 (1 to 2 deep in the middle, mud all round), scar 1 in 10
 * (no water: a crescent of grass and mud in the plain).
 *
 * <p>On an oxbow site ({@link OxbowSites}) the chunk tries {@link #TRIES} spots: from each it finds the nearest river water
 * and lays the oxbow with its open side to the river, {@link #NECK_MIN} to {@link #NECK_MAX} blocks of land between (past
 * the river's 4-block low strip). The lake's water stands at the floodplain's lowest ground there, a block or two over the
 * river's (Minecraft-wise: the river's own decoration reads only sea-level water as the river, so the oxbow stays its own
 * lake; the eye does not see the step). It goes only on level floodplain (within 2 blocks, its lowest ground 1 or 2 over
 * the river), soft ground with nothing but plants on it, no termite mound within {@link #MOUND_CLEAR} blocks, no floodplain
 * or giant tree's foot within {@link #TREE_CLEAR} blocks, all of it inside the 3 by 3 chunks a feature may write, never in
 * a quiet savanna ({@link BigSavanna}). A site with no room stays empty: oxbows form where the floodplain is wide.
 */
public class OxbowFeature extends Feature<NoneFeatureConfiguration> {
    /** The size across, the oxbow's longest side, picked at random between these (the user, 2026-11-22). */
    public static final int ACROSS_MIN = 16, ACROSS_MAX = 32;
    /** Blocks of floodplain between the oxbow's ends and the river (my pick; past the river's 4-block low strip). */
    public static final int NECK_MIN = 5, NECK_MAX = 8;
    /** Spots a site chunk tries before giving up. */
    static final int TRIES = 8;
    /** No termite mound place's middle this close (a mound's foot reaches about 13 from it). */
    static final int MOUND_CLEAR = 15;
    /** No tree's foot this close to the water or its bank. */
    static final int TREE_CLEAR = 3;
    /** How far the bank round the lake is cut down to a block over the water. */
    static final double RING = 4;

    public enum Age { FRESH, SILTING, OLD, SCAR }

    /** The share of each age, in order (my picks). */
    static final double[] AGE_SHARE = {0.3, 0.4, 0.2, 0.1};

    /** The model viewer's shape numbers: loop radius, lake width, how far round, squash, turn, wobble, one arm wider. */
    record Shape(double rc, double w, double half, double stretch, double tilt, double phase, double wob, double lop) {
        Shape withRc(double r) {
            return new Shape(r, w, half, stretch, tilt, phase, wob, lop);
        }
    }

    /** Where a column lies in the oxbow: {@code along} 0 in the middle to 1 at an end, {@code u} 0 down the lake's middle to 1 at its edge, {@code out} blocks past the edge. */
    record Spot(double along, double u, double out) {
    }

    /** What was laid. */
    public record Laid(BlockPos middle, Age age, int across, int deepest, int water, int neck) {
    }

    /** What a world's oxbow must keep clear of (the mounds and trees; none in a test arena). */
    @FunctionalInterface
    public interface Guard {
        boolean refuses(int minX, int minZ, int maxX, int maxZ, Iterable<Long> cells);
    }

    public OxbowFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        var source = level.getLevel().getChunkSource();
        if (!OxbowSites.site(source.getGenerator().getBiomeSource(), source.randomState().sampler(), level.getSeed(), x0 >> 4, z0 >> 4)) {
            return false;
        }
        int sea = level.getSeaLevel() - 1;
        if (!BigSavanna.lives(level.getLevel(), level.getBiome(new BlockPos(x0 + 8, sea, z0 + 8)), x0 + 8, z0 + 8)) {
            return false;
        }
        RandomSource random = context.random();
        int[] bounds = {x0 - 16, z0 - 16, x0 + 31, z0 + 31};
        Guard guard = (minX, minZ, maxX, maxZ, cells) -> nearMound(level, minX, minZ, maxX, maxZ) || nearTree(level, bounds, cells);
        Map<String, Integer> why = new java.util.TreeMap<>();
        for (int t = 0; t < TRIES; t++) {
            WHY.remove();
            Laid laid = lay(level, random, bounds, sea, 0, null, guard);
            if (laid != null) {
                SavannaMod.LOGGER.info("[world oxbows] {} oxbow at {} {}: {} across, {} deep, {} water blocks, neck {}", laid.age(),
                        laid.middle().getX(), laid.middle().getZ(), laid.across(), laid.deepest(), laid.water(), laid.neck());
                return true;
            }
            why.merge(String.valueOf(WHY.get()), 1, Integer::sum);
        }
        SavannaMod.LOGGER.info("[world oxbows] site at {} {}: no room beside the river {}", x0 + 8, z0 + 8, why);
        return false;
    }

    /**
     * Lays an oxbow beside the sea-level river water in {@code bounds} ({minX, minZ, maxX, maxZ}, everything read and
     * written stays inside), if it fits: straight out from an edge of the water picked at random among those with room,
     * its open side to the river, with water at {@code sea} as the river's surface. {@code across} 0 and {@code age} null pick at random; {@code guard} null keeps clear of nothing more.
     * Returns what was laid, or null (nothing changed).
     */
    public static Laid lay(WorldGenLevel level, RandomSource random, int[] bounds, int sea, int across, Age age, Guard guard) {
        int size = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]) + 1;
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, bounds[0], bounds[1], size, sea);
        // the dice, always rolled in the same order
        int want = across > 0 ? across : ACROSS_MIN + random.nextInt(ACROSS_MAX - ACROSS_MIN + 1);
        double roll = random.nextDouble();
        Age picked = Age.SCAR;
        for (int i = 0; i < AGE_SHARE.length; i++) {
            if (roll < cumulative(i)) {
                picked = Age.values()[i];
                break;
            }
        }
        Age theAge = age != null ? age : picked;
        int neck = NECK_MIN + random.nextInt(NECK_MAX - NECK_MIN + 1);
        double w = Math.max(4, Math.min(want * 0.32, want * (0.22 + 0.1 * random.nextDouble())));
        Shape shape = new Shape((want - w) / 2, w, Math.toRadians(100 + 70 * random.nextDouble()), 0.75 + 0.55 * random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 6.28, 0.05 + 0.12 * random.nextDouble(), (random.nextDouble() - 0.5) * 0.5);
        // fit the loop to the size wanted: its longest side, counted in its own frame
        double[] box = null;
        for (int i = 0; i < 5; i++) {
            box = localBox(shape, 1.0);
            double longest = Math.max(box[1] - box[0], box[3] - box[2]) + 1;
            if (Math.abs(longest - want) < 0.5) {
                break;
            }
            shape = shape.withRc(Math.max(shape.w() / 2 + 1, shape.rc() + (want - longest) / 2));
        }
        box = localBox(shape, 1.0);
        int got = (int) Math.round(Math.max(box[1] - box[0], box[3] - box[2]) + 1);
        double[] bank = localBox(shape, 1.5);
        double out = neck + 1 - bank[2]; // from the river's edge to the oxbow's middle, along the way out from the river
        // where along the river: an edge of the river water whose oxbow, laid straight out from it, stays inside the
        // chunks it may touch (one picked at random among those)
        int margin = TREE_CLEAR;
        java.util.List<double[]> fits = new java.util.ArrayList<>();
        for (int x = bounds[0]; x <= bounds[2]; x++) {
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                if (!waters.isWater(x, z) || waters.isWater(x + 1, z) && waters.isWater(x - 1, z) && waters.isWater(x, z + 1) && waters.isWater(x, z - 1)) {
                    continue;
                }
                // the way out from the river here: toward the land round this edge
                double nx = 0, nz = 0;
                for (int ax = -5; ax <= 5; ax++) {
                    for (int az = -5; az <= 5; az++) {
                        if ((ax != 0 || az != 0) && ax * ax + az * az <= 25 && !waters.isWater(x + ax, z + az)) {
                            double l = Math.sqrt(ax * ax + az * az);
                            nx += ax / l;
                            nz += az / l;
                        }
                    }
                }
                double nl = Math.sqrt(nx * nx + nz * nz);
                if (nl < 1) {
                    continue;
                }
                nx /= nl;
                nz /= nl;
                double cx = x + 0.5 + nx * out, cz = z + 0.5 + nz * out;
                boolean inside = true;
                for (double sd : new double[] {bank[0], bank[1]}) {
                    for (double aw : new double[] {bank[2], bank[3]}) {
                        double wxp = cx + sd * nz + aw * nx, wzp = cz - sd * nx + aw * nz;
                        inside &= wxp >= bounds[0] + margin && wxp <= bounds[2] - margin && wzp >= bounds[1] + margin && wzp <= bounds[3] - margin;
                    }
                }
                if (inside) {
                    fits.add(new double[] {cx, cz, nx, nz});
                }
            }
        }
        if (fits.isEmpty()) {
            return no("no river edge with room");
        }
        double[] pick = fits.get(random.nextInt(fits.size()));
        double cx = pick[0], cz = pick[1], dx = pick[2], dz = pick[3];
        // the columns, in the world: lake, bank (the rim) and the ring past it
        Map<Long, Spot> lake = new HashMap<>(), rim = new HashMap<>(), ring = new HashMap<>();
        int reach = (int) Math.ceil((shape.rc() + shape.w()) * Math.max(1, shape.stretch()) + RING + 2);
        for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.floor(cx) + reach; x++) {
            for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.floor(cz) + reach; z++) {
                double rx = x + 0.5 - cx, rz = z + 0.5 - cz;
                Spot s = spot(shape, rx * dz - rz * dx, rx * dx + rz * dz);
                if (s == null) {
                    continue;
                }
                long key = BlockPos.asLong(x, 0, z);
                if (s.u() <= 1) {
                    lake.put(key, s);
                } else if (s.out() < 1.5) {
                    rim.put(key, s);
                } else if (s.out() < RING) {
                    ring.put(key, s);
                }
            }
        }
        if (lake.isEmpty()) {
            return no("no lake");
        }
        // inside the chunks it may touch, with room to spare for a tree's foot outside them
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map<Long, Spot> part : java.util.List.of(lake, rim)) {
            for (long key : part.keySet()) {
                int x = BlockPos.getX(key), z = BlockPos.getZ(key);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        if (minX < bounds[0] + TREE_CLEAR || maxX > bounds[2] - TREE_CLEAR || minZ < bounds[1] + TREE_CLEAR || maxZ > bounds[3] - TREE_CLEAR) {
            return no("past the chunks it may touch");
        }
        // off the river's low strip, and beside the river (its ends near the water)
        double nearest = Double.MAX_VALUE;
        for (long key : lake.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            double d = waters.distance(x, z, NECK_MAX + 6);
            if (d <= NECK_MIN) {
                return no("water too near the lake");
            }
            nearest = Math.min(nearest, d);
        }
        if (nearest > NECK_MAX + 4) {
            return no("not beside the river");
        }
        for (long key : rim.keySet()) {
            if (waters.distance(BlockPos.getX(key), BlockPos.getZ(key), (int) RiverBankFeature.STRIP) <= RiverBankFeature.STRIP) {
                return no("bank on the low strip");
            }
        }
        // level, soft, open floodplain a block or two over the river
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (Map<Long, Spot> part : java.util.List.of(lake, rim)) {
            for (long key : part.keySet()) {
                int x = BlockPos.getX(key), z = BlockPos.getZ(key);
                int g = RiverBankFeature.ground(level, x, z, sea + 8);
                if (!RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z)))) {
                    return no("not soft ground: " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, g, z)).getBlock()).getPath());
                }
                if (!clearAbove(level, x, g, z)) {
                    return no("something on the ground: " + onTop(level, x, g, z));
                }
                low = Math.min(low, g);
                high = Math.max(high, g);
            }
        }
        if (low <= sea || low > sea + 2 || high - low > 2) {
            return no(low <= sea ? "ground at the river's level" : low > sea + 2 ? "ground too high" : "not level");
        }
        java.util.List<Long> footprint = new java.util.ArrayList<>(lake.keySet());
        footprint.addAll(rim.keySet());
        if (guard != null && guard.refuses(minX, minZ, maxX, maxZ, footprint)) {
            return no("a termite mound or tree");
        }
        // lay it
        BlockState mud = SavannaBlocks.RIVER_MUD.get().defaultBlockState(), silt = SavannaBlocks.SILT.get().defaultBlockState();
        BlockState wetSilt = SavannaBlocks.WET_SILT.get().defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        Map<Long, Integer> depths = new HashMap<>();
        int deepest = 0, waterBlocks = 0;
        for (Map.Entry<Long, Spot> e : lake.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            Spot s = e.getValue();
            int depth = depth(theAge, s);
            clear(level, x, low, z);
            if (depth > 0) {
                for (int k = 0; k < depth; k++) {
                    level.setBlock(new BlockPos(x, low - k, z), water, Block.UPDATE_CLIENTS);
                }
                level.setBlock(new BlockPos(x, low - depth, z), RiverBankFeature.hash(x, z) < 0.45 ? mud : silt, Block.UPDATE_CLIENTS);
                depths.put(e.getKey(), depth);
                deepest = Math.max(deepest, depth);
                waterBlocks += depth;
            } else if (theAge == Age.SCAR) {
                // a crescent of grass and mud patches in the plain, tufts of grass on it
                boolean patch = RiverBankFeature.hash(x, z) < 0.15;
                level.setBlock(new BlockPos(x, low, z), patch ? mud : Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (!patch && RiverBankFeature.hash(x + 1, z + 2) < 0.35) {
                    level.setBlock(new BlockPos(x, low + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            } else {
                // mud flats where it silted up
                level.setBlock(new BlockPos(x, low, z), RiverBankFeature.hash(x, z) < 0.55 ? mud : wetSilt, Block.UPDATE_CLIENTS);
            }
        }
        for (long key : rim.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            int g = RiverBankFeature.ground(level, x, z, sea + 8);
            if (theAge == Age.SCAR) {
                cutTo(level, x, z, g, low + 1);
                continue;
            }
            // a bank of mud and wet silt round the water: flush with it, a fresh oxbow's a block over it
            int top = theAge == Age.FRESH ? Math.min(g, low + 1) : low;
            clear(level, x, top, z);
            level.setBlock(new BlockPos(x, top, z), RiverBankFeature.hash(x + 2, z) < 0.6 ? mud : wetSilt, Block.UPDATE_CLIENTS);
        }
        for (long key : ring.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            if (x < bounds[0] || x > bounds[2] || z < bounds[1] || z > bounds[3]) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, sea + 8);
            if (g > low + 1 && g <= low + 3 && RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) && clearAbove(level, x, g, z)) {
                cutTo(level, x, z, g, low + 1);
            }
        }
        // the water held: every side of it, and under the bed, solid (a cave under the plain would drain it)
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (Map.Entry<Long, Integer> e : depths.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            for (int k = 0; k <= e.getValue(); k++) {
                int y = low - k;
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) {
                    int sx = x + side[0], sz = z + side[1];
                    if (side[0] == 0 && side[1] == 0 && k < e.getValue()) {
                        continue;
                    }
                    Integer theirs = depths.get(BlockPos.asLong(sx, 0, sz));
                    if (theirs != null && theirs > k) {
                        continue; // water there too
                    }
                    BlockPos at = side[0] == 0 && side[1] == 0 ? new BlockPos(x, y - 1, z) : new BlockPos(sx, y, sz);
                    if (!level.getBlockState(at).isSolid()) {
                        level.setBlock(at, dirt, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return new Laid(BlockPos.containing(cx, low, cz), theAge, got, deepest, waterBlocks, neck);
    }

    /** Why the last try on this thread laid nothing (for the log). */
    private static final ThreadLocal<String> WHY = new ThreadLocal<>();

    private static Laid no(String why) {
        WHY.set(why);
        return null;
    }

    /** The running share of the ages up to and including {@code i}. */
    private static double cumulative(int i) {
        double sum = 0;
        for (int k = 0; k <= i; k++) {
            sum += AGE_SHARE[k];
        }
        return sum;
    }

    /** How deep the water stands over a column of the lake, by the oxbow's age (the model viewer's rules). */
    static int depth(Age age, Spot s) {
        double u = s.u(), along = s.along();
        return switch (age) {
            case FRESH -> Math.max(1, (int) Math.ceil(4 * (1 - u * u * u)));
            case SILTING -> along < 0.8 ? Math.max(1, (int) Math.round(3 * (1 - u * u) * (1 - 0.7 * along * along))) : 0;
            case OLD -> along < 0.45 && u < 0.75 ? (along < 0.25 && u < 0.4 ? 2 : 1) : 0;
            case SCAR -> 0;
        };
    }

    /**
     * Where a point lies in the oxbow, in its own frame: {@code side} across, {@code away} out from the river; null past
     * its ends (the viewer's {@code inOxbow}).
     */
    static Spot spot(Shape o, double side, double away) {
        double ct = Math.cos(o.tilt()), st = Math.sin(o.tilt());
        double sx = (side * ct - away * st) / o.stretch(), sz = side * st + away * ct;
        double a = Math.atan2(sx, sz), r = Math.hypot(sx, sz); // a = 0 straight away from the river
        double along = Math.abs(a) / o.half();
        if (along > 1.15) {
            return null;
        }
        double hw = o.w() / 2 * (1 + o.wob() * Math.sin(a * 5 + o.phase())) * (1 + o.lop() * a / o.half());
        if (along > 0.82) {
            hw *= Math.sqrt(Math.max(0, 1 - Math.pow((along - 0.82) / 0.18, 2))); // rounded ends
        }
        double across = Math.abs(r - o.rc());
        return new Spot(Math.min(along, 1), hw > 0.01 ? across / hw : 99, across - hw);
    }

    /** The oxbow's box in its own frame ({minSide, maxSide, minAway, maxAway}): the lake ({@code out} 1) or it and its bank (1.5). */
    static double[] localBox(Shape o, double out) {
        int reach = (int) Math.ceil((o.rc() + o.w()) * Math.max(1, o.stretch()) + 3);
        double[] box = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                Spot s = spot(o, x, z);
                if (s != null && (out <= 1 ? s.u() <= 1 : s.out() < out)) {
                    box[0] = Math.min(box[0], x); box[1] = Math.max(box[1], x);
                    box[2] = Math.min(box[2], z); box[3] = Math.max(box[3], z);
                }
            }
        }
        return box[0] == Double.MAX_VALUE ? new double[] {0, 0, 0, 0} : box;
    }

    /** Ground over {@code top} taken down to it, its top block grass. */
    private static void cutTo(WorldGenLevel level, int x, int z, int g, int top) {
        if (g <= top) {
            return;
        }
        clear(level, x, top, z);
        level.setBlock(new BlockPos(x, top, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** A plant the oxbow may clear: grass, flowers, bushes (Minecraft counts flowers as not replaceable); never a doum palm's part. */
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

    /** The first block over the ground that is not a plant or air (for the log). */
    private static String onTop(WorldGenLevel level, int x, int g, int z) {
        for (int y = g + 1; y <= g + 3; y++) {
            BlockState s = level.getBlockState(new BlockPos(x, y, z));
            if (!s.isAir() && !plant(s)) {
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            }
        }
        return "?";
    }

    /** Takes the plants and the ground over {@code y} away, up to the column's top plus a few blocks. */
    private static void clear(WorldGenLevel level, int x, int y, int z) {
        int g = RiverBankFeature.ground(level, x, z, y + 6);
        for (int k = y + 1; k <= Math.max(g, y) + 3; k++) {
            BlockPos at = new BlockPos(x, k, z);
            if (!level.getBlockState(at).isAir()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Whether a termite mound stands, or will stand, within {@link #MOUND_CLEAR} blocks of the box (by the mounds' own pattern). */
    static boolean nearMound(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(MoundPlaces.STRUCTURE);
        if (!(structure instanceof TermiteMoundStructure mounds)) {
            return false;
        }
        MoundPlaces.Numbers places = mounds.numbers().places();
        MoundPlaces.Land land = MoundPlaces.Land.of(level.getLevel(), mounds.biomes());
        for (int cx = (minX - MOUND_CLEAR) >> 4; cx <= (maxX + MOUND_CLEAR) >> 4; cx++) {
            for (int cz = (minZ - MOUND_CLEAR) >> 4; cz <= (maxZ + MOUND_CLEAR) >> 4; cz++) {
                MoundPlaces.Place place = MoundPlaces.candidate(level.getSeed(), cx, cz, places);
                BlockPos middle = place.middle();
                if (middle.getX() >= minX - MOUND_CLEAR && middle.getX() <= maxX + MOUND_CLEAR
                        && middle.getZ() >= minZ - MOUND_CLEAR && middle.getZ() <= maxZ + MOUND_CLEAR
                        && MoundPlaces.isPlace(level.getSeed(), cx, cz, places) && MoundPlaces.holds(land, place, places) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a floodplain or giant tree of new land has its foot within {@link #TREE_CLEAR} blocks of these columns: the
     * trees are structures, put in after the lakes, and grow nothing into water, so a tree standing in the oxbow would
     * stand cut off. Reads the trees started in the chunks the oxbow may touch (one started farther off stands farther
     * off than {@link #TREE_CLEAR}: the oxbow keeps that far inside them).
     */
    static boolean nearTree(WorldGenLevel level, int[] bounds, Iterable<Long> cells) {
        java.util.List<BlockPos> feet = new java.util.ArrayList<>();
        for (int cx = bounds[0] >> 4; cx <= bounds[2] >> 4; cx++) {
            for (int cz = bounds[1] >> 4; cz <= bounds[3] >> 4; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (StructureStart start : chunk.getAllStarts().values()) {
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece instanceof FloodplainTreePiece tree) {
                            feet.add(tree.tree().foot());
                        } else if (piece instanceof GiantTreePiece giant) {
                            feet.add(giant.foot());
                        }
                    }
                }
            }
        }
        if (feet.isEmpty()) {
            return false;
        }
        for (long key : cells) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            for (BlockPos foot : feet) {
                if (Math.abs(foot.getX() - x) <= TREE_CLEAR && Math.abs(foot.getZ() - z) <= TREE_CLEAR) {
                    return true;
                }
            }
        }
        return false;
    }
}
