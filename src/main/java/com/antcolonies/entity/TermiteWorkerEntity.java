package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DigGoal;
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
 * A termite worker (Macrotermes): pale, soft and blind. For now it digs (the first workers open the entrance the
 * royal pair sealed) and wanders near the nest; building the mound (step 3b) and gathering food come later. It joins
 * the nearest termite colony when put down with a spawn egg, and is born into its colony when a nymph grows up.
 */
public class TermiteWorkerEntity extends AbstractAntEntity {
    /** Counted under the same caste name as ant workers (each colony has its own counts). */
    public static final String CASTE = WorkerAntEntity.CASTE;
    /** How close (in blocks) a termite colony must be for a new worker to join it. */
    private static final double JOIN_RADIUS = 32.0;

    private boolean colonyChecked;

    public TermiteWorkerEntity(EntityType<? extends TermiteWorkerEntity> type, Level level) {
        super(type, level);
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
        this.goalSelector.addGoal(1, new DigGoal(this, () -> true));
        this.goalSelector.addGoal(2, new AntWanderGoal(this, 1.0, 160, 5, () -> true).hangsFromCeilings(0.25F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // Until the mound is built (step 3b) the soil it bites off is left where it falls.
    @Override
    public boolean canPlaceBlocks() {
        return false;
    }

    /** A worker that grew up from a nymph of this colony belongs to it from the start (and is counted). */
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(CASTE, colony.getCount(CASTE) + 1);
    }

    @Override
    public void tick() {
        super.tick();
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
