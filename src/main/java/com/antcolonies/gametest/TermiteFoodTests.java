package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Termites gathering food (M7 step 4): dead plant material carried into the food store beside the nest shaft. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteFoodTests {
    /**
     * A termite colony with workers digs its food store (a side room across the shaft from the royal chamber), then
     * the foragers cut dead plants round the mound to pieces (a dead bush, tall and short grass, a hay bale) and fetch
     * a stick, a sapling and wheat off the ground, and carry every piece into the store, where it lies in forage heaps.
     * Checked: the small plants and the items are all gathered, every piece cut so far is accounted for (in the store,
     * in a termite's mandibles or dropped on the ground), and all the heaps lie in the food store.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void termitesGatherDeadPlantsIntoTheirFoodStore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 3, SURFACE, MIDDLE - 2 + i / 3);
            }
        });
        // The plants, a few blocks out from the mound, and the items on the ground.
        BlockPos bush = new BlockPos(MIDDLE + 7, SURFACE, MIDDLE);
        BlockPos tallGrass = new BlockPos(MIDDLE - 7, SURFACE, MIDDLE + 2);
        BlockPos grassA = new BlockPos(MIDDLE + 2, SURFACE, MIDDLE + 7);
        BlockPos grassB = new BlockPos(MIDDLE - 3, SURFACE, MIDDLE - 7);
        BlockPos hay = new BlockPos(MIDDLE + 6, SURFACE, MIDDLE - 6);
        helper.setBlock(bush, Blocks.DEAD_BUSH);
        DoublePlantBlock.placeAt(level, Blocks.TALL_GRASS.defaultBlockState(), helper.absolutePos(tallGrass), 3);
        helper.setBlock(grassA, Blocks.SHORT_GRASS);
        helper.setBlock(grassB, Blocks.SHORT_GRASS);
        helper.setBlock(hay, Blocks.HAY_BLOCK);
        List<ItemEntity> items = new ArrayList<>();
        int[][] itemSpots = {{MIDDLE - 6, MIDDLE - 3}, {MIDDLE + 4, MIDDLE + 6}, {MIDDLE - 5, MIDDLE + 6}};
        ItemStack[] stacks = {new ItemStack(Items.STICK), new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.WHEAT)};
        for (int i = 0; i < stacks.length; i++) {
            BlockPos at = helper.absolutePos(new BlockPos(itemSpots[i][0], SURFACE, itemSpots[i][1]));
            ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, stacks[i]);
            item.setDeltaMovement(0, 0, 0);
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
            items.add(item);
        }

        Colony[] colony = {null};
        helper.onEachTick(() -> {
            if (colony[0] == null) {
                colony[0] = TermiteMoundTests.colony(helper);
            }
            if (colony[0] != null && helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[forage-test] tick {}: food {}, store cells {}, hay cut {}, dig target {}", helper.getTick(),
                        colony[0].food().getFood(), colony[0].nest().getFoodStoreCells(level).size(),
                        colony[0].food().getPiecesCut(helper.absolutePos(hay)), colony[0].nest().findDigTarget(level));
            }
        });

        helper.succeedWhen(() -> {
            Colony c = colony[0];
            helper.assertTrue(c != null, "no termite colony yet");
            // Small plants and items all gathered.
            for (BlockPos plant : List.of(bush, tallGrass, grassA, grassB)) {
                helper.assertTrue(helper.getBlockState(plant).isAir(), "a plant is still there at " + plant.toShortString()
                        + " (food " + c.food().getFood() + ")");
            }
            for (ItemEntity item : items) {
                helper.assertTrue(!item.isAlive(), "the " + item.getItem().getHoverName().getString() + " is still lying on the ground");
            }
            // Every piece cut so far is somewhere: in the store, in a termite's mandibles, or dropped on the ground.
            BlockPos hayAt = helper.absolutePos(hay);
            int small = c.food().piecesOf(Blocks.DEAD_BUSH.defaultBlockState()) + c.food().piecesOf(Blocks.TALL_GRASS.defaultBlockState())
                    + 2 * c.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState()) + stacks.length;
            int hayCut = level.getBlockState(hayAt).is(Blocks.HAY_BLOCK) ? c.food().getPiecesCut(hayAt)
                    : c.food().piecesOf(Blocks.HAY_BLOCK.defaultBlockState());
            // (Only this colony's termites and food items: the tests next door have ants and seeds of their own.)
            int carried = 0;
            for (AbstractAntEntity ant : level.getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(16))) {
                if (c.getId().equals(ant.getColonyId())) {
                    carried += ant.getCarriedSeed().getCount();
                }
            }
            int lying = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
                if (item.getItem().is(c.profile().food().itemTag())) {
                    lying += item.getItem().getCount();
                }
            }
            int stored = c.food().getFood();
            helper.assertTrue(stored + carried + lying == small + hayCut, "stored " + stored + " + carried " + carried + " + lying " + lying
                    + " is not the " + small + " small pieces + " + hayCut + " cut off the hay bale");
            // The heaps lie in the food store, the side room across the shaft from the royal chamber.
            NestPlan.Chamber store = c.nest().getSideRooms(level).get(0);
            NestPlan.Chamber royal = c.nest().getPlannedChambers(level).get(0);
            helper.assertTrue(store.kind() == NestPlan.Kind.FOOD && royal.kind() == NestPlan.Kind.QUEEN, "the nest plan is not royal chamber + food store");
            BlockPos entrance = c.getCenter();
            helper.assertTrue((store.center().getX() - entrance.getX()) * (royal.center().getX() - entrance.getX())
                    + (store.center().getZ() - entrance.getZ()) * (royal.center().getZ() - entrance.getZ()) < 0,
                    "the food store is not across the shaft from the royal chamber");
            int layers = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(24, SURFACE + 2, 24)))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.FORAGE_PILE.get())) {
                    helper.assertTrue(store.room().contains(pos.immutable()), "a forage heap lies outside the food store at " + pos.toShortString());
                    layers += state.getValue(SeedPileBlock.LAYERS);
                } else {
                    helper.assertTrue(!state.is(ModBlocks.SEED_PILE.get()), "termites made a seed heap");
                }
            }
            helper.assertTrue(layers == stored, "the heaps in the store hold " + layers + " pieces, the colony counts " + stored);
        });
    }
}
