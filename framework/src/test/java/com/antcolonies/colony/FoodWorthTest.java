package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How the foragers weigh a food plant (see {@link FoodStore#worth}): the food left on it against the walk. */
class FoodWorthTest {
    @Test
    void aHayBaleFarOutBeatsGrassRightByTheHole() {
        assertTrue(FoodStore.worth(48, 11, 0) > FoodStore.worth(4, 1, 0));
        assertTrue(FoodStore.worth(48, 24, 0) > FoodStore.worth(4, 1, 0)); // anywhere in the food radius
    }

    @Test
    void aHayBaleNearlyUsedUpIsWorthNoMoreThanGrassNearer() {
        assertTrue(FoodStore.worth(4, 10, 0) < FoodStore.worth(4, 3, 0)); // 4 pieces left on the hay, 10 blocks out
        assertTrue(FoodStore.worth(2, 10, 0) < FoodStore.worth(4, 10, 0));
    }

    @Test
    void ofTwoEqualPlantsTheNearerOneWins() {
        assertTrue(FoodStore.worth(4, 3, 0) > FoodStore.worth(4, 8, 0));
        assertTrue(FoodStore.worth(4, 5, 4) > FoodStore.worth(4, 5, 100)); // a tie: the one nearer the ant
    }
}
