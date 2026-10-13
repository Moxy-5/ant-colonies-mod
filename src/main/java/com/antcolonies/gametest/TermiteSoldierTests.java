package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.FungusCombBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Soldiers;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Termite soldiers (M7 step 6a): nymphs grow into minor and major soldiers when the colony is short of them, nurses
 * feed the soldiers fungus nodules, the soldiers stand guard inside the nest (majors at both ends of the royal chamber's
 * tunnel, minors in the foraging tunnels), they drum standing still, and they do not fit the termites'
 * tunnels.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteSoldierTests {
    /**
     * Soldiers on demand. A colony of 10 workers wants one minor: the first nymph to hatch grows into a minor (with the
     * minor's meal), the next into a worker. At 20 workers (as many as it may have) it wants 2 minors and a major: the
     * next nymphs grow into a minor and a major (the major's bigger meal), then a worker. A nymph keeps what it grows
     * into after a reload. Fed their meals, the minor and the major nymphs grow into a minor and a major soldier of the
     * colony. A worker nymph that has eaten its fill while the colony has all the workers it may have grows into a
     * soldier instead once the colony is short of one (it then eats the soldier's meal first).
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void nymphsGrowIntoSoldiersWhenTheColonyNeedsThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        AntLarvaEntity[] nymphs = new AntLarvaEntity[5];
        helper.runAfterDelay(5, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            var numbers = colony.profile().soldiers().orElseThrow();
            int workerMeal = colony.profile().brood().larvaMeal();
            colony.setCount(WorkerAntEntity.CASTE, 10);
            nymphs[0] = hatch(helper, colony, 0);
            nymphs[1] = hatch(helper, colony, 1);
            helper.assertTrue(nymphs[0].getSoldierRank() == Rank.MINOR, "with 10 workers the first nymph should grow into a minor: "
                    + nymphs[0].getSoldierRank());
            helper.assertTrue(nymphs[0].getMeal() == numbers.minorMeal(), "the minor nymph's meal is " + nymphs[0].getMeal());
            helper.assertTrue(nymphs[1].getSoldierRank() == null && nymphs[1].getMeal() == workerMeal, "the second nymph should grow into a worker");
            colony.setCount(WorkerAntEntity.CASTE, colony.profile().colony().maxWorkers());
            nymphs[2] = hatch(helper, colony, 2);
            nymphs[3] = hatch(helper, colony, 3);
            nymphs[4] = hatch(helper, colony, 4);
            helper.assertTrue(nymphs[2].getSoldierRank() == Rank.MINOR, "the second minor: " + nymphs[2].getSoldierRank());
            helper.assertTrue(nymphs[3].getSoldierRank() == Rank.MAJOR && nymphs[3].getMeal() == numbers.majorMeal(),
                    "the major: " + nymphs[3].getSoldierRank() + ", meal " + nymphs[3].getMeal());
            helper.assertTrue(nymphs[4].getSoldierRank() == null, "with every soldier on its way the next nymph grows into a worker");
            // Saved and read back, the major nymph is still one.
            AntLarvaEntity copy = new AntLarvaEntity(ModEntities.TERMITE_NYMPH.get(), level);
            copy.load(nymphs[3].saveWithoutId(new CompoundTag()));
            helper.assertTrue(copy.getSoldierRank() == Rank.MAJOR, "after a reload the major nymph is a " + copy.getSoldierRank());
            // Fed their meals: they grow up.
            nymphs[0].setFed(nymphs[0].getMeal());
            nymphs[3].setFed(nymphs[3].getMeal());
        });
        helper.runAfterDelay(60, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(!nymphs[0].isAlive() && !nymphs[3].isAlive(), "the fed soldier nymphs did not grow up");
            List<TermiteSoldierEntity> soldiers = colony.soldiers().getSoldiers(level);
            helper.assertTrue(soldiers.stream().filter(s -> s.getRank() == Rank.MINOR).count() == 1
                    && soldiers.stream().filter(s -> s.getRank() == Rank.MAJOR).count() == 1, "expected a minor and a major soldier, found "
                    + soldiers.stream().map(TermiteSoldierEntity::getRank).toList());
            helper.assertTrue(colony.soldiers().count(Rank.MINOR) == 1 && colony.soldiers().count(Rank.MAJOR) == 1,
                    "the colony counts " + colony.soldiers().describe(level));
            // The worker nymph eats its fill, but the colony has all its workers: it waits (the second minor is on its way).
            nymphs[1].setFed(nymphs[1].getMeal());
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(nymphs[1].isAlive() && nymphs[1].getSoldierRank() == null, "the full worker nymph should wait as a worker nymph");
            // The second minor nymph is gone (as if it had died): now the colony is short of a minor.
            nymphs[2].discard();
        });
        helper.runAfterDelay(140, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(nymphs[1].isAlive() && nymphs[1].getSoldierRank() == Rank.MINOR, "the waiting worker nymph should now grow into a minor: "
                    + nymphs[1].getSoldierRank());
            helper.assertTrue(!nymphs[1].isFull() && nymphs[1].getMeal() == colony.profile().soldiers().orElseThrow().minorMeal(),
                    "it should eat the minor's meal first");
            helper.succeed();
        });
    }

    /** An egg of the colony laid next to the queen and hatched at once; the new nymph. */
    private static AntLarvaEntity hatch(GameTestHelper helper, Colony colony, int index) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = helper.absolutePos(new BlockPos(MIDDLE - 4 + index * 2, SURFACE, MIDDLE + 4));
        AntEggEntity egg = colony.brood().spawnEgg(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        helper.assertTrue(colony.brood().hatchEgg(level, egg), "the egg did not hatch");
        return colony.brood().getLarvae(level).stream().filter(nymph -> nymph.blockPosition().equals(spot)).findFirst()
                .orElseThrow(() -> new AssertionError("no nymph at " + spot));
    }

    /**
     * Soldiers stand guard. A colony with a royal chamber, a garden with fungus comb and nodules, and both foraging
     * tunnels dug out (by day, three young workers: one of them the nurse). Two majors and a minor are put in the royal
     * chamber. The majors walk to the first two majors' posts (both ends of the royal chamber's tunnel: its doorway, and
     * the way in from the shaft),
     * the minor to a post in a foraging tunnel, and each stands there facing out. One major is hungry: a nurse brings it
     * a nodule (it holds still for her).
     */
    @GameTest(template = "arena", timeoutTicks = 6000, skyAccess = true)
    public static void soldiersStandGuardAndANurseFeedsAHungrySoldier(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 3; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i, SURFACE, MIDDLE - 2);
            }
        });
        List<TermiteSoldierEntity> soldiers = new ArrayList<>();
        long[] hungrySince = {-1};
        int[] guarding = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            colony.setForagingTimeForTests(false); // day: everybody stays in
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(level.getGameTime()); // young: the colony's nurse is one of them
            }
            digNestAtOnce(level, colony);
            NestPlan.Chamber garden = colony.nest().getGardens(level).get(0);
            for (int i = 0; i < 3; i++) {
                level.setBlock(garden.room().get(garden.room().size() - 1 - i), ModBlocks.FUNGUS_COMB.get().defaultBlockState()
                        .setValue(SeedPileBlock.LAYERS, 2).setValue(FungusCombBlock.NODULES, FungusCombBlock.MAX_NODULES), 3);
            }
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            soldiers.add(put(level, ModEntities.TERMITE_MAJOR_SOLDIER.get(), room.get(0)));
            soldiers.add(put(level, ModEntities.TERMITE_MAJOR_SOLDIER.get(), room.get(1)));
            soldiers.add(put(level, ModEntities.TERMITE_MINOR_SOLDIER.get(), room.get(2)));
        });
        helper.runAfterDelay(40, () -> {
            // Joined, and the first major has gone without food for longer than a day.
            Colony colony = TermiteMoundTests.colony(helper);
            for (TermiteSoldierEntity soldier : soldiers) {
                helper.assertTrue(colony.getId().equals(soldier.getColonyId()), "a soldier did not join the colony");
            }
            hungrySince[0] = level.getGameTime();
            soldiers.get(0).setLastFed(hungrySince[0] - colony.profile().soldiers().orElseThrow().mealIntervalTicks() - 100);
            helper.assertTrue(soldiers.get(0).wantsFood(), "the major is not hungry");
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || soldiers.size() < 3 || hungrySince[0] < 0) {
                return;
            }
            guarding[0] = allAtTheirPosts(level, colony, soldiers) == null ? guarding[0] + 1 : 0;
            if (helper.getTick() % 500 == 0) {
                AntColoniesMod.LOGGER.info("[soldier-test] tick {}: {}; {}; hungry major fed at {}", helper.getTick(),
                        allAtTheirPosts(level, colony, soldiers), colony.soldiers().describe(level), soldiers.get(0).getLastFed());
            }
        });
        helper.succeedWhen(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(hungrySince[0] >= 0, "not started");
            helper.assertTrue(soldiers.get(0).getLastFed() >= hungrySince[0] && !soldiers.get(0).wantsFood(),
                    "no nurse has fed the hungry major yet (nodules " + colony.garden().available(level) + ")");
            String wrong = allAtTheirPosts(level, colony, soldiers);
            helper.assertTrue(wrong == null, wrong);
            helper.assertTrue(guarding[0] >= 60, "the soldiers have stood at their posts for only " + guarding[0] + " ticks");
            AntColoniesMod.LOGGER.info("[soldier-test] all at their posts at tick {}: {} (major posts {}, minor posts {}); hungry major fed at {}",
                    helper.getTick(), soldiers.stream().map(s -> s.getRank() + " " + s.blockPosition().toShortString()).toList(),
                    colony.soldiers().posts(level, Rank.MAJOR), colony.soldiers().posts(level, Rank.MINOR), soldiers.get(0).getLastFed());
        });
    }

    /** A soldier of this kind put at the middle of this cell (it joins the nearest termite colony). */
    private static TermiteSoldierEntity put(ServerLevel level, EntityType<TermiteSoldierEntity> type, BlockPos cell) {
        TermiteSoldierEntity soldier = type.create(level);
        soldier.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(soldier);
        return soldier;
    }

    /**
     * Null if the two majors stand at the first two majors' posts and the minor at a minor's post, each on the floor
     * facing out; otherwise what is wrong.
     */
    private static String allAtTheirPosts(ServerLevel level, Colony colony, List<TermiteSoldierEntity> soldiers) {
        List<Soldiers.Post> majorPosts = colony.soldiers().posts(level, Rank.MAJOR);
        List<Soldiers.Post> minorPosts = colony.soldiers().posts(level, Rank.MINOR);
        if (majorPosts.size() < 3 || minorPosts.size() < 2) {
            return "posts missing: majors " + majorPosts + ", minors " + minorPosts;
        }
        Set<BlockPos> wanted = new HashSet<>(List.of(majorPosts.get(0).cell(), majorPosts.get(1).cell()));
        Set<BlockPos> majorsAt = new HashSet<>();
        for (TermiteSoldierEntity soldier : soldiers) {
            List<Soldiers.Post> posts = soldier.getRank() == Rank.MAJOR ? majorPosts.subList(0, 2) : minorPosts;
            Soldiers.Post post = posts.stream().filter(p -> p.cell().equals(soldier.blockPosition())).findFirst().orElse(null);
            if (post == null || soldier.isClimbing()) {
                return soldier.getRank() + " soldier at " + soldier.blockPosition().toShortString() + (soldier.isClimbing() ? " (climbing)" : "")
                        + " is not at a post of its kind " + posts.stream().map(p -> p.cell().toShortString()).toList();
            }
            float facing = (float) (Mth.atan2(post.facing().getZ() - post.cell().getZ(), post.facing().getX() - post.cell().getX())
                    * Mth.RAD_TO_DEG) - 90.0F;
            if (Math.abs(Mth.wrapDegrees(soldier.getYRot() - facing)) > 20.0F) {
                return soldier.getRank() + " soldier at " + soldier.blockPosition().toShortString() + " faces " + soldier.getYRot() + ", not out ("
                        + facing + ")";
            }
            if (soldier.getRank() == Rank.MAJOR) {
                majorsAt.add(soldier.blockPosition());
            }
        }
        return majorsAt.equals(wanted) ? null : "the majors stand at " + majorsAt + ", expected " + wanted;
    }

    /** Digs the termite colony's shaft, royal chamber, first garden room and foraging tunnels out at once. */
    private static void digNestAtOnce(ServerLevel level, Colony colony) {
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos pos = colony.getCenter().below(); pos.getY() >= bottom.getY(); pos = pos.below()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        List<BlockPos> cells = new ArrayList<>();
        for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
            cells.addAll(chamber.cells());
        }
        cells.addAll(colony.nest().getGardens(level).get(0).cells());
        for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
            cells.addAll(tunnel.tunnel());
        }
        for (BlockPos pos : cells) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** A soldier told to drum stands still drumming for that long, then stops. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aSoldierDrumsStandingStill(GameTestHelper helper) {
        TermiteMoundTests.deepGround(helper);
        TermiteSoldierEntity soldier = TestAnts.loneSoldier(helper, true, MIDDLE, SURFACE, MIDDLE);
        double[] at = new double[2];
        helper.runAfterDelay(5, () -> soldier.drum(60));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(soldier.isDrumming(), "the soldier is not drumming");
            at[0] = soldier.getX();
            at[1] = soldier.getZ();
        });
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(soldier.isDrumming(), "the soldier stopped drumming too soon");
            double moved = Math.hypot(soldier.getX() - at[0], soldier.getZ() - at[1]);
            helper.assertTrue(moved < 0.05, "the drumming soldier moved " + moved + " blocks");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertFalse(soldier.isDrumming(), "the soldier is still drumming");
            helper.succeed();
        });
    }

    /** Soldiers are too big for the termite tunnel blocks: a major finds no way into a tunnel a worker crawls through. */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void soldiersFindNoWayIntoTermiteTunnels(GameTestHelper helper) {
        TermiteTunnelTests.lumpWithATunnel(helper);
        TermiteSoldierEntity major = TestAnts.loneSoldier(helper, true, 3, 1, 10);
        TermiteSoldierEntity minor = TestAnts.loneSoldier(helper, false, 3, 1, 12);
        TermiteWorkerEntity worker = TestAnts.loneTermite(helper, 3, 1, 8);
        helper.runAfterDelay(10, () -> {
            for (BlockPos cell : List.of(TermiteTunnelTests.TUNNEL.get(1), TermiteTunnelTests.TUNNEL.get(3))) {
                helper.assertFalse(major.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "a major found a way into " + cell);
                helper.assertFalse(minor.getNavigator().moveTo(helper.absolutePos(cell), 1.0), "a minor found a way into " + cell);
            }
            helper.assertTrue(worker.getNavigator().moveTo(helper.absolutePos(TermiteTunnelTests.TUNNEL.get(3)), 1.0), "a worker found no way in");
            helper.succeed();
        });
    }
}
