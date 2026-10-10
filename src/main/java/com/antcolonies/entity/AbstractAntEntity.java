package com.antcolonies.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.AntMoveControl;
import com.antcolonies.nav.AntNavigator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared base for every colony insect: never despawns, doesn't push, plays idle/walk animations. */
public abstract class AbstractAntEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /** True while the ant is climbing a wall. Synced to the client so the renderer can tilt the model. */
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    /** The soil pellet held in the mandibles (empty = carrying nothing). Synced so the renderer can draw it. */
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRIED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final AntNavigator navigator = new AntNavigator(this);

    /** Which colony this ant belongs to (null = none). Saved with the ant. */
    @Nullable
    private UUID colonyId;

    protected AbstractAntEntity(EntityType<? extends AbstractAntEntity> type, Level level) {
        super(type, level);
        this.moveControl = new AntMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLIMBING, false);
        builder.define(DATA_CARRIED, Optional.empty());
    }

    /** The block type of the pellet this ant carries, or null if its mandibles are empty. */
    @Nullable
    public BlockState getCarriedBlock() {
        return entityData.get(DATA_CARRIED).orElse(null);
    }

    public boolean isCarrying() {
        return entityData.get(DATA_CARRIED).isPresent();
    }

    public void setCarriedBlock(@Nullable BlockState state) {
        entityData.set(DATA_CARRIED, Optional.ofNullable(state));
    }

    /** The legs: plans routes (walk, climb, drop) and walks them. Goals only say where to go. */
    public AntNavigator getNavigator() {
        return navigator;
    }

    // Runs every tick, just before the move control: keeps the ant walking its planned route.
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        navigator.tick();
        if (navigator.isClimbing()) {
            // Clinging to a wall: the navigator decides the exact motion (rise at a steady speed, or hold
            // still when level with the target), whether or not the ant happens to touch the wall this tick.
            setDeltaMovement(navigator.getClimbMotion());
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Only the route's climbing stretches make the ant climb, so ants never wander up walls by themselves.
        if (!level().isClientSide) {
            entityData.set(DATA_CLIMBING, navigator.isClimbing());
        }
    }

    public boolean isClimbing() {
        return entityData.get(DATA_CLIMBING);
    }

    // Tells the game this entity can climb right now (ladder-style movement up the wall).
    @Override
    public boolean onClimbable() {
        return isClimbing();
    }

    // Ants are light enough to survive any fall (needed for the deep shaft).
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    protected void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (colonyId != null) {
            tag.putUUID("ColonyId", colonyId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ColonyId")) {
            colonyId = tag.getUUID("ColonyId");
        }
    }

    // Colony members never despawn.
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // Colony members don't push each other.
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, this::animate));
    }

    private PlayState animate(AnimationState<AbstractAntEntity> state) {
        // Going up a wall isn't "horizontal" movement, so check climbing separately.
        if (isClimbing() && Math.abs(getDeltaMovement().y) > 0.01) {
            state.getController().setAnimationSpeed(1.5);
            return state.setAndContinue(WALK);
        }
        if (state.isMoving()) {
            // Legs move faster when the ant moves faster, so they don't slide.
            state.getController().setAnimationSpeed(Math.max(0.6, state.getLimbSwingAmount() * 6.0));
            return state.setAndContinue(WALK);
        }
        state.getController().setAnimationSpeed(1.0);
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
