package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.DeadWoodBlock;
import com.antcolonies.block.DryGrassBlock;
import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;
import com.antcolonies.worldgen.BranchShedding;
import com.antcolonies.worldgen.FallenBranchFeature;
import com.antcolonies.worldgen.FallenTreeFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BoneMealItem;
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
     * cut dead plants round the mound to pieces (a dead bush, tall and short Dry Grass, a hay bale) and fetch a stick
     * and wheat off the ground, and carry every piece into the garden, where it is built into fungus comb. Living plants
     * are left alone: a tuft of green grass and a sapling lying on the ground. Checked: the dead plants and the items
     * are all gathered, the green grass and the sapling are still there, every piece cut so far is accounted for (in the
     * comb, grown into a fungus nodule, in a termite's mandibles or dropped on the ground), and all the comb lies in the
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
        BlockPos greenGrass = new BlockPos(MIDDLE + 7, SURFACE, MIDDLE + 3);
        helper.setBlock(bush, Blocks.DEAD_BUSH);
        DoublePlantBlock.placeAt(level, ModBlocks.TALL_DRY_GRASS.get().defaultBlockState(), helper.absolutePos(tallGrass), 3);
        helper.setBlock(grassA, ModBlocks.DRY_GRASS.get());
        helper.setBlock(grassB, ModBlocks.DRY_GRASS.get());
        helper.setBlock(hay, Blocks.HAY_BLOCK);
        helper.setBlock(greenGrass, Blocks.SHORT_GRASS);
        List<ItemEntity> items = new ArrayList<>();
        int[][] itemSpots = {{MIDDLE - 6, MIDDLE - 3}, {MIDDLE - 5, MIDDLE + 6}, {MIDDLE + 4, MIDDLE + 6}};
        ItemStack[] stacks = {new ItemStack(Items.STICK), new ItemStack(Items.WHEAT)};
        for (int i = 0; i < stacks.length; i++) {
            items.add(lay(helper, itemSpots[i], stacks[i]));
        }
        ItemEntity sapling = lay(helper, itemSpots[2], new ItemStack(Items.OAK_SAPLING)); // alive: not termite food

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
            // Living plants are left alone.
            helper.assertTrue(helper.getBlockState(greenGrass).is(Blocks.SHORT_GRASS), "the termites took the green grass");
            helper.assertTrue(sapling.isAlive(), "the termites took the sapling");
            // Every piece cut so far is somewhere: in the store, in a termite's mandibles, or dropped on the ground.
            BlockPos hayAt = helper.absolutePos(hay);
            int small = c.food().piecesOf(Blocks.DEAD_BUSH.defaultBlockState()) + c.food().piecesOf(ModBlocks.TALL_DRY_GRASS.get().defaultBlockState())
                    + 2 * c.food().piecesOf(ModBlocks.DRY_GRASS.get().defaultBlockState()) + stacks.length;
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
     * Dry Grass right beside each hole and a hay bale 11 blocks out from the first. By "day" nobody knows the hay. A
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
                level.setBlock(spots[1 + i], ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
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
        return level.getBlockState(tuft).is(ModBlocks.DRY_GRASS.get()) ? colony.food().getPiecesCut(tuft)
                : colony.food().piecesOf(ModBlocks.DRY_GRASS.get().defaultBlockState());
    }

    /**
     * Fallen branches: a branch laid on the ground is 2 or 3 blocks long, thick end first; termites count each block as
     * food and it looks more eaten (mud-covered and thinner) with every quarter cut off, until the last piece leaves
     * nothing. A block someone else already gnawed half away counts as half eaten. An acacia's natural leaves above the
     * ground shed a branch, leaves a player placed do not. Quick: the cuts are made by hand.
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void aFallenBranchIsEatenInStagesAndAcaciasShedThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            BlockPos start = helper.absolutePos(new BlockPos(MIDDLE + 6, SURFACE, MIDDLE - 6));
            helper.assertTrue(FallenBranchFeature.placeBranch(level, start, level.getRandom(), 3), "no branch fitted");
            BlockState base = level.getBlockState(start);
            helper.assertTrue(base.is(ModBlocks.FALLEN_BRANCH.get()) && base.getValue(FallenBranchBlock.PART) == FallenBranchBlock.Part.BASE,
                    "the branch does not start with its thick end: " + base);
            int pieces = colony.food().piecesOf(base);
            helper.assertTrue(pieces == 8, "a branch block is worth " + pieces + " pieces");

            // Cut it piece by piece: the stage never goes back, ends at the last stage, and the last piece leaves nothing.
            java.util.UUID ant = java.util.UUID.randomUUID();
            int stage = 0;
            for (int cut = 1; cut <= pieces; cut++) {
                colony.food().clippedGrass(level, start, ant);
                BlockState now = level.getBlockState(start);
                if (cut < pieces) {
                    int eaten = now.getValue(FallenBranchBlock.EATEN);
                    helper.assertTrue(eaten >= stage, "the branch got less eaten after cut " + cut);
                    stage = eaten;
                } else {
                    helper.assertTrue(now.isAir(), "the branch is still there after its last piece: " + now);
                }
            }
            helper.assertTrue(stage == FallenBranchBlock.MAX_EATEN, "the branch only got to stage " + stage);

            // A block gnawed half away by someone else counts as half eaten.
            BlockPos half = helper.absolutePos(new BlockPos(MIDDLE - 6, SURFACE, MIDDLE + 6));
            level.setBlock(half, ModBlocks.FALLEN_BRANCH.get().defaultBlockState().setValue(FallenBranchBlock.EATEN, 2), 3);
            colony.food().clippedGrass(level, half, ant);
            helper.assertTrue(colony.food().getPiecesCut(half) == pieces / 2 + 1, "a half-eaten branch counts "
                    + colony.food().getPiecesCut(half) + " pieces cut after one more");

            // Acacias shed branches from natural leaves, not from leaves a player placed.
            BlockPos under = helper.absolutePos(new BlockPos(MIDDLE - 6, SURFACE, MIDDLE - 6));
            for (BlockPos pos : BlockPos.betweenClosed(under.offset(-3, 4, -3), under.offset(3, 4, 3))) {
                level.setBlock(pos, Blocks.ACACIA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
            }
            helper.assertTrue(!BranchShedding.tryShed(level, under.getX(), under.getZ(), level.getRandom()), "placed leaves shed a branch");
            for (BlockPos pos : BlockPos.betweenClosed(under.offset(-3, 4, -3), under.offset(3, 4, 3))) {
                level.setBlock(pos, Blocks.ACACIA_LEAVES.defaultBlockState(), 2); // natural (distance 7, would decay: not ticked here)
            }
            boolean shed = false;
            for (int i = 0; i < 20 && !shed; i++) {
                shed = BranchShedding.tryShed(level, under.getX(), under.getZ(), level.getRandom());
            }
            helper.assertTrue(shed, "a natural acacia shed no branch");
            helper.succeed();
        });
    }

    /**
     * A fallen tree: an upright stump of dead wood, and a trunk of 4 to 6 dead wood blocks lying along the ground beside
     * it; termites count each dead wood block as a big food plant (several foragers at once), and it is eaten in stages.
     */
    @GameTest(template = "arena", skyAccess = true)
    public static void aFallenTreeLiesBesideItsStumpAndIsTermiteFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        BlockPos stump = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        helper.assertTrue(FallenTreeFeature.placeTree(level, stump, level.getRandom(), 3), "no fallen tree fitted on flat ground");
        BlockState stumpState = level.getBlockState(stump);
        helper.assertTrue(stumpState.is(ModBlocks.DEAD_WOOD.get())
                && stumpState.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) == net.minecraft.core.Direction.Axis.Y,
                "no upright stump: " + stumpState);
        int lying = 0;
        for (BlockPos pos : BlockPos.betweenClosed(stump.offset(-8, 0, -8), stump.offset(8, 0, 8))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.DEAD_WOOD.get()) && state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS).isHorizontal()) {
                lying++;
            }
        }
        helper.assertTrue(lying >= FallenTreeFeature.MIN_LENGTH && lying <= FallenTreeFeature.MAX_LENGTH, "the trunk is " + lying + " blocks long");
        SpeciesProfile.FoodNumbers food = SpeciesProfiles.get(Species.MACROTERMES).food();
        SpeciesProfile.PlantFood wood = food.plantFor(ModBlocks.DEAD_WOOD.get().defaultBlockState());
        helper.assertTrue(wood != null && wood.pieces() > food.plantFor(ModBlocks.FALLEN_BRANCH.get().defaultBlockState()).pieces()
                && wood.foragers() > 1, "dead wood is not a big termite food");
        BlockState eaten = ModBlocks.DEAD_WOOD.get().eatenTo(stumpState, wood.pieces() - 1, wood.pieces());
        helper.assertTrue(eaten.getValue(DeadWoodBlock.EATEN) == DeadWoodBlock.MAX_EATEN, "nearly eaten dead wood is at stage " + eaten);
        helper.succeed();
    }

    /** An item lying still on the ground at this spot (x, z) of the arena surface, never despawning. */
    private static ItemEntity lay(GameTestHelper helper, int[] spot, ItemStack stack) {
        BlockPos at = helper.absolutePos(new BlockPos(spot[0], SURFACE, spot[1]));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, stack);
        item.setDeltaMovement(0, 0, 0);
        item.setUnlimitedLifetime();
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    /**
     * Dry Grass: a green tuft dries step by step (green, drying, dry) on random ticks and then stays dry; a tall plant
     * dries both halves together; bone meal grows a short tuft tall, as green as it was. Termites only count it as food
     * once it is dry (green vanilla grass never). Quick: the random ticks are given by hand.
     */
    @GameTest(template = "arena")
    public static void dryGrassDriesOutAndTermitesOnlyEatItDry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = MIDDLE - 1; x <= MIDDLE + 5; x++) {
            helper.setBlock(x, SURFACE - 1, MIDDLE, Blocks.GRASS_BLOCK); // grass grows on soil
        }
        BlockState green = ModBlocks.DRY_GRASS.get().defaultBlockState().setValue(DryGrassBlock.GROWTH, DryGrassBlock.Growth.GREEN);
        BlockPos shortAt = new BlockPos(MIDDLE, SURFACE, MIDDLE);
        BlockPos tallAt = new BlockPos(MIDDLE + 2, SURFACE, MIDDLE);
        BlockPos bonemealAt = new BlockPos(MIDDLE + 4, SURFACE, MIDDLE);
        helper.setBlock(shortAt, green);
        helper.setBlock(bonemealAt, green);
        DoublePlantBlock.placeAt(level, ModBlocks.TALL_DRY_GRASS.get().defaultBlockState().setValue(DryGrassBlock.GROWTH, DryGrassBlock.Growth.GREEN),
                helper.absolutePos(tallAt), 3);

        SpeciesProfile.FoodNumbers food = SpeciesProfiles.get(Species.MACROTERMES).food();
        helper.assertTrue(food.plantFor(green) == null, "termites eat green Dry Grass");
        helper.assertTrue(food.plantFor(Blocks.SHORT_GRASS.defaultBlockState()) == null, "termites eat green vanilla grass");
        helper.assertTrue(food.plantFor(Blocks.TALL_GRASS.defaultBlockState()) == null, "termites eat green vanilla tall grass");

        // Bone meal: the green tuft grows into a green tall plant.
        BlockPos bonemeal = helper.absolutePos(bonemealAt);
        helper.assertTrue(BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, bonemeal), "bone meal did nothing");
        helper.assertTrue(level.getBlockState(bonemeal).is(ModBlocks.TALL_DRY_GRASS.get())
                && level.getBlockState(bonemeal).getValue(DryGrassBlock.GROWTH) == DryGrassBlock.Growth.GREEN
                && level.getBlockState(bonemeal.above()).getValue(DryGrassBlock.GROWTH) == DryGrassBlock.Growth.GREEN,
                "bone meal did not grow a green tall plant: " + level.getBlockState(bonemeal) + " / " + level.getBlockState(bonemeal.above()));

        RandomSource random = level.getRandom();
        for (BlockPos at : List.of(shortAt, tallAt)) {
            BlockPos pos = helper.absolutePos(at);
            List<DryGrassBlock.Growth> seen = new ArrayList<>(List.of(DryGrassBlock.Growth.GREEN));
            for (int i = 0; i < 5000 && level.getBlockState(pos).getValue(DryGrassBlock.GROWTH) != DryGrassBlock.Growth.DRY; i++) {
                BlockState state = level.getBlockState(pos);
                if (state.isRandomlyTicking()) {
                    state.randomTick(level, pos, random);
                }
                DryGrassBlock.Growth now = level.getBlockState(pos).getValue(DryGrassBlock.GROWTH);
                if (now != seen.get(seen.size() - 1)) {
                    seen.add(now);
                }
                if (at == tallAt) {
                    helper.assertTrue(level.getBlockState(pos.above()).getValue(DryGrassBlock.GROWTH) == now, "the tall plant's halves dried apart");
                }
            }
            helper.assertTrue(seen.equals(List.of(DryGrassBlock.Growth.GREEN, DryGrassBlock.Growth.DRYING, DryGrassBlock.Growth.DRY)),
                    "the grass at " + at.toShortString() + " went " + seen);
            helper.assertTrue(!level.getBlockState(pos).isRandomlyTicking(), "dry grass keeps changing");
            helper.assertTrue(food.plantFor(level.getBlockState(pos)) != null, "termites do not eat dry grass at " + at.toShortString());
        }
        helper.succeed();
    }
}
