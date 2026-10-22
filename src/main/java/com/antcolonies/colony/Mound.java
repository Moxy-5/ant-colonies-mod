package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.MoundPieceBlock;
import com.antcolonies.block.MudCrustBlock;
import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteHoleBlock;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteMouthBlock;
import com.antcolonies.block.TermiteRampBlock;
import com.antcolonies.block.TermiteSpireBlock;
import com.antcolonies.block.TermiteTunnelBlock;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModTags;
import com.antcolonies.species.SpeciesProfile.MoundNumbers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The termite mound of one colony (species whose profile has a {@code mound}; the others build the ant hill): how big
 * the colony lets it be now, where the next layer of soil goes, the chimney blocks to shape, what has to be cleared out
 * when the mound grows, and the galleries under it where the soil is dug.
 *
 * <p>How big: the colony earns blocks over time ({@link #getAllowance}: the profile's blocks per day, plus more per
 * worker, so age and size both count), and the mound's stage ({@link MoundPlan#stage}) is the tallest one whose blocks
 * it has earned. The termites build that stage block by block, layer by layer: one dug block gives the soil for one
 * mound block ({@link TermiteMoundBlock#FINISHED} pellets), and wet soil must set before more is stacked on it.
 *
 * <p>The cathedral (tunnel plan step 4): the plan's tunnels ({@link MoundPlan.Part#TUNNEL}) are built as soil and then
 * carved ({@link MoundShaping}); ramps, spire pieces and chimney mouths take one pellet each and go where the plan
 * puts them once what they stand on is there. The termites work from inside, by the rules of {@link MoundReach}.
 */
public class Mound {
    /** How long (in ticks) a termite's claim on a spot it carries soil to, or a chimney block it shapes, lasts. */
    static final int CLAIM_TICKS = 600;
    /** How long (in ticks) a termite that set out to dig soil counts as bringing some. */
    private static final int SOIL_CLAIM_TICKS = 600;
    /** How far down (in blocks) a wall at the foot of the mound is filled in to reach the ground (a dip, a slope). */
    private static final int MAX_FOOTING = 3;
    /**
     * How long (in ticks, 5 minutes) the termites may go without building anything before they move on to the next stage
     * anyway (my pick): a spot nobody can get to (a player broke the tunnels, odd ground) never stops the mound growing.
     */
    private static final int STALL_TICKS = 6000;
    /** How often (in ticks) they look whether the stage they build is finished. */
    private static final int STAGE_CHECK_TICKS = 100;
    /**
     * After this long (in ticks, a minute) without anything built, the termites may fall back on building from outside
     * (at night) or from 2 blocks away (my pick): before that a spot they cannot get to from inside just waits for the
     * tunnel that will lead to it (the plan never needs the fallbacks).
     */
    private static final int FALLBACK_AFTER_TICKS = 1200;
    /** How long (in ticks) a spot nobody found a way to from inside is left alone. */
    private static final int UNREACHABLE_TICKS = 200;

    private final Colony colony;
    private final BlockPos center;

    /** Blocks the colony has earned for its mound (see {@link #getAllowance}). Saved. */
    private double allowance;
    /** Game time the allowance was last brought up to date. Saved. */
    private long updatedAt = Colony.NOT_SET;
    /** The stage the termites build now (see {@link #getStage}). Saved; 0 = not started yet (or an old save). */
    private int workStage;
    /** When the termites last built, carved, reshaped or cleared something (not saved: counts from the load). */
    private long lastProgress = Colony.NOT_SET;
    private long stageCheckedAt;

    /** Who carries soil to which spot, and who carves which tunnel (not saved). */
    private final Map<UUID, Claim> buildClaims = new HashMap<>();
    /** The shaping work: tunnels to carve, pieces to reshape or clear (see {@link MoundShaping}). */
    private final MoundShaping shaping = new MoundShaping(this);
    /** Spots a termite found no way to from inside, left alone until the game time given (not saved). */
    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    /** Termites on their way to dig soil (not saved). */
    private final Claims<UUID> soilClaims = new Claims<>();
    /** How many pellets have been taken from each block being dug (not saved: a half-dug block gives a few too many after a reload). */
    private final Map<BlockPos, Integer> pelletsTaken = new HashMap<>();

    record Claim(BlockPos spot, long until) {
    }

    /** Worked out once per profile (it only changes on /reload). */
    @Nullable
    private MoundNumbers shapesFor;
    @Nullable
    private Long seedForTests;
    private int[] wallsAt = new int[0];
    private int cachedStage;
    private boolean crumbleDue; // the stage changed: leftovers of earlier stages still to crumble
    private Map<BlockPos, MoundPlan.Part> cachedParts = Map.of();
    @Nullable
    private MoundPlan.Shape cachedShape;
    @Nullable
    private List<BlockPos> galleries;
    private BlockPos galleriesHub = BlockPos.ZERO;
    private long spotsTick = Long.MIN_VALUE;
    private List<BlockPos> spots = List.of();

    Mound(Colony colony) {
        this.colony = colony;
        this.center = colony.getCenter();
    }

    /** The shaping work of the plan: tunnels to carve, pieces to reshape or clear away. */
    public MoundShaping shaping() {
        return shaping;
    }

    Colony colony() {
        return colony;
    }

    /** The entrance of the nest, where the mound stands (the plan's offsets are from it). */
    BlockPos center() {
        return center;
    }

    /** The plan of the stage now being built (worked out by {@link #getParts}), or null before it is. */
    @Nullable
    MoundPlan.Shape shape() {
        return cachedShape;
    }

    /** Something of the mound was shaped: the build spots are worked out again, and the stage is still going. */
    void shaped(Level level) {
        spotsTick = Long.MIN_VALUE;
        progressed(level);
    }

    /** The mound numbers of the colony's species, or null if it builds no mound. */
    @Nullable
    public MoundNumbers numbers() {
        return colony.profile().mound().orElse(null);
    }

    /** True once there is building to do: the species builds a mound and the colony has workers. */
    public boolean isActive() {
        return numbers() != null && !colony.isFounding();
    }

    /** What makes this colony's mound look a little different from every other one (fixed per colony). */
    private long seed() {
        return seedForTests != null ? seedForTests : colony.getId().getMostSignificantBits() ^ colony.getId().getLeastSignificantBits();
    }

    /** Tests only: gives this mound the shape of another colony's (from a save of the user's world). */
    public void setSeedForTests(long seed) {
        seedForTests = seed;
        shapesFor = null;
        cachedStage = 0;
    }

    private void prepareShapes(MoundNumbers numbers) {
        if (numbers.equals(shapesFor)) {
            return;
        }
        shapesFor = numbers;
        wallsAt = new int[numbers.maxHeight() + 1];
        for (int h = 1; h <= numbers.maxHeight(); h++) {
            wallsAt[h] = MoundPlan.walls(MoundPlan.stage(h, numbers, seed()));
        }
        cachedStage = 0;
    }

    /** How many blocks of soil the mound has at stage {@code h}. */
    public int wallsAt(int h) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        prepareShapes(numbers);
        return wallsAt[Math.max(1, Math.min(h, numbers.maxHeight()))];
    }

    /**
     * The blocks the colony has earned for its mound by now: the profile's {@code blocks_per_day}, plus
     * {@code blocks_per_day_per_worker} for each worker it has, for every day since its first workers were born (never
     * more than the full mound). Worked out from the game time, so it keeps growing while the chunk is not loaded.
     */
    public double getAllowance(Level level) {
        MoundNumbers numbers = numbers();
        long now = level.getGameTime();
        if (numbers == null || colony.isFounding() || updatedAt == Colony.NOT_SET) {
            updatedAt = numbers == null || colony.isFounding() ? Colony.NOT_SET : now;
            return allowance;
        }
        int workers = colony.getCount(TermiteWorkerEntity.CASTE);
        double perTick = (numbers.blocksPerDay() + numbers.blocksPerDayPerWorker() * workers) / 24000.0;
        allowance = Math.min(wallsAt(numbers.maxHeight()), allowance + Math.max(0, now - updatedAt) * perTick);
        updatedAt = now;
        return allowance;
    }

    /** The tallest stage whose blocks the colony has earned (at least the first). */
    public int getEarnedStage(Level level) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        double earned = getAllowance(level);
        int h = 1;
        while (h < numbers.maxHeight() && wallsAt(h + 1) <= earned) {
            h++;
        }
        return h;
    }

    /**
     * The stage the termites build now. They finish one stage before they start the next, up to the stage the colony
     * has earned ({@link #getEarnedStage}): the stages are proven buildable one after the other ({@link MoundGrowth}),
     * not in jumps. If they have built nothing for {@link #STALL_TICKS} (a spot nobody can get to), they move on anyway.
     * An old save (no stage saved yet) starts at the first stage and climbs. The pieces of earlier stages where the plan has
     * nothing any more crumble when a new stage begins (see {@link MoundGrowth#crumblesAway}).
     */
    public int getStage(Level level) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        int earned = getEarnedStage(level);
        long now = level.getGameTime();
        if (workStage <= 0) {
            workStage = 1; // a new colony, or an old save: up from the first stage, one at a time
        }
        workStage = Math.min(workStage, earned);
        if (lastProgress == Colony.NOT_SET || now - stageCheckedAt > 3L * STAGE_CHECK_TICKS) {
            lastProgress = now; // just loaded (or the chunk was away): the stall clock starts now
        }
        if (workStage < earned && now - stageCheckedAt >= STAGE_CHECK_TICKS) {
            stageCheckedAt = now;
            if (now - lastProgress >= STALL_TICKS || isStageDone(level)) {
                workStage++;
                lastProgress = now;
            }
        } else if (now - stageCheckedAt >= STAGE_CHECK_TICKS) {
            stageCheckedAt = now;
        }
        return workStage;
    }

    /**
     * Sets the mound's stage (debug command and tests): the colony has earned exactly the blocks of stage {@code h}; the
     * termites build their way up to it one stage after the other.
     */
    public void setStage(Level level, int h) {
        allowance = wallsAt(h);
        updatedAt = colony.isFounding() ? Colony.NOT_SET : level.getGameTime();
        workStage = Math.min(Math.max(workStage, 1), h);
    }

    /** Sets the stage the termites build now as well (tests, the instant build): stage {@code h} at once. */
    public void setWorkStage(Level level, int h) {
        setStage(level, h);
        workStage = h;
    }

    /**
     * The brain, step 2d ({@link BuildLife}): if the stage being built is finished (or {@code stalled}: nothing more of
     * it can be built, see {@link #STALL_TICKS}) and the colony has earned a taller one, the next stage starts now (the
     * real termites look every few seconds, {@link #getStage}). True if it did.
     */
    boolean nextStage(Level level, boolean stalled) {
        int stage = getStage(level);
        if (stage >= getEarnedStage(level) || !stalled && !isStageDone(level)) {
            return false;
        }
        workStage = stage + 1;
        stageCheckedAt = level.getGameTime();
        progressed(level);
        spotsTick = Long.MIN_VALUE;
        return true;
    }

    /** Something was built, carved, reshaped or cleared: the stage is still going. */
    private void progressed(Level level) {
        lastProgress = level.getGameTime();
    }

    /** Whether every cell of the stage being built is as the plan has it (nothing left to build, carve, reshape or clear). */
    private boolean isStageDone(Level level) {
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            if (level.isLoaded(entry.getKey()) && jobAt(level, entry.getKey(), entry.getValue()) != null) {
                return false;
            }
        }
        return true;
    }

    /** Every cell of the mound at its stage now, in world positions. Empty if the colony builds no mound. */
    public Map<BlockPos, MoundPlan.Part> getParts(Level level) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return Map.of();
        }
        int h = getStage(level);
        if (h != cachedStage) {
            MoundPlan.Shape shape = MoundPlan.stage(h, numbers, seed());
            Map<BlockPos, MoundPlan.Part> parts = new LinkedHashMap<>();
            shape.parts().forEach((offset, part) -> parts.put(center.offset(offset), part));
            cachedParts = parts;
            cachedShape = shape;
            cachedStage = h;
            crumbleDue = true; // done on the colony's next tick, not here: this is only a lookup
        }
        return cachedParts;
    }

    /**
     * Called once a second for the colony (by the queen): the pieces of earlier stages where the stage now being built
     * has nothing crumble away, once after the stage has changed (see {@link #crumbleLeftovers}).
     */
    public void tick(Level level) {
        Map<BlockPos, MoundPlan.Part> parts = getParts(level);
        MoundNumbers numbers = numbers();
        if (crumbleDue && numbers != null && !parts.isEmpty()) {
            crumbleDue = false;
            crumbleLeftovers(level, numbers, cachedStage);
        }
    }

    /**
     * The pieces (ramps, spire pieces, mouths) of earlier stages in cells where stage {@code h} has nothing crumble away
     * (the termites could not get to them from inside: an old spire top the spire has grown away from).
     */
    private void crumbleLeftovers(Level level, MoundNumbers numbers, int h) {
        java.util.Set<BlockPos> earlier = new java.util.HashSet<>();
        for (int stage = 1; stage < h; stage++) {
            MoundPlan.stage(stage, numbers, seed()).parts().forEach((offset, part) -> {
                if (isPiece(part)) {
                    earlier.add(offset);
                }
            });
        }
        for (BlockPos offset : earlier) {
            BlockPos pos = center.offset(offset);
            if (MoundGrowth.crumblesAway(cachedParts.get(pos)) && level.isLoaded(pos) && isPieceBlock(level.getBlockState(pos))) {
                level.destroyBlock(pos, false);
            }
        }
    }

    /** A shaped piece of the mound (a ramp, a spire piece, a mouth), not a tunnel block. */
    private static boolean isPieceBlock(BlockState state) {
        return state.getBlock() instanceof MoundPieceBlock && !(state.getBlock() instanceof TermiteTunnelBlock);
    }

    /**
     * What the plan still wants done in a cell now, the same as in the pretend mound ({@link MoundGrowth}): soil (or a
     * layer more of it), a piece, a carved tunnel (or a tunnel's doors into the shaft), a piece reshaped into the
     * plan's one (or filled up where it stands into the soil or tunnel block the plan has there: never cleared first, so
     * nothing above it is left hanging and a spire's channel is never cut), the shaft dug out. Null if nothing (done, or something it does
     * not change stands there: soil where a piece would go stays).
     */
    @Nullable
    public MoundGrowth.Job jobAt(Level level, BlockPos pos, MoundPlan.Part part) {
        BlockState state = level.getBlockState(pos);
        boolean empty = state.canBeReplaced() && state.getFluidState().isEmpty();
        boolean wet = state.getBlock() instanceof TermiteMoundBlock && !TermiteMoundBlock.isFinished(state);
        boolean crust = MudCrustBlock.isCrust(state); // a thin wall of mud, packed full with soil
        return switch (part) {
            case OPEN -> isClearJob(level, pos) ? MoundGrowth.Job.CLEAR : null;
            case WALL -> empty || wet || crust ? MoundGrowth.Job.SOIL : isPieceBlock(state) ? MoundGrowth.Job.RESHAPE : null;
            case TUNNEL -> empty || wet || crust ? MoundGrowth.Job.SOIL : shaping.isCarveJob(level, pos) ? MoundGrowth.Job.CARVE
                    : isPieceBlock(state) ? MoundGrowth.Job.RESHAPE : null;
            case RAMP, SPIRE, MOUTH -> empty ? MoundGrowth.Job.PIECE
                    : isPieceBlock(state) && !isPlannedPiece(state, pos, part) ? MoundGrowth.Job.RESHAPE : null;
        };
    }

    /** Whether the piece standing here is the one the plan wants (a ramp's corner follows the ramps beside it on its own). */
    private boolean isPlannedPiece(BlockState state, BlockPos pos, MoundPlan.Part part) {
        BlockPos offset = pos.subtract(center);
        return switch (part) {
            case RAMP -> state.getBlock() instanceof TermiteRampBlock && cachedShape != null
                    && state.getValue(TermiteRampBlock.FACING) == cachedShape.facing().get(offset);
            case SPIRE -> state.getBlock() instanceof TermiteSpireBlock && cachedShape != null
                    && state.getValue(TermiteSpireBlock.THICKNESS).equals(cachedShape.thickness().get(offset));
            case MOUTH -> state.getBlock() instanceof TermiteMouthBlock;
            default -> true;
        };
    }

    /** How many blocks of the mound are finished (for the colony list). */
    public int countFinished(Level level) {
        int finished = 0;
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            if (isSoil(entry.getValue()) && level.isLoaded(entry.getKey()) && isWallDone(level, entry.getKey())) {
                finished++;
            }
        }
        return finished;
    }

    // ---- building ----

    /** Whether a part of the plan is built as soil: plain mound soil, or soil a tunnel is carved into afterwards. */
    static boolean isSoil(MoundPlan.Part part) {
        return part == MoundPlan.Part.WALL || part == MoundPlan.Part.TUNNEL;
    }

    /** Whether a part of the plan is a piece: a ramp, a spire piece or a chimney mouth (one pellet each). */
    static boolean isPiece(MoundPlan.Part part) {
        return part == MoundPlan.Part.RAMP || part == MoundPlan.Part.SPIRE || part == MoundPlan.Part.MOUTH;
    }

    /** A wall cell needs no more soil: a finished mound block, or anything else solid already fills it. */
    private static boolean isWallDone(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TermiteMoundBlock) {
            return TermiteMoundBlock.isFinished(state);
        }
        return !TermiteMoundBlock.isRoomForSoil(state);
    }

    /**
     * Every spot where a pellet of soil can go now: soil blocks of the mound still being built, empty ones whose floor
     * may be built on (see {@link TermiteMoundBlock#canStartAt}), and pieces (ramps, spire pieces, mouths) whose footing
     * is there. A wall at the foot of the mound over a dip is filled in from the ground up. Blocks being built come first
     * (they are finished before new ones are started), then the lowest. Worked out once a tick.
     */
    public List<BlockPos> getBuildSpots(Level level) {
        if (!isActive()) {
            return List.of();
        }
        long now = level.getGameTime();
        if (now == spotsTick) {
            return spots;
        }
        Set<BlockPos> nestCells = colony.nest().nestCells(level);
        List<BlockPos> found = new ArrayList<>();
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            if (!level.isLoaded(entry.getKey())) {
                continue;
            }
            BlockPos spot = isSoil(entry.getValue()) ? spotIn(level, entry.getKey(), nestCells)
                    : isPiece(entry.getValue()) && isPieceSpot(level, entry.getKey(), entry.getValue()) ? entry.getKey() : null;
            if (spot != null) {
                found.add(spot);
            }
        }
        found.sort(Comparator.comparing((BlockPos pos) -> !(level.getBlockState(pos).getBlock() instanceof TermiteMoundBlock))
                .thenComparingInt(BlockPos::getY));
        spots = found;
        spotsTick = now;
        return found;
    }

    /** Where the next layer for this wall goes: the wall itself, a cell under it at the foot of the mound, or nowhere yet. */
    @Nullable
    BlockPos spotIn(Level level, BlockPos wall, Set<BlockPos> nestCells) {
        BlockState state = level.getBlockState(wall);
        if (state.getBlock() instanceof TermiteMoundBlock) {
            return TermiteMoundBlock.isFinished(state) ? null : wall;
        }
        if (!TermiteMoundBlock.isRoomForSoil(state)) {
            return null;
        }
        if (TermiteMoundBlock.canStartAt(level, wall)) {
            return wall;
        }
        if (wall.getY() > center.getY()) {
            return null;
        }
        // The foot of the mound over a dip: fill it from the ground up.
        for (int i = 1; i <= MAX_FOOTING; i++) {
            BlockPos below = wall.below(i);
            if (nestCells.contains(below) || !level.isLoaded(below)) {
                return null;
            }
            BlockState under = level.getBlockState(below);
            if (under.getBlock() instanceof TermiteMoundBlock) {
                return TermiteMoundBlock.isFinished(under) ? null : below;
            }
            if (!TermiteMoundBlock.isRoomForSoil(under)) {
                return null;
            }
            if (TermiteMoundBlock.canStartAt(level, below)) {
                return below;
            }
        }
        return null;
    }

    /**
     * Whether a piece can go here now: the cell is empty, and what it stands on is there (a ramp: soil that has set or a
     * tunnel block; a spire piece or a mouth: anything solid that is not wet soil, usually the piece under it).
     */
    boolean isPieceSpot(Level level, BlockPos pos, MoundPlan.Part part) {
        BlockState here = level.getBlockState(pos);
        return here.canBeReplaced() && here.getFluidState().isEmpty() && hasPieceSupport(level, pos, part);
    }

    /** Whether a piece has what it stands on here (see {@link #isPieceSpot}, without the cell having to be empty). */
    boolean hasPieceSupport(Level level, BlockPos pos, MoundPlan.Part part) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (part == MoundPlan.Part.RAMP) {
            return TermiteMoundBlock.canBuildOn(under, level, below);
        }
        return !under.getCollisionShape(level, below).isEmpty() && !(under.getBlock() instanceof TermiteMoundBlock && !TermiteMoundBlock.isFinished(under));
    }

    /** Whether a pellet of soil can go here now. */
    public boolean isBuildSpot(Level level, BlockPos pos) {
        return getBuildSpots(level).contains(pos);
    }

    /** Whether this spot is a piece of the plan (a ramp, a spire piece, a mouth) rather than soil. */
    public boolean isPieceJob(Level level, BlockPos pos) {
        MoundPlan.Part part = getParts(level).get(pos);
        return part != null && isPiece(part);
    }

    /**
     * Whether termites may bite windows in their tunnels now to build outside (the user's rule: at night or after rain,
     * {@link Colony#isForagingTime}; always for a species that keeps no schedule).
     */
    public boolean mayOpenWindows(Level level) {
        return !colony.keepsASchedule() || colony.isForagingTime(level);
    }

    /**
     * Whether termites can work on this spot now: always while they may open windows (at night, in the rain), but by
     * day only from the shaft or the nest with no window: one of the cells round it is inside and no channel.
     */
    public boolean isWorkableNow(Level level, BlockPos spot) {
        if (mayOpenWindows(level)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cell = spot.offset(dx, dy, dz);
                    if ((dx != 0 || dy != 0 || dz != 0) && MoundReach.isHollowInside(level, colony, cell)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * A spot for this termite's soil (claimed for it, one termite to a spot: they do not push each other), or null if
     * there is none free. Blocks being built first, then the lowest, then the nearest. By day only spots that can be
     * worked on from inside ({@link #isWorkableNow}).
     */
    @Nullable
    public BlockPos pickBuildSpot(Level level, AbstractAntEntity ant) {
        long now = level.getGameTime();
        buildClaims.values().removeIf(claim -> claim.until <= now);
        unreachable.values().removeIf(until -> until <= now);
        BlockPos best = null;
        for (BlockPos spot : getBuildSpots(level)) {
            if (isClaimedByOther(buildClaims, spot, ant.getUUID()) || !isWorkableNow(level, spot) || unreachable.containsKey(spot)) {
                continue;
            }
            if (best == null) {
                best = spot;
                continue;
            }
            boolean bestStarted = level.getBlockState(best).getBlock() instanceof TermiteMoundBlock;
            boolean started = level.getBlockState(spot).getBlock() instanceof TermiteMoundBlock;
            if (started != bestStarted || spot.getY() != best.getY()) {
                break; // the list is sorted: nothing further on is better
            }
            if (spot.distSqr(ant.blockPosition()) < best.distSqr(ant.blockPosition())) {
                best = spot;
            }
        }
        if (best != null) {
            buildClaims.put(ant.getUUID(), new Claim(best, now + CLAIM_TICKS));
        }
        return best;
    }

    static boolean isClaimedByOther(Map<UUID, Claim> claims, BlockPos spot, UUID ant) {
        for (Map.Entry<UUID, Claim> entry : claims.entrySet()) {
            if (!entry.getKey().equals(ant) && entry.getValue().spot.equals(spot)) {
                return true;
            }
        }
        return false;
    }

    public void releaseBuildSpot(UUID ant) {
        buildClaims.remove(ant);
    }

    /** A termite found no way to this spot from inside: it is left alone for a while (another may carve the way to it). */
    public void markUnreachable(Level level, BlockPos spot) {
        unreachable.put(spot.immutable(), level.getGameTime() + UNREACHABLE_TICKS);
    }

    /**
     * Whether the termites may fall back on building from outside or from 2 blocks away now: only once nothing has been
     * built for a while (see {@link #FALLBACK_AFTER_TICKS}).
     */
    public boolean mayUseFallbacks(Level level) {
        getStage(level);
        return level.getGameTime() - lastProgress >= FALLBACK_AFTER_TICKS;
    }

    /**
     * Adds a layer of soil at {@code spot}. Termites in the way are lifted onto the new layer (or stepped aside); a
     * player or another creature in the cell stops it (false, try again later). False too if the spot is no longer one.
     */
    public boolean addLayer(Level level, BlockPos spot) {
        if (!isBuildSpot(level, spot)) {
            return false;
        }
        List<LivingEntity> inCell = level.getEntitiesOfClass(LivingEntity.class, new AABB(spot));
        for (LivingEntity living : inCell) {
            if (!(living instanceof AbstractAntEntity)) {
                return false;
            }
        }
        if (isPieceJob(level, spot)) {
            // A piece: the plan's ramp, spire piece or mouth, fresh (it dries with age like the rest).
            BlockState piece = blockFor(cachedShape, spot.subtract(center)).setValue(MoundSoil.AGE, 0);
            if (!level.setBlockAndUpdate(spot, Block.updateFromNeighbourShapes(piece, level, spot))) {
                return false;
            }
        } else if (!ModBlocks.TERMITE_MOUND.get().addLayer(level, spot)) {
            return false;
        }
        progressed(level);
        spotsTick = Long.MIN_VALUE;
        BlockState now = level.getBlockState(spot);
        double top = spot.getY() + now.getCollisionShape(level, spot).max(Direction.Axis.Y);
        for (LivingEntity ant : inCell) {
            if (ant.getBoundingBox().minY < top) {
                NestWalls.liftOnto(level, ant, spot, top);
            }
        }
        return true;
    }

    // ---- soil ----

    /** Whether more soil is wanted: more spots ready for a layer (that can be worked on now) than termites already bringing soil. */
    public boolean wantsSoil(Level level) {
        long now = level.getGameTime();
        buildClaims.values().removeIf(claim -> claim.until <= now);
        soilClaims.prune(now);
        int spots = 0;
        for (BlockPos spot : getBuildSpots(level)) {
            if (isWorkableNow(level, spot)) {
                spots++;
            }
        }
        return spots > buildClaims.size() + soilClaims.size();
    }

    /**
     * The next block for a termite to bite, after the nest itself is dug (see {@link Nest#findDigTarget}): first the
     * mound's own soil where it must now be hollow (the old cap of a chimney that grew, a tunnel through the base), then,
     * if more soil is wanted, the next block of the galleries. A digger that goes for gallery soil is counted as
     * bringing some ({@code digger}, may be null when only asking).
     */
    @Nullable
    BlockPos findDigTarget(Level level, @Nullable UUID digger) {
        if (!isActive()) {
            return null;
        }
        BlockPos clear = findClearJob(level);
        if (clear != null) {
            return clear;
        }
        if (!wantsSoil(level)) {
            return null;
        }
        BlockPos next = colony.nest().firstDiggable(level, getGalleries(level));
        if (next != null && digger != null) {
            soilClaims.put(digger, level.getGameTime() + SOIL_CLAIM_TICKS);
        }
        return next;
    }

    /** The galleries under the mound in world positions, in digging order (below the bottom of the nest shaft). */
    List<BlockPos> getGalleries(Level level) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return List.of();
        }
        BlockPos hub = center.below(colony.nest().shaftDepth(level));
        if (galleries == null || !hub.equals(galleriesHub)) {
            List<BlockPos> cells = new ArrayList<>();
            for (BlockPos offset : MoundPlan.galleries(numbers.galleryLevels(), numbers.galleryRadius())) {
                cells.add(hub.offset(offset));
            }
            galleries = List.copyOf(cells);
            galleriesHub = hub;
        }
        return galleries;
    }

    /** Whether a block is the mound's own soil (or a bump of earth) in a cell that must now be hollow. */
    public boolean isClearJob(Level level, BlockPos pos) {
        if (!isActive() || getParts(level).get(pos) != MoundPlan.Part.OPEN || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        // A plug the termites packed into the entrance or a foraging hole is opened on the schedule, not cleared away.
        if ((pos.equals(center) || colony.foragingHoles().isHole(level, pos)) && colony.profile().nest().isPlug(state)) {
            return false;
        }
        return isMoundBlock(state) || state.is(ModTags.DIGGABLE);
    }

    /** The lowest block that must be cleared out of the mound and can be reached (see {@link Nest#standSpotsFor}). */
    @Nullable
    private BlockPos findClearJob(Level level) {
        BlockPos best = null;
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() == MoundPlan.Part.OPEN && (best == null || pos.getY() < best.getY()) && isClearJob(level, pos)
                    && !colony.nest().standSpotsFor(level, pos).isEmpty()) {
                best = pos;
            }
        }
        return best;
    }

    /**
     * A termite bit a block: whether the bite gives it a soil pellet to carry. A block gives one pellet per layer of a
     * mound block ({@link TermiteMoundBlock#FINISHED}), so one dug block builds one mound block. Only while the mound
     * wants soil (or the termite went for gallery soil it was counted for): soil from digging the nest's rooms when the
     * mound has nothing to build is left behind, so termites are not left holding soil with nowhere to put it.
     */
    public boolean takePellet(Level level, BlockPos dug, UUID ant) {
        boolean counted = soilClaims.contains(ant);
        soilClaims.remove(ant);
        if (!counted && !wantsSoil(level)) {
            return false;
        }
        if (pelletsTaken.size() > 64) {
            pelletsTaken.clear(); // only blocks being dug right now matter
        }
        int taken = pelletsTaken.getOrDefault(dug, 0);
        if (taken >= TermiteMoundBlock.FINISHED) {
            return false;
        }
        pelletsTaken.put(dug.immutable(), taken + 1);
        return true;
    }

    /** The block is dug out: forget how many pellets it gave. */
    public void blockDug(Level level, BlockPos dug) {
        pelletsTaken.remove(dug);
        if (getParts(level).containsKey(dug)) {
            progressed(level);
        }
    }

    /** Any block of a termite mound: soil, the old chimney crown, a spire piece, a chimney mouth or a ramp. */
    private static boolean isMoundBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof TermiteMoundBlock || block instanceof TermiteHoleBlock || block instanceof MoundPieceBlock;
    }

    // ---- for testing ----

    /**
     * Puts the whole mound of stage {@code h} up at once (debug command): soil, spire pieces, chimney mouths and ramps
     * of the ages the plan gives them, the hollow shaft, and the foot filled down to the ground. Only air, plants, mound
     * blocks and diggable earth are replaced. Mound blocks left from a bigger or older mound round it are taken away.
     * The colony is set to that stage.
     */
    public void buildInstantly(Level level, int h) {
        MoundNumbers numbers = numbers();
        if (numbers == null) {
            return;
        }
        setWorkStage(level, h);
        cachedStage = 0;
        Map<BlockPos, MoundPlan.Part> parts = getParts(level);
        MoundPlan.Shape shape = cachedShape;
        // Clear what is left of an earlier mound outside the new shape.
        int reach = MoundPlan.REACH + 1;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -MAX_FOOTING, -reach),
                center.offset(reach, numbers.maxHeight() + 8, reach))) {
            if (!parts.containsKey(pos) && isMoundBlock(level.getBlockState(pos)) && !isFooting(parts, pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        List<BlockPos> mouths = new ArrayList<>();
        List<BlockPos> tunnels = new ArrayList<>();
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (!(state.canBeReplaced() || isMoundBlock(state) || state.is(ModTags.DIGGABLE))) {
                continue;
            }
            BlockPos offset = pos.subtract(center);
            level.setBlock(pos, blockFor(shape, offset), 2);
            if (entry.getValue() == MoundPlan.Part.MOUTH) {
                mouths.add(pos);
            }
            if (entry.getValue() == MoundPlan.Part.TUNNEL) {
                tunnels.add(pos);
            }
            if (isSoil(entry.getValue()) && offset.getY() == 0) {
                for (int i = 1; i <= MAX_FOOTING && TermiteMoundBlock.isRoomForSoil(level.getBlockState(pos.below(i))); i++) {
                    level.setBlock(pos.below(i), soil(3), 2);
                }
            }
        }
        // A mouth is slim on a lone spire and a full block beside soil (all the soil round it is there now); every tunnel
        // joins up with the channels round it and opens into the shaft beside it.
        for (BlockPos pos : mouths) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), 2);
        }
        for (BlockPos pos : tunnels) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TermiteTunnelBlock tunnel) {
                level.setBlock(pos, tunnel.stateFor(level, pos, state.getValue(MoundSoil.AGE), shaping.doorsOf(level, pos)), 2);
            }
        }
        spotsTick = Long.MIN_VALUE;
    }

    /** Soil under the foot of the mound, filling a dip down to the ground (kept when an old mound is cleared away). */
    private boolean isFooting(Map<BlockPos, MoundPlan.Part> parts, BlockPos pos) {
        if (pos.getY() >= center.getY()) {
            return false;
        }
        BlockPos foot = new BlockPos(pos.getX(), center.getY(), pos.getZ());
        MoundPlan.Part part = parts.get(foot);
        return part != null && isSoil(part);
    }

    /**
     * The block a cell of a planned mound gets when the mound is put up at once: soil, a tunnel block, a spire piece, a
     * chimney mouth or a ramp of the age the plan gives it, or air in the shaft. ({@code offset}: the cell's offset from
     * the entrance.) A mouth is put slim and a tunnel closed; the blocks beside them set them right once they are there.
     */
    public static BlockState blockFor(MoundPlan.Shape shape, BlockPos offset) {
        int age = shape.age().getOrDefault(offset, 0);
        return switch (shape.parts().get(offset)) {
            case WALL -> soil(age);
            case TUNNEL -> ModBlocks.TERMITE_TUNNEL.get().defaultBlockState().setValue(MoundSoil.AGE, age);
            case OPEN -> Blocks.AIR.defaultBlockState();
            case MOUTH -> ModBlocks.TERMITE_MOUTH.get().defaultBlockState().setValue(MoundSoil.AGE, age);
            case SPIRE -> ModBlocks.TERMITE_SPIRE.get().defaultBlockState().setValue(MoundSoil.AGE, age)
                    .setValue(TermiteSpireBlock.THICKNESS, shape.thickness().get(offset));
            case RAMP -> ModBlocks.TERMITE_RAMP.get().defaultBlockState().setValue(MoundSoil.AGE, age)
                    .setValue(TermiteRampBlock.FACING, shape.facing().get(offset))
                    .setValue(TermiteRampBlock.SHAPE, shape.corner().get(offset));
        };
    }

    /** Finished mound soil of an age (0 fresh to 3 old). */
    static BlockState soil(int age) {
        return ModBlocks.TERMITE_MOUND.get().defaultBlockState()
                .setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED).setValue(TermiteMoundBlock.AGE, age);
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayTests.everyPartSavesAndLoads}). */
    public void fillForTests(net.minecraft.server.level.ServerLevel level) {
        allowance = 3.5;
        updatedAt = level.getGameTime();
        workStage = 2;
    }

    void save(CompoundTag tag) {
        tag.putDouble("moundAllowance", allowance);
        tag.putLong("moundUpdated", updatedAt);
        tag.putInt("moundWorkStage", workStage);
    }

    void load(CompoundTag tag) {
        allowance = tag.getDouble("moundAllowance");
        updatedAt = tag.contains("moundUpdated") ? tag.getLong("moundUpdated") : Colony.NOT_SET;
        workStage = tag.getInt("moundWorkStage"); // 0 in an old save: climbs up from the first stage
    }
}
