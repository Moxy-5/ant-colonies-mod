package com.antcolonies.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Sends an animal into one exact block (animal life cycle step 2, 2026-11-19). Minecraft's own "go there"
 * ({@code moveTo(x, y, z)} and {@code moveTo(mob)}) plans its path to end within a block of the place, so an animal that
 * must stand on its nest, beside its mate or at its laying spot stops one block short and stays there. This plans the
 * path into the block itself.
 */
public final class GoExactly {
    private GoExactly() {
    }

    /** Starts a path into the block holding this point; false if there is none. */
    public static boolean to(Mob animal, double x, double y, double z, double speed) {
        Path path = animal.getNavigation().createPath(BlockPos.containing(x, y, z), 0);
        return animal.getNavigation().moveTo(path, speed);
    }
}
