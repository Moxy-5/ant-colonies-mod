package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.colony.Caste;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.Alarm;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

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
 * The alarm (M7 step 6c; local since the rework's step 3, 2026-11-12): chickens hunt termite workers, soldiers bite
 * them, the foragers there hurry home, footsteps bring the minors within earshot to look and drum, a break-in spreads
 * the drumming through the nest and sends the soldiers to the breach while the workers seal it; break-ins again and
 * again are still only breaches (no level 4, nothing kept after a reload, the rest of the colony carries on).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteAlarmTests {
    /**
     * A chicken comes for the foragers at night. A colony with its nest dug out and both foraging holes open, five old
     * workers (foragers) cutting at a hay bale 11 blocks out, three minors out on guard. A chicken is put down at the
     * hay: a foe is felt there (the workers there go in; the rework, step 3: no longer the whole colony), the minors feel it, go at the chicken
     * and bite it until it is dead, and the workers all go in.
     */
    @GameTest(template = "arena", timeoutTicks = 8000, skyAccess = true)
    public static void minorsBiteAChickenThatHuntsTheForagersWhoHurryHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        BlockPos[] hay = new BlockPos[1];
        Chicken[] chicken = new Chicken[1];
        long[] at = {-1, -1, -1}; // when the chicken came, when the alarm went up, when every worker where it is felt was in
        Set<UUID> biters = new HashSet<>();
        Set<UUID> heard = new HashSet<>();
        TermiteScenes.colony(helper, TermiteScenes.spots(5, i -> new BlockPos(MIDDLE - 2 + i, SURFACE, MIDDLE - 2)), colony -> {
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            TestClock.still(helper, 12.0); // midday: everybody stays in
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : TestIsolation.insectsOf(helper, TermiteWorkerEntity.class)) {
                worker.setGrownUpAt(old);
            }
            TermiteScenes.digNestAtOnce(level, colony);
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
                minors.add(TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
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
                    SavannaMod.LOGGER.info("[alarm-test] tick {}: a chicken at the hay; {}", tick, colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
                }
                return;
            }
            if (at[1] < 0 && colony.part(SavannaParts.ALARM).orElseThrow().anyKeptIn(level)) {
                at[1] = tick;
                SavannaMod.LOGGER.info("[alarm-test] tick {}: alarm up {} ticks after the chicken came; {}", tick, tick - at[0],
                        colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
            }
            // (the rework, step 3: an alarm no longer ends the foraging time for the whole colony, so that check is gone;
            // the workers at the hay going in is checked at the end)
            for (TermiteSoldierEntity minor : minors) {
                if (minor.isBiting()) {
                    biters.add(minor.getUUID());
                }
                if (colony.part(SavannaParts.ALARM).orElseThrow().hasHeard(minor.getUUID())) {
                    heard.add(minor.getUUID());
                }
            }
            // The rework, step 3: the workers where the foe is felt go in (not the whole colony any more).
            if (at[1] >= 0 && at[2] < 0 && colony.part(SavannaParts.ALARM).orElseThrow().anyKeptIn(level)
                    && TestIsolation.insectsOf(helper, 8, TermiteWorkerEntity.class, worker -> colony.getId().equals(worker.getColonyId())
                            && !colony.nest().isInside(level, worker.blockPosition()) && colony.isAlarmed(level, worker.blockPosition())).isEmpty()) {
                at[2] = tick;
            }
            if (tick % 500 == 0) {
                SavannaMod.LOGGER.info("[alarm-test] tick {}: chicken {} health {}, minors {}; {}", tick, chicken[0].blockPosition().toShortString(),
                        chicken[0].getHealth(), minors.stream().map(m -> m.blockPosition().toShortString() + (m.isDrumming() ? " drumming" : "")).toList(),
                        colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && chicken[0] != null, "no chicken yet");
            helper.assertTrue(at[1] >= 0, "the alarm did not go up");
            // At least one: the first minor may kill the chicken (two bites) before the drumming reaches the others 15 blocks
            // off, and they go in behind the workers (a full run, 2026-10-14: only 1 of 3 had felt it, the chicken dead).
            helper.assertFalse(heard.isEmpty(), "no minor felt the alarm");
            helper.assertFalse(biters.isEmpty(), "no minor has bitten the chicken");
            helper.assertFalse(chicken[0].isAlive(), "the chicken is still alive (" + chicken[0].getHealth() + " health)");
            helper.assertTrue(at[2] >= 0, "a worker stayed outside where the foe was felt");
            SavannaMod.LOGGER.info("[alarm-test] tick {}: the chicken is dead, bitten by {} minors, the workers are in", helper.getTick(), biters.size());
        });
    }

    /**
     * The rework, step 3 (2026-11-12): the alarm no longer keeps the whole colony in, and plugs no hole itself, so the
     * scene now checks what is left of it: once the chicken has come, every worker outside where the foe is felt goes in
     * while it is still felt (they crowded the open shaft over the hole before), and the hole is open to them all the while. The old
     * check (the middle hole plugged within 20 seconds of everybody being in) went with the rule it checked; holes being
     * plugged once the foraging time is over is checked in the foraging tests. The scene as it was written:
     *
     * <p>The user's bug (2026-10-13): a young colony at night whose only way out is the open hole in the middle of the mound;
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
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        BlockPos[] bush = new BlockPos[1];
        Chicken[] chicken = new Chicken[1];
        long[] at = {-1, -1, -1}; // the chicken came, the fight was over (chicken dead or the minors back in), everybody was in
        TermiteScenes.colony(helper, TermiteScenes.spots(10, i -> new BlockPos(MIDDLE - 2 + i % 4, SURFACE, MIDDLE - 2 + i / 4)), colony -> {
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            List<TermiteWorkerEntity> workers = TestIsolation.insectsOf(helper, TermiteWorkerEntity.class);
            for (int i = 0; i < workers.size(); i++) {
                workers.get(i).setGrownUpAt(i < 8 ? old : level.getGameTime()); // 8 foragers (a crowd at the hole), 2 young ones inside
            }
            TermiteScenes.digNestAtOnce(level, colony);
            for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
                level.setBlock(tunnel.hole(), Blocks.STONE.defaultBlockState(), 3); // no foraging holes yet: only the middle one
            }
            // A young mound still being built: its hollow shaft over the hole is not roofed over yet (open to the sky).
            colony.part(SavannaParts.MOUND).orElseThrow().buildInstantly(level, 3);
            for (java.util.Map.Entry<BlockPos, com.antcolonies.savanna.colony.MoundPlan.Part> part : colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).entrySet()) {
                if (part.getValue() == com.antcolonies.savanna.colony.MoundPlan.Part.OPEN) {
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
                minors.add(TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
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
            if (at[1] < 0 && (!chicken[0].isAlive() || !colony.part(SavannaParts.SOLDIERS).orElseThrow().minorsOut(level))) {
                at[1] = tick;
            }
            if (at[2] < 0 && !colony.part(SavannaParts.ALARM).orElseThrow().isCalm(level)
                    && TestIsolation.insectsOf(helper, 8, TermiteWorkerEntity.class,
                            worker -> colony.getId().equals(worker.getColonyId()) && !colony.nest().isInside(level, worker.blockPosition())
                                    && colony.isAlarmed(level, worker.blockPosition())).isEmpty()) {
                at[2] = tick; // every worker where the foe is felt is in (those searching farther off carry on: the alarm is local)
            }
            if (tick % 500 == 0) {
                StringBuilder all = new StringBuilder();
                for (TermiteWorkerEntity worker : TestIsolation.insectsOf(helper, TermiteWorkerEntity.class)) {
                    all.append("\n    worker ").append(worker.getId()).append(" at ").append(worker.blockPosition().toShortString())
                            .append(" ").append(colony.roleOf(level, worker)).append(worker.isIdle() ? " idle" : "")
                            .append(" ").append(worker.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                    .map(g -> g.getGoal().getClass().getSimpleName()).toList());
                }
                SavannaMod.LOGGER.info("[middle-hole] tick {} above {} part {} covered {} height {} workers:{}", tick,
                        level.getBlockState(hole.above()), colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).get(hole.above()), colony.nest().isEntranceCovered(level),
                        level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, hole.getX(), hole.getZ()) - hole.getY(), all);
            }
            if (tick % 100 == 0) {
                StringBuilder near = new StringBuilder();
                for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(hole).inflate(1, 2, 1))) {
                    near.append("\n    ").append(ant.getClass().getSimpleName()).append(" at ").append(ant.blockPosition().toShortString())
                            .append(" ").append(ant.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                    .map(g -> g.getGoal().getClass().getSimpleName()).toList());
                }
                SavannaMod.LOGGER.info("[middle-hole] tick {}: chicken {}, alarm {}, foraging {}, hole {} ({}), wants plugging {}, anyone outside {}, minors out {}{}",
                        tick, chicken[0].isAlive() ? "alive" : "dead", colony.part(SavannaParts.ALARM).orElseThrow().describe(level), colony.isForagingTime(level),
                        ForagingHoles.isOpen(level, hole) ? "open" : "shut", level.getBlockState(hole).getBlock().getName().getString(),
                        colony.foragingHoles().wantsPlugging(level), colony.foragingHoles().isAnyoneOutside(level),
                        colony.part(SavannaParts.SOLDIERS).orElseThrow().minorsOut(level), near);
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(chicken[0] != null, "no chicken yet: no forager reached the bush while a minor was outside");
            helper.assertTrue(at[2] >= 0, "the workers outside did not all go in while the foe was felt (the chicken came at tick " + at[0] + "):"
                    + colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
            helper.assertTrue(at[2] - at[0] <= 1200, "the workers took " + (at[2] - at[0]) + " ticks to get in after the chicken came");
            helper.assertTrue(at[2] - at[0] <= 600, "the workers where the foe was felt took " + (at[2] - at[0]) + " ticks to get in, more than 30 seconds");
        });
    }

    /**
     * A break-in by day. A colony with its royal chamber and first garden dug, three builders, three minors and two majors
     * on their beats. A player breaks a side wall of the garden: a breach is felt there, the drumming runs out from it
     * (the soldiers feel it at different times), a soldier stands in the hole itself (a major if one is free: its head
     * fills it), the workers seal the hole behind it, and once everyone has felt it every soldier stands at its place:
     * the minors nearer the breach than the majors (but for the one that held the hole).
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true)
    public static void aBreakInSpreadsTheDrummingAndSendsTheSoldiersToTheBreach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        List<TermiteSoldierEntity> soldiers = new ArrayList<>();
        BlockPos[] breach = new BlockPos[1];
        Set<UUID> stoodInTheBreach = new HashSet<>();
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: everybody stays in
            TermiteScenes.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                TermiteWorkerEntity worker = SavannaEntities.TERMITE_WORKER.get().create(level);
                BlockPos cell = room.get(room.size() - 1 - i);
                worker.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(worker);
            }
            for (int i = 0; i < 5; i++) {
                soldiers.add(TermiteSoldierTests.put(level, i < 3 ? SavannaEntities.TERMITE_MINOR_SOLDIER.get() : SavannaEntities.TERMITE_MAJOR_SOLDIER.get(),
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
            colony.part(SavannaParts.ALARM).orElseThrow().blockBroken(level, breach[0], null);
            level.setBlock(breach[0], Blocks.AIR.defaultBlockState(), 3);
            SavannaMod.LOGGER.info("[alarm-test] tick {}: broke into the garden at {}; {}", helper.getTick(), breach[0].toShortString(),
                    colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || breach[0] == null) {
                return;
            }
            for (TermiteSoldierEntity soldier : soldiers) {
                if (soldier.blockPosition().equals(breach[0])) {
                    stoodInTheBreach.add(soldier.getUUID());
                }
            }
            if (helper.getTick() % 200 == 0) {
                SavannaMod.LOGGER.info("[alarm-test] tick {}: breach {} stood in it {}, places {}", helper.getTick(), level.getBlockState(breach[0]),
                        stoodInTheBreach.size(), soldiers.stream().map(s -> {
                            Alarm.Task task = colony.part(SavannaParts.ALARM).orElseThrow().taskFor(level, s);
                            return task == null ? "none" : task.cell().toShortString();
                        }).toList());
                SavannaMod.LOGGER.info("[alarm-test] tick {}: soldiers {}; {}", helper.getTick(),
                        soldiers.stream().map(s -> s.getRank() + " " + s.blockPosition().toShortString() + (s.isDrumming() ? " drumming" : "")).toList(),
                        colony.part(SavannaParts.ALARM).orElseThrow().describe(level));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && breach[0] != null, "not broken into yet");
            helper.assertTrue(colony.part(SavannaParts.ALARM).orElseThrow().worst(level) == Alarm.Kind.BREACH, "no breach is felt");
            helper.assertTrue(soldiers.stream().allMatch(soldier -> colony.part(SavannaParts.ALARM).orElseThrow().hasHeard(soldier.getUUID())), "not every soldier felt the alarm");
            Set<Long> times = new HashSet<>();
            soldiers.forEach(soldier -> times.add(colony.part(SavannaParts.ALARM).orElseThrow().heardAt(soldier.getUUID())));
            helper.assertTrue(times.size() >= 2, "every soldier felt it at the same moment (no spreading)");
            helper.assertFalse(stoodInTheBreach.isEmpty(), "no soldier stood in the breach");
            helper.assertTrue(colony.profile().nest().isPlug(level.getBlockState(breach[0])), "the breach is not sealed");
            double farthestMinor = 0;
            double nearestMajor = Double.MAX_VALUE;
            for (TermiteSoldierEntity soldier : soldiers) {
                Alarm.Task task = colony.part(SavannaParts.ALARM).orElseThrow().taskFor(level, soldier);
                helper.assertTrue(task != null && soldier.blockPosition().equals(task.cell()),
                        soldier.getRank() + " at " + soldier.blockPosition().toShortString() + " is not at its spot " + (task == null ? null : task.cell()));
                if (stoodInTheBreach.contains(soldier.getUUID())) {
                    continue; // (the hole's own holder, a major first: it took what place was left once the hole was sealed)
                }
                double distance = task.cell().distSqr(breach[0]);
                if (soldier.getRank() == Rank.MINOR) {
                    farthestMinor = Math.max(farthestMinor, distance);
                } else {
                    nearestMajor = Math.min(nearestMajor, distance);
                }
            }
            // (places are spots within cells now, several to a cell: a neighbouring cell's near spot may go before a far one)
            helper.assertTrue(farthestMinor <= nearestMajor + 2, "a major stood nearer the breach than a minor");
        });
    }

    /**
     * The rework, step 3 (was "repeated break-ins make it level 4"): three break-ins into the royal chamber are one
     * breach felt there, and nothing more: the colony still forages elsewhere (it is "night"), wants no more soldiers,
     * the trouble is felt at the chamber and not 40 blocks away, footsteps there do not lower it, and nothing of it is
     * kept after a reload. What counts as a break-in is as before (the nest's and the mound's walls, by a player too).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void breakInsAreBreachesFeltOnlyNearThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 0.0); // midnight: foraging time
            Alarm alarm = colony.part(SavannaParts.ALARM).orElseThrow();
            colony.setCount(Caste.WORKER, 20);
            helper.assertTrue(colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) == 2 && colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR) == 1,
                    "20 workers should want 2 minors and 1 major");
            TermiteScenes.digNestAtOnce(level, colony);
            BlockPos chamber = colony.nest().getHomeChamber(level).center();
            // Three blocks of the royal chamber's walls: under it, over it, and a side wall.
            BlockPos side = Direction.Plane.HORIZONTAL.stream().map(direction -> chamber.relative(direction, 2))
                    .filter(wall -> alarm.isBreakIn(level, wall)).findFirst().orElseThrow();
            BlockPos[] walls = {chamber.below(), chamber.above(), side};
            for (int i = 0; i < 3; i++) {
                helper.assertTrue(alarm.isBreakIn(level, walls[i]), "breaking " + walls[i].toShortString() + " is no break-in");
                if (i == 0) {
                    // The first one broken by a player, as in the game (the break event, ModEvents.onBreak), not by
                    // calling the alarm: the user's report of 2026-10-22, a broken mound and no drumming.
                    net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
                    player.moveTo(walls[i].getX() + 0.5, walls[i].getY() + 1, walls[i].getZ() + 0.5);
                    helper.assertTrue(player.gameMode.destroyBlock(walls[i]), "the player could not break " + walls[i].toShortString());
                } else {
                    alarm.blockBroken(level, walls[i], null);
                }
                helper.assertTrue(alarm.worst(level) == Alarm.Kind.BREACH && alarm.felt(level).size() == 1,
                        "after " + (i + 1) + " break-ins beside one another there should be one breach:" + alarm.describe(level));
            }
            helper.assertTrue(colony.isForagingTime(level), "break-ins at the royal chamber ended the foraging time for the whole colony");
            helper.assertTrue(colony.isAlarmed(level, chamber), "the breach is not felt at the royal chamber");
            helper.assertFalse(colony.isAlarmed(level, chamber.offset(40, 0, 0)), "the breach is felt 40 blocks away");
            helper.assertTrue(colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) == 2 && colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR) == 1,
                    "break-ins should not make the colony want more soldiers, it wants " + colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MINOR) + " and "
                            + colony.part(SavannaParts.SOLDIERS).orElseThrow().wanted(level, Rank.MAJOR));
            alarm.trouble(level, Alarm.Kind.FELT, chamber);
            helper.assertTrue(alarm.worst(level) == Alarm.Kind.BREACH && alarm.felt(level).size() == 1, "footsteps lowered the alarm:" + alarm.describe(level));
            Colony loaded = Colony.load(colony.save());
            helper.assertTrue(loaded.part(SavannaParts.ALARM).orElseThrow().isCalm(level), "a trouble was kept after a reload");
            // A block far from the nest and the mound is no break-in.
            helper.assertFalse(alarm.isBreakIn(level, chamber.offset(8, 0, 8)), "a block away from the nest counted as a break-in");
            alarm.calmForTests();
            helper.assertTrue(alarm.isCalm(level), "the alarm is not calm");
            // A player breaking a wall of the mound (the user's report of 2026-10-22): a break-in too.
            colony.part(SavannaParts.MOUND).orElseThrow().buildInstantly(level, 3);
            BlockPos moundWall = com.antcolonies.savanna.colony.Mound.partsOf(colony, level).entrySet().stream()
                    .filter(part -> part.getValue() == com.antcolonies.savanna.colony.MoundPlan.Part.WALL && !level.getBlockState(part.getKey()).isAir())
                    .map(java.util.Map.Entry::getKey).max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
            net.minecraft.server.level.ServerPlayer builder = helper.makeMockServerPlayerInLevel();
            builder.moveTo(moundWall.getX() + 0.5, moundWall.getY() + 1, moundWall.getZ() + 0.5);
            helper.assertTrue(builder.gameMode.destroyBlock(moundWall), "the player could not break the mound at " + moundWall.toShortString());
            helper.assertTrue(alarm.worst(level) == Alarm.Kind.BREACH,
                    "breaking the mound at " + moundWall.toShortString() + " left the alarm at" + alarm.describe(level));
            alarm.calmForTests();
            helper.succeed();
        });
    }

    /**
     * Footsteps on the mound by day (something felt): the minors within earshot (6 blocks, shrinking) come to the nest
     * cells nearest the spot and drum once there; nobody is kept in; it is over after 30 seconds. (The rework, step 3:
     * it was "exactly the two nearest".)
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void footstepsBringTheMinorsWithinEarshotToLookAndDrum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        List<TermiteSoldierEntity> minors = new ArrayList<>();
        Set<UUID> drummedAtTheSpot = new HashSet<>();
        long[] raised = {-1};
        TermiteScenes.colony(helper, List.of(), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: everybody stays in
            TermiteScenes.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 3; i++) {
                minors.add(TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i)));
            }
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            colony.part(SavannaParts.ALARM).orElseThrow().trouble(level, Alarm.Kind.FELT, minors.get(0).blockPosition().above(3));
            helper.assertFalse(colony.isAlarmed(level, minors.get(0).blockPosition()), "footsteps kept the members in");
            raised[0] = helper.getTick();
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || raised[0] < 0 || colony.part(SavannaParts.ALARM).orElseThrow().isCalm(level)) {
                return;
            }
            for (TermiteSoldierEntity minor : minors) {
                Alarm.Task task = colony.part(SavannaParts.ALARM).orElseThrow().taskFor(level, minor);
                if (task != null && minor.blockPosition().equals(task.cell()) && minor.isDrumming()) {
                    drummedAtTheSpot.add(minor.getUUID());
                }
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && raised[0] >= 0, "no footsteps yet");
            helper.assertFalse(drummedAtTheSpot.isEmpty(), "no minor came to look and drummed");
            helper.assertTrue(colony.part(SavannaParts.ALARM).orElseThrow().isCalm(level), "the alarm has not calmed down");
            helper.assertTrue(helper.getTick() - raised[0] <= 700, "footsteps were felt for " + (helper.getTick() - raised[0]) + " ticks, more than 30 seconds");
        });
    }
}
