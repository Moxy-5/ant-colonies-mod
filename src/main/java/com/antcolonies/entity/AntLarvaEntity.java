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
    /** Game time of the last piece of bread. A larva that goes {@link Colony#LARVA_STARVE_TICKS} without one starves. Saved. */
    private long lastFed = Colony.NOT_SET;

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
        lastFed = level().getGameTime();
    }

    /** The game time of the last meal (or of the last time it needed none); {@link Colony#NOT_SET} before the first second. */
    public long getLastFed() {
        return lastFed;
    }

    public void setLastFed(long time) {
        lastFed = time;
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
        lastFed = tag.contains("lastFed") ? tag.getLong("lastFed") : Colony.NOT_SET;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putInt("fed", getFed());
        tag.putLong("lastFed", lastFed);
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
        // The hunger clock only runs for a larva that still has to eat; a full one waiting to grow up has been fed.
        long now = level.getGameTime();
        if (lastFed == Colony.NOT_SET || isFull() || !colony.isHungerOn()) {
            lastFed = now;
        } else if (now - lastFed >= Colony.LARVA_STARVE_TICKS) {
            colony.starveLarva(level, this);
            manager.markChanged();
            return;
        }
        if (isFull() && colony.hatchLarva(level, this)) {
            manager.markChanged();
        }
    }
}
