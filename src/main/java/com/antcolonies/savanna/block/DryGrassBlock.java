package com.antcolonies.block;

import com.antcolonies.registry.SavannaBlocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tuft of Dry Grass, the savanna grass Macrotermes live on (like real red oat grass and thatching grass). It grows up
 * {@link Growth#GREEN}, starts {@link Growth#DRYING} from the tips, and ends up {@link Growth#DRY} (straw coloured),
 * where it stays until something eats it. Termites only take it once it is dry: real Macrotermes eat dead plant
 * material, not living grass. Each step takes about a day and a half on average (random ticks), so a green tuft is dry
 * after about three in-game days (my pick). Later, seasons (Serene Seasons) can make whole areas dry out together.
 * Placed by hand it is dry (ready for the termites); world generation puts down all three. Bone meal makes it grow into
 * {@link TallDryGrassBlock}. Dry grass burns well, green grass less so.
 */
public class DryGrassBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<DryGrassBlock> CODEC = simpleCodec(DryGrassBlock::new);

    /** How far a tuft has dried. */
    public enum Growth implements StringRepresentable {
        GREEN("green"), DRYING("drying"), DRY("dry");

        private final String name;

        Growth(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The next step (dry stays dry). */
        public Growth next() {
            return this == DRY ? DRY : values()[ordinal() + 1];
        }
    }

    public static final EnumProperty<Growth> GROWTH = EnumProperty.create("growth", Growth.class);

    /**
     * Out of how many random ticks one dries the grass a step (a block gets a random tick about every 68 seconds, so 27
     * is about 30 minutes, a day and a half). My pick.
     */
    static final int DRY_CHANCE = 27;

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public DryGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GROWTH, Growth.DRY));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(GROWTH) != Growth.DRY;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(DRY_CHANCE) == 0) {
            level.setBlock(pos, state.setValue(GROWTH, state.getValue(GROWTH).next()), 3);
        }
    }

    // Bone meal makes a tuft grow tall (as dry or green as it was), if there is room above it.

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockState tall = SavannaBlocks.TALL_DRY_GRASS.get().defaultBlockState().setValue(GROWTH, state.getValue(GROWTH));
        if (tall.canSurvive(level, pos) && level.isEmptyBlock(pos.above())) {
            DoublePlantBlock.placeAt(level, tall, pos, 2);
        }
    }

    // Fire: dry grass catches and spreads fire like vanilla grass, green grass half as well.

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability(state);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSpread(state);
    }

    /** How easily this grass burns away (vanilla grass: 100). */
    static int flammability(BlockState state) {
        return state.getValue(GROWTH) == Growth.GREEN ? 50 : 100;
    }

    /** How easily this grass catches fire (vanilla grass: 60). */
    static int fireSpread(BlockState state) {
        return state.getValue(GROWTH) == Growth.GREEN ? 30 : 60;
    }
}
