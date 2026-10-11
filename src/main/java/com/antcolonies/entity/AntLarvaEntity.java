package com.antcolonies.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A larva of one colony: what an egg turns into. It lies in the nursery, cannot move, and is fed ant bread by the
 * nurses. After {@link Colony#LARVA_MEAL} pieces it grows into a worker (as soon as the colony has room for one).
 * Saved with the world; disappears when its colony is gone.
 */
public class AntLarvaEntity extends Entity {
    /** How many pieces of bread this larva has eaten (drawn bigger the more it has eaten). Synced to the client. */
    private static final EntityDataAccessor<Integer> DATA_FED =
            SynchedEntityData.defineId(AntLarvaEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID colonyId;
    /** The nurse that is on its way with bread for this larva, and until when (game time). Not saved. */
    @Nullable
    private UUID feeder;
    private long feederUntil;

    public AntLarvaEntity(EntityType<? extends AntLarvaEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    public void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    public int getFed() {
        return entityData.get(DATA_FED);
    }

    public void setFed(int fed) {
        entityData.set(DATA_FED, fed);
    }

    /** One more piece of bread eaten. */
    public void feed() {
        setFed(getFed() + 1);
    }

    public boolean isFull() {
        return getFed() >= Colony.LARVA_MEAL;
    }

    /** Whether a nurse is already on its way with bread for this larva. */
    public boolean hasFeeder(long now) {
        return feeder != null && feederUntil > now;
    }

    public void claim(UUID nurse, long until) {
        feeder = nurse;
        feederUntil = until;
    }

    public void release(UUID nurse) {
        if (nurse.equals(feeder)) {
            feeder = null;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FED, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        colonyId = tag.hasUUID("colony") ? tag.getUUID("colony") : null;
        setFed(tag.getInt("fed"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putInt("fed", getFed());
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0, onGround() ? 0 : (motion.y - 0.04) * 0.98, 0);
        move(MoverType.SELF, getDeltaMovement());
        // Once a second: is the colony still there, has the larva eaten enough?
        if (level().isClientSide || tickCount % 20 != 0 || colonyId == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(level);
        Colony colony = manager.getColony(colonyId);
        if (colony == null) {
            discard(); // the queen died
            return;
        }
        if (isFull() && colony.hatchLarva(level, this)) {
            manager.markChanged();
        }
    }
}
