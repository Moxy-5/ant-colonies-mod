package com.antcolonies.savanna.gametest;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.HippoGrassBlock;
import com.antcolonies.savanna.block.HippoGrassClumpBlock;
import com.antcolonies.savanna.block.ReedBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Reeds and hippo grass (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 2): where a reed column roots,
 * how tall it grows and that it falls with its foot; hippo grass mats going out from their clump, turning with it ring by
 * ring and dying back from the outside in.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class RiverPlantTests {
    /**
     * A basin: stone floor (y 0), a silt bed (y 1), stone walls round {@code x0..x1, z0..z1}, water {@code deep} blocks
     * over the bed and air above it up to y 8.
     */
    private static void basin(GameTestHelper helper, int x0, int z0, int x1, int z1, int deep) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean wall = x < x0 || x > x1 || z < z0 || z > z1;
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), wall ? Blocks.STONE : SavannaBlocks.SILT.get());
                for (int y = 2; y <= 8; y++) {
                    helper.setBlock(new BlockPos(x, y, z), wall && y < 2 + deep ? Blocks.STONE : !wall && y < 2 + deep ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
    }

    private static boolean roots(GameTestHelper helper, BlockPos rel) {
        BlockPos pos = helper.absolutePos(rel);
        BlockState reed = SavannaBlocks.REEDS.get().defaultBlockState()
                .setValue(ReedBlock.WATERLOGGED, helper.getLevel().getFluidState(pos).isSource());
        return reed.canSurvive(helper.getLevel(), pos);
    }

    /**
     * Reeds root in water 1 or 2 deep and on the bed at the waterline (water beside it), never in water 3 deep, on dry
     * ground away from water, or on stone.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void reedsRootInShallowWaterAndAtTheWaterline(GameTestHelper helper) {
        basin(helper, 2, 2, 4, 4, 1);
        basin(helper, 8, 2, 10, 4, 2);
        basin(helper, 14, 2, 16, 4, 3);
        helper.assertTrue(roots(helper, new BlockPos(3, 2, 3)), "reeds should root in water 1 deep");
        helper.assertTrue(roots(helper, new BlockPos(9, 2, 3)), "reeds should root in water 2 deep");
        helper.assertFalse(roots(helper, new BlockPos(15, 2, 3)), "reeds should not root in water 3 deep");
        // the waterline: a silt bank beside the 1-deep basin (its wall's top swapped for silt, level with the water), and
        // silt far from any water
        helper.setBlock(new BlockPos(5, 2, 3), SavannaBlocks.SILT.get());
        helper.assertTrue(roots(helper, new BlockPos(5, 3, 3)), "reeds should root on the bank with water beside it");
        for (int x = 2; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 0, 10), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 1, 10), x == 6 ? Blocks.STONE : SavannaBlocks.SILT.get());
            helper.setBlock(new BlockPos(x, 2, 10), Blocks.AIR);
        }
        helper.assertFalse(roots(helper, new BlockPos(3, 2, 10)), "reeds should not root on dry ground away from water");
        helper.assertFalse(roots(helper, new BlockPos(6, 2, 10)), "reeds should not root on stone");
        helper.succeed();
    }

    /**
     * A reed column in water 1 deep grows a block at a time to its height (the water block and 2 or 3 over it), the top
     * block drawn with the plume, the one in the water waterlogged; broken at its foot, the whole column falls.
     */
    @GameTest(template = "arena", timeoutTicks = 60)
    public static void aReedColumnGrowsToItsHeightAndFallsWithItsFoot(GameTestHelper helper) {
        basin(helper, 2, 2, 4, 4, 1);
        ServerLevel level = helper.getLevel();
        ReedBlock reeds = SavannaBlocks.REEDS.get();
        BlockPos foot = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(foot, reeds.defaultBlockState().setValue(ReedBlock.WATERLOGGED, true), Block.UPDATE_ALL);
        BlockPos top = foot;
        int grew = 0;
        while (reeds.grow(level, top) && grew < 10) {
            top = top.above();
            grew++;
        }
        int height = top.getY() - foot.getY() + 1, want = 1 + ReedBlock.aboveWater(foot);
        helper.assertTrue(height == want, "the column should stand " + want + " blocks (1 in the water), stands " + height);
        for (BlockPos at = foot; at.getY() <= top.getY(); at = at.above()) {
            BlockState s = level.getBlockState(at);
            helper.assertTrue(s.is(reeds), "a reed block missing at " + at);
            helper.assertTrue(s.getValue(ReedBlock.TOP) == at.equals(top), "only the top block should have the plume");
            helper.assertTrue(s.getValue(ReedBlock.WATERLOGGED) == at.equals(foot), "only the block in the water should hold water");
        }
        level.destroyBlock(foot, false);
        BlockPos last = top;
        helper.runAfterDelay(10, () -> {
            for (BlockPos at = foot; at.getY() <= last.getY(); at = at.above()) {
                helper.assertFalse(level.getBlockState(at).is(reeds), "the column should fall with its foot, still at " + at);
            }
            helper.succeed();
        });
    }

    /**
     * Pushing through reeds (step 3): a pushed column is marked pushed on every block (the client then draws it bent) and
     * stays so while a big body stands in it; once it is gone the column settles for the spring back, then is still. A
     * chicken is too small to push.
     */
    @GameTest(template = "arena", timeoutTicks = 120)
    public static void aPushedReedColumnStaysPushedWhileTouchedThenSettles(GameTestHelper helper) {
        basin(helper, 2, 2, 4, 4, 1);
        ServerLevel level = helper.getLevel();
        ReedBlock reeds = SavannaBlocks.REEDS.get();
        BlockPos foot = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(foot, reeds.defaultBlockState().setValue(ReedBlock.WATERLOGGED, true), Block.UPDATE_ALL);
        BlockPos top = foot;
        while (reeds.grow(level, top)) {
            top = top.above();
        }
        BlockPos last = top;
        java.util.function.IntPredicate all = push -> {
            for (BlockPos at = foot; at.getY() <= last.getY(); at = at.above()) {
                if (level.getBlockState(at).getValue(com.antcolonies.savanna.block.PlantPush.PUSH) != push) {
                    return false;
                }
            }
            return true;
        };
        helper.assertFalse(com.antcolonies.savanna.block.PlantPush.pusher(helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.CHICKEN, 8.5F, 2, 8.5F)),
                "a chicken should be too small to push reeds");
        net.minecraft.world.entity.Mob husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, 3.5F, 2, 3.5F);
        reeds.push(level, last);
        helper.assertTrue(all.test(com.antcolonies.savanna.block.PlantPush.PUSHED), "the whole column should be marked pushed");
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(all.test(com.antcolonies.savanna.block.PlantPush.PUSHED), "the column should stay pushed while the husk stands in it");
            husk.discard();
        });
        helper.runAtTickTime(26, () -> helper.assertTrue(all.test(com.antcolonies.savanna.block.PlantPush.SETTLING),
                "the column should settle once nothing touches it"));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(all.test(com.antcolonies.savanna.block.PlantPush.STILL), "the column should be still after settling");
            helper.succeed();
        });
    }

    /**
     * A hippo pushes plants with its whole body as drawn (5 blocks nose to rump), not only its square box (1.9 across):
     * the blocks 2 ahead of its middle and 2 behind lie under it, 2 to the side do not; a husk's is just its box (the user,
     * 2026-11-22: "the hippo is also missing the ones it should hit").
     */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void aHippoPushesPlantsWithItsWholeBodyNotOnlyItsBox(GameTestHelper helper) {
        for (int x = 2; x <= 20; x++) {
            for (int z = 2; z <= 20; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        net.minecraft.world.entity.Mob hippo = helper.spawnWithNoFreeWill(com.antcolonies.savanna.registry.SavannaEntities.HIPPO.get(), 10.5F, 2, 10.5F);
        hippo.setYRot(-90);                                   // facing +x
        hippo.setYBodyRot(-90);
        com.antcolonies.savanna.block.PlantPush.Footprint f = com.antcolonies.savanna.block.PlantPush.footprint(hippo);
        BlockPos mid = helper.absolutePos(new BlockPos(10, 2, 10));
        double size = hippo.getScale();
        helper.assertTrue(f.overlaps(mid.getX() + (int) Math.floor(2 * size), mid.getZ()), "the block at its nose should lie under it");
        helper.assertTrue(f.overlaps(mid.getX() - (int) Math.floor(2 * size), mid.getZ()), "the block at its rump should lie under it");
        helper.assertFalse(f.overlaps(mid.getX(), mid.getZ() + 2), "a block 2 to its side should not");
        helper.assertFalse(f.overlaps(mid.getX() + (int) Math.ceil(2.5 * size) + 1, mid.getZ()), "a block past its nose should not");
        net.minecraft.world.entity.Mob husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, 15.5F, 2, 15.5F);
        com.antcolonies.savanna.block.PlantPush.Footprint h = com.antcolonies.savanna.block.PlantPush.footprint(husk);
        BlockPos at = helper.absolutePos(new BlockPos(15, 2, 15));
        helper.assertTrue(h.overlaps(at.getX(), at.getZ()) && !h.overlaps(at.getX() + 1, at.getZ()), "a husk should touch only its own block");
        helper.succeed();
    }

    /**
     * Hippo grass: a clump on a silt bank sends mats out over the water (young only 1 block out); the mats take the
     * clump's stage ring by ring; dying back they go from the outside in, the clump staying, which then comes round to
     * young again.
     */
    @GameTest(template = "arena", timeoutTicks = 40)
    public static void hippoGrassTurnsWithItsClumpAndDiesBackToTheBank(GameTestHelper helper) {
        basin(helper, 2, 3, 10, 8, 1);
        ServerLevel level = helper.getLevel();
        RandomSource random = RandomSource.create(5);
        for (int x = 2; x <= 10; x++) {                       // the bank: silt over the wall on the z 2 side
            helper.setBlock(new BlockPos(x, 2, 2), SavannaBlocks.SILT.get());
        }
        BlockPos clump = helper.absolutePos(new BlockPos(6, 3, 2));
        BlockPos r1 = clump.south(), r2 = r1.south(), r3 = r2.south();
        level.setBlock(clump, SavannaBlocks.HIPPO_GRASS_CLUMP.get().defaultBlockState().setValue(HippoGrassClumpBlock.STAGE, HippoGrassClumpBlock.YOUNG), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(clump).canSurvive(level, clump), "a clump should live on the bank by the water");
        helper.assertFalse(HippoGrassBlock.sendOut(level, r2, HippoGrassClumpBlock.YOUNG, 2), "young hippo grass should reach only 1 block out");
        helper.assertTrue(HippoGrassBlock.sendOut(level, r1, HippoGrassClumpBlock.YOUNG, 1), "a young clump should send a mat out beside it");
        helper.assertTrue(HippoGrassBlock.sendOut(level, r2, HippoGrassClumpBlock.GROWN, 2) == (HippoGrassBlock.mostReachAt(r2) >= 2),
                "a grown mat should reach 2 out only where the edge lets it");
        // the next two rings laid by hand whatever the ragged edge, to watch them turn and die back
        BlockState mat = SavannaBlocks.HIPPO_GRASS.get().defaultBlockState().setValue(HippoGrassBlock.STAGE, HippoGrassClumpBlock.YOUNG);
        level.setBlock(r2, mat.setValue(HippoGrassBlock.REACH, 2), Block.UPDATE_ALL);
        level.setBlock(r3, mat.setValue(HippoGrassBlock.REACH, 3), Block.UPDATE_ALL);
        level.setBlock(clump, level.getBlockState(clump).setValue(HippoGrassClumpBlock.STAGE, HippoGrassClumpBlock.FLOWERING), Block.UPDATE_ALL);
        level.getBlockState(r3).randomTick(level, r3, random);
        helper.assertTrue(level.getBlockState(r3).getValue(HippoGrassBlock.STAGE) == HippoGrassClumpBlock.YOUNG,
                "the outer mat should wait for the ring nearer the bank");
        for (BlockPos at : new BlockPos[] {r1, r2, r3}) {
            level.getBlockState(at).randomTick(level, at, random);
            helper.assertTrue(level.getBlockState(at).getValue(HippoGrassBlock.STAGE) == HippoGrassClumpBlock.FLOWERING,
                    "the mats should flower with their clump, ring by ring");
        }
        level.setBlock(clump, level.getBlockState(clump).setValue(HippoGrassClumpBlock.STAGE, HippoGrassClumpBlock.DYING), Block.UPDATE_ALL);
        for (BlockPos at : new BlockPos[] {r1, r2, r3}) {
            level.getBlockState(at).randomTick(level, at, random);
        }
        for (int i = 0; i < 40; i++) {                         // the inner mats hold on while one lies farther out
            level.getBlockState(r1).randomTick(level, r1, random);
            level.getBlockState(r2).randomTick(level, r2, random);
        }
        helper.assertTrue(level.getBlockState(r1).is(SavannaBlocks.HIPPO_GRASS.get()) && level.getBlockState(r2).is(SavannaBlocks.HIPPO_GRASS.get()),
                "dying back should go from the outside in");
        for (int i = 0; i < 40; i++) {
            for (BlockPos at : new BlockPos[] {r3, r2, r1}) {
                BlockState s = level.getBlockState(at);
                if (s.is(SavannaBlocks.HIPPO_GRASS.get())) {
                    s.randomTick(level, at, random);
                }
            }
        }
        for (BlockPos at : new BlockPos[] {r1, r2, r3}) {
            helper.assertTrue(level.getBlockState(at).isAir(), "every mat should be gone once died back, still at " + at);
        }
        helper.assertTrue(level.getBlockState(clump).is(SavannaBlocks.HIPPO_GRASS_CLUMP.get()), "the clump should live on");
        helper.assertTrue(HippoGrassClumpBlock.next(HippoGrassClumpBlock.DYING) == HippoGrassClumpBlock.YOUNG, "dying back should come round to young");
        helper.succeed();
    }
}
