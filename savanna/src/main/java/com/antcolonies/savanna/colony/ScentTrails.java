package com.antcolonies.savanna.colony;

import com.antcolonies.colony.LandReady;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * M8 raid rework R4, a Matabele colony's scent trails ({@code docs/plans/m8-matabele.md}, 3R). Real Megaponera lay their
 * raid trail from the poison gland: the scout on its run home, then every raider on the way out (so the way home does
 * not hang on the scout); the trail lasts up to about 29 hours (Hoelldobler et al. 1994). Here a trail is a thing in the
 * world, kept by the colony's raids ({@link Raids}): its points on the ground (the nest end first), the spot it leads
 * to, when it was last walked and whether a column walked it (a lone scout's line is weaker and fades sooner). Later
 * raids to the same spot follow a trail still smelt; limpers follow it home.
 *
 * <p>The scent lies on the ground: a point is <b>broken</b> where a block is dug out of the ground under it or put on
 * it (the player's block events, {@link #blockChanged}), or once its cell is filled or flooded (checked every
 * {@link #CHECK_EVERY} ticks where loaded). A broken point stays broken (the scent is gone) until a column crossing the
 * gap lays new scent over it ({@link Trail#mend}). Points near the entrance (a dead mound's open chimney) and points
 * laid where the ant stood on no floor (on a wall) are never checked. Numbers: the species file's {@code raids.trails}
 * ({@link SavannaNumbers.TrailNumbers}).
 */
public final class ScentTrails {
    /** How often (ticks) the trails' points are checked for blocks put on them, dug out under them, or water. */
    private static final int CHECK_EVERY = 40;
    /** Points this near the entrance (blocks, flat) are never checked: a dead mound's chimney is an open hole. */
    private static final double ENTRANCE_FREE = 2.5;
    /** At most this many trails kept (the oldest dropped first). */
    private static final int MOST = 12;

    /** One scent trail. */
    public static final class Trail {
        private final int id;
        private final BlockPos to;
        private final List<BlockPos> points;
        /** The points broken now, and the points watched for breaks (laid on a floor, not by the entrance). */
        private final BitSet broken;
        private final BitSet watched;
        /**
         * The raid code review, finding 15: points laid in land not loaded then (no ground known to put them on): each is
         * moved onto its ground and watched once its land is loaded ({@link ScentTrails#tick}), instead of never.
         */
        private final BitSet unsure;
        /** Counts every change to the points' places (laid, mended): the column keeps its gap look-up till it changes. */
        private int shape;
        /** The box round the points, kept till the shape changes (the block events ask it often). */
        @Nullable
        private AABB box;
        private long laidAt;
        private boolean byColumn;
        /** How often a column has walked it, and how many gaps were mended (the colony list, tests). */
        private int walks;
        private int mended;

        Trail(int id, BlockPos to, List<BlockPos> points, BitSet broken, BitSet watched, BitSet unsure, long laidAt, boolean byColumn,
                int walks, int mended) {
            this.id = id;
            this.to = to.immutable();
            this.points = new ArrayList<>(points);
            this.broken = broken;
            this.watched = watched;
            this.unsure = unsure;
            this.laidAt = laidAt;
            this.byColumn = byColumn;
            this.walks = walks;
            this.mended = mended;
        }

        public int id() {
            return id;
        }

        /** The spot it leads to. */
        public BlockPos to() {
            return to;
        }

        /**
         * Its points on the ground, the nest end first: a view, not a copy (the raid code review, finding 15: the raid
         * view drew every trail four times a second); it changes as the trail is mended.
         */
        public List<BlockPos> points() {
            return Collections.unmodifiableList(points);
        }

        /** Counts the changes to the points' places (the column's gap look-up is kept till it changes). */
        int shape() {
            return shape;
        }

        /** How many of its points wait for their land to load (tests). */
        public int unsureCount() {
            return unsure.cardinality();
        }

        public boolean isBroken(int point) {
            return broken.get(point);
        }

        /** How many of its points are broken now. */
        public int brokenCount() {
            return broken.cardinality();
        }

        public long laidAt() {
            return laidAt;
        }

        public boolean byColumn() {
            return byColumn;
        }

        public int walks() {
            return walks;
        }

        public int mended() {
            return mended;
        }

        /** How long it lasts after it was last walked (ticks): a column's trail longer than a lone scout's line. */
        public long lasting(SavannaNumbers.TrailNumbers numbers) {
            return byColumn ? numbers.lastingTicks() : numbers.scoutLastingTicks();
        }

        /** Whether it is still smelt now. */
        public boolean alive(SavannaNumbers.TrailNumbers numbers, long now) {
            return now - laidAt < lasting(numbers);
        }

        /** How fresh it is now, 1 just walked down to 0 gone. */
        public double freshness(SavannaNumbers.TrailNumbers numbers, long now) {
            return Math.max(0.0, 1.0 - (now - laidAt) / (double) Math.max(1, lasting(numbers)));
        }

        /** Tests: as if it was last walked this many ticks earlier. */
        public void ageForTests(long ticks) {
            laidAt -= ticks;
        }

        /** A raid column walked it: every raider laid scent, so it is fresh again and lasts as a column's trail. */
        public void walked(long now) {
            laidAt = now;
            byColumn = true;
            walks++;
        }

        /**
         * Ants crossing a gap lay new scent over it: every broken point for which {@code inGap} says yes (by its own
         * place) moves to the ground to stand on next to it and is whole again, or, with no ground there (water, say),
         * is dropped (the trail runs straight across). How many points were mended or dropped.
         */
        public int mend(ServerLevel level, Predicate<BlockPos> inGap) {
            int count = 0;
            for (int i = points.size() - 1; i >= 0; i--) {
                if (!broken.get(i) || !inGap.test(points.get(i))) {
                    continue;
                }
                BlockPos was = points.get(i);
                BlockPos ground = RaidGround.standAt(level, was.getX() + 0.5, was.getZ() + 0.5, was.getY());
                if (ground != null) {
                    points.set(i, ground.immutable());
                    broken.clear(i);
                    watched.set(i);
                } else {
                    points.remove(i);
                    shift(broken, i);
                    shift(watched, i);
                    shift(unsure, i);
                }
                count++;
            }
            if (count > 0) {
                mended++;
                changed();
            }
            return count;
        }

        /** Drops bit {@code at} from a point list's bits, the later ones moving down one. */
        private static void shift(BitSet bits, int at) {
            int size = Math.max(bits.length(), at + 1);
            for (int i = at; i < size; i++) {
                bits.set(i, bits.get(i + 1));
            }
        }

        /** Every point in order, with whether it is broken (the column looks for gaps ahead). */
        void forEachPoint(PointVisitor visitor) {
            for (int i = 0; i < points.size(); i++) {
                visitor.visit(i, points.get(i), broken.get(i));
            }
        }

        /** Its points' places changed: the kept box and the column's gap look-up are worked out again. */
        private void changed() {
            shape++;
            box = null;
        }

        /** The box round all its points (a little wider), for the block events; kept till the points change. */
        AABB box() {
            if (box == null) {
                box = workOutBox();
            }
            return box;
        }

        private AABB workOutBox() {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos p : points) {
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
            }
            return new AABB(minX - 1, minY - 2, minZ - 1, maxX + 2, maxY + 2, maxZ + 2);
        }
    }

    /** One point of a trail: its index, place, and whether it is broken. */
    @FunctionalInterface
    interface PointVisitor {
        void visit(int index, BlockPos at, boolean broken);
    }

    private final List<Trail> trails = new ArrayList<>();
    private int nextId = 1;
    /** When the trails are next checked (game time; not saved: the first check comes at once after a load). */
    private long nextCheckAt;

    /**
     * The raid code review, finding 15: the points laid where the land was not loaded, now loaded: each moved onto the
     * ground to stand on next to it and watched from now on (as {@link #lay} does), else kept as it is, never watched.
     */
    private static void settle(ServerLevel level, Trail trail) {
        if (trail.unsure.isEmpty()) {
            return;
        }
        boolean moved = false;
        for (int i = trail.unsure.nextSetBit(0); i >= 0; i = trail.unsure.nextSetBit(i + 1)) {
            BlockPos p = trail.points.get(i);
            if (!LandReady.at(level, p)) {
                continue;
            }
            trail.unsure.clear(i);
            BlockPos ground = RaidGround.standAt(level, p.getX() + 0.5, p.getZ() + 0.5, p.getY());
            if (ground != null && Math.abs(ground.getY() - p.getY()) <= 1) {
                trail.watched.set(i);
                if (!ground.equals(p)) {
                    trail.points.set(i, ground.immutable());
                    moved = true;
                }
            }
        }
        if (moved) {
            trail.changed();
        }
    }

    /** The trails kept now (alive or not yet dropped), oldest first. */
    public List<Trail> all() {
        return List.copyOf(trails);
    }

    /**
     * A new trail on the ground along these points (the nest end first) to this spot: each point moved onto the ground
     * to stand on next to it if there is one (watched for breaks from then on, unless by the entrance), else kept as it
     * is and never watched (laid on a wall, say).
     */
    public Trail lay(ServerLevel level, BlockPos entrance, BlockPos to, List<BlockPos> line, long now, boolean byColumn) {
        List<BlockPos> points = new ArrayList<>();
        BitSet watched = new BitSet();
        BitSet unsure = new BitSet();
        for (BlockPos p : line) {
            boolean byEntrance = RaidGround.flat(p, entrance) <= ENTRANCE_FREE;
            if (!byEntrance && !LandReady.at(level, p)) {
                unsure.set(points.size()); // (its land not loaded: put on its ground once it is; finding 15)
                points.add(p.immutable());
                continue;
            }
            BlockPos ground = RaidGround.standAt(level, p.getX() + 0.5, p.getZ() + 0.5, p.getY());
            if (ground != null && !byEntrance && Math.abs(ground.getY() - p.getY()) <= 1) {
                watched.set(points.size());
                points.add(ground.immutable());
            } else {
                points.add(p.immutable());
            }
        }
        Trail trail = new Trail(nextId++, to, points, new BitSet(), watched, unsure, now, byColumn, 0, 0);
        trails.add(trail);
        while (trails.size() > MOST) {
            trails.remove(0);
        }
        return trail;
    }

    /** The freshest trail still smelt that leads to within {@code within} blocks of this spot; null if none. */
    @Nullable
    public Trail liveTo(BlockPos at, double within, SavannaNumbers.TrailNumbers numbers, long now) {
        Trail best = null;
        for (Trail trail : trails) {
            if (trail.alive(numbers, now) && trail.to.closerThan(at, within) && (best == null || trail.laidAt > best.laidAt)) {
                best = trail;
            }
        }
        return best;
    }

    /**
     * The raid code review, finding 2: the freshest trail still smelt one of whose points lies within {@code within}
     * blocks (flat) of this place, the way home for an insect out there; null if none.
     */
    @Nullable
    public Trail nearestLive(BlockPos at, double within, SavannaNumbers.TrailNumbers numbers, long now) {
        Trail best = null;
        for (Trail trail : trails) {
            if (!trail.alive(numbers, now) || best != null && trail.laidAt <= best.laidAt) {
                continue;
            }
            for (BlockPos p : trail.points) {
                if (RaidGround.flat(p, at) <= within && Math.abs(p.getY() - at.getY()) <= within) {
                    best = trail;
                    break;
                }
            }
        }
        return best;
    }

    /**
     * The raid code review, finding 7: watched points this near a trail's spot (blocks) are not checked by the world's
     * own changes: the raiders bite the termites' mud and soil open round the spot, and that is no break in the way there
     * (my pick; the world's changes elsewhere along it, mud, fallen trees, soil wearing away, still break it).
     */
    private static final double SPOT_FREE = 6.0;

    /**
     * Every {@link #CHECK_EVERY} ticks: trails no longer smelt are dropped (not one a raid walks now), and the watched
     * points where loaded are checked: a point whose cell is filled or flooded, or whose ground is gone, is broken.
     * {@code say} gets a line for the raid diary for each trail newly broken.
     */
    void tick(ServerLevel level, long now, SavannaNumbers.TrailNumbers numbers, @Nullable Trail inUse, Consumer<String> say) {
        if (now < nextCheckAt) {
            return;
        }
        nextCheckAt = now + CHECK_EVERY; // (a clock of its own, no game-time remainder: the raid code review, finding 8)
        trails.removeIf(trail -> trail != inUse && !trail.alive(numbers, now));
        for (Trail trail : trails) {
            settle(level, trail);
            int newly = 0;
            BlockPos first = null;
            for (int i = 0; i < trail.points.size(); i++) {
                if (!trail.watched.get(i) || trail.broken.get(i)) {
                    continue;
                }
                BlockPos p = trail.points.get(i);
                if (!LandReady.at(level, p) || p.closerThan(trail.to, SPOT_FREE)) {
                    continue;
                }
                if (!RaidGround.canStand(level, p)) { // (the scent holds where an ant can stand)
                    trail.broken.set(i);
                    newly++;
                    if (first == null) {
                        first = p;
                    }
                }
            }
            if (newly > 0) {
                say.accept("the scent trail to " + trail.to.toShortString() + " is broken at " + first.toShortString() + " (" + newly
                        + " point(s) filled, flooded or dug away; " + trail.brokenCount() + " broken in all)");
            }
        }
    }

    /**
     * A block was dug or put here (a player's block event): the scent on the ground there is gone, so every watched
     * point on this cell or on the cell over it (its ground) is broken. How many points broke; {@code say} gets a line
     * for each trail newly broken.
     */
    int blockChanged(BlockPos pos, Consumer<String> say) {
        int count = 0;
        for (Trail trail : trails) {
            if (!trail.box().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                continue;
            }
            int newly = 0;
            for (int i = 0; i < trail.points.size(); i++) {
                BlockPos p = trail.points.get(i);
                if (trail.watched.get(i) && !trail.broken.get(i) && (p.equals(pos) || p.below().equals(pos))) {
                    trail.broken.set(i);
                    newly++;
                }
            }
            if (newly > 0) {
                count += newly;
                say.accept("the scent trail to " + trail.to.toShortString() + " is broken at " + pos.toShortString()
                        + " (a block dug or put there; " + trail.brokenCount() + " broken in all)");
            }
        }
        return count;
    }

    /** The trails in words (the colony list). */
    String describe(SavannaNumbers.TrailNumbers numbers, long now) {
        long alive = trails.stream().filter(trail -> trail.alive(numbers, now)).count();
        if (alive == 0) {
            return "";
        }
        long broken = trails.stream().filter(trail -> trail.alive(numbers, now) && trail.brokenCount() > 0).count();
        return "; " + alive + " scent trail(s)" + (broken > 0 ? " (" + broken + " broken)" : "");
    }

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Trail trail : trails) {
            CompoundTag one = new CompoundTag();
            one.putInt("id", trail.id);
            one.put("to", NbtUtils.writeBlockPos(trail.to));
            one.put("points", new LongArrayTag(trail.points.stream().mapToLong(BlockPos::asLong).toArray()));
            one.putLongArray("broken", trail.broken.toLongArray());
            one.putLongArray("watched", trail.watched.toLongArray());
            one.putLongArray("unsure", trail.unsure.toLongArray());
            one.putLong("laidAt", trail.laidAt);
            one.putBoolean("byColumn", trail.byColumn);
            one.putInt("walks", trail.walks);
            one.putInt("mended", trail.mended);
            list.add(one);
        }
        tag.put("raidTrails", list);
        tag.putInt("raidTrailNext", nextId);
    }

    void load(CompoundTag tag) {
        trails.clear();
        for (Tag t : tag.getList("raidTrails", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) t;
            BlockPos to = NbtUtils.readBlockPos(one, "to").orElse(null);
            long[] raw = one.getLongArray("points");
            if (to == null || raw.length == 0) {
                continue;
            }
            List<BlockPos> points = new ArrayList<>();
            for (long point : raw) {
                points.add(BlockPos.of(point));
            }
            trails.add(new Trail(one.getInt("id"), to, points, BitSet.valueOf(one.getLongArray("broken")),
                    BitSet.valueOf(one.getLongArray("watched")), BitSet.valueOf(one.getLongArray("unsure")), one.getLong("laidAt"), one.getBoolean("byColumn"),
                    one.getInt("walks"), one.getInt("mended")));
        }
        nextId = Math.max(1, tag.getInt("raidTrailNext"));
    }

    /** Tests (the save-and-load check): one trail with a broken point. */
    void fillForTests(BlockPos entrance) {
        BitSet broken = new BitSet();
        broken.set(2);
        BitSet watched = new BitSet();
        watched.set(1, 3);
        BitSet unsure = new BitSet();
        unsure.set(3);
        trails.add(new Trail(nextId++, entrance.offset(20, 0, 6), List.of(entrance, entrance.east(2), entrance.east(4), entrance.offset(18, 0, 6)),
                broken, watched, unsure, 130, true, 2, 1));
    }
}
