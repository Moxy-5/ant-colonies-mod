package com.antcolonies.savanna.entity;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.load.CarriedLoad;
import com.antcolonies.entity.load.LoadKind;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * The Matabele ants' own loads (Project Apex T3: a new load is a row in its species mod). M8 step 1e: termite leftovers
 * on their way out to the heap by the entrance ({@code CarryRemainsGoal}); a carrier that dies holding them leaves the
 * job to the numbers, its layer laid on the heap out of sight. M8 step 3c: a bundle of paralysed termites on its way
 * home from a raid ({@code colony.RaidColumn}, {@code BringPreyGoal}), held as that many prey items; a carrier that dies
 * holding them drops them: they lie stung where it fell (the raid code review, finding 18; before, they vanished). Drawn in
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
            MatabeleLoads::dropTermites));

    /**
     * The raid code review, finding 18: a carrier dying on the way home drops the stung termites it held where it died,
     * as real Megaponera's prey falls from a killed raider's jaws: each lies there paralysed (a termite worker of no
     * colony; the bundle does not say which kind, and raids take the workers first, Frank 2017), to be picked up by
     * another raider or to die of the venom in a day.
     */
    private static void dropTermites(ServerLevel level, com.antcolonies.colony.Colony colony, AbstractAntEntity carrier, CarriedLoad load) {
        int count = load.item().getCount();
        for (int i = 0; i < count; i++) {
            TermiteWorkerEntity termite = com.antcolonies.savanna.registry.SavannaEntities.TERMITE_WORKER.get().create(level);
            if (termite == null) {
                return;
            }
            double angle = i * 2.39996; // (the golden angle: spread a little round where it fell)
            double radius = count == 1 ? 0.0 : 0.3 + 0.15 * i;
            termite.moveTo(carrier.getX() + Math.cos(angle) * radius, carrier.getY(), carrier.getZ() + Math.sin(angle) * radius,
                    carrier.getRandom().nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(termite);
            termite.fallParalysedNow();
        }
        com.antcolonies.savanna.SavannaMod.LOGGER.info("[raid] a carrier died at {}: its {} stung termite(s) fall there",
                carrier.blockPosition().toShortString(), count);
    }

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
