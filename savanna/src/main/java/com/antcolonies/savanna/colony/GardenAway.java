package com.antcolonies.savanna.colony;

import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.NestLedger;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.species.SpeciesProfile;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.server.level.ServerLevel;

/**
 * The fungus garden while the colony is away ({@link NestLife}, once a second after the nurses' work; Project Apex step
 * 8c, moved out of the shared code unchanged), plugged in by the {@link FungusGarden}.
 */
final class GardenAway {
    private final FungusGarden garden;
    private final Colony colony;
    /** Nodule growth not come yet (a fraction of one carried over). Not saved. */
    private double noduleGrowth;
    /** The comb's ageing while away not come yet (fractions carried over): ripening, going old, crumbling. Not saved. */
    private double ripening;
    private double ageing;
    private double crumbling;

    GardenAway(FungusGarden garden) {
        this.garden = garden;
        this.colony = garden.colony();
    }

    /**
     * The fungus garden while away, as in {@link FungusGarden#grow}, from the garden as last seen with what is owed to
     * it since: fresh layers ripen, each comb block with a ripe layer and room for another nodule grows one about every
     * {@code nodule_ticks} (that layer goes old), other ripe layers go old by themselves, and old layers crumble away.
     */
    boolean grow(ServerLevel level) {
        SavannaNumbers.GardenNumbers numbers = colony.profile().section(SavannaNumbers.GARDEN).orElse(null);
        if (numbers == null || garden.seenCombs() == 0) {
            return false;
        }
        NestLedger ledger = colony.ledger();
        boolean changed = false;
        float speed = garden.ageingSpeed();
        ripening += garden.freshKnown() * speed * 20.0 / Math.max(20, numbers.ripenTicks());
        while (ripening >= 1 && garden.freshKnown() > 0) {
            ripening--;
            ledger.addRipened(1);
            colony.away().note(level, AwayReport.What.COMB_RIPENED, 1);
            changed = true;
        }
        ripening = garden.freshKnown() > 0 ? ripening : 0;
        int room = garden.seenCombs() * FungusCombBlock.MAX_NODULES - (garden.seenNodules() + ledger.nurseFood());
        int canGrow = Math.min(garden.seenCombs(), Math.min(garden.ripeKnown(), room));
        if (canGrow <= 0) {
            noduleGrowth = 0;
        } else {
            noduleGrowth += canGrow * FungusGarden.chancePerSecond(numbers);
            while (noduleGrowth >= 1 && canGrow-- > 0) {
                noduleGrowth--;
                ledger.addAged(1);
                ledger.addNurseFood(1);
                garden.grewAway();
                colony.away().note(level, AwayReport.What.NODULES_GROWN, 1);
                changed = true;
            }
        }
        ageing += garden.ripeKnown() * speed * 20.0 / Math.max(20, numbers.oldTicks());
        while (ageing >= 1 && garden.ripeKnown() > 0) {
            ageing--;
            ledger.addAged(1);
            colony.away().note(level, AwayReport.What.COMB_AGED, 1);
            changed = true;
        }
        ageing = garden.ripeKnown() > 0 ? ageing : 0;
        crumbling += garden.oldKnown() * speed * 20.0 / Math.max(20, numbers.crumbleTicks());
        while (crumbling >= 1 && garden.oldKnown() > 0) {
            crumbling--;
            ledger.addStore(-1);
            colony.food().used(1);
            changed = true;
        }
        crumbling = garden.oldKnown() > 0 ? crumbling : 0;
        return changed;
    }
}
