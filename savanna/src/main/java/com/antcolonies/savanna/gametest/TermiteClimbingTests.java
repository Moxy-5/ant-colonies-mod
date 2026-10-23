package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestWatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.MoundSoil;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteRampBlock;
import com.antcolonies.savanna.block.TermiteSpireBlock;
import com.antcolonies.savanna.colony.Mound;
import com.antcolonies.savanna.colony.MoundPlan;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.nav.ClimbShape;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.LevelCellMap;
import com.antcolonies.nav.Route;
import com.antcolonies.nav.StepKind;
import com.antcolonies.savanna.species.SavannaNumbers.MoundNumbers;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Termites on the outside of a cathedral mound: they walk up and down its ramps like slopes (no climbing), climb its
 * spire pieces hugging the narrower column (never floating where a full block's face would be) and go round them, and
 * roam all over a whole mound put up at once without ever getting stuck. Every walk is watched for floating, hovering
 * and leaning ({@link TestWatch#watch}).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TermiteClimbingTests {
    private static BlockState soil() {
        return SavannaBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED)
                .setValue(TermiteMoundBlock.AGE, 2);
    }

    /** A dirt floor at y = 0, {@code sizeX} by {@code sizeZ}. */
    private static void floor(GameTestHelper helper, int sizeX, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
    }

    /**
     * Three steps of mound soil going east, each covered by a row of ramps rising toward it: a termite walks from the
     * ground up to the top and back down without climbing once (the ramps are slopes), and every step of both routes is
     * a walk.
     */
    @GameTest(template = "arena", timeoutTicks = 1500, skyAccess = true)
    public static void termitesWalkUpAndDownRampsWithoutClimbing(GameTestHelper helper) {
        floor(helper, 13, 7);
        BlockState ramp = SavannaBlocks.TERMITE_RAMP.get().defaultBlockState().setValue(TermiteRampBlock.FACING, Direction.EAST)
                .setValue(MoundSoil.AGE, 2);
        for (int z = 1; z <= 5; z++) {
            for (int x = 5; x <= 10; x++) {
                for (int y = 1; y <= Math.min(3, x - 4); y++) {
                    helper.setBlock(x, y, z, soil());
                }
            }
            for (int step = 0; step < 3; step++) {
                helper.setBlock(4 + step, 1 + step, z, ramp);
            }
        }
        BlockPos start = new BlockPos(1, 1, 3);
        BlockPos top = new BlockPos(9, 4, 3);
        TermiteWorkerEntity termite = SavannaTests.loneTermite(helper, start.getX(), start.getY(), start.getZ());
        TestWatch.watch(helper, termite, false);
        int[] phase = {0};
        boolean[] climbed = {false};
        helper.onEachTick(() -> {
            climbed[0] |= termite.getNavigator().isClimbing();
            if (phase[0] == 0 && helper.getTick() >= 10) {
                helper.assertTrue(termite.getNavigator().moveTo(helper.absolutePos(top), 1.0), "no route up the ramps");
                assertOnlyWalks(helper, termite.getNavigator().getPlannedRoute(), "up");
                phase[0] = 1;
            } else if (phase[0] == 1 && termite.getNavigator().isDone()) {
                helper.assertTrue(!termite.getNavigator().hasFailed() && termite.blockPosition().equals(helper.absolutePos(top)),
                        "the termite did not get up the ramps");
                helper.assertTrue(termite.getNavigator().moveTo(helper.absolutePos(start), 1.0), "no route down the ramps");
                assertOnlyWalks(helper, termite.getNavigator().getPlannedRoute(), "down");
                phase[0] = 2;
            } else if (phase[0] == 2 && termite.getNavigator().isDone()) {
                helper.assertTrue(!termite.getNavigator().hasFailed() && termite.blockPosition().equals(helper.absolutePos(start)),
                        "the termite did not get down the ramps");
                phase[0] = 3;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 3, "still walking (phase " + phase[0] + ")");
            helper.assertTrue(!climbed[0], "the termite climbed instead of walking on the ramps");
        });
    }

    private static void assertOnlyWalks(GameTestHelper helper, Route route, String way) {
        for (Route.Step step : route.steps()) {
            helper.assertTrue(step.kind() == StepKind.START || step.kind() == StepKind.WALK,
                    "the way " + way + " the ramps has a " + step.kind() + " step: " + route);
        }
    }

    /**
     * A spire (a block of soil and the five pieces narrowing to the tip): a termite climbs to the thin piece and holds
     * it, pressed against the narrow piece (drawn in past where a full block's face would be, touching it, not inside
     * it), goes on round to another side of the spire, and comes back down to the ground. The needle tip is nothing to
     * hold.
     */
    @GameTest(template = "arena", timeoutTicks = 2400, skyAccess = true)
    public static void termitesClimbASpireHuggingItAndGoRoundIt(GameTestHelper helper) {
        floor(helper, 7, 7);
        BlockPos column = new BlockPos(3, 1, 3);
        helper.setBlock(column, soil());
        for (int t = TermiteSpireBlock.BASE; t <= TermiteSpireBlock.TIP; t++) {
            helper.setBlock(column.above(1 + t), SavannaBlocks.TERMITE_SPIRE.get().defaultBlockState()
                    .setValue(TermiteSpireBlock.THICKNESS, t).setValue(MoundSoil.AGE, 2));
        }
        TermiteWorkerEntity termite = SavannaTests.loneTermite(helper, 1, 1, 3);
        TestWatch.watch(helper, termite, false);
        ServerLevel level = helper.getLevel();
        Foothold onThin = new Foothold(helper.absolutePos(new BlockPos(2, 5, 3)), Direction.EAST);
        Foothold roundTheSide = new Foothold(helper.absolutePos(new BlockPos(3, 4, 4)), Direction.NORTH);
        BlockPos ground = new BlockPos(5, 1, 3);
        double columnWestFace = helper.absolutePos(column).getX();
        double columnSouthFace = helper.absolutePos(column).getZ() + 1.0;
        int[] phase = {0};
        helper.onEachTick(() -> {
            if (phase[0] == 0 && helper.getTick() >= 10) {
                LevelCellMap map = new LevelCellMap(level, termite);
                helper.assertTrue(!new Foothold(helper.absolutePos(new BlockPos(2, 6, 3)), Direction.EAST).isValid(map),
                        "the needle tip can be held");
                helper.assertTrue(termite.getNavigator().moveTo(onThin, 1.0), "no route up the spire");
                phase[0] = 1;
            } else if (phase[0] == 1 && termite.getNavigator().isDone()) {
                helper.assertTrue(!termite.getNavigator().hasFailed(), "the termite did not get up to the thin piece");
                AABB body = termite.getBoundingBox();
                helper.assertTrue(body.maxX > columnWestFace + 0.1, "the termite is not drawn in onto the thin piece (its side is at "
                        + (body.maxX - columnWestFace) + " into the spire's block)");
                helper.assertTrue(level.noCollision(termite, body), "the termite is inside the spire");
                helper.assertTrue(!level.noCollision(termite, body.expandTowards(0.03, 0.0, 0.0)), "the termite does not touch the thin piece");
                helper.assertTrue(termite.getNavigator().moveTo(roundTheSide, 1.0), "no route round the spire");
                phase[0] = 2;
            } else if (phase[0] == 2 && termite.getNavigator().isDone()) {
                helper.assertTrue(!termite.getNavigator().hasFailed(), "the termite did not get round the spire");
                AABB body = termite.getBoundingBox();
                helper.assertTrue(body.minZ < columnSouthFace - 0.1, "the termite is not drawn in onto the middle piece");
                helper.assertTrue(level.noCollision(termite, body), "the termite is inside the spire");
                helper.assertTrue(!level.noCollision(termite, body.expandTowards(0.0, 0.0, -0.03)), "the termite does not touch the middle piece");
                helper.assertTrue(termite.getNavigator().moveTo(helper.absolutePos(ground), 1.0), "no route down the spire");
                phase[0] = 3;
            } else if (phase[0] == 3 && termite.getNavigator().isDone()) {
                helper.assertTrue(!termite.getNavigator().hasFailed() && termite.blockPosition().equals(helper.absolutePos(ground)),
                        "the termite did not get back down");
                phase[0] = 4;
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(phase[0] == 4, "still climbing (phase " + phase[0] + ")"));
    }

    /** Where a termite on the mound can be sent: onto its ramps, onto its spire pieces, onto the top of its soil. */
    private enum Spot {
        RAMP, SPIRE, TOP
    }

    /**
     * A whole cathedral mound (stage 13 of 32, 15 blocks tall) put up at once; 8 termites start on the ground round it and
     * each walks to 4 places picked at random on it (a ramp, the side of a spire piece, the top of its soil) and then
     * back to where it started. Every walk that finds a route must get there (never stuck, never giving up), and the
     * termites must between them reach every kind of place several times.
     */
    @GameTest(template = "arena", timeoutTicks = 14000, skyAccess = true)
    public static void termitesRoamAllOverACathedralMound(GameTestHelper helper) {
        floor(helper, 25, 25);
        ServerLevel level = helper.getLevel();
        MoundPlan.Shape shape = MoundPlan.stage(13, new MoundNumbers(32, 2.0F, 0.5F, 3, 13), 7);
        BlockPos center = new BlockPos(12, 1, 12);
        List<BlockPos> mouths = new ArrayList<>();
        shape.parts().forEach((offset, part) -> {
            BlockPos pos = center.offset(offset);
            if (pos.getY() <= 15) {
                helper.setBlock(pos, Mound.blockFor(shape, offset));
                if (part == MoundPlan.Part.MOUTH) {
                    mouths.add(pos);
                }
            }
        });
        for (BlockPos mouth : mouths) {
            BlockPos abs = helper.absolutePos(mouth);
            level.setBlock(abs, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs), 2);
        }
        int[][] starts = {{1, 1}, {12, 1}, {23, 1}, {1, 12}, {23, 12}, {1, 23}, {12, 23}, {23, 23}};
        List<TermiteWorkerEntity> termites = new ArrayList<>();
        for (int[] s : starts) {
            TermiteWorkerEntity termite = SavannaTests.loneTermite(helper, s[0], 1, s[1]);
            TestWatch.watch(helper, termite, false);
            termites.add(termite);
        }
        // Every place on the mound a termite could be sent, by kind.
        List<List<Foothold>> spots = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        LevelCellMap map = new LevelCellMap(level, termites.get(0));
        for (BlockPos rel : BlockPos.betweenClosed(0, 1, 0, 24, 15, 24)) {
            BlockPos pos = helper.absolutePos(rel);
            if (map.isSlope(pos)) {
                if (Foothold.floor(pos).isValid(map)) {
                    spots.get(Spot.RAMP.ordinal()).add(Foothold.floor(pos));
                }
                continue;
            }
            if (!map.isOpen(pos)) {
                continue;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Foothold wall = new Foothold(pos, side);
                if (level.getBlockState(pos.relative(side)).getBlock() instanceof ClimbShape && wall.isValid(map)) {
                    spots.get(Spot.SPIRE.ordinal()).add(wall);
                }
            }
            if (level.getBlockState(pos.below()).getBlock() instanceof TermiteMoundBlock && Foothold.floor(pos).isValid(map)) {
                spots.get(Spot.TOP.ordinal()).add(Foothold.floor(pos));
            }
        }
        for (Spot spot : Spot.values()) {
            helper.assertTrue(!spots.get(spot.ordinal()).isEmpty(), "the mound has no " + spot + " places");
        }
        Random random = new Random(1234);
        int walks = 4;
        int[] done = new int[termites.size()];
        Spot[] going = new Spot[termites.size()];
        Foothold[] target = new Foothold[termites.size()];
        int[] reached = new int[Spot.values().length];
        int[] unreachable = {0};
        helper.onEachTick(() -> {
            if (helper.getTick() < 20) {
                return;
            }
            for (int k = 0; k < termites.size(); k++) {
                TermiteWorkerEntity termite = termites.get(k);
                if (done[k] > walks) {
                    continue;
                }
                if (target[k] != null) {
                    if (!termite.getNavigator().isDone()) {
                        continue;
                    }
                    helper.assertTrue(!termite.getNavigator().hasFailed(), "termite " + k + " got stuck on its way to " + target[k]
                            + " (a " + going[k] + "), at " + termite.position() + "; see the debug log for why");
                    if (going[k] != null) {
                        reached[going[k].ordinal()]++;
                    }
                    target[k] = null;
                    done[k]++;
                    continue;
                }
                if (done[k] == walks) {
                    // Back to where it started.
                    BlockPos home = helper.absolutePos(new BlockPos(starts[k][0], 1, starts[k][1]));
                    helper.assertTrue(termite.getNavigator().moveTo(home, 1.0), "termite " + k + " finds no way back to the ground from "
                            + termite.position());
                    target[k] = Foothold.floor(home);
                    going[k] = null;
                    continue;
                }
                for (int attempt = 0; attempt < 10 && target[k] == null; attempt++) {
                    Spot spot = Spot.values()[random.nextInt(Spot.values().length)];
                    List<Foothold> list = spots.get(spot.ordinal());
                    Foothold pick = list.get(random.nextInt(list.size()));
                    if (termite.getNavigator().moveTo(pick, 1.0)) {
                        target[k] = pick;
                        going[k] = spot;
                    } else {
                        unreachable[0]++;
                    }
                }
            }
        });
        helper.succeedWhen(() -> {
            for (int k = 0; k < termites.size(); k++) {
                helper.assertTrue(done[k] > walks, "termite " + k + " has done " + done[k] + " of its walks");
            }
            for (Spot spot : Spot.values()) {
                helper.assertTrue(reached[spot.ordinal()] >= 3, "the termites reached a " + spot + " place only " + reached[spot.ordinal()]
                        + " times");
            }
            SavannaMod.LOGGER.info("[climb-test] reached ramps {}, spire pieces {}, tops {}; {} picked places had no route",
                    reached[0], reached[1], reached[2], unreachable[0]);
        });
    }
}
