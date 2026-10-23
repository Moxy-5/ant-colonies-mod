package com.antcolonies.savanna.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A seedling of a giant savanna tree (M7.5): a thin stem and its first leaves. One planted by a player goes into the
 * tree book ({@link com.antcolonies.savanna.tree.Forest}) and grows from there, a little every day; bone meal speeds it up.
 */
public class TreeSeedlingBlock extends BushBlock {
    public static final MapCodec<TreeSeedlingBlock> CODEC = simpleCodec(TreeSeedlingBlock::new);
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 12, 13);

    public TreeSeedlingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            for (com.antcolonies.savanna.registry.TreeBlocks.Kind kind : com.antcolonies.savanna.registry.TreeBlocks.KINDS) {
                if (kind.seedling().get() == this) {
                    com.antcolonies.savanna.tree.Forest.get(server).addSeedling(kind.name(), pos, server.getRandom().nextLong() & 0xFFFFFFFFL,
                            com.antcolonies.savanna.tree.Forest.today(server), true);
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
