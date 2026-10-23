package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.TermiteQueenEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;

/**
 * The termite queen's model (tools/termite_models.py): the king's head, thorax and legs, and an abdomen of six
 * segments. As she swells (0 to 1, {@link TermiteQueenEntity#getSize}) each segment's pale skin stretches longer, wider
 * and taller, while its dark back plate and lighter belly plate keep their size and stay on the outside of the skin, so
 * the plates drift apart into brown islands on a cream body, as in a real Macrotermes queen. Full grown she is about
 * 2 blocks long, centred on where she lies. The abdomen sinks to the floor as it grows, and slow waves run along it
 * from front to back (the "pulse"), stronger the bigger she is. The live layer ({@link InsectModel}) bends her abdomen
 * over edges while she is small enough to walk.
 */
public class TermiteQueenModel extends InsectModel<TermiteQueenEntity> {
    /** The renderer's scale for the king and the queen (the model is drawn at 3/4 size). */
    public static final float RENDER_SCALE = 0.75F;

    // The abdomen as built by tools/termite_models.py (model pixels). Keep in step with that script.
    private static final float SEG_LENGTH = 0.65F;
    private static final float HEIGHT = 2.1F;
    private static final float CENTER_Y = 1.8F;
    private static final float[] TAPER = {0.8F, 0.95F, 1.0F, 1.0F, 0.93F, 0.75F};

    /**
     * Full grown, the skin is this many times longer, wider and taller than at founding: she is then about 2 blocks
     * long and four and a half times as long as wide, like a real queen.
     */
    private static final float GROW_LENGTH = 9.5F;
    private static final float GROW_WIDTH = 3.0F;
    private static final float GROW_HEIGHT = 3.4F;
    /** How far the bottom of the abdomen sinks as she grows (it ends up lying on the floor). */
    private static final float SINK = 0.7F;
    /**
     * Between these sizes she settles, like a real queen grown too heavy for her legs: her abdomen comes to rest on
     * the floor and carries her, and her head and thorax rise to sit on its front end, legs dangling.
     */
    private static final float SETTLE_FROM = 0.45F;
    private static final float SETTLE_TO = 0.65F;
    /** The height of the middle of the thorax (model pixels), and how far up the front of the abdomen it ends up. */
    private static final float THORAX_Y = 1.7F;
    private static final float THORAX_HOLD = 0.55F;
    /**
     * From DROOP_FROM to DROOP_TO her front (the forebody bone: head, thorax and legs) tips forward and down around the
     * joint with the abdomen, up to DROOP_DEGREES, and stays so: hanging off the front of the abdomen.
     */
    private static final float DROOP_FROM = 0.65F;
    private static final float DROOP_TO = 0.75F;
    private static final float DROOP_DEGREES = 30.0F;

    public TermiteQueenModel() {
        super(ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_queen"));
    }

    @Override
    public void setCustomAnimations(TermiteQueenEntity queen, long instanceId, AnimationState<TermiteQueenEntity> state) {
        super.setCustomAnimations(queen, instanceId, state);
        float size = queen.getSize();
        float length = 1.0F + (GROW_LENGTH - 1.0F) * size;
        float width = 1.0F + (GROW_WIDTH - 1.0F) * size;
        float height = 1.0F + (GROW_HEIGHT - 1.0F) * size;
        // The middle of the abdomen rises as it gets taller, while its bottom sinks to the floor.
        float bottom = CENTER_Y - HEIGHT / 2.0F - SINK * size;
        float rise = bottom + HEIGHT * height / 2.0F - CENTER_Y;
        // The whole body slides forward by half of what the abdomen grew, so she stays centred on her spot (a long
        // queen in the middle of her chamber would otherwise push her tail through the wall).
        float forward = SEG_LENGTH * TAPER.length * (length - 1.0F) / 2.0F;
        // Settling: the head, thorax and legs (all of the body) are lifted onto the front of the abdomen, and the
        // abdomen is lowered by as much, so it stays on the floor.
        float t = Mth.clamp((size - SETTLE_FROM) / (SETTLE_TO - SETTLE_FROM), 0.0F, 1.0F);
        float settle = t * t * (3.0F - 2.0F * t);
        float lift = settle * Math.max(0.0F, bottom + HEIGHT * height * THORAX_HOLD - THORAX_Y);
        getBone("body").ifPresent(bone -> {
            bone.setPosY(lift);
            bone.setPosZ(-forward);
        });
        float d = Mth.clamp((size - DROOP_FROM) / (DROOP_TO - DROOP_FROM), 0.0F, 1.0F);
        float droop = DROOP_DEGREES * d * d * (3.0F - 2.0F * d);
        // A negative tilt about X puts the head (which points to -Z) down.
        getBone("forebody").ifPresent(bone -> bone.setRotX(-droop * Mth.DEG_TO_RAD));
        float time = queen.tickCount + state.getPartialTick();
        float strength = 0.015F + 0.05F * size;
        for (int i = 0; i < TAPER.length; i++) {
            int n = i + 1;
            float pulse = 1.0F + strength * Mth.sin(time * 0.2F - i * 0.9F);
            float slide = SEG_LENGTH * (length - 1.0F) * (i + 0.5F); // the segments in front of it got longer
            float plateLift = HEIGHT * TAPER[i] * (height * pulse - 1.0F) / 2.0F;
            float finalPulse = pulse;
            getBone("seg_" + n).ifPresent(bone -> {
                bone.setPosY(rise - lift);
                bone.setPosZ(slide);
            });
            getBone("seg_" + n + "_skin").ifPresent(bone -> {
                bone.setScaleX(width * finalPulse);
                bone.setScaleY(height * finalPulse);
                bone.setScaleZ(length);
            });
            getBone("seg_" + n + "_plate").ifPresent(bone -> bone.setPosY(plateLift));
            getBone("seg_" + n + "_belly").ifPresent(bone -> bone.setPosY(-plateLift));
        }
    }
}
