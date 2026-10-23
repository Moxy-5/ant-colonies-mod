package com.antcolonies.desert.gametest;

import com.antcolonies.gametest.TestScenes;
import static com.antcolonies.gametest.TestScenes.*;
import static com.antcolonies.gametest.TestScenes.MIDDLE;
import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.function.IntBinaryOperator;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.colony.NestSite;
import com.antcolonies.desert.registry.DesertBlocks;
import com.antcolonies.desert.registry.DesertEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


/**
 * Colonies on hills and uneven ground (the user's decisions of 2026-10-14: one shared ground reader, every planner
 * tested on hill arenas). The arenas are shaped by {@link TestScenes#shapeGround}: slopes, terraces, valleys and cliff
 * edges.
 * The red harvester ant's scenes (the split, step S4; the termites' are in {@link TermiteHillTests}).
 */
@GameTestHolder(AntColoniesMod.MODID)
@PrefixGameTestTemplate(false)
public class HarvesterHillTests {
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
        helper.spawn(DesertEntities.QUEEN_ANT.get(), MIDDLE, SURFACE + rise.applyAsInt(MIDDLE, MIDDLE), MIDDLE);
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
            if (founding == null || !level.getBlockState(entrance).is(DesertBlocks.BURROWED_DIRT.get())) {
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
        helper.spawn(DesertEntities.QUEEN_ANT.get(), MIDDLE, SURFACE, MIDDLE);
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < 6; i++) {
                helper.spawn(DesertEntities.WORKER_ANT.get(), MIDDLE - 2 + i % 3 * 2, SURFACE, MIDDLE - 3 + i / 3 * 6);
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
                    int planned = com.antcolonies.desert.colony.Hill.moundHeightAt(dx, dz);
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
                        && c.getQueen(helper.getLevel()) instanceof com.antcolonies.desert.entity.QueenAntEntity)
                .findFirst().orElse(null);
    }
}
