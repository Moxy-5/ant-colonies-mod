package com.antcolonies.entity;

import java.util.EnumSet;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.ColonyParts;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DefendGoal;
import com.antcolonies.entity.goal.GoInsideGoal;
import com.antcolonies.entity.goal.GuardPostGoal;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * A termite soldier (Macrotermes): a pale body with a big shiny orange-brown armoured head and long black crossing
 * jaws, blind. Two kinds, as in real Macrotermes: the <b>minor</b> soldier, about a worker's size with a bigger head,
 * and the <b>major</b>, about twice a worker's size with a huge head. A nymph grows into a soldier instead of a worker
 * when the colony has fewer soldiers than it wants (see {@code colony.Soldiers}); a major needs a bigger meal.
 * <p>
 * Soldiers cannot feed themselves (their jaws are too big): about once a day they want a fungus nodule, and a nurse
 * brings it to them ({@code FeedLarvaGoal}); a soldier a nurse is on its way to holds still. One that has gone without
 * for long (the profile's {@code soldiers.slow_after_ticks}) moves at half speed (it does not starve to death).
 * <p>
 * They stand guard (see {@link GuardPostGoal}): the majors inside, at the royal chamber's doorway, the way into its
 * tunnel from the shaft and the garden doorways. The minors go out at foraging time: one beside each open way out and
 * the others in a sentry line along the foragers' trail, facing out; when the foraging is over they wait at their posts
 * until the workers are in, then go in last, and by day they wait in the foraging tunnels just inside the holes (see
 * {@code colony.Soldiers}). They answer the alarm ({@code colony.Alarm}, {@link DefendGoal}): they drum (the head bang,
 * {@link #drum}) when they feel it, the drumming spreading soldier to soldier, then go at an enemy or to the breach.
 * For now they only bite chickens ({@link #bite}, a simple first version; fighting players and ants comes with M8).
 * They do not fit the termite tunnel blocks (only workers do).
 */
public class TermiteSoldierEntity extends AbstractAntEntity implements FedByNurses, ColonyBorn {
    /** The two kinds of soldier. Each is counted in the colony under its own caste name. */
    public enum Rank {
        MINOR("minor_soldier"), MAJOR("major_soldier");

        private final String caste;

        Rank(String caste) {
            this.caste = caste;
        }

        /** The caste name the colony counts this kind under. */
        public String caste() {
            return caste;
        }

        /** Saved and synced as a byte: 0 = none (a worker), 1 = minor, 2 = major. */
        public byte id() {
            return (byte) (ordinal() + 1);
        }

        /** Apex step 9c: the rank counted under this caste name, or null (a worker, or none). */
        @Nullable
        public static Rank byCaste(@Nullable String caste) {
            for (Rank rank : values()) {
                if (rank.caste.equals(caste)) {
                    return rank;
                }
            }
            return null;
        }

        @Nullable
        public static Rank byId(int id) {
            return id == 1 ? MINOR : id == 2 ? MAJOR : null;
        }
    }

    /** How close (in blocks) a termite colony must be for a soldier put down with a spawn egg to join it. */
    private static final double JOIN_RADIUS = 32.0;
    /** The soldiers' numbers when the colony has none in its profile (or there is no colony): the termite file's own. */
    private static final SpeciesProfile.SoldierNumbers DEFAULT_NUMBERS = new SpeciesProfile.SoldierNumbers(0.1F, 3, 6, 10, 24000, 48000, 3, 300);
    private static final ResourceLocation HUNGRY_SLOWDOWN = ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "soldier_hungry");
    private static final RawAnimation HEAD_BANG = RawAnimation.begin().thenLoop("head_bang");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
    /** How long (ticks) the bite is shown (the animation is 0.35 seconds). */
    private static final int BITE_TICKS = 7;
    /** True while the soldier bites. Synced so the client plays the bite. */
    private static final EntityDataAccessor<Boolean> DATA_BITING =
            SynchedEntityData.defineId(TermiteSoldierEntity.class, EntityDataSerializers.BOOLEAN);
    /** True while the soldier drums (the head bang). Synced so the client plays the animation. */
    private static final EntityDataAccessor<Boolean> DATA_DRUMMING =
            SynchedEntityData.defineId(TermiteSoldierEntity.class, EntityDataSerializers.BOOLEAN);

    private final Rank rank;
    private boolean colonyChecked;
    /** The game time of the last nodule a nurse brought ({@link Colony#NOT_SET}: its clock starts in a colony). Saved. */
    private long lastFed = Colony.NOT_SET;
    /** The nurse on its way with food, and until when (game time). Not saved. */
    @Nullable
    private UUID feeder;
    private long feederUntil;
    /** How many more ticks it drums. Server only, not saved. */
    private int drumTicks;
    /** How many more ticks the bite is shown. Server only, not saved. */
    private int biteTicks;

    public TermiteSoldierEntity(EntityType<? extends TermiteSoldierEntity> type, Level level, Rank rank) {
        super(type, level);
        this.rank = rank;
    }

    @Override
    protected ResourceLocation species() {
        return Species.MACROTERMES;
    }

    /** A minor soldier: quick and agile (it guards the foragers outside). */
    public static AttributeSupplier.Builder createMinorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    /** A major soldier: big, tough and slow. */
    public static AttributeSupplier.Builder createMajorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Drumming, it stands still; with a nurse on her way with food, it waits for her (not during an alarm).
        this.goalSelector.addGoal(1, new HoldStillGoal(() -> drumTicks > 0));
        this.goalSelector.addGoal(2, new HoldStillGoal(() -> wantsFood() && hasFeeder(level().getGameTime()) && !isAlarmed()));
        // The alarm: at an enemy, or to the breach.
        this.goalSelector.addGoal(3, new DefendGoal(this));
        // Outside when it should be in (a major put down with a spawn egg, the minors once the workers are in): back inside.
        this.goalSelector.addGoal(4, new GoInsideGoal(this));
        this.goalSelector.addGoal(5, new GuardPostGoal(this));
        // No post to guard (the nest is not dug that far yet, every post taken): it strolls about (inside unless it may be out).
        this.goalSelector.addGoal(6, new AntWanderGoal(this, 0.8, 200, 4, () -> true));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Holds the soldier still (no walking) while {@code when} is true. */
    private class HoldStillGoal extends Goal {
        private final java.util.function.BooleanSupplier when;

        HoldStillGoal(java.util.function.BooleanSupplier when) {
            this.when = when;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return when.getAsBoolean();
        }

        @Override
        public void start() {
            getNavigator().stop();
        }

        @Override
        public void tick() {
            getNavigator().stop();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DRUMMING, false);
        builder.define(DATA_BITING, false);
    }

    public Rank getRank() {
        return rank;
    }

    // The majors stay inside; the minors are out while their colony sends them out (see Soldiers.minorsOut), and any
    // soldier the alarm sends somewhere may go there (a minor at an enemy, at a breach).
    @Override
    public boolean mayGoOut() {
        Colony colony = getColony();
        if (colony == null) {
            return false;
        }
        if (level() instanceof ServerLevel serverLevel && colony.part(ColonyParts.ALARM).map(alarm -> alarm.isBusy(serverLevel, this)).orElse(false)) {
            return true;
        }
        return rank == Rank.MINOR && colony.part(ColonyParts.SOLDIERS).map(soldiers -> soldiers.minorsOut(level())).orElse(false);
    }

    /** Whether its colony's alarm is up (it then has no time to wait for a nurse). */
    private boolean isAlarmed() {
        Colony colony = getColony();
        return colony != null && colony.part(ColonyParts.ALARM).map(alarm -> alarm.level(level())).orElse(0) >= 2;
    }

    // Soldiers dig nothing and carry nothing.
    @Override
    public boolean canPlaceBlocks() {
        return false;
    }

    /** The colony's soldier numbers (the termites' own while it has no colony). */
    private SpeciesProfile.SoldierNumbers numbers() {
        Colony colony = getColony();
        return colony == null ? DEFAULT_NUMBERS : colony.profile().soldiers().orElse(DEFAULT_NUMBERS);
    }

    // --- food: a nurse brings it --------------------------------------------------------------------------------

    /** Whether it wants a nodule: in a colony whose members get hungry, a day (the profile's meal interval) after the last. */
    @Override
    public boolean wantsFood() {
        Colony colony = getColony();
        return colony != null && colony.isHungerOn() && lastFed != Colony.NOT_SET
                && level().getGameTime() - lastFed >= numbers().mealIntervalTicks();
    }

    /** A nurse gave it a nodule: full for another day. */
    @Override
    public void takeFood() {
        lastFed = level().getGameTime();
        updateHungry(false);
    }

    public long getLastFed() {
        return lastFed;
    }

    /** Sets the game time of its last meal (a new soldier has just eaten; the tests make it hungry). */
    public void setLastFed(long gameTime) {
        lastFed = gameTime;
    }

    @Override
    public long getLastMeal() {
        return lastFed;
    }

    @Override
    public void setLastMeal(long gameTime) {
        lastFed = gameTime;
    }

    @Override
    public boolean hasFeeder(long now) {
        return feeder != null && feederUntil > now;
    }

    @Override
    public void claimFeeder(UUID nurse, long until) {
        feeder = nurse;
        feederUntil = until;
    }

    @Override
    public void releaseFeeder(UUID nurse) {
        if (nurse.equals(feeder)) {
            feeder = null;
        }
    }

    /** Moves at half speed while it has gone without food for long. */
    private void updateHungry(boolean slow) {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (slow) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(HUNGRY_SLOWDOWN, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            speed.removeModifier(HUNGRY_SLOWDOWN);
        }
    }

    // --- drumming -----------------------------------------------------------------------------------------------

    /**
     * Drums (the head bang) for {@code ticks} ticks, standing still: real soldiers strike the ground with their heads in
     * quick bursts to raise the alarm, and the others feel it through the walls ({@code colony.Alarm}); also the command
     * {@code /antcolonies drum}.
     */
    public void drum(int ticks) {
        drumTicks = Math.max(drumTicks, ticks);
    }

    /** Whether it is drumming now (synced: the client plays the head bang). */
    public boolean isDrumming() {
        return entityData.get(DATA_DRUMMING);
    }

    /**
     * Bites the enemy: its jaws snap shut on it (the bite animation) for the soldier's attack damage (a minor 3). A
     * simple first version (the user's "something simple for now"; M8 refines the fighting).
     */
    public void bite(LivingEntity enemy) {
        biteTicks = BITE_TICKS;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        enemy.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        playSound(SoundEvents.TURTLE_EGG_CRACK, 0.5F, 1.8F + getRandom().nextFloat() * 0.2F);
    }

    /** Whether it is biting now (synced: the client plays the bite). */
    public boolean isBiting() {
        return entityData.get(DATA_BITING);
    }

    @Override
    @Nullable
    protected RawAnimation specialAnimation() {
        return isDrumming() ? HEAD_BANG : isBiting() ? BITE : null;
    }

    // --- the colony ---------------------------------------------------------------------------------------------

    /** A soldier that grew up from a nymph of this colony belongs to it from the start (and is counted). */
    @Override
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(rank.caste(), colony.getCount(rank.caste()) + 1);
    }

    /** It just had its last meal as a nymph. */
    @Override
    public void lastMealAsYoung(long now) {
        setLastFed(now);
    }

    /** This soldier joins no colony (one a test walks around by hand). */
    public void keepOutOfColonies() {
        colonyChecked = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return; // back from being frozen, it went into its colony's numbers (see colony.AwayLife)
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Once, on the first tick: a soldier put down with a spawn egg joins the nearest termite colony.
        if (!colonyChecked) {
            colonyChecked = true;
            ColonyManager manager = ColonyManager.get(serverLevel);
            if (getColonyId() == null || manager.getColony(getColonyId()) == null) {
                Colony colony = manager.findNearest(blockPosition(), JOIN_RADIUS, Species.MACROTERMES);
                setColonyId(null);
                if (colony != null) {
                    joinColony(colony);
                    manager.markChanged();
                }
            }
        }
        // Drumming: a quiet dry rattle in bursts (a tap every other tick for 12 ticks, then a rest).
        if (drumTicks > 0) {
            drumTicks--;
            if (drumTicks % 20 >= 8 && drumTicks % 2 == 0) {
                playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.08F, 1.6F + getRandom().nextFloat() * 0.4F);
            }
        }
        entityData.set(DATA_DRUMMING, drumTicks > 0);
        if (biteTicks > 0) {
            biteTicks--;
        }
        entityData.set(DATA_BITING, biteTicks > 0);
        // Now and then: does it feel the alarm drumming through the walls?
        if ((tickCount + getId()) % 5 == 0 && getColonyId() != null) {
            Colony colony = ColonyManager.get(serverLevel).getColony(getColonyId());
            if (colony != null) {
                colony.part(ColonyParts.ALARM).ifPresent(alarm -> alarm.listen(serverLevel, this));
            }
        }
    }

    // Once a second: the hunger clock (it only runs in a colony whose members get hungry).
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 != 0) {
            return;
        }
        long now = level().getGameTime();
        Colony colony = getColony();
        if (lastFed == Colony.NOT_SET || colony == null || !colony.isHungerOn()) {
            lastFed = now;
        }
        updateHungry(now - lastFed >= numbers().slowAfterTicks());
    }

    // A dead soldier leaves the colony's headcount.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                colony.setCount(rank.caste(), colony.getCount(rank.caste()) - 1);
                manager.markChanged();
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LastFed", lastFed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastFed = tag.contains("LastFed") ? tag.getLong("LastFed") : Colony.NOT_SET;
    }

    // A dead soldier leaves a body behind where it died (see AbstractAntEntity.remove).
    @Override
    protected boolean leavesBody() {
        return true;
    }

    @Override
    protected net.minecraft.world.entity.EntityType<AntCorpseEntity> bodyType() {
        return com.antcolonies.registry.ModEntities.TERMITE_CORPSE.get();
    }

    @Override
    public String nestCaste() {
        return rank.caste();
    }

    @Override
    public void comeOutOfNest(Colony colony, long grownUpAt) {
        super.comeOutOfNest(colony, grownUpAt);
        colonyChecked = true;
        lastFed = level().getGameTime();
    }
}
