package com.antcolonies.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The renderer every insect and animal of the mods is drawn by: GeckoLib's, with each bone's boxes drawn from faces
 * made ready once ({@link BoneMesh}) in place of GeckoLib's own box drawing, which works every box's turn out again
 * for every animal every frame and makes about 33 small objects a box doing it. The insects had this from 2026-11-09
 * ({@link AntRenderer}); the animals, with more boxes each than any insect, were still drawn the slow way (code review
 * R4-18, 2026-11-26).
 *
 * <p>{@link BoneMesh} leaves out faces buried in another box of the same bone, which is only right while the model's
 * textures are solid on every box face: the savanna mod's unit test {@code BoneMeshTexturesTest} checks every model's
 * textures for it (a mud coat's or any other layer's picture may have holes: it is drawn over the solid one).
 * {@code -Dantcolonies.plainBoxes=true} draws GeckoLib's way, to compare pictures and timings.
 *
 * <p>It is also the one place the in-game camera sets the moment an animal is drawn at (code review R12-43, R12-45,
 * R12-49, 2026-11-27): {@link #render} asks {@link FilmDirector} for the in-between time, moves the animal to its place
 * at that time and hands over to {@link #draw}, which a kind with more to draw (a fish in its jaws, a tongue) overrides
 * in place of {@code render}. An animal drawn by its holder goes through {@link #drawHeld}, so the camera's checks
 * and the recorder know where it really was drawn. Outside films {@code render} only calls {@code draw}.
 */
public class BoneMeshRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    /** While filming: the pose this animal's drawing started from and where it was drawn (for {@link #drawHeld}). */
    private final Matrix4f startPose = new Matrix4f();
    private Vec3 startAt = Vec3.ZERO;
    /**
     * While an animal held by another is being drawn in a film: its frame in its holder's (a point in the held
     * animal's drawn space times this is its place relative to where the holder was drawn), and that place. GeckoLib's
     * own "local space" of a held animal leaves the holder's jaw out (it is measured from the pose its drawing
     * started from).
     */
    private static Matrix4f heldFrame;
    private static Vec3 heldAt;

    public BoneMeshRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    /** The in-between time this kind is drawn at in a film (the insects have their own: {@link AntRenderer}). */
    protected float filmPartial(float gamePartial) {
        return FilmDirector.animalPartial(gamePartial);
    }

    /** Not for overriding (override {@link #draw}): in a film the animal is drawn at the camera's moment, in its place then. */
    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (!FilmDirector.active()) {
            draw(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
        float partial = filmPartial(partialTick);
        poseStack.pushPose();
        if (heldFrame == null) {
            // the game put it where its own in-between time says
            Vec3 at = entity.getPosition(partial);
            Vec3 shift = at.subtract(entity.getPosition(partialTick));
            poseStack.translate(shift.x, shift.y, shift.z);
            startPose.set(poseStack.last().pose());
            startAt = at;
        }
        try {
            draw(entity, yaw, partial, poseStack, buffers, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    /** Draws the animal, and whatever its kind draws with it (override this, never {@link #render}). */
    protected void draw(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    /**
     * Draws an animal this one holds (a fish in its jaws) with its own renderer, in the frame the pose stack is in;
     * called from {@link #draw} after the holder itself is drawn.
     */
    @SuppressWarnings("unchecked")
    protected void drawHeld(Entity held, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        EntityRenderer<Entity> drawer = (EntityRenderer<Entity>) entityRenderDispatcher.getRenderer(held);
        if (!FilmDirector.active()) {
            drawer.render(held, 0.0F, partialTick, poseStack, buffers, packedLight);
            return;
        }
        heldFrame = new Matrix4f(startPose).invert().mul(poseStack.last().pose());
        heldAt = startAt;
        try {
            drawer.render(held, 0.0F, partialTick, poseStack, buffers, packedLight);
        } finally {
            heldFrame = null;
            heldAt = null;
        }
    }

    /** The frame of the held animal being drawn now in its holder's (null when the animal drawn now is not held). */
    static Matrix4f heldFrame() {
        return heldFrame;
    }

    /** Where the holder of the held animal being drawn now was drawn (null when the animal drawn now is not held). */
    static Vec3 heldAt() {
        return heldAt;
    }

    /** A bone's boxes from its ready-made faces ({@link BoneMesh}), the buried ones and those turned away from the eye left out. */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight,
            int packedOverlay, int colour) {
        if (BoneMesh.PLAIN) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        } else if (!bone.isHidden()) {
            BoneMesh.draw(poseStack.last(), bone, buffer, packedLight, packedOverlay, colour, BoneMesh.eyeAtTheMiddle());
        }
    }
}
