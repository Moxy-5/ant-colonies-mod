package com.antcolonies.nav;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * Hidden ways (termites underground, step 2b): long stretches of a colony's galleries, closed all round, that nobody
 * can see into. An insect whose route runs a long way along them is taken out of the world while it goes, and put back
 * at the far end after the time the walk takes ({@link AntNavigator}): the long walk costs nothing and plans nothing,
 * as a real termite's trip is unseen. Who owns the ways and what happens to the insect meanwhile is not the legs'
 * business: the legs only ask whether a cell is hidden and hand over the stretch.
 */
public interface HiddenWays {
    /** Whether this cell is a hidden way now: one of the galleries' cells, closed all round (nothing open beside it but more gallery). */
    boolean isHidden(BlockPos cell);

    /** The fewest steps of hidden way a route must run along to be travelled unseen (shorter stretches are walked). */
    int shortest();

    /**
     * Takes the insect out of the world for its trip along {@code run} (the footholds from where it stands now to where
     * it comes out, all hidden, the last one a floor), which takes {@code ticks}. False if it cannot go (it walks).
     */
    boolean goUnder(com.antcolonies.entity.AbstractAntEntity insect, List<Foothold> run, long ticks);
}
