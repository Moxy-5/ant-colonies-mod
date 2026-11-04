package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * M8 step 3b, one raid column ({@link Raids}; {@code docs/DESIGN.md}, "Step 3b, the column"). Real Megaponera
 * (Longhurst and Howse 1979, Hoelldobler et al. 1994, Frank et al. 2017-2018, Yusuf 2026): a scout home from a find
 * recruits nestmates at the entrance; they pour out and mill round it, then follow the scout out along its scent line,
 * single file near the nest and wider in the open, the column moving in surges as the front waits for the ants behind;
 * a little short of the termites it halts and bunches up, then they rush in all at once (the majors bite the
 * galleries open, the minors go in and sting); after the fight they gather again and walk home as a column.
 *
 * <p>Here the colony's brain keeps the column: who is in it (the scout leads), what stage it is at, and where along the
 * trail each ant should be now ({@link #orderFor}). Each raider's mind ({@code MatabeleRaidGoal}) only walks to that
 * place through its navigator; the fight at the spot is step 2's ({@code FightGoal}), each raider sent there with a
 * fight order. Not saved: after a reload the raiders are just ants outside, and go home.
 * Numbers: the species file's {@code raids.column} ({@link SavannaNumbers.ColumnNumbers}).
 */
public final class RaidColumn {
    public enum Stage { FORMING, MARCHING, GATHERING, ATTACKING, REGROUPING, HOMING, DONE }

    /** Where a raider should walk now and how fast, or that it should stand where it is ({@code to} null). */
    public record Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt) {
    }

    /** The front's pace: a little slower than the rest, so the ants behind catch up (my pick). */
    private static final double LEAD_PACE = 0.9;
    /** A raider this far (blocks along the trail) behind its place hurries (my pick). */
    private static final double HURRY_BEHIND = 4.0;
    private static final double HURRY_PACE = 1.15;
    /** How far ahead (blocks along the trail) of itself the front walks to at a time. */
    private static final double LEAD_STEP = 6.0;
    /** A raider counts as at the gathering place, or out at the entrance, this near it (blocks, flat). */
    private static final double NEAR = 4.0;
    /** The share of the raiders that must be at the gathering place before the attack or the walk home (my pick). */
    private static final double MOST_THERE = 0.75;
    /** A raider is home this near the entrance (blocks, flat). */
    private static final double HOME = 2.5;
    /** How often (ticks) the column works out its places. */
    private static final int EVERY = 5;

    private final Colony colony;
    private final SavannaNumbers.ColumnNumbers numbers;
    private final Raids.Find find;
    /** The march out, from the entrance to the gathering place, and the walk home, the other way. */
    private final Route out;
    private final Route back;
    /** The raiders in marching order, the front first. */
    private final List<UUID> members = new ArrayList<>();
    private UUID leader;
    /** The whole raid's size, real ants and the numbers who go along (the numbers walk in step 4). */
    private final int size;
    private final int numbersAlong;
    private Stage stage = Stage.FORMING;
    private long stageSince;
    private final long startedAt;
    /** Where the front walks to now, and where it is (blocks along the route). */
    private double head;
    private double frontAt;
    /** Each raider's place now, and the raiders sent in to fight. */
    private final Map<UUID, Order> orders = new HashMap<>();
    private final Set<UUID> sentIn = new HashSet<>();
    private long updatedAt = -EVERY;
    /** What happened, for the colony list and the tests. */
    private int cameHome;
    private long attackedAt = -1;
    private int gatheredAtAttack;

    RaidColumn(Colony colony, SavannaNumbers.ColumnNumbers numbers, Raids.Find find, List<MatabeleEntity> raiders, int numbersAlong, long now) {
        this.colony = colony;
        this.numbers = numbers;
        this.find = find;
        this.startedAt = now;
        this.stageSince = now;
        for (MatabeleEntity raider : raiders) {
            members.add(raider.getUUID());
        }
        this.leader = members.get(0);
        this.size = members.size() + numbersAlong;
        this.numbersAlong = numbersAlong;
        List<BlockPos> trail = new ArrayList<>(find.trail());
        if (trail.isEmpty() || !trail.get(0).closerThan(colony.getCenter(), 3)) {
            trail.add(0, colony.getCenter());
        }
        // The gathering place: the last point of the scent line at least stop_short from the find.
        int gather = 0;
        for (int i = 0; i < trail.size(); i++) {
            if (flat(trail.get(i), find.at()) >= numbers.stopShort()) {
                gather = i;
            }
        }
        List<BlockPos> outPoints = new ArrayList<>(trail.subList(0, Math.max(1, gather) + 1));
        this.out = new Route(outPoints);
        List<BlockPos> backPoints = new ArrayList<>(outPoints);
        java.util.Collections.reverse(backPoints);
        this.back = new Route(backPoints);
        this.head = 0.0;
    }

    public Stage stage() {
        return stage;
    }

    public Raids.Find find() {
        return find;
    }

    public UUID leader() {
        return leader;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public boolean has(UUID ant) {
        return members.contains(ant);
    }

    public int size() {
        return size;
    }

    public long startedAt() {
        return startedAt;
    }

    /** When the attack began (game time, -1 not yet), and how many raiders were at the gathering place then. */
    public long attackedAt() {
        return attackedAt;
    }

    public int gatheredAtAttack() {
        return gatheredAtAttack;
    }

    /** How many raiders came home (left the column at the entrance). */
    public int cameHome() {
        return cameHome;
    }

    /** The gathering place, a few blocks short of the find. */
    public BlockPos gatheringPlace() {
        return out.end();
    }

    /** The scent line the column walks out on (the entrance end first), as points. */
    public List<BlockPos> route() {
        return out.points;
    }

    /** How far along the march out (blocks from the entrance) this place lies. */
    public double alongOut(BlockPos at) {
        return out.along(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** How far this place is from the scent line the column walks out on (blocks, flat). */
    public double offTrail(BlockPos at) {
        return out.offBy(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** Where this raider should be now, or null to stand (worked out every few ticks). */
    @Nullable
    public Order orderFor(ServerLevel level, MatabeleEntity ant) {
        update(level);
        return orders.get(ant.getUUID());
    }

    /** It leaves the column (gone home on its own, crippled, dead). */
    void drop(UUID ant) {
        members.remove(ant);
        orders.remove(ant);
        sentIn.remove(ant);
    }

    /** The column's stages and every raider's place, every {@link #EVERY} ticks (asked by the raiders and the colony). */
    void update(ServerLevel level) {
        long now = level.getGameTime();
        if (now - updatedAt < EVERY || stage == Stage.DONE) {
            return;
        }
        updatedAt = now;
        List<MatabeleEntity> ants = new ArrayList<>();
        for (UUID id : List.copyOf(members)) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive() && !ant.fightsNoMore() && !ant.isDowned()) {
                ants.add(ant);
            } else {
                drop(id); // dead, crippled, clamped, stung still, or out of the loaded world: out of the column
            }
        }
        if (ants.isEmpty()) {
            stage = Stage.DONE;
            return;
        }
        if (!members.contains(leader)) {
            leader = members.get(0); // the front fell: the next ant follows the scent on
        }
        orders.clear();
        switch (stage) {
            case FORMING -> form(level, ants, now);
            case MARCHING -> {
                if (march(level, ants, out)) {
                    next(Stage.GATHERING, now);
                }
            }
            case GATHERING -> gather(level, ants, now);
            case ATTACKING -> attack(level, ants, now);
            case REGROUPING -> regroup(level, ants, now);
            case HOMING -> home(level, ants, now);
            default -> {
            }
        }
    }

    private void next(Stage to, long now) {
        stage = to;
        stageSince = now;
        head = 0.0;
        frontAt = 0.0;
    }

    /** The recruits come out and mill round the entrance; the scout waits at the head of its line. */
    private void form(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos entrance = colony.getCenter();
        int outside = 0;
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (flat(ant.blockPosition(), entrance) <= NEAR && ant.getY() >= entrance.getY() - 1) {
                outside++;
            }
            BlockPos spot;
            if (ant.getUUID().equals(leader)) {
                spot = place(level, this.out, Math.min(2.0, this.out.length()), 0.0);
            } else {
                double angle = i * 2.39996; // (the golden angle: spread evenly round the entrance)
                double radius = 1.5 + 1.5 * ((i * 7) % 10) / 10.0;
                spot = standAt(level, entrance.getX() + 0.5 + Math.cos(angle) * radius, entrance.getZ() + 0.5 + Math.sin(angle) * radius,
                        entrance.getY());
            }
            orders.put(ant.getUUID(), new Order(spot, 1.0, point(this.out, 4.0)));
        }
        long since = now - stageSince;
        if (outside >= ants.size() && since >= numbers.leastFormTicks() || since >= numbers.formTicks()) {
            // Off they go; the recruits not out by now stay home.
            for (MatabeleEntity ant : ants) {
                if (!ant.getUUID().equals(leader) && !(flat(ant.blockPosition(), entrance) <= NEAR + 2 && ant.getY() >= entrance.getY() - 2)) {
                    drop(ant.getUUID());
                }
            }
            next(Stage.MARCHING, now);
        }
    }

    /**
     * On the march: the front walks the route ahead of itself, waiting while the ants keeping up are strung out too far
     * behind it; every other ant walks to its place behind the front, single file near the entrance, up to
     * {@code abreast} side by side further out. At the route's end the next stage.
     */
    private boolean march(ServerLevel level, List<MatabeleEntity> ants, Route route) {
        MatabeleEntity front = ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().orElse(ants.get(0));
        frontAt = route.along(front.getX(), front.getZ());
        Map<UUID, Double> at = new HashMap<>();
        for (MatabeleEntity ant : ants) {
            at.put(ant.getUUID(), route.along(ant.getX(), ant.getZ()));
        }
        int rows = (int) Math.ceil((ants.size() - 1) / (double) Math.max(1, numbers.abreast()));
        double length = rows * numbers.spacing();
        double rear = frontAt;
        for (MatabeleEntity ant : ants) {
            double along = at.get(ant.getUUID());
            if (along >= frontAt - length - numbers.stretch() * 2.0) {
                rear = Math.min(rear, along); // (stragglers far behind are not waited for: they follow the scent on their own)
            }
        }
        boolean strung = frontAt - rear > length + numbers.stretch();
        if (!strung && frontAt >= head - LEAD_STEP / 2) {
            head = Math.min(route.length(), frontAt + LEAD_STEP);
        }
        if (frontAt >= route.length() - 1.5) {
            return true; // the front is at the route's end
        }
        BlockPos ahead = point(route, Math.min(route.length(), frontAt + 4.0));
        orders.put(front.getUUID(), strung ? new Order(null, LEAD_PACE, ahead) : new Order(place(level, route, head, 0.0), LEAD_PACE, ahead));
        // The others, row by row behind the front.
        double cursor = frontAt;
        int lanes = 0;
        int lane = 0;
        for (MatabeleEntity ant : ants) {
            if (ant == front) {
                continue;
            }
            if (lane >= lanes) {
                cursor -= numbers.spacing();
                lanes = lanesAt(route, Math.max(0.0, cursor));
                lane = 0;
            }
            double along = Math.max(0.0, cursor);
            double side = lanes == 1 ? 0.0 : lane - (lanes - 1) / 2.0; // (a block apart side by side)
            lane++;
            double pace = at.get(ant.getUUID()) < along - HURRY_BEHIND ? HURRY_PACE : 1.0;
            orders.put(ant.getUUID(), new Order(place(level, route, along, side), pace, point(route, Math.min(route.length(), along + 3.0))));
        }
        return false;
    }

    /** How many side by side at this place along a route: single file near the entrance, wider further out. */
    private int lanesAt(Route route, double along) {
        double fromNest = route == out ? along : route.length() - along;
        if (fromNest < numbers.singleFile()) {
            return 1;
        }
        return fromNest < numbers.singleFile() * 2.0 ? Math.min(2, numbers.abreast()) : Math.max(1, numbers.abreast());
    }

    /** A little short of the find the column halts and bunches up; once most are there (or after a while), the attack. */
    private void gather(ServerLevel level, List<MatabeleEntity> ants, long now) {
        int there = bunch(level, ants);
        if (there >= Math.ceil(ants.size() * MOST_THERE) || now - stageSince >= numbers.gatherTicks()) {
            next(Stage.ATTACKING, now);
            attackedAt = now;
            gatheredAtAttack = there;
            for (MatabeleEntity ant : ants) {
                sendIn(ant);
            }
        }
    }

    /** Everyone to a place round the gathering place, a little back toward home; how many are there now. */
    private int bunch(ServerLevel level, List<MatabeleEntity> ants) {
        BlockPos middle = point(out, Math.max(0.0, out.length() - 1.5));
        int there = 0;
        for (int i = 0; i < ants.size(); i++) {
            MatabeleEntity ant = ants.get(i);
            if (flat(ant.blockPosition(), middle) <= NEAR) {
                there++;
            }
            double angle = i * 2.39996;
            double radius = i == 0 ? 0.0 : 1.0 + 1.5 * Math.sqrt(i / (double) ants.size());
            BlockPos spot = standAt(level, middle.getX() + 0.5 + Math.cos(angle) * radius, middle.getZ() + 0.5 + Math.sin(angle) * radius,
                    middle.getY());
            orders.put(ant.getUUID(), new Order(spot, 1.0, find.at()));
        }
        return there;
    }

    /** One raider sent in to fight round the find (step 2's fight: the majors break in, the minors go in after). */
    private void sendIn(MatabeleEntity ant) {
        ant.orderToFight(find.at(), attackedAt + numbers.fightTicks());
        sentIn.add(ant.getUUID());
    }

    /**
     * The fight: every raider at the gathering place is sent in (stragglers as they arrive); one whose fight is over
     * waits round the gathering place. Once nobody fights any more (or the fight's time is up), they regroup.
     */
    private void attack(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos middle = point(out, Math.max(0.0, out.length() - 1.5));
        boolean fighting = false;
        for (MatabeleEntity ant : ants) {
            if (!sentIn.contains(ant.getUUID()) && flat(ant.blockPosition(), middle) <= NEAR + 2 && now - attackedAt < numbers.fightTicks()) {
                sendIn(ant);
            }
            if (ant.getFightSpot() != null) {
                fighting = true;
            }
        }
        bunch(level, ants); // (the places of those not fighting: stragglers on their way, and those done)
        boolean stragglers = ants.stream().anyMatch(ant -> !sentIn.contains(ant.getUUID()));
        if (!fighting && (!stragglers || now - attackedAt >= numbers.fightTicks()) || now - attackedAt >= numbers.fightTicks() + 200) {
            next(Stage.REGROUPING, now);
        }
    }

    /** After the fight they gather again, then walk home as a column. */
    private void regroup(ServerLevel level, List<MatabeleEntity> ants, long now) {
        int there = bunch(level, ants);
        if (there >= Math.ceil(ants.size() * MOST_THERE) || now - stageSince >= numbers.regroupTicks()) {
            next(Stage.HOMING, now);
        }
    }

    /** Home along the trail as a column, the scout in front; at the entrance each one leaves the column and goes in. */
    private void home(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos entrance = colony.getCenter();
        for (MatabeleEntity ant : List.copyOf(ants)) {
            if (flat(ant.blockPosition(), entrance) <= HOME && Math.abs(ant.getY() - entrance.getY()) <= 3) {
                drop(ant.getUUID());
                ants.remove(ant);
                cameHome++;
            }
        }
        if (ants.isEmpty() || now - stageSince >= numbers.homeTicks()) {
            stage = Stage.DONE; // (any left out now go in by themselves)
            return;
        }
        if (!members.contains(leader)) {
            leader = members.get(0);
        }
        if (march(level, ants, back)) {
            for (MatabeleEntity ant : ants) {
                orders.put(ant.getUUID(), new Order(entrance, 1.0, null)); // (the front is at the entrance: the rest come in behind it)
            }
        }
    }

    /** The cell to stand in at this place along a route, {@code side} blocks to its right; the route's own point if none. */
    private BlockPos place(ServerLevel level, Route route, double along, double side) {
        double[] p = route.at(along);
        if (side != 0.0) {
            double[] d = route.direction(along);
            BlockPos beside = standAt(level, p[0] - d[1] * side, p[1] + d[0] * side, (int) Math.round(p[2]));
            if (beside != null) {
                return beside;
            }
        }
        BlockPos on = standAt(level, p[0], p[1], (int) Math.round(p[2]));
        return on != null ? on : BlockPos.containing(p[0], p[2], p[1]);
    }

    private static BlockPos point(Route route, double along) {
        double[] p = route.at(along);
        return BlockPos.containing(p[0], p[2], p[1]);
    }

    /** A cell an ant can stand in at this column of the world near height {@code y}: free, dry, with a floor; null if none. */
    @Nullable
    public static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos cell = new BlockPos(bx, y + dy, bz);
            if (!level.hasChunkAt(cell)) {
                return null;
            }
            BlockPos below = cell.below();
            if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && level.getFluidState(cell).isEmpty()
                    && level.getFluidState(below).isEmpty() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    /** The column in words (the colony list). */
    public String describe() {
        StringBuilder line = new StringBuilder("; a raid of ").append(size).append(" (").append(members.size()).append(" real out")
                .append(numbersAlong > 0 ? ", " + numbersAlong + " as numbers" : "").append(") ").append(stage.name().toLowerCase())
                .append(" to ").append(find.at().toShortString());
        return line.toString();
    }

    static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A line of points (block middles) with the distance walked along it to each (blocks, flat). */
    static final class Route {
        final List<BlockPos> points;
        private final double[] xs;
        private final double[] zs;
        private final double[] ys;
        private final double[] sums;

        Route(List<BlockPos> points) {
            this.points = List.copyOf(points);
            int n = points.size();
            xs = new double[n];
            zs = new double[n];
            ys = new double[n];
            sums = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = points.get(i).getX() + 0.5;
                zs[i] = points.get(i).getZ() + 0.5;
                ys[i] = points.get(i).getY();
                sums[i] = i == 0 ? 0.0 : sums[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            }
        }

        double length() {
            return sums[sums.length - 1];
        }

        BlockPos end() {
            return points.get(points.size() - 1);
        }

        /** The place {@code along} blocks along the line: x, z, y. */
        double[] at(double along) {
            int n = xs.length;
            if (n == 1 || along <= 0.0) {
                return new double[] {xs[0], zs[0], ys[0]};
            }
            for (int i = 1; i < n; i++) {
                if (along <= sums[i] || i == n - 1) {
                    double piece = sums[i] - sums[i - 1];
                    double t = piece <= 1.0E-6 ? 1.0 : Mth.clamp((along - sums[i - 1]) / piece, 0.0, 1.0);
                    return new double[] {Mth.lerp(t, xs[i - 1], xs[i]), Mth.lerp(t, zs[i - 1], zs[i]), Mth.lerp(t, ys[i - 1], ys[i])};
                }
            }
            return new double[] {xs[n - 1], zs[n - 1], ys[n - 1]};
        }

        /** The line's heading at this place along it (a unit x, z). */
        double[] direction(double along) {
            int n = xs.length;
            for (int i = 1; i < n; i++) {
                if (along <= sums[i] || i == n - 1) {
                    double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
                    double length = Math.hypot(dx, dz);
                    if (length > 1.0E-6) {
                        return new double[] {dx / length, dz / length};
                    }
                }
            }
            return new double[] {1.0, 0.0};
        }

        /** How far along the line the nearest place on it to (x, z) lies. */
        double along(double x, double z) {
            return nearest(x, z)[0];
        }

        /** How far (x, z) is from the line. */
        double offBy(double x, double z) {
            return nearest(x, z)[1];
        }

        private double[] nearest(double x, double z) {
            int n = xs.length;
            if (n == 1) {
                return new double[] {0.0, Math.hypot(x - xs[0], z - zs[0])};
            }
            double best = Double.MAX_VALUE;
            double bestAlong = 0.0;
            for (int i = 1; i < n; i++) {
                double dx = xs[i] - xs[i - 1], dz = zs[i] - zs[i - 1];
                double piece = dx * dx + dz * dz;
                double t = piece <= 1.0E-9 ? 0.0 : Mth.clamp(((x - xs[i - 1]) * dx + (z - zs[i - 1]) * dz) / piece, 0.0, 1.0);
                double px = xs[i - 1] + dx * t, pz = zs[i - 1] + dz * t;
                double distance = Math.hypot(x - px, z - pz);
                if (distance < best) {
                    best = distance;
                    bestAlong = sums[i - 1] + Math.sqrt(piece) * t;
                }
            }
            return new double[] {bestAlong, best};
        }
    }
}
