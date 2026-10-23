package com.antcolonies.gametest;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;

/**
 * Helpers for spawning insects in the game tests, for every species (the split, step S4: each species' own lone insects
 * are in its tests' sign-up, {@code SavannaTests} and {@code DesertTests}).
 */
final class TestAnts {
    private TestAnts() {
    }

    /**
     * An insect of this body with no mind of its own, for a test to walk around by hand. It joins no colony: the tests
     * run side by side, and an insect that joined a neighbouring test's colony would spoil that test's headcount (a lone
     * queen with workers is not founding any more, so she never seals herself in).
     */
    static <T extends AbstractAntEntity> T lone(GameTestHelper helper, EntityType<T> body, int x, int y, int z) {
        T insect = helper.spawnWithNoFreeWill(body, x, y, z); // in the middle of the block, as before
        insect.keepOutOfColonies();
        return insect;
    }

    /** The same at an exact spot (not the middle of a block). */
    static <T extends AbstractAntEntity> T lone(GameTestHelper helper, EntityType<T> body, float x, float y, float z) {
        T insect = helper.spawnWithNoFreeWill(body, x, y, z);
        insect.keepOutOfColonies();
        return insect;
    }

    /** An insect of this body with no colony but with its own mind (it wanders, and so on), for a test to watch. */
    static <T extends AbstractAntEntity> T loneWithAMind(GameTestHelper helper, EntityType<T> body, int x, int y, int z) {
        T insect = helper.spawn(body, x, y, z);
        insect.keepOutOfColonies();
        return insect;
    }
}
