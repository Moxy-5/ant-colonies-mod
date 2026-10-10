package com.antcolonies;

import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.ModItems;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = AntColoniesMod.MODID)
public class ModEvents {
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WORKER_ANT.get(), WorkerAntEntity.createAttributes().build());
        event.put(ModEntities.QUEEN_ANT.get(), QueenAntEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.BURROWED_DIRT);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.WORKER_ANT_SPAWN_EGG);
            event.accept(ModItems.QUEEN_ANT_SPAWN_EGG);
        }
    }
}
