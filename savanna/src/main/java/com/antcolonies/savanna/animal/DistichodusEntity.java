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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
 * The six-banded distichodus (M9 river life; the river food web's step 5, 2026-11-23, {@code docs/plans/distichodus.md};
 * generalized, inspired by the real big plant-eating fish of central African rivers; animals are fun first): a deep,
 * flat-sided peach-gold fish with six straight dark bands and red fins, 0.8 blocks long grown, in loose groups of 2 to 4
 * along the slower edges of the near and mid-inland rivers and the oxbows.
 *
 * <p>The river's big plant eater, with no hunger (fewer rules, as the barb): it grazes hippo grass mats from under them
 * and nips young reed shoots at the stem's foot, picking at algae for looks with no plants near ({@link
 * DistichodusGrazeGoal}); a bite takes at most a mat's outermost block, by the tilapia's rule, and each fish bites at most
 * once every 2 minutes, so a group cannot strip a bed. A big one now and then flares its fins at a smaller one on its
 * patch and chases it off (looks only).
 *
 * <p>Danger: a strike near it or a player swimming near sends it bolting into the reeds or under a mat ({@link
 * HideInCoverGoal}, the food web's step 4); a hard scare (a hit, a strike right beside it) sends it leaping clear of
 * the water first ({@link #leap}). Eaten by teen and grown crocodiles and the bull shark, as a tilapia; too big for the
 * catfish. Each fish has its own size, 0.85 to 1.15 of the usual (my pick).
 */
public class DistichodusEntity extends AbstractSchoolingFish implements GeoEntity, RiverCover.Hider {
    /** What it is doing to be seen (synced). */
    public static final byte NONE = 0, GRAZE = 1, NIBBLE = 2, BOLT = 3, LEAP = 4, FLARE = 5;
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(DistichodusEntity.class, EntityDataSerializers.BYTE);
    /** The clips' lengths (ticks): the bolt 0.45 s, the leap 1 s, the flare 1.4 s. */
    static final int BOLT_TICKS = 9, LEAP_TICKS = 20, FLARE_TICKS = 28;
    /** A leap's throw: up and away from the scare (blocks a tick, added once; my picks, about a block and a half clear). */
    static final double LEAP_UP = 0.55, LEAP_AWAY = 0.3;
    /** It leaps only from this near the surface (blocks of water over its back; deeper it bolts). */
    static final int LEAP_FROM_DEPTH = 2;
    /** The share of strikes right beside it that make it leap (the rest only bolt; my pick). */
    static final float LEAP_ON_STRIKE = 0.5F;
    /** How long before it can leap again (ticks). */
    static final int LEAP_REST = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Its hiding in cover (made on first use: Minecraft sets up the goals before this class's own fields are filled in). */
    private RiverCover.Hiding hiding;
    /** Ticks the bolt, leap or flare still shows for (server). */
    private int actionLeft;
    /** No leap before this (game time). */
    private long leapAgain;
    /** No bite off a hippo grass mat before this (game time; {@link DistichodusGrazeGoal#BITE_EVERY}). */
    long nextBite;
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;

    public DistichodusEntity(EntityType<? extends DistichodusEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 6.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new HideInCoverGoal(this, () -> true));     // scared: into the reeds or under a mat
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, BullSharkEntity.class, BullSharkEntity.FISH_PART_FROM, 1.2, 1.5, BullSharkEntity::partsFish));
        goalSelector.addGoal(3, new DistichodusGrazeGoal(this));     // over Minecraft's fish swimming (4)
    }

    @Override
    public RiverCover.Hiding hiding() {
        if (hiding == null) {
            hiding = new RiverCover.Hiding();
        }
        return hiding;
    }

    /** Off into cover: the C-start bolt plays. */
    @Override
    public void startedDash() {
        if (action() != LEAP) {
            show(BOLT, BOLT_TICKS);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
    }

    /** {@link #NONE}, {@link #GRAZE}, {@link #NIBBLE}, {@link #BOLT}, {@link #LEAP} or {@link #FLARE}. */
    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    /** Shows a grazing or nibbling action until it is set back ({@link #NONE}). */
    void setAction(byte action) {
        if (actionLeft <= 0 || action != NONE) {
            entityData.set(DATA_ACTION, action);
        }
    }

    /** Shows a bolt, leap or flare for this many ticks. */
    void show(byte action, int ticks) {
        entityData.set(DATA_ACTION, action);
        actionLeft = ticks;
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
     * A hard scare from {@code from}: near the surface it leaps clear of the water, up and away (real distichodus are
     * strong jumpers), and is scared into cover after; deeper, or just after a leap, it only bolts. Whether it leapt.
     */
    public boolean leap(Vec3 from) {
        hiding().scare(level().getGameTime());
        if (!isInWater() || isPassenger() || level().getGameTime() < leapAgain || waterOver() > LEAP_FROM_DEPTH) {
            return false;
        }
        Vec3 away = position().subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) {
            away = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        away = away.normalize();
        setDeltaMovement(away.x * LEAP_AWAY, LEAP_UP, away.z * LEAP_AWAY);
        setYRot((float) Math.toDegrees(Math.atan2(away.z, away.x)) - 90.0F);
        yBodyRot = getYRot();
        hurtMarked = true;
        getNavigation().stop();
        show(LEAP, LEAP_TICKS);
        leapAgain = level().getGameTime() + LEAP_REST;
        playSound(SoundEvents.FISH_SWIM, 0.8F, 0.8F);
        return true;
    }

    /** Blocks of water over it (0 at the surface). */
    private int waterOver() {
        BlockPos.MutableBlockPos at = blockPosition().mutable();
        int over = 0;
        while (over <= LEAP_FROM_DEPTH && level().getFluidState(at.move(0, 1, 0)).is(FluidTags.WATER)) {
            over++;
        }
        return over;
    }

    /** Hit: it leaps from whatever hit it (a scare too hard for a bolt). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && !level().isClientSide) {
            Entity by = source.getEntity();
            leap(by != null ? by.position() : position().add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5));
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (actionLeft > 0 && --actionLeft == 0) {
                entityData.set(DATA_ACTION, NONE);
            }
            return;
        }
        swimPitchO = swimPitch;
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float want = isInWater() && action() == NONE && flat + Math.abs(dy) > 0.005 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -30, 30) : 0;
        swimPitch += (want - swimPitch) * 0.2F;
    }

    /** The drawn body's pitch this frame (degrees, nose up positive); none while it leaps (the leap clip pitches it itself). */
    public float swimPitch(float partialTick) {
        return action() == LEAP ? 0 : Mth.lerp(partialTick, swimPitchO, swimPitch);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        setOwnSize(0.85F + level.getRandom().nextFloat() * 0.3F);
        return out;
    }

    /** No distichodus bucket: a water bucket does nothing to it. */
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

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = TilapiaEntity.DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    /** Loose groups of 2 to 4. */
    @Override
    public int getMaxSchoolSize() {
        return 4;
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

    /** The water a column must hold for it to spawn there (blocks deep; the slower edges, 2 to 4). */
    static final int SHALLOWEST = 2, DEEPEST = 4;

    /**
     * Where it may spawn: in water 2 to 4 blocks deep (counted over the whole column, so any height the spawner draws in
     * it will do) of the savanna's rivers and oxbows, from 13 under sea level up to 6 over it (the river fish's band).
     * The river zones keep it to the near and mid-inland rivers and the oxbows.
     */
    public static boolean canSpawn(EntityType<DistichodusEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        int depth = 1;
        for (int up = 1; depth <= DEEPEST && level.getFluidState(pos.above(up)).is(FluidTags.WATER); up++) {
            depth++;
        }
        for (int down = 1; depth <= DEEPEST && level.getFluidState(pos.below(down)).is(FluidTags.WATER); down++) {
            depth++;
        }
        return depth >= SHALLOWEST && depth <= DEEPEST;
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation NIBBLE_CLIP = RawAnimation.begin().thenLoop("nibble");
    private static final RawAnimation BOLT_CLIP = RawAnimation.begin().thenPlay("bolt");
    private static final RawAnimation LEAP_CLIP = RawAnimation.begin().thenPlay("leap");
    private static final RawAnimation FLARE_CLIP = RawAnimation.begin().thenPlay("flare");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, 1.2 a second; my pick); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.06;
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();

    /**
     * For the in-game camera ({@code client/FilmDirector}, development only): a distichodus named {@code film:<clip>}
     * plays only that clip, looping, so every clip can be filmed frame by frame on a still fish. Null for every other.
     */
    @Nullable
    private String filmClip() {
        net.minecraft.network.chat.Component name = getCustomName();
        return name != null && name.getString().startsWith("film:") ? name.getString().substring(5) : null;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 6, this::animate));      // 6 ticks: a big fish tips into its nose-down nibble slowly (3 snapped, the offline check)
    }

    private PlayState animate(AnimationState<DistichodusEntity> state) {
        AnimationController<DistichodusEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        String film = filmClip();
        if (film != null) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film, c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (getVehicle() != null) {         // in a crocodile's jaws: thrashing
            return state.setAndContinue(FLOP);
        }
        byte action = action();
        // the quick moves cut in fast (3 ticks, clean in the offline check); every other change eases over 6
        player.transitionLength(action == BOLT || action == LEAP ? 3 : 6);
        if (action == LEAP) {
            return state.setAndContinue(LEAP_CLIP);
        }
        if (!isInWater()) {
            return state.setAndContinue(FLOP);
        }
        switch (action) {
            case BOLT -> {
                return state.setAndContinue(BOLT_CLIP);
            }
            case FLARE -> {
                return state.setAndContinue(FLARE_CLIP);
            }
            case GRAZE -> {
                return state.setAndContinue(GRAZE_CLIP);
            }
            case NIBBLE -> {
                return state.setAndContinue(NIBBLE_CLIP);
            }
            default -> { }
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
