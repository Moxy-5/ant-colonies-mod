package com.antcolonies.gametest;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteHoleBlock;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteMouthBlock;
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
     * The cathedral pieces placed by hand, as a player does: spire pieces stacked in a column get thinner one by one up
     * to the tip; two ramps meeting at a right angle join into a corner like stairs; a chimney mouth alone on a spire
     * is slim and becomes a full block when soil is put beside it, and termites pass through it while a player cannot;
     * the pieces dry with age like mound soil.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void cathedralPiecesArePlacedByHand(GameTestHelper helper) {
        ground(helper);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.item.ItemStack spires = new net.minecraft.world.item.ItemStack(com.antcolonies.registry.ModItems.TERMITE_SPIRE.get(), 64);
        // A spire: five pieces in a column narrow to the tip.
        for (int y = 0; y < 6; y++) {
            place(helper, player, spires, new BlockPos(1, y, 1));
        }
        for (int y = 1; y <= 6; y++) {
            BlockState piece = helper.getBlockState(new BlockPos(1, y, 1));
            helper.assertTrue(piece.is(ModBlocks.TERMITE_SPIRE.get()), "no spire piece at height " + y);
            int expected = Math.min(com.antcolonies.block.TermiteSpireBlock.TIP, y - 1);
            helper.assertTrue(piece.getValue(com.antcolonies.block.TermiteSpireBlock.THICKNESS) == expected,
                    "spire piece " + y + " has thickness " + piece.getValue(com.antcolonies.block.TermiteSpireBlock.THICKNESS) + ", not " + expected);
        }
        // Two ramps: one rising east, and ahead of it one rising north: the first becomes an outer corner.
        net.minecraft.world.item.ItemStack ramps = new net.minecraft.world.item.ItemStack(com.antcolonies.registry.ModItems.TERMITE_RAMP.get(), 64);
        player.setYRot(-90.0F); // looking east
        place(helper, player, ramps, new BlockPos(3, 0, 3));
        player.setYRot(180.0F); // looking north
        place(helper, player, ramps, new BlockPos(4, 0, 3));
        BlockState first = helper.getBlockState(new BlockPos(3, 1, 3));
        BlockState second = helper.getBlockState(new BlockPos(4, 1, 3));
        helper.assertTrue(first.is(ModBlocks.TERMITE_RAMP.get()) && second.is(ModBlocks.TERMITE_RAMP.get()), "the ramps were not placed");
        helper.assertTrue(first.getValue(com.antcolonies.block.TermiteRampBlock.FACING) == net.minecraft.core.Direction.EAST
                && second.getValue(com.antcolonies.block.TermiteRampBlock.FACING) == net.minecraft.core.Direction.NORTH,
                "the ramps do not rise the way the player looked");
        helper.assertTrue(first.getValue(com.antcolonies.block.TermiteRampBlock.SHAPE) == net.minecraft.world.level.block.state.properties.StairsShape.OUTER_LEFT,
                "the ramps did not join into a corner (" + first.getValue(com.antcolonies.block.TermiteRampBlock.SHAPE) + ")");
        // A chimney mouth on top of a spire piece: slim; with soil beside it, a full block.
        helper.setBlock(new BlockPos(4, 1, 1), ModBlocks.TERMITE_SPIRE.get());
        place(helper, player, new net.minecraft.world.item.ItemStack(com.antcolonies.registry.ModItems.TERMITE_MOUTH.get()), new BlockPos(4, 1, 1));
        BlockPos mouth = new BlockPos(4, 2, 1);
        helper.assertTrue(helper.getBlockState(mouth).is(ModBlocks.TERMITE_MOUTH.get()) && helper.getBlockState(mouth).getValue(TermiteMouthBlock.SLIM),
                "a chimney mouth alone on a spire is not slim");
        helper.setBlock(new BlockPos(5, 2, 1), ModBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED));
        helper.assertTrue(!helper.getBlockState(mouth).getValue(TermiteMouthBlock.SLIM), "a chimney mouth with soil beside it is still slim");
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 0, 1, 0);
        BlockPos abs = helper.absolutePos(mouth);
        helper.assertTrue(helper.getBlockState(mouth).getCollisionShape(helper.getLevel(), abs, CollisionContext.of(termite)).isEmpty(),
                "a termite cannot pass through the chimney mouth");
        helper.assertTrue(!helper.getBlockState(mouth).getCollisionShape(helper.getLevel(), abs, CollisionContext.of(player)).isEmpty(),
                "a player can pass through the chimney mouth");
        // Ageing: random ticks dry a spire piece through the ages to old.
        BlockPos aged = new BlockPos(1, 1, 1);
        for (int i = 0; i < 20000 && helper.getBlockState(aged).getValue(com.antcolonies.block.MoundSoil.AGE) < com.antcolonies.block.MoundSoil.OLDEST; i++) {
            helper.randomTick(aged);
        }
        helper.assertTrue(helper.getBlockState(aged).getValue(com.antcolonies.block.MoundSoil.AGE) == com.antcolonies.block.MoundSoil.OLDEST,
                "a spire piece did not dry to old");
        helper.succeed();
    }

    /**
     * Tunnel blocks placed by hand join up on their own: sides next to another tunnel block open, sides against solid
     * blocks stay closed, and a side toward open air keeps what it was (a shovel opens and closes a window there; soil
     * put against it closes it; a neighbour broken away leaves its side open). An open tunnel lets a little light in,
     * it is still solid for termites (they learn to crawl through later), and it dries with age.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void tunnelBlocksJoinUpOnTheirOwn(GameTestHelper helper) {
        ground(helper);
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.item.ItemStack tunnels = new net.minecraft.world.item.ItemStack(com.antcolonies.registry.ModItems.TERMITE_TUNNEL.get(), 64);
        BlockPos a = new BlockPos(3, 1, 3);
        BlockPos b = a.east();
        BlockPos c = a.above();
        place(helper, player, tunnels, a.below());
        place(helper, player, tunnels, b.below());
        place(helper, player, tunnels, a);
        helper.assertTrue(helper.getBlockState(a).is(ModBlocks.TERMITE_TUNNEL.get()) && helper.getBlockState(b).is(ModBlocks.TERMITE_TUNNEL.get())
                && helper.getBlockState(c).is(ModBlocks.TERMITE_TUNNEL.get()), "the tunnel blocks were not placed");
        assertOpen(helper, a, "a tunnel with one beside it and one on top", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        assertOpen(helper, b, "the tunnel beside it", net.minecraft.core.Direction.WEST);
        assertOpen(helper, c, "the tunnel on top", net.minecraft.core.Direction.DOWN);
        // A shovel opens a window toward open air, and closes it again; not toward the ground.
        net.minecraft.world.item.ItemStack shovel = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_SHOVEL);
        useShovel(helper, player, shovel, a, net.minecraft.core.Direction.NORTH);
        assertOpen(helper, a, "after the shovel on its north side", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH);
        useShovel(helper, player, shovel, a, net.minecraft.core.Direction.DOWN);
        assertOpen(helper, a, "after the shovel on the side against the ground", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH);
        useShovel(helper, player, shovel, a, net.minecraft.core.Direction.NORTH);
        assertOpen(helper, a, "after the shovel closed the window", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        // A window with soil put against it closes, and stays closed when the soil is taken away.
        useShovel(helper, player, shovel, a, net.minecraft.core.Direction.NORTH);
        helper.setBlock(a.north(), Blocks.DIRT);
        assertOpen(helper, a, "with soil against its window", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        helper.setBlock(a.north(), Blocks.AIR);
        assertOpen(helper, a, "after the soil was taken away", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        // A neighbour broken away leaves the side open (a broken-into tunnel).
        helper.setBlock(b, Blocks.AIR);
        assertOpen(helper, a, "after the tunnel beside it was broken", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        BlockPos absA = helper.absolutePos(a);
        helper.assertTrue(helper.getBlockState(a).getLightBlock(level, absA) < level.getMaxLightLevel(), "no light gets into an open tunnel");
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 0, 1, 0);
        helper.assertTrue(!helper.getBlockState(a).getCollisionShape(level, absA, CollisionContext.of(termite)).isEmpty(),
                "a termite can pass through a tunnel block already");
        for (int i = 0; i < 20000 && helper.getBlockState(a).getValue(com.antcolonies.block.MoundSoil.AGE) < com.antcolonies.block.MoundSoil.OLDEST; i++) {
            helper.randomTick(a);
        }
        helper.assertTrue(helper.getBlockState(a).getValue(com.antcolonies.block.MoundSoil.AGE) == com.antcolonies.block.MoundSoil.OLDEST,
                "a tunnel block did not dry to old");
        assertOpen(helper, a, "after it dried", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        helper.succeed();
    }

    /** The tunnel block at {@code pos} opens out of exactly these sides. */
    private static void assertOpen(GameTestHelper helper, BlockPos pos, String what, net.minecraft.core.Direction... open) {
        BlockState state = helper.getBlockState(pos);
        java.util.Set<net.minecraft.core.Direction> expected = java.util.Set.of(open);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            boolean isOpen = state.getValue(com.antcolonies.block.TermiteTunnelBlock.OPEN.get(side));
            helper.assertTrue(isOpen == expected.contains(side), what + ": its " + side + " side is " + (isOpen ? "open" : "closed"));
        }
    }

    /** The player uses a shovel on one side of a block. */
    private static void useShovel(GameTestHelper helper, net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack shovel,
            BlockPos pos, net.minecraft.core.Direction side) {
        BlockPos abs = helper.absolutePos(pos);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, shovel);
        net.minecraft.world.phys.Vec3 hit = net.minecraft.world.phys.Vec3.atCenterOf(abs).relative(side, 0.5);
        helper.getBlockState(pos).useItemOn(shovel, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(hit, side, abs, false));
    }

    /** The player places the item on top of the block at {@code below}, holding it in its hand. */
    private static void place(GameTestHelper helper, net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack,
            BlockPos below) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        helper.placeAt(player, stack, below, net.minecraft.core.Direction.UP);
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
     * A termite colony with workers builds the soil of its mound: the first stage (a low dome round the open entrance),
     * then the second. The soil comes from digging (the nest, then the galleries). Wet soil is set at once here, as if
     * it had dried (it takes about 20 minutes in the game). Termites do not shape the cathedral's spire pieces, mouths
     * and ramps yet (that comes with the tunnel block), so only the soil and the hollow entrance are checked.
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
            colony.setForagingTimeForTests(true); // always night here: the whole mound may be worked on from outside
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
            helper.assertTrue(level.getBlockState(colony.getCenter()).isAir(), "the entrance is not open");
        });
    }

    /** The soil of the colony's mound at its stage now is there: walls finished, hollows open (pieces are not built yet). */
    private static boolean isBuilt(ServerLevel level, Colony colony) {
        for (var entry : colony.mound().getParts(level).entrySet()) {
            BlockState state = level.getBlockState(entry.getKey());
            boolean ok = switch (entry.getValue()) {
                case WALL -> state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state);
                case OPEN -> state.isAir();
                case MOUTH, SPIRE, RAMP -> true;
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
     * the spire until its head is out, a look round (it holds still the whole time, only its head moves, see
     * DoorwayMotion), then over the rim and down onto the mound. Then it goes back in by the mouth and down the shaft,
     * without looking round. Checked: it came up through the middle of the spire to the rim, it looked round once for
     * the whole look round with its head out and without moving, and it ends where it was sent.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void aTermiteComesOutOfTheChimneyMouthAndGoesBackIn(GameTestHelper helper) {
        comeOutOfTheMouthAndGoBackIn(helper, ModBlocks.TERMITE_HOLE.get().defaultBlockState());
    }

    /**
     * The same with the cathedral mound's chimney mouth (a tube on top of the block, see TermiteMouthBlock) on top of
     * the shaft: the termite comes up the tube, looks round, climbs over its rim and down it, and goes back in by it.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void aTermiteComesOutOfTheSpireMouthAndGoesBackIn(GameTestHelper helper) {
        comeOutOfTheMouthAndGoBackIn(helper, ModBlocks.TERMITE_MOUTH.get().defaultBlockState().setValue(TermiteMouthBlock.SLIM, false));
    }

    private static void comeOutOfTheMouthAndGoBackIn(GameTestHelper helper, BlockState doorway) {
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
        helper.setBlock(new BlockPos(2, 3, 2), doorway);
        BlockPos hole = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos bottom = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos onTop = helper.absolutePos(new BlockPos(3, 4, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 2, 1, 2);
        double rim = hole.getY() + ((com.antcolonies.nav.Doorway) doorway.getBlock()).mouthHeight();
        int[] phase = {0};
        int[] lookingOut = {0};
        double[] highest = {0};
        net.minecraft.world.phys.Vec3[] peekedAt = {null};
        helper.runAfterDelay(10, () -> helper.assertTrue(termite.getNavigator().moveTo(onTop, 1.0), "no route out of the chimney"));
        helper.onEachTick(() -> {
            // In the spire: near its middle (the hitbox is a little off it, so the drawn body is in the middle).
            boolean inMouth = Math.abs(termite.getX() - (hole.getX() + 0.5)) < 0.2 && Math.abs(termite.getZ() - (hole.getZ() + 0.5)) < 0.2;
            if (inMouth) {
                highest[0] = Math.max(highest[0], termite.getY());
            }
            if (termite.isPeeking()) {
                helper.assertTrue(phase[0] == 0, "the termite looked round on its way in");
                helper.assertTrue(inMouth && Math.abs(termite.getY() - (rim - com.antcolonies.nav.DoorwayMotion.PEEK_DEPTH)) < 0.02,
                        "the termite looks round from " + (termite.getY() - rim) + " below the rim, not with its head just out of the mouth");
                if (peekedAt[0] == null) {
                    peekedAt[0] = termite.position();
                }
                helper.assertTrue(termite.position().distanceTo(peekedAt[0]) < 0.001, "the termite moved while it looked round");
                lookingOut[0]++;
            }
            if (phase[0] == 0 && helper.getTick() > 20 && termite.getNavigator().isDone() && termite.blockPosition().equals(onTop)) {
                helper.assertTrue(highest[0] >= rim, "the termite did not come up through the spire to its rim (highest " + (highest[0] - hole.getY()) + ")");
                helper.assertTrue(Math.abs(lookingOut[0] - com.antcolonies.nav.DoorwayMotion.PEEK_TICKS) <= 1,
                        "the termite looked round for " + lookingOut[0] + " ticks, not the whole look round");
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

    /**
     * A termite on a walk that keeps away from chimney mouths (most walks, see the profile's chimney_trip_chance) leaves
     * a chimney by another way when there is one (an opening at the foot of the chimney), without looking out of the
     * mouth. With that way closed it goes in by the mouth and comes out of it again, looking round: the mouth is still
     * used when there is no other way.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aTermiteOnlyUsesTheChimneyMouthWhenThereIsNoOtherWay(GameTestHelper helper) {
        ground(helper);
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
        BlockPos side = new BlockPos(3, 1, 2);
        helper.setBlock(side, Blocks.AIR); // a way out at the foot of the chimney
        BlockPos bottom = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos onTop = helper.absolutePos(new BlockPos(3, 4, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), 2, 1, 2);
        termite.setWalkHabitsForTests(com.antcolonies.nav.Doorway.avoidMouths(helper.getLevel()));
        int[] phase = {0};
        int[] peeked = new int[3];
        helper.runAfterDelay(10, () -> helper.assertTrue(termite.getNavigator().moveTo(onTop, 1.0), "no route out"));
        helper.onEachTick(() -> {
            if (helper.getTick() <= 10 || phase[0] > 2) {
                return;
            }
            if (termite.isPeeking()) {
                peeked[phase[0]]++;
            }
            if (!termite.getNavigator().isDone()) {
                return;
            }
            if (phase[0] == 0 && termite.blockPosition().equals(onTop)) {
                helper.assertTrue(peeked[0] == 0, "the termite came out of the chimney's mouth although there was another way");
                helper.setBlock(side, soil); // the other way is closed
                helper.assertTrue(termite.getNavigator().moveTo(bottom, 1.0), "no route back in by the mouth");
                phase[0] = 1;
            } else if (phase[0] == 1 && termite.blockPosition().equals(bottom)) {
                helper.assertTrue(termite.getNavigator().moveTo(onTop, 1.0), "no route out by the mouth");
                phase[0] = 2;
            } else if (phase[0] == 2 && termite.blockPosition().equals(onTop)) {
                phase[0] = 3;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 3, "the termite is at " + termite.blockPosition().subtract(bottom) + " from the shaft bottom in phase " + phase[0]);
            helper.assertTrue(peeked[2] > 0, "with no other way the termite did not come out of the mouth (it never looked out)");
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
