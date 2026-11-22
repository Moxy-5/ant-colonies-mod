package com.antcolonies.savanna.worldgen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

import com.antcolonies.savanna.block.HippoGrassBlock;
import com.antcolonies.savanna.block.HippoGrassClumpBlock;
import com.antcolonies.savanna.block.PlantPush;
import com.antcolonies.savanna.block.ReedBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * New land's reed beds and hippo grass (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 4), laid after the
 * banks, the algae, the crocodiles' nests and the hippos' pools ({@code vegetal_decoration}), only on its own chunk's
 * columns:
 * <ul>
 * <li><b>reed beds</b> ({@link ReedBlock}, full height, plumes fresh or pale) in water 1 or 2 deep and at the waterline:
 *     on the slow near and mid inland rivers and the floodplain's other waters about one bed per 100 blocks of bank
 *     (about 15 long, 30 to 45 clumps), and along about a third of the oxbows' shallow edges, so most oxbows have one or
 *     two;</li>
 * <li><b>hippo grass</b> ({@link HippoGrassClumpBlock} at the waterline, {@link HippoGrassBlock} mats lying 1 to 3 out
 *     over the water, ring by ring as they grow) on the same rivers and the oxbows, about one patch per 60 blocks of
 *     bank, about 12 long; each patch at its own stage of the cycle, so a bank shows them at every stage.</li>
 * </ul>
 * Where a bed or patch goes is a smooth patch noise over the land (from the seed), so the beds are ragged and the same
 * whichever chunk is made first. Never on or beside sand (the sand bars, the crocodiles' slides on them) or a crocodile
 * nest ({@link #NO_PLANTS}), never on the far inland or coast rivers (fast, clear, sandy), never in a pan (open savanna
 * water is not ours). Numbers my picks, measured offline (patch cells and cuts) for the plan's spacing.
 */
public class RiverPlantsFeature extends Feature<NoneFeatureConfiguration> {
    /** Ground no river plant grows on or beside in new land: the sand bars and their slides, the crocodiles' nests. */
    public static final TagKey<Block> NO_PLANTS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(
            com.antcolonies.savanna.SavannaMod.MODID, "no_river_plants"));
    /** How far over sea level a water surface is looked for (the oxbows lie a block or two up on the plain). */
    static final int ABOVE_SEA = RiverAlgaeFeature.ABOVE_SEA;

    /** Where beds or patches lie: a smooth noise over the land, a column in a patch where it passes the cut. */
    public record Patches(double cell, double cut, long salt) {
        /** Everywhere (game tests). */
        public static final Patches ALL = new Patches(1, -1, 0);
        /** Nowhere (game tests). */
        public static final Patches NONE = new Patches(1, 2, 0);

        public boolean at(long seed, int x, int z) {
            return SandBanks.noise(x / cell, z / cell, seed ^ salt) > cut;
        }
    }

    /** Reed beds on the slow rivers: about one per 100 blocks of bank, about 15 long (measured offline). */
    public static final Patches RIVER_REEDS = new Patches(16, 0.74, 0x7265656473L);
    /** Reed beds round the oxbows: about a third of their shallow edges. */
    static final Patches OXBOW_REEDS = new Patches(12, 0.6, 0x7265656473L);
    /** Hippo grass on the slow rivers and the oxbows: about one patch per 60 blocks of bank, about 12 long. */
    static final Patches GRASS = new Patches(12, 0.7, 0x6869707067L);
    /** A patch's stage of the cycle: one noise over a wider cell, so neighbouring patches differ. */
    static final double STAGE_CELL = 40;

    /** What to lay where (the world's patches, or a game test's own). */
    public record Plan(long seed, Predicate<BlockPos> ours, Patches reeds, Patches grass) {
    }

    public RiverPlantsFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        BlockPos middle = new BlockPos(x0 + 8, level.getSeaLevel(), z0 + 8);
        if (!BigSavanna.lives(level.getLevel(), level.getBiome(middle), x0 + 8, z0 + 8)) {
            return false;       // the gate: no river life in a savanna too small to live in
        }
        var cont = RiverZones.continentalness(level);
        RiverZones.Zone zone = RiverZones.river(RiverZones.band(cont == null ? 0.1 : RiverBends.at(cont, x0 + 8, z0 + 8)));
        boolean slowRiver = zone == RiverZones.Zone.NEAR || zone == RiverZones.Zone.MID;
        long seed = level.getSeed();
        int sea = level.getSeaLevel() - 1;
        int laid = 0;
        // the slow rivers' banks, then the oxbows (any water of the floodplain)
        if (slowRiver) {
            laid += lay(level, x0, z0, 16, 16, sea, new Plan(seed, at -> level.getBiome(at).is(RiverZones.RIVER), RIVER_REEDS, GRASS));
        }
        int river = laid;
        // the floodplain: thick beds round an oxbow (within its site's 3 by 3 chunks, where it may lie), elsewhere (its
        // pools, the river's edge across it) the slow river's spacing: a floodplain is slow water wherever it is
        var source = level.getLevel().getChunkSource();
        boolean oxbow = false;
        for (int dx = -1; dx <= 1 && !oxbow; dx++) {
            for (int dz = -1; dz <= 1 && !oxbow; dz++) {
                oxbow = OxbowSites.site(source.getGenerator().getBiomeSource(), source.randomState().sampler(), seed, (x0 >> 4) + dx, (z0 >> 4) + dz);
            }
        }
        laid += lay(level, x0, z0, 16, 16, sea, new Plan(seed, at -> level.getBiome(at).is(MudSites.FLOODPLAIN), oxbow ? OXBOW_REEDS : RIVER_REEDS, GRASS));
        if (laid > 0) {
            com.antcolonies.savanna.SavannaMod.LOGGER.info("[world plants] at {} {} ({} river): {} on the river, {} on the floodplain{}",
                    x0 + 8, z0 + 8, zone, river, laid - river, oxbow ? " (an oxbow's)" : "");
        }
        return laid > 0;
    }

    /**
     * Lays reed beds and hippo grass in the columns {@code x0..x0+w-1, z0..z0+d-1} round water whose surface is at most
     * {@link #ABOVE_SEA} over {@code sea} (sea level in the world; any height in a game test), only columns the plan's
     * {@code ours} says yes to and its patches cover. How many plants (reed columns, clumps and mats) it laid. Also the
     * game tests' way in.
     */
    public static int lay(WorldGenLevel level, int x0, int z0, int w, int d, int sea, Plan plan) {
        int laid = 0;
        BlockState reed = SavannaBlocks.REEDS.get().defaultBlockState();
        BlockState clump = SavannaBlocks.HIPPO_GRASS_CLUMP.get().defaultBlockState();
        Deque<BlockPos> clumps = new ArrayDeque<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                if (!plan.ours().test(at.set(x, sea, z))) {
                    continue;
                }
                boolean reeds = plan.reeds().at(plan.seed(), x, z), grass = plan.grass().at(plan.seed(), x, z);
                if (!reeds && !grass) {
                    continue;
                }
                BlockPos foot = foot(level, x, z, sea);
                if (foot == null || nearSand(level, foot)) {
                    continue;
                }
                boolean wet = level.getFluidState(foot).is(FluidTags.WATER);
                if (reeds && reed.canSurvive(level, foot)) {
                    laid += reedColumn(level, foot) ? 1 : 0;
                } else if (grass && !wet && clump.canSurvive(level, foot)) {
                    int stage = stageAt(plan.seed(), x, z);
                    level.setBlock(foot, clump.setValue(HippoGrassClumpBlock.STAGE, stage), Block.UPDATE_CLIENTS);
                    clumps.add(foot.immutable());
                    laid++;
                }
            }
        }
        // the mats: out over the water from every clump together, ring by ring, as they grow
        laid += mats(level, clumps, x0, z0, w, d, plan);
        return laid;
    }

    /**
     * Where a plant would stand in a column: in the water on its bed when the water is 1 or 2 deep, or on the bank just
     * over the water's level (the waterline); null for deeper water, high ground, or no water near.
     */
    static BlockPos foot(WorldGenLevel level, int x, int z, int sea) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, sea + ABOVE_SEA, z);
        // down to the first block that is not air or a replaceable plant
        while (at.getY() > sea - 3) {
            BlockState state = level.getBlockState(at);
            if (!state.isAir() && !(state.canBeReplaced() && state.getFluidState().isEmpty())) {
                break;
            }
            at.move(0, -1, 0);
        }
        if (at.getY() <= sea - 3) {
            return null;
        }
        BlockState top = level.getBlockState(at);
        if (top.getFluidState().is(FluidTags.WATER)) {
            int depth = 0;
            while (depth <= ReedBlock.DEEPEST && level.getFluidState(at.below(depth)).is(FluidTags.WATER)) {
                depth++;
            }
            // water 1 or 2 deep over a bed (algae on the bed is taken by the reed)
            return depth <= ReedBlock.DEEPEST ? at.below(depth - 1).immutable() : null;
        }
        // land: the bank's top block, at most a block over the water's level beside it
        return at.above().immutable();
    }

    /** Whether the ground at or beside a plant's foot is sand, a crocodile slide or a nest ({@link #NO_PLANTS}). */
    static boolean nearSand(WorldGenLevel level, BlockPos foot) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (level.getBlockState(foot.offset(dx, dy, dz)).is(NO_PLANTS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Grows a reed column at {@code foot} to its full height (its plume fresh or pale, by the spot); whether it stood. */
    static boolean reedColumn(WorldGenLevel level, BlockPos foot) {
        int height = ReedBlock.fullHeight(level, foot);
        boolean pale = RiverBankFeature.hash(foot.getX() + 11, foot.getZ() - 5) < 0.5;
        BlockState reed = SavannaBlocks.REEDS.get().defaultBlockState();
        int made = 0;
        for (int i = 0; i < height; i++) {
            BlockPos p = foot.above(i);
            BlockState there = level.getBlockState(p);
            boolean water = there.getFluidState().is(FluidTags.WATER) && there.getFluidState().isSource();
            if (!(water || there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty())) {
                break;
            }
            level.setBlock(p, reed.setValue(ReedBlock.WATERLOGGED, water).setValue(ReedBlock.TOP, false).setValue(ReedBlock.PALE, pale)
                    .setValue(PlantPush.PUSH, PlantPush.STILL), Block.UPDATE_CLIENTS);
            made++;
        }
        if (made == 0) {
            return false;
        }
        BlockPos top = foot.above(made - 1);
        level.setBlock(top, level.getBlockState(top).setValue(ReedBlock.TOP, true), Block.UPDATE_CLIENTS);
        return true;
    }

    /** A patch's stage of the hippo grass cycle (young, grown, flowering, dying back), the same over a patch. */
    public static int stageAt(long seed, int x, int z) {
        double n = SandBanks.noise(x / STAGE_CELL, z / STAGE_CELL, seed ^ 0x7374616765L);
        return Math.min(3, (int) (n * 4.0));
    }

    /**
     * Lays the mats out from the clumps over the water beside them, ring by ring from all of them at once (a mat
     * {@code r} out has a mat {@code r - 1} out, or a clump, beside it, and takes that one's stage), as far as the stage and
     * the spot allow ({@link HippoGrassBlock#sendOut}'s own rules), inside the area being laid and away from sand. How
     * many it laid.
     */
    static int mats(WorldGenLevel level, Deque<BlockPos> clumps, int x0, int z0, int w, int d, Plan plan) {
        int laid = 0;
        BlockState mat = SavannaBlocks.HIPPO_GRASS.get().defaultBlockState();
        Deque<BlockPos> ring = new ArrayDeque<>();
        for (BlockPos clump : clumps) {
            if (level.getBlockState(clump).getValue(HippoGrassClumpBlock.STAGE) != HippoGrassClumpBlock.DYING) {
                ring.add(clump);    // dying back: only the clump is left
            }
        }
        for (int reach = 1; reach <= HippoGrassBlock.FARTHEST && !ring.isEmpty(); reach++) {
            Deque<BlockPos> next = new ArrayDeque<>();
            for (BlockPos from : ring) {
                int stage = level.getBlockState(from).getValue(HippoGrassClumpBlock.STAGE);
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos p = from.relative(side);
                    if (p.getX() < x0 || p.getX() >= x0 + w || p.getZ() < z0 || p.getZ() >= z0 + d || !plan.ours().test(p)) {
                        continue;
                    }
                    if (stage == HippoGrassClumpBlock.YOUNG && reach > 1 || reach > HippoGrassBlock.mostReachAt(p)
                            || !level.isEmptyBlock(p) || nearSand(level, p.below())) {
                        continue;
                    }
                    BlockState here = mat.setValue(HippoGrassBlock.STAGE, stage).setValue(HippoGrassBlock.REACH, reach);
                    if (here.canSurvive(level, p)) {
                        level.setBlock(p, here, Block.UPDATE_CLIENTS);
                        next.add(p);
                        laid++;
                    }
                }
            }
            ring = next;
        }
        return laid;
    }
}
