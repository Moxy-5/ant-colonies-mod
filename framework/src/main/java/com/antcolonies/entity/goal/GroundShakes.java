package com.antcolonies.entity.goal;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * What blind insects feel through the ground (the animation engine, 2026-10-16): a player jumping or landing shakes it
 * hard (told by {@code ModEvents}). The shake reaches every insect in its range at the moment it happens (real
 * vibrations run through the soil far faster than an insect walks, {@code docs/BUGS.md} 66), and the insect's
 * {@link NoticeGoal} freezes on its next step. Walking and sprinting are read from the players themselves. Server only.
 */
public final class GroundShakes {
    /** How far round a player the insects are looked for (blocks); each one's own profile's shake range decides. */
    private static final double SEARCH = 16.0;

    private GroundShakes() {
    }

    /** A player jumped or landed: every insect within its shake range of the player now feels it. */
    public static void hard(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        for (AbstractAntEntity insect : level.getEntitiesOfClass(AbstractAntEntity.class, player.getBoundingBox().inflate(SEARCH))) {
            double range = insect.profile().bodyLanguage().flatMap(SpeciesProfile.BodyLanguage::notice)
                    .filter(SpeciesProfile.Notice::byVibration).map(n -> (double) n.shakeRange()).orElse(-1.0);
            if (player.distanceTo(insect) <= range) {
                insect.feelShake(player.getUUID(), now);
            }
        }
    }
}
