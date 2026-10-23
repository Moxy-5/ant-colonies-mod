package com.antcolonies.desert.client;

import com.antcolonies.client.AntLarvaRenderer;
import com.antcolonies.client.AntRenderer;
import com.antcolonies.client.InsectModel;
import com.antcolonies.AntColoniesMod;
import com.antcolonies.desert.entity.QueenAntEntity;
import com.antcolonies.desert.entity.WorkerAntEntity;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** How the harvester ants are drawn (the split, step S4, out of the shared {@link ClientEvents}). */
@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
public class DesertClientEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Looks for: geo/entity/worker_ant.geo.json, animations/entity/worker_ant.animation.json,
        // textures/entity/worker_ant.png
        // (InsectModel: its head turns toward a player it looks at.)
        event.registerEntityRenderer(DesertEntities.WORKER_ANT.get(), context -> new AntRenderer<>(
                context, new InsectModel<WorkerAntEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "worker_ant")))
                .withScale(0.65F));

        event.registerEntityRenderer(DesertEntities.ANT_LARVA.get(), context -> new AntLarvaRenderer(context,
                SpeciesBodies.of(Species.RED_HARVESTER_ANT).youngLook()));

        // Placeholder: the queen reuses the worker model and animations, just bigger and with her own texture (and the
        // live layer: her head look and her waist bending).
        ResourceLocation workerAsset = ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "worker_ant");
        event.registerEntityRenderer(DesertEntities.QUEEN_ANT.get(), context -> new AntRenderer<>(
                context, new InsectModel<QueenAntEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "queen_ant"))
                .withAltModel(workerAsset)
                .withAltAnimations(workerAsset))
                .withScale(1.3F));
    }
}
