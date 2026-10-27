package com.antcolonies.savanna.tree;

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
        return build(kind, seed, age, stage, leafless, false);
    }

    /** hollowed: a tree over a fungus-growing termite colony, its lower trunk eaten hollow ({@link #hollowOut}, step 5f). */
    public static TreeVoxels build(TreeKind kind, long seed, double age, TreeStage stage, boolean leafless, boolean hollowed) {
        return build(kind, seed, age, stage, leafless, hollowed, false);
    }

    /** rotted: its trunk hollowed by heart rot up to the fork (dead wood for the raids, part 4, {@code HeartRot}). */
    public static TreeVoxels build(TreeKind kind, long seed, double age, TreeStage stage, boolean leafless, boolean hollowed, boolean rotted) {
        TreeVoxels v = kind.isBaobab() ? new BaobabShape(kind, seed).build(age, stage, leafless)
                : kind.isUmbrella() ? new UmbrellaShape(kind, seed).snapped(rotted).build(age, stage)
                : new AcaciaShape(kind, seed).snapped(rotted).build(age, stage);
        if (!kind.isBaobab() && (stage == TreeStage.SNAG || stage == TreeStage.MUD || stage == TreeStage.SHED)) {
            keepWithinLiving(v, build(kind, seed, age, TreeStage.LIVING), stage == TreeStage.SHED ? 2 : 0);
        }
        if ((hollowed || rotted) && (stage == TreeStage.LIVING || stage == TreeStage.SNAG || stage == TreeStage.MUD || stage == TreeStage.SHED)) {
            hollowOut(v, seed, hollowed, rotted);
        }
        v.clearLooseFoliage();
        return v;
    }

    /** How high up the trunk termites eat it hollow (my pick: the lower trunk, about 7 m). */
    static final int HOLLOW_HEIGHT = 7;
    /** How high up the trunk heart rot hollows it (my pick: up to the fork, which stops it first in most trees). */
    static final int ROT_HEIGHT = 14;

    /**
     * Step 5f: the lower trunk eaten hollow by termites. Real: Macrotermes build at a tree's foot and eat its dead
     * heartwood from below; the trunk stands as a shell of sound wood round a hollow lined with rotten wood, frass on its
     * floor, and a hole at the foot where they came in. Layer by layer up the trunk to {@link #HOLLOW_HEIGHT}, or to where
     * it forks or gets too thin for a hollow (my picks: the bark ring stays, a block of rotten wood inside it, the rest
     * hollow; a grown trunk 5 wide gets a hollow 1 wide, an ancient one more).
     */
    static void hollowOut(TreeVoxels v, long seed) {
        hollowOut(v, seed, true, false);
    }

    /**
     * As {@link #hollowOut(TreeVoxels, long)}, by termites ({@code termites}: the hole at the foot where they came in),
     * by heart rot ({@code rot}: the hollow runs on up to {@link #ROT_HEIGHT} or the fork, and opens to the air through
     * one hole up the trunk where a limb broke off and let the rot in; real hollow acacias show such a hole), or both.
     */
    static void hollowOut(TreeVoxels v, long seed, boolean termites, boolean rot) {
        double cx = 0, cz = 0;
        java.util.Random dice = new java.util.Random(seed * 31 + 7);
        double door = dice.nextDouble() * Math.PI * 2;
        double limbSide = dice.nextDouble() * Math.PI * 2, limbAt = dice.nextDouble();
        double[] first = null; // the lowest trunk layer's middle and radius, for the floor under it (the roots flare there)
        java.util.List<double[]> layers = new java.util.ArrayList<>(); // each hollowed layer's y, middle and radius
        for (int y = 1; y <= (rot ? ROT_HEIGHT : HOLLOW_HEIGHT); y++) {
            java.util.List<int[]> trunk = new java.util.ArrayList<>();
            double sx = 0, sz = 0;
            for (int x = (int) Math.floor(cx) - 5; x <= (int) Math.ceil(cx) + 5; x++) {
                for (int z = (int) Math.floor(cz) - 5; z <= (int) Math.ceil(cz) + 5; z++) {
                    TreePart p = v.get(x, y, z);
                    if (p != null && log(p) && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= 25) {
                        trunk.add(new int[] {x, z});
                        sx += x;
                        sz += z;
                    }
                }
            }
            if (trunk.size() < 9) {
                break; // too thin for a hollow
            }
            cx = sx / trunk.size();
            cz = sz / trunk.size();
            double r = 0;
            for (int[] b : trunk) {
                r = Math.max(r, Math.hypot(b[0] - cx, b[1] - cz));
            }
            if (r < 1.9 || r > Math.sqrt(trunk.size() / Math.PI) + 1.5) {
                break; // too thin for a hollow, or not one round trunk any more (the fork)
            }
            if (first == null) {
                first = new double[] {cx, cz, r};
            }
            for (int[] b : trunk) {
                eat(v, b[0], y, b[1], Math.hypot(b[0] - cx, b[1] - cz), r, false);
            }
            layers.add(new double[] {y, cx, cz, r});
        }
        if (first == null) {
            return;
        }
        if (rot && layers.size() >= 3) {
            // The limb hole: one block through the bark ring, somewhere from the third hollow layer up.
            double[] at = layers.get(2 + (int) (limbAt * (layers.size() - 2)));
            int y = (int) at[0];
            for (double t = 0; t <= 8; t += 0.5) {
                int x = (int) Math.round(at[1] + t * Math.cos(limbSide)), z = (int) Math.round(at[2] + t * Math.sin(limbSide));
                TreePart p = v.get(x, y, z);
                if (p != null && log(p) || p == TreePart.ROTTEN) {
                    v.set(x, y, z, null);
                } else if (t > at[3]) {
                    break; // through the bark
                }
            }
        }
        // The floor (frass inside, at ground level among the roots) and the way in at the foot, on one side.
        double r = first[2];
        for (int x = (int) Math.floor(first[0]) - 3; x <= (int) Math.ceil(first[0]) + 3; x++) {
            for (int z = (int) Math.floor(first[1]) - 3; z <= (int) Math.ceil(first[1]) + 3; z++) {
                TreePart p = v.get(x, 0, z);
                if (p != null && log(p)) {
                    eat(v, x, 0, z, Math.hypot(x - first[0], z - first[1]), r, true);
                }
            }
        }
        for (int y = 0; y <= (termites ? 1 : -1); y++) {
            boolean out = false;
            for (double t = 0; t <= 8 && !out; t += 0.5) {
                int x = (int) Math.round(first[0] + t * Math.cos(door)), z = (int) Math.round(first[1] + t * Math.sin(door));
                TreePart p = v.get(x, y, z);
                if (p != null && log(p) || p == TreePart.ROTTEN) {
                    v.set(x, y, z, y == 0 ? TreePart.FRASS : null);
                } else if (t > r) {
                    out = true; // through the trunk (and the root under the door)
                }
            }
        }
    }

    /** One trunk block, {@code d} from the trunk's middle of radius {@code r}: eaten out, rotten, or left (the bark ring). */
    private static void eat(TreeVoxels v, int x, int y, int z, double d, double r, boolean floor) {
        if (d < r - 1.2) {
            v.set(x, y, z, floor ? TreePart.FRASS : null);
        } else if (d < r - 0.6 && v.get(x, y, z) != TreePart.MUD) {
            v.set(x, y, z, TreePart.ROTTEN);
        }
    }

    /** Trunk wood (or the mud over it), not a thin branch, a limb or a fallen piece. */
    private static boolean log(TreePart p) {
        return solid(p) && p != TreePart.THIN && p != TreePart.DEAD_THIN && p != TreePart.LIMB && p != TreePart.DEAD_LIMB;
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
