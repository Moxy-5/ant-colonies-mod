package com.antcolonies.gametest;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteMoundBlock;
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

    /** Termites walk through the hole block; an ant (and so any other creature) bumps into it. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void onlyTermitesPassThroughTheHoleBlock(GameTestHelper helper) {
        ground(helper);
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.TERMITE_HOLE.get());
        BlockPos hole = helper.absolutePos(new BlockPos(2, 1, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 4, 1, 4);
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), 4, 1, 1);
        BlockState state = helper.getLevel().getBlockState(hole);
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(termite)).isEmpty(), "a termite bumps into the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(ant)).isEmpty(), "an ant walks through the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.empty()).isEmpty(), "the hole block is not solid for everyone else");
        helper.succeed();
    }
}
