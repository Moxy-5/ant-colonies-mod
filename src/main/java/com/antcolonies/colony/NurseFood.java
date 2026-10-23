package com.antcolonies.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
     * The world catches up with the count (see {@link NestLedger}): one piece is put down at {@code spot} (a heap layer,
     * a nodule) without counting it. False if there is no room there.
     */
    boolean addPieceAt(Level level, BlockPos spot);

    /** The world catches up with the count: one piece is taken away at {@code spot} without counting it. False if none lies there. */
    boolean removePieceAt(Level level, BlockPos spot);

    /** Pieces counted that could be put down nowhere: they are taken out of the count (where there is a count). */
    void forget(int pieces);

    /**
     * Last resort for a nurse holding a piece it can take nowhere: it puts it down where it stands, if it can. Returns
     * true if the piece was put down (otherwise it is lost, and that is logged).
     */
    boolean putDownWhereItStands(Level level, AbstractAntEntity ant);

    // --- for the numbers (Project Apex step 8b: the shared nest life asks these in place of naming the bakery or the garden)

    /** How many pieces there are as far as the brain knows (the count, or what was last seen with what is owed since). */
    int counted(ServerLevel level);

    /**
     * One of the numbers takes a counted piece (its block comes off later, out of sight); returns whether it was rich
     * ({@code wantRich}: rich first, for the queen). Food that is never rich only keeps its own count (the ledger's).
     */
    default boolean takeCounted(boolean wantRich) {
        return false;
    }

    /** One more piece counted in now, brought in by the numbers (its block is put down later); food the ledger counts alone does nothing. */
    default void countedIn(boolean rich) {
    }

    /** What a piece looks like in the jaws. */
    ItemStack piece();
}
