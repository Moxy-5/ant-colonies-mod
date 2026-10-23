package com.antcolonies.desert.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;

import java.util.List;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.desert.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.desert.registry.DesertEntities;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body (the animation engine, 2026-10-16): what the server decides about how an insect holds itself (naps,
 * noticing a player, personality). The look itself is checked by eye (the animation lab, the pose viewer and the
 * in-game camera). The red harvester ant's scenes (the split, step S4: the scenes were shared with a termite, whose part
 * is now in {@link TermiteBodyTests}, every check kept).
 */
@GameTestHolder(DesertMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterBodyTests {
    /**
     * A worker with nothing to do falls asleep (here it takes every chance, so the test is quick) and wakes at once when
     * hurt. One holding a seed never naps (a termite never does: {@link TermiteBodyTests}). In the same scene, cleaning
     * the antennae: an idle worker that does not nap stops and cleans an antenna (or both), and goes back to standing when
     * the clip is over; the one holding a seed never does.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void idleWorkersNapAndWakeWhenHurt(GameTestHelper helper) {
        WorkerAntEntity sleeper = helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        sleeper.keepOutOfColonies();
        sleeper.setNapChanceForTests(1.0F);
        WorkerAntEntity holder = helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE + 4, SURFACE, MIDDLE);
        holder.keepOutOfColonies();
        holder.setNapChanceForTests(1.0F);
        holder.setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
        WorkerAntEntity groomer = helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE, SURFACE, MIDDLE + 4);
        groomer.keepOutOfColonies();
        groomer.setNapChanceForTests(0.0F);
        groomer.setGroomChanceForTests(1.0F);
        holder.setGroomChanceForTests(1.0F);
        int[] groomedTicks = {0, 0};   // this cleaning so far; the longest finished one
        helper.onEachTick(() -> {
            if (holder.getActivity() != AbstractAntEntity.ACTIVITY_NONE) {
                helper.fail("an ant holding a seed started cleaning its antennae");
            }
            if (groomer.getActivity() != AbstractAntEntity.ACTIVITY_NONE) {
                groomedTicks[0]++;
                if (groomedTicks[0] > AbstractAntEntity.GROOM_BOTH_TICKS + 20) {
                    helper.fail("the cleaning ant went on cleaning for " + groomedTicks[0] + " ticks");
                }
            } else if (groomedTicks[0] > 0) {
                groomedTicks[1] = Math.max(groomedTicks[1], groomedTicks[0]);
                groomedTicks[0] = 0;
            }
            if (holder.isAsleep()) {
                helper.fail("an ant holding a seed fell asleep");
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(sleeper.isAsleep(), "the idle worker has not fallen asleep"))
                .thenExecute(() -> sleeper.hurt(helper.getLevel().damageSources().generic(), 0.1F))
                .thenWaitUntil(() -> helper.assertFalse(sleeper.isAsleep(), "the hurt worker sleeps on"))
                .thenWaitUntil(() -> helper.assertTrue(groomedTicks[1] >= AbstractAntEntity.GROOM_ONE_TICKS,
                        "the idle worker has not cleaned an antenna and stood again (" + groomedTicks[0] + " ticks cleaning)"))
                .thenSucceed();
    }

    /**
     * Noticing a player who moves (each insect here can tell only its own mock player is there, so the scenes don't get
     * in each other's way; each takes every chance to notice). An ant of middling boldness stops and looks, its head
     * turned toward the player, and stays where it is; after the look it takes no notice for a while. A bold one walks
     * up close, a shy one opens its jaws (wary) and backs off. One by a player who keeps still takes no notice, nor does
     * one holding a seed (a termite worker feels steps instead: {@link TermiteBodyTests}). Personality is drawn once, kept,
     * and saved.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void insectsNoticeMovingPlayersByTheirPersonality(GameTestHelper helper) {
        // Personality: drawn once (each trait 0 to 1), kept, and saved with the insect.
        WorkerAntEntity drawn = helper.spawn(DesertEntities.WORKER_ANT.get(), 2, FLOOR, MIDDLE);
        drawn.keepOutOfColonies();
        drawn.setNapChanceForTests(0.0F);
        drawn.setNoticeChanceForTests(0.0F);
        float curiosity = drawn.curiosity();
        float restlessness = drawn.restlessness();
        float boldness = drawn.boldness();
        helper.assertTrue(curiosity >= 0.0F && curiosity <= 1.0F && restlessness >= 0.0F && restlessness <= 1.0F
                && boldness >= 0.0F && boldness <= 1.0F, "a personality trait is out of 0 to 1");
        helper.assertTrue(drawn.curiosity() == curiosity && drawn.boldness() == boldness, "its personality changed when asked again");
        CompoundTag saved = drawn.saveWithoutId(new CompoundTag());
        WorkerAntEntity loaded = DesertEntities.WORKER_ANT.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.curiosity() == curiosity && loaded.restlessness() == restlessness && loaded.boldness() == boldness,
                "its personality was not saved");

        Player lookerSeen = helper.makeMockPlayer(GameType.SURVIVAL);
        Player stillSeen = helper.makeMockPlayer(GameType.SURVIVAL);
        Player boldSeen = helper.makeMockPlayer(GameType.SURVIVAL);
        Player shySeen = helper.makeMockPlayer(GameType.SURVIVAL);
        Player carrierSeen = helper.makeMockPlayer(GameType.SURVIVAL);
        // (Boldness grows by 0.15 for a grown worker: 0.4 is middling, 1 bold, 0 shy.)
        WorkerAntEntity looker = noticing(helper, 4, 4, 0.4F, 0.5F, lookerSeen);
        WorkerAntEntity still = noticing(helper, 20, 4, 0.4F, 1.0F, stillSeen);
        WorkerAntEntity bold = noticing(helper, 4, 20, 1.0F, 1.0F, boldSeen);
        WorkerAntEntity shy = noticing(helper, 20, 20, 0.0F, 1.0F, shySeen);
        WorkerAntEntity carrier = noticing(helper, 12, 4, 0.4F, 1.0F, carrierSeen);
        carrier.setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
        place(helper, stillSeen, 20, 7);

        int[] tick = {0};
        // What has been seen so far (each scene's state, by tick).
        int[] lookStart = {-1};
        int[] lookEnd = {-1};
        Vec3[] lookAt = {null};
        boolean[] headOnPlayer = {false};
        boolean[] boldClose = {false};
        double[] shyStart = {-1};
        boolean[] shyBackedOff = {false};
        String[] state = {""};
        int lookerRest = looker.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::notice).orElseThrow().restTicks();
        // Where each moving player stands (in the arena, before its sway): beside its insect until the insect notices, then
        // still there. Insects stroll, and with the players on fixed spots a shy ant (or a termite) sometimes strolled out
        // of range before its first chance to notice (the full run of 2026-10-16).
        Vec3[] lookerAt = {null};
        Vec3[] boldAt = {null};
        Vec3[] shyAt = {null};
        boolean[] boldNoticed = {false};
        helper.onEachTick(() -> {
            int t = ++tick[0];
            boldNoticed[0] |= bold.getNoticing() != AbstractAntEntity.NOTICE_NONE;
            lookerAt[0] = beside(helper, lookerAt[0], looker, 0, 4, lookStart[0] < 0);
            boldAt[0] = beside(helper, boldAt[0], bold, 5, 0, !boldNoticed[0]);
            shyAt[0] = beside(helper, shyAt[0], shy, -3, 0, shyStart[0] < 0);
            double hx = lookerSeen.getX() - looker.getX();
            double hz = lookerSeen.getZ() - looker.getZ();
            state[0] = String.format(java.util.Locale.ROOT,
                    " (tick %d; middling ant: noticing %d, look from %d to %d, head %.0f off; bold: noticing %d, %.1f off;"
                            + " shy: noticing %d, %.1f off, from %.1f)",
                    t, looker.getNoticing(), lookStart[0], lookEnd[0],
                    Mth.wrapDegrees(looker.getYHeadRot() - ((float) (Mth.atan2(hz, hx) * (180.0 / Math.PI)) - 90.0F)),
                    bold.getNoticing(), horizontal(bold.position(), helper.absoluteVec(boldAt[0])),
                    shy.getNoticing(), horizontal(shy.position(), helper.absoluteVec(shyAt[0])), shyStart[0]);
            double sway = 0.6 * Math.sin(t * 2.0 * Math.PI / 30.0);
            place(helper, lookerSeen, lookerAt[0].add(sway, 0.0, 0.0));
            place(helper, boldSeen, boldAt[0].add(0.0, 0.0, sway));
            place(helper, shySeen, shyAt[0].add(0.0, 0.0, sway));
            place(helper, carrierSeen, beside(helper, null, carrier, 0, 3, true).add(sway, 0.0, 0.0));
            if (still.getNoticing() != AbstractAntEntity.NOTICE_NONE) {
                helper.fail("an ant noticed a player who stood still");
            }
            if (carrier.getNoticing() != AbstractAntEntity.NOTICE_NONE) {
                helper.fail("an ant holding a seed stopped to look at a player");
            }
            if (bold.getNoticing() == AbstractAntEntity.NOTICE_WARY || looker.getNoticing() == AbstractAntEntity.NOTICE_WARY) {
                helper.fail("a bold or middling ant was wary");
            }
            if (shy.getNoticing() == AbstractAntEntity.NOTICE_LOOK) {
                helper.fail("a shy ant looked without being wary");
            }

            // The middling ant: looks, its head on the player, and stays put; then takes no notice for a while.
            if (looker.getNoticing() == AbstractAntEntity.NOTICE_LOOK && lookEnd[0] >= 0) {
                if (t - lookEnd[0] < lookerRest) {
                    helper.fail("the ant noticed the player again right after its look (" + (t - lookEnd[0]) + " ticks after)");
                }
            } else if (looker.getNoticing() == AbstractAntEntity.NOTICE_LOOK) {
                if (lookStart[0] < 0) {
                    lookStart[0] = t;
                    lookAt[0] = looker.position();
                }
                if (looker.position().distanceTo(lookAt[0]) > 0.5) {
                    helper.fail("the middling ant walked off while looking");
                }
                if (t - lookStart[0] >= 30) {
                    double dx = lookerSeen.getX() - looker.getX();
                    double dz = lookerSeen.getZ() - looker.getZ();
                    float toPlayer = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                    if (Math.abs(Mth.wrapDegrees(looker.getYHeadRot() - toPlayer)) < 20.0F) {
                        headOnPlayer[0] = true;
                    }
                }
            } else if (lookStart[0] >= 0 && lookEnd[0] < 0) {
                lookEnd[0] = t;
            }

            // The bold ant walks up to the player; the shy one backs off, wary.
            if (bold.getNoticing() == AbstractAntEntity.NOTICE_LOOK
                    && horizontal(bold.position(), helper.absoluteVec(boldAt[0])) <= 2.6) {
                boldClose[0] = true;
            }
            if (shy.getNoticing() == AbstractAntEntity.NOTICE_WARY) {
                double away = horizontal(shy.position(), helper.absoluteVec(shyAt[0]));
                if (shyStart[0] < 0) {
                    shyStart[0] = away;
                } else if (away >= shyStart[0] + 1.2) {
                    shyBackedOff[0] = true;
                }
            }

        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(headOnPlayer[0], "the middling ant has not turned its head to the player" + state[0]))
                .thenWaitUntil(() -> helper.assertTrue(boldClose[0], "the bold ant has not come up close to the player" + state[0]))
                .thenWaitUntil(() -> helper.assertTrue(shyBackedOff[0], "the shy ant has not backed off from the player" + state[0]))
                .thenWaitUntil(() -> helper.assertTrue(lookEnd[0] >= 0 && tick[0] >= lookEnd[0] + 100,
                        "the middling ant's look has not ended (and 5 seconds passed)" + state[0]))
                .thenSucceed();
    }

    /** A grown worker ant with no colony and no naps, of this boldness and curiosity, that notices only this player. */
    private static WorkerAntEntity noticing(GameTestHelper helper, int x, int z, float boldness, float curiosity, Player seen) {
        WorkerAntEntity ant = helper.spawn(DesertEntities.WORKER_ANT.get(), x, FLOOR, z);
        ant.keepOutOfColonies();
        ant.setNapChanceForTests(0.0F);
        ant.setNoticeChanceForTests(1.0F);
        ant.setTraitsForTests(boldness, curiosity, 0.5F);
        ant.setPlayersForTests(List.of(seen));
        return ant;
    }
}
