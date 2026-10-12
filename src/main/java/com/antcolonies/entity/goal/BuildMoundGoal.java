package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A termite holding a soil pellet takes it to the mound and adds it as a layer to the next block being built (see
 * {@link com.antcolonies.colony.Mound#pickBuildSpot}). It works from any spot right next to the block: beside it, under
 * it in the shaft, or clinging to the mound's outside. With no spot free it keeps its pellet until one is.
 */
public class BuildMoundGoal extends Goal {
    /** If the termite has not put its soil down in this many ticks, it tries another spot. */
    private static final int GIVE_UP_TICKS = 600;
    /** After this many spots with no route to them, it waits a while before trying again. */
    private static final int MAX_FAILED_ROUTES = 4;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int failedRoutes;

    public BuildMoundGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Any foothold from which an insect can work on {@code block}: in one of the 26 cells round it. */
    public static Predicate<Foothold> withinReach(BlockPos block) {
        return at -> {
            BlockPos cell = at.cell();
            return !cell.equals(block) && Math.abs(cell.getX() - block.getX()) <= 1 && Math.abs(cell.getY() - block.getY()) <= 1
                    && Math.abs(cell.getZ() - block.getZ()) <= 1;
        };
    }

    /** Whether the insect is now in one of the 26 cells round {@code block}. */
    public static boolean isWithinReach(AbstractAntEntity ant, BlockPos block) {
        BlockPos cell = ant.blockPosition();
        return !cell.equals(block) && Math.abs(cell.getX() - block.getX()) <= 1 && Math.abs(cell.getY() - block.getY()) <= 1
                && Math.abs(cell.getZ() - block.getZ()) <= 1;
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarrying() || ant.getColonyId() == null || ant.getRandom().nextInt(10) != 0
                || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.mound().isActive()) {
            return false;
        }
        spot = colony.mound().pickBuildSpot(level, ant);
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && ant.isCarrying();
    }

    @Override
    public void start() {
        ticks = 0;
        failedRoutes = 0;
        walkToSpot();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.mound().releaseBuildSpot(ant.getUUID());
        }
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (++ticks >= GIVE_UP_TICKS) {
            AntColoniesMod.LOGGER.info("[mound] termite at {} gave up on spot {} (navigator done: {}, still a spot: {})",
                    ant.blockPosition().toShortString(), spot.toShortString(), ant.getNavigator().isDone(), colony.mound().isBuildSpot(level, spot));
            spot = null;
            return;
        }
        ant.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (ant.getNavigator().isDone() && isWithinReach(ant, spot)) {
            place(level);
        } else if (ant.getNavigator().isDone()) {
            walkToSpot();
        }
    }

    /** Plans the walk to the spot; tries other spots when there is no route, and stops trying after a few. */
    private void walkToSpot() {
        while (spot != null && !ant.getNavigator().moveTo(withinReach(spot), spot, 1.0)) {
            if (++failedRoutes >= MAX_FAILED_ROUTES) {
                AntColoniesMod.LOGGER.info("[mound] termite at {} found no route to mound spots (last: {})", ant.blockPosition().toShortString(), spot.toShortString());
                spot = null;
                return;
            }
            spot = colony.mound().pickBuildSpot(ant.level(), ant);
        }
    }

    private void place(ServerLevel level) {
        if (colony.mound().addLayer(level, spot)) {
            BlockState now = level.getBlockState(spot);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, now),
                    spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, spot, now.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
            ant.setCarriedBlock(null);
            ColonyManager.get(level).markChanged();
            spot = null;
            return;
        }
        if (!colony.mound().isBuildSpot(level, spot)) {
            // Someone else finished it, or it can't take soil any more: another spot.
            spot = colony.mound().pickBuildSpot(level, ant);
            if (spot != null) {
                walkToSpot();
            }
        }
        // Otherwise a creature is standing in it: wait a moment (the spot is ours, nobody else brings soil to it).
    }
}
