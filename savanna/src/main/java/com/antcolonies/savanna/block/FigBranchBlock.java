package com.antcolonies.savanna.block;

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
 * A sycamore fig's branch (floodplain trees step 3): the limbs once they are thinner than a block, through the six
 * thicknesses of the model viewer the user picked the fig from (14, 12, 10, 8, 6 and 4 pixels;
 * {@code tools/floodplain_viewer.template.html}, {@code TAPER}), wearing the fig's barks ({@link FigBark}). It joins
 * every way it touches tree wood or leaves, like the dry trees' branches ({@link TreeBranchBlock}). A tree grown by the
 * mod sets each branch's thickness; a player's branches taper as they are built out, as the acacias' do
 * ({@link TaperingBranchBlock}): the first placed is the thickest, every 2 placed on along it one thickness thinner.
 */
public class FigBranchBlock extends TreeBranchBlock {
    public static final MapCodec<FigBranchBlock> CODEC = simpleCodec(FigBranchBlock::new);
    /** How many thicknesses: level 0 is 14 pixels, each level 2 thinner, level 5 is 4 pixels. */
    public static final int LEVELS = 6;
    /** How far along the chain this branch is; 2 steps make a thickness level. */
    public static final IntegerProperty STEP = IntegerProperty.create("step", 0, LEVELS * 2 - 1);
    /** The last step, the thinnest; also the default. */
    public static final int MAX_STEP = LEVELS * 2 - 1;

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

    public FigBranchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STEP, MAX_STEP).setValue(FigBark.BARK, FigBark.MOTTLED));
    }

    /** A level's thickness in pixels: 14, then 2 less per level (tools/floodplain_tree_art.py draws the same). */
    public static int width(int level) {
        return 14 - 2 * level;
    }

    /** The thickness level whose width is nearest these pixels, as the model viewer picks it. */
    public static int levelFor(double pixels) {
        int best = 0;
        for (int level = 1; level < LEVELS; level++) {
            if (Math.abs(width(level) - pixels) < Math.abs(width(best) - pixels)) {
                best = level;
            }
        }
        return best;
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STEP, FigBark.BARK);
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
     * Placed against a fig branch it grows on from it, one step further; placed only touching some, it grows on from
     * the thickest; placed against anything else it starts a new limb, thickest of all.
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
        int step = from < 0 ? 0 : Math.min(from + 1, MAX_STEP);
        return joined(context.getLevel(), pos, defaultBlockState().setValue(STEP, step));
    }
}
