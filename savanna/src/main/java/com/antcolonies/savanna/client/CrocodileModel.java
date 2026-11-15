package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The crocodile's model with its body lying on what is under it, on top of its clips (the crocodile's life step 1,
 * 2026-11-15, {@link GroundBend}): over a bank's edge the head tips down to the water while the tail still lies on the
 * bank; coming out the front rises onto the bank with the back half afloat.
 */
public class CrocodileModel extends DefaultedEntityGeoModel<CrocodileEntity> {
    /** The bend only within this many blocks of the camera (farther it is too small to see it; my pick). */
    private static final double NEAR = 64.0;

    public CrocodileModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "crocodile"));
    }

    @Override
    public void setCustomAnimations(CrocodileEntity croc, long instanceId, AnimationState<CrocodileEntity> state) {
        super.setCustomAnimations(croc, instanceId, state);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, croc.xo, croc.getX()), y = Mth.lerp(pt, croc.yo, croc.getY()), z = Mth.lerp(pt, croc.zo, croc.getZ());
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR) {
            return;
        }
        GroundBend bend = croc.bend();
        bend.update(croc.level(), x, y, z, Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot), CrocodileEntity.MODEL_SCALE * croc.getScale(),
                croc.isInWater(), croc.floatDepth(), !croc.isDeadOrDying(), System.nanoTime());
        double scale = CrocodileEntity.MODEL_SCALE * croc.getScale();
        GeoBone body = getAnimationProcessor().getBone("body");
        if (body != null && bend.lift() > 1.0E-3) {      // the shoulders lifted onto a bank: the whole body up, in model pixels
            boolean marked = body.hasRotationChanged() || body.hasPositionChanged() || body.hasScaleChanged();
            body.setPosY(body.getPosY() + (float) (bend.lift() * 16.0 / scale));
            if (!marked) {
                body.resetStateChanges();
            }
        }
        int front = bend.frontPieces();
        for (int i = 0; i < CrocodileEntity.BENT_BONES.length; i++) {
            GeoBone bone = getAnimationProcessor().getBone(CrocodileEntity.BENT_BONES[i]);
            if (bone != null) {
                double turn = Math.toRadians(bend.turn(i));
                turnUp(bone, (float) (i < front ? turn : -turn));
            }
        }
    }

    /**
     * Turns a bone this much more about X than the clip has it this frame. The model's bones are shared by every
     * crocodile drawn, and GeckoLib puts a bone back to its clip's pose at the next frame only if nothing marked it as
     * moved, so the marks are left as they were (the insects' lesson, {@code BodyLanguage.turnBy}).
     */
    private static void turnUp(GeoBone bone, float x) {
        if (Math.abs(x) < 1.0E-4F) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotX(bone.getRotX() + x);
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
