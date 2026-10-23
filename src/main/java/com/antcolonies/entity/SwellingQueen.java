package com.antcolonies.entity;

/**
 * A queen whose abdomen swells as her colony grows (the Macrotermes queen, physogastry: {@link TermiteQueenEntity}).
 * Step 9c: shared colony code reads her size through this, never naming the species' body.
 */
public interface SwellingQueen {
    /** How far she has swollen, 0 (founding) to 1 (full grown). */
    float getSize();
}
