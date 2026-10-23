package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AntEggEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The shared mobs: the egg every species lays (the split, step S4: each species signs up its own bodies, SavannaEntities and DesertEntities). */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AntColoniesMod.MODID);

    // An egg lying in the nursery.
    public static final DeferredHolder<EntityType<?>, EntityType<AntEggEntity>> ANT_EGG =
            ENTITY_TYPES.register("ant_egg", () -> EntityType.Builder
                    .of(AntEggEntity::new, MobCategory.MISC)
                    .sized(0.12F, 0.16F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("ant_egg"));
}
