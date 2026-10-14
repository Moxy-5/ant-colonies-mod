package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Alarm;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The alarm (M7 step 6c): chickens hunt termite workers, minors bite them, the foragers hurry home, footsteps bring two
 * minors to look and drum, a break-in spreads the drumming through the nest and sends the minors to the breach with
 * the majors behind them while the workers seal it, and repeated break-ins make it level 4 (everybody in, more soldiers
 * wanted, kept after a reload).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteAlarmTests {
    /** A chicken walks up to a termite worker standing in the open and pecks it to death. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aChickenHuntsATermiteWorker(GameTestHelper helper) {
        TermiteMoundTests.deepGround(helper);
        TermiteWorkerEntity termite = TestAnts.loneTermite(helper, MIDDLE, SURFACE, MIDDLE);
        Chicken chicken = helper.spawn(EntityType.CHICKEN, MIDDLE + 5, SURFACE, MIDDLE);
        helper.succeedWhen(() -> {
            helper.assertFalse(termite.isAlive() && termite.getHealth() >= termite.getMaxHealth(), "the chicken has not pecked the termite");
            helper.assertFalse(termite.isAlive(), "the termite is still alive (" + termite.getHealth() + " health)");
            helper.assertTrue(chicken.isAlive(), "the chicken died");
        });
    }

    /**
     * A chicken comes for the foragers at night. A colony with its nest dug out and both foraging holes open, five old
     * workers (foragers) cutting at a hay bale 11 blocks out, three minors out on guard. A chicken is put down at the
     * hay: the alarm goes up to 2, the foraging stops (even though it is "night"), the minors feel it, go at the chicken
     * and bite it until it is dead, and the workers all go in.
     */
    @GameTest(template = "arena", timeoutTicks = 8000, skyAccess = true)
    public static void minorsBiteAChickenThatHuntsTheForagersWhoHurryHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 5; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i, SURFACE, MIDDLE - 2);
            }
        });
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        BlockPos[] hay = new BlockPos[1];
        Chicken[] chicken = new Chicken[1];
        long[] at = {-1, -1}; // when the chicken came, when the alarm went up
        Set<UUID> biters = new HashSet<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            colony.setForagingTimeForTests(false);
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(old);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            level.setBlock(colony.nest().getEntrance(), Blocks.STONE.defaultBlockState(), 3);
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                level.setBlock(tunnel.hole(), Blocks.AIR.defaultBlockState(), 3);
            }
            hay[0] = tunnels.get(0).hole().above().relative(tunnels.get(0).side().getClockWise(), 11);
            level.setBlock(hay[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                minors.add(TermiteSoldierTests.put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
        });
        helper.runAfterDelay(40, () -> TermiteMoundTests.colony(helper).setForagingTimeForTests(true));
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || hay[0] == null || helper.getTick() < 40) {
                return;
            }
            long tick = helper.getTick();
            if (chicken[0] == null) {
                // The chicken comes once a forager is at the hay and a minor is out on guard.
                boolean foragerAtTheHay = !level.getEntitiesOfClass(TermiteWorkerEntity.class, new AABB(hay[0]).inflate(2),
                        worker -> colony.getId().equals(worker.getColonyId())).isEmpty();
                boolean minorOut = minors.stream().anyMatch(minor -> !colony.nest().isInside(level, minor.blockPosition()));
                if (foragerAtTheHay && minorOut) {
                    BlockPos spot = hay[0].relative(Direction.UP);
                    chicken[0] = EntityType.CHICKEN.create(level);
                    chicken[0].moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0F, 0.0F);
                    level.addFreshEntity(chicken[0]);
                    at[0] = tick;
                    AntColoniesMod.LOGGER.info("[alarm-test] tick {}: a chicken at the hay; {}", tick, colony.alarm().describe(level));
                }
                return;
            }
            if (at[1] < 0 && colony.alarm().level(level) >= 2) {
                at[1] = tick;
                AntColoniesMod.LOGGER.info("[alarm-test] tick {}: alarm up {} ticks after the chicken came; {}", tick, tick - at[0],
                        colony.alarm().describe(level));
            }
            if (at[1] >= 0) {
                helper.assertFalse(colony.isForagingTime(level), "still foraging time with the alarm up");
            }
            for (TermiteSoldierEntity minor : minors) {
                if (minor.isBiting()) {
                    biters.add(minor.getUUID());
                }
            }
            if (tick % 500 == 0) {
                AntColoniesMod.LOGGER.info("[alarm-test] tick {}: chicken {} health {}, minors {}; {}", tick, chicken[0].blockPosition().toShortString(),
                        chicken[0].getHealth(), minors.stream().map(m -> m.blockPosition().toShortString() + (m.isDrumming() ? " drumming" : "")).toList(),
                        colony.alarm().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && chicken[0] != null, "no chicken yet");
            helper.assertTrue(at[1] >= 0, "the alarm did not go up");
            helper.assertTrue(minors.stream().allMatch(minor -> colony.alarm().hasHeard(minor.getUUID())), "not every minor felt the alarm");
            helper.assertFalse(biters.isEmpty(), "no minor has bitten the chicken");
            helper.assertFalse(chicken[0].isAlive(), "the chicken is still alive (" + chicken[0].getHealth() + " health)");
            boolean workerOut = !level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds().inflate(8),
                    worker -> colony.getId().equals(worker.getColonyId()) && !colony.nest().isInside(level, worker.blockPosition())).isEmpty();
            helper.assertFalse(workerOut, "a worker is still outside");
            AntColoniesMod.LOGGER.info("[alarm-test] tick {}: the chicken is dead, bitten by {} minors, the workers are in", helper.getTick(), biters.size());
        });
    }

    /**
     * A break-in by day. A colony with its royal chamber and first garden dug, three builders, three minors and two majors
     * on their posts. A player breaks a side wall of the garden: the alarm goes to 3, the drumming spreads from soldier
     * to soldier (they feel it at different times), a minor stands in the hole itself, the workers seal the hole behind
     * it, and once everyone has felt it every soldier stands at its spot: the minors nearest the breach, the majors
     * behind them.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true)
    public static void aBreakInSpreadsTheDrummingAndSendsTheSoldiersToTheBreach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        List<TermiteSoldierEntity> soldiers = new ArrayList<>();
        BlockPos[] breach = new BlockPos[1];
        boolean[] minorInTheBreach = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.setForagingTimeForTests(false);
            TermiteSoldierTests.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                TermiteWorkerEntity worker = ModEntities.TERMITE_WORKER.get().create(level);
                BlockPos cell = room.get(room.size() - 1 - i);
                worker.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(worker);
            }
            for (int i = 0; i < 5; i++) {
                soldiers.add(TermiteSoldierTests.put(level, i < 3 ? ModEntities.TERMITE_MINOR_SOLDIER.get() : ModEntities.TERMITE_MAJOR_SOLDIER.get(),
                        room.get(i)));
            }
        });
        helper.runAfterDelay(300, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            NestPlan.Chamber garden = colony.nest().getGardens(level).get(0);
            Set<BlockPos> planned = new HashSet<>();
            for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
                planned.addAll(chamber.cells());
            }
            for (NestPlan.Chamber room : colony.nest().getGardens(level)) {
                planned.addAll(room.cells());
            }
            cut:
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int along : new int[] {-1, 1}) {
                    BlockPos wall = garden.center().relative(side, 2).relative(side.getClockWise(), along);
                    if (!planned.contains(wall)) {
                        breach[0] = wall;
                        break cut;
                    }
                }
            }
            // As the game does it: the break is reported, then the block goes.
            colony.alarm().blockBroken(level, breach[0], null);
            level.setBlock(breach[0], Blocks.AIR.defaultBlockState(), 3);
            AntColoniesMod.LOGGER.info("[alarm-test] tick {}: broke into the garden at {}; {}", helper.getTick(), breach[0].toShortString(),
                    colony.alarm().describe(level));
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || breach[0] == null) {
                return;
            }
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.getRank() == Rank.MINOR && soldier.blockPosition().equals(breach[0])) {
                    minorInTheBreach[0] = true;
                }
            }
            if (helper.getTick() % 200 == 0) {
                AntColoniesMod.LOGGER.info("[alarm-test] tick {}: soldiers {}; {}", helper.getTick(),
                        soldiers.stream().map(s -> s.getRank() + " " + s.blockPosition().toShortString() + (s.isDrumming() ? " drumming" : "")).toList(),
                        colony.alarm().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && breach[0] != null, "not broken into yet");
            helper.assertTrue(colony.alarm().level(level) >= 3, "the alarm is not at 3");
            helper.assertTrue(soldiers.stream().allMatch(soldier -> colony.alarm().hasHeard(soldier.getUUID())), "not every soldier felt the alarm");
            Set<Long> times = new HashSet<>();
            soldiers.forEach(soldier -> times.add(colony.alarm().heardAt(soldier.getUUID())));
            helper.assertTrue(times.size() >= 2, "every soldier felt it at the same moment (no spreading)");
            helper.assertTrue(minorInTheBreach[0], "no minor stood in the breach");
            helper.assertTrue(colony.profile().nest().isPlug(level.getBlockState(breach[0])), "the breach is not sealed");
            double farthestMinor = 0;
            double nearestMajor = Double.MAX_VALUE;
            for (TermiteSoldierEntity soldier : soldiers) {
                Alarm.Task task = colony.alarm().taskFor(level, soldier);
                helper.assertTrue(task != null && task.cell() != null && soldier.blockPosition().equals(task.cell()),
                        soldier.getRank() + " at " + soldier.blockPosition().toShortString() + " is not at its spot " + (task == null ? null : task.cell()));
                double distance = task.cell().distSqr(breach[0]);
                if (soldier.getRank() == Rank.MINOR) {
                    farthestMinor = Math.max(farthestMinor, distance);
                } else {
                    nearestMajor = Math.min(nearestMajor, distance);
                }
            }
            helper.assertTrue(farthestMinor <= nearestMajor, "a major stood nearer the breach than a minor");
        });
    }

    /**
     * Three break-ins within five minutes make it level 4: no foraging (even at "night"), the colony wants twice as many
     * soldiers; the level and the boost are kept after a reload. Footsteps after that change nothing (lower level).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void repeatedBreakInsMakeItLevelFourAndTheColonyWantsMoreSoldiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.setForagingTimeForTests(true);
            colony.setCount(WorkerAntEntity.CASTE, 20);
            helper.assertTrue(colony.soldiers().wanted(level, Rank.MINOR) == 2 && colony.soldiers().wanted(level, Rank.MAJOR) == 1,
                    "20 workers should want 2 minors and 1 major");
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos chamber = colony.nest().getHomeChamber(level).center();
            // Three blocks of the royal chamber's walls: under it, over it, and a side wall.
            BlockPos side = Direction.Plane.HORIZONTAL.stream().map(direction -> chamber.relative(direction, 2))
                    .filter(wall -> colony.alarm().isBreakIn(level, wall)).findFirst().orElseThrow();
            BlockPos[] walls = {chamber.below(), chamber.above(), side};
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(colony.alarm().isBreakIn(level, walls[i]), "breaking " + walls[i].toShortString() + " is no break-in");
                colony.alarm().blockBroken(level, walls[i], null);
                helper.assertTrue(colony.alarm().level(level) == (i < 2 ? 3 : 4), "after " + (i + 1) + " break-ins the alarm is at "
                        + colony.alarm().level(level));
            }
            helper.assertFalse(colony.isForagingTime(level), "foraging during a level 4 alarm");
            helper.assertTrue(colony.soldiers().wanted(level, Rank.MINOR) == 3 && colony.soldiers().wanted(level, Rank.MAJOR) == 1,
                    "after a level 4 the colony should want 3 minors and 1 major, it wants " + colony.soldiers().wanted(level, Rank.MINOR) + " and "
                            + colony.soldiers().wanted(level, Rank.MAJOR));
            colony.alarm().raise(level, 1, chamber, null);
            helper.assertTrue(colony.alarm().level(level) == 4, "footsteps lowered the alarm");
            Colony loaded = Colony.load(colony.save());
            helper.assertTrue(loaded.alarm().level(level) == 4 && loaded.alarm().soldierBoost(level) > 1.0F, "the alarm was not kept after a reload");
            // A block far from the nest and the mound is no break-in.
            helper.assertFalse(colony.alarm().isBreakIn(level, chamber.offset(8, 0, 8)), "a block away from the nest counted as a break-in");
            colony.alarm().calmForTests();
            helper.succeed();
        });
    }

    /**
     * Footsteps on the mound (level 1) by day: the two minors nearest come to the nest cells nearest the spot and drum
     * once; the third minor stays at its post; the alarm calms down again after its calm time (30 seconds).
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void footstepsBringTwoMinorsToLookAndDrum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        Set<UUID> drummedAtTheSpot = new HashSet<>();
        long[] raised = {-1};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.setForagingTimeForTests(false);
            TermiteSoldierTests.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                minors.add(TermiteSoldierTests.put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            colony.alarm().raise(level, 1, colony.getCenter().above(), null);
            raised[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || raised[0] < 0 || colony.alarm().level(level) == 0) {
                return;
            }
            for (TermiteSoldierEntity minor : minors) {
                Alarm.Task task = colony.alarm().taskFor(level, minor);
                if (task != null && minor.blockPosition().equals(task.cell()) && minor.isDrumming()) {
                    drummedAtTheSpot.add(minor.getUUID());
                }
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && raised[0] >= 0, "no footsteps yet");
            helper.assertTrue(drummedAtTheSpot.size() == 2, drummedAtTheSpot.size() + " minors came to look and drummed, not 2");
            helper.assertTrue(colony.alarm().level(level) == 0, "the alarm has not calmed down");
        });
    }
}
