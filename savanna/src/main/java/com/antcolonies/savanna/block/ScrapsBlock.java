package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;

/**
 * Scraps on the river bed (M9 river life, 2026-11-15; look B picked by the user in
 * {@code python tools/model_viewer.py droppings}): a thin film of pale flecks of flesh on a soft brown dusting, one block
 * for flesh scraps and fish droppings (they feed the same eaters and look alike that small). Every fish eaten in the water
 * ({@link #leave}) sends a puff of bits sinking and leaves a fresh film on the bed under it; the crab picks at it
 * ({@link #nibble}). Generalized, fun first (the user's rule for animals).
 *
 * <p>{@link #AMOUNT}: 0 fresh, 1 settled, 2 nearly gone; untouched it thins a step on random ticks and is gone in about
 * a game day (my pick), so it never piles up and nothing has to eat it to live. Like the algae it always holds water.
 */
public class ScrapsBlock extends BushBlock implements LiquidBlockContainer {
    public static final MapCodec<ScrapsBlock> CODEC = simpleCodec(ScrapsBlock::new);
    public static final IntegerProperty AMOUNT = IntegerProperty.create("amount", 0, 2);
    public static final int FRESH = 0, SETTLED = 1, NEARLY_GONE = 2;
    /** The chance a random tick thins it a step: about 3 steps a game day (a block gets ~17 random ticks a day). */
    static final float FADE_CHANCE = 1 / 6f;
    /** How far down a puff sinks looking for the bed. */
    static final int DEEPEST = 16;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public ScrapsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AMOUNT, FRESH));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AMOUNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(Blocks.MAGMA_BLOCK);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState water = context.getLevel().getFluidState(context.getClickedPos());
        return water.is(FluidTags.WATER) && water.isSource() ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        BlockState after = super.updateShape(state, facing, facingState, level, pos, facingPos);
        if (!after.isAir()) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return after;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < FADE_CHANCE) {
            thin(level, pos, state);
        }
    }

    /** One step thinner, or gone (back to plain water) when nearly gone already. */
    private static void thin(Level level, BlockPos pos, BlockState state) {
        int amount = state.getValue(AMOUNT);
        level.setBlock(pos, amount < NEARLY_GONE ? state.setValue(AMOUNT, amount + 1) : Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * An eater's bite (the crab, 2026-11-15): the film a step thinner, with a few bits stirred up. Whether there was
     * anything here to eat.
     */
    public static boolean nibble(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(SavannaBlocks.SCRAPS.get())) {
            return false;
        }
        thin(level, pos, state);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE_MEAL)),
                    pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 2, 0.15, 0.03, 0.15, 0.01);
        }
        return true;
    }

    /**
     * A fish eaten at {@code at} (2026-11-15, the user: "every fish eaten leaves a little something"): a puff of pale bits
     * and raw fish bits sinking, and a fresh film on the bed under it (or beside it, when algae or remains already lie
     * there; a film already there is made fresh again). Nothing out of the water. Returns where the film went, or null.
     */
    @Nullable
    public static BlockPos leave(ServerLevel level, Vec3 at) {
        BlockPos pos = nearestWater(level, at);
        if (pos == null) {
            return null;
        }
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE_MEAL)), at.x, at.y, at.z,
                8, 0.2, 0.15, 0.2, 0.02);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SavannaItems.TILAPIA.get())), at.x, at.y, at.z,
                4, 0.2, 0.15, 0.2, 0.02);
        for (int d = 0; d < DEEPEST && level.getFluidState(pos.below()).is(FluidTags.WATER); d++) {
            pos = pos.below();
        }
        BlockState fresh = SavannaBlocks.SCRAPS.get().defaultBlockState();
        if (place(level, pos, fresh)) {
            return pos;
        }
        for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom())) {
            if (place(level, pos.relative(side), fresh)) {
                return pos.relative(side);
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(level.getRandom())) {   // (algae all round: the corners)
            BlockPos corner = pos.relative(side).relative(side.getClockWise());
            if (place(level, corner, fresh)) {
                return corner;
            }
        }
        return null;
    }

    /**
     * The water the flecks start sinking from: the block at the spot, or else the nearest water in the block under it or
     * round either (BUGS 279/298: a catch at the top of the water has the mouth poking out, and one against a wall has it
     * inside the stone, and both left nothing); null with no water that near.
     */
    @Nullable
    private static BlockPos nearestWater(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            return pos;
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 0, 1))) {
            double d = Vec3.atCenterOf(p).distanceToSqr(at);
            if (d < bestD && level.getFluidState(p).is(FluidTags.WATER)) {
                best = p.immutable();
                bestD = d;
            }
        }
        return best;
    }

    private static boolean place(ServerLevel level, BlockPos pos, BlockState fresh) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState there = level.getBlockState(pos);
        if (there.is(fresh.getBlock())) {
            level.setBlock(pos, fresh, Block.UPDATE_CLIENTS);
            return true;
        }
        if (there.is(Blocks.WATER) && there.getFluidState().isSource() && fresh.canSurvive(level, pos)) {
            level.setBlock(pos, fresh, Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }
}
