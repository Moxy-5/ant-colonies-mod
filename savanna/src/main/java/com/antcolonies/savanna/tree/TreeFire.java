package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.debug.Perf;
import com.antcolonies.savanna.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Step 5d, fire on the giant trees (the user's pick, "real: char, not vanish"). A giant's blocks no longer burn away
 * ({@code TreeBlocks.setFlammable}); instead, the moment a fire appears next to them, its bark chars black and green
 * leaves scorch brown. Real: savanna acacias have thick bark; a grass fire blackens their foot and browns the lower
 * crown, and most live. The tree hit is told ({@link Forest#inFire}): young trees die, grown ones may.
 */
public final class TreeFire {
    private TreeFire() {
    }

    /** A block has just changed at {@code pos}: if it is a fire, it chars and scorches the giant tree blocks round it. */
    public static void changed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BaseFireBlock)) {
            return;
        }
        List<BlockPos> hit = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = pos.relative(d);
            BlockState there = level.getBlockState(p);
            Block to = TreePlacer.charredOf(there.getBlock());
            if (to == null) {
                to = TreePlacer.scorchedOf(there.getBlock());
            }
            if (to == null && !TreePlacer.isCharred(there)) {
                continue;
            }
            if (to != null) {
                level.setBlock(p, to.withPropertiesOf(there), Block.UPDATE_CLIENTS);
            }
            hit.add(p.immutable());
        }
        if (!hit.isEmpty()) {
            boolean timed = Perf.begin(Perf.Part.TREES);
            try {
                Forest.get(level).inFire(level, hit);
            } finally {
                if (timed) {
                    Perf.end();
                }
            }
        }
    }
}
