package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.FungusCombBlock;
import com.antcolonies.block.SeedPileBlock;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModItems;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The termites' fungus garden (species with a {@code garden} in their profile): the garden rooms round the royal
 * chamber, where the foragers' pieces are built into fungus comb ({@link FungusCombBlock}, one layer per piece, through
 * the food store: the profile's food pile is the comb). The fungus grows white nodules on the comb, and the nurses pick
 * them to feed the queen and the nymphs: for termites this is the nurses' food ({@link NurseFood}) instead of ant bread.
 * The comb ages like the real conveyor belt (the user's request, 2026-10-15; see {@link FungusCombBlock}): new forage
 * goes on top as a fresh layer, a fresh layer ripens, a nodule grows from a ripe layer and turns it old (the fungus
 * has used it up; before, a nodule used the layer up), a ripe layer also goes old by itself in time, the old workers
 * eat old layers from the bottom ({@link WorkerFood}), and an old layer nobody eats crumbles away. So one piece of
 * forage feeds twice: a nodule, then old comb. Nothing is counted here: the comb and the nodules are looked up in the
 * world (what the brain last saw is kept for while the colony is away).
 */
public class FungusGarden implements NurseFood {
    private final Colony colony;
    /** How many nodules have grown since the colony was loaded (each used up a piece of comb). Not saved: for tests. */
    private int nodulesGrown;
    /**
     * What the garden looked like the last second it was loaded (for the brain while the colony is away, when no block
     * may be read): how many comb blocks, how many layers they have over their beds, how many nodules. Saved.
     */
    private int seenCombs;
    private int seenFresh;
    private int seenRipe;
    private int seenOld;
    private int seenNodules;
    /** How many old layers have crumbled away since the colony was loaded (out of the food count). Not saved: for tests. */
    private int layersCrumbled;
    /** Tests only: how many times faster the comb ripens, goes old and crumbles. Not saved. */
    private float ageingSpeedForTests = 1.0F;

    FungusGarden(Colony colony) {
        this.colony = colony;
    }

    /** Whether this colony's species keeps a fungus garden. */
    public boolean exists() {
        return colony.profile().garden().isPresent();
    }

    /** The fungus comb blocks in the dug-out garden rooms. */
    public List<BlockPos> getCombs(Level level) {
        List<BlockPos> combs = new ArrayList<>();
        for (BlockPos pos : colony.nest().getGardenCells(level)) {
            if (level.getBlockState(pos).is(ModBlocks.FUNGUS_COMB.get())) {
                combs.add(pos);
            }
        }
        return combs;
    }

    /** How many layers of comb there are in all the gardens. */
    public int countCombLayers(Level level) {
        int layers = 0;
        for (BlockPos pos : getCombs(level)) {
            layers += level.getBlockState(pos).getValue(SeedPileBlock.LAYERS);
        }
        return layers;
    }

    /**
     * The fungus grows and the comb ages, called once a second (by the queen). On every comb block: each fresh layer
     * ripens about once every {@code ripenTicks}; with a ripe layer and room for another nodule, a nodule sprouts about
     * once every {@code noduleTicks} and that ripe layer goes old; each other ripe layer goes old by itself about once
     * every {@code oldTicks}; and the bottom old layer crumbles away about once every {@code crumbleTicks} (taken out of
     * the food count). Not under an egg or a nymph lying on the comb (it would sink). Returns true if anything changed.
     */
    public boolean grow(ServerLevel level) {
        SpeciesProfile.GardenNumbers numbers = colony.profile().garden().orElse(null);
        if (numbers == null) {
            return false;
        }
        RandomSource random = level.getRandom();
        float chance = chancePerSecond(numbers);
        float ripen = Math.min(1.0F, ageingSpeedForTests * 20.0F / Math.max(20, numbers.ripenTicks()));
        float age = Math.min(1.0F, ageingSpeedForTests * 20.0F / Math.max(20, numbers.oldTicks()));
        float crumble = Math.min(1.0F, ageingSpeedForTests * 20.0F / Math.max(20, numbers.crumbleTicks()));
        java.util.Set<BlockPos> young = colony.brood().getBroodCells(level);
        boolean changed = false;
        int combs = 0;
        int fresh = 0;
        int ripe = 0;
        int old = 0;
        int allNodules = 0;
        for (BlockPos pos : getCombs(level)) {
            BlockState state = level.getBlockState(pos);
            BlockState was = state;
            for (int i = FungusCombBlock.fresh(state); i > 0; i--) {
                if (random.nextFloat() < ripen) {
                    state = FungusCombBlock.ripenOne(state);
                    level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + FungusCombBlock.topOf(state), pos.getZ() + 0.5,
                            2, 0.3, 0.02, 0.3, 0.0); // fine white fungus threads spreading through it
                }
            }
            int nodules = state.getValue(FungusCombBlock.NODULES);
            if (FungusCombBlock.ripe(state) > 0 && nodules < FungusCombBlock.MAX_NODULES && random.nextFloat() < chance) {
                state = FungusCombBlock.ageOne(state).setValue(FungusCombBlock.NODULES, nodules + 1);
                nodulesGrown++;
                level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + FungusCombBlock.topOf(state), pos.getZ() + 0.5,
                        4, 0.25, 0.05, 0.25, 0.0);
            }
            for (int i = FungusCombBlock.ripe(state); i > 0; i--) {
                if (random.nextFloat() < age) {
                    state = FungusCombBlock.ageOne(state);
                }
            }
            for (int i = FungusCombBlock.old(state); i > 0 && !young.contains(pos); i--) {
                if (random.nextFloat() < crumble) {
                    BlockState less = FungusCombBlock.withoutBottomLayer(state);
                    colony.food().used(1);
                    layersCrumbled++;
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                            pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, 3, 0.3, 0.02, 0.3, 0.0);
                    if (less == null) {
                        state = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                        break;
                    }
                    state = less;
                }
            }
            if (state != was) {
                level.setBlock(pos, state, 3);
                changed = true;
            }
            if (state.is(ModBlocks.FUNGUS_COMB.get())) {
                combs++;
                fresh += FungusCombBlock.fresh(state);
                ripe += FungusCombBlock.ripe(state);
                old += FungusCombBlock.old(state);
                allNodules += state.getValue(FungusCombBlock.NODULES);
            }
        }
        seenCombs = combs;
        seenFresh = fresh;
        seenRipe = ripe;
        seenOld = old;
        seenNodules = allNodules;
        return changed;
    }

    /** Tests only: the comb ripens, goes old and crumbles this many times faster. */
    public void setAgeingSpeedForTests(float speed) {
        ageingSpeedForTests = speed;
    }

    /** The chance each second that a comb block with room for it grows a nodule. */
    static float chancePerSecond(SpeciesProfile.GardenNumbers numbers) {
        return 20.0F / Math.max(20, numbers.noduleTicks());
    }

    /** How many comb blocks the garden had the last second it was loaded. */
    int seenCombs() {
        return seenCombs;
    }

    /**
     * What the brain knows of the comb's ages: what it saw the last second the garden was loaded, with what the counts
     * have changed since and the blocks have not caught up with ({@link NestLedger}: layers added on top are fresh,
     * layers taken off the bottom old; layers ripened and gone old).
     */
    int freshKnown() {
        NestLedger ledger = colony.ledger();
        return Math.max(0, seenFresh + Math.max(0, ledger.store()) - ledger.ripened());
    }

    int ripeKnown() {
        NestLedger ledger = colony.ledger();
        return Math.max(0, seenRipe + ledger.ripened() - ledger.aged());
    }

    int oldKnown() {
        NestLedger ledger = colony.ledger();
        return Math.max(0, seenOld + ledger.aged() + Math.min(0, ledger.store()));
    }

    /** How many nodules there were the last second the garden was loaded. */
    int seenNodules() {
        return seenNodules;
    }

    void save(net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.nbt.CompoundTag seen = new net.minecraft.nbt.CompoundTag();
        seen.putInt("combs", seenCombs);
        seen.putInt("fresh", seenFresh);
        seen.putInt("ripe", seenRipe);
        seen.putInt("old", seenOld);
        seen.putInt("nodules", seenNodules);
        tag.put("gardenSeen", seen);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.nbt.CompoundTag seen = tag.getCompound("gardenSeen");
        seenCombs = seen.getInt("combs");
        seenFresh = seen.getInt("fresh");
        seenRipe = seen.contains("ripe") ? seen.getInt("ripe") : seen.getInt("spare"); // (older saves: the layers over the beds)
        seenOld = seen.getInt("old");
        seenNodules = seen.getInt("nodules");
    }

    /** How many old layers have crumbled away since the colony was loaded (for tests). */
    public int getLayersCrumbled() {
        return layersCrumbled;
    }

    /** How many nodules have grown since the colony was loaded (for tests: each turned a ripe layer old). */
    public int getNodulesGrown() {
        return nodulesGrown;
    }

    // ---- the nurses' food: fungus nodules on the comb ----

    @Override
    public int available(Level level) {
        int nodules = 0;
        for (BlockPos pos : getCombs(level)) {
            nodules += level.getBlockState(pos).getValue(FungusCombBlock.NODULES);
        }
        return nodules;
    }

    @Override
    @Nullable
    public BlockPos findFood(Level level, RandomSource random) {
        List<BlockPos> withNodules = new ArrayList<>();
        for (BlockPos pos : getCombs(level)) {
            if (level.getBlockState(pos).getValue(FungusCombBlock.NODULES) > 0) {
                withNodules.add(pos);
            }
        }
        return withNodules.isEmpty() ? null : withNodules.get(random.nextInt(withNodules.size()));
    }

    @Override
    public ItemStack takeFoodAt(Level level, BlockPos spot, boolean wantRich) {
        BlockState state = level.getBlockState(spot);
        if (!state.is(ModBlocks.FUNGUS_COMB.get()) || state.getValue(FungusCombBlock.NODULES) == 0) {
            return ItemStack.EMPTY;
        }
        level.setBlock(spot, state.setValue(FungusCombBlock.NODULES, state.getValue(FungusCombBlock.NODULES) - 1), 3);
        return new ItemStack(ModItems.FUNGUS_NODULE.get());
    }

    @Override
    @Nullable
    public BlockPos pickReturnSpot(Level level, RandomSource random) {
        List<BlockPos> room = new ArrayList<>();
        for (BlockPos pos : getCombs(level)) {
            if (level.getBlockState(pos).getValue(FungusCombBlock.NODULES) < FungusCombBlock.MAX_NODULES) {
                room.add(pos);
            }
        }
        return room.isEmpty() ? null : room.get(random.nextInt(room.size()));
    }

    @Override
    public boolean putBack(Level level, BlockPos spot, boolean rich) {
        BlockState state = level.getBlockState(spot);
        if (!state.is(ModBlocks.FUNGUS_COMB.get()) || state.getValue(FungusCombBlock.NODULES) >= FungusCombBlock.MAX_NODULES) {
            return false;
        }
        level.setBlock(spot, state.setValue(FungusCombBlock.NODULES, state.getValue(FungusCombBlock.NODULES) + 1), 3);
        return true;
    }

    @Override
    public boolean addPieceAt(Level level, BlockPos spot) {
        return putBack(level, spot, false);
    }

    @Override
    public boolean removePieceAt(Level level, BlockPos spot) {
        return !takeFoodAt(level, spot, false).isEmpty();
    }

    @Override
    public void forget(int pieces) {
        // nodules are not counted: nothing to take out of a count
    }

    @Override
    public boolean putDownWhereItStands(Level level, AbstractAntEntity ant) {
        // Onto the comb it stands on, if that has room; otherwise the nodule is lost (it is only a little fungus).
        if (putBack(level, ant.blockPosition(), false)) {
            return true;
        }
        AntColoniesMod.LOGGER.info("[garden] termite {} could take its nodule nowhere and lost it at {}", ant.getId(), ant.blockPosition().toShortString());
        return false;
    }
}
