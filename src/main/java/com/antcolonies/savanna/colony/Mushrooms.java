package com.antcolonies.savanna.colony;

import com.antcolonies.colony.Colony;
import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.block.TermitomycesBlock;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Termitomyces mushrooms (M7 step 5c): the termites' garden fungus fruiting above ground. When a rain ends, a colony
 * whose fungus garden is healthy (enough layers of comb, see the profile's {@code garden}) sends up a flush of
 * mushrooms ({@link TermitomycesBlock}) on the ground round its mound and now and then on the mound's lower slopes,
 * within the garden's {@code mushroom_radius} of the entrance; a few of them are giants. The mushrooms then live on
 * their own (they grow, open, wilt and go on random ticks), so picking them does not harm the colony. Never in the
 * mound's plan (where the termites are about to build), the nest, a foraging hole or the entrance. Nothing is saved:
 * the weather is watched from the colony's first second after loading.
 */
public class Mushrooms {
    /** How far above or below the entrance's ground a mushroom may come up (lower slopes, small dips). */
    private static final int MAX_RISE = 4;
    private static final int TRIES_PER_MUSHROOM = 12;

    private final Colony colony;
    /** Whether it rained at the last look (null before the first look). */
    @Nullable
    private Boolean wasRaining;
    /** For tests: the weather this colony sees instead of the world's (null: the world's). */
    @Nullable
    private Boolean rainForTests;
    /** How many came up after the last rain since the colony was loaded (-1: no rain has ended yet). For the list. */
    private int lastFlush = -1;

    Mushrooms(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private SpeciesProfile.GardenNumbers numbers() {
        return colony.profile().garden().orElse(null);
    }

    /** Called once a second (by the queen): when a rain has just ended, a healthy garden sends up its mushrooms. */
    public void tick(ServerLevel level) {
        if (numbers() == null) {
            return;
        }
        boolean raining = rainForTests != null ? rainForTests : level.isRaining();
        boolean ended = Boolean.TRUE.equals(wasRaining) && !raining;
        wasRaining = raining;
        if (ended) {
            int wanted = wanted(level);
            lastFlush = sprout(level, wanted, false);
            AntColoniesMod.LOGGER.info("[mushrooms] the rain ended at colony {}: {} of {} mushrooms came up (comb {} layers)",
                    colony.getId(), lastFlush, wanted, combLayers(level));
        }
    }

    /** The layers of comb in the garden (none without one). */
    private int combLayers(Level level) {
        return colony.part(SavannaParts.FUNGUS_GARDEN).map(garden -> garden.countCombLayers(level)).orElse(0);
    }

    /** How many mushrooms the garden sends up after a rain now (0 if it is not healthy yet). */
    public int wanted(Level level) {
        SpeciesProfile.GardenNumbers numbers = numbers();
        return numbers == null ? 0 : numbers.mushroomsFor(combLayers(level));
    }

    /** How many came up after the last rain since the colony was loaded, -1 if no rain has ended since. */
    public int getLastFlush() {
        return lastFlush;
    }

    /**
     * Sends up {@code count} mushroom buttons round the mound (fewer if not enough spots are found), each a giant one
     * with the profile's chance, or every one where a giant fits if {@code giants}. Returns how many came up.
     */
    public int sprout(ServerLevel level, int count, boolean giants) {
        SpeciesProfile.GardenNumbers numbers = numbers();
        if (numbers == null || count <= 0) {
            return 0;
        }
        RandomSource random = colony.random();
        int sprouted = 0;
        for (int tries = 0; tries < count * TRIES_PER_MUSHROOM && sprouted < count; tries++) {
            BlockPos cell = pickSpot(level, random, numbers.mushroomRadius());
            if (cell == null) {
                continue;
            }
            boolean giant = (giants || random.nextFloat() < numbers.giantMushroomChance()) && mayBeGiant(level, cell);
            level.setBlock(cell, SavannaBlocks.TERMITOMYCES.get().defaultBlockState().setValue(TermitomycesBlock.GIANT, giant), 3);
            level.sendParticles(ParticleTypes.WHITE_ASH, cell.getX() + 0.5, cell.getY() + 0.1, cell.getZ() + 0.5, 6, 0.2, 0.05, 0.2, 0.0);
            sprouted++;
        }
        return sprouted;
    }

    /** A random free spot on the ground (or the mound's lower slopes) round the entrance where a mushroom may come up. */
    @Nullable
    private BlockPos pickSpot(ServerLevel level, RandomSource random, int radius) {
        BlockPos entrance = colony.nest().getEntrance();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 2.0 + random.nextDouble() * Math.max(0, radius - 2);
        int x = entrance.getX() + Mth.floor(Math.cos(angle) * distance + 0.5);
        int z = entrance.getZ() + Mth.floor(Math.sin(angle) * distance + 0.5);
        if (!level.hasChunkAt(new BlockPos(x, entrance.getY(), z))) {
            return null;
        }
        BlockPos cell = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return isGoodSpot(level, cell) ? cell : null;
    }

    /** Whether a mushroom may come up in {@code cell}. */
    public boolean isGoodSpot(Level level, BlockPos cell) {
        BlockPos entrance = colony.nest().getEntrance();
        int rise = cell.getY() - (entrance.getY() + 1);
        if (Math.abs(rise) > MAX_RISE || !level.canSeeSky(cell)) {
            return false;
        }
        BlockState here = level.getBlockState(cell);
        if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || here.is(SavannaBlocks.TERMITOMYCES.get())) {
            return false; // air, or grass a mushroom pushes aside
        }
        BlockPos ground = cell.below();
        if (!TermitomycesBlock.isGround(level.getBlockState(ground)) || ground.equals(entrance)) {
            return false;
        }
        return !isTermiteSpace(level, cell) && !colony.foragingHoles().isHole(level, ground);
    }

    /** A cell the termites use or are about to build in: the mound's plan, the nest, a foraging hole. */
    private boolean isTermiteSpace(Level level, BlockPos cell) {
        return colony.moundParts(level).containsKey(cell) || colony.nest().isInside(level, cell)
                || colony.foragingHoles().isHole(level, cell);
    }

    /** Whether a giant may grow here: on earth (not on the mound), with room for its stalk and cap clear of the termites. */
    private boolean mayBeGiant(Level level, BlockPos cell) {
        if (!level.getBlockState(cell.below()).is(BlockTags.DIRT) || !SavannaBlocks.GIANT_TERMITOMYCES.get().fitsAt(level, cell)) {
            return false;
        }
        return !isTermiteSpace(level, cell.above()) && !isTermiteSpace(level, cell.above(2));
    }

    /** For tests: the weather this colony sees (true rain, false dry), or null for the world's weather again. */
    public void setRainForTests(@Nullable Boolean raining) {
        this.rainForTests = raining;
    }
}
