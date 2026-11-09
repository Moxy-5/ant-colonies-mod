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
import com.antcolonies.savanna.registry.SavannaEntities;

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
        MatabeleEntity[] clamped = new MatabeleEntity[1];
        MatabeleEntity[] wounded = new MatabeleEntity[2];
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
                    // M8 step 2c: a worker in the nest comes home with a dead termite soldier's head locked on a leg.
                    MatabeleEntity hurt = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                            .filter(MatabeleEntity::canLoseLegs).findFirst().orElse(null);
                    helper.assertTrue(hurt != null, "no Matabele worker to put a clamped head on");
                    BlockPos room = colony.nest().getFinishedRoomCenter(level);
                    hurt.moveTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5, hurt.getYRot(), 0.0F); // home in the queen's room
                    com.antcolonies.entity.AbstractAntEntity holder = SavannaEntities.TERMITE_MAJOR_SOLDIER.get().create(level);
                    holder.moveTo(hurt.getX(), hurt.getY(), hurt.getZ(), 0.0F, 0.0F);
                    level.addFreshEntity(holder);
                    helper.assertTrue(hurt.clamp().clampOn(holder), "the soldier could not clamp on");
                    holder.kill();
                    hurt.clamp().becomeHead();
                    clamped[0] = hurt;
                    // M8 step 5: two more come home with a leg gone, one wound fresh, one infected and smelling of it.
                    List<MatabeleEntity> others = TestIsolation.insectsOf(level, colony, MatabeleEntity.class).stream()
                            .filter(ant -> ant != hurt && ant.canLoseLegs()).limit(2).toList();
                    helper.assertTrue(others.size() == 2, "no two Matabele workers to wound");
                    for (int i = 0; i < 2; i++) {
                        MatabeleEntity ant = others.get(i);
                        ant.moveTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5, ant.getYRot(), 0.0F);
                        ant.loseLeg(i == 0 ? 1 : 4);
                        wounded[i] = ant;
                    }
                    com.antcolonies.fight.FightNumbers.Rescue n = com.antcolonies.entity.Wounds.numbers(wounded[1]);
                    helper.assertTrue(n != null, "the Matabele has no rescue numbers");
                    wounded[1].wounds().setForTests(n.smellTicks() + 20, true);
                    helper.assertTrue(wounded[0].wounds().wantsLicking() && wounded[1].wounds().wantsDab(), "the wounds want no care: "
                            + wounded[0].wounds().describe() + " / " + wounded[1].wounds().describe());
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
                // M8 step 2c: a nestmate in the nest takes the head off (real: 9 in 10 within a day, Frank et al. 2017).
                .thenWaitUntil(() -> helper.assertTrue(!clamped[0].isAlive() || !clamped[0].clamp().isOn(),
                        "nobody in the nest has bitten the termite head off the worker at " + helper.relativePos(clamped[0].blockPosition())
                                + " (goals " + clamped[0].runningGoals() + ")"))
                // M8 step 5: nestmates lick the fresh wound and dab gland secretion on the infected one (Frank et al. 2018, 2023).
                .thenWaitUntil(() -> helper.assertTrue(!wounded[0].isAlive() || wounded[0].wounds().licks() >= 1,
                        "nobody licked the fresh wound: " + wounded[0].wounds().describe() + " at " + helper.relativePos(wounded[0].blockPosition())))
                .thenWaitUntil(() -> helper.assertTrue(!wounded[1].isAlive() || wounded[1].wounds().isDabbed(),
                        "nobody treated the infected wound: " + wounded[1].wounds().describe() + " at " + helper.relativePos(wounded[1].blockPosition())))
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
        Colony termites = MatabeleScenes.deadMound(helper, MIDDLE, MIDDLE);
        Colony colony = MatabeleScenes.colony(helper, MIDDLE + 9, MIDDLE, 10);
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
