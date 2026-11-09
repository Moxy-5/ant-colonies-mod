package com.antcolonies.savanna.gametest;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.gametest.TestWatch;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Matabele ants in the game (M8 step 1, 2026-10-23): a major and a minor (and, since 2026-10-24, the queen, the
 * biggest body) each walk to a 3-block stone pillar, up it, onto its top and back down to the ground on its far side,
 * the course their climbing was drawn on in the pose viewer ({@code tools/matabele_climb.py}). Every walk is watched
 * for floating, hovering and leaning ({@link TestWatch}). Since 1c each worker has its own size: the longest major and the
 * shortest minor of the real spread climb.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class MatabeleTests {
    @GameTest(template = "arena", timeoutTicks = 1600, skyAccess = true)
    public static void matabeleMajorsAndMinorsClimbAPillarAndComeDown(GameTestHelper helper) {
        for (int x = 0; x < 13; x++) {
            for (int z = 0; z < 16; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        // M8 step 1c: each worker has its own size; the two ends of the real spread climb, the longest major and the shortest minor.
        com.antcolonies.savanna.entity.MatabeleEntity major = climber(helper, SavannaEntities.MATABELE_MAJOR.get(), 3);
        com.antcolonies.savanna.entity.MatabeleEntity minor = climber(helper, SavannaEntities.MATABELE_MINOR.get(), 8);
        var sizes = com.antcolonies.species.SpeciesProfiles.get(com.antcolonies.savanna.entity.MatabeleEntity.SPECIES)
                .section(com.antcolonies.savanna.species.SavannaNumbers.WORKER_SIZES).orElseThrow();
        major.setLengthMm(sizes.longest());
        minor.setLengthMm(sizes.shortest());
        AbstractAntEntity queen = climber(helper, SavannaEntities.MATABELE_QUEEN.get(), 13);
        int[] phase = {0, 0, 0};
        AbstractAntEntity[] ants = {major, minor, queen};
        int[] lanes = {3, 8, 13};
        String[] names = {"the major", "the minor", "the queen"};
        helper.onEachTick(() -> {
            for (int i = 0; i < ants.length; i++) {
                AbstractAntEntity ant = ants[i];
                BlockPos top = helper.absolutePos(new BlockPos(6, 4, lanes[i]));
                BlockPos beyond = helper.absolutePos(new BlockPos(10, 1, lanes[i]));
                String name = names[i];
                if (phase[i] == 0 && helper.getTick() >= 10) {
                    helper.assertTrue(ant.getNavigator().moveTo(top, 1.0), "no route up the pillar for " + name);
                    phase[i] = 1;
                } else if (phase[i] == 1 && ant.getNavigator().isDone()) {
                    helper.assertTrue(!ant.getNavigator().hasFailed() && ant.blockPosition().equals(top),
                            name + " did not get onto the pillar's top (at " + helper.relativePos(ant.blockPosition()) + ")");
                    helper.assertTrue(ant.getNavigator().moveTo(beyond, 1.0), "no route down the pillar for " + name);
                    phase[i] = 2;
                } else if (phase[i] == 2 && ant.getNavigator().isDone()) {
                    helper.assertTrue(!ant.getNavigator().hasFailed() && ant.blockPosition().equals(beyond),
                            name + " did not get back down (at " + helper.relativePos(ant.blockPosition()) + ")");
                    phase[i] = 3;
                }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(phase[0] == 3 && phase[1] == 3 && phase[2] == 3,
                "still climbing (the major at phase " + phase[0] + ", the minor at " + phase[1] + ", the queen at "
                        + phase[2] + ")"));
    }

    /**
     * BUGS 194 (2026-11-08): the user's raid at 19:43 piled up at a diagonal staircase of one-block grass steps
     * ({@code run/reports/2026-11-08_19-43-36-player}, round 2423, 69, -1378), raiders sliding off a step's edge into the
     * low corner beside it and pushing there. The same ground, copied block for block as heights (report y 66 is the
     * arena's floor), four times: a lone longest major, a lone shortest minor and a lone termite worker each walk the
     * moves the raiders were making (from the low corner up onto the diagonal step, along the step's edge, down the
     * trail's slope and back up), and eight majors walk the trail down and back as a crowd. Nobody may stand still 5
     * seconds on the way, and every walk must end where it was sent.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void insectsWalkTheRaidsStaircaseOfGrassStepsWithoutSlidingOff(GameTestHelper helper) {
        int[][] copies = {{0, 0}, {20, 0}, {40, 0}, {0, 20}};
        for (int[] copy : copies) {
            staircase(helper, copy[0], copy[1]);
        }
        var sizes = com.antcolonies.species.SpeciesProfiles.get(com.antcolonies.savanna.entity.MatabeleEntity.SPECIES)
                .section(com.antcolonies.savanna.species.SavannaNumbers.WORKER_SIZES).orElseThrow();
        // The moves, in the copy's own blocks: the low corner, the step's diagonal top, the top cell beside it, the trail's low end, its high end.
        BlockPos low = new BlockPos(9, 3, 6), diagonal = new BlockPos(8, 4, 8), top = new BlockPos(9, 4, 7);
        BlockPos trailLow = new BlockPos(4, 2, 6), trailHigh = new BlockPos(13, 5, 10);
        BlockPos[] legs = {diagonal, top, diagonal, trailLow, trailHigh};
        java.util.List<Walker> walkers = new java.util.ArrayList<>();
        com.antcolonies.savanna.entity.MatabeleEntity major = TestAnts.lone(helper, SavannaEntities.MATABELE_MAJOR.get(), low.getX(), low.getY(), low.getZ());
        major.setLengthMm(sizes.longest());
        com.antcolonies.savanna.entity.MatabeleEntity minor = TestAnts.lone(helper, SavannaEntities.MATABELE_MINOR.get(), low.getX() + 20, low.getY(), low.getZ());
        minor.setLengthMm(sizes.shortest());
        AbstractAntEntity termite = TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), low.getX() + 40, low.getY(), low.getZ());
        walkers.add(new Walker("the major", major, 0, 0, legs, 10, 0));
        walkers.add(new Walker("the minor", minor, 20, 0, legs, 10, 0));
        walkers.add(new Walker("the termite worker", termite, 40, 0, legs, 10, 0));
        BlockPos[] crowdLegs = {trailLow, trailHigh};
        int n = 0;
        for (int x = 12; x <= 14 && n < 8; x++) {
            for (int z = 29; z <= 31 && n < 8; z++) {
                com.antcolonies.savanna.entity.MatabeleEntity ant = TestAnts.lone(helper, SavannaEntities.MATABELE_MAJOR.get(), x, 5, z);
                walkers.add(new Walker("crowd major " + n, ant, 0, 20, crowdLegs, 10 + 5 * n, 2.0));
                n++;
            }
        }
        for (Walker walker : walkers) {
            TestWatch.watch(helper, walker.ant, false);
        }
        helper.onEachTick(() -> {
            for (Walker walker : walkers) {
                walker.tick(helper);
            }
        });
        helper.succeedWhen(() -> {
            for (Walker walker : walkers) {
                helper.assertTrue(walker.leg >= walker.legs.length, walker.name + " still walking (leg " + walker.leg + ")");
            }
        });
    }

    /** One insect walking its moves in turn, watched for standing still; {@code near} 0 = must end on the very block. */
    private static final class Walker {
        final String name;
        final AbstractAntEntity ant;
        final int ox, oz;
        final BlockPos[] legs;
        final int startTick;
        final double near;
        int leg = -1;
        net.minecraft.world.phys.Vec3 stillFrom;
        int stillSince;

        Walker(String name, AbstractAntEntity ant, int ox, int oz, BlockPos[] legs, int startTick, double near) {
            this.name = name;
            this.ant = ant;
            this.ox = ox;
            this.oz = oz;
            this.legs = legs;
            this.startTick = startTick;
            this.near = near;
        }

        BlockPos goal(GameTestHelper helper, int i) {
            return helper.absolutePos(legs[i].offset(ox, 0, oz));
        }

        void tick(GameTestHelper helper) {
            if (leg >= legs.length || helper.getTick() < startTick) return;
            if (leg >= 0) {
                if (!ant.getNavigator().isDone()) {
                    net.minecraft.world.phys.Vec3 here = ant.position();
                    if (stillFrom == null || here.distanceTo(stillFrom) > 0.1) {
                        stillFrom = here;
                        stillSince = (int) helper.getTick();
                    } else if (helper.getTick() - stillSince > 100) {
                        helper.fail(name + " stood still 5 s at " + helper.relativePos(ant.blockPosition()) + " ("
                                + String.format(java.util.Locale.ROOT, "%.2f, %.2f, %.2f", here.x, here.y, here.z)
                                + ") on its way to " + legs[leg].offset(ox, 0, oz));
                    }
                    return;
                }
                BlockPos goal = goal(helper, leg);
                boolean there = near == 0 ? ant.blockPosition().equals(goal)
                        : ant.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(goal)) <= near;
                helper.assertTrue(!ant.getNavigator().hasFailed() && there, name + " did not get to " + legs[leg].offset(ox, 0, oz)
                        + " (at " + helper.relativePos(ant.blockPosition()) + ")");
            }
            leg++;
            stillFrom = null;
            if (leg < legs.length) {
                helper.assertTrue(ant.getNavigator().moveTo(goal(helper, leg), 1.0), "no route for " + name + " to " + legs[leg].offset(ox, 0, oz));
            }
        }
    }

    /**
     * The ground round the raid's pile-up (BUGS 194) as heights, report x 2415 to 2431 west to east, z -1384 to -1372 north
     * to south; each digit is the top block over report y 66 (the arena's floor), grass on top, dirt under.
     */
    private static final String[] STAIRCASE = {
            "00000011112222223", "00000011122222333", "00001111222233333", "00011112222333333", "00111122223333333",
            "01111122233333444", "11112222333334444", "11122223333344444", "11122223333444444", "11222233334444444",
            "12222333344444445", "22223333444444445", "22233334444444455"};

    private static void staircase(GameTestHelper helper, int ox, int oz) {
        for (int row = 0; row < STAIRCASE.length; row++) {
            for (int col = 0; col < STAIRCASE[row].length(); col++) {
                int height = STAIRCASE[row].charAt(col) - '0';
                for (int y = 0; y <= height; y++) {
                    helper.setBlock(ox + col + 1, y, oz + row + 1, y == height ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                }
            }
        }
    }

    /** A Matabele of this body in its lane ({@code z}), 4 blocks west of its lane's stone pillar, 3 blocks tall. */
    private static <T extends AbstractAntEntity> T climber(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> body, int z) {
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(6, y, z, Blocks.STONE);
        }
        T ant = TestAnts.lone(helper, body, 2, 1, z);
        TestWatch.watch(helper, ant, false);
        return ant;
    }
}
