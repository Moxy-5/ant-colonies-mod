package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.savanna.registry.SavannaBlockEntities;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The African bullfrog's eggs (prey plan step 4, 2026-11-21; fun first, inspired by the real frog, which lays a film of
 * small eggs in the shallow water of a rain pool): a thin film of eggs lying on the water's top, drawn with Minecraft's
 * own frogspawn look (my pick), in the air block just over the water as Minecraft's frogspawn sits. The kit's clutch
 * ({@link EggClutchBlock}): after a day, watched and with room for tadpoles, each egg hatches into a tadpole that drops
 * into the water below; the father guards the spot. Gone (to air) when hatched, or if the water under it goes.
 */
public class FrogEggsBlock extends EggClutchBlock {
    public static final MapCodec<FrogEggsBlock> CODEC = simpleCodec(FrogEggsBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1.5, 16);

    public FrogEggsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EggClutchBlockEntity(SavannaBlockEntities.EGG_CLUTCH.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** It floats on still water: water right under it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** The tadpoles hatch into the water under the film. */
    @Override
    protected BlockPos youngAt(BlockPos pos) {
        return pos.below();
    }

    /**
     * Where to lay near this spot (a frog's block in the water): the air just over the water's top in its column (up to
     * 3 blocks up), or in a column next to it; null if there is no open water top so close.
     */
    @Nullable
    public static BlockPos spotNear(ServerLevel level, BlockPos at) {
        for (int r = 0; r <= 1; r++) {
            for (BlockPos column : BlockPos.betweenClosed(at.offset(-r, 0, -r), at.offset(r, 0, r))) {
                for (int up = -1; up <= 3; up++) {
                    BlockPos p = column.above(up);
                    if (level.getFluidState(p).is(FluidTags.WATER) && level.getBlockState(p.above()).isAir()) {
                        return p.above().immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Lays {@code eggs} of this kind as a film on the water here (an air block over water). False if it cannot. */
    public static boolean lay(ServerLevel level, BlockPos pos, EntityType<?> kind, int eggs) {
        BlockState film = SavannaBlocks.FROG_EGGS.get().defaultBlockState();
        if (!level.getBlockState(pos).isAir() || !film.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, film, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch) {
            clutch.lay(kind, eggs, level.getGameTime());
            return true;
        }
        return false;
    }
}
