package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

/**
 * The bull shark's mind ({@link BullSharkEntity}; the plan's steps 6 and 7, 2026-11-22; fun first, inspired by real bull
 * sharks; every number my pick unless said). Each goal only says where to swim and at what pace ({@link
 * BullSharkEntity#swimTo}) and starts the actions that have clips of their own; its swimming keeps it in deep water.
 * In order of weight: keep clear of hippos; meet a player or warn off a crocodile; hunt; spy hop; patrol.
 */
final class BullSharkGoals {
    /** How far it notices prey to hunt (blocks). */
    static final double HUNT_REACH = 20.0;
    /** The fin-out prowl ends this far from the prey (blocks, flat): the slow approach under water begins. */
    static final double CLOSE_FROM = 8.0;
    /** The burst begins with its jaws this near (blocks). */
    static final double BURST_FROM = 4.0;
    /** One hunt in this many at prey near the surface is a breach (a leap out at it); the prey within this of the surface (blocks). */
    static final int BREACH_ONE_IN = 5;
    static final double NEAR_SURFACE = 2.0;
    /** Strikes missed before it gives a hunt up a while. */
    static final int MISSES = 3;
    /** A player swimming this near in deep water is noticed (blocks). */
    static final double NOTICE = 14.0;
    /** After the bump, a player staying within this many blocks this long is warned off (blocks, ticks). */
    static final double STAY_NEAR = 8.0;
    static final int STAY_TICKS = 160;
    /** The bite on a player (health points: three hearts before the difficulty's own change). */
    static final float BITE_HURT = 6.0F;
    /** A crocodile this near in the water is warned off (blocks between their middles). */
    static final double CROC_NEAR = 8.0;
    /** A hippo this near sends it away until it is this far off (blocks). */
    static final double HIPPO_NEAR = 12.0, HIPPO_CLEAR = 20.0;
    /** A player out of the water (on the bank, in a boat) this near may get a spy hop, one check in this many (blocks; checks every 5 s). */
    static final double SPY_NOTICE = 20.0;
    static final int SPY_ONE_IN = 4;

    private BullSharkGoals() {
    }

    /** Whether a shark is free to take on something new: in the water, no clip of its own under way, nothing in its jaws. */
    static boolean free(BullSharkEntity shark) {
        return shark.isAlive() && shark.isInWater() && shark.action() == BullSharkEntity.ACT_NONE && !shark.isHolding();
    }

    /** Whether a player swims in water deep enough for it (not in creative, not a spectator). */
    static boolean inDeepWater(Player p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && swimming(p)
                && BullSharkEntity.depthAt(p.level(), p.getX(), p.getY() + 0.3, p.getZ()) >= BullSharkEntity.MIN_DEPTH;
    }

    /** Whether a player's body is in the water (read from the water at its waist, not a flag of its own last tick). */
    static boolean swimming(Player p) {
        return p.getVehicle() == null && p.level().getFluidState(net.minecraft.core.BlockPos.containing(p.getX(), p.getY() + 0.4, p.getZ()))
                .is(net.minecraft.tags.FluidTags.WATER);
    }

    /** Whether a player stands on something solid (a bank, a jetty). */
    static boolean standing(Player p) {
        net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(p.getX(), p.getY() - 0.1, p.getZ());
        return p.onGround() || !p.level().getBlockState(under).getCollisionShape(p.level(), under).isEmpty();
    }

    /**
     * A point to swim to in deep water about this far off (blocks), as near this way as it can find (yaw in degrees; any
     * way when {@code anyWay}), away from hippos, through water deep enough the whole way: the deepest of a few tries,
     * so it keeps to the middle of the channel; null if none.
     */
    @Nullable
    static Vec3 deepPoint(BullSharkEntity shark, float yaw, double near, double far, boolean anyWay, @Nullable Vec3 awayFrom) {
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        var hippos = shark.level().getEntitiesOfClass(HippoEntity.class, shark.getBoundingBox().inflate(far + HIPPO_CLEAR));
        for (int i = 0; i < 16; i++) {
            float off = anyWay ? shark.getRandom().nextFloat() * 360.0F : (float) (shark.getRandom().nextGaussian() * 50.0);
            float a = (yaw + off) * Mth.DEG_TO_RAD;
            double d = near + shark.getRandom().nextDouble() * (far - near);
            double x = shark.getX() - Mth.sin(a) * d, z = shark.getZ() + Mth.cos(a) * d;
            BullSharkEntity.Column c = BullSharkEntity.column(shark.level(), x, shark.getY() + 0.5 * shark.getScale(), z);
            if (c == null || c.depth() < BullSharkEntity.MIN_DEPTH) {
                continue;
            }
            Vec3 at = new Vec3(x, shark.cruiseY(c), z);
            boolean nearHippo = false;
            for (HippoEntity h : hippos) {
                nearHippo |= h.position().distanceToSqr(at) < HIPPO_CLEAR * HIPPO_CLEAR;
            }
            if (nearHippo || !shark.deepLine(at, true)) {
                continue;
            }
            double score = Math.min(c.depth(), 8) - Math.abs(off) / 40.0 + (awayFrom == null ? 0 : at.distanceTo(awayFrom) / 4.0);
            if (score > bestScore) {
                bestScore = score;
                best = at;
            }
        }
        return best;
    }

    /** Splashes at the surface over a point, sized to the shark. */
    static void splash(BullSharkEntity shark, Vec3 at, int count, float volume) {
        ServerLevel level = shark.server();
        if (level == null) {
            return;
        }
        BullSharkEntity.Column c = BullSharkEntity.column(level, at.x, at.y, at.z);
        double y = c == null ? at.y : c.surface();
        double size = shark.getScale();
        level.sendParticles(ParticleTypes.SPLASH, at.x, y, at.z, (int) (count * size), 0.8 * size, 0.1, 0.8 * size, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE, at.x, y - 0.6, at.z, count / 3, 0.6, 0.3, 0.6, 0.1);
        level.playSound(null, at.x, y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, volume, 0.7F + shark.getRandom().nextFloat() * 0.2F);
    }

    // --- patrol --------------------------------------------------------------------------------------------------------
    /**
     * Up and down the deep channel: a point 10 to 24 blocks on, mostly ahead, as deep as it finds, through deep water the
     * whole way; a new one on arriving, when shallow water stops it, or when it has hardly moved for 3 s. Slow (its idle
     * glide) by day and when fed; at its swimming pace when hungry in its hunting hours, looking for prey.
     */
    static class PatrolGoal extends Goal {
        private final BullSharkEntity shark;
        @Nullable
        private Vec3 target;
        private int t;
        private Vec3 lastPos = Vec3.ZERO;

        PatrolGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return free(shark);
        }

        @Override
        public boolean canContinueToUse() {
            return free(shark);
        }

        @Override
        public void start() {
            target = null;
            t = 0;
        }

        @Override
        public void tick() {
            t++;
            boolean stuck = t % 60 == 0 && shark.position().distanceToSqr(lastPos) < 1.0;
            if (t % 60 == 0) {
                lastPos = shark.position();
            }
            if (target == null || stuck || shark.swimBlocked() || flat(shark.position(), target) < 2.0 || t > 600) {
                boolean turnAround = stuck || shark.swimBlocked();
                target = deepPoint(shark, shark.getYRot() + (turnAround ? 180 : 0), 10, 24, turnAround, null);
                if (target == null) {
                    target = deepPoint(shark, shark.getYRot(), 4, 10, true, null);
                }
                t = 1;
            }
            if (target != null) {
                double pace = shark.isHungry() && shark.huntingHours() ? BullSharkEntity.SWIM : BullSharkEntity.IDLE;
                shark.swimTo(target.x, target.y, target.z, pace);
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- hunting -------------------------------------------------------------------------------------------------------
    /**
     * The hunt (dusk and night, hungry): it rises until its fin cuts the surface and prowls toward the prey at its prowl
     * pace; from {@link #CLOSE_FROM} blocks it sinks and comes on slowly; from {@link #BURST_FROM} it bursts at it, the
     * school scattering, and bites (the jaws shut on the clip's tick 8). A barb or tilapia is swallowed. A catfish is too
     * big: held across the jaws and shaken ({@code thrash}) until it tears, the front half left as remains. One hunt in
     * {@link #BREACH_ONE_IN} at prey near the surface ends in a breach instead: up from below and clean out of the water,
     * jaws wide, snapping it in the air. Three misses and it gives up half a minute. Fed, it is not hungry for about a day.
     */
    static class HuntGoal extends Goal {
        private enum Phase { PROWL, CLOSE, BURST, BITE, BREACH, THRASH, DONE }

        private final BullSharkEntity shark;
        @Nullable
        private LivingEntity prey;
        private Phase phase = Phase.DONE;
        private int t, left, misses, look;
        private boolean caught;

        HuntGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!shark.isHungry() || shark.huntWait > 0 || !free(shark) || ++look % 10 != 0 || !shark.huntingHours()) {
                return false;
            }
            prey = null;
            // the nearest prey over deep enough water that it can swim straight to (the line looked along for the
            // nearest few only: a school can be thirty fish)
            var seen = new java.util.ArrayList<>(shark.level().getEntitiesOfClass(LivingEntity.class,
                    shark.getBoundingBox().inflate(HUNT_REACH, 8.0, HUNT_REACH), e -> BullSharkEntity.isPrey(e) && e.distanceToSqr(shark) < HUNT_REACH * HUNT_REACH));
            seen.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(shark)));
            for (int i = 0; i < Math.min(3, seen.size()) && prey == null; i++) {
                LivingEntity e = seen.get(i);
                if (BullSharkEntity.depthAt(shark.level(), e.getX(), e.getY() + 0.2, e.getZ()) >= BullSharkEntity.MIN_DEPTH
                        && shark.deepLine(new Vec3(e.getX(), Math.min(shark.getY(), e.getY()), e.getZ()), false)) {
                    prey = e;
                }
            }
            return prey != null;
        }

        @Override
        public void start() {
            phase = Phase.PROWL;
            t = 0;
            left = 900;
            misses = 0;
            caught = false;
            shark.setProwling(true);
            shark.hunting = true;
        }

        @Override
        public boolean canContinueToUse() {
            return phase != Phase.DONE && shark.isAlive() && (shark.isInWater() || phase == Phase.BREACH);
        }

        @Override
        public boolean isInterruptable() {
            return phase == Phase.PROWL || phase == Phase.CLOSE;
        }

        @Override
        public void tick() {
            t++;
            switch (phase) {
                case PROWL -> prowl();
                case CLOSE -> close();
                case BURST -> burst();
                case BITE -> bite();
                case BREACH -> breach();
                case THRASH -> thrash();
                default -> { }
            }
        }

        private boolean lost() {
            return prey == null || !prey.isAlive() || prey.isRemoved() || !prey.isInWater() || prey.distanceToSqr(shark) > (HUNT_REACH + 8) * (HUNT_REACH + 8);
        }

        /** The fin out at the surface, at its prowl pace toward the prey; near it, a breach or the slow approach under water. */
        private void prowl() {
            if (lost() || --left <= 0) {
                giveUp(200);
                return;
            }
            BullSharkEntity.Column here = shark.columnHere();
            double y = here == null ? shark.getY() : shark.prowlY(here);
            shark.swimTo(prey.getX(), y, prey.getZ(), BullSharkEntity.PROWL);
            double flat = flat(shark.position(), prey.position());
            if (flat < CLOSE_FROM) {
                BullSharkEntity.Column at = BullSharkEntity.column(shark.level(), prey.getX(), prey.getY(), prey.getZ());
                boolean nearTop = at != null && at.surface() - prey.getY() < NEAR_SURFACE;
                if (nearTop && flat < 5.0 && (shark.breachNext || shark.getRandom().nextInt(BREACH_ONE_IN) == 0)) {
                    shark.breachNext = false;
                    startBreach();
                } else if (!nearTop || flat < 5.0) {
                    shark.setProwling(false);
                    phase = Phase.CLOSE;
                    t = 0;
                }
            }
        }

        /** Sunk to the prey's height, coming on slowly; from {@link #BURST_FROM} the burst. */
        private void close() {
            if (lost() || --left <= 0) {
                giveUp(200);
                return;
            }
            shark.swimAtWithJaws(prey, BullSharkEntity.SLOW);
            if (shark.jawDistance(prey) < BURST_FROM) {
                HuntTilapiaGoal.scatter(shark, prey);
                shark.playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 0.6F);
                phase = Phase.BURST;
                t = 0;
            } else if (flat(shark.position(), prey.position()) > CLOSE_FROM + 6) {
                shark.setProwling(true);
                phase = Phase.PROWL;
                t = 0;
            }
        }

        /** Flat out at the prey; its jaws near enough, the bite. */
        private void burst() {
            if (lost()) {
                giveUp(200);
                return;
            }
            shark.swimAtWithJaws(prey, BullSharkEntity.BURST);
            if (shark.jawDistance(prey) <= shark.biteReach()) {
                shark.startAction(BullSharkEntity.ACT_BITE);
                phase = Phase.BITE;
                t = 0;
            } else if (t > 30) {
                miss();
            }
        }

        /** The bite clip: the jaws shut on its tick 8 (after the blend); the prey caught if it is still in reach. */
        private void bite() {
            if (prey != null && !caught) {
                shark.swimAtWithJaws(prey, t < BullSharkEntity.BITE_HIT ? BullSharkEntity.SWIM : BullSharkEntity.IDLE);
            }
            if (t == BullSharkEntity.BITE_HIT) {
                shark.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.7F);
                boolean inReach = prey != null && !lost() && shark.jawDistance(prey) <= shark.biteReach() + 0.6 * shark.getScale();
                if (shark.strikeLuck > 0 || inReach && shark.strikeLuck == 0) {
                    take();
                }
            }
            if (t >= BullSharkEntity.BITE_TICKS) {
                shark.endAction();
                if (shark.isHolding()) {
                    startThrash();
                } else if (caught) {
                    phase = Phase.DONE;
                } else {
                    miss();
                }
            }
        }

        private void startBreach() {
            shark.startAction(BullSharkEntity.ACT_BREACH);
            phase = Phase.BREACH;
            t = 0;
        }

        /**
         * The breach: it keeps its place under the prey, drifting at it, while the clip throws its body up out of the
         * water and twists it in the air; a splash as it breaks the surface and as it falls back; the jaws snap on the prey
         * if it is still near its head then.
         */
        private void breach() {
            if (prey != null && !caught && !lost()) {
                BullSharkEntity.Column here = shark.columnHere();
                double y = here == null ? shark.getY() : shark.prowlY(here);
                shark.swimTo(prey.getX(), y, prey.getZ(), BullSharkEntity.IDLE);
            }
            if (t == BullSharkEntity.BREACH_OUT) {
                splash(shark, shark.position().add(shark.heading().scale(0.5 * shark.getScale())), 30, 1.2F);
                shark.playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2F, 0.6F);
            }
            if (t == BullSharkEntity.BREACH_SNAP) {
                shark.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.1F, 0.7F);
                Vec3 head = shark.position().add(shark.heading().scale(1.2 * shark.getScale()));
                boolean inReach = prey != null && !lost() && flat(head, prey.position()) < 3.0 * shark.getScale()
                        && Math.abs(prey.getY() - shark.getY()) < 3.5 * shark.getScale();
                if (shark.strikeLuck > 0 || inReach && shark.strikeLuck == 0) {
                    take();
                }
            }
            if (t == BullSharkEntity.BREACH_IN) {
                splash(shark, shark.position(), 40, 1.4F);
            }
            if (t >= BullSharkEntity.BREACH_TICKS) {
                shark.endAction();
                if (shark.isHolding()) {
                    startThrash();
                } else if (caught) {
                    phase = Phase.DONE;
                } else {
                    miss();
                }
            }
        }

        /** Caught: a barb or tilapia swallowed there and then, a catfish held across the jaws. */
        private void take() {
            caught = true;
            ServerLevel level = shark.server();
            Vec3 m = shark.jawPoint();
            if (prey instanceof CatfishEntity) {
                shark.hold(prey.getScale());
                prey.discard();
                return;
            }
            if (level != null) {
                level.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 10, 0.3, 0.2, 0.3, 0.05);
                level.playSound(null, m.x, m.y, m.z, SoundEvents.DOLPHIN_EAT, SoundSource.NEUTRAL, 0.9F, 0.5F);
                com.antcolonies.savanna.block.ScrapsBlock.leave(level, m);
            }
            prey.discard();
            shark.fed();
        }

        private void startThrash() {
            shark.startAction(BullSharkEntity.ACT_THRASH);
            phase = Phase.THRASH;
            t = 0;
        }

        /**
         * The catfish held across the jaws, shaken side to side (the clip's sawing head-shake) until it tears: the shark
         * swallows the back half, the front half drifts down as remains.
         */
        private void thrash() {
            BullSharkEntity.Column here = shark.columnHere();
            if (here != null) {
                shark.swimTo(shark.getX() + shark.heading().x, shark.cruiseY(here), shark.getZ() + shark.heading().z, BullSharkEntity.IDLE * 0.5);
            }
            ServerLevel level = shark.server();
            Vec3 m = shark.jawPoint();
            if (level != null && t % 6 == 3) {
                level.sendParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 6, 0.5, 0.2, 0.5, 0.08);
                if (here != null && here.surface() - m.y < 1.2) {
                    splash(shark, m, 10, 0.5F);
                }
            }
            if (t >= BullSharkEntity.THRASH_TICKS) {
                if (level != null) {
                    float size = shark.heldSize();
                    RemainsEntity left = RemainsEntity.leave(level, RemainsEntity.Kind.CATFISH_TORN, m, shark.getYRot() + 90.0F, size);
                    if (left != null) {
                        left.setDeltaMovement((shark.getRandom().nextDouble() - 0.5) * 0.08, 0.02, (shark.getRandom().nextDouble() - 0.5) * 0.08);
                        left.bite(shark);       // the tear, with its puff of meat bits
                        left.setBitesLeft(RemainsEntity.Kind.CATFISH_TORN.bites);
                    }
                    level.playSound(null, m.x, m.y, m.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.9F, 0.7F);
                    com.antcolonies.savanna.block.ScrapsBlock.leave(level, m);
                }
                shark.letGo();
                shark.endAction();
                shark.fed();
                phase = Phase.DONE;
            }
        }

        private void miss() {
            if (++misses >= MISSES) {
                giveUp(600);
                return;
            }
            phase = lost() ? Phase.DONE : Phase.CLOSE;
            t = 0;
        }

        private void giveUp(int wait) {
            shark.huntWait = wait;
            phase = Phase.DONE;
        }

        @Override
        public void stop() {
            shark.hunting = false;
            shark.setProwling(false);
            if (shark.action() == BullSharkEntity.ACT_BITE || shark.action() == BullSharkEntity.ACT_BREACH || shark.action() == BullSharkEntity.ACT_THRASH) {
                shark.endAction();
            }
            shark.letGo();
            prey = null;
            phase = Phase.DONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // --- meeting a player ----------------------------------------------------------------------------------------------
    /**
     * A player swimming in deep water (real bull sharks bump what they do not know before they bite it): it swims up and
     * bumps them with its snout (no harm), turns off and circles; a player who stays near after that, or who hits it, gets
     * the hunched warning ({@code threat_display}: back arched, fins down, jaws snapping), then a burst and a bite; then it
     * swims off and leaves players be half a minute. It never follows into water under {@link BullSharkEntity#MIN_DEPTH}
     * deep: a player who gets out of the deep water is let go.
     */
    static class MeetGoal extends Goal {
        private enum Phase { COME, BUMP, OFF, WATCH, THREAT, BURST, BITE, AWAY, DONE }

        private final BullSharkEntity shark;
        @Nullable
        private Player player;
        private Phase phase = Phase.DONE;
        private int t, near, look;
        private double closest = 99;
        @Nullable
        private Vec3 spot;

        MeetGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!free(shark) || ++look % 10 != 0) {
                return false;
            }
            if (shark.provokedBy != null && inDeepWater(shark.provokedBy) && shark.provokedBy.distanceToSqr(shark) < 24 * 24) {
                player = shark.provokedBy;
                phase = Phase.THREAT;
                return true;
            }
            if (shark.meetWait > 0) {
                return false;
            }
            player = null;
            double best = NOTICE * NOTICE;
            for (Player p : shark.level().players()) {
                double d = p.distanceToSqr(shark);
                if (d < best && inDeepWater(p) && shark.deepLine(new Vec3(p.getX(), Math.min(shark.getY(), p.getY()), p.getZ()), false)) {
                    best = d;
                    player = p;
                }
            }
            if (player != null) {
                phase = Phase.COME;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            t = 0;
            near = 0;
            closest = 99;
            shark.note("meet " + phase);
            if (phase == Phase.THREAT) {
                shark.startAction(BullSharkEntity.ACT_THREAT);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return phase != Phase.DONE && shark.isAlive() && shark.isInWater() && !shark.isHolding();
        }

        @Override
        public void tick() {
            t++;
            if (player == null || !player.isAlive() || player.isRemoved()) {
                end(200);
                return;
            }
            // hit while it was only curious: straight to the warning
            if ((phase == Phase.COME || phase == Phase.OFF || phase == Phase.WATCH) && shark.provokedBy == player && inDeepWater(player)) {
                go(Phase.THREAT);
                shark.startAction(BullSharkEntity.ACT_THREAT);
            }
            switch (phase) {
                case COME -> {
                    if (!inDeepWater(player) || t > 300) {
                        end(300);
                        return;
                    }
                    shark.swimAtWithJaws(player, BullSharkEntity.SWIM * 0.8);
                    if (shark.jawDistance(player) < 1.4 * shark.getScale()) {
                        shark.startAction(BullSharkEntity.ACT_BUMP);
                        go(Phase.BUMP);
                    }
                }
                case BUMP -> {
                    if (t < BullSharkEntity.BUMP_HIT) {
                        shark.swimAtWithJaws(player, BullSharkEntity.IDLE);
                    }
                    if (t == BullSharkEntity.BUMP_HIT && shark.jawDistance(player) < 2.2 * shark.getScale()) {
                        Vec3 h = shark.heading();
                        player.push(h.x * 0.6, 0.15, h.z * 0.6);
                        player.hurtMarked = true;
                        shark.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.5F);
                    }
                    if (t >= BullSharkEntity.BUMP_TICKS) {
                        shark.endAction();
                        spot = deepPoint(shark, shark.getYRot() + 120, 8, 12, false, player.position());
                        go(Phase.OFF);
                    }
                }
                case OFF -> {
                    if (spot != null) {
                        shark.swimTo(spot.x, spot.y, spot.z, BullSharkEntity.SWIM * 0.7);
                    }
                    if (t > 60 || spot == null || flat(shark.position(), spot) < 2.0) {
                        go(Phase.WATCH);
                    }
                }
                case WATCH -> {
                    // round the player at a distance, slowly; a player who stays near is warned off
                    if (!inDeepWater(player) || player.distanceToSqr(shark) > 20 * 20 || t > 400) {
                        end(600);
                        return;
                    }
                    if (player.distanceToSqr(shark) < STAY_NEAR * STAY_NEAR) {
                        near++;
                    }
                    if (near >= STAY_TICKS) {
                        go(Phase.THREAT);
                        shark.startAction(BullSharkEntity.ACT_THREAT);
                        return;
                    }
                    if (spot == null || t % 60 == 1 || flat(shark.position(), spot) < 2.0) {
                        float side = (float) (Mth.atan2(shark.getZ() - player.getZ(), shark.getX() - player.getX()) * Mth.RAD_TO_DEG) + 50.0F;
                        double x = player.getX() + Mth.cos(side * Mth.DEG_TO_RAD) * 7, z = player.getZ() + Mth.sin(side * Mth.DEG_TO_RAD) * 7;
                        BullSharkEntity.Column c = BullSharkEntity.column(shark.level(), x, shark.getY() + 0.5, z);
                        spot = c != null && c.depth() >= BullSharkEntity.MIN_DEPTH ? new Vec3(x, shark.cruiseY(c), z) : null;
                    }
                    if (spot != null) {
                        shark.swimTo(spot.x, spot.y, spot.z, BullSharkEntity.IDLE * 1.5);
                    }
                }
                case THREAT -> {
                    // hunched at the player, levelling with them for the charge
                    shark.face(player.position());
                    double level = player.getY() + player.getBbHeight() * 0.5 - BullSharkEntity.JAW_UP * shark.getScale();
                    shark.swimTo(shark.getX() + shark.heading().x * 0.5, level, shark.getZ() + shark.heading().z * 0.5, BullSharkEntity.IDLE * 0.5);
                    if (t >= BullSharkEntity.THREAT_TICKS) {
                        shark.endAction();
                        if (inDeepWater(player)) {
                            shark.playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 0.6F);
                            go(Phase.BURST);
                        } else {
                            end(600);
                        }
                    }
                }
                case BURST -> {
                    if (!inDeepWater(player) || t > 60) {
                        go(Phase.AWAY);
                        return;
                    }
                    shark.swimAtWithJaws(player, BullSharkEntity.BURST);
                    closest = Math.min(closest, shark.jawDistance(player));
                    if (shark.jawDistance(player) <= shark.biteReach()) {
                        shark.startAction(BullSharkEntity.ACT_BITE);
                        go(Phase.BITE);
                    }
                }
                case BITE -> {
                    if (t < BullSharkEntity.BITE_HIT) {
                        shark.swimAtWithJaws(player, BullSharkEntity.SWIM);
                    }
                    if (t == BullSharkEntity.BITE_HIT) {
                        shark.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.7F);
                        if (shark.jawDistance(player) <= shark.biteReach() + 0.6 * shark.getScale()) {
                            player.hurt(shark.damageSources().mobAttack(shark), BITE_HURT);
                        }
                    }
                    if (t >= BullSharkEntity.BITE_TICKS) {
                        shark.endAction();
                        go(Phase.AWAY);
                    }
                }
                case AWAY -> {
                    if (t == 1) {
                        spot = deepPoint(shark, shark.getYRot() + 180, 10, 16, false, player.position());
                    }
                    if (spot != null) {
                        shark.swimTo(spot.x, spot.y, spot.z, BullSharkEntity.SWIM);
                    }
                    if (t > 80 || spot == null) {
                        if (shark.provokedBy == player) {
                            shark.provokedBy = null;
                            shark.provokedFor = 0;
                        }
                        end(600);
                    }
                }
                default -> { }
            }
        }

        private void go(Phase next) {
            phase = next;
            t = 0;
            shark.note("meet " + next + " (jaws " + String.format("%.1f", shark.jawDistance(player)) + ")");
        }

        private void end(int wait) {
            shark.meetWait = wait;
            shark.note("meet ended in " + phase + ", jaws came within " + String.format("%.2f", closest));
            phase = Phase.DONE;
        }

        @Override
        public void stop() {
            byte a = shark.action();
            if (a == BullSharkEntity.ACT_BUMP || a == BullSharkEntity.ACT_THREAT || a == BullSharkEntity.ACT_BITE) {
                shark.endAction();
            }
            if (phase != Phase.DONE) {
                shark.meetWait = Math.max(shark.meetWait, 200);
            }
            player = null;
            phase = Phase.DONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // --- a crocodile ---------------------------------------------------------------------------------------------------
    /** A grown crocodile close in the water: the hunched warning turned on it, then it swims on (a minute before the next). */
    static class CrocodileGoal extends Goal {
        private final BullSharkEntity shark;
        @Nullable
        private CrocodileEntity croc;
        private int look;

        CrocodileGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (shark.crocWait > 0 || !free(shark) || ++look % 20 != 0) {
                return false;
            }
            croc = null;
            double best = CROC_NEAR * CROC_NEAR;
            for (CrocodileEntity c : shark.level().getEntitiesOfClass(CrocodileEntity.class, shark.getBoundingBox().inflate(CROC_NEAR, 4.0, CROC_NEAR))) {
                double d = c.distanceToSqr(shark);
                if (d < best && c.isAlive() && c.isInWater() && c.life().stage().grown()) {
                    best = d;
                    croc = c;
                }
            }
            return croc != null;
        }

        @Override
        public void start() {
            shark.startAction(BullSharkEntity.ACT_THREAT);
            if (croc != null) {
                croc.answerShark(shark);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return shark.action() == BullSharkEntity.ACT_THREAT && shark.actionTicks() < BullSharkEntity.THREAT_TICKS && shark.isInWater();
        }

        @Override
        public void tick() {
            if (croc != null) {
                shark.face(croc.position());
            }
            shark.swimTo(shark.getX() + shark.heading().x * 0.5, shark.getY(), shark.getZ() + shark.heading().z * 0.5, BullSharkEntity.IDLE * 0.5);
        }

        @Override
        public void stop() {
            if (shark.action() == BullSharkEntity.ACT_THREAT) {
                shark.endAction();
            }
            shark.crocWait = 1200;
            croc = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // --- hippos --------------------------------------------------------------------------------------------------------
    /** A hippo near in the water: away through the deep water until it is {@link #HIPPO_CLEAR} blocks off. */
    static class KeepClearGoal extends Goal {
        private final BullSharkEntity shark;
        @Nullable
        private HippoEntity hippo;
        @Nullable
        private Vec3 away;
        private int t, look;

        KeepClearGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!free(shark) || ++look % 20 != 0) {
                return false;
            }
            hippo = shark.level().getEntitiesOfClass(HippoEntity.class, shark.getBoundingBox().inflate(HIPPO_NEAR, 6.0, HIPPO_NEAR),
                    h -> h.isAlive() && h.isInWater()).stream().findFirst().orElse(null);
            return hippo != null;
        }

        @Override
        public void start() {
            t = 0;
            pickAway();
        }

        private void pickAway() {
            float from = (float) (Mth.atan2(shark.getZ() - hippo.getZ(), shark.getX() - hippo.getX()) * Mth.RAD_TO_DEG) - 90.0F;
            away = deepPoint(shark, from, 10, 22, false, hippo.position());
        }

        @Override
        public boolean canContinueToUse() {
            return hippo != null && hippo.isAlive() && t < 300 && shark.isInWater() && !shark.isHolding()
                    && hippo.distanceToSqr(shark) < HIPPO_CLEAR * HIPPO_CLEAR;
        }

        @Override
        public void tick() {
            t++;
            if (away == null || flat(shark.position(), away) < 2.0 || shark.swimBlocked()) {
                pickAway();
            }
            if (away != null) {
                shark.swimTo(away.x, away.y, away.z, BullSharkEntity.SWIM);
            }
        }

        @Override
        public void stop() {
            hippo = null;
            away = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // --- the spy hop ---------------------------------------------------------------------------------------------------
    /**
     * Now and then a player on the bank or in a boat near the channel catches its eye: it swims up near them (water at
     * least {@link BullSharkEntity#SPAWN_DEPTH} deep, for the tail swinging down as the head comes up), faces them and lifts
     * its head out to look ({@code spy_hop}), then slips back under; a few minutes before the next.
     */
    static class SpyHopGoal extends Goal {
        private final BullSharkEntity shark;
        @Nullable
        private Player watched;
        @Nullable
        private Vec3 spot;
        private int t, look;
        private boolean hopping;

        SpyHopGoal(BullSharkEntity shark) {
            this.shark = shark;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // (asked for by a test or a film: at once, no dice)
            boolean asked = shark.spyNext;
            if (shark.spyWait > 0 || !free(shark) || ++look % 100 != 0 && !asked || !asked && shark.getRandom().nextInt(SPY_ONE_IN) != 0) {
                return false;
            }
            watched = null;
            for (Player p : shark.level().players()) {
                if (!p.isSpectator() && p.isAlive() && !swimming(p) && (standing(p) || p.getVehicle() instanceof Boat)
                        && flat(p.position(), shark.position()) < SPY_NOTICE && Math.abs(p.getY() - shark.getY()) < 8) {
                    watched = p;
                    break;
                }
            }
            if (watched == null) {
                return false;
            }
            spot = lookoutNear(watched);
            if (spot != null) {
                shark.spyNext = false;
            }
            return spot != null;
        }

        /** Deep water 3 to 6 blocks from the player that it can swim straight to, or null. */
        @Nullable
        private Vec3 lookoutNear(Player p) {
            for (int i = 0; i < 16; i++) {
                float a = shark.getRandom().nextFloat() * Mth.TWO_PI;
                double d = 3 + shark.getRandom().nextDouble() * 3;
                double x = p.getX() + Mth.cos(a) * d, z = p.getZ() + Mth.sin(a) * d;
                BullSharkEntity.Column c = BullSharkEntity.column(shark.level(), x, shark.getY() + 0.5, z);
                if (c == null || c.depth() < BullSharkEntity.SPAWN_DEPTH) {
                    continue;
                }
                Vec3 at = new Vec3(x, shark.prowlY(c), z);
                if (shark.deepLine(at, true)) {
                    return at;
                }
            }
            return null;
        }

        @Override
        public void start() {
            t = 0;
            hopping = false;
        }

        @Override
        public boolean canContinueToUse() {
            return watched != null && watched.isAlive() && shark.isInWater() && !shark.isHolding()
                    && (hopping ? shark.action() == BullSharkEntity.ACT_SPY : t < 200);
        }

        @Override
        public void tick() {
            t++;
            if (hopping) {
                shark.face(watched.position());
                shark.swimTo(shark.getX() + shark.heading().x * 0.3, shark.getY(), shark.getZ() + shark.heading().z * 0.3, BullSharkEntity.IDLE * 0.3);
                if (shark.actionTicks() >= BullSharkEntity.SPY_TICKS) {
                    shark.endAction();
                }
                return;
            }
            shark.swimTo(spot.x, spot.y, spot.z, BullSharkEntity.SWIM * 0.8);
            if (flat(shark.position(), spot) < 1.5 && Math.abs(shark.getY() - spot.y) < 0.6) {
                hopping = true;
                shark.startAction(BullSharkEntity.ACT_SPY);
                splash(shark, shark.jawPoint(), 8, 0.4F);
            }
        }

        @Override
        public void stop() {
            if (shark.action() == BullSharkEntity.ACT_SPY) {
                shark.endAction();
            }
            shark.spyWait = 2400 + shark.getRandom().nextInt(2400);
            watched = null;
            spot = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }
}
