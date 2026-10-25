package com.antcolonies.registry;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.LostLegEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The shared mobs: the egg every species lays and the legs bitten off in fights (the split, step S4: each species signs up its own bodies, SavannaEntities and DesertEntities). */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ColonyFrameworkMod.MODID);

    // An egg lying in the nursery.
    public static final DeferredHolder<EntityType<?>, EntityType<AntEggEntity>> ANT_EGG =
            ENTITY_TYPES.register("ant_egg", () -> EntityType.Builder
                    .of(AntEggEntity::new, MobCategory.MISC)
                    .sized(0.12F, 0.16F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("ant_egg"));

    // A piece of leg bitten off in a fight, lying on the ground a while (M8 step 2d).
    public static final DeferredHolder<EntityType<?>, EntityType<LostLegEntity>> LOST_LEG =
            ENTITY_TYPES.register("lost_leg", () -> EntityType.Builder
                    .<LostLegEntity>of(LostLegEntity::new, MobCategory.MISC)
                    .sized(0.15F, 0.05F)
                    .clientTrackingRange(6)
                    .updateInterval(10)
                    .build("lost_leg"));
}
