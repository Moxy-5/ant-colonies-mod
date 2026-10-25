package com.antcolonies.savanna.entity;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.ColonyBorn;
import com.antcolonies.entity.WorkerHunger;
import com.antcolonies.entity.goal.AntWanderGoal;
import com.antcolonies.entity.goal.CarryEggGoal;
import com.antcolonies.entity.goal.DigGoal;
import com.antcolonies.entity.goal.EatGoal;
import com.antcolonies.entity.goal.FightGoal;
import com.antcolonies.entity.goal.FeedLarvaGoal;
import com.antcolonies.entity.goal.FeedQueenGoal;
import com.antcolonies.entity.goal.GoInsideGoal;
import com.antcolonies.entity.goal.WaitAtNestGoal;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.entity.goal.CarryRemainsGoal;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaTags;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

/**
 * A Matabele ant worker (Megaponera analis), a major or a minor: the model, clips and climbing made in M8 step 1, put
 * into the game by the user 2026-10-23 with a spawn egg each. M8 step 1c: it belongs to a colony. Real Matabele workers
 * are one caste on a continuous spread of sizes (5 to 18 mm, the majors about twice the minors' length), so majors and
 * minors are both the colony's workers ({@link Caste#WORKER}); each rolls its own length from the species file's
 * {@code worker_sizes} once, keeps it for life, and is drawn (and its box sized) at that length with whichever of the
 * two models is nearer. Step 1d: it digs the way into its colony's nest (only to join the spaces already there) and
 * stays inside (real Megaponera leave the nest only to raid, step 4); the raids, the fights and the
 * prey pile come with M8's steps ({@code docs/plans/m8-matabele.md}). It joins the nearest Matabele colony when put
 * down with a spawn egg, and it gets hungry (the user's pick, 2026-10-24: with no prey pile yet, a colony with workers
 * starves).
 */
public class MatabeleEntity extends AbstractAntEntity implements ColonyBorn {
    /** Its species id. */
    public static final ResourceLocation SPECIES = SavannaSpecies.MATABELE;
    /** Majors and minors are the colony's workers, one caste on one size spread. */
    public static final String CASTE = Caste.WORKER;
    /** How close (in blocks) a Matabele colony must be for a new worker to join it. */
    private static final double JOIN_RADIUS = 32.0;
    private static final String LENGTH_TAG = "BodyLength";

    /** A foe this far below it (blocks) is stabbed down into. */
    private static final double BELOW = 0.4;
    private static final Attack GRAB_AND_STING = new Attack("attack_a", 24);
    private static final Attack STAB_DOWN = new Attack("attack_b", 17);
    /** How near (blocks, to the block's middle) it must be to bite a cover open, and how many bites it takes. */
    private static final double BITE_REACH = 1.8;
    private static final int BITES_TO_OPEN = 3;

    private boolean colonyChecked;
    /** The cover it is biting open, and how many bites it has given it (not saved). */
    @Nullable
    private BlockPos breaking;
    private int bites;
    /** Its length in mm (0 until rolled, on its first tick). */
    private float lengthMm;
    @Nullable
    private WorkerHunger hunger; // (made on first use: the goals are set up before the fields, in the mob's constructor)

    public MatabeleEntity(EntityType<? extends MatabeleEntity> type, Level level) {
        super(type, level);
    }

    private WorkerHunger hunger() {
        if (hunger == null) {
            hunger = new WorkerHunger(this);
        }
        return hunger;
    }

    @Override
    public ResourceLocation species() {
        return SPECIES;
    }

    @Override
    public SpeciesProfile profile() {
        return SpeciesProfiles.get(SPECIES);
    }

    /** No body of its own kind yet (M8 step 5 has the dead carried home): it goes as any mob does. */
    @Override
    protected boolean leavesBody() {
        return false;
    }

    /**
     * A major: big and strong (the health and damage it really has come from the species file's {@code fight} numbers,
     * M8 step 2a; these are only the game's starting values). Majors and minors march
     * at one speed, about 2.5 blocks a second (the user's pick, 2026-10-24, lowered from 3 with the smaller majors; attribute 0.2275), the speed their walks are fitted to ({@code tools/anim_lab.py WALK_SPEED}).
     */
    public static AttributeSupplier.Builder createMajorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2275)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    /** A minor: half the length, lighter. */
    public static AttributeSupplier.Builder createMinorAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2275)
                .add(Attributes.ATTACK_DAMAGE, 1.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // M8 step 2b: sent to fight (the skirmish command; the raids later), or hurt by a termite, it fights.
        this.goalSelector.addGoal(0, new FightGoal(this));
        // Step 1e: they eat from the prey pile, feed the queen and the larvae from it, carry eggs and carry the waste out
        // (real Megaponera: any nestmate does any of it, no special nurses).
        this.goalSelector.addGoal(1, new EatGoal(this, hunger()));
        this.goalSelector.addGoal(2, new FeedQueenGoal(this));
        this.goalSelector.addGoal(2, new FeedLarvaGoal(this));
        this.goalSelector.addGoal(2, new CarryEggGoal(this));
        this.goalSelector.addGoal(2, new CarryRemainsGoal(this));
        this.goalSelector.addGoal(3, new DigGoal(this, () -> true));
        this.goalSelector.addGoal(4, new GoInsideGoal(this));
        this.goalSelector.addGoal(5, new WaitAtNestGoal(this));
        this.goalSelector.addGoal(6, new AntWanderGoal(this, 1.0, 120, 8, () -> true));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** The body a new worker of the colony gets, by the dice: the major's or the minor's, as often as the size spread draws each. */
    public static EntityType<MatabeleEntity> pickBody(RandomSource random) {
        SavannaNumbers.WorkerSizes sizes = sizes();
        boolean major = sizes == null || sizes.majorModelFor(sizes.roll(random));
        return major ? SavannaEntities.MATABELE_MAJOR.get() : SavannaEntities.MATABELE_MINOR.get();
    }

    @Nullable
    private static SavannaNumbers.WorkerSizes sizes() {
        return SpeciesProfiles.get(SPECIES).section(SavannaNumbers.WORKER_SIZES).orElse(null);
    }

    /** Whether this ant has the major's model (else the minor's). */
    public boolean hasMajorBody() {
        return getType() == SavannaEntities.MATABELE_MAJOR.get();
    }

    /** Its length in mm (0 before its first tick). */
    public float getLengthMm() {
        return lengthMm;
    }

    /**
     * Sets its length in mm (a test may: the largest and smallest for climbing) and with it the size it is drawn at and
     * its box, as {@link Attributes#SCALE} (the game sends it to the players and keeps it in the save).
     */
    public void setLengthMm(float length) {
        lengthMm = length;
        SavannaNumbers.WorkerSizes sizes = sizes();
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (sizes != null && scale != null && length > 0.0F) {
            scale.setBaseValue(length / (hasMajorBody() ? sizes.majorModelMm() : sizes.minorModelMm()));
        }
    }

    @Override
    protected boolean hasColonyRole() {
        return true;
    }

    @Override
    public void keepOutOfColonies() {
        colonyChecked = true;
    }

    @Override
    public void joinColony(Colony colony) {
        setColonyId(colony.getId());
        colonyChecked = true;
        colony.setCount(CASTE, colony.getCount(CASTE) + 1);
    }

    @Override
    public long getLastMeal() {
        return hunger().getLastAte();
    }

    @Override
    public void setLastMeal(long gameTime) {
        hunger().setLastAte(gameTime);
    }

    /** Whether it has gone long enough without a meal to slow down. */
    public boolean isHungry() {
        return hunger().isHungry();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            hunger().tick(serverLevel);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hunger().save(tag);
        tag.putFloat(LENGTH_TAG, lengthMm);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hunger().load(tag);
        if (tag.contains(LENGTH_TAG)) {
            setLengthMm(tag.getFloat(LENGTH_TAG));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || !(level() instanceof ServerLevel serverLevel)) {
            return; // (back from being frozen, it may have gone into its colony's numbers, see colony.AwayLife)
        }
        // Once, on its first tick: its own length, from the size spread its model draws.
        if (lengthMm <= 0.0F) {
            SavannaNumbers.WorkerSizes sizes = sizes();
            if (sizes != null) {
                setLengthMm(sizes.rollFor(hasMajorBody(), getRandom()));
            }
        }
        // Once, on its first tick: a worker put down with a spawn egg joins the nearest Matabele colony.
        if (!colonyChecked) {
            colonyChecked = true;
            ColonyManager manager = ColonyManager.get(serverLevel);
            if (getColonyId() == null || manager.getColony(getColonyId()) == null) {
                Colony colony = manager.findNearest(blockPosition(), JOIN_RADIUS, SPECIES);
                setColonyId(null);
                if (colony != null) {
                    joinColony(colony);
                    manager.markChanged();
                }
            }
        }
    }

    // A dead worker leaves the colony's headcount.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (getColonyId() != null && level() instanceof ServerLevel serverLevel) {
            ColonyManager manager = ColonyManager.get(serverLevel);
            Colony colony = manager.getColony(getColonyId());
            if (colony != null) {
                dropLoadOnDeath(serverLevel, colony);
                colony.setCount(CASTE, colony.getCount(CASTE) - 1);
                manager.markChanged();
            }
        }
    }

    @Override
    public String nestCaste() {
        return CASTE;
    }

    /** Its body in the species file's fight numbers: the major's or the minor's (M8 step 2a). */
    @Override
    public String fightBody() {
        return hasMajorBody() ? "major" : "minor";
    }

    /**
     * How it attacks (M8 step 2b, the user's pick, 2026-10-23): a termite in front is grabbed in the jaws and stung under
     * the body (attack_a); one below it, down in a broken tunnel, is bitten and stabbed down into (attack_b).
     */
    @Override
    protected Attack attackFor(LivingEntity foe) {
        return foe.getY() < getY() - BELOW ? STAB_DOWN : GRAB_AND_STING;
    }

    /**
     * A termite it cannot reach, under mud or in a gallery just under the ground (M8 step 2b): a major bites the cover
     * open (real Megaponera: the big workers break the termites' mud open, the small ones go in and sting). The cover is
     * the termites' mud over or beside the termite (sheeting, a cast), or the one block of soil over its gallery when
     * the sky is right above that; never the mound. It walks up to the cover, bites at it {@link #BITES_TO_OPEN} times
     * and the block falls away. A minor leaves it.
     */
    @Override
    public boolean openWayTo(ServerLevel level, LivingEntity foe) {
        if (!hasMajorBody()) {
            return false;
        }
        BlockPos cover = coverOver(level, foe.blockPosition());
        if (cover == null) {
            breaking = null;
            return false;
        }
        if (distanceToSqr(Vec3.atCenterOf(cover)) > BITE_REACH * BITE_REACH) {
            breaking = null;
            List<BlockPos> stands = new ArrayList<>();
            stands.add(cover.above());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos beside = cover.relative(side);
                if (!beside.equals(foe.blockPosition()) && level.getBlockState(beside).getCollisionShape(level, beside).isEmpty()) {
                    stands.add(beside);
                }
            }
            return getNavigator().moveTo(stands, 1.2);
        }
        getNavigator().stop();
        getLookControl().setLookAt(Vec3.atCenterOf(cover));
        if (!cover.equals(breaking)) {
            breaking = cover;
            bites = 0;
        }
        playAttack(STAB_DOWN);
        playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.6F, 1.4F);
        if (++bites >= BITES_TO_OPEN) {
            level.destroyBlock(cover, false, this);
            breaking = null;
        }
        return true;
    }

    /** What covers this termite's cell that a major may bite open, nearest the sky first; null for nothing it may open. */
    @Nullable
    private static BlockPos coverOver(ServerLevel level, BlockPos cell) {
        BlockPos above = cell.above();
        BlockState roof = level.getBlockState(above);
        if (roof.is(SavannaTags.MATABELE_BREAKS_OPEN) && (isMud(roof) || level.canSeeSky(above.above()))) {
            return above;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = cell.relative(side);
            if (isMud(level.getBlockState(beside))) {
                return beside;
            }
        }
        return null;
    }

    private static boolean isMud(BlockState state) {
        return state.is(SavannaBlocks.MUD_CRUST.get()) || state.is(SavannaBlocks.MUD_CAST.get());
    }

    @Override
    public void comeOutOfNest(Colony colony, long grownUpAt) {
        super.comeOutOfNest(colony, grownUpAt);
        colonyChecked = true;
        hunger().eat();
    }
}
