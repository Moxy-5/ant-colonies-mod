package com.antcolonies.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.species.SpeciesProfile;

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
 * nurses. After its meal (the species profile's larva_meal) it grows into a worker (as soon as the colony has room for one).
 * It may instead be growing into another of its species' castes ({@link #getGrowsInto}, a termite nymph into a soldier,
 * decided by the colony when it hatches through its castes hook, {@code ColonyHooks.Castes}): it then eats that caste's
 * meal and grows into one. Saved with the world; disappears when its colony is gone.
 */
public class AntLarvaEntity extends Entity implements FedByNurses {
    /** How many pieces of bread this larva has eaten (drawn bigger the more it has eaten). Synced to the client. */
    private static final EntityDataAccessor<Integer> DATA_FED =
            SynchedEntityData.defineId(AntLarvaEntity.class, EntityDataSerializers.INT);
    /** How many pieces of bread it needs to grow up (from its colony's species profile). Synced, so the client can draw its size. */
    private static final EntityDataAccessor<Integer> DATA_MEAL =
            SynchedEntityData.defineId(AntLarvaEntity.class, EntityDataSerializers.INT);
    /**
     * What it grows into: 0 a worker, else its caste's number from the colony's castes hook ({@link Colony#casteNumber}).
     * Synced (the species' renderer draws by it: a nymph growing into a soldier gets a big head), saved.
     */
    private static final EntityDataAccessor<Byte> DATA_GROWS_INTO =
            SynchedEntityData.defineId(AntLarvaEntity.class, EntityDataSerializers.BYTE);

    @Nullable
    private UUID colonyId;
    /** The nurse that is on its way with bread for this larva, and until when (game time). Not saved. */
    @Nullable
    private UUID feeder;
    private long feederUntil;
    /** Game time of the last piece of bread. A larva that goes the species profile's larva_starve_ticks without one starves. Saved. */
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

    /** How many pieces of bread it needs to grow up. */
    public int getMeal() {
        return entityData.get(DATA_MEAL);
    }

    public void setMeal(int meal) {
        entityData.set(DATA_MEAL, Math.max(1, meal));
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
        return getFed() >= getMeal();
    }

    /** Step 10: the number of the caste it grows into (0: a worker; {@link Colony#casteNumber}). The client has only this. */
    public int getGrowsIntoNumber() {
        return entityData.get(DATA_GROWS_INTO);
    }

    /** The caste it grows into besides a worker (a soldier's caste name), or null: a worker. Asks its colony. */
    @Nullable
    public String getGrowsInto(Colony colony) {
        return colony.casteOfNumber(getGrowsIntoNumber());
    }

    /** It grows into this caste of its colony (a soldier's caste name; null: a worker). */
    public void setGrowsInto(Colony colony, @Nullable String caste) {
        entityData.set(DATA_GROWS_INTO, colony.casteNumber(caste));
    }

    @Override
    public boolean wantsFood() {
        return !isFull();
    }

    @Override
    public void takeFood() {
        feed();
    }

    @Override
    public void claimFeeder(UUID nurse, long until) {
        claim(nurse, until);
    }

    @Override
    public void releaseFeeder(UUID nurse) {
        release(nurse);
    }

    /** Whether a nurse is already on its way with bread for this larva. */
    @Override
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
        builder.define(DATA_MEAL, SpeciesProfile.RED_HARVESTER_ANT.brood().larvaMeal());
        builder.define(DATA_GROWS_INTO, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        colonyId = tag.hasUUID("colony") ? tag.getUUID("colony") : null;
        setFed(tag.getInt("fed"));
        lastFed = tag.contains("lastFed") ? tag.getLong("lastFed") : Colony.NOT_SET;
        entityData.set(DATA_GROWS_INTO, tag.getByte("soldier")); // (saved under its pre-step-10 word, so old worlds load)
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (colonyId != null) {
            tag.putUUID("colony", colonyId);
        }
        tag.putInt("fed", getFed());
        tag.putLong("lastFed", lastFed);
        tag.putByte("soldier", entityData.get(DATA_GROWS_INTO));
    }

    /** The game time of this one's last server tick (not saved: after a load it has always just come back). */
    private long lastServerTick = Long.MIN_VALUE;

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel) {
            // Back from being frozen in unloaded land while its colony was away: it is one of the colony's numbers now.
            long now = serverLevel.getGameTime();
            boolean gap = now != lastServerTick + 1;
            lastServerTick = now;
            if ((gap || tickCount % 20 == 0) && com.antcolonies.colony.AwayLife.backFromFrozen(serverLevel, this, colonyId)) {
                return;
            }
        }
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
        ColonyHooks.Castes castes = colony.hooks().first(ColonyHooks.CASTES); // (the termite soldiers' own meals)
        setMeal(castes != null ? castes.mealFor(getGrowsInto(colony)) : colony.profile().brood().larvaMeal()); // (the profile can change with /reload)
        // The hunger clock only runs for a larva that still has to eat; a full one waiting to grow up has been fed.
        long now = level.getGameTime();
        if (lastFed == Colony.NOT_SET || isFull() || !colony.isHungerOn()) {
            lastFed = now;
        } else if (now - lastFed >= colony.profile().brood().larvaStarveTicks()) {
            colony.brood().starveLarva(level, this);
            manager.markChanged();
            return;
        }
        if (isFull() && colony.brood().hatchLarva(level, this)) {
            manager.markChanged();
        }
    }
}
