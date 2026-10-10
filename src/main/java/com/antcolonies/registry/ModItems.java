package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AntColoniesMod.MODID);

    public static final DeferredItem<BlockItem> BURROWED_DIRT = ITEMS.registerSimpleBlockItem(ModBlocks.BURROWED_DIRT);

    public static final DeferredItem<DeferredSpawnEggItem> WORKER_ANT_SPAWN_EGG =
            ITEMS.register("worker_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.WORKER_ANT, 0x5A1A0C, 0xD9A441, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> QUEEN_ANT_SPAWN_EGG =
            ITEMS.register("queen_ant_spawn_egg",
                    () -> new DeferredSpawnEggItem(ModEntities.QUEEN_ANT, 0x7A2E14, 0x2B1208, new Item.Properties()));
}
