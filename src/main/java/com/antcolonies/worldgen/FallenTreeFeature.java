package com.antcolonies.worldgen;

import com.antcolonies.block.DeadWoodBlock;
import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.registry.ModBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A fallen dead acacia (user's idea): the stump it snapped off, 1 or 2 blocks of upright {@link DeadWoodBlock}, and
 * beside it the trunk lying on the ground, {@link #MIN_LENGTH} to {@link #MAX_LENGTH} blocks long, with a few dead
 * branches lying round the crown end where the tree's top crashed down. The trunk needs solid ground under it, but may
 * span a one-block dip (as a real one would, resting on its ends). It was rare in savannas until M7.5 step 6c
 * (2026-10-19), when the giant trees' fallen young trees and giant limbs replaced it in new land; the blocks stay in old
 * worlds, and the game tests still lay one to check termites eat the old dead wood.
 */
public class FallenTreeFeature extends Feature<NoneFeatureConfiguration> {
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 6;

    public FallenTreeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return placeTree(context.level(), context.origin(), context.random(), 2);
    }

    /** Lays a fallen tree with its stump at {@code stump}, if it fits there (trying all four ways). True if placed. */
    public static boolean placeTree(LevelAccessor level, BlockPos stump, RandomSource random, int flags) {
        int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
        Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (Direction way : new Direction[] {first, first.getClockWise(), first.getOpposite(), first.getCounterClockWise()}) {
            // The trunk lies alongside where it fell: starting one block out from the stump, one block to the side.
            BlockPos trunkStart = stump.relative(way).relative(way.getClockWise());
            if (!isFree(level, stump) || !solid(level, stump.below()) || !trunkFits(level, trunkStart, way, length)) {
                continue;
            }
            BlockState wood = ModBlocks.DEAD_WOOD.get().defaultBlockState();
            int stumpHeight = 1 + random.nextInt(2);
            for (int i = 0; i < stumpHeight && isFree(level, stump.above(i)); i++) {
                level.setBlock(stump.above(i), wood.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), flags);
            }
            for (int i = 0; i < length; i++) {
                level.setBlock(trunkStart.relative(way, i), wood.setValue(RotatedPillarBlock.AXIS, way.getAxis()), flags);
            }
            // The crown end: dead branches lying out to both sides, and maybe one beyond the tip.
            BlockPos crown = trunkStart.relative(way, length - 1);
            for (Direction side : new Direction[] {way.getClockWise(), way.getCounterClockWise()}) {
                if (random.nextInt(3) > 0) {
                    placeBranchOut(level, crown.relative(side).relative(way, -random.nextInt(2)), side, random, flags);
                }
            }
            if (random.nextBoolean()) {
                placeBranchOut(level, crown.relative(way), way, random, flags);
            }
            return true;
        }
        return false;
    }

    /** A 2-block branch lying from {@code start} going {@code way}, thick end at the trunk; skipped if it does not fit. */
    private static void placeBranchOut(LevelAccessor level, BlockPos start, Direction way, RandomSource random, int flags) {
        BlockPos tip = start.relative(way);
        if (!isFree(level, start) || !isFree(level, tip) || !solid(level, start.below()) || !solid(level, tip.below())) {
            return;
        }
        BlockState branch = ModBlocks.FALLEN_BRANCH.get().defaultBlockState().setValue(FallenBranchBlock.AXIS, way.getAxis());
        level.setBlock(start, branch.setValue(FallenBranchBlock.PART, FallenBranchBlock.Part.BASE), flags);
        level.setBlock(tip, branch.setValue(FallenBranchBlock.PART, FallenBranchBlock.Part.TIP), flags);
    }

    /** Whether the trunk fits: every cell free, its two ends on solid ground, and no more than one block in a row without. */
    private static boolean trunkFits(LevelAccessor level, BlockPos start, Direction way, int length) {
        int gap = 0;
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(way, i);
            if (!isFree(level, pos)) {
                return false;
            }
            boolean ground = solid(level, pos.below());
            if ((i == 0 || i == length - 1) && !ground) {
                return false;
            }
            gap = ground ? 0 : gap + 1;
            if (gap > 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFree(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty() && !(state.getBlock() instanceof DoublePlantBlock);
    }

    private static boolean solid(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }
}
