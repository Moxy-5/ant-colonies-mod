package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reeds (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 2; the common reed, Phragmites, in place of
 * papyrus at the user's word): leafy stalks like very tall grass standing in thick beds in shallow still and slow water,
 * a block column like sugar cane, 2 or 3 blocks over the water. No collision (animals, fish and players pass through).
 *
 * <p>A column stands in water 1 or 2 deep (its lower blocks {@link #WATERLOGGED}) on a soft bed
 * ({@link #GROUND}), or on the bed at the waterline with water beside it, as sugar cane does. It grows up a block at a
 * time on random ticks until {@link #aboveWater} blocks stand over the water's surface: 2 or 3, fixed per column by
 * where it stands (my picks). {@link #TOP} is true on the column's top block, drawn with the plume; the blocks under it
 * are drawn as stalks. A plume starts fresh (dusky purple-brown) and turns {@link #PALE} (silvery tan) over a few game
 * days, then a fresh one comes again (the user, 2026-11-22: "go fresh turning pale"; it follows the shared season source
 * once seasons come, M9.5). Broken, the blocks above fall with it, as with sugar cane.
 */
public class ReedBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<ReedBlock> CODEC = simpleCodec(ReedBlock::new);
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty PALE = BooleanProperty.create("pale");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** What a reed column roots in: river silt and mud, savanna mud, sand, clay, gravel and dirt. */
    public static final TagKey<Block> GROUND = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("antcolonies", "river_plant_ground"));
    /** The deepest water a reed column roots in (blocks; the plan's 1 or 2). */
    public static final int DEEPEST = 2;
    /**
     * The chance a random tick of a top block grows the column a block: about one block a game day (a block gets about 17
     * random ticks a day; my pick, a little slower than sugar cane, which a player farms).
     */
    static final float GROW_CHANCE = 1 / 16f;
    /** The chance a random tick of a top block turns its plume (fresh to pale, pale to a new fresh one): about 4 game days each. */
    static final float PLUME_CHANCE = 1 / 70f;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public ReedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, true).setValue(PALE, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, PALE, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        net.minecraft.world.phys.Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, 0, offset.z);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluid = level.getFluidState(pos);
        BlockState state = defaultBlockState().setValue(WATERLOGGED, fluid.is(FluidTags.WATER) && fluid.isSource())
                .setValue(TOP, !level.getBlockState(pos.above()).is(this));
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (!state.canSurvive(level, pos)) {
            level.scheduleTick(pos, this, 1);
        }
        if (facing == Direction.UP) {
            return state.setValue(TOP, !facingState.is(this));
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(this)) {
            return true;
        }
        return below.is(GROUND) && rootsHere(level, pos);
    }

    /**
     * Whether a reed column may root at {@code pos} (its bottom block, on the bed): in water 1 or 2 deep, or out of the
     * water with water beside the bed (the waterline), as sugar cane.
     */
    public static boolean rootsHere(LevelReader level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return !level.getFluidState(pos.above(DEEPEST)).is(FluidTags.WATER);
        }
        return atWaterline(level, pos);
    }

    /** Whether {@code pos}, out of the water on the bed, has water beside it or beside its bed (the waterline; shared with hippo grass). */
    public static boolean atWaterline(LevelReader level, BlockPos pos) {
        BlockPos bed = pos.below();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(bed.relative(side)).is(FluidTags.WATER) || level.getFluidState(pos.relative(side)).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** The column's bottom block, walking down from {@code pos}. */
    public BlockPos bottom(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos at = pos.mutable();
        while (level.getBlockState(at.below()).is(this)) {
            at.move(Direction.DOWN);
        }
        return at.immutable();
    }

    /** How many blocks a column rooted at {@code bottom} stands over the water: 2 or 3, fixed by where it stands (my picks). */
    public static int aboveWater(BlockPos bottom) {
        return 2 + (int) (Mth.getSeed(bottom.getX(), 0, bottom.getZ()) >>> 7 & 1);
    }

    /** How tall a column rooted at {@code bottom} grows: its blocks in the water and {@link #aboveWater} over it. */
    public static int fullHeight(BlockGetter level, BlockPos bottom) {
        int under = 0;
        while (under < DEEPEST && level.getFluidState(bottom.above(under)).is(FluidTags.WATER)) {
            under++;
        }
        return under + aboveWater(bottom);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(TOP);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < PLUME_CHANCE) {
            level.setBlock(pos, state.setValue(PALE, !state.getValue(PALE)), Block.UPDATE_CLIENTS);
            return;
        }
        if (random.nextFloat() < GROW_CHANCE) {
            grow(level, pos);
        }
    }

    /** Grows the column whose top is {@code top} a block, if it is not yet full height and there is air over it. */
    public boolean grow(ServerLevel level, BlockPos top) {
        BlockPos bottom = bottom(level, top), up = top.above();
        if (top.getY() - bottom.getY() + 1 >= fullHeight(level, bottom) || !level.isEmptyBlock(up)) {
            return false;
        }
        level.setBlock(up, defaultBlockState(), Block.UPDATE_ALL);     // a new top with a fresh plume; the old top turns stalk
        return true;
    }
}
