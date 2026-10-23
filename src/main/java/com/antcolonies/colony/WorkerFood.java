package com.antcolonies.colony;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.registry.ModItems;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfile.MealFood;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Where the workers' own meals come from, for the real ones ({@code entity.goal.EatGoal}) and the numbers
 * ({@link Meals}) alike (species-neutral, from the profile's {@code worker.diet}). Real Macrotermes: the young workers
 * eat the fungus nodules, the older ones the old comb under the fresh (the bed of each comb block stays, for the
 * fungus); the harvester ant: every worker a seed from the store. Where the diet has two kinds, a worker whose own
 * food is not there eats the other, as real workers do when food is short. One piece makes {@code workers_per_piece} meals: the meals still left in
 * a piece one worker started are kept here and eaten first (so a layer of comb feeds 4 termites).
 */
public class WorkerFood {
    private final Colony colony;
    /** Meals still left in a piece already started, by kind of food. Saved. */
    private final int[] mealsLeft = new int[MealFood.values().length];
    /** How many pieces of each kind the workers have started on since the colony was loaded. Not saved: for tests. */
    private final int[] piecesEaten = new int[MealFood.values().length];

    /** A meal a real worker goes for: where, and what. */
    public record Meal(BlockPos spot, MealFood food) {
    }

    WorkerFood(Colony colony) {
        this.colony = colony;
    }

    private SpeciesProfile.WorkerDiet diet() {
        return colony.profile().worker().diet();
    }

    /**
     * Whether the workers' hunger clocks run now: the colony's hunger is on, its workers eat, and (for a species whose
     * workers only get hungry once there is food, the termites) its store has held its first piece.
     */
    public boolean hungerRuns() {
        return colony.isHungerOn() && colony.profile().worker().eats() && (!diet().hungryFromFirstFood() || colony.food().hasHadFood());
    }

    /** What a worker of this age (ticks since it grew up) usually eats. */
    public MealFood usualFood(long age) {
        return diet().forRole(colony.profile().worker().roleForAge(age));
    }

    /** A meal for this real worker: its usual food first, the other kind if there is none; null if there is nothing. */
    @Nullable
    public Meal findMeal(ServerLevel level, AbstractAntEntity worker, RandomSource random) {
        for (MealFood food : foodsFor(usualFood(worker.getAgeTicks()))) {
            BlockPos spot = spotFor(level, food, random);
            if (spot != null) {
                return new Meal(spot, food);
            }
        }
        return null;
    }

    /**
     * What a worker whose usual food is {@code usual} eats, in order: its own, then the other kind if the diet has both
     * (a harvester ant only ever eats seeds, never the queen's bread).
     */
    private MealFood[] foodsFor(MealFood usual) {
        SpeciesProfile.WorkerDiet diet = diet();
        return diet.young() == diet.older() ? new MealFood[] {usual} : new MealFood[] {usual, usual.other()};
    }

    @Nullable
    private BlockPos spotFor(ServerLevel level, MealFood food, RandomSource random) {
        return food == MealFood.STORE ? colony.food().findMealPile(level, random) : colony.nurseFood().findFood(level, random);
    }

    /**
     * The real worker eats at its meal's spot: from a piece already started if there is one (no block changes), else it
     * takes a new piece off the block there. Returns what it eats (to show in its jaws), or an empty stack if nothing is
     * there any more.
     */
    public ItemStack eatAt(ServerLevel level, AbstractAntEntity worker, Meal meal) {
        int kind = meal.food().ordinal();
        if (mealsLeft[kind] > 0) {
            mealsLeft[kind]--;
            return look(meal.food());
        }
        net.minecraft.world.level.block.state.BlockState before = level.getBlockState(meal.spot());
        ItemStack piece = meal.food() == MealFood.STORE ? colony.food().eatSeedAt(level, meal.spot(), worker.getUUID())
                : colony.nurseFood().takeFoodAt(level, meal.spot(), false);
        if (!piece.isEmpty() && meal.food() == MealFood.STORE && com.antcolonies.block.GardenBed.isBed(before)) {
            // Old comb is dry and crumbly: crumbs fall where it is chewed off.
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, before),
                    meal.spot().getX() + 0.5, meal.spot().getY() + 0.05, meal.spot().getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.0);
        }
        if (!piece.isEmpty()) {
            mealsLeft[kind] = Math.max(1, diet().workersPerPiece()) - 1;
            piecesEaten[kind]++;
        }
        return piece;
    }

    /** What a meal from a piece already started looks like in the jaws. */
    private ItemStack look(MealFood food) {
        if (food == MealFood.STORE) {
            return colony.food().cutPiece();
        }
        NurseFood nurseFood = colony.nurseFood();
        return nurseFood == null ? new ItemStack(ModItems.ANT_BREAD.get()) : nurseFood.piece();
    }

    /**
     * One of the worker numbers eats ({@code food}: its usual kind, the other if there is none): from a piece already
     * started, else a counted piece whose block comes off later, out of sight ({@link NestLedger}). False if there is no
     * food of either kind.
     */
    boolean eatForNumbers(ServerLevel level, MealFood food) {
        for (MealFood kind : foodsFor(food)) {
            if (mealsLeft[kind.ordinal()] > 0) {
                mealsLeft[kind.ordinal()]--;
                return true;
            }
            boolean took = kind == MealFood.STORE ? oldCombKnown() && colony.food().eatForNumbers() : colony.life().takeNurseFood(level);
            if (took) {
                mealsLeft[kind.ordinal()] = Math.max(1, diet().workersPerPiece()) - 1;
                piecesEaten[kind.ordinal()]++;
                return true;
            }
        }
        return false;
    }

    /** How many pieces of this kind the workers (real and numbers) have started on since the colony was loaded (for tests). */
    public int getPiecesEaten(MealFood food) {
        return piecesEaten[food.ordinal()];
    }

    /** Whether the store has a piece the workers eat: any seed; for fungus comb, an old layer as far as the brain knows. */
    private boolean oldCombKnown() {
        return !colony.questions().ask(null, ColonyQuestions.NOTHING_TO_EAT_IN_STORE, null);
    }

    /** Tests only: a sample in every saved number (the save and load check, {@code AwayTests.everyPartSavesAndLoads}). */
    public void fillForTests(ServerLevel level) {
        for (MealFood food : MealFood.values()) {
            mealsLeft[food.ordinal()] = food.ordinal() + 2;
        }
    }

    void save(CompoundTag tag) {
        CompoundTag left = new CompoundTag();
        for (MealFood food : MealFood.values()) {
            left.putInt(food.name().toLowerCase(java.util.Locale.ROOT), mealsLeft[food.ordinal()]);
        }
        tag.put("workerMealsLeft", left);
    }

    void load(CompoundTag tag) {
        CompoundTag left = tag.getCompound("workerMealsLeft");
        for (MealFood food : MealFood.values()) {
            mealsLeft[food.ordinal()] = left.getInt(food.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
