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
import com.antcolonies.savanna.colony.Soldiers;
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
                        // (the rework, step 3: the seal waits for a soldier just inside, so the one from the nest may have
                        // run out past it and be shut out with the others; never one still on the nest's side)
                        helper.assertTrue(!raid.isShutOut(deepIn.getUUID())
                                || deepIn.position().distanceTo(Vec3.atCenterOf(breakAt)) < Math.sqrt(plug.distSqr(breakAt)) + 0.5,
                                "the soldier still on the nest's side of the seal counts as shut out");
                        helper.assertTrue(worker.position().distanceTo(Vec3.atCenterOf(breakAt)) > Math.sqrt(plug.distSqr(breakAt)),
                                "the worker should have gone deeper than the seal, but is at " + worker.blockPosition().toShortString());
                        raider.discard(); // the raiders go
                        colony.part(SavannaParts.ALARM).ifPresent(Alarm::calmForTests);
                        calmedAt[0] = tick;
                    }
                    if (calmedAt[0] >= 0 && waitedAt[0] < 0 && tick - calmedAt[0] > 20) {
                        BlockPos waitAt = cells.get(at - PLUG_BACK + 1);
                        for (TermiteSoldierEntity soldier : new TermiteSoldierEntity[] {byTheBreak, beside}) {
                            if (soldier.isAlive() && soldier.position().distanceTo(Vec3.atBottomCenterOf(waitAt)) <= 2.5) { // (its beat by the seal)
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

    /**
     * The termite alarm rework, step 3 (was 3S's waves): a raider breaks into a gallery by day. Four soldiers stand in
     * the tunnel on the nest side of where the seal will be; forty more are numbers in the nest. The drumming runs out
     * from the break: the four hear it one after another (not at once), run out past the seal's place before it is
     * packed (the workers seal behind the soldiers), take their own places round the break and bite the raider; then
     * the seal is packed. While the drumming reaches the nest, soldiers come out of the numbers.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "termite_raid_waves")
    public static void soldiersWithinEarshotComeToARaidsBreakTakeTheirOwnPlacesAndBite(GameTestHelper helper) {
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
            List<TermiteSoldierEntity> four = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                four.add(TermiteSoldierTests.put(level, i == 3 ? SavannaEntities.TERMITE_MAJOR_SOLDIER.get() : SavannaEntities.TERMITE_MINOR_SOLDIER.get(),
                        cells.get(Math.max(0, at - PLUG_BACK - 1 - i))));
            }
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), level.getGameTime(), 30);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), level.getGameTime(), 10);
            colony.population().setOnForTests(true); // (the real-mob swap on, nobody watching: only the trouble calls soldiers out)
            MatabeleEntity raider = raider(level, breakAt);
            // (the rework, step 4, the held mouth: a second raider out of everyone's reach, asked whether the break is a wall to it)
            MatabeleEntity watcher = raider(level, breakAt.above(8));
            int[] heldTicks = {0};
            Alarm alarm = colony.part(SavannaParts.ALARM).orElseThrow();
            RaidDefence raid = colony.part(SavannaParts.RAID_DEFENCE).orElseThrow();
            long tellAt = helper.getTick() + 10;
            boolean[] told = {false};
            boolean[] bitten = {false};
            int[] mostPlaces = {0};
            int[] mostReal = {0};
            long[] sealedAt = {-1};
            java.util.Set<Long> heardAt = new java.util.HashSet<>();
            each[0] = () -> {
                long tick = helper.getTick();
                if (!told[0]) {
                    if (tick < tellAt) {
                        return;
                    }
                    told[0] = true;
                    colony.news().tell(level, ColonyNews.BROKEN_OPEN, new ColonyNews.BrokenOpen(breakAt, raider));
                    helper.assertTrue(alarm.worst(level) == Alarm.Kind.BREACH, "a raid's break should be felt as a breach:" + alarm.describe(level));
                    helper.assertTrue(four.stream().noneMatch(soldier -> alarm.hasHeard(soldier.getUUID())),
                            "a soldier 5 or more blocks off heard the drumming the moment it began");
                }
                if (raider.isAlive()) {
                    if (raider.getHealth() < raider.getMaxHealth()) {
                        bitten[0] = true;
                    }
                    raider.setHealth(raider.getMaxHealth()); // (it stays at the break)
                }
                int places = 0;
                List<TermiteSoldierEntity> real = TestIsolation.insectsOf(helper, TermiteSoldierEntity.class);
                TermiteSoldierEntity inMouth = null;
                for (TermiteSoldierEntity soldier : real) {
                    if (alarm.holdsPlace(soldier.getUUID())) {
                        places++;
                    }
                    if (soldier.isAlive() && alarm.holdsMouth(soldier.getUUID()) && soldier.blockPosition().equals(breakAt)) {
                        inMouth = soldier;
                    }
                }
                // Step 4, the held mouth: while a living soldier stands in the opening it is a wall to the raiders (their
                // legs and their "is it shut in?"), never to its own side; with no soldier in it, to nobody.
                boolean barred = com.antcolonies.fight.HeldCells.barred(level, breakAt, watcher);
                if (inMouth == null) {
                    helper.assertTrue(!barred, "the break is a wall to a raider with no soldier standing in it");
                } else if (barred) {
                    heldTicks[0]++;
                    helper.assertTrue(!new com.antcolonies.nav.LevelCellMap(level, watcher).isOpen(breakAt),
                            "a raider's legs may still plan through the mouth a soldier holds");
                    for (TermiteSoldierEntity soldier : real) {
                        helper.assertTrue(!com.antcolonies.fight.HeldCells.barred(level, breakAt, soldier),
                                "the mouth a soldier holds is a wall to its own nestmates");
                    }
                    if (sealedAt[0] < 0) {
                        helper.assertTrue(com.antcolonies.fight.Fights.isShutIn(level, breakAt.relative(net.minecraft.core.Direction.DOWN, 0).offset(
                                        cells.get(at - 1).getX() - breakAt.getX(), cells.get(at - 1).getY() - breakAt.getY(), cells.get(at - 1).getZ() - breakAt.getZ()), watcher)
                                        || com.antcolonies.fight.Fights.isInTheOpen(level, cells.get(at - 1)),
                                "a termite one cell behind the held mouth is not shut in for a raider");
                    }
                }
                for (TermiteSoldierEntity soldier : four) {
                    if (alarm.hasHeard(soldier.getUUID())) {
                        heardAt.add(alarm.heardAt(soldier.getUUID()));
                    }
                }
                mostPlaces[0] = Math.max(mostPlaces[0], places);
                mostReal[0] = Math.max(mostReal[0], real.size());
                if (sealedAt[0] < 0 && level.getBlockState(plug).is(colony.profile().nest().plugState().getBlock())) {
                    sealedAt[0] = tick;
                }
                if (sealedAt[0] >= 0 && bitten[0] && mostPlaces[0] >= 2 && mostReal[0] > 4 && heldTicks[0] >= 20) {
                    helper.assertTrue(heardAt.size() >= 2, "the four soldiers heard the drumming at the same moment (it does not run out)");
                    long out = four.stream().filter(soldier -> raid.isShutOut(soldier.getUUID()) || !soldier.isAlive()).count();
                    helper.assertTrue(out >= 2, "the seal was packed at " + (sealedAt[0] - tellAt) + " ticks before the soldiers were out: only "
                            + out + " of the four are outside it");
                    SavannaMod.LOGGER.info("[raid-earshot-test] {} held places, {} of the four out before the seal, sealed after {} ticks, {} soldiers real, the mouth held {} ticks",
                            mostPlaces[0], out, sealedAt[0] - tellAt, mostReal[0], heldTicks[0]);
                    helper.succeed();
                }
            };
            failAt[0] = () -> "mouth held " + heldTicks[0] + " ticks, bitten " + bitten[0] + ", most holding places " + mostPlaces[0] + ", most real soldiers " + mostReal[0] + ", heard at "
                    + heardAt + ", sealed at " + sealedAt[0] + "; the four at " + four.stream().map(s -> s.blockPosition().toShortString()).toList()
                    + ", break " + breakAt.toShortString() + ", plug " + plug.toShortString() + alarm.describe(level) + raid.describe(level);
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
            long[] leftAt = {-1};
            long[] preyAt = {0};
            long[] soldierAt = {-1};
            long[] biteAt = {-1};
            long[] sealedAt = {-1};
            int[] mostAtBreak = {0};
            int[] mostAlarmed = {0};
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
                    preyAt[0] = since;
                    // (a new ant's health settles with its own size in its first ticks: "hurt" is health lost after that)
                    ants.forEach(ant -> whole.put(ant.getUUID(), ant.getHealth()));
                    return;
                }
                if (brokeAt[0] < 0 && !level.getBlockState(cover).is(com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get())) {
                    brokeAt[0] = since;
                }
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
                // Step 5: the scene's raid ends as a column's does (it was a 5-minute skirmish): a badly wounded raider
                // leaves the fight, and the attack is over 30 s after the last prey about the spot (soldiers alone do
                // not keep it going), or after its longest time; what is counted is what had happened by then.
                if (leftAt[0] < 0) {
                    boolean breaking = ants.stream().anyMatch(ant -> ant.isAlive() && ant.breaksIn() && ant.getFightSpot() != null);
                    if (com.antcolonies.savanna.colony.RaidColumn.preyAbout(level, null, breakAt, 10.0, breaking)) {
                        preyAt[0] = since;
                    }
                    for (MatabeleEntity ant : ants) {
                        if (ant.isAlive() && ant.badlyWounded() && ant.getFightSpot() != null) {
                            ant.orderToFight(breakAt, 0L);
                        }
                    }
                    // (the scene's majors take from 4 s to 3 minutes to find the cover: the attack's clock starts at its break)
                    if (brokeAt[0] < 0 && stung.isEmpty()) {
                        preyAt[0] = since;
                        if (since < 3600) {
                            return;
                        }
                    }
                    long began = brokeAt[0] < 0 ? 0 : brokeAt[0];
                    if (since - Math.max(preyAt[0], began) >= 600 || since - began >= 2400) {
                        leftAt[0] = since;
                        ants.forEach(ant -> ant.orderToFight(breakAt, 0L));
                    }
                    return;
                }
                if (since < leftAt[0] + 100) {
                    return;
                }
                long feedingStung = feeding.stream().filter(stung::contains).count();
                long dead = ants.stream().filter(ant -> !ant.isAlive()).count();
                long hurt = ants.stream().filter(ant -> ant.isAlive() && (ant.lostLegs() != 0 || ant.getHealth() < whole.get(ant.getUUID()) - 0.01F)).count();
                long legs = ants.stream().filter(ant -> ant.isAlive() && ant.lostLegs() != 0).count();
                SavannaMod.LOGGER.info("[alarm-measure] colony of {} (added{}); at the start: {}. Ticks after the raiders came: cover broken {}, "
                        + "first soldier at the break {}, first bite on a raider {}, sealed {}. Soldiers: most answering {}, most within 4 "
                        + "blocks of the break {}, seen {}, lost {}. Workers stung {} ({} of the 6 feeding). Raiders of 20: {} dead, {} hurt ({} "
                        + "with a leg off). The raid left at {}.{}{}",
                        colony.getCount(com.antcolonies.colony.Caste.WORKER), added, atStart[0], brokeAt[0], soldierAt[0], biteAt[0], sealedAt[0],
                        mostAlarmed[0], mostAtBreak[0], soldiersSeen.size(), soldiersLost.size(), stung.size(), feedingStung, dead,
                        hurt, legs, leftAt[0], alarm.describe(level), raid.describe(level));
                helper.assertTrue(brokeAt[0] >= 0 || !stung.isEmpty(), "the raid never happened: the cover was not broken and no termite stung");
                helper.succeed();
            };
        });
    }

    /**
     * The termite alarm rework, step 2: soldiers go where the workers are. The same colony by day, with a feeding place
     * on its record: the last cells of its first foraging tunnel a gallery under dead wood, two blocks of it eaten
     * through and plastered with mud (so the workers can eat there by day under cover, the place a raid comes to). A
     * minor and three majors are put in the royal chamber. The minor takes the beat at the feeding place before any
     * tunnel end; two majors hold the heart (the royal doorway, the way in from the shaft) and the third goes out to
     * the feeding place too (the user's ask); both are there within the time, on their beats.
     */
    @GameTest(template = "arena", timeoutTicks = 4000, skyAccess = true, batch = "termite_feeding_beats")
    public static void soldiersWalkBeatsAtAFeedingPlaceByDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Scene scene = scene(helper, 9.0);
        List<TermiteSoldierEntity> soldiers = new java.util.ArrayList<>();
        helper.runAfterDelay(15, () -> {
            Colony colony = scene.colony[0];
            List<BlockPos> cells = scene.tunnel[0].tunnel();
            int at = cells.size() - 2;
            helper.assertTrue(at >= 4, "the foraging tunnel is too short for the test: " + cells.size() + " cells");
            List<BlockPos> gallery = List.of(cells.get(at - 3), cells.get(at - 2), cells.get(at - 1), cells.get(at));
            // (the mud: a crust with its plate toward the gallery, so the gallery is closed all round and fed in by day)
            net.minecraft.world.level.block.state.BlockState mud = ((com.antcolonies.block.FacedWall) com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get())
                    .withFace(com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get().defaultBlockState(), net.minecraft.core.Direction.DOWN);
            for (int i = 0; i < 4; i++) {
                level.setBlock(gallery.get(i).above(), i == 1 || i == 2 ? mud
                        : com.antcolonies.savanna.registry.SavannaBlocks.DEAD_WOOD.get().defaultBlockState(), 3);
            }
            BlockPos food = gallery.get(3).above();
            colony.foodMap().knowForTests(food);
            colony.nest().foodWays().addNewTunnelForTests(new com.antcolonies.colony.FoodWays.NewTunnel(scene.tunnel[0], food, gallery));
            helper.assertTrue(com.antcolonies.savanna.colony.FeedingSpots.byDayPlaces(level, colony).size() == 1,
                    "the test's feeding gallery does not count as fed at by day");
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            soldiers.add(TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), room.get(0)));
            for (int i = 1; i <= 3; i++) {
                soldiers.add(TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), room.get(i % room.size())));
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = scene.colony[0];
            helper.assertTrue(colony != null && soldiers.size() == 4, "not started");
            Soldiers part = colony.part(SavannaParts.SOLDIERS).orElseThrow();
            int majorsThere = 0;
            StringBuilder where = new StringBuilder();
            for (TermiteSoldierEntity soldier : soldiers) {
                Soldiers.Post post = part.beatOf(level, soldier);
                String wrong = TermiteSoldierTests.offItsBeat(soldier, post);
                where.append(soldier.getRank()).append(' ').append(post == null ? "no beat" : post.kind() + (wrong == null ? "" : " (not there yet)")).append("; ");
                if (soldier.getRank() == TermiteSoldierEntity.Rank.MAJOR && post != null && post.kind() == Soldiers.Kind.FEEDING && wrong == null) {
                    majorsThere++;
                }
            }
            Soldiers.Post minors = part.beatOf(level, soldiers.get(0));
            helper.assertTrue(minors != null && minors.kind() == Soldiers.Kind.FEEDING && TermiteSoldierTests.offItsBeat(soldiers.get(0), minors) == null,
                    "the minor is not on a beat at the feeding place: " + where);
            helper.assertTrue(majorsThere == 1, majorsThere + " majors on a beat at the feeding place, expected 1: " + where);
            SavannaMod.LOGGER.info("[soldier-test] feeding beats at tick {}: {}", helper.getTick(), where);
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
