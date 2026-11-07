package com.antcolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground's top in a column, leaves of every tree passed through (BUGS 116). Minecraft's height map "without leaves"
 * ({@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES}) leaves out only its own leaf blocks; a species mod's leaves that
 * are solid blocks of their own (the giant savanna trees' crowns) stop it at the crown's top, 15 to 20 blocks over the
 * grass. This looks down from that top past anything in the {@code minecraft:leaves} tag (and the open air under a
 * crown) to the first block that stops a walker, or water. Wood (a trunk, a limb) still counts, as Minecraft's own map
 * counts it.
 *
 * <p>Cheap: one block read where no crown stands, at most a crown's depth plus the air under it where one does. Reads
 * only the one column, never loads land of its own: a caller that may be at the edge of loaded land checks
 * {@code hasChunk} first, as it did for the height map.
 */
public final class GroundTop {
    private GroundTop() {
    }

    /** The first free cell over the ground at x, z (the height map's meaning: the ground block is one below it). */
    public static int y(LevelReader level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int floor = level.getMinBuildHeight();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top - 1, z);
        while (at.getY() >= floor && !isGround(level.getBlockState(at))) {
            at.move(Direction.DOWN);
        }
        return at.getY() + 1;
    }

    /** The first free cell over the ground at x, z. */
    public static BlockPos cell(LevelReader level, int x, int z) {
        return new BlockPos(x, y(level, x, z), z);
    }

    /** The first free cell over the ground in {@code column}'s column (its height ignored). */
    public static BlockPos cell(LevelReader level, BlockPos column) {
        return cell(level, column.getX(), column.getZ());
    }

    /** Whether this cell has ground (or water, or wood) over it somewhere higher up: no open sky, leaves aside. */
    public static boolean isRoofed(LevelReader level, BlockPos cell) {
        return y(level, cell.getX(), cell.getZ()) > cell.getY() + 1;
    }

    /** Whether a block counts as ground or a roof: it stops a walker (or is water) and is no leaf of any tree. */
    public static boolean isGround(BlockState state) {
        return (state.blocksMotion() || !state.getFluidState().isEmpty()) && !state.is(BlockTags.LEAVES);
    }
}
