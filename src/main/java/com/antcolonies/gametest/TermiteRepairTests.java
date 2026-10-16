package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.registry.ModTags;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Termites keep their nest shut like the ants do, but with their own mound soil. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteRepairTests {
    /**
     * By day, once the first fungus garden is dug, the nest is broken open the way a player digging down to look would:
     * the royal chamber and the garden are opened to the sky (a pit from the surface down into each), and a garden's side
     * wall is cut. The workers pack every opening shut from inside with finished Termite Mound Soil (no Burrowed Dirt
     * anywhere), and the nest has no opening left. The middle hole, plugged by day, is mound soil too.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void termitesSealOpeningsInTheirNestWithMoundSoil(GameTestHelper helper) {
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
        List<BlockPos> holes = new ArrayList<>(); // the cut blocks that touch the nest: these must be plugged
        int groundTop = helper.absolutePos(new BlockPos(0, SURFACE - 1, 0)).getY();
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null) {
                return;
            }
            TestClock.still(helper, 12.0); // midday: everything is done from inside
            if (!holes.isEmpty()) {
                if (helper.getTick() % 1000 == 0) {
                    AntColoniesMod.LOGGER.info("[repair-test] tick {}: leaks {}, holes {}", helper.getTick(),
                            colony.walls().getLeaks(level), holes.stream().map(level::getBlockState).toList());
                }
                return;
            }
            List<NestPlan.Chamber> gardens = colony.nest().getGardens(level);
            if (gardens.isEmpty() || !gardens.get(0).cells().stream().noneMatch(cell -> level.getBlockState(cell).is(ModTags.DIGGABLE))) {
                return;
            }
            NestPlan.Chamber garden = gardens.get(0);
            NestPlan.Chamber royal = colony.nest().getHomeChamber(level);
            Set<BlockPos> planned = new HashSet<>();
            for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
                planned.addAll(chamber.cells());
            }
            for (NestPlan.Chamber room : gardens) {
                planned.addAll(room.cells()); // every garden the plan may ever dig, with its doorway: the cut wall must stay a wall
            }
            for (NestPlan.Chamber chamber : List.of(royal, garden)) {
                BlockPos top = chamber.center().above();
                holes.add(top);
                for (int y = top.getY(); y <= groundTop; y++) {
                    level.setBlock(new BlockPos(top.getX(), y, top.getZ()), Blocks.AIR.defaultBlockState(), 3);
                }
            }
            // A side wall of the garden, beside one of its corner cells (the middles of the walls may be doorways to later
            // gardens). It may lie between two rooms: the walls between rooms must be whole too.
            cut:
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int along : new int[] {-1, 1}) {
                    BlockPos wall = garden.center().relative(side, 2).relative(side.getClockWise(), along);
                    if (!planned.contains(wall)) {
                        holes.add(wall);
                        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
                        break cut;
                    }
                }
            }
            AntColoniesMod.LOGGER.info("[repair-test] tick {}: cut {} holes: {}", helper.getTick(), holes.size(), holes);
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && holes.size() == 3, "no colony or holes cut yet");
            for (BlockPos hole : holes) {
                BlockState state = level.getBlockState(hole);
                helper.assertTrue(state.is(ModBlocks.TERMITE_MOUND.get()) && TermiteMoundBlock.isFinished(state),
                        "the hole at " + hole + " holds " + state);
            }
            var leaks = colony.walls().getLeaks(level);
            helper.assertTrue(leaks.isEmpty(), "the nest still has openings: " + leaks);
            BlockState middle = level.getBlockState(colony.nest().getEntrance());
            helper.assertFalse(middle.is(ModBlocks.BURROWED_DIRT.get()), "the middle hole is plugged with Burrowed Dirt");
            AntColoniesMod.LOGGER.info("[repair-test] all sealed at tick {}", helper.getTick());
        });
    }
}
