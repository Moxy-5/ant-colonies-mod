package com.antcolonies.gametest;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteHoleBlock;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.species.Species;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The termite mound blocks on their own (step 3b, part 1): layers, setting before stacking, ageing, the hole block. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteMoundTests {
    static final int SURFACE = 12;
    static final int MIDDLE = 12;

    /** Earth 12 blocks deep under grass over the whole test area (room for the nest and the galleries). */
    static void deepGround(GameTestHelper helper) {
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
    }

    /** The termite colony in this test area (one with a living queen). */
    static Colony colony(GameTestHelper helper) {
        net.minecraft.world.phys.AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(colony -> area.contains(net.minecraft.world.phys.Vec3.atCenterOf(colony.getCenter()))
                        && colony.getQueen(helper.getLevel()) != null && colony.getSpecies().equals(Species.MACROTERMES))
                .findFirst().orElse(null);
    }

    private static void ground(GameTestHelper helper) {
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }

    /**
     * A block is built in 5 layers (4 quarters of height, then the finishing layer), and nothing can be built on it
     * until it is finished and has set. A finished block dries and hardens through all four ages, never further.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aMoundBlockIsBuiltInLayersAndMustSetBeforeMoreIsStackedOnIt(GameTestHelper helper) {
        ground(helper);
        ServerLevel level = helper.getLevel();
        TermiteMoundBlock mound = ModBlocks.TERMITE_MOUND.get();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int layer = 1; layer <= TermiteMoundBlock.FINISHED; layer++) {
            helper.assertTrue(mound.addLayer(level, pos), "layer " + layer + " could not be added");
            BlockState state = level.getBlockState(pos);
            helper.assertTrue(state.getValue(TermiteMoundBlock.LAYERS) == layer, "the block has " + state.getValue(TermiteMoundBlock.LAYERS) + " layers, not " + layer);
            if (layer < TermiteMoundBlock.FINISHED) {
                helper.assertTrue(!mound.addLayer(level, pos.above()), "something was built on a block with only " + layer + " layers");
            }
        }
        helper.assertTrue(!mound.addLayer(level, pos), "a sixth layer was added to a finished block");
        helper.assertTrue(level.getBlockState(pos).getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) == 1.0, "a finished block is not a full block");
        helper.assertTrue(!mound.addLayer(level, pos.above()), "something was built on a block that has not set yet");
        // It dries with age: random ticks until it is old, and no further.
        RandomSource random = RandomSource.create(1L);
        int[] seen = new int[TermiteMoundBlock.OLDEST + 1];
        for (int tick = 0; tick < 20000; tick++) {
            BlockState state = level.getBlockState(pos);
            seen[state.getValue(TermiteMoundBlock.AGE)]++;
            if (state.isRandomlyTicking()) {
                state.randomTick(level, pos, random);
            }
        }
        for (int age = 0; age <= TermiteMoundBlock.OLDEST; age++) {
            helper.assertTrue(seen[age] > 0, "the block never reached age " + age);
        }
        BlockState old = level.getBlockState(pos);
        helper.assertTrue(old.getValue(TermiteMoundBlock.AGE) == TermiteMoundBlock.OLDEST && !old.isRandomlyTicking(), "the block did not end up old");
        helper.assertTrue(TermiteMoundBlock.hardness(old) > TermiteMoundBlock.hardness(ModBlocks.TERMITE_MOUND.get().defaultBlockState()),
                "old soil is not harder than wet soil");
        helper.assertTrue(mound.addLayer(level, pos.above()), "nothing could be built on a block that has set");
        // A finished block drops itself (this also fails if the loot table does not load); wet soil drops nothing.
        BlockState fresh = mound.defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED);
        helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(fresh, level, pos, null).stream()
                .anyMatch(stack -> stack.is(com.antcolonies.registry.ModItems.TERMITE_MOUND.get())), "a finished block drops nothing");
        helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(mound.defaultBlockState(), level, pos, null).isEmpty(),
                "a block still being built drops something");
        helper.succeed();
    }

    /** Over a gap (a tunnel in the mound) a block is built out from set soil beside it, never from wet soil. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aBlockOverAGapIsBuiltOutFromSetSoilBesideIt(GameTestHelper helper) {
        ground(helper);
        ServerLevel level = helper.getLevel();
        TermiteMoundBlock mound = ModBlocks.TERMITE_MOUND.get();
        BlockState finished = mound.defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED);
        // A pillar two blocks high beside an empty space whose floor is one block lower (the gap).
        helper.setBlock(new BlockPos(2, 1, 2), finished);
        helper.setBlock(new BlockPos(2, 2, 2), finished);
        BlockPos over = helper.absolutePos(new BlockPos(3, 2, 2));
        helper.assertTrue(!mound.addLayer(level, over), "a block was started over the gap beside wet soil");
        helper.setBlock(new BlockPos(2, 2, 2), finished.setValue(TermiteMoundBlock.AGE, 1));
        helper.assertTrue(mound.addLayer(level, over), "no block could be built out from set soil over the gap");
        helper.assertTrue(level.getBlockState(over).is(mound), "the new layer fell off");
        helper.succeed();
    }

    /**
     * A termite colony with workers builds its mound: first the ring round the entrance with a chimney block in it,
     * then (one stage taller) a second ring on the first, the old chimney block cleared out of the shaft and a new one
     * on top. The soil comes from digging (the nest, then the galleries). Wet soil is set at once here, as if it had
     * dried (it takes about 20 minutes in the game).
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void termitesBuildTheFirstTwoStagesOfTheirMound(GameTestHelper helper) {
        helper.getLevel().getServer().tickRateManager().setTickRate(2000.0F);
        deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 3 + i % 4, SURFACE, MIDDLE - 3 + i / 4);
            }
        });
        int[] stage = {1};
        long[] stageOneAt = {-1};
        helper.onEachTick(() -> {
            Colony colony = colony(helper);
            if (colony == null) {
                return;
            }
            ServerLevel level = helper.getLevel();
            if (colony.mound().getStage(level) != stage[0]) {
                colony.mound().setStage(level, stage[0]);
            }
            // Wet soil sets at once.
            for (var entry : colony.mound().getParts(level).entrySet()) {
                BlockState state = level.getBlockState(entry.getKey());
                if (state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state) && state.getValue(TermiteMoundBlock.AGE) == 0) {
                    level.setBlockAndUpdate(entry.getKey(), state.setValue(TermiteMoundBlock.AGE, 1));
                }
            }
            if (stage[0] == 1 && isBuilt(level, colony)) {
                stage[0] = 2;
                stageOneAt[0] = helper.getTick();
                AntColoniesMod.LOGGER.info("[mound-test] tick {}: stage 1 built", helper.getTick());
            }
            if (helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[mound-test] tick {}: stage {}, {} of {} blocks finished, spots {}, dig target {}",
                        helper.getTick(), stage[0], colony.mound().countFinished(level), colony.mound().wallsAt(stage[0]),
                        colony.mound().getBuildSpots(level).size(), colony.nest().findDigTarget(level));
            }
        });
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && stageOneAt[0] >= 0, "the first stage is not built yet");
            helper.assertTrue(isBuilt(level, colony), "stage 2: " + colony.mound().countFinished(level) + " of "
                    + colony.mound().wallsAt(2) + " blocks finished");
            BlockPos entrance = colony.getCenter();
            helper.assertTrue(level.getBlockState(entrance).isAir(), "the old chimney block is still in the entrance");
            helper.assertTrue(level.getBlockState(entrance.above()).getBlock() instanceof TermiteHoleBlock, "no chimney block on top");
        });
    }

    /** Every part of the colony's mound at its stage now is there: walls finished, chimney blocks shaped, hollows open. */
    private static boolean isBuilt(ServerLevel level, Colony colony) {
        for (var entry : colony.mound().getParts(level).entrySet()) {
            BlockState state = level.getBlockState(entry.getKey());
            boolean ok = switch (entry.getValue()) {
                case WALL -> state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state);
                case HOLE -> state.getBlock() instanceof TermiteHoleBlock;
                case OPEN -> state.isAir();
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * A termite walks through a chimney block into a room that is closed all round; an ant finds no way in. The route
     * planner treats the chimney block as a doorway for termites only.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void aTermiteWalksThroughAChimneyBlockIntoAClosedRoom(GameTestHelper helper) {
        ground(helper);
        // A closed room (inside 3x3, 2 high) with one chimney block in its wall at floor height.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                for (int y = 1; y <= 3; y++) {
                    boolean inside = x > 0 && x < 4 && z > 0 && z < 4 && y < 3;
                    helper.setBlock(new BlockPos(x, y, z), inside ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(4, 1, 2), ModBlocks.TERMITE_HOLE.get());
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 6, 1, 2);
        WorkerAntEntity ant = TestAnts.loneAnt(helper, 6, 1, 4);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(termite.getNavigator().moveTo(inside, 1.0), "the termite found no route through the chimney block");
            helper.assertTrue(!ant.getNavigator().moveTo(inside, 1.0), "the ant found a route into the closed room");
        });
        helper.succeedWhen(() -> helper.assertTrue(helper.getTick() > 20 && termite.blockPosition().equals(inside),
                "the termite is at " + termite.blockPosition().subtract(inside) + " from the middle of the room"));
    }

    /**
     * A termite climbs up a chimney shaft and comes out of the mouth of the spire on top of the chimney block: up inside
     * the spire, a look round with its head out, then over the rim and down onto the mound. Then it goes back in the
     * same way and down the shaft. Checked: it really came up through the middle of the spire to the rim, it stopped
     * there for a moment, and it ends where it was sent.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void aTermiteComesOutOfTheChimneyMouthAndGoesBackIn(GameTestHelper helper) {
        ground(helper);
        // A chimney: a ring of soil round a shaft, 3 blocks high, with a chimney block on top of the shaft.
        BlockState soil = ModBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED);
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || z != 2) {
                        helper.setBlock(new BlockPos(x, y, z), soil);
                    }
                }
            }
        }
        helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.TERMITE_HOLE.get());
        BlockPos hole = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos bottom = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos onTop = helper.absolutePos(new BlockPos(3, 4, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 2, 1, 2);
        double rim = hole.getY() + ModBlocks.TERMITE_HOLE.get().mouthHeight();
        int[] phase = {0};
        int[] lookingOut = {0};
        double[] highest = {0};
        helper.runAfterDelay(10, () -> helper.assertTrue(termite.getNavigator().moveTo(onTop, 1.0), "no route out of the chimney"));
        helper.onEachTick(() -> {
            // In the spire: near its middle (the hitbox is a little off it, so the drawn body is in the middle).
            boolean inMouth = Math.abs(termite.getX() - (hole.getX() + 0.5)) < 0.2 && Math.abs(termite.getZ() - (hole.getZ() + 0.5)) < 0.2;
            if (inMouth) {
                highest[0] = Math.max(highest[0], termite.getY());
                if (termite.getY() > rim - 0.3 && termite.getY() < rim - 0.05) {
                    lookingOut[0]++;
                }
            }
            if (phase[0] == 0 && helper.getTick() > 20 && termite.getNavigator().isDone() && termite.blockPosition().equals(onTop)) {
                helper.assertTrue(highest[0] >= rim, "the termite did not come up through the spire to its rim (highest " + (highest[0] - hole.getY()) + ")");
                helper.assertTrue(lookingOut[0] >= 20, "the termite did not stop to look out of the mouth (" + lookingOut[0] + " ticks)");
                phase[0] = 1;
                highest[0] = 0;
                helper.assertTrue(termite.getNavigator().moveTo(bottom, 1.0), "no route back into the chimney");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 1, "the termite is not out on top yet (at " + termite.blockPosition().subtract(hole) + " from the chimney block)");
            helper.assertTrue(termite.getNavigator().isDone() && termite.blockPosition().equals(bottom),
                    "the termite is not back down the shaft (at " + termite.blockPosition().subtract(hole) + " from the chimney block)");
            helper.assertTrue(highest[0] >= rim, "the termite did not go in by the mouth of the spire (highest " + (highest[0] - hole.getY()) + ")");
        });
    }

    /** Termites walk through the hole block; an ant (and so any other creature) bumps into it. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void onlyTermitesPassThroughTheHoleBlock(GameTestHelper helper) {
        ground(helper);
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.TERMITE_HOLE.get());
        BlockPos hole = helper.absolutePos(new BlockPos(2, 1, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 4, 1, 4);
        WorkerAntEntity ant = TestAnts.loneAnt(helper, 4, 1, 1);
        BlockState state = helper.getLevel().getBlockState(hole);
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(termite)).isEmpty(), "a termite bumps into the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(ant)).isEmpty(), "an ant walks through the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.empty()).isEmpty(), "the hole block is not solid for everyone else");
        helper.succeed();
    }
}
