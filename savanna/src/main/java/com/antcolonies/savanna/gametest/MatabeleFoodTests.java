package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.gametest.TestScenes;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.PreyPileBlock;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele's food (M8 step 1e): the prey pile in the queen's room. Stocked (as {@code /antcolonies prey} does, until
 * the raids exist), hungry workers eat from it; a day on its layers have rotted, the pile lies still and dead, and the
 * rotten layers are carried out to a heap of remains beside the entrance, leaving the queen's room clear. With the pile
 * empty, hungry workers find nothing and starve.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleFoodTests {
    /** Stocked, eaten, rotted and carried out. */
    @GameTest(template = "arena", timeoutTicks = 9000, skyAccess = true)
    public static void aMatabelePreyPileIsEatenThenRotsAndIsCarriedOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = settled(helper);
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(pile != null, "a Matabele colony has no prey pile");
        long[] hungrySince = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the queen's room is not open yet"))
                .thenExecute(() -> {
                    helper.assertTrue(pile.stock(level, 6) == 6, "the pile did not take 6 layers");
                    helper.assertTrue(pile.edible() == 6, pile.edible() + " good layers after stocking 6");
                    List<BlockPos> heaps = heaps(level, colony);
                    helper.assertTrue(!heaps.isEmpty(), "no prey pile in the queen's room after stocking");
                    for (BlockPos pos : heaps) {
                        helper.assertFalse(level.getBlockState(pos).getValue(PreyPileBlock.DEAD), "a fresh pile looks dead");
                    }
                    // Every worker a day and a quarter without a meal: hungry, not yet slow.
                    hungrySince[0] = level.getGameTime() - 30000;
                    for (MatabeleEntity ant : TestIsolation.insectsOf(level, colony, MatabeleEntity.class)) {
                        ant.setLastMeal(hungrySince[0]);
                    }
                })
                .thenWaitUntil(() -> {
                    long fed = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream().filter(ant -> ant.getLastMeal() > hungrySince[0]).count();
                    helper.assertTrue(fed >= 3, "only " + fed + " hungry workers have eaten from the pile" + pile.describe(level));
                    helper.assertTrue(pile.edible() < 6, "nobody took a termite off the pile" + pile.describe(level));
                })
                .thenExecute(() -> {
                    pile.stock(level, 2); // (the queen may have been fed the rest meanwhile)
                    int good = pile.edible();
                    helper.assertTrue(good > 0, "nothing left on the pile to rot");
                    pile.ageForTests(level, PreyPile.ROT_TICKS);
                    helper.assertTrue(pile.edible() == 0 && pile.rotten() == good, "a day on, " + pile.edible() + " layers still good and "
                            + pile.rotten() + " rotten, not 0 and " + good);
                    for (BlockPos pos : heaps(level, colony)) {
                        helper.assertTrue(level.getBlockState(pos).getValue(PreyPileBlock.DEAD), "a rotten pile still twitches at " + pos.toShortString());
                    }
                })
                .thenWaitUntil(() -> {
                    // Carried out, by real ants or (after a while) the numbers: the room clear, a heap of remains beside the entrance.
                    helper.assertTrue(pile.rotten() == 0, pile.rotten() + " rotten layers still to carry out" + pile.describe(level));
                    helper.assertTrue(heaps(level, colony).isEmpty(), "prey pile heaps still in the nest at " + heaps(level, colony) + pile.describe(level));
                    BlockPos heap = pile.heap();
                    helper.assertTrue(heap != null && level.getBlockState(heap).is(SavannaBlocks.TERMITE_REMAINS.get()),
                            "no heap of termite remains beside the entrance" + pile.describe(level));
                    double off = Math.sqrt(heap.distToLowCornerSqr(colony.getCenter().getX(), heap.getY(), colony.getCenter().getZ()));
                    helper.assertTrue(off >= 1.5 && off <= 5, "the heap lies " + off + " blocks from the entrance");
                })
                .thenSucceed();
    }

    /** An empty pile: the hungry workers find nothing to eat and starve, and the colony counts them gone. */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aMatabeleColonyWithAnEmptyPreyPileStarves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = settled(helper);
        PreyPile pile = colony.part(SavannaParts.PREY_PILE).orElse(null);
        helper.assertTrue(pile != null, "a Matabele colony has no prey pile");
        List<MatabeleEntity> ants = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(colony.nest().getFinishedRoomCenter(level) != null, "the queen's room is not open yet"))
                .thenExecute(() -> {
                    helper.assertTrue(pile.edible() == 0, "a new colony's pile is not empty");
                    // Every worker a few seconds short of starving.
                    long starve = colony.profile().worker().starveAfterTicks();
                    ants.addAll(TestIsolation.insectsOf(level, colony, MatabeleEntity.class));
                    helper.assertTrue(ants.size() == 10, ants.size() + " real workers, not 10");
                    for (MatabeleEntity ant : ants) {
                        ant.setLastMeal(level.getGameTime() - starve + 200);
                    }
                })
                .thenWaitUntil(() -> {
                    long alive = ants.stream().filter(MatabeleEntity::isAlive).count();
                    helper.assertTrue(alive == 0, alive + " workers still alive with nothing to eat");
                    helper.assertTrue(colony.getCount(Caste.WORKER) == 0, colony.getCount(Caste.WORKER) + " workers still counted after all starved");
                })
                .thenSucceed();
    }

    /** A colony of 10, all real, settled in a month-old dead mound in the arena's middle. */
    private static Colony settled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestScenes.deepGround(helper);
        Colony termites = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE)), NewLandMounds.pickFor("dead", 30, 5L));
        helper.assertTrue(termites != null, "no dead mound put in");
        Colony colony = MatabeleCommands.makeColony(level, helper.absolutePos(new BlockPos(MIDDLE + 9, SURFACE, MIDDLE)), 10);
        helper.assertTrue(colony != null, "no Matabele colony made");
        return colony;
    }

    /** The prey pile's heaps in the nest's rooms. */
    private static List<BlockPos> heaps(ServerLevel level, Colony colony) {
        List<BlockPos> heaps = new ArrayList<>();
        for (BlockPos pos : colony.nest().getAllRoomCells(level)) {
            if (level.getBlockState(pos).is(SavannaBlocks.PREY_PILE.get())) {
                heaps.add(pos);
            }
        }
        for (BlockPos pos : colony.nest().getQueenRoomCells(level)) {
            if (!heaps.contains(pos) && level.getBlockState(pos).is(SavannaBlocks.PREY_PILE.get())) {
                heaps.add(pos);
            }
        }
        return heaps;
    }
}
