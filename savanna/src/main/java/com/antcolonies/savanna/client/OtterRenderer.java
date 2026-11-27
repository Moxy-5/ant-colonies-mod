package com.antcolonies.savanna.client;

import com.antcolonies.savanna.animal.HeldCatch;
import com.antcolonies.savanna.animal.OtterEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import com.antcolonies.client.BoneMeshRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the otter (2026-11-22; on a block move along the move's path in {@link OtterModel}): one model and look
 * ({@code tools/otter_models.py game}), drawn at {@link OtterEntity#MODEL_SCALE} of its 2.6-block model, with mud on its
 * fur when it has been in mud. Its death clip lays it on its side, so Minecraft's tip-over is off.
 */
public class OtterRenderer extends BoneMeshRenderer<OtterEntity> {
    /** Its head's place as drawn this frame (noted at the carry bone, a still child of the head), and whether it was drawn. */
    private final Matrix4f headPose = new Matrix4f();
    private final Matrix3f headNormal = new Matrix3f();
    private boolean headSeen;
    /** Its jaw's hinge and the carry point (model pixels) and the jaw's live turn (GeckoLib's radians), this frame. */
    private float hingeX, hingeY, hingeZ, carryX, carryY, carryZ, jawTurn;

    public OtterRenderer(EntityRendererProvider.Context context) {
        super(context, new OtterModel());
        addRenderLayer(new com.antcolonies.client.MudCoatLayer<>(this));
        // the head's place, straight from the drawing as it passes the carry bone (as CatfishRenderer does), and the
        // jaw's hinge and live turn as it passes the jaw
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void renderForBone(PoseStack poseStack, OtterEntity otter, GeoBone bone, RenderType renderType,
                    MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (bone.getName().equals("carry")) {
                    headPose.set(poseStack.last().pose());
                    headNormal.set(poseStack.last().normal());
                    carryX = bone.getPivotX();
                    carryY = bone.getPivotY();
                    carryZ = bone.getPivotZ();
                    headSeen = true;
                } else if (bone.getName().equals("jaw")) {
                    hingeX = bone.getPivotX();
                    hingeY = bone.getPivotY();
                    hingeZ = bone.getPivotZ();
                    jawTurn = bone.getRotX();
                }
            }
        });
        withScale(OtterEntity.MODEL_SCALE);
        shadowRadius = 0.45F;
    }

    /**
     * After drawing itself, the fish in its jaws (2026-11-22, the user: "make it look exactly like the fish in the river
     * it catches with its own animation"): the very fish it caught, drawn by the fish's own renderer (its model, size,
     * colours and its own {@code held} clip, struggling) in the otter's head's frame, gripped as {@link HeldCatch} works
     * out from the angle it was caught at, between the live jaws: its middle at the jaw tips, turned down with the jaw
     * by {@link HeldCatch#ride} (halfway while gripped, lying on the lower jaw when the jaw opens wider, as in a gulp).
     * The fish draws none of its own turn while held ({@link TilapiaRenderer}, {@link BarbRenderer}), so what is placed
     * here is what shows; it is not drawn on its own while held.
     */
    @Override
    public void render(OtterEntity otter, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        headSeen = false;
        super.render(otter, yaw, partialTick, poseStack, buffers, packedLight);
        Entity fish = otter.getFirstPassenger();
        HeldCatch.Fish f = HeldCatch.fish(fish);
        if (!headSeen || f == null) {
            return;
        }
        double otterSize = otter.getScale() * OtterEntity.MODEL_SCALE, fishPx = f.px() * ((LivingEntity) fish).getScale();
        HeldCatch.Grip grip = HeldCatch.grip(f, otter.caughtAt());
        poseStack.pushPose();
        boolean eating = otter.doing() == OtterEntity.EAT;
        if (eating) {
            // eating, it has it down on the ground under its jaws, flat, crosswise to its body, the head chewing over it
            // (the plan's "ground_clips": its jaws stay about this far ahead through the eat clip)
            float otterYaw = Mth.rotLerp(partialTick, otter.yBodyRotO, otter.yBodyRot);
            poseStack.translate(0.0, HeldCatch.thickness(fish) * 0.5, 0.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - otterYaw));        // into its model's frame, as GeckoLib turns it
            poseStack.translate(0.0, 0.0, -HeldCatch.carry().eatAhead() / 16.0 * otterSize);
        } else {
            // in the head's frame (model pixels), turned down about the jaw's hinge as far as the catch rides with the
            // jaw, to the carry point at the jaw tips; then out of the otter's size, the fish keeping its own
            poseStack.last().pose().set(headPose);
            poseStack.last().normal().set(headNormal);
            poseStack.translate(hingeX / 16.0F, hingeY / 16.0F, hingeZ / 16.0F);
            poseStack.mulPose(Axis.XP.rotation((float) HeldCatch.ride(fish, otterSize, jawTurn)));
            poseStack.translate((carryX - hingeX) / 16.0F, (carryY - hingeY) / 16.0F, (carryZ - hingeZ) / 16.0F);
            float undo = (float) (1.0 / otterSize);
            poseStack.scale(undo, undo, undo);
        }
        // across the jaws, its head toward the side it was heading, the long end swept back (HeldCatch.grip), lying on
        // its side (the user: "laying on its side inside of its mouth like what it would be realistically"), so its
        // own clip's side-to-side bend flaps its head and tail up and down
        poseStack.mulPose(Axis.YP.rotationDegrees((float) grip.turn()));
        // (on the ground on its other flank, so its sag curls its ends up off the ground)
        poseStack.mulPose(Axis.ZP.rotationDegrees(eating ? -90.0F : 90.0F));
        poseStack.translate(0.0, -f.middle() * fishPx, -grip.along() * fishPx);      // the gripped point at the teeth
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> drawer = (EntityRenderer<Entity>) entityRenderDispatcher.getRenderer(fish);
        drawer.render(fish, 0.0F, partialTick, poseStack, buffers, packedLight);
        poseStack.popPose();
    }

    @Override
    protected float getDeathMaxRotation(OtterEntity otter) {
        return 0;
    }
}
