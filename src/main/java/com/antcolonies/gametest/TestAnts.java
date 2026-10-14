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

    /** A termite worker with no mind of its own and no colony, for a test to walk around by hand (see {@link #loneAnt}). */
    static com.antcolonies.entity.TermiteWorkerEntity loneTermite(GameTestHelper helper, int x, int y, int z) {
        com.antcolonies.entity.TermiteWorkerEntity termite = helper.spawnWithNoFreeWill(ModEntities.TERMITE_WORKER.get(), x, y, z);
        termite.keepOutOfColonies();
        return termite;
    }

    /** A termite soldier of this kind with no colony, with its own mind (it wanders, drums when told), for a test to watch. */
    static com.antcolonies.entity.TermiteSoldierEntity loneSoldier(GameTestHelper helper, boolean major, int x, int y, int z) {
        com.antcolonies.entity.TermiteSoldierEntity soldier = helper.spawn(major ? ModEntities.TERMITE_MAJOR_SOLDIER.get()
                : ModEntities.TERMITE_MINOR_SOLDIER.get(), x, y, z);
        soldier.keepOutOfColonies();
        return soldier;
    }

    /** The same at an exact spot (not the middle of a block). */
    static WorkerAntEntity loneAnt(GameTestHelper helper, float x, float y, float z) {
        WorkerAntEntity ant = helper.spawnWithNoFreeWill(ModEntities.WORKER_ANT.get(), x, y, z);
        ant.keepOutOfColonies();
        return ant;
    }
}
