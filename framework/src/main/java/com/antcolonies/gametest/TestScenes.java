package com.antcolonies.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scene kit every species' game tests share (the split, step S4: the helpers that were in the mixed test files, none
 * naming a species): the arena's ground, finding a colony of a species, digging its nest at once, its real insects,
 * and the mock players of the body tests.
 */
public final class TestScenes {
    /** The arena's ground level (the top of the ground is one below) and its middle, in the old arena's corner. */
    public static final int SURFACE = 12;
    public static final int MIDDLE = 12;
    /** The height the insects stand at in the noticing tests: on the test world's own floor, under the empty arena. */
    public static final int FLOOR = 0;

    private TestScenes() {
    }

    /** Deep earth under the old arena (25 x 25): dirt up to the surface, grass on top. */
    public static void deepGround(GameTestHelper helper) {
        for (int x = 0; x < 25; x++) {
            for (int z = 0; z < 25; z++) {
                for (int y = 0; y < SURFACE - 1; y++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
                helper.setBlock(x, SURFACE - 1, z, Blocks.GRASS_BLOCK);
            }
        }
    }

    /** Shapes the arena's ground: the top ground block of column (x, z) is {@code SURFACE - 1 + rise(x, z)}, grass on top. */
    public static void shapeGround(GameTestHelper helper, int size, IntBinaryOperator rise) {
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

    /** The colony of this species in this test area (one with a living queen), or null if there is none yet. */
    public static Colony colony(GameTestHelper helper, ResourceLocation species) {
        AABB area = helper.getBounds();
        return ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .findFirst().orElse(null);
    }

    /** The colonies of this species in the test's arena with a living queen, west first. */
    public static List<Colony> colonies(GameTestHelper helper, ResourceLocation species) {
        AABB area = helper.getBounds();
        List<Colony> found = new ArrayList<>(ColonyManager.get(helper.getLevel()).getColonies().stream()
                .filter(c -> area.contains(Vec3.atCenterOf(c.getCenter())) && c.getQueen(helper.getLevel()) != null && c.getSpecies().equals(species))
                .toList());
        found.sort(Comparator.comparingInt(c -> c.getCenter().getX()));
        return found;
    }

    /** Every block of the shaft and of every room of the plan dug out at once. */
    public static void digNestAtOnce(ServerLevel level, Colony colony) {
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
    public static List<AbstractAntEntity> real(GameTestHelper helper, Colony colony) {
        return helper.getLevel().getEntitiesOfClass(AbstractAntEntity.class, helper.getBounds().inflate(16),
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && mob.nestCaste() != null);
    }

    /** Puts a mock player standing on the arena floor at this spot (the middle of the block at x, z, plus any part). */
    public static void place(GameTestHelper helper, Player player, double x, double z) {
        place(helper, player, new Vec3(x + 0.5, FLOOR, z + 0.5));
    }

    /** Puts the player at this spot in the arena (relative), on the ground. */
    public static void place(GameTestHelper helper, Player player, Vec3 spot) {
        Vec3 at = helper.absoluteVec(spot);
        player.setPos(at.x, at.y, at.z);
        player.setOnGround(true);
    }

    /** A spot in the arena dx, dz blocks from the insect (while {@code following}, or the first time), else where it was. */
    public static Vec3 beside(GameTestHelper helper, @Nullable Vec3 was, AbstractAntEntity insect, double dx, double dz, boolean following) {
        if (was != null && !following) {
            return was;
        }
        Vec3 at = helper.relativeVec(insect.position());
        return new Vec3(at.x + dx, FLOOR, at.z + dz);
    }

    public static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }
}
