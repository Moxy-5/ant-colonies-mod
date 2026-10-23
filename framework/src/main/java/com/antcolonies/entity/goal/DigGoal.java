package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;

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
    /**
     * The walk to a spot to dig the block from; a block it found no way to is left alone for a while, as in the other
     * goals (the full run of 2026-10-16: a worker walled in planned a route to the colony's next block about once a
     * second for minutes, each failed search up to 6000 steps; and the king in the user's mound cost 29 ms a tick that way).
     */
    private final Errand<BlockPos> errand;
    /** Lets an ant dig only some of the time (the queen stops once her room is finished). */
    private final BooleanSupplier allowed;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos target;
    private int biteCooldown;
    private int stuckTicks;

    /** Checked only when the ant starts digging (a worker's role), not while it digs. */
    private final BooleanSupplier mayStart;

    public DigGoal(AbstractAntEntity ant, BooleanSupplier allowed) {
        this(ant, allowed, () -> true);
    }

    public DigGoal(AbstractAntEntity ant, BooleanSupplier allowed, BooleanSupplier mayStart) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        this.allowed = allowed;
        this.mayStart = mayStart;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        // An ant with a pellet in its mouth must carry it out before biting again.
        if (ant.getColonyId() == null || ant.isCarrying() || !ant.looksForWork(20) || !allowed.getAsBoolean()
                || !mayStart.getAsBoolean()) {
            return false;
        }
        if (!(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony != null && soilWaits(level)) {
            return false;
        }
        target = colony == null ? null : colony.nest().findDigTarget(level, ant.getUUID());
        return target != null && !errand.isLeftAlone(target);
    }

    /**
     * Whether the dug soil waits to be dealt with before this builder digs on: asked of where the colony's soil goes
     * ({@link ColonyHooks#DUG_SOIL}; the harvester's morning clear-out, {@link com.antcolonies.desert.colony.Hill#soilWaits}).
     */
    private boolean soilWaits(ServerLevel level) {
        ColonyHooks.DugSoil soil = colony.hooks().first(ColonyHooks.DUG_SOIL);
        return ant.canPlaceBlocks() && soil != null && soil.waits(level, ant);
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !ant.isCarrying() && stuckTicks < GIVE_UP_TICKS && allowed.getAsBoolean()
                && colony != null && colony.nest().canDig(ant.level(), target)
                // now and then: has the night soil piled up (rest), or is there a pile to carry up first?
                && (ant.tickCount % 20 != 0 || target.equals(colony.nest().getEntrance())
                        || !(ant.level() instanceof ServerLevel level) || !soilWaits(level));
    }

    @Override
    public void start() {
        stuckTicks = 0;
        biteCooldown = BITE_INTERVAL;
        errand.begin();
    }

    @Override
    public void stop() {
        // The crack overlay is left in place (the game fades it out by itself): the ant leaves to
        // carry a pellet and the block is still half dug.
        errand.stop();
        ant.setActivity(AbstractAntEntity.ACTIVITY_NONE);
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

        // In a block touching it counts as within reach too, wherever the ant clings in it: right under the block
        // (biting an entrance plug from the shaft), or on the far wall of a tunnel's step up a slope, where there is no
        // floor (the hill tunnels' workers stood just out of reach there and never bit, 2026-10-14).
        boolean touching = ant.blockPosition().distManhattan(target) == 1;
        if (!touching && ant.distanceToSqr(blockCenter) > REACH * REACH) {
            // Walk to the open space next to the block (above it in the shaft, beside it in the tunnel): the nearest it can reach.
            ant.setActivity(AbstractAntEntity.ACTIVITY_NONE);
            if (!errand.isWalking()) {
                List<BlockPos> stands = colony.nest().standSpotsFor(level, target);
                // By day a termite digs from inside only.
                if (!errand.goTo(target.immutable(), stands.stream().filter(ant::mayBeAt).toList(), 1.0)) {
                    stuckTicks = GIVE_UP_TICKS; // no way to get there: let another block (or ant) have a go
                    com.antcolonies.ColonyFrameworkMod.LOGGER.debug("[dig] {} found no way to {} from {} (stand spots {})", ant.getId(),
                            target.toShortString(), ant.position(), stands);
                } else if (++stuckTicks >= GIVE_UP_TICKS) {
                    // Arrived but still out of reach counts toward giving up.
                    com.antcolonies.ColonyFrameworkMod.LOGGER.debug("[dig] {} gave up on {}: out of reach at {} on {} (stand spots {})", ant.getId(),
                            target.toShortString(), ant.position(), ant.getNavigator().getFoothold(), stands);
                }
            }
            return;
        }

        stuckTicks = 0;
        errand.stop();
        ant.setActivity(AbstractAntEntity.ACTIVITY_DIG);   // biting the soil loose (the body's dig clip)
        if (--biteCooldown <= 0) {
            biteCooldown = BITE_INTERVAL;
            bite(level, blockCenter);
        }
    }

    private void bite(ServerLevel level, Vec3 blockCenter) {
        BlockState state = level.getBlockState(target);
        if (target.equals(colony.nest().getEntrance()) && colony.nest().isPlantInEntrance(level)) {
            // A plant in the entrance (grass, a flower): bitten through at the stem, no soil from it.
            level.playSound(null, target, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.4F, 1.4F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    blockCenter.x, target.getY() + 0.3, blockCenter.z, 6, 0.2, 0.1, 0.2, 0.05);
            colony.nest().clearPlantInEntrance(level, ant);
            target = null;
            return;
        }
        // Harder blocks take more bites: plain dirt is about 6 bites, roughly 3 seconds for one ant.
        float bitesNeeded = Math.max(1.0F, state.getDestroySpeed(level, target) * 12.0F);
        float progress = colony.nest().addDigProgress(target, 1.0F / bitesNeeded);

        // Every bite leaves a soil pellet in the mandibles (grass and roots turn into plain dirt), as long as
        // the hill still has room for it. Once the hill is complete the soil is simply left behind. A termite mound
        // takes one pellet per layer of a mound block from each dug block (one dug block builds one mound block): asked of
        // where the colony's soil goes ({@link ColonyHooks#DUG_SOIL}).
        ColonyHooks.DugSoil soil = colony.hooks().first(ColonyHooks.DUG_SOIL);
        // Grass cut away where a runway of sheeting runs gives no soil.
        if (ant.canPlaceBlocks() && !colony.nest().foodWays().isRunwayPlant(level, target)
                && soil != null && soil.takesPellet(level, target, ant.getUUID())) {
            ant.setCarriedBlock(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.ROOTED_DIRT) ? Blocks.DIRT.defaultBlockState()
                    : soil.pellet(state));
        }

        level.playSound(null, target, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.4F, 1.3F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                blockCenter.x, target.getY() + 1.0, blockCenter.z, 4, 0.15, 0.05, 0.15, 0.05);

        if (progress >= 1.0F && !colony.walls().wallFirst(level, target)) {
            // Wall first: the sides this bite would open to the outside are plastered with mud before it goes through;
            // a plate that cannot go in yet (something in the way) holds the last bite until it can.
            level.destroyBlockProgress(ant.getId(), target, 9);
        } else if (progress >= 1.0F) {
            colony.nest().clearDigProgress(target);
            colony.news().tell(level, ColonyNews.BLOCK_DUG, new ColonyNews.BlockDug(target)); // (the mound counts its soil)
            level.destroyBlockProgress(ant.getId(), target, -1);
            if (colony.nest().foodWays().isRunwayPlant(level, target) && colony.profile().food().plantFor(state) != null) {
                colony.food().cutAwayForTheForagers(level, target); // food in the way of a runway: left for the foragers
            } else {
                level.destroyBlock(target, false, ant);
            }
        } else {
            level.destroyBlockProgress(ant.getId(), target, Math.min(9, (int) (progress * 10.0F)));
        }
    }
}
