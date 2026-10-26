package com.antcolonies.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.fight.FightNumbers;
import com.antcolonies.fight.Fights;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

/**
 * The shared fight (M8 step 2b, {@code docs/plans/m8-matabele.md}): an insect sent to fight round a spot (the skirmish
 * command; step 4's raids later) goes there and attacks the foes it may attack within {@link #SPOT_REACH} of it (by the
 * shared rules, {@link Fights}), nearest first; one that was hurt fights back at whoever hurt it, if it may attack
 * that. At a foe it can reach it strikes again and again ({@link AbstractAntEntity#strike}: on prey, the paralysing
 * sting); one it cannot reach (in a closed gallery, under mud) it may open the way to
 * ({@link AbstractAntEntity#openWayTo}: a Matabele major bites the mud open), else it leaves it for a while. With no foe
 * left round the spot for {@link #DONE_TICKS} the order ends and it goes back to its usual work (home, for one with a
 * colony). The legs walk it ({@code AntNavigator}); this goal only says where.
 *
 * <p>M8 fight polish, round A: one foe at a time, each blow finished. It strikes only standing on a floor
 * ({@link AbstractAntEntity#standsToFight}: never from a wall or a ceiling) and facing the foe; the blow's clip plays to
 * its end ({@link AbstractAntEntity#isInABlow}) while it holds still, and a foe it has felled it stands over a moment,
 * feeling it with its antennae (the species file's {@code fight.look_over_ticks}), before it turns to the next. A foe
 * another hunter holds in its jaws is left to that one.
 *
 * <p>Round B: with no foe in reach it searches round the spot, antennae down, walking from place to place within
 * {@link #SEARCH_REACH} blocks of it, for its species' {@code fight.give_up_ticks} before the fight is over (real
 * Megaponera search the opened galleries and the ground round a feeding site for many minutes); a foe in its own nest,
 * past its feeding ways ({@link AbstractAntEntity#isSheltered}), is left alone: raids hit feeding sites, never the mound;
 * one it chases for {@link #CHASE_TICKS} without a blow at it is left a while.
 *
 * <p>Step 2g (the user's report of 2026-10-25, "they need to be smarter"): real Megaponera find termites by smell, not
 * sight, and the majors break the galleries open while the minors go in. An insect foe shut in under cover
 * ({@link Fights#isShutIn}: under a log, mud or soil) is not chased (no walk planned that cannot be found); a breaker
 * ({@link AbstractAntEntity#breaksIn}) leaves every foe under cover alone unless it can already strike it, and with no
 * foe it opens the way in ({@link AbstractAntEntity#breakIn}); the others search where they smell prey
 * ({@link AbstractAntEntity#scentNear}) first. A foe is kept until it is caught, lost or a much nearer one turns up,
 * and the search walks on from place to place without stopping dead.
 */
public class FightGoal extends Goal {
    /** How far (blocks) round the spot it looks for foes, unless its order says (a defender's, M8 step 2e). */
    public static final double SPOT_REACH = 12.0;
    /** How far (blocks) it follows one that hurt it, unless made with its own reach. */
    private static final double FIGHT_BACK_REACH = 8.0;
    /** How far off (degrees) its heading may be from the foe for it to strike: it turns to face it first. */
    private static final float FACING = 30.0F;
    /** A new foe is taken over the one it is after only if it is this much nearer (as a share of the squared distance). */
    private static final double MUCH_NEARER = 0.25;
    /**
     * At one search place in this many it stops a moment, feeling about ({@link #SEARCH_PAUSE} ticks at most, my picks);
     * at the others it walks straight on to the next, choosing it just before it arrives (it stopped dead at every one).
     */
    private static final int PAUSE_ONE_IN = 3;
    private static final int SEARCH_PAUSE = 20;
    /** A foe it found no way to is left alone this long (ticks). */
    private static final int UNREACHABLE_TICKS = 200;
    /** How far (blocks) from the spot it searches when no foe is in reach. */
    private static final int SEARCH_REACH = 4;
    /** How often (ticks) it picks a new place to search. */
    private static final int SEARCH_EVERY = 60;
    /** Chasing one foe this long (ticks) without a blow at it, it leaves it a while ({@link #UNREACHABLE_TICKS}). */
    private static final int CHASE_TICKS = 200;
    /** How fast (degrees a tick) it turns to face its foe. */
    private static final float TURN = 20.0F;

    private final AbstractAntEntity ant;
    /** How far (blocks) it follows one that hurt it. */
    private final double fightBackReach;
    @Nullable
    private LivingEntity foe;
    @Nullable
    private BlockPos walkingTo;
    /** The foe of its last blow (to stand over it once felled), and how long it still stands over one. */
    @Nullable
    private LivingEntity struck;
    private int lookingOver;
    private int idleTicks;
    /** Whether its walking is the search round the spot (not a chase), and the idle tick it picks its next walk at. */
    private boolean searching;
    private int nextStep;
    /** Whether it stops a moment at the place it is walking to before the next (see {@link #PAUSE_ONE_IN}). */
    private boolean pauseNext;
    /** The foe it is chasing and for how long, without a blow at it. */
    @Nullable
    private LivingEntity chasing;
    private int chaseTicks;
    /** Foes it found no way to, and until when it leaves each alone. */
    private final Map<UUID, Long> unreachable = new HashMap<>();

    public FightGoal(AbstractAntEntity ant) {
        this(ant, FIGHT_BACK_REACH);
    }

    /** With its own reach for fighting back (M8 step 2e: the Matabele queen, who never leaves her room). */
    public FightGoal(AbstractAntEntity ant, double fightBackReach) {
        this.ant = ant;
        this.fightBackReach = fightBackReach;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % 5 != 0 || !(ant.level() instanceof ServerLevel level) || ant.fightsNoMore()) {
            return false; // with 3 or more legs gone (M8 step 2d), or a termite clamped on (2c), it fights no more
        }
        foe = pickFoe(level);
        return foe != null || ant.getFightSpot() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.fightsNoMore() && (foe != null && foe.isAlive() || ant.getFightSpot() != null || ant.isInABlow() || lookingOver > 0);
    }

    @Override
    public void start() {
        walkingTo = null;
        idleTicks = 0;
        searching = false;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        foe = null;
        struck = null;
        lookingOver = 0;
        walkingTo = null;
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level)) {
            return;
        }
        // A blow under way plays to its end; it holds still, facing its foe.
        if (ant.isInABlow()) {
            ant.getNavigator().stop();
            LivingEntity target = ant.blowTarget();
            if (target != null) {
                face(target.getX(), target.getZ());
            }
            return;
        }
        // After the blow: a foe it felled it stands over a moment, its antennae on it, before it turns to the next.
        if (struck != null) {
            if (struck instanceof AbstractAntEntity prey && prey.isDowned()) {
                lookingOver = FightNumbers.of(ant.profile()).map(numbers -> numbers.lookOverTicks(ant.getRandom())).orElse(0);
            }
            if (lookingOver > 0) {
                ant.getLookControl().setLookAt(struck);
            }
            struck = null;
        }
        if (lookingOver > 0) {
            lookingOver--;
            ant.getNavigator().stop();
            return;
        }
        if (foe == null || !Fights.mayAttack(ant, foe) || (ant.tickCount + ant.getId()) % 20 == 0) {
            LivingEntity was = foe;
            LivingEntity next = pickFoe(level);
            // Kept until caught or lost: a new one only if it is much nearer (it turned from one to another every second).
            boolean keep = was != null && next != null && next != was && was.isAlive() && usable(was) && Fights.mayAttack(ant, was)
                    && worthChasing(level, was) && next.distanceToSqr(ant) > MUCH_NEARER * was.distanceToSqr(ant);
            foe = keep ? was : next;
        }
        if (foe != null) {
            idleTicks = 0;
            searching = false;
            fight(level, foe);
            return;
        }
        BlockPos spot = ant.getFightSpot();
        if (spot == null) {
            return;
        }
        // No foe in reach: a breaker opens the way in to the prey it smells (step 2g); the rest go to the spot, and once
        // there search round it a while for more before the fight is over. The spot is often the food itself (a hay
        // bale, dead wood): it walks to any foothold near it, never into it (the user's bug of 2026-10-25: ants far off
        // planned a way into the solid spot every tick, found none and stood in a corner till a termite came by). With
        // no way to the spot it searches round where it is, and tries again later.
        if (ant.breaksIn() && ant.breakIn(level, spot, ant.getFightReach())) {
            searching = false;
            idleTicks = 0; // breaking in is work at the spot, not an empty search
            return;
        }
        if (!searching) {
            searching = true; // (no stop: it walks on from where it was going)
            nextStep = idleTicks;
        }
        boolean walkOn = !pauseNext && walkingTo != null && stepsLeft() <= 1;
        if ((ant.getNavigator().isDone() || walkOn) && idleTicks >= nextStep) {
            boolean far = ant.blockPosition().distSqr(spot) > (SEARCH_REACH + 2) * (SEARCH_REACH + 2);
            if (far && ant.getNavigator().moveTo(at -> at.cell().closerThan(spot, SEARCH_REACH), spot, SEARCH_REACH, 1.2)) {
                walkingTo = spot;
            } else if (!far && searchScent(level, spot)) {
                nextStep = idleTicks; // walking to where it smells prey
            } else {
                // Each search step goes somewhere new (never a place it already stands at: it kept "searching" on the
                // spot, the same bug report).
                BlockPos here = ant.blockPosition();
                BlockPos round = far ? here : spot;
                // Mostly ahead of it, a meander rather than a zigzag: a place behind it means turning round on the spot.
                BlockPos pick = round;
                double bestAhead = -2.0;
                for (int tries = 0; tries < 8; tries++) {
                    BlockPos next = round.offset(ant.getRandom().nextInt(SEARCH_REACH * 2 + 1) - SEARCH_REACH, 0,
                            ant.getRandom().nextInt(SEARCH_REACH * 2 + 1) - SEARCH_REACH);
                    if (next.distManhattan(here) < 3) {
                        continue;
                    }
                    double ahead = ahead(next);
                    if (ahead > bestAhead) {
                        bestAhead = ahead;
                        pick = next;
                    }
                    if (ahead > 0.5) {
                        break;
                    }
                }
                BlockPos look = pick;
                walkingTo = look;
                if (!ant.getNavigator().moveTo(at -> at.cell().distManhattan(look) <= 2 && !at.cell().equals(here), look, 2, 0.8)) {
                    nextStep = idleTicks + SEARCH_EVERY; // no way there: it feels about here a while before trying again
                }
            }
            pauseNext = ant.getRandom().nextInt(PAUSE_ONE_IN) == 0;
            if (nextStep <= idleTicks && pauseNext) {
                nextStep = idleTicks + ant.getRandom().nextInt(SEARCH_PAUSE + 1); // a short feel about, then on
            }
        }
        int giveUp = FightNumbers.of(ant.profile()).map(FightNumbers::giveUpTicks).orElse(FightNumbers.DEFAULT_GIVE_UP_TICKS);
        if (++idleTicks >= giveUp) {
            ant.endFightOrder();
        }
    }

    /** At the foe: strikes it now and then; otherwise goes at it, or opens the way to it, or leaves it for a while. */
    private void fight(ServerLevel level, LivingEntity target) {
        // Only from a floor (the user's rule): reaching it from a wall or a ceiling, it goes on to the floor first.
        if (target != chasing) {
            chasing = target;
            chaseTicks = 0;
        }
        if (ant.reachesInFight(target) && ant.standsToFight()) {
            ant.getNavigator().stop();
            walkingTo = null;
            if (face(target.getX(), target.getZ()) && ant.strike(target)) {
                struck = target;
                chaseTicks = 0;
            }
            return;
        }
        // A chase that gets nowhere (it keeps out of reach, in a hole too tight): left a while, the next foe instead.
        if (++chaseTicks > CHASE_TICKS) {
            unreachable.put(target.getUUID(), level.getGameTime() + UNREACHABLE_TICKS);
            foe = null;
            chasing = null;
            return;
        }
        BlockPos at = target.blockPosition();
        if (ant.getNavigator().isDone() || walkingTo == null || walkingTo.distSqr(at) > 2) {
            walkingTo = at;
            if (!ant.getNavigator().moveTo(at, 1.2) && !ant.openWayTo(level, target)) {
                ant.countChaseNotFound();
                unreachable.put(target.getUUID(), level.getGameTime() + UNREACHABLE_TICKS);
                foe = null;
            }
        }
    }

    /**
     * The foe to fight: the one that hurt it (if it may attack it and it is near), else the nearest it may attack round
     * its fight spot. Null for none.
     */
    @Nullable
    private LivingEntity pickFoe(ServerLevel level) {
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        LivingEntity hurtBy = ant.getLastHurtByMob();
        if (hurtBy != null && hurtBy.distanceToSqr(ant) <= fightBackReach * fightBackReach && usable(hurtBy)) {
            return hurtBy;
        }
        BlockPos spot = ant.getFightSpot();
        if (spot == null) {
            return null;
        }
        AABB area = new AABB(spot).inflate(ant.getFightReach());
        return level.getEntitiesOfClass(LivingEntity.class, area, candidate -> usable(candidate) && worthChasing(level, candidate)).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(ant))).orElse(null);
    }

    private boolean usable(LivingEntity candidate) {
        return candidate != ant && !unreachable.containsKey(candidate.getUUID()) && Fights.mayAttack(ant, candidate)
                && !(candidate instanceof AbstractAntEntity insect
                        && (insect.isPinned() && candidate != ant.blowTarget() || insect.isSheltered()));
    }

    /**
     * One search step to where it smells prey round the spot (step 2g: an opened gallery, a termite in it), not where it
     * stands; true if it is walking there.
     */
    private boolean searchScent(ServerLevel level, BlockPos spot) {
        List<BlockPos> scent = new ArrayList<>(ant.scentNear(level, spot, ant.getFightReach()));
        BlockPos here = ant.blockPosition();
        scent.removeIf(cell -> cell.distManhattan(here) <= 1);
        for (int tries = 0; tries < 3 && !scent.isEmpty(); tries++) {
            BlockPos smell = scent.remove(ant.getRandom().nextInt(scent.size()));
            if (ant.getNavigator().moveTo(at -> at.cell().distManhattan(smell) <= 1, smell, 1, 0.9)) {
                walkingTo = smell;
                return true;
            }
            scent.removeIf(cell -> cell.distManhattan(smell) <= 2); // (no way there: not its neighbours either)
        }
        return false;
    }

    /**
     * Whether it goes after this foe (step 2g): one it can strike now, anything not an insect, or an insect in the open;
     * an insect under cover only if it is not shut in, and never by a breaker, who opens the way in instead.
     */
    private boolean worthChasing(ServerLevel level, LivingEntity candidate) {
        if (!(candidate instanceof AbstractAntEntity) || ant.reachesInFight(candidate)) {
            return true;
        }
        BlockPos at = candidate.blockPosition();
        if (Fights.isInTheOpen(level, at)) {
            return true;
        }
        return !ant.breaksIn() && !Fights.isShutIn(level, at);
    }

    /** How far this place lies ahead of it: 1 straight ahead, 0 to the side, -1 behind. */
    private double ahead(BlockPos place) {
        double dx = place.getX() + 0.5 - ant.getX();
        double dz = place.getZ() + 0.5 - ant.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            return 0.0;
        }
        float yaw = ant.getYRot() * Mth.DEG_TO_RAD;
        return (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz) / length;
    }

    /** How many steps of its walk are left (0 with none). */
    private int stepsLeft() {
        var route = ant.getNavigator().getPlannedRoute();
        return route == null || ant.getNavigator().isDone() ? 0 : route.steps().size() - ant.getNavigator().getNextWaypoint();
    }

    /** Turns toward the point; true once it faces it (within {@link #FACING} degrees). */
    private boolean face(double x, double z) {
        double dx = x - ant.getX();
        double dz = z - ant.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return true;
        }
        float target = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(ant.getYRot(), target, TURN);
        ant.setYRot(yaw);
        ant.setYBodyRot(yaw);
        ant.setYHeadRot(yaw);
        return Math.abs(Mth.wrapDegrees(target - yaw)) <= FACING;
    }

}
