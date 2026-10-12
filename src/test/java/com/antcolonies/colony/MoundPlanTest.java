package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.antcolonies.colony.MoundPlan.Part;
import com.antcolonies.species.SpeciesProfile.MoundNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Checks the termite mound's shape at every stage: it can be built (every block stands on something, or is built out
 * from a block beside it over a tunnel), every chimney cap sits in a ring of walls, every hollow part is joined to the
 * nest shaft, and the full mound is over 20 blocks tall. Also the galleries under it.
 */
class MoundPlanTest {
    private static final MoundNumbers NUMBERS = new MoundNumbers(22, 6.5F, 3, 9, 2.0F, 0.5F, 3, 13);

    @Test
    void everyStageCanBeBuiltAndItsHollowsJoinTheNest() {
        for (Direction first : Direction.Plane.HORIZONTAL) {
            for (int h = 1; h <= NUMBERS.maxHeight(); h++) {
                Map<BlockPos, Part> stage = MoundPlan.stage(h, NUMBERS, first);
                for (Map.Entry<BlockPos, Part> entry : stage.entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (entry.getValue() == Part.WALL && pos.getY() > 0) {
                        Part below = stage.get(pos.below());
                        boolean beside = false;
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            beside |= stage.get(pos.relative(side)) == Part.WALL;
                        }
                        assertTrue(below == Part.WALL || below == Part.HOLE || (below == Part.OPEN && beside),
                                "stage " + h + ": the block at " + pos + " has nothing to be built on");
                    }
                    if (entry.getValue() == Part.HOLE && pos.getY() > 0) {
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            assertTrue(stage.get(pos.relative(side)) == Part.WALL,
                                    "stage " + h + ": the chimney cap at " + pos + " has no wall to the " + side);
                        }
                        assertTrue(stage.get(pos.above()) == null, "stage " + h + ": something is built on the cap at " + pos);
                    }
                }
                // Every hollow cell and chimney block is reached from the entrance through hollows.
                Set<BlockPos> reached = new HashSet<>();
                Deque<BlockPos> queue = new ArrayDeque<>(List.of(BlockPos.ZERO));
                reached.add(BlockPos.ZERO);
                while (!queue.isEmpty()) {
                    BlockPos at = queue.poll();
                    for (Direction direction : Direction.values()) {
                        BlockPos n = at.relative(direction);
                        Part part = stage.get(n);
                        if ((part == Part.OPEN || part == Part.HOLE) && reached.add(n)) {
                            queue.add(n);
                        }
                    }
                }
                for (Map.Entry<BlockPos, Part> entry : stage.entrySet()) {
                    if (entry.getValue() != Part.WALL) {
                        assertTrue(reached.contains(entry.getKey()), "stage " + h + ": " + entry.getKey() + " is a hollow nobody can get to");
                    }
                }
                assertTrue(stage.get(new BlockPos(0, h - 1, 0)) == Part.HOLE, "stage " + h + " has no cap on its tower");
            }
        }
    }

    @Test
    void theMoundGrowsWithEveryStageToOverTwentyBlocksTall() {
        int last = 0;
        for (int h = 1; h <= NUMBERS.maxHeight(); h++) {
            int walls = MoundPlan.walls(MoundPlan.stage(h, NUMBERS, Direction.NORTH));
            assertTrue(walls > last, "stage " + h + " has " + walls + " blocks, not more than stage " + (h - 1) + " (" + last + ")");
            last = walls;
        }
        Map<BlockPos, Part> full = MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, Direction.NORTH);
        int top = full.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
        assertTrue(top + 1 >= 20, "the full mound is only " + (top + 1) + " blocks tall");
        assertTrue(last > 500 && last < 1500, "the full mound has " + last + " blocks");
        long caps = full.entrySet().stream().filter(e -> e.getValue() == Part.HOLE && e.getKey().getY() > 0).count();
        long entrances = full.entrySet().stream().filter(e -> e.getValue() == Part.HOLE && e.getKey().getY() == 0).count();
        assertTrue(caps == 1 + NUMBERS.sideChimneys(), "the full mound has " + caps + " chimney caps");
        assertTrue(entrances == NUMBERS.sideChimneys(), "the full mound has " + entrances + " entrances at its foot");
        System.out.println("[mound-plan] full mound: " + last + " blocks, " + (top + 1) + " tall");
    }

    /** Writes some stages to build/mound-plan/ for the picture tool (tools/termite_mound_preview.py). */
    @Test
    void writeStagesForThePictureTool() throws java.io.IOException {
        java.nio.file.Path folder = java.nio.file.Path.of("..", "mound-plan"); // the tests run in build/minecraft-junit
        java.nio.file.Files.createDirectories(folder);
        for (int h : new int[] {1, 3, 6, 9, 14, 22}) {
            StringBuilder text = new StringBuilder();
            MoundPlan.stage(h, NUMBERS, Direction.NORTH).forEach((pos, part) ->
                    text.append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ()).append(' ').append(part).append('\n'));
            java.nio.file.Files.writeString(folder.resolve("stage_" + h + ".txt"), text);
        }
    }

    @Test
    void theGalleriesGiveEnoughSoilAndAreDugFromTheShaftOutward() {
        List<BlockPos> galleries = MoundPlan.galleries(NUMBERS.galleryLevels(), NUMBERS.galleryRadius());
        int full = MoundPlan.walls(MoundPlan.stage(NUMBERS.maxHeight(), NUMBERS, Direction.NORTH));
        assertTrue(galleries.size() >= full, galleries.size() + " gallery blocks for a mound of " + full);
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
