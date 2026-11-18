package com.antcolonies.animal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * A care hook (the animal life kit, {@code docs/plans/animal-life-cycle.md}): young carried by a parent, the tilapia's
 * mouth, the crab's flap, the crocodile's jaws. A carried young leaves the world and rides along as its saved data,
 * costing nothing, and is let out again whole where the parent is. The parent saves this with its own data; each
 * species draws what it carries its own way ({@link #count}).
 */
public final class Carried {
    private final List<CompoundTag> riders = new ArrayList<>();

    /** Takes a young into the parent's care: it leaves the world. False if it cannot be carried. */
    public boolean take(Entity young) {
        CompoundTag tag = new CompoundTag();
        if (!young.saveAsPassenger(tag)) {
            return false;
        }
        riders.add(tag);
        young.discard();
        return true;
    }

    /** How many it carries. */
    public int count() {
        return riders.size();
    }

    public boolean isEmpty() {
        return riders.isEmpty();
    }

    /** Lets every carried young out at this place; returns those let out. */
    public List<Entity> letOut(ServerLevel level, Vec3 at) {
        List<Entity> out = new ArrayList<>();
        for (CompoundTag tag : riders) {
            Entity young = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
                return e;
            });
            if (young != null && level.addFreshEntity(young)) {
                out.add(young);
            }
        }
        riders.clear();
        return out;
    }

    /** Saves what it carries into the parent's data. */
    public void save(CompoundTag tag, String key) {
        if (riders.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        list.addAll(riders);
        tag.put(key, list);
    }

    /** Loads what it carries from the parent's data. */
    public void load(CompoundTag tag, String key) {
        riders.clear();
        for (Tag t : tag.getList(key, Tag.TAG_COMPOUND)) {
            riders.add((CompoundTag) t);
        }
    }
}
