package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.watch.BlackBox;
import com.antcolonies.watch.Watch;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The raid rewrite's tests, written before the new raid ({@code docs/plans/raid-rewrite.md}, step 2): one whole-chain
 * test a scene, each checking the new stage rules and nothing of how the raid is built. They read only what stays
 * through the rewrite: the colony's {@code [raid]} diary in its black box ({@link Watch#boxOf}), the insects in the
 * world, the prey pile and the watch rules. No test here names the old {@code Raid} or the new {@code Raid}.
 *
 * <p><b>The diary a raid must keep</b> (the contract these tests read; one line on entering a stage, one on leaving):
 * <pre>
 * [raid] muster: begins, N ant(s) called to X, Y, Z, D blocks out, T termite(s) counted there; led by ID
 * [raid] STAGE: ends after S s, N ant(s): REASON
 * [raid] NEXT STAGE: begins, N ant(s)
 * [raid] done: N home, C termite(s) on the pile, H hurt carried, L let go
 * [raid] no raid to X, Y, Z: no termite there now (the find is forgotten)
 * </pre>
 * The stages, in order: {@code muster}, {@code march out}, {@code halt}, {@code attack}, {@code regroup},
 * {@code march home}, {@code done}. From any stage the raid may turn home (its end line's reason starts
 * {@code turns home:}) and goes on at {@code march home}.
 *
 * <p><b>The rules every scene checks</b> ({@link Book}): the stages come in order, each once; no stage lasts past its
 * deadline (the plan's table: muster 20 s, halt 15 s, attack 2 minutes, regroup 30 s, a march a second a block and
 * half a minute); the halt can be seen (3 s or more) unless the raid turned home; in the attack no raider fights
 * farther than 16 blocks from the spot (the leash); once the attack is over nobody fights; every termite stung is taken
 * off the ground, and what was taken lies on the prey pile; at the end nobody stands about outside and no watch rule
 * broke.
 *
 * <p>Written before the new raid (they failed on the old raid code, which kept no such diary and broke the rules the
 * soak of 2026-11-23 measured); in the {@code raids} category and in full runs since step 3 built {@code colony.Raid}.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleRaidChainTests {
    /** The nest's middle in every scene here (as in the old raid tests). */
    private static final int NEST = 20;
    /** A stage may last this much past its deadline (ticks): the raid works itself out every few ticks. */
    private static final long SLACK = 100;
    /** The leash: in the attack no raider fights farther than this (flat blocks) from the spot. */
    private static final double LEASH = 16.0;

    private static final List<String> STAGES = List.of("muster", "march out", "halt", "attack", "regroup", "march home", "done");
    private static final Pattern STAGE_LINE = Pattern.compile("^\\[raid\\] (muster|march out|halt|attack|regroup|march home|done): (begins|ends after \\d+ s|\\d+ home)(.*)$");
    private static final Pattern BLOCKS_OUT = Pattern.compile("(\\d+) blocks out");

    /** What a scene does to its raid on the way (none of them for the plain scenes). */
    private enum Twist { NONE, EMPTY_AT_MUSTER, NO_WAY_IN, EMPTIES_ON_THE_MARCH, DUSK_AT_THE_SPOT, SCOUT_KILLED_ON_THE_MARCH }

    // ------------------------------------------------------------------ the scenes

    /**
     * Flat ground (the old {@code aScoutLeadsARaidColumn...} and {@code aBigColonyInADeadMound...} scenes): a colony of 40
     * in a month-dead mound, six termite workers feeding under three mud-cased logs about 17 blocks out, the scout
     * command's scout finding them by itself. Every stage in order and in time; the prey reaches the pile.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidOnFlatGroundRunsEveryStageInItsTimeAndBringsItsPreyToThePile(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 3, false, true, Twist.NONE);
    }

    /** The same from a tall dead mound (6 days dead: the raiders come up its chimney and down its hump), a colony of 120. */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidFromATallDeadMoundRunsEveryStageInItsTimeAndBringsItsPreyToThePile(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 6, 120, 3, false, true, Twist.NONE);
    }

    /** The same from a dead mound worn to a low hump (a month dead, as most are), a colony of 120 (50 real). */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidFromALowDeadMoundRunsEveryStageInItsTimeAndBringsItsPreyToThePile(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 120, 3, false, true, Twist.NONE);
    }

    /**
     * Lumpy ground (the user's savanna of 2026-11-08 in small: one-block steps up and one-block holes every few blocks
     * between the nest and the termites, where the old ribbon march piled up): the trail laid by hand over the lumps.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidOverLumpyGroundRunsEveryStageInItsTimeAndBringsItsPreyToThePile(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> x > NEST + 9 && x < 34 ? ((x * 7 + z * 13) % 5 == 0 ? 1 : (x * 3 + z * 5) % 11 == 0 ? -1 : 0) : 0,
                false, 30, 40, 3, true, false, Twist.NONE);
    }

    /**
     * The ridge and the pond (the old {@code aScoutsTrailOverARidgeAndRoundAPond...} scene): a ridge 3 high between the
     * nest and the termites and a pond on the straight way; the scout's own find and line.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidOverARidgeAndRoundAPondRunsEveryStageInItsTimeAndBringsItsPreyToThePile(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> Math.max(0, 3 - Math.abs(x - 33)), true, 30, 60, 3, false, true, Twist.NONE);
    }

    /**
     * An empty spot (the soak of 2026-11-23: half the watched raids went to finds where no termite was; BUGS 168, 202):
     * the colony remembers a find with a trail, but the termites have left the log. No raid sets out, nobody comes out,
     * and the find is forgotten.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "matabele_raid_chain")
    public static void noRaidSetsOutToASpotWhereNoTermiteIsNowAndTheFindIsForgotten(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 1, true, false, Twist.EMPTY_AT_MUSTER);
    }

    /**
     * No way in (BUGS 202, the soak of 2026-11-27: 13 of 27 watched attacks found nothing they might go for, most at a
     * gallery dug in plain ground under a whole log): the termites are there, shut in under a log not eaten through,
     * with no mud anywhere. Majors bite only the termites' mud, never wood or the ground, so no raid sets out, the
     * diary says why, and the find is forgotten.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "matabele_raid_chain")
    public static void noRaidSetsOutToTermitesTheRaidersHaveNoWayInTo(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 1, true, false, Twist.NO_WAY_IN);
    }

    /**
     * The termites leave while the column is on its way: at the halt the raid finds nobody, looks round a short while
     * and goes home empty; no stage hangs, nobody is left out, and the find is forgotten.
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidWhoseTermitesLeftOnTheWayLooksRoundBrieflyAndComesHomeEmpty(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 1, true, false, Twist.EMPTIES_ON_THE_MARCH);
    }

    /**
     * Dusk in the middle of a raid (R7, the user's answer of 2026-11-04): the clock jumps past 19:30 as the attack
     * begins. The raid turns home, what was stung is still taken along, and everyone is in for the night.
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidCaughtByDuskAtTheSpotTurnsHomeWithWhatItStung(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 3, true, false, Twist.DUSK_AT_THE_SPOT);
    }

    /**
     * The scout killed on the march out. Real Megaponera cannot follow the scout's line out without her (Yusuf 2010, as
     * cited; the plan's "the real numbers, checked"): the raid turns home at once, never attacks, and everyone left
     * comes home. The find is kept (the termites are still there).
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true, batch = "matabele_raid_chain")
    public static void aRaidWhoseScoutIsKilledOnTheMarchOutTurnsHomeAndNobodyIsLeftOut(GameTestHelper helper) {
        chain(helper, 45, (x, z) -> 0, false, 30, 40, 3, true, false, Twist.SCOUT_KILLED_ON_THE_MARCH);
    }

    /**
     * Termites in the open that run (the soak: four raids stung 8 to 29 termites and carried none; raiders chased 37
     * blocks off and went on stinging while "regrouping"): six termite workers on open ground by the spot, free to flee.
     * Whatever is stung is carried, nobody fights past the leash, nobody fights once the attack is over.
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_chain")
    public static void everyTermiteStungInTheOpenIsCarriedHomeAndNobodyFightsPastTheSpot(GameTestHelper helper) {
        chain(helper, 65, (x, z) -> 0, false, 30, 40, 0, true, false, Twist.NONE);
    }

    /**
     * The raid rewrite, step 4 (the plan's "Done when"): a raid watched and a raid out of sight to the same find bring
     * home the same catch, within the dice. Flat ground, six termite workers in the open, a colony of 100 (a raid of
     * 75): first a raid with real ants, which stings and carries what stands there; then six termites again and the
     * colony far from every watcher, the same raid machine without bodies, its catch by the one rule
     * ({@code RaidCatch}: about 0.1 a raider by the dice, here of the termites standing there). Both bring termites
     * home and the two catches differ by no more than 3 (my pick: the rule's dice for 75 raiders run from 5 to 9, of
     * six termites to take).
     */
    @GameTest(template = "arena", timeoutTicks = 24000, skyAccess = true, batch = "matabele_raid_same_catch")
    public static void aWatchedRaidAndARaidOutOfSightBringHomeTheSameCatch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 8.0); // (the morning raid hours)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        MatabeleScenes.deadMound(helper, NEST, NEST, 30);
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        Runnable six = () -> {
            for (int i = 0; i < 6; i++) {
                termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 37 + i % 3, SURFACE, NEST + 3 + 2 * (i / 3)));
            }
        };
        six.run();
        BlockPos at = termites.get(0).blockPosition();
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, 100);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        com.antcolonies.savanna.colony.Raid[] near = new com.antcolonies.savanna.colony.Raid[1];
        int[] stage = {0};
        long[] stageAt = new long[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (now < 40) {
                        return;
                    }
                    MatabeleEntity leader = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                            .filter(ant -> ant.hasMajorBody() && !raids.scouts().isScouting(ant.getUUID())).findFirst().orElse(null);
                    helper.assertTrue(leader != null, "no real major to be the scout");
                    near[0] = raids.raidForTests(level, at, 18, trail(level, colony.getCenter(), at), leader.getUUID());
                    helper.assertTrue(near[0] != null && !near[0].goesAsNumbers(), "no watched raid set out; " + raids.describe(level));
                    stage[0] = 1;
                    stageAt[0] = now;
                }
                case 1 -> {
                    if (near[0].stage() != com.antcolonies.savanna.colony.Raid.Stage.DONE || raids.column() != null) {
                        stageAt[0] = now;
                        return;
                    }
                    if (now - stageAt[0] < 400) {
                        return; // (a little while to go in)
                    }
                    helper.assertTrue(near[0].turned() == null, "the watched raid turned home: " + near[0].endedWhy());
                    // the same find again, six termites there, the colony now far from every watcher
                    termites.forEach(termite -> termite.discard());
                    termites.clear();
                    six.run();
                    raids.forgetFindsForTests();
                    raids.rememberForTests(at, 18, level.getGameTime());
                    colony.population().setOnForTests(true);
                    colony.away().setOnForTests(true);
                    colony.away().setFarForTests(true);
                    colony.away().setNearForTests(false);
                    colony.away().setGraceForTests(40L);
                    stage[0] = 2;
                    stageAt[0] = now;
                }
                case 2 -> {
                    com.antcolonies.savanna.colony.Raid last = raids.lastRaid();
                    if (last == null || last == near[0] || raids.column() != null) {
                        helper.assertTrue(now - stageAt[0] < 10000, "no raid out of sight went and came home after the watched one; " + raids.describe(level));
                        return;
                    }
                    helper.assertTrue(last.wasOutOfSight() && last.members().isEmpty(), "the second raid was not out of sight: " + last.endedWhy());
                    helper.assertTrue(last.stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE && last.turned() == null && last.find().at().equals(at),
                            "the raid out of sight did not go through to done at the same find: " + last.endedWhy());
                    int watched = near[0].carried() + near[0].hiddenCatch();
                    int unseen = last.carried() + last.hiddenCatch();
                    SavannaMod.LOGGER.info("[same catch test] watched: a raid of {} brought {}; out of sight: a raid of {} brought {}",
                            near[0].size(), watched, last.size(), unseen);
                    helper.assertTrue(watched > 0 && unseen > 0, "a raid came home with nothing: watched " + watched + ", out of sight " + unseen);
                    helper.assertTrue(Math.abs(watched - unseen) <= 3, "to the same find the watched raid (of " + near[0].size() + ") brought home "
                            + watched + " termite(s), the raid out of sight (of " + last.size() + ") " + unseen);
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    // ------------------------------------------------------------------ the one driver

    /**
     * Builds a scene and runs its raid under the {@link Book}.
     *
     * @param size       the ground shaped (blocks a side)
     * @param ground     the ground's height over the plain at (x, z)
     * @param pond       the old ridge scene's pond on the straight way
     * @param deadDays   how long the mound has been dead (6 a tall hump, 30 a low one)
     * @param workers    the colony's size
     * @param logs       mud-cased logs with two termite workers under each (0: six termite workers in the open instead)
     * @param byHand     the find and its trail put in by hand ({@code raidForTests}); else the scout command's scout finds it
     * @param morning    a moving clock from 7:00 (else the test's still 7:00)
     */
    private static void chain(GameTestHelper helper, int size, IntBinaryOperator ground, boolean pond, int deadDays, int workers, int logs,
            boolean byHand, boolean morning, Twist twist) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        if (morning) {
            TestClock.moving(helper, 7.0);
        }
        TestScenes.shapeGround(helper, size, (x, z) -> ground.applyAsInt(x, z));
        if (pond) {
            for (int x = 27; x <= 29; x++) {
                for (int z = 15; z <= 27; z++) {
                    helper.setBlock(x, SURFACE - 1, z, Blocks.WATER);
                    helper.setBlock(x, SURFACE - 2, z, Blocks.WATER);
                }
            }
        }
        MatabeleScenes.deadMound(helper, NEST, NEST, deadDays);
        // (the mound's own dead tree off the ground past the hump: it may lie across the logs, and a log with a trunk
        // over it is no feeding spot a major can break into from the open; as the old ridge scene did)
        for (int x = 27; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = SURFACE - 1; y < SURFACE + 40; y++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir() && !state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.WATER)) {
                        helper.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        if (logs == 0) {
            for (int i = 0; i < 6; i++) {
                termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 37 + i % 3, SURFACE, NEST + 3 + 2 * (i / 3)));
            }
        } else {
            for (int i = 0; i < logs; i++) {
                termites.addAll(MatabeleScenes.feedingUnderALog(helper, 36, NEST + 3 + 2 * i));
            }
        }
        BlockPos at = termites.get(0).blockPosition();
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, workers);
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(raids != null && pile != null, "a Matabele colony has no raids part or no prey pile");
        Watch.watchForTests(colony);
        int pileBefore = pile.broughtIn();
        Book book = new Book(helper, level, colony, raids, at, termites);
        UUID[] scout = {null};
        boolean[] twisted = {false};
        helper.onEachTick(() -> {
            book.tick();
            if (twisted[0]) {
                return;
            }
            switch (twist) {
                case EMPTIES_ON_THE_MARCH -> {
                    if (book.in("march out")) {
                        termites.forEach(termite -> termite.discard()); // (gone down their gallery and away)
                        twisted[0] = true;
                    }
                }
                case DUSK_AT_THE_SPOT -> {
                    if (book.in("attack") && termites.stream().anyMatch(TermiteWorkerEntity::isParalysed)) {
                        TestClock.still(helper, 19.6); // (past 19:30: a raid still out turns home)
                        twisted[0] = true;
                    }
                }
                case SCOUT_KILLED_ON_THE_MARCH -> {
                    if (book.in("march out") && level.getGameTime() - book.began("march out") >= 100 && scout[0] != null
                            && level.getEntity(scout[0]) instanceof MatabeleEntity leader && leader.isAlive()) {
                        leader.kill();
                        twisted[0] = true;
                    }
                }
                default -> {
                }
            }
        });
        var sequence = helper.startSequence().thenIdle(40);
        if (byHand) {
            sequence = sequence.thenExecute(() -> {
                if (twist == Twist.EMPTY_AT_MUSTER) {
                    termites.forEach(termite -> termite.discard()); // (the termites have left the log)
                }
                if (twist == Twist.NO_WAY_IN) {
                    // (the log not eaten through anywhere: whole wood over the gallery where the scene put mud casts)
                    for (int x = 37; x <= 38; x++) {
                        helper.setBlock(x, SURFACE, NEST + 3, Blocks.OAK_LOG.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
                    }
                    helper.assertTrue(termites.stream().allMatch(termite -> termite.isAlive()
                                    && com.antcolonies.fight.Fights.isShutIn(level, termite.blockPosition())),
                            "the test's termites are not shut in under the whole log");
                }
                MatabeleEntity leader = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                        .filter(ant -> ant.hasMajorBody() && !raids.scouts().isScouting(ant.getUUID())).findFirst().orElse(null);
                helper.assertTrue(leader != null, "no real major to be the scout");
                scout[0] = leader.getUUID();
                raids.raidForTests(level, at, 3 * termites.size(), trail(level, colony.getCenter(), at), leader.getUUID());
            });
        } else {
            sequence = sequence.thenExecute(() -> {
                scoutsHeadToward(helper, colony, at);
                helper.assertTrue(raids.scouts().sendNow(level) != null, "the scout command found no fit major");
            });
        }
        if (twist == Twist.EMPTY_AT_MUSTER || twist == Twist.NO_WAY_IN) {
            String why = twist == Twist.NO_WAY_IN ? "no way in" : "no termite";
            sequence.thenIdle(600)
                    .thenExecute(() -> {
                        helper.assertTrue(!book.sawAny(), "a raid set out to a spot where " + why + " is: " + book.story());
                        helper.assertTrue(book.sawLine("no raid to") && book.sawLine(why),
                                "the diary does not say why no raid went (\"no raid to ...: " + why + " ...\"); " + raids.describe(level));
                        helper.assertTrue(raids.finds().stream().noneMatch(find -> find.at().closerThan(at, 6)),
                                "the find with no termites at it is still remembered; " + raids.describe(level));
                        book.nobodyOutside("after the raid that never went");
                    })
                    .thenSucceed();
            return;
        }
        long[] doneAt = {0};
        sequence.thenWaitUntil(() -> helper.assertTrue(book.sawAny(), "no raid mustered yet; " + raids.describe(level)))
                .thenWaitUntil(() -> helper.assertTrue(book.began("done") >= 0, "the raid is not done yet: " + book.story() + "; " + raids.describe(level)))
                .thenExecute(() -> doneAt[0] = level.getGameTime())
                .thenIdle(400) // (a little while to go in)
                // (laying the prey down takes longer from a tall mound: 70 ants up 11 blocks of wall and down the chimney in
                // single file; a minute at most, well inside the 2 minutes after which a carrier with no way to the pile
                // has its termites laid there out of sight, so a pass means they were carried in)
                .thenWaitUntil(() -> helper.assertTrue(twist != Twist.NONE && twist != Twist.DUSK_AT_THE_SPOT
                                || pile.broughtIn() - pileBefore >= 1 || level.getGameTime() - doneAt[0] >= 1200,
                        "the carriers have not laid their prey down yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    SavannaMod.LOGGER.info("[raid chain] {}: {}", twist, book.story());
                    book.judge(twist != Twist.NONE);
                    int laid = pile.broughtIn() - pileBefore;
                    switch (twist) {
                        case NONE -> helper.assertTrue(laid >= 1, "the raid laid no termite on the prey pile: " + book.story());
                        case EMPTIES_ON_THE_MARCH -> {
                            helper.assertTrue(laid == 0, "an empty raid laid " + laid + " termite(s) on the pile");
                            helper.assertTrue(book.began("halt") >= 0 && book.began("march home") >= 0
                                            && book.began("march home") - book.began("halt") <= 300 + 200 + SLACK,
                                    "at an empty spot the raid should halt, look round 10 s at most and go home: " + book.story());
                            helper.assertTrue(raids.finds().stream().noneMatch(find -> find.at().closerThan(at, 6)),
                                    "the spot found empty is still remembered; " + raids.describe(level));
                        }
                        case DUSK_AT_THE_SPOT -> {
                            helper.assertTrue(book.turnedHome("dusk"), "the raid did not turn home for dusk: " + book.story());
                            helper.assertTrue(laid >= 1, "the raid turned home at dusk without the termites it had stung: " + book.story());
                        }
                        case SCOUT_KILLED_ON_THE_MARCH -> {
                            helper.assertTrue(twisted[0], "the scout was never killed on the march");
                            helper.assertTrue(book.turnedHome("scout"), "the raid did not turn home when its scout died: " + book.story());
                            helper.assertTrue(book.began("attack") < 0, "the raid attacked without its scout: " + book.story());
                            helper.assertTrue(raids.finds().stream().anyMatch(find -> find.at().closerThan(at, 6)),
                                    "the find was forgotten though the raid never reached it; " + raids.describe(level));
                        }
                        default -> {
                        }
                    }
                })
                .thenSucceed();
    }

    /** A trail from the entrance to the find, a point every 2 blocks on the ground's top (as the old tests laid it). */
    private static List<BlockPos> trail(ServerLevel level, BlockPos entrance, BlockPos at) {
        List<BlockPos> trail = new ArrayList<>();
        int steps = (int) Math.ceil(Math.hypot(at.getX() - entrance.getX(), at.getZ() - entrance.getZ()) / 2.0);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = entrance.getX() + (int) Math.round((at.getX() - entrance.getX()) * t);
            int z = entrance.getZ() + (int) Math.round((at.getZ() - entrance.getZ()) * t);
            trail.add(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, entrance.getY(), z)));
        }
        return trail;
    }

    /** Every real major's own scouting way set toward the termites (a new scout's way is random; as the old tests did). */
    private static void scoutsHeadToward(GameTestHelper helper, Colony colony, BlockPos to) {
        BlockPos center = colony.getCenter();
        double way = Math.atan2(to.getZ() - center.getZ(), to.getX() - center.getX());
        for (MatabeleEntity ant : TestIsolation.insectsOf(helper.getLevel(), colony, MatabeleEntity.class)) {
            if (ant.hasMajorBody()) {
                ant.scoutMemory().setHeading(way);
            }
        }
    }

    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    // ------------------------------------------------------------------ the book: the stage rules, read from outside

    /**
     * Reads one raid's stages from the colony's diary and watches the world beside it; {@link #judge} gives the verdict.
     * (Open to the other raid tests: a raid out of sight keeps the same diary, {@code MatabeleAwayTests}.)
     */
    static final class Book {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final Colony colony;
        private final Raids raids;
        private final BlockPos at;
        private final List<TermiteWorkerEntity> termites;
        /** Diary lines already read (tick and text), so a line is taken once. */
        private final Set<String> read = new HashSet<>();
        private final List<String> lines = new ArrayList<>();
        /** The tick each stage began and ended, and its end line's reason. */
        private final Map<String, Long> began = new HashMap<>();
        private final Map<String, Long> ended = new HashMap<>();
        private final Map<String, String> reasons = new HashMap<>();
        private final List<String> order = new ArrayList<>();
        private final List<String> faults = new ArrayList<>();
        private final Set<UUID> stung = new HashSet<>();
        private final Map<UUID, Integer> pastLeash = new HashMap<>();
        private final Map<UUID, Integer> fightingAfter = new HashMap<>();
        private int blocksOut = 60;

        Book(GameTestHelper helper, ServerLevel level, Colony colony, Raids raids, BlockPos at, List<TermiteWorkerEntity> termites) {
            this.helper = helper;
            this.level = level;
            this.colony = colony;
            this.raids = raids;
            this.at = at;
            this.termites = termites;
        }

        void tick() {
            long now = level.getGameTime();
            if (now % 5 == 0) {
                for (BlackBox.Line line : Watch.boxOf(colony).lines()) {
                    if (line.text().startsWith("[raid]") && read.add(line.tick() + "|" + line.text())) {
                        take(line.tick(), line.text());
                    }
                }
            }
            for (TermiteWorkerEntity termite : termites) {
                if (termite.isParalysed()) {
                    stung.add(termite.getUUID());
                }
            }
            String stage = stage();
            boolean attack = "attack".equals(stage);
            boolean after = stage != null && STAGES.indexOf(stage) > STAGES.indexOf("attack");
            for (MatabeleEntity ant : TestIsolation.insectsOf(level, colony, MatabeleEntity.class)) {
                boolean fights = ant.isAlive() && ant.getFightSpot() != null;
                if (attack && fights && flat(ant.blockPosition(), at) > LEASH) {
                    if (pastLeash.merge(ant.getUUID(), 1, Integer::sum) == 40) {
                        faults.add("in the attack a raider fought " + Math.round(flat(ant.blockPosition(), at)) + " blocks from the spot (the leash is "
                                + (int) LEASH + "), at " + ant.blockPosition().toShortString());
                    }
                } else {
                    pastLeash.remove(ant.getUUID());
                }
                if (after && fights) {
                    if (fightingAfter.merge(ant.getUUID(), 1, Integer::sum) == 60) {
                        faults.add("a raider still fought 3 s into " + stage + ", at " + ant.blockPosition().toShortString() + " doing " + ant.runningGoals());
                    }
                } else {
                    fightingAfter.remove(ant.getUUID());
                }
            }
            // a stage past its deadline fails at once, with the story so far (a hung raid must not wait for the test's own time)
            if (stage != null && !"done".equals(stage) && now - began.get(stage) > deadline(stage) + SLACK) {
                helper.fail("the raid hung in " + stage + " for " + (now - began.get(stage)) / 20 + " s (its deadline " + deadline(stage) / 20
                        + " s): " + story() + "; " + raids.describe(level));
            }
        }

        private void take(long tick, String text) {
            lines.add(text);
            Matcher line = STAGE_LINE.matcher(text);
            if (!line.matches()) {
                return;
            }
            String stage = line.group(1);
            if (line.group(2).equals("begins") || stage.equals("done")) {
                if (began.putIfAbsent(stage, tick) != null) {
                    faults.add("the stage " + stage + " began twice");
                }
                order.add(stage);
                Matcher out = BLOCKS_OUT.matcher(text);
                if (stage.equals("muster") && out.find()) {
                    blocksOut = Integer.parseInt(out.group(1));
                }
            } else {
                ended.putIfAbsent(stage, tick);
                reasons.putIfAbsent(stage, line.group(3));
            }
        }

        /** The longest a stage may last (ticks): the plan's table. */
        private long deadline(String stage) {
            return switch (stage) {
                case "muster" -> 400;
                case "halt" -> 300;
                case "attack" -> 2400;
                case "regroup" -> 600;
                default -> 20L * blocksOut + 600; // (a march: a second a block and half a minute)
            };
        }

        /** The stage the raid is in now (null before its muster). */
        String stage() {
            return order.isEmpty() ? null : order.get(order.size() - 1);
        }

        boolean in(String stage) {
            return stage.equals(stage());
        }

        long began(String stage) {
            return began.getOrDefault(stage, -1L);
        }

        boolean sawAny() {
            return !order.isEmpty();
        }

        boolean sawLine(String part) {
            return lines.stream().anyMatch(line -> line.contains(part));
        }

        /** Whether a stage ended by turning home for this reason. */
        boolean turnedHome(String why) {
            return reasons.values().stream().anyMatch(reason -> reason.contains("turns home") && reason.contains(why));
        }

        String story() {
            StringBuilder story = new StringBuilder();
            for (int i = 0; i < order.size(); i++) {
                String stage = order.get(i);
                long until = i + 1 < order.size() ? began.get(order.get(i + 1)) : level.getGameTime();
                story.append(i == 0 ? "" : " > ").append(stage);
                if (!stage.equals("done")) {
                    story.append(' ').append((until - began.get(stage)) / 20).append(" s").append(reasons.containsKey(stage) ? " (" + reasons.get(stage).trim() + ")" : "");
                }
            }
            return order.isEmpty() ? "no stage line in the diary (" + lines.size() + " [raid] lines)" : story.toString();
        }

        void nobodyOutside(String when) {
            for (MatabeleEntity ant : TestIsolation.insectsOf(level, colony, MatabeleEntity.class)) {
                if (ant.isAlive() && !ant.isCrippled() && !ant.isDowned() && !raids.scouts().isScouting(ant.getUUID())) {
                    helper.assertTrue(flat(ant.blockPosition(), colony.getCenter()) <= 8.0 || colony.nest().isInside(level, ant.blockPosition()),
                            when + " a raider stands about outside at " + ant.blockPosition().toShortString() + ", "
                                    + Math.round(flat(ant.blockPosition(), colony.getCenter())) + " from the nest, doing " + ant.runningGoals());
                    helper.assertTrue(ant.getFightSpot() == null, when + " a raider still holds a fight order, at " + ant.blockPosition().toShortString());
                }
            }
        }

        /** The verdict once the raid is done; {@code turned}: this scene's raid may turn home (stages skipped, no halt to see). */
        void judge(boolean turned) {
            // the stages in order, each once, none skipped unless the raid turned home
            int last = -1;
            for (String stage : order) {
                int index = STAGES.indexOf(stage);
                helper.assertTrue(index > last, "the stages came out of order: " + story());
                boolean skipped = index > last + 1;
                helper.assertTrue(!skipped || stage.equals("march home") && reasons.values().stream().anyMatch(reason -> reason.contains("turns home")),
                        "a stage was skipped without the raid turning home: " + story());
                last = index;
            }
            helper.assertTrue(order.contains("march home") && order.contains("done"), "the raid never marched home to done: " + story());
            for (int i = 0; i + 1 < order.size(); i++) {
                String stage = order.get(i);
                long lasted = began.get(order.get(i + 1)) - began.get(stage);
                helper.assertTrue(lasted <= deadline(stage) + SLACK, stage + " lasted " + lasted / 20 + " s, past its deadline of " + deadline(stage) / 20 + " s: " + story());
                helper.assertTrue(ended.containsKey(stage), "the diary has no end line for " + stage + " (with its count and its reason)");
            }
            if (!turned) {
                helper.assertTrue(began("halt") >= 0 && began("attack") - began("halt") >= 60,
                        "no halt to see before the attack (3 s at least, the column's tail closing up): " + story());
            }
            helper.assertTrue(faults.isEmpty(), faults.size() + " fault(s): " + faults);
            // every termite stung was taken off the ground, and what was taken lies on the pile
            long left = termites.stream().filter(termite -> termite.isAlive() && termite.isParalysed()).count();
            helper.assertTrue(left == 0, left + " stung termite(s) were left lying at the spot (" + stung.size() + " stung in all): " + story());
            nobodyOutside("after the raid");
            List<Watch.Breach> broken = Watch.breaches(colony);
            helper.assertTrue(broken.isEmpty(), broken.size() + " watch rule(s) broken in the raid: " + broken);
        }
    }

    /**
     * The raid rewrite, step 5 (BUGS 158, 198, 281, 303): a find right beside the nest. Every point of the scout's short
     * line lies within the first step out of the entrance, so the march's line had only its start left and making the
     * raid threw an error that stopped the colony's whole tick. The line now always has two points (the start and the
     * find), the raid sets out and comes to its end like any other.
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true, batch = "matabele_raid_beside_nest")
    public static void aRaidToAFindRightBesideTheNestSetsOutWithoutACrash(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 8.0); // (the morning raid hours)
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        MatabeleScenes.deadMound(helper, NEST, NEST, 30);
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, 40);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        BlockPos at = colony.getCenter().offset(2, 0, 0);
        MatabeleScenes.termitesAt(helper, at);
        com.antcolonies.savanna.colony.Raid[] raid = new com.antcolonies.savanna.colony.Raid[1];
        helper.onEachTick(() -> {
            if (helper.getTick() < 40 || raid[0] != null) {
                return;
            }
            MatabeleEntity leader = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                    .filter(MatabeleEntity::hasMajorBody).findFirst().orElse(null);
            helper.assertTrue(leader != null, "no real major to lead the raid");
            raid[0] = raids.raidForTests(level, at, 18, List.of(colony.getCenter(), at), leader.getUUID());
            helper.assertTrue(raid[0] != null, "no raid set out to the find beside the nest; " + raids.describe(level));
        });
        helper.succeedWhen(() -> helper.assertTrue(raid[0] != null && raid[0].stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE,
                "the raid to the find beside the nest has not come to its end; " + raids.describe(level)));
    }

    // ------------------------------------------------------------------ review round 11, the grade A findings

    /**
     * R11-1 (BUGS 316): a termite colony's feeding place in land that is not loaded is never read. A Matabele scout
     * setting out asks every termite colony within about 133 blocks for its by-day feeding places, and that question
     * read the gallery's blocks wherever they lay: a chunk read from disk or made new on the spot, a freeze of up to
     * seconds. Here a living colony has a feeding tunnel 4,000 blocks off; after the scout's question that land is still
     * not loaded.
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true, batch = "matabele_unloaded_gallery")
    public static void aFeedingPlaceInLandNotLoadedIsNeverRead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony termites = TermiteScenes.livingMound(helper, NEST, NEST, 6, 7L);
        helper.assertTrue(termites != null, "the living mound could not go in");
        BlockPos far = helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)).offset(4000, 0, 4000);
        helper.assertTrue(!com.antcolonies.colony.LandReady.at(level, far), "the land 4,000 blocks off is loaded before the test began");
        // (the test's own check: a block read in land not loaded does load it, and this test can see that)
        BlockPos other = far.offset(1000, 0, 0);
        level.getBlockState(other);
        helper.assertTrue(com.antcolonies.colony.LandReady.at(level, other), "a block read 5,000 blocks off left its land not loaded: this test cannot see a read");
        List<BlockPos> tunnel = List.of(far.below(2).west(2), far.below(2).west());
        List<BlockPos> gallery = List.of(far.below(), far.below().east());
        termites.nest().foodWays().addNewTunnelForTests(new com.antcolonies.colony.FoodWays.NewTunnel(
                new com.antcolonies.colony.NestPlan.ForagingTunnel(tunnel, far.west(), net.minecraft.core.Direction.EAST), far, gallery));
        // (asked at once, as a scout asks a termite colony that is not being lived through: one out of range keeps its
        // record of its ways to food, and that is when a gallery of its lies in land not loaded)
        com.antcolonies.savanna.colony.FeedingSpots.byDayPlaces(level, termites);
        helper.assertTrue(!com.antcolonies.colony.LandReady.at(level, far),
                "asking for the by-day feeding places loaded the land a far one lies in (the scout's question at every trip's start)");
        com.antcolonies.savanna.colony.FeedingSpots.of(level, termites);
        termites.food().isFeedableFromInside(level, far);
        termites.nest().isInside(level, gallery.get(0));
        helper.assertTrue(!com.antcolonies.colony.LandReady.at(level, far),
                "asking whether a far feeding place can be eaten from inside loaded its land");
        helper.succeed();
    }

    /**
     * R11-2 (BUGS 317): a scout with no way on asks its legs for a route only now and then, never every tick (BUGS 76's
     * rule, which the raid goal and the way-home goal keep). A scout on its trip is shut in a glass box: every route it
     * asks for fails. In any 10 ticks it asks for at most 19 routes (one try of up to 8 dodges, and one more set off by
     * something new it smelt or noticed, with 3 hops; my pick of the bound); before, it asked for 40 and more.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "matabele_scout_no_way")
    public static void aScoutWithNoWayOnAsksForARouteOnlyNowAndThen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 8.0);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        MatabeleScenes.deadMound(helper, NEST, NEST, 30);
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, 40);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        MatabeleEntity[] scout = new MatabeleEntity[1];
        long[] boxedAt = {-1};
        int[] asksWhenBoxed = {0};
        java.util.ArrayDeque<Integer> lastTen = new java.util.ArrayDeque<>();
        int[] most = {0};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (now < 40) {
                return;
            }
            if (scout[0] == null) {
                scout[0] = raids.scouts().sendNow(level);
                helper.assertTrue(scout[0] != null, "the scout command found no fit major");
                return;
            }
            MatabeleEntity ant = scout[0];
            if (boxedAt[0] < 0) {
                if (!raids.scouts().isScouting(ant.getUUID()) || ant.scoutGoalForTests().routeAsksForTests() == 0) {
                    return; // (not off on its trip yet)
                }
                // a glass box on the plain, one cell of air inside: no way out of it
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            helper.setBlock(38 + dx, SURFACE + dy, 8 + dz, dx == 0 && dz == 0 && dy == 0 ? Blocks.AIR : Blocks.GLASS);
                        }
                    }
                }
                BlockPos cell = helper.absolutePos(new BlockPos(38, SURFACE, 8));
                ant.getNavigator().stop();
                ant.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
                boxedAt[0] = now;
                asksWhenBoxed[0] = ant.scoutGoalForTests().routeAsksForTests();
                lastTen.clear();
                return;
            }
            int asks = ant.scoutGoalForTests().routeAsksForTests();
            lastTen.addLast(asks);
            if (lastTen.size() > 10) {
                int inTen = asks - lastTen.removeFirst();
                most[0] = Math.max(most[0], inTen);
                helper.assertTrue(inTen <= 19, "a scout with no way on asked its legs for " + inTen + " routes in 10 ticks ("
                        + (now - boxedAt[0]) + " ticks after it was shut in): a route search every tick");
            }
            if (now - boxedAt[0] >= 200 || !raids.scouts().isScouting(ant.getUUID()) && now - boxedAt[0] >= 20) {
                helper.assertTrue(asks - asksWhenBoxed[0] >= 2, "the scout shut in the box never asked for a route (the test proves nothing): "
                        + (asks - asksWhenBoxed[0]) + " ask(s); " + raids.describe(level));
                SavannaMod.LOGGER.info("[scout no way test] {} route(s) asked for in {} ticks shut in, at most {} in any 10 ticks",
                        asks - asksWhenBoxed[0], now - boxedAt[0], most[0]);
                helper.succeed();
            }
        });
    }

    /**
     * R11-4 (BUGS 319): a raid a watcher is near keeps its bodies when its nest's own land goes out of range. The
     * colony counts as away then, and the raid was folded into numbers a minute later whoever was looking: every ant
     * vanished in front of the player. Here the colony goes away in mid-march with a watcher at the spot: 10 s later the
     * raid still has its real ants. With the watcher gone, the same raid goes on as numbers as before.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_raid_watched_far")
    public static void aRaidAWatcherIsNearKeepsItsBodiesWhenItsNestGoesOutOfRange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 8.0);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        MatabeleScenes.deadMound(helper, NEST, NEST, 30);
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            termites.addAll(MatabeleScenes.feedingUnderALog(helper, 36, NEST + 3 + 2 * i));
        }
        BlockPos at = termites.get(0).blockPosition();
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, 40);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        com.antcolonies.savanna.colony.Raid[] raid = new com.antcolonies.savanna.colony.Raid[1];
        int[] step = {0};
        long[] stepAt = new long[1];
        helper.onEachTick(() -> {
            long now = helper.getTick();
            switch (step[0]) {
                case 0 -> {
                    if (now < 40) {
                        return;
                    }
                    MatabeleEntity leader = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                            .filter(ant -> ant.hasMajorBody() && !raids.scouts().isScouting(ant.getUUID())).findFirst().orElse(null);
                    helper.assertTrue(leader != null, "no real major to be the scout");
                    raid[0] = raids.raidForTests(level, at, 18, trail(level, colony.getCenter(), at), leader.getUUID());
                    helper.assertTrue(raid[0] != null && !raid[0].goesAsNumbers(), "no watched raid set out; " + raids.describe(level));
                    step[0] = 1;
                    stepAt[0] = now;
                }
                case 1 -> {
                    helper.assertTrue(now - stepAt[0] < 4000, "the raid never marched out; " + raids.describe(level));
                    if (raid[0].stage() != com.antcolonies.savanna.colony.Raid.Stage.MARCH_OUT) {
                        return;
                    }
                    // the nest's land out of range (as when its chunk unloads), a watcher by the raid
                    colony.away().setOnForTests(true);
                    colony.away().setAwayForTests(true);
                    colony.away().setGraceForTests(40L);
                    colony.away().setWatcherForTests(at);
                    step[0] = 2;
                    stepAt[0] = now;
                }
                case 2 -> {
                    helper.assertTrue(!raid[0].goesAsNumbers(), "the raid a watcher stands by was folded into numbers " + (now - stepAt[0])
                            + " ticks after its nest went out of range; " + raids.describe(level));
                    if (now - stepAt[0] < 200 && raid[0].stage() != com.antcolonies.savanna.colony.Raid.Stage.DONE) {
                        return;
                    }
                    helper.assertTrue(raid[0].stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE
                                    || !TestIsolation.insectsOf(level, colony, MatabeleEntity.class).isEmpty(),
                            "the watched raid has no real ant left; " + raids.describe(level));
                    if (raid[0].stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE) {
                        helper.succeed(); // (home already: nothing left to fold)
                        return;
                    }
                    colony.away().setWatcherForTests(null); // (nobody looks any more)
                    step[0] = 3;
                    stepAt[0] = now;
                }
                case 3 -> {
                    if (raid[0].goesAsNumbers() || raid[0].stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE) {
                        helper.succeed();
                        return;
                    }
                    helper.assertTrue(now - stepAt[0] < 200, "with nobody watching, the raid of a colony out of range still has bodies after 10 s; "
                            + raids.describe(level));
                }
                default -> {
                }
            }
        });
    }

    /**
     * R11-5 (BUGS 320): a raid out as numbers takes no termite standing in front of somebody who can see the spot. A
     * player arriving right beside an unseen raid (a teleport, a login) meets a raid that stays numbers (ants would be
     * seen popping in), and its hidden catch carried off the real termites in the open: termites vanishing with no ant
     * in sight. With a watcher at the spot as the raid halts there, every termite in the open is still standing when
     * the raid is home; its catch comes from under the mud only.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_raid_unseen_catch")
    public static void aRaidOutOfSightTakesNoTermiteStandingInFrontOfAWatcher(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TestClock.still(helper, 8.0);
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        MatabeleScenes.deadMound(helper, NEST, NEST, 30);
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), 37 + i % 3, SURFACE, NEST + 3 + 2 * (i / 3)));
        }
        BlockPos at = termites.get(0).blockPosition();
        Colony colony = MatabeleScenes.colony(helper, NEST + 6, NEST, 100);
        Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
        boolean[] watched = {false};
        long[] began = {-1};
        long[] standingThen = {-1};
        java.util.function.LongSupplier standingNow = () -> termites.stream()
                .filter(termite -> termite.isAlive() && !termite.isRemoved() && !termite.isParalysed()).count();
        helper.onEachTick(() -> {
            long now = helper.getTick();
            if (now < 40) {
                return;
            }
            if (began[0] < 0) {
                raids.forgetFindsForTests();
                raids.rememberForTests(at, 18, level.getGameTime());
                colony.population().setOnForTests(true);
                colony.away().setOnForTests(true);
                colony.away().setFarForTests(true);
                colony.away().setNearForTests(false);
                colony.away().setGraceForTests(40L);
                began[0] = now;
                return;
            }
            com.antcolonies.savanna.colony.Raid out = raids.column();
            if (out != null && !watched[0] && (out.stage() == com.antcolonies.savanna.colony.Raid.Stage.HALT
                    || out.stage() == com.antcolonies.savanna.colony.Raid.Stage.MARCH_OUT && out.blocksOutForTests() >= 12)) {
                helper.assertTrue(out.goesAsNumbers(), "the raid of a colony far from every watcher has bodies; " + raids.describe(level));
                colony.away().setWatcherForTests(at); // (somebody is suddenly there, inside the 50 blocks: it stays numbers)
                watched[0] = true;
                standingThen[0] = standingNow.getAsLong();
                helper.assertTrue(standingThen[0] >= 3, "only " + standingThen[0] + " termite(s) stand in the open as the raid comes (the scene wants some)");
            }
            com.antcolonies.savanna.colony.Raid last = raids.lastRaid();
            if (last == null || out != null) {
                helper.assertTrue(now - began[0] < 10000, "no raid out of sight went and came home; " + raids.describe(level));
                return;
            }
            helper.assertTrue(watched[0], "the raid was never seen at its halt (the test proves nothing): " + last.endedWhy());
            helper.assertTrue(last.wasOutOfSight() && last.stage() == com.antcolonies.savanna.colony.Raid.Stage.DONE,
                    "the raid was not out of sight to its end: " + last.endedWhy());
            long standing = standingNow.getAsLong();
            helper.assertTrue(standing == standingThen[0], "a raid nobody could see took " + (standingThen[0] - standing) + " of the "
                    + standingThen[0] + " termites standing in the open in front of a watcher; " + raids.describe(level));
            helper.succeed();
        });
    }
}
