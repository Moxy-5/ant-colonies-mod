package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.block.GiantTermitomycesBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.savanna.block.TermitomycesBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Termitomyces mushrooms (M7 step 5c): they come up round a mound with a healthy garden after rain, grow, open and wilt. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermitomycesTests {
    /**
     * One mushroom's life, moved on stage by stage as random ticks would: button, closed, open (picked: 2 mushrooms),
     * wilted (picked: nothing), gone. Bone meal brings a button on. A giant button with room grows into a giant three
     * blocks tall that opens; breaking its cap breaks the whole of it and drops 10 mushrooms once. A giant button with no
     * room grows into an ordinary mushroom. The mushroom is food raw and cooks into a better food.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aTermitomycesGrowsOpensWiltsAndIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            }
        }
        BlockPos small = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState button = SavannaBlocks.TERMITOMYCES.get().defaultBlockState();
        level.setBlockAndUpdate(small, button);
        int[] expected = {TermitomycesBlock.CLOSED, TermitomycesBlock.OPEN};
        for (int stage : expected) {
            TermitomycesBlock.growOn(level.getBlockState(small), level, small);
            helper.assertTrue(stageAt(level, small) == stage, "the mushroom should be at stage " + stage + " but is " + level.getBlockState(small));
        }
        level.destroyBlock(small, true);
        helper.assertTrue(mushroomItems(level, small) == 2, "an open mushroom should give 2 mushrooms, gave " + mushroomItems(level, small));
        clearItems(level, small);

        level.setBlockAndUpdate(small, button.setValue(TermitomycesBlock.STAGE, TermitomycesBlock.OPEN));
        TermitomycesBlock.growOn(level.getBlockState(small), level, small);
        helper.assertTrue(stageAt(level, small) == TermitomycesBlock.WILTED, "an open mushroom should wilt");
        TermitomycesBlock.growOn(level.getBlockState(small), level, small);
        helper.assertTrue(level.getBlockState(small).isAir(), "a wilted mushroom should be gone: " + level.getBlockState(small));
        helper.assertTrue(mushroomItems(level, small) == 0, "a mushroom that wilted away dropped something");
        level.setBlockAndUpdate(small, button.setValue(TermitomycesBlock.STAGE, TermitomycesBlock.WILTED));
        level.destroyBlock(small, true);
        helper.assertTrue(mushroomItems(level, small) == 0, "a picked wilted mushroom dropped something");

        // Bone meal.
        level.setBlockAndUpdate(small, button);
        helper.assertTrue(SavannaBlocks.TERMITOMYCES.get().isValidBonemealTarget(level, small, button), "bone meal does not work on a button");
        SavannaBlocks.TERMITOMYCES.get().performBonemeal(level, level.getRandom(), small, button);
        helper.assertTrue(stageAt(level, small) == TermitomycesBlock.CLOSED, "bone meal did not bring the button on");
        level.setBlockAndUpdate(small, Blocks.AIR.defaultBlockState());

        // A giant.
        BlockPos foot = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlockAndUpdate(foot, button.setValue(TermitomycesBlock.GIANT, true));
        TermitomycesBlock.growOn(level.getBlockState(foot), level, foot);
        for (int part = GiantTermitomycesBlock.FOOT; part <= GiantTermitomycesBlock.TOP; part++) {
            BlockState state = level.getBlockState(foot.above(part));
            helper.assertTrue(state.is(SavannaBlocks.GIANT_TERMITOMYCES.get()) && state.getValue(GiantTermitomycesBlock.PART) == part
                    && state.getValue(GiantTermitomycesBlock.STAGE) == GiantTermitomycesBlock.CLOSED,
                    "the giant button did not grow into a closed giant: part " + part + " is " + state);
        }
        SavannaBlocks.GIANT_TERMITOMYCES.get().growOn(level, foot);
        BlockState top = level.getBlockState(foot.above(2));
        helper.assertTrue(top.is(SavannaBlocks.GIANT_TERMITOMYCES.get()) && top.getValue(GiantTermitomycesBlock.STAGE) == GiantTermitomycesBlock.OPEN,
                "the giant's cap did not open: " + top);
        level.destroyBlock(foot.above(2), true);
        for (int part = 0; part < 3; part++) {
            helper.assertTrue(level.getBlockState(foot.above(part)).isAir(), "part " + part + " of the giant is still there after its cap was broken");
        }
        helper.assertTrue(mushroomItems(level, foot) == 10, "a giant should give 10 mushrooms, gave " + mushroomItems(level, foot));
        clearItems(level, foot);

        // A giant button under a low roof grows into an ordinary mushroom.
        level.setBlockAndUpdate(foot, button.setValue(TermitomycesBlock.GIANT, true));
        level.setBlockAndUpdate(foot.above(2), Blocks.STONE.defaultBlockState());
        TermitomycesBlock.growOn(level.getBlockState(foot), level, foot);
        helper.assertTrue(stageAt(level, foot) == TermitomycesBlock.CLOSED, "a giant button with no room should grow as an ordinary mushroom: "
                + level.getBlockState(foot));

        // Food, raw and cooked.
        FoodProperties raw = new ItemStack(SavannaItems.TERMITOMYCES.get()).get(DataComponents.FOOD);
        FoodProperties cooked = new ItemStack(SavannaItems.COOKED_TERMITOMYCES.get()).get(DataComponents.FOOD);
        helper.assertTrue(raw != null && cooked != null && cooked.nutrition() > raw.nutrition(), "the mushroom should be food, better cooked");
        for (var type : List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING)) {
            ItemStack result = level.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(new ItemStack(SavannaItems.TERMITOMYCES.get())), level)
                    .map(recipe -> recipe.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(result.is(SavannaItems.COOKED_TERMITOMYCES.get()), "no " + type + " recipe cooks the mushroom");
        }
        helper.succeed();
    }

    /**
     * A termite colony's gardens are dug (by hand, at once); a rain ends while they hold no comb: no mushrooms. Then they
     * are filled with comb (36 layers, a healthy garden: 6 mushrooms) and another rain ends: mushrooms come up round
     * the mound, every one on earth or finished mound soil, in the open, not in the mound's plan, the nest or a hole,
     * within reach of the entrance. Giants sent up on purpose grow where they fit.
     */
    @GameTest(template = "arena", timeoutTicks = 600, skyAccess = true)
    public static void aHealthyGardenSendsUpMushroomsWhenTheRainEnds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 4; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 2, SURFACE, MIDDLE - 2 + i / 2);
            }
        });
        Colony[] colony = {null};
        helper.runAfterDelay(30, () -> {
            Colony c = TermiteMoundTests.colony(helper);
            helper.assertTrue(c != null && !c.isFounding(), "no termite colony with workers");
            colony[0] = c;
            // Dig the nest at once, up to the first garden.
            for (int i = 0; i < 400 && c.nest().getGardenCells(level).size() < 9; i++) {
                BlockPos target = c.nest().findDigTarget(level);
                if (target == null) {
                    break;
                }
                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            }
            helper.assertTrue(c.nest().getGardenCells(level).size() >= 9, "the first garden is not dug: "
                    + c.nest().getGardenCells(level).size() + " cells");
            c.part(SavannaParts.MUSHROOMS).orElseThrow().setRainForTests(true);
        });
        helper.runAfterDelay(80, () -> colony[0].part(SavannaParts.MUSHROOMS).orElseThrow().setRainForTests(false));
        helper.runAfterDelay(130, () -> {
            Colony c = colony[0];
            helper.assertTrue(c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush() == 0, "mushrooms came up with no comb in the garden: " + c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush());
            helper.assertTrue(mushroomsAround(level, helper).isEmpty(), "there are mushrooms round a mound without a garden");
            for (BlockPos cell : c.nest().getGardenCells(level)) {
                level.setBlockAndUpdate(cell, SavannaBlocks.FUNGUS_COMB.get().defaultBlockState().setValue(SeedPileBlock.LAYERS, 4));
            }
            helper.assertTrue(c.part(SavannaParts.MUSHROOMS).orElseThrow().wanted(level) >= 5, "a garden with " + c.part(SavannaParts.FUNGUS_GARDEN).orElseThrow().countCombLayers(level)
                    + " layers of comb should send up 5 or 6 mushrooms, not " + c.part(SavannaParts.MUSHROOMS).orElseThrow().wanted(level));
            c.part(SavannaParts.MUSHROOMS).orElseThrow().setRainForTests(true);
        });
        helper.runAfterDelay(180, () -> colony[0].part(SavannaParts.MUSHROOMS).orElseThrow().setRainForTests(false));
        helper.runAfterDelay(230, () -> {
            Colony c = colony[0];
            List<BlockPos> mushrooms = mushroomsAround(level, helper);
            helper.assertTrue(c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush() >= 4, "too few mushrooms came up after the rain: " + c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush()
                    + " of " + c.part(SavannaParts.MUSHROOMS).orElseThrow().wanted(level));
            helper.assertTrue(mushrooms.size() == c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush(), mushrooms.size() + " mushrooms round the mound, but "
                    + c.part(SavannaParts.MUSHROOMS).orElseThrow().getLastFlush() + " came up");
            BlockPos entrance = c.nest().getEntrance();
            for (BlockPos cell : mushrooms) {
                helper.assertTrue(TermitomycesBlock.isGround(level.getBlockState(cell.below())), "a mushroom stands on " + level.getBlockState(cell.below()));
                helper.assertTrue(level.canSeeSky(cell), "a mushroom came up under cover at " + cell.toShortString());
                helper.assertTrue(!c.part(SavannaParts.MOUND).orElseThrow().getParts(level).containsKey(cell) && !c.nest().isInside(level, cell),
                        "a mushroom came up where the termites build or live at " + cell.toShortString());
                double distance = Math.sqrt(Math.pow(cell.getX() - entrance.getX(), 2) + Math.pow(cell.getZ() - entrance.getZ(), 2));
                helper.assertTrue(distance <= 13.0, "a mushroom came up too far out: " + distance);
            }
            // Giants sent up on purpose (as the command does) grow into giants three blocks tall.
            int giantsBefore = (int) mushrooms.stream().filter(cell -> level.getBlockState(cell).getValue(TermitomycesBlock.GIANT)).count();
            int giants = c.part(SavannaParts.MUSHROOMS).orElseThrow().sprout(level, 3, true);
            helper.assertTrue(giants > 0, "no giant mushroom found a spot");
            int grown = 0;
            for (BlockPos cell : mushroomsAround(level, helper)) {
                BlockState state = level.getBlockState(cell);
                if (state.getValue(TermitomycesBlock.GIANT)) {
                    TermitomycesBlock.growOn(state, level, cell);
                    helper.assertTrue(level.getBlockState(cell.above(2)).is(SavannaBlocks.GIANT_TERMITOMYCES.get()), "a giant button did not grow into a giant");
                    grown++;
                }
            }
            helper.assertTrue(grown == giants + giantsBefore, grown + " giants grew of " + giants + " sent up and " + giantsBefore + " from the rain");
            AntColoniesMod.LOGGER.info("[mushroom-test] {} mushrooms after the rain, {} giants", mushrooms.size(), giants);
            helper.succeed();
        });
    }

    private static int stageAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(SavannaBlocks.TERMITOMYCES.get()) ? state.getValue(TermitomycesBlock.STAGE) : -1;
    }

    private static int mushroomItems(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5)).stream()
                .filter(item -> item.getItem().is(SavannaItems.TERMITOMYCES.get())).mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static void clearItems(ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5)).forEach(ItemEntity::discard);
    }

    /** The small mushrooms (and giant buttons) in the test area. */
    private static List<BlockPos> mushroomsAround(ServerLevel level, GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        List<BlockPos> found = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            if (level.getBlockState(pos).is(SavannaBlocks.TERMITOMYCES.get())) {
                found.add(pos.immutable());
            }
        }
        return found;
    }
}
