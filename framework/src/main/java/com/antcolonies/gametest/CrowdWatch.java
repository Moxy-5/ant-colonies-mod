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
 * tick, for two one inside the other ({@link Crowd#inside}) for long. A little overlap is allowed (a crowded breach, two
 * squeezing past): two both standing never more than {@link #LONGEST} ticks in a row; a pair where one walks (one
 * squeezing past the other, which the legs allow for a second once there is no room to step round) never more than
 * {@link #LONGEST_PASSING} (real sizes, step 4: the squeeze's second plus walking in and out). Only insects on the
 * ground on a floor count (one climbing, in a channel, felled, held or riding moves exactly or lies where it fell, and
 * the others step over it). {@code counts}: which of them the test cares about (outside the nest, say).
 */
public final class CrowdWatch {
    /** Ticks in a row two may stand inside one another (a second). */
    public static final int LONGEST = 20;
    /** Ticks in a row two may be inside one another while one of them walks (two seconds). */
    public static final int LONGEST_PASSING = 40;

    private final Predicate<AbstractAntEntity> counts;
    /** Per pair: ticks in a row inside one another, and of those, ticks in a row both standing. */
    private final Map<Long, int[]> together = new HashMap<>();
    private final Worst standing = new Worst();
    private final Worst passing = new Worst();
    private int ticks;

    /** The longest run of one kind so far, the pair and the watched tick it ended at. */
    private static final class Worst {
        int ticks;
        String what = "none";
        int at;
    }

    public CrowdWatch(Predicate<AbstractAntEntity> counts) {
        this.counts = counts;
    }

    /** One tick: every pair of these insects inside one another now counts on, the others start again. */
    public void tick(Collection<? extends AbstractAntEntity> insects) {
        ticks++;
        List<AbstractAntEntity> ants = insects.stream().filter(this::watched).map(ant -> (AbstractAntEntity) ant).toList();
        Map<Long, int[]> now = new HashMap<>();
        for (int i = 0; i < ants.size(); i++) {
            AbstractAntEntity a = ants.get(i);
            for (int j = i + 1; j < ants.size(); j++) {
                AbstractAntEntity b = ants.get(j);
                if (!Crowd.inside(a, b)) {
                    continue;
                }
                long key = Math.min(a.getId(), b.getId()) * 1_000_003L + Math.max(a.getId(), b.getId());
                int[] before = together.getOrDefault(key, new int[2]);
                boolean bothStand = a.getNavigator().isDone() && b.getNavigator().isDone();
                int[] count = {before[0] + 1, bothStand ? before[1] + 1 : 0};
                now.put(key, count);
                note(passing, count[0], a, b, ants);
                note(standing, count[1], a, b, ants);
            }
        }
        together.clear();
        together.putAll(now);
    }

    private void note(Worst worst, int count, AbstractAntEntity a, AbstractAntEntity b, List<AbstractAntEntity> ants) {
        if (count > worst.ticks) {
            worst.ticks = count;
            worst.at = ticks;
            worst.what = describe(a) + " and " + describe(b) + ", " + String.format("%.2f", Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()))
                    + " apart; " + around(a, ants);
        }
    }

    /** Ticks watched so far. */
    public int watchedTicks() {
        return ticks;
    }

    /** The most ticks in a row any two stood inside one another, both standing. */
    public int worstTicks() {
        return standing.ticks;
    }

    /** Whether no two stood inside one another for longer than {@link #LONGEST} ticks, nor passed for longer than {@link #LONGEST_PASSING}. */
    public boolean keptApart() {
        return standing.ticks <= LONGEST && passing.ticks <= LONGEST_PASSING;
    }

    /** For a test's message: the worst pairs, and how long. */
    public String describe() {
        return "over " + ticks + " ticks watched, the worst pair both standing inside one another " + standing.ticks + " tick(s) in a row (limit "
                + LONGEST + ", ending at watched tick " + standing.at + ": " + standing.what + "); the worst inside one another at all "
                + passing.ticks + " (limit " + LONGEST_PASSING + ", ending at watched tick " + passing.at + ": " + passing.what + ")";
    }

    private boolean watched(AbstractAntEntity ant) {
        return ant.isAlive() && Crowd.keepsSpace(ant) && ant.onGround() && !ant.isPassenger() && !ant.isDowned() && !ant.isPinned()
                && !ant.getNavigator().isClimbing() && !ant.getNavigator().isInChannel() && counts.test(ant);
    }

    /**
     * For the message: the ground round {@code a} on its floor, 7 x 7 cells ({@code #} solid at its height, {@code .}
     * floor to stand on, {@code _} a drop) with the number of watched insects standing in each floor cell, and how many
     * stand within 1.5 blocks of it.
     */
    private static String around(AbstractAntEntity a, List<AbstractAntEntity> ants) {
        net.minecraft.core.BlockPos here = a.blockPosition();
        StringBuilder out = new StringBuilder("ground round it (x across, z down):");
        for (int dz = -3; dz <= 3; dz++) {
            out.append(" |");
            for (int dx = -3; dx <= 3; dx++) {
                net.minecraft.core.BlockPos p = here.offset(dx, 0, dz);
                if (!a.level().getBlockState(p).getCollisionShape(a.level(), p).isEmpty()) {
                    out.append('#');
                    continue;
                }
                if (a.level().getBlockState(p.below()).getCollisionShape(a.level(), p.below()).isEmpty()) {
                    out.append('_');
                    continue;
                }
                long n = ants.stream().filter(o -> o.blockPosition().equals(p)).count();
                out.append(n == 0 ? "." : String.valueOf(Math.min(9, n)));
            }
        }
        long near = ants.stream().filter(o -> o != a && Math.abs(o.getY() - a.getY()) <= Crowd.SAME_FLOOR
                && Math.hypot(o.getX() - a.getX(), o.getZ() - a.getZ()) <= 1.5).count();
        return out + "; " + near + " within 1.5 blocks";
    }

    private static String describe(AbstractAntEntity ant) {
        return ant.getType().builtInRegistryHolder().key().location().getPath() + " #" + ant.getId() + " at "
                + String.format("%.2f %.2f %.2f", ant.getX(), ant.getY(), ant.getZ()) + (ant.getNavigator().isDone() ? " standing" : " walking") + " " + ant.runningGoals()
                + " {" + ant.getNavigator().crowdState() + "}";
    }
}
