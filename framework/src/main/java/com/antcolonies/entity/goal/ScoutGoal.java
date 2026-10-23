package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Searching for food, for species that find their food by looking ({@code food.find_radius}, see {@code FoodMap}: a
 * plant within the find radius of a worker is found by it). Two kinds, by the species' profile:
 *
 * <p><b>Scouting and telling</b> (termites): a forager with no food plant it knows of walks out to a random spot in the
 * colony's food area and looks round there; the colony knows what it found once it is back inside. Even when there is
 * known food, about one trip in {@link #SCOUT_ANYWAY} is a scouting trip, so a richer find farther out is still made.
 * The spot lies between a third of the food radius and all of it from the entrance, so the scouts range out.
 *
 * <p><b>Searching alone</b> ({@code food.search.alone}, the red harvester ant; brain step 4c, real <i>Pogonomyrmex
 * barbatus</i>): a forager with no food of its own to go for walks out to search. Where: round its own spot (where it
 * last found food, see {@code ForagerSpots}) if it has one; otherwise out along a trunk trail (a stronger trail draws
 * more) to about where the foragers leave it, now and then in a new direction instead (how a new trail opens); after an
 * empty trip, another way than that one, from near the nest (the user's decision of 2026-10-16). How: out by the trail, then a meandering search in short hops round the
 * search centre, feeling about at each. It searches until it finds food (the user's decision: real foragers seldom come
 * home empty); then {@code ClipGrassGoal} or {@code GatherSeedGoal} take over. A trip to its own spot that finds nothing
 * there in {@code spot_ticks} counts as empty (two and the spot is dropped). Every {@code drift_ticks} without a find,
 * its search drifts farther out (the range stretches from the ants' own failures, the user's decision). It comes home
 * empty only when the foraging time ends or after {@code give_up_ticks}.
 *
 * <p><b>Called out to a rich patch</b> ({@code food.search.recruit}, see {@code Recruits}; real <i>P. barbatus</i> and
 * <i>P. rugosus</i>): a forager touched by a nestmate home from a rich patch goes out at once, the patch as its spot,
 * and follows the finder's scent line out point by point, single file with the others called out, passing poorer
 * food by on the way. Once at the patch it takes food there like any forager.
 *
 * <p>Only at foraging time, only while the colony wants food, and only once the door lets it out. Mind layer: it only
 * says where to go, through the ant's navigator.
 */
public class ScoutGoal extends Goal {
    /** With known food to go for, one trip in this many is still a scouting trip (scouting and telling). */
    private static final int SCOUT_ANYWAY = 6;
    /** It gives up a scouting walk after this long (ticks; scouting and telling). */
    private static final int GIVE_UP_TICKS = 600;
    /** How long (ticks) it stands and looks round at the spot (scouting and telling). */
    private static final int LOOK_TICKS = 40;
    /** How long (ticks) a lone searcher feels about after each hop. My pick. */
    private static final int FEEL_TICKS = 20;
    /** How often (ticks) a lone searcher checks whether it has come across food. */
    private static final int CHECK_TICKS = 10;
    /** A lone searcher that came across food stands by it this long (ticks), for cutting or gathering to take over. */
    private static final int STAY_BY_FOOD = 100;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    /** Where it goes to look: the spot (scouting) or the search centre (searching alone). */
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int looking;

    // --- searching alone ---
    private boolean alone;
    /** Where it leaves the trunk trail for its search centre; null once there, or with no trail that way. */
    @Nullable
    private BlockPos via;
    /** The search's direction from the nest's middle (radians). */
    private double angle;
    /** How far out (blocks from the nest's middle) the search centre was when it set out, and is now (it drifts). */
    private double from;
    private double out;
    /**
     * How far out (blocks from the nest's middle) this search's way reaches: the tip of the trail it went out by, or the
     * colony's range; its drift goes at most the stretch past it (long trails: a trail grows from its tip).
     */
    private int reachOfWay;
    /** Whether it is searching round its own spot, not yet found empty this trip. */
    private boolean atOwnSpot;
    private boolean arrived;
    /** How long (ticks) it has searched round its centre. */
    private long searched;
    private boolean found;
    private int stayed;
    private int feeling;
    /** The scent line a recruit follows out (nest end first), and the point it walks to next; null when not following one. */
    @Nullable
    private List<BlockPos> line;
    private int lineAt;
    private int lineStuck;

    public ScoutGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || ant.hasFullMandibles() || !ant.looksForWork(40)
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.foodMap().findsByLooking() || !colony.isForagingTime(level) || !colony.food().wantsSeeds(level)
                || colony.mustWait(level, ant)) {
            return false; // (a searcher waits for the homecomers' touches like any forager, see colony.Door)
        }
        alone = colony.foodMap().searchesAlone();
        if (alone) {
            if (hasFoodOfItsOwn(level)) {
                return false; // it has come across food: off to cut it (ClipGrassGoal, GatherSeedGoal)
            }
            spot = planSearch(level, colony);
        } else {
            boolean foodToGoFor = colony.food().findGrass(level, ant.blockPosition(), Set.of(), ant.getUUID()) != null;
            if (foodToGoFor && ant.getRandom().nextInt(SCOUT_ANYWAY) != 0) {
                return false;
            }
            spot = pickSpot(level, colony);
        }
        if (spot != null) {
            colony.news().tell(level, ColonyNews.SET_OUT, new ColonyNews.SetOut(ant));
        }
        return spot != null;
    }

    /**
     * Whether this lone searcher has come across food it may go for: a plant it found, or a seed by it (a recruit on its
     * way to its patch passes poorer food by, see {@code Recruits}).
     */
    private boolean hasFoodOfItsOwn(ServerLevel level) {
        UUID id = ant.getUUID();
        if (colony.foodMap().hasFound(id)) {
            BlockPos grass = colony.food().findGrass(level, ant.blockPosition(), Set.of(), id);
            if (grass != null && !passesBy(id, grass)) {
                return true;
            }
        }
        ItemEntity seed = colony.food().findSeed(level, ant.blockPosition(), Set.of());
        return seed != null && !passesBy(id, seed.blockPosition());
    }

    /** Whether this recruit passes the food by on its way to its patch (never in a colony without recruiting). */
    private boolean passesBy(UUID id, BlockPos food) {
        return colony.passesBy(ant.level(), id, food);
    }

    /** A random open spot on the ground in the colony's food area, outside the nest and the mound; null if none was hit. */
    @Nullable
    private BlockPos pickSpot(ServerLevel level, Colony colony) {
        int radius = colony.foragingRadius();
        for (int tries = 0; tries < 8; tries++) {
            float angle = ant.getRandom().nextFloat() * Mth.TWO_PI;
            double distance = radius / 3.0 + ant.getRandom().nextDouble() * radius * 2.0 / 3.0;
            BlockPos cell = groundCell(level, colony, angle, distance, 0);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Where a lone searcher searches this trip (see the class comment), or null if no open ground was hit, as the colony's
     * lone search plans it (the harvester's forager spots, {@code colony.ForagerSearch}, Apex step 9a). Sets the search's
     * direction, how far out it starts and how far its way reaches.
     */
    @Nullable
    private BlockPos planSearch(ServerLevel level, Colony colony) {
        ColonyHooks.LoneSearch search = colony.hooks().first(ColonyHooks.LONE_SEARCH);
        ColonyHooks.SearchPlan plan = search == null ? null
                : search.plan(level, ant, (way, distance, jitter) -> groundCell(level, colony, way, distance, jitter));
        if (plan == null) {
            atOwnSpot = false;
            return null;
        }
        atOwnSpot = plan.atOwnSpot();
        angle = plan.angle();
        from = plan.from();
        out = from;
        reachOfWay = plan.reachOfWay();
        return plan.spot();
    }

    /**
     * An open cell on the ground {@code distance} blocks out from the nest's middle in direction {@code angle}, give or
     * take {@code jitter} blocks: loaded, near the nest's height, dry, free to stand in, with a floor, outside the nest
     * and the mound, and in the test's arena; null if none was hit.
     */
    @Nullable
    private BlockPos groundCell(ServerLevel level, Colony colony, double angle, double distance, int jitter) {
        BlockPos center = colony.getCenter();
        for (int tries = 0; tries < 4; tries++) {
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance) + (jitter > 0 ? ant.getRandom().nextInt(jitter * 2 + 1) - jitter : 0);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * distance) + (jitter > 0 ? ant.getRandom().nextInt(jitter * 2 + 1) - jitter : 0);
            BlockPos cell = openGround(level, colony, x, z);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    /** The cell to stand in on the ground at this column if it is open ground (see {@link #groundCell}), or null. */
    @Nullable
    private static BlockPos openGround(ServerLevel level, Colony colony, int x, int z) {
        BlockPos center = colony.getCenter();
        if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
            return null;
        }
        BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockPos below = cell.below();
        if (!colony.foodMap().mayScout(cell) || !colony.mayForageAt(level, cell)) {
            return null;
        }
        if (Math.abs(cell.getY() - center.getY()) > 6 || !level.getFluidState(cell).isEmpty()
                || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || colony.nest().isInside(level, cell)
                || colony.moundParts(level).containsKey(cell)) {
            return null;
        }
        return cell;
    }

    /** How far (blocks, sideways) a cell is from the nest's middle. */
    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean canContinueToUse() {
        if (colony == null || spot == null || ant.hasFullMandibles() || !colony.isForagingTime(ant.level())) {
            return false;
        }
        if (alone) {
            return found ? stayed < STAY_BY_FOOD : ticks < giveUpTicks();
        }
        return ticks < GIVE_UP_TICKS && looking < LOOK_TICKS;
    }

    private int giveUpTicks() {
        ColonyHooks.LoneSearch search = colony == null ? null : colony.hooks().first(ColonyHooks.LONE_SEARCH);
        SpeciesProfile.SearchNumbers numbers = search == null ? null : search.settings();
        return numbers == null ? GIVE_UP_TICKS : numbers.giveUpTicks();
    }

    @Override
    public void start() {
        ticks = 0;
        looking = 0;
        searched = 0;
        feeling = 0;
        found = false;
        stayed = 0;
        arrived = false;
        via = null;
        line = null;
        lineStuck = 0;
        if (alone && colony != null && spot != null && ant.level() instanceof ServerLevel level) {
            colony.news().tell(level, ColonyNews.SEARCH_STARTED, new ColonyNews.SearchStarted(ant.getUUID()));
            ColonyHooks.CalledOut called = colony.hooks().first(ColonyHooks.CALLED_OUT);
            line = called == null ? null : called.lineFor(level, ant.getUUID());
            if (line != null) {
                lineAt = nearestPoint(line); // called out: along the finder's scent line, from where it meets it
                if (ant.getNavigator().moveTo(line.get(lineAt), 1.0)) {
                    return;
                }
                line = null; // no way onto the line from here: as any forager to its spot
            }
            ColonyHooks.Trails trails = colony.lastingTrails();
            via = trails == null ? null : trails.wayOut(level, spot);
            if (via != null && flat(ant.blockPosition()) >= flat(via) - 1.0) {
                via = null; // already out past where it would leave the trail
            }
        }
        if (spot != null && !ant.getNavigator().moveTo(via != null ? via : spot, 1.0)) {
            if (via != null) {
                via = null; // no way onto the trail from here: straight there
                if (ant.getNavigator().moveTo(spot, 1.0)) {
                    return;
                }
            }
            spot = null; // no way there: another time, another spot
        }
    }

    /** The point of the line nearest the ant (where it joins it). */
    private int nearestPoint(List<BlockPos> points) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double distance = points.get(i).distSqr(ant.blockPosition());
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        line = null;
        if (alone && colony != null && ant.level() instanceof ServerLevel level) {
            boolean closing = !colony.isForagingTime(level);
            boolean gaveUp = ticks >= giveUpTicks();
            // Heard by the recruits (its call is used up: it got there and took food, or did not), the forager spots (home
            // empty: its next search goes another way, from near the nest again) and the door (gave up: it goes in and
            // waits for touches again).
            boolean empty = !found && !ant.hasFullMandibles() && arrived && (closing || gaveUp);
            colony.news().tell(level, ColonyNews.SEARCH_OVER, new ColonyNews.SearchOver(ant, empty, angle, gaveUp));
        }
        spot = null;
        via = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null) {
            return;
        }
        ticks++;
        if (alone) {
            searchAlone();
            return;
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        if (ant.blockPosition().distSqr(spot) > 2.0 && looking == 0) {
            if (!ant.getNavigator().moveTo(spot, 1.0)) {
                ticks = GIVE_UP_TICKS;
            }
            return;
        }
        if (looking++ % 15 == 0) { // stands and looks round (feels about with its antennae)
            lookRound();
        }
    }

    /** One tick of a lone search: out by the trail, to the centre, then hops round it, drifting outward with no find. */
    private void searchAlone() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (found) {
            if (stayed++ % 15 == 0) {
                lookRound(); // stands by its find, feeling it over, till cutting or gathering takes over
            }
            return;
        }
        if (ticks % CHECK_TICKS == 0 && hasFoodOfItsOwn(level)) {
            found = true; // it has come across food (it is cut by ClipGrassGoal, picked up by GatherSeedGoal)
            ant.getNavigator().stop();
            return;
        }
        if (line != null) {
            followLine();
            return;
        }
        if (via != null) {
            BlockPos at = ant.blockPosition();
            if (Math.abs(at.getX() - via.getX()) <= 1 && Math.abs(at.getZ() - via.getZ()) <= 1 && Math.abs(at.getY() - via.getY()) <= 2) {
                via = null; // off the trail here
                ant.getNavigator().stop();
            } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(via, 1.0)) {
                via = null; // no way along the trail from here: straight to the centre
            } else {
                return;
            }
        }
        if (!arrived) {
            if (horizontal(ant.blockPosition(), spot) <= 2.5) {
                arrived = true;
            } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(spot, 1.0)) {
                arrived = true; // no way right to the centre: it searches from where it got to
            } else {
                return;
            }
        }
        ColonyHooks.LoneSearch lone = colony.hooks().first(ColonyHooks.LONE_SEARCH);
        SpeciesProfile.SearchNumbers search = lone == null ? null : lone.settings();
        if (search == null) {
            return;
        }
        searched++;
        if (atOwnSpot && searched >= search.spotTicks()) {
            lone.emptyAtSpot(ant.getUUID()); // nothing at its spot this time
            atOwnSpot = false;
        }
        double drifted = search.drifted(from, searched, reachOfWay);
        if (drifted > out + 0.5) {
            BlockPos moved = groundCell(level, colony, angle, drifted, 1);
            out = drifted;
            if (moved != null) {
                spot = moved; // nothing here for a while: it searches farther out
                ant.getNavigator().moveTo(spot, 1.0);
                feeling = 0;
                return;
            }
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        if (feeling > 0) {
            if (feeling-- % 7 == 0) { // stands and feels about with its antennae
                lookRound();
            }
            return;
        }
        int wander = search.wander();
        for (int tries = 0; tries < 4; tries++) {
            int x = spot.getX() + ant.getRandom().nextInt(wander * 2 + 1) - wander;
            int z = spot.getZ() + ant.getRandom().nextInt(wander * 2 + 1) - wander;
            BlockPos hop = openGround(level, colony, x, z);
            if (hop != null && horizontal(hop, ant.blockPosition()) >= 1.5 && ant.getNavigator().moveTo(hop, 0.8)) {
                feeling = FEEL_TICKS;
                return;
            }
        }
        feeling = FEEL_TICKS; // nowhere to hop to just now: feels about here
    }

    /** A recruit walks the scent line out, point by point (single file: every recruit walks the same points). */
    private void followLine() {
        BlockPos next = line.get(lineAt);
        BlockPos at = ant.blockPosition();
        boolean there = horizontal(at, next) <= 1.5 && Math.abs(at.getY() - next.getY()) <= 2;
        if (!there && ant.getNavigator().isDone()) {
            there = ++lineStuck > 3 || !ant.getNavigator().moveTo(next, 1.0); // can't get right onto this point: on to the next
        }
        if (!there) {
            return;
        }
        lineStuck = 0;
        if (++lineAt >= line.size()) {
            line = null; // at the patch's end of the line: it walks to its spot and searches round it
            ant.getNavigator().stop();
            return;
        }
        ant.getNavigator().moveTo(line.get(lineAt), 1.0);
    }

    private void lookRound() {
        float turn = ant.getRandom().nextFloat() * Mth.TWO_PI;
        ant.getLookControl().setLookAt(ant.getX() + Mth.cos(turn) * 3, ant.getY(), ant.getZ() + Mth.sin(turn) * 3);
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
