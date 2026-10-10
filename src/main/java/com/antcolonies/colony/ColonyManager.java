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

    public void removeColony(UUID id) {
        if (colonies.remove(id) != null) {
            setDirty();
        }
    }

    @Nullable
    public Colony getColony(UUID id) {
        return colonies.get(id);
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
