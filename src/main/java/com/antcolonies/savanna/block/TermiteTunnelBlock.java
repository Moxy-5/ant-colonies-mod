package com.antcolonies.block;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.Channel;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Termite tunnel (cathedral mound, user's idea): finished mound soil with a termite-sized channel through it, 6 pixels
 * wide and 6 tall, the way a real mound looks cut open. The channel can open out of any of the 6 sides ({@link #OPEN}),
 * so one block is a straight tunnel, a bend, a T, a crossing or a tunnel turning upward.
 *
 * <p>It joins up on its own, like a fence: a side next to another tunnel block (or any channel block that can open toward
 * it, such as a hollow spire piece above it) is always open (both blocks open toward each other), a side against any
 * other solid block is always closed. A side toward open air (the mound's shaft, or
 * outside) is never opened on its own: it keeps what it was, so only a termite (later) or a player with a shovel opens
 * a window there, and it stays open until it is closed again or something is put against it.
 *
 * <p>For players and every other creature it is a full block; termite workers (the entity tag
 * {@code antcolonies:fits_termite_tunnels}) crawl through the channel ({@link Channel}): for them the block is solid
 * only where its soil is. Light seeps a little way into a channel through its openings. It dries and hardens with age like the rest of the mound
 * ({@link MoundSoil}). The models (a frame and a plug for each closed side) come from {@code tools/termite_blocks.py}.
 */
public class TermiteTunnelBlock extends MoundPieceBlock implements Channel {
    /** Whether the channel opens out of each side. */
    public static final Map<Direction, BooleanProperty> OPEN = PipeBlock.PROPERTY_BY_DIRECTION;
    /** The channel runs between these pixels across the block (6 pixels wide and tall, in the middle). */
    public static final int CHANNEL_FROM = 5;
    public static final int CHANNEL_TO = 11;
    /** How much a block with an open channel dims light passing through it (a closed one stops it). */
    private static final int OPEN_LIGHT_BLOCK = 4;

    /** The soil of the block (what hides the light and the faces behind it), by the mask of open sides. */
    private static final VoxelShape[] SOIL = new VoxelShape[64];

    static {
        VoxelShape middle = Block.box(CHANNEL_FROM, CHANNEL_FROM, CHANNEL_FROM, CHANNEL_TO, CHANNEL_TO, CHANNEL_TO);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape hollow = middle;
            for (Direction side : Direction.values()) {
                if ((mask & (1 << side.get3DDataValue())) != 0) {
                    hollow = Shapes.or(hollow, arm(side));
                }
            }
            SOIL[mask] = Shapes.join(Shapes.block(), hollow, BooleanOp.ONLY_FIRST).optimize();
        }
    }

    public TermiteTunnelBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(MoundSoil.AGE, 0);
        for (BooleanProperty open : OPEN.values()) {
            state = state.setValue(open, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MoundSoil.AGE);
        OPEN.values().forEach(builder::add);
    }

    /** The part of the channel that reaches from the middle out to one side of the block. */
    private static VoxelShape arm(Direction side) {
        double[] from = {CHANNEL_FROM, CHANNEL_FROM, CHANNEL_FROM};
        double[] to = {CHANNEL_TO, CHANNEL_TO, CHANNEL_TO};
        int axis = side.getAxis().ordinal();
        if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            from[axis] = CHANNEL_TO;
            to[axis] = 16;
        } else {
            from[axis] = 0;
            to[axis] = CHANNEL_FROM;
        }
        return Block.box(from[0], from[1], from[2], to[0], to[1], to[2]);
    }

    /** The sides the channel opens out of, as a mask (bit {@code Direction.get3DDataValue()}). */
    public static int openMask(BlockState state) {
        int mask = 0;
        for (Map.Entry<Direction, BooleanProperty> entry : OPEN.entrySet()) {
            if (state.getValue(entry.getValue())) {
                mask |= 1 << entry.getKey().get3DDataValue();
            }
        }
        return mask;
    }

    /** For players and every creature: a full block. */
    @Override
    protected VoxelShape shape(BlockState state) {
        return Shapes.block();
    }

    /** For an insect that fits through the channel: the soil only, so it can be in the channel. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && fits(entityContext.getEntity())) {
            return SOIL[openMask(state)];
        }
        return shape(state);
    }

    @Override
    public boolean fits(@Nullable Entity insect) {
        return insect != null && insect.getType().is(ModTags.FITS_TERMITE_TUNNELS);
    }

    @Override
    public boolean canJoin(BlockState state, Direction side) {
        return true;
    }

    @Override
    public int openSides(BlockGetter level, BlockPos pos, BlockState state) {
        return openMask(state);
    }

    /** What hides the faces of the blocks beside it and the light: the soil, without the channel. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SOIL[openMask(state)];
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return openMask(state) == 0 ? level.getMaxLightLevel() : OPEN_LIGHT_BLOCK;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    /**
     * Whether the side toward {@code side} should be open: always toward another tunnel block (or another channel block
     * that can open toward it), never against any other solid block, and toward open air as it is now ({@code now}:
     * only a window, or a termite opening it into the mound's shaft, opens it).
     */
    private static boolean opensToward(BlockGetter level, BlockPos pos, Direction side, boolean now) {
        BlockPos beside = pos.relative(side);
        BlockState state = level.getBlockState(beside);
        if (state.getBlock() instanceof Channel channel && channel.canJoin(state, side.getOpposite())) {
            return true;
        }
        if (state.getCollisionShape(level, beside).isEmpty()) {
            return now;
        }
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : OPEN.entrySet()) {
            state = state.setValue(entry.getValue(), opensToward(context.getLevel(), context.getClickedPos(), entry.getKey(), false));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        BooleanProperty open = OPEN.get(direction);
        return state.setValue(open, opensToward(level, pos, direction, state.getValue(open)));
    }

    /**
     * The tunnel block for {@code pos} as the termites make it (carving soil or putting one up): joined up with the blocks
     * round it, and also open toward the sides in {@code alsoOpen} where there is open air (where the termite came from
     * out of the mound's shaft, or the shaft beside it), of age {@code age}.
     */
    public BlockState stateFor(BlockGetter level, BlockPos pos, int age, java.util.Collection<Direction> alsoOpen) {
        BlockState state = defaultBlockState().setValue(MoundSoil.AGE, age);
        for (Map.Entry<Direction, BooleanProperty> entry : OPEN.entrySet()) {
            state = state.setValue(entry.getValue(), opensToward(level, pos, entry.getKey(), alsoOpen.contains(entry.getKey())));
        }
        return state;
    }

    /**
     * Opens or closes a window: the side toward open air on {@code side}. False if there is no open air there (a tunnel
     * block or a solid block decides that side on its own).
     */
    public static boolean setWindow(Level level, BlockPos pos, Direction side, boolean open) {
        BlockState state = level.getBlockState(pos);
        BlockPos beside = pos.relative(side);
        if (!(state.getBlock() instanceof TermiteTunnelBlock) || !level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
            return false;
        }
        level.setBlockAndUpdate(pos, state.setValue(OPEN.get(side), open));
        return true;
    }

    /** A shovel opens or closes a window on the side it is used on (for testing by hand). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.SHOVELS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Direction side = hit.getDirection();
        BlockPos beside = pos.relative(side);
        if (!level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean open = !state.getValue(OPEN.get(side));
        if (!level.isClientSide) {
            setWindow(level, pos, side, open);
        }
        level.playSound(player, pos, open ? SoundEvents.MUD_BREAK : SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
