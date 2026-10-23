package com.antcolonies.desert.species;

import java.util.List;
import java.util.Optional;

import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfile.BroodNumbers;
import com.antcolonies.species.SpeciesProfile.ColonyNumbers;
import com.antcolonies.species.SpeciesProfile.FoodNumbers;
import com.antcolonies.species.SpeciesProfile.Founding;
import com.antcolonies.species.SpeciesProfile.LevelUnlock;
import com.antcolonies.species.SpeciesProfile.NestNumbers;
import com.antcolonies.species.SpeciesProfile.PlantFood;
import com.antcolonies.species.SpeciesProfile.QueenNumbers;
import com.antcolonies.species.SpeciesProfile.RefuseNumbers;
import com.antcolonies.species.SpeciesProfile.WorkerNumbers;

/**
 * The red harvester ant's numbers as built into the code: used only when its file is missing or broken (an error is
 * logged then). The real numbers are in {@code data/desertcolonies/species/red_harvester_ant.json}. Signed up with the
 * framework at start ({@code SpeciesProfiles.signUpBuiltIn}); until the split's clean-up (2026-10-23) they sat in the
 * framework's {@code SpeciesProfile} as every species' fallback.
 */
public final class HarvesterBuiltIn {
    public static final SpeciesProfile NUMBERS = new SpeciesProfile(
            new ColonyNumbers(60, List.of(3, 8, 12, 20)),
            new WorkerNumbers(24000, 36000, 72000, 72000, 192000, 480000),
            new QueenNumbers(20, 5, 5, 1200, 3600, 120000L),
            new BroodNumbers(10, 1200, 5, 48000L),
            new FoodNumbers(24, "colonyframework:seeds", List.of(new PlantFood("minecraft:short_grass", 10, false, 1)),
                    "minecraft:wheat_seeds", "desertcolonies:seed_pile", 60, 20, FoodNumbers.ALWAYS, 0, Optional.empty()),
            new RefuseNumbers(6000),
            new NestNumbers(4, true, true, List.of(), 0, 8, "desertcolonies:burrowed_dirt",
                    List.of(new LevelUnlock(1, 0), new LevelUnlock(6, 10), new LevelUnlock(12, 30), new LevelUnlock(20, 30))),
            Optional.empty(),
            Optional.of(new Founding(3, 400, 200, 8, 1, 2)),
            Optional.empty())
            .withParts(List.of("hill", "bakery", "trunk_trails", "patrol", "forager_spots", "door", "recruits"));

    private HarvesterBuiltIn() {
    }
}
