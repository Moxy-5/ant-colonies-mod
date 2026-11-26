package com.antcolonies.savanna.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * River algae (M9 river life, the first link of the food chain, 2026-11-14; look C picked by the user in
 * {@code python tools/model_viewer.py algae}): a thin green mat on the river bed under water, laid over whatever the bed
 * is (silt, mud, sand, stone), with streamers standing up from it when thick (an animated texture, so they sway).
 *
 * <p>{@link #GROWTH}: 0 grazed bare (a few specks; it never goes, so a grazed patch grows back without seeding again),
 * 1 thin, 2 thick, 3 lush. It grows on random ticks (about one stage a game day, my pick), only as far as the water's
 * depth lets it ({@link #mostFor}: lush only in shallow water, thin at most in the deep channel, where little light
 * reaches), and a thick mat spreads to bare bed beside it.
 *
 * <p>{@link #STRANDS}: the streamers left standing on each of the two crossed planes a thick or lush mat is drawn with
 * (up to 5 short ones on a thick mat, 7 tall ones on a lush mat; none on a thin or bare one). A grazing fish's bite
 * ({@link #graze}) takes 2 strands, one off each plane (the user's ask, 2026-11-15), and once the strands are gone the
 * next bite tears the mat off down to the bare bed. A thick mat regrows its strands one pair at a time before it
 * thickens to lush. Like seagrass it always holds water: broken, it leaves water.
 */
public class AlgaeBlock extends BushBlock implements LiquidBlockContainer, BonemealableBlock {
    public static final MapCodec<AlgaeBlock> CODEC = simpleCodec(AlgaeBlock::new);
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 3);
    public static final int BARE = 0, THIN = 1, THICK = 2, LUSH = 3;
    /** Strand pairs standing (one strand on each crossed plane): see {@link #fullStrands}. */
    public static final IntegerProperty STRANDS = IntegerProperty.create("strands", 0, 7);
    /** The chance a random tick grows the mat a stage (about one stage a game day: a block gets ~17 random ticks a day). */
    static final float GROW_CHANCE = 1 / 14f;
    /**
     * The chance a random tick of a thick or lush mat grows back a pair of strands: about 6 pairs a game day, so a bitten
     * tuft is whole again in about a day (my pick, 2026-11-15).
     */
    static final float STRAND_CHANCE = 1 / 3f;
    /** The chance a random tick of a thick or lush mat seeds bare bed beside it. */
    static final float SPREAD_CHANCE = 1 / 10f;
    /** How deep the water is counted (deeper is all the same: the channel). */
    static final int DEEPEST = 8;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public AlgaeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GROWTH, THIN).setValue(STRANDS, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GROWTH, STRANDS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(Blocks.MAGMA_BLOCK);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState water = context.getLevel().getFluidState(context.getClickedPos());
        return water.is(FluidTags.WATER) && water.isSource() ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        BlockState after = super.updateShape(state, facing, facingState, level, pos, facingPos);
        if (!after.isAir()) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return after;
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

    /** How many blocks of water stand here, this cell counted (up to {@link #DEEPEST}). */
    public static int depth(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos at = pos.mutable();
        int d = 0;
        while (d < DEEPEST && level.getFluidState(at).is(FluidTags.WATER)) {
            d++;
            at.move(Direction.UP);
        }
        return d;
    }

    /** The thickest a mat grows in water this deep: lush in 1 or 2 blocks, thick in 3 or 4, thin deeper (my picks). */
    public static int mostFor(int depth) {
        return depth <= 2 ? LUSH : depth <= 4 ? THICK : THIN;
    }

    /** How many strand pairs a whole mat of this stage stands: 5 thick, 7 lush, none thin or bare (as the pictures draw). */
    public static int fullStrands(int growth) {
        return growth == LUSH ? 7 : growth == THICK ? 5 : 0;
    }

    /** A mat of this stage with all its strands standing. */
    public BlockState whole(int growth) {
        return defaultBlockState().setValue(GROWTH, growth).setValue(STRANDS, fullStrands(growth));
    }

    /**
     * Bites a fish can still take here: one per strand pair, then one for the mat (none when bare); a mat with catfish
     * eggs on it ({@link CatfishEggsBlock}, animal life cycle step 3) one more for its eggs.
     */
    public static int bitesLeft(BlockState state) {
        boolean eggs = state.getBlock() instanceof CatfishEggsBlock;
        if (!(state.getBlock() instanceof AlgaeBlock || eggs)) {
            return 0;
        }
        int growth = state.getValue(GROWTH);
        int mat = growth == BARE ? 0 : Math.min(state.getValue(STRANDS), fullStrands(growth)) + 1;
        return mat + (eggs ? 1 : 0);
    }

    /** Whether there are catfish eggs on the mat here. */
    public static boolean hasEggs(BlockState state) {
        return state.getBlock() instanceof CatfishEggsBlock;
    }

    /** The mat one step thicker: a thick or lush mat missing strands grows a pair back first, else it thickens a stage. */
    private static BlockState grown(BlockState state) {
        int growth = state.getValue(GROWTH), strands = state.getValue(STRANDS);
        if (strands < fullStrands(growth)) {
            return state.setValue(STRANDS, strands + 1);
        }
        return state.setValue(GROWTH, growth + 1);       // thick to lush keeps its strands; the rest grow back after
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int growth = state.getValue(GROWTH);
        if (state.getValue(STRANDS) < fullStrands(growth)) {
            if (random.nextFloat() < STRAND_CHANCE) {
                level.setBlock(pos, grown(state), Block.UPDATE_CLIENTS);
            }
        } else if (growth < mostFor(depth(level, pos)) && random.nextFloat() < GROW_CHANCE) {
            level.setBlock(pos, grown(state), Block.UPDATE_CLIENTS);
        }
        if (growth >= THICK && random.nextFloat() < SPREAD_CHANCE) {
            spread(level, pos, random);
        }
    }

    /** How many mats may lie in the 5 by 5 round a spot (its own level, one up and one down) for a new one to be seeded there (my pick). */
    static final int PATCH_MOST = 8;

    /**
     * Whether a mat may be seeded here (the user's call, 2026-11-26, review R10-5: algae crept out of the savanna's
     * rivers into any shallow water joined to them, and filled every bed): only in the savanna's own waters, and only
     * while the bed round the spot is still patchy, as new land lays it.
     */
    static boolean roomToSpread(ServerLevel level, BlockPos to) {
        if (!level.getBiome(to).is(com.antcolonies.savanna.worldgen.RiverBankFeature.WATERS)) {
            return false;
        }
        int mats = 0;
        for (BlockPos near : BlockPos.betweenClosed(to.offset(-2, -1, -2), to.offset(2, 1, 2))) {
            if (level.isLoaded(near) && level.getBlockState(near).getBlock() instanceof AlgaeBlock && ++mats > PATCH_MOST) {
                return false;
            }
        }
        return true;
    }

    /** Seeds a bare mat on the bed beside {@code pos} (one cell, the bed up to a block higher or lower), if there is room. */
    static void spread(ServerLevel level, BlockPos pos, RandomSource random) {
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos to = pos.relative(side).above(random.nextInt(3) - 1);
        if (!level.isLoaded(to)) {
            return;
        }
        BlockState there = level.getBlockState(to);
        BlockState bare = level.getBlockState(pos).getBlock().defaultBlockState().setValue(GROWTH, BARE).setValue(STRANDS, 0);
        if (there.is(Blocks.WATER) && there.getFluidState().isSource() && bare.canSurvive(level, to) && roomToSpread(level, to)) {
            level.setBlock(to, bare, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    /**
     * A fish's bite: 2 strands off (one off each crossed plane), with a few torn bits; or, with no strands left, the mat
     * torn off down to the bare bed, with a puff of algae bits and a few bubbles. Whether there was anything to eat (a
     * bare mat has nothing; its specks stay, so the patch grows back without seeding again).
     */
    public static boolean graze(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (bitesLeft(state) == 0) {
            return false;
        }
        if (hasEggs(state)) {
            // catfish eggs on the mat (animal life cycle step 3): the bite takes a strand with a few eggs stuck to it,
            // with a puff of egg bits; once the eggs are gone the mat is as it was
            int strands = Math.min(state.getValue(STRANDS), fullStrands(state.getValue(GROWTH)));
            if (strands > 0) {
                level.setBlock(pos, state.setValue(STRANDS, strands - 1), Block.UPDATE_CLIENTS);
            }
            if (level instanceof ServerLevel server) {
                eggPuff(server, pos);
                com.antcolonies.animal.EggClutchBlock.eat(server, pos, EGGS_A_BITE);
            }
            return true;
        }
        int strands = Math.min(state.getValue(STRANDS), fullStrands(state.getValue(GROWTH)));
        boolean mat = strands == 0;
        level.setBlock(pos, mat ? state.setValue(GROWTH, BARE).setValue(STRANDS, 0) : state.setValue(STRANDS, strands - 1), Block.UPDATE_CLIENTS);
        if (level instanceof ServerLevel server) {
            double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
            if (mat) {
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        x, pos.getY() + 0.15, z, 6 + 4 * state.getValue(GROWTH), 0.3, 0.1, 0.3, 0.05);
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, x, pos.getY() + 0.35, z, 4, 0.25, 0.1, 0.25, 0.02);
            } else {
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        x, pos.getY() + 0.5, z, 3, 0.2, 0.25, 0.2, 0.03);
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, x, pos.getY() + 0.5, z, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
        return true;
    }

    /** Eggs a grazer's bite takes off a mat (my pick: a clutch of 2 or 3 eggs lasts a fish about two bites). */
    static final int EGGS_A_BITE = 2;
    /** The catfish eggs' colour, pale yellow (the life viewer's), for the puff when they are eaten. */
    private static final org.joml.Vector3f EGG_COLOUR = new org.joml.Vector3f(0.91F, 0.86F, 0.54F);

    /** A puff of egg bits where eggs are eaten (pale, a few, drifting up). */
    public static void eggPuff(ServerLevel server, BlockPos pos) {
        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(EGG_COLOUR, 0.6F),
                pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 6, 0.2, 0.08, 0.2, 0.01);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5,
                2, 0.2, 0.1, 0.2, 0.02);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(GROWTH) < LUSH || state.getValue(STRANDS) < fullStrands(LUSH);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, grown(state), Block.UPDATE_CLIENTS);
    }
}
