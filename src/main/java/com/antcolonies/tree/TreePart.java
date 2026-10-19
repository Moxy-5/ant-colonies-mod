package com.antcolonies.tree;

/**
 * What a block of a giant savanna tree's shape is made of (M7.5), before it is turned into a real block for the tree's
 * kind (step 3). Where two parts of the shape meet in one block, the one with the higher priority wins (wood over
 * leaves, mud over bark). The names match the tree viewer's (tools/tree_viewer.template.html).
 */
public enum TreePart {
    SEEDLING("seedling", 1), THORNS("thorns", 1), PODS("pods", 1), LEAVES("leaves", 2),
    THIN("thin", 3), DEAD_THIN("deadthin", 3), FALLEN_BRANCH("fallenbranch", 3), FRASS("frass", 4),
    INNER("inner", 5), DEAD_INNER("deadinner", 5), BARK("bark", 5), DEAD_BARK("deadbark", 5), PEELING("peeling", 5),
    FIBRE("fibre", 5), ROTTEN("rotten", 5), MUD("mud", 6);

    /** The viewer's name for it. */
    public final String id;
    final int priority;

    TreePart(String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    /** Living log wood: bark or the wood inside it (not the thin branches). */
    public boolean isLivingLog() {
        return this == BARK || this == INNER;
    }

    /** Any wood, living or dead, thick or thin. */
    public boolean isWood() {
        return this != SEEDLING && this != THORNS && this != PODS && this != LEAVES && this != FRASS && this != MUD;
    }

    /** Leaves and what hangs in them: what may be cleared away when it ends up cut off from the tree. */
    public boolean isFoliage() {
        return this == LEAVES || this == THORNS || this == PODS;
    }
}
