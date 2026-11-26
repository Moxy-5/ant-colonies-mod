package com.antcolonies.savanna.animal;

import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.colony.GroundTop;
import com.antcolonies.savanna.block.DryGrassBlock;
import com.antcolonies.savanna.block.TallDryGrassBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Grazing on the savanna's land, one part for every grass eater (lifted out of the hippo when the Cape buffalo came,
 * 2026-11-25, {@code docs/plans/buffalo.md}): what a grazer eats, the plant at its jaws or near it, cropping it, where it
 * may stand on grass, and the water's top and the bank beside a pool or pan. The hippo crops a short plant to the ground
 * (its lawns, worn bare at the middle); the buffalo only crops a tall plant to a short one (grass never grows back in
 * Minecraft, so a herd never bares the ground). Every read is of loaded land only.
 */
public final class Grazing {
    private Grazing() {
    }

    /** Whether a grazer eats this plant: grass and ferns, and the savanna's grass tufts while still green (dry ones are the termites'). */
    public static boolean edible(BlockState s) {
        if (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.LARGE_FERN)) {
            return true;
        }
        return (s.getBlock() instanceof DryGrassBlock || s.getBlock() instanceof TallDryGrassBlock)
                && s.getValue(DryGrassBlock.GROWTH) != DryGrassBlock.Growth.DRY;
    }

    /** An edible plant at jaws over the column {@code jawX, jawZ} (the block at a grazer's feet height {@code feetY}, or the one over it), or null. */
    @Nullable
    public static BlockPos plantAt(Level level, double jawX, double feetY, double jawZ) {
        BlockPos at = BlockPos.containing(jawX, feetY + 0.2, jawZ);
        for (BlockPos p : List.of(at, at.above())) {
            if (level.isLoaded(p) && edible(level.getBlockState(p))) {
                return p;
            }
        }
        return null;
    }

    /** The nearest edible plant standing on the ground within {@code reach} columns of {@code stand} (its level or one off), or null. */
    @Nullable
    public static BlockPos plantNear(Level level, BlockPos stand, int reach) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = stand.offset(dx, dy, dz);
                    double d = dx * dx + dz * dz;
                    if (d < bestD && level.isLoaded(p) && edible(level.getBlockState(p))) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Eats the plant at {@code p}, grazed short, not pulled up: tall grass and large ferns are cropped to short grass and
     * ferns, a tall green savanna tuft to a short one; a short plant is cropped to the ground (the grass block under it
     * stays) only when {@code shortToGround} (the hippo), else left (the buffalo). Whether it ate anything. Mob griefing
     * off, it eats nothing.
     */
    public static boolean crop(Mob grazer, @Nullable BlockPos p, boolean shortToGround) {
        Level level = grazer.level();
        if (p == null || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, grazer)) {
            return false;
        }
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(DoublePlantBlock.HALF)) {
            BlockPos lower = s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
            BlockState low = level.getBlockState(lower);
            BlockState shorter = low.is(Blocks.TALL_GRASS) ? Blocks.SHORT_GRASS.defaultBlockState()
                    : low.is(Blocks.LARGE_FERN) ? Blocks.FERN.defaultBlockState()
                    : low.getBlock() instanceof TallDryGrassBlock
                            ? SavannaBlocks.DRY_GRASS.get().defaultBlockState().setValue(DryGrassBlock.GROWTH, low.getValue(DryGrassBlock.GROWTH))
                            : null;
            if (shorter != null) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, lower.above(), Block.getId(low));
                level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                level.setBlock(lower, shorter, Block.UPDATE_ALL);
                return true;
            }
        }
        if (!shortToGround) {
            return false;
        }
        level.destroyBlock(p, false, grazer);
        return true;
    }

    /**
     * Where a grazer would stand on grass (or soil) in the column x, z within {@code dy} of {@code y}: the ground's top
     * ({@link GroundTop}, through a giant tree's crown), dry, in loaded land; else null.
     */
    @Nullable
    public static BlockPos grassStand(Level level, int x, int z, int y, int dy) {
        if (!level.hasChunkAt(new BlockPos(x, y, z))) {
            return null;
        }
        BlockPos stand = GroundTop.cell(level, x, z);
        if (Math.abs(stand.getY() - y) > dy || !level.getFluidState(stand).isEmpty() || !level.getFluidState(stand.below()).isEmpty()) {
            return null;
        }
        BlockState ground = level.getBlockState(stand.below());
        return ground.is(Blocks.GRASS_BLOCK) || ground.is(BlockTags.DIRT) ? stand : null;
    }

    /**
     * The top block of the water at {@code pool} (its y), or {@link Integer#MIN_VALUE} if there is no pool, its land is
     * not loaded (an animal led far from home never loads it, review R9-17) or it holds no water (at its block or the one over it).
     */
    public static int waterTop(Level level, @Nullable BlockPos pool) {
        if (pool == null || !level.isLoaded(pool)) {
            return Integer.MIN_VALUE;
        }
        BlockPos.MutableBlockPos p = pool.mutable();
        if (!level.getFluidState(p).is(FluidTags.WATER)) {
            // A block landed on the bed at the pool's spot (sand, gravel, a player's block): the water over it is still
            // the pool (review R9-18: "gone" asked the block over it too, this did not, and the two disagreed for good).
            p.move(0, 1, 0);
            if (!level.getFluidState(p).is(FluidTags.WATER)) {
                return Integer.MIN_VALUE;
            }
        }
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < pool.getY() + 16) {
            p.move(0, 1, 0);
        }
        return p.getY();
    }

    /** Where a grazer would stand on dry bank in the column x, z: its ground's top level with the water's top {@code w} or a block over; else null. */
    @Nullable
    public static BlockPos bankStand(Level level, int x, int z, int w) {
        if (!level.hasChunkAt(new BlockPos(x, w, z))) {
            return null;
        }
        BlockPos stand = GroundTop.cell(level, x, z);
        if (stand.getY() < w + 1 || stand.getY() > w + 2 || !full(level, stand.below()) || !dryOpen(level, stand)
                || !dryOpen(level, stand.above()) || !level.getFluidState(stand.below()).isEmpty()) {
            return null;
        }
        return stand;
    }

    private static boolean full(Level level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static boolean dryOpen(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }
}
