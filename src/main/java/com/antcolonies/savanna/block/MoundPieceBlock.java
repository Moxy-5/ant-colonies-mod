package com.antcolonies.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shaped piece of finished termite mound soil, for the cathedral mound (user's design, concept pictures in
 * {@code references/termites/preview/concept/}): spire pieces, the chimney mouth and ramps. Each piece is made of soil
 * that has already been built and sealed, so it has no layers; it dries and hardens with age like the plain mound
 * block ({@link MoundSoil}). Subclasses add their own shape and must add {@link MoundSoil#AGE} to their states.
 * Models and textures come from {@code tools/termite_blocks.py}.
 */
public abstract class MoundPieceBlock extends Block {
    public static final int LAYER_PIXELS = 2;
    public static final int LAYERS = 16 / LAYER_PIXELS;

    protected MoundPieceBlock(Properties properties) {
        super(properties);
    }

    /** The piece's shape for players and every creature (the same as it looks). */
    protected abstract VoxelShape shape(BlockState state);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(MoundSoil.AGE) < MoundSoil.OLDEST;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MoundSoil.age(state, level, pos, random);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        int age = state.getValue(MoundSoil.AGE);
        return MoundSoil.destroyProgress(MoundSoil.isStoneHard(age), MoundSoil.hardness(age), player, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return MoundSoil.sound(state.getValue(MoundSoil.AGE));
    }

    /**
     * A shape built from 2-pixel layers: {@code columnHeight(u, w)} is how many layers of the 8 x 8 columns (2 pixels
     * each) are filled, from the bottom.
     */
    protected static VoxelShape fromHeights(java.util.function.IntBinaryOperator columnHeight) {
        VoxelShape shape = Shapes.empty();
        for (int u = 0; u < LAYERS; u++) {
            for (int w = 0; w < LAYERS; w++) {
                int h = columnHeight.applyAsInt(u, w);
                if (h > 0) {
                    shape = Shapes.or(shape, Block.box(u * LAYER_PIXELS, 0, w * LAYER_PIXELS, (u + 1) * LAYER_PIXELS,
                            h * LAYER_PIXELS, (w + 1) * LAYER_PIXELS));
                }
            }
        }
        return shape.optimize();
    }

    /** A square column narrowing from {@code bottomWidth} to {@code topWidth} pixels, in 2-pixel layers (see {@link #taper}). */
    protected static VoxelShape tapered(int bottomWidth, int topWidth, int fromLayer, int toLayer) {
        VoxelShape shape = Shapes.empty();
        for (int v = fromLayer; v < toLayer; v++) {
            int width = taper(bottomWidth, topWidth, v - fromLayer, toLayer - fromLayer);
            if (width > 0) {
                double lo = (16 - width) / 2.0;
                shape = Shapes.or(shape, Block.box(lo, v * LAYER_PIXELS, lo, lo + width, (v + 1) * LAYER_PIXELS, lo + width));
            }
        }
        return shape.optimize();
    }

    /**
     * The width (pixels, a multiple of 2) of layer {@code v} of {@code count} in a column narrowing evenly from
     * {@code bottomWidth} to {@code topWidth}. The same sum is in {@code tools/termite_blocks.py}.
     */
    public static int taper(int bottomWidth, int topWidth, int v, int count) {
        double halves = bottomWidth / 2.0 + (topWidth - bottomWidth) / 2.0 * (v + 0.5) / count;
        return 2 * (int) Math.floor(halves + 0.5);
    }
}
