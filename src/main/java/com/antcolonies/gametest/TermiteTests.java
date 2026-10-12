package com.antcolonies.gametest;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.TermiteKingEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.entity.goal.StayWithQueenGoal;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Automatic in-game tests for the termites (M7): the royal pair founds a colony and the queen swells. */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteTests {
    private static final int SURFACE = 12;
    private static final int MIDDLE = 12;
    /** The founding nest: a 3-deep shaft, a 3-long tunnel and the 3x3 royal chamber. */
    private static final int CHAMBER_CELLS = 3 + 3 + 9;

    /**
     * A queen and a king are put down near each other (and a worker ant, who must not join them). The king joins her,
     * the pair dig the shaft, tunnel and royal chamber, the king closes the entrance from inside, and they settle in
     * the chamber: she in the middle, he beside her.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void royalPairDigsTheirChamberAndSealsTheEntrance(GameTestHelper helper) {
        foundColony(helper, true);
        WorkerAntEntity[] ant = {null};
        helper.runAfterDelay(5, () -> ant[0] = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE - 3, SURFACE, MIDDLE));

        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = colony(helper);
            helper.assertTrue(colony != null, "no termite colony yet");
            helper.assertTrue(colony.getSpecies().equals(Species.MACROTERMES), "the colony is " + colony.getSpecies());
            helper.assertTrue(colony.getCount(TermiteKingEntity.CASTE) == 1, "the colony has " + colony.getCount(TermiteKingEntity.CASTE) + " kings");
            helper.assertTrue(ant[0] != null && !colony.getId().equals(ant[0].getColonyId()), "the worker ant joined the termites");
            helper.assertTrue(colony.nest().getPlannedChambers(level).size() == 1, "the founding nest should have 1 level");
            helper.assertTrue(colony.nest().findDigTarget(level) == null, "still digging: " + airBelowSurface(helper) + " of " + CHAMBER_CELLS);
            helper.assertTrue(airBelowSurface(helper) == CHAMBER_CELLS, airBelowSurface(helper) + " open cells, expected " + CHAMBER_CELLS);
            helper.assertBlockPresent(ModBlocks.BURROWED_DIRT.get(), new BlockPos(MIDDLE, SURFACE, MIDDLE));
            TermiteQueenEntity queen = (TermiteQueenEntity) colony.getQueen(level);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(queen.blockPosition().equals(room), "the queen is not in the middle of the chamber");
            var kings = level.getEntitiesOfClass(TermiteKingEntity.class, helper.getBounds());
            helper.assertTrue(kings.size() == 1 && colony.getId().equals(kings.get(0).getColonyId()), "the king is missing or not in her colony");
            BlockPos beside = StayWithQueenGoal.spotBesideQueen(level, colony);
            helper.assertTrue(kings.get(0).blockPosition().equals(beside), "the king is not beside the queen: " + kings.get(0).blockPosition().subtract(beside));
        });
    }

    /** A queen without a king digs alone and closes the entrance herself. */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void aQueenWithoutAKingSealsTheEntranceHerself(GameTestHelper helper) {
        foundColony(helper, false);
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && colony.nest().findDigTarget(level) == null, "still digging");
            helper.assertBlockPresent(ModBlocks.BURROWED_DIRT.get(), new BlockPos(MIDDLE, SURFACE, MIDDLE));
            helper.assertTrue(colony.getQueen(level).blockPosition().equals(colony.nest().getFinishedRoomCenter(level)),
                    "the queen is not in the middle of the chamber");
        });
    }

    /**
     * The founding batch: once the pair have sealed themselves in, the queen lays 5 eggs in the chamber, they hatch into
     * nymphs, the pair feed the nymphs on their own fat until they grow into the first 5 workers, and the workers dig
     * the entrance open again.
     */
    @GameTest(template = "arena", timeoutTicks = 45000, skyAccess = true)
    public static void theFoundingBatchGrowsIntoWorkersWhoOpenTheEntrance(GameTestHelper helper) {
        foundColony(helper, true);
        boolean[] sealed = {false};
        helper.onEachTick(() -> {
            if (!sealed[0] && helper.getBlockState(new BlockPos(MIDDLE, SURFACE, MIDDLE)).is(ModBlocks.BURROWED_DIRT.get())) {
                sealed[0] = true;
                AntColoniesMod.LOGGER.info("[termite-test] tick {}: sealed in", helper.getTick());
            }
            Colony colony = colony(helper);
            if (colony != null && helper.getTick() % 400 == 0) {
                AntColoniesMod.LOGGER.info("[termite-test] tick {}: eggs laid {}, eggs {}, nymphs {}, workers {}", helper.getTick(),
                        colony.queenCare().getEggsLaid(), colony.brood().countEggs(helper.getLevel()),
                        colony.brood().countLarvae(helper.getLevel()), colony.getCount(TermiteWorkerEntity.CASTE));
            }
        });
        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            Colony colony = colony(helper);
            helper.assertTrue(colony != null && sealed[0], "not sealed in yet");
            helper.assertTrue(colony.queenCare().getEggsLaid() == 5, colony.queenCare().getEggsLaid() + " eggs laid, expected 5");
            helper.assertTrue(colony.brood().countBrood(level) == 0, "still " + colony.brood().countBrood(level) + " eggs and nymphs");
            var workers = level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds());
            helper.assertTrue(workers.size() == 5 && colony.getCount(TermiteWorkerEntity.CASTE) == 5,
                    workers.size() + " termite workers, the colony counts " + colony.getCount(TermiteWorkerEntity.CASTE) + ", expected 5");
            helper.assertTrue(workers.stream().allMatch(w -> colony.getId().equals(w.getColonyId())), "a worker is not in the colony");
            helper.assertTrue(colony.nest().isEntranceOpen(level), "the entrance is still closed");
        });
    }

    /** A queen swollen past the profile's too_big_to_walk_at cannot walk any more, and her hitbox has grown. */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aSwollenQueenCanNoLongerWalk(GameTestHelper helper) {
        ground(helper);
        TermiteQueenEntity queen = helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        Vec3[] start = {null};
        helper.runAfterDelay(10, () -> {
            queen.setSize(0.6F);
            start[0] = queen.position();
            queen.getNavigator().moveTo(helper.absolutePos(new BlockPos(MIDDLE + 6, SURFACE, MIDDLE)), 1.0);
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(queen.isTooBigToWalk(), "a queen of size 0.6 should be too big to walk");
            helper.assertTrue(queen.position().distanceTo(start[0]) < 0.1, "the swollen queen moved " + queen.position().distanceTo(start[0]) + " blocks");
            helper.assertTrue(queen.getBbWidth() > 0.6F, "her hitbox did not grow: " + queen.getBbWidth());
            helper.succeed();
        });
    }

    private static void ground(GameTestHelper helper) {
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
    }

    private static void foundColony(GameTestHelper helper, boolean withKing) {
        helper.getLevel().getServer().tickRateManager().setTickRate(2000.0F);
        ground(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        if (withKing) {
            helper.runAfterDelay(5, () -> helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 2, SURFACE, MIDDLE + 1));
        }
        helper.onEachTick(() -> {
            if (helper.getTick() % 400 == 0) {
                Colony colony = colony(helper);
                AntColoniesMod.LOGGER.info("[termite-test] tick {}: {} of {} cells dug, entrance {}", helper.getTick(),
                        airBelowSurface(helper), CHAMBER_CELLS, helper.getBlockState(new BlockPos(MIDDLE, SURFACE, MIDDLE)));
                if (colony != null) {
                    AntColoniesMod.LOGGER.info("[termite-test] dig target {}, home {}", colony.nest().findDigTarget(helper.getLevel()),
                            colony.nest().getFinishedRoomCenter(helper.getLevel()));
                }
            }
        });
    }

    /** The termite colony in this test area (one with a living queen). */
    private static Colony colony(GameTestHelper helper) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(colony -> area.contains(Vec3.atCenterOf(colony.getCenter())) && colony.getQueen(helper.getLevel()) != null
                        && colony.getSpecies().equals(Species.MACROTERMES))
                .findFirst().orElse(null);
    }

    private static int airBelowSurface(GameTestHelper helper) {
        int air = 0;
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE; y++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        air++;
                    }
                }
            }
        }
        return air;
    }
}
