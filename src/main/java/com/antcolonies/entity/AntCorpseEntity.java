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
 * The body of a dead ant of one colony. It lies where the ant died (it falls if the floor goes away) until a worker
 * carries it to the refuse chamber and sets it down there; a corpse in the refuse chamber stays for good. A corpse that
 * nobody brings there crumbles to dust after {@link Colony#CORPSE_DECAY_TICKS}, so nothing is left lying about for ever.
 * Saved with the world; disappears when its colony is gone.
 */
public class AntCorpseEntity extends Entity {
    @Nullable
    private UUID colonyId;
    /** True once a worker has set the body down in the refuse chamber. Saved. */
    private boolean placed;

    public AntCorpseEntity(EntityType<? extends AntCorpseEntity> type, Level level) {
        super(type, level);
    }

    @Nullable
    public UUID getColonyId() {
        return colonyId;
    }

    public void setColonyId(@Nullable UUID colonyId) {
        this.colonyId = colonyId;
    }

    public boolean isPlaced() {
        return placed;
    }

    public void setPlaced(boolean placed) {
        this.placed = placed;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        colonyId = tag.hasUUID("colony") ? tag.getUUID("colony") : null;
        placed = tag.getBoolean("placed");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putBoolean("placed", placed);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(0, onGround() ? 0 : (motion.y - 0.04) * 0.98, 0);
        move(MoverType.SELF, getDeltaMovement());
        // Once a second: is the colony still there, has the body lain about too long?
        if (level().isClientSide || tickCount % 20 != 0 || colonyId == null || !(level() instanceof ServerLevel level)) {
            return;
        }
        Colony colony = ColonyManager.get(level).getColony(colonyId);
        if (colony == null) {
            discard(); // the queen died
        } else if (!placed && colony.isInRefuseChamber(level, blockPosition())) {
            placed = true; // it lies where it belongs: it stays
        } else if (!placed && tickCount >= Colony.CORPSE_DECAY_TICKS) {
            discard(); // nobody carried it away: it crumbles to dust
        }
    }
}
