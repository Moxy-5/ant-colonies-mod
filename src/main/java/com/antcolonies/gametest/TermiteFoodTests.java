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

/** Termites gathering food (M7 step 4): dead plant material carried into the fungus garden and built into comb (step 5). */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteFoodTests {
    /**
     * A termite colony with workers digs its first fungus garden room (beside the royal chamber), then the foragers
     * cut dead plants round the mound to pieces (a dead bush, tall and short grass, a hay bale) and fetch a stick, a
     * sapling and wheat off the ground, and carry every piece into the garden, where it is built into fungus comb.
     * Checked: the small plants and the items are all gathered, every piece cut so far is accounted for (in the comb,
     * grown into a fungus nodule, in a termite's mandibles or dropped on the ground), and all the comb lies in the
     * garden rooms.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void termitesGatherDeadPlantsIntoTheirFungusGarden(GameTestHelper helper) {
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
                if (colony[0] != null) {
                    colony[0].setForagingTimeForTests(true); // always night here: the foragers go out
                    for (BlockPos plant : List.of(bush, tallGrass, grassA, grassB, hay)) {
                        colony[0].foodMap().knowForTests(helper.absolutePos(plant)); // (finding them is the next test)
                    }
                }
            }
            if (colony[0] != null && helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[forage-test] tick {}: food {}, store cells {}, hay cut {}, bush cut {}, dig target {}{}{}", helper.getTick(),
                        colony[0].food().getFood(), colony[0].nest().getFoodStoreCells(level).size(),
                        colony[0].food().getPiecesCut(helper.absolutePos(hay)), colony[0].food().getPiecesCut(helper.absolutePos(bush)),
                        colony[0].nest().findDigTarget(level), colony[0].alarm().describe(level), colony[0].foodMap().describe());
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
                if (c.getId().equals(ant.getColonyId()) && !ant.isCarryingBread()) { // food pieces, not nodules
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
            int grown = c.garden().getNodulesGrown();
            helper.assertTrue(stored + grown + carried + lying == small + hayCut, "stored " + stored + " + grown into nodules " + grown + " + carried "
                    + carried + " + lying " + lying + " is not the " + small + " small pieces + " + hayCut + " cut off the hay bale");
            // The comb lies in the garden rooms round the royal chamber; no side room, no forage or seed heaps.
            helper.assertTrue(c.nest().getSideRooms(level).isEmpty(), "termites planned a side room");
            helper.assertTrue(c.nest().getPlannedChambers(level).get(0).kind() == NestPlan.Kind.QUEEN, "the nest plan is not the royal chamber");
            java.util.Set<BlockPos> gardens = new java.util.HashSet<>();
            c.nest().getGardens(level).forEach(garden -> gardens.addAll(garden.room()));
            int layers = 0;
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(24, SURFACE + 2, 24)))) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.FUNGUS_COMB.get())) {
                    helper.assertTrue(gardens.contains(pos.immutable()), "fungus comb lies outside the garden rooms at " + pos.toShortString());
                    layers += state.getValue(SeedPileBlock.LAYERS);
                } else {
                    helper.assertTrue(!state.is(ModBlocks.SEED_PILE.get()) && !state.is(ModBlocks.FORAGE_PILE.get()), "termites made a seed or forage heap");
                }
            }
            helper.assertTrue(layers == stored, "the comb holds " + layers + " pieces, the colony counts " + stored);
        });
    }

    /**
     * Finding food (the colony's food map): an old colony with its nest dug out and both foraging holes open, a tuft of
     * short grass right beside each hole and a hay bale 11 blocks out from the first. By "day" nobody knows the hay. A
     * worker is put down beside it (as the user tests it): it finds the hay and, once it is back inside, tells the
     * colony. Then "night": the foragers go for the hay (worth far more) rather than the grass by the holes. Checked:
     * the hay is unknown at first, found and then known only once the finder is inside, and by the time 8 pieces are cut
     * off the hay at most half as many have been cut off the grass.
     */
    @GameTest(template = "arena", timeoutTicks = 8000, skyAccess = true)
    public static void aWorkerFindsAHayBaleAndTheForagersGoForItRatherThanTheGrass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 5; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i, SURFACE, MIDDLE - 2);
            }
        });
        BlockPos[] spots = new BlockPos[3]; // the hay, the grass by the first hole, the grass by the second
        int[] phase = {0}; // 0 setting up, 1 day: the finder, 2 night
        com.antcolonies.entity.TermiteWorkerEntity[] finder = {null};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            colony.setForagingTimeForTests(false);
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            for (com.antcolonies.entity.TermiteWorkerEntity worker : level.getEntitiesOfClass(com.antcolonies.entity.TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(old); // old workers: foragers first
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            level.setBlock(colony.nest().getEntrance(), Blocks.STONE.defaultBlockState(), 3);
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            helper.assertTrue(tunnels.size() == 2, "the nest has " + tunnels.size() + " foraging tunnels");
            for (int i = 0; i < 2; i++) {
                NestPlan.ForagingTunnel tunnel = tunnels.get(i);
                level.setBlock(tunnel.hole(), Blocks.AIR.defaultBlockState(), 3);
                // A tuft of grass right beside the hole, on the side away from the hay.
                spots[1 + i] = tunnel.hole().above().relative(tunnel.side().getCounterClockWise(), 2);
                level.setBlock(spots[1 + i], Blocks.SHORT_GRASS.defaultBlockState(), 3);
            }
            spots[0] = tunnels.get(0).hole().above().relative(tunnels.get(0).side().getClockWise(), 11);
            level.setBlock(spots[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            helper.assertTrue(!colony.foodMap().isKnown(spots[0]) && !colony.foodMap().isFoundButUntold(spots[0]),
                    "the colony knew of the hay before anyone went near it");
            // (Put down now, while the holes are still open: by day they are plugged once everybody is in.)
            BlockPos beside = spots[0].relative(net.minecraft.core.Direction.NORTH, 2);
            finder[0] = ModEntities.TERMITE_WORKER.get().create(level);
            finder[0].moveTo(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5, 0.0F, 0.0F);
            finder[0].joinColony(colony); // (on its own it joins the nearest colony, which can be a neighbouring test's)
            level.addFreshEntity(finder[0]);
            phase[0] = 1;
        });
        boolean[] sawUntold = {false};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0 || finder[0] == null) {
                return;
            }
            if (phase[0] == 1) {
                if (helper.getTick() % 200 == 0) {
                    com.antcolonies.entity.TermiteWorkerEntity f = finder[0];
                    AntColoniesMod.LOGGER.info("[find-test] tick {}: finder alive {} at {} (inside {}, colony ours {}, last hurt by {}), found {}, holes {}{}",
                            helper.getTick(), f.isAlive(), f.blockPosition().toShortString(), colony.nest().isInside(level, f.blockPosition()),
                            colony.getId().equals(f.getColonyId()), f.getLastHurtByMob(), colony.foodMap().isFoundButUntold(spots[0]),
                            colony.foragingHoles().waysOut(level).size(), colony.alarm().describe(level));
                }
                if (colony.foodMap().isFoundButUntold(spots[0])) {
                    sawUntold[0] = true;
                    helper.assertTrue(!colony.foodMap().isKnown(spots[0]), "the hay is known before its finder is back inside");
                }
                if (colony.foodMap().isKnown(spots[0])) {
                    helper.assertTrue(sawUntold[0], "the colony knew of the hay without anyone finding it first");
                    helper.assertTrue(colony.nest().isInside(level, finder[0].blockPosition()), "the hay is known while its finder is still outside");
                    colony.setForagingTimeForTests(true);
                    phase[0] = 2;
                    AntColoniesMod.LOGGER.info("[find-test] tick {}: the hay is known, night", helper.getTick());
                }
            } else if (helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[find-test] tick {}: hay cut {}, grass cut {} and {}; {}", helper.getTick(),
                        colony.food().getPiecesCut(spots[0]), grassCut(level, colony, spots[1]), grassCut(level, colony, spots[2]),
                        colony.foodMap().describe());
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(phase[0] == 2, "the hay is not known yet");
            int hay = colony.food().getPiecesCut(spots[0]);
            int grass = grassCut(level, colony, spots[1]) + grassCut(level, colony, spots[2]);
            helper.assertTrue(hay >= 8, "only " + hay + " pieces cut off the hay (grass " + grass + ")");
            helper.assertTrue(grass * 2 <= hay, grass + " pieces cut off the grass by the holes, " + hay + " off the hay");
        });
    }

    /** How many pieces were cut off the tuft at this spot (all of them if it is gone). */
    private static int grassCut(ServerLevel level, Colony colony, BlockPos tuft) {
        return level.getBlockState(tuft).is(Blocks.SHORT_GRASS) ? colony.food().getPiecesCut(tuft)
                : colony.food().piecesOf(Blocks.SHORT_GRASS.defaultBlockState());
    }
}
