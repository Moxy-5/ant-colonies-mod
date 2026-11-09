package com.antcolonies.desert.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.List;
import java.util.function.Consumer;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.desert.registry.DesertEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The scene kit's harvester scenes (test revamp R3, {@code docs/plans/test-revamp.md}): a red harvester ant nest
 * already dug, as the population, away and trail tests built it by hand. The timings are the hand-built ones (the
 * queen at once, workers after 5 ticks, the nest dug after 10), so no colony's life shifts.
 */
public final class HarvesterScenes {
    private HarvesterScenes() {
    }

    /** An ant queen on deep earth; after 10 ticks her colony has its nest dug, no hunger and the brain on, with nobody watching. */
    static void dugNest(GameTestHelper helper) {
        dugNest(helper, 0, colony -> {
        });
    }

    /**
     * An ant queen on deep earth with so many real workers (spawned after 5 ticks in rows of 5 west of her); after 10
     * ticks her colony (checked there) has its whole nest dug, no hunger and the brain on, with nobody watching, and
     * {@code then} gets it.
     */
    static void dugNest(GameTestHelper helper, int workers, Consumer<Colony> then) {
        List<BlockPos> spots = TestScenes.spots(workers, i -> new BlockPos(MIDDLE - 2 + i % 5, SURFACE, MIDDLE - 2 - i / 5));
        TestScenes.queenAndWorkers(helper, DesertEntities.QUEEN_ANT.get(), DesertEntities.WORKER_ANT.get(), spots, 10,
                HarvesterPopulationTests::antColony, colony -> {
                    helper.assertTrue(colony != null, "no ant colony");
                    colony.setHungerOn(false);
                    TestScenes.digNestAtOnce(helper.getLevel(), colony);
                    colony.population().setOnForTests(true);
                    colony.population().setViewersForTests(List.of());
                    then.accept(colony);
                });
    }
}
