package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;

/**
 * The shallow river bed, home of the bed dwellers (the crab and the snail; M9 river life, 2026-11-16): one rule for
 * where they may spawn, shared so the two never drift apart.
 * <p>
 * Minecraft's natural spawner draws one random height for a try in a column and keeps it for the whole group (BUGS 224).
 * A bed dweller that could spawn only at the one height just over the bed passed about one draw in seven in a river,
 * the tilapia seven times as many, and its group spawned one at a time (the next spot's bed lies at another height). So a
 * bed dweller may spawn anywhere in the water of a shallow column, and the world's own spawns are set down on the bed
 * under that spot ({@link #settle}).
 */
public final class RiverBed {
    private RiverBed() {
    }

    /** The deepest water a bed dweller spawns in (blocks of water over the bed): the shallows by the banks. */
    public static final int SHALLOW = 4;

    /**
     * The bed spot under a water spot: the lowest water block over a solid floor, at most {@link #SHALLOW} blocks down, with
     * at most {@link #SHALLOW} blocks of water over the floor; null where the water is deeper or there is no such floor.
     */
    @Nullable
    public static BlockPos bedUnder(LevelAccessor level, BlockPos pos) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return null;
        }
        BlockPos bed = pos;
        for (int i = 1; i < SHALLOW && level.getFluidState(bed.below()).is(FluidTags.WATER); i++) {
            bed = bed.below();
        }
        BlockPos floor = bed.below();
        if (!level.getBlockState(floor).isSolidRender(level, floor) || level.getFluidState(bed.above(SHALLOW)).is(FluidTags.WATER)) {
            return null;
        }
        return bed;
    }

    /**
     * Where a bed dweller may spawn: in the water of a shallow column (see {@link #bedUnder}) whose bed lies in the river
     * fish's height band, 13 under sea level to 6 over it (the pans lie a block or two up on the floodplain).
     */
    public static boolean maySpawnAt(LevelAccessor level, BlockPos pos) {
        BlockPos bed = bedUnder(level, pos);
        int sea = level.getSeaLevel();
        return bed != null && bed.getY() >= sea - 13 && bed.getY() <= sea + com.antcolonies.savanna.worldgen.RiverZones.HIGHEST_POOL;
    }

    /** A bed dweller the world spawned (not an egg or a command) is set down on the bed under where it was drawn. */
    public static void settle(Mob mob, LevelAccessor level, MobSpawnType reason) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        BlockPos bed = bedUnder(level, mob.blockPosition());
        if (bed != null && bed.getY() < mob.getBlockY()) {
            mob.moveTo(mob.getX(), bed.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        }
    }
}
