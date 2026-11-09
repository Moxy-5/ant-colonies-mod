package com.antcolonies.gametest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestPlan;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * This test's colony of this species (one with a living queen), or null if there is none yet: found by its owner,
     * the test whose arena it was founded in (test revamp R2, {@link TestIsolation#colonyOf}), not by the arena's box.
     */
    public static Colony colony(GameTestHelper helper, ResourceLocation species) {
        return TestIsolation.colonyOf(helper, species);
    }

    /** This test's colonies of this species with a living queen, west first (found by owner, as {@link #colony}). */
    public static List<Colony> colonies(GameTestHelper helper, ResourceLocation species) {
        List<Colony> found = new ArrayList<>(TestIsolation.coloniesOf(helper, species));
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

    /** This colony's real workers and soldiers that may go into the numbers, wherever they are (R2: not only near the arena). */
    public static List<AbstractAntEntity> real(GameTestHelper helper, Colony colony) {
        return TestIsolation.insectsOf(helper.getLevel(), colony, AbstractAntEntity.class).stream()
                .filter(mob -> mob.isAlive() && mob.nestCaste() != null).toList();
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

    /**
     * The scene kit's start (test revamp R3), species-neutral (the species' scenes hand their types in): deep ground, a
     * queen of this type at the arena's middle, after 5 ticks a worker of that type at each spot (the arena's own
     * numbers; none, no workers), and after {@code lookAfter} ticks {@code then} gets the colony {@code find} finds (the
     * test's own colony, maybe null: the scene or the test checks it).
     */
    public static void queenAndWorkers(GameTestHelper helper, EntityType<?> queen, EntityType<?> worker, List<BlockPos> spots, int lookAfter,
            Function<GameTestHelper, Colony> find, Consumer<Colony> then) {
        queenAndWorkers(helper, queen, null, null, worker, spots);
        helper.runAfterDelay(lookAfter, () -> then.accept(find.apply(helper)));
    }

    /**
     * The scene kit's start without the look-up: deep ground, a queen of this type at the middle, and after 5 ticks the
     * partner (a termite king, say; none if null) at its spot, then a worker at each spot.
     */
    public static void queenAndWorkers(GameTestHelper helper, EntityType<?> queen, @Nullable EntityType<?> partner, @Nullable BlockPos partnerSpot,
            EntityType<?> worker, List<BlockPos> spots) {
        deepGround(helper);
        helper.spawn(queen, MIDDLE, SURFACE, MIDDLE);
        if (partner != null || !spots.isEmpty()) {
            helper.runAfterDelay(5, () -> {
                if (partner != null) {
                    helper.spawn(partner, partnerSpot);
                }
                spots.forEach(spot -> helper.spawn(worker, spot));
            });
        }
    }

    /** {@code n} spots, the i-th where {@code spot} puts it (the workers of {@link #queenAndWorkers}). */
    public static List<BlockPos> spots(int n, IntFunction<BlockPos> spot) {
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            spots.add(spot.apply(i));
        }
        return spots;
    }
}
