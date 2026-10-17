package com.antcolonies.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.KingCare;
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
 * confirm); unfed he starves by the queen's rule. His hunger clock is kept by the brain ({@link KingCare}), so it runs on
 * while the colony is away.
 */
public class TermiteKingEntity extends AbstractAntEntity implements FedByNurses {
    public static final String CASTE = "king";
    /** How close (in blocks) a queen without a king must be for him to join her. */
    private static final double JOIN_RADIUS = 32.0;

    /** The nurse on its way to feed him, and until when. Not saved. */
    @org.jetbrains.annotations.Nullable
    private java.util.UUID feeder;
    private long feederUntil;

    public TermiteKingEntity(EntityType<? extends TermiteKingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation species() {
        return Species.MACROTERMES;
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
        // He digs with her while they found the colony, until their chamber is finished.
        this.goalSelector.addGoal(3, new DigGoal(this, () -> QueenToRoomGoal.mayDig(this)));
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

    /** Whether he wants a nodule: a day (the workers' meal interval) after the last, see {@link KingCare#wantsFood}. */
    @Override
    public boolean wantsFood() {
        Colony colony = colony();
        return colony != null && level() instanceof ServerLevel serverLevel && colony.kingCare().wantsFood(serverLevel);
    }

    @Override
    public void takeFood() {
        Colony colony = colony();
        if (colony != null && level() instanceof ServerLevel serverLevel) {
            colony.kingCare().fed(serverLevel);
        }
    }

    /** The game time of his last meal (kept by his colony's brain; {@link Colony#NOT_SET} without a colony). */
    public long getLastFed() {
        Colony colony = colony();
        return colony == null ? Colony.NOT_SET : colony.kingCare().getLastFed();
    }

    /** For the tests: pretend his last meal was at this game time. */
    public void setLastFed(long gameTime) {
        Colony colony = colony();
        if (colony != null) {
            colony.kingCare().setLastFed(gameTime);
        }
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
    public void tick() {
        super.tick();
        if (tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            Colony colony = colony();
            if (colony != null && colony.kingCare().starves(serverLevel)) {
                hurt(damageSources().starve(), 1000.0F); // unfed for days (here, or while the colony was away): he dies
                return;
            }
            if (colony != null && tickCount % 1200 == 0) {
                ColonyManager.get(serverLevel).markChanged(); // his hunger clock changes all the time: make sure it gets saved
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
            colony.kingCare().joined(serverLevel);
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
