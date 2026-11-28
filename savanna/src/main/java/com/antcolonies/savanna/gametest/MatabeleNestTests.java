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
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele's nest (M8 step 1d): a colony lives only in a dead termite mound for now (the user, 2026-10-24). It moves
 * into the old nest under the hump, down the chimney, the empty royal chamber its queen's room at once (nothing to dig),
 * and the queen ends in her room; with no dead mound near, a queen has no colony until one is near her.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleNestTests {
    /** A month-old dead mound: the colony moves in, the queen's room open from the start, and she and her workers go down into it. */
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
        Colony colony = settled(helper, at);
        helper.assertTrue(colony.getCenter().getX() == dead.center.getX() && colony.getCenter().getZ() == dead.center.getZ()
                && colony.getCenter().getY() > dead.center.getY(), "the entrance " + colony.getCenter().toShortString()
                + " is not up the old chimney over " + dead.center.toShortString());
        helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the old royal chamber is not open for the queen at once" + colony.nest().describeSite(level));
        queenEndsInHerRoom(helper, colony);
    }

    /**
     * Review R6-20: a torch a player puts down the old chimney to look in (a block the ants walk through) leaves the
     * nest's rooms where they were: the queen's room is still open and every room still counts. A solid block in the same
     * cell shuts the way (as a stone would shut real ants in), and taken out again the rooms are back.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aTorchDownTheChimneyLeavesTheMatabeleRoomsOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos middle = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Colony termites = NewLandMounds.get(level).put(level, middle, NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(termites != null, "no dead mound put in");
        Colony colony = settled(helper, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)));
        com.antcolonies.colony.NestPlan.Chamber home = colony.nest().getHomeChamber(level);
        helper.assertTrue(home != null && !home.wayIn().isEmpty(), "the nest has no royal chamber with a way in" + colony.nest().describeSite(level));
        int rooms = colony.nest().getAllRoomCells(level).size();
        helper.assertTrue(rooms > 0 && colony.nest().getFinishedRoomCenter(level) != null, "the rooms are not open before the torch");
        BlockPos way = home.wayIn().get(home.wayIn().size() / 2);
        helper.assertTrue(level.getBlockState(way).isAir(), "the chimney cell " + way.toShortString() + " is not open to put a torch in");
        level.setBlock(way, net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        helper.assertTrue(colony.nest().getAllRoomCells(level).size() == rooms, "a torch in the chimney at " + way.toShortString() + " hid the rooms: "
                + colony.nest().getAllRoomCells(level).size() + " room cells of " + rooms);
        helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "a torch in the chimney shut the queen out of her room");
        level.setBlock(way, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        helper.assertTrue(colony.nest().getAllRoomCells(level).size() < rooms, "a stone in the chimney does not shut the way in");
        level.setBlock(way, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        helper.assertTrue(colony.nest().getAllRoomCells(level).size() == rooms, "the rooms are not back once the stone is gone");
        helper.succeed();
    }

    /**
     * No dead mound near: a queen put down has no colony and the command makes none (dead mounds are the only home for
     * now, the user, 2026-10-24); once a dead mound is put in near her she finds it on her next look and settles in it.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aMatabeleQueenWithNoDeadMoundNearWaitsForOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        BlockPos at = helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE));
        helper.assertTrue(MatabeleCommands.makeColony(level, at, 10) == null, "the command made a colony with no dead mound near");
        MatabeleQueenEntity queen = helper.spawn(SavannaEntities.MATABELE_QUEEN.get(), MIDDLE + 9, SURFACE, MIDDLE);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(queen.getColonyId() == null, "the queen started a colony with no dead mound near");
            Colony termites = MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
            // The hump may rise round her (a mound never appears under an ant in play): up onto its top.
            queen.teleportTo(queen.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    queen.getBlockX(), queen.getBlockZ()), queen.getZ());
            helper.succeedWhen(() -> {
                Colony colony = queen.getColonyId() == null ? null : com.antcolonies.colony.ColonyManager.get(level).getColony(queen.getColonyId());
                helper.assertTrue(colony != null, "the queen has not found the dead mound yet");
                helper.assertTrue(MatabeleNest.IN_A_MOUND.equals(colony.nest().site()), "the queen's colony settled " + colony.nest().site());
            });
        });
    }

    /** A colony of 10 made by the command's code at {@code at}: it settled in a dead mound. */
    private static Colony settled(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        Colony colony = MatabeleCommands.makeColony(level, at, 10);
        helper.assertTrue(colony != null, "no colony made");
        helper.assertTrue(MatabeleNest.IN_A_MOUND.equals(colony.nest().site()), "the colony settled " + colony.nest().site());
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
            net.minecraft.world.entity.Entity queen = colony.getQueen(level);
            helper.assertTrue(queen instanceof MatabeleQueenEntity && queen.blockPosition().closerThan(room, 2.5),
                    "the queen is not in her room at " + room.toShortString() + ": she is at "
                    + (queen == null ? "nowhere" : queen.blockPosition().toShortString() + " doing " + ((MatabeleQueenEntity) queen).goalSelector.getAvailableGoals().stream()
                    .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(g -> g.getGoal().getClass().getSimpleName()).toList()));
            // The workers too, down in the rooms, not only the queen (the user, 2026-10-24: they all piled up at the top of the
            // chimney, which counted as inside, while she walked down to her room).
            // Not the ants out on the colony's work (BUGS 83, M8 step 3a): a new colony's prey pile is empty, so it is
            // hungry, and the test's still 7:00 is in the raid hours, so a major or two go out scouting (or raiding), as
            // real hungry colonies do.
            java.util.Set<BlockPos> rooms = new java.util.HashSet<>(colony.nest().getAllRoomCells(level));
            com.antcolonies.savanna.colony.Raids raids = colony.part(com.antcolonies.savanna.colony.SavannaParts.RAIDS).orElse(null);
            StringBuilder outside = new StringBuilder();
            for (com.antcolonies.savanna.entity.MatabeleEntity ant : com.antcolonies.gametest.TestIsolation.insectsOf(level, colony, com.antcolonies.savanna.entity.MatabeleEntity.class)) {
                if (raids != null && (raids.scouts().isScouting(ant.getUUID()) || raids.isRaider(ant.getUUID()))) {
                    continue;
                }
                if (!rooms.contains(ant.blockPosition())) {
                    outside.append(" | ").append(ant.hasMajorBody() ? "major " : "minor ").append(ant.getLengthMm()).append(" mm at ")
                            .append(ant.position()).append(" box ").append(ant.getBbWidth()).append(" doing ").append(ant.goalSelector.getAvailableGoals().stream()
                            .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(g -> g.getGoal().getClass().getSimpleName()).toList());
                }
            }
            helper.assertTrue(outside.isEmpty(), "workers not down in the rooms (entrance " + colony.getCenter().toShortString() + ")" + outside);
        });
    }
}
