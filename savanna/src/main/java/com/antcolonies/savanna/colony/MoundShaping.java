package com.antcolonies.savanna.colony;

import com.antcolonies.colony.NestWalls;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.block.MoundSoil;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteTunnelBlock;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The shaping work of a termite mound ({@link Mound}): the plan's tunnels carved into finished soil (and their doors
 * into the shaft opened), pieces reshaped into the plan's ones (or filled up where they stand), and pieces cleared away
 * where the plan has nothing. No soil is needed for any of it. Who shapes what is claimed here, one termite to a cell
 * (not saved).
 */
public class MoundShaping {
    private final Mound mound;
    /** Who carves or shapes which cell (not saved). */
    private final Map<UUID, Mound.Claim> carveClaims = new HashMap<>();

    MoundShaping(Mound mound) {
        this.mound = mound;
    }

    /**
     * Shaping work of the plan to do now, claimed for this termite, or null (lowest first): a tunnel to carve (finished
     * soil where the plan has a tunnel, or a tunnel block that does not open yet into the shaft beside it), a piece to
     * reshape into the plan's one, or a piece to clear away where soil goes. No soil is needed for any of it. Carving is
     * inside work, so it goes on by day too; the rest only while it can be done now ({@link #isWorkableNow}).
     */
    @Nullable
    public BlockPos pickShapeJob(Level level, AbstractAntEntity ant) {
        if (!mound.isActive()) {
            return null;
        }
        long now = level.getGameTime();
        carveClaims.values().removeIf(claim -> claim.until() <= now);
        BlockPos best = null;
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : mound.getParts(level).entrySet()) {
            BlockPos pos = entry.getKey();
            if ((best != null && pos.getY() >= best.getY()) || !level.isLoaded(pos) || Mound.isClaimedByOther(carveClaims, pos, ant.getUUID())) {
                continue;
            }
            MoundGrowth.Job job = shapeJobAt(level, pos);
            if (job != null && (job == MoundGrowth.Job.CARVE || mound.isWorkableNow(level, pos)) && (job != MoundGrowth.Job.RESHAPE || Mound.isSoil(entry.getValue()) || mound.hasPieceSupport(level, pos, entry.getValue()))) {
                best = pos;
            }
        }
        if (best != null) {
            carveClaims.put(ant.getUUID(), new Mound.Claim(best, now + Mound.CLAIM_TICKS));
        }
        return best;
    }

    public void releaseShapeJob(UUID ant) {
        carveClaims.remove(ant);
    }

    /** The shaping job in a cell now (carving, reshaping, clearing a piece), or null. */
    @Nullable
    public MoundGrowth.Job shapeJobAt(Level level, BlockPos pos) {
        MoundPlan.Part part = mound.getParts(level).get(pos);
        if (part == null || part == MoundPlan.Part.OPEN) {
            return null; // the shaft is dug out by the diggers (it gives soil)
        }
        MoundGrowth.Job job = mound.jobAt(level, pos, part);
        return job == MoundGrowth.Job.CARVE || job == MoundGrowth.Job.RESHAPE || job == MoundGrowth.Job.CLEAR ? job : null;
    }

    /** Whether there is a tunnel to carve here now (or a tunnel's door into the shaft to open). */
    public boolean isCarveJob(Level level, BlockPos pos) {
        if (mound.getParts(level).get(pos) != MoundPlan.Part.TUNNEL || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TermiteMoundBlock) {
            return TermiteMoundBlock.isFinished(state);
        }
        if (state.getBlock() instanceof TermiteTunnelBlock) {
            for (Direction side : doorsOf(level, pos)) {
                if (!state.getValue(TermiteTunnelBlock.OPEN.get(side))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The sides of a tunnel cell that open into the shaft: toward a cell the plan keeps open that is empty now. */
    List<Direction> doorsOf(Level level, BlockPos pos) {
        List<Direction> doors = new ArrayList<>();
        Map<BlockPos, MoundPlan.Part> parts = mound.getParts(level);
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (parts.get(beside) == MoundPlan.Part.OPEN && level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
                doors.add(side);
            }
        }
        return doors;
    }

    /**
     * Does the shaping job here: carves the tunnel (a tunnel block of the soil's age, joined to the channels round it
     * and open into the shaft beside it, and toward {@code from} where the termite works from if that is the shaft),
     * reshapes the piece into the plan's one (keeping its age; into soil or a tunnel block where the plan has one), or
     * clears the piece away. False if there is no such job
     * any more.
     */
    public boolean shape(Level level, BlockPos pos, @Nullable BlockPos from) {
        MoundGrowth.Job job = shapeJobAt(level, pos);
        if (job == null) {
            return false;
        }
        MoundPlan.Shape plan = mound.shape();
        BlockPos center = mound.center();
        BlockState was = level.getBlockState(pos);
        int age = was.hasProperty(MoundSoil.AGE) ? was.getValue(MoundSoil.AGE) : 0;
        boolean done;
        if (job == MoundGrowth.Job.CARVE) {
            List<Direction> open = doorsOf(level, pos);
            Direction toward = from == null ? null : MoundGrowth.sideToward(pos, from);
            if (toward != null && !open.contains(toward) && MoundReach.isHollowInside(level, mound.colony(), from)) {
                open.add(toward); // carved from the nest (or the shaft): it opens toward where the termite came from
            }
            done = level.setBlockAndUpdate(pos, SavannaBlocks.TERMITE_TUNNEL.get().stateFor(level, pos, age, open));
        } else if (job == MoundGrowth.Job.RESHAPE) {
            // A piece is filled up where it stands. Insects in it must not be buried: a player or an animal there stops
            // it (try again later); termites crawling up its channel keep a channel (it becomes a tunnel block instead
            // of solid soil), and one standing on it outside the channel is lifted out.
            List<LivingEntity> inCell = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive);
            if (inCell.stream().anyMatch(living -> !(living instanceof AbstractAntEntity))) {
                return false;
            }
            BlockState filled;
            if (mound.getParts(level).get(pos) == MoundPlan.Part.TUNNEL || !inCell.isEmpty() && plan != null
                    && Mound.blockFor(plan, pos.subtract(center)).getBlock() instanceof TermiteMoundBlock) {
                // A piece (the bottom of a spire that moves up) becomes a tunnel block where it stands, joined to the channels round it.
                filled = SavannaBlocks.TERMITE_TUNNEL.get().stateFor(level, pos, age, doorsOf(level, pos));
            } else {
                filled = Block.updateFromNeighbourShapes(Mound.blockFor(plan, pos.subtract(center)).setValue(MoundSoil.AGE, age), level, pos);
            }
            done = level.setBlockAndUpdate(pos, filled);
            if (done) {
                double top = pos.getY() + filled.getCollisionShape(level, pos).max(Direction.Axis.Y);
                for (LivingEntity ant : inCell) {
                    if (!(ant instanceof AbstractAntEntity insect && insect.getNavigator().isInChannel()) && ant.getBoundingBox().minY < top) {
                        NestWalls.liftOnto(level, ant, pos, top);
                    }
                }
            }
        } else {
            done = level.destroyBlock(pos, false);
        }
        if (done) {
            mound.shaped(level);
        }
        return done;
    }
}
