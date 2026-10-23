package com.antcolonies.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps the game tests out of each other's way (the test health plan, 2026-10-16). The tests run side by side in one
 * world, and three things leaked from one test into another, so a test could pass alone and fail beside others:
 * <ol>
 * <li><b>Shared dice.</b> Every colony rolled the world's one random stream, so its luck changed with whatever the
 * neighbouring colonies rolled. Now each colony rolls its own ({@link Colony#random}), seeded here from its test's name
 * and its spot in the arena: a test rolls the same colony dice alone or in a crowd. (Each insect still has its own
 * dice, as every Minecraft mob does, so a test is not fully the same every run.)</li>
 * <li><b>Leftover colonies.</b> When a test passes, Minecraft removes only the creatures inside its box (not a forager
 * out searching), and when it fails nothing at all; the colony itself lived on in the colony list, "away", through all
 * the later tests. Now, when a test is done, every colony that was founded in its arena is removed, with every insect,
 * egg, larva and body of it, wherever it is.</li>
 * <li><b>Long trails</b> (2026-10-17): a trunk trail can reach 96 blocks and the arenas are about 45 apart, so each
 * colony's trails stop at its own arena's edge ({@code TrunkTrails.setTestFence}, signed up by the desert's tests).</li>
 * <li><b>Neighbours</b> (2026-10-18): termite galleries reach 50 blocks, so a colony met the next test's colony and walled
 * its galleries off; a colony only meets colonies of its own arena ({@code Neighbours.setTestFence}).</li>
 * <li><b>Ground round the arena</b> (2026-10-22): the test server runs only the chunks under a test's arena, but a
 * colony reaches past it (a termite nest 12 blocks from the arena's edge has gallery arms of 14 to 16 blocks). Where the
 * arena's edge fell near its chunk's edge (the test's random place in the world decides it, about 1 run in 3), the arm
 * ends lay in a chunk Minecraft keeps but does not run, and every worker walking there stood still for the rest of the
 * test. Now the chunks within {@link #GROUND_ROUND} blocks of each running arena run too, by a loading ticket of the
 * test's own, taken away when the test is done.</li>
 * <li><b>Counting boxes</b> (in the tests themselves): a test that counts "the workers in my box" misses its own
 * foragers out searching past the box's edge and counts a neighbour's. Such checks count the colony's own insects
 * instead ({@link #insectsOf}).</li>
 * </ol>
 */
public final class TestIsolation {
    /** The tests running now, as the test server keeps them ({@code GameTestTicker.testInfos}). */
    @Nullable
    private static Collection<GameTestInfo> running;
    /** Each running test's box, read while it runs (its structure block may be gone once it is done). */
    private static final Map<GameTestInfo, AABB> ARENAS = new HashMap<>();
    /** Each colony and the test in whose arena it was founded. */
    private static final Map<UUID, GameTestInfo> OWNERS = new HashMap<>();
    /** Each species' own fences round a colony in its arena (the split, step S4: signed up by its tests, {@link #addFence}). */
    private static final List<BiConsumer<Colony, AABB>> FENCES = new ArrayList<>();
    /** How far past its arena's edge a test's ground runs (blocks; a colony's food radius is 24, its gallery arms 16). */
    static final int GROUND_ROUND = 32;
    /** The loading ticket for the ground round the arenas (one per chunk, so a chunk two tests share runs while either does). */
    private static final TicketType<ChunkPos> GROUND = TicketType.create("antcolonies_test_ground", Comparator.comparingLong(ChunkPos::toLong));
    /** The chunks each running test keeps running. */
    private static final Map<GameTestInfo, List<ChunkPos>> GROUND_HELD = new HashMap<>();

    private TestIsolation() {
    }

    /** A species' own fence round each colony founded in a test's arena (its tests' sign-up), given that arena's box. */
    public static void addFence(BiConsumer<Colony, AABB> fence) {
        FENCES.add(fence);
    }

    /** Called once when the test server starts. */
    @SuppressWarnings("unchecked")
    static void install() {
        try {
            Field field = GameTestTicker.class.getDeclaredField("testInfos");
            field.setAccessible(true);
            running = (Collection<GameTestInfo>) field.get(GameTestTicker.SINGLETON);
        } catch (ReflectiveOperationException e) {
            AntColoniesMod.LOGGER.error("[test-isolation] could not see the running tests: the tests are not kept apart", e);
            return;
        }
        Colony.setTestSeeds(TestIsolation::seedAt);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer().overworld()));
    }

    /** The seed of the dice of a colony at this spot: from its test's name and the spot in the test's arena. */
    @Nullable
    private static Long seedAt(BlockPos pos) {
        GameTestInfo test = testAt(pos);
        if (test == null) {
            return null;
        }
        AABB arena = ARENAS.get(test);
        long x = pos.getX() - (long) Math.floor(arena.minX);
        long z = pos.getZ() - (long) Math.floor(arena.minZ);
        return test.getTestName().hashCode() * 1_000_003L + x * 1009L + z;
    }

    /** The running test whose arena holds this spot, or null. */
    @Nullable
    private static GameTestInfo testAt(BlockPos pos) {
        readArenas();
        for (Map.Entry<GameTestInfo, AABB> entry : ARENAS.entrySet()) {
            AABB arena = entry.getValue();
            if (!entry.getKey().isDone() && arena.minX <= pos.getX() && pos.getX() < arena.maxX
                    && arena.minZ <= pos.getZ() && pos.getZ() < arena.maxZ) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Keeps every chunk within {@link #GROUND_ROUND} blocks of this arena running while its test runs. */
    private static void holdGround(ServerLevel level, GameTestInfo test, AABB arena) {
        List<ChunkPos> held = new ArrayList<>();
        int minX = ((int) Math.floor(arena.minX) - GROUND_ROUND) >> 4;
        int maxX = ((int) Math.ceil(arena.maxX) + GROUND_ROUND) >> 4;
        int minZ = ((int) Math.floor(arena.minZ) - GROUND_ROUND) >> 4;
        int maxZ = ((int) Math.ceil(arena.maxZ) + GROUND_ROUND) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                // distance 2 from level 33: ticket level 31, entities run in this very chunk
                level.getChunkSource().addRegionTicket(GROUND, chunk, 2, chunk);
                held.add(chunk);
            }
        }
        GROUND_HELD.put(test, held);
    }

    private static void letGroundGo(ServerLevel level, GameTestInfo test) {
        List<ChunkPos> held = GROUND_HELD.remove(test);
        if (held != null) {
            held.forEach(chunk -> level.getChunkSource().removeRegionTicket(GROUND, chunk, 2, chunk));
        }
    }

    private static void readArenas() {
        if (running == null) {
            return;
        }
        for (GameTestInfo test : running) {
            if (!test.isDone() && test.getStructureBlockPos() != null && !ARENAS.containsKey(test)) {
                try {
                    AABB arena = test.getStructureBounds();
                    ARENAS.put(test, arena);
                    holdGround(test.getLevel(), test, arena);
                } catch (IllegalStateException e) {
                    // the structure is not placed yet: next tick
                }
            }
        }
    }

    private static void tick(ServerLevel level) {
        if (running == null) {
            return;
        }
        readArenas(); // (each new test's ground starts running at once)
        ColonyManager manager = ColonyManager.get(level);
        // Which test each colony belongs to (a colony can be founded at any moment of its test).
        for (Colony colony : manager.getColonies()) {
            if (!OWNERS.containsKey(colony.getId())) {
                GameTestInfo test = testAt(colony.getCenter());
                if (test != null) {
                    OWNERS.put(colony.getId(), test);
                    FENCES.forEach(fence -> fence.accept(colony, ARENAS.get(test))); // (e.g. its trails stop at its own arena's edge)
                    colony.neighbours().setTestFence(ARENAS.get(test)); // (another test's colonies are not neighbours)
                }
            }
        }
        // The colonies of tests that are done go, with all of their insects.
        List<UUID> gone = new ArrayList<>();
        OWNERS.forEach((id, test) -> {
            if (test.isDone()) {
                gone.add(id);
            }
        });
        if (!gone.isEmpty()) {
            List<Entity> leftovers = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                UUID colonyId = colonyOf(entity);
                if (colonyId != null && gone.contains(colonyId)) {
                    leftovers.add(entity);
                }
            }
            leftovers.forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED));
            for (UUID id : gone) {
                manager.removeColony(id);
                OWNERS.remove(id);
            }
        }
        GROUND_HELD.keySet().stream().filter(GameTestInfo::isDone).toList().forEach(test -> letGroundGo(level, test));
        ARENAS.keySet().removeIf(test -> test.isDone() && !OWNERS.containsValue(test));
    }

    /** The colony an insect, egg, larva or body belongs to, or null. */
    @Nullable
    private static UUID colonyOf(Entity entity) {
        if (entity instanceof AbstractAntEntity ant) {
            return ant.getColonyId();
        } else if (entity instanceof AntEggEntity egg) {
            return egg.getColonyId();
        } else if (entity instanceof AntLarvaEntity larva) {
            return larva.getColonyId();
        } else if (entity instanceof AntCorpseEntity corpse) {
            return corpse.getColonyId();
        }
        return null;
    }

    /**
     * Every insect of this kind that belongs to the colony, wherever it is in the world (a forager out searching can be
     * past the edge of its test's box, and a neighbour's forager inside it).
     */
    public static <T extends AbstractAntEntity> List<T> insectsOf(ServerLevel level, Colony colony, Class<T> kind) {
        List<T> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (kind.isInstance(entity) && colony.getId().equals(((AbstractAntEntity) entity).getColonyId())) {
                found.add(kind.cast(entity));
            }
        }
        return found;
    }
}
