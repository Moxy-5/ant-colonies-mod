package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.species.SpeciesProfiles;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele colony (M8 step 1c): a queen put down starts a colony; majors and minors put down near her join it and
 * are counted; each worker has its own length within the real spread, drawn (and its box sized) with the model nearer
 * its length, and keeps it through a save; the queen alone digs nothing, and both colonies settle in a dead mound (step 1d,
 * {@code MatabeleNestTests}; two dead mounds are put in for them); a dead worker leaves the headcount;
 * {@code /antcolonies matabele} makes a queen with her workers, real ones up to the cap and the rest numbers; and the
 * size spread gives the real mix (about half majors, a quarter between, a quarter minors).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleColonyTests {
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aMatabeleQueenStartsAColonyThatMajorsAndMinorsJoinEachWithItsOwnSize(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        // Soil for the second dead mound too (out in the arena's far corner, past the deep ground).
        for (int x = MIDDLE + 26; x < 65; x++) {
            for (int z = MIDDLE + 26; z < 65; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
            }
        }
        SavannaNumbers.WorkerSizes sizes = SpeciesProfiles.get(MatabeleEntity.SPECIES).section(SavannaNumbers.WORKER_SIZES).orElseThrow();
        checkTheMix(helper, sizes);
        // A home for each colony (dead mounds are the Matabele's only home for now): one here, one for the command's colony.
        helper.assertTrue(NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)),
                NewLandMounds.pickFor("dead", 30, 5L)) != null, "no dead mound put in for the queen");
        helper.assertTrue(NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE + 40, SURFACE, MIDDLE + 40)),
                NewLandMounds.pickFor("dead", 30, 6L)) != null, "no dead mound put in for the command's colony");
        MatabeleQueenEntity queen = helper.spawn(SavannaEntities.MATABELE_QUEEN.get(), MIDDLE + 9, top(helper, MIDDLE + 9, MIDDLE), MIDDLE);
        List<MatabeleEntity> workers = new ArrayList<>();
        Colony[] made = new Colony[1];
        helper.runAfterDelay(10, () -> {
            Colony colony = queenColony(helper, queen);
            helper.assertTrue(colony != null, "the queen started no Matabele colony; colonies: " + com.antcolonies.colony.ColonyManager.get(level).getColonies().stream()
                    .map(c -> c.getSpecies() + "@" + c.getCenter().toShortString() + " queen " + c.getQueen(level)).toList() + " queen at " + queen.blockPosition().toShortString() + " id " + queen.getColonyId());
            helper.assertTrue(colony.getId().equals(queen.getColonyId()), "the queen is not the colony's queen");
            helper.assertTrue(colony.isFounding(), "a queen alone does not count as founding (she could starve alone)");
            // Alone she digs nothing (the user's pick, 2026-10-24): her workers dig the way in.
            helper.assertTrue(colony.nest().findDigTarget(level) == null, "a Matabele queen alone has something to dig");
            for (int i = 0; i < 4; i++) {
                workers.add(helper.spawn(SavannaEntities.MATABELE_MAJOR.get(), MIDDLE + 6 + 2 * i, top(helper, MIDDLE + 6 + 2 * i, MIDDLE + 3), MIDDLE + 3));
                workers.add(helper.spawn(SavannaEntities.MATABELE_MINOR.get(), MIDDLE + 6 + 2 * i, top(helper, MIDDLE + 6 + 2 * i, MIDDLE - 3), MIDDLE - 3));
            }
            made[0] = colony;
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = made[0];
            helper.assertTrue(colony.getCount(Caste.WORKER) == 8, colony.getCount(Caste.WORKER) + " workers counted, not 8");
            for (MatabeleEntity ant : workers) {
                helper.assertTrue(colony.getId().equals(ant.getColonyId()), "a " + ant.getType().getDescriptionId() + " did not join the colony");
                float length = ant.getLengthMm();
                helper.assertTrue(length >= sizes.shortest() && length <= sizes.longest(), "a worker " + length + " mm long, outside the real spread");
                helper.assertTrue(ant.hasMajorBody() == sizes.majorModelFor(length), "a worker " + length + " mm long has the farther model");
                helper.assertTrue(Math.abs(ant.getScale() - sizes.scaleFor(length)) < 1.0E-3, "a worker " + length + " mm long is drawn at " + ant.getScale());
                float width = ant.getType().getWidth() * sizes.scaleFor(length);
                helper.assertTrue(Math.abs(ant.getBbWidth() - width) < 1.0E-3, "a worker " + length + " mm long has a box " + ant.getBbWidth() + " wide, not " + width);
                // Through a save: the same length and size.
                CompoundTag tag = new CompoundTag();
                ant.saveWithoutId(tag);
                MatabeleEntity loaded = ((EntityType<MatabeleEntity>) ant.getType()).create(level);
                loaded.load(tag);
                helper.assertTrue(loaded.getLengthMm() == length && Math.abs(loaded.getScale() - ant.getScale()) < 1.0E-6,
                        "a worker " + length + " mm long came back " + loaded.getLengthMm() + " mm long, drawn at " + loaded.getScale());
                loaded.discard();
            }
            // The workers no longer leave the queen founding: with them she would starve unfed (hunger on, no prey pile yet).
            helper.assertFalse(colony.isFounding(), "a queen with workers still counts as founding");
            MatabeleEntity dead = workers.get(1);
            dead.kill();
            helper.assertTrue(colony.getCount(Caste.WORKER) == 7, "a dead worker stayed in the headcount");
            // The command: a queen with 60 workers, 50 real (the cap) round her, 10 numbers inside.
            Colony whole = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 31, SURFACE, MIDDLE + 40)), 60);
            helper.assertTrue(whole != null, "the command made no colony");
            made[0] = whole;
        });
        helper.runAfterDelay(60, () -> {
            Colony whole = made[0];
            helper.assertTrue(whole.getCount(Caste.WORKER) == 60, whole.getCount(Caste.WORKER) + " workers in the command's colony, not 60");
            List<MatabeleEntity> real = TestIsolation.insectsOf(level, whole, MatabeleEntity.class);
            int cap = whole.profile().colony().realCap();
            helper.assertTrue(real.size() == cap, real.size() + " real workers round the command's queen, not " + cap);
            helper.assertTrue(whole.population().inside(Caste.WORKER) == 60 - cap, whole.population().inside(Caste.WORKER) + " workers inside, not " + (60 - cap));
            helper.assertTrue(whole.getQueen(level) instanceof MatabeleQueenEntity, "the command's colony has no Matabele queen");
            helper.assertTrue(real.stream().anyMatch(MatabeleEntity::hasMajorBody) && real.stream().anyMatch(ant -> !ant.hasMajorBody()),
                    "the command's 50 workers are not majors and minors both");
        });
        helper.runAfterDelay(300, () -> {
            // Both colonies settled in a dead mound (step 1d), each in its own.
            for (Colony colony : List.of(made[0], queenColony(helper, queen))) {
                helper.assertTrue(MatabeleNest.IN_A_MOUND.equals(colony.nest().site()), "a Matabele colony settled " + colony.nest().site());
            }
            helper.succeed();
        });
    }

    /** The size spread gives the real mix: about half majors (13.5 mm and up), a quarter between, a quarter minors; every one 5 to 18 mm. */
    private static void checkTheMix(GameTestHelper helper, SavannaNumbers.WorkerSizes sizes) {
        RandomSource random = RandomSource.create(42);
        int rolls = 4000;
        int majors = 0;
        int minors = 0;
        int majorModels = 0;
        for (int i = 0; i < rolls; i++) {
            float length = sizes.roll(random);
            helper.assertTrue(length >= 5.0F && length <= 18.0F, "a worker " + length + " mm long");
            majors += length >= 13.5F ? 1 : 0;
            minors += length < 11.5F ? 1 : 0;
            majorModels += MatabeleEntity.pickBody(random) == SavannaEntities.MATABELE_MAJOR.get() ? 1 : 0;
        }
        helper.assertTrue(Math.abs(majors / (double) rolls - 0.5) < 0.04, majors + " majors in " + rolls);
        helper.assertTrue(Math.abs(minors / (double) rolls - 0.25) < 0.04, minors + " minors in " + rolls);
        // Minors up to about 10.6 mm (the length as near the one model as the other) have the minor's model: about 23 in 100.
        helper.assertTrue(Math.abs(majorModels / (double) rolls - 0.77) < 0.04, majorModels + " with the major's model in " + rolls);
    }

    /** The queen's own colony (its middle, up the dead mound's chimney, may stand above the arena's box). */
    private static Colony queenColony(GameTestHelper helper, MatabeleQueenEntity queen) {
        return queen.getColonyId() == null ? null : com.antcolonies.colony.ColonyManager.get(helper.getLevel()).getColony(queen.getColonyId());
    }

    /** The height (in the arena's own numbers) to stand on at (x, z): on top of a dead mound's hump where it rises there. */
    private static int top(GameTestHelper helper, int x, int z) {
        BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE, z));
        return helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ())
                - helper.absolutePos(BlockPos.ZERO).getY();
    }
}
