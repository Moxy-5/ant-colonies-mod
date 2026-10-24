package com.antcolonies.savanna;

import net.minecraft.resources.ResourceLocation;

/** The savanna's species ids. Each one has a profile in {@code data/antcolonies/species/<name>.json}. */
public final class SavannaSpecies {
    public static final ResourceLocation MACROTERMES = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "macrotermes");
    /** The Matabele ant (Megaponera analis, M8): in the game since 2026-10-23, its species file still to come with M8. */
    public static final ResourceLocation MATABELE = ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "megaponera_analis");

    private SavannaSpecies() {
    }
}
