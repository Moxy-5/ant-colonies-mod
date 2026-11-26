package com.antcolonies.savanna.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.colony.Raid;
import com.antcolonies.savanna.colony.RaidGround;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.entity.goal.GroomGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 3b, a raider walking in its colony's raid ({@link Raid}). The raid (the colony's brain) says where this ant
 * should be now: its place in the bunch while the raid musters, in the line on the march, bunched up short of the
 * termites, home again after the fight. This goal only walks there through the ant's navigator, hop by hop for a far
 * place, and faces where the raid is heading; standing at its place it is never a statue (it turns, touches a
 * neighbour, cleans an antenna). The fight itself is {@code FightGoal}'s: the raid gives each raider a fight order at
 * the attack, and this goal stands aside while one holds. Step 3c: after the fight the raid gives each raider with room
 * a stung termite to fetch ({@code take}): it walks to it and picks it up. An ant that left its raid and goes home by
 * itself is {@link MatabeleWayHomeGoal}'s (the raid rewrite, part c: it was a second job of this goal). Mind layer only.
 */
public class MatabeleRaidGoal extends Goal {
    /** The longest hop (blocks) it walks in one go. */
    private static final int HOP = 14;
    /** It walks again once its place moved this far from where it was walking (blocks). */
    private static final double MOVED = 1.5;
    /** It counts as at its place this near it (blocks). */
    private static final double AT = 1.2;
    /** ... and this near it up or down (blocks). */
    private static final double UP_DOWN = 1.5;
    /** After no way to its place it tries again this many ticks later. */
    private static final int RETRY = 20;

    private final MatabeleEntity ant;
    @Nullable
    private Raid column;
    @Nullable
    private BlockPos walkingTo;
    private int waitUntil;
    /** Step 3c: whether it stands picking a termite up. */
    private boolean picking;
    /** BUGS 141: walking without getting anywhere, it stops and stands where it is a while. */
    private final Headway headway = new Headway();
    /**
     * Step 3Q, Q5: cleaning an antenna while it waits at its place (real raiders standing at a halt clean themselves
     * between bouts of antenna-waving): the tick the clean ends, 0 for none.
     */
    private int cleaningUntil;
    /**
     * Raid flow (the user's raid of 2026-11-06: "it just looks wrong for ants to be standing still while they wait"): a
     * raider waiting at its place is never a statue. Real raiders bunched at the entrance, at a halt or short of the
     * termites keep moving: they turn, touch a neighbour with the antennae, shuffle a step. The point it looks at now and
     * until when (tick, 0 for none); the next tick it may do something new.
     */
    @Nullable
    private Vec3 millLook;
    private int millLookUntil;
    private int nextMill;

    public MatabeleRaidGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private Raids raids() {
        Colony colony = ant.getColony();
        return colony == null ? null : colony.part(SavannaParts.RAIDS).orElse(null);
    }

    @Nullable
    private Raid myColumn() {
        Raids raids = raids();
        Raid found = raids == null ? null : raids.column();
        return found != null && found.has(ant.getUUID()) && found.stage() != Raid.Stage.DONE ? found : null;
    }

    @Override
    public boolean canUse() {
        if (ant.getFightSpot() != null || ant.isDowned() || !(ant.level() instanceof ServerLevel)) {
            return false;
        }
        column = ant.fightsNoMore() ? null : myColumn();
        return column != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (ant.getFightSpot() != null || ant.isDowned()) {
            return false;
        }
        return column != null && !ant.fightsNoMore() && column == myColumn();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        millLook = null;
        walkingTo = null;
        waitUntil = 0;
        headway.reset();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        ant.getNavigator().setClimbPace(1.0);
        endCleaning();
        column = null;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        Raid.Order order = column == null ? null : column.orderFor(level, ant);
        if (order == null) {
            return;
        }
        if (ant.tickCount < waitUntil && picking) {
            return; // (picking a termite up: it stands)
        }
        picking = false;
        if (cleaningUntil != 0 && ant.tickCount >= cleaningUntil) {
            endCleaning();
        }
        boolean millLooking = millLook != null && ant.tickCount < millLookUntil;
        if (millLooking && ant.getNavigator().isDone() && cleaningUntil == 0) {
            ant.getLookControl().setLookAt(millLook.x, millLook.y, millLook.z);
        } else if (order.lookAt() != null && ant.getNavigator().isDone() && cleaningUntil == 0) {
            ant.getLookControl().setLookAt(order.lookAt().getX() + 0.5, order.lookAt().getY() + 0.3, order.lookAt().getZ() + 0.5);
        }
        if (headway.lost(ant)) {
            ant.getNavigator().stop();
            walkingTo = order.to();
            waitUntil = ant.tickCount + RETRY * 5;
            return;
        }
        ant.getNavigator().setClimbPace(column.climbPace());
        if (order.onTheMove() && order.to() != null) {
            march(level, order);
            return;
        }
        BlockPos to = order.to();
        if (to == null) {
            ant.getNavigator().stop(); // (it stands: behind a broken trail, at the gathering place, ...)
            walkingTo = null;
            mill(level, null); // (turning and touching its neighbours as it waits)
            return;
        }
        Entity prey = order.take() != null ? level.getEntity(order.take()) : null;
        int atPrey = prey instanceof LivingEntity stung ? atPrey(stung) : NOT_AT_PREY;
        if (atPrey == STEPPING_TO_PREY) {
            return; // (the last little bit straight at it)
        }
        if (atPrey == AT_PREY) {
            // Step 3c: at its stung termite: it stops, takes it up in its jaws, and stands a moment.
            ant.getNavigator().stop();
            ant.getLookControl().setLookAt(prey);
            if (column.pickUp(level, ant, order.take())) {
                picking = true;
                waitUntil = ant.tickCount + column.pickTicks();
            }
            walkingTo = null;
            return;
        }
        // At its place counts in height too (R3, BUGS 87: a raider down a dead mound's chimney, right under its place by
        // the entrance, counted as there by the flat distance and stood still 8 blocks down).
        boolean atPlace = flat(ant.blockPosition(), to) <= AT && Math.abs(ant.getY() - to.getY()) <= UP_DOWN;
        boolean moved = walkingTo == null || flat(walkingTo, to) >= MOVED || Math.abs(walkingTo.getY() - to.getY()) > UP_DOWN;
        // Step 3Q, Q5: one standing where it last walked to stays there while its place shifts less than MOVED (the
        // column's places wobble a little as the line closes up: it used to walk off and back for every wobble).
        boolean settled = walkingTo != null && flat(ant.blockPosition(), walkingTo) <= AT
                && Math.abs(ant.getY() - walkingTo.getY()) <= UP_DOWN;
        boolean idleAndOff = ant.getNavigator().isDone() && !atPlace && !settled;
        if (!moved && !idleAndOff || ant.tickCount < waitUntil) {
            if (!moved && ant.getNavigator().isDone() && order.take() == null && (atPlace || settled)) {
                maybeClean();
                if (cleaningUntil == 0) {
                    mill(level, walkingTo != null ? walkingTo : to);
                }
            }
            return;
        }
        endCleaning();
        if (atPlace && ant.getNavigator().isDone() && order.take() == null) {
            walkingTo = to;
            return;
        }
        if (!walkToward(level, to, order.pace())) {
            waitUntil = ant.tickCount + RETRY; // (no way now: a little later; BUGS 76: never searched every tick)
            if (order.take() != null) {
                column.noWayTo(level, ant, order.take()); // (another may reach it, or this one later)
            }
        }
    }

    /**
     * The stream: marching, its place on the trail moves on with the column every few ticks, a little ahead of the ant. It never settles or mills there: it walks on, its route planned again once the place has moved
     * {@link #MARCH_MOVED} or its walk is over, and in between only its pace changes (hurrying to its place in the stream,
     * or walking on slower to let the column come up to it).
     */
    private void march(ServerLevel level, Raid.Order order) {
        endCleaning();
        millLook = null;
        BlockPos to = order.to();
        boolean moved = walkingTo == null || flat(walkingTo, to) >= MARCH_MOVED || Math.abs(walkingTo.getY() - to.getY()) > UP_DOWN;
        if (!moved && !ant.getNavigator().isDone()) {
            ant.getNavigator().setPace(order.pace());
            return;
        }
        if (!moved && flat(ant.blockPosition(), to) < 1.0 && Math.abs(ant.getY() - to.getY()) <= UP_DOWN || ant.tickCount < waitUntil) {
            return; // (at its place already: the column gives it the next one in a moment)
        }
        if (!walkToward(level, to, order.pace())) {
            waitUntil = ant.tickCount + RETRY; // (no way now: a little later; the column's stuck check lets it go if it lasts)
        }
    }

    /** The stream: a marching raider plans its route again once its place has moved this far (blocks, flat). */
    private static final double MARCH_MOVED = 1.0;

    /**
     * Step 3c, at its stung termite to pick it up. Real sizes, step 2: its jaws on it (as in a fight, a little more
     * downward into a broken tunnel; it used to take one up to 1.6 blocks off, three major lengths); beside it, its
     * walk over, it steps the last little bit straight at it first; getting no nearer (a step of comb, a hole's edge), it
     * takes it from where it stands, within {@link Raid#PICK_REACH} as before real sizes.
     */
    private int atPrey(LivingEntity prey) {
        if (ant.reachesInFight(prey)) {
            return AT_PREY;
        }
        if (prey.distanceTo(ant) > Raid.PICK_REACH) {
            return NOT_AT_PREY;
        }
        AbstractAntEntity.CloseIn step = ant.closeIn(prey.position(), Raid.PICK_REACH);
        if (step == AbstractAntEntity.CloseIn.STEPPING) {
            return STEPPING_TO_PREY;
        }
        if (step == AbstractAntEntity.CloseIn.NO_NEARER) {
            return AT_PREY;
        }
        // FAR with its walk over: the prey a little up or down where no step reaches (up a step of comb, down a broken
        // tunnel): taken from where it stands, as before real sizes (BUGS 130; it used to wait there for ever).
        return ant.getNavigator().isDone() ? AT_PREY : NOT_AT_PREY;
    }

    /** {@link #atPrey}: not there yet (it walks on), there (it picks it up), or stepping the last little bit. */
    private static final int NOT_AT_PREY = 0;
    private static final int AT_PREY = 1;
    private static final int STEPPING_TO_PREY = 2;

    /**
     * Step 3Q, Q5: standing at its place, now and then it cleans an antenna, by the one rule every idle insect cleans by
     * ({@link GroomGoal#pick}, asked once a second); never with jaws full, hurt or off the ground.
     */
    private void maybeClean() {
        if (cleaningUntil != 0 || ant.tickCount % 20 != (ant.getId() + 8) % 20 || !ant.onGround() || ant.hasFullMandibles()
                || ant.hurtTime > 0 || ant.isInWater()) {
            return;
        }
        GroomGoal.Clean clean = GroomGoal.pick(ant);
        if (clean != null) {
            ant.setActivity(clean.side());
            cleaningUntil = ant.tickCount + clean.ticks();
        }
    }

    /**
     * Raid flow: standing at its place, every 1 to 3 s it does something small (my picks): turns its head to a nestmate
     * beside it (antennating, half the time when one is near), looks about, or shuffles a step to a cell beside it still
     * at its place (never from a wall, with jaws full, or for the front, {@code anchor} null).
     */
    private void mill(ServerLevel level, @Nullable BlockPos anchor) {
        if (ant.tickCount < nextMill || !ant.onGround() || ant.isInWater()) {
            return;
        }
        nextMill = ant.tickCount + MILL_LEAST + ant.getRandom().nextInt(MILL_MORE);
        float roll = ant.getRandom().nextFloat();
        if (roll < 0.5F) {
            MatabeleEntity mate = level.getEntitiesOfClass(MatabeleEntity.class, ant.getBoundingBox().inflate(1.5),
                    other -> other != ant && other.isAlive()).stream()
                    .min(Comparator.comparingDouble(ant::distanceToSqr)).orElse(null);
            if (mate != null) {
                millLook = mate.position().add(0.0, mate.getBbHeight() * 0.6, 0.0);
                millLookUntil = ant.tickCount + 20 + ant.getRandom().nextInt(20);
                return;
            }
        }
        if (roll < 0.8F || anchor == null || ant.hasFullMandibles()) {
            double turn = (ant.getRandom().nextDouble() - 0.5) * Math.PI * 1.2;
            double yaw = Math.toRadians(ant.getYRot() + 90.0) + turn;
            millLook = ant.position().add(Math.cos(yaw) * 2.0, ant.getBbHeight() * 0.5, Math.sin(yaw) * 2.0);
            millLookUntil = ant.tickCount + 15 + ant.getRandom().nextInt(25);
            return;
        }
        // A shuffle: a step to a cell beside it, still at its place (so it does not walk back), at an easy pace.
        List<BlockPos> steps = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos cell = ant.blockPosition().relative(side);
            if (flat(cell, anchor) <= AT - 0.2 && RaidGround.canStand(level, cell)) {
                steps.add(cell);
            }
        }
        if (!steps.isEmpty()) {
            millLook = null;
            ant.getNavigator().moveTo(steps.get(ant.getRandom().nextInt(steps.size())), MILL_PACE);
        }
    }

    /** Raid flow: a waiting raider does something small every {@link #MILL_LEAST} to that plus {@link #MILL_MORE} ticks, a shuffle at this pace (my picks). */
    private static final int MILL_LEAST = 20;
    private static final int MILL_MORE = 40;
    private static final double MILL_PACE = 0.5;

    private void endCleaning() {
        if (cleaningUntil != 0) {
            cleaningUntil = 0;
            byte now = ant.getActivity();
            if (now == AbstractAntEntity.ACTIVITY_GROOM_L || now == AbstractAntEntity.ACTIVITY_GROOM_R || now == AbstractAntEntity.ACTIVITY_GROOM_BOTH) {
                ant.setActivity(AbstractAntEntity.ACTIVITY_NONE);
            }
        }
    }

    /**
     * Straight to its place when near, else a hop of {@link #HOP} blocks its way: along the raid's trail when it and its
     * place both lie by the trail (the raid code review, finding 17: a hop straight across cut the trail's bends), else
     * straight; false if no way was found.
     */
    private boolean walkToward(ServerLevel level, BlockPos to, double pace) {
        BlockPos from = ant.blockPosition();
        boolean near = flat(from, to) <= HOP;
        BlockPos along = near ? null : column.hopAlong(level, from, to, HOP);
        if (along != null && ant.getNavigator().moveTo(along, pace)) {
            walkingTo = to;
            return true;
        }
        BlockPos next = near ? to : RaidGround.hopCell(level, from, to, HOP, 0.0);
        if (next != null && ant.getNavigator().moveTo(next, pace)) {
            walkingTo = to;
            return true;
        }
        return false;
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
