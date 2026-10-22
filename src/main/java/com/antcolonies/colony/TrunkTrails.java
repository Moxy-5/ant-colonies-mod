package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lasting trunk trails (brain part 4, step 4a; the user's decisions in {@code docs/DESIGN.md}, "Brain part 4
 * decisions"). Real red harvester ants (<i>Pogonomyrmex barbatus</i>) keep a few cleared paths running straight out
 * from the nest disc for years: the foragers walk out along a trail and fan out at its end to search for seeds, and
 * the traffic and the ants' clipping keep the path bare.
 *
 * <p><b>A trail</b> is a direction from the nest's middle, saved as an angle, and the blocks (columns) along that line
 * from the hill's edge out to the longest a trail may get ({@code most_length}). Its <b>tip</b> is as far out as its trips
 * have gone (long trails, the user's decisions of 2026-10-17, {@code docs/plans/long-trails.md}): a trip to food past
 * the tip moves it out, and the tip pulls back where the traffic no longer holds it ({@link SpeciesProfile.TrunkNumbers#holds}:
 * a long trail needs more trips a day), the outer part's wear forgotten and its bare ground growing back. The colony's
 * reach is its longest trail's tip ({@link Colony#foragingRadius}). Each column counts the trips that walked over it (its <b>wear</b>); a
 * column walked by enough trips wears bare (the plants on it are cut, the ground turns to coarse dirt). Each trip also
 * makes the trail stronger; unused, its strength fades ({@link SpeciesProfile.Trails}), and a faded trail is forgotten
 * and its bare ground grows back to grass a day or two later.
 *
 * <p><b>A trip</b> (a real forager setting out, or one of the forager numbers, see {@link FieldLife}) goes by the
 * trail whose line is nearest its food's direction, within the profile's spread: it walks the trail out to the spot
 * nearest its food and leaves it there (that column and all the ones before it are walked). A trip in a direction no
 * trail goes is remembered for a day; once enough have gone one way, the colony lays a new trail there, while it may
 * have more (1 while young, more as it grows). The real foragers are only told where to leave the trail
 * ({@link #setOut}, {@link #wayHome}); how to walk there is the legs' business.
 *
 * <p>The blocks only change where no player can see them, or where one of the colony's real workers is right there
 * (it is that ant wearing it). Never reads or changes a block in unloaded land: the counts go on, the blocks catch up.
 *
 * <p>Species-neutral: any colony whose profile has {@code food.trails.trunk} (later the leafcutters too).
 */
public class TrunkTrails {
    /** How often (ticks) the worn columns and the regrowing ground are looked at. */
    private static final int CHECK_TICKS = 100;
    /** At most this many blocks are changed in one look. */
    private static final int BLOCKS_PER_CHECK = 32;
    /** How long (ticks) a trip in a direction with no trail is remembered toward laying a new one: a day. */
    private static final int RECENT_TICKS = 24000;
    /** At most this many such trips are remembered. */
    private static final int MOST_RECENT = 64;
    /** A real worker this close (blocks, sideways) to a worn column is wearing it: it may change in sight. */
    private static final int WORKER_REACH = 1;

    /** One trunk trail. */
    public static final class Trail {
        /** Its direction from the nest's middle (radians: 0 east, PI/2 south). */
        final double angle;
        /** Its columns, nearest the nest first: offsets from the nest's middle. */
        final int[][] columns;
        /** The trips that walked over each column. */
        final int[] wear;
        /** The columns worn bare (or with nothing to wear: stone, sand, water). */
        final BitSet bare;
        /** How far out (columns) a forager has left it at most. */
        int reach;
        /** Its strength at the game time {@link #strengthAt}. */
        double strength;
        long strengthAt;

        Trail(double angle, int[][] columns, int[] wear, BitSet bare, int reach, double strength, long strengthAt) {
            this.angle = angle;
            this.columns = columns;
            this.wear = wear;
            this.bare = bare;
            this.reach = reach;
            this.strength = strength;
            this.strengthAt = strengthAt;
        }

        public double angle() {
            return angle;
        }

        public int length() {
            return columns.length;
        }

        public int wear(int column) {
            return wear[column];
        }

        public boolean isBare(int column) {
            return bare.get(column);
        }

        /** How far out (columns) a forager has left it at most. */
        public int reach() {
            return reach;
        }
    }

    /** A trip in a direction no trail goes, and when. */
    private record Trip(double angle, long time) {
    }

    /** Bare ground of a faded trail, growing back at {@code growsAt}. */
    private record Regrow(int x, int z, long growsAt) {
    }

    private final Colony colony;
    /** The trails. Saved. */
    private final List<Trail> trails = new ArrayList<>();
    /** Recent trips in directions with no trail. Saved. */
    private final List<Trip> recent = new ArrayList<>();
    /** Bare ground of faded trails growing back. Saved. */
    private final List<Regrow> regrowing = new ArrayList<>();
    private long nextCheck;
    /** Game tests only: the trails stop at their test's arena's edge (arenas are 45 blocks apart, trails reach 96). */
    @Nullable
    private AABB testFence;

    TrunkTrails(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private SpeciesProfile.TrunkNumbers numbers() {
        return colony.profile().food().trails().trunk().orElse(null);
    }

    private SpeciesProfile.Trails strengthNumbers() {
        return colony.profile().food().trails();
    }

    /** Whether this colony keeps trunk trails. */
    public boolean keepsTrails() {
        return numbers() != null;
    }

    // --- trips ------------------------------------------------------------------------------------------------------

    /**
     * A real forager sets out for food at {@code plant}: the trip is counted, and if a trail goes that way the forager
     * is told where to leave it (the cell to stand in, over the ground of the trail's column nearest its food; loaded
     * land only). Null: no trail goes that way, or the food is right by the hill (it goes straight there).
     */
    @Nullable
    public BlockPos setOut(ServerLevel level, BlockPos plant) {
        Trail trail = trip(level, plant);
        return trail == null ? null : standOn(level, trail, leaveAt(trail, plant));
    }

    /**
     * A real searcher sets out to search round {@code toward} (brain step 4c): where it leaves the trail going that way,
     * as {@link #setOut}, but the trip is not counted yet (it is, once the searcher finds food and goes for it). Null: no
     * trail goes that way, or the spot is right by the hill.
     */
    @Nullable
    public BlockPos wayOut(ServerLevel level, BlockPos toward) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null || flat(toward) <= numbers.start() + 1) {
            return null;
        }
        Trail trail = nearest(angleOf(toward), numbers);
        return trail == null ? null : standOn(level, trail, leaveAt(trail, toward));
    }

    /**
     * A trail for a searcher with no spot of its own to walk out on, picked at random by strength (a stronger, busier
     * trail draws more foragers); null if the colony has none.
     */
    @Nullable
    public Trail pickForSearch(Level level, net.minecraft.util.RandomSource random) {
        List<Trail> safe = new ArrayList<>();
        for (Trail trail : trails) {
            if (!dangerAlong(level, trail, Math.max(0, trail.reach - 1)) && !colony.neighbours().turnsFrom(level, trail.angle)) {
                safe.add(trail); // (a trail through remembered danger, or toward a neighbour the colony yields to, draws no searchers: brain step 4g)
            }
        }
        double total = 0;
        for (Trail trail : safe) {
            total += Math.max(0.5, strength(level, trail));
        }
        double pick = random.nextDouble() * total;
        for (Trail trail : safe) {
            pick -= Math.max(0.5, strength(level, trail));
            if (pick <= 0) {
                return trail;
            }
        }
        return safe.isEmpty() ? null : safe.get(safe.size() - 1);
    }

    /**
     * Brain step 4g: whether a forager going for {@code target} would walk the trail going that way through a dangerous
     * place (see {@link DangerMap}), out to where it leaves the trail (never past the tip). False if no trail goes that way.
     */
    public boolean passesDanger(Level level, BlockPos target) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null || trails.isEmpty() || colony.danger().isEmpty() || flat(target) <= numbers.start() + 1) {
            return false;
        }
        Trail trail = nearest(angleOf(target), numbers);
        return trail != null && trail.reach > 0 && dangerAlong(level, trail, Math.min(leaveAt(trail, target), trail.reach - 1));
    }

    /** Whether any column of the trail from its start out to {@code last} is a dangerous place. */
    private boolean dangerAlong(Level level, Trail trail, int last) {
        DangerMap danger = colony.danger();
        if (danger.isEmpty()) {
            return false;
        }
        BlockPos center = colony.getCenter();
        for (int i = 0; i <= last && i < trail.columns.length; i++) {
            if (danger.isDangerous(level, new BlockPos(center.getX() + trail.columns[i][0], center.getY(), center.getZ() + trail.columns[i][1]))) {
                return true;
            }
        }
        return false;
    }

    /** How far out (blocks from the nest's middle) foragers have left this trail at most: its tip (its start if none yet). */
    public int reachBlocks(Trail trail) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        return trail.reach <= 0 ? (numbers == null ? 0 : numbers.start()) : (int) Math.round(columnOut(trail, trail.reach - 1));
    }

    /** How far out (blocks from the nest's middle) the longest trail's tip is; 0 with no trail. */
    public int longestTip() {
        int longest = 0;
        for (Trail trail : trails) {
            longest = Math.max(longest, reachBlocks(trail));
        }
        return longest;
    }

    /**
     * The columns (offsets from the nest's middle) within {@code side} blocks either side of each trail's line, from its
     * start out to its tip plus the searchers' stretch and {@code side} (where its foragers fan out), never past
     * {@code most} blocks out: the ground the colony looks over for food along its trails.
     */
    public List<int[]> columnsNear(int side, int most) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        List<int[]> columns = new ArrayList<>();
        if (numbers == null) {
            return columns;
        }
        int stretch = colony.profile().food().search().map(SpeciesProfile.SearchNumbers::stretch).orElse(0);
        Set<Long> seen = new HashSet<>();
        for (Trail trail : trails) {
            double cos = Math.cos(trail.angle);
            double sin = Math.sin(trail.angle);
            double end = Math.min(most, reachBlocks(trail) + stretch + side);
            for (double t = numbers.start(); t <= end; t += 0.5) {
                for (double u = -side; u <= side; u += 0.5) {
                    int[] column = {(int) Math.round(cos * t - sin * u), (int) Math.round(sin * t + cos * u)};
                    if (seen.add(key(column))) {
                        columns.add(column);
                    }
                }
            }
        }
        return columns;
    }

    /** How far (blocks, sideways) this column of the trail is from the nest's middle. */
    private static double columnOut(Trail trail, int column) {
        return Math.hypot(trail.columns[column][0], trail.columns[column][1]);
    }

    /**
     * How a walk on a trail counts against a walk through the grass (worth the walk, long trails step 3): a cleared
     * trunk trail is walked about twice as fast (real <i>Pogonomyrmex</i> foragers run faster and straighter on their
     * trails; Fewell 1988). My pick, to confirm.
     */
    static final double ON_TRAIL = 0.5;

    /**
     * The walk from the nest out to {@code plant} by the trail going that way, counted as through the grass (see
     * {@link #ON_TRAIL}): out along its cleared part to where the forager would leave it (never past its tip), then
     * straight to the plant; or -1 if no trail goes that way. So food out along a trail is worth more than food as far off
     * it, and a forager leaves the trail only for food worth the extra walk.
     */
    public double walkTo(BlockPos plant) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null || flat(plant) <= numbers.start() + 1) {
            return -1;
        }
        Trail trail = nearest(angleOf(plant), numbers);
        if (trail == null || trail.reach <= 0) {
            return -1;
        }
        int column = Math.min(leaveAt(trail, plant), trail.reach - 1);
        double along = columnOut(trail, column);
        double side = Math.hypot(colony.getCenter().getX() + trail.columns[column][0] - plant.getX(),
                colony.getCenter().getZ() + trail.columns[column][1] - plant.getZ());
        return numbers.start() + Math.max(0, along - numbers.start()) * ON_TRAIL + side;
    }

    /** One of the forager numbers goes for food at {@code plant} (maybe in unloaded land): the trip is counted. */

    void tripByNumbers(ServerLevel level, BlockPos plant) {
        trip(level, plant);
    }

    /**
     * A real forager out in the field with food, at {@code from}, going home: where it joins the trail it came out by
     * (the trail nearest its direction, at the column nearest it); null if no trail goes that way or it is near the hill.
     */
    @Nullable
    public BlockPos wayHome(ServerLevel level, BlockPos from) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null || flat(from) <= numbers.start() + 2) {
            return null;
        }
        Trail trail = nearest(angleOf(from), numbers);
        if (trail == null) {
            return null;
        }
        int column = leaveAt(trail, from);
        return column <= 0 ? null : standOn(level, trail, column);
    }

    /** Counts one trip toward {@code plant}; returns the trail it goes by, or null. */
    @Nullable
    private Trail trip(Level level, BlockPos plant) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null || flat(plant) <= numbers.start() + 1) {
            return null;
        }
        long now = level.getGameTime();
        double angle = angleOf(plant);
        Trail trail = nearest(angle, numbers);
        if (trail == null) {
            trail = maybeLayNewTrail(level, angle, now, numbers);
        }
        if (trail == null) {
            return null;
        }
        int column = leaveAt(trail, plant);
        while (column > 0 && !insideFence(trail, column)) {
            column--; // (a game test's trail stops at its arena's edge)
        }
        while (column > 0 && colony.neighbours().yieldsAt(level, columnPos(trail, column))) {
            column--; // (brain step 4g, step 5: the tip is held short of where the colony yields to a neighbour)
        }
        for (int i = 0; i <= column; i++) {
            trail.wear[i] = Math.min(trail.wear[i] + 1, 1_000_000);
        }
        trail.reach = Math.max(trail.reach, column + 1);
        SpeciesProfile.Trails strength = strengthNumbers();
        trail.strength = Math.min(strength.mostStrength(), strength.decayed(trail.strength, now - trail.strengthAt) + 1.0);
        trail.strengthAt = now;
        return trail;
    }

    /**
     * A trip in a direction no trail goes: remembered for a day. Once the profile's {@code new_trail_trips} have gone
     * within the spread of this direction, and the colony may keep another trail, one is laid along their average
     * direction (unless that is too near a trail already there).
     */
    @Nullable
    private Trail maybeLayNewTrail(Level level, double angle, long now, SpeciesProfile.TrunkNumbers numbers) {
        recent.removeIf(trip -> now - trip.time() > RECENT_TICKS || trip.time() > now);
        recent.add(new Trip(angle, now));
        while (recent.size() > MOST_RECENT) {
            recent.remove(0);
        }
        if (trails.size() >= numbers.trailsFor(colony.getCount(com.antcolonies.entity.WorkerAntEntity.CASTE))) {
            return null;
        }
        double spread = Math.toRadians(numbers.spreadDegrees());
        double sin = 0;
        double cos = 0;
        int count = 0;
        for (Trip trip : recent) {
            if (difference(trip.angle(), angle) <= spread) {
                sin += Math.sin(trip.angle());
                cos += Math.cos(trip.angle());
                count++;
            }
        }
        if (count < numbers.newTrailTrips()) {
            return null;
        }
        double mean = Math.atan2(sin, cos);
        if (colony.neighbours().turnsFrom(level, mean)) {
            return null; // (brain step 4g, step 5: no new trail toward a neighbour the colony yields to)
        }
        for (Trail trail : trails) {
            if (difference(trail.angle, mean) <= spread) {
                return null; // (the trips straddle a trail already there: they will go by it)
            }
        }
        recent.removeIf(trip -> difference(trip.angle(), angle) <= spread);
        // As strong as the trips that laid it (this one adds its own 1 in trip()): starting at 0, one trip's 1 sat right
        // at the profile's fade line and the trail faded a second later.
        Trail trail = newTrail(mean, numbers, now, count - 1);
        trails.add(trail);
        com.antcolonies.AntColoniesMod.LOGGER.info("[trails] colony {} laid a new trunk trail toward {} ({} trails)", colony.getId(),
                compass(mean), trails.size());
        return trail;
    }

    private Trail newTrail(double angle, SpeciesProfile.TrunkNumbers numbers, long now, double strength) {
        int[][] columns = columnsFor(angle, numbers.start(), numbers.mostLength());
        return new Trail(angle, columns, new int[columns.length], new BitSet(columns.length), 0, strength, now);
    }

    /**
     * The columns of a straight line from {@code start} blocks out to {@code end} blocks out, in this direction: one
     * column per block, joined side to side (never only corner to corner), nearest the nest first.
     */
    static int[][] columnsFor(double angle, int start, int end) {
        List<int[]> columns = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        int[] last = null;
        for (double t = start; t <= end; t += 0.25) {
            int dx = (int) Math.round(cos * t);
            int dz = (int) Math.round(sin * t);
            if (last != null && last[0] != dx && last[1] != dz) {
                // A corner step: the column between too, on the side the line runs nearer.
                double tx = Math.abs(cos * t - last[0]);
                double tz = Math.abs(sin * t - last[1]);
                int[] between = tx > tz ? new int[] {dx, last[1]} : new int[] {last[0], dz};
                if (seen.add(key(between))) {
                    columns.add(between);
                }
            }
            int[] column = {dx, dz};
            if (seen.add(key(column))) {
                columns.add(column);
            }
            last = column;
        }
        return columns.toArray(new int[0][]);
    }

    private static long key(int[] column) {
        return ((long) column[0] << 32) ^ (column[1] & 0xffffffffL);
    }

    /** The trail whose line is nearest this direction, within the spread; null if none is. */
    @Nullable
    private Trail nearest(double angle, SpeciesProfile.TrunkNumbers numbers) {
        double spread = Math.toRadians(numbers.spreadDegrees());
        Trail best = null;
        double bestDifference = spread;
        for (Trail trail : trails) {
            double difference = difference(trail.angle, angle);
            if (difference <= bestDifference) {
                best = trail;
                bestDifference = difference;
            }
        }
        return best;
    }

    /** The column where a forager going for {@code target} leaves the trail: the one nearest it. */
    private int leaveAt(Trail trail, BlockPos target) {
        int dx = target.getX() - colony.getCenter().getX();
        int dz = target.getZ() - colony.getCenter().getZ();
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < trail.columns.length; i++) {
            double ox = trail.columns[i][0] - dx;
            double oz = trail.columns[i][1] - dz;
            double distance = ox * ox + oz * oz;
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The cell to stand in over the ground of this column of the trail (null if its land is not loaded). */
    @Nullable
    private BlockPos standOn(Level level, Trail trail, int column) {
        BlockPos ground = groundAt(level, trail, column);
        return ground == null ? null : ground.above();
    }

    /** The ground block of this column of the trail: its top block that stops a walker (null if its land is not loaded). */
    @Nullable
    public BlockPos groundAt(Level level, Trail trail, int column) {
        int x = colony.getCenter().getX() + trail.columns[column][0];
        int z = colony.getCenter().getZ() + trail.columns[column][1];
        return ground(level, x, z);
    }

    @Nullable
    private static BlockPos ground(Level level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return null;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private double angleOf(BlockPos pos) {
        return Math.atan2(pos.getZ() - colony.getCenter().getZ(), pos.getX() - colony.getCenter().getX());
    }

    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far apart two directions are (radians, 0 to PI). */
    static double difference(double a, double b) {
        double d = Math.abs(a - b) % (2 * Math.PI);
        return d > Math.PI ? 2 * Math.PI - d : d;
    }

    // --- once a second ------------------------------------------------------------------------------------------------

    /**
     * Once a second, here ({@code away} false) or away: faded trails are forgotten (their bare ground starts growing
     * back); while here, every few seconds the worn columns wear bare and the old bare ground grows back, where no
     * player can see it (or one of the colony's real workers is right there).
     */
    void tick(ServerLevel level, boolean away) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        long now = level.getGameTime();
        fade(now, numbers, level);
        if (away || now < nextCheck) {
            return;
        }
        nextCheck = now + CHECK_TICKS;
        List<Vec3> eyes = colony.population().viewersInSight(level);
        Set<Long> workers = workerColumns(level);
        int changed = wearBare(level, numbers, eyes, workers);
        changed += growBack(level, now, eyes, BLOCKS_PER_CHECK - changed);
        if (changed > 0) {
            ColonyManager.get(level).markChanged();
        }
    }

    /** Trails gone below the fade strength are forgotten; their bare columns grow back about the profile's regrow time later. */
    private void fade(long now, SpeciesProfile.TrunkNumbers numbers, ServerLevel level) {
        SpeciesProfile.Trails strength = strengthNumbers();
        for (Iterator<Trail> it = trails.iterator(); it.hasNext(); ) {
            Trail trail = it.next();
            double left = strength.decayed(trail.strength, now - trail.strengthAt);
            if (left >= strength.fadeBelow()) {
                pullBack(trail, numbers.holds(left, strength.halfLifeTicks()), now, numbers, level);
                continue;
            }
            it.remove();
            for (int i = trail.bare.nextSetBit(0); i >= 0; i = trail.bare.nextSetBit(i + 1)) {
                long growsAt = now + (long) (numbers.regrowTicks() * (0.5 + colony.random().nextDouble()));
                regrowing.add(new Regrow(colony.getCenter().getX() + trail.columns[i][0], colony.getCenter().getZ() + trail.columns[i][1], growsAt));
            }
            com.antcolonies.AntColoniesMod.LOGGER.info("[trails] colony {}: the trunk trail toward {} faded", colony.getId(), compass(trail.angle));
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * The tip of a trail pulls back to as far as its traffic holds ({@code holds} blocks out): the columns past it are
     * forgotten (no wear) and their bare ground grows back about the regrow time later, as a faded trail's does.
     */
    private void pullBack(Trail trail, double holds, long now, SpeciesProfile.TrunkNumbers numbers, ServerLevel level) {
        int before = trail.reach;
        while (trail.reach > 0 && columnOut(trail, trail.reach - 1) > holds + 0.5) {
            int i = --trail.reach;
            trail.wear[i] = 0;
            if (trail.bare.get(i)) {
                trail.bare.clear(i);
                long growsAt = now + (long) (numbers.regrowTicks() * (0.5 + colony.random().nextDouble()));
                regrowing.add(new Regrow(colony.getCenter().getX() + trail.columns[i][0], colony.getCenter().getZ() + trail.columns[i][1], growsAt));
            }
        }
        if (trail.reach < before) {
            com.antcolonies.AntColoniesMod.LOGGER.info("[trails] colony {}: the trunk trail toward {} pulled back to {} blocks (its traffic holds {})",
                    colony.getId(), compass(trail.angle), reachBlocks(trail), Math.round(holds));
            ColonyManager.get(level).markChanged();
        }
    }

    /** The columns (sideways) the colony's real workers outside stand in, and the ones round them. */
    private Set<Long> workerColumns(ServerLevel level) {
        Set<Long> columns = new HashSet<>();
        double reach = colony.searchReach() + 2.0;
        AABB area = new AABB(colony.getCenter()).inflate(reach, 16.0, reach);
        for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, area,
                ant -> ant.isAlive() && ant.isColonyWorker() && colony.getId().equals(ant.getColonyId()))) {
            for (int dx = -WORKER_REACH; dx <= WORKER_REACH; dx++) {
                for (int dz = -WORKER_REACH; dz <= WORKER_REACH; dz++) {
                    columns.add(key(new int[] {ant.getBlockX() + dx, ant.getBlockZ() + dz}));
                }
            }
        }
        return columns;
    }

    /** Columns walked by enough trips wear bare (out of sight, or where a real worker is). Returns how many changed. */
    private int wearBare(ServerLevel level, SpeciesProfile.TrunkNumbers numbers, List<Vec3> eyes, Set<Long> workers) {
        int changed = 0;
        BlockState bareGround = numbers.bareGroundState();
        for (Trail trail : trails) {
            for (int i = 0; i < trail.columns.length && changed < BLOCKS_PER_CHECK; i++) {
                if (trail.bare.get(i) || trail.wear[i] < numbers.bareAfterTrips()) {
                    continue;
                }
                BlockPos ground = groundAt(level, trail, i);
                if (ground == null || !level.isLoaded(ground.above())) {
                    continue;
                }
                if (!workers.contains(key(new int[] {ground.getX(), ground.getZ()})) && !Population.outOfSight(level, ground.above(), eyes)) {
                    continue; // a player is looking and no ant is there: it waits
                }
                wearAway(level, ground, bareGround);
                trail.bare.set(i);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Wears one column of a trail bare: the plant on it is cut (a food plant is cut down for the foragers, its seeds
     * left lying; any other is bitten off), and grass or dirt turns to the bare ground.
     */
    private void wearAway(ServerLevel level, BlockPos ground, BlockState bareGround) {
        BlockPos above = ground.above();
        BlockState plant = level.getBlockState(above);
        if (colony.food().isFoodPlant(plant)) {
            colony.food().cutAwayForTheForagers(level, above);
        } else if (!plant.isAir() && plant.getFluidState().isEmpty()
                && (plant.canBeReplaced() || plant.is(BlockTags.FLOWERS) || plant.is(BlockTags.SAPLINGS))) {
            level.destroyBlock(above, false);
        }
        if (isWearable(level.getBlockState(ground))) {
            level.setBlock(ground, bareGround, 3);
        }
    }

    /** Ground that wears bare under the ants' feet: grass and soil (sand, stone and the like stay as they are). */
    private static boolean isWearable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** The bare ground of faded trails whose time has come grows back to grass (out of sight; not on a trail in use). */
    private int growBack(ServerLevel level, long now, List<Vec3> eyes, int most) {
        SpeciesProfile.TrunkNumbers numbers = numbers();
        BlockState bareGround = numbers == null ? Blocks.COARSE_DIRT.defaultBlockState() : numbers.bareGroundState();
        int changed = 0;
        for (Iterator<Regrow> it = regrowing.iterator(); it.hasNext() && changed < most; ) {
            Regrow spot = it.next();
            if (spot.growsAt() > now) {
                continue;
            }
            BlockPos ground = ground(level, spot.x(), spot.z());
            if (ground == null || !level.isLoaded(ground.above())) {
                continue;
            }
            if (!Population.outOfSight(level, ground.above(), eyes)) {
                continue;
            }
            it.remove();
            if (onATrail(spot.x(), spot.z()) || !level.getBlockState(ground).is(bareGround.getBlock()) || !level.getBlockState(ground.above()).isAir()) {
                continue; // a trail in use runs here again, or something else is there now
            }
            level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            changed++;
        }
        return changed;
    }

    /** The block of this column of the trail at the nest's middle's height (for the memory's flat distances). */
    private BlockPos columnPos(Trail trail, int column) {
        return new BlockPos(colony.getCenter().getX() + trail.columns[column][0], colony.getCenter().getY(),
                colony.getCenter().getZ() + trail.columns[column][1]);
    }

    /**
     * Brain step 4g, step 5: whether {@code pos} stands on one of this colony's trunk trails, out to its tip (an ant of
     * another colony walking here has stepped onto this colony's trail: a meeting).
     */
    public boolean isOnTrail(BlockPos pos) {
        int x = pos.getX() - colony.getCenter().getX();
        int z = pos.getZ() - colony.getCenter().getZ();
        if (trails.isEmpty() || x * x + z * z > (longestTip() + 2) * (longestTip() + 2)) {
            return false;
        }
        for (Trail trail : trails) {
            for (int i = 0; i < trail.reach && i < trail.columns.length; i++) {
                if (trail.columns[i][0] == x && trail.columns[i][1] == z) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a bare column of a trail in use lies here. */
    private boolean onATrail(int x, int z) {
        for (Trail trail : trails) {
            for (int i = trail.bare.nextSetBit(0); i >= 0; i = trail.bare.nextSetBit(i + 1)) {
                if (colony.getCenter().getX() + trail.columns[i][0] == x && colony.getCenter().getZ() + trail.columns[i][1] == z) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- the colony list and tests ------------------------------------------------------------------------------------

    /** The trails (for tests). */
    public List<Trail> trails() {
        return List.copyOf(trails);
    }

    /** A trail's strength now. */
    public double strength(Level level, Trail trail) {
        return strengthNumbers().decayed(trail.strength, level.getGameTime() - trail.strengthAt);
    }

    /** How many columns of faded trails are waiting to grow back (for tests). */
    public int regrowing() {
        return regrowing.size();
    }

    private boolean insideFence(Trail trail, int column) {
        if (testFence == null) {
            return true;
        }
        int x = colony.getCenter().getX() + trail.columns[column][0];
        int z = colony.getCenter().getZ() + trail.columns[column][1];
        return testFence.minX <= x && x < testFence.maxX && testFence.minZ <= z && z < testFence.maxZ;
    }

    /** Game tests only: this colony's trails stop at the edge of this area (its test's arena), or nowhere (null). */
    public void setTestFence(@Nullable AABB area) {
        testFence = area;
    }

    /** Every faded trail's bare ground grows back on the next look (for tests). */
    public void growBackSoonForTests() {
        regrowing.replaceAll(spot -> new Regrow(spot.x(), spot.z(), 0));
        nextCheck = 0;
    }

    /** For the tests: every trail's last trip, and every remembered trip, was this many ticks earlier. */
    public void ageForTests(long ticks) {
        for (Trail trail : trails) {
            trail.strengthAt -= ticks;
        }
        recent.replaceAll(trip -> new Trip(trip.angle(), trip.time() - ticks));
    }

    /** The worn columns and the regrowing ground are looked at on the next tick (for tests). */
    public void checkSoonForTests() {
        nextCheck = 0;
    }

    /** The direction as a compass point ("north", "south-east", ...). */
    static String compass(double angle) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int index = (int) Math.round(angle / (Math.PI / 4));
        return names[((index % 8) + 8) % 8];
    }

    /** For the colony list: " | trunk trails: south-east 14 blocks (strength 30, 9 bare), ...", or empty. */
    public String describe(Level level) {
        if (trails.isEmpty()) {
            return regrowing.isEmpty() ? "" : " | trunk trails: none (" + regrowing.size() + " bare blocks growing back)";
        }
        StringBuilder line = new StringBuilder(" | trunk trails:");
        for (Trail trail : trails) {
            line.append(' ').append(compass(trail.angle)).append(' ').append(reachBlocks(trail)).append(" blocks (strength ")
                    .append(Math.round(strength(level, trail))).append(", ").append(trail.bare.cardinality()).append(" bare),");
        }
        line.setLength(line.length() - 1);
        return line.toString();
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayTests.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        long now = level.getGameTime();
        SpeciesProfile.TrunkNumbers numbers = numbers();
        double angle = 0.5;
        int[][] columns = columnsFor(angle, numbers == null ? 4 : numbers.start(), numbers == null ? colony.profile().food().radius() : numbers.mostLength());
        int[] wear = new int[columns.length];
        BitSet bare = new BitSet();
        if (columns.length > 0) {
            wear[0] = 3;
            bare.set(0);
        }
        trails.add(new Trail(angle, columns, wear, bare, Math.min(2, columns.length), 1.5, now));
        recent.add(new Trip(angle, now));
        regrowing.add(new Regrow(5, 1, now + 1000));
    }

    void save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        ListTag list = new ListTag();
        for (Trail trail : trails) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("angle", trail.angle);
            entry.put("wear", new IntArrayTag(trail.wear));
            entry.put("bare", new LongArrayTag(trail.bare.toLongArray()));
            entry.putInt("reach", trail.reach);
            entry.putDouble("strength", trail.strength);
            entry.putLong("strengthAt", trail.strengthAt);
            list.add(entry);
        }
        all.put("trails", list);
        ListTag trips = new ListTag();
        for (Trip trip : recent) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("angle", trip.angle());
            entry.putLong("time", trip.time());
            trips.add(entry);
        }
        all.put("recent", trips);
        ListTag grow = new ListTag();
        for (Regrow spot : regrowing) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", spot.x());
            entry.putInt("z", spot.z());
            entry.putLong("at", spot.growsAt());
            grow.add(entry);
        }
        all.put("regrowing", grow);
        tag.put("trunkTrails", all);
    }

    void load(CompoundTag tag) {
        trails.clear();
        recent.clear();
        regrowing.clear();
        CompoundTag all = tag.getCompound("trunkTrails");
        SpeciesProfile.TrunkNumbers numbers = numbers();
        int start = numbers == null ? 4 : numbers.start();
        for (Tag t : all.getList("trails", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            double angle = entry.getDouble("angle");
            int[][] columns = columnsFor(angle, start, numbers == null ? colony.profile().food().radius() : numbers.mostLength());
            int[] wear = new int[columns.length];
            int[] saved = entry.getIntArray("wear");
            System.arraycopy(saved, 0, wear, 0, Math.min(saved.length, wear.length));
            BitSet bare = BitSet.valueOf(entry.getLongArray("bare"));
            bare.clear(columns.length, Math.max(columns.length, bare.length()));
            trails.add(new Trail(angle, columns, wear, bare, Math.min(entry.getInt("reach"), columns.length), entry.getDouble("strength"),
                    entry.getLong("strengthAt")));
        }
        for (Tag t : all.getList("recent", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            recent.add(new Trip(entry.getDouble("angle"), entry.getLong("time")));
        }
        for (Tag t : all.getList("regrowing", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            regrowing.add(new Regrow(entry.getInt("x"), entry.getInt("z"), entry.getLong("at")));
        }
    }
}
