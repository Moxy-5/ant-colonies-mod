package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ant bread: the nurses mill seeds from the store into bread (plain, or rich from rich seeds), kept as heaps in the
 * nursery (or the queen's room), and feed it to the queen and the larvae. Counts the bread and who is milling.
 */
public class Bakery implements NurseFood {
    // How much bread the nurses keep ready for the queen and how long milling takes come from the species profile:
    // colony.profile().food().
    /** How long (in ticks) a worker on its way to mill a seed counts as making bread. */
    private static final int MILL_CLAIM_TICKS = 1300;

    private final Colony colony;

    /** The pieces of bread lying in bread piles in the nest. Saved. */
    private int bread;
    /** How many of the pieces of bread in the nest are rich ant bread (never more than {@link #bread}). Saved. */
    private int richBread;
    /** The workers carrying a seed to the mill or milling it: ant -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> millers = new Claims<>();
    /** The workers carrying a rich seed to the mill or milling it. Not saved. */
    private final Set<UUID> richMillers = new HashSet<>();

    Bakery(Colony colony) {
        this.colony = colony;
    }

    public int getBread() {
        return bread;
    }

    public int getRichBread() {
        return Math.min(richBread, bread);
    }

    /** How many seeds are being milled right now (on their way to the mill, or in it). */
    public int getMilling() {
        return millers.size();
    }

    /**
     * Whether more bread should be milled: there is a place for it (the nursery, or the queen's room while there is no
     * nursery), and the pile plus the seeds already being milled are under what is wanted: one meal for the queen (while
     * she has room for more young) plus what the hungry larvae still have to eat.
     */
    public boolean needsBread(ServerLevel level) {
        if (!colony.profile().food().makesBread()) {
            return false; // a species that mills no bread (termites)
        }
        millers.prune(level.getGameTime());
        Brood brood = colony.brood();
        int wanted = (brood.isFull(level) ? 0 : colony.profile().food().breadStock()) + brood.larvaHunger(level);
        return bread + millers.size() < wanted && pickBreadSpot(level, level.getRandom()) != null;
    }

    /** A worker sets out to mill a seed. False if no more bread is needed right now. */
    public boolean claimMilling(ServerLevel level, UUID ant) {
        if (!needsBread(level)) {
            return false;
        }
        millers.put(ant, level.getGameTime() + MILL_CLAIM_TICKS);
        return true;
    }

    /** The worker has made its bread, or gave up. */
    public void releaseMilling(UUID ant) {
        millers.remove(ant);
        richMillers.remove(ant);
    }

    /** This worker took a rich seed from the store (for the mill, unless it eats it: {@link #removeRichMiller}). */
    void addRichMiller(UUID ant) {
        richMillers.add(ant);
    }

    void removeRichMiller(UUID ant) {
        richMillers.remove(ant);
    }

    /**
     * Whether the next seed to be milled should be a rich one: rich bread is wanted (the queen is not in a burst and
     * has not enough rich bread fed or ready yet) and the store has a rich seed.
     */
    public boolean wantsRichBread() {
        QueenCare queen = colony.queenCare();
        return queen.getBurstLeft() == 0 && !queen.isIncubating() && colony.food().getRichSeeds() > 0
                && queen.getQueenRich() + getRichBread() + richMillers.size() < colony.profile().queen().richMeal();
    }

    /**
     * A worker that has just picked up a seed outside decides where it goes: to the food store if that has room,
     * otherwise (no store yet, or full) to the mill if bread is needed. True = the mill; the worker is then counted.
     */
    public boolean claimSeedForBread(ServerLevel level, UUID ant) {
        return colony.food().pickFoodSpot(level, level.getRandom()) == null && claimMilling(level, ant);
    }

    /**
     * A spot for one more piece of bread, or null if there is none: a growing heap or an empty cell of the nursery, and
     * while the nursery has none, the same in the queen's room. Cells with an egg are left out.
     */
    @Nullable
    public BlockPos pickBreadSpot(Level level, RandomSource random) {
        Set<BlockPos> eggs = colony.brood().getBroodCells(level);
        Nest nest = colony.nest();
        for (List<BlockPos> cells : List.of(nest.getNurseryCells(level), nest.getHomeRoomCells(level))) {
            List<BlockPos> growing = new ArrayList<>();
            List<BlockPos> empty = new ArrayList<>();
            for (BlockPos pos : cells) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() && !eggs.contains(pos)) {
                    empty.add(pos);
                } else if (state.is(ModBlocks.BREAD_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
                    growing.add(pos);
                }
            }
            List<BlockPos> from = !growing.isEmpty() && (empty.isEmpty() || random.nextFloat() < 0.75F) ? growing : empty;
            if (!from.isEmpty()) {
                return from.get(random.nextInt(from.size()));
            }
        }
        return null;
    }

    /** A worker puts its freshly milled bread at {@code spot}: the heap grows by a layer (or a new one starts). False if that spot is full. */
    /**
     * Last resort for an ant holding bread it can take nowhere (no heap to reach, nobody wants it): it puts the piece
     * down where it stands, as a bread heap, so the colony never loses it. Only if even that is impossible (no floor, the
     * cell taken) is it lost, and that is logged. Returns true if the piece was put down.
     */
    @Override
    public boolean putDownWhereItStands(Level level, AbstractAntEntity ant) {
        return putDownBreadWhereItStands(level, ant);
    }

    public boolean putDownBreadWhereItStands(Level level, AbstractAntEntity ant) {
        BlockPos here = ant.blockPosition();
        boolean floor = level.getBlockState(here.below()).isFaceSturdy(level, here.below(), Direction.UP);
        if (floor && depositBread(level, here, ant.isCarryingRichBread())) {
            AntColoniesMod.LOGGER.info("[bread] ant {} could take its bread nowhere and put it down at {}", ant.getId(), here.toShortString());
            return true;
        }
        AntColoniesMod.LOGGER.info("[bread] ant {} could take its bread nowhere and lost it at {}", ant.getId(), here.toShortString());
        return false;
    }

    public boolean depositBread(Level level, BlockPos spot, boolean rich) {
        if (!addBreadAt(level, spot)) {
            return false;
        }
        countIn(rich);
        return true;
    }

    private boolean addBreadAt(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        if (state.isAir()) {
            level.setBlock(spot, ModBlocks.BREAD_PILE.get().defaultBlockState(), 3);
        } else if (state.is(ModBlocks.BREAD_PILE.get()) && state.getValue(SeedPileBlock.LAYERS) < SeedPileBlock.MAX_LAYERS) {
            level.setBlock(spot, state.setValue(SeedPileBlock.LAYERS, state.getValue(SeedPileBlock.LAYERS) + 1), 3);
        } else {
            return false;
        }
        return true;
    }

    /** One more piece of bread, counted now; its heap layer is put down later (see {@link NestLedger}). */
    void countIn(boolean rich) {
        bread++;
        if (rich) {
            richBread++;
        }
    }

    /**
     * A piece of bread is taken out of the count (by the numbers: for the queen or a larva); its layer comes off a heap
     * later, out of sight. Rich if {@code wantRich} and there is some, or if all of it is rich. Returns whether it was
     * rich. Call only while {@link #getBread} is above 0.
     */
    boolean takeCounted(boolean wantRich) {
        richBread = Math.min(richBread, bread);
        boolean rich = richBread > 0 && (wantRich || bread <= richBread);
        bread = Math.max(0, bread - 1);
        if (rich) {
            richBread--;
        }
        return rich;
    }

    /**
     * Whether the numbers should mill more bread while the colony is away (nobody real does it then): less bread than
     * one meal for the queen (while she has room for more young) plus what the hungry young still have to eat.
     */
    boolean needsBreadAway(ServerLevel level) {
        if (!colony.profile().food().makesBread()) {
            return false;
        }
        Brood brood = colony.brood();
        return bread < (brood.isFull(level) ? 0 : colony.profile().food().breadStock()) + brood.larvaHunger(level);
    }

    @Override
    public boolean addPieceAt(Level level, BlockPos spot) {
        return !colony.brood().getBroodCells(level).contains(spot) && addBreadAt(level, spot);
    }

    @Override
    public boolean removePieceAt(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        if (!state.is(ModBlocks.BREAD_PILE.get())) {
            return false;
        }
        int layers = state.getValue(SeedPileBlock.LAYERS);
        level.setBlock(spot, layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SeedPileBlock.LAYERS, layers - 1), 3);
        return true;
    }

    @Override
    public void forget(int pieces) {
        bread = Math.max(0, bread - pieces);
        richBread = Math.min(richBread, bread);
    }

    /**
     * A heap of bread to take a piece from, or null if there is none. Every room is searched, because bread made while
     * the queen lived in another room stays there when she moves down.
     */
    @Nullable
    public BlockPos findBreadPile(Level level, RandomSource random) {
        Nest nest = colony.nest();
        List<BlockPos> piles = new ArrayList<>();
        for (List<BlockPos> cells : List.of(nest.getNurseryCells(level), nest.getHomeRoomCells(level), nest.getFoodStoreCells(level),
                nest.getQueenRoomCells(level))) {
            for (BlockPos pos : cells) {
                if (level.getBlockState(pos).is(ModBlocks.BREAD_PILE.get())) {
                    piles.add(pos);
                }
            }
        }
        return piles.isEmpty() ? null : piles.get(random.nextInt(piles.size()));
    }

    /**
     * A worker takes one piece of bread off the heap at {@code pile}. Returns the piece (rich ant bread or plain ant
     * bread), or an empty stack if the heap is gone. Rich bread is taken if {@code wantRich} (for the queen) and the
     * colony has some, or if all the bread in the nest is rich.
     */
    public ItemStack takeBreadAt(Level level, BlockPos pile, boolean wantRich) {
        BlockState state = level.getBlockState(pile);
        if (!state.is(ModBlocks.BREAD_PILE.get())) {
            return ItemStack.EMPTY;
        }
        int layers = state.getValue(SeedPileBlock.LAYERS);
        level.setBlock(pile, layers <= 1 ? Blocks.AIR.defaultBlockState() : state.setValue(SeedPileBlock.LAYERS, layers - 1), 3);
        richBread = Math.min(richBread, bread);
        boolean rich = richBread > 0 && (wantRich || bread <= richBread);
        bread = Math.max(0, bread - 1);
        if (rich) {
            richBread--;
        }
        return new ItemStack(rich ? ModItems.RICH_ANT_BREAD.get() : ModItems.ANT_BREAD.get());
    }

    // As the nurses' food (see NurseFood): bread heaps.

    @Override
    public int available(Level level) {
        return bread;
    }

    @Override
    @Nullable
    public BlockPos findFood(Level level, RandomSource random) {
        return findBreadPile(level, random);
    }

    @Override
    public ItemStack takeFoodAt(Level level, BlockPos spot, boolean wantRich) {
        return takeBreadAt(level, spot, wantRich);
    }

    @Override
    @Nullable
    public BlockPos pickReturnSpot(Level level, RandomSource random) {
        return pickBreadSpot(level, random);
    }

    @Override
    public boolean putBack(Level level, BlockPos spot, boolean rich) {
        return depositBread(level, spot, rich);
    }

    void save(CompoundTag tag) {
        tag.putInt("bread", bread);
        tag.putInt("richBread", richBread);
    }

    void load(CompoundTag tag) {
        bread = tag.getInt("bread");
        richBread = tag.getInt("richBread");
    }
}
