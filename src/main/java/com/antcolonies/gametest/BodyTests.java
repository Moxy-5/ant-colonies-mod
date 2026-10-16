package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The body (the animation engine, 2026-10-16): what the server decides about how an insect holds itself (naps, ...).
 * The look itself is checked by eye (the animation lab, the pose viewer and the in-game camera).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class BodyTests {
    /**
     * A worker with nothing to do falls asleep (here it takes every chance, so the test is quick) and wakes at once when
     * hurt. One holding a seed never naps, and a termite never does (its species has no naps).
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void idleWorkersNapAndWakeWhenHurt(GameTestHelper helper) {
        WorkerAntEntity sleeper = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        sleeper.keepOutOfColonies();
        sleeper.setNapChanceForTests(1.0F);
        WorkerAntEntity holder = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 4, SURFACE, MIDDLE);
        holder.keepOutOfColonies();
        holder.setNapChanceForTests(1.0F);
        holder.setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
        TermiteWorkerEntity termite = helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 4, SURFACE, MIDDLE);
        termite.keepOutOfColonies();
        termite.setNapChanceForTests(1.0F);
        helper.onEachTick(() -> {
            if (holder.isAsleep()) {
                helper.fail("an ant holding a seed fell asleep");
            }
            if (termite.isAsleep()) {
                helper.fail("a termite fell asleep (termites have no naps)");
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(sleeper.isAsleep(), "the idle worker has not fallen asleep"))
                .thenExecute(() -> sleeper.hurt(helper.getLevel().damageSources().generic(), 0.1F))
                .thenWaitUntil(() -> helper.assertFalse(sleeper.isAsleep(), "the hurt worker sleeps on"))
                .thenSucceed();
    }
}
