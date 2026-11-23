package com.antcolonies.savanna.client;

import com.antcolonies.client.FaceLayer;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.OtterEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import org.joml.Vector3d;

/**
 * The otter's model (2026-11-22): its clips as made ({@code tools/otter_models.py game}); on a block move drawn on the
 * move's own path ({@link com.antcolonies.entity.body.MoveRider#drawnShift}, as the monitor and the hippo); its neck and
 * head turned toward what caught its eye ({@link OtterEntity#attention}), shared between the two; its face showing its
 * mood on top of every clip ({@link FaceLayer}, {@code assets/antcolonies/faces/otter.json}, 2026-11-22).
 */
public class OtterModel extends DefaultedEntityGeoModel<OtterEntity> {
    /** Drawn on its path and looking about only within this many blocks of the camera (my pick). */
    private static final double NEAR = 48.0;
    /** How far forward of its middle its eyes are (model pixels), and how far its head turns at most (degrees; my picks). */
    private static final double EYES_AHEAD = 18.4, HEAD_TURN = 55.0;
    /** Its moods' faces (written by {@code python tools/otter_models.py game}). */
    private static final FaceLayer FACE = FaceLayer.of(OtterModel.class, SavannaMod.MODID, "otter");
    /** How much of the mood's tail it shows moving: on land all, afloat a little (the swim's own wave pushes it). */
    private static final double TAIL_LAND = 1.0, TAIL_SWIMMING = 0.3;

    public OtterModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "otter"));
    }

    @Override
    public void setCustomAnimations(OtterEntity otter, long instanceId, AnimationState<OtterEntity> state) {
        super.setCustomAnimations(otter, instanceId, state);
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, otter.xo, otter.getX()), y = Mth.lerp(pt, otter.yo, otter.getY());
        double z = Mth.lerp(pt, otter.zo, otter.getZ());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean onMove = otter.rider().play() != null;
        if (camera.distanceToSqr(x, y, z) > NEAR * NEAR && !onMove) {
            otter.rider().notDrawn();
            return;
        }
        float yaw = Mth.rotLerp(pt, otter.yBodyRotO, otter.yBodyRot);
        Vector3d shift = otter.rider().drawnShift(x, y, z, yaw, otter.rider().clipTicks(otter.bodyClips));
        nudge(getAnimationProcessor().getBone("root"), shift);
        byte doing = otter.doing();
        // (rearing at a playmate it looks straight at it: its body faces it)
        boolean looks = !onMove && !otter.isDeadOrDying() && doing != OtterEntity.REST && doing != OtterEntity.SHAKE
                && doing != OtterEntity.PLAY_UP && doing != OtterEntity.PLAY && doing != OtterEntity.PLAY_DOWN;
        double scale = OtterEntity.MODEL_SCALE * otter.getScale(), ahead = EYES_AHEAD * scale / 16.0;
        otter.attention.aim(pt, x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead, yaw,
                looks ? HEAD_TURN : 0.0);
        if (looks && Math.abs(otter.attention.headAim) > 1.0E-3) {
            turnY(getAnimationProcessor().getBone("neck"), otter.attention.headAim * 0.45);
            turnY(getAnimationProcessor().getBone("head"), otter.attention.headAim * 0.55);
        }
        if (!otter.isDeadOrDying()) {
            // standing, its mood's stance clip carries the tail; the bite and the eat clips carry the jaw
            double tail = otter.standing() || doing != OtterEntity.CALM ? 0.0 : otter.swimming() ? TAIL_SWIMMING : TAIL_LAND;
            boolean jawFree = doing != OtterEntity.BITE && doing != OtterEntity.EAT;
            FACE.draw(otter, getAnimationProcessor()::getBone, new FaceLayer.Frame(OtterEntity.MOODS[otter.mood()],
                    (otter.tickCount + pt) / 20.0, jawFree, tail));
        }
    }

    /** Moves a bone by this much more than its clip has it (model pixels), leaving the marks as they were (the bones are shared). */
    private static void nudge(GeoBone bone, Vector3d by) {
        if (bone == null || by.lengthSquared() < 1.0E-10) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setPosX((float) (bone.getPosX() + by.x));
        bone.setPosY((float) (bone.getPosY() + by.y));
        bone.setPosZ((float) (bone.getPosZ() + by.z));
        if (!marked) {
            bone.resetStateChanges();
        }
    }

    /** Turns a bone this many degrees more about its up axis (toward its right for positive), leaving the marks. */
    private static void turnY(GeoBone bone, double degrees) {
        if (bone == null) {
            return;
        }
        boolean marked = bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged();
        bone.setRotY(bone.getRotY() - (float) Math.toRadians(degrees));
        if (!marked) {
            bone.resetStateChanges();
        }
    }
}
