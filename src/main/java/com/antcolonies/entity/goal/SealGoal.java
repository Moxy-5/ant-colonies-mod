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
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the nest shut: walks to an opening in the nest walls (a cave, a crack to the outside, a gap towards
 * another room) and packs it full of soil. The entrance hole is the only opening that stays.
 */
public class SealGoal extends Goal {
    /** Ticks between two packs of soil, and how many it takes to close a hole. */
    private static final int PACK_INTERVAL = 10;
    private static final int PACKS_NEEDED = 4;
    /** If the ant can't finish within 20 seconds it gives up (another ant, or later, will try). */
    private static final int GIVE_UP_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private Colony.Leak leak;
    private int ticks;
    private int packs;
    private int packCooldown;

    public SealGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        // An ant with a pellet in its mouth must carry it out first.
        if (ant.getColonyId() == null || ant.isCarrying() || ant.getRandom().nextInt(20) != 0) {
            return false;
        }
        if (!(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        leak = colony == null ? null : colony.findLeak(level, ant.blockPosition());
        if (leak == null) {
            return false;
        }
        colony.claimLeak(level, leak.hole());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && leak != null && !ant.isCarrying() && ticks < GIVE_UP_TICKS
                && colony.isLeakOpen(ant.level(), leak.hole());
    }

    @Override
    public void start() {
        ticks = 0;
        packs = 0;
        packCooldown = PACK_INTERVAL;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        leak = null;
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

        if (!ant.blockPosition().equals(leak.stand())) {
            if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(leak.stand(), 1.0)) {
                ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
            }
            return;
        }

        ant.getNavigator().stop();
        if (--packCooldown > 0) {
            return;
        }
        packCooldown = PACK_INTERVAL;
        BlockState soil = ModBlocks.BURROWED_DIRT.get().defaultBlockState();
        level.playSound(null, hole, soil.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil),
                hole.getX() + 0.5, hole.getY() + 0.5, hole.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
        if (++packs >= PACKS_NEEDED && colony.plugLeak(level, hole)) {
            level.playSound(null, hole, soil.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }
}
