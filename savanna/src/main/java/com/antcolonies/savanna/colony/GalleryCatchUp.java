package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.colony.NestPlan;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The galleries catching up after the colony comes back (Apex part 3, T2, one of the four parts of {@link Galleries},
 * step 4): the growth the builder numbers' time paid for while away, and for a while every branch due filled in at once.
 */
final class GalleryCatchUp {
    /** Step 4: until when (game time) every branch due is filled in at once, just after coming back. Not saved. */
    long catchUpUntil = Long.MIN_VALUE;

    /** How long (ticks) after coming back the branches due are filled in at once (then one per upkeep again). */
    private static final int CATCH_UP_TICKS = 600;

    /** At most this many branches are filled in a second while catching up. */
    static final int CATCH_UP_FILLS = 20;

    final Galleries g;
    private final Colony colony;

    GalleryCatchUp(Galleries g) {
        this.g = g;
        this.colony = g.colony;
    }

    /**
     * Step 4: the colony is back (from {@link AwayLife#settle}): for a while every branch due to fill in (10 days unused,
     * counted while away) is filled in as soon as it may be, as the real termites would have done meanwhile.
     */
    public void cameBack(ServerLevel level) {
        if (g.isOn()) {
            catchUpUntil = level.getGameTime() + CATCH_UP_TICKS;
            g.upkeep.upkeptAt = Long.MIN_VALUE;
        }
    }

    /**
     * Step 4: on return, the builder numbers' saved time grows the galleries ({@link BuildLife}): one step of growth now,
     * whatever the clock, by the same rules as here (the arms, the holes along dug tunnels, rich food first, then grass
     * pulling a tip, then the even spreading; within the workers' budget and the reach). Returns the pieces planned, of
     * them those toward food, and the blocks of gallery they add ({0, 0, 0}: nothing may grow now).
     */
    public int[] growNow(ServerLevel level) {
        int[] none = {0, 0, 0};
        if (!g.isOn() || colony.isFounding()) {
            return none;
        }
        List<NestPlan.ForagingTunnel> own = colony.nest().ownForagingTunnels(level);
        if (own.isEmpty()) {
            return none;
        }
        Set<BlockPos> before = new HashSet<>();
        g.web.arms.forEach(arm -> before.add(arm.hole()));
        int length = g.web.length();
        g.web.adoptFoodTunnels(level);
        if (!g.web.armsPlanned) {
            g.web.planArms(level, own);
        }
        g.turns.layOutHoles(level, own);
        if (g.web.armsPlanned) {
            colony.foragingHoles().checkNewHolesNow(level); // food first
            if (g.web.arms.size() == before.size()) {
                g.web.growTips(level);
            }
        }
        int pieces = 0;
        int toFood = 0;
        FoodWays ways = colony.nest().foodWays();
        for (NestPlan.ForagingTunnel arm : g.web.arms) {
            if (!before.contains(arm.hole())) {
                pieces++;
                boolean pulled = g.web.tips.stream().anyMatch(tip -> tip.pulled() && tip.way().hole().equals(arm.hole()));
                toFood += ways.isLiveNewHole(arm.hole()) || pulled ? 1 : 0;
            }
        }
        return new int[] {pieces, toFood, Math.max(0, g.web.length() - length)};
    }

    /** Whether the branches due are still being filled in at once (just back). */
    boolean isCatchingUp(long now) {
        return now < catchUpUntil;
    }
}
