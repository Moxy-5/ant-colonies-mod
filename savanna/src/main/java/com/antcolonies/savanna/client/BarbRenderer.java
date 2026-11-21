package com.antcolonies.savanna.client;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BarbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the river barb (M9 river life, 2026-11-20; {@code tools/barb_models.py game}): the everyday look and the flash
 * (the flank bright silver) while it turns hard or darts off ({@link BarbEntity#flashing}); the body pitched toward
 * where it swims. The death clip rolls it onto its side itself, so Minecraft's own tip-over is off.
 *
 * <p>Its life's looks (2026-11-21, the user's approved designs): a female in her own colours (paler amber fins), plump
 * with eggs while ripe (her own fuller model); a male in his breeding colours while he spawns (deeper orange-red fins,
 * scarlet tail tips, a coppery flush, white bumps on the head); a fry (the young stage) its own glassy model
 * ({@code tools/barb_fry_models.py game}) with its own clips, no flash. A teen is the grown model drawn small.
 */
public class BarbRenderer extends GeoEntityRenderer<BarbEntity> {
    /** The model is 30 pixels long as made (1.875 blocks); a grown barb is drawn 0.25 blocks (my pick), its own size on top. */
    static final float SCALE = 0.25F / 1.875F;

    /** The fry's model is 12 pixels long (0.75 blocks): drawn this much bigger than the grown one's scale, so a fry at
     * 0.32 of grown is 0.08 blocks (the design's size). */
    static final float FRY_SCALE = (0.25F / 0.75F) / SCALE;

    private static final ResourceLocation EVERYDAY = texture("barb");
    private static final ResourceLocation FLASH = texture("barb_flash");
    private static final ResourceLocation FEMALE = texture("barb_female");
    private static final ResourceLocation FEMALE_FLASH = texture("barb_female_flash");
    private static final ResourceLocation PLUMP = texture("barb_plump");
    private static final ResourceLocation PLUMP_FLASH = texture("barb_plump_flash");
    private static final ResourceLocation BREEDING = texture("barb_breeding");
    private static final ResourceLocation BREEDING_FLASH = texture("barb_breeding_flash");
    private static final ResourceLocation FRY = texture("barb_fry");
    private static final ResourceLocation PLUMP_MODEL = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/barb_plump.geo.json");
    private static final ResourceLocation FRY_MODEL = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "geo/entity/barb_fry.geo.json");
    private static final ResourceLocation FRY_CLIPS = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "animations/entity/barb_fry.animation.json");

    public BarbRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(SCALE);
        shadowRadius = 0.08F;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    protected void applyRotations(BarbEntity fish, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick, float nativeScale) {
        super.applyRotations(fish, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        if (fish.isFry()) {
            poseStack.scale(FRY_SCALE, FRY_SCALE, FRY_SCALE);
        }
        float pitch = fish.swimPitch(partialTick);
        if (pitch != 0) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        }
    }

    @Override
    protected float getDeathMaxRotation(BarbEntity fish) {
        return 0;
    }

    @Override
    protected float getShadowRadius(BarbEntity fish) {
        return super.getShadowRadius(fish) * fish.getScale();
    }

    static class Model extends DefaultedEntityGeoModel<BarbEntity> {
        Model() {
            super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "barb"));
        }

        @Override
        public ResourceLocation getModelResource(BarbEntity fish) {
            if (fish.isFry()) {
                return FRY_MODEL;
            }
            return !fish.life().isMale() && fish.dressed() ? PLUMP_MODEL : super.getModelResource(fish);
        }

        @Override
        public ResourceLocation getAnimationResource(BarbEntity fish) {
            return fish.isFry() ? FRY_CLIPS : super.getAnimationResource(fish);
        }

        @Override
        public ResourceLocation getTextureResource(BarbEntity fish) {
            if (fish.isFry()) {
                return FRY;
            }
            boolean flash = fish.flashing();
            if (!fish.life().isMale()) {
                return fish.dressed() ? (flash ? PLUMP_FLASH : PLUMP) : flash ? FEMALE_FLASH : FEMALE;
            }
            return fish.dressed() ? (flash ? BREEDING_FLASH : BREEDING) : flash ? FLASH : EVERYDAY;
        }
    }
}
