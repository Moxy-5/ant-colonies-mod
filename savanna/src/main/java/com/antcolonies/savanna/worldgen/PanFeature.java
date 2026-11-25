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
 * <p>On a pan site ({@link PanSites}) the chunk tries {@link #TRIES} spots round its middle. Trim, don't refuse (the user,
 * 2026-11-22): a pan too big for its room shrinks; a column with something in the way is left out (see {@link #lay});
 * never in a quiet savanna. Numbers my picks unless marked.
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
    public record Laid(BlockPos middle, int across, boolean twoBowls, int deepest, int ring, int water, int kept) {
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
        OxbowFeature.Keepout keepout = OxbowFeature.keepout(level, bounds);
        Map<String, Integer> why = new java.util.TreeMap<>();
        for (int t = 0; t < TRIES; t++) {
            WHY.remove();
            int px = x0 + 4 + random.nextInt(9), pz = z0 + 4 + random.nextInt(9);
            // the land's own ground there, from before any tree or plant (the lakes come first)
            int lookFrom = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, px, pz) + UNEVEN + 4;
            Laid laid = lay(level, random, px, pz, bounds, sea, lookFrom, 0, tone, keepout);
            if (laid != null) {
                SavannaMod.LOGGER.info("[world pans] pan at {} {}: {} across{}, {} deep, ring {}, {} water blocks, {}% of its water kept", laid.middle().getX(),
                        laid.middle().getZ(), laid.across(), laid.twoBowls() ? " (two bowls)" : "", laid.deepest(), laid.ring(), laid.water(), laid.kept());
                // its herd of Cape buffalo, grazing past its ring (as an oxbow's hippo pod)
                BuffaloHerds.Herd herd = BuffaloHerds.lay(level, random, laid, bounds, keepout);
                SavannaMod.LOGGER.info("[world herds] pan at {} {}: {}", laid.middle().getX(), laid.middle().getZ(), BuffaloHerds.words(herd));
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
     * Lays a pan round {@code cx, cz}, everything read and written inside {@code bounds} ({minX, minZ, maxX, maxZ});
     * {@code sea} the sea-level water's top; every column's ground read down from {@code lookFrom} (a few blocks over the
     * middle's ground: never from a column's own top, which may be a tree's crown or a test's barrier roof). Trim, don't
     * refuse (the user, 2026-11-22): too big for the chunks it may touch, it shrinks (never under {@link #ACROSS_MIN}); a
     * column with something in the way (a tree, a log, a rock, a termite mound, water within {@link #FROM_WATER}) is left
     * out and the water goes round it; ground more than {@link #UNEVEN} over the water's level stays as a bank. Laid while
     * {@link OxbowFeature#KEEP_MOST} of its water is left, else null (nothing changed). {@code across} 0 picks at random.
     */
    public static Laid lay(WorldGenLevel level, RandomSource random, int cx, int cz, int[] bounds, int sea, int lookFrom, int across,
            SavannaMudBlock.Tone tone, OxbowFeature.Keepout keepout) {
        int rolled = across > 0 ? across : ACROSS_MIN + random.nextInt(ACROSS_MAX - ACROSS_MIN + 1);
        // the shape's dice, for a pan of radius 1 (scaled to its size below)
        List<Lobe> unit = new ArrayList<>();
        unit.add(new Lobe(0, 0, 1, 1 + 0.45 * random.nextDouble(), random.nextDouble() * Math.PI, random.nextDouble() * 6.28,
                random.nextDouble() * 6.28, 0.04 + 0.08 * random.nextDouble()));
        if (random.nextDouble() < 0.4) {
            double a = random.nextDouble() * 6.28, rr = 0.45 + 0.25 * random.nextDouble();
            unit.add(new Lobe(Math.cos(a) * 0.8, Math.sin(a) * 0.8, rr, 1 + 0.3 * random.nextDouble(), random.nextDouble() * Math.PI,
                    random.nextDouble() * 6.28, random.nextDouble() * 6.28, 0.06));
        }
        double ringDice = random.nextDouble();
        // the biggest size up to the one rolled whose water, shore and ring fit the chunks it may touch (shrinking 2 at a time)
        Cells cells = null;
        int want = rolled;
        for (; want >= ACROSS_MIN; want -= 2) {
            cells = cells(unit, want / 2.0, ringDice, cx, cz);
            if (cells.fits(bounds)) {
                break;
            }
        }
        if (want < ACROSS_MIN) {
            return no("past the chunks it may touch");
        }
        // what is in the way: each water column open, soft, clear of water, mounds and trees
        int size = Math.max(bounds[2] - bounds[0], bounds[3] - bounds[1]) + 1;
        RiverBankFeature.Waters waters = new RiverBankFeature.Waters(level, bounds[0], bounds[1], size, sea);
        Map<Long, Integer> ground = new HashMap<>();
        java.util.Set<Long> open = new java.util.HashSet<>();
        int low = Integer.MAX_VALUE;
        for (long key : cells.water().keySet()) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            if (!OxbowFeature.inside(bounds, x, z) || keepout.at(x, z) || waters.distance(x, z, FROM_WATER) <= FROM_WATER) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, lookFrom);
            if (!RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
                continue;
            }
            ground.put(key, g);
            open.add(key);
            low = Math.min(low, g);
        }
        if (open.isEmpty()) {
            return no("everything in the way");
        }
        if (low <= sea) {
            return no("too low");
        }
        final int level0 = low;
        open.removeIf(key -> ground.get(key) > level0 + UNEVEN); // higher ground stays as a bank
        java.util.Set<Long> kept = OxbowFeature.biggestPatch(open);
        int keptShare = (int) Math.round(100.0 * kept.size() / cells.water().size());
        if (kept.size() < OxbowFeature.KEEP_MOST * cells.water().size()) {
            return no("too much in the way");
        }
        // lay it
        BlockState mud = SavannaBlocks.RIVER_MUD.get().defaultBlockState(), wetSilt = SavannaBlocks.WET_SILT.get().defaultBlockState();
        BlockState waterState = Blocks.WATER.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockState shoreMud = SavannaBlocks.SAVANNA_MUD.get().with(SavannaMudBlock.Wetness.STICKY, 1, tone);
        BlockState dryMud = SavannaBlocks.SAVANNA_MUD.get().with(SavannaMudBlock.Wetness.DRYING, 1, tone);
        Map<Long, Integer> depths = new HashMap<>();
        int deepest = 0, waterBlocks = 0;
        for (long key : kept) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key), depth = cells.water().get(key);
            clear(level, x, low, z);
            for (int k = 0; k < depth; k++) {
                level.setBlock(new BlockPos(x, low - k, z), waterState, Block.UPDATE_CLIENTS);
            }
            level.setBlock(new BlockPos(x, low - depth, z), RiverBankFeature.hash(x, z) < 0.5 ? mud : wetSilt, Block.UPDATE_CLIENTS);
            depths.put(key, depth);
            deepest = Math.max(deepest, depth);
            waterBlocks += depth;
        }
        Map<Long, Double> shoreLaid = new HashMap<>();
        for (Map.Entry<Long, Double> e : cells.shore().entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            int g = open(level, bounds, keepout, x, z, lookFrom, low);
            if (g == Integer.MIN_VALUE) {
                continue; // a bank a step up, or something on it: left as it is
            }
            raiseTo(level, x, z, g, low, dirt);
            level.setBlock(new BlockPos(x, low, z), shoreMud, Block.UPDATE_CLIENTS);
            shoreLaid.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Long, Double> e : cells.trampled().entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            int g = open(level, bounds, keepout, x, z, lookFrom, low);
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            int top = e.getValue() < cells.ring() / 2.0 + 1.5 ? low : Math.max(low, Math.min(g, low + 1));
            double k = RiverBankFeature.hash(x + 4, z);
            BlockState kind = k < 0.4 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 0.7 ? Blocks.DIRT.defaultBlockState() : dryMud;
            raiseTo(level, x, z, g, top, dirt);
            level.setBlock(new BlockPos(x, top, z), kind, Block.UPDATE_CLIENTS);
        }
        for (Map.Entry<Long, Double> e : cells.past().entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey());
            if (x < bounds[0] || x > bounds[2] || z < bounds[1] || z > bounds[3]) {
                continue;
            }
            int g = RiverBankFeature.ground(level, x, z, lookFrom);
            if (!clearAbove(level, x, g, z)) {
                continue; // a tree, a log: left as it is
            }
            // the land past the ring slopes up a block a block, its grass grazed short
            int most = low + 1 + (int) Math.floor(e.getValue());
            if (g > most && g <= low + UNEVEN + 2 && RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z)))) {
                clear(level, x, most, z);
                level.setBlock(new BlockPos(x, most, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
                g = most;
            }
            BlockState plant = level.getBlockState(new BlockPos(x, g + 1, z));
            if (!plant.isAir() && plant.getFluidState().isEmpty() && RiverBankFeature.hash(x, z + 5) >= 0.03) {
                clear(level, x, g, z);
            }
        }
        // thin algae at the edge only: the cloudy water keeps the light out (the model viewer's pans)
        for (long key : kept) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            if (cells.edge().contains(key) && RiverBankFeature.hash(x + 1, z) < 0.25) {
                BlockPos cell = new BlockPos(x, low - depths.get(key) + 1, z);
                BlockState algae = SavannaBlocks.ALGAE.get().whole(com.antcolonies.savanna.block.AlgaeBlock.THIN);
                if (level.getBlockState(cell).is(Blocks.WATER) && algae.canSurvive(level, cell)) {
                    level.setBlock(cell, algae, Block.UPDATE_CLIENTS);
                }
            }
        }
        wallow(level, random, depths, shoreLaid, low, tone);
        OxbowFeature.holdWater(level, depths, low);
        return new Laid(new BlockPos(cx, low, cz), want, unit.size() > 1, deepest, cells.ring(), waterBlocks, keptShare);
    }

    /** A pan's columns: water (with its depth), shore, trampled ring and the grazed land past it (blocks past the ring). */
    record Cells(Map<Long, Integer> water, java.util.Set<Long> edge, Map<Long, Double> shore, Map<Long, Double> trampled, Map<Long, Double> past, int ring) {
        /** Whether its water, shore and ring lie inside the chunks a feature may touch, a tree's foot to spare. */
        boolean fits(int[] bounds) {
            for (Map<Long, ?> part : List.of(water, shore, trampled)) {
                for (long key : part.keySet()) {
                    if (!OxbowFeature.inside(bounds, BlockPos.getX(key), BlockPos.getZ(key))) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** The columns of a pan of radius {@code r} from its radius-1 shape (the model viewer's {@code panAt}). */
    private static Cells cells(List<Lobe> unit, double r, double ringDice, int cx, int cz) {
        int ring = (int) Math.round(2 + r / 8 + 2 * ringDice);
        Map<Long, Integer> water = new HashMap<>();
        java.util.Set<Long> rimWater = new java.util.HashSet<>();
        Map<Long, Double> shore = new HashMap<>(), trampled = new HashMap<>(), past = new HashMap<>();
        int reach = (int) Math.ceil(r * 2.2 + ring + GRAZED + 2);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Double.MAX_VALUE;
                int depth = 1;
                for (Lobe l : unit) {
                    double lr = l.r() * r;
                    double ex = dx - l.x() * r, ez = dz - l.z() * r, c = Math.cos(l.ang()), s = Math.sin(l.ang()), k = Math.sqrt(l.sx());
                    double u = (ex * c + ez * s) / k, v = (-ex * s + ez * c) * k, a = Math.atan2(v, u), rr = Math.hypot(u, v);
                    double edge = lr * (1 + l.wob() * Math.sin(3 * a + l.p1()) + l.wob() * 0.6 * Math.sin(5 * a + l.p2()));
                    d = Math.min(d, rr - edge);
                    if (rr < edge * 0.55) {
                        depth = Math.max(depth, 2);
                    }
                    if (rr < edge * 0.25 && lr >= 14) {
                        depth = 3; // only the biggest pans are 3 deep in the very middle
                    }
                }
                long key = BlockPos.asLong(x, 0, z);
                if (d < 0) {
                    water.put(key, depth);
                    if (d > -2) {
                        rimWater.add(key);
                    }
                } else if (d < 1.5) {
                    shore.put(key, d);
                } else if (d < ring + 1.5) {
                    trampled.put(key, d);
                } else if (d < ring + 1.5 + GRAZED) {
                    past.put(key, d - ring - 1.5);
                }
            }
        }
        return new Cells(water, rimWater, shore, trampled, past, ring);
    }

    /** A bank column's ground if the pan may lay its shore or ring there, else {@link Integer#MIN_VALUE}. */
    private static int open(WorldGenLevel level, int[] bounds, OxbowFeature.Keepout keepout, int x, int z, int lookFrom, int low) {
        if (!OxbowFeature.inside(bounds, x, z) || keepout.at(x, z)) {
            return Integer.MIN_VALUE;
        }
        int g = RiverBankFeature.ground(level, x, z, lookFrom);
        if (g > low + UNEVEN || !RiverBankFeature.soft(level.getBlockState(new BlockPos(x, g, z))) || !clearAbove(level, x, g, z)) {
            return Integer.MIN_VALUE;
        }
        return g;
    }

    /** The ground taken down to {@code top} or filled up to it with dirt (a dip beside the water would drain it). */
    private static void raiseTo(WorldGenLevel level, int x, int z, int g, int top, BlockState dirt) {
        clear(level, x, top, z);
        for (int y = g + 1; y < top; y++) {
            level.setBlock(new BlockPos(x, y, z), dirt, Block.UPDATE_CLIENTS);
        }
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
