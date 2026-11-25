package com.antcolonies.savanna.animal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Whom an animal's temper has warned and watched, per player (lifted out of the Cape buffalo when the lions came,
 * 2026-11-25, {@code docs/plans/lion.md}; review R9-13: one timer for all let a second player be charged unwarned): each
 * player it warned and when (game time), and how far each player it watched was when its watch ended (it is stared at
 * again only after coming nearer). Old warnings and players no longer noticed are forgotten every 5 s.
 */
public final class WarnMemory {
    private final Map<UUID, Long> warned = new HashMap<>();
    private final Map<UUID, Double> watched = new HashMap<>();
    private final int memory;
    private int forgotAt;

    /** Warnings are remembered this long (ticks). */
    public WarnMemory(int memory) {
        this.memory = memory;
    }

    /** Forgets old warnings and watched players no longer noticed, at most once every 5 s (asked from {@code canUse}). */
    public void tidy(Mob self, Predicate<Player> notices) {
        if (self.tickCount - forgotAt < 100 && self.tickCount >= forgotAt) {
            return;
        }
        forgotAt = self.tickCount;                  // (asked every other tick, so never by "tickCount % 100")
        long now = self.level().getGameTime();
        warned.values().removeIf(at -> now - at > memory);
        watched.keySet().removeIf(id -> {
            Player p = self.level().getPlayerByUUID(id);
            return p == null || !notices.test(p);
        });
    }

    public void warn(Mob self, Player p) {
        warned.put(p.getUUID(), self.level().getGameTime());
    }

    public boolean warnedLately(Mob self, Player p) {
        Long at = warned.get(p.getUUID());
        return at != null && self.level().getGameTime() - at <= memory;
    }

    /** It watched this player until they were this far off. */
    public void watched(Player p, double distance) {
        watched.put(p.getUUID(), distance);
    }

    /** How far off this player was when its last watch of them ended, or null. */
    @Nullable
    public Double watchedAt(Player p) {
        return watched.get(p.getUUID());
    }
}
