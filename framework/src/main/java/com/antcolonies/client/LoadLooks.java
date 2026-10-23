package com.antcolonies.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.entity.load.FrameworkLoads;
import com.antcolonies.entity.load.LoadKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * The client's half of the load list ({@link LoadKind}): how each row is drawn in the jaws ({@link CarriedLoadLayer}).
 * The framework's rows are drawn here; a species signs up the look of its own rows from its client start
 * ({@link #signUp}). A row with no look (food in the crop) shows nothing.
 */
public final class LoadLooks {
    /** Draws a load at the hold point in front of the mandible tips (the pose is already there, in blocks). */
    @FunctionalInterface
    public interface Look {
        void draw(PoseStack pose, AbstractAntEntity carrier, CarriedLoad load, MultiBufferSource buffers, int light, int overlay);
    }

    /** Soil pellet size as a fraction of a full block. */
    private static final float SOIL_SIZE = 0.12F;
    /** A carried item (a seed, bread, forage, a nodule) is drawn this big (as a fraction of a full item). */
    private static final float ITEM_SIZE = 0.3F;
    /** A carried egg is drawn at this fraction of the size it has in the nursery. */
    private static final float EGG_SIZE = 0.6F;
    /** A carried dead ant is drawn at this fraction of the size it has on the floor. */
    private static final float CORPSE_SIZE = 0.6F;
    private static final ResourceLocation EGG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "textures/entity/ant_egg.png");

    private static final Map<LoadKind, Function<EntityRendererProvider.Context, Look>> MAKERS = new HashMap<>();

    static {
        // A soil pellet: a tiny block.
        signUp(FrameworkLoads.SOIL, context -> (pose, carrier, load, buffers, light, overlay) -> {
            pose.scale(SOIL_SIZE, SOIL_SIZE, SOIL_SIZE);
            pose.translate(-0.5F, -0.5F, -0.5F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(load.block(), pose, buffers, light, overlay);
        });
        // A seed, bread, forage or a nodule: the item, small.
        signUp(FrameworkLoads.ITEM, context -> (pose, carrier, load, buffers, light, overlay) -> {
            pose.scale(ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(load.item(), ItemDisplayContext.FIXED, light, overlay,
                    pose, buffers, carrier.level(), carrier.getId());
        });
        // An egg: the same grain the nursery shows, lying across the mandibles.
        signUp(FrameworkLoads.EGG, context -> {
            ModelPart egg = context.bakeLayer(AntEggRenderer.LAYER);
            return (pose, carrier, load, buffers, light, overlay) -> {
                // The egg model stands on its foot point and is 6 pixels tall: lay it along the ant and centre it.
                pose.scale(EGG_SIZE, EGG_SIZE, EGG_SIZE);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.translate(0.0F, -3.0F / 16.0F, 0.0F);
                egg.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(EGG_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            };
        });
        // A dead nestmate: the same body the refuse chamber shows, smaller.
        signUp(FrameworkLoads.CORPSE, context -> {
            ModelPart corpse = context.bakeLayer(AntCorpseRenderer.LAYER);
            return (pose, carrier, load, buffers, light, overlay) -> {
                // The dead ant hangs from the mandibles by its head and trails out in front of the carrier, legs up.
                pose.translate(0.0F, 0.0F, -3.0F / 16.0F);
                pose.scale(CORPSE_SIZE, CORPSE_SIZE, CORPSE_SIZE);
                AntCorpseRenderer.renderModel(corpse, pose, buffers, light,
                        com.antcolonies.species.SpeciesBodies.of(carrier.species()).corpseLook()); // a nestmate: its own species' dead
            };
        });
    }

    private LoadLooks() {
    }

    /**
     * Signs up how a row is drawn: made once per renderer from its context (to bake the models it needs). Species mods
     * load side by side, so the list takes turns ({@code docs/BUGS.md} 64).
     */
    public static synchronized void signUp(LoadKind kind, Function<EntityRendererProvider.Context, Look> maker) {
        MAKERS.put(kind, maker);
    }

    /** How this row is made into a look, or null if it shows nothing. */
    @Nullable
    static synchronized Function<EntityRendererProvider.Context, Look> makerOf(LoadKind kind) {
        return MAKERS.get(kind);
    }
}
