package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.worldgen.GiantTreePiece;
import com.antcolonies.savanna.worldgen.GiantTreeStructure;
import com.antcolonies.savanna.worldgen.TermiteMoundPiece;
import com.antcolonies.savanna.worldgen.TermiteMoundStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna's structures: the giant savanna trees in new land (M7.5 step 3) and the termite mounds (M8 step 1b). */
public final class SavannaStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, SavannaMod.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, SavannaMod.MODID);

    /** A giant savanna tree; placed in savannas by data/antcolonies/worldgen/structure/ and structure_set/. */
    public static final DeferredHolder<StructureType<?>, StructureType<GiantTreeStructure>> GIANT_TREE =
            STRUCTURE_TYPES.register("giant_tree", () -> () -> GiantTreeStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> GIANT_TREE_PIECE =
            STRUCTURE_PIECES.register("giant_tree", () -> (StructurePieceType.ContextlessType) GiantTreePiece::new);

    /** A termite mound's place, living or dead; placed in savannas by data/antcolonies/worldgen/structure/termite_mound.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<TermiteMoundStructure>> TERMITE_MOUND =
            STRUCTURE_TYPES.register("termite_mound", () -> () -> TermiteMoundStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> TERMITE_MOUND_PIECE =
            STRUCTURE_PIECES.register("termite_mound", () -> (StructurePieceType.ContextlessType) TermiteMoundPiece::new);

    private SavannaStructures() {
    }
}
