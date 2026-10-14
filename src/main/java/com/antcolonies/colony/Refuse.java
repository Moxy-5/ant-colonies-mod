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
 * The colony's dead: bodies left where an ant or termite died. Ants carry them to the refuse chamber (one to a cell).
 * Species whose profile has {@code refuse.eat_fresh_ticks} (termites, which have no refuse chamber) carry a fresh body
 * into the fungus garden, where it is eaten and becomes a layer of comb, and wall an older one up with soil where it
 * lies. A body nobody deals with crumbles to dust.
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
        AntCorpseEntity corpse = new AntCorpseEntity(colony.getSpecies().equals(com.antcolonies.species.Species.MACROTERMES)
                ? ModEntities.TERMITE_CORPSE.get() : ModEntities.ANT_CORPSE.get(), level);
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

    /** Whether this colony eats its fresh dead and buries the old ones (termites) instead of using a refuse chamber. */
    public boolean eatsOrBuries() {
        return colony.profile().refuse().eatsOrBuries();
    }

    /** Whether the body is still fresh enough to be eaten (only for species that eat their dead). */
    public boolean isFresh(AntCorpseEntity corpse) {
        return colony.profile().refuse().eatFreshTicks().map(ticks -> corpse.getAge() < ticks).orElse(false);
    }

    /**
     * Where a worker carrying a body takes it: a free cell of the refuse chamber, or for species that eat their dead a
     * spot on the fungus comb in a garden. Null if there is none.
     */
    @Nullable
    public BlockPos pickDestination(Level level, RandomSource random) {
        return eatsOrBuries() ? colony.food().pickFoodSpot(level, random) : pickCorpseSpot(level, random);
    }

    /**
     * The worker carrying a body sets it down at {@code spot}: in the refuse chamber, or it is eaten on the comb there
     * (one more layer of comb, like a piece of forage). False if that spot cannot take it (the worker looks for another).
     */
    public boolean setDown(Level level, BlockPos spot) {
        if (!eatsOrBuries()) {
            return placeCorpse(level, spot);
        }
        if (!colony.food().depositSeed(level, spot)) {
            return false;
        }
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.2F, 1.6F);
        return true;
    }

    /** A worker walls an old body up with soil where it lies: the body is gone under a thin layer of earth. */
    public void bury(Level level, AntCorpseEntity corpse) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()), corpse.getX(), corpse.getY() + 0.1, corpse.getZ(), 8, 0.1, 0.05, 0.1, 0.0);
        }
        level.playSound(null, corpse.blockPosition(), net.minecraft.sounds.SoundEvents.GRAVEL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.3F, 1.4F);
        releaseCorpse(corpse);
        corpse.discard();
    }

    /**
     * The dead nestmate nearest to {@code near} that a worker should deal with, or null: not one that lies in the refuse
     * chamber already, not one another worker is on its way to, not one at a spot {@code canGo} rules out (a termite stays
     * in by day). Ants only take one while there is a free spot in the refuse chamber; termites only take a fresh one
     * while there is room on the comb (an old one they bury where it lies). {@code avoid}: bodies this worker found no way to.
     */
    @Nullable
    public AntCorpseEntity findLooseCorpse(Level level, BlockPos near, Set<UUID> avoid, java.util.function.Predicate<BlockPos> canGo) {
        boolean eats = eatsOrBuries();
        if (!eats && pickCorpseSpot(level, level.getRandom()) == null) {
            return null;
        }
        boolean roomToEat = eats && colony.food().pickFoodSpot(level, level.getRandom()) != null;
        claimedCorpses.prune(level.getGameTime());
        Set<BlockPos> chamber = new HashSet<>(colony.nest().getRefuseCells(level));
        AntCorpseEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntCorpseEntity corpse : getCorpses(level)) {
            if (corpse.isPlaced() || chamber.contains(corpse.blockPosition()) || claimedCorpses.contains(corpse.getUUID())
                    || avoid.contains(corpse.getUUID()) || !canGo.test(corpse.blockPosition()) || eats && isFresh(corpse) && !roomToEat) {
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
