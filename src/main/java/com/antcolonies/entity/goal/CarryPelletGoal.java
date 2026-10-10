package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** An ant holding a soil pellet carries it out of the hole and adds it to the mound around the entrance. */
public class CarryPelletGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get before it places the pellet. */
    private static final double REACH = 1.3;
    /** If the ant can't get there in 20 seconds it just lets the pellet go. */
    private static final int GIVE_UP_TICKS = 400;
    /** After this many spots with no route to them, the ant lets the pellet go. */
    private static final int MAX_FAILED_ROUTES = 4;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int failedRoutes;

    public CarryPelletGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarrying() || !(ant.level() instanceof ServerLevel level) || ant.getColonyId() == null) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        spot = colony == null ? null : colony.pickMoundSpot(level, ant.getRandom());
        if (spot == null) {
            // Colony gone, or the mound is finished: nowhere to put it, so let go.
            ant.setCarriedBlock(null);
            return false;
        }
        return true;
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
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (++ticks >= GIVE_UP_TICKS) {
            ant.setCarriedBlock(null);
            return;
        }
        // Sideways distance only: a small height difference (a step up or down) doesn't matter.
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        double dy = ant.getY() - spot.getY();
        if (dx * dx + dz * dz <= REACH * REACH && dy > -2.5 && dy < 2.0) {
            place(level);
        } else if (ant.getNavigator().isDone()) {
            walkToSpot();
        }
    }

    /** Plans the walk to the mound spot. If there is no route, tries other spots, and lets the pellet go after a few failures. */
    private void walkToSpot() {
        while (spot != null && !ant.getNavigator().moveTo(spot, 1.0)) {
            if (++failedRoutes >= MAX_FAILED_ROUTES) {
                ant.setCarriedBlock(null);
                return;
            }
            spot = colony.pickMoundSpot(ant.level(), ant.getRandom());
        }
        if (spot == null) {
            ant.setCarriedBlock(null);
        }
    }

    private void place(ServerLevel level) {
        // Don't wall the ant into its own block: if it is standing in the spot, try another one.
        boolean antInTheWay = ant.getBoundingBox().intersects(new AABB(spot));
        if (antInTheWay || !colony.placePellet(level, spot)) {
            spot = colony.pickMoundSpot(level, ant.getRandom());
            if (spot == null) {
                ant.setCarriedBlock(null);
            } else {
                walkToSpot();
            }
            return;
        }
        BlockState pellet = ant.getCarriedBlock();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pellet),
                spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, spot, pellet.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
        ant.setCarriedBlock(null);
    }
}
