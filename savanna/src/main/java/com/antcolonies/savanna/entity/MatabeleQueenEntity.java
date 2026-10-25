package com.antcolonies.savanna.entity;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.GoInsideGoal;
import com.antcolonies.entity.goal.QueenToRoomGoal;
import com.antcolonies.entity.goal.WaitAtNestGoal;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.resources.ResourceLocation;
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
 * The Matabele queen (M8 step 1c, 2026-10-24): Megaponera's queen is wingless (ergatoid), a major's body a little larger
 * with a swollen gaster (the user's look B, 1.2 times a major). She is her colony's queen: a real one never founds alone
 * (a colony splits in two, M9), but until then one put down with a spawn egg starts an empty colony where she stands,
 * and spawn-egg majors and minors near her join it (the user's pick, 2026-10-24; {@code /antcolonies matabele} makes a
 * whole colony). Her colony ends when she dies; unfed for her species' {@code queen.starve_ticks} once she has workers
 * she starves (with no prey pile yet, M8 step 1e, she does). Her colony settles in the nearest free dead termite mound
 * within {@value MatabeleNest#LOOK} blocks (step 1d, {@link MatabeleNest}, the user's call, 2026-10-24: only dead mounds
 * for now); she walks to it and down into the old royal chamber. With none near she has no colony yet: she wanders and
 * looks again every {@link #LOOK_AGAIN} ticks (a mound may die near her), and starts her colony once one is found.
 */
public class MatabeleQueenEntity extends AbstractAntEntity {
    /** How often a queen with no home yet looks again for a dead mound (ticks; my pick, 10 seconds). */
    static final int LOOK_AGAIN = 200;
    private boolean colonyChecked;

    public MatabeleQueenEntity(EntityType<? extends MatabeleQueenEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation species() {
        return MatabeleEntity.SPECIES;
    }

    @Override
    public SpeciesProfile profile() {
        return SpeciesProfiles.get(MatabeleEntity.SPECIES);
    }

    @Override
    protected boolean leavesBody() {
        return false;
    }

    /**
     * Heavy with eggs she walks at half the workers' pace, about 1.5 blocks a second (attribute 0.137, the speed her
     * walks are fitted to, {@code tools/anim_lab.py WALK_SPEED}); health and sting my picks, to be set with the fights (M8 step 2).
     */
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.137)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new QueenToRoomGoal(this));
        this.goalSelector.addGoal(3, new GoInsideGoal(this));
        this.goalSelector.addGoal(4, new WaitAtNestGoal(this));
        this.goalSelector.addGoal(5, new AntWanderGoal(this, 0.6, 400, 3, () -> QueenToRoomGoal.findFinishedRoom(this) == null));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    /** She is the queen of this colony (made for her, by the command or new land). */
    public void becomeQueenOf(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
    }

    /** She starts no colony (a queen a test walks around by hand). */
    @Override
    public void keepOutOfColonies() {
        colonyChecked = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(serverLevel);
        // On the first tick, then every LOOK_AGAIN ticks until she has one: a colony for this queen, settled in the
        // nearest free dead mound near her.
        if (!colonyChecked && (tickCount <= 1 || tickCount % LOOK_AGAIN == 0)) {
            if (getColonyId() != null && manager.getColony(getColonyId()) != null) {
                colonyChecked = true;
            } else {
                MatabeleNest.Site site = MatabeleNest.pick(serverLevel, blockPosition());
                if (site != null) {
                    setColonyId(MatabeleNest.settle(serverLevel, site, getUUID()).getId());
                    colonyChecked = true;
                }
            }
        }
        // Once a second: unfed for days (here, or while away), she dies and the colony ends.
        if (tickCount % 20 == 0 && getColonyId() != null) {
            Colony colony = manager.getColony(getColonyId());
            if (colony != null && colony.queenCare().queenStarves(serverLevel)) {
                hurt(damageSources().starve(), 1000.0F);
            }
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
