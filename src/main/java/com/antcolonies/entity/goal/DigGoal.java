package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Walks to the colony's next shaft block and digs it bite by bite. */
public class DigGoal extends Goal {
    private static final double REACH = 1.4;
    private static final int BITE_INTERVAL = 10;
    private static final int GIVE_UP_TICKS = 60;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos target;
    private int biteCooldown;
    private int stuckTicks;

    public DigGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        // An ant with a pellet in its mouth must carry it out before biting again.
        if (ant.getColonyId() == null || ant.isCarrying() || ant.getRandom().nextInt(20) != 0) {
            return false;
        }
        if (!(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        target = colony == null ? null : colony.findDigTarget(level);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !ant.isCarrying() && stuckTicks < GIVE_UP_TICKS
                && ant.level().getBlockState(target).is(ModTags.DIGGABLE);
    }

    @Override
    public void start() {
        stuckTicks = 0;
        biteCooldown = BITE_INTERVAL;
    }

    @Override
    public void stop() {
        // The crack overlay is left in place (the game fades it out by itself): the ant leaves to
        // carry a pellet and the block is still half dug.
        ant.getNavigator().stop();
        target = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (target == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 blockCenter = Vec3.atCenterOf(target);
        ant.getLookControl().setLookAt(blockCenter);

        if (ant.distanceToSqr(blockCenter) > REACH * REACH) {
            // Walk to the open space next to the block (above it in the shaft, beside it in the tunnel).
            AntNavigator legs = ant.getNavigator();
            if (legs.isDone()) {
                boolean walking = false;
                for (BlockPos stand : Colony.findStandSpots(level, target)) {
                    if (legs.moveTo(stand, 1.0)) {
                        walking = true;
                        break;
                    }
                }
                if (!walking) {
                    stuckTicks = GIVE_UP_TICKS; // no way to get there: let another block (or ant) have a go
                } else {
                    // Arrived but still out of reach counts toward giving up.
                    stuckTicks++;
                }
            }
            return;
        }

        stuckTicks = 0;
        ant.getNavigator().stop();
        if (--biteCooldown <= 0) {
            biteCooldown = BITE_INTERVAL;
            bite(level, blockCenter);
        }
    }

    private void bite(ServerLevel level, Vec3 blockCenter) {
        BlockState state = level.getBlockState(target);
        // Harder blocks take more bites: plain dirt is about 6 bites, roughly 3 seconds for one ant.
        float bitesNeeded = Math.max(1.0F, state.getDestroySpeed(level, target) * 12.0F);
        float progress = colony.addDigProgress(target, 1.0F / bitesNeeded);

        // Every bite leaves a soil pellet in the mandibles (grass and roots turn into plain dirt), as long as
        // the hill still has room for it. Once the hill is complete the soil is simply left behind.
        if (colony.hasMoundRoom(level)) {
            ant.setCarriedBlock(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.ROOTED_DIRT)
                    ? Blocks.DIRT.defaultBlockState() : state);
        }

        level.playSound(null, target, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.3F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                blockCenter.x, target.getY() + 1.0, blockCenter.z, 4, 0.15, 0.05, 0.15, 0.05);

        if (progress >= 1.0F) {
            colony.clearDigProgress(target);
            level.destroyBlockProgress(ant.getId(), target, -1);
            level.destroyBlock(target, false, ant);
        } else {
            level.destroyBlockProgress(ant.getId(), target, Math.min(9, (int) (progress * 10.0F)));
        }
    }
}
