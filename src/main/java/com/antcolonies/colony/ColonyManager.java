package com.antcolonies.colony;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;

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
    private static final String DATA_NAME = AntColoniesMod.MODID + "_colonies";

    private final Map<UUID, Colony> colonies = new LinkedHashMap<>();

    public static ColonyManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ColonyManager::new, ColonyManager::load, null), DATA_NAME);
    }

    public Colony createColony(ResourceLocation species, UUID queenId, BlockPos center) {
        Colony colony = new Colony(UUID.randomUUID(), species, queenId, center);
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
            manager.removeColony(colonyId);
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
                com.antcolonies.AntColoniesMod.LOGGER.info("[colony] queenless colony {} at {} has died out; its nest is abandoned",
                        colony.getId(), colony.getCenter().toShortString());
                removeColony(colony.getId());
            }
        }
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
