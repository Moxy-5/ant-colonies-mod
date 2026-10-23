package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestClock;
import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.List;
import java.util.function.IntBinaryOperator;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * Colonies on hills and uneven ground (the user's decisions of 2026-10-14: one shared ground reader, every planner
 * tested on hill arenas). The arenas are shaped by {@link TestScenes#shapeGround}: slopes, terraces, valleys and cliff
 * edges.
 * The termites' scenes (the split, step S4; the red harvester ant's are in {@link HarvesterHillTests}).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteHillTests {
    /**
     * A termite nest on a hillside: the ground slopes down to the north-west and up to the south-east (a block every 3
     * columns, along both x and z, so whichever way the foraging tunnels run, one runs uphill and the other downhill).
     * The downhill hole of a level tunnel would be in the open air, the uphill one under the soil: both follow the
     * ground and open at the surface.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesOnAHillsideOpenAtTheSurfaceUphillAndDownhill(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> Math.max(-6, Math.min(3, Math.floorDiv(x + z - 2 * MIDDLE, 3))));
    }

    /**
     * A termite nest on a low flat with a terrace 2 blocks high from 7 blocks out (the user's exact bug: the ground 2
     * blocks above a side hole instead of 1). The tunnels climb the terrace in one step and open on top of it.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesUpATerraceOpenOnTopOfIt(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> chebyshev(x, z) >= 7 ? 2 : 0);
    }

    /**
     * A termite nest at the foot of a cliff 5 blocks high from 7 blocks out, too high to tunnel up: the tunnels stop
     * short of it and open their holes at the cliff's foot.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesBelowACliffOpenAtItsFoot(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> chebyshev(x, z) >= 7 ? 5 : 0);
    }

    /**
     * A termite colony on a hillside (the ground rises a block every 3 blocks to the east, 3 blocks up and down from the
     * nest): termites build where they land, and the mound fills in round the slope (the user's decision of
     * 2026-10-14). The workers build the first two stages of the mound: on the low side its foot is filled down to the
     * ground, on the high side the hillside itself stands in for the mound's soil, and its hollows (the shaft, the
     * tunnels) are open there too. Wet soil sets at once, as in the flat test.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void termitesBuildTheirMoundRoundAHillside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        IntBinaryOperator rise = (x, z) -> Math.max(-3, Math.min(3, Math.floorDiv(x - MIDDLE, 3)));
        shapeGround(helper, 25, rise);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(SavannaEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 8; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 4, SURFACE, MIDDLE - 3 + i / 4);
            }
        });
        TestClock.still(helper, 0.0); // midnight: the mound is built at foraging time
        int[] stage = {1};
        int[] stageOneParts = {0};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null) {
                return;
            }
            if (colony.part(SavannaParts.MOUND).orElseThrow().getStage(level) != stage[0]) {
                colony.part(SavannaParts.MOUND).orElseThrow().setStage(level, stage[0]);
            }
            // Wet soil sets at once, the mound's and the footing under it.
            if (helper.getTick() % 20 == 0) {
                for (BlockPos pos : BlockPos.betweenClosed(colony.getCenter().offset(-8, -4, -8), colony.getCenter().offset(8, 8, 8))) {
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof com.antcolonies.savanna.block.TermiteMoundBlock && com.antcolonies.savanna.block.TermiteMoundBlock.isFinished(state)
                            && state.getValue(com.antcolonies.savanna.block.TermiteMoundBlock.AGE) == 0) {
                        level.setBlockAndUpdate(pos, state.setValue(com.antcolonies.savanna.block.TermiteMoundBlock.AGE, 1));
                    }
                }
            }
            String missing = unbuilt(level, colony);
            if (stage[0] == 1 && missing.isEmpty()) {
                stage[0] = 2;
                stageOneParts[0] = colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).size();
                SavannaMod.LOGGER.info("[hill-mound] tick {}: stage 1 built", helper.getTick());
            } else if (stage[0] == 2 && missing.isEmpty() && colony.part(SavannaParts.MOUND).orElseThrow().getStage(level) == 2
                    && colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).size() > stageOneParts[0]) {
                helper.assertTrue(level.getBlockState(colony.getCenter()).isAir(), "the entrance is not open");
                SavannaMod.LOGGER.info("[hill-mound] tick {}: stage 2 built", helper.getTick());
                helper.succeed();
                return;
            }
            if (helper.getTick() % 1000 == 0) {
                SavannaMod.LOGGER.info("[hill-mound] tick {}: stage {}, not built: {}", helper.getTick(), stage[0], missing);
            }
            helper.assertTrue(helper.getTick() < 38000, "stage " + stage[0] + " of the mound on the hillside is not built: " + missing);
        });
    }

    /**
     * The parts of the mound at its stage now that are not there yet (empty if it is built): a wall is finished mound
     * soil or the natural earth of the hillside, a tunnel is a tunnel block, an open part is air. Pieces are not checked.
     */
    private static String unbuilt(ServerLevel level, Colony colony) {
        StringBuilder missing = new StringBuilder();
        int count = 0;
        for (var entry : colony.part(SavannaParts.MOUND).orElseThrow().getParts(level).entrySet()) {
            BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            boolean ok = switch (entry.getValue()) {
                case WALL -> state.getBlock() instanceof com.antcolonies.savanna.block.TermiteMoundBlock && com.antcolonies.savanna.block.TermiteMoundBlock.isFinished(state)
                        || state.is(com.antcolonies.registry.ModTags.DIGGABLE);
                case TUNNEL -> state.getBlock() instanceof com.antcolonies.savanna.block.TermiteTunnelBlock;
                case OPEN -> state.isAir();
                case MOUTH, SPIRE, RAMP -> true;
            };
            if (!ok) {
                if (count++ < 8) {
                    missing.append(entry.getValue()).append(' ').append(pos.subtract(colony.getCenter()).toShortString())
                            .append(" is ").append(state.getBlock().getName().getString()).append("; ");
                }
            }
        }
        return count == 0 ? "" : count + " parts: " + missing;
    }

    /** How far the column (x, z) is from the arena's middle, the larger of the two distances (a square ring). */
    private static int chebyshev(int x, int z) {
        return Math.max(Math.abs(x - MIDDLE), Math.abs(z - MIDDLE));
    }

    /**
     * A termite colony in an arena shaped by {@code rise} (see {@link #shapeGround}): its two foraging holes are planned
     * at the surface, the workers dig both tunnels and open both holes.
     */
    private static void sideHolesOpenAtTheSurface(GameTestHelper helper, IntBinaryOperator rise) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        shapeGround(helper, 25, rise);
        int middleTop = SURFACE + rise.applyAsInt(MIDDLE, MIDDLE);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, middleTop, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                int x = MIDDLE - 3 + i;
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), x, SURFACE + rise.applyAsInt(x, MIDDLE - 2), MIDDLE - 2);
            }
        });
        boolean[] ready = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            helper.assertTrue(tunnels.size() == 2, "the nest has " + tunnels.size() + " foraging tunnels");
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                // (Checked on the blocks themselves: the arena may be turned, so the shape's x and z are not the world's.)
                BlockPos hole = tunnel.hole();
                helper.assertTrue(level.getBlockState(hole).is(Blocks.GRASS_BLOCK) && level.getBlockState(hole.above()).isAir(),
                        "the " + tunnel.side() + " hole " + hole.toShortString() + " is not at the surface: " + level.getBlockState(hole)
                                + " with " + level.getBlockState(hole.above()) + " over it");
            }
            // The shaft and rooms dug at once; the foraging tunnels are left for the workers.
            BlockPos bottom = colony.nest().getShaftBottom(level);
            for (BlockPos pos = colony.getCenter().below(); pos.getY() >= bottom.getY(); pos = pos.below()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
                chamber.cells().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            }
            colony.nest().getGardens(level).get(0).cells().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            // This scene is about where the two holes open on the slope, not which holes take their turn tonight (a
            // small colony opens only one of them): the arms and turns are switched off here (they have their own
            // proof over 400 landscapes, GalleryProofTest, and their own scene in TermiteForagingTests).
            com.antcolonies.savanna.colony.Galleries.of(colony).setSwitchForTests(false);
            TestClock.still(helper, 0.0); // midnight: foraging time
            ready[0] = true;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || !ready[0]) {
                return;
            }
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            boolean allOpen = true;
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                allOpen &= ForagingHoles.isDug(level, tunnel) && ForagingHoles.isOpen(level, tunnel.hole());
            }
            if (helper.getTick() % 1000 == 0) {
                SavannaMod.LOGGER.info("[hill-test] tick {}: {}", helper.getTick(), tunnels.stream().map(t -> t.tunnel().stream()
                        .filter(c -> com.antcolonies.colony.Nest.isDugOut(level, c)).count() + "/" + t.tunnel().size() + " dug, hole " + level.getBlockState(t.hole())).toList());
            }
            if (allOpen && colony.foragingHoles().waysOut(level).size() >= 2) {
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() < 19000, "the foraging tunnels were not dug and opened");
        });
    }
}
