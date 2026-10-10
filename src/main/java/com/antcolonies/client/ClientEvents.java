package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Looks for: geo/entity/worker_ant.geo.json, animations/entity/worker_ant.animation.json,
        // textures/entity/worker_ant.png
        event.registerEntityRenderer(ModEntities.WORKER_ANT.get(), context -> new AntRenderer<>(
                context, new DefaultedEntityGeoModel<WorkerAntEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "worker_ant")))
                .withScale(0.65F));

        // Placeholder: the queen reuses the worker model and animations, just bigger and with her own texture.
        ResourceLocation workerAsset = ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "worker_ant");
        event.registerEntityRenderer(ModEntities.QUEEN_ANT.get(), context -> new AntRenderer<>(
                context, new DefaultedEntityGeoModel<QueenAntEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "queen_ant"))
                .withAltModel(workerAsset)
                .withAltAnimations(workerAsset))
                .withScale(1.3F));
    }
}
