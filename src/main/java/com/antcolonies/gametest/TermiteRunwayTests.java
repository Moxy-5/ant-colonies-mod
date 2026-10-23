package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.List;

import com.antcolonies.colony.Caste;
import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runways of mud sheeting (the user's plan of 2026-10-14; {@link FoodWays.Runway}, {@link com.antcolonies.colony.RunwayRoute}):
 * rich food near one of the colony's holes gets a covered runway of mud from the hole to it, built at night; once it is
 * closed the hole stays open by day and the food is eaten from under the sheeting; once the food is gone the mud
 * crumbles away.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteRunwayTests {
    /** How far out past the first foraging hole the hay lies (nearer than a new hole is dug for). */
    private static final int HAY_OUT = 5;
    /** The ground is a block higher from this far out past the hole on. */
    private static final int STEP_OUT = 3;

    /**
     * A colony (500 workers in the numbers, the brain off) with its nest dug out, both foraging holes dug, four
     * middle-aged workers (builders) and four old ones (foragers). The colony knows of a hay bale 5 blocks past its
     * first hole, up a step one block high (the runway climbs it, so it is 2 blocks high at the foot of the step: the
     * user's runway on a hill of 2026-10-14 stayed open on its sides there), with short grass growing on the way. It plans a runway from the hole to the hay; at night the grass
     * on it is cut and the workers plaster mud over it until it is closed all round. Then by day: the hole under the
     * runway stays open, the other is plugged, and the hay is eaten from under the sheeting. Then the hay is taken away:
     * the runway is given up, its hole plugged by day, and its mud crumbles away. On the test clock (roadmap 3a): real
     * nights and days from 18:30 (the runway built over the nights, the day checks only by day; the bale is kept from
     * running out by night until then, as a bigger stack of hay would last).
     */
    @GameTest(template = "arena", timeoutTicks = 70000, skyAccess = true)
    public static void hayNearAHoleGetsARunwayOfMudAndIsEatenUnderItByDay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        // The ground laid again block by block (as in the new hole tests), so the world knows where its top is.
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 4 + i, SURFACE, MIDDLE - 2);
            }
        });
        int[] phase = {0}; // 0 setting up, 1 waiting for the plan, 2 building it, 3 by day, 4 hay gone, 5 crumbled
        BlockPos[] hay = {null};
        FoodWays.Runway[] runway = {null};
        int[] cutWhenFinished = {0};
        boolean[] seenInside = {false};
        int[] since = {0};
        TestClock clock = TestClock.moving(helper, 18.5);
        boolean[] night = {false}; // (phase 3: a night came between the days)
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long now = level.getGameTime();
            List<TermiteWorkerEntity> workers = level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds());
            for (int i = 0; i < workers.size(); i++) {
                long builder = now - colony.profile().worker().nurseUntilTicks() - 1000;
                long old = now - colony.profile().worker().builderUntilTicks() - 1000;
                workers.get(i).setGrownUpAt(i % 2 == 0 ? builder : old);
            }
            TermiteSoldierTests.digNestAtOnce(level, colony);
            colony.population().addNewMembers(Caste.WORKER, now - 1000, 500);
            // The hole whose way out leads into the arena: the nest is in the arena's middle and its holes face a side
            // picked by the colony, so the hay past a hole facing north or west lay outside the arena, under its barrier
            // wall, unseen (the test failed now and then: 2026-10-14).
            NestPlan.ForagingTunnel first = null;
            for (NestPlan.ForagingTunnel tunnel : colony.nest().getForagingTunnels(level)) {
                if (first == null && helper.getBounds().deflate(1).contains(tunnel.hole().relative(tunnel.side(), HAY_OUT + 3).getCenter())) {
                    first = tunnel;
                }
            }
            helper.assertTrue(first != null, "no foraging hole faces into the arena");
            Direction out = first.side();
            // A step one block up from 3 blocks out on, in a strip round the way to the hay.
            Direction across = out.getClockWise();
            for (int i = STEP_OUT; i <= HAY_OUT + 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    BlockPos ground = first.hole().relative(out, i).relative(across, j);
                    level.setBlock(ground, Blocks.DIRT.defaultBlockState(), 3);
                    level.setBlock(ground.above(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
            hay[0] = first.hole().above().relative(out, HAY_OUT).above();
            // Short grass on the way (it is cut away under the runway).
            for (int i = 2; i < HAY_OUT; i++) {
                level.setBlock(first.hole().above().relative(out, i).above(i >= STEP_OUT ? 1 : 0), Blocks.SHORT_GRASS.defaultBlockState(), 3);
            }
            level.setBlock(hay[0], Blocks.HAY_BLOCK.defaultBlockState(), 3);
            colony.food().rescanPlantsSoon();
            colony.foodMap().knowForTests(hay[0]);
            phase[0] = 1;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || phase[0] == 0) {
                return;
            }
            boolean day = clock.phase(colony) == TestClock.Phase.DAY;
            if (phase[0] == 2 ? !day : phase[0] != 3 || day) {
                since[0]++; // (building the runway: night time only; eating under it: day time only)
            }
            if ((phase[0] == 2 || phase[0] == 3) && !day && colony.food().getPiecesCut(hay[0]) > 24) {
                // A bale holds 48 pieces and 500 workers eat it in one night, out in the open too: until the day checks
                // are done it stands for a bigger stack of hay, which lasts.
                colony.food().setPiecesCutForTests(hay[0], 0);
            }
            if (phase[0] == 3 && !day) {
                night[0] = true; // (by night they eat the hay from the open too: only the days count)
                return;
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[runway-test] tick {} {} phase {}{}{}", helper.getTick(), clock.describe(), phase[0],
                        colony.foragingHoles().describeRunways(level), colony.foragingHoles().describeNewHoles(level));
            }
            switch (phase[0]) {
                case 1 -> {
                    if (!colony.nest().foodWays().getRunways().isEmpty()) {
                        runway[0] = colony.nest().foodWays().getRunways().get(0);
                        helper.assertTrue(runway[0].food().equals(hay[0]), "the runway is for " + runway[0].food() + ", not the hay");
                        helper.assertTrue(colony.nest().foodWays().getNewTunnels().isEmpty(), "a new hole was planned for food near a hole");
                        AntColoniesMod.LOGGER.info("[runway-test] planned: {} blocks from {}", runway[0].cells().size(),
                                runway[0].hole().toShortString());
                        phase[0] = 2;
                        since[0] = 0;
                    } else {
                        helper.assertTrue(since[0] < 600, "no runway was planned to the hay" + colony.foragingHoles().describeRunways(level)
                                + colony.foragingHoles().whyNoRunway(level, hay[0]) + " | hay at " + hay[0].toShortString()
                                + ", in the arena " + helper.getBounds().contains(hay[0].getCenter()));
                    }
                }
                case 2 -> {
                    if (colony.nest().foodWays().isRunwayClosed(level, runway[0]) && day) {
                        for (BlockPos cell : runway[0].cells()) {
                            helper.assertTrue(level.getBlockState(cell).isAir(), "the runway is not clear at " + cell.toShortString());
                            helper.assertTrue(level.getBlockState(cell.above()).getBlock() instanceof MudCrustBlock
                                    || runway[0].cells().contains(cell.above()), "no mud roof over " + cell.toShortString());
                        }
                        AntColoniesMod.LOGGER.info("[runway-test] closed at tick {}", helper.getTick());
                        cutWhenFinished[0] = colony.food().getPiecesCut(hay[0]);
                        phase[0] = 3;
                        since[0] = 0;
                    }
                    helper.assertTrue(since[0] < 16000, "the runway was not closed: " + openSides(level, colony, runway[0]));
                }
                case 3 -> {
                    if (night[0]) {
                        night[0] = false;
                        cutWhenFinished[0] = colony.food().getPiecesCut(hay[0]);
                    }
                    BlockPos hole = runway[0].hole();
                    if (since[0] > 600) {
                        helper.assertTrue(ForagingHoles.isOpen(level, hole), "the hole under the finished runway was plugged by day");
                    }
                    for (BlockPos cell : runway[0].cells()) {
                        seenInside[0] |= !level.getEntitiesOfClass(TermiteWorkerEntity.class, new AABB(cell),
                                worker -> colony.getId().equals(worker.getColonyId())).isEmpty();
                    }
                    int cut = colony.food().getPiecesCut(hay[0]) - cutWhenFinished[0];
                    if (seenInside[0] && cut >= 2 && since[0] > 600) {
                        AntColoniesMod.LOGGER.info("[runway-test] eaten by day under the sheeting: {} pieces", cut);
                        level.setBlock(hay[0], Blocks.AIR.defaultBlockState(), 3);
                        colony.food().rescanPlantsSoon();
                        phase[0] = 4;
                        since[0] = 0;
                    }
                    helper.assertTrue(since[0] < 10000, "the hay was not eaten by day from the runway: seen in it " + seenInside[0]
                            + ", " + cut + " pieces cut, hole open " + ForagingHoles.isOpen(level, hole));
                }
                case 4 -> {
                    BlockPos hole = runway[0].hole();
                    boolean gone = colony.nest().foodWays().runwayAt(hole) == null;
                    if (gone && day && !ForagingHoles.isOpen(level, hole) && mudPlates(level, runway[0]) == 0) {
                        AntColoniesMod.LOGGER.info("[runway-test] crumbled away at tick {}", helper.getTick());
                        helper.succeed();
                    }
                    helper.assertTrue(since[0] < 30000, "the runway did not crumble away: given up " + gone + ", hole open "
                            + ForagingHoles.isOpen(level, hole) + ", " + openSides(level, colony, runway[0]));
                }
                default -> {
                }
            }
        });
    }

    /** For messages: how many sides of the runway are still open to the air and how many mud plates it has. */
    private static String openSides(ServerLevel level, Colony colony, FoodWays.Runway runway) {
        int open = 0;
        for (BlockPos cell : runway.cells()) {
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (!runway.cells().contains(next) && !next.equals(runway.hole()) && !next.equals(runway.food())
                        && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                    open++;
                }
            }
        }
        return "open sides " + open + ", mud plates " + mudPlates(level, runway);
    }

    /** How many mud plates face into the runway. */
    private static int mudPlates(ServerLevel level, FoodWays.Runway runway) {
        int mud = 0;
        for (BlockPos cell : runway.cells()) {
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (!runway.cells().contains(next) && MudCrustBlock.hasFace(level.getBlockState(next), side.getOpposite())) {
                    mud++;
                }
            }
        }
        return mud;
    }
}
