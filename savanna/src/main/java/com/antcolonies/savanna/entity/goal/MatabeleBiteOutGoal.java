package com.antcolonies.savanna.entity.goal;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.fight.Fights;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.RaidGround;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.registry.TreeBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * BUGS 102, the user's report of 2026-11-05: termite workers sealed Matabele in (a raid seal packed into a gallery, a
 * foraging hole plugged, a leak walled up), and the ants stood there for ever, their route searches failing. Real
 * Macrotermes do wall off their galleries in a raid, and a Megaponera cut off digs and bites its way out through the
 * fresh, soft soil. So: an ant out of its own nest that stays shut in ({@link Fights#isShutIn}) for a second finds the
 * block of its trap best to bite through ({@link MatabeleEntity#mayBiteOut}: soil, termite mud, a fresh plug; never the
 * mound), walks to stand by it and bites it open, then looks again, until it is out. Best is a block with the open on its
 * far side (up through a thin roof, or the plug itself), then the one nearest home. Mind layer only: it picks and asks
 * the legs to walk; the body bites.
 * <p>
 * Priority -1, ahead of the fight (priority 0): a fight goal holding an ant with a fight spot outside its trap kept it
 * searching for a way it could never find. It stands aside while a foe is right by it (a termite in the pocket with it:
 * the fight first).
 */
public class MatabeleBiteOutGoal extends Goal {
    /** How often it looks whether it is shut in (ticks), and how many looks in a row make it start (my pick: 1 s). */
    private static final int LOOK_EVERY = 10;
    private static final int LOOKS_TO_START = 2;
    /** How far its pocket is searched for a block to bite (cells; the same reach as the shut-in check). */
    private static final int POCKET = 40;
    /** A block it found no way to stand by is left this long (ticks). */
    private static final int NO_WAY_TICKS = 200;
    /** A foe this near (blocks) is fought first. */
    private static final double FOE_NEAR = 2.5;

    private final MatabeleEntity ant;
    private int looksShut;
    @Nullable
    private BlockPos block;
    @Nullable
    private BlockPos standAt;
    private final Map<BlockPos, Long> noWay = new HashMap<>();
    /**
     * BUGS 113: nothing it may bite found (a trap of hard soil, wood or stone, or a place only looking shut in): it lets
     * go until this game time, so its walk home or a climb can try, instead of standing there for ever.
     */
    private long letGoUntil;
    /** How long it lets go when it found nothing to bite (ticks; my pick, 30 s). */
    private static final int NOTHING_TO_BITE_WAIT = 600;

    public MatabeleBiteOutGoal(MatabeleEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((ant.tickCount + ant.getId()) % LOOK_EVERY != 0) {
            return false;
        }
        if (!(ant.level() instanceof ServerLevel level) || level.getGameTime() < letGoUntil || !trapped(level)) {
            looksShut = 0;
            return false;
        }
        return ++looksShut >= LOOKS_TO_START && !foeNear();
    }

    @Override
    public boolean canContinueToUse() {
        if (!(ant.level() instanceof ServerLevel level) || ant.isDowned() || foeNear() || level.getGameTime() < letGoUntil) {
            return false;
        }
        if ((ant.tickCount + ant.getId()) % LOOK_EVERY == 0 && !trapped(level)) {
            return false; // out
        }
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        block = null;
        standAt = null;
        SavannaMod.LOGGER.info("[raid] Matabele {} is shut in at {}: it bites its way out",
                RaidGround.shortId(ant.getUUID()), ant.blockPosition().toShortString());
    }

    @Override
    public void stop() {
        looksShut = 0;
        block = null;
        standAt = null;
        ant.getNavigator().stop();
    }

    @Override
    public void tick() {
        if (!(ant.level() instanceof ServerLevel level) || ant.isStriking()) {
            return; // (a bite's clip still plays)
        }
        long now = level.getGameTime();
        noWay.values().removeIf(until -> until <= now);
        if (block != null && (!MatabeleEntity.mayBiteOut(level, block) || noWay.containsKey(block))) {
            block = null; // bitten open (or no longer ours to bite)
            standAt = null;
        }
        if (block == null && !pick(level)) {
            letGoUntil = now + NOTHING_TO_BITE_WAIT; // (BUGS 113: no wait without a limit)
            SavannaMod.LOGGER.debug("[raid] Matabele {} at {} finds nothing it may bite: it lets go for {} s",
                    RaidGround.shortId(ant.getUUID()), ant.blockPosition().toShortString(), NOTHING_TO_BITE_WAIT / 20);
            return;
        }
        if (ant.inBiteReach(block) && ant.standsToFight()) {
            ant.getNavigator().stop();
            ant.biteOut(level, block);
            return;
        }
        if (ant.getNavigator().isDone()) {
            if (ant.blockPosition().equals(standAt) || !ant.getNavigator().moveTo(standAt, 1.0)) {
                SavannaMod.LOGGER.debug("[raid] Matabele {} finds no stand to bite {} from (at {}, stand {}, floor {})",
                        RaidGround.shortId(ant.getUUID()), block.toShortString(), ant.blockPosition().toShortString(), standAt.toShortString(), ant.standsToFight());
                noWay.put(block, now + NO_WAY_TICKS); // there and still not in reach on a floor, or no way: another block
                block = null;
                standAt = null;
            }
        }
    }

    /** Out of its own nest, under cover and shut in. */
    private boolean trapped(ServerLevel level) {
        if (ant.isDowned() || !ant.isAlive()) {
            return false;
        }
        BlockPos at = ant.blockPosition();
        if (Fights.isInTheOpen(level, at)) {
            return false;
        }
        Colony colony = ant.getColony();
        if (colony != null && colony.nest().isInside(level, at)) {
            return false; // its own rooms: home, not a trap
        }
        if (!roofed(level, at)) {
            return false; // BUGS 160: a roof far over it (a dead tree's bare limbs) is no trap
        }
        if (!Fights.isShutIn(level, at)) {
            return false;
        }
        // BUGS 185: under tree wood (a giant baobab's bulge, a limb) the open may lie past the quick 40-cell look, so
        // the ant looks wider before it believes it; a seal of soil or mud is judged as before
        return !woodOver(level, at) || Fights.isShutIn(level, at, SURE_SHUT_IN);
    }

    /** BUGS 185: whether the roof just over it is a tree's wood. */
    private static boolean woodOver(ServerLevel level, BlockPos at) {
        for (int up = 1; up <= ROOF_NEAR; up++) {
            if (level.getBlockState(at.above(up)).is(TreeBlocks.TREE_WOOD)) {
                return true;
            }
        }
        return false;
    }

    /** How many cells the wide look walks before an ant is sure it is shut in (BUGS 185; my pick). */
    private static final int SURE_SHUT_IN = 400;

    /** How near over it (blocks) a roof must be for the ant to be shut in (BUGS 160; my pick). */
    private static final int ROOF_NEAR = 3;

    /**
     * BUGS 160: whether something solid lies just over it. A seal of soil or mud is always right on top of a shut-in ant;
     * under a dead tree's bare crown 15 blocks up (no leaves, so BUGS 113's leaf rule did not help) the shut-in walk of
     * 40 cells never reached the open sky round the crown, and the ant bit at the grass every second.
     */
    private static boolean roofed(ServerLevel level, BlockPos at) {
        for (int up = 1; up <= ROOF_NEAR; up++) {
            BlockPos above = at.above(up);
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean foeNear() {
        LivingEntity foe = ant.getTarget();
        return foe != null && foe.isAlive() && foe.distanceTo(ant) <= FOE_NEAR;
    }

    /**
     * The block to bite: over its pocket's cells (the open cells it can reach), each block round them it may bite
     * through, judged by the straight way out behind it ({@link #wayOut}): the fewest blocks between it and the open
     * first (so it keeps digging one way, not a cave round itself), then nearness to home and to the ant. Up only
     * through a last block (it bites standing on a floor, so a deeper roof is dug round sideways); down never (it leads
     * deeper in).
     */
    private boolean pick(ServerLevel level) {
        Colony colony = ant.getColony();
        BlockPos home = colony == null ? null : colony.getCenter();
        BlockPos best = null;
        BlockPos bestStand = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos cell : pocket(level)) {
            if (!level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
                continue; // it bites standing on a floor (the user's rule)
            }
            for (Direction side : Direction.values()) {
                if (side == Direction.DOWN) {
                    continue;
                }
                BlockPos wall = cell.relative(side);
                if (noWay.containsKey(wall) || !MatabeleEntity.mayBiteOut(level, wall)) {
                    continue;
                }
                int blocks = wayOut(level, wall, side);
                if (blocks == Integer.MAX_VALUE || side == Direction.UP && blocks > 1) {
                    continue;
                }
                double score = blocks * 4.0 + (side == Direction.UP ? 0.0 : 0.5);
                if (home != null) {
                    score += 0.2 * Math.sqrt(wall.distSqr(home));
                }
                score += Math.sqrt(ant.distanceToSqr(Vec3.atCenterOf(cell)));
                if (score < bestScore) {
                    bestScore = score;
                    best = wall;
                    bestStand = cell;
                }
            }
        }
        if (best == null) {
            return false;
        }
        block = best.immutable();
        standAt = bestStand.immutable();
        SavannaMod.LOGGER.debug("[raid] Matabele {} at {} picks {} to bite out through, standing at {} (score {})",
                RaidGround.shortId(ant.getUUID()), ant.blockPosition().toShortString(), block.toShortString(), standAt.toShortString(), String.format(Locale.ROOT, "%.1f", bestScore));
        return true;
    }

    /** How far it looks along a way out (blocks). */
    private static final int LINE = 8;

    /**
     * How many blocks it would bite through going straight on from this wall: counted until an open cell that is in the
     * open or not shut in (out); a hollow still shut in counts 3 more, no end within {@link #LINE} blocks {@code LINE}
     * plus 5; a block it may not bite (the mound, stone, wood) on the way: no way ({@link Integer#MAX_VALUE}).
     */
    private static int wayOut(ServerLevel level, BlockPos wall, Direction side) {
        BlockPos at = wall;
        int blocks = 0;
        while (blocks < LINE) {
            if (MatabeleEntity.mayBiteOut(level, at)) {
                blocks++;
                at = at.relative(side);
                continue;
            }
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return Integer.MAX_VALUE;
            }
            return Fights.isInTheOpen(level, at) || !Fights.isShutIn(level, at) ? blocks : blocks + 3;
        }
        return LINE + 5;
    }

    /** The open cells round it it can get to without biting (its pocket), up to {@link #POCKET}. */
    private Set<BlockPos> pocket(ServerLevel level) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = ant.blockPosition().immutable();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < POCKET) {
            BlockPos at = queue.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!seen.contains(next) && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        return seen;
    }
}
