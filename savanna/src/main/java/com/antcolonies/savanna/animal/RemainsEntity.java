package com.antcolonies.savanna.animal;

import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * What is left of a dead fish (M9 river life, 2026-11-15; {@code docs/DESIGN.md}, "Remains, the crab and the Nile
 * monitor"): meat on the bones in Minecraft's raw-meat style, no blood, lying on its side on the bed.
 *
 * <p>It counts bites the way the algae counts strands: each eater's bite ({@link #bite}) takes one, with a puff of meat
 * bits, and the look drops a stage (fresh, fed on, picked over, bones) each time a third of the bites is gone. It also
 * rots, a bite at a time, so the meat is gone within about a game day even if nothing eats it, and the bones crumble a
 * game day after that: remains never pile up, and nothing has to eat them to live (my picks, to check against the crab's
 * hunger when it comes). A player's hit breaks it at once.
 *
 * <p>The makers: the crocodile, tearing a catfish in two and leaving the front half ({@link Kind#CATFISH_TORN}), and the
 * catfish, whose jaws snap shut early on half the tilapia it catches, the front half falling away ({@link Kind#TILAPIA_HALF});
 * whole fish that die and fish a player kills come later, their numbers tuned in a soak.
 */
public class RemainsEntity extends Entity implements GeoEntity {
    /** The kinds (the front half of a tilapia a catfish bit off, 2026-11-15), their game names (models {@code remains_<name>_<stage>}, {@code tools/remains_models.py game}) and bites (my picks). */
    public enum Kind {
        CATFISH("catfish", 12), CATFISH_TORN("catfish_torn", 9), TILAPIA("tilapia", 6), TILAPIA_HALF("tilapia_half", 3);

        public final String name;
        public final int bites;

        Kind(String name, int bites) {
            this.name = name;
            this.bites = bites;
        }

        public static Kind byName(String name) {
            for (Kind k : values()) {
                if (k.name.equals(name)) {
                    return k;
                }
            }
            return CATFISH;
        }
    }

    /** The meat rots to the bones in this long, untouched (ticks: a game day, my pick). */
    public static final int ROTS_TICKS = 24000;
    /** The bones lie this long, then crumble (ticks: a game day, my pick). */
    public static final int BONES_TICKS = 24000;

    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BITES = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.INT);
    /** Lying on its other side: its back to the other hand (a tilapia a catfish held on its left side falls away as it lay). */
    private static final EntityDataAccessor<Boolean> DATA_OTHER_SIDE = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(RemainsEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks since the last bite rotted away, and since it was left as bones. */
    private int rot, bones;

    public RemainsEntity(EntityType<? extends RemainsEntity> type, Level level) {
        super(type, level);
    }

    /** Leaves remains of this kind at a place, lying along a heading (degrees, the fish's head that way), at a fish's size. */
    public static RemainsEntity leave(ServerLevel level, Kind kind, Vec3 at, float yaw, float size) {
        return leave(level, kind, at, yaw, size, TilapiaEntity.EVERYDAY);
    }

    /** The same, in the dead fish's colours (a tilapia's look, {@link TilapiaEntity#look()}: the user, 2026-11-15). */
    public static RemainsEntity leave(ServerLevel level, Kind kind, Vec3 at, float yaw, float size, int look) {
        RemainsEntity remains = SavannaEntities.REMAINS.get().create(level);
        if (remains == null) {
            return null;
        }
        remains.entityData.set(DATA_KIND, kind.ordinal());
        remains.entityData.set(DATA_BITES, kind.bites);
        remains.entityData.set(DATA_SIZE, size);
        remains.entityData.set(DATA_LOOK, look);
        remains.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        remains.setYHeadRot(yaw);
        level.addFreshEntity(remains);
        return remains;
    }

    /** The colours of the fish it was (a tilapia's look, {@link TilapiaEntity#look()}; a catfish has only one). */
    public int look() {
        return entityData.get(DATA_LOOK);
    }

    /** Whether it lies on its other side (drawn mirrored, its back to the other hand). */
    public boolean onOtherSide() {
        return entityData.get(DATA_OTHER_SIDE);
    }

    /** Lays it on its other side. */
    public void lieOnOtherSide() {
        entityData.set(DATA_OTHER_SIDE, true);
    }

    public Kind kind() {
        int i = entityData.get(DATA_KIND);
        return Kind.values()[i >= 0 && i < Kind.values().length ? i : 0];
    }

    /** Bites of meat left (0: only bones). */
    public int bitesLeft() {
        return entityData.get(DATA_BITES);
    }

    /** The fish's size it came from (1 a usual fish). */
    public float size() {
        return entityData.get(DATA_SIZE);
    }

    /** The look's stage: 1 fresh, 2 fed on, 3 picked over, 4 bones. */
    public int stage() {
        int left = bitesLeft();
        int all = kind().bites;
        return left <= 0 ? 4 : 1 + Math.min(2, (all - left) * 3 / all);
    }

    /** Sets how many bites are left (for tests and the command). */
    public void setBitesLeft(int left) {
        entityData.set(DATA_BITES, Math.max(0, Math.min(kind().bites, left)));
    }

    /** An eater takes a bite: true if there was meat to take. A puff of meat bits flies off, more when the look changes. */
    public boolean bite(Entity eater) {
        int left = bitesLeft();
        if (left <= 0) {
            return false;
        }
        int before = stage();
        entityData.set(DATA_BITES, left - 1);
        puff(stage() != before ? 14 : 6);
        return true;
    }

    private void puff(int count) {
        if (level() instanceof ServerLevel server) {
            ItemStack bits = new ItemStack(kind() == Kind.TILAPIA || kind() == Kind.TILAPIA_HALF ? SavannaItems.TILAPIA.get() : SavannaItems.CATFISH.get());
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, bits), getX(), getY() + 0.15, getZ(),
                    count, 0.25 * size(), 0.08, 0.25 * size(), 0.04);
        }
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (onGround()) {
            setDeltaMovement(0, 0, 0);
        } else if (isInWater()) {
            setDeltaMovement(motion.x * 0.8, Math.max(-0.06, motion.y * 0.9 - 0.008), motion.z * 0.8);   // drifts down to the bed
        } else {
            setDeltaMovement(motion.x * 0.9, (motion.y - 0.04) * 0.98, motion.z * 0.9);
        }
        move(MoverType.SELF, getDeltaMovement());
        if (level().isClientSide) {
            return;
        }
        if (bitesLeft() > 0) {
            if (++rot >= ROTS_TICKS / kind().bites) {
                rot = 0;
                entityData.set(DATA_BITES, bitesLeft() - 1);
            }
        } else if (++bones >= BONES_TICKS) {
            discard();
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof Player) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE_MEAL)),
                        getX(), getY() + 0.15, getZ(), 10, 0.2 * size(), 0.05, 0.2 * size(), 0.05);
            }
            puff(bitesLeft() > 0 ? 8 : 0);
            level().playSound(null, blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6F, 1.2F);
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, 0);
        builder.define(DATA_BITES, Kind.CATFISH.bites);
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_OTHER_SIDE, false);
        builder.define(DATA_LOOK, TilapiaEntity.EVERYDAY);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_KIND, Kind.byName(tag.getString("kind")).ordinal());
        setBitesLeft(tag.contains("bites") ? tag.getInt("bites") : kind().bites);     // a summon with no bites: fresh
        entityData.set(DATA_SIZE, tag.contains("size") ? tag.getFloat("size") : 1.0F);
        entityData.set(DATA_LOOK, tag.contains("look") ? net.minecraft.util.Mth.clamp(tag.getInt("look"), TilapiaEntity.YOUNG, TilapiaEntity.BREEDING) : TilapiaEntity.EVERYDAY);
        entityData.set(DATA_OTHER_SIDE, tag.getBoolean("other_side"));
        rot = tag.getInt("rot");
        bones = tag.getInt("bones");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("kind", kind().name);
        tag.putInt("bites", bitesLeft());
        tag.putFloat("size", size());
        tag.putInt("look", look());
        tag.putBoolean("other_side", onOtherSide());
        tag.putInt("rot", rot);
        tag.putInt("bones", bones);
    }

    /** Drawn about 2 blocks long on a small box: culled by the drawn fish, not the box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.6 * size(), 0.3, 1.6 * size());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = 64.0 * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // still: nothing moves on remains
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
