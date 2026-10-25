package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
        boolean[] bitten = {false};
        // When the minor's last blow began, and when the worker was felled (-1 for not yet).
        long[] blowAt = {-1};
        boolean[] inBlow = {false};
        long[] felledAt = {-1};
        long[] paralysedAt = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
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
                    "the termite major soldier has not been stung still yet (the minor at " + helper.relativePos(hunterMinor.blockPosition()) + ")");
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

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }
}
