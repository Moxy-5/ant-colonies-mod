package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.colony.Caste;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Termites go out at night and in the rain through foraging holes, and stay in by day (realism pass, part 1). */
@GameTestHolder(SavannaMod.MODID)
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
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(SavannaEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 6; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 3, SURFACE, MIDDLE - 2 + i / 3);
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
                    .filter(t -> ForagingHoles.isDug(level, t) && com.antcolonies.savanna.colony.Galleries.of(colony).isInTurn(t.hole()))
                    .allMatch(t -> ForagingHoles.isOpen(level, t.hole()));
            switch (phase[0]) {
                case 0 -> {
                    if (turnsOpen && !bothOpen[0]) {
                        bothOpen[0] = true;
                        SavannaMod.LOGGER.info("[holes-test] tick {}: the holes in turn open at {} ({} of {})", helper.getTick(), clock.describe(), holes.countOpen(level), tunnels);
                    }
                    if (bothOpen[0] && clock.phase(colony) == TestClock.Phase.DAY) {
                        SavannaMod.LOGGER.info("[holes-test] tick {}: sunrise ({})", helper.getTick(), clock.describe());
                        helper.setBlock(bush, Blocks.DEAD_BUSH);
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    helper.assertTrue(clock.phase(colony) == TestClock.Phase.DAY, "by sunset the holes (and the middle hole) were not all plugged with everybody inside");
                    // Every hole that was opened holds a plug of soil, and none is open (a hole whose turn never came is
                    // still the ground it was).
                    boolean plugged = opened.stream().allMatch(h -> level.getBlockState(h).is(SavannaBlocks.TERMITE_MOUND.get()))
                            && holes.tunnels(level).stream().noneMatch(t -> ForagingHoles.isOpen(level, t.hole()))
                            && (level.getBlockState(colony.nest().getEntrance()).is(SavannaBlocks.TERMITE_MOUND.get()) || colony.nest().isEntranceCovered(level));
                    if (plugged && !holes.isAnyoneOutside(level)) {
                        SavannaMod.LOGGER.info("[holes-test] tick {}: both holes plugged, everybody inside ({})", helper.getTick(), clock.describe());
                        phase[0] = 2;
                    }
                }
                case 2 -> {
                    if (clock.phase(colony) == TestClock.Phase.NIGHT) { // (the bush is the foragers' to cut from now on)
                        SavannaMod.LOGGER.info("[holes-test] tick {}: night ({})", helper.getTick(), clock.describe());
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
                SavannaMod.LOGGER.info("[holes-test] tick {}: {}, phase {}, tunnels {}, open {}, middle {} (covered {}), anyone outside {}, dig target {}",
                        helper.getTick(), clock.describe(), phase[0], tunnels, holes.countOpen(level), level.getBlockState(colony.nest().getEntrance()),
                        colony.nest().isEntranceCovered(level), holes.isAnyoneOutside(level), colony.nest().findDigTarget(level) + ", leaks " + colony.walls().getLeaks(level).size());
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            helper.assertTrue(phase[0] == 4, switch (phase[0]) {
                case 0 -> "the foraging holes in turn were not all opened at night (" + colony.foragingHoles().countOpen(level) + " open"
                        + com.antcolonies.savanna.colony.Galleries.of(colony).describe(level) + ")";
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
     * along, a hole that opens is always one whose turn it is.
     * Brain step 4g, step 3 (termites plug and close): the next night the food hole is opened by hand and a worker in it is
     * killed by a zombie. At once the hole and every hole whose gallery runs within 6 blocks of the kill are closed (no
     * turn, no hidden trip through the gallery there, no food fetched round it), the others not; the food hole is plugged
     * from inside although it is foraging time. A day and a half on it is still closed; two days and a bit on (the death
     * faded below 1) it is back in the turns and its food may be fetched. Then a death and a hurt more at the same place
     * (once as strong as 6): the hole is left for good, saved, still closed once the memory is gone, and a new hole is
     * opened on a gallery at least 7 blocks from the place, not closed. Last, the switch off brings back the 2
     * holes of before.
     */
    @GameTest(template = "arena", timeoutTicks = 16000, skyAccess = true)
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
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), mid - 2 + i % 4, SURFACE, mid - 3 - i / 4);
            }
        });
        TestClock clock = TestClock.moving(helper, 12);
        int[] stage = {0};
        long[] since = {0};
        BlockPos[] food = {null};
        java.util.List<BlockPos> bushes = new java.util.ArrayList<>();
        java.util.Set<BlockPos> firstNight = new java.util.HashSet<>();
        java.util.Set<BlockPos> openBefore = new java.util.HashSet<>();
        java.util.Set<BlockPos> holesBefore = new java.util.HashSet<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long now = level.getGameTime();
            int i = 0;
            for (com.antcolonies.savanna.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.savanna.entity.TermiteWorkerEntity.class)) {
                worker.setGrownUpAt(now - (i++ < 2 ? colony.profile().worker().nurseUntilTicks() : colony.profile().worker().builderUntilTicks()) - 1000);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(Caste.WORKER, now - colony.profile().worker().builderUntilTicks() - 1000, 30);
            com.antcolonies.savanna.colony.Galleries.of(colony).setSwitchForTests(true);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || stage[0] == 0) {
                return;
            }
            ForagingHoles holes = colony.foragingHoles();
            com.antcolonies.savanna.colony.Galleries galleries = com.antcolonies.savanna.colony.Galleries.of(colony);
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
                        SavannaMod.LOGGER.info("[turns-test] tick {}: {} tunnels, the arms dug at once", tick, tunnels.size());
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
                        SavannaMod.LOGGER.info("[turns-test] tick {}: {} holes, food by the hole at {}, night", tick, tunnels.size(),
                                helper.relativePos(food[0]));
                    } else {
                        helper.assertTrue(tick < 800, "too few holes laid out along the tunnels: " + tunnels.size() + galleries.describe(level));
                    }
                }
                case 3 -> { // the first night, dry
                    if (tick - since[0] >= 30) {
                        int foragers = colony.foragersNow(level);
                        int wanted = Math.min(tunnels.size(), colony.profile().nest().galleries().orElseThrow().holesFor(foragers, false));
                        SavannaMod.LOGGER.info("[turns-test] tick {}: first night, {} foragers, in turn {}", tick, foragers,
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
                        SavannaMod.LOGGER.info("[turns-test] tick {}: rain, {} foragers, in turn {}", tick, foragers,
                                rainy.stream().map(helper::relativePos).toList());
                        helper.assertTrue(rainy.size() == wanted && rainy.size() > firstNight.size(), "in the rain " + rainy.size()
                                + " holes had their turn, not more than the " + firstNight.size() + " of the dry night (wanted " + wanted + ")");
                        helper.assertTrue(rainy.contains(food[0]), "in the rain the hole by the known food had no turn");
                        helper.assertTrue(rainy.stream().anyMatch(hole -> !firstNight.contains(hole)),
                                "no hole rested on the first night had a turn in the rain (no exploring)");
                        colony.setRainForTests(null);
                        clock.skipTo(colony, TestClock.Phase.NIGHT);
                        since[0] = tick;
                        stage[0] = 6;
                    }
                }
                case 6 -> { // brain step 4g, step 3: the next night a worker in the food hole killed by a zombie
                    if (tick - since[0] >= 30) {
                        com.antcolonies.savanna.entity.TermiteWorkerEntity victim = null;
                        for (com.antcolonies.savanna.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.savanna.entity.TermiteWorkerEntity.class)) {
                            if (colony.roleOf(level, worker) != com.antcolonies.colony.ColonyRole.BUILDER) {
                                victim = worker; // (the builders plug the holes)
                            }
                        }
                        helper.assertTrue(victim != null, "no worker to be killed but the builders");
                        level.setBlock(food[0], Blocks.AIR.defaultBlockState(), 3); // the food hole open
                        victim.teleportTo(food[0].getX() + 0.5, food[0].getY(), food[0].getZ() + 0.5);
                        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,
                                helper.relativePos(food[0].above(3)));
                        victim.hurt(level.damageSources().mobAttack(zombie), 100.0F);
                        zombie.discard();
                        colony.part(SavannaParts.ALARM).orElseThrow().calmForTests(); // (the alarm is tested elsewhere: here the foraging time goes on)
                        helper.assertTrue(victim.isDeadOrDying(), "the worker in the food hole was not killed");
                        helper.assertTrue(colony.danger().places().size() == 1 && colony.danger().places().get(0).deaths() == 1,
                                "the death in the food hole is not remembered: " + colony.danger().describe(level));
                        stage[0] = 7;
                    }
                }
                case 7 -> { // at once: the hole and the holes whose gallery runs near the kill closed, the others not
                    BlockPos spot = colony.danger().places().get(0).pos();
                    helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, food[0]), "the hole where a worker was killed is not closed");
                    helper.assertTrue(!galleries.holesInTurn(level).contains(food[0]), "the hole where a worker was killed still has its turn");
                    int open = 0;
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        boolean near = flat(tunnel.hole(), spot) <= 6;
                        for (BlockPos cell : tunnel.tunnel()) {
                            if (flat(cell, spot) <= 6) {
                                near = true;
                                helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().closesCell(cell), "a hidden trip may run through the gallery at "
                                        + helper.relativePos(cell) + ", by the kill");
                            }
                        }
                        helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, tunnel.hole()) == near, "the hole at " + helper.relativePos(tunnel.hole())
                                + (near ? " is not closed, its gallery runs by the kill" : " is closed, far from the kill"));
                        open += near ? 0 : 1;
                    }
                    helper.assertTrue(open > 0, "every hole was closed for one death at the end of an arm");
                    for (BlockPos bush : bushes) {
                        helper.assertTrue(!colony.mayForageAt(level, bush), "the dead bush at " + helper.relativePos(bush) + " by the kill may still be fetched");
                        helper.assertTrue("closed_holes".equals(colony.whoKeepsOff(level, bush)), "the closed holes should keep the termites off the dead bush at "
                                + helper.relativePos(bush) + " (colony parts step 6), not " + colony.whoKeepsOff(level, bush));
                    }
                    helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().describe(level).contains("closed for danger"), "the colony list does not tell the closed holes");
                    since[0] = tick;
                    stage[0] = 8;
                }
                case 8 -> { // plugged from inside, at foraging time; then a day and a half: still closed; two days and a bit: back
                    if (!ForagingHoles.isOpen(level, food[0])) {
                        SavannaMod.LOGGER.info("[turns-test] tick {}: the food hole plugged {} ticks after the kill", tick, tick - since[0]);
                        colony.danger().ageForTests(36000L);
                        helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, food[0]), "a day and a half after the death the food hole is open again: "
                                + colony.danger().describe(level));
                        colony.danger().ageForTests(14400L); // the death (4) halved a little more than twice: under 1
                        helper.assertTrue(!colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, food[0]) && galleries.holesInTurn(level).contains(food[0]),
                                "two days and a bit after the death the food hole is not back in the turns: " + colony.danger().describe(level));
                        helper.assertTrue(colony.mayForageAt(level, bushes.get(0)), "two days and a bit after the death the food there is still not fetched");
                        tunnels.forEach(tunnel -> holesBefore.add(tunnel.hole()));
                        colony.danger().hurt(level, food[0], "zombie", true); // hit again and again: once as strong as 6
                        colony.danger().hurt(level, food[0], "zombie", false);
                        since[0] = tick;
                        stage[0] = 9;
                    } else {
                        helper.assertTrue(tick - since[0] < 3000, "the hole where a worker was killed was not plugged at foraging time");
                    }
                }
                case 9 -> { // left for good: a new hole on the same gallery at least 7 blocks from the place
                    BlockPos spot = colony.danger().places().get(0).pos();
                    if (colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().left().contains(food[0]) && colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().opened() > 0) {
                        java.util.List<BlockPos> added = tunnels.stream().map(NestPlan.ForagingTunnel::hole).filter(hole -> !holesBefore.contains(hole)).toList();
                        SavannaMod.LOGGER.info("[turns-test] tick {}: left {}, new holes {}", tick,
                                colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().left().stream().map(helper::relativePos).toList(), added.stream().map(helper::relativePos).toList());
                        helper.assertTrue(added.size() == colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().opened(), "the new holes in place of the left ones are not among the holes: "
                                + added.size() + " for " + colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().opened());
                        for (BlockPos hole : added) {
                            helper.assertTrue(flat(hole, spot) >= 7 && !colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, hole), "the new hole at "
                                    + helper.relativePos(hole) + " is within 7 blocks of the place hit again and again, or closed");
                        }
                        helper.assertTrue(com.antcolonies.colony.Colony.load(colony.save()).part(SavannaParts.CLOSED_HOLES).orElseThrow().left().contains(food[0]),
                                "the hole left for good was not saved");
                        colony.danger().ageForTests(4 * 24000L); // the memory gone: the hole stays left
                        helper.assertTrue(colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().isClosed(level, food[0]) && !galleries.holesInTurn(level).contains(food[0]),
                                "the hole left for good has a turn again once the danger is forgotten");
                        galleries.setSwitchForTests(false);
                        stage[0] = 10;
                    } else {
                        helper.assertTrue(tick - since[0] < 400, "the hole hit again and again was not left for a new one: "
                                + colony.part(SavannaParts.CLOSED_HOLES).orElseThrow().describe(level) + ", " + colony.danger().describe(level));
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] == 10, "the test did not get through its foraging times (stage " + stage[0] + ")");
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.foragingHoles().tunnels(level).size() == 2, "with the switch off the colony did not go back to its 2 holes");
            helper.assertTrue(com.antcolonies.savanna.colony.Galleries.of(colony).isInTurn(colony.foragingHoles().tunnels(level).get(1).hole()),
                    "with the switch off a hole may not open at foraging time");
        });
    }

    /** Blocks apart, sideways. */
    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /**
     * Termites underground, step 2a (the galleries grow tip by tip, the user's decisions of 2026-10-17) and step 3
     * (growing toward food, filling in). The same colony as above, by day (they dig underground day and night), tips
     * kept within 26 blocks of the middle (the arena). It knows of a hay bale out to the south-east, 21 blocks from the
     * middle. With its 38 workers the colony's arms are already longer than its workers allow: no tip grows, and the
     * piece out to the hay waits (food leads: nothing else grows meanwhile). Then 600 worker numbers join: the first
     * growth is the piece to the hay (its hole beside the bale), no tip grows on past the hay while it lasts; then the
     * tips grow one piece at a time (each dug at once here) until the galleries are as long as the workers allow (a
     * fork's second piece may go a little over), the tips fork, no hole lies past the reach, holes are laid out along
     * the new pieces, and the colony's range is the galleries' (the farthest hole and the graze round it).
     * Step 3 on: the hay is taken away (its hole is spent), a dead bush the colony knows is put by one far piece, and
     * 11 days go by: every far piece that led to no food is packed with soil, the hay's piece and its feeding gallery
     * too (its feeding spot leaves the record), back to the first arms, which stay; the piece by the dead bush and the
     * pieces it grew from stay; no tip spreads into the length given back. Last, dead bushes the colony knows 8 blocks
     * past a first arm's end pull that tip toward them, using the length given back. Then 4 hay bales the colony knows,
     * each 3 blocks from a different hole: each gets a runway or, as only 3 runways are made at a time, a gallery piece.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void theGalleriesGrowTipByTipWithTheColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int mid = 32;
        int reach = 26;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), mid - 2 + i % 4, SURFACE, mid - 3 - i / 4);
            }
        });
        TestClock.still(helper, 12); // midday: the termites stay in, and dig
        BlockPos hay = helper.absolutePos(new BlockPos(mid + 15, SURFACE, mid + 15));
        int[] stage = {0};
        long[] since = {0};
        int[] holesBefore = {0};
        int[] lastLength = {-1};
        int[] mostTips = {0};
        int[] lengthAtStart = {0}; // when the 600 join
        BlockPos[] hayHole = {null};
        BlockPos[] kept = {null}; // the far piece by the dead bush
        java.util.Set<BlockPos> keptChain = new java.util.HashSet<>();
        java.util.Set<BlockPos> firstArms = new java.util.HashSet<>();
        java.util.List<BlockPos> hayCells = new java.util.ArrayList<>();
        int[] lengthBeforeFills = {0};
        long[] agedAt = {0};
        boolean[] hayFilled = {false};
        int[] lastFills = {0};
        long[] lastFillAt = {0};
        BlockPos[] pullTip = {null};
        BlockPos[] pullAim = {null};
        int[] filledBeforePull = {0};
        long[] spentAt = {-1};
        java.util.Map<BlockPos, BlockPos> piecesSeen = new java.util.HashMap<>(); // piece -> the piece it grew from
        java.util.Set<BlockPos> backfilledAt = new java.util.HashSet<>(); // pieces whose branch beyond was filled in
        java.util.List<BlockPos> nearHay = new java.util.ArrayList<>(); // hay bales by the holes (stage 6)
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long now = level.getGameTime();
            for (com.antcolonies.savanna.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.savanna.entity.TermiteWorkerEntity.class)) {
                worker.setGrownUpAt(now - colony.profile().worker().nurseUntilTicks() - 1000);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(Caste.WORKER, now - colony.profile().worker().builderUntilTicks() - 1000, 30);
            com.antcolonies.savanna.colony.Galleries.of(colony).setSwitchForTests(true);
            com.antcolonies.savanna.colony.Galleries.of(colony).setReachForTests(reach);
            level.setBlock(hay, Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(hay);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || stage[0] == 0) {
                return;
            }
            com.antcolonies.savanna.colony.Galleries galleries = com.antcolonies.savanna.colony.Galleries.of(colony);
            java.util.List<NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
            // Whatever is planned is dug at once (the digging itself is the other scenes'): the arms, then each new piece.
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                for (BlockPos cell : galleries.digCells(tunnel)) {
                    if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            long tick = helper.getTick();
            if (tick % 200 == 0) {
                SavannaMod.LOGGER.info("[web-test] tick {}: stage {}{}, range {}, filled {}", tick, stage[0], galleries.describe(level),
                        colony.foragingRadius(), galleries.filledLength());
            }
            BlockPos center = colony.getCenter();
            int pieceLength = colony.profile().nest().galleries().orElseThrow().pieceLength();
            switch (stage[0]) {
                case 1 -> { // the arms planned and their tips made: the small colony's galleries stay as they are
                    if (galleries.tipCount() >= 3) {
                        since[0]++;
                        helper.assertTrue(galleries.length() <= galleries.lengthAllowed(),
                                "a colony of " + colony.getCount(Caste.WORKER) + " workers grew its galleries past the arms:"
                                        + galleries.describe(level));
                        helper.assertTrue(colony.nest().foodWays().getNewTunnels().isEmpty(),
                                "a colony of 38 workers dug a piece out to the hay, more than its workers allow:" + galleries.describe(level));
                        if (since[0] >= 300) {
                            helper.assertTrue(colony.foragingHoles().isFoodWaiting(), "the known hay is not waiting for its piece:"
                                    + galleries.describe(level) + colony.foragingHoles().describeNewHoles(level));
                            holesBefore[0] = tunnels.size();
                            lengthAtStart[0] = galleries.length();
                            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 1000, 600);
                            SavannaMod.LOGGER.info("[web-test] tick {}: 600 workers join, {} blocks allowed", tick, galleries.lengthAllowed());
                            since[0] = 0;
                            stage[0] = 2;
                        }
                    } else {
                        helper.assertTrue(tick < 600, "no arms and tips:" + galleries.describe(level));
                    }
                }
                case 2 -> { // growing: the piece to the hay first
                    if (hayHole[0] == null) {
                        if (!colony.nest().foodWays().getNewTunnels().isEmpty()) {
                            com.antcolonies.colony.FoodWays.NewTunnel toHay = colony.nest().foodWays().getNewTunnels().get(0);
                            hayHole[0] = toHay.tunnel().hole();
                            helper.assertTrue(toHay.food().equals(hay), "the piece is for " + toHay.food() + ", not the hay");
                            helper.assertTrue(Math.max(Math.abs(hayHole[0].getX() - hay.getX()), Math.abs(hayHole[0].getZ() - hay.getZ())) <= 2,
                                    "the hole " + hayHole[0].toShortString() + " is not beside the hay");
                            helper.assertTrue(galleries.hasPiece(hayHole[0]), "the tunnel to the hay is not a piece of the galleries");
                            SavannaMod.LOGGER.info("[web-test] tick {}: the piece to the hay, hole at {}", tick, helper.relativePos(hayHole[0]));
                        } else {
                            helper.assertTrue(galleries.length() == lengthAtStart[0], "the galleries spread before the piece out to the known hay:"
                                    + galleries.describe(level));
                            helper.assertTrue(++since[0] < 1000, "no piece out to the hay:" + galleries.describe(level)
                                    + colony.foragingHoles().describeNewHoles(level));
                            return;
                        }
                    }
                    for (BlockPos piece : galleries.pieceHoles()) {
                        helper.assertTrue(!hayHole[0].equals(galleries.parentOf(piece)), "a tip grew on past the hay while it lasts, to "
                                + helper.relativePos(piece));
                    }
                    mostTips[0] = Math.max(mostTips[0], galleries.tipCount()); // tips at the reach stop, so count the most at once
                    helper.assertTrue(galleries.length() <= galleries.lengthAllowed() + 2 * (pieceLength + 3),
                            "the galleries grew past what the workers allow:" + galleries.describe(level));
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        double out = Math.hypot(tunnel.hole().getX() - center.getX(), tunnel.hole().getZ() - center.getZ());
                        helper.assertTrue(out <= reach + 2.5, "a hole at " + helper.relativePos(tunnel.hole()) + " is " + Math.round(out)
                                + " blocks out, past the reach of " + reach);
                    }
                    if (galleries.length() != lastLength[0]) {
                        lastLength[0] = galleries.length();
                        since[0] = 0;
                    }
                    boolean done = galleries.length() >= galleries.lengthAllowed();
                    if (!done && ++since[0] >= 600) {
                        // Stopped short of what the workers allow: only right if the tips are all out at the reach.
                        helper.assertTrue(galleries.reach() >= reach - 3, "the galleries stopped growing short of what the workers allow and of the reach:"
                                + galleries.describe(level));
                        done = true;
                    }
                    if (done) {
                        helper.assertTrue(mostTips[0] > 4, "the tips never forked (at most " + mostTips[0] + " at once):" + galleries.describe(level));
                        helper.assertTrue(galleries.reach() > 20, "the galleries did not grow out past the arms:" + galleries.describe(level));
                        int holes = tunnels.size();
                        helper.assertTrue(holes > holesBefore[0] + 4, "no holes along the new pieces: " + holesBefore[0] + " before, " + holes + " now");
                        helper.assertTrue(colony.foragingRadius() >= galleries.range() && galleries.range() == galleries.reach() + 6,
                                "the range should be the galleries' (farthest hole " + galleries.reach() + " and the graze): " + colony.foragingRadius());
                        // Step 3: the hay goes; a dead bush the colony knows by the farthest other piece that leads on from no other.
                        level.setBlock(hay, Blocks.AIR.defaultBlockState(), 3);
                        for (BlockPos piece : galleries.pieceHoles()) {
                            if (galleries.isFirstArm(piece)) {
                                firstArms.add(piece);
                            } else if (galleries.isLeaf(piece) && !piece.equals(hayHole[0]) && (kept[0] == null
                                    || Math.hypot(piece.getX() - center.getX(), piece.getZ() - center.getZ())
                                            > Math.hypot(kept[0].getX() - center.getX(), kept[0].getZ() - center.getZ()))) {
                                kept[0] = piece;
                            }
                        }
                        helper.assertTrue(kept[0] != null, "no far piece to put the dead bush by:" + galleries.describe(level));
                        BlockPos bush = new BlockPos(kept[0].getX() + 1, hay.getY(), kept[0].getZ() + 1);
                        level.setBlock(bush, Blocks.DEAD_BUSH.defaultBlockState(), 3);
                        colony.foodMap().knowForTests(bush);
                        colony.food().rescanPlantsSoon();
                        for (BlockPos at = kept[0]; at != null; at = galleries.parentOf(at)) {
                            keptChain.add(at);
                        }
                        NestPlan.ForagingTunnel hayPiece = galleries.pieceAt(hayHole[0]);
                        hayCells.addAll(galleries.digCells(hayPiece));
                        com.antcolonies.colony.FoodWays.NewTunnel toHay = colony.nest().foodWays().newTunnelAt(hayHole[0]);
                        if (toHay.gallery() != null) {
                            toHay.gallery().stream().filter(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell)).forEach(hayCells::add);
                        }
                        SavannaMod.LOGGER.info("[web-test] tick {}: the hay goes; a dead bush by the piece to {}", tick, helper.relativePos(kept[0]));
                        since[0] = 0;
                        stage[0] = 3;
                    }
                }
                case 3 -> { // the hay's hole spent (and a moment for its tip to grow on, as it may): 11 days go by
                    if (spentAt[0] < 0 && colony.nest().foodWays().isSpent(hayHole[0])) {
                        spentAt[0] = tick;
                    }
                    if (spentAt[0] >= 0 && tick - spentAt[0] >= 100) {
                        for (BlockPos piece : galleries.pieceHoles()) {
                            piecesSeen.put(piece, galleries.parentOf(piece));
                        }
                        lengthBeforeFills[0] = galleries.length();
                        galleries.ageForTests(11 * 24000L);
                        agedAt[0] = level.getGameTime();
                        lastFillAt[0] = tick;
                        since[0] = 0;
                        stage[0] = 4;
                    } else {
                        helper.assertTrue(++since[0] < 1100, "the hole by the gone hay was not spent");
                    }
                }
                case 4 -> { // filling in
                    helper.assertTrue(galleries.length() + galleries.filledLength()
                            <= Math.max(galleries.lengthAllowed(), lengthBeforeFills[0]) + 2 * (pieceLength + 3),
                            "the tips spread into the length the filled-in pieces gave back:" + galleries.describe(level));
                    for (BlockPos arm : firstArms) {
                        helper.assertTrue(galleries.hasPiece(arm), "a first arm was filled in, to " + helper.relativePos(arm));
                    }
                    for (BlockPos piece : keptChain) {
                        helper.assertTrue(galleries.hasPiece(piece), "the piece to " + helper.relativePos(piece)
                                + " was filled in, though it leads to the dead bush the colony knows");
                    }
                    // A piece whose branch beyond was filled in does not dig that way again (it led nowhere).
                    for (BlockPos piece : galleries.pieceHoles()) {
                        if (!piecesSeen.containsKey(piece)) {
                            piecesSeen.put(piece, galleries.parentOf(piece));
                            helper.assertTrue(!backfilledAt.contains(galleries.parentOf(piece)), "the piece to " + helper.relativePos(galleries.parentOf(piece))
                                    + " dug again where its branch was filled in, to " + helper.relativePos(piece));
                        }
                    }
                    for (java.util.Map.Entry<BlockPos, BlockPos> seen : piecesSeen.entrySet()) {
                        if (!galleries.hasPiece(seen.getKey()) && seen.getValue() != null) {
                            backfilledAt.add(seen.getValue());
                        }
                    }
                    if (!hayFilled[0] && !galleries.hasPiece(hayHole[0])) {
                        hayFilled[0] = true;
                        helper.assertTrue(colony.nest().foodWays().newTunnelAt(hayHole[0]) == null, "the filled-in hay piece's feeding spot is still on record");
                        for (BlockPos cell : hayCells) {
                            helper.assertTrue(!com.antcolonies.colony.Nest.isDugOut(level, cell), "a block of the hay's filled-in piece is still open at "
                                    + helper.relativePos(cell));
                        }
                        SavannaMod.LOGGER.info("[web-test] tick {}: the hay's piece is filled in ({} blocks)", tick, hayCells.size());
                    }
                    if (galleries.fills() != lastFills[0]) {
                        lastFills[0] = galleries.fills();
                        lastFillAt[0] = tick;
                    }
                    if (tick - lastFillAt[0] >= 1200) {
                        helper.assertTrue(hayFilled[0], "the hay's piece was never filled in:" + galleries.describe(level));
                        helper.assertTrue(galleries.filledLength() > 0, "no length given back:" + galleries.describe(level));
                        for (BlockPos piece : galleries.pieceHoles()) {
                            helper.assertTrue(galleries.isFirstArm(piece) || keptChain.contains(piece) || galleries.lastUsed(piece) >= agedAt[0],
                                    "the piece to " + helper.relativePos(piece) + " led to no food for 11 days and was not filled in:"
                                            + galleries.describe(level));
                        }
                        // Dead bushes the colony knows 8 blocks past a first arm's end, clear of every hole: they pull that tip.
                        java.util.List<BlockPos> holes = colony.foragingHoles().tunnels(level).stream().map(NestPlan.ForagingTunnel::hole).toList();
                        for (BlockPos arm : firstArms) {
                            if (!galleries.isLeaf(arm)) {
                                continue;
                            }
                            double out = Math.hypot(arm.getX() - center.getX(), arm.getZ() - center.getZ());
                            double k = (out + 8) / out;
                            BlockPos aim = new BlockPos((int) Math.round(center.getX() + (arm.getX() - center.getX()) * k), hay.getY(),
                                    (int) Math.round(center.getZ() + (arm.getZ() - center.getZ()) * k));
                            if (holes.stream().allMatch(hole -> Math.hypot(hole.getX() - aim.getX(), hole.getZ() - aim.getZ()) > 7.5)) {
                                pullTip[0] = arm;
                                pullAim[0] = aim;
                                break;
                            }
                        }
                        helper.assertTrue(pullTip[0] != null, "no first arm's end clear for the dead bushes:" + galleries.describe(level));
                        for (int dx = -1; dx <= 1; dx++) {
                            BlockPos bush = pullAim[0].offset(dx, 0, 0);
                            level.setBlock(bush, Blocks.DEAD_BUSH.defaultBlockState(), 3);
                            colony.foodMap().knowForTests(bush);
                        }
                        colony.food().rescanPlantsSoon();
                        filledBeforePull[0] = galleries.filledLength();
                        SavannaMod.LOGGER.info("[web-test] tick {}: {} pieces filled in; dead bushes at {} past the arm to {}", tick,
                                galleries.fills(), helper.relativePos(pullAim[0]), helper.relativePos(pullTip[0]));
                        since[0] = 0;
                        stage[0] = 5;
                    }
                }
                case 5 -> { // the dead bushes pull the tip
                    for (BlockPos piece : galleries.pieceHoles()) {
                        if (pullTip[0].equals(galleries.parentOf(piece))) {
                            double before = Math.hypot(pullTip[0].getX() - pullAim[0].getX(), pullTip[0].getZ() - pullAim[0].getZ());
                            double after = Math.hypot(piece.getX() - pullAim[0].getX(), piece.getZ() - pullAim[0].getZ());
                            helper.assertTrue(after < before - 1, "the tip grew from " + helper.relativePos(pullTip[0]) + " to "
                                    + helper.relativePos(piece) + ", not toward the dead bushes at " + helper.relativePos(pullAim[0]));
                            helper.assertTrue(galleries.filledLength() < filledBeforePull[0], "growing toward food did not use the length given back");
                            // Four hay bales the colony knows, each 3 blocks from a different hole (the user's bug,
                            // 2026-10-17: known hay near a hole, with the 3 runways in use, got neither a runway nor a piece).
                            java.util.List<BlockPos> holes = tunnels.stream().map(NestPlan.ForagingTunnel::hole).toList();
                            java.util.Set<BlockPos> cells = new java.util.HashSet<>();
                            tunnels.forEach(t -> cells.addAll(t.tunnel()));
                            for (BlockPos hole : holes) {
                                if (nearHay.size() == 4 || colony.nest().foodWays().isSpent(hole) || colony.nest().foodWays().runwayAt(hole) != null) {
                                    continue;
                                }
                                for (int[] side : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
                                    BlockPos spot = new BlockPos(hole.getX() + side[0], hay.getY(), hole.getZ() + side[1]);
                                    boolean clear = helper.getBounds().inflate(-2, 0, -2).contains(spot.getX() + 0.5, helper.getBounds().minY + 1, spot.getZ() + 0.5)
                                            && level.getBlockState(spot).isAir() && level.getBlockState(spot.below()).isSolidRender(level, spot.below())
                                            && holes.stream().allMatch(h -> Math.hypot(h.getX() - spot.getX(), h.getZ() - spot.getZ()) >= 2)
                                            && cells.stream().noneMatch(c -> c.getX() == spot.getX() && c.getZ() == spot.getZ())
                                            && nearHay.stream().allMatch(o -> Math.hypot(o.getX() - spot.getX(), o.getZ() - spot.getZ()) >= 6);
                                    if (clear) {
                                        level.setBlock(spot, Blocks.HAY_BLOCK.defaultBlockState(), 3);
                                        colony.foodMap().knowForTests(spot);
                                        nearHay.add(spot);
                                        break;
                                    }
                                }
                            }
                            helper.assertTrue(nearHay.size() == 4, "found room for only " + nearHay.size() + " hay bales by the " + holes.size() + " holes");
                            colony.food().rescanPlantsSoon();
                            SavannaMod.LOGGER.info("[web-test] tick {}: hay by the holes at {}", tick, nearHay.stream().map(helper::relativePos).toList());
                            since[0] = 0;
                            stage[0] = 6;
                            return;
                        }
                    }
                    helper.assertTrue(++since[0] < 1500, "the dead bushes past the arm's end did not pull its tip:" + galleries.describe(level));
                }
                case 6 -> { // hay by the holes: each gets a runway or a piece of its own (only 3 runways at a time)
                    java.util.List<BlockPos> served = new java.util.ArrayList<>();
                    colony.nest().foodWays().getRunways().forEach(runway -> served.add(runway.food()));
                    int pieces = 0;
                    for (com.antcolonies.colony.FoodWays.NewTunnel tunnel : colony.nest().foodWays().getNewTunnels()) {
                        served.add(tunnel.food());
                        if (nearHay.contains(tunnel.food())) {
                            pieces++;
                            helper.assertTrue(galleries.hasPiece(tunnel.tunnel().hole()), "the tunnel to the hay by a hole is not a gallery piece");
                        }
                    }
                    java.util.List<BlockPos> unserved = nearHay.stream().filter(bale -> !served.contains(bale)).toList();
                    if (unserved.isEmpty()) {
                        helper.assertTrue(pieces >= 1, "4 hay bales by holes and only 3 runways may be made, but no bale got a piece");
                        SavannaMod.LOGGER.info("[web-test] tick {}: the hay by the holes: {} runways, {} pieces", tick,
                                colony.nest().foodWays().getRunways().size(), pieces);
                        stage[0] = 7;
                    } else {
                        helper.assertTrue(++since[0] < 2000, "the known hay at " + helper.relativePos(unserved.get(0))
                                + " by a hole got neither a runway nor a gallery piece:" + galleries.describe(level)
                                + colony.foragingHoles().describeNewHoles(level) + colony.foragingHoles().describeRunways(level)
                                + colony.foragingHoles().whyNoRunway(level, unserved.get(0)));
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(stage[0] == 7, "the galleries' growth and filling in did not finish (stage " + stage[0] + ")"));
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
