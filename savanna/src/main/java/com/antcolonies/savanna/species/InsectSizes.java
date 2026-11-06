package com.antcolonies.savanna.species;

import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.entity.body.BodySizes;
import com.antcolonies.savanna.SavannaMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Real sizes (the user, 2026-11-05, "Yes, real sizes"; {@code docs/DESIGN.md} "Insect sizes", plan
 * {@code docs/plans/real-sizes.md}): every savanna insect drawn at its real length on one scale, a Matabele major (15
 * mm) half a block long, so 1 block = 30 mm of ant. This table is the one place the sizes live: each body's real length
 * and the length its model is drawn at today with no size factor (measured in the size viewer, {@code python
 * tools/model_viewer.py sizes}: the whole model's length front to back, jaws included, times its renderer's scale). The
 * size factor (real length on the scale over today's drawn length) is signed up with {@link BodySizes} for the living
 * insects (their drawing, box and climbing follow it) and read here for their young, their dead and the termites a
 * Matabele carries.
 *
 * <p>In code, not in the species file (my pick): the boxes of the young and the dead are made when the mod starts, and
 * the drawing needs the sizes on the player's side, where the species files are never read.
 */
public final class InsectSizes {
    /** Blocks per mm of real insect: a Matabele major, 15 mm, is 0.5 blocks long. */
    public static final float BLOCKS_PER_MM = 1.0F / 30.0F;

    /** One body: its real length (mm, without antennae) and its model's drawn length today at size factor 1 (blocks). */
    public record Body(float realMm, float drawnLength) {
        /** Its length on the scale (blocks). */
        public float length() {
            return realMm * BLOCKS_PER_MM;
        }

        /** Its size factor: how much smaller (or bigger) than its model's own size it is drawn and boxed. */
        public float factor() {
            return length() / drawnLength;
        }
    }

    /**
     * The bodies (real lengths my picks from the literature: Macrotermes workers 3.5 to 6 mm, minor soldiers about 7,
     * majors 11 to 13, kings about 14; the Matabele models 15 mm (major) and 7.5 mm (minor), each ant drawn at its own
     * length from its colony's size spread; the queen 1.2 times a major, the user's pick). The termite queen starts as
     * the king and swells from there (her own box grows, {@code TermiteQueenEntity}).
     */
    public static final Body MATABELE_MAJOR = new Body(15.0F, 0.94114F);
    public static final Body MATABELE_MINOR = new Body(7.5F, 0.65462F);
    public static final Body MATABELE_QUEEN = new Body(18.0F, 1.56061F);
    public static final Body TERMITE_WORKER = new Body(4.5F, 0.35705F);
    public static final Body TERMITE_MINOR_SOLDIER = new Body(7.0F, 0.47258F);
    public static final Body TERMITE_MAJOR_SOLDIER = new Body(12.0F, 0.74551F);
    public static final Body TERMITE_KING = new Body(14.0F, 0.42F);
    public static final Body TERMITE_QUEEN = new Body(14.0F, 0.43359F);

    private static final Map<String, Body> BY_MOB = new LinkedHashMap<>();

    static {
        BY_MOB.put("matabele_major", MATABELE_MAJOR);
        BY_MOB.put("matabele_minor", MATABELE_MINOR);
        BY_MOB.put("matabele_queen", MATABELE_QUEEN);
        BY_MOB.put("termite_worker", TERMITE_WORKER);
        BY_MOB.put("termite_minor_soldier", TERMITE_MINOR_SOLDIER);
        BY_MOB.put("termite_major_soldier", TERMITE_MAJOR_SOLDIER);
        BY_MOB.put("termite_king", TERMITE_KING);
        BY_MOB.put("termite_queen", TERMITE_QUEEN);
    }

    private InsectSizes() {
    }

    /** Signs up every living savanna insect's size factor and model length (once, while the mod loads). */
    public static void signUp() {
        BY_MOB.forEach((mob, body) -> BodySizes.signUp(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, mob), body.factor(), body.drawnLength()));
    }

    /** Every body by its mob's name (for the checks). */
    public static Map<String, Body> all() {
        return Map.copyOf(BY_MOB);
    }
}
