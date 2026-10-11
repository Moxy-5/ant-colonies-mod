package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Dead ants and the refuse chamber: bodies left where an ant died, carried by the workers to the refuse chamber (one to
 * a cell), and crumbling to dust when nobody can carry them away.
 */
public class Refuse {
    // A dead ant that no worker brings to the refuse chamber crumbles to dust after the profile's
    // colony.profile().refuse().corpseDecayTicks().
    /** How long (in ticks) a worker on its way to a dead ant keeps the others away from it. */
    private static final int CORPSE_CLAIM_TICKS = 1200;

    private final Colony colony;
    private final UUID id;

    /** The dead ants a worker is on its way to: corpse -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> claimedCorpses = new Claims<>();

    Refuse(Colony colony) {
        this.colony = colony;
        this.id = colony.getId();
    }

    /** Puts the body of a dead ant of this colony into the world, where it lies until a worker carries it away. */
    public AntCorpseEntity spawnCorpse(Level level, double x, double y, double z) {
        AntCorpseEntity corpse = new AntCorpseEntity(ModEntities.ANT_CORPSE.get(), level);
        corpse.setColonyId(id);
        corpse.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(corpse);
        return corpse;
    }

    /** All the dead ants of this colony around the nest: in the refuse chamber, or lying where they died. */
    public List<AntCorpseEntity> getCorpses(Level level) {
        return level.getEntitiesOfClass(AntCorpseEntity.class, colony.nestArea(), corpse -> corpse.isAlive() && id.equals(corpse.getColonyId()));
    }

    /** The dead ants that lie in the refuse chamber. */
    public List<AntCorpseEntity> getRefuseCorpses(Level level) {
        Set<BlockPos> cells = new HashSet<>(colony.nest().getRefuseCells(level));
        return getCorpses(level).stream().filter(corpse -> cells.contains(corpse.blockPosition())).toList();
    }

    /** Whether this block is a floor cell of the refuse chamber (so a body lying there is where it belongs and stays). */
    public boolean isInRefuseChamber(Level level, BlockPos pos) {
        return colony.nest().getRefuseCells(level).contains(pos);
    }

    /** A free floor cell of the refuse chamber (one body to a cell), or null if there is none (not dug yet, or full). */
    @Nullable
    public BlockPos pickCorpseSpot(Level level, RandomSource random) {
        Set<BlockPos> taken = new HashSet<>();
        for (AntCorpseEntity corpse : getCorpses(level)) {
            taken.add(corpse.blockPosition());
        }
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos pos : colony.nest().getRefuseCells(level)) {
            if (!taken.contains(pos) && level.getBlockState(pos).isAir()) {
                free.add(pos);
            }
        }
        return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }

    /** A worker sets its dead nestmate down at {@code spot}. False if another body lies there (the worker looks for another spot). */
    public boolean placeCorpse(Level level, BlockPos spot) {
        if (!level.getBlockState(spot).isAir()) {
            return false;
        }
        for (AntCorpseEntity corpse : getCorpses(level)) {
            if (corpse.blockPosition().equals(spot)) {
                return false;
            }
        }
        spawnCorpse(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5).setPlaced(true);
        return true;
    }

    /**
     * The dead ant nearest to {@code near} that a worker should carry to the refuse chamber, or null: only when there is
     * a free spot for it, not one that lies in the chamber already, not one another worker is on its way to.
     * {@code avoid}: bodies this worker found no way to.
     */
    @Nullable
    public AntCorpseEntity findLooseCorpse(Level level, BlockPos near, Set<UUID> avoid) {
        if (pickCorpseSpot(level, level.getRandom()) == null) {
            return null;
        }
        claimedCorpses.prune(level.getGameTime());
        Set<BlockPos> chamber = new HashSet<>(colony.nest().getRefuseCells(level));
        AntCorpseEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntCorpseEntity corpse : getCorpses(level)) {
            if (corpse.isPlaced() || chamber.contains(corpse.blockPosition()) || claimedCorpses.contains(corpse.getUUID())
                    || avoid.contains(corpse.getUUID())) {
                continue;
            }
            double distance = corpse.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = corpse;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A worker is on its way to this dead ant: the others leave it alone for a while. */
    public void claimCorpse(Level level, AntCorpseEntity corpse) {
        claimedCorpses.put(corpse.getUUID(), level.getGameTime() + CORPSE_CLAIM_TICKS);
    }

    public void releaseCorpse(AntCorpseEntity corpse) {
        claimedCorpses.remove(corpse.getUUID());
    }
}
