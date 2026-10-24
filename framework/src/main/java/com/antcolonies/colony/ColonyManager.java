package com.antcolonies.colony;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;


import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Keeps every colony in one dimension. This is a SavedData, so Minecraft writes it into the
 * world save and loads it back automatically.
 */
public class ColonyManager extends SavedData {
    // The save file keeps its name from before the split (step S6, my pick 3 in docs/plans/mod-split.md).
    private static final String DATA_NAME = "antcolonies_colonies";

    private final Map<UUID, Colony> colonies = new LinkedHashMap<>();

    public static ColonyManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ColonyManager::new, ColonyManager::load, null), DATA_NAME);
    }

    public Colony createColony(ResourceLocation species, UUID queenId, BlockPos center) {
        return createColony(species, UUID.randomUUID(), queenId, center);
    }

    /** As above, with the colony's id chosen beforehand (a colony of new land whose nest was worked out ahead of it). */
    public Colony createColony(ResourceLocation species, UUID colonyId, UUID queenId, BlockPos center) {
        Colony colony = new Colony(colonyId, species, queenId, center);
        colonies.put(colony.getId(), colony);
        setDirty();
        return colony;
    }

    /**
     * A colony's queen has died. The colony carries on without her and dwindles (see {@link Colony#isQueenless}); if
     * nobody is left it ends at once.
     */
    public static void queenDied(ServerLevel level, UUID colonyId) {
        ColonyManager manager = get(level);
        Colony colony = manager.getColony(colonyId);
        if (colony == null) {
            return;
        }
        if (colony.queenDied(level)) {
            manager.markChanged();
        } else {
            manager.colonyEnded(level, colony);
        }
    }

    /**
     * Once a second: every colony whose middle is loaded runs its own clock; a queenless one with nobody left is
     * abandoned. A colony whose middle is out of range lives on as numbers (the brain, part 2: {@link AwayLife#tickAway}).
     */
    public void tickColonies(ServerLevel level) {
        for (Colony colony : java.util.List.copyOf(colonies.values())) {
            if (!colony.away().isHere(level)) {
                colony.away().tickAway(level);
                continue;
            }
            if (!colony.isQueenless() && colony.getQueen(level) == null) {
                continue; // her part of the world is not loaded yet (or a game test has cleared her away)
            }
            if (!colony.tick(level)) {
                com.antcolonies.ColonyFrameworkMod.LOGGER.info("[colony] queenless colony {} at {} has died out; its nest is abandoned",
                        colony.getId(), colony.getCenter().toShortString());
                colonyEnded(level, colony);
            }
        }
    }

    /**
     * The colony has died out: its parts hear it first ({@link ColonyNews#COLONY_ENDED}, so a species can leave its
     * dead nest behind), then it is removed. Its nest stays in the ground.
     */
    public void colonyEnded(ServerLevel level, Colony colony) {
        colony.news().tell(level, ColonyNews.COLONY_ENDED, new ColonyNews.ColonyEnded(colony.getId(), colony.getCenter()));
        removeColony(colony.getId());
    }

    /**
     * For testing (the command {@code /antcolonies kill}): the colony dies out at once, its insects, eggs, young and bodies
     * in loaded land gone (a real queenless colony dwindles over weeks).
     */
    public void kill(ServerLevel level, Colony colony) {
        java.util.List<net.minecraft.world.entity.Entity> members = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (colony.getId().equals(colonyOf(entity))) {
                members.add(entity);
            }
        }
        members.forEach(entity -> entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
        colonyEnded(level, colony);
    }

    /** The colony an insect, egg, larva or body belongs to, or null. */
    @Nullable
    public static UUID colonyOf(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof com.antcolonies.entity.AbstractAntEntity ant) {
            return ant.getColonyId();
        } else if (entity instanceof com.antcolonies.entity.AntEggEntity egg) {
            return egg.getColonyId();
        } else if (entity instanceof com.antcolonies.entity.AntLarvaEntity larva) {
            return larva.getColonyId();
        } else if (entity instanceof com.antcolonies.entity.AntCorpseEntity corpse) {
            return corpse.getColonyId();
        }
        return null;
    }

    /**
     * Tests only: the game was saved and loaded again (the player quit and came back): this copy, loaded from the
     * colony's save, takes the place of the colony with its id.
     */
    public void replaceForTests(Colony copy) {
        colonies.put(copy.getId(), copy);
        setDirty();
    }

    public void removeColony(UUID id) {
        if (colonies.remove(id) != null) {
            setDirty();
        }
    }

    @Nullable
    public Colony getColony(UUID id) {
        return colonies.get(id);
    }

    /** The closest colony of this species whose center is within maxDistance blocks, or null. */
    @Nullable
    public Colony findNearest(BlockPos pos, double maxDistance, ResourceLocation species) {
        Colony best = null;
        double bestDist = maxDistance * maxDistance;
        for (Colony colony : colonies.values()) {
            if (!colony.getSpecies().equals(species) || colony.isQueenless()) {
                continue; // a new worker never joins a colony whose queen has died
            }
            double dist = colony.getCenter().distSqr(pos);
            if (dist <= bestDist) {
                bestDist = dist;
                best = colony;
            }
        }
        return best;
    }

    /** Call after changing a colony's numbers so the change gets saved. */
    public void markChanged() {
        setDirty();
    }

    public Collection<Colony> getColonies() {
        return Collections.unmodifiableCollection(colonies.values());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Colony colony : colonies.values()) {
            list.add(colony.save());
        }
        tag.put("colonies", list);
        return tag;
    }

    private static ColonyManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ColonyManager manager = new ColonyManager();
        ListTag list = tag.getList("colonies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Colony colony = Colony.load(list.getCompound(i));
            manager.colonies.put(colony.getId(), colony);
        }
        return manager;
    }
}
