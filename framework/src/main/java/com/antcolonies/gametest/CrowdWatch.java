package com.antcolonies.gametest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Crowd;

/**
 * Real sizes, step 3, personal space ({@code docs/plans/real-sizes.md}): watches a crowd of insects in a test, tick by
 * tick, for two standing one inside the other ({@link Crowd#inside}) for long. A little overlap is allowed (a crowded
 * breach, two squeezing past); never one body inside another for more than {@link #LONGEST} ticks in a row. Only
 * insects on the ground on a floor count (one climbing, in a channel, felled, held or riding moves exactly or lies
 * where it fell, and the others step over it). {@code counts}: which of them the test cares about (outside the nest,
 * say).
 */
public final class CrowdWatch {
    /** Ticks in a row two may stand inside one another (a second). */
    public static final int LONGEST = 20;

    private final Predicate<AbstractAntEntity> counts;
    private final Map<Long, Integer> together = new HashMap<>();
    private int worst;
    private String worstWhat = "none";
    private int ticks;

    public CrowdWatch(Predicate<AbstractAntEntity> counts) {
        this.counts = counts;
    }

    /** One tick: every pair of these insects standing inside one another now counts on, the others start again. */
    public void tick(Collection<? extends AbstractAntEntity> insects) {
        ticks++;
        List<AbstractAntEntity> ants = insects.stream().filter(this::watched).map(ant -> (AbstractAntEntity) ant).toList();
        Map<Long, Integer> now = new HashMap<>();
        for (int i = 0; i < ants.size(); i++) {
            AbstractAntEntity a = ants.get(i);
            for (int j = i + 1; j < ants.size(); j++) {
                AbstractAntEntity b = ants.get(j);
                if (!Crowd.inside(a, b)) {
                    continue;
                }
                long key = Math.min(a.getId(), b.getId()) * 1_000_003L + Math.max(a.getId(), b.getId());
                int count = together.getOrDefault(key, 0) + 1;
                now.put(key, count);
                if (count > worst) {
                    worst = count;
                    worstWhat = describe(a) + " and " + describe(b) + ", " + String.format("%.2f", Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()))
                            + " apart";
                }
            }
        }
        together.clear();
        together.putAll(now);
    }

    /** The most ticks in a row any two stood inside one another. */
    public int worstTicks() {
        return worst;
    }

    /** Whether no two stood inside one another for longer than {@link #LONGEST} ticks. */
    public boolean keptApart() {
        return worst <= LONGEST;
    }

    /** For a test's message: the worst pair, and how long. */
    public String describe() {
        return "the worst pair stood inside one another " + worst + " tick(s) in a row (" + worstWhat + ") over " + ticks + " ticks watched";
    }

    private boolean watched(AbstractAntEntity ant) {
        return ant.isAlive() && Crowd.keepsSpace(ant) && ant.onGround() && !ant.isPassenger() && !ant.isDowned() && !ant.isPinned()
                && !ant.getNavigator().isClimbing() && !ant.getNavigator().isInChannel() && counts.test(ant);
    }

    private static String describe(AbstractAntEntity ant) {
        return ant.getType().builtInRegistryHolder().key().location().getPath() + " #" + ant.getId() + " at "
                + String.format("%.2f %.2f %.2f", ant.getX(), ant.getY(), ant.getZ()) + (ant.getNavigator().isDone() ? " standing" : " walking") + " " + ant.runningGoals();
    }
}
