package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.HashSet;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.FungusCombBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The termites' fungus garden (M7 step 5a): garden rooms round the royal chamber, forage built into fungus comb, white
 * nodules growing on it, the nurses feeding them to the queen (who lays a steady stream of eggs) and to the nymphs, the
 * eggs carried into the gardens and laid on the comb.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteGardenTests {
    /**
     * A queen, a king and 6 workers on deep ground with two hay bales nearby (always night here, so the foragers go
     * out). The workers dig the royal chamber and the first garden room, bring in hay and build it into fungus comb,
     * nodules grow on it, the nurses feed the queen (full grown here, so she lays fast), she lays eggs behind her, the
     * nurses carry them into the garden and lay them on the comb, they hatch into nymphs, the nurses feed the nymphs
     * nodules, and a nymph grows into a new worker. All along: no egg or nymph ever lies inside the comb, and every egg
     * that a nurse put down lies in a garden room.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true, batch = "garden")
    public static void termitesGrowAFungusGardenAndRaiseTheQueensEggsOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 3, SURFACE, MIDDLE - 2 + i / 3);
            }
        });
        helper.setBlock(new BlockPos(MIDDLE + 6, SURFACE, MIDDLE - 6), Blocks.HAY_BLOCK);
        helper.setBlock(new BlockPos(MIDDLE - 6, SURFACE, MIDDLE + 6), Blocks.HAY_BLOCK);

        Colony[] colony = {null};
        int[] firstWorkers = {-1};
        boolean[] sawEggOnComb = {false};
        helper.onEachTick(() -> {
            if (colony[0] == null) {
                Colony found = TermiteMoundTests.colony(helper);
                if (found == null || found.isFounding()) {
                    return;
                }
                colony[0] = found;
                found.setForagingTimeForTests(true);
            }
            Colony c = colony[0];
            if (helper.getTick() > 20 && firstWorkers[0] < 0) {
                firstWorkers[0] = c.getCount(WorkerAntEntity.CASTE);
            }
            // Once she lies in her room she is full grown, so she lays fast.
            if (c.getQueen(level) instanceof TermiteQueenEntity queen && queen.getSize() < 1.0F && c.queenCare().isQueenHome(level)) {
                queen.setSize(1.0F);
            }
            // No egg or young one lies inside the comb.
            for (Entity young : youngOf(level, helper, c)) {
                BlockPos cell = young.blockPosition();
                BlockState state = level.getBlockState(cell);
                if (state.is(ModBlocks.FUNGUS_COMB.get())) {
                    double top = cell.getY() + FungusCombBlock.topOf(state);
                    helper.assertTrue(young.getY() >= top - 0.01, young.getType().toShortString() + " lies inside the comb at " + cell.toShortString()
                            + " (y " + young.getY() + ", comb top " + top + ")");
                    if (young instanceof AntEggEntity) {
                        sawEggOnComb[0] = true;
                    }
                }
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[garden-test] tick {}: workers {}, gardens dug cells {}, comb layers {}, nodules {} (grown {}), queen holds {}, "
                                + "eggs laid {}, eggs {}, nymphs {}, dig target {}", helper.getTick(), c.getCount(WorkerAntEntity.CASTE),
                        c.nest().getGardenCells(level).size(), c.garden().countCombLayers(level), c.garden().available(level), c.garden().getNodulesGrown(),
                        c.queenCare().getQueenBread(), c.queenCare().getEggsLaid(), c.brood().countEggs(level), c.brood().countLarvae(level),
                        c.nest().findDigTarget(level));
            }
        });

        helper.succeedWhen(() -> {
            Colony c = colony[0];
            helper.assertTrue(c != null, "no termite colony with workers yet");
            helper.assertTrue(!c.garden().getCombs(level).isEmpty(), "no fungus comb yet");
            helper.assertTrue(c.garden().getNodulesGrown() > 0, "no nodule has grown yet");
            helper.assertTrue(c.queenCare().getEggsLaid() > 0, "the queen has laid no egg yet (she holds " + c.queenCare().getQueenBread() + " nodules)");
            helper.assertTrue(sawEggOnComb[0], "no egg was laid on the comb yet");
            // Every egg a nurse put down lies in a garden room.
            Set<BlockPos> gardens = new HashSet<>(c.nest().getGardenCells(level));
            for (AntEggEntity egg : c.brood().getEggs(level)) {
                helper.assertTrue(!egg.isPlaced() || gardens.contains(egg.blockPosition()), "an egg was put down outside the gardens at "
                        + egg.blockPosition().toShortString());
            }
            helper.assertTrue(c.getCount(WorkerAntEntity.CASTE) > firstWorkers[0], "no nymph has grown into a worker yet (workers "
                    + c.getCount(WorkerAntEntity.CASTE) + ", nymphs " + c.brood().countLarvae(level) + ", eggs " + c.brood().countEggs(level) + ")");
        });
    }

    /** This colony's eggs and nymphs round the test area. */
    private static java.util.List<Entity> youngOf(ServerLevel level, GameTestHelper helper, Colony colony) {
        java.util.List<Entity> young = new java.util.ArrayList<>();
        for (AntEggEntity egg : level.getEntitiesOfClass(AntEggEntity.class, helper.getBounds().inflate(4))) {
            if (colony.getId().equals(egg.getColonyId())) {
                young.add(egg);
            }
        }
        for (AntLarvaEntity nymph : level.getEntitiesOfClass(AntLarvaEntity.class, helper.getBounds().inflate(4))) {
            if (colony.getId().equals(nymph.getColonyId())) {
                young.add(nymph);
            }
        }
        return young;
    }
}
