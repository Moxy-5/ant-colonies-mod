package com.antcolonies.savanna.block;

import com.antcolonies.nav.Doorway;
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
 * its own hole block and tag. The route planner uses it as a doorway for them, and they come out of the tall spire in
 * the middle of the crown and go back in by it (see {@link Doorway}).
 */
public class TermiteHoleBlock extends Block implements Doorway {
    /** The middle spire of the crown (the block model): its rim is 12 pixels above the block, and it is 4 pixels wide. */
    private static final double SPIRE_TOP = 28.0 / 16.0;
    private static final double SPIRE_HALF_WIDTH = 2.0 / 16.0;
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

    @Override
    public double mouthHeight() {
        return SPIRE_TOP;
    }

    @Override
    public double mouthRadius() {
        return SPIRE_HALF_WIDTH;
    }
}
