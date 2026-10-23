package com.antcolonies.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.SavannaParts;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.colony.Mound;
import com.antcolonies.colony.MoundGrowth;
import com.antcolonies.colony.MoundReach;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A termite shapes its mound where the plan says (see {@link com.antcolonies.colony.MoundShaping#pickShapeJob}): it carves a
 * tunnel into finished soil from a channel or the shaft right beside it (so the new tunnel joins up at once, and opens
 * into the shaft beside it), reshapes a piece into the one the plan has there now (a thicker spire piece, a ramp facing
 * another way, a mouth that is now a spire piece), or fills a piece up into the soil or tunnel block the plan has where
 * it stands. It works for a moment and it is done; no soil is needed. Carving is inside work (by day too); the rest from inside by the rules of
 * {@link MoundReach} (through a window at night, or from the shaft). Builders do it, and anyone with nothing else to do.
 */
public class ShapeMoundGoal extends Goal {
    /** How long (in ticks) the work takes. */
    private static final int WORK_TICKS = 30;
    private static final int GIVE_UP_TICKS = 600;

    private final AbstractAntEntity ant;
    /**
     * The walk to a foothold to work from; a job it found no way to is left alone for a while (docs/BUGS.md 42: without
     * it a cut-off insect searched for a route again every time it looked for work).
     */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    /** Its mound (asked once in {@link #canUse}: only a species that lists the mound shapes one). */
    @Nullable
    private Mound mound;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int work;

    public ShapeMoundGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.hasFullMandibles() || ant.getColonyId() == null || !ant.looksForWork(20) || !ant.mayDo(ColonyRole.BUILDER)
                || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        mound = colony == null ? null : colony.part(SavannaParts.MOUND).orElse(null);
        if (mound == null || !mound.isActive()) {
            return false;
        }
        spot = mound.shaping().pickShapeJob(level, ant);
        if (spot != null && (errand.isLeftAlone(spot) || !walkThere())) {
            mound.shaping().releaseShapeJob(ant.getUUID());
            spot = null;
        }
        return spot != null;
    }

    /** Where it can do the job from: beside the soil for carving, else by the building rules. */
    private Predicate<Foothold> workFrom(ServerLevel level, BlockPos target) {
        if (mound.shaping().shapeJobAt(level, target) == MoundGrowth.Job.CARVE) {
            return at -> MoundReach.canCarveFrom(level, colony, at, target);
        }
        boolean windows = mound.mayOpenWindows(level);
        return at -> MoundReach.canBuildFrom(level, colony, at, target, windows);
    }

    /**
     * Sets off to a foothold to work from. False if there is none it can get to: then the job is left alone for a
     * while.
     */
    private boolean walkThere() {
        ServerLevel level = (ServerLevel) ant.level();
        return errand.setOff(workFrom(level, spot), spot, 1.0) || errand.leaveAlone(spot);
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && mound != null && spot != null && !ant.hasFullMandibles() && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        work = 0;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        if (mound != null) {
            mound.shaping().releaseShapeJob(ant.getUUID());
        }
        spot = null;
        colony = null;
        mound = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || mound == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        ticks++;
        if (mound.shaping().shapeJobAt(level, spot) == null) {
            spot = null; // done by someone else, or not wanted any more
            return;
        }
        if (errand.isWalking()) {
            return;
        }
        Foothold at = ant.getNavigator().getFoothold();
        if (at == null || !workFrom(level, spot).test(at)) {
            if (!walkThere()) {
                spot = null;
            }
            return;
        }
        ant.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (++work % 10 == 0) {
            BlockState here = level.getBlockState(spot);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, here),
                    spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, spot, here.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.3F, 1.3F);
        }
        if (work >= WORK_TICKS && mound.shaping().shape(level, spot, at.cell())) {
            ColonyManager.get(level).markChanged();
            spot = null;
        }
    }
}
