package com.antcolonies.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * An egg of one colony. It just lies on the floor (it falls if the floor goes away), is saved with the world, and
 * disappears when its colony is gone. Once it lies in the nursery it hatches after a while into a worker.
 */
public class AntEggEntity extends Entity {
    @Nullable
    private UUID colonyId;
    /** The game time when the egg hatches; -1 until it lies in the nursery (the clock starts there). Saved. */
    private long hatchAt = -1;

    public AntEggEntity(EntityType<? extends AntEggEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    public void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        colonyId = tag.hasUUID("colony") ? tag.getUUID("colony") : null;
        hatchAt = tag.contains("hatchAt") ? tag.getLong("hatchAt") : -1;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putLong("hatchAt", hatchAt);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0, onGround() ? 0 : (motion.y - 0.04) * 0.98, 0);
        move(MoverType.SELF, getDeltaMovement());
        // Once a second: is the colony still there, is it time to hatch?
        if (level().isClientSide || tickCount % 20 != 0 || colonyId == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(level);
        Colony colony = manager.getColony(colonyId);
        if (colony == null) {
            discard(); // the queen died
            return;
        }
        long now = level.getGameTime();
        if (hatchAt < 0) {
            if (colony.isInNursery(level, blockPosition())) {
                hatchAt = now + Colony.HATCH_TICKS; // kept warm in the nursery: the clock starts
            }
        } else if (now >= hatchAt && colony.hatchEgg(level, this)) {
            manager.markChanged();
        }
    }
}
