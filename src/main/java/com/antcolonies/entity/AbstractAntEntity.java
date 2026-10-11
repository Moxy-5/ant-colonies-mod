package com.antcolonies.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.AntMoveControl;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.registry.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.item.ItemStack;
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

    /** The seed held in the mandibles (empty = none). Synced so the renderer can draw it. Not saved. */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_SEED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.ITEM_STACK);

    /** True while the ant holds an egg in its mandibles (carrying it to the nursery). Synced so the renderer can draw it. Not saved. */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_EGG =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final AntNavigator navigator = new AntNavigator(this);

    /** Which colony this ant belongs to (null = none). Saved with the ant. */
    @Nullable
    private UUID colonyId;

    private boolean seedForBread;
    /** How many seconds in a row the ant has had nothing to do. Server side only, not saved. */
    private int idleSeconds;

    protected AbstractAntEntity(EntityType<? extends AbstractAntEntity> type, Level level) {
        super(type, level);
        this.moveControl = new AntMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CLIMBING, false);
        builder.define(DATA_CARRIED, Optional.empty());
        builder.define(DATA_CARRIED_SEED, ItemStack.EMPTY);
        builder.define(DATA_CARRIED_EGG, false);
    }

    /** The block type of the pellet this ant carries, or null if its mandibles are empty. */
    @Nullable
    public BlockState getCarriedBlock() {
        return entityData.get(DATA_CARRIED).orElse(null);
    }

    /** Whether this ant can carry soil out and pack it into place. A queen can only dig: the soil stays where it falls. */
    public boolean canPlaceBlocks() {
        return true;
    }

    public boolean isCarrying() {
        return entityData.get(DATA_CARRIED).isPresent();
    }

    public void setCarriedBlock(@Nullable BlockState state) {
        entityData.set(DATA_CARRIED, Optional.ofNullable(state));
    }

    /** What this ant carries in its mandibles besides soil and eggs: a seed, or a piece of ant bread (empty if none). */
    public ItemStack getCarriedSeed() {
        return entityData.get(DATA_CARRIED_SEED);
    }

    /** Whether the ant holds a seed (on its way to the food store or the mill). */
    public boolean isCarryingSeed() {
        return !getCarriedSeed().isEmpty() && !isCarryingBread();
    }

    /** Whether the ant holds a piece of ant bread (on its way to the queen). */
    public boolean isCarryingBread() {
        return getCarriedSeed().is(ModItems.ANT_BREAD.get());
    }

    public void setCarriedSeed(ItemStack seed) {
        entityData.set(DATA_CARRIED_SEED, seed);
    }

    /** Whether this ant holds an egg in its mandibles (a worker bringing it to the nursery). */
    public boolean isCarryingEgg() {
        return entityData.get(DATA_CARRIED_EGG);
    }

    public void setCarryingEgg(boolean carrying) {
        entityData.set(DATA_CARRIED_EGG, carrying);
    }

    /** The seed in the mandibles is for the mill (it becomes bread), not for the food store. Server side only, not saved. */
    public boolean isSeedForBread() {
        return seedForBread;
    }

    public void setSeedForBread(boolean forBread) {
        this.seedForBread = forBread;
    }

    /** Whether the mandibles are full (a soil pellet, a seed, bread or an egg), so the ant can't pick up anything else. */
    public boolean hasFullMandibles() {
        return isCarrying() || !getCarriedSeed().isEmpty() || isCarryingEgg();
    }

    /** How long (in seconds) an ant must have nothing to do before it helps with work that is not its role's. */
    public static final int IDLE_SECONDS = 5;

    /** Nothing to do for a few seconds: time to help with other work. */
    public boolean isIdle() {
        return idleSeconds >= IDLE_SECONDS;
    }

    /** Whether this ant takes up work of this role now: its own role's work always, the others' only when it is idle. Everything goes for ants without roles. */
    public boolean mayDo(ColonyRole role) {
        return true;
    }

    /** Whether this ant's own role is {@code role}. */
    public boolean hasRole(ColonyRole role) {
        return false;
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
        // Once a second (staggered): is the ant doing anything, or only wandering and looking around?
        if (tickCount % 20 == getId() % 20) {
            boolean busy = goalSelector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.isRunning()
                    && !(wrapped.getGoal() instanceof FloatGoal || wrapped.getGoal() instanceof AntWanderGoal
                    || wrapped.getGoal() instanceof RandomLookAroundGoal));
            idleSeconds = busy ? 0 : idleSeconds + 1;
        }
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
        // Going up a wall isn't "horizontal" movement, so check climbing separately (an ant hanging still does not move its legs).
        if (isClimbing() && Math.abs(getY() - yo) > 0.01) {
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
