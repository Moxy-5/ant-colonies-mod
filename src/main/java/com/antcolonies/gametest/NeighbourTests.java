package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.Galleries;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Neighbours;
import com.antcolonies.colony.TrunkTrails;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Brain step 4g, step 5: neighbours ({@code colony.Neighbours}; the user's answers of 2026-10-18). Category {@code colony}.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class NeighbourTests {
    /** Where the two colonies' queens land (x, in the arena), on one line west to east, 30 blocks apart. */
    private static final int WEST = 12;
    private static final int EAST = 42;
    private static final int ROW = 12;

    /** The colonies of this species in the test's arena with a living queen, west first. */
    static List<Colony> colonies(GameTestHelper helper, ResourceLocation species) {
        AABB area = helper.getBounds();
        List<Colony> found = new ArrayList<>(ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .toList());
        found.sort(Comparator.comparingInt(c -> c.getCenter().getX()));
        return found;
    }

    /** A real worker of {@code colony}, out on the surface here (an old forager). */
    private static WorkerAntEntity workerOf(GameTestHelper helper, Colony colony, int x, int z) {
        WorkerAntEntity ant = helper.spawn(ModEntities.WORKER_ANT.get(), x, SURFACE, z);
        ant.comeOutOfNest(colony, helper.getLevel().getGameTime() - colony.profile().worker().builderUntilTicks() - 1000);
        return ant;
    }

    /**
     * Two harvester colonies 30 blocks apart on a flat meadow: the west one old (10 game days), the east one just founded.
     * One worker of each meets the other halfway: both colonies count a meeting there. The old one yields at once: food
     * past the meeting place that way is no longer foraged (food short of it and the other ways still are), its east trail's
     * tip is held short of the place, no searcher goes out that trail; the young one does not yield. An east worker
     * standing on the west colony's trail counts a meeting too (stepping onto a neighbour's trail). The young colony
     * yields only once it holds 4 meetings at one place (3 are not enough); then it lays no new trail that way, but does
     * elsewhere. Saved and loaded with the colony, with the colony's age. Two days on the old colony's single meeting has
     * faded to half and it forages that way again; a little later it is forgotten.
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void oldColoniesYieldToNeighboursAndYoungOnesPushOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 56; x++) {
            for (int z = 0; z <= 26; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.QUEEN_ANT.get(), WEST, SURFACE, ROW);
        helper.spawn(ModEntities.QUEEN_ANT.get(), EAST, SURFACE, ROW);
        Colony[] both = new Colony[2];
        WorkerAntEntity[] met = new WorkerAntEntity[3];
        int groundY = helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        helper.runAfterDelay(10, () -> {
            List<Colony> found = colonies(helper, Species.RED_HARVESTER_ANT);
            helper.assertTrue(found.size() == 2, "there should be two harvester colonies: " + found.size());
            both[0] = found.get(0);
            both[1] = found.get(1);
            for (Colony colony : both) {
                colony.setHungerOn(false);
                PopulationTests.digNestAtOnce(level, colony);
                colony.population().setOnForTests(false);
                colony.population().setViewersForTests(List.of());
                colony.patrol().setOnForTests(false);
            }
            both[0].setAgeForTests(level, 10);
            both[1].setAgeForTests(level, 0);
            // The old colony's east trail, out 10 blocks.
            BlockPos west = both[0].getCenter();
            for (int i = 0; i < 9; i++) {
                both[0].trunkTrails().setOut(level, new BlockPos(west.getX() + 10, groundY, west.getZ()));
            }
            helper.assertTrue(both[0].trunkTrails().trails().size() == 1, "the old colony should have its east trail: "
                    + both[0].trunkTrails().describe(level));
            // One worker of each halfway, a block apart.
            int half = (WEST + EAST) / 2;
            met[0] = workerOf(helper, both[0], half, ROW);
            met[1] = workerOf(helper, both[1], half + 1, ROW);
        });
        helper.runAfterDelay(45, () -> {
            Colony old = both[0];
            Colony young = both[1];
            AntColoniesMod.LOGGER.info("[neighbour-test] old{} / young{}", old.neighbours().describe(level), young.neighbours().describe(level));
            helper.assertTrue(old.neighbours().meetings().size() == 1 && young.neighbours().meetings().size() == 1,
                    "the two workers should each have told their colony of the meeting: " + old.neighbours().describe(level) + " / "
                            + young.neighbours().describe(level));
            helper.assertTrue(old.ageDays(level) >= 9.9 && young.ageDays(level) < 0.1, "the ages: " + old.ageDays(level) + ", " + young.ageDays(level));
            met[0].discard();
            met[1].discard();
            BlockPos w = old.getCenter();
            BlockPos meeting = old.neighbours().meetings().get(0).pos();
            int toMeeting = meeting.getX() - w.getX();
            BlockPos past = new BlockPos(w.getX() + toMeeting + 2, groundY, w.getZ() + 2);
            BlockPos shortOf = new BlockPos(w.getX() + toMeeting - 6, groundY, w.getZ());
            BlockPos south = new BlockPos(w.getX(), groundY, w.getZ() + toMeeting + 2);
            helper.assertTrue(!old.mayForageAt(level, past) && old.mayForageAt(level, shortOf) && old.mayForageAt(level, south),
                    "the old colony should yield past the meeting (" + toMeeting + " blocks east) only: " + old.neighbours().describe(level));
            // Its east trail's tip is held short of the meeting place, and no searcher goes out that trail.
            TrunkTrails trails = old.trunkTrails();
            for (int i = 0; i < 5; i++) {
                trails.setOut(level, new BlockPos(w.getX() + toMeeting + 6, groundY, w.getZ()));
            }
            int tip = trails.reachBlocks(trails.trails().get(0));
            helper.assertTrue(tip <= toMeeting - 2, "the old colony's east tip should be held short of the meeting " + toMeeting + " blocks out: " + tip);
            for (int i = 0; i < 20; i++) {
                helper.assertTrue(trails.pickForSearch(level, old.random()) == null, "no searcher should go out the trail toward the neighbour");
            }
            BlockPos e = young.getCenter();
            helper.assertTrue(young.mayForageAt(level, new BlockPos(e.getX() - (e.getX() - meeting.getX()) - 4, groundY, e.getZ())),
                    "the young colony should not yield after 1 meeting: " + young.neighbours().describe(level));
            // An east worker standing on the old colony's trail.
            met[2] = workerOf(helper, young, WEST + 7, ROW);
        });
        helper.runAfterDelay(80, () -> {
            Colony old = both[0];
            Colony young = both[1];
            met[2].discard();
            helper.assertTrue(young.neighbours().meetings().size() == 2, "standing on the old colony's trail should be a meeting: "
                    + young.neighbours().describe(level));
            Neighbours.Meeting halfway = young.neighbours().meetings().stream()
                    .max(Comparator.comparingInt(m -> m.pos().getX())).orElseThrow();
            BlockPos at = halfway.pos();
            young.neighbours().met(level, at, old.getId(), UUID.randomUUID());
            young.neighbours().met(level, at, old.getId(), UUID.randomUUID());
            BlockPos e = young.getCenter();
            BlockPos westward = new BlockPos(at.getX() - 4, groundY, e.getZ());
            helper.assertTrue(young.mayForageAt(level, westward), "3 meetings should not make a new colony yield: " + young.neighbours().describe(level));
            young.neighbours().met(level, at, old.getId(), UUID.randomUUID());
            helper.assertTrue(!young.mayForageAt(level, westward), "4 meetings should make a new colony yield: " + young.neighbours().describe(level));
            // No new trail toward the neighbour (food short of the meeting, on its side), but one the other way.
            TrunkTrails trails = young.trunkTrails();
            for (int i = 0; i < 10; i++) {
                trails.setOut(level, new BlockPos(e.getX() - 8, groundY, e.getZ()));
            }
            helper.assertTrue(trails.trails().isEmpty(), "the young colony should lay no trail toward the neighbour it yields to: " + trails.describe(level));
            for (int i = 0; i < 10; i++) {
                trails.setOut(level, new BlockPos(e.getX() + 8, groundY, e.getZ()));
            }
            helper.assertTrue(trails.trails().size() == 1, "the young colony should lay a trail away from the neighbour: " + trails.describe(level));
            // Saved and loaded, with the age.
            Colony copy = Colony.load(old.save());
            helper.assertTrue(copy.neighbours().meetings().size() == old.neighbours().meetings().size()
                    && Math.abs(copy.ageDays(level) - old.ageDays(level)) < 0.01, "the meetings and the age should be saved: "
                    + copy.neighbours().describe(level));
            helper.assertTrue(old.neighbours().describe(level).contains("neighbours:"), "the colony list should tell the neighbours");
            // Two days: the old colony's one meeting has faded to half, and it forages that way again; a little more and it is forgotten.
            old.neighbours().ageForTests(48000L);
            BlockPos w = old.getCenter();
            int toMeeting = old.neighbours().meetings().get(0).pos().getX() - w.getX();
            helper.assertTrue(old.mayForageAt(level, new BlockPos(w.getX() + toMeeting + 2, groundY, w.getZ() + 2)) && old.neighbours().meetings().size() == 1,
                    "two days on the old colony should forage that way again, still remembering: " + old.neighbours().describe(level));
            old.neighbours().ageForTests(4800L);
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(both[0].neighbours().meetings().isEmpty(), "the faded meeting should be forgotten: " + both[0].neighbours().describe(level));
            helper.succeed();
        });
    }

    /**
     * Two Macrotermes colonies 44 blocks apart, their galleries growing toward each other (dug at once here): the west one
     * old (10 game days), the east one just founded. Where their galleries come within 3 blocks both colonies count a
     * meeting; the old one yields there at once (its tip stops, no piece is dug that way), the young one only after
     * several meetings at one place, and then walls its piece off (packed with soil). The two webs never join: no cell of
     * one comes within a block of the other's.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true, batch = "neighbour_termites")
    public static void neighbouringTermitesWallOffTheirGalleries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int west = 10;
        int east = 54;
        int row = 32;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), west, SURFACE, row);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), east, SURFACE, row);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), west - 2 + i % 4, SURFACE, row - 3 - i / 4);
                helper.spawn(ModEntities.TERMITE_WORKER.get(), east - 2 + i % 4, SURFACE, row - 3 - i / 4);
            }
        });
        TestClock.still(helper, 12); // midday: the termites stay in, and dig
        Colony[] both = new Colony[2];
        boolean[] started = {false};
        helper.runAfterDelay(10, () -> {
            List<Colony> found = colonies(helper, Species.MACROTERMES);
            helper.assertTrue(found.size() == 2 && found.stream().noneMatch(Colony::isFounding), "there should be two termite colonies with workers: " + found.size());
            both[0] = found.get(0);
            both[1] = found.get(1);
            long now = level.getGameTime();
            for (Colony colony : both) {
                TermiteMoundTests.colony(helper); // (keeps the food map and the alarm in the arena; the first colony only)
                colony.foodMap().setTestArea(helper.getBounds().expandTowards(0, 16, 0));
                colony.alarm().setTestArea(helper.getBounds());
                for (com.antcolonies.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.entity.TermiteWorkerEntity.class)) {
                    worker.setGrownUpAt(now - colony.profile().worker().nurseUntilTicks() - 1000);
                }
                TermiteSoldierTests.digNestAtOnce(level, colony);
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now - colony.profile().worker().builderUntilTicks() - 1000, 900);
                colony.nest().galleries().setSwitchForTests(true);
                colony.nest().galleries().setReachForTests(30);
            }
            both[0].setAgeForTests(level, 10);
            both[1].setAgeForTests(level, 0);
            started[0] = true;
        });
        helper.onEachTick(() -> {
            if (!started[0]) {
                return;
            }
            for (Colony colony : both) {
                Galleries galleries = colony.nest().galleries();
                for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                    for (BlockPos cell : galleries.digCells(tunnel)) {
                        if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
            Colony old = both[0];
            Colony young = both[1];
            // The webs never join.
            for (NestPlan.ForagingTunnel tunnel : old.foragingHoles().tunnels(level)) {
                for (BlockPos cell : tunnel.tunnel()) {
                    BlockPos theirs = young.nest().galleries().galleryCellNear(cell, 1.0);
                    helper.assertTrue(theirs == null, "the two colonies' galleries touch at " + helper.relativePos(cell) + " and " + (theirs == null ? null
                            : helper.relativePos(theirs)));
                }
            }
            long tick = helper.getTick();
            if (tick % 500 == 0) {
                AntColoniesMod.LOGGER.info("[neighbour-test] tick {}: old{}{} / young{}{}", tick, old.nest().galleries().describe(level), old.neighbours().describe(level),
                        young.nest().galleries().describe(level), young.neighbours().describe(level));
            }
            boolean oldMet = !old.neighbours().meetings().isEmpty();
            boolean youngMet = !young.neighbours().meetings().isEmpty();
            if (oldMet && youngMet) {
                helper.assertTrue(!old.neighbours().yielding(level).isEmpty(), "the old colony should yield at its first meeting: " + old.neighbours().describe(level));
            }
            int most = young.neighbours().meetings().stream().mapToInt(Neighbours.Meeting::count).max().orElse(0);
            if (young.nest().galleries().walledOff() > 0) {
                helper.assertTrue(most >= 4 || young.neighbours().meetings().size() > 1, "the young colony walled off its gallery after too few meetings: "
                        + young.neighbours().describe(level));
                helper.assertTrue(oldMet, "the old colony never felt the meeting: " + old.neighbours().describe(level));
                helper.succeed();
            }
            helper.assertTrue(tick < 38000, "the young colony never walled off a gallery: old" + old.nest().galleries().describe(level)
                    + old.neighbours().describe(level) + " / young" + young.nest().galleries().describe(level) + young.neighbours().describe(level));
        });
    }
}
