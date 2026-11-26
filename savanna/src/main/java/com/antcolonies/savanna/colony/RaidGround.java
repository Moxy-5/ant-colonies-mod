package com.antcolonies.savanna.colony;

import java.util.Locale;
import java.util.UUID;

import com.antcolonies.colony.LandReady;
import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.GroundTop;
import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.LevelCellMap;
import com.antcolonies.savanna.registry.TreeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** An ant's short name in the raid diary: the first 8 letters of its id (the raid tidy-up: it was written out by hand 50 times). */
    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** The distance between two places on the ground's plan (x and z only, blocks). */
    public static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** BUGS 185: the most wood (blocks) a ground cell may lie on top of where the wood rests on the ground (a fallen log; my pick). */
    private static final int MOST_WOOD_UNDERFOOT = 3;

    /**
     * BUGS 185: the ground a walking ant stands on at x, z: {@link GroundTop}'s cell, but under a tree's limbs and crown
     * the ground below them, not their top (the user's raid test, 2026-11-07: a scout told to stand over a termite
     * tunnel climbed a giant baobab toward a dead limb 22 blocks up, thought itself shut in under the trunk's bulge and
     * gave its trip up). Real Megaponera scouts search the ground. Wood with open air (or leaves) under it is passed;
     * wood resting on the ground counts if at most {@value #MOST_WOOD_UNDERFOOT} blocks thick (a fallen log), a taller
     * stack (a standing trunk) is no place to stand: null. Reads the one column only; the caller checks it is loaded.
     */
    @Nullable
    public static BlockPos groundCell(ServerLevel level, int x, int z) {
        BlockPos cell = GroundTop.cell(level, x, z);
        int floor = level.getMinBuildHeight();
        for (int pass = 0; pass < 8; pass++) { // (a few layers of limbs one over another at most)
            BlockPos.MutableBlockPos at = cell.below().mutable();
            int wood = 0;
            while (at.getY() > floor && level.getBlockState(at).is(TreeBlocks.TREE_WOOD)) {
                at.move(Direction.DOWN);
                wood++;
            }
            if (wood == 0) {
                return cell;
            }
            if (GroundTop.isGround(level.getBlockState(at))) {
                return wood <= MOST_WOOD_UNDERFOOT ? cell : null;
            }
            while (at.getY() > floor && !GroundTop.isGround(level.getBlockState(at))) {
                at.move(Direction.DOWN); // (open air under hanging wood: down to what is under it)
            }
            cell = at.above().immutable();
        }
        return cell;
    }

    /**
     * Whether an ant can stand in this cell (and scent laid there holds): the cell free and dry, the ground under it
     * dry, and a walk can end standing there by the legs' own rule ({@link AntPathFinder#floorCell}: a floor under it, or
     * a ramp or slab it stands on). BUGS 362: this used to be "anything solid under it", which took the top of a thin
     * upright plate of termite mud for ground the legs never stand on, and raiders were sent there and stood with no route.
     */
    public static boolean canStand(ServerLevel level, BlockPos cell) {
        LevelCellMap map = new LevelCellMap(level);
        return map.isOpen(cell) && level.getFluidState(cell.below()).isEmpty() && AntPathFinder.floorCell(map, cell) != null;
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

    /** BUGS 121: the heights round the walker's own a hop's landing is looked for at, after the guess (each ±3 more by {@link #standAt}). */
    private static final int[] OWN_HEIGHTS = {0, 6, -6};

    /**
     * Where a hop of {@code length} blocks from {@code from} toward {@code to}, turned by {@code turn} radians, lands: a
     * cell to stand in, or null (none there, or the two at one place). BUGS 121: the height guessed from the way's two
     * ends missed the ground on a hillside (a scout on a ravine's lip 10 blocks above its nest: every hop's guess lay
     * inside the hill beside it or in the air over the ravine); the walker's own height, and a little over and under
     * it, are tried too.
     */
    @Nullable
    public static BlockPos hopCell(ServerLevel level, BlockPos from, BlockPos to, double length, double turn) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-3) {
            return null;
        }
        double share = Math.min(1.0, length / distance);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double x = from.getX() + 0.5 + (dx * cos - dz * sin) * share;
        double z = from.getZ() + 0.5 + (dx * sin + dz * cos) * share;
        int y = Mth.floor(Mth.lerp(share, from.getY(), to.getY()));
        BlockPos hop = standAt(level, x, z, y);
        for (int dy : OWN_HEIGHTS) {
            if (hop != null) {
                break;
            }
            if (from.getY() + dy != y) {
                hop = standAt(level, x, z, from.getY() + dy);
            }
        }
        return hop;
    }

    /** The raid diary's clock at the nest, "hh:mm" on the colony's own day (6:00 at day tick 0); "?" with no level yet. */
    public static String clock(Colony colony, @Nullable ServerLevel level) {
        if (level == null) {
            return "?";
        }
        long dayTime = colony.timeOfDay(level) % 24000;
        return String.format(Locale.ROOT, "%02d:%02d", (dayTime / 1000 + 6) % 24, (dayTime % 1000) * 60 / 1000);
    }
}
