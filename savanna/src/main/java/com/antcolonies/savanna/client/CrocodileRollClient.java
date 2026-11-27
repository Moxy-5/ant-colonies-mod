package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * The death roll as seen (2026-11-15): whatever a rolling crocodile holds in its jaws turns with it round the
 * crocodile's spine, and a player it holds sees the world spin (the camera rolls with it; the user's ask).
 */
@EventBusSubscriber(modid = SavannaMod.MODID, value = Dist.CLIENT)
public final class CrocodileRollClient {
    private CrocodileRollClient() {
    }

    /** The held player's view spins with the roll. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Entity viewer = event.getCamera().getEntity();
        if (viewer != null && viewer.getVehicle() instanceof CrocodileEntity croc) {
            float roll = croc.rollAngle((float) event.getPartialTick());
            if (roll != 0.0F) {
                event.setRoll(event.getRoll() + roll);
            }
        }
    }

    /**
     * The held one whose drawing frame is turned now, and that frame: saved before the turn in {@link #onDraw} and
     * put back in {@link #putBack} once its body is drawn (review R4-24).
     */
    private static LivingEntity turned;
    private static PoseStack turnedFrame;

    /**
     * The held animal (or another player) is drawn turned round the crocodile's spine, the same way as its body.
     * Only its body and what it wears: the frame is saved here first and put back before its name, and so before its
     * shadow and its flames (review R4-24: Minecraft draws those after the body with the same frame, and nothing put
     * the frame back, so a held player's shadow stood on edge in the air and spun round in the roll). Last of all
     * handlers, so none after it can call the drawing off with the frame still saved.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDraw(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity held = event.getEntity();
        if (!(held.getVehicle() instanceof CrocodileEntity croc)) {
            return;
        }
        float pt = event.getPartialTick();
        float roll = croc.rollAngle(pt);
        if (roll == 0.0F && !(held instanceof Player) && croc.jaws() != CrocodileEntity.JAWS_DRAG) {
            return; // (not turned at all)
        }
        turned = held;
        turnedFrame = event.getPoseStack();
        turnedFrame.pushPose();
        if (roll == 0.0F) {
            if (held instanceof Player) {
                layAlong(event, croc, held, pt);
            } else if (croc.jaws() == CrocodileEntity.JAWS_DRAG) {
                struggle(event, croc, pt);
            }
            return;
        }
        // the pose is already at the held one's drawn place: turn round the line through the crocodile's spine
        double cx = Mth.lerp(pt, croc.xo, croc.getX()), cy = Mth.lerp(pt, croc.yo, croc.getY()) + CrocodileEntity.ROLL_AXIS * croc.getScale();
        double cz = Mth.lerp(pt, croc.zo, croc.getZ());
        double hx = Mth.lerp(pt, held.xo, held.getX()), hy = Mth.lerp(pt, held.yo, held.getY()), hz = Mth.lerp(pt, held.zo, held.getZ());
        float yaw = Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot) * Mth.DEG_TO_RAD;
        Vector3f forward = new Vector3f(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
        float dx = (float) (cx - hx), dy = (float) (cy - hy), dz = (float) (cz - hz);
        // only the part of the gap across the spine matters (any point on the spine line will do)
        float along = dx * forward.x + dz * forward.z;
        dx -= forward.x * along;
        dz -= forward.z * along;
        var pose = event.getPoseStack();
        pose.translate(dx, dy, dz);
        pose.mulPose(Axis.of(forward).rotationDegrees(ROLL_SIGN * roll));
        pose.translate(-dx, -dy, -dz);
        if (held instanceof Player) {
            layAlong(event, croc, held, pt);
        }
    }

    /** The name is drawn after the body, and the shadow and the flames after the name: all three upright. */
    @SubscribeEvent
    public static void onName(RenderNameTagEvent event) {
        putBack(event.getEntity());
    }

    /** Should the name's turn never have come: the frame is put back when the drawing ends. */
    @SubscribeEvent
    public static void onDrawn(RenderLivingEvent.Post<?, ?> event) {
        putBack(event.getEntity());
    }

    private static void putBack(Entity drawn) {
        if (drawn == turned && turnedFrame != null) {
            turnedFrame.popPose();
            turned = null;
            turnedFrame = null;
        }
    }

    /**
     * A held player (2026-11-15, the user's ask, for a server with friends): laid flat along the line of the
     * crocodile's snout, gripped round the legs with the body and head sticking out in front, twisting from side to
     * side; its arms and legs flail (the crocodile sets its walk swinging, {@link CrocodileEntity#tick}). Turned about
     * the player's middle, which is held at the height of the crocodile's spine, so the roll spins it like a spit.
     */
    private static void layAlong(RenderLivingEvent.Pre<?, ?> event, CrocodileEntity croc, LivingEntity held, float pt) {
        float time = croc.tickCount + pt;
        float yaw = Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot) * Mth.DEG_TO_RAD;
        float half = held.getBbHeight() * 0.5F;
        var pose = event.getPoseStack();
        pose.translate(0.0F, half, 0.0F);
        // about the line across the snout: the head tipped forward, away from the crocodile, until the body lies level
        pose.mulPose(Axis.of(new Vector3f(Mth.cos(yaw), 0.0F, Mth.sin(yaw))).rotationDegrees(90.0F + 8.0F * Mth.sin(time * Mth.TWO_PI / 9.0F)));
        pose.mulPose(Axis.YP.rotationDegrees(14.0F * Mth.sin(time * Mth.TWO_PI / 6.0F)));
        pose.translate(0.0F, -half, 0.0F);
    }

    /**
     * The tug of war on the bank (2026-11-15, the user's ask): the animal held by the head digs in and leans back
     * from the crocodile, haunches down, rocking with each heave ({@link CrocodileEntity#HEAVE_TICKS}), and throws
     * itself from side to side (my picks: 12 degrees of lean, 6 more in the heave, 7 each way).
     */
    private static void struggle(RenderLivingEvent.Pre<?, ?> event, CrocodileEntity croc, float pt) {
        float time = croc.tickCount + pt;
        float lean = 12.0F + 6.0F * Mth.sin(time * Mth.TWO_PI / CrocodileEntity.HEAVE_TICKS);
        float thrash = 7.0F * Mth.sin(time * Mth.TWO_PI / 7.0F);
        float yaw = Mth.rotLerp(pt, croc.yBodyRotO, croc.yBodyRot) * Mth.DEG_TO_RAD;
        // about the line across the crocodile's snout, through the animal's feet: its top away from the crocodile
        var pose = event.getPoseStack();
        pose.mulPose(Axis.of(new Vector3f(Mth.cos(yaw), 0.0F, Mth.sin(yaw))).rotationDegrees(lean));
        pose.mulPose(Axis.YP.rotationDegrees(thrash));
    }

    /**
     * Which way round the held one turns, to match the crocodile's body in {@link CrocodileModel}. The body bone turns
     * about the model's z, which GeckoLib draws turned by 180 less its yaw, so pointing backward along the crocodile:
     * the held one, turned about the line pointing forward, must go the other way round (it was 1, and the animals
     * rolled against the crocodile: the user's report, 2026-11-15).
     */
    static final float ROLL_SIGN = -1.0F;
}
