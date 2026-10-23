package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.LevelCellMap;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Noticing a player (the animation engine, step 6, 2026-10-16). Mind only: it says "look at that", "hold still" or
 * "go to this cell"; the legs walk, and the body's clips and the head look (client) do the rest.
 * <ul>
 * <li><b>Ants see</b> (the user's decision: movement only). An ant with nothing to do, in plain view of a player who
 * moves within its profile's range, may notice (more often the more curious it is): it stops, stands taller and looks,
 * its head following the player. Then by its personality (the user's decision): a bold one walks up to about a block
 * off and looks again; a shy one backs off a couple of blocks, jaws open; most just look. Once the player has stood still
 * for a few seconds, or its look time is over, it goes on, and for a while takes no notice of players (it has got used
 * to a harmless thing).</li>
 * <li><b>Termites feel</b> (the user's decision: Macrotermes workers are blind). A player walking near, or jumping,
 * landing or sprinting further off, makes an idle worker freeze with its antennae raised for a second or two. It never
 * turns to look.</li>
 * </ul>
 * Only an insect with nothing to do notices (this goal comes after every job), never one carrying something, hurt,
 * asleep or in an alarm. Numbers in the profile's {@code body_language.notice}; personality from the insect's traits.
 */
public class NoticeGoal extends Goal {
    /** A player who moved less than this (blocks) since the last look stood still. */
    private static final double MOVED = 0.2;
    /** How long (ticks) it looks before a bold one comes closer, and a shy one before it backs off. */
    private static final int LOOK_BEFORE_COMING = 30;
    private static final int LOOK_BEFORE_BACKING = 12;
    /** How fast it walks closer (times its normal speed: slowly, carefully) and backs off (quickly). */
    private static final double CLOSER_SPEED = 0.6;
    private static final double BACK_OFF_SPEED = 1.0;

    private enum Step { LOOK, CLOSER, BACK_OFF, FREEZE }

    private final AbstractAntEntity insect;
    @Nullable
    private Player player;
    private Step step = Step.LOOK;
    private boolean wary;
    private int ticksLeft;
    private int looked;
    private boolean movedYet;
    private int stillTicks;
    private Vec3 playerWasAt = Vec3.ZERO;
    /** Before this game time it takes no notice (it has just looked: it got used to the player for a while). */
    private long restUntil = Long.MIN_VALUE;

    /** Where each player near was at its last check, and when, to tell who moved. */
    private record Seen(Vec3 at, long time) {
    }

    private final Map<UUID, Seen> seen = new HashMap<>();

    public NoticeGoal(AbstractAntEntity insect) {
        this.insect = insect;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private SpeciesProfile.Notice notice() {
        return insect.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::notice).orElse(null);
    }

    @Override
    public boolean canUse() {
        if (insect.tickCount % 20 != insect.getId() % 20 || !(insect.level() instanceof ServerLevel level)) {
            return false; // (asked once a second)
        }
        SpeciesProfile.Notice notice = notice();
        if (notice == null) {
            return false;
        }
        Player moving = movingPlayer(level, notice);
        if (moving == null || level.getGameTime() < restUntil || !mayStop(level) || !insect.onGround() || insect.isClimbing()) {
            return false;
        }
        float chance = insect.noticeChanceForTests() >= 0.0F ? insect.noticeChanceForTests() : notice.chanceFor(insect.curiosity());
        if (insect.getRandom().nextFloat() >= chance) {
            return false;
        }
        player = moving;
        return true;
    }

    /**
     * The nearest player it can tell is moving: by sight one in plain view who moved since the last check, within the
     * range; by feel one walking within the range (not sneaking: soft steps), or jumping, landing or sprinting within the
     * shake range. Remembers where every player near is, for the next check.
     */
    @Nullable
    private Player movingPlayer(ServerLevel level, SpeciesProfile.Notice notice) {
        long now = level.getGameTime();
        double reach = notice.byVibration() ? Math.max(notice.range(), notice.shakeRange()) : notice.range();
        Player found = null;
        double best = Double.MAX_VALUE;
        for (Player near : insect.playersToNotice()) {
            double distance = near.distanceTo(insect);
            if (near.isSpectator() || !near.isAlive() || distance > reach + 2.0) {
                continue;
            }
            Seen before = seen.put(near.getUUID(), new Seen(near.position(), now));
            boolean moved = before != null && now - before.time() <= 30 && before.at().distanceToSqr(near.position()) > MOVED * MOVED;
            boolean noticed;
            if (notice.byVibration()) {
                boolean hard = GroundShakes.hardSince(near, now - 20) || near.isSprinting() && near.onGround() && moved;
                noticed = hard ? distance <= notice.shakeRange()
                        : moved && near.onGround() && !near.isShiftKeyDown() && distance <= notice.range();
            } else {
                noticed = moved && distance <= notice.range() && insect.hasLineOfSight(near);
            }
            if (noticed && distance < best) {
                best = distance;
                found = near;
            }
        }
        if (seen.size() > 16) {
            seen.values().removeIf(s -> now - s.time() > 100);
        }
        return found;
    }

    /** Whether it may stop for a player now: empty jaws, unhurt, not swimming, and no alarm in its colony. */
    private boolean mayStop(ServerLevel level) {
        if (insect.hasFullMandibles() || insect.hurtTime > 0 || insect.isInWater() || insect.isAsleep()) {
            return false;
        }
        Colony colony = insect.getColony();
        return colony == null || !colony.isAlarmed(level);
    }

    @Override
    public void start() {
        SpeciesProfile.Notice notice = notice();
        insect.getNavigator().stop();
        float curiosity = insect.curiosity();
        ticksLeft = notice == null ? 20 : Math.max(20, Math.round(notice.lookTicksFor(curiosity) * (0.85F + 0.3F * insect.getRandom().nextFloat())));
        looked = 0;
        movedYet = false;
        stillTicks = 0;
        playerWasAt = player.position();
        if (notice != null && notice.byVibration()) {
            step = Step.FREEZE;
            insect.setNoticing(AbstractAntEntity.NOTICE_FREEZE);
            return;
        }
        step = Step.LOOK;
        wary = notice != null && insect.boldness() < notice.shyBelow();
        insect.setNoticing(wary ? AbstractAntEntity.NOTICE_WARY : AbstractAntEntity.NOTICE_LOOK);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; // it counts its time in ticks (the game ticks other goals only every other tick)
    }

    @Override
    public boolean canContinueToUse() {
        SpeciesProfile.Notice notice = notice();
        if (notice == null || player == null || !player.isAlive() || player.isSpectator() || player.level() != insect.level()
                || ticksLeft <= 0 || !(insect.level() instanceof ServerLevel level) || !mayStop(level)) {
            return false;
        }
        if (step == Step.FREEZE) {
            return true; // a fixed time, whatever the player does
        }
        double reach = notice.range() + 3.0;
        return stillTicks < Math.max(1, notice.forgetTicks()) && insect.distanceToSqr(player) <= reach * reach
                && !(step != Step.LOOK && insect.getNavigator().hasFailed());
    }

    @Override
    public void tick() {
        ticksLeft--;
        if (step == Step.FREEZE || player == null) {
            return; // a termite never turns to look
        }
        insect.getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ(), 10.0F, insect.getMaxHeadXRot());
        if (player.position().distanceToSqr(playerWasAt) > 0.0025) {
            stillTicks = 0;
            playerWasAt = player.position();
        } else {
            stillTicks++;
        }
        looked++;
        SpeciesProfile.Notice notice = notice();
        if (!movedYet && notice != null) {
            if (wary && looked >= LOOK_BEFORE_BACKING) {
                movedYet = true;
                backOff(notice);
            } else if (!wary && looked >= LOOK_BEFORE_COMING && insect.boldness() >= notice.boldFrom()) {
                movedYet = true;
                comeCloser(notice);
            }
        }
        if (step != Step.LOOK && insect.getNavigator().isDone()) {
            step = Step.LOOK; // there: it stands and looks again
        }
    }

    /** A bold one walks up to about {@code come_within} blocks of the player, on the side it is on. */
    private void comeCloser(SpeciesProfile.Notice notice) {
        Vec3 from = player.position();
        Vec3 side = insect.position().subtract(from).multiply(1.0, 0.0, 1.0);
        if (side.length() <= notice.comeWithin() + 1.0) {
            return; // close enough already
        }
        Vec3 spot = from.add(side.normalize().scale(notice.comeWithin() + 0.5));
        walkTo(BlockPos.containing(spot.x, insect.getY(), spot.z), CLOSER_SPEED, Step.CLOSER);
    }

    /** A shy one backs off {@code back_off} blocks, away from the player. */
    private void backOff(SpeciesProfile.Notice notice) {
        Vec3 away = insect.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 spot = insect.position().add(away.normalize().scale(notice.backOff()));
        walkTo(BlockPos.containing(spot), BACK_OFF_SPEED, Step.BACK_OFF);
    }

    private void walkTo(BlockPos column, double speed, Step walking) {
        BlockPos cell = AntWanderGoal.findFloor(new LevelCellMap(insect.level(), insect), column);
        if (cell != null && insect.mayBeAt(cell) && insect.getNavigator().moveTo(cell, speed)) {
            step = walking;
        }
    }

    @Override
    public void stop() {
        insect.setNoticing(AbstractAntEntity.NOTICE_NONE);
        insect.getNavigator().stop();
        SpeciesProfile.Notice notice = notice();
        restUntil = insect.level().getGameTime() + (notice == null ? 0 : notice.restTicks());
        player = null;
    }

    /** Tests only: whether it is walking closer to the player, backing off, or neither. */
    public String stepForTests() {
        return step.name().toLowerCase(java.util.Locale.ROOT);
    }
}
