package com.antcolonies.animal;

import java.lang.reflect.Method;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;

/**
 * Room to breed, the game balancer's guard (the animal life kit, {@code docs/plans/animal-life-cycle.md}): an animal
 * breeds, and its eggs hatch, only while fewer than its life file's local limit of its own kind live within
 * {@link #REACH} blocks, <b>and</b> its spawning group is under its cap where it is. So breeding can only fill a river up
 * to what the world's own spawner already allows: it can never boom and lag. Young count in their parents' spawning
 * group like any member.
 */
public final class RoomToBreed {
    /** How far round an animal its own kind is counted (blocks). */
    public static final double REACH = 48;
    /** Minecraft's spawner divides a group's cap by this (17 by 17 chunks, {@code NaturalSpawner.MAGIC_NUMBER}). */
    private static final int CHUNKS_PER_CAP = 17 * 17;
    /** Minecraft's own "is this group under its cap here?" (the world's cap and the cap round each player). */
    private static final Method UNDER_CAP = underCap();

    private RoomToBreed() {
    }

    /** Whether there is room for this animal's kind to grow here (both rules). */
    public static boolean here(Mob mob, int localLimit) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        return ownKindNear(level, mob) < localLimit && groupHasRoom(level, mob.getType().getCategory(), new ChunkPos(mob.blockPosition()));
    }

    /** How many of its own kind live within {@link #REACH} of it, itself counted. */
    public static int ownKindNear(ServerLevel level, Mob mob) {
        return level.getEntities(mob.getType(), mob.getBoundingBox().inflate(REACH), e -> e.isAlive()).size();
    }

    /**
     * Whether this spawning group is under its cap at this chunk, by the spawner's own last count (the world's cap and
     * the cap round the players near). With no count yet (the spawner has not run), or no player anywhere near enough
     * for the spawner to count (a game test server, an empty world, only a spectator watching: the spawner's cap is
     * then 0), there is room: the local limit alone holds.
     */
    public static boolean groupHasRoom(ServerLevel level, MobCategory group, ChunkPos at) {
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        if (state == null || state.getSpawnableChunkCount() == 0 || !countingPlayerNear(level, at)) {
            return true;
        }
        if (UNDER_CAP != null) {
            try {
                return (boolean) UNDER_CAP.invoke(state, group, at);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall through to the world's cap alone
            }
        }
        int cap = group.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / CHUNKS_PER_CAP;
        return state.getMobCategoryCounts().getInt(group) < cap;
    }

    /**
     * Whether a player the spawner counts (any but a spectator) is within its reach of this chunk. Minecraft's own cap
     * check says "no room" wherever no such player is near (its cap round each player is the only one it asks there),
     * so with only a spectator watching nothing could ever breed (found 2026-11-19 filming the crabs: the user watches
     * in spectator). There the spawner spawns nothing either, and the local limit alone holds.
     */
    private static boolean countingPlayerNear(ServerLevel level, ChunkPos at) {
        double x = at.getMiddleBlockX(), z = at.getMiddleBlockZ();
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            double dx = player.getX() - x, dz = player.getZ() - z;
            if (!player.isSpectator() && dx * dx + dz * dz < SPAWNER_REACH * SPAWNER_REACH) {
                return true;
            }
        }
        return false;
    }

    /** How far from a player the spawner counts a chunk as near it (Minecraft's 128 blocks). */
    private static final double SPAWNER_REACH = 128;

    private static Method underCap() {
        try {
            Method m = NaturalSpawner.SpawnState.class.getDeclaredMethod("canSpawnForCategory", MobCategory.class, ChunkPos.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ColonyFrameworkMod.LOGGER.warn("Room to breed: Minecraft's cap check not found, counting the world's cap only ({})", e.toString());
            return null;
        }
    }
}
