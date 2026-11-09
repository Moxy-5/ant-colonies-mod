package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * The scene kit's termite scenes (test revamp R3, {@code docs/plans/test-revamp.md}): the scenes many termite tests
 * built by hand, each in one line. A test asks for one and then does its own checks; every scene keeps the timings the
 * hand-built ones had (the queen at once, the workers after 5 ticks, the colony looked up after 10), so no colony's life
 * shifts.
 */
public final class TermiteScenes {
    private TermiteScenes() {
    }

    /**
     * Deep ground, a termite queen at the middle, after 5 ticks a worker at each spot (the arena's own numbers), and
     * after 10 ticks {@code then} gets the test's termite colony ({@link TermiteMoundTests#colony}, fenced to the arena;
     * null if there is none, which the test checks as it always did).
     */
    static void colony(GameTestHelper helper, List<BlockPos> workers, Consumer<Colony> then) {
        colony(helper, workers, false, then);
    }

    /** {@link #colony(GameTestHelper, List, Consumer)}, with the king beside the queen too if asked (see {@link #start}). */
    static void colony(GameTestHelper helper, List<BlockPos> workers, boolean king, Consumer<Colony> then) {
        start(helper, workers, king);
        helper.runAfterDelay(10, () -> then.accept(TermiteMoundTests.colony(helper)));
    }

    /**
     * Only the start of {@link #colony}, for tests that look their colony up their own way: deep ground, a termite queen
     * at the middle, and after 5 ticks the king (if asked) 2 east and 1 south of her, then a worker at each spot.
     */
    static void start(GameTestHelper helper, List<BlockPos> workers, boolean king) {
        TestScenes.queenAndWorkers(helper, SavannaEntities.TERMITE_QUEEN.get(), king ? SavannaEntities.TERMITE_KING.get() : null,
                new BlockPos(MIDDLE + 2, SURFACE, MIDDLE + 1), SavannaEntities.TERMITE_WORKER.get(), workers);
    }

    /** {@code n} spots, the i-th where {@code spot} puts it (for {@link #colony}'s workers). */
    static List<BlockPos> spots(int n, IntFunction<BlockPos> spot) {
        return TestScenes.spots(n, spot);
    }

    /**
     * Every block of the shaft, the planned chambers, the first garden and the foraging tunnels dug out at once (moved
     * here from {@code TermiteSoldierTests}, unchanged).
     */
    static void digNestAtOnce(ServerLevel level, Colony colony) {
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos pos = colony.getCenter().below(); pos.getY() >= bottom.getY(); pos = pos.below()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        List<BlockPos> cells = new ArrayList<>();
        for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
            cells.addAll(chamber.cells());
        }
        cells.addAll(colony.nest().getGardens(level).get(0).cells());
        for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
            cells.addAll(tunnel.tunnel());
        }
        for (BlockPos pos : cells) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * A living termite mound of new land at this stage (1 to {@link TermiteMoundStructure#FULL_STAGE}) at (x, z) on the
     * arena's surface, with its colony, garden and galleries as new land makes them (the same pick new land's "full",
     * "half" and "young" give for stages 30, 16 and 6). Returns its colony, or null if it could not go in.
     */
    static Colony livingMound(GameTestHelper helper, int x, int z, int stage, long seed) {
        ServerLevel level = helper.getLevel();
        return NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(x, SURFACE, z)), livingPick(stage, seed));
    }

    /** New land's pick for a living mound at this stage (as {@code NewLandMounds.pickFor} makes for its three sizes). */
    static TermiteMoundStructure.Pick livingPick(int stage, long seed) {
        double age = 5 + Math.pow(stage, 1.6); // (the middle of TermiteMoundStructure.ageFor, as NewLandMounds.livingAt)
        return new TermiteMoundStructure.Pick(true, TermiteMoundStructure.sizeOf(stage), (double) stage / TermiteMoundStructure.FULL_STAGE,
                age, TermiteMoundStructure.NO_WOODS, false, false, seed);
    }
}
