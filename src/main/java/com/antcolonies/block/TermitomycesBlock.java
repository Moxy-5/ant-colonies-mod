package com.antcolonies.block;

import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModItems;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Termitomyces mushroom, the termites' own fungus fruiting above ground: after a rain it comes up round a termite
 * mound whose fungus garden is healthy (see {@code colony.Mushrooms}). It goes through {@link #STAGE}s on random ticks:
 * a small white {@link #BUTTON} pushing out of the soil, a young mushroom with a {@link #CLOSED} pointed cap, the cap
 * {@link #OPEN} (pale, with a dark brown point in the middle), and after about two days {@link #WILTED} (brown and
 * drooping), until it is gone. A button marked {@link #GIANT} grows into a {@link GiantTermitomycesBlock} instead, if
 * there is room above it. Players pick it (it breaks at once) for food; a wilted one gives nothing. Nothing collides with
 * it and anything placed into its cell replaces it, like grass (termites building there simply build over it).
 */
public class TermitomycesBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<TermitomycesBlock> CODEC = simpleCodec(TermitomycesBlock::new);

    public static final int BUTTON = 0;
    public static final int CLOSED = 1;
    public static final int OPEN = 2;
    public static final int WILTED = 3;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", BUTTON, WILTED);
    /** A button that grows into a giant mushroom. */
    public static final BooleanProperty GIANT = BooleanProperty.create("giant");

    /**
     * Out of how many random ticks one moves the mushroom on from each stage (a block gets a random tick about every 68
     * seconds): button and closed cap about 3 to 4 minutes each, open about 45 minutes (2 days), wilted about 18 minutes
     * (a day) before it is gone. My picks.
     */
    private static final int[] STAGE_CHANCE = {3, 3, 40, 16};

    private static final VoxelShape[] SHAPES = {
            Block.box(5.0, 0.0, 5.0, 11.0, 4.0, 11.0),
            Block.box(5.0, 0.0, 5.0, 11.0, 13.0, 11.0),
            Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0),
            Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0)};

    public TermitomycesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, BUTTON).setValue(GIANT, false));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, GIANT);
    }

    /** The ground a Termitomyces comes up on: earth (grass, dirt, coarse dirt...) or finished termite mound soil. */
    public static boolean isGround(BlockState ground) {
        return ground.is(BlockTags.DIRT) || ground.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(ground);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return isGround(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPES[state.getValue(STAGE)].move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (random.nextInt(STAGE_CHANCE[stage]) == 0) {
            growOn(state, level, pos);
        }
    }

    /**
     * Moves the mushroom on to its next stage: a giant button becomes a giant mushroom if it has room, and a wilted one
     * is gone (nothing dropped).
     */
    public static void growOn(BlockState state, Level level, BlockPos pos) {
        int stage = state.getValue(STAGE);
        if (stage == BUTTON && state.getValue(GIANT) && ModBlocks.GIANT_TERMITOMYCES.get().growAt(level, pos)) {
            return;
        }
        if (stage == WILTED) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(STAGE, stage + 1), 3);
        }
    }

    // White spores drift off an open cap now and then.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == OPEN && random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.6,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, -0.01, 0.0);
        }
    }

    // Bone meal brings a button or a closed cap on to the next stage.

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(STAGE) < OPEN;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        growOn(state, level, pos);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.TERMITOMYCES.get());
    }
}
