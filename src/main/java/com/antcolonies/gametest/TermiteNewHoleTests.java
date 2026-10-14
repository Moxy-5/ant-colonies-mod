package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.Nest;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * New foraging holes near rich food (the user's decision, 2026-10-14; {@link ForagingHoles}, {@link
 * com.antcolonies.colony.TunnelRoute}): the colony digs a tunnel underground out to rich food far from its holes and
 * opens a hole beside it; once the food is gone the hole is plugged for good.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteNewHoleTests {
    /** How far east of the nest's middle the hay bale lies (beyond both planned foraging holes, 8 blocks out). */
    private static final int HAY_EAST = 22;

    /**
     * A big colony (500 workers in the numbers, so its tunnels reach 40 blocks; the brain is off, so they stay inside)
     * with its nest dug out and six middle-aged real workers (builders, who dig). The colony knows of a hay bale 22
     * blocks east. It plans a tunnel to it, the workers dig it to the end and open a hole beside the hay at foraging
     * time. Then the hay is taken away: the hole is spent, plugged when the foraging is over and never opened again.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void aBigColonyDigsATunnelToFarHayAndPlugsItsHoleWhenTheHayIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        // Ground east of the old arena too, out past the hay.
        for (int x = 0; x <= MIDDLE + HAY_EAST + 6; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        BlockPos hay = helper.absolutePos(new BlockPos(MIDDLE + HAY_EAST, SURFACE, MIDDLE));
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 digging, 5 foraging by the new hole, 3 hay gone, 4 plugged
        int[] cutWhenOpened = {0};
        boolean[] usedTheHole = {false};
        Nest.NewTunnel[] tunnel = {null};
        int[] since = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            colony.setForagingTimeForTests(true);
            level.setBlock(hay, Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(hay);
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            since[0]++;
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] tick {} phase {}: {}{}", helper.getTick(), phase[0],
                        tunnel[0] == null ? "no tunnel yet" : "dug " + tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count()
                                + " of " + tunnel[0].tunnel().tunnel().size() + ", hole " + level.getBlockState(tunnel[0].tunnel().hole()),
                        colony.foragingHoles().describeNewHoles(level));
            }
            switch (phase[0]) {
                case 1 -> {
                    if (!colony.nest().getNewTunnels().isEmpty()) {
                        tunnel[0] = colony.nest().getNewTunnels().get(0);
                        BlockPos hole = tunnel[0].tunnel().hole();
                        helper.assertTrue(tunnel[0].food().equals(hay), "the tunnel is for " + tunnel[0].food() + ", not the hay");
                        helper.assertTrue(Math.max(Math.abs(hole.getX() - hay.getX()), Math.abs(hole.getZ() - hay.getZ())) <= 2,
                                "the hole " + hole.toShortString() + " is not beside the hay");
                        AntColoniesMod.LOGGER.info("[new-hole-test] planned: {} blocks, hole at {}", tunnel[0].tunnel().tunnel().size(), hole.toShortString());
                        phase[0] = 2;
                        since[0] = 0;
                    } else {
                        helper.assertTrue(since[0] < 600, "no tunnel was planned to the hay: " + colony.foragingHoles().describeNewHoles(level));
                    }
                }
                case 2 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    if (ForagingHoles.isDug(level, tunnel[0].tunnel()) && ForagingHoles.isOpen(level, hole)) {
                        helper.assertTrue(colony.foragingHoles().waysOut(level).stream().anyMatch(way -> way.mouth().equals(hole.above())),
                                "the new hole is open but not a way out");
                        AntColoniesMod.LOGGER.info("[new-hole-test] dug and opened at tick {}", helper.getTick());
                        cutWhenOpened[0] = colony.food().getPiecesCut(hay);
                        phase[0] = 5;
                        since[0] = 0;
                    }
                }
                case 5 -> {
                    // Foragers come and go by the new hole: one is seen at the far end of the tunnel or in the hole.
                    java.util.List<BlockPos> farEnd = new java.util.ArrayList<>(tunnel[0].tunnel().tunnel());
                    farEnd = farEnd.subList(Math.max(0, farEnd.size() - 2), farEnd.size());
                    farEnd.add(tunnel[0].tunnel().hole());
                    for (BlockPos cell : farEnd) {
                        usedTheHole[0] |= !level.getEntitiesOfClass(TermiteWorkerEntity.class, new net.minecraft.world.phys.AABB(cell),
                                worker -> colony.getId().equals(worker.getColonyId())).isEmpty();
                    }
                    int cut = colony.food().getPiecesCut(hay) - cutWhenOpened[0];
                    if (usedTheHole[0] && cut >= 2) {
                        AntColoniesMod.LOGGER.info("[new-hole-test] foragers use the new hole: {} pieces cut since it opened", cut);
                        level.setBlock(hay, Blocks.AIR.defaultBlockState(), 3);
                        colony.food().rescanPlantsSoon();
                        phase[0] = 3;
                        since[0] = 0;
                    }
                    helper.assertTrue(since[0] < 8000, "the foragers do not use the new hole: seen in it " + usedTheHole[0] + ", "
                            + cut + " pieces cut off the hay since it opened");
                }
                case 3 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    if (colony.nest().isSpent(hole)) {
                        colony.setForagingTimeForTests(false); // the foraging is over: the holes are plugged
                        if (!ForagingHoles.isOpen(level, hole)) {
                            colony.setForagingTimeForTests(true); // and a new night: the spent one stays shut
                            phase[0] = 4;
                            since[0] = 0;
                        }
                    }
                    helper.assertTrue(since[0] < 6000, "the hole beside the gone hay was not spent and plugged: spent "
                            + colony.nest().isSpent(hole) + ", " + level.getBlockState(hole));
                }
                case 4 -> {
                    BlockPos hole = tunnel[0].tunnel().hole();
                    helper.assertTrue(!ForagingHoles.isOpen(level, hole), "the spent hole was opened again");
                    helper.assertTrue(colony.foragingHoles().waysOut(level).stream().noneMatch(way -> way.mouth().equals(hole.above())),
                            "the spent hole is still a way out");
                    if (since[0] > 1200) {
                        helper.assertTrue(colony.nest().isInside(level, tunnel[0].tunnel().end()), "the tunnel is no longer part of the nest");
                        helper.succeed();
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * The user's bugs (2026-10-14): a 5-block fallen tree lying up a slope, 5 to 7 blocks higher than the nest. The
     * colony must see it and plan a tunnel to its lowest block; then that block is eaten away (taken out here), but the
     * rest of the tree is still there, so the hole must not be given up; and the tunnel, climbing the slope, is dug to
     * its last block (dug upward) and the hole beside the tree opened.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void aFallenTreeUpASlopeKeepsItsHoleWhenItsLowestBlockIsEaten(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int treeStart = MIDDLE + 20;
        // The ground rises east of the nest, a block a step: 5 blocks up at the tree's foot (higher than the plants the colony
        // sees round the nest; the user's tree on a terrace was never seen), 7 under its far end.
        java.util.function.IntUnaryOperator rise = x -> x <= treeStart ? Math.max(0, x - (treeStart - 5)) : Math.min(7, 5 + (x - treeStart));
        for (int x = 0; x <= treeStart + 8; x++) {
            for (int z = 0; z < 25; z++) {
                int top = SURFACE - 1 + rise.applyAsInt(x);
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        java.util.List<BlockPos> tree = new java.util.ArrayList<>();
        for (int x = treeStart; x < treeStart + 5; x++) {
            tree.add(helper.absolutePos(new BlockPos(x, SURFACE + rise.applyAsInt(x), MIDDLE)));
        }
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 digging and opening
        Nest.NewTunnel[] tunnel = {null};
        int[] since = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            colony.setForagingTimeForTests(true);
            for (BlockPos wood : tree) {
                level.setBlock(wood, com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
            }
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(tree.get(0));
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            since[0]++;
            if (phase[0] == 1) {
                if (colony.nest().getNewTunnels().isEmpty()) {
                    helper.assertTrue(since[0] < 600, "no tunnel was planned to the tree: " + colony.foragingHoles().describeNewHoles(level)
                            + ", tree seen " + colony.food().plants(level).contains(tree.get(0)) + ", known " + colony.foodMap().knows(tree.get(0), null) + ", tree " + (tree.get(0).getY() - colony.getCenter().getY()) + " above the nest middle, " + level.getBlockState(tree.get(0)));
                    return;
                }
                tunnel[0] = colony.nest().getNewTunnels().get(0);
                helper.assertTrue(tunnel[0].food().equals(tree.get(0)), "the tunnel is for " + tunnel[0].food() + ", not the tree's lowest block");
                level.setBlock(tree.get(0), Blocks.AIR.defaultBlockState(), 3); // eaten away
                colony.food().rescanPlantsSoon();
                phase[0] = 2;
                since[0] = 0;
                return;
            }
            BlockPos hole = tunnel[0].tunnel().hole();
            if (since[0] % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] slope: dug {} of {}, hole {}", tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count(),
                        tunnel[0].tunnel().tunnel().size(), level.getBlockState(hole));
            }
            helper.assertTrue(!colony.nest().isSpent(hole), "the hole was given up while the rest of the tree is still there");
            if (ForagingHoles.isDug(level, tunnel[0].tunnel()) && ForagingHoles.isOpen(level, hole)) {
                helper.succeed();
            }
            helper.assertTrue(since[0] < 28000, "the tunnel to the tree was not dug and opened: " + colony.foragingHoles().describeNewHoles(level));
        });
    }
}
