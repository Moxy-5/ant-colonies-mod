package com.antcolonies.registry;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.load.CarriedLoad;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The framework's own kinds of synced insect data: the one slot for what an insect carries ({@link CarriedLoad}). */
public class ModDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, ColonyFrameworkMod.MODID);

    static {
        SERIALIZERS.register("carried_load", () -> CarriedLoad.SERIALIZER);
    }
}
