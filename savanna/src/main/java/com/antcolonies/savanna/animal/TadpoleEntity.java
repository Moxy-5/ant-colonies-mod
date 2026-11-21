package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.AnimalLives;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.LifeNumbers;
import com.antcolonies.animal.LifeStage;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
 * The African bullfrog's tadpole (prey plan step 4, 2026-11-21; animals are fun first, inspired by the real bullfrog's
 * dense black schools; the user's approved model, {@code tools/tadpole_models.py}): hatched from its father's egg film
 * ({@code FrogEggsBlock}), 0.15 blocks long when grown. It schools with its clutch mates near where it hatched (its
 * father guards the spot), grazes the algae mats nose down (now and then taking a bite: the link algae, tadpole, frog),
 * and now and then rises to gulp air. Its life ({@code animal_life/tadpole.json}): hatched at 0.7 of its length, the
 * hind legs out from day 3, and from day 4 it turns into a froglet (a teen {@link BullfrogEntity}) as soon as the
 * bullfrogs have room there (their local limit and their group's cap round a player, the user's 4): until then it
 * stays a tadpole, so frogs never crowd. Eaten by catfish and young crocodiles. Never spawned by the world.
 */
public class TadpoleEntity extends AbstractSchoolingFish implements GeoEntity, HasLife {
    /** What it is doing to be seen (synced). */
    public static final byte NONE = 0, GRAZE = 1, GULP = 2;
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(TadpoleEntity.class);
    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(TadpoleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_LEGS = SynchedEntityData.defineId(TadpoleEntity.class, EntityDataSerializers.BOOLEAN);
    /** The hind legs come out on this day of its life (the plan's day 3). */
    static final double LEGS_DAY = 3.0;
    /** How far it strays from where it hatched (blocks; my pick): the school keeps by its father's spot. */
    static final int STAY = 6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final AnimalLife life;
    private boolean keptNear;

    public TadpoleEntity(EntityType<? extends TadpoleEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(4, new FeedGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_LEGS, false);
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    void setAction(byte action) {
        entityData.set(DATA_ACTION, action);
    }

    /** Whether its hind legs are out (day 3 on). */
    public boolean hasLegs() {
        return entityData.get(DATA_LEGS);
    }

    @Override
    public int getMaxSchoolSize() {
        return 12;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (life.tick() || tickCount % 20 == 0) {
            entityData.set(DATA_LEGS, life.age() >= LEGS_DAY * LifeNumbers.DAY);
            if (!keptNear) {
                restrictTo(blockPosition(), STAY);
                keptNear = true;
            }
            if (life.stage() == LifeStage.GROWN && isInWater() && level() instanceof ServerLevel server && frogsHaveRoom(server)) {
                turnIntoFroglet(server);
            }
        }
    }

    /** Whether one more bullfrog fits here: fewer than their local limit near, and their spawning group under its cap. */
    private boolean frogsHaveRoom(ServerLevel level) {
        EntityType<BullfrogEntity> kind = SavannaEntities.BULLFROG.get();
        int near = level.getEntities(kind, getBoundingBox().inflate(RoomToBreed.REACH), Entity::isAlive).size();
        return near < AnimalLives.get(kind).localLimit() && RoomToBreed.groupHasRoom(level, kind.getCategory(), new ChunkPos(blockPosition()));
    }

    /** It turns: a froglet (a teen bullfrog, 0.25 blocks) here in its place, with a few bubbles. */
    public void turnIntoFroglet(ServerLevel level) {
        BullfrogEntity frog = SavannaEntities.BULLFROG.get().create(level);
        if (frog == null) {
            return;
        }
        frog.moveTo(getX(), getY(), getZ(), getYRot(), 0);
        frog.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.CONVERSION, null);
        frog.life().setUp(false, level.getRandom());
        frog.life().setAge(0);
        level.addFreshEntity(frog);
        level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.1, getZ(), 6, 0.1, 0.05, 0.1, 0.02);
        discard();
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        life.setUp(true, level.getRandom());
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
    }

    /** No tadpole bucket: a water bucket does nothing to it. */
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

    /** Kept near a player as the river fish are; past 128 blocks always removed. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer) && distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.TADPOLE_FLOP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TADPOLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TADPOLE_DEATH;
    }

    // --- its meals, for looks and a little algae -------------------------------------------------------------------

    /**
     * Now and then (about every 15 s) it noses down onto an algae mat within a few blocks and grazes a few seconds, one
     * graze in {@link #BITE_ONE_IN} taking a bite off the mat (so a school never strips one); now and then (about every
     * minute) it rises to gulp air at the surface right above it. My picks.
     */
    static final class FeedGoal extends Goal {
        static final int GRAZE_CHANCE = 150, GULP_CHANCE = 600, BITE_ONE_IN = 6, LOOK = 4, GRAZE_TICKS = 64, GULP_TICKS = 24,
                GIVE_UP = 100;
        private final TadpoleEntity tadpole;
        @Nullable
        private Vec3 spot;
        @Nullable
        private BlockPos mat;
        private byte kind;
        private int ticks, eating;

        FeedGoal(TadpoleEntity tadpole) {
            this.tadpole = tadpole;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!tadpole.isInWater()) {
                return false;
            }
            if (tadpole.getRandom().nextInt(GULP_CHANCE) == 0) {
                spot = surfaceAbove();
                kind = GULP;
                mat = null;
            } else if (tadpole.getRandom().nextInt(GRAZE_CHANCE) == 0) {
                mat = algaeNear();
                spot = mat == null ? null : new Vec3(mat.getX() + 0.5, mat.getY() + 0.05, mat.getZ() + 0.5);
                kind = GRAZE;
            } else {
                return false;
            }
            return spot != null;
        }

        @Override
        public void start() {
            ticks = 0;
            eating = 0;
            tadpole.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return spot != null && tadpole.isInWater() && ticks < GIVE_UP + GRAZE_TICKS;
        }

        @Override
        public void stop() {
            tadpole.setAction(NONE);
            tadpole.getNavigation().stop();
            spot = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (spot == null) {
                return;
            }
            ticks++;
            if (eating > 0) {
                tadpole.setDeltaMovement(tadpole.getDeltaMovement().scale(0.6));
                if (kind == GULP && eating == 8 && tadpole.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.BUBBLE_POP, spot.x, Math.floor(spot.y) + 0.95, spot.z, 2, 0.05, 0.0, 0.05, 0.0);
                }
                if (++eating > (kind == GULP ? GULP_TICKS : GRAZE_TICKS)) {
                    if (kind == GRAZE && mat != null && tadpole.getRandom().nextInt(BITE_ONE_IN) == 0) {
                        AlgaeBlock.graze(tadpole.level(), mat);
                    }
                    spot = null;
                }
                return;
            }
            double dx = spot.x - tadpole.getX(), dy = spot.y - tadpole.getY(), dz = spot.z - tadpole.getZ();
            double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (far < 0.3) {
                tadpole.getNavigation().stop();
                tadpole.setAction(kind);
                eating = 1;
                return;
            }
            if (ticks > GIVE_UP) {
                spot = null;
                return;
            }
            if (far < 1.5) {
                tadpole.getNavigation().stop();         // the last stretch straight there (a fish's path stops short)
                double push = 0.015 / Math.max(far, 0.05);
                tadpole.setDeltaMovement(tadpole.getDeltaMovement().scale(0.8).add(dx * push, dy * push, dz * push));
                if (dx * dx + dz * dz > 0.01) {
                    float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                    tadpole.setYRot(Mth.approachDegrees(tadpole.getYRot(), yaw, 20.0F));
                    tadpole.yBodyRot = tadpole.getYRot();
                }
            } else if (ticks % 20 == 0 || tadpole.getNavigation().isDone()) {
                tadpole.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            }
        }

        @Nullable
        private Vec3 surfaceAbove() {
            Level level = tadpole.level();
            BlockPos at = tadpole.blockPosition();
            for (int up = 0; up <= 3; up++) {
                BlockPos p = at.above(up);
                if (!level.getFluidState(p).is(FluidTags.WATER)) {
                    return null;
                }
                if (level.getBlockState(p.above()).isAir()) {
                    return new Vec3(tadpole.getX(), p.getY() + 0.9 - tadpole.getBbHeight(), tadpole.getZ());
                }
            }
            return null;
        }

        @Nullable
        private BlockPos algaeNear() {
            Level level = tadpole.level();
            BlockPos home = tadpole.blockPosition();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-LOOK, -3, -LOOK), home.offset(LOOK, 1, LOOK))) {
                if (!level.getBlockState(p).is(SavannaBlocks.ALGAE.get()) || level.getBlockState(p).getValue(AlgaeBlock.GROWTH) == AlgaeBlock.BARE) {
                    continue;
                }
                double d = p.distSqr(home);
                if (d < bestD) {
                    bestD = d;
                    best = p.immutable();
                }
            }
            return best;
        }
    }

    // --- animations ------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation GULP_CLIP = RawAnimation.begin().thenPlay("gulp");
    private static final RawAnimation SCATTER = RawAnimation.begin().thenPlay("scatter");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's wobble is made for this speed (blocks a tick; my pick); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.03;
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
    }

    private PlayState animate(AnimationState<TadpoleEntity> state) {
        AnimationController<TadpoleEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component name = getCustomName();
        if (name != null && name.getString().startsWith("film:")) {     // the film tool's still tadpole, one clip
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(name.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (!isInWater()) {
            return state.setAndContinue(FLOP);
        }
        if (hurtTime > 0) {
            return state.setAndContinue(SCATTER);
        }
        byte action = action();
        if (action == GRAZE) {
            return state.setAndContinue(GRAZE_CLIP);
        }
        if (action == GULP) {
            return state.setAndContinue(GULP_CLIP);
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
