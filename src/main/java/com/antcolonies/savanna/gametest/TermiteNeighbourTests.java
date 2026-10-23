package com.antcolonies.gametest;

import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.Galleries;
import com.antcolonies.colony.Neighbours;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.SavannaParts;
import com.antcolonies.registry.SavannaEntities;
import com.antcolonies.species.Species;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * Brain step 4g, step 5: neighbours ({@code colony.Neighbours}; the user's answers of 2026-10-18). Category {@code colony}.
 * The termites' scene (the split, step S4; the red harvester ant's is in {@link HarvesterNeighbourTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteNeighbourTests {
    /**
     * Two Macrotermes colonies 44 blocks apart, their galleries growing toward each other (dug at once here): the west one
     * old (10 game days), the east one just founded. Where their galleries come within 3 blocks both colonies count a
     * meeting; the old one yields there at once (its tip stops, no piece is dug that way), the young one only after
     * several meetings at one place, and then walls its piece off (packed with soil). The two webs never join: no cell of
     * one comes within a block of the other's.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true, batch = "neighbour_termites")
    public static void neighbouringTermitesWallOffTheirGalleries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        int west = 10;
        int east = 54;
        int row = 32;
        for (int x = 0; x <= 64; x++) {
            for (int z = 0; z <= 64; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), west, SURFACE, row);
        helper.spawn(SavannaEntities.TERMITE_QUEEN.get(), east, SURFACE, row);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 8; i++) {
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), west - 2 + i % 4, SURFACE, row - 3 - i / 4);
                helper.spawn(SavannaEntities.TERMITE_WORKER.get(), east - 2 + i % 4, SURFACE, row - 3 - i / 4);
            }
        });
        TestClock.still(helper, 12); // midday: the termites stay in, and dig
        Colony[] both = new Colony[2];
        boolean[] started = {false};
        helper.runAfterDelay(10, () -> {
            List<Colony> found = colonies(helper, Species.MACROTERMES);
            helper.assertTrue(found.size() == 2 && found.stream().noneMatch(Colony::isFounding), "there should be two termite colonies with workers: " + found.size());
            both[0] = found.get(0);
            both[1] = found.get(1);
            long now = level.getGameTime();
            for (Colony colony : both) {
                TermiteMoundTests.colony(helper); // (keeps the food map and the alarm in the arena; the first colony only)
                colony.foodMap().setTestArea(helper.getBounds().expandTowards(0, 16, 0));
                colony.part(SavannaParts.ALARM).orElseThrow().setTestArea(helper.getBounds());
                for (com.antcolonies.entity.TermiteWorkerEntity worker : TestIsolation.insectsOf(level, colony, com.antcolonies.entity.TermiteWorkerEntity.class)) {
                    worker.setGrownUpAt(now - colony.profile().worker().nurseUntilTicks() - 1000);
                }
                TermiteSoldierTests.digNestAtOnce(level, colony);
                colony.population().addNewMembers(Caste.WORKER, now - colony.profile().worker().builderUntilTicks() - 1000, 900);
                Galleries.of(colony).setSwitchForTests(true);
                Galleries.of(colony).setReachForTests(30);
            }
            both[0].setAgeForTests(level, 10);
            both[1].setAgeForTests(level, 0);
            started[0] = true;
        });
        helper.onEachTick(() -> {
            if (!started[0]) {
                return;
            }
            for (Colony colony : both) {
                Galleries galleries = Galleries.of(colony);
                for (NestPlan.ForagingTunnel tunnel : colony.foragingHoles().tunnels(level)) {
                    for (BlockPos cell : galleries.digCells(tunnel)) {
                        if (!com.antcolonies.colony.Nest.isDugOut(level, cell)) {
                            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
            Colony old = both[0];
            Colony young = both[1];
            // The webs never join.
            for (NestPlan.ForagingTunnel tunnel : old.foragingHoles().tunnels(level)) {
                for (BlockPos cell : tunnel.tunnel()) {
                    BlockPos theirs = Galleries.of(young).galleryCellNear(cell, 1.0);
                    helper.assertTrue(theirs == null, "the two colonies' galleries touch at " + helper.relativePos(cell) + " and " + (theirs == null ? null
                            : helper.relativePos(theirs)));
                }
            }
            long tick = helper.getTick();
            if (tick % 500 == 0) {
                AntColoniesMod.LOGGER.info("[neighbour-test] tick {}: old{}{} / young{}{}", tick, Galleries.of(old).describe(level), old.neighbours().describe(level),
                        Galleries.of(young).describe(level), young.neighbours().describe(level));
            }
            boolean oldMet = !old.neighbours().meetings().isEmpty();
            boolean youngMet = !young.neighbours().meetings().isEmpty();
            if (oldMet && youngMet) {
                helper.assertTrue(!old.neighbours().yielding(level).isEmpty(), "the old colony should yield at its first meeting: " + old.neighbours().describe(level));
            }
            int most = young.neighbours().meetings().stream().mapToInt(Neighbours.Meeting::count).max().orElse(0);
            if (Galleries.of(young).walledOff() > 0) {
                helper.assertTrue(most >= 4 || young.neighbours().meetings().size() > 1, "the young colony walled off its gallery after too few meetings: "
                        + young.neighbours().describe(level));
                helper.assertTrue(oldMet, "the old colony never felt the meeting: " + old.neighbours().describe(level));
                helper.succeed();
            }
            helper.assertTrue(tick < 38000, "the young colony never walled off a gallery: old" + Galleries.of(old).describe(level)
                    + old.neighbours().describe(level) + " / young" + Galleries.of(young).describe(level) + young.neighbours().describe(level));
        });
    }
}
