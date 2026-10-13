package com.antcolonies.block;

import com.antcolonies.registry.ModItems;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A giant Termitomyces (like the real Termitomyces titanicus, whose cap can be a metre across): a rare mushroom three
 * blocks tall that grows out of a giant button ({@link TermitomycesBlock#GIANT}). Its {@link #PART}s are the foot
 * ({@link #FOOT}), the middle of the stalk and the top with the cap, which overhangs the blocks round it. It comes up as a
 * tall {@link #CLOSED} spike, its cap {@link #OPEN}s, and after about two days it is {@link #WILTED} and then gone, like
 * the small one. Breaking any part breaks the whole mushroom; only the foot drops the mushrooms (a whole heap of them),
 * so they drop once. A thin stalk collides, the cap only over its own block.
 */
public class GiantTermitomycesBlock extends Block {
    public static final MapCodec<GiantTermitomycesBlock> CODEC = simpleCodec(GiantTermitomycesBlock::new);

    public static final int FOOT = 0;
    public static final int MIDDLE = 1;
    public static final int TOP = 2;
    public static final IntegerProperty PART = IntegerProperty.create("part", FOOT, TOP);

    public static final int CLOSED = 0;
    public static final int OPEN = 1;
    public static final int WILTED = 2;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", CLOSED, WILTED);

    /** Out of how many random ticks one moves it on from each stage (as for the small one: minutes, 2 days, a day). */
    private static final int[] STAGE_CHANCE = {4, 40, 16};

    private static final VoxelShape STALK = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
    private static final VoxelShape FOOT_SHAPE = Shapes.or(STALK, Block.box(5.0, 0.0, 5.0, 11.0, 4.0, 11.0));
    private static final VoxelShape MIDDLE_CLOSED = Shapes.or(Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0), Block.box(3.0, 8.0, 3.0, 13.0, 16.0, 13.0));
    private static final VoxelShape TOP_CLOSED = Block.box(4.0, 0.0, 4.0, 12.0, 15.0, 12.0);
    private static final VoxelShape TOP_OPEN = Shapes.or(Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0), Block.box(0.0, 8.0, 0.0, 16.0, 14.0, 16.0));
    private static final VoxelShape TOP_WILTED = Shapes.or(Block.box(6.0, 0.0, 6.0, 10.0, 4.0, 10.0), Block.box(0.0, 4.0, 0.0, 16.0, 11.0, 16.0));

    public GiantTermitomycesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, FOOT).setValue(STAGE, CLOSED));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, STAGE);
    }

    /** Whether a giant mushroom fits with its foot at {@code foot}: earth under it and room for its stalk and cap. */
    public boolean fitsAt(LevelReader level, BlockPos foot) {
        if (!TermitomycesBlock.isGround(level.getBlockState(foot.below()))) {
            return false;
        }
        for (int part = FOOT; part <= TOP; part++) {
            BlockPos pos = foot.above(part);
            BlockState here = level.getBlockState(pos);
            boolean button = part == FOOT && here.getBlock() instanceof TermitomycesBlock;
            if (!button && !(here.canBeReplaced() && here.getFluidState().isEmpty())) {
                return false;
            }
        }
        return true;
    }

    /** Puts up a young (closed) giant mushroom with its foot at {@code foot} if it fits there; false if not. */
    public boolean growAt(Level level, BlockPos foot) {
        if (!fitsAt(level, foot)) {
            return false;
        }
        for (int part = FOOT; part <= TOP; part++) {
            level.setBlock(foot.above(part), defaultBlockState().setValue(PART, part), 3);
        }
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int stage = state.getValue(STAGE);
        return switch (state.getValue(PART)) {
            case FOOT -> FOOT_SHAPE;
            case MIDDLE -> stage == CLOSED ? MIDDLE_CLOSED : STALK;
            default -> stage == CLOSED ? TOP_CLOSED : stage == OPEN ? TOP_OPEN : TOP_WILTED;
        };
    }

    // The foot stands on earth, every other part on the part under it.
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        int part = state.getValue(PART);
        BlockState below = level.getBlockState(pos.below());
        return part == FOOT ? TermitomycesBlock.isGround(below) : below.is(this) && below.getValue(PART) == part - 1;
    }

    // When a part goes, the parts beside it go too (the foot, dropping the mushrooms, last or first).
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        int part = state.getValue(PART);
        if (direction == Direction.UP && part < TOP && !(neighborState.is(this) && neighborState.getValue(PART) == part + 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // A player in creative breaking the stalk or the cap gets nothing: the foot goes first, quietly.
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != FOOT) {
            BlockPos foot = pos.below(state.getValue(PART));
            if (level.getBlockState(foot).is(this)) {
                level.setBlock(foot, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(PART) == FOOT;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(STAGE_CHANCE[state.getValue(STAGE)]) == 0) {
            growOn(level, pos);
        }
    }

    /** Moves the giant mushroom whose foot is at {@code foot} on to its next stage; a wilted one is gone (nothing dropped). */
    public void growOn(Level level, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        if (!state.is(this)) {
            return;
        }
        int stage = state.getValue(STAGE);
        if (stage == WILTED) {
            level.removeBlock(foot, false); // the stalk and the cap follow; a wilted one drops nothing
            return;
        }
        for (int part = FOOT; part <= TOP; part++) {
            BlockPos pos = foot.above(part);
            BlockState here = level.getBlockState(pos);
            if (here.is(this)) {
                level.setBlock(pos, here.setValue(STAGE, stage + 1), 3);
            }
        }
    }

    // White spores drift down from under an open cap.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) == TOP && state.getValue(STAGE) == OPEN && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() - 0.6 + random.nextDouble() * 2.2, pos.getY() + 0.45,
                    pos.getZ() - 0.6 + random.nextDouble() * 2.2, 0.0, -0.02, 0.0);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.TERMITOMYCES.get());
    }
}
