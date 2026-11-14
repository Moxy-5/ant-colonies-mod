package com.antcolonies.savanna.client;

import com.antcolonies.client.AntRenderer;
import com.antcolonies.client.InsectModel;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
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
    static final float MATABELE_MAJOR_SCALE = 0.633F;
    static final float MATABELE_MINOR_SCALE = 0.455F;
    /**
     * How far from the camera (blocks) a Matabele is drawn with its far model, 54 boxes for 227 (tools/matabele_models.py,
     * model(far=True); AntRenderer.withFarLook): where the full model's teeth, rounded edges and plate rims are under a
     * pixel on a 1080-line screen. My picks, 2026-11-09, from side-by-side pictures (tools/film/far_look.film).
     */
    static final float MATABELE_MAJOR_FAR = 7.0F;
    static final float MATABELE_MINOR_FAR = 4.5F;
    static final float MATABELE_QUEEN_SCALE = 0.76F;
    /** Carried termite leftovers are drawn this big (as a fraction of a full block). */
    private static final float REMAINS_SIZE = 0.3F;
    /**
     * Real sizes (2026-11-05): a carried termite is drawn a real Macrotermes worker's length on the scale
     * ({@code InsectSizes.TERMITE_WORKER}, 0.15 blocks; its model 8 pixels long), however big its carrier.
     */
    private static final float CARRIED_TERMITE_LENGTH = com.antcolonies.savanna.species.InsectSizes.TERMITE_WORKER.length();
    private static final ResourceLocation CARRIED_TERMITE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/carried_termite.png");

    /** The doum palm's sculpted pieces (floodplain trees step 5): the model files' loader. */
    @SubscribeEvent
    public static void onRegisterGeometry(net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
        event.register(DoumModel.LOADER, DoumModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TermiteNymphRenderer.LAYER, TermiteNymphRenderer::createLayer);
    }

    /** One carried termite (textures from tools/carried_termite_art.py): the soft body, 6 pixels long, and the head. */
    private static net.minecraft.client.model.geom.ModelPart carriedTermite() {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
        mesh.getRoot().addOrReplaceChild("termite", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F)
                .texOffs(0, 4).addBox(-4.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), net.minecraft.client.model.geom.PartPose.ZERO);
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }
    /** The camel thorn and baobab sign pictures (Minecraft makes the sign list once; this adds ours if it was early). */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> com.antcolonies.savanna.registry.TreeBlocks.WOOD_SETS
                .forEach(set -> net.minecraft.client.renderer.Sheets.addWoodType(set.type())));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // M8 step 1e: termite leftovers carried out to the heap: a small heap of them in the jaws.
        com.antcolonies.client.LoadLooks.signUp(com.antcolonies.savanna.entity.MatabeleLoads.TERMITE_REMAINS, context -> (pose, carrier, load, buffers, light, overlay) -> {
            pose.scale(REMAINS_SIZE, REMAINS_SIZE, REMAINS_SIZE);
            pose.translate(-0.5F, -0.3F, -0.5F);
            net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(SavannaBlocks.TERMITE_REMAINS.get().defaultBlockState()
                    .setValue(com.antcolonies.block.SeedPileBlock.LAYERS, 3), pose, buffers, light, overlay);
        });
        // M8 step 3c: paralysed termites carried home from a raid: held crosswise under the jaws in rows of two, one row
        // under the other, the heads to alternate sides (real Megaponera majors carry a bundle so, minors one to three);
        // always a real termite's size, however big the carrier.
        com.antcolonies.client.LoadLooks.signUp(com.antcolonies.savanna.entity.MatabeleLoads.TERMITES, context -> {
            net.minecraft.client.model.geom.ModelPart termite = carriedTermite();
            return (pose, carrier, load, buffers, light, overlay) -> {
                // (the pose is the carrier's drawn size: its renderer's scale times its own; the termite 8 pixels long)
                float carrierScale = (carrier instanceof MatabeleEntity ant && !ant.hasMajorBody() ? MATABELE_MINOR_SCALE : MATABELE_MAJOR_SCALE)
                        * Math.max(0.05F, carrier.getScale());
                float k = CARRIED_TERMITE_LENGTH / (8.0F / 16.0F * carrierScale);
                float thick = 2.0F / 16.0F * k;
                int count = load.item().getCount();
                com.mojang.blaze3d.vertex.VertexConsumer consumer = buffers.getBuffer(
                        net.minecraft.client.renderer.RenderType.entityCutoutNoCull(CARRIED_TERMITE_TEXTURE));
                for (int i = 0; i < count; i++) {
                    int row = i / 2;
                    int col = i % 2;
                    pose.pushPose();
                    pose.translate(0.0F, -(0.1F + row) * thick, count > 1 ? (col - 0.5F) * thick : 0.0F);
                    if (col == 1) {
                        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
                    }
                    pose.scale(k, k, k);
                    termite.render(pose, consumer, light, overlay);
                    pose.popPose();
                }
            };
        });
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
                .withFarLook(context, InsectModel.farLook(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_major"),
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_major_far")), MATABELE_MAJOR_FAR)
                .withScale(MATABELE_MAJOR_SCALE));
        event.registerEntityRenderer(SavannaEntities.MATABELE_MINOR.get(), context -> new AntRenderer<>(
                context, new InsectModel<MatabeleEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_minor")))
                .withFarLook(context, InsectModel.farLook(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_minor"),
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_minor_far")), MATABELE_MINOR_FAR)
                .withScale(MATABELE_MINOR_SCALE));
        event.registerEntityRenderer(SavannaEntities.MATABELE_QUEEN.get(), context -> new AntRenderer<>(
                context, new InsectModel<com.antcolonies.savanna.entity.MatabeleQueenEntity>(
                        ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_queen")))
                .withScale(MATABELE_QUEEN_SCALE));
        event.registerEntityRenderer(SavannaEntities.TILAPIA.get(), TilapiaRenderer::new);
        event.registerEntityRenderer(SavannaEntities.MATABELE_LARVA.get(), context -> new com.antcolonies.client.AntLarvaRenderer(context,
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_larva.png"), SavannaEntities.MATABELE_DRAW_SIZE));
    }
}
