package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The river catfish (M9 river life, the hunter, 2026-11-14; a generalized catfish, fun first, inspired by the African
 * sharptooth catfish of the user's picture): the top of the river's food chain, algae -> tilapia -> catfish. A big,
 * slow fish about 2 blocks long (the user's pick) that cruises alone and, when hungry, stalks a tilapia, then lunges
 * and swallows it whole ({@link HuntTilapiaGoal}); the school round it scatters.
 *
 * <p>One look, A, grey-brown mottled (the user's pick). Each catfish has a length of its own, 0.85 to 1.15 of the usual
 * (my pick), drawn and boxed through {@link Attributes#SCALE}. It never starves (the balance rule): with no tilapia near
 * it swims on. Too big for a bucket (my pick). Out of water it stays upright and wriggles, as real catfish crawl over wet
 * ground between pools (it still dries out like any fish).
 */
public class CatfishEntity extends AbstractFish implements GeoEntity {
    /** How long a strike is shown (ticks; the strike clip is 1 s, the mouth open from 0.05 to 0.38 s). */
    static final int STRIKE_TICKS = 20;

    private static final EntityDataAccessor<Boolean> DATA_STRIKING = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Its own length against the usual catfish. */
    private float ownSize = 1.0F;
    /** Ticks until it is hungry again ({@link HuntTilapiaGoal}). */
    int hungryIn;
    /** Ticks left of the strike being shown. */
    private int striking;
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;

    public CatfishEntity(EntityType<? extends CatfishEntity> type, Level level) {
        super(type, level);
        hungryIn = 600 + level.random.nextInt(2400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new HuntTilapiaGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STRIKING, false);
    }

    public boolean isStriking() {
        return entityData.get(DATA_STRIKING);
    }

    /** Shows the strike (the lunge and the open mouth) for {@link #STRIKE_TICKS}. */
    void strike() {
        striking = STRIKE_TICKS;
        entityData.set(DATA_STRIKING, true);
    }

    /** For tests: makes it hungry now. */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isHungry() {
        return hungryIn <= 0;
    }

    public float ownSize() {
        return ownSize;
    }

    /** Where its mouth is: the front of the snout, about 0.85 blocks ahead of its middle at the usual size. */
    Vec3 mouth() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double reach = 0.85 * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * reach, getY() + 0.17 * getScale(), getZ() + Mth.cos(yaw) * reach);
    }

    private void refresh() {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - ownSize) > 0.005) {
            scale.setBaseValue(ownSize);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swimPitchO = swimPitch;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float want = isInWater() && flat + Math.abs(dy) > 0.01 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -25, 25) : 0;
            swimPitch += (want - swimPitch) * 0.1F;
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (striking > 0 && --striking == 0) {
            entityData.set(DATA_STRIKING, false);
        }
    }

    /** The drawn body's pitch this frame (degrees, nose up positive). */
    public float swimPitch(float partialTick) {
        return Mth.lerp(partialTick, swimPitchO, swimPitch);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        ownSize = 0.85F + 0.3F * level.getRandom().nextFloat();
        refresh();
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("OwnSize", ownSize);
        tag.putInt("HungryIn", hungryIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("OwnSize")) {
            ownSize = Mth.clamp(tag.getFloat("OwnSize"), 0.5F, 1.5F);
            hungryIn = tag.getInt("HungryIn");
            refresh();
        }
    }

    /** Too big for a bucket (my pick): a water bucket does nothing to it. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(Items.WATER_BUCKET)) {
            return InteractionResult.PASS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return ItemStack.EMPTY;
    }

    /** Kept near a player like the tilapia ({@link TilapiaEntity#KEEP_NEAR}). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer)
                && distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = TilapiaEntity.DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.SALMON_FLOP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SALMON_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SALMON_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SALMON_HURT;
    }

    /** Where it may spawn: the tilapia's waters (rivers, pans, oxbows), with two blocks of water under it, so not in puddles. */
    public static boolean canSpawn(EntityType<CatfishEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 13 && pos.getY() <= sea + 6 && level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos.below(2)).is(FluidTags.WATER);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation STRIKE = RawAnimation.begin().thenPlay("strike");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, about 1 a second); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.05;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 3, this::animate));
    }

    private PlayState animate(AnimationState<CatfishEntity> state) {
        AnimationController<CatfishEntity> player = state.getController();
        if (isDeadOrDying()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(DEATH);
        }
        if (!isInWater()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(FLOP);
        }
        if (isStriking()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(STRIKE);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed < 0.008) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(IDLE);
        }
        player.setAnimationSpeed(Mth.clamp(speed / SWIM_CLIP_SPEED, 0.5, 2.5));
        return state.setAndContinue(SWIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
