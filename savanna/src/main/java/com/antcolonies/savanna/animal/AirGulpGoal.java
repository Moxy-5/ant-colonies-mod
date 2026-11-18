package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The catfish comes up for a gulp of air (the user, 2026-11-18, "catfish coming up to gulp air to breathe"; real
 * air-breathing catfish do this in warm, still water; fun first, every number my pick). Every 2 to 5 minutes, when it is
 * not hunting or eating and open air lies straight above it within {@link #LOOK_UP} blocks, it swims up nose first
 * (the drawn body tips up, {@link CatfishEntity#swimPitch}), holds with its snout just out of the water, opens wide and
 * snaps a gulp of air with a splash (its clip "gulp"), then dives a couple of blocks, letting bubbles out of its gills.
 * Drawn and heard only: no air meter (the game's fish breathe in water by themselves). It never cuts into a hunt.
 */
public class AirGulpGoal extends Goal {
    /** How far above it looks for the surface (blocks). */
    static final int LOOK_UP = 12;
    /** Ticks between gulps: 2 to 5 minutes; and the wait when no surface is in reach. */
    static final int EVERY_MIN = 2400, EVERY_MORE = 3600, RETRY = 200;
    /**
     * It does not come up with a player (not a spectator) this near (blocks). The gulp outranks the fish's own fleeing
     * from players within 8 blocks (Minecraft's, for every fish), so a player watching from the bank sees it.
     */
    static final double SHY_WITHIN = 3.0;
    /** The rise may take this long before it is given up (ticks). */
    static final int RISE_TICKS = 200;
    /** The dive after the gulp (ticks). */
    static final int DIVE_TICKS = 24;
    /** The ticks of the gulp the mouth breaks open (the splash) and snaps shut (the clip's 0.4 and 0.78 s, plus its lag). */
    static final int SPLASH_AT = 8 + CatfishEntity.CLIP_LAG, SNAP_AT = 16 + CatfishEntity.CLIP_LAG;

    private enum Phase { RISE, GULP, DIVE }

    private final CatfishEntity fish;
    private Phase phase = Phase.RISE;
    private Vec3 at = Vec3.ZERO;
    private int ticks;

    public AirGulpGoal(CatfishEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (fish.gulpIn > 0 || !fish.isInWater() || fish.isHolding() || fish.isStriking() || fish.isHunting()
                || fish.getRandom().nextInt(10) != 0) {
            return false;
        }
        if (fish.level().getNearestPlayer(fish.getX(), fish.getY(), fish.getZ(), SHY_WITHIN,
                net.minecraft.world.entity.EntitySelector.NO_SPECTATORS) != null) {
            fish.gulpIn = RETRY;        // a player right by it: later
            return false;
        }
        Vec3 surface = surfaceAbove();
        if (surface == null) {
            fish.gulpIn = RETRY;
            return false;
        }
        at = surface;
        return true;
    }

    /**
     * Where it holds for the gulp: the top water block straight above it with air over it, the body a little under the
     * surface so that, tipped nose up, the snout breaks it; or null with no open air within {@link #LOOK_UP}.
     */
    private Vec3 surfaceAbove() {
        BlockPos.MutableBlockPos pos = fish.blockPosition().mutable();
        for (int i = 0; i <= LOOK_UP; i++) {
            BlockPos above = pos.above();
            if (!fish.level().getFluidState(above).is(FluidTags.WATER)) {
                if (!fish.level().getFluidState(pos).is(FluidTags.WATER) || !fish.level().getBlockState(above).getCollisionShape(fish.level(), above).isEmpty()) {
                    return null;
                }
                double top = pos.getY() + fish.level().getFluidState(pos).getHeight(fish.level(), pos);
                return new Vec3(fish.getX(), top - 0.42 * fish.getScale(), fish.getZ());
            }
            pos.move(0, 1, 0);
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!fish.isAlive() || !fish.isInWater() || fish.isHolding() || fish.isStriking() || fish.hurtTime > 0) {
            return false;       // hit: it bolts (the fish's own panic)
        }
        return switch (phase) {
            case RISE -> ticks < RISE_TICKS;
            case GULP -> ticks < CatfishEntity.GULP_TICKS;
            case DIVE -> ticks < DIVE_TICKS;
        };
    }

    @Override
    public void start() {
        phase = Phase.RISE;
        ticks = 0;
    }

    @Override
    public void stop() {
        fish.setGulping(false);
        fish.gulpIn = EVERY_MIN + fish.getRandom().nextInt(EVERY_MORE);
        fish.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ticks++;
        switch (phase) {
            case RISE -> {
                double off = fish.position().distanceTo(at);
                if (off < 0.35) {
                    phase = Phase.GULP;
                    ticks = 0;
                    fish.getNavigation().stop();
                    fish.setGulping(true);
                    return;
                }
                // straight up, a little faster once near (the last push that lifts the snout out)
                Vec3 to = at.subtract(fish.position());
                double speed = off < 2.0 ? 0.06 : 0.045;
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.6).add(to.normalize().scale(speed)));
            }
            case GULP -> {
                // held at the surface while the clip plays: nudged back to the spot, no drift
                fish.setDeltaMovement(at.subtract(fish.position()).scale(0.25));
                if (fish.level() instanceof ServerLevel server) {
                    Vec3 m = snout();
                    if (ticks == SPLASH_AT) {
                        server.sendParticles(ParticleTypes.SPLASH, m.x, at.y + 0.42 * fish.getScale(), m.z, 10, 0.18, 0.02, 0.18, 0.08);
                        server.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.35F, 1.5F);
                    } else if (ticks == SNAP_AT) {
                        server.sendParticles(ParticleTypes.BUBBLE, m.x, m.y - 0.2, m.z, 6, 0.1, 0.05, 0.1, 0.02);
                        server.playSound(null, m.x, m.y, m.z, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.NEUTRAL, 0.9F, 0.7F);
                    }
                }
                if (ticks >= CatfishEntity.GULP_TICKS - 1) {
                    phase = Phase.DIVE;
                    ticks = 0;
                    fish.setGulping(false);
                }
            }
            case DIVE -> {
                // down and ahead, letting out bubbles from the gills
                float yaw = fish.getYRot() * Mth.DEG_TO_RAD;
                fish.setDeltaMovement(fish.getDeltaMovement().scale(0.7).add(-Mth.sin(yaw) * 0.03, -0.05, Mth.cos(yaw) * 0.03));
                if (ticks % 4 == 1 && ticks < 16 && fish.level() instanceof ServerLevel server) {
                    float back = 0.35F * fish.getScale();
                    server.sendParticles(ParticleTypes.BUBBLE, fish.getX() - Mth.sin(yaw) * back, fish.getY() + 0.2 * fish.getScale(),
                            fish.getZ() + Mth.cos(yaw) * back, 3, 0.12, 0.05, 0.12, 0.02);
                }
            }
        }
    }

    /** The snout, tipped up out of the water. */
    private Vec3 snout() {
        Vec3 m = fish.mouth();
        return new Vec3(m.x, at.y + 0.5 * fish.getScale(), m.z);
    }
}
