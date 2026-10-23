package com.antcolonies.savanna.client;

import com.antcolonies.client.AntRenderer;
import com.antcolonies.client.InsectModel;
import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** How the termites are drawn (the split, step S4, out of the shared {@link ClientEvents}). */
@EventBusSubscriber(modid = AntColoniesMod.MODID, value = Dist.CLIENT)
public class SavannaClientEvents {
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TermiteNymphRenderer.LAYER, TermiteNymphRenderer::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Termites (placeholder models made by tools/termite_models.py). Each has its own animation file of the same name,
        // its walks made for its own legs (2026-10-18); the queen's swelling and pulsing are done in TermiteQueenModel.
        // (InsectModel: the live layer, its abdomen following the path over edges and round turns; termites are blind and
        // never turn their heads to look.)
        event.registerEntityRenderer(SavannaEntities.TERMITE_KING.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteKingEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_king")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_QUEEN.get(), TermiteQueenRenderer::new);
        event.registerEntityRenderer(SavannaEntities.TERMITE_WORKER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteWorkerEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_worker")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_NYMPH.get(), context -> new TermiteNymphRenderer(context,
                SpeciesBodies.of(Species.MACROTERMES).youngLook()));
        // Soldiers (tools/termite_models.py): each its own file (idle, the walks and turns, head_bang, bite). The major's
        // bigger body is pressed farther toward a wall it climbs, and lifted more so its tail clears the floor (its body
        // plan's climbing numbers, as every insect's).
        event.registerEntityRenderer(SavannaEntities.TERMITE_MINOR_SOLDIER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteSoldierEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_minor_soldier")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteSoldierEntity>(
                        ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_major_soldier")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
    }
}
