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
     * A termite colony digs its two foraging tunnels and, at foraging time (night), opens a hole at the end of each (with
     * holes taking turns, termites underground step 1: every hole whose turn it is; the arms and the holes along them
     * come as they are dug).
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
        java.util.Set<BlockPos> opened = new java.util.HashSet<>(); // every hole seen open (the ones to be plugged with soil)
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null) {
                return;
            }
            ForagingHoles holes = colony.foragingHoles();
            holes.tunnels(level).stream().map(NestPlan.ForagingTunnel::hole).filter(h -> ForagingHoles.isOpen(level, h)).forEach(opened::add);
            int tunnels = holes.tunnels(level).size();
            // Every hole whose turn it is (termites underground: holes take turns; switched off, both holes) is open.
            boolean turnsOpen = holes.countOpen(level) > 0 && holes.tunnels(level).stream()
                    .filter(t -> ForagingHoles.isDug(level, t) && colony.nest().galleries().isInTurn(t.hole()))
                    .allMatch(t -> ForagingHoles.isOpen(level, t.hole()));
            switch (phase[0]) {
                case 0 -> {
                    if (turnsOpen && !bothOpen[0]) {
                        bothOpen[0] = true;
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: the holes in turn open at {} ({} of {})", helper.getTick(), clock.describe(), holes.countOpen(level), tunnels);
                    }
                    if (bothOpen[0] && clock.phase(colony) == TestClock.Phase.DAY) {
                        AntColoniesMod.LOGGER.info("[holes-test] tick {}: sunrise ({})", helper.getTick(), clock.describe());
                        helper.setBlock(bush, Blocks.DEAD_BUSH);
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    helper.assertTrue(clock.phase(colony) == TestClock.Phase.DAY, "by sunset the holes (and the middle hole) were not all plugged with everybody inside");
                    // Every hole that was opened holds a plug of soil, and none is open (a hole whose turn never came is
                    // still the ground it was).
                    boolean plugged = opened.stream().allMatch(h -> level.getBlockState(h).is(ModBlocks.TERMITE_MOUND.get()))
                            && holes.tunnels(level).stream().noneMatch(t -> ForagingHoles.isOpen(level, t.hole()))
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
                    if (turnsOpen && (ForagingHoles.isOpen(level, colony.nest().getEntrance()) || colony.nest().isEntranceCovered(level))) {
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
                case 0 -> "the foraging holes in turn were not all opened at night (" + colony.foragingHoles().countOpen(level) + " open"
                        + colony.nest().galleries().describe(level) + ")";
                case 1 -> "by day the holes (and the middle hole) were not all plugged with everybody inside";
                case 2 -> "the next night never came";
                default -> "the holes in turn and the middle hole were not opened again the next night";
            });
            // The holes lie at the ends of their tunnels, beyond the foot of the mound.
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                helper.assertTrue(ForagingHoles.isDug(level, tunnel), "a foraging tunnel is not dug out");
            }
        });
    }

    /**
     * Termites underground, step 1 (holes take turns, the user's decisions of 2026-10-17). A colony in the middle of the
     * arena with its nest dug at once, 8 real workers (2 builders, 6 old) and 30 old worker numbers (foragers). Once the
     * 4 arms are planned they are dug at once, and holes are laid out along every tunnel. Dead bushes the colony knows lie
     * just past the end of the first arm. The first night (dry): one hole open per 6 foragers, the arm's end hole by the
     * bushes among them (food first). The next foraging time is a rainy day: more holes than on the dry night (half as many
     * again), the food hole again, and at least one hole that had no turn on the first night (the rested ones explore). All
     * along, a hole that opens is always one whose turn it is. Last, the switch off brings back the 2 holes of before.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true)
    public static void holesTakeTurnsNearFoodFirstAndMoreInTheRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int mid = 32; // the middle of the 65-block arena: room for 16-block arms every way
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), mid - 2 + i % 4, SURFACE, mid - 3 - i / 4);
            }
        });
        TestClock clock = TestClock.moving(helper, 12);
        int[] stage = {0};
        long[] since = {0};
        BlockPos[] food = {null};
        java.util.List<BlockPos> bushes = new java.util.ArrayList<>();
        java.util.Set<BlockPos> firstNight = new java.util.HashSet<>();
        java.util.Set<BlockPos> openBefore = new java.util.HashSet<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long now = level.getGameTime();
            int i = 0;
            for (com.antcolonies.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.entity.TermiteWorkerEntity.class)) {
                worker.setGrownUpAt(now - (i++ < 2 ? colony.profile().worker().nurseUntilTicks() : colony.profile().worker().builderUntilTicks()) - 1000);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(com.antcolonies.entity.WorkerAntEntity.CASTE, now - colony.profile().worker().builderUntilTicks() - 1000, 30);
            colony.nest().galleries().setSwitchForTests(true);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || stage[0] == 0) {
                return;
            }
            ForagingHoles holes = colony.foragingHoles();
            com.antcolonies.colony.Galleries galleries = colony.nest().galleries();
            java.util.List<NestPlan.ForagingTunnel> tunnels = holes.tunnels(level);
            // A hole that opens is always one whose turn it is.
            java.util.Set<BlockPos> openNow = new java.util.HashSet<>();
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                if (ForagingHoles.isOpen(level, tunnel.hole()) && ForagingHoles.isDug(level, tunnel)) {
                    openNow.add(tunnel.hole());
                    helper.assertTrue(openBefore.contains(tunnel.hole()) || galleries.isInTurn(tunnel.hole()),
                            "the hole at " + helper.relativePos(tunnel.hole()) + " opened out of turn (in turn: " + galleries.tonight() + ")");
                }
            }
            openBefore.clear();
            openBefore.addAll(openNow);
            long tick = helper.getTick();
            switch (stage[0]) {
                case 1 -> { // the arms planned: dug at once
                    if (tunnels.size() > 2) {
                        for (NestPlan.ForagingTunnel tunnel : tunnels) {
                            tunnel.tunnel().forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3));
                        }
                        stage[0] = 2;
                        AntColoniesMod.LOGGER.info("[turns-test] tick {}: {} tunnels, the arms dug at once", tick, tunnels.size());
                    } else {
                        helper.assertTrue(tick < 400, "no arms were planned (" + tunnels.size() + " tunnels)");
                    }
                }
                case 2 -> { // the holes along the tunnels laid out: food past the end of the first arm, then night
                    if (tunnels.size() >= 8) {
                        NestPlan.ForagingTunnel arm = tunnels.get(2);
                        food[0] = arm.hole();
                        net.minecraft.core.Direction out = arm.side();
                        for (int k = 2; k <= 3; k++) {
                            for (int s = -1; s <= 1; s++) {
                                BlockPos bush = arm.hole().relative(out, k).relative(out.getClockWise(), s).above();
                                level.setBlock(bush, Blocks.DEAD_BUSH.defaultBlockState(), 3);
                                bushes.add(bush);
                                colony.foodMap().knowForTests(bush);
                            }
                        }
                        colony.food().rescanPlantsSoon();
                        clock.skipTo(colony, TestClock.Phase.NIGHT);
                        since[0] = tick;
                        stage[0] = 3;
                        AntColoniesMod.LOGGER.info("[turns-test] tick {}: {} holes, food by the hole at {}, night", tick, tunnels.size(),
                                helper.relativePos(food[0]));
                    } else {
                        helper.assertTrue(tick < 800, "too few holes laid out along the tunnels: " + tunnels.size() + galleries.describe(level));
                    }
                }
                case 3 -> { // the first night, dry
                    if (tick - since[0] >= 30) {
                        int foragers = colony.foragersNow(level);
                        int wanted = Math.min(tunnels.size(), colony.profile().nest().galleries().orElseThrow().holesFor(foragers, false));
                        AntColoniesMod.LOGGER.info("[turns-test] tick {}: first night, {} foragers, in turn {}", tick, foragers,
                                galleries.tonight().stream().map(helper::relativePos).toList());
                        helper.assertTrue(wanted < tunnels.size(), "every hole is wanted, nothing to take turns: " + wanted + " of " + tunnels.size());
                        helper.assertTrue(galleries.tonight().size() == wanted, "on the dry night " + galleries.tonight().size()
                                + " holes had their turn, not 1 per 6 of the " + foragers + " foragers (" + wanted + ")");
                        helper.assertTrue(galleries.isInTurn(food[0]), "the hole by the known food had no turn");
                        firstNight.addAll(galleries.tonight());
                        clock.skipTo(colony, TestClock.Phase.DAY);
                        since[0] = tick;
                        stage[0] = 4;
                    }
                }
                case 4 -> { // the day: then rain, a foraging time by day
                    if (tick - since[0] >= 60) {
                        for (BlockPos bush : bushes) {
                            level.setBlock(bush, Blocks.DEAD_BUSH.defaultBlockState(), 3);
                            colony.foodMap().knowForTests(bush);
                        }
                        colony.food().rescanPlantsSoon();
                        colony.setRainForTests(true);
                        since[0] = tick;
                        stage[0] = 5;
                    }
                }
                case 5 -> { // the rainy day
                    if (tick - since[0] >= 30) {
                        int foragers = colony.foragersNow(level);
                        int wanted = Math.min(tunnels.size(), colony.profile().nest().galleries().orElseThrow().holesFor(foragers, true));
                        java.util.Set<BlockPos> rainy = galleries.tonight();
                        AntColoniesMod.LOGGER.info("[turns-test] tick {}: rain, {} foragers, in turn {}", tick, foragers,
                                rainy.stream().map(helper::relativePos).toList());
                        helper.assertTrue(rainy.size() == wanted && rainy.size() > firstNight.size(), "in the rain " + rainy.size()
                                + " holes had their turn, not more than the " + firstNight.size() + " of the dry night (wanted " + wanted + ")");
                        helper.assertTrue(rainy.contains(food[0]), "in the rain the hole by the known food had no turn");
                        helper.assertTrue(rainy.stream().anyMatch(hole -> !firstNight.contains(hole)),
                                "no hole rested on the first night had a turn in the rain (no exploring)");
                        colony.setRainForTests(null);
                        galleries.setSwitchForTests(false);
                        stage[0] = 6;
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 6, "the test did not get through its foraging times (stage " + stage[0] + ")");
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.foragingHoles().tunnels(level).size() == 2, "with the switch off the colony did not go back to its 2 holes");
            helper.assertTrue(colony.nest().galleries().isInTurn(colony.foragingHoles().tunnels(level).get(1).hole()),
                    "with the switch off a hole may not open at foraging time");
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
