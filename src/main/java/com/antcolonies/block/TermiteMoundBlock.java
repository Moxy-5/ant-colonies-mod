package com.antcolonies.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Termite mound soil (user's design, step 3b). A block is built from wet soil pellets in layers: layers 1 to 4 each
 * add a quarter of the height (still cracked as the soil dries, and easy to break), and on layer 5 the termites seal
 * the cracks and the block is finished. A finished block then dries and hardens with age (fresh, setting, dry, old),
 * paler and harder each time: like dirt, like clay, then a pickaxe is needed, then like stone. Real mounds bake pale and
 * hard as concrete in the sun.
 *
 * <p>The user's rule: wet soil must firm up before more is stacked on it, so a block can only be built on once it is
 * finished and has set ({@link #canBuildOn}). That gives the mound its slow, natural pace. Textures and models come
 * from {@code tools/termite_blocks.py}.
 */
public class TermiteMoundBlock extends Block {
    /** The layer on which the block is finished (layers 1 to 4 build it up a quarter at a time). */
    public static final int FINISHED = 5;
    public static final int OLDEST = 3;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, FINISHED);
    /** How dry and hard a finished block is: 0 fresh, 1 setting, 2 dry, 3 old. Only finished blocks age. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, OLDEST);

    /** How hard a block still being built is to break (wet, cracked soil). */
    private static final float BUILDING_HARDNESS = 0.3F;
    /** How hard a finished block is at each age (dirt is 0.5, clay 0.6, stone 1.5). */
    private static final float[] HARDNESS = {0.6F, 1.0F, 1.5F, 2.5F};
    /**
     * On each random tick a finished block grows one age older with a chance of 1 in this (a block gets a random tick
     * about once a minute at the normal tick speed): setting after about 20 minutes, dry after about 50 more, old
     * after about 2.5 hours more.
     */
    private static final int[] AGE_CHANCE = {20, 45, 130};

    private static final VoxelShape[] SHAPES = new VoxelShape[FINISHED + 1];

    static {
        for (int layers = 1; layers <= FINISHED; layers++) {
            SHAPES[layers] = Block.box(0.0, 0.0, 0.0, 16.0, Math.min(4, layers) * 4.0, 16.0);
        }
    }

    public TermiteMoundBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, AGE);
    }

    public static boolean isFinished(BlockState state) {
        return state.getValue(LAYERS) == FINISHED;
    }

    /**
     * Whether termites may build a new block on top of this one: a finished mound block that has set (wet soil must
     * firm up first), or any other block with a solid top.
     */
    public static boolean canBuildOn(BlockState below, LevelReader level, BlockPos belowPos) {
        if (below.getBlock() instanceof TermiteMoundBlock) {
            return isFinished(below) && below.getValue(AGE) >= 1;
        }
        return below.isFaceSturdy(level, belowPos, Direction.UP);
    }

    /**
     * Adds one layer of soil at {@code pos}: starts a new block in an empty space (only on something it may be built on)
     * or adds to a block still being built. False if nothing was added. For the termites' building job.
     */
    public boolean addLayer(Level level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (here.is(this)) {
            if (isFinished(here)) {
                return false;
            }
            return level.setBlockAndUpdate(pos, here.setValue(LAYERS, here.getValue(LAYERS) + 1));
        }
        if (!here.canBeReplaced() || !canBuildOn(level.getBlockState(pos.below()), level, pos.below())) {
            return false;
        }
        return level.setBlockAndUpdate(pos, defaultBlockState());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    // A block still being built needs a solid floor; a finished one stays where it is (a mound is not undermined by one gap).
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (isFinished(state)) {
            return true;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // Placing mound soil on a block still being built adds a layer, like snow.
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (context.getItemInHand().is(asItem()) && !isFinished(state)) {
            return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
        }
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) {
            return here.setValue(LAYERS, Math.min(FINISHED, here.getValue(LAYERS) + 1));
        }
        return defaultBlockState();
    }

    // --- Drying and hardening ---

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return isFinished(state) && state.getValue(AGE) < OLDEST;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (random.nextInt(AGE_CHANCE[age]) == 0) {
            level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        }
    }

    /** How hard the block is to break now. */
    public static float hardness(BlockState state) {
        return isFinished(state) ? HARDNESS[state.getValue(AGE)] : BUILDING_HARDNESS;
    }

    /** Whether the block is hard enough that it breaks like stone (needs a pickaxe) rather than like dirt (a shovel helps). */
    public static boolean isStoneHard(BlockState state) {
        return isFinished(state) && state.getValue(AGE) >= 2;
    }

    // Breaking speed follows the age: young soil breaks like dirt (a shovel is quick), dry and old soil like stone
    // (a pickaxe is needed, by hand it is very slow). Worked out as for dirt or stone, with this block's hardness.
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState feel = isStoneHard(state) ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        int divisor = player.hasCorrectToolForDrops(feel) ? 30 : 100;
        return player.getDigSpeed(feel, pos) / hardness(state) / divisor;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        if (!isFinished(state) || state.getValue(AGE) == 0) {
            return SoundType.MUD;
        }
        return state.getValue(AGE) == 1 ? SoundType.PACKED_MUD : SoundType.MUD_BRICKS;
    }
}
