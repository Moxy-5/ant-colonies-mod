package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.DeadMounds;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele's shallow nest (M8 step 1d): a colony settles in the best site near its queen and its workers dig only
 * what joins the spaces there. Near a dead termite mound it moves into the old nest under the hump, down the chimney,
 * the empty royal chamber its queen's room at once (nothing to dig); near a fallen log 3 or more across, or the foot of a
 * standing dead tree, it digs a small room one block down under the wood (a long log has room for two colonies, a tree or a
 * mound for one); in bare land a burrow stand-in, one room 2 down. In each the queen ends in her room.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleNestTests {
    /** A month-old dead mound: the colony moves in, the queen's room open from the start, and she goes down into it. */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aMatabeleColonyMovesIntoADeadTermiteMound(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos middle = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Colony termites = NewLandMounds.get(level).put(level, middle, NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(termites != null, "no dead mound put in");
        DeadMounds.DeadMound dead = DeadMounds.get(level).get(termites.getId());
        helper.assertTrue(dead != null, "no dead mound in the book");
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE));
        Colony colony = settled(helper, at, MatabeleNest.Kind.MOUND);
        helper.assertTrue(colony.getCenter().getX() == dead.center.getX() && colony.getCenter().getZ() == dead.center.getZ()
                && colony.getCenter().getY() > dead.center.getY(), "the entrance " + colony.getCenter().toShortString()
                + " is not up the old chimney over " + dead.center.toShortString());
        helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the old royal chamber is not open for the queen at once" + colony.nest().describeSite(level));
        queenEndsInHerRoom(helper, colony);
    }

    /** A thick log (3 across, 9 long) lying on the ground: the colony settles beside it and digs a small room one block down under it. */
    @GameTest(template = "arena", timeoutTicks = 8000)
    public static void aMatabeleColonyDigsInUnderAFallenLog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        thickLog(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE + 7));
        Colony colony = settled(helper, at, MatabeleNest.Kind.LOG);
        BlockPos entrance = colony.getCenter();
        BlockPos log = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        helper.assertTrue(entrance.getY() == log.getY() && Math.abs(entrance.getZ() - log.getZ()) == 2 && Math.abs(entrance.getX() - log.getX()) <= 4,
                "the entrance " + entrance.toShortString() + " is not beside the log's side, " + log.toShortString());
        helper.assertTrue(colony.nest().getPlannedChambers(level).size() == 1, "not one room");
        for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
            for (BlockPos cell : room.room()) {
                helper.assertTrue(cell.getY() == log.getY() - 1, "a room cell " + cell.toShortString() + " is not one block down");
                helper.assertTrue(level.getBlockState(cell.above()).is(net.minecraft.tags.BlockTags.LOGS), "the log is not the roof over " + cell.toShortString());
            }
        }
        queenEndsInHerRoom(helper, colony);
    }

    /** A long log has room for two colonies, never a third; a thin log or a short one is no roof. */
    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void aFallenLogTakesTwoColoniesAtMost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        thickLog(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE + 7));
        Colony first = settled(helper, at, MatabeleNest.Kind.LOG);
        Colony second = settled(helper, at, MatabeleNest.Kind.LOG);
        java.util.Set<BlockPos> cells = new java.util.HashSet<>();
        for (Colony colony : java.util.List.of(first, second)) {
            for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
                for (BlockPos cell : room.room()) {
                    helper.assertTrue(cells.add(cell), "both colonies' rooms hold " + cell.toShortString());
                }
            }
        }
        helper.assertTrue(MatabeleNest.pick(level, at, MatabeleNest.Kind.LOG) == null, "a third colony found room under the log");
        helper.succeed();
    }

    /** A log one block thick, however long, is no roof. */
    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void aThinLogIsNoRoofForTheMatabele(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        for (int x = MIDDLE - 4; x <= MIDDLE + 4; x++) {
            helper.setBlock(x, SURFACE, MIDDLE, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE + 7));
        helper.assertTrue(MatabeleNest.pick(level, at, MatabeleNest.Kind.LOG) == null, "a one-block log was taken for a roof");
        helper.succeed();
    }

    /** A standing dead trunk 4 across: one colony digs in at its foot, one block down, and no second. */
    @GameTest(template = "arena", timeoutTicks = 8000)
    public static void aMatabeleColonyDigsInAtTheFootOfAStandingDeadTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        for (int x = MIDDLE; x <= MIDDLE + 3; x++) {
            for (int z = MIDDLE; z <= MIDDLE + 3; z++) {
                for (int y = SURFACE; y <= SURFACE + 5; y++) {
                    helper.setBlock(x, y, z, Blocks.OAK_LOG.defaultBlockState());
                }
            }
        }
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE - 5, SURFACE, MIDDLE));
        Colony colony = settled(helper, at, MatabeleNest.Kind.STANDING);
        for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
            for (BlockPos cell : room.room()) {
                helper.assertTrue(cell.getY() == helper.absolutePos(new BlockPos(0, SURFACE, 0)).getY() - 1
                        && level.getBlockState(cell.above()).is(net.minecraft.tags.BlockTags.LOGS), "a room cell " + cell.toShortString() + " is not under the trunk");
            }
        }
        helper.assertTrue(MatabeleNest.pick(level, at, MatabeleNest.Kind.STANDING) == null, "a second colony found room at the same tree");
        queenEndsInHerRoom(helper, colony);
    }

    /** No cover anywhere: the stand-in for an animal's burrow, a shaft and one room 2 blocks down. */
    @GameTest(template = "arena", timeoutTicks = 8000)
    public static void aMatabeleColonyMovesIntoABurrowInOpenGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Colony colony = settled(helper, at, MatabeleNest.Kind.GROUND);
        helper.assertTrue(colony.nest().getPlannedChambers(level).size() == 1, "not one room");
        for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
            int depth = colony.getCenter().getY() - room.center().getY();
            helper.assertTrue(depth == 2, "the room is " + depth + " blocks down, not 2");
        }
        queenEndsInHerRoom(helper, colony);
    }

    /** A log 3 across, 3 high and 9 long, along X, with its middle at the arena's middle. */
    private static void thickLog(GameTestHelper helper) {
        for (int x = MIDDLE - 4; x <= MIDDLE + 4; x++) {
            for (int z = MIDDLE - 1; z <= MIDDLE + 1; z++) {
                for (int y = SURFACE; y <= SURFACE + 2; y++) {
                    helper.setBlock(x, y, z, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                }
            }
        }
    }

    /** A colony of 10 made by the command's code at {@code at}: it settled in a site of {@code kind}. */
    private static Colony settled(GameTestHelper helper, BlockPos at, MatabeleNest.Kind kind) {
        ServerLevel level = helper.getLevel();
        Colony colony = MatabeleCommands.makeColony(level, at, 10);
        helper.assertTrue(colony != null, "no colony made");
        helper.assertTrue(kind.words.equals(colony.nest().site()), "the colony settled " + colony.nest().site() + ", not " + kind.words);
        return colony;
    }

    /** The queen's room is opened and she ends in it. */
    private static void queenEndsInHerRoom(GameTestHelper helper, Colony colony) {
        ServerLevel level = helper.getLevel();
        helper.succeedWhen(() -> {
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            StringBuilder why = new StringBuilder(" entrance " + colony.getCenter().toShortString() + " dig " + colony.nest().findDigTarget(level)
                    + " founding " + colony.isFounding() + " members " + colony.getMemberCount());
            for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
                why.append(" | ").append(chamber.kind()).append(" way ").append(chamber.wayIn().stream().map(p -> p.toShortString() + "="
                        + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath()).toList());
            }
            helper.assertTrue(room != null, "the queen's room is not open yet" + colony.nest().describeSite(level) + why);
            helper.assertTrue(colony.getQueen(level) instanceof MatabeleQueenEntity queen && queen.blockPosition().closerThan(room, 2.5),
                    "the queen is not in her room at " + room.toShortString());
        });
    }
}
