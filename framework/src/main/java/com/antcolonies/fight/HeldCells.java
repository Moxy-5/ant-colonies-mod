package com.antcolonies.fight;

import com.antcolonies.entity.AbstractAntEntity;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cells held by a living defender (the termite alarm rework, step 4, the held mouth; species-neutral): a cell an insect
 * holds is a wall to everything it would fight, for as long as it lives and stands in it. One rule with two readers:
 * the legs (the route planner's map, {@code LevelCellMap}: no route of a foe's passes through it) and the hunters'
 * "is this one shut in?" ({@link Fights#isShutIn}: nobody plans a walk to a member behind it). The holder itself can
 * still be fought from the cell beside it. Real: a Macrotermes major soldier plugs a gallery with its head and jaws
 * (phragmosis), and Megaponera must kill it before they get at the workers behind.
 *
 * <p>Server thread only. The holder says so again every few ticks ({@link #hold}); a hold not renewed, or whose holder
 * is dead, down or out of its cell, is gone.
 */
public final class HeldCells {
    /** How long one {@link #hold} lasts (ticks): the holder renews it while it stands there. */
    private static final int HOLD_TICKS = 10;

    private record Hold(AbstractAntEntity holder, long until) {
    }

    private static final Map<Level, Map<BlockPos, Hold>> HELD = new WeakHashMap<>();

    private HeldCells() {
    }

    /** The insect holds this cell, which it stands in, for the next few ticks. */
    public static void hold(AbstractAntEntity holder, BlockPos cell) {
        if (holder.level().isClientSide) {
            return;
        }
        HELD.computeIfAbsent(holder.level(), level -> new HashMap<>())
                .put(cell.immutable(), new Hold(holder, holder.level().getGameTime() + HOLD_TICKS));
    }

    /** Whether any cell of this world may be held now (a cheap first question). */
    public static boolean any(Level level) {
        Map<BlockPos, Hold> held = HELD.get(level);
        return held != null && !held.isEmpty();
    }

    /**
     * Whether this cell is a wall to the asker: a living insect that would fight it holds the cell and stands in it.
     * Never to an asker with no body, to the holder's own side, or to one already inside the cell.
     */
    public static boolean barred(Level level, BlockPos cell, @Nullable Entity asker) {
        Map<BlockPos, Hold> held = HELD.get(level);
        if (held == null || held.isEmpty() || asker == null) {
            return false;
        }
        Hold hold = held.get(cell);
        if (hold == null) {
            return false;
        }
        AbstractAntEntity holder = hold.holder();
        if (hold.until() < level.getGameTime() || !holder.isAlive() || holder.isDowned() || !holder.blockPosition().equals(cell)) {
            held.remove(cell);
            return false;
        }
        return asker != holder && !asker.blockPosition().equals(cell) && Fights.mayAttack(holder, asker);
    }
}
