package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
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
        ant.getNavigation().stop();
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
            // Stand in the open space next to the block (above it in the shaft, beside it in the tunnel).
            if (ant.getNavigation().isDone()) {
                BlockPos stand = Colony.findStandSpot(level, target);
                if (stand == null) {
                    stuckTicks = GIVE_UP_TICKS;
                    return;
                }
                Vec3 standCenter = Vec3.atBottomCenterOf(stand);
                if (ant.distanceToSqr(standCenter) < 1.0) {
                    // Walking counts as "arrived" a little before the middle of the spot, which can
                    // leave the ant just out of biting range of the next block along a tunnel:
                    // so take the last small step straight toward the block.
                    ant.getMoveControl().setWantedPosition(blockCenter.x, ant.getY(), blockCenter.z, 0.6);
                } else {
                    ant.getNavigation().moveTo(standCenter.x, standCenter.y, standCenter.z, 1.0);
                }
                stuckTicks++;
            }
            return;
        }

        stuckTicks = 0;
        ant.getNavigation().stop();
        // Cancel any leftover step toward the block.
        ant.getMoveControl().setWantedPosition(ant.getX(), ant.getY(), ant.getZ(), 0.0);
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

        // Every bite leaves a soil pellet in the mandibles. Grass and roots turn into plain dirt.
        ant.setCarriedBlock(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.ROOTED_DIRT)
                ? Blocks.DIRT.defaultBlockState() : state);

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
