package com.antcolonies.colony;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * What the nest's blocks still owe the colony's counts (the brain, part 2, step 2b). The counts are the truth: when the
 * numbers inside the nest eat a seed, mill bread, feed the queen or a nodule grows (while nobody can see, or while the
 * colony is away and its land is not even loaded), the count changes at once and the block is changed here later, once
 * the whole nest is loaded and no player can see that spot. So the heaps, the comb and the nodules shrink and grow only
 * out of sight, and never in unloaded land.
 *
 * <p>Species-neutral: the food store (seed heaps or fungus comb), the nurses' food (bread heaps or fungus nodules, see
 * {@link NurseFood}) and the bodies of the dead.
 */
public class NestLedger {
    /** At most this many changes of one kind a second (a long time away can owe a lot; the rest comes the next second). */
    private static final int PER_SECOND = 200;

    private final Colony colony;
    /** Layers of the food store still to put down (above 0) or take away (below 0). Saved. */
    private int store;
    /** Pieces of the nurses' food still to put down (above 0) or take away (below 0). Saved. */
    private int nurseFood;
    /** Bodies still to lay in the refuse chamber (termites eat theirs). Saved. */
    private int bodies;

    NestLedger(Colony colony) {
        this.colony = colony;
    }

    void addStore(int layers) {
        store += layers;
    }

    void addNurseFood(int pieces) {
        nurseFood += pieces;
    }

    void addBodies(int count) {
        bodies += count;
    }

    /** Layers of the food store owed: above 0 to put down, below 0 to take away. */
    public int store() {
        return store;
    }

    /** Pieces of the nurses' food owed: above 0 to put down, below 0 to take away. */
    public int nurseFood() {
        return nurseFood;
    }

    public int bodies() {
        return bodies;
    }

    public boolean isEmpty() {
        return store == 0 && nurseFood == 0 && bodies == 0;
    }

    /**
     * Once a second while the whole nest is loaded: as much as can be done out of every player's sight ({@code eyes}).
     * What has nowhere to go is dropped (a store full or not dug: the seed was eaten or put somewhere else, and taken out
     * of the count); what there is nothing left to take off is dropped too (it was taken already). True if anything changed.
     */
    boolean apply(ServerLevel level, List<Vec3> eyes) {
        boolean changed = applyBodies(level, eyes);
        changed |= applyStore(level, eyes);
        changed |= applyNurseFood(level, eyes);
        return changed;
    }

    private boolean applyBodies(ServerLevel level, List<Vec3> eyes) {
        boolean changed = false;
        for (int tries = 0; bodies > 0 && tries < PER_SECOND; tries++) {
            if (colony.refuse().eatsOrBuries()) {
                bodies = 0; // termites ate their dead in the garden
                return true;
            }
            BlockPos spot = colony.refuse().pickCorpseSpot(level, level.getRandom());
            if (spot == null) {
                bodies = 0; // the refuse chamber is full (or not dug): the rest were carried out of the nest
                return true;
            }
            if (!Population.outOfSight(level, Vec3.atBottomCenterOf(spot).add(0, 0.2, 0), eyes)) {
                break; // a player can see in: later
            }
            if (colony.refuse().placeCorpse(level, spot)) {
                bodies--;
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyStore(ServerLevel level, List<Vec3> eyes) {
        FoodStore food = colony.food();
        boolean changed = false;
        for (int tries = 0; store > 0 && tries < PER_SECOND; tries++) {
            BlockPos spot = food.pickFoodSpot(level, level.getRandom());
            if (spot == null) {
                store--; // the store is full (or not dug): eaten, or put somewhere else
                food.used(1);
                changed = true;
                continue;
            }
            if (!Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                break;
            }
            if (food.addLayerAt(level, spot)) {
                store--;
                changed = true;
            }
        }
        for (int tries = 0; store < 0 && tries < PER_SECOND; tries++) {
            List<BlockPos> heaps = food.layersToTake(level);
            if (heaps.isEmpty()) {
                store = 0; // nothing left to take off: it was taken already
                return true;
            }
            BlockPos heap = heaps.get(level.getRandom().nextInt(heaps.size()));
            if (!Population.outOfSight(level, Vec3.atCenterOf(heap), eyes)) {
                if (heaps.stream().noneMatch(pos -> Population.outOfSight(level, Vec3.atCenterOf(pos), eyes))) {
                    break; // a player can see every heap: later
                }
                continue;
            }
            if (food.removeLayerAt(level, heap)) {
                store++;
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyNurseFood(ServerLevel level, List<Vec3> eyes) {
        NurseFood food = colony.nurseFood();
        boolean changed = false;
        for (int tries = 0; nurseFood > 0 && tries < PER_SECOND; tries++) {
            BlockPos spot = food.pickReturnSpot(level, level.getRandom());
            if (spot == null) {
                nurseFood--; // nowhere to keep it: eaten meanwhile
                food.forget(1);
                changed = true;
                continue;
            }
            if (!Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                break;
            }
            if (food.addPieceAt(level, spot)) {
                nurseFood--;
                changed = true;
            }
        }
        for (int tries = 0; nurseFood < 0 && tries < PER_SECOND; tries++) {
            BlockPos spot = food.findFood(level, level.getRandom());
            if (spot == null) {
                nurseFood = 0; // nothing left to take: it was taken already
                return true;
            }
            if (!Population.outOfSight(level, Vec3.atCenterOf(spot), eyes)) {
                continue; // (another heap may be out of sight; the rest later)
            }
            if (food.removePieceAt(level, spot)) {
                nurseFood++;
                changed = true;
            }
        }
        return changed;
    }

    /** For the colony list: "2 bodies, -3 food layers, 1 nurse food" (empty if nothing is owed). */
    public String describe() {
        List<String> owed = new java.util.ArrayList<>();
        if (bodies > 0) {
            owed.add(bodies + " bodies");
        }
        if (store != 0) {
            owed.add((store > 0 ? "+" : "") + store + " food");
        }
        if (nurseFood != 0) {
            owed.add((nurseFood > 0 ? "+" : "") + nurseFood + " nurse food");
        }
        return String.join(", ", owed);
    }

    void save(CompoundTag tag) {
        CompoundTag ledger = new CompoundTag();
        ledger.putInt("store", store);
        ledger.putInt("nurseFood", nurseFood);
        ledger.putInt("bodies", bodies);
        tag.put("ledger", ledger);
    }

    void load(CompoundTag tag) {
        CompoundTag ledger = tag.getCompound("ledger");
        store = ledger.getInt("store");
        nurseFood = ledger.getInt("nurseFood");
        bodies = ledger.getInt("bodies");
        if (tag.contains("away")) {
            // An older save (step 2a) kept what was owed with the going away; those seeds and bread were not counted yet.
            CompoundTag away = tag.getCompound("away");
            bodies += away.getInt("bodies");
            int seeds = away.getInt("seeds");
            int richSeeds = away.getInt("richSeeds");
            int bread = away.getInt("nurseFood");
            int richBread = away.getInt("richNurseFood");
            store += seeds + richSeeds;
            nurseFood += bread + richBread;
            for (int i = 0; i < seeds + richSeeds; i++) {
                colony.food().countIn(i < richSeeds);
            }
            for (int i = 0; colony.profile().food().makesBread() && i < bread + richBread; i++) {
                colony.bakery().countIn(i < richBread);
            }
        }
    }
}
