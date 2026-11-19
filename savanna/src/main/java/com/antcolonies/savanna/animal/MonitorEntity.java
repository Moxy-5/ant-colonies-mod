package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.SeekMateGoal;

import com.antcolonies.entity.body.GroundBend;
import com.antcolonies.entity.body.Footing;
import com.antcolonies.savanna.block.CrabShellBlock;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
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
 * The Nile monitor (M9 river life, into the game 2026-11-17, the user: "add him now"; model {@code tools/monitor_models.py
 * game}). Generalized and fun first (the animals' rule); every number my pick. A bank animal, where the crocodile is a
 * water one:
 * <ul>
 * <li>It walks the banks near the water, swims across or floats at the surface with its head and back out, and never
 * strays far from the water it was last in.</li>
 * <li>By day, dry, it lies flat on the bank in the sun 1 to 3 minutes; at night it lies still and rests.</li>
 * <li>Hungry, it hunts what the river leaves it: a crab (swallowed whole), a snail (crushed, the broken shell left on the
 * bed) or remains (three bites), whichever is nearest within {@link #HUNT_REACH} blocks ({@link HuntGoal}).</li>
 * <li>A survival player coming near on land makes it stand tall and hiss; coming right up to it, it bites (1.5 hearts) and
 * runs; hit, it bites back if the hitter is at its jaws, then runs for the water and dives ({@link ThreatGoal},
 * {@link FleeGoal}). It keeps away from crocodiles.</li>
 * </ul>
 * Not yet: raiding crocodile nests and its own eggs (they come with the crocodile's nests), burrows.
 */
public class MonitorEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, BASK = 1, HISS = 2, BITE = 3, EAT = 4, WATCH = 5, BITE_PLAYER = 6;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(MonitorEntity.class, EntityDataSerializers.BYTE);

    /**
     * The model is made 6 blocks long, nose to tail tip (the user's length "for now"); drawn and boxed at this share of
     * that. 0.6 would make it 3.6 blocks.
     */
    public static final float MODEL_SCALE = 0.75F;
    /** How far its jaws reach ahead of its middle (blocks, at size 1): the snout's tip is 3 blocks out on a 6-block monitor. */
    static final double JAWS_AHEAD = 2.9 * MODEL_SCALE;
    /** How far it looks for food (blocks). */
    static final double HUNT_REACH = 12.0;
    /** How near a survival player makes it hiss (blocks from its jaws; crouching, nearer). */
    static final double HISS_REACH = 4.0, HISS_REACH_CROUCHING = 2.0;
    /** How near a player at its jaws gets bitten (blocks from the snout's tip). */
    static final double BITE_REACH = 1.3 * MODEL_SCALE;
    /** How hard it bites (1.5 hearts). */
    static final float BITE_DAMAGE = 3.0F;
    /** How far from the water it last swam in it may wander (blocks). */
    static final int NEAR_WATER = 16;
    /** Kept while a player is within this many blocks (as the river animals are), gone past the group's 128. */
    static final double KEEP_NEAR = 96.0;
    static final double DRAW_DISTANCE = 64.0;
    /** No world-spawned monitor within this many blocks of another: each its own stretch of river (80; 48 until the user asked for rarer, 2026-11-17). */
    public static final int SPAWN_APART = 80;
    /** How far from a bank the world may spawn one in the water (blocks). */
    public static final int BANK_REACH = 3;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): head and back out. */
    static final double AFLOAT = 0.75 * MODEL_SCALE;
    /** Startled in the water, it sinks out of sight to this depth (blocks). */
    static final double HIDE_DEPTH = 1.6 * MODEL_SCALE;
    static final int STARTLED_TICKS = 300;
    /** The walk clip's stride (0.86 blocks a 1.2 s step) is made for this speed (blocks a tick, at size 1). */
    static final double WALK_CLIP_SPEED = 0.036 * MODEL_SCALE;
    /** Ticks into the bite clip its jaws snap shut (0.28 s). */
    static final int SNAP_TICK = 6, BITE_TICKS = 14, EAT_TICKS = 40;
    /** The bite at a player ({@code bite_player}, 1.4 s): the jaws clamp shut at 0.42 s, then the head shake. */
    static final int SNAP_PLAYER_TICK = 8, BITE_PLAYER_TICKS = 28;
    /** A player's bite reaches a little farther: the lunge throws the head forward (blocks). */
    static final double BITE_PLAYER_REACH = BITE_REACH + 0.4;
    /** Curious (2026-11-17, the user's ask): a survival player within WATCH_REACH blocks is watched, and trailed back to TRAIL_NEAR if farther than TRAIL_FAR. */
    static final double WATCH_REACH = 14.0, TRAIL_FAR = 8.0, TRAIL_NEAR = 6.0;

    int hungryIn;
    int sunIn;
    /** A hunt is on: the hunt steers it in the water, not the float at the surface. */
    boolean hunting;
    /** A hunt that ends with nothing caught (given up, the prey gone, startled): it hunts again after this many ticks (a minute, as the catfish). */
    static final int REST_AFTER_GIVING_UP = 1200;
    int startled;
    /** Ticks before it may hiss again. */
    int hissWait;
    /** Ticks before it takes an interest in a player again. */
    int watchWait;
    /** Where its head and eyes are turned (degrees from its heading) and when last drawn: drawing only (MonitorModel). */
    public double headAim, eyeAim;
    public long lookNanos;
    /**
     * The joints along its middle line the body bends at to lie on what is under it ({@link GroundBend}, as the
     * crocodile's; model z, pixels, from {@code tools/monitor_models.py}; 2026-11-18, the user's ask: over a step or a
     * bank's edge the body curves instead of tipping like a plank, most of all going into the river): the anchor at the
     * body's middle, where chest and hips turn; forward through the chest, the two neck bones and the head to the snout
     * tip; back through the hips and the twelve tail bones to the tail tip.
     */
    public static final double[] FRONT_JOINTS = {-13, -17.5, -22, -26, -30, -34, -47},
            REAR_JOINTS = {-13, 0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48};
    /** The bones of those pieces, in the same order: front chain, then rear chain. */
    public static final String[] BENT_BONES = {"waist", "ribs", "chest", "neck1", "neck2", "head", "hips", "tail1", "tail2", "tail3", "tail4",
            "tail5", "tail6", "tail7", "tail8", "tail9", "tail10", "tail11", "tail12"};
    /** Where each leg pair's feet stand along the body (model z, pixels): the forelegs, then the hind legs. */
    public static final double[] LEG_PAIRS = {-25, -3};
    /** How much each leg pair stands now (1) or is folded back along the body as it swims (0), eased; drawing only (MonitorModel). */
    public final double[] legStand = {1, 1};
    public long legNanos;
    /**
     * Where its feet stand, held in the world (the Animal Maker Engine step 2, feet first, 2026-11-19); drawing only, made
     * by {@code MonitorModel} on first draw (it needs the model's rest pose).
     */
    @Nullable
    public Footing footing;
    /**
     * Both leg pairs are folded this far (stand under this) before the swim clip plays (2026-11-18, the user's report: going
     * into the water the hind legs swung up over the back for a moment). Until then the walk or idle clip plays and the
     * model folds each pair itself as its feet lose the ground, so the clip never switches under a leg still on the bank.
     */
    public static final double FOLDED = 0.05;
    /**
     * Going into the water (2026-11-18, the user's ask): the "enter" clip's length (ticks; {@code ENTER_LEN} in
     * {@code tools/monitor_models.py}), played once from {@link #enterTick}, which the model sets (drawing only) as the
     * forelegs lose the bank over water while the hind legs still stand: it leans out, shoves off with the hind legs and
     * folds into the swim.
     */
    public static final int ENTER_TICKS = 24;
    public int enterTick = -1000;
    /**
     * The front half is three pieces (waist, ribs, chest: the first {@link #TRUNK_PIECES} of {@link #BENT_BONES}), each
     * bending at most {@link #TRUNK_BEND} degrees against the one behind (my picks, 2026-11-18, the user's reports: one
     * chest bending far made a peak in the back going down a bank, held to a small bend it looked rigid), so the body
     * curves; the neck and head take the rest of a dip.
     */
    public static final int TRUNK_PIECES = 3;
    public static final double TRUNK_BEND = 15.0;
    private final GroundBend bend = new GroundBend(FRONT_JOINTS, REAR_JOINTS, TRUNK_PIECES, TRUNK_BEND);
    /** Who just hurt it, to bite back if at its jaws. */
    @Nullable
    LivingEntity biteBack;
    /** What startled it last (to run from where there is no water). */
    @Nullable
    Entity scare;
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(MonitorEntity.class);
    /** Its life: sex, stage, own size (the animal life kit). */
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MonitorEntity(EntityType<? extends MonitorEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.6F, false);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        hungryIn = random.nextInt(1200);
        sunIn = random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeGoal(this));
        goalSelector.addGoal(1, new ThreatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, CrocodileEntity.class, 8.0F, 1.0, 1.5));
        goalSelector.addGoal(2, new HuntGoal(this));
        goalSelector.addGoal(3, new WatchGoal(this));
        goalSelector.addGoal(4, new BaskGoal(this));
        // never stopped by Minecraft's "nothing happened for 5 s and no player within 32" rule (docs/BUGS.md 216)
        goalSelector.addGoal(5, new SeekMateGoal<>(this, 0.8));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8, 100, false));
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_DOING, CALM);
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        entityData.set(DATA_DOING, doing);
    }

    /** Where its jaws close: the snout's tip, a little over the ground. */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * life.ownSize();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + 0.5 * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** Faces a place, body and head (a little at a time). */
    void face(double x, double z, float step) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, step);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Whether its jaws are at an entity (its box within reach of the snout's tip). */
    boolean atJaws(Entity e, double reach) {
        return e.getBoundingBox().inflate(reach).contains(jawPoint());
    }

    public void startle(@Nullable Entity by) {
        startled = STARTLED_TICKS;
        scare = by;
        sunIn = Math.max(sunIn, 600 + random.nextInt(600));
        if (doing() == BASK || doing() == HISS || doing() == WATCH) {
            setDoing(CALM);
        }
    }

    public boolean isStartled() {
        return startled > 0;
    }

    /** In water it swims where it points, weightless; against a bank with room above it, up and out (as the crocodile). */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), travelVector);
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
            if (horizontalCollision && isFree(v.x, v.y + 0.6 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.3, v.z);
            }
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (sunIn > 0) {
            sunIn--;
        }
        if (hissWait > 0) {
            hissWait--;
        }
        if (watchWait > 0) {
            watchWait--;
        }
        if (startled > 0 && --startled == 0) {
            scare = null;
        }
        if (tickCount % 20 == 0 && isInWater()) {
            restrictTo(blockPosition(), NEAR_WATER);       // its strolls stay near the water it last swam in
        }
        // resting in the water it hangs at the surface, head and back out (startled, it dives out of sight); hunting, the hunt steers it
        if (isInWater() && getNavigation().isDone() && !hunting) {
            double depth = getFluidHeight(FluidTags.WATER) - (startled > 0 ? HIDE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, -0.02, 0.03) + v.y * 0.5, v.z);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            Entity by = source.getEntity();
            if (by instanceof LivingEntity living && !(living instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))
                    && random.nextFloat() < 0.6F) {
                biteBack = living;
            }
            startle(by);
        }
        return hurt;
    }

    /** Bites: the clip, and the bite counts when the jaws shut (the goals call {@link #snapAt} then). */
    void biteAt(LivingEntity target) {
        double reach = target instanceof Player ? BITE_PLAYER_REACH : BITE_REACH;
        if (target.isAlive() && atJaws(target, reach) && target.hurt(damageSources().mobAttack(this), BITE_DAMAGE)) {
            playSound(SoundEvents.FOX_BITE, 1.0F, 0.7F);
        }
    }

    /** The bite's clip for this target: a player gets the lunge, clamp and head shake ({@code bite_player}). */
    static byte biteKind(Entity target) {
        return target instanceof Player ? BITE_PLAYER : BITE;
    }

    static int biteTicks(byte kind) {
        return kind == BITE_PLAYER ? BITE_PLAYER_TICKS : BITE_TICKS;
    }

    static int snapTick(byte kind) {
        return kind == BITE_PLAYER ? SNAP_PLAYER_TICK : SNAP_TICK;
    }

    /** Biting, either kind. */
    public boolean isBiting() {
        return doing() == BITE || doing() == BITE_PLAYER;
    }

    /** It breathes in water as on land (NeoForge's socket). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** Its spawn spot is water by a bank (as the crocodile's, BUGS 218): only blocks and mobs stand in its way. */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
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
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            BlockPos bank = bankNear(level, blockPosition(), BANK_REACH);      // spawned in the water by a bank: onto the bank
            if (bank != null) {
                moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            }
        }
        life.setUp(false, level.getRandom());
        refresh();
        return out;
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** Its body's lie on what is under it, worked out and drawn by its model only. */
    public GroundBend bend() {
        return bend;
    }

    /** How deep its feet hang under the surface when it rests afloat (blocks; the drawing's guess: the client does not know if it was startled). */
    public double floatDepth() {
        return AFLOAT * getScale();
    }

    private void refresh() {
        life.apply();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
        tag.putInt("SunIn", sunIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        hungryIn = tag.getInt("HungryIn");
        sunIn = tag.getInt("SunIn");
        refresh();
    }

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /** Wants the sun now (for tests and the film camera). */
    public void wantSun() {
        sunIn = 0;
    }

    /**
     * Where it may spawn: in the water of the savanna's rivers, pans and oxbows within {@link #BANK_REACH} blocks of a bank
     * it can climb onto, in the river animals' height band, and (spawned by the world) no other monitor within
     * {@link #SPAWN_APART} blocks. Any height in the water passes (Minecraft's spawner keeps one random height for a try);
     * the world's spawns are set on the bank after ({@link #finalizeSpawn}).
     */
    public static boolean canSpawn(EntityType<MonitorEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 8 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)
                || bankNear(level, pos, BANK_REACH) == null) {
            return false;
        }
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION
                || level.getEntitiesOfClass(MonitorEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(SPAWN_APART)).isEmpty();
    }

    /**
     * The nearest dry bank to stand on within reach (sideways) of a spot in the water: the top of a dry column, at most a
     * few blocks over the water's surface level; null if none.
     */
    @Nullable
    static BlockPos bankNear(LevelAccessor level, BlockPos pos, int reach) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int x = pos.getX() + dx, z = pos.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < sea - 1 || y > sea + 4) {
                    continue;
                }
                BlockPos stand = new BlockPos(x, y, z), under = stand.below();
                if (!level.getFluidState(under).isEmpty() || !level.getFluidState(stand).isEmpty()
                        || !level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) {
                    continue;
                }
                int d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = stand;
                }
            }
        }
        return best;
    }

    /** The nearest water to run into, aimed two blocks past the edge where that is water too; null if none within reach. */
    @Nullable
    BlockPos waterAim(int reach) {
        Level level = level();
        BlockPos water = BlockPos.findClosestMatch(blockPosition(), reach, 4,
                p -> level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p.below()).is(FluidTags.WATER)).orElse(null);
        if (water == null) {
            return null;
        }
        BlockPos from = blockPosition();
        int dx = Integer.signum(water.getX() - from.getX()), dz = Integer.signum(water.getZ() - from.getZ());
        for (int far = 2; far >= 1; far--) {
            BlockPos in = water.offset(dx * far, 0, dz * far);
            if (level.getFluidState(in).is(FluidTags.WATER)) {
                return in;
            }
        }
        return water;
    }

    // --- the goals -----------------------------------------------------------------------------------------------------

    /**
     * Startled (hit, pressed by a player, its hiss ignored): it bites back once if the hitter is at its jaws, then runs for
     * the nearest water and dives, staying under a while; with no water near it runs away from what scared it.
     */
    static class FleeGoal extends Goal {
        private final MonitorEntity m;
        private BlockPos water;
        private int biting, left;
        private byte kind = BITE;

        FleeGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return m.startled > 0 && (!m.isInWater() || m.biteBack != null);
        }

        @Override
        public void start() {
            left = 400;
            biting = 0;
            water = null;
            LivingEntity foe = m.biteBack;
            m.biteBack = null;
            if (foe != null && foe.isAlive() && m.atJaws(foe, BITE_REACH + 0.5)) {
                kind = biteKind(foe);
                biting = biteTicks(kind);
                m.setDoing(kind);
                m.getNavigation().stop();
                m.biteTarget = foe;
            } else {
                run();
            }
        }

        private LivingEntity targetOrNull() {
            return m.biteTarget;
        }

        private void run() {
            m.setDoing(CALM);
            water = m.isInWater() ? null : m.waterAim(16);
            if (water != null) {
                m.getNavigation().moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5, 1.7);
            } else if (m.scare != null) {
                Vec3 away = DefaultRandomPos.getPosAway(m, 12, 4, m.scare.position());
                if (away != null) {
                    m.getNavigation().moveTo(away.x, away.y, away.z, 1.7);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && (biting > 0 || m.startled > 0 && !m.isInWater());
        }

        @Override
        public void tick() {
            left--;
            if (biting > 0) {
                LivingEntity foe = targetOrNull();
                if (foe != null) {
                    m.face(foe.getX(), foe.getZ(), 30.0F);
                }
                if (biteTicks(kind) - biting == snapTick(kind) && foe != null) {
                    m.biteAt(foe);
                }
                if (--biting == 0) {
                    m.biteTarget = null;
                    run();
                }
                return;
            }
            if (m.getNavigation().isDone() && left % 20 == 0) {
                run();
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.biteTarget = null;
            if (m.isBiting()) {
                m.setDoing(CALM);
            }
        }
    }

    /** Who the bite in the flee goal is aimed at. */
    @Nullable
    LivingEntity biteTarget;

    /**
     * A survival player coming near on land: it stops, turns to face them and stands tall hissing, mouth open; one that
     * comes right up to its jaws gets bitten and it runs; if they stay near past the hiss, it runs too.
     */
    static class ThreatGoal extends Goal {
        private final MonitorEntity m;
        private Player player;
        private int left, biting;

        ThreatGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Nullable
        private Player near() {
            Vec3 jaws = m.jawPoint();
            for (Player p : m.level().players()) {
                if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) || !p.isAlive()) {
                    continue;
                }
                double reach = p.isCrouching() ? HISS_REACH_CROUCHING : HISS_REACH;
                double fromBody = p.distanceTo(m);
                if (p.position().distanceTo(jaws) < reach + 0.5 || fromBody < reach) {
                    return p;
                }
            }
            return null;
        }

        @Override
        public boolean canUse() {
            if (m.startled > 0 || m.isInWater() || m.hissWait > 0 || m.getRandom().nextInt(3) != 0) {   // the goals are looked at every other tick
                return false;
            }
            player = near();
            return player != null;
        }

        @Override
        public void start() {
            left = 60;
            biting = 0;
            m.getNavigation().stop();
            m.setDoing(HISS);
            m.playSound(SoundEvents.CAT_HISS, 1.2F, 0.6F);
        }

        @Override
        public boolean canContinueToUse() {
            return biting > 0 || left > 0 && m.startled == 0 && player.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player);
        }

        @Override
        public void tick() {
            if (biting > 0) {
                m.face(player.getX(), player.getZ(), 30.0F);
                if (BITE_PLAYER_TICKS - biting == SNAP_PLAYER_TICK) {
                    m.biteAt(player);
                }
                if (--biting == 0) {
                    m.startle(player);
                }
                return;
            }
            left--;
            m.face(player.getX(), player.getZ(), 12.0F);
            if (left % 30 == 0) {
                m.playSound(SoundEvents.CAT_HISS, 1.0F, 0.55F);
            }
            if (m.atJaws(player, BITE_REACH)) {          // right at its jaws: it lunges, clamps on and shakes its head
                biting = BITE_PLAYER_TICKS;
                m.setDoing(BITE_PLAYER);
            } else if (left == 0) {
                if (near() == player) {                  // still near after the hiss: it gives up and runs
                    m.startle(player);
                } else {
                    left = 0;
                }
            } else if (left % 10 == 0 && near() != player) {
                left = 0;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.hissWait = 100;
            if (m.doing() == HISS || m.isBiting()) {
                m.setDoing(CALM);
            }
        }
    }

    /**
     * Hungry: goes to the nearest crab, snail or remains within {@link #HUNT_REACH} blocks and, with it at its jaws, bites;
     * a crab is swallowed whole, a snail crushed (its broken shell left), remains take three bites. Then it eats, and is
     * fed 5 to 10 minutes. Swimming, it dives to prey on the bed under it (2026-11-19, BUGS 230: it used to hang at the
     * surface over it). It gives up a chase after 20 s and, with nothing caught, rests a minute before it hunts again.
     */
    static class HuntGoal extends Goal {
        private final MonitorEntity m;
        private Entity prey;
        private int left, biting, eating, bites, repath;

        HuntGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (m.isFed() || m.startled > 0 || m.getRandom().nextInt(10) != 0) {   // about once a second (goals are looked at every other tick, odd or even by the id)
                return false;
            }
            prey = nearest();
            return prey != null;
        }

        @Nullable
        private Entity nearest() {
            List<Entity> near = m.level().getEntities(m, m.getBoundingBox().inflate(HUNT_REACH, 4.0, HUNT_REACH), e -> e.isAlive()
                    && (e instanceof CrabEntity || e instanceof SnailEntity || e instanceof RemainsEntity r && r.bitesLeft() > 0));
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : near) {
                double d = e.distanceToSqr(m);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        @Override
        public void start() {
            left = 400;
            biting = eating = bites = repath = 0;
            m.setDoing(CALM);
            m.hunting = true;
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && m.startled == 0 && (biting > 0 || eating > 0 || prey != null && prey.isAlive() && !m.isFed());
        }

        @Override
        public void tick() {
            if (eating > 0) {
                if (--eating == 0) {
                    m.setDoing(CALM);
                    if (prey instanceof RemainsEntity r && r.isAlive() && r.bitesLeft() > 0 && bites < 3) {
                        return;                           // another bite of the remains
                    }
                    m.hungryIn = 6000 + m.getRandom().nextInt(6000);
                    prey = null;
                }
                return;
            }
            if (biting > 0) {
                m.face(prey.getX(), prey.getZ(), 30.0F);
                if (BITE_TICKS - biting == SNAP_TICK) {
                    snap();
                }
                if (--biting == 0) {
                    eating = EAT_TICKS;
                    m.setDoing(EAT);
                }
                return;
            }
            if (prey == null || !prey.isAlive()) {
                return;
            }
            left--;                                       // only the chase counts against its 20 s, not biting and eating
            if (m.atJaws(prey, 0.5)) {
                m.getNavigation().stop();
                biting = BITE_TICKS;
                m.setDoing(BITE);
                return;
            }
            // close: walk the last bit with its head turned onto the prey (its jaws are 3 blocks ahead of its middle)
            double d = Math.sqrt(m.distanceToSqr(prey.getX(), m.getY(), prey.getZ()));
            if (d < JAWS_AHEAD * m.ownSize() + 1.5) {
                m.face(prey.getX(), prey.getZ(), 15.0F);
                if (m.isInWater()) {                      // swimming: it steers its jaws onto the prey, diving to the bed if it lies there
                    m.getNavigation().stop();
                    Vec3 to = new Vec3(prey.getX(), prey.getY() + prey.getBbHeight() * 0.5, prey.getZ()).subtract(m.jawPoint()).scale(0.1);
                    double most = 0.08;
                    m.setDeltaMovement(to.lengthSqr() > most * most ? to.normalize().scale(most) : to);
                } else if (d < JAWS_AHEAD * m.ownSize() - 0.2) {      // too close for its jaws: back off a little
                    float yaw = m.getYRot() * Mth.DEG_TO_RAD;
                    m.setDeltaMovement(m.getDeltaMovement().add(Mth.sin(yaw) * 0.05, 0, -Mth.cos(yaw) * 0.05));
                } else {
                    m.getNavigation().stop();
                    float yaw = m.getYRot() * Mth.DEG_TO_RAD;
                    m.setDeltaMovement(m.getDeltaMovement().add(-Mth.sin(yaw) * 0.04, 0, Mth.cos(yaw) * 0.04));
                }
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                m.getNavigation().moveTo(prey, 1.1);
            }
        }

        private void snap() {
            if (!(m.level() instanceof ServerLevel server) || prey == null || !prey.isAlive() || !m.atJaws(prey, 0.8)) {
                return;                                   // it got away: the jaws shut on nothing
            }
            Vec3 at = prey.position();
            if (prey instanceof RemainsEntity r) {
                if (r.bite(m)) {
                    bites++;
                }
                m.playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.8F);
            } else if (prey instanceof SnailEntity) {
                prey.discard();
                CrabShellBlock.leave(server, at);
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SavannaItems.CRAB_SHELL.get())),
                        at.x, at.y + 0.2, at.z, 10, 0.15, 0.1, 0.15, 0.05);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL, 1.0F, 0.8F);
                bites = 3;
            } else {
                prey.discard();                           // a crab, swallowed whole
                m.playSound(SoundEvents.FOX_BITE, 1.0F, 0.8F);
                bites = 3;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            prey = null;
            m.hunting = false;
            if (bites == 0) {
                m.hungryIn = REST_AFTER_GIVING_UP;        // caught nothing: it rests a minute, so it never stays on prey it cannot reach
            }
            if (m.doing() == BITE || m.doing() == EAT) {
                m.setDoing(CALM);
            }
        }
    }

    /**
     * Curious (2026-11-17, the user's ask; fun first): a survival player on land within {@link #WATCH_REACH} blocks, but not
     * near enough to hiss at, is watched: it stops, turns to face them and flicks its tongue, and if they walk off past
     * {@link #TRAIL_FAR} it follows slowly back to about {@link #TRAIL_NEAR}, never out of its stretch of bank. After 15 to
     * 30 s it loses interest for 20 to 50 s. Coming close still makes it hiss and bite ({@link ThreatGoal}); a basking
     * monitor only turns its head (the model).
     */
    static class WatchGoal extends Goal {
        private final MonitorEntity m;
        private Player player;
        private int left, repath;

        WatchGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean watchable(Player p) {
            return p != null && p.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p) && p.distanceTo(m) < WATCH_REACH;
        }

        @Override
        public boolean canUse() {
            if (m.startled > 0 || m.isInWater() || !m.onGround() || m.watchWait > 0 || m.doing() != CALM
                    || m.getRandom().nextInt(10) != 0) {
                return false;
            }
            player = null;
            double best = Double.MAX_VALUE;
            for (Player p : m.level().players()) {
                double d = p.distanceTo(m);
                if (watchable(p) && d > HISS_REACH + 1.5 && d < best) {
                    best = d;
                    player = p;
                }
            }
            return player != null;
        }

        @Override
        public void start() {
            left = 300 + m.getRandom().nextInt(300);
            repath = 0;
            m.getNavigation().stop();
            m.setDoing(WATCH);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && m.startled == 0 && !m.isInWater() && m.doing() == WATCH && watchable(player);
        }

        @Override
        public void tick() {
            left--;
            double d = player.distanceTo(m);
            if (d > TRAIL_FAR) {
                if (--repath <= 0) {
                    repath = 20;
                    Vec3 to = player.position().add(m.position().subtract(player.position()).normalize().scale(TRAIL_NEAR));
                    if (m.isWithinRestriction(BlockPos.containing(to))) {
                        m.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
                    }
                }
            } else if (d < TRAIL_NEAR + 0.5) {
                m.getNavigation().stop();
            }
            if (m.getNavigation().isDone()) {
                m.face(player.getX(), player.getZ(), 6.0F);
            }
            m.getLookControl().setLookAt(player, 20.0F, 20.0F);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            m.watchWait = 400 + m.getRandom().nextInt(600);
            m.getNavigation().stop();
            if (m.doing() == WATCH) {
                m.setDoing(CALM);
            }
            player = null;
        }
    }

    /** By day, dry, on a bank under the sky: lies flat in the sun 1 to 3 minutes; at night on land it lies still till dawn. */
    static class BaskGoal extends Goal {
        private final MonitorEntity m;
        private int left;
        private boolean night;

        BaskGoal(MonitorEntity m) {
            this.m = m;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean sunTime() {
            return m.sunIn <= 0 && m.level().isDay() && !m.level().isRaining();
        }

        @Override
        public boolean canUse() {
            if (m.isInWater() || !m.onGround() || m.startled > 0 || !m.getNavigation().isDone() && !m.level().isDay()) {
                return false;
            }
            if (!m.level().isDay()) {
                night = true;
                return m.getRandom().nextInt(40) == 0;
            }
            night = false;
            return sunTime() && m.level().canSeeSky(m.blockPosition());
        }

        @Override
        public void start() {
            left = night ? 24000 : 1200 + m.getRandom().nextInt(2400);
            m.getNavigation().stop();
            m.setDoing(BASK);
        }

        @Override
        public boolean canContinueToUse() {
            return left > 0 && !m.isInWater() && m.startled == 0 && (night ? !m.level().isDay() : !m.level().isRaining());
        }

        @Override
        public void tick() {
            left--;
        }

        @Override
        public void stop() {
            if (m.doing() == BASK) {
                m.setDoing(CALM);
            }
            if (!night) {
                m.sunIn = 2400 + m.getRandom().nextInt(2400);    // about 2 to 4 minutes before it basks again
            }
        }
    }

    // --- animations ----------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation ENTER = RawAnimation.begin().thenPlayAndHold("enter");
    private static final RawAnimation BASK_CLIP = RawAnimation.begin().thenLoop("bask");
    private static final RawAnimation HISS_CLIP = RawAnimation.begin().thenLoop("hiss");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation BITE_PLAYER_CLIP = RawAnimation.begin().thenPlayAndHold("bite_player");
    private static final RawAnimation EAT_CLIP = RawAnimation.begin().thenPlayAndHold("eat");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation TONGUE = RawAnimation.begin().thenLoop("tongue");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, this::animate));
        controllers.add(new AnimationController<>(this, "tongue", 0, this::tongue));
    }

    /**
     * Whether the swim clip plays (drawing only): afloat, with both leg pairs folded ({@link #FOLDED}). The model eases
     * {@link #legStand} by each pair's own footing, and sets it straight from "afloat" when drawn too far to bend.
     */
    public boolean swimClip() {
        return isInWater() && !onGround() && legStand[0] < FOLDED && legStand[1] < FOLDED;
    }

    /** Whether the going-in clip plays (drawing only). */
    public boolean entering() {
        return tickCount >= enterTick && tickCount - enterTick < ENTER_TICKS && !isDeadOrDying();
    }

    public boolean walking() {
        double dx = getX() - xo, dz = getZ() - zo;
        return dx * dx + dz * dz > 0.004 * 0.004;
    }

    private PlayState animate(AnimationState<MonitorEntity> state) {
        AnimationController<MonitorEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        switch (doing()) {
            case BASK:
                return state.setAndContinue(BASK_CLIP);
            case HISS:
                return state.setAndContinue(HISS_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case BITE_PLAYER:
                return state.setAndContinue(BITE_PLAYER_CLIP);
            case EAT:
                return state.setAndContinue(EAT_CLIP);
            default:
                break;
        }
        double dx = getX() - xo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (entering()) {
            return state.setAndContinue(ENTER);
        }
        if (swimClip()) {
            player.setAnimationSpeed(Mth.clamp(0.6 + speed / 0.08, 0.6, 2.0));
            return state.setAndContinue(SWIM);
        }
        if (walking()) {
            player.setAnimationSpeed(Mth.clamp(speed / (WALK_CLIP_SPEED * getScale()), 0.5, 3.0));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    /** Walking on land, or watching a player, it flicks its tongue now and then (two flicks in each of its own 5-second windows). */
    private PlayState tongue(AnimationState<MonitorEntity> state) {
        boolean window = (tickCount + getId() * 37) % 100 < 40;
        if (window && (walking() && doing() == CALM || doing() == WATCH) && !isInWater() && !isDeadOrDying()) {
            return state.setAndContinue(TONGUE);
        }
        state.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
