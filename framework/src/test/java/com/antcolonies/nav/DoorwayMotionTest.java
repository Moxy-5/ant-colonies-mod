package com.antcolonies.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * The way a termite comes out of a chimney spire and goes back in ({@link DoorwayMotion}). Also writes the motion to
 * build/chimney/ for tools/chimney_preview.py, which draws it frame by frame.
 */
class DoorwayMotionTest {
    /** The termite chimney block's middle spire (TermiteHoleBlock) and a termite worker's hitbox. */
    private static final double RIM = 28.0 / 16.0;
    private static final double MOUTH = 2.0 / 16.0;
    private static final double HALF_WIDTH = 0.15;
    private static final double OVER_THE_TOP = 0.05;

    @Test
    void itPeeksOnceWithOnlyItsHeadOutThenComesOutOverTheRimOntoTheBlockBeside() {
        List<DoorwayMotion.Key> keys = DoorwayMotion.comeOut(RIM, MOUTH, HALF_WIDTH, OVER_THE_TOP);
        List<DoorwayMotion.Key> peeks = keys.stream().filter(DoorwayMotion.Key::peek).toList();
        assertEquals(1, peeks.size(), "one look round");
        DoorwayMotion.Key peek = peeks.get(0);
        assertEquals(DoorwayMotion.PEEK_TICKS, peek.hold());
        assertEquals(DoorwayMotion.On.INSIDE, peek.on(), "it looks round from inside the spire");
        // The neck (about 0.28 above the feet, climbing head up) is just above the rim: the head is out, the body in.
        double neck = peek.y() + 0.28;
        assertTrue(neck > RIM && neck < RIM + 0.1, "the neck is at " + (neck - RIM) + " above the rim");
        // Before the look round it climbs up the middle of the mouth, never out of it.
        for (DoorwayMotion.Key key : keys.subList(0, keys.indexOf(peek) + 1)) {
            assertEquals(DoorwayMotion.On.INSIDE, key.on());
            double drawn = key.out() + DoorwayMotion.DRAWN_INTO_WALL;
            assertTrue(Math.abs(drawn) <= MOUTH, "the body is drawn " + drawn + " from the middle, outside the mouth");
        }
        // It ends on the top of the block beside, and goes over the rim tipping slowly.
        DoorwayMotion.Key last = keys.get(keys.size() - 1);
        assertEquals(DoorwayMotion.On.TOP, last.on());
        assertTrue(last.out() > 0.5 + HALF_WIDTH, "it does not end on the block beside");
        assertTrue(keys.stream().anyMatch(key -> key.on() == DoorwayMotion.On.TOP && key.slowTurn() && Math.abs(key.y() - RIM) < 0.01),
                "it does not go over the rim slowly");
        assertTrue(keys.stream().allMatch(key -> key.speed() > 0.0 && key.speed() <= 1.0));
    }

    @Test
    void goingInClimbsTheOutsideTipsOverTheRimAndGoesHeadFirstDownTheMiddleOfTheMouth() {
        List<DoorwayMotion.Key> in = DoorwayMotion.goIn(RIM, MOUTH, HALF_WIDTH, OVER_THE_TOP);
        assertTrue(in.stream().noneMatch(DoorwayMotion.Key::peek), "no look round on the way in");
        assertTrue(in.stream().allMatch(key -> key.hold() == 0));
        // It walks to the foot of the spire on the block's top first, and climbs its outside.
        assertEquals(DoorwayMotion.On.TOP, in.get(0).on());
        assertTrue(in.get(0).y() < 1.01 && in.get(0).out() > MOUTH, "it does not start at the foot of the spire");
        assertEquals(DoorwayMotion.On.OUTSIDE, in.get(1).on());
        // Both tips (onto the top, and head first into the mouth) are slow.
        assertEquals(2, in.stream().filter(DoorwayMotion.Key::slowTurn).count());
        // Inside, it goes down the middle of the mouth, and ends in the block under it.
        for (DoorwayMotion.Key key : in) {
            if (key.on() == DoorwayMotion.On.INSIDE) {
                double drawn = key.out() + DoorwayMotion.DRAWN_INTO_WALL;
                assertTrue(Math.abs(drawn) <= MOUTH, "the body is drawn " + drawn + " from the middle, outside the mouth");
            }
        }
        DoorwayMotion.Key last = in.get(in.size() - 1);
        assertEquals(DoorwayMotion.On.INSIDE, last.on());
        assertTrue(last.y() < 1.0, "it does not end inside the block");
    }

    @Test
    void writeTheMotionForThePictureTool() throws IOException {
        Path dir = Path.of("..", "chimney"); // build/chimney (the tests run in a folder inside build)
        Files.createDirectories(dir);
        write(dir.resolve("come_out.txt"), DoorwayMotion.comeOut(RIM, MOUTH, HALF_WIDTH, OVER_THE_TOP));
        write(dir.resolve("go_in.txt"), DoorwayMotion.goIn(RIM, MOUTH, HALF_WIDTH, OVER_THE_TOP));
    }

    private static void write(Path file, List<DoorwayMotion.Key> keys) throws IOException {
        StringBuilder text = new StringBuilder("# out y on speed hold peek slowTurn (DoorwayMotionTest)\n");
        for (DoorwayMotion.Key key : keys) {
            text.append(String.format(Locale.ROOT, "%.4f %.4f %s %.2f %d %b %b%n", key.out(), key.y(), key.on(), key.speed(),
                    key.hold(), key.peek(), key.slowTurn()));
        }
        Files.writeString(file, text);
    }
}
