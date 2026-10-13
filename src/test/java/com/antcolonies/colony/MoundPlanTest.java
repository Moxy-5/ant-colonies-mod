package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.antcolonies.colony.MoundPlan.Part;
import com.antcolonies.colony.MoundPlan.Shape;
import com.antcolonies.species.SpeciesProfile.MoundNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Checks the cathedral mound's shape at every stage, for several colonies (every mound is a little different): every
 * block stands on something (or is built out from a block beside it), spire pieces and mouths stand on soil or on a
 * spire piece, ramps on soil, the shaft is closed in and joined to the entrance, the mound grows with every stage, the
 * young mound is low with a small spire, and the full mound is about 32 blocks tall with 3 chimney mouths. Also the
 * galleries under it.
 */
class MoundPlanTest {
    private static final MoundNumbers NUMBERS = new MoundNumbers(32, 2.0F, 0.5F, 3, 13);
    private static final long[] SEEDS = {1, 7, 42, 2024, -987654321L, 31337};

    private static int tall(Shape shape) {
        int top = 0;
        for (Map.Entry<BlockPos, Part> entry : shape.parts().entrySet()) {
            if (entry.getValue() != Part.OPEN) {
                top = Math.max(top, entry.getKey().getY() + 1);
            }
        }
        return top;
    }

    private static long count(Shape shape, Part part) {
        return shape.parts().values().stream().filter(p -> p == part).count();
    }

    @Test
    void everyStageCanBeBuiltAndItsShaftIsClosedIn() {
        for (long seed : SEEDS) {
            for (int h = 1; h <= NUMBERS.maxHeight(); h++) {
                Map<BlockPos, Part> parts = MoundPlan.stage(h, NUMBERS, seed).parts();
                String where = "seed " + seed + ", stage " + h + ": ";
                for (Map.Entry<BlockPos, Part> entry : parts.entrySet()) {
                    BlockPos pos = entry.getKey();
                    Part below = parts.get(pos.below());
                    switch (entry.getValue()) {
                        case WALL -> {
                            if (pos.getY() > 0 && below != Part.WALL) {
                                boolean beside = false;
                                for (Direction side : Direction.Plane.HORIZONTAL) {
                                    beside |= parts.get(pos.relative(side)) == Part.WALL;
                                }
                                assertTrue(beside, where + "the block at " + pos + " has nothing to be built on");
                            }
                        }
                        case SPIRE, MOUTH -> assertTrue(below == Part.WALL || below == Part.SPIRE,
                                where + "the spire piece at " + pos + " stands on " + below);
                        case RAMP -> assertEquals(Part.WALL, below, where + "the ramp at " + pos + " stands on " + below);
                        case OPEN -> {
                            if (pos.getY() > 0) {
                                for (Direction side : Direction.Plane.HORIZONTAL) {
                                    Part beside = parts.get(pos.relative(side));
                                    assertTrue(beside == Part.WALL || beside == Part.OPEN, where + "the shaft at " + pos + " is open to the " + side);
                                }
                            }
                            Part above = parts.get(pos.above());
                            assertTrue(above == Part.WALL || above == Part.OPEN || (pos.getY() == 0 && above == null),
                                    where + "the shaft at " + pos + " is open at the top");
                        }
                    }
                }
                // Every hollow cell is reached from the entrance through hollows.
                Set<BlockPos> reached = new HashSet<>(List.of(BlockPos.ZERO));
                Deque<BlockPos> queue = new ArrayDeque<>(reached);
                while (!queue.isEmpty()) {
                    BlockPos at = queue.poll();
                    for (Direction direction : Direction.values()) {
                        BlockPos n = at.relative(direction);
                        if (parts.get(n) == Part.OPEN && reached.add(n)) {
                            queue.add(n);
                        }
                    }
                }
                for (Map.Entry<BlockPos, Part> entry : parts.entrySet()) {
                    if (entry.getValue() == Part.OPEN) {
                        assertTrue(reached.contains(entry.getKey()), where + entry.getKey() + " is a hollow nobody can get to");
                    }
                }
                assertEquals(Part.OPEN, parts.get(BlockPos.ZERO), where + "the entrance is not open");
            }
        }
    }

    @Test
    void theMoundGrowsIntoATallCathedral() {
        for (long seed : SEEDS) {
            int last = 0;
            for (int h = 1; h <= NUMBERS.maxHeight(); h++) {
                int walls = MoundPlan.walls(MoundPlan.stage(h, NUMBERS, seed));
                assertTrue(walls >= last, "seed " + seed + ": stage " + h + " has " + walls + " blocks, fewer than stage " + (h - 1) + " (" + last + ")");
                last = walls;
            }
            Shape full = MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, seed);
            int tall = tall(full);
            assertTrue(tall >= 28 && tall <= 36, "seed " + seed + ": the full mound is " + tall + " blocks tall");
            assertTrue(last > 700 && last < 1600, "seed " + seed + ": the full mound has " + last + " blocks");
            assertEquals(3, count(full, Part.MOUTH), "seed " + seed + ": chimney mouths on the full mound");
            assertTrue(count(full, Part.RAMP) > 50, "seed " + seed + ": only " + count(full, Part.RAMP) + " ramps");
            assertTrue(count(full, Part.SPIRE) >= 10, "seed " + seed + ": only " + count(full, Part.SPIRE) + " spire pieces");
            // Young (about a sixth grown): a low base with only a small, stubby spire.
            Shape young = MoundPlan.stage(5, NUMBERS, seed);
            assertTrue(tall(young) >= 3 && tall(young) <= 7, "seed " + seed + ": the young mound is " + tall(young) + " blocks tall");
            assertEquals(1, count(young, Part.MOUTH), "seed " + seed + ": the young mound's spire has no mouth");
            System.out.println("[mound-plan] seed " + seed + ": " + last + " blocks, " + tall + " tall, " + count(full, Part.RAMP)
                    + " ramps, " + count(full, Part.SPIRE) + " spire pieces");
        }
    }

    @Test
    void everyMoundIsALittleDifferent() {
        assertNotEquals(MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, 1).parts(), MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, 2).parts());
        assertEquals(MoundPlan.stage(20, NUMBERS, 5).parts(), MoundPlan.stage(20, NUMBERS, 5).parts());
    }

    /**
     * Writes some stages to build/mound-plan/ for the picture tool (tools/termite_mound_preview.py): one line per cell,
     * x y z part age, and the spire piece's thickness or the ramp's high side and corner.
     */
    @Test
    void writeStagesForThePictureTool() throws java.io.IOException {
        java.nio.file.Path folder = java.nio.file.Path.of("..", "mound-plan"); // the tests run in build/minecraft-junit
        java.nio.file.Files.createDirectories(folder);
        for (long seed : new long[] {7, 42}) {
            for (int h : new int[] {5, 13, 22, 32}) {
                Shape shape = MoundPlan.stage(h, NUMBERS, seed);
                StringBuilder text = new StringBuilder();
                shape.parts().forEach((pos, part) -> {
                    text.append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ()).append(' ').append(part)
                            .append(' ').append(shape.age().getOrDefault(pos, 0));
                    if (part == Part.SPIRE) {
                        text.append(' ').append(shape.thickness().get(pos));
                    } else if (part == Part.RAMP) {
                        text.append(' ').append(shape.facing().get(pos).getName()).append(' ').append(shape.corner().get(pos).getSerializedName());
                    }
                    text.append('\n');
                });
                java.nio.file.Files.writeString(folder.resolve("seed_" + seed + "_stage_" + h + ".txt"), text);
            }
        }
    }

    @Test
    void theGalleriesGiveEnoughSoilAndAreDugFromTheShaftOutward() {
        List<BlockPos> galleries = MoundPlan.galleries(NUMBERS.galleryLevels(), NUMBERS.galleryRadius());
        int most = 0;
        for (long seed : SEEDS) {
            most = Math.max(most, MoundPlan.walls(MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, seed)));
        }
        assertTrue(galleries.size() >= most, galleries.size() + " gallery blocks for a mound of " + most);
        Set<BlockPos> dug = new HashSet<>(List.of(BlockPos.ZERO));
        for (BlockPos cell : galleries) {
            boolean touches = false;
            for (Direction direction : Direction.values()) {
                touches |= dug.contains(cell.relative(direction));
            }
            assertTrue(touches, cell + " is dug before anything next to it");
            assertTrue(cell.getY() < 0 && cell.getY() >= -2 * NUMBERS.galleryLevels(), cell + " is outside the gallery levels");
            dug.add(cell);
        }
    }
}
