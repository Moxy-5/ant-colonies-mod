package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
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

    /** The held animal (or another player) is drawn turned round the crocodile's spine, the same way as its body. */
    @SubscribeEvent
    public static void onDraw(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity held = event.getEntity();
        if (!(held.getVehicle() instanceof CrocodileEntity croc)) {
            return;
        }
        float pt = event.getPartialTick();
        float roll = croc.rollAngle(pt);
        if (roll == 0.0F) {
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
    }

    /** Which way round the held one turns, to match the crocodile's body in {@link CrocodileModel} (checked in film). */
    static final float ROLL_SIGN = 1.0F;
}
