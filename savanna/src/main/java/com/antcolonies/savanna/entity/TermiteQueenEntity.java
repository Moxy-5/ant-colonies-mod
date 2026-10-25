package com.antcolonies.savanna.entity;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.SwellingQueen;
import java.util.Optional;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.QueenToRoomGoal;
import com.antcolonies.entity.goal.SealEntranceGoal;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.species.SpeciesProfile;

import com.antcolonies.savanna.species.SavannaNumbers;

import net.minecraft.core.BlockPos;
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
 * founding batch, and the pair raise it (see {@code QueenCare.raiseFoundingBatch}). Then, fed fungus nodules by the
 * nurses, she lays a steady stream of eggs behind her, faster the bigger she is ({@code QueenCare.layInStream}). She
 * also keeps the colony's fungus garden growing (once a second). The colony dies with her.
 */
public class TermiteQueenEntity extends AbstractAntEntity implements SwellingQueen {
    /** How far she has swollen: 0 = just founded (king-sized), 1 = full grown. Synced so the renderer can draw her. Saved. */
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(TermiteQueenEntity.class, EntityDataSerializers.FLOAT);

    /** How far (in blocks) behind her middle her egg comes out (more as her abdomen grows). */
    private static final double EGG_BEHIND = 0.4;

    private boolean colonyChecked;

    public TermiteQueenEntity(EntityType<? extends TermiteQueenEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public net.minecraft.resources.ResourceLocation species() {
        return SavannaSpecies.MACROTERMES;
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
        this.goalSelector.addGoal(3, new DigGoal(this, () -> QueenToRoomGoal.mayDig(this)));
        this.goalSelector.addGoal(4, new AntWanderGoal(this, 0.6, 400, 3, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SIZE, 0.0F);
    }

    /** How far she has swollen, 0 (founding) to 1 (full grown). */
    @Override
    public float getSize() {
        return entityData.get(DATA_SIZE);
    }

    public void setSize(float size) {
        entityData.set(DATA_SIZE, Mth.clamp(size, 0.0F, 1.0F));
    }

    /** M8 step 1b: she is the queen of a colony placed in new land (made for her, not founded by her). */
    public void becomeQueenOf(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
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
        Optional<SavannaNumbers.QueenBody> body = colony == null ? Optional.empty() : colony.profile().section(SavannaNumbers.QUEEN_BODY);
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
                Colony colony = manager.createColony(SavannaSpecies.MACROTERMES, getUUID(), blockPosition());
                setColonyId(colony.getId());
            }
        }
        // Once a second: she grows toward the size her colony calls for (never shrinks), a little each day.
        if (tickCount % 20 == 0 && getColonyId() != null) {
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                if (colony.queenCare().queenStarves(serverLevel)) {
                    hurt(damageSources().starve(), 1000.0F); // unfed for days (here, or while away): she dies and the colony ends
                    return;
                }
                if (colony.queenCare().raiseFoundingBatch(serverLevel, this)) {
                    manager.markChanged();
                }
                if (colony.queenCare().layInStream(serverLevel, getSize())) {
                    Vec3 at = position().subtract(Vec3.directionFromRotation(0.0F, getYRot()).scale(EGG_BEHIND + 0.4 * getSize()));
                    BlockPos cell = BlockPos.containing(at.x, getY() + 0.1, at.z);
                    if (!serverLevel.getBlockState(cell).getCollisionShape(serverLevel, cell).isEmpty()) {
                        at = position(); // a wall behind her: the egg comes out where she lies
                    }
                    colony.queenCare().layStreamEgg(serverLevel, at.x, getY(), at.z, getSize());
                    manager.markChanged();
                }
                colony.profile().section(SavannaNumbers.QUEEN_BODY).ifPresent(body -> {
                    int members = colony.getMemberCount() - colony.getCount(TermiteKingEntity.CASTE);
                    float target = Math.min(1.0F, members / (float) Math.max(1, body.fullSizeAtMembers()));
                    float step = body.growthPerDay() / 1200.0F; // a day is 1200 seconds
                    if (getSize() < target) {
                        setSize(Math.min(target, getSize() + step));
                    }
                });
                // Swollen too big to walk while on her way somewhere: she lies where she is, and her legs drop the
                // route they were on (her goals no longer run to stop it; found by the stuck watcher, 2026-10-17).
                if (isTooBigToWalk() && !getNavigator().isDone()) {
                    getNavigator().stop();
                }
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Size", getSize());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSize(tag.getFloat("Size"));
    }

    // The colony dies when the queen dies.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager.queenDied(serverLevel, getColonyId());
        }
    }

    /** Its body in the species file's fight numbers (M8 step 2a). */
    @Override
    public String fightBody() {
        return "queen";
    }
}
