package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Nile tilapia (M9 river life, the grazing fish, 2026-11-14; a named species, the user's OK; animals are fun first,
 * inspired by real life): a deep-bodied fish of the savanna's rivers, pans and oxbows that swims in schools and grazes
 * the river algae ({@link GrazeAlgaeGoal}, {@code AlgaeBlock.graze}), the second link of the food chain.
 *
 * <p>Three looks from one model (the user, 2026-11-14, "do them all for variation"; my picks): a young fish is pale and
 * strongly barred with the black "tilapia spot" ({@link #YOUNG}) and small; after {@link #GROW_UP_TICKS} it is grown
 * ({@link #EVERYDAY}, olive-silver), and about 1 grown male in 3 wears the breeding colours ({@link #BREEDING}: dark
 * blue-grey, red throat, red-edged fins). A young fish grows its size little by little. Each grown fish has a length of
 * its own (0.85 to 1.15 of the usual, my pick), drawn and boxed through {@link Attributes#SCALE}.
 *
 * <p>It never starves (my pick: the food chain must never crash): a hungry fish with no algae near just swims on.
 */
public class TilapiaEntity extends AbstractSchoolingFish implements GeoEntity {
    public static final int YOUNG = 0, EVERYDAY = 1, BREEDING = 2;
    /** Three game days from hatching to grown (my pick). */
    public static final int GROW_UP_TICKS = 3 * 24000;
    /** A young fish's size against a grown one's at hatching (it grows to 1 by {@link #GROW_UP_TICKS}). */
    public static final float YOUNG_SIZE = 0.5F;
    /** How many grown males wear the breeding colours (my pick: about 1 in 3). */
    static final float BREEDING_SHARE = 1 / 3F;
    /** The share of naturally spawned fish that are young (my pick). */
    static final float YOUNG_SHARE = 0.3F;

    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(TilapiaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GRAZING = SynchedEntityData.defineId(TilapiaEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks since it hatched (counted on to {@link #GROW_UP_TICKS}). */
    private int age = GROW_UP_TICKS;
    private boolean male;
    /** Its own length against the usual grown fish. */
    private float ownSize = 1.0F;
    /** Ticks until it is hungry again ({@link GrazeAlgaeGoal}). */
    int hungryIn;
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;

    public TilapiaEntity(EntityType<? extends TilapiaEntity> type, Level level) {
        super(type, level);
        hungryIn = 200 + level.random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 4.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new GrazeAlgaeGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, EVERYDAY);
        builder.define(DATA_GRAZING, false);
    }

    /** {@link #YOUNG}, {@link #EVERYDAY} or {@link #BREEDING} (the texture it wears). */
    public int look() {
        return entityData.get(DATA_LOOK);
    }

    public boolean isYoung() {
        return age < GROW_UP_TICKS;
    }

    public boolean isMale() {
        return male;
    }

    public boolean isGrazing() {
        return entityData.get(DATA_GRAZING);
    }

    void setGrazing(boolean grazing) {
        entityData.set(DATA_GRAZING, grazing);
    }

    /** Hatches it as a young fish (or makes it grown), its sex and grown size rolled. */
    public void setUp(boolean young, RandomSource random) {
        male = random.nextBoolean();
        ownSize = 0.85F + 0.3F * random.nextFloat();
        age = young ? random.nextInt(GROW_UP_TICKS / 3) : GROW_UP_TICKS;
        if (!young) {
            grownLook(random);
        }
        refresh();
    }

    /** For tests: makes it hungry now. */
    public void makeHungry() {
        hungryIn = 0;
    }

    /** Its own grown length against the usual. */
    public float ownSize() {
        return ownSize;
    }

    /** For tests: makes it this many ticks from grown. */
    public void setTicksToGrow(int ticks) {
        age = Math.max(0, GROW_UP_TICKS - ticks);
        refresh();
    }

    private int breedingLook;   // -1 not rolled yet, else the grown look

    private void grownLook(RandomSource random) {
        breedingLook = male && random.nextFloat() < BREEDING_SHARE ? BREEDING : EVERYDAY;
    }

    /** Its look and size from its age (and the grown look rolled once). */
    private void refresh() {
        if (level().isClientSide) {
            return;
        }
        if (!isYoung() && breedingLook != BREEDING && breedingLook != EVERYDAY) {
            grownLook(random);
        }
        entityData.set(DATA_LOOK, isYoung() ? YOUNG : breedingLook);
        float grown = isYoung() ? YOUNG_SIZE + (1 - YOUNG_SIZE) * age / (float) GROW_UP_TICKS : 1.0F;
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null && Math.abs(scale.getBaseValue() - grown * ownSize) > 0.005) {
            scale.setBaseValue(grown * ownSize);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swimPitchO = swimPitch;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float want = isInWater() && flat + Math.abs(dy) > 0.01 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -35, 35) : 0;
            swimPitch += (want - swimPitch) * 0.15F;
            return;
        }
        if (isYoung()) {
            age++;
            if (age % 200 == 0 || age == GROW_UP_TICKS) {
                refresh();
            }
        }
        if (hungryIn > 0) {
            hungryIn--;
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
        setUp(spawnType != MobSpawnType.BUCKET && level.getRandom().nextFloat() < YOUNG_SHARE, level.getRandom());
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeLife(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readLife(tag);
    }

    private void writeLife(CompoundTag tag) {
        tag.putInt("TilapiaAge", age);
        tag.putBoolean("Male", male);
        tag.putFloat("OwnSize", ownSize);
        tag.putInt("GrownLook", breedingLook);
    }

    private void readLife(CompoundTag tag) {
        if (!tag.contains("TilapiaAge")) {
            return;
        }
        age = tag.getInt("TilapiaAge");
        male = tag.getBoolean("Male");
        ownSize = Mth.clamp(tag.getFloat("OwnSize"), 0.5F, 1.5F);
        breedingLook = tag.getInt("GrownLook");
        refresh();
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, this::writeLife);
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        readLife(tag);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(SavannaItems.TILAPIA_BUCKET.get());
    }

    /**
     * Tilapia live in their river like cows on their plain (2026-11-14, the user: "they barely spawn"): Minecraft removes
     * its own fish once no player is within 64 blocks, and at random past 32, while new ones spawn 24 to 128 blocks out,
     * so most were gone as soon as they came and a narrow river held only one or two. They still count toward the
     * water mobs' cap near a player (20), so the river fills to that and no further.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** How far off a fish is still drawn (blocks; Minecraft's own rule, by the box's size, gave about 25). */
    static final double DRAW_DISTANCE = 64.0;

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public int getMaxSchoolSize() {
        return 8;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    /**
     * Where it may spawn: in water of the savanna's rivers, pans and oxbows, from 13 under sea level up to 6 over it
     * (the pans lie a block or two up on the floodplain; Minecraft's own fish rule stops at sea level).
     */
    public static boolean canSpawn(EntityType<TilapiaEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 13 && pos.getY() <= sea + 6
                && level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GRAZE = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, about 1.2 a second); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.06;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<TilapiaEntity> state) {
        AnimationController<TilapiaEntity> player = state.getController();
        if (isDeadOrDying()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(DEATH);
        }
        if (!isInWater()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(FLOP);
        }
        if (isGrazing()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(GRAZE);
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
