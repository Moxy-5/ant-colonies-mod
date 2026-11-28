package com.antcolonies.entity.goal;

import com.antcolonies.colony.LandReady;
import java.util.EnumSet;
import java.util.Set;

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

/**
 * Searching for food, for species that find their food by looking ({@code food.find_radius}, see {@code FoodMap}: a
 * plant within the find radius of a worker is found by it).
 *
 * <p><b>Scouting and telling</b> (termites): a forager with no food plant it knows of walks out to a random spot in the
 * colony's food area and looks round there; the colony knows what it found once it is back inside. Even when there is
 * known food, about one trip in {@link #SCOUT_ANYWAY} is a scouting trip, so a richer find farther out is still made.
 * The spot lies between a third of the food radius and all of it from the entrance, so the scouts range out; with
 * galleries (termites underground) it lies within the graze of one of the holes open tonight (review R5-61).
 *
 * <p>(Until 2026-11-23 this goal also held the red harvester ant's two other ways: searching alone and being called
 * out to a rich patch along a scent line. They left with it; the git tag {@code harvester-last} has them.)
 *
 * <p>Only at foraging time and only while the colony wants food. Mind layer: it only says where to go, through the
 * ant's navigator.
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
    /** Where it goes to look. */
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
        if (ant.getColonyId() == null || ant.hasFullMandibles() || !ant.looksForWork(40)
                || !ant.mayDo(ColonyRole.FORAGER) || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        if (colony == null || !colony.foodMap().findsByLooking() || !colony.isForagingTime(level) || colony.isAlarmed(level, ant.blockPosition())
                || !colony.food().wantsSeeds(level)) {
            return false;
        }
        boolean foodToGoFor = colony.food().findGrass(level, ant.blockPosition(), Set.of(), ant.getUUID()) != null;
        if (foodToGoFor && ant.getRandom().nextInt(SCOUT_ANYWAY) != 0) {
            return false;
        }
        spot = pickSpot(level, colony);
        return spot != null;
    }

    /**
     * A random open spot on the ground in the colony's food area, outside the nest and the mound; null if none was hit.
     * A colony with galleries scouts only round the holes open to the foragers tonight, within the graze of one picked
     * at random (review R5-61: the one rule of where it grazes, {@code FieldLife#grazedFrom}; the night's explore holes,
     * picked on the web's far side, give the new ground). Before, a scout walked to a random spot anywhere in the whole
     * range, up to 56 blocks out over open ground.
     */
    @Nullable
    private BlockPos pickSpot(ServerLevel level, Colony colony) {
        com.antcolonies.colony.ColonyHooks.Underground web = colony.underground();
        if (web != null) {
            java.util.List<BlockPos> holes = web.holesInTurn(level);
            if (holes.isEmpty()) {
                return null;
            }
            int graze = web.grazeRadius();
            for (int tries = 0; tries < 8; tries++) {
                BlockPos hole = holes.get(ant.getRandom().nextInt(holes.size()));
                float angle = ant.getRandom().nextFloat() * Mth.TWO_PI;
                double distance = 1.0 + ant.getRandom().nextDouble() * Math.max(0, graze - 1);
                BlockPos cell = openGround(level, colony, hole.getX() + Mth.floor(Math.cos(angle) * distance),
                        hole.getZ() + Mth.floor(Math.sin(angle) * distance));
                if (cell != null) {
                    return cell;
                }
            }
            return null;
        }
        int radius = colony.foragingRadius();
        for (int tries = 0; tries < 8; tries++) {
            float angle = ant.getRandom().nextFloat() * Mth.TWO_PI;
            double distance = radius / 3.0 + ant.getRandom().nextDouble() * radius * 2.0 / 3.0;
            BlockPos cell = groundCell(level, colony, angle, distance, 0);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    /**
     * An open cell on the ground {@code distance} blocks out from the nest's middle in direction {@code angle}, give or
     * take {@code jitter} blocks: loaded, near the nest's height, dry, free to stand in, with a floor, outside the nest
     * and the mound, and in the test's arena; null if none was hit.
     */
    @Nullable
    private BlockPos groundCell(ServerLevel level, Colony colony, double angle, double distance, int jitter) {
        BlockPos center = colony.getCenter();
        for (int tries = 0; tries < 4; tries++) {
            int x = center.getX() + Mth.floor(Math.cos(angle) * distance) + (jitter > 0 ? ant.getRandom().nextInt(jitter * 2 + 1) - jitter : 0);
            int z = center.getZ() + Mth.floor(Math.sin(angle) * distance) + (jitter > 0 ? ant.getRandom().nextInt(jitter * 2 + 1) - jitter : 0);
            BlockPos cell = openGround(level, colony, x, z);
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    /** The cell to stand in on the ground at this column if it is open ground (see {@link #groundCell}), or null. */
    @Nullable
    private static BlockPos openGround(ServerLevel level, Colony colony, int x, int z) {
        BlockPos center = colony.getCenter();
        if (!LandReady.at(level, new BlockPos(x, center.getY(), z))) {
            return null;
        }
        BlockPos cell = com.antcolonies.colony.GroundTop.cell(level, x, z);
        BlockPos below = cell.below();
        if (!colony.foodMap().mayScout(cell) || !colony.mayForageAt(level, cell)) {
            return null;
        }
        if (Math.abs(cell.getY() - center.getY()) > 6 || !level.getFluidState(cell).isEmpty()
                || !level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || colony.nest().isInside(level, cell)
                || colony.moundParts(level).containsKey(cell)) {
            return null;
        }
        return cell;
    }

    @Override
    public boolean canContinueToUse() {
        if (colony == null || spot == null || ant.hasFullMandibles() || !colony.isForagingTime(ant.level())) {
            return false;
        }
        return ticks < GIVE_UP_TICKS && looking < LOOK_TICKS;
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
            lookRound();
        }
    }

    private void lookRound() {
        float turn = ant.getRandom().nextFloat() * Mth.TWO_PI;
        ant.getLookControl().setLookAt(ant.getX() + Mth.cos(turn) * 3, ant.getY(), ant.getZ() + Mth.sin(turn) * 3);
    }
}
