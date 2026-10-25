package com.antcolonies.savanna.colony;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.colony.Claims;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.NurseFood;
import com.antcolonies.colony.Population;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.block.PreyPileBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Matabele prey pile (M8 step 1e, {@code docs/DESIGN.md} "Step 1e"): the colony's food and the nurses' food, a heap
 * of stung, paralysed termites in the queen's room, a layer for each bunch brought in (about 10 termites, one big ant's
 * load). Real Megaponera eat their prey fresh and keep no store (Yusuf 2026): one termite per ant a day (a layer makes 10
 * meals, {@code workers_per_piece}), the queen and the larvae from the same pile, the larvae with the termite laid on
 * them. A layer uneaten for a day ({@link #ROT_TICKS}) has rotted; in the last quarter of its day the termites have died
 * and the pile lies still and grey ({@link PreyPileBlock#DEAD}). The rotten layers, and the leftovers of the eaten ones
 * (heads, legs; {@link #SCRAPS_PER_LOAD} pieces' worth a load), are carried out to a heap beside the entrance
 * ({@code CarryRemainsGoal}); a load nobody real carries out for a while is carried out by the numbers, its layer laid
 * on the heap out of sight. Until the raids exist the pile is stocked by {@code /antcolonies prey}.
 *
 * <p>A piece is one layer. The count ({@link #edible}, each piece with the time it was laid) is the truth, as with the
 * harvester's bread: the numbers eat from it at once and the layer comes off later, out of sight ({@code NestLedger}).
 */
public class PreyPile implements NurseFood {
    /** A layer uneaten for this long (a day) has rotted. */
    public static final long ROT_TICKS = 24000;
    /** In the last quarter of its day the termites in a layer have died: the pile is dead once its youngest layer is this old. */
    public static final long STILL_AFTER_TICKS = 18000;
    /** The leftovers of this many eaten pieces (80 termites' heads and legs) make one load for the heap (my pick). */
    public static final int SCRAPS_PER_LOAD = 8;
    /** A load waiting this long with nobody real carrying it out is carried out by the numbers (2 minutes, my pick). */
    static final long NUMBERS_CARRY_AFTER_TICKS = 2400;
    /** How long (in ticks) a carrier on its way to a load holds it. */
    private static final long CARRY_CLAIM_TICKS = 2400;
    /** The heap lies this far (sideways) from the entrance at most, and at least {@link #HEAP_NEAREST}. */
    private static final int HEAP_FARTHEST = 4;
    private static final int HEAP_NEAREST = 2;
    /** Heap layers laid out of sight at most a second. */
    private static final int HEAP_PER_SECOND = 50;

    private final Colony colony;
    /** The pieces still good to eat, each the game time it was laid, oldest first. Saved. */
    private final ArrayDeque<Long> edible = new ArrayDeque<>();
    /** Rotted layers still lying in the pile, waiting to be carried out. Saved. */
    private int rotten;
    /** Pieces eaten whose leftovers still lie by the pile ({@link #SCRAPS_PER_LOAD} make a load). Saved. */
    private int scraps;
    /** Loads the numbers carried out (or a carrier dropped) whose heap layer is still to be laid, out of sight. Saved. */
    private int heapOwed;
    /** When a load first waited to be carried out with nobody real on it, or {@link Colony#NOT_SET}. Saved. */
    private long waitingSince = Colony.NOT_SET;
    /** The heap now growing beside the entrance. Saved. */
    @Nullable
    private BlockPos heap;
    /** The ants on their way to a load: ant -> game time the claim runs out. Not saved. */
    private final Claims<UUID> carriers = new Claims<>();
    /** The game time at the last tick (here or away), for a piece counted in with no clock at hand. Not saved. */
    private long lastTime;

    PreyPile(Colony colony) {
        this.colony = colony;
    }

    // --- stocking (the command, until the raids exist) --------------------------------------------------------------

    /** {@code layers} bunches of fresh termites laid on the pile now (as many as there is room for). Returns how many went on. */
    public int stock(ServerLevel level, int layers) {
        int laid = 0;
        for (int i = 0; i < layers; i++) {
            BlockPos spot = pickReturnSpot(level, colony.random());
            if (spot == null || !addLayerAt(level, spot)) {
                break;
            }
            edible.addLast(level.getGameTime());
            laid++;
        }
        lookAfter(level);
        ColonyManager.get(level).markChanged();
        return laid;
    }

    // --- once a second ---------------------------------------------------------------------------------------------

    /** Loaded: layers rot, the piles show fresh or dead, waiting loads are left to the numbers after a while, the heap catches up. */
    void tick(ServerLevel level) {
        lastTime = level.getGameTime();
        rot(lastTime);
        lookAfter(level);
        long now = level.getGameTime();
        carriers.prune(now);
        if (loadsWaiting() <= carriers.size()) {
            waitingSince = Colony.NOT_SET;
        } else if (waitingSince == Colony.NOT_SET) {
            waitingSince = now;
        } else if (now - waitingSince >= NUMBERS_CARRY_AFTER_TICKS) {
            carryOutByNumbers(); // nobody real came for them
            waitingSince = Colony.NOT_SET;
        }
        layHeapOutOfSight(level);
    }

    /** Away: layers rot and the numbers carry everything out at once (the heap layers are laid once the nest is loaded again). */
    void tickAway(ServerLevel level) {
        lastTime = level.getGameTime();
        rot(lastTime);
        carryOutByNumbers();
    }

    /** Every piece past its day has rotted. */
    private void rot(long now) {
        while (!edible.isEmpty() && now - edible.peekFirst() >= ROT_TICKS) {
            edible.pollFirst();
            rotten++;
        }
    }

    /** Whether the pile's termites have died: no piece younger than three quarters of a day (and something lies there). */
    boolean isDead(long now) {
        return edible.isEmpty() ? rotten > 0 : now - edible.peekLast() >= STILL_AFTER_TICKS;
    }

    /** Every heap in the nest shows the pile's look (one look for all: a fresh bunch on top sets the heap twitching). */
    private void lookAfter(ServerLevel level) {
        boolean dead = isDead(level.getGameTime());
        for (BlockPos pos : piles(level)) {
            BlockState state = level.getBlockState(pos);
            BlockState look = PreyPileBlock.withDead(state, dead);
            if (look != state) {
                level.setBlock(pos, look, 2);
            }
        }
    }

    /** The rotten layers and the leftovers go out as numbers: the layers come off the pile later, out of sight, and lie on the heap. */
    private void carryOutByNumbers() {
        while (rotten > 0) {
            rotten--;
            colony.ledger().addNurseFood(-1);
            heapOwed++;
        }
        while (scraps >= SCRAPS_PER_LOAD) {
            scraps -= SCRAPS_PER_LOAD;
            heapOwed++;
        }
    }

    /** The heap layers owed are laid out of every player's sight; with no place for the heap they are scattered and gone. */
    private void layHeapOutOfSight(ServerLevel level) {
        if (heapOwed <= 0) {
            return;
        }
        List<Vec3> eyes = colony.population().viewersInSight(level);
        for (int i = 0; heapOwed > 0 && i < HEAP_PER_SECOND; i++) {
            BlockPos spot = heapSpot(level);
            if (spot == null) {
                heapOwed = 0;
                return;
            }
            if (!Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                return; // a player can see it: later
            }
            if (!layOnHeap(level, spot)) {
                heap = null; // (taken meanwhile: another spot next time)
                return;
            }
            heapOwed--;
        }
    }

    // --- the carriers ----------------------------------------------------------------------------------------------

    /** Leftovers brought in from elsewhere (a termite head bitten off a nestmate's leg, M8 step 2c) join the scraps. */
    public void scrapsLeft(int pieces) {
        scraps += Math.max(0, pieces);
    }

    /** Loads waiting to be carried out: each rotten layer, and every {@link #SCRAPS_PER_LOAD} pieces' leftovers. */
    public int loadsWaiting() {
        return rotten + scraps / SCRAPS_PER_LOAD;
    }

    /** An ant sets out to carry a load out. False if every load waiting has a carrier already. */
    public boolean claimLoad(ServerLevel level, UUID ant) {
        carriers.prune(level.getGameTime());
        if (loadsWaiting() <= carriers.size()) {
            return false;
        }
        carriers.put(ant, level.getGameTime() + CARRY_CLAIM_TICKS);
        return true;
    }

    public void releaseLoad(UUID ant) {
        carriers.remove(ant);
    }

    /** Where the loads lie: a heap of the pile (the leftovers lie round it), else the queen's room; null if neither. */
    @Nullable
    public BlockPos loadSpot(Level level, RandomSource random) {
        List<BlockPos> piles = piles(level);
        if (!piles.isEmpty()) {
            return piles.get(random.nextInt(piles.size()));
        }
        return colony.nest().getFinishedRoomCenter(level);
    }

    /**
     * The carrier takes its load at {@code spot}: a rotten layer off the heap there (first), or the leftovers. False if
     * there is nothing left to carry.
     */
    public boolean pickUpLoad(ServerLevel level, BlockPos spot, UUID ant) {
        carriers.remove(ant);
        if (rotten > 0) {
            rotten--;
            if (!removePieceAt(level, spot)) {
                colony.ledger().addNurseFood(-1); // its layer lies on another heap: it comes off later, out of sight
            }
        } else if (scraps >= SCRAPS_PER_LOAD) {
            scraps -= SCRAPS_PER_LOAD;
        } else {
            return false;
        }
        waitingSince = Colony.NOT_SET; // (somebody real carries them out: the numbers wait again)
        lookAfter(level);
        ColonyManager.get(level).markChanged();
        return true;
    }

    /** A carrier lost its load on the way (it died, or gave up): the numbers finish the job, its layer laid on the heap out of sight. */
    public void loadDropped() {
        heapOwed++;
    }

    /**
     * Where the next load goes: the heap now growing, else a new one beside the entrance, on the ground {@value #HEAP_NEAREST}
     * to {@value #HEAP_FARTHEST} blocks off it, the nearest place free first. Null if there is none.
     */
    @Nullable
    public BlockPos heapSpot(Level level) {
        if (heap != null && hasRoom(level, heap)) {
            return heap;
        }
        BlockPos entrance = colony.getCenter();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -HEAP_FARTHEST; dx <= HEAP_FARTHEST; dx++) {
            for (int dz = -HEAP_FARTHEST; dz <= HEAP_FARTHEST; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < HEAP_NEAREST || distance > HEAP_FARTHEST + 0.5) {
                    continue;
                }
                int x = entrance.getX() + dx;
                int z = entrance.getZ() + dz;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (Math.abs(ground.getY() - entrance.getY()) > 3 || !hasRoom(level, ground)) {
                    continue;
                }
                double score = distance + colony.random().nextDouble() * 0.5; // (not always the same corner)
                if (score < bestDistance) {
                    bestDistance = score;
                    best = ground;
                }
            }
        }
        heap = best;
        return best;
    }

    /** Whether a heap layer can go at {@code pos}: open air on a solid floor, or a remains heap not yet a block high. */
    private static boolean hasRoom(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(SavannaBlocks.TERMITE_REMAINS.get())) {
            return state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS;
        }
        BlockPos below = pos.below();
        return state.isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** A load is laid on the heap at {@code spot}. False if there is no room there. */
    public boolean layOnHeap(Level level, BlockPos spot) {
        if (!hasRoom(level, spot)) {
            return false;
        }
        BlockState state = level.getBlockState(spot);
        var remains = SavannaBlocks.TERMITE_REMAINS.get();
        level.setBlock(spot, state.is(remains) ? remains.oneLayerMore(state) : remains.newHeap(), 3);
        heap = spot;
        return true;
    }

    // --- the heaps in the nest -------------------------------------------------------------------------------------

    /** Every heap of the pile in the nest's rooms (the queen's room first). */
    private List<BlockPos> piles(Level level) {
        List<BlockPos> piles = new ArrayList<>();
        for (BlockPos pos : rooms(level)) {
            if (level.getBlockState(pos).is(SavannaBlocks.PREY_PILE.get())) {
                piles.add(pos);
            }
        }
        return piles;
    }

    private List<BlockPos> rooms(Level level) {
        List<BlockPos> cells = new ArrayList<>(colony.nest().getQueenRoomCells(level));
        for (BlockPos pos : colony.nest().getAllRoomCells(level)) {
            if (!cells.contains(pos)) {
                cells.add(pos);
            }
        }
        return cells;
    }

    private boolean addLayerAt(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        PreyPileBlock pile = SavannaBlocks.PREY_PILE.get();
        if (state.isAir()) {
            BlockPos below = spot.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return false;
            }
            level.setBlock(spot, pile.newHeap(), 3); // (fresh: lookAfter sets every heap's look)
        } else if (state.is(pile) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
            level.setBlock(spot, PreyPileBlock.withDead(pile.oneLayerMore(state), false), 3);
        } else {
            return false;
        }
        return true;
    }

    // --- as the nurses' food (see NurseFood) -----------------------------------------------------------------------

    @Override
    public int available(Level level) {
        return edible.size();
    }

    /**
     * A heap to take a piece from: while there is a piece to eat, or a layer the ledger still has to take away (a piece
     * the numbers ate, a rotten one they carried out), so the heaps always catch up with the count. Null if neither.
     */
    @Override
    @Nullable
    public BlockPos findFood(Level level, RandomSource random) {
        if (edible.isEmpty() && colony.ledger().nurseFood() >= 0) {
            return null;
        }
        List<BlockPos> piles = piles(level);
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /** A worker or a nurse takes a termite (the oldest good one, as far as the count goes) off the heap at {@code spot}; its leftovers stay. */
    @Override
    public ItemStack takeFoodAt(Level level, BlockPos spot, boolean wantRich) {
        if (edible.isEmpty() || !removePieceAt(level, spot)) {
            return ItemStack.EMPTY;
        }
        edible.pollFirst();
        scraps++;
        return piece();
    }

    /** The queen's room: a growing heap (mostly), else an empty cell on a solid floor without brood. Null if there is no room. */
    @Override
    @Nullable
    public BlockPos pickReturnSpot(Level level, RandomSource random) {
        Set<BlockPos> brood = colony.brood().getBroodCells(level);
        List<BlockPos> growing = new ArrayList<>();
        List<BlockPos> empty = new ArrayList<>();
        for (BlockPos pos : colony.nest().getQueenRoomCells(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(SavannaBlocks.PREY_PILE.get())) {
                if (state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
                    growing.add(pos);
                }
            } else if (state.isAir() && !brood.contains(pos) && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                empty.add(pos);
            }
        }
        List<BlockPos> from = !growing.isEmpty() && (empty.isEmpty() || random.nextFloat() < 0.75F) ? growing : empty;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    /** A nurse puts back a termite nobody wanted: good to eat again, as fresh as now (its leftovers were counted too soon). */
    @Override
    public boolean putBack(Level level, BlockPos spot, boolean rich) {
        if (!addLayerAt(level, spot)) {
            return false;
        }
        edible.addLast(level.getGameTime());
        scraps = Math.max(0, scraps - 1);
        return true;
    }

    @Override
    public boolean addPieceAt(Level level, BlockPos spot) {
        return !colony.brood().getBroodCells(level).contains(spot) && addLayerAt(level, spot);
    }

    @Override
    public boolean removePieceAt(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        if (!state.is(SavannaBlocks.PREY_PILE.get())) {
            return false;
        }
        level.setBlock(spot, SavannaBlocks.PREY_PILE.get().oneLayerLess(state), 3);
        return true;
    }

    /** Pieces counted that could be put down nowhere: the newest go out of the count. */
    @Override
    public void forget(int pieces) {
        for (int i = 0; i < pieces && !edible.isEmpty(); i++) {
            edible.pollLast();
        }
    }

    @Override
    public boolean putDownWhereItStands(Level level, AbstractAntEntity ant) {
        if (addLayerAt(level, ant.blockPosition())) {
            edible.addLast(level.getGameTime());
            scraps = Math.max(0, scraps - 1);
            return true;
        }
        return false;
    }

    @Override
    public int counted(ServerLevel level) {
        return edible.size();
    }

    /** One of the numbers takes a counted piece (the oldest good one); its layer comes off later, out of sight. Never rich. */
    @Override
    public boolean takeCounted(boolean wantRich) {
        if (edible.pollFirst() != null) {
            scraps++;
        }
        return false;
    }

    /** A piece brought in by the numbers (an ant carrying one folded into them): good to eat, as fresh as now. */
    @Override
    public void countedIn(boolean rich) {
        edible.addLast(lastTime);
        scraps = Math.max(0, scraps - 1);
    }

    @Override
    public ItemStack piece() {
        return new ItemStack(SavannaItems.PARALYSED_TERMITE.get());
    }

    // --- the colony list and the save ------------------------------------------------------------------------------

    /** For the colony list: " | prey pile 12 (fresh, oldest 0.4 days), 2 rotten, 1 load to carry out, heap 3 owed". */
    public String describe(ServerLevel level) {
        long now = level.getGameTime();
        String oldest = edible.isEmpty() ? "" : String.format(", oldest %.1f days", (now - edible.peekFirst()) / 24000.0);
        return " | prey pile " + edible.size() + " (" + (isDead(now) ? "dead" : "fresh") + oldest + "), " + rotten + " rotten, "
                + loadsWaiting() + " to carry out" + (heapOwed > 0 ? ", heap " + heapOwed + " owed" : "");
    }

    /** How many pieces are good to eat (for tests and the command). */
    public int edible() {
        return edible.size();
    }

    /** Rotten layers still in the pile (for tests). */
    public int rotten() {
        return rotten;
    }

    /** The heap beside the entrance (for tests and the command), or null if none yet. */
    @Nullable
    public BlockPos heap() {
        return heap;
    }

    void save(CompoundTag tag) {
        CompoundTag pile = new CompoundTag();
        pile.putLongArray("edible", edible.stream().mapToLong(Long::longValue).toArray());
        pile.putInt("rotten", rotten);
        pile.putInt("scraps", scraps);
        pile.putInt("heapOwed", heapOwed);
        pile.putLong("waitingSince", waitingSince);
        if (heap != null) {
            pile.putLong("heap", heap.asLong());
        }
        tag.put("preyPile", pile);
    }

    void load(CompoundTag tag) {
        CompoundTag pile = tag.getCompound("preyPile");
        edible.clear();
        for (long laid : pile.getLongArray("edible")) {
            edible.addLast(laid);
        }
        rotten = pile.getInt("rotten");
        scraps = pile.getInt("scraps");
        heapOwed = pile.getInt("heapOwed");
        waitingSince = pile.contains("waitingSince") ? pile.getLong("waitingSince") : Colony.NOT_SET;
        heap = pile.contains("heap") ? BlockPos.of(pile.getLong("heap")) : null;
    }

    /** Tests only: every piece in the pile laid {@code ticks} earlier (a day's rotting in a moment), then rotted and looked at. */
    public void ageForTests(ServerLevel level, long ticks) {
        List<Long> laid = new ArrayList<>(edible);
        edible.clear();
        for (long time : laid) {
            edible.addLast(time - ticks);
        }
        rot(level.getGameTime());
        lookAfter(level);
    }

    /** Tests only: a sample in every saved number (the save and load check). */
    void fillForTests(ServerLevel level) {
        edible.addLast(level.getGameTime() - 100);
        edible.addLast(level.getGameTime());
        rotten = 2;
        scraps = 3;
        heapOwed = 1;
        waitingSince = level.getGameTime() - 5;
        heap = colony.getCenter().offset(2, 0, 1);
    }
}
