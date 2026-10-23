package com.antcolonies.desert.entity.goal;

import com.antcolonies.entity.goal.Errand;
import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.desert.colony.DesertParts;
import com.antcolonies.desert.colony.Hill;
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
 * in the midday rest: see {@link AbstractAntEntity#mayGoOut}), or while the entrance is still plugged (the first workers
 * opening their founding queen's plug: the way out is the block they bite), it puts the pellet on a pile of night soil
 * near the top of the nest instead ({@link com.antcolonies.desert.colony.NightSoil}); {@link CarryNightSoilGoal} brings that up
 * once the surface opens. With the piles full the pellet is packed into the walls.
 */
public class CarryPelletGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get before it places the pellet. */
    private static final double REACH = 1.3;
    /** If the ant can't get there in 20 seconds it just lets the pellet go. */
    private static final int GIVE_UP_TICKS = 400;

    private final AbstractAntEntity ant;
    /** The walk to the hill or the heap (after a few spots with no way to them, the ant lets the pellet go). */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    /** Its colony's hill (asked once in {@link #canUse}: only a species that lists the hill carries soil onto one). */
    @Nullable
    private Hill hill;
    @Nullable
    private BlockPos spot;
    private int ticks;
    /** Putting it on the night soil heap (the surface is shut), not on the hill. */
    private boolean night;

    public CarryPelletGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarrying() || !(ant.level() instanceof ServerLevel level) || ant.getColonyId() == null) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        hill = colony == null ? null : colony.part(DesertParts.HILL).orElse(null);
        night = hill != null && surfaceShut(level) && hill.hasMoundRoom(level);
        if (night) {
            spot = hill.nightSoil().pickPile(level);
            if (spot == null) {
                ant.setCarriedBlock(null); // the piles are full: packed into the walls
                return false;
            }
            return true;
        }
        spot = hill == null ? null : hill.pickMoundSpot(level, ant.getRandom(), ant.getUUID());
        if (spot == null) {
            // Colony gone (or no hill), or the mound is finished: nowhere to put it, so let go.
            ant.setCarriedBlock(null);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && hill != null && spot != null && ant.isCarrying() && ant.level() instanceof ServerLevel level && night == surfaceShut(level);
    }

    /** Whether this ant's soil cannot go out onto the hill now: the surface is shut to it, or the entrance is plugged. */
    private boolean surfaceShut(ServerLevel level) {
        return !ant.mayGoOut() || colony.nest().isEntrancePlugged(level);
    }

    @Override
    public void start() {
        ticks = 0;
        errand.begin();
        if (night) {
            errand.setOff(spot, 1.0);
        } else {
            walkToSpot();
        }
    }

    @Override
    public void stop() {
        errand.stop();
        if (hill != null) {
            hill.releaseMoundSpot(ant.getUUID());
        }
        spot = null;
        colony = null;
        hill = null;
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
            DesertMod.LOGGER.info("[pellet] ant at {} gave up on spot {} after {} ticks (navigator done: {}, spot valid: {}, living things in the spot: {}, block there: {}, block below: {})",
                    ant.blockPosition().toShortString(), spot.toShortString(), ticks, ant.getNavigator().isDone(),
                    hill.isMoundSpotValid(level, spot), level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(spot)).size(),
                    level.getBlockState(spot), level.getBlockState(spot.below()));
            ant.setCarriedBlock(null);
            hill.pauseHillRepair(level); // a gap that can't be reached: don't keep fetching soil for it
            return;
        }
        // A small height difference (a step up or down) doesn't matter.
        if (errand.within(spot, REACH, 2.5, 2.0)) {
            place(level);
        } else if (!errand.isWalking()) {
            walkToSpot();
        }
    }

    /** Sets off to the mound spot. If there is no way there, tries other spots, and lets the pellet go after a few (or with none left). */
    private void walkToSpot() {
        spot = errand.setOffTo(spot, () -> hill.pickMoundSpot(ant.level(), ant.getRandom(), ant.getUUID()), 1.0);
        if (spot == null) {
            if (errand.spotsWithNoWay() >= Errand.MOST_SPOTS) {
                DesertMod.LOGGER.info("[pellet] ant at {} found no route to {} mound spots", ant.blockPosition().toShortString(), errand.spotsWithNoWay());
                hill.pauseHillRepair(ant.level());
            }
            ant.setCarriedBlock(null);
        }
    }

    /** Surface shut: to a pile of night soil near the top of the nest, where the pellet is put down as one more layer. */
    private void heapNightSoil(ServerLevel level) {
        boolean there = errand.within(spot, REACH);
        if (!there && ++ticks < GIVE_UP_TICKS) {
            if (!errand.walkOn(spot, 1.0)) {
                ticks = GIVE_UP_TICKS; // no way there: packed into the walls where the ant is
            }
            return;
        }
        errand.stop();
        boolean put = there && hill.nightSoil().addLayer(level, spot);
        if (there && !put) {
            // Somebody else's soil filled this pile meanwhile: another one, or packed into the walls if they are all full.
            spot = hill.nightSoil().pickPile(level);
            ticks = 0;
            if (spot != null) {
                return;
            }
        }
        if (put) {
            BlockState pellet = ant.getCarriedBlock();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pellet), spot.getX() + 0.5, spot.getY() + 0.2,
                    spot.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
            level.playSound(null, spot, pellet.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.3F, 1.2F);
        }
        ant.setCarriedBlock(null);
        spot = null;
    }

    /** Starts walking to a free cell next to the spot. False if there is none to go to. */
    private boolean stepAside(ServerLevel level) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = spot.relative(side);
            if (level.getBlockState(next).canBeReplaced() && errand.setOff(next, 1.0)) {
                return true;
            }
        }
        return false;
    }

    private void place(ServerLevel level) {
        // Don't wall the ant into its own block: if it is standing in the spot, it steps to the side first
        // (it can place the pellet from the next cell), and tries again when it is out of the way.
        if (ant.getBoundingBox().intersects(new AABB(spot))) {
            if (!errand.isWalking() && !stepAside(level)) {
                spot = hill.pickMoundSpot(level, ant.getRandom(), ant.getUUID());
                walkToSpot();
            }
            return;
        }
        if (!hill.placePellet(level, spot)) {
            if (hill.isMoundSpotValid(level, spot)) {
                return; // someone is standing in the spot: wait a moment (the spot is ours, nobody else carries soil to it)
            }
            spot = hill.pickMoundSpot(level, ant.getRandom(), ant.getUUID());
            walkToSpot();
            return;
        }
        BlockState pellet = ant.getCarriedBlock();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pellet),
                spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, spot, pellet.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
        ant.setCarriedBlock(null);
    }
}
