package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Lays a dead branch on the ground (see {@link FallenBranchBlock}): 2 or 3 blocks long, lying east-west or north-south,
 * thick end first. Used by the giant trees dropping branches ({@code tree.BranchShedding}, {@code tree.Forest}); until
 * M7.5 step 6c also by world generation in the open savanna. The whole branch must fit on flat solid ground, in air or
 * short plants (which it flattens); otherwise nothing is placed.
 */
public class FallenBranchFeature extends Feature<NoneFeatureConfiguration> {
    public FallenBranchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        return placeBranch(context.level(), context.origin(), context.random(), 2);
    }

    /**
     * Lays a branch with its thick end at {@code start} (block flags {@code flags}), if it fits there (trying the other
     * way round too). True if it was placed.
     */
    public static boolean placeBranch(LevelAccessor level, BlockPos start, RandomSource random, int flags) {
        int length = 2 + random.nextInt(2);
        Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (Direction way : new Direction[] {first, first.getClockWise(), first.getOpposite(), first.getCounterClockWise()}) {
            if (fits(level, start, way, length)) {
                for (int i = 0; i < length; i++) {
                    FallenBranchBlock.Part part = i == 0 ? FallenBranchBlock.Part.BASE
                            : i == length - 1 ? FallenBranchBlock.Part.TIP : FallenBranchBlock.Part.MIDDLE;
                    level.setBlock(start.relative(way, i), SavannaBlocks.FALLEN_BRANCH.get().defaultBlockState()
                            .setValue(FallenBranchBlock.AXIS, way.getAxis()).setValue(FallenBranchBlock.PART, part), flags);
                }
                return true;
            }
        }
        return false;
    }

    /** Whether a branch this long fits from {@code start} going {@code way}: free cells on sturdy ground, all level. */
    private static boolean fits(LevelAccessor level, BlockPos start, Direction way, int length) {
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(way, i);
            BlockState state = level.getBlockState(pos);
            boolean free = state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()
                    && !(state.getBlock() instanceof DoublePlantBlock) && !(state.getBlock() instanceof FallenBranchBlock);
            if (!free || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                return false;
            }
        }
        return true;
    }
}
