package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.TrunkTrails;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The brain, part 4: lasting trails ({@link TrunkTrails}, step 4a). Category {@code colony}.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TrailTests {
    /** How far out (blocks) the test's food lies, east and south of the nest. */
    private static final int FOOD_OUT = 15;

    /**
     * Step 4a, the harvester ant's trunk trails, with the trips sent straight to the brain (as its numbers make them).
     * Grass ground runs east and south from the nest, with short grass on it east and poppies south. 7 trips east lay no
     * trail; the 8th does, and the forager is told to leave it at the block nearest its food; the new trail is as strong as
     * the 8 trips that laid it, so a minute unused it is still there (it once faded a second later). A young colony keeps 1
     * trail: 8 trips south lay none; with 1,500 workers it may keep more, and the next trip south lays one (the earlier
     * trips are remembered). 60 trips each way: the east trail wears bare out to the food (the grass cut, its seeds left
     * lying; coarse dirt), not past it; the south one waits while a pretend player looks at it, and wears bare once
     * nobody does (a low wall hides the east trail from them). Saved and loaded with the colony. Unused for a week it fades, and its bare ground grows back to grass.
     * <p>Brain step 4b part 3, in the same scene: with the patrollers on, a new round's patrol (the share of the workers,
     * all numbers here) walks the two trunk trails first; once they are back food along the trails is open; a patroller
     * killed on the east trail shuts it for the day (the south one stays open) and fewer foragers may go out; the
     * patrol is saved and loaded with the colony.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void foragersWearLastingTrunkTrailsThatFadeAndGrowBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PopulationTests.startAntColony(helper);
        helper.onEachTick(() -> AwayTests.noRealWorkers(helper));
        BlockPos[] center = new BlockPos[1];
        int[] bare = new int[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = PopulationTests.antColony(helper);
            colony.population().setOnForTests(false); // (the numbers stay numbers)
            center[0] = colony.getCenter();
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            for (int t = 1; t <= 26; t++) {
                BlockPos east = new BlockPos(center[0].getX() + t, groundY, center[0].getZ());
                BlockPos south = new BlockPos(center[0].getX(), groundY, center[0].getZ() + t);
                level.setBlock(east, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(south, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                if (t >= 4) {
                    level.setBlock(east.above(), Blocks.SHORT_GRASS.defaultBlockState(), 3);
                    level.setBlock(south.above(), Blocks.POPPY.defaultBlockState(), 3);
                }
            }
            TrunkTrails trails = colony.trunkTrails();
            BlockPos eastFood = new BlockPos(center[0].getX() + FOOD_OUT, groundY + 1, center[0].getZ());
            BlockPos southFood = new BlockPos(center[0].getX(), groundY + 1, center[0].getZ() + FOOD_OUT);
            for (int i = 0; i < 7; i++) {
                helper.assertTrue(trails.setOut(level, eastFood) == null, "no trail should go east yet");
            }
            helper.assertTrue(trails.trails().isEmpty(), "7 trips should lay no trail: " + trails.describe(level));
            BlockPos via = trails.setOut(level, eastFood);
            helper.assertTrue(trails.trails().size() == 1 && Math.abs(trails.trails().get(0).angle()) < Math.toRadians(10),
                    "the 8th trip should lay a trail east: " + trails.describe(level));
            helper.assertTrue(via != null && via.equals(new BlockPos(center[0].getX() + FOOD_OUT, groundY + 1, center[0].getZ())),
                    "the forager should leave the trail by its food: " + via);
            // A new trail is as strong as the trips that laid it (it used to start at the fade line and fade a second
            // later, the user's live game 2026-10-16): a minute unused, it is still there after the colony's next look.
            trails.ageForTests(60 * 20);
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = PopulationTests.antColony(helper);
            TrunkTrails trails = colony.trunkTrails();
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            BlockPos eastFood = new BlockPos(center[0].getX() + FOOD_OUT, groundY + 1, center[0].getZ());
            BlockPos southFood = new BlockPos(center[0].getX(), groundY + 1, center[0].getZ() + FOOD_OUT);
            int laidBy = colony.profile().food().trails().trunk().orElseThrow().newTrailTrips();
            helper.assertTrue(trails.trails().size() == 1 && trails.strength(level, trails.trails().get(0)) > laidBy - 1,
                    "a trail laid by " + laidBy + " trips should not fade a minute later: " + trails.describe(level));
            for (int i = 0; i < 8; i++) {
                trails.setOut(level, southFood);
            }
            helper.assertTrue(trails.trails().size() == 1, "a young colony should keep 1 trail: " + trails.describe(level));
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 1500);
            trails.setOut(level, southFood);
            helper.assertTrue(trails.trails().size() == 2, "a big colony should lay a trail south: " + trails.describe(level));
            for (int i = 0; i < 60; i++) {
                trails.setOut(level, eastFood);
                trails.setOut(level, southFood);
            }
            // A pretend player stands right by the south trail; a low wall hides the east one from them.
            for (int x = 3; x <= FOOD_OUT + 4; x++) {
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(new BlockPos(center[0].getX() + x, groundY + y, center[0].getZ() + 1), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(new BlockPos(center[0].getX() + 8, groundY + 2, center[0].getZ() + 8))));
            trails.checkSoonForTests();
        });
        helper.runAfterDelay(85, () -> {
            Colony colony = PopulationTests.antColony(helper);
            AntColoniesMod.LOGGER.info("[trail-test] worn: {}", colony.trunkTrails().describe(level));
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            for (int t = 4; t <= FOOD_OUT; t++) {
                BlockPos east = new BlockPos(center[0].getX() + t, groundY, center[0].getZ());
                helper.assertTrue(level.getBlockState(east).is(Blocks.COARSE_DIRT) && level.getBlockState(east.above()).isAir(),
                        "the east trail should be worn bare " + t + " out: " + level.getBlockState(east) + ", " + level.getBlockState(east.above()));
                BlockPos south = new BlockPos(center[0].getX(), groundY, center[0].getZ() + t);
                helper.assertTrue(level.getBlockState(south).is(Blocks.GRASS_BLOCK) && level.getBlockState(south.above()).is(Blocks.POPPY),
                        "the south trail should wait while a player looks: " + t + " out");
            }
            BlockPos past = new BlockPos(center[0].getX() + FOOD_OUT + 3, groundY, center[0].getZ());
            helper.assertTrue(level.getBlockState(past).is(Blocks.GRASS_BLOCK) && level.getBlockState(past.above()).is(Blocks.SHORT_GRASS),
                    "the trail past the food should not be worn");
            AABB eastTrail = new AABB(center[0]).expandTowards(FOOD_OUT + 1, 0, 0).inflate(1, 3, 1);
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, eastTrail).isEmpty(), "the cut grass should leave its seeds lying");
            colony.population().setViewersForTests(List.of());
            colony.trunkTrails().checkSoonForTests();
            colony.patrol().setOnForTests(true); // brain step 4b: a new round, with its patrol (it sees the day clock open, 7:00)
        });
        helper.runAfterDelay(110, () -> {
            Colony colony = PopulationTests.antColony(helper);
            List<String> ways = colony.patrol().waysForTests();
            AntColoniesMod.LOGGER.info("[trail-test] patrol: {} ways {}", colony.patrol().describe(level), ways);
            helper.assertTrue(colony.patrol().isRoundOn() && ways.size() >= 2 && ways.get(0).endsWith("(trail)") && ways.get(1).endsWith("(trail)")
                    && ways.contains("east (trail)") && ways.contains("south (trail)"), "the patrol should walk the two trunk trails first: " + ways);
            helper.assertTrue(colony.patrol().sentThisRound() == colony.profile().food().patrol().orElseThrow().dawnPatrol(colony.getCount(WorkerAntEntity.CASTE)),
                    "the dawn patrol should be the share of the workers: " + colony.patrol().describe(level));
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            helper.assertTrue(!colony.patrol().isOpen(level, new BlockPos(center[0].getX() + FOOD_OUT, groundY + 1, center[0].getZ())),
                    "nothing is open before a patroller is back");
            colony.patrol().bringNumbersHomeForTests();
        });
        // (25 ticks: colonies think once a second, every 20 ticks; 15 ticks failed about 1 run in 4)
        helper.runAfterDelay(135, () -> {
            Colony colony = PopulationTests.antColony(helper);
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            BlockPos eastFood = new BlockPos(center[0].getX() + FOOD_OUT, groundY + 1, center[0].getZ());
            BlockPos southFood = new BlockPos(center[0].getX(), groundY + 1, center[0].getZ() + FOOD_OUT);
            helper.assertTrue(colony.patrol().backThisRound() > 0 && colony.patrol().isOpen(level, eastFood) && colony.patrol().isOpen(level, southFood),
                    "with the patrol back the trails should be open: " + colony.patrol().describe(level));
            colony.patrol().patrollerKilledForTests(level, 0.0);
            helper.assertTrue(!colony.patrol().isOpen(level, eastFood) && colony.patrol().isOpen(level, southFood),
                    "a patroller killed east should shut the east trail only: " + colony.patrol().describe(level));
            helper.assertTrue(colony.patrol().dangerToday() && colony.patrol().foragerShareToday(level) < 1.0F,
                    "after a patroller is lost fewer foragers may go out today");
            Colony copy = Colony.load(colony.save());
            copy.patrol().setOnForTests(true);
            helper.assertTrue(copy.patrol().describe(level).equals(colony.patrol().describe(level)) && !copy.patrol().isOpen(level, eastFood),
                    "the patrol should be saved: " + copy.patrol().describe(level) + " / " + colony.patrol().describe(level));
            for (int t = 4; t <= FOOD_OUT; t++) {
                BlockPos south = new BlockPos(center[0].getX(), groundY, center[0].getZ() + t);
                helper.assertTrue(level.getBlockState(south).is(Blocks.COARSE_DIRT) && level.getBlockState(south.above()).isAir(),
                        "with nobody looking the south trail should wear bare " + t + " out");
            }
            // Saved and loaded with the colony.
            List<TrunkTrails.Trail> before = colony.trunkTrails().trails();
            List<TrunkTrails.Trail> after = Colony.load(colony.save()).trunkTrails().trails();
            helper.assertTrue(after.size() == before.size(), "the trails should be saved: " + after.size());
            for (int i = 0; i < before.size(); i++) {
                TrunkTrails.Trail a = before.get(i);
                TrunkTrails.Trail b = after.get(i);
                helper.assertTrue(a.angle() == b.angle() && a.reach() == b.reach() && a.length() == b.length(), "trail " + i + " should load the same");
                for (int c = 0; c < a.length(); c++) {
                    helper.assertTrue(a.wear(c) == b.wear(c) && a.isBare(c) == b.isBare(c), "trail " + i + " column " + c + " should load the same");
                    bare[0] += a.isBare(c) ? 1 : 0;
                }
            }
            // A week unused: they fade.
            colony.trunkTrails().ageForTests(7 * 24000L);
            colony.trunkTrails().checkSoonForTests();
        });
        helper.runAfterDelay(175, () -> {
            Colony colony = PopulationTests.antColony(helper);
            helper.assertTrue(colony.trunkTrails().trails().isEmpty(), "unused for a week the trails should fade: " + colony.trunkTrails().describe(level));
            helper.assertTrue(colony.trunkTrails().regrowing() == bare[0] && bare[0] >= 2 * (FOOD_OUT - 3),
                    "their " + bare[0] + " bare blocks should be growing back: " + colony.trunkTrails().regrowing());
            colony.trunkTrails().growBackSoonForTests();
        });
        helper.runAfterDelay(215, () -> {
            Colony colony = PopulationTests.antColony(helper);
            int groundY = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
            for (int t = 4; t <= FOOD_OUT; t++) {
                helper.assertTrue(level.getBlockState(new BlockPos(center[0].getX() + t, groundY, center[0].getZ())).is(Blocks.GRASS_BLOCK)
                        && level.getBlockState(new BlockPos(center[0].getX(), groundY, center[0].getZ() + t)).is(Blocks.GRASS_BLOCK),
                        "the bare ground should grow back to grass " + t + " out");
            }
            helper.assertTrue(colony.trunkTrails().regrowing() == 0, "nothing should be left to grow back: " + colony.trunkTrails().regrowing());
            helper.succeed();
        });
    }
}
