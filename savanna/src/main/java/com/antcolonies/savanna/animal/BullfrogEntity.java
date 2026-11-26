package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import java.util.UUID;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.Sex;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.block.FrogEggsBlock;
import com.antcolonies.savanna.registry.SavannaSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * The African bullfrog (M9 river life, prey plan step 3, 2026-11-20; the user's pick and picture; animals are fun first,
 * inspired by the real frog's big character): a huge round frog of the pans, oxbows and slow river edges, males 0.8
 * blocks long and females 0.65 (the user's sizes), each 0.9 to 1.1 of that.
 *
 * <p>Its day ({@link BullfrogGoals}; every number my pick unless marked):
 * <ul>
 * <li>An ambush eater: it sits still and lunges with a huge gape at small things passing within about 1.5 blocks of its
 * mouth (barbs, snails, baby crabs), then is fed a while; it never starves.</li>
 * <li>Its tongue (the user, 2026-11-20): flies of a cloud that sinks within 2 blocks of its mouth, in front of it and
 * above it along a clear line, it flicks at with a long tongue (drawn as a rope by its renderer); one flick in four
 * misses; a few flies fill it.</li>
 * <li>Bold, not shy: a player coming near makes it puff up; one who keeps coming gets a barking lunge and a bite (1
 * heart). From a monitor or a grown crocodile it leaps into the water (Minecraft's own long-jump aim, so the arc is
 * clear).</li>
 * <li>In the hottest hours of a dry day it sits half dug into the ground at the water's edge (only its back and eyes
 * out), and comes out when a danger comes near.</li>
 * <li>Males call from dusk and in rain, a deep lowing whoop (the throat swelling), and others answer.</li>
 * <li>On land it hops ({@link BullfrogMoveControl}); in the water it swims and floats at the surface.</li>
 * </ul>
 * <p>Its life (the animal life kit, its life file {@code animal_life/bullfrog.json}; the river tie-up part B,
 * 2026-11-23): a tadpole ({@link TadpoleEntity}) climbs out a froglet, its young stage, grown in 3 days. In the rains,
 * while it rains, a ready female comes to a calling male ({@link com.antcolonies.animal.SeekMateGoal}); he climbs onto
 * her back and clasps her round the waist (the clasp clip) while she carries him to the shallows, where she lays a
 * floating mat of eggs at the surface ({@link FrogEggsBlock}). He stays by the eggs, and then by the tadpole school that
 * hatches from them, for its first 3 days, puffing and lunging at anything that comes near ({@link #guardSpot}); he
 * never eats his own tadpoles (other frogs still may).
 */
public class BullfrogEntity extends Animal implements GeoEntity, HasLife {
    /** What it is doing to be seen (synced). */
    public static final byte NONE = 0, HOP = 1, LEAP = 2, LUNGE = 3, TONGUE = 4, PUFF = 5, CALL = 6, DIG_IN = 7, DUG = 8, DIG_OUT = 9,
            AIM = 10, STALK = 11, HOP_DIVE = 12, CLIMB_OUT = 13, CLASP = 14;
    /** A female's length against a male's (the user's 0.65 against 0.8). */
    static final float FEMALE_SIZE = 0.65F / 0.8F;
    /** The model's mouth, against the frog's feet and middle (blocks at a male's size: the snout 0.4 ahead, 0.19 up). */
    static final double MOUTH_AHEAD = 0.4, MOUTH_UP = 0.19;
    /** How long a meal lasts (ticks; 4 to 8 minutes), and the rest after a few flies (1 to 2 minutes). */
    static final int FED_MIN = 4800, FED_MORE = 4800, FLIES_FILL = 3, FLY_REST_MIN = 1200, FLY_REST_MORE = 1200;
    /**
     * A hop: the kick's push up (blocks a tick; up a step a bigger one, its arc over a block's top from tick 4 to 7 and
     * down onto it at about 7, the same flight as a plain hop's, so the clip fits both), how far it goes (blocks at a
     * male's size: the user, 2026-11-20, "make him jump further": at most 1.1, about 1.4 of its length, my pick; at least
     * 0.4, a short hop to a spot close by) and the clip's tick of the kick. The forward push is held through the whole
     * flight (the polish: Minecraft wipes a mob's speed into a block's side, so a hop up from right against a step rose
     * straight up and fell back for good, the user's find on the course's stairs).
     */
    static final double HOP_UP = 0.24, HOP_UP_STEP = 0.40, HOP_LONGEST = 1.1, HOP_SHORTEST = 0.4;
    static final int HOP_KICK = 2;
    /** The hop clip's flight (seconds, at speed 1: from 0.12 to 0.6 of its 0.8 s) and the real flight (ticks). */
    static final double HOP_CLIP_FLIGHT = 0.384, HOP_FLIGHT_TICKS = 7.0;
    /**
     * After landing it stays in its hop this many ticks, the clip's landing (from 0.6 to 0.8 of it, at the clip's
     * speed: 3 ticks) folding it up; landing stops its slide (the polish's step 1: the course found each landing slid on
     * in the walk clip for 8 ticks, its feet jumping at both clip changes).
     */
    static final int HOP_SETTLE = 3;
    /** How much of its sideways speed a landing keeps (frogs land and stop). */
    static final double LANDING_KEEPS = 0.0;     // (0.15 slid on 3 to 5 ticks in the walk clip: the second course run)

    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.BYTE);
    /** Counts every action started, so a clip that plays once restarts for the next (a second hop, a second flick). */
    private static final EntityDataAccessor<Integer> DATA_ACTION_ID = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.INT);
    /** Its life: sex, stage (a froglet is its young stage), own size (the animal life kit). */
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(BullfrogEntity.class);
    private final AnimalLife life;
    /** Where its tongue reaches to (world), while it flicks. */
    private static final EntityDataAccessor<Vector3f> DATA_TONGUE_AT = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.VECTOR3);
    /**
     * The fly cloud it is aiming at or flicking into (its entity id; -1 none), and whether this flick catches a fly (the
     * server's call, one in four misses): each player's computer picks the real fly and shows the catch or the dodge.
     */
    private static final EntityDataAccessor<Integer> DATA_TONGUE_CLOUD = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_TONGUE_HIT = SynchedEntityData.defineId(BullfrogEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Ticks until it is hungry again, and flies eaten since its last rest. */
    int hungryIn;
    int fliesEaten;
    /** Ticks into the action now under way (server), and until its next hop may start. */
    int actionTicks;
    private int hopCooldown, landedAt;
    /** The climb out of the water: from where, onto where (blocks, world). */
    @Nullable
    private Vec3 climbFrom, climbTo;
    private boolean hopUp;
    /** This hop's forward push each tick of its flight (blocks a tick). */
    private double hopPush;
    /** Ticks until a male calls again (night and rain only). */
    int callIn;
    // client: the action last seen (to restart its clip) and when it began
    private int seenActionId = -1;
    private int clientActionStart;

    public BullfrogEntity(EntityType<? extends BullfrogEntity> type, Level level) {
        super(type, level);
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new BullfrogMoveControl(this);
        setPathfindingMalus(PathType.WATER, 0.0F);
        hungryIn = 200 + level.random.nextInt(1200);
        callIn = 200 + level.random.nextInt(600);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);     // Minecraft's own step (0.6): only a hop takes it up a block
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        BullfrogGoals.register(this, goalSelector);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, NONE);
        builder.define(DATA_ACTION_ID, 0);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_TONGUE_AT, new Vector3f());
        builder.define(DATA_TONGUE_CLOUD, -1);
        builder.define(DATA_TONGUE_HIT, false);
    }

    // --- what it is ----------------------------------------------------------------------------------------------------

    @Override
    public AnimalLife life() {
        return life;
    }

    public boolean isMale() {
        return life.isMale();
    }

    /**
     * Makes it a grown male or female of its own length (a share of its sex's usual: 1 is 0.8 blocks for a male, 0.65
     * for a female), drawn and boxed through SCALE. Tests and the film tool.
     */
    public void setUp(boolean male, float own) {
        life.setUp(false, male ? Sex.MALE : Sex.FEMALE, getRandom());
        life.setOwnSize((male ? 1.0F : FEMALE_SIZE) * own);
    }

    public boolean isHungry() {
        return hungryIn <= 0;
    }

    /** For tests: hungry now. */
    public void makeHungry() {
        hungryIn = 0;
    }

    // --- what it is doing ----------------------------------------------------------------------------------------------

    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    /** Starts an action (its clip plays from the start, even the same one again). */
    void act(byte action) {
        entityData.set(DATA_ACTION, action);
        entityData.set(DATA_ACTION_ID, entityData.get(DATA_ACTION_ID) + 1);
        actionTicks = 0;
    }

    /** Back to sitting (or swimming). */
    void stopActing() {
        if (action() != NONE) {
            entityData.set(DATA_ACTION, NONE);
        }
        actionTicks = 0;
    }

    /** Busy with something its legs must not walk out of (a lunge, a flick, a call, digging; a hop in the air is the legs' own). */
    public boolean busy() {
        byte a = action();
        return a != NONE && a != HOP && a != HOP_DIVE && a != PUFF && a != STALK;
    }

    public boolean isDug() {
        byte a = action();
        return a == DIG_IN || a == DUG || a == DIG_OUT;
    }

    /** Whether a new hop may start: on the ground, not busy, rested from the last. */
    boolean mayHop() {
        return onGround() && !busy() && action() != HOP && action() != HOP_DIVE && hopCooldown <= 0;
    }

    /**
     * A hop toward where it faces (its legs ask; up a block if the way climbs). One that will come down in the water is
     * a dive (the polish's step 5: nose down, arms forward, into the water, not onto its hands).
     */
    void startHop(boolean up, double far) {
        hopUp = up;
        landedAt = 0;
        double reach = Mth.clamp(far + 0.1, HOP_SHORTEST * getScale(), HOP_LONGEST * getScale());
        hopPush = reach / HOP_FLIGHT_TICKS;
        act(!up && landsInWater(reach) ? HOP_DIVE : HOP);
    }

    /**
     * Out of the water onto a bank (the polish's step 5, a move of its own as the engine's monitor climbs are): over
     * {@link #CLIMB_TICKS} the game carries it up (most of the rise in the first {@link #CLIMB_RISE} of the move) and
     * then forward onto the bank (from {@link #CLIMB_ON} of it), in step with the climb_out clip: arms onto the bank,
     * the pull, the kick, sitting.
     */
    static final int CLIMB_TICKS = 16;
    static final double CLIMB_RISE = 0.6, CLIMB_ON = 0.4;
    /**
     * How high a bank it hauls itself out onto (blocks over its feet as it floats: a bank about level with the water;
     * the user, 2026-11-20: African bullfrogs are too heavy to climb, so a bank a block over the water keeps it in, and
     * it gets out where the bank is low), and how far onto it it ends (at a male's size).
     */
    static final double CLIMB_HIGHEST = 0.9, CLIMB_ONTO = 0.4;

    /**
     * Where a climb out onto the bank ahead (toward x, z) would end: the bank's top a little way onto it, if the bank is
     * right in front of it, its top over the water and within {@link #CLIMB_HIGHEST}, with room for it there.
     */
    @Nullable
    Vec3 bankAhead(double x, double z) {
        double dx = x - getX(), dz = z - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.05) {
            return null;
        }
        dx /= flat;
        dz /= flat;
        double edge = getBbWidth() * 0.5 + 0.35;      // a little ahead of its nose: before it bumps (and steps up)
        BlockPos front = BlockPos.containing(getX() + dx * edge, getY(), getZ() + dz * edge);
        for (int up = 0; up <= 2; up++) {
            BlockPos at = front.above(up);
            var shape = level().getBlockState(at).getCollisionShape(level(), at);
            if (shape.isEmpty()) {
                continue;
            }
            double top = at.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            BlockPos over = BlockPos.containing(getX() + dx * edge, top + 0.05, getZ() + dz * edge);
            if (top - getY() < 0.2 || top - getY() > CLIMB_HIGHEST || !level().getBlockState(over).getCollisionShape(level(), over).isEmpty()
                    || level().getFluidState(over).is(FluidTags.WATER) || !level().getBlockState(over.above()).getCollisionShape(level(), over.above()).isEmpty()) {
                continue;
            }
            double onto = edge + CLIMB_ONTO * getScale();
            return new Vec3(getX() + dx * onto, top, getZ() + dz * onto);
        }
        return null;
    }

    /** Starts the climb out onto that spot. */
    void climbOut(Vec3 to) {
        climbFrom = position();
        climbTo = to;
        float yaw = (float) (Mth.atan2(to.z - getZ(), to.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setNoGravity(true);
        act(CLIMB_OUT);
    }

    private void climbTick() {
        if (climbFrom == null || climbTo == null) {
            stopActing();
            setNoGravity(false);
            return;
        }
        double u = Math.min(1.0, actionTicks / (double) CLIMB_TICKS);
        double rise = Mth.clamp(u / CLIMB_RISE, 0, 1), on = Mth.clamp((u - CLIMB_ON) / (1 - CLIMB_ON), 0, 1);
        rise = rise * rise * (3 - 2 * rise);
        on = on * on * (3 - 2 * on);
        setPos(climbFrom.x + (climbTo.x - climbFrom.x) * on, climbFrom.y + (climbTo.y - climbFrom.y) * rise,
                climbFrom.z + (climbTo.z - climbFrom.z) * on);
        setDeltaMovement(Vec3.ZERO);
        if (u >= 1.0) {
            climbFrom = climbTo = null;
            setNoGravity(false);
            stopActing();
            hopCooldown = 6;
        }
    }

    /** Whether a hop this long from here comes down in the water: water where it lands, nothing solid on top of it. */
    boolean landsInWater(double ahead) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        BlockPos at = BlockPos.containing(getX() - Mth.sin(yaw) * ahead, getY() - 0.2, getZ() + Mth.cos(yaw) * ahead);
        return level().getFluidState(at).is(FluidTags.WATER) && level().getBlockState(at).getCollisionShape(level(), at).isEmpty();
    }

    /** Where its tongue reaches to now (client: for the rope), while it flicks. */
    public Vec3 tongueAt() {
        Vector3f v = entityData.get(DATA_TONGUE_AT);
        return new Vec3(v.x(), v.y(), v.z());
    }

    void setTongueAt(Vec3 at) {
        entityData.set(DATA_TONGUE_AT, new Vector3f((float) at.x, (float) at.y, (float) at.z));
    }

    /** The fly cloud it aims at or flicks into, or none (its entity id on every computer). */
    @Nullable
    public FlyCloudEntity tongueCloud() {
        int id = entityData.get(DATA_TONGUE_CLOUD);
        return id >= 0 && level().getEntity(id) instanceof FlyCloudEntity cloud ? cloud : null;
    }

    void setTongueCloud(@Nullable FlyCloudEntity cloud) {
        entityData.set(DATA_TONGUE_CLOUD, cloud == null ? -1 : cloud.getId());
    }

    /** Whether the flick under way catches its fly (the server's call). */
    public boolean tongueHits() {
        return entityData.get(DATA_TONGUE_HIT);
    }

    void setTongueHit(boolean hit) {
        entityData.set(DATA_TONGUE_HIT, hit);
    }

    /**
     * Client only (the renderer's, unknown here): the fly of the cloud's swarm its eyes are on and its tongue goes for,
     * the action id it was picked for, where the tongue met it or where it dodged from, and the head's tilt toward it.
     */
    @Nullable
    public Object clientFly;
    public int clientFlyFor = -1;
    @Nullable
    public Vec3 clientContact;
    public boolean clientCaught;
    public float clientHeadPitch;
    public long clientHeadTime;

    /** The id of the action now under way (counts up with each one started; the players' computers see it too). */
    public int actionId() {
        return entityData.get(DATA_ACTION_ID);
    }

    /** Ticks since the action now under way began, as the players' computers see it (for the tongue's rope). */
    public float clientActionAge(float partialTick) {
        return tickCount - clientActionStart + partialTick;
    }

    /** Its mouth: just in front of its snout, at its lip line (blocks, world). */
    public Vec3 mouth() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double s = getScale();
        return new Vec3(getX() - Mth.sin(yaw) * MOUTH_AHEAD * s, getY() + MOUTH_UP * s, getZ() + Mth.cos(yaw) * MOUTH_AHEAD * s);
    }

    /** A meal from a lunge: fed for 4 to 8 minutes. */
    public void ateMeal() {
        hungryIn = FED_MIN + random.nextInt(FED_MORE);
        fliesEaten = 0;
    }

    /** A fly: a few fill it for a minute or two. */
    void ateFly() {
        if (++fliesEaten >= FLIES_FILL) {
            hungryIn = FLY_REST_MIN + random.nextInt(FLY_REST_MORE);
            fliesEaten = 0;
        }
    }

    // --- its tick ------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int id = entityData.get(DATA_ACTION_ID);
            if (id != seenActionId) {
                seenActionId = id;
                clientActionStart = tickCount;
            }
            return;
        }
        breedingTick();
        filmTick();
        if (hungryIn > 0) {
            hungryIn--;
        }
        if (callIn > 0) {
            callIn--;
        }
        if (hopCooldown > 0) {
            hopCooldown--;
        }
        actionTicks++;
        byte a = action();
        if (a == CLIMB_OUT) {
            climbTick();
        } else if (a == HOP || a == HOP_DIVE) {
            hopTick();
        } else if (a == LEAP && (actionTicks > 4 && (onGround() || isInWater()) || actionTicks > 60)) {
            stopActing();
            setDiscardFriction(false);
        } else if (a == DIG_OUT && actionTicks >= BullfrogGoals.DIG_OUT_TICKS) {
            stopActing();       // out of the ground: the clip is over
        }
        // "No gravity" belongs to the climb out and "no air drag" to the escape leap, and to nothing else (review R10-8:
        // a puff at a player, a female's clasp or a save in the middle of a climb took the action away and left the
        // frog hanging with no gravity for good; Minecraft saves that mark with the frog).
        if (action() != CLIMB_OUT && isNoGravity()) {
            setNoGravity(false);
        }
        if (action() != LEAP && shouldDiscardFriction()) {
            setDiscardFriction(false);
        }
        floatTick();
    }

    /**
     * The film tool's hook ({@code tools/film/bullfrog_frames.film}): a frog named {@code film-act:tongue} (held still
     * with NoAI) flicks its tongue every second at a spot 1.4 blocks ahead and 1.2 up, the real action, so the clip and
     * the drawn rope are filmed together as players see them.
     */
    private void filmTick() {
        Component name = getCustomName();
        if (name != null && name.getString().equals("film-act:tongue") && (action() != TONGUE || actionTicks >= 20)) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;       // a frog held still never turns its body to its facing
            double ahead = MOUTH_AHEAD * getScale() + 1.4;
            setTongueAt(position().add(-Mth.sin(yaw) * ahead, MOUTH_UP * getScale() + 1.2, Mth.cos(yaw) * ahead));
            act(TONGUE);
        }
    }

    /** The hop: the kick at its tick, then the flight; done on landing. */
    private void hopTick() {
        if (actionTicks >= HOP_KICK && landedAt == 0) {
            // the kick, then its forward push held through the flight (no air drag, nothing lost against a step's side)
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double up = actionTicks == HOP_KICK ? (hopUp ? HOP_UP_STEP : HOP_UP) : getDeltaMovement().y;
            setDeltaMovement(-Mth.sin(yaw) * hopPush, up, Mth.cos(yaw) * hopPush);
            hasImpulse = true;
        }
        if (actionTicks == HOP_KICK) {
            return;
        } else if (landedAt == 0 && (actionTicks > HOP_KICK + 2 && (action() == HOP_DIVE ? isInWater() : onGround() || isInWater())
                || actionTicks > 40)) {     // (a dive grazing the bank's edge on its way out is not a landing)
            landedAt = actionTicks;
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x * LANDING_KEEPS, v.y, v.z * LANDING_KEEPS);
        }
        if (landedAt > 0 && (actionTicks >= landedAt + HOP_SETTLE || isInWater())) {
            landedAt = 0;
            stopActing();
            hopCooldown = 4 + random.nextInt(10);
        }
    }

    /** In the water, still: it floats with its eyes and back out (a frog's lungs), bobbing a little. */
    private void floatTick() {
        if (!isInWater() || action() == LEAP || action() == LUNGE || !getNavigation().isDone()) {     // (a lunge may go down at the bed)
            return;
        }
        double under = getFluidHeight(FluidTags.WATER);
        Vec3 v = getDeltaMovement();
        if (under > getBbHeight() * 0.6) {
            setDeltaMovement(v.x * 0.9, Math.max(v.y, 0.025), v.z * 0.9);
        } else if (under < getBbHeight() * 0.35) {
            setDeltaMovement(v.x * 0.9, Math.min(v.y, -0.01), v.z * 0.9);
        } else {
            setDeltaMovement(v.x * 0.85, v.y * 0.5, v.z * 0.85);
        }
    }

    /** No fall hurts it from its own leaps (it never drowns: it is in the game's can-breathe-under-water tag). */
    @Override
    protected int calculateFallDamage(float distance, float multiplier) {
        return super.calculateFallDamage(distance - 3.0F, multiplier);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getMaxHeadYRot() {
        return 20;
    }

    // --- spawning ------------------------------------------------------------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        life.setUp(false, level.getRandom());
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            // drawn anywhere in its column: set down on the top (on the bank, or afloat on the water)
            int top = surface(level, getX(), getZ());
            boolean water = level.getFluidState(BlockPos.containing(getX(), top - 1, getZ())).is(FluidTags.WATER);
            setPos(getX(), water ? top - 0.4 : top, getZ());
        }
        return out;
    }

    static int surface(LevelReader level, double x, double z) {
        return com.antcolonies.colony.GroundTop.y(level, Mth.floor(x), Mth.floor(z)); // through any tree crown (review R7-30)
    }

    /** How deep the water a bullfrog lives by may be (blocks: pans, edges, shallows), and how far up the bank it spawns. */
    static final int SHALLOW = 3, BANK = 2;

    /**
     * Where it may spawn by itself: at the edges of shallow water (1 to {@link #SHALLOW} deep) and on the bank within
     * {@link #BANK} blocks of it, in the river animals' band of heights. Minecraft's spawner draws one height in the
     * column: any spot in the water there or the free block over the top passes ({@link #finalizeSpawn} sets it down).
     */
    public static boolean canSpawn(EntityType<BullfrogEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 13 || pos.getY() > sea + com.antcolonies.savanna.worldgen.RiverZones.HIGHEST_POOL + 1) {
            return false;
        }
        int top = surface(level, pos.getX() + 0.5, pos.getZ() + 0.5);
        if (pos.getY() > top || pos.getY() < top && !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        for (int dx = -BANK; dx <= BANK; dx++) {
            for (int dz = -BANK; dz <= BANK; dz++) {
                if (FlyCloudEntity.shallowWater(level, pos.getX() + dx, pos.getZ() + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The world's own rules (light, grass) do not apply: its spawn rule has said where it may be. */
    @Override
    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    /** It may spawn in water as on land (the water animals' answer, BUGS 218): only other mobs in the way stop it. */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** Within this many blocks of a player it is never removed (as the river fish). */
    static final double KEEP_NEAR = 96.0;

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > KEEP_NEAR * KEEP_NEAR && !hasCustomName();
    }

    // --- the froglet: its young stage (the river tie-up, 2026-11-23) -------------------------------------------------

    /**
     * Whether it is still a froglet, its life's young stage: fresh out of the water from a tadpole
     * ({@link TadpoleEntity#becomeFroglet}) at 0.32 of its grown size (about 0.25 blocks), grown in 3 days (its life
     * file). Too small to call or square up to anyone. Its size goes through SCALE (the life kit), so its box, its
     * drawing, its mouth and its tongue's reach all shrink with it.
     */
    public boolean isFroglet() {
        return life.isYoung();
    }

    /** Makes it a froglet just out of the water (its sex and own size rolled anew). */
    public void makeFroglet() {
        life.setUp(true, getRandom());
        life.setAge(0);
    }

    // --- breeding (the river tie-up part B, 2026-11-23; every number my pick, fun first) -------------------------------

    /** A male takes a mate only while he has called this recently (ticks: a female comes to his call). */
    static final int CALLED_WITHIN = 1200;
    /** How far round her a clasped female looks for the shallows to lay in (blocks across; up or down 3). */
    static final int LAY_REACH = 10;
    /**
     * Where the clasped male sits on her back (her model pixels at her size, one a 50th of a block on a male of size 1):
     * this high over her feet (under her back's top: his belly rests on her back, the two bodies just meeting) and this
     * far back from her middle, so his chin lies on her back behind her eyes and her head shows in front of his (the
     * first film had him over her head, a stack of two frogs), his hands round her waist (the clasp clip).
     */
    static final double CLASP_UP = 14.5, CLASP_BACK = 9.0, PIXEL = 0.32 / 16.0;
    /**
     * The father's guard: he keeps within {@link #GUARD_STAY} blocks of his eggs (or of his nearest tadpole), sees off
     * anything that comes within {@link #GUARD_SEE_OFF} of them ({@link BullfrogGoals.ThreatGoal}), and follows his school
     * up to {@link #GUARD_FOLLOW} blocks off.
     */
    static final double GUARD_STAY = 3.0, GUARD_SEE_OFF = 4.0, GUARD_FOLLOW = 16.0;
    /** How near his eggs or school a small egg thief must come before a father goes for it (blocks; the food web's step 2, my pick). */
    static final double RAIDER_NEAR = 2.0;

    /** When it last called (its own ticks; a male only). */
    int lastCall = -CALLED_WITHIN;
    /** The shallows a clasped female is carrying her male to (not saved). */
    @Nullable
    private BlockPos layAt;
    /** Where she laid her last eggs (her male reads it as they part). */
    @Nullable
    private BlockPos lastClutch;
    /** The eggs a father guards (saved), and what he guards this second: the eggs, then his nearest tadpole. */
    @Nullable
    private BlockPos guardAt;
    @Nullable
    private BlockPos guardSpot;

    /** A female goes to the male; a male waits, calling. */
    @Override
    public boolean seeksMate() {
        return !isMale();
    }

    /** A male is picked only while he is calling (or already clasping her). */
    @Override
    public boolean takesMate() {
        return isMale() && (isPassenger() || tickCount - lastCall < CALLED_WITHIN);
    }

    /**
     * The pair's courtship (on her, the one that came): he climbs onto her back and clasps her (the clasp clip, set on
     * her back by {@link #getPassengerAttachmentPoint}), and she carries him to the nearest shallows to lay in.
     */
    @Override
    public void courting(Mob mate, int ticks) {
        if (isMale() || !(mate instanceof BullfrogEntity male)) {
            return;
        }
        if (!male.isPassenger()) {
            male.getNavigation().stop();
            male.startRiding(this, true);
            male.act(CLASP);
        }
        if (layAt == null && ticks % 20 == 0 || layAt != null && ticks % 40 == 0 && !FrogEggsBlock.canLayAt(level(), layAt)) {
            layAt = findLayingSpot(LAY_REACH);
        }
        if (layAt != null && distanceToSqr(Vec3.atBottomCenterOf(layAt)) > 1.0 && (ticks % 10 == 0 || getNavigation().isDone())) {
            getNavigation().moveTo(layAt.getX() + 0.5, layAt.getY(), layAt.getZ() + 0.5, 1.0);
        }
    }

    /** She lays where she is in the shallows, still clasped, and they part; he stays to guard the eggs. */
    @Override
    public void mated(Mob mate) {
        if (!isMale()) {
            ejectPassengers();
            BlockPos at = layAt != null && FrogEggsBlock.canLayAt(level(), layAt) && distanceToSqr(Vec3.atBottomCenterOf(layAt)) < 9.0
                    ? layAt : findLayingSpot(3);
            int eggs = life.numbers().clutchMin() + random.nextInt(life.numbers().clutchMax() - life.numbers().clutchMin() + 1);
            lastClutch = at != null && level() instanceof ServerLevel server && FrogEggsBlock.lay(server, at, getType(), eggs) ? at : null;
            layAt = null;
            getNavigation().stop();
            return;
        }
        stopRiding();
        stopActing();
        guardAt = mate instanceof BullfrogEntity female ? female.lastClutch : null;
        guardSpot = guardAt;
    }

    /**
     * The nearest water to lay in within this reach: the top of shallow water (a still water block with air over it and
     * the bed at most 2 below), nearest first; null with none.
     */
    @Nullable
    BlockPos findLayingSpot(int reach) {
        BlockPos home = blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-reach, -3, -reach), home.offset(reach, 3, reach))) {
            double d = at.distSqr(home);
            if (d < bestD && FrogEggsBlock.canLayAt(level(), at)) {
                bestD = d;
                best = at.immutable();
            }
        }
        return best;
    }

    /** Where a clasped male sits: on her back behind her head. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity rider, EntityDimensions dimensions, float partialTick) {
        double px = PIXEL * getScale();
        return new Vec3(0.0, CLASP_UP * px, -CLASP_BACK * px).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /** He faces as she does. */
    @Override
    protected void positionRider(Entity rider, Entity.MoveFunction move) {
        super.positionRider(rider, move);
        rider.setYRot(getYRot());
        rider.setYHeadRot(getYHeadRot());
        if (rider instanceof LivingEntity living) {
            living.yBodyRot = yBodyRot;
            living.yBodyRotO = yBodyRotO;
        }
    }

    /** A clasped male only holds on: she goes where she likes. */
    @Override
    public boolean canControlVehicle() {
        return false;
    }

    /**
     * Each tick: a pair no longer courting part (a courtship broken off, or a world saved mid-clasp); each second the
     * father's guard spot is found anew.
     */
    private void breedingTick() {
        if (isVehicle() && life.partner() == null && !(getFirstPassenger() instanceof BullfrogEntity rider && rider.filmed())) {
            ejectPassengers();
        }
        if (getVehicle() instanceof BullfrogEntity && !filmed() && life.partner() == null) {
            stopRiding();       // (only a frog on a frog: a frog carried off in an otter's or a croc's jaws rides its hunter)
        }
        if (action() == CLASP && !isPassenger()) {
            stopActing();
        }
        if (tickCount % 20 == 0) {
            guardSpot = findGuardSpot();
        }
    }

    /** A frog the film tool set up (named film...): a clasped pair it summons stays together. */
    private boolean filmed() {
        Component name = getCustomName();
        return name != null && name.getString().startsWith("film");
    }

    /** What a father guards now: his eggs while they lie, then his nearest tadpole through its first days; else null. */
    @Nullable
    private BlockPos findGuardSpot() {
        if (guardAt == null) {
            return null;
        }
        if (level().getBlockState(guardAt).getBlock() instanceof FrogEggsBlock) {
            return guardAt;
        }
        int care = life.numbers().careTicks();
        TadpoleEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (TadpoleEntity t : level().getEntitiesOfClass(TadpoleEntity.class, getBoundingBox().inflate(GUARD_FOLLOW),
                t -> t.isAlive() && isFatherOf(t) && t.age() < care)) {
            double d = t.distanceToSqr(this);
            if (d < best) {
                best = d;
                nearest = t;
            }
        }
        if (nearest == null) {
            guardAt = null;         // the eggs gone, his school grown up, eaten or lost: his guard is over
            return null;
        }
        return nearest.blockPosition();
    }

    /** What he guards this second (his eggs or his nearest tadpole), or null. */
    @Nullable
    public BlockPos guardSpot() {
        return guardSpot;
    }

    /** Sets the eggs he guards (tests). */
    public void guard(@Nullable BlockPos eggs) {
        guardAt = eggs;
        guardSpot = eggs;
    }

    /** Whether he guards the eggs here. */
    public boolean guards(BlockPos eggs) {
        return eggs.equals(guardAt);
    }

    /** Whether this tadpole hatched from eggs he guarded (he never eats it). */
    public boolean isFatherOf(LivingEntity e) {
        return e instanceof TadpoleEntity t && getUUID().equals(t.father());
    }

    /**
     * Whether this is a small egg thief (the river food web's step 2, 2026-11-23): a tilapia or a crab, which nibble frog
     * spawn; a father goes for one only when it comes within {@link #RAIDER_NEAR} blocks of his eggs or school.
     */
    static boolean raidsEggs(LivingEntity e) {
        return e.isAlive() && (e instanceof TilapiaEntity || e instanceof CrabEntity);
    }

    /** Whether this comes to harm his eggs or school, when it comes near them: a player, a monitor, a young crocodile. */
    static boolean threatensYoung(LivingEntity e) {
        return e.isAlive() && (e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof MonitorEntity
                || e instanceof CrocodileEntity c && !c.life().stage().grown());
    }

    /** For tests: as if it had just called. */
    public void calledNow() {
        lastCall = tickCount;
    }

    /** For tests: where she last laid. */
    @Nullable
    public BlockPos lastClutch() {
        return lastClutch;
    }

    // --- not a farm animal ---------------------------------------------------------------------------------------------

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    // --- sounds --------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return null;        // its voice is the call (BullfrogGoals), not a random noise
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SavannaSounds.BULLFROG_BARK.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FROG_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return (isMale() ? 0.9F : 1.1F) + (random.nextFloat() - 0.5F) * 0.1F;
    }

    // --- saving --------------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (guardAt != null) {
            tag.putLong("GuardAt", guardAt.asLong());
        }
        tag.putInt("HungryIn", hungryIn);
        byte a = action();
        if (a == DUG || a == DIG_IN) {
            tag.putBoolean("Dug", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("Life") && tag.contains("Male")) {
            // a frog saved before the life kit (2026-11-23): its size was its "FullSize" (or, before froglets, its SCALE),
            // already its sex's share, which is what the kit calls its own size; a froglet then is grown now
            AttributeInstance scale = getAttribute(Attributes.SCALE);
            tag.putFloat("OwnSize", tag.contains("FullSize") ? tag.getFloat("FullSize") : scale != null ? (float) scale.getBaseValue() : 1.0F);
        } else if (!tag.contains("Life") && tag.contains("sex") && Sex.fromFile(tag.getString("sex")) != null) {
            life.setUp(false, Sex.fromFile(tag.getString("sex")), getRandom());    // a summon's sex: its size in that sex's range
        }
        life.load(tag);
        guardAt = tag.contains("GuardAt") ? BlockPos.of(tag.getLong("GuardAt")) : null;
        hungryIn = tag.getInt("HungryIn");
        if (tag.getBoolean("Dug")) {
            entityData.set(DATA_ACTION, DUG);
        }
    }

    // --- animations --------------------------------------------------------------------------------------------------
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation STALK_CLIP = RawAnimation.begin().thenLoop("stalk");
    private static final RawAnimation HOP_CLIP = RawAnimation.begin().thenPlay("hop");
    private static final RawAnimation CLIMB_OUT_CLIP = RawAnimation.begin().thenPlayAndHold("climb_out");
    private static final RawAnimation HOP_DIVE_CLIP = RawAnimation.begin().thenPlayAndHold("hop_dive");
    private static final RawAnimation LEAP_CLIP = RawAnimation.begin().thenPlayAndHold("leap");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation CALL_CLIP = RawAnimation.begin().thenPlay("call");
    private static final RawAnimation PUFF_CLIP = RawAnimation.begin().thenLoop("puff");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation TONGUE_CLIP = RawAnimation.begin().thenPlay("tongue");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation DIG_IN_CLIP = RawAnimation.begin().thenPlayAndHold("dig_in");
    private static final RawAnimation DUG_CLIP = RawAnimation.begin().thenLoop("dug");
    private static final RawAnimation DIG_OUT_CLIP = RawAnimation.begin().thenPlay("dig_out");
    private static final RawAnimation CLASP_CLIP = RawAnimation.begin().thenLoop("clasp");
    /** The walk clip's planted feet match this pace (blocks a tick at a male's size: 5.1 model pixels a second). */
    public static final double WALK_CLIP_PACE = 0.10 / 20.0;
    /**
     * The creep (the polish's step 4): the stalk clip's planted feet slide back 6.7 model pixels a second, 0.0067 blocks
     * a tick for a male (tools/bullfrog_models.py, STALK_*); its legs move it at exactly this, the clip at its own speed.
     */
    public static final double STALK_CLIP_PACE = 0.0067;
    /** The swim clip's kick is made for this pace (blocks a tick at a male's size; my pick). */
    static final double SWIM_CLIP_PACE = 0.05;

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.HashMap<>();
    private int animatedActionId = -1;

    private static final RawAnimation BLINK_CLIP = RawAnimation.begin().thenPlay("blink");
    /** The blink clip's length (ticks, its 0.3 s), and the wait between blinks (ticks: 4 to 10 s; my picks). */
    static final int BLINK_TICKS = 6, BLINK_GAP_MIN = 80, BLINK_GAP_MORE = 120;
    // client: when the last blink began and the next is due (its own ticks)
    private int blinkStart = -100, blinkNext = -1;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", 3, this::animate));
        controllers.add(new AnimationController<>(this, "blink", 0, this::blink));
    }

    /**
     * The blink (the polish's step 2, 2026-11-20): the lower lids rise over the eyes every 4 to 10 seconds, a layer of
     * its own over whatever the body is doing (the blink clip moves only the lids), so it blinks sitting, puffed up,
     * floating, calling or dug in alike. None for a frog being filmed in a set clip, or a dead one.
     */
    private PlayState blink(AnimationState<BullfrogEntity> state) {
        Component name = getCustomName();
        if (isDeadOrDying() || name != null && name.getString().startsWith("film")) {
            return PlayState.STOP;
        }
        if (blinkNext < 0) {
            blinkNext = tickCount + BLINK_GAP_MIN + random.nextInt(BLINK_GAP_MORE);
        } else if (tickCount >= blinkNext) {
            blinkStart = tickCount;
            blinkNext = tickCount + BLINK_GAP_MIN + random.nextInt(BLINK_GAP_MORE);
            state.getController().forceAnimationReset();
        }
        if (tickCount - blinkStart > BLINK_TICKS + 1) {
            return PlayState.STOP;
        }
        return state.setAndContinue(BLINK_CLIP);
    }

    private PlayState animate(AnimationState<BullfrogEntity> state) {
        AnimationController<BullfrogEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        Component name = getCustomName();
        if (name != null && name.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(name.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        int id = entityData.get(DATA_ACTION_ID);
        if (id != animatedActionId) {
            animatedActionId = id;
            if (action() != AIM) {
                player.forceAnimationReset();   // a new action: its clip from the start, even the same clip again
            }
        }
        double size = Math.max(0.3, getScale());
        double dx = getX() - xo, dz = getZ() - zo, dy = getY() - yo;
        double speed = Math.sqrt(dx * dx + dz * dz + (isInWater() ? dy * dy : 0));
        switch (action()) {
            case HOP -> {
                player.setAnimationSpeed(HOP_CLIP_FLIGHT * 20.0 / HOP_FLIGHT_TICKS);
                return state.setAndContinue(HOP_CLIP);
            }
            case CLIMB_OUT -> {
                return state.setAndContinue(CLIMB_OUT_CLIP);
            }
            case HOP_DIVE -> {
                player.setAnimationSpeed(HOP_CLIP_FLIGHT * 20.0 / HOP_FLIGHT_TICKS);
                return state.setAndContinue(HOP_DIVE_CLIP);
            }
            case LEAP -> {
                return state.setAndContinue(LEAP_CLIP);
            }
            case LUNGE -> {
                return state.setAndContinue(ATTACK);
            }
            case TONGUE -> {
                return state.setAndContinue(TONGUE_CLIP);
            }
            case STALK -> {
                // its legs move it at the clip's own pace (BullfrogMoveControl), so the clip plays at its own speed
                return state.setAndContinue(STALK_CLIP);
            }
            case PUFF -> {
                return state.setAndContinue(PUFF_CLIP);
            }
            case CALL -> {
                return state.setAndContinue(CALL_CLIP);
            }
            case DIG_IN -> {
                return state.setAndContinue(DIG_IN_CLIP);
            }
            case DUG -> {
                return state.setAndContinue(DUG_CLIP);
            }
            case DIG_OUT -> {
                return state.setAndContinue(DIG_OUT_CLIP);
            }
            case CLASP -> {
                return state.setAndContinue(CLASP_CLIP);
            }
            default -> { }
        }
        if (isInWater()) {
            if (speed > 0.01) {
                player.setAnimationSpeed(Mth.clamp(speed / (SWIM_CLIP_PACE * size), 0.5, 2.0));
                return state.setAndContinue(SWIM);
            }
            return state.setAndContinue(FLOAT);
        }
        if (speed > 0.002 && onGround()) {
            player.setAnimationSpeed(Mth.clamp(speed / (WALK_CLIP_PACE * size), 0.5, 3.0));
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
