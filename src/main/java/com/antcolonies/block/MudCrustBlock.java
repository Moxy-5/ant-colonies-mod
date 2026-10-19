package com.antcolonies.block;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.TreeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin crust of termite mud (the user's decision, 2026-10-14; look C, "crumbly with pores", picked in
 * references/mud_crust_viewer.html): real termites plaster soil pellets and saliva over anything they want covered,
 * a gallery's open side, the wood they eat, a runway. One block holds a plate of mud on any of its 6 faces, like
 * vanilla glow lichen, but standing on its own (sheeting spans gaps, it needs nothing behind it).
 *
 * <p>A plate on a face walls off the cell on that side: a termite gallery there has a wall, and its insects can cling
 * to it ({@code isFaceSturdy}). A player breaks it with a punch or two (a break-in for the alarm, see
 * {@code Alarm#isBreakIn}). It dries with age like the mound soil (fresh, setting, dry, old). A plate whose cell is
 * filled again (soil, a stone, any full block that is not food) is no longer needed and crumbles away.
 */
public class MudCrustBlock extends Block {
    public static final IntegerProperty AGE = MoundSoil.AGE;
    /** One plate per face: the one on {@code NORTH} walls off the cell to the north. */
    public static final Map<Direction, BooleanProperty> FACES = PipeBlock.PROPERTY_BY_DIRECTION;
    /** How thick a plate is (1 pixel, look C). */
    private static final double THICKNESS = 1.0;
    /** How hard the crust is at each age: thin mud, a punch or two (dirt is 0.5). */
    private static final float[] HARDNESS = {0.15F, 0.2F, 0.3F, 0.4F};
    /** On each random tick a plate that is not needed any more crumbles away with a chance of 1 in this. */
    private static final int CRUMBLE_CHANCE = 4;

    private static final Map<Direction, VoxelShape> PLATES = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
            Direction.SOUTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, THICKNESS, 16, 16),
            Direction.EAST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
            Direction.DOWN, Block.box(0, 0, 0, 16, THICKNESS, 16),
            Direction.UP, Block.box(0, 16 - THICKNESS, 0, 16, 16, 16));
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public MudCrustBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(AGE, 0);
        for (BooleanProperty face : FACES.values()) {
            state = state.setValue(face, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        FACES.values().forEach(builder::add);
    }

    /** Whether this block is a mud crust. */
    public static boolean isCrust(BlockState state) {
        return state.getBlock() instanceof MudCrustBlock;
    }

    /** Whether the crust has a plate on this face (walling off the cell on that side). False for any other block. */
    public static boolean hasFace(BlockState state, Direction face) {
        return isCrust(state) && state.getValue(FACES.get(face));
    }

    /** The crust with one more plate (fresh mud if it was not a crust yet). */
    public BlockState withFace(BlockState state, Direction face) {
        BlockState crust = isCrust(state) ? state : defaultBlockState();
        return crust.setValue(FACES.get(face), true);
    }

    private static int faceCount(BlockState state) {
        int count = 0;
        for (BooleanProperty face : FACES.values()) {
            count += state.getValue(face) ? 1 : 0;
        }
        return count;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, key -> {
            VoxelShape shape = Shapes.empty();
            for (Map.Entry<Direction, BooleanProperty> face : FACES.entrySet()) {
                if (key.getValue(face.getValue())) {
                    shape = Shapes.or(shape, PLATES.get(face.getKey()));
                }
            }
            return shape.isEmpty() ? PLATES.get(Direction.DOWN) : shape;
        });
    }

    // --- Placing by hand (to look at it): a plate against the face clicked, more plates added to a crust ---

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        return withFace(there, context.getClickedFace().getOpposite());
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && !hasFace(state, context.getClickedFace().getOpposite())
                && faceCount(state) < FACES.size();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    // --- Drying, and crumbling once nobody needs it ---

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState crust = state;
        for (Map.Entry<Direction, BooleanProperty> face : FACES.entrySet()) {
            if (crust.getValue(face.getValue()) && !isNeeded(level, pos.relative(face.getKey())) && random.nextInt(CRUMBLE_CHANCE) == 0) {
                crust = crust.setValue(face.getValue(), false);
            }
        }
        if (faceCount(crust) == 0) {
            level.removeBlock(pos, false);
            return;
        }
        if (crust != state) {
            level.setBlock(pos, crust, Block.UPDATE_CLIENTS);
            return;
        }
        if (state.getValue(AGE) < MoundSoil.OLDEST) {
            MoundSoil.age(state, level, pos, random);
        }
    }

    /**
     * Whether a plate over this cell still walls something off: the cell is open (a gallery, a hole) or holds the
     * insects' food (wood they are eating under it, a dead tree's wood too). A cell filled again with soil or stone needs no wall.
     */
    private static boolean isNeeded(LevelReader level, BlockPos cell) {
        if (!level.hasChunkAt(cell)) {
            return true;
        }
        BlockState state = level.getBlockState(cell);
        return !state.isCollisionShapeFullBlock(level, cell) || state.getBlock() instanceof DeadWoodBlock
                || state.getBlock() instanceof FallenBranchBlock || state.is(TreeBlocks.TERMITE_WOOD);
    }

    // --- Breaking: thin mud, a punch or two; harder as it dries ---

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return MoundSoil.destroyProgress(false, HARDNESS[state.getValue(AGE)], player, pos);
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return MoundSoil.sound(state.getValue(AGE));
    }
}
