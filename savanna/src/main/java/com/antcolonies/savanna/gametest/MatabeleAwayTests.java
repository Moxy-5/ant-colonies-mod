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
                    helper.assertTrue(brought * 15 >= raiders && brought * 3 <= raiders, "a raid of " + raiders + " brought home only " + brought + " termites" + pile.describe(level));
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
     * The brain while away, step 2 ({@code docs/plans/brain-away-raids.md}): one rule for "is this colony watched". A
     * watcher 84 blocks from the nest (past the far rule's 80) but near one of the colony's ants walking home from the
     * field keeps the colony here and the ant real, past the grace; once the ant is home and nothing of the colony is
     * out near the watcher, the colony goes over to numbers as any far colony does.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "matabele_away_watched")
    public static void aWatcherNearAnAntOutInTheFieldWatchesItsColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        TestClock.still(helper, 13.0); // (between the raid hours: no scout's find starts a raid)
        MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 30);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        com.antcolonies.savanna.entity.MatabeleEntity[] out = new com.antcolonies.savanna.entity.MatabeleEntity[1];
        int[] stage = {0};
        long[] stageAt = new long[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            BlockPos nest = colony.getCenter();
            switch (stage[0]) {
                case 0 -> {
                    if (now < 5) {
                        return;
                    }
                    // one of the colony's real workers, put 27 blocks out in the field and sent home from there
                    out[0] = com.antcolonies.gametest.TestIsolation.insectsOf(level, colony, com.antcolonies.savanna.entity.MatabeleEntity.class)
                            .stream().filter(mob -> mob.nestCaste() != null && mob.isAlive()).findFirst().orElse(null);
                    helper.assertTrue(out[0] != null, "the colony has no real worker");
                    BlockPos field = helper.absolutePos(new BlockPos(MIDDLE + 36, SURFACE + 1, MIDDLE));
                    out[0].teleportTo(field.getX() + 0.5, field.getY(), field.getZ() + 0.5);
                    raids.sendHome(level, out[0], "test");
                    helper.assertTrue(raids.isGoingHome(out[0].getUUID()), "the ant out in the field was not sent home");
                    colony.away().setOnForTests(true);
                    colony.away().setFarForTests(true);
                    colony.away().setGraceForTests(40L);
                    colony.away().setWatcherForTests(nest.offset(84, 0, 0)); // (the ant is between the watcher and the nest)
                    stage[0] = 1;
                    stageAt[0] = now;
                }
                case 1 -> {
                    if (now - stageAt[0] < 100) { // (well past the grace)
                        return;
                    }
                    double far = Math.sqrt(out[0].blockPosition().distSqr(nest));
                    helper.assertTrue(out[0].isAlive() && !out[0].isRemoved() && raids.isGoingHome(out[0].getUUID()) && far > 8,
                            "the ant was to be still walking home here: " + Math.round(far) + " blocks out, going home " + raids.isGoingHome(out[0].getUUID()));
                    helper.assertTrue(colony.away().isHere(level) && !colony.away().isAway(level),
                            "a watcher 84 blocks from the nest but near the colony's ant in the field does not watch the colony");
                    stage[0] = 2;
                    stageAt[0] = now;
                }
                case 2 -> {
                    if (colony.away().isAway(level) && level.getEntity(out[0].getUUID()) == null) {
                        helper.succeed(); // (home, nothing out near the watcher: far, its real ants folded into the numbers)
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * The brain while away, step 3 ({@code docs/plans/brain-away-raids.md}): a raid that set out as numbers is shown as
     * real ants when a watcher comes near where it is. The far scene (a remembered find 39 blocks out at a termite
     * colony's feeding place, the colony far from every watcher); once the raid is out, a watcher stands 56 blocks past
     * the spot, so the raid comes within the far rule's 64 blocks of it over its last 8 blocks, never within the 50
     * where it could be seen appearing. On the way out: a real column marching, its front near the spot, nobody left
     * at the nest end, the raid counted once.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_away_shown_out")
    public static void aRaidOutAsNumbersIsShownOnItsWayOutWhenAWatcherComesNear(GameTestHelper helper) {
        aRaidOutAsNumbersIsShown(helper, false);
    }

    /**
     * The same, the watcher coming only after the raid has attacked as numbers: the raiders stand regrouping at the spot
     * with the catch in their jaws and walk home; out of the watcher's reach they are numbers again, from where they
     * were (never back at the spot), and the catch reaches the prey pile.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_away_shown_home")
    public static void aRaidOutAsNumbersIsShownWithItsCatchAfterItsAttack(GameTestHelper helper) {
        aRaidOutAsNumbersIsShown(helper, true);
    }

    private static void aRaidOutAsNumbersIsShown(GameTestHelper helper, boolean afterAttack) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestScenes.deepGround(helper);
        TestClock.still(helper, 8.0); // (the morning raid hours)
        MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 100);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElseThrow();
        BlockPos queenAt = helper.absolutePos(new BlockPos(54, SURFACE, 54));
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), 54, SURFACE, 54);
        BlockPos spot = helper.absolutePos(new BlockPos(44, SURFACE, 44));
        int[] stage = {0};
        long[] stageAt = new long[1];
        int[] shown = new int[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            BlockPos nest = colony.getCenter();
            double far = Math.sqrt(nest.distSqr(spot));
            // 56 blocks past the spot, on the line from the nest through it
            BlockPos watcher = BlockPos.containing(spot.getX() + (spot.getX() - nest.getX()) / far * 56, spot.getY(), spot.getZ() + (spot.getZ() - nest.getZ()) / far * 56);
            switch (stage[0]) {
                case 0 -> {
                    if (now < 10) {
                        return;
                    }
                    Colony termites = ColonyManager.get(level).getColonies().stream()
                            .filter(other -> SavannaSpecies.MACROTERMES.equals(other.getSpecies()) && other.getCenter().closerThan(queenAt, 6))
                            .findFirst().orElse(null);
                    helper.assertTrue(termites != null, "no termite colony founded by the queen");
                    termites.population().setOnForTests(true);
                    termites.population().addNewMembers(Caste.WORKER, level.getGameTime(), 200);
                    raids.rememberForTests(spot, 12, level.getGameTime());
                    helper.assertTrue(raids.isHungry(), "the Matabele colony is not hungry with an empty pile");
                    colony.population().setOnForTests(true);
                    colony.away().setOnForTests(true);
                    colony.away().setFarForTests(true);
                    colony.away().setGraceForTests(40L);
                    colony.away().setWatcherForTests(nest.offset(0, 0, -600)); // (nobody near)
                    stage[0] = 1;
                    stageAt[0] = now;
                }
                case 1 -> {
                    if (!raids.away().isOut()) {
                        helper.assertTrue(now - stageAt[0] < 4000, "no raid set out as numbers from the far colony");
                        return;
                    }
                    helper.assertTrue(raids.column() == null,
                            "the far colony's raid set out with real ants in the world");
                    if (!afterAttack) {
                        colony.away().setWatcherForTests(watcher);
                    }
                    stage[0] = afterAttack ? 2 : 3;
                    stageAt[0] = now;
                }
                case 2 -> {
                    if (raids.away().hasAttacked()) {
                        colony.away().setWatcherForTests(watcher);
                        stage[0] = 3;
                        stageAt[0] = now;
                    } else {
                        helper.assertTrue(raids.away().isOut(), "the raid out as numbers ended before its attack");
                    }
                }
                case 3 -> {
                    com.antcolonies.savanna.colony.RaidColumn column = raids.column();
                    if (column == null) {
                        helper.assertTrue(raids.away().isOut(), "the raid out as numbers came home without being shown to the watcher by its trail");
                        return;
                    }
                    helper.assertFalse(raids.away().isOut(), "the raid is real ants and numbers at once");
                    helper.assertTrue(raids.away().raidsToday() == 1, "the shown raid was counted " + raids.away().raidsToday() + " times today");
                    helper.assertTrue(column.members().size() >= 10, "only " + column.members().size() + " raiders were shown");
                    shown[0] = column.members().size();
                    int held = 0;
                    for (java.util.UUID id : column.members()) {
                        helper.assertTrue(level.getEntity(id) instanceof com.antcolonies.savanna.entity.MatabeleEntity, "a shown raider is not in the world");
                        com.antcolonies.savanna.entity.MatabeleEntity ant = (com.antcolonies.savanna.entity.MatabeleEntity) level.getEntity(id);
                        double toSpot = Math.sqrt(ant.blockPosition().distSqr(spot));
                        double toWatcher = Math.hypot(ant.getX() - watcher.getX(), ant.getZ() - watcher.getZ());
                        helper.assertTrue(toSpot <= far + 4 && toWatcher >= 50, "a shown raider stands " + Math.round(toSpot) + " blocks from the spot, "
                                + Math.round(toWatcher) + " from the watcher (it should be on the trail between nest and spot, out of the watcher's sight)");
                        held += com.antcolonies.savanna.entity.MatabeleLoads.termitesHeldBy(ant);
                    }
                    SavannaMod.LOGGER.info("[shown raid test] shown at {}: {} raiders, stage {}, {} termite(s) held, carried {}",
                            now, shown[0], column.stage(), held, column.carried());
                    if (!afterAttack) {
                        helper.assertTrue(column.stage() == com.antcolonies.savanna.colony.RaidColumn.Stage.MARCHING, "shown on its way out, the column is "
                                + column.stage() + ", not marching");
                        helper.assertTrue(held == 0, "raiders on their way out hold termites");
                        helper.succeed();
                        return;
                    }
                    helper.assertTrue(column.stage() == com.antcolonies.savanna.colony.RaidColumn.Stage.REGROUPING
                            || column.stage() == com.antcolonies.savanna.colony.RaidColumn.Stage.HOMING, "shown after its attack, the column is " + column.stage());
                    helper.assertTrue(held > 0 && held == column.carried(), "the raiders shown after the attack hold " + held + " termite(s), the column counts "
                            + column.carried());
                    stage[0] = 4;
                    stageAt[0] = now;
                }
                case 4 -> {
                    // they walk home; out of the watcher's reach they are numbers again and the catch comes home
                    if (raids.column() == null && !raids.away().isOut() && pile.broughtIn() > 0) {
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * The brain while away, step 4: a scout out as numbers is shown as a real scout when a watcher comes near where it
     * is. A hungry far colony with no find; once it is away its number scouts have places on their own ways out; a
     * watcher stands 56 blocks past one (inside the far rule's 64, outside the 50 where it would be seen appearing):
     * a real major stands there, one of the colony's scouts out, never nearer the watcher than 50 as it appears, and
     * its real search goes on from there (it walks).
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_away_shown_scout")
    public static void aScoutOutAsNumbersIsShownWhenAWatcherComesNear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        TestClock.still(helper, 8.0);
        MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 100);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        int[] stage = {0};
        long[] stageAt = new long[1];
        BlockPos[] placeAt = new BlockPos[1];
        BlockPos[] watcherAt = new BlockPos[1];
        java.util.UUID[] scout = new java.util.UUID[1];
        BlockPos[] firstAt = new BlockPos[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            BlockPos nest = colony.getCenter();
            switch (stage[0]) {
                case 0 -> {
                    if (now < 10) {
                        return;
                    }
                    helper.assertTrue(raids.isHungry(), "the Matabele colony is not hungry with an empty pile");
                    colony.population().setOnForTests(true);
                    colony.away().setOnForTests(true);
                    colony.away().setFarForTests(true);
                    colony.away().setGraceForTests(40L);
                    colony.away().setWatcherForTests(nest.offset(0, 0, -600)); // (nobody near)
                    stage[0] = 1;
                    stageAt[0] = now;
                }
                case 1 -> {
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(now - stageAt[0] < 400, "the far colony never went away");
                        return;
                    }
                    // (no raid and no find in this scene: with no termites on record the far colony hunts by the land, and
                    // a colony with a find worth a raid sends no scouts; the day's raids are made, the dice finds forgotten)
                    raids.fillDayForTests(level);
                    raids.forgetFindsForTests();
                    java.util.List<BlockPos> places = raids.away().scoutPlaces(level);
                    if (places.isEmpty()) {
                        helper.assertTrue(now - stageAt[0] < 6000, "the far, hungry colony's scouts out as numbers never had a place (raid out as numbers "
                                + raids.away().isOut() + ", finds " + raids.finds().size() + ", hungry " + raids.isHungry() + ", scouting time " + raids.isScoutingTime(level)
                                + ", reach " + raids.range() + ")");
                        return;
                    }
                    helper.assertTrue(raids.scoutsOut().isEmpty(), "a colony away has real scouts out");
                    BlockPos place = places.get(0);
                    double far = Math.sqrt((place.getX() - nest.getX()) * (double) (place.getX() - nest.getX())
                            + (place.getZ() - nest.getZ()) * (double) (place.getZ() - nest.getZ()));
                    helper.assertTrue(far >= 15 && far <= raids.range() + 1, "a scout out as numbers is " + Math.round(far) + " blocks from its nest");
                    placeAt[0] = place;
                    watcherAt[0] = BlockPos.containing(place.getX() + (place.getX() - nest.getX()) / far * 56, place.getY(),
                            place.getZ() + (place.getZ() - nest.getZ()) / far * 56);
                    colony.away().setWatcherForTests(watcherAt[0]);
                    stage[0] = 2;
                    stageAt[0] = now;
                }
                case 2 -> {
                    if (raids.scoutsOut().isEmpty()) {
                        helper.assertTrue(now - stageAt[0] < 200, "no real scout was shown to the watcher 56 blocks past the number scout's place");
                        return;
                    }
                    scout[0] = raids.scoutsOut().iterator().next();
                    helper.assertTrue(level.getEntity(scout[0]) instanceof com.antcolonies.savanna.entity.MatabeleEntity, "the shown scout is not in the world");
                    com.antcolonies.savanna.entity.MatabeleEntity ant = (com.antcolonies.savanna.entity.MatabeleEntity) level.getEntity(scout[0]);
                    helper.assertTrue(ant.hasMajorBody(), "the shown scout is a minor");
                    double toPlace = Math.hypot(ant.getX() - placeAt[0].getX(), ant.getZ() - placeAt[0].getZ());
                    double toWatcher = Math.hypot(ant.getX() - watcherAt[0].getX(), ant.getZ() - watcherAt[0].getZ());
                    double toNest = Math.hypot(ant.getX() - nest.getX(), ant.getZ() - nest.getZ());
                    SavannaMod.LOGGER.info("[shown scout test] shown at {}: {} from its place, {} from the watcher, {} from the nest",
                            now, Math.round(toPlace), Math.round(toWatcher), Math.round(toNest));
                    helper.assertTrue(toPlace <= 12 && toWatcher >= 50 && toNest >= 12, "the shown scout stands " + Math.round(toPlace) + " blocks from the number scout's place, "
                            + Math.round(toWatcher) + " from the watcher, " + Math.round(toNest) + " from the nest");
                    helper.assertFalse(colony.away().isAway(level), "the colony is away with a watcher near its scout");
                    firstAt[0] = ant.blockPosition();
                    stage[0] = 3;
                    stageAt[0] = now;
                }
                case 3 -> {
                    // its real search goes on from there: still a scout out, and it walks
                    if (!(level.getEntity(scout[0]) instanceof com.antcolonies.savanna.entity.MatabeleEntity ant)) {
                        helper.fail("the shown scout left the world while watched");
                        return;
                    }
                    if (raids.isScouting(scout[0]) && !ant.blockPosition().closerThan(firstAt[0], 5)) {
                        helper.succeed();
                        return;
                    }
                    helper.assertTrue(now - stageAt[0] < 1200, "the shown scout never walked on searching (it stands at " + ant.blockPosition().toShortString()
                            + ", scouting " + raids.isScouting(scout[0]) + ")");
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
                    // Honest sizes (2026-11-11): a raid is all real up to 75, so few raiders go as numbers (5 here), each
                    // taking raids.catch.per_raider (0.1) by the dice of RaidCatch (0.75 to 1.25 of that): the rule is the
                    // range those raiders can take, no longer "at least one" (that was the old 1.5 a raider).
                    int asNumbers = column[0].size() - column[0].members().size();
                    double each = colony.profile().section(com.antcolonies.savanna.species.SavannaNumbers.CATCH)
                            .orElse(com.antcolonies.savanna.species.SavannaNumbers.CatchNumbers.DEFAULT).perRaider();
                    int least = (int) Math.floor(asNumbers * each * 0.75);
                    int most = (int) Math.ceil(asNumbers * each * 1.25);
                    helper.assertTrue(hidden >= least && hidden <= most, "the " + asNumbers + " raiders going as numbers took " + hidden
                            + " termites, not " + least + " to " + most + "; " + column[0].describe());
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
                    // (a colony that lost nobody has nothing to remember: with 5 number raiders the dice may take none)
                    helper.assertTrue(hidden == 0 || termites[0].keepsOff(level, spot), "the termites do not keep off the spot they lost "
                            + hidden + " at");
                    SavannaMod.LOGGER.info("[near catch test] home: {}; {}", column[0].describe(), pile.describe(level));
                })
                .thenSucceed();
    }
}
