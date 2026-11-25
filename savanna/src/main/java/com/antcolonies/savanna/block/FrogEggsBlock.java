package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.savanna.animal.BullfrogEntity;
import com.antcolonies.savanna.animal.TadpoleEntity;
import com.antcolonies.savanna.registry.SavannaBlockEntities;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bullfrog's eggs (the river tie-up part B, 2026-11-23; fun first, inspired by the real frog): a floating mat of small
 * black eggs in clear jelly at the top of shallow water, laid by a female while her male clasps her
 * ({@link BullfrogEntity#mated}). Drawn as a thin film at the surface with Minecraft's own frogspawn picture (my pick: it
 * already looks right and matches the game). It always holds water and turns back into plain water when the eggs hatch
 * or are long gone.
 *
 * <p>The life kit's clutch ({@link EggClutchBlock}): after the bullfrog's hatching days (a day), with a watcher near and
 * room for tadpoles (fewer than {@link TadpoleEntity#LOCAL_LIMIT} within {@link RoomToBreed#REACH} blocks and the tadpole
 * group under its cap), each egg the room allows hatches into a tadpole, one school, the eggs' guarding father its
 * father ({@link TadpoleEntity#father}).
 */
public class FrogEggsBlock extends EggClutchBlock implements LiquidBlockContainer {
    public static final MapCodec<FrogEggsBlock> CODEC = simpleCodec(FrogEggsBlock::new);
    /** The film just under and over the water's top (a still water block's surface is about 14.2 pixels up). */
    private static final VoxelShape SHAPE = Block.box(0, 13.5, 0, 16, 15, 16);
    /** How far from the eggs their father is looked for when they hatch (blocks). */
    static final double FATHER_WITHIN = 16.0;

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

    /** Floating: there must be water under it or a bed right under it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getFluidState(below).is(FluidTags.WATER) || level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return canSurvive(state, level, pos) ? state : Blocks.WATER.defaultBlockState();
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /** Hatched or long gone: plain water again. */
    @Override
    protected void toShells(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** The eggs' room to hatch: room for tadpoles here (not for frogs: a tadpole asks that when it climbs out). */
    @Override
    protected boolean roomFor(ServerLevel level, BlockPos pos, EntityType<?> kind) {
        EntityType<TadpoleEntity> tadpole = SavannaEntities.TADPOLE.get();
        return RoomToBreed.leftAt(level, pos, tadpole, TadpoleEntity.LOCAL_LIMIT) > 0;
    }

    /** Each egg the room allows becomes a tadpole of one school, just hatched, knowing its guarding father. */
    @Override
    protected void hatch(ServerLevel level, BlockPos pos, BlockState state, EggClutchBlockEntity clutch, EntityType<?> kind, RandomSource random) {
        EntityType<TadpoleEntity> type = SavannaEntities.TADPOLE.get();
        int count = Math.min(clutch.count(), RoomToBreed.leftAt(level, pos, type, TadpoleEntity.LOCAL_LIMIT));
        BullfrogEntity father = fatherOf(level, pos);
        TadpoleEntity leader = null;
        for (int i = 0; i < count; i++) {
            TadpoleEntity young = type.create(level);
            if (young == null) {
                break;
            }
            young.moveTo(pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    random.nextFloat() * 360, 0);
            young.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.BREEDING,
                    leader == null ? null : new AbstractSchoolingFish.SchoolSpawnGroupData(leader));
            young.setAge(0);
            young.setFather(father == null ? null : father.getUUID());
            level.addFreshEntity(young);
            if (leader == null) {
                leader = young;
            }
        }
        toShells(level, pos, state);
    }

    /** The male guarding these eggs, if he is still near. */
    @Nullable
    static BullfrogEntity fatherOf(ServerLevel level, BlockPos pos) {
        for (BullfrogEntity frog : level.getEntitiesOfClass(BullfrogEntity.class, new AABB(pos).inflate(FATHER_WITHIN), f -> f.isAlive() && f.guards(pos))) {
            return frog;
        }
        return null;
    }

    /**
     * Whether eggs may be laid here: the top of still, shallow water (a plain water source with air over it and the bed
     * at most 2 blocks down).
     */
    public static boolean canLayAt(LevelReader level, BlockPos at) {
        BlockState there = level.getBlockState(at);
        if (!there.is(Blocks.WATER) || !there.getFluidState().isSource() || !level.getBlockState(at.above()).isAir()) {
            return false;
        }
        return !(level.getFluidState(at.below()).is(FluidTags.WATER) && level.getFluidState(at.below(2)).is(FluidTags.WATER));
    }

    /** Lays {@code eggs} of this kind here. False if it is no place to lay ({@link #canLayAt}). */
    public static boolean lay(ServerLevel level, BlockPos at, EntityType<?> kind, int eggs) {
        if (!canLayAt(level, at)) {
            return false;
        }
        level.setBlock(at, SavannaBlocks.FROG_EGGS.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof EggClutchBlockEntity clutch) {
            clutch.lay(kind, eggs, level.getGameTime());
            return true;
        }
        return false;
    }
}
