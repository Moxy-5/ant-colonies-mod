package com.antcolonies.block;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.ClimbShape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of a termite spire (cathedral mound): like pointed dripstone, it comes in 5 thicknesses that stack into a
 * spire narrowing to a point. Thickness 0 (base) is a full block at the bottom narrowing to 12 pixels, then thick
 * (12 to 10), middle (10 to 8), thin (8 to 6) and the tip (6 to a point, 12 pixels tall). Placed by hand on a spire
 * piece it becomes the next thinner one, so placing 5 in a column makes a whole spire top.
 */
public class TermiteSpireBlock extends MoundPieceBlock implements ClimbShape {
    public static final int BASE = 0;
    public static final int TIP = 4;
    public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", BASE, TIP);
    /** Width (pixels) at the bottom and the top of each thickness. */
    private static final int[][] WIDTHS = {{16, 12}, {12, 10}, {10, 8}, {8, 6}, {6, 0}};

    private static final VoxelShape[] SHAPES = new VoxelShape[TIP + 1];

    static {
        for (int t = BASE; t <= TIP; t++) {
            SHAPES[t] = tapered(WIDTHS[t][0], WIDTHS[t][1], 0, LAYERS);
        }
    }

    public TermiteSpireBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(THICKNESS, BASE).setValue(MoundSoil.AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THICKNESS, MoundSoil.AGE);
    }

    @Override
    protected VoxelShape shape(BlockState state) {
        return SHAPES[state.getValue(THICKNESS)];
    }

    /** Insects climb every piece but the needle tip (a termite clinging to a point would look wrong). */
    @Override
    public boolean isClimbable(BlockState state) {
        return state.getValue(THICKNESS) < TIP;
    }

    @Override
    public double widthAt(BlockState state, double y) {
        int t = state.getValue(THICKNESS);
        int layer = Math.max(0, Math.min(LAYERS - 1, (int) Math.floor(y * LAYERS)));
        return taper(WIDTHS[t][0], WIDTHS[t][1], layer, LAYERS) / 16.0;
    }

    /** Width (pixels) of the piece's top: what the next piece up should start from. */
    public static int topWidth(int thickness) {
        return WIDTHS[thickness][1];
    }

    /** Width (pixels) of the piece's foot. */
    public static int bottomWidth(int thickness) {
        return WIDTHS[thickness][0];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos below = context.getClickedPos().below();
        BlockState under = context.getLevel().getBlockState(below);
        int thickness = under.is(this) ? Math.min(TIP, under.getValue(THICKNESS) + 1) : BASE;
        return defaultBlockState().setValue(THICKNESS, thickness);
    }
}
