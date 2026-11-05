package com.antcolonies.gametest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

/**
 * M8 step 3Q, Q1: the share of time insects with nothing to do spend walking, per kind (the real numbers: about 40
 * percent of a colony's workers do nothing at any moment, Charbonneau and Dornhaus 2015; an idle ant mostly stands,
 * cleaning itself or napping, it does not pace). A test calls {@link #tick} every tick with the insects to watch and
 * which of them count as waiting (by default an insect that has had nothing to do for {@code IDLE_SECONDS},
 * {@link AbstractAntEntity#isIdle}); a waiting insect that moved more than {@link #MOVED} blocks sideways since the
 * tick before counts as walking. {@link #report} gives the shares in words, {@link #stillShare} one kind's (or all).
 * The target (Q6): idle insects still at least 3 quarters of the time.
 */
public final class IdleWatch {
    /** A sideways move this big in a tick (blocks) is walking; less is standing (a turn on the spot, a settle). */
    public static final double MOVED = 0.02;

    private final String name;
    private final Map<UUID, Vec3> last = new HashMap<>();
    /** Per kind (the entity type's path): ticks waiting, of those ticks walking, and all ticks seen. */
    private final Map<String, long[]> counts = new LinkedHashMap<>();

    public IdleWatch(String name) {
        this.name = name;
    }

    /** Counts this tick for these insects; {@code waiting} says which of them have nothing to do now. */
    public void tick(Iterable<? extends AbstractAntEntity> insects, Predicate<AbstractAntEntity> waiting) {
        for (AbstractAntEntity insect : insects) {
            if (!insect.isAlive()) {
                continue;
            }
            Vec3 now = insect.position();
            Vec3 before = last.put(insect.getUUID(), now);
            if (before == null) {
                continue;
            }
            long[] count = counts.computeIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(insect.getType()).getPath(), k -> new long[3]);
            count[2]++;
            if (!waiting.test(insect)) {
                continue;
            }
            count[0]++;
            double dx = now.x - before.x;
            double dz = now.z - before.z;
            if (dx * dx + dz * dz > MOVED * MOVED) {
                count[1]++;
            }
        }
    }

    /** Counts this tick for insects idle by their own goals ({@link AbstractAntEntity#isIdle}). */
    public void tick(Iterable<? extends AbstractAntEntity> insects) {
        tick(insects, AbstractAntEntity::isIdle);
    }

    /** The share of waiting time spent standing (0 to 1) for this kind (an entity type's path), or all kinds (null); 1 with none counted. */
    public double stillShare(String kind) {
        long waiting = 0;
        long walking = 0;
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            if (kind == null || entry.getKey().equals(kind)) {
                waiting += entry.getValue()[0];
                walking += entry.getValue()[1];
            }
        }
        return waiting == 0 ? 1.0 : 1.0 - walking / (double) waiting;
    }

    /** How many insect-ticks of waiting were counted for this kind (or all, null). */
    public long waitingTicks(String kind) {
        return counts.entrySet().stream().filter(e -> kind == null || e.getKey().equals(kind)).mapToLong(e -> e.getValue()[0]).sum();
    }

    /** The kinds counted. */
    public java.util.Set<String> kinds() {
        return counts.keySet();
    }

    /** The shares in words, per kind: "termite_worker still 82% of 4,000 idle ticks". */
    public String report() {
        StringBuilder line = new StringBuilder("[idle] ").append(name).append(':');
        if (counts.isEmpty()) {
            line.append(" nobody idle");
        }
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            long[] c = entry.getValue();
            line.append(' ').append(entry.getKey()).append(" still ")
                    .append(c[0] == 0 ? "-" : Math.round(100.0 * (1.0 - c[1] / (double) c[0])) + "%")
                    .append(" of ").append(c[0]).append(" idle ticks (of ").append(c[2]).append(" seen);");
        }
        return line.toString();
    }

    /** Writes {@link #report} to the log (the numbers to compare before and after, Q1 and Q6). */
    public void log() {
        ColonyFrameworkMod.LOGGER.info(report());
    }
}
