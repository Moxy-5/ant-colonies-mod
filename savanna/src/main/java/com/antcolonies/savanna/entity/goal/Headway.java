package com.antcolonies.savanna.entity.goal;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.world.phys.Vec3;

/**
 * BUGS 141: whether an insect walking to a place is getting anywhere. Walking {@link #NO_PROGRESS} ticks without
 * moving half a block (its place inside the refuse heap by the entrance, a step it cannot take), its goal stops it and
 * lets it stand a while: it used to plan the same walk for ever. Waiting for the crowd ahead is not counted.
 */
final class Headway {
    /** Ticks walking without headway before it counts as lost (my pick: 8 s, past a doorway's peek and a crowd's shuffle). */
    private static final int NO_PROGRESS = 160;

    /** Where it was when it last made headway on a walk, and the tick (none while not walking). */
    @Nullable
    private Vec3 at;
    private int since;

    void reset() {
        at = null;
    }

    /** Asked every tick of a walk: true once, when it has walked {@link #NO_PROGRESS} ticks without moving half a block. */
    boolean lost(AbstractAntEntity ant) {
        Vec3 here = ant.position();
        if (ant.getNavigator().isDone() || ant.getNavigator().heldForCrowd() || at == null || here.distanceToSqr(at) > 0.25) {
            at = ant.getNavigator().isDone() ? null : here;
            since = ant.tickCount;
            return false;
        }
        if (ant.tickCount - since < NO_PROGRESS) {
            return false;
        }
        at = null;
        return true;
    }
}
