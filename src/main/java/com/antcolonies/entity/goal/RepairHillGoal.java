package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mends the ant hill: when a block of it has been knocked out, a builder climbs into the entrance shaft, scrapes up
 * a soil pellet there (no block of the nest is removed) and takes it in its mandibles. {@link CarryPelletGoal} then
 * carries it out and sets it into the gap, the same way as every pellet of the hill.
 */
public class RepairHillGoal extends Goal {
    /** Ticks the ant scrapes before it has its pellet. */
    private static final int SCRAPE_TICKS = 30;
    private static final int SCRAPE_INTERVAL = 10;
    /** If the ant can't get to the soil within 20 seconds it gives up (another ant, or later, will try). */
    private static final int GIVE_UP_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos soilSpot;
    private int ticks;
    private int scraped;

    public RepairHillGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.getColonyId() == null || !ant.canPlaceBlocks() || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0
                || !ant.mayDo(ColonyRole.BUILDER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.hill().needsHillRepair(level)) {
            return false;
        }
        soilSpot = colony.hill().getRepairSoilSpot();
        colony.hill().claimHillRepair(level, ant.getUUID());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && soilSpot != null && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        scraped = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        soilSpot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (soilSpot == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        Vec3 spot = Vec3.atCenterOf(soilSpot);
        ant.getLookControl().setLookAt(spot);

        if (!ant.blockPosition().equals(soilSpot)) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(soilSpot, 1.0)) {
                ticks = GIVE_UP_TICKS; // no way into the shaft: let another ant have a go
            }
            return;
        }

        ant.getNavigator().stop();
        if (++scraped % SCRAPE_INTERVAL == 0) {
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            level.playSound(null, soilSpot, dirt.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.3F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dirt),
                    spot.x, spot.y, spot.z, 4, 0.2, 0.2, 0.2, 0.05);
        }
        if (scraped >= SCRAPE_TICKS) {
            ant.setCarriedBlock(ModBlocks.BURROWED_DIRT.get().defaultBlockState());
        }
    }
}
