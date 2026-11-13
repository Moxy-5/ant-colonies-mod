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
import net.minecraft.world.level.levelgen.Heightmap;
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
                int cap = Math.min(sizes[i], colony.profile().colony().realCap()); // (honest sizes: a small colony is all real)
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
        int[] t = new int[16];
        long start = System.nanoTime();
        for (long seed : seeds) {
            MoundPlaces.Land land = realLand(level, seed, mounds);
            int[] r = survey(land, mounds.numbers(), savannaPlaces(land, mounds.numbers().places()), helper);
            for (int i = 0; i < t.length; i++) {
                t[i] = i == 14 ? Math.max(t[i], r[i]) : t[i] + r[i]; // (14: the farthest, not a sum)
            }
        }
        String report = report("real savanna", t) + String.format(", %d ms", (System.nanoTime() - start) / 1_000_000);
        SavannaMod.LOGGER.info("[new-land] the savanna survey: {}", report);
        helper.assertTrue(t[0] >= 300, "too few savanna places in the survey: " + report);
        helper.assertTrue(t[1] >= 0.28 * t[0], "too few places hold a mound (slopes up to 6 take one): " + report);
        double dead = t[2] / (double) t[1];
        helper.assertTrue(dead >= 0.25 && dead <= 0.45, "dead mounds not about a third of them: " + report);
        double looksDead = t[7] / (double) t[1];
        helper.assertTrue(looksDead >= FEWEST_LOOKING_DEAD && looksDead <= MOST_LOOKING_DEAD, String.format("dead mounds still looking"
                + " dead (bare soil, under %.0f days worn) outside %.0f%% to %.0f%% of mounds (%.0f%%; look at dead_stages, dead_share and"
                + " dead_apart in termite_mound.json): %s", LOOKS_DEAD_DAYS, 100 * FEWEST_LOOKING_DEAD, 100 * MOST_LOOKING_DEAD, 100 * looksDead, report));
        helper.assertTrue(t[4] == 0, "dead mounds of new land with no living termite mound in reach (they should live instead): " + report);
        checkTheMatabeleShare(helper, mounds.numbers(), t, report);
        checkWaterTreesAndReach(helper, t, report);
        double matabele = t[3] / (double) t[1];
        helper.assertTrue(matabele >= FEWEST_MATABELE, String.format("Matabele dead mounds too rare (%.0f%% of mounds, want at least %.0f%%;"
                + " look at the share and living_near in termite_mound.json's matabele block, or what changed where mounds stand): %s",
                100 * matabele, 100 * FEWEST_MATABELE, report));
        helper.assertTrue(matabele <= MOST_MATABELE, String.format("Matabele dead mounds too common (%.0f%% of mounds, want at most %.0f%%): %s",
                100 * matabele, 100 * MOST_MATABELE, report));

        // BUGS 162: the user plays single-biome savanna worlds, rugged land with mounds farther apart; dead mounds still
        // spread out and still looking dead about 1 in 4 to 5; lonely dead mounds there stand empty (the user, 2026-11-07),
        // so the Matabele may be rarer than on mixed land, but never rare
        long[] singleSeeds = {-5841907898462156635L, 1L};
        int[] u = new int[16];
        // flat savannas (2026-11-07): a single-biome savanna world is flattened everywhere, as the game does for one
        com.antcolonies.savanna.worldgen.FlatSavanna.setWholeWorld(true);
        try {
            for (long seed : singleSeeds) {
                MoundPlaces.Land land = singleBiomeSavanna(level, seed, mounds);
                int[] r = survey(land, mounds.numbers(), savannaPlaces(land, mounds.numbers().places()), helper);
                for (int i = 0; i < u.length; i++) {
                    u[i] = i == 14 ? Math.max(u[i], r[i]) : u[i] + r[i];
                }
            }
        } finally {
            com.antcolonies.savanna.worldgen.FlatSavanna.setWholeWorld(false);
        }
        String single = report("single-biome savanna", u);
        SavannaMod.LOGGER.info("[new-land] the savanna survey: {}", single);
        double singleLooks = u[7] / (double) Math.max(1, u[1]);
        helper.assertTrue(singleLooks >= FEWEST_LOOKING_DEAD && singleLooks <= MOST_LOOKING_DEAD, String.format("dead mounds still looking"
                + " dead of a single-biome savanna outside %.0f%% to %.0f%% of mounds (%.0f%%): %s", 100 * FEWEST_LOOKING_DEAD,
                100 * MOST_LOOKING_DEAD, 100 * singleLooks, single));
        helper.assertTrue(u[4] == 0, "dead mounds with no living termite mound in reach: " + single);
        checkTheMatabeleShare(helper, mounds.numbers(), u, single);
        checkWaterTreesAndReach(helper, u, single);
        double singleShare = u[3] / (double) Math.max(1, u[1]);
        helper.assertTrue(singleShare >= FEWEST_MATABELE_SINGLE && singleShare <= MOST_MATABELE, String.format("Matabele dead mounds of a"
                + " single-biome savanna outside %.0f%% to %.0f%% of mounds (%.0f%%): %s", 100 * FEWEST_MATABELE_SINGLE, 100 * MOST_MATABELE,
                100 * singleShare, single));
        helper.succeed();
    }

    /** One survey's line for the log and the failures. */
    private static String report(String land, int[] t) {
        return String.format("%s: %d places, %d hold a mound, %d dead (%d still looking dead, 1 mound in %.1f; %d with another dead"
                + " within %.0f, %d too near), %d of them Matabele (1 mound in %.1f, %.0f%% of the dead near termites), %d lonely dead"
                + " mounds (no living mound in reach, empty), %d dead by the dice living instead, %d mounds nearer than 40; %d mounds"
                + " in water or on the lowest floodplain, %d with little room for a dead tree; of the Matabele, %d with a living mound's"
                + " edge within the scouts' reach less 10, its edge %.0f blocks off on average, %d at most", land, t[0], t[1],
                t[2], t[7], t[1] / (double) Math.max(1, t[7]), t[9], CLUMP, t[8], t[3], t[1] / (double) Math.max(1, t[3]),
                100.0 * t[3] / Math.max(1, t[2] - t[4]), t[4], t[6], t[5], t[10], t[11], t[12], t[13] / (double) Math.max(1, t[3]), t[14]);
    }

    /** Of the dead mounds with living termites in reach, the share's worth hold Matabele (3 in 4, the user 2026-11-07). */
    private static void checkTheMatabeleShare(GameTestHelper helper, TermiteMoundStructure.Numbers numbers, int[] t, String report) {
        double near = t[2] - t[4];
        if (near < 20) {
            return;
        }
        double share = t[3] / near;
        helper.assertTrue(Math.abs(share - numbers.matabele().share()) < 0.2, String.format("%.0f%% of the dead mounds near termites"
                + " hold Matabele, not about %.0f%%: %s", 100 * share, 100 * numbers.matabele().share(), report));
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

    /**
     * The commonness band of Matabele dead mounds among all mounds of new land (the user, 2026-11-06: uncommon, not rare;
     * 2026-11-07: dead mounds 1 in 3, 3 in 4 of them Matabele, so about 1 mound in 4).
     */
    static final double FEWEST_MATABELE = 0.18, MOST_MATABELE = 0.35;

    /**
     * The lowest Matabele share on rugged single-biome savanna, where mounds stand farther apart and a lonely dead mound
     * stands empty (the user, 2026-11-07: "dead mounds not by termite mounds never have Matabele").
     */
    static final double FEWEST_MATABELE_SINGLE = 0.12;

    /**
     * Dead mounds the eye reads as dead (the user, 2026-11-07: "still seeing dead mounds about 1 in 10 to 1 in 15, I want 1 in
     * 4 to 1 in 5"; then 2026-11-07: 1 in 3): under {@link #LOOKS_DEAD_DAYS} days worn a dead mound still stands as bare soil (spires to day 5, the
     * body slumping fast to day 12, {@code MoundWear}); after that it is a low lump, then a grassy hump, read as a hill. The
     * user's game logs had a quarter of the mounds dead but only about 4 in 10 of those this young; this guard counts what is
     * seen, not the dice.
     */
    static final double LOOKS_DEAD_DAYS = 12, FEWEST_LOOKING_DEAD = 0.26, MOST_LOOKING_DEAD = 0.40;

    /** Minecraft's overworld for a seed, as the noise makes it (no chunks, big savannas and all), with the mounds' biomes. */
    private static MoundPlaces.Land realLand(ServerLevel level, long seed, TermiteMoundStructure mounds) {
        var registries = level.registryAccess();
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        // big savannas (2026-11-06): TerraBlender's zones with ours laid out for this seed, as a new normal world has them
        BiomeSource biomes = BiomeMaps.bigSavannaSource(registries, seed);
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
     * Places, held, dead held, Matabele dead mounds (dice and a living mound in reach), lonely dead mounds (no living
     * mound in reach, never Matabele), feet nearer than 40 to another (each fails the test), dead mounds by the dice living
     * instead, dead still looking dead, pairs of dead mounds nearer than the numbers' {@code dead_apart} (each fails the
     * test, the user's 2026-11-07 spreading), and dead mounds with another dead within {@link #CLUMP} (the clumps the user
     * saw, reported).
     */
    private static int[] survey(MoundPlaces.Land land, TermiteMoundStructure.Numbers numbers, List<MoundPlaces.Place> places, GameTestHelper helper) {
        int[] out = new int[16];
        List<BlockPos> feet = new ArrayList<>(), deadFeet = new ArrayList<>();
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
            // test revamp R6: no mound in water or on the floodplain's lowest ground, read again from the land itself
            String wet = wetFoot(land, numbers.places(), foot);
            if (wet != null) {
                out[10]++;
                helper.assertTrue(false, "a mound at " + foot.toShortString() + " " + wet);
            }
            // test revamp R6: room for its dead tree, dry fairly flat ground 10 to 30 blocks past its foot
            out[11] += deadTreeGround(land, foot, MoundPlaces.footReach(pick.stage())) < DEAD_TREE_SPOTS_WANTED ? 1 : 0;
            if (pick.living()) {
                out[6] += MoundPlaces.living(land.seed(), place, numbers.deadShare()) ? 0 : 1;
                continue;
            }
            out[2]++;
            deadFeet.add(foot);
            out[7] += pick.days() < LOOKS_DEAD_DAYS ? 1 : 0;
            if (!NewLandMounds.livingPlaceNear(land, numbers, foot)) {
                out[4]++;
            } else if (MatabeleColonies.wouldSettle(pick.seed(), numbers.matabele().share())) {
                out[3]++;
                // BUGS 208: the survey counted the dice only, while a freshly dead mound's capped chimney let no colony in
                helper.assertTrue(pick.days() >= com.antcolonies.savanna.colony.MoundWear.PIECES_TO, "a Matabele mound at " + foot.toShortString()
                        + " is dead only " + pick.days() + " days: its chimney is still capped, so no colony gets in");
                // test revamp R6: the living mound the raids feed on, its foot's edge from the Matabele nest
                double edge = nearestLivingEdge(land, numbers, foot);
                out[12] += edge <= SCOUT_REACH - NewLandMounds.DEAD_TREE_NEAREST ? 1 : 0;
                out[13] += (int) Math.round(edge);
                out[14] = Math.max(out[14], (int) Math.round(edge));
            }
        }
        for (int i = 0; i < feet.size(); i++) {
            for (int j = i + 1; j < feet.size(); j++) {
                double d = sideways(feet.get(i), feet.get(j));
                if (d < NewLandMounds.KEEP_APART) {
                    out[5]++;
                    helper.assertTrue(false, "two mounds " + Math.round(d) + " apart at "
                            + feet.get(i).toShortString() + " and " + feet.get(j).toShortString());
                }
            }
        }
        for (int i = 0; i < deadFeet.size(); i++) {
            boolean clumped = false;
            for (int j = 0; j < deadFeet.size(); j++) {
                if (i == j) {
                    continue;
                }
                double d = sideways(deadFeet.get(i), deadFeet.get(j));
                if (j > i && d <= numbers.deadApart()) {
                    out[8]++;
                    helper.assertTrue(false, "two dead mounds " + Math.round(d) + " apart (dead_apart " + numbers.deadApart() + ") at "
                            + deadFeet.get(i).toShortString() + " and " + deadFeet.get(j).toShortString());
                }
                clumped |= d <= CLUMP;
            }
            out[9] += clumped ? 1 : 0;
        }
        return out;
    }

    /** Dead mounds with another dead one this near count as a clump in the survey's report (about 1.5 mound spacings). */
    static final double CLUMP = 100;

    /**
     * Test revamp R6, what new land promises the raids and the termites, measured over the survey's real land: no mound
     * in water or on the floodplain's lowest ground (asserted per mound in {@link #survey}); nearly every mound with room
     * for its dead tree (the termites' by-day feeding, where the scouts look; {@code docs/BUGS.md} 81 once left 16 of 21
     * without one); and nearly every Matabele nest with a living mound's foot within the scouts' reach, its dead tree
     * 10 to 30 past that foot, so a raid has a feeding spot to find.
     */
    private static void checkWaterTreesAndReach(GameTestHelper helper, int[] t, String report) {
        helper.assertTrue(t[10] == 0, "mounds in water or on the floodplain's lowest ground: " + report);
        helper.assertTrue(t[11] <= MOST_WITHOUT_TREE_ROOM * t[1], String.format("%d of %d mounds have little room for their dead"
                + " tree (fewer than %d dry, fairly flat spots 10 to 30 past the foot): %s", t[11], t[1], DEAD_TREE_SPOTS_WANTED, report));
        if (t[3] >= 10) {
            helper.assertTrue(t[12] >= LEAST_IN_SCOUT_REACH * t[3], String.format("only %d of %d Matabele nests have a living mound's"
                    + " foot within %d blocks: %s", t[12], t[3], SCOUT_REACH - NewLandMounds.DEAD_TREE_NEAREST, report));
            helper.assertTrue(t[13] / (double) t[3] <= MOST_AVERAGE_EDGE, String.format("the living mounds stand %.0f blocks from the"
                    + " Matabele nests on average, over %.0f: %s", t[13] / (double) t[3], MOST_AVERAGE_EDGE, report));
            helper.assertTrue(t[14] <= LIVING_NEAR_MOST, "a Matabele nest's nearest living mound's edge is " + t[14]
                    + " blocks off, past matabele.living_near: " + report);
        }
    }

    /** {@code matabele.living_near} in {@code termite_mound.json} (100), rounded up: no Matabele nest farther from termites. */
    static final int LIVING_NEAR_MOST = 101;
    /** The Matabele scouts' whole reach on new land ({@code raids.scouting.settled_range}). */
    static final int SCOUT_REACH = 96;
    /** Dead tree spots a mound should have (of the 24 looked at round it), and the share of mounds allowed fewer. */
    static final int DEAD_TREE_SPOTS_WANTED = 3;
    static final double MOST_WITHOUT_TREE_ROOM = 0.05;
    /** The share of Matabele nests that must have a living mound's foot within the scouts' reach less 10, and the average edge. */
    static final double LEAST_IN_SCOUT_REACH = 0.9, MOST_AVERAGE_EDGE = 70;

    /** Why a mound's foot is wet (water at or round it, or ground at most {@code low_ground} over the sea), or null. */
    private static String wetFoot(MoundPlaces.Land land, MoundPlaces.Numbers numbers, BlockPos foot) {
        int[][] around = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}};
        for (int[] o : around) {
            int x = foot.getX() + o[0], z = foot.getZ() + o[1];
            if (land.height(x, z, Heightmap.Types.WORLD_SURFACE_WG) != land.height(x, z, Heightmap.Types.OCEAN_FLOOR_WG)) {
                return "has water " + o[0] + ", " + o[1] + " from its middle";
            }
        }
        int over = foot.getY() - land.generator().getSeaLevel();
        return over <= numbers.lowGround() ? "stands only " + over + " over the water (low_ground " + numbers.lowGround() + ")" : null;
    }

    /**
     * How many (up to {@link #DEAD_TREE_SPOTS_WANTED}) of 24 spots round a mound (8 bearings, 3 distances 10 to 30 past its foot) have dry ground, flat within
     * the dead tree's {@code DEAD_TREE_UNEVEN} over 3 blocks round, and near the mound's height (the dead tree's ground
     * rule in {@code NewLandMounds}, read on land no world holds).
     */
    private static int deadTreeGround(MoundPlaces.Land land, BlockPos foot, double footReach) {
        int found = 0;
        int uneven = NewLandMounds.DEAD_TREE_UNEVEN;
        for (int b = 0; b < 8; b++) {
            double bearing = b * Math.PI / 4;
            for (int d = 0; d < 3; d++) {
                double r = footReach + NewLandMounds.DEAD_TREE_NEAREST + d * (NewLandMounds.DEAD_TREE_FARTHEST - NewLandMounds.DEAD_TREE_NEAREST) / 2.0;
                int x = (int) Math.floor(foot.getX() + 0.5 + Math.cos(bearing) * r), z = (int) Math.floor(foot.getZ() + 0.5 + Math.sin(bearing) * r);
                int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
                boolean dry = true;
                for (int[] o : new int[][] {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                    int surface = land.height(x + o[0], z + o[1], Heightmap.Types.WORLD_SURFACE_WG);
                    dry &= surface == land.height(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
                    lowest = Math.min(lowest, surface);
                    highest = Math.max(highest, surface);
                }
                if (dry && highest - lowest <= uneven && Math.abs(lowest - foot.getY()) <= 2 * uneven && ++found >= DEAD_TREE_SPOTS_WANTED) {
                    return found; // (enough: each ground height costs about 2 ms on this land)
                }
            }
        }
        return found;
    }

    /** How far (blocks, sideways) the foot's edge of the nearest living mound of new land lies from {@code center}. */
    private static double nearestLivingEdge(MoundPlaces.Land land, TermiteMoundStructure.Numbers numbers, BlockPos center) {
        MoundPlaces.Numbers places = numbers.places();
        int look = numbers.matabele().livingNear() + (int) Math.ceil(MoundPlaces.footReach(TermiteMoundStructure.FULL_STAGE));
        double nearest = Double.MAX_VALUE;
        for (MoundPlaces.Place place : MoundPlaces.near(land.seed(), center.getX(), center.getZ(), look, places)) {
            BlockPos foot = MoundPlaces.holds(land, place, places);
            if (foot == null) {
                continue;
            }
            TermiteMoundStructure.Pick pick = TermiteMoundStructure.pickAt(numbers, land, place, MoundPlaces.sizeQuantile(land, place, places));
            if (pick.living()) {
                nearest = Math.min(nearest, Math.max(0, sideways(foot, center) - MoundPlaces.footReach(pick.stage())));
            }
        }
        return nearest;
    }

    private static double sideways(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The dice give the data file's share of dead mounds a colony; the sizes run 30 to 100, half under about 55. */
    private static void checkTheDice(GameTestHelper helper) {
        double share = MatabeleColonies.share(helper.getLevel());
        int rolls = 3000, yes = 0, small = 0;
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < rolls; i++) {
            yes += MatabeleColonies.wouldSettle(random.nextLong(), share) ? 1 : 0;
            int size = MatabeleColonies.sizeFor(random);
            helper.assertTrue(size >= MatabeleColonies.FEWEST && size <= MatabeleColonies.MOST, "a colony of " + size);
            small += size < 55 ? 1 : 0;
        }
        helper.assertTrue(Math.abs(yes / (double) rolls - share) < 0.04, yes + " colonies in " + rolls + " dead mounds");
        helper.assertTrue(Math.abs(small / (double) rolls - 0.5) < 0.04, small + " of " + rolls + " colonies under 55");
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
