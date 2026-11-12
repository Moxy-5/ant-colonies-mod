package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestIsolation;
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
 * <p>{@link #soldiersPourOutToARaidsBreakInWavesTakeTheirOwnPlacesAndBite} (M8 step 3S): a colony with 40 soldiers
 * kept as numbers (30 minor, 10 major) and none real; a raider breaks into a gallery; soldiers come out of the gallery
 * near the break in waves until a tenth of the colony's soldiers (4) have come, then no more; they take places of their
 * own round the break (not one cell each in a line) and bite the raider standing at it; the seal waits for them.
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
                    + lying[0].blockPosition().toShortString()) + "; workers " + TestIsolation.insectsOf(helper, TermiteWorkerEntity.class).stream()
                            .map(w -> w.blockPosition().toShortString() + " " + w.runningGoals()).toList();
        });
        helper.runAtTickTime(5990, () -> helper.fail(failAt[0] == null ? "the scene never got ready" : failAt[0].get()));
    }

    /** The colony and the tunnel the scene was made ready with (set 10 ticks in). */
    private record Scene(Colony[] colony, NestPlan.ForagingTunnel[] tunnel) {}

    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "termite_raid_waves")
    public static void soldiersPourOutToARaidsBreakInWavesTakeTheirOwnPlacesAndBite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 12.0);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<String>[] failAt = new java.util.function.Supplier[1];
        Runnable[] each = new Runnable[1];
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
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), level.getGameTime(), 30);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), level.getGameTime(), 10);
            MatabeleEntity raider = raider(level, breakAt);
            Alarm alarm = colony.part(SavannaParts.ALARM).orElseThrow();
            RaidDefence raid = colony.part(SavannaParts.RAID_DEFENCE).orElseThrow();
            long tellAt = helper.getTick() + 10;
            boolean[] told = {false};
            boolean[] bitten = {false};
            int[] mostPlaces = {0};
            int[] wavesAtSeal = {-1};
            long[] sealedAt = {-1};
            each[0] = () -> {
                long tick = helper.getTick();
                if (!told[0]) {
                    if (tick < tellAt) {
                        return;
                    }
                    told[0] = true;
                    colony.news().tell(level, ColonyNews.BROKEN_OPEN, new ColonyNews.BrokenOpen(breakAt, raider));
                    helper.assertTrue(alarm.waveTarget() == 4, "a tenth of 40 soldiers (4) should come, not " + alarm.waveTarget());
                }
                if (raider.isAlive()) {
                    if (raider.getHealth() < raider.getMaxHealth()) {
                        bitten[0] = true;
                    }
                    raider.setHealth(raider.getMaxHealth()); // (it stays at the break)
                }
                int waves = alarm.cameInWaves(TermiteSoldierEntity.Rank.MINOR) + alarm.cameInWaves(TermiteSoldierEntity.Rank.MAJOR);
                helper.assertTrue(waves <= 4, waves + " soldiers came in waves, more than the colony's tenth (4)");
                int places = 0;
                for (TermiteSoldierEntity soldier : TestIsolation.insectsOf(helper, TermiteSoldierEntity.class)) {
                    if (alarm.holdsPlace(soldier.getUUID())) {
                        places++;
                    }
                }
                mostPlaces[0] = Math.max(mostPlaces[0], places);
                if (sealedAt[0] < 0 && level.getBlockState(plug).is(colony.profile().nest().plugState().getBlock())) {
                    sealedAt[0] = tick;
                    wavesAtSeal[0] = waves;
                }
                if (sealedAt[0] >= 0 && bitten[0] && waves >= 2 && mostPlaces[0] >= 2) {
                    helper.assertTrue(wavesAtSeal[0] >= 2 || sealedAt[0] - tellAt >= 600,
                            "the seal was packed at " + (sealedAt[0] - tellAt) + " ticks with only " + wavesAtSeal[0] + " soldiers come in waves");
                    SavannaMod.LOGGER.info("[raid-waves-test] {} minor and {} major came in waves, {} held places, sealed after {} ticks with {} come",
                            alarm.cameInWaves(TermiteSoldierEntity.Rank.MINOR), alarm.cameInWaves(TermiteSoldierEntity.Rank.MAJOR), mostPlaces[0],
                            sealedAt[0] - tellAt, wavesAtSeal[0]);
                    helper.succeed();
                }
            };
            failAt[0] = () -> "bitten " + bitten[0] + ", came in waves " + alarm.cameInWaves(TermiteSoldierEntity.Rank.MINOR) + " minor "
                    + alarm.cameInWaves(TermiteSoldierEntity.Rank.MAJOR) + " major, most holding places " + mostPlaces[0] + ", sealed at "
                    + sealedAt[0] + " with " + wavesAtSeal[0] + "; wave cells " + raid.waveCells(level, breakAt).size() + ", break "
                    + breakAt.toShortString() + ", plug " + plug.toShortString() + alarm.describe(level) + raid.describe(level);
        });
        helper.runAtTickTime(2990, () -> helper.fail(failAt[0] == null ? "the scene never got ready" : failAt[0].get()));
    }

    /**
     * The raid queue fix, part 2 (the user's raid of 2026-11-06: the raiders stood by the broken mud while termite workers
     * packed soil into the holes just inside, and nobody attacked them). A foraging tunnel is opened to the sky at one
     * cell; a worker stands one cell in from the opening (as a worker packing the hole does) and another four cells in.
     * The one at the mouth counts as at an opening, not sheltered; the one deeper in stays sheltered. A Matabele minor sent
     * to fight there stings the worker at the mouth still and leaves the one deeper in alone (real Megaponera: the minors
     * reach into the openings for the termites there; the nest itself they do not enter, Longhurst and Howse 1979).
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "termite_raid_mouth")
    public static void aWorkerAtABreaksMouthIsStungAndOneDeeperInIsLeft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 12.0);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<String>[] failAt = new java.util.function.Supplier[1];
        Runnable[] each = new Runnable[1];
        helper.onEachTick(() -> {
            if (each[0] != null) {
                each[0].run();
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = scene.colony[0];
            List<BlockPos> cells = scene.tunnel[0].tunnel();
            int at = cells.size() - 2;
            helper.assertTrue(at - 4 >= 1, "the foraging tunnel is too short for the test: " + cells.size() + " cells");
            BlockPos breakAt = cells.get(at);
            BlockPos mouthCell = cells.get(at - 1);
            BlockPos deepCell = cells.get(at - 4);
            // The break: the ground over the cell dug away to the sky.
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, breakAt.getX(), breakAt.getZ());
            for (int y = breakAt.getY() + 1; y < top; y++) {
                level.setBlock(new BlockPos(breakAt.getX(), y, breakAt.getZ()), Blocks.AIR.defaultBlockState(), 3);
            }
            TermiteWorkerEntity mouth = put(level, SavannaEntities.TERMITE_WORKER.get(), mouthCell);
            TermiteWorkerEntity deep = put(level, SavannaEntities.TERMITE_WORKER.get(), deepCell);
            long readyAt = helper.getTick() + 10; // once they have joined the colony
            boolean[] ready = {false};
            MatabeleEntity[] minor = {null};
            each[0] = () -> {
                if (!ready[0]) {
                    if (helper.getTick() < readyAt) {
                        return;
                    }
                    ready[0] = true;
                    for (TermiteWorkerEntity worker : new TermiteWorkerEntity[] {mouth, deep}) {
                        helper.assertTrue(worker.getColony() == colony, "a worker put in the tunnel did not join the colony");
                        worker.setNoAi(true); // (they stand there, packing soil)
                        worker.moveTo(Vec3.atBottomCenterOf(worker == mouth ? mouthCell : deepCell));
                        worker.setDodgeForTests(0.0, 0.0);
                    }
                    helper.assertTrue(mouth.isIndoors() && deep.isIndoors(), "the tunnel cells should be the nest's: mouth "
                            + mouth.isIndoors() + ", deeper in " + deep.isIndoors());
                    helper.assertTrue(mouth.stepsToAnOpening() == 1 && !mouth.isSheltered(), "the worker at the mouth should be 1 step from the opening and not sheltered, not "
                            + mouth.stepsToAnOpening() + " steps, sheltered " + mouth.isSheltered());
                    helper.assertTrue(deep.stepsToAnOpening() < 0 && deep.isSheltered(), "the worker deeper in should be sheltered, not "
                            + deep.stepsToAnOpening() + " steps from an opening");
                    BlockPos rel = helper.relativePos(breakAt);
                    minor[0] = com.antcolonies.gametest.TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MINOR.get(), rel.getX() + 3, TestScenes.SURFACE, rel.getZ());
                    minor[0].orderToFight(breakAt.atY(top), level.getGameTime() + 2400);
                    return;
                }
                // (the raiders keep the break open: the colony's builders mend it, as real termites do, and the plug is bitten away)
                for (int y = breakAt.getY(); y < top; y++) {
                    BlockPos open = new BlockPos(breakAt.getX(), y, breakAt.getZ());
                    if (!level.getBlockState(open).isAir()) {
                        level.setBlock(open, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                helper.assertTrue(!deep.isParalysed(), "the worker deeper in (sheltered) was stung");
                if (minor[0].isAlive()) {
                    minor[0].setHealth(minor[0].getMaxHealth());
                }
                if (mouth.isParalysed()) {
                    SavannaMod.LOGGER.info("[raid-mouth-test] the worker at the mouth stung at tick {}", helper.getTick());
                    helper.succeed();
                }
            };
            failAt[0] = () -> "the worker at the mouth was never stung: it is at " + (mouth.isAlive() ? mouth.blockPosition().toShortString() : "dead")
                    + " (" + mouth.stepsToAnOpening() + " steps from an opening), the minor at "
                    + (minor[0] == null ? "none" : minor[0].blockPosition().toShortString()) + ", break " + breakAt.toShortString();
        });
        helper.runAtTickTime(2990, () -> helper.fail(failAt[0] == null ? "the scene never got ready" : failAt[0].get()));
    }

    /**
     * The termite alarm rework, step 1 ({@code docs/plans/termite-alarm-rework.md}): a measuring scene, compared before
     * and after every step of the rework. A full colony by day (9:00, a raid hour): 180 workers, 15 minor and 5 major
     * soldiers, most of them numbers, shown as they are to a player standing at the nest (the real-mob swap on, a pretend
     * watcher). Six workers stand in the last cells of a foraging tunnel, a pit over it covered with a plate of termite
     * mud (a feeding gallery under its cover); twenty Matabele (ten majors, ten minors) come from 6 blocks off, sent to
     * fight there. Three minutes on, one {@code [alarm-measure]} line: when the cover was broken, how many soldiers
     * answered and how soon the first stood at the break, the first bite on a raider, when the gallery was sealed, the
     * workers stung and escaped, the raiders hurt and dead, the soldiers lost. It only checks that the raid happened
     * (the cover broken or a termite stung); the rework's later steps add its checks.
     */
    @GameTest(template = "arena", timeoutTicks = 4400, skyAccess = true, batch = "termite_alarm_measure")
    public static void aDaytimeRaidOnAFullColonyIsMeasured(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 9.0);
        Runnable[] each = new Runnable[1];
        helper.onEachTick(() -> {
            if (each[0] != null) {
                each[0].run();
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = scene.colony[0];
            Alarm alarm = colony.part(SavannaParts.ALARM).orElseThrow();
            RaidDefence raid = colony.part(SavannaParts.RAID_DEFENCE).orElseThrow();
            List<BlockPos> cells = scene.tunnel[0].tunnel();
            int at = cells.size() - 2;
            helper.assertTrue(at - PLUG_BACK - 1 >= 1, "the foraging tunnel is too short for the test: " + cells.size() + " cells");
            BlockPos breakAt = cells.get(at);
            long now = level.getGameTime();
            long old = now - colony.profile().worker().builderUntilTicks() - 1000;
            colony.population().addNewMembers(com.antcolonies.colony.Caste.WORKER, old, 180 - colony.getCount(com.antcolonies.colony.Caste.WORKER));
            String added = colony.part(SavannaParts.SOLDIERS).orElseThrow().addFor(180, now);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(colony.getCenter().above(2))));
            // The feeding gallery's cover: a pit over the cell down to a plate of termite mud.
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, breakAt.getX(), breakAt.getZ());
            BlockPos cover = breakAt.above();
            for (int y = cover.getY() + 1; y < top; y++) {
                level.setBlock(new BlockPos(breakAt.getX(), y, breakAt.getZ()), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(cover, com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get().defaultBlockState(), 3);
            java.util.Set<java.util.UUID> feeding = new java.util.HashSet<>();
            for (int i = 0; i < 6; i++) {
                feeding.add(put(level, SavannaEntities.TERMITE_WORKER.get(), cells.get(at - i % 3)).getUUID());
            }
            int dx = Integer.signum(breakAt.getX() - cells.get(0).getX());
            int dz = Integer.signum(breakAt.getZ() - cells.get(0).getZ());
            if (Math.abs(breakAt.getX() - cells.get(0).getX()) >= Math.abs(breakAt.getZ() - cells.get(0).getZ())) {
                dz = 0;
            } else {
                dx = 0;
            }
            BlockPos from = new BlockPos(breakAt.getX() + dx * 6, top, breakAt.getZ() + dz * 6);
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(colony.getCenter()).inflate(40.0);
            List<MatabeleEntity> ants = new java.util.ArrayList<>();
            java.util.Set<java.util.UUID> soldiersSeen = new java.util.HashSet<>();
            java.util.Set<java.util.UUID> soldiersLost = new java.util.HashSet<>();
            java.util.Set<java.util.UUID> stung = new java.util.HashSet<>();
            long comeAt = helper.getTick() + 100; // (the real mobs have come out of the numbers by then)
            java.util.Map<java.util.UUID, Float> whole = new java.util.HashMap<>();
            long[] cameAt = {-1};
            long[] brokeAt = {-1};
            long[] soldierAt = {-1};
            long[] biteAt = {-1};
            long[] sealedAt = {-1};
            int[] mostAtBreak = {0};
            int[] mostAlarmed = {0};
            int[] waveTarget = {-1};
            String[] atStart = {""};
            each[0] = () -> {
                long tick = helper.getTick();
                List<TermiteSoldierEntity> soldiers = level.getEntitiesOfClass(TermiteSoldierEntity.class, area,
                        soldier -> colony.getId().equals(soldier.getColonyId()));
                List<TermiteWorkerEntity> workers = level.getEntitiesOfClass(TermiteWorkerEntity.class, area,
                        worker -> colony.getId().equals(worker.getColonyId()));
                if (cameAt[0] < 0) {
                    if (tick < comeAt) {
                        return;
                    }
                    cameAt[0] = tick;
                    double nearest = soldiers.stream().mapToDouble(soldier -> Math.sqrt(soldier.blockPosition().distSqr(breakAt))).min().orElse(-1);
                    atStart[0] = soldiers.stream().filter(soldier -> soldier.getRank() == TermiteSoldierEntity.Rank.MINOR).count() + " minor and "
                            + soldiers.stream().filter(soldier -> soldier.getRank() == TermiteSoldierEntity.Rank.MAJOR).count()
                            + " major soldiers real, the nearest " + String.format(java.util.Locale.ROOT, "%.1f", nearest) + " blocks from the break; "
                            + workers.size() + " workers real, " + workers.stream().filter(worker -> feeding.contains(worker.getUUID())
                                    && worker.blockPosition().closerThan(breakAt, 4.0)).count() + " of the 6 feeding still by the break";
                    for (int i = 0; i < 20; i++) {
                        MatabeleEntity ant = (i % 2 == 0 ? SavannaEntities.MATABELE_MAJOR.get() : SavannaEntities.MATABELE_MINOR.get()).create(level);
                        ant.moveTo(from.getX() + 0.2 + (i % 4) * 0.25, from.getY(), from.getZ() + 0.2 + (i / 4) * 0.2, 0.0F, 0.0F);
                        ant.keepOutOfColonies();
                        ant.orderToFight(breakAt.atY(top), level.getGameTime() + 6000);
                        level.addFreshEntity(ant);
                        ants.add(ant);
                    }
                    return;
                }
                long since = tick - cameAt[0];
                if (since <= 20) {
                    // (a new ant's health settles with its own size in its first ticks: "hurt" is health lost after that)
                    ants.forEach(ant -> whole.put(ant.getUUID(), ant.getHealth()));
                    return;
                }
                if (brokeAt[0] < 0 && !level.getBlockState(cover).is(com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get())) {
                    brokeAt[0] = since;
                }
                waveTarget[0] = Math.max(waveTarget[0], alarm.waveTarget());
                mostAlarmed[0] = Math.max(mostAlarmed[0], alarm.alarmedSoldiers().size());
                int atBreak = 0;
                for (TermiteSoldierEntity soldier : soldiers) {
                    soldiersSeen.add(soldier.getUUID());
                    if (soldier.isDowned() || !soldier.isAlive()) {
                        soldiersLost.add(soldier.getUUID());
                    } else if (soldier.blockPosition().closerThan(breakAt, 4.0)) {
                        atBreak++;
                    }
                }
                mostAtBreak[0] = Math.max(mostAtBreak[0], atBreak);
                if (soldierAt[0] < 0 && atBreak > 0 && brokeAt[0] >= 0) {
                    soldierAt[0] = since;
                }
                if (biteAt[0] < 0 && ants.stream().anyMatch(ant -> !ant.isAlive() || ant.getHealth() < whole.get(ant.getUUID()) - 0.01F)) {
                    biteAt[0] = since;
                }
                if (sealedAt[0] < 0 && !raid.seals().isEmpty()) {
                    sealedAt[0] = since;
                }
                for (TermiteWorkerEntity worker : workers) {
                    if (worker.isDowned()) {
                        stung.add(worker.getUUID());
                    }
                }
                if (since < 3600) {
                    return;
                }
                long feedingStung = feeding.stream().filter(stung::contains).count();
                long dead = ants.stream().filter(ant -> !ant.isAlive()).count();
                long hurt = ants.stream().filter(ant -> ant.isAlive() && (ant.lostLegs() != 0 || ant.getHealth() < whole.get(ant.getUUID()) - 0.01F)).count();
                long legs = ants.stream().filter(ant -> ant.isAlive() && ant.lostLegs() != 0).count();
                SavannaMod.LOGGER.info("[alarm-measure] colony of {} (added{}); at the start: {}. Ticks after the raiders came: cover broken {}, "
                        + "first soldier at the break {}, first bite on a raider {}, sealed {}. Soldiers: wave target {}, most alarmed {}, most within 4 "
                        + "blocks of the break {}, seen {}, lost {}. Workers stung {} ({} of the 6 feeding). Raiders of 20: {} dead, {} hurt ({} "
                        + "with a leg off).{}{}",
                        colony.getCount(com.antcolonies.colony.Caste.WORKER), added, atStart[0], brokeAt[0], soldierAt[0], biteAt[0], sealedAt[0],
                        waveTarget[0], mostAlarmed[0], mostAtBreak[0], soldiersSeen.size(), soldiersLost.size(), stung.size(), feedingStung, dead,
                        hurt, legs, alarm.describe(level), raid.describe(level));
                helper.assertTrue(brokeAt[0] >= 0 || !stung.isEmpty(), "the raid never happened: the cover was not broken and no termite stung");
                helper.succeed();
            };
        });
    }

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
            TermiteScenes.digNestAtOnce(level, colony);
            level.setBlock(colony.nest().getEntrance(), Blocks.STONE.defaultBlockState(), 3);
            for (TermiteWorkerEntity worker : TestIsolation.insectsOf(helper, TermiteWorkerEntity.class)) {
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
