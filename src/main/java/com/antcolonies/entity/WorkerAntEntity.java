package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.CarryCorpseGoal;
import com.antcolonies.entity.goal.CarryEggGoal;
import com.antcolonies.entity.goal.CarryPelletGoal;
import com.antcolonies.entity.goal.ClipGrassGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.EatSeedGoal;
import com.antcolonies.entity.goal.FeedLarvaGoal;
import com.antcolonies.entity.goal.FeedQueenGoal;
import com.antcolonies.entity.goal.MillBreadGoal;
import com.antcolonies.entity.goal.GatherSeedGoal;
import com.antcolonies.entity.goal.RepairHillGoal;
import com.antcolonies.entity.goal.SealGoal;
import com.antcolonies.entity.goal.StandByQueenGoal;
import com.antcolonies.entity.goal.StoreSeedGoal;

import com.antcolonies.AntColoniesMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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

    /** A worker eats one seed a day: after this many ticks without a meal (a day = 24000 ticks) it is hungry and goes to the food store. */
    public static final int MEAL_INTERVAL_TICKS = 24000;
    /** Without a meal for this long (a day and a half) it slows down ... */
    public static final int SLOW_AFTER_TICKS = 36000;
    /** ... and without one for this long (three days) it starves to death and leaves its body. */
    public static final int STARVE_AFTER_TICKS = 72000;
    private static final ResourceLocation STARVING_SLOWDOWN =
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "starving");

    private boolean colonyChecked;
    /** {@link #lastAte} before the worker has been in a colony (its hunger clock starts then). */
    private static final long NOT_SET = Long.MIN_VALUE;

    /** The game time of this worker's last meal ({@link #NOT_SET} = not yet, it counts from the first moment in a colony). Saved. */
    private long lastAte = NOT_SET;

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
        this.goalSelector.addGoal(1, new EatSeedGoal(this));
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
        this.goalSelector.addGoal(8, new DigGoal(this, () -> true, () -> mayDo(ColonyRole.BUILDER)));
        // Once the nest is dug, builders mend the hill when a block of it has been knocked out.
        this.goalSelector.addGoal(8, new RepairHillGoal(this));
        // The nurse waits by the queen until there is an egg or a meal for her.
        this.goalSelector.addGoal(9, new StandByQueenGoal(this));
        this.goalSelector.addGoal(10, new AntWanderGoal(this, 1.0, 120, 6, () -> true));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hasRole(ColonyRole role) {
        if (getColonyId() == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
        return colony != null && colony.roleOf(serverLevel, getUUID()) == role;
    }

    @Override
    public boolean mayDo(ColonyRole role) {
        return isIdle() || getColonyId() == null || hasRole(role) || !(level() instanceof ServerLevel serverLevel)
                || ColonyManager.get(serverLevel).getColony(getColonyId()) == null;
    }

    /** A worker that hatched in this colony's nursery belongs to it from the start (and is counted). */
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(CASTE, colony.getCount(CASTE) + 1);
    }

    /** Whether this worker has not eaten for a day. */
    public boolean isHungry() {
        return lastAte != NOT_SET && level().getGameTime() - lastAte >= MEAL_INTERVAL_TICKS;
    }

    /** The worker has eaten: it is full for another day. */
    public void eat() {
        lastAte = level().getGameTime();
        updateStarving(0);
    }

    public long getLastAte() {
        return lastAte;
    }

    /** For the tests: pretend the last meal was at this game time. */
    public void setLastAte(long gameTime) {
        lastAte = gameTime;
    }

    // Once a second: hungry for a day and a half = slower, for three days = dead.
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        Colony colony = getColonyId() == null ? null : ColonyManager.get(serverLevel).getColony(getColonyId());
        if (lastAte == NOT_SET || colony == null || !colony.isHungerOn()) {
            lastAte = now; // the clock only runs for a worker in a colony that gets hungry
        }
        long hungry = now - lastAte;
        updateStarving(hungry);
        if (hungry >= STARVE_AFTER_TICKS) {
            hurt(damageSources().starve(), 1000.0F);
        }
    }

    /** Slows the worker down while it has gone without a meal for {@link #SLOW_AFTER_TICKS}. */
    private void updateStarving(long ticksWithoutMeal) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (ticksWithoutMeal >= SLOW_AFTER_TICKS) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(STARVING_SLOWDOWN, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(STARVING_SLOWDOWN);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("lastAte", lastAte);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastAte = tag.contains("lastAte") ? tag.getLong("lastAte") : NOT_SET;
    }

    @Override
    public void tick() {
        super.tick();
        // Once, on the first tick: a worker with no colony joins the nearest one.
        if (!colonyChecked && level() instanceof ServerLevel serverLevel) {
            colonyChecked = true;
            if (getColonyId() == null) {
                ColonyManager manager = ColonyManager.get(serverLevel);
                Colony colony = manager.findNearest(blockPosition(), JOIN_RADIUS);
                if (colony != null) {
                    setColonyId(colony.getId());
                    colony.setCount(CASTE, colony.getCount(CASTE) + 1);
                    manager.markChanged();
                }
            }
        }
    }

    // A dead worker leaves a body behind where it died (once the death animation is over), for the workers to carry to the refuse chamber.
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED && getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
            if (colony != null) {
                colony.spawnCorpse(serverLevel, getX(), getY(), getZ());
            }
        }
        super.remove(reason);
    }

    // A dead worker leaves the colony's headcount, and the egg or body it carried falls to the ground for another worker to pick up.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                if (isCarryingEgg()) {
                    setCarryingEgg(false);
                    colony.spawnEgg(serverLevel, getX(), getY(), getZ());
                }
                if (isCarryingCorpse()) {
                    setCarryingCorpse(false);
                    colony.spawnCorpse(serverLevel, getX(), getY(), getZ());
                }
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }
}
