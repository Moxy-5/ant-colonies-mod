package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.List;
import java.util.function.IntBinaryOperator;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colonies on hills and uneven ground (the user's decisions of 2026-10-14: one shared ground reader, every planner
 * tested on hill arenas). The arenas are shaped here: slopes, terraces, valleys and cliff edges.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class HillTests {
    /** Shapes the arena's ground: the top ground block of column (x, z) is {@code SURFACE - 1 + rise(x, z)}, grass on top. */
    static void shapeGround(GameTestHelper helper, int size, IntBinaryOperator rise) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int top = SURFACE - 1 + rise.applyAsInt(x, z);
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
                for (int y = top + 1; y < 16; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    /**
     * A termite nest in a hollow: the ground is flat round the nest and rises a block a column from 5 blocks out, up to 4
     * blocks higher, so the planned foraging holes (8 blocks out) lie 4 blocks under the surface (the user's bug: the
     * side holes on a hill were opened one block and stayed shut). The foraging tunnels follow the ground up, the
     * workers dig them (the last blocks upward) and both holes open at the real surface.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesInAHollowFollowTheGroundUpAndOpenAtTheSurface(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> Math.min(4, Math.max(0, Math.max(Math.abs(x - MIDDLE), Math.abs(z - MIDDLE)) - 4)));
    }

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
     * A termite nest on a ridge: the ground falls away on every side from 4 blocks out, a block a column, 4 blocks down.
     * Both tunnels run downhill and open at the surface below.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesOffARidgeOpenDownTheSlopes(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> -Math.min(4, Math.max(0, chebyshev(x, z) - 3)));
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
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, middleTop, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                int x = MIDDLE - 3 + i;
                helper.spawn(ModEntities.TERMITE_WORKER.get(), x, SURFACE + rise.applyAsInt(x, MIDDLE - 2), MIDDLE - 2);
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
            colony.setForagingTimeForTests(true);
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
                AntColoniesMod.LOGGER.info("[hill-test] tick {}: {}", helper.getTick(), tunnels.stream().map(t -> t.tunnel().stream()
                        .filter(c -> com.antcolonies.colony.Nest.isDugOut(level, c)).count() + "/" + t.tunnel().size() + " dug, hole " + level.getBlockState(t.hole())).toList());
            }
            if (allOpen && colony.foragingHoles().waysOut(level).size() >= 2) {
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() < 19000, "the foraging tunnels were not dug and opened");
        });
    }
}
