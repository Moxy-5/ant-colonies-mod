package com.antcolonies.tree;

/**
 * The tree shape maker (M7.5 step 2): the blocks of a giant savanna tree for its kind, seed, age and stage, around its
 * foot at (0, 0, 0). The same seed always gives the same tree, and a tree only adds wood as it grows (real growth, the
 * user's decision: see {@link AcaciaShape}). Ages: 0 a seedling, 0.45 the end of the young look, 1 grown, 1.25 ancient.
 * Placing the shape in the world with the kind's blocks is step 3.
 */
public final class TreeShapes {
    /** The grown tree. */
    public static final double GROWN = 1;
    /** The oldest a tree gets. */
    public static final double ANCIENT = 1.25;

    private TreeShapes() {
    }

    public static TreeVoxels build(TreeKind kind, long seed, double age, TreeStage stage) {
        return build(kind, seed, age, stage, false);
    }

    /** leafless: a baobab in the dry season (no effect on the acacias, which keep their leaves). */
    public static TreeVoxels build(TreeKind kind, long seed, double age, TreeStage stage, boolean leafless) {
        TreeVoxels v = kind.isBaobab() ? new BaobabShape(kind, seed).build(age, stage, leafless) : new AcaciaShape(kind, seed).build(age, stage);
        v.clearLooseFoliage();
        return v;
    }

    /**
     * The new umbrella thorn on trial in the tree viewer (the user's redo from photos, 2026-10-18): the grown, living tree
     * only, in one of its versions. Not used by the game yet.
     */
    static TreeVoxels buildNewUmbrella(TreeKind kind, long seed, UmbrellaShape.Version version) {
        TreeVoxels v = new UmbrellaShape(kind, seed, version).build();
        v.clearLooseFoliage();
        return v;
    }

    /** The stages a kind goes through. */
    public static TreeStage[] stages(TreeKind kind) {
        return kind.isBaobab() ? TreeStage.BAOBAB : TreeStage.ACACIA;
    }
}
