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
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
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
            TestClock.still(helper, 12.0); // midday: everybody stays in
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
            hay[0] = tunnels.get(0).hole().above().relative(TermiteSoldierTests.intoArena(tunnels.get(0).side().getClockWise()), 11);
            colony.foodMap().knowForTests(hay[0]); // (finding it is tested in TermiteFoodTests)
            level.setBlock(hay[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                minors.add(TermiteSoldierTests.put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
        });
        helper.runAfterDelay(40, () -> TestClock.still(helper, 0.0)); // midnight: the foragers go out
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
                    AntColoniesMod.LOGGER.info("[alarm-test] tick {}: a chicken at the hay; {}", tick, colony.part(ColonyParts.ALARM).orElseThrow().describe(level));
                }
                return;
            }
            if (at[1] < 0 && colony.part(ColonyParts.ALARM).orElseThrow().level(level) >= 2) {
                at[1] = tick;
                AntColoniesMod.LOGGER.info("[alarm-test] tick {}: alarm up {} ticks after the chicken came; {}", tick, tick - at[0],
                        colony.part(ColonyParts.ALARM).orElseThrow().describe(level));
            }
            if (at[1] >= 0) {
                if (colony.part(ColonyParts.ALARM).orElseThrow().level(level) >= 2) { // (it calms down again after 30 seconds, and foraging starts again)
                    helper.assertFalse(colony.isForagingTime(level), "still foraging time with the alarm up");
                }
            }
            for (TermiteSoldierEntity minor : minors) {
                if (minor.isBiting()) {
                    biters.add(minor.getUUID());
                }
            }
            if (tick % 500 == 0) {
                AntColoniesMod.LOGGER.info("[alarm-test] tick {}: chicken {} health {}, minors {}; {}", tick, chicken[0].blockPosition().toShortString(),
                        chicken[0].getHealth(), minors.stream().map(m -> m.blockPosition().toShortString() + (m.isDrumming() ? " drumming" : "")).toList(),
                        colony.part(ColonyParts.ALARM).orElseThrow().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && chicken[0] != null, "no chicken yet");
            helper.assertTrue(at[1] >= 0, "the alarm did not go up");
            // At least one: the first minor may kill the chicken (two bites) before the drumming reaches the others 15 blocks
            // off, and they go in behind the workers (a full run, 2026-10-14: only 1 of 3 had felt it, the chicken dead).
            helper.assertTrue(minors.stream().anyMatch(minor -> colony.part(ColonyParts.ALARM).orElseThrow().hasHeard(minor.getUUID())), "no minor felt the alarm");
            helper.assertFalse(biters.isEmpty(), "no minor has bitten the chicken");
            helper.assertFalse(chicken[0].isAlive(), "the chicken is still alive (" + chicken[0].getHealth() + " health)");
            boolean workerOut = !level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds().inflate(8),
                    worker -> colony.getId().equals(worker.getColonyId()) && !colony.nest().isInside(level, worker.blockPosition())).isEmpty();
            helper.assertFalse(workerOut, "a worker is still outside");
            AntColoniesMod.LOGGER.info("[alarm-test] tick {}: the chicken is dead, bitten by {} minors, the workers are in", helper.getTick(), biters.size());
        });
    }

    /**
     * The user's bug (2026-10-13): a young colony at night whose only way out is the open hole in the middle of the mound;
     * eight foragers out at a dead bush, minors out on guard. A chicken comes: the alarm goes to 2 and the colony stays in, so
     * the middle hole is to be plugged; in the game the termites crowded the hole and the plugging went on and on, until
     * the alarm calmed down (2 minutes). The mound is young: its hollow shaft over the hole is not roofed over yet, and
     * the workers coming home stopped in it (it counted as inside), clinging to its walls above the hole. Checked: once the chicken is dead and everybody is in, the middle hole is plugged
     * soon after (within 20 seconds), while the alarm is still up, or roofed over: the builders sometimes put the plan's
     * tunnel block back over it first (it failed 1 run in 3 that way, 2026-10-14), and then it is closed to the sky.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true)
    public static void theMiddleHoleIsPluggedSoonAfterAChickenAlarm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 10; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 4, SURFACE, MIDDLE - 2 + i / 4);
            }
        });
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        BlockPos[] bush = new BlockPos[1];
        Chicken[] chicken = new Chicken[1];
        long[] at = {-1, -1, -1}; // the chicken came, the fight was over (chicken dead or the minors back in), everybody was in
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            List<TermiteWorkerEntity> workers = level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds());
            for (int i = 0; i < workers.size(); i++) {
                workers.get(i).setGrownUpAt(i < 8 ? old : level.getGameTime()); // 8 foragers (a crowd at the hole), 2 young ones inside
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
                level.setBlock(tunnel.hole(), Blocks.STONE.defaultBlockState(), 3); // no foraging holes yet: only the middle one
            }
            // A young mound still being built: its hollow shaft over the hole is not roofed over yet (open to the sky).
            colony.part(ColonyParts.MOUND).orElseThrow().buildInstantly(level, 3);
            for (java.util.Map.Entry<BlockPos, com.antcolonies.colony.MoundPlan.Part> part : colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).entrySet()) {
                if (part.getValue() == com.antcolonies.colony.MoundPlan.Part.OPEN) {
                    for (BlockPos above = part.getKey().above(); above.getY() < part.getKey().getY() + 12; above = above.above()) {
                        level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            level.setBlock(colony.nest().getEntrance(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!colony.nest().isEntranceCovered(level), "the mound's shaft is roofed over");
            bush[0] = colony.nest().getEntrance().offset(9, 0, 1); // (clear of the mound)
            level.setBlock(bush[0], Blocks.DEAD_BUSH.defaultBlockState(), 3);
            colony.foodMap().knowForTests(bush[0]);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 2; i++) {
                minors.add(TermiteSoldierTests.put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
            TestClock.still(helper, 0.0); // midnight: foraging time
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || bush[0] == null) {
                return;
            }
            long tick = helper.getTick();
            BlockPos hole = colony.nest().getEntrance();
            if (chicken[0] == null) {
                boolean workerOut = !level.getEntitiesOfClass(TermiteWorkerEntity.class, new AABB(bush[0]).inflate(3),
                        worker -> colony.getId().equals(worker.getColonyId())).isEmpty();
                if (workerOut && minors.stream().anyMatch(minor -> !colony.nest().isInside(level, minor.blockPosition()))) {
                    chicken[0] = EntityType.CHICKEN.create(level);
                    chicken[0].moveTo(bush[0].getX() + 0.5, bush[0].getY(), bush[0].getZ() + 1.5, 0.0F, 0.0F);
                    level.addFreshEntity(chicken[0]);
                    at[0] = tick;
                }
                return;
            }
            // Over when the chicken is dead, or when the minors have gone in behind the workers and left it outside
            // (real soldiers guard the foragers, then go in last and the hole is sealed).
            if (at[1] < 0 && (!chicken[0].isAlive() || !colony.part(ColonyParts.SOLDIERS).orElseThrow().minorsOut(level))) {
                at[1] = tick;
            }
            if (at[1] >= 0 && at[2] < 0 && !colony.foragingHoles().isAnyoneOutside(level)) {
                at[2] = tick;
            }
            if (tick % 500 == 0) {
                StringBuilder all = new StringBuilder();
                for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                    all.append("\n    worker ").append(worker.getId()).append(" at ").append(worker.blockPosition().toShortString())
                            .append(" ").append(colony.roleOf(level, worker)).append(worker.isIdle() ? " idle" : "")
                            .append(" ").append(worker.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                    .map(g -> g.getGoal().getClass().getSimpleName()).toList());
                }
                AntColoniesMod.LOGGER.info("[middle-hole] tick {} above {} part {} covered {} height {} workers:{}", tick,
                        level.getBlockState(hole.above()), colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).get(hole.above()), colony.nest().isEntranceCovered(level),
                        level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hole.getX(), hole.getZ()) - hole.getY(), all);
            }
            if (tick % 100 == 0) {
                StringBuilder near = new StringBuilder();
                for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(hole).inflate(1, 2, 1))) {
                    near.append("\n    ").append(ant.getClass().getSimpleName()).append(" at ").append(ant.blockPosition().toShortString())
                            .append(" ").append(ant.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                    .map(g -> g.getGoal().getClass().getSimpleName()).toList());
                }
                AntColoniesMod.LOGGER.info("[middle-hole] tick {}: chicken {}, alarm {}, foraging {}, hole {} ({}), wants plugging {}, anyone outside {}, minors out {}{}",
                        tick, chicken[0].isAlive() ? "alive" : "dead", colony.part(ColonyParts.ALARM).orElseThrow().level(level), colony.isForagingTime(level),
                        ForagingHoles.isOpen(level, hole) ? "open" : "shut", level.getBlockState(hole).getBlock().getName().getString(),
                        colony.foragingHoles().wantsPlugging(level), colony.foragingHoles().isAnyoneOutside(level),
                        colony.part(ColonyParts.SOLDIERS).orElseThrow().minorsOut(level), near);
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(chicken[0] != null, "no chicken yet: no forager reached the bush while a minor was outside");
            helper.assertTrue(at[1] >= 0, "the chicken is alive and the minors are still out (it came at tick " + at[0] + ")");
            helper.assertTrue(at[2] >= 0, "not everybody is in yet");
            // Plugged, or roofed over: the builders may put the mound's tunnel block back over it first (the plan has one
            // there), and then it opens only into the mound's tunnels, no longer to the sky (by design it is then not plugged).
            helper.assertTrue(!ForagingHoles.isOpen(level, colony.nest().getEntrance()) || colony.nest().isEntranceCovered(level),
                    "the middle hole is still open to the sky");
            helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().level(level) >= 2 || helper.getTick() - at[2] <= 400,
                    "the middle hole was only plugged " + (helper.getTick() - at[2]) + " ticks after everybody was in");
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
            TestClock.still(helper, 12.0); // midday: everybody stays in
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
            colony.part(ColonyParts.ALARM).orElseThrow().blockBroken(level, breach[0], null);
            level.setBlock(breach[0], Blocks.AIR.defaultBlockState(), 3);
            AntColoniesMod.LOGGER.info("[alarm-test] tick {}: broke into the garden at {}; {}", helper.getTick(), breach[0].toShortString(),
                    colony.part(ColonyParts.ALARM).orElseThrow().describe(level));
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
                        colony.part(ColonyParts.ALARM).orElseThrow().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && breach[0] != null, "not broken into yet");
            helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().level(level) >= 3, "the alarm is not at 3");
            helper.assertTrue(soldiers.stream().allMatch(soldier -> colony.part(ColonyParts.ALARM).orElseThrow().hasHeard(soldier.getUUID())), "not every soldier felt the alarm");
            Set<Long> times = new HashSet<>();
            soldiers.forEach(soldier -> times.add(colony.part(ColonyParts.ALARM).orElseThrow().heardAt(soldier.getUUID())));
            helper.assertTrue(times.size() >= 2, "every soldier felt it at the same moment (no spreading)");
            helper.assertTrue(minorInTheBreach[0], "no minor stood in the breach");
            helper.assertTrue(colony.profile().nest().isPlug(level.getBlockState(breach[0])), "the breach is not sealed");
            double farthestMinor = 0;
            double nearestMajor = Double.MAX_VALUE;
            for (TermiteSoldierEntity soldier : soldiers) {
                Alarm.Task task = colony.part(ColonyParts.ALARM).orElseThrow().taskFor(level, soldier);
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
            TestClock.still(helper, 0.0); // midnight: foraging time
            colony.setCount(WorkerAntEntity.CASTE, 20);
            helper.assertTrue(colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) == 2 && colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR) == 1,
                    "20 workers should want 2 minors and 1 major");
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos chamber = colony.nest().getHomeChamber(level).center();
            // Three blocks of the royal chamber's walls: under it, over it, and a side wall.
            BlockPos side = Direction.Plane.HORIZONTAL.stream().map(direction -> chamber.relative(direction, 2))
                    .filter(wall -> colony.part(ColonyParts.ALARM).orElseThrow().isBreakIn(level, wall)).findFirst().orElseThrow();
            BlockPos[] walls = {chamber.below(), chamber.above(), side};
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().isBreakIn(level, walls[i]), "breaking " + walls[i].toShortString() + " is no break-in");
                colony.part(ColonyParts.ALARM).orElseThrow().blockBroken(level, walls[i], null);
                helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().level(level) == (i < 2 ? 3 : 4), "after " + (i + 1) + " break-ins the alarm is at "
                        + colony.part(ColonyParts.ALARM).orElseThrow().level(level));
            }
            helper.assertFalse(colony.isForagingTime(level), "foraging during a level 4 alarm");
            helper.assertTrue(colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) == 3 && colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR) == 1,
                    "after a level 4 the colony should want 3 minors and 1 major, it wants " + colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) + " and "
                            + colony.part(ColonyParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR));
            colony.part(ColonyParts.ALARM).orElseThrow().raise(level, 1, chamber, null);
            helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().level(level) == 4, "footsteps lowered the alarm");
            Colony loaded = Colony.load(colony.save());
            helper.assertTrue(loaded.part(ColonyParts.ALARM).orElseThrow().level(level) == 4 && loaded.part(ColonyParts.ALARM).orElseThrow().soldierBoost(level) > 1.0F, "the alarm was not kept after a reload");
            // A block far from the nest and the mound is no break-in.
            helper.assertFalse(colony.part(ColonyParts.ALARM).orElseThrow().isBreakIn(level, chamber.offset(8, 0, 8)), "a block away from the nest counted as a break-in");
            colony.part(ColonyParts.ALARM).orElseThrow().calmForTests();
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
            TestClock.still(helper, 12.0); // midday: everybody stays in
            TermiteSoldierTests.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                minors.add(TermiteSoldierTests.put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            colony.part(ColonyParts.ALARM).orElseThrow().raise(level, 1, colony.getCenter().above(), null);
            raised[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || raised[0] < 0 || colony.part(ColonyParts.ALARM).orElseThrow().level(level) == 0) {
                return;
            }
            for (TermiteSoldierEntity minor : minors) {
                Alarm.Task task = colony.part(ColonyParts.ALARM).orElseThrow().taskFor(level, minor);
                if (task != null && minor.blockPosition().equals(task.cell()) && minor.isDrumming()) {
                    drummedAtTheSpot.add(minor.getUUID());
                }
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && raised[0] >= 0, "no footsteps yet");
            helper.assertTrue(drummedAtTheSpot.size() == 2, drummedAtTheSpot.size() + " minors came to look and drummed, not 2");
            helper.assertTrue(colony.part(ColonyParts.ALARM).orElseThrow().level(level) == 0, "the alarm has not calmed down");
        });
    }
}
