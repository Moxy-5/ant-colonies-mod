package com.antcolonies.desert.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.desert.colony.DesertParts;
import com.antcolonies.colony.Neighbours;
import com.antcolonies.desert.colony.TrunkTrails;
import com.antcolonies.desert.entity.WorkerAntEntity;
import com.antcolonies.desert.registry.DesertEntities;
import com.antcolonies.desert.DesertSpecies;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * Brain step 4g, step 5: neighbours ({@code colony.Neighbours}; the user's answers of 2026-10-18). Category {@code colony}.
 * The red harvester ant's scene (the split, step S4; the termites' is in {@link TermiteNeighbourTests}).
 */
@GameTestHolder(DesertMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterNeighbourTests {
    /** Where the two colonies' queens land (x, in the arena), on one line west to east, 30 blocks apart. */
    private static final int WEST = 12;

    private static final int EAST = 42;

    private static final int ROW = 12;

    /** A real worker of {@code colony}, out on the surface here (an old forager). */
    private static WorkerAntEntity workerOf(GameTestHelper helper, Colony colony, int x, int z) {
        WorkerAntEntity ant = helper.spawn(DesertEntities.WORKER_ANT.get(), x, SURFACE, z);
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
        helper.spawn(DesertEntities.QUEEN_ANT.get(), WEST, SURFACE, ROW);
        helper.spawn(DesertEntities.QUEEN_ANT.get(), EAST, SURFACE, ROW);
        Colony[] both = new Colony[2];
        WorkerAntEntity[] met = new WorkerAntEntity[3];
        int groundY = helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY();
        helper.runAfterDelay(10, () -> {
            List<Colony> found = colonies(helper, DesertSpecies.RED_HARVESTER_ANT);
            helper.assertTrue(found.size() == 2, "there should be two harvester colonies: " + found.size());
            both[0] = found.get(0);
            both[1] = found.get(1);
            for (Colony colony : both) {
                colony.setHungerOn(false);
                TestScenes.digNestAtOnce(level, colony);
                colony.population().setOnForTests(false);
                colony.population().setViewersForTests(List.of());
                colony.part(DesertParts.PATROL).orElseThrow().setOnForTests(false);
            }
            both[0].setAgeForTests(level, 10);
            both[1].setAgeForTests(level, 0);
            // The old colony's east trail, out 10 blocks.
            BlockPos west = both[0].getCenter();
            for (int i = 0; i < 9; i++) {
                both[0].part(DesertParts.TRUNK_TRAILS).orElseThrow().setOut(level, new BlockPos(west.getX() + 10, groundY, west.getZ()));
            }
            helper.assertTrue(both[0].part(DesertParts.TRUNK_TRAILS).orElseThrow().trails().size() == 1, "the old colony should have its east trail: "
                    + both[0].part(DesertParts.TRUNK_TRAILS).orElseThrow().describe(level));
            // One worker of each halfway, a block apart.
            int half = (WEST + EAST) / 2;
            met[0] = workerOf(helper, both[0], half, ROW);
            met[1] = workerOf(helper, both[1], half + 1, ROW);
        });
        helper.runAfterDelay(45, () -> {
            Colony old = both[0];
            Colony young = both[1];
            DesertMod.LOGGER.info("[neighbour-test] old{} / young{}", old.neighbours().describe(level), young.neighbours().describe(level));
            helper.assertTrue(old.neighbours().meetings().size() == 1 && young.neighbours().meetings().size() == 1,
                    "the two workers should each have told their colony of the meeting: " + old.neighbours().describe(level) + " / "
                            + young.neighbours().describe(level) + " | may meet: " + old.neighbours().canMeet(young) + ", "
                            + young.neighbours().canMeet(old) + " | middles " + old.getCenter() + ", " + young.getCenter()
                            + " | workers " + met[0].blockPosition() + " sky " + level.canSeeSky(met[0].blockPosition()) + " alive "
                            + met[0].isAlive() + ", " + met[1].blockPosition() + " sky " + level.canSeeSky(met[1].blockPosition())
                            + " alive " + met[1].isAlive() + " | tests near: "
                            + com.antcolonies.gametest.TestIsolation.describeTestsNear(old.getCenter(), 40));
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
            helper.assertTrue("neighbours".equals(old.whoKeepsOff(level, past)),
                    "the neighbours should keep the old colony off past the meeting (colony parts step 6), not " + old.whoKeepsOff(level, past));
            // Its east trail's tip is held short of the meeting place, and no searcher goes out that trail.
            TrunkTrails trails = old.part(DesertParts.TRUNK_TRAILS).orElseThrow();
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
            TrunkTrails trails = young.part(DesertParts.TRUNK_TRAILS).orElseThrow();
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
}
