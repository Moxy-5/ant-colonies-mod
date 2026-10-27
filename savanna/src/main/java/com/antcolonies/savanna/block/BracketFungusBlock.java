package com.antcolonies.savanna.block;

import java.util.EnumMap;
import java.util.Map;

import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.RotMarks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bracket fungus (dead wood for the raids, part 4): the hard woody shelf of a heart rot fungus (real: <i>Ganoderma</i>
 * and <i>Phellinus</i> on savanna acacias), sticking out of the bark of a tree whose core is rotting away. It grows from
 * the trunk behind it ({@link #FACING} points away from the trunk) and drops off when that wood is gone (the tree fell
 * or was cut).
 * <p>
 * It grows with time (the looks picked by the user 2026-10-26, tools/rot_looks_art.py; real <i>Ganoderma</i> brackets
 * are perennial, a new layer each year): {@link #AGE} 0 a soft white knob, 1 a thin young shelf with a bright white
 * growing rim, 2 a thick woody hoof banded brown by year, 3 old, darker and cracked, its top drifted with its own
 * cocoa-brown spores. Once grown it may be {@link #TIERS} (shelves one over another) or start a new knob on the block
 * above, and its spores stain the bark under it ({@link RotMarks.Mark#SPORES}). Speeds my picks: about 1 day a knob,
 * 3 days young, 10 days grown, then old (at Minecraft's normal random tick speed, while the place is loaded).
 */
public class BracketFungusBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BracketFungusBlock> CODEC = simpleCodec(BracketFungusBlock::new);
    public static final int KNOB = 0, YOUNG = 1, GROWN = 2, OLD = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    /** Grown shelves one over another on this block (only ever set from {@link #GROWN} on). */
    public static final BooleanProperty TIERS = BooleanProperty.create("tiers");
    /** The chance each random tick grows it on, by age (my picks: about 1, 3 and 10 days at the normal tick speed). */
    private static final int[] GROW_ONE_IN = {18, 53, 176};
    /** The chance a bracket turning grown gets tiers, and starts a new knob on the block above (my picks). */
    private static final double TIERS_CHANCE = 1 / 3.0, KNOB_ABOVE_CHANCE = 1 / 3.0;
    /** The shelf against the trunk facing north (the wall at z 16), by age; tiers fill the block's height. */
    private static final VoxelShape[] NORTH = {box(5, 5, 12, 11, 9, 16), box(4, 6, 10, 12, 8, 16), box(2, 3, 7, 14, 9, 16),
            box(1, 2, 5, 15, 9, 16)};
    private static final VoxelShape NORTH_TIERS = box(2, 1, 8, 14, 13, 16);
    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape[] shapes = new VoxelShape[NORTH.length + 1];
            for (int i = 0; i <= NORTH.length; i++) {
                shapes[i] = turn(i < NORTH.length ? NORTH[i] : NORTH_TIERS, d);
            }
            SHAPES.put(d, shapes);
        }
    }

    public BracketFungusBlock(Properties properties) {
        super(properties.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(AGE, GROWN).setValue(TIERS, false));
    }

    /** A box made facing north, turned to face {@code d} (Minecraft's turn: clockwise seen from above). */
    private static VoxelShape turn(VoxelShape north, Direction d) {
        var b = north.bounds();
        double x1 = b.minX * 16, x2 = b.maxX * 16, y1 = b.minY * 16, y2 = b.maxY * 16, z1 = b.minZ * 16, z2 = b.maxZ * 16;
        return switch (d) {
            case SOUTH -> box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> box(16 - z2, y1, x1, 16 - z1, y2, x2);
            default -> north;
        };
    }

    /** A bracket of this age facing {@code d}; tiers only from grown on. */
    public BlockState aged(Direction d, int age, boolean tiers) {
        return defaultBlockState().setValue(FACING, d).setValue(AGE, age).setValue(TIERS, tiers && age >= GROWN);
    }

    /**
     * A grown bracket's spores settle on the bark below it: the log under the wood it grows from turns spore-dusted
     * (a wound stays a wound).
     */
    public static void dust(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos below = pos.relative(state.getValue(FACING).getOpposite()).below();
        BlockState dusted = RotMarks.marked(level.getBlockState(below), RotMarks.Mark.SPORES);
        if (dusted != null) {
            level.setBlock(below, dusted, Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < OLD;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= OLD || random.nextInt(GROW_ONE_IN[age]) != 0) {
            return;
        }
        BlockState next = state.setValue(AGE, age + 1);
        if (age + 1 == GROWN) {
            next = next.setValue(TIERS, random.nextDouble() < TIERS_CHANCE);
            BlockPos above = pos.above();
            BlockState knob = defaultBlockState().setValue(FACING, state.getValue(FACING)).setValue(AGE, KNOB);
            if (random.nextDouble() < KNOB_ABOVE_CHANCE && level.getBlockState(above).isAir() && canSurvive(knob, level, above)) {
                level.setBlock(above, knob, Block.UPDATE_ALL); // a new shelf starting over the grown one
            }
        }
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (age + 1 >= GROWN) {
            dust(level, pos, next);
        }
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE, TIERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING))[state.getValue(TIERS) ? NORTH.length : state.getValue(AGE)];
    }

    /** The trunk wood it grows from. */
    public static boolean holdsOn(BlockState behind) {
        return behind.is(TreeBlocks.TREE_WOOD) || behind.is(TreeBlocks.ROTTEN_WOOD.get());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return holdsOn(level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** A player sets it on the side of a trunk they click. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }
}
