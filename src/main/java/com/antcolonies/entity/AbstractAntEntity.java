package com.antcolonies.entity;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.nav.AntMoveControl;
import com.antcolonies.colony.ColonyRole;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.nav.AntNavigator;
import com.antcolonies.registry.ModItems;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.Vec3;
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

    /**
     * The surface the body is on (a {@link Direction} 3D id: 0 = DOWN = a floor, 2 to 5 = the wall on that side), and on
     * a wall where the head points (degrees: 0 up, 180 down, +90 to its left). Synced so the renderer can lay the body
     * on the surface (see {@link #getBodyTilt} and the other body angles).
     */
    private static final EntityDataAccessor<Byte> DATA_SURFACE =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEADING =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.FLOAT);
    /** How fast the drawn body turns (degrees per tick) onto a new surface, round a corner or to a new heading. */
    private static final float BODY_TURN_SPEED = 30.0F;

    /** The soil pellet held in the mandibles (empty = carrying nothing). Synced so the renderer can draw it. */
    private static final EntityDataAccessor<Optional<BlockState>> DATA_CARRIED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

    /** The seed held in the mandibles (empty = none). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<ItemStack> DATA_CARRIED_SEED =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.ITEM_STACK);

    /** True while the ant holds an egg in its mandibles (carrying it to the nursery). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_EGG =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    /** True while the ant holds a dead nestmate in its mandibles (carrying it to the refuse chamber). Synced so the renderer can draw it. Saved. */
    private static final EntityDataAccessor<Boolean> DATA_CARRIED_CORPSE =
            SynchedEntityData.defineId(AbstractAntEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final AntNavigator navigator = new AntNavigator(this);

    /** Which colony this ant belongs to (null = none). Saved with the ant. */
    @Nullable
    private UUID colonyId;

    private boolean seedForBread;
    /** How many seconds in a row the ant has had nothing to do. Server side only, not saved. */
    private int idleSeconds;
    /** Whether the navigator set the motion last tick (so the leftover ladder push can be cleared when it lets go). */
    private boolean wasAttached;

    // Client only: how the body is drawn, turned smoothly toward the synced surface and heading (see AntRenderer).
    // While on a wall (or tipping onto or off one) the body is drawn by these three angles instead of the body yaw:
    // the yaw of the wall it faces, the tilt (0 = lying on the floor, 90 = upright on the wall) and the heading within it.
    private boolean wallPose;
    private float bodyTilt;
    private float bodyTiltO;
    private float wallYaw;
    private float wallYawO;
    private float bodyHeading;
    private float bodyHeadingO;

    protected AbstractAntEntity(EntityType<? extends AbstractAntEntity> type, Level level) {
        super(type, level);
        this.moveControl = new AntMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SURFACE, (byte) Direction.DOWN.get3DDataValue());
        builder.define(DATA_HEADING, 0.0F);
        builder.define(DATA_CARRIED, Optional.empty());
        builder.define(DATA_CARRIED_SEED, ItemStack.EMPTY);
        builder.define(DATA_CARRIED_EGG, false);
        builder.define(DATA_CARRIED_CORPSE, false);
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

    /** Whether the ant holds a piece of ant bread, plain or rich (on its way to the queen or a larva). */
    public boolean isCarryingBread() {
        return getCarriedSeed().is(ModItems.ANT_BREAD.get()) || isCarryingRichBread();
    }

    /** Whether the piece of bread the ant holds is rich ant bread. */
    public boolean isCarryingRichBread() {
        return getCarriedSeed().is(ModItems.RICH_ANT_BREAD.get());
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

    /** Whether this ant holds a dead nestmate in its mandibles (a worker bringing it to the refuse chamber). */
    public boolean isCarryingCorpse() {
        return entityData.get(DATA_CARRIED_CORPSE);
    }

    public void setCarryingCorpse(boolean carrying) {
        entityData.set(DATA_CARRIED_CORPSE, carrying);
    }

    /** The seed in the mandibles is for the mill (it becomes bread), not for the food store. Server side only, saved. */
    public boolean isSeedForBread() {
        return seedForBread;
    }

    public void setSeedForBread(boolean forBread) {
        this.seedForBread = forBread;
    }

    /** Whether the mandibles are full (a soil pellet, a seed, bread, an egg or a dead ant), so the ant can't pick up anything else. */
    public boolean hasFullMandibles() {
        return isCarrying() || !getCarriedSeed().isEmpty() || isCarryingEgg() || isCarryingCorpse();
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
        if (navigator.isAttached()) {
            // Holding a wall or going round an edge: the navigator decides the exact motion along the surface.
            setDeltaMovement(navigator.getClimbMotion());
        } else if (wasAttached) {
            setDeltaMovement(Vec3.ZERO); // just let go of the wall: no leftover ladder push
        }
        wasAttached = navigator.isAttached();
    }

    @Override
    public void tick() {
        super.tick();
        // Only the route's climbing stretches make the ant climb, so ants never wander up walls by themselves.
        if (!level().isClientSide) {
            entityData.set(DATA_SURFACE, (byte) navigator.getSurface().get3DDataValue());
            entityData.set(DATA_HEADING, navigator.getHeading());
        } else {
            turnBody();
        }
    }

    /** The surface the body is on: {@link Direction#DOWN} = a floor, a horizontal direction = the wall on that side. */
    public Direction getSurface() {
        return Direction.from3DDataValue(entityData.get(DATA_SURFACE));
    }

    /** True while the body is on a wall. */
    public boolean isClimbing() {
        return getSurface().getAxis().isHorizontal();
    }

    // Tells the game this entity is holding on to something (no falling while the navigator moves it along a surface).
    @Override
    public boolean onClimbable() {
        return isClimbing() || (!level().isClientSide && navigator.isAttached());
    }

    /** Client: turns the drawn body a little toward the synced surface and heading, so it never snaps round. */
    private void turnBody() {
        bodyTiltO = bodyTilt;
        wallYawO = wallYaw;
        bodyHeadingO = bodyHeading;
        Direction surface = getSurface();
        if (surface.getAxis().isHorizontal()) {
            if (!wallPose) {
                // Tipping up from the floor: start from how the body lies now (facing its body yaw).
                wallPose = true;
                wallYaw = surface.toYRot();
                bodyHeading = Mth.wrapDegrees(wallYaw - yBodyRot);
                bodyTilt = 0.0F;
                wallYawO = wallYaw;
                bodyHeadingO = bodyHeading;
                bodyTiltO = bodyTilt;
            }
            wallYaw = approach(wallYaw, surface.toYRot());
            bodyTilt = Math.min(90.0F, bodyTilt + BODY_TURN_SPEED);
            bodyHeading = approach(bodyHeading, entityData.get(DATA_HEADING));
        } else if (wallPose) {
            // Tipping back onto the floor, ending up facing the body yaw.
            bodyTilt = Math.max(0.0F, bodyTilt - BODY_TURN_SPEED);
            bodyHeading = approach(bodyHeading, wallYaw - yBodyRot);
            if (bodyTilt <= 0.0F && Math.abs(Mth.wrapDegrees(bodyHeading - (wallYaw - yBodyRot))) < 1.0F) {
                wallPose = false;
            }
        }
    }

    private static float approach(float from, float to) {
        return from + Mth.clamp(Mth.wrapDegrees(to - from), -BODY_TURN_SPEED, BODY_TURN_SPEED);
    }

    /** Client: whether the body is drawn by the wall angles below (on a wall, or tipping onto or off one). */
    public boolean isInWallPose() {
        return wallPose;
    }

    /** Client: how far the body is tipped up onto the wall, 0 (floor) to 90 (upright on the wall). */
    public float getBodyTilt(float partialTick) {
        return Mth.lerp(partialTick, bodyTiltO, bodyTilt);
    }

    /** Client: the yaw of the wall the body faces. */
    public float getWallYaw(float partialTick) {
        return Mth.lerp(partialTick, wallYawO, wallYaw);
    }

    /** Client: where the head points within the wall (0 up, 180 down, +90 to its left). */
    public float getBodyHeading(float partialTick) {
        return Mth.lerp(partialTick, bodyHeadingO, bodyHeading);
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
        // What the ant holds in its mandibles, so nothing is lost when the world is saved and loaded again: the job that
        // carries it picks it up again after the reload (each one carries on when the ant already holds its load).
        BlockState pellet = getCarriedBlock();
        if (pellet != null) {
            tag.put("CarriedBlock", NbtUtils.writeBlockState(pellet));
        }
        if (!getCarriedSeed().isEmpty()) {
            tag.put("CarriedSeed", getCarriedSeed().save(registryAccess()));
        }
        tag.putBoolean("CarryingEgg", isCarryingEgg());
        tag.putBoolean("CarryingCorpse", isCarryingCorpse());
        tag.putBoolean("SeedForBread", seedForBread);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ColonyId")) {
            colonyId = tag.getUUID("ColonyId");
        }
        if (tag.contains("CarriedBlock")) {
            setCarriedBlock(NbtUtils.readBlockState(registryAccess().lookupOrThrow(Registries.BLOCK), tag.getCompound("CarriedBlock")));
        }
        if (tag.contains("CarriedSeed")) {
            setCarriedSeed(ItemStack.parse(registryAccess(), tag.getCompound("CarriedSeed")).orElse(ItemStack.EMPTY));
        }
        setCarryingEgg(tag.getBoolean("CarryingEgg"));
        setCarryingCorpse(tag.getBoolean("CarryingCorpse"));
        seedForBread = tag.getBoolean("SeedForBread");
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
        // Moving on a wall isn't "horizontal" movement, so check climbing separately (an ant hanging still does not move its legs).
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        if (isClimbing() && dx * dx + dy * dy + dz * dz > 1.0E-4) {
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
