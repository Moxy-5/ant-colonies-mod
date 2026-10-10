package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.CarryPelletGoal;
import com.antcolonies.entity.goal.ClipGrassGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.GatherSeedGoal;
import com.antcolonies.entity.goal.SealGoal;
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
        this.goalSelector.addGoal(4, new GatherSeedGoal(this));
        this.goalSelector.addGoal(5, new ClipGrassGoal(this));
        this.goalSelector.addGoal(6, new DigGoal(this, () -> true));
        this.goalSelector.addGoal(7, new AntWanderGoal(this, 1.0, 120, 6, () -> true));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
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

    // A dead worker leaves the colony's headcount.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }
}
