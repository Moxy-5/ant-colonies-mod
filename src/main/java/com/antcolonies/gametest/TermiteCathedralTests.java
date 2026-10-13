package com.antcolonies.gametest;

import java.util.Map;
import java.util.TreeMap;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteMouthBlock;
import com.antcolonies.block.TermiteSpireBlock;
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

    /**
     * The user's report: a mound put up to stage 13, the colony earns stage 14, and the termites all got stuck at the top
     * trying to put the bottom piece of a spire in place. A real colony (8 workers) on a stage 13 mound put up at once
     * must finish stage 14 by itself.
     */
    @GameTest(template = "arena", timeoutTicks = 120000, skyAccess = true, batch = "cathedral14")
    public static void termitesGrowStageThirteenIntoFourteen(GameTestHelper helper) {
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
        boolean[] built = {false};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || colony.isFounding()) {
                return;
            }
            ServerLevel level = helper.getLevel();
            boolean night = (helper.getTick() / 6000) % 2 == 0; // night and day take turns, as in the user's world
            colony.setForagingTimeForTests(night);
            if (!built[0]) {
                if (colony.nest().isEntrancePlugged(level)) {
                    return;
                }
                colony.mound().setSeedForTests(-1005795587942992884L); // the user's mound
                colony.mound().buildInstantly(level, 13);
                colony.mound().setStage(level, 14);
                built[0] = true;
                AntColoniesMod.LOGGER.info("[cathedral-test] tick {}: stage 13 put up, 14 earned", helper.getTick());
            }
            for (var entry : colony.mound().getParts(level).entrySet()) {
                BlockState state = level.getBlockState(entry.getKey());
                if (state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state) && state.getValue(TermiteMoundBlock.AGE) == 0) {
                    level.setBlockAndUpdate(entry.getKey(), state.setValue(TermiteMoundBlock.AGE, 1));
                }
            }
            // The spire moves up a block: its pieces are reshaped where they stand, so none is ever left hanging in the air.
            for (BlockPos pos : colony.mound().getParts(level).keySet()) {
                BlockState state = level.getBlockState(pos);
                if ((state.getBlock() instanceof TermiteSpireBlock || state.getBlock() instanceof TermiteMouthBlock)
                        && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                    helper.fail("a spire piece hangs in the air at " + pos.subtract(colony.getCenter()) + " (" + state + ")");
                }
            }
            int stage = colony.mound().getStage(level);
            if (stage != lastStage[0] || helper.getTick() % 2000 == 0) {
                AntColoniesMod.LOGGER.info("[cathedral-test] tick {} ({}): building stage {}, left {}, windows open {}", helper.getTick(),
                        night ? "night" : "day", stage, jobsLeft(level, colony), openWindows(level, colony));
                lastStage[0] = stage;
                if (helper.getTick() % 6000 == 5000) {
                    describe(helper, level, colony);
                }
            }
        });
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(built[0], "stage 13 not put up yet");
            int stage = colony.mound().getStage(level);
            helper.assertTrue(stage == 14, "still building stage " + stage + " (left " + jobsLeft(level, colony) + ")");
            Map<String, Integer> left = jobsLeft(level, colony);
            helper.assertTrue(left.isEmpty(), "stage 14 is not finished: " + left);
            helper.assertTrue(openWindows(level, colony) == 0, openWindows(level, colony) + " windows are still open to the outside");
        });
    }

    /** Puts the user's own half-built stage 14 mound (dumped from their save) round the colony's entrance. */
    private static void putUpTheUsersMound(ServerLevel level, Colony colony) {
        try (var in = TermiteCathedralTests.class.getResourceAsStream("/data/antcolonies/gametest_user_mound_14.txt")) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] f = line.trim().split(" ", 4);
                BlockPos pos = colony.getCenter().offset(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                BlockState state = net.minecraft.commands.arguments.blocks.BlockStateParser
                        .parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), f[3], false).blockState();
                level.setBlock(pos, state, 2);
            }
        } catch (Exception e) {
            throw new RuntimeException("could not put up the user's mound", e);
        }
    }

    /**
     * The user's report, with their own mound (stage 14 half built, dumped from their save, same shape seed): the
     * termites all stood at the top where the bottom of the main spire goes and nothing more was built. Night and day
     * take turns and wet soil sets at its own pace, as in their world. The termites must finish stage 14.
     */
    @GameTest(template = "arena", timeoutTicks = 240000, skyAccess = true, batch = "usermound")
    public static void termitesFinishTheUsersHalfBuiltStageFourteen(GameTestHelper helper) {
        helper.getLevel().getServer().tickRateManager().setTickRate(4000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), TermiteMoundTests.MIDDLE, TermiteMoundTests.SURFACE, TermiteMoundTests.MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), TermiteMoundTests.MIDDLE + 2, TermiteMoundTests.SURFACE, TermiteMoundTests.MIDDLE + 1);
            for (int i = 0; i < 12; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), TermiteMoundTests.MIDDLE - 3 + i % 4, TermiteMoundTests.SURFACE,
                        TermiteMoundTests.MIDDLE - 3 + i / 4);
            }
        });
        int[] lastLeft = {-1};
        boolean[] built = {false};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || colony.isFounding()) {
                return;
            }
            ServerLevel level = helper.getLevel();
            boolean night = (helper.getTick() / 12000) % 2 == 0;
            colony.setForagingTimeForTests(night);
            if (!built[0]) {
                if (colony.nest().isEntrancePlugged(level)) {
                    return;
                }
                colony.mound().setSeedForTests(-1005795587942992884L);
                colony.mound().setWorkStage(level, 14);
                putUpTheUsersMound(level, colony);
                built[0] = true;
                AntColoniesMod.LOGGER.info("[user-mound-test] tick {}: the user's mound put up", helper.getTick());
            }
            // The test world has no random ticks: wet soil sets here at about the game's pace (a finished block in about 20
            // minutes at normal speed, see TermiteMoundBlock).
            for (BlockPos pos : colony.mound().getParts(level).keySet()) {
                BlockState state = level.getBlockState(pos);
                if (state.hasProperty(com.antcolonies.block.MoundSoil.AGE) && state.getValue(com.antcolonies.block.MoundSoil.AGE) == 0
                        && (!(state.getBlock() instanceof TermiteMoundBlock) || TermiteMoundBlock.isFinished(state))
                        && level.getRandom().nextInt(24000) == 0) {
                    level.setBlockAndUpdate(pos, state.setValue(com.antcolonies.block.MoundSoil.AGE, 1));
                }
            }
            if (helper.getTick() % 3000 == 0) {
                long outside = level.getEntitiesOfClass(com.antcolonies.entity.TermiteWorkerEntity.class, helper.getBounds().inflate(8)).stream()
                        .filter(w -> !colony.nest().isInside(level, w.blockPosition())).count();
                AntColoniesMod.LOGGER.info("[user-mound-test]   workers outside: {}", outside);
                Map<String, Integer> left = jobsLeft(level, colony);
                int total = left.values().stream().mapToInt(Integer::intValue).sum();
                AntColoniesMod.LOGGER.info("[user-mound-test] tick {} ({}): building stage {}, left {}, windows open {}", helper.getTick(),
                        night ? "night" : "day", colony.mound().getStage(level), left, openWindows(level, colony));
                if (helper.getTick() % 12000 == 9000) {
                    describe(helper, level, colony);
                }
                lastLeft[0] = total;
            }
        });
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(built[0], "the user's mound is not put up yet");
            Map<String, Integer> left = jobsLeft(level, colony);
            helper.assertTrue(left.isEmpty(), "stage 14 is not finished: " + left);
        });
    }
}
