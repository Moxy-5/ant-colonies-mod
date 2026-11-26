package com.antcolonies.savanna.client;

import com.antcolonies.entity.body.DrawClock;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.LionEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The lion's model (2026-11-25, {@code docs/plans/lion.md}; Neon's models, coats and clips, {@code docs/handoff/lion.md}):
 * the male's, the lioness's or the cub's by its form, its coat by its synced coat (the male's Kalahari coat on its own
 * heavier geo), and three things on top of the clips, all drawn only:
 * <ul>
 * <li>Drawn its form's {@code drawAheadPx} (toward its tail: the model's origin is a little behind its feet's middle) and
 * its trunk tilted to the ground under its front and hind feet ({@link StandTilt}, the buffalo's).</li>
 * <li>Its head turned toward what caught its eye ({@link LionEntity#attention}), about 35 degrees at most, slowly; only
 * over its plain loops (the clips that carry the head keep it). Its pupils are the clips' own.</li>
 * <li>Lying and watching a player, its neck lifts about 15 degrees (eased): a lying pride raises its heads to stare and
 * stays down.</li>
 * </ul>
 */
public class LionModel extends DefaultedEntityGeoModel<LionEntity> {
    /** How far the head turns toward what it watches, and how far a lying lion's neck lifts to watch a player (degrees). */
    private static final double HEAD_TURN = 35.0, NECK_LIFT = 15.0;
    /** How quickly the neck's lift eases (seconds). */
    private static final double LIFT_EASE = 0.35;
    public LionModel() {
        super(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "lion"));
    }

    private static ResourceLocation file(String path) {
        return ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, path);
    }

    @Override
    public ResourceLocation getModelResource(LionEntity lion) {
        return file("geo/entity/" + switch (lion.form()) {
            case MALE -> lion.coat() == LionEntity.COAT_THIRD ? "lion_kalahari" : "lion";
            case LIONESS -> "lioness";
            case CUB -> "lion_cub";
        } + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LionEntity lion) {
        byte coat = lion.coat();
        return file("textures/entity/" + switch (lion.form()) {
            case MALE -> coat == LionEntity.COAT_SECOND ? "lion_monarch" : coat == LionEntity.COAT_THIRD ? "lion_kalahari" : "lion";
            case LIONESS -> coat == LionEntity.COAT_SECOND ? "lioness_kalahari" : coat == LionEntity.COAT_THIRD ? "lioness_elder" : "lioness";
            case CUB -> "lion_cub";
        } + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(LionEntity lion) {
        return file("animations/entity/" + lion.form().clips + ".animation.json");
    }

    @Override
    public void setCustomAnimations(LionEntity lion, long instanceId, AnimationState<LionEntity> state) {
        super.setCustomAnimations(lion, instanceId, state);
        float pt = state.getPartialTick();
        double x = Mth.lerp(pt, lion.xo, lion.getX()), y = Mth.lerp(pt, lion.yo, lion.getY());
        double z = Mth.lerp(pt, lion.zo, lion.getZ());
        float yaw = Mth.rotLerp(pt, lion.yBodyRotO, lion.yBodyRot);
        double scale = lion.getScale();
        LionEntity.Measures m = lion.measures;
        new StandTilt(m.feetPx(), m.feetPx(), m.feetOutPx(), m.drawAheadPx(), 30.0).place(lion, lion.drawn, getAnimationProcessor().getBone("root"), scale, x, y, z, yaw, pt);
        head(lion, scale, pt, x, z, yaw);
    }

    /** The head turned toward what caught its eye (over its plain loops only), and a lying lion's neck lifted to watch a player. */
    private void head(LionEntity lion, double scale, float pt, double x, double z, float yaw) {
        boolean turns = lion.faceShows();
        double ahead = lion.measures.eyesAheadPx() * scale / 16.0;
        lion.attention.aim(lion, pt, x - Mth.sin(yaw * Mth.DEG_TO_RAD) * ahead, z + Mth.cos(yaw * Mth.DEG_TO_RAD) * ahead, yaw,
                turns ? HEAD_TURN : 0.0);
        if (!lion.isAlive()) {
            return;
        }
        // the neck's lift: lying still and watching a player
        double want = lion.doing() == LionEntity.REST && lion.attention.on() instanceof Player ? NECK_LIFT : 0.0;
        long now = DrawClock.nanos(lion, pt);
        double dt = lion.neckNanos == 0 ? 1.0 : Mth.clamp((now - lion.neckNanos) / 1.0E9, 0.0, 0.5);
        lion.neckNanos = now;
        double k = 1.0 - Math.exp(-dt / LIFT_EASE);
        lion.drawnNeckLift += (want - lion.drawnNeckLift) * k;
        GeoBone neck = getAnimationProcessor().getBone("neck");
        if (neck == null) {
            return;
        }
        boolean marked = neck.hasRotationChanged() || neck.hasPositionChanged() || neck.hasScaleChanged();
        boolean changed = false;
        if (Math.abs(lion.attention.headAim) > 1.0E-3) {     // eased out after its loops, never dropped (phase 2, step 5)
            neck.setRotY(neck.getRotY() - (float) Math.toRadians(lion.attention.headAim));
            changed = true;
        }
        if (Math.abs(lion.drawnNeckLift) > 0.05) {
            neck.setRotX(neck.getRotX() + (float) Math.toRadians(lion.drawnNeckLift));
            changed = true;
        }
        if (changed && !marked) {
            neck.resetStateChanges();
        }
    }
}
