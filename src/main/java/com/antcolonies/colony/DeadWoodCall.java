package com.antcolonies.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.DeadTreeWoodBlock;
import com.antcolonies.block.DeadWoodBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.SoftDeadWoodBlock;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Big dead wood calls the galleries (the user's pick, 2026-10-19, after termites never found a standing dead giant 15 to
 * 30 blocks out): real termites sense rotting wood through the soil from afar, by the smell of the wood and the fungi in
 * it, and judge a log's size from how it vibrates. Every so often the colony listens round its dug holes and growing
 * tips: dead tree wood lying on the ground or standing (the dead giants' bleached wood, rotten wood, baobab fibre, the
 * old dead wood) of at least the profile's {@code min_blocks} blocks together is noticed from its {@code reach} past a
 * hole or tip, farther for bigger wood ({@link SpeciesProfile.WoodCall}). The colony then knows the wood's foot (the
 * lowest block on the ground nearest the nest), so the plant scan looks there and a gallery piece out to it (or a runway
 * from a hole near it) comes before the tips spread evenly ({@link ForagingHoles}, {@link Galleries}).
 */
final class DeadWoodCall {
    /** How often (ticks) the colony listens: 1 minute. */
    private static final int LISTEN_TICKS = 1200;
    /** Columns looked at: every second one each way (a dead giant's trunk and a fallen giant are 2 or more thick). */
    private static final int STEP = 2;
    /** How far (blocks) a piece of wood is followed from where it was first seen, as it is measured. */
    private static final int SPREAD = 16;
    /** The most blocks of one piece of wood followed (a whole dead giant with its limbs is well under this). */
    private static final int MOST = 4000;

    private final Colony colony;
    private long listenedAt = Long.MIN_VALUE;

    DeadWoodCall(Colony colony) {
        this.colony = colony;
    }

    /**
     * Listens for big dead wood round {@code from} (the dug holes and the growing tips), at most once a minute; wood
     * found is learnt by the colony's food map. Returns the feet of the wood newly learnt.
     */
    List<BlockPos> listen(ServerLevel level, Collection<BlockPos> from, SpeciesProfile.WoodCall call) {
        long now = level.getGameTime();
        List<BlockPos> learnt = new ArrayList<>();
        int far = call.farthest();
        if (far <= 0 || from.isEmpty() || listenedAt != Long.MIN_VALUE && now - listenedAt < LISTEN_TICKS && now >= listenedAt) {
            return learnt;
        }
        listenedAt = now;
        Set<Long> columns = new HashSet<>();
        Set<BlockPos> measured = new HashSet<>();
        BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos();
        for (BlockPos source : from) {
            int x0 = Math.floorDiv(source.getX() - far, STEP) * STEP;
            int z0 = Math.floorDiv(source.getZ() - far, STEP) * STEP;
            for (int x = x0; x <= source.getX() + far; x += STEP) {
                for (int z = z0; z <= source.getZ() + far; z += STEP) {
                    int dx = x - source.getX(), dz = z - source.getZ();
                    if (dx * dx + dz * dz > far * far || !columns.add(BlockPos.asLong(x, 0, z)) || !level.hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    top.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    if (measured.contains(top) || !isDeadWood(level.getBlockState(top))) {
                        continue;
                    }
                    Wood wood = measure(level, top.immutable(), measured);
                    if (wood == null || colony.foodMap().knows(wood.foot(), null) || colony.keepsOff(level, wood.foot())) {
                        continue;
                    }
                    int reach = call.reachFor(wood.blocks());
                    if (reach > 0 && nearAny(wood.foot(), from, reach)) {
                        colony.foodMap().learn(wood.foot());
                        learnt.add(wood.foot());
                        AntColoniesMod.LOGGER.debug("[wood-call] colony {} senses dead wood of {} blocks at {} (heard from {} blocks)",
                                colony.getId(), wood.blocks(), wood.foot().toShortString(), reach);
                    }
                }
            }
        }
        return learnt;
    }

    /**
     * Whether {@code food} is part of dead wood big enough to call (at least the call's {@code min_blocks} blocks
     * together): the way to it may go past the length the workers allow (a big find gets the diggers).
     */
    boolean isBig(ServerLevel level, BlockPos food, SpeciesProfile.WoodCall call) {
        if (!level.isLoaded(food) || !isDeadWood(level.getBlockState(food))) {
            return false;
        }
        Wood wood = measure(level, food, new HashSet<>());
        return wood != null && wood.blocks() >= call.minBlocks();
    }

    /** A piece of dead wood: how many blocks and its foot. */
    record Wood(int blocks, BlockPos foot) {
    }

    /**
     * Follows the dead wood touching {@code start} (up to {@link #MOST} blocks, within {@link #SPREAD} blocks of it), all
     * of it marked {@code measured}; its foot is the lowest block resting on the ground, the nearest the nest's middle.
     * Null if none of it rests on the ground (wood up in a living crown).
     */
    @Nullable
    private Wood measure(ServerLevel level, BlockPos start, Set<BlockPos> measured) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start);
        measured.add(start);
        int blocks = 0;
        BlockPos foot = null;
        BlockPos center = colony.getCenter();
        while (!open.isEmpty() && blocks < MOST) {
            BlockPos pos = open.poll();
            blocks++;
            BlockPos below = pos.below();
            BlockState under = level.getBlockState(below);
            if (!isDeadWood(under) && under.isFaceSturdy(level, below, Direction.UP) && (foot == null || pos.getY() < foot.getY()
                    || pos.getY() == foot.getY() && pos.distSqr(center) < foot.distSqr(center)) && isUncovered(level, pos)) {
                foot = pos;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (Math.abs(next.getX() - start.getX()) <= SPREAD && Math.abs(next.getZ() - start.getZ()) <= SPREAD
                        && Math.abs(next.getY() - start.getY()) <= 2 * SPREAD && level.isLoaded(next) && !measured.contains(next)
                        && isDeadWood(level.getBlockState(next))) {
                    measured.add(next);
                    open.add(next);
                }
            }
        }
        return foot == null ? null : new Wood(blocks, foot);
    }

    /**
     * Whether this wood lies open over the ground (only more of the wood, air, plants or mud plates over it), as the
     * colony sees its food: not a root buried under the soil (the dip of 2026-10-19: a dead giant's roots ran on under
     * the grass past the dip it stood in, the foot was learnt on a buried root the colony never saw as food, and no
     * gallery went out to it).
     */
    private boolean isUncovered(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos over = pos.mutable();
        for (int up = 1; up <= 2 * SPREAD; up++) {
            over.setY(pos.getY() + up);
            BlockState state = level.getBlockState(over);
            if (!isDeadWood(state)) {
                return state.isAir() || MudCrustBlock.isCrust(state) || !state.blocksMotion()
                        || state.is(com.antcolonies.registry.TreeBlocks.TREE_WOOD) || state.is(com.antcolonies.registry.TreeBlocks.TREE_LEAVES);
            }
        }
        return true;
    }

    /** Dead tree wood the colony eats: the dead giants' wood, rotten wood and baobab fibre, the old dead wood. */
    private boolean isDeadWood(BlockState state) {
        return (state.getBlock() instanceof DeadTreeWoodBlock || state.getBlock() instanceof SoftDeadWoodBlock
                || state.getBlock() instanceof DeadWoodBlock) && colony.food().isFoodPlant(state);
    }

    private static boolean nearAny(BlockPos pos, Collection<BlockPos> others, int within) {
        for (BlockPos other : others) {
            double dx = pos.getX() - other.getX(), dz = pos.getZ() - other.getZ();
            if (dx * dx + dz * dz <= (double) within * within) {
                return true;
            }
        }
        return false;
    }
}
