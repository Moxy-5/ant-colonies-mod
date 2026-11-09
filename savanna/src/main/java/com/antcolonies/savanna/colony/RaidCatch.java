package com.antcolonies.savanna.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * M8 step 3d, the termites' losses ({@code docs/plans/m8-matabele.md}; the user's pick 2026-11-06, "count stands for
 * many"). Real Megaponera raids carry home about 200 to 1,000 termites with 150 to 550 ants (Yusuf 2026, Table 1), from
 * Macrotermes colonies of 1 to 2 million; the game's termite colonies count 50 to 700, so each counted termite stands for
 * many real ones (the termite profile's {@code colony.stands_for}, {@code Population.loseToHunters}).
 *
 * <p>The termites feeding under the mud at a spot are numbers in their colony's galleries. The raiders who go as numbers
 * (the column's numbers till step 4 draws them, and every raider of a raid out of sight, {@link RaidsAway}) each take about
 * {@code raids.catch.per_raider} of them, a quarter more or less, at most {@code most_catch} a raid and never more than
 * {@code raids.away.most_take_share} of the real termites the colony's workers inside stand for; only from a termite
 * colony that still feeds there (alive, not keeping off the spot). The termite colony's headcount loses one in
 * {@code stands_for} of the termites taken; the termites remember the attack (their danger map, brain step 4g), so the
 * spot dries up for a few days. The real raiders carry the real termites they sting, as before.
 */
public final class RaidCatch {
    private RaidCatch() {
    }

    static SavannaNumbers.CatchNumbers numbers(Colony matabele) {
        return matabele.profile().section(SavannaNumbers.CATCH).orElse(SavannaNumbers.CatchNumbers.DEFAULT);
    }

    /**
     * The Macrotermes colony feeding at this spot: the one whose mound's middle is nearest it within {@code reach} blocks
     * and still has workers, or null.
     */
    @Nullable
    static Colony termitesAt(ServerLevel level, BlockPos at, int reach) {
        Colony best = null;
        for (Colony termites : Raids.termiteColonies(level, at, reach)) {
            if (SavannaSpecies.MACROTERMES.equals(termites.getSpecies()) && termites.getCount(Caste.WORKER) > 0
                    && (best == null || termites.getCenter().distSqr(at) < best.getCenter().distSqr(at))) {
                best = termites;
            }
        }
        return best;
    }

    /** How many termites this many raiders going as numbers could carry home: {@code per_raider} each, a quarter more or less, at most {@code most_catch}. */
    static int couldCarry(Colony matabele, int raiders, RandomSource random) {
        if (raiders <= 0) {
            return 0;
        }
        return couldCarry(raiders, numbers(matabele), random.nextDouble());
    }

    /** {@link #couldCarry(Colony, int, RandomSource)} with its dice roll (0 to 1) handed in (test revamp R5). */
    static int couldCarry(int raiders, SavannaNumbers.CatchNumbers numbers, double roll) {
        if (raiders <= 0) {
            return 0;
        }
        int wanted = (int) Math.round(raiders * numbers.perRaider() * (0.75 + 0.5 * roll));
        return Math.max(0, Math.min(numbers.mostCatch(), wanted));
    }

    /** The most real termites a raid may take from this colony: {@code most_take_share} of what its workers inside stand for. */
    static int mostToTake(Colony matabele, Colony termites) {
        double share = matabele.profile().section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT).mostTakeShare();
        double real = (double) termites.population().inside(Caste.WORKER) * termites.population().standsFor() * share;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(real));
    }

    /** The raid takes {@code real} termite workers: the termite colony's count loses one in its {@code stands_for} of them. Returns the counted workers lost. */
    static int take(ServerLevel level, Colony termites, int real) {
        return termites.population().loseToHunters(level, Caste.WORKER, real, AwayReport.What.TAKEN_BY_RAIDERS);
    }
}
