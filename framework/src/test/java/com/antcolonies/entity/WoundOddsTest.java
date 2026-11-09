package com.antcolonies.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.antcolonies.fight.FightNumbers;

/**
 * Test revamp R5: the wound care odds as a quick rule test with no Minecraft (M8 step 5, {@link Wounds}): a licked
 * wound turns infected with the licked chance, an unlicked one with the untended; an infected wound kills with the
 * treated chance once dabbed, else the untreated. The species' real numbers are checked in its own mod.
 */
class WoundOddsTest {
    private static FightNumbers.Rescue numbers(double infectUntended, double infectLicked, double deathUntreated, double deathTreated) {
        return new FightNumbers.Rescue(6.0, 4, 4, 200, 300, 3, 200, 6000, 11000, 36000, 24000,
                infectUntended, infectLicked, deathUntreated, deathTreated);
    }

    @Test
    void oneLickIsEnoughForTheLickedOdds() {
        FightNumbers.Rescue n = numbers(0.8, 0.2, 0.9, 0.2);
        assertEquals(0.8, Wounds.infectChance(0, n));
        assertEquals(0.2, Wounds.infectChance(1, n));
        assertEquals(0.2, Wounds.infectChance(3, n));
    }

    @Test
    void aDabCutsTheDeathOdds() {
        FightNumbers.Rescue n = numbers(0.8, 0.2, 0.9, 0.2);
        assertEquals(0.9, Wounds.deathChance(false, n));
        assertEquals(0.2, Wounds.deathChance(true, n));
    }

    @Test
    void careNeverMakesAWoundWorse() {
        // With any numbers where care helps, the full course of care (licked, then dabbed if infected) kills fewer than none.
        FightNumbers.Rescue n = numbers(0.8, 0.2, 0.9, 0.2);
        double untended = Wounds.infectChance(0, n) * Wounds.deathChance(false, n);
        double cared = Wounds.infectChance(3, n) * Wounds.deathChance(true, n);
        assertTrue(cared < untended, "cared-for deaths " + cared + " against untended " + untended);
    }
}
