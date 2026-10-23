package com.antcolonies.entity;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.BuildMoundGoal;
import com.antcolonies.entity.goal.CarryCorpseGoal;
import com.antcolonies.entity.goal.CarryEggGoal;
import com.antcolonies.entity.goal.ClipGrassGoal;
import com.antcolonies.entity.goal.FeedLarvaGoal;
import com.antcolonies.entity.goal.FeedQueenGoal;
import com.antcolonies.entity.goal.GatherSeedGoal;
import com.antcolonies.entity.goal.GoInsideGoal;
import com.antcolonies.entity.goal.NoticeGoal;
import com.antcolonies.entity.goal.PlugHoleGoal;
import com.antcolonies.entity.goal.ScoutGoal;
import com.antcolonies.entity.goal.SealGoal;
import com.antcolonies.entity.goal.ShapeMoundGoal;
import com.antcolonies.entity.goal.StandByQueenGoal;
import com.antcolonies.entity.goal.StoreSeedGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.EatGoal;
import com.antcolonies.species.Species;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

/**
 * A termite worker (Macrotermes): pale, soft and blind. It digs (the first workers open the entrance the royal pair
 * sealed, then the fungus garden rooms and galleries under the mound for soil), builds the mound layer by layer and
 * shapes its pieces, gathers dead plant material (forage) and builds it into fungus comb in the gardens, and wanders
 * near the nest. Like the ants' workers they share the work out: nurses (from 3 workers on) carry the queen's eggs into
 * the gardens and feed fungus nodules to her and the nymphs, the rest are half builders (digging and building) and half
 * foragers; one with nothing to do for a few seconds helps with the other jobs. Foragers go out at night and in the rain, through the foraging holes (which the
 * workers open then and plug again afterwards) or now and then by a chimney; the rest of the time every worker stays
 * inside the nest and the mound, out of the sun. It joins the nearest termite colony when put down with a spawn egg,
 * and is born into its colony when a nymph grows up.
 */
public class TermiteWorkerEntity extends AbstractAntEntity implements ColonyBorn {
    /** Counted under the same caste name as ant workers (each colony has its own counts). */
    public static final String CASTE = Caste.WORKER;
    /** How close (in blocks) a termite colony must be for a new worker to join it. */
    private static final double JOIN_RADIUS = 32.0;

    private boolean colonyChecked;
    /**
     * Its own meals (once its colony has comb): a young worker eats a fungus nodule, an older one a bite of old comb,
     * once a day; without it slows down and then starves. The numbers are in the species profile ({@code worker}).
     */
    @org.jetbrains.annotations.Nullable
    private WorkerHunger hunger; // (made on first use: the goals are set up before the fields, in the mob's constructor)

    private WorkerHunger hunger() {
        if (hunger == null) {
            hunger = new WorkerHunger(this);
        }
        return hunger;
    }

    public TermiteWorkerEntity(EntityType<? extends TermiteWorkerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public net.minecraft.resources.ResourceLocation species() {
        return Species.MACROTERMES;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A hungry worker eats before anything else: a nodule (young) or old comb (older) in the fungus garden.
        this.goalSelector.addGoal(1, new EatGoal(this, hunger()));
        // What a termite holds goes where it belongs first: forage to the food store, soil to the mound.
        this.goalSelector.addGoal(1, new StoreSeedGoal(this));
        this.goalSelector.addGoal(1, new BuildMoundGoal(this));
        this.goalSelector.addGoal(2, new ShapeMoundGoal(this));
        // Nurses carry the queen's eggs into the fungus gardens and feed nodules to the queen and the nymphs.
        this.goalSelector.addGoal(2, new CarryEggGoal(this));
        this.goalSelector.addGoal(2, new FeedLarvaGoal(this));
        this.goalSelector.addGoal(2, new FeedQueenGoal(this));
        this.goalSelector.addGoal(2, new PlugHoleGoal(this));
        // Builders keep the nest shut: any opening in its walls (a cave, a block a player broke) is packed with mound soil.
        this.goalSelector.addGoal(2, new SealGoal(this));
        // Builders dig, foragers fetch food off the ground and cut it off plants.
        this.goalSelector.addGoal(3, new DigGoal(this, () -> true, () -> mayDo(ColonyRole.BUILDER)));
        this.goalSelector.addGoal(3, new GatherSeedGoal(this));
        this.goalSelector.addGoal(3, new CarryCorpseGoal(this));
        this.goalSelector.addGoal(4, new ClipGrassGoal(this));
        this.goalSelector.addGoal(4, new ScoutGoal(this));
        // Outside when it is time to stay in: back inside.
        this.goalSelector.addGoal(4, new GoInsideGoal(this));
        // With nothing to do, a player's steps or a jump shaking the ground near: it freezes, antennae up (it is blind).
        this.goalSelector.addGoal(4, new NoticeGoal(this));
        this.goalSelector.addGoal(5, new StandByQueenGoal(this));
        this.goalSelector.addGoal(5, new AntWanderGoal(this, 1.0, 160, 5, () -> true).hangsFromCeilings(0.25F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected boolean hasColonyRole() {
        return true;
    }

    /**
     * This worker joins no colony (a termite a test walks around by hand): otherwise it would join the nearest termite
     * colony on its first tick, which may be another test's colony nearby.
     */
    @Override
    public void keepOutOfColonies() {
        colonyChecked = true;
    }

    /** A worker that grew up from a nymph of this colony belongs to it from the start (and is counted). */
    @Override
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(CASTE, colony.getCount(CASTE) + 1);
    }

    /** Whether this worker has gone a day without a meal. */
    public boolean isHungry() {
        return hunger().isHungry();
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
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hunger().save(tag);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger().load(tag);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return; // back from being frozen, it went into its colony's numbers (see colony.AwayLife)
        }
        // Once, on the first tick: a worker put down with a spawn egg joins the nearest termite colony.
        if (!colonyChecked && level() instanceof ServerLevel serverLevel) {
            colonyChecked = true;
            ColonyManager manager = ColonyManager.get(serverLevel);
            if (getColonyId() == null || manager.getColony(getColonyId()) == null) {
                Colony colony = manager.findNearest(blockPosition(), JOIN_RADIUS, Species.MACROTERMES);
                setColonyId(null);
                if (colony != null) {
                    joinColony(colony);
                    manager.markChanged();
                }
            }
        }
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

    // A dead worker leaves a body behind where it died (see AbstractAntEntity.remove).
    @Override
    protected boolean leavesBody() {
        return true;
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
