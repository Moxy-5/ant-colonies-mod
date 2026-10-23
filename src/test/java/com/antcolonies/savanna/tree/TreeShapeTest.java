package com.antcolonies.savanna.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * The tree shape maker's checks (M7.5 step 2): nothing floats, one seed is one tree, a tree only adds wood as it grows
 * (real growth), an ancient tree keeps its trunk, and grown trees come out at the sizes picked with the user.
 */
class TreeShapeTest {
    static final String[] KINDS = {"umbrella_thorn", "camel_thorn", "baobab"};
    private static final double[] AGES = {0, 0.05, 0.1, 0.2, 0.3, 0.4, 0.44, 0.46, 0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.25};

    @Test
    void nothingFloats() {
        for (String name : KINDS) {
            if (skipForLook(name)) {
                continue;
            }
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 5; seed++) {
                for (double age : AGES) {
                    for (TreeStage stage : TreeShapes.stages(kind)) {
                        TreeVoxels v = TreeShapes.build(kind, seed, age, stage);
                        LongOpenHashSet standing = v.grounded();
                        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
                            long k = e.getLongKey();
                            assertTrue(standing.contains(k), () -> name + " seed " + " age " + age + " " + stage.id + ": " + e.getValue().id
                                    + " floating at " + BlockPos.of(k).toShortString());
                        }
                    }
                }
            }
        }
    }

    @Test
    void sameSeedSameTree() {
        for (String name : KINDS) {
            TreeKind kind = TreeKind.load(name);
            for (TreeStage stage : TreeShapes.stages(kind)) {
                TreeVoxels a = TreeShapes.build(kind, 7, 0.8, stage), b = TreeShapes.build(kind, 7, 0.8, stage);
                assertEquals(a.size(), b.size());
                for (Long2ObjectMap.Entry<TreePart> e : a.entries()) {
                    assertEquals(e.getValue(), b.get(e.getLongKey()), name + " " + stage.id);
                }
            }
        }
    }

    /** Real growth: every log block of a living tree is still wood a little later (except the side branches it drops). */
    @Test
    void aGrowingTreeOnlyAddsWood() {
        for (String name : KINDS) {
            if (skipForLook(name)) {
                continue;
            }
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 6; seed++) {
                TreeVoxels before = null;
                for (int step = 2; step <= 20; step++) {
                    double age = step * 0.05;
                    TreeVoxels now = TreeShapes.build(kind, seed, age, TreeStage.LIVING);
                    if (before != null) {
                        for (Long2ObjectMap.Entry<TreePart> e : before.entries()) {
                            long k = e.getLongKey();
                            if (!e.getValue().isLivingLog() || before.isDropped(k)) {
                                continue;
                            }
                            TreePart later = now.get(k);
                            long s = seed;
                            assertTrue(later != null && later.isLivingLog(), () -> name + " seed " + s + " age " + age + ": the log at "
                                    + BlockPos.of(k).toShortString() + " became " + (later == null ? "air" : later.id));
                        }
                    }
                    before = now;
                }
            }
        }
    }

    /**
     * Step 5, death: wood never jumps as a tree dies and goes through its dead stages: the snag's wood is where the
     * living tree had wood, the mud-climbed snag's where the snag had it, and the shed tree's (above the pieces lying
     * on the ground) where the mud-climbed one had it.
     */
    @Test
    void deadWoodNeverJumps() {
        TreeStage[] order = {TreeStage.LIVING, TreeStage.SNAG, TreeStage.MUD, TreeStage.SHED};
        for (String name : new String[] {"umbrella_thorn", "camel_thorn"}) {
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 6; seed++) {
                for (double age : new double[] {0.5, 0.8, 1, 1.15, 1.25}) {
                    TreeVoxels before = TreeShapes.build(kind, seed, age, order[0]);
                    for (int i = 1; i < order.length; i++) {
                        TreeVoxels now = TreeShapes.build(kind, seed, age, order[i]);
                        int ground = order[i] == TreeStage.SHED ? 2 : 0;
                        for (Long2ObjectMap.Entry<TreePart> e : now.entries()) {
                            long k = e.getLongKey();
                            if (!solid(e.getValue()) || BlockPos.getY(k) <= ground) {
                                continue;
                            }
                            TreePart was = before.get(k);
                            long s = seed;
                            TreeStage st = order[i];
                            assertTrue(was != null && solid(was), () -> name + " seed " + s + " age " + age + " " + st.id + ": "
                                    + e.getValue().id + " at " + BlockPos.of(k).toShortString() + " where it had " + (was == null ? "air" : was.id));
                        }
                        before = now;
                    }
                }
            }
        }
    }

    private static boolean solid(TreePart p) {
        return (p.isWood() || p == TreePart.MUD) && p != TreePart.FALLEN_BRANCH;
    }

    /** Every limb look on trial in the tree viewer (the user's pick, 2026-10-18), with today's growth and the new one, stands and grows by the same rules. */
    @Test
    void everyLimbLookStandsAndGrows() {
        try {
            for (boolean early : new boolean[] {false, true}) {
                TreeDraw.earlyGrowth = early;
                for (TreeDraw.LimbLook look : TreeDraw.LimbLook.values()) {
                    TreeDraw.look = look;
                    nothingFloats();
                    aGrowingTreeOnlyAddsWood();
                }
            }
        } finally {
            TreeDraw.look = TreeDraw.LimbLook.A;
            TreeDraw.earlyGrowth = false;
        }
    }

    /** The limb looks on trial were for the old acacia shape: the umbrella thorn's own shape has no limb looks. */
    private static boolean skipForLook(String name) {
        return TreeDraw.look != TreeDraw.LimbLook.A && TreeKind.load(name).isUmbrella();
    }

    /** An ancient tree is the grown one thicker: the grown trunk's bark is still wood (rotten inside, or an old baobab's door). */
    @Test
    void anAncientTreeKeepsItsTrunk() {
        for (String name : KINDS) {
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 6; seed++) {
                TreeVoxels grown = TreeShapes.build(kind, seed, TreeShapes.GROWN, TreeStage.LIVING);
                TreeVoxels ancient = TreeShapes.build(kind, seed, TreeShapes.ANCIENT, TreeStage.LIVING);
                for (Long2ObjectMap.Entry<TreePart> e : grown.entries()) {
                    long k = e.getLongKey();
                    int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                    // The trunk's outside: bark open to the air at the side, from the foot to head height.
                    boolean outside = grown.get(x + 1, y, z) == null || grown.get(x - 1, y, z) == null
                            || grown.get(x, y, z + 1) == null || grown.get(x, y, z - 1) == null;
                    if (e.getValue() != TreePart.BARK || !outside || y > 5 || y < 0 || Math.hypot(x, z) > 7) {
                        continue;
                    }
                    TreePart later = ancient.get(k);
                    if (ancient.holes().contains(k)) {
                        continue; // carved out: an old baobab's door into its hollow
                    }
                    assertTrue(later != null && later.isWood(), name + " seed " + seed + ": trunk bark at " + BlockPos.of(k).toShortString() + " gone");
                }
            }
        }
    }

    /** Grown trees: tall, wide and thick as picked with the user (DESIGN.md "Giant savanna trees"). */
    @Test
    void grownTreesHaveThePickedSizes() {
        check("umbrella_thorn", 24, 30, 40, 52, 4, 5);
        check("camel_thorn", 20, 30, 32, 46, 3, 5);
        check("baobab", 25, 33, 28, 42, 8, 14);
    }

    private static void check(String name, int hLo, int hHi, int wLo, int wHi, int tLo, int tHi) {
        TreeKind kind = TreeKind.load(name);
        List<String> report = new ArrayList<>();
        boolean ok = true;
        for (long seed = 1; seed <= 20; seed++) {
            int[] m = measure(TreeShapes.build(kind, seed, TreeShapes.GROWN, TreeStage.LIVING));
            report.add(seed + ": " + m[0] + " tall, " + m[1] + " wide, trunk " + m[2]);
            ok &= m[0] >= hLo && m[0] <= hHi && m[1] >= wLo && m[1] <= wHi && m[2] >= tLo && m[2] <= tHi;
        }
        System.out.println(name + " grown: " + String.join("; ", report));
        assertTrue(ok, name + " sizes out of range: " + String.join("; ", report));
    }

    /** Height (blocks above the ground), crown width, trunk width at head height (blocks across, west to east). */
    static int[] measure(TreeVoxels v) {
        int top = 0, wide = 0;
        for (Long2ObjectMap.Entry<TreePart> e : v.entries()) {
            long k = e.getLongKey();
            top = Math.max(top, BlockPos.getY(k) + 1);
            if (e.getValue() == TreePart.LEAVES) {
                wide = Math.max(wide, (int) Math.round(2 * Math.hypot(BlockPos.getX(k), BlockPos.getZ(k))));
            }
        }
        int across = 0;
        for (int dz = -1; dz <= 1; dz++) {
            int run = 0;
            for (int x = -15; x <= 15; x++) {
                TreePart p = v.get(x, 4, dz);
                run += p != null && p.isWood() ? 1 : 0;
            }
            across = Math.max(across, run);
        }
        return new int[] {top, wide, across};
    }
}
