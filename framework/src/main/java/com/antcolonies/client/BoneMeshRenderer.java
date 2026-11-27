package com.antcolonies.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The renderer every insect and animal of the mods is drawn by: GeckoLib's, with each bone's boxes drawn from faces
 * made ready once ({@link BoneMesh}) in place of GeckoLib's own box drawing, which works every box's turn out again
 * for every animal every frame and makes about 33 small objects a box doing it. The insects had this from 2026-11-09
 * ({@link AntRenderer}); the animals, with more boxes each than any insect, were still drawn the slow way (code review
 * R4-18, 2026-11-26).
 *
 * <p>{@link BoneMesh} leaves out faces buried in another box of the same bone, which is only right while the model's
 * textures are solid on every box face: the savanna mod's unit test {@code BoneMeshTexturesTest} checks every model's
 * textures for it (a mud coat's or any other layer's picture may have holes: it is drawn over the solid one).
 * {@code -Dantcolonies.plainBoxes=true} draws GeckoLib's way, to compare pictures and timings.
 */
public class BoneMeshRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    public BoneMeshRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    /** A bone's boxes from its ready-made faces ({@link BoneMesh}), the buried ones and those turned away from the eye left out. */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight,
            int packedOverlay, int colour) {
        if (BoneMesh.PLAIN) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        } else if (!bone.isHidden()) {
            BoneMesh.draw(poseStack.last(), bone, buffer, packedLight, packedOverlay, colour, BoneMesh.eyeAtTheMiddle());
        }
    }
}
