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
        Colony dead = MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 200);
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
                    // M8 step 3e, the raid day: a starving colony wants the most raids a day (4); with them all made today
                    // (near or away) no raid leaves in the evening raid hours, and the next morning's do.
                    helper.assertTrue(raids.raidsWanted() == 4, "a colony with an empty pile wants " + raids.raidsWanted() + " raids a day, not 4");
                    raids.fillDayForTests(level);
                    helper.assertFalse(raids.dayHasRoom(level), "the day has room for a raid after all its raids");
                    clock.skipTo(16.5); // (the evening raid hours)
                    stage[0] = 10;
                    stageAt[0] = now;
                }
                case 10 -> {
                    if (now - stageAt[0] < 600) {
                        helper.assertFalse(raids.away().isOut(), "a raid left out of sight with the day's raids all made");
                        return;
                    }
                    helper.assertTrue(colony.away().report().get(AwayReport.What.RAIDS) == 0,
                            "the away report counts a raid with the day's raids all made: " + colony.away().report().describe());
                    clock.skipTo(7.0); // (the next morning's raid hours: a new day)
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
                    // M8 step 3d, honest sizes (2026-11-11): about per_raider (0.3) termites a raider, never more than a raider each, and
                    // the termites' headcount losing only one in its stands_for of them (each counted termite stands for
                    // many real ones; the remainder by dice, and a little slack for a natural death meanwhile).
                    int brought = report.get(AwayReport.What.PREY_BROUGHT);
                    int raiders = report.get(AwayReport.What.RAIDERS);
                    helper.assertTrue(brought * 5 >= raiders && brought <= raiders, "a raid of " + raiders + " brought home only " + brought + " termites" + pile.describe(level));
                    int left = termites[0].population().inside(Caste.WORKER);
                    int many = termites[0].population().standsFor();
                    helper.assertTrue(termitesBefore[0] - left <= brought / many + 3, "the raid brought home " + brought
                            + " termites, but the termites' count lost " + (termitesBefore[0] - left) + " (one counted stands for " + many + ")");
                    helper.assertTrue(termites[0].keepsOff(level, spot), "the termites do not keep off the spot they were raided at");
                    SavannaMod.LOGGER.info("[away raid test] home at {}: {}; {}", now, report.describe(), raids.describe(level));
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    /**
     * M8 step 3d, the same scene near: a raid with more raiders than walk as real ants (the rest go along as numbers till
     * step 4) takes about {@code per_raider} termites for each of those numbers from the termite colony feeding at the spot
     * as the attack starts; the termites' count loses only one in its {@code stands_for} of them; the catch comes home onto
     * the prey pile with the column, and the termites keep off the spot.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_away")
    public static void aNearRaidsNumbersTakeTermitesFromTheColonyAndBringThemHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.deepGround(helper);
        TestClock.still(helper, 8.0); // (the morning raid hours)
        Colony dead = MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 200);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElseThrow();
        BlockPos queenAt = helper.absolutePos(new BlockPos(54, SURFACE, 54));
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), 54, SURFACE, 54);
        BlockPos spot = helper.absolutePos(new BlockPos(44, SURFACE, 44));
        Colony[] termites = new Colony[1];
        int[] termitesBefore = new int[1];
        com.antcolonies.savanna.colony.RaidColumn[] column = new com.antcolonies.savanna.colony.RaidColumn[1];
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    termites[0] = ColonyManager.get(level).getColonies().stream()
                            .filter(other -> SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(queenAt, 6))
                            .findFirst().orElse(null);
                    helper.assertTrue(termites[0] != null, "no termite colony founded by the queen");
                    termites[0].population().setOnForTests(true);
                    termites[0].population().addNewMembers(Caste.WORKER, level.getGameTime(), 3000);
                    termitesBefore[0] = termites[0].population().inside(Caste.WORKER);
                    BlockPos entrance = colony.getCenter();
                    java.util.List<BlockPos> trail = new java.util.ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(spot.getX() - entrance.getX(), spot.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((spot.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((spot.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    helper.assertTrue(raids.isHungry(), "the Matabele colony is not hungry with an empty pile");
                    column[0] = raids.raidForTests(level, spot, 12, trail, null);
                    helper.assertTrue(column[0] != null, "no raid set out; " + raids.describe(level));
                    SavannaMod.LOGGER.info("[near catch test] set out: {}", column[0].describe());
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage().ordinal() >= com.antcolonies.savanna.colony.RaidColumn.Stage.ATTACKING.ordinal(),
                        "the column did not reach the spot; " + column[0].describe()))
                .thenExecute(() -> {
                    int hidden = column[0].hiddenCatch();
                    helper.assertTrue(hidden >= 1, "the raiders going as numbers took only " + hidden + " termites; " + column[0].describe());
                    int many = termites[0].population().standsFor();
                    int lost = termitesBefore[0] - termites[0].population().inside(Caste.WORKER);
                    helper.assertTrue(lost <= hidden / many + 1, hidden + " termites taken, but the termites' count lost " + lost
                            + " (one counted stands for " + many + ")");
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.column() != column[0], "the raid is not home yet; " + column[0].describe()))
                .thenExecute(() -> {
                    int hidden = column[0].hiddenCatch();
                    helper.assertTrue(pile.broughtIn() >= hidden, hidden + " termites taken by the numbers, but " + pile.broughtIn()
                            + " laid on the pile" + pile.describe(level));
                    helper.assertTrue(termites[0].keepsOff(level, spot), "the termites do not keep off the spot they were raided at");
                    SavannaMod.LOGGER.info("[near catch test] home: {}; {}", column[0].describe(), pile.describe(level));
                })
                .thenSucceed();
    }
}
