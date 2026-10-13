package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A termite shapes a chimney block (the hole block) where the mound needs one: the cap on top of a chimney shaft, or an
 * entrance at the foot of the mound (see {@link com.antcolonies.colony.Mound#pickHoleJob}). It walks next to the spot,
 * works the soil for a couple of seconds, and the chimney block is there.
 */
public class ShapeHoleGoal extends Goal {
    /** How long (in ticks) shaping a chimney block takes. */
    private static final int WORK_TICKS = 40;
    private static final int GIVE_UP_TICKS = 600;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int work;

    public ShapeHoleGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.hasFullMandibles() || ant.getColonyId() == null || ant.getRandom().nextInt(20) != 0
                || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.mound().isActive()) {
            return false;
        }
        spot = colony.mound().pickHoleJob(level, ant);
        return spot != null && ant.getNavigator().moveTo(BuildMoundGoal.workFrom(ant, colony, spot), spot, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        work = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.mound().releaseHoleJob(ant.getUUID());
        }
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (!colony.mound().isHoleJob(level, spot)) {
            spot = null; // done by someone else, or not wanted any more
            return;
        }
        if (!ant.getNavigator().isDone()) {
            return;
        }
        if (!BuildMoundGoal.isWithinReach(ant, spot)) {
            if (!ant.getNavigator().moveTo(BuildMoundGoal.workFrom(ant, colony, spot), spot, 1.0)) {
                spot = null;
            }
            return;
        }
        ant.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (++work % 10 == 0) {
            BlockState soil = ModBlocks.TERMITE_MOUND.get().defaultBlockState();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                    spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, spot, soil.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.3F, 1.3F);
        }
        if (work >= WORK_TICKS && colony.mound().shapeHole(level, spot)) {
            ColonyManager.get(level).markChanged();
            spot = null;
        }
    }
}
