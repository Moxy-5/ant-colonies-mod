package com.antcolonies.savanna.block;

import com.antcolonies.block.GardenBed;
import com.antcolonies.block.SeedPileBlock;
import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.Slope;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Fungus comb: the sponge-like mat termites build in their garden rooms out of the forage they bring in (one piece = one
 * layer, two pixels high, at most {@link #MAX_COMB_LAYERS}), on which their fungus grows. White fungus nodules sprout on
 * top of it ({@link #NODULES}, up to {@link #MAX_NODULES}). The nurses pick the nodules to feed the queen and the
 * nymphs. Unlike the seed and bread heaps it is solid: termites walk on it (a flat {@link Slope} for their legs) and the
 * eggs and nymphs lie on top of it.
 *
 * <p>It ages like real Macrotermes comb, a conveyor belt (the user's request, 2026-10-15): each layer is fresh, ripe or
 * old, drawn in bands, so a thick comb is dark on top and pale underneath. From the bottom up: {@link #OLD} old layers
 * (pale, dry, full of holes: the fungus has used them up, the old workers eat them), then the ripe ones (mottled with
 * white fungus threads; the nodules grow from them), then {@link #FRESH} fresh ones on top (dark, damp pellets of
 * chewed plant matter, just laid). New comb goes on top; it is eaten (or crumbles) from the bottom (see
 * {@code colony.FungusGarden}).
 */
public class FungusCombBlock extends SeedPileBlock implements Slope, GardenBed {
    /** The comb is at most this many layers high (half a block), so a termite still fits on it in a room one block tall. */
    public static final int MAX_COMB_LAYERS = 4;
    public static final int MAX_NODULES = 3;
    public static final IntegerProperty NODULES = IntegerProperty.create("nodules", 0, MAX_NODULES);
    /** How many of its layers, from the bottom, are old. */
    public static final IntegerProperty OLD = IntegerProperty.create("old", 0, MAX_COMB_LAYERS);
    /** How many of its layers, from the top, are fresh. */
    public static final IntegerProperty FRESH = IntegerProperty.create("fresh", 0, MAX_COMB_LAYERS);

    public FungusCombBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(NODULES, 0).setValue(OLD, 0).setValue(FRESH, 0));
    }

    @Override
    public int maxLayers() {
        return MAX_COMB_LAYERS;
    }

    // --- as a heap in the food store (see SeedPileBlock) ------------------------------------------------------------

    @Override
    public boolean ages() {
        return true; // built onto comb with room first: the fungus only grows where the comb is thick enough
    }

    @Override
    public BlockState newHeap() {
        return newComb(defaultBlockState());
    }

    @Override
    public BlockState oneLayerMore(BlockState state) {
        return withLayerOnTop(state); // the new layer goes on top, fresh
    }

    @Override
    public BlockState oneLayerLess(BlockState state) {
        BlockState less = withoutBottomLayer(state); // off its bottom, an old one first
        return less == null ? Blocks.AIR.defaultBlockState() : less;
    }

    @Override
    public boolean hasLayerToTake(BlockState state) {
        return old(state) > 0; // the old workers eat old comb, from the bottom
    }

    @Override
    public int growthOn(BlockState state) {
        return state.getValue(NODULES);
    }

    @Override
    public void laid(ServerLevel level, BlockPos spot) {
        // Fresh comb laid: a soft wet squelch and a few dark specks.
        level.playSound(null, spot, net.minecraft.sounds.SoundEvents.MUD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.25F, 1.6F);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                level.getBlockState(spot)), spot.getX() + 0.5, spot.getY() + topOf(level.getBlockState(spot)),
                spot.getZ() + 0.5, 4, 0.2, 0.02, 0.2, 0.0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NODULES, OLD, FRESH);
    }

    /** How high (in blocks) the top of the comb is over the floor: where an egg or a nymph lies on it. */
    public static double topOf(BlockState state) {
        return state.getValue(LAYERS) * 2.0 / 16.0;
    }

    @Override
    public double restingHeight(BlockState state) {
        return topOf(state);
    }

    @Override
    public int surfaceHeight(BlockState state, int u, int w) {
        return state.getValue(LAYERS) * 2;
    }

    // --- the layers' ages -----------------------------------------------------------------------------------------

    public static int layers(BlockState state) {
        return state.getValue(LAYERS);
    }

    /** Old layers (at most all of them). */
    public static int old(BlockState state) {
        return Math.min(state.getValue(OLD), layers(state));
    }

    /** Fresh layers (at most the ones over the old). */
    public static int fresh(BlockState state) {
        return Math.min(state.getValue(FRESH), layers(state) - old(state));
    }

    /** Ripe layers: between the old and the fresh. */
    public static int ripe(BlockState state) {
        return layers(state) - old(state) - fresh(state);
    }

    private static BlockState with(BlockState state, int layers, int old, int fresh) {
        old = Math.max(0, Math.min(old, Math.min(layers, MAX_COMB_LAYERS)));
        fresh = Math.max(0, Math.min(fresh, Math.min(layers - old, MAX_COMB_LAYERS)));
        return state.setValue(LAYERS, layers).setValue(OLD, old).setValue(FRESH, fresh);
    }

    /** A new comb: one fresh layer. */
    public static BlockState newComb(BlockState defaultState) {
        return with(defaultState, 1, 0, 1);
    }

    /** One more fresh layer on top (call only with room for it). */
    public static BlockState withLayerOnTop(BlockState state) {
        return with(state, layers(state) + 1, old(state), fresh(state) + 1);
    }

    /** The bottom layer gone (eaten or crumbled: an old one if it has one), or null if it was the last. */
    @Nullable
    public static BlockState withoutBottomLayer(BlockState state) {
        int layers = layers(state);
        if (layers <= 1) {
            return null;
        }
        int old = old(state);
        int fresh = fresh(state);
        if (old > 0) {
            return with(state, layers - 1, old - 1, fresh);
        }
        return ripe(state) > 0 ? with(state, layers - 1, 0, fresh) : with(state, layers - 1, 0, fresh - 1);
    }

    /** The lowest fresh layer has ripened (call only with a fresh layer). */
    public static BlockState ripenOne(BlockState state) {
        return with(state, layers(state), old(state), fresh(state) - 1);
    }

    /** The lowest ripe layer has gone old (call only with a ripe layer). */
    public static BlockState ageOne(BlockState state) {
        return with(state, layers(state), old(state) + 1, fresh(state));
    }
}
