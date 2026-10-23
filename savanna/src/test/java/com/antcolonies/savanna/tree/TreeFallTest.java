package com.antcolonies.savanna.tree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Step 5c: a dead acacia falls toward its heavier side (the way most of its shed wood hangs). */
class TreeFallTest {
    @Test
    void fallsTowardItsHeavierSide() {
        for (String name : new String[] {"umbrella_thorn", "camel_thorn"}) {
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 12; seed++) {
                for (double age : new double[] {0.6, 1.0, 1.2}) {
                    double[] heavy = side(TreeShapes.build(kind, seed, age, TreeStage.SHED), 4, 0, true);
                    double[] lies = side(TreeShapes.build(kind, seed, age, TreeStage.FALLEN), 0, 3, false);
                    if (Math.hypot(heavy[0], heavy[1]) < 1) {
                        continue; // its weight sits over its stump: it keeps the way its dice gave
                    }
                    double a = Math.atan2(heavy[1], heavy[0]), b = Math.atan2(lies[1], lies[0]);
                    double off = Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
                    String what = name + " seed " + seed + " age " + age + ": heavy side " + Math.round(Math.toDegrees(a)) + " ("
                            + Math.round(Math.hypot(heavy[0], heavy[1]) * 10) / 10.0 + " out), log lies " + Math.round(Math.toDegrees(b));
                    assertTrue(off < Math.toRadians(45), what); // the log's limb stubs spread it a little
                }
            }
        }
    }

    /** The mean place of the wood (and the mud over it) from {@code minY} up and farther out than {@code minR}. */
    private static double[] side(TreeVoxels v, int minY, int minR, boolean mud) {
        double[] s = new double[3];
        v.forEach((x, y, z, b) -> {
            if (y >= minY && x * x + z * z > minR * minR && (b.isWood() || mud && b == TreePart.MUD)) {
                s[0] += x;
                s[1] += z;
                s[2]++;
            }
        });
        return s[2] == 0 ? new double[] {0, 0} : new double[] {s[0] / s[2], s[1] / s[2]};
    }
}
