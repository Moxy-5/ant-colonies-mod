package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

/**
 * How a dead termite mound wears down (M8 step 1a part 2, {@code docs/DESIGN.md}, "Dead mounds, details"): pure
 * numbers, no world; {@link DeadMounds} does the blocks. Nobody patches a dead mound any more, so rain eats it in the
 * real order, sped up: the thin pieces on top (spire tips, chimney mouths, ramps) crumble first, then the tall thin parts
 * of the soil (the spires) wear down to the body over a few days, then the body slowly slumps into a low, broad, rounded
 * hump that grass takes over and that stays for good (the savanna's "mound islands").
 *
 * <p>The mound is worked out column by column (one column per block of ground under it): how tall it stood when the
 * colony died ({@link Column#top}), how tall its body is without the spires ({@link Column#body}, the mound with
 * everything narrower than about 5 blocks taken off), and how tall the final hump is there ({@link Column#hump}). Each
 * column's height on a given day comes from those three ({@link #heightAt}); it only ever goes down.
 */
public final class MoundWear {
    /** Pieces (spire tips, chimney mouths, ramps) crumble between these days, the highest first (my picks, to confirm). */
    public static final double PIECES_FROM = 0.25;
    public static final double PIECES_TO = 3.0;
    /** By this day the spires are worn down to the mound's body (my pick, to confirm: "a few days"). */
    public static final double SPIRES_DAYS = 5.0;
    /**
     * By about this day the body has slumped into the final hump (my pick, to confirm, from the user's 20 to 40); each
     * column a little sooner or later ({@link #jitter}), so the last ones settle between about day 26 and day 35.
     */
    public static final double HUMP_DAYS = 30.0;
    /** Grass takes the top of a column once it has worn down to the hump, from this day on (my pick, to confirm). */
    public static final double GRASS_DAYS = 6.0;
    /** The hump's middle is this share of the mound's height (real mound islands are low and broad; my pick) ... */
    static final double HUMP_SHARE = 0.12;
    /** ... at most this many blocks (a full 32-block mound leaves a hump 4 blocks high; my pick, to confirm). */
    static final int HUMP_MOST = 4;
    /**
     * The soil washed off the mound spreads round its foot: a ring this many blocks wide of one layer of soil on the
     * ground (my pick, to confirm), so the hump ends lower and broader than the mound stood.
     */
    static final int APRON = 2;
    /** Soil narrower than about {@code 2 * SPIRE_RADIUS + 1} blocks counts as spire and wears away first. */
    static final int SPIRE_RADIUS = 2;
    /**
     * Part 3, the way in: at most this many tunnel blocks at the top of the final hump stay open upward, little holes down
     * into the old channels (my pick, to confirm); the hollow shaft up the middle always stays open, the big way in.
     */
    public static final int MOST_TUNNEL_WAYS = 3;
    /**
     * Part 3, the colony's works out round the mound: nobody repairs them, so the rain takes the thin mud first. The mud
     * sheeting over the runways, the mud plastered over the wood they ate, the mud casts and any gallery wall under the open
     * sky crumble between these days (my picks, to confirm; real: within a few weeks, sped up like the mound) ...
     */
    public static final double SURFACE_MUD_FROM = 0.5;
    public static final double SURFACE_MUD_TO = 3.0;
    /** ... the foraging holes, plugged or open, silt up and grass closes over them between these days ... */
    public static final double HOLES_FROM = 1.0;
    public static final double HOLES_TO = 5.0;
    /**
     * ... and the galleries under the ground last much longer, filling in with washed-in soil between these days, the far
     * ends first and the galleries by the nest last (my picks, to confirm; real abandoned galleries last for years).
     */
    public static final double GALLERIES_FROM = 15.0;
    public static final double GALLERIES_TO = 45.0;

    private MoundWear() {
    }

    /**
     * One column of the dead mound: its ground offset from the entrance, and its heights in blocks above the ground
     * (offset y 0 is the mound's lowest layer): as it stood, its body without the spires, and the final hump. A column of
     * the ring of washed soil round the foot ({@link #isApron}) stood at 0 and gets 1.
     */
    public record Column(int dx, int dz, int top, int body, int hump) {
        public boolean isApron() {
            return top == 0;
        }
    }

    /**
     * The columns of a dead mound from the cells actually standing when it died ({@code built}: offsets from the
     * entrance, every mound block, not the hollow shaft). Columns with nothing standing are left out.
     */
    public static List<Column> plan(Iterable<BlockPos> built) {
        Map<Long, Integer> tops = new HashMap<>();
        int minX = 0, maxX = 0, minZ = 0, maxZ = 0, highest = 0;
        for (BlockPos cell : built) {
            if (cell.getY() < 0) {
                continue; // the footing under the mound: never worn
            }
            tops.merge(key(cell.getX(), cell.getZ()), cell.getY() + 1, Math::max);
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minZ = Math.min(minZ, cell.getZ());
            maxZ = Math.max(maxZ, cell.getZ());
            highest = Math.max(highest, cell.getY() + 1);
        }
        if (tops.isEmpty()) {
            return List.of();
        }
        // The heights on a grid with a border of bare ground round it.
        int pad = SPIRE_RADIUS;
        int width = maxX - minX + 1 + 2 * pad, depth = maxZ - minZ + 1 + 2 * pad;
        int[][] top = new int[width][depth];
        double radius = 0;
        for (Map.Entry<Long, Integer> entry : tops.entrySet()) {
            int dx = (int) (entry.getKey() >> 32), dz = (int) (long) entry.getKey();
            top[dx - minX + pad][dz - minZ + pad] = entry.getValue();
            radius = Math.max(radius, Math.sqrt(dx * dx + dz * dz) + 0.5);
        }
        int[][] body = opening(top, SPIRE_RADIUS);
        int humpTop = humpTop(highest);
        double humpRadius = radius + APRON;
        List<Column> columns = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (top[x][z] <= 0) {
                    continue;
                }
                int dx = x - pad + minX, dz = z - pad + minZ;
                int bodyHere = Math.max(1, Math.min(top[x][z], body[x][z]));
                int hump = Math.min(bodyHere, humpAt(Math.sqrt(dx * dx + dz * dz), humpRadius, humpTop));
                columns.add(new Column(dx, dz, top[x][z], bodyHere, hump));
            }
        }
        // The ring of washed soil: bare ground within APRON blocks of the mound's foot.
        int reach = (int) Math.ceil(humpRadius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!tops.containsKey(key(dx, dz)) && nearFoot(tops, dx, dz)) {
                    columns.add(new Column(dx, dz, 0, 0, 1));
                }
            }
        }
        return columns;
    }

    /** Whether bare ground at (dx, dz) lies within {@link #APRON} blocks of a column of the mound. */
    private static boolean nearFoot(Map<Long, Integer> tops, int dx, int dz) {
        for (int i = -APRON; i <= APRON; i++) {
            for (int j = -APRON; j <= APRON; j++) {
                if (i * i + j * j <= APRON * APRON && tops.containsKey(key(dx + i, dz + j))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * The heights with every bump narrower than the disc of {@code radius} taken off (a "grey opening": the lowest
     * height round each column, then the highest of those round it). A thin spire comes down to the body under it; a
     * broad dome keeps its shape. Ground off the grid counts as bare (0).
     */
    static int[][] opening(int[][] heights, int radius) {
        int width = heights.length, depth = heights[0].length;
        int[][] low = new int[width][depth];
        int[][] out = new int[width][depth];
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int least = Integer.MAX_VALUE;
                for (int i = -radius; i <= radius; i++) {
                    for (int j = -radius; j <= radius; j++) {
                        if (i * i + j * j <= radius * radius + 1) {
                            int xi = x + i, zj = z + j;
                            least = Math.min(least, xi < 0 || zj < 0 || xi >= width || zj >= depth ? 0 : heights[xi][zj]);
                        }
                    }
                }
                low[x][z] = least;
            }
        }
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                int most = 0;
                for (int i = -radius; i <= radius; i++) {
                    for (int j = -radius; j <= radius; j++) {
                        int xi = x + i, zj = z + j;
                        if (i * i + j * j <= radius * radius + 1 && xi >= 0 && zj >= 0 && xi < width && zj < depth) {
                            most = Math.max(most, low[xi][zj]);
                        }
                    }
                }
                out[x][z] = Math.min(heights[x][z], most);
            }
        }
        return out;
    }

    /** The hump's height in its middle, for a mound that stood {@code highest} blocks tall. */
    static int humpTop(int highest) {
        return Math.max(1, Math.min(HUMP_MOST, (int) Math.round(highest * HUMP_SHARE)));
    }

    /** The hump's height {@code distance} blocks from the middle of a mound {@code radius} blocks across: a low dome, at least 1. */
    static int humpAt(double distance, double radius, int humpTop) {
        double share = 1 - Math.pow(Math.min(1, distance / Math.max(1, radius)), 2);
        return Math.max(1, (int) Math.round(humpTop * share));
    }

    /**
     * A column's height {@code days} after the colony died: its top until the rain starts, worn down to its body by
     * {@link #SPIRES_DAYS} (the spires, steadily), then slumping to the hump by about {@link #HUMP_DAYS} (fast at first,
     * slower as it flattens), then the hump for good. {@code jitter} (about 0.85 to 1.15, {@link #jitter}) runs this
     * column's clock a little faster or slower than its neighbours'. Never more than {@code top}, never less than the hump.
     */
    public static int heightAt(Column column, double days, double jitter) {
        int top = column.top(), body = Math.min(column.body(), top), hump = Math.min(column.hump(), body);
        double t = Math.max(0, days) * jitter;
        if (t <= 0) {
            return top;
        }
        if (t < SPIRES_DAYS) {
            return (int) Math.ceil(body + (top - body) * (1 - t / SPIRES_DAYS) - 1e-9);
        }
        double slump = (t - SPIRES_DAYS) / (HUMP_DAYS - SPIRES_DAYS);
        if (slump >= 1) {
            return hump;
        }
        return (int) Math.ceil(hump + (body - hump) * (1 - slump) * (1 - slump) - 1e-9);
    }

    /** The day the washed soil reaches a place of the ring round the foot: about a third of the way through the slump. */
    public static double apronDay(double jitter) {
        return SPIRES_DAYS + (HUMP_DAYS - SPIRES_DAYS) * 0.35 * jitter;
    }

    /** The day a piece {@code y} blocks up a mound {@code highest} tall crumbles: the highest first. */
    public static double pieceDay(int y, int highest, double jitter) {
        double low = 1 - y / (double) Math.max(1, highest);
        return PIECES_FROM + (PIECES_TO - PIECES_FROM) * Math.max(0, Math.min(1, low * jitter));
    }

    /** A steady 0.85 to 1.15 for a place (the same every time): neighbouring columns and pieces wear a little apart. */
    public static double jitter(long place) {
        long z = place + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return 0.85 + 0.3 * ((z >>> 11) / (double) (1L << 53));
    }

    /** Whether grass grows over this place's top once it is bare hump: about 1 in 3 gets a tuft (my pick). */
    public static boolean tuft(long place) {
        return jitter(place * 31 + 7) < 0.95;
    }

    // --- part 3: the way in and the works round the mound ------------------------------------------------------------

    /** Whether a tunnel block at the top of the final hump here falls open upward: about 1 in 3 (my pick). */
    public static boolean opensUp(long place) {
        return jitter(place * 17 + 3) < 0.95;
    }

    /** The day the thin mud at a place (sheeting, mud over wood, a cast, a gallery wall in the open) crumbles. */
    public static double surfaceMudDay(double jitter) {
        return SURFACE_MUD_FROM + (SURFACE_MUD_TO - SURFACE_MUD_FROM) * spread(jitter);
    }

    /** The day a foraging hole silts up and grass closes over it. */
    public static double holeDay(double jitter) {
        return HOLES_FROM + (HOLES_TO - HOLES_FROM) * spread(jitter);
    }

    /**
     * The day a gallery cell {@code distance} blocks from the nest fills in, the galleries reaching {@code reach} blocks
     * out: mostly by the distance (the far ends first, the ones by the nest last), a little by chance.
     */
    public static double galleryDay(double distance, double reach, double jitter) {
        double near = 1 - Math.min(1, Math.max(0, distance) / Math.max(1, reach));
        return GALLERIES_FROM + (GALLERIES_TO - GALLERIES_FROM) * Math.min(1, 0.75 * near + 0.25 * spread(jitter));
    }

    /** A place's {@link #jitter} (0.85 to 1.15) as a share from 0 to 1. */
    private static double spread(double jitter) {
        return Math.max(0, Math.min(1, (jitter - 0.85) / 0.3));
    }
}
