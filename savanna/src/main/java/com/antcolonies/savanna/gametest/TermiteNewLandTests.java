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
    private static BlockPos middle(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
    }

    /**
     * A full living colony: the queen, swollen, and the king together in the royal chamber; hundreds of workers and
     * their soldiers in the numbers; the nest dug; comb in the gardens; the mound near full height; its builders' time
     * waiting to grow the galleries out of sight. A second mound 20 blocks off is refused (too near).
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void aFullColonyOfNewLandIsAWholeLivingColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        NewLandMounds book = NewLandMounds.get(level);
        Colony colony = book.put(level, middle(helper), NewLandMounds.pickFor("full", 0, 1234L));
        helper.assertTrue(colony != null, "no colony put in");
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
        Colony second = book.put(level, middle(helper).offset(20, 0, 0), NewLandMounds.pickFor("young", 0, 99L));
        helper.assertTrue(second == null, "a second colony put in 20 blocks off");
        helper.succeed();
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
        Colony old = book.put(level, middle(helper), NewLandMounds.pickFor("dead", 120, seed));
        helper.assertTrue(old != null, "no old hump put in");
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
