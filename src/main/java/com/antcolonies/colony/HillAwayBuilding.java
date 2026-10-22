package com.antcolonies.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The ant hill's share of the building on return ({@link BuildLife}; Project Apex step 8c, moved out of the shared code
 * unchanged): the night soil carried up first, and the soil of each block the builder numbers dig heaped on the hill
 * while it has room. Founders leave theirs where it falls (they carry no soil, as when they dig here).
 */
final class HillAwayBuilding implements ColonyHooks.AwayBuilder {
    /** Who claims a pile of night soil for the builder numbers (see {@link #first}). */
    private static final java.util.UUID BUILDERS = new java.util.UUID(0L, 0x4255494cL);

    private final Hill hill;
    /** Where the soil of the block about to be dug goes (looked at before it is dug), or null. Only for that block. */
    private BlockPos heap;

    HillAwayBuilding(Hill hill) {
        this.hill = hill;
    }

    /**
     * The night soil first (as the real builders do each morning, see {@link NightSoil}): the piles near the top of
     * the nest are carried up onto the hill before anything more is dug. True if some still waits (time ran out, or a
     * player can see the pile or the hill).
     */
    @Override
    public boolean first(ServerLevel level, BuildLife build) {
        Colony colony = build.colony();
        if (colony.buildsMound() || colony.isFounding()) {
            return false;
        }
        while (build.timeLeft() && hill.hasMoundRoom(level)) {
            if (build.secondFull()) {
                return true;
            }
            BlockPos pile = hill.nightSoil().claimPile(level, BUILDERS);
            if (pile == null) {
                return false; // nothing waits
            }
            hill.nightSoil().release(BUILDERS);
            if (build.seen(level, pile)) {
                return true;
            }
            BlockPos spot = hill.pickMoundSpot(level, colony.random(), null);
            if (spot == null) {
                return false;
            }
            if (!build.freeAndHidden(level, spot)) {
                return true;
            }
            if (hill.nightSoil().takeLayer(level, pile, BUILDERS) == null || !hill.placePellet(level, spot)) {
                return false;
            }
            build.spend(BuildLife.trip(pile, spot));
            build.changed();
            colony.away().report().add(AwayReport.What.HILL, 1);
        }
        return false;
    }

    /** The hill's first spot for this block's soil, looked at before the block is dug: false if it cannot be used unseen now. */
    @Override
    public boolean readyToDig(ServerLevel level, BuildLife build, BlockPos target) {
        Colony colony = build.colony();
        heap = !colony.buildsMound() && !colony.isFounding() && hill.hasMoundRoom(level) ? hill.pickMoundSpot(level, colony.random(), null) : null;
        return heap == null || build.freeAndHidden(level, heap);
    }

    /** The dug block's soil carried up onto the hill, a pellet a bite, while it has room and nobody sees it. */
    @Override
    public void dug(ServerLevel level, BuildLife build, BlockPos target, int bites) {
        Colony colony = build.colony();
        if (colony.buildsMound()) {
            return;
        }
        for (int i = 0; i < bites && heap != null; i++) {
            if (!hill.placePellet(level, heap)) {
                break;
            }
            build.spend(BuildLife.trip(target, heap));
            build.changed();
            colony.away().report().add(AwayReport.What.HILL, 1);
            heap = hill.hasMoundRoom(level) ? hill.pickMoundSpot(level, colony.random(), null) : null;
            if (heap != null && !build.freeAndHidden(level, heap)) {
                heap = null; // a player can see the hill now: the rest of this block's soil is left behind
            }
        }
        heap = null;
    }
}
