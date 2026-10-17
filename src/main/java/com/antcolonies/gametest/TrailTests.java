package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.GramaGrassBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.TrunkTrails;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;

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
    /** A trip this far out (blocks) moves the east trail's tip out (long trails). */
    private static final int FAR_OUT = 30;

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
            // Long trails (2026-10-17): a trip to food past the east trail's tip moves the tip out to it, and the colony's
            // range is its longest trail's tip (a young harvester colony reaches 12 blocks).
            helper.assertTrue(Math.abs(colony.foragingRadius() - FOOD_OUT) <= 1, "the range should be the trails' tips, " + FOOD_OUT + " blocks out: "
                    + colony.foragingRadius() + ", " + trails.describe(level));
            trails.setOut(level, new BlockPos(center[0].getX() + FAR_OUT, groundY + 1, center[0].getZ()));
            TrunkTrails.Trail east = trails.trails().get(0);
            helper.assertTrue(Math.abs(trails.reachBlocks(east) - FAR_OUT) <= 1 && colony.foragingRadius() >= FAR_OUT - 1,
                    "a trip " + FAR_OUT + " blocks out should move the east tip out there: " + trails.describe(level) + ", range " + colony.foragingRadius());
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
            // Five days unused: their traffic no longer holds them out to the food, so their tips pull back.
            colony.trunkTrails().ageForTests(5 * 24000L);
        });
        helper.runAfterDelay(160, () -> {
            Colony colony = PopulationTests.antColony(helper);
            TrunkTrails trails = colony.trunkTrails();
            helper.assertTrue(trails.trails().size() == 2, "five days unused the trails should still be there: " + trails.describe(level));
            for (TrunkTrails.Trail trail : trails.trails()) {
                helper.assertTrue(trails.reachBlocks(trail) >= 10 && trails.reachBlocks(trail) <= 13,
                        "a weak trail's tip should pull back to about 12 blocks: " + trails.describe(level));
            }
            helper.assertTrue(trails.regrowing() > 0, "the bare ground past the tips should be growing back: " + trails.describe(level));
            helper.assertTrue(colony.foragingRadius() == colony.profile().food().radius(), "the range should shrink with the trails: " + colony.foragingRadius());
            // A week unused: they fade.
            colony.trunkTrails().ageForTests(7 * 24000L);
            colony.trunkTrails().checkSoonForTests();
        });
        helper.runAfterDelay(185, () -> {
            Colony colony = PopulationTests.antColony(helper);
            helper.assertTrue(colony.trunkTrails().trails().isEmpty(), "unused for a week the trails should fade: " + colony.trunkTrails().describe(level));
            helper.assertTrue(colony.trunkTrails().regrowing() == bare[0] && bare[0] >= 2 * (FOOD_OUT - 3),
                    "their " + bare[0] + " bare blocks should be growing back: " + colony.trunkTrails().regrowing());
            colony.trunkTrails().growBackSoonForTests();
        });
        helper.runAfterDelay(225, () -> {
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

    /** The far meadow's near and far edge (blocks east of the nest's middle), and how far it reaches north and south. */
    private static final int MEADOW_FROM = 34;
    private static final int MEADOW_TO = 40;
    private static final int MEADOW_SIDE = 2;
    /** Ticks for a finder that cut at the meadow just before it went to get home and call (Recruits: 3,600 to lay, 200 to call). */
    private static final int HOME_FROM_THE_MEADOW = 4000;
    /** The old trail east reaches this far out when the test begins. */
    private static final int OLD_TIP = 26;

    /**
     * Long trails step 4 (2026-10-17), the far meadow: a finished harvester nest with 30 real workers, an old trunk
     * trail east whose food is eaten (its tip 26 blocks out), and a ripe blue grama meadow 34 to 40 blocks out, past
     * the tip, on a moving test clock. Real <i>Pogonomyrmex</i> foragers walk out a trail and fan out past its end; a
     * find out there pulls the trail out to it, and a rich patch is recruited to. Checks: a searcher fanning out past
     * the tip finds the meadow; it lays a scent line home to it and calls nestmates out, and more than one of those
     * called out takes food there; the trail's tip moves out to the meadow and the colony's range with it. Then the
     * meadow is gone (cut bare): once the finders on their way home from it are in, the scent line fades within the
     * day and, unused, the tip pulls back from the meadow within a week of game days (aged a day at a time). The trails stop at the arena's edge ({@code TestIsolation}).
     */
    @GameTest(template = "arena", timeoutTicks = 72000, skyAccess = true, batch = "far_meadow")
    public static void aFarMeadowPastTheTrailsTipIsFoundRecruitedToAndPullsTheTrailOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PopulationTests.startAntColony(helper);
        // Ground east of the nest out past the meadow (the arena's own ground is only 25 blocks across).
        for (int x = 25; x <= MIDDLE + MEADOW_TO + 6; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = SURFACE - 4; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        List<BlockPos> meadow = new ArrayList<>();
        for (int x = MIDDLE + MEADOW_FROM; x <= MIDDLE + MEADOW_TO; x++) {
            for (int z = MIDDLE - MEADOW_SIDE; z <= MIDDLE + MEADOW_SIDE; z++) {
                meadow.add(new BlockPos(x, SURFACE, z));
            }
        }
        long[] phase = {0, -1, -1, -1, -1}; // 0 waiting, 1 running, 2 meadow gone; then the ticks: found, recruited, tip out, gone
        Set<UUID> called = new HashSet<>();
        Set<UUID> calledAtTheMeadow = new HashSet<>();
        int[] days = {0};
        helper.runAfterDelay(15, () -> {
            Colony colony = PopulationTests.antColony(helper);
            for (BlockPos grama : meadow) {
                helper.setBlock(grama, ModBlocks.BLUE_GRAMA.get().defaultBlockState().setValue(GramaGrassBlock.STAGE, GramaGrassBlock.Stage.RIPE));
            }
            long now = level.getGameTime();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 250000, 24); // old: foragers
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 100000, 6); // middle-aged: builders
            // The old trail east: its food, 26 blocks out, is eaten (12 trips laid and held it).
            BlockPos center = colony.getCenter();
            BlockPos oldFood = new BlockPos(center.getX() + OLD_TIP, center.getY(), center.getZ());
            for (int i = 0; i < 12; i++) {
                colony.trunkTrails().setOut(level, oldFood);
            }
            helper.assertTrue(colony.trunkTrails().trails().size() == 1 && Math.abs(colony.trunkTrails().longestTip() - OLD_TIP) <= 1,
                    "the old trail east should reach " + OLD_TIP + " blocks: " + colony.trunkTrails().describe(level));
            TestClock.moving(helper, 6.5);
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = PopulationTests.antColony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            long tick = helper.getTick();
            BlockPos center = colony.getCenter();
            TrunkTrails trails = colony.trunkTrails();
            if (phase[0] == 1) {
                if (phase[1] < 0) {
                    for (BlockPos grama : meadow) {
                        if (colony.food().getPiecesCut(helper.absolutePos(grama)) > 0) {
                            phase[1] = tick;
                            AntColoniesMod.LOGGER.info("[far-meadow] tick {}: a searcher found the meadow at {} | {}", tick, grama.toShortString(), trails.describe(level));
                            break;
                        }
                    }
                }
                for (var line : colony.recruits().lines()) {
                    int out = line.patch().getX() - center.getX();
                    helper.assertTrue(out >= MEADOW_FROM - 5 && Math.abs(line.patch().getZ() - center.getZ()) <= MEADOW_SIDE + 5,
                            "a scent line was laid to " + line.patch().subtract(center).toShortString() + ", not the far meadow");
                }
                for (WorkerAntEntity ant : TestIsolation.insectsOf(level, colony, WorkerAntEntity.class)) {
                    if (colony.recruits().isRecruited(ant.getUUID())) {
                        called.add(ant.getUUID());
                    }
                    BlockPos spot = colony.spots().spotOf(ant.getUUID());
                    if (called.contains(ant.getUUID()) && ant.isCarryingSeed() && spot != null && spot.getX() - center.getX() >= MEADOW_FROM - 5
                            && calledAtTheMeadow.add(ant.getUUID())) {
                        AntColoniesMod.LOGGER.info("[far-meadow] tick {}: a nestmate called out took food at the meadow ({} so far){}", tick,
                                calledAtTheMeadow.size(), colony.recruits().describe(level));
                    }
                }
                if (phase[2] < 0 && !colony.recruits().lines().isEmpty() && calledAtTheMeadow.size() >= 2) {
                    phase[2] = tick;
                }
                if (phase[3] < 0 && trails.longestTip() >= MEADOW_FROM - 1) {
                    phase[3] = tick;
                    AntColoniesMod.LOGGER.info("[far-meadow] tick {}: the trail's tip is out at the meadow | {}", tick, trails.describe(level));
                }
                if (tick % 2000 == 0) {
                    StringBuilder out = new StringBuilder();
                    for (WorkerAntEntity ant : TestIsolation.insectsOf(level, colony, WorkerAntEntity.class)) {
                        if (!colony.nest().isInside(level, ant.blockPosition())) {
                            out.append(' ').append(ant.getBlockX() - center.getX()).append(',').append(ant.getBlockZ() - center.getZ());
                        }
                    }
                    AntColoniesMod.LOGGER.info("[far-meadow] tick {}: {} | range {}{} | {} | small {} | out:{}", tick, trails.describe(level), colony.foragingRadius(),
                            colony.recruits().describe(level), colony.spots().describe(), colony.door().isSmallColony(level), out);
                }
                if (phase[1] >= 0 && phase[2] >= 0 && phase[3] >= 0) {
                    helper.assertTrue(colony.foragingRadius() >= MEADOW_FROM - 1, "the range should be the trail's tip: " + colony.foragingRadius());
                    for (BlockPos grama : meadow) {
                        helper.setBlock(grama, Blocks.AIR); // cut bare and gone
                    }
                    phase[4] = tick;
                    phase[0] = 2;
                    AntColoniesMod.LOGGER.info("[far-meadow] tick {}: the meadow is gone | {}{}", tick, trails.describe(level), colony.recruits().describe(level));
                }
                return;
            }
            // The meadow gone: first the finders still on their way home from it lay their lines and call (as real ones
            // would: they found it rich), then a day of game time passes every 40 ticks (aged) and the colony looks at
            // its trails.
            if (tick - phase[4] < HOME_FROM_THE_MEADOW || (tick - phase[4]) % 40 != 0) {
                return;
            }
            if (days[0] >= 1) {
                helper.assertTrue(colony.recruits().lines().isEmpty(), "a day after the meadow is gone its scent line should have faded"
                        + colony.recruits().describe(level));
            }
            int tip = trails.longestTip();
            if (days[0] >= 1 && tip < MEADOW_FROM - 4) {
                AntColoniesMod.LOGGER.info("[far-meadow] {} days after the meadow was gone the tip pulled back to {} | {}", days[0], tip, trails.describe(level));
                helper.succeed();
                return;
            }
            helper.assertTrue(days[0] < 7, "a week after the meadow was gone the trail's tip should have pulled back: " + trails.describe(level));
            days[0]++;
            trails.ageForTests(24000);
            colony.recruits().ageForTests(24000);
            trails.checkSoonForTests();
        });
    }
}
