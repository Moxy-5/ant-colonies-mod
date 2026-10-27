package com.antcolonies.savanna.gametest;

import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.Alarm;
import com.antcolonies.savanna.colony.RaidDefence;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The termites' side of the Matabele fights (M8 step 2f, {@code docs/DESIGN.md}, "Steps 2e and 2f"; {@link RaidDefence}).
 *
 * <p>{@link #aRaidBreakingIntoAGalleryIsSealedBehindTheSoldiers}: a Matabele breaks into a foraging tunnel; the soldiers
 * near drum; the worker between the break and the nest goes deeper and the gallery is packed 4 blocks back from the
 * break, behind it; the soldiers by the break are shut out; once the raiders have gone they wait by the seal; at night
 * the seal is bitten open and they are let back in.
 *
 * <p>{@link #theDeadAreCarriedOutAndTheParalysedCarriedIn}: at night a raider's body in a tunnel and an old body of the
 * colony's own are dragged out to a hole and left there; a body left out fades; a nestmate lying paralysed is not
 * fetched while a Matabele is within 8 blocks of it, and is carried in and eaten once it has gone.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteRaidDefenceTests {
    /** The cells packed back from the break (macrotermes.json, {@code alarm.raid.plug_back}). */
    private static final int PLUG_BACK = 4;

    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true, batch = "termite_raid_seal")
    public static void aRaidBreakingIntoAGalleryIsSealedBehindTheSoldiers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 12.0);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<String>[] failAt = new java.util.function.Supplier[1];
        Runnable[] each = new Runnable[1]; // the checks, once the scene is ready (callbacks are only added at the start)
        helper.onEachTick(() -> {
            if (each[0] != null) {
                each[0].run();
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = scene.colony[0];
            List<BlockPos> cells = scene.tunnel[0].tunnel();
            int at = cells.size() - 2;
            helper.assertTrue(at - PLUG_BACK - 1 >= 1, "the foraging tunnel is too short for the test: " + cells.size() + " cells");
            BlockPos breakAt = cells.get(at);
            BlockPos plug = cells.get(at - PLUG_BACK);
            TermiteSoldierEntity byTheBreak = TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), breakAt);
            TermiteSoldierEntity beside = TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), cells.get(at - 1));
            BlockPos home = colony.nest().getHomeChamber(level).room().get(0);
            TermiteSoldierEntity deepIn = TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), home);
            for (TermiteSoldierEntity soldier : new TermiteSoldierEntity[] {byTheBreak, beside, deepIn}) {
                soldier.setClampForTests(0.0);
            }
            TermiteWorkerEntity worker = put(level, SavannaEntities.TERMITE_WORKER.get(), cells.get(at - 2));
            MatabeleEntity raider = raider(level, breakAt.above(2));
            {
                RaidDefence raid = colony.part(SavannaParts.RAID_DEFENCE).orElseThrow();
                long tellAt = helper.getTick() + 10; // once the insects put down have joined the colony
                boolean[] told = {false};
                boolean[] drummed = {false};
                long[] sealedAt = {-1};
                long[] calmedAt = {-1};
                long[] waitedAt = {-1};
                long[] nightAt = {-1};
                each[0] = () -> {
                    if (!told[0]) {
                        if (helper.getTick() < tellAt) {
                            return;
                        }
                        told[0] = true;
                        colony.news().tell(level, ColonyNews.BROKEN_OPEN, new ColonyNews.BrokenOpen(breakAt, raider));
                        helper.assertTrue(plug.equals(raid.plannedSealNear(breakAt)), "the seal should be planned " + PLUG_BACK
                                + " cells back at " + plug.toShortString() + ", not " + raid.plannedSealNear(breakAt));
                    }
                    if (byTheBreak.isDrumming() || beside.isDrumming()) {
                        drummed[0] = true;
                    }
                    if (raider.isAlive()) {
                        raider.setHealth(raider.getMaxHealth()); // the raider stays till the seal is packed
                    }
                    long tick = helper.getTick();
                    if (sealedAt[0] < 0 && level.getBlockState(plug).is(colony.profile().nest().plugState().getBlock())) {
                        sealedAt[0] = tick;
                        helper.assertTrue(drummed[0], "the soldiers by the break never drummed");
                        helper.assertTrue(raid.isShutOut(byTheBreak.getUUID()) && raid.isShutOut(beside.getUUID()),
                                "the soldiers by the break should be shut out by the seal");
                        helper.assertTrue(!raid.isShutOut(deepIn.getUUID()), "the soldier deep in the nest was shut out too");
                        helper.assertTrue(worker.position().distanceTo(Vec3.atCenterOf(breakAt)) > Math.sqrt(plug.distSqr(breakAt)),
                                "the worker should have gone deeper than the seal, but is at " + worker.blockPosition().toShortString());
                        raider.discard(); // the raiders go
                        colony.part(SavannaParts.ALARM).ifPresent(Alarm::calmForTests);
                        calmedAt[0] = tick;
                    }
                    if (calmedAt[0] >= 0 && waitedAt[0] < 0 && tick - calmedAt[0] > 20) {
                        BlockPos waitAt = cells.get(at - PLUG_BACK + 1);
                        for (TermiteSoldierEntity soldier : new TermiteSoldierEntity[] {byTheBreak, beside}) {
                            if (soldier.isAlive() && soldier.position().distanceTo(Vec3.atBottomCenterOf(waitAt)) <= 1.5) {
                                waitedAt[0] = tick;
                                TestClock.still(helper, 0.0); // the night: the seal is to be opened
                                nightAt[0] = tick;
                            }
                        }
                    }
                    if (nightAt[0] >= 0) {
                        if (level.getBlockState(plug).isAir() && !raid.isShutOut(byTheBreak.getUUID()) && !raid.isShutOut(beside.getUUID())) {
                            SavannaMod.LOGGER.info("[raid-test] sealed at {}, waiting by it at {}, opened at {}", sealedAt[0], waitedAt[0], tick);
                            helper.succeed();
                        }
                    }
                };
                failAt[0] = () -> "sealed at " + sealedAt[0] + ", waited by it at " + waitedAt[0] + ", night from "
                        + nightAt[0] + "; plug " + level.getBlockState(plug) + ", seals " + raid.seals() + ", shut out "
                        + raid.isShutOut(byTheBreak.getUUID()) + "/" + raid.isShutOut(beside.getUUID()) + ", soldiers at "
                        + byTheBreak.blockPosition().toShortString() + " and " + beside.blockPosition().toShortString() + ", break "
                        + breakAt.toShortString() + ", worker " + (worker.isAlive() ? worker.blockPosition().toShortString() : "dead") + ", in the plug cell "
                        + level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(plug)).stream()
                                .map(e -> e.getType().toShortString()).toList() + ", planned " + raid.plannedSealNear(breakAt) + raid.describe(level);
            }
        });
        helper.runAtTickTime(3990, () -> helper.fail(failAt[0] == null ? "the scene never got ready" : failAt[0].get()));
    }

    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "termite_raid_dead")
    public static void theDeadAreCarriedOutAndTheParalysedCarriedIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 0.0);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<String>[] failAt = new java.util.function.Supplier[1];
        Runnable[] each = new Runnable[1]; // the checks, once the scene is ready (callbacks are only added at the start)
        helper.onEachTick(() -> {
            if (each[0] != null) {
                each[0].run();
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = scene.colony[0];
            for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
                level.setBlock(tunnel.hole(), Blocks.AIR.defaultBlockState(), 3); // the holes open for the night
            }
            List<BlockPos> cells = scene.tunnel[0].tunnel();
            BlockPos home = colony.nest().getHomeChamber(level).room().get(0);
            AntCorpseEntity old = corpse(level, SavannaEntities.TERMITE_CORPSE.get(), home, colony);
            old.ageForTests(colony.profile().refuse().eatFreshTicks().orElse(0) + 200); // too old to eat
            AntCorpseEntity stranger = corpse(level, SavannaEntities.MATABELE_CORPSE.get(), cells.get(cells.size() / 2), null);
            AntCorpseEntity fading = corpse(level, SavannaEntities.TERMITE_CORPSE.get(), scene.tunnel[0].hole().above(2), colony);
            fading.setOut(level.getGameTime(), 40);
            long[] outAt = {-1};
            TermiteWorkerEntity[] lying = {null};
            MatabeleEntity[] hunter = {null};
            long[] goneAt = {-1};
            each[0] = () -> {
                long tick = helper.getTick();
                if (tick == 80) {
                    helper.assertTrue(!fading.isAlive(), "a body left out by a hole should fade after its time");
                }
                if (outAt[0] < 0 && old.isOut() && stranger.isOut()) {
                    outAt[0] = tick;
                    List<BlockPos> mouths = colony.refuse().wayOutMouths(level);
                    for (AntCorpseEntity body : new AntCorpseEntity[] {old, stranger}) {
                        helper.assertTrue(mouths.stream().anyMatch(mouth -> body.position().distanceTo(Vec3.atBottomCenterOf(mouth)) <= 1.5),
                                "a body was left at " + body.blockPosition().toShortString() + ", not by a hole " + mouths);
                    }
                    // Now a nestmate lying paralysed in the tunnel, a Matabele beside it.
                    BlockPos at = cells.get(cells.size() / 2);
                    lying[0] = put(level, SavannaEntities.TERMITE_WORKER.get(), at);
                    lying[0].paralyseForTests();
                    hunter[0] = raider(level, at.above(3));
                }
                if (outAt[0] >= 0 && hunter[0] != null) {
                    if (hunter[0].isAlive()) {
                        hunter[0].setHealth(hunter[0].getMaxHealth());
                        helper.assertTrue(lying[0].isAlive(), "the paralysed nestmate was fetched with a Matabele beside it");
                        if (tick - outAt[0] >= 200) {
                            hunter[0].discard(); // the hunter goes: now it may be fetched
                            colony.part(SavannaParts.ALARM).ifPresent(Alarm::calmForTests);
                        }
                    } else if (!lying[0].isAlive() && goneAt[0] < 0) {
                        goneAt[0] = tick;
                        helper.assertTrue(level.getEntitiesOfClass(AntCorpseEntity.class, lying[0].getBoundingBox().inflate(2.0),
                                body -> body.isAlive() && body != old && body != stranger).isEmpty(), "the paralysed nestmate died where it lay");
                        SavannaMod.LOGGER.info("[raid-dead-test] bodies out at {}, paralysed nestmate carried in at {}", outAt[0], tick);
                        helper.succeed();
                    }
                }
            };
            failAt[0] = () -> "old body out " + old.isOut() + " at " + old.blockPosition().toShortString()
                    + ", raider's body out " + stranger.isOut() + " at " + stranger.blockPosition().toShortString() + ", ways out "
                    + colony.refuse().wayOutMouths(level) + ", paralysed " + (lying[0] == null ? "not yet" : lying[0].isAlive() + " at "
                    + lying[0].blockPosition().toShortString()) + "; workers " + level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds()).stream()
                            .map(w -> w.blockPosition().toShortString() + " " + w.runningGoals()).toList();
        });
        helper.runAtTickTime(5990, () -> helper.fail(failAt[0] == null ? "the scene never got ready" : failAt[0].get()));
    }

    /** The colony and the tunnel the scene was made ready with (set 10 ticks in). */
    private record Scene(Colony[] colony, NestPlan.ForagingTunnel[] tunnel) {}

    /**
     * A queen and six old workers (builders, who carry the dead); at tick 10 the clock is still at {@code hour}, the
     * nest is dug out at once and the entrance closed with stone (the scene of {@code MatabeleFightTests}).
     */
    private static Scene scene(GameTestHelper helper, double hour) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), TestScenes.MIDDLE, TestScenes.SURFACE, TestScenes.MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), TestScenes.MIDDLE - 3 + i, TestScenes.SURFACE, TestScenes.MIDDLE - 2);
            }
        });
        Scene scene = new Scene(new Colony[1], new NestPlan.ForagingTunnel[1]);
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            TestClock.still(helper, hour);
            TermiteSoldierTests.digNestAtOnce(level, colony);
            level.setBlock(colony.nest().getEntrance(), Blocks.STONE.defaultBlockState(), 3);
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(builderAge(level, colony));
            }
            scene.colony()[0] = colony;
            scene.tunnel()[0] = colony.foragingHoles().tunnels(level).get(0);
        });
        return scene;
    }

    /** A grown-up-at time that makes a worker a builder now (past nursing, not yet a forager). */
    private static long builderAge(ServerLevel level, Colony colony) {
        int nurse = colony.profile().worker().nurseUntilTicks();
        int builder = colony.profile().worker().builderUntilTicks();
        return level.getGameTime() - (nurse + builder) / 2;
    }

    /** A worker put at the middle of this cell, a builder of the nearest colony. */
    private static TermiteWorkerEntity put(ServerLevel level, EntityType<TermiteWorkerEntity> type, BlockPos cell) {
        TermiteWorkerEntity worker = type.create(level);
        worker.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(worker);
        Colony colony = worker.getColony();
        if (colony != null) {
            worker.setGrownUpAt(builderAge(level, colony));
        }
        return worker;
    }

    /** A Matabele major of no colony and with no mind (it only stands there), at this cell. */
    private static MatabeleEntity raider(ServerLevel level, BlockPos cell) {
        MatabeleEntity ant = SavannaEntities.MATABELE_MAJOR.get().create(level);
        ant.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
        ant.keepOutOfColonies();
        ant.setNoAi(true);
        level.addFreshEntity(ant);
        return ant;
    }

    /** A body of this kind at this cell, of this colony (null: a stranger's). */
    private static AntCorpseEntity corpse(ServerLevel level, EntityType<AntCorpseEntity> type, BlockPos cell, Colony colony) {
        AntCorpseEntity body = type.create(level);
        body.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
        body.setColonyId(colony == null ? null : colony.getId());
        level.addFreshEntity(body);
        return body;
    }
}
