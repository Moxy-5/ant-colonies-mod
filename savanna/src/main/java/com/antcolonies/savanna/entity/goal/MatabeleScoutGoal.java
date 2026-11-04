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
    private final List<BlockPos> points = new ArrayList<>();
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
        lookRound(level);
        points.clear();
        if (trip.find() != null) {
            points.add(trip.find().at());
        } else {
            planSlice(trip.angle(), trip.inner(), trip.outer());
        }
        walkOn(level);
    }

    /** The living termite mounds and their by-day feeding places out to the trip's reach (looked up once a trip). */
    private void lookRound(ServerLevel level) {
        mounds.clear();
        byDay.clear();
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
                points.add(new BlockPos(colony.getCenter().getX() + Mth.floor(Math.cos(a) * radius), colony.getCenter().getY(),
                        colony.getCenter().getZ() + Mth.floor(Math.sin(a) * radius)));
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
        if (ticks > numbers.tripTicks() || !commanded && !raids.isScoutingTime(level)) {
            goHome(); // nothing found in time, or the scouting hours are over: home empty
            return;
        }
        if (ticks % SMELL_EVERY == 0 && smell(level)) {
            ant.getNavigator().stop();
            stage = Stage.INSPECT;
            stageTicks = 0;
            return;
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        BlockPos point = next < points.size() ? points.get(next) : null;
        if (point != null && flat(ant.blockPosition(), point) <= AT_POINT) {
            arrived(level);
        }
        walkOn(level);
    }

    /** At a point of its trip: at a remembered spot it smelt nothing at, it searches that spot's piece of land instead. */
    private void arrived(ServerLevel level) {
        next++;
        tries = 0;
        reached++;
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
            if (hopToward(level, points.get(next), 1.0)) {
                return;
            }
            if (++tries >= TRIES_PER_POINT) {
                tries = 0;
                next++;
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
        if ((trip.find() == null || recheckDone) && find == null && reached * 2 >= points.size()) {
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
