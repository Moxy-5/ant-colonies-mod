package com.antcolonies.entity;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
    // Placeholder until species profiles (JSON) exist.
    private static final ResourceLocation SPECIES =
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "red_harvester_ant");

    private boolean colonyChecked;

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

    @Override
    public void tick() {
        super.tick();
        // Once, on the first tick: make sure the brain has a colony for this queen.
        if (!colonyChecked && level() instanceof ServerLevel serverLevel) {
            colonyChecked = true;
            ColonyManager manager = ColonyManager.get(serverLevel);
            @Nullable var existing = getColonyId() == null ? null : manager.getColony(getColonyId());
            if (existing == null) {
                Colony colony = manager.createColony(SPECIES, getUUID(), blockPosition());
                setColonyId(colony.getId());
            }
        }
    }

    // The colony dies when the queen dies.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager.get(serverLevel).removeColony(getColonyId());
        }
    }
}
