package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Jobs by age (step 5b): a worker's age decides its usual job, and young workers are drawn paler. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteJobsByAgeTests {
    /**
     * Six termite workers of three ages (two just grown up, two a few days old, two old): the young ones nurse, the
     * middle-aged ones build and the old ones forage. The young are pale, the others not, and the age is saved.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void termiteWorkersTakeTheJobOfTheirAge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        List<TermiteWorkerEntity> workers = new ArrayList<>();
        long[] ages = {0, 1000, 100000, 150000, 200000, 300000};
        ColonyRole[] expected = {ColonyRole.NURSE, ColonyRole.NURSE, ColonyRole.BUILDER, ColonyRole.BUILDER, ColonyRole.FORAGER, ColonyRole.FORAGER};
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < ages.length; i++) {
                workers.add(helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 3, SURFACE, MIDDLE - 2 + i / 3));
            }
        });
        helper.runAfterDelay(20, () -> {
            for (int i = 0; i < ages.length; i++) {
                workers.get(i).setGrownUpAt(level.getGameTime() - ages[i]);
            }
        });
        helper.runAfterDelay(80, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            for (int i = 0; i < ages.length; i++) {
                TermiteWorkerEntity worker = workers.get(i);
                helper.assertTrue(colony.getId().equals(worker.getColonyId()), "worker " + i + " did not join the colony");
                ColonyRole role = colony.roleOf(level, worker);
                helper.assertTrue(role == expected[i], "worker " + i + " (" + ages[i] + " ticks old) is a " + role + ", expected " + expected[i]
                        + "; " + colony.describeJobs(level));
            }
            helper.assertTrue(workers.get(0).getYouth() > 0.9F, "the newest worker is not pale: " + workers.get(0).getYouth());
            helper.assertTrue(workers.get(2).getYouth() == 0.0F, "a middle-aged worker is still pale: " + workers.get(2).getYouth());
            CompoundTag saved = new CompoundTag();
            workers.get(3).saveWithoutId(saved);
            helper.assertTrue(saved.getLong("GrownUpAt") == workers.get(3).getGrownUpAt(), "the age was not saved");
            AntColoniesMod.LOGGER.info("[jobs-test] {}", colony.describeJobs(level));
            helper.succeed();
        });
    }
}
