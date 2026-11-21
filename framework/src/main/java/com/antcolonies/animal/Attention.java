package com.antcolonies.animal;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Where an animal's eyes go (the animal kit, 2026-11-21; the user: eyes that "move around ... to things that catch their
 * attention like the player ... or a crocodile getting too close"). Drawn only: the animal ticks it on the client, its
 * model reads {@link #on()} and {@link #eyeAim} / {@link #headAim} to slide its pupils and turn its head a little.
 *
 * <p>Every quarter second it picks what catches its eye: each living thing within {@link #searchReach} is scored by its
 * distance times the species' {@link Pull} (0 or less: never), the lowest wins; it keeps its eyes on one 2 to 5 seconds
 * (what it already watches counts a little nearer) before it looks round again. With nothing to watch its eyes wander
 * ahead or to either side every 2 to 6 seconds. The same rules as the crocodile's own eyes (2026-11-15, confirmed by the
 * user), which still keeps its own copy; it can move onto this part when it is next worked on.
 */
public final class Attention {
    /** How a species weighs what it sees: a multiplier on the distance (lower catches the eye more), 0 or less for never. */
    @FunctionalInterface
    public interface Pull {
        double weight(Entity seen, double distance, boolean moving);
    }

    /** How quickly the eyes dart and the head follows (seconds; the crocodile's). */
    private static final double EYE_EASE = 0.05, HEAD_EASE = 0.6;

    private final double searchReach;
    private final Pull pull;
    @Nullable
    private Entity on;
    private int held, glanceIn;
    private float glance;
    /** Drawn state, the client's: where the eyes and head point now (degrees off straight ahead, its right positive). */
    public double eyeAim, headAim;
    private long nanos;

    public Attention(double searchReach, Pull pull) {
        this.searchReach = searchReach;
        this.pull = pull;
    }

    /** A player's pull as the crocodile's: within reach (less when crouching), spectators too, never one invisible. */
    public static boolean playerInReach(Player player, double distance, double reach, double sneakReach) {
        return (player.isSpectator() || !player.isInvisible()) && distance < (player.isCrouching() ? sneakReach : reach);
    }

    /** Call every tick on the client. */
    public void tick(Mob self) {
        if (held > 0) {
            held--;
        }
        if (self.tickCount % 5 != 0) {
            return;
        }
        if (on != null && (on.isRemoved() || !on.isAlive() || score(self, on, false) <= 0)) {
            on = null;
            held = 0;
        }
        RandomSource random = self.getRandom();
        if (on == null || held == 0) {
            Entity best = null;
            double bestScore = Double.MAX_VALUE;
            for (Entity seen : self.level().getEntities(self, self.getBoundingBox().inflate(searchReach), e -> e instanceof LivingEntity)) {
                double score = score(self, seen, seen != on);
                if (score <= 0) {
                    continue;
                }
                score *= seen == on ? 0.7 : 1.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = seen;
                }
            }
            on = best;
            held = 40 + random.nextInt(60);
        }
        if (on == null && (glanceIn -= 5) <= 0) {
            glance = random.nextFloat() < 0.4F ? 0.0F : (random.nextFloat() * 2.0F - 1.0F) * 80.0F;
            glanceIn = 40 + random.nextInt(80);
        }
    }

    /** Its distance times its pull, or 0 if it does not catch the eye (starting: it must be moving if the pull asks). */
    private double score(Mob self, Entity seen, boolean starting) {
        double d = self.distanceTo(seen);
        double dx = seen.getX() - seen.xo, dy = seen.getY() - seen.yo, dz = seen.getZ() - seen.zo;
        boolean moving = !starting || dx * dx + dy * dy + dz * dz > 0.0004;
        double w = pull.weight(seen, d, moving);
        return w <= 0 ? 0 : Math.max(0.01, d) * w;
    }

    /** What its eyes are on now (null: nothing, they wander, {@link #glance()}). */
    @Nullable
    public Entity on() {
        return on;
    }

    /** Where its eyes wander with nothing to watch (degrees off straight ahead, its right positive). */
    public float glance() {
        return glance;
    }

    /**
     * Moves {@link #eyeAim} and {@link #headAim} toward what it watches, seen from its eyes (eyeX, eyeZ: where its eyes
     * are in the world, yaw its body's turn): the eyes dart there, the head follows slowly, turning at most headTurn
     * degrees and only toward what is in front or to the side. Call once a frame from the model.
     */
    public void aim(float partialTick, double eyeX, double eyeZ, float yaw, double headTurn) {
        double rel = glance;
        if (on != null) {
            double tx = Mth.lerp(partialTick, on.xo, on.getX()), tz = Mth.lerp(partialTick, on.zo, on.getZ());
            rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(tz - eyeZ, tx - eyeX)) - 90.0 - yaw);
        }
        long now = System.nanoTime();
        double dt = nanos == 0 ? 1.0 : (now - nanos) / 1.0E9;
        nanos = now;
        double eyeK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / EYE_EASE);
        double headK = dt <= 0 ? 0 : dt > 0.5 ? 1.0 : 1.0 - Math.exp(-dt / HEAD_EASE);
        eyeAim = Mth.wrapDegrees(eyeAim + Mth.wrapDegrees(rel - eyeAim) * eyeK);
        double headWant = Math.abs(rel) < 120.0 ? Mth.clamp(rel, -headTurn, headTurn) : 0.0;
        headAim += (headWant - headAim) * headK;
    }
}
