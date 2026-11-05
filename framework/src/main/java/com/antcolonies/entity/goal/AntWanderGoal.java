package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.CellMap;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.LevelCellMap;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Idle wandering: now and then walks to a random nearby spot, through tunnels too. Underground some insects now and
 * then pick a spot on the ceiling instead and hang there upside down for a while, like real ants in their nest
 * ({@link #hangsFromCeilings}). An insect that stays in (termites by day, see {@link AbstractAntEntity#mayGoOut}) only
 * strolls inside; one that keeps to the rooms ({@link AbstractAntEntity#restsInRooms}) strolls only in them.
 * <p>
 * Resting first (M8 step 3Q, {@link #restsFirst}): for a species with {@code body_language.idle}, the insect mostly
 * stands where it is, strolling about once a minute a block to three (its restlessness sets how often), and walks to
 * its place to settle ({@link AbstractAntEntity#idlePlace}) when it is away from it; a stroll never leaves that place.
 * Without the species' numbers, or for a goal not asked to, it strolls as before (the harvester ant, confirmed in-game).
 */
public class AntWanderGoal extends Goal {
    private final AbstractAntEntity ant;
    private final double speed;
    /** On average one stroll starts every this many goal checks. */
    private final int interval;
    private final int radius;
    private final BooleanSupplier allowed;
    /** The chance (0 to 1) that a stroll underground ends on the ceiling instead of the floor. */
    private float ceilingChance;
    /** Resting first (M8 step 3Q): the share of the species' strolls this insect takes (0: none, it stands), or below 0: off. */
    private float restScale = -1.0F;
    /** Its place to settle, asked once a second ({@link AbstractAntEntity#idlePlace}), and when. */
    @Nullable
    private BlockPos place;
    private int placeAt = Integer.MIN_VALUE / 2;

    public AntWanderGoal(AbstractAntEntity ant, double speed, int interval, int radius, BooleanSupplier allowed) {
        this.allowed = allowed;
        this.ant = ant;
        this.speed = speed;
        this.interval = interval;
        this.radius = radius;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Underground, this share of strolls (0 to 1) goes up a wall and ends hanging from the ceiling. */
    public AntWanderGoal hangsFromCeilings(float chance) {
        this.ceilingChance = chance;
        return this;
    }

    /**
     * Rest first (M8 step 3Q): with its species' {@code body_language.idle}, it takes {@code share} of the strolls (1 a
     * worker, 0 a soldier with no post: it only walks back to where it may be).
     */
    public AntWanderGoal restsFirst(float share) {
        this.restScale = share;
        return this;
    }

    @Override
    public boolean canUse() {
        if (ant.isBeingGroomed(null)) {
            return false; // a nestmate is cleaning it: it holds still (M8 step 3Q, AllogroomGoal)
        }
        // (with a fight order it does not rest: between bouts of its fight it strolls as before, BUGS 97's skirmish)
        SpeciesProfile.Idle idle = restScale < 0.0F || ant.getFightSpot() != null ? null
                : ant.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::idle).orElse(null);
        if (idle != null) {
            return allowed.getAsBoolean() && rest(idle);
        }
        if (ant.getRandom().nextInt(interval) != 0 || !allowed.getAsBoolean()) {
            return false;
        }
        CellMap map = new LevelCellMap(ant.level(), ant);
        BlockPos here = ant.blockPosition();
        int rest = ant.restsNearEntrance();
        Colony colony = ant.restsInRooms() ? ant.getColony() : null;
        java.util.Set<BlockPos> rooms = colony == null ? java.util.Set.of() : new java.util.HashSet<>(colony.nest().getAllRoomCells(ant.level()));
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = ant.getRandom().nextInt(radius * 2 + 1) - radius;
            BlockPos spot = findFloor(map, here.offset(dx, 0, dz));
            if (spot == null || !ant.mayBeAt(spot)) {
                continue; // by day a termite only strolls inside
            }
            if (rest > 0 && !restsAt(spot, here, rest)) {
                continue; // a harvester ant with nothing to do stays in, up by the entrance
            }
            if (!rooms.isEmpty() && !rooms.contains(spot)) {
                continue; // a Matabele ant stays in the rooms, with the queen
            }
            if (ceilingChance > 0.0F && ant.getRandom().nextFloat() < ceilingChance && !ant.level().canSeeSky(spot)
                    && Foothold.ceiling(spot).isValid(map)
                    && ant.getNavigator().moveTo(Foothold.ceiling(spot), speed)) {
                return true;
            }
            if (ant.getNavigator().moveTo(spot, speed)) {
                return true;
            }
        }
        return false;
    }

    /** Asked every other tick (the game looks for new goals then): on average this many checks a minute. */
    private static final double CHECKS_A_MINUTE = 600.0;
    /** A stroll start or a settle walk this many checks apart at most, on average, when away from its place (about 5 s). */
    private static final int SETTLE_EVERY = 50;

    /**
     * Rest first: a walk to its place to settle when away from it (or from where it may be), else now and then a
     * short stroll that stays there. Most of the time nothing: it stands.
     */
    private boolean rest(SpeciesProfile.Idle idle) {
        BlockPos here = ant.blockPosition();
        CellMap map = null;
        if (ant.tickCount - placeAt >= 20 || ant.tickCount < placeAt) {
            place = ant.idlePlace(); // (asked once a second: finding the queen's room reads the nest)
            placeAt = ant.tickCount;
        }
        double settle = idle.settleWithin();
        boolean away = place != null && here.distSqr(place) > settle * settle;
        if (away || !ant.mayBeAt(here)) {
            if (ant.getRandom().nextInt(SETTLE_EVERY) != 0) {
                return false;
            }
            map = new LevelCellMap(ant.level(), ant);
            BlockPos around = place != null ? place : here;
            int reach = place != null ? Math.max(1, (int) Math.floor(settle)) : radius;
            for (int attempt = 0; attempt < 6; attempt++) {
                BlockPos spot = findFloor(map, around.offset(ant.getRandom().nextInt(reach * 2 + 1) - reach, 0,
                        ant.getRandom().nextInt(reach * 2 + 1) - reach));
                if (spot != null && ant.mayBeAt(spot) && inRooms(spot) && ant.getNavigator().moveTo(spot, speed)) {
                    return true;
                }
            }
            return false;
        }
        // A calm one a quarter of the species' strolls, the most restless 1.75 times (its personality, for life).
        double chance = idle.strollsPerMinute() * restScale * (0.25 + 1.5 * ant.restlessness()) / CHECKS_A_MINUTE;
        if (ant.getRandom().nextDouble() >= chance) {
            return false;
        }
        map = new LevelCellMap(ant.level(), ant);
        for (int attempt = 0; attempt < 6; attempt++) {
            double far = idle.strollFor(ant.getRandom().nextDouble());
            double angle = ant.getRandom().nextDouble() * Math.PI * 2.0;
            BlockPos spot = findFloor(map, here.offset((int) Math.round(Math.cos(angle) * far), 0, (int) Math.round(Math.sin(angle) * far)));
            if (spot == null || spot.equals(here) || !ant.mayBeAt(spot) || !inRooms(spot)
                    || place != null && spot.distSqr(place) > settle * settle) {
                continue;
            }
            if (ant.getNavigator().moveTo(spot, speed)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this spot is in the nest's rooms, for an insect that keeps to them (any spot for the others). */
    private boolean inRooms(BlockPos spot) {
        if (!ant.restsInRooms()) {
            return true;
        }
        Colony colony = ant.getColony();
        java.util.List<BlockPos> rooms = colony == null ? java.util.List.of() : colony.nest().getAllRoomCells(ant.level());
        return rooms.isEmpty() || rooms.contains(spot);
    }

    /**
     * For an insect that rests near the entrance ({@link AbstractAntEntity#restsNearEntrance}): a spot inside the nest,
     * within {@code rest} blocks of the entrance, or, for one deeper down, nearer to it than where it is now (it drifts up).
     */
    private boolean restsAt(BlockPos spot, BlockPos here, int rest) {
        Colony colony = ant.getColony();
        if (colony == null || !colony.nest().isInside(ant.level(), spot)) {
            return colony == null;
        }
        BlockPos entrance = colony.nest().getEntrance();
        double limit = (double) rest * rest;
        return spot.distSqr(entrance) <= limit || spot.distSqr(entrance) < here.distSqr(entrance);
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.getNavigator().isDone() && !ant.isBeingGroomed(null);
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
    }

    /** The first open cell with a floor in this column, looking a few blocks up and down from the ant's level. */
    @Nullable
    static BlockPos findFloor(CellMap map, BlockPos column) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = column.above(dy);
            if (map.isOpen(pos) && map.isSolidFace(pos.below(), Direction.UP)) {
                return pos;
            }
        }
        return null;
    }
}
