package com.antcolonies.savanna.animal;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * An animal's anger at whoever hurt it (lifted out of the Cape buffalo when the lions came, 2026-11-25,
 * {@code docs/plans/lion.md}): at whom, until when (its own tick), and where it stood when hurt. Its mates read it (they
 * see it, never told). Never at one of its own group, and never at a creative or spectator player, as for Minecraft's own
 * mobs (the user tests in creative and explores in spectator: a punched animal must not chase someone it can never hurt).
 */
public final class Anger {
    @Nullable
    private LivingEntity at;
    private int until;
    @Nullable
    private Vec3 where;

    /** Whether someone can be a foe at all: not a creative or spectator player. */
    public static boolean canBeFoe(@Nullable Entity e) {
        return e != null && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    /** A hurt from {@code source}: angry for {@code ticks} at whoever did it, unless {@code friend} lets them off. */
    public void hurt(Mob self, DamageSource source, Predicate<Entity> friend, int ticks) {
        if (!self.level().isClientSide && self.isAlive() && source.getEntity() instanceof LivingEntity by && by != self
                && !friend.test(by) && canBeFoe(by)) {
            at = by;
            until = self.tickCount + ticks;
            where = self.position();
        }
    }

    /** Whom it is angry at now, or null (the time is up, or they are gone, dead, elsewhere or no foe any more). */
    @Nullable
    public LivingEntity at(Mob self) {
        if (at != null && (self.tickCount > until || !at.isAlive() || at.isRemoved() || at.level() != self.level() || !canBeFoe(at))) {
            at = null;
        }
        return at;
    }

    /** Where it stood when last hurt. */
    @Nullable
    public Vec3 where() {
        return where;
    }

    /** It gives up its anger. */
    public void calm() {
        at = null;
    }
}
