package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.DeadWoodBlock;
import com.antcolonies.block.DryGrassBlock;
import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.block.FungusCombBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;
import com.antcolonies.worldgen.FallenBranchFeature;
import com.antcolonies.worldgen.FallenTreeFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Termites gathering food (M7 step 4): dead plant material carried into the fungus garden and built into comb, and the
 * garden itself (step 5a): nodules growing on the comb, the nurses feeding them to the queen (who lays a steady stream
 * of eggs) and to the nymphs, the eggs carried into the gardens and laid on the comb.
 */
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
     * <p>
     * The garden at work: nodules grow on the comb, the nurses feed the queen (full grown here, so she lays fast), she
     * lays eggs behind her, the nurses carry them into the garden and lay them on the comb, they hatch into nymphs, the
     * nurses feed the nymphs nodules, and a nymph grows into a new worker. All along: no egg or nymph ever lies inside
     * the comb, and every egg that a nurse put down lies in a garden room. In a batch of its own (a whole colony at work).
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true, batch = "garden")
    public static void termitesGatherDeadPlantsIntoTheirFungusGardenAndRaiseTheQueensEggsOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        StuckWatcher.watch(helper, () -> TermiteMoundTests.colony(helper)); // the termites-underground safety net (2026-10-17)
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
        int[] firstWorkers = {-1};
        boolean[] sawEggOnComb = {false};
        helper.onEachTick(() -> {
            if (colony[0] == null) {
                colony[0] = TermiteMoundTests.colony(helper);
                if (colony[0] != null) {
                    TestClock.still(helper, 0.0); // always midnight here: the foragers go out
                    for (BlockPos plant : List.of(bush, tallGrass, grassA, grassB, hay)) {
                        colony[0].foodMap().knowForTests(helper.absolutePos(plant)); // (finding them is the next test)
                    }
                }
            }
            Colony c = colony[0];
            if (c != null && !c.isFounding()) {
                if (helper.getTick() > 20 && firstWorkers[0] < 0) {
                    firstWorkers[0] = c.getCount(WorkerAntEntity.CASTE);
                }
                // Once she lies in her room she is full grown, so she lays fast.
                if (c.getQueen(level) instanceof TermiteQueenEntity queen && queen.getSize() < 1.0F && c.queenCare().isQueenHome(level)) {
                    queen.setSize(1.0F);
                }
                // No egg or young one lies inside the comb.
                for (Entity young : youngOf(level, helper, c)) {
                    BlockPos cell = young.blockPosition();
                    BlockState state = level.getBlockState(cell);
                    if (state.is(ModBlocks.FUNGUS_COMB.get())) {
                        double top = cell.getY() + FungusCombBlock.topOf(state);
                        helper.assertTrue(young.getY() >= top - 0.01, young.getType().toShortString() + " lies inside the comb at " + cell.toShortString()
                                + " (y " + young.getY() + ", comb top " + top + ")");
                        if (young instanceof AntEggEntity) {
                            sawEggOnComb[0] = true;
                        }
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
            // (Since the comb ages, 2026-10-15, a nodule no longer uses a piece up: it turns a ripe layer old. A piece
            // leaves the store when a worker eats old comb or an old layer crumbles away.)
            int stored = c.food().getFood();
            int eaten = c.food().getSeedsEaten();
            int crumbled = c.garden().getLayersCrumbled();
            helper.assertTrue(stored + eaten + crumbled + carried + lying == small + hayCut, "stored " + stored + " + eaten " + eaten
                    + " + crumbled " + crumbled + " + carried " + carried + " + lying " + lying + " is not the " + small + " small pieces + "
                    + hayCut + " cut off the hay bale (nodules grown " + c.garden().getNodulesGrown() + ")");
            helper.assertTrue(c.garden().getNodulesGrown() > 0, "no nodules grew on the comb");
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
            // The garden at work.
            helper.assertTrue(c.garden().getNodulesGrown() > 0, "no nodule has grown yet");
            helper.assertTrue(c.queenCare().getEggsLaid() > 0, "the queen has laid no egg yet (she holds " + c.queenCare().getQueenBread() + " nodules)");
            helper.assertTrue(sawEggOnComb[0], "no egg was laid on the comb yet");
            Set<BlockPos> gardenCells = new HashSet<>(c.nest().getGardenCells(level));
            for (AntEggEntity egg : c.brood().getEggs(level)) {
                helper.assertTrue(!egg.isPlaced() || gardenCells.contains(egg.blockPosition()), "an egg was put down outside the gardens at "
                        + egg.blockPosition().toShortString());
            }
            helper.assertTrue(firstWorkers[0] >= 0 && c.getCount(WorkerAntEntity.CASTE) > firstWorkers[0], "no nymph has grown into a worker yet (workers "
                    + c.getCount(WorkerAntEntity.CASTE) + ", nymphs " + c.brood().countLarvae(level) + ", eggs " + c.brood().countEggs(level) + ")");
        });
    }

    /** This colony's eggs and nymphs round the test area. */
    private static List<Entity> youngOf(ServerLevel level, GameTestHelper helper, Colony colony) {
        List<Entity> young = new ArrayList<>();
        for (AntEggEntity egg : level.getEntitiesOfClass(AntEggEntity.class, helper.getBounds().inflate(4))) {
            if (colony.getId().equals(egg.getColonyId())) {
                young.add(egg);
            }
        }
        for (AntLarvaEntity nymph : level.getEntitiesOfClass(AntLarvaEntity.class, helper.getBounds().inflate(4))) {
            if (colony.getId().equals(nymph.getColonyId())) {
                young.add(nymph);
            }
        }
        return young;
    }

    /**
     * Finding food (the colony's food map): an old colony with its nest dug out and both foraging holes open, a tuft of
     * Dry Grass right beside each hole and a hay bale 11 blocks out from the first. By day nobody knows the hay. A
     * worker is put down beside it (as the user tests it): it finds the hay and, once it is back inside, tells the
     * colony. Then night: the foragers go for the hay (worth far more) rather than the grass by the holes. Checked:
     * the hay is unknown at first, found and then known only once the finder is inside, and by the time 8 pieces are cut
     * off the hay at most half as many have been cut off the grass. Brain part 3: the brain (the foraging numbers' memory)
     * never remembers the hay before the colony knows of it, and once it does it remembers the walk to it by the nearest
     * way out, and that it lies out in the open (not to be eaten from inside by day).
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
            TestClock.still(helper, 12.0); // midday: nobody goes out
            long old = level.getGameTime() - colony.profile().worker().builderUntilTicks() - 1000;
            for (com.antcolonies.entity.TermiteWorkerEntity worker : level.getEntitiesOfClass(com.antcolonies.entity.TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(old); // old workers: foragers first
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            level.setBlock(colony.nest().getEntrance(), Blocks.STONE.defaultBlockState(), 3);
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            helper.assertTrue(tunnels.size() == 2, "the nest has " + tunnels.size() + " foraging tunnels");
            // The hay lies out from the first hole at a right angle to its tunnel, toward the open middle of the arena.
            net.minecraft.core.Direction toHay = TermiteSoldierTests.intoArena(tunnels.get(0).side().getClockWise());
            for (int i = 0; i < 2; i++) {
                NestPlan.ForagingTunnel tunnel = tunnels.get(i);
                level.setBlock(tunnel.hole(), Blocks.AIR.defaultBlockState(), 3);
                // A tuft of grass right beside the hole, on the side away from the hay.
                spots[1 + i] = tunnel.hole().above().relative(toHay.getOpposite(), 2);
                level.setBlock(spots[1 + i], ModBlocks.DRY_GRASS.get().defaultBlockState(), 3);
            }
            spots[0] = tunnels.get(0).hole().above().relative(toHay, 11);
            level.setBlock(spots[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            // (The colony looks for food plants every 5 seconds: the finder walked home before the hay was on the list,
            // and the test failed now and then in full runs, 2026-10-14.)
            colony.food().rescanPlantsSoon();
            helper.assertTrue(!colony.foodMap().isKnown(spots[0]) && !colony.foodMap().isFoundButUntold(spots[0]),
                    "the colony knew of the hay before anyone went near it");
            // (Put down now, while the holes are still open: by day they are plugged once everybody is in.)
            BlockPos beside = spots[0].relative(tunnels.get(0).side().getOpposite(), 2); // (toward the middle: over the ground)
            finder[0] = ModEntities.TERMITE_WORKER.get().create(level);
            finder[0].moveTo(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5, 0.0F, 0.0F);
            finder[0].joinColony(colony); // (on its own it joins the nearest colony, which can be a neighbouring test's)
            level.addFreshEntity(finder[0]);
            colony.field().setOnForTests(true); // (brain part 3: the brain looks at the food; the colony has no numbers to forage)
            phase[0] = 1;
        });
        boolean[] sawUntold = {false};
        long[] knownAt = {-1};
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
                // Brain part 3: the brain never remembers food nobody has found and told of.
                helper.assertTrue(colony.foodMap().isKnown(spots[0]) || !colony.field().remembers(spots[0]),
                        "the brain remembers the hay before the colony knows of it");
                if (colony.foodMap().isFoundButUntold(spots[0])) {
                    sawUntold[0] = true;
                    helper.assertTrue(!colony.foodMap().isKnown(spots[0]), "the hay is known before its finder is back inside");
                }
                if (colony.foodMap().isKnown(spots[0])) {
                    helper.assertTrue(sawUntold[0], "the colony knew of the hay without anyone finding it first");
                    helper.assertTrue(colony.nest().isInside(level, finder[0].blockPosition()), "the hay is known while its finder is still outside");
                    TestClock.still(helper, 0.0); // midnight: the foragers go out
                    knownAt[0] = helper.getTick();
                    phase[0] = 2;
                    AntColoniesMod.LOGGER.info("[find-test] tick {}: the hay is known, night", helper.getTick());
                }
            } else if (knownAt[0] >= 0 && helper.getTick() == knownAt[0] + 140) {
                // Brain part 3: once known, the brain remembers the hay (its look comes every 5 seconds) with the walk
                // there and back to the nest's middle by the nearest way out: at most the 11 blocks out from the first
                // hole and from that hole to the middle, at least the straight line.
                BlockPos hole = colony.nest().getForagingTunnels(level).get(0).hole();
                BlockPos center = colony.getCenter();
                double viaHole = flat(spots[0], hole) + flat(hole, center);
                float walk = colony.field().walkTo(spots[0]);
                helper.assertTrue(colony.field().remembers(spots[0]) && walk <= viaHole + 0.01 && walk >= flat(spots[0], center) - 0.01,
                        "the brain should remember the hay with its walk: " + walk + " (by the first hole " + viaHole + ", straight "
                                + flat(spots[0], center) + ")");
                helper.assertTrue(!colony.field().remembersFromInside(spots[0]), "hay out in the open cannot be eaten from inside");
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
            // Brain step 4a: the colony remembers a trail to the hay (strengthened by every visit), saved with the
            // colony, and fading once unused: a week of game days later it is forgotten.
            List<com.antcolonies.colony.ForagingTrails.Remembered> remembered = colony.trails().remembered(level);
            helper.assertTrue(remembered.stream().anyMatch(trail -> flat(trail.patch(), spots[0]) <= 4.0) && remembered.get(0).strength() >= 4.0,
                    "a remembered trail should lead to the hay: " + colony.trails().describeMemory(level));
            List<com.antcolonies.colony.ForagingTrails.Remembered> loaded = Colony.load(colony.save()).trails().remembered(level);
            helper.assertTrue(loaded.size() == remembered.size() && loaded.get(0).patch().equals(remembered.get(0).patch())
                    && Math.abs(loaded.get(0).strength() - remembered.get(0).strength()) < 0.01,
                    "the remembered trails should be saved: " + loaded + " vs " + remembered);
            colony.trails().ageMemoryForTests(7 * 24000L);
            helper.assertTrue(colony.trails().remembered(level).isEmpty(), "a week unused the trails should be forgotten: "
                    + colony.trails().describeMemory(level));
        });
    }

    /** The distance (blocks) between two spots, sideways. */
    private static double flat(BlockPos a, BlockPos b) {
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getZ() - b.getZ(), 2));
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

            // (Shedding moved to the giant trees in M7.5 step 6b: checked in TreeGrowthTests.)
            giantTreesDeadWood(helper, colony);
        });
    }

    /**
     * M7.5 step 6d: the dead giant trees' wood is termite food like the old Dead Wood: a fallen branch under a crown of
     * leaves is seen, a dead trunk standing taller than the food scan's stack is seen at its foot; a log block is worth
     * as much as the old Dead Wood, soft rot less; it is eaten in stages, the last piece leaving it gone, and wood the
     * termites started on is no longer the tree's own (the tree book leaves it alone).
     */
    private static void giantTreesDeadWood(GameTestHelper helper, Colony colony) {
        ServerLevel level = helper.getLevel();
        BlockPos shaded = helper.absolutePos(new BlockPos(MIDDLE + 6, SURFACE, MIDDLE + 6));
        level.setBlock(shaded, ModBlocks.FALLEN_BRANCH.get().defaultBlockState(), 3);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(shaded.offset(dx, 5, dz), com.antcolonies.registry.TreeBlocks.UMBRELLA_THORN.leaves().get().defaultBlockState(), 3);
            }
        }
        BlockPos snag = helper.absolutePos(new BlockPos(MIDDLE - 6, SURFACE, MIDDLE - 6));
        for (int y = 0; y < 9; y++) {
            level.setBlock(snag.above(y), com.antcolonies.registry.TreeBlocks.BLEACHED_LOG.get().defaultBlockState(), 3);
        }
        colony.food().rescanPlantsSoon();
        java.util.List<BlockPos> plants = colony.food().plants(level);
        helper.assertTrue(plants.contains(shaded), "a fallen branch under a crown of leaves is not seen as food");
        helper.assertTrue(plants.contains(snag), "a dead trunk 9 tall is not seen at its foot");
        BlockState log = com.antcolonies.registry.TreeBlocks.BLEACHED_LOG.get().defaultBlockState();
        int logPieces = colony.food().piecesOf(log), deadWood = colony.food().piecesOf(ModBlocks.DEAD_WOOD.get().defaultBlockState());
        int rot = colony.food().piecesOf(com.antcolonies.registry.TreeBlocks.ROTTEN_WOOD.get().defaultBlockState());
        helper.assertTrue(logPieces == deadWood && colony.food().piecesOf(com.antcolonies.registry.TreeBlocks.BLEACHED_WOOD.get().defaultBlockState()) == deadWood
                && rot > 0 && rot < logPieces && colony.food().piecesOf(com.antcolonies.registry.TreeBlocks.BLEACHED_BRANCH.get().defaultBlockState()) > 0,
                "the dead trees' wood is worth " + logPieces + " (old Dead Wood " + deadWood + "), rot " + rot);
        // The snag's top block eaten piece by piece (nothing of a nest beside it: it goes, no cast).
        BlockPos top = snag.above(8);
        java.util.UUID ant = java.util.UUID.randomUUID();
        int stage = 0;
        for (int cut = 1; cut <= logPieces; cut++) {
            colony.food().clippedGrass(level, top, ant);
            BlockState now = level.getBlockState(top);
            if (cut < logPieces) {
                stage = Math.max(stage, now.getValue(com.antcolonies.block.DeadWoodBlock.EATEN));
                helper.assertTrue(!com.antcolonies.worldgen.TreePlacer.same(now, log) || stage == 0, "eaten wood is still the tree's own");
            } else {
                helper.assertTrue(!now.is(log.getBlock()), "the dead trunk's block is still there after its last piece: " + now);
            }
        }
        helper.assertTrue(stage == com.antcolonies.block.DeadWoodBlock.MAX_EATEN, "the dead trunk's block only got to stage " + stage);
        helper.succeed();
    }

    /**
     * Mud casts and crusts once nobody needs them: two casts joined side by side with dead wood beside one of them stay
     * (the log is still being eaten); once the wood is gone they crumble away. A plate of mud on top of a log stays while
     * the wood is there and crumbles once the wood under it is a cast. Quick: random ticks are given by hand.
     */
    @GameTest(template = "arena", skyAccess = true)
    public static void mudCastsAndCrustsCrumbleOnceTheWoodIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        BlockPos first = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        BlockPos second = first.east();
        BlockPos wood = second.east();
        BlockState cast = ModBlocks.MUD_CAST.get().defaultBlockState();
        level.setBlock(first, cast, 3);
        level.setBlock(second, cast, 3);
        level.setBlock(wood, ModBlocks.DEAD_WOOD.get().defaultBlockState(), 3);
        level.setBlock(wood.above(), ModBlocks.MUD_CRUST.get().withFace(Blocks.AIR.defaultBlockState(), net.minecraft.core.Direction.DOWN), 3);
        helper.assertTrue(level.getBlockState(first).getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(net.minecraft.core.Direction.EAST)),
                "two casts side by side do not join up: " + level.getBlockState(first));
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 400; i++) {
            for (BlockPos pos : List.of(first, second, wood.above())) {
                level.getBlockState(pos).randomTick(level, pos, random);
            }
        }
        helper.assertTrue(com.antcolonies.block.MudCastBlock.isCast(level.getBlockState(first)) && com.antcolonies.block.MudCastBlock.isCast(level.getBlockState(second)),
                "a cast crumbled while the log beside it is still being eaten");
        helper.assertTrue(com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(wood.above()), net.minecraft.core.Direction.DOWN),
                "the mud on top of the log crumbled while the wood is still there");
        level.setBlock(wood, cast, 3); // the last block eaten too
        for (int i = 0; i < 400; i++) {
            for (BlockPos pos : List.of(first, second, wood, wood.above())) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    state.randomTick(level, pos, random);
                }
            }
        }
        helper.assertTrue(level.getBlockState(first).isAir() && level.getBlockState(second).isAir() && level.getBlockState(wood).isAir(),
                "the casts did not crumble once the wood was gone");
        helper.assertTrue(level.getBlockState(wood.above()).isAir(), "the mud on top of the log did not crumble once the wood was a cast");
        helper.succeed();
    }

    /**
     * The user's bug (2026-10-14): termites eating a branch from outside at night were cased in with it (a plate of mud
     * put down in the spot they stood in), could plan no route from inside the plate's block and were shut out by day.
     * Now a plate put down where a nestmate stands nudges it out first, to an open spot away from the plate; and a
     * termite already inside a plate's block (put there by hand here) still finds a route out from the spots beside it.
     */
    @GameTest(template = "arena", skyAccess = true)
    public static void aPlateOfMudIsNeverPutDownOnATermite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE + 4, SURFACE, MIDDLE);
            helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 4, SURFACE, MIDDLE);
        });
        helper.runAfterDelay(15, () -> {
            com.antcolonies.colony.Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            List<com.antcolonies.entity.TermiteWorkerEntity> workers = level.getEntitiesOfClass(com.antcolonies.entity.TermiteWorkerEntity.class,
                    helper.getBounds(), worker -> colony.getId().equals(worker.getColonyId()));
            helper.assertTrue(workers.size() == 2, workers.size() + " workers joined the colony, not 2");
            com.antcolonies.entity.TermiteWorkerEntity first = workers.get(0);
            BlockPos cell = first.blockPosition();
            first.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
            helper.assertTrue(colony.walls().plate(level, cell, net.minecraft.core.Direction.SOUTH), "the plate was not put down");
            helper.assertTrue(com.antcolonies.block.MudCrustBlock.hasFace(level.getBlockState(cell), net.minecraft.core.Direction.SOUTH),
                    "no plate in the cell");
            helper.assertTrue(!first.blockPosition().equals(cell) && first.blockPosition().distManhattan(cell) == 1,
                    "the termite was not nudged out beside the plate: at " + first.blockPosition().toShortString());
            helper.assertTrue(!first.blockPosition().equals(cell.south()), "the termite was nudged through the plate");
            // A termite already inside a plate's block plans its way out.
            com.antcolonies.entity.TermiteWorkerEntity second = workers.get(1);
            BlockPos caught = second.blockPosition();
            second.moveTo(caught.getX() + 0.5, caught.getY(), caught.getZ() + 0.5);
            level.setBlock(caught, ModBlocks.MUD_CRUST.get().withFace(Blocks.AIR.defaultBlockState(), net.minecraft.core.Direction.SOUTH), 3);
            helper.assertTrue(second.getNavigator().moveTo(caught.north(4), 1.0), "a termite inside a plate's block found no route out");
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

    /**
     * Dead termites leave bodies (user's decision; the user saw none in the game, 2026-10-14): a worker killed by a
     * player and a minor soldier killed by a player each leave a termite body where they died, once the death animation
     * is over.
     */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aTermiteKilledByAPlayerLeavesABody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        com.antcolonies.entity.TermiteWorkerEntity[] worker = {null};
        com.antcolonies.entity.TermiteSoldierEntity[] minor = {null};
        com.antcolonies.entity.TermiteWorkerEntity[] loner = {null};
        java.util.UUID[] colonyId = {null};
        helper.runAfterDelay(5, () -> {
            worker[0] = helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE + 5, SURFACE, MIDDLE);
            minor[0] = helper.spawn(ModEntities.TERMITE_MINOR_SOLDIER.get(), MIDDLE - 5, SURFACE, MIDDLE);
            loner[0] = helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE, SURFACE, MIDDLE + 5);
            loner[0].keepOutOfColonies(); // a worker of no colony leaves a body too
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(worker[0].getColonyId() != null, "the worker joined no colony");
            helper.assertTrue(minor[0].getColonyId() != null, "the soldier joined no colony");
            net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.world.entity.player.Player creative = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            colonyId[0] = worker[0].getColonyId();
            worker[0].hurt(level.damageSources().playerAttack(creative), 100.0F);
            minor[0].hurt(level.damageSources().playerAttack(player), 100.0F);
            loner[0].hurt(level.damageSources().playerAttack(creative), 100.0F);
        });
        helper.runAfterDelay(80, () -> {
            for (net.minecraft.world.entity.LivingEntity dead : List.<net.minecraft.world.entity.LivingEntity>of(worker[0], minor[0], loner[0])) {
                helper.assertTrue(dead.isRemoved(), "the " + dead.getType().getDescription().getString() + " is not dead");
                List<com.antcolonies.entity.AntCorpseEntity> bodies = level.getEntitiesOfClass(com.antcolonies.entity.AntCorpseEntity.class,
                        new net.minecraft.world.phys.AABB(dead.blockPosition()).inflate(2), body -> body.getType() == ModEntities.TERMITE_CORPSE.get());
                helper.assertTrue(!bodies.isEmpty(), "no termite body where the " + dead.getType().getDescription().getString() + " died");
            }
            // Brain step 4g: the colony remembers both deaths, by a player, as two dangerous places (10 blocks apart).
            com.antcolonies.colony.Colony colony = com.antcolonies.colony.ColonyManager.get(level).getColony(colonyId[0]);
            helper.assertTrue(colony != null && colony.danger().places().size() == 2
                    && colony.danger().places().stream().allMatch(place -> place.deaths() == 1 && place.what().contains("player")),
                    "the two deaths should be remembered: " + (colony == null ? "no colony" : colony.danger().describe(level)));
            helper.succeed();
        });
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

    /**
     * Big dead wood calls (the user's report, 2026-10-19: termites never found a standing dead giant 15 to 30 blocks out;
     * their pick: big dead wood is sensed from afar, as real termites smell and feel rotting wood through the soil): a
     * colony with its galleries grown as long as its workers allow, which has never seen it, senses a dead trunk 3
     * thick, 10 tall, 10 to 15 blocks from its nearest hole (at midday, nobody outside), and a gallery piece with its
     * feeding gallery goes out to its foot past the length allowed (the user's pick, 2026-10-19: a big find gets the
     * diggers; before, the colony sensed it and waited for ever), or from a hole near it a runway of mud. A fallen dead
     * stem of 6 blocks, too small to call, is not sensed.
     */
    @GameTest(template = "arena", timeoutTicks = 16000, skyAccess = true)
    public static void aStandingDeadTreeIsSensedAndGetsAGalleryToItsFoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int mid = 32;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), mid, SURFACE, mid);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), mid - 2 + i % 4, SURFACE, mid - 3 - i / 4);
            }
        });
        TestClock.still(helper, 12); // midday: nobody goes out, so nothing is found by looking
        List<BlockPos> foot = new ArrayList<>();
        List<BlockPos> stem = new ArrayList<>();
        int[] stage = {0};
        long[] placedAt = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long now = level.getGameTime();
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - colony.profile().worker().builderUntilTicks() - 1000, 500);
            colony.nest().galleries().setSwitchForTests(true);
            stage[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || stage[0] == 0) {
                return;
            }
            com.antcolonies.colony.Galleries galleries = colony.nest().galleries();
            for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                for (BlockPos cell : galleries.digCells(tunnel)) {
                    if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                        level.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            long tick = helper.getTick();
            if (stage[0] == 1 && tick >= 1000 && tick % 100 == 0 && galleries.length() >= galleries.lengthAllowed()) {
                // The galleries as long as the workers allow (the user's world: 500 workers, the tips all grown), then
                // the dead giant: bleached log 3 thick and 10 tall, 10 to 15 blocks from the nearest hole (too far for a
                // runway, so a gallery piece past the length allowed: a big find gets the diggers).
                List<BlockPos> holes = colony.foragingHoles().tunnels(level).stream().map(NestPlan.ForagingTunnel::hole).toList();
                BlockPos spot = null;
                for (int x = 3; x <= 61 && spot == null; x++) {
                    for (int z = 3; z <= 61 && spot == null; z++) {
                        BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE, z));
                        double near = Double.MAX_VALUE;
                        for (BlockPos hole : holes) {
                            near = Math.min(near, Math.sqrt(Math.pow(hole.getX() - at.getX(), 2) + Math.pow(hole.getZ() - at.getZ(), 2)));
                        }
                        if (near >= 10 && near <= 15 && Math.abs(x - (mid - 21)) + Math.abs(z - (mid - 10)) > 8) {
                            spot = new BlockPos(x, SURFACE, z);
                        }
                    }
                }
                helper.assertTrue(spot != null, "no spot 10 to 15 blocks from the nearest hole:" + galleries.describe(level));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int y = 0; y < 10; y++) {
                            BlockPos at = helper.absolutePos(spot.offset(dx, y, dz));
                            level.setBlock(at, com.antcolonies.registry.TreeBlocks.BLEACHED_LOG.get().defaultBlockState(), 3);
                            if (y == 0) {
                                foot.add(at);
                            }
                        }
                    }
                }
                placedAt[0] = tick;
                // A fallen dead stem, 6 blocks: too small to be sensed from afar.
                for (int dx = 0; dx < 6; dx++) {
                    BlockPos at = helper.absolutePos(new BlockPos(mid - 24 + dx, SURFACE, mid - 10));
                    level.setBlock(at, com.antcolonies.registry.TreeBlocks.BLEACHED_LOG.get().defaultBlockState()
                            .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X), 3);
                    stem.add(at);
                }
                helper.assertTrue(foot.stream().noneMatch(colony.foodMap()::isKnown), "the colony knew the dead tree before sensing it");
                stage[0] = 2;
            }
            if (stage[0] != 2) {
                helper.assertTrue(tick < 8000, "the galleries never grew as long as the workers allow: " + galleries.length() + " of "
                        + galleries.lengthAllowed());
                return;
            }
            helper.assertTrue(stem.stream().noneMatch(colony.foodMap()::isKnown), "the small fallen stem was sensed from afar");
            boolean way = colony.nest().foodWays().getNewTunnels().stream()
                    .anyMatch(tunnel -> foot.contains(tunnel.food()) && tunnel.gallery() != null && !tunnel.gallery().isEmpty())
                    || colony.nest().foodWays().getRunways().stream().anyMatch(runway -> foot.contains(runway.food()));
            if (way) {
                helper.succeed();
                return;
            }
            helper.assertTrue(tick < placedAt[0] + 5000 && tick < 15900, "no way out to the dead tree's foot:" + galleries.describe(level)
                    + colony.foragingHoles().describeNewHoles(level) + ", sensed " + foot.stream().filter(colony.foodMap()::isKnown).count()
                    + ", foot seen " + foot.stream().filter(colony.food().plants(level)::contains).count()
                    + ", food waiting " + colony.foragingHoles().isFoodWaiting() + colony.foragingHoles().whyNoRunway(level, foot.get(0)));
        });
    }
}
