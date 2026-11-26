package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The leaves of a giant savanna tree (M7.5): fine, see-through, flat layers of tiny leaflets. Unlike Minecraft's leaves
 * they never decay on their own: a giant crown spreads far wider than 7 blocks from its trunk, and our trees die and drop
 * their leaves by their own rules (M7.5 step 5). Like leaves they dim the light a little and let it through.
 */
public class TreeLeavesBlock extends Block {
    public static final MapCodec<TreeLeavesBlock> CODEC = simpleCodec(TreeLeavesBlock::new);

    /**
     * Scorched by a fire: a green leaf of a living tree turned brown (the user, 2026-11-24, "scorch, then regreen";
     * review R7-10). A scorched leaf does not burn away (until then the fire made its own fuel: it scorched a leaf, burnt
     * the scorched leaf and so ate a living crown) and greens again by itself over the next days. A dead tree's own
     * withered leaves are not scorched: they burn like Minecraft's.
     */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty SCORCHED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("scorched");
    /** A scorched leaf greens again on one random tick in this many: about two game days where a player is near (my pick). */
    public static final int REGREEN_ONE_IN = 36;

    public TreeLeavesBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SCORCHED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SCORCHED);
    }

    /** A scorched leaf feeds a fire beside it no more than a green one and never burns away. */
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(SCORCHED) ? 0 : super.getFlammability(state, level, pos, direction);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(SCORCHED) ? 30 : super.getFireSpreadSpeed(state, level, pos, direction);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(SCORCHED);
    }

    /** A scorched leaf greens again, unless a fire still burns beside it. */
    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (random.nextInt(REGREEN_ONE_IN) == 0) {
            regreen(state, level, pos);
        }
    }

    /** Turns a scorched leaf back into its kind's green leaf (not beside a fire). True if it did. Public for the tests. */
    public static boolean regreen(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (!(state.getBlock() instanceof TreeLeavesBlock) || !state.getValue(SCORCHED)) {
            return false;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                return false;
            }
        }
        for (com.antcolonies.savanna.registry.TreeBlocks.Kind k : com.antcolonies.savanna.registry.TreeBlocks.KINDS) {
            if (state.is(k.deadLeaves().get())) {
                level.setBlock(pos, k.leaves().get().defaultBlockState(), Block.UPDATE_CLIENTS);
                return true;
            }
        }
        return false;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }
}
