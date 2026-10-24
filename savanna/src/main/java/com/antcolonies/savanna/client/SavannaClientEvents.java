package com.antcolonies.savanna.client;

import com.antcolonies.client.AntRenderer;
import com.antcolonies.client.InsectModel;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** How the termites are drawn (the split, step S4, out of the shared {@link ClientEvents}). */
@EventBusSubscriber(modid = SavannaMod.MODID, value = Dist.CLIENT)
public class SavannaClientEvents {
    /** The Matabele models' drawing scales, as the animation lab's (tools/anim_lab.py RENDER_SCALE). */
    static final float MATABELE_MAJOR_SCALE = 0.95F;
    static final float MATABELE_MINOR_SCALE = 0.455F;
    static final float MATABELE_QUEEN_SCALE = 1.14F;

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
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_king")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_QUEEN.get(), TermiteQueenRenderer::new);
        event.registerEntityRenderer(SavannaEntities.TERMITE_WORKER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteWorkerEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_worker")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_NYMPH.get(), context -> new TermiteNymphRenderer(context,
                SpeciesBodies.of(SavannaSpecies.MACROTERMES).youngLook()));
        // Soldiers (tools/termite_models.py): each its own file (idle, the walks and turns, head_bang, bite). The major's
        // bigger body is pressed farther toward a wall it climbs, and lifted more so its tail clears the floor (its body
        // plan's climbing numbers, as every insect's).
        event.registerEntityRenderer(SavannaEntities.TERMITE_MINOR_SOLDIER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteSoldierEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_minor_soldier")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        event.registerEntityRenderer(SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), context -> new AntRenderer<>(
                context, new InsectModel<TermiteSoldierEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_major_soldier")))
                .withScale(TermiteQueenModel.RENDER_SCALE));
        // Matabele ants (tools/matabele_models.py, clips by tools/matabele_anims.py), drawn at the model viewer's sizes.
        event.registerEntityRenderer(SavannaEntities.MATABELE_MAJOR.get(), context -> new AntRenderer<>(
                context, new InsectModel<MatabeleEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_major")))
                .withScale(MATABELE_MAJOR_SCALE));
        event.registerEntityRenderer(SavannaEntities.MATABELE_MINOR.get(), context -> new AntRenderer<>(
                context, new InsectModel<MatabeleEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_minor")))
                .withScale(MATABELE_MINOR_SCALE));
        event.registerEntityRenderer(SavannaEntities.MATABELE_QUEEN.get(), context -> new AntRenderer<>(
                context, new InsectModel<com.antcolonies.savanna.entity.MatabeleQueenEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_queen")))
                .withScale(MATABELE_QUEEN_SCALE));
        event.registerEntityRenderer(SavannaEntities.MATABELE_LARVA.get(), context -> new com.antcolonies.client.AntLarvaRenderer(context,
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_larva.png"), SavannaEntities.MATABELE_DRAW_SIZE));
    }
}
