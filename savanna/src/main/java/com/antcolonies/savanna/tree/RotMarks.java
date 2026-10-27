package com.antcolonies.savanna.tree;

import com.antcolonies.savanna.registry.TreeBlocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Heart rot marks on a trunk's bark (dead wood for the raids, part 4, the looks picked by the user 2026-10-26): a log
 * may be spore-dusted (under a grown bracket) or the wound (where the rot got in). A marked log is the same wood as its
 * plain log for everything else: the tree's own block when it grows, dies, burns or falls ({@code TreePlacer.same}),
 * and when its wood changes (a living log turning bleached as the tree dies, or charred in a fire) the mark goes with it.
 */
public final class RotMarks {
    public enum Mark { SPORES, WOUND }

    private RotMarks() {
    }

    /** The plain log a marked one marks; the block itself if it is not marked. */
    public static Block base(Block b) {
        for (TreeBlocks.RotBark r : TreeBlocks.ROT_BARKS) {
            if (b == r.spores().get() || b == r.wound().get()) {
                return r.base().get();
            }
        }
        return b;
    }

    /** The mark on this block, or null. */
    public static Mark mark(Block b) {
        for (TreeBlocks.RotBark r : TreeBlocks.ROT_BARKS) {
            if (b == r.spores().get()) {
                return Mark.SPORES;
            }
            if (b == r.wound().get()) {
                return Mark.WOUND;
            }
        }
        return null;
    }

    /** This plain log with a mark; null if that log takes no marks. */
    public static Block marked(Block base, Mark mark) {
        for (TreeBlocks.RotBark r : TreeBlocks.ROT_BARKS) {
            if (base == r.base().get()) {
                return mark == Mark.SPORES ? r.spores().get() : r.wound().get();
            }
        }
        return null;
    }

    /** A plain log state given a mark (its axis and other properties kept); null if it is marked already or takes none. */
    public static BlockState marked(BlockState plain, Mark mark) {
        if (mark(plain.getBlock()) != null) {
            return null;
        }
        Block m = marked(plain.getBlock(), mark);
        return m == null ? null : m.withPropertiesOf(plain);
    }

    /** {@code state}, the tree's new block where {@code there} stands: with {@code there}'s mark on it if it takes one. */
    public static BlockState keep(BlockState there, BlockState state) {
        Mark mark = mark(there.getBlock());
        if (mark == null) {
            return state;
        }
        Block m = marked(base(state.getBlock()), mark);
        return m == null ? state : m.withPropertiesOf(state);
    }
}
