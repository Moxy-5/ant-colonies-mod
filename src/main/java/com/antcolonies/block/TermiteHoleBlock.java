package com.antcolonies.block;

import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The termites' hole block, a chimney crown (user's design): a block of mound soil with a crown of hollow little
 * spires on top. It sits at the top of the tower, the top of each side chimney and the entrances. It is solid for the
 * player and every other creature, but the insects listed in the entity tag {@code antcolonies:passes_termite_holes}
 * walk straight through it (they vanish inside, as if going down the hole). Species-neutral: another species can get
 * its own hole block and tag. (The route planner learns to use it as a doorway when the termites build the mound.)
 */
public class TermiteHoleBlock extends Block {
    public TermiteHoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null
                && entityContext.getEntity().getType().is(ModTags.PASSES_TERMITE_HOLES)) {
            return Shapes.empty();
        }
        return Shapes.block();
    }
}
