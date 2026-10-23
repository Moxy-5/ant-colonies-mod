package com.antcolonies.colony;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Where a real forager of a species that searches alone searches this trip (brain step 4c, {@code food.search}; the
 * harvester ant), plugged into {@code ScoutGoal} by its {@link ForagerSpots} (Project Apex step 9a, moved out of the
 * shared goal unchanged): its own spot, or out along a trunk trail, or a new way (after an empty trip, not the way that
 * failed). The goal keeps the walking and the searching round the centre.
 */
final class ForagerSearch implements ColonyHooks.LoneSearch {
    /** Searching alone with trails to walk out on, one search in this many goes a new way instead. My pick. */
    private static final int NEW_WAY = 5;
    /** After an empty trip the next new way is at least this far (radians) from the way that failed: 60 degrees. My pick. */
    private static final double ANOTHER_WAY = Math.PI / 3.0;
    /** A new way's search starts this many blocks or fewer past {@code LEAST_OUT + 2} from the nest's middle. My pick. */
    private static final int NEAR_START = 2;
    /** A search never starts nearer the nest's middle than this (the hill). */
    private static final int LEAST_OUT = 4;

    private final ForagerSpots spots;
    private final Colony colony;

    ForagerSearch(ForagerSpots spots) {
        this.spots = spots;
        this.colony = spots.colony();
    }

    @Override
    public SpeciesProfile.SearchNumbers settings() {
        return spots.settings().orElse(null);
    }

    @Override
    public void emptyAtSpot(UUID ant) {
        spots.emptyAtSpot(ant);
    }

    /**
     * Where a lone searcher searches this trip, or null if no open ground was hit: its own spot, or out along a trunk
     * trail, or a new way (after an empty trip, not the way that failed). A new way's search starts just past the hill (6
     * to 8 blocks from the nest's middle) and drifts outward while it finds nothing, so the ground near the nest keeps
     * being searched.
     */
    @Override
    @Nullable
    public ColonyHooks.SearchPlan plan(ServerLevel level, AbstractAntEntity ant, ColonyHooks.GroundPicker ground) {
        SpeciesProfile.SearchNumbers search = settings();
        if (search == null) {
            return null;
        }
        BlockPos middle = colony.getCenter();
        BlockPos own = spots.spotOf(ant.getUUID());
        if (own != null && level.hasChunkAt(own) && colony.mayForageAt(level, own) && colony.foodMap().mayScout(own)) {
            double angle = Math.atan2(own.getZ() - middle.getZ(), own.getX() - middle.getX());
            double from = Math.max(LEAST_OUT, flat(own));
            return new ColonyHooks.SearchPlan(own, angle, from, Math.max(colony.foragingRadius(), (int) Math.ceil(from)), true);
        }
        int range = colony.foragingRadius();
        // It came home empty last time: another way this time.
        ForagerSpots.Lead failed = spots.leadOf(ant.getUUID());
        TrunkTrails trails = colony.part(DesertParts.TRUNK_TRAILS).orElse(null);
        // A young colony's foragers spread out every way: real Pogonomyrmex trunk trails only form as the colony grows
        // (Gordon), so a small colony's searchers pick a new way each trip instead of following its few trails.
        boolean young = colony.part(DesertParts.DOOR).map(door -> door.isSmallColony(level)).orElse(false);
        for (int tries = 0; tries < 8; tries++) {
            TrunkTrails.Trail trail = !young && trails != null && trails.keepsTrails() && ant.getRandom().nextInt(NEW_WAY) != 0
                    ? trails.pickForSearch(level, ant.getRandom()) : null;
            double base;
            double angle;
            int reachOfWay;
            if (trail != null && failed != null && Math.abs(Mth.wrapDegrees(Math.toDegrees(trail.angle() - failed.angle()))) < Math.toDegrees(ANOTHER_WAY)) {
                trail = null; // the way that failed last time: a new way instead
            }
            if (trail != null) {
                angle = trail.angle() + (ant.getRandom().nextDouble() - 0.5) * 0.5;
                base = trails.reachBlocks(trail) - ant.getRandom().nextInt(4);
                // Out at the trail's tip, fanning out past it (not out to the colony's longest trail).
                reachOfWay = Math.max(colony.profile().food().radius(), trails.reachBlocks(trail));
            } else {
                reachOfWay = range;
                angle = failed == null ? ant.getRandom().nextDouble() * Mth.TWO_PI
                        : failed.angle() + ANOTHER_WAY + ant.getRandom().nextDouble() * (Mth.TWO_PI - 2 * ANOTHER_WAY);
                // Near the nest, then outward as it drifts (the user's decision of 2026-10-16: it used to start anywhere in
                // the inner half of the range, and seeds 6 blocks from the nest lay unfound for a game day).
                base = LEAST_OUT + 2 + ant.getRandom().nextInt(NEAR_START + 1);
            }
            double from = Math.min(Math.max(LEAST_OUT + 2, base), reachOfWay + search.stretch());
            BlockPos cell = ground.cell(angle, from, 2);
            if (cell != null && colony.mayForageAt(level, cell)) {
                return new ColonyHooks.SearchPlan(cell, angle, from, reachOfWay, false);
            }
        }
        return null;
    }

    /** How far (blocks, sideways) a cell is from the nest's middle. */
    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
