package com.antcolonies.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.entity.load.LoadKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws what the insect holds in its mandibles, by its row's look ({@link LoadLooks}): the soil pellet (a tiny block), a
 * seed or other item, an egg (the same grain the nursery shows, lying across the mandibles), a dead nestmate (the same
 * body the refuse chamber shows, smaller), or a species' own load.
 */
public class CarriedLoadLayer<T extends AbstractAntEntity> extends GeoRenderLayer<T> {
    /*
     * The bone that marks where a load is held: an empty bone of the head, just in front of the mandible tips (every
     * ant and termite model has one, made by tools/ant_models.py and tools/termite_models.py; its body plan names it,
     * the "carry" socket, BodyPlans.isHoldBone), so the load sits in each model's own jaws and moves with the head.
     */
    private final EntityRendererProvider.Context context;
    /** Each row's look, made from this renderer's context the first time the row is drawn (empty: it shows nothing). */
    private final Map<LoadKind, Optional<LoadLooks.Look>> looks = new HashMap<>();

    public CarriedLoadLayer(GeoRenderer<T> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.context = context;
    }

    @Override
    public void renderForBone(PoseStack poseStack, T ant, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        CarriedLoad load = ant.getLoad();
        if (load.isEmpty() || !BodyPlans.isHoldBone(bone.getName())) {
            return;
        }
        LoadLooks.Look look = looks.computeIfAbsent(load.kind(), kind -> {
            Function<EntityRendererProvider.Context, LoadLooks.Look> maker = LoadLooks.makerOf(kind);
            return Optional.ofNullable(maker == null ? null : maker.apply(context));
        }).orElse(null);
        if (look == null) {
            return;
        }
        poseStack.pushPose();
        RenderUtil.translateAndRotateMatrixForBone(poseStack, bone);
        poseStack.pushPose();
        // To the hold bone's pivot (the bones are drawn in the model's own space, in pixels).
        poseStack.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
        look.draw(poseStack, ant, load, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
        poseStack.popPose();
    }
}
