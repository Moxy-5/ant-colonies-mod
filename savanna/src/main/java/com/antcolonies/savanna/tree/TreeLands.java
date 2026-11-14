package com.antcolonies.savanna.tree;

import com.antcolonies.savanna.worldgen.SavannaRegion;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Where each kind of tree may stand: the one place this is decided (floodplain trees, step 2, 2026-11-13,
 * {@code docs/plans/floodplain-trees.md}). The dry savanna trees (umbrella thorn, camel thorn, baobab) never stand on the
 * floodplain's greener ground (the user: "dry savanna trees no longer can grow on the greener dirt to make room for the new
 * trees"); the floodplain's own trees (the sycamore fig, {@code fig}, and the doum palm, {@code doum}; new land since step 7,
 * {@code worldgen/FloodplainTreeStructure}) stand only there. Real: the
 * acacias and the baobab are trees of the dry, well-drained savanna; by the rivers the wet ground belongs to the riverine
 * trees. Asked by new land's trees ({@code worldgen/GiantTreeStructure}), the seedlings after rain ({@link Forest#sproutIn})
 * and the trees new land puts on and by termite mounds ({@code colony/NewLandMounds}). A seedling a player plants grows
 * wherever it is put (my pick).
 */
public final class TreeLands {
    private TreeLands() {
    }

    /** Whether a tree of this kind may stand (come up, be put in new land) on ground of this biome. */
    public static boolean mayStand(String kind, Holder<Biome> biome) {
        boolean floodplain = biome.is(SavannaRegion.SAVANNA_FLOODPLAIN);
        return kind.equals("fig") || kind.equals("doum") ? floodplain : !floodplain;
    }
}
