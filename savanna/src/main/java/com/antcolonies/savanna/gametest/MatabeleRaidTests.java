package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M8 step 3, the Matabele raids ({@code colony.Raids}). Step 3a, the scouts. */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleRaidTests {
    /** The nest's middle in this scene (away from the arena's edges, so every slice round it has ground). */
    private static final int NEST = 20;

    /**
     * A hungry colony in the raid hours (the test's still 7:00, the prey pile empty) sends a scout out by itself; it finds
     * termites feeding under a log 17 to 18 blocks out (there are two such logs, in opposite slices), never the termites'
     * harm, and comes home with a scent line from the nest to the find; the next scout rechecks the remembered spot first,
     * and it is the same ant (the colony's old scout goes first), named on the find.
     */
    @GameTest(template = "arena", timeoutTicks = 12000, skyAccess = true, batch = "matabele_scouts")
    public static void aHungryMatabeleColonySendsAScoutWhoFindsTermitesAndComesHomeWithAScentLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.shapeGround(helper, 45, (x, z) -> 0);
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(NEST, SURFACE, NEST)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        termites.addAll(feedingUnderALog(helper, 36, NEST + 4));
        termites.addAll(feedingUnderALog(helper, 2, NEST - 4));
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(NEST + 6, SURFACE, NEST)), 10);
        helper.assertTrue(colony != null, "no Matabele colony made");
        Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
        helper.assertTrue(raids != null, "a Matabele colony has no raids part");
        long[] outAt = {-1};
        long[] foundAt = {-1};
        java.util.Set<java.util.UUID> scouted = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            colony.population().realMembers(level).stream().filter(ant -> raids.isScouting(ant.getUUID()))
                    .forEach(ant -> scouted.add(ant.getUUID()));
            if (outAt[0] < 0 && !scouted.isEmpty()) {
                outAt[0] = helper.getTick();
            }
            for (TermiteWorkerEntity termite : termites) {
                helper.assertTrue(termite.isAlive() && !termite.isParalysed(), "a scout attacked a termite (scouts only look)");
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(outAt[0] >= 0, "no scout out yet (hungry, 7:00)"))
                .thenWaitUntil(() -> helper.assertTrue(!raids.finds().isEmpty(), "no find yet; " + raids.describe(level)))
                .thenExecute(() -> {
                    foundAt[0] = level.getGameTime();
                    Raids.Find find = raids.finds().get(0);
                    boolean byTermites = termites.stream().anyMatch(termite -> termite.blockPosition().closerThan(find.at(), 6));
                    helper.assertTrue(byTermites, "the find at " + find.at().toShortString() + " is not by the termites");
                    List<BlockPos> line = find.trail();
                    helper.assertTrue(line.size() >= 5, "a scent line of only " + line.size() + " points");
                    helper.assertTrue(line.get(0).closerThan(colony.getCenter(), 4), "the scent line does not start at the nest");
                    helper.assertTrue(line.get(line.size() - 1).closerThan(find.at(), 8), "the scent line does not reach the find");
                    for (int i = 1; i < line.size(); i++) {
                        helper.assertTrue(line.get(i).closerThan(line.get(i - 1), 5), "a gap in the scent line at point " + i);
                    }
                    helper.assertTrue(find.scout() != null && scouted.contains(find.scout()) && raids.isVeteran(find.scout()),
                            "the find does not name its scout as one of the colony's scouts");
                    SavannaMod.LOGGER.info("[scout test] out at {}, found at {}: {}", outAt[0], helper.getTick(), raids.describe(level));
                })
                .thenWaitUntil(() -> helper.assertTrue(raids.finds().stream().anyMatch(find -> find.checkedAt() > foundAt[0]),
                        "the remembered spot was not rechecked; " + raids.describe(level)))
                .thenExecute(() -> helper.assertTrue(raids.range() == 32, "the search range grew to " + raids.range() + " though termites were found"))
                .thenExecute(() -> helper.assertTrue(scouted.size() == 1,
                        "the recheck went to a new ant, not the colony's old scout (" + scouted.size() + " scouts)"))
                .thenSucceed();
    }

    /** Two termite workers feeding in a gallery under a 4-block log (along x from {@code x}) at row {@code z}. */
    private static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z) {
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            helper.setBlock(new BlockPos(x + i, SURFACE, z), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            helper.setBlock(x + i, SURFACE - 1, z, Blocks.AIR);
        }
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 1, SURFACE - 1, z));
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 2, SURFACE - 1, z));
        return termites;
    }
}
