package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DeadCombBlock;
import com.antcolonies.savanna.block.FungusCombBlock;
import com.antcolonies.savanna.block.MoundSoil;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermiteTunnelBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The dead mound book (M8 step 1a, {@code docs/DESIGN.md}, "Dead mounds, details"): every termite mound whose colony
 * has died out, kept for good (the Matabele ants look for them as homes later). Written when a colony with a mound ends
 * ({@code ColonyNews.COLONY_ENDED}, heard by the mound and the garden).
 *
 * <p>The garden: nothing weeds it any more, so Xylaria takes the comb ({@link DeadCombBlock}): furred the first day,
 * black with stalks from day 1 to 4, rotting from day 4, losing its layers, gone after {@link #GONE_DAYS} days (my
 * picks from the real "within days, gone in about a week"). The days count by the world's clock ({@code /time add}
 * moves them on); the comb only ever moves forward, so land away for a while catches up when it loads.
 *
 * <p>The mound (part 2): nobody patches it any more, so rain wears it down in the real order, sped up ({@link MoundWear}):
 * the pieces on top first, the spires down to the body in a few days, then the body slumping into a low hump over about a
 * month, its soil washing out round the foot, grass taking it as it settles; the hump stays for good. Only forward, by
 * the world's clock, catching up when the land loads, at most {@link #BLOCKS_PER_LOOK} blocks a look (a whole mound worn
 * at once takes a minute or two of looks). Only mound blocks are taken: a player's block in a column keeps that column
 * as it is, and nothing under the ground is touched.
 *
 * <p>Part 3, the way in and the works round it: the hollow shaft up the middle stays open as the mound wears (the old
 * chimney falls open, a hole from the top of the hump down to the nest), and a few tunnel blocks at the top of the final
 * hump open upward ({@link MoundWear#MOST_TUNNEL_WAYS}): the ways in the Matabele ants use later. The colony's works out
 * round the mound weather too ({@link DeadWorks}): the surface mud first, then the holes grass over, the galleries under
 * the ground last.
 */
public class DeadMounds extends SavedData {
    private static final String DATA_NAME = SavannaMod.MODID + "_dead_mounds";
    /** How often (in ticks) the book looks at the dead mounds in loaded land. */
    static final int LOOK_TICKS = 100;
    /** Days after the colony's end: furred until 1, black until 4, rotting until gone. */
    public static final double BLACK_DAYS = 1.0;
    public static final double ROTTEN_DAYS = 4.0;
    public static final double GONE_DAYS = 6.5;
    /** The most blocks the wear changes in one look, in all the dead mounds together (my pick). */
    static final int BLOCKS_PER_LOOK = 256;

    /** One comb of the dead garden: where, and how many layers it had when the colony died. */
    public record Comb(BlockPos pos, int layers) {
    }

    /** One dead mound: its colony (by id), the nest's entrance, when the colony died (world clock ticks), its comb left. */
    public static final class DeadMound {
        public final UUID colony;
        public final BlockPos center;
        /** (Set back once for a dead mound of new land, M8 step 1b, which died before the world was made.) */
        public long diedAt;
        final List<Comb> combs = new ArrayList<>();
        /** The mound's wear (none if the colony had no mound standing). */
        Wear wear;
        /** Part 3: the colony's works out round the mound (none if it had built none). */
        DeadWorks works;

        DeadMound(UUID colony, BlockPos center, long diedAt) {
            this.colony = colony;
            this.center = center;
            this.diedAt = diedAt;
        }

        public List<Comb> combs() {
            return List.copyOf(combs);
        }

        /** Whole and part days since the colony died, by the world's clock. */
        public double days(ServerLevel level) {
            return Math.max(0, level.getDayTime() - diedAt) / 24000.0;
        }

        /** Whether the mound's wear was written down when the colony died (it had a mound standing). */
        public boolean wears() {
            return wear != null;
        }

        /** Whether the mound has worn down to its grassy hump for good (or had nothing to wear). */
        public boolean isSettled() {
            return wear == null || wear.settled;
        }

        /** Whether any of the colony's works round the mound (holes, mud, galleries) are still to weather. */
        public boolean hasWorksLeft() {
            return works != null && !works.isEmpty();
        }

        /** Whether anything about it still changes: comb left to rot, the mound still wearing down, or its works weathering. */
        boolean isChanging() {
            return !combs.isEmpty() || !isSettled() || hasWorksLeft();
        }
    }

    /**
     * A dead mound's wear (part 2): its columns and how far each has come, the pieces (spire tips, chimney mouths, ramps)
     * still standing, the cells of its hollow shaft (left open: the way in, part 3), and the tunnel blocks fallen open
     * upward at the top of the hump.
     */
    static final class Wear {
        final List<WearColumn> columns = new ArrayList<>();
        final List<BlockPos> pieces = new ArrayList<>();
        final Set<BlockPos> shaft = new HashSet<>();
        /** Part 3: the tunnel blocks at the top of the hump left open upward, little holes into the old channels. */
        final Set<BlockPos> ways = new HashSet<>();
        /** How tall the mound stood (blocks above the ground): the pieces high up go first. */
        int highest;
        /** Everything has come to the grassy hump: nothing more to do, ever. */
        boolean settled;
        /** The columns by their offset (made when first asked, not saved). */
        private Map<Long, WearColumn> byOffset;

        WearColumn columnAt(int dx, int dz) {
            if (byOffset == null) {
                byOffset = new java.util.HashMap<>();
                for (WearColumn column : columns) {
                    byOffset.put(BlockPos.asLong(column.plan.dx(), 0, column.plan.dz()), column);
                }
            }
            return byOffset.get(BlockPos.asLong(dx, 0, dz));
        }
    }

    /** One column of the wear: its plan, its height now (blocks above the ground), and what has happened to it. */
    static final class WearColumn {
        final MoundWear.Column plan;
        int now;
        /** Left as it is from now on: a player's block stands in it, or the ground here takes no washed soil. */
        boolean kept;
        /** Its top has turned to grass. */
        boolean grassed;

        WearColumn(MoundWear.Column plan, int now) {
            this.plan = plan;
            this.now = now;
        }

        /** The height it wears down to in the end: the hump (one layer for the washed soil round the foot). */
        int end() {
            return plan.isApron() ? 1 : plan.hump();
        }
    }

    private final Map<UUID, DeadMound> mounds = new LinkedHashMap<>();

    public static DeadMounds get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadMounds::new, DeadMounds::load, null), DATA_NAME);
    }

    public List<DeadMound> all() {
        return List.copyOf(mounds.values());
    }

    /** The colony with this id has died out at {@code center}: its dead mound (a new one, or the one already written). */
    public DeadMound died(ServerLevel level, UUID colony, BlockPos center) {
        DeadMound mound = mounds.computeIfAbsent(colony, id -> new DeadMound(id, center, level.getDayTime()));
        setDirty();
        return mound;
    }

    /**
     * The dead colony's mound as it stands when it dies ({@code parts}: the mound part's cells): its columns, its pieces
     * and its hollow shaft written down for the wear. Cells in land not loaded count as standing (the wear only ever
     * takes mound blocks it finds).
     */
    public void addMound(ServerLevel level, UUID colony, BlockPos center, Map<BlockPos, MoundPlan.Part> parts) {
        DeadMound mound = died(level, colony, center);
        if (mound.wear != null || parts.isEmpty()) {
            return;
        }
        Wear wear = new Wear();
        List<BlockPos> built = new ArrayList<>();
        for (Map.Entry<BlockPos, MoundPlan.Part> entry : parts.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean loaded = level.isLoaded(pos);
            BlockState state = loaded ? level.getBlockState(pos) : null;
            if (entry.getValue() == MoundPlan.Part.OPEN) {
                wear.shaft.add(pos.immutable());
            } else if (!loaded || Mound.isMoundBlock(state)) {
                built.add(pos.subtract(center));
                if (Mound.isPiece(entry.getValue()) && (!loaded || Mound.isPieceBlock(state))) {
                    wear.pieces.add(pos.immutable());
                }
            }
        }
        wear.pieces.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed()); // a mound catching up loses its tips first
        for (MoundWear.Column column : MoundWear.plan(built)) {
            wear.columns.add(new WearColumn(column, column.top()));
            wear.highest = Math.max(wear.highest, column.top());
        }
        mound.wear = wear;
        setDirty();
    }

    /** Part 3: the dead colony's works out round its mound ({@link DeadWorks#record}), to weather from now on. */
    void addWorks(ServerLevel level, UUID colony, BlockPos center, DeadWorks works) {
        DeadMound mound = died(level, colony, center);
        if (mound.works == null && !works.isEmpty()) {
            mound.works = works;
            setDirty();
        }
    }

    /** The dead colony's comb, as it was when it died: each turns furred at once (its nodules gone). */
    public void addCombs(ServerLevel level, UUID colony, BlockPos center, List<BlockPos> combs) {
        DeadMound mound = died(level, colony, center);
        for (BlockPos pos : combs) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FungusCombBlock) {
                mound.combs.add(new Comb(pos.immutable(), FungusCombBlock.layers(state)));
            }
        }
        updateCombs(level, mound, mound.days(level));
    }

    /** Every few seconds: the dead gardens and mounds in loaded land move on. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % LOOK_TICKS != 0) {
            return;
        }
        DeadMounds book = get(level);
        int budget = BLOCKS_PER_LOOK;
        for (DeadMound mound : book.mounds.values()) {
            if (mound.isChanging() && level.isLoaded(mound.center)) {
                budget -= book.update(level, mound, mound.days(level), budget);
                if (budget <= 0) {
                    break; // the rest next look
                }
            }
        }
    }

    /**
     * The game tests: the mound's comb and the mound itself as they would be {@code days} after the colony died (the
     * world's clock is shared), all at once.
     */
    public void updateForTests(ServerLevel level, DeadMound mound, double days) {
        update(level, mound, days, Integer.MAX_VALUE);
    }

    /**
     * M8 step 1b: a dead mound of new land, its colony dead {@code days} already: its clock set back that far and the
     * mound, comb and works brought up to that day at once (the land was just made: nobody saw it standing). Null if
     * there is no such dead mound.
     */
    public DeadMound agedForNewLand(ServerLevel level, UUID colony, double days) {
        DeadMound mound = mounds.get(colony);
        if (mound == null) {
            return null;
        }
        mound.diedAt = level.getDayTime() - Math.round(days * 24000.0);
        update(level, mound, mound.days(level), Integer.MAX_VALUE);
        setDirty();
        return mound;
    }

    /** The game tests: one column of the wear, told in words (for a failing check's message). */
    public String describeColumnForTests(DeadMound mound, int dx, int dz) {
        WearColumn column = mound.wear == null ? null : mound.wear.columnAt(dx, dz);
        return column == null ? "no column there" : column.plan + " now " + column.now + (column.kept ? " kept" : "")
                + (column.grassed ? " grassed" : "");
    }

    /** The game tests: the ways in (the hollow shaft and the tunnel blocks open upward at the top of the hump). */
    public Set<BlockPos> waysInForTests(DeadMound mound) {
        Set<BlockPos> ways = new HashSet<>();
        if (mound.wear != null) {
            ways.addAll(mound.wear.shaft);
            ways.addAll(mound.wear.ways);
        }
        return ways;
    }

    /** The game tests: how many of the tunnel blocks at the top of the hump stand open upward. */
    public int tunnelWaysForTests(DeadMound mound) {
        return mound.wear == null ? 0 : mound.wear.ways.size();
    }

    /** The game tests: the foraging holes round the mound still to silt up and grass over. */
    public List<BlockPos> holesLeftForTests(DeadMound mound) {
        return mound.works == null ? List.of() : List.copyOf(mound.works.holes);
    }

    /** The game tests: the gallery cells round the mound still to fill in. */
    public List<BlockPos> galleriesLeftForTests(DeadMound mound) {
        return mound.works == null ? List.of() : List.copyOf(mound.works.galleries);
    }

    /** The game tests: the works round the mound left to weather, told in words. */
    public String describeWorksForTests(DeadMound mound) {
        return mound.works == null ? "no works" : mound.works.describe();
    }

    /** The game tests: the dead mound is forgotten (the test's land is cleared after it). */
    public void forgetForTests(UUID colony) {
        if (mounds.remove(colony) != null) {
            setDirty();
        }
    }

    public DeadMound get(UUID colony) {
        return mounds.get(colony);
    }

    /** The dead mound brought up to its day, changing at most {@code budget} blocks of the mound: how many it changed. */
    private int update(ServerLevel level, DeadMound mound, double days, int budget) {
        updateCombs(level, mound, days);
        int used = wear(level, mound, days, budget);
        if (mound.works != null && used < budget && days > 0) {
            used += mound.works.weather(level, mound.center, days, budget - used, this::setDirty);
        }
        return used;
    }

    /** The mound's comb brought up to its day: furred, black, rotting, gone; a comb broken or replaced is forgotten. */
    private void updateCombs(ServerLevel level, DeadMound mound, double days) {
        for (Iterator<Comb> it = mound.combs.iterator(); it.hasNext(); ) {
            Comb comb = it.next();
            if (!level.isLoaded(comb.pos())) {
                continue;
            }
            BlockState now = level.getBlockState(comb.pos());
            boolean living = now.getBlock() instanceof FungusCombBlock;
            if (!living && !now.is(SavannaBlocks.DEAD_COMB.get())) {
                it.remove(); // broken or built over: nothing left to rot
                setDirty();
                continue;
            }
            BlockState target = stateAt(days, comb.layers());
            if (target == null) {
                level.setBlock(comb.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                it.remove();
                setDirty();
            } else if (living || isLater(target, now)) {
                level.setBlock(comb.pos(), target, Block.UPDATE_ALL);
            }
        }
    }

    // --- the wear (part 2) -------------------------------------------------------------------------------------------

    /**
     * The mound worn down to its day, changing at most {@code budget} blocks: the pieces due crumble, the columns come
     * down (the highest block first, so a mound catching up melts from the top), soil washes out round the foot, and grass
     * takes the columns that have settled. Returns how many blocks it changed.
     */
    private int wear(ServerLevel level, DeadMound mound, double days, int budget) {
        Wear wear = mound.wear;
        if (wear == null || wear.settled || days <= 0) {
            return 0;
        }
        int used = 0;
        // The pieces on top crumble, the highest first (a piece already gone or replaced is forgotten).
        for (Iterator<BlockPos> it = wear.pieces.iterator(); it.hasNext() && used < budget; ) {
            BlockPos pos = it.next();
            if (!level.isLoaded(pos) || days < MoundWear.pieceDay(pos.getY() - mound.center.getY(), wear.highest, MoundWear.jitter(pos.asLong()))) {
                continue;
            }
            if (Mound.isPieceBlock(level.getBlockState(pos)) && !underPlayersBlock(level, pos)) {
                level.destroyBlock(pos, false);
                used += 1 + closeBeside(level, pos);
            }
            it.remove();
            setDirty();
        }
        // The columns come down to their day's height, the highest block of the whole mound first.
        PriorityQueue<WearColumn> due = new PriorityQueue<>(Comparator.comparingInt((WearColumn column) -> column.now).reversed());
        for (WearColumn column : wear.columns) {
            if (!column.kept && !column.plan.isApron() && column.now > height(mound, column, days) && level.isLoaded(base(mound, column))) {
                due.add(column);
            }
        }
        while (!due.isEmpty() && used < budget) {
            WearColumn column = due.poll();
            BlockPos pos = base(mound, column).above(column.now - 1);
            BlockState state = level.getBlockState(pos);
            if (Mound.isMoundBlock(state)) {
                level.destroyBlock(pos, false);
                used++;
            } else if (!state.canBeReplaced()) {
                column.kept = true; // a player's block: it stays, and the column under it with it
                setDirty();
                continue;
            }
            used += closeBeside(level, pos); // (also where it was hollow: the shaft, now open to the sky)
            column.now--;
            setDirty();
            if (column.now > height(mound, column, days)) {
                due.add(column);
            } else {
                used += cap(level, mound, column);
            }
        }
        // The soil washed off settles round the foot, one layer on bare level ground.
        for (WearColumn column : wear.columns) {
            if (used >= budget) {
                break;
            }
            BlockPos pos = base(mound, column);
            if (!column.plan.isApron() || column.kept || column.now > 0 || !level.isLoaded(pos)
                    || days < MoundWear.apronDay(MoundWear.jitter(pos.asLong()))) {
                continue;
            }
            BlockState here = level.getBlockState(pos), below = level.getBlockState(pos.below());
            if (here.canBeReplaced() && here.getFluidState().isEmpty() && below.isFaceSturdy(level, pos.below(), Direction.UP)
                    && (below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.getBlock() instanceof TermiteMoundBlock)) {
                level.setBlock(pos, Mound.soil(MoundSoil.OLDEST), Block.UPDATE_ALL);
                column.now = 1;
                used++;
            } else {
                column.kept = true; // a slope, water, a path, something built: no soil settles here
            }
            setDirty();
        }
        // Grass takes the columns worn down to the hump.
        if (days >= MoundWear.GRASS_DAYS) {
            for (WearColumn column : wear.columns) {
                if (used >= budget) {
                    break;
                }
                if (column.kept || column.grassed || column.now != column.end() || !level.isLoaded(base(mound, column))) {
                    continue;
                }
                used += grass(level, mound, column);
                column.grassed = true;
                setDirty();
            }
        }
        if (used < budget && wear.pieces.isEmpty()
                && wear.columns.stream().allMatch(column -> column.kept || column.grassed && column.now == column.end())) {
            wear.settled = true;
            setDirty();
        }
        return used;
    }

    /**
     * Whether something a player put stands on this piece, straight or on the mound over it: the piece stays, as a
     * column under a player's block does.
     */
    private static boolean underPlayersBlock(ServerLevel level, BlockPos pos) {
        for (BlockPos up = pos.above(); level.isLoaded(up) && up.getY() < level.getMaxBuildHeight(); up = up.above()) {
            BlockState state = level.getBlockState(up);
            if (state.canBeReplaced()) {
                return false;
            }
            if (!Mound.isMoundBlock(state)) {
                return true;
            }
        }
        return false;
    }

    /** The column's height on this day (only ever lower than now, see {@link MoundWear#heightAt}). */
    private static int height(DeadMound mound, WearColumn column, double days) {
        return MoundWear.heightAt(column.plan, days, MoundWear.jitter(base(mound, column).asLong()));
    }

    /** The ground cell of a column: the mound's lowest layer, level with the entrance. */
    private static BlockPos base(DeadMound mound, WearColumn column) {
        return mound.center.offset(column.plan.dx(), 0, column.plan.dz());
    }

    /**
     * The new top of a column worn down to its day's height: weathered old soil, closing a tunnel under it (rain washes
     * soil into it). Part 3, the ways in: the hollow shaft is never closed (the old chimney falls open, a hole down to the
     * nest), and a tunnel block at the top of the final hump may fall open upward instead (about 1 in 3, at most
     * {@link MoundWear#MOST_TUNNEL_WAYS} to a mound). Returns the blocks changed.
     */
    private static int cap(ServerLevel level, DeadMound mound, WearColumn column) {
        if (column.now < 1) {
            return 0;
        }
        BlockPos pos = base(mound, column).above(column.now - 1);
        BlockState state = level.getBlockState(pos);
        BlockState old = Mound.soil(MoundSoil.OLDEST);
        Wear wear = mound.wear;
        if (state.getBlock() instanceof TermiteTunnelBlock && column.now == column.end() && wear.ways.size() < MoundWear.MOST_TUNNEL_WAYS
                && MoundWear.opensUp(pos.asLong()) && level.getBlockState(pos.above()).isAir()) {
            TermiteTunnelBlock.setWindow(level, pos, Direction.UP, true);
            wear.ways.add(pos.immutable());
            return 1;
        }
        if (state.getBlock() instanceof TermiteTunnelBlock || state.getBlock() instanceof TermiteMoundBlock && !state.equals(old)) {
            level.setBlock(pos, old, Block.UPDATE_ALL);
            return 1;
        }
        return 0;
    }

    /**
     * A block of the mound has worn away at {@code gone} (or a hollow there is now open to the sky): a tunnel block's side
     * laid bare toward it shuts again, as washed soil and slumping walls would (the ways in are the shaft, never closed,
     * and the few tunnel tops opened in {@link #cap}). Returns the blocks changed.
     */
    private static final Direction[] BARED = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN};

    private static int closeBeside(ServerLevel level, BlockPos gone) {
        int changed = 0;
        for (Direction side : BARED) {
            BlockPos beside = gone.relative(side);
            if (!level.isLoaded(beside)) {
                continue;
            }
            BlockState state = level.getBlockState(beside);
            if (state.getBlock() instanceof TermiteTunnelBlock && state.getValue(TermiteTunnelBlock.OPEN.get(side.getOpposite()))) {
                TermiteTunnelBlock.setWindow(level, beside, side.getOpposite(), false);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Grass over a settled column: its top block of mound soil (found from its height down: a crumbled ramp may have
     * left it lower, over a tunnel at the foot, which silts up) turns to grass, with a tuft on about 1 in 3. Returns the
     * blocks changed.
     */
    private static int grass(ServerLevel level, DeadMound mound, WearColumn column) {
        BlockPos base = base(mound, column);
        for (int y = column.now - 1; y >= 0; y--) {
            BlockPos pos = base.above(y);
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced()) {
                continue;
            }
            if (!(state.getBlock() instanceof TermiteMoundBlock) && !(state.getBlock() instanceof TermiteTunnelBlock)
                    || mound.wear.ways.contains(pos)) {
                return 0; // something a player put, or a tunnel fallen open (a way in): no grass
            }
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            if (MoundWear.tuft(pos.asLong()) && level.getBlockState(pos.above()).isAir()) {
                level.setBlock(pos.above(), Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
                return 2;
            }
            return 1;
        }
        return 0;
    }

    // --- the comb -------------------------------------------------------------------------------------------------

    /** Whether {@code target} lies further along the comb's death than {@code now} (it never goes back). */
    static boolean isLater(BlockState target, BlockState now) {
        int stage = target.getValue(DeadCombBlock.STAGE), was = now.getValue(DeadCombBlock.STAGE);
        return stage > was || stage == was && target.getValue(DeadCombBlock.LAYERS) < now.getValue(DeadCombBlock.LAYERS);
    }

    /** The dead comb {@code days} after the colony died, from a comb of {@code layers}; null once it has rotted away. */
    public static BlockState stateAt(double days, int layers) {
        int[] stage = stage(days, layers);
        return stage == null ? null : SavannaBlocks.DEAD_COMB.get().defaultBlockState()
                .setValue(DeadCombBlock.STAGE, stage[0]).setValue(DeadCombBlock.LAYERS, stage[1]);
    }

    /** {stage, layers} {@code days} after the colony died, from a comb of {@code layers}; null once gone. */
    static int[] stage(double days, int layers) {
        layers = Math.max(1, Math.min(DeadCombBlock.MAX_LAYERS, layers));
        if (days < BLACK_DAYS) {
            return new int[] {DeadCombBlock.FURRED, layers};
        } else if (days < ROTTEN_DAYS) {
            return new int[] {DeadCombBlock.BLACK, layers};
        } else if (days < GONE_DAYS) {
            // Slumping as it rots: the layers go one by one through the rotting days.
            int left = (int) Math.ceil(layers * (GONE_DAYS - days) / (GONE_DAYS - ROTTEN_DAYS));
            return new int[] {DeadCombBlock.ROTTEN, Math.max(1, Math.min(layers, left))};
        }
        return null;
    }

    /** For the command: one line per dead mound. */
    public String describe(ServerLevel level) {
        if (mounds.isEmpty()) {
            return "No dead termite mounds.";
        }
        StringBuilder out = new StringBuilder("Dead termite mounds: " + mounds.size());
        for (DeadMound mound : mounds.values()) {
            out.append("\n  ").append(mound.center.toShortString()).append(": dead ")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", mound.days(level))).append(" days, comb left ")
                    .append(mound.combs.size());
            if (mound.wear != null) {
                int now = 0, end = 0;
                for (WearColumn column : mound.wear.columns) {
                    now = Math.max(now, column.now);
                    end = Math.max(end, column.end());
                }
                out.append(", mound ").append(now).append(" tall (stood ").append(mound.wear.highest).append(", hump ").append(end)
                        .append("), pieces left ").append(mound.wear.pieces.size()).append(", ways in ")
                        .append(1 + mound.wear.ways.size()).append(mound.wear.settled ? ", settled" : "");
            }
            if (mound.works != null) {
                out.append(mound.works.isEmpty() ? ", works round it all weathered" : ", works left: " + mound.works.describe());
            }
        }
        return out.toString();
    }

    // --- saving ---------------------------------------------------------------------------------------------------

    /** Each column as 7 numbers: offset x and z, its top, body and hump, its height now, and kept (1) and grassed (2). */
    private static final int COLUMN_INTS = 7;

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (DeadMound mound : mounds.values()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("colony", mound.colony);
            m.put("center", NbtUtils.writeBlockPos(mound.center));
            m.putLong("diedAt", mound.diedAt);
            ListTag combs = new ListTag();
            for (Comb comb : mound.combs) {
                CompoundTag c = new CompoundTag();
                c.putLong("pos", comb.pos().asLong());
                c.putInt("layers", comb.layers());
                combs.add(c);
            }
            m.put("combs", combs);
            if (mound.wear != null) {
                m.put("wear", saveWear(mound.wear));
            }
            if (mound.works != null) {
                m.put("works", mound.works.save());
            }
            list.add(m);
        }
        tag.put("mounds", list);
        return tag;
    }

    private static DeadMounds load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadMounds book = new DeadMounds();
        for (Tag t : tag.getList("mounds", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            BlockPos center = NbtUtils.readBlockPos(m, "center").orElse(BlockPos.ZERO);
            DeadMound mound = new DeadMound(m.getUUID("colony"), center, m.getLong("diedAt"));
            for (Tag ct : m.getList("combs", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) ct;
                mound.combs.add(new Comb(BlockPos.of(c.getLong("pos")), c.getInt("layers")));
            }
            if (m.contains("wear", Tag.TAG_COMPOUND)) {
                mound.wear = loadWear(m.getCompound("wear"));
            }
            if (m.contains("works", Tag.TAG_COMPOUND)) {
                mound.works = DeadWorks.load(m.getCompound("works"));
            }
            book.mounds.put(mound.colony, mound);
        }
        return book;
    }

    private static CompoundTag saveWear(Wear wear) {
        CompoundTag tag = new CompoundTag();
        int[] columns = new int[wear.columns.size() * COLUMN_INTS];
        for (int i = 0; i < wear.columns.size(); i++) {
            WearColumn column = wear.columns.get(i);
            int at = i * COLUMN_INTS;
            columns[at] = column.plan.dx();
            columns[at + 1] = column.plan.dz();
            columns[at + 2] = column.plan.top();
            columns[at + 3] = column.plan.body();
            columns[at + 4] = column.plan.hump();
            columns[at + 5] = column.now;
            columns[at + 6] = (column.kept ? 1 : 0) | (column.grassed ? 2 : 0);
        }
        tag.putIntArray("columns", columns);
        tag.putLongArray("pieces", wear.pieces.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("shaft", wear.shaft.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("ways", wear.ways.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("highest", wear.highest);
        tag.putBoolean("settled", wear.settled);
        return tag;
    }

    private static Wear loadWear(CompoundTag tag) {
        Wear wear = new Wear();
        int[] columns = tag.getIntArray("columns");
        for (int at = 0; at + COLUMN_INTS <= columns.length; at += COLUMN_INTS) {
            WearColumn column = new WearColumn(new MoundWear.Column(columns[at], columns[at + 1], columns[at + 2], columns[at + 3],
                    columns[at + 4]), columns[at + 5]);
            column.kept = (columns[at + 6] & 1) != 0;
            column.grassed = (columns[at + 6] & 2) != 0;
            wear.columns.add(column);
        }
        for (long pos : tag.getLongArray("pieces")) {
            wear.pieces.add(BlockPos.of(pos));
        }
        for (long pos : tag.getLongArray("shaft")) {
            wear.shaft.add(BlockPos.of(pos));
        }
        for (long pos : tag.getLongArray("ways")) {
            wear.ways.add(BlockPos.of(pos));
        }
        wear.highest = tag.getInt("highest");
        wear.settled = tag.getBoolean("settled");
        return wear;
    }

    /**
     * The game tests: the dead mound as it comes back after a save and a load, put in this book in its place (the wear
     * must carry on from where it was).
     */
    public DeadMound reloadForTests(ServerLevel level, UUID colony) {
        DeadMound loaded = load(save(new CompoundTag(), level.registryAccess()), level.registryAccess()).get(colony);
        mounds.put(colony, loaded);
        setDirty();
        return loaded;
    }
}
