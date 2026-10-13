package com.antcolonies.gametest;

import java.util.Map;
import java.util.TreeMap;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteTunnelBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.MoundGrowth;
import com.antcolonies.colony.MoundPlan;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Termites build the cathedral mound from inside (tunnel plan step 4): a real colony grows its mound up to the young
 * cathedral (stage 5: a low dome with a small spire, its chimney mouth and ramps), one stage after the other, carving
 * the plan's tunnels, putting up and reshaping the pieces, and working on the outside through windows it plugs again.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteCathedralTests {
    private static final int YOUNG = 5;

    /** What is still to do on the colony's mound at the stage it builds now, by kind (empty when it is all done). */
    static Map<String, Integer> jobsLeft(ServerLevel level, Colony colony) {
        Map<String, Integer> left = new TreeMap<>();
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : colony.mound().getParts(level).entrySet()) {
            MoundGrowth.Job job = colony.mound().jobAt(level, entry.getKey(), entry.getValue());
            if (job != null) {
                left.merge(job.name().toLowerCase(), 1, Integer::sum);
            }
        }
        return left;
    }

    /** Sides of the mound's tunnel blocks open to the air outside (windows not plugged again): there must be none. */
    static int openWindows(ServerLevel level, Colony colony) {
        Map<BlockPos, MoundPlan.Part> parts = colony.mound().getParts(level);
        int open = 0;
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts.entrySet()) {
            BlockState state = level.getBlockState(entry.getKey());
            if (!(state.getBlock() instanceof TermiteTunnelBlock)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos beside = entry.getKey().relative(side);
                if (state.getValue(TermiteTunnelBlock.OPEN.get(side)) && parts.get(beside) != MoundPlan.Part.OPEN
                        && level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
                    open++;
                }
            }
        }
        return open;
    }

    /** Logs every job left (where, what, what stands there) and what every worker is up to: for working out a stall. */
    static void describe(GameTestHelper helper, ServerLevel level, Colony colony) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : colony.mound().getParts(level).entrySet()) {
            MoundGrowth.Job job = colony.mound().jobAt(level, entry.getKey(), entry.getValue());
            if (job != null) {
                AntColoniesMod.LOGGER.info("[cathedral-test]   left: {} {} at {} (from the entrance {}), now {}", job, entry.getValue(),
                        entry.getKey().subtract(origin).toShortString(), entry.getKey().subtract(colony.getCenter()).toShortString(), level.getBlockState(entry.getKey()));
            }
        }
        for (com.antcolonies.entity.TermiteWorkerEntity worker : level.getEntitiesOfClass(com.antcolonies.entity.TermiteWorkerEntity.class, helper.getBounds().inflate(8))) {
            AntColoniesMod.LOGGER.info("[cathedral-test]   worker {} at {} on {}, carrying {}, goals {}, walking {}", worker.getId(),
                    worker.blockPosition().subtract(origin).toShortString(), worker.getNavigator().getFoothold(), worker.getCarriedBlock(),
                    worker.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList(),
                    !worker.getNavigator().isDone());
        }
    }

    /**
     * A queen, a king and 8 workers on deep ground; the colony earns stage 5 at once and its termites build up to it
     * (it is always night here, so they may bite windows; wet soil sets at once). In the end every cell of the young
     * cathedral is as the plan has it (soil, tunnels carved, ramps, spire pieces and the mouth), and no window is left
     * open to the outside.
     */
    @GameTest(template = "arena", timeoutTicks = 120000, skyAccess = true, batch = "cathedral")
    public static void termitesBuildTheYoungCathedralFromInside(GameTestHelper helper) {
        helper.getLevel().getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), TermiteMoundTests.MIDDLE, TermiteMoundTests.SURFACE, TermiteMoundTests.MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), TermiteMoundTests.MIDDLE + 2, TermiteMoundTests.SURFACE, TermiteMoundTests.MIDDLE + 1);
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), TermiteMoundTests.MIDDLE - 3 + i % 4, TermiteMoundTests.SURFACE,
                        TermiteMoundTests.MIDDLE - 3 + i / 4);
            }
        });
        int[] lastStage = {0};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || colony.isFounding()) {
                return;
            }
            ServerLevel level = helper.getLevel();
            colony.setForagingTimeForTests(true);
            if (colony.mound().getEarnedStage(level) != YOUNG) {
                colony.mound().setStage(level, YOUNG);
            }
            // Wet soil sets at once.
            for (var entry : colony.mound().getParts(level).entrySet()) {
                BlockState state = level.getBlockState(entry.getKey());
                if (state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state) && state.getValue(TermiteMoundBlock.AGE) == 0) {
                    level.setBlockAndUpdate(entry.getKey(), state.setValue(TermiteMoundBlock.AGE, 1));
                }
            }
            int stage = colony.mound().getStage(level);
            if (stage != lastStage[0] || helper.getTick() % 2000 == 0) {
                AntColoniesMod.LOGGER.info("[cathedral-test] tick {}: building stage {}, left {}, windows open {}", helper.getTick(), stage,
                        jobsLeft(level, colony), openWindows(level, colony));
                lastStage[0] = stage;
                if (helper.getTick() % 10000 == 0) {
                    describe(helper, level, colony);
                }
            }
        });
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no colony with workers yet");
            int stage = colony.mound().getStage(level);
            helper.assertTrue(stage == YOUNG, "still building stage " + stage + " (left " + jobsLeft(level, colony) + ")");
            Map<String, Integer> left = jobsLeft(level, colony);
            helper.assertTrue(left.isEmpty(), "stage " + YOUNG + " is not finished: " + left);
            helper.assertTrue(openWindows(level, colony) == 0, openWindows(level, colony) + " windows are still open to the outside");
        });
    }
}
