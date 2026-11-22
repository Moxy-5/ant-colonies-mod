package com.antcolonies.savanna.client;

import com.antcolonies.client.MudCoatLook;
import com.antcolonies.coat.MudCoat;
import com.antcolonies.savanna.SavannaMod;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Mud on a player (savanna mud step 4): Neon's player masks ({@code player_mud_low.png}, legs and boots;
 * {@code player_mud_full.png}, all but the face; the standard 64 by 64 skin layout) drawn over the player in the coat's
 * soil colour, as Minecraft draws a spider's eyes over its body ({@link MudCoatLook} for how much shows).
 */
public class MudPlayerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[] MASKS = {
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/player_mud_low.png"),
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/player_mud_full.png")};

    public MudPlayerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || !MudCoatLook.shows(player)) {
            return;
        }
        float[] alphas = MudCoatLook.alphas(MudCoat.coverage(player), MudCoat.dryness(player));
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);
        for (int i = 0; i < 2; i++) {
            if (alphas[i] > 0) {
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(MASKS[i])), light, overlay,
                        MudCoatLook.argb(player, alphas[i]));
            }
        }
    }
}
