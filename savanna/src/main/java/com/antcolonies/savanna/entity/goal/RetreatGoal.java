package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.Errand;
import com.antcolonies.savanna.colony.RaidDefence;
import com.antcolonies.savanna.colony.SavannaParts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Going deeper when a raid breaks into the gallery (M8 step 2f, {@link RaidDefence}): a worker between the break and the
 * nest drops what it is doing and hurries down the gallery, past where it is to be packed shut; the one furthest in stops
 * just behind that spot (it packs it, {@link RaidDefence} places the soil). Real Macrotermes workers feel the soldiers'
 * drumming through the walls and retreat deeper, sealing the gallery behind them. The legs walk it; this goal only says where.
 */
public class RetreatGoal extends Goal {
    /** How fast it hurries (times its walk). */
    private static final double SPEED = 1.3;
    /** It gives up after this long (ticks) if it never gets there. */
    private static final int GIVE_UP_TICKS = 300;
    /** How near (blocks) counts as there. */
    private static final double REACH = 0.3; // the middle of the cell: its body clear of the one to be packed

    private final AbstractAntEntity worker;
    private final Errand<BlockPos> errand;
    @Nullable
    private RaidDefence raid;
    @Nullable
    private BlockPos to;
    private int ticks;

    public RetreatGoal(AbstractAntEntity worker) {
        this.worker = worker;
        this.errand = new Errand<>(worker);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if ((worker.tickCount + worker.getId()) % 5 != 0 || worker.isDowned() || worker.isPinned()
                || !(worker.level() instanceof ServerLevel)) {
            return false;
        }
        Colony colony = worker.getColony();
        raid = colony == null ? null : colony.part(SavannaParts.RAID_DEFENCE).orElse(null);
        to = raid == null ? null : raid.retreatFor(worker.getUUID());
        return to != null;
    }

    @Override
    public boolean canContinueToUse() {
        return raid != null && to != null && ticks < GIVE_UP_TICKS && !worker.isDowned();
    }

    @Override
    public void start() {
        ticks = 0;
        errand.begin();
        errand.setOff(to, SPEED);
    }

    @Override
    public void stop() {
        errand.stop();
        if (raid != null) {
            raid.retreated(worker.getUUID());
        }
        raid = null;
        to = null;
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos now = raid.retreatFor(worker.getUUID());
        if (now == null) {
            to = null; // sealed (or forgotten): it may go about its work
            return;
        }
        if (errand.within(now, REACH)) {
            errand.stop(); // there: it holds still (the plugger, till the soil is packed)
            if (!now.equals(to)) {
                to = now;
            }
            return;
        }
        if (!now.equals(to) || !errand.isWalking()) {
            to = now;
            if (!errand.setOff(now, SPEED)) {
                ticks += 20; // no way there now: it tries again a little, then gives up
            }
        }
    }
}
