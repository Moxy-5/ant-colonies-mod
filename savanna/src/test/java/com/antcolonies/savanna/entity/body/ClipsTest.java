package com.antcolonies.savanna.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.antcolonies.entity.body.Clips;

/**
 * The game's clip lengths come from the clip files (the Motion Studio, the new phase 2, step 3, 2026-11-27; the framework's
 * {@link Clips}). The lion's and the buffalo's Java kept tables of them typed by hand; this pins that the files give the
 * very numbers those tables had (so moving them changed nothing in play), and that a clip file's length and loop are read
 * as GeckoLib reads them (a length in seconds, 20 ticks a second; "hold_on_last_frame" is not a loop).
 */
class ClipsTest {
    /** The lion's old table, by doing: clip, ticks (0: a loop). The roar is the male's; the lioness's was 50, the cub's call 32. */
    private static final Object[][] LION = {{"sit_down", 15}, {"sit", 0}, {"sit_up", 15}, {"rest_down", 19}, {"rest", 0},
            {"rest_up", 19}, {"rest_stretch_up", 76}, {"groom", 88}, {"drink", 60}, {"roar", 68}, {"pride_greet", 36},
            {"mood_alert", 0}, {"mood_angry", 0}, {"mood_flehmen", 0}, {"bluff_charge", 27}, {"swipe", 20}, {"swipe_quick", 18},
            {"swipe_heavy", 21}, {"swipe_combo", 29}, {"bite", 15}, {"pounce", 25}, {"gallop_swat", 19}, {"stalk", 0},
            {"stalk_leap_bite", 29}, {"jump_smack", 20}, {"takedown_pin", 26}, {"feed", 0}, {"death", 24}};
    private static final Object[][] LIONESS_ONLY = {{"roar", 50}, {"cub_groom", 64}, {"ambush_freeze", 0}, {"throat_hold", 0},
            {"hold_sink", 41}, {"hold_down", 0}};
    private static final Object[][] CUB_ONLY = {{"cub_call", 32}, {"mama_nuzzle", 37}, {"play_pounce", 33}, {"play_wrestle", 44},
            {"mood_curious", 0}};
    /** The buffalo's old table. */
    private static final Object[][] BUFFALO = {{"graze", 80}, {"drink", 72}, {"rest_down", 48}, {"rest", 0}, {"rest_up", 48},
            {"ear_flutter", 30}, {"shake_off", 30}, {"snort", 28}, {"bellow", 36}, {"paw_ground", 36}, {"mood_alert", 0},
            {"mood_angry", 0}, {"headbutt", 25}, {"gore", 22}, {"charge_headbutt", 36}, {"held_sink", 40}, {"held_down", 0},
            {"death", 28}, {"death_down", 28}};

    private static void same(String file, Object[][] table) {
        Clips clips = Clips.of(ClipsTest.class, "antcolonies", file);
        for (Object[] row : table) {
            String clip = (String) row[0];
            int ticks = (int) row[1];
            assertTrue(clips.has(clip), file + " has no " + clip);
            if (ticks == 0) {
                assertTrue(clips.loops(clip), file + " " + clip + " should loop");
            } else {
                assertFalse(clips.loops(clip), file + " " + clip + " should play once");
                assertEquals(ticks, clips.ticks(clip), file + " " + clip + "'s length");
            }
        }
    }

    @Test
    void theClipFilesGiveTheLengthsTheJavaKeptBefore() {
        Object[][] male = LION.clone();
        same("lion", male);
        Object[][] lioness = java.util.Arrays.stream(LION).filter(r -> !r[0].equals("roar") && !r[0].equals("swipe_heavy")
                && !r[0].equals("swipe_combo")).toArray(Object[][]::new);
        same("lioness", lioness);
        same("lioness", LIONESS_ONLY);
        same("lion_cub", CUB_ONLY);
        same("lion_cub", new Object[][] {{"rest_down", 19}, {"rest_up", 19}, {"feed", 0}, {"death", 24}});
        same("buffalo", BUFFALO);
    }

    @Test
    void aClipTheFileLacksHasNoLengthAndNoLoop() {
        Clips clips = Clips.of(ClipsTest.class, "antcolonies", "lion_cub");
        assertFalse(clips.has("swipe_heavy"));
        assertEquals(0, clips.ticks("swipe_heavy"));
        assertFalse(clips.loops("swipe_heavy"));
        assertEquals(clips.anim("rest"), clips.anim("rest"), "the same clip asked twice is the same clip (never restarted)");
    }
}
