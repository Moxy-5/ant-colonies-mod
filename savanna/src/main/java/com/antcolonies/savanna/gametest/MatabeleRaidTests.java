package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M8 step 3, the Matabele raids ({@code colony.Raids}). Step 3a, the scouts; step 3b, the column ({@code colony.RaidColumn}). */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleRaidTests {
    /** The nest's middle in this scene (away from the arena's edges, so every slice round it has ground). */
    private static final int NEST = 20;

    /**
     * A hungry colony in the raid hours (the test's still 7:00, the prey pile empty) sends a scout out by itself; it finds
     * termites feeding under a log 17 to 18 blocks out (there are two such logs, in opposite slices), never the termites'
     * harm, and comes home with a scent line from the nest to the find; the next scout rechecks the remembered spot first,
     * and it is the same ant (the colony's old scout goes first), named on the find. Step 3b: home with its find, the
     * scout leads a raid there (no scout attacks a termite before then); the recheck waits till the raid is home.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_scouts")
    public static void aHungryMatabeleColonySendsAScoutWhoFindsTermitesAndComesHomeWithAScentLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F); // (step 3b: a raid goes out between the find and the recheck)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        termites.addAll(feedingUnderALog(helper, 36, NEST + 4));
        termites.addAll(feedingUnderALog(helper, 2, NEST - 4));
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 10);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        long[] outAt = {-1};
        long[] foundAt = {-1};
        java.util.Set<java.util.UUID> scouted = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            colony.population().realMembers(level).stream().filter(ant -> raids.isScouting(ant.getUUID()))
                    .forEach(ant -> scouted.add(ant.getUUID()));
            if (outAt[0] < 0 && !scouted.isEmpty()) {
                outAt[0] = helper.getTick();
            }
            if (raids.column() == null && foundAt[0] < 0) {
                for (TermiteWorkerEntity termite : termites) {
                    helper.assertTrue(termite.isAlive() && !termite.isParalysed(), "a scout attacked a termite (scouts only look)");
                }
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(outAt[0] >= 0, "no scout out yet (hungry, 7:00)"))
                .thenWaitUntil(() -> helper.assertTrue(!raids.finds().isEmpty(), "no find yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    foundAt[0] = level.getGameTime();
                    Raids.Find find = raids.finds().get(0);
                    boolean byTermites = termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6));
                    helper.assertTrue(byTermites, "the find at " + find.at().toShortString() + " is not by the termites");
                    List<BlockPos> line = find.trail();
                    helper.assertTrue(line.size() >= 5, "a scent line of only " + line.size() + " points");
                    helper.assertTrue(line.get(0).closerThan(colony.getCenter(), 4), "the scent line does not start at the nest");
                    helper.assertTrue(line.get(line.size() - 1).closerThan(find.at(), 8), "the scent line does not reach the find");
                    for (int i = 1; i < line.size(); i++) {
                        helper.assertTrue(line.get(i).closerThan(line.get(i - 1), 5), "a gap in the scent line at point " + i);
                    }
                    helper.assertTrue(find.scout() != null && scouted.contains(find.scout()) && raids.isVeteran(find.scout()),
                            "the find does not name its scout as one of the colony's scouts");
                    RaidColumn column = raids.column();
                    helper.assertTrue(column != null && column.leader().equals(find.scout()),
                            "home with its find in the raid hours, the scout led no raid there; " + raids.describe(level));
                    SavannaMod.LOGGER.info("[scout test] out at {}, found at {}: {}", outAt[0], helper.getTick(), raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.finds().stream().anyMatch(find -> find.checkedAt() > foundAt[0]),
                        "the remembered spot was not rechecked; " + raids.describe(level)))
                .thenExecute(() -> helper.assertTrue(raids.range() == 32, "the search range grew to " + raids.range() + " though termites were found"))
                .thenExecute(() -> helper.assertTrue(scouted.size() == 1,
                        "the recheck went to a new ant, not the colony's old scout (" + scouted.size() + " scouts)"))
                .thenSucceed();
    }

    /**
     * Step 3b, the column: a hungry colony of 20 with a find (termites feeding under a log about 17 blocks out, the
     * scout's scent line to them put in by hand) sends a raid: 10 real ants (the least raid; 4 an ant for each of
     * the 2 termites smelt is 8; the hungry colony could spare 15), the find's scout in front. They mill round the entrance, then walk the scent line with the scout
     * leading, stop a few blocks short of the termites and bunch up, attack together and sting the termite workers
     * (food first: a termite soldier further off ranks below the workers), then walk home along the trail and go in;
     * nobody is left at the termites.
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true, batch = "matabele_raids")
    public static void aScoutLeadsARaidColumnAlongItsTrailThatStopsShortAttacksTheWorkersAndComesHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> termites = feedingUnderALog(helper, 36, NEST + 4);
        // A termite soldier far from everything, to ask the raiders how they rank it (food first).
        TermiteSoldierEntity soldier = TestAnts.lone(helper, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), 60, SURFACE, 60);
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 20);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        RaidColumn[] column = {null};
        List<MatabeleEntity> raiders = new ArrayList<>();
        int[] marchTicks = {0};
        int[] leaderBehind = {0};
        double[] mostOffTrail = {0.0};
        boolean[] rankChecked = {false};
        helper.onEachTick(() -> {
            RaidColumn raid = column[0];
            if (raid == null) {
                return;
            }
            if (raid.stage() == RaidColumn.Stage.MARCHING && level.getEntity(raid.leader()) instanceof MatabeleEntity leader) {
                marchTicks[0]++;
                double front = raid.alongOut(leader.blockPosition());
                boolean behind = false;
                for (MatabeleEntity ant : raiders) {
                    if (!ant.isAlive() || !raid.has(ant.getUUID())) {
                        continue;
                    }
                    double along = raid.alongOut(ant.blockPosition());
                    behind |= along > front + 3.0;
                    if (along > 4.0) {
                        mostOffTrail[0] = Math.max(mostOffTrail[0], raid.offTrail(ant.blockPosition()));
                    }
                }
                if (behind) {
                    leaderBehind[0]++;
                }
            }
            if (!rankChecked[0] && raid.stage() == RaidColumn.Stage.MARCHING && !raiders.isEmpty()) {
                MatabeleEntity raider = raiders.get(raiders.size() - 1);
                helper.assertTrue(raider.isInAColumn(), "a raider on the march is not in a column");
                helper.assertTrue(raider.foeRank(soldier) > raider.foeRank(termites.get(0)),
                        "a raider ranks a termite soldier further off no lower than the workers (food first)");
                rankChecked[0] = true;
            }
        });
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockPos entrance = colony.getCenter();
                    MatabeleEntity scout = colony.population().realMembers(level).stream()
                            .filter(ant -> ant instanceof MatabeleEntity matabele && matabele.hasMajorBody() && !raids.isScouting(ant.getUUID()))
                            .map(ant -> (MatabeleEntity) ant)
                            .findFirst().orElse(null);
                    helper.assertTrue(scout != null, "no real major to be the scout");
                    BlockPos at = termites.get(0).blockPosition();
                    List<BlockPos> trail = new ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(at.getX() - entrance.getX(), at.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((at.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((at.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    RaidColumn raid = raids.raidForTests(level, at, 2, trail, scout.getUUID());
                    helper.assertTrue(raid != null, "no raid set out; " + raids.describe(level));
                    helper.assertTrue(raid.leader().equals(scout.getUUID()), "the raid is not led by the find's scout");
                    helper.assertTrue(raid.members().size() == 10, "a raid of " + raid.members().size() + " real ants, not 10 (worth 2 at 4 an ant is 8, at least 10)");
                    helper.assertTrue(flat(raid.gatheringPlace(), at) >= 4.0, "the gathering place is not short of the termites");
                    for (java.util.UUID id : raid.members()) {
                        if (level.getEntity(id) instanceof MatabeleEntity ant) {
                            raiders.add(ant);
                        }
                    }
                    column[0] = raid;
                    SavannaMod.LOGGER.info("[raid test] set out: {}", raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage().ordinal() >= RaidColumn.Stage.ATTACKING.ordinal(),
                        "the column did not reach the termites; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(raid.attackedAt() >= 0, "no attack");
                    helper.assertTrue(raid.gatheredAtAttack() * 2 >= raid.members().size(),
                            "only " + raid.gatheredAtAttack() + " of " + raid.members().size() + " bunched up before the attack");
                    helper.assertTrue(marchTicks[0] > 0 && leaderBehind[0] * 5 <= marchTicks[0],
                            "on the march others ran ahead of the scout " + leaderBehind[0] + " ticks of " + marchTicks[0]);
                    helper.assertTrue(mostOffTrail[0] <= 5.0, "a raider strayed " + mostOffTrail[0] + " blocks off the trail");
                    helper.assertTrue(rankChecked[0], "food first not checked on the march");
                    SavannaMod.LOGGER.info("[raid test] attack at {}: {}", helper.getTick(), raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(termites.stream().anyMatch(termite -> !termite.isAlive() || termite.isParalysed()),
                        "no termite worker stung; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid is not home yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(raid.cameHome() >= 1, "nobody came home with the column: " + raid.endedWhy());
                    BlockPos at = raid.find().at();
                    for (MatabeleEntity ant : raiders) {
                        if (ant.isAlive() && !ant.isCrippled() && ant.getFightSpot() == null) {
                            helper.assertTrue(!ant.blockPosition().closerThan(at, 8), "a raider stayed at the termites, at " + ant.blockPosition().toShortString()
                                    + " fights no more " + ant.fightsNoMore() + " downed " + ant.isDowned() + " health " + ant.getHealth() + " doing "
                                    + ant.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                                    .map(g -> g.getGoal().getClass().getSimpleName()).toList() + "; the raid " + raid.endedWhy());
                        }
                    }
                    SavannaMod.LOGGER.info("[raid test] home at {}, {} came home ({})", helper.getTick(), raid.cameHome(), raid.endedWhy());
                })
                .thenSucceed();
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Two termite workers feeding in a gallery under a 4-block log (along x from {@code x}) at row {@code z}. */
    private static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z) {
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            helper.setBlock(new BlockPos(x + i, SURFACE, z), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            helper.setBlock(x + i, SURFACE - 1, z, Blocks.AIR);
        }
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 1, SURFACE - 1, z));
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 2, SURFACE - 1, z));
        return termites;
    }
}
