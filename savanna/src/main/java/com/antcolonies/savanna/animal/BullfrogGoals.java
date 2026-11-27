package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.antcolonies.savanna.registry.SavannaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.LongJumpUtil;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The bullfrog's mind (prey plan step 3, 2026-11-20; every number my pick unless marked): its goals, from the most
 * pressing. Each only decides what to do and where to go; the hops and swimming are its legs'
 * ({@link BullfrogMoveControl}), the clips its body's.
 */
final class BullfrogGoals {
    private BullfrogGoals() {
    }

    /** How far its lunge reaches from its mouth, and its tongue (blocks at a male's size; the tongue the user's 2). */
    static final double LUNGE_REACH = 1.5, TONGUE_REACH = 2.0;
    /**
     * The creep (the polish's step 4; my picks): prey on land seen within this many blocks of its mouth (at a male's
     * size) but past the lunge's reach gets a slow creep first; it gives up after this many ticks (the creep covers
     * about 1.7 blocks in that time) or if the prey gets this much farther than where it started creeping.
     */
    static final double STALK_REACH = 3.0, STALK_LOSE = 1.5;
    static final int STALK_TICKS = 400;
    /**
     * The hunt (the user, 2026-11-20: "hunting the snails underwater", frogs too slow when hunting; my picks): a hungry
     * frog goes after a snail or a young crab it sees within this many blocks, on land or on the bed under the water
     * (diving to it), for at most this many ticks (15 s), then rests from hunting this many ticks (5 s) before it looks
     * again. Barbs are too quick to chase: they are only ambushed as they pass.
     */
    static final double HUNT_REACH = 10.0;
    static final int HUNT_TICKS = 300, HUNT_REST = 100;
    /** How near a monitor or a grown crocodile sends it leaping for the water, and how far it looks for water. */
    static final double DANGER_NEAR = 5.0;
    static final int WATER_REACH = 6;
    /** How near a player makes it puff up, and how near (after puffing a while) makes it lunge and bite (blocks). */
    static final double PUFF_NEAR = 3.0;
    /**
     * The bite's reach (blocks from its mouth's side of its box to the foe's edge: a male of size 1 reaches 1.8, a smaller
     * frog less), and how far inside it the lunge starts (the lunge carries it little, least in the water: started at the
     * old fixed 1.6 a female, reaching 1.5, always fell short, and a father's bites at a monitor half did).
     */
    static final double BITE_REACH = 1.0, BITE_REACH_MORE = 0.8, LUNGE_FROM = 0.3;

    static double biteReach(BullfrogEntity frog) {
        return BITE_REACH * frog.getScale() + BITE_REACH_MORE;
    }
    /** The bite (hearts x 2: the plan's 1 heart). */
    static final float BITE = 2.0F;
    /** One flick in four misses (the plan). */
    static final float TONGUE_MISS = 0.25F;
    /** The tongue clip: the pad at the fly on this tick (0.15 s), back in the mouth on this one, the clip over on this. */
    static final int TONGUE_OUT = 3, TONGUE_BACK = 7, TONGUE_END = 14;
    /**
     * The aim before a flick (ticks: eyes on the fly, the head tipping up to it), the wait between one flick and the next
     * while flies stay in reach (20 to 40 ticks: 1 to 2 s), and the most flicks in one go (my picks).
     */
    static final int AIM_TICKS = 8, FLICK_GAP_MIN = 12, FLICK_GAP_MORE = 20, MOST_FLICKS = 8;
    /** The lunge: the push on this tick, the jaws shut on this one, the clip (0.8 s) over on this. */
    static final int LUNGE_PUSH = 2, LUNGE_SNAP = 6, LUNGE_END = 16;
    /** Digging in and out (the clips' 3 s and 1.2 s). */
    static final int DIG_IN_TICKS = 60, DIG_OUT_TICKS = 24;
    /** The call (the clip's 3 s), and the wait before the next (10 to 30 s). */
    static final int CALL_TICKS = 60, CALL_REST_MIN = 200, CALL_REST_MORE = 400;
    /** How far a call is answered (blocks). */
    static final double ANSWER_REACH = 24.0;

    static void register(BullfrogEntity frog, GoalSelector goals) {
        goals.addGoal(0, new EscapeGoal(frog));
        goals.addGoal(1, new LungeGoal(frog));
        goals.addGoal(1, new TongueGoal(frog));
        goals.addGoal(1, new com.antcolonies.animal.SeekMateGoal<>(frog, 1.0));     // a female to a calling male
        goals.addGoal(2, new ThreatGoal(frog));
        // the father by his eggs and school (the life kit's guard; who comes near them is ThreatGoal's to see off)
        goals.addGoal(3, new com.antcolonies.animal.GuardPlaceGoal(frog, frog::guardSpot, BullfrogEntity.GUARD_STAY, 0.0, e -> false, 1.0));
        goals.addGoal(4, new DigGoal(frog));
        goals.addGoal(5, new CallGoal(frog));
        goals.addGoal(6, new ToCloudGoal(frog));
        goals.addGoal(7, new WanderGoal(frog));
    }

    // --- shared looks -------------------------------------------------------------------------------------------------

    /**
     * About once every this many ticks, by chance. (Minecraft looks at a mob's idle goals only every other tick, so a
     * check on the tick number ("every 10th tick") can fall on ticks it never looks at; a chance cannot.)
     */
    static boolean now(BullfrogEntity frog, int ticks) {
        return frog.getRandom().nextInt(Math.max(1, ticks / 2)) == 0;
    }

    /** Nothing solid between its mouth and this (the bank's own edge does not hide water right under its nose). */
    static boolean clearFromMouth(BullfrogEntity frog, Vec3 to) {
        return frog.level().clip(new ClipContext(frog.mouth(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, frog)).getType() == HitResult.Type.MISS;
    }

    /** A monitor or a grown crocodile near it, the danger it leaps into the water from. */
    @Nullable
    static LivingEntity danger(BullfrogEntity frog, double within) {
        List<LivingEntity> near = frog.level().getEntitiesOfClass(LivingEntity.class, frog.getBoundingBox().inflate(within, 3.0, within),
                e -> e.isAlive() && (e instanceof MonitorEntity && frog.guardSpot() == null || e instanceof CrocodileEntity c && c.life().stage().grown()));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : near) {
            double d = e.distanceToSqr(frog);
            if (d < bestD && d < within * within) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** A player near enough to bother it (not in creative or spectator mode). */
    @Nullable
    static Player playerNear(BullfrogEntity frog, double within) {
        Player p = frog.level().getNearestPlayer(frog, within);
        return p != null && !p.isCreative() && !p.isSpectator() && p.isAlive() ? p : null;
    }

    /** The nearest water's top (a water block with air over it) within this reach, the farther from a danger the better. */
    @Nullable
    static BlockPos nearestWater(BullfrogEntity frog, int reach, @Nullable Vec3 awayFrom) {
        Level level = frog.level();
        BlockPos home = frog.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-reach, -3, -reach), home.offset(reach, 1, reach))) {
            if (!level.getFluidState(p).is(FluidTags.WATER) || !level.getBlockState(p.above()).isAir()) {
                continue;
            }
            double d = Math.sqrt(p.distSqr(home));
            if (d > reach) {
                continue;
            }
            double score = d - (awayFrom == null ? 0 : 0.6 * Math.sqrt(awayFrom.distanceToSqr(Vec3.atCenterOf(p))));
            if (score < bestScore) {
                best = p.immutable();
                bestScore = score;
            }
        }
        return best;
    }

    /** Turns its body toward a point, at most this much a tick; returns how far off it still faces (degrees). */
    static float turnTo(BullfrogEntity frog, Vec3 at, float most) {
        float want = (float) (Mth.atan2(at.z - frog.getZ(), at.x - frog.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float now = Mth.approachDegrees(frog.getYRot(), want, most);
        frog.setYRot(now);
        frog.yBodyRot = now;
        frog.yHeadRot = now;
        return Math.abs(Mth.wrapDegrees(want - now));
    }

    // --- 0: the leap into the water ------------------------------------------------------------------------------------

    /**
     * A monitor or a grown crocodile near (or one that hurt it): on land it leaps for the nearest water away from it,
     * aimed with Minecraft's own long-jump sums (the whole arc checked clear of blocks; the frog's own leap, my pick of
     * angles flatter than Minecraft's frog, a dive); with no clear arc, or already in the water, it swims or hops away.
     */
    static final class EscapeGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private LivingEntity from;
        private int ticks;

        EscapeGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!now(frog, 5) || frog.action() == BullfrogEntity.DIG_OUT || frog.action() == BullfrogEntity.LEAP || frog.getVehicle() instanceof BullfrogEntity) {
                return false;       // (a clasped male holds on: she does the leaping)
            }
            if (!frog.isInWater() && !frog.onGround()) {
                return false;       // in the air over land: it picks leap or run once it lands (a fresh frog's first tick, BUGS 248)
            }
            from = danger(frog, DANGER_NEAR);
            LivingEntity hurtBy = frog.getLastHurtByMob();
            if (from == null && hurtBy != null && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100
                    && (hurtBy instanceof MonitorEntity || hurtBy instanceof CrocodileEntity)) {
                from = hurtBy;
            }
            return from != null;
        }

        @Override
        public void start() {
            ticks = 0;
            if (frog.isDug()) {
                frog.stopActing();      // bursting out of the ground
            }
            Vec3 danger = from.position();
            if (!frog.isInWater() && frog.onGround()) {
                BlockPos water = nearestWater(frog, WATER_REACH, danger);
                if (water != null) {
                    Vec3 to = new Vec3(water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
                    turnTo(frog, to, 360);
                    for (int angle : new int[] {45, 55, 35, 65}) {
                        Optional<Vec3> jump = LongJumpUtil.calculateJumpVectorForAngle(frog, to, 1.2F, angle, true);
                        if (jump.isPresent()) {
                            frog.act(BullfrogEntity.LEAP);
                            frog.setDiscardFriction(true);      // no air drag in the leap (the sums assume none; Minecraft's frog does the same)
                            frog.setDeltaMovement(jump.get());
                            frog.hasImpulse = true;
                            frog.playSound(SoundEvents.FROG_LONG_JUMP, 1.0F, 0.7F);
                            return;
                        }
                    }
                    frog.getNavigation().moveTo(to.x, to.y, to.z, 1.6);
                    return;
                }
            }
            Vec3 away = DefaultRandomPos.getPosAway(frog, 8, 3, danger);
            if (away != null) {
                frog.getNavigation().moveTo(away.x, away.y, away.z, 1.6);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return ++ticks < 60 && (frog.action() == BullfrogEntity.LEAP || !frog.getNavigation().isDone());
        }

        @Override
        public void stop() {
            from = null;
        }
    }

    // --- 1: the ambush lunge -------------------------------------------------------------------------------------------

    /**
     * A hungry frog that is out (not dug in) lunges at something small passing within {@link #LUNGE_REACH} blocks of
     * its mouth: a barb in the water, a snail, a young crab. Mouth wide, a push forward, the jaws shut; caught, it is
     * swallowed (the clip's hands push it in) and the frog is fed. Prey on land seen a little farther off, within
     * {@link #STALK_REACH}, gets a slow creep first (the polish's step 4: real frogs creep up on prey just out of reach,
     * body low, eyes fixed), then the lunge.
     *
     * <p>The hunt (2026-11-20, the user: frogs sat still while hungry, and should hunt the snails under the water): a
     * snail or a young crab seen within {@link #HUNT_REACH} blocks it goes after, hopping on land and swimming in the
     * water, diving to the bed for one there (seen from the bank through the water's top), creeping the last bit on land
     * and lunging once in reach (under the water the lunge goes down at it too); after a meal on the bed it floats back
     * up. Barbs dart off too fast to chase: those it only ambushes.
     */
    static final class LungeGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private LivingEntity prey;
        private int stalked, hunted, restUntil, noRoute;
        private double startFar;
        private boolean chasing;

        LungeGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        static boolean isPrey(LivingEntity e) {
            if (!e.isAlive() || e.isPassenger()) {
                return false;
            }
            if (e instanceof BarbEntity barb) {
                return barb.isInWater() && !barb.fromBucket() && !barb.hasCustomName();
            }
            if (e instanceof TadpoleEntity tadpole) {
                // a tadpole swimming past its mouth (2026-11-23: real bullfrogs eat their own kind's too), ambushed only
                return tadpole.isInWater() && !tadpole.hasCustomName();
            }
            return e instanceof SnailEntity && !e.hasCustomName() || e instanceof CrabEntity crab && crab.life().isYoung() && !crab.hasCustomName();
        }

        private static Vec3 middle(LivingEntity e) {
            return e.position().add(0, e.getBbHeight() * 0.5, 0);
        }

        /** Prey worth creeping up on: on land (not in the water: a barb or a snail on the river bed is only ambushed). */
        private boolean stalkable(LivingEntity e) {
            return !e.isInWater() && frog.onGround() && !frog.isInWater();
        }

        /** Prey worth going after from afar: slow things (a snail, a young crab), not the darting barbs. */
        private static boolean chased(LivingEntity e) {
            return !(e instanceof BarbEntity || e instanceof TadpoleEntity);
        }

        /**
         * Whether it sees this prey: a clear line from its mouth to it, or, for prey under the water, to the water's top
         * over it (a frog on the bank looks down through the water; the bank's edge would hide the bed itself).
         */
        private boolean sees(LivingEntity e) {
            if (clearFromMouth(frog, middle(e))) {
                return true;
            }
            if (!e.isInWater()) {
                return false;
            }
            BlockPos.MutableBlockPos at = e.blockPosition().mutable();
            for (int up = 0; up < 5 && frog.level().getFluidState(at).is(FluidTags.WATER); up++) {
                at.move(0, 1, 0);
            }
            return frog.level().getBlockState(at).isAir() && clearFromMouth(frog, new Vec3(e.getX(), at.getY() - 0.1, e.getZ()));
        }

        @Override
        public boolean canUse() {
            if (!frog.isHungry() || frog.isDug() || frog.busy() || !now(frog, 4)) {
                return false;
            }
            Vec3 mouth = frog.mouth();
            double reach = LUNGE_REACH * frog.getScale(), far = STALK_REACH * frog.getScale();
            boolean hunting = frog.tickCount >= restUntil;
            double look = hunting ? HUNT_REACH : far;
            prey = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : frog.level().getEntitiesOfClass(LivingEntity.class, frog.getBoundingBox().inflate(look + 0.5, 3.0, look + 0.5), // (3 high, not as high as wide: review R10-10)
                    e -> isPrey(e) && !frog.isFatherOf(e) && !RiverCover.hiddenFrom(frog, e))) {      // (never his own tadpoles; a barb still in the reeds unseen)
                double d = middle(e).distanceTo(mouth);
                boolean wanted = d < reach && clearFromMouth(frog, middle(e)) || d < far && stalkable(e) && clearFromMouth(frog, middle(e))
                        || hunting && d < HUNT_REACH && chased(e) && sees(e);
                if (wanted && d < best) {
                    best = d;
                    prey = e;
                }
            }
            return prey != null;
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            stalked = 0;
            hunted = 0;
            noRoute = 0;
            chasing = false;
            startFar = middle(prey).distanceTo(frog.mouth());
            if (startFar < LUNGE_REACH * frog.getScale() && clearFromMouth(frog, middle(prey))) {
                lunge();
            } else if (startFar < STALK_REACH * frog.getScale() && stalkable(prey) && clearFromMouth(frog, middle(prey))) {
                frog.act(BullfrogEntity.STALK);
            } else {
                chasing = true;         // after it: hopping, swimming, diving
                frog.getNavigation().moveTo(prey, 1.0);
            }
        }

        private void lunge() {
            chasing = false;
            frog.getNavigation().stop();
            turnTo(frog, prey.position(), 360);
            frog.act(BullfrogEntity.LUNGE);
        }

        /**
         * Lost it (it got away, no route, too long): it rests from hunting a while (it still lunges at what comes close).
         * Prey that is simply gone (eaten by another) is no failure: it looks again at once.
         */
        private void giveUp() {
            if (prey != null && prey.isAlive()) {
                restUntil = frog.tickCount + HUNT_REST;
            }
            chasing = false;
            prey = null;
            frog.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            byte a = frog.action();
            return a == BullfrogEntity.STALK || a == BullfrogEntity.LUNGE && frog.actionTicks < LUNGE_END
                    || chasing && prey != null && a == BullfrogEntity.NONE || chasing && (a == BullfrogEntity.HOP || a == BullfrogEntity.HOP_DIVE);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (frog.action() == BullfrogEntity.STALK) {
                stalkTick();
                return;
            }
            if (chasing) {
                chaseTick();
                return;
            }
            int t = frog.actionTicks;
            if (prey == null) {
                return;
            }
            if (t == LUNGE_PUSH) {
                if (frog.isInWater()) {
                    // under the water the lunge goes straight at it, down to the bed too
                    Vec3 to = middle(prey).subtract(frog.mouth());
                    double far = Math.max(0.05, to.length());
                    double push = Math.min(0.3, far * 0.35) * frog.getScale();
                    frog.setDeltaMovement(frog.getDeltaMovement().add(to.scale(push / far)));
                    frog.hasImpulse = true;
                    return;
                }
                Vec3 to = prey.position().subtract(frog.position());
                double flat = Math.max(0.05, Math.sqrt(to.x * to.x + to.z * to.z));
                double push = Math.min(0.3, flat * 0.35) * frog.getScale();
                frog.setDeltaMovement(frog.getDeltaMovement().add(to.x / flat * push, 0.12, to.z / flat * push));
                frog.hasImpulse = true;
            } else if (t == LUNGE_SNAP) {
                Vec3 mouth = frog.mouth();
                double near = middle(prey).distanceTo(mouth);
                if (prey.isAlive() && near < 0.6 * frog.getScale() + prey.getBbWidth() * 0.5 + 0.2) {
                    prey.discard();         // swallowed whole
                    frog.ateMeal();
                    frog.playSound(SoundEvents.FROG_EAT, 1.0F, 0.75F);
                }
                prey = null;
            }
        }

        /**
         * After it (its legs: hops on land, swimming in the water; the route kept on the prey, a new one every half
         * second): the lunge once in reach, the creep for the last bit on land; it gives up after {@link #HUNT_TICKS}, if
         * the prey gets well away or no route reaches it.
         */
        private void chaseTick() {
            hunted++;
            if (prey == null || !prey.isAlive() || !isPrey(prey) || hunted > HUNT_TICKS || prey.distanceTo(frog) > HUNT_REACH + 4) {
                giveUp();
                return;
            }
            byte a = frog.action();
            if (a == BullfrogEntity.HOP || a == BullfrogEntity.HOP_DIVE) {
                return;         // in the air: the hop carries it
            }
            double d = middle(prey).distanceTo(frog.mouth());
            frog.getLookControl().setLookAt(prey, 30, 30);
            if (d < LUNGE_REACH * frog.getScale() && clearFromMouth(frog, middle(prey))) {
                lunge();
                return;
            }
            if (d < STALK_REACH * frog.getScale() && frog.onGround() && stalkable(prey) && clearFromMouth(frog, middle(prey))) {
                chasing = false;
                frog.getNavigation().stop();
                startFar = d;
                stalked = 0;
                frog.act(BullfrogEntity.STALK);
                return;
            }
            if (hunted % 10 == 0 || frog.getNavigation().isDone()) {
                // no way to it for 2 s: it gives up (one failed route alone is no matter: none is found mid-splash)
                noRoute = frog.getNavigation().moveTo(prey, 1.0) ? 0 : noRoute + 1;
                if (noRoute > 4) {
                    giveUp();
                }
            }
        }

        /** The creep: toward the prey at the clip's pace (its legs'), the lunge once it is in reach; or it gives up. */
        private void stalkTick() {
            stalked++;
            double d = prey == null ? Double.MAX_VALUE : middle(prey).distanceTo(frog.mouth());
            if (prey == null || !prey.isAlive() || stalked > STALK_TICKS || frog.isInWater() || prey.isInWater()
                    || d > startFar + STALK_LOSE * frog.getScale() || frog.horizontalCollision || !clearFromMouth(frog, middle(prey))) {
                frog.stopActing();          // lost it: sits again
                restUntil = frog.tickCount + HUNT_REST;
                return;
            }
            frog.getLookControl().setLookAt(prey, 10, 10);
            if (d < LUNGE_REACH * frog.getScale()) {
                lunge();
                return;
            }
            frog.getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), 1.0);
        }

        @Override
        public void stop() {
            byte a = frog.action();
            if (a == BullfrogEntity.LUNGE || a == BullfrogEntity.STALK) {
                frog.stopActing();
            }
            if (chasing) {
                frog.getNavigation().stop();
            }
            chasing = false;
            prey = null;
        }
    }

    // --- 1: the tongue at flies ----------------------------------------------------------------------------------------

    /**
     * A hungry frog under a fly cloud (the polish's step 3, 2026-11-20: the tongue aims at one fly). When the cloud sinks
     * within reach (the user's 2 blocks, at a male's size), in front of it and above it along a clear line, it turns to
     * face it and fixes its eyes on one fly (AIM: the head tips up to it), then flicks. The flies fly only on the players'
     * computers, so the server decides when and whether a flick catches (one in four misses) and each computer picks the
     * real fly: the tongue's tip follows that fly as it shoots out and the fly rides back on the pad, or dodges at the
     * last moment. While flies stay in reach and it is hungry it flicks again {@link #FLICK_GAP_MIN} to
     * {@link #FLICK_GAP_MIN} + {@link #FLICK_GAP_MORE} ticks later, until a few fill it.
     *
     * <p>A fly landed in reach (the river food web's step 1, 2026-11-23: {@code FlyCloudEntity.landedFly}) is the easy
     * one: on the ground or the water in front of it, lower than its mouth too; it claims it, turns, and flicks once.
     */
    static final class TongueGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private FlyCloudEntity cloud;
        private Vec3 at = Vec3.ZERO;
        private boolean caught;
        /** Whether it is after the cloud's landed fly (else the swarm), and that cloud (let go of when it stops). */
        private boolean landed;
        @Nullable
        private FlyCloudEntity claimed;
        private int ticks, aimed, flicks, wait;

        TongueGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        /** The point of this cloud nearest the frog's mouth (its flies spread about 0.45 round its middle), if in reach. */
        @Nullable
        static Vec3 inReach(BullfrogEntity frog, FlyCloudEntity cloud) {
            Vec3 mouth = frog.mouth();
            Vec3 middle = cloud.position();
            Vec3 to = middle.subtract(mouth);
            double far = to.length();
            Vec3 edge = far < 0.5 ? middle : middle.subtract(to.scale(0.45 / far));
            if (edge.y < mouth.y - 0.1 || edge.distanceTo(mouth) > TONGUE_REACH * frog.getScale()) {
                return null;        // only in front and above, and within reach
            }
            double flat = Math.sqrt((edge.x - mouth.x) * (edge.x - mouth.x) + (edge.z - mouth.z) * (edge.z - mouth.z));
            if (Math.atan2(edge.y - mouth.y, flat) > Math.toRadians(80)) {
                return null;        // straight overhead: it cannot aim up its own back
            }
            HitResult hit = frog.level().clip(new ClipContext(mouth, edge, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, frog));
            return hit.getType() == HitResult.Type.MISS ? edge : null;
        }

        /** A landed fly's spot if it lies within the tongue's reach of the mouth along a clear line, else none. */
        @Nullable
        static Vec3 landedInReach(BullfrogEntity frog, FlyCloudEntity cloud) {
            Vec3 spot = cloud.landedFly();
            if (spot == null) {
                return null;
            }
            Vec3 mouth = frog.mouth();
            Vec3 aim = spot.add(0, 0.05, 0);
            if (aim.distanceTo(mouth) > TONGUE_REACH * frog.getScale()) {
                return null;
            }
            HitResult hit = frog.level().clip(new ClipContext(mouth, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, frog));
            return hit.getType() == HitResult.Type.MISS ? aim : null;
        }

        @Override
        public boolean canUse() {
            if (!frog.isHungry() || frog.isDug() || frog.busy() || !now(frog, 10)) {
                return false;
            }
            FlyCloudEntity down = FlyCloudEntity.landedNear(frog, TONGUE_REACH * frog.getScale() + 1.0, false);
            if (down != null) {
                Vec3 spot = landedInReach(frog, down);
                if (spot != null && down.claimLanded(frog)) {
                    cloud = down;
                    claimed = down;
                    at = spot;
                    landed = true;
                    return true;
                }
            }
            landed = false;
            for (FlyCloudEntity c : frog.level().getEntitiesOfClass(FlyCloudEntity.class, frog.getBoundingBox().inflate(4.0))) {
                if (c.flies() <= 0) {
                    continue;
                }
                Vec3 edge = inReach(frog, c);
                if (edge != null && frog.getRandom().nextInt(4) == 0) {     // a fly comes within reach now
                    cloud = c;
                    at = edge;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            flicks = 0;
            ticks = 0;
            aim(0);
        }

        /** Eyes on a fly (the head tips up to it on the players' computers), the next flick after this many ticks at least. */
        private void aim(int pause) {
            aimed = 0;
            wait = pause;
            frog.setTongueCloud(cloud);
            frog.setTongueAt(at);       // where its eyes are: its renderer picks the landed fly when it aims at its spot
            frog.act(BullfrogEntity.AIM);
        }

        @Override
        public boolean canContinueToUse() {
            return cloud != null && ++ticks < 400;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (cloud == null) {
                return;
            }
            if (frog.action() == BullfrogEntity.AIM) {
                // it turns to face the flies (a frog cannot flick sideways), holds its eyes on one, then shoots
                aimed++;
                boolean facing = turnTo(frog, at, 30) < 12;
                if (aimed > 20 + wait) {
                    cloud = null;       // never came round to face them
                    return;
                }
                if (facing && aimed >= AIM_TICKS + wait) {
                    at = landed ? cloud.claimedBy(frog) ? landedInReach(frog, cloud) : null : inReach(frog, cloud);
                    if (at == null || !cloud.isAlive() || cloud.flies() <= 0) {
                        cloud = null;
                        return;
                    }
                    caught = frog.getRandom().nextFloat() >= TONGUE_MISS;
                    frog.setTongueAt(at);
                    frog.setTongueHit(caught);
                    frog.act(BullfrogEntity.TONGUE);
                    frog.playSound(SoundEvents.FROG_TONGUE, 1.0F, 0.8F);
                    flicks++;
                }
                return;
            }
            if (frog.action() != BullfrogEntity.TONGUE) {
                cloud = null;           // something else took over
                return;
            }
            int t = frog.actionTicks;
            if (landed && t == TONGUE_OUT && !caught) {
                cloud.landedFlyOff(frog);       // the landed fly zips up just as the pad comes
            }
            if (t == TONGUE_BACK && caught && cloud.isAlive() && cloud.flies() > 0 && (!landed || cloud.claimedBy(frog))) {
                if (landed) {
                    cloud.eatLanded(frog);
                } else {
                    cloud.takeFly();
                }
                frog.ateFly();
                frog.playSound(SoundEvents.FROG_EAT, 0.8F, 1.1F);
            } else if (t >= TONGUE_END) {
                // flick after flick while it is hungry and the flies stay in reach (a landed fly: one flick)
                if (!landed && frog.isHungry() && flicks < MOST_FLICKS && cloud.isAlive() && cloud.flies() > 0 && (at = inReach(frog, cloud)) != null) {
                    aim(FLICK_GAP_MIN + frog.getRandom().nextInt(FLICK_GAP_MORE));
                } else {
                    cloud = null;
                }
            }
        }

        @Override
        public void stop() {
            byte a = frog.action();
            if (a == BullfrogEntity.TONGUE || a == BullfrogEntity.AIM) {
                frog.stopActing();
            }
            frog.setTongueCloud(null);
            if (claimed != null) {
                claimed.releaseLanded(frog);
                claimed = null;
            }
            cloud = null;
            landed = false;
        }
    }

    // --- 2: the threat to a player, and the father's guard -------------------------------------------------------------

    /**
     * Bold, not shy (the plan): a player coming within {@link #PUFF_NEAR} blocks makes it face them and puff up; one who
     * comes within its bite's reach ({@link #biteReach}) after it has puffed a second and a half, or who hurt it, gets a barking lunge and a
     * bite ({@link #BITE}: 1 heart), then it puffs again. A father (the river tie-up part B) does the same to anything
     * that comes within {@link BullfrogEntity#GUARD_SEE_OFF} blocks of his eggs or school ({@link BullfrogEntity#threatensYoung}:
     * a player, a monitor, a young crocodile), going right up to it first, and to a tilapia or crab come within
     * {@link BullfrogEntity#RAIDER_NEAR} of them to eat the eggs (the river food web's step 2, 2026-11-23).
     */
    static final class ThreatGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private LivingEntity foe;
        private int puffed, rest, ticks;
        private boolean bit;

        ThreatGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!now(frog, 5) || frog.action() == BullfrogEntity.LEAP || frog.isFroglet() || frog.getVehicle() instanceof BullfrogEntity || frog.isVehicle()) {
                return false;       // (a froglet is too small to square up to anyone; a clasping pair is busy)
            }
            foe = playerNear(frog, PUFF_NEAR);
            if (foe == null) {
                foe = intruder(frog);
            }
            if (foe == null && frog.getLastHurtByMob() instanceof Player hurter && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100
                    && !hurter.isCreative() && !hurter.isSpectator() && hurter.distanceTo(frog) < 6) {
                foe = hurter;
            }
            return foe != null && frog.hasLineOfSight(foe);
        }

        /** The nearest thing come within reach of the eggs or school a father guards, if any. */
        @Nullable
        static LivingEntity intruder(BullfrogEntity frog) {
            BlockPos spot = frog.guardSpot();
            if (spot == null) {
                return null;
            }
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            Vec3 middle = Vec3.atCenterOf(spot);
            for (LivingEntity e : frog.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(spot).inflate(BullfrogEntity.GUARD_SEE_OFF),
                    e -> e != frog && (BullfrogEntity.threatensYoung(e)
                            || BullfrogEntity.raidsEggs(e) && e.distanceToSqr(middle) < Mth.square(BullfrogEntity.RAIDER_NEAR)))) {
                double d = e.distanceToSqr(frog);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        /** Whether the foe is still the frog's business: near it, or (for a father) still near his young. */
        private boolean stillNear(LivingEntity e) {
            BlockPos spot = frog.guardSpot();
            if (BullfrogEntity.raidsEggs(e)) {
                // an egg thief is left once it has backed off from the eggs (he does not hunt fish across the pool)
                return spot != null && e.distanceToSqr(Vec3.atCenterOf(spot)) < Mth.square(BullfrogEntity.RAIDER_NEAR + 2.0);
            }
            if (e.distanceTo(frog) < PUFF_NEAR + 2.0) {
                return true;
            }
            return spot != null && e.distanceToSqr(Vec3.atCenterOf(spot)) < Mth.square(BullfrogEntity.GUARD_SEE_OFF + 2.0);
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            puffed = 0;
            ticks = 0;
            rest = 0;
            if (frog.isDug()) {
                frog.act(BullfrogEntity.DIG_OUT);       // out of the ground first
            }
        }

        @Override
        public boolean canContinueToUse() {
            return foe != null && foe.isAlive() && !(foe instanceof Player p && (p.isCreative() || p.isSpectator()))
                    && stillNear(foe) && ++ticks < 600;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (foe == null) {
                return;
            }
            byte a = frog.action();
            frog.getLookControl().setLookAt(foe, 30, 30);
            if (a == BullfrogEntity.DIG_OUT) {
                return;             // the entity ends its digging out by itself
            }
            if (a == BullfrogEntity.LUNGE) {
                int t = frog.actionTicks;
                if (t == LUNGE_PUSH) {
                    Vec3 to = foe.position().subtract(frog.position());
                    double flat = Math.max(0.05, Math.sqrt(to.x * to.x + to.z * to.z));
                    double push = 0.3 * frog.getScale();
                    frog.setDeltaMovement(frog.getDeltaMovement().add(to.x / flat * push, 0.15, to.z / flat * push));
                    frog.hasImpulse = true;
                } else if (t == LUNGE_SNAP && !bit) {
                    bit = true;
                    if (foe.distanceTo(frog) - body(foe) < biteReach(frog)) {
                        foe.hurt(frog.damageSources().mobAttack(frog), BITE);
                    }
                } else if (t >= LUNGE_END) {
                    frog.act(BullfrogEntity.PUFF);
                    rest = 50;
                }
                return;
            }
            double far = foe.distanceTo(frog) - body(foe);
            double lungeFrom = biteReach(frog) - LUNGE_FROM;
            if (far > lungeFrom && frog.guardSpot() != null) {
                // a father goes right up to it (hopping or swimming: his legs' own moves), then puffs and bites
                if (a == BullfrogEntity.PUFF) {
                    frog.stopActing();
                }
                if (frog.getNavigation().isDone() || frog.tickCount % 10 == 0) {
                    frog.getNavigation().moveTo(foe, 1.2);
                }
                return;
            }
            if (a == BullfrogEntity.HOP || a == BullfrogEntity.HOP_DIVE) {
                return;             // a hop under way lands first
            }
            frog.getNavigation().stop();
            turnTo(frog, foe.position(), 15);
            if (a != BullfrogEntity.PUFF) {
                frog.act(BullfrogEntity.PUFF);
            }
            puffed++;
            if (rest > 0) {
                rest--;
            }
            boolean provoked = frog.getLastHurtByMob() == foe && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100;
            if (rest <= 0 && (puffed > 30 && far < lungeFrom || provoked && far < 2.5)) {
                frog.act(BullfrogEntity.LUNGE);
                bit = false;
                frog.playSound(SavannaSounds.BULLFROG_BARK.get(), 1.2F, frog.getVoicePitch());
            }
        }

        /** How much nearer a big foe's middle is than its edge (none for a player: the old reaches were made for them). */
        private static double body(LivingEntity e) {
            return e instanceof Player ? 0.0 : e.getBbWidth() * 0.5;
        }

        @Override
        public void stop() {
            byte a = frog.action();
            if (a == BullfrogEntity.PUFF || a == BullfrogEntity.LUNGE) {
                frog.stopActing();
            }
            frog.getNavigation().stop();
            foe = null;
        }
    }

    // --- 3: dug in through the hottest hours ---------------------------------------------------------------------------

    /** Ground a bullfrog can shuffle backward into: soil, sand, mud, clay, the river's silt. */
    static boolean diggable(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
                || state.is(Blocks.GRAVEL) || state.is(com.antcolonies.savanna.registry.SavannaBlocks.SILT.get())
                || state.is(com.antcolonies.savanna.registry.SavannaBlocks.WET_SILT.get())
                || state.is(com.antcolonies.savanna.registry.SavannaBlocks.RIVER_MUD.get());
    }

    /**
     * In the hottest hours of a dry day (10:00 to 14:00, my pick: the real frog sits dug in by day, kept short so a
     * player still sees frogs about most of the day; fun first) a frog on soft ground by the water shuffles backward into
     * it, only its back and eyes out; it comes out when the hours are over, it rains, or something comes within 3 blocks.
     */
    static final class DigGoal extends Goal {
        private final BullfrogEntity frog;

        DigGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        static boolean hotHours(Level level, net.minecraft.core.BlockPos at) {
            long time = com.antcolonies.colony.Colony.dayTimeAt(level, at) % 24000L;      // the one shared day clock
            return time >= 4000L && time < 8000L && !level.isRaining();
        }

        private boolean disturbed() {
            return playerNear(frog, 3.0) != null || danger(frog, 3.0) != null
                    || frog.getLastHurtByMob() != null && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100;
        }

        @Override
        public boolean canUse() {
            if (frog.action() == BullfrogEntity.DUG) {
                return true;        // dug in when the world was saved
            }
            if (!now(frog, 40) || frog.busy() || !frog.onGround() || frog.isInWater()
                    || !hotHours(frog.level(), frog.blockPosition()) || disturbed()) {
                return false;
            }
            return diggable(frog.level().getBlockState(frog.blockPosition().below())) && nearestWater(frog, 3, null) != null;
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            if (frog.action() != BullfrogEntity.DUG) {
                frog.act(BullfrogEntity.DIG_IN);
            }
        }

        @Override
        public boolean canContinueToUse() {
            return frog.isDug() && frog.action() != BullfrogEntity.DIG_OUT && hotHours(frog.level(), frog.blockPosition()) && !disturbed();
        }

        @Override
        public void tick() {
            if (frog.action() == BullfrogEntity.DIG_IN && frog.actionTicks >= DIG_IN_TICKS) {
                frog.act(BullfrogEntity.DUG);
            }
        }

        @Override
        public void stop() {
            byte a = frog.action();
            if (a == BullfrogEntity.DUG || a == BullfrogEntity.DIG_IN) {
                frog.act(BullfrogEntity.DIG_OUT);       // the entity ends it after the clip
            }
        }
    }

    // --- 4: the males' call --------------------------------------------------------------------------------------------

    /**
     * From dusk through the night and in rain a male sits (on the bank or afloat) and calls, the throat swelling into a
     * yellow pouch with a deep lowing whoop; other males within {@link #ANSWER_REACH} blocks answer a few seconds later.
     */
    static final class CallGoal extends Goal {
        private final BullfrogEntity frog;

        CallGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        static boolean callingTime(Level level, net.minecraft.core.BlockPos at) {
            long time = com.antcolonies.colony.Colony.dayTimeAt(level, at) % 24000L;
            return time >= 12500L && time < 23500L || level.isRaining();
        }

        @Override
        public boolean canUse() {
            return frog.isMale() && !frog.isFroglet() && frog.callIn <= 0 && !frog.busy() && !frog.isDug() && callingTime(frog.level(), frog.blockPosition())
                    && (frog.onGround() || frog.isInWater());
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            frog.act(BullfrogEntity.CALL);
            frog.lastCall = frog.tickCount;     // a ready female within reach comes to him (its life kit's SeekMateGoal)
            frog.playSound(SavannaSounds.BULLFROG_CALL.get(), 2.0F, frog.getVoicePitch());
            for (BullfrogEntity other : frog.level().getEntitiesOfClass(BullfrogEntity.class, frog.getBoundingBox().inflate(ANSWER_REACH, 8, ANSWER_REACH),
                    o -> o != frog && o.isMale())) {
                other.callIn = Math.min(other.callIn, 40 + frog.getRandom().nextInt(60));        // an answer 2 to 5 s later
            }
        }

        @Override
        public boolean canContinueToUse() {
            return frog.action() == BullfrogEntity.CALL && frog.actionTicks < CALL_TICKS;
        }

        @Override
        public void stop() {
            if (frog.action() == BullfrogEntity.CALL) {
                frog.stopActing();
            }
            frog.callIn = CALL_REST_MIN + frog.getRandom().nextInt(CALL_REST_MORE);
        }
    }

    // --- 5: under a fly cloud ------------------------------------------------------------------------------------------

    /** A hungry frog that sees a fly cloud within 12 blocks hops to the ground (or water) under its edge and waits there. */
    static final class ToCloudGoal extends Goal {
        private final BullfrogEntity frog;
        private Vec3 spot = Vec3.ZERO;
        private int ticks;

        ToCloudGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!frog.isHungry() || frog.busy() || frog.isDug() || !now(frog, 40)) {
                return false;
            }
            FlyCloudEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (FlyCloudEntity c : frog.level().getEntitiesOfClass(FlyCloudEntity.class, frog.getBoundingBox().inflate(12, 6, 12))) {
                double d = c.distanceToSqr(frog);
                if (c.flies() > 0 && d < bestD) {
                    best = c;
                    bestD = d;
                }
            }
            if (best == null) {
                return false;
            }
            // under the cloud's edge on the frog's side, so the cloud hangs ahead of it and above
            Vec3 to = frog.position().subtract(best.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            double x = best.getX() + (flat < 0.1 ? 0.8 : to.x / flat * 0.8), z = best.getZ() + (flat < 0.1 ? 0 : to.z / flat * 0.8);
            spot = new Vec3(x, BullfrogEntity.surface(frog.level(), x, z), z);
            return frog.position().distanceToSqr(spot) > 0.6 * 0.6;
        }

        @Override
        public void start() {
            ticks = 0;
            frog.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return ++ticks < 240 && !frog.getNavigation().isDone() && frog.isHungry();
        }

        @Override
        public void stop() {
            frog.getNavigation().stop();
        }
    }

    // --- 6: a short wander by the water --------------------------------------------------------------------------------

    /** Now and then (about every minute) a few hops to a new spot by the water; far from water, back to it. */
    static final class WanderGoal extends Goal {
        private final BullfrogEntity frog;
        private int ticks;

        WanderGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (frog.busy() || frog.isDug() || !frog.getNavigation().isDone() || frog.getRandom().nextInt(600) != 0) {
                return false;       // (never over a route under way: the polish's step 1 test caught it taking over one)
            }
            BlockPos water = nearestWater(frog, 6, null);
            Vec3 to;
            if (water == null || water.distSqr(frog.blockPosition()) > 16) {
                to = water == null ? DefaultRandomPos.getPos(frog, 8, 3) : Vec3.atBottomCenterOf(water);
            } else {
                to = DefaultRandomPos.getPos(frog, 4, 2);
                if (to != null && nearestWaterTo(frog.level(), BlockPos.containing(to)) > 3) {
                    to = null;          // stays by the water
                }
            }
            if (to == null) {
                return false;
            }
            ticks = 0;
            return frog.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
        }

        /** How far from this spot the nearest water is (blocks, up to 4; 99 for none). */
        private static int nearestWaterTo(Level level, BlockPos at) {
            for (int r = 0; r <= 4; r++) {
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 1, r))) {
                    if (level.getFluidState(p).is(FluidTags.WATER)) {
                        return r;
                    }
                }
            }
            return 99;
        }

        @Override
        public boolean canContinueToUse() {
            return ++ticks < 300 && !frog.getNavigation().isDone();
        }

        @Override
        public void stop() {
            frog.getNavigation().stop();
        }
    }
}
