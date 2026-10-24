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
 * the empty royal chamber its queen's room at once (nothing to dig); near a fallen log it digs a room under the trunk
 * and one under that; on open ground a shaft and two rooms, 3 and 5 down. In each the queen ends in her room.
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

    /** A log lying on the ground: the colony settles beside it, digs a room under the trunk and the queen's under that. */
    @GameTest(template = "arena", timeoutTicks = 8000)
    public static void aMatabeleColonyDigsInUnderAFallenLog(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        for (int x = MIDDLE - 3; x <= MIDDLE + 3; x++) {
            helper.setBlock(x, SURFACE, MIDDLE, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE + 7));
        Colony colony = settled(helper, at, MatabeleNest.Kind.LOG);
        BlockPos entrance = colony.getCenter();
        BlockPos log = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        helper.assertTrue(entrance.getY() == log.getY() && entrance.getX() == log.getX() && Math.abs(entrance.getZ() - log.getZ()) == 2,
                "the entrance " + entrance.toShortString() + " is not beside the log's middle " + log.toShortString());
        boolean underTheLog = false;
        for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
            underTheLog |= room.room().contains(log.below(2));
        }
        helper.assertTrue(underTheLog, "no room right under the log");
        queenEndsInHerRoom(helper, colony);
    }

    /** Open ground, no mound or log near: a shaft and two rooms, 3 and 5 blocks down, the queen's the deeper. */
    @GameTest(template = "arena", timeoutTicks = 8000)
    public static void aMatabeleColonyDigsAShallowNestInOpenGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Colony colony = settled(helper, at, MatabeleNest.Kind.GROUND);
        int deepest = 0;
        for (NestPlan.Chamber room : colony.nest().getPlannedChambers(level)) {
            int depth = colony.getCenter().getY() - room.center().getY();
            helper.assertTrue(depth >= 2 && depth <= 5, "a room " + depth + " blocks down, not 2 to 5");
            if (room.kind() == NestPlan.Kind.QUEEN) {
                deepest = depth;
            }
        }
        helper.assertTrue(deepest == 5, "the queen's room is " + deepest + " down, not 5");
        queenEndsInHerRoom(helper, colony);
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
