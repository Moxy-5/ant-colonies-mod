package com.antcolonies.client;

import com.antcolonies.coat.MudCoat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Mud on a GeckoLib creature (savanna mud step 4; see {@link MudCoatLook}): its masks laid over the body, drawn as the
 * young workers' pale copy is ({@link YoungPaleLayer}). The masks are found by the texture's name, and for a model with
 * none of its own (a crocodile hatchling) by its model's name ({@code geo/entity/<name>.geo.json} gives
 * {@code textures/entity/<name>_mud_*.png}).
 */
public class MudCoatLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
    public MudCoatLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!MudCoatLook.shows(animatable)) {
            return;
        }
        float[] alphas = MudCoatLook.alphas(MudCoat.coverage(animatable), MudCoat.dryness(animatable));
        String[] kinds = {"low", "full"};
        for (int i = 0; i < 2; i++) {
            if (alphas[i] <= 0) {
                continue;
            }
            ResourceLocation mask = maskFor(animatable, kinds[i]);
            if (mask == null) {
                continue;
            }
            RenderType mud = RenderType.entityTranslucent(mask);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, mud, bufferSource.getBuffer(mud), partialTick,
                    packedLight, packedOverlay, MudCoatLook.argb(animatable, alphas[i]));
        }
    }

    private ResourceLocation maskFor(T animatable, String kind) {
        ResourceLocation own = MudCoatLook.mask(getTextureResource(animatable), kind);
        if (own != null) {
            return own;
        }
        ResourceLocation model = getGeoModel().getModelResource(animatable);
        String name = model.getPath().substring(model.getPath().lastIndexOf('/') + 1).replace(".geo.json", "");
        return MudCoatLook.mask(model.withPath("textures/entity/" + name + ".png"), kind);
    }
}
