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
    /** How near a monitor or a grown crocodile sends it leaping for the water, and how far it looks for water. */
    static final double DANGER_NEAR = 5.0;
    static final int WATER_REACH = 6;
    /** How near a player makes it puff up, and how near (after puffing a while) makes it lunge and bite (blocks). */
    static final double PUFF_NEAR = 3.0, BITE_NEAR = 1.6;
    /** The bite (hearts x 2: the plan's 1 heart). */
    static final float BITE = 2.0F;
    /** One flick in four misses (the plan). */
    static final float TONGUE_MISS = 0.25F;
    /** The tongue clip: the pad at the fly on this tick (0.15 s), back in the mouth on this one, the clip over on this. */
    static final int TONGUE_OUT = 3, TONGUE_BACK = 7, TONGUE_END = 14;
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
        goals.addGoal(2, new ThreatGoal(frog));
        goals.addGoal(3, new DigGoal(frog));
        goals.addGoal(4, new CallGoal(frog));
        goals.addGoal(5, new ToCloudGoal(frog));
        goals.addGoal(6, new WanderGoal(frog));
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
                e -> e.isAlive() && (e instanceof MonitorEntity || e instanceof CrocodileEntity c && c.life().stage().grown()));
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
            if (!now(frog, 5) || frog.action() == BullfrogEntity.DIG_OUT || frog.action() == BullfrogEntity.LEAP) {
                return false;
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
     * swallowed (the clip's hands push it in) and the frog is fed.
     */
    static final class LungeGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private LivingEntity prey;

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
            return e instanceof SnailEntity && !e.hasCustomName() || e instanceof CrabEntity crab && crab.life().isYoung() && !crab.hasCustomName();
        }

        @Override
        public boolean canUse() {
            if (!frog.isHungry() || frog.isDug() || frog.busy() || !now(frog, 4)) {
                return false;
            }
            Vec3 mouth = frog.mouth();
            double reach = LUNGE_REACH * frog.getScale();
            prey = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : frog.level().getEntitiesOfClass(LivingEntity.class, frog.getBoundingBox().inflate(reach + 0.5), LungeGoal::isPrey)) {
                double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(mouth);
                if (d < reach && d < best && clearFromMouth(frog, e.position().add(0, e.getBbHeight() * 0.5, 0))) {
                    best = d;
                    prey = e;
                }
            }
            return prey != null;
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            turnTo(frog, prey.position(), 360);
            frog.act(BullfrogEntity.LUNGE);
        }

        @Override
        public boolean canContinueToUse() {
            return frog.action() == BullfrogEntity.LUNGE && frog.actionTicks < LUNGE_END;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            int t = frog.actionTicks;
            if (prey == null) {
                return;
            }
            if (t == LUNGE_PUSH) {
                Vec3 to = prey.position().subtract(frog.position());
                double flat = Math.max(0.05, Math.sqrt(to.x * to.x + to.z * to.z));
                double push = Math.min(0.3, flat * 0.35) * frog.getScale();
                frog.setDeltaMovement(frog.getDeltaMovement().add(to.x / flat * push, frog.isInWater() ? 0 : 0.12, to.z / flat * push));
                frog.hasImpulse = true;
            } else if (t == LUNGE_SNAP) {
                Vec3 mouth = frog.mouth();
                double near = prey.position().add(0, prey.getBbHeight() * 0.5, 0).distanceTo(mouth);
                if (prey.isAlive() && near < 0.6 * frog.getScale() + prey.getBbWidth() * 0.5 + 0.2) {
                    prey.discard();         // swallowed whole
                    frog.ateMeal();
                    frog.playSound(SoundEvents.FROG_EAT, 1.0F, 0.75F);
                }
                prey = null;
            }
        }

        @Override
        public void stop() {
            if (frog.action() == BullfrogEntity.LUNGE) {
                frog.stopActing();
            }
            prey = null;
        }
    }

    // --- 1: the tongue at flies ----------------------------------------------------------------------------------------

    /**
     * A hungry frog under a fly cloud flicks its tongue (the user's 2 blocks, at a male's size) at the cloud's nearest
     * flies when they sink within reach, in front of it and above it along a clear line: it turns to face them, the
     * tongue shoots out (the renderer's rope), one fly in four gets away, a caught one rides the tongue back into its
     * mouth. The flies' places are only on the players' computers, so the server decides: a fly "comes within reach" by
     * chance every few seconds while the cloud's edge is within reach.
     */
    static final class TongueGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private FlyCloudEntity cloud;
        private Vec3 at = Vec3.ZERO;
        private boolean flicked, caught;
        private int turning;

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

        @Override
        public boolean canUse() {
            if (!frog.isHungry() || frog.isDug() || frog.busy() || !now(frog, 10)) {
                return false;
            }
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
            flicked = false;
            caught = false;
            turning = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return cloud != null && (!flicked ? turning < 20 : frog.action() == BullfrogEntity.TONGUE && frog.actionTicks < TONGUE_END);
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
            if (!flicked) {
                // it turns to face the flies first (a frog cannot flick sideways), then shoots
                turning++;
                if (turnTo(frog, at, 30) < 12) {
                    at = inReach(frog, cloud);
                    if (at == null || cloud.flies() <= 0) {
                        cloud = null;
                        return;
                    }
                    flicked = true;
                    frog.setTongueAt(at);
                    frog.act(BullfrogEntity.TONGUE);
                    cloud.aimTongue(at);
                    frog.playSound(SoundEvents.FROG_TONGUE, 1.0F, 0.8F);
                }
                return;
            }
            int t = frog.actionTicks;
            if (t == TONGUE_OUT) {
                caught = frog.getRandom().nextFloat() >= TONGUE_MISS && cloud.isAlive() && cloud.flies() > 0;
                if (caught) {
                    cloud.aimTongue(frog.mouth());      // the fly rides the tongue back into its mouth
                } else {
                    cloud.clearTongue();                // missed: the fly zips off
                }
            } else if (t == TONGUE_BACK && caught) {
                cloud.takeFly();
                frog.ateFly();
                frog.playSound(SoundEvents.FROG_EAT, 0.8F, 1.1F);
            }
        }

        @Override
        public void stop() {
            if (frog.action() == BullfrogEntity.TONGUE) {
                frog.stopActing();
            }
            if (cloud != null && flicked && !caught) {
                cloud.clearTongue();
            }
            cloud = null;
        }
    }

    // --- 2: the threat to a player -------------------------------------------------------------------------------------

    /**
     * Bold, not shy (the plan): a player coming within {@link #PUFF_NEAR} blocks makes it face them and puff up; one who
     * comes within {@link #BITE_NEAR} after it has puffed a second and a half, or who hurt it, gets a barking lunge and a
     * bite ({@link #BITE}: 1 heart), then it puffs again.
     */
    static final class ThreatGoal extends Goal {
        private final BullfrogEntity frog;
        @Nullable
        private Player player;
        private int puffed, rest, ticks;
        private boolean bit;

        ThreatGoal(BullfrogEntity frog) {
            this.frog = frog;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!now(frog, 5) || frog.action() == BullfrogEntity.LEAP) {
                return false;
            }
            player = playerNear(frog, PUFF_NEAR);
            if (player == null && frog.getLastHurtByMob() instanceof Player hurter && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100
                    && !hurter.isCreative() && !hurter.isSpectator() && hurter.distanceTo(frog) < 6) {
                player = hurter;
            }
            return player != null && frog.hasLineOfSight(player);
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
            return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator()
                    && player.distanceTo(frog) < PUFF_NEAR + 2.0 && ++ticks < 600;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (player == null) {
                return;
            }
            byte a = frog.action();
            frog.getLookControl().setLookAt(player, 30, 30);
            if (a == BullfrogEntity.DIG_OUT) {
                return;             // the entity ends its digging out by itself
            }
            if (a == BullfrogEntity.LUNGE) {
                int t = frog.actionTicks;
                if (t == LUNGE_PUSH) {
                    Vec3 to = player.position().subtract(frog.position());
                    double flat = Math.max(0.05, Math.sqrt(to.x * to.x + to.z * to.z));
                    double push = 0.3 * frog.getScale();
                    frog.setDeltaMovement(frog.getDeltaMovement().add(to.x / flat * push, 0.15, to.z / flat * push));
                    frog.hasImpulse = true;
                } else if (t == LUNGE_SNAP && !bit) {
                    bit = true;
                    if (player.distanceTo(frog) < 1.0 * frog.getScale() + 0.8) {
                        player.hurt(frog.damageSources().mobAttack(frog), BITE);
                    }
                } else if (t >= LUNGE_END) {
                    frog.act(BullfrogEntity.PUFF);
                    rest = 50;
                }
                return;
            }
            turnTo(frog, player.position(), 15);
            if (a != BullfrogEntity.PUFF) {
                frog.act(BullfrogEntity.PUFF);
            }
            puffed++;
            if (rest > 0) {
                rest--;
            }
            boolean provoked = frog.getLastHurtByMob() == player && frog.tickCount - frog.getLastHurtByMobTimestamp() < 100;
            if (rest <= 0 && (puffed > 30 && player.distanceTo(frog) < BITE_NEAR || provoked && player.distanceTo(frog) < 2.5)) {
                frog.act(BullfrogEntity.LUNGE);
                bit = false;
                frog.playSound(SavannaSounds.BULLFROG_BARK.get(), 1.2F, frog.getVoicePitch());
            }
        }

        @Override
        public void stop() {
            byte a = frog.action();
            if (a == BullfrogEntity.PUFF || a == BullfrogEntity.LUNGE) {
                frog.stopActing();
            }
            player = null;
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
            return frog.isMale() && frog.callIn <= 0 && !frog.busy() && !frog.isDug() && callingTime(frog.level(), frog.blockPosition())
                    && (frog.onGround() || frog.isInWater());
        }

        @Override
        public void start() {
            frog.getNavigation().stop();
            frog.act(BullfrogEntity.CALL);
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
            if (frog.busy() || frog.isDug() || frog.getRandom().nextInt(600) != 0) {
                return false;
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
