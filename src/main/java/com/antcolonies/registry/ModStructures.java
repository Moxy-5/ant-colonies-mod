package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.worldgen.GiantTreePiece;
import com.antcolonies.worldgen.GiantTreeStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's structures: the giant savanna trees in new land (M7.5 step 3). */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, AntColoniesMod.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, AntColoniesMod.MODID);

    /** A giant savanna tree; placed in savannas by data/antcolonies/worldgen/structure/ and structure_set/. */
    public static final DeferredHolder<StructureType<?>, StructureType<GiantTreeStructure>> GIANT_TREE =
            STRUCTURE_TYPES.register("giant_tree", () -> () -> GiantTreeStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GIANT_TREE_PIECE =
            STRUCTURE_PIECES.register("giant_tree", () -> (StructurePieceType.ContextlessType) GiantTreePiece::new);

    private ModStructures() {
    }
}
