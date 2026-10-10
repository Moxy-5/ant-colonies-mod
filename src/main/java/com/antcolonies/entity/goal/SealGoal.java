package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Packs openings full of soil. Every ant adds its share of the work to the same opening, so the more ants are
 * on it the sooner it is closed.
 *
 * <p>Two copies of this goal run in every worker. The calm one ({@code emergency == false}) walks to an opening in
 * the nest walls (a cave, a crack to the outside, a gap towards another room) between the other jobs. The emergency
 * one answers the colony's emergency signal (water or lava is about, or a room had to be given up): it comes before
 * everything else, wherever the ant is, hurries, and works faster. It shuts doors of rooms and blocks the source
 * of whatever is running towards the nest, climbing walls to get to it.
 */
public class SealGoal extends Goal {
    /** Ticks between two packs of soil, and the share of an opening one pack closes (an ant on its own needs four). */
    private static final int PACK_INTERVAL = 10;
    private static final int EMERGENCY_PACK_INTERVAL = 5;
    private static final float PACK_SHARE = 0.25F;
    /** If the ant can't finish within 20 seconds it gives up (another ant, or later, will try). */
    private static final int GIVE_UP_TICKS = 400;
    /** Walking speed in an emergency, as a multiple of the normal speed. */
    private static final double EMERGENCY_SPEED = 1.8;
    /** An opening this ant could not find a way to is left to the others for this long. */
    private static final int UNREACHABLE_TICKS = 200;

    private final AbstractAntEntity ant;
    private final boolean emergency;
    @Nullable
    private Colony colony;
    @Nullable
    private Colony.Leak leak;
    private int ticks;
    private int packCooldown;
    /** Openings this ant failed to reach: hole -> game time until which it leaves them alone. */
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    public SealGoal(AbstractAntEntity ant, boolean emergency) {
        this.ant = ant;
        this.emergency = emergency;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        if (emergency) {
            // Listens for the signal a few times a second; an ant does not wait to finish what it was doing.
            if ((ant.tickCount + ant.getId()) % 4 != 0) {
                return false;
            }
        } else if (ant.isCarrying() || ant.getRandom().nextInt(20) != 0) {
            // Only look for work now and then, so ants don't all think on the same tick.
            // An ant with a pellet in its mouth must carry it out first.
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || (emergency && !colony.isEmergency(level))) {
            return false;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        leak = emergency
                ? colony.findEmergencyTask(level, ant.blockPosition(), unreachable.keySet())
                : colony.findLeak(level, ant.blockPosition(), unreachable.keySet());
        if (leak == null) {
            return false;
        }
        colony.claimLeak(level, leak.hole(), ant.getUUID());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && leak != null && (emergency || !ant.isCarrying()) && ticks < GIVE_UP_TICKS
                && colony.isLeakOpen(ant.level(), leak);
    }

    @Override
    public void start() {
        ticks = 0;
        packCooldown = interval();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && leak != null) {
            colony.releaseLeak(leak.hole(), ant.getUUID());
        }
        leak = null;
        colony = null;
    }

    private int interval() {
        return emergency ? EMERGENCY_PACK_INTERVAL : PACK_INTERVAL;
    }

    @Override
    public void tick() {
        if (leak == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        BlockPos hole = leak.hole();
        ant.getLookControl().setLookAt(Vec3.atCenterOf(hole));

        // Close enough to reach it: in the block next to it (corners count) but not in it.
        BlockPos at = ant.blockPosition();
        boolean inReach = !at.equals(hole) && Math.abs(at.getX() - hole.getX()) <= 1
                && Math.abs(at.getY() - hole.getY()) <= 1 && Math.abs(at.getZ() - hole.getZ()) <= 1;
        if (!inReach) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(leak.stand(), emergency ? EMERGENCY_SPEED : 1.0)) {
                unreachable.put(hole.immutable(), level.getGameTime() + UNREACHABLE_TICKS);
                ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
            }
            return;
        }

        ant.getNavigator().stop();
        if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
            ant.getNavigator().cling(); // no floor here: hold on to the wall while working
        }
        if (--packCooldown > 0) {
            return;
        }
        packCooldown = interval();
        BlockState soil = ModBlocks.BURROWED_DIRT.get().defaultBlockState();
        level.playSound(null, hole, soil.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                hole.getX() + 0.5, hole.getY() + 0.5, hole.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
        if (colony.addSealProgress(hole, PACK_SHARE) >= 1.0F && colony.plugLeak(level, leak)) {
            level.playSound(null, hole, soil.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }
}
