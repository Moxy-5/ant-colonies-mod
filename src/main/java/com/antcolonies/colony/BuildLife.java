package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The brain, part 2, step 2d: the builders among the numbers. The user's decision (2026-10-14): building while away is
 * applied when the player returns. While the colony is away its land is not loaded, so no block can change: the brain
 * only counts the builders' working time ({@link #tickAway}: every builder-age number works every second). When the
 * nest is loaded again, that time is spent ({@link #catchUp}), out of every player's sight, on exactly the work the real
 * builders would have done, in their order and by their rules: the nest's plan dug (the shaft, the rooms the colony may
 * dig now, the side rooms, the gardens, the foraging tunnels and the feeding galleries; walls of mud first where the
 * species plasters them), the soil heaped on the ant hill, and the termite mound built up stage by stage (soil from the
 * galleries under it, its pieces, its tunnels carved). What was built while away has had time to dry: a mound block
 * goes in finished and set.
 *
 * <p><b>How much.</b> The builders work at the real workers' pace: a bite of soil every {@link #BITE_TICKS}, a pellet
 * carried {@link #TICKS_PER_BLOCK} a block there and back (as the forager numbers walk), a mound block shaped in
 * {@link #SHAPE_TICKS}. The plan itself still sets how far they may go (the nest's stages by the colony's size, the
 * mound's stage by what the colony has earned), so time left over once nothing more may be built is dropped: the
 * builders had nothing to do.
 *
 * <p>Species-neutral: any species with a nest plan; the ant hill for species without a mound, the mound for those with one.
 */
public class BuildLife {
    /** How long (ticks) a bite of soil takes (as {@code DigGoal}). */
    static final int BITE_TICKS = 10;
    /** How long (ticks) a builder takes for each block of the way with a pellet, there or back (the forager numbers' pace). */
    static final int TICKS_PER_BLOCK = 8;
    /** How long (ticks) shaping a block of the mound takes (carving a tunnel, reshaping a piece; as {@code ShapeMoundGoal}). */
    static final int SHAPE_TICKS = 30;
    /** At most this many blocks change a second (a long time away can owe a lot; the rest comes the next second). */
    private static final int PER_SECOND = 200;
    /** At most this many times a second the whole mound plan is looked over. */
    private static final int MOUND_PASSES = 8;

    private final Colony colony;
    /** The builders' working time (ticks of one builder's work) from while away, not spent yet. Saved. */
    private long work;
    /** Pellets of soil dug and not built into the mound yet (termites). Saved. */
    private int soil;
    /** Whether the last second's catch-up waited for something (a player looking, an insect in the way). Not saved. */
    private boolean waited;

    BuildLife(Colony colony) {
        this.colony = colony;
    }

    /** Whether there is building time from while away still to spend. */
    public boolean hasWork() {
        return work > 0;
    }

    /** The builders' time still to spend (ticks of one builder's work), for tests and the colony list. */
    public long work() {
        return work;
    }

    /** How many of the worker numbers are builders: the middle-aged ones (the colony shares out its jobs, see {@link Colony#shareOut}). */
    public int buildersInside(ServerLevel level) {
        int workers = colony.population().inside(WorkerAntEntity.CASTE);
        if (workers <= 0) {
            return 0;
        }
        int[] ages = colony.population().insideByAge(level.getGameTime());
        return Colony.shareOut(workers, colony.profile().colony().nurses(workers), ages)[ColonyRole.BUILDER.ordinal()];
    }

    /** Once a second while away (from {@link AwayLife#tickAway}): every builder number works the second. Never touches the world. */
    void tickAway(ServerLevel level) {
        if (colony.isFounding()) {
            // The founders (the queen alone, or the royal pair) dig their own chamber at the real pace (the user's bug,
            // 2026-10-15: a founding nest dug nothing while away).
            if (colony.life().foundersAtWork()) {
                work += colony.life().founders() * 20L;
            }
            return;
        }
        int builders = buildersInside(level);
        if (builders > 0) {
            work += builders * 20L;
        }
    }

    // --- coming back ----------------------------------------------------------------------------------------------

    /** Who claims a pile of night soil for the builder numbers (see {@link #carryNightSoil}). */
    private static final java.util.UUID BUILDERS = new java.util.UUID(0L, 0x4255494cL);

    /**
     * Once a second while the whole nest is loaded and there is building time left (from {@link AwayLife#settle}): the
     * work is done, out of every player's sight ({@code eyes}). True if any block changed. Once nothing more may be
     * built (or dug) the time left over is dropped.
     */
    boolean catchUp(ServerLevel level, List<Vec3> eyes) {
        boolean founders = colony.life().foundedAway();
        if (work <= 0 && !founders) {
            return false;
        }
        waited = false;
        int[] changes = {0};
        boolean nestLeft = work > 0 && (carryNightSoil(level, eyes, changes) || digNest(level, eyes, changes));
        boolean moundLeft = work > 0 && colony.mound().isActive() && buildMound(level, eyes, changes);
        if (!nestLeft && founders) {
            settleFounders(level, eyes, changes);
        }
        if (!nestLeft && !moundLeft && !waited) {
            if (work > 0) {
                log("is done");
            }
            work = 0; // nothing more to do: the builders had time to spare
            soil = 0; // (soil nobody needed was left in the galleries)
        }
        return changes[0] > 0;
    }

    private void log(String what) {
        AntColoniesMod.LOGGER.info("[away-build] colony {} {}: {} ticks of work left over, {} soil", colony.getId(), what, work, soil);
    }

    /**
     * The night soil first (as the real builders do each morning, see {@link NightSoil}): the piles near the top of
     * the nest are carried up onto the hill before anything more is dug. True if some still waits (time ran out, or a
     * player can see the pile or the hill).
     */
    private boolean carryNightSoil(ServerLevel level, List<Vec3> eyes, int[] changes) {
        Hill hill = colony.hill();
        if (colony.mound().numbers() != null || colony.isFounding()) {
            return false;
        }
        while (work > 0 && hill.hasMoundRoom(level)) {
            if (changes[0] >= PER_SECOND) {
                return true;
            }
            BlockPos pile = hill.nightSoil().claimPile(level, BUILDERS);
            if (pile == null) {
                return false; // nothing waits
            }
            hill.nightSoil().release(BUILDERS);
            if (!Population.outOfSight(level, pile, eyes)) {
                waited = true;
                return true;
            }
            BlockPos spot = hill.pickMoundSpot(level, colony.random(), null);
            if (spot == null) {
                return false;
            }
            if (!freeAndHidden(level, spot, eyes)) {
                return true;
            }
            if (hill.nightSoil().takeLayer(level, pile, BUILDERS) == null || !hill.placePellet(level, spot)) {
                return false;
            }
            work -= trip(pile, spot);
            changes[0]++;
            colony.away().report().add(AwayReport.What.HILL, 1);
        }
        return false;
    }

    /** The nest's plan, dug in the real diggers' order. True if there is more to dig (time ran out, or it waits). */
    private boolean digNest(ServerLevel level, List<Vec3> eyes, int[] changes) {
        boolean ants = colony.mound().numbers() == null;
        Hill hill = colony.hill();
        while (work > 0) {
            if (changes[0] >= PER_SECOND) {
                return true;
            }
            BlockPos target = colony.nest().findPlannedDigTarget(level);
            if (target == null) {
                return false; // dug as far as the colony may dig now
            }
            if (!Population.outOfSight(level, target, eyes)) {
                waited = true;
                return true;
            }
            if (target.equals(colony.getCenter()) && colony.nest().isPlantInEntrance(level)) {
                colony.nest().clearPlantInEntrance(level, null); // (a plant in the entrance: bitten off, no soil)
                changes[0]++;
                work -= BITE_TICKS;
                continue;
            }
            // Ants carry the soil up onto the hill while it has room (the first spot is looked at before the block is
            // dug). Founders leave theirs where it falls (they carry no soil, as when they dig here).
            boolean founding = colony.isFounding();
            BlockPos heap = ants && !founding && hill.hasMoundRoom(level) ? hill.pickMoundSpot(level, colony.random(), null) : null;
            if (heap != null && !freeAndHidden(level, heap, eyes)) {
                waited = true;
                return true;
            }
            if (!colony.walls().wallFirst(level, target)) {
                waited = true; // a plate of mud could not go in yet
                return true;
            }
            int bites = bites(level, target);
            dig(level, target);
            changes[0]++;
            work -= (long) bites * BITE_TICKS;
            colony.away().report().add(AwayReport.What.DUG, 1);
            if (ants) {
                for (int i = 0; i < bites && heap != null; i++) {
                    if (!hill.placePellet(level, heap)) {
                        break;
                    }
                    work -= trip(target, heap);
                    changes[0]++;
                    colony.away().report().add(AwayReport.What.HILL, 1);
                    heap = hill.hasMoundRoom(level) ? hill.pickMoundSpot(level, colony.random(), null) : null;
                    if (heap != null && !freeAndHidden(level, heap, eyes)) {
                        heap = null; // a player can see the hill now: the rest of this block's soil is left behind
                    }
                }
            } else if (colony.mound().isActive() && !founding) {
                soil += TermiteMoundBlock.FINISHED; // a dug block gives the soil for a mound block (as Mound.takePellet)
            }
        }
        return true;
    }

    /**
     * The termite mound, built up the way the real termites do: lowest first, a stage finished before the next one the
     * colony has earned is started, each block the plan's own (soil, then carved where the plan has a tunnel; ramps,
     * spire pieces and chimney mouths; pieces reshaped; soil cleared out of the shaft). The soil comes from the nest's
     * digging and then from the galleries under the mound. True if there is more to build (time ran out, or it waits).
     */
    private boolean buildMound(ServerLevel level, List<Vec3> eyes, int[] changes) {
        Mound mound = colony.mound();
        for (int pass = 0; pass < MOUND_PASSES; pass++) {
            int before = changes[0];
            boolean left = false;
            boolean noSoil = false;
            Set<BlockPos> nestCells = colony.nest().nestCells(level);
            List<Map.Entry<BlockPos, MoundPlan.Part>> parts = new ArrayList<>(mound.getParts(level).entrySet());
            parts.sort(Comparator.comparingInt(entry -> entry.getKey().getY()));
            for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts) {
                if (work <= 0 || changes[0] >= PER_SECOND) {
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
                Outcome outcome = buildAt(level, eyes, mound, pos, part, job, nestCells);
                if (outcome == Outcome.NO_SOIL) {
                    noSoil = true; // no soil left to dig (the galleries are used up)
                    break;
                }
                if (outcome == Outcome.DONE) {
                    changes[0]++;
                }
            }
            if (changes[0] == before && noSoil) {
                return waited; // nothing to build with (the real termites would stand idle too), or it waits for a player to look away
            }
            if (changes[0] == before) {
                // Nothing more could be done on this stage: the next one, if a taller one is earned. As with the real
                // termites, a stage with a spot nobody can build moves on anyway (they would have stalled on it long ago).
                if (!mound.nextStage(level, !waited)) {
                    return left && waited;
                }
            }
        }
        return true;
    }

    private enum Outcome {
        DONE, SKIPPED, NO_SOIL
    }

    private Outcome buildAt(ServerLevel level, List<Vec3> eyes, Mound mound, BlockPos pos, MoundPlan.Part part, MoundGrowth.Job job,
            Set<BlockPos> nestCells) {
        BlockPos hub = colony.getCenter().below(colony.nest().shaftDepth(level));
        switch (job) {
            case SOIL -> {
                BlockPos spot = mound.spotIn(level, pos, nestCells);
                if (spot == null) {
                    return Outcome.SKIPPED; // what it stands on is not there yet
                }
                if (!freeAndHidden(level, spot, eyes)) {
                    return Outcome.SKIPPED;
                }
                BlockState was = level.getBlockState(spot);
                int pellets = TermiteMoundBlock.FINISHED - (was.getBlock() instanceof TermiteMoundBlock ? was.getValue(TermiteMoundBlock.LAYERS) : 0);
                if (!takeSoil(level, eyes, pellets)) {
                    return Outcome.NO_SOIL;
                }
                level.setBlock(spot, Mound.soil(setAge(mound, pos)), 3);
                work -= pellets * trip(hub, spot);
                colony.away().report().add(AwayReport.What.MOUND_BUILT, 1);
            }
            case PIECE -> {
                if (!mound.isPieceSpot(level, pos, part) || !freeAndHidden(level, pos, eyes) || mound.shape() == null) {
                    return Outcome.SKIPPED;
                }
                if (!takeSoil(level, eyes, 1)) {
                    return Outcome.NO_SOIL;
                }
                BlockState piece = Mound.blockFor(mound.shape(), pos.subtract(colony.getCenter()));
                piece = piece.setValue(MoundSoil.AGE, Math.max(1, piece.getValue(MoundSoil.AGE)));
                level.setBlock(pos, Block.updateFromNeighbourShapes(piece, level, pos), 3);
                work -= trip(hub, pos);
                colony.away().report().add(AwayReport.What.MOUND_BUILT, 1);
            }
            case CARVE, RESHAPE -> {
                if (!freeAndHidden(level, pos, eyes)
                        || job == MoundGrowth.Job.RESHAPE && !Mound.isSoil(part) && !mound.hasPieceSupport(level, pos, part)
                        || !mound.shaping().shape(level, pos, null)) {
                    return Outcome.SKIPPED;
                }
                work -= SHAPE_TICKS + (long) TICKS_PER_BLOCK * hub.distManhattan(pos);
            }
            case CLEAR -> {
                if (!freeAndHidden(level, pos, eyes)) {
                    return Outcome.SKIPPED;
                }
                int bites = bites(level, pos);
                if (part == MoundPlan.Part.OPEN) {
                    dig(level, pos);
                    soil += TermiteMoundBlock.FINISHED; // the shaft's old soil goes into the new blocks
                } else if (!mound.shaping().shape(level, pos, null)) {
                    return Outcome.SKIPPED;
                }
                work -= (long) bites * BITE_TICKS;
            }
        }
        mound.shaped(level);
        return Outcome.DONE;
    }

    /** The age a mound block built while away is put in with: the plan's own, but at least set (it has had time to dry). */
    private int setAge(Mound mound, BlockPos pos) {
        MoundPlan.Shape shape = mound.shape();
        int age = shape == null ? 1 : shape.age().getOrDefault(pos.subtract(colony.getCenter()), 1);
        return Math.max(1, Math.min(TermiteMoundBlock.OLDEST, age));
    }

    /** Takes this many pellets of soil, digging the next blocks of the galleries under the mound for more. False if there is none. */
    private boolean takeSoil(ServerLevel level, List<Vec3> eyes, int pellets) {
        while (soil < pellets) {
            BlockPos next = colony.nest().firstDiggable(level, colony.mound().getGalleries(level));
            if (next == null) {
                return false;
            }
            if (!Population.outOfSight(level, next, eyes)) {
                waited = true;
                return false;
            }
            work -= (long) bites(level, next) * BITE_TICKS;
            dig(level, next);
            colony.away().report().add(AwayReport.What.DUG, 1);
            soil += TermiteMoundBlock.FINISHED;
        }
        soil -= pellets;
        return true;
    }

    /** How many bites a block takes (as {@code DigGoal}: plain dirt about 6). */
    static int bites(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return Math.max(1, (int) Math.ceil(state.getDestroySpeed(level, pos) * 12.0F));
    }

    /**
     * The founders finished their chamber and closed themselves in while away (see {@link NestLife}): now, out of every
     * player's sight, what is left of their digging is done (the brain counted it dug), the queen (and the king) are
     * put in their chamber, and, while they are still alone, the entrance is packed shut behind them. Sets
     * {@link #waited} if a player can see it or something is in the way.
     */
    private void settleFounders(ServerLevel level, List<Vec3> eyes, int[] changes) {
        Nest nest = colony.nest();
        List<BlockPos> left = nest.foundersDigLeft(level);
        if (left == null) {
            waited = true;
            return;
        }
        for (BlockPos pos : left) {
            if (!Population.outOfSight(level, pos, eyes)) {
                waited = true;
                return;
            }
            if (pos.equals(colony.getCenter()) && nest.isPlantInEntrance(level)) {
                nest.clearPlantInEntrance(level, null);
            } else if (colony.walls().wallFirst(level, pos)) {
                dig(level, pos);
                colony.away().report().add(AwayReport.What.DUG, 1);
            } else {
                waited = true;
                return;
            }
            changes[0]++;
        }
        NestPlan.Chamber home = nest.getHomeChamber(level);
        if (home == null) {
            waited = true;
            return;
        }
        BlockPos middle = home.center();
        Vec3 spot = Vec3.atBottomCenterOf(middle);
        List<LivingEntity> founders = new ArrayList<>();
        LivingEntity queen = colony.getQueen(level);
        if (queen != null) {
            founders.add(queen);
        }
        founders.addAll(level.getEntitiesOfClass(com.antcolonies.entity.TermiteKingEntity.class, colony.nestArea().inflate(16.0),
                king -> king.isAlive() && colony.getId().equals(king.getColonyId())));
        for (int i = 0; i < founders.size(); i++) {
            LivingEntity founder = founders.get(i);
            Vec3 to = spot.add(i == 0 ? 0.0 : 0.6, 0.0, 0.0); // (the king at her side)
            if (founder.position().distanceToSqr(to) < 0.25) {
                continue;
            }
            if (!Population.outOfSight(level, founder.getEyePosition(), eyes) || !Population.outOfSight(level, middle, eyes)) {
                waited = true;
                return;
            }
            if (founder instanceof com.antcolonies.entity.AbstractAntEntity ant) {
                ant.getNavigator().stop();
            }
            founder.moveTo(to.x, to.y, to.z, founder.getYRot(), 0.0F);
            changes[0]++;
        }
        BlockPos hole = nest.getEntrance();
        if (colony.isFounding() && colony.profile().nest().sealWhileFounding() && nest.isEntranceOpen(level)) {
            if (!freeAndHidden(level, hole, eyes)) {
                waited = true;
                return;
            }
            level.setBlock(hole, colony.profile().nest().plugState(), 3);
            changes[0]++;
        }
        colony.life().foundersSettled();
        AntColoniesMod.LOGGER.info("[away-build] colony {}: the founders are in their chamber at {}{}", colony.getId(), middle.toShortString(),
                colony.isFounding() ? ", the hole shut" : "");
    }

    /** The block is dug out, quietly (nobody is there to hear it). */
    private void dig(ServerLevel level, BlockPos pos) {
        colony.nest().clearDigProgress(pos);
        colony.mound().blockDug(level, pos);
        level.removeBlock(pos, false);
    }

    /** The time (ticks) a pellet takes from where it was dug to where it goes, and the builder back. */
    static long trip(BlockPos from, BlockPos to) {
        return 2L * TICKS_PER_BLOCK * from.distManhattan(to);
    }

    /** Whether nobody stands in this cell (nobody is ever buried, docs/BUGS.md 9 and 6g) and no player can see it. */
    private boolean freeAndHidden(ServerLevel level, BlockPos pos, List<Vec3> eyes) {
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()
                || !Population.outOfSight(level, pos, eyes)) {
            waited = true;
            return false;
        }
        return true;
    }

    /** For the colony list: "builders' work owed: 2.5 builder-hours" (empty if none). */
    public String describe() {
        if (work <= 0) {
            return "";
        }
        return String.format(java.util.Locale.ROOT, "%.1f builder-hours of building", work / 1000.0);
    }

    // --- tests ----------------------------------------------------------------------------------------------------

    /** Tests only: the builders' time to spend, as if they had worked that long while away. */
    public void setWorkForTests(long ticks) {
        work = ticks;
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag building = new CompoundTag();
        building.putLong("work", work);
        building.putInt("soil", soil);
        tag.put("awayBuilding", building);
    }

    void load(CompoundTag tag) {
        CompoundTag building = tag.getCompound("awayBuilding"); // (an older save: none, nothing owed)
        work = building.getLong("work");
        soil = building.getInt("soil");
    }
}
