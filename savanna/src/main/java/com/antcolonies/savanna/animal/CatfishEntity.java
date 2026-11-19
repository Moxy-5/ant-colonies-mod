package com.antcolonies.savanna.animal;

import com.antcolonies.entity.body.SteadyController;
import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.SeekMateGoal;

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
 * and catches it in its mouth ({@link HuntTilapiaGoal}, {@link #hold}), shakes it and gulps it down; the school round
 * it scatters.
 *
 * <p>One look, A, grey-brown mottled (the user's pick). Each catfish has a length of its own, 0.85 to 1.15 of the usual
 * (my pick), drawn and boxed through {@link Attributes#SCALE}. It never starves (the balance rule): with no tilapia near
 * it swims on. Too big for a bucket (my pick). Out of water it stays upright and wriggles, as real catfish crawl over wet
 * ground between pools (it still dries out like any fish).
 */
public class CatfishEntity extends AbstractFish implements GeoEntity, HasLife {
    /** How long a strike is shown (ticks; the strike clip is 1 s, the mouth open from 0.05 to 0.38 s). */
    static final int STRIKE_TICKS = 20;

    /**
     * How long a caught tilapia is held in the open mouth before it is gone (ticks; the catch clip's 2.6 s,
     * {@code tools/catfish_models.py}), and the tick of the hold the last gulp is heard.
     */
    static final int HOLD_TICKS = 52, GULP_AT = 44;
    /**
     * The bite-off (the user, 2026-11-15): on this share of its catches the tilapia fights harder and harder (its clip
     * "caught_fight"), working itself a little way out, and the catfish lunges, takes a new hold and bites it through at
     * its middle; the front half falls away as remains ({@link RemainsEntity.Kind#TILAPIA_HALF}).
     * The clip "bite_off" is {@link #CUT_TICKS} long and its jaws close through the fish at {@link #CUT_AT_TICK}
     * ({@code tools/catfish_models.py}, CUT_AT and CUT_LEN, 30 and 42 ticks), each {@link #CLIP_LAG} later: the clip
     * starts once the animation player has blended into it, so the remains appear on the tick the jaws are seen to meet
     * (filmed, {@code tools/film/catch_fight.film}: without it the front half fell away from a mouth still open).
     */
    static final float BITE_OFF_CHANCE = 0.5F;
    static final int CLIP_LAG = 4, CUT_TICKS = 42 + CLIP_LAG, CUT_AT_TICK = 30 + CLIP_LAG;

    private static final EntityDataAccessor<Boolean> DATA_STRIKING = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.BOOLEAN);
    /** The look of the tilapia held in its mouth ({@link TilapiaEntity#look()}), or -1 with nothing held; and its size. */
    private static final EntityDataAccessor<Integer> DATA_HELD_LOOK = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HELD_SIZE = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.FLOAT);
    /** Whether the held tilapia lies on its right side (the catfish came at it from its right), else on its left. */
    private static final EntityDataAccessor<Boolean> DATA_HELD_ON_RIGHT = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.BOOLEAN);
    /** Whether this catch is a bite-off ({@link #BITE_OFF_CHANCE}): the mouth shuts early and the front half falls away. */
    private static final EntityDataAccessor<Boolean> DATA_HELD_CUT = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.BOOLEAN);
    /** A held tilapia drawn smaller than this (against a grown one) gets the tighter bite, the clip "catch_small". */
    static final float SMALL_PREY = 0.75F;
    /** Up at the surface for a gulp of air ({@link AirGulpGoal}): the clip "gulp" is 1.5 s, plus the blend in. */
    static final int GULP_TICKS = 30 + CLIP_LAG;
    private static final EntityDataAccessor<Boolean> DATA_GULPING = SynchedEntityData.defineId(CatfishEntity.class, EntityDataSerializers.BOOLEAN);
    /** How far the drawn body tips nose up for the gulp (degrees). */
    static final float GULP_PITCH = 35;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Its own length against the usual catfish. */
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(CatfishEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    /** Ticks until it is hungry again ({@link HuntTilapiaGoal}). */
    int hungryIn;
    /** Ticks until it comes up for air again ({@link AirGulpGoal}). */
    int gulpIn;
    /** Ticks left of the strike being shown. */
    private int striking;
    /** Ticks left of the caught tilapia being held in the mouth. */
    private int holding;
    /** The held tilapia as drawn (client only): a tilapia of the same look and size that is in no world. */
    @Nullable
    private TilapiaEntity heldDrawn;
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;

    public CatfishEntity(EntityType<? extends CatfishEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        hungryIn = 600 + level.random.nextInt(2400);
        gulpIn = 600 + level.random.nextInt(AirGulpGoal.EVERY_MORE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractFish.createAttributes().add(Attributes.MAX_HEALTH, 12.0).add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new HuntTilapiaGoal(this));
        goalSelector.addGoal(5, new SeekMateGoal<>(this, 1.0));
        goalSelector.addGoal(1, new AirGulpGoal(this));     // above the fish's fleeing from players (2), so it is seen
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_STRIKING, false);
        builder.define(DATA_HELD_LOOK, -1);
        builder.define(DATA_HELD_SIZE, 1.0F);
        builder.define(DATA_HELD_ON_RIGHT, false);
        builder.define(DATA_HELD_CUT, false);
        builder.define(DATA_GULPING, false);
    }

    /** Whether it is up at the surface gulping air. */
    public boolean isGulping() {
        return entityData.get(DATA_GULPING);
    }

    void setGulping(boolean gulping) {
        entityData.set(DATA_GULPING, gulping);
    }

    /** Whether it is out hunting a tilapia ({@link HuntTilapiaGoal}). */
    boolean isHunting() {
        return goalSelector.getAvailableGoals().stream().anyMatch(g -> g.isRunning() && g.getGoal() instanceof HuntTilapiaGoal);
    }

    /** For tests and films: comes up for air at the next chance. */
    public void makeWantAir() {
        gulpIn = 0;
    }

    /** Whether a caught tilapia is in its mouth. */
    public boolean isHolding() {
        return entityData.get(DATA_HELD_LOOK) >= 0;
    }

    /** Whether the tilapia in its mouth lies on its right side (the catfish came from its right), else on its left. */
    public boolean heldOnRight() {
        return entityData.get(DATA_HELD_ON_RIGHT);
    }

    /** The look of the tilapia in its mouth ({@link TilapiaEntity#look()}), or -1. */
    public int heldLook() {
        return entityData.get(DATA_HELD_LOOK);
    }

    /**
     * Takes a caught tilapia into its mouth (2026-11-15, the user's ask): the fish itself leaves the world
     * ({@link HuntTilapiaGoal}), and for {@link #HOLD_TICKS} the catfish holds its mouth open round a tilapia of the
     * same look and size, drawn with the tilapia's own model ({@code CatfishRenderer}): the jaws bite down on it and
     * stay clamped, it shakes it and gulps it down.
     */
    void hold(TilapiaEntity prey) {
        // which side of the tilapia the catfish came from (the user, 2026-11-15): from its right, it lies in the mouth
        // on its right side; from its left, on its left. Facing yaw 0 (+Z), a fish's right is -X.
        float yaw = prey.getYRot() * Mth.DEG_TO_RAD;
        double toRight = (getX() - prey.getX()) * -Mth.cos(yaw) + (getZ() - prey.getZ()) * -Mth.sin(yaw);
        entityData.set(DATA_HELD_ON_RIGHT, toRight > 0);
        entityData.set(DATA_HELD_SIZE, prey.getScale());
        entityData.set(DATA_HELD_LOOK, prey.look());
        boolean cut = getRandom().nextFloat() < BITE_OFF_CHANCE;
        entityData.set(DATA_HELD_CUT, cut);
        holding = cut ? CUT_TICKS : HOLD_TICKS;
    }

    /** Whether the catch in its mouth is a bite-off (the front half to fall away). */
    public boolean heldCut() {
        return entityData.get(DATA_HELD_CUT);
    }

    /** The jaws snap shut through the held tilapia: its front half falls away from the mouth as remains, head first. */
    private void biteOff(net.minecraft.server.level.ServerLevel server) {
        Vec3 m = mouth();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double ahead = 0.12 * entityData.get(DATA_HELD_SIZE);      // the half's middle, a little in front of the lips
        Vec3 at = new Vec3(m.x - Mth.sin(yaw) * ahead, m.y - 0.05 - 0.08 * entityData.get(DATA_HELD_SIZE), m.z + Mth.cos(yaw) * ahead);
        RemainsEntity left = RemainsEntity.leave(server, RemainsEntity.Kind.TILAPIA_HALF, at, getYRot(), entityData.get(DATA_HELD_SIZE), heldLook());
        if (left != null) {
            if (!heldOnRight()) {       // it falls away on the side it lay on in the mouth
                left.lieOnOtherSide();
            }
            left.setDeltaMovement(getDeltaMovement().scale(0.3).add(0, -0.02, 0));
        }
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, m.x, m.y, m.z, 6, 0.15, 0.1, 0.15, 0.03);
        server.playSound(null, m.x, m.y, m.z, SoundEvents.SLIME_SQUISH_SMALL, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.3F);
        com.antcolonies.savanna.block.ScrapsBlock.leave(server, m);     // flecks sinking to the bed
    }

    /**
     * The held tilapia to draw (client only), or none: a tilapia in no world, wearing the caught fish's look and size
     * and playing its "caught" clip, so what is drawn in the mouth is the very same model.
     */
    @Nullable
    public TilapiaEntity heldDrawn() {
        int look = heldLook();
        if (look < 0) {
            heldDrawn = null;
            return null;
        }
        if (heldDrawn == null) {
            heldDrawn = new TilapiaEntity(com.antcolonies.savanna.registry.SavannaEntities.TILAPIA.get(), level());
            heldDrawn.showCaught(look, entityData.get(DATA_HELD_SIZE), heldCut());
        }
        heldDrawn.tickCount = tickCount;
        return heldDrawn;
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
        return life.ownSize();
    }

    /** Where its mouth is: the front of the snout, about 0.85 blocks ahead of its middle at the usual size. */
    Vec3 mouth() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double reach = 0.85 * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * reach, getY() + 0.17 * getScale(), getZ() + Mth.cos(yaw) * reach);
    }

    private void refresh() {
        life.apply();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swimPitchO = swimPitch;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float want = isInWater() && flat + Math.abs(dy) > 0.01 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -25, 25) : 0;
            if (isGulping()) {
                want = GULP_PITCH;
            }
            swimPitch += (want - swimPitch) * (isGulping() ? 0.25F : 0.1F);
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (gulpIn > 0) {
            gulpIn--;
        }
        if (striking > 0 && --striking == 0) {
            entityData.set(DATA_STRIKING, false);
        }
        if (holding > 0) {
            holding--;
            if (heldCut() && holding == CUT_TICKS - CUT_AT_TICK && level() instanceof net.minecraft.server.level.ServerLevel server) {
                biteOff(server);
            }
            if (!heldCut() && holding == HOLD_TICKS - GULP_AT && level() instanceof net.minecraft.server.level.ServerLevel server) {
                Vec3 m = mouth();
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE, m.x, m.y, m.z, 8, 0.2, 0.15, 0.2, 0.04);
                server.playSound(null, m.x, m.y, m.z, SoundEvents.DOLPHIN_EAT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.6F);
                com.antcolonies.savanna.block.ScrapsBlock.leave(server, m);     // flecks sinking to the bed
            }
            if (holding == 0 || !isAlive()) {
                holding = 0;
                entityData.set(DATA_HELD_LOOK, -1);
            }
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
        life.setUp(false, level.getRandom());
        refresh();
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
        tag.putInt("GulpIn", gulpIn);
        if (holding > 0) {
            tag.putInt("HeldLook", heldLook());
            tag.putFloat("HeldSize", entityData.get(DATA_HELD_SIZE));
            tag.putInt("HeldTicks", holding);
            tag.putBoolean("HeldOnRight", heldOnRight());
            tag.putBoolean("HeldCut", heldCut());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        if (tag.contains("HungryIn")) {
            hungryIn = tag.getInt("HungryIn");
        }
        if (tag.contains("GulpIn")) {
            gulpIn = tag.getInt("GulpIn");
        }
        if (tag.getInt("HeldTicks") > 0) {
            entityData.set(DATA_HELD_SIZE, tag.contains("HeldSize") ? Mth.clamp(tag.getFloat("HeldSize"), 0.2F, 1.5F) : 1.0F);
            entityData.set(DATA_HELD_LOOK, Mth.clamp(tag.getInt("HeldLook"), TilapiaEntity.YOUNG, TilapiaEntity.BREEDING));
            holding = tag.getInt("HeldTicks");
            entityData.set(DATA_HELD_ON_RIGHT, tag.getBoolean("HeldOnRight"));
            entityData.set(DATA_HELD_CUT, tag.getBoolean("HeldCut"));
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
    /**
     * Never counts as idle for its wandering (2026-11-14, the user's report: after the catfish's strikes the tilapia
     * huddled in a corner and stood still). Minecraft lets a mob wander only while its idle clock is under 5 s, and
     * winds that clock back only while a player who is not in spectator mode is within 32 blocks; with the user
     * watching in spectator nothing wound it back, so the school's leader stopped wandering and its school sat by it (the catfish would too).
     * River fish always wander (cheap: at most 60 near a player). Only the wandering reads this; Minecraft's own
     * despawning still reads the real clock.
     */
    @Override
    public int getNoActionTime() {
        return 0;
    }

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
    private static final RawAnimation CATCH = RawAnimation.begin().thenPlayAndHold("catch");
    private static final RawAnimation CATCH_SMALL = RawAnimation.begin().thenPlayAndHold("catch_small");
    private static final RawAnimation BITE_OFF = RawAnimation.begin().thenPlayAndHold("bite_off");
    private static final RawAnimation BITE_OFF_SMALL = RawAnimation.begin().thenPlayAndHold("bite_off_small");
    private static final RawAnimation GULP = RawAnimation.begin().thenPlayAndHold("gulp");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, about 1 a second); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.05;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
    }

    private PlayState animate(AnimationState<CatfishEntity> state) {
        AnimationController<CatfishEntity> player = state.getController();
        if (isDeadOrDying()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(DEATH);
        }
        if (!isInWater() || getVehicle() instanceof CrocodileEntity) {     // out of the water, or thrashing in a crocodile's jaws
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(FLOP);
        }
        if (isHolding()) {
            player.setAnimationSpeed(1.0);
            boolean small = entityData.get(DATA_HELD_SIZE) < SMALL_PREY;
            return state.setAndContinue(heldCut() ? (small ? BITE_OFF_SMALL : BITE_OFF) : small ? CATCH_SMALL : CATCH);
        }
        if (isStriking()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(STRIKE);
        }
        if (isGulping()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(GULP);
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
