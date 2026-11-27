package com.antcolonies.savanna.animal;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Attention;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.HitChain;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.entity.body.BodyPlans;
import com.antcolonies.entity.body.GaitShift;
import com.antcolonies.entity.body.Gaits;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
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
 * The Cape buffalo, the first savanna land animal (into the game 2026-11-25, {@code docs/plans/buffalo.md}; Neon's model,
 * paint and 23 clips, workshop v12, approved by the user: "perfect"). An animal: fun first, inspired by real buffalo, every
 * number my pick unless said.
 * <ul>
 * <li>Herds of 3 to 5 (a bull and cows) are laid with new land beside a pan and kept for good ({@code worldgen/BuffaloHerds});
 * never spawned by the world's spawner. A herd's home is its pan's middle and how far across the pan is, saved by every
 * member; the home is the herd's only name (a mate is a buffalo with the same home, {@link #mates}) and it never moves.
 * One from a spawn egg or a summon takes the home of the nearest buffalo within {@link #HOME_FROM_MATE} that has one, else
 * its home is where it stands.</li>
 * <li>Its body (this step): a box 1.6 wide and 1.9 tall at size 1, so every size gets 2 by 2 routes 2 high, two hit
 * beads ({@link HitChain}) for its head and rump, drawn whenever any of it is in view, out to {@link #DRAW_DISTANCE}. It
 * steps up a block on Minecraft's own step (no block moves in this step: the shared one was sized for the otter), its
 * trunk tilting to the ground under its front and hind feet as it goes ({@code client/BuffaloModel}); it turns at most
 * {@link #MAX_TURN} degrees a tick (a 3.8-block body swung 90 degrees in a tick); in deep water it floats low, back and
 * head out, and swims; it can drown.</li>
 * <li>What it does has a clip of its own ({@link #doing}, synced): grazing, drinking, lying down and getting up, its
 * little things (an ear flutter, a shake, a snort, a bull's bellow, pawing the ground) and its moods and hits. Its day and
 * its temper are the goals' ({@link BuffaloGoals}): grazing in the cool hours, drinking at its pan and lying down at
 * midday, lying down at night; watching, warning and charging a player who comes too near, and charging whoever hurt
 * it ({@link #angryAt}), its herd's bull joining and the cows bunching.</li>
 * </ul>
 * Not yet: wallowing, calves, block moves.
 */
public class BuffaloEntity extends PathfinderMob implements GeoEntity, HasLife, com.antcolonies.debug.FilmFacts {
    /** What it is doing, for the clips (synced). One-shot clips end by themselves once played ({@link #tick}); the loops (rest, the moods) last until a goal ends them. */
    public static final byte CALM = 0, GRAZE = 1, DRINK = 2, REST_DOWN = 3, REST = 4, REST_UP = 5, EAR_FLUTTER = 6, SHAKE_OFF = 7,
            SNORT = 8, BELLOW = 9, PAW_GROUND = 10, MOOD_ALERT = 11, MOOD_ANGRY = 12, HEADBUTT = 13, GORE = 14, CHARGE_HEADBUTT = 15,
            // held by lions (the Motion Studio phase 3, step 3): brought down by two or more, struggling on the ground
            HELD_SINK = 16, HELD_DOWN = 17;
    /** Each doing's clip, by its number. */
    private static final String[] DOING_CLIPS = {"idle", "graze", "drink", "rest_down", "rest", "rest_up", "ear_flutter", "shake_off",
            "snort", "bellow", "paw_ground", "mood_alert", "mood_angry", "headbutt", "gore", "charge_headbutt", "held_sink", "held_down"};
    /**
     * Its clips as its clip file says them: each one's length and whether it loops (the Motion Studio, the new phase 2,
     * step 3: before, a table of lengths typed here from the file by hand). Calves play the grown model's.
     */
    static com.antcolonies.entity.body.Clips clips() {
        return com.antcolonies.entity.body.Clips.of(BuffaloEntity.class, com.antcolonies.savanna.SavannaMod.MODID, "buffalo");
    }

    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(BuffaloEntity.class, EntityDataSerializers.BYTE);
    /** Counted up at every doing started, so the same clip started again plays from its start (as the bull shark's actions). */
    private static final EntityDataAccessor<Integer> DATA_DOING_ID = SynchedEntityData.defineId(BuffaloEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(BuffaloEntity.class);

    /** The model is made at a bull's size (3.83 blocks long, 2.28 tall at the hump; 16 px a block); drawn at this share. */
    public static final float MODEL_SCALE = 1.0F;
    /**
     * The model's origin sits at its front legs (front hooves 0.2 blocks ahead of it, hind hooves 1.46 behind): it is drawn
     * this far toward its head (model px) so the middle between its feet sits on its box's middle ({@code client/BuffaloModel}).
     * Every place on its body measured from the model is this much farther ahead of its middle.
     */
    public static final double DRAW_AHEAD_PX = 10.0;
    /**
     * Where lions grip it (the Motion Studio, phase 3): its body plan's spots (the haunch, the throat from each front
     * side, the flanks), each found on its skin and posed through the clips it plays while held by {@code tools/spots.py}.
     */
    public static final com.antcolonies.entity.body.Spots SPOTS = com.antcolonies.entity.body.Spots.of(BuffaloEntity.class,
            com.antcolonies.savanna.SavannaMod.MODID, "buffalo");
    /** Its jaws at the chewing part of a graze: 17 px ahead of the model's origin (the clip), so this far ahead of its middle (blocks, at size 1). */
    static final double JAWS_AHEAD = (17.0 + DRAW_AHEAD_PX) / 16.0;
    /**
     * The clips' blend (ticks): GeckoLib eases from one clip into the next this long, then plays the new one from its
     * start, so a moment of a clip comes this many ticks after its doing started ({@link #clipTick}). Read by
     * {@code tools/clip_check.py} from the controller below.
     */
    public static final int BLEND = 5;
    /**
     * Its gaits (walk, trot, charge), from its body plan (the Motion Studio, phase 2, step 2): each clip's own speed (0.61,
     * 1.39 and 2.45 blocks a second at size 1), where it is drawn trotting, then charging (faster than 1.25 and 2.6 blocks
     * a second at size 1, between the paces below), and each clip's play-speed limits. Calves share the grown model's.
     */
    final Gaits gaits = BodyPlans.gaitsOf("buffalo");
    /** The gait drawn (client): a change waits for the stride's matching moment. */
    private final GaitShift gaitShift = new GaitShift();
    /**
     * Its paces (the move control's share of its speed). Minecraft's walking push goes with the square of the speed:
     * measured 2026-11-25, 1.73 x pace squared blocks a second (2.3 ran 9.23, 1.0 ran 1.73). So: stepping between plants
     * at the walk clip's own 0.6 blocks a second, travelling about 1.0, trotting to catch up about 1.6, charging about 5
     * (faster than a walking player, 4.3, slower than a sprint, 5.6). Measured in game tests, never trusted as worked out (BUGS 246):
     * {@code BuffaloTests.aChargeRunsAtAboutFiveBlocksASecondAndItsHitLands} (CHARGE, 4.5 to 5.5 blocks a second) and
     * {@code BuffaloTests.aBuffaloTravelsAtAboutOneBlockASecond} (TRAVEL); each logs the pace it measured.
     */
    public static final double GRAZE_STEP = 0.59, TRAVEL = 0.76, TROT = 0.96, CHARGE = 1.70;
    /** It turns at most this much a tick (degrees; Minecraft's own is 90). */
    static final float MAX_TURN = 20.0F;
    /** In water deeper than this over its feet (blocks, at size 1) it swims up: it floats low, its back and head out. */
    static final double FLOAT_DEPTH = 1.3;
    /** A spawn egg's or a summon's buffalo takes the home of the nearest buffalo this near that has one (blocks). */
    static final double HOME_FROM_MATE = 16.0;
    /** Mates are looked for this far round it (blocks), anew once a second. */
    static final double MATES_REACH = 64.0;
    /** Drawn this far off (blocks): herds are few and big, seen across the plain. */
    static final double DRAW_DISTANCE = 128.0;
    /** Its grunt (the cow's, deep). */
    static final float VOICE_PITCH = 0.55F;

    /** Where its herd lives (its pan's middle), and how far across the pan is (0: no water, a home on open grass); null until it has one. */
    @Nullable
    BlockPos home;
    int homeAcross;
    /** The game tick its doing started (for its clips' lengths and moments). */
    int doingSince;
    /** Its mates (buffalo with the same home), looked for anew once a second, and when. */
    private List<BuffaloEntity> mates = List.of();
    private int matesAt = -MATES_EVERY;
    /** Mates are looked for anew this often (ticks). */
    static final int MATES_EVERY = 20;
    /** The last tick it moved, and how far a tick, eased (for the clips). */
    private int movedTick = -100;
    private double stride;
    static final int WALK_HOLD = 3;
    /** Drawing only: the doing whose clip was last started. */
    private int shownDoingId = -1;
    /** Drawing only (BuffaloModel): the ground under its front and hind feet as drawn. */
    public final DrawnGround drawn = new DrawnGround();
    /**
     * Led by a game test or the film camera: its own day goals are off, so only where it is sent and its temper move it
     * ("Led:1b").
     */
    public boolean led;
    /**
     * Whoever hurt it last (never another buffalo), until when it is angry at them (its own tick), and where it stood when
     * hurt: it charges them ({@link BuffaloGoals}), and its mates see it (the bull joins, the cows bunch round that spot).
     */
    private final Anger anger = new Anger();
    /** Its temper now and at whom (the temper goal's; read by tests). */
    BuffaloGoals.Temper temper = BuffaloGoals.Temper.NONE;
    @Nullable
    LivingEntity temperOn;
    /**
     * Where it means to stand for the part of the day it is on (its day goal's), and which part ({@link BuffaloGoals}'s
     * key: the bout and the act): mates on the same part keep {@link BuffaloGoals#SPACE} apart by it.
     */
    @Nullable
    BlockPos standAt;
    long standKey = Long.MIN_VALUE;
    /** How far in front of its middle its head bead reaches (blocks, at size 1): the bead's middle and half its width. */
    static final double HEAD_FRONT = 1.7 + 0.85;

    /**
     * Where its eyes go (drawn only, {@code client/BuffaloModel}): a player within 16 blocks (6 crouching; spectators too),
     * a lion within 24 and a crocodile within 14 even lying still, or any other animal stirring within 10; never another
     * buffalo. My picks.
     */
    public final Attention attention = new Attention(16.0, BuffaloEntity::catchesEye);

    private static double catchesEye(Entity seen, double distance, boolean moving) {
        if (seen instanceof BuffaloEntity) {
            return 0;
        }
        if (seen instanceof Player player) {
            return Attention.playerInReach(player, distance, 16.0, 6.0) ? 0.5 : 0;
        }
        if (seen instanceof CrocodileEntity) {
            return distance < 14.0 ? 0.4 : 0;
        }
        if (seen instanceof LionEntity lion) {
            // a lion is watched from far (the lions, 2026-11-25); a frozen one never, a creeping one only near (drawn: the
            // head-down part of a graze is the server's, so the gaze leaves that out)
            return lion.frozen() ? 0 : lion.stalking() ? (distance < LION_STALK_SEEN ? 0.3 : 0) : distance < LION_GALLOP_SEEN ? 0.3 : 0;
        }
        return moving && distance < 10.0 ? 1.0 : 0;
    }

    private final AnimalLife life;

    // --- lions (lion step 2, docs/plans/lion-hunt.md, part C) -----------------------------------------------------------
    /** A walking or creeping lion is seen this near, a galloping one this near (blocks). */
    public static final double LION_SEEN = 12.0, LION_STALK_SEEN = 12.0, LION_GALLOP_SEEN = 24.0;
    /** A graze's head is down from this clip tick to this one (the neck past 20 degrees in Neon's clip). */
    static final int GRAZE_HEAD_DOWN = 9, GRAZE_HEAD_UP = 69;
    /** The lions holding it now (their ids and the game time each last held it): its pace drops by half for each. */
    private final java.util.Map<Integer, Long> holders = new java.util.HashMap<>();

    /** Whether its head is up: not lying, not in the head-down part of a graze or a drink. */
    public boolean headUp() {
        byte d = doing();
        if (d == REST || d == REST_DOWN || d == REST_UP || d == DRINK) {
            return false;
        }
        return d != GRAZE || clipTick() < GRAZE_HEAD_DOWN || clipTick() > GRAZE_HEAD_UP;
    }

    /**
     * Whether its head is down and stays down a while yet (lying, or in the head-down part of a graze or a drink with at
     * least {@link #HEAD_UP_WARNING} ticks to go): a creeping lion moves only then, freezing a moment before a head comes
     * up (real lions time their steps to the grazing; it also keeps the lion from being caught moving by the order the
     * game ticks them in).
     */
    public boolean headDownAWhile() {
        byte d = doing();
        if (d == REST || d == REST_DOWN || d == REST_UP) {
            return true;
        }
        int t = clipTick();
        if (d == GRAZE) {
            return t >= GRAZE_HEAD_DOWN && t <= GRAZE_HEAD_UP - HEAD_UP_WARNING;
        }
        return d == DRINK && t >= 5 && t <= DRINK_HEAD_UP - HEAD_UP_WARNING;
    }

    static final int HEAD_UP_WARNING = 9, DRINK_HEAD_UP = 60;

    /**
     * Whether it sees this lion (the one rule for its gaze, its watch and its flight): a frozen lion never; lying, it sees
     * none; a galloping lion within {@link #LION_GALLOP_SEEN}; a creeping one within {@link #LION_STALK_SEEN} with its
     * head up; a walking one within {@link #LION_SEEN}.
     */
    public boolean seesLion(LionEntity lion) {
        if (!lion.isAlive() || lion.frozen()) {
            return false;
        }
        byte d = doing();
        if (d == REST || d == REST_DOWN || d == REST_UP) {
            return false;
        }
        double dist = distanceTo(lion);
        if (lion.galloping()) {
            return dist <= LION_GALLOP_SEEN;
        }
        if (lion.stalking()) {
            return dist <= LION_STALK_SEEN && headUp();
        }
        return lion.walking() && dist <= LION_SEEN;
    }

    /** A lion clings to it this tick (each holder calls it every tick it holds). */
    public void heldBy(Entity lion) {
        holders.put(lion.getId(), level().getGameTime());
    }

    /** How many lions hold it now. */
    public int holders() {
        if (holders.isEmpty()) {
            return 0;
        }
        long now = level().getGameTime();
        holders.values().removeIf(t -> now - t > 1);
        return holders.size();
    }

    /** Held by lions, its pace drops by half for each (two stop it); counted fresh each tick, so it frees itself. */
    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed * (float) Math.max(0.0, 1.0 - 0.5 * holders()));
    }

    // --- its spots: where lions grip it (the Motion Studio, phase 3) ---------------------------------------------------

    /**
     * A spot of its body where a hunter grips it, now (the world; {@link #SPOTS}): where the clip it plays puts the spot
     * at this tick (a loop's middle place: its phase at the speed it is drawn is not known here), with the model drawn
     * {@link #DRAW_AHEAD_PX} toward its head at its size. The same on the server and on every screen: its clip's tick is
     * counted on both ({@link #onSyncedDataUpdated}).
     */
    public Vec3 spotAt(int spot) {
        String clip;
        int tick;
        boolean loop;
        if (isDeadOrDying()) {
            clip = clipName();
            tick = deathTime - BLEND;
            loop = false;
        } else {
            byte d = doing();
            clip = clipName();
            tick = clipTick();
            loop = !oneShot(d);
        }
        return com.antcolonies.entity.body.Spots.world(SPOTS.at(spot, clip, tick, loop), position(), yBodyRot,
                MODEL_SCALE * getScale(), DRAW_AHEAD_PX);
    }

    /**
     * The clip it plays now: its doing's (calm: its idle, walking too), its death's when dying (lying already: dying where
     * it lies, death_down, not standing up first into death). Its holders pick their hold by it ({@link com.antcolonies.entity.body.Spots#holdClip}).
     */
    public String clipName() {
        byte d = doing();
        if (isDeadOrDying()) {
            return down(d) ? "death_down" : "death";
        }
        return d > CALM && d < DOING_CLIPS.length ? DOING_CLIPS[d] : "idle";
    }

    /** Whether this doing has it down on the ground (lying, lying down or getting up, held down, going down). */
    static boolean down(byte d) {
        return d == REST || d == REST_DOWN || d == REST_UP || d == HELD_SINK || d == HELD_DOWN;
    }

    /** Its size as its spots are drawn (its model's share times its scale): the size its holders' pairs are chosen by. */
    public double spotSize() {
        return MODEL_SCALE * getScale();
    }

    /**
     * Whether a lion is on it (both sides: its holders ride it). Held, it is drawn as its clips alone pose it, its head
     * not turned and its trunk not tilted to the ground ({@code client/BuffaloModel}): that is the body its holders are
     * set on ({@link #spotAt}) and the offline pair check measures (code review R12-71).
     */
    public boolean held() {
        for (Entity p : getPassengers()) {
            if (p instanceof LionEntity) {
                return true;
            }
        }
        return false;
    }

    /** The lions holding it that have finished their strike (it goes down under two of them). */
    int steadyHolders() {
        int n = 0;
        for (Entity p : getPassengers()) {
            if (p instanceof LionEntity lion && !lion.striking()) {
                n++;
            }
        }
        return n;
    }

    /** A doing started: its clip's tick counted on the players' screens too (the spots its holders are placed on). */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_DOING_ID && level().isClientSide) {
            doingSince = tickCount;
        }
    }

    /**
     * A lion gripping it rides it (the crocodile's way of carrying prey): placed on its spot here every tick, on the
     * server and on every screen alike, so jaws and hide never drift apart with lag and its feet never skate.
     */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (passenger instanceof LionEntity lion) {
            if (!LionHunt.ride(lion, this, move)) {
                move.accept(lion, lion.getX(), lion.getY(), lion.getZ());   // its spot not known yet (a screen): where it is
            }
            return;
        }
        super.positionRider(passenger, move);
    }

    /** Nobody steers a buffalo: a lion on it holds on, it does not drive it (Minecraft lets a mob steer the mob it rides). */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    /** A holder stands on the ground beside it, not in a riding seat. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** A holder letting go stays where it stands (Minecraft would set it on the buffalo's back). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    /** Its health follows its size (a bull's 60 at size 1): a calf is not as tough as a bull. Kept as a share when it grows. */
    public void fitHealth() {
        var max = getAttribute(Attributes.MAX_HEALTH);
        if (max == null) {
            return;
        }
        double want = Math.max(4.0, Math.round(FULL_HEALTH * getScale()));
        if (Math.abs(max.getBaseValue() - want) < 0.5) {
            return;
        }
        float share = getHealth() / getMaxHealth();
        max.setBaseValue(want);
        setHealth((float) (want * share));
    }

    static final double FULL_HEALTH = 60.0;

    @Override
    public void grew() {
        fitHealth();
    }

    // --- calves (lion step 2, docs/plans/lion-hunt.md, part A) ---------------------------------------------------------
    /** How often a cow looks to breed (ticks), how near her herd's bull must be (blocks), and how long she carries (ticks: 2 game days). */
    static final int BREED_LOOK = 200;
    static final double BULL_NEAR = 16.0;
    public static final int GESTATION = 48000;
    /** A calf is born this far to its mother's left (blocks, at her size). */
    public static final double CALF_SIDE = 1.8;
    /** Ticks until her calf is born (-1: not carrying). */
    private int carrying = -1;
    /** A calf's mother (her id), and the mother found in the world (looked for anew at most once a second). */
    @Nullable
    private UUID motherId;
    @Nullable
    private BuffaloEntity mother;
    private int motherAt = -100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Its hit beads along the drawn body ({@link HitChain}; measured on the model's geo, then moved {@link #DRAW_AHEAD_PX}
     * forward with the drawing): the head, horns and neck from just ahead of its box to the muzzle (0.85 to 2.55 ahead),
     * and the rump past the back of its box. Its box covers the middle of a body 3.8 blocks long.
     */
    private final HitChain chain = new HitChain(this,
            new HitChain.Link("head", 1.7, 0.2, 1.7F, 2.05F),
            new HitChain.Link("rump", -0.85, 0.1, 1.0F, 2.05F));

    public BuffaloEntity(EntityType<? extends BuffaloEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit beads take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new HeavyTurn(this, MAX_TURN);
        getNavigation().setCanFloat(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        BuffaloGoals.register(this, goalSelector);
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_DOING, CALM);
        builder.define(DATA_DOING_ID, 0);
    }

    // --- what it does ----------------------------------------------------------------------------------------------------

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    /**
     * Starts a doing and its clip. The doing it is on already goes on as it is (a loop is never restarted by being asked
     * again), unless it is a one-shot that has played out, which plays again from its start.
     */
    public void setDoing(byte doing) {
        byte now = doing();
        if (doing == now && !(oneShot(now) && doingFor() >= doingTicks(now))) {
            return;
        }
        doingSince = tickCount;
        entityData.set(DATA_DOING, doing);
        entityData.set(DATA_DOING_ID, entityData.get(DATA_DOING_ID) + 1);
        if (doing == SNORT) {
            playSound(SoundEvents.HORSE_BREATHE, 1.2F, 0.6F);
        } else if (doing == BELLOW) {
            playSound(SoundEvents.COW_AMBIENT, 2.0F, 0.45F);
        }
    }

    /** Ticks since its doing started (its own tick count). */
    int doingFor() {
        return tickCount - doingSince;
    }

    /** How far into its doing's clip it is (ticks of the clip; below 0 while the clip is still being eased into, {@link #BLEND}). */
    public int clipTick() {
        return doingFor() - BLEND;
    }

    /** A doing whose clip plays once (its clip file says so: all but calm, the rest, the moods and held down). */
    static boolean oneShot(byte doing) {
        return doing > CALM && doing < DOING_CLIPS.length && !clips().loops(DOING_CLIPS[doing]);
    }

    /** How long a one-shot doing lasts (ticks, its clip file's length and its blend in); 0 for calm and the loops. */
    static int doingTicks(byte doing) {
        return oneShot(doing) ? clips().ticks(DOING_CLIPS[doing]) + BLEND : 0;
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** Its herd's home (its pan's middle), or null until it has one (its first tick gives it one). */
    @Nullable
    public BlockPos home() {
        return home;
    }

    /** How far across its home's pan is (blocks; 0: a home with no water). */
    public int homeAcross() {
        return homeAcross;
    }

    /** Its herd's home, set when it is laid with its pan; it never moves after. */
    public void setHome(BlockPos home, int across) {
        this.home = home.immutable();
        this.homeAcross = across;
        matesAt = tickCount - MATES_EVERY; // look anew at the next ask
    }

    /**
     * Its mates: the living buffalo within {@link #MATES_REACH} with the same home, nearest first; looked for anew at most
     * once a second (every goal of every member asks).
     */
    public List<BuffaloEntity> mates() {
        if (tickCount - matesAt >= MATES_EVERY || tickCount < matesAt) {
            matesAt = tickCount;
            mates = HomeGroup.find(this, BuffaloEntity.class, home, BuffaloEntity::home, MATES_REACH);
        }
        return mates;
    }

    /**
     * A buffalo with no home (from a spawn egg or a summon) takes the home of the nearest buffalo within
     * {@link #HOME_FROM_MATE} that has one, joining its herd; with none near, its home is where it stands, with no water.
     */
    private void takeHome() {
        BuffaloEntity nearest = HomeGroup.nearestWithHome(this, BuffaloEntity.class, HOME_FROM_MATE, BuffaloEntity::home);
        if (nearest != null) {
            setHome(nearest.home, nearest.homeAcross);
        } else {
            setHome(blockPosition(), 0);
        }
    }

    /**
     * Its place in its herd's order (0 first): how many of its mates have a smaller id (its UUID, kept through saves). Mates
     * set off one after another in this order, and each takes its own spot round the herd's place by it.
     */
    int rank() {
        return HomeGroup.rank(this, grownMates(), HomeGroup::byId);
    }

    /**
     * Whether it is its herd's nearest grown member to something, each working it out from its own mate list (ties by id;
     * a calf nearer never answers for the herd: it never warns or charges).
     */
    boolean nearestOfHerdTo(Entity e) {
        return HomeGroup.nearestTo(this, grownMates(), e);
    }

    /** Its grown mates (calves have no rank and never answer for the herd). */
    private List<BuffaloEntity> grownMates() {
        List<BuffaloEntity> all = mates();
        for (BuffaloEntity m : all) {
            if (!m.grownUp()) {
                return all.stream().filter(BuffaloEntity::grownUp).toList();
            }
        }
        return all;
    }

    // --- its temper ------------------------------------------------------------------------------------------------------

    /**
     * Whoever hurt it, while it is still angry at them ({@link BuffaloGoals#REVENGE_TICKS} from the last hurt), or null.
     * Its mates read this: never told, they see it.
     */
    @Nullable
    public LivingEntity angryAt() {
        return anger.at(this);
    }

    /** Where it stood when last hurt (its cows bunch round this spot). */
    @Nullable
    Vec3 hurtAt() {
        return anger.where();
    }

    /** It gives up its anger (its foe got past its follow range). */
    void calmDown() {
        anger.calm();
    }

    /** Its temper now (for tests): watching, warning, charging, calming down, bunching, or none. */
    public BuffaloGoals.Temper temper() {
        return temper;
    }

    /** Whom its temper is on now (for tests), or null. */
    @Nullable
    public LivingEntity temperOn() {
        return temperOn;
    }

    /** Hurt by anything but another buffalo, it is angry at whoever did it ({@link #angryAt}). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            anger.hurt(this, source, by -> by instanceof BuffaloEntity, BuffaloGoals.REVENGE_TICKS);
        }
        return hurt;
    }

    /** Its head bead's box (its head, horns and neck): a hit lands only where this touches. */
    AABB headBox() {
        return chain.parts()[0].getBoundingBox();
    }

    /** How far it moves a tick now (blocks, eased): its pace. */
    double stride() {
        return stride;
    }

    /**
     * A hit lands on its target: its attack's harm and a toss ({@link BuffaloGoals#TOSS} along the way it faces, with
     * Minecraft's own little lift). Only ever on the target its temper is on.
     */
    void hitLands(LivingEntity target) {
        if (target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            target.knockback(BuffaloGoals.TOSS, Mth.sin(yaw), -Mth.cos(yaw));
            target.hurtMarked = true;               // a player's client is told it was tossed
            playHitSound();
        }
    }

    /** Where its jaws are at the chewing part of a graze: ahead of its middle, a little over its feet. */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + 0.2, getZ() + Mth.cos(yaw) * ahead);
    }

    /** An edible plant at its jaws ({@link Grazing#plantAt}), or null. */
    @Nullable
    BlockPos plantAtJaws() {
        Vec3 jaw = jawPoint();
        return Grazing.plantAt(level(), jaw.x, getY(), jaw.z);
    }

    /** Crops the plant at its jaws: a tall one to a short one, a short one left (a herd never bares the ground). Whether it ate. */
    boolean cropAtJaws() {
        return Grazing.crop(this, plantAtJaws(), false);
    }

    /** Faces a place, turning at most {@code step} degrees (never more than {@link #MAX_TURN}). */
    void face(double x, double z, float step) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        faceYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, step);
    }

    /** Turns toward a way (degrees, Minecraft's), at most {@code step} degrees; whether it faces that way now. */
    boolean faceYaw(float want, float step) {
        float yaw = Mth.approachDegrees(getYRot(), want, Math.min(step, MAX_TURN));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        return Math.abs(Mth.wrapDegrees(want - yaw)) < 3.0F;
    }

    /** Its hit landing (the ravager's). */
    void playHitSound() {
        playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 0.9F);
    }

    // --- its body ----------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        chain.place();
        double dx = getX() - xo, dz = getZ() - zo, moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 0.004) {
            movedTick = tickCount;
            stride += (moved - stride) * 0.5;
        }
        if (level().isClientSide) {
            attention.tick(this);
            return;
        }
        if (home == null) {
            takeHome();
        }
        breed();
        // stuck inside blocks (a tree grown through it, a mound, a player's building): out to the nearest open spot, once
        // every few seconds at most (Minecraft's own push-out only frees small overlaps)
        if (tickCount % FREE_EVERY == 1 && !level().noBlockCollision(this, getBoundingBox().deflate(0.05))) {
            free();
        }
        // held by lions (the Motion Studio phase 3, step 3): a grown buffalo held by two that have struck goes down under
        // them (real lionesses bring a buffalo down by their weight); fewer than two, it gets up again (its bull driving one
        // off frees it). A calf is knocked down at the strike (LionHunt).
        byte d = doing();
        if (!isDeadOrDying() && grownUp()) {
            int held = steadyHolders();
            if (held >= 2 && d != HELD_SINK && d != HELD_DOWN) {
                getNavigation().stop();
                setDoing(HELD_SINK);
            } else if (held < 2 && (d == HELD_SINK || d == HELD_DOWN)) {
                setDoing(REST_UP);
            }
            d = doing();
        }
        // a one-shot clip played out: lying down goes on into lying, going down under lions into struggling there, the
        // rest back to calm
        if (oneShot(d) && doingFor() >= doingTicks(d)) {
            setDoing(d == REST_DOWN ? REST : d == HELD_SINK ? HELD_DOWN : CALM);
        }
    }

    /** Whether it is grown (a calf and a teen are not: they keep at their mothers' sides and never fight). */
    public boolean grownUp() {
        return life.stage().grown();
    }

    /** Whether she carries a calf now. */
    public boolean carrying() {
        return carrying >= 0;
    }

    /** Ticks until her calf is born (-1: none); tests set it. */
    public int carryingLeft() {
        return carrying;
    }

    public void setCarrying(int ticks) {
        carrying = ticks;
    }

    /**
     * A cow's breeding (no courting walk: a herd grazes together): every {@link #BREED_LOOK} ticks a grown cow ready to
     * breed (her life kit's wait, room for her kind round her) with a grown bull of her herd within {@link #BULL_NEAR}
     * has bred, and carries her calf {@link #GESTATION}; then it is born at her side.
     */
    private void breed() {
        if (!(level() instanceof ServerLevel level) || life.isMale() || !grownUp()) {
            return;
        }
        if (carrying > 0) {
            carrying--;
            return;
        }
        if (carrying == 0) {
            carrying = -1;
            giveBirth(level);
            return;
        }
        if ((tickCount + getId()) % BREED_LOOK != 0 || !life.readyToBreed() || !roomToBreed(level)) {
            return;
        }
        for (BuffaloEntity m : mates()) {
            if (m.life.isMale() && m.grownUp() && distanceTo(m) <= BULL_NEAR) {
                life.bred();
                carrying = GESTATION;
                return;
            }
        }
    }

    /** Room for a calf: the buffalo within {@link RoomToBreed#REACH}, and the calves carried there, under the local limit. */
    private boolean roomToBreed(ServerLevel level) {
        List<BuffaloEntity> near = level.getEntitiesOfClass(BuffaloEntity.class, getBoundingBox().inflate(RoomToBreed.REACH), BuffaloEntity::isAlive);
        int count = near.size();
        for (BuffaloEntity b : near) {
            if (b.carrying()) {
                count++;
            }
        }
        return count < life.numbers().localLimit();
    }

    /** Her calf is born at her left side, young (its age 0), of her herd, knowing her. */
    @Nullable
    public BuffaloEntity giveBirth(ServerLevel level) {
        BuffaloEntity calf = SavannaEntities.BUFFALO.get().create(level);
        if (calf == null) {
            return null;
        }
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double side = CALF_SIDE * getScale();
        double x = getX() + Mth.cos(yaw) * side, z = getZ() + Mth.sin(yaw) * side;
        calf.moveTo(x, getY(), z, getYRot(), 0.0F);
        calf.setYBodyRot(getYRot());
        calf.setYHeadRot(getYRot());
        calf.life.setUp(true, level.getRandom());
        calf.life.setAge(0);
        calf.life.apply();
        calf.fitHealth();
        calf.setHealth(calf.getMaxHealth());
        if (home != null) {
            calf.setHome(home, homeAcross);
        }
        calf.motherId = getUUID();
        calf.mother = this;
        calf.motherAt = calf.tickCount;
        level.addFreshEntity(calf);
        return calf;
    }

    /** A calf's mother if she is alive and near (null for a grown one, or a calf whose mother died or is gone). */
    @Nullable
    public BuffaloEntity mother() {
        if (motherId == null || grownUp()) {
            mother = null;
            return null;
        }
        if (tickCount - motherAt >= MATES_EVERY || tickCount < motherAt) {
            motherAt = tickCount;
            mother = level() instanceof ServerLevel level && level.getEntity(motherId) instanceof BuffaloEntity b ? b : null;
        }
        return mother != null && mother.isAlive() && distanceTo(mother) <= MATES_REACH ? mother : null;
    }

    /** Whether this buffalo is that calf's mother. */
    public boolean motherOf(BuffaloEntity calf) {
        return getUUID().equals(calf.motherId) && !calf.grownUp();
    }

    /** Makes it this calf's mother (laying a herd, tests). */
    public void adopt(BuffaloEntity calf) {
        calf.motherId = getUUID();
        calf.mother = this;
        calf.motherAt = calf.tickCount;
    }

    /** How often it checks it is not stuck inside blocks (ticks), and how far it looks for room (blocks). */
    static final int FREE_EVERY = 60, FREE_REACH = 6;

    /** Moves it to the nearest spot within {@link #FREE_REACH} where its whole box is clear of blocks and liquid. */
    private void free() {
        AABB box = getBoundingBox();
        BlockPos at = blockPosition();
        for (int r = 1; r <= FREE_REACH; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // the ring at r only
                    }
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos p = at.offset(dx, dy, dz);
                        if (!level().isLoaded(p)) {
                            continue;
                        }
                        double x = p.getX() + 0.5, y = p.getY(), z = p.getZ() + 0.5;
                        AABB moved = box.move(x - getX(), y - getY(), z - getZ());
                        if (level().noBlockCollision(this, moved) && !level().containsAnyLiquid(moved)
                                && !level().getBlockState(p.below()).getCollisionShape(level(), p.below()).isEmpty()) {
                            moveTo(x, y, z, getYRot(), getXRot());
                            getNavigation().stop();
                            return;
                        }
                    }
                }
            }
        }
    }

    /** It swims up only where the water is deeper than {@link #FLOAT_DEPTH} over its feet, so it floats low. */
    @Override
    public double getFluidJumpThreshold() {
        return FLOAT_DEPTH * getScale();
    }

    /** The death clip (1.4 s, its file's length) is played to its end before the body goes. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= clips().ticks(clipName()) + BLEND && !level().isClientSide() && !isRemoved()) {
            if (level() instanceof ServerLevel level && getFluidHeight(net.minecraft.tags.FluidTags.WATER) < getBbHeight() * 0.5) {
                // its body stays: a carcass for the lions (and later the scavengers), in its death pose (lion step 2)
                RemainsEntity carcass = RemainsEntity.leave(level, RemainsEntity.Kind.BUFFALO, position(), yBodyRot, getScale());
                if (carcass != null) {
                    carcass.setKilledBy(killedByDen);
                }
            } else {
                level().broadcastEntityEvent(this, (byte) 60);
            }
            remove(Entity.RemovalReason.KILLED);
        }
    }

    /** The den of the pride whose lion killed it (its carcass is theirs), or null: set at its death from its killer. */
    @Nullable
    private BlockPos killedByDen;

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        killedByDen = source.getEntity() instanceof LionEntity lion ? lion.den() : null;
        super.die(source);
    }

    /** Kept for good, as Minecraft's villagers: the herd a player left is there when they come back. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Drawn whenever any of its body is in view: its box with its hit beads, and a little more for the horns' sweep (its
     * box is only the middle of its body, so its head and rump blinked away at the screen's edge with the box alone, BUGS
     * 271).
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return chain.cullingBox(0.4, 0.3);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        // Minecraft adds a random bonus to the follow range here; the buffalo's 32 is a rule (a revenge charge gives up
        // past it, the bull joins within it), so it is set back, the same for every buffalo
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.removeModifiers();
        }
        life.setUp(false, level.getRandom());
        life.apply();
        fitHealth();
        setHealth(getMaxHealth());
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
            tag.putInt("HomeAcross", homeAcross);
        }
        if (led) {
            tag.putBoolean("Led", true);
        }
        if (carrying >= 0) {
            tag.putInt("Carrying", carrying);
        }
        if (motherId != null) {
            tag.putUUID("Mother", motherId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        homeAcross = tag.getInt("HomeAcross");
        led = tag.getBoolean("Led");             // the film camera's walkers and game tests ("Led:1b")
        carrying = tag.contains("Carrying") ? tag.getInt("Carrying") : -1;
        motherId = tag.hasUUID("Mother") ? tag.getUUID("Mother") : null;
        life.apply();
    }

    /** What it is about, for tests' messages. */
    /**
     * What its mind is doing, for the in-game camera's recordings (development only; the Motion Studio, phase 0): its
     * doing, its temper, its health, how many lions hold it, grown or young.
     */
    @Override
    public void filmFacts(java.util.Map<String, Object> out) {
        out.put("doing", DOING_CLIPS[Mth.clamp(doing(), 0, DOING_CLIPS.length - 1)]);
        out.put("doing_for", doingFor());
        out.put("temper", temper.name());
        out.put("health", getHealth());
        out.put("held_by", holders());
        out.put("grown", grownUp());
        out.put("age", tickCount);
    }

    public String about() {
        Path p = getNavigation().getPath();
        return "home " + home + " (across " + homeAcross + "), doing " + DOING_CLIPS[Mth.clamp(doing(), 0, DOING_CLIPS.length - 1)]
                + " for " + doingFor() + ", temper " + temper + (temperOn == null ? "" : " on " + temperOn.getName().getString())
                + ", stand " + standAt + ", route " + (p == null ? "none" : p.getNextNodeIndex() + "/" + p.getNodeCount() + " to "
                + p.getTarget() + (p.canReach() ? "" : " (can't reach)")) + ", on ground " + onGround() + ", in water " + isInWater()
                + ", " + life.stage() + (carrying >= 0 ? " carrying " + carrying : "") + ", health " + getHealth() + "/" + getMaxHealth()
                + (holders() > 0 ? ", held by " + holders() : "");
    }

    // --- sounds (vanilla ones, re-pitched: the cow's grunts deep, the ravager's hooves) ------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return VOICE_PITCH + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.3F;
    }

    /** A grunt now and then (about every 20 s, not the cow's 4). */
    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.35F, 1.1F);
    }

    // --- clips -----------------------------------------------------------------------------------------------------------

    /** Clips named by the film camera ({@code film:<clip>}, a still one). */
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation DEATH_DOWN = RawAnimation.begin().thenPlayAndHold("death_down");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", BLEND, this::animate));
    }

    /** Whether it walks (drawing only): it moved within the last {@link #WALK_HOLD} ticks. */
    public boolean walking() {
        return tickCount - movedTick <= WALK_HOLD;
    }

    /**
     * How fast its looping clips play (idle, lying, the moods): 0.92 to 1.08 of their speed, fixed for each buffalo from its
     * id, so a herd does not breathe and flick its ears in step.
     */
    public double loopRate() {
        long bits = getUUID().getLeastSignificantBits() ^ getUUID().getMostSignificantBits();
        return 0.92 + 0.16 * (Math.floorMod(bits, 1000L) / 999.0);
    }

    private PlayState animate(AnimationState<BuffaloEntity> state) {
        AnimationController<BuffaloEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(down(doing()) ? DEATH_DOWN : DEATH);   // lying already: it dies where it lies
        }
        byte d = doing();
        int id = entityData.get(DATA_DOING_ID);
        if (id != shownDoingId) {
            shownDoingId = id;
            if (d != CALM) {
                player.forceAnimationReset();   // a new doing: its clip from the start, even the same clip again
            }
        }
        if (d > CALM && d < DOING_CLIPS.length) {
            if (!oneShot(d)) {
                player.setAnimationSpeed(loopRate());
            }
            return state.setAndContinue(clips().anim(DOING_CLIPS[d]));   // (looped, or once and held, as its clip file says)
        }
        if (walking()) {
            // its gait by how fast it goes, each clip's pace following its stride so its hooves never slide (in the water
            // too: the walk, at its swimming pace)
            double s = Gaits.perSize(stride, getScale());
            Gaits.Gait gait = gaitShift.shown(gaits.pick(s), s, player);
            player.setAnimationSpeed(gait.rate(s));
            return state.setAndContinue(gait.loop());
        }
        player.setAnimationSpeed(loopRate());
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
}
