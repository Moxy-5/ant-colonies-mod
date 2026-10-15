package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.DryGrassBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Bare patches and grass growing back (the user's plan of 2026-10-14; {@link com.antcolonies.colony.BarePatches}): dry
 * grass the termites eat away by a foraging hole leaves bare ground, grass eaten farther out does not, and every eaten
 * tuft grows back green later, the bare ground turning back to grass.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteBarePatchTests {
    /**
     * A colony with its nest and foraging holes dug. Three tufts of dry grass: one 2 blocks out from the first hole, one
     * far from every hole, and one that a stone is put on after it is eaten. Each is eaten away (its pieces cut as a
     * forager would). The ground by the hole turns bare (coarse dirt), the far ground stays grass. Then they grow back:
     * green tufts where the spot is free, the bare ground grass again; none under the stone.
     */
    @GameTest(template = "arena", timeoutTicks = 2000)
    public static void dryGrassEatenByAHoleLeavesBareGroundAndGrowsBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2, SURFACE, MIDDLE - 2));
        BlockPos[] tufts = new BlockPos[3]; // by the hole, far out, under a stone later
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            TermiteSoldierTests.digNestAtOnce(level, colony);
            NestPlan.ForagingTunnel first = colony.nest().getForagingTunnels(level).get(0);
            Direction out = first.side();
            tufts[0] = first.hole().above().relative(out, 2);
            // Far out: on the side of the nest away from every hole, past the holes' radius.
            tufts[1] = tufts[0].relative(out.getClockWise(), 9);
            tufts[2] = tufts[0].relative(out.getCounterClockWise(), 9);
            for (BlockPos tuft : tufts) {
                helper.assertTrue(level.getBlockState(tuft.below()).is(Blocks.GRASS_BLOCK), "no grass block under " + tuft.toShortString());
                for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
                    if (tuft != tufts[0]) {
                        helper.assertTrue(Math.max(Math.abs(tunnel.hole().getX() - tuft.getX()), Math.abs(tunnel.hole().getZ() - tuft.getZ())) > 3,
                                "the far tuft " + tuft.toShortString() + " is near a hole");
                    }
                }
                level.setBlock(tuft, ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
                UUID forager = UUID.randomUUID();
                for (int piece = 0; piece < 20 && !level.getBlockState(tuft).isAir(); piece++) {
                    colony.food().clippedGrass(level, tuft, forager);
                }
                helper.assertTrue(level.getBlockState(tuft).isAir(), "the tuft at " + tuft.toShortString() + " was not eaten away");
            }
            helper.assertTrue(level.getBlockState(tufts[0].below()).is(Blocks.COARSE_DIRT), "no bare ground by the hole");
            helper.assertTrue(level.getBlockState(tufts[1].below()).is(Blocks.GRASS_BLOCK), "bare ground far from the holes");
            helper.assertTrue(colony.food().barePatches().waiting() == 3, "not 3 tufts waiting: " + colony.food().barePatches().describe());
            level.setBlock(tufts[2], Blocks.STONE.defaultBlockState(), 3);
            colony.food().barePatches().growBackSoonForTests(level);
        });
        helper.runAfterDelay(400, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            for (int i = 0; i < 2; i++) {
                helper.assertTrue(level.getBlockState(tufts[i]).is(ModBlocks.DRY_GRASS.get())
                        && level.getBlockState(tufts[i]).getValue(DryGrassBlock.GROWTH) == DryGrassBlock.Growth.GREEN,
                        "no green tuft grew back at " + tufts[i].toShortString() + ": " + level.getBlockState(tufts[i]));
                helper.assertTrue(level.getBlockState(tufts[i].below()).is(Blocks.GRASS_BLOCK), "the ground at " + tufts[i].toShortString() + " is not grass again");
            }
            helper.assertTrue(level.getBlockState(tufts[2]).is(Blocks.STONE), "the stone was replaced");
            helper.assertTrue(colony.food().barePatches().waiting() == 0, "tufts still waiting: " + colony.food().barePatches().describe());
            helper.succeed();
        });
    }
}
