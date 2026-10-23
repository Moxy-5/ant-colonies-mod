package com.antcolonies.desert;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.ModEvents;
import com.antcolonies.desert.block.GramaGrassBlock;
import com.antcolonies.desert.entity.QueenAntEntity;
import com.antcolonies.desert.entity.WorkerAntEntity;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.registry.DesertItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/** The desert's game events (the split, step S4, out of the shared {@link ModEvents}): the harvester ants' strength and health, its creative menu rows. */
@EventBusSubscriber(modid = DesertMod.MODID)
public class DesertEvents {
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DesertEntities.WORKER_ANT.get(), WorkerAntEntity.createAttributes().build());
        event.put(DesertEntities.QUEEN_ANT.get(), QueenAntEntity.createAttributes().build());
    }

    /** A grama seeds item that places a grown plant at this stage, named so it can be told apart (for testing). */
    private static ItemStack grama(ItemStack stack, GramaGrassBlock.Stage stage, String name) {
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GramaGrassBlock.STAGE, stage));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(name));
        return stack;
    }

    /** The harvester's rows in the creative menu, round the savanna's (their places keep today's order). */
    static void signUpCreativeTab() {
        ModEvents.addToCreativeTab(CreativeModeTabs.NATURAL_BLOCKS, 10, event -> {
            event.accept(DesertItems.BURROWED_DIRT);
            event.accept(DesertItems.DEEP_SOIL);
            event.accept(DesertItems.SEED_PILE);
            event.accept(DesertItems.BREAD_PILE);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.NATURAL_BLOCKS, 30, event -> {
            // Grama: the seeds (planted, they come up young), and grown plants for testing (leaves wait for rain; ripe for the ants).
            event.accept(DesertItems.BLUE_GRAMA_SEEDS);
            event.accept(grama(DesertItems.BLUE_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.LEAVES, "block.desertcolonies.blue_grama.leaves"));
            event.accept(grama(DesertItems.BLUE_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.RIPE, "block.desertcolonies.blue_grama.ripe"));
            event.accept(DesertItems.BLACK_GRAMA_SEEDS);
            event.accept(grama(DesertItems.BLACK_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.LEAVES, "block.desertcolonies.black_grama.leaves"));
            event.accept(grama(DesertItems.BLACK_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.RIPE, "block.desertcolonies.black_grama.ripe"));
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.SPAWN_EGGS, 10, event -> {
            event.accept(DesertItems.WORKER_ANT_SPAWN_EGG);
            event.accept(DesertItems.QUEEN_ANT_SPAWN_EGG);
        });
    }
}
