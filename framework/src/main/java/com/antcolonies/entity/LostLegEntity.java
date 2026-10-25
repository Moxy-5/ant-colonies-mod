package com.antcolonies.entity;

import com.antcolonies.registry.ModEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A piece of leg bitten off in a fight (M8 step 2d, {@code docs/DESIGN.md} "Step 2d, lost legs"): the shin and foot,
 * lying where it fell for {@link #LIES_TICKS}, then gone (later the savanna's scavengers take it, M9.5). Drawn in its
 * species' look of the dead ({@code client.LostLegRenderer}), its size the insect's ({@link AbstractAntEntity#lostLegLength}).
 */
public class LostLegEntity extends Entity {
    /** How long it lies (ticks: 5 minutes, my pick, to confirm). */
    public static final int LIES_TICKS = 6000;
    private static final EntityDataAccessor<String> DATA_SPECIES =
            SynchedEntityData.defineId(LostLegEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_LENGTH =
            SynchedEntityData.defineId(LostLegEntity.class, EntityDataSerializers.FLOAT);
    /** Which way along the ground it lies, and whether it is a left leg (drawn bent the other way). */
    private static final EntityDataAccessor<Boolean> DATA_LEFT =
            SynchedEntityData.defineId(LostLegEntity.class, EntityDataSerializers.BOOLEAN);
    private int age;

    public LostLegEntity(EntityType<? extends LostLegEntity> type, Level level) {
        super(type, level);
    }

    /** Drops the piece of this insect's leg (an index in {@code LostLegs.LEGS}) beside it, flung a little. */
    public static void drop(ServerLevel level, AbstractAntEntity insect, int leg) {
        LostLegEntity piece = ModEntities.LOST_LEG.get().create(level);
        if (piece == null) {
            return;
        }
        piece.entityData.set(DATA_SPECIES, insect.species().toString());
        piece.entityData.set(DATA_LENGTH, insect.lostLegLength());
        piece.entityData.set(DATA_LEFT, leg % 2 == 0);
        piece.moveTo(insect.getX(), insect.getY() + insect.getBbHeight() * 0.5, insect.getZ(),
                insect.getRandom().nextFloat() * 360.0F, 0.0F);
        piece.setDeltaMovement((insect.getRandom().nextDouble() - 0.5) * 0.15, 0.12, (insect.getRandom().nextDouble() - 0.5) * 0.15);
        level.addFreshEntity(piece);
    }

    /** The species of the insect it came from (its look). */
    public ResourceLocation species() {
        return ResourceLocation.parse(entityData.get(DATA_SPECIES));
    }

    /** How long (blocks) it is. */
    public float length() {
        return entityData.get(DATA_LENGTH);
    }

    public boolean isLeft() {
        return entityData.get(DATA_LEFT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPECIES, "colonyframework:none");
        builder.define(DATA_LENGTH, 0.2F);
        builder.define(DATA_LEFT, true);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_SPECIES, tag.getString("species").isEmpty() ? "colonyframework:none" : tag.getString("species"));
        entityData.set(DATA_LENGTH, tag.contains("length") ? tag.getFloat("length") : 0.2F);
        entityData.set(DATA_LEFT, tag.getBoolean("left"));
        age = tag.getInt("age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("species", entityData.get(DATA_SPECIES));
        tag.putFloat("length", length());
        tag.putBoolean("left", isLeft());
        tag.putInt("age", age);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        if (onGround()) {
            setDeltaMovement(0, 0, 0);
        } else {
            setDeltaMovement(motion.x * 0.9, (motion.y - 0.04) * 0.98, motion.z * 0.9);
        }
        move(MoverType.SELF, getDeltaMovement());
        if (!level().isClientSide && ++age >= LIES_TICKS) {
            discard();
        }
    }
}
