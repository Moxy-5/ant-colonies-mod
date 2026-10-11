package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Walks to a tuft of short grass near the nest, nibbles at it for a couple of seconds and clips one seed off it. A tuft
 * gives up to {@link Colony#SEEDS_PER_GRASS} seeds and then breaks; {@link StoreSeedGoal} then carries the seed down
 * to the food store. Only looks for grass when there is somewhere for the seeds to go: part of the food store is dug out, or the queen wants them.
 */
public class ClipGrassGoal extends Goal {
    /** How close (sideways, in blocks) the ant must get to the grass. */
    private static final double REACH = 0.6;
    /** How long (in ticks) the ant nibbles before the seed comes off. */
    private static final int CLIP_TICKS = 50;
    /** If the ant can't get there in 30 seconds it leaves the grass to the others. */
    private static final int GIVE_UP_TICKS = 600;
    /** Grass this ant found no way to is left alone for this long. */
    private static final int UNREACHABLE_TICKS = 400;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos grass;
    private int ticks;
    private int clipping;
    /** Grass this ant failed to reach: position -> game time until which it leaves it alone. */
    private final Map<BlockPos, Long> unreachable = new HashMap<>();

    public ClipGrassGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick.
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(40) != 0
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null) {
            return false;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        grass = colony.findGrass(level, ant.blockPosition(), unreachable.keySet());
        if (grass == null) {
            return false;
        }
        colony.claimGrass(level, grass);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && grass != null && ant.level().getBlockState(grass).is(Blocks.SHORT_GRASS)
                && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        clipping = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null && grass != null) {
            colony.releaseGrass(grass); // a clipped tuft is marked again by clippedGrass, after this
        }
        grass = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (grass == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        ant.getLookControl().setLookAt(grass.getX() + 0.5, grass.getY() + 0.3, grass.getZ() + 0.5);
        double dx = ant.getX() - (grass.getX() + 0.5);
        double dz = ant.getZ() - (grass.getZ() + 0.5);
        double dy = ant.getY() - grass.getY();
        if (dx * dx + dz * dz <= REACH * REACH && Math.abs(dy) < 1.5) {
            ant.getNavigator().stop();
            clip(level);
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(grass, 1.0)) {
            unreachable.put(grass.immutable(), level.getGameTime() + UNREACHABLE_TICKS);
            ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
        }
    }

    /** Nibbles at the grass; after a while one seed comes off and the ant takes it. */
    private void clip(ServerLevel level) {
        clipping++;
        if (clipping % 12 == 1) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(grass)),
                    grass.getX() + 0.5, grass.getY() + 0.3, grass.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.02);
            level.playSound(null, grass, SoundEvents.GRASS_HIT, SoundSource.NEUTRAL, 0.2F, 1.6F);
        }
        if (clipping >= CLIP_TICKS) {
            ant.setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
            ant.setSeedForBread(colony.claimSeedForBread(level, ant.getUUID())); // to the mill if there is no store yet
            colony.clippedGrass(level, grass);
            ColonyManager.get(level).markChanged();
            grass = null; // so stop() does not free the tuft again
        }
    }
}
