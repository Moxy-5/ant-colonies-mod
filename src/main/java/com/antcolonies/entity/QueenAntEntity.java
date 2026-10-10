package com.antcolonies.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

/** Queen ant: bigger, tougher and slow. The colony lives and dies with her. */
public class QueenAntEntity extends AbstractAntEntity {
    public QueenAntEntity(EntityType<? extends QueenAntEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // She hardly ever wanders: low chance to start a stroll.
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.002F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }
}
