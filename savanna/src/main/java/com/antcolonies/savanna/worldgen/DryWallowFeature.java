package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;
import com.antcolonies.savanna.block.SavannaMudBlock.Tone;
import com.antcolonies.savanna.block.SavannaMudBlock.Wetness;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * New land's dry wallows (the user, 2026-11-21, "river mud plus dry wallows"; {@code docs/plans/savanna-mud.md}, section
 * 5.1): on a dry wallow site ({@link MudSites#dry}), a small oval hollow of mud {@link #ACROSS_MIN} to {@link #ACROSS_MAX}
 * blocks across in flat open savanna, in the land's own colour ({@link MudSites#landTone}). Real: warthog and buffalo
 * wallows, dips in clay soil that hold the rain. Away from water they are cracked most of the time and come alive in every
 * storm ({@link SavannaMudBlock}); new land lays them dry, cracked round the edge and still drying in the middle. Laid
 * level with the grass (a dry wallow is a flat patch of clay), only on level ground (every column within a block of the
 * middle's), with nothing but plants within {@link #CLEAR} blocks (no tree, log, nest or mound), and no termite mound
 * place within {@link #FROM_MOUNDS} blocks. Numbers my picks.
 */
public class DryWallowFeature extends Feature<NoneFeatureConfiguration> {
    static final int ACROSS_MIN = 4, ACROSS_MAX = 7;
    /** Nothing but plants this close round the wallow. */
    static final int CLEAR = 4;
    /** No termite mound place's middle this close (a mound's foot reaches about 13 from it, so about 10 off the mound). */
    static final int FROM_MOUNDS = 22;
    /** The share of wallows with a deep middle (depth 4); the others reach 3. */
    static final float DEEP = 0.5F;

    public DryWallowFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int x0 = context.origin().getX() & ~15, z0 = context.origin().getZ() & ~15;
        RandomSource random = context.random();
        int cx = x0 + 5 + random.nextInt(7), cz = z0 + 5 + random.nextInt(7);
        if (!MudSites.dryLand(level.getBiome(new BlockPos(cx, level.getSeaLevel(), cz)))
                || !BigSavanna.lives(level.getLevel(), level.getBiome(new BlockPos(cx, level.getSeaLevel(), cz)), cx, cz)) {
            return false;
        }
        var source = level.getLevel().getChunkSource();
        var sampler = source.randomState().sampler();
        if (!MudSites.dry(source.getGenerator().getBiomeSource(), sampler, level.getSeed(), x0 >> 4, z0 >> 4) || nearMound(level, cx, cz)) {
            return false;
        }
        int laid = lay(level, random, cx, cz, MudSites.landTone(sampler, cx, cz), level.getHeight(Heightmap.Types.OCEAN_FLOOR, cx, cz) + 4);
        SavannaMod.LOGGER.info("[world mud] dry wallow site at {} {}: {} blocks", cx, cz, laid);
        return laid > 0;
    }

    /** Whether a termite mound place lies within {@link #FROM_MOUNDS} blocks (by the mounds' own pattern, made yet or not). */
    static boolean nearMound(WorldGenLevel level, int x, int z) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(MoundPlaces.STRUCTURE);
        if (!(structure instanceof TermiteMoundStructure mounds)) {
            return false;
        }
        return !MoundPlaces.near(level.getSeed(), x, z, FROM_MOUNDS, mounds.numbers().places()).isEmpty();
    }

    /**
     * Lays a dry wallow round {@code cx, cz} in this tone if the ground lets it (level, grass or soil, nothing but plants
     * within {@link #CLEAR}), the ground read down from {@code lookFrom} (a few blocks over the middle's ground); how many
     * mud blocks it laid (0 when it does not fit). Also the game tests' way in.
     */
    public static int lay(WorldGenLevel level, RandomSource random, int cx, int cz, Tone tone, int lookFrom) {
        double a = (ACROSS_MIN + random.nextDouble() * (ACROSS_MAX - ACROSS_MIN)) / 2.0;
        double b = (ACROSS_MIN + random.nextDouble() * (ACROSS_MAX - ACROSS_MIN)) / 2.0;
        double turn = random.nextDouble() * Math.PI;
        int most = random.nextFloat() < DEEP ? 4 : 3;
        double cos = Math.cos(turn), sin = Math.sin(turn);
        int reach = (int) Math.ceil(Math.max(a, b));
        int middle = surface(level, cx, cz, lookFrom);
        // the oval's columns and how far out each lies (0 middle, 1 edge)
        Map<BlockPos, Double> cells = new HashMap<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double u = (dx * cos + dz * sin) / a, v = (-dx * sin + dz * cos) / b;
                double n = Math.sqrt(u * u + v * v);
                if (n <= 1.0) {
                    cells.put(new BlockPos(cx + dx, 0, cz + dz), n);
                }
            }
        }
        // level soil under open sky, and nothing but plants round it
        for (BlockPos c : cells.keySet()) {
            int g = surface(level, c.getX(), c.getZ(), lookFrom);
            BlockState ground = level.getBlockState(new BlockPos(c.getX(), g, c.getZ()));
            if (Math.abs(g - middle) > 1 || !ground.is(BlockTags.DIRT) || ground.is(SavannaBlocks.SAVANNA_MUD.get())) {
                return 0;
            }
        }
        for (int dx = -reach - CLEAR; dx <= reach + CLEAR; dx++) {
            for (int dz = -reach - CLEAR; dz <= reach + CLEAR; dz++) {
                int x = cx + dx, z = cz + dz;
                int g = surface(level, x, z, lookFrom);
                BlockState top = level.getBlockState(new BlockPos(x, g, z));
                if (Math.abs(g - middle) > 3 || !top.getFluidState().isEmpty()) {
                    return 0;       // a drop, a hill or water beside it
                }
                if (!top.is(BlockTags.DIRT) && !top.is(BlockTags.SAND) && !top.is(SavannaBlocks.SAVANNA_MUD.get())) {
                    return 0;       // a log, a mound, a nest lying on the ground
                }
                for (int y = g + 1; y <= g + 3; y++) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty())) {
                        return 0;   // a tree, a log, a nest, a mound
                    }
                }
            }
        }
        int laid = 0;
        for (Map.Entry<BlockPos, Double> e : cells.entrySet()) {
            int x = e.getKey().getX(), z = e.getKey().getZ();
            int g = surface(level, x, z, lookFrom);
            int depth = Math.max(1, Math.min(most, 1 + (int) Math.floor((1.0 - e.getValue()) * most)));
            Wetness wetness = depth <= 2 ? Wetness.CRACKED : Wetness.DRYING;
            BlockPos at = new BlockPos(x, g, z);
            RiverMudFeature.clearPlants(level, at.above());
            level.setBlock(at, SavannaBlocks.SAVANNA_MUD.get().with(wetness, depth, tone), Block.UPDATE_CLIENTS);
            laid++;
        }
        return laid;
    }

    /** The top solid block of a column at or under {@code from} (the ground under any plants). */
    static int surface(WorldGenLevel level, int x, int z, int from) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, from, z);
        while (p.getY() > level.getMinBuildHeight() && (level.getBlockState(p).isAir() || level.getBlockState(p).canBeReplaced())) {
            p.move(0, -1, 0);
        }
        return p.getY();
    }
}
