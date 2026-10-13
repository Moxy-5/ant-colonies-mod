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
 * the food store: the profile's food pile is the comb). The fungus grows white nodules on the comb, each using up a
 * layer of it, and the nurses pick them to feed the queen and the nymphs: for termites this is the nurses' food
 * ({@link NurseFood}) instead of ant bread. Nothing is counted here: the comb and the nodules are looked up in the world.
 */
public class FungusGarden implements NurseFood {
    private final Colony colony;
    /** How many nodules have grown since the colony was loaded (each used up a piece of comb). Not saved: for tests. */
    private int nodulesGrown;

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
     * The fungus grows, called once a second (by the queen). On every comb block with a layer to spare (the bottom layer
     * stays, the bed of the comb) and room for another nodule, a nodule sprouts about once every {@code noduleTicks}, and
     * it uses up a layer of the comb. Returns true if anything grew.
     */
    public boolean grow(ServerLevel level) {
        SpeciesProfile.GardenNumbers numbers = colony.profile().garden().orElse(null);
        if (numbers == null) {
            return false;
        }
        float chance = 20.0F / Math.max(20, numbers.noduleTicks());
        boolean grew = false;
        for (BlockPos pos : getCombs(level)) {
            BlockState state = level.getBlockState(pos);
            int layers = state.getValue(SeedPileBlock.LAYERS);
            int nodules = state.getValue(FungusCombBlock.NODULES);
            if (layers >= 2 && nodules < FungusCombBlock.MAX_NODULES && level.getRandom().nextFloat() < chance) {
                level.setBlock(pos, state.setValue(SeedPileBlock.LAYERS, layers - 1).setValue(FungusCombBlock.NODULES, nodules + 1), 3);
                colony.food().used(1);
                nodulesGrown++;
                level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + FungusCombBlock.topOf(state), pos.getZ() + 0.5,
                        4, 0.25, 0.05, 0.25, 0.0);
                grew = true;
            }
        }
        return grew;
    }

    /** How many nodules have grown since the colony was loaded (for tests: each used up one stored piece). */
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
    public boolean putDownWhereItStands(Level level, AbstractAntEntity ant) {
        // Onto the comb it stands on, if that has room; otherwise the nodule is lost (it is only a little fungus).
        if (putBack(level, ant.blockPosition(), false)) {
            return true;
        }
        AntColoniesMod.LOGGER.info("[garden] termite {} could take its nodule nowhere and lost it at {}", ant.getId(), ant.blockPosition().toShortString());
        return false;
    }
}
