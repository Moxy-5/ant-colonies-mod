package com.antcolonies.gametest;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteSpireBlock;
import com.antcolonies.block.TermiteTunnelBlock;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Route;
import com.antcolonies.nav.StepKind;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Termites crawling through tunnels (tunnel plan step 3): along the 6-pixel channel of termite tunnel blocks, up the
 * hollow pieces of a spire, out of its chimney mouth with the look round and down the outside; nobody else gets in.
 * The small mound in these tests: a lump of soil 5 x 2 x 5 with a tunnel from a window in its west side east and up
 * into a spire with a mouth on top (see {@link #lumpWithATunnel}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteTunnelTests {
    /** The tunnel blocks, from the window in the lump's west side to the one under the spire. */
    static final List<BlockPos> TUNNEL = List.of(new BlockPos(8, 1, 10), new BlockPos(9, 1, 10), new BlockPos(10, 1, 10),
            new BlockPos(10, 2, 10));
    private static final BlockPos BASE = new BlockPos(10, 3, 10);
    private static final BlockPos THICK = new BlockPos(10, 4, 10);
    private static final BlockPos MOUTH = new BlockPos(10, 5, 10);

    private static BlockState soil() {
        return ModBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED)
                .setValue(TermiteMoundBlock.AGE, 2);
    }

    /**
     * A dirt floor, a lump of mound soil at x 8 to 12, y 1 and 2, z 8 to 12, with tunnel blocks from (8, 1, 10) east to
     * (10, 1, 10) and up to (10, 2, 10), a window open in the west side of the first; on top of the last a spire: a base
     * piece, a thick piece and a chimney mouth (alone, so slim).
     */
    static void lumpWithATunnel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        for (int x = 8; x <= 12; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 8; z <= 12; z++) {
                    helper.setBlock(x, y, z, soil());
                }
            }
        }
        BlockState tunnel = ModBlocks.TERMITE_TUNNEL.get().defaultBlockState().setValue(MoundSoil.AGE, 2);
        for (BlockPos pos : TUNNEL) {
            helper.setBlock(pos, tunnel);
        }
        BlockState spire = ModBlocks.TERMITE_SPIRE.get().defaultBlockState().setValue(MoundSoil.AGE, 2);
        helper.setBlock(BASE, spire.setValue(TermiteSpireBlock.THICKNESS, 0));
        helper.setBlock(THICK, spire.setValue(TermiteSpireBlock.THICKNESS, 1));
        helper.setBlock(MOUTH, ModBlocks.TERMITE_MOUTH.get().defaultBlockState().setValue(MoundSoil.AGE, 2));
        // Join everything up the way it would be had it been built block by block.
        for (BlockPos rel : List.of(TUNNEL.get(0), TUNNEL.get(1), TUNNEL.get(2), TUNNEL.get(3), MOUTH)) {
            BlockPos pos = helper.absolutePos(rel);
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), 2);
        }
        TermiteTunnelBlock.setWindow(level, helper.absolutePos(TUNNEL.get(0)), Direction.WEST, true);
    }

    private static boolean has(Route route, StepKind kind) {
        return route != null && route.steps().stream().anyMatch(step -> step.kind() == kind);
    }

    /**
     * A termite walks in at the window, along the tunnel and up inside the spire into its mouth (hidden in the channel
     * on the way); then, with the window shut behind it, the only way out is the mouth: it comes out with its look round,
     * climbs down the outside of the spire and walks off the lump to the ground on its far side.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void aTermiteCrawlsUpInsideASpireAndComesOutOfItsMouth(GameTestHelper helper) {
        lumpWithATunnel(helper);
        TermiteWorkerEntity termite = TestAnts.loneTermite(helper, 3, 1, 10);
        NavigationTests.watch(helper, termite, false);
        BlockPos mouth = helper.absolutePos(MOUTH);
        BlockPos ground = helper.absolutePos(new BlockPos(16, 1, 10));
        int[] phase = {0};
        boolean[] seen = new boolean[3]; // in a channel, crawling with the route through the tunnel, looking round
        helper.onEachTick(() -> {
            if (termite.getNavigator().isInChannel()) {
                seen[0] = true;
            }
            if (termite.getNavigator().isPeeking()) {
                seen[2] = true;
            }
            switch (phase[0]) {
                case 0 -> {
                    if (helper.getTick() >= 10) {
                        helper.assertTrue(termite.getNavigator().moveTo(mouth, 1.0), "no route in by the window and up into the mouth");
                        Route route = termite.getNavigator().getPlannedRoute();
                        seen[1] = has(route, StepKind.INTO_CHANNEL) && has(route, StepKind.CRAWL_CHANNEL);
                        helper.assertTrue(seen[1], "the route does not go in by the window and along the tunnel: " + route);
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    if (termite.getNavigator().isDone()) {
                        helper.assertFalse(termite.getNavigator().hasFailed(), "it got stuck on its way up into the mouth, at "
                                + termite.position() + "; see the debug log");
                        helper.assertTrue(Foothold.channel(mouth).equals(termite.getNavigator().getFoothold()),
                                "it is not in the mouth's channel but on " + termite.getNavigator().getFoothold());
                        helper.assertTrue(seen[0], "it was never in a channel");
                        TermiteTunnelBlock.setWindow(helper.getLevel(), helper.absolutePos(TUNNEL.get(0)), Direction.WEST, false);
                        helper.assertTrue(termite.getNavigator().moveTo(ground, 1.0), "no way out of the mouth and down to the ground");
                        Route route = termite.getNavigator().getPlannedRoute();
                        helper.assertTrue(has(route, StepKind.OUT_OF_MOUTH), "it does not leave by the mouth: " + route);
                        phase[0] = 2;
                    }
                }
                case 2 -> {
                    if (termite.getNavigator().isDone()) {
                        helper.assertFalse(termite.getNavigator().hasFailed(), "it got stuck on its way out of the mouth and down, at "
                                + termite.position() + "; see the debug log");
                        phase[0] = 3;
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 3, "still walking (part " + phase[0] + ")");
            helper.assertTrue(seen[2], "it never looked round out of the mouth");
            helper.assertTrue(termite.blockPosition().equals(ground), "it ended at " + termite.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))
                    + " instead of on the ground beyond the lump");
        });
    }

    /** An ant (and so anything that does not fit) finds no way into a termite tunnel or a mouth. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void antsFindNoWayIntoTermiteTunnels(GameTestHelper helper) {
        lumpWithATunnel(helper);
        WorkerAntEntity ant = TestAnts.loneAnt(helper, 3, 1, 10);
        helper.runAfterDelay(10, () -> {
            for (BlockPos cell : List.of(TUNNEL.get(1), TUNNEL.get(3), THICK, MOUTH)) {
                helper.assertFalse(ant.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "an ant found a way into " + cell);
            }
            helper.succeed();
        });
    }

    /**
     * A termite resting in the upright part of the tunnel (where it would drop if it let go) is still there after its
     * chunk is saved and loaded again, still in the channel.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void aTermiteInATunnelIsStillThereAfterAReload(GameTestHelper helper) {
        lumpWithATunnel(helper);
        TermiteWorkerEntity termite = TestAnts.loneTermite(helper, 3, 1, 10);
        BlockPos goal = helper.absolutePos(TUNNEL.get(3));
        helper.runAfterDelay(10, () -> helper.assertTrue(termite.getNavigator().moveTo(goal, 1.0), "no route into the tunnel"));
        TermiteWorkerEntity[] copy = {null};
        long[] upSince = {-1};
        double[] savedY = {0.0};
        long[] loadedAt = {0};
        helper.onEachTick(() -> {
            if (copy[0] != null) {
                return;
            }
            if (upSince[0] < 0 && helper.getTick() > 12 && termite.getNavigator().isDone() && termite.blockPosition().equals(goal)) {
                upSince[0] = helper.getTick();
            }
            if (upSince[0] >= 0 && helper.getTick() >= upSince[0] + 20) {
                CompoundTag tag = termite.saveWithoutId(new CompoundTag());
                tag.remove("UUID");
                savedY[0] = termite.getY();
                termite.discard();
                TermiteWorkerEntity loaded = ModEntities.TERMITE_WORKER.get().create(helper.getLevel());
                loaded.load(tag);
                loaded.removeFreeWill();
                loaded.keepOutOfColonies();
                helper.getLevel().addFreshEntity(loaded);
                copy[0] = loaded;
                loadedAt[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(copy[0] != null, "the termite has not got into the tunnel yet");
            TermiteWorkerEntity loaded = copy[0];
            helper.assertTrue(loaded.getY() >= savedY[0] - 0.05, "the termite dropped after the reload (from " + savedY[0] + " to " + loaded.getY() + ")");
            helper.assertTrue(loaded.getNavigator().isInChannel(), "the termite is no longer in the channel after the reload");
            helper.assertTrue(helper.getTick() >= loadedAt[0] + 80, "waiting to see whether the reloaded termite stays");
        });
    }
}
