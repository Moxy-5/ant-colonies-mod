package com.antcolonies.savanna.entity.goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.colony.FeedingSpots;
import com.antcolonies.savanna.colony.RaidGround;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 3a, a Matabele scout's trip ({@link Raids}; real Megaponera: a lone scout leaves the nest in the raid hours,
 * searches for termites feeding under cover, looks a find over, then runs home laying a scent line). Set out by its
 * colony's raids part ({@link Raids#mayScout}), a major walks out of the nest to its trip: a remembered good spot first
 * (rechecked; found empty, it searches that spot's slice instead), else a slice of the land round the nest, walked in
 * arcs {@code arc_step} apart, turning at each end, out to the colony's search range. All the way it smells for termites
 * within {@code smell} blocks: termites out of their mound (never the mound itself), and the feeding galleries and
 * runways of the termite colonies there. A find: it stops and looks it over, then walks home on the straightest way,
 * laying its scent line point by point, and tells the colony at the entrance. Nothing found by {@code trip_ticks}, or the
 * scouting hours over, or rain: home empty. Mind layer only: it says where to go, in hops, through the ant's navigator.
 * Raid rework R6, the mound as a clue (the user's idea): on a search, a scout that comes near a living termite mound
 * searches round it in widening rings (points put in front of the rest of its piece), and a tunnel of termites going to
 * food smelt on the way is followed outward to its end (points put in front again); what it finds there is chosen by
 * the same rules as any find.
 */
public class MatabeleScoutGoal extends Goal {
    /** The longest hop (blocks) it walks in one go; a farther point is reached hop by hop. */
    private static final int HOP = 14;
    /** It counts as at a point this near it (blocks, flat). */
    private static final double AT_POINT = 3.0;
    /** How often (ticks) it smells about while walking. */
    private static final int SMELL_EVERY = 10;
    /** A point it found no way to after this many tries is skipped; after this many skips in all, it goes home. */
    private static final int TRIES_PER_POINT = 3;
    private static final int MOST_SKIPS = 12;
    /** Its scent line has a point every this many blocks. */
    private static final double LINE_EVERY = 2.0;
    /** Home after this long (ticks) on the way back or it gives up the trip (the goal ends; going in takes over). */
    private static final int HOME_GIVE_UP = 2400;
    /** It is home this near the entrance (blocks). */
    private static final double HOME = 2.5;
    /** Its pace home with a find (real scouts run home fast and straight, by path integration; my pick 1.3 times its walk). */
    private static final double FIND_PACE = 1.3;

    private enum Stage { OUT, INSPECT, HOME, DONE }

    /** R6: what a point of its walk is for: its piece of land (or a recheck), a ring round a mound, a tunnel followed out. */
    private enum Kind { SLICE, RING, FOLLOW }

    /** A point of its walk; {@code ring}: which ring round the mound (rings only). */
    private record Point(BlockPos at, Kind kind, int ring) {
        static Point slice(BlockPos at) {
            return new Point(at, Kind.SLICE, 0);
        }
    }

    /** R6: a tunnel of termites out to food, and the mound it leaves from. */
    private record Way(BlockPos mound, FeedingSpots.WayOut way) {
    }

    private final MatabeleEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private Raids raids;
    @Nullable
    private SavannaNumbers.ScoutNumbers numbers;
    @Nullable
    private Raids.Trip trip;
    private Stage stage = Stage.DONE;
    /** The points of its search, in order, and the one it walks to now. */
    private final List<Point> points = new ArrayList<>();
    private int next;
    private int tries;
    private int skips;
    private int ticks;
    private int stageTicks;
    /**
     * The living termite mounds near (never walked over, never smelt as a find) and, R5, the termites' feeding places fed
     * at by day near (a gallery under dead wood or a mud runway, termites eating there out of sight); a bare gallery cell
     * no longer counts as a find.
     */
    private final List<Raids.MoundAt> mounds = new ArrayList<>();
    private final List<FeedingSpots.ByDay> byDay = new ArrayList<>();
    /** What it found: where, and how much termite it smelt. */
    @Nullable
    private BlockPos find;
    private int worth;
    /** R5: on its look at a find, the most termites it counted there at once, and the termites hidden in by-day places there. */
    private int termitesSeen;
    private int hiddenSeen;
    /** R5: ticks of its look spent near enough to count. */
    private int lookTicks;
    /** R5: places it passed over this trip as too poor (not smelt again this trip). */
    private final List<BlockPos> passed = new ArrayList<>();
    /** R5: how many points of its search it reached, and whether its recheck of a remembered spot is done. */
    private int reached;
    private boolean recheckDone;
    /** R6: the tunnels out to food near, the mound it is ringing (null none), the tunnel it follows (null none). */
    private final List<Way> ways = new ArrayList<>();
    @Nullable
    private Raids.MoundAt ringing;
    @Nullable
    private Way following;
    /** R6: whether it rang a mound this trip (once a trip), the ring it last told of, the tunnels it followed this trip. */
    private boolean rangThisTrip;
    private int ringSaid;
    private final List<BlockPos> followed = new ArrayList<>();
    /** R6: ticks added to its trip (ringing a mound). */
    private int extraTicks;
    /** Its scent line home, the find end first. */
    private final List<BlockPos> line = new ArrayList<>();
    /** Whether its find was told at home. */
    private boolean told;
    /** When it next asks whether a scouting trip is wanted (game time). */
    private long nextLookAt;
    /** How often it asks (ticks). */
    private static final long LOOK_EVERY = 20;

    public MatabeleScoutGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(ant.level() instanceof ServerLevel level) || level.getGameTime() < nextLookAt || ant.getColonyId() == null) {
            return false;
        }
        nextLookAt = level.getGameTime() + LOOK_EVERY; // (the raid code review, finding 8: a clock of its own, no tick count)
        Colony mine = ant.getColony();
        Raids part = mine == null ? null : mine.part(SavannaParts.RAIDS).orElse(null);
        if (part == null || part.numbers() == null || !part.mayScout(level, ant)) {
            return false;
        }
        Raids.Trip planned = part.plan(level);
        if (planned == null) {
            return false;
        }
        colony = mine;
        raids = part;
        numbers = part.numbers();
        trip = planned;
        return true;
    }

    @Override
    public void start() {
        if (!(ant.level() instanceof ServerLevel level) || raids == null || trip == null || numbers == null) {
            stage = Stage.DONE;
            return;
        }
        raids.setOut(ant, trip);
        told = false;
        stage = Stage.OUT;
        ticks = 0;
        stageTicks = 0;
        next = 0;
        tries = 0;
        skips = 0;
        find = null;
        worth = 0;
        passed.clear();
        reached = 0;
        recheckDone = false;
        line.clear();
        ringing = null;
        following = null;
        rangThisTrip = false;
        ringSaid = -1;
        followed.clear();
        extraTicks = 0;
        lookRound(level);
        points.clear();
        if (trip.find() != null) {
            points.add(Point.slice(trip.find().at()));
        } else {
            planSlice(trip.angle(), trip.inner(), trip.outer());
        }
        walkOn(level);
    }

    /** The living termite mounds and their by-day feeding places out to the trip's reach (looked up once a trip). */
    private void lookRound(ServerLevel level) {
        mounds.clear();
        byDay.clear();
        ways.clear();
        int far = Math.max(trip.range(), trip.outer());
        double reach = far + numbers.smell() + 32.0;
        java.util.List<Colony> near = Raids.termiteColonies(level, colony.getCenter(), reach); // (once: the review, finding 11)
        for (Colony termites : near) {
            mounds.add(new Raids.MoundAt(termites.getCenter(), FeedingSpots.moundReach(level, termites)));
        }
        for (Colony termites : near) {
            for (FeedingSpots.ByDay place : FeedingSpots.byDayPlaces(level, termites)) {
                if (place.food().closerThan(colony.getCenter(), far + numbers.smell() + 8.0) && Raids.outOfMound(place.food(), mounds)) {
                    byDay.add(place);
                }
            }
            // R6: the tunnels out to food it may follow (never to food past the colony's foraging range; my pick)
            for (FeedingSpots.WayOut way : FeedingSpots.waysOut(level, termites)) {
                if (!way.cells().isEmpty() && RaidGround.flat(way.food(), colony.getCenter()) <= numbers.mostRange()) {
                    ways.add(new Way(termites.getCenter(), way));
                }
            }
        }
    }

    /**
     * R5, the search of one ring of a slice: arcs {@code arc_step} apart across the slice, from {@code inner} out to
     * {@code outer} (the near rings are searched on earlier trips: trips go from near to far), one way then back the
     * other.
     */
    private void planSlice(double angle, int inner, int outer) {
        double half = Math.toRadians(numbers.sliceDegrees()) / 2.0;
        int step = Math.max(2, numbers.arcStep());
        boolean forward = colony.random().nextBoolean();
        for (int radius = inner + step / 2; radius <= outer; radius += step) {
            int count = Math.max(1, (int) Math.ceil(half * 2 * radius / step));
            for (int i = 0; i <= count; i++) {
                double a = angle - half + half * 2 * (forward ? i : count - i) / count;
                points.add(Point.slice(new BlockPos(colony.getCenter().getX() + Mth.floor(Math.cos(a) * radius), colony.getCenter().getY(),
                        colony.getCenter().getZ() + Mth.floor(Math.sin(a) * radius))));
            }
            forward = !forward;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return stage != Stage.DONE && colony != null && ant.isAlive() && !ant.isCrippled() && !ant.clamp().isOn()
                && ant.getFightSpot() == null && !ant.isDowned();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (raids != null) {
            raids.back(ant, told ? null : find); // (far out, it goes home along a trail; an untold find is lost: the diary says so)
        }
        stage = Stage.DONE;
        colony = null;
        raids = null;
        trip = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level) || colony == null || raids == null) {
            stage = Stage.DONE;
            return;
        }
        ticks++;
        stageTicks++;
        switch (stage) {
            case OUT -> searchOn(level);
            case INSPECT -> inspect(level);
            case HOME -> walkHome(level);
            default -> {
            }
        }
    }

    /** Out on its trip: smelling as it goes, from point to point. */
    private void searchOn(ServerLevel level) {
        boolean commanded = raids.wasSent(ant.getUUID());
        if (ticks > numbers.tripTicks() + extraTicks || !commanded && !raids.isScoutingTime(level)) {
            goHome(); // nothing found in time, or the scouting hours are over: home empty
            return;
        }
        if (ticks % SMELL_EVERY == 0) {
            if (smell(level)) {
                ant.getNavigator().stop();
                stage = Stage.INSPECT;
                stageTicks = 0;
                return;
            }
            if (followTunnel(level) || noticeMound(level)) {
                ant.getNavigator().stop(); // (R6: new points in front: off to the first of them)
                walkOn(level);
                return;
            }
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        BlockPos point = next < points.size() ? points.get(next).at() : null;
        if (point != null && flat(ant.blockPosition(), point) <= AT_POINT) {
            arrived(level);
        }
        walkOn(level);
    }

    /** At a point of its trip: at a remembered spot it smelt nothing at, it searches that spot's piece of land instead. */
    private void arrived(ServerLevel level) {
        if (points.get(next).kind() == Kind.SLICE) {
            reached++; // (R6: only its own piece's points count toward searching it)
        }
        next++;
        tries = 0;
        afterPoint(level);
        if (trip.find() != null && !recheckDone && next >= points.size() && find == null) {
            raids.foundEmpty(level, trip.find());
            searchRecheckedPiece();
        }
    }

    /** R5: the recheck is done (empty or too poor): it searches the remembered spot's own piece of land. */
    private void searchRecheckedPiece() {
        recheckDone = true;
        points.clear();
        next = 0;
        reached = 0;
        planSlice(trip.angle(), trip.inner(), trip.outer());
    }

    /** On to its next point, a hop at a time; a point it finds no way to is skipped after a few tries. */
    private void walkOn(ServerLevel level) {
        while (next < points.size()) {
            if (hopToward(level, points.get(next).at(), 1.0)) {
                return;
            }
            if (++tries >= TRIES_PER_POINT) {
                tries = 0;
                next++;
                afterPoint(level);
                if (++skips > MOST_SKIPS) {
                    goHome();
                    return;
                }
            } else {
                return; // another try next time
            }
        }
        // the whole piece searched: home empty. R5: it counts as searched empty only if most of it was walked (my pick:
        // half its points or more); a scout cut off from most of it leaves it for another
        if ((trip.find() == null || recheckDone) && find == null && reached * 2 >= slicePoints()) {
            raids.searchedEmpty(level, trip, ant.getUUID());
        }
        goHome();
    }

    /** One hop toward this point (straight to it when near, else {@link #HOP} blocks its way); false if no way was found. */
    private boolean hopToward(ServerLevel level, BlockPos point, double pace) {
        BlockPos from = ant.blockPosition();
        double distance = flat(from, point);
        double share = distance <= HOP ? 1.0 : HOP / distance;
        int x = from.getX() + Mth.floor((point.getX() - from.getX()) * share);
        int z = from.getZ() + Mth.floor((point.getZ() - from.getZ()) * share);
        for (int jitter = 0; jitter <= 2; jitter++) {
            int dx = jitter == 0 ? 0 : ant.getRandom().nextInt(jitter * 2 + 1) - jitter;
            int dz = jitter == 0 ? 0 : ant.getRandom().nextInt(jitter * 2 + 1) - jitter;
            BlockPos cell = openGround(level, x + dx, z + dz);
            if (cell != null && ant.getNavigator().moveTo(cell, pace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cell to stand in on the ground at this column, or null: loaded, near the nest's height, dry, free, with a
     * floor, off the living termite mounds, and ground the colony may go to (no danger or neighbour there).
     */
    @Nullable
    private BlockPos openGround(ServerLevel level, int x, int z) {
        BlockPos center = colony.getCenter();
        if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
            return null;
        }
        BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        // (the raid code review, finding 11: the raiders' own ground rule, so a scout's line only goes where a column can stand)
        if (Math.abs(cell.getY() - center.getY()) > 12 || !RaidGround.canStand(level, cell)
                || !Raids.outOfMound(cell, mounds) || !colony.mayForageAt(level, cell)) {
            return null;
        }
        return cell;
    }

    /**
     * Whether it smells termites feeding here (R5: real feeding only): termites out of their mound (not sheltered in
     * their nest), or a feeding place fed at by day (a dug gallery or runway cell of it, or its food, within
     * {@code smell} blocks); a place it passed over this trip is not smelt again. Sets the find: the nearest termite, else
     * the place's nearest dug cell. How much termite is there is counted on the look ({@link #inspect}).
     */
    private boolean smell(ServerLevel level) {
        int smell = numbers.smell();
        BlockPos at = ant.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AbstractAntEntity termite : termitesNear(level, ant.getBoundingBox().inflate(smell))) {
            double distance = termite.distanceToSqr(ant);
            if (distance < nearestDistance && !wasPassed(termite.blockPosition())) {
                nearestDistance = distance;
                nearest = termite.blockPosition();
            }
        }
        if (nearest == null) {
            for (FeedingSpots.ByDay place : byDay) {
                BlockPos cell = nearestSmelt(level, place, at, smell);
                if (cell != null && !wasPassed(cell) && (nearest == null || cell.distSqr(at) < nearestDistance)) {
                    nearest = cell;
                    nearestDistance = cell.distSqr(at);
                }
            }
        }
        if (nearest == null) {
            return false;
        }
        find = nearest.immutable();
        worth = 0;
        termitesSeen = 0;
        hiddenSeen = 0;
        lookTicks = 0;
        return true;
    }

    /** The termites feeding (out of their mound, not sheltered, not downed) in this box. */
    private List<AbstractAntEntity> termitesNear(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(AbstractAntEntity.class, box,
                insect -> SavannaSpecies.MACROTERMES.equals(insect.species()) && insect.isAlive() && !insect.isDowned()
                        && !insect.isSheltered() && Raids.outOfMound(insect.blockPosition(), mounds));
    }

    /** A by-day feeding place's dug cell (or its food) within {@code smell} of here, the nearest; null if none. */
    @Nullable
    private static BlockPos nearestSmelt(ServerLevel level, FeedingSpots.ByDay place, BlockPos at, int smell) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : place.cells()) {
            if (cell.closerThan(at, smell) && cell.distSqr(at) < bestDistance && level.isLoaded(cell)
                    && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                best = cell;
                bestDistance = cell.distSqr(at);
            }
        }
        if (best == null && place.food().closerThan(at, smell)) {
            best = place.food();
        }
        return best;
    }

    /** Whether this place lies near one it passed over this trip (two smelling distances; my pick). */
    private boolean wasPassed(BlockPos at) {
        double reach = numbers.smell() * 2.0;
        for (BlockPos old : passed) {
            if (old.closerThan(at, reach)) {
                return true;
            }
        }
        return false;
    }

    /**
     * R5, the look: it walks up to its find (to {@code inspect_from} blocks, just outside the termites' feel) and counts
     * the termites feeding within {@code smell} blocks of it all through its look, keeping the most it counted at once
     * (the raid diary's R1 finding: counting only at the first whiff, it scored 6 termites as worth 3 to 6), and the
     * termites hidden in the by-day places there ({@code by_day_termites} each). After {@code inspect_ticks} near (or
     * four times that, if it cannot get near) it chooses: worth a raid this far out with the colony this hungry, it goes
     * home with it; too poor, it passes it over and searches on (Frank and Linsenmair 2017).
     */
    private void inspect(ServerLevel level) {
        if (find == null) {
            stage = Stage.OUT;
            return;
        }
        ant.getLookControl().setLookAt(Vec3.atCenterOf(find));
        SavannaNumbers.ChooseNumbers choose = raids.choosing();
        double from = flat(ant.blockPosition(), find);
        boolean near = from <= choose.inspectFrom() + 1.0;
        if (!near && ant.getNavigator().isDone()) {
            double share = Math.max(0.0, (from - choose.inspectFrom()) / Math.max(0.01, from));
            BlockPos at = ant.blockPosition();
            BlockPos cell = openGround(level, at.getX() + Mth.floor((find.getX() - at.getX()) * share),
                    at.getZ() + Mth.floor((find.getZ() - at.getZ()) * share));
            if (cell != null) {
                ant.getNavigator().moveTo(cell, 1.0);
            }
        }
        if (near) {
            lookTicks++;
        }
        if (stageTicks % SMELL_EVERY == 1) {
            int smell = numbers.smell();
            BlockPos here = find;
            int counted = (int) termitesNear(level, new AABB(here).inflate(smell)).stream()
                    .filter(termite -> termite.blockPosition().closerThan(here, smell + 0.5)).count();
            termitesSeen = Math.max(termitesSeen, counted);
            int hidden = 0;
            for (FeedingSpots.ByDay place : byDay) {
                if (nearestSmelt(level, place, here, smell) != null) {
                    hidden += choose.byDayTermites();
                }
            }
            hiddenSeen = Math.max(hiddenSeen, hidden);
        }
        if (lookTicks < numbers.inspectTicks() && stageTicks < numbers.inspectTicks() * 4) {
            return;
        }
        worth = 3 * Math.max(termitesSeen, hiddenSeen);
        double need = raids.need(find);
        if (worth > 0 && worth >= need) {
            raids.takes(level, find, worth, ant.getUUID());
            clueDone(level, true);
            ant.getNavigator().stop();
            goHome();
            return;
        }
        // too poor for a raid this far out now: passed over (a recheck of a remembered spot counts as an empty check)
        Raids.Find rechecking = trip.find() != null && !recheckDone && trip.find().at().closerThan(find, numbers.smell() * 2.0) ? trip.find() : null;
        raids.passedOver(level, find, worth, rechecking, ant.getUUID());
        passed.add(find);
        find = null;
        worth = 0;
        stage = Stage.OUT;
        stageTicks = 0;
        ant.getNavigator().stop();
        if (rechecking != null) {
            searchRecheckedPiece();
        }
        walkOn(level);
    }

    /** R6: how many points of its walk belong to its own piece of land. */
    private int slicePoints() {
        int count = 0;
        for (Point point : points) {
            if (point.kind() == Kind.SLICE) {
                count++;
            }
        }
        return count;
    }

    /** R6: whether a point of this kind lies ahead in its walk. */
    private boolean ahead(Kind kind) {
        for (int i = next; i < points.size(); i++) {
            if (points.get(i).kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** R6: after a point is reached or skipped: a tunnel followed to its end, a mound ringed round, a ring started. */
    private void afterPoint(ServerLevel level) {
        if (following != null && !ahead(Kind.FOLLOW)) {
            if (find == null) {
                raids.tunnelEnd(level, following.way().food(), ant.getUUID());
            }
            following = null;
        }
        if (ringing != null && !ahead(Kind.RING)) {
            raids.moundRinged(level, ringing.center(), ant.getUUID(), false);
            ringing = null;
        }
        tellRing(level);
    }

    /** R6, the diary: the next point starts a new ring round the mound. */
    private void tellRing(ServerLevel level) {
        if (ringing != null && next < points.size() && points.get(next).kind() == Kind.RING && points.get(next).ring() != ringSaid) {
            ringSaid = points.get(next).ring();
            SavannaNumbers.MoundClueNumbers clue = raids.moundClue();
            raids.ringOn(level, ringing.center(), ringSaid, clue.ringStep() / 2 + ringSaid * clue.ringStep(), ant.getUUID());
        }
    }

    /** R6: a find was taken: the mound clue it came from told, the mound it was ringing let go. */
    private void clueDone(ServerLevel level, boolean found) {
        if (found && find != null && (following != null || ringing != null)) {
            raids.clueFind(level, find, following != null ? "following a termite tunnel out from the mound at " + following.mound().toShortString()
                    : "ringing the termite mound at " + ringing.center().toShortString(), ant.getUUID());
        }
        if (ringing != null) {
            raids.moundRinged(level, ringing.center(), ant.getUUID(), found);
        }
        ringing = null;
        following = null;
    }

    /** R6: no clue on a recheck's way to its remembered spot; on a search, or once the recheck is done, there is. */
    private boolean searching() {
        return trip.find() == null || recheckDone;
    }

    /**
     * R6, following a tunnel: a dug cell of a tunnel of termites going out to food (a gallery out to a feeding place, or
     * the way to a mud runway) within {@code smell} blocks, and the scout follows it outward, away from the mound, to its
     * end: points every {@code follow_every} blocks along the tunnel past the cell smelt, then the food, each on the
     * ground over it, put in front of the rest of its walk. One tunnel at a time; each once a trip.
     */
    private boolean followTunnel(ServerLevel level) {
        if (following != null || !searching() || ways.isEmpty()) {
            return false;
        }
        int smell = numbers.smell();
        BlockPos at = ant.blockPosition();
        for (Way way : ways) {
            BlockPos food = way.way().food();
            if (followed.contains(food) || wasPassed(food)) {
                continue;
            }
            List<BlockPos> cells = way.way().cells();
            int best = -1;
            double bestDistance = (double) smell * smell;
            for (int i = 0; i < cells.size(); i++) {
                BlockPos cell = cells.get(i);
                double distance = cell.distSqr(at);
                if (distance <= bestDistance && level.isLoaded(cell) && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                    best = i;
                    bestDistance = distance;
                }
            }
            if (best < 0) {
                continue;
            }
            followed.add(food);
            int every = Math.max(1, raids.moundClue().followEvery());
            List<Point> out = new ArrayList<>();
            for (int i = best + every; i < cells.size() + every; i += every) {
                BlockPos cell = i < cells.size() ? cells.get(i) : food;
                BlockPos ground = openGround(level, cell.getX(), cell.getZ());
                if (ground != null && (out.isEmpty() || !out.get(out.size() - 1).at().equals(ground))) {
                    out.add(new Point(ground, Kind.FOLLOW, 0));
                }
            }
            if (out.isEmpty()) {
                continue; // (nowhere to stand over it: not followed)
            }
            following = way;
            raids.followsTunnel(level, cells.get(best), food, out.size(), ant.getUUID());
            points.addAll(next, out);
            tries = 0;
            return true;
        }
        return false;
    }

    /**
     * R6, noticing a mound: on a search, a living termite mound whose foot is within {@code notice} blocks (and within
     * the colony's foraging range), one the colony has not ringed lately and no other scout is ringing: the scout
     * searches round it in rings {@code ring_step} apart, the first half a step past its foot, out to {@code past_foot}
     * blocks past it, each ring all the way round from the side it came from, one way then back the other (points on
     * ground it can stand on, put in front of the rest of its piece). Once a trip; its trip is {@code extra_ticks} longer.
     */
    private boolean noticeMound(ServerLevel level) {
        if (ringing != null || rangThisTrip || !searching() || mounds.isEmpty()) {
            return false;
        }
        SavannaNumbers.MoundClueNumbers clue = raids.moundClue();
        BlockPos at = ant.blockPosition();
        BlockPos nest = colony.getCenter();
        for (Raids.MoundAt mound : mounds) {
            if (flat(at, mound.center()) - mound.reach() > clue.notice() || flat(mound.center(), nest) - mound.reach() > numbers.mostRange()
                    || !raids.mayRingMound(level, mound.center())) {
                continue;
            }
            List<Point> rings = ringsRound(level, mound, clue, at);
            if (rings.isEmpty()) {
                continue;
            }
            rangThisTrip = true;
            ringing = mound;
            ringSaid = -1;
            extraTicks += clue.extraTicks();
            raids.ringsMound(level, mound.center(), mound.reach(), rings.get(rings.size() - 1).ring() + 1, rings.size(), ant.getUUID());
            points.addAll(next, rings);
            tries = 0;
            tellRing(level);
            return true;
        }
        return false;
    }

    /** R6: the points of the rings round a mound, each ring starting on the side the scout stands. */
    private List<Point> ringsRound(ServerLevel level, Raids.MoundAt mound, SavannaNumbers.MoundClueNumbers clue, BlockPos from) {
        List<Point> out = new ArrayList<>();
        int step = Math.max(2, clue.ringStep());
        double start = Math.atan2(from.getZ() - mound.center().getZ(), from.getX() - mound.center().getX());
        boolean forward = colony.random().nextBoolean();
        int ring = 0;
        for (int past = step / 2; past <= clue.pastFoot(); past += step) {
            double radius = mound.reach() + past;
            int count = Math.max(6, (int) Math.ceil(Math.PI * 2 * radius / step));
            boolean any = false;
            for (int i = 0; i < count; i++) {
                double a = start + (forward ? 1 : -1) * Math.PI * 2 * i / count;
                BlockPos ground = openGround(level, mound.center().getX() + Mth.floor(Math.cos(a) * radius),
                        mound.center().getZ() + Mth.floor(Math.sin(a) * radius));
                if (ground != null && flat(ground, colony.getCenter()) <= numbers.mostRange()) {
                    out.add(new Point(ground, Kind.RING, ring));
                    any = true;
                }
            }
            if (any) {
                ring++;
            }
            forward = !forward;
        }
        return out;
    }

    private void goHome() {
        stage = Stage.HOME;
        stageTicks = 0;
        tries = 0;
        ant.getNavigator().stop();
        if (find != null) {
            line.add(ant.blockPosition()); // (its scent line starts at the find)
        }
    }

    /** Home on the straightest way, laying its scent line if it found something (and then at a run); told at the entrance. */
    private void walkHome(ServerLevel level) {
        BlockPos entrance = colony.getCenter();
        BlockPos at = ant.blockPosition();
        if (find != null && (line.isEmpty() || line.get(line.size() - 1).distSqr(at) >= LINE_EVERY * LINE_EVERY)) {
            line.add(at);
        }
        if (flat(at, entrance) <= HOME && Math.abs(at.getY() - entrance.getY()) <= 3) {
            if (find != null) {
                line.add(entrance);
                List<BlockPos> nestFirst = new ArrayList<>(line);
                java.util.Collections.reverse(nestFirst);
                raids.found(level, find, worth, nestFirst, ant.getUUID());
                told = true;
            }
            stage = Stage.DONE; // (going in takes over)
            return;
        }
        if (stageTicks > HOME_GIVE_UP) {
            stage = Stage.DONE;
            return;
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        double pace = find != null ? FIND_PACE : 1.0;
        if (flat(at, entrance) <= HOP && ant.getNavigator().moveTo(entrance, pace) || hopToward(level, entrance, pace)
                || tries >= TRIES_PER_POINT && ant.getNavigator().moveTo(entrance, pace)) { // (no hop: the whole way at once)
            tries = 0;
            return;
        }
        if (++tries > TRIES_PER_POINT * 4) {
            stage = Stage.DONE; // no way home from here (going in takes over)
        }
    }

    /** Whether it has a find to tell of (tests). */
    @Nullable
    public BlockPos findForTests() {
        return find;
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
