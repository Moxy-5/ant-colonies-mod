package com.antcolonies.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A branch that tapers as the player builds it out (the user's ask, 2026-10-19; umbrella and camel thorn only): the
 * first branch placed is 75 percent of a block thick, and every 2 branches placed on along it are 10 percent thinner
 * than the 2 before, like a real acacia limb thinning toward its twigs. {@link #STEP} counts how far along the chain
 * this branch is. Trees grown by the mod use the default step, about as thin as the old 6-pixel twig.
 */
public class TaperingBranchBlock extends TreeBranchBlock {
    public static final MapCodec<TaperingBranchBlock> CODEC = simpleCodec(TaperingBranchBlock::new);
    /** How many thickness levels: level 0 is 75 percent of a block, each level 10 percent thinner (level 10 about 4 pixels). */
    public static final int LEVELS = 11;
    /** How far along the chain this branch is; 2 steps make a thickness level. */
    public static final IntegerProperty STEP = IntegerProperty.create("step", 0, LEVELS * 2 - 1);
    /** The step trees grown by the mod use: level 7, about 5.7 pixels, nearest the old 6-pixel twig. */
    public static final int GROWN_STEP = 14;

    private static final VoxelShape[][] SHAPES = new VoxelShape[LEVELS][64];

    static {
        for (int level = 0; level < LEVELS; level++) {
            double lo = (16 - width(level)) / 2 / 16, hi = 1 - lo;
            for (int mask = 0; mask < 64; mask++) {
                VoxelShape shape = Shapes.box(lo, lo, lo, hi, hi, hi);
                for (Direction d : Direction.values()) {
                    if ((mask & (1 << d.ordinal())) != 0) {
                        shape = Shapes.or(shape, Shapes.box(
                                d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? hi : lo, d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? hi : lo,
                                d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? hi : lo, d.getStepX() < 0 ? lo : d.getStepX() > 0 ? 1 : hi,
                                d.getStepY() < 0 ? lo : d.getStepY() > 0 ? 1 : hi, d.getStepZ() < 0 ? lo : d.getStepZ() > 0 ? 1 : hi));
                    }
                }
                SHAPES[level][mask] = shape;
            }
        }
    }

    public TaperingBranchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STEP, GROWN_STEP));
    }

    /** A level's thickness in pixels: 75 percent of 16, then 10 percent less per level (tools/tree_art.py draws the same). */
    public static double width(int level) {
        return 16 * 0.75 * Math.pow(0.9, level);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STEP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[state.getValue(STEP) / 2][mask];
    }

    /**
     * Placed against a branch of the same kind it grows on from it, one step further; placed only touching some, it
     * grows on from the thickest; placed against anything else it starts a new limb, thickest of all.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState against = context.getLevel().getBlockState(pos.relative(context.getClickedFace().getOpposite()));
        int from = -1;
        if (against.is(this)) {
            from = against.getValue(STEP);
        } else {
            for (Direction d : Direction.values()) {
                BlockState n = context.getLevel().getBlockState(pos.relative(d));
                if (n.is(this) && (from < 0 || n.getValue(STEP) < from)) {
                    from = n.getValue(STEP);
                }
            }
        }
        int step = from < 0 ? 0 : Math.min(from + 1, LEVELS * 2 - 1);
        return joined(context.getLevel(), pos, defaultBlockState().setValue(STEP, step));
    }
}
