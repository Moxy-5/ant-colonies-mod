package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteMoundBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The termite mound's share of the building on return ({@link BuildLife}; Project Apex step 8c, moved out of the shared
 * code unchanged): each block the builder numbers dig gives the soil for a mound block, and once the nest is dug as far
 * as it may be the mound is built up stage by stage, out of every player's sight. What was built while away has had
 * time to dry: a mound block goes in finished and set.
 */
final class MoundAwayBuilding implements ColonyHooks.AwayBuilder {
    /** At most this many times a second the whole mound plan is looked over. */
    private static final int MOUND_PASSES = 8;

    private final Mound mound;
    private final Colony colony;

    MoundAwayBuilding(Mound mound) {
        this.mound = mound;
        this.colony = mound.colony();
    }

    /** A dug block gives the soil for a mound block (as {@code Mound.takePellet}), once the mound is being built. */
    @Override
    public void dug(ServerLevel level, BuildLife build, BlockPos target, int bites) {
        if (mound.isActive() && !colony.isFounding()) {
            build.addSoil(TermiteMoundBlock.FINISHED);
        }
    }

    @Override
    public boolean after(ServerLevel level, BuildLife build) {
        return mound.isActive() && buildMound(level, build);
    }

    /**
     * The termite mound, built up the way the real termites do: lowest first, a stage finished before the next one the
     * colony has earned is started, each block the plan's own (soil, then carved where the plan has a tunnel; ramps,
     * spire pieces and chimney mouths; pieces reshaped; soil cleared out of the shaft). The soil comes from the nest's
     * digging and then from the galleries under the mound. True if there is more to build (time ran out, or it waits).
     */
    private boolean buildMound(ServerLevel level, BuildLife build) {
        for (int pass = 0; pass < MOUND_PASSES; pass++) {
            int before = build.changesNow();
            boolean left = false;
            boolean noSoil = false;
            Set<BlockPos> nestCells = colony.nest().nestCells(level);
            List<Map.Entry<BlockPos, MoundPlan.Part>> parts = new ArrayList<>(mound.getParts(level).entrySet());
            parts.sort(Comparator.comparingInt(entry -> entry.getKey().getY()));
            for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts) {
                if (!build.timeLeft() || build.secondFull()) {
                    return true;
                }
                BlockPos pos = entry.getKey();
                MoundPlan.Part part = entry.getValue();
                if (!level.isLoaded(pos)) {
                    continue;
                }
                MoundGrowth.Job job = mound.jobAt(level, pos, part);
                if (job == null) {
                    continue;
                }
                left = true;
                Outcome outcome = buildAt(level, build, pos, part, job, nestCells);
                if (outcome == Outcome.NO_SOIL) {
                    noSoil = true; // no soil left to dig (the galleries are used up)
                    break;
                }
                if (outcome == Outcome.DONE) {
                    build.changed();
                }
            }
            if (build.changesNow() == before && noSoil) {
                return build.waited(); // nothing to build with (the real termites would stand idle too), or it waits for a player to look away
            }
            if (build.changesNow() == before) {
                // Nothing more could be done on this stage: the next one, if a taller one is earned. As with the real
                // termites, a stage with a spot nobody can build moves on anyway (they would have stalled on it long ago).
                if (!mound.nextStage(level, !build.waited())) {
                    return left && build.waited();
                }
            }
        }
        return true;
    }

    private enum Outcome {
        DONE, SKIPPED, NO_SOIL
    }

    private Outcome buildAt(ServerLevel level, BuildLife build, BlockPos pos, MoundPlan.Part part, MoundGrowth.Job job, Set<BlockPos> nestCells) {
        BlockPos hub = colony.getCenter().below(colony.nest().shaftDepth(level));
        switch (job) {
            case SOIL -> {
                BlockPos spot = mound.spotIn(level, pos, nestCells);
                if (spot == null) {
                    return Outcome.SKIPPED; // what it stands on is not there yet
                }
                if (!build.freeAndHidden(level, spot)) {
                    return Outcome.SKIPPED;
                }
                BlockState was = level.getBlockState(spot);
                int pellets = TermiteMoundBlock.FINISHED - (was.getBlock() instanceof TermiteMoundBlock ? was.getValue(TermiteMoundBlock.LAYERS) : 0);
                if (!takeSoil(level, build, pellets)) {
                    return Outcome.NO_SOIL;
                }
                level.setBlock(spot, Mound.soil(setAge(pos)), 3);
                build.spend(pellets * BuildLife.trip(hub, spot));
                colony.away().report().add(AwayReport.What.MOUND_BUILT, 1);
            }
            case PIECE -> {
                if (!mound.isPieceSpot(level, pos, part) || !build.freeAndHidden(level, pos) || mound.shape() == null) {
                    return Outcome.SKIPPED;
                }
                if (!takeSoil(level, build, 1)) {
                    return Outcome.NO_SOIL;
                }
                BlockState piece = Mound.blockFor(mound.shape(), pos.subtract(colony.getCenter()));
                piece = piece.setValue(MoundSoil.AGE, Math.max(1, piece.getValue(MoundSoil.AGE)));
                level.setBlock(pos, Block.updateFromNeighbourShapes(piece, level, pos), 3);
                build.spend(BuildLife.trip(hub, pos));
                colony.away().report().add(AwayReport.What.MOUND_BUILT, 1);
            }
            case CARVE, RESHAPE -> {
                if (!build.freeAndHidden(level, pos)
                        || job == MoundGrowth.Job.RESHAPE && !Mound.isSoil(part) && !mound.hasPieceSupport(level, pos, part)
                        || !mound.shaping().shape(level, pos, null)) {
                    return Outcome.SKIPPED;
                }
                build.spend(BuildLife.SHAPE_TICKS + (long) BuildLife.TICKS_PER_BLOCK * hub.distManhattan(pos));
            }
            case CLEAR -> {
                if (!build.freeAndHidden(level, pos)) {
                    return Outcome.SKIPPED;
                }
                int bites = BuildLife.bites(level, pos);
                if (part == MoundPlan.Part.OPEN) {
                    build.dig(level, pos);
                    build.addSoil(TermiteMoundBlock.FINISHED); // the shaft's old soil goes into the new blocks
                } else if (!mound.shaping().shape(level, pos, null)) {
                    return Outcome.SKIPPED;
                }
                build.spend((long) bites * BuildLife.BITE_TICKS);
            }
        }
        mound.shaped(level);
        return Outcome.DONE;
    }

    /** The age a mound block built while away is put in with: the plan's own, but at least set (it has had time to dry). */
    private int setAge(BlockPos pos) {
        MoundPlan.Shape shape = mound.shape();
        int age = shape == null ? 1 : shape.age().getOrDefault(pos.subtract(colony.getCenter()), 1);
        return Math.max(1, Math.min(TermiteMoundBlock.OLDEST, age));
    }

    /** Takes this many pellets of soil, digging the next blocks of the galleries under the mound for more. False if there is none. */
    private boolean takeSoil(ServerLevel level, BuildLife build, int pellets) {
        while (build.soil() < pellets) {
            BlockPos next = colony.nest().firstDiggable(level, mound.getGalleries(level));
            if (next == null) {
                return false;
            }
            if (build.seen(level, next)) {
                return false;
            }
            build.spend((long) BuildLife.bites(level, next) * BuildLife.BITE_TICKS);
            build.dig(level, next);
            colony.away().report().add(AwayReport.What.DUG, 1);
            build.addSoil(TermiteMoundBlock.FINISHED);
        }
        build.addSoil(-pellets);
        return true;
    }
}
