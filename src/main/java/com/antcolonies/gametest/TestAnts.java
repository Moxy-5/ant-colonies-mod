package com.antcolonies.gametest;

import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.gametest.framework.GameTestHelper;

/** Helpers for spawning ants in the game tests. */
final class TestAnts {
    private TestAnts() {
    }

    /**
     * A worker ant with no mind of its own, for a test to walk around by hand. It joins no colony: the tests run side by
     * side, and an ant that joined a neighbouring test's colony would spoil that test's headcount (a lone queen with
     * workers is not founding any more, so she never seals herself in).
     */
    static WorkerAntEntity loneAnt(GameTestHelper helper, int x, int y, int z) {
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), x, y, z); // in the middle of the block, as before
        ant.keepOutOfColonies();
        return ant;
    }

    /** The same at an exact spot (not the middle of a block). */
    static WorkerAntEntity loneAnt(GameTestHelper helper, float x, float y, float z) {
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), x, y, z);
        ant.keepOutOfColonies();
        return ant;
    }
}
