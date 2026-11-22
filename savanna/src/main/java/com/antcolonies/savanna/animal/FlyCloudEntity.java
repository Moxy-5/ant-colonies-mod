package com.antcolonies.savanna.animal;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cloud of flies (fly clouds step 2, 2026-11-20, {@code docs/plans/fly-clouds.md}; animals are fun first): one mob
 * that is a whole cloud of 4 to 12 flies (the user's numbers). The server knows only the cloud: where it hangs, how
 * high, how many flies it holds. The flies are not mobs: the cloud's renderer ({@code FlyCloudRenderer}) draws every
 * fly from the fly model and moves each on the player's computer (darting, hovering, now and then landing nearby to rub
 * its legs), so a cloud costs the server almost nothing and the network nothing per fly.
 *
 * <p>Where (the user: "around all day for now, just for testing"): over the shallows and the banks beside them, any time
 * of day, in a spawning group of its own (cap 18 round a player, one or two clouds at a spot; was 6 one at a time, tripled
 * 2026-11-20 when the user asked for more flies round the river). The cloud wanders slowly round the spot it came to
 * ({@link #DRIFT} blocks), its middle between about 1.7 and 3 blocks over the ground or water under it, mostly near 2,
 * rising and sinking every minute or two (the user's heights: in reach of a bullfrog's 2-block tongue when it sinks,
 * out of reach when it rises). Eaten flies come back one a minute ({@link #REFILL_TICKS}); the bullfrog's tongue, barbs
 * and young crocodiles eating them is the plan's step 3.
 *
 * <p>Not a creature to fight: it cannot be hit, pushed, leashed or hurt (only {@code /kill} removes it), and it passes
 * through blocks.
 */
public class FlyCloudEntity extends Mob {
    /** How many flies a cloud may hold (the user, 2026-11-20: "range from 4-12"). */
    public static final int FEWEST = 4, MOST = 12;
    /** How far it wanders round its spot (blocks), and how fast it moves at most (blocks a tick; my picks). */
    static final double DRIFT = 2.5, MOST_SPEED = 0.04;
    /** One eaten fly comes back every this many ticks (a minute, my pick). */
    static final int REFILL_TICKS = 1200;
    /** Clouds within this many blocks of a player are never removed (as the river animals; was 64); past 128 they always are. */
    static final double KEEP_NEAR = 96.0;

    private static final EntityDataAccessor<Integer> DATA_FLIES = SynchedEntityData.defineId(FlyCloudEntity.class, EntityDataSerializers.INT);

    /** The players' computers start the cloud's buzz through this (set by the client's setup; nothing on a server). */
    public static Consumer<FlyCloudEntity> startBuzz = cloud -> { };
    /** The swarm the renderer moves (client only; the client's own object, unknown here). */
    @Nullable
    public Object clientSwarm;
    private boolean buzzing;

    /** The spot it wanders round. */
    @Nullable
    private Vec3 home;
    /** How many flies it holds when full, and ticks until the next eaten one comes back. */
    private int most = 8, refill;
    /** Its own phases for the wandering and the rise and fall (radians), so no two clouds move together. */
    private float phaseX, phaseZ, phaseLow, phaseHigh;

    public FlyCloudEntity(EntityType<? extends FlyCloudEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        RandomSource r = level.getRandom();
        phaseX = r.nextFloat() * Mth.TWO_PI;
        phaseZ = r.nextFloat() * Mth.TWO_PI;
        phaseLow = r.nextFloat() * Mth.TWO_PI;
        phaseHigh = r.nextFloat() * Mth.TWO_PI;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLIES, 8);
    }

    /** How many flies it holds now. */
    public int flies() {
        return entityData.get(DATA_FLIES);
    }

    /** For tests and the hunters (step 3): sets how many flies it holds now (4 to 12 when full). */
    public void setFlies(int flies) {
        entityData.set(DATA_FLIES, Mth.clamp(flies, 0, MOST));
    }

    /**
     * A fly caught by a bullfrog's tongue is swallowed: one fly fewer (it comes back in a minute or so). Which fly is
     * each player's computer's own pick (the frog's renderer aims the tongue at a real fly of the swarm).
     */
    public void takeFly() {
        if (flies() > 0) {
            setFlies(flies() - 1);
            if (refill <= 0) {
                refill = REFILL_TICKS;
            }
        }
    }

    /** How many it holds when full. */
    public int mostFlies() {
        return most;
    }

    /** The spot it wanders round (its spawn spot), or none yet. */
    @Nullable
    public Vec3 home() {
        return home;
    }

    /**
     * How high its middle wants to hang over the ground or water under it at this time (blocks): mostly near 2, a slow
     * sway of 0.3 every minute and a half, and now and then (every two and a half minutes) a rise to 3, out of a
     * bullfrog's reach. From 1.7 to 3.
     */
    public double heightAt(long gameTime) {
        double t = gameTime / 20.0;
        double rise = Math.max(0.0, Math.sin(Mth.TWO_PI * t / 150.0 + phaseHigh));
        return 2.0 + 0.3 * Math.sin(Mth.TWO_PI * t / 90.0 + phaseLow) + 0.7 * rise * rise;
    }

    /** The top of the ground or water at this column (the first free block over it; leaves do not count). */
    static int surface(LevelReader level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (!buzzing) {
                buzzing = true;
                startBuzz.accept(this);
            }
            return;
        }
        if (home == null) {
            home = position();
        }
        if (isNoAi()) {
            return;         // a cloud summoned with NoAI stays where it is put (as any NoAI mob does; films and tests)
        }
        // the wandering round its spot and the rise and fall, eased toward
        double t = level().getGameTime() / 20.0;
        double sx = Math.sin(Mth.TWO_PI * t / 73.0 + phaseX), sz = Math.sin(Mth.TWO_PI * t / 61.0 + phaseZ);
        double round = DRIFT / Math.max(1.0, Math.sqrt(sx * sx + sz * sz));      // inside a circle, not a square's corners
        double x = home.x + sx * round;
        double z = home.z + sz * round;
        double y = surface(level(), x, z) + heightAt(level().getGameTime());
        Vec3 to = new Vec3(x, y, z).subtract(position());
        double far = to.length();
        if (far > 1.0E-4) {
            Vec3 step = far > MOST_SPEED ? to.scale(MOST_SPEED / far) : to;
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        }
        int now = flies();
        if (now < most && --refill <= 0) {
            setFlies(now + 1);
            refill = REFILL_TICKS;
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        most = FEWEST + level.getRandom().nextInt(MOST - FEWEST + 1);
        setFlies(most);
        // drawn anywhere in the column (the spawner's one height): set up to its hanging height over the top
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            setPos(getX(), surface(level, getX(), getZ()) + heightAt(level.getLevel().getGameTime()), getZ());
        }
        home = position();
        return out;
    }

    /** How deep the water under a cloud may be at most (blocks: the shallows), and how far off the bank it may be over land. */
    static final int SHALLOW = 3, BANK = 2;

    /**
     * Where a cloud may spawn by itself: over shallow water (1 to {@link #SHALLOW} deep), or over the land within
     * {@link #BANK} blocks of it, from 13 under sea level up to the highest pans ({@link com.antcolonies.savanna.worldgen.RiverZones#HIGHEST_POOL}). Minecraft's
     * spawner draws one height in the column: any spot in the water there or the free block just over the top passes
     * ({@link #finalizeSpawn} sets the cloud up to its height).
     */
    public static boolean canSpawn(EntityType<FlyCloudEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + com.antcolonies.savanna.worldgen.RiverZones.HIGHEST_POOL + 1) {
            return false;
        }
        int top = surface(level, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (pos.getY() > top || pos.getY() < top && !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        for (int dx = -BANK; dx <= BANK; dx++) {
            for (int dz = -BANK; dz <= BANK; dz++) {
                if (shallowWater(level, pos.getX() + dx, pos.getZ() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this column's top is water 1 to {@link #SHALLOW} blocks deep. */
    static boolean shallowWater(LevelAccessor level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, top - 1, z);
        int depth = 0;
        while (depth <= SHALLOW && level.getFluidState(at).is(FluidTags.WATER)) {
            depth++;
            at.move(0, -1, 0);
        }
        return depth >= 1 && depth <= SHALLOW;
    }

    /** Water in its box is no matter (it hangs in the air over it): only other mobs in the way stop a spawn. */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer) && distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR;
    }

    // --- not a creature to fight ---------------------------------------------------------------------------------------
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    protected void tickDeath() {
        if (!level().isClientSide && !isRemoved()) {
            remove(Entity.RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    /** Its flies spread round it and land on the ground under it: drawn while any of that is in view. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(DRIFT_DRAWN, 0.6, DRIFT_DRAWN).expandTowards(0, -3.5, 0);
    }

    /** How far round the cloud its flies are drawn: the cloud's own spread and the landing spots (blocks). */
    static final double DRIFT_DRAWN = 1.8;

    /** Its flies are drawn up to 24 blocks off (the renderer drops each fly past that). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 26.0 * 26.0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Flies", flies());
        tag.putInt("Most", most);
        tag.putInt("Refill", refill);
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
        tag.putFloat("PhaseX", phaseX);
        tag.putFloat("PhaseZ", phaseZ);
        tag.putFloat("PhaseLow", phaseLow);
        tag.putFloat("PhaseHigh", phaseHigh);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        most = tag.contains("Most") ? Mth.clamp(tag.getInt("Most"), FEWEST, MOST) : most;
        setFlies(tag.contains("Flies") ? tag.getInt("Flies") : most);
        refill = tag.getInt("Refill");
        home = tag.contains("HomeX") ? new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ")) : null;
        phaseX = tag.getFloat("PhaseX");
        phaseZ = tag.getFloat("PhaseZ");
        phaseLow = tag.getFloat("PhaseLow");
        phaseHigh = tag.getFloat("PhaseHigh");
    }
}
