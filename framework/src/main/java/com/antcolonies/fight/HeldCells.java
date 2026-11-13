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
 * is dead, down or out of its cell, is gone. Several insects may hold one cell (soldiers side by side in a gallery).
 */
public final class HeldCells {
    /** How long one {@link #hold} lasts (ticks): the holder renews it while it stands there. */
    private static final int HOLD_TICKS = 10;

    /** Per world, per cell: its holders, each with the game time its hold lasts to. */
    private static final Map<Level, Map<BlockPos, Map<AbstractAntEntity, Long>>> HELD = new WeakHashMap<>();

    private HeldCells() {
    }

    /** The insect holds this cell, which it stands in, for the next few ticks. */
    public static void hold(AbstractAntEntity holder, BlockPos cell) {
        if (holder.level().isClientSide) {
            return;
        }
        long now = holder.level().getGameTime();
        Map<AbstractAntEntity, Long> holders = HELD.computeIfAbsent(holder.level(), level -> new HashMap<>())
                .computeIfAbsent(cell.immutable(), at -> new HashMap<>(2));
        holders.entrySet().removeIf(hold -> gone(hold.getKey(), hold.getValue(), cell, now));
        holders.put(holder, now + HOLD_TICKS);
    }

    private static boolean gone(AbstractAntEntity holder, long until, BlockPos cell, long now) {
        return until < now || !holder.isAlive() || holder.isDowned() || !holder.blockPosition().equals(cell);
    }

    /** Whether this insect holds the cell it stands in now. */
    public static boolean holds(AbstractAntEntity insect) {
        Map<BlockPos, Map<AbstractAntEntity, Long>> held = HELD.get(insect.level());
        Map<AbstractAntEntity, Long> holders = held == null ? null : held.get(insect.blockPosition());
        Long until = holders == null ? null : holders.get(insect);
        return until != null && until >= insect.level().getGameTime() && insect.isAlive() && !insect.isDowned();
    }

    /** Whether any cell of this world may be held now (a cheap first question). */
    public static boolean any(Level level) {
        Map<BlockPos, Map<AbstractAntEntity, Long>> held = HELD.get(level);
        return held != null && !held.isEmpty();
    }

    /**
     * Whether this cell is a wall to the asker: a living insect that would fight it holds the cell and stands in it.
     * Never to an asker with no body, to the holder's own side, or to one already inside the cell.
     */
    public static boolean barred(Level level, BlockPos cell, @Nullable Entity asker) {
        Map<BlockPos, Map<AbstractAntEntity, Long>> held = HELD.get(level);
        if (held == null || held.isEmpty() || asker == null) {
            return false;
        }
        Map<AbstractAntEntity, Long> holders = held.get(cell);
        if (holders == null) {
            return false;
        }
        long now = level.getGameTime();
        holders.entrySet().removeIf(hold -> gone(hold.getKey(), hold.getValue(), cell, now));
        if (holders.isEmpty()) {
            held.remove(cell);
            return false;
        }
        if (asker.blockPosition().equals(cell)) {
            return false;
        }
        for (AbstractAntEntity holder : holders.keySet()) {
            if (asker != holder && Fights.mayAttack(holder, asker)) {
                return true;
            }
        }
        return false;
    }
}
