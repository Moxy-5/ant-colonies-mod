package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.List;
import java.util.function.IntBinaryOperator;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.colony.ForagingHoles;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.NestSite;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colonies on hills and uneven ground (the user's decisions of 2026-10-14: one shared ground reader, every planner
 * tested on hill arenas). The arenas are shaped here: slopes, terraces, valleys and cliff edges.
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class HillTests {
    /** Shapes the arena's ground: the top ground block of column (x, z) is {@code SURFACE - 1 + rise(x, z)}, grass on top. */
    static void shapeGround(GameTestHelper helper, int size, IntBinaryOperator rise) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int top = SURFACE - 1 + rise.applyAsInt(x, z);
                for (int y = 0; y < top; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, top, z, Blocks.GRASS_BLOCK);
                for (int y = top + 1; y < 16; y++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    /**
     * A termite nest on a hillside: the ground slopes down to the north-west and up to the south-east (a block every 3
     * columns, along both x and z, so whichever way the foraging tunnels run, one runs uphill and the other downhill).
     * The downhill hole of a level tunnel would be in the open air, the uphill one under the soil: both follow the
     * ground and open at the surface.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesOnAHillsideOpenAtTheSurfaceUphillAndDownhill(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> Math.max(-6, Math.min(3, Math.floorDiv(x + z - 2 * MIDDLE, 3))));
    }

    /**
     * A termite nest on a low flat with a terrace 2 blocks high from 7 blocks out (the user's exact bug: the ground 2
     * blocks above a side hole instead of 1). The tunnels climb the terrace in one step and open on top of it.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesUpATerraceOpenOnTopOfIt(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> chebyshev(x, z) >= 7 ? 2 : 0);
    }

    /**
     * A termite nest at the foot of a cliff 5 blocks high from 7 blocks out, too high to tunnel up: the tunnels stop
     * short of it and open their holes at the cliff's foot.
     */
    @GameTest(template = "arena", timeoutTicks = 20000, skyAccess = true)
    public static void termiteSideHolesBelowACliffOpenAtItsFoot(GameTestHelper helper) {
        sideHolesOpenAtTheSurface(helper, (x, z) -> chebyshev(x, z) >= 7 ? 5 : 0);
    }

    /**
     * A harvester ant queen landed on a hillside (the ground rises a block a column toward the east, from 8 blocks
     * lower in the west up to a level shelf 4 blocks higher from 4 blocks east of her): real harvester ant queens look
     * for level open ground, so she walks up onto the shelf, founds her nest on level ground there, digs her founding
     * chamber and seals herself in, with no room or shaft open to the slope.
     */
    @GameTest(template = "arena", timeoutTicks = 30000, skyAccess = true)
    public static void antQueenOnAHillsideWalksToLevelGroundAndFoundsThere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        IntBinaryOperator rise = (x, z) -> Math.max(-8, Math.min(4, x - MIDDLE));
        shapeGround(helper, 25, rise);
        helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE + rise.applyAsInt(MIDDLE, MIDDLE), MIDDLE);
        BlockPos landed = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        boolean[] checked = {false};
        helper.onEachTick(() -> {
            Colony colony = antColony(helper);
            if (colony == null) {
                helper.assertTrue(helper.getTick() < 2000, "she has not founded a colony after 2000 ticks");
                return;
            }
            BlockPos entrance = colony.getCenter();
            if (!checked[0]) {
                checked[0] = true;
                AntColoniesMod.LOGGER.info("[hill-test] the queen landed at {} and founded her colony at {} (tick {})",
                        landed.toShortString(), entrance.toShortString(), helper.getTick());
                helper.assertTrue(NestSite.isLevel(level, entrance.below(), 1), "she founded on uneven ground at " + entrance.toShortString());
                helper.assertTrue(entrance.distManhattan(new BlockPos(landed.getX(), entrance.getY(), landed.getZ())) >= 3,
                        "she founded where she landed, on the slope");
            }
            NestPlan.Chamber founding = colony.nest().getFoundingChamber(level);
            if (founding == null || !level.getBlockState(entrance).is(ModBlocks.BURROWED_DIRT.get())) {
                helper.assertTrue(helper.getTick() < 28000, "she has not dug her founding chamber and sealed herself in");
                return;
            }
            // Sealed in: the shaft and her chamber are dug, and closed all round (soil, or the plug).
            java.util.Set<BlockPos> nest = new java.util.HashSet<>(founding.cells());
            for (int i = 1; i <= NestPlan.FOUNDING_DEPTH; i++) {
                nest.add(entrance.below(i));
            }
            for (BlockPos cell : nest) {
                helper.assertTrue(level.getBlockState(cell).isAir(), "the nest block " + cell.toShortString() + " is not dug");
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                    BlockPos next = cell.relative(side);
                    helper.assertTrue(nest.contains(next) || !level.getBlockState(next).isAir(),
                            "the nest is open to the outside at " + next.toShortString());
                }
            }
            helper.succeed();
        });
    }

    /**
     * A termite colony on a hillside (the ground rises a block every 3 blocks to the east, 3 blocks up and down from the
     * nest): termites build where they land, and the mound fills in round the slope (the user's decision of
     * 2026-10-14). The workers build the first two stages of the mound: on the low side its foot is filled down to the
     * ground, on the high side the hillside itself stands in for the mound's soil, and its hollows (the shaft, the
     * tunnels) are open there too. Wet soil sets at once, as in the flat test.
     */
    @GameTest(template = "arena", timeoutTicks = 40000, skyAccess = true)
    public static void termitesBuildTheirMoundRoundAHillside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        IntBinaryOperator rise = (x, z) -> Math.max(-3, Math.min(3, Math.floorDiv(x - MIDDLE, 3)));
        shapeGround(helper, 25, rise);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            helper.spawn(ModEntities.TERMITE_KING.get(), MIDDLE + 1, SURFACE, MIDDLE + 1);
            for (int i = 0; i < 8; i++) {
                helper.spawn(ModEntities.TERMITE_WORKER.get(), MIDDLE - 2 + i % 4, SURFACE, MIDDLE - 3 + i / 4);
            }
        });
        TestClock.still(helper, 0.0); // midnight: the mound is built at foraging time
        int[] stage = {1};
        int[] stageOneParts = {0};
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null) {
                return;
            }
            if (colony.part(ColonyParts.MOUND).orElseThrow().getStage(level) != stage[0]) {
                colony.part(ColonyParts.MOUND).orElseThrow().setStage(level, stage[0]);
            }
            // Wet soil sets at once, the mound's and the footing under it.
            if (helper.getTick() % 20 == 0) {
                for (BlockPos pos : BlockPos.betweenClosed(colony.getCenter().offset(-8, -4, -8), colony.getCenter().offset(8, 8, 8))) {
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof com.antcolonies.block.TermiteMoundBlock && com.antcolonies.block.TermiteMoundBlock.isFinished(state)
                            && state.getValue(com.antcolonies.block.TermiteMoundBlock.AGE) == 0) {
                        level.setBlockAndUpdate(pos, state.setValue(com.antcolonies.block.TermiteMoundBlock.AGE, 1));
                    }
                }
            }
            String missing = unbuilt(level, colony);
            if (stage[0] == 1 && missing.isEmpty()) {
                stage[0] = 2;
                stageOneParts[0] = colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).size();
                AntColoniesMod.LOGGER.info("[hill-mound] tick {}: stage 1 built", helper.getTick());
            } else if (stage[0] == 2 && missing.isEmpty() && colony.part(ColonyParts.MOUND).orElseThrow().getStage(level) == 2
                    && colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).size() > stageOneParts[0]) {
                helper.assertTrue(level.getBlockState(colony.getCenter()).isAir(), "the entrance is not open");
                AntColoniesMod.LOGGER.info("[hill-mound] tick {}: stage 2 built", helper.getTick());
                helper.succeed();
                return;
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[hill-mound] tick {}: stage {}, not built: {}", helper.getTick(), stage[0], missing);
            }
            helper.assertTrue(helper.getTick() < 38000, "stage " + stage[0] + " of the mound on the hillside is not built: " + missing);
        });
    }

    /**
     * The parts of the mound at its stage now that are not there yet (empty if it is built): a wall is finished mound
     * soil or the natural earth of the hillside, a tunnel is a tunnel block, an open part is air. Pieces are not checked.
     */
    private static String unbuilt(ServerLevel level, Colony colony) {
        StringBuilder missing = new StringBuilder();
        int count = 0;
        for (var entry : colony.part(ColonyParts.MOUND).orElseThrow().getParts(level).entrySet()) {
            BlockPos pos = entry.getKey();
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            boolean ok = switch (entry.getValue()) {
                case WALL -> state.getBlock() instanceof com.antcolonies.block.TermiteMoundBlock && com.antcolonies.block.TermiteMoundBlock.isFinished(state)
                        || state.is(com.antcolonies.registry.ModTags.DIGGABLE);
                case TUNNEL -> state.getBlock() instanceof com.antcolonies.block.TermiteTunnelBlock;
                case OPEN -> state.isAir();
                case MOUTH, SPIRE, RAMP -> true;
            };
            if (!ok) {
                if (count++ < 8) {
                    missing.append(entry.getValue()).append(' ').append(pos.subtract(colony.getCenter()).toShortString())
                            .append(" is ").append(state.getBlock().getName().getString()).append("; ");
                }
            }
        }
        return count == 0 ? "" : count + " parts: " + missing;
    }

    /**
     * A harvester ant hill on ground with a step: the ground is a block higher from 2 blocks east of the entrance (a spot
     * level enough for the queen to found on). The workers dig the nest and pile the soil into the hill round the hole;
     * the hill's top follows its planned round shape from the hole, so on the higher side fewer pellets make it up and
     * no column is left lower or piled higher than planned.
     */
    @GameTest(template = "arena", timeoutTicks = 60000, skyAccess = true)
    public static void antHillOnAStepKeepsItsRoundShape(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        IntBinaryOperator rise = (x, z) -> x >= MIDDLE + 2 ? 1 : 0;
        shapeGround(helper, 25, rise);
        helper.spawn(ModEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(ModEntities.WORKER_ANT.get(), MIDDLE - 2 + i % 3 * 2, SURFACE, MIDDLE - 3 + i / 3 * 6);
            }
        });
        helper.runAfterDelay(10, () -> {
            Colony colony = antColony(helper);
            helper.assertTrue(colony != null, "no ant colony");
            colony.setHungerOn(false);
            colony.nest().unlockAllLevelsForTests();
        });
        helper.onEachTick(() -> {
            Colony colony = antColony(helper);
            if (colony == null || helper.getTick() < 20) {
                return;
            }
            BlockPos entrance = colony.getCenter();
            StringBuilder problems = new StringBuilder();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int planned = com.antcolonies.colony.Hill.moundHeightAt(dx, dz);
                    if (planned == 0) {
                        continue;
                    }
                    int x = entrance.getX() + dx;
                    int z = entrance.getZ() + dz;
                    int top = entrance.getY() + 6;
                    while (top > entrance.getY() - 4 && level.getBlockState(new BlockPos(x, top, z)).getCollisionShape(level, new BlockPos(x, top, z)).isEmpty()) {
                        top--;
                    }
                    int want = entrance.getY() - 1 + planned; // the top block of the hill in this column
                    if (top != want) {
                        problems.append(String.format("(%d,%d) top %d not %d; ", dx, dz, top - entrance.getY(), want - entrance.getY()));
                    }
                }
            }
            BlockPos toDig = colony.nest().findDigTarget(level);
            if (problems.length() == 0 && toDig == null) {
                AntColoniesMod.LOGGER.info("[hill-ant] tick {}: the hill is built at {}", helper.getTick(), entrance.toShortString());
                helper.succeed();
                return;
            }
            if (helper.getTick() % 2000 == 0) {
                AntColoniesMod.LOGGER.info("[hill-ant] tick {}: {}", helper.getTick(), problems);
            }
            helper.assertTrue(helper.getTick() < 58000, problems.length() > 0 ? "the hill is not its planned shape: " + problems
                    : "the hill is built but the nest is not dug yet: next block " + toDig.subtract(entrance).toShortString() + " from the entrance");
        });
    }

    private static Colony antColony(GameTestHelper helper) {
        return com.antcolonies.colony.ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> helper.getBounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(c.getCenter()))
                        && c.getQueen(helper.getLevel()) instanceof com.antcolonies.entity.QueenAntEntity)
                .findFirst().orElse(null);
    }

    /** How far the column (x, z) is from the arena's middle, the larger of the two distances (a square ring). */
    private static int chebyshev(int x, int z) {
        return Math.max(Math.abs(x - MIDDLE), Math.abs(z - MIDDLE));
    }

    /**
     * A termite colony in an arena shaped by {@code rise} (see {@link #shapeGround}): its two foraging holes are planned
     * at the surface, the workers dig both tunnels and open both holes.
     */
    private static void sideHolesOpenAtTheSurface(GameTestHelper helper, IntBinaryOperator rise) {
        ServerLevel level = helper.getLevel();
        level.getServer().tickRateManager().setTickRate(2000.0F);
        shapeGround(helper, 25, rise);
        int middleTop = SURFACE + rise.applyAsInt(MIDDLE, MIDDLE);
        helper.spawn(ModEntities.TERMITE_QUEEN.get(), MIDDLE, middleTop, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                int x = MIDDLE - 3 + i;
                helper.spawn(ModEntities.TERMITE_WORKER.get(), x, SURFACE + rise.applyAsInt(x, MIDDLE - 2), MIDDLE - 2);
            }
        });
        boolean[] ready = {false};
        helper.runAfterDelay(10, () -> {
            Colony colony = TermiteMoundTests.colony(helper);
            helper.assertTrue(colony != null && !colony.isFounding(), "no termite colony with workers");
            long middleAged = level.getGameTime() - colony.profile().worker().nurseUntilTicks() - 1000;
            for (TermiteWorkerEntity worker : level.getEntitiesOfClass(TermiteWorkerEntity.class, helper.getBounds())) {
                worker.setGrownUpAt(middleAged);
            }
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            helper.assertTrue(tunnels.size() == 2, "the nest has " + tunnels.size() + " foraging tunnels");
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                // (Checked on the blocks themselves: the arena may be turned, so the shape's x and z are not the world's.)
                BlockPos hole = tunnel.hole();
                helper.assertTrue(level.getBlockState(hole).is(Blocks.GRASS_BLOCK) && level.getBlockState(hole.above()).isAir(),
                        "the " + tunnel.side() + " hole " + hole.toShortString() + " is not at the surface: " + level.getBlockState(hole)
                                + " with " + level.getBlockState(hole.above()) + " over it");
            }
            // The shaft and rooms dug at once; the foraging tunnels are left for the workers.
            BlockPos bottom = colony.nest().getShaftBottom(level);
            for (BlockPos pos = colony.getCenter().below(); pos.getY() >= bottom.getY(); pos = pos.below()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (NestPlan.Chamber chamber : colony.nest().getPlannedChambers(level)) {
                chamber.cells().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            }
            colony.nest().getGardens(level).get(0).cells().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
            // This scene is about where the two holes open on the slope, not which holes take their turn tonight (a
            // small colony opens only one of them): the arms and turns are switched off here (they have their own
            // proof over 400 landscapes, GalleryProofTest, and their own scene in TermiteForagingTests).
            com.antcolonies.colony.Galleries.of(colony).setSwitchForTests(false);
            TestClock.still(helper, 0.0); // midnight: foraging time
            ready[0] = true;
        });
        helper.onEachTick(() -> {
            Colony colony = TermiteMoundTests.colony(helper);
            if (colony == null || !ready[0]) {
                return;
            }
            List<NestPlan.ForagingTunnel> tunnels = colony.nest().getForagingTunnels(level);
            boolean allOpen = true;
            for (NestPlan.ForagingTunnel tunnel : tunnels) {
                allOpen &= ForagingHoles.isDug(level, tunnel) && ForagingHoles.isOpen(level, tunnel.hole());
            }
            if (helper.getTick() % 1000 == 0) {
                AntColoniesMod.LOGGER.info("[hill-test] tick {}: {}", helper.getTick(), tunnels.stream().map(t -> t.tunnel().stream()
                        .filter(c -> com.antcolonies.colony.Nest.isDugOut(level, c)).count() + "/" + t.tunnel().size() + " dug, hole " + level.getBlockState(t.hole())).toList());
            }
            if (allOpen && colony.foragingHoles().waysOut(level).size() >= 2) {
                helper.succeed();
            }
            helper.assertTrue(helper.getTick() < 19000, "the foraging tunnels were not dug and opened");
        });
    }
}
