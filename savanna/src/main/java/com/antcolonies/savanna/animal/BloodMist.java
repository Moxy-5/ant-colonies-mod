package com.antcolonies.savanna.animal;

import com.antcolonies.savanna.registry.SavannaParticles;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * A red mist in the water where a hunt ends (the user's idea, 2026-11-23): a soft cloud of blood that billows out round
 * the catch and fades in a few seconds, so a kill under water reads at a glance (no blood on the ground, nothing left
 * behind). Shown when a hunter takes its catch in the water (the shark, the crocodile, the catfish, the otter: their own
 * code calls {@link #puff}), at every bite or tear of remains in the water, and when anything that bleeds is killed in the
 * water by a creature or a player ({@link #onDeath}). Fun first, as for every animal: crabs and snails (their blood is not
 * red), undead and golems give none. The cloud's look is {@code client/BloodMistParticle}.
 */
public final class BloodMist {
    private BloodMist() {
    }

    /** A cloud the size of this creature, if it is in the water and bleeds. */
    public static void puff(Entity prey) {
        puff(prey, 1.0F);
    }

    /** A cloud the size of this creature times {@code more} (a bite of remains: less), if it is in the water and bleeds. */
    public static void puff(Entity prey, float more) {
        if (!(prey.level() instanceof ServerLevel level) || !prey.isInWater() || !bleeds(prey)) {
            return;
        }
        double size = Math.max(prey.getBbWidth(), prey.getBbHeight());
        int count = Math.max(6, Math.min(60, Math.round((float) (10 + 28 * size) * more)));
        double spread = 0.12 + 0.3 * size;
        level.sendParticles(SavannaParticles.BLOOD_MIST.get(), prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ(),
                count, spread, spread * 0.6, spread, 0.015);
    }

    /** Whether a creature shows red when hurt (my pick, fun first). */
    static boolean bleeds(Entity e) {
        return !(e instanceof CrabEntity || e instanceof SnailEntity || e instanceof Enemy || e instanceof AbstractGolem);
    }

    /** Anything that bleeds, killed in the water by a creature or a player (a crocodile's death roll, a sword). */
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.level().isClientSide && event.getSource().getEntity() instanceof LivingEntity) {
            puff(dead, 1.3F);
        }
    }
}
