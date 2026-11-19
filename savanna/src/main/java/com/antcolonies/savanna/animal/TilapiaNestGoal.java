package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.GoExactly;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.savanna.block.TilapiaNestBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A tilapia male's nest (M9, animal life cycle step 2, 2026-11-19; the plan's picks, fun first, inspired by the real
 * cichlid's nest leks): a grown male ready to breed, with room for tilapia to grow ({@link RoomToBreed}), now and then
 * picks a spot of shallow silt or sand bed near him (the tag {@code tilapia_nest_ground}, at most
 * {@link RiverBed#SHALLOW} blocks of water over it, no other pit right beside it), swims there and fans a round
 * {@linkplain TilapiaNestBlock nest pit} out of it with his nose down, puffs of the bed rising. Then he holds it: he
 * wears his breeding colours ({@link TilapiaEntity#BREEDING}), stays over the pit and darts at any other grown male that
 * comes near it, shoving him off. A ready female finds him there ({@link TilapiaEntity#takesMate}) and they circle over
 * the pit ({@link TilapiaEntity#courting}). He leaves the pit when they have mated, when the pit is gone, or after
 * {@link #HOLD_TICKS} with no female.
 */
public class TilapiaNestGoal extends Goal {
    /** How often a ready male thinks about a nest (ticks). */
    static final int LOOK_EVERY = 40;
    /** The chance he starts one at a look: about once in 3 game hours once ready (my pick, so not every male at once). */
    static final int NEST_CHANCE = 60;
    /** How far round him he looks for a spot (blocks, sideways). */
    static final int REACH = 8;
    /** How long he fans the pit out (ticks). */
    static final int DIG_TICKS = 80;
    /** Gives up a spot he has not reached in this long (ticks). */
    static final int GIVE_UP = 600;
    /** How long he holds a pit with no female before going back to his school (ticks: half a game day, my pick). */
    static final int HOLD_TICKS = 12000;
    /** How near the pit's middle a rival male is chased off (blocks). */
    static final double RIVAL_NEAR = 3.5;

    private final TilapiaEntity fish;
    @Nullable
    private BlockPos spot;
    private int dug, walked;
    @Nullable
    private TilapiaEntity rival;

    public TilapiaNestGoal(TilapiaEntity fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (fish.holdsNest()) {
            return true;
        }
        if (!fish.isMale() || (fish.tickCount + fish.getId()) % LOOK_EVERY != 0 || !fish.isInWater()
                || !fish.life().readyToBreed() || !(fish.wantsNestNow || fish.getRandom().nextInt(NEST_CHANCE) == 0)) {
            return false;
        }
        if (!RoomToBreed.here(fish, fish.life().numbers().localLimit())) {
            return false;
        }
        spot = findSpot();
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        return fish.holdsNest() || (spot != null && walked < GIVE_UP && fish.life().readyToBreed());
    }

    @Override
    public void start() {
        dug = 0;
        walked = 0;
        rival = null;
        fish.wantsNestNow = false;
    }

    @Override
    public void stop() {
        spot = null;
        rival = null;
        fish.setGrazing(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (fish.holdsNest()) {
            hold();
        } else if (spot != null) {
            dig();
        }
    }

    // --- fanning the pit out ------------------------------------------------------------------------------------------

    private void dig() {
        Vec3 over = Vec3.atBottomCenterOf(spot).add(0, 0.35, 0);
        double dx = fish.getX() - over.x, dz = fish.getZ() - over.z;
        if (dx * dx + dz * dz > 0.8 * 0.8 || Math.abs(fish.getY() - over.y) > 0.9) {
            walked++;
            fish.setGrazing(false);
            if (fish.getNavigation().isDone() || walked % 20 == 0) {
                GoExactly.to(fish, over.x, over.y, over.z, 1.0);
            }
            return;
        }
        fish.getNavigation().stop();
        fish.setGrazing(true);         // nose down, fanning and mouthing the bed (the graze clip)
        fish.getLookControl().setLookAt(over.x, spot.getY(), over.z);
        if (dug % 6 == 0 && fish.level() instanceof ServerLevel server) {
            BlockState bed = server.getBlockState(spot.below());
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bed), over.x, spot.getY() + 0.1, over.z, 4, 0.3, 0.05, 0.3, 0.02);
        }
        if (++dug < DIG_TICKS) {
            return;
        }
        fish.setGrazing(false);
        if (fitsANest(fish.level(), spot)) {
            fish.level().setBlock(spot, TilapiaNestBlock.over(fish.level().getBlockState(spot.below())), Block.UPDATE_ALL);
            fish.takeNest(spot);
        }
        spot = null;
    }

    // --- holding it -----------------------------------------------------------------------------------------------------

    private void hold() {
        BlockPos nest = fish.nest();
        if (nest == null || !fish.level().getBlockState(nest).is(SavannaBlocks.TILAPIA_NEST.get())
                || fish.level().getGameTime() - fish.nestSince() > HOLD_TICKS || fish.distanceToSqr(Vec3.atCenterOf(nest)) > 16 * 16) {
            fish.leaveNest();
            return;
        }
        Vec3 over = Vec3.atBottomCenterOf(nest).add(0, 0.35, 0);
        if (fish.life().partner() != null) {
            rival = null;               // courting: the female's courtship steers them both
            return;
        }
        if (rival == null && fish.tickCount % 10 == 0) {
            rival = rivalNear(over);
        }
        if (rival != null) {
            if (!rival.isAlive() || rival.distanceToSqr(over) > (RIVAL_NEAR + 1) * (RIVAL_NEAR + 1)) {
                rival = null;
            } else {
                chase(rival, over);
                return;
            }
        }
        if (fish.distanceToSqr(over) > 1.2 * 1.2 && (fish.getNavigation().isDone() || fish.tickCount % 20 == 0)) {
            GoExactly.to(fish, over.x, over.y, over.z, 0.8);
        }
    }

    private void chase(TilapiaEntity other, Vec3 over) {
        fish.getLookControl().setLookAt(other, 30, 30);
        if (fish.distanceTo(other) > 0.9) {
            if (fish.tickCount % 5 == 0) {
                fish.getNavigation().moveTo(other, 1.8);
            }
            return;
        }
        // a quick nip and a shove: the other male turns tail away from the pit
        Vec3 away = other.position().subtract(over).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        other.setDeltaMovement(other.getDeltaMovement().add(away.scale(0.35)));
        Vec3 flee = over.add(away.scale(7));
        other.getNavigation().moveTo(flee.x, other.getY(), flee.z, 1.6);
        rival = null;
    }

    @Nullable
    private TilapiaEntity rivalNear(Vec3 over) {
        List<TilapiaEntity> near = fish.level().getEntitiesOfClass(TilapiaEntity.class,
                fish.getBoundingBox().inflate(RIVAL_NEAR + 2), t -> t != fish && t.isAlive() && t.isMale() && !t.isYoung()
                        && !t.holdsNest() && t.distanceToSqr(over) < RIVAL_NEAR * RIVAL_NEAR);
        return near.isEmpty() ? null : near.get(0);
    }

    // --- where ---------------------------------------------------------------------------------------------------------

    /** The nearest spot round him that fits a pit ({@link #fitsANest}), or null. */
    @Nullable
    private BlockPos findSpot() {
        BlockPos at = fish.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dz = -REACH; dz <= REACH; dz++) {
                for (int dy = -RiverBed.SHALLOW; dy <= 2; dy++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    double d = p.distSqr(at);
                    if (d < bestDistance && fitsANest(fish.level(), p)) {
                        best = p.immutable();
                        bestDistance = d;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Whether a pit fits here: plain still water lying on a nest ground's sturdy top, the lowest water of a shallow
     * column ({@link RiverBed#bedUnder}), with no other pit beside it.
     */
    static boolean fitsANest(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!here.is(Blocks.WATER) || !here.getFluidState().isSource()) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState bed = level.getBlockState(below);
        if (!bed.is(SavannaTags.TILAPIA_NEST_GROUND) || !bed.isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                || !pos.equals(RiverBed.bedUnder(level, pos))) {
            return false;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (level.getBlockState(near).is(SavannaBlocks.TILAPIA_NEST.get())) {
                return false;
            }
        }
        return true;
    }
}
