package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.SavannaMudBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * How savanna mud is drawn (2026-11-21, {@code docs/plans/savanna-mud.md}): Neon's grey looks tinted by the soil's tone and
 * how wet it is ({@link SavannaMudBlock#tint}), and the screen gone brown while the camera is under the mud's surface (as
 * powder snow's white).
 */
@EventBusSubscriber(modid = SavannaMod.MODID, value = Dist.CLIENT)
public final class SavannaMudClient {
    /** How thick the brown is with the eyes under the mud (out of 255; my pick: you see shapes, barely). */
    private static final int UNDER_ALPHA = 225;

    private SavannaMudClient() {
    }

    @SubscribeEvent
    public static void onBlockColours(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> SavannaMudBlock.tint(state), SavannaBlocks.SAVANNA_MUD.get());
    }

    @SubscribeEvent
    public static void onItemColours(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFF000000 | SavannaMudBlock.tint(SavannaBlocks.SAVANNA_MUD.get().defaultBlockState()),
                SavannaItems.SAVANNA_MUD.get());
    }

    /** The coats' colours (mud step 4): Neon's soil tints, wet to dry ({@link SavannaMudBlock.Tone#colour}). */
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.antcolonies.client.MudCoatLook.tint = (tone, dryness) -> SavannaMudBlock.Tone.values()[tone & 3].colour(dryness, 1.0F);
    }

    /** Mud on players (mud step 4): Neon's player masks over both skins. */
    @SubscribeEvent
    public static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new MudPlayerLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "under_mud"),
                SavannaMudClient::drawUnderMud);
    }

    private static void drawUnderMud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        BlockState at = minecraft.gameRenderer.getMainCamera().getBlockAtCamera();
        if (!(at.getBlock() instanceof SavannaMudBlock) || minecraft.player != null && minecraft.player.isSpectator()) {
            return;
        }
        int colour = SavannaMudBlock.tint(at) & 0xFFFFFF;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (UNDER_ALPHA << 24) | colour);
    }
}
