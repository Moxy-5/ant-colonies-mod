package com.antcolonies.savanna.block;

import com.antcolonies.block.EatenInStages;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of a dead acacia branch lying on the ground, 2 to 3 blocks long in all: the thick {@link Part#BASE} where it
 * broke off the tree, {@link Part#MIDDLE}, and the thin {@link Part#TIP} with a twig. Termites cut forage off it (each
 * block is a food plant of its own, so several work along one branch) and, like real termites, plaster mud over the wood
 * they are eating: it goes through 4 {@link #EATEN} stages, thinner and more mud-covered each time, and the last piece
 * leaves nothing. It lies at world generation in savannas and acacias shed new ones now and then (see
 * {@code worldgen.FallenBranchFeature} and {@code tree.BranchShedding}). Nothing bumps into it (it is thin, like a
 * twig in the grass); it needs solid ground under it (or a casing of termite mud).
 */
public class FallenBranchBlock extends Block implements EatenInStages {
    public static final MapCodec<FallenBranchBlock> CODEC = simpleCodec(FallenBranchBlock::new);

    /** Which piece of the branch this block is. */
    public enum Part implements StringRepresentable {
        BASE("base", 5), MIDDLE("middle", 4), TIP("tip", 3);

        private final String name;
        /** How thick (pixels) the wood is before it is eaten. */
        final int thickness;

        Part(String name, int thickness) {
            this.name = name;
            this.thickness = thickness;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final int MAX_EATEN = 3;
    public static final IntegerProperty EATEN = IntegerProperty.create("eaten", 0, MAX_EATEN);

    public FallenBranchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(PART, Part.MIDDLE).setValue(EATEN, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PART, EATEN);
    }

    /** How thick (pixels) the wood of this block is now: a pixel thinner for each stage eaten, never under 2. */
    public static int thickness(BlockState state) {
        return Math.max(2, state.getValue(PART).thickness - state.getValue(EATEN));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double half = thickness(state) / 2.0;
        double top = thickness(state);
        return state.getValue(AXIS) == Direction.Axis.X
                ? Block.box(0.0, 0.0, 8.0 - half, 16.0, top, 8.0 + half)
                : Block.box(8.0 - half, 0.0, 0.0, 8.0 + half, top, 16.0);
    }

    // Placed by hand: a single middle piece lying the way the player faces.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    /**
     * It needs solid ground under it, or a casing of termite mud round it: termites dig a feeding gallery up under a
     * branch and case it in mud first (see {@code NestWalls#wallFirst}), and the mud holds it up over the gallery.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || isHeldInMud(level, pos);
    }

    /** Whether a crust of mud beside it has a plate against it. */
    private static boolean isHeldInMud(LevelReader level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (MudCrustBlock.hasFace(level.getBlockState(pos.relative(side)), side.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return state.setValue(EATEN, Math.min(MAX_EATEN, cut * (MAX_EATEN + 1) / Math.max(1, pieces)));
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return state.getValue(EATEN) * pieces / (MAX_EATEN + 1);
    }

    // Dry dead wood catches fire easily.

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }
}
