package com.antcolonies.savanna.block;

import com.antcolonies.nav.Slope;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dead colony's fungus comb (M8 step 1a, {@code docs/DESIGN.md}, "Dead mounds, details"): once the termites are gone,
 * nothing weeds the garden and the fungus Xylaria, in the comb all along, takes it over. {@link #STAGE} 0: furred, a
 * grey-white fuzz of threads over the old comb, the first tiny stalks (about the first day); 1: black, the comb a black
 * crust with black stalks forked like antlers, their tips white with spores (days 1 to 4); 2: rotten, soft and
 * slumping, the stalks black all over and falling (days 4 to 6), losing its layers until it is gone. The dead mound
 * book ({@code colony.DeadMounds}) moves it on; nothing eats it.
 *
 * <p>Layers as the living comb's, 2 pixels each, at most 4; solid and flat on top, so insects walk on it. The look
 * (the user's pick, look B "antler forest") comes from {@code tools/xylaria_art.py}.
 */
public class DeadCombBlock extends Block implements Slope {
    public static final int MAX_LAYERS = FungusCombBlock.MAX_COMB_LAYERS;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, MAX_LAYERS);
    public static final int FURRED = 0;
    public static final int BLACK = 1;
    public static final int ROTTEN = 2;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", FURRED, ROTTEN);

    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

    static {
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            SHAPES[layers] = Block.box(0.0, 0.0, 0.0, 16.0, layers * 2.0, 16.0);
        }
    }

    public DeadCombBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(STAGE, FURRED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    public int surfaceHeight(BlockState state, int u, int w) {
        return state.getValue(LAYERS) * 2;
    }
}
