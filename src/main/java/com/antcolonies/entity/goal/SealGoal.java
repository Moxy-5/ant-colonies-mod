package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestWalls;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntPathFinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the nest shut: walks to an opening in the nest walls (a cave, a crack to the outside, a gap towards
 * another room) and packs it full of soil. The entrance hole is the only opening that stays.
 */
public class SealGoal extends Goal {
    /** Ticks between two packs of soil, and the share of a hole one pack closes (an ant on its own needs four packs). */
    private static final int PACK_INTERVAL = 10;
    private static final float PACK_SHARE = 0.25F;
    /** If the ant can't finish within 20 seconds it gives up (another ant, or later, will try). */
    private static final int GIVE_UP_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private NestWalls.Leak leak;
    /** Where this ant closes the hole from: a spot it can reach without passing through the hole. */
    @Nullable
    private BlockPos stand;
    private int ticks;
    private int packCooldown;

    public SealGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        // An ant with a pellet (or a seed, or an egg) in its mouth must carry it to its place first.
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0
                || !ant.mayDo(ColonyRole.BUILDER)) {
            return false;
        }
        if (!(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        leak = colony == null ? null : colony.walls().findLeak(level, ant.blockPosition());
        if (leak == null) {
            return false;
        }
        colony.walls().claimLeak(level, leak.hole(), ant.getUUID());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && leak != null && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS
                && colony.walls().isLeakOpen(ant.level(), leak.hole());
    }

    @Override
    public void start() {
        ticks = 0;
        packCooldown = PACK_INTERVAL;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && leak != null) {
            colony.walls().releaseLeak(leak.hole(), ant.getUUID());
        }
        leak = null;
        stand = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (leak == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        BlockPos hole = leak.hole();
        ant.getLookControl().setLookAt(Vec3.atCenterOf(hole));

        if (stand == null || !ant.blockPosition().equals(stand)) {
            if (ant.getNavigator().isDone() && !walkToAPlugSpot(level, hole)) {
                ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
            }
            return;
        }

        ant.getNavigator().stop();
        if (--packCooldown > 0) {
            return;
        }
        packCooldown = PACK_INTERVAL;
        BlockState soil = colony.profile().nest().plugState();
        level.playSound(null, hole, soil.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                hole.getX() + 0.5, hole.getY() + 0.5, hole.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
        if (colony.walls().addSealProgress(hole, PACK_SHARE) >= 1.0F && colony.walls().plugLeak(level, hole)) {
            level.playSound(null, hole, soil.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }

    /**
     * Walks to the best place to close the hole from that it can reach without passing through the hole itself: a
     * spot only reachable through the hole is on its far side, and the ant would shut itself in. False if there is none.
     */
    private boolean walkToAPlugSpot(ServerLevel level, BlockPos hole) {
        AntPathFinder.StepCosts notThroughTheHole = AntPathFinder.StepCosts.forbid(java.util.List.of(hole));
        for (BlockPos spot : colony.walls().plugSpots(level, hole)) {
            if (ant.getNavigator().moveTo(spot, 1.0, notThroughTheHole)) {
                stand = spot;
                return true;
            }
        }
        return false;
    }
}
