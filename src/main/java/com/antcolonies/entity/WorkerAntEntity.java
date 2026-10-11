package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.CarryEggGoal;
import com.antcolonies.entity.goal.CarryPelletGoal;
import com.antcolonies.entity.goal.ClipGrassGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.FeedQueenGoal;
import com.antcolonies.entity.goal.GatherSeedGoal;
import com.antcolonies.entity.goal.SealGoal;
import com.antcolonies.entity.goal.StandByQueenGoal;
import com.antcolonies.entity.goal.StoreSeedGoal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
        this.goalSelector.addGoal(1, new StoreSeedGoal(this));
        this.goalSelector.addGoal(2, new CarryPelletGoal(this));
        this.goalSelector.addGoal(3, new SealGoal(this));
        // The queen's egg comes out behind her: a worker carries it to the nursery.
        this.goalSelector.addGoal(4, new CarryEggGoal(this));
        // Feeding the queen (seeds from the food store) comes before fetching more seeds.
        this.goalSelector.addGoal(5, new FeedQueenGoal(this));
        this.goalSelector.addGoal(6, new GatherSeedGoal(this));
        this.goalSelector.addGoal(7, new ClipGrassGoal(this));
        this.goalSelector.addGoal(8, new DigGoal(this, () -> true, () -> mayDo(ColonyRole.BUILDER)));
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

    // A dead worker leaves the colony's headcount, and the egg it carried falls to the ground for another worker to pick up.
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
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }
}
