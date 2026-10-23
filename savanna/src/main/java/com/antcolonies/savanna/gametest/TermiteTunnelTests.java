package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestWatch;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.MoundSoil;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteSpireBlock;
import com.antcolonies.savanna.block.TermiteTunnelBlock;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Route;
import com.antcolonies.nav.StepKind;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

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
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteTunnelTests {
    /** The tunnel blocks, from the window in the lump's west side to the one under the spire. */
    static final List<BlockPos> TUNNEL = List.of(new BlockPos(8, 1, 10), new BlockPos(9, 1, 10), new BlockPos(10, 1, 10),
            new BlockPos(10, 2, 10));
    private static final BlockPos BASE = new BlockPos(10, 3, 10);
    private static final BlockPos THICK = new BlockPos(10, 4, 10);
    private static final BlockPos MOUTH = new BlockPos(10, 5, 10);

    private static BlockState soil() {
        return SavannaBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED)
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
        BlockState tunnel = SavannaBlocks.TERMITE_TUNNEL.get().defaultBlockState().setValue(MoundSoil.AGE, 2);
        for (BlockPos pos : TUNNEL) {
            helper.setBlock(pos, tunnel);
        }
        BlockState spire = SavannaBlocks.TERMITE_SPIRE.get().defaultBlockState().setValue(MoundSoil.AGE, 2);
        helper.setBlock(BASE, spire.setValue(TermiteSpireBlock.THICKNESS, 0));
        helper.setBlock(THICK, spire.setValue(TermiteSpireBlock.THICKNESS, 1));
        helper.setBlock(MOUTH, SavannaBlocks.TERMITE_MOUTH.get().defaultBlockState().setValue(MoundSoil.AGE, 2));
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
     * on the way); then, with the window shut behind it, the only way out is the mouth: it comes out with its look round
     * (it holds still the whole time with its head just out of the mouth, only its head moves, see DoorwayMotion, for the
     * whole look round), climbs down the outside of the spire and walks off the lump to the ground on its far side. Then
     * it goes back in by the mouth, without looking round, and down the channel to the window.
     */
    @GameTest(template = "arena", timeoutTicks = 3600, skyAccess = true)
    public static void aTermiteCrawlsUpInsideASpireAndComesOutOfItsMouth(GameTestHelper helper) {
        lumpWithATunnel(helper);
        TermiteWorkerEntity termite = SavannaTests.loneTermite(helper, 3, 1, 10);
        TestWatch.watch(helper, termite, false);
        BlockPos mouth = helper.absolutePos(MOUTH);
        BlockPos ground = helper.absolutePos(new BlockPos(16, 1, 10));
        BlockPos window = helper.absolutePos(TUNNEL.get(0));
        double rim = mouth.getY() + ((com.antcolonies.nav.Doorway) helper.getLevel().getBlockState(mouth).getBlock()).mouthHeight();
        int[] phase = {0};
        boolean[] seen = new boolean[3]; // in a channel, crawling with the route through the tunnel, looking round
        int[] lookingOut = {0};
        net.minecraft.world.phys.Vec3[] peekedAt = {null};
        helper.onEachTick(() -> {
            if (termite.getNavigator().isInChannel()) {
                seen[0] = true;
            }
            if (termite.getNavigator().isPeeking()) {
                seen[2] |= phase[0] == 2;
                helper.assertTrue(phase[0] != 3, "the termite looked round on its way back in by the mouth");
            }
            if (termite.getNavigator().isPeeking() && phase[0] == 2) {
                helper.assertTrue(Math.abs(termite.getY() - (rim - com.antcolonies.nav.DoorwayMotion.PEEK_DEPTH)) < 0.02,
                        "the termite looks round from " + (termite.getY() - rim) + " below the rim, not with its head just out of the mouth");
                if (peekedAt[0] == null) {
                    peekedAt[0] = termite.position();
                }
                helper.assertTrue(termite.position().distanceTo(peekedAt[0]) < 0.001, "the termite moved while it looked round");
                lookingOut[0]++;
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
                        helper.assertTrue(termite.blockPosition().equals(ground), "it ended at " + termite.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))
                                + " instead of on the ground beyond the lump");
                        helper.assertTrue(Math.abs(lookingOut[0] - com.antcolonies.nav.DoorwayMotion.PEEK_TICKS) <= 1,
                                "the termite looked round for " + lookingOut[0] + " ticks, not the whole look round");
                        helper.assertTrue(termite.getNavigator().moveTo(window, 1.0), "no way back in by the mouth");
                        Route route = termite.getNavigator().getPlannedRoute();
                        helper.assertTrue(has(route, StepKind.INTO_CHANNEL) || has(route, StepKind.CRAWL_CHANNEL), "it does not go back in by the mouth: " + route);
                        phase[0] = 3;
                    }
                }
                case 3 -> {
                    if (termite.getNavigator().isDone()) {
                        helper.assertFalse(termite.getNavigator().hasFailed(), "it got stuck on its way back in by the mouth, at "
                                + termite.position() + "; see the debug log");
                        phase[0] = 4;
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 4, "still walking (part " + phase[0] + ")");
            helper.assertTrue(seen[2], "it never looked round out of the mouth");
            helper.assertTrue(termite.blockPosition().equals(window), "it ended at " + termite.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))
                    + " instead of back at the window");
        });
    }

    /**
     * Soldiers, too big for the tunnel blocks (a major and a minor, and a minor from no colony standing still: anything
     * that does not fit), find no way into a termite tunnel or a mouth, while a worker does crawl in. (The one standing
     * still was a harvester ant until the split, step S4; an ant comes back with the Matabele ant, M8 step 1.)
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void soldiersFindNoWayIntoTermiteTunnels(GameTestHelper helper) {
        lumpWithATunnel(helper);
        com.antcolonies.savanna.entity.TermiteSoldierEntity outsider = TestAnts.lone(helper, SavannaEntities.TERMITE_MINOR_SOLDIER.get(), 3, 1, 10);
        com.antcolonies.savanna.entity.TermiteSoldierEntity major = SavannaTests.loneSoldier(helper, true, 3, 1, 12);
        com.antcolonies.savanna.entity.TermiteSoldierEntity minor = SavannaTests.loneSoldier(helper, false, 3, 1, 14);
        TermiteWorkerEntity worker = SavannaTests.loneTermite(helper, 3, 1, 8);
        helper.runAfterDelay(10, () -> {
            for (BlockPos cell : List.of(TUNNEL.get(1), TUNNEL.get(3), THICK, MOUTH)) {
                helper.assertFalse(outsider.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "a soldier from no colony found a way into " + cell);
                helper.assertFalse(major.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "a major found a way into " + cell);
                helper.assertFalse(minor.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "a minor found a way into " + cell);
            }
            helper.assertTrue(worker.getNavigator().moveTo(helper.absolutePos(TUNNEL.get(3)), 1.0), "a worker found no way in");
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
        TermiteWorkerEntity termite = SavannaTests.loneTermite(helper, 3, 1, 10);
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
                TermiteWorkerEntity loaded = SavannaEntities.TERMITE_WORKER.get().create(helper.getLevel());
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
