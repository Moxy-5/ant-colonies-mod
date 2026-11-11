package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.antcolonies.entity.Wounds;
import com.antcolonies.fight.FightNumbers;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.species.SpeciesProfile;
import com.google.gson.JsonParser;

/**
 * Test revamp R5: the Matabele raid rules as quick tests with no Minecraft, on the numbers in its own species file: the
 * raid day's count (M8 step 3e: 1 raid a day when fed, up to 4 when starving; real 1 to 4), the raid's size (3d: 30 to
 * 75 percent of the colony, Yusuf 2026), the catch the raiders going as numbers bring home (3d), the scouts'
 * choosiness (R5: richer the farther, poorer the hungrier, Frank and Linsenmair 2017) and the wound care odds (step 5:
 * Frank et al. 2017, 2023). Each rule is the plain method the game code itself calls.
 */
class RaidRulesTest {
    private static SpeciesProfile matabele;

    @BeforeAll
    static void readTheFile() throws Exception {
        SavannaNumbers.signUp();
        try (InputStream in = RaidRulesTest.class.getResourceAsStream("/data/antcolonies/species/megaponera_analis.json")) {
            assertNotNull(in, "no Matabele species file");
            matabele = SpeciesProfile.read(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .getOrThrow(error -> new AssertionError("megaponera_analis: " + error));
        }
    }

    private static int mostRaidsADay() {
        return matabele.section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT).mostRaidsADay();
    }

    private static SavannaNumbers.ColumnNumbers column() {
        return matabele.section(SavannaNumbers.COLUMN).orElseThrow();
    }

    private static SavannaNumbers.CatchNumbers caught() {
        return matabele.section(SavannaNumbers.CATCH).orElse(SavannaNumbers.CatchNumbers.DEFAULT);
    }

    @Test
    void theRaidDayIsOneRaidWhenFedUpToFourWhenStarving() {
        int most = mostRaidsADay();
        assertEquals(4, most, "real colonies raid 1 to 4 times a day");
        assertEquals(most, Raids.raidsWanted(most, 0.0), "a starving colony raids the most");
        assertEquals(1, Raids.raidsWanted(most, 1.0), "a fed colony still raids once");
        int last = Integer.MAX_VALUE;
        for (double fed = 0.0; fed <= 1.0001; fed += 0.01) {
            int wanted = Raids.raidsWanted(most, fed);
            assertTrue(wanted >= 1 && wanted <= most, "fed " + fed + ": " + wanted + " raids");
            assertTrue(wanted <= last, "a better fed colony wants more raids at fed " + fed);
            last = wanted;
        }
    }

    @Test
    void howFedIsTheEdibleTermitesAnAnt() {
        assertEquals(0.0, Raids.fedShare(0, 500));
        assertEquals(0.5, Raids.fedShare(250, 500));
        assertEquals(1.0, Raids.fedShare(900, 500), "more than a termite an ant is fully fed");
        assertEquals(1.0, Raids.fedShare(3, 0), "no workers: counted as one");
    }

    @Test
    void aRaidIsAShareOfTheColonyBiggerWhenHungryAndForRicherFinds() {
        SavannaNumbers.ColumnNumbers numbers = column();
        int mostRaid = caught().mostRaid();
        double rich = numbers.richWorth();
        // (Honest sizes, 2026-11-11: a colony is at most 100.) A starving colony of 80 sends 75 percent to a rich find, a fed one 30 percent (Yusuf 2026: 30 to 75).
        assertEquals((int) Math.floor(80 * numbers.mostShare()), Raids.raidSize(80, 0.0, rich, numbers, mostRaid));
        assertEquals((int) Math.floor(80 * numbers.leastShare()), Raids.raidSize(80, 1.0, rich, numbers, mostRaid));
        // Never more than the most a raid can be, never fewer than the least (if the colony can spare them).
        assertEquals(mostRaid, Raids.raidSize(2300, 0.0, rich, numbers, mostRaid));
        assertEquals(numbers.leastRaid(), Raids.raidSize(1000, 1.0, 0.0, numbers, mostRaid));
        for (int workers : new int[] {1, 5, 40, 100, 300, 1000, 2300}) {
            int last = 0;
            for (double worth = 0.0; worth <= rich * 2; worth += 0.5) {
                int size = Raids.raidSize(workers, 0.5, worth, numbers, mostRaid);
                assertTrue(size >= 1 && size <= Math.max(1, workers), workers + " workers, worth " + worth + ": a raid of " + size);
                assertTrue(size >= last, "a richer find gets a smaller raid: " + workers + " workers, worth " + worth);
                last = size;
            }
            assertTrue(Raids.raidSize(workers, 0.0, rich, numbers, mostRaid) >= Raids.raidSize(workers, 1.0, rich, numbers, mostRaid),
                    "a hungry colony of " + workers + " sends fewer than a fed one");
        }
    }

    @Test
    void theNumbersBringHomeTheirShareOfTermitesEach() {
        SavannaNumbers.CatchNumbers numbers = caught();
        assertEquals(0, RaidCatch.couldCarry(0, numbers, 0.5));
        assertEquals((int) Math.round(40 * numbers.perRaider()), RaidCatch.couldCarry(40, numbers, 0.5), "on average");
        assertEquals((int) Math.round(40 * numbers.perRaider() * 0.75), RaidCatch.couldCarry(40, numbers, 0.0), "a quarter less");
        assertEquals((int) Math.round(40 * numbers.perRaider() * 1.25), RaidCatch.couldCarry(40, numbers, 1.0), "a quarter more");
        assertEquals(numbers.mostCatch(), RaidCatch.couldCarry(100000, numbers, 1.0), "never more than the most a raid brings");
    }

    @Test
    void scoutsWantRicherFindsFartherOutAndTakePoorerOnesWhenHungry() {
        SavannaNumbers.ChooseNumbers choosing = matabele.section(SavannaNumbers.CHOOSING).orElse(SavannaNumbers.ChooseNumbers.DEFAULT);
        for (double fed : new double[] {0.0, 0.5, 1.0}) {
            double last = -1;
            for (double distance = 0; distance <= 96; distance += 4) {
                double need = choosing.need(distance, fed);
                assertTrue(need > last, "a farther find needs no more worth at " + distance + " blocks");
                last = need;
            }
        }
        for (double distance = 0; distance <= 96; distance += 8) {
            assertTrue(choosing.need(distance, 0.0) < choosing.need(distance, 1.0), "hungry scouts are no less choosy at " + distance);
        }
        // Balance (BUGS 191): a by-day feeding place (worth 3 a termite) is worth a raid anywhere a scout reaches (96),
        // even for a fed colony.
        double placeWorth = 3.0 * choosing.byDayTermites();
        assertTrue(placeWorth >= choosing.need(96, 1.0), "a feeding place, worth " + placeWorth + ", is too poor at 96 blocks: needs "
                + choosing.need(96, 1.0));
    }

    @Test
    void woundCareCutsDeathsAsInThePapers() {
        FightNumbers.Rescue n = FightNumbers.of(matabele).flatMap(FightNumbers::rescue).orElseThrow();
        // Frank et al. 2017: untreated, about 80 percent infected; 2023: infected deaths about 90 percent untreated, 20 treated.
        assertEquals(0.8, Wounds.infectChance(0, n));
        assertTrue(Wounds.infectChance(1, n) < Wounds.infectChance(0, n));
        assertEquals(0.9, Wounds.deathChance(false, n));
        assertEquals(0.2, Wounds.deathChance(true, n));
        double untended = Wounds.infectChance(0, n) * Wounds.deathChance(false, n);
        double cared = Wounds.infectChance(n.licks(), n) * Wounds.deathChance(true, n);
        assertTrue(untended >= 0.7 && cared <= 0.1, "untended deaths " + untended + ", cared-for " + cared);
    }
}
