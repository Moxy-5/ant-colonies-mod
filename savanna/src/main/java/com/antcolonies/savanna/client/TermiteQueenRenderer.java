package com.antcolonies.savanna.client;

import com.antcolonies.client.AntRenderer;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Draws the termite queen; her shadow grows with her. */
public class TermiteQueenRenderer extends AntRenderer<TermiteQueenEntity> {
    public TermiteQueenRenderer(EntityRendererProvider.Context context) {
        super(context, new TermiteQueenModel());
        withScale(TermiteQueenModel.RENDER_SCALE);
    }

    @Override
    public void render(TermiteQueenEntity queen, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        shadowRadius = 0.2F + 0.4F * queen.getSize();
        super.render(queen, yaw, partialTick, pose, buffers, light);
    }
}
