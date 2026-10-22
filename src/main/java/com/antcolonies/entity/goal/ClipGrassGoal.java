package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyParts;
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

    private final AbstractAntEntity ant;
    /** The walk to the plant; a plant it found no way to is left alone for a while. */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos grass;
    private int ticks;
    private int clipping;
    /** Eating from inside the nest (out of foraging time): from a foothold inside only. */
    private boolean fromInside;
    /**
     * Where the forager leaves the colony's trunk trail for its plant (harvester ants: it walks out along the trail
     * first, then fans out; see {@code TrunkTrails}); null once it is there, or with no trail that way.
     */
    @Nullable
    private BlockPos via;

    public ClipGrassGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only look for work now and then, so ants don't all think on the same tick (every time just before it would count as idle).
        if (ant.getColonyId() == null || ant.hasFullMandibles() || !ant.looksForWork(40)
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
        if (!fromInside && !colony.part(ColonyParts.DOOR).map(door -> door.mayGo(level, ant)).orElse(true)) {
            return false; // waits by the entrance for the homecomers' touches (Gordon's rule, see colony.Door)
        }
        grass = colony.food().findGrass(level, ant.blockPosition(), errand.leftAlone(), ant.getUUID(), fromInside);
        if (grass == null || colony.part(ColonyParts.RECRUITS).map(recruits -> recruits.passesBy(ant.getUUID(), grass)).orElse(false)) {
            return false; // (a recruit on its way to its patch passes poorer food by)
        }
        colony.food().claimGrass(level, grass, ant.getUUID());
        if (!fromInside) {
            colony.part(ColonyParts.DOOR).ifPresent(door -> door.setOut(level, ant));
        }
        via = fromInside ? null : colony.part(ColonyParts.TRUNK_TRAILS).map(trunk -> trunk.setOut(level, grass)).orElse(null);
        if (via != null && flat(ant.blockPosition()) >= flat(via) - 1.0) {
            via = null; // already out past the trail's end for this plant (it was out in the field): straight there
        }
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

    /** How far (sideways) a cell is from the nest's middle. */
    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isNear(BlockPos cell, BlockPos plant) {
        return Math.abs(cell.getX() - plant.getX()) <= 1 && Math.abs(cell.getY() - plant.getY()) <= 1
                && Math.abs(cell.getZ() - plant.getZ()) <= 1;
    }

    @Override
    public void start() {
        ticks = 0;
        clipping = 0;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (colony != null) {
            colony.food().releaseGrass(ant.getUUID());
        }
        grass = null;
        via = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (grass == null || colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (via != null) {
            // Out along the trunk trail first.
            BlockPos at = ant.blockPosition();
            if (Math.abs(at.getX() - via.getX()) <= 1 && Math.abs(at.getZ() - via.getZ()) <= 1 && Math.abs(at.getY() - via.getY()) <= 2) {
                via = null;
                errand.stop();
            } else if (!errand.walkOn(via, 1.0)) {
                via = null; // no way onto the trail from here: straight to the plant
            } else {
                return;
            }
        }
        ant.getLookControl().setLookAt(grass.getX() + 0.5, grass.getY() + 0.3, grass.getZ() + 0.5);
        if (!errand.isWalking() && isNear(ant.blockPosition(), grass)) {
            clip(level);
        } else if (!errand.goTo(grass.immutable(), fromInside
                ? nearPlant(grass).and(at -> colony.nest().isInside(level, at.cell())) : nearPlant(grass), grass, 1.0)) {
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
            if (!colony.part(ColonyParts.BAKERY).map(bakery -> bakery.hasPlaceForASeed(level, ant.getUUID()))
                    .orElseGet(() -> colony.food().claimStorePlace(level, ant.getUUID()))) {
                ticks = GIVE_UP_TICKS; // nowhere to take a piece now (the store full, the mill busy): the plant is left
                return;
            }
            ant.setCarriedSeed(colony.food().cutPiece());
            ant.setSeedForBread(colony.part(ColonyParts.BAKERY).map(bakery -> bakery.isMilling(ant.getUUID())).orElse(false)); // to the mill if the store has no room
            colony.food().clippedGrass(level, grass, ant.getUUID());
            ColonyManager.get(level).markChanged();
            grass = null; // so stop() does not free the tuft again
        }
    }
}
