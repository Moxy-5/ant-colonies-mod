package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.colony.Colony;
import com.antcolonies.gametest.TestAnts;
import com.antcolonies.savanna.colony.NewLandMounds;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.command.MatabeleCommands;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;

/**
 * The scene kit's Matabele scenes (test revamp R3, {@code docs/plans/test-revamp.md}): a Matabele colony settled in a
 * dead termite mound with its prey pile, and a termite feeding spot under a log with termites at it, as the raid,
 * colony, food and nest tests built them by hand. Every check the hand-built scenes made is made here.
 */
public final class MatabeleScenes {
    private MatabeleScenes() {
    }

    /** A month-old dead termite mound of new land at (x, z) on the arena's surface (checked put in). */
    static Colony deadMound(GameTestHelper helper, int x, int z) {
        return deadMound(helper, x, z, 30);
    }

    /** A dead termite mound of new land, dead so many days, at (x, z) on the arena's surface (checked put in). */
    static Colony deadMound(GameTestHelper helper, int x, int z, double days) {
        ServerLevel level = helper.getLevel();
        Colony dead = NewLandMounds.get(level).put(level, helper.absolutePos(new BlockPos(x, SURFACE, z)), NewLandMounds.pickFor("dead", days, 5L));
        helper.assertTrue(dead != null, "no dead mound put in");
        return dead;
    }

    /**
     * A Matabele colony of so many workers made by the command's code at (x, z) on the arena's surface: it settles in the
     * dead mound nearby, with its queen in her room and a day's prey on the pile. Checked: made, with its raids part and
     * its prey pile.
     */
    static Colony colony(GameTestHelper helper, int x, int z, int workers) {
        Colony colony = MatabeleCommands.makeColony(helper.getLevel(), helper.absolutePos(new BlockPos(x, SURFACE, z)), workers);
        helper.assertTrue(colony != null, "no Matabele colony made");
        helper.assertTrue(colony.part(SavannaParts.RAIDS).isPresent(), "a Matabele colony has no raids part");
        helper.assertTrue(colony.part(SavannaParts.PREY_PILE).isPresent(), "a Matabele colony has no prey pile");
        return colony;
    }

    /** Two termite workers feeding in a gallery under a 4-block log (see {@link #feedingUnderALog(GameTestHelper, int, int, int)}). */
    static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z) {
        return feedingUnderALog(helper, x, z, 2);
    }

    /**
     * One or two termite workers feeding in a gallery under a 4-block log (along x from {@code x}) at row {@code z}. The
     * two blocks over them are mud casts, as the termites leave a log they have been eating from below (raid flow,
     * 2026-11-06: majors break only the termites' mud, never the ground; a gallery under bare wood and dirt has no way in).
     * (Moved here from {@code MatabeleRaidTests}, unchanged.)
     */
    static List<TermiteWorkerEntity> feedingUnderALog(GameTestHelper helper, int x, int z, int count) {
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            helper.setBlock(new BlockPos(x + i, SURFACE, z), i == 1 || i == 2 ? SavannaBlocks.MUD_CAST.get().defaultBlockState()
                    : Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            helper.setBlock(x + i, SURFACE - 1, z, Blocks.AIR);
        }
        termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 1, SURFACE - 1, z));
        if (count > 1) {
            termites.add(TestAnts.lone(helper, SavannaEntities.TERMITE_WORKER.get(), x + 2, SURFACE - 1, z));
        }
        return termites;
    }
}
