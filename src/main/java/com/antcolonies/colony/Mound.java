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
import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteHoleBlock;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermiteMouthBlock;
import com.antcolonies.block.TermiteRampBlock;
import com.antcolonies.block.TermiteSpireBlock;
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
 */
public class Mound {
    /** How long (in ticks) a termite's claim on a spot it carries soil to, or a chimney block it shapes, lasts. */
    private static final int CLAIM_TICKS = 600;
    /** How long (in ticks) a termite that set out to dig soil counts as bringing some. */
    private static final int SOIL_CLAIM_TICKS = 600;
    /** How far down (in blocks) a wall at the foot of the mound is filled in to reach the ground (a dip, a slope). */
    private static final int MAX_FOOTING = 3;

    private final Colony colony;
    private final BlockPos center;

    /** Blocks the colony has earned for its mound (see {@link #getAllowance}). Saved. */
    private double allowance;
    /** Game time the allowance was last brought up to date. Saved. */
    private long updatedAt = Colony.NOT_SET;

    /** Who carries soil to which spot, and who shapes which chimney block (not saved). */
    private final Map<UUID, Claim> buildClaims = new HashMap<>();
    private final Map<UUID, Claim> holeClaims = new HashMap<>();
    /** Termites on their way to dig soil (not saved). */
    private final Claims<UUID> soilClaims = new Claims<>();
    /** How many pellets have been taken from each block being dug (not saved: a half-dug block gives a few too many after a reload). */
    private final Map<BlockPos, Integer> pelletsTaken = new HashMap<>();

    private record Claim(BlockPos spot, long until) {
    }

    /** Worked out once per profile (it only changes on /reload). */
    @Nullable
    private MoundNumbers shapesFor;
    private int[] wallsAt = new int[0];
    private int cachedStage;
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
        return colony.getId().getMostSignificantBits() ^ colony.getId().getLeastSignificantBits();
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

    /** The stage the mound is built to now: the tallest one whose blocks the colony has earned (at least the first). */
    public int getStage(Level level) {
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

    /** Sets the mound's stage (debug command and tests): the colony has earned exactly the blocks of stage {@code h}. */
    public void setStage(Level level, int h) {
        allowance = wallsAt(h);
        updatedAt = colony.isFounding() ? Colony.NOT_SET : level.getGameTime();
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
        }
        return cachedParts;
    }

    /** How many blocks of the mound are finished (for the colony list). */
    public int countFinished(Level level) {
        int finished = 0;
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            if (entry.getValue() == MoundPlan.Part.WALL && level.isLoaded(entry.getKey()) && isWallDone(level, entry.getKey())) {
                finished++;
            }
        }
        return finished;
    }

    // ---- building ----

    /** A wall cell needs no more soil: a finished mound block, or anything else solid already fills it. */
    private static boolean isWallDone(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TermiteMoundBlock) {
            return TermiteMoundBlock.isFinished(state);
        }
        return !state.canBeReplaced();
    }

    /**
     * Every spot where a layer of soil can go now: walls of the mound still being built, and empty walls whose floor
     * may be built on (see {@link TermiteMoundBlock#canStartAt}). A wall at the foot of the mound over a dip is filled
     * in from the ground up. Blocks being built come first (they are finished before new ones are started), then the
     * lowest. Worked out once a tick.
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
            if (entry.getValue() != MoundPlan.Part.WALL || !level.isLoaded(entry.getKey())) {
                continue;
            }
            BlockPos spot = spotIn(level, entry.getKey(), nestCells);
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
    private BlockPos spotIn(Level level, BlockPos wall, Set<BlockPos> nestCells) {
        BlockState state = level.getBlockState(wall);
        if (state.getBlock() instanceof TermiteMoundBlock) {
            return TermiteMoundBlock.isFinished(state) ? null : wall;
        }
        if (!state.canBeReplaced()) {
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
            if (!under.canBeReplaced()) {
                return null;
            }
            if (TermiteMoundBlock.canStartAt(level, below)) {
                return below;
            }
        }
        return null;
    }

    /** Whether a layer of soil can go here now. */
    public boolean isBuildSpot(Level level, BlockPos pos) {
        return getBuildSpots(level).contains(pos);
    }

    /**
     * Whether termites can work on this spot now: always while they may go out, but while they stay in (by day, see
     * {@link Colony#isForagingTime}) only from inside: one of the cells round it is inside the nest or the mound.
     */
    public boolean isWorkableNow(Level level, BlockPos spot) {
        if (!colony.keepsASchedule() || colony.isForagingTime(level)) {
            return true;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0) && colony.nest().isInside(level, spot.offset(dx, dy, dz))) {
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
        BlockPos best = null;
        for (BlockPos spot : getBuildSpots(level)) {
            if (isClaimedByOther(buildClaims, spot, ant.getUUID()) || !isWorkableNow(level, spot)) {
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

    private static boolean isClaimedByOther(Map<UUID, Claim> claims, BlockPos spot, UUID ant) {
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
        if (!ModBlocks.TERMITE_MOUND.get().addLayer(level, spot)) {
            return false;
        }
        spotsTick = Long.MIN_VALUE;
        BlockState now = level.getBlockState(spot);
        double top = spot.getY() + now.getCollisionShape(level, spot).max(Direction.Axis.Y);
        for (LivingEntity ant : inCell) {
            if (ant.getBoundingBox().minY < top) {
                liftOnto(level, ant, spot, top);
            }
        }
        return true;
    }

    /** Puts an insect in a cell that was just filled up on top of the new soil, or beside it if there is no room on top. */
    private static void liftOnto(Level level, LivingEntity ant, BlockPos spot, double top) {
        BlockPos above = BlockPos.containing(ant.getX(), top, ant.getZ());
        if (top < spot.getY() + 1.0 || level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
            ant.setPos(ant.getX(), top, ant.getZ());
        } else {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = spot.relative(side);
                if (level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
                    ant.setPos(beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5);
                    break;
                }
            }
        }
        ant.setDeltaMovement(0, 0, 0);
        if (ant instanceof AbstractAntEntity insect) {
            insect.getNavigator().stop();
        }
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
    public void blockDug(BlockPos dug) {
        pelletsTaken.remove(dug);
    }

    /** Any block of a termite mound: soil, the old chimney crown, a spire piece, a chimney mouth or a ramp. */
    private static boolean isMoundBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof TermiteMoundBlock || block instanceof TermiteHoleBlock || block instanceof MoundPieceBlock;
    }

    // ---- chimney mouths ----

    /**
     * A chimney mouth (at the tip of a spire) that can be shaped now, claimed for this termite, or null. It is shaped
     * once the walls beside it are finished and the spire it stands on is there. (Termites do not shape the spire
     * pieces yet, so on a mound they build the mouths wait; the instant command puts them up.)
     */
    @Nullable
    public BlockPos pickHoleJob(Level level, AbstractAntEntity ant) {
        if (!isActive()) {
            return null;
        }
        long now = level.getGameTime();
        holeClaims.values().removeIf(claim -> claim.until <= now);
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : getParts(level).entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() == MoundPlan.Part.MOUTH && !isClaimedByOther(holeClaims, pos, ant.getUUID()) && isHoleJob(level, pos)) {
                holeClaims.put(ant.getUUID(), new Claim(pos, now + CLAIM_TICKS));
                return pos;
            }
        }
        return null;
    }

    public void releaseHoleJob(UUID ant) {
        holeClaims.remove(ant);
    }

    /** Whether a chimney mouth can be shaped here now. */
    public boolean isHoleJob(Level level, BlockPos pos) {
        Map<BlockPos, MoundPlan.Part> parts = getParts(level);
        if (parts.get(pos) != MoundPlan.Part.MOUTH || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TermiteMouthBlock || !(state.canBeReplaced() || state.getBlock() instanceof TermiteMoundBlock)) {
            return false;
        }
        BlockPos under = pos.below();
        if (parts.get(under) != MoundPlan.Part.OPEN && level.getBlockState(under).canBeReplaced()) {
            return false; // nothing to stand on yet
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(side);
            if (parts.get(beside) == MoundPlan.Part.WALL && !isWallDone(level, beside)) {
                return false;
            }
        }
        for (BlockPos below = pos.below(); parts.get(below) == MoundPlan.Part.OPEN; below = below.below()) {
            if (isClearJob(level, below)) {
                return false;
            }
        }
        return true;
    }

    /** Shapes the chimney mouth. False if it cannot be done now (not a job any more, or a creature other than a termite is in the way). */
    public boolean shapeHole(Level level, BlockPos pos) {
        if (!isHoleJob(level, pos)) {
            return false;
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos))) {
            if (!(living instanceof AbstractAntEntity)) {
                return false;
            }
        }
        return level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(ModBlocks.TERMITE_MOUTH.get().defaultBlockState(), level, pos));
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
        setStage(level, h);
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
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (!(state.canBeReplaced() || isMoundBlock(state) || state.is(ModTags.DIGGABLE))) {
                continue;
            }
            BlockPos offset = pos.subtract(center);
            int age = shape.age().getOrDefault(offset, 0);
            BlockState placed = switch (entry.getValue()) {
                case WALL -> soil(age);
                case OPEN -> Blocks.AIR.defaultBlockState();
                case MOUTH -> ModBlocks.TERMITE_MOUTH.get().defaultBlockState().setValue(MoundSoil.AGE, age);
                case SPIRE -> ModBlocks.TERMITE_SPIRE.get().defaultBlockState().setValue(MoundSoil.AGE, age)
                        .setValue(TermiteSpireBlock.THICKNESS, shape.thickness().get(offset));
                case RAMP -> ModBlocks.TERMITE_RAMP.get().defaultBlockState().setValue(MoundSoil.AGE, age)
                        .setValue(TermiteRampBlock.FACING, shape.facing().get(offset))
                        .setValue(TermiteRampBlock.SHAPE, shape.corner().get(offset));
            };
            level.setBlock(pos, placed, 2);
            if (entry.getValue() == MoundPlan.Part.MOUTH) {
                mouths.add(pos);
            }
            if (entry.getValue() == MoundPlan.Part.WALL && offset.getY() == 0) {
                for (int i = 1; i <= MAX_FOOTING && level.getBlockState(pos.below(i)).canBeReplaced(); i++) {
                    level.setBlock(pos.below(i), soil(3), 2);
                }
            }
        }
        // A mouth is slim on a lone spire and a full block beside soil (all the soil round it is there now).
        for (BlockPos pos : mouths) {
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), 2);
        }
        spotsTick = Long.MIN_VALUE;
    }

    /** Soil under the foot of the mound, filling a dip down to the ground (kept when an old mound is cleared away). */
    private boolean isFooting(Map<BlockPos, MoundPlan.Part> parts, BlockPos pos) {
        if (pos.getY() >= center.getY()) {
            return false;
        }
        BlockPos foot = new BlockPos(pos.getX(), center.getY(), pos.getZ());
        return parts.get(foot) == MoundPlan.Part.WALL;
    }

    /** Finished mound soil of an age (0 fresh to 3 old). */
    private static BlockState soil(int age) {
        return ModBlocks.TERMITE_MOUND.get().defaultBlockState()
                .setValue(TermiteMoundBlock.LAYERS, TermiteMoundBlock.FINISHED).setValue(TermiteMoundBlock.AGE, age);
    }

    void save(CompoundTag tag) {
        tag.putDouble("moundAllowance", allowance);
        tag.putLong("moundUpdated", updatedAt);
    }

    void load(CompoundTag tag) {
        allowance = tag.getDouble("moundAllowance");
        updatedAt = tag.contains("moundUpdated") ? tag.getLong("moundUpdated") : Colony.NOT_SET;
    }
}
