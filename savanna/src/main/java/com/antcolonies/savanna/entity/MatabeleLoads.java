package com.antcolonies.savanna.entity;

import com.antcolonies.entity.load.LoadKind;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;

import net.minecraft.resources.ResourceLocation;

/**
 * The Matabele ants' own loads (Project Apex T3: a new load is a row in its species mod). M8 step 1e: termite leftovers
 * on their way out to the heap by the entrance ({@code CarryRemainsGoal}); a carrier that dies holding them leaves the
 * job to the numbers, its layer laid on the heap out of sight. Drawn in {@code client.SavannaClientEvents}.
 */
public final class MatabeleLoads {
    public static final LoadKind TERMITE_REMAINS = LoadKind.signUp(LoadKind.flag(
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_remains"), "CarryingTermiteRemains",
            (level, colony, carrier, load) -> colony.part(SavannaParts.PREY_PILE).ifPresent(PreyPile::loadDropped)));

    private MatabeleLoads() {
    }

    /** Signs the rows above up (called once at start, before any save is read; the first use of the class does it). */
    public static void signUp() {
    }
}
