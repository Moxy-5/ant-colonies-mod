package com.antcolonies.watch;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The raid test bench, B6 (labels over the ants, {@code /antcolonies labels}): every insect within 32 blocks of a player
 * who turned them on shows a short line above it: what it is doing and what it waits for ("attacking: waiting for
 * termites; still 12 s"), red while a rule it broke is fresh ({@link Watch#isFlagged}). A screenshot then tells the
 * cause by itself.
 *
 * <p>The labels are the insects' name tags, changed twice a second; they are never saved
 * ({@code AbstractAntEntity.saveWithoutId}), and an insect with a name of its own (a name tag) keeps it, unlabelled.
 */
public final class Labels {
    /** Insects this near a viewer are labelled (blocks). */
    static final double REACH = 32;
    /** How often the labels are written (ticks). */
    static final int EVERY = 10;

    /** The players who turned the labels on. */
    private static final Set<UUID> VIEWERS = ConcurrentHashMap.newKeySet();
    /** The insects labelled now, and the world they are in. */
    private static final Map<UUID, ServerLevel> LABELLED = new ConcurrentHashMap<>();

    private Labels() {
    }

    /** Turns the labels on or off for this player; true when now on. */
    public static boolean toggle(ServerPlayer player) {
        if (VIEWERS.remove(player.getUUID())) {
            if (VIEWERS.isEmpty()) {
                clearAll();
            }
            return false;
        }
        VIEWERS.add(player.getUUID());
        return true;
    }

    /** A player left the game: their labels go. */
    public static void forget(UUID player) {
        if (VIEWERS.remove(player) && VIEWERS.isEmpty()) {
            clearAll();
        }
    }

    /** Whether this insect wears a label now (its save leaves it out). */
    public static boolean isLabelled(UUID insect) {
        return LABELLED.containsKey(insect);
    }

    /** The server stopped. */
    public static void clear() {
        VIEWERS.clear();
        LABELLED.clear();
    }

    /** Each tick of each world: the labels written every {@link #EVERY} ticks. */
    public static void tick(ServerLevel level) {
        if (VIEWERS.isEmpty() && LABELLED.isEmpty() || level.getGameTime() % EVERY != 0) {
            return;
        }
        long now = level.getGameTime();
        Map<UUID, AbstractAntEntity> wanted = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            if (!VIEWERS.contains(player.getUUID())) {
                continue;
            }
            for (AbstractAntEntity insect : level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(player.blockPosition()).inflate(REACH),
                    insect -> insect.isAlive() && (insect.getCustomName() == null || LABELLED.containsKey(insect.getUUID())))) {
                wanted.put(insect.getUUID(), insect);
            }
        }
        // Labels no longer wanted in this world come off.
        for (Map.Entry<UUID, ServerLevel> labelled : LABELLED.entrySet()) {
            if (labelled.getValue() == level && !wanted.containsKey(labelled.getKey())) {
                Entity entity = level.getEntity(labelled.getKey());
                if (entity != null) {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                }
                LABELLED.remove(labelled.getKey());
            }
        }
        for (AbstractAntEntity insect : wanted.values()) {
            String text = InsectStories.label(level, insect, Watch.stillFor(insect));
            boolean flagged = Watch.isFlagged(insect, now);
            insect.setCustomName(Component.literal(text).withStyle(flagged ? ChatFormatting.RED : ChatFormatting.GRAY));
            insect.setCustomNameVisible(true);
            LABELLED.put(insect.getUUID(), level);
        }
    }

    private static void clearAll() {
        for (Map.Entry<UUID, ServerLevel> labelled : LABELLED.entrySet()) {
            Entity entity = labelled.getValue().getEntity(labelled.getKey());
            if (entity != null) {
                entity.setCustomName(null);
                entity.setCustomNameVisible(false);
            }
        }
        LABELLED.clear();
    }
}
