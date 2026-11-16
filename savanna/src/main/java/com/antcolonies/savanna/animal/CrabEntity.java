package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The river crab (M9 river life, the clean-up crew, 2026-11-15; the user's picture, model {@code tools/crab_models.py
 * game}). Generalized and fun first (the animals' rule). First step, all my picks:
 * <ul>
 * <li>It walks sideways over the river bed and out onto the banks (never far from the water), breathing in both.</li>
 * <li>Hungry, it walks to remains, a scraps film or algae near it, in that order, and picks at it: each claw in turn
 * pinches a bit off and carries it to its mouth, which opens ({@link CrabPickGoal}). It never starves.</li>
 * <li>It scuttles away from a player or a crocodile that comes near; with a player right on top of it, or hit, it rears
 * up with its claws raised and pinches (half a heart; {@link CrabThreatGoal}).</li>
 * <li>Now and then it hunts a river snail near it instead (2026-11-16, the user: "Yes, crabs eat snails"): it grabs the
 * snail, its claws squeeze the shell till it cracks, it picks the snail out into its mouth and leaves the broken shell
 * on the bed ({@link CrabPickGoal}, {@link com.antcolonies.savanna.block.CrabShellBlock}).</li>
 * </ul>
 * Not yet: its burrow (broken shells to gather outside it), catfish eating it.
 */
public class CrabEntity extends PathfinderMob implements GeoEntity {
    /** What it is doing, for the clips and the look. */
    public static final byte CALM = 0, PICK_ALGAE = 1, PICK_MEAT = 2, THREAT = 3, CRACK = 4;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(CrabEntity.class, EntityDataSerializers.BYTE);

    /** The model is made 0.82 blocks wide with its legs; drawn as made. */
    public static final float MODEL_SCALE = 1.0F;
    /** How far from the water it last stood in it may wander (blocks). */
    static final int NEAR_WATER = 8;
    /** Kept while a player is within this many blocks (as the fish are), gone past the group's 128. */
    static final double KEEP_NEAR = 96.0;
    /** How far off it is still drawn (blocks; Minecraft's own rule, by the box's size, gave about 20). */
    static final double DRAW_DISTANCE = 48.0;
    /** The walk clip's stride is made for this speed (blocks a tick); it plays faster or slower by the crab's speed. */
    static final double WALK_CLIP_SPEED = 0.06;

    /** Ticks until it is hungry again (0: hungry). */
    int hungryIn;
    private float ownSize = 1.0F;
    /** The body's turn against its heading (degrees): 90 to one side while it walks or stands, 0 facing what it picks at or threatens. Drawing only. */
    private float sideTurn, sideTurnO;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CrabEntity(EntityType<? extends CrabEntity> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, 0.0F);        // the bed is its ground: it walks under water as on land
        hungryIn = random.nextInt(600);
        sideTurn = sideTurnO = side() * 90.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 1.0).add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new CrabThreatGoal(this));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, CrocodileEntity.class, 6.0F, 1.2, 1.5));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.2, 1.5, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
        goalSelector.addGoal(4, new CrabPickGoal(this));
        // never stopped by Minecraft's "nothing happened for 5 s and no player within 32" rule (docs/BUGS.md 216)
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 100, false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DOING, CALM);
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        entityData.set(DATA_DOING, doing);
    }

    /** Which side leads when it walks sideways: its own, for good (1 or -1). */
    private int side() {
        return (getId() & 1) == 0 ? 1 : -1;
    }

    /** The body's turn against its heading (degrees), for the drawing. */
    public float sideTurn(float partialTick) {
        return Mth.lerp(partialTick, sideTurnO, sideTurn);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            sideTurnO = sideTurn;
            sideTurn = Mth.approach(sideTurn, doing() == CALM ? side() * 90.0F : 0.0F, 15.0F);
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (tickCount % 20 == 0 && isInWater()) {
            restrictTo(blockPosition(), NEAR_WATER);      // its strolls and flights stay near the water it was last in
        }
    }

    /** Faces a place at once, body and all (it has no head to turn). */
    void face(double x, double z) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        yaw = Mth.approachDegrees(getYRot(), yaw, 25.0F);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /**
     * Its spawn spot is water (as the crocodile's, BUGS 218): only blocks and other mobs stand in its way. A land mob's
     * own check refuses any spot with water in its box, so the world would never spawn a crab on the bed.
     */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Always wanders, as the river fish do (BUGS 216: Minecraft stops wandering with only a spectator near). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** It breathes in water as on land (NeoForge's socket; Minecraft's own breathing check cannot be overridden). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        ownSize = 0.85F + 0.3F * level.getRandom().nextFloat();
        refresh();
        return out;
    }

    private void refresh() {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(ownSize);
        }
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
        }
        hungryIn = tag.getInt("HungryIn");
        refresh();
    }

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /**
     * Where it may spawn: on the bed of the savanna's rivers, pans and oxbows, in water at most 4 deep (the shallows by
     * the banks), in the fish's height band.
     */
    public static boolean canSpawn(EntityType<CrabEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)
                || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
            return false;
        }
        return !level.getFluidState(pos.above(4)).is(FluidTags.WATER);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation PICK = RawAnimation.begin().thenLoop("pick");
    private static final RawAnimation THREAT_CLIP = RawAnimation.begin().thenLoop("threat");
    private static final RawAnimation CRACK_CLIP = RawAnimation.begin().thenPlayAndHold("crack");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<CrabEntity> state) {
        AnimationController<CrabEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        byte doing = doing();
        if (doing == THREAT) {
            return state.setAndContinue(THREAT_CLIP);
        }
        if (doing == CRACK) {
            return state.setAndContinue(CRACK_CLIP);
        }
        if (doing == PICK_ALGAE || doing == PICK_MEAT) {
            return state.setAndContinue(PICK);
        }
        double dx = getX() - xo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed > 0.004) {
            player.setAnimationSpeed(Mth.clamp(speed / WALK_CLIP_SPEED, 0.5, 2.5));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
