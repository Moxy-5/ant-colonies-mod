package com.antcolonies.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * Room to breed, the game balancer's guard (the animal life kit, {@code docs/plans/animal-life-cycle.md}): an animal
 * breeds, and its eggs hatch, only while fewer than its life file's local limit of its own kind live within
 * {@link #REACH} blocks. That one limit cannot run away, so breeding can never boom and lag.
 *
 * <p>Breeding is the main way a water refills (the user, 2026-11-24; review R10-1). Until then breeding also asked
 * whether the animal's spawning group was under its cap, the very room Minecraft's own spawner fills every tick, so
 * with a player playing there was almost never room to breed (and with only a spectator watching, always). Now one rule
 * owns each place: breeding asks the local limit only, and the world's spawner fills a kind that breeds only up to a
 * share of its count (the species mod's spawn rule: the savanna's {@code RiverZones.SPAWNER_SHARE}).
 */
public final class RoomToBreed {
    /** How far round an animal its own kind is counted (blocks). */
    public static final double REACH = 48;

    private RoomToBreed() {
    }

    /** Whether there is room for this animal's kind to grow here. */
    public static boolean here(Mob mob, int localLimit) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        return ownKindNear(level, mob) < localLimit;
    }

    /**
     * How many more of its kind the local limit has room for round it (0 or more): a clutch or a brood lets out at most
     * this many young, the rest failing, so a hatching never carries its kind past its limit (code review 2026-11-21: a
     * crab at 11 of 12 let out all 8 babies).
     */
    public static int left(ServerLevel level, Mob mob, int localLimit) {
        return Math.max(0, localLimit - ownKindNear(level, mob));
    }

    /** The same room left round a place (a clutch of eggs). */
    public static int leftAt(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.EntityType<?> kind, int localLimit) {
        int near = level.getEntities(kind, new net.minecraft.world.phys.AABB(pos).inflate(REACH), e -> e.isAlive()).size();
        return Math.max(0, localLimit - near);
    }

    /** How many of its own kind live within {@link #REACH} of it, itself counted. */
    public static int ownKindNear(ServerLevel level, Mob mob) {
        return level.getEntities(mob.getType(), mob.getBoundingBox().inflate(REACH), e -> e.isAlive()).size();
    }
}
