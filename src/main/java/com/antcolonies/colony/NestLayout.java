package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a nest's rooms go (see {@link NestPlan}), and choosing it from the ground ({@link Planner}). Decided once by
 * {@link Nest} and saved: worked out again after a reload, the answer could differ, because the ground has changed
 * since (seed and bread heaps lie in the rooms). The side rooms and the garden rooms follow from the rest (the profile
 * says which and how many, the direction where), so they are not saved themselves; the room sizes are (a nest keeps
 * the sizes it was planned with when the species file changes).
 */
record NestLayout(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens,
        Set<BlockPos> cells, int shaftDepth, Direction direction, @Nullable NestPlan.Chamber founding, NestPlan.RoomSizes sizes) {
    NestLayout(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens, int shaftDepth,
            Direction direction, @Nullable NestPlan.Chamber founding, NestPlan.RoomSizes sizes) {
        this(chambers, sideRooms, gardens, cellsOf(chambers, sideRooms, gardens), shaftDepth, direction, founding, sizes);
    }

    /** The level or side room used for this purpose (or the founding chamber), or null if the nest has none. */
    @Nullable
    NestPlan.Chamber of(NestPlan.Kind kind) {
        if (kind == NestPlan.Kind.FOUNDING) {
            return founding;
        }
        for (NestPlan.Chamber chamber : chambers) {
            if (chamber.kind() == kind) {
                return chamber;
            }
        }
        for (NestPlan.Chamber chamber : sideRooms) {
            if (chamber.kind() == kind) {
                return chamber;
            }
        }
        return null;
    }

    private static Set<BlockPos> cellsOf(List<NestPlan.Chamber> chambers, List<NestPlan.Chamber> sideRooms, List<NestPlan.Chamber> gardens) {
        Set<BlockPos> cells = new HashSet<>();
        for (List<NestPlan.Chamber> list : List.of(chambers, sideRooms, gardens)) {
            for (NestPlan.Chamber chamber : list) {
                cells.addAll(chamber.cells());
            }
        }
        return cells;
    }

    /**
     * How big the rooms of this colony's new nest are: the species profile's {@code nest.room_sizes}. A species with a
     * garden keeps its royal chamber 3x3, because the garden rooms are laid out round a 3x3 room ({@link NestPlan#gardens}).
     */
    static NestPlan.RoomSizes sizesFor(Colony colony) {
        NestPlan.RoomSizes sizes = colony.profile().nest().roomSizes();
        if (colony.profile().garden().isPresent() && !sizes.queen().equals(NestPlan.GARDEN_SIZE)) {
            AntColoniesMod.LOGGER.warn("The royal chamber of {} stays 3x3 (its garden rooms are laid out round it), not {}",
                    colony.getSpecies(), sizes.queen());
            sizes = sizes.withQueen(NestPlan.GARDEN_SIZE);
        }
        return sizes;
    }

    /**
     * Works out a colony's nest layout: chosen from the ground for a new nest ({@link #choose}), or built again from
     * what was saved ({@link #rebuild}). The rooms are as big as {@code sizes} says.
     */
    static final class Planner {
        private final Colony colony;
        private final BlockPos center;
        private final NestPlan.RoomSizes sizes;

        Planner(Colony colony, BlockPos center, NestPlan.RoomSizes sizes) {
            this.colony = colony;
            this.center = center;
            this.sizes = sizes;
        }

        /** The layout saved with a nest: its depth, direction, how many levels and whether it has a founding chamber. */
        NestLayout rebuild(int depth, Direction direction, int levels, boolean withFounding) {
            NestPlan.Chamber founding = withFounding ? NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction, sizes) : null;
            List<NestPlan.Chamber> chambers = NestPlan.chambers(center.below(depth), direction, levels, sizes);
            return new NestLayout(chambers, sideRooms(center.below(depth), direction), gardens(chambers, direction), depth, direction, founding, sizes);
        }

        /**
         * The garden rooms of a nest with these levels (species with a {@code garden} in their profile, whose nest has one
         * level: the royal chamber), as many as the profile allows; none otherwise.
         */
        private List<NestPlan.Chamber> gardens(List<NestPlan.Chamber> chambers, Direction direction) {
            var garden = colony.profile().garden();
            if (garden.isEmpty() || chambers.size() != 1 || chambers.get(0).kind() != NestPlan.Kind.QUEEN) {
                return List.of();
            }
            return NestPlan.gardens(chambers.get(0), direction, garden.get().rooms());
        }

        /** The side rooms of a nest whose shaft ends at {@code bottom} and whose rooms lie toward {@code direction} (from the species profile). */
        private List<NestPlan.Chamber> sideRooms(BlockPos bottom, Direction direction) {
            List<NestPlan.Kind> kinds = new ArrayList<>();
            for (String name : colony.profile().nest().sideRooms()) {
                try {
                    kinds.add(NestPlan.Kind.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    AntColoniesMod.LOGGER.warn("Unknown side room kind '{}' in the species profile of {}", name, colony.getSpecies());
                }
            }
            return NestPlan.sideRooms(bottom, direction, kinds, sizes);
        }

        /**
         * Picks how deep the nest sits, which side the rooms lie toward and how many levels it has, by looking at the
         * ground: a depth only counts if the whole shaft is soil, and a nest only counts if every stair, tunnel and
         * room of its plan is soil (no stone or water in the way). The nest with the most levels wins, the deeper one on
         * a tie. Null while chunks are missing, or when there is no room at all.
         */
        @Nullable
        NestLayout choose(Level level) {
            // Everything the plan could touch must be loaded, or the answer would change later.
            for (int depth = Nest.SHAFT_DEPTH; depth >= Nest.MIN_SHAFT_DEPTH; depth--) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    List<NestPlan.Chamber> all = new ArrayList<>(NestPlan.chambers(center.below(depth), direction, NestPlan.MAX_LEVELS, sizes));
                    all.add(NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction, sizes));
                    all.addAll(sideRooms(center.below(depth), direction));
                    for (NestPlan.Chamber chamber : all) {
                        for (BlockPos pos : chamber.cells()) {
                            if (!level.isLoaded(pos)) {
                                return null;
                            }
                        }
                    }
                }
            }
            NestLayout best = null;
            for (int depth = Nest.SHAFT_DEPTH; depth >= Nest.MIN_SHAFT_DEPTH; depth--) {
                NestLayout found = choose(level, center.below(depth), depth);
                // The depth with the most levels wins, then the one where the side rooms fit too; on a tie the deeper one (it is tried first).
                if (found != null && (best == null || found.chambers().size() > best.chambers().size()
                        || found.chambers().size() == best.chambers().size() && sideRoomsFit(level, found) && !sideRoomsFit(level, best))) {
                    best = found;
                }
            }
            return best;
        }

        /** Whether the side rooms of this layout are all soil (or already dug). A nest is still planned where they don't fit; they are then dug as far as the soil goes. */
        private static boolean sideRoomsFit(Level level, NestLayout layout) {
            for (NestPlan.Chamber chamber : layout.sideRooms()) {
                if (!allSoilOrAir(level, chamber.cells())) {
                    return false;
                }
            }
            return true;
        }

        /** The best layout with the shaft {@code depth} blocks deep (all the chunks involved are loaded), or null if the shaft or the first level does not fit. */
        @Nullable
        private NestLayout choose(Level level, BlockPos bottom, int depth) {
            List<BlockPos> shaft = new ArrayList<>();
            for (int i = 1; i <= depth; i++) {
                shaft.add(center.below(i));
            }
            if (!allSoilOrAir(level, shaft)) {
                return null; // stone in the way of the shaft itself
            }
            int start = Math.floorMod(colony.getId().hashCode(), 4);
            boolean withFounding = colony.profile().nest().foundingChamber();
            NestLayout best = null;
            boolean bestSideFits = false;
            for (int i = 0; i < 4; i++) {
                Direction direction = Direction.from2DDataValue((start + i) % 4);
                // The founding chamber (the queen's first home, on the far side of the shaft) has to fit too.
                NestPlan.Chamber founding = withFounding ? NestPlan.foundingChamber(center.below(NestPlan.FOUNDING_DEPTH), direction, sizes) : null;
                if (founding != null && (!allSoilOrAir(level, founding.cells()) || !isCovered(level, founding.cells(), shaft))) {
                    continue;
                }
                // Side rooms (the termites' food store) don't have to fit, but a direction where they do is better.
                List<NestPlan.Chamber> side = sideRooms(bottom, direction);
                boolean sideFits = true;
                for (NestPlan.Chamber chamber : side) {
                    sideFits &= allSoilOrAir(level, chamber.cells());
                }
                // The most levels whose whole plan is soil. A shorter nest uses its levels differently (and its last one is
                // always the small queen's room), so each length is checked as the plan it really is.
                int maxLevels = Math.min(NestPlan.MAX_LEVELS, Math.max(1, colony.profile().nest().maxLevels())); // from the species profile
                for (int levels = maxLevels; levels >= 1; levels--) {
                    List<NestPlan.Chamber> plan = NestPlan.chambers(bottom, direction, levels, sizes);
                    if (best != null && (levels < best.chambers().size() || levels == best.chambers().size() && (bestSideFits || !sideFits))) {
                        break; // not better than what is already found
                    }
                    boolean fits = true;
                    for (NestPlan.Chamber chamber : plan) {
                        if (!allSoilOrAir(level, chamber.cells())) {
                            fits = false;
                            break;
                        }
                    }
                    if (fits) {
                        best = new NestLayout(plan, side, gardens(plan, direction), depth, direction, founding, sizes);
                        bestSideFits = sideFits;
                        break;
                    }
                }
            }
            return best;
        }

        /**
         * Whether these cells lie under cover: no block beside them (but the cells themselves and the shaft) is open air or
         * a plant under the sky, or water. On a hillside the queen's chamber must not run out under the slope with its roof or a wall open to
         * the outside (the hill test of 2026-10-14: the side the plan picked changed with each new colony).
         */
        private static boolean isCovered(Level level, List<BlockPos> cells, List<BlockPos> shaft) {
            Set<BlockPos> own = new HashSet<>(cells);
            own.addAll(shaft);
            for (BlockPos pos : cells) {
                for (Direction side : Direction.values()) {
                    BlockPos next = pos.relative(side);
                    if (own.contains(next)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    // Open to the sky (rooms of the nest dug beside it later are underground and fine), or water.
                    if (!state.getFluidState().isEmpty() || (state.isAir() || state.canBeReplaced()) && level.canSeeSky(next)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Soil, or already dug out (a heap of seeds or bread on the floor counts as dug: in an old save the plan is worked out again on a nest in use). */
        private static boolean allSoilOrAir(Level level, List<BlockPos> cells) {
            for (BlockPos pos : cells) {
                BlockState state = level.getBlockState(pos);
                if (!(state.is(ModTags.DIGGABLE) || Nest.isDugOut(level, pos)) || Nest.touchesFluid(level, pos)) {
                    return false;
                }
            }
            return true;
        }
    }
}
