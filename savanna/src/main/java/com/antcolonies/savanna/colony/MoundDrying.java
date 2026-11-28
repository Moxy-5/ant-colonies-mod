package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.antcolonies.colony.Colony;
import com.antcolonies.block.WithheldTicks;
import com.antcolonies.colony.LandReady;
import com.antcolonies.savanna.block.MoundSoil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mound's soil drying where Minecraft does not dry it (review R5-3). Mound soil sets and dries on Minecraft's
 * random ticks ({@link MoundSoil#age}), and Minecraft gives a chunk random ticks only while a player who is not a
 * spectator is within 128 blocks of it. So beside a spectator nothing the termites laid ever set (and they may only
 * build on soil that has set), and a mound left for a hundred days came back as wet as it was left. Every other clock
 * of the colony runs on game time; this makes the soil's drying do so too, with no second pace: the mound gives its
 * own cells the random ticks the game withholds, as many as the game would have given.
 *
 * <p>Once a second while the mound holds its plan (a watcher within 128 blocks, {@link Mound#keepShapes}) and its land
 * is loaded: if the game is giving the random ticks itself, nothing; else a few of the mound's cells, picked at random,
 * get one random tick each. After a pause (the colony far from every player, its land not loaded, the game closed) the
 * whole mound is walked once, a slice a second, and every block dries by the time that passed.
 */
final class MoundDrying {
    /** The chance of a random tick for one block in one game tick: 3 blocks of a 16 x 16 x 16 section a tick, Minecraft's own pace. */
    private static final double PER_TICK = 3.0 / 4096;
    /** Not looked at for longer than this (ticks): the time between counts as time away, and the whole mound dries by it. */
    private static final int PAUSE_TICKS = 100;
    /** At most this many cells a second on the walk after a pause. */
    private static final int WALK_PER_SECOND = 400;

    private final Mound mound;
    /** The drying's own dice (not the colony's: the colony's rolls stay what they were). */
    private final RandomSource random;
    /** Game time the mound's soil was last looked at. Saved. */
    long last = Colony.NOT_SET;
    /** The walk after a pause: the time to dry by (0: no walk), the cells, and how far it has got. Not saved. */
    private long pause;
    private List<BlockPos> walk = List.of();
    private int walked;
    /** The cells to pick from (those of {@link #cellsOf}), and the fraction of a pick carried over. */
    private Map<BlockPos, MoundPlan.Part> cellsOf = Map.of();
    private List<BlockPos> cells = List.of();
    private double picks;

    MoundDrying(Mound mound) {
        this.mound = mound;
        this.random = RandomSource.create(mound.colony().getId().getLeastSignificantBits() ^ 0x5011L);
    }

    /** Once a second, with the cells of the stage being built (none while the plan is not here: nothing happens). */
    void tick(ServerLevel level, Map<BlockPos, MoundPlan.Part> parts) {
        if (parts.isEmpty() || !LandReady.at(level, mound.center())) {
            return; // (the time goes on counting: it is dried by on the next look)
        }
        long now = level.getGameTime();
        long passed = last == Colony.NOT_SET ? 0 : now - last;
        last = now;
        if (pause > 0) {
            walkOn(level);
        } else if (passed > PAUSE_TICKS) {
            pause = passed;
            walk = new ArrayList<>(parts.keySet());
            walked = 0;
            walkOn(level);
        } else if (passed > 0 && !givenByTheGame(level)) {
            give(level, parts, passed);
        }
    }

    /** A few cells picked at random get one random tick each: as many as the game gives a mound this size in this time. */
    private void give(ServerLevel level, Map<BlockPos, MoundPlan.Part> parts, long ticks) {
        if (parts != cellsOf) {
            cellsOf = parts;
            cells = new ArrayList<>(parts.keySet());
        }
        picks += cells.size() * ticks * PER_TICK;
        for (; picks >= 1; picks--) {
            for (BlockPos pos : withFooting(cells.get(random.nextInt(cells.size())))) {
                BlockState state = soilAt(level, pos);
                if (state != null) {
                    state.randomTick(level, pos, random);
                }
            }
        }
    }

    /** The next slice of the walk after a pause: each block dries by the random ticks of the whole pause at once. */
    private void walkOn(ServerLevel level) {
        double ticks = pause * PER_TICK;
        for (int n = 0; n < WALK_PER_SECOND && walked < walk.size(); n++, walked++) {
            for (BlockPos pos : withFooting(walk.get(walked))) {
                BlockState state = soilAt(level, pos);
                if (state != null) {
                    BlockState dried = MoundSoil.agedBy(state, ticks, random);
                    if (dried != state) {
                        level.setBlock(pos, dried, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        if (walked >= walk.size()) {
            pause = 0;
            walk = List.of();
        }
    }

    /** The cell, and under a cell at the mound's foot the soil filled in down to the ground (no cells of the plan). */
    private List<BlockPos> withFooting(BlockPos cell) {
        if (cell.getY() != mound.center().getY()) {
            return List.of(cell);
        }
        return List.of(cell, cell.below(), cell.below(2), cell.below(3));
    }

    /** The mound soil standing here that can still dry, or null (not loaded, something else, old already, still being built). */
    private static BlockState soilAt(ServerLevel level, BlockPos pos) {
        if (!LandReady.at(level, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return Mound.isMoundBlock(state) && state.hasProperty(MoundSoil.AGE) && state.isRandomlyTicking() ? state : null;
    }

    /** Whether Minecraft itself gives the mound's chunk random ticks now (the one judge, {@link WithheldTicks#givenByTheGame}). */
    private boolean givenByTheGame(ServerLevel level) {
        return WithheldTicks.givenByTheGame(level, new ChunkPos(mound.center()));
    }

    /** Tests only: the mound's cells get the random ticks of this many game ticks now, whoever is near. */
    void giveForTests(ServerLevel level, Map<BlockPos, MoundPlan.Part> parts, long ticks) {
        give(level, parts, ticks);
    }
}
