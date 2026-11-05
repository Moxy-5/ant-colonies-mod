package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleLoads;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M8 step 3, the Matabele raids ({@code colony.Raids}). Step 3a, the scouts; step 3b, the column ({@code colony.RaidColumn});
 * step 3c, home with the prey (the stung termites carried home onto the prey pile, the empty raid spreading out, limpers).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleRaidTests {
    /** The nest's middle in this scene (away from the arena's edges, so every slice round it has ground). */
    private static final int NEST = 20;

    /**
     * A hungry colony (the prey pile empty) sends a scout out by itself, at 13:00 too, between the raid hours (scouts
     * search all day, the user's pick 2026-11-03); it finds
     * termites feeding under a log 17 to 18 blocks out (there are two such logs, in opposite slices), never the termites'
     * harm, and comes home with a scent line from the nest to the find; the next scout rechecks the remembered spot first,
     * and it is the same ant (the colony's old scout goes first), named on the find. Step 3b: home with its find, the
     * scout leads a raid there (no scout attacks a termite before then): found at 13:00, the find waits (no raid, no new
     * scout) till the evening raid hours, 16:00, and the raid leaves then; the recheck waits till the raid is home.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_scouts")
    public static void aHungryMatabeleColonySendsAScoutWhoFindsTermitesAndComesHomeWithAScentLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F); // (step 3b: a raid goes out between the find and the recheck)
        TestClock clock = TestClock.still(helper, 13.0); // (between the raid hours: the scouts search, no raid leaves)
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
        scoutsHeadToward(helper, colony, 37, NEST + 4);
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
                .thenWaitUntil(() -> helper.assertTrue(outAt[0] >= 0, "no scout out yet (hungry, 13:00: scouts search all day)"))
                .thenWaitUntil(() -> helper.assertTrue(!raids.finds().isEmpty(), "no find yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    foundAt[0] = level.getGameTime();
                    Raids.Find find = raids.finds().get(0);
                    boolean byTermites = termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6));
                    helper.assertTrue(byTermites, "the find at " + find.at().toShortString() + " is not by the termites");
                    // (raid rework R5: the scout counts every termite feeding there, both under the log, worth 3 each)
                    helper.assertTrue(find.worth() >= 6, "the scout scored the two termites under the log as worth only " + find.worth());
                    List<BlockPos> line = find.trail();
                    helper.assertTrue(line.size() >= 5, "a scent line of only " + line.size() + " points");
                    helper.assertTrue(line.get(0).closerThan(colony.getCenter(), 4), "the scent line does not start at the nest");
                    helper.assertTrue(line.get(line.size() - 1).closerThan(find.at(), 8), "the scent line does not reach the find");
                    for (int i = 1; i < line.size(); i++) {
                        helper.assertTrue(line.get(i).closerThan(line.get(i - 1), 5), "a gap in the scent line at point " + i);
                    }
                    helper.assertTrue(find.scout() != null && scouted.contains(find.scout()) && raids.isVeteran(find.scout()),
                            "the find does not name its scout as one of the colony's scouts");
                    helper.assertTrue(raids.column() == null, "a raid left at 13:00, between the raid hours; " + raids.describe(level));
                    helper.assertTrue(raids.waiting() == find, "the 13:00 find is not waiting for the raid hours; " + raids.describe(level));
                    SavannaMod.LOGGER.info("[scout test] out at {}, found at {}: {}", outAt[0], helper.getTick(), raids.describe(level));
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertTrue(raids.column() == null && scouted.size() == 1,
                            "with a find waiting, a raid or a new scout went out before the raid hours (" + scouted.size() + " scouts)");
                    clock.skipTo(16.25); // the evening raid hours
                })
                .thenWaitUntil(() -> {
                    Raids.Find find = raids.finds().get(0);
                    RaidColumn column = raids.column();
                    helper.assertTrue(column != null && column.leader().equals(find.scout()) && column.find() == find,
                            "in the raid hours the waiting find's scout led no raid there; " + raids.describe(level));
                    helper.assertTrue(raids.waiting() == null, "the find still waits with its raid out");
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.finds().stream().anyMatch(find -> find.checkedAt() > foundAt[0]),
                        "the remembered spot was not rechecked; " + raids.describe(level)))
                .thenExecute(() -> {
                    // (scouts search like real ants: after its find 10 blocks out the scout's reach is back to the first range)
                    MatabeleEntity scout = scoutNamed(level, colony, raids.finds().get(0).scout());
                    helper.assertTrue(scout != null && scout.scoutMemory().reach(32) == 32 && raids.range() == 32,
                            "the scout's reach is " + (scout == null ? "?" : scout.scoutMemory().reach(32)) + ", the colony's " + raids.range()
                                    + ", though termites were found 10 blocks out");
                })
                .thenExecute(() -> helper.assertTrue(scouted.size() == 1,
                        "the recheck went to a new ant, not the colony's old scout (" + scouted.size() + " scouts)"))
                .thenSucceed();
    }

    /**
     * Step 3b, the column: a hungry colony of 20 with a find (termites feeding under a log about 17 blocks out, the
     * scout's scent line to them put in by hand) sends a raid: 10 real ants (the least raid; 4 an ant for each of
     * the 2 termites smelt is 8; the hungry colony could spare 15), the find's scout in front. They mill round the entrance, then walk the scent line with the scout
     * leading, stop a few blocks short of the termites and bunch up, attack together and sting the termite workers
     * (food first: for a minor a termite soldier further off ranks below the workers; M8 step 3S: a major ranks it with
     * them), then walk home along the trail and go in;
     * nobody is left at the termites. Step 3c: they pick the stung termites up and carry them home (no more than each
     * can hold), and every termite carried home goes onto the colony's prey pile. Raid rework R4: a block put on the
     * trail half way out breaks it; the column stops there, casts about, picks the trail up past the gap and lays new
     * scent over it (the trail whole again), and the trail is a column's trail once walked. The raid code review,
     * findings 1 and 3: a hungry recruit stays in the column (it never leaves to eat), and a raider out of sight on the
     * march for 10 s, less than the grace (its land not ticking) is kept in the column, and walks on with it once back.
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
        BlockPos[] blocked = {null};
        MatabeleEntity[] hungry = {null};
        MatabeleEntity[] hidden = {null};
        long[] hiddenAt = {-1};
        boolean[] hiddenKept = {false};
        helper.onEachTick(() -> {
            RaidColumn raid = column[0];
            if (raid == null) {
                return;
            }
            // Finding 3: the hungry recruit never leaves the column to eat.
            if (hungry[0] != null && raid.has(hungry[0].getUUID()) && hungry[0].runsGoal(com.antcolonies.entity.goal.EatGoal.class)) {
                helper.fail("a hungry raider in the column went to eat: " + hungry[0].runningGoals());
            }
            // Finding 1: one raider out of sight 10 s on the march (less than the minute's grace): kept, then back.
            if (hidden[0] == null && raid.stage() == RaidColumn.Stage.MARCHING && level.getEntity(raid.leader()) instanceof MatabeleEntity front
                    && raid.alongOut(front.blockPosition()) > 6.0) {
                for (java.util.UUID id : raid.members()) {
                    if (!id.equals(raid.leader()) && (hungry[0] == null || !id.equals(hungry[0].getUUID()))
                            && level.getEntity(id) instanceof MatabeleEntity ant) {
                        hidden[0] = ant;
                        hiddenAt[0] = helper.getTick();
                        raid.hideForTests(id);
                        break;
                    }
                }
            }
            if (hidden[0] != null && hiddenAt[0] >= 0 && helper.getTick() - hiddenAt[0] >= 200) {
                hiddenKept[0] = raid.has(hidden[0].getUUID()) && raid.isUnseen(hidden[0].getUUID());
                raid.showForTests(hidden[0].getUUID());
                hiddenAt[0] = -1;
                if (!hiddenKept[0]) {
                    helper.fail("a raider out of sight 10 s (less than the grace) was dropped from the column; " + raid.describe());
                }
            }
            if (raid.stage() == RaidColumn.Stage.MARCHING && level.getEntity(raid.leader()) instanceof MatabeleEntity leader
                    && !raid.isCasting(raid.leader())) { // (R4: casting about at the gap, the front's helpers range ahead of it)
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
                // Food first for the minors; M8 step 3S: a major ranks the soldiers with the workers (Corbara and Dejean 2000).
                for (MatabeleEntity raider : raiders) {
                    if (!raider.isAlive() || !raider.isInAColumn()) {
                        continue;
                    }
                    if (raider.hasMajorBody()) {
                        helper.assertTrue(raider.foeRank(soldier) == raider.foeRank(termites.get(0)),
                                "a major raider ranks a termite soldier below the workers (it should take on the soldiers too)");
                    } else {
                        helper.assertTrue(raider.foeRank(soldier) > raider.foeRank(termites.get(0)),
                                "a minor raider ranks a termite soldier further off no lower than the workers (food first)");
                    }
                    rankChecked[0] = true;
                }
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
                    // Finding 3: a hungry recruit (it last ate a meal interval ago) goes along all the same.
                    hungry[0] = raiders.get(raiders.size() - 1);
                    hungry[0].hungryForTests();
                    helper.assertTrue(hungry[0].isHungry(), "the recruit made hungry is not hungry");
                    // R4: a block put on the trail half way out (no event in a test: the trail's own check finds it).
                    List<BlockPos> points = raid.trail().points();
                    blocked[0] = points.get(points.size() / 2);
                    level.setBlockAndUpdate(blocked[0], Blocks.STONE.defaultBlockState());
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
                    helper.assertTrue(!hungry[0].isAlive() || hungry[0].fightsNoMore() || raid.has(hungry[0].getUUID()),
                            "the hungry recruit left the column before the attack"
                                    + (raids.whyGoingHome(hungry[0].getUUID()) == null ? "" : " (" + raids.whyGoingHome(hungry[0].getUUID()) + ")"));
                    SavannaMod.LOGGER.info("[raid test] attack at {}: {}", helper.getTick(), raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(termites.stream().anyMatch(termite -> !termite.isAlive() || termite.isParalysed()),
                        "no termite worker stung; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid is not home yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(raid.cameHome() >= 1, "nobody came home with the column: " + raid.endedWhy());
                    helper.assertTrue(hiddenKept[0], "no raider was out of sight on the march and kept in the column");
                    // R4: the gap was met and crossed, the trail mended and walked by the column.
                    helper.assertTrue(raid.gapsCrossed() >= 1, "the column never crossed the gap in its trail at " + blocked[0].toShortString()
                            + "; " + raid.endedWhy());
                    helper.assertTrue(raid.trail().brokenCount() == 0, raid.trail().brokenCount() + " trail point(s) still broken after the column crossed");
                    helper.assertTrue(raid.trail().byColumn() && raid.trail().walks() >= 1, "the trail is not a column's trail after the raid");
                    helper.assertTrue(!raid.lostTrail(), "the column turned home at a short gap");
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
                    // Step 3c: the stung termites came home in the raiders' jaws.
                    helper.assertTrue(raid.carried() >= 1, "the raid came home with no termites; " + raids.describe(level));
                    long taken = termites.stream().filter(termite -> !termite.isAlive()).count();
                    helper.assertTrue(taken >= raid.carried(), raid.carried() + " termites carried but only " + taken + " taken off the ground");
                    for (MatabeleEntity ant : raiders) {
                        helper.assertTrue(MatabeleLoads.termitesHeldBy(ant) <= ant.termitesItCanCarry(),
                                "a raider holds " + MatabeleLoads.termitesHeldBy(ant) + " termites, more than its " + ant.termitesItCanCarry());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(raiders.stream().noneMatch(ant -> ant.isAlive() && ant.isCarrying(MatabeleLoads.TERMITES)),
                        "a raider still holds its termites; " + colony.part(SavannaParts.PREY_PILE).map(pile -> pile.describe(level)).orElse("no pile")))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
                    helper.assertTrue(pile != null, "a Matabele colony has no prey pile");
                    boolean anyDied = raiders.stream().anyMatch(ant -> !ant.isAlive());
                    helper.assertTrue(anyDied ? pile.broughtIn() <= raid.carried() && pile.broughtIn() >= 1 : pile.broughtIn() == raid.carried(),
                            raid.carried() + " termites carried home but " + pile.broughtIn() + " laid on the pile; " + pile.describe(level));
                    SavannaMod.LOGGER.info("[raid test] on the pile: {}", pile.describe(level));
                })
                .thenSucceed();
    }

    /**
     * Step 3c, the empty raid: a raid to a find where no termite is any more (the log is there, the termites gone) bunches
     * up and attacks nothing, spreads round the spot a while looking, then comes home empty; the find counts one empty
     * check. A raider crippled on the march (3 legs gone) leaves the column and limps home along the trail by itself;
     * M8 step 3S: so does one badly wounded (below 40 percent of its health, every leg kept).
     * The raid code review, finding 4: a survival player stands on the entrance while the raid forms: raiders milling
     * there are called away to defend the nest, stay in the column and rejoin it once the player is gone, and at the
     * spot the raid's own fight orders stand (the nest's defence never ends them).
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true, batch = "matabele_raids")
    public static void aRaidThatFindsNoTermitesSpreadsOutThenComesHomeEmptyAndALimperFollowsTheTrailHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> gone = feedingUnderALog(helper, 36, NEST + 4);
        BlockPos at = gone.get(0).blockPosition();
        gone.forEach(termite -> termite.discard()); // (the termites have left the log)
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 20);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        RaidColumn[] column = {null};
        Raids.Find[] find = {null};
        MatabeleEntity[] limper = {null};
        MatabeleEntity[] wounded = {null};
        boolean[] spread = {false};
        int[] columnOrders = {0};
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        MatabeleDefenceTests.stand(player, colony.getCenter().offset(0, 0, 40));
        helper.onEachTick(() -> {
            if (column[0] != null && column[0].stage() == RaidColumn.Stage.SPREADING) {
                spread[0] = true;
            }
            RaidColumn raid = column[0];
            if (raid != null && raid.stage() == RaidColumn.Stage.ATTACKING && raid.attackedAt() >= 0 && level.getGameTime() - raid.attackedAt() >= 40) {
                int holding = 0;
                for (java.util.UUID id : raid.members()) {
                    if (level.getEntity(id) instanceof MatabeleEntity ant && raid.fightsForTheColumn(ant)) {
                        holding++;
                    }
                }
                columnOrders[0] = Math.max(columnOrders[0], holding);
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
                    column[0] = raid;
                    find[0] = raid.find();
                    MatabeleDefenceTests.stand(player, MatabeleDefenceTests.beside(level, colony.getCenter())); // (on the entrance as it forms)
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].members().stream().anyMatch(column[0]::isDrafted),
                        "no raider milling at the entrance was called to defend the nest; " + raids.describe(level) + column[0].describe()))
                .thenExecute(() -> {
                    helper.assertTrue(column[0].stage() == RaidColumn.Stage.FORMING || column[0].stage() == RaidColumn.Stage.MARCHING,
                            "the raid was at " + column[0].stage() + " when its raiders were called to defend");
                    MatabeleDefenceTests.stand(player, colony.getCenter().offset(0, 0, 40)); // (gone: the defence ends)
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].members().stream().noneMatch(column[0]::isDrafted),
                        "a raider called to defend did not come back to the column; " + column[0].describe()))
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage() == RaidColumn.Stage.MARCHING
                        && level.getEntity(column[0].leader()) instanceof MatabeleEntity leader && column[0].alongOut(leader.blockPosition()) > 6.0,
                        "the column is not well out on the march yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    for (java.util.UUID id : raid.members()) {
                        if (!id.equals(raid.leader()) && level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive()) {
                            if (limper[0] == null) {
                                limper[0] = ant;
                            } else {
                                wounded[0] = ant;
                                break;
                            }
                        }
                    }
                    helper.assertTrue(limper[0] != null && wounded[0] != null, "no two raiders to cripple and to wound");
                    limper[0].setLostLegsForTests(0b000111, 0);
                    wounded[0].setHealth(wounded[0].getMaxHealth() * 0.3F);
                    helper.assertTrue(wounded[0].badlyWounded() && wounded[0].fightsNoMore(), "a raider at 30 percent health should fight no more");
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.isLimping(limper[0].getUUID()),
                        "the crippled raider was not sent limping home; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(!wounded[0].isAlive() || raids.isLimping(wounded[0].getUUID())
                        || !column[0].members().contains(wounded[0].getUUID()),
                        "the badly wounded raider was not sent home; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid is not home yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(spread[0] && raid.spread(), "the empty raid did not spread out round the spot");
                    helper.assertTrue(raid.carried() == 0, "the empty raid carried " + raid.carried() + " termites home");
                    helper.assertTrue(raid.cameHome() >= 1, "nobody came home with the column: " + raid.endedWhy());
                    helper.assertTrue(find[0].misses() >= 1 || !raids.finds().contains(find[0]),
                            "the empty raid did not count as an empty check of the spot; " + raids.describe(level));
                    helper.assertTrue(raid.foundNothing() && !raid.lostFromSight(), "the empty raid did not count as found nothing");
                    helper.assertTrue(columnOrders[0] >= 1, "at the spot no raider held the raid's own fight order (the nest's defence ended them?)");
                    SavannaMod.LOGGER.info("[empty raid test] home at {}: {}", helper.getTick(), raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(!limper[0].isAlive() || !raids.isLimping(limper[0].getUUID()),
                        "the limper is still on its way home, at " + limper[0].blockPosition().toShortString()))
                .thenWaitUntil(() -> helper.assertTrue(!wounded[0].isAlive() || !raids.isLimping(wounded[0].getUUID()),
                        "the wounded raider is still on its way home, at " + wounded[0].blockPosition().toShortString()))
                .thenExecute(() -> {
                    if (limper[0].isAlive()) {
                        BlockPos entrance = colony.getCenter();
                        helper.assertTrue(flat(limper[0].blockPosition(), entrance) <= 9.0 || colony.nest().isInside(level, limper[0].blockPosition()),
                                "the limper gave up short of home, at " + limper[0].blockPosition().toShortString());
                    }
                    if (wounded[0].isAlive()) {
                        BlockPos entrance = colony.getCenter();
                        helper.assertTrue(flat(wounded[0].blockPosition(), entrance) <= 9.0 || colony.nest().isInside(level, wounded[0].blockPosition()),
                                "the wounded raider gave up short of home, at " + wounded[0].blockPosition().toShortString());
                    }
                })
                .thenSucceed();
    }

    /**
     * Raid rework R2, the whole chain in one go, as in the user's world (BUGS 87): a big hungry colony in a dead
     * termite mound (120 workers, 50 of them real, every one starting inside down the chimney), the scout command's
     * scout finding termites feeding under three logs about 17 blocks out by itself, coming home with its own scent line
     * (none put in by hand), and the raid it leads: at least the least raid of 10 real raiders (4 an ant for each termite smelt; raid rework R5: the scout counts the termites under all three logs, 5 or 6 of the
     * 6, where it once scored them 3 to 6) come up out of the chimney, march, attack,
     * carry the stung termites home and go in. Every stage must end within its time (no stage may hang: the march of
     * about 17 blocks within 60 s), and at the end no raider stands about outside.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raids")
    public static void aBigColonyInADeadMoundRaidsFromTheScoutsOwnFindToThePileWithNoStageHanging(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.moving(helper, 7.0); // (the morning raid hours, a real moving clock)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        termites.addAll(feedingUnderALog(helper, 36, NEST + 3));
        termites.addAll(feedingUnderALog(helper, 36, NEST + 5));
        termites.addAll(feedingUnderALog(helper, 36, NEST + 7));
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 120);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        scoutsHeadToward(helper, colony, 37, NEST + 5);
        RaidColumn[] column = {null};
        List<MatabeleEntity> raiders = new ArrayList<>();
        java.util.Map<RaidColumn.Stage, Long> stageAt = new java.util.EnumMap<>(RaidColumn.Stage.class);
        boolean[] hung = {false};
        // The raid code review, finding 18: a carrier killed on the way home drops its stung termites where it died.
        BlockPos[] diedAt = {null};
        int[] diedHolding = {0};
        int[] dropped = {-1};
        helper.onEachTick(() -> {
            RaidColumn raid = column[0] != null ? column[0] : raids.column();
            if (raid == null) {
                return;
            }
            if (column[0] == null) {
                column[0] = raid;
                for (java.util.UUID id : raid.members()) {
                    if (level.getEntity(id) instanceof MatabeleEntity ant) {
                        raiders.add(ant);
                    }
                }
            }
            long now = helper.getTick();
            RaidColumn.Stage stage = raid.stage();
            stageAt.putIfAbsent(stage, now);
            long since = now - stageAt.get(stage);
            // (the raid code review, finding 18: each stage's longest time from the species file's own numbers, a little
            // over for the column's update every few ticks; no number of its own here)
            SavannaNumbers.ColumnNumbers numbers = raid.columnNumbers();
            SavannaNumbers.CarryNumbers carry = raid.carryNumbers();
            long most = SLACK + switch (stage) {
                case FORMING -> numbers.formTicks();
                case MARCHING -> raid.marchLimit();
                case GATHERING -> numbers.gatherTicks();
                case ATTACKING -> numbers.fightTicks() + 200; // (the fight's own hard end)
                case COLLECTING -> carry.collectTicks();
                case SPREADING -> carry.spreadTicks() + 200;
                case REGROUPING -> numbers.regroupTicks();
                case HOMING -> numbers.homeTicks();
                case DONE -> Long.MAX_VALUE - SLACK;
            };
            if (stage == RaidColumn.Stage.HOMING && diedAt[0] == null) {
                for (java.util.UUID id : raid.members()) {
                    if (level.getEntity(id) instanceof MatabeleEntity ant && MatabeleLoads.termitesHeldBy(ant) > 0
                            && flat(ant.blockPosition(), raid.find().at()) >= 10.0) {
                        diedAt[0] = ant.blockPosition();
                        diedHolding[0] = MatabeleLoads.termitesHeldBy(ant);
                        ant.kill();
                        break;
                    }
                }
            } else if (diedAt[0] != null && dropped[0] < 0) {
                dropped[0] = level.getEntitiesOfClass(TermiteWorkerEntity.class, new net.minecraft.world.phys.AABB(diedAt[0]).inflate(2.5),
                        termite -> termite.isParalysed() && termite.getColonyId() == null).size();
            }
            if (since > most && !hung[0]) {
                hung[0] = true;
                helper.fail("the raid hung in " + stage.name().toLowerCase() + " for " + since / 20 + " s (most " + most / 20 + " s); "
                        + raids.describe(level) + raid.describe());
            }
        });
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(raids.sendNow(level) != null, "the scout command found no fit major"))
                .thenWaitUntil(() -> helper.assertTrue(column[0] != null, "no raid yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    com.antcolonies.savanna.colony.RaidCost.reset(); // (the raid review, finding 9: the raid's own cost, out and home)
                    RaidColumn raid = column[0];
                    Raids.Find find = raid.find();
                    helper.assertTrue(termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6)),
                            "the scout's find at " + find.at().toShortString() + " is not by the termites");
                    helper.assertTrue(find.worth() >= 15, "the scout scored the six termites under the three logs as worth only " + find.worth());
                    helper.assertTrue(raid.members().size() >= 10, "a raid of only " + raid.members().size() + " real ants from a colony of 120 (50 real); "
                            + raids.describe(level) + raid.describe());
                    helper.assertTrue(raid.leader().equals(find.scout()), "the raid is not led by the scout who found the spot");
                    SavannaMod.LOGGER.info("[raid chain test] set out at {}: {}{}", helper.getTick(), raids.describe(level), raid.describe());
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage().ordinal() >= RaidColumn.Stage.ATTACKING.ordinal(),
                        "the column did not reach the termites; " + raids.describe(level) + column[0].describe()))
                .thenWaitUntil(() -> helper.assertTrue(termites.stream().anyMatch(termite -> !termite.isAlive() || termite.isParalysed()),
                        "no termite stung; " + raids.describe(level) + column[0].describe()))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() != column[0], "the raid is not home yet; " + column[0].describe()))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    SavannaMod.LOGGER.info("[raid chain test] home at {}, stages {}, {} came home ({}), {} carried", helper.getTick(), stageAt,
                            raid.cameHome(), raid.endedWhy(), raid.carried());
                    SavannaMod.LOGGER.info("[raid chain test] the raid's cost: {}", com.antcolonies.savanna.colony.RaidCost.report());
                    helper.assertTrue(raid.carried() >= 1, "the raid came home with no termites: " + raid.endedWhy());
                    helper.assertTrue(diedAt[0] != null, "no carrier was out on the way home to kill");
                    helper.assertTrue(dropped[0] >= diedHolding[0], "a carrier killed on the way home held " + diedHolding[0]
                            + " stung termite(s), but " + Math.max(0, dropped[0]) + " lie where it died at " + diedAt[0].toShortString());
                    helper.assertTrue(raid.cameHome() * 2 >= raid.members().size(),
                            "only " + raid.cameHome() + " of " + raid.members().size() + " came home with the column: " + raid.endedWhy());
                    // The raid and fight review 2, finding 2 (the user's raid): every milling place out on the open ground,
                    // and nobody left home while standing at the place it was given.
                    helper.assertTrue(raid.formFaults() == 0, raid.formFaults() + " milling place(s) in the nest or under the entrance,"
                            + " or ants left home standing at theirs: " + raid.endedWhy());
                    // Finding 3: the attack ends once no termite the raiders would go for is about, not at its longest time
                    // (the termites hidden in their galleries or shut in under the logs once no major still breaks in).
                    Long attackAt = stageAt.get(RaidColumn.Stage.ATTACKING);
                    Long afterAt = stageAt.get(RaidColumn.Stage.COLLECTING);
                    helper.assertTrue(attackAt != null && afterAt != null && afterAt - attackAt < raid.columnNumbers().fightTicks(),
                            "the attack ran to its longest time (" + (attackAt == null || afterAt == null ? "?" : (afterAt - attackAt) / 20)
                                    + " s): hidden termites kept it going; stages " + stageAt);
                })
                .thenIdle(400) // (a little while to go in)
                .thenExecute(() -> {
                    for (MatabeleEntity ant : raiders) {
                        if (ant.isAlive() && !ant.isCrippled() && !ant.isDowned() && ant.getFightSpot() == null && !raids.isRaider(ant.getUUID())
                                && !raids.isScouting(ant.getUUID())) {
                            helper.assertTrue(flat(ant.blockPosition(), colony.getCenter()) <= 8.0 || colony.nest().isInside(level, ant.blockPosition()),
                                    "a raider stands about outside at " + ant.blockPosition().toShortString() + ", "
                                            + Math.round(flat(ant.blockPosition(), colony.getCenter())) + " from the nest, doing " + ant.runningGoals());
                        }
                    }
                })
                .thenSucceed();
    }

    /**
     * BUGS 101 (the user's raid of 2026-11-05): from a tall dead mound (dead only a few days, the hump still high round
     * its open chimney), a raid of about 24 forms. Real Megaponera: the scout goes in and recruits, the raiders pour out
     * after it and the column leaves almost at once, the late ones following the trail. Here: the scout goes into the nest
     * first; every recruit that came out of the nest counts as out, wherever round the hump it stands (none is left home
     * standing at the place it was given, and none out of the nest is left home at all); the column sets out before the
     * forming's longest time; and a recruit at its place stands there, never walking off and back again and again.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "matabele_raids")
    public static void aRaidFromATallDeadMoundSetsOutPromptlyWithEveryRecruitThatCameOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.moving(helper, 7.0);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 6, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 120);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        RaidColumn[] column = {null};
        long[] formedAt = {-1};
        long[] startedAt = {-1};
        long[] milled = {-1};
        int[] members = {0};
        boolean[] scoutWentIn = {false};
        // Each recruit's walks after it first stood at its place (BUGS 101: back and forth at the top of the hump).
        java.util.Map<java.util.UUID, Integer> rewalks = new java.util.HashMap<>();
        java.util.Set<java.util.UUID> settled = new java.util.HashSet<>();
        java.util.Map<java.util.UUID, BlockPos> walkingTo = new java.util.HashMap<>();
        helper.onEachTick(() -> {
            RaidColumn raid = column[0];
            if (raid == null || formedAt[0] >= 0) {
                return;
            }
            if (raid.stage() != RaidColumn.Stage.FORMING) {
                formedAt[0] = helper.getTick();
                milled[0] = raid.recruitedAt() < 0 ? -1 : level.getGameTime() - raid.recruitedAt();
                return;
            }
            for (java.util.UUID id : raid.members()) {
                if (!(level.getEntity(id) instanceof MatabeleEntity ant)) {
                    continue;
                }
                if (id.equals(raid.leader()) && colony.nest().isInside(level, ant.blockPosition())) {
                    scoutWentIn[0] = true;
                }
                RaidColumn.Order order = raid.orderFor(level, ant);
                if (order == null || order.to() == null || id.equals(raid.leader())) {
                    continue;
                }
                boolean at = flat(ant.blockPosition(), order.to()) <= 1.5 && Math.abs(ant.getY() - order.to().getY()) <= 1.5;
                boolean walking = !ant.getNavigator().isDone();
                if (at && !walking) {
                    settled.add(id);
                    walkingTo.remove(id);
                } else if (settled.contains(id) && walking && !order.to().equals(walkingTo.get(id))) {
                    walkingTo.put(id, order.to());
                    rewalks.merge(id, 1, Integer::sum);
                }
            }
        });
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockPos entrance = colony.getCenter();
                    MatabeleEntity scout = colony.population().realMembers(level).stream()
                            .filter(ant -> ant instanceof MatabeleEntity matabele && matabele.hasMajorBody() && !raids.isScouting(ant.getUUID())
                                    && !colony.nest().isInside(level, ant.blockPosition()))
                            .map(ant -> (MatabeleEntity) ant)
                            .findFirst().orElse(null);
                    helper.assertTrue(scout != null, "no real major outside to be the scout");
                    BlockPos by = com.antcolonies.savanna.colony.RaidGround.standAt(level, entrance.getX() + 2.5, entrance.getZ() + 0.5, entrance.getY());
                    helper.assertTrue(by != null, "no ground to stand on by the entrance");
                    scout.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5); // (home from its find, by the entrance)
                    BlockPos at = helper.absolutePos(new BlockPos(NEST + 24, SURFACE, NEST));
                    List<BlockPos> trail = new ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(at.getX() - entrance.getX(), at.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((at.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((at.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    RaidColumn raid = raids.raidForTests(level, at, 6, trail, scout.getUUID());
                    helper.assertTrue(raid != null, "no raid set out; " + raids.describe(level));
                    column[0] = raid;
                    startedAt[0] = helper.getTick();
                    members[0] = raid.members().size();
                    StringBuilder ground = new StringBuilder();
                    for (int dx = -6; dx <= 6; dx++) {
                        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                entrance.offset(dx, 0, 0));
                        ground.append(top.getY() - entrance.getY()).append(' ');
                    }
                    SavannaMod.LOGGER.info("[raid form test] {} raiders, entrance {}, the ground west to east from it: {}", members[0],
                            entrance.toShortString(), ground);
                })
                .thenWaitUntil(() -> helper.assertTrue(formedAt[0] >= 0, "still forming; " + column[0].describe()))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    long took = formedAt[0] - startedAt[0];
                    int rewalked = rewalks.values().stream().mapToInt(Integer::intValue).sum();
                    SavannaMod.LOGGER.info("[raid form test] formed in {} s ({} s milling after the scout recruited), {} of {} set out, {} walk(s) off a place reached, the scout went in: {}; {}",
                            took / 20, milled[0] / 20, raid.members().size(), members[0], rewalked, scoutWentIn[0], raid.endedWhy());
                    helper.assertTrue(scoutWentIn[0], "the scout never went into the nest to recruit");
                    helper.assertTrue(raid.formFaults() == 0, raid.formFaults() + " recruit(s) out of the nest left home: " + raid.describe());
                    helper.assertTrue(milled[0] >= 0 && milled[0] < raid.columnNumbers().formTicks(), "the column milled the forming's longest time, "
                            + milled[0] / 20 + " s after the scout recruited: " + raid.describe());
                    helper.assertTrue(raid.members().size() * 10 >= members[0] * 9, "only " + raid.members().size() + " of " + members[0]
                            + " recruits set out");
                    helper.assertTrue(rewalked <= Math.max(2, members[0] / 6), rewalked + " walks off a place already reached while forming "
                            + rewalks + " (back and forth)");
                })
                .thenSucceed();
    }

    /**
     * The raid code review, finding 18: a trail over a hill and round a bend, all of it a real scout's own. Between the
     * nest (a dead mound) and termites feeding under three logs about 20 blocks east lie a pond (a strip of water 3 wide
     * and 12 long, across the straight way) and a ridge 3 blocks high. The scout command's scout searches by itself,
     * finds the termites past the ridge and runs home, round the pond's end and over the ridge, laying its scent line:
     * the line climbs the ridge (2 or more blocks over the plain), bends round the pond (3 or more blocks off the
     * straight way), and never lies in the water. The raid it leads walks that line out (hopping along it, not straight
     * across the pond: finding 17), stings termites and carries them home.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raids")
    public static void aScoutsTrailOverARidgeAndRoundAPondLeadsTheRaidThereAndHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.moving(helper, 7.0); // (the morning raid hours)
        TestScenes.shapeGround(helper, 45, (x, z) -> Math.max(0, 3 - Math.abs(x - 33))); // (the ridge, top at x 33, its foot at 30 and 36)
        for (int x = 27; x <= 29; x++) {
            for (int z = 15; z <= 27; z++) {
                helper.setBlock(x, SURFACE - 1, z, Blocks.WATER);
                helper.setBlock(x, SURFACE - 2, z, Blocks.WATER);
            }
        }
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        // (the mound's own dead tree off the plain past the hump: it may lie across the logs or the ridge, and a log
        // with a trunk lying over it is no feeding spot a major can break into from the open)
        for (int x = 27; x < 45; x++) {
            for (int z = 0; z < 45; z++) {
                for (int y = SURFACE - 1; y < SURFACE + 40; y++) { // (a standing dead tree stands tall)
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir() && !state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.WATER)) {
                        helper.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        termites.addAll(feedingUnderALog(helper, 36, NEST + 3));
        termites.addAll(feedingUnderALog(helper, 36, NEST + 5));
        termites.addAll(feedingUnderALog(helper, 36, NEST + 7));
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 60);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        scoutsHeadToward(helper, colony, 37, NEST + 5);
        int plain = helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        RaidColumn[] column = {null};
        helper.onEachTick(() -> {
            if (column[0] == null && raids.column() != null) {
                column[0] = raids.column();
            }
        });
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(raids.sendNow(level) != null, "the scout command found no fit major"))
                .thenWaitUntil(() -> helper.assertTrue(column[0] != null, "no raid yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    Raids.Find find = raid.find();
                    helper.assertTrue(termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6)),
                            "the scout's find at " + find.at().toShortString() + " is not by the termites");
                    List<BlockPos> line = find.trail();
                    // (over the ridge itself, not the mound's hump at the nest end)
                    int ridge = helper.absolutePos(new BlockPos(33, SURFACE, NEST)).getX();
                    int top = line.stream().filter(p -> Math.abs(p.getX() - ridge) <= 1).mapToInt(BlockPos::getY).max().orElse(plain);
                    helper.assertTrue(top >= plain + 2, "the scent line does not climb the ridge: its highest point on the ridge is "
                            + (top - plain) + " over the plain; " + line);
                    BlockPos a = line.get(0), b = line.get(line.size() - 1);
                    double off = 0.0;
                    int pondMiddle = helper.absolutePos(new BlockPos(28, SURFACE, NEST)).getX();
                    for (BlockPos p : line) {
                        off = Math.max(off, offLine(p, a, b));
                        // (over the pond's middle row: an ant skirting the rim may hang its middle over the edge a moment)
                        helper.assertTrue(p.getX() != pondMiddle || level.getFluidState(p).isEmpty() && level.getFluidState(p.below()).isEmpty(),
                                "a point of the scent line lies in the pond at " + p.toShortString());
                    }
                    helper.assertTrue(off >= 3.0, "the scent line does not bend round the pond: at most "
                            + Math.round(off * 10) / 10.0 + " blocks off the straight way; " + line);
                    SavannaMod.LOGGER.info("[raid hill test] set out at {}, the line {} blocks off the straight way, {} up: {}{}", helper.getTick(),
                            Math.round(off * 10) / 10.0, top - plain, raids.describe(level), raid.describe());
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage().ordinal() >= RaidColumn.Stage.ATTACKING.ordinal(),
                        "the column did not reach the termites over the ridge and round the pond; " + raids.describe(level) + column[0].describe()))
                .thenWaitUntil(() -> helper.assertTrue(termites.stream().anyMatch(termite -> !termite.isAlive() || termite.isParalysed()),
                        "no termite stung; " + raids.describe(level) + column[0].describe()))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() != column[0], "the raid is not home yet; " + column[0].describe()))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    SavannaMod.LOGGER.info("[raid hill test] home at {}: {} came home ({}), {} carried", helper.getTick(), raid.cameHome(),
                            raid.endedWhy(), raid.carried());
                    helper.assertTrue(raid.carried() >= 1, "the raid came home with no termites: " + raid.endedWhy());
                    helper.assertTrue(raid.cameHome() * 2 >= raid.members().size(),
                            "only " + raid.cameHome() + " of " + raid.members().size() + " came home with the column: " + raid.endedWhy());
                })
                .thenSucceed();
    }

    /** How far (flat) this place lies from the straight line through a and b. */
    private static double offLine(BlockPos p, BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            return flat(p, a);
        }
        return Math.abs((p.getX() - a.getX()) * dz - (p.getZ() - a.getZ()) * dx) / length;
    }

    /**
     * Raid rework R4, the scent trail lost: a raid led by an ant that never scouted (no former scout in the column) meets
     * a gap in its trail too wide to pick up (a wall put on 8 blocks of it): it stops, casts about, gives up and turns
     * home, and the spot is no empty check (it was never reached). The trails are saved with their age; once this one
     * has faded and the ant that led there is gone, nobody knows the way: no raid sets out to the spot. The raid code
     * review, finding 6: turned round, the ant nearest home leads and the column walks home at once (no freeze). Finding
     * 1: a second raid whose ants are all out of sight past the minute's grace ends "lost from sight"; its spot is no
     * empty check, and every one of its ants, seen again, goes home along the trail by itself.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true, batch = "matabele_raids")
    public static void aRaidWithNoFormerScoutTurnsHomeAtAWideGapAndAFadedTrailWithNobodyWhoKnowsTheWayMeansNoRaid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock clock = TestClock.still(helper, 21.0); // (night: no scout sets out by itself, so the colony has no former scouts)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 20);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        BlockPos at = helper.absolutePos(new BlockPos(52, SURFACE, NEST + 4));
        List<java.util.UUID> lostOnes = new ArrayList<>();
        long[] hiddenSince = {-1};
        RaidColumn[] column = {null};
        boolean[] cast = {false};
        long[] turned = {-1};
        long[] progressAt = {-1};
        double[] best = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            RaidColumn raid = column[0];
            if (raid != null && raid.isCasting(raid.leader())) {
                cast[0] = true;
            }
            if (raid == null || !raid.lostTrail() || raid.stage() != RaidColumn.Stage.HOMING) {
                return;
            }
            BlockPos entrance = colony.getCenter();
            if (turned[0] < 0) {
                // Finding 6: the ant nearest home leads the way back.
                turned[0] = helper.getTick();
                progressAt[0] = helper.getTick();
                double nearest = Double.MAX_VALUE;
                for (java.util.UUID id : raid.members()) {
                    if (level.getEntity(id) instanceof MatabeleEntity ant) {
                        nearest = Math.min(nearest, flat(ant.blockPosition(), entrance));
                    }
                }
                if (level.getEntity(raid.leader()) instanceof MatabeleEntity front && flat(front.blockPosition(), entrance) > nearest + 3.0) {
                    helper.fail("turned round, the column is led by an ant " + Math.round(flat(front.blockPosition(), entrance))
                            + " from home while one is " + Math.round(nearest) + " from it");
                }
            }
            if (level.getEntity(raid.leader()) instanceof MatabeleEntity front) {
                double now = flat(front.blockPosition(), entrance);
                if (now < best[0] - 1.0) {
                    best[0] = now;
                    progressAt[0] = helper.getTick();
                }
            }
            if (best[0] > 9.0 && helper.getTick() - progressAt[0] > 400) {
                helper.fail("the column froze after turning back: its front made no headway home for 20 s; " + raid.describe());
            }
        });
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    BlockPos entrance = colony.getCenter();
                    List<BlockPos> trail = new ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(at.getX() - entrance.getX(), at.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((at.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((at.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    RaidColumn raid = raids.raidForTests(level, at, 2, trail, null);
                    helper.assertTrue(raid != null, "no raid set out; " + raids.describe(level));
                    helper.assertTrue(!raids.isVeteran(raid.leader()), "the raid's leader is a former scout");
                    // R7: set out by hand at 21:00, outside the raid hours, it keeps no hours (dusk does not turn it)
                    helper.assertTrue(raids.isDusk(level) && raid.keepsNoHours(), "a raid set out by hand at night does not keep no hours");
                    // A wall on the trail from 10 to 18 blocks out: a gap of about 10 blocks, past casting reach.
                    for (BlockPos p : raid.trail().points()) {
                        double out = flat(p, entrance);
                        if (out >= 10.0 && out <= 18.0) {
                            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                        }
                    }
                    column[0] = raid;
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].lostTrail(), "the raid did not turn home at the wide gap; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid is not home yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(cast[0], "the front never cast about at the gap");
                    helper.assertTrue(raid.gapsCrossed() == 0, "the raid crossed a gap too wide to pick up, with no former scout");
                    helper.assertTrue(raid.attackedAt() < 0, "the raid attacked after losing its trail");
                    Raids.Find find = raid.find();
                    helper.assertTrue(raids.finds().contains(find) && find.misses() == 0,
                            "a raid that lost its trail counted as an empty check of the spot; " + raids.describe(level));
                    helper.assertTrue(raid.trail().brokenCount() > 0, "the wall's trail points are not broken");
                    // The trail fades and the ant that led there is gone: nobody knows the way.
                    raid.trail().ageForTests(raid.trail().lasting(raids.trailNumbers()) + 1);
                    if (find.scout() != null && level.getEntity(find.scout()) != null) {
                        level.getEntity(find.scout()).discard();
                    }
                    RaidColumn again = raids.raidNow(level);
                    helper.assertTrue(again == null, "a raid set out along a faded trail with nobody who knows the way");
                    helper.assertTrue(raids.whyNoRaid() != null && raids.whyNoRaid().contains("faded"),
                            "no raid, but not for the faded trail: " + raids.whyNoRaid());
                })
                // Finding 1: a second raid, to a spot the other way, out of sight as a whole on the march.
                .thenExecute(() -> {
                    BlockPos entrance = colony.getCenter();
                    BlockPos there = helper.absolutePos(new BlockPos(NEST + 6, SURFACE, 52));
                    List<BlockPos> trail = new ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(there.getX() - entrance.getX(), there.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((there.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((there.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    RaidColumn raid = raids.raidForTests(level, there, 2, trail, null);
                    helper.assertTrue(raid != null, "no second raid set out; " + raids.describe(level));
                    column[0] = raid;
                    lostOnes.addAll(raid.members());
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage() == RaidColumn.Stage.MARCHING
                        && level.getEntity(column[0].leader()) instanceof MatabeleEntity leader && column[0].alongOut(leader.blockPosition()) > 4.0,
                        "the second raid is not out on the march yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    lostOnes.clear();
                    lostOnes.addAll(column[0].members());
                    lostOnes.forEach(column[0]::hideForTests);
                    hiddenSince[0] = level.getGameTime();
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid out of sight did not end; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(raid.lostFromSight(), "the raid out of sight did not end lost from sight: " + raid.endedWhy());
                    helper.assertTrue(level.getGameTime() - hiddenSince[0] >= com.antcolonies.colony.AwayLife.GRACE_TICKS,
                            "the raid ended " + (level.getGameTime() - hiddenSince[0]) + " ticks after going out of sight, before the grace");
                    Raids.Find find = raid.find();
                    helper.assertTrue(raids.finds().contains(find) && find.misses() == 0,
                            "a raid lost from sight counted as an empty check of the spot; " + raids.describe(level));
                    for (java.util.UUID id : lostOnes) {
                        helper.assertTrue(!(level.getEntity(id) instanceof MatabeleEntity ant) || !ant.isAlive() || raids.isGoingHome(id)
                                        || flat(ant.blockPosition(), colony.getCenter()) <= 8.0,
                                "an ant of the raid lost from sight is not going home: " + id);
                    }
                })
                .thenWaitUntil(() -> {
                    for (java.util.UUID id : lostOnes) {
                        if (level.getEntity(id) instanceof MatabeleEntity ant && ant.isAlive()) {
                            helper.assertTrue(flat(ant.blockPosition(), colony.getCenter()) <= 9.0 || colony.nest().isInside(level, ant.blockPosition()),
                                    "an ant of the raid lost from sight is not home, at " + ant.blockPosition().toShortString() + ", "
                                            + Math.round(flat(ant.blockPosition(), colony.getCenter())) + " out, doing " + ant.runningGoals()
                                            + " (going home: " + raids.whyGoingHome(id) + ")");
                        }
                    }
                })
                // Raid rework R7, dusk: a raid set out at 18:45, in the evening raid hours, keeps them; at 19:30 on the
                // march it turns home where it is, attacks nothing, and the spot is no empty check (never reached).
                .thenExecute(() -> {
                    clock.skipTo(18.75);
                    helper.assertTrue(raids.isRaidTime(level) && !raids.isDusk(level), "18:45 is not in the raid hours");
                    BlockPos entrance = colony.getCenter();
                    BlockPos there = helper.absolutePos(new BlockPos(52, SURFACE, 52));
                    List<BlockPos> trail = new ArrayList<>();
                    int steps = (int) Math.ceil(Math.hypot(there.getX() - entrance.getX(), there.getZ() - entrance.getZ()) / 2.0);
                    for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        int x = entrance.getX() + (int) Math.round((there.getX() - entrance.getX()) * t);
                        int z = entrance.getZ() + (int) Math.round((there.getZ() - entrance.getZ()) * t);
                        trail.add(level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                new BlockPos(x, entrance.getY(), z)));
                    }
                    RaidColumn raid = raids.raidForTests(level, there, 2, trail, null);
                    helper.assertTrue(raid != null, "no dusk raid set out; " + raids.describe(level));
                    helper.assertTrue(!raid.keepsNoHours(), "a raid set out in the raid hours keeps no hours");
                    column[0] = raid;
                })
                .thenWaitUntil(() -> helper.assertTrue(column[0].stage() == RaidColumn.Stage.MARCHING
                        && level.getEntity(column[0].leader()) instanceof MatabeleEntity leader && column[0].alongOut(leader.blockPosition()) > 4.0,
                        "the dusk raid is not out on the march yet; " + raids.describe(level)))
                .thenExecute(() -> clock.skipTo(19.5))
                .thenWaitUntil(() -> helper.assertTrue(column[0].turnedAtDusk(), "the raid out at 19:30 did not turn home at dusk; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(raids.column() == null, "the raid turned home at dusk is not home yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    RaidColumn raid = column[0];
                    helper.assertTrue(raid.attackedAt() < 0, "the raid turned home at dusk on the march attacked");
                    helper.assertTrue(raid.cameHome() >= 1, "nobody came home with the dusk raid: " + raid.endedWhy());
                    Raids.Find find = raid.find();
                    helper.assertTrue(raids.finds().contains(find) && find.misses() == 0,
                            "a raid turned home at dusk counted as an empty check of the spot; " + raids.describe(level));
                    // R7, unloaded land: no raid sets out to a spot in land not loaded (the find is only remembered).
                    BlockPos far = colony.getCenter().offset(600, 0, 0);
                    RaidColumn none = raids.raidForTests(level, far, 2, List.of(colony.getCenter(), far), null);
                    helper.assertTrue(none == null && raids.whyNoRaid() != null && raids.whyNoRaid().contains("not loaded"),
                            "a raid set out to land not loaded, or not for that: " + raids.whyNoRaid());
                })
                .thenSucceed();
    }

    /**
     * Raid scouts search like real ants (2026-11-04, in place of R5's rings and slices), the scouts' memory asked of the
     * colony directly (no ant walks; at 3:00 no scout sets out by itself). Three new scouts get their own ways out,
     * spread round the nest (40 degrees or more apart), and each keeps its way trip after trip; a trip walks out a share
     * of the scout's reach (the first range at first). The search range bug in its new form: a scout that only set out
     * and came back, or came home after searching less than {@code searched_share} of its trip time, learns nothing, its
     * reach and way unchanged. A trip it really searched empty grows its reach by {@code range_step} (its way drifting a
     * little) up to {@code most_range}; then its way swings instead. The colony's reach is its scouts' farthest. A find
     * 20 blocks out turns its way to the find and its reach back to the first range. Choosiness (kept from R5): a find
     * needs more worth the farther it is, and less the hungrier the colony.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true, batch = "matabele_scouts")
    public static void eachScoutKeepsItsOwnWayAndItsReachGrowsOnlyAfterTripsItReallySearchedThenShrinksToItsFind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestClock.still(helper, 3.0); // (night: no scout sets out by itself; the scouts' trips are the test's own)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 20);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(raids != null && pile != null, "a Matabele colony has no raids part or prey pile");
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    SavannaNumbers.ScoutNumbers numbers = raids.numbers();
                    SavannaNumbers.SearchNumbers search = raids.search();
                    int first = numbers.firstRange();
                    int step = numbers.rangeStep();
                    int most = numbers.mostRange();
                    List<MatabeleEntity> majors = colony.population().realMembers(level).stream()
                            .filter(ant -> ant instanceof MatabeleEntity matabele && matabele.hasMajorBody())
                            .map(ant -> (MatabeleEntity) ant).limit(3).toList();
                    helper.assertTrue(majors.size() == 3, "fewer than 3 real majors in the colony: " + majors.size());
                    MatabeleEntity a = majors.get(0);
                    helper.assertTrue(raids.range() == first, "the colony's reach starts at " + raids.range());

                    // each new scout its own way, spread from the others'; each keeps it
                    List<Double> ways = new ArrayList<>();
                    for (MatabeleEntity major : majors) {
                        Raids.Trip trip = raids.plan(level, major);
                        helper.assertTrue(trip != null && trip.find() == null && trip.reach() == first, "a new scout's first trip is " + trip);
                        helper.assertTrue(trip.out() >= Math.floor(first * search.outNear()) - 1 && trip.out() <= Math.ceil(first * search.outFar()) + 1,
                                "a trip walks out " + trip.out() + " blocks of a reach of " + first);
                        helper.assertTrue(major.scoutMemory().hasHeading() && Math.abs(Raids.turnBetween(trip.heading(), major.scoutMemory().heading())) < 1e-6,
                                "the trip does not go the scout's own way");
                        ways.add(trip.heading());
                    }
                    for (int i = 0; i < ways.size(); i++) {
                        for (int j = i + 1; j < ways.size(); j++) {
                            double gap = Math.toDegrees(Math.abs(Raids.turnBetween(ways.get(i), ways.get(j))));
                            helper.assertTrue(gap >= 40, "two new scouts' ways are only " + Math.round(gap) + " degrees apart: " + ways);
                        }
                    }
                    double aWay = a.scoutMemory().heading();
                    Raids.Trip trip = raids.plan(level, a);
                    helper.assertTrue(trip != null && Math.abs(Raids.turnBetween(trip.heading(), aWay)) < 1e-6, "the scout's next trip changed its way");

                    // the search range bug, in its new form: setting out is not searching
                    raids.setOut(a, trip);
                    raids.back(a);
                    helper.assertTrue(a.scoutMemory().reach(first) == first && raids.range() == first,
                            "a scout's reach grew from only setting out: " + a.scoutMemory().reach(first));
                    int least = (int) Math.ceil(numbers.tripTicks() * search.searchedShare());
                    helper.assertTrue(!raids.cameHomeEmpty(level, a, trip, least - 20, "the test"),
                            "a trip cut short counted as searched empty");
                    helper.assertTrue(a.scoutMemory().reach(first) == first && Math.abs(Raids.turnBetween(a.scoutMemory().heading(), aWay)) < 1e-6,
                            "a trip cut short changed the scout's reach or way");

                    // trips really searched empty: farther each time, the way drifting a little
                    helper.assertTrue(raids.cameHomeEmpty(level, a, trip, least, "the test"), "a trip searched long enough did not count");
                    helper.assertTrue(a.scoutMemory().reach(first) == first + step && raids.range() == first + step,
                            "after a trip searched empty the scout's reach is " + a.scoutMemory().reach(first) + ", the colony's " + raids.range());
                    double drift = Math.toDegrees(Math.abs(Raids.turnBetween(aWay, a.scoutMemory().heading())));
                    helper.assertTrue(drift < 90, "the scout's way swung " + Math.round(drift) + " degrees after one empty trip (it drifts)");
                    for (int i = 0; i < 40 && a.scoutMemory().reach(first) < most; i++) {
                        raids.cameHomeEmpty(level, a, raids.plan(level, a), numbers.tripTicks(), "the test");
                    }
                    helper.assertTrue(a.scoutMemory().reach(first) == most && raids.range() == most,
                            "the reach did not grow to the most, " + most + ": " + a.scoutMemory().reach(first));
                    Raids.Trip far = raids.plan(level, a);
                    helper.assertTrue(far != null && far.reach() == most && far.out() <= Math.ceil(most * search.outFar()) + 1,
                            "a trip at the most reach is " + far);
                    raids.cameHomeEmpty(level, a, far, numbers.tripTicks(), "the test");
                    helper.assertTrue(a.scoutMemory().reach(first) == most, "the reach grew past the most: " + a.scoutMemory().reach(first));
                    helper.assertTrue(a.scoutMemory().emptyTrips() >= (most - first) / step + 1, "empty trips counted " + a.scoutMemory().emptyTrips());

                    // choosiness (Frank and Linsenmair 2017)
                    BlockPos center = colony.getCenter();
                    double near = raids.need(center.offset(10, 0, 0));
                    double farNeed = raids.need(center.offset(60, 0, 0));
                    helper.assertTrue(near < farNeed, "a find 10 blocks out needs " + near + ", one 60 out " + farNeed + ": not more the farther");
                    helper.assertTrue(pile.stock(level, 2) == 2, "the pile did not take 2 layers");
                    double fedFar = raids.need(center.offset(60, 0, 0));
                    helper.assertTrue(fedFar > farNeed, "fed, a find 60 out needs " + fedFar + ", hungry " + farNeed + ": not less the hungrier");

                    // a find 20 blocks out to the east: its way turns to the find, its reach shrinks back
                    raids.foundBy(level, a, center.offset(20, 0, 0));
                    helper.assertTrue(a.scoutMemory().reach(first) == Math.max(first, 20 + search.findMargin()) && a.scoutMemory().emptyTrips() == 0,
                            "after a find 20 out the scout's reach is " + a.scoutMemory().reach(first));
                    helper.assertTrue(Math.abs(Raids.turnBetween(a.scoutMemory().heading(), 0.0)) < 0.05,
                            "after a find to the east the scout's way is " + Math.round(Math.toDegrees(a.scoutMemory().heading())) + " degrees");
                    helper.assertTrue(raids.range() == first, "after the find the colony's reach is " + raids.range());
                    Raids.Trip after = raids.plan(level, a);
                    helper.assertTrue(after != null && after.reach() == first && Math.abs(Raids.turnBetween(after.heading(), 0.0)) < 0.05,
                            "the trip after the find is " + after);
                })
                .thenSucceed();
    }

    /**
     * Raid scouts search like real ants, on real walks: a hungry colony of 150 (3 scouts) on an empty plain, no termites
     * anywhere. The scouts set out their own ways, spread round the nest; each walks out, then searches on a winding path
     * (steps turning this way and that, not a straight line or a neat sweep); with nothing found they come home empty,
     * and a scout that really searched goes farther next time (its reach grows past the first range).
     */
    @GameTest(template = "arena", timeoutTicks = 16000, skyAccess = true, batch = "matabele_scouts")
    public static void scoutsSetOutTheirOwnWaysSearchOnWindingPathsAndReachFartherAfterEmptyTrips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 13.0); // (scouts search all day)
        TestScenes.shapeGround(helper, 65, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(30, SURFACE, 32)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(34, SURFACE, 32)), 150);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        int first = raids.numbers().firstRange();
        java.util.Map<java.util.UUID, List<BlockPos>> paths = new java.util.HashMap<>();
        helper.onEachTick(() -> {
            for (var insect : colony.population().realMembers(level)) {
                if (insect instanceof MatabeleEntity ant && ant.scoutGoalForTests() != null && ant.scoutGoalForTests().isSearchingForTests()) {
                    List<BlockPos> steps = ant.scoutGoalForTests().searchStepsForTests();
                    if (steps.size() > paths.getOrDefault(ant.getUUID(), List.of()).size()) {
                        paths.put(ant.getUUID(), steps);
                    }
                }
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(raids.headingsForTests().size() >= 3,
                        "not 3 scouts out yet: " + raids.headingsForTests().size() + "; " + raids.describe(level)))
                .thenExecute(() -> {
                    List<Double> ways = new ArrayList<>(raids.headingsForTests().values());
                    double smallest = 360;
                    for (int i = 0; i < ways.size(); i++) {
                        for (int j = i + 1; j < ways.size(); j++) {
                            smallest = Math.min(smallest, Math.toDegrees(Math.abs(Raids.turnBetween(ways.get(i), ways.get(j)))));
                        }
                    }
                    helper.assertTrue(smallest >= 30, "two scouts of one colony set out only " + Math.round(smallest) + " degrees apart");
                })
                .thenWaitUntil(() -> helper.assertTrue(paths.values().stream().anyMatch(steps -> steps.size() >= 12),
                        "no scout has searched 12 steps yet"))
                .thenExecute(() -> {
                    List<BlockPos> steps = paths.values().stream().max(java.util.Comparator.comparingInt(List::size)).orElseThrow();
                    double walked = 0;
                    int turns = 0;
                    for (int i = 1; i < steps.size(); i++) {
                        walked += flat(steps.get(i - 1), steps.get(i));
                        if (i >= 2) {
                            double before = Math.atan2(steps.get(i - 1).getZ() - steps.get(i - 2).getZ(), steps.get(i - 1).getX() - steps.get(i - 2).getX());
                            double now = Math.atan2(steps.get(i).getZ() - steps.get(i - 1).getZ(), steps.get(i).getX() - steps.get(i - 1).getX());
                            if (Math.abs(Raids.turnBetween(before, now)) > Math.toRadians(15)) {
                                turns++;
                            }
                        }
                    }
                    double straight = flat(steps.get(0), steps.get(steps.size() - 1));
                    helper.assertTrue(walked > straight * 1.5, "the search path is not winding: walked " + Math.round(walked)
                            + " blocks to get " + Math.round(straight) + " from its start");
                    helper.assertTrue(turns * 3 >= steps.size() - 2, "the search path turned at only " + turns + " of " + (steps.size() - 2) + " steps");
                })
                .thenWaitUntil(() -> helper.assertTrue(colony.population().realMembers(level).stream()
                                .anyMatch(ant -> ant instanceof MatabeleEntity matabele && matabele.scoutMemory().reach(first) > first),
                        "no scout's reach grew after an empty trip; " + raids.describe(level)))
                .thenSucceed();
    }

    /**
     * Raid rework R5, a choosy scout on a real walk: a colony of 40, about half fed (its prey pile kept between a half and
     * three quarters of a day's food), has a lone termite feeding under a log about 17 blocks out in the middle of
     * every slice, and a group of four under two logs about 28 blocks out to the south-east. A lone termite (worth 3)
     * that far out is too poor for a raid with the colony half fed (it needs 3.5 or more), so the scouts pass them over
     * and search on; the group (worth 12) is taken. No find is ever made at a lone termite, and no scout harms one.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true, batch = "matabele_scouts")
    public static void aHalfFedColonysScoutsPassOverLoneTermitesAndTakeARicherSpotFurtherOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 13.0); // (between the raid hours: the find waits, no raid)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> lone = new ArrayList<>();
        lone.addAll(feedingUnderALog(helper, NEST + 11, NEST + 12, 1));
        lone.addAll(feedingUnderALog(helper, NEST - 13, NEST + 12, 1));
        lone.addAll(feedingUnderALog(helper, NEST - 13, NEST - 12, 1));
        lone.addAll(feedingUnderALog(helper, NEST + 11, NEST - 12, 1));
        List<TermiteWorkerEntity> group = new ArrayList<>();
        group.addAll(feedingUnderALog(helper, NEST + 19, NEST + 19));
        group.addAll(feedingUnderALog(helper, NEST + 19, NEST + 21));
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 40);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(raids != null && pile != null, "a Matabele colony has no raids part or prey pile");
        scoutsHeadToward(helper, colony, NEST + 20, NEST + 20); // (its way past the south-east lone termite to the group)
        helper.onEachTick(() -> {
            if (helper.getTick() > 20 && helper.getTick() % 5 == 0 && pile.edibleTermites() < 20) {
                pile.stock(level, 1); // (about half fed: never a day's food, so still hungry, never starving; a new
                                      // colony's 40 hungry workers eat its first meals fast)
            }
            for (TermiteWorkerEntity termite : lone) {
                helper.assertTrue(termite.isAlive() && !termite.isParalysed(), "a scout attacked a lone termite (scouts only look)");
            }
            for (Raids.Find find : raids.finds()) {
                for (TermiteWorkerEntity termite : lone) {
                    helper.assertTrue(!termite.blockPosition().closerThan(find.at(), 6),
                            "a find was made at a lone termite (worth " + find.worth() + ") at " + find.at().toShortString() + "; " + raids.describe(level));
                }
            }
        });
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(pile.stock(level, 2) == 2, "the pile did not take 2 layers"))
                .thenExecute(() -> helper.assertTrue(raids.fedShare() >= 0.4 && raids.isHungry(),
                        "the colony is " + Math.round(raids.fedShare() * 100) + " percent fed, not about half"))
                .thenWaitUntil(() -> helper.assertTrue(!raids.finds().isEmpty(), "no find yet (" + raids.passedOverCount() + " passed over); "
                        + raids.describe(level)))
                .thenExecute(() -> {
                    Raids.Find find = raids.finds().get(0);
                    helper.assertTrue(group.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6)),
                            "the find at " + find.at().toShortString() + " is not at the group of four");
                    helper.assertTrue(find.worth() >= 9, "the group of four scored worth only " + find.worth());
                    helper.assertTrue(raids.passedOverCount() >= 1, "the scouts passed no lone termite over on their way out to the group");
                    SavannaMod.LOGGER.info("[choosy scout test] found at {}, {} passed over: {}", helper.getTick(), raids.passedOverCount(),
                            raids.describe(level));
                })
                .thenSucceed();
    }

    /**
     * Raid rework R6, the mound as a clue (the user's idea): a hungry colony of 10 in a dead mound in the arena's middle,
     * a living young termite mound 41 blocks off in its north-east corner, two tunnels of its termites dug out from it by
     * hand (as if the colony had dug them to food): one south-east to four termites feeding under two logs 40 blocks from
     * the nest, one west to a lone termite under a log 40 out. Both lie past the first reach (32 blocks), so the plain search never
     * finds them. The scout's own way is set toward the mound (a new scout's way is random); searching that way, it
     * notices it, rings it, smells the east tunnel on the first ring and follows it out to the four termites: the find is
     * there, worth a raid that far out (R5's choosing), made by the mound clue; no find at
     * the lone termite (too poor that far out for a starving colony: passed over if smelt), and no scout harms a termite.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true, batch = "matabele_scouts")
    public static void aScoutRingsALivingMoundItComesNearAndFollowsATermiteTunnelOutToTheFeeding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 13.0); // (between the raid hours: the find waits, no raid)
        TestScenes.shapeGround(helper, 65, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(24, SURFACE, 38)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        Colony termites = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(52, SURFACE, 8)), NewLandMounds.pickFor("young", 0, 11L));
        helper.assertTrue(termites != null, "no living termite mound put in");
        termites.foodMap().setTestArea(helper.getBounds().expandTowards(0, 16, 0));
        termites.part(SavannaParts.ALARM).ifPresent(alarm -> alarm.setTestArea(helper.getBounds()));
        // the east tunnel, 2 under the ground, from the mound's middle out to the logs, and its feeding gallery under the first
        List<BlockPos> east = new ArrayList<>();
        for (int z = 8; z <= 19; z++) {
            east.add(new BlockPos(52 + Math.min(7, z - 8), SURFACE - 2, z));
        }
        List<TermiteWorkerEntity> group = new ArrayList<>();
        group.addAll(feedingUnderALog(helper, 58, 20));
        group.addAll(feedingUnderALog(helper, 58, 22));
        List<BlockPos> eastGallery = List.of(new BlockPos(58, SURFACE - 1, 20), new BlockPos(59, SURFACE - 1, 20),
                new BlockPos(60, SURFACE - 1, 20), new BlockPos(61, SURFACE - 1, 20));
        // the west tunnel out to one termite under a log
        List<BlockPos> west = new ArrayList<>();
        for (int x = 52; x >= 37; x--) {
            west.add(new BlockPos(x, SURFACE - 2, Math.max(1, 8 - (52 - x) / 2)));
        }
        List<TermiteWorkerEntity> lone = feedingUnderALog(helper, 34, 0, 1);
        List<BlockPos> westGallery = List.of(new BlockPos(35, SURFACE - 1, 0), new BlockPos(36, SURFACE - 1, 0));
        com.antcolonies.colony.FoodWays ways = termites.nest().foodWays();
        for (List<BlockPos> tunnel : List.of(east, west)) {
            List<BlockPos> absolute = new ArrayList<>();
            for (BlockPos cell : tunnel) {
                helper.setBlock(cell, Blocks.AIR);
                absolute.add(helper.absolutePos(cell));
            }
            boolean isEast = tunnel == east;
            BlockPos end = absolute.get(absolute.size() - 1);
            ways.addNewTunnelForTests(new com.antcolonies.colony.FoodWays.NewTunnel(
                    new com.antcolonies.colony.NestPlan.ForagingTunnel(List.copyOf(absolute), end.above(), isEast ? Direction.SOUTH : Direction.WEST),
                    helper.absolutePos(isEast ? new BlockPos(59, SURFACE, 20) : new BlockPos(35, SURFACE, 0)),
                    (isEast ? eastGallery : westGallery).stream().map(helper::absolutePos).toList()));
        }
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(28, SURFACE, 38)), 10);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        scoutsHeadToward(helper, colony, 52, 8); // (its own way toward the living mound: it comes near it)
        for (BlockPos far : List.of(new BlockPos(59, SURFACE, 21), new BlockPos(35, SURFACE, 0), new BlockPos(59, SURFACE, 19), new BlockPos(40, SURFACE, 1))) {
            helper.assertTrue(flat(colony.getCenter(), helper.absolutePos(far)) > 37, far.toShortString() + " is only "
                    + flat(colony.getCenter(), helper.absolutePos(far)) + " blocks from the nest (the scene wants it past the first ring's reach)");
        }
        SavannaMod.LOGGER.info("[mound clue test] the living mound's foot reaches {} blocks from its middle, {} blocks from the nest",
                com.antcolonies.savanna.colony.FeedingSpots.moundReach(level, termites), flat(colony.getCenter(), termites.getCenter()));
        helper.onEachTick(() -> {
            for (TermiteWorkerEntity termite : lone) {
                helper.assertTrue(termite.isAlive() && !termite.isParalysed(), "a scout attacked the lone termite (scouts only look)");
            }
            for (TermiteWorkerEntity termite : group) {
                helper.assertTrue(termite.isAlive() && !termite.isParalysed(), "a scout attacked a termite of the group (scouts only look)");
            }
            for (Raids.Find find : raids.finds()) {
                for (TermiteWorkerEntity termite : lone) {
                    helper.assertTrue(!termite.blockPosition().closerThan(find.at(), 6),
                            "a find was made at the lone termite (worth " + find.worth() + ") at " + find.at().toShortString() + "; " + raids.describe(level));
                }
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(raids.moundsNoticed() >= 1, "no scout noticed the living mound yet; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(!raids.finds().isEmpty(), "no find yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    Raids.Find find = raids.finds().get(0);
                    helper.assertTrue(group.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6)),
                            "the find at " + find.at().toShortString() + " is not at the four termites at the east tunnel's end; " + raids.describe(level));
                    helper.assertTrue(find.worth() >= 9, "the four termites scored worth only " + find.worth());
                    helper.assertTrue(raids.tunnelsFollowed() >= 1 && raids.clueFinds() >= 1,
                            "the find was not made by following a tunnel from the mound (" + raids.tunnelsFollowed() + " followed, "
                                    + raids.clueFinds() + " clue finds); " + raids.describe(level));
                    // (scouts search like real ants: the find 40 out sets the finder's reach to 48, so the colony's reach is no
                    // sign of how it was found any more; the clue counts above are)
                    List<BlockPos> line = find.trail();
                    helper.assertTrue(line.size() >= 5 && line.get(0).closerThan(colony.getCenter(), 4)
                            && line.get(line.size() - 1).closerThan(find.at(), 8), "no scent line from the nest to the find");
                    SavannaMod.LOGGER.info("[mound clue test] found at {}: {}", helper.getTick(), raids.describe(level));
                })
                .thenSucceed();
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /**
     * Every real major of the colony takes the way toward this place (the scene's) as its own way out: the scene's
     * termites lie that way (raid scouts search like real ants: a new scout's way is random, and a scene with one scout
     * could otherwise wait many trips for it to turn there).
     */
    private static void scoutsHeadToward(GameTestHelper helper, Colony colony, int x, int z) {
        BlockPos center = colony.getCenter();
        BlockPos to = helper.absolutePos(new BlockPos(x, SURFACE, z));
        double way = Math.atan2(to.getZ() - center.getZ(), to.getX() - center.getX());
        for (var insect : colony.population().realMembers(helper.getLevel())) {
            if (insect instanceof MatabeleEntity ant && ant.hasMajorBody()) {
                ant.scoutMemory().setHeading(way);
            }
        }
    }

    /** The colony's real ant with this id (null none). */
    @org.jetbrains.annotations.Nullable
    private static MatabeleEntity scoutNamed(ServerLevel level, Colony colony, @org.jetbrains.annotations.Nullable java.util.UUID id) {
        for (var insect : colony.population().realMembers(level)) {
            if (insect instanceof MatabeleEntity ant && ant.getUUID().equals(id)) {
                return ant;
            }
        }
        return null;
    }

    /** A stage may last this much past its own longest time (ticks): the column works itself out every few ticks. */
    private static final long SLACK = 300;

    /** Two termite workers feeding in a gallery under a 4-block log (along x from {@code x}) at row {@code z}. */
    private static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z) {
        return feedingUnderALog(helper, x, z, 2);
    }

    /** One or two termite workers feeding in a gallery under a 4-block log (along x from {@code x}) at row {@code z}. */
    private static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z, int count) {
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            helper.setBlock(new BlockPos(x + i, SURFACE, z), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            helper.setBlock(x + i, SURFACE - 1, z, Blocks.AIR);
        }
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 1, SURFACE - 1, z));
        if (count > 1) {
            termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 2, SURFACE - 1, z));
        }
        return termites;
    }
}
