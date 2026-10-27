package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.Map;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DeadCombBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.colony.DeadMounds;
import com.antcolonies.savanna.colony.MoundPlan;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.tree.Forest;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The termite mounds of new land (M8 step 1b, {@code colony/NewLandMounds}): each kind put in on flat ground as world
 * generation would, and checked as it stands.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteNewLandTests {
    /**
     * The whole 65-block arena flat ground (room for the dead tree beside a full mound), the dead tree's foot and low parts
     * fenced inside it (a cathedral's foot reaches about 12 and its tree stands up to 30 past that, past the arena's
     * side: only places inside are taken).
     */
    private static void flatArena(GameTestHelper helper) {
        TestScenes.shapeGround(helper, 65, (x, z) -> 0);
        net.minecraft.world.phys.AABB b = helper.getBounds();
        NewLandMounds.fenceForTests(new net.minecraft.world.level.levelgen.structure.BoundingBox((int) b.minX, (int) b.minY, (int) b.minZ,
                (int) b.maxX - 1, (int) b.maxY - 1, (int) b.maxZ - 1));
    }

    /**
     * The dead wood for the raids, part 3: a dead tree beside the mound, 10 to 30 blocks past its foot (the user's ask,
     * 2026-10-25), in the tree book (dead), its wood in the world.
     */
    private static void deadTreeIsThere(GameTestHelper helper, NewLandMounds book, BlockPos center) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = book.lastDeadTreeForTests();
        helper.assertTrue(foot != null, "no dead tree by the mound");
        double off = Math.sqrt(foot.distSqr(new BlockPos(center.getX(), foot.getY(), center.getZ())));
        int reach = book.lastDeadTreeReachForTests();
        helper.assertTrue(off >= reach + NewLandMounds.DEAD_TREE_NEAREST - 1 && off <= reach + NewLandMounds.DEAD_TREE_FARTHEST + 1,
                "the dead tree stands " + Math.round(off) + " blocks from the mound's middle, " + Math.round(off - reach) + " past its foot (it reaches " + reach + ")");
        Forest.Tree tree = Forest.get(level).at(foot);
        helper.assertTrue(tree != null && !tree.living(), "the dead tree at " + foot.toShortString() + " is not a dead tree in the tree book");
        long wood = BlockPos.betweenClosedStream(foot.offset(-20, -2, -20), foot.offset(20, 20, 20))
                .filter(pos -> level.getBlockState(pos).getBlock() instanceof com.antcolonies.savanna.block.DeadTreeWoodBlock).count();
        helper.assertTrue(wood >= 10, "only " + wood + " blocks of dead wood by the mound");
    }

    /**
     * Whether the colony's works reach the dead wood at {@code wood}: a gallery piece or new tunnel dug out to it, or a
     * runway of mud from a hole near it (the way real Macrotermes feed at wood near a hole).
     */
    private static boolean galleryNear(GameTestHelper helper, Colony colony, BlockPos wood) {
        com.antcolonies.colony.FoodWays ways = colony.nest().foodWays();
        java.util.function.Predicate<BlockPos> ofIt = food -> near(food, wood, 16) // (any block of the same dead tree: the far log and the tree stand well apart)
                && helper.getLevel().getBlockState(food).getBlock() instanceof com.antcolonies.savanna.block.DeadTreeWoodBlock;
        return ways.getRunways().stream().anyMatch(r -> ofIt.test(r.food())) || ways.getNewTunnels().stream().anyMatch(t -> ofIt.test(t.food()));
    }

    private static boolean near(BlockPos a, BlockPos b, double within) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) <= within;
    }

    private static BlockPos middle(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
    }

    /**
     * A full living colony: the queen, swollen, and the king together in the royal chamber; hundreds of workers and
     * their soldiers in the numbers; the nest dug; comb in the gardens; the mound near full height; its builders' time
     * waiting to grow the galleries out of sight; a dead tree beside it, in the tree book and known to the colony (the
     * dead wood for the raids, part 3). A second mound 20 blocks off is refused (too near). Then its galleries reach the
     * dead tree, 10 to 30 blocks past the mound's foot, and big dead wood 30 past it.
     */
    @GameTest(template = "arena", timeoutTicks = 9000, skyAccess = true)
    public static void aFullColonyOfNewLandIsAWholeLivingColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        NewLandMounds book = NewLandMounds.get(level);
        flatArena(helper);
        BlockPos at = helper.absolutePos(new BlockPos(32, SURFACE, 32)); // (the arena's middle: room all round for the dead tree)
        Colony colony = book.put(level, at, NewLandMounds.pickFor("full", 0, 1234L));
        NewLandMounds.fenceForTests(null);
        helper.assertTrue(colony != null, "no colony put in");
        deadTreeIsThere(helper, book, colony.getCenter());
        BlockPos wood = book.lastDeadWoodForTests();
        helper.assertTrue(wood != null && colony.foodMap().knows(wood, null), "the living colony does not know its dead tree's wood");
        helper.assertTrue(colony.getQueen(level) != null, "no queen");
        NestPlan.Chamber home = colony.nest().getHomeChamber(level);
        helper.assertTrue(home != null, "no royal chamber");
        helper.assertTrue(level.getBlockState(home.center()).isAir(), "the royal chamber is not dug");
        helper.assertTrue(colony.getQueen(level).blockPosition().distManhattan(home.center()) <= 3,
                "the queen is at " + colony.getQueen(level).blockPosition().toShortString() + ", not in her chamber at " + home.center().toShortString());
        int kings = level.getEntitiesOfClass(TermiteKingEntity.class, new AABB(home.center()).inflate(4),
                king -> colony.getId().equals(king.getColonyId())).size();
        helper.assertTrue(kings == 1, kings + " kings by the queen");
        helper.assertTrue(colony.getMemberCount() >= 400, "only " + colony.getMemberCount() + " members");
        long comb = colony.nest().getGardenCells(level).stream()
                .filter(cell -> level.getBlockState(cell).getBlock() instanceof FungusCombBlock).count();
        helper.assertTrue(comb >= 4, "only " + comb + " garden cells hold comb");
        int stood = TermiteMoundTests.highest(TermiteMoundTests.snapshot(level, colony.getCenter()), colony.getCenter());
        helper.assertTrue(stood >= 26, "the mound stands only " + stood + " tall");
        helper.assertTrue(colony.building().hasWork(), "no builders' time left for the galleries");
        Colony second = book.put(level, at.offset(20, 0, 0), NewLandMounds.pickFor("young", 0, 99L));
        helper.assertTrue(second == null, "a second colony put in 20 blocks off");
        // Then the galleries grown as the builders' time grows them out of sight (dug at once here), the colony living on
        // (a way it found no route to yet is tried again after 5 minutes): a piece or a runway goes out to the dead tree's
        // wood, and to big dead wood as far as a dead tree may stand, 30 past the cathedral's foot between two arms
        // (toward the arena's corner; 27 blocks of bleached log the colony knows).
        int reach = book.lastDeadTreeReachForTests();
        double diagonal = (reach + NewLandMounds.DEAD_TREE_FARTHEST) / Math.sqrt(2);
        BlockPos far = new BlockPos(colony.getCenter().getX() + (int) Math.round(diagonal), helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY(),
                colony.getCenter().getZ() + (int) Math.round(diagonal));
        helper.assertTrue(far.getX() <= (int) helper.getBounds().maxX - 3 && far.getZ() <= (int) helper.getBounds().maxZ - 3,
                "the farthest dead wood at " + far.toShortString() + " is past the arena");
        boolean farPut = level.getBlockState(far).isAir();
        if (farPut) {
            for (BlockPos log : BlockPos.betweenClosed(far.offset(-1, 0, -1), far.offset(1, 2, 1))) {
                level.setBlock(log, com.antcolonies.savanna.registry.TreeBlocks.BLEACHED_LOG.get().defaultBlockState(), 3);
            }
            colony.foodMap().learn(far);
        }
        level.getServer().tickRateManager().setTickRate(2000.0F);
        com.antcolonies.savanna.colony.Galleries galleries = com.antcolonies.savanna.colony.Galleries.of(colony);
        helper.onEachTick(() -> {
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                for (BlockPos cell : galleries.digCells(tunnel)) {
                    if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                        level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            if (helper.getTick() % 20 == 0) {
                galleries.growNow(level);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(galleryNear(helper, colony, wood), "no gallery out to the dead tree's wood at " + wood.toShortString() + " ("
                    + Math.round(Math.sqrt(wood.distSqr(new BlockPos(colony.getCenter().getX(), wood.getY(), colony.getCenter().getZ())))) + " blocks out):"
                    + galleries.describe(level) + colony.foragingHoles().whyNoRunway(level, wood));
            helper.assertTrue(!farPut || galleryNear(helper, colony, far), "no gallery out to the big dead wood at " + far.toShortString() + " ("
                    + Math.round(Math.sqrt(far.distSqr(new BlockPos(colony.getCenter().getX(), far.getY(), colony.getCenter().getZ())))) + " blocks out):"
                    + galleries.describe(level));
            // The dead wood for the raids, part 5: the living colony ends with a feeding spot fed at by day (a gallery
            // under dead wood or a runway of mud sheeting closed all round), what a Matabele raid needs (looked at every
            // 2 seconds: the count looks at the mud round each spot).
            helper.assertTrue(helper.getTick() % 40 == 0, "(the feeding spots are counted every 2 seconds)");
            java.util.List<com.antcolonies.savanna.colony.FeedingSpots.Spot> spots = com.antcolonies.savanna.colony.FeedingSpots.of(level, colony);
            helper.assertTrue(com.antcolonies.savanna.colony.FeedingSpots.byDay(spots) >= 1, "no feeding spot fed at by day: " + spots);
        });
    }

    /**
     * The way world generation hands a place over: queued, its mound's shape grown in the background while it waits, then
     * put in on a look, quickly (the game never stops for the second the growing takes).
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aQueuedMoundIsGrownAsideThenPutInQuickly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos middle = middle(helper);
        NewLandMounds.queue(level, middle, NewLandMounds.pickFor("half", 0, 4321L));
        helper.succeedWhen(() -> {
            Colony colony = ColonyManager.get(level).getColonies().stream()
                    .filter(c -> c.getCenter().closerThan(middle, 3)).findFirst().orElse(null);
            if (colony == null) {
                MoundPlan.awaitGrowingForTests(10_000); // (the test server's ticks outrun the second of growing: BUGS 73)
            }
            helper.assertTrue(colony != null, "not put in yet");
            double millis = NewLandMounds.get(level).lastMillisForTests();
            helper.assertTrue(millis < 400, "putting it in took " + millis + " ms");
        });
    }

    /** A freshly dead mound (1 day): no colony left, the dead mound in the book, the spires still standing, Xylaria on the comb. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void aFreshDeadMoundOfNewLandStillStands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        NewLandMounds book = NewLandMounds.get(level);
        Colony fresh = book.put(level, middle(helper), NewLandMounds.pickFor("dead", 1, 77L));
        helper.assertTrue(fresh != null, "no fresh dead mound put in");
        BlockPos center = fresh.getCenter();
        helper.assertTrue(ColonyManager.get(level).getColony(fresh.getId()) == null, "the colony of a dead mound lives on");
        DeadMounds.DeadMound dead = DeadMounds.get(level).get(fresh.getId());
        helper.assertTrue(dead != null, "no dead mound in the book");
        helper.assertTrue(Math.abs(dead.days(level) - 1) < 0.2, "the dead mound is " + dead.days(level) + " days dead, not 1");
        Map<BlockPos, BlockState> whole = TermiteMoundTests.snapshot(level, center);
        int stood = TermiteMoundTests.highest(whole, center);
        helper.assertTrue(stood >= 20, "a mound a day dead stands only " + stood + " tall");
        boolean xylaria = BlockPos.betweenClosedStream(center.offset(-12, -12, -12), center.offset(12, 0, 12))
                .anyMatch(pos -> level.getBlockState(pos).getBlock() instanceof DeadCombBlock);
        helper.assertTrue(xylaria, "no Xylaria on the dead comb");
        helper.succeed();
    }

    /** An old hump (120 days dead): low and grassy, its trees in the tree book, an aardvark's burrow open at its foot. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void anOldHumpOfNewLandIsGrassyWithTreesAndABurrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        NewLandMounds book = NewLandMounds.get(level);
        long seed = 3L; // (woods: 1 + 3 mod 3 = a thicket)
        flatArena(helper);
        Colony old = book.put(level, helper.absolutePos(new BlockPos(32, SURFACE, 32)), NewLandMounds.pickFor("dead", 120, seed)); // (the arena's middle: room all round for the dead tree)
        NewLandMounds.fenceForTests(null);
        helper.assertTrue(old != null, "no old hump put in");
        deadTreeIsThere(helper, book, old.getCenter());
        BlockPos center = old.getCenter();
        int hump = TermiteMoundTests.highest(TermiteMoundTests.snapshot(level, center), center);
        helper.assertTrue(hump >= 1 && hump <= 6, "an old hump stands " + hump + " tall");
        long grass = BlockPos.betweenClosedStream(center.offset(-9, -1, -9), center.offset(9, 8, 9))
                .filter(pos -> level.getBlockState(pos).is(Blocks.SHORT_GRASS) || level.getBlockState(pos).is(Blocks.TALL_GRASS)).count();
        helper.assertTrue(grass >= 20, "only " + grass + " grass on the hump");
        helper.assertTrue(!book.lastTreesForTests().isEmpty(), "no trees on the hump (pick " + TermiteMoundStructure.THICKET + ")");
        for (BlockPos foot : book.lastTreesForTests()) {
            helper.assertTrue(Forest.get(level).at(foot) != null, "the tree at " + foot.toShortString() + " is not in the tree book");
        }
        BlockPos mouth = book.lastBurrowForTests();
        helper.assertTrue(mouth != null, "no aardvark burrow");
        helper.assertTrue(level.getBlockState(mouth).isAir() && level.getBlockState(mouth.above()).isAir(),
                "the burrow's mouth at " + mouth.toShortString() + " is not open");
        helper.succeed();
    }
}
