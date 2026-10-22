package com.antcolonies.entity.goal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.colony.Patrol;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The mind of a patroller (brain step 4b, part 3; see {@code colony.Patrol}, which picks the patrollers and their ways).
 * Real red harvester ant patrollers come out first each morning, walk over and round the hill, then out along the
 * first part of a trail, and come back in: they are the colony's safety check, not food scouts. Here: four points round
 * the hill, then out along its way to its reach (none for a patroller only sent round the hill after danger), then home
 * to the entrance, where it touches the foragers waiting by the door. It turns back at once, and its way is shut for
 * the day, if it is hurt (see {@code AbstractAntEntity.hurt}), a hostile mob is close, or it meets ants of another
 * colony. Only says where to go; the legs ({@code AntNavigator}) find the way.
 */
public class PatrolGoal extends Goal {
    /** How often (ticks) it looks round for trouble. */
    private static final int LOOK_EVERY = 10;
    /** A waypoint it can't reach in this long is skipped. */
    private static final int WAYPOINT_TICKS = 300;
    /** However the walk went, after this long it counts as home safely (so a stuck patroller can't hold a round up). */
    private static final int GIVE_UP_TICKS = 3600;
    /** Another colony's ant this close: neighbours. */
    private static final double NEIGHBOUR_WITHIN = 4.0;
    /** How long it stays by the door once home, touching the foragers waiting there. */
    private static final int AT_DOOR_TICKS = 100;

    private final AbstractAntEntity ant;
    /** The walk round the hill, out along its way and home. */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    /** The colony's patrol (only a species that sends patrollers out has one). */
    @Nullable
    private Patrol patrol;
    private final List<BlockPos> waypoints = new ArrayList<>();
    private int ticks;
    private int waypointTicks;
    private boolean goingHome;
    /** Ticks since it got home (-1: not home yet). */
    private int atDoor = -1;
    private boolean calm = true;

    public PatrolGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || ant.hasFullMandibles() || !(ant.level() instanceof ServerLevel level)
                || ant.tickCount % 10 != ant.getId() % 10) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        patrol = colony == null ? null : colony.part(ColonyParts.PATROL).orElse(null);
        return patrol != null && patrol.jobOf(ant.getUUID()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (colony == null || patrol == null || ticks >= GIVE_UP_TICKS + AT_DOOR_TICKS) {
            return false;
        }
        return atDoor >= 0 ? atDoor < AT_DOOR_TICKS : patrol.jobOf(ant.getUUID()) != null;
    }

    @Override
    public void start() {
        ticks = 0;
        waypointTicks = 0;
        atDoor = -1;
        calm = true;
        goingHome = false;
        waypoints.clear();
        errand.begin();
        if (!(ant.level() instanceof ServerLevel level) || colony == null || patrol == null) {
            return;
        }
        Patrol.Job job = patrol.jobOf(ant.getUUID());
        if (job == null) {
            return;
        }
        patrol.setsOff(ant.getUUID());
        // Round the hill, starting on the far side from its way and ending on its way's side.
        BlockPos center = colony.getCenter();
        for (int i = 1; i <= 4; i++) {
            double angle = job.angle() + Math.PI + i * Math.PI / 2;
            waypoints.add(surface(level, center, angle, Patrol.HILL_ROUND));
        }
        if (!job.hillOnly()) {
            int reach = job.reach();
            waypoints.add(surface(level, center, job.angle(), (Patrol.HILL_ROUND + reach) / 2));
            waypoints.add(surface(level, center, job.angle(), reach));
        }
        nextWaypoint(level);
    }

    @Override
    public void stop() {
        errand.stop();
        if (patrol != null && ant.level() instanceof ServerLevel level && atDoor < 0 && patrol.jobOf(ant.getUUID()) != null
                && ticks >= GIVE_UP_TICKS) {
            patrol.home(level, ant, calm); // gave up: counts as home
        }
        if (colony != null && atDoor >= 0) {
            colony.part(ColonyParts.DOOR).ifPresent(door -> door.doneCarrying(ant));
        }
        waypoints.clear();
        colony = null;
        patrol = null;
    }

    @Override
    public void tick() {
        if (colony == null || patrol == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (atDoor >= 0) {
            atDoor++;
            if (atDoor % 5 == 0 && calm) {
                colony.part(ColonyParts.DOOR).ifPresent(door -> door.homecomer(level, ant, false));
            }
            return;
        }
        if (ticks >= GIVE_UP_TICKS) {
            arriveHome(level);
            return;
        }
        if (!goingHome && ticks % LOOK_EVERY == 0) {
            lookForTrouble(level);
        }
        if (goingHome || patrol.turnedBack(ant.getUUID())) {
            goingHome = true;
            BlockPos entrance = colony.nest().getEntrance();
            if (colony.nest().isInside(level, ant.blockPosition()) || near(ant.blockPosition(), entrance)) {
                arriveHome(level);
            } else if (!errand.walkOn(entrance, 1.0)) {
                arriveHome(level); // no way home from here: it gets in somehow
            }
            return;
        }
        waypointTicks++;
        BlockPos target = waypoints.isEmpty() ? null : waypoints.get(0);
        if (target == null) {
            goingHome = true;
            errand.stop();
        } else if (near(ant.blockPosition(), target) || waypointTicks >= WAYPOINT_TICKS) {
            waypoints.remove(0);
            nextWaypoint(level);
        } else if (!errand.walkOn(target, 1.0)) {
            waypoints.remove(0); // no way there: on to the next
            nextWaypoint(level);
        }
    }

    private void nextWaypoint(ServerLevel level) {
        waypointTicks = 0;
        errand.stop();
        while (!waypoints.isEmpty() && !errand.setOff(waypoints.get(0), 1.0)) {
            waypoints.remove(0);
        }
        if (waypoints.isEmpty()) {
            goingHome = true;
        }
    }

    /** Hurt (see {@code AbstractAntEntity.hurt}), a hostile mob close, or another colony's ants: it turns for home. */
    private void lookForTrouble(ServerLevel level) {
        SpeciesProfile.PatrolNumbers numbers = colony.profile().food().patrol().orElse(null);
        double hostile = numbers == null ? 4.0 : numbers.hostileWithin();
        boolean danger = !level.getEntitiesOfClass(Mob.class, ant.getBoundingBox().inflate(hostile),
                mob -> mob.isAlive() && !(mob instanceof AbstractAntEntity)
                        && (mob instanceof Enemy || mob.getTarget() instanceof AbstractAntEntity)).isEmpty();
        if (danger) {
            calm = false;
            goingHome = true;
            patrol.danger(level, ant, "a hostile mob close");
            errand.stop();
            return;
        }
        boolean neighbours = !level.getEntitiesOfClass(AbstractAntEntity.class, ant.getBoundingBox().inflate(NEIGHBOUR_WITHIN),
                other -> other.isAlive() && other.getColonyId() != null && !other.getColonyId().equals(ant.getColonyId())).isEmpty();
        if (neighbours) {
            calm = false;
            goingHome = true;
            patrol.neighbours(level, ant);
            errand.stop();
        }
        if (patrol.turnedBack(ant.getUUID())) {
            calm = false; // hurt: AbstractAntEntity.hurt told the patrol
            goingHome = true;
        }
    }

    private void arriveHome(ServerLevel level) {
        errand.stop();
        if (patrol.turnedBack(ant.getUUID())) {
            calm = false;
        }
        patrol.home(level, ant, calm);
        atDoor = 0;
    }

    /** The open cell on the ground {@code distance} blocks from the middle in this direction. */
    private static BlockPos surface(ServerLevel level, BlockPos center, double angle, int distance) {
        int x = center.getX() + Mth.floor(Math.cos(angle) * distance + 0.5);
        int z = center.getZ() + Mth.floor(Math.sin(angle) * distance + 0.5);
        if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
            return center;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static boolean near(BlockPos at, BlockPos target) {
        double dx = at.getX() - target.getX();
        double dz = at.getZ() - target.getZ();
        return dx * dx + dz * dz <= 1.5 * 1.5 && Math.abs(at.getY() - target.getY()) <= 2;
    }
}
