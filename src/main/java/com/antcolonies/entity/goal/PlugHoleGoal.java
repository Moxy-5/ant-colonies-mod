package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.colony.NestPlan;
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
 * When the foraging time is over a worker plugs an open foraging hole from inside (builders; others when idle): it
 * walks to the end of the hole's tunnel, under the hole, never through the hole itself (so it is not shut out), and
 * packs it shut with soil. See {@link com.antcolonies.colony.ForagingHoles}.
 */
public class PlugHoleGoal extends Goal {
    /** Packing the soil into the hole takes this long: 2 seconds. */
    private static final int PACK_TICKS = 40;
    private static final int PACK_INTERVAL = 10;
    /** If the worker can't get under the hole within 30 seconds it gives up for now. */
    private static final int GIVE_UP_TICKS = 600;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private NestPlan.ForagingTunnel tunnel;
    private int ticks;
    private int packed;

    public PlugHoleGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(20) != 0 || !ant.mayDo(ColonyRole.BUILDER)
                || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        tunnel = colony == null ? null : colony.foragingHoles().pickHoleToPlug(level, ant);
        if (tunnel == null) {
            return false;
        }
        // Up to the end of the tunnel from inside: never through the hole it is about to close.
        if (!ant.getNavigator().moveTo(tunnel.end(), 1.0, AntPathFinder.StepCosts.forbid(List.of(tunnel.hole())))) {
            colony.foragingHoles().releasePlug(ant.getUUID());
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && tunnel != null && ticks < GIVE_UP_TICKS && !ant.hasFullMandibles()
                && colony.foragingHoles().wantsPlugging(ant.level()) && com.antcolonies.colony.ForagingHoles.isOpen(ant.level(), tunnel.hole());
    }

    @Override
    public void start() {
        ticks = 0;
        packed = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.foragingHoles().releasePlug(ant.getUUID());
        }
        tunnel = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (tunnel == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        BlockPos hole = tunnel.hole();
        ant.getLookControl().setLookAt(Vec3.atCenterOf(hole));
        if (!ant.blockPosition().equals(tunnel.end())) {
            if (ant.getNavigator().isDone()
                    && !ant.getNavigator().moveTo(tunnel.end(), 1.0, AntPathFinder.StepCosts.forbid(List.of(hole)))) {
                ticks = GIVE_UP_TICKS; // no way there from inside
            }
            return;
        }
        // Under the hole: pack soil into it.
        ant.getNavigator().stop();
        BlockState soil = colony.profile().nest().plugState();
        if (++packed % PACK_INTERVAL == 0) {
            level.playSound(null, hole, soil.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                    hole.getX() + 0.5, hole.getY() + 0.1, hole.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
        }
        if (packed >= PACK_TICKS && !colony.foragingHoles().plug(level, hole)) {
            packed = PACK_TICKS - PACK_INTERVAL; // someone is in the hole: wait for them to pass
        }
    }
}
