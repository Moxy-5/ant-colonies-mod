package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Carries the night soil up (brain step 4b): the soil dug while the surface was shut waits in a heap just under the
 * entrance (see {@code Hill.addNightSoil}); once the clock opens, builders pick it up there one pellet at a time, and
 * {@link CarryPelletGoal} takes each out onto the hill like any other pellet. Like {@link RepairHillGoal}.
 */
public class CarryNightSoilGoal extends Goal {
    /** Ticks the ant takes to pick a pellet off the heap. */
    private static final int PICK_TICKS = 10;
    /** If the ant can't get to the heap within 20 seconds it gives up (another ant, or later, will try). */
    private static final int GIVE_UP_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos heap;
    private int ticks;
    private int picking;

    public CarryNightSoilGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || !ant.canPlaceBlocks() || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0
                || !ant.mayDo(ColonyRole.BUILDER) || !ant.mayGoOut() || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.hill().needsNightSoilCarried(level)) {
            return false;
        }
        heap = colony.hill().getRepairSoilSpot();
        colony.hill().claimNightSoil(level, ant.getUUID());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && heap != null && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS && ant.mayGoOut();
    }

    @Override
    public void start() {
        ticks = 0;
        picking = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        heap = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (heap == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        Vec3 spot = Vec3.atCenterOf(heap);
        ant.getLookControl().setLookAt(spot);
        double dx = ant.getX() - spot.x;
        double dz = ant.getZ() - spot.z;
        if (dx * dx + dz * dz > 1.3 * 1.3 || Math.abs(ant.getY() - heap.getY()) >= 2.0) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(heap, 1.0)) {
                ticks = GIVE_UP_TICKS; // no way to the heap: let another ant have a go
            }
            return;
        }
        ant.getNavigator().stop();
        if (++picking >= PICK_TICKS) {
            BlockState pellet = colony.hill().takeNightSoil(ant.getUUID());
            if (pellet != null) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pellet), spot.x, spot.y, spot.z, 4, 0.2, 0.2, 0.2, 0.05);
                level.playSound(null, heap, pellet.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.3F);
                ant.setCarriedBlock(pellet);
                ColonyManager.get(level).markChanged();
            }
            heap = null;
        }
    }
}
