package com.antcolonies.colony;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The foraging holes of one colony (species whose profile has {@code nest.foraging_holes}: termites): small holes in
 * the ground a few blocks out from the nest, at the end of shallow tunnels from the entrance shaft (see
 * {@link NestPlan#foragingTunnels}). The workers dig the tunnels like the rest of the nest; the holes are opened when it
 * is time to forage ({@link Colony#isForagingTime}) and plugged with soil from inside when it is over, once everybody is
 * back in (or after a while: a straggler finds another way in, such as a chimney).
 *
 * <p>The middle hole: while a young mound is too low to be roofed over, the entrance in its middle (the top of the
 * nest's shaft) is open to the sky. A colony that keeps to a schedule plugs it by day like the foraging holes (the
 * user's request: real mounds are closed) and opens it again at night ({@link Nest#mayOpenEntrance}). Once the mound
 * covers it, it stays open: it is then only the way up into the mound's own shaft.
 */
public class ForagingHoles {
    /** After the foraging time is over the holes are plugged at the latest this long after (ticks), even with workers still outside. */
    static final int PLUG_GRACE_TICKS = 600;
    /** A worker's claim on a hole it plugs lasts this long (ticks). */
    private static final int CLAIM_TICKS = 600;
    /** How often (ticks) the colony checks whether any of its members are still outside. */
    private static final int OUTSIDE_CHECK_TICKS = 20;

    private final Colony colony;
    private final Claims<UUID> plugClaims = new Claims<>();
    /** Which hole each worker that plugs one is on (one worker to a hole). */
    private final java.util.Map<UUID, BlockPos> plugging = new java.util.HashMap<>();
    /** Game time the foraging time was first seen to be over (not saved: after a reload the grace starts again). */
    private long closedSince = Colony.NOT_SET;
    private long outsideCheckedAt = Long.MIN_VALUE;
    private boolean anyoneOutside;

    ForagingHoles(Colony colony) {
        this.colony = colony;
    }

    /** The foraging tunnels of the nest's plan (none until the plan is made, or if the species has none). */
    public List<NestPlan.ForagingTunnel> tunnels(Level level) {
        return colony.nest().getForagingTunnels(level);
    }

    /** Whether this block is one of the colony's foraging holes. */
    public boolean isHole(Level level, BlockPos pos) {
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (tunnel.hole().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The middle hole as a hole to plug from the top of the shaft under it, or null when it needs no plugging: a species
     * that keeps no schedule or builds no mound, a colony still founding (the founders seal it themselves), the shaft
     * not dug, or the mound already covers it.
     */
    @Nullable
    NestPlan.ForagingTunnel middleHole(Level level) {
        BlockPos entrance = colony.nest().getEntrance();
        if (!colony.keepsASchedule() || !colony.mound().isActive() || !level.isLoaded(entrance)
                || !Nest.isDugOut(level, entrance.below()) || colony.nest().isEntranceCovered(level)) {
            return null;
        }
        return new NestPlan.ForagingTunnel(List.of(entrance.below()), entrance, Direction.UP);
    }

    /** The foraging holes, and the middle hole while it needs plugging by day. */
    private List<NestPlan.ForagingTunnel> holesToPlug(Level level) {
        NestPlan.ForagingTunnel middle = middleHole(level);
        if (middle == null) {
            return tunnels(level);
        }
        List<NestPlan.ForagingTunnel> all = new java.util.ArrayList<>(tunnels(level));
        all.add(middle);
        return all;
    }

    /** Whether the whole tunnel is dug out, so its hole leads somewhere. */
    public static boolean isDug(Level level, NestPlan.ForagingTunnel tunnel) {
        for (BlockPos pos : tunnel.tunnel()) {
            if (!Nest.isDugOut(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the hole is open: nothing solid in it, no water. */
    public static boolean isOpen(Level level, BlockPos hole) {
        BlockState state = level.getBlockState(hole);
        return level.isLoaded(hole) && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** Whether the hole holds the plug of soil the workers packed into it. */
    public boolean isPlug(Level level, BlockPos pos) {
        return isHole(level, pos) && colony.profile().nest().isPlug(level.getBlockState(pos));
    }

    /** Whether a worker may bite this hole open now: it is foraging time and the hole is closed with soil or its plug. */
    public boolean mayOpen(Level level, BlockPos hole) {
        if (!colony.isForagingTime(level) || !level.isLoaded(hole)) {
            return false;
        }
        BlockState state = level.getBlockState(hole);
        return (state.is(ModTags.DIGGABLE) || colony.profile().nest().isPlug(state)) && !touchesFluid(level, hole);
    }

    /** A closed hole to open now (its tunnel is dug and it is foraging time), or null. */
    @Nullable
    BlockPos holeToOpen(Level level) {
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel) && !isOpen(level, tunnel.hole()) && mayOpen(level, tunnel.hole())) {
                return tunnel.hole();
            }
        }
        return null;
    }

    /** How many holes are open now (for the colony list). */
    public int countOpen(Level level) {
        int open = 0;
        for (NestPlan.ForagingTunnel tunnel : tunnels(level)) {
            if (isDug(level, tunnel) && isOpen(level, tunnel.hole())) {
                open++;
            }
        }
        return open;
    }

    /**
     * Whether open holes should be plugged now: the foraging time is over, and either every member of the colony is
     * back inside or {@link #PLUG_GRACE_TICKS} have passed since.
     */
    public boolean wantsPlugging(Level level) {
        long now = level.getGameTime();
        if (colony.isForagingTime(level)) {
            closedSince = Colony.NOT_SET;
            return false;
        }
        if (closedSince == Colony.NOT_SET) {
            closedSince = now;
        }
        return now - closedSince >= PLUG_GRACE_TICKS || !isAnyoneOutside(level);
    }

    /** Whether any member of the colony is outside the nest and the mound (checked once a second). */
    public boolean isAnyoneOutside(Level level) {
        long now = level.getGameTime();
        if (now - outsideCheckedAt >= OUTSIDE_CHECK_TICKS || now < outsideCheckedAt) {
            outsideCheckedAt = now;
            AABB area = colony.nestArea().inflate(8);
            anyoneOutside = !level.getEntitiesOfClass(AbstractAntEntity.class, area,
                    ant -> ant.isAlive() && colony.getId().equals(ant.getColonyId()) && !colony.nest().isInside(level, ant.blockPosition())).isEmpty();
        }
        return anyoneOutside;
    }

    /** An open hole for this worker to plug now (claimed for it, one worker to a hole), or null. */
    @Nullable
    public NestPlan.ForagingTunnel pickHoleToPlug(Level level, AbstractAntEntity ant) {
        if (!wantsPlugging(level)) {
            return null;
        }
        long now = level.getGameTime();
        plugClaims.prune(now);
        plugging.keySet().removeIf(id -> !plugClaims.contains(id));
        for (NestPlan.ForagingTunnel tunnel : holesToPlug(level)) {
            if (!isDug(level, tunnel) || !isOpen(level, tunnel.hole())) {
                continue;
            }
            boolean taken = false;
            for (java.util.Map.Entry<UUID, BlockPos> entry : plugging.entrySet()) {
                taken |= !entry.getKey().equals(ant.getUUID()) && entry.getValue().equals(tunnel.hole());
            }
            if (!taken) {
                plugClaims.put(ant.getUUID(), now + CLAIM_TICKS);
                plugging.put(ant.getUUID(), tunnel.hole());
                return tunnel;
            }
        }
        return null;
    }

    public void releasePlug(UUID ant) {
        plugClaims.remove(ant);
        plugging.remove(ant);
    }

    /**
     * Packs the hole shut with soil (the species' plug block). False if a creature (a termite on its way through, a player) is
     * in the hole: it must move first, nobody is shut in.
     */
    public boolean plug(Level level, BlockPos hole) {
        if (!isOpen(level, hole)) {
            return false;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(hole), LivingEntity::isAlive).isEmpty()) {
            return false;
        }
        return level.setBlockAndUpdate(hole, colony.profile().nest().plugState());
    }

    private static boolean touchesFluid(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
