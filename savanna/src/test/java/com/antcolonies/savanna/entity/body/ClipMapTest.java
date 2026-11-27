package com.antcolonies.savanna.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.body.ClipMap;
import com.antcolonies.entity.body.Clips;

/**
 * The clip map (the Motion Studio, the new phase 2, step 5, 2026-11-27; the framework's {@link ClipMap}, from each body
 * plan's postures): every clip a map names is in the animal's clip file, and the map walks the ways the lion's goals
 * wrote out by hand before (lying down into the feed with nothing between, up first from lying or sitting, a way down
 * turned back by its way up, each one-shot's end), so moving them into the map changed nothing in play.
 */
class ClipMapTest {
    @BeforeAll
    static void signUp() {
        BodyPlans.signUp(com.antcolonies.savanna.SavannaMod.class, "/assets/antcolonies/body_plans/");
    }

    private static final String[] ANIMALS = {"lion", "lioness", "lion_cub", "buffalo"};

    @Test
    void everyClipAMapNamesIsInTheClipFile() {
        for (String animal : ANIMALS) {
            ClipMap map = BodyPlans.clipMapOf(animal);
            Clips clips = Clips.of(ClipMapTest.class, "antcolonies", animal);
            for (String posture : new String[] {"standing", "lying", "sitting", "held"}) {
                ClipMap.Posture p = map.posture(posture);
                if (p == null) {
                    continue;
                }
                assertTrue(clips.has(p.hold()), animal + " " + posture + ": no clip " + p.hold());
                assertTrue(p.down() == null || clips.has(p.down()), animal + " " + posture + ": no clip " + p.down());
                p.up().forEach(c -> assertTrue(clips.has(c), animal + " " + posture + ": no clip " + c));
                p.clips().forEach(c -> assertTrue(clips.has(c), animal + " " + posture + ": no clip " + c));
                assertTrue(clips.loops(p.hold()), animal + " " + posture + ": its hold " + p.hold() + " should loop");
            }
        }
    }

    @Test
    void theLionsWaysAreTheOnesItsGoalsWroteOut() {
        for (String lion : new String[] {"lion", "lioness", "lion_cub"}) {
            ClipMap m = BodyPlans.clipMapOf(lion);
            // the feast: standing, it lies down first; lying down plays out, then the feed, no rest between (BUGS 376)
            assertEquals("rest_down", m.toward("idle", false, "feed"), lion);
            assertEquals("rest_down", m.toward("rest_down", false, "feed"), lion);
            assertEquals("feed", m.toward("rest_down", true, "feed"), lion);
            assertEquals("feed", m.toward("rest", false, "feed"), lion);
            // up first from lying (any of its lying clips: the rest, a groom, the feed) or sitting, then the clip
            for (String lying : new String[] {"rest", "groom", "feed"}) {
                assertEquals("rest_up", m.toward(lying, false, "roar"), lion + " " + lying);
            }
            assertEquals("sit_up", m.toward("sit", false, "roar"), lion);
            assertEquals("rest_up", m.toward("rest_up", false, "roar"), lion + ": getting up plays out");
            assertEquals("roar", m.toward("rest_up", true, "roar"), lion);
            assertEquals("rest_stretch_up", m.toward("rest_stretch_up", false, "idle"), lion + ": a stretch up plays out");
            // a way down is turned back at once by its way up (as the old onFeet did: rest_down to rest_up)
            assertEquals("rest_up", m.way("rest_down", false, ClipMap.STANDING), lion);
            assertEquals("sit_up", m.way("sit_down", false, ClipMap.STANDING), lion);
            // from sitting to lying: up, then down (there is no clip from one to the other)
            assertEquals("sit_up", m.way("sit", false, "lying"), lion);
            assertEquals("rest_down", m.way("sit_up", true, "lying"), lion);
            // standing it is on its feet; a played-out way up too
            assertNull(m.way("idle", false, ClipMap.STANDING), lion);
            assertNull(m.way("mood_alert", false, ClipMap.STANDING), lion);
            assertNull(m.way("rest_up", true, ClipMap.STANDING), lion);
            // each one-shot's end: the lion's old table (lying down and a groom into the rest, sitting down into the sit,
            // everything else back to calm)
            assertEquals("rest", m.after("rest_down"), lion);
            assertEquals("rest", m.after("groom"), lion);
            assertEquals("sit", m.after("sit_down"), lion);
            for (String clip : new String[] {"rest_up", "sit_up", "rest_stretch_up", "roar", "drink", "swipe"}) {
                assertEquals("idle", m.after(clip), lion + " " + clip);
            }
        }
    }

    @Test
    void theBuffalosEndsAreItsOldTable() {
        ClipMap m = BodyPlans.clipMapOf("buffalo");
        assertEquals("rest", m.after("rest_down"));
        assertEquals("held_down", m.after("held_sink"));
        assertEquals("idle", m.after("rest_up"));
        assertEquals("idle", m.after("graze"));
        assertEquals("rest_up", m.way("held_down", false, ClipMap.STANDING), "let go, it gets up");
        assertEquals("rest_up", m.way("held_sink", false, ClipMap.STANDING), "let go going down, it gets up at once");
        assertEquals("lying", m.startsIn("rest_up"), "a way up two postures share names the first's start");
    }
}
