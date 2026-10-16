package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.GoInsideGoal;
import com.antcolonies.entity.goal.CarryCorpseGoal;
import com.antcolonies.entity.goal.CarryEggGoal;
import com.antcolonies.entity.goal.CarryPelletGoal;
import com.antcolonies.entity.goal.ClipGrassGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.EatGoal;
import com.antcolonies.entity.goal.FeedLarvaGoal;
import com.antcolonies.entity.goal.FeedQueenGoal;
import com.antcolonies.entity.goal.MillBreadGoal;
import com.antcolonies.entity.goal.GatherSeedGoal;
import com.antcolonies.entity.goal.RepairHillGoal;
import com.antcolonies.entity.goal.ScoutGoal;
import com.antcolonies.entity.goal.SealGoal;
import com.antcolonies.entity.goal.StandByQueenGoal;
import com.antcolonies.entity.goal.StoreSeedGoal;
import com.antcolonies.species.Species;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

/** Worker ant: joins the nearest colony when spawned, then just wanders for now. */
public class WorkerAntEntity extends AbstractAntEntity {
    public static final String CASTE = "worker";
    /** How close (in blocks) a queen must be for a new worker to join her colony. */
    private static final double JOIN_RADIUS = 32.0;

    private boolean colonyChecked;
    /**
     * A worker eats one seed a day; without one it slows down and then starves to death and leaves its body. How long
     * each takes comes from its colony's species profile: colony.profile().worker().
     */
    @org.jetbrains.annotations.Nullable
    private WorkerHunger hunger; // (made on first use: the goals are set up before the fields, in the mob's constructor)

    private WorkerHunger hunger() {
        if (hunger == null) {
            hunger = new WorkerHunger(this);
        }
        return hunger;
    }

    public WorkerAntEntity(EntityType<? extends WorkerAntEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A hungry worker eats a raw seed from the food store before anything else.
        this.goalSelector.addGoal(1, new EatGoal(this, hunger()));
        this.goalSelector.addGoal(1, new StoreSeedGoal(this));
        this.goalSelector.addGoal(2, new CarryPelletGoal(this));
        this.goalSelector.addGoal(3, new SealGoal(this));
        // The queen's egg comes out behind her: a worker carries it to the nursery.
        this.goalSelector.addGoal(4, new CarryEggGoal(this));
        // Feeding the queen (bread from the nursery) and milling seeds into bread come before fetching more seeds.
        this.goalSelector.addGoal(5, new FeedLarvaGoal(this));
        this.goalSelector.addGoal(5, new FeedQueenGoal(this));
        this.goalSelector.addGoal(5, new MillBreadGoal(this));
        // Dead nestmates are carried down to the refuse chamber (the builders' job) before more seeds are fetched.
        this.goalSelector.addGoal(6, new CarryCorpseGoal(this));
        this.goalSelector.addGoal(6, new GatherSeedGoal(this));
        this.goalSelector.addGoal(7, new ClipGrassGoal(this));
        this.goalSelector.addGoal(7, new ScoutGoal(this));
        this.goalSelector.addGoal(8, new DigGoal(this, () -> true, () -> mayDo(ColonyRole.BUILDER)));
        // Once the nest is dug, builders mend the hill when a block of it has been knocked out.
        this.goalSelector.addGoal(8, new RepairHillGoal(this));
        // The nurse waits by the queen until there is an egg or a meal for her.
        this.goalSelector.addGoal(9, new StandByQueenGoal(this));
        // With nothing to do it goes in (real harvester ants keep their spare workers inside, by the entrance) and strolls there.
        this.goalSelector.addGoal(10, new GoInsideGoal(this));
        this.goalSelector.addGoal(11, new AntWanderGoal(this, 1.0, 120, 6, () -> true).hangsFromCeilings(0.25F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean hasColonyRole() {
        return true;
    }

    /** A worker that hatched in this colony's nursery belongs to it from the start (and is counted). */
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(CASTE, colony.getCount(CASTE) + 1);
    }

    /** Whether this worker has not eaten for a day (its species' meal interval). */
    public boolean isHungry() {
        return hunger().isHungry();
    }

    public long getLastAte() {
        return hunger().getLastAte();
    }

    /** For the tests: pretend the last meal was at this game time. */
    public void setLastAte(long gameTime) {
        hunger().setLastAte(gameTime);
    }

    @Override
    public long getLastMeal() {
        return hunger().getLastAte();
    }

    @Override
    public void setLastMeal(long gameTime) {
        hunger().setLastAte(gameTime);
    }

    // Once a second: hungry for a day and a half = slower, for three days = dead.
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            hunger().tick(serverLevel);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hunger().save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger().load(tag);
    }

    /**
     * This worker joins no colony (an ant a test walks around by hand): otherwise it would join the nearest colony on
     * its first tick, which may be another test's colony nearby and spoil that test's headcount.
     */
    public void keepOutOfColonies() {
        colonyChecked = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return; // back from being frozen, it went into its colony's numbers (see colony.AwayLife)
        }
        // Once, on the first tick: a worker with no colony joins the nearest one.
        if (!colonyChecked && level() instanceof ServerLevel serverLevel) {
            colonyChecked = true;
            if (getColonyId() == null) {
                ColonyManager manager = ColonyManager.get(serverLevel);
                Colony colony = manager.findNearest(blockPosition(), JOIN_RADIUS, Species.RED_HARVESTER_ANT);
                if (colony != null) {
                    setColonyId(colony.getId());
                    colony.setCount(CASTE, colony.getCount(CASTE) + 1);
                    manager.markChanged();
                }
            }
        }
    }

    // A dead worker leaves a body behind where it died (see AbstractAntEntity.remove).
    @Override
    protected boolean leavesBody() {
        return true;
    }

    // A dead worker leaves the colony's headcount, and what it carried is left for another worker to pick up.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                dropLoadOnDeath(serverLevel, colony);
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }

    @Override
    public String nestCaste() {
        return CASTE;
    }

    @Override
    public void comeOutOfNest(Colony colony, long grownUpAt) {
        super.comeOutOfNest(colony, grownUpAt);
        colonyChecked = true;
        hunger().eat();
    }
}
