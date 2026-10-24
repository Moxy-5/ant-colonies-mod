package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.TermiteHoleBlock;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteMouthBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

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
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteMoundTests {
    static final int SURFACE = 12;
    static final int MIDDLE = 12;

    /** The termite colony in this test area (one with a living queen). */
    static Colony colony(GameTestHelper helper) {
        net.minecraft.world.phys.AABB area = helper.getBounds();
        Colony found = ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(colony -> area.contains(net.minecraft.world.phys.Vec3.atCenterOf(colony.getCenter()))
                        && colony.getQueen(helper.getLevel()) != null && colony.getSpecies().equals(SavannaSpecies.MACROTERMES))
                .findFirst().orElse(null);
        if (found != null) {
            // Both still needed on the 65 x 65 arenas (tried 2026-10-14: without either, the hay test failed in full runs
            // and passed alone): scouts and foragers stay in this test's arena, and only this test's enemies raise its alarm.
            found.foodMap().setTestArea(area.expandTowards(0, 16, 0)); // (taller: food up a slope may stand above the arena)
            found.part(SavannaParts.ALARM).orElseThrow().setTestArea(area);
        }
        return found;
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
        net.minecraft.world.item.ItemStack spires = new net.minecraft.world.item.ItemStack(com.antcolonies.savanna.registry.SavannaItems.TERMITE_SPIRE.get(), 64);
        // A spire: five pieces in a column narrow to the tip.
        for (int y = 0; y < 6; y++) {
            place(helper, player, spires, new BlockPos(1, y, 1));
        }
        for (int y = 1; y <= 6; y++) {
            BlockState piece = helper.getBlockState(new BlockPos(1, y, 1));
            helper.assertTrue(piece.is(SavannaBlocks.TERMITE_SPIRE.get()), "no spire piece at height " + y);
            int expected = Math.min(com.antcolonies.savanna.block.TermiteSpireBlock.TIP, y - 1);
            helper.assertTrue(piece.getValue(com.antcolonies.savanna.block.TermiteSpireBlock.THICKNESS) == expected,
                    "spire piece " + y + " has thickness " + piece.getValue(com.antcolonies.savanna.block.TermiteSpireBlock.THICKNESS) + ", not " + expected);
        }
        // Two ramps: one rising east, and ahead of it one rising north: the first becomes an outer corner.
        net.minecraft.world.item.ItemStack ramps = new net.minecraft.world.item.ItemStack(com.antcolonies.savanna.registry.SavannaItems.TERMITE_RAMP.get(), 64);
        player.setYRot(-90.0F); // looking east
        place(helper, player, ramps, new BlockPos(3, 0, 3));
        player.setYRot(180.0F); // looking north
        place(helper, player, ramps, new BlockPos(4, 0, 3));
        BlockState first = helper.getBlockState(new BlockPos(3, 1, 3));
        BlockState second = helper.getBlockState(new BlockPos(4, 1, 3));
        helper.assertTrue(first.is(SavannaBlocks.TERMITE_RAMP.get()) && second.is(SavannaBlocks.TERMITE_RAMP.get()), "the ramps were not placed");
        helper.assertTrue(first.getValue(com.antcolonies.savanna.block.TermiteRampBlock.FACING) == net.minecraft.core.Direction.EAST
                && second.getValue(com.antcolonies.savanna.block.TermiteRampBlock.FACING) == net.minecraft.core.Direction.NORTH,
                "the ramps do not rise the way the player looked");
        helper.assertTrue(first.getValue(com.antcolonies.savanna.block.TermiteRampBlock.SHAPE) == net.minecraft.world.level.block.state.properties.StairsShape.OUTER_LEFT,
                "the ramps did not join into a corner (" + first.getValue(com.antcolonies.savanna.block.TermiteRampBlock.SHAPE) + ")");
        // A chimney mouth on top of a spire piece: slim; with soil beside it, a full block.
        helper.setBlock(new BlockPos(4, 1, 1), SavannaBlocks.TERMITE_SPIRE.get());
        place(helper, player, new net.minecraft.world.item.ItemStack(com.antcolonies.savanna.registry.SavannaItems.TERMITE_MOUTH.get()), new BlockPos(4, 1, 1));
        BlockPos mouth = new BlockPos(4, 2, 1);
        helper.assertTrue(helper.getBlockState(mouth).is(SavannaBlocks.TERMITE_MOUTH.get()) && helper.getBlockState(mouth).getValue(TermiteMouthBlock.SLIM),
                "a chimney mouth alone on a spire is not slim");
        helper.setBlock(new BlockPos(5, 2, 1), SavannaBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED));
        helper.assertTrue(!helper.getBlockState(mouth).getValue(TermiteMouthBlock.SLIM), "a chimney mouth with soil beside it is still slim");
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(SavannaEntities.TERMITE_WORKER.get(), 0, 1, 0);
        BlockPos abs = helper.absolutePos(mouth);
        helper.assertTrue(helper.getBlockState(mouth).getCollisionShape(helper.getLevel(), abs, CollisionContext.of(termite)).isEmpty(),
                "a termite cannot pass through the chimney mouth");
        helper.assertTrue(!helper.getBlockState(mouth).getCollisionShape(helper.getLevel(), abs, CollisionContext.of(player)).isEmpty(),
                "a player can pass through the chimney mouth");
        // Ageing: random ticks dry a spire piece through the ages to old.
        BlockPos aged = new BlockPos(1, 1, 1);
        for (int i = 0; i < 20000 && helper.getBlockState(aged).getValue(com.antcolonies.savanna.block.MoundSoil.AGE) < com.antcolonies.savanna.block.MoundSoil.OLDEST; i++) {
            helper.randomTick(aged);
        }
        helper.assertTrue(helper.getBlockState(aged).getValue(com.antcolonies.savanna.block.MoundSoil.AGE) == com.antcolonies.savanna.block.MoundSoil.OLDEST,
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
        net.minecraft.world.item.ItemStack tunnels = new net.minecraft.world.item.ItemStack(com.antcolonies.savanna.registry.SavannaItems.TERMITE_TUNNEL.get(), 64);
        BlockPos a = new BlockPos(3, 1, 3);
        BlockPos b = a.east();
        BlockPos c = a.above();
        place(helper, player, tunnels, a.below());
        place(helper, player, tunnels, b.below());
        place(helper, player, tunnels, a);
        helper.assertTrue(helper.getBlockState(a).is(SavannaBlocks.TERMITE_TUNNEL.get()) && helper.getBlockState(b).is(SavannaBlocks.TERMITE_TUNNEL.get())
                && helper.getBlockState(c).is(SavannaBlocks.TERMITE_TUNNEL.get()), "the tunnel blocks were not placed");
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
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(SavannaEntities.TERMITE_WORKER.get(), 0, 1, 0);
        helper.assertTrue(!helper.getBlockState(a).getCollisionShape(level, absA, CollisionContext.of(termite)).isEmpty(),
                "a termite can pass through a tunnel block already");
        for (int i = 0; i < 20000 && helper.getBlockState(a).getValue(com.antcolonies.savanna.block.MoundSoil.AGE) < com.antcolonies.savanna.block.MoundSoil.OLDEST; i++) {
            helper.randomTick(a);
        }
        helper.assertTrue(helper.getBlockState(a).getValue(com.antcolonies.savanna.block.MoundSoil.AGE) == com.antcolonies.savanna.block.MoundSoil.OLDEST,
                "a tunnel block did not dry to old");
        assertOpen(helper, a, "after it dried", net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        helper.succeed();
    }

    /** The tunnel block at {@code pos} opens out of exactly these sides. */
    private static void assertOpen(GameTestHelper helper, BlockPos pos, String what, net.minecraft.core.Direction... open) {
        BlockState state = helper.getBlockState(pos);
        java.util.Set<net.minecraft.core.Direction> expected = java.util.Set.of(open);
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            boolean isOpen = state.getValue(com.antcolonies.savanna.block.TermiteTunnelBlock.OPEN.get(side));
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
        TermiteMoundBlock mound = SavannaBlocks.TERMITE_MOUND.get();
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
        helper.assertTrue(TermiteMoundBlock.hardness(old) > TermiteMoundBlock.hardness(SavannaBlocks.TERMITE_MOUND.get().defaultBlockState()),
                "old soil is not harder than wet soil");
        helper.assertTrue(mound.addLayer(level, pos.above()), "nothing could be built on a block that has set");
        // A finished block drops itself (this also fails if the loot table does not load); wet soil drops nothing.
        BlockState fresh = mound.defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED);
        helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(fresh, level, pos, null).stream()
                .anyMatch(stack -> stack.is(com.antcolonies.savanna.registry.SavannaItems.TERMITE_MOUND.get())), "a finished block drops nothing");
        helper.assertTrue(net.minecraft.world.level.block.Block.getDrops(mound.defaultBlockState(), level, pos, null).isEmpty(),
                "a block still being built drops something");
        helper.succeed();
    }

    /** Over a gap (a tunnel in the mound) a block is built out from set soil beside it, never from wet soil. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aBlockOverAGapIsBuiltOutFromSetSoilBesideIt(GameTestHelper helper) {
        ground(helper);
        ServerLevel level = helper.getLevel();
        TermiteMoundBlock mound = SavannaBlocks.TERMITE_MOUND.get();
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
     * A termite walks through a chimney block into a room that is closed all round; a minor soldier from no colony finds
     * no way in. The route planner treats the chimney block as a doorway for the termites that pass the holes only, and
     * so does the block itself: workers walk through it, a soldier (and so any other creature) bumps into it. (The
     * outsider was a harvester ant until the split, step S4; an ant comes back with the Matabele ant, M8 step 1.)
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
        helper.setBlock(new BlockPos(4, 1, 2), SavannaBlocks.TERMITE_HOLE.get());
        BlockPos inside = helper.absolutePos(new BlockPos(2, 1, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(SavannaEntities.TERMITE_WORKER.get(), 6, 1, 2);
        TermiteSoldierEntity outsider = TestAnts.lone(helper, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), 6, 1, 4);
        BlockPos hole = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockState state = helper.getLevel().getBlockState(hole);
        helper.assertTrue(state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(termite)).isEmpty(), "a termite bumps into the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.of(outsider)).isEmpty(), "a soldier walks through the hole block");
        helper.assertTrue(!state.getCollisionShape(helper.getLevel(), hole, CollisionContext.empty()).isEmpty(), "the hole block is not solid for everyone else");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(termite.getNavigator().moveTo(inside, 1.0), "the termite found no route through the chimney block");
            helper.assertTrue(!outsider.getNavigator().moveTo(inside, 1.0), "the soldier found a route into the closed room");
        });
        helper.succeedWhen(() -> helper.assertTrue(helper.getTick() > 20 && termite.blockPosition().equals(inside),
                "the termite is at " + termite.blockPosition().subtract(inside) + " from the middle of the room"));
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
        BlockState soil = SavannaBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED);
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || z != 2) {
                        helper.setBlock(new BlockPos(x, y, z), soil);
                    }
                }
            }
        }
        helper.setBlock(new BlockPos(2, 3, 2), SavannaBlocks.TERMITE_HOLE.get());
        BlockPos side = new BlockPos(3, 1, 2);
        helper.setBlock(side, Blocks.AIR); // a way out at the foot of the chimney
        BlockPos bottom = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos onTop = helper.absolutePos(new BlockPos(3, 4, 2));
        TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(SavannaEntities.TERMITE_WORKER.get(), 2, 1, 2);
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

    /**
     * M8 step 1a part 2: a full grown mound (put up at once) whose colony dies wears down in the real order, driven day by
     * day through the dead mound book (the world's clock is shared): nothing on the day it dies; the spire tips and
     * chimney mouths first and the spires lower by day 2; every piece gone and the spires down to the body by day 6, grass
     * on the settled foot; the body lower by day 18; by day 40 a low hump 4 blocks high, grass over every column, washed
     * soil round the foot. Never back up when the clock goes back; carries on after a save and load; a player's block
     * keeps its column; the hump stays. Part 3, the way in and the works round it: the hollow shaft stays open, a hole from
     * the top of the hump down to the nest, with at most 3 tunnel tops fallen open beside it, and no other tunnel open to
     * the sky; out round it a tunnel to an open hole and a feeding gallery under hay plastered with mud, and a tunnel to a
     * plugged hole with a runway of mud sheeting on from it: the mud gone by day 6, both holes grass by day 6 while every
     * gallery is still open; by day 30 the far ends filling in and the cells near the nest still open; by day 1000 every
     * gallery filled; the nest's shaft open and nothing else under the ground changed.
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aDeadMoundWearsDownToAGrassyHump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.antcolonies.gametest.TestScenes.deepGround(helper);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(10, () -> {
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            com.antcolonies.savanna.colony.Mound mound = colony.part(SavannaParts.MOUND).orElseThrow();
            mound.buildInstantly(level, mound.numbers().maxHeight());
            BlockPos center = colony.getCenter();
            java.util.UUID id = colony.getId();
            com.antcolonies.savanna.colony.DeadMounds book = com.antcolonies.savanna.colony.DeadMounds.get(level);
            try {
                java.util.Map<BlockPos, BlockState> whole = snapshot(level, center);
                int stood = highest(whole, center);
                long pieces = pieces(whole);
                java.util.Set<Long> footprint = new java.util.HashSet<>();
                whole.forEach((pos, state) -> {
                    if (isMound(state)) {
                        footprint.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
                    }
                });
                helper.assertTrue(stood >= 28 && pieces >= 20, "the full mound stands " + stood + " tall with " + pieces + " pieces");
                // A player's block in a column of the body: it and the soil under it stay.
                BlockPos cobble = null;
                for (int dx = -5; dx <= 5 && cobble == null; dx++) {
                    for (int dz = -5; dz <= 5 && cobble == null; dz++) {
                        double distance = Math.sqrt(dx * dx + dz * dz);
                        BlockPos base = center.offset(dx, 0, dz);
                        boolean solid = distance >= 3 && distance <= 5;
                        for (int y = 0; y <= 6 && solid; y++) {
                            solid = isMound(level.getBlockState(base.above(y)));
                        }
                        if (solid) {
                            cobble = base.above(4);
                        }
                    }
                }
                helper.assertTrue(cobble != null, "no column of the body 7 blocks tall 3 to 5 blocks out");
                level.setBlock(cobble, Blocks.COBBLESTONE.defaultBlockState(), 3);
                java.util.List<BlockState> underCobble = new java.util.ArrayList<>();
                for (int y = 1; y <= 4; y++) {
                    underCobble.add(level.getBlockState(cobble.below(y)));
                }
                // Part 3, the works round it: the nest shaft dug out; a tunnel east to an open hole, on to hay with a
                // feeding gallery under it and mud plastered over it; a tunnel south to a plugged hole, a runway of mud
                // sheeting on from it.
                int shaftDepth = colony.nest().shaftDepth(level);
                for (int i = 1; i <= shaftDepth; i++) {
                    level.setBlock(center.below(i), Blocks.AIR.defaultBlockState(), 3);
                }
                java.util.List<BlockPos> eastTunnel = new java.util.ArrayList<>(), southTunnel = new java.util.ArrayList<>();
                for (int d = 6; d <= 20; d++) {
                    eastTunnel.add(center.offset(d, -2, 0));
                }
                for (int d = 6; d <= 16; d++) {
                    southTunnel.add(center.offset(0, -2, d));
                }
                BlockPos openHole = center.offset(20, -1, 0), plugHole = center.offset(0, -1, 16), hay = center.offset(22, 0, 3);
                java.util.List<BlockPos> feeding = java.util.List.of(center.offset(20, -2, 1), center.offset(20, -2, 2), center.offset(20, -2, 3),
                        center.offset(21, -2, 3), center.offset(22, -2, 3), center.offset(22, -1, 3));
                java.util.List<BlockPos> runway = java.util.List.of(center.offset(0, 0, 17), center.offset(0, 0, 18), center.offset(0, 0, 19));
                java.util.List<BlockPos> dug = new java.util.ArrayList<>(eastTunnel);
                dug.addAll(southTunnel);
                dug.addAll(feeding);
                for (BlockPos pos : dug) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(openHole, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(plugHole, SavannaBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED), 3);
                level.setBlock(hay, Blocks.HAY_BLOCK.defaultBlockState(), 3);
                java.util.List<BlockPos> mud = new java.util.ArrayList<>();
                plaster(level, hay, java.util.List.of(hay), mud);
                for (BlockPos cell : runway) {
                    plaster(level, cell, runway, mud);
                }
                com.antcolonies.colony.FoodWays ways = colony.nest().foodWays();
                ways.addNewTunnelForTests(new com.antcolonies.colony.FoodWays.NewTunnel(
                        new com.antcolonies.colony.NestPlan.ForagingTunnel(eastTunnel, openHole, net.minecraft.core.Direction.EAST), hay, feeding));
                ways.addNewTunnelForTests(new com.antcolonies.colony.FoodWays.NewTunnel(
                        new com.antcolonies.colony.NestPlan.ForagingTunnel(southTunnel, plugHole, net.minecraft.core.Direction.SOUTH),
                        center.offset(0, 0, 21), null));
                ways.addRunwayForTests(new com.antcolonies.colony.FoodWays.Runway(plugHole, center.offset(0, 0, 21), runway));
                helper.assertTrue(mud.size() >= 8, "only " + mud.size() + " plates of mud put");

                java.util.Map<BlockPos, BlockState> underground = new java.util.HashMap<>();
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -6, -12), center.offset(12, -1, 12))) {
                    underground.put(pos.immutable(), level.getBlockState(pos));
                }
                java.util.Map<BlockPos, BlockState> before = snapshot(level, center);

                ColonyManager.get(level).kill(level, colony);
                com.antcolonies.savanna.colony.DeadMounds.DeadMound dead = book.get(id);
                helper.assertTrue(dead != null && dead.wears(), "the dead mound's wear was not written down");
                java.util.List<BlockPos> holesAtDeath = book.holesLeftForTests(dead), galleriesAtDeath = book.galleriesLeftForTests(dead);
                helper.assertTrue(holesAtDeath.contains(openHole) && holesAtDeath.contains(plugHole), "the foraging holes were not written down: "
                        + book.describeWorksForTests(dead));
                helper.assertTrue(galleriesAtDeath.containsAll(eastTunnel) && galleriesAtDeath.containsAll(feeding)
                        && galleriesAtDeath.containsAll(southTunnel.subList(6, southTunnel.size())), "the galleries were not written down: "
                        + book.describeWorksForTests(dead));
                for (int i = 1; i <= shaftDepth; i++) {
                    helper.assertTrue(!galleriesAtDeath.contains(center.below(i)), "the nest's shaft is to be filled in with the galleries");
                }

                book.updateForTests(level, dead, 0.1);
                helper.assertTrue(snapshot(level, center).equals(before), "the mound changed on the day it died");
                helper.assertTrue(mudLeft(level, mud) == mud.size() && level.getBlockState(openHole).isAir()
                        && level.getBlockState(plugHole).getBlock() instanceof TermiteMoundBlock, "the works changed on the day it died: mud "
                                + mudLeft(level, mud) + " of " + mud.size() + ", the open hole " + level.getBlockState(openHole) + ", the plug "
                                + level.getBlockState(plugHole));

                book.updateForTests(level, dead, 2.0);
                java.util.Map<BlockPos, BlockState> day2 = snapshot(level, center);
                helper.assertTrue(highest(day2, center) <= stood - 5, "the spires are only " + (stood - highest(day2, center)) + " lower on day 2");
                helper.assertTrue(pieces(day2) < pieces, "no piece has crumbled by day 2");
                day2.forEach((pos, state) -> helper.assertTrue(!isPiece(state) || pos.getY() - center.getY() <= 16,
                        "a piece " + (pos.getY() - center.getY()) + " blocks up still stands on day 2: " + state));
                book.updateForTests(level, dead, 1.0); // the clock set back: nothing comes back
                helper.assertTrue(snapshot(level, center).equals(day2), "the mound changed when the clock went back");

                dead = book.reloadForTests(level, id); // saved and loaded: the wear carries on from where it was
                book.updateForTests(level, dead, 6.0);
                java.util.Map<BlockPos, BlockState> day6 = snapshot(level, center);
                BlockPos playersBlock = cobble;
                long standing = day6.entrySet().stream().filter(entry -> isPiece(entry.getValue()) && !(entry.getKey().getX() == playersBlock.getX()
                        && entry.getKey().getZ() == playersBlock.getZ() && entry.getKey().getY() < playersBlock.getY())).count();
                helper.assertTrue(standing == 0, standing + " pieces still stand on day 6 (not counting any under the player's block)");
                int body = highest(day6, center);
                helper.assertTrue(body <= stood * 0.6 && body >= 8, "on day 6 the mound is " + body + " tall (stood " + stood + "): not down to its body");
                helper.assertTrue(day6.values().stream().anyMatch(state -> state.is(Blocks.GRASS_BLOCK)), "no grass on the settled foot by day 6");
                java.util.Set<BlockPos> waysIn = book.waysInForTests(dead);
                helper.assertTrue(windowsToTheSky(level, day6, waysIn) == 0, "tunnels laid open to the sky by day 6 besides the ways in");
                helper.assertTrue(mudLeft(level, mud) == 0, mudLeft(level, mud) + " plates of mud still stand on day 6");
                helper.assertTrue(level.getBlockState(openHole).is(Blocks.GRASS_BLOCK) && level.getBlockState(plugHole).is(Blocks.GRASS_BLOCK),
                        "on day 6 the open hole is " + level.getBlockState(openHole) + " and the plugged one " + level.getBlockState(plugHole));
                helper.assertTrue(book.holesLeftForTests(dead).isEmpty(), "holes still open on day 6: " + book.describeWorksForTests(dead));
                helper.assertTrue(level.getBlockState(hay).is(Blocks.HAY_BLOCK), "the hay the colony was eating is gone");
                for (BlockPos pos : dug) {
                    helper.assertTrue(level.getBlockState(pos).isAir(), "a gallery " + pos.subtract(center).toShortString() + " filled in by day 6");
                }

                book.updateForTests(level, dead, 18.0);
                java.util.Map<BlockPos, BlockState> day18 = snapshot(level, center);
                int slumping = highest(day18, center);
                helper.assertTrue(slumping < body && slumping > 4, "on day 18 the mound is " + slumping + " tall (" + body + " on day 6)");
                helper.assertTrue(windowsToTheSky(level, day18, book.waysInForTests(dead)) == 0, "tunnels laid open to the sky by day 18 besides the ways in");

                book.updateForTests(level, dead, 30.0); // the galleries fill in, the far ends first
                long filledFar = 0;
                for (BlockPos pos : dug) {
                    double out = Math.sqrt(Math.pow(pos.getX() - center.getX(), 2) + Math.pow(pos.getZ() - center.getZ(), 2));
                    if (out <= 7) {
                        helper.assertTrue(level.getBlockState(pos).isAir(), "a gallery " + pos.subtract(center).toShortString()
                                + " near the nest filled in by day 30");
                    } else if (out >= 16 && !level.getBlockState(pos).isAir()) {
                        filledFar++;
                    }
                }
                helper.assertTrue(filledFar > 0, "no far gallery filled in by day 30: " + book.describeWorksForTests(dead));

                book.updateForTests(level, dead, 40.0);
                java.util.Map<BlockPos, BlockState> hump = snapshot(level, center);
                helper.assertTrue(highest(hump, center) >= 3 && highest(hump, center) <= 4, "the hump is " + highest(hump, center) + " tall on day 40 (4, or 3 round the open chimney at its middle)");
                waysIn = book.waysInForTests(dead);
                helper.assertTrue(book.tunnelWaysForTests(dead) <= com.antcolonies.savanna.colony.MoundWear.MOST_TUNNEL_WAYS,
                        book.tunnelWaysForTests(dead) + " tunnel tops open upward");
                boolean seesSky = false;
                for (BlockPos pos : waysIn) {
                    seesSky |= pos.getY() - center.getY() < 4 && level.getBlockState(pos).isAir() && underTheSky(level, pos);
                }
                helper.assertTrue(level.getBlockState(center).isAir() && seesSky, "no way in down the old shaft on day 40: the entrance is "
                        + level.getBlockState(center) + ", the shaft open to the sky: " + seesSky + "; its cells " + shaftInWords(level, center, waysIn)
                        + "; the middle column " + book.describeColumnForTests(dead, 0, 0));
                com.antcolonies.savanna.SavannaMod.LOGGER.info("Dead mound test: {} tunnel tops open, {}", book.tunnelWaysForTests(dead),
                        book.describeWorksForTests(dead));
                int apron = 0;
                for (int dx = -11; dx <= 11; dx++) {
                    for (int dz = -11; dz <= 11; dz++) {
                        BlockPos base = center.offset(dx, 0, dz);
                        for (int y = 12; y >= 0; y--) {
                            BlockState state = level.getBlockState(base.above(y));
                            if (state.canBeReplaced()) {
                                continue;
                            }
                            helper.assertTrue(y <= 4 && (state.is(Blocks.GRASS_BLOCK) || base.above(y).equals(cobble) || waysIn.contains(base.above(y))),
                                    "the top of the hump at " + dx + ", " + dz + " is " + state + " " + y + " blocks up on day 40 (was "
                                            + before.get(base.above(y)) + ", under it " + level.getBlockState(base.below()) + "; " + book.describeColumnForTests(dead, dx, dz) + ")");
                            if (y == 0 && !footprint.contains(BlockPos.asLong(base.getX(), 0, base.getZ()))) {
                                apron++;
                            }
                            break;
                        }
                    }
                }
                helper.assertTrue(apron >= 20, "only " + apron + " places of washed soil round the foot");
                helper.assertTrue(windowsToTheSky(level, hump, waysIn) == 0, windowsToTheSky(level, hump, waysIn)
                        + " tunnel sides open to the sky in the hump besides the ways in");
                helper.assertTrue(level.getBlockState(cobble).is(Blocks.COBBLESTONE), "the player's block is gone");
                for (int y = 1; y <= 4; y++) {
                    // (the same blocks: a tunnel block's side toward a column worn away beside it shuts, as it does anywhere)
                    helper.assertTrue(level.getBlockState(cobble.below(y)).getBlock() == underCobble.get(y - 1).getBlock(), "the soil under the player's block changed " + y + " down, at " + cobble.subtract(center).toShortString() + ": " + underCobble.get(y - 1) + " to " + level.getBlockState(cobble.below(y)));
                }
                underground.keySet().removeAll(galleriesAtDeath); // (they fill in)
                underground.keySet().removeAll(holesAtDeath);
                underground.forEach((pos, state) -> helper.assertTrue(level.getBlockState(pos).equals(state),
                        "under the ground " + pos.subtract(center).toShortString() + " changed from " + state + " to " + level.getBlockState(pos)));
                helper.assertTrue(dead.isSettled(), "the hump is not settled on day 40");
                book.updateForTests(level, dead, 10.0);
                book.updateForTests(level, dead, 1000.0);
                helper.assertTrue(snapshot(level, center).equals(hump), "the settled hump changed");
                for (BlockPos pos : dug) {
                    helper.assertTrue(!level.getBlockState(pos).isAir(), "a gallery " + pos.subtract(center).toShortString() + " still open on day 1000");
                }
                helper.assertTrue(book.galleriesLeftForTests(dead).isEmpty(), "galleries left on day 1000: " + book.describeWorksForTests(dead));
                for (int i = 1; i <= shaftDepth; i++) {
                    helper.assertTrue(level.getBlockState(center.below(i)).isAir(), "the nest's shaft filled in at " + i + " down");
                }
            } finally {
                book.forgetForTests(id);
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-11, 5, -11), center.offset(11, 40, 11))) {
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            helper.succeed();
        });
    }

    /** Every block that is not air round a mound's middle, from its ground up. */
    private static java.util.Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos center) {
        java.util.Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-11, 0, -11), center.offset(11, 40, 11))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                blocks.put(pos.immutable(), state);
            }
        }
        return blocks;
    }

    /** How tall the mound stands: the highest mound block's height above the ground, plus one. */
    private static int highest(java.util.Map<BlockPos, BlockState> blocks, BlockPos center) {
        int top = 0;
        for (java.util.Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (isMound(entry.getValue())) {
                top = Math.max(top, entry.getKey().getY() - center.getY() + 1);
            }
        }
        return top;
    }

    /**
     * Sides of tunnel blocks in {@code blocks} (as they stand now) open to air under the open sky, but for the ways in
     * ({@code waysIn}: the old shaft and the tunnel tops fallen open, part 3).
     */
    private static int windowsToTheSky(ServerLevel level, java.util.Map<BlockPos, BlockState> blocks, java.util.Set<BlockPos> waysIn) {
        int open = 0;
        for (BlockPos pos : blocks.keySet()) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof com.antcolonies.savanna.block.TermiteTunnelBlock) || waysIn.contains(pos)) {
                continue;
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                BlockPos beside = pos.relative(side);
                if (!waysIn.contains(beside) && state.getValue(com.antcolonies.savanna.block.TermiteTunnelBlock.OPEN.get(side)) && level.getBlockState(beside).isAir()
                        && underTheSky(level, beside)) {
                    open++;
                }
            }
        }
        return open;
    }

    /**
     * Whether nothing stands over this place up to the sky (by the height map: the sky light lags behind a whole mound
     * worn away in one tick).
     */
    private static boolean underTheSky(ServerLevel level, BlockPos pos) {
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
    }

    /** The old shaft's cells up to the hump's top, low to high, told in words (for a failing check's message). */
    private static String shaftInWords(ServerLevel level, BlockPos center, java.util.Set<BlockPos> waysIn) {
        StringBuilder out = new StringBuilder();
        waysIn.stream().filter(pos -> pos.getY() - center.getY() < 8).sorted(java.util.Comparator.comparingInt(BlockPos::getY))
                .forEach(pos -> out.append(pos.subtract(center).toShortString()).append(' ').append(level.getBlockState(pos).getBlock())
                        .append(underTheSky(level, pos) ? " sky" : "").append(", above it ").append(level.getBlockState(pos.above()).getBlock()).append("; "));
        return out.toString();
    }

    /** Mud plastered round {@code cell} on every side but below it and toward {@code along} (a runway's own cells, the food). */
    private static void plaster(ServerLevel level, BlockPos cell, java.util.List<BlockPos> along, java.util.List<BlockPos> mud) {
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
            BlockPos wall = cell.relative(side);
            BlockState state = level.getBlockState(wall);
            if (side == net.minecraft.core.Direction.DOWN || along.contains(wall) || !(state.isAir() || com.antcolonies.block.FacedWall.isWall(state))) {
                continue;
            }
            level.setBlock(wall, SavannaBlocks.MUD_CRUST.get().withFace(state, side.getOpposite()), 3);
            if (!mud.contains(wall)) {
                mud.add(wall);
            }
        }
    }

    /** How many of these places still hold mud. */
    private static long mudLeft(ServerLevel level, java.util.List<BlockPos> mud) {
        return mud.stream().filter(pos -> com.antcolonies.block.FacedWall.isWall(level.getBlockState(pos))).count();
    }

    private static long pieces(java.util.Map<BlockPos, BlockState> blocks) {
        return blocks.values().stream().filter(TermiteMoundTests::isPiece).count();
    }

    private static boolean isMound(BlockState state) {
        return state.getBlock() instanceof TermiteMoundBlock || state.getBlock() instanceof TermiteHoleBlock
                || state.getBlock() instanceof com.antcolonies.savanna.block.MoundPieceBlock;
    }

    /** A spire piece, a chimney mouth or a ramp. */
    private static boolean isPiece(BlockState state) {
        return state.getBlock() instanceof com.antcolonies.savanna.block.TermiteSpireBlock || state.getBlock() instanceof TermiteMouthBlock
                || state.getBlock() instanceof com.antcolonies.savanna.block.TermiteRampBlock;
    }
}
