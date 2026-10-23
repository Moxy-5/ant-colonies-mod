package com.antcolonies.client;

import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Young workers are paler (jobs by age, step 5b): the pale copy of the texture ({@code <texture>_young.png}, drawn by
 * {@code tools/young_textures.py}) is drawn over the body, see-through by how young the worker is
 * ({@link AbstractAntEntity#getYouth}): fully the day it grows up, gone by the end of its time as a nurse.
 */
public class YoungPaleLayer<T extends AbstractAntEntity> extends GeoRenderLayer<T> {
    public YoungPaleLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        float youth = animatable.getYouth();
        if (youth <= 0.01F) {
            return;
        }
        ResourceLocation texture = getTextureResource(animatable);
        ResourceLocation young = texture.withPath(path -> path.replace(".png", "_young.png"));
        RenderType pale = RenderType.entityTranslucent(young);
        int alpha = Math.round(youth * 255.0F);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, pale, bufferSource.getBuffer(pale), partialTick,
                packedLight, packedOverlay, alpha << 24 | 0xFFFFFF);
    }
}
