package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.MatabeleColonies;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.colony.MoundPlan;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;
import com.antcolonies.savanna.worldgen.MoundPlaces;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Matabele colonies in new land (M8 step 1f): a dead mound handed over by world generation (queued, as new land does)
 * gets a Matabele colony when its dice say so and a living termite mound stands within reach (M8 step 3F, F4), the queen in her room under the hump, 100 to 2,300 workers inside as
 * numbers who come out as real ants up to the cap, a day's prey on the pile; a dead mound whose dice say no gets none;
 * the dice give about two in three and the sizes lean small; and the command makes a colony of any real size, the
 * workers past the cap numbers.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleNewLandTests {
    @GameTest(template = "arena", timeoutTicks = 900, skyAccess = true)
    public static void aDeadMoundOfNewLandGetsAMatabeleColony(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        cornerGround(helper);
        checkTheDice(helper);
        long yes = 1, no = 1;
        while (!MatabeleColonies.wouldSettle(level, yes)) {
            yes++;
        }
        while (MatabeleColonies.wouldSettle(level, no)) {
            no++;
        }
        // A dead mound whose dice say no: no Matabele in it.
        NewLandMounds book = NewLandMounds.get(level);
        BlockPos middle = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        helper.assertTrue(MatabeleColonies.inNewLand(level, middle, no, false) == null, "a dead mound whose dice say no got a Matabele colony");
        // M8 step 3F, F4: Matabele settle only near living termites, so a young living mound stands in the corner, about
        // 57 blocks off (its foot within 70 of the dead one).
        BlockPos living = helper.absolutePos(new BlockPos(MIDDLE + 40, SURFACE, MIDDLE + 40));
        helper.assertTrue(book.put(level, living, NewLandMounds.pickFor("young", 0, 3L)) != null, "no living mound put in the corner");
        helper.assertTrue(NewLandMounds.livingMoundNear(level, middle), "the living mound in the corner is not counted near the dead one");
        // The one whose dice say yes, the way world generation hands it over.
        NewLandMounds.queue(level, middle, NewLandMounds.pickFor("dead", 30, yes));
        boolean[] checked = new boolean[1];
        helper.succeedWhen(() -> {
            Colony colony = matabeleNear(level, middle);
            if (colony == null) {
                MoundPlan.awaitGrowingForTests(10_000); // (the test server's ticks outrun the second of growing: BUGS 73)
            }
            helper.assertTrue(colony != null, "no Matabele colony in the dead mound yet");
            int workers = colony.getCount(Caste.WORKER);
            if (!checked[0]) {
                checked[0] = true;
                colony.population().setOnForTests(true); // (off on the test server unless a test turns it on)
                helper.assertTrue(MatabeleNest.IN_A_MOUND.equals(colony.nest().site()), "the new-land colony settled " + colony.nest().site());
                helper.assertTrue(workers >= MatabeleColonies.FEWEST && workers <= MatabeleColonies.MOST, workers + " workers, outside 100 to 2,300");
                helper.assertTrue(colony.getQueen(level) instanceof MatabeleQueenEntity queen && queen.getY() < colony.getCenter().getY(),
                        "the queen is not down in the nest under the entrance " + colony.getCenter().toShortString());
                PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElseThrow();
                helper.assertTrue(pile.edible() > 0, "a new-land colony with no prey on its pile");
                helper.assertTrue(pile.edible() <= MatabeleColonies.dayOfFood(workers), pile.edible() + " layers of prey, more than a day's");
                // R8's balance pass: an old colony of the land, its scouts knowing their whole foraging range from the start
                Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
                helper.assertTrue(raids.isSettled(), "a new-land colony is not settled");
                helper.assertTrue(raids.range() == raids.numbers().settledRange(),
                        "a new-land colony's scouts start at " + raids.range() + " blocks, not the settled range " + raids.numbers().settledRange());
            }
            // The numbers come out as real ants, up to the cap; the rest stay numbers.
            List<MatabeleEntity> real = TestIsolation.insectsOf(level, colony, MatabeleEntity.class);
            int cap = colony.profile().colony().realCap();
            helper.assertTrue(real.size() <= cap, real.size() + " real workers, more than the cap " + cap);
            helper.assertTrue(real.size() + colony.population().inside(Caste.WORKER) == workers,
                    real.size() + " real and " + colony.population().inside(Caste.WORKER) + " inside do not make the " + workers + " workers");
            helper.assertTrue(real.size() >= Math.min(10, workers), "only " + real.size() + " real workers out yet");
        });
    }

    /** The command: a colony of the real smallest (100) and largest (2,300), every worker past the cap a number. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void theCommandMakesAMatabeleColonyOfAnyRealSize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        cornerGround(helper);
        helper.assertTrue(NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)),
                NewLandMounds.pickFor("dead", 30, 5L)) != null, "no dead mound put in for the big colony");
        helper.assertTrue(NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE + 40, SURFACE, MIDDLE + 40)),
                NewLandMounds.pickFor("dead", 30, 6L)) != null, "no dead mound put in for the small colony");
        List<Colony> made = new ArrayList<>();
        made.add(MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)), MatabeleColonies.MOST));
        made.add(MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 31, SURFACE, MIDDLE + 40)), MatabeleColonies.FEWEST));
        helper.runAfterDelay(20, () -> {
            int[] sizes = {MatabeleColonies.MOST, MatabeleColonies.FEWEST};
            for (int i = 0; i < 2; i++) {
                Colony colony = made.get(i);
                helper.assertTrue(colony != null, "the command made no colony of " + sizes[i]);
                int cap = colony.profile().colony().realCap();
                helper.assertTrue(colony.getCount(Caste.WORKER) == sizes[i], colony.getCount(Caste.WORKER) + " workers, not " + sizes[i]);
                int real = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).size();
                helper.assertTrue(real == cap, real + " real workers round the queen, not " + cap);
                helper.assertTrue(colony.population().inside(Caste.WORKER) == sizes[i] - cap,
                        colony.population().inside(Caste.WORKER) + " numbers inside, not " + (sizes[i] - cap));
                // R8's balance pass: the command's colony is new to the place, its scouts starting at the first range
                Raids raids = colony.part(SavannaParts.RAIDS).orElseThrow();
                helper.assertTrue(!raids.isSettled() && raids.range() == raids.numbers().firstRange(),
                        "the command's colony starts its scouts at " + raids.range() + " blocks, not the first range");
            }
            helper.succeed();
        });
    }

    /**
     * BUGS 104: in real savanna land (Minecraft's own overworld terrain, made from the noise for 5 seeds, 8 stretches of
     * savanna each, no chunks needed) no dead mound whose dice say Matabele is turned away for having no living termite
     * mound within reach (F4), a fair share of the places hold a mound, about a quarter of the mounds are dead, and mounds
     * still stand at least 40 apart. The user's world (2026-11-05): 12 of 17 such dead mounds were turned away (only about
     * 1 place in 5 held a mound on its slopes and by its lakes). The land is fixed by the seeds, so the numbers are the same
     * every run. Its own batch: it holds the server about 20 seconds working out ground heights.
     *
     * <p><b>The commonness guard</b> (the user, 2026-11-06, "uncommon, not rare"; {@code docs/plans/matabele-commonness.md}):
     * Matabele dead mounds stay between {@link #FEWEST_MATABELE} and {@link #MOST_MATABELE} of the mounds (about 1 in 4 to
     * 5). Twice before a change to where mounds stand (spacing, ground rules) made them rare without a test failing; now
     * such a change fails here, and the fix is in the mound numbers' {@code matabele} block
     * ({@code worldgen/structure/termite_mound.json}: {@code share}, {@code living_near}).
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "savanna_survey")
    public static void deadMoundsOfRealSavannaHaveLivingTermitesInReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!(level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(MoundPlaces.STRUCTURE) instanceof TermiteMoundStructure mounds)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("no termite mound structure");
        }
        long[] seeds = {-3635740773639857461L, 1L, 20261105L, 424242L, 987654321L};
        int[] t = new int[7];
        long start = System.nanoTime();
        for (long seed : seeds) {
            MoundPlaces.Land land = realLand(level, seed, mounds);
            int[] r = survey(land, mounds.numbers(), savannaPlaces(land, mounds.numbers().places()), helper);
            for (int i = 0; i < t.length; i++) {
                t[i] += r[i];
            }
        }
        String report = String.format("%d savanna places, %d hold a mound, %d dead, %d of them Matabele (1 mound in %.1f), %d turned away,"
                + " %d Matabele dead mounds alone living instead, %d mounds nearer than 40, %d ms", t[0], t[1], t[2], t[3],
                t[1] / (double) Math.max(1, t[3]), t[4], t[6], t[5], (System.nanoTime() - start) / 1_000_000);
        SavannaMod.LOGGER.info("[new-land] the savanna survey (BUGS 104): {}", report);
        helper.assertTrue(t[0] >= 300, "too few savanna places in the survey: " + report);
        helper.assertTrue(t[1] >= 0.28 * t[0], "too few places hold a mound (slopes up to 6 take one): " + report);
        double dead = t[2] / (double) t[1];
        helper.assertTrue(dead >= 0.2 && dead <= 0.4, "dead mounds not about a quarter of them: " + report);
        helper.assertTrue(t[4] == 0, "Matabele dead mounds turned away at put time (world making and the server's check disagree): " + report);
        double matabele = t[3] / (double) t[1];
        helper.assertTrue(matabele >= FEWEST_MATABELE, String.format("Matabele dead mounds too rare (%.0f%% of mounds, want at least %.0f%%;"
                + " look at the share and living_near in termite_mound.json's matabele block, or what changed where mounds stand): %s",
                100 * matabele, 100 * FEWEST_MATABELE, report));
        helper.assertTrue(matabele <= MOST_MATABELE, String.format("Matabele dead mounds too common (%.0f%% of mounds, want at most %.0f%%): %s",
                100 * matabele, 100 * MOST_MATABELE, report));

        // BUGS 162: the user plays single-biome savanna worlds, rugged land with mounds about 150 apart; the same band there
        long[] singleSeeds = {-5841907898462156635L, 1L};
        int[] u = new int[7];
        for (long seed : singleSeeds) {
            MoundPlaces.Land land = singleBiomeSavanna(level, seed, mounds);
            int[] r = survey(land, mounds.numbers(), savannaPlaces(land, mounds.numbers().places()), helper);
            for (int i = 0; i < u.length; i++) {
                u[i] += r[i];
            }
        }
        String single = String.format("single-biome savanna: %d places, %d hold a mound, %d dead, %d of them Matabele (1 mound in %.1f),"
                + " %d turned away, %d mounds nearer than 40", u[0], u[1], u[2], u[3], u[1] / (double) Math.max(1, u[3]), u[4], u[5]);
        SavannaMod.LOGGER.info("[new-land] the savanna survey (BUGS 162): {}", single);
        helper.assertTrue(u[4] == 0, "Matabele dead mounds turned away at put time: " + single);
        double singleShare = u[3] / (double) Math.max(1, u[1]);
        helper.assertTrue(singleShare >= FEWEST_MATABELE && singleShare <= MOST_MATABELE, String.format("Matabele dead mounds of a"
                + " single-biome savanna outside %.0f%% to %.0f%% of mounds (%.0f%%): %s", 100 * FEWEST_MATABELE, 100 * MOST_MATABELE,
                100 * singleShare, single));
        helper.assertTrue(u[3] >= 0.75 * u[2], "most dead mounds should hold Matabele (the dice share), not stand empty: " + single);
        helper.succeed();
    }

    /** A single-biome savanna world for a seed (the user's way to play, BUGS 162): the overworld's terrain, all of it savanna. */
    private static MoundPlaces.Land singleBiomeSavanna(ServerLevel level, long seed, TermiteMoundStructure mounds) {
        var registries = level.registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BiomeSource savanna = new net.minecraft.world.level.biome.FixedBiomeSource(
                registries.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA));
        RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
        return new MoundPlaces.Land(seed, new NoiseBasedChunkGenerator(savanna, settings), level, random, mounds.biomes());
    }

    /** The commonness band of Matabele dead mounds among all mounds of new land (the user, 2026-11-06: uncommon, not rare). */
    static final double FEWEST_MATABELE = 0.18, MOST_MATABELE = 0.30;

    /** Minecraft's overworld for a seed, as the noise makes it (no chunks), with the mounds' biomes. */
    private static MoundPlaces.Land realLand(ServerLevel level, long seed, TermiteMoundStructure mounds) {
        var registries = level.registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
        RandomState random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), seed);
        return new MoundPlaces.Land(seed, generator, level, random, mounds.biomes());
    }

    /** The places of the pattern in up to 4 stretches of savanna (192 blocks round a savanna spot, 600 apart). */
    private static List<MoundPlaces.Place> savannaPlaces(MoundPlaces.Land land, MoundPlaces.Numbers numbers) {
        List<BlockPos> spots = new ArrayList<>();
        for (int x = -4096; x <= 4096 && spots.size() < 8; x += 160) {
            for (int z = -4096; z <= 4096 && spots.size() < 8; z += 160) {
                BlockPos spot = new BlockPos(x, 0, z);
                if (inSavanna(land, x, z) && spots.stream().allMatch(s -> s.distSqr(spot) > 500 * 500)) {
                    spots.add(spot);
                }
            }
        }
        List<MoundPlaces.Place> out = new ArrayList<>();
        for (BlockPos spot : spots) {
            for (MoundPlaces.Place place : MoundPlaces.near(land.seed(), spot.getX(), spot.getZ(), 192, numbers)) {
                if (inSavanna(land, place.middle().getX(), place.middle().getZ())) {
                    out.add(place);
                }
            }
        }
        return out;
    }

    private static boolean inSavanna(MoundPlaces.Land land, int x, int z) {
        return land.biomes().contains(land.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(70),
                QuartPos.fromBlock(z), land.random().sampler()));
    }

    /**
     * Places, held, dead held, of those with Matabele dice, those turned away by F4 (no living mound in reach), feet
     * nearer than 40 to another (each fails the test), and dead mounds with Matabele dice living instead (BUGS 104).
     */
    private static int[] survey(MoundPlaces.Land land, TermiteMoundStructure.Numbers numbers, List<MoundPlaces.Place> places, GameTestHelper helper) {
        int[] out = new int[7];
        List<BlockPos> feet = new ArrayList<>();
        for (MoundPlaces.Place place : places) {
            out[0]++;
            BlockPos foot = MoundPlaces.holds(land, place, numbers.places());
            if (foot == null) {
                continue;
            }
            out[1]++;
            feet.add(foot);
            TermiteMoundStructure.Pick pick = TermiteMoundStructure.pickAt(numbers, land, place,
                    MoundPlaces.sizeQuantile(land, place, numbers.places()));
            if (pick.living()) {
                out[6] += MoundPlaces.living(land.seed(), place, numbers.deadShare()) ? 0 : 1;
                continue;
            }
            out[2]++;
            if (MatabeleColonies.wouldSettle(pick.seed(), numbers.matabele().share())) {
                out[3]++;
                if (!NewLandMounds.livingPlaceNear(land, numbers, foot)) {
                    out[4]++;
                }
            }
        }
        for (int i = 0; i < feet.size(); i++) {
            for (int j = i + 1; j < feet.size(); j++) {
                double dx = feet.get(i).getX() - feet.get(j).getX(), dz = feet.get(i).getZ() - feet.get(j).getZ();
                if (dx * dx + dz * dz < NewLandMounds.KEEP_APART * NewLandMounds.KEEP_APART) {
                    out[5]++;
                    helper.assertTrue(false, "two mounds " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " apart at "
                            + feet.get(i).toShortString() + " and " + feet.get(j).toShortString());
                }
            }
        }
        return out;
    }

    /** The dice give the data file's share of dead mounds a colony; the sizes run 100 to 2,300, half under about 480. */
    private static void checkTheDice(GameTestHelper helper) {
        double share = MatabeleColonies.share(helper.getLevel());
        int rolls = 3000, yes = 0, small = 0;
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < rolls; i++) {
            yes += MatabeleColonies.wouldSettle(random.nextLong(), share) ? 1 : 0;
            int size = MatabeleColonies.sizeFor(random);
            helper.assertTrue(size >= MatabeleColonies.FEWEST && size <= MatabeleColonies.MOST, "a colony of " + size);
            small += size < 480 ? 1 : 0;
        }
        helper.assertTrue(Math.abs(yes / (double) rolls - share) < 0.04, yes + " colonies in " + rolls + " dead mounds");
        helper.assertTrue(Math.abs(small / (double) rolls - 0.5) < 0.04, small + " of " + rolls + " colonies under 480");
    }

    /** Soil in the arena's far corner for the second dead mound (past the deep ground). */
    private static void cornerGround(GameTestHelper helper) {
        for (int x = MIDDLE + 26; x < 65; x++) {
            for (int z = MIDDLE + 26; z < 65; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
    }

    private static Colony matabeleNear(ServerLevel level, BlockPos middle) {
        return ColonyManager.get(level).getColonies().stream()
                .filter(c -> MatabeleEntity.SPECIES.equals(c.getSpecies()) && Math.abs(c.getCenter().getX() - middle.getX()) <= 3
                        && Math.abs(c.getCenter().getZ() - middle.getZ()) <= 3).findFirst().orElse(null);
    }
}
