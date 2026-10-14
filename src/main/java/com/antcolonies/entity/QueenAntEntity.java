package com.antcolonies.entity;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.colony.GroundReader;
import com.antcolonies.colony.NestSite;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.FindNestSiteGoal;
import com.antcolonies.entity.goal.QueenToRoomGoal;
import com.antcolonies.entity.goal.SealEntranceGoal;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Queen ant: bigger, tougher and slow. The colony lives and dies with her. On her own she founds the colony like a
 * real harvester ant queen: she digs a small founding chamber just under the surface, seals the entrance from inside,
 * and raises her founding batch on her own body fat (species profile {@code nest.founding_chamber}, {@code founding};
 * see {@code QueenCare.raiseFoundingBatch}); the first workers open the entrance and dig the rest of the nest.
 * Landed on uneven ground, she first walks a little to a level spot (profile {@code founding.site_search_radius}).
 */
public class QueenAntEntity extends AbstractAntEntity {
    /** How far (in blocks) behind the queen her egg comes out. */
    private static final double EGG_BEHIND = 0.7;

    private boolean colonyChecked;
    /** The level spot she is walking to before founding her colony (not saved: after a reload she looks again). */
    @Nullable
    private BlockPos siteTarget;

    public QueenAntEntity(EntityType<? extends QueenAntEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.ATTACK_DAMAGE, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Landed on uneven ground with no colony yet: she walks to a level spot near her first, then founds it there.
        this.goalSelector.addGoal(1, new FindNestSiteGoal(this, () -> siteTarget, this::foundColony));
        // Alone (no workers yet), once her founding chamber is dug she closes the entrance from inside.
        this.goalSelector.addGoal(1, new SealEntranceGoal(this));
        // She helps dig the shaft and her own room; when the room is finished she goes down and sits in its middle.
        this.goalSelector.addGoal(2, new QueenToRoomGoal(this));
        // Otherwise she hardly ever wanders: low chance to start a stroll, and never far. Settled in her room, never.
        this.goalSelector.addGoal(3, new DigGoal(this, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(4, new AntWanderGoal(this, 0.6, 400, 4, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // Every second: has the egg she carries grown? Then it comes out behind her (she has to be in her room).
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 != 0 || getColonyId() == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(serverLevel);
        Colony colony = manager.getColony(getColonyId());
        if (colony == null) {
            return;
        }
        if (colony.queenCare().queenStarves(serverLevel)) {
            hurt(damageSources().starve(), 1000.0F); // five days without food: she dies and the colony ends (see die)
            return;
        }
        if (tickCount % 1200 == 0) {
            manager.markChanged(); // the hunger clock changes all the time: make sure it gets saved
        }
        if (colony.queenCare().raiseFoundingBatch(serverLevel, this)) {
            manager.markChanged();
        }
        if (!colony.queenCare().isEggDue(serverLevel) || !colony.queenCare().isQueenHome(serverLevel)) {
            return;
        }
        Vec3 at = position().subtract(Vec3.directionFromRotation(0.0F, getYRot()).scale(EGG_BEHIND));
        BlockPos cell = BlockPos.containing(at.x, getY() + 0.1, at.z);
        if (!serverLevel.getBlockState(cell).getCollisionShape(serverLevel, cell).isEmpty()) {
            at = position(); // a wall behind her: the egg comes out where she sits
        }
        colony.queenCare().layEgg(serverLevel, at.x, getY(), at.z);
        manager.markChanged();
    }

    // She digs her own room with the workers, but cannot place blocks: no soil pellets for the hill, no sealing of
    // holes in the nest walls (closing the entrance while she founds is a goal of its own).
    @Override
    public boolean canPlaceBlocks() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        // Once, on the first tick: make sure the brain has a colony for this queen.
        if (!colonyChecked && level() instanceof ServerLevel serverLevel) {
            colonyChecked = true;
            ColonyManager manager = ColonyManager.get(serverLevel);
            @Nullable var existing = getColonyId() == null ? null : manager.getColony(getColonyId());
            if (existing == null) {
                siteTarget = levelSpotToWalkTo(serverLevel);
                if (siteTarget == null) {
                    foundColony();
                }
            }
        }
    }

    /**
     * Where she should walk before founding, or null to found right here: her spot is level, no spot near her is more
     * level, or she is not standing on open ground (spawned into a nest in a test, or with no free will).
     */
    @Nullable
    private BlockPos levelSpotToWalkTo(ServerLevel level) {
        SpeciesProfile.Founding founding = SpeciesProfiles.get(Species.RED_HARVESTER_ANT).founding().orElse(null);
        BlockPos feet = blockPosition();
        if (founding == null || founding.siteSearchRadius() <= 0 || isNoAi()
                || !GroundReader.surface(level, feet.getX(), feet.getZ()).equals(feet.below())) {
            return null;
        }
        BlockPos spot = NestSite.findLevelSpot(level, feet, founding.siteSearchRadius(), founding.maxUnevenness());
        if (spot != null) {
            com.antcolonies.AntColoniesMod.LOGGER.debug("[nest-site] a queen at {} on uneven ground walks to {} to found her nest",
                    feet.toShortString(), spot.toShortString());
        }
        return spot;
    }

    /** Founds her colony where she stands. */
    private void foundColony() {
        siteTarget = null;
        if (getColonyId() == null && level() instanceof ServerLevel serverLevel) {
            Colony colony = ColonyManager.get(serverLevel).createColony(Species.RED_HARVESTER_ANT, getUUID(), blockPosition());
            setColonyId(colony.getId());
        }
    }

    // The colony dies when the queen dies.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager.queenDied(serverLevel, getColonyId());
        }
    }
}
