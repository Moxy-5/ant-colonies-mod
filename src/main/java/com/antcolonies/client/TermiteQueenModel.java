package com.antcolonies.client;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.TermiteQueenEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The termite queen's model (tools/termite_models.py): the king's head, thorax and legs, and an abdomen of six
 * segments. As she swells (0 to 1, {@link TermiteQueenEntity#getSize}) each segment's pale skin stretches longer, wider
 * and taller, while its dark back plate and lighter belly plate keep their size and stay on the outside of the skin, so
 * the plates drift apart into brown islands on a cream body, as in a real Macrotermes queen. The abdomen sinks to the
 * floor as it grows, and slow waves run along it from front to back (the "pulse"), stronger the bigger she is.
 */
public class TermiteQueenModel extends DefaultedEntityGeoModel<TermiteQueenEntity> {
    /** The renderer's scale for the king and the queen (the model is drawn at 3/4 size). */
    public static final float RENDER_SCALE = 0.75F;

    // The abdomen as built by tools/termite_models.py (model pixels). Keep in step with that script.
    private static final float SEG_LENGTH = 0.65F;
    private static final float HEIGHT = 2.1F;
    private static final float CENTER_Y = 1.8F;
    private static final float[] TAPER = {0.92F, 1.0F, 1.0F, 0.97F, 0.88F, 0.7F};

    /** Full grown, the skin is this many times longer, wider and taller than at founding. */
    private static final float GROW_LENGTH = 4.4F;
    private static final float GROW_WIDTH = 2.9F;
    private static final float GROW_HEIGHT = 3.1F;
    /** How far the bottom of the abdomen sinks as she grows (it ends up lying on the floor). */
    private static final float SINK = 0.7F;

    public TermiteQueenModel() {
        super(ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_queen"));
        withAltAnimations(ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "termite_king"));
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
        float time = queen.tickCount + state.getPartialTick();
        float strength = 0.015F + 0.05F * size;
        for (int i = 0; i < TAPER.length; i++) {
            int n = i + 1;
            float pulse = 1.0F + strength * Mth.sin(time * 0.2F - i * 0.9F);
            float slide = SEG_LENGTH * (length - 1.0F) * (i + 0.5F); // the segments in front of it got longer
            float plateLift = HEIGHT * TAPER[i] * (height * pulse - 1.0F) / 2.0F;
            float finalPulse = pulse;
            getBone("seg_" + n).ifPresent(bone -> {
                bone.setPosY(rise);
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
