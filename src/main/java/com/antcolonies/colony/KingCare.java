package com.antcolonies.colony;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.TermiteKingEntity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The king's hunger (termites: a species whose founders are a royal pair). Real Macrotermes kings are fed mouth to mouth
 * by the nurses and die without it. He wants a nodule a day (the workers' meal interval; my pick, to confirm), here from
 * the real nurses ({@code FeedLarvaGoal}) and while the colony is away from the nurse numbers ({@link NestLife}). Unfed
 * he starves by the queen's rule: once he has wanted food for the queen's starve time. His clock is kept by the brain,
 * not by his body, so it runs on while he is frozen in unloaded land; starved there, he dies when his land loads again.
 */
public class KingCare {
    private final Colony colony;
    /** The game time of his last meal, or of the last time he could not get hungry ({@link Colony#NOT_SET}: not started). Saved. */
    private long lastFed = Colony.NOT_SET;
    /** He starved while the colony was away (he dies when his land loads again, see {@link #starves}). Saved. */
    private boolean starvedAway;

    KingCare(Colony colony) {
        this.colony = colony;
    }

    /** Whether the colony has a king who is alive as far as the brain knows. */
    boolean isAlive() {
        return colony.getCount(TermiteKingEntity.CASTE) > 0 && !starvedAway;
    }

    public long getLastFed() {
        return lastFed;
    }

    /** For the tests (and old saves, where his body kept the clock): his last meal was at this game time. */
    public void setLastFed(long gameTime) {
        lastFed = gameTime;
    }

    /** A new king has joined the colony: his clock starts now. */
    public void joined(ServerLevel level) {
        lastFed = level.getGameTime();
        starvedAway = false;
    }

    /** Whether he wants a nodule: a day (the workers' meal interval) after his last one, in a colony whose royals get hungry. */
    public boolean wantsFood(ServerLevel level) {
        if (!isAlive()) {
            return false; // (no king: his clock is left alone; a new king starts it, see joined)
        }
        updateClock(level);
        return level.getGameTime() - lastFed >= colony.profile().worker().mealIntervalTicks();
    }

    /** A nurse (a real one or a number) has fed him. */
    public void fed(ServerLevel level) {
        lastFed = level.getGameTime();
    }

    /**
     * Called by the king once a second while he is loaded: true when he must die of hunger, because he starved while the
     * colony was away, or here, unfed for the queen's starve time after he wanted food.
     */
    public boolean starves(ServerLevel level) {
        if (starvedAway) {
            return true;
        }
        if (colony.away().isComingBack()) {
            return false; // back in range: the brain looked after him while away (AwayLife.settle ends that first)
        }
        return hungerRunsOut(level);
    }

    /** A nurse's job while away (see {@link NestLife}): a piece of the nurses' food for him when he wants some. True if he was fed. */
    boolean fedAway(ServerLevel level, NestLife life) {
        if (!wantsFood(level) || !life.takeNurseFood(level)) {
            return false;
        }
        fed(level);
        colony.away().note(level, AwayReport.What.KING_FED, 1);
        return true;
    }

    /** Once a second while the colony is away: his clock runs on; true if he starved just now. */
    boolean liveAway(ServerLevel level) {
        if (!isAlive() || !hungerRunsOut(level)) {
            return false;
        }
        starvedAway = true;
        colony.away().noteKingStarved();
        AntColoniesMod.LOGGER.info("[away] colony {}: the king starved while the colony was away", colony.getId());
        return true;
    }

    /** His clock stands still (starts over) while he cannot get hungry: see {@link QueenCare#royalsGetHungry}. */
    private void updateClock(ServerLevel level) {
        if (lastFed == Colony.NOT_SET || !QueenCare.royalsGetHungry(colony)) {
            lastFed = level.getGameTime();
        }
    }

    private boolean hungerRunsOut(ServerLevel level) {
        updateClock(level);
        long hungrySince = lastFed + colony.profile().worker().mealIntervalTicks();
        return level.getGameTime() - hungrySince >= colony.profile().queen().starveTicks();
    }

    void save(CompoundTag tag) {
        tag.putLong("kingLastFed", lastFed);
        tag.putBoolean("kingStarvedAway", starvedAway);
    }

    void load(CompoundTag tag) {
        lastFed = tag.contains("kingLastFed") ? tag.getLong("kingLastFed") : Colony.NOT_SET;
        starvedAway = tag.getBoolean("kingStarvedAway");
    }
}
