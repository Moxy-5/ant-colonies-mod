package com.antcolonies.savanna.entity.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.TermiteTunnelBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.colony.Mound;
import com.antcolonies.savanna.colony.MoundReach;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.Foothold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A termite holding a soil pellet takes it to the mound and adds it to the next block being built (a layer of soil, or
 * a whole ramp, spire piece or chimney mouth; see {@link com.antcolonies.savanna.colony.Mound#pickBuildSpot}). It works from
 * inside, by the rules of {@link MoundReach}: from the shaft or the nest, or from a tunnel's channel, where it bites a
 * small window to the outside, adds the soil beside it, and plugs the window again (windows only at night or in the
 * rain; by day it only builds what it can reach from the shaft). A spot it finds no way to from inside is left for a
 * while (the tunnel to it may not be carved yet) and it takes another. Only when the mound has not grown for a while
 * (see {@link com.antcolonies.savanna.colony.Mound#mayUseFallbacks}) does it fall back: from outside at night, then from 2 blocks
 * away (for surprises like a player breaking the tunnels; the plan never needs them). With no spot free it keeps its
 * pellet until one is.
 */
public class BuildMoundGoal extends Goal {
    /** If the termite has not put its soil down in this many ticks, it tries another spot. */
    private static final int GIVE_UP_TICKS = 600;
    /** After this many spots with no route to them, it waits a while before trying again. */
    private static final int MAX_FAILED_ROUTES = 4;
    /**
     * A termite that finds no spot for its soil this many times in a row (one look every half second or so, so about
     * 15 seconds) leaves it behind: the mound has nothing more to build for now.
     */
    private static final int MAX_LOOKS_WITHOUT_SPOT = 30;
    /** How long (in ticks) biting a window open takes before the soil goes out through it. */
    private static final int WINDOW_TICKS = 12;

    /** Where a termite works from: inside (the usual way), outside at night, or from 2 blocks away (the last fallback). */
    public enum Reach {
        INSIDE, OUTSIDE, FAR
    }

    private final AbstractAntEntity ant;
    @Nullable
    private Colony colony;
    /** Its mound (asked once in {@link #canUse}: only a species that lists the mound builds one). */
    @Nullable
    private Mound mound;
    @Nullable
    private BlockPos spot;
    private Reach reach = Reach.INSIDE;
    private int ticks;
    private int failedRoutes;
    private int looksWithoutSpot;
    /** The tunnel block whose window it has bitten open, and the side, while it works through it. */
    @Nullable
    private BlockPos windowIn;
    @Nullable
    private Direction windowSide;
    private int windowTicks;

    public BuildMoundGoal(AbstractAntEntity ant) {
        this.ant = ant;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Any foothold from which an insect can work on {@code block}: in one of the 26 cells round it. */
    public static Predicate<Foothold> withinReach(BlockPos block) {
        return at -> {
            BlockPos cell = at.cell();
            return !cell.equals(block) && Math.abs(cell.getX() - block.getX()) <= 1 && Math.abs(cell.getY() - block.getY()) <= 1
                    && Math.abs(cell.getZ() - block.getZ()) <= 1;
        };
    }

    /**
     * Where an insect may build on {@code block} from with this reach: from inside by the rules of {@link MoundReach}
     * (windows only while the colony may open them); from outside, any foothold in the 26 cells round it; from far, any
     * within 2 blocks (outside only while it may go out).
     */
    public static Predicate<Foothold> workFrom(AbstractAntEntity ant, Colony colony, Mound mound, BlockPos block, Reach reach) {
        Level level = ant.level();
        return switch (reach) {
            case INSIDE -> {
                boolean windows = mound.mayOpenWindows(level);
                yield at -> MoundReach.canBuildFrom(level, colony, at, block, windows);
            }
            case OUTSIDE -> withinReach(block);
            case FAR -> at -> MoundReach.isWithin(at.cell(), block, 2) && (ant.mayGoOut() || colony.nest().isInside(level, at.cell()));
        };
    }

    /** Whether the insect is now in one of the 26 cells round {@code block}. */
    public static boolean isWithinReach(AbstractAntEntity ant, BlockPos block) {
        BlockPos cell = ant.blockPosition();
        return !cell.equals(block) && Math.abs(cell.getX() - block.getX()) <= 1 && Math.abs(cell.getY() - block.getY()) <= 1
                && Math.abs(cell.getZ() - block.getZ()) <= 1;
    }

    @Override
    public boolean canUse() {
        if (!ant.isCarrying() || ant.getColonyId() == null || ant.getRandom().nextInt(10) != 0
                || !(ant.level() instanceof ServerLevel level)) {
            return false;
        }
        colony = ColonyManager.get(level).getColony(ant.getColonyId());
        mound = colony == null ? null : colony.part(SavannaParts.MOUND).orElse(null);
        spot = mound == null || !mound.isActive() ? null : mound.pickBuildSpot(level, ant);
        if (spot == null) {
            if (++looksWithoutSpot >= MAX_LOOKS_WITHOUT_SPOT) {
                leaveSoilBehind(level);
            }
            return false;
        }
        looksWithoutSpot = 0;
        return true;
    }

    /** Puts the soil down where the termite is: it crumbles away (nothing to build with it now). */
    private void leaveSoilBehind(ServerLevel level) {
        BlockState soil = ant.getCarriedBlock();
        if (soil != null) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, soil), ant.getX(), ant.getY() + 0.1, ant.getZ(), 4, 0.1, 0.05, 0.1, 0.02);
        }
        ant.setCarriedBlock(null);
        looksWithoutSpot = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return colony != null && mound != null && spot != null && ant.isCarrying();
    }

    @Override
    public void start() {
        ticks = 0;
        failedRoutes = 0;
        walkToSpot();
    }

    @Override
    public void stop() {
        ant.getNavigator().stop();
        closeWindow();
        if (mound != null) {
            mound.releaseBuildSpot(ant.getUUID());
        }
        spot = null;
        colony = null;
        mound = null;
    }

    @Override
    public void tick() {
        if (spot == null || colony == null || mound == null || !(ant.level() instanceof ServerLevel level)) {
            return;
        }
        if (++ticks >= GIVE_UP_TICKS) {
            SavannaMod.LOGGER.info("[mound] termite at {} gave up on spot {} (navigator done: {}, still a spot: {}, reach {})",
                    ant.blockPosition().toShortString(), spot.toShortString(), ant.getNavigator().isDone(), mound.isBuildSpot(level, spot), reach);
            closeWindow();
            spot = null;
            return;
        }
        ant.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5);
        if (!ant.getNavigator().isDone()) {
            return;
        }
        if (canWorkHere(level)) {
            place(level);
        } else {
            closeWindow();
            walkToSpot();
        }
    }

    /** Whether the termite can work on its spot from where it is now, with the reach it walked there for. */
    private boolean canWorkHere(Level level) {
        Foothold at = ant.getNavigator().getFoothold();
        return switch (reach) {
            case INSIDE -> at != null && MoundReach.canBuildFrom(level, colony, at, spot, mound.mayOpenWindows(level));
            case OUTSIDE -> isWithinReach(ant, spot);
            case FAR -> MoundReach.isWithin(ant.blockPosition(), spot, 2);
        };
    }

    /**
     * Plans the walk to the spot: from inside if it can, else from outside at night, else from 2 blocks away; tries other
     * spots when there is no way at all, and stops trying after a few.
     */
    private void walkToSpot() {
        while (spot != null) {
            boolean fallbacks = mound.mayUseFallbacks(ant.level());
            for (Reach way : Reach.values()) {
                if (way != Reach.INSIDE && !fallbacks || way == Reach.OUTSIDE && !ant.mayGoOut()) {
                    continue;
                }
                if (ant.getNavigator().moveTo(workFrom(ant, colony, mound, spot, way), spot, 1.0)) {
                    if (way != Reach.INSIDE) {
                        SavannaMod.LOGGER.info("[mound] termite at {} builds {} from {} (no way from inside)",
                                ant.blockPosition().toShortString(), spot.toShortString(), way.name().toLowerCase());
                    }
                    reach = way;
                    return;
                }
            }
            mound.markUnreachable(ant.level(), spot);
            if (++failedRoutes >= MAX_FAILED_ROUTES) {
                SavannaMod.LOGGER.debug("[mound] termite at {} found no way to mound spots from inside for now (last: {})", ant.blockPosition().toShortString(), spot.toShortString());
                spot = null;
                return;
            }
            spot = mound.pickBuildSpot(ant.level(), ant);
        }
    }

    private void place(ServerLevel level) {
        // From a tunnel's channel, the soil goes out through a window: bite it open first, then build.
        if (reach == Reach.INSIDE && windowSide == null) {
            Foothold at = ant.getNavigator().getFoothold();
            Direction side = at == null ? null : MoundReach.windowFor(level, colony, at, spot);
            if (side != null && level.getBlockState(at.cell()).getBlock() instanceof TermiteTunnelBlock
                    && TermiteTunnelBlock.setWindow(level, at.cell(), side, true)) {
                windowIn = at.cell();
                windowSide = side;
                windowTicks = 0;
                level.playSound(null, windowIn, SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 0.3F, 1.4F);
            }
        }
        if (windowSide != null && ++windowTicks < WINDOW_TICKS) {
            return;
        }
        if (mound.addLayer(level, spot)) {
            BlockState now = level.getBlockState(spot);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, now),
                    spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, spot, now.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.4F, 1.2F);
            ant.setCarriedBlock(null);
            ColonyManager.get(level).markChanged();
            closeWindow();
            spot = null;
            return;
        }
        if (!mound.isBuildSpot(level, spot)) {
            // Someone else finished it, or it can't take soil any more: another spot.
            closeWindow();
            spot = mound.pickBuildSpot(level, ant);
            if (spot != null) {
                walkToSpot();
            }
        }
        // Otherwise a creature is standing in it: wait a moment (the spot is ours, nobody else brings soil to it).
    }

    /** Plugs the window it bit open again (if it still opens to the air: soil put right against it closes it anyway). */
    private void closeWindow() {
        if (windowIn != null && windowSide != null) {
            Level level = ant.level();
            if (level.getBlockState(windowIn).getBlock() instanceof TermiteTunnelBlock
                    && level.getBlockState(windowIn).getValue(TermiteTunnelBlock.OPEN.get(windowSide))) {
                TermiteTunnelBlock.setWindow(level, windowIn, windowSide, false);
                level.playSound(null, windowIn, SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 0.3F, 1.2F);
            }
        }
        windowIn = null;
        windowSide = null;
        windowTicks = 0;
    }
}
