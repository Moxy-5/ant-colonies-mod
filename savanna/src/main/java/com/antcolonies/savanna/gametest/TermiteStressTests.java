package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Caste;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.debug.Perf;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Performance step 1: measures, never fails on speed. A termite colony with its nest dug and a big crowd in its numbers
 * is run with 60 real termites, then 100 (the stress test, {@code /antcolonies stress}), building by day and then out
 * foraging; the stopwatch ({@link Perf}) writes its report to the log every 5 seconds, and each stage's last report is
 * logged as {@code [stress]}. Only runs when named ({@code ANTCOLONIES_TESTS=stress}), never in a full run.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteStressTests {
    /** Ticks per stage (1.5 minutes: time to fill up, then a minute of measuring). */
    private static final int STAGE = 1800;

    @GameTest(template = "arena", timeoutTicks = 4 * STAGE + 200, skyAccess = true)
    public static void stressTermiteColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            digNestAtOnce(level, colony);
            TestClock.still(helper, 12.0); // midday: building, no foraging
            colony.population().setOnForTests(true);
            // A pretend player 20 blocks off, watching (so soldiers come out to their posts too).
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
            long now = level.getGameTime();
            colony.population().addNewMembers(Caste.WORKER, now - 100000, 400);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 30);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 10);
            colony.population().setStress(60);
        });
        stage(helper, 1, "60 real, building (no foraging)", 60, false);
        stage(helper, 2, "60 real, foraging time", 100, true);
        stage(helper, 3, "100 real, building (no foraging)", 100, false);
        helper.runAfterDelay(4 * STAGE, () -> {
            log(helper, "100 real, foraging time");
            helper.succeed();
        });
    }

    /** At the end of stage {@code n}: logs it, then sets up the next one (its real cap and whether it is foraging time). */
    private static void stage(GameTestHelper helper, int n, String name, int nextCap, boolean nextForaging) {
        helper.runAfterDelay(n * STAGE, () -> {
            log(helper, name);
            Colony colony = TermiteMoundTests.colony(helper);
            colony.population().setStress(nextCap);
            TestClock.still(helper, nextForaging ? 0.0 : 12.0); // midnight: foraging time; midday: building
        });
    }

    private static void log(GameTestHelper helper, String stage) {
        Colony colony = TermiteMoundTests.colony(helper);
        long real = helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(48),
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId())).size();
        SavannaMod.LOGGER.info("[stress] ===== {}: {} real mobs of the colony =====\n{}", stage, real, Perf.report());
    }

    /** Every block of the shaft and of every room of the plan dug out at once (as in the population tests). */
    private static void digNestAtOnce(ServerLevel level, Colony colony) {
        colony.nest().unlockAllLevelsForTests();
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos cell = colony.getCenter().below(); bottom != null && cell.getY() >= bottom.getY(); cell = cell.below()) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>(colony.nest().getPlannedChambers(level));
        rooms.addAll(colony.nest().getSideRooms(level));
        rooms.addAll(colony.nest().getGardens(level));
        if (colony.nest().getFoundingChamber(level) != null) {
            rooms.add(colony.nest().getFoundingChamber(level));
        }
        for (NestPlan.Chamber room : rooms) {
            for (BlockPos cell : room.cells()) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
