package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walks to a food plant near the nest, nibbles at it for a couple of seconds and cuts one piece off it: a seed off a
 * tuft of short grass for the harvester ant, a piece of forage off a dead bush, dry grass, dying leaves or a hay bale
 * for termites (the species profile's food {@code plants}). A plant gives the profile's number of pieces and then
 * breaks; {@link StoreSeedGoal} then carries the piece down to the food store. Only looks for plants when there is
 * somewhere for the food to go: part of the food store is dug out, or the queen wants seeds. The ant works from the
 * plant's own cell (grass can be walked into) or from any spot right next to it (a hay bale is solid). Only at
 * foraging time (see {@code Colony.isForagingTime}); when it ends the forager leaves the plant.
 */
public class ClipGrassGoal extends Goal {
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
    /** Eating from inside the nest (out of foraging time): from a foothold inside only. */
    private boolean fromInside;
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
        // Foragers only go out at foraging time (termites: at night and in the rain). Out of it a colony that keeps a
        // schedule still eats food it can reach from inside (a fallen log over a feeding gallery).
        fromInside = !colony.isForagingTime(level);
        if (fromInside && !colony.keepsASchedule()) {
            return false;
        }
        long now = level.getGameTime();
        unreachable.values().removeIf(until -> until <= now);
        grass = colony.food().findGrass(level, ant.blockPosition(), unreachable.keySet(), ant.getUUID(), fromInside);
        if (grass == null) {
            return false;
        }
        colony.food().claimGrass(level, grass, ant.getUUID());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && grass != null && colony.food().isFoodPlant(ant.level().getBlockState(grass))
                && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS
                && (fromInside ? colony.food().isFeedableFromInside(ant.level(), grass) : colony.isForagingTime(ant.level()));
    }

    /** Any foothold from which the ant can nibble at {@code plant}: in its own cell or one of the 26 round it. */
    private static Predicate<Foothold> nearPlant(BlockPos plant) {
        return at -> isNear(at.cell(), plant);
    }

    private static boolean isNear(BlockPos cell, BlockPos plant) {
        return Math.abs(cell.getX() - plant.getX()) <= 1 && Math.abs(cell.getY() - plant.getY()) <= 1
                && Math.abs(cell.getZ() - plant.getZ()) <= 1;
    }

    @Override
    public void start() {
        ticks = 0;
        clipping = 0;
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        if (colony != null) {
            colony.food().releaseGrass(ant.getUUID());
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
        if (ant.getNavigator().isDone() && isNear(ant.blockPosition(), grass)) {
            clip(level);
        } else if (ant.getNavigator().isDone() && !ant.getNavigator().moveTo(fromInside
                ? nearPlant(grass).and(at -> colony.nest().isInside(level, at.cell())) : nearPlant(grass), grass, 1.0)) {
            unreachable.put(grass.immutable(), level.getGameTime() + UNREACHABLE_TICKS);
            ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
        }
    }

    /** Nibbles at the plant; after a while one piece comes off and the ant takes it. */
    private void clip(ServerLevel level) {
        clipping++;
        if (clipping % 12 == 1) {
            BlockState plant = level.getBlockState(grass);
            SoundType sound = plant.getSoundType();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, plant),
                    grass.getX() + 0.5, grass.getY() + 0.3, grass.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.02);
            level.playSound(null, grass, sound.getHitSound(), SoundSource.NEUTRAL, 0.2F, 1.6F);
        }
        if (clipping >= CLIP_TICKS) {
            ant.setCarriedSeed(colony.food().cutPiece());
            ant.setSeedForBread(colony.bakery().claimSeedForBread(level, ant.getUUID())); // to the mill if there is no store yet
            colony.food().clippedGrass(level, grass, ant.getUUID());
            ColonyManager.get(level).markChanged();
            grass = null; // so stop() does not free the tuft again
        }
    }
}
