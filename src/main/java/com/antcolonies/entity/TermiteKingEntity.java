package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.QueenToRoomGoal;
import com.antcolonies.entity.goal.SealEntranceGoal;
import com.antcolonies.entity.goal.StayWithQueenGoal;
import com.antcolonies.species.Species;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

/**
 * The termite king: founds the colony together with the queen (a royal pair), helps her dig the royal chamber, closes
 * the entrance from inside, and then lives beside her for good. He joins the nearest termite queen that has no king yet.
 * Like the queen he is fed by the nurses, mouth to mouth (a nodule a day, the workers' meal interval; my pick, to
 * confirm); unfed he does not starve (not decided yet).
 */
public class TermiteKingEntity extends AbstractAntEntity implements FedByNurses {
    public static final String CASTE = "king";
    /** How close (in blocks) a queen without a king must be for him to join her. */
    private static final double JOIN_RADIUS = 32.0;

    /** The game time of his last meal ({@link Colony#NOT_SET}: his clock has not started). Saved. */
    private long lastFed = Colony.NOT_SET;
    /** The nurse on its way to feed him, and until when. Not saved. */
    @org.jetbrains.annotations.Nullable
    private java.util.UUID feeder;
    private long feederUntil;

    public TermiteKingEntity(EntityType<? extends TermiteKingEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.12)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SealEntranceGoal(this));
        this.goalSelector.addGoal(2, new StayWithQueenGoal(this));
        // He digs with her until their chamber is finished.
        this.goalSelector.addGoal(3, new DigGoal(this, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(4, new AntWanderGoal(this, 0.8, 300, 3, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // He digs, but leaves the soil where it falls (no mound yet: that is the workers' job).
    @Override
    public boolean canPlaceBlocks() {
        return false;
    }

    // --- food: the nurses bring it, mouth to mouth -------------------------------------------------------------

    @org.jetbrains.annotations.Nullable
    private Colony colony() {
        return getColonyId() == null || !(level() instanceof ServerLevel serverLevel) ? null : ColonyManager.get(serverLevel).getColony(getColonyId());
    }

    /** Whether he wants a nodule: in a colony whose members get hungry, a day (the workers' meal interval) after the last. */
    @Override
    public boolean wantsFood() {
        Colony colony = colony();
        return colony != null && colony.isHungerOn() && lastFed != Colony.NOT_SET
                && level().getGameTime() - lastFed >= colony.profile().worker().mealIntervalTicks();
    }

    @Override
    public void takeFood() {
        lastFed = level().getGameTime();
    }

    public long getLastFed() {
        return lastFed;
    }

    /** For the tests: pretend his last meal was at this game time. */
    public void setLastFed(long gameTime) {
        lastFed = gameTime;
    }

    @Override
    public boolean hasFeeder(long now) {
        return feeder != null && feederUntil > now;
    }

    @Override
    public void claimFeeder(java.util.UUID nurse, long until) {
        feeder = nurse;
        feederUntil = until;
    }

    @Override
    public void releaseFeeder(java.util.UUID nurse) {
        if (nurse.equals(feeder)) {
            feeder = null;
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LastFed", lastFed);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastFed = tag.contains("LastFed") ? tag.getLong("LastFed") : Colony.NOT_SET;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount % 20 == 0 && level() instanceof ServerLevel) {
            Colony colony = colony();
            if (lastFed == Colony.NOT_SET || colony == null || !colony.isHungerOn()) {
                lastFed = level().getGameTime(); // his clock only runs in a colony that gets hungry
            }
        }
        // Every two seconds while he has no queen: look for a termite queen nearby that has no king yet.
        if (tickCount % 40 != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(serverLevel);
        if (getColonyId() != null && manager.getColony(getColonyId()) != null) {
            return;
        }
        setColonyId(null); // his queen is gone
        Colony colony = null;
        double best = JOIN_RADIUS * JOIN_RADIUS;
        for (Colony candidate : manager.getColonies()) {
            double distance = candidate.getCenter().distSqr(blockPosition());
            if (candidate.getSpecies().equals(Species.MACROTERMES) && candidate.getCount(CASTE) == 0 && distance <= best) {
                colony = candidate;
                best = distance;
            }
        }
        if (colony != null) {
            setColonyId(colony.getId());
            colony.setCount(CASTE, 1);
            manager.markChanged();
        }
    }

    // The colony loses its king (the queen lives on).
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }
}
