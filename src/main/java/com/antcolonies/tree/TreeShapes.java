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
        TreeVoxels v = kind.isBaobab() ? new BaobabShape(kind, seed).build(age, stage, leafless)
                : kind.isUmbrella() ? new UmbrellaShape(kind, seed).build(age, stage) : new AcaciaShape(kind, seed).build(age, stage);
        if (!kind.isBaobab() && (stage == TreeStage.SNAG || stage == TreeStage.MUD || stage == TreeStage.SHED)) {
            keepWithinLiving(v, build(kind, seed, age, TreeStage.LIVING), stage == TreeStage.SHED ? 2 : 0);
        }
        v.clearLooseFoliage();
        return v;
    }

    /**
     * Wood never jumps as a tree dies (step 5): a standing dead acacia's wood (and the mud over it) only where the living
     * tree had wood, above the pieces lying on the ground (up to {@code ground}). A broken limb's end can round out a
     * block past its whole limb; that block goes.
     */
    private static void keepWithinLiving(TreeVoxels dead, TreeVoxels living, int ground) {
        java.util.List<Long> extra = new java.util.ArrayList<>();
        for (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry<TreePart> e : dead.entries()) {
            long k = e.getLongKey();
            TreePart was = living.get(k);
            if (solid(e.getValue()) && net.minecraft.core.BlockPos.getY(k) > ground && (was == null || !solid(was))) {
                extra.add(k);
            }
        }
        for (long k : extra) {
            dead.set(net.minecraft.core.BlockPos.getX(k), net.minecraft.core.BlockPos.getY(k), net.minecraft.core.BlockPos.getZ(k), null);
        }
        if (!extra.isEmpty()) {
            dead.clearLoose();
        }
    }

    /** Wood or the mud over it (not a fallen branch on the ground). */
    static boolean solid(TreePart p) {
        return (p.isWood() || p == TreePart.MUD) && p != TreePart.FALLEN_BRANCH;
    }

    /** The stages a kind goes through. */
    public static TreeStage[] stages(TreeKind kind) {
        return kind.isBaobab() ? TreeStage.BAOBAB : TreeStage.ACACIA;
    }
}
