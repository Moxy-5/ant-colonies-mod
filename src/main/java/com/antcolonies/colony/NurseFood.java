package com.antcolonies.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The food the nurses feed the queen and the young, and where it is kept: ant bread in bread heaps for the harvester
 * ant ({@link Bakery}), fungus nodules on the fungus comb for termites ({@link FungusGarden}). The feeding goals only
 * talk to this, so they work for any species ({@code Colony.nurseFood()} picks the colony's own).
 */
public interface NurseFood {
    /** How many pieces there are to take right now. */
    int available(Level level);

    /** A place to take a piece from, or null if there is none. */
    @Nullable
    BlockPos findFood(Level level, RandomSource random);

    /**
     * A nurse takes one piece at {@code spot}. Returns it, or an empty stack if there is nothing there any more.
     * {@code wantRich}: for the queen, who gets rich bread first (where there is such a thing).
     */
    ItemStack takeFoodAt(Level level, BlockPos spot, boolean wantRich);

    /** A place to put a piece back that nobody wanted, or null if there is none. */
    @Nullable
    BlockPos pickReturnSpot(Level level, RandomSource random);

    /** A nurse puts a piece back at {@code spot}. False if there is no room for it there (the nurse looks for another). */
    boolean putBack(Level level, BlockPos spot, boolean rich);

    /**
     * Last resort for a nurse holding a piece it can take nowhere: it puts it down where it stands, if it can. Returns
     * true if the piece was put down (otherwise it is lost, and that is logged).
     */
    boolean putDownWhereItStands(Level level, AbstractAntEntity ant);
}
