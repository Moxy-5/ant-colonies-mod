package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.block.GardenBed;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.ColonyBorn;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.SpeciesBodies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The colony's young: eggs (laid by the queen, carried to the nursery, hatching into larvae) and larvae (fed bread by
 * the nurses, growing into workers, starving without food). The eggs and larvae are real entities in the world; this
 * finds them, picks spots for them and keeps track of who is carrying or feeding which.
 */
public class Brood {
    // How long an egg takes to hatch, how much a larva eats, how long it lasts unfed and how many young there may be
    // come from the species profile: colony.profile().brood().

    /** How long (in ticks) a nurse on its way to a larva keeps the other nurses away from it. */
    public static final int LARVA_CLAIM_TICKS = 600;
    /** How long (in ticks) a worker on its way to an egg keeps the others away from it. */
    private static final int EGG_CLAIM_TICKS = 600;

    private final Colony colony;
    private final UUID id;

    /** The eggs a worker is on its way to: egg -> game time when the claim runs out. Not saved. */
    private final Claims<UUID> claimedEggs = new Claims<>();
    /**
     * Young that are numbers, not real entities: eggs and larvae that were frozen while the colony was away (see
     * {@link AwayLife}). They come back into the nursery as real ones once the nest is loaded and nobody can see. Saved.
     */
    private final List<Young> inside = new ArrayList<>();

    /**
     * One young as a number. An egg: {@code left} = ticks until it hatches ({@code -1}: not put in its place yet, the
     * clock starts in the nursery). A larva (or nymph): {@code fed} pieces eaten so far, {@code left} = how long it had
     * gone hungry, and the caste it grows into besides a worker (a soldier; null: a worker). Their clocks go on while they
     * are numbers (step 2b, {@link NestLife}): eggs hatch, larvae are fed by the nurses that are numbers, grow up or starve.
     */
    public record Young(boolean isEgg, long left, int fed, @Nullable String growsInto) {
        public static Young egg(long hatchLeft) {
            return new Young(true, hatchLeft, 0, null);
        }

        public static Young larva(int fed, long hungry, @Nullable String growsInto) {
            return new Young(false, hungry, fed, growsInto);
        }
    }

    Brood(Colony colony) {
        this.colony = colony;
        this.id = colony.getId();
    }

    /** Puts an egg of this colony into the world. */
    public AntEggEntity spawnEgg(Level level, double x, double y, double z) {
        AntEggEntity egg = new AntEggEntity(ModEntities.ANT_EGG.get(), level);
        egg.setColonyId(id);
        egg.moveTo(x, y, z, colony.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(egg);
        return egg;
    }

    /** All the eggs of this colony around the nest: in the nursery, lying where the queen laid them, or dropped on the way. */
    public List<AntEggEntity> getEggs(Level level) {
        return level.getEntitiesOfClass(AntEggEntity.class, colony.nestArea(), egg -> egg.isAlive() && id.equals(egg.getColonyId()));
    }

    public int countEggs(Level level) {
        return getEggs(level).size();
    }

    /** The eggs lying in the nursery. */
    public List<AntEggEntity> getNurseryEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(colony.nest().getNurseryCells(level));
        return getEggs(level).stream().filter(egg -> cells.contains(egg.blockPosition())).toList();
    }

    /** The eggs that no worker has put in its place yet (not in the nursery or a garden, and not set down by a worker). */
    public List<AntEggEntity> getLooseEggs(Level level) {
        Set<BlockPos> cells = new HashSet<>(colony.nest().getNurseryCells(level));
        cells.addAll(colony.nest().getGardenCells(level));
        return getEggs(level).stream().filter(egg -> !egg.isPlaced() && !cells.contains(egg.blockPosition())).toList();
    }

    /** Whether this block is a floor cell of the nursery or a garden room (so an egg lying there is being kept and will hatch). */
    public boolean isInNursery(Level level, BlockPos pos) {
        return colony.nest().getNurseryCells(level).contains(pos) || colony.nest().getGardenCells(level).contains(pos);
    }

    /**
     * How much a larva (or nymph) growing into this caste (null: a worker) eats before it grows up: the profile's larva
     * meal, or the bigger meal of the soldier it is growing into (in a colony with soldiers, see {@link Soldiers#mealFor}).
     */
    public int mealFor(@Nullable String growsInto) {
        ColonyHooks.Castes castes = colony.hooks().first(ColonyHooks.CASTES);
        return castes == null ? colony.profile().brood().larvaMeal() : castes.mealFor(growsInto);
    }

    /** The caste a new nymph should grow into besides a worker, or null: soldiers enough, or a species without them (see {@link Soldiers#rankForNewNymph}). */
    @Nullable
    public String soldierWanted(Level level) {
        ColonyHooks.Castes castes = colony.hooks().first(ColonyHooks.CASTES);
        return castes == null ? null : castes.wanted(level);
    }

    /** What an egg of this colony hatches into (an ant larva, a termite nymph: the species' bodies, {@link SpeciesBodies}). */
    private EntityType<AntLarvaEntity> youngType() {
        return SpeciesBodies.of(colony.getSpecies()).young().get();
    }

    /**
     * The egg hatches: a larva (or nymph) takes its place (in the same spot). A termite colony short of soldiers makes
     * the nymph one that grows into a soldier (see {@link Soldiers#rankForNewNymph}).
     */
    public boolean hatchEgg(ServerLevel level, AntEggEntity egg) {
        String growsInto = soldierWanted(level);
        AntLarvaEntity larva = new AntLarvaEntity(youngType(), level);
        larva.setColonyId(id);
        larva.setGrowsInto(growsInto);
        larva.setMeal(mealFor(growsInto));
        larva.moveTo(egg.getX(), egg.getY(), egg.getZ(), egg.getYRot(), 0.0F);
        level.addFreshEntity(larva);
        egg.discard();
        return true;
    }

    /** Puts a larva of this colony into the world (used by the tests). */
    public AntLarvaEntity spawnLarva(Level level, double x, double y, double z) {
        AntLarvaEntity larva = new AntLarvaEntity(youngType(), level);
        larva.setColonyId(id);
        larva.setMeal(mealFor(null));
        larva.moveTo(x, y, z, colony.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(larva);
        return larva;
    }

    /** All the larvae of this colony around the nest. */
    public List<AntLarvaEntity> getLarvae(Level level) {
        return level.getEntitiesOfClass(AntLarvaEntity.class, colony.nestArea(), larva -> larva.isAlive() && id.equals(larva.getColonyId()));
    }

    public int countLarvae(Level level) {
        return getLarvae(level).size();
    }

    /** Eggs and larvae together (the real ones and the ones that are numbers). */
    public int countBrood(Level level) {
        return countEggs(level) + countLarvae(level) + inside.size();
    }

    // --- young as numbers (the brain, part 2) ----------------------------------------------------------------------

    /** A young one becomes a number (it was frozen while the colony went away, or laid while away). */
    void addInside(Young young) {
        inside.add(young);
    }

    /** Tests only: a young one that is a number, as if it had been frozen. */
    public void addInsideForTests(Young young) {
        addInside(young);
    }

    /** Tests only: the young that are numbers now (a copy). */
    public List<Young> youngInsideForTests() {
        return List.copyOf(inside);
    }

    /** How many young are numbers now. */
    public int insideCount() {
        return inside.size();
    }

    /** The young that are numbers (the list itself: {@link NestLife} moves their clocks on). */
    List<Young> young() {
        return inside;
    }

    /** How many of the larvae (nymphs) that are numbers grow into this caste (a soldier; null: a worker). */
    int insideGrowingInto(@Nullable String caste) {
        int count = 0;
        for (Young young : inside) {
            if (!young.isEgg() && java.util.Objects.equals(young.growsInto(), caste)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The larva that is a number and has eaten least so far, and is not full yet (index in {@link #young}), or -1: the
     * hungriest is fed first.
     */
    int hungriestInside() {
        int best = -1;
        for (int i = 0; i < inside.size(); i++) {
            Young young = inside.get(i);
            if (young.isEgg() || young.fed() >= mealFor(young.growsInto())) {
                continue;
            }
            if (best < 0 || young.fed() < inside.get(best).fed()
                    || young.fed() == inside.get(best).fed() && young.left() > inside.get(best).left()) {
                best = i;
            }
        }
        return best;
    }

    /** The larva that is a number at this index gets a piece of food: its hunger clock starts over. */
    void feedInside(int index) {
        Young young = inside.get(index);
        inside.set(index, Young.larva(young.fed() + 1, 0, young.growsInto()));
    }

    /**
     * The young that are numbers come back as real eggs and larvae, each on a free spot of the nursery (or a garden, or
     * the queen's room) that no player can see, the clocks going on from where they stopped. Those with no such spot yet
     * wait. True if any came back.
     */
    boolean comeOut(ServerLevel level, List<net.minecraft.world.phys.Vec3> eyes) {
        boolean changed = false;
        for (java.util.Iterator<Young> it = inside.iterator(); it.hasNext(); ) {
            Young young = it.next();
            BlockPos spot = pickEggSpot(level, colony.random());
            if (spot == null) {
                break; // no room: later
            }
            double height = restingHeight(level, spot);
            net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(spot).add(0, Math.max(0, height), 0);
            if (height < 0 || !Population.outOfSight(level, at.add(0, 0.1, 0), eyes)) {
                continue; // a player can see that spot (or something lies there): try the next one, the rest later
            }
            long now = level.getGameTime();
            if (young.isEgg()) {
                AntEggEntity egg = spawnEgg(level, at.x, at.y, at.z);
                egg.setPlaced(true);
                egg.setHatchAt(now + (young.left() < 0 ? colony.profile().brood().hatchTicks() : young.left()));
            } else {
                AntLarvaEntity larva = spawnLarva(level, at.x, at.y, at.z);
                larva.setGrowsInto(young.growsInto());
                larva.setMeal(mealFor(young.growsInto()));
                larva.setFed(young.fed());
                larva.setLastFed(now - young.left());
            }
            it.remove();
            changed = true;
        }
        return changed;
    }

    void save(net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Young young : inside) {
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putBoolean("egg", young.isEgg());
            entry.putLong("left", young.left());
            entry.putInt("fed", young.fed());
            if (young.growsInto() != null) {
                entry.putString("rank", savedAs(colony, young.growsInto()));
            }
            list.add(entry);
        }
        tag.put("youngInside", list);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        inside.clear();
        net.minecraft.nbt.ListTag list = tag.getList("youngInside", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag entry = list.getCompound(i);
            String growsInto = entry.contains("rank") ? fromSaved(colony, entry.getString("rank")) : null;
            inside.add(new Young(entry.getBoolean("egg"), entry.getLong("left"), entry.getInt("fed"), growsInto));
        }
    }

    /** The word a young one's caste is saved under (the soldiers' "MINOR", "MAJOR", as before step 9c: old saves load). */
    public static String savedAs(Colony colony, String caste) {
        for (ColonyHooks.Castes castes : colony.hooks().all(ColonyHooks.CASTES)) {
            String word = castes.savedAs(caste);
            if (word != null) {
                return word;
            }
        }
        throw new IllegalStateException("no caste " + caste + " in " + colony.getSpecies());
    }

    /** The caste saved under this word ({@link #savedAs}). */
    public static String fromSaved(Colony colony, String word) {
        for (ColonyHooks.Castes castes : colony.hooks().all(ColonyHooks.CASTES)) {
            String caste = castes.fromSaved(word);
            if (caste != null) {
                return caste;
            }
        }
        throw new IllegalArgumentException("no caste saved as " + word + " in " + colony.getSpecies());
    }

    /** Whether there are as many young (eggs and larvae, in the nursery or lying about) as there may be: the queen is not fed then. */
    public boolean isFull(Level level) {
        return countBrood(level) >= colony.profile().brood().maxYoung();
    }

    /** The cells that eggs and larvae lie in (nothing else is put there). */
    public Set<BlockPos> getBroodCells(Level level) {
        Set<BlockPos> cells = new HashSet<>();
        for (AntEggEntity egg : getEggs(level)) {
            cells.add(egg.blockPosition());
        }
        for (AntLarvaEntity larva : getLarvae(level)) {
            cells.add(larva.blockPosition());
        }
        return cells;
    }

    /** How many more pieces of bread all the larvae together (the real ones and the numbers) still have to eat. */
    public int larvaHunger(Level level) {
        int hunger = 0;
        for (AntLarvaEntity larva : getLarvae(level)) {
            hunger += Math.max(0, larva.getMeal() - larva.getFed());
        }
        for (Young young : inside) {
            if (!young.isEgg()) {
                hunger += Math.max(0, mealFor(young.growsInto()) - young.fed());
            }
        }
        return hunger;
    }

    /**
     * The larva has eaten enough: a new worker (or the soldier it was growing into) takes its place and joins the
     * colony (as one of the numbers inside the nest, see {@link Population#hatchesIntoNumbers}, when no more real ones are
     * wanted and nobody can see it). False (the larva stays) while the colony has as many workers as it may (the profile's max_workers); a
     * termite nymph waiting for that turns into one growing into a soldier if the colony is short of soldiers (it then
     * eats the soldier's bigger meal first). Soldiers are not held back by the workers' cap.
     */
    public boolean hatchLarva(ServerLevel level, AntLarvaEntity larva) {
        String growsInto = larva.getGrowsInto();
        if (growsInto != null) {
            return hatchSoldier(level, larva, growsInto);
        }
        if (colony.getCount(Caste.WORKER) >= colony.profile().colony().maxWorkers()) {
            String wanted = soldierWanted(level);
            if (wanted != null) {
                larva.setGrowsInto(wanted);
                larva.setMeal(mealFor(wanted));
            }
            return false;
        }
        if (colony.population().hatchesIntoNumbers(level, Caste.WORKER, larva.position())) {
            // The colony has all the real workers it wants and nobody sees it: the new worker is one of the numbers.
            colony.population().addNewMembers(Caste.WORKER, level.getGameTime(), 1);
            larva.discard();
            return true;
        }
        return growUp(level, larva, Caste.WORKER); // a young worker: a nurse first
    }

    /** The nymph has eaten a soldier's meal: a soldier of this kind takes its place and joins the colony. */
    private boolean hatchSoldier(ServerLevel level, AntLarvaEntity larva, String caste) {
        if (colony.population().hatchesIntoNumbers(level, caste, larva.position())) {
            colony.population().addNewMembers(caste, level.getGameTime(), 1);
            larva.discard();
            return true;
        }
        return growUp(level, larva, caste);
    }

    /** The real mob of this caste (the species' body, {@link SpeciesBodies}) takes the young one's place and joins the colony. */
    private boolean growUp(ServerLevel level, AntLarvaEntity larva, String caste) {
        EntityType<? extends AbstractAntEntity> type = SpeciesBodies.of(colony.getSpecies()).typeFor(caste);
        AbstractAntEntity grown = type == null ? null : type.create(level);
        if (!(grown instanceof ColonyBorn born)) {
            return false;
        }
        grown.moveTo(larva.getX(), larva.getY(), larva.getZ(), larva.getYRot(), 0.0F);
        born.joinColony(colony);
        grown.setGrownUpAt(level.getGameTime());
        born.lastMealAsYoung(level.getGameTime()); // (a soldier just had its last meal as a nymph)
        level.addFreshEntity(grown);
        larva.discard();
        return true;
    }

    /** A larva has starved: it dies and leaves a body, which the workers carry to the refuse chamber. */
    public void starveLarva(ServerLevel level, AntLarvaEntity larva) {
        colony.refuse().spawnCorpse(level, larva.getX(), larva.getY(), larva.getZ());
        level.sendParticles(ParticleTypes.SMOKE, larva.getX(), larva.getY() + 0.1, larva.getZ(), 4, 0.05, 0.03, 0.05, 0.01);
        larva.discard();
    }

    /** A free spot for an egg (one of {@link #eggSpots}, at random), or null if there is none. */
    @Nullable
    public BlockPos pickEggSpot(Level level, RandomSource random) {
        List<BlockPos> spots = eggSpots(level);
        return spots.isEmpty() ? null : spots.get(random.nextInt(spots.size()));
    }

    /**
     * The free spots for an egg, empty if there is none: in the nursery if any of it is dug out, then in a garden room
     * (termites: on the fungus comb, or on the floor beside it), otherwise (or when those are full) on the floor of the
     * queen's room. The nursery and the gardens are the preferred places, not a must.
     */
    public List<BlockPos> eggSpots(Level level) {
        Set<BlockPos> taken = getBroodCells(level);
        Nest nest = colony.nest();
        for (List<BlockPos> cells : List.of(nest.getNurseryCells(level), nest.getGardenCells(level), nest.getHomeRoomCells(level))) {
            List<BlockPos> free = new ArrayList<>();
            List<BlockPos> onComb = new ArrayList<>();
            for (BlockPos pos : cells) {
                if (taken.contains(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    free.add(pos);
                } else if (GardenBed.isBed(state)) {
                    onComb.add(pos);
                }
            }
            // On the comb first, where the nymph will find its food.
            List<BlockPos> from = !onComb.isEmpty() ? onComb : free;
            if (!from.isEmpty()) {
                return from;
            }
        }
        return List.of();
    }

    /**
     * How high over the bottom of this cell an egg or a young one lies: on top of a garden bed (a fungus comb), otherwise
     * on the floor.
     * -1 if nothing can be put there (a block in the way).
     */
    public static double restingHeight(Level level, BlockPos spot) {
        BlockState state = level.getBlockState(spot);
        if (state.isAir()) {
            return 0.0;
        }
        return state.getBlock() instanceof GardenBed bed ? bed.restingHeight(state) : -1.0;
    }

    /** A worker puts its egg down at {@code spot}. False if another egg lies there (the worker looks for another spot). */
    public boolean placeEgg(Level level, BlockPos spot) {
        double height = restingHeight(level, spot);
        if (height < 0) {
            return false;
        }
        if (getBroodCells(level).contains(spot)) {
            return false;
        }
        spawnEgg(level, spot.getX() + 0.5, spot.getY() + height, spot.getZ() + 0.5).setPlaced(true);
        return true;
    }

    /**
     * The egg nearest to {@code near} that a worker should carry to its place, or null: only when there is a free spot
     * for it, not one that already lies in its place, not one another worker is on its way to. {@code avoid}: eggs
     * this worker found no way to.
     */
    @Nullable
    public AntEggEntity findLooseEgg(Level level, BlockPos near, Set<UUID> avoid) {
        if (pickEggSpot(level, colony.random()) == null) {
            return null;
        }
        claimedEggs.prune(level.getGameTime());
        AntEggEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AntEggEntity egg : getLooseEggs(level)) {
            if (claimedEggs.contains(egg.getUUID()) || avoid.contains(egg.getUUID())) {
                continue;
            }
            double distance = egg.distanceToSqr(near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5);
            if (distance < bestDistance) {
                best = egg;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** A worker is on its way to this egg: the others leave it alone for a while. */
    public void claimEgg(Level level, AntEggEntity egg) {
        claimedEggs.put(egg.getUUID(), level.getGameTime() + EGG_CLAIM_TICKS);
    }

    public void releaseEgg(AntEggEntity egg) {
        claimedEggs.remove(egg.getUUID());
    }
}
