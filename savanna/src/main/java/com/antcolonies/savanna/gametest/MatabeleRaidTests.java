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
     * check. A raider crippled on the march (3 legs gone) leaves the column and limps home along the trail by itself.
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
                            limper[0] = ant;
                            break;
                        }
                    }
                    helper.assertTrue(limper[0] != null, "no raider to cripple");
                    limper[0].setLostLegsForTests(0b000111, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.isLimping(limper[0].getUUID()),
                        "the crippled raider was not sent limping home; " + raids.describe(level)))
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
                .thenExecute(() -> {
                    if (limper[0].isAlive()) {
                        BlockPos entrance = colony.getCenter();
                        helper.assertTrue(flat(limper[0].blockPosition(), entrance) <= 9.0 || colony.nest().isInside(level, limper[0].blockPosition()),
                                "the limper gave up short of home, at " + limper[0].blockPosition().toShortString());
                    }
                })
                .thenSucceed();
    }

    /**
     * Raid rework R2, the whole chain in one go, as in the user's world (BUGS 87): a big hungry colony in a dead
     * termite mound (120 workers, 50 of them real, every one starting inside down the chimney), the scout command's
     * scout finding termites feeding under three logs about 17 blocks out by itself, coming home with its own scent line
     * (none put in by hand), and the raid it leads: at least the least raid of 10 real raiders (4 an ant for each termite smelt; the scout smells too few today, 3 to 6 for 6 termites, raid rework R5) come up out of the chimney, march, attack,
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
        RaidColumn[] column = {null};
        List<MatabeleEntity> raiders = new ArrayList<>();
        java.util.Map<RaidColumn.Stage, Long> stageAt = new java.util.EnumMap<>(RaidColumn.Stage.class);
        boolean[] hung = {false};
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
            long most = switch (stage) {
                case FORMING -> 900; // (form is 600 at most, and a little)
                case MARCHING -> 1200; // (about 17 blocks: 60 s is three times a slow walk)
                case GATHERING -> 600;
                case ATTACKING -> 6600;
                case COLLECTING, SPREADING, REGROUPING -> 1800;
                case HOMING -> 3900;
                case DONE -> Long.MAX_VALUE;
            };
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
                    RaidColumn raid = column[0];
                    Raids.Find find = raid.find();
                    helper.assertTrue(termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6)),
                            "the scout's find at " + find.at().toShortString() + " is not by the termites");
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
                    helper.assertTrue(raid.carried() >= 1, "the raid came home with no termites: " + raid.endedWhy());
                    helper.assertTrue(raid.cameHome() * 2 >= raid.members().size(),
                            "only " + raid.cameHome() + " of " + raid.members().size() + " came home with the column: " + raid.endedWhy());
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
        TestClock.still(helper, 21.0); // (night: no scout sets out by itself, so the colony has no former scouts)
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
