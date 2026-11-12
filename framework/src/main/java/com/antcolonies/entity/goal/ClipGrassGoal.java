package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyNews;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    /**
     * Ticks spent on the last little step to this plant, over every try (BUGS 145): a step that dropped the ant into the
     * cell below sent it on a walk back, and a walk wipes the step's own "no nearer" clock, so it stepped and walked for
     * ever. Past {@link #CLOSE_IN_LIMIT} it nibbles from any cell beside the plant.
     */
    private int closingTicks;
    private static final int CLOSE_IN_LIMIT = 60;
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
        // (nor where its colony's alarm is felt: the termite alarm rework, step 3)
        fromInside = !colony.isForagingTime(level) || colony.isAlarmed(level, ant.blockPosition());
        if (fromInside && !colony.keepsASchedule()) {
            return false;
        }
        if (!fromInside && colony.mustWait(level, ant)) {
            return false; // waits by the entrance for the homecomers' touches (Gordon's rule, see colony.Door)
        }
        grass = colony.food().findGrass(level, ant.blockPosition(), errand.leftAlone(), ant.getUUID(), fromInside);
        if (grass == null || colony.passesBy(level, ant.getUUID(), grass)) {
            return false; // (a recruit on its way to its patch passes poorer food by)
        }
        colony.food().claimGrass(level, grass, ant.getUUID());
        if (!fromInside) {
            colony.news().tell(level, ColonyNews.SET_OUT, new ColonyNews.SetOut(ant));
        }
        ColonyHooks.Trails trails = colony.lastingTrails();
        via = fromInside || trails == null ? null : trails.setOut(level, grass);
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
        closingTicks = 0;
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
        // (BUGS 145: a small body a hair under the cell beside the plant, its feet at 64.9 down in an open mud cast's
        // top, counted as a cell too low; its walk to the cell above, a tenth of a block up, slipped back and began again
        // for ever. Within a quarter block of a cell beside the plant, it stops walking and nibbles.)
        boolean justUnder = !isNear(ant.blockPosition(), grass)
                && isNear(BlockPos.containing(ant.getX(), ant.getY() + 0.25, ant.getZ()), grass);
        if (justUnder && errand.isWalking()) {
            errand.stop();
        }
        if ((!errand.isWalking() || justUnder) && (justUnder || isNear(ant.blockPosition(), grass))) {
            if (atPlant(level)) {
                clip(level);
            }
        } else if (!errand.goTo(grass.immutable(), fromInside
                ? nearPlant(grass).and(at -> colony.nest().isInside(level, at.cell())) : nearPlant(grass), grass, 1.0)) {
            colony.food().noWayTo(level, grass, Errand.LEAVE_ALONE_TICKS); // (the others skip it for as long as this ant does)
            ticks = GIVE_UP_TICKS; // no way to get there: let another ant have a go
        }
    }

    /**
     * Real sizes (2026-11-06, docs/BUGS.md 132): whether its jaws are at the plant. A walk ends in a cell beside it (one
     * of the 26 round it, or its own), which a big body filled; a real-size termite worker stood half a block off and
     * nibbled the air. So a body with a size first steps the last little bit to the plant's nearest face
     * ({@link AbstractAntEntity#closeIn}); getting no nearer, it nibbles from where it stands, as before. A body with
     * no size (the red harvester ant, until its rework) nibbles at once, as before.
     */
    private boolean atPlant(ServerLevel level) {
        if (ant.bodyLength() <= 0.0) {
            return true;
        }
        VoxelShape shape = level.getBlockState(grass).getShape(level, grass);
        AABB plant = shape.isEmpty() ? new AABB(grass) : shape.bounds().move(grass);
        double reach = ant.jawReach();
        if (ant.getBoundingBox().inflate(reach, 0.2, reach).intersects(plant)) {
            return true;
        }
        double nx = Math.max(plant.minX, Math.min(plant.maxX, ant.getX()));
        double nz = Math.max(plant.minZ, Math.min(plant.maxZ, ant.getZ()));
        Vec3 away = new Vec3(ant.getX() - nx, 0.0, ant.getZ() - nz);
        if (away.lengthSqr() < 1.0E-4) {
            return true; // (right under or over it: nothing to step)
        }
        if (++closingTicks > CLOSE_IN_LIMIT) {
            return true; // (stepped long enough, over every try: from where it stands, BUGS 145)
        }
        Vec3 stand = new Vec3(nx, ant.getY(), nz).add(away.normalize().scale(ant.getBbWidth() / 2.0 + 0.01));
        return ant.closeIn(stand, 1.8) != AbstractAntEntity.CloseIn.STEPPING;
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
            if (!colony.claimPlaceForAPiece(level, ant.getUUID())) {
                ticks = GIVE_UP_TICKS; // nowhere to take a piece now (the store full, the mill busy): the plant is left
                return;
            }
            ant.setCarriedSeed(colony.food().cutPiece());
            ant.setSeedForBread(colony.pieceGoesToMill(ant.getUUID())); // to the mill if the store has no room
            colony.food().clippedGrass(level, grass, ant.getUUID());
            ColonyManager.get(level).markChanged();
            grass = null; // so stop() does not free the tuft again
        }
    }
}
