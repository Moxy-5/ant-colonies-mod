package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.savanna.block.HippoGrassBlock;
import com.antcolonies.savanna.block.ReedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/**
 * Cover in the river (the food web's step 4, 2026-11-23, {@code docs/plans/river-tie-up.md}; reeds plan step 6): the
 * water among a reed bed's stalks and the water under a hippo grass mat. A scared barb or tilapia dashes into it and
 * keeps still a while ({@link HideInCoverGoal}); hatchling crocodiles rest in it at their crèche's edge.
 *
 * <p>The one shared "can I see it" rule for every fish hunter (the catfish, crocodiles, otters, the bull shark,
 * bullfrogs): a fish keeping still in cover is seen only within {@link #SEEN_WITHIN} blocks ({@link #hiddenFrom}). A
 * fish swimming out of the reeds is seen as usual, so hunters lose a chase, not their meals. My picks, animals fun first.
 */
public final class RiverCover {
    /** How near a hunter must be to see a fish keeping still in cover (blocks; the plan's 2). */
    public static final double SEEN_WITHIN = 2.0;
    /** How far a scared fish looks for cover (blocks across; the plan's 8), and up or down. */
    static final int LOOK_ACROSS = 8, LOOK_UP_DOWN = 3;

    private RiverCover() {
    }

    /** A fish that hides in cover: its hiding state, read by the hunters. */
    public interface Hider {
        Hiding hiding();

        /** Called as it sets off for cover (a fish with a bolt clip plays it). */
        default void startedDash() {
        }
    }

    /** One fish's hiding: scared until when (game time), and whether it is keeping still in cover now. */
    public static final class Hiding {
        long scaredUntil;
        boolean hidden;
        /** No new look for cover before this (game time), after a look found none. */
        long noLookUntil;

        /** Scared now, for a few seconds: it looks for cover at its next chance. */
        public void scare(long now) {
            scaredUntil = now + HideInCoverGoal.SCARED_TICKS;
        }

        /** Whether it is keeping still in cover now. */
        public boolean hidden() {
            return hidden;
        }
    }

    /** Scares this fish if it is one that hides (a strike near it). */
    static void scare(Entity e) {
        if (e instanceof Hider h) {
            h.hiding().scare(e.level().getGameTime());
        }
    }

    /** Whether this water block is cover: water among reed stalks, or water with a hippo grass mat on the surface over it. */
    public static boolean isCover(BlockGetter level, BlockPos pos) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        if (level.getBlockState(pos).getBlock() instanceof ReedBlock) {
            return true;
        }
        BlockPos.MutableBlockPos up = pos.mutable();
        for (int i = 0; i < 4; i++) {
            up.move(0, 1, 0);
            if (!level.getFluidState(up).is(FluidTags.WATER)) {
                return level.getBlockState(up).getBlock() instanceof HippoGrassBlock;
            }
        }
        return false;
    }

    /** The nearest cover water within {@link #LOOK_ACROSS} blocks of this spot (null: none). */
    @Nullable
    public static BlockPos nearest(BlockGetter level, BlockPos from) {
        return nearest(level, from, LOOK_ACROSS);
    }

    /** The nearest cover water within {@code reach} blocks across (and {@link #LOOK_UP_DOWN} up or down) of this spot (null: none). */
    @Nullable
    public static BlockPos nearest(BlockGetter level, BlockPos from, int reach) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int flat = dx * dx + dz * dz;
                if (flat > reach * reach || flat >= bestD) {
                    continue;
                }
                for (int dy = -LOOK_UP_DOWN; dy <= LOOK_UP_DOWN; dy++) {
                    double d = flat + dy * dy;
                    if (d < bestD && isCover(level, at.setWithOffset(from, dx, dy, dz))) {
                        best = at.immutable();
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }

    /** Whether this hunter cannot see this prey: a fish keeping still in cover, farther than {@link #SEEN_WITHIN}. */
    public static boolean hiddenFrom(Entity hunter, Entity prey) {
        return prey instanceof Hider h && h.hiding().hidden && prey.distanceToSqr(hunter) > SEEN_WITHIN * SEEN_WITHIN;
    }
}
