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
 * for floating, hovering and leaning ({@link TestWatch}).
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
        AbstractAntEntity major = climber(helper, SavannaEntities.MATABELE_MAJOR.get(), 3);
        AbstractAntEntity minor = climber(helper, SavannaEntities.MATABELE_MINOR.get(), 8);
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
