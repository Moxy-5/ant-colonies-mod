package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.*;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body (the animation engine): what the server decides about how a Matabele ant holds itself (naps, cleaning its
 * antennae, personality). The look itself is checked by eye (the animation lab, the pose viewer and the in-game camera).
 * These were the red harvester ant's body tests until it left the mod (2026-11-23); the rules are the shared goals'
 * ({@code NapGoal}, {@code GroomGoal}), which the Matabele run with their own numbers. The termites' part is in
 * {@link TermiteBodyTests}.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleBodyTests {
    /**
     * A Matabele with nothing to do falls asleep (here it takes every chance, so the test is quick) and wakes at once when
     * hurt. One holding a load never naps (a termite never does: {@link TermiteBodyTests}). In the same scene, cleaning
     * the antennae: an idle ant that does not nap stops and cleans an antenna (or both), and goes back to standing when
     * the clip is over; the one holding a load never does. And personality: drawn once (each trait 0 to 1), kept, and
     * saved with the insect.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void idleMatabeleNapAndWakeWhenHurt(GameTestHelper helper) {
        MatabeleEntity sleeper = helper.spawn(SavannaEntities.MATABELE_MINOR.get(), MIDDLE, SURFACE, MIDDLE);
        sleeper.keepOutOfColonies();
        sleeper.setNapChanceForTests(1.0F);
        MatabeleEntity holder = helper.spawn(SavannaEntities.MATABELE_MINOR.get(), MIDDLE + 4, SURFACE, MIDDLE);
        holder.keepOutOfColonies();
        holder.setNapChanceForTests(1.0F);
        holder.setCarriedBlock(Blocks.DIRT.defaultBlockState());
        MatabeleEntity groomer = helper.spawn(SavannaEntities.MATABELE_MINOR.get(), MIDDLE, SURFACE, MIDDLE + 4);
        groomer.keepOutOfColonies();
        groomer.setNapChanceForTests(0.0F);
        groomer.setGroomChanceForTests(1.0F);
        holder.setGroomChanceForTests(1.0F);

        float curiosity = groomer.curiosity();
        float restlessness = groomer.restlessness();
        float boldness = groomer.boldness();
        helper.assertTrue(curiosity >= 0.0F && curiosity <= 1.0F && restlessness >= 0.0F && restlessness <= 1.0F
                && boldness >= 0.0F && boldness <= 1.0F, "a personality trait is out of 0 to 1");
        helper.assertTrue(groomer.curiosity() == curiosity && groomer.boldness() == boldness, "its personality changed when asked again");
        CompoundTag saved = groomer.saveWithoutId(new CompoundTag());
        MatabeleEntity loaded = SavannaEntities.MATABELE_MINOR.get().create(helper.getLevel());
        loaded.load(saved);
        helper.assertTrue(loaded.curiosity() == curiosity && loaded.restlessness() == restlessness && loaded.boldness() == boldness,
                "its personality was not saved");

        int[] groomedTicks = {0, 0};   // this cleaning so far; the longest finished one
        helper.onEachTick(() -> {
            if (holder.getActivity() != AbstractAntEntity.ACTIVITY_NONE) {
                helper.fail("an ant holding a load started cleaning its antennae");
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
                helper.fail("an ant holding a load fell asleep");
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(sleeper.isAsleep(), "the idle ant has not fallen asleep"))
                .thenExecute(() -> sleeper.hurt(helper.getLevel().damageSources().generic(), 0.1F))
                .thenWaitUntil(() -> helper.assertFalse(sleeper.isAsleep(), "the hurt ant sleeps on"))
                .thenWaitUntil(() -> helper.assertTrue(groomedTicks[1] >= AbstractAntEntity.GROOM_ONE_TICKS,
                        "the idle ant has not cleaned an antenna and stood again (" + groomedTicks[0] + " ticks cleaning)"))
                .thenSucceed();
    }
}
