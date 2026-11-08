package com.antcolonies.savanna.colony;

import com.antcolonies.colony.LandReady;
import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * The raid code review, finding 11: the small pieces of ground and clock the raids share (the column, the scouts, the
 * scent trails, the way home, the raid goals), once. One rule for a cell an ant can stand in, so a scout's line, a
 * column's places and the scent left on the ground all mean the same ground: free, dry, with a floor under it.
 */
public final class RaidGround {
    /** The heights tried round the asked one, nearest first ({@link #standAt}). */
    private static final int[] NEAR_HEIGHTS = {0, 1, -1, 2, -2, 3, -3};

    private RaidGround() {
    }

    /** The distance between two places on the ground's plan (x and z only, blocks). */
    public static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether an ant can stand in this cell (and scent laid there holds): the cell free and dry, the ground under it
     * there and dry. The cell must be loaded.
     */
    public static boolean canStand(ServerLevel level, BlockPos cell) {
        BlockPos below = cell.below();
        return level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && level.getFluidState(cell).isEmpty()
                && level.getFluidState(below).isEmpty() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    /** A cell an ant can stand in at this column of the world near height {@code y} ({@link #canStand}); null if none or not loaded. */
    @Nullable
    public static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int dy : NEAR_HEIGHTS) {
            BlockPos cell = new BlockPos(bx, y + dy, bz);
            if (!LandReady.at(level, cell)) {
                return null;
            }
            if (canStand(level, cell)) {
                return cell;
            }
        }
        return null;
    }

    /** The raid diary's clock at the nest, "hh:mm" on the colony's own day (6:00 at day tick 0); "?" with no level yet. */
    public static String clock(Colony colony, @Nullable ServerLevel level) {
        if (level == null) {
            return "?";
        }
        long dayTime = colony.timeOfDay(level) % 24000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", (dayTime / 1000 + 6) % 24, (dayTime % 1000) * 60 / 1000);
    }
}
