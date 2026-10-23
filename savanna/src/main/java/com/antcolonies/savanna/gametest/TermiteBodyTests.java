package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;

import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.entity.goal.GroundShakes;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body (the animation engine, 2026-10-16): what the server decides about how a termite holds itself. The termites'
 * scenes (the split, step S4: they were the termite's part of the shared body scenes, now in
 * {@link HarvesterBodyTests}, every check kept).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteBodyTests {
    /**
     * A termite worker with nothing to do never falls asleep (its species has no naps) and never stops to clean its
     * antennae (its species has no cleaning), though it would take every chance to. Watched for 15 seconds (the length
     * of the ant's nap and cleaning it shared a scene with before the split, my pick).
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void idleTermitesNeverNapNorCleanTheirAntennae(GameTestHelper helper) {
        TermiteWorkerEntity termite = helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 4, SURFACE, MIDDLE);
        termite.keepOutOfColonies();
        termite.setNapChanceForTests(1.0F);
        termite.setGroomChanceForTests(1.0F);
        helper.onEachTick(() -> {
            if (termite.getActivity() != AbstractAntEntity.ACTIVITY_NONE) {
                helper.fail("a termite started cleaning its antennae (its species has no cleaning)");
            }
            if (termite.isAsleep()) {
                helper.fail("a termite fell asleep (termites have no naps)");
            }
        });
        helper.runAfterDelay(300, helper::succeed);
    }

    /**
     * A termite worker feels no sneaking player's steps (it is blind), but freezes at a jump 5 blocks off, holding still,
     * and not again at once (it can tell only its own mock player is there, and takes every chance to notice).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void termitesFeelNoSneakingStepsButFreezeAtAJump(GameTestHelper helper) {
        Player termiteFelt = helper.makeMockPlayer(GameType.SURVIVAL);
        TermiteWorkerEntity termite = helper.spawn(SavannaEntities.TERMITE_WORKER.get(), 12, FLOOR, 20);
        termite.keepOutOfColonies();
        termite.setNoticeChanceForTests(1.0F);
        termite.setPlayersForTests(List.of(termiteFelt));
        termiteFelt.setShiftKeyDown(true);

        int[] tick = {0};
        int jumpAt = 100;
        int[] freezeStart = {-1};
        Vec3[] frozenAt = {null};
        int[] secondJumpAt = {-1};
        String[] state = {""};
        int termiteRest = termite.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::notice).orElseThrow().restTicks();
        // Where the player stands (in the arena, before its sway): beside the termite until the jump, then still there.
        Vec3[] termiteAt = {null};
        helper.onEachTick(() -> {
            int t = ++tick[0];
            termiteAt[0] = beside(helper, termiteAt[0], termite, 0, t <= 80 ? -2 : -5, t < jumpAt);
            state[0] = String.format(java.util.Locale.ROOT, " (tick %d; termite: noticing %d, froze at %d)", t, termite.getNoticing(), freezeStart[0]);
            double sway = 0.6 * Math.sin(t * 2.0 * Math.PI / 30.0);
            if (t <= 80) {
                place(helper, termiteFelt, termiteAt[0].add(sway, 0.0, 0.0)); // sneaking 2 blocks off: soft steps
            } else {
                termiteFelt.setShiftKeyDown(false);
                place(helper, termiteFelt, termiteAt[0]); // 5 blocks off, and still from the jump on
            }

            // The termite: no notice of soft steps, a freeze at a jump (holding still), and not again at once.
            byte felt = termite.getNoticing();
            if (felt != AbstractAntEntity.NOTICE_NONE && felt != AbstractAntEntity.NOTICE_FREEZE) {
                helper.fail("a termite looked at a player (it is blind: it only freezes)");
            }
            if (felt == AbstractAntEntity.NOTICE_FREEZE && secondJumpAt[0] >= 0) {
                if (t - secondJumpAt[0] < termiteRest) {
                    helper.fail("a termite froze again right after its freeze");
                }
            } else if (felt == AbstractAntEntity.NOTICE_FREEZE) {
                if (t < jumpAt) {
                    helper.fail("a termite felt a sneaking player's steps, or one standing still");
                }
                if (freezeStart[0] < 0) {
                    freezeStart[0] = t;
                }
                if (t - freezeStart[0] == 3) {
                    frozenAt[0] = termite.position();
                } else if (frozenAt[0] != null && termite.position().distanceTo(frozenAt[0]) > 0.1) {
                    helper.fail("a frozen termite moved");
                }
            } else if (freezeStart[0] >= 0 && secondJumpAt[0] < 0) {
                secondJumpAt[0] = t;
                GroundShakes.hard(termiteFelt);
            }
            if (t == jumpAt) {
                GroundShakes.hard(termiteFelt);
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(secondJumpAt[0] >= 0 && tick[0] >= secondJumpAt[0] + 60,
                        "the termite has not frozen at the jump and come out of it (and 3 seconds passed)" + state[0]))
                .thenSucceed();
    }
}
