package com.antcolonies.savanna.animal;

import javax.annotation.Nullable;

import com.antcolonies.entity.body.SteadyController;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
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
 * The bull shark (M9 river life, 2026-11-22, {@code docs/plans/otter-and-bull-shark.md} steps 5 to 7; fun first, inspired
 * by the real <i>Carcharhinus leucas</i>, which swims up from the sea into the wide lower rivers). Water only: it keeps to
 * the deep middle channel of the coast and near-inland rivers ({@code worldgen/RiverZones}), never into water under
 * {@link #MIN_DEPTH} blocks deep (the one place that judges it: {@link #shallowAhead}, read by its swimming), alone and
 * rare. Neon's approved model v14 ({@code docs/handoff/bull_shark.md}): 5.5 blocks long at size 1, each shark its own
 * length 5.0 to 7.5 blocks in half-block steps ({@link #rollLength}), the old scarred look from 6.0 up.
 *
 * <p>Its life ({@link BullSharkGoals}): by day it patrols slowly up and down the deep channel (sharks never stop
 * swimming); at dusk and by night a hungry shark hunts: the fin out cutting the surface ({@code surface_prowl}), a slow
 * approach, then a burst and a bite; a barb or tilapia is swallowed, a catfish is held across the jaws and shaken apart
 * ({@code thrash}), its front half left as remains; now and then it takes prey near the surface with a leap clean out of
 * the water ({@code breach}). Fed, it cruises slowly about a game day before it hunts again, so it never clears a river.
 * A player swimming in deep water is first bumped (a test, no harm); one who stays near or hits it gets the hunched
 * warning ({@code threat_display}) and then a bite. It warns off a crocodile that comes close, keeps clear of hippos, and
 * now and then lifts its head out to look at a player on the bank or in a boat ({@code spy_hop}).
 *
 * <p>Stranded out of the water it thrashes ({@code beached_flop}), flopping toward water within reach, and slowly
 * suffocates; dying, it rolls belly up ({@code death}) and floats up a little.
 */
public class BullSharkEntity extends WaterAnimal implements GeoEntity {
    // --- the model (tools/bull_shark_models.py, v14; model pixels over 16, blocks at size 1) -------------------------
    /** The model's length, snout to tail tip (88 pixels). */
    public static final float MODEL_LENGTH = 5.5F;
    /** Its own lengths (blocks; the user's 5.0 to 7.5) and where the scarred old look starts (the card's 6.0). */
    public static final float SHORTEST = 5.0F, LONGEST = 7.5F, SCARRED_FROM = 6.0F;
    /** The water's surface over its feet when it prowls with the fin out (the card: 24.5 pixels). */
    public static final double WATERLINE = 24.5 / 16.0;
    /** Its jaws (the prey bone: 33.5 pixels ahead of its middle, 12.15 over its feet) and its snout's tip (44 ahead). */
    public static final double JAW_AHEAD = 33.5 / 16.0, JAW_UP = 12.15 / 16.0, SNOUT = 44.0 / 16.0;

    // --- speeds: blocks a tick at size 1, each its clip's own pace (the card), so tail beats never slide ---------------
    static final double IDLE_BPT = 0.06, PROWL_BPT = 0.12, SWIM_BPT = 0.165, BURST_BPT = 0.30;
    /** The paces goals ask for, as shares of the swim's (idle, prowl, a slow approach, swim, burst). */
    static final double IDLE = IDLE_BPT / SWIM_BPT, PROWL = PROWL_BPT / SWIM_BPT, SLOW = 0.5, SWIM = 1.0, BURST = BURST_BPT / SWIM_BPT;

    // --- water --------------------------------------------------------------------------------------------------------
    /** The shallowest water it swims into (blocks deep: the user's "never follows into water under 3 deep"). */
    public static final int MIN_DEPTH = 3;
    /** The shallowest water the world spawns one in (blocks deep). */
    public static final int SPAWN_DEPTH = 4;
    /** No other shark within this many blocks of a world spawn (as the monitor's 80: each passes through alone). */
    public static final double SPAWN_APART = 80.0;
    /** The water's surface over the top water block's floor (a still source block is 8/9 full). */
    static final double SURFACE_IN_BLOCK = 8.0 / 9.0;

    // --- clips (ticks; each starts once the player has blended into it, CLIP_LAG later) ------------------------------
    static final int CLIP_LAG = 4;
    /** The bite: 0.95 s, the jaws shut on tick 8 (the card). */
    static final int BITE_TICKS = 19 + CLIP_LAG, BITE_HIT = 8 + CLIP_LAG;
    /** Shaking a held catfish: two beats of the 1.2 s clip (my pick). */
    static final int THRASH_TICKS = 48 + CLIP_LAG;
    /** The warning hunch, 1.8 s. */
    static final int THREAT_TICKS = 36 + CLIP_LAG;
    /** The bump, 1.35 s, the snout touching on tick 10 (the card). */
    static final int BUMP_TICKS = 27 + CLIP_LAG, BUMP_HIT = 10 + CLIP_LAG;
    /**
     * The breach, 1.5 s (read from the clip): it breaks the surface about tick 7, its jaws, wide from tick 12, snap shut
     * about tick 21, and it falls back in about tick 25.
     */
    static final int BREACH_TICKS = 30 + CLIP_LAG, BREACH_OUT = 7 + CLIP_LAG, BREACH_SNAP = 21 + CLIP_LAG, BREACH_IN = 25 + CLIP_LAG;
    /** The spy hop, 2.2 s. */
    static final int SPY_TICKS = 44 + CLIP_LAG;
    /** The belly-up roll, 1.6 s, played to its end before the body goes (Minecraft's own is a second). */
    static final int DEATH_TICKS = 32 + CLIP_LAG;

    /** What it is doing that has a clip of its own (synced; the client plays the clip from its start for each new one). */
    public static final byte ACT_NONE = 0, ACT_BITE = 1, ACT_THRASH = 2, ACT_THREAT = 3, ACT_BUMP = 4, ACT_BREACH = 5, ACT_SPY = 6;

    // --- life ----------------------------------------------------------------------------------------------------------
    /** Hungry again this long after a meal (ticks: about a game day, the plan; give or take a fifth, my pick). */
    static final int FED_REST = 24000;
    /** Out of the water: its air lasts this long, then it loses a point of health every so often (ticks; my picks: slow). */
    static final int STRANDED_AIR = 300, STRANDED_HURT_EVERY = 40;
    /** How far a stranded shark finds water to flop toward (blocks; my pick). */
    static final int FLOP_REACH = 4;

    private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(BullSharkEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ACTION_ID = SynchedEntityData.defineId(BullSharkEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PROWLING = SynchedEntityData.defineId(BullSharkEntity.class, EntityDataSerializers.BOOLEAN);
    /** The size of the catfish held in its jaws, or 0 with none. */
    private static final EntityDataAccessor<Float> DATA_HELD = SynchedEntityData.defineId(BullSharkEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** Its own length (blocks). */
    private float length = MODEL_LENGTH;
    /** Ticks until it is hungry again. */
    int hungryIn;
    /** Waits after a hunt given up, a player met, a crocodile warned, a spy hop (ticks). */
    int huntWait, meetWait, crocWait, spyWait;
    /** The player who hit it, and how long it still minds it (ticks). */
    @Nullable
    Player provokedBy;
    int provokedFor;
    /** Ticks into the action under way. */
    private int actionTicks;
    /** Ticks out of the water. */
    private int strandedFor;
    /** For tests: the next strike always lands (1), always misses (-1), or as luck has it (0). */
    public int strikeLuck;
    /** For tests and films: the next hunt at prey near the surface is a breach; the next chance at a spy hop is taken. */
    public boolean breachNext, spyNext;
    /** What its mind is doing (the running goal and its phase), for tests' messages and the log. */
    String mind = "";

    /** Notes a step of its mind (the last few kept, with the tick), for tests' messages. */
    void note(String step) {
        mind = mind + " > " + step + " @" + tickCount;
        if (mind.length() > 400) {
            mind = mind.substring(mind.length() - 400);
        }
    }

    /** A line about it for tests' messages: its mind, action, hunger, air and the last thing that hurt it. */
    public String about() {
        DamageSource last = getLastDamageSource();
        return "mind " + mind + ", action " + action() + ", hungry " + isHungry() + ", in water " + isInWater() + ", stranded " + strandedFor
                + ", last hurt by " + (last == null ? "nothing" : last.getMsgId()) + ", speed " + String.format("%.3f", swimSpeed);
    }

    /** Its swimming this tick, set by its move control and read by its travel: speed ahead and rise (blocks a tick). */
    double swimSpeed, swimRise;
    /** How quickly its speed follows the one asked (a share a tick). */
    double swimEase = 0.12;

    /** Client only: the drawn body's pitch, eased toward where it swims; the action the clip player last started. */
    private float swimPitch, swimPitchO;
    private int animatedActionId = -1;
    /** Client only: the held catfish as drawn (a catfish in no world). */
    @Nullable
    private CatfishEntity heldDrawn;

    /**
     * Its hit boxes along the drawn body ({@link com.antcolonies.animal.HitChain}; my picks measured on the v14 model,
     * blocks at size 1): the snout, the head, the chest ahead of its box; the dorsal fin over it; the rear, the tail stalk
     * and the two lobes of the tail fin behind. Its own box is only the middle of a fish 5.5 blocks long.
     */
    private final com.antcolonies.animal.HitChain chain = new com.antcolonies.animal.HitChain(this,
            new com.antcolonies.animal.HitChain.Link("snout", 2.3, 0.62, 0.8F, 0.6F),
            new com.antcolonies.animal.HitChain.Link("head", 1.5, 0.57, 0.95F, 0.88F),
            new com.antcolonies.animal.HitChain.Link("chest", 0.75, 0.57, 1.05F, 0.92F),
            new com.antcolonies.animal.HitChain.Link("dorsal", 0.4, 1.45, 0.6F, 0.7F),
            new com.antcolonies.animal.HitChain.Link("rear", -0.8, 0.68, 0.75F, 0.62F),
            new com.antcolonies.animal.HitChain.Link("stalk", -1.4, 0.8, 0.5F, 0.38F),
            new com.antcolonies.animal.HitChain.Link("tail_low", -2.0, 0.27, 0.65F, 0.85F),
            new com.antcolonies.animal.HitChain.Link("tail_high", -2.3, 1.07, 0.85F, 0.9F));

    public BullSharkEntity(EntityType<? extends BullSharkEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        moveControl = new SwimControl(this);
        hungryIn = level.random.nextInt(2400);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WaterAnimal.createMobAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new BullSharkGoals.KeepClearGoal(this));
        goalSelector.addGoal(2, new BullSharkGoals.MeetGoal(this));
        goalSelector.addGoal(2, new BullSharkGoals.CrocodileGoal(this));
        goalSelector.addGoal(3, new BullSharkGoals.HuntGoal(this));
        goalSelector.addGoal(4, new BullSharkGoals.SpyHopGoal(this));
        goalSelector.addGoal(5, new BullSharkGoals.PatrolGoal(this));
    }

    /** Routes (a film's "go", nothing of its own): the swimmers' planner, steered by its own move control. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    /** One stiff body: its body and head always face the way it swims (no neck to turn). */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                yBodyRot = getYRot();
                yHeadRot = getYRot();
            }
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTION, ACT_NONE);
        builder.define(DATA_ACTION_ID, 0);
        builder.define(DATA_PROWLING, false);
        builder.define(DATA_HELD, 0.0F);
    }

    // --- size ----------------------------------------------------------------------------------------------------------
    /** The sizes a shark comes in (the user, 2026-11-22: 5 to 7.5 blocks in half-block steps) and how often each comes up. */
    static final float[] LENGTHS = {5.0F, 5.5F, 6.0F, 6.5F, 7.0F, 7.5F};
    static final float[] LENGTH_SHARES = {0.15F, 0.25F, 0.25F, 0.18F, 0.11F, 0.06F};   // my picks: most mid-sized, few giants

    /** A length for a new shark (blocks): one of {@link #LENGTHS}, by its share. */
    static float rollLength(RandomSource random) {
        float r = random.nextFloat();
        for (int i = 0; i < LENGTHS.length - 1; i++) {
            r -= LENGTH_SHARES[i];
            if (r < 0) {
                return LENGTHS[i];
            }
        }
        return LENGTHS[LENGTHS.length - 1];
    }

    /** Its own length (blocks). */
    public float length() {
        return length;
    }

    /** Sets its own length (the nearest half block from 5.0 to 7.5) and draws and boxes it at that size. */
    public void setLength(float blocks) {
        length = Mth.clamp(Math.round(blocks * 2.0F) / 2.0F, SHORTEST, LONGEST);
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(length / MODEL_LENGTH);
        }
    }

    /** Whether it wears the scarred old look (the client reads its size: scale times the model's length). */
    public boolean isScarred() {
        return getScale() * MODEL_LENGTH >= SCARRED_FROM - 0.01F;
    }

    // --- actions and held prey -----------------------------------------------------------------------------------------
    public byte action() {
        return entityData.get(DATA_ACTION);
    }

    /** Ticks into the action under way. */
    int actionTicks() {
        return actionTicks;
    }

    /** Starts an action with a clip of its own (server). */
    void startAction(byte action) {
        entityData.set(DATA_ACTION, action);
        entityData.set(DATA_ACTION_ID, entityData.get(DATA_ACTION_ID) + 1);
        actionTicks = 0;
    }

    void endAction() {
        entityData.set(DATA_ACTION, ACT_NONE);
        actionTicks = 0;
    }

    public boolean isProwling() {
        return entityData.get(DATA_PROWLING);
    }

    void setProwling(boolean prowling) {
        entityData.set(DATA_PROWLING, prowling);
    }

    /** The size of the catfish in its jaws, or 0. */
    public float heldSize() {
        return entityData.get(DATA_HELD);
    }

    public boolean isHolding() {
        return heldSize() > 0;
    }

    void hold(float catfishSize) {
        entityData.set(DATA_HELD, Math.max(0.2F, catfishSize));
    }

    void letGo() {
        entityData.set(DATA_HELD, 0.0F);
    }

    /**
     * The held catfish to draw (client only), or none: a catfish in no world at the caught one's size, out of the water,
     * so its own clip player has it wriggle (its flop) in the jaws.
     */
    @Nullable
    public CatfishEntity heldDrawn() {
        float size = heldSize();
        if (size <= 0) {
            heldDrawn = null;
            return null;
        }
        if (heldDrawn == null) {
            heldDrawn = new CatfishEntity(com.antcolonies.savanna.registry.SavannaEntities.CATFISH.get(), level());
        }
        var scale = heldDrawn.getAttribute(Attributes.SCALE);
        if (scale != null && scale.getBaseValue() != size) {
            scale.setBaseValue(size);
        }
        heldDrawn.tickCount = tickCount;
        return heldDrawn;
    }

    // --- hunger --------------------------------------------------------------------------------------------------------
    public boolean isHungry() {
        return hungryIn <= 0;
    }

    /** For tests and films: hungry now and ready to hunt. */
    public void makeHungry() {
        hungryIn = 0;
        huntWait = 0;
    }

    /** For tests: fed (not hunting) for this long. */
    public void feed(int ticks) {
        hungryIn = ticks;
    }

    /** For tests and films: ready to meet a player, a crocodile, or spy hop now. */
    public void makeCurious() {
        meetWait = 0;
        crocWait = 0;
        spyWait = 0;
    }

    /** A meal: not hungry for about a game day. */
    void fed() {
        hungryIn = FED_REST * 4 / 5 + random.nextInt(FED_REST * 2 / 5);
    }

    /** Whether it is the shark's hunting time: dusk and night (17:30 to 5:00, my pick), on the one shared day clock. */
    boolean huntingHours() {
        long time = com.antcolonies.colony.Colony.dayTimeAt(level(), blockPosition()) % 24000L;
        return time >= 11500 && time <= 23000;
    }

    // --- where its body is ---------------------------------------------------------------------------------------------
    /** Its heading (a unit vector along the ground). */
    Vec3 heading() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** Where its jaws close (the prey bone's place, at its body's facing). */
    public Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAW_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + JAW_UP * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** How far its jaws are from a target's box (0 inside it). */
    double jawDistance(Entity target) {
        Vec3 jaw = jawPoint();
        AABB box = target.getBoundingBox();
        double dx = Math.max(0, Math.max(box.minX - jaw.x, jaw.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - jaw.y, jaw.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - jaw.z, jaw.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** How near its jaws must come to start a bite (blocks; its size). */
    double biteReach() {
        return 0.7 * getScale();
    }

    /** Turns it to face a point at once (its body with it). */
    void face(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 8.0F));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Swims toward a point at a pace (a share of the swim's: {@link #IDLE}, {@link #SWIM}, {@link #BURST}...). */
    void swimTo(double x, double y, double z, double pace) {
        getMoveControl().setWantedPosition(x, y, z, pace);
    }

    /**
     * Swims so its jaws, not its middle, come to a target's middle: it aims its middle the jaws' reach short of the target
     * along the way to it, so it stops with its jaws there instead of driving its jaws on past (a shark 5 to 7 blocks long
     * has its jaws two blocks ahead of its middle). A target nearer than that (beside its head) it turns to, slowly.
     */
    void swimAtWithJaws(Entity target, double pace) {
        Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
        double dx = mid.x - getX(), dz = mid.z - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz), reach = JAW_AHEAD * getScale();
        boolean beside = flat <= reach + 0.2;
        double k = beside ? 1.0 : (flat - reach) / flat;
        swimTo(getX() + dx * k, mid.y - JAW_UP * getScale(), getZ() + dz * k, beside ? Math.min(pace, IDLE) : pace);
    }

    // --- the water round it --------------------------------------------------------------------------------------------
    /** A column of water: its lowest and highest water blocks. */
    record Column(int bed, int top) {
        int depth() {
            return top - bed + 1;
        }

        /** The surface's height. */
        double surface() {
            return top + SURFACE_IN_BLOCK;
        }
    }

    /** The column of water at (x, z) through the water nearest the height y (looked for 3 blocks either way), or null. */
    @Nullable
    static Column column(LevelReader level, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        int start = p.getY();
        boolean found = false;
        for (int d = 0; d <= 3 && !found; d++) {
            for (int s : d == 0 ? new int[] {0} : new int[] {d, -d}) {
                p.setY(start + s);
                if (level.getFluidState(p).is(FluidTags.WATER)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            return null;
        }
        int at = p.getY(), top = at, bed = at;
        while (top - at < 24 && level.getFluidState(p.setY(top + 1)).is(FluidTags.WATER)) {
            top++;
        }
        while (at - bed < 24 && level.getFluidState(p.setY(bed - 1)).is(FluidTags.WATER)) {
            bed--;
        }
        return new Column(bed, top);
    }

    /** How deep the water is at (x, z) round the height y (0 with none). */
    public static int depthAt(LevelReader level, double x, double y, double z) {
        Column c = column(level, x, y, z);
        return c == null ? 0 : c.depth();
    }

    /** Where it swims in a column: its body in the middle of the water, its back at least half a block under. */
    double cruiseY(Column c) {
        double body = 1.5 * getScale();
        double mid = c.bed() + (c.depth() - body) * 0.5;
        return Math.max(c.bed() + 0.05, Math.min(mid, c.surface() - body - 0.5));
    }

    /** Where its feet are when it prowls with the fin out at this column: the water's surface at its waterline. */
    double prowlY(Column c) {
        return Math.max(c.bed() + 0.05, c.surface() - WATERLINE * getScale());
    }

    /** The column under it now, or null (out of the water). */
    @Nullable
    Column columnHere() {
        return column(level(), getX(), getY() + 0.5 * getScale(), getZ());
    }

    /**
     * Whether the water ahead of its snout is too shallow to go on into (the one rule for "never into water under
     * {@link #MIN_DEPTH} deep"): shallower than that and shallower than where it is, so a shark already in shallow water
     * (an egg's) may still swim out toward deeper.
     */
    boolean shallowAhead() {
        Vec3 h = heading();
        double ahead = SNOUT * getScale() + 0.8;
        double y = getY() + 0.5 * getScale();
        int there = depthAt(level(), getX() + h.x * ahead, y, getZ() + h.z * ahead);
        return there < MIN_DEPTH && there < depthAt(level(), getX(), y, getZ());
    }

    /**
     * Whether it can swim straight from here to a point through water at least {@link #MIN_DEPTH} deep the whole way, its
     * swimming height inside the water at every block (no bank, bar or ledge between). For a place its middle stops at
     * ({@code middleStops}: a patrol spot, a lookout) the line goes on past the point as far as its snout reaches, as it
     * stops there facing the way it came and its head must be in deep water too, not in the bank (the course found the
     * head inside a pool's wall, 2026-11-22); for prey or a player its jaws stop at the point, so the line ends there.
     */
    boolean deepLine(Vec3 to, boolean middleStops) {
        Vec3 from = position();
        double len = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        double reach = len < 0.01 || !middleStops ? 1 : (len + SNOUT * getScale()) / len;
        int steps = Math.max(1, (int) Math.ceil(len * Math.max(1, reach)));
        for (int i = 1; i <= steps; i++) {
            double k = Math.max(1, reach) * i / steps, ky = Math.min(1, k);
            double x = from.x + (to.x - from.x) * k, y = from.y + (to.y - from.y) * ky, z = from.z + (to.z - from.z) * k;
            Column c = column(level(), x, y + 0.5 * getScale(), z);
            if (c == null || c.depth() < MIN_DEPTH || y + 0.4 < c.bed() || y > c.top() + 0.5) {
                return false;
            }
        }
        return true;
    }

    // --- ticking -------------------------------------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        chain.place();
        if (level().isClientSide) {
            swimPitchO = swimPitch;
            double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float want = isInWater() && action() == ACT_NONE && flat + Math.abs(dy) > 0.01
                    ? (float) Mth.clamp(Math.toDegrees(Math.atan2(dy, Math.max(flat, 0.05))), -20, 20) : 0;
            swimPitch += (want - swimPitch) * 0.1F;
            return;
        }
        if (hungryIn > 0) {
            hungryIn--;
        }
        huntWait = Math.max(0, huntWait - 1);
        meetWait = Math.max(0, meetWait - 1);
        crocWait = Math.max(0, crocWait - 1);
        spyWait = Math.max(0, spyWait - 1);
        if (provokedFor > 0 && --provokedFor == 0) {
            provokedBy = null;
        }
        if (action() != ACT_NONE) {
            actionTicks++;
        }
        if (!isInWater() && isAlive() && !isNoAi()) {     // (a still one, a film's, lies still)
            stranded();
        }
    }

    /**
     * Out of the water (drained, washed up): it thrashes ({@code beached_flop}), and each flop on the ground throws it a
     * little toward water within {@link #FLOP_REACH} blocks, as a stranded real shark can sometimes thrash its way back.
     */
    private void stranded() {
        if (action() != ACT_NONE) {
            endAction();
        }
        setProwling(false);
        if (!onGround() || tickCount % 18 != 0) {
            return;
        }
        BlockPos feet = blockPosition();
        BlockPos water = null;
        double best = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-FLOP_REACH, -1, -FLOP_REACH), feet.offset(FLOP_REACH, 0, FLOP_REACH))) {
            double fx = p.getX() + 0.5 - getX(), fz = p.getZ() + 0.5 - getZ();
            double d = fx * fx + fz * fz;
            // water at least a block off (not the edge block it already hangs over, which would leave it stuck there)
            if (d >= 1.0 && d < best && level().getFluidState(p).is(FluidTags.WATER)) {
                best = d;
                water = p.immutable();
            }
        }
        Vec3 push = water == null ? Vec3.ZERO : new Vec3(water.getX() + 0.5 - getX(), 0, water.getZ() + 0.5 - getZ());
        push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.18);
        setDeltaMovement(push.x, 0.28, push.z);
        hasImpulse = true;
        playSound(SoundEvents.GUARDIAN_FLOP, 1.0F, 0.6F + random.nextFloat() * 0.15F);
    }

    /**
     * Out of the water it slowly suffocates (my picks: {@link #STRANDED_AIR} ticks, then a point of health every
     * {@link #STRANDED_HURT_EVERY}), counted on its own clock: Minecraft's air is kept full, since NeoForge's breathing
     * hook drowns anything whose air is under -20, every tick, whatever the animal's own count says (the first test: 28
     * points lost in 30 s instead of 7).
     */
    @Override
    protected void handleAirSupply(int airSupply) {
        setAirSupply(getMaxAirSupply());
        if (isAlive() && !isInWaterOrBubble()) {
            strandedFor++;
            if (strandedFor > STRANDED_AIR && (strandedFor - STRANDED_AIR) % STRANDED_HURT_EVERY == 0) {
                hurt(damageSources().dryOut(), 1.0F);
            }
        } else {
            strandedFor = 0;
        }
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;
    }

    /** In water it swims where it points at the speed its move control asks (weightless); on land it falls and flops. */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && isInWater()) {
            if (isDeadOrDying()) {
                swimSpeed = 0;
                swimRise = 0.02;        // dead, it floats slowly up
            }
            Vec3 h = heading();
            Vec3 want = new Vec3(h.x * swimSpeed, swimRise, h.z * swimSpeed);
            Vec3 v = getDeltaMovement();
            v = v.add(want.subtract(v).scale(swimEase));
            move(MoverType.SELF, v);
            setDeltaMovement(v);
        } else {
            super.travel(travelVector);
        }
    }

    /** The belly-up roll is played to its end (1.6 s) before the body goes. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(Entity.RemovalReason.KILLED);
        }
    }

    /**
     * Hit by a player (not in creative, not a spectator): it minds that player a while ({@link BullSharkGoals.MeetGoal}
     * warns, then bites, if the player is in deep water); prey in its jaws is not let go for that.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            provokedBy = player;
            provokedFor = 600;
        }
        return hurt;
    }

    // --- spawning ------------------------------------------------------------------------------------------------------
    /**
     * Where the world may spawn one: in the river animals' height band, in water at least {@link #SPAWN_DEPTH} deep (at
     * any height of its column: Minecraft's spawner keeps one random height for a try; set into the middle of the water
     * after, {@link #finalizeSpawn}), and (the world's spawns only) no other shark within {@link #SPAWN_APART} blocks.
     */
    public static boolean canSpawn(EntityType<BullSharkEntity> type, LevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        int sea = level.getSeaLevel();
        if (pos.getY() < sea - 16 || pos.getY() > sea + 6 || !level.getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }
        Column c = column(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (c == null || c.depth() < SPAWN_DEPTH) {
            return false;
        }
        return reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION
                || level.getEntitiesOfClass(BullSharkEntity.class, new AABB(pos).inflate(SPAWN_APART)).isEmpty();
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        setLength(rollLength(level.getRandom()));
        setYRot(level.getRandom().nextFloat() * 360.0F);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            Column c = column(level, getX(), getY(), getZ());
            if (c != null) {
                setPos(getX(), cruiseY(c), getZ());      // into the middle of the water
            }
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Length", length);
        tag.putInt("HungryIn", hungryIn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Length")) {
            setLength(tag.getFloat("Length"));
        }
        if (tag.contains("HungryIn")) {
            hungryIn = tag.getInt("HungryIn");
        }
    }

    /** Kept near a player like the other river animals ({@link TilapiaEntity#KEEP_NEAR}). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > TilapiaEntity.KEEP_NEAR * TilapiaEntity.KEEP_NEAR;
    }

    /** Always swims on (BUGS 216: Minecraft stops a mob's wandering with only a spectator near). */
    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Too big to shove. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = 96.0 * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 5 + random.nextInt(4);
    }

    // --- sounds (a shark is silent; hurt and dying, a deep fish's thrash) ----------------------------------------------
    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SALMON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SALMON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.DOLPHIN_SWIM;
    }

    // --- its swimming ----------------------------------------------------------------------------------------------
    /**
     * Its legs, in the water (the mind says only "swim to this point at this pace", {@link #swimTo}): it turns toward the
     * point at most a few degrees a tick (one stiff body 5 to 7 blocks long), slows while the point is far off its nose
     * so it turns rather than circling it, slows as it arrives (braking faster than it speeds up), rises or sinks toward
     * the point's height, and stops short of water too shallow ({@link #shallowAhead}). Its speed is the clip's own pace times its size, so the tail beats never slide.
     */
    static class SwimControl extends MoveControl {
        private final BullSharkEntity shark;

        SwimControl(BullSharkEntity shark) {
            super(shark);
            this.shark = shark;
        }

        @Override
        public void tick() {
            if (!shark.isInWater() || operation != Operation.MOVE_TO) {
                shark.swimSpeed = 0;
                shark.swimRise = 0;
                return;
            }
            double dx = wantedX - shark.getX(), dy = wantedY - shark.getY(), dz = wantedZ - shark.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            shark.swimRise = Mth.clamp(dy * 0.08, -0.08, 0.08);
            if (flat < 0.4) {
                shark.swimSpeed = 0;
                if (Math.abs(dy) < 0.3) {
                    operation = Operation.WAIT;
                }
                return;
            }
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float off = Math.abs(Mth.wrapDegrees(want - shark.getYRot()));
            boolean fast = speedModifier > SWIM + 0.01;
            shark.setYRot(Mth.approachDegrees(shark.getYRot(), want, fast ? 8.0F : 5.0F));
            shark.yBodyRot = shark.getYRot();
            shark.yHeadRot = shark.getYRot();
            double asked = speedModifier * SWIM_BPT * shark.getScale();
            // far off its nose it slows to its idle glide while it turns (never stopping: a shark keeps swimming)
            double facing = off >= 90 ? 0 : Math.cos(off * Mth.DEG_TO_RAD);
            double speed = Math.max(Math.min(asked, IDLE_BPT * shark.getScale()), asked * facing);
            // arriving, it slows (a bite that comes at a burst ends with the jaws at the prey, not two blocks past it)
            speed = Math.min(speed, flat * 0.25);
            if (shark.shallowAhead()) {
                speed = 0;
            }
            Vec3 v = shark.getDeltaMovement();
            boolean braking = speed < Math.sqrt(v.x * v.x + v.z * v.z);
            shark.swimSpeed = speed;
            shark.swimEase = fast || braking ? 0.25 : 0.12;
        }

        /** Whether it stopped this tick for shallow water ahead (its goals pick another way). */
        boolean blocked() {
            return operation == Operation.MOVE_TO && shark.swimSpeed == 0 && shark.shallowAhead();
        }
    }

    /** Whether its swimming is held up by shallow water ahead. */
    boolean swimBlocked() {
        return moveControl instanceof SwimControl c && c.blocked();
    }

    // --- animations ------------------------------------------------------------------------------------------------
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE_CLIP = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM_CLIP = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation BURST_CLIP = RawAnimation.begin().thenLoop("burst");
    private static final RawAnimation PROWL_CLIP = RawAnimation.begin().thenLoop("surface_prowl");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation THRASH_CLIP = RawAnimation.begin().thenLoop("thrash");
    private static final RawAnimation FLOP_CLIP = RawAnimation.begin().thenLoop("beached_flop");
    private static final RawAnimation DEATH_CLIP = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation THREAT_CLIP = RawAnimation.begin().thenPlayAndHold("threat_display");
    private static final RawAnimation BUMP_CLIP = RawAnimation.begin().thenPlayAndHold("bump_investigate");
    private static final RawAnimation BREACH_CLIP = RawAnimation.begin().thenPlayAndHold("breach");
    private static final RawAnimation SPY_CLIP = RawAnimation.begin().thenPlayAndHold("spy_hop");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SteadyController<>(this, "body", CLIP_LAG, this::animate));
    }

    private PlayState animate(AnimationState<BullSharkEntity> state) {
        AnimationController<BullSharkEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        Component name = getCustomName();
        if (name != null && name.getString().startsWith("film:")) {     // the film camera's still look: that clip only
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(name.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH_CLIP);
        }
        if (!isInWater()) {
            return state.setAndContinue(FLOP_CLIP);
        }
        int id = entityData.get(DATA_ACTION_ID);
        if (id != animatedActionId) {
            animatedActionId = id;
            if (action() != ACT_NONE) {
                player.forceAnimationReset();   // a new action: its clip from the start, even the same clip again
            }
        }
        switch (action()) {
            case ACT_BITE -> {
                return state.setAndContinue(BITE_CLIP);
            }
            case ACT_THRASH -> {
                return state.setAndContinue(THRASH_CLIP);
            }
            case ACT_THREAT -> {
                return state.setAndContinue(THREAT_CLIP);
            }
            case ACT_BUMP -> {
                return state.setAndContinue(BUMP_CLIP);
            }
            case ACT_BREACH -> {
                return state.setAndContinue(BREACH_CLIP);
            }
            case ACT_SPY -> {
                return state.setAndContinue(SPY_CLIP);
            }
            default -> {
            }
        }
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz) / Math.max(0.3, getScale());
        if (isProwling()) {
            player.setAnimationSpeed(Mth.clamp(speed / PROWL_BPT, 0.6, 1.6));
            return state.setAndContinue(PROWL_CLIP);
        }
        if (speed < (IDLE_BPT + SWIM_BPT) * 0.5) {
            player.setAnimationSpeed(Mth.clamp(speed / IDLE_BPT, 0.7, 1.6));     // never still: it always glides
            return state.setAndContinue(IDLE_CLIP);
        }
        if (speed < (SWIM_BPT + BURST_BPT) * 0.5) {
            player.setAnimationSpeed(Mth.clamp(speed / SWIM_BPT, 0.6, 1.5));
            return state.setAndContinue(SWIM_CLIP);
        }
        player.setAnimationSpeed(Mth.clamp(speed / BURST_BPT, 0.7, 1.4));
        return state.setAndContinue(BURST_CLIP);
    }

    /** The drawn body's pitch this frame (degrees, nose up positive). */
    public float swimPitch(float partialTick) {
        return Mth.lerp(partialTick, swimPitchO, swimPitch);
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

    /** For tests and the goals: the server level it lives in, or null on the client. */
    @Nullable
    ServerLevel server() {
        return level() instanceof ServerLevel s ? s : null;
    }

    /** Whether this is something it hunts: a wild river fish in the water (never one from a bucket or named). */
    static boolean isPrey(LivingEntity e) {
        return e.isAlive() && e.isInWater() && !e.isPassenger() && CrocodileEntity.isFish(e);
    }
}
