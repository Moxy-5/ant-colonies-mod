package com.antcolonies.entity.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The nurse waits by the queen: walks to her room and stays near her. It keeps going while the queen sits in her room
 * (so the nurse neither wanders off nor takes other work), and the jobs that matter to her (carrying an egg, bringing the
 * queen a meal) come first because they have higher priority. Where the nurses feed mouth to mouth (termites), they wait
 * in a court round her head end, each in its own place in a half ring in front of her (real Macrotermes queens have a
 * court of workers at the head, feeding her, and others along her body); the ants stand anywhere near her.
 */
public class StandByQueenGoal extends Goal {
    /** The nurse tries to stay within this many blocks (sideways) of the queen. */
    private static final double NEAR = 1.6;
    /** The court round the queen's head (mouth to mouth): how many places, and how far from her mouth. */
    private static final int COURT_PLACES = 5;
    private static final double COURT_RADIUS = 0.45;

    private final AbstractAntEntity ant;
    /** The walk to the queen (and to its place in her court). */
    private final Errand<BlockPos> errand;
    @Nullable
    private Colony colony;
    private int repath;

    public StandByQueenGoal(AbstractAntEntity ant) {
        this.ant = ant;
        this.errand = new Errand<>(ant);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ant.getRandom().nextInt(10) == 0 && ant.getColonyId() != null && !ant.hasFullMandibles() && nurseWithHomeQueen();
    }

    @Override
    public boolean canContinueToUse() {
        return !ant.hasFullMandibles() && nurseWithHomeQueen();
    }

    private boolean nurseWithHomeQueen() {
        if (!(ant.level() instanceof ServerLevel level) || ant.getColonyId() == null || !ant.hasRole(ColonyRole.NURSE)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        return colony != null && colony.queenCare().isQueenHome(level);
    }

    @Override
    public void start() {
        repath = 0;
        errand.begin();
    }

    @Override
    public void stop() {
        errand.stop();
        colony = null;
    }

    @Override
    public void tick() {
        if (colony == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        AbstractAntEntity queen = colony.getQueen(level);
        if (queen == null) {
            return;
        }
        if (MouthToMouth.on(colony)) {
            standInTheCourt(queen);
            return;
        }
        ant.getLookControl().setLookAt(queen);
        if (errand.within(queen, NEAR)) {
            errand.stop();
        } else if (!errand.isWalking() && --repath <= 0) {
            errand.setOff(queen.blockPosition(), 1.0);
            repath = 40; // not every tick if there is no way
        }
    }

    /**
     * The nurse's own place in the court: on a half ring round the queen's head (5 places, from her left through her
     * front to her right, picked by the nurse's id), facing her mouth. Where its place cannot be reached, anywhere near her.
     */
    private void standInTheCourt(AbstractAntEntity queen) {
        Vec3 mouth = queen.headPoint();
        int place = Math.floorMod(ant.getUUID().hashCode(), COURT_PLACES) - COURT_PLACES / 2;
        Vec3 out = Vec3.directionFromRotation(0.0F, queen.yBodyRot + place * 40.0F).scale(COURT_RADIUS);
        Vec3 spot = mouth.add(out);
        ant.getLookControl().setLookAt(mouth.x, mouth.y, mouth.z);
        double dx = ant.getX() - spot.x;
        double dz = ant.getZ() - spot.z;
        boolean there = dx * dx + dz * dz <= 0.3 * 0.3 && Math.abs(ant.getY() - queen.getY()) < 1.5;
        boolean nearEnough = !errand.isWalking() && errand.within(queen, NEAR);
        if (there || nearEnough && repath > 0) {
            errand.stop();
        } else if (!errand.isWalking() && --repath <= 0) {
            if (!errand.setOff(BlockPos.containing(spot.add(0, 0.1, 0)), 1.0)) {
                errand.setOff(queen.blockPosition(), 1.0);
            }
            repath = 40; // not every tick if there is no way
        }
    }
}
