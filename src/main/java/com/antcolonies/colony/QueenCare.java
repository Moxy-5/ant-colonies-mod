package com.antcolonies.colony;

import java.util.UUID;

import com.antcolonies.entity.QueenAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The queen's side of the colony: how much bread she has been fed, the egg she carries (or the burst of eggs after rich
 * bread), the workers on their way to feed her, and her hunger clock (she starves after days without food).
 */
public class QueenCare {
    /** The workers must feed the queen this many pieces of ant bread before she carries an egg. */
    public static final int QUEEN_MEAL = 20;
    /** This many pieces of rich ant bread (made from rich seeds, see {@code antcolonies:rich_seeds}) fed to the queen start a burst of eggs. */
    public static final int RICH_MEAL = 5;
    /** In a burst she lays this many eggs ... */
    public static final int BURST_EGGS = 5;
    /** ... one every minute at normal speed (an egg is still held back while the colony has {@link Brood#MAX_EGGS} young). */
    public static final int BURST_INTERVAL_TICKS = 1200;
    /** How long (in ticks) she carries the egg before it comes out behind her: 3 minutes at normal speed. */
    public static final int INCUBATION_TICKS = 3600;
    /** A queen who has not been fed for this many ticks (5 days) dies: she lives on her fat for a while. */
    public static final long QUEEN_STARVE_TICKS = 120000;
    /** How long (in ticks) a worker on its way to feed the queen counts as feeding her. */
    private static final int FEED_CLAIM_TICKS = 1300;

    private final Colony colony;

    /** How many pieces of bread the workers have fed the queen for her next egg (QUEEN_MEAL = full). Saved. */
    private int queenBread;
    /** The game time when the egg she carries comes out (-1 = she carries none). During a burst: when the next egg is due. Saved. */
    private long eggDue = -1;
    /** How many pieces of rich bread the workers have fed the queen towards a burst ({@link #RICH_MEAL} = it starts). Saved. */
    private int queenRich;
    /** Eggs the queen still has to lay in her burst (0 = no burst going on). Saved. */
    private int burstLeft;
    /** How many eggs the queen has laid in all. Saved. */
    private int eggsLaid;
    /** The game time of the queen's last piece of bread, or of the last time she needed none. Saved. */
    private long queenLastFed = Colony.NOT_SET;
    /** The workers on their way to feed the queen: ant -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> feeders = new Claims<>();

    QueenCare(Colony colony) {
        this.colony = colony;
    }

    public int getQueenBread() {
        return queenBread;
    }

    public int getQueenRich() {
        return queenRich;
    }

    /** Eggs left in the queen's burst (0 = none going on). */
    public int getBurstLeft() {
        return burstLeft;
    }

    /** Whether the queen carries an egg right now. */
    public boolean isIncubating() {
        return eggDue >= 0;
    }

    /** The game time when the egg she carries comes out (-1 if she carries none). */
    public long getEggDue() {
        return eggDue;
    }

    public int getEggsLaid() {
        return eggsLaid;
    }

    public long getQueenLastFed() {
        return queenLastFed;
    }

    public void setQueenLastFed(long time) {
        queenLastFed = time;
    }

    /** True when the queen sits in her finished room (the workers feed her there, and her egg comes out there). */
    public boolean isQueenHome(ServerLevel level) {
        BlockPos room = colony.nest().getFinishedRoomCenter(level);
        QueenAntEntity queen = room == null ? null : colony.getQueen(level);
        return queen != null && Math.abs(queen.getX() - (room.getX() + 0.5)) <= 2.5
                && Math.abs(queen.getZ() - (room.getZ() + 0.5)) <= 2.5 && Math.abs(queen.getY() - room.getY()) < 1.5;
    }

    /**
     * Whether the queen can take more bread: she is home and not carrying an egg, there are not yet {@link Brood#MAX_EGGS}
     * eggs, and the workers already on their way with bread would not fill her.
     */
    public boolean needsFeeding(ServerLevel level) {
        feeders.prune(level.getGameTime());
        return eggDue < 0 && queenBread + feeders.size() < QUEEN_MEAL && isQueenHome(level)
                && colony.brood().countBrood(level) < Brood.MAX_EGGS;
    }

    /** A worker sets out to bring the queen bread. False if she needs no more food right now. */
    public boolean claimFeeding(ServerLevel level, UUID ant) {
        if (!needsFeeding(level)) {
            return false;
        }
        feeders.put(ant, level.getGameTime() + FEED_CLAIM_TICKS);
        return true;
    }

    /** The worker has fed her, or gave up. */
    public void releaseFeeding(UUID ant) {
        feeders.remove(ant);
    }

    /**
     * A worker gives the queen one piece of bread. With {@link #QUEEN_MEAL} pieces she starts carrying an egg, which
     * comes out {@link #INCUBATION_TICKS} later. False if she takes no more right now (the worker keeps the bread).
     */
    public boolean feedQueen(ServerLevel level, boolean rich) {
        if (eggDue >= 0 || queenBread >= QUEEN_MEAL) {
            return false;
        }
        queenLastFed = level.getGameTime();
        if (rich) {
            // Rich bread: with RICH_MEAL pieces a burst of eggs starts, one every BURST_INTERVAL_TICKS, and she needs no more food for it.
            if (++queenRich >= RICH_MEAL) {
                queenRich = 0;
                queenBread = 0;
                burstLeft = BURST_EGGS;
                eggDue = level.getGameTime() + BURST_INTERVAL_TICKS;
            }
            return true;
        }
        queenBread++;
        if (queenBread >= QUEEN_MEAL) {
            eggDue = level.getGameTime() + INCUBATION_TICKS;
        }
        return true;
    }

    /** Whether the egg the queen carries is ready to come out. */
    public boolean isEggDue(ServerLevel level) {
        if (eggDue < 0 || level.getGameTime() < eggDue) {
            return false;
        }
        return burstLeft == 0 || colony.brood().countBrood(level) < Brood.MAX_EGGS; // a burst egg waits while the nursery is full of young
    }

    /** The egg comes out of the queen at this spot; she starts over with an empty stomach. */
    public void layEgg(ServerLevel level, double x, double y, double z) {
        eggsLaid++;
        if (burstLeft > 0) {
            // A burst egg: the next one is due a minute later, until the burst is over.
            burstLeft--;
            eggDue = burstLeft > 0 ? level.getGameTime() + BURST_INTERVAL_TICKS : -1;
        } else {
            queenBread = 0;
            eggDue = -1;
        }
        colony.brood().spawnEgg(level, x, y, z);
    }

    /**
     * Called by the queen once a second. Her hunger clock only runs while she wants food and gets none: it starts over
     * every time she is fed and for as long as she needs nothing (she carries an egg, she has her full meal, or the
     * nursery is full of young). Returns true when she has gone {@link #QUEEN_STARVE_TICKS} without it.
     */
    public boolean queenStarves(ServerLevel level) {
        long now = level.getGameTime();
        boolean satisfied = !colony.isHungerOn() || eggDue >= 0 || queenBread >= QUEEN_MEAL
                || colony.brood().countBrood(level) >= Brood.MAX_EGGS;
        if (queenLastFed == Colony.NOT_SET || satisfied) {
            queenLastFed = now;
            return false;
        }
        return now - queenLastFed >= QUEEN_STARVE_TICKS;
    }

    void save(CompoundTag tag) {
        tag.putInt("queenBread", queenBread);
        tag.putInt("queenRich", queenRich);
        tag.putInt("burstLeft", burstLeft);
        tag.putLong("queenLastFed", queenLastFed);
        tag.putLong("eggDue", eggDue);
        tag.putInt("eggsLaid", eggsLaid);
    }

    void load(CompoundTag tag) {
        queenBread = tag.contains("queenBread") ? tag.getInt("queenBread") : tag.getInt("queenSeeds"); // (old saves: seeds)
        queenRich = tag.getInt("queenRich");
        burstLeft = tag.getInt("burstLeft");
        queenLastFed = tag.contains("queenLastFed") ? tag.getLong("queenLastFed") : Colony.NOT_SET;
        eggDue = tag.contains("eggDue") ? tag.getLong("eggDue") : -1;
        eggsLaid = tag.getInt("eggsLaid");
    }
}
