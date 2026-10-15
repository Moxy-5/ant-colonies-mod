package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Brood;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.Population;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The brain, part 1 ({@link Population}): most of the colony is numbers inside the nest, a few are real mobs. Game tests
 * have no players, so each test says where pretend players' eyes are ({@link Population#setViewersForTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class PopulationTests {
    /** An ant colony in this test area with its whole nest dug out at once and the brain on; null if there is none yet. */
    private static Colony antColony(GameTestHelper helper) {
        return colony(helper, Species.RED_HARVESTER_ANT);
    }

    private static Colony colony(GameTestHelper helper, net.minecraft.resources.ResourceLocation species) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .findFirst().orElse(null);
    }

    /** Every block of the shaft and of every room of the plan dug out at once. */
    private static void digNestAtOnce(ServerLevel level, Colony colony) {
        colony.nest().unlockAllLevelsForTests();
        BlockPos bottom = colony.nest().getShaftBottom(level);
        for (BlockPos cell = colony.getCenter().below(); bottom != null && cell.getY() >= bottom.getY(); cell = cell.below()) {
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        }
        List<NestPlan.Chamber> rooms = new ArrayList<>(colony.nest().getPlannedChambers(level));
        rooms.addAll(colony.nest().getSideRooms(level));
        rooms.addAll(colony.nest().getGardens(level));
        if (colony.nest().getFoundingChamber(level) != null) {
            rooms.add(colony.nest().getFoundingChamber(level));
        }
        for (NestPlan.Chamber room : rooms) {
            for (BlockPos cell : room.cells()) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** This colony's real workers and soldiers that may go into the numbers, in the test area. */
    private static List<AbstractAntEntity> real(GameTestHelper helper, Colony colony) {
        return helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(16),
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && mob.nestCaste() != null);
    }

    /** An ant queen on deep earth; after 10 ticks her colony has its nest dug, no hunger and the brain on, with nobody watching. */
    private static void startAntColony(GameTestHelper helper) {
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(10, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "no ant colony");
            colony.setHungerOn(false);
            digNestAtOnce(helper.getLevel(), colony);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of());
        });
    }

    /**
     * With a small crew (10, set for the test) and nobody near, 10 real workers come out of the 40 in the numbers, two a
     * second, and the headcount stays 40. A player near (eyes 20 blocks off, above ground): all 40 come out (fewer than
     * the cap of 50). The player gone: they go back in down to the crew again. Then the species' own crew (the whole cap,
     * the user's decision: a loaded colony works as before the brain): all 40 come out with nobody near. Nothing is lost.
     */
    @GameTest(template = "arena", timeoutTicks = 2900)
    public static void aCrewWorksWhileNobodyIsNearAndMoreComeOutWhenAPlayerIs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            antColony(helper).population().setCrewForTests(10);
            antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 40);
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 10, "with nobody near the crew should be out: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 30,
                    "40 workers, 30 inside expected: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
        });
        helper.runAfterDelay(1000, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] a player near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 0,
                    "with a player near all 40 should be out: " + real + " real, " + colony.population().describe(level));
            colony.population().setViewersForTests(List.of());
        });
        helper.runAfterDelay(2300, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the player gone: {}", colony.population().describe(level));
            helper.assertTrue(real == 10, "the player gone, back to the crew expected: " + real + " real, "
                    + colony.population().describe(level));
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 40
                    && colony.population().inside(WorkerAntEntity.CASTE) + real == 40, "nobody should be lost: " + colony.getCount(WorkerAntEntity.CASTE)
                    + " counted, " + colony.population().inside(WorkerAntEntity.CASTE) + " inside, " + real + " real");
            colony.population().setCrewForTests(null);
        });
        helper.runAfterDelay(2800, () -> {
            Colony colony = antColony(helper);
            int real = real(helper, colony).size();
            AntColoniesMod.LOGGER.info("[population-test] the species' crew, nobody near: {}", colony.population().describe(level));
            helper.assertTrue(real == 40 && colony.population().inside(WorkerAntEntity.CASTE) == 0 && colony.getCount(WorkerAntEntity.CASTE) == 40,
                    "with the species' crew all 40 should be out with nobody near: " + real + " real, " + colony.population().describe(level));
            helper.succeed();
        });
    }

    /**
     * Insects come out only where no player can see: a player's eyes in the middle of the nest's first room and one up
     * on the surface. Every new real worker must appear out of both players' sight.
     */
    @GameTest(template = "arena", timeoutTicks = 800)
    public static void insectsComeOutOnlyWhereNoPlayerCanSee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        Set<UUID> seen = new HashSet<>();
        List<Vec3> eyes = new ArrayList<>();
        int[] cameOut = {0};
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            NestPlan.Chamber first = colony.nest().getPlannedChambers(level).get(0);
            eyes.add(Vec3.atCenterOf(first.center()));
            eyes.add(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 3, SURFACE + 1, MIDDLE + 3))));
            colony.population().setViewersForTests(eyes);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 20);
        });
        helper.onEachTick(() -> {
            Colony colony = antColony(helper);
            if (colony == null || eyes.isEmpty()) {
                return;
            }
            for (AbstractAntEntity mob : real(helper, colony)) {
                if (seen.add(mob.getUUID())) {
                    cameOut[0]++;
                    helper.assertTrue(Population.outOfSight(level, mob.position().add(0, 0.2, 0), eyes),
                            "a worker came out where a player can see it, at " + mob.blockPosition().toShortString());
                }
            }
        });
        helper.runAfterDelay(700, () -> {
            AntColoniesMod.LOGGER.info("[population-test] {} came out unseen", cameOut[0]);
            helper.assertTrue(cameOut[0] >= 10, "only " + cameOut[0] + " came out");
            helper.succeed();
        });
    }

    /**
     * Workers in the numbers grow old and die like real ones: 20 grew up longer ago than any worker lives, 10 not long
     * ago. Within a few seconds the 20 are dead (the headcount drops to 10, bodies in the refuse chamber), the 10 live on.
     * And the numbers with their ages are saved with the colony.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void workersInTheNumbersDieOfOldAgeAndAreSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // only the numbers, no swapping
            long lifespan = colony.profile().worker().lifespanTicks();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - Math.round(lifespan * 1.2), 20);
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - 2400, 10);
            Colony copy = Colony.load(colony.save());
            helper.assertTrue(copy.population().inside(WorkerAntEntity.CASTE) == 30 && copy.getCount(WorkerAntEntity.CASTE) == 30,
                    "after a reload the numbers are " + copy.population().inside(WorkerAntEntity.CASTE));
        });
        helper.runAfterDelay(120, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 10 && colony.population().inside(WorkerAntEntity.CASTE) == 10,
                    "the 20 old workers should have died: " + colony.getCount(WorkerAntEntity.CASTE) + " counted, "
                            + colony.population().inside(WorkerAntEntity.CASTE) + " inside");
            helper.assertTrue(!colony.refuse().getRefuseCorpses(level).isEmpty(), "no bodies in the refuse chamber");
            helper.succeed();
        });
    }

    /**
     * A larva that has eaten its fill grows into a worker in the numbers when the colony has all the real workers it
     * wants (a crew of 10, set for the test, nobody near) and nobody can see it; with the brain off it comes out as a real worker as before.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aYoungOneGrowsUpIntoTheNumbersWhenNoMoreRealOnesAreWanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        helper.runAfterDelay(15, () -> {
            antColony(helper).population().setCrewForTests(10);
            antColony(helper).population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 12);
        });
        helper.runAfterDelay(300, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(real(helper, colony).size() == 10, "the crew is not out: " + colony.population().describe(level));
            BlockPos spot = colony.nest().getNurseryCells(level).get(0);
            AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            int before = real(helper, colony).size();
            helper.assertTrue(colony.brood().hatchLarva(level, larva), "the larva did not grow up");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 13 && colony.population().inside(WorkerAntEntity.CASTE) == 3
                    && real(helper, colony).size() == before, "the new worker should be one of the numbers: " + colony.population().describe(level));
            colony.population().setOnForTests(false);
            AntLarvaEntity second = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            helper.assertTrue(colony.brood().hatchLarva(level, second), "the second larva did not grow up");
            helper.assertTrue(real(helper, colony).size() == before + 1 && colony.getCount(WorkerAntEntity.CASTE) == 14,
                    "with the brain off it should come out real: " + real(helper, colony).size() + " real");
            helper.succeed();
        });
    }

    /**
     * A big termite colony: 200 workers, minors and majors in the numbers, a player near. Real termites come out, never
     * more than the cap of 50, and no more soldiers than they have posts for (plus none outside by day); some of the real
     * ones are termite workers. The headcount stays.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void aBigTermiteColonyShowsNoMoreThanTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        int[] counted = {0};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            digNestAtOnce(level, colony);
            colony.setForagingTimeForTests(false);
            colony.population().setOnForTests(true);
            colony.population().setViewersForTests(List.of(Vec3.atCenterOf(helper.absolutePos(new BlockPos(MIDDLE + 20, SURFACE + 2, MIDDLE)))));
            long now = level.getGameTime();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 100000, 200);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MINOR.caste(), now, 15);
            colony.population().addNewMembers(TermiteSoldierEntity.Rank.MAJOR.caste(), now, 5);
            counted[0] = colony.getMemberCount();
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony != null && counted[0] > 0) {
                int real = real(helper, colony).size();
                helper.assertTrue(real <= colony.profile().colony().realCap(), real + " real termites, more than the cap");
            }
        });
        helper.runAfterDelay(1500, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            List<AbstractAntEntity> real = real(helper, colony);
            AntColoniesMod.LOGGER.info("[population-test] big termite colony: {} | {}", colony.population().describe(level), colony.soldiers().describe(level));
            long workers = real.stream().filter(mob -> mob instanceof TermiteWorkerEntity).count();
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                long soldiers = real.stream().filter(mob -> mob instanceof TermiteSoldierEntity s && s.getRank() == rank).count();
                int posts = colony.soldiers().posts(level, rank).size();
                helper.assertTrue(soldiers <= posts, soldiers + " real " + rank + " soldiers for " + posts + " posts");
            }
            helper.assertTrue(workers >= 20, "only " + workers + " real termite workers came out: " + colony.population().describe(level));
            int inside = colony.population().inside(WorkerAntEntity.CASTE);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == inside + workers, "the headcount " + colony.getCount(WorkerAntEntity.CASTE)
                    + " is not the " + inside + " inside and the " + workers + " real workers");
            helper.succeed();
        });
    }

    // --- the brain, part 2: going away and coming back (AwayLife) ---------------------------------------------------

    /** What a frozen insect looked like on disk: saved before it leaves the world, so it can be loaded again. */
    private static CompoundTag freezeAndUnload(Colony colony, ServerLevel level, Entity entity) {
        colony.away().freeze(level, entity); // what the game does when the insect's chunk goes out of range
        CompoundTag tag = new CompoundTag();
        entity.save(tag);
        entity.discard();
        return tag;
    }

    /** The insect's chunk is loaded again: the same insect (same id) is back in the world. */
    private static void loadAgain(ServerLevel level, CompoundTag tag) {
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);
        if (entity != null) {
            level.addFreshEntity(entity);
        }
    }

    /**
     * Step 2a. The nest goes out of range with 3 real workers (one carrying a seed), an egg and 2 larvae (one fed 3
     * pieces), and 4 old workers in the numbers about to die; the queen just fed. After the grace minute
     * (shortened for the test) the colony is away: the 3 workers are numbers (headcount still 3), the young are numbers,
     * the old ones have died (4 bodies owed), the seed is owed. Back in range, the frozen insects load again and leave
     * the world on their first tick; the queen is alive (the brain looked after her while away, step 2b); the egg and
     * the larvae are real again in the nursery (the fed one still fed 3), the 4 bodies lie in the refuse chamber and the
     * seed is in the store. Then two stragglers frozen at the edge while the colony is here: one back after a moment
     * carries on, one back after more than the grace has walked home (it is one of the numbers).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aColonyAwayFoldsItsFrozenInsectsIntoTheNumbersAndPutsThingsBackOnReturn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        List<UUID> workers = new ArrayList<>();
        List<UUID> stragglers = new ArrayList<>();
        Map<UUID, CompoundTag> unloaded = new HashMap<>();
        UUID[] queen = new UUID[1];
        int[] foodBefore = new int[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay where the test puts them
            colony.setHungerOn(true);
            for (int i = 0; i < 3; i++) {
                WorkerAntEntity worker = helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 2 + i, SURFACE, MIDDLE + 2);
                workers.add(worker.getUUID());
            }
            BlockPos eggSpot = colony.brood().pickEggSpot(level, level.getRandom());
            helper.assertTrue(eggSpot != null && colony.brood().placeEgg(level, eggSpot), "no room for the egg");
            for (int i = 0; i < 2; i++) {
                BlockPos spot = colony.brood().pickEggSpot(level, level.getRandom());
                helper.assertTrue(spot != null, "no room for a larva");
                AntLarvaEntity larva = colony.brood().spawnLarva(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                if (i == 0) {
                    larva.setFed(3);
                }
            }
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3, "3 workers expected, " + colony.getCount(WorkerAntEntity.CASTE));
            AbstractAntEntity theQueen = colony.getQueen(level);
            queen[0] = theQueen.getUUID();
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(100L);
            colony.queenCare().setQueenLastFed(level.getGameTime());
            ((WorkerAntEntity) level.getEntity(workers.get(0))).setCarriedSeed(new ItemStack(Items.WHEAT_SEEDS));
            foodBefore[0] = colony.food().getFood();
            // Out of range: every insect of the colony is frozen and unloaded (the queen too: she is no number).
            List<Entity> all = new ArrayList<>();
            for (UUID id : workers) {
                all.add(level.getEntity(id));
            }
            all.addAll(colony.brood().getEggs(level));
            all.addAll(colony.brood().getLarvae(level));
            all.add(theQueen);
            for (Entity entity : all) {
                unloaded.put(entity.getUUID(), freezeAndUnload(colony, level, entity));
            }
            colony.away().setAwayForTests(true);
            long lifespan = colony.profile().worker().lifespanTicks();
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime() - lifespan * 12 / 10, 4);
        });
        helper.runAfterDelay(200, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] away: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            for (UUID id : unloaded.keySet()) {
                helper.assertTrue(id.equals(queen[0]) != colony.away().isFoldedIn(id), "every frozen insect but the queen should be folded in: " + id);
            }
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3 && colony.population().inside(WorkerAntEntity.CASTE) == 3,
                    "the 3 frozen workers should be numbers and the 4 old ones dead: " + colony.getCount(WorkerAntEntity.CASTE) + " counted, "
                            + colony.population().inside(WorkerAntEntity.CASTE) + " inside");
            helper.assertTrue(colony.brood().insideCount() == 3, "the egg and 2 larvae should be numbers: " + colony.brood().insideCount());
            helper.assertTrue(colony.away().getBodiesOwed() == 4 && colony.away().getFoodOwed() == 1,
                    "4 bodies and 1 seed owed expected: " + colony.away().describe(level));
            // Back in range: the chunks load again, with everything that was frozen in them.
            colony.away().setAwayForTests(false);
            unloaded.values().forEach(tag -> loadAgain(level, tag));
        });
        helper.runAfterDelay(240, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "the queen should be back and alive");
            colony.queenCare().setQueenLastFed(level.getGameTime()); // (fed: the rest of the test is not about her)
            for (UUID id : unloaded.keySet()) {
                if (!id.equals(queen[0])) {
                    helper.assertTrue(level.getEntity(id) == null, "a folded insect should leave the world when it loads: " + level.getEntity(id));
                }
            }
            AntColoniesMod.LOGGER.info("[away-test] back: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.brood().countEggs(level) == 1 && colony.brood().countLarvae(level) == 2 && colony.brood().insideCount() == 0,
                    "the egg and the 2 larvae should be real again: " + colony.brood().countEggs(level) + " eggs, " + colony.brood().countLarvae(level)
                            + " larvae, " + colony.brood().insideCount() + " inside");
            helper.assertTrue(colony.brood().getLarvae(level).stream().anyMatch(larva -> larva.getFed() == 3), "the fed larva should still be fed 3");
            helper.assertTrue(colony.refuse().getRefuseCorpses(level).size() == 4, "4 bodies expected in the refuse chamber: "
                    + colony.refuse().getRefuseCorpses(level).size());
            helper.assertTrue(colony.food().getFood() == foodBefore[0] + 1, "the carried seed should be in the store: " + colony.food().getFood()
                    + " food, was " + foodBefore[0]);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 3 && colony.population().inside(WorkerAntEntity.CASTE) == 3,
                    "still 3 workers, all numbers: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            // Two stragglers at the edge of the loaded land while the colony is here.
            for (int i = 0; i < 2; i++) {
                stragglers.add(helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE + 6 + i, SURFACE, MIDDLE - 4).getUUID());
            }
        });
        helper.runAfterDelay(260, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 5, "5 workers expected: " + colony.getCount(WorkerAntEntity.CASTE));
            for (UUID id : stragglers) {
                unloaded.put(id, freezeAndUnload(colony, level, level.getEntity(id)));
            }
        });
        helper.runAfterDelay(280, () -> loadAgain(level, unloaded.get(stragglers.get(0)))); // back after a moment
        helper.runAfterDelay(400, () -> loadAgain(level, unloaded.get(stragglers.get(1)))); // back after more than the grace
        helper.runAfterDelay(440, () -> {
            Colony colony = antColony(helper);
            Entity first = level.getEntity(stragglers.get(0));
            helper.assertTrue(first != null && first.isAlive() && !colony.away().isFrozen(stragglers.get(0)),
                    "the straggler back after a moment should carry on");
            helper.assertTrue(level.getEntity(stragglers.get(1)) == null, "the straggler back after the grace should have walked home");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 5 && colony.population().inside(WorkerAntEntity.CASTE) == 4,
                    "5 workers, 4 of them numbers expected: " + colony.getCount(WorkerAntEntity.CASTE) + ", " + colony.population().inside(WorkerAntEntity.CASTE));
            helper.succeed();
        });
    }

    /**
     * Step 2b. A colony away lives on as numbers: 300 young workers (102 of them nurses), 48 seeds in the store, an egg
     * and 2 hungry larvae as numbers, and the queen in her room carrying an egg (all as if frozen). While away the
     * nurses mill seeds into bread and feed the larvae; the egg hatches and is fed; all 3 grow up into workers (303);
     * the queen's egg comes out as a number and the nurses feed her bread. Then, with 10 seeds left in the store, the
     * workers have gone almost their whole starve time without food: the ones the store can feed eat, the rest starve
     * and leave bodies. Back in range the
     * queen is alive, her egg comes out into the nursery, bodies lie in the refuse chamber, and the blocks have caught up
     * with the counts (nothing owed).
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aColonyAwayEatsMillsBreadAndRaisesItsYoungAsNumbers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        startAntColony(helper);
        UUID[] queen = new UUID[1];
        CompoundTag[] queenOnDisk = new CompoundTag[1];
        int[] eggsLaid = new int[1];
        int[] foodLeft = new int[1];
        helper.runAfterDelay(15, () -> {
            Colony colony = antColony(helper);
            colony.population().setOnForTests(false); // no swapping: the numbers stay numbers
            colony.setHungerOn(true);
            BlockPos room = colony.nest().getFinishedRoomCenter(level);
            helper.assertTrue(room != null, "the queen's room should be dug");
            colony.getQueen(level).teleportTo(room.getX() + 0.5, room.getY(), room.getZ() + 0.5);
            colony.queenCare().setQueenLastFed(level.getGameTime());
            colony.population().addNewMembers(WorkerAntEntity.CASTE, level.getGameTime(), 300);
            List<BlockPos> store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 48; i++) {
                helper.assertTrue(colony.food().depositSeed(level, store.get(i % store.size())), "could not put a seed in the store");
            }
        });
        helper.runAfterDelay(40, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony.queenCare().isQueenHome(level), "the queen should be in her room");
            queen[0] = colony.getQueen(level).getUUID();
            eggsLaid[0] = colony.queenCare().getEggsLaid();
            colony.queenCare().setEggDueForTests(level.getGameTime() + 60);
            colony.away().setOnForTests(true);
            colony.away().setGraceForTests(20L);
            queenOnDisk[0] = freezeAndUnload(colony, level, colony.getQueen(level));
            colony.away().setAwayForTests(true);
            colony.brood().addInsideForTests(Brood.Young.egg(100)); // (frozen young: here they would come out at once)
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
            colony.brood().addInsideForTests(Brood.Young.larva(0, 0, null));
        });
        helper.runAfterDelay(500, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            AntColoniesMod.LOGGER.info("[away-test] 2b away: {}{} | food {}, bread {}, queen bread {}", colony.population().describe(level),
                    colony.away().describe(level), colony.food().getFood(), colony.bakery().getBread(), colony.queenCare().getQueenBread());
            helper.assertTrue(colony.away().isAway(level), "the colony should be away");
            helper.assertTrue(colony.getCount(WorkerAntEntity.CASTE) == 303 && colony.population().inside(WorkerAntEntity.CASTE) == 303,
                    "the egg and the 2 larvae should have grown up into workers (303): " + colony.getCount(WorkerAntEntity.CASTE));
            helper.assertTrue(colony.queenCare().getEggsLaid() == eggsLaid[0] + 1, "the queen's egg should have come out while away");
            List<Brood.Young> young = colony.brood().youngInsideForTests();
            helper.assertTrue(young.size() == 1 && young.get(0).isEgg(), "her egg should be a number: " + young);
            helper.assertTrue(colony.queenCare().getQueenBread() > 0 || colony.queenCare().isIncubating(),
                    "the nurses should have fed the queen: " + colony.queenCare().getQueenBread());
            helper.assertTrue(colony.food().getFood() < 48 && colony.ledger().store() < 0,
                    "the nurses should have milled seeds into bread: " + colony.food().getFood() + " food, " + colony.ledger().describe());
            List<BlockPos> store = colony.nest().getFoodStoreCells(level);
            for (int i = 0; i < 10; i++) {
                colony.food().depositSeed(level, store.get(i % store.size())); // (a few seeds left in the store)
            }
            foodLeft[0] = colony.food().getFood();
            // The workers last ate long ago: nearly their whole starve time.
            var worker = colony.profile().worker();
            colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, level.getGameTime() - worker.starveAfterTicks() + 60);
        });
        helper.runAfterDelay(600, () -> {
            Colony colony = ColonyManager.get(level).getColonies().stream().filter(c -> c.getQueenId().equals(queen[0])).findFirst().orElseThrow();
            int workers = colony.getCount(WorkerAntEntity.CASTE);
            AntColoniesMod.LOGGER.info("[away-test] 2b starving: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(workers > 0 && workers <= foodLeft[0] && workers == colony.population().inside(WorkerAntEntity.CASTE),
                    "only the workers the store could feed (" + foodLeft[0] + ") should be alive: " + workers);
            helper.assertTrue(colony.food().getFood() == 0 || workers == 0, "the store should be eaten empty: " + colony.food().getFood());
            helper.assertTrue(colony.ledger().bodies() >= 303 - foodLeft[0], "the starved workers should leave bodies: " + colony.ledger().describe());
            // Back in range: the queen's chunk loads again.
            colony.away().setAwayForTests(false);
            loadAgain(level, queenOnDisk[0]);
        });
        helper.runAfterDelay(700, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "the queen should be back and alive");
            AntColoniesMod.LOGGER.info("[away-test] 2b back: {}{}", colony.population().describe(level), colony.away().describe(level));
            helper.assertTrue(colony.brood().insideCount() == 0 && colony.brood().countEggs(level) == 1,
                    "her egg should be real in the nursery: " + colony.brood().countEggs(level) + " eggs, " + colony.brood().insideCount() + " inside");
            helper.assertTrue(colony.ledger().isEmpty(), "the blocks should have caught up with the counts: " + colony.ledger().describe());
            helper.assertTrue(!colony.refuse().getRefuseCorpses(level).isEmpty(), "the starved workers' bodies should be in the refuse chamber");
            helper.succeed();
        });
    }

    // --- termite workers eating (the step after 2b) -----------------------------------------------------------------

    /**
     * Real Macrotermes worker eating. A young termite worker (nurse age) and an old one, both near starving: while the
     * colony has had no comb their hunger clocks do not run, so neither starves. Then comb with nodules in the garden
     * and both hungry: the young one eats a fungus nodule, the old one a layer of old comb (never the comb's bed). Then
     * 100 worker numbers (40 young, 60 old) whose meal is due: the young share eat nodules, the old ones comb, one piece
     * making 4 meals, so about 10 nodules and 15 layers, and nobody starves.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void termiteWorkersEatNodulesWhenYoungAndOldCombWhenOlder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TermiteMoundTests.deepGround(helper);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        TermiteWorkerEntity[] workers = new TermiteWorkerEntity[2]; // young, old
        int[] phase = {0};
        long[] phaseAt = {0};
        int[] before = new int[2];
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null, "no termite colony");
            colony.setForagingTimeForTests(false);
            colony.setHungerOn(true);
            TermiteSoldierTests.digNestAtOnce(level, colony);
            List<BlockPos> room = colony.nest().getHomeChamber(level).room();
            for (int i = 0; i < 2; i++) {
                TermiteWorkerEntity worker = ModEntities.TERMITE_WORKER.get().create(level);
                BlockPos cell = room.get(room.size() - 1 - i);
                worker.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0.0F, 0.0F);
                level.addFreshEntity(worker);
                workers[i] = worker;
            }
        });
        helper.runAfterDelay(15, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            long now = level.getGameTime();
            workers[0].setGrownUpAt(now);
            workers[1].setGrownUpAt(now - 250000);
            for (TermiteWorkerEntity worker : workers) {
                helper.assertTrue(colony.getId().equals(worker.getColonyId()), "a worker did not join the colony");
                worker.setLastAte(now - colony.profile().worker().starveAfterTicks() + 40); // two seconds from starving
            }
            helper.assertTrue(!colony.food().hasHadFood(), "the new colony should have had no comb yet");
            phase[0] = 1;
            phaseAt[0] = now;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            long now = level.getGameTime();
            var food = com.antcolonies.species.SpeciesProfile.MealFood.values();
            if (phase[0] == 1 && now - phaseAt[0] >= 80) {
                for (TermiteWorkerEntity worker : workers) {
                    helper.assertTrue(worker.isAlive() && !worker.isHungry(), "with no comb yet the workers should not get hungry");
                }
                // The garden gets comb (4 layers a block) with 3 nodules on each.
                for (int i = 0; i < 40; i++) {
                    BlockPos spot = colony.food().pickFoodSpot(level, level.getRandom());
                    if (spot == null) {
                        break;
                    }
                    colony.food().depositSeed(level, spot);
                }
                for (BlockPos comb : colony.garden().getCombs(level)) {
                    level.setBlock(comb, level.getBlockState(comb).setValue(com.antcolonies.block.FungusCombBlock.NODULES, 3), 3);
                }
                helper.assertTrue(colony.food().getFood() >= 24, "only " + colony.food().getFood() + " layers of comb could be put down");
                for (TermiteWorkerEntity worker : workers) {
                    worker.setLastAte(now - colony.profile().worker().mealIntervalTicks() - 5);
                }
                before[0] = colony.workerFood().getPiecesEaten(food[0]);
                before[1] = colony.workerFood().getPiecesEaten(food[1]);
                phase[0] = 2;
                phaseAt[0] = now;
            } else if (phase[0] == 2 && !workers[0].isHungry() && !workers[1].isHungry()) {
                helper.assertTrue(workers[0].isAlive() && workers[1].isAlive(), "a worker died");
                int combEaten = colony.workerFood().getPiecesEaten(food[0]) - before[0];
                int nodulesEaten = colony.workerFood().getPiecesEaten(food[1]) - before[1];
                helper.assertTrue(combEaten == 1 && nodulesEaten == 1, "one nodule and one layer of comb should have been started on: "
                        + nodulesEaten + " nodules, " + combEaten + " comb");
                for (BlockPos comb : colony.garden().getCombs(level)) {
                    helper.assertTrue(level.getBlockState(comb).getValue(com.antcolonies.block.SeedPileBlock.LAYERS) >= 1, "a comb's bed was eaten");
                }
                // The worker numbers: 40 young, 60 old, all due a meal.
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now, 40);
                colony.population().addNewMembers(WorkerAntEntity.CASTE, now - 250000, 60);
                colony.population().setLastAteForTests(level, WorkerAntEntity.CASTE, now - colony.profile().worker().mealIntervalTicks() - 30);
                before[0] = colony.workerFood().getPiecesEaten(food[0]);
                before[1] = colony.workerFood().getPiecesEaten(food[1]);
                phase[0] = 3;
                phaseAt[0] = now;
            } else if (phase[0] == 3 && now - phaseAt[0] >= 60) {
                int comb = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.STORE) - before[0];
                int nodules = colony.workerFood().getPiecesEaten(com.antcolonies.species.SpeciesProfile.MealFood.NURSE_FOOD) - before[1];
                AntColoniesMod.LOGGER.info("[eat-test] 100 worker numbers ate {} layers of comb and {} nodules; {}", comb, nodules,
                        colony.population().describe(level));
                helper.assertTrue(comb >= 13 && comb <= 16, "the 60 old worker numbers should eat about 15 layers of comb, ate " + comb);
                helper.assertTrue(nodules >= 8 && nodules <= 11, "the 40 young worker numbers should eat about 10 nodules, ate " + nodules);
                helper.assertTrue(colony.population().inside(WorkerAntEntity.CASTE) == 100, "a worker number starved: "
                        + colony.population().inside(WorkerAntEntity.CASTE));
                helper.succeed();
            }
        });
    }
}
