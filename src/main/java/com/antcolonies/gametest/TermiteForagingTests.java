package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Termites go out at night and in the rain through foraging holes, and stay in by day (realism pass, part 1). */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteForagingTests {
    /**
     * A termite colony digs its two foraging tunnels and, at foraging time (night), opens a hole at the end of each.
     * When it is time to stay in (day) the workers plug both holes with soil from inside and everybody is back inside the
     * nest and the mound; a dead bush right outside is left alone all day. The middle hole (the entrance, open to the
     * sky while the young mound has no roof over it) is plugged by day too. The next night the holes and the middle hole
     * are opened again. On the test clock (roadmap 3a): real nights and days from 18:30, so digging the tunnels may take
     * more than one night, as for real termites.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void termitesOpenTheirForagingHolesAtNightAndPlugThemByDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 3, SURFACE, MIDDLE - 2 + i / 3);
            }
        });
        BlockPos bush = new BlockPos(MIDDLE + 5, SURFACE, MIDDLE + 5);
        TestClock clock = TestClock.moving(helper, 18.5);
        int[] phase = {0}; // 0 the nights until both holes are open, 1 the day after, 2 waiting for the next night, 3 done
        boolean[] bothOpen = {false};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null) {
                return;
            }
            ForagingHoles holes = colony.foragingHoles();
            int tunnels = holes.tunnels(level).size();
            switch (phase[0]) {
                case 0 -> {
                    if (tunnels == 2 && holes.countOpen(level) == 2 && !bothOpen[0]) {
                        bothOpen[0] = true;
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: both holes open at {}", helper.getTick(), clock.describe());
                    }
                    if (bothOpen[0] && clock.phase(colony) == TestClock.Phase.DAY) {
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: sunrise ({})", helper.getTick(), clock.describe());
                        helper.setBlock(bush, Blocks.DEAD_BUSH);
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    helper.assertTrue(clock.phase(colony) == TestClock.Phase.DAY, "by sunset the holes (and the middle hole) were not all plugged with everybody inside");
                    boolean plugged = holes.tunnels(level).stream().allMatch(t -> level.getBlockState(t.hole()).is(ModBlocks.TERMITE_MOUND.get()))
                            && (level.getBlockState(colony.nest().getEntrance()).is(ModBlocks.TERMITE_MOUND.get()) || colony.nest().isEntranceCovered(level));
                    if (plugged && !holes.isAnyoneOutside(level)) {
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: both holes plugged, everybody inside ({})", helper.getTick(), clock.describe());
                        phase[0] = 2;
                    }
                }
                case 2 -> {
                    if (clock.phase(colony) == TestClock.Phase.NIGHT) { // (the bush is the foragers' to cut from now on)
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: night ({})", helper.getTick(), clock.describe());
                        phase[0] = 3;
                    }
                }
                case 3 -> {
                    if (holes.countOpen(level) == 2 && (ForagingHoles.isOpen(level, colony.nest().getEntrance()) || colony.nest().isEntranceCovered(level))) {
                        phase[0] = 4;
                    }
                }
                default -> {
                }
            }
            if ((phase[0] == 1 || phase[0] == 2) && clock.phase(colony) == TestClock.Phase.DAY) {
                helper.assertTrue(helper.getBlockState(bush).is(Blocks.DEAD_BUSH), "a termite cut at the dead bush by day");
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[holes-test] tick {}: {}, phase {}, tunnels {}, open {}, middle {} (covered {}), anyone outside {}, dig target {}",
                        helper.getTick(), clock.describe(), phase[0], tunnels, holes.countOpen(level), level.getBlockState(colony.nest().getEntrance()),
                        colony.nest().isEntranceCovered(level), holes.isAnyoneOutside(level), colony.nest().findDigTarget(level) + ", leaks " + colony.walls().getLeaks(level).size());
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            helper.assertTrue(phase[0] == 4, switch (phase[0]) {
                case 0 -> "the foraging holes were not both opened at night (" + colony.foragingHoles().countOpen(level) + " open)";
                case 1 -> "by day the holes (and the middle hole) were not all plugged with everybody inside";
                case 2 -> "the next night never came";
                default -> "the holes and the middle hole were not opened again the next night";
            });
            // The holes lie at the ends of their tunnels, beyond the foot of the mound.
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                helper.assertTrue(ForagingHoles.isDug(level, tunnel), "a foraging tunnel is not dug out");
            }
        });
    }

    /** Termites head in before dawn: the last in-game hour or so of the night already counts as day for them. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void theNightEndsForTermitesBeforeDawn(GameTestHelper helper) {
        helper.assertFalse(Colony.isBeforeDawn(14000), "the middle of the night counted as dawn");
        helper.assertFalse(Colony.isBeforeDawn(21000), "a late night counted as dawn");
        helper.assertTrue(Colony.isBeforeDawn(22500), "an hour before sunrise did not count as dawn");
        helper.assertTrue(Colony.isBeforeDawn(24000L * 3 + 23200), "just before sunrise on a later day did not count as dawn");
        helper.assertFalse(Colony.isBeforeDawn(24000L * 3 + 100), "the morning counted as before dawn");
        helper.succeed();
    }
}
