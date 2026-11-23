package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.entity.body.SteadyController;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
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
 * The river barb (M9 river life, the small prey fish, 2026-11-20; generalized, inspired by African barbs and robber
 * fish; animals are fun first): a slim silver fish a quarter of a block long that swims in tight schools of 8 to 14 in
 * the shallows, the young crocodiles' first meals and a snack for catfish ({@code docs/plans/prey-fish-and-frogs.md}).
 *
 * <p>Its school moves (my picks, from real schooling fish): a fish turning hard tips its flank to the light and flashes
 * silver for a moment (drawn by the renderer, {@link #flashing}); a strike into the school makes every barb near it
 * burst outward with a fast start ({@link #startle}, the "flash expansion"), and the school re-forms behind its leader;
 * a hunter or a swimming player near makes them part round it ({@link AvoidEntityGoal}, the "fountain").
 *
 * <p>No hunger and no blocks eaten (fewer rules: the algae stays the tilapia's): at dawn and dusk barbs rise to sip at
 * the surface, and now and then they pick at an algae mat, for looks only ({@link BarbFeedGoal}). Each fish has its own
 * length (0.85 to 1.15 of the usual, my pick) through {@link Attributes#SCALE}. Breeding is the plan's next step.
 */
public class BarbEntity extends AbstractSchoolingFish implements GeoEntity {
    /** What it is doing to be seen (synced): nothing, sipping at the surface, or nibbling algae. */
    public static final byte NONE = 0, SIP = 1, NIBBLE = 2;
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(BarbEntity.class, EntityDataSerializers.BYTE);
    /** The entity event that tells the players' computers it was startled (plays the fast start, flashing). */
    private static final byte STARTLED_EVENT = 61;
    /** How hard a startled barb darts away (blocks a tick, added once; my pick, faster than a tilapia's 0.4). */
    static final double STARTLE_PUSH = 0.5;
    /** The turn clips' and the fast start's lengths (ticks: 0.6 s and 0.4 s). */
    static final int TURN_TICKS = 12, SCATTER_TICKS = 8;
    /** A body turning faster than this (degrees a tick, smoothed) is a hard turn: the turn clip and the flash (my pick). */
    static final float TURN_FROM = 9.0F;
    /** Ticks after a hard turn before the next can start (so a wobbling school does not flicker; my pick). */
    static final int TURN_REST = 16;
    /** How near a hunter makes it swim aside (blocks; my pick). */
    static final float PART_FROM = 3.0F;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;
    // client only: the hard turn and the fast start now under way
    private float lastYaw, turnRate;
    private int turnTicks, turnRest, scatterTicks;
    private boolean turnRight;

    public BarbEntity(EntityType<? extends BarbEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 2.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // the fountain: a hunter swimming through makes the school part round it (players are Minecraft's own fish rule)
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, LivingEntity.class, PART_FROM, 1.4, 1.6, BarbEntity::isHunter));
        // a cruising bull shark, a big shape seen from afar: the school parts round it wider
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, BullSharkEntity.class, BullSharkEntity.FISH_PART_FROM, 1.4, 1.6, BullSharkEntity::partsFish));
        goalSelector.addGoal(4, new BarbFeedGoal(this));
    }

    /**
     * What makes a barb swim aside: a catfish, a crocodile past its hatchling days or a monitor near it. A hatchling
     * crocodile is too small to fear: it lies still in the shallows and snaps at barbs passing by.
     */
    static boolean isHunter(LivingEntity e) {
        return e instanceof CatfishEntity || e instanceof CrocodileEntity c && !c.life().isYoung() || e instanceof MonitorEntity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
    }

    /** {@link #NONE}, {@link #SIP} or {@link #NIBBLE}. */
    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    void setAction(byte action) {
        entityData.set(DATA_ACTION, action);
    }

    /** Its own length against the usual. */
    public float ownSize() {
        return getScale();
    }

    /** For tests: gives it this length. */
    public void setOwnSize(float size) {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(size);
        }
    }

    /**
     * A strike nearby (a catfish's lunge, a crocodile's snap): it darts straight away from where the hunter is with a
     * fast start, flashing as it goes; the school re-forms behind its leader.
     */
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

    /** Whether it flashes silver now (drawn with the bright flank): in a hard turn or a fast start (client only). */
    public boolean flashing() {
        String film = filmClip();
        return turnTicks > 0 || scatterTicks > 0 || film != null && (film.startsWith("turn") || film.equals("scatter"));
    }

    /**
     * For the in-game camera ({@code client/FilmDirector}, development only): a barb named {@code film:<clip>} plays
     * only that clip, looping, so every clip can be filmed frame by frame on a still fish. Null for every other barb.
     */
    @Nullable
    private String filmClip() {
        net.minecraft.network.chat.Component name = getCustomName();
        return name != null && name.getString().startsWith("film:") ? name.getString().substring(5) : null;
    }

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            return;
        }
        swimPitchO = swimPitch;
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float want = isInWater() && flat + Math.abs(dy) > 0.005 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -35, 35) : 0;
        swimPitch += (want - swimPitch) * 0.2F;
        // a hard turn: the body's turning speed, smoothed over a few ticks, past TURN_FROM
        float turned = Mth.wrapDegrees(yBodyRot - lastYaw);
        lastYaw = yBodyRot;
        turnRate = turnRate * 0.6F + turned * 0.4F;
        if (scatterTicks > 0) {
            scatterTicks--;
        }
        if (turnTicks > 0) {
            turnTicks--;
        } else if (turnRest > 0) {
            turnRest--;
        } else if (isInWater() && action() == NONE && Math.abs(turnRate) > TURN_FROM) {
            turnTicks = TURN_TICKS;
            turnRest = TURN_REST;
            turnRight = turnRate > 0;       // a yaw growing is a turn to the right (Minecraft's yaw runs clockwise from above)
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
        setOwnSize(0.85F + level.getRandom().nextFloat() * 0.3F);
        return out;
    }

    /** No barb bucket (yet): a water bucket does nothing to a barb. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Items.WATER_BUCKET);
    }

    /** Never idle for its wandering (the tilapia's reason: a player in spectator mode winds no idle clock back). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Kept near a player like the tilapia ({@link TilapiaEntity#KEEP_NEAR}); past 128 blocks always removed. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer) && distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    /** How far off a barb is still drawn (blocks; my pick: past this a quarter-block fish is a speck). */
    static final double DRAW_DISTANCE = 40.0;

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public int getMaxSchoolSize() {
        return 14;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TROPICAL_FISH_FLOP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TROPICAL_FISH_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TROPICAL_FISH_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TROPICAL_FISH_HURT;
    }

    /** How deep the water a barb spawns in may be at most (blocks; the shallows, my pick). */
    static final int SHALLOW = 3;

    /**
     * Where it may spawn: in shallow water (1 to {@link #SHALLOW} blocks deep) of the savanna's rivers, pans and oxbows,
     * from 13 under sea level up to 6 over it (the tilapia's band); the tilapia keep to the deeper beds.
     */
    public static boolean canSpawn(EntityType<BarbEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        // the water's depth here: water above and below this spot, counted, at most SHALLOW in all
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
    private static final RawAnimation TURN_LEFT = RawAnimation.begin().thenPlay("turn_left");
    private static final RawAnimation TURN_RIGHT = RawAnimation.begin().thenPlay("turn_right");
    private static final RawAnimation SIP_CLIP = RawAnimation.begin().thenPlay("sip");
    private static final RawAnimation NIBBLE_CLIP = RawAnimation.begin().thenLoop("nibble");
    private static final RawAnimation SCATTER = RawAnimation.begin().thenPlay("scatter");
    private static final RawAnimation STRUGGLE = RawAnimation.begin().thenLoop("scatter");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, 1 a second); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.05;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
    }

    private PlayState animate(AnimationState<BarbEntity> state) {
        AnimationController<BarbEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        String film = filmClip();
        if (film != null) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film, c -> RawAnimation.begin().thenLoop(c)));
        }
        if (getVehicle() != null) {         // in a crocodile's jaws: thrashing
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
        byte action = action();
        if (action == SIP) {
            return state.setAndContinue(SIP_CLIP);
        }
        if (action == NIBBLE) {
            return state.setAndContinue(NIBBLE_CLIP);
        }
        if (turnTicks > 0) {
            return state.setAndContinue(turnRight ? TURN_RIGHT : TURN_LEFT);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed < 0.006) {
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
