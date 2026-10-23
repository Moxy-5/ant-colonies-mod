package com.antcolonies.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.TermiteMouthBlock;
import com.antcolonies.block.TermiteSpireBlock;
import com.antcolonies.block.TermiteTunnelBlock;
import com.antcolonies.nav.Foothold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a termite can work on a block of its mound from, in the game: the same rules as the pretend mound of
 * {@link MoundGrowth} (which proves every stage can be built by them). From inside, the normal way:
 * <ul>
 * <li>from the mound's shaft or the nest (a foothold in a cell that is inside, see {@link Nest#isInside}): the 26 cells
 * round it;</li>
 * <li>from a channel (a tunnel block, a hollow spire piece; see {@link MoundGrowth#canOpenWindow}): the block beside it
 * by a window, or a block diagonally beside it past an empty cell outside the plan it pokes out into. Windows only while
 * it may (at night or in the rain, the user's rule).</li>
 * </ul>
 * And the fallbacks, only for surprises the plan does not need (a player breaking blocks, odd ground): from outside at
 * night (any foothold in the 26 cells round it), then a reach of 2 blocks.
 */
public final class MoundReach {
    private MoundReach() {
    }

    /** The kind of channel a block is, for the rules (a tunnel, a hollow spire piece, a mouth), or null. */
    @Nullable
    static MoundGrowth.Kind channelKind(BlockState state) {
        if (state.getBlock() instanceof TermiteTunnelBlock) {
            return MoundGrowth.Kind.TUNNEL;
        }
        if (state.getBlock() instanceof TermiteSpireBlock) {
            return state.getValue(TermiteSpireBlock.THICKNESS) < TermiteSpireBlock.TIP ? MoundGrowth.Kind.SPIRE : null;
        }
        return state.getBlock() instanceof TermiteMouthBlock ? MoundGrowth.Kind.MOUTH : null;
    }

    /** Whether a cell is empty enough to open a window into or build in (air, a plant). */
    static boolean isEmpty(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /**
     * Whether a termite holding {@code at} can build on {@code target} from inside (see the class). {@code windows}:
     * whether it may bite a window now.
     */
    public static boolean canBuildFrom(Level level, Colony colony, Foothold at, BlockPos target, boolean windows) {
        BlockPos from = at.cell();
        if (!MoundGrowth.isNear(from, target)) {
            return false;
        }
        if (!at.isChannel()) {
            return isHollowInside(level, colony, from);
        }
        return windows && windowFor(level, colony, at, target) != null;
    }

    /**
     * Whether a cell is hollow inside the colony's home: the mound's shaft or the nest (not a cell of the mound that is
     * still empty but will be soil or a piece: that is outside air for now).
     */
    public static boolean isHollowInside(Level level, Colony colony, BlockPos cell) {
        MoundPlan.Part part = Mound.partsOf(colony, level).get(cell);
        return (part == null || part == MoundPlan.Part.OPEN) && colony.nest().isInside(level, cell)
                && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
    }

    /**
     * The side of the channel {@code at} a termite bites its window in to work on {@code target}: toward it if it is
     * beside the channel, else toward an empty cell between them that is outside the mound's plan. Null if there is no
     * way (or {@code at} is no channel).
     */
    @Nullable
    public static Direction windowFor(Level level, Colony colony, Foothold at, BlockPos target) {
        if (!at.isChannel()) {
            return null;
        }
        BlockPos from = at.cell();
        MoundGrowth.Kind kind = channelKind(level.getBlockState(from));
        if (kind == null) {
            return null;
        }
        Direction side = MoundGrowth.sideToward(from, target);
        if (side != null) {
            return MoundGrowth.canOpenWindow(kind, side) ? side : null;
        }
        java.util.Map<BlockPos, ?> parts = colony.moundParts(level);
        return MoundGrowth.windowPast(from, target, kind, pos -> isEmpty(level, pos) && !parts.containsKey(pos));
    }

    /**
     * Whether a termite holding {@code at} can carve the soil at {@code target} into a tunnel joined to where it is: from
     * a channel beside it that can open toward it, or from the shaft or the nest beside it.
     */
    public static boolean canCarveFrom(Level level, Colony colony, Foothold at, BlockPos target) {
        Direction side = MoundGrowth.sideToward(at.cell(), target);
        if (side == null) {
            return false;
        }
        if (at.isChannel()) {
            MoundGrowth.Kind kind = channelKind(level.getBlockState(at.cell()));
            return kind != null && MoundGrowth.canJoin(kind, side);
        }
        return isHollowInside(level, colony, at.cell());
    }

    /** The fallback reach: within {@code blocks} blocks every way (but not the block itself). */
    public static boolean isWithin(BlockPos from, BlockPos target, int blocks) {
        return !from.equals(target) && Math.abs(from.getX() - target.getX()) <= blocks && Math.abs(from.getY() - target.getY()) <= blocks
                && Math.abs(from.getZ() - target.getZ()) <= blocks;
    }
}
