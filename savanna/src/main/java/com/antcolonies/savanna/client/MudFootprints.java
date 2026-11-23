package com.antcolonies.savanna.client;

import com.antcolonies.client.MudCoatLook;
import com.antcolonies.coat.MudCoat;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Muddy footprints (the user's idea, 2026-11-23): a player wearing mud ({@link MudCoat}) leaves a print of each boot as it
 * comes down, timed to the legs' own swing, so the prints fall in step: left, right, a little to each side of the middle.
 * Wet mud leaves dark, clear prints; as the coat dries they come paler and fainter, and once it is washed off in water
 * there are none. Nothing on water, on the mud itself, while swimming, flying, riding or in spectator. Made on each
 * computer for the players it sees (the coat is already sent to them), so the server does nothing. The print is
 * {@link MudFootprintParticle}.
 */
@EventBusSubscriber(modid = SavannaMod.MODID, value = Dist.CLIENT)
public final class MudFootprints {
    /** The legs' swing per unit of walk (Minecraft's own number, from its player and zombie models). */
    private static final float SWING = 0.6662F;
    /** Prints are made only for players this near the camera (blocks). */
    private static final double NEAR = 48.0;
    /** How far apart the two feet are (blocks from the middle: a Minecraft leg is 4 pixels wide). */
    private static final double SIDE = 0.125;
    /** How dark a print is from wet (0) to dry (7) mud (my picks). */
    private static final float WET_ALPHA = 0.85F, DRY_ALPHA = 0.35F;

    /** Which step each player last printed. */
    private static final Map<Player, Integer> LAST_STEP = new WeakHashMap<>();

    private MudFootprints() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Player player : mc.level.players()) {
            // a step is each half swing of the legs: one foot down, then the other
            int step = Mth.floor(player.walkAnimation.position() * SWING / Mth.PI);
            Integer last = LAST_STEP.put(player, step);
            if (last == null || last == step || MudCoat.coverage(player) == 0 || !printsHere(player)
                    || player.position().distanceToSqr(camera) > NEAR * NEAR) {
                continue;
            }
            boolean left = (step & 1) == 0;
            float yaw = player.yBodyRot + (player.getRandom().nextFloat() - 0.5F) * 10.0F;
            double r = yaw * Mth.DEG_TO_RAD;
            double fx = -Math.sin(r), fz = Math.cos(r);         // forward
            double rx = -fz, rz = fx;                           // to the right
            double side = left ? -SIDE : SIDE;
            double x = player.getX() + fx * 0.08 + rx * side, z = player.getZ() + fz * 0.08 + rz * side;
            int dryness = MudCoat.dryness(player);
            float alpha = Mth.lerp(dryness / 7.0F, WET_ALPHA, DRY_ALPHA);
            int rgb = MudCoatLook.argb(player, 1.0F) & 0xFFFFFF;
            mc.particleEngine.add(new MudFootprintParticle(mc.level, x, player.getY() + 0.012, z, yaw, left, rgb, alpha));
        }
    }

    /** Whether a boot coming down here leaves a print: on firm ground, not in water or the mud itself. */
    private static boolean printsHere(Player player) {
        if (!player.onGround() || player.isSpectator() || player.isPassenger() || player.isInWater() || player.isSwimming()
                || player.isFallFlying() || player.walkAnimation.speed() < 0.05F) {
            return false;
        }
        BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.05, player.getZ());
        BlockState in = player.level().getBlockState(feet), under = player.level().getBlockState(feet.below());
        return !(in.getBlock() instanceof SavannaMudBlock) && !(under.getBlock() instanceof SavannaMudBlock)
                && in.getFluidState().isEmpty();
    }
}
