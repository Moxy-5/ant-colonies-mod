package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Attention;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.HitChain;
import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.entity.body.MoveRider;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
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
 * The African clawless otter (M9 savanna life, into the game 2026-11-22, the user: "I want the otter's animations to be
 * really special ... one of the players' favorites"; model and clips {@code tools/otter_models.py game}, v6 approved by
 * the user 2026-11-21; plan {@code docs/plans/otter-and-bull-shark.md}). An animal: generalized and fun first, every
 * number my pick, real habits used where they make it better:
 * <ul>
 * <li>Families of two to four live along the rivers and oxbows: strolling and bounding on the banks, swimming at the
 * surface with head and back out, diving and swimming underwater (about 20 seconds a breath), resting flat on a bank in
 * the heat of the day.</li>
 * <li>Its block moves (the Animal Maker Engine, played by the shared {@link MoveRider}): a quick cute scramble up a block,
 * a neat hop down, a calm slide into the water off a level bank, a leaping dive when in a hurry (or off a bank a block up),
 * and a quick climb out onto a bank level with the water or a block over it. Out of the water it shakes itself dry.</li>
 * <li>Hurt, or a crocodile or a hippo near, it runs for the water and dives away, or gets out if the danger is in it.</li>
 * <li>Pups follow the grown otters of their family.</li>
 * </ul>
 * Not yet (later steps of the plan): hunting crabs, fish and frogs, play-wrestling, biting back, the pet.
 */
public class OtterEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, SHAKE = 1, REST = 2, REST_UP = 3, FORAGE = 4, BITE = 5, EAT = 6, SKID = 7, HOP = 8;
    /**
     * How it goes on land (the skitter, 2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 6b): the even
     * walk, or the quick choppy scurry and the slow nose-down sniff of its burst-and-stop wandering ({@link SkitterGoal}).
     */
    public static final byte EVEN = 0, SCURRY = 1, SNIFF = 2;
    private static final EntityDataAccessor<Byte> DATA_GAIT = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    /** The skid stop's and the hop turn's lengths (ticks), and the hop's feet off the ground (its ticks 3 to 7: it turns then). */
    static final int SKID_TICKS = 8, HOP_TICKS = 10, HOP_UP = 3, HOP_DOWN = 7;
    /**
     * The skitter's speeds (shares of its speed attribute; my picks, measured in {@code RiverLifeTests}: about 1.6 and
     * 0.6 blocks a second, the clips' own strides) and the clips' made speeds (blocks a tick at size 1).
     */
    public static final double SCURRY_GO = 0.66, SNIFF_GO = 0.40;
    static final double SCURRY_CLIP_SPEED = 1.567 / 20.0, SNIFF_CLIP_SPEED = 0.593 / 20.0;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> DATA_MOVE = SynchedEntityData.defineId(OtterEntity.class,
            EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(OtterEntity.class);
    /**
     * Its mood (2026-11-22, the faces; plan {@code docs/plans/otter-play-and-fish.md}, section 6a): one value, worked
     * out in one place ({@link #workOutMood}), read by its face, its tail and its standing stance. In order of how
     * urgent it is: a more urgent mood comes at once, a calmer one only after {@link #MOOD_HOLD} ticks.
     */
    public static final String[] MOODS = {"calm", "playful", "curious", "content", "wary", "cross", "scared"};
    public static final byte CALM_MOOD = 0, PLAYFUL = 1, CURIOUS = 2, CONTENT = 3, WARY = 4, CROSS = 5, SCARED = 6;
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(OtterEntity.class, EntityDataSerializers.BYTE);
    /** A calmer mood waits this long (ticks: 3 s) after the last change, so the face does not flicker (my pick). */
    static final int MOOD_HOLD = 60;
    /** A player sprinting within this many blocks, or standing (not crouching) within the second, makes it wary (my picks). */
    static final double SPRINT_WARY = 10.0, TOO_CLOSE = 3.5;
    /** Another otter this near (blocks), fed and safe in the day, it is playful (my pick; play itself comes later). */
    static final double PLAYMATE_NEAR = 6.0;
    /** Hit within this long (ticks: 5 s) and not running, it is cross (my pick). */
    static final int CROSS_FOR = 100;

    /** The model is made at a grown otter's size (2.6 blocks nose to tail tip; 16 px a block); drawn at this share. */
    public static final float MODEL_SCALE = 1.0F;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): the swim clip's waterline (11.3 px). */
    public static final double AFLOAT = 11.3 / 16.0;
    /** Diving, it swims this far under the surface (blocks, at size 1; the bed stops it in shallower water). */
    static final double DIVE_DEPTH = 1.9;
    /** The clips' made speeds (blocks a tick, at size 1): the walk 1.12 blocks a second, the bound 2, the swims (my measure of the clips' strokes). */
    static final double WALK_CLIP_SPEED = 1.123 / 20.0, BOUND_CLIP_SPEED = 2.0 / 20.0, SWIM_CLIP_SPEED = 0.07, DIVE_CLIP_SPEED = 0.11;
    /** Faster than this (blocks a tick, at size 1) it is drawn bounding, not walking. */
    static final double BOUND_DRAWN = 0.075;
    /** Its speeds (shares of its speed attribute: about 1.1, 2.4 and 5 blocks a second on land, faster in the water). */
    public static final double STROLL = 0.55, BOUND = 0.8, DASH = 1.15;
    /** The clips' lengths (ticks): shaking dry, lying down and getting up from a rest. */
    static final int SHAKE_TICKS = 22, REST_UP_TICKS = 17;
    /** The longest it stays under (ticks: 20 s), and how long it then breathes at the surface before diving again. */
    static final int DIVE_MOST = 400, BREATHE = 80;
    /** It shakes once out of the water after this long in it (ticks). */
    static final int SOAKED = 30;
    /** Its strolls stay within this many blocks of the water it last swam in. */
    static final int NEAR_WATER = 14;
    /** A crocodile or a hippo this near (blocks) sends it away. */
    static final double DANGER_NEAR = 9.0;
    /**
     * The hunt (step 2, my picks): after a meal it is full for 5 to 7.5 minutes; hungry, it looks for prey within
     * {@link #HUNT_REACH} blocks; it feels under the stones for a crab this long before it bites (ticks), the bite's
     * snap lands on the clip's tick 6, and it eats on the bank this long (two rounds of the eat clip's crunches).
     */
    static final int FULL_TICKS = 6000, FULL_MORE = 3000, FEEL_TICKS = 36, BITE_TICKS = 12, BITE_HIT = 6, EAT_TICKS = 64;
    static final double HUNT_REACH = 12.0;
    /** How far ahead of its middle its jaws close, and how high (blocks at size 1: the model's carry socket, 20.9 px ahead, 7.9 up). */
    static final double JAWS_AHEAD = 1.31, JAWS_UP = 0.49;
    /** A hunt with no catch gives up after this long (ticks: 20 s). */
    static final int HUNT_GIVE_UP = 400;

    /**
     * Its moves (the Animal Maker Engine, {@code assets/antcolonies/block_moves/otter.json} written by
     * {@code python tools/otter_models.py game}), played by the shared {@link MoveRider}; pups (under 0.8) step the old way.
     */
    public static final BlockMoves MOVES = BlockMoves.of(OtterEntity.class, SavannaMod.MODID, "otter");
    private final MoveRider rider = new MoveRider(this, new MoveRider.Setup(MOVES, DATA_MOVE)
            .edge(MoveRider.Edge.UP, "up").edge(MoveRider.Edge.DOWN, "down")
            .edge(MoveRider.Edge.IN, "in").edge(MoveRider.Edge.RUN_IN, "dive").edge(MoveRider.Edge.DROP, "dive")
            .edge(MoveRider.Edge.OUT, "out").edge(MoveRider.Edge.OUT_UP, "out_up")
            .modelScale(MODEL_SCALE).feetOut(3.5).afloat(AFLOAT).run(BOUND_DRAWN).smallest(0.8));

    /** The game tick its doing started (for its clips' lengths). */
    int doingSince;
    /** Ticks under water this dive, and ticks before it may dive again. */
    int diveFor, breatheFor;
    /** Ticks it has pressed against a bank in the water. */
    private int pressedFor;
    /** Ticks it has been in the water (to shake once out). */
    int soakedFor;
    /** Ticks before it is hungry again. */
    int hungryIn;
    /** Ticks left to go straight at the move control's wanted spot with no route (the hunt's last few blocks). */
    int straightFor;
    /** What its jaws hold now (its catch, riding it so it is carried along), or null. */
    @Nullable
    LivingEntity held;
    /** Going into the water for fun with the leaping dive (set by its swim about). */
    boolean leapForFun;
    /** What it runs from now, and until when (its tick). */
    @Nullable
    Entity scare;
    int scaredUntil;
    /** The body's clip player. */
    @Nullable
    public SteadyController<OtterEntity> bodyClips;
    /** Where its eyes and head go (drawn only): a player within 14 blocks (5 crouching), a crocodile or hippo within 16, anything else stirring within 8. */
    public final Attention attention = new Attention(16.0, OtterEntity::catchesEye);
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Its hit boxes along the drawn body ({@link HitChain}; my picks measured on its model: the snout's front 1.3 ahead of
     * its middle, the tail tip 1.3 behind): its walking box is only the middle of a body 2.6 blocks long.
     */
    private final HitChain chain = new HitChain(this,
            new HitChain.Link("head", 0.95, 0.2, 0.45F, 0.5F),
            new HitChain.Link("rump", -0.55, 0.0, 0.55F, 0.6F),
            new HitChain.Link("tail", -1.0, 0.1, 0.35F, 0.3F));

    private static double catchesEye(Entity seen, double distance, boolean moving) {
        if (seen instanceof OtterEntity) {
            return 0;
        }
        if (seen instanceof Player player) {
            return Attention.playerInReach(player, distance, 14.0, 5.0) ? 0.5 : 0;
        }
        if (seen instanceof CrocodileEntity || seen instanceof HippoEntity) {
            return 0.35;
        }
        return moving && distance < 8.0 ? 1.0 : 0;
    }

    public OtterEntity(EntityType<? extends OtterEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new OtterMoveControl(this);
        lookControl = new SmoothSwimmingLookControl(this, 20);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FleeGoal(this));
        goalSelector.addGoal(1, new FollowFamilyGoal(this));
        goalSelector.addGoal(2, new HuntGoal(this));
        goalSelector.addGoal(2, new RestGoal(this));
        goalSelector.addGoal(3, new SwimAboutGoal(this));
        goalSelector.addGoal(4, new SkitterGoal(this));
        goalSelector.addGoal(4, new RandomStrollGoal(this, STROLL, 100, false) {
            @Override
            public boolean canUse() {
                return !skittery() && super.canUse();      // curious or playful, it skitters instead
            }
        });
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        MoveRider.define(builder, DATA_MOVE);
        builder.define(DATA_DOING, CALM);
        builder.define(DATA_DIVING, false);
        builder.define(DATA_MOOD, CALM_MOOD);
        builder.define(DATA_GAIT, EVEN);
    }

    public MoveRider rider() {
        return rider;
    }

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    void setDoing(byte doing) {
        if (doing != doing()) {
            doingSince = tickCount;
        }
        entityData.set(DATA_DOING, doing);
    }

    int doingFor() {
        return tickCount - doingSince;
    }

    public boolean diving() {
        return entityData.get(DATA_DIVING);
    }

    void setDiving(boolean on) {
        if (on && breatheFor > 0) {
            return;
        }
        if (!on && diving()) {
            breatheFor = BREATHE;
            diveFor = 0;
        }
        entityData.set(DATA_DIVING, on);
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** How it goes on land now ({@link #EVEN}, {@link #SCURRY} or {@link #SNIFF}). */
    public byte gait() {
        return entityData.get(DATA_GAIT);
    }

    void setGait(byte gait) {
        entityData.set(DATA_GAIT, gait);
    }

    /** In the mood to skitter about (curious or playful: the burst-and-stop wandering instead of the even stroll). */
    boolean skittery() {
        byte m = mood();
        return m == CURIOUS || m == PLAYFUL;
    }

    /** Its mood now (one of {@link #MOODS}). */
    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    /** The game tick its mood last changed. */
    private int moodSince;

    /**
     * Its mood from what it is doing and what is near (the one place that judges it; every 5 ticks on the server).
     * A custom name "mood:wary" (a name tag) or a tag "mood.wary" (the film camera's walker, named already) holds a
     * mood for looking at it.
     */
    byte workOutMood() {
        net.minecraft.network.chat.Component name = getCustomName();
        for (byte i = 0; i < MOODS.length; i++) {
            if (name != null && name.getString().equals("mood:" + MOODS[i]) || getTags().contains("mood." + MOODS[i])) {
                return i;
            }
        }
        if (scared()) {
            return SCARED;
        }
        if (getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < CROSS_FOR) {
            return CROSS;
        }
        byte d = doing();
        if (d == EAT || d == REST || d == REST_UP || d == SHAKE) {
            return CONTENT;
        }
        Player p = level().getNearestPlayer(this, 14.0);
        if (p != null && !p.isSpectator() && !p.isInvisible()) {
            double dist = distanceTo(p);
            if (p.isSprinting() && dist < SPRINT_WARY || !p.isCrouching() && dist < TOO_CLOSE) {
                return WARY;
            }
            if (Attention.playerInReach(p, dist, 14.0, 5.0)) {
                return CURIOUS;
            }
        }
        if (isFed() && level().isDay() && !level().getEntitiesOfClass(OtterEntity.class,
                getBoundingBox().inflate(PLAYMATE_NEAR), o -> o != this && o.isAlive()).isEmpty()) {
            return PLAYFUL;
        }
        return CALM_MOOD;
    }

    private void updateMood() {
        byte now = mood(), want = workOutMood();
        if (want != now && (want > now || tickCount - moodSince >= MOOD_HOLD)) {
            entityData.set(DATA_MOOD, want);
            moodSince = tickCount;
        }
    }

    /** Standing still on land doing nothing else (its mood's standing stance plays then; drawing too). */
    public boolean standing() {
        return !going() && !swimming() && doing() == CALM && rider.play() == null && !isDeadOrDying();
    }

    /** Whether it is scared of something now (fleeing). */
    public boolean scared() {
        return scare != null && tickCount < scaredUntil;
    }

    void scare(@Nullable Entity by, int ticks) {
        scare = by;
        scaredUntil = tickCount + ticks;
        if (doing() == REST) {
            setDoing(REST_UP);
        } else if (doing() == SKID || doing() == HOP) {
            setDoing(CALM);                                  // off at once
        }
        setGait(EVEN);
    }

    /** Faces a place, body and head (a little at a time). */
    void face(double x, double z, float step) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        float yaw = Mth.approachDegrees(getYRot(), (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, step);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /** Afloat or under: in water and off the bed. */
    public boolean swimming() {
        return isInWater() && !onGround();
    }

    // --- moving ------------------------------------------------------------------------------------------------------

    /** In water it swims where it points, weightless; on a move the move places it. */
    @Override
    public void travel(Vec3 travelVector) {
        if (rider.travel(doing() == CALM && !isDeadOrDying())) {
            return;
        }
        if (isControlledByLocalInstance() && isInWater()) {
            // at the surface it stays at its float depth (its tick holds it there): the swim steering's climb toward a
            // bank's top lifted it off it, so it bumped the bank and hopped out the old way instead of climbing out
            moveRelative(getSpeed(), diving() ? travelVector : new Vec3(travelVector.x, 0.0, travelVector.z));
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement().scale(0.9);
            // against a bank it has no move for, after a second, Minecraft's hop out (sooner, the hop lifted it off its
            // float depth so its own climb out never started)
            pressedFor = horizontalCollision ? pressedFor + 1 : 0;
            if (pressedFor > 20 && isFree(v.x, v.y + 0.6 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.3, v.z);
            }
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void tick() {
        super.tick();
        chain.place();
        rider.noteMoved();
        if (level().isClientSide) {
            attention.tick(this);
            rider.wetLooks(0.9, 2.2);
            shakeLooks();
            return;
        }
        life.tick();
        if (tickCount % 5 == 0) {
            updateMood();
        }
        if (breatheFor > 0) {
            breatheFor--;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (held != null && (!held.isAlive() || held.getVehicle() != this)) {
            release();
        }
        if (scare != null && (tickCount >= scaredUntil || !scare.isAlive())) {
            scare = null;
        }
        if (isInWater()) {
            leapForFun = false;
        }
        rider.hurry = scared() || leapForFun;
        byte d = doing();
        if (d == SHAKE && doingFor() >= SHAKE_TICKS || d == REST_UP && doingFor() >= REST_UP_TICKS
                || d == BITE && doingFor() >= BITE_TICKS || d == SKID && doingFor() >= SKID_TICKS
                || d == HOP && doingFor() >= HOP_TICKS) {
            setDoing(CALM);
        }
        if (isInWater()) {
            soakedFor = Math.min(soakedFor + 1, 1000);
            if (tickCount % 20 == 0) {
                restrictTo(blockPosition(), NEAR_WATER);     // its strolls stay near the water it last swam in
            }
            if (d == REST) {
                setDoing(REST_UP);
            }
        } else if (soakedFor >= SOAKED && onGround() && rider.play() == null && doing() == CALM && !scared()) {
            setDoing(SHAKE);                                 // out of the water: a good shake from nose to tail
            getNavigation().stop();
            soakedFor = 0;
        } else if (!isInWater() && rider.play() == null && soakedFor > 0 && soakedFor < SOAKED) {
            soakedFor = 0;
        }
        // in the water it hangs at the surface, head and back out, or dives and swims under
        if (isInWater() && rider.play() == null) {
            if (diving() && ++diveFor > DIVE_MOST) {
                setDiving(false);                            // up for air
            }
            double depth = getFluidHeight(FluidTags.WATER) - (diving() ? DIVE_DEPTH : AFLOAT) * getScale();
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Mth.clamp(depth * 0.1, diving() ? -0.04 : -0.02, 0.04) + v.y * 0.5, v.z);
        } else if (!isInWater() && diving()) {
            setDiving(false);
        }
    }

    /** Drawing only: water flung off in a spray as it shakes, from the head back to the tail (the clip's ticks 2 to 18). */
    private void shakeLooks() {
        if (doing() != SHAKE) {
            return;
        }
        int t = doingFor();
        if (t < 2 || t > 18) {
            return;
        }
        if (t == 2) {
            rider.dry();
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.WOLF_SHAKE, getSoundSource(), 0.5F,
                    1.5F + (random.nextFloat() - 0.5F) * 0.2F, false);
        }
        double size = getScale();
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double along = (0.9 - (t - 2) / 16.0 * 1.9) * size;        // the wave runs from the head to the tail
        double cx = getX() - Mth.sin(yaw) * along, cz = getZ() + Mth.cos(yaw) * along;
        for (int i = 0; i < 3; i++) {
            double side = (random.nextBoolean() ? 1 : -1) * (0.25 + random.nextDouble() * 0.2) * size;
            double sx = Mth.cos(yaw) * side, sz = Mth.sin(yaw) * side;
            level().addParticle(ParticleTypes.SPLASH, cx + sx, getY() + 0.45 * size, cz + sz, sx * 0.4, 0.15, sz * 0.4);
        }
    }

    // --- life, saving, spawning --------------------------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            rider.cut();
            release();                                   // a fright and its catch is dropped
            if (isAlive()) {
                scare(source.getEntity(), 200);
                // its family hears it and runs too
                for (OtterEntity mate : level().getEntitiesOfClass(OtterEntity.class, getBoundingBox().inflate(12.0), o -> o != this)) {
                    mate.scare(source.getEntity(), 160);
                }
            }
        }
        return hurt;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        boolean world = spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
        if (world) {
            BlockPos bank = MonitorEntity.bankNear(level, blockPosition(), BANK_REACH);    // spawned in the water by a bank: onto the bank
            if (bank != null) {
                moveTo(bank.getX() + 0.5, bank.getY(), bank.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
            }
        }
        life.setUp(world && life.spawnsYoung(level.getRandom()), level.getRandom());
        return out;
    }

    /** How far from a bank (sideways) the world may spawn it in the water. */
    public static final int BANK_REACH = 6;

    /**
     * Where the world may spawn a family: in the water of the savanna's rivers and oxbows within {@link #BANK_REACH} of a
     * bank to stand on, in the river animals' height band. Any height in the water passes (Minecraft's spawner keeps one
     * random height for a try); the world's spawns are set on the bank after ({@link #finalizeSpawn}).
     */
    public static boolean canSpawn(EntityType<OtterEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        return pos.getY() >= sea - 8 && pos.getY() <= sea + 6 && level.getFluidState(pos).is(FluidTags.WATER)
                && MonitorEntity.bankNear(level, pos, BANK_REACH) != null;
    }

    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        tag.putInt("HungryIn", hungryIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        life.apply();
        hungryIn = tag.getInt("HungryIn");
    }

    // --- the catch ---------------------------------------------------------------------------------------------------

    /** Hungry now (for tests and the film camera). */
    public void makeHungry() {
        hungryIn = 0;
    }

    public boolean isFed() {
        return hungryIn > 0;
    }

    /** What it holds in its jaws now, or null (for tests). */
    @Nullable
    public LivingEntity held() {
        return held;
    }

    /** What it hunts: crabs, barbs, tilapia and bullfrogs. */
    static boolean isPrey(Entity e) {
        return e instanceof CrabEntity || e instanceof BarbEntity || e instanceof TilapiaEntity || e instanceof BullfrogEntity;
    }

    /** Where its jaws close: the snout's front (its carry socket). */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + JAWS_UP * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** Whether something is at its jaws (its box within reach of the snout). */
    boolean atJaws(Entity e, double reach) {
        return e.getBoundingBox().inflate(reach).contains(jawPoint());
    }

    /**
     * Its jaws close on its catch: the prey rides it from now on, held crosswise between its teeth, still (its own will
     * off) and kept from drowning or dying in the air while it is carried, until it is eaten.
     */
    boolean grab(LivingEntity prey) {
        if (!prey.startRiding(this, true)) {
            return false;
        }
        held = prey;
        if (prey instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
        }
        prey.setInvulnerable(true);
        playSound(SoundEvents.FOX_BITE, 0.6F, 1.4F);
        return true;
    }

    /** Lets go of its catch (scared off, or the hunt is over). */
    void release() {
        LivingEntity was = held;
        held = null;
        if (was != null) {
            if (was.getVehicle() == this) {
                was.stopRiding();
            }
            if (was instanceof net.minecraft.world.entity.Mob mob) {
                mob.setNoAi(false);
            }
            was.setInvulnerable(false);
        }
    }

    /** Eats its catch: gone (no drops), and it is full. */
    void eatCatch() {
        LivingEntity was = held;
        held = null;
        if (was != null) {
            was.stopRiding();
            was.discard();
        }
        hungryIn = FULL_TICKS + random.nextInt(FULL_MORE);
        heal(4.0F);
    }

    /** Nobody steers an otter: a catch in its jaws rides it but must not drive it. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Its catch held crosswise between its teeth, at its snout's front. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Vec3 jaws = jawPoint();
        double down = doing() == EAT ? 0.25 * getScale() : 0.0;        // the head down over the catch as it eats
        callback.accept(passenger, jaws.x, jaws.y - passenger.getBbHeight() * 0.5 - down, jaws.z);
        if (passenger instanceof LivingEntity living) {
            float across = yBodyRot + 90.0F;
            living.setYRot(across);
            living.yBodyRot = across;
            living.yHeadRot = across;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            release();
        }
        super.remove(reason);
    }

    /** Its own dives decide its breath (up after 20 s at most, DIVE_MOST): Minecraft's air would drown it afloat, since
     *  its eye point sits just under the surface at its float depth (BUGS 288). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;                  // its chirps and squeaks (a fox's, pitched up)
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * (life.isYoung() ? 1.7F : 1.35F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    /**
     * Minecraft's smooth swimming control stands still once its route is done; this one also goes straight at a wanted
     * spot with no route while {@code straightFor} lasts (the hunt's last few blocks at its prey, on land or swimming).
     */
    static class OtterMoveControl extends SmoothSwimmingMoveControl {
        private final OtterEntity otter;

        OtterMoveControl(OtterEntity otter) {
            super(otter, 85, 10, 0.3F, 1.0F, false);
            this.otter = otter;
        }

        @Override
        public void tick() {
            if (otter.straightFor <= 0 || operation != Operation.MOVE_TO || !otter.getNavigation().isDone()) {
                super.tick();
                return;
            }
            otter.straightFor--;
            double dx = wantedX - otter.getX(), dy = wantedY - otter.getY(), dz = wantedZ - otter.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                otter.setZza(0.0F);
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            otter.setYRot(rotlerp(otter.getYRot(), yaw, 85.0F));
            otter.yBodyRot = otter.getYRot();
            otter.yHeadRot = otter.getYRot();
            float speed = (float) (speedModifier * otter.getAttributeValue(Attributes.MOVEMENT_SPEED));
            if (otter.isInWater()) {
                speed *= 0.3F;
                otter.setSpeed(speed);
                float pitch = Mth.clamp(-(float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -10.0F, 10.0F);
                otter.setXRot(rotlerp(otter.getXRot(), pitch, 5.0F));
                otter.zza = Mth.cos(otter.getXRot() * Mth.DEG_TO_RAD) * speed;
                otter.yya = -Mth.sin(otter.getXRot() * Mth.DEG_TO_RAD) * speed;
            } else {
                otter.setSpeed(speed);
            }
        }
    }

    // --- goals -------------------------------------------------------------------------------------------------------

    /** A random spot within reach that is water open over its top (its column's top water block), or null. */
    @Nullable
    static Vec3 waterSpot(OtterEntity otter, double reach, @Nullable Vec3 awayFrom) {
        RandomSource r = otter.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = reach * (0.4 + 0.6 * r.nextDouble());
            double x = otter.getX() + Math.cos(a) * d, z = otter.getZ() + Math.sin(a) * d;
            if (awayFrom != null && (x - otter.getX()) * (awayFrom.x - otter.getX()) + (z - otter.getZ()) * (awayFrom.z - otter.getZ()) > 0) {
                continue;                                   // toward the danger: no
            }
            BlockPos.MutableBlockPos p = BlockPos.containing(x, otter.getY() + 3, z).mutable();
            for (int k = 0; k < 8; k++, p.move(0, -1, 0)) {
                if (otter.level().getFluidState(p).is(FluidTags.WATER)) {
                    if (otter.level().getFluidState(p.above()).isEmpty() && otter.level().getBlockState(p.above()).isAir()) {
                        return new Vec3(x, p.getY() + 0.2, z);
                    }
                    break;
                }
                if (!otter.level().getBlockState(p).getCollisionShape(otter.level(), p).isEmpty()) {
                    break;
                }
            }
        }
        return null;
    }

    /** A random dry spot on land within reach, near the water, away from a danger if one is given; or null. */
    @Nullable
    static Vec3 landSpot(OtterEntity otter, double reach, @Nullable Vec3 awayFrom) {
        RandomSource r = otter.getRandom();
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = reach * (0.4 + 0.6 * r.nextDouble());
            double x = otter.getX() + Math.cos(a) * d, z = otter.getZ() + Math.sin(a) * d;
            if (awayFrom != null && (x - otter.getX()) * (awayFrom.x - otter.getX()) + (z - otter.getZ()) * (awayFrom.z - otter.getZ()) > 0) {
                continue;
            }
            int y = otter.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            BlockPos at = new BlockPos(Mth.floor(x), y, Mth.floor(z));
            if (Math.abs(y - otter.getY()) <= 3 && roomyGround(otter.level(), at)) {
                return Vec3.atBottomCenterOf(at);
            }
        }
        return null;
    }

    /**
     * The nearest bank it can haul out on, looking out over the water 16 ways from itself: the first dry column each way
     * at the water's top or a block over it, with dry level ground 3 by 3 just past its edge (the spot returned, a
     * block in from the edge). Null if none within reach. (A real otter swims to the nearest bank it can climb out on;
     * a random dry spot could lie behind a one-block wall it cannot get over: the hunt test, 2026-11-22.)
     */
    @Nullable
    static Vec3 nearestBank(OtterEntity otter, double reach) {
        Level level = otter.level();
        BlockPos.MutableBlockPos w = otter.blockPosition().mutable();
        while (level.getFluidState(w.above()).is(FluidTags.WATER) && w.getY() < otter.getY() + 8) {
            w.move(0, 1, 0);
        }
        if (!level.getFluidState(w).is(FluidTags.WATER)) {
            return null;
        }
        int top = w.getY();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0, dx = Math.cos(a), dz = Math.sin(a);
            for (double d = 0.5; d <= reach; d += 0.5) {
                BlockPos col = BlockPos.containing(otter.getX() + dx * d, top, otter.getZ() + dz * d);
                if (level.getFluidState(col).is(FluidTags.WATER)) {
                    continue;
                }
                BlockPos in = BlockPos.containing(otter.getX() + dx * (d + 1.0), top, otter.getZ() + dz * (d + 1.0));
                for (int up = 1; up <= 2; up++) {
                    if (roomyGround(level, in.above(up)) && d < bestD) {
                        bestD = d;
                        best = Vec3.atBottomCenterOf(in.above(up));
                    }
                }
                break;                                      // the first dry thing that way: a bank or not
            }
        }
        return best;
    }

    /**
     * Dry level ground 3 by 3 round a spot (a real bank, not a one-block strip it could not climb out onto: its climb
     * out needs two blocks of ground ahead; the hunt test, 2026-11-22, an otter stuck by a one-wide wall with its fish).
     */
    static boolean roomyGround(Level level, BlockPos at) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.below()).isEmpty()
                        || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                        || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The nearest danger it sees (a crocodile or a hippo within {@link #DANGER_NEAR} blocks), or null. */
    @Nullable
    LivingEntity dangerNear() {
        List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DANGER_NEAR),
                e -> e.isAlive() && (e instanceof CrocodileEntity || e instanceof HippoEntity));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : near) {
            double d = e.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Hurt, or a crocodile or a hippo near: away fast. On land with the danger on land it makes for the water (taking the
     * leaping dive in) and swims away under; with the danger in the water it gets out and runs along the bank.
     */
    static class FleeGoal extends Goal {
        private final OtterEntity otter;
        private int left;

        FleeGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!otter.scared() && otter.random.nextInt(5) == 0) {
                LivingEntity danger = otter.dangerNear();
                if (danger != null) {
                    otter.scare(danger, 160);
                }
            }
            return otter.scared();
        }

        @Override
        public void start() {
            left = 0;
            route();
        }

        private void route() {
            Entity from = otter.scare;
            Vec3 away = from == null ? null : from.position();
            boolean dangerInWater = from != null && from.isInWater();
            Vec3 to = dangerInWater ? landSpot(otter, 12.0, away) : waterSpot(otter, 14.0, away);
            if (to == null) {
                to = dangerInWater ? waterSpot(otter, 14.0, away) : landSpot(otter, 12.0, away);
            }
            if (to != null) {
                otter.getNavigation().moveTo(to.x, to.y, to.z, DASH);
            }
            left = 60;
        }

        @Override
        public boolean canContinueToUse() {
            return otter.scared();
        }

        @Override
        public void tick() {
            if (otter.isInWater() && otter.scare != null && !otter.scare.isInWater()) {
                otter.setDiving(true);                       // away under the water from a danger on land
            }
            if (--left <= 0 || otter.getNavigation().isDone()) {
                route();
            }
        }

        @Override
        public void stop() {
            otter.getNavigation().stop();
        }
    }

    /** Pups keep near the grown otters of their family: following the nearest within 20 blocks once more than 3 off. */
    static class FollowFamilyGoal extends Goal {
        private final OtterEntity otter;
        @Nullable
        private OtterEntity parent;
        private int again;

        FollowFamilyGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!otter.life.isYoung() || otter.doing() != CALM) {
                return false;
            }
            parent = null;
            double best = 20.0 * 20.0;
            for (OtterEntity o : otter.level().getEntitiesOfClass(OtterEntity.class, otter.getBoundingBox().inflate(20.0),
                    o -> o != otter && !o.life.isYoung())) {
                double d = o.distanceToSqr(otter);
                if (d < best) {
                    best = d;
                    parent = o;
                }
            }
            return parent != null && best > 9.0;
        }

        @Override
        public boolean canContinueToUse() {
            return parent != null && parent.isAlive() && otter.distanceToSqr(parent) > 4.0 && otter.doing() == CALM;
        }

        @Override
        public void start() {
            again = 0;
        }

        @Override
        public void tick() {
            if (--again <= 0 && parent != null) {
                again = 10;
                double speed = otter.distanceToSqr(parent) > 64.0 ? DASH : BOUND;
                otter.getNavigation().moveTo(parent, speed);
                if (parent.diving() && otter.isInWater()) {
                    otter.setDiving(true);
                } else if (!parent.diving() && otter.diving()) {
                    otter.setDiving(false);
                }
            }
        }

        @Override
        public void stop() {
            otter.getNavigation().stop();
        }
    }

    /**
     * The hunt (step 2, 2026-11-22; my picks, the real clawless otter's ways where they make it better): hungry and
     * grown, it picks the nearest prey within {@link #HUNT_REACH} blocks. A crab it wades up to and feels for under
     * the stones with its clever front paws ({@code forage_feel}) before it bites; a fish it dives after and chases
     * under the water, snapping when its snout is there; a bullfrog it pounces on. The catch is held crosswise in its
     * jaws and carried to the nearest bank, where it climbs out (and shakes itself dry), lies down to it and eats it with
     * a crunch and a wag of its tail. Then it is full for a good while, so a family never clears a river.
     */
    static class HuntGoal extends Goal {
        private final OtterEntity otter;
        @Nullable
        private LivingEntity prey;
        private int left, again;
        @Nullable
        private Vec3 bank;

        HuntGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (otter.isFed() || otter.life.isYoung() || otter.doing() != CALM || otter.scared() || otter.held != null
                    || otter.rider.play() != null || otter.random.nextInt(5) != 0) {
                // (a look round about every 10 ticks: Minecraft checks goals every other tick, odd or even by the
                // creature's id, so "tickCount % 10" never came round for half the otters)
                return false;
            }
            prey = null;
            double best = HUNT_REACH * HUNT_REACH;
            for (LivingEntity e : otter.level().getEntitiesOfClass(LivingEntity.class, otter.getBoundingBox().inflate(HUNT_REACH),
                    e -> e.isAlive() && isPrey(e) && !e.isPassenger())) {
                double d = e.distanceToSqr(otter) * (e instanceof CrabEntity ? 0.6 : 1.0);     // crabs first: its favourite
                if (d < best) {
                    best = d;
                    prey = e;
                }
            }
            return prey != null;
        }

        @Override
        public void start() {
            left = HUNT_GIVE_UP;
            again = 0;
            bank = null;
        }

        @Override
        public boolean canContinueToUse() {
            if (otter.scared() || otter.isDeadOrDying()) {
                return false;
            }
            if (otter.held != null) {
                return true;                                // carrying it to the bank and eating it
            }
            return prey != null && prey.isAlive() && !prey.isPassenger() && --left > 0 && otter.doing() != EAT;
        }

        @Override
        public void tick() {
            if (otter.held != null) {
                carry();
                return;
            }
            if (prey == null) {
                return;
            }
            byte d = otter.doing();
            if (d == FORAGE) {
                otter.face(prey.getX(), prey.getZ(), 20.0F);
                otter.getLookControl().setLookAt(prey);
                if (otter.doingFor() >= FEEL_TICKS) {
                    otter.setDoing(BITE);
                }
                return;
            }
            if (d == BITE) {
                otter.face(prey.getX(), prey.getZ(), 30.0F);
                if (otter.doingFor() == BITE_HIT) {
                    if (otter.atJaws(prey, reach(prey) + 0.3)) {        // a little slack: it moved while felt for
                        otter.grab(prey);
                        otter.getNavigation().stop();
                    }
                }
                return;
            }
            if (d != CALM || otter.rider.play() != null) {
                return;
            }
            boolean crab = prey instanceof CrabEntity;
            boolean preyUnder = prey.isInWater() && prey.getY() < surfaceOver(prey) - 0.6;
            if (otter.isInWater()) {
                otter.setDiving(preyUnder);                 // down after it, or up at the surface
            }
            if (crab && otter.atJaws(prey, reach(prey))) {
                otter.getNavigation().stop();
                otter.setDoing(FORAGE);                     // its paws feel under the stones round the crab
                return;
            }
            if (!crab && otter.atJaws(prey, reach(prey))) {
                otter.getNavigation().stop();
                otter.setDoing(BITE);
                return;
            }
            if (otter.distanceToSqr(prey) < 3.5 * 3.5) {
                // the last few blocks straight at it, facing it (a route ends a block or two off, facing anywhere)
                otter.getNavigation().stop();
                otter.face(prey.getX(), prey.getZ(), 20.0F);
                otter.straightFor = 3;
                otter.getMoveControl().setWantedPosition(prey.getX(), otter.isInWater() ? prey.getY() : otter.getY(), prey.getZ(),
                        crab ? STROLL : DASH);
                return;
            }
            if (--again <= 0) {
                again = 8;
                otter.getNavigation().moveTo(prey, crab ? BOUND : DASH);
            }
        }

        /**
         * How near its jaws must be to the prey's box to feel for it or snap (one reach for both, the bite a little
         * more: the hunt test, 2026-11-22, an otter feeling and biting forever at a crab a block down that was "near"
         * for the feeling but out of its bite).
         */
        private static double reach(Entity prey) {
            return prey instanceof CrabEntity ? 0.7 : 0.4;
        }

        /** With its catch: to the nearest bank, out, and eat it lying on the bank. */
        private void carry() {
            if (otter.doing() == EAT) {
                if (otter.doingFor() >= EAT_TICKS) {
                    otter.eatCatch();
                    otter.setDoing(CALM);
                }
                return;
            }
            otter.setDiving(false);
            if (!otter.isInWater() && otter.onGround() && otter.rider.play() == null && otter.doing() == CALM) {
                otter.getNavigation().stop();
                otter.setDoing(EAT);
                return;
            }
            if (otter.isInWater() && (bank == null || otter.getNavigation().isDone())) {
                bank = nearestBank(otter, 12.0);
                if (bank == null) {
                    bank = landSpot(otter, 10.0, null);
                }
                if (bank != null) {
                    otter.getNavigation().moveTo(bank.x, bank.y, bank.z, BOUND);
                }
            }
        }

        private static double surfaceOver(Entity e) {
            BlockPos.MutableBlockPos p = e.blockPosition().mutable();
            while (e.level().getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < e.getY() + 16) {
                p.move(0, 1, 0);
            }
            return p.getY() + e.level().getFluidState(p).getHeight(e.level(), p);
        }

        @Override
        public void stop() {
            byte d = otter.doing();
            if (d == FORAGE || d == BITE || d == EAT) {
                otter.setDoing(CALM);
            }
            if (otter.held != null) {
                otter.release();
            }
            otter.setDiving(false);
            otter.getNavigation().stop();
            prey = null;
        }
    }

    /**
     * Rests flat on a dry bank now and then, most in the heat of the day (real clawless otters rest through midday and
     * are about in the mornings and evenings): 20 to 60 seconds, eyes shut, unless a player comes near without crouching,
     * it is hurt or a danger shows up.
     */
    static class RestGoal extends Goal {
        private final OtterEntity otter;
        private int left;

        RestGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (otter.doing() != CALM || otter.isInWater() || !otter.onGround() || otter.soakedFor > 0 || otter.scared()
                    || otter.rider.play() != null || !otter.isFed() && !otter.life.isYoung()) {
                return false;
            }
            long day = otter.level().getDayTime() % 24000L;
            int chance = day > 4000 && day < 9000 ? 300 : 1500;
            return otter.random.nextInt(chance) == 0 && !playerNear();
        }

        private boolean playerNear() {
            Player p = otter.level().getNearestPlayer(otter, 5.0);
            return p != null && !p.isCrouching() && !p.isSpectator() && !p.isCreative();
        }

        @Override
        public void start() {
            otter.getNavigation().stop();
            otter.setDoing(REST);
            left = 400 + otter.random.nextInt(800);
        }

        @Override
        public boolean canContinueToUse() {
            return otter.doing() == REST && --left > 0 && !otter.scared() && !otter.isInWater() && !playerNear();
        }

        @Override
        public void stop() {
            if (otter.doing() == REST) {
                otter.setDoing(REST_UP);
            }
        }
    }

    /**
     * Swims about: from the water (or the bank near it) to another spot in it, at a bound's pace, diving down on the way
     * one time in three; now and then, after a while in the water, out onto a bank again. Going in from a bank it slides in
     * calmly, or one time in three takes the leaping dive for the fun of it.
     */
    /**
     * The skitter (2026-11-22; plan {@code docs/plans/otter-play-and-fish.md}, section 6b): curious or playful on land,
     * a real otter does not stroll evenly but scurries in short bursts. A dash of 1 to 3 blocks, a skid stop with the head
     * coming up, then a look about (standing, its mood's stance) or a short nose-down sniff, then a hop round to a new
     * heading 30 to 90 degrees off (a smaller turn now and then without the hop), and the next dash; 3 to 6 bursts. All
     * numbers my picks. It keeps within the water it last swam in, as the stroll does.
     */
    static class SkitterGoal extends Goal {
        private enum Phase { DASH, SKID, LOOK, SNIFF, HOP }
        private final OtterEntity otter;
        private Phase phase = Phase.DASH;
        private int left, bursts;
        /** The hop's whole turn (degrees), shared out over its feet-off-the-ground ticks. */
        private float turn;
        /** Where its dash or sniff goes. */
        private double toX, toZ;

        SkitterGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean free() {
            return !otter.isInWater() && otter.onGround() && !otter.scared() && otter.rider.play() == null
                    && otter.held == null && !otter.isDeadOrDying();
        }

        @Override
        public boolean canUse() {
            return otter.skittery() && otter.doing() == CALM && free() && otter.random.nextInt(20) == 0;
        }

        /** Every tick (Minecraft steps a goal every other tick by default: its pauses ran twice as long, the hop half turned). */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            bursts = 3 + otter.random.nextInt(4);
            if (!dash(otter.random.nextInt(3) == 0 ? (otter.random.nextFloat() - 0.5F) * 120.0F : 0.0F)) {
                hop();
            }
        }

        @Override
        public boolean canContinueToUse() {
            byte d = otter.doing();
            return bursts > 0 && free() && (d == CALM || d == SKID || d == HOP);
        }

        @Override
        public void stop() {
            halt();
            if (otter.doing() == SKID || otter.doing() == HOP) {
                otter.setDoing(CALM);
            }
        }

        /** A dash 1 to 3 blocks, this many degrees off its heading; false if there is no way there. */
        private boolean dash(float off) {
            for (int i = 0; i < 6; i++) {
                float wander = i == 0 ? 0.0F : (otter.random.nextFloat() - 0.5F) * 40.0F * i;
                float yaw = (otter.getYRot() + off + wander) * Mth.DEG_TO_RAD;
                double d = 1.2 + otter.random.nextDouble() * 1.8;
                double x = otter.getX() - Mth.sin(yaw) * d, z = otter.getZ() + Mth.cos(yaw) * d;
                if (goStraight(x, z, SCURRY_GO, 40)) {
                    otter.setGait(SCURRY);
                    phase = Phase.DASH;
                    return true;
                }
            }
            return false;
        }

        /**
         * Goes straight at a spot this near, no route (a route of a block or two counts as done before it takes a step: the
         * test, 2026-11-22), if the way is level dry ground in reach of its water; for at most this many ticks.
         */
        private boolean goStraight(double x, double z, double speed, int ticks) {
            Level level = otter.level();
            for (double f = 0.34; f <= 1.0; f += 0.33) {
                BlockPos at = BlockPos.containing(Mth.lerp(f, otter.getX(), x), otter.getY(), Mth.lerp(f, otter.getZ(), z));
                if (otter.hasRestriction() && !otter.isWithinRestriction(at) || !level.getFluidState(at).isEmpty()
                        || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                        || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
            otter.getNavigation().stop();
            otter.getMoveControl().setWantedPosition(x, otter.getY(), z, speed);
            otter.straightFor = ticks;
            left = ticks;
            toX = x;
            toZ = z;
            return true;
        }

        /** Got there, or out of time, or against something. */
        private boolean arrived() {
            double dx = toX - otter.getX(), dz = toZ - otter.getZ();
            return dx * dx + dz * dz < 0.09 || --left <= 0 || otter.horizontalCollision;
        }

        private void halt() {
            otter.straightFor = 0;
            otter.getNavigation().stop();
            otter.getMoveControl().setWantedPosition(otter.getX(), otter.getY(), otter.getZ(), 0.0);
            otter.setZza(0.0F);
            otter.setGait(EVEN);
        }

        /** Turns to a new heading with a little hop. */
        private void hop() {
            turn = (otter.random.nextBoolean() ? 1 : -1) * (30.0F + otter.random.nextFloat() * 60.0F);
            halt();
            otter.setDoing(HOP);
            phase = Phase.HOP;
            bursts--;
        }

        /** A short slow sniff along, nose down, a block or so ahead; false if there is no way. */
        private boolean sniff() {
            float yaw = (otter.getYRot() + (otter.random.nextFloat() - 0.5F) * 60.0F) * Mth.DEG_TO_RAD;
            double d = 1.0 + otter.random.nextDouble() * 1.0;
            double x = otter.getX() - Mth.sin(yaw) * d, z = otter.getZ() + Mth.cos(yaw) * d;
            if (!goStraight(x, z, SNIFF_GO, 60)) {
                return false;
            }
            otter.setGait(SNIFF);
            return true;
        }

        @Override
        public void tick() {
            switch (phase) {
                case DASH -> {
                    if (arrived()) {
                        halt();
                        otter.setDoing(SKID);                    // braced on its front paws, head coming up
                        phase = Phase.SKID;
                    }
                }
                case SKID -> {
                    if (otter.doing() == CALM) {
                        if (otter.random.nextFloat() < 0.45F && sniff()) {
                            phase = Phase.SNIFF;
                        } else {
                            phase = Phase.LOOK;                  // stands and looks about: its mood's stance
                            left = 10 + otter.random.nextInt(31);
                        }
                    }
                }
                case LOOK, SNIFF -> {
                    if (phase == Phase.SNIFF ? arrived() : --left <= 0) {
                        halt();
                        if (otter.random.nextInt(3) == 0) {
                            bursts--;                            // a small turn without the hop: the dash turns it
                            if (bursts > 0 && !dash((otter.random.nextFloat() - 0.5F) * 50.0F)) {
                                hop();
                            }
                        } else {
                            hop();
                        }
                    }
                }
                case HOP -> {
                    int t = otter.doingFor();
                    if (otter.doing() == HOP && t >= HOP_UP && t < HOP_DOWN) {
                        float yaw = otter.getYRot() + turn / (HOP_DOWN - HOP_UP);   // turned while its feet are up
                        otter.setYRot(yaw);
                        otter.yBodyRot = yaw;
                        otter.yHeadRot = yaw;
                    } else if (otter.doing() == CALM && bursts > 0 && !dash(0.0F)) {
                        hop();
                    }
                }
            }
        }
    }

    static class SwimAboutGoal extends Goal {
        private final OtterEntity otter;
        private boolean dive, out;
        private int left;

        SwimAboutGoal(OtterEntity otter) {
            this.otter = otter;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (otter.doing() != CALM || otter.scared() || otter.rider.play() != null) {
                return false;
            }
            int chance = otter.isInWater() ? 40 : 160;
            if (otter.random.nextInt(chance) != 0) {
                return false;
            }
            out = otter.isInWater() && otter.soakedFor > 600 && otter.random.nextInt(3) == 0;
            Vec3 to = out ? landSpot(otter, 10.0, null) : waterSpot(otter, 10.0, null);
            if (to == null) {
                return false;
            }
            dive = !out && otter.random.nextInt(3) == 0;
            return otter.getNavigation().moveTo(to.x, to.y, to.z, BOUND);
        }

        @Override
        public void start() {
            left = 300;
            // going in from a bank, one time in three the leaping dive, for the fun of it
            otter.leapForFun = !otter.isInWater() && otter.random.nextInt(3) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !otter.getNavigation().isDone() && --left > 0 && otter.doing() == CALM && !otter.scared();
        }

        @Override
        public void tick() {
            if (dive && otter.isInWater() && otter.rider.play() == null) {
                otter.setDiving(true);
            }
        }

        @Override
        public void stop() {
            if (dive) {
                otter.setDiving(false);
            }
            otter.leapForFun = false;
            otter.getNavigation().stop();
        }
    }

    // --- clips -------------------------------------------------------------------------------------------------------

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, RawAnimation> MOVE_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, RawAnimation> STANCES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation BOUND_CLIP = RawAnimation.begin().thenLoop("bound");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation DIVE_SWIM = RawAnimation.begin().thenLoop("dive_swim");
    private static final RawAnimation SHAKE_CLIP = RawAnimation.begin().thenPlayAndHold("shake_dry");
    private static final RawAnimation REST_CLIP = RawAnimation.begin().thenPlay("rest_down").thenLoop("rest");
    private static final RawAnimation REST_UP_CLIP = RawAnimation.begin().thenPlayAndHold("rest_up");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation FORAGE_CLIP = RawAnimation.begin().thenLoop("forage_feel");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation EAT_CLIP = RawAnimation.begin().thenLoop("eat");
    private static final RawAnimation SCURRY_CLIP = RawAnimation.begin().thenLoop("scurry");
    private static final RawAnimation SNIFF_CLIP = RawAnimation.begin().thenLoop("sniff_walk");
    private static final RawAnimation SKID_CLIP = RawAnimation.begin().thenPlayAndHold("skid_stop");
    private static final RawAnimation HOP_CLIP = RawAnimation.begin().thenPlayAndHold("hop_turn");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        bodyClips = new SteadyController<>(this, "body", MOVES.lead(), this::animate).clock((otter, clip, tick) -> otter.rider.clock(clip, tick));
        bodyClips.setCustomInstructionKeyframeHandler(note -> {
        });         // the moves' clips' notes (their path) are for the tools only
        controllers.add(bodyClips);
    }

    /** Whether it walks or swims (drawing only): it moved within the last few ticks. */
    public boolean going() {
        return rider.movedWithin(3);
    }

    private PlayState animate(AnimationState<OtterEntity> state) {
        AnimationController<OtterEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        BlockMoves.Play c = rider.play();
        if (c != null && !c.turning(level().getGameTime())) {     // a move wins over everything else it does
            return state.setAndContinue(MOVE_CLIPS.computeIfAbsent(MOVES.clip(c), n -> RawAnimation.begin().thenPlayAndHold(n)));
        }
        if (c != null) {                        // turning square to the edge on the spot first: its feet step round
            return state.setAndContinue(WALK);
        }
        switch (doing()) {
            case SHAKE:
                return state.setAndContinue(SHAKE_CLIP);
            case REST:
                return state.setAndContinue(REST_CLIP);
            case REST_UP:
                return state.setAndContinue(REST_UP_CLIP);
            case FORAGE:
                return state.setAndContinue(FORAGE_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case EAT:
                return state.setAndContinue(EAT_CLIP);
            case SKID:
                return state.setAndContinue(SKID_CLIP);
            case HOP:
                return state.setAndContinue(HOP_CLIP);
            default:
                break;
        }
        double size = getScale(), stride = rider.stride();
        if (swimming()) {
            if (diving()) {
                player.setAnimationSpeed(going() ? Mth.clamp(stride / (DIVE_CLIP_SPEED * size), 0.7, 2.2) : 0.6);
                return state.setAndContinue(DIVE_SWIM);
            }
            player.setAnimationSpeed(going() ? Mth.clamp(stride / (SWIM_CLIP_SPEED * size), 0.6, 2.2) : 0.5);
            return state.setAndContinue(SWIM);
        }
        if (going()) {
            byte gait = gait();
            if (gait == SCURRY) {
                player.setAnimationSpeed(Mth.clamp(stride / (SCURRY_CLIP_SPEED * size), 0.5, 2.0));
                return state.setAndContinue(SCURRY_CLIP);
            }
            if (gait == SNIFF) {
                player.setAnimationSpeed(Mth.clamp(stride / (SNIFF_CLIP_SPEED * size), 0.5, 2.0));
                return state.setAndContinue(SNIFF_CLIP);
            }
            if (stride > BOUND_DRAWN * size) {
                player.setAnimationSpeed(Mth.clamp(stride / (BOUND_CLIP_SPEED * size), 0.6, 2.6));
                return state.setAndContinue(BOUND_CLIP);
            }
            player.setAnimationSpeed(Mth.clamp(stride / (WALK_CLIP_SPEED * size), 0.5, 2.0));
            return state.setAndContinue(WALK);
        }
        byte m = mood();
        if (m != CALM_MOOD && !isInWater()) {      // its mood's stance and tail (tools/otter_models.py stance_clips)
            return state.setAndContinue(STANCES.computeIfAbsent(MOODS[m], n -> RawAnimation.begin().thenLoop("stand_" + n)));
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (chain != null) {                              // null while Entity's own constructor runs
            chain.setIds(id);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return chain.parts();
    }

    /** Ticks it has been in the water, for tests. */
    public int soakedFor() {
        return soakedFor;
    }
}
