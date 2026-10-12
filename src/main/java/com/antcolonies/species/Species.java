package com.antcolonies.species;

import com.antcolonies.AntColoniesMod;

import net.minecraft.resources.ResourceLocation;

/** The species ids. Each one has a profile in {@code data/antcolonies/species/<name>.json}. */
public final class Species {
    public static final ResourceLocation RED_HARVESTER_ANT = ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "red_harvester_ant");
    public static final ResourceLocation MACROTERMES = ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "macrotermes");

    private Species() {
    }
}
