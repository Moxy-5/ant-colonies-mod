package com.antcolonies.colony;

import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The shared ground reader (the user's decision of 2026-10-14, after hill bugs in the savanna): tells the planners
 * where the real ground is in each column, so nothing assumes flat land. Species-neutral: the colony only adds what is
 * not ground to it (its food plants, fallen wood) and what may not be dug (its mound, its rooms).
 *
 * <p>The top ground block of a column is the highest block that blocks motion (not leaves), then down through
 * anything a plant or a mob could stand in (grass, flowers, snow layers) and the colony's food plants (a fallen tree
 * or branch lies on the ground; it is not ground). A column under the colony's mound has no known ground (null): the
 * mound is not the land.
 */
public final class GroundReader implements TunnelRoute.Ground {
    /** At most this many plant or fallen-wood blocks are looked through to find the ground under them. */
    private static final int MAX_COVER = 4;

    private final Level level;
    private final Colony colony;
    private final Map<BlockPos, ?> mound;
    private final Set<BlockPos> keepOut;

    /**
     * @param keepOut blocks a tunnel may not be dug through (the nest's rooms and shaft: a tunnel running into a room
     *                would open it to the outside)
     */
    public GroundReader(Level level, Colony colony, Set<BlockPos> keepOut) {
        this.level = level;
        this.colony = colony;
        this.mound = colony.mound().getParts(level);
        this.keepOut = keepOut;
    }

    @Override
    @Nullable
    public Integer groundTop(int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, colony.getCenter().getY(), z))) {
            return null;
        }
        BlockPos top = surface(level, x, z, this::isCover);
        return mound.containsKey(top) || mound.containsKey(top.above()) ? null : top.getY();
    }

    /**
     * The top ground block of a loaded column with no colony yet (a founding queen looking for a site): through plants
     * and snow only.
     */
    public static BlockPos surface(Level level, int x, int z) {
        return surface(level, x, z, BlockState::canBeReplaced);
    }

    private static BlockPos surface(Level level, int x, int z, java.util.function.Predicate<BlockState> cover) {
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        for (int i = 0; i < MAX_COVER && cover.test(level.getBlockState(top)); i++) {
            top = top.below();
        }
        return top;
    }

    /** Lies on the ground but is not ground: plants, snow layers, the colony's food (fallen wood). */
    private boolean isCover(BlockState state) {
        return state.canBeReplaced() || colony.food().isFoodPlant(state);
    }

    @Override
    public boolean mayDig(BlockPos cell) {
        return level.isLoaded(cell) && level.getBlockState(cell).is(ModTags.DIGGABLE) && !touchesFluid(cell)
                && !(level.getBlockState(cell.above()).getBlock() instanceof FallingBlock)
                && !mound.containsKey(cell) && !keepOut.contains(cell);
    }

    @Override
    public boolean mayOpenHole(BlockPos hole) {
        BlockState above = level.getBlockState(hole.above());
        return mayDig(hole) && above.canBeReplaced() && above.getFluidState().isEmpty();
    }

    /** Dug out already under a roof of solid ground (an older tunnel): a tunnel may run on through it. */
    @Override
    public boolean isDug(BlockPos cell) {
        return level.isLoaded(cell) && Nest.isDugOut(level, cell) && !keepOut.contains(cell) && !touchesFluid(cell)
                && !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty();
    }

    private boolean touchesFluid(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
