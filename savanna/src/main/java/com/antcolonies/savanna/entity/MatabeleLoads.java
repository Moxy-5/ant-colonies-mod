package com.antcolonies.savanna.entity;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.entity.load.LoadKind;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The Matabele ants' own loads (Project Apex T3: a new load is a row in its species mod). M8 step 1e: termite leftovers
 * on their way out to the heap by the entrance ({@code CarryRemainsGoal}); a carrier that dies holding them leaves the
 * job to the numbers, its layer laid on the heap out of sight. M8 step 3c: a bundle of paralysed termites on its way
 * home from a raid ({@code colony.RaidColumn}, {@code BringPreyGoal}), held as that many prey items; a carrier that dies
 * holding them loses them (they lie stung where it fell, prey for nobody: my pick). Drawn in
 * {@code client.SavannaClientEvents}.
 */
public final class MatabeleLoads {
    public static final LoadKind TERMITE_REMAINS = LoadKind.signUp(LoadKind.flag(
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termite_remains"), "CarryingTermiteRemains",
            (level, colony, carrier, load) -> colony.part(SavannaParts.PREY_PILE).ifPresent(PreyPile::loadDropped)));

    /** Saved as {@code CarryingTermites: n}. */
    public static final LoadKind TERMITES = LoadKind.signUp(new LoadKind(
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "termites"),
            (tag, load, registries) -> tag.putInt("CarryingTermites", load.item().getCount()),
            (tag, registries) -> tag.getInt("CarryingTermites") > 0 ? termites(tag.getInt("CarryingTermites")) : null,
            (level, colony, carrier, load) -> { }));

    /** A bundle of this many paralysed termites. */
    public static CarriedLoad termites(int count) {
        return CarriedLoad.of(TERMITES, new ItemStack(SavannaItems.PARALYSED_TERMITE.get(), Math.max(1, count)));
    }

    /** How many termites this insect holds in its jaws (0: none). */
    public static int termitesHeldBy(AbstractAntEntity ant) {
        return ant.isCarrying(TERMITES) ? ant.getLoad().item().getCount() : 0;
    }

    private MatabeleLoads() {
    }

    /** Signs the rows above up (called once at start, before any save is read; the first use of the class does it). */
    public static void signUp() {
    }
}
