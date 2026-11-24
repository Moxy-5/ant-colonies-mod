package com.antcolonies.savanna.animal;

import com.antcolonies.entity.body.SteadyController;
import javax.annotation.Nullable;

import java.util.List;
import java.util.UUID;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Carried;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.animal.SeekMateGoal;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Nile tilapia (M9 river life, the grazing fish, 2026-11-14; a named species, the user's OK; animals are fun first,
 * inspired by real life): a deep-bodied fish of the savanna's rivers, pans and oxbows that swims in schools and grazes
 * the river algae ({@link GrazeAlgaeGoal}, {@code AlgaeBlock.graze}), the second link of the food chain.
 *
 * <p>Three looks from one model (the user, 2026-11-14, "do them all for variation"; my picks): a young fish is pale and
 * strongly barred with the black "tilapia spot" ({@link #YOUNG}) and small; grown it is olive-silver ({@link #EVERYDAY}),
 * and a male wears the breeding colours ({@link #BREEDING}: dark blue-grey, red throat, red-edged fins) only while he
 * holds a nest pit (animal life cycle step 2, 2026-11-19; before, 1 male in 3 wore them for life). A young fish grows its
 * size little by little. Each grown fish has a length of its own (0.85 to 1.15 of the usual, my pick), drawn and boxed
 * through {@link Attributes#SCALE}.
 *
 * <p>Breeding (animal life cycle step 2, {@code docs/plans/animal-life-cycle.md}; numbers in its life file): a ready male
 * fans a nest pit out of the shallow bed and holds it ({@link TilapiaNestGoal}); a ready female comes to him
 * ({@link SeekMateGoal}), they circle over the pit, and she takes the eggs into her <b>mouth</b> (the real tilapia's
 * mouthbrooding): her throat swells (its own model) and she does not eat. After the hatching days she lets out 4 to 6
 * tiny fry that stay round her face ({@link TilapiaFryGoal}); when a catfish, a crocodile, a monitor or a player comes
 * near she takes them back into her mouth (the kit's carrying hook, {@link Carried}) and lets them out again when it is
 * gone. After the care days they school on their own as young fish. Fish busy breeding neither lead nor join a school.
 *
 * <p>It never starves (my pick: the food chain must never crash): a hungry fish with no algae near just swims on.
 */
public class TilapiaEntity extends AbstractSchoolingFish implements GeoEntity, HasLife, RiverCover.Hider {
    public static final int YOUNG = 0, EVERYDAY = 1, BREEDING = 2;
    /** How near a danger makes a mother take her fry into her mouth (blocks). */
    static final double DANGER_NEAR = 6;
    /** How long after the danger is gone she lets them out again (ticks). */
    static final int CALM_TICKS = 60;
    /** How near her a fry must be to be taken in (blocks). */
    static final double TAKE_WITHIN = 4;

    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(TilapiaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GRAZING = SynchedEntityData.defineId(TilapiaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(TilapiaEntity.class);
    /** Her mouth full of eggs or fry: drawn with the swollen throat. */
    private static final EntityDataAccessor<Boolean> DATA_MOUTH_FULL = SynchedEntityData.defineId(TilapiaEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Its life: sex, age (young for 3 days from half size, its life file), own size (the animal life kit). */
    private final AnimalLife life;
    /** Ticks until it is hungry again ({@link GrazeAlgaeGoal}). */
    int hungryIn;
    /** The drawn body's pitch (degrees, nose up positive), eased toward its swimming direction (client only). */
    private float swimPitch, swimPitchO;

    // --- breeding (animal life cycle step 2) ---
    /** A male's nest pit while he holds it, and since when (game time). */
    @Nullable
    private BlockPos nest;
    private long nestSince;
    /** For tests: the next look round starts a nest at once (skips the chance). */
    public boolean wantsNestNow;
    /** A female's eggs in her mouth, and since when they were laid (game time). */
    private int eggs;
    private long eggsSince;
    /** Until when (game time) she cares for her fry. */
    private long caringUntil;
    /** Her fry taken back into her mouth from danger. */
    private final Carried mouth = new Carried();
    private int calm;
    /** A fry's mother. */
    @Nullable
    private UUID mother;

    public TilapiaEntity(EntityType<? extends TilapiaEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        hungryIn = 200 + level.random.nextInt(1200);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSchoolingFish.createAttributes().add(Attributes.MAX_HEALTH, 4.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new TilapiaFryGoal(this));
        // a cruising bull shark: the group swims aside (the catfish and crocodile lie in ambush, so nothing to see coming)
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this, BullSharkEntity.class,
                BullSharkEntity.FISH_PART_FROM, 1.2, 1.5, BullSharkEntity::partsFish));
        goalSelector.addGoal(2, new TilapiaNestGoal(this));
        goalSelector.addGoal(3, new GrazeAlgaeGoal(this));
        goalSelector.addGoal(4, new SeekMateGoal<>(this, 1.0));
        // scared: into the reeds (the food web's step 4); a fry keeps to its mother, a male on his nest pit and a mother
        // with eggs or fry stand their ground
        goalSelector.addGoal(1, new HideInCoverGoal(this, () -> !isYoung() && nest == null && eggs == 0 && !caring() && mouth.isEmpty()));
    }

    /** Its hiding in cover (made on first use: Minecraft sets up the goals before this class's own fields are filled in). */
    private RiverCover.Hiding hiding;

    @Override
    public RiverCover.Hiding hiding() {
        if (hiding == null) {
            hiding = new RiverCover.Hiding();
        }
        return hiding;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOOK, EVERYDAY);
        builder.define(DATA_GRAZING, false);
        builder.define(DATA_MOUTH_FULL, false);
        AnimalLife.define(builder, DATA_LIFE);
    }

    /** {@link #YOUNG}, {@link #EVERYDAY} or {@link #BREEDING} (the texture it wears). */
    public int look() {
        return entityData.get(DATA_LOOK);
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    public boolean isYoung() {
        return life.isYoung();
    }

    public boolean isMale() {
        return life.isMale();
    }

    public boolean isGrazing() {
        return entityData.get(DATA_GRAZING);
    }

    void setGrazing(boolean grazing) {
        entityData.set(DATA_GRAZING, grazing);
    }

    /** Hatches it as a young fish (or makes it grown), its sex and grown size rolled. */
    public void setUp(boolean young, RandomSource random) {
        life.setUp(young, random);
        refresh();
    }

    /** Drawn in a catfish's mouth (client only, {@link CatfishEntity#heldDrawn}): it plays its "caught" clip. */
    private boolean caught;
    /** Drawn in a catfish's mouth on a catch that ends bitten in two: it plays "caught_fight" once, beside the catfish's "bite_off". */
    private boolean fighting;

    /**
     * Makes this a fish to draw in a catfish's mouth (client only, in no world): this look, this size, thrashing;
     * fighting: the catch ends bitten in two, so it fights harder and harder until the jaws come down (the user, 2026-11-15).
     */
    void showCaught(int look, float size, boolean fighting) {
        caught = true;
        this.fighting = fighting;
        entityData.set(DATA_LOOK, look);
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(size);
        }
        yBodyRot = yBodyRotO = 180;     // drawn with no turn of its own: the catfish's mouth places it
        setYRot(180);
        yRotO = 180;
    }

    /** For tests: makes it hungry now. */
    public void makeHungry() {
        hungryIn = 0;
    }

    /** Its own grown length against the usual. */
    public float ownSize() {
        return life.ownSize();
    }

    /** For tests: makes it this many ticks from grown. */
    public void setTicksToGrow(int ticks) {
        life.setAge(life.numbers().grownAge() - ticks);
        refresh();
    }

    /** Its look and size from its age, its nest and its mouth. */
    private void refresh() {
        if (level().isClientSide) {
            return;
        }
        entityData.set(DATA_LOOK, isYoung() ? YOUNG : holdsNest() ? BREEDING : EVERYDAY);
        entityData.set(DATA_MOUTH_FULL, mouthFull());
        life.apply();
    }

    // --- breeding ------------------------------------------------------------------------------------------------------

    /** Whether her mouth is full, of eggs or of fry (drawn with the swollen throat; she does not eat). */
    public boolean mouthFull() {
        return level().isClientSide ? entityData.get(DATA_MOUTH_FULL) : eggs > 0 || !mouth.isEmpty();
    }

    /** A male's nest pit, if he holds one. */
    @Nullable
    public BlockPos nest() {
        return nest;
    }

    public boolean holdsNest() {
        return nest != null;
    }

    long nestSince() {
        return nestSince;
    }

    /** He has fanned out a pit here and holds it. */
    void takeNest(BlockPos at) {
        nest = at.immutable();
        nestSince = level().getGameTime();
        leaveSchool();
        refresh();
    }

    /** He leaves his pit (it stays and fades). */
    void leaveNest() {
        nest = null;
        refresh();
    }

    /** How many eggs she carries in her mouth. */
    public int eggsInMouth() {
        return eggs;
    }

    /** How many fry she has taken back into her mouth. */
    public int fryInMouth() {
        return mouth.count();
    }

    /** Whether she is caring for fry now. */
    public boolean caring() {
        return caringUntil > level().getGameTime();
    }

    /** A fry's mother, if it has one. */
    @Nullable
    public UUID motherId() {
        return mother;
    }

    /** Whether it is a fry still kept by its mother (young, within its first care days). */
    public boolean withMother() {
        return mother != null && isYoung() && life.age() < life.numbers().careTicks();
    }

    void loseMother() {
        mother = null;
    }

    /** Whether it is busy breeding: then it neither leads nor joins a school. */
    public boolean busyBreeding() {
        return holdsNest() || eggs > 0 || !mouth.isEmpty() || caring() || withMother();
    }

    /** A tilapia male waits on his pit for the female to come; only a female goes looking. */
    @Override
    public boolean seeksMate() {
        return !isMale();
    }

    /** A female may pick a male only while he holds a nest pit. */
    @Override
    public boolean takesMate() {
        return isMale() && holdsNest();
    }

    /** The pair circle slowly over his pit, side by side head to tail (her goal steers them both). */
    @Override
    public void courting(Mob mate, int ticks) {
        if (!(mate instanceof TilapiaEntity male) || male.nest() == null || ticks % 4 != 0) {
            return;
        }
        Vec3 middle = Vec3.atBottomCenterOf(male.nest()).add(0, 0.4, 0);
        double a = ticks * 0.06;
        Vec3 her = middle.add(Math.cos(a) * 0.35, 0, Math.sin(a) * 0.35);
        Vec3 him = middle.add(-Math.cos(a) * 0.35, 0, -Math.sin(a) * 0.35);
        getNavigation().moveTo(her.x, her.y, her.z, 0.6);
        male.getNavigation().moveTo(him.x, him.y, him.z, 0.6);
        male.getLookControl().setLookAt(this, 30, 30);
        if (ticks % 20 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.BUBBLE, middle.x, middle.y, middle.z, 3, 0.3, 0.1, 0.3, 0.02);
        }
    }

    /** She takes the eggs into her mouth; he leaves his pit. */
    @Override
    public void mated(Mob mate) {
        if (isMale()) {
            leaveNest();
            return;
        }
        int[] clutch = {life.numbers().clutchMin(), life.numbers().clutchMax()};
        eggs = clutch[0] + random.nextInt(clutch[1] - clutch[0] + 1);
        eggsSince = level().getGameTime();
        leaveSchool();
        refresh();
    }

    /** Stops following and lets its own followers go (a fish busy breeding has no school). */
    private void leaveSchool() {
        stopFollowing();
        if (hasFollowers()) {
            for (TilapiaEntity other : level().getEntitiesOfClass(TilapiaEntity.class, getBoundingBox().inflate(16),
                    t -> t.followedForTests() == this)) {
                other.stopFollowing();
            }
        }
    }

    /** Where her face is: just in front of her snout (the fry's cloud, where they are let out). */
    Vec3 facePoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = 0.35 * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + getBbHeight() * 0.45, getZ() + Mth.cos(yaw) * ahead);
    }

    /** For tests: her eggs have been in her mouth this many ticks already. */
    public void ageEggs(long ticks) {
        eggsSince = level().getGameTime() - ticks;
    }

    /**
     * A mother's day (server, every tick): her eggs hatch into fry round her face after the hatching days (or are lost
     * when the river has no room for them); while she cares for her fry she takes them into her mouth when danger comes
     * near and lets them out a few seconds after it has gone.
     */
    private void motherTick() {
        long now = level().getGameTime();
        if (eggs > 0 && now - eggsSince >= life.numbers().hatchTicks()) {
            if (RoomToBreed.here(this, life.numbers().localLimit()) && level() instanceof ServerLevel server) {
                letOutFry(Math.min(eggs, RoomToBreed.left(server, this, life.numbers().localLimit())));   // the rest fail (code review 2026-11-21)
                caringUntil = now + life.numbers().careTicks();
            }
            eggs = 0;
            refresh();
        }
        if (!caring()) {
            if (!mouth.isEmpty()) {
                letOutCarried();
            }
            return;
        }
        if (tickCount % 5 != 0) {
            return;
        }
        if (dangerNear()) {
            calm = 0;
            if (mouth.isEmpty()) {
                takeFryIn();
            }
        } else if (!mouth.isEmpty() && (calm += 5) >= CALM_TICKS) {
            letOutCarried();
        }
    }

    private boolean dangerNear() {
        return !level().getEntities(this, getBoundingBox().inflate(DANGER_NEAR), e -> e.isAlive()
                && (e instanceof CatfishEntity || e instanceof CrocodileEntity || e instanceof MonitorEntity || e instanceof BullSharkEntity
                || e instanceof Player p && !p.isSpectator())).isEmpty();
    }

    private void takeFryIn() {
        List<TilapiaEntity> fry = level().getEntitiesOfClass(TilapiaEntity.class, getBoundingBox().inflate(TAKE_WITHIN),
                t -> t.isAlive() && getUUID().equals(t.motherId()) && t.withMother());
        for (TilapiaEntity f : fry) {
            mouth.take(f);
        }
        if (!fry.isEmpty()) {
            refresh();
        }
    }

    private void letOutCarried() {
        if (level() instanceof ServerLevel server) {
            mouth.letOut(server, facePoint());
        }
        calm = 0;
        refresh();
    }

    private void letOutFry(int count) {
        Vec3 face = facePoint();
        for (int i = 0; i < count; i++) {
            TilapiaEntity fry = SavannaEntities.TILAPIA.get().create(level());
            if (fry == null) {
                return;
            }
            fry.moveTo(face.x + (random.nextDouble() - 0.5) * 0.3, face.y, face.z + (random.nextDouble() - 0.5) * 0.3, random.nextFloat() * 360, 0);
            fry.setUp(true, random);
            fry.life().setAge(0);
            fry.mother = getUUID();
            fry.refresh();
            level().addFreshEntity(fry);
        }
    }

    /** Her fry in her mouth swim out if she is caught or taken away (fun first: some fry escape the catfish). */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && !mouth.isEmpty() && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            letOutCarried();
        }
        super.remove(reason);
    }

    // a fish busy breeding never leads nor joins a school
    @Override
    public boolean canBeFollowed() {
        return !busyBreeding() && super.canBeFollowed();
    }

    @Override
    public void addFollowers(java.util.stream.Stream<? extends AbstractSchoolingFish> followers) {
        if (!busyBreeding()) {
            super.addFollowers(followers.filter(f -> !(f instanceof TilapiaEntity t) || !t.busyBreeding()));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swimPitchO = swimPitch;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float want = isInWater() && flat + Math.abs(dy) > 0.01 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, flat)), -35, 35) : 0;
            swimPitch += (want - swimPitch) * 0.15F;
            return;
        }
        if (life.tick()) {
            refresh();
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (eggs > 0 || caringUntil > 0 || !mouth.isEmpty()) {
            motherTick();
            if (!caring() && eggs == 0 && mouth.isEmpty()) {
                caringUntil = 0;
            }
        }
    }

    /** The drawn body's pitch this frame (degrees, nose up positive). */
    public float swimPitch(float partialTick) {
        return Mth.lerp(partialTick, swimPitchO, swimPitch);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        setUp(spawnType != MobSpawnType.BUCKET && life.spawnsYoung(level.getRandom()), level.getRandom());
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeLife(tag);
        if (nest != null) {
            tag.putLong("Nest", nest.asLong());
            tag.putLong("NestSince", nestSince);
        }
        if (eggs > 0) {
            tag.putInt("Eggs", eggs);
            tag.putLong("EggsSince", eggsSince);
        }
        if (caringUntil > 0) {
            tag.putLong("CaringUntil", caringUntil);
        }
        mouth.save(tag, "Mouth");
        if (mother != null) {
            tag.putUUID("Mother", mother);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        nest = tag.contains("Nest") ? BlockPos.of(tag.getLong("Nest")) : null;
        nestSince = tag.getLong("NestSince");
        eggs = tag.getInt("Eggs");
        eggsSince = tag.getLong("EggsSince");
        caringUntil = tag.getLong("CaringUntil");
        mouth.load(tag, "Mouth");
        mother = tag.hasUUID("Mother") ? tag.getUUID("Mother") : null;
        readLife(tag);
        refresh();
    }

    private void writeLife(CompoundTag tag) {
        life.save(tag);
    }

    /** Its life from its data (a fish saved before the kit kept its age as "TilapiaAge"); a fish with none keeps what it has. */
    private void readLife(CompoundTag tag) {
        boolean has = tag.contains("Life") || tag.contains("TilapiaAge") || tag.contains("sex") || tag.contains("young");
        life.load(tag, "TilapiaAge");
        if (has) {
            refresh();
        }
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, this::writeLife);
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        readLife(tag);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(SavannaItems.TILAPIA_BUCKET.get());
    }

    /**
     * Never counts as idle for its wandering (2026-11-14, the user's report: after the catfish's strikes the tilapia
     * huddled in a corner and stood still). Minecraft lets a mob wander only while its idle clock is under 5 s, and
     * winds that clock back only while a player who is not in spectator mode is within 32 blocks; with the user
     * watching in spectator nothing wound it back, so the school's leader stopped wandering and its school sat by it.
     * River fish always wander (cheap: at most 60 near a player). Only the wandering reads this; Minecraft's own
     * despawning still reads the real clock.
     */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Fish within this many blocks of a player are never removed (my pick); past 128 they always are. */
    public static final double KEEP_NEAR = 96.0;

    /**
     * The river stays full wherever the player goes (2026-11-14, the user: "I want the river to be alive with fish, not
     * speckles of them"). Tilapia are in their own spawning group, river fish ({@code SavannaMobCategories}), with its
     * own cap of 60 round a player, apart from Minecraft's cod, salmon and tropical fish (20). A fish near a player
     * stays put; one left more than 96 blocks behind may go and past 128 always does, so the cap is free to fill the
     * water ahead. A fish from a bucket or with a name stays for ever (Minecraft's own fish rule, kept).
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return super.removeWhenFarAway(distanceToClosestPlayer) && distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR;
    }

    /** How far off a fish is still drawn (blocks; Minecraft's own rule, by the box's size, gave about 25). */
    static final double DRAW_DISTANCE = 64.0;

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    /** The fish it follows (Minecraft keeps its own copy private), or none. */
    @Nullable
    private AbstractSchoolingFish followed;

    @Override
    public AbstractSchoolingFish startFollowing(AbstractSchoolingFish leader) {
        if (busyBreeding() || leader instanceof TilapiaEntity t && t.busyBreeding()) {
            return leader;          // busy breeding: no school
        }
        followed = leader;
        return super.startFollowing(leader);
    }

    @Override
    public void stopFollowing() {
        if (followed != null) {
            super.stopFollowing();
        }
        followed = null;
    }

    /** For tests: the fish it follows, or none. */
    @Nullable
    public AbstractSchoolingFish followedForTests() {
        return isFollower() ? followed : null;
    }

    @Override
    public int getMaxSchoolSize() {
        return 8;
    }

    @Override
    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COD_HURT;
    }

    /**
     * Where it may spawn: in water of the savanna's rivers, pans and oxbows, from 13 under sea level up to 6 over it
     * (the pans lie a block or two up on the floodplain; Minecraft's own fish rule stops at sea level).
     */
    public static boolean canSpawn(EntityType<TilapiaEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 13 && pos.getY() <= sea + 6
                && level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation GRAZE = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation FLOP = RawAnimation.begin().thenLoop("flop");
    private static final RawAnimation CAUGHT = RawAnimation.begin().thenLoop("caught");
    private static final RawAnimation HELD = RawAnimation.begin().thenLoop("held");
    private static final RawAnimation HELD_DOWN = RawAnimation.begin().thenLoop("held_down");
    private static final RawAnimation CAUGHT_FIGHT = RawAnimation.begin().thenPlayAndHold("caught_fight");
    private static final RawAnimation DEATH =RawAnimation.begin().thenPlayAndHold("death");
    /** The swim clip's beat is made for this speed (blocks a tick, about 1.2 a second); it plays faster or slower by it. */
    static final double SWIM_CLIP_SPEED = 0.06;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 4, this::animate));
    }

    private PlayState animate(AnimationState<TilapiaEntity> state) {
        AnimationController<TilapiaEntity> player = state.getController();
        if (getVehicle() instanceof OtterEntity otter) {   // in an otter's jaws: on its side, thrashing in bursts;
            // pinned on the ground as it eats it: curled, twitching
            if (otter.doing() == OtterEntity.EAT) {
                return state.setAndContinue(HELD_DOWN);
            }
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(HELD);
        }
        if (caught && fighting) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(CAUGHT_FIGHT);
        }
        if (caught || getVehicle() instanceof CrocodileEntity) {     // in a catfish's mouth, or held in a crocodile's jaws
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(CAUGHT);
        }
        if (isDeadOrDying()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(DEATH);
        }
        if (!isInWater()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(FLOP);
        }
        if (isGrazing()) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(GRAZE);
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed < 0.008) {
            player.setAnimationSpeed(1.0);
            return state.setAndContinue(IDLE);
        }
        player.setAnimationSpeed(Mth.clamp(speed / SWIM_CLIP_SPEED, 0.5, 2.5));
        return state.setAndContinue(SWIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
