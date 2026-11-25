package com.antcolonies.savanna.animal;

import com.antcolonies.animal.AnimalLives;
import com.antcolonies.animal.RoomToBreed;

import javax.annotation.Nullable;

import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
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
 * The bullfrog's tadpole (the river tie-up, 2026-11-23, the user: "tadpoles spawning in the water naturally, being a
 * food source as well and coming from frog breeding"; designed 2026-11-20, {@code docs/plans/prey-fish-and-frogs.md}):
 * a near-black tadpole 0.15 blocks long that swims in dense schools of 6 to 10 in shallow still water (pans, oxbows,
 * slow edges), grazing the algae (the link algae to tadpole to frog). Animals are fun first; every number my pick.
 *
 * <p>Its life: hatched (or spawned) it is a plain tadpole; from day 3 ({@link #LEGS_AT}) its hind leg buds show; at the
 * end of day 3 ({@link #FROGLET_AT}) it becomes a froglet, a young {@link BullfrogEntity}, which is grown 3 days later,
 * but only while the bullfrog has room to breed ({@link RoomToBreed}: fewer than its life file's 4 within 48 blocks; the
 * user found 4 frogs round a player enough, so a school of ten does not turn into ten frogs); the rest quietly do not
 * make it, as most real tadpoles do not.
 *
 * <p>Schools come from the world's own spawns and from bullfrog eggs ({@link com.antcolonies.savanna.block.FrogEggsBlock},
 * part B): a hatched tadpole knows its father ({@link #father}), who guards the school and never eats it.
 *
 * <p>Eaten by: grown bullfrogs (their ambush lunge; real bullfrogs are cannibals), hatchling crocodiles, catfish (a
 * snack, as a barb). A strike near the school makes it dart off ({@link #startle}); a hunter near makes it swim aside.
 */
public class TadpoleEntity extends AbstractSchoolingFish implements GeoEntity {
    /** What it is doing to be seen (synced): nothing, or grazing a mat. */
    public static final byte NONE = 0, GRAZE = 1;
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(TadpoleEntity.class, EntityDataSerializers.BYTE);
    /**
     * Whether its leg buds show (synced, set once at {@link #LEGS_AT}). Its age itself is the server's own count (review
     * R10-9: the age was synced and changed every tick, so every tadpole sent every player a packet every tick).
     */
    private static final EntityDataAccessor<Boolean> DATA_LEGS = SynchedEntityData.defineId(TadpoleEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its age in ticks (the server's; saved). */
    private int age;
    /** The leg buds show from the start of day 3, and it climbs out a froglet at the end of it (ticks). */
    public static final int LEGS_AT = 2 * 24000, FROGLET_AT = 3 * 24000;
    /**
     * Eggs hatch only while fewer than this many tadpoles live within {@link RoomToBreed#REACH} blocks (the eggs' room to
     * hatch, as the life kit's local limit; two or three schools) and the tadpole group is under its cap.
     */
    public static final int LOCAL_LIMIT = 24;
    /** The entity event that tells the players' computers it was startled (plays its dart). */
    private static final byte STARTLED_EVENT = 61;
    /** How hard a startled tadpole darts away (blocks a tick, added once), and its dart's length (ticks: 0.3 s). */
    static final double STARTLE_PUSH = 0.3;
    static final int SCATTER_TICKS = 6;
    /** How near a hunter makes it swim aside (blocks). */
    static final float PART_FROM = 2.5F;
    /** Deepest water it spawns in (blocks): the shallow edges. */
    static final int SHALLOW = 2;
    /** How far off it is still drawn (blocks: past this a tadpole is not a speck but nothing). */
    static final double DRAW_DISTANCE = 24.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float swimPitch, swimPitchO;
    private int scatterTicks;
    /** The bullfrog that guarded the eggs it hatched from, if any. */
    @Nullable
    private java.util.UUID father;

    public TadpoleEntity(EntityType<? extends TadpoleEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, PART_FROM, 1.3, 1.5, BarbEntity::isHunter));
        goalSelector.addGoal(3, new TadpoleGrazeGoal(this));     // over Minecraft's random swim (4), else a swim held off the graze roll (BUGS 302)
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_LEGS, false);
    }

    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    void setAction(byte action) {
        entityData.set(DATA_ACTION, action);
    }

    /** Its age (ticks since it hatched). */
    public int age() {
        return age;
    }

    public void setAge(int ticks) {
        age = Math.max(0, ticks);
        if (!level().isClientSide) {
            entityData.set(DATA_LEGS, age >= LEGS_AT);      // (sent only when it changes)
        }
    }

    /** The bullfrog that guarded the eggs it hatched from (null for the world's own spawns). */
    @Nullable
    public java.util.UUID father() {
        return father;
    }

    public void setFather(@Nullable java.util.UUID frog) {
        father = frog;
    }

    /** Whether its hind leg buds show (day 3 on). */
    public boolean hasLegs() {
        return entityData.get(DATA_LEGS);
    }

    /** A strike nearby (a catfish's lunge, a crocodile's snap): it darts straight away from the hunter. */
    public void startle(Vec3 from) {
        Vec3 away = position().subtract(from);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        setDeltaMovement(getDeltaMovement().add(away.normalize().scale(STARTLE_PUSH)));
        hurtMarked = true;
        getNavigation().stop();
        level().broadcastEntityEvent(this, STARTLED_EVENT);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == STARTLED_EVENT) {
            scatterTicks = SCATTER_TICKS;
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (isAlive() && getVehicle() == null) {
                int age = age() + 1;
                setAge(age);
                if (age >= FROGLET_AT) {
                    becomeFroglet((ServerLevel) level());
                }
            }
            return;
        }
        swimPitchO = swimPitch;
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float want = isInWater() && flat + Math.abs(dy) > 0.004 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -35, 35) : 0;
        swimPitch += (want - swimPitch) * 0.2F;
        if (scatterTicks > 0) {
            scatterTicks--;
        }
    }

    /**
     * The end of day 3: it climbs out a froglet where it is (a young bullfrog, a male or a female, grown in 3 days), if
     * the bullfrog has room to breed here ({@link RoomToBreed}, its life file's local limit and the frog group's cap);
     * else it quietly does not make it. Either way the tadpole goes.
     */
    void becomeFroglet(ServerLevel level) {
        EntityType<BullfrogEntity> frog = SavannaEntities.BULLFROG.get();
        if (RoomToBreed.leftAt(level, blockPosition(), frog, AnimalLives.get(frog).localLimit()) > 0) {
            BullfrogEntity froglet = frog.create(level);
            if (froglet != null) {
                froglet.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                froglet.makeFroglet();
                if (isPersistenceRequired()) {
                    froglet.setPersistenceRequired();
                }
                level.addFreshEntity(froglet);
                playSound(SoundEvents.TADPOLE_GROW_UP, 0.5F, 1.0F);
            }
        }
        discard();
    }

    /** Its drawn body's pitch this frame (degrees, nose up positive). */
    public float swimPitch(float partialTick) {
        return Mth.lerp(partialTick, swimPitchO, swimPitch);
    }

    /**
     * A school spawned by the world: the first tadpole's age is any time in its first two and a half days, its
     * schoolmates within an hour of it (one clutch).
     */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        RandomSource dice = level.getRandom();
        if (data instanceof AbstractSchoolingFish.SchoolSpawnGroupData school && school.leader instanceof TadpoleEntity first) {
            setAge(first.age() + dice.nextInt(2000) - 1000);
        } else {
            setAge(dice.nextInt(FROGLET_AT - 12000));
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", age());
        if (father != null) {
            tag.putUUID("Father", father);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAge(tag.getInt("Age"));
        father = tag.hasUUID("Father") ? tag.getUUID("Father") : null;
    }

    /** No tadpole bucket of ours: a water bucket does nothing to it. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Kept near a player like the river fish; past 128 blocks always removed (unless hatched from eggs, kept). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer) && distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public int getMaxSchoolSize() {
        return 12;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TADPOLE_FLOP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TADPOLE_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TADPOLE_HURT;
    }

    /**
     * Where it may spawn by itself: in shallow water (1 to {@link #SHALLOW} blocks deep) of the savanna's pans, oxbows
     * and river edges, in the river animals' band of heights (the barbs' band).
     */
    public static boolean canSpawn(EntityType<TadpoleEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        int depth = 1;
        for (int up = 1; depth <= SHALLOW && level.getFluidState(pos.above(up)).is(FluidTags.WATER); up++) {
            depth++;
        }
        for (int down = 1; depth <= SHALLOW && level.getFluidState(pos.below(down)).is(FluidTags.WATER); down++) {
            depth++;
        }
        return depth <= SHALLOW;
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation SCATTER = RawAnimation.begin().thenPlay("scatter");
    private static final RawAnimation STRUGGLE = RawAnimation.begin().thenLoop("scatter");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.03;
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();

    /** For the in-game camera: a tadpole named {@code film:<clip>} plays only that clip, looping. */
    @Nullable
    private String filmClip() {
        net.minecraft.network.chat.Component name = getCustomName();
        return name != null && name.getString().startsWith("film:") ? name.getString().substring(5) : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
    }

    private PlayState animate(AnimationState<TadpoleEntity> state) {
        AnimationController<TadpoleEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        String film = filmClip();
        if (film != null) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film, c -> RawAnimation.begin().thenLoop(c)));
        }
        if (getVehicle() != null) {
            return state.setAndContinue(STRUGGLE);
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!isInWater()) {
            return state.setAndContinue(FLOP);
        }
        if (scatterTicks > 0) {
            return state.setAndContinue(SCATTER);
        }
        if (action() == GRAZE) {
            return state.setAndContinue(GRAZE_CLIP);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed < 0.004) {
            return state.setAndContinue(IDLE);
        }
        player.setAnimationSpeed(Mth.clamp(speed / SWIM_CLIP_SPEED, 0.6, 3.0));
        return state.setAndContinue(SWIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
