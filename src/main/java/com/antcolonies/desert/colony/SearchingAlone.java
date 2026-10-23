package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The forager numbers of a species that searches alone (brain step 4c, {@code food.search}; the harvester ant), plugged
 * into {@link FieldLife} by its {@link ForagerSpots} (Project Apex step 8c, moved out of the shared code unchanged).
 */
final class SearchingAlone implements ColonyHooks.NumbersSearch {
    /** What a search of the numbers costs besides the walk out and back (ticks): out of the nest, the search itself, in again (my pick). */
    static final int SEARCH_TICKS = 400;
    /** At most this many searches of the numbers end a second. */
    private static final int SEARCHES_PER_SECOND = 8;

    private final ForagerSpots spots;
    private final Colony colony;
    /** The remembered plants by 8 x 8 column cells, for the searches (built again each second while searching alone). Not saved. */
    private final Map<Long, List<BlockPos>> byCell = new HashMap<>();
    /** A search on its way to its end (a fraction carried over to the next second). Not saved. */
    private double searchProgress;

    SearchingAlone(ForagerSpots spots) {
        this.spots = spots;
        this.colony = spots.colony();
    }

    /**
     * The forager numbers of a species that searches alone ({@code food.search}, see {@link ForagerSpots}): those that
     * hold a spot work the remembered plants near it, by the real foragers' trip rules; a spot with nothing left near it
     * is found empty. The rest search the land as last seen (it is their walk, not the colony's knowledge): a search
     * ends after the walk out to its centre and {@link #SEARCH_TICKS}; a plant with food within {@code wander} of the
     * centre makes a new spot for one of them, nothing there and the next searches start farther out (the drift). While
     * searching they are out and bring nothing home (they come home with food, not empty: the user's decision).
     */
    @Override
    public void forage(ServerLevel level, FieldLife field, int foragers) {
        SpeciesProfile.SearchNumbers search = spots.settings().orElse(null);
        if (search == null) {
            return;
        }
        Map<BlockPos, FieldLife.Plant> known = field.known();
        indexPlants(known);
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        FoodStore food = colony.food();
        List<Vec3> eyes = colony.population().viewersInSight(level);
        double free = field.foragersFree(level, foragers);
        int worked = 0;
        int wander = search.wander();
        List<BlockPos> usedUp = new ArrayList<>();
        for (ForagerSpots.Held spot : new ArrayList<>(spots.held())) {
            if (free <= 0 || field.roomNow() <= 0) {
                break;
            }
            if (colony.questions().ask(level, ColonyQuestions.NOT_CLEARED, spot.pos())) {
                continue; // that way is shut today: its holders stay in
            }
            if (colony.keepsOff(level, spot.pos())) {
                spots.dropHeld(spot); // remembered danger there (brain step 4g): its holders search elsewhere
                continue;
            }
            List<BlockPos> near = new ArrayList<>();
            for (BlockPos pos : plantsNear(spot.pos(), wander)) {
                FieldLife.Plant plant = known.get(pos);
                if (plant != null && plant.kind() >= 0 && plant.kind() < kinds.size() && kinds.get(plant.kind()).pieces() - food.getPiecesCut(pos) > 0) {
                    near.add(pos);
                }
            }
            if (near.isEmpty()) {
                spots.emptyHeld(spot); // nothing left there: its holders search again
                continue;
            }
            near.sort(Comparator.comparingDouble(pos -> -FoodStore.worth(kinds.get(known.get(pos).kind()).pieces() - food.getPiecesCut(pos),
                    known.get(pos).trip(), pos.distSqr(spot.pos()))));
            double spotFree = Math.min(free, spot.holders());
            for (BlockPos pos : near) {
                if (spotFree <= 0 || field.roomNow() <= 0 || worked++ >= FieldLife.PLANTS_PER_SECOND) {
                    break;
                }
                double sent = field.work(level, pos, known.get(pos), spotFree, eyes, usedUp);
                spotFree -= sent;
                free -= sent;
            }
        }
        field.forgetUsedUp(level, usedUp);
        int searchers = (int) Math.floor(Math.min(free, Math.max(0, foragers - spots.holders())));
        if (searchers <= 0) {
            searchProgress = 0;
            return;
        }
        field.sendSearching(searchers); // (out searching: they come home with food, not empty)
        int range = colony.foragingRadius();
        double distance = Math.max(spots.numbersStartOut(), range / 3.0);
        searchProgress += searchers * 20.0 / (SEARCH_TICKS + 2.0 * distance * FieldLife.TICKS_PER_BLOCK);
        for (int searches = 0; searchProgress >= 1 && searches < SEARCHES_PER_SECOND; searches++) {
            searchProgress--;
            searchOnce(level, field, known, search, range);
        }
        searchProgress = Math.min(searchProgress, 1.0);
    }

    /**
     * One search of the numbers ends (see {@link #forage}): out along a trunk trail by strength (one search in 5 a
     * new way, as the real ones); the remembered plant with food nearest its centre within {@code wander} becomes a
     * spot. How far out: anywhere from near the hill (or the trail's end) out to {@code numbersStartOut}, the band the
     * numbers' failed searches have pushed out to. They are many ants searching each their own way, so the band grows a
     * block with each search that finds nothing and shrinks {@code drift_blocks} with each find (my picks): in rich land
     * it stays near home, where food runs out it stretches to the range plus the stretch, and near food is still found.
     */
    private void searchOnce(ServerLevel level, FieldLife field, Map<BlockPos, FieldLife.Plant> known, SpeciesProfile.SearchNumbers search, int range) {
        BlockPos middle = colony.getCenter();
        var random = colony.random();
        TrunkTrails trails = colony.part(DesertParts.TRUNK_TRAILS).orElse(null);
        TrunkTrails.Trail trail = trails != null && trails.keepsTrails() && random.nextInt(5) != 0 ? trails.pickForSearch(level, random) : null;
        double angle;
        double near;
        double far;
        if (trail != null) {
            angle = trail.angle() + (random.nextDouble() - 0.5) * 0.5;
            near = Math.max(6, trails.reachBlocks(trail) - 3);
            far = Math.max(near, trails.reachBlocks(trail));
        } else {
            angle = random.nextDouble() * Math.PI * 2.0;
            near = 6;
            far = Math.max(near, range / 2.0); // the inner half of the range
        }
        int most = range + search.stretch();
        far = Math.min(Math.max(far, spots.numbersStartOut()), most);
        near = Math.min(near, far);
        double distance = near + random.nextDouble() * (far - near);
        BlockPos centre = new BlockPos(middle.getX() + (int) Math.floor(Math.cos(angle) * distance), middle.getY(),
                middle.getZ() + (int) Math.floor(Math.sin(angle) * distance));
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        FoodStore food = colony.food();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : plantsNear(centre, search.wander())) {
            FieldLife.Plant plant = known.get(pos);
            if (plant == null || plant.kind() < 0 || plant.kind() >= kinds.size() || kinds.get(plant.kind()).pieces() - food.getPiecesCut(pos) <= 0
                    || !colony.mayForageAt(level, pos) || !colony.foodMap().mayScout(pos)) {
                continue;
            }
            double away = FieldLife.flat(pos, centre);
            if (away < bestDistance) {
                best = pos;
                bestDistance = away;
            }
        }
        int band = Math.max(spots.numbersStartOut(), range / 2);
        if (best != null) {
            spots.addHeld(best, 1, 0);
            // A rich patch draws more of them out (recruiting, see Recruits), as many as are free to go.
            Recruits recruits = colony.part(DesertParts.RECRUITS).orElse(null);
            int more = recruits == null ? 0 : recruits.numbersCalled(best, rememberedPatch(known, best));
            int free = field.foragersInside(level) - spots.holders();
            if (more > 0 && free > 0) {
                spots.addHeld(best, Math.min(more, free), 0);
            }
            spots.setNumbersStartOut(band - search.driftBlocks()); // a find: the band settles back
        } else {
            spots.setNumbersStartOut(Math.min(band + 1, most)); // nothing: the band reaches a little farther
        }

    }

    /** The food the colony remembers in the patch round {@code at}: the pieces left on remembered plants that count for recruiting. */
    private int rememberedPatch(Map<BlockPos, FieldLife.Plant> known, BlockPos at) {
        SpeciesProfile.RecruitNumbers numbers = colony.part(DesertParts.RECRUITS).flatMap(Recruits::settings).orElse(null);
        if (numbers == null) {
            return 0;
        }
        List<SpeciesProfile.PlantFood> kinds = colony.profile().food().plants();
        int food = 0;
        for (BlockPos pos : plantsNear(at, numbers.patchRadius())) {
            FieldLife.Plant plant = known.get(pos);
            if (plant != null && plant.kind() >= 0 && plant.kind() < kinds.size() && numbers.counts(kinds.get(plant.kind()))) {
                food += Math.max(0, kinds.get(plant.kind()).pieces() - colony.food().getPiecesCut(pos));
            }
        }
        return food;
    }

    /** The remembered plants within {@code reach} blocks (sideways) of {@code at}. */
    private List<BlockPos> plantsNear(BlockPos at, int reach) {
        List<BlockPos> near = new ArrayList<>();
        for (int cx = (at.getX() - reach) >> 3; cx <= (at.getX() + reach) >> 3; cx++) {
            for (int cz = (at.getZ() - reach) >> 3; cz <= (at.getZ() + reach) >> 3; cz++) {
                List<BlockPos> cell = byCell.get(cellKey(cx, cz));
                if (cell == null) {
                    continue;
                }
                for (BlockPos pos : cell) {
                    if (Math.abs(pos.getX() - at.getX()) <= reach && Math.abs(pos.getZ() - at.getZ()) <= reach) {
                        near.add(pos);
                    }
                }
            }
        }
        return near;
    }

    private void indexPlants(Map<BlockPos, FieldLife.Plant> known) {
        byCell.clear();
        for (BlockPos pos : known.keySet()) {
            byCell.computeIfAbsent(cellKey(pos.getX() >> 3, pos.getZ() >> 3), key -> new ArrayList<>()).add(pos);
        }
    }

    private static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
