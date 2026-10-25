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
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Matabele colonies in new land (M8 step 1f): a dead mound handed over by world generation (queued, as new land does)
 * gets a Matabele colony when its dice say so, the queen in her room under the hump, 100 to 2,300 workers inside as
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
        while (!MatabeleColonies.wouldSettle(yes)) {
            yes++;
        }
        while (MatabeleColonies.wouldSettle(no)) {
            no++;
        }
        // A dead mound whose dice say no, put in as new land does: no Matabele in it.
        NewLandMounds book = NewLandMounds.get(level);
        BlockPos empty = helper.absolutePos(new BlockPos(MIDDLE + 40, SURFACE, MIDDLE + 40));
        helper.assertTrue(book.putWithAnts(level, empty, NewLandMounds.pickFor("dead", 30, no), UUID.randomUUID(), false) != null,
                "no dead mound put in for the dice's no");
        helper.assertTrue(book.lastAnts() == null, "a dead mound whose dice say no got a Matabele colony");
        // The one whose dice say yes, the way world generation hands it over.
        BlockPos middle = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
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
            }
            helper.succeed();
        });
    }

    /** About two dead mounds in three get a colony; the sizes run 100 to 2,300, half under about 480. */
    private static void checkTheDice(GameTestHelper helper) {
        int rolls = 3000, yes = 0, small = 0;
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < rolls; i++) {
            yes += MatabeleColonies.wouldSettle(random.nextLong()) ? 1 : 0;
            int size = MatabeleColonies.sizeFor(random);
            helper.assertTrue(size >= MatabeleColonies.FEWEST && size <= MatabeleColonies.MOST, "a colony of " + size);
            small += size < 480 ? 1 : 0;
        }
        helper.assertTrue(Math.abs(yes / (double) rolls - MatabeleColonies.SHARE) < 0.04, yes + " colonies in " + rolls + " dead mounds");
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
