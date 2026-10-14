package com.antcolonies.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A forager with no food plant it knows of to go for walks out to a random spot in the colony's food area and looks
 * round there, so the workers find the plants round the nest (see {@code FoodMap}: a plant within the find radius of a
 * worker is found, and the colony knows it once that worker is back inside). Even when there is known food, about one
 * trip in {@link #SCOUT_ANYWAY} is a scouting trip, so a richer find farther out is still made. Only for species that
 * find their food by looking (termites), only at foraging time, and only while the colony wants food. The spot lies
 * between a third of the food radius and all of it from the entrance, so the scouts range out. My picks.
 */
public class ScoutGoal extends Goal {
    /** With known food to go for, one trip in this many is still a scouting trip. */
    private static final int SCOUT_ANYWAY = 6;
    /** It gives up a scouting walk after this long (ticks). */
    private static final int GIVE_UP_TICKS = 600;
    /** How long (ticks) it stands and looks round at the spot. */
    private static final int LOOK_TICKS = 40;

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    @Nullable
    private BlockPos spot;
    private int ticks;
    private int looking;

    public ScoutGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (ant.getColonyId() == null || ant.hasFullMandibles() || ant.getRandom().nextInt(40) != 0
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.foodMap().findsByLooking() || !colony.isForagingTime(level) || !colony.food().wantsSeeds(level)) {
            return false;
        }
        boolean foodToGoFor = colony.food().findGrass(level, ant.blockPosition(), java.util.Set.of(), ant.getUUID()) != null;
        if (foodToGoFor && ant.getRandom().nextInt(SCOUT_ANYWAY) != 0) {
            return false;
        }
        spot = pickSpot(level, colony);
        return spot != null;
    }

    /** A random open spot on the ground in the colony's food area, outside the nest and the mound; null if none was hit. */
    @Nullable
    private BlockPos pickSpot(ServerLevel level, Colony colony) {
        BlockPos center = colony.getCenter();
        int radius = colony.profile().food().radius();
        for (int tries = 0; tries < 8; tries++) {
            float angle = ant.getRandom().nextFloat() * Mth.TWO_PI;
            double distance = radius / 3.0 + ant.getRandom().nextDouble() * radius * 2.0 / 3.0;
            int x = center.getX() + Mth.floor(Mth.cos(angle) * distance);
            int z = center.getZ() + Mth.floor(Mth.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) {
                continue;
            }
            BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockPos below = cell.below();
            if (!colony.foodMap().mayScout(cell)) {
                continue;
            }
            if (Math.abs(cell.getY() - center.getY()) > 6 || !level.getFluidState(cell).isEmpty()
                    || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || colony.nest().isInside(level, cell) || colony.mound().getParts(level).containsKey(cell)) {
                continue;
            }
            return cell;
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && spot != null && ticks < GIVE_UP_TICKS && looking < LOOK_TICKS && !ant.hasFullMandibles()
                && colony.isForagingTime(ant.level());
    }

    @Override
    public void start() {
        ticks = 0;
        looking = 0;
        if (spot != null && !ant.getNavigator().moveTo(spot, 1.0)) {
            spot = null; // no way there: another time, another spot
        }
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        spot = null;
        colony = null;
    }

    @Override
    public void tick() {
        if (spot == null) {
            return;
        }
        ticks++;
        if (!ant.getNavigator().isDone()) {
            return;
        }
        if (ant.blockPosition().distSqr(spot) > 2.0 && looking == 0) {
            if (!ant.getNavigator().moveTo(spot, 1.0)) {
                ticks = GIVE_UP_TICKS;
            }
            return;
        }
        if (looking++ % 15 == 0) { // stands and looks round (feels about with its antennae)
            float angle = ant.getRandom().nextFloat() * Mth.TWO_PI;
            ant.getLookControl().setLookAt(ant.getX() + Mth.cos(angle) * 3, ant.getY(), ant.getZ() + Mth.sin(angle) * 3);
        }
    }
}
