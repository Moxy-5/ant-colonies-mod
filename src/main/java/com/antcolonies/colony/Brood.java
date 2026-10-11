package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * The colony's young: eggs (laid by the queen, carried to the nursery, hatching into larvae) and larvae (fed bread by
 * the nurses, growing into workers, starving without food). The eggs and larvae are real entities in the world; this
 * finds them, picks spots for them and keeps track of who is carrying or feeding which.
 */
public class Brood {
    /** An egg hatches into a larva this many ticks after it was put in the nursery: 1 minute at normal speed. */
    public static final int HATCH_TICKS = 1200;
    /** A larva needs this many pieces of ant bread, fed by the nurses, before it grows into a worker. */
    public static final int LARVA_MEAL = 5;
    /** A larva that has not been fed for this many ticks (2 days) dies: young larvae are the most sensitive. */
    public static final long LARVA_STARVE_TICKS = 48000;
    /** How long (in ticks) a nurse on its way to a larva keeps the other nurses away from it. */
    public static final int LARVA_CLAIM_TICKS = 600;
    /** At most this many young (eggs and larvae, in the nursery or lying about) at the same time; while there are that many the queen is not fed. */
    public static final int MAX_EGGS = 10;
    /** How long (in ticks) a worker on its way to an egg keeps the others away from it. */
    private static final int EGG_CLAIM_TICKS = 600;

    private final Colony colony;
    private final UUID id;

    /** The eggs a worker is on its way to: egg -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> claimedEggs = new Claims<>();

    Brood(Colony colony) {
        this.colony = colony;
        this.id = colony.getId();
    }

    /** Puts an egg of this colony into the world. */
    public AntEggEntity spawnEgg(Level level, double x, double y, double z) {
        AntEggEntity egg = new AntEggEntity(ModEntities.ANT_EGG.get(), level);
        egg.setColonyId(id);
        egg.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(egg);
        return egg;
    }

    /** All the eggs of this colony around the nest: in the nursery, lying where the queen laid them, or dropped on the way. */
    public List<AntEggEntity> getEggs(Level level) {
        return level.getEntitiesOfClass(AntEggEntity.class, colony.nestArea(), egg -> egg.isAlive() && id.equals(egg.getColonyId()));
    }

    public int countEggs(Level level) {
        return getEggs(level).size();
    }

    /** The eggs lying in the nursery. */
    public List<AntEggEntity> getNurseryEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(colony.nest().getNurseryCells(level));
        return getEggs(level).stream().filter(egg -> cells.contains(egg.blockPosition())).toList();
    }

    /** The eggs that no worker has put in its place yet (not in the nursery and not set down by a worker). */
    public List<AntEggEntity> getLooseEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(colony.nest().getNurseryCells(level));
        return getEggs(level).stream().filter(egg -> !egg.isPlaced() && !cells.contains(egg.blockPosition())).toList();
    }

    /** Whether this block is a floor cell of the nursery (so an egg lying there is being kept warm and will hatch). */
    public boolean isInNursery(Level level, BlockPos pos) {
        return colony.nest().getNurseryCells(level).contains(pos);
    }

    /** The egg hatches: a larva takes its place (in the same spot). */
    public boolean hatchEgg(ServerLevel level, AntEggEntity egg) {
        AntLarvaEntity larva = new AntLarvaEntity(ModEntities.ANT_LARVA.get(), level);
        larva.setColonyId(id);
        larva.moveTo(egg.getX(), egg.getY(), egg.getZ(), egg.getYRot(), 0.0F);
        level.addFreshEntity(larva);
        egg.discard();
        return true;
    }

    /** Puts a larva of this colony into the world (used by the tests). */
    public AntLarvaEntity spawnLarva(Level level, double x, double y, double z) {
        AntLarvaEntity larva = new AntLarvaEntity(ModEntities.ANT_LARVA.get(), level);
        larva.setColonyId(id);
        larva.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(larva);
        return larva;
    }

    /** All the larvae of this colony around the nest. */
    public List<AntLarvaEntity> getLarvae(Level level) {
        return level.getEntitiesOfClass(AntLarvaEntity.class, colony.nestArea(), larva -> larva.isAlive() && id.equals(larva.getColonyId()));
    }

    public int countLarvae(Level level) {
        return getLarvae(level).size();
    }

    /** Eggs and larvae together. */
    public int countBrood(Level level) {
        return countEggs(level) + countLarvae(level);
    }

    /** The cells that eggs and larvae lie in (nothing else is put there). */
    Set<BlockPos> getBroodCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        for (AntEggEntity egg : getEggs(level)) {
            cells.add(egg.blockPosition());
        }
        for (AntLarvaEntity larva : getLarvae(level)) {
            cells.add(larva.blockPosition());
        }
        return cells;
    }

    /** How many more pieces of bread all the larvae together still have to eat. */
    public int larvaHunger(Level level) {
        int hunger = 0;
        for (AntLarvaEntity larva : getLarvae(level)) {
            hunger += Math.max(0, LARVA_MEAL - larva.getFed());
        }
        return hunger;
    }

    /**
     * A larva that still has to eat and that no other nurse is on its way to, or null. The nurse {@code ant} is entered
     * as its feeder.
     */
    @Nullable
    public AntLarvaEntity claimHungryLarva(ServerLevel level, UUID ant) {
        long now = level.getGameTime();
        List<AntLarvaEntity> hungry = new ArrayList<>();
        for (AntLarvaEntity larva : getLarvae(level)) {
            if (!larva.isFull() && !larva.hasFeeder(now)) {
                hungry.add(larva);
            }
        }
        if (hungry.isEmpty()) {
            return null;
        }
        AntLarvaEntity larva = hungry.get(level.getRandom().nextInt(hungry.size()));
        larva.claim(ant, now + LARVA_CLAIM_TICKS);
        return larva;
    }

    /**
     * The larva has eaten enough: a new worker takes its place and joins the colony. False (the larva stays) while the
     * colony has {@link Colony#MAX_WORKERS} workers.
     */
    public boolean hatchLarva(ServerLevel level, AntLarvaEntity larva) {
        if (colony.getCount(WorkerAntEntity.CASTE) >= Colony.MAX_WORKERS) {
            return false;
        }
        WorkerAntEntity worker = ModEntities.WORKER_ANT.get().create(level);
        if (worker == null) {
            return false;
        }
        worker.moveTo(larva.getX(), larva.getY(), larva.getZ(), larva.getYRot(), 0.0F);
        worker.joinColony(colony);
        level.addFreshEntity(worker);
        larva.discard();
        return true;
    }

    /** A larva has starved: it dies and leaves a body, which the workers carry to the refuse chamber. */
    public void starveLarva(ServerLevel level, AntLarvaEntity larva) {
        colony.refuse().spawnCorpse(level, larva.getX(), larva.getY(), larva.getZ());
        level.sendParticles(ParticleTypes.SMOKE, larva.getX(), larva.getY() + 0.1, larva.getZ(), 4, 0.05, 0.03, 0.05, 0.01);
        larva.discard();
    }

    /**
     * A free spot for an egg, or null if there is none: in the nursery if any of it is dug out, otherwise (or when the
     * nursery is full) on the floor of the queen's room. The nursery is the preferred place, not a must.
     */
    @Nullable
    public BlockPos pickEggSpot(Level level, RandomSource random) {
        Set<BlockPos> taken = getBroodCells(level);
        Nest nest = colony.nest();
        for (List<BlockPos> cells : List.of(nest.getNurseryCells(level), nest.getHomeRoomCells(level))) {
            List<BlockPos> free = new ArrayList<>();
            for (BlockPos pos : cells) {
                if (!taken.contains(pos) && level.getBlockState(pos).isAir()) {
                    free.add(pos);
                }
            }
            if (!free.isEmpty()) {
                return free.get(random.nextInt(free.size()));
            }
        }
        return null;
    }

    /** A worker puts its egg down at {@code spot}. False if another egg lies there (the worker looks for another spot). */
    public boolean placeEgg(Level level, BlockPos spot) {
        if (!level.getBlockState(spot).isAir()) {
            return false;
        }
        if (getBroodCells(level).contains(spot)) {
            return false;
        }
        spawnEgg(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5).setPlaced(true);
        return true;
    }

    /**
     * The egg nearest to {@code near} that a worker should carry to its place, or null: only when there is a free spot
     * for it, not one that already lies in its place, not one another worker is on its way to. {@code avoid}: eggs
     * this worker found no way to.
     */
    @Nullable
    public AntEggEntity findLooseEgg(Level level, BlockPos near, Set<UUID> avoid) {
        if (pickEggSpot(level, level.getRandom()) == null) {
            return null;
        }
        claimedEggs.prune(level.getGameTime());
        AntEggEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntEggEntity egg : getLooseEggs(level)) {
            if (claimedEggs.contains(egg.getUUID()) || avoid.contains(egg.getUUID())) {
                continue;
            }
            double distance = egg.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = egg;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A worker is on its way to this egg: the others leave it alone for a while. */
    public void claimEgg(Level level, AntEggEntity egg) {
        claimedEggs.put(egg.getUUID(), level.getGameTime() + EGG_CLAIM_TICKS);
    }

    public void releaseEgg(AntEggEntity egg) {
        claimedEggs.remove(egg.getUUID());
    }
}
