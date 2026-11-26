package com.antcolonies.nav;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A cell map that remembers its answers for the length of one route search (review R2-17): the planner asks about the
 * same cells again and again from neighbouring footholds (about 50 to 60 block reads for each step of a search, most
 * of them repeats), and blocks do not change while one search runs. Made at the start of a search round the real map
 * and thrown away at its end; never kept from one tick to the next.
 */
final class SearchMemo implements CellMap {
    private static final int OPEN_KNOWN = 1, OPEN = 1 << 1, SLOPE_KNOWN = 1 << 2, SLOPE = 1 << 3, MOUTH_KNOWN = 1 << 4, MOUTH = 1 << 5,
            CHANNEL_KNOWN = 1 << 6;
    private static final int CHANNEL_SHIFT = 7, FACE_KNOWN_SHIFT = 13, FACE_SHIFT = 19;

    private final CellMap map;
    private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap(1024);

    SearchMemo(CellMap map) {
        this.map = map;
    }

    @Override
    public boolean isOpen(BlockPos pos) {
        long key = pos.asLong();
        int known = cells.get(key);
        if ((known & OPEN_KNOWN) == 0) {
            known |= OPEN_KNOWN | (map.isOpen(pos) ? OPEN : 0);
            cells.put(key, known);
        }
        return (known & OPEN) != 0;
    }

    @Override
    public boolean isSolidFace(BlockPos pos, Direction face) {
        long key = pos.asLong();
        int known = cells.get(key);
        int bit = face.get3DDataValue();
        if ((known & 1 << FACE_KNOWN_SHIFT + bit) == 0) {
            known |= 1 << FACE_KNOWN_SHIFT + bit | (map.isSolidFace(pos, face) ? 1 << FACE_SHIFT + bit : 0);
            cells.put(key, known);
        }
        return (known & 1 << FACE_SHIFT + bit) != 0;
    }

    @Override
    public int slopeHeight(BlockPos cell, int u, int w) {
        return isSlope(cell) ? map.slopeHeight(cell, u, w) : -1;
    }

    @Override
    public boolean isSlope(BlockPos cell) {
        long key = cell.asLong();
        int known = cells.get(key);
        if ((known & SLOPE_KNOWN) == 0) {
            known |= SLOPE_KNOWN | (map.isSlope(cell) ? SLOPE : 0);
            cells.put(key, known);
        }
        return (known & SLOPE) != 0;
    }

    @Override
    public int slopeTop(BlockPos cell) {
        return isSlope(cell) ? map.slopeTop(cell) : -1;
    }

    @Override
    public int channelSides(BlockPos cell) {
        long key = cell.asLong();
        int known = cells.get(key);
        if ((known & CHANNEL_KNOWN) == 0) {
            known |= CHANNEL_KNOWN | (map.channelSides(cell) & 63) << CHANNEL_SHIFT;
            cells.put(key, known);
        }
        return known >> CHANNEL_SHIFT & 63;
    }

    @Override
    public double footCost(Foothold at) {
        return map.footCost(at);
    }

    @Override
    public boolean isMouth(BlockPos cell) {
        long key = cell.asLong();
        int known = cells.get(key);
        if ((known & MOUTH_KNOWN) == 0) {
            known |= MOUTH_KNOWN | (map.isMouth(cell) ? MOUTH : 0);
            cells.put(key, known);
        }
        return (known & MOUTH) != 0;
    }
}
