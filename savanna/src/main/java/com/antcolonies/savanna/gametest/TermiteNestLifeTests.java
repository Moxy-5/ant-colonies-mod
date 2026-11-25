package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.*;

import java.util.List;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.gametest.TestIsolation;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The shared colony rules of the nest's life, on a termite colony: young waiting when the colony is full, a young one
 * and the queen starving, the nest's plan and an insect's load kept over a save and load. These were the red harvester
 * ant's nest tests until it left the mod (2026-11-23, {@code docs/plans/harvester-removal.md}); the rules are the shared
 * brain's ({@code Brood}, {@code QueenCare}, {@code Nest}, the one load slot), which the termites live by too.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteNestLifeTests {
    /** A termite colony with so many real workers south of the queen, its nest dug at once and no hunger; {@code then} gets it after 10 ticks. */
    private static void colony(GameTestHelper helper, int workers, java.util.function.Consumer<Colony> then) {
        TermiteScenes.colony(helper, TermiteScenes.spots(workers, i -> new BlockPos(MIDDLE - 2 + i % 5, SURFACE, MIDDLE + 3 + i / 5)), colony -> {
            helper.assertTrue(colony != null, "no termite colony");
            colony.setHungerOn(false);
            digNestAtOnce(helper.getLevel(), colony);
            then.accept(colony);
        });
    }

    /** The colony's real workers, wherever they are. */
    private static List<TermiteWorkerEntity> workers(GameTestHelper helper, Colony colony) {
        return TestIsolation.insectsOf(helper.getLevel(), colony, TermiteWorkerEntity.class);
    }

    /**
     * When the colony already has one worker less than the most it can have, three fully fed nymphs (all to be workers:
     * the colony has soldiers enough) make exactly one worker; the other two stay nymphs.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void nymphsWaitWhenTheColonyHasEnoughWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony[] found = {null};
        long[] placedAt = {0};
        int[] realBefore = {0};
        colony(helper, 0, colony -> {
            colony.population().setOnForTests(false);
            long now = level.getGameTime();
            int most = colony.profile().colony().maxWorkers();
            // Soldiers enough for a full colony, so every new young one is to be a worker.
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, most);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, most);
            colony.population().addNewMembers(Caste.WORKER, now, most - 1);
            helper.assertTrue(colony.getCount(Caste.WORKER) == most - 1, colony.getCount(Caste.WORKER) + " workers counted, expected " + (most - 1));
            realBefore[0] = workers(helper, colony).size();
            for (int i = 0; i < 3; i++) {
                BlockPos cell = colony.brood().pickEggSpot(level, level.getRandom());
                helper.assertTrue(cell != null, "no room for a nymph");
                AntLarvaEntity nymph = colony.brood().spawnLarva(level, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
                nymph.setGrowsInto(colony, null); // a worker, whatever the soldier count asked for
                nymph.setFed(colony.profile().brood().larvaMeal());
            }
            placedAt[0] = level.getGameTime();
            found[0] = colony;
        });
        helper.succeedWhen(() -> {
            Colony colony = found[0];
            helper.assertTrue(colony != null, "no nymphs have been put down yet");
            int most = colony.profile().colony().maxWorkers();
            // Wait well past the moment the first one grows up, so the other two had every chance to grow up too.
            helper.assertTrue(level.getGameTime() >= placedAt[0] + 600, "still waiting for the nymphs to have grown up");
            helper.assertTrue(colony.getCount(Caste.WORKER) == most, "the colony counts " + colony.getCount(Caste.WORKER) + " workers, expected " + most);
            helper.assertTrue(colony.brood().countLarvae(level) == 2, colony.brood().countLarvae(level) + " nymphs are left, expected 2");
            helper.assertTrue(workers(helper, colony).size() == realBefore[0] + 1,
                    "expected exactly one new real worker, but there are " + workers(helper, colony).size() + " and there were " + realBefore[0]);
        });
    }

    /**
     * A nymph that goes its whole starve time without food starves and leaves a body; a nymph that was just fed lives
     * on, and so does a full one that waits to grow up. A body a worker has picked up counts too.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true, batch = "larva_alone")
    public static void aNymphThatIsNotFedStarves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AntLarvaEntity[] nymphs = new AntLarvaEntity[3];
        Colony[] found = {null};
        colony(helper, 0, colony -> {
            colony.setHungerOn(true);
            long now = level.getGameTime();
            // Soldiers enough, so all three are to be workers (a worker's meal is the profile's larva_meal).
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 20);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 20);
            BlockPos base = helper.absolutePos(new BlockPos(MIDDLE + 4, SURFACE, MIDDLE + 4));
            for (int i = 0; i < 3; i++) {
                nymphs[i] = colony.brood().spawnLarva(level, base.getX() + 0.5 + i, base.getY(), base.getZ() + 0.5);
                nymphs[i].setLastFed(now - colony.profile().brood().larvaStarveTicks() + 60); // three seconds from starving
            }
            nymphs[1].feed(); // fed just now: starts over
            nymphs[2].setFed(colony.profile().brood().larvaMeal()); // full: nothing to starve of (it waits for a place as a worker, or grows up)
            found[0] = colony;
        });
        helper.succeedWhen(() -> {
            Colony colony = found[0];
            helper.assertTrue(colony != null && nymphs[0] != null && !nymphs[0].isAlive(), "the unfed nymph has not starved yet");
            helper.assertTrue(nymphs[1].isAlive(), "the nymph that was just fed starved");
            helper.assertTrue(!nymphs[2].isAlive() || nymphs[2].isFull(), "the full nymph is not full");
            int carried = (int) workers(helper, colony).stream().filter(TermiteWorkerEntity::isCarryingCorpse).count();
            int lying = TestIsolation.insectsOf(helper, com.antcolonies.entity.AntCorpseEntity.class,
                    corpse -> corpse.isAlive() && colony.getId().equals(corpse.getColonyId())).size();
            helper.assertTrue(lying + carried == 1, (lying + carried) + " bodies (" + carried + " carried), expected 1");
        });
    }

    /**
     * A queen who gets no food for her starve time starves. Her colony carries on without her, queenless (no new eggs),
     * until its last worker dies of old age: then it ends.
     */
    @GameTest(template = "arena", timeoutTicks = 3000, skyAccess = true)
    public static void aTermiteQueenThatIsNotFedStarvesAndHerColonyDwindles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.antcolonies.entity.AbstractAntEntity[] queen = {null};
        Colony[] colony = {null};
        int[] phase = {0};
        colony(helper, 1, found -> {
            colony[0] = found;
            found.setHungerOn(true);
            // Fresh forage in the garden: the colony has had its first comb, so its queen gets hungry (no nodule to feed her).
            BlockPos spot = found.food().pickFoodSpot(level, level.getRandom());
            helper.assertTrue(spot != null, "no spot for comb in the garden");
            found.food().depositSeed(level, spot);
            queen[0] = found.getQueen(level);
            helper.assertTrue(queen[0] != null, "the colony has no queen");
            found.queenCare().setQueenLastFed(level.getGameTime() - found.profile().queen().starveTicks() + 60); // three seconds from starving
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            if (phase[0] == 1 && !queen[0].isAlive()) {
                ColonyManager manager = ColonyManager.get(level);
                helper.assertTrue(manager.getColony(colony[0].getId()) == colony[0] && colony[0].isQueenless(),
                        "the colony should carry on without its queen");
                List<TermiteWorkerEntity> workers = workers(helper, colony[0]);
                helper.assertTrue(workers.size() == 1, "expected the one worker, found " + workers.size());
                // No young left to grow up (this test is about the workers), and the worker is at the end of its life.
                colony[0].brood().getEggs(level).forEach(net.minecraft.world.entity.Entity::discard);
                colony[0].brood().getLarvae(level).forEach(net.minecraft.world.entity.Entity::discard);
                workers.get(0).setGrownUpAt(level.getGameTime() - colony[0].profile().worker().lifespanTicks() * 2L);
                phase[0] = 2;
            } else if (phase[0] == 2 && ColonyManager.get(level).getColony(colony[0].getId()) == null) {
                helper.succeed(); // the last worker died of old age: the colony has died out
            }
        });
    }

    /**
     * After a save and reload the colony keeps the nest it was digging: with a heap of comb lying in the royal chamber and
     * one in a garden, the reloaded colony plans exactly the same rooms (it must not start a new nest in another
     * direction), from an old save without a stored plan too, and with a torch a player put on a room's floor.
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void theTermiteNestPlanIsTheSameAfterAReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        colony(helper, 0, colony -> {
            List<NestPlan.Chamber> before = colony.nest().getPlannedChambers(level);
            helper.assertTrue(before.size() == 1, "a termite nest has " + before.size() + " levels, expected the royal chamber alone");
            List<NestPlan.Chamber> gardensBefore = colony.nest().getGardens(level);
            var heap = colony.profile().food().pileBlock().defaultBlockState();
            NestPlan.Chamber royal = before.get(0);
            level.setBlock(royal.room().get(royal.room().size() - 1), heap, 3);
            if (!gardensBefore.isEmpty()) {
                NestPlan.Chamber garden = gardensBefore.get(0);
                level.setBlock(garden.room().get(garden.room().size() - 1), heap, 3);
            }
            // An old save has no nest plan stored: worked out again from the ground, the heaps count as dug out.
            CompoundTag oldSave = colony.save();
            oldSave.remove("nestDepth");
            oldSave.remove("nestDirection");
            oldSave.remove("nestLevels");
            oldSave.remove("nestFounding");
            List<NestPlan.Chamber> fromOldSave = Colony.load(oldSave).nest().getPlannedChambers(level);
            helper.assertTrue(fromOldSave.equals(before), "from an old save the nest plan changed: " + describe(before, colony)
                    + " became " + describe(fromOldSave, colony));
            // A torch a player put on the royal chamber's floor: only a saved plan gets past that.
            level.setBlock(royal.room().get(0), Blocks.TORCH.defaultBlockState(), 3);
            Colony reloaded = Colony.load(colony.save());
            List<NestPlan.Chamber> after = reloaded.nest().getPlannedChambers(level);
            helper.assertTrue(after.equals(before), "after the reload the nest plan changed: " + describe(before, colony)
                    + " became " + describe(after, colony));
            helper.assertTrue(reloaded.nest().getGardens(level).equals(gardensBefore), "the garden rooms changed after the reload");
            helper.succeed();
        });
    }

    /** The plan in a few words for the log: how many levels and which way the first tunnel leaves the shaft. */
    private static String describe(List<NestPlan.Chamber> plan, Colony colony) {
        if (plan.isEmpty()) {
            return "no plan";
        }
        BlockPos room = plan.get(0).center();
        return plan.size() + " levels, rooms toward " + net.minecraft.core.Direction.getNearest(room.getX() - colony.getCenter().getX(), 0,
                room.getZ() - colony.getCenter().getZ());
    }

    /**
     * What a termite holds in its mandibles survives a save and reload: soil, a piece of forage, a fungus nodule (in its
     * jaws and in its crop), an egg and a dead nestmate. And a worker that was reloaded while carrying an egg still takes
     * it to the young's room afterwards.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void whatATermiteCarriesIsKeptAfterAReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteWorkerEntity[] reloaded = {null};
        Colony[] found = {null};
        colony(helper, 1, colony -> {
            TermiteWorkerEntity worker = workers(helper, colony).get(0);
            // Every kind of load, one at a time, written to the save and read back into a new termite.
            worker.setCarriedBlock(Blocks.COARSE_DIRT.defaultBlockState());
            helper.assertTrue(Blocks.COARSE_DIRT.defaultBlockState().equals(reload(level, worker).getCarriedBlock()), "the soil pellet was lost");
            worker.setCarriedBlock(null);
            worker.setCarriedSeed(new ItemStack(SavannaItems.FORAGE.get()));
            helper.assertTrue(reload(level, worker).getCarriedSeed().is(SavannaItems.FORAGE.get()), "the piece of forage was lost");
            worker.setCarriedSeed(new ItemStack(SavannaItems.FUNGUS_NODULE.get()));
            helper.assertTrue(reload(level, worker).getCarriedSeed().is(SavannaItems.FUNGUS_NODULE.get()), "the nodule was lost");
            worker.setCarriedSeed(ItemStack.EMPTY);
            // Food in the crop, to pass on mouth to mouth (the one load slot, Apex part 3, T3: each row its own save words).
            worker.swallowToPassOn(new ItemStack(SavannaItems.FUNGUS_NODULE.get()));
            TermiteWorkerEntity fed = reload(level, worker);
            helper.assertTrue(fed.isFoodInCrop() && fed.getCarriedSeed().is(SavannaItems.FUNGUS_NODULE.get()), "the food in the crop was lost");
            worker.setCarriedSeed(ItemStack.EMPTY);
            // A save written before the one slot (every flag written, false ones too) still reads the same.
            CompoundTag old = new CompoundTag();
            worker.saveWithoutId(old);
            old.put("CarriedSeed", new ItemStack(SavannaItems.FORAGE.get()).save(level.registryAccess()));
            old.putBoolean("CarryingEgg", false);
            old.putBoolean("FoodInCrop", false);
            old.putBoolean("CarryingCorpse", false);
            TermiteWorkerEntity fromOld = SavannaEntities.TERMITE_WORKER.get().create(level);
            fromOld.load(old);
            helper.assertTrue(fromOld.isCarryingSeed() && !fromOld.isCarryingEgg() && !fromOld.isFoodInCrop() && !fromOld.isCarryingCorpse(),
                    "a piece in an old save did not read back as only a piece");
            worker.setCarryingCorpse(true);
            helper.assertTrue(reload(level, worker).isCarryingCorpse(), "the dead nestmate was lost");
            worker.setCarryingCorpse(false);
            helper.assertTrue(!reload(level, worker).hasFullMandibles(), "a termite with empty mandibles came back holding something");

            // The real thing: the worker is reloaded while it carries an egg, in a dug-out nest.
            worker.setCarryingEgg(true);
            reloaded[0] = reload(level, worker);
            reloaded[0].setUUID(java.util.UUID.randomUUID());
            worker.discard();
            level.addFreshEntity(reloaded[0]);
            found[0] = colony;
        });
        helper.succeedWhen(() -> {
            Colony colony = found[0];
            helper.assertTrue(colony != null && reloaded[0] != null, "not reloaded yet");
            helper.assertTrue(!reloaded[0].isCarryingEgg(), "the reloaded worker still holds the egg");
            List<com.antcolonies.entity.AntEggEntity> eggs = colony.brood().getEggs(level);
            helper.assertTrue(eggs.size() == 1, eggs.size() + " eggs, expected 1");
            helper.assertTrue(eggs.get(0).isPlaced(),
                    "the egg was not set down in its place (at " + helper.relativePos(eggs.get(0).blockPosition()) + ")");
        });
    }

    /**
     * A dead termite nobody can carry away (the colony has no workers) stays where it lies for the species profile's
     * {@code corpse_decay_ticks} and then crumbles to dust, never sooner.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void aDeadTermiteNobodyCarriesAwayCrumblesToDust(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        com.antcolonies.entity.AntCorpseEntity[] corpse = {null};
        Colony[] found = {null};
        colony(helper, 0, colony -> {
            BlockPos pos = helper.absolutePos(new BlockPos(MIDDLE + 5, SURFACE, MIDDLE + 5));
            corpse[0] = colony.refuse().spawnCorpse(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            found[0] = colony;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(corpse[0] != null && !corpse[0].isAlive(), "the body is still there");
            if (corpse[0].tickCount < found[0].profile().refuse().corpseDecayTicks()) {
                helper.fail("the body was gone after " + corpse[0].tickCount + " ticks, but nobody could have carried it away");
            }
        });
    }

    /**
     * A nurse with nothing to do waits beside the queen (the shared {@code StandByQueenGoal}): in a dug nest with no
     * hunger and no young, at midday, of the colony's 6 young workers the nurses end up within 3 blocks of her, one at
     * least, and stay.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void aTermiteNurseWithNothingToDoWaitsByTheQueen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony[] found = {null};
        int[] near = {0};
        colony(helper, 6, colony -> {
            com.antcolonies.gametest.TestClock.still(helper, 12.0);
            workers(helper, colony).forEach(worker -> worker.setGrownUpAt(level.getGameTime()));
            found[0] = colony;
        });
        helper.succeedWhen(() -> {
            Colony colony = found[0];
            helper.assertTrue(colony != null, "no colony yet");
            com.antcolonies.entity.AbstractAntEntity queen = colony.getQueen(level);
            helper.assertTrue(queen != null, "no queen");
            List<TermiteWorkerEntity> nurses = workers(helper, colony).stream()
                    .filter(worker -> colony.roleOf(level, worker) == com.antcolonies.colony.ColonyRole.NURSE).toList();
            helper.assertTrue(!nurses.isEmpty(), "none of the young workers is a nurse: " + colony.describeJobs(level));
            boolean by = nurses.stream().anyMatch(nurse -> nurse.distanceToSqr(queen.getX(), nurse.getY(), queen.getZ()) <= 3.0 * 3.0
                    && Math.abs(nurse.getY() - queen.getY()) < 1.5);
            near[0] = by ? near[0] + 1 : 0;
            helper.assertTrue(near[0] >= 100, "no nurse is waiting by the queen");
        });
    }

    /** A new worker read back from this worker's save, as after a reload (not put into the world). */
    private static TermiteWorkerEntity reload(ServerLevel level, TermiteWorkerEntity worker) {
        CompoundTag tag = new CompoundTag();
        worker.saveWithoutId(tag);
        TermiteWorkerEntity copy = SavannaEntities.TERMITE_WORKER.get().create(level);
        copy.load(tag);
        return copy;
    }
}
