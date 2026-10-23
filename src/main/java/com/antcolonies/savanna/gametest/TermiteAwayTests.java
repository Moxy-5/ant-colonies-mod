package com.antcolonies.gametest;

import static com.antcolonies.gametest.AwayKit.*;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Brood;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.SavannaParts;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * The brain, part 2 and on: colonies that live while the player is away ({@code AwayLife}, {@code NestLife},
 * {@code Meals}, {@code NestLedger}). Their own category, {@code away} (the user's rule, 2026-10-15): run it after
 * every new feature or change to how colonies live, as well as the category the change touches. The shared helpers are
 * in {@link AwayKit} and {@link TestScenes}.
 * The termites' scenes (the split, step S4; the red harvester ant's are in {@link HarvesterAwayTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteAwayTests {
    private static double flat(BlockPos a, BlockPos b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /**
     * An open spot on the ground (air over a grass block, off every hole and tunnel, inside the arena) {@code minOut} to
     * {@code maxOut} blocks from {@code from} that {@code ok} accepts, or null.
     */
    private static BlockPos groundSpot(GameTestHelper helper, Colony colony, BlockPos from, double minOut, double maxOut,
            java.util.function.Predicate<BlockPos> ok) {
        ServerLevel level = helper.getLevel();
        int y = helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        Set<Long> columns = new HashSet<>();
        for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
            columns.add(BlockPos.asLong(tunnel.hole().getX(), 0, tunnel.hole().getZ()));
            tunnel.tunnel().forEach(cell -> columns.add(BlockPos.asLong(cell.getX(), 0, cell.getZ())));
        }
        AABB inside = helper.getBounds().inflate(-3, 0, -3);
        for (double r = minOut; r <= maxOut; r += 0.5) {
            for (int a = 0; a < 360; a += 7) {
                BlockPos at = new BlockPos((int) Math.round(from.getX() + r * Math.cos(Math.toRadians(a))), y,
                        (int) Math.round(from.getZ() + r * Math.sin(Math.toRadians(a))));
                double out = flat(at, from);
                if (out >= minOut && out <= maxOut && inside.contains(at.getX() + 0.5, inside.minY + 1, at.getZ() + 0.5)
                        && !columns.contains(BlockPos.asLong(at.getX(), 0, at.getZ())) && level.getBlockState(at).isAir()
                        && level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK) && ok.test(at)) {
                    return at;
                }
            }
        }
        return null;
    }

    /** How many gallery pieces are dug all through. */
    private static int dugPieces(ServerLevel level, com.antcolonies.colony.Galleries galleries) {
        int dug = 0;
        for (BlockPos piece : galleries.pieceHoles()) {
            if (galleries.digCells(galleries.pieceAt(piece)).stream().allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell))) {
                dug++;
            }
        }
        return dug;
    }

    /** A tuft of dry grass (on a grass block) with 2 of its 4 pieces cut already, which the colony knows. */
    static BlockPos knownDryTuft(GameTestHelper helper, Colony colony, int x, int z) {
        BlockPos tuft = helper.absolutePos(new BlockPos(x, SURFACE, z));
        helper.getLevel().setBlock(tuft.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(tuft, com.antcolonies.registry.SavannaBlocks.DRY_GRASS.get().defaultBlockState(), 3);
        colony.food().setPiecesCutForTests(tuft, 2);
        colony.foodMap().knowForTests(tuft);
        colony.food().rescanPlantsSoon();
        return tuft;
    }

    /** The comb blocks' layers by age and their nodules, added up: fresh, ripe, old, nodules, layers. */
    static int[] combTotals(ServerLevel level, Colony colony) {
        int[] totals = new int[5];
        for (BlockPos pos : colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
            var state = level.getBlockState(pos);
            totals[0] += com.antcolonies.block.FungusCombBlock.fresh(state);
            totals[1] += com.antcolonies.block.FungusCombBlock.ripe(state);
            totals[2] += com.antcolonies.block.FungusCombBlock.old(state);
            totals[3] += state.getValue(com.antcolonies.block.FungusCombBlock.NODULES);
            totals[4] += com.antcolonies.block.FungusCombBlock.layers(state);
        }
        return totals;
    }

    /** Step 2d in the termite scene (see above): nothing while the player looks, then the mound out of sight. */
    private static void moundOnReturn(GameTestHelper helper, Colony colony, long[] phase) {
        ServerLevel level = helper.getLevel();
        long since = level.getGameTime() - phase[1];
        AwayReport report = colony.away().report();
        if (phase[0] == 1 && since >= 60) {
            AntColoniesMod.LOGGER.info("[away-test] 2d termites back, watched: mound {}, {}", colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level),
                    colony.away().describe(level));
            helper.assertTrue(colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level) == 0 && report.get(AwayReport.What.MOUND_BUILT) == 0,
                    "nothing of the mound should go up while a player looks: " + colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level) + " built");
            colony.population().setViewersForTests(List.of());
            phase[0] = 2;
            phase[1] = level.getGameTime();
        } else if (phase[0] == 2 && (!colony.building().hasWork() || since >= 300)) {
            int finished = colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level);
            AntColoniesMod.LOGGER.info("[away-test] 2d termites back: mound {} blocks, stage {}, {}", finished, colony.part(SavannaParts.MOUND).orElseThrow().getStage(level),
                    colony.away().describe(level));
            helper.assertTrue(finished > 0 && report.get(AwayReport.What.MOUND_BUILT) > 0, "the mound should have gone up out of sight: "
                    + finished + " blocks, " + report.describe());
            for (BlockPos pos : colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).keySet()) {
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof com.antcolonies.block.TermiteMoundBlock) {
                    helper.assertTrue(com.antcolonies.block.TermiteMoundBlock.isFinished(state)
                            && state.getValue(com.antcolonies.block.TermiteMoundBlock.AGE) >= 1, "a mound block built while away should be finished and set: "
                                    + pos + " " + state);
                }
            }
            helper.assertTrue(report.get(AwayReport.What.DUG) >= finished, "its soil should have been dug (the galleries under it): "
                    + report.describe());
            helper.succeed();
        }
    }

    /**
     * Step 2c, the termites. 60 old workers in the numbers, no real workers, and 4 tufts of dry grass round the mound,
     * each with 2 of its 4 pieces cut already; the colony knows 3 of them (the 4th nobody has found). By day nothing is
     * foraged (dry grass cannot be eaten from inside). At night the numbers eat the 3 known tufts up (6 pieces into the
     * comb's count); they vanish out of sight and are remembered to grow back 3 days after they were eaten; the unknown
     * tuft is left.
     * <p>Brain part 3, in the same scene: then the species' own rule decides (the profile's {@code forage_at}, with a
     * test clock and a pretend rain): on a dry day 2 more known tufts are left; in the rain by day they are eaten (real Macrotermes forage
     * in the wet); on a dry night with the alarm at 2 nothing is foraged; the alarm calmed but the store full (as the
     * brain last saw it), nothing either; with room again, 2 more tufts are eaten up.
     */
    @GameTest(template = "arena", timeoutTicks = 4900, skyAccess = true)
    public static void termiteNumbersForageKnownDryGrassOnlyAtNightOrInTheRain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        List<BlockPos> tufts = new ArrayList<>();
        int[] foodBefore = new int[1];
        long[] nightFrom = new long[1];
        List<BlockPos> more = new ArrayList<>(); // (brain part 3: the tufts of the later phases)
        TestClock clock = TestClock.moving(helper, 12.0); // midday
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 250000, 60);
            // Step 2c's rule with the 2 holes of before (the galleries' night holes have their own scene, step 4 below).
            com.antcolonies.colony.Galleries.of(colony).setSwitchForTests(false);
            int[][] spots = {{MIDDLE + 6, MIDDLE}, {MIDDLE - 6, MIDDLE}, {MIDDLE, MIDDLE + 6}, {MIDDLE, MIDDLE - 6}};
            for (int[] spot : spots) {
                BlockPos tuft = helper.absolutePos(new BlockPos(spot[0], SURFACE, spot[1]));
                level.setBlock(tuft.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(tuft, com.antcolonies.registry.SavannaBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                colony.food().setPiecesCutForTests(tuft, 2);
                tufts.add(tuft);
            }
            for (int i = 0; i < 3; i++) {
                colony.foodMap().knowForTests(tufts.get(i));
            }
            colony.food().rescanPlantsSoon();
            foodBefore[0] = colony.food().getFood();
            colony.field().setOnForTests(true);
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 2c termites by day: food {}{}", colony.food().getFood(), colony.field().describe());
            for (BlockPos tuft : tufts) {
                helper.assertTrue(colony.food().getPiecesCut(tuft) == 2, "nothing should be foraged by day: " + tuft + " cut "
                        + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.field().remembers(tufts.get(0)) && !colony.field().remembers(tufts.get(3)),
                    "the brain should remember the known tufts only");
            helper.assertTrue(colony.food().getFood() == foodBefore[0], "no food by day: " + colony.food().getFood());
            clock.skipTo(colony, TestClock.Phase.NIGHT); // sunset
            nightFrom[0] = level.getGameTime();
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 2c termites at night: food {}{}", colony.food().getFood(), colony.field().describe());
            long regrowth = colony.profile().food().regrowth().orElseThrow().ticks();
            for (int i = 0; i < 3; i++) {
                BlockPos tuft = tufts.get(i);
                helper.assertTrue(level.getBlockState(tuft).isAir(), "a known tuft should be eaten up at night and gone: " + tuft);
                long growsAt = colony.part(com.antcolonies.colony.SavannaParts.BARE_PATCHES).orElseThrow().growsAtForTests(tuft);
                helper.assertTrue(growsAt > nightFrom[0] + regrowth && growsAt <= level.getGameTime() + regrowth,
                        "the tuft should grow back 3 days after it was eaten: " + growsAt);
                helper.assertTrue(!colony.foodMap().isKnown(tuft), "an eaten-up tuft should be forgotten: " + tuft);
            }
            helper.assertTrue(level.getBlockState(tufts.get(3)).is(com.antcolonies.registry.SavannaBlocks.DRY_GRASS.get())
                    && colony.food().getPiecesCut(tufts.get(3)) == 2, "the tuft nobody found should be left: cut " + colony.food().getPiecesCut(tufts.get(3)));
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 6, "6 pieces should have come in: " + colony.food().getFood()
                    + ", was " + foodBefore[0]);
            // Brain part 3: the species' own rule with rain too: a dry day first (the next sunrise), 2 more known tufts.
            clock.skipTo(colony, TestClock.Phase.DAY);
            colony.setRainForTests(false);
            more.add(knownDryTuft(helper, colony, MIDDLE + 6, MIDDLE + 6));
            more.add(knownDryTuft(helper, colony, MIDDLE - 6, MIDDLE - 6));
        });
        helper.runAfterDelay(1800, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(!colony.isForagingTime(level), "a dry day should be no foraging time for termites");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.field().remembers(tuft) && colony.food().getPiecesCut(tuft) == 2,
                        "a known tuft should be remembered and left on a dry day: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            colony.setRainForTests(true); // rain by day: real Macrotermes forage in the wet too
            foodBefore[0] = colony.food().getFood();
        });
        helper.runAfterDelay(2900, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] part 3 termites in the rain: food {}{}", colony.food().getFood(), colony.field().describe());
            helper.assertTrue(colony.isForagingTime(level), "rain should be foraging time for termites, by day too");
            for (BlockPos tuft : more) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "a known tuft should be eaten up in the rain: " + tuft);
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 4, "4 pieces should have come in during the rain: "
                    + colony.food().getFood() + ", was " + foodBefore[0]);
            // A dry night, but an enemy at the foragers: the alarm at 2 keeps everyone in.
            more.clear();
            more.add(knownDryTuft(helper, colony, MIDDLE + 6, MIDDLE - 6));
            more.add(knownDryTuft(helper, colony, MIDDLE - 6, MIDDLE + 6));
            clock.skipTo(colony, TestClock.Phase.NIGHT); // a dry night
            colony.setRainForTests(false);
            colony.part(SavannaParts.ALARM).orElseThrow().raise(level, 2, colony.getCenter(), null);
            foodBefore[0] = colony.food().getFood();
        });
        helper.runAfterDelay(3200, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.part(SavannaParts.ALARM).orElseThrow().keepsEveryoneIn(level), "the alarm should still keep everyone in");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.field().remembers(tuft) && colony.food().getPiecesCut(tuft) == 2,
                        "nothing should be foraged while the alarm keeps everyone in: " + tuft + " cut " + colony.food().getPiecesCut(tuft));
            }
            // The alarm calms, but the food store is full (as the brain last saw it).
            colony.part(SavannaParts.ALARM).orElseThrow().calmForTests();
            colony.food().setRoomForTests(0);
        });
        helper.runAfterDelay(3500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.isForagingTime(level), "a calm night should be foraging time");
            for (BlockPos tuft : more) {
                helper.assertTrue(colony.food().getPiecesCut(tuft) == 2, "nothing should be foraged while the store has no room: " + tuft
                        + " cut " + colony.food().getPiecesCut(tuft));
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0], "no food with the store full: " + colony.food().getFood());
            colony.food().setRoomForTests(null); // room again
        });
        helper.runAfterDelay(4600, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            for (BlockPos tuft : more) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "with room again the tuft should be eaten up: " + tuft);
            }
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 4, "4 pieces should have come in: " + colony.food().getFood()
                    + ", was " + foodBefore[0]);
            helper.succeed();
        });
    }

    /**
     * Termites underground step 4, away and speed (the user's answers, 2026-10-17). A Macrotermes colony with its nest
     * and its 4 arms of gallery dug, 38 worker numbers (30 old enough to forage), the galleries reaching 26 blocks. By
     * the farthest hole: a dry tuft the colony knows with 1 piece left, and one it does not know (the brain sees it
     * round the hole: its picture of the land). Far from every hole: another known tuft. The queen is frozen and the
     * colony goes away.
     * <p>A night away: the night's holes are picked from the holes the brain remembers dug, the one by known food among
     * them, the others from the outer half of the web first (the far holes); the numbers graze only round them (the far
     * tuft is left, the one by the hole eaten up); the unknown tuft by the hole is found; the report counts the night,
     * the holes and the find. Then 600 young builders join, and hay the colony knows lies far from every hole.
     * <p>Back: the builders' time grows the galleries out of sight: a piece out to the hay first, then more, each dug,
     * within the workers' budget and the reach; the report counts them.
     * <p>Away again: 11 days go by, a known tuft with 1 piece left lies by a grown far piece, and at night the numbers
     * eat it up from that piece's hole. Back: every far piece that led to no food is filled in at once (not one every 10
     * seconds), the piece foraged from while away stays, and the report counts the fills.
     */
    @GameTest(template = "arena", timeoutTicks = 9000, skyAccess = true)
    public static void aTermiteColonyAwayForagesFromTheNightsHolesAndGrowsItsGalleriesOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int mid = 32;
        int reach = 26;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        TestClock clock = TestClock.still(helper, 12); // midday: the foragers stay in
        helper.onEachTick(() -> noRealWorkers(helper));
        Colony[] the = {null};
        CompoundTag[] queenOnDisk = {null};
        int[] stage = {0};
        long[] since = {0};
        BlockPos[] far = {null}; // the farthest hole
        BlockPos[] spot = new BlockPos[5]; // the known tuft by it, the unknown tuft by it, the far known tuft, the hay, the tuft by a grown piece
        BlockPos[] grown = {null}; // the grown far piece foraged from while away
        int[] dugBefore = {0};
        int[] lastLength = {-1};
        long[] agedAt = {0};
        int[] fillsBefore = {0};
        Set<BlockPos> piecesBefore = new HashSet<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            the[0] = colony;
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            long now = level.getGameTime();
            colony.population().addNewMembers(Caste.WORKER, now - colony.profile().worker().builderUntilTicks() - 1000, 30);
            colony.population().addNewMembers(Caste.WORKER, now - colony.profile().worker().nurseUntilTicks() - 1000, 8);
            TermiteSoldierTests.digNestAtOnce(level, colony); // (the nest of a colony with workers, not the founders' chamber)
            com.antcolonies.colony.Galleries.of(colony).setSwitchForTests(true);
            com.antcolonies.colony.Galleries.of(colony).setReachForTests(reach);
            colony.field().setOnForTests(true);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = the[0];
            if (colony == null || stage[0] == 0) {
                return;
            }
            com.antcolonies.colony.Galleries galleries = com.antcolonies.colony.Galleries.of(colony);
            List<NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
            BlockPos center = colony.getCenter();
            int graze = colony.profile().nest().galleries().orElseThrow().foodRadius();
            int pieceLength = colony.profile().nest().galleries().orElseThrow().pieceLength();
            List<BlockPos> holes = tunnels.stream().map(NestPlan.ForagingTunnel::hole).distinct().toList();
            AwayReport report = colony.away().report();
            long tick = helper.getTick();
            if (tick % 200 == 0) {
                AntColoniesMod.LOGGER.info("[step4-test] tick {}: stage {}{} | {}", tick, stage[0], galleries.describe(level), report.describe());
            }
            switch (stage[0]) {
                case 1 -> { // the arms planned and dug (by the test, at once): then the tufts
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        for (BlockPos cell : galleries.digCells(tunnel)) {
                            if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                            }
                        }
                    }
                    if (galleries.tipCount() < 3 || !galleries.dugHolesForTests().containsAll(holes)) {
                        helper.assertTrue(tick < 1500, "the arms were not planned and dug, or the brain does not remember their holes dug:"
                                + galleries.describe(level) + " remembered " + galleries.dugHolesForTests().size() + " of " + holes.size());
                        since[0] = 0;
                        return;
                    }
                    if (++since[0] < 60) {
                        return;
                    }
                    // The farthest hole with room beside it for a tuft no other hole grazes.
                    for (BlockPos hole : holes.stream().sorted(java.util.Comparator.comparingDouble(hole -> -flat(hole, center))).toList()) {
                        spot[0] = groundSpot(helper, colony, hole, 2, 4,
                                at -> holes.stream().allMatch(other -> other.equals(hole) || flat(other, at) > graze + 0.5));
                        if (spot[0] != null && flat(hole, center) >= galleries.reach() / 2.0) {
                            far[0] = hole;
                            break;
                        }
                    }
                    helper.assertTrue(far[0] != null, "no room for a tuft by a far hole:" + galleries.describe(level));
                    spot[1] = groundSpot(helper, colony, far[0], 2, 4, at -> flat(at, spot[0]) >= 2);
                    spot[2] = groundSpot(helper, colony, center, 9, 20, at -> holes.stream().allMatch(hole -> flat(hole, at) > graze + 2.5));
                    helper.assertTrue(spot[1] != null && spot[2] != null, "no room for the other tufts");
                    for (int i = 0; i < 3; i++) {
                        level.setBlock(spot[i], com.antcolonies.registry.SavannaBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                        colony.food().setPiecesCutForTests(spot[i], i == 0 ? 3 : 2);
                        if (i != 1) {
                            colony.foodMap().knowForTests(spot[i]);
                        }
                    }
                    colony.food().rescanPlantsSoon();
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: far hole {}, tufts {}", tick, helper.relativePos(far[0]),
                            java.util.Arrays.stream(spot, 0, 3).map(helper::relativePos).toList());
                    since[0] = 0;
                    stage[0] = 2;
                }
                case 2 -> { // the brain has looked: then away
                    if (!colony.field().remembers(spot[0]) || !colony.field().remembers(spot[2]) || !colony.field().seesUnknown(spot[1])) {
                        helper.assertTrue(++since[0] < 600, "the brain did not see the tufts (known " + colony.field().remembers(spot[0]) + "/"
                                + colony.field().remembers(spot[2]) + ", the unknown one in its picture " + colony.field().seesUnknown(spot[1]) + ")");
                        return;
                    }
                    helper.assertTrue(!colony.foodMap().isKnown(spot[1]), "the colony should not know the tuft nobody found");
                    colony.away().setOnForTests(true);
                    colony.away().setGraceForTests(20L);
                    queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
                    colony.away().setAwayForTests(true);
                    since[0] = 0;
                    stage[0] = 3;
                }
                case 3 -> { // away: a night
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(++since[0] < 400, "the colony did not go away");
                        return;
                    }
                    clock.skipTo(colony, TestClock.Phase.NIGHT);
                    lastLength[0] = galleries.length();
                    since[0] = 0;
                    stage[0] = 4;
                }
                case 4 -> { // the night's holes, the graze round them, the find
                    // (the tuft eaten up takes the numbers a while: 400 ticks were not always enough, 2 runs in 4 cut 3 of
                    // its 4 pieces by then, docs/BUGS.md 50; they get up to 2000, as in stage 9)
                    boolean tuftEaten = colony.food().getPiecesCut(spot[0]) >= 4 || level.getBlockState(spot[0]).isAir();
                    if (++since[0] < 400 || !tuftEaten && since[0] < 2000) {
                        return;
                    }
                    Set<BlockPos> tonight = galleries.tonight();
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: tonight's holes {}, field {}", tick, tonight.stream().map(helper::relativePos).toList(),
                            colony.field().describe());
                    helper.assertTrue(!tonight.isEmpty() && galleries.dugHolesForTests().containsAll(tonight),
                            "the night's holes while away should be picked from the holes remembered dug: " + tonight);
                    helper.assertTrue(tonight.contains(far[0]), "the hole by the known tuft should open first: " + tonight);
                    List<BlockPos> unfed = tonight.stream().filter(hole -> flat(hole, spot[0]) > graze && flat(hole, spot[2]) > graze).toList();
                    boolean outerLeft = holes.stream().anyMatch(hole -> !hole.equals(far[0]) && flat(hole, center) >= galleries.reach() / 2.0);
                    helper.assertTrue(unfed.isEmpty() || !outerLeft || unfed.stream().anyMatch(hole -> flat(hole, center) >= galleries.reach() / 2.0),
                            "the holes opened to explore should come from the outer half first: " + unfed.stream().map(helper::relativePos).toList());
                    // (eaten up: every piece cut; the block goes once the land is shown again)
                    helper.assertTrue(colony.food().getPiecesCut(spot[0]) >= 4 || level.getBlockState(spot[0]).isAir(),
                            "the known tuft by the open hole should be eaten up: cut " + colony.food().getPiecesCut(spot[0]));
                    helper.assertTrue(colony.field().remembers(spot[2]) && colony.food().getPiecesCut(spot[2]) == 2,
                            "the known tuft far from every hole should be left (no hole open near it): cut " + colony.food().getPiecesCut(spot[2]));
                    helper.assertTrue(!colony.field().seesUnknown(spot[1]) && (colony.foodMap().isKnown(spot[1]) || level.getBlockState(spot[1]).isAir()
                            || colony.food().getPiecesCut(spot[1]) > 2), "the tuft by the open hole should have been found");
                    helper.assertTrue(report.get(AwayReport.What.FORAGING_NIGHTS) >= 1 && report.get(AwayReport.What.HOLES_OPENED) >= tonight.size()
                            && report.get(AwayReport.What.FOUND_FROM_HOLES) >= 1, "the report should count the night, its holes and the find: "
                            + report.describe());
                    // 600 young builders join, and hay the colony knows lies far from every hole.
                    clock.skipTo(colony, TestClock.Phase.DAY);
                    colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000, 600);
                    spot[3] = groundSpot(helper, colony, center, 17, 22, at -> holes.stream().allMatch(hole -> flat(hole, at) > 12) && flat(at, spot[2]) > 10);
                    helper.assertTrue(spot[3] != null, "no room for the hay far from every hole");
                    level.setBlock(spot[3], Blocks.HAY_BLOCK.defaultBlockState(), 3);
                    colony.foodMap().knowForTests(spot[3]);
                    since[0] = 0;
                    stage[0] = 5;
                }
                case 5 -> { // the builders work the time away: then back
                    if (++since[0] < 400) {
                        return;
                    }
                    helper.assertTrue(galleries.length() == lastLength[0], "the galleries grew while away (the land is not loaded then):"
                            + galleries.describe(level));
                    dugBefore[0] = dugPieces(level, galleries);
                    lastLength[0] = galleries.length();
                    colony.away().setAwayForTests(false);
                    loadAgain(level, queenOnDisk[0]);
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: back, {} pieces dug", tick, dugBefore[0]);
                    since[0] = 0;
                    stage[0] = 6;
                }
                case 6 -> { // the galleries grow out of sight with the builders' time
                    if (galleries.length() != lastLength[0]) {
                        lastLength[0] = galleries.length();
                        since[0] = 0;
                        return;
                    }
                    if (++since[0] < 300) {
                        return;
                    }
                    int pieces = report.get(AwayReport.What.GALLERY_PIECES);
                    helper.assertTrue(pieces >= 2 && report.get(AwayReport.What.GALLERY_BLOCKS) >= pieces,
                            "the builders' time should have grown the galleries on return: " + report.describe() + galleries.describe(level));
                    helper.assertTrue(report.get(AwayReport.What.GALLERY_TO_FOOD) >= 1, "a piece should have grown out to the known hay first: "
                            + report.describe());
                    com.antcolonies.colony.FoodWays.NewTunnel toHay = colony.nest().foodWays().getNewTunnels().stream()
                            .filter(tunnel -> tunnel.food().equals(spot[3])).findFirst().orElse(null);
                    helper.assertTrue(toHay != null && galleries.hasPiece(toHay.tunnel().hole()), "no gallery piece out to the hay:"
                            + galleries.describe(level));
                    helper.assertTrue(galleries.digCells(galleries.pieceAt(toHay.tunnel().hole())).stream()
                            .allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell)), "the piece out to the hay should be dug out of sight");
                    int dug = dugPieces(level, galleries);
                    helper.assertTrue(dug >= dugBefore[0] + pieces - 1, "the pieces grown should be dug out of sight: " + dug + " dug, "
                            + dugBefore[0] + " before, " + pieces + " grown");
                    helper.assertTrue(galleries.length() <= galleries.lengthAllowed() + 2 * (pieceLength + 3),
                            "the galleries grew past what the workers allow:" + galleries.describe(level));
                    for (NestPlan.ForagingTunnel tunnel : tunnels) {
                        helper.assertTrue(flat(tunnel.hole(), center) <= reach + 2.5, "a hole at " + helper.relativePos(tunnel.hole())
                                + " lies past the reach of " + reach);
                    }
                    // Away again: a known tuft with 1 piece left by the farthest grown piece that leads on from no other, dug out
                    // (one grown piece may still wait to be dug, see above, and a hole not dug never opens).
                    for (BlockPos piece : galleries.pieceHoles()) {
                        piecesBefore.add(piece);
                        boolean pieceDug = galleries.digCells(galleries.pieceAt(piece)).stream().allMatch(cell -> com.antcolonies.colony.Nest.isDugOut(level, cell));
                        if (pieceDug && !galleries.isFirstArm(piece) && galleries.isLeaf(piece) && !piece.equals(toHay.tunnel().hole())
                                && (grown[0] == null || flat(piece, center) > flat(grown[0], center))) {
                            grown[0] = piece;
                        }
                    }
                    helper.assertTrue(grown[0] != null, "no grown far piece:" + galleries.describe(level));
                    spot[4] = groundSpot(helper, colony, grown[0], 2, 3.5, at -> true);
                    helper.assertTrue(spot[4] != null, "no room for a tuft by the grown piece " + helper.relativePos(grown[0]));
                    level.setBlock(spot[4], com.antcolonies.registry.SavannaBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                    colony.food().setPiecesCutForTests(spot[4], 3);
                    colony.foodMap().knowForTests(spot[4]);
                    colony.food().rescanPlantsSoon();
                    colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000, 300);
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: grown {}; a tuft by the piece to {}", tick, report.describe(), helper.relativePos(grown[0]));
                    since[0] = 0;
                    stage[0] = 7;
                }
                case 7 -> { // the brain has seen the tuft: away again
                    if (!colony.field().remembers(spot[4])) {
                        helper.assertTrue(++since[0] < 600, "the brain did not see the tuft by the grown piece");
                        return;
                    }
                    queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
                    colony.away().setAwayForTests(true);
                    since[0] = 0;
                    stage[0] = 8;
                }
                case 8 -> { // away: 11 days go by, then a night
                    if (!colony.away().isAway(level)) {
                        helper.assertTrue(++since[0] < 400, "the colony did not go away again");
                        return;
                    }
                    galleries.ageForTests(11 * 24000L);
                    agedAt[0] = level.getGameTime();
                    fillsBefore[0] = galleries.fills();
                    clock.skipTo(colony, TestClock.Phase.NIGHT);
                    since[0] = 0;
                    stage[0] = 9;
                }
                case 9 -> { // the numbers eat the tuft from the grown piece's hole (the hay first, it is richer): then back
                    boolean eaten = colony.food().getPiecesCut(spot[4]) >= 4 || level.getBlockState(spot[4]).isAir();
                    if (++since[0] < 100 || !eaten && since[0] < 2000) {
                        return;
                    }
                    helper.assertTrue(galleries.tonight().contains(grown[0]), "the grown piece's hole by known food should open: "
                            + galleries.tonight().stream().map(helper::relativePos).toList());
                    helper.assertTrue(colony.food().getPiecesCut(spot[4]) >= 4 || level.getBlockState(spot[4]).isAir(),
                            "the tuft by the grown piece should be eaten up: cut " + colony.food().getPiecesCut(spot[4]));
                    helper.assertTrue(galleries.fills() == fillsBefore[0], "nothing should be filled in while away (the land waits for the return)");
                    clock.skipTo(colony, TestClock.Phase.DAY);
                    colony.away().setAwayForTests(false);
                    loadAgain(level, queenOnDisk[0]);
                    since[0] = 0;
                    stage[0] = 10;
                }
                case 10 -> { // back: every branch due filled in at once
                    if (++since[0] < 200) {
                        return;
                    }
                    AntColoniesMod.LOGGER.info("[step4-test] tick {}: back again, {} filled in{} | {}", tick, galleries.fills() - fillsBefore[0],
                            galleries.describe(level), report.describe());
                    helper.assertTrue(galleries.hasPiece(grown[0]), "the piece the numbers foraged from while away was filled in");
                    int filled = galleries.fills() - fillsBefore[0];
                    helper.assertTrue(filled >= 1 && report.get(AwayReport.What.FILLED_IN) == filled && report.get(AwayReport.What.FILLED_BLOCKS) > 0,
                            "the branches due should be filled in on return and counted: " + filled + " filled; " + report.describe());
                    for (BlockPos piece : galleries.pieceHoles()) {
                        helper.assertTrue(!piecesBefore.contains(piece) || galleries.isFirstArm(piece) || galleries.lastUsed(piece) >= agedAt[0],
                                "the piece to " + helper.relativePos(piece) + " led to no food for 11 days and was not filled in at once:"
                                        + galleries.describe(level));
                    }
                    helper.succeed();
                }
                default -> {
                }
            }
        });
    }

    /**
     * Brain step 4g, step 4, harm out of sight (the user's decisions of 2026-10-18), the termites. 60 old worker numbers,
     * galleries off, and a place 10 blocks east where hostiles were seen 6 times (strength 6: not an attack, so no hole
     * closes and the termites still graze there). At night, with no hostile really there (loaded land), the numbers eat
     * two known tufts by it and nobody is lost: nobody dies of a mere memory. Then a husk stands there (out of sight):
     * at the next tufts some numbers are lost (no piece from those trips), the headcount drops by as many, and a body
     * found by nestmates (likely 10 blocks out) counts in the memory as a death. With the husk gone, the chance is 0 again.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void termiteNumbersAreLostOutOfSightOnlyWhereAHostileReallyIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        BlockPos place = helper.absolutePos(new BlockPos(MIDDLE + 10, SURFACE, MIDDLE));
        List<BlockPos> first = new ArrayList<>();
        List<BlockPos> second = new ArrayList<>();
        int[] before = new int[2];
        net.minecraft.world.entity.Mob[] husk = new net.minecraft.world.entity.Mob[1];
        TestClock clock = TestClock.moving(helper, 12.0);
        helper.onEachTick(() -> noRealWorkers(helper));
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime() - 250000, 60);
            com.antcolonies.colony.Galleries.of(colony).setSwitchForTests(false);
            for (int i = 0; i < 6; i++) {
                colony.danger().seen(level, place, UUID.randomUUID(), "husk"); // seen only: not an attack
            }
            first.add(knownDryTuft(helper, colony, MIDDLE + 10, MIDDLE - 3));
            first.add(knownDryTuft(helper, colony, MIDDLE + 10, MIDDLE + 3));
            colony.field().setOnForTests(true);
            before[0] = colony.getCount(Caste.WORKER);
            clock.skipTo(colony, TestClock.Phase.NIGHT);
        });
        helper.runAfterDelay(1100, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4, no hostile there: {}{}", colony.field().describe(), colony.danger().describe(level));
            for (BlockPos tuft : first) {
                helper.assertTrue(level.getBlockState(tuft).isAir(), "the tufts by the remembered place should be eaten (no hole closes for a sighting): " + tuft);
            }
            helper.assertTrue(colony.danger().lostOutOfSight() == 0 && colony.getCount(Caste.WORKER) == before[0],
                    "nobody should be lost where no hostile really is: " + colony.danger().describe(level));
            helper.assertTrue(colony.danger().lossChance(level, place, null) == 0, "no loss chance without a hostile there");
            // Now a husk really stands there, out of sight.
            husk[0] = helper.spawn(EntityType.HUSK, MIDDLE + 12, SURFACE, MIDDLE);
            husk[0].setNoAi(true);
            husk[0].setPersistenceRequired();
            helper.assertTrue(colony.danger().lossChance(level, place, null) >= 0.5, "a hostile there should make the full loss chance: "
                    + colony.danger().lossChance(level, place, null));
            int[][] spots = {{MIDDLE + 9, MIDDLE - 1}, {MIDDLE + 9, MIDDLE + 1}, {MIDDLE + 11, MIDDLE - 1}, {MIDDLE + 11, MIDDLE + 1}, {MIDDLE + 8, MIDDLE + 4}};
            for (int[] spot : spots) {
                second.add(knownDryTuft(helper, colony, spot[0], spot[1]));
            }
            before[1] = colony.getCount(Caste.WORKER);
        });
        helper.runAfterDelay(2400, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            int lost = colony.danger().lostOutOfSight();
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4, a husk there: {} lost, {}{}", lost, colony.field().describe(), colony.danger().describe(level));
            helper.assertTrue(lost >= 1, "some numbers should be lost where a hostile really is: " + colony.danger().describe(level));
            helper.assertTrue(colony.getCount(Caste.WORKER) == before[1] - lost, "the headcount should drop by the lost: "
                    + colony.getCount(Caste.WORKER) + ", was " + before[1] + ", lost " + lost);
            boolean found = colony.danger().places().stream().anyMatch(p -> p.deaths() > 0);
            AntColoniesMod.LOGGER.info("[away-test] 4g step 4: a body {}", found ? "was found (a death remembered)" : "was never found");
            husk[0].discard();
            helper.assertTrue(colony.danger().lossChance(level, place, null) == 0, "with the husk gone nobody should be lost there");
            helper.succeed();
        });
    }

    /**
     * Step 2b, the termite side. A Macrotermes colony with its nest dug, 100 worker numbers (40 young, 60 old), 2 minor
     * and 1 major soldier numbers, all just fed, and a garden of aged comb (each block: 2 old layers, a ripe one, a fresh
     * one, 3 nodules). The queen, full-grown, lies in the royal chamber; she is frozen and the colony goes away. While
     * away (the ageing sped up 100 times): the fresh layers ripen, ripe layers go old (by themselves and when a nodule
     * grows from them), nodules grow, old layers crumble (out of the food count), all as counts owed to the blocks; the
     * known ages still add up to the layers counted; the nurses feed the queen nodules and she lays her stream. Then the
     * workers' and soldiers' meals come due: the young share eat nodules, the old ones old comb (4 meals a piece), the
     * soldiers a nodule each, and nobody is left hungry. The king lies beside the queen, a day and more since his last
     * nodule (brain part 4, the king fix): the nurse numbers feed him too, and he lives. Then (brain part 3) the game is
     * saved and loaded again while away, and the loaded copy carries on. Back in range, the blocks catch up: the ledger empties and the comb blocks
     * hold just the ages and nodules the brain counted.
     * <p>Step 2d in the same scene: the colony has earned the mound's second stage and nothing of it is built. While away
     * nothing is built (the land is not loaded), the builders' time is counted. Back in range a pretend player looks at the
     * mound's place: nothing goes up while it is seen. The player gone: the mound goes up out of sight, every block of it
     * finished and set (it had time to dry), its soil dug from the galleries under it, and the report counts it.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aTermiteColonyAwayAgesItsCombFeedsItsQueenAndEatsAsNumbers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(2, () -> helper.spawn(SavannaEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE));
        var foods = com.antcolonies.species.SpeciesProfile.MealFood.values();
        UUID[] queen = new UUID[1];
        UUID[] king = new UUID[1];
        long[] kingHungrySince = new long[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        CompoundTag[] kingOnDisk = new CompoundTag[1];
        int[] eggsLaid = new int[1];
        int[] foodBefore = new int[1];
        int[] grownBefore = new int[1];
        int[] lostBefore = new int[1];
        int[] eatenBefore = new int[2];
        int[] expected = new int[5]; // fresh, ripe, old, nodules, layers: what the brain counted when the colony came back
        boolean[] back = {false};
        long[] phase = {0, 0}; // step 2d after the comb: 1 watched, 2 the player gone; when it started
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.setHungerOn(true);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the royal chamber should be dug");
            AbstractAntEntity theQueen = colony.getQueen(level);
            theQueen.teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            ((com.antcolonies.entity.TermiteQueenEntity) theQueen).setSize(1.0F); // full-grown: an egg every 15 seconds
            long now = level.getGameTime();
            colony.population().addNewMembers(Caste.WORKER, now, 40);
            colony.population().addNewMembers(Caste.WORKER, now - 250000, 60);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 2);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 1);
            for (int i = 0; i < 40; i++) {
                BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
                if (spot == null) {
                    break;
                }
                colony.food().depositSeed(level, spot);
            }
            for (BlockPos comb : colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level)) {
                if (com.antcolonies.block.FungusCombBlock.layers(level.getBlockState(comb)) < 4) {
                    continue; // (a comb not full yet stays all fresh)
                }
                // Aged comb: 2 old layers at the bottom, a ripe one, a fresh one on top; 3 nodules.
                level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.block.FungusCombBlock.NODULES, 3)
                        .setValue(com.antcolonies.block.FungusCombBlock.OLD, 2).setValue(com.antcolonies.block.FungusCombBlock.FRESH, 1), 3);
            }
            helper.assertTrue(colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).size() >= 5, "only " + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getCombs(level).size() + " comb blocks");
            helper.assertTrue(colony.food().getFood() == combTotals(level, colony)[4], "the food count " + colony.food().getFood()
                    + " is not the " + combTotals(level, colony)[4] + " layers of comb");
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in the royal chamber");
            helper.assertTrue(!colony.isFounding(), "the colony should have workers");
            AbstractAntEntity theQueen = colony.getQueen(level);
            queen[0] = theQueen.getUUID();
            // The king, a day and more since his last nodule: he wants one.
            var theKing = level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                    mob -> colony.getId().equals(mob.getColonyId())).stream().findFirst().orElse(null);
            helper.assertTrue(theKing != null && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 1, "the king should have joined");
            theKing.teleportTo(theQueen.getX() + 1.0, theQueen.getY(), theQueen.getZ());
            king[0] = theKing.getUUID();
            kingHungrySince[0] = level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 100;
            colony.part(SavannaParts.KING_CARE).orElseThrow().setLastFed(kingHungrySince[0]);
            helper.assertTrue(colony.part(SavannaParts.KING_CARE).orElseThrow().wantsFood(level), "the king should want a nodule");
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            colony.queenCare().setQueenLastFed(level.getGameTime());
            int[] blocks = combTotals(level, colony);
            helper.assertTrue(colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() == blocks[0] && colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() == blocks[1]
                    && colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown() == blocks[2], "the brain should have seen the comb as it is: " + java.util.Arrays.toString(blocks)
                            + " vs " + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + "/" + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + "/" + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown());
            foodBefore[0] = colony.food().getFood();
            grownBefore[0] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown();
            colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(100.0F);
            colony.part(SavannaParts.MOUND).orElseThrow().setStage(level, 2);
            helper.assertTrue(colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level) == 0, "no mound built yet");
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, theQueen);
            kingOnDisk[0] = freezeAndUnload(colony, level, theKing);
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(420, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] termites away: {}{} | known comb {}/{}/{}, nodules {}, food {}, queen food {}",
                    colony.population().describe(level), colony.away().describe(level), colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown(), colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown(),
                    colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown(), colony.life().nurseFoodLeft(level), colony.food().getFood(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            var ledger = colony.ledger();
            helper.assertTrue(ledger.ripened() > 0, "fresh comb should have ripened while away: " + ledger.describe());
            helper.assertTrue(ledger.aged() > 0, "ripe comb should have gone old while away: " + ledger.describe());
            helper.assertTrue(colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown() > grownBefore[0], "nodules should have grown while away");
            helper.assertTrue(ledger.store() < 0 && colony.food().getFood() == foodBefore[0] + ledger.store(),
                    "old comb should have crumbled, out of the food count: " + colony.food().getFood() + " food, was " + foodBefore[0] + "; "
                            + ledger.describe());
            int known = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "the known ages (" + known + " layers) should add up to the "
                    + colony.food().getFood() + " counted");
            helper.assertTrue(ledger.nurseFood() < 0 && colony.queenCare().getQueenBread() + 2 * (colony.queenCare().getEggsLaid() - eggsLaid[0]) > 0,
                    "the nurses should have fed the queen nodules: " + ledger.describe() + ", she holds " + colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().report().get(AwayReport.What.KING_FED) >= 1 && colony.part(SavannaParts.KING_CARE).orElseThrow().getLastFed() > kingHungrySince[0]
                    && !colony.part(SavannaParts.KING_CARE).orElseThrow().wantsFood(level), "the nurses should have fed the king a nodule while away: " + colony.away().report().describe());
            helper.assertTrue(!colony.away().report().didKingStarve(), "the king should not starve");
            // The meals come due: the workers' and the soldiers'.
            long due = level.getGameTime() - colony.profile().worker().mealIntervalTicks() - 30;
            colony.population().setLastAteForTests(level, Caste.WORKER, due);
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                colony.population().setLastAteForTests(level, rank.caste(), due);
            }
            eatenBefore[0] = colony.workerFood().getPiecesEaten(foods[0]);
            eatenBefore[1] = colony.workerFood().getPiecesEaten(foods[1]);
        });
        helper.runAfterDelay(480, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int comb = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.STORE) - eatenBefore[0];
            int nodules = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.NURSE_FOOD) - eatenBefore[1];
            AntColoniesMod.LOGGER.info("[away-test] termites away ate {} layers of comb and {} nodules: {}{}", comb, nodules,
                    colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(comb >= 13 && comb <= 16, "the 60 old worker numbers should eat about 15 layers of old comb, ate " + comb);
            helper.assertTrue(nodules >= 8 && nodules <= 11, "the 40 young worker numbers should eat about 10 nodules, ate " + nodules);
            helper.assertTrue(colony.population().hungry(level) == 0, "every number should have eaten, the soldiers too: "
                    + colony.population().describe(level));
            helper.assertTrue(colony.population().inside(Caste.WORKER) == 100, "a worker number died: "
                    + colony.population().inside(Caste.WORKER));
            helper.assertTrue(colony.queenCare().getEggsLaid() > eggsLaid[0], "the queen should have laid her stream while away");
            helper.assertTrue(colony.brood().youngInsideForTests().stream().anyMatch(Brood.Young::isEgg), "her eggs should be numbers: "
                    + colony.brood().youngInsideForTests());
            int known = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() + colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            helper.assertTrue(known == colony.food().getFood(), "after the meals the known ages (" + known + " layers) should still add up to the "
                    + colony.food().getFood() + " counted");
            // Brain part 3: the game is saved and loaded again while away; the loaded colony comes back below (the comb
            // blocks must then show what the copy counted, and the mound must go up from the copy's building time).
            Colony original = colony;
            colony = saveAndLoad(helper, colony);
            colony.setHungerOn(true);
            colony.population().setOnForTests(false);
            colony.population().setViewersForTests(List.of());
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            colony.away().setAwayForTests(true);
            helper.assertTrue(colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() == original.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown() && colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown() == original.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown()
                    && colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown() == original.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown() && colony.life().nurseFoodLeft(level) == original.life().nurseFoodLeft(level)
                    && colony.food().getFood() == original.food().getFood() && colony.building().work() == original.building().work()
                    && colony.queenCare().getEggsLaid() == original.queenCare().getEggsLaid()
                    && colony.brood().insideCount() == original.brood().insideCount() && colony.population().hungry(level) == 0,
                    "the loaded colony should know its comb, nodules, food, eggs, young, meals and building time as before the save");
            // Back in range: what the brain counted is what the blocks must show once they have caught up.
            helper.assertTrue(!colony.ledger().isEmpty(), "the blocks should owe something");
            helper.assertTrue(colony.part(SavannaParts.MOUND).orElseThrow().countFinished(level) == 0 && colony.building().hasWork(),
                    "nothing of the mound should be built while away, but the builders' time counted: " + colony.building().work());
            colony.building().setWorkForTests(colony.building().work() + 100000); // (longer away than the test waits)
            // A pretend player looks at the mound's place from 7 blocks off.
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(colony.getCenter().offset(7, 2, 0))));
            expected[0] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().freshKnown();
            expected[1] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().ripeKnown();
            expected[2] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().oldKnown();
            expected[3] = colony.life().nurseFoodLeft(level);
            expected[4] = colony.food().getFood();
            grownBefore[0] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown();
            lostBefore[0] = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesLost();
            colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(0.0F); // (only nodules may still grow: counted below)
            helper.assertTrue(colony.part(SavannaParts.KING_CARE).orElseThrow().getLastFed() == original.part(SavannaParts.KING_CARE).orElseThrow().getLastFed(), "the loaded colony should know when the king ate");
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
            loadAgain(level, kingOnDisk[0]);
            back[0] = true;
        });
        helper.onEachTick(() -> {
            if (!back[0]) {
                return;
            }
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || !colony.ledger().isEmpty()) {
                return;
            }
            if (phase[0] > 0) {
                moundOnReturn(helper, colony, phase);
                return;
            }
            int[] blocks = combTotals(level, colony);
            int grown = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesGrown() - grownBefore[0]; // (each turned a ripe layer old)
            int lost = colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().getNodulesLost() - lostBefore[0]; // (on comb eaten or crumbled right away)
            AntColoniesMod.LOGGER.info("[away-test] termites back: blocks {} (fresh, ripe, old, nodules, layers), counted {}, {} nodules grown since, {} lost",
                    java.util.Arrays.toString(blocks), java.util.Arrays.toString(expected), grown, lost);
            helper.assertTrue(blocks[4] == expected[4] && blocks[4] == colony.food().getFood(), "the comb should have " + expected[4]
                    + " layers as counted: " + java.util.Arrays.toString(blocks) + ", food " + colony.food().getFood());
            helper.assertTrue(blocks[0] == expected[0] && blocks[1] == expected[1] - grown && blocks[2] == expected[2] + grown,
                    "the comb's ages should be as counted: blocks " + java.util.Arrays.toString(blocks) + ", counted " + java.util.Arrays.toString(expected)
                            + ", " + grown + " nodules grown since");
            helper.assertTrue(blocks[3] == expected[3] + grown - lost, "the nodules should be as counted: blocks " + java.util.Arrays.toString(blocks)
                    + ", counted " + java.util.Arrays.toString(expected) + ", " + grown + " grown since, " + lost + " lost with used-up comb");
            Entity theKing = level.getEntity(king[0]);
            helper.assertTrue(theKing != null && theKing.isAlive() && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 1,
                    "the king, fed while away, should be alive when his land loads: " + theKing);
            colony.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().setAgeingSpeedForTests(1.0F);
            phase[0] = 1;
            phase[1] = level.getGameTime();
        });
    }

    /**
     * Brain part 4, the king fix: a starving termite royal pair while away (real Macrotermes kings and queens are fed
     * mouth to mouth and die without it). A Macrotermes colony with its nest dug, 20 worker numbers (just fed), a comb
     * of fresh forage but not one nodule, the queen in the royal chamber and the king beside her, both long unfed. While
     * away the nurses have nothing to give them: the queen starves, and the king too by her rule (his hunger clock is
     * the brain's, so it runs on while he is frozen); the workers live. The report says so. Back in range both die as
     * their land loads and the colony carries on queenless.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aStarvingTermitePairAwayDieWhenTheirLandLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(2, () -> helper.spawn(SavannaEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE));
        UUID[] id = new UUID[1];
        UUID[] royals = new UUID[2];
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            id[0] = colony.getId();
            TestClock.still(helper, 12.0); // midday: the foragers stay in
            colony.setHungerOn(true);
            colony.population().setOnForTests(false);
            colony.population().setViewersForTests(List.of());
            TermiteSoldierTests.digNestAtOnce(level, colony);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the royal chamber should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 20);
            BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
            helper.assertTrue(spot != null, "no spot for comb in the garden");
            colony.food().depositSeed(level, spot); // (fresh forage: the colony has had its first comb, so its royals get hungry)
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = byId(helper, id[0]);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in the royal chamber");
            helper.assertTrue(colony.life().nurseFoodLeft(level) == 0, "there should be no nodule: " + colony.life().nurseFoodLeft(level));
            AbstractAntEntity theQueen = colony.getQueen(level);
            var theKing = level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, helper.getBounds(),
                    mob -> colony.getId().equals(mob.getColonyId())).stream().findFirst().orElse(null);
            helper.assertTrue(theKing != null, "the king should have joined");
            theKing.teleportTo(theQueen.getX() + 1.0, theQueen.getY(), theQueen.getZ());
            royals[0] = theQueen.getUUID();
            royals[1] = theKing.getUUID();
            long now = level.getGameTime();
            colony.queenCare().setQueenLastFed(now - colony.profile().queen().starveTicks() + 200);
            colony.part(SavannaParts.KING_CARE).orElseThrow().setLastFed(now - colony.profile().worker().mealIntervalTicks() - colony.profile().queen().starveTicks() + 300);
            helper.assertTrue(!colony.part(SavannaParts.KING_CARE).orElseThrow().starves(level), "the king should not have starved yet");
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            unloaded.put(royals[0], freezeAndUnload(colony, level, theQueen));
            unloaded.put(royals[1], freezeAndUnload(colony, level, theKing));
            colony.away().setAwayForTests(true);
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = byId(helper, id[0]);
            AwayReport report = colony.away().report();
            AntColoniesMod.LOGGER.info("[away-test] king fix, starving pair: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(report.describe().contains("the queen starved") && report.didKingStarve() && report.describe().contains("the king starved"),
                    "the queen and the king should starve while away: " + report.describe());
            helper.assertTrue(report.get(AwayReport.What.QUEEN_FED) == 0 && report.get(AwayReport.What.KING_FED) == 0,
                    "there was nothing to feed them: " + report.describe());
            helper.assertTrue(colony.getCount(Caste.WORKER) == 20 && report.get(AwayReport.What.STARVED) == 0,
                    "the workers, just fed, should live: " + colony.getCount(Caste.WORKER));
            colony.away().setAwayForTests(false);
            unloaded.values().forEach(tag -> loadAgain(level, tag));
        });
        helper.runAfterDelay(650, () -> {
            Colony colony = byId(helper, id[0]);
            Entity theQueen = level.getEntity(royals[0]);
            Entity theKing = level.getEntity(royals[1]);
            AntColoniesMod.LOGGER.info("[away-test] king fix, starving pair back: queen {}, king {}, {}", theQueen, theKing, colony.away().describe(level));
            helper.assertTrue(theQueen == null || !theQueen.isAlive(), "the queen who starved while away should die as her land loads");
            helper.assertTrue(theKing == null || !theKing.isAlive(), "the king who starved while away should die as his land loads");
            helper.assertTrue(colony.isQueenless() && colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) == 0,
                    "the colony should carry on without them: kings " + colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE));
            helper.succeed();
        });
    }

    /** Brain part 3: a termite royal pair raise their first workers while away (see {@link #foundsWhileAway}). */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void aFoundingTermitePairRaiseTheirFirstWorkersWhileAway(GameTestHelper helper) {
        foundsWhileAway(helper, new AwayKit.Founders(com.antcolonies.species.Species.MACROTERMES, h -> {
            h.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
            h.runAfterDelay(5, () -> h.spawn(SavannaEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1));
        }, com.antcolonies.entity.TermiteKingEntity.class, com.antcolonies.entity.TermiteKingEntity.CASTE, true));
    }
}
