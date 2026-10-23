package com.antcolonies.savanna.tree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Step 5f: a grown tree over termites has its lower trunk eaten hollow: a shell of sound wood round a hollow lined with
 * rotten wood, frass on its floor and a way in at the foot. Hollowing only takes wood away or turns it rotten (or to
 * frass); it never adds a block where the plain tree has none, and it leaves the crown alone.
 */
class TreeHollowTest {
    @Test
    void termitesHollowTheLowerTrunk() {
        int trees = 0, hollow = 0;
        for (String name : new String[] {"umbrella_thorn", "camel_thorn"}) {
            TreeKind kind = TreeKind.load(name);
            for (long seed = 1; seed <= 8; seed++) {
                for (double age : new double[] {1.0, 1.2}) {
                    for (TreeStage stage : new TreeStage[] {TreeStage.LIVING, TreeStage.SNAG}) {
                        TreeVoxels plain = TreeShapes.build(kind, seed, age, stage, false, false);
                        TreeVoxels eaten = TreeShapes.build(kind, seed, age, stage, false, true);
                        String what = name + " seed " + seed + " age " + age + " " + stage.id;
                        int[] gone = new int[1], rotten = new int[1], frass = new int[1];
                        plain.forEach((x, y, z, p) -> {
                            TreePart now = eaten.get(x, y, z);
                            if (now == p) {
                                return;
                            }
                            assertTrue(y <= TreeShapes.HOLLOW_HEIGHT, what + ": changed above the hollow at y " + y);
                            if (now == null) {
                                gone[0]++;
                            } else if (now == TreePart.ROTTEN) {
                                rotten[0]++;
                            } else if (now == TreePart.FRASS) {
                                frass[0]++;
                            } else {
                                throw new AssertionError(what + ": " + p + " turned to " + now);
                            }
                        });
                        eaten.forEach((x, y, z, p) -> assertTrue(plain.get(x, y, z) != null, what + ": a block added at " + x + " " + y + " " + z));
                        trees++;
                        if (gone[0] >= 3 && rotten[0] >= 4 && frass[0] >= 1) {
                            hollow++;
                        }
                    }
                }
            }
        }
        // A grown trunk is thick enough for a hollow in nearly every tree (a thin one keeps its heart).
        assertTrue(hollow >= trees * 3 / 4, hollow + " of " + trees + " grown trees hollowed");
    }
}
