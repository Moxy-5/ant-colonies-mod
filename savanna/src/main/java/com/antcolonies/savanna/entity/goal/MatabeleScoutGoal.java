package com.antcolonies.savanna.entity.goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.colony.FeedingSpots;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
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
    /** The living termite mounds near (never walked over, never smelt as a find) and the termites' feeding ways near. */
    private final List<Raids.MoundAt> mounds = new ArrayList<>();
    private final List<BlockPos> feedingCells = new ArrayList<>();
    /** What it found: where, and how much termite it smelt. */
    @Nullable
    private BlockPos find;
    private int worth;
    /** Its scent line home, the find end first. */
    private final List<BlockPos> line = new ArrayList<>();

    public MatabeleScoutGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.tickCount % 20 != 0 || ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
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
        raids.setOut(ant);
        stage = Stage.OUT;
        ticks = 0;
        stageTicks = 0;
        next = 0;
        tries = 0;
        skips = 0;
        find = null;
        worth = 0;
        line.clear();
        lookRound(level);
        points.clear();
        if (trip.find() != null) {
            points.add(trip.find().at());
        } else {
            planSlice(trip.angle(), trip.range());
        }
        walkOn(level);
    }

    /** The living termite mounds and their feeding ways out to the trip's range (looked up once a trip). */
    private void lookRound(ServerLevel level) {
        mounds.clear();
        feedingCells.clear();
        double reach = trip.range() + numbers.smell() + 32.0;
        for (Colony termites : Raids.termiteColonies(level, colony.getCenter(), reach)) {
            mounds.add(new Raids.MoundAt(termites.getCenter(), FeedingSpots.moundReach(level, termites)));
        }
        for (Colony termites : Raids.termiteColonies(level, colony.getCenter(), reach)) {
            for (BlockPos cell : termites.nest().foodWays().galleryCells()) {
                if (cell.closerThan(colony.getCenter(), trip.range() + numbers.smell()) && Raids.outOfMound(level, cell, mounds)) {
                    feedingCells.add(cell);
                }
            }
        }
    }

    /**
     * The search of a slice: arcs {@code arc_step} apart across the slice, out to {@code range}, the far band only
     * ({@code first_range} deep: the near land was searched before the range grew), one way then back the other.
     */
    private void planSlice(double angle, int range) {
        double half = Math.toRadians(numbers.sliceDegrees()) / 2.0;
        int inner = Math.max(0, range - numbers.firstRange());
        int step = Math.max(2, numbers.arcStep());
        boolean forward = colony.random().nextBoolean();
        for (int radius = inner + step; radius <= range; radius += step) {
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
            raids.back(ant);
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

    /** At a point of its trip: at a remembered spot it smelt nothing at, it searches that spot's slice instead. */
    private void arrived(ServerLevel level) {
        next++;
        tries = 0;
        if (trip.find() != null && next >= points.size() && find == null) {
            raids.foundEmpty(level, trip.find());
            points.clear();
            next = 0;
            planSlice(trip.angle(), trip.range());
        }
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
        goHome(); // the whole slice searched: home empty
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
        BlockPos below = cell.below();
        if (Math.abs(cell.getY() - center.getY()) > 12 || !level.getFluidState(cell).isEmpty() || !level.getFluidState(below).isEmpty()
                || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || !Raids.outOfMound(level, cell, mounds) || !colony.mayForageAt(level, cell)) {
            return null;
        }
        return cell;
    }

    /**
     * Whether it smells termites here: termites out of their mound (not sheltered in their nest) and the termites'
     * feeding ways, within {@code smell} blocks; a dug way counts, a way only planned not. Sets the find (the nearest
     * termite, else the nearest way) and its worth (a way's cell 1, a termite 3).
     */
    private boolean smell(ServerLevel level) {
        int smell = numbers.smell();
        BlockPos at = ant.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        int termites = 0;
        for (AbstractAntEntity termite : level.getEntitiesOfClass(AbstractAntEntity.class, ant.getBoundingBox().inflate(smell),
                insect -> SavannaSpecies.MACROTERMES.equals(insect.species()) && insect.isAlive() && !insect.isDowned()
                        && !insect.isSheltered() && Raids.outOfMound(level, insect.blockPosition(), mounds))) {
            termites++;
            double distance = termite.distanceToSqr(ant);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = termite.blockPosition();
            }
        }
        int cells = 0;
        BlockPos nearestCell = null;
        double nearestCellDistance = Double.MAX_VALUE;
        for (BlockPos cell : feedingCells) {
            if (!cell.closerThan(at, smell) || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                continue;
            }
            cells++;
            double distance = cell.distSqr(at);
            if (distance < nearestCellDistance) {
                nearestCellDistance = distance;
                nearestCell = cell;
            }
        }
        if (termites == 0 && cells == 0) {
            return false;
        }
        find = (nearest != null ? nearest : nearestCell).immutable();
        worth = cells + termites * 3;
        return true;
    }

    /** It stands at its find and looks it over, antennae on the ground, then turns for home. */
    private void inspect(ServerLevel level) {
        if (find != null) {
            ant.getLookControl().setLookAt(Vec3.atCenterOf(find));
        }
        if (stageTicks >= numbers.inspectTicks()) {
            goHome();
        }
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
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
