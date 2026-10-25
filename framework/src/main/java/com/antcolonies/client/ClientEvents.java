package com.antcolonies.client;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.SpeciesBodies;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The shared shapes drawn on the screen: the egg, the larva's and the dead's models, and every species' dead in its
 * own look ({@link SpeciesBodies#corpseLook}). The split, step S4: each species draws its own insects and young in
 * {@link SavannaClientEvents} and {@link DesertClientEvents}.
 */
@EventBusSubscriber(modid = ColonyFrameworkMod.MODID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AntEggRenderer.LAYER, AntEggRenderer::createLayer);
        event.registerLayerDefinition(AntLarvaRenderer.LAYER, AntLarvaRenderer::createLayer);
        event.registerLayerDefinition(AntCorpseRenderer.LAYER, AntCorpseRenderer::createLayer);
        event.registerLayerDefinition(LostLegRenderer.LAYER, LostLegRenderer::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ANT_EGG.get(), AntEggRenderer::new);
        event.registerEntityRenderer(ModEntities.LOST_LEG.get(), LostLegRenderer::new);
        for (SpeciesBodies bodies : SpeciesBodies.all()) {
            event.registerEntityRenderer(bodies.corpse().get(), context -> new AntCorpseRenderer(context, bodies.corpseLook(), bodies.drawSize()));
        }
    }
}
