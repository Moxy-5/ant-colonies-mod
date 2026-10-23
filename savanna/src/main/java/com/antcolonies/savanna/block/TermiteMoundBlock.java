package com.antcolonies.savanna.block;

import com.antcolonies.block.PlugBlock;
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
public class TermiteMoundBlock extends Block implements PlugBlock {
    /** The layer on which the block is finished (layers 1 to 4 build it up a quarter at a time). */
    public static final int FINISHED = 5;
    public static final int OLDEST = MoundSoil.OLDEST;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, FINISHED);
    /** How dry and hard a finished block is: 0 fresh, 1 setting, 2 dry, 3 old (see {@link MoundSoil}). Only finished blocks age. */
    public static final IntegerProperty AGE = MoundSoil.AGE;

    /** How hard a block still being built is to break (wet, cracked soil). */
    private static final float BUILDING_HARDNESS = 0.3F;
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

    /** Packed into a hole finished and fresh, so it sets and hardens with age like the mound. */
    @Override
    public BlockState packedState() {
        return defaultBlockState().setValue(LAYERS, FINISHED);
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
     * Whether termites may start a new block in this empty space: on top of something it may be built on (see
     * {@link #canBuildOn}), or, where there is nothing solid under it (over a tunnel in the mound, over a ramp or a spire
     * piece), built out from a finished block that has set right beside it (or a tunnel block), like an arch. Never on
     * wet soil that has not set.
     */
    public static boolean canStartAt(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (canBuildOn(under, level, below)) {
            return true;
        }
        if (under.getBlock() instanceof TermiteMoundBlock
                || !under.getCollisionShape(level, below).isEmpty() && !(under.getBlock() instanceof MoundPieceBlock)) {
            return false;
        }
        return besideSetSoil(level, pos);
    }

    /**
     * True if a finished mound block that has set stands right beside this space (sideways), or a tunnel block (soil with
     * a channel carved through it, as firm as the soil it was).
     */
    private static boolean besideSetSoil(LevelReader level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState beside = level.getBlockState(pos.relative(side));
            if (beside.getBlock() instanceof TermiteMoundBlock && isFinished(beside) && beside.getValue(AGE) >= 1
                    || beside.getBlock() instanceof TermiteTunnelBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds one layer of soil at {@code pos}: starts a new block in an empty space (only where {@link #canStartAt}
     * allows) or adds to a block still being built. False if nothing was added. For the termites' building job.
     */
    /**
     * Whether soil can go into a cell holding this: open space (air, grass) or a thin crust of mud (a wall of the nest
     * that a whole block of soil walls off as well: the termites pack it full, e.g. under the foot of a mound on a slope).
     */
    public static boolean isRoomForSoil(BlockState state) {
        return state.canBeReplaced() || MudCrustBlock.isCrust(state);
    }

    public boolean addLayer(Level level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (here.is(this)) {
            if (isFinished(here)) {
                return false;
            }
            return level.setBlockAndUpdate(pos, here.setValue(LAYERS, here.getValue(LAYERS) + 1));
        }
        if (!isRoomForSoil(here) || !canStartAt(level, pos)) {
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

    // A block still being built needs a solid floor, or set soil beside it to stick to (built out over a tunnel); a
    // finished one stays where it is (a mound is not undermined by one gap).
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (isFinished(state)) {
            return true;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || besideSetSoil(level, pos);
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
        MoundSoil.age(state, level, pos, random);
    }

    /** How hard the block is to break now. */
    public static float hardness(BlockState state) {
        return isFinished(state) ? MoundSoil.hardness(state.getValue(AGE)) : BUILDING_HARDNESS;
    }

    /** Whether the block is hard enough that it breaks like stone (needs a pickaxe) rather than like dirt (a shovel helps). */
    public static boolean isStoneHard(BlockState state) {
        return isFinished(state) && MoundSoil.isStoneHard(state.getValue(AGE));
    }

    // Breaking speed follows the age: young soil breaks like dirt (a shovel is quick), dry and old soil like stone
    // (a pickaxe is needed, by hand it is very slow). Worked out as for dirt or stone, with this block's hardness.
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return MoundSoil.destroyProgress(isStoneHard(state), hardness(state), player, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return isFinished(state) ? MoundSoil.sound(state.getValue(AGE)) : SoundType.MUD;
    }
}
