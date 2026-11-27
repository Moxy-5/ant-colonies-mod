package com.antcolonies.entity.goal;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;

/**
 * A goal's memory of the one nestmate it found no way to (review R3-11): it leaves that one alone for a while and does
 * something else, so a carer, a rescuer or a groomer cut off from its nestmate does not search for a route again and
 * again for as long as its goal lasts (BUGS 42's lesson, which {@link Errand} keeps for things in cells).
 */
final class LeftAlone {
    /** How long (game ticks) the nestmate is left alone: 10 seconds. */
    private static final int TICKS = 200;

    @Nullable
    private UUID who;
    private long until;

    /** No way to this one: it is left alone from now. */
    void leave(Entity nestmate) {
        who = nestmate.getUUID();
        until = nestmate.level().getGameTime() + TICKS;
    }

    /** Whether this one is still left alone. */
    boolean is(Entity nestmate) {
        return who != null && who.equals(nestmate.getUUID()) && nestmate.level().getGameTime() < until;
    }
}
