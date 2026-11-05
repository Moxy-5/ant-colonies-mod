package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M8 step 3f, out of sight: a Matabele colony nobody is near (the far rule: it lives as numbers) still raids. Its prey
 * pile empty and a termite feeding spot remembered, no raid leaves at 13:00 (between the raid hours); in the evening raid
 * hours a raid sets out as numbers, takes termites from the termite colony there and brings them home: the pile fills,
 * the termites have fewer workers, and the away report counts it.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleAwayTests {
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_away")
    public static void aFarMatabeleColonyRaidsOutOfSightInTheRaidHours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.deepGround(helper);
        TestClock clock = TestClock.still(helper, 13.0); // (between the raid hours: no raid may leave)
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)), 200);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElseThrow();
        // the termites: a royal pair founding in the far corner, with a big crowd of workers in its numbers
        BlockPos queenAt = helper.absolutePos(new BlockPos(54, SURFACE, 54));
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), 54, SURFACE, 54);
        BlockPos spot = helper.absolutePos(new BlockPos(44, SURFACE, 44)); // (where the termites feed, 14 blocks off their mound)
        Colony[] termites = new Colony[1];
        int[] termitesBefore = new int[1];
        int[] stage = {0};
        long[] stageAt = new long[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (now < 10) {
                        return;
                    }
                    termites[0] = ColonyManager.get(level).getColonies().stream()
                            .filter(other -> SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(queenAt, 6))
                            .findFirst().orElse(null);
                    helper.assertTrue(termites[0] != null, "no termite colony founded by the queen");
                    termites[0].population().setOnForTests(true);
                    termites[0].population().addNewMembers(Caste.WORKER, level.getGameTime(), 3000);
                    termitesBefore[0] = termites[0].population().inside(Caste.WORKER);
                    raids.rememberForTests(spot, 12, level.getGameTime()); // (a scout found them before the player left)
                    helper.assertTrue(raids.isHungry(), "the Matabele colony is not hungry with an empty pile");
                    // far: the away rule on, nobody near, a short grace
                    colony.away().setOnForTests(true);
                    colony.away().setFarForTests(true);
                    colony.away().setNearForTests(false);
                    colony.away().setGraceForTests(40L);
                    stage[0] = 1;
                    stageAt[0] = now;
                }
                case 1 -> {
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(now - stageAt[0] < 2000, "the far Matabele colony never went away");
                        return;
                    }
                    if (now - stageAt[0] < 600) {
                        return; // (10 game seconds of 13:00 away)
                    }
                    helper.assertFalse(raids.away().isOut(), "a raid left out of sight at 13:00, between the raid hours");
                    helper.assertTrue(colony.away().report().get(AwayReport.What.RAIDS) == 0,
                            "the away report counts a raid at 13:00: " + colony.away().report().describe());
                    clock.skipTo(16.5); // (the evening raid hours)
                    stage[0] = 2;
                    stageAt[0] = now;
                }
                case 2 -> {
                    AwayReport report = colony.away().report();
                    if (report.get(AwayReport.What.RAIDS) < 1 || report.get(AwayReport.What.PREY_BROUGHT) <= 0) {
                        return;
                    }
                    helper.assertTrue(colony.away().isAway(level), "the colony came back during the test");
                    helper.assertTrue(report.get(AwayReport.What.RAIDERS) >= 10, "a raid of only " + report.get(AwayReport.What.RAIDERS) + " set out");
                    helper.assertTrue(pile.broughtIn() > 0 && pile.edible() > 0, "the catch did not reach the prey pile" + pile.describe(level));
                    int left = termites[0].population().inside(Caste.WORKER);
                    helper.assertTrue(left < termitesBefore[0], "the termites lost no workers to the raid (" + left + " of " + termitesBefore[0] + ")");
                    helper.assertTrue(termitesBefore[0] - left >= report.get(AwayReport.What.PREY_BROUGHT),
                            "the raid brought home " + report.get(AwayReport.What.PREY_BROUGHT) + " termites, the termites lost only " + (termitesBefore[0] - left));
                    helper.assertTrue(termites[0].keepsOff(level, spot), "the termites do not keep off the spot they were raided at");
                    SavannaMod.LOGGER.info("[away raid test] home at {}: {}; {}", now, report.describe(), raids.describe(level));
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }
}
