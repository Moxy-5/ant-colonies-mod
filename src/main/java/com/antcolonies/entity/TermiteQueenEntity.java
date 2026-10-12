package com.antcolonies.entity;

import java.util.Optional;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.QueenToRoomGoal;
import com.antcolonies.entity.goal.SealEntranceGoal;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The termite queen. With her king she founds the colony, digs the royal chamber and settles in it. She starts the
 * size of the king and swells as her colony grows (her species profile's {@code queen_body}): her head, thorax and legs
 * stay the same, only her abdomen stretches, and past a certain size she can no longer walk. Sealed in, she lays the
 * founding batch, and the pair raise it (see {@link #foundingBatch}). The colony dies with her.
 */
public class TermiteQueenEntity extends AbstractAntEntity {
    /** How far she has swollen: 0 = just founded (king-sized), 1 = full grown. Synced so the renderer can draw her. Saved. */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(TermiteQueenEntity.class, EntityDataSerializers.FLOAT);

    private boolean colonyChecked;
    /** When the next egg of the founding batch comes ({@link Colony#NOT_SET} = not counting yet). Saved. */
    private long nextFoundingEgg = Colony.NOT_SET;

    public TermiteQueenEntity(EntityType<? extends TermiteQueenEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // She closes the entrance herself only if she has no king.
        this.goalSelector.addGoal(1, new SealEntranceGoal(this));
        this.goalSelector.addGoal(2, new QueenToRoomGoal(this));
        this.goalSelector.addGoal(3, new DigGoal(this, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(4, new AntWanderGoal(this, 0.6, 400, 3, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE, 0.0F);
    }

    /** How far she has swollen, 0 (founding) to 1 (full grown). */
    public float getSize() {
        return entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        entityData.set(DATA_SIZE, Mth.clamp(size, 0.0F, 1.0F));
    }

    // A bigger queen takes up more room (her hitbox grows with her).
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float size = getSize();
        return EntityDimensions.scalable(0.35F + 0.45F * size, 0.25F + 0.2F * size);
    }

    // She digs, but leaves the soil where it falls (no mound yet: that is the workers' job).
    @Override
    public boolean canPlaceBlocks() {
        return false;
    }

    /** Too swollen to walk (her species' too_big_to_walk_at): she lies where she is and only her abdomen moves. Server side. */
    public boolean isTooBigToWalk() {
        if (getColonyId() == null || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
        Optional<SpeciesProfile.QueenBody> body = colony == null ? Optional.empty() : colony.profile().queenBody();
        return body.isPresent() && getSize() >= body.get().tooBigToWalkAt();
    }

    // A queen too big to walk does not move or look around (her goals do not run).
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || (!level().isClientSide && isTooBigToWalk());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(serverLevel);
        // Once, on the first tick: make sure the brain has a colony for this queen.
        if (!colonyChecked) {
            colonyChecked = true;
            if (getColonyId() == null || manager.getColony(getColonyId()) == null) {
                Colony colony = manager.createColony(Species.MACROTERMES, getUUID(), blockPosition());
                setColonyId(colony.getId());
            }
        }
        // Once a second: she grows toward the size her colony calls for (never shrinks), a little each day.
        if (tickCount % 20 == 0 && getColonyId() != null) {
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                colony.profile().founding().ifPresent(founding -> foundingBatch(serverLevel, manager, colony, founding));
                colony.profile().queenBody().ifPresent(body -> {
                    int members = colony.getMemberCount() - colony.getCount(TermiteKingEntity.CASTE);
                    float target = Math.min(1.0F, members / (float) Math.max(1, body.fullSizeAtMembers()));
                    float step = body.growthPerDay() / 1200.0F; // a day is 1200 seconds
                    if (getSize() < target) {
                        setSize(Math.min(target, getSize() + step));
                    }
                });
            }
        }
    }

    /**
     * The founding batch (once a second): once she has sealed herself in and lies in her chamber, the queen lays the
     * batch's eggs one by one from her own reserves; and the royal pair feed the nymphs on their own body fat, one
     * piece of food at a time, until the nymphs grow into the first workers.
     */
    private void foundingBatch(ServerLevel level, ColonyManager manager, Colony colony, SpeciesProfile.Founding founding) {
        long now = level.getGameTime();
        boolean sealedIn = colony.queenCare().isQueenHome(level) && !colony.nest().isEntranceOpen(level);
        if (sealedIn && colony.queenCare().getEggsLaid() < founding.eggs()) {
            if (nextFoundingEgg == Colony.NOT_SET) {
                nextFoundingEgg = now + founding.eggIntervalTicks();
            } else if (now >= nextFoundingEgg) {
                BlockPos spot = colony.brood().pickEggSpot(level, getRandom());
                Vec3 at = spot == null ? position() : Vec3.atBottomCenterOf(spot);
                colony.queenCare().layFoundingEgg(level, at.x, at.y, at.z);
                nextFoundingEgg = now + founding.eggIntervalTicks();
                manager.markChanged();
            }
        }
        if (now % founding.feedIntervalTicks() < 20) {
            // The hungriest nymph gets the next piece.
            colony.brood().getLarvae(level).stream().filter(nymph -> !nymph.isFull())
                    .min((a, b) -> Integer.compare(a.getFed(), b.getFed()))
                    .ifPresent(nymph -> {
                        nymph.feed();
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, nymph.getX(), nymph.getY() + 0.2, nymph.getZ(), 2, 0.1, 0.05, 0.1, 0.0);
                    });
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Size", getSize());
        tag.putLong("NextFoundingEgg", nextFoundingEgg);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSize(tag.getFloat("Size"));
        nextFoundingEgg = tag.contains("NextFoundingEgg") ? tag.getLong("NextFoundingEgg") : Colony.NOT_SET;
    }

    // The colony dies when the queen dies.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager.get(serverLevel).removeColony(getColonyId());
        }
    }
}
