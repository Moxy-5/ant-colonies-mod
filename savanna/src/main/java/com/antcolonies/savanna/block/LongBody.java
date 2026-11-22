package com.antcolonies.savanna.block;

/**
 * An animal drawn much longer than its box (Minecraft's boxes are square from above: the hippo's covers the barrel of its
 * body, the crocodile's and the monitor's their body only), so plants are pushed by the body as drawn, nose to tail tip
 * along the way it faces ({@link PlantPush#footprint}; the user, 2026-11-22: "the hippo is also missing the ones it should
 * hit"). Distances in blocks from its position at its own size.
 */
public interface LongBody {
    /** How far its drawn body reaches ahead of its position (the snout's tip). */
    double noseAhead();

    /** How far its drawn body reaches behind its position (the tail's tip, or its rump). */
    double tailBehind();
}
