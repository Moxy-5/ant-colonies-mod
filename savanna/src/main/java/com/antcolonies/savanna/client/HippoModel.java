package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.HippoEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import org.joml.Vector3d;

/**
 * The hippo's model (2026-11-21): its clips as made ({@code tools/hippo_models.py game}), and on a move (up or down a
 * block, out of the shallows; {@link BlockMoves.Play}) drawn on the move's own path, as the monitor's
 * ({@link MonitorModel}): its place worked out from where the move started and how far into the clip it is, not from its
 * box (which steps up all at once at the face), sliding over ({@link #HAND_OVER}) where the drawing hands over between
 * its box and the path instead of jumping.
 */
public class HippoModel extends DefaultedEntityGeoModel<HippoEntity> {
    /** Drawn on its path only within this many blocks of the camera (farther the hand over is too small to see; my pick). */
    private static final double NEAR = 64.0;
    /** How quickly the drawing slides from its box onto the move's path, or back (seconds; the monitor's). */
    private static final double HAND_OVER = 0.12;

    public HippoModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "hippo"));
    }

    @Override
    public void setCustomAnimations(HippoEntity hippo, long instanceId, AnimationState<HippoEntity> state) {
        super.setCustomAnimations(hippo, instanceId, state);
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, hippo.xo, hippo.getX()), y = Mth.lerp(pt, hippo.yo, hippo.getY());
        double z = Mth.lerp(pt, hippo.zo, hippo.getZ());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockMoves.Play climb = hippo.climb();
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR && climb == null) {
            hippo.drawnNanos = 0;
            return;
        }
        float yaw = Mth.rotLerp(pt, hippo.yBodyRotO, hippo.yBodyRot);
        place(hippo, climb, HippoEntity.MODEL_SCALE * hippo.getScale(), x, y, z, yaw);
    }

    /** Where it is drawn: at its box, or on a move, on the move's path, sliding from one to the other ({@link #HAND_OVER}). */
    private void place(HippoEntity hippo, BlockMoves.Play climb, double scale, double x, double y, double z, float yaw) {
        double wx = x, wy = y, wz = z;
        if (climb != null) {
            Vec3 at = HippoEntity.MOVES.drawnAt(climb, Math.max(0.0, hippo.climbClipTicks()), scale / 16.0);
            wx = at.x;
            wy = at.y;
            wz = at.z;
        }
        long now = System.nanoTime();
        boolean climbing = climb != null;
        if (hippo.drawnNanos == 0) {
            hippo.shiftX = hippo.shiftY = hippo.shiftZ = 0;
        } else if (climbing != hippo.drawnClimbing) {
            hippo.shiftX = hippo.drawnX - wx;
            hippo.shiftY = hippo.drawnY - wy;
            hippo.shiftZ = hippo.drawnZ - wz;
        }
        double dt = hippo.drawnNanos == 0 ? 1.0 : (now - hippo.drawnNanos) / 1.0E9;
        double keep = dt <= 0 ? 1.0 : dt > 0.5 ? 0.0 : Math.exp(-dt / HAND_OVER);
        hippo.shiftX *= keep;
        hippo.shiftY *= keep;
        hippo.shiftZ *= keep;
        hippo.drawnClimbing = climbing;
        hippo.drawnNanos = now;
        hippo.drawnX = wx + hippo.shiftX;
        hippo.drawnY = wy + hippo.shiftY;
        hippo.drawnZ = wz + hippo.shiftZ;
        // from the world into the model (the drawing is turned by 180 less its yaw and scaled, as BoneFrames.placement)
        Vector3d m = new Vector3d(hippo.drawnX - x, hippo.drawnY - y, hippo.drawnZ - z).rotateY(-Math.toRadians(180.0 - yaw)).div(scale);
        GeoBone root = getAnimationProcessor().getBone("root");
        if (root == null || m.lengthSquared() < 1.0E-10) {
            return;
        }
        boolean marked = root.hasRotationChanged() || root.hasPositionChanged() || root.hasScaleChanged();
        root.setPosX((float) (root.getPosX() - m.x * 16.0));       // GeckoLib draws a bone's x move the other way
        root.setPosY((float) (root.getPosY() + m.y * 16.0));
        root.setPosZ((float) (root.getPosZ() + m.z * 16.0));
        if (!marked) {
            root.resetStateChanges();
        }
    }
}
