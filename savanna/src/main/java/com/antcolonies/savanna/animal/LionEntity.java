package com.antcolonies.savanna.animal;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Attention;
import com.antcolonies.animal.HasLife;
import com.antcolonies.animal.HitChain;
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
import net.minecraft.util.RandomSource;
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
 * The African lion (into the game 2026-11-25, {@code docs/plans/lion.md}, step 1; Neon's models, coats and clips,
 * {@code docs/handoff/lion.md}). An animal: fun first, inspired by real lions, every number my pick unless said. One class
 * for three entity types, its {@link Form}: the male ({@code lion}), the lioness and the cub, each its own box, spawn egg
 * and clips.
 * <ul>
 * <li>Prides are laid with new land beside some pans ({@code worldgen/LionPrides}) and kept for good; never spawned by the
 * world's spawner. A pride's den (a tree's shade) is its only name: a mate is a lion with the same den ({@link #mates}).
 * Every member also keeps its pride's water (its pan's middle and how far across it is) and the way the pride lies at
 * the den. One from a spawn egg or a summon joins the nearest pride within {@link #DEN_FROM_MATE}, else its den is where it
 * stands.</li>
 * <li>Its body: two hit beads ({@link HitChain}) for its head and rump (a cub's head only), drawn whenever any of it is in
 * view; Minecraft's own 1-block step, its trunk tilted on the ground ({@code client/LionModel}); it turns at most
 * {@link #MAX_TURN} degrees a tick; it floats and swims out of deep water.</li>
 * <li>What it does has a clip of its own ({@link #doing}, synced), and its face a second layer over its gaits and poses
 * ({@link #mood}, synced). Its day and its temper are the goals' ({@link LionGoals}).</li>
 * </ul>
 */
public class LionEntity extends PathfinderMob implements GeoEntity, HasLife, com.antcolonies.debug.FilmFacts {
    /** The male, the lioness and the cub: one class, three entity types. */
    public enum Form {
        MALE("lion"), LIONESS("lioness"), CUB("lion_cub");

        /** The name of its clips and its body plan (both males, the Kalahari's own model too, play the lion's clips). */
        public final String clips;

        Form(String clips) {
            this.clips = clips;
        }
    }

    /** What it is doing, for the clips (synced). One-shots end by themselves ({@link #tick}); the loops last until a goal ends them. */
    public static final byte CALM = 0, SIT_DOWN = 1, SIT = 2, SIT_UP = 3, REST_DOWN = 4, REST = 5, REST_UP = 6, STRETCH_UP = 7, GROOM = 8,
            DRINK = 9, ROAR = 10, GREET = 11, NUZZLE = 12, CUB_GROOM = 13, PLAY_POUNCE = 14, PLAY_WRESTLE = 15, MOOD_ALERT = 16,
            MOOD_ANGRY = 17, MOOD_FLEHMEN = 18, MOOD_CURIOUS = 19, BLUFF = 20, SWIPE = 21, SWIPE_QUICK = 22, SWIPE_HEAVY = 23,
            SWIPE_COMBO = 24, BITE = 25, POUNCE = 26, GALLOP_SWAT = 27,
            // the hunt (lion step 2): creeping crouched, frozen flat, the leap from the stalk, the bounding smack, the pin
            STALK = 28, FREEZE = 29, LEAP_BITE = 30, JUMP_SMACK = 31, PIN = 32;
    /** Each doing's clip, by its number (a cub's roar is its {@code cub_call}). */
    private static final String[] DOING_CLIPS = {"idle", "sit_down", "sit", "sit_up", "rest_down", "rest", "rest_up", "rest_stretch_up",
            "groom", "drink", "roar", "pride_greet", "mama_nuzzle", "cub_groom", "play_pounce", "play_wrestle", "mood_alert", "mood_angry",
            "mood_flehmen", "mood_curious", "bluff_charge", "swipe", "swipe_quick", "swipe_heavy", "swipe_combo", "bite", "pounce", "gallop_swat",
            "stalk", "ambush_freeze", "stalk_leap_bite", "jump_smack", "takedown_pin"};
    /** Each one-shot doing's clip length (ticks, from the clip files); 0 for the loops. The roar's is the male's (see {@link #oneShotTicks}). */
    private static final int[] DOING_TICKS = {0, 15, 0, 15, 19, 0, 19, 76, 88, 60, 68, 36, 37, 64, 33, 44, 0, 0, 0, 0, 27, 20, 18, 21, 29,
            15, 25, 19, 0, 0, 29, 24, 26};
    /** The lioness's roar (her sharper contact call) and the cub's call (ticks). */
    static final int LIONESS_ROAR_TICKS = 50, CUB_CALL_TICKS = 32;
    /** The moods, by number: the face clips {@code face_<mood>}. */
    public static final String[] MOODS = {"content", "alert", "curious", "angry", "fearful", "sleepy", "flehmen"};
    public static final byte CONTENT = 0, ALERT = 1, CURIOUS = 2, ANGRY = 3, FEARFUL = 4, SLEEPY = 5, FLEHMEN = 6;
    /** The coats, by number: a male's Pride King, Golden Monarch and Kalahari black mane; a lioness's tawny, pale sand and the elder's. */
    public static final byte COAT_FIRST = 0, COAT_SECOND = 1, COAT_THIRD = 2;

    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(LionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_DOING_ID = SynchedEntityData.defineId(LionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(LionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_COAT = SynchedEntityData.defineId(LionEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(LionEntity.class);

    /** The clips' blend (ticks; Neon's card: all 120 switches checked at 4). */
    public static final int BLEND = 4;
    /** The death clip (1.2 s) is played to its end before the body goes. */
    static final int DEATH_TICKS = 24 + BLEND;
    /** Its paces (the move control's share of its speed; Minecraft's push goes about with its square, 1.73 x pace squared blocks a second): a shift of a few steps, a walk about 1.2, a trot about 2.8, a gallop about 7.5. Measured in game tests (BUGS 246). */
    public static final double STEP = 0.6, WALK = 0.83, TROT = 1.27, GALLOP = 2.08;
    /** It turns at most this much a tick while moving, and this much standing (degrees). */
    static final float MAX_TURN = 20.0F, STAND_TURN = 10.0F;
    /** In water deeper than this over its feet (blocks, at size 1) it swims up. */
    static final double FLOAT_DEPTH = 1.1;
    /** A spawn egg's or a summon's lion takes the den of the nearest lion this near that has one (blocks; the buffalo's). */
    static final double DEN_FROM_MATE = 16.0;
    /** Mates are looked for this far round it (blocks), anew once a second. */
    static final double MATES_REACH = 96.0;
    static final int MATES_EVERY = 20;
    /** Drawn this far off (blocks). */
    static final double DRAW_DISTANCE = 128.0;
    /** How often it checks it is not stuck inside blocks (ticks), and how far it looks for room (blocks). */
    static final int FREE_EVERY = 60, FREE_REACH = 6;
    static final int WALK_HOLD = 3;

    private final Form form = formOf(getType());
    /**
     * The model's measures for its form (model px, 16 a block, at size 1; measured from Neon's geo): drawn this far toward
     * its head (negative: toward its tail) so the middle between its feet sits on its box's middle; its front and hind feet
     * from that middle and how far out; its eyes ahead of the middle; the front of its head bead (blocks).
     */
    public final Measures measures = Measures.of(form);
    /**
     * Its gaits (walk, trot, gallop, and the stalk by name): each clip's own speed, where each starts and its play-speed
     * limits, from its body plan (the Motion Studio, phase 2, step 2; before, copies here, the lioness's and cub's stalk
     * a little off).
     */
    public final Gaits gaits = BodyPlans.gaitsOf(form.clips);
    /** The gait drawn (client): a change waits for the stride's matching moment. */
    private final GaitShift gaitShift = new GaitShift();

    /** The model's measures for one form. */
    public record Measures(double drawAheadPx, double feetPx, double feetOutPx, double eyesAheadPx, double headFront) {
        static Measures of(Form form) {
            return switch (form) {
                case MALE -> new Measures(-3.2, 13.4, 5.5, 30.2, 2.15);
                case LIONESS -> new Measures(-2.85, 11.85, 4.3, 26.9, 1.9);
                case CUB -> new Measures(-0.85, 5.55, 3.15, 14.1, 1.02);
            };
        }
    }

    /** Its pride's den (null until it has one), its water (its pan's middle; null: none) and how far across, and the way the pride lies at the den (degrees). */
    @Nullable
    BlockPos den, water;
    int waterAcross;
    float denYaw;
    /** The pride's elder (the first lioness laid): she leads every trip and wears the elder's coat. */
    boolean elder;
    /** The evening it last drank (or found no water to drink), so its mates know when all have. */
    long drankEvening = Long.MIN_VALUE;
    /** The chorus it last roared in (its number). */
    long roaredChorus = Long.MIN_VALUE;
    /** The mate it greets, nuzzles or plays with now (its day goal's; read by that mate). */
    @Nullable
    LionEntity focus;
    /** The game tick its doing started. */
    int doingSince;
    private List<LionEntity> mates = List.of();
    private int matesAt = -MATES_EVERY;
    private int movedTick = -100;
    private double stride;
    private int shownDoingId = -1;
    private int moodSince;
    /** Drawing only: the ground under its feet as drawn, and the neck's lift while lying and watching (degrees, eased). */
    public final DrawnGround drawn = new DrawnGround();
    public double drawnNeckLift;
    public long neckNanos;
    /** Led by a game test or the film camera: its own day goal is off ("Led:1b"). */
    public boolean led;
    private final Anger anger = new Anger();
    /** Its temper now and at whom (the temper goal's; read by tests and its mood). */
    LionGoals.Temper temper = LionGoals.Temper.NONE;
    @Nullable
    LivingEntity temperOn;
    /** The part of its day it is on (the day goal's; read by its mood). */
    LionGoals.Act act = LionGoals.Act.REST;

    /** Where its eyes go (drawn only): a player within 24 (12 crouching; spectators too), a buffalo within 24, any other animal stirring within 10; never another lion. */
    public final Attention attention = new Attention(24.0, LionEntity::catchesEye);

    private static double catchesEye(Entity seen, double distance, boolean moving) {
        if (seen instanceof LionEntity) {
            return 0;
        }
        if (seen instanceof Player player) {
            return Attention.playerInReach(player, distance, 24.0, 12.0) ? 0.5 : 0;
        }
        if (seen instanceof BuffaloEntity) {
            return distance < 24.0 ? 0.3 : 0;
        }
        return moving && distance < 10.0 ? 1.0 : 0;
    }

    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Its hit beads (measured on the geo, moved with the drawing): an adult's head and rump, a cub's head. */
    private final HitChain chain = chainFor(form, this);

    public LionEntity(EntityType<? extends LionEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit beads take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        moveControl = new HeavyTurn(this, MAX_TURN);
        getNavigation().setCanFloat(true);
    }

    static Form formOf(EntityType<?> type) {
        if (type == SavannaEntities.LIONESS.get()) {
            return Form.LIONESS;
        }
        if (type == SavannaEntities.LION_CUB.get()) {
            return Form.CUB;
        }
        return Form.MALE;
    }

    private static HitChain chainFor(Form form, LionEntity lion) {
        return switch (form) {
            case MALE -> new HitChain(lion, new HitChain.Link("head", 1.6, 0.75, 1.1F, 1.25F), new HitChain.Link("rump", -0.85, 0.3, 0.8F, 1.2F));
            case LIONESS -> new HitChain(lion, new HitChain.Link("head", 1.43, 0.7, 0.95F, 1.05F), new HitChain.Link("rump", -0.75, 0.25, 0.7F, 1.05F));
            case CUB -> new HitChain(lion, new HitChain.Link("head", 0.72, 0.45, 0.6F, 0.55F));
        };
    }

    /** A male: 50 health, armour 3, a hit of 9 (at Normal); my picks. */
    public static AttributeSupplier.Builder maleAttributes() {
        return base(50.0, 3.0, 9.0, 0.6);
    }

    /** A lioness: 40 health, armour 2, a hit of 7. */
    public static AttributeSupplier.Builder lionessAttributes() {
        return base(40.0, 2.0, 7.0, 0.6);
    }

    /** A cub: 10 health, no armour; it never hits a player. */
    public static AttributeSupplier.Builder cubAttributes() {
        return base(10.0, 0.0, 1.0, 0.0);
    }

    private static AttributeSupplier.Builder base(double health, double armour, double hit, double knockback) {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, hit).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockback).add(Attributes.ARMOR, armour);
    }

    @Override
    protected void registerGoals() {
        LionGoals.register(this, goalSelector);
    }

    @Override
    public AnimalLife life() {
        return life;
    }

    public Form form() {
        return form;
    }

    public boolean isCub() {
        return form == Form.CUB;
    }

    public boolean isAdult() {
        return form != Form.CUB;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        AnimalLife.define(builder, DATA_LIFE);
        builder.define(DATA_DOING, CALM);
        builder.define(DATA_DOING_ID, 0);
        builder.define(DATA_MOOD, CONTENT);
        builder.define(DATA_COAT, COAT_FIRST);
    }

    // --- what it does ----------------------------------------------------------------------------------------------------

    public byte doing() {
        return entityData.get(DATA_DOING);
    }

    /** Starts a doing and its clip; a loop it is on goes on as it is, a one-shot played out plays again from its start. */
    void setDoing(byte doing) {
        byte now = doing();
        if (doing == now && !(oneShot(now) && doingFor() >= doingTicks(now))) {
            return;
        }
        doingSince = tickCount;
        entityData.set(DATA_DOING, doing);
        entityData.set(DATA_DOING_ID, entityData.get(DATA_DOING_ID) + 1);
        if (doing == MOOD_ANGRY && isAdult() && now != MOOD_ANGRY) {
            playSound(SoundEvents.WOLF_GROWL, 1.6F, form == Form.MALE ? 0.45F : 0.55F);
        }
    }

    int doingFor() {
        return tickCount - doingSince;
    }

    /** How far into its doing's clip it is (below 0 while the clip is still being eased into). */
    int clipTick() {
        return doingFor() - BLEND;
    }

    static boolean oneShot(byte doing) {
        return doing > CALM && doing < DOING_TICKS.length && DOING_TICKS[doing] > 0;
    }

    /** A one-shot's clip length for its form (ticks). */
    int oneShotTicks(byte doing) {
        if (doing == ROAR) {
            return form == Form.MALE ? DOING_TICKS[ROAR] : form == Form.LIONESS ? LIONESS_ROAR_TICKS : CUB_CALL_TICKS;
        }
        return DOING_TICKS[doing];
    }

    /** How long a one-shot doing lasts (ticks, its blend in); 0 for calm and the loops. */
    int doingTicks(byte doing) {
        return oneShot(doing) ? oneShotTicks(doing) + BLEND : 0;
    }

    /** Whether its form has this doing's clip. */
    boolean has(byte doing) {
        return switch (doing) {
            case NUZZLE, PLAY_POUNCE, PLAY_WRESTLE, MOOD_CURIOUS -> form == Form.CUB;
            case CUB_GROOM -> form == Form.LIONESS;
            case SWIPE_HEAVY, SWIPE_COMBO -> form == Form.MALE;
            case MOOD_FLEHMEN, BLUFF, GALLOP_SWAT, LEAP_BITE, JUMP_SMACK, PIN -> form != Form.CUB;
            case FREEZE -> form == Form.LIONESS;
            default -> true;
        };
    }

    /** Lying (down, still, getting up or grooming lying). */
    boolean lying() {
        byte d = doing();
        return d == REST || d == REST_DOWN || d == REST_UP || d == GROOM || d == STRETCH_UP;
    }

    /** Sitting (down, still or getting up). */
    boolean sitting() {
        byte d = doing();
        return d == SIT || d == SIT_DOWN || d == SIT_UP;
    }

    public float ownSize() {
        return life.ownSize();
    }

    // --- its pride -----------------------------------------------------------------------------------------------------------

    @Nullable
    public BlockPos den() {
        return den;
    }

    @Nullable
    public BlockPos water() {
        return water;
    }

    public int waterAcross() {
        return waterAcross;
    }

    public float denYaw() {
        return denYaw;
    }

    public boolean isElder() {
        return elder;
    }

    /** Its pride's den, the way the pride lies there, and its water (null: none). */
    public void setDen(BlockPos den, float yaw, @Nullable BlockPos water, int across) {
        this.den = den.immutable();
        this.denYaw = yaw;
        this.water = water == null ? null : water.immutable();
        this.waterAcross = water == null ? 0 : across;
        matesAt = tickCount - MATES_EVERY;
    }

    /** The pride's elder: she leads every trip and wears the elder's coat. */
    public void setElder(boolean elder) {
        this.elder = elder && form == Form.LIONESS;
        if (this.elder) {
            setCoat(COAT_THIRD);
        }
    }

    public byte coat() {
        return entityData.get(DATA_COAT);
    }

    public void setCoat(byte coat) {
        entityData.set(DATA_COAT, coat);
    }

    /** A coat rolled for its form: a male Pride King 1 in 2, Golden Monarch 1 in 3, Kalahari 1 in 6; a lioness tawny, 1 in 4 pale sand. */
    public void rollCoat(RandomSource random) {
        float r = random.nextFloat();
        setCoat(switch (form) {
            case MALE -> r < 0.5F ? COAT_FIRST : r < 0.8333F ? COAT_SECOND : COAT_THIRD;
            case LIONESS -> r < 0.25F ? COAT_SECOND : COAT_FIRST;
            case CUB -> COAT_FIRST;
        });
    }

    /** Its pride mates (lions with the same den), nearest first; looked for anew at most once a second. */
    public List<LionEntity> mates() {
        if (tickCount - matesAt >= MATES_EVERY || tickCount < matesAt) {
            matesAt = tickCount;
            mates = HomeGroup.find(this, LionEntity.class, den, LionEntity::den, MATES_REACH);
        }
        return mates;
    }

    /** The pride's order: the elder first, then the lionesses, the cubs and the males last; by id within each. */
    static final Comparator<LionEntity> ORDER = Comparator.comparingInt(LionEntity::orderGroup).thenComparing(Entity::getUUID);

    private int orderGroup() {
        return elder ? 0 : switch (form) {
            case LIONESS -> 1;
            case CUB -> 2;
            case MALE -> 3;
        };
    }

    /** Its place in its pride's order (0 first). */
    int rank() {
        return HomeGroup.rank(this, mates(), ORDER);
    }

    /** Whether a cub is in its pride (a lioness is then a mother: the creche). */
    boolean prideHasCubs() {
        for (LionEntity m : mates()) {
            if (m.isCub()) {
                return true;
            }
        }
        return false;
    }

    /** One from an egg or a summon joins the nearest pride within {@link #DEN_FROM_MATE}; with none, its den is where it stands. */
    private void takeDen() {
        LionEntity nearest = HomeGroup.nearestWithHome(this, LionEntity.class, DEN_FROM_MATE, LionEntity::den);
        if (nearest != null) {
            setDen(nearest.den, nearest.denYaw, nearest.water, nearest.waterAcross);
        } else {
            setDen(blockPosition(), getYRot(), null, 0);
        }
    }

    // --- its temper ------------------------------------------------------------------------------------------------------

    /** Whoever hurt it, while it is still angry at them ({@link LionGoals#REVENGE_TICKS} from the last hurt), or null. */
    @Nullable
    public LivingEntity angryAt() {
        return anger.at(this);
    }

    @Nullable
    Vec3 hurtAt() {
        return anger.where();
    }

    void calmDown() {
        anger.calm();
    }

    /** The part of its day it is on (for tests). */
    public LionGoals.Act act() {
        return act;
    }

    /** The evening it last drank (for tests). */
    public long drankEvening() {
        return drankEvening;
    }

    public LionGoals.Temper temper() {
        return temper;
    }

    @Nullable
    public LivingEntity temperOn() {
        return temperOn;
    }

    /** Hurt by anything but a pride mate, it is angry at whoever did it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt) {
            // never angry at its own pride, nor ever at a buffalo (no revenge on prey: a lion backs off from a charging
            // buffalo, LionHunt; a revenge fight there was a pride-against-herd brawl)
            anger.hurt(this, source, by -> by instanceof BuffaloEntity || by instanceof LionEntity l && den != null && den.equals(l.den),
                    LionGoals.REVENGE_TICKS);
            if (source.getEntity() instanceof BuffaloEntity b) {
                hunt.hitBy(this, b);
            }
        }
        return hurt;
    }

    /** Its hunt's state (lion step 2; kept on the lion, so a short interruption resumes it). */
    final LionHunt.State hunt = new LionHunt.State();

    /** Whether it is creeping crouched (and moving): a buffalo with its head up sees it within 12. */
    public boolean stalking() {
        return doing() == STALK && tickCount - movedTick <= WALK_HOLD;
    }

    /** Whether it lies frozen flat ({@code ambush_freeze}): never seen. */
    public boolean frozen() {
        return doing() == FREEZE;
    }

    /** Whether it runs at a gallop now. */
    public boolean galloping() {
        return tickCount - movedTick <= WALK_HOLD && gaits.pick(Gaits.perSize(stride, getScale())).clip().equals("gallop");
    }

    /** Whether it is on a hunt now (spread, stalking or in the fight): it stares at no player. */
    public boolean hunting() {
        return hunt.hunting();
    }

    /** Where it is in a hunt now (tests). */
    public LionHunt.Stage huntStage() {
        return hunt.stage;
    }

    /** Whether it knows a feast's carcass (kept through a save; tests). */
    public boolean feastKnown() {
        return hunt.feastId != null;
    }

    /** Whether it has come to a hunting try yet (the leader's window: a try is taken whatever comes of it; tests). */
    public boolean huntTried() {
        return hunt.tryKey != Long.MIN_VALUE;
    }

    /** Whether it has eaten its fill at a kill (tests). */
    public boolean fedFull() {
        return hunt.full;
    }

    /** Whether it clings to prey now. */
    public boolean holding() {
        return hunt.holding();
    }

    /** Ticks it has lived since its last meal (hungry from {@link LionHunt#HUNGRY}). */
    public int sinceMeal() {
        return hunt.sinceMeal;
    }

    public void setSinceMeal(int ticks) {
        hunt.sinceMeal = ticks;
    }

    /** Its head bead's box: a hit lands only where this touches. */
    AABB headBox() {
        return chain.parts()[0].getBoundingBox();
    }

    /** The gap between the front of its head and someone (blocks, flat). */
    double headGap(Entity e) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return Math.sqrt(dx * dx + dz * dz) - measures.headFront() * getScale() - e.getBbWidth() / 2.0;
    }

    /** How far it moves a tick now (blocks, eased). */
    double stride() {
        return stride;
    }

    /** A strike lands: its hit and a light shove along the way it faces. */
    void hitLands(LivingEntity target) {
        if (target.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            target.knockback(LionGoals.SHOVE, Mth.sin(yaw), -Mth.cos(yaw));
            target.hurtMarked = true;
            playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 1.3F);
        }
    }

    /** Faces a place, turning at most {@code step} degrees (never more than it may). */
    void face(double x, double z, float step) {
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < 0.02) {
            return;
        }
        faceYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F, step);
    }

    /** Turns toward a way (degrees), at most {@code step} (and {@link #STAND_TURN} standing); whether it faces that way now. */
    boolean faceYaw(float want, float step) {
        float most = tickCount - movedTick <= WALK_HOLD ? MAX_TURN : STAND_TURN;
        float yaw = Mth.approachDegrees(getYRot(), want, Math.min(step, most));
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        return Math.abs(Mth.wrapDegrees(want - yaw)) < 3.0F;
    }

    private int turnedTick = -100;

    // --- its mood ----------------------------------------------------------------------------------------------------------

    /** Its mood now (synced): the face its face layer shows. */
    public byte mood() {
        return entityData.get(DATA_MOOD);
    }

    /** A calmer mood waits this long after the last change (ticks: 3 s), so the face does not flicker. */
    static final int MOOD_HOLD = 60;

    /**
     * Its mood, looked up (never judged anew): from its temper stage, what it is doing and the part of its day. The
     * temper is the one judge of a player's threat; the mood only shows it.
     */
    byte workOutMood() {
        switch (temper) {
            case WATCH -> {
                return isCub() ? CURIOUS : ALERT;
            }
            case WARN, BLUFF, ATTACK, CALM, RING -> {
                return ANGRY;
            }
            case HIDE -> {
                return FEARFUL;
            }
            default -> {
            }
        }
        byte d = doing();
        if (d == MOOD_FLEHMEN) {
            return FLEHMEN;
        }
        if (d == GREET || d == NUZZLE || d == CUB_GROOM || d == PLAY_POUNCE || d == PLAY_WRESTLE) {
            return CONTENT;
        }
        if (lying()) {
            Player p = level().getNearestPlayer(this, 24.0);
            if (p != null && !p.isSpectator() && !p.isCreative() && Attention.playerInReach(p, distanceTo(p), 24.0, 12.0)) {
                return ALERT;                           // a lying lion watched: it lifts its head and stares, and stays down
            }
            return act == LionGoals.Act.REST ? SLEEPY : CONTENT;
        }
        return CONTENT;
    }

    /** Mood urgency: a more urgent mood comes at once, a calmer one only after {@link #MOOD_HOLD}. */
    private static int urgency(byte mood) {
        return switch (mood) {
            case ANGRY, FEARFUL -> 3;
            case ALERT, CURIOUS -> 2;
            case FLEHMEN -> 1;
            default -> 0;
        };
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
        if (Math.abs(Mth.wrapDegrees(yBodyRot - yBodyRotO)) > 1.0F) {
            turnedTick = tickCount;                     // a turn in place shows a slow walk (its feet never skate); both sides
        }
        if (level().isClientSide) {
            attention.tick(this);
            return;
        }
        if (den == null) {
            takeDen();
        }
        hunt.sinceMeal = Math.min(hunt.sinceMeal + 1, LionHunt.HUNGRY * 4);
        if (tickCount % FREE_EVERY == 1 && !level().noBlockCollision(this, getBoundingBox().deflate(0.05))) {
            free();
        }
        byte d = doing();
        if (d == ROAR && clipTick() == (isCub() ? 5 : 10)) {
            roarSound();
        }
        if (oneShot(d) && doingFor() >= doingTicks(d)) {
            setDoing(d == REST_DOWN || d == GROOM ? REST : d == SIT_DOWN ? SIT : CALM);
        }
        if (tickCount % 5 == 0) {
            byte now = mood(), want = workOutMood();
            if (want != now && (urgency(want) > urgency(now) || tickCount - moodSince >= MOOD_HOLD)) {
                entityData.set(DATA_MOOD, want);
                moodSince = tickCount;
            }
        }
    }

    /** Its roar: heard far (a male's about 128 blocks, a lioness's 64, a cub's call 16). */
    private void roarSound() {
        switch (form) {
            case MALE -> playSound(SoundEvents.RAVAGER_ROAR, 8.0F, 0.7F);
            case LIONESS -> playSound(SoundEvents.RAVAGER_ROAR, 4.0F, 0.85F);
            case CUB -> playSound(SoundEvents.CAT_PURREOW, 1.0F, 1.3F);
        }
    }

    private void free() {
        AABB box = getBoundingBox();
        BlockPos at = blockPosition();
        for (int r = 1; r <= FREE_REACH; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue;
                    }
                    for (int oy = -1; oy <= 2; oy++) {
                        BlockPos p = at.offset(ox, oy, oz);
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

    @Override
    public double getFluidJumpThreshold() {
        return FLOAT_DEPTH * getScale() * (isCub() ? 0.5 : 1.0);
    }

    /** Lions never shove each other (a pride lies touching, in a row; a shove slid them off their beds). */
    @Override
    protected void doPush(Entity other) {
        if (!(other instanceof LionEntity) && !(other instanceof BuffaloEntity && holding())) {
            super.doPush(other);                        // nor does a lion clinging to prey shove it about
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    /** Kept for good: the pride a player left is there when they come back. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** Drawn whenever any of its body is in view: its box with its hit beads, and a little more for the tail. */
    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = getBoundingBox();
        if (firstTick) {
            return box.inflate(2.4 * getScale(), 0.4 * getScale(), 2.4 * getScale());
        }
        for (net.neoforged.neoforge.entity.PartEntity<?> part : chain.parts()) {
            box = box.minmax(part.getBoundingBox());
        }
        double tail = isCub() ? 0.6 : 1.3;
        return box.inflate(tail * getScale(), 0.3 * getScale(), tail * getScale());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        var follow = getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.removeModifiers();               // its 32 is a rule (a hurt lion gives up past it), the same for every lion
        }
        life.setUp(false, level.getRandom());
        life.apply();
        rollCoat(level.getRandom());
        hunt.sinceMeal = LionHunt.HUNGRY;           // one from an egg or a summon is hungry (a hunt can be set up at once); the world's prides roll theirs
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (den != null) {
            tag.put("Den", NbtUtils.writeBlockPos(den));
            tag.putFloat("DenYaw", denYaw);
        }
        if (water != null) {
            tag.put("Water", NbtUtils.writeBlockPos(water));
            tag.putInt("WaterAcross", waterAcross);
        }
        tag.putBoolean("Elder", elder);
        tag.putByte("Coat", coat());
        tag.putLong("Drank", drankEvening);
        tag.putLong("Roared", roaredChorus);
        hunt.save(tag);
        byte d = doing();
        if (d == REST || d == SIT) {
            tag.putByte("Lying", d);                    // a lying pride comes back lying, not standing up in front of you
        }
        if (led) {
            tag.putBoolean("Led", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        den = NbtUtils.readBlockPos(tag, "Den").orElse(null);
        denYaw = tag.getFloat("DenYaw");
        water = NbtUtils.readBlockPos(tag, "Water").orElse(null);
        waterAcross = tag.getInt("WaterAcross");
        elder = tag.getBoolean("Elder");
        setCoat(tag.getByte("Coat"));
        drankEvening = tag.contains("Drank") ? tag.getLong("Drank") : Long.MIN_VALUE;
        roaredChorus = tag.contains("Roared") ? tag.getLong("Roared") : Long.MIN_VALUE;
        led = tag.getBoolean("Led");
        hunt.load(tag);
        byte lying = tag.getByte("Lying");
        if (lying == REST || lying == SIT) {
            setDoing(lying);
        }
        life.apply();
    }

    /** What it is about, for tests' messages. */
    public String about() {
        Path p = getNavigation().getPath();
        return form + " den " + den + ", doing " + DOING_CLIPS[Mth.clamp(doing(), 0, DOING_CLIPS.length - 1)] + " for " + doingFor()
                + ", act " + act + ", temper " + temper + (temperOn == null ? "" : " on " + temperOn.getName().getString())
                + ", mood " + MOODS[Mth.clamp(mood(), 0, MOODS.length - 1)] + ", route " + (p == null ? "none" : p.getNextNodeIndex() + "/"
                + p.getNodeCount() + " to " + p.getTarget() + (p.canReach() ? "" : " (can't reach)")) + ", on ground " + onGround()
                + ", hunt " + hunt.stage + (hunt.holding ? " holding" : "") + ", since meal " + hunt.sinceMeal + (hunt.full ? " full" : "");
    }

    /**
     * What its mind is doing, for the in-game camera's recordings (development only; the Motion Studio, phase 0): its
     * doing and how far into its clip, its part of the day and its temper, and its hunt (stage, its prey, whether and
     * where it holds on, its hunger).
     */
    @Override
    public void filmFacts(java.util.Map<String, Object> out) {
        out.put("doing", DOING_CLIPS[Mth.clamp(doing(), 0, DOING_CLIPS.length - 1)]);
        out.put("clip_tick", clipTick());
        out.put("act", act.name());
        out.put("temper", temper.name());
        out.put("stage", hunt.stage.name());
        out.put("holding", hunt.holding());
        if (hunt.holding()) {
            out.put("hold_angle", hunt.holdAngle);
        }
        if (hunt.prey != null) {
            out.put("prey", hunt.prey.getId());
        }
        out.put("since_meal", hunt.sinceMeal);
        out.put("full", hunt.full);
        out.put("age", tickCount);
    }

    // --- sounds (vanilla ones, re-pitched) ----------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return isCub() ? SoundEvents.CAT_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isCub() ? SoundEvents.CAT_HURT : SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isCub() ? SoundEvents.CAT_DEATH : SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        float base = switch (form) {
            case MALE -> 0.75F;
            case LIONESS -> 0.9F;
            case CUB -> 1.3F;
        };
        return base + (random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 600;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, isCub() ? 0.1F : 0.2F, isCub() ? 1.2F : 0.7F);
    }

    // --- clips -----------------------------------------------------------------------------------------------------------

    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation CUB_CALL = RawAnimation.begin().thenPlayAndHold("cub_call");
    private static final RawAnimation[] DOING_ANIMS = new RawAnimation[DOING_CLIPS.length];
    private static final RawAnimation[] FACES = new RawAnimation[MOODS.length];

    static {
        for (byte d = 1; d < DOING_CLIPS.length; d++) {
            DOING_ANIMS[d] = oneShot(d) ? RawAnimation.begin().thenPlayAndHold(DOING_CLIPS[d]) : RawAnimation.begin().thenLoop(DOING_CLIPS[d]);
        }
        for (int m = 0; m < MOODS.length; m++) {
            FACES[m] = RawAnimation.begin().thenLoop("face_" + MOODS[m]);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", BLEND, this::animate));
        // the face's own layer: face_<mood> over the plain loops only (idle, the gaits, sitting, lying); during a one-shot
        // or a whole-body mood clip it stops, as the otter's head layer does, because every lion clip keys the face's
        // bones and the later controller's keys win (a face over the roar would hold its jaw shut)
        controllers.add(new AnimationController<>(this, "face", BLEND, state -> {
            if (!faceShows()) {
                state.getController().forceAnimationReset();
                return PlayState.STOP;
            }
            byte m = mood();
            if (m == FLEHMEN && isCub()) {
                m = CONTENT;                            // (a cub has no flehmen face)
            }
            return state.setAndContinue(FACES[Mth.clamp(m, 0, FACES.length - 1)]);
        }));
    }

    /** Whether its face layer shows: alive, on a plain loop (calm and its gaits, sitting, lying still), not a film clip. */
    public boolean faceShows() {
        if (isDeadOrDying()) {
            return false;
        }
        Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return film.getString().startsWith("film:face:");
        }
        byte d = doing();
        return d == CALM || d == SIT || d == REST;
    }

    /** Whether it walks (drawing only): it moved within the last few ticks, or is turning in place. */
    public boolean walking() {
        return tickCount - movedTick <= WALK_HOLD || tickCount - turnedTick <= WALK_HOLD;
    }

    /** How fast its looping clips play: 0.92 to 1.08 of their speed, fixed for each lion from its id. */
    public double loopRate() {
        long bits = getUUID().getLeastSignificantBits() ^ getUUID().getMostSignificantBits();
        return 0.92 + 0.16 * (Math.floorMod(bits, 1000L) / 999.0);
    }

    private PlayState animate(AnimationState<LionEntity> state) {
        AnimationController<LionEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            String clip = film.getString().startsWith("film:face:") ? "idle" : film.getString().substring(5);
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(clip, c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        byte d = doing();
        int id = entityData.get(DATA_DOING_ID);
        if (id != shownDoingId) {
            shownDoingId = id;
            if (d != CALM) {
                player.forceAnimationReset();
            }
        }
        if (d == STALK) {
            // creeping: the stalk at its pace; still between steps, held where it is (crouched, not frozen flat)
            double s = Gaits.perSize(stride, getScale());
            player.setAnimationSpeed(tickCount - movedTick > WALK_HOLD ? 0.0 : gaits.of("stalk").rate(s));
            return state.setAndContinue(DOING_ANIMS[d]);
        }
        if (d > CALM && d < DOING_ANIMS.length) {
            if (!oneShot(d)) {
                player.setAnimationSpeed(loopRate());
            }
            return state.setAndContinue(d == ROAR && isCub() ? CUB_CALL : DOING_ANIMS[d]);
        }
        if (walking()) {
            // its gait by how fast it goes, each clip's pace following its stride (its body plan's gaits), changed on the
            // matching footfall; turning in place: a slow walk, its feet never skating
            double s = Gaits.perSize(stride, getScale());
            boolean turning = tickCount - movedTick > WALK_HOLD;
            Gaits.Gait gait = gaitShift.shown(turning ? gaits.of("walk") : gaits.pick(s), player);
            player.setAnimationSpeed(turning && gait.clip().equals("walk") ? 0.5 : gait.rate(s));
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
        if (chain != null) {
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
