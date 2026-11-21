package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.GoExactly;
import com.antcolonies.savanna.block.CrocodileNestBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Nile crocodile's breeding (M9, animal life cycle step 4, 2026-11-20; {@code docs/plans/animal-life-cycle.md}; fun
 * first, from what real Nile crocodiles do; every number my pick). The kit ({@code SeekMateGoal}) brings a ready male to
 * a ready female in the water; then:
 * <ul>
 * <li><b>Courtship</b> ({@link #court}): the male head-slaps the surface, head and tail raised, then slams his jaws down
 * with a bang and a splash, and lies low rumbling too deep to hear while the water dances on his back in droplets; she
 * lies still with her snout raised to him ({@link HeldForMateGoal}). Another grown male near is gaped at and the smaller
 * of the two backs off (the loser of a courtship waits his next breeding). At the end the pair roll at the surface.</li>
 * <li><b>The nest</b> ({@link MotherGoal}): she goes up a sand bank above the water (any dry bank with none near), digs
 * with her hind feet, lays and scrapes the sand back over the eggs, and guards the nest: an intruder (a player not in
 * creative, a monitor) coming near is hissed at and charged and bitten ({@link ChaseOffGoal}); she keeps near it and
 * neither hunts nor flees a player.</li>
 * <li><b>Hatching</b>: when the eggs call ({@link CrocodileNestBlock}) she digs them out and carries the hatchlings to
 * the water in her mouth, a few at a time ({@link com.antcolonies.animal.Carried}), and keeps them in a crèche in the
 * shallows for the care days, coming at anything that hurts one. The young wait in the nest for her
 * ({@link WaitForMotherGoal}) and keep near the crèche.</li>
 * </ul>
 */
public final class CrocodileBreeding {
    /**
     * The courtship in the kit's courtship ticks (one every other game tick, so 25 are the head_slap clip's 2.5 s): three
     * rounds of a slap (the jaws hit the water 13 in, the clip's 1.3 s) and a rumble, then the roll to the end (the life
     * file's 210: 6 s of rolling).
     */
    static final int ROUND = 50, SLAP_PART = 25, SLAP_AT = 13, ROLL_FROM = 150;
    /** How far another grown male is noticed (blocks). */
    static final double RIVAL_REACH = 12.0;
    /** The nest's work (ticks): digging the pit, lying still while she lays, scraping it closed, digging it open. */
    static final int DIG_TICKS = 240, LAY_TICKS = 120, COVER_TICKS = 160, OPEN_TICKS = 100;
    /** How far she looks for a sand bank to nest on (blocks), and how far from another nest. */
    static final int SITE_REACH = 40, NESTS_APART = 6;
    /** At most this many hatchlings in her mouth at once (real mothers carry several), taken from within this far of her snout (blocks). */
    static final int CARRY_MAX = 4;
    static final double PICK_UP = 2.0;
    /** Guarding: she keeps within this many blocks of the nest or crèche, and sees off what comes within this many. */
    static final double GUARD_STAY = 6.0, GUARD_SEE_OFF = 5.0;
    /** Seeing off: the hiss, then the charge (ticks), the bite (hearts x 2), the rest before the next (ticks). */
    static final int HISS_TICKS = 30, CHARGE_TICKS = 40, GUARD_COOL = 60;
    static final float GUARD_BITE = 4.0F;
    /** How long hatchlings wait in the nest for their mother before making for the water alone (ticks: a minute). */
    static final int YOUNG_WAIT = 1200;
    /** The young keep within this many blocks of the crèche while their mother cares for them. */
    static final double CRECHE_STAY = 4.0;

    private CrocodileBreeding() {
    }

    // --- courtship ------------------------------------------------------------------------------------------------------

    /** The male's courtship, {@code ticks} into it (the kit's courting hook; the female lies still, {@link HeldForMateGoal}). */
    static void court(CrocodileEntity male, Mob mate, int ticks) {
        if (!(mate instanceof CrocodileEntity female)) {
            return;
        }
        alongside(male, female);
        if (ticks >= ROLL_FROM) {
            male.setShow(CrocodileEntity.SHOW_ROLL);
            female.setShow(CrocodileEntity.SHOW_ROLL);
            return;
        }
        int t = ticks % ROUND;
        male.setShow(t < SLAP_PART ? CrocodileEntity.SHOW_SLAP : CrocodileEntity.SHOW_RUMBLE);
        female.setShow(CrocodileEntity.SHOW_SNOUT_UP);
        if (t == SLAP_AT) {
            slap(male);
        }
        if (t >= SLAP_PART) {
            dance(male);
        }
        if (ticks % 10 == 0) {
            rival(male);
        }
    }

    /**
     * He lies alongside her, on whichever side of her he came to, as far off as two basking crocodiles lie side by side
     * ({@link CrocodileEntity#SIDE_BY_SIDE}, for their sizes), facing her way: the kit brings the pair's middles within a
     * block and a half, which for two 7-block bodies is one inside the other.
     */
    private static void alongside(CrocodileEntity male, CrocodileEntity female) {
        float yaw = female.yBodyRot * Mth.DEG_TO_RAD;
        double sideX = Mth.cos(yaw), sideZ = Mth.sin(yaw);
        double off = CrocodileEntity.SIDE_BY_SIDE * (male.getScale() + female.getScale()) / 2;
        double k = (male.getX() - female.getX()) * sideX + (male.getZ() - female.getZ()) * sideZ >= 0 ? off : -off;
        double x = female.getX() + sideX * k, z = female.getZ() + sideZ * k;
        double dx = x - male.getX(), dz = z - male.getZ();
        male.getNavigation().stop();
        if (dx * dx + dz * dz > 0.3 * 0.3) {
            male.getMoveControl().setWantedPosition(x, male.getY(), z, 0.5);
        } else {
            Vec3 v = male.getDeltaMovement();
            male.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
            float turned = Mth.approachDegrees(male.getYRot(), female.yBodyRot, 4.0F);
            male.setYRot(turned);
            male.yBodyRot = turned;
        }
    }

    /** The head slap: his jaws hit the water with a bang and a splash (at 1.3 s into the head_slap clip). */
    private static void slap(CrocodileEntity male) {
        if (!(male.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 jaw = male.jawPoint();
        double surface = surface(male);
        double s = male.getScale();
        level.sendParticles(ParticleTypes.SPLASH, jaw.x, surface + 0.1, jaw.z, 60, 0.6 * s, 0.1, 0.6 * s, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE, jaw.x, surface - 0.2, jaw.z, 12, 0.4 * s, 0.1, 0.4 * s, 0.05);
        level.playSound(null, jaw.x, surface, jaw.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 2.0F, 0.55F);
        level.playSound(null, jaw.x, surface, jaw.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 1.2F, 0.7F);
    }

    /** The water dancing on his back: droplets jumping off it from his deep rumble, along the chest, the middle and the hips. */
    private static void dance(CrocodileEntity male) {
        if (!(male.level() instanceof ServerLevel level)) {
            return;
        }
        double surface = surface(male);
        float yaw = male.yBodyRot * Mth.DEG_TO_RAD;
        double s = male.getScale();
        for (double along : new double[] {0.9, 0.2, -0.5}) {
            double x = male.getX() - Mth.sin(yaw) * along * s, z = male.getZ() + Mth.cos(yaw) * along * s;
            level.sendParticles(ParticleTypes.SPLASH, x, surface + 0.02, z, 4, 0.22 * s, 0.0, 0.22 * s, 0.12);
        }
    }

    /** The water's surface over it. */
    private static double surface(CrocodileEntity croc) {
        return croc.getY() + croc.getFluidHeight(FluidTags.WATER);
    }

    /**
     * Another grown male near: the two gape at each other and the smaller backs off, sinking away; if that is the
     * courting male, he has lost her and waits his next breeding.
     */
    private static void rival(CrocodileEntity male) {
        for (CrocodileEntity other : male.level().getEntitiesOfClass(CrocodileEntity.class, male.getBoundingBox().inflate(RIVAL_REACH, 4.0, RIVAL_REACH),
                c -> c != male && c.isAlive() && c.isInWater() && c.life().isMale() && c.life().stage().grown())) {
            CrocodileEntity loser = other.getScale() > male.getScale() ? male : other;
            CrocodileEntity winner = loser == male ? other : male;
            winner.gape(20);
            loser.startle();
            if (loser == male) {
                male.life().bred();             // lost her: the courtship ends and he waits his next breeding
            }
            return;
        }
    }

    /** The female while her mate courts her: still in the water, facing him, her snout raised (the male's hook sets her look). */
    static class HeldForMateGoal extends Goal {
        private final CrocodileEntity croc;
        @Nullable
        private Entity mate;

        HeldForMateGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (croc.life().isMale() || croc.life().partner() == null || !croc.isInWater()) {
                return false;
            }
            mate = croc.level() instanceof ServerLevel level ? level.getEntity(croc.life().partner()) : null;
            return mate != null && mate.distanceToSqr(croc) < 32 * 32;
        }

        @Override
        public boolean canContinueToUse() {
            return mate != null && mate.isAlive() && croc.life().partner() != null && mate.getUUID().equals(croc.life().partner());
        }

        @Override
        public void tick() {
            croc.getNavigation().stop();        // she keeps her heading: he comes alongside her (court)
            Vec3 v = croc.getDeltaMovement();
            croc.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
        }

        @Override
        public void stop() {
            mate = null;
        }
    }

    // --- the nest, hatching and the crèche ----------------------------------------------------------------------------

    /** Whether this is something a guarding mother sees off: a player in survival or adventure, or a monitor. */
    static boolean intruder(CrocodileEntity mother, LivingEntity e) {
        if (e instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return e instanceof MonitorEntity;
    }

    /**
     * The mother's work, picked anew each time from how things stand, so she can be called away (to see an intruder off)
     * and take it up again: ripe with eggs and no nest, she nests (to a sand bank, dig, lay); a nest still open, she
     * covers it; eggs calling, she digs them out; young in her mouth, she takes them to the water; her young still on the
     * bank by the nest, she fetches them.
     */
    static class MotherGoal extends Goal {
        private enum Job { NEST, COVER, OPEN, DELIVER, FETCH }

        private final CrocodileEntity croc;
        private Job job;
        @Nullable
        private BlockPos site;
        @Nullable
        private BlockPos water;
        private int work, left, look, waitSite;
        /**
         * Nest spots she could not reach in time or that would not hold a nest: passed by while she looks for another, so
         * she never paces after one she cannot get to (code review 2026-11-21: she picked the same nearest spot again
         * every minute, for good). Cleared when she lays, or when nothing else is left to try.
         */
        private final java.util.Set<BlockPos> failedSites = new java.util.HashSet<>();

        MotherGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (croc.life().isMale() || croc.life().partner() != null || croc.startled > 0 || ++look % 10 != 0) {
                return false;
            }
            if (waitSite > 0) {
                waitSite -= 10;
            }
            job = pick();
            return job != null;
        }

        @Nullable
        private Job pick() {
            Level level = croc.level();
            if (!croc.carried.isEmpty()) {
                return Job.DELIVER;
            }
            if (croc.nestAt != null) {
                var state = level.getBlockState(croc.nestAt);
                if (!(state.getBlock() instanceof CrocodileNestBlock)) {
                    croc.nestAt = null;         // gone (dug up, broken): nothing to guard
                } else if (CrocodileNestBlock.hatching(state)) {
                    return Job.OPEN;
                } else if (state.getValue(CrocodileNestBlock.OPEN)) {
                    return Job.COVER;
                }
            }
            if (!youngToFetch().isEmpty()) {
                return Job.FETCH;
            }
            if (croc.gravid && croc.nestAt == null && waitSite <= 0) {
                return Job.NEST;
            }
            return null;
        }

        @Override
        public void start() {
            work = 0;
            left = 1200;
            water = null;
            site = job == Job.NEST ? null : croc.nestAt;
            croc.setBasking(false);
        }

        @Override
        public boolean canContinueToUse() {
            return job != null && left > 0 && croc.isAlive() && croc.startled <= 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            croc.getNavigation().stop();
            if (job == Job.NEST && site != null && left <= 0) {
                failedSites.add(site);          // out of time on the way there (or digging): another spot next time
            }
            if (croc.show() == CrocodileEntity.SHOW_DIG || croc.show() == CrocodileEntity.SHOW_CARRY && croc.carried.isEmpty()) {
                croc.setShow(CrocodileEntity.SHOW_NONE);
            }
            if (left <= 0 && !croc.carried.isEmpty()) {
                deliver();          // could not reach the water in time: lets them out where she is
            }
            job = null;
        }

        @Override
        public void tick() {
            left--;
            if (job == null) {
                return;     // its job ended this tick (it ticks every tick; the selector stops it at its next check)
            }
            switch (job) {
                case NEST -> nest();
                case COVER -> cover();
                case OPEN -> open();
                case DELIVER -> toWater();
                case FETCH -> fetch();
            }
        }

        private void nest() {
            if (site == null) {
                site = nestSite(croc, failedSites);
                if (site == null) {
                    waitSite = 600;             // nowhere yet: she looks again in half a minute, every spot worth a new try
                    failedSites.clear();
                    job = null;
                    return;
                }
            }
            if (!walkTo(site)) {
                return;
            }
            croc.setShow(CrocodileEntity.SHOW_DIG);
            sand(site);
            if (++work >= DIG_TICKS) {
                var numbers = croc.life().numbers();
                int eggs = numbers.clutchMin() + croc.getRandom().nextInt(numbers.clutchMax() - numbers.clutchMin() + 1);
                if (croc.level() instanceof ServerLevel level && CrocodileNestBlock.lay(level, site, croc.getType(), eggs)) {
                    croc.nestAt = site;
                    croc.gravid = false;
                    failedSites.clear();
                    job = Job.COVER;
                    work = -LAY_TICKS;          // lying still over the pit while she lays
                    left = 1200;
                } else {
                    failedSites.add(site);
                    site = null;                // the spot would not hold a nest: another
                    work = 0;
                }
            }
        }

        private void cover() {
            if (site == null || !walkTo(site)) {
                return;
            }
            if (work < 0) {
                croc.setShow(CrocodileEntity.SHOW_NONE);
                work++;
                return;
            }
            croc.setShow(CrocodileEntity.SHOW_DIG);
            sand(site);
            if (++work >= COVER_TICKS) {
                CrocodileNestBlock.cover(croc.level(), site, false);
                croc.setShow(CrocodileEntity.SHOW_NONE);
                job = null;
            }
        }

        private void open() {
            if (site == null || !walkTo(site)) {
                return;
            }
            croc.setShow(CrocodileEntity.SHOW_DIG);
            sand(site);
            if (++work == OPEN_TICKS / 2) {
                CrocodileNestBlock.cover(croc.level(), site, true);
            }
            if (work >= OPEN_TICKS && croc.level() instanceof ServerLevel level) {
                CrocodileNestBlock.letOut(level, site, croc);
                croc.hatchSite = site;
                croc.nestAt = null;
                croc.setShow(CrocodileEntity.SHOW_NONE);
                job = Job.FETCH;
                left = 1200;
            }
        }

        /** Her young still out of the water near the nest she dug them from (or near her), waiting for her. */
        private List<CrocodileEntity> youngToFetch() {
            BlockPos at = croc.hatchSite != null ? croc.hatchSite : croc.blockPosition();
            return croc.level().getEntitiesOfClass(CrocodileEntity.class, new AABB(at).inflate(8.0, 3.0, 8.0),
                    y -> y.isAlive() && y.life().isYoung() && croc.getUUID().equals(y.motherId) && !y.isInWater() && y.crecheAt() == null);
        }

        private void fetch() {
            List<CrocodileEntity> young = youngToFetch();
            if (young.isEmpty()) {
                if (!croc.carried.isEmpty()) {
                    job = Job.DELIVER;
                    return;
                }
                croc.hatchSite = null;
                job = null;
                return;
            }
            young.sort(java.util.Comparator.comparingDouble(croc::distanceToSqr));
            CrocodileEntity nearest = young.get(0);
            Vec3 jaw = croc.jawPoint();
            if (nearest.position().distanceToSqr(jaw.x, nearest.getY(), jaw.z) > PICK_UP * PICK_UP) {
                if (croc.getNavigation().isDone() || left % 20 == 0) {
                    // to put her snout on it: her middle half her length back from it, coming from where she is
                    Vec3 back = croc.position().subtract(nearest.position()).multiply(1, 0, 1);
                    back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize();
                    Vec3 stand = nearest.position().add(back.scale(CrocodileEntity.JAWS_AHEAD * croc.getScale()));
                    croc.getNavigation().moveTo(stand.x, stand.y, stand.z, 0.8);
                }
                croc.getLookControl().setLookAt(nearest, 30, 30);
                return;
            }
            croc.getNavigation().stop();
            // gently up into her mouth, every one in reach, up to a mouthful
            for (CrocodileEntity y : young) {
                if (croc.carried.count() >= CARRY_MAX) {
                    break;
                }
                if (y.position().distanceToSqr(jaw.x, y.getY(), jaw.z) < PICK_UP * PICK_UP) {
                    croc.carried.take(y);
                }
            }
            croc.setShow(CrocodileEntity.SHOW_CARRY);
            if (croc.carried.count() >= CARRY_MAX || youngToFetch().isEmpty()) {
                job = Job.DELIVER;
                water = null;
            }
        }

        private void toWater() {
            croc.setShow(CrocodileEntity.SHOW_CARRY);
            if (croc.isInWater() && (croc.getFluidHeight(FluidTags.WATER) > 0.3 * croc.getScale() || croc.getNavigation().isDone())) {
                deliver();
                job = youngToFetch().isEmpty() ? null : Job.FETCH;
                left = 1200;
                return;
            }
            if (water == null) {
                water = CrocodileEntity.waterAim(croc, 16);
                if (water == null) {
                    deliver();                  // no water near: lets them out here
                    job = null;
                    return;
                }
            }
            if (croc.getNavigation().isDone() || left % 20 == 0) {
                croc.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 0.8);
            }
        }

        /**
         * Lets the young out of her mouth into the water, into the crèche (started on her first trip): at her snout if it
         * is over water, else beside her body (her snout may still lie on the bank as she slides in).
         */
        private void deliver() {
            if (!(croc.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 at = croc.jawPoint();
            if (!level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER)) {
                at = croc.position().add(0, 0.2, 0);
            }
            long now = level.getGameTime();
            if (croc.crecheAt == null || now >= croc.crecheUntil) {
                croc.crecheAt = at;
                croc.crecheUntil = now + croc.life().numbers().careTicks();
            }
            for (Entity e : croc.carried.letOut(level, at)) {
                if (e instanceof CrocodileEntity young) {
                    young.joinCreche(croc);
                }
            }
            croc.setShow(CrocodileEntity.SHOW_NONE);
        }

        /** Walks into the spot; true once there (standing on it, still). */
        private boolean walkTo(BlockPos spot) {
            double dx = spot.getX() + 0.5 - croc.getX(), dz = spot.getZ() + 0.5 - croc.getZ();
            if (dx * dx + dz * dz < 0.8 * 0.8 && Math.abs(spot.getY() - croc.getY()) < 1.5 && !croc.isInWater()) {
                croc.getNavigation().stop();
                return true;
            }
            if (croc.getNavigation().isDone() || left % 20 == 0) {
                if (!GoExactly.to(croc, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.9)) {
                    croc.getMoveControl().setWantedPosition(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.9);
                }
            }
            return false;
        }

        /** Sand flying from her hind feet as she digs. */
        private void sand(BlockPos at) {
            if (left % 4 != 0 || !(croc.level() instanceof ServerLevel level)) {
                return;
            }
            float yaw = croc.yBodyRot * Mth.DEG_TO_RAD;
            double back = 0.9 * croc.getScale();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(at.below())),
                    croc.getX() + Mth.sin(yaw) * back, croc.getY() + 0.2, croc.getZ() - Mth.cos(yaw) * back, 6, 0.3, 0.1, 0.3, 0.15);
        }
    }

    /**
     * A spot for her nest: the nearest dry river sand a block or two back from the water (a sand bank, as she basks on),
     * else the nearest dry bank that near the water, within {@link #SITE_REACH} blocks and at least {@link #NESTS_APART}
     * from another nest; looked for round the water's surface over her. Null if there is none.
     */
    @Nullable
    static BlockPos nestSite(CrocodileEntity croc) {
        return nestSite(croc, java.util.Set.of());
    }

    /** {@link #nestSite(CrocodileEntity)}, passing by the spots in {@code skip}. */
    @Nullable
    static BlockPos nestSite(CrocodileEntity croc, java.util.Set<BlockPos> skip) {
        Level level = croc.level();
        BlockPos at = croc.blockPosition();
        for (int up = 0; up < 16 && level.getFluidState(at.above()).is(FluidTags.WATER); up++) {
            at = at.above();
        }
        BlockPos sand = null, bank = null;
        int bestSand = Integer.MAX_VALUE, bestBank = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -SITE_REACH; dx <= SITE_REACH; dx++) {
            for (int dz = -SITE_REACH; dz <= SITE_REACH; dz++) {
                int d = dx * dx + dz * dz;
                if (d > SITE_REACH * SITE_REACH || d >= bestBank && d >= bestSand) {
                    continue;
                }
                for (int dy = 3; dy >= -1; dy--) {      // a bank a little over the water, up to 3 blocks
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!level.isLoaded(p) || !level.getBlockState(p).isAir() || !CrocodileEntity.isBank(level, p)
                            || level.getFluidState(p.below()).is(FluidTags.WATER)
                            || !CrocodileEntity.AshoreGoal.waterBeside(level, p, 3) || CrocodileEntity.AshoreGoal.waterBeside(level, p, 1)) {
                        continue;
                    }
                    if (skip.contains(p) || nestNear(level, p)) {
                        break;
                    }
                    if (CrocodileEntity.isRiverSand(level.getBlockState(p.below()))) {
                        if (d < bestSand) {
                            bestSand = d;
                            sand = p.immutable();
                        }
                    } else if (d < bestBank) {
                        bestBank = d;
                        bank = p.immutable();
                    }
                    break;
                }
            }
        }
        return sand != null ? sand : bank;
    }

    private static boolean nestNear(Level level, BlockPos p) {
        for (BlockPos q : BlockPos.betweenClosed(p.offset(-NESTS_APART, -2, -NESTS_APART), p.offset(NESTS_APART, 2, NESTS_APART))) {
            if (level.getBlockState(q).getBlock() instanceof CrocodileNestBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * Seeing off what comes at her nest or her young (the kit's {@code GuardPlaceGoal} picks it as her target): she turns
     * to it and hisses, high on her legs with her mouth wide; if it is still near she charges and bites once, then goes
     * back. It leaving ends it.
     */
    static class ChaseOffGoal extends Goal {
        private final CrocodileEntity croc;
        private int t, cool;
        private boolean bit;

        ChaseOffGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (cool > 0) {
                cool--;
                return false;
            }
            if (seen() == null) {
                if (croc.getTarget() != null && croc.guardPlace() != null) {
                    croc.setTarget(null);       // it went off: nothing left to see off
                }
                return false;
            }
            return true;
        }

        /** Her target while it is near what she guards and still worth seeing off. */
        @Nullable
        private LivingEntity seen() {
            BlockPos place = croc.guardPlace();
            LivingEntity target = croc.getTarget();
            if (place == null || target == null || !target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator())) {
                return null;
            }
            double reach = GUARD_SEE_OFF + 4.0;
            return target.distanceToSqr(Vec3.atCenterOf(place)) < reach * reach ? target : null;
        }

        @Override
        public void start() {
            t = 0;
            bit = false;
            croc.setBasking(false);
            croc.getNavigation().stop();
        }

        @Override
        public boolean canContinueToUse() {
            return !bit && t < HISS_TICKS + CHARGE_TICKS && seen() != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = croc.getTarget();
            if (target == null) {
                return;
            }
            croc.getLookControl().setLookAt(target, 30, 30);
            if (t == 0) {
                croc.level().playSound(null, croc.getX(), croc.getY(), croc.getZ(), SoundEvents.CAT_HISS, SoundSource.HOSTILE, 1.4F, 0.45F);
            }
            t++;
            if (t <= HISS_TICKS) {
                croc.setShow(CrocodileEntity.SHOW_HISS);
                croc.getNavigation().stop();
                float face = (float) (Mth.atan2(target.getZ() - croc.getZ(), target.getX() - croc.getX()) * Mth.RAD_TO_DEG) - 90.0F;
                croc.setYRot(Mth.approachDegrees(croc.getYRot(), face, 12.0F));
                croc.yBodyRot = croc.getYRot();
                if (t == HISS_TICKS && target.distanceToSqr(Vec3.atCenterOf(croc.guardPlace())) > GUARD_SEE_OFF * GUARD_SEE_OFF) {
                    bit = true;                 // it backed off: enough
                }
                return;
            }
            croc.setShow(CrocodileEntity.SHOW_NONE);
            croc.setJaws(CrocodileEntity.JAWS_GAPE);
            if (croc.getNavigation().isDone() || t % 10 == 0) {
                croc.getNavigation().moveTo(target, CrocodileEntity.CHARGE_PACE);
            }
            if (target.getBoundingBox().inflate(0.5, 0.6, 0.5).contains(croc.jawPoint())) {
                target.hurt(croc.damageSources().mobAttack(croc), GUARD_BITE);
                double dx = target.getX() - croc.getX(), dz = target.getZ() - croc.getZ();
                target.knockback(1.0, -dx, -dz);
                croc.level().playSound(null, croc.getX(), croc.getY(), croc.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.6F);
                croc.guardBites++;
                bit = true;
            }
        }

        @Override
        public void stop() {
            croc.setTarget(null);
            croc.setJaws(CrocodileEntity.JAWS_NONE);
            if (croc.show() == CrocodileEntity.SHOW_HISS) {
                croc.setShow(CrocodileEntity.SHOW_NONE);
            }
            croc.getNavigation().stop();
            cool = GUARD_COOL;
        }
    }

    /** Hatchlings dug out of the nest wait in it for their mother to carry them to the water (a minute at most). */
    static class WaitForMotherGoal extends Goal {
        private final CrocodileEntity croc;

        WaitForMotherGoal(CrocodileEntity croc) {
            this.croc = croc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return croc.waitForMother > 0 && !croc.isInWater();
        }

        @Override
        public void tick() {
            croc.getNavigation().stop();
        }
    }
}
