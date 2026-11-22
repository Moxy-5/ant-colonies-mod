package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * the river's 4-block low strip). Trim, don't refuse (the user, 2026-11-22, "it's okay if it's on a step or if it's
 * misshapen, that's the beauty of natural generation"): an oxbow too big for its room shrinks, and what is in the way is
 * left out of it (see {@link #lay}). The lake's water stands at the floodplain's lowest ground there, a block or two over the
 * river's (Minecraft-wise: the river's own decoration reads only sea-level water as the river, so the oxbow stays its own
 * lake; the eye does not see the step). Its lowest ground must be 1 to 3 over the river; then a column of its lake is
 * left out where something is in the way (a tree or log on it, rock, the river's low strip, a termite mound within
 * {@link #MOUND_CLEAR}, a floodplain or giant tree's foot within {@link #TREE_CLEAR}) and ground over {@link #STEP} above
 * the water stays as a bank; laid while {@link #KEEP_MOST} of the lake is left, never in a quiet savanna
 * ({@link BigSavanna}). A site with no room stays empty: oxbows form where the floodplain is wide.
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
    public record Laid(BlockPos middle, Age age, int across, int deepest, int water, int neck, int kept) {
    }

    /** What a world's oxbow or pan keeps off, column by column: termite mounds and tree feet (nothing in a test arena). */
    @FunctionalInterface
    public interface Keepout {
        Keepout NONE = (x, z) -> false;

        boolean at(int x, int z);
    }

    /** The share of an oxbow or pan that must be left once what is in the way is left out (my pick), else none is laid. */
    public static final double KEEP_MOST = 0.6;
    /** Ground up to this much over the water's level is cut down to it; higher stays as a bank, a step up. */
    static final int STEP = 3;

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
        Keepout keepout = keepout(level, bounds);
        Map<String, Integer> why = new java.util.TreeMap<>();
        for (int t = 0; t < TRIES; t++) {
            WHY.remove();
            Laid laid = lay(level, random, bounds, sea, 0, null, keepout);
            if (laid != null) {
                SavannaMod.LOGGER.info("[world oxbows] {} oxbow at {} {}: {} across, {} deep, {} water blocks, neck {}, {}% of its lake kept", laid.age(),
                        laid.middle().getX(), laid.middle().getZ(), laid.across(), laid.deepest(), laid.water(), laid.neck(), laid.kept());
                return true;
            }
            why.merge(String.valueOf(WHY.get()), 1, Integer::sum);
        }
        SavannaMod.LOGGER.info("[world oxbows] site at {} {}: no room beside the river {}", x0 + 8, z0 + 8, why);
        return false;
    }

    /**
     * Lays an oxbow beside the sea-level river water in {@code bounds} ({minX, minZ, maxX, maxZ}, everything read and
     * written stays inside): straight out from an edge of the water picked at random among those with room, its open side
     * to the river, with water at {@code sea} as the river's surface. Trim, don't refuse (the user, 2026-11-22): too big
     * for the chunks it may touch, it shrinks (never under {@link #ACROSS_MIN}); a column with something in the way (a
     * tree, a log, a rock, a termite mound, the river's low strip) is left out and the water goes round it; ground more than
     * {@link #STEP} over the water's level stays as a bank. Laid while {@link #KEEP_MOST} of the lake is left, else null
     * (nothing changed). {@code across} 0 and {@code age} null pick at random.
     */
    public static Laid lay(WorldGenLevel level, RandomSource random, int[] bounds, int sea, int across, Age age, Keepout keepout) {
        int size = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]) + 1;
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, bounds[0], bounds[1], size, sea);
        // the dice, always rolled in the same order
        int rolled = across > 0 ? across : ACROSS_MIN + random.nextInt(ACROSS_MAX - ACROSS_MIN + 1);
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
        double widthShare = 0.22 + 0.1 * random.nextDouble();
        Shape base = new Shape(0, 0, Math.toRadians(100 + 70 * random.nextDouble()), 0.75 + 0.55 * random.nextDouble(),
                (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 6.28, 0.05 + 0.12 * random.nextDouble(), (random.nextDouble() - 0.5) * 0.5);
        // the biggest size up to the one rolled that has a river edge with room (shrinking 2 at a time)
        Shape shape = null;
        int got = 0;
        List<double[]> fits = new ArrayList<>();
        for (int want = rolled; want >= ACROSS_MIN && fits.isEmpty(); want = want == ACROSS_MIN ? 0 : Math.max(ACROSS_MIN, want - 2)) {
            double w = Math.max(4, want * widthShare);
            shape = new Shape((want - w) / 2, w, base.half(), base.stretch(), base.tilt(), base.phase(), base.wob(), base.lop());
            for (int i = 0; i < 5; i++) {
                double[] box = localBox(shape, 1.0);
                double longest = Math.max(box[1] - box[0], box[3] - box[2]) + 1;
                if (Math.abs(longest - want) < 0.5) {
                    break;
                }
                shape = shape.withRc(Math.max(shape.w() / 2 + 1, shape.rc() + (want - longest) / 2));
            }
            double[] box = localBox(shape, 1.0);
            got = (int) Math.round(Math.max(box[1] - box[0], box[3] - box[2]) + 1);
            fits = edgesWithRoom(waters, bounds, localBox(shape, 1.5), neck);
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
        // what is in the way: each lake column open, soft, off the river's strip, clear of mounds and trees
        Map<Long, Integer> ground = new HashMap<>();
        java.util.Set<Long> open = new java.util.HashSet<>();
        int low = Integer.MAX_VALUE;
        for (long key : lake.keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            if (!inside(bounds, x, z) || keepout.at(x, z) || waters.distance(x, z, NECK_MIN) <= NECK_MIN) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, sea + 8);
            if (g <= sea || !RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
                continue;
            }
            ground.put(key, g);
            open.add(key);
            low = Math.min(low, g);
        }
        if (open.isEmpty()) {
            return no("everything in the way");
        }
        if (low > sea + STEP) {
            return no("ground too high");
        }
        // higher ground stays as a bank
        final int water0 = low;
        open.removeIf(key -> ground.get(key) > water0 + STEP);
        java.util.Set<Long> kept = biggestPatch(open);
        int keptShare = (int) Math.round(100.0 * kept.size() / lake.size());
        if (kept.size() < KEEP_MOST * lake.size()) {
            return no("too much in the way");
        }
        double nearest = Double.MAX_VALUE;
        for (long key : kept) {
            nearest = Math.min(nearest, waters.distance(BlockPos.getX(key), BlockPos.getZ(key), 20));
        }
        if (nearest > 20) {
            return no("not beside the river");
        }
        // lay it
        BlockState mud = SavannaBlocks.RIVER_MUD.get().defaultBlockState(), silt = SavannaBlocks.SILT.get().defaultBlockState();
        BlockState wetSilt = SavannaBlocks.WET_SILT.get().defaultBlockState(), water = Blocks.WATER.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        Map<Long, Integer> depths = new HashMap<>();
        int deepest = 0, waterBlocks = 0;
        for (long key : kept) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            Spot s = lake.get(key);
            int depth = depth(theAge, s);
            clear(level, x, low, z);
            if (depth > 0) {
                for (int k = 0; k < depth; k++) {
                    level.setBlock(new BlockPos(x, low - k, z), water, Block.UPDATE_CLIENTS);
                }
                level.setBlock(new BlockPos(x, low - depth, z), RiverBankFeature.hash(x, z) < 0.45 ? mud : silt, Block.UPDATE_CLIENTS);
                depths.put(key, depth);
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
            if (!inside(bounds, x, z) || keepout.at(x, z)) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, sea + 8);
            if (g > low + STEP || !RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
                continue; // a bank a step up, or something on it: left as it is
            }
            if (theAge == Age.SCAR) {
                cutTo(level, x, z, g, low + 1);
                continue;
            }
            // a bank of mud and wet silt round the water: flush with it, a fresh oxbow's a block over it
            int top = theAge == Age.FRESH ? Math.max(low, Math.min(g, low + 1)) : low;
            clear(level, x, top, z);
            for (int y = g + 1; y < top; y++) {
                level.setBlock(new BlockPos(x, y, z), dirt, Block.UPDATE_CLIENTS);
            }
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
        holdWater(level, depths, low);
        return new Laid(BlockPos.containing(cx, low, cz), theAge, got, deepest, waterBlocks, neck, keptShare);
    }

    /**
     * The river edges an oxbow with this bank box ({@link #localBox}) may be laid out from: the middle of each, and the
     * way out from the river there; only those whose whole oxbow stays inside {@code bounds} with a tree's foot to spare.
     */
    private static List<double[]> edgesWithRoom(RiverBankFeature.Waters waters, int[] bounds, double[] bank, int neck) {
        double out = neck + 1 - bank[2]; // from the river's edge to the oxbow's middle, along the way out from the river
        int margin = TREE_CLEAR;
        List<double[]> fits = new ArrayList<>();
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
                boolean in = true;
                for (double sd : new double[] {bank[0], bank[1]}) {
                    for (double aw : new double[] {bank[2], bank[3]}) {
                        double wxp = cx + sd * nz + aw * nx, wzp = cz - sd * nx + aw * nz;
                        in &= wxp >= bounds[0] + margin && wxp <= bounds[2] - margin && wzp >= bounds[1] + margin && wzp <= bounds[3] - margin;
                    }
                }
                if (in) {
                    fits.add(new double[] {cx, cz, nx, nz});
                }
            }
        }
        return fits;
    }

    /** Whether a column is inside the chunks a feature may touch, with a tree's foot to spare. */
    static boolean inside(int[] bounds, int x, int z) {
        return x >= bounds[0] + TREE_CLEAR && x <= bounds[2] - TREE_CLEAR && z >= bounds[1] + TREE_CLEAR && z <= bounds[3] - TREE_CLEAR;
    }

    /** The biggest patch of these columns joined side to side (what is left once things in the way are left out). */
    static java.util.Set<Long> biggestPatch(java.util.Set<Long> cells) {
        java.util.Set<Long> best = new java.util.HashSet<>(), seen = new java.util.HashSet<>();
        for (long start : cells) {
            if (!seen.add(start)) {
                continue;
            }
            java.util.Set<Long> patch = new java.util.HashSet<>();
            java.util.ArrayDeque<Long> todo = new java.util.ArrayDeque<>();
            todo.add(start);
            while (!todo.isEmpty()) {
                long c = todo.poll();
                patch.add(c);
                int x = BlockPos.getX(c), z = BlockPos.getZ(c);
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long n = BlockPos.asLong(x + side[0], 0, z + side[1]);
                    if (cells.contains(n) && seen.add(n)) {
                        todo.add(n);
                    }
                }
            }
            if (patch.size() > best.size()) {
                best = patch;
            }
        }
        return best;
    }

    /** The water held: every side of it, and the bed, solid (a cave, a dip or a left-out column beside it would drain it). */
    static void holdWater(WorldGenLevel level, Map<Long, Integer> depths, int low) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (Map.Entry<Long, Integer> e : depths.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            for (int k = 0; k < e.getValue(); k++) {
                for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Integer theirs = depths.get(BlockPos.asLong(x + side[0], 0, z + side[1]));
                    if (theirs != null && theirs > k) {
                        continue; // water there too
                    }
                    BlockPos at = new BlockPos(x + side[0], low - k, z + side[1]);
                    BlockState there = level.getBlockState(at);
                    if (!there.isSolid()) {
                        level.setBlock(at, dirt, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
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

    /**
     * What a world's oxbow or pan in these chunks keeps off: within {@link #MOUND_CLEAR} blocks of a termite mound's place
     * that holds a mound (by the mounds' own pattern, made yet or not), and within {@link #TREE_CLEAR} of a floodplain or
     * giant tree's foot (the trees are structures put in after the lakes, and grow nothing into water, so a tree standing in
     * the water would stand cut off). The trees read are those started in these chunks (one started farther off stands
     * farther off than {@link #TREE_CLEAR}: the water keeps that far inside them).
     */
    static Keepout keepout(WorldGenLevel level, int[] bounds) {
        List<BlockPos> mounds = new ArrayList<>(), feet = new ArrayList<>();
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(MoundPlaces.STRUCTURE);
        if (structure instanceof TermiteMoundStructure moundStructure) {
            MoundPlaces.Numbers places = moundStructure.numbers().places();
            MoundPlaces.Land land = MoundPlaces.Land.of(level.getLevel(), moundStructure.biomes());
            for (int cx = (bounds[0] - MOUND_CLEAR) >> 4; cx <= (bounds[2] + MOUND_CLEAR) >> 4; cx++) {
                for (int cz = (bounds[1] - MOUND_CLEAR) >> 4; cz <= (bounds[3] + MOUND_CLEAR) >> 4; cz++) {
                    MoundPlaces.Place place = MoundPlaces.candidate(level.getSeed(), cx, cz, places);
                    BlockPos middle = place.middle();
                    if (middle.getX() >= bounds[0] - MOUND_CLEAR && middle.getX() <= bounds[2] + MOUND_CLEAR
                            && middle.getZ() >= bounds[1] - MOUND_CLEAR && middle.getZ() <= bounds[3] + MOUND_CLEAR
                            && MoundPlaces.isPlace(level.getSeed(), cx, cz, places) && MoundPlaces.holds(land, place, places) != null) {
                        mounds.add(middle);
                    }
                }
            }
        }
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
        if (mounds.isEmpty() && feet.isEmpty()) {
            return Keepout.NONE;
        }
        return (x, z) -> {
            for (BlockPos m : mounds) {
                double ddx = m.getX() - x, ddz = m.getZ() - z;
                if (ddx * ddx + ddz * ddz <= MOUND_CLEAR * MOUND_CLEAR) {
                    return true;
                }
            }
            for (BlockPos f : feet) {
                if (Math.abs(f.getX() - x) <= TREE_CLEAR && Math.abs(f.getZ() - z) <= TREE_CLEAR) {
                    return true;
                }
            }
            return false;
        };
    }
}
