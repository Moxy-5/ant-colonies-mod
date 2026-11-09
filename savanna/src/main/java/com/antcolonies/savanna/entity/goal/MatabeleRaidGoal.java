package com.antcolonies.savanna.entity.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.RaidGround;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 3b, a raider walking in its colony's raid column ({@link RaidColumn}). The column (the colony's brain) says
 * where this ant should be now: round the entrance while the raid forms, its place in the line on the march, bunched
 * up short of the termites, home again after the fight. This goal only walks there through the ant's navigator, hop by
 * hop for a far place, and faces where the column is heading. The fight itself is {@code FightGoal}'s: the column gives
 * each raider a fight order at the attack, and this goal stands aside while one holds. Step 3c: after the fight the
 * column gives each raider with room a stung termite to fetch ({@code take}): it walks to it and picks it up; and a
 * raider that left the column hurt (crippled, a soldier clamped on a leg) limps home along the raid's trail with this
 * goal, the colony's raids saying where ({@code Raids.wayHomeOrder}); so does every ant the raid code lets go out in the field (the raid code review, finding 2). Mind layer only.
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
    /** BUGS 140: going home alone along a trail, after this many tries in a row with no way it leaves the trail (my pick: 3, 15 s). */
    private static final int LEAVE_TRAIL_AFTER = 3;

    private final MatabeleEntity ant;
    @Nullable
    private RaidColumn column;
    @Nullable
    private BlockPos walkingTo;
    private int waitUntil;
    /** Step 3c: whether it limps home along a raid's trail (else it walks in its column), and whether it stands picking a termite up. */
    private boolean limping;
    private boolean picking;
    /** BUGS 140: going home alone, the tries in a row that found no way (after {@link #LEAVE_TRAIL_AFTER}, it leaves the trail). */
    private int limpFails;
    /**
     * BUGS 141: where it was when it last made headway on a walk, and the tick (none while not walking). Walking that long
     * without getting anywhere (its place inside the refuse heap by the entrance, a step it cannot take), it stops and
     * stands where it is a while: the column's own stuck check looks only at raiders sent far, so one sent a block or two
     * kept planning the same walk for ever.
     */
    @Nullable
    private Vec3 progressAt;
    private int progressSince;
    /** BUGS 141: ticks walking without headway before it stops (my pick: 8 s, past a doorway's peek and a crowd's shuffle). */
    private static final int NO_PROGRESS = 160;
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
    private RaidColumn myColumn() {
        Raids raids = raids();
        RaidColumn found = raids == null ? null : raids.column();
        return found != null && found.has(ant.getUUID()) && found.stage() != RaidColumn.Stage.DONE ? found : null;
    }

    /** Step 3c: whether it limps home from a raid by itself (hurt, out of the column). */
    private boolean limps() {
        Raids raids = raids();
        return raids != null && raids.isGoingHome(ant.getUUID());
    }

    @Override
    public boolean canUse() {
        if (ant.getFightSpot() != null || ant.isDowned() || !(ant.level() instanceof ServerLevel)) {
            return false;
        }
        column = ant.fightsNoMore() ? null : myColumn();
        limping = column == null && limps();
        return column != null || limping;
    }

    @Override
    public boolean canContinueToUse() {
        if (ant.getFightSpot() != null || ant.isDowned()) {
            return false;
        }
        return limping ? limps() : column != null && !ant.fightsNoMore() && column == myColumn();
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
        limpFails = 0;
        progressAt = null;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        ant.getNavigator().setClimbPace(1.0);
        endCleaning();
        column = null;
        limping = false;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        RaidColumn.Order order;
        if (limping) {
            Raids raids = raids();
            BlockPos to = raids == null ? null : raids.wayHomeOrder(level, ant);
            if (to == null) {
                return; // (home: it is let go, and goes in as any ant)
            }
            order = new RaidColumn.Order(to, 1.0, null);
        } else {
            order = column == null ? null : column.orderFor(level, ant);
        }
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
        if (gotNowhere()) {
            ant.getNavigator().stop();
            walkingTo = order.to();
            waitUntil = ant.tickCount + RETRY * 5;
            if (limping) {
                limpFails++;
            }
            return;
        }
        ant.getNavigator().setClimbPace(column != null && !limping
                && (column.stage() == RaidColumn.Stage.FORMING || column.stage() == RaidColumn.Stage.MARCHING)
                ? RaidColumn.MARCH_CLIMB : 1.0); // (the way out: up the nest's shaft and down the hump at about a walk)
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
        Entity prey = order.take() != null && column != null ? level.getEntity(order.take()) : null;
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
        boolean walked = walkToward(level, to, order.pace());
        if (limping) {
            limpFails = walked ? 0 : limpFails + 1;
            if (limpFails >= LEAVE_TRAIL_AFTER) {
                Raids raids = raids();
                if (raids != null && raids.leaveTrailHome(level, ant)) {
                    limpFails = 0;
                    return; // (the entrance next tick, hop by hop)
                }
            }
        }
        if (!walked) {
            // no way now: a little later (BUGS 76: never searched every tick); going home alone it tried five ways, so it
            // waits longer (B7: a stuck one costs a few searches every 5 s, and is folded in out of sight after minutes)
            waitUntil = ant.tickCount + (limping ? RETRY * 5 : RETRY);
            if (order.take() != null && column != null) {
                column.noWayTo(level, ant, order.take()); // (another may reach it, or this one later)
            }
        }
    }

    /**
     * The stream: marching, its place on the trail moves on with the column every few ticks, a little ahead of the ant. It never settles or mills there: it walks on, its route planned again once the place has moved
     * {@link #MARCH_MOVED} or its walk is over, and in between only its pace changes (hurrying to its place in the stream,
     * or walking on slower to let the column come up to it).
     */
    private void march(ServerLevel level, RaidColumn.Order order) {
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
     * takes it from where it stands, within {@link RaidColumn#PICK_REACH} as before real sizes.
     */
    private int atPrey(LivingEntity prey) {
        if (ant.reachesInFight(prey)) {
            return AT_PREY;
        }
        if (prey.distanceTo(ant) > RaidColumn.PICK_REACH) {
            return NOT_AT_PREY;
        }
        AbstractAntEntity.CloseIn step = ant.closeIn(prey.position(), RaidColumn.PICK_REACH);
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
     * Step 3Q, Q5: standing at its place, now and then it cleans an antenna (its species' {@code body_language.groom}
     * chance, a restless one more often, asked once a second); never with jaws full, hurt or off the ground.
     */
    private void maybeClean() {
        if (cleaningUntil != 0 || ant.tickCount % 20 != (ant.getId() + 8) % 20 || !ant.onGround() || ant.hasFullMandibles()
                || ant.hurtTime > 0 || ant.isInWater()) {
            return;
        }
        SpeciesProfile.Groom groom = ant.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::groom).orElse(null);
        if (groom == null || ant.getRandom().nextFloat() >= groom.chancePerSecond() * (0.5F + ant.restlessness())) {
            return;
        }
        boolean both = ant.getRandom().nextFloat() < groom.bothShare();
        ant.setActivity(both ? AbstractAntEntity.ACTIVITY_GROOM_BOTH
                : ant.getRandom().nextBoolean() ? AbstractAntEntity.ACTIVITY_GROOM_L : AbstractAntEntity.ACTIVITY_GROOM_R);
        cleaningUntil = ant.tickCount + (both ? AbstractAntEntity.GROOM_BOTH_TICKS : AbstractAntEntity.GROOM_ONE_TICKS)
                + AbstractAntEntity.GROOM_SETTLE_TICKS + 2;
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
     * Straight to its place when near, else a hop of {@link #HOP} blocks its way: along the column's trail when it and
     * its place both lie by the trail (the raid code review, finding 17: a hop straight across cut the trail's bends),
     * else straight; false if no way was found.
     */
    private boolean walkToward(ServerLevel level, BlockPos to, double pace) {
        BlockPos from = ant.blockPosition();
        double distance = flat(from, to);
        if (distance <= HOP) {
            if (ant.getNavigator().moveTo(to, pace)) {
                walkingTo = to;
                return true;
            }
            // BUGS 140: a near place with no way to it was given up at once, even going home alone; such an ant now
            // tries the slanting hops below too (a scout stood 60 s and more by a ravine, its trail's next place below it).
            if (!limping) {
                return false;
            }
        } else {
            BlockPos along = column == null ? null : column.hopAlong(level, from, to, HOP);
            if (along != null && ant.getNavigator().moveTo(along, pace)) {
                walkingTo = to;
                return true;
            }
            if (hopToward(level, from, to, HOP, 0.0, pace)) {
                return true;
            }
        }
        // B7 (BUGS 121): the straight hop has no way (a cliff, a pond, a thorn tree's trunk in the way): it tries a shorter
        // hop, then hops slanting off to either side, before it waits and tries again. A scout far from home stood for
        // hours where every straight hop home failed. (Only after the straight hop failed: no cost on open ground; and only
        // for an ant going home by itself, so a column's many raiders never run five searches each.)
        if (!limping) {
            return false;
        }
        for (double turn : SIDE_TURNS) {
            if (hopToward(level, from, to, turn == 0.0 ? HOP / 2.0 : HOP * 0.7, turn, pace)) {
                return true;
            }
        }
        // BUGS 140, again: on a ravine's lip by a lake every hop's landing lay in the water, over the drop or inside the
        // hill, so no route was ever asked for and the scout stood all night. Last, the legs themselves look for any
        // floor some blocks nearer home (searching toward it first): round the lake, over the hill, whatever way there is.
        double nearer = Math.max(2.0, Math.min(HOP / 2.0, distance - AT));
        if (ant.getNavigator().moveTo(hold -> hold.isFloor() && flat(hold.cell(), to) <= distance - nearer, to, pace)) {
            walkingTo = to;
            return true;
        }
        return false;
    }

    /** B7: the turns (radians) a blocked hop tries, after a half hop straight: 45 degrees, then 90, each side. */
    private static final double[] SIDE_TURNS = {0.0, Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2};
    /** BUGS 121: the heights round the ant's own a hop's landing is looked for at, after the guess (each ±3 more by standAt). */
    private static final int[] OWN_HEIGHTS = {0, 6, -6};

    /** A hop of {@code length} blocks from {@code from} toward {@code to}, turned by {@code turn} radians; false if no way. */
    private boolean hopToward(ServerLevel level, BlockPos from, BlockPos to, double length, double turn, double pace) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-3) {
            return false;
        }
        double share = Math.min(1.0, length / distance);
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double x = from.getX() + 0.5 + (dx * cos - dz * sin) * share;
        double z = from.getZ() + 0.5 + (dx * sin + dz * cos) * share;
        int y = Mth.floor(Mth.lerp(share, from.getY(), to.getY()));
        BlockPos hop = RaidGround.standAt(level, x, z, y);
        // BUGS 121: the height guessed from the way's two ends missed the ground on a hillside (the scout on a ravine's
        // lip 10 blocks above its nest: every hop's guess lay inside the hill beside it or in the air over the ravine);
        // the ant's own height, and a little over and under it, are tried too.
        for (int dy : OWN_HEIGHTS) {
            if (hop != null) {
                break;
            }
            if (from.getY() + dy != y) {
                hop = RaidGround.standAt(level, x, z, from.getY() + dy);
            }
        }
        if (hop != null && ant.getNavigator().moveTo(hop, pace)) {
            walkingTo = to;
            return true;
        }
        return false;
    }

    /** BUGS 141: whether it has walked {@link #NO_PROGRESS} ticks without moving half a block (waiting for the crowd aside). */
    private boolean gotNowhere() {
        Vec3 here = ant.position();
        if (ant.getNavigator().isDone() || ant.getNavigator().heldForCrowd() || progressAt == null || here.distanceToSqr(progressAt) > 0.25) {
            progressAt = ant.getNavigator().isDone() ? null : here;
            progressSince = ant.tickCount;
            return false;
        }
        if (ant.tickCount - progressSince < NO_PROGRESS) {
            return false;
        }
        progressAt = null;
        return true;
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
