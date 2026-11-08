package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The savanna rivers' banks (2026-11-07, {@code docs/plans/savanna-rivers.md}; the user's picks in the river viewer,
 * {@code python tools/model_viewer.py rivers}). The land (flattened in data, {@code tools/flat_savannas/flat_offset.py})
 * reaches the river as a floodplain a block above the water; this cuts the last {@link #STRIP} blocks down flush with the
 * water, the low strip a real river keeps beside its channel (the user's "step 4 blocks back"), and lays the river's own
 * soil on it, look 6: silt bars on the inside of bends, where the current slows and drops its load, wet silt by the water,
 * and trampled wet river mud at the waterline and in the hollows between the bars' ridges, where animals come down to
 * drink; a thin wet line on the outside of bends, where the current cuts a grassy bank. The river bed becomes silt.
 *
 * <p>Run once per chunk, it changes only its own chunk's columns and only reads round them, so chunks made in any order
 * fit together: a column's look depends only on the sea-level water round it, which this never takes away. Only columns
 * of the savanna's own biomes and its river ({@link #WATERS}) change, and only near water at sea level.
 */
public class RiverBankFeature extends Feature<NoneFeatureConfiguration> {
    /** The savannas and their river ({@code data/antcolonies/tags/worldgen/biome/savanna_waters.json}). */
    public static final TagKey<Biome> WATERS = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_waters"));
    /** How far back from the water the low strip runs (the user's pick, 4 blocks). */
    public static final double STRIP = 4.0;
    /** A bank higher than this over the water is left alone (a cut bank or a hill, not floodplain). */
    static final int HIGHEST_BANK = 3;
    /** How far the bend is read (rays that find water within this many blocks). */
    static final int BEND_LOOK = 12;
    private static final int RAYS = 24;
    private static final int LOOK = (int) Math.ceil(STRIP + 1.5);

    public RiverBankFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        return shape(level, x0, z0, 16, 16, level.getSeaLevel() - 1, at -> level.getBiome(at).is(WATERS)) > 0;
    }

    /**
     * Shapes the banks of the columns {@code x0..x0+w-1, z0..z0+d-1} round water whose surface is at {@code top} (sea
     * level in the world; any height in a game test), only columns {@code ours} says yes to. How many it changed.
     */
    public static int shape(WorldGenLevel level, int x0, int z0, int w, int d, int top, java.util.function.Predicate<BlockPos> ours) {
        Waters waters = new Waters(level, x0 - BEND_LOOK - 1, z0 - BEND_LOOK - 1, Math.max(w, d) + 2 * (BEND_LOOK + 1), top);
        int changed = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                at.set(x, top, z);
                if (ours.test(at) && (waters.isWater(x, z) ? silt(level, x, z, waters) : bank(level, x, z, waters))) {
                    changed++;
                }
            }
        }
        return changed;
    }

    /** The top solid block of a column at or under {@code from} (the land, or the bed under water). */
    static int ground(WorldGenLevel level, int x, int z, int from) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, from, z);
        while (at.getY() > from - 40) {
            BlockState s = level.getBlockState(at);
            if (s.isSolid()) {
                return at.getY();
            }
            at.move(0, -1, 0);
        }
        return from - 40;
    }

    /** The river bed under sea-level water: silt in place of the soil. */
    private static boolean silt(WorldGenLevel level, int x, int z, Waters waters) {
        int g = ground(level, x, z, waters.top);
        BlockPos bed = new BlockPos(x, g, z);
        if (g < waters.top - 16 || !soft(level.getBlockState(bed))) {
            return false;
        }
        level.setBlock(bed, SavannaBlocks.SILT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** A land column near the water: cut flush with the water and given its look. */
    private static boolean bank(WorldGenLevel level, int x, int z, Waters waters) {
        double out = waters.distance(x, z, LOOK) - 0.5; // from the water's edge (a column beside the water is half a block from it)
        if (out >= STRIP + 0.25 * wobble(x * 0.7, z * 0.7)) {
            return false;
        }
        int top = waters.top;
        int g = ground(level, x, z, top + HIGHEST_BANK + 1);
        if (g < top || g > top + HIGHEST_BANK || !level.getBlockState(new BlockPos(x, g + 1, z)).isAir()
                || !soft(level.getBlockState(new BlockPos(x, top, z)))) {
            return false;
        }
        for (int y = top + 1; y <= g; y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        // look 6: the bars on the inside of bends, wider the sharper the bend; a thin wet line on the outside
        double bend = waters.bend(x, z);
        boolean inner = bend > 0.1;
        double width = inner ? Math.min(STRIP, 0.6 + 3.6 * Math.pow(bend, 0.8)) : 0.15;
        BlockState surface = Blocks.GRASS_BLOCK.defaultBlockState();
        boolean silty = false;
        if (out < width + 0.5 * wobble(x, z)) {
            silty = true;
            Block kind = out < 1.1 ? SavannaBlocks.WET_SILT.get() : SavannaBlocks.SILT.get();
            if (out < 1.1 && hash(x, z) < 0.45) {
                kind = SavannaBlocks.RIVER_MUD.get(); // trampled at the waterline
            } else if (width > 2.4 && out > 1.6 && out < 2.4 + 0.4 * wobble(z, x) && hash(x + 3, z) < 0.75) {
                kind = SavannaBlocks.RIVER_MUD.get(); // the fine mud in the hollow between the bar's ridges
            }
            surface = kind.defaultBlockState();
            if (inner && bend > 0.55 && out < 0.9 && holdsWater(level, x, top, z)) {
                // shallow water over the bar's foot on a sharp bend
                level.setBlock(new BlockPos(x, top, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(new BlockPos(x, top - 1, z), SavannaBlocks.WET_SILT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                return true;
            }
        }
        level.setBlock(new BlockPos(x, top, z), surface, Block.UPDATE_CLIENTS);
        if (silty && soft(level.getBlockState(new BlockPos(x, top - 1, z)))) {
            level.setBlock(new BlockPos(x, top - 1, z), SavannaBlocks.SILT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** Whether water put at this cell stays: every side solid or water. */
    private static boolean holdsWater(WorldGenLevel level, int x, int y, int z) {
        for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockState side = level.getBlockState(new BlockPos(x + s[0], y, z + s[1]));
            if (!side.getFluidState().is(FluidTags.WATER) && !side.isSolid()) {
                return false;
            }
        }
        return true;
    }

    /** The land's soft top: soil, sand, gravel, clay (never rock). */
    static boolean soft(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(SavannaBlocks.SILT.get()) || state.is(SavannaBlocks.WET_SILT.get()) || state.is(SavannaBlocks.RIVER_MUD.get());
    }

    /** The river viewer's wobble, a smooth wave of about a block (the same numbers). */
    static double wobble(double x, double z) {
        return 0.45 * Math.sin(x * 1.7 + z * 0.9) + 0.4 * Math.sin(x * 0.6 - z * 1.3);
    }

    /** A fixed 0 to 1 dice per column. */
    static double hash(int x, int z) {
        long h = x * 3129871L ^ z * 116129781L;
        h = h * h * 42317861L + h * 11L;
        return ((h >> 16) & 0xFFFF) / 65536.0;
    }

    /** Sea-level water round a chunk, read once a column. */
    static final class Waters {
        final WorldGenLevel level;
        final int x0, z0, size, top;
        private final byte[] seen;

        Waters(WorldGenLevel level, int x0, int z0, int size, int top) {
            this.level = level;
            this.x0 = x0;
            this.z0 = z0;
            this.size = size;
            this.top = top;
            this.seen = new byte[size * size];
        }

        /** Water at sea level with open air over it (a river or lake, not a pool up on the plain). */
        boolean isWater(int x, int z) {
            int i = x - x0, j = z - z0;
            if (i < 0 || j < 0 || i >= size || j >= size) {
                return false;
            }
            int k = i * size + j;
            if (seen[k] == 0) {
                BlockState here = level.getBlockState(new BlockPos(x, top, z));
                // (the shallow water this puts over a bar's foot, on wet silt, is not counted: chunks made in any order agree)
                boolean water = here.getFluidState().is(FluidTags.WATER) && here.getFluidState().isSource()
                        && level.getBlockState(new BlockPos(x, top + 1, z)).isAir()
                        && !level.getBlockState(new BlockPos(x, top - 1, z)).is(SavannaBlocks.WET_SILT.get());
                seen[k] = (byte) (water ? 1 : 2);
            }
            return seen[k] == 1;
        }

        /** The distance to the nearest water column within {@code look} (or more than {@code look}). */
        double distance(int x, int z, int look) {
            double best = look + 1;
            for (int dx = -look; dx <= look; dx++) {
                for (int dz = -look; dz <= look; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d < best && isWater(x + dx, z + dz)) {
                        best = d;
                    }
                }
            }
            return best;
        }

        /**
         * How much the land here is the inside of a bend, 0 (straight or outside) to 1 (a tongue of land the river wraps):
         * from the share of {@link #RAYS} rays that find water within {@link #BEND_LOOK}; a straight bank sees water on
         * about half of them, the inside of a bend more, the outside less.
         */
        double bend(int x, int z) {
            int hits = 0;
            for (int r = 0; r < RAYS; r++) {
                double a = r * 2 * Math.PI / RAYS, dx = Math.cos(a), dz = Math.sin(a);
                for (int s = 1; s <= BEND_LOOK; s++) {
                    if (isWater((int) Math.floor(x + 0.5 + dx * s), (int) Math.floor(z + 0.5 + dz * s))) {
                        hits++;
                        break;
                    }
                }
            }
            return Math.max(0, Math.min(1, (hits / (double) RAYS - 0.5) / 0.25));
        }
    }
}
