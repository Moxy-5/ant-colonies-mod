package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AntColoniesMod.MODID);

    // Hitbox: 0.3 wide x 0.2 tall (smaller than a vanilla tadpole)
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerAntEntity>> WORKER_ANT =
            ENTITY_TYPES.register("worker_ant", () -> EntityType.Builder
                    .of(WorkerAntEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.2F)
                    .build("worker_ant"));
}
