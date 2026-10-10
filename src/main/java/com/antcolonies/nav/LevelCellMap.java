package com.antcolonies.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The real game world, seen as cells for the route planner. */
public final class LevelCellMap implements CellMap {
    private final Level level;

    public LevelCellMap(Level level) {
        this.level = level;
    }

    @Override
    public boolean isOpen(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.FIRE)
                && !state.is(Blocks.COBWEB)
                && !state.is(Blocks.SWEET_BERRY_BUSH)
                && !state.is(Blocks.POWDER_SNOW);
    }

    @Override
    public boolean isSolidFace(BlockPos pos, Direction face) {
        return level.isLoaded(pos) && level.getBlockState(pos).isFaceSturdy(level, pos, face);
    }
}
