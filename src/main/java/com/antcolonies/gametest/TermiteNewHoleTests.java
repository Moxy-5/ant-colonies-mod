package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
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
import net.minecraft.world.level.block.state.BlockState;
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
     * On the test clock (roadmap 3a): real nights and days from 18:30; the spent hole is plugged at the end of the night
     * and stays shut the next night. The bale is kept from running out until the new hole has been used (the workers
     * eat it from the tunnel's end by day), as a bigger stack of hay would last.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
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
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 digging, 5 foraging by the new hole, 3 hay gone, 6 plugged, 4 the next night
        TestClock clock = TestClock.moving(helper, 18.5);
        int[] cutWhenOpened = {0};
        boolean[] usedTheHole = {false};
        FoodWays.NewTunnel[] tunnel = {null};
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
            if (phase[0] != 5 || colony.isForagingTime(level)) {
                since[0]++; // (foraging by the new hole: night time only)
            }
            if ((phase[0] == 2 || phase[0] == 5) && colony.food().getPiecesCut(hay) > 24) {
                // A bale holds 48 pieces, and once the tunnel reaches it the workers eat it from inside by day: until the
                // new hole has been used it stands for a bigger stack of hay, which lasts (cuts since the hole opened kept).
                cutWhenOpened[0] -= colony.food().getPiecesCut(hay);
                colony.food().setPiecesCutForTests(hay, 0);
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] tick {} {} phase {}: {}{}", helper.getTick(), clock.describe(), phase[0],
                        tunnel[0] == null ? "no tunnel yet" : "dug " + tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count()
                                + " of " + tunnel[0].tunnel().tunnel().size() + ", hole " + level.getBlockState(tunnel[0].tunnel().hole()),
                        colony.foragingHoles().describeNewHoles(level));
            }
            switch (phase[0]) {
                case 1 -> {
                    if (!colony.nest().foodWays().getNewTunnels().isEmpty()) {
                        tunnel[0] = colony.nest().foodWays().getNewTunnels().get(0);
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
                    if (colony.nest().foodWays().isSpent(hole)) {
                        // The foraging is over at the end of the night: the holes are plugged.
                        if (!ForagingHoles.isOpen(level, hole)) {
                            phase[0] = 6;
                            since[0] = 0;
                        } else {
                            helper.assertTrue(clock.phase(colony) != TestClock.Phase.DAY, "the spent hole was not plugged by sunrise: "
                                    + level.getBlockState(hole));
                        }
                    } else {
                        helper.assertTrue(since[0] < 6000, "the hole beside the gone hay was not spent: " + level.getBlockState(hole));
                    }
                }
                case 6 -> {
                    if (clock.phase(colony) == TestClock.Phase.NIGHT) { // and a new night: the spent one stays shut
                        phase[0] = 4;
                        since[0] = 0;
                    }
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
        FoodWays.NewTunnel[] tunnel = {null};
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
            TestClock.still(helper, 0.0); // midnight: foraging time
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
                if (colony.nest().foodWays().getNewTunnels().isEmpty()) {
                    helper.assertTrue(since[0] < 600, "no tunnel was planned to the tree: " + colony.foragingHoles().describeNewHoles(level)
                            + ", tree seen " + colony.food().plants(level).contains(tree.get(0)) + ", known " + colony.foodMap().knows(tree.get(0), null) + ", tree " + (tree.get(0).getY() - colony.getCenter().getY()) + " above the nest middle, " + level.getBlockState(tree.get(0)));
                    return;
                }
                tunnel[0] = colony.nest().foodWays().getNewTunnels().get(0);
                helper.assertTrue(tunnel[0].food().equals(tree.get(0)), "the tunnel is for " + tunnel[0].food() + ", not the tree's lowest block");
                level.setBlock(tree.get(0), Blocks.AIR.defaultBlockState(), 3); // eaten away
                colony.food().rescanPlantsSoon();
                phase[0] = 2;
                since[0] = 0;
                return;
            }
            BlockPos hole = tunnel[0].tunnel().hole();
            if (since[0] % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] slope: dug {} of {}, hole {}, may open {}, foraging time {}{}", tunnel[0].tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count(),
                        tunnel[0].tunnel().tunnel().size(), level.getBlockState(hole), colony.foragingHoles().mayOpen(level, hole), colony.isForagingTime(level),
                        colony.alarm().describe(level));
                java.util.List<BlockPos> gallery = tunnel[0].gallery() == null ? java.util.List.of() : tunnel[0].gallery();
                BlockPos next = colony.nest().findDigTarget(level);
                java.util.List<BlockPos> cells = tunnel[0].tunnel().tunnel();
                AntColoniesMod.LOGGER.info("[new-hole-test] slope: gallery dug {} of {}, next dig {} ({}), hole {}, tunnel from {} to {}, workers at {}",
                        gallery.stream().filter(c -> Nest.isDugOut(level, c)).count(), gallery.size(),
                        next == null ? null : helper.relativePos(next), next == null ? null : level.getBlockState(next), helper.relativePos(hole),
                        helper.relativePos(cells.get(0)), helper.relativePos(cells.get(cells.size() - 1)),
                        level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds()).stream()
                                .map(w -> helper.relativePos(w.blockPosition()).toShortString()).toList());
            }
            helper.assertTrue(!colony.nest().foodWays().isSpent(hole), "the hole was given up while the rest of the tree is still there");
            if (ForagingHoles.isDug(level, tunnel[0].tunnel()) && ForagingHoles.isOpen(level, hole)) {
                helper.succeed();
            }
            helper.assertTrue(since[0] < 28000, "the tunnel to the tree was not dug and opened: " + colony.foragingHoles().describeNewHoles(level));
        });
    }
    /**
     * Like {@link #aFallenLogAndTheStumpOnItAreEatenFromBelowByDay}, with the log lying on the edge of a step: the ground beside it on
     * one side is 2 blocks lower (the user's tree in the savanna, whose gallery was left out, 2026-10-14). The open side
     * of the gallery is walled with a mud crust as it is dug (wall first), and the log is eaten from below by day.
     * Brain part 3: by day the brain remembers the log as food that can be eaten from inside, and the forager numbers go
     * to it then too (they forage by day only what they can eat from inside).
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenLogOnTheEdgeOfAStepIsEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, true, Food.LOG);
    }

    /**
     * Eating dead wood from below (the user's idea, 2026-10-14): a fallen log lies on level ground 20 blocks east of the
     * nest. At night the workers dig a tunnel out to it, then a feeding gallery up under it; by day (no foraging) they
     * still eat the log, from the gallery under it, without going out. Mud casts (the user's choice, 2026-10-14): a block
     * of the log eaten away leaves a mud cast joined to the feeding gallery, and the termites eat on from inside it: a
     * block of stump standing on the log, which no gallery cell touches, is eaten by day from the cast under it, and is a
     * cast itself afterwards.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenLogAndTheStumpOnItAreEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, false, Food.STUMP);
    }

    /**
     * A fallen branch lies on the ground, thin (nothing bumps into it), so it is no roof by itself: the workers case it
     * in mud as they dig the feeding gallery under it (wall first), the mud holds it up over the gallery, and it is
     * eaten from below by day. It lies on the edge of a step (the ground beside it 2 blocks lower, the user's branch of
     * 2026-10-14 whose casing and gallery stayed open on that side, 2 blocks high): the gallery's open side is walled
     * and the branch is cased on that side too.
     */
    @GameTest(template = "arena", timeoutTicks = 90000, skyAccess = true)
    public static void aFallenBranchOnTheEdgeOfAStepIsCasedInMudAndEatenFromBelowByDay(GameTestHelper helper) {
        eatenFromBelow(helper, true, Food.BRANCH);
    }

    private enum Food { LOG, STUMP, BRANCH }

    /**
     * (60000 ticks, was 40000: once the stump was still on its last bitten stage when the time ran out, 2026-10-14; 90000
     * on the test clock, roadmap 3a: real nights and days from 18:30 for the log and the stump, the gallery dug through
     * the nights, then eaten from below by day, over more than one day if need be; the branch on a still midday.)
     */
    private static void eatenFromBelow(GameTestHelper helper, boolean onAStep, Food kind) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int logStart = MIDDLE + 20;
        for (int x = 0; x <= logStart + 8; x++) {
            for (int z = 0; z < 25; z++) {
                int top = onAStep && x >= logStart - 3 && z > MIDDLE ? SURFACE - 3 : SURFACE - 1;
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
                for (int y = top + 1; y < SURFACE; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i, SURFACE, MIDDLE - 2);
            }
        });
        java.util.List<BlockPos> log = new java.util.ArrayList<>();
        for (int x = logStart; x < logStart + (kind == Food.BRANCH ? 3 : 5); x++) {
            log.add(helper.absolutePos(new BlockPos(x, SURFACE, MIDDLE)));
        }
        BlockPos stump = log.get(0).above(); // put on the log at dawn (Food.STUMP)
        int[] phase = {0}; // 0 setting up, 1 tunnel and gallery dug (by night), 2 day: eaten from below
        // A branch is small: by night the foragers outside would eat it all before the gallery is dug, so it is day.
        TestClock clock = kind == Food.BRANCH ? TestClock.still(helper, 12.0) : TestClock.moving(helper, 18.5);
        boolean[] night = {false}; // (phase 2: a night came between the days)
        int[] eatenAtDawn = {0};
        boolean[] numbersAte = {kind != Food.LOG}; // (brain part 3, the log test only)
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 1000, 500);
            for (int i = 0; i < log.size(); i++) {
                level.setBlock(log.get(i), kind == Food.BRANCH
                        ? com.antcolonies.registry.ModBlocks.FALLEN_BRANCH.get().defaultBlockState()
                                .setValue(com.antcolonies.block.FallenBranchBlock.AXIS, net.minecraft.core.Direction.Axis.X)
                                .setValue(com.antcolonies.block.FallenBranchBlock.PART, i == 0 ? com.antcolonies.block.FallenBranchBlock.Part.BASE
                                        : i == log.size() - 1 ? com.antcolonies.block.FallenBranchBlock.Part.TIP : com.antcolonies.block.FallenBranchBlock.Part.MIDDLE)
                        : com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
            }
            if (kind == Food.BRANCH) {
                // Tall grass round the branch, 2 blocks high, green and dry by turns (the savanna, the user's question of
                // 2026-10-14): on both sides of it and down on the step.
                for (int x = logStart - 2; x <= logStart + 3; x++) {
                    for (int z : new int[] {MIDDLE - 1, MIDDLE + 1}) {
                        BlockPos foot = helper.absolutePos(new BlockPos(x, (onAStep && x >= logStart - 3 && z > MIDDLE ? SURFACE - 2 : SURFACE), z));
                        if (level.getBlockState(foot).isAir() && level.getBlockState(foot.above()).isAir()) {
                            net.minecraft.world.level.block.DoublePlantBlock.placeAt(level, x % 2 == 0 ? Blocks.TALL_GRASS.defaultBlockState()
                                    : com.antcolonies.registry.ModBlocks.TALL_DRY_GRASS.get().defaultBlockState(), foot, 3);
                        }
                    }
                }
            }
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(log.get(0));
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            int eaten = 0;
            for (BlockPos wood : log) {
                BlockState state = level.getBlockState(wood);
                eaten += state.getBlock() instanceof com.antcolonies.block.DeadWoodBlock ? state.getValue(com.antcolonies.block.DeadWoodBlock.EATEN) * 10
                        + colony.food().getPiecesCut(wood)
                        : state.getBlock() instanceof com.antcolonies.block.FallenBranchBlock ? state.getValue(com.antcolonies.block.FallenBranchBlock.EATEN) * 10
                        + colony.food().getPiecesCut(wood) : 100;
            }
            FoodWays.NewTunnel tunnel = colony.nest().foodWays().getNewTunnels().isEmpty() ? null : colony.nest().foodWays().getNewTunnels().get(0);
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[new-hole-test] log from below: {}, phase {}, tunnel {}, gallery {}, eaten {}", clock.describe(), phase[0],
                        tunnel == null ? "none" : tunnel.tunnel().tunnel().stream().filter(c -> Nest.isDugOut(level, c)).count() + "/" + tunnel.tunnel().tunnel().size(),
                        tunnel == null || tunnel.gallery() == null ? "none" : tunnel.gallery().stream().filter(c -> Nest.isDugOut(level, c)).count() + "/" + tunnel.gallery().size(),
                        eaten);
            }
            if (tunnel != null) {
                // Planned with the tunnel, as one job (the user's decision, 2026-10-14), not once the tunnel is dug.
                helper.assertTrue(tunnel.gallery() != null && !tunnel.gallery().isEmpty(), "the tunnel was planned without its feeding gallery");
                // Wall first: no wall, floor or roof of a dug tunnel or gallery block is ever open to the outside, not even
                // for a tick (the roof of the gallery is the log, and a block of it eaten away leaves a mud cast).
                java.util.Set<BlockPos> nest = new java.util.HashSet<>(tunnel.gallery());
                nest.addAll(tunnel.tunnel().tunnel());
                nest.add(tunnel.tunnel().hole());
                java.util.List<BlockPos> dug = new java.util.ArrayList<>(tunnel.tunnel().tunnel());
                dug.addAll(tunnel.gallery());
                for (BlockPos cell : dug) {
                    if (!Nest.isDugOut(level, cell)) {
                        continue;
                    }
                    for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                        BlockPos next = cell.relative(side);
                        helper.assertTrue(nest.contains(next) || colony.nest().nestCells(level).contains(next)
                                || !com.antcolonies.colony.NestWalls.isOpenToward(level, next, side.getOpposite()),
                                "wall first: the dug block " + cell.toShortString() + " is open to the outside at " + next.toShortString() + " (" + level.getBlockState(next) + ")");
                        // A branch over it (no roof by itself) is cased in mud all round.
                        if (com.antcolonies.colony.NestWalls.isWalkThroughFood(level, next)) {
                            for (net.minecraft.core.Direction round : net.minecraft.core.Direction.values()) {
                                BlockPos beside = next.relative(round);
                                helper.assertTrue(beside.equals(cell) || nest.contains(beside) || colony.nest().nestCells(level).contains(beside)
                                        || com.antcolonies.colony.NestWalls.isWalkThroughFood(level, beside)
                                        || !com.antcolonies.colony.NestWalls.isOpenToward(level, beside, round.getOpposite()),
                                        "wall first: the branch " + next.toShortString() + " over the dug block " + cell.toShortString()
                                                + " is not cased in mud at " + beside.toShortString());
                            }
                        }
                    }
                }
            }
            if (phase[0] == 1) {
                if (tunnel != null) {
                    long under = log.stream().filter(wood -> tunnel.gallery().contains(wood.below())).count();
                    helper.assertTrue(under >= log.size() - 1, "the feeding gallery runs under only " + under + " of the " + log.size() + " blocks of food: " + tunnel.gallery());
                    if (tunnel.gallery().stream().allMatch(c -> Nest.isDugOut(level, c)) && clock.phase(colony) == TestClock.Phase.DAY) {
                        eatenAtDawn[0] = eaten;
                        if (kind == Food.LOG) {
                            colony.field().setOnForTests(true); // (brain part 3: the 500 numbers forage too, by day only from inside)
                        }
                        phase[0] = 2;
                        if (kind == Food.STUMP) {
                            // The stump on the log; it and every block of the log nearly eaten (quicker, and alike, so the
                            // block under the stump, nearest the hole, goes first). The stump only from the log's cast.
                            BlockState wood = com.antcolonies.registry.ModBlocks.DEAD_WOOD.get().defaultBlockState()
                                    .setValue(com.antcolonies.block.DeadWoodBlock.EATEN, com.antcolonies.block.DeadWoodBlock.MAX_EATEN);
                            for (BlockPos block : log) {
                                if (level.getBlockState(block).getBlock() instanceof com.antcolonies.block.DeadWoodBlock) {
                                    level.setBlock(block, wood.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
                                    colony.food().forgetCutsForTests(block);
                                }
                            }
                            level.setBlock(stump, wood, 3);
                            colony.food().rescanPlantsSoon();
                        }
                    }
                }
                helper.assertTrue(helper.getTick() < 45000, "the tunnel and the feeding gallery under the log were not dug");
                return;
            }
            if (clock.phase(colony) != TestClock.Phase.DAY) {
                night[0] = true; // (the foragers outside may eat it from above by night: only the days count)
                return;
            }
            if (night[0]) {
                night[0] = false;
                eatenAtDawn[0] = eaten;
            }
            // By day: eaten from below, and on the step the gallery's open side is a wall of mud crust.
            int crusts = 0;
            for (BlockPos cell : tunnel.gallery()) {
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    crusts += com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(cell.relative(side)), side.getOpposite()) ? 1 : 0;
                }
            }
            helper.assertTrue(!colony.isForagingTime(level), "foraging time");
            if (!numbersAte[0] && colony.field().foragersOut() > 0) {
                helper.assertTrue(log.stream().anyMatch(colony.field()::remembersFromInside),
                        "by day the numbers should only go to food they can eat from inside");
                numbersAte[0] = true;
            }
            BlockState under = level.getBlockState(log.get(0));
            BlockState top = level.getBlockState(stump);
            if (kind == Food.STUMP) {
                // A cast joined to the gallery, and the stump on it eaten from it by day: a cast too.
                if (com.antcolonies.block.MudCastBlock.isCast(under) && com.antcolonies.block.MudCastBlock.isCast(top)) {
                    helper.assertTrue(under.getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(net.minecraft.core.Direction.DOWN))
                            && under.getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(net.minecraft.core.Direction.UP)),
                            "the cast of the log does not join the gallery and the cast of the stump: " + under);
                    helper.succeed();
                }
            } else if (kind == Food.BRANCH) {
                // Cased in mud (a plate over each block of it), held up over the gallery, eaten from below.
                int cased = 0;
                for (BlockPos wood : log) {
                    cased += com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(wood.above()), net.minecraft.core.Direction.DOWN) ? 1 : 0;
                }
                if (eaten > eatenAtDawn[0] && cased > 0) {
                    helper.succeed();
                }
            } else if (eaten > eatenAtDawn[0] && (!onAStep || crusts > 0) && numbersAte[0]) {
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() < 89000, "by day: eaten " + eaten + " (at dawn " + eatenAtDawn[0] + "), numbers ate " + numbersAte[0]
                    + " (" + colony.field().describe() + ", from inside " + log.stream().filter(colony.field()::remembersFromInside).count() + "), " + crusts
                    + " crust walls beside the gallery, " + colony.walls().getLeaks(level).size() + " leaks, log " + under + ", stump " + top
                    + ": first log block seen " + colony.food().plants(level).contains(log.get(0)) + ", gallery under it "
                    + tunnel.gallery().contains(log.get(0).below()) + ", eaten from inside " + colony.food().isFeedableFromInside(level, log.get(0))
                    + ", cut " + colony.food().getPiecesCut(log.get(0)) + ", hole " + tunnel.tunnel().hole().toShortString() + ", gallery " + tunnel.gallery());
        });
    }
}
