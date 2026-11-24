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
    private enum Twist { NONE, EMPTY_AT_MUSTER, EMPTIES_ON_THE_MARCH, DUSK_AT_THE_SPOT, SCOUT_KILLED_ON_THE_MARCH }

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
                MatabeleEntity leader = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                        .filter(ant -> ant.hasMajorBody() && !raids.isScouting(ant.getUUID())).findFirst().orElse(null);
                helper.assertTrue(leader != null, "no real major to be the scout");
                scout[0] = leader.getUUID();
                raids.raidForTests(level, at, 3 * termites.size(), trail(level, colony.getCenter(), at), leader.getUUID());
            });
        } else {
            sequence = sequence.thenExecute(() -> {
                scoutsHeadToward(helper, colony, at);
                helper.assertTrue(raids.sendNow(level) != null, "the scout command found no fit major");
            });
        }
        if (twist == Twist.EMPTY_AT_MUSTER) {
            sequence.thenIdle(600)
                    .thenExecute(() -> {
                        helper.assertTrue(!book.sawAny(), "a raid set out to a spot where no termite is: " + book.story());
                        helper.assertTrue(book.sawLine("no raid to") && book.sawLine("no termite"),
                                "the diary does not say why no raid went (\"no raid to ...: no termite there now\"); " + raids.describe(level));
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

    /** Reads one raid's stages from the colony's diary and watches the world beside it; {@link #judge} gives the verdict. */
    private static final class Book {
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
                if (ant.isAlive() && !ant.isCrippled() && !ant.isDowned() && !raids.isScouting(ant.getUUID())) {
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
}
