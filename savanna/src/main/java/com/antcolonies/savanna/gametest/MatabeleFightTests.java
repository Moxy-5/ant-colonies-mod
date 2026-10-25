package com.antcolonies.savanna.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared fight system's first pieces (M8 steps 2a and 2b, {@code docs/plans/m8-matabele.md}): the health and damage
 * of each body come from its species file's {@code fight} numbers; a Matabele sent to fight stings a termite and the
 * termite falls paralysed, alive (the venom, not the wound), and is not stung again; a termite soldier's bite hurts a
 * Matabele by the soldier's damage and does not paralyse it; a Matabele major bites open the mud over a termite it
 * cannot reach and stings it. The termite soldiers, major and minor, are stung still too (the user's ask, 2026-10-25:
 * the fights are tested on the soldier bodies as well as the worker's).
 *
 * <p>M8 fight polish, round A: a blow plays its clip and lands only at the sting moment (the termite falls no sooner
 * than {@link #STING_MOMENT} ticks after the blow began), the venom then takes the termite's {@code venom_ticks} to fell
 * it (it staggers, then lies paralysed), and an ant on a wall never strikes (the user's rule).
 *
 * <p>Round B: grabs can fail (a termite twists away, a soldier bites first); a ten-ant skirmish at a feeding spot lasts
 * minutes, with workers fleeing down the holes ({@link #tenMatabelesAtAFeedingSpotTakeMinutesAndManyTermitesEscape}).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleFightTests {
    /** Ticks from the start of a Matabele's blow to its sting going in (attack_a and attack_b, {@code MatabeleEntity}). */
    private static final int STING_MOMENT = 11;
    /** The venom's time on a termite worker (macrotermes.json, {@code venom_ticks}). */
    private static final int WORKER_VENOM = 50;

    @GameTest(template = "arena", timeoutTicks = 1200, skyAccess = true)
    public static void matabeleStingParalysesATermiteAndASoldierBitesBack(GameTestHelper helper) {
        floor(helper);
        TermiteWorkerEntity worker = TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 8, 1, 6);
        MatabeleEntity minor = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MINOR.get(), 4, 1, 6);
        minor.orderToFight(helper.absolutePos(new BlockPos(8, 1, 6)), helper.getLevel().getGameTime() + 2400);
        TermiteSoldierEntity soldier = TestAnts.lone(helper, SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), 4, 1, 10);
        MatabeleEntity major = TestAnts.lone(helper, SavannaEntities.MATABELE_MAJOR.get(), 5, 1, 10);
        // The soldiers as prey: a major sent at a termite minor soldier, a minor at a termite major soldier.
        TermiteSoldierEntity minorSoldier = TestAnts.lone(helper, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), 14, 1, 2);
        MatabeleEntity hunterMajor = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MAJOR.get(), 11, 1, 2);
        hunterMajor.orderToFight(helper.absolutePos(new BlockPos(14, 1, 2)), helper.getLevel().getGameTime() + 2400);
        TermiteSoldierEntity majorSoldier = TestAnts.lone(helper, SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), 14, 1, 13);
        MatabeleEntity hunterMinor = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MINOR.get(), 11, 1, 13);
        hunterMinor.orderToFight(helper.absolutePos(new BlockPos(14, 1, 13)), helper.getLevel().getGameTime() + 2400);
        // Round B: a termite major soldier that always twists out of the grab and bites first: the grabs miss, the
        // Matabele is bitten, the soldier stays on its feet.
        TermiteSoldierEntity dodger = TestAnts.lone(helper, SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), 1, 1, 1);
        dodger.setDodgeForTests(1.0, 1.0);
        MatabeleEntity grabber = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MAJOR.get(), 1, 1, 4);
        grabber.orderToFight(helper.absolutePos(new BlockPos(1, 1, 1)), helper.getLevel().getGameTime() + 2400);
        boolean[] grabberBitten = {false};
        boolean[] bitten = {false};
        // When the minor's last blow began, and when the worker was felled (-1 for not yet).
        long[] blowAt = {-1};
        boolean[] inBlow = {false};
        long[] felledAt = {-1};
        long[] paralysedAt = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            // The soldiers bite back now and then as they twist out of a grab (round B); the hunters are kept whole
            // here, so the stings on the soldiers are seen through (a bitten hunter is checked on the grabber).
            if (grabber.getHealth() < grabber.getMaxHealth()) {
                grabberBitten[0] = true;
            }
            for (MatabeleEntity hunter : new MatabeleEntity[] {minor, hunterMajor, hunterMinor, grabber}) {
                if (hunter.isAlive()) {
                    hunter.setHealth(hunter.getMaxHealth());
                }
            }
            if (minor.isInABlow() && !inBlow[0]) {
                blowAt[0] = tick;
            }
            inBlow[0] = minor.isInABlow();
            if (worker.isDowned() && felledAt[0] < 0) {
                felledAt[0] = tick;
                helper.assertTrue(blowAt[0] >= 0 && tick - blowAt[0] >= STING_MOMENT - 1,
                        "the termite was felled " + (tick - blowAt[0]) + " ticks into the blow; the sting goes in at " + STING_MOMENT);
                helper.assertTrue(!worker.isParalysed(), "the termite should stagger a while before it lies paralysed");
            }
            if (worker.isParalysed() && paralysedAt[0] < 0) {
                paralysedAt[0] = tick;
                helper.assertTrue(felledAt[0] >= 0 && tick - felledAt[0] >= WORKER_VENOM - 1,
                        "the termite lay paralysed " + (tick - felledAt[0]) + " ticks after the sting; the venom takes " + WORKER_VENOM);
            }
            if (helper.getTick() == 5) {
                // 2a: each body's numbers from its species file (macrotermes.json, megaponera_analis.json).
                helper.assertTrue(worker.getMaxHealth() == 3.0F, "a termite worker should have 3 health, has " + worker.getMaxHealth());
                helper.assertTrue(soldier.getMaxHealth() == 12.0F, "a termite major soldier should have 12 health, has " + soldier.getMaxHealth());
                helper.assertTrue(minor.getMaxHealth() == 5.0F, "a Matabele minor should have 5 health, has " + minor.getMaxHealth());
                helper.assertTrue(major.getMaxHealth() == 10.0F && major.getHealth() == 10.0F,
                        "a Matabele major should start with 10 of 10 health, has " + major.getHealth() + " of " + major.getMaxHealth());
                // A soldier's bite is a wound by its damage (4), never the venom.
                soldier.bite(major);
                helper.assertTrue(major.getHealth() == 6.0F, "the soldier's bite should take 4 health, the major has " + major.getHealth());
                helper.assertTrue(!major.isParalysed(), "a termite's bite should not paralyse a Matabele");
                bitten[0] = true;
            }
            if (worker.isParalysed()) {
                helper.assertTrue(worker.isAlive() && worker.getHealth() > 0.0F, "a stung termite should lie paralysed, alive");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bitten[0], "the soldier has not bitten yet");
            helper.assertTrue(worker.isParalysed(), "the Matabele has not stung the termite still yet (the minor at "
                    + helper.relativePos(minor.blockPosition()) + ")");
            helper.assertTrue(worker.isAlive(), "the stung termite died; it should lie paralysed");
            helper.assertTrue(worker.getHealth() >= 2.5F, "a paralysed termite should not be stung again (health "
                    + worker.getHealth() + ")");
            helper.assertTrue(minorSoldier.isParalysed() && minorSoldier.isAlive(),
                    "the termite minor soldier has not been stung still yet (the major at " + helper.relativePos(hunterMajor.blockPosition()) + ")");
            helper.assertTrue(majorSoldier.isParalysed() && majorSoldier.isAlive(),
                    "the termite major soldier has not been stung still yet (the minor at " + helper.relativePos(hunterMinor.blockPosition())
                            + ", alive " + hunterMinor.isAlive() + ", goals " + hunterMinor.runningGoals() + ", spot " + hunterMinor.getFightSpot()
                            + ", missed " + hunterMinor.grabsMissed() + "; the soldier at " + helper.relativePos(majorSoldier.blockPosition())
                            + ", stings taken? downed " + majorSoldier.isDowned() + ")");
            helper.assertTrue(grabber.grabsMissed() >= 2, "the soldier that always dodges should have made the grabs miss ("
                    + grabber.grabsMissed() + " missed, the major at " + helper.relativePos(grabber.blockPosition()) + ")");
            helper.assertTrue(grabberBitten[0], "the soldier should have bitten the Matabele as it twisted away");
            helper.assertTrue(!dodger.isDowned(), "a soldier that dodges every grab should never be stung");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1200, skyAccess = true)
    public static void matabeleMajorBitesTheMudOpenOverATermite(GameTestHelper helper) {
        floor(helper);
        // A termite in a one-block gallery under the ground: soil all round, termite mud for its roof.
        for (int x = 7; x <= 9; x++) {
            for (int z = 5; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.DIRT);
            }
        }
        helper.setBlock(8, 1, 6, Blocks.AIR);
        helper.setBlock(8, 2, 6, SavannaBlocks.MUD_CRUST.get());
        TermiteWorkerEntity worker = TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 8, 1, 6);
        MatabeleEntity major = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MAJOR.get(), 3, 1, 6);
        major.orderToFight(helper.absolutePos(new BlockPos(8, 1, 6)), helper.getLevel().getGameTime() + 2400);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 2, 6)).isAir(), "the major has not bitten the mud open yet (at "
                    + helper.relativePos(major.blockPosition()) + ")");
            helper.assertTrue(worker.isParalysed() && worker.isAlive(), "the termite under the mud has not been stung still yet");
        });
    }

    /**
     * The user's rule (M8 fight polish, round A): nothing strikes unless standing on a floor. A termite sits on top of a
     * block two high; the Matabele sent at it climbs the side, and on the wall, with the termite within reach, it never
     * begins a blow; once on top it stings it.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void matabeleOnAWallNeverStrikes(GameTestHelper helper) {
        floor(helper);
        for (int x = 7; x <= 9; x++) {
            for (int z = 5; z <= 7; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        TermiteWorkerEntity worker = TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 8, 3, 6);
        MatabeleEntity minor = TestAnts.loneWithAMind(helper, SavannaEntities.MATABELE_MINOR.get(), 3, 1, 6);
        minor.orderToFight(helper.absolutePos(new BlockPos(8, 3, 6)), helper.getLevel().getGameTime() + 2400);
        boolean[] climbed = {false};
        boolean[] inBlow = {false};
        helper.onEachTick(() -> {
            if (minor.isClimbing()) {
                climbed[0] = true;
            }
            if (minor.isInABlow() && !inBlow[0]) {
                helper.assertTrue(!minor.isClimbing(), "the Matabele began a blow while on a wall (" + minor.getSurface() + ")");
            }
            inBlow[0] = minor.isInABlow();
            helper.assertTrue(!worker.isPinned() || !minor.isClimbing(), "the termite is held by a Matabele on a wall");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(climbed[0], "the Matabele has not climbed the block yet (at " + helper.relativePos(minor.blockPosition()) + ")");
            helper.assertTrue(worker.isDowned() && worker.isAlive(), "the Matabele has not stung the termite on top yet (at "
                    + helper.relativePos(minor.blockPosition()) + ", " + minor.getSurface() + ")");
        });
    }

    /**
     * The pace (M8 fight polish, round B; real raids: the attack at a feeding site lasts 5 to 30 minutes, Yusuf 2026). A
     * termite colony with its nest dug and both foraging holes open; twelve old workers forage at night at a hay bale 5
     * blocks from a hole, two minors on guard. Once four are at the hay, ten Matabele come from the far side, sent to
     * fight there (as the skirmish command does). Checked: half a minute in, the fight is still on and not every
     * worker is down; workers flee down the holes and hide; at least one termite is stung still; the ants search on
     * and the fight is over only after minutes (2 or more), within the order's time.
     */
    @GameTest(template = "arena", timeoutTicks = 9000, skyAccess = true, batch = "matabele_pace")
    public static void tenMatabelesAtAFeedingSpotTakeMinutesAndManyTermitesEscape(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), TestScenes.MIDDLE, TestScenes.SURFACE, TestScenes.MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 12; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), TestScenes.MIDDLE - 3 + i % 6, TestScenes.SURFACE, TestScenes.MIDDLE - 2 - i / 6);
            }
        });
        BlockPos[] hay = new BlockPos[1];
        Direction[] out = new Direction[1];
        List<MatabeleEntity> ants = new ArrayList<>();
        long[] cameAt = {-1};
        Set<UUID> foragers = new HashSet<>();
        Set<UUID> downed = new HashSet<>();
        Set<UUID> hid = new HashSet<>();
        boolean[] overLogged = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            TestClock.still(helper, 12.0); // midday: everybody stays in while the nest is made ready
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
            out[0] = TermiteSoldierTests.intoArena(tunnels.get(0).side().getClockWise());
            hay[0] = tunnels.get(0).hole().above().relative(out[0], 5);
            colony.foodMap().knowForTests(hay[0]);
            level.setBlock(hay[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 2; i++) {
                TermiteSoldierTests.put(level, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), room.get(i));
            }
        });
        helper.runAfterDelay(40, () -> TestClock.still(helper, 0.0)); // midnight: the foragers go out
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || hay[0] == null || helper.getTick() < 40) {
                return;
            }
            long tick = helper.getTick();
            List<TermiteWorkerEntity> workers = level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds().inflate(8),
                    worker -> colony.getId().equals(worker.getColonyId()));
            if (cameAt[0] < 0) {
                List<TermiteWorkerEntity> atTheHay = workers.stream().filter(worker -> worker.blockPosition().closerThan(hay[0], 4.0)).toList();
                if (tick % 400 == 0) {
                    SavannaMod.LOGGER.info("[pace-test] tick {}: {} workers, {} out, {} at the hay; foraging time {}", tick, workers.size(),
                            workers.stream().filter(worker -> !colony.nest().isInside(level, worker.blockPosition())).count(), atTheHay.size(),
                            colony.isForagingTime(level));
                }
                if (atTheHay.size() >= 4) {
                    cameAt[0] = tick;
                    for (TermiteWorkerEntity worker : workers) {
                        if (!colony.nest().isInside(level, worker.blockPosition())) {
                            foragers.add(worker.getUUID());
                        }
                    }
                    BlockPos from = hay[0].relative(out[0], 8);
                    for (int i = 0; i < 10; i++) {
                        MatabeleEntity ant = (i % 2 == 0 ? SavannaEntities.MATABELE_MAJOR.get() : SavannaEntities.MATABELE_MINOR.get()).create(level);
                        ant.moveTo(from.getX() + 0.2 + (i % 3) * 0.3, from.getY(), from.getZ() + 0.2 + (i / 3) * 0.2, 0.0F, 0.0F);
                        ant.orderToFight(hay[0], level.getGameTime() + 6000);
                        level.addFreshEntity(ant);
                        ants.add(ant);
                    }
                    SavannaMod.LOGGER.info("[pace-test] tick {}: ten Matabele come, {} foragers out", tick, foragers.size());
                }
                return;
            }
            for (TermiteWorkerEntity worker : workers) {
                if (worker.isDowned()) {
                    downed.add(worker.getUUID());
                }
                // Escaped: a forager safe inside its nest (fled on its own, or gone in on the alarm), never stung.
                if ((worker.isHiding() || foragers.contains(worker.getUUID()) && worker.isSheltered()) && !downed.contains(worker.getUUID())) {
                    hid.add(worker.getUUID());
                }
            }
            long since = tick - cameAt[0];
            boolean on = ants.stream().anyMatch(ant -> ant.isAlive() && ant.getFightSpot() != null);
            if (since == 600) {
                helper.assertTrue(on, "the fight was over within half a minute");
                helper.assertTrue(downed.size() < workers.size(), "every worker was stung still within half a minute");
            }
            if (since % 600 == 0) {
                SavannaMod.LOGGER.info("[pace-test] {} s in: {} stung, {} hid, grabs missed {}, fight on {}", since / 20, downed.size(), hid.size(),
                        ants.stream().mapToInt(MatabeleEntity::grabsMissed).sum(), on);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cameAt[0] >= 0, "the Matabele have not come yet (fewer than 4 foragers at the hay)");
            long since = helper.getTick() - cameAt[0];
            helper.assertTrue(ants.stream().noneMatch(ant -> ant.isAlive() && ant.getFightSpot() != null), "the fight is still on ("
                    + since / 20 + " s, " + downed.size() + " stung, " + hid.size() + " hid)");
            if (!overLogged[0]) {
                overLogged[0] = true;
                SavannaMod.LOGGER.info("[pace-test] over after {} s: {} of {} foragers stung, {} hid, grabs missed {}", since / 20, downed.size(),
                        foragers.size(), hid.size(), ants.stream().mapToInt(MatabeleEntity::grabsMissed).sum());
            }
            helper.assertTrue(since >= 2400, "the fight was over after " + since / 20 + " s; it should take minutes");
            helper.assertTrue(!downed.isEmpty(), "no termite was stung still");
            helper.assertTrue(hid.size() >= 3, "only " + hid.size() + " workers escaped down the holes; many should");
        });
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }
}
