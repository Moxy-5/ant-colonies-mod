package com.antcolonies.desert.worldgen;

import com.antcolonies.desert.block.GramaGrassBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Lays down a stand of grama grass (see {@link GramaGrassBlock}; the user's decisions, 2026-10-16). With no
 * {@code bushes}: a meadow, a rough blob {@code radius} blocks out (blue grama on the plains: real blue grama grows in
 * mats over whole stretches of shortgrass prairie; the user's decision, 2026-10-17: mostly small meadows 5 to 9 blocks
 * across, about 1 in 8 a big one 15 to 20 across, the long trails' goal). With {@code bushes}: that many dead bushes, each with loose bunches
 * round it within {@code radius} (black grama in the badlands: in the real desert grassland the grass gathers round
 * the shrubs, where the soil holds more water). {@code density}: the share of cells in the stand that get a plant.
 *
 * <p>Every plant of one stand is at the same stage (as a real stand that got the same rain): mostly grown and waiting
 * for rain, sometimes in flower, ripe or dried (my picks: 45, 15, 20, 20 in 100); its count within the stage varies,
 * so they move on close together but not all in one tick. A plant replaces short plants (vanilla short grass) but not
 * tall ones, and only goes where its kind can grow.
 */
public class GramaPatchFeature extends Feature<GramaPatchFeature.Config> {
    public record Config(Block block, IntProvider radius, float density, IntProvider bushes) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(Config::block),
                IntProvider.codec(1, 12).fieldOf("radius").forGetter(Config::radius),
                Codec.floatRange(0.0F, 1.0F).fieldOf("density").forGetter(Config::density),
                IntProvider.codec(0, 4).fieldOf("bushes").forGetter(Config::bushes)).apply(i, Config::new));
    }

    public GramaPatchFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        if (!(config.block() instanceof GramaGrassBlock)) {
            return false;
        }
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        GramaGrassBlock.Stage stage = pickStage(random);
        int bushes = config.bushes().sample(random);
        boolean placed = false;
        if (bushes == 0) {
            placed = stand(level, random, config, origin, config.radius().sample(random), stage, 1.25);
        } else {
            for (int i = 0; i < bushes; i++) {
                BlockPos at = origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
                BlockPos bush = surface(level, at.getX(), at.getZ());
                if (bush != null && Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, bush)) {
                    level.setBlock(bush, Blocks.DEAD_BUSH.defaultBlockState(), 2);
                    placed |= stand(level, random, config, bush, config.radius().sample(random), stage, 1.0);
                }
            }
        }
        return placed;
    }

    /** One stand round {@code middle}: a blob (squashed by {@code squash} one way) with a wavy edge. */
    private static boolean stand(WorldGenLevel level, RandomSource random, Config config, BlockPos middle, int radius,
            GramaGrassBlock.Stage stage, double squash) {
        boolean placed = false;
        double wave = random.nextDouble() * Math.PI * 2;
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                double angle = Math.atan2(dz, dx);
                double edge = radius + 0.5 + Math.sin(angle * 3 + wave) * 0.7;
                if (Math.hypot(dx, dz * squash) > edge || random.nextFloat() >= config.density()) {
                    continue;
                }
                BlockPos pos = surface(level, middle.getX() + dx, middle.getZ() + dz);
                if (pos == null || Math.abs(pos.getY() - middle.getY()) > 2) {
                    continue;
                }
                BlockState plant = config.block().defaultBlockState().setValue(GramaGrassBlock.STAGE, stage)
                        .setValue(GramaGrassBlock.AGE, random.nextInt(4));
                if (plant.canSurvive(level, pos)) {
                    level.setBlock(pos, plant, 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /** The open cell on top of the ground at this column (air or a short replaceable plant), or null. */
    private static BlockPos surface(WorldGenLevel level, int x, int z) {
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
        if (shortPlant(level.getBlockState(pos.below()))) {
            pos = pos.below(); // (the heightmap stood on a short plant: it is replaced)
        }
        BlockState here = level.getBlockState(pos);
        return here.isAir() || shortPlant(here) ? pos : null;
    }

    /** A short plant a grama plant may take the place of (vanilla short grass, a flower), not water or a tall plant. */
    private static boolean shortPlant(BlockState state) {
        return !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof DoublePlantBlock);
    }

    private static GramaGrassBlock.Stage pickStage(RandomSource random) {
        int roll = random.nextInt(100);
        return roll < 45 ? GramaGrassBlock.Stage.LEAVES : roll < 60 ? GramaGrassBlock.Stage.FLOWERING
                : roll < 80 ? GramaGrassBlock.Stage.RIPE : GramaGrassBlock.Stage.DRIED;
    }
}
