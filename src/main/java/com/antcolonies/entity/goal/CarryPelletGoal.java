package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * An ant holding a soil pellet carries it out of the hole and adds it to the mound around the entrance (or into a gap
 * in the hill that was knocked out; see {@link RepairHillGoal}). While the surface is shut to it (at night, in the rain,
 * in the midday rest: see {@link AbstractAntEntity#mayGoOut}) it puts the pellet on the night soil heap just under the
 * entrance instead; {@link CarryNightSoilGoal} brings that up once the clock opens.
 */
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
    /** Putting it on the night soil heap (the surface is shut), not on the hill. */
    private boolean night;

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
        night = colony != null && !ant.mayGoOut() && colony.hill().hasMoundRoom(level);
        if (night) {
            spot = colony.hill().getRepairSoilSpot();
            return true;
        }
        spot = colony == null ? null : colony.hill().pickMoundSpot(level, ant.getRandom(), ant.getUUID());
        if (spot == null) {
            // Colony gone, or the mound is finished: nowhere to put it, so let go.
            ant.setCarriedBlock(null);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && ant.isCarrying() && night == !ant.mayGoOut();
    }

    @Override
    public void start() {
        ticks = 0;
        failedRoutes = 0;
        if (night) {
            ant.getNavigator().moveTo(spot, 1.0);
        } else {
            walkToSpot();
        }
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.hill().releaseMoundSpot(ant.getUUID());
        }
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (!ant.isCarrying()) {
            // It let the pellet go (no route to any spot, see walkToSpot) in this very tick, maybe while standing right
            // next to the spot: nothing left to place.
            spot = null;
        }
        if (spot == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (night) {
            heapNightSoil(level);
            return;
        }
        if (++ticks >= GIVE_UP_TICKS) {
            AntColoniesMod.LOGGER.info("[pellet] ant at {} gave up on spot {} after {} ticks (navigator done: {}, spot valid: {}, living things in the spot: {}, block there: {}, block below: {})",
                    ant.blockPosition().toShortString(), spot.toShortString(), ticks, ant.getNavigator().isDone(),
                    colony.hill().isMoundSpotValid(level, spot), level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(spot)).size(),
                    level.getBlockState(spot), level.getBlockState(spot.below()));
            ant.setCarriedBlock(null);
            colony.hill().pauseHillRepair(level); // a gap that can't be reached: don't keep fetching soil for it
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
                AntColoniesMod.LOGGER.info("[pellet] ant at {} found no route to mound spots (last: {})", ant.blockPosition().toShortString(), spot.toShortString());
                ant.setCarriedBlock(null);
                colony.hill().pauseHillRepair(ant.level());
                spot = null;
                return;
            }
            spot = colony.hill().pickMoundSpot(ant.level(), ant.getRandom(), ant.getUUID());
        }
        if (spot == null) {
            ant.setCarriedBlock(null);
        }
    }

    /** Night: up the shaft to just under the entrance, where the pellet goes on the heap (it is not placed as a block). */
    private void heapNightSoil(ServerLevel level) {
        double dx = ant.getX() - (spot.getX() + 0.5);
        double dz = ant.getZ() - (spot.getZ() + 0.5);
        double dy = ant.getY() - spot.getY();
        boolean there = dx * dx + dz * dz <= REACH * REACH && Math.abs(dy) < 2.0;
        if (!there && ++ticks < GIVE_UP_TICKS) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(spot, 1.0)) {
                ticks = GIVE_UP_TICKS; // no way up: it is left where the ant is, on the heap all the same
            }
            return;
        }
        ant.getNavigator().stop();
        colony.hill().addNightSoil(ant.getCarriedBlock());
        ant.setCarriedBlock(null);
        ColonyManager.get(level).markChanged();
    }

    /** Starts walking to a free cell next to the spot. False if there is none to go to. */
    private boolean stepAside(ServerLevel level) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = spot.relative(side);
            if (level.getBlockState(next).canBeReplaced() && ant.getNavigator().moveTo(next, 1.0)) {
                return true;
            }
        }
        return false;
    }

    private void place(ServerLevel level) {
        // Don't wall the ant into its own block: if it is standing in the spot, it steps to the side first
        // (it can place the pellet from the next cell), and tries again when it is out of the way.
        if (ant.getBoundingBox().intersects(new AABB(spot))) {
            if (ant.getNavigator().isDone() && !stepAside(level)) {
                spot = colony.hill().pickMoundSpot(level, ant.getRandom(), ant.getUUID());
                if (spot == null) {
                    ant.setCarriedBlock(null);
                } else {
                    walkToSpot();
                }
            }
            return;
        }
        if (!colony.hill().placePellet(level, spot)) {
            if (colony.hill().isMoundSpotValid(level, spot)) {
                return; // someone is standing in the spot: wait a moment (the spot is ours, nobody else carries soil to it)
            }
            spot = colony.hill().pickMoundSpot(level, ant.getRandom(), ant.getUUID());
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
