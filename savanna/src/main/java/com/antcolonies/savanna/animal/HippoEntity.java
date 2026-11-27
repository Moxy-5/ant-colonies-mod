package com.antcolonies.savanna.animal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLife;
import com.antcolonies.animal.Attention;
import com.antcolonies.animal.HasLife;
import com.antcolonies.entity.body.BlockMoves;
import com.antcolonies.entity.body.SteadyController;
import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
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
 * The hippopotamus (M9 savanna life, into the game 2026-11-21, the user: "put the hippo in game"; model, clips and moves
 * {@code tools/hippo_models.py game}, v8 approved by the user 2026-11-21). An animal: generalized and fun first, every
 * number my pick, real habits used where they make the river look right ({@code docs/DESIGN.md}, "River zones"):
 * <ul>
 * <li>Pods live in the pools of the near and mid-inland rivers, a lone bull or a bull with one or two cows, placed with new
 * land and kept for good ({@code worldgen/HippoPodFeature}, the user 2026-11-21; never spawned by the world's spawner,
 * BUGS 271). The pod shares the bull's pool and packs into it. By day each rests in its pool, afloat with only its eyes, ears
 * and back out where the water is deep, standing on the bed where it is shallow, and now and then wanders the pool,
 * bounding along the bed under the water ({@code underwater_walk}); it yawns its great territorial yawn now and then,
 * and flicks its ears coming up.</li>
 * <li>At dusk it walks out of the water and grazes the land near its pool all night ({@code graze}), eating the grass
 * plants in front of it, flicking its dung about now and then; at dawn it walks back to its pool.</li>
 * <li>The most dangerous animal in the river: a survival player coming near gets a yawn as a warning; coming nearer,
 * or staying, or hitting it, it charges and bites hard (5 hearts). Grown crocodiles are put up with, as real pods let
 * them bask close by: only a bull minds one coming right up to him, yawning first, then a short bluff charge that ends
 * once it is off, one bite at most (BUGS 272); crocodiles swim clear of a hippo.</li>
 * <li>Climbing up or down a block and walking out of the shallows onto a bank are moves of its own (the Animal Maker
 * Engine, made once with their clips, played by the shared {@link BlockMoves} as the monitor's): it stops short of the
 * step, rises and sets its forefeet on top before its body touches it, then heaves its hind legs up.</li>
 * <li>Its night's grazing keeps to the pod's two lawns ({@link #lawns}, from its pool, so pod mates share them): it
 * leaves the water at the same landing every night (the bank spot nearest the lawn) and walks the same way to the lawn,
 * wearing a path into the grass (grass to path under its feet, {@link #PATH_WEAR}); on the lawn it crops tall grass short
 * and short grass to the ground, stepping on to the next plant as each bite runs out (2026-11-23).</li>
 * <li>In the cool of the morning and late afternoon it hauls out onto the bank beside its pool and lies there, pod mates
 * side by side ({@link BankRestGoal}); it gets up and back into the water as the day heats up, or when a player comes.</li>
 * <li>Now and then, by day or before going out at night, it wades to a hippo grass mat near its pool and eats it, dunking
 * its head ({@link NibbleGoal}); the mats grow back from their clump.</li>
 * <li>In its pool by day, afloat or standing, it now and then flicks its dung into the water ({@link #dungInWater}): a brown cloud
 * behind it that the barbs and tilapia near crowd round and pick at a while (the river food web's step 6, 2026-11-23;
 * real: hippo dung feeds African rivers, and fish gather where hippos are).</li>
 * </ul>
 * Not yet: calves and breeding.
 */
public class HippoEntity extends PathfinderMob implements GeoEntity, HasLife {
    /** What it is doing, for the clips. */
    public static final byte CALM = 0, YAWN = 1, BITE = 2, GRAZE = 3, EAR_FLICK = 4, DUNG = 5, REST = 6, RISE = 7;
    private static final EntityDataAccessor<Byte> DATA_DOING = SynchedEntityData.defineId(HippoEntity.class, EntityDataSerializers.BYTE);
    /** Its move now ({@link BlockMoves.Play}), empty when not on one. */
    private static final EntityDataAccessor<CompoundTag> DATA_CLIMB = SynchedEntityData.defineId(HippoEntity.class,
            EntityDataSerializers.COMPOUND_TAG);
    /** Whether it charges now (the charge clip, drawing only). */
    private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(HippoEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_LIFE = AnimalLife.dataFor(HippoEntity.class);

    /** The model is made at a bull's size (5 blocks long nose to tail, 2 tall at the back; 16 px a block); drawn at this share. */
    public static final float MODEL_SCALE = 1.0F;
    /** How far its jaws reach ahead of its middle (blocks, at size 1): the snout's front is 2.5 blocks out. */
    static final double JAWS_AHEAD = 2.3;
    /** Afloat, its feet hang this far under the surface (blocks, at size 1): the float clip's waterline (21.5 px). */
    static final double AFLOAT = 21.5 / 16.0;
    /** The walk clip is made for this speed (blocks a tick, at size 1: 0.434 blocks a second), the charge for this (1.395). */
    static final double WALK_CLIP_SPEED = 0.434 / 20.0, CHARGE_CLIP_SPEED = 1.395 / 20.0;
    /** Faster than this (blocks a tick, at size 1) its run is drawn with the charge clip. */
    static final double CHARGE_DRAWN = 0.05;
    /** Its strolling and charging speeds (the move control's share of its speed: about 0.5 and 4 blocks a second). */
    static final double STROLL = 0.8, CHARGE_SPEED = 6.0;
    /** The clips' lengths and moments (ticks): the bite's crushing hit, the yawn, the ear flick, the dung flick, a graze loop. */
    static final int BITE_TICKS = 15, BITE_HIT = 8, YAWN_TICKS = 52, EAR_TICKS = 22, DUNG_TICKS = 32, GRAZE_LOOP = 48, RISE_TICKS = 18;
    /**
     * Its grazing lawns (2026-11-23, my picks): this many per pool, at least this far apart, grazed within this reach of
     * their middle (and this much farther once the middle is cropped bare: the lawn grows at its edge).
     */
    static final int LAWNS = 2, LAWN_APART = 10, LAWN_REACH = 5, LAWN_GROW = 4;
    /** Its landing (where it leaves and comes back to the water) lies this near its pool (blocks). */
    static final int LANDING_REACH = 12;
    /** The chance each block of grass a foot comes onto, walking on land off its lawns, wears to path (a path in about 3 nights). */
    static final float PATH_WEAR = 1 / 4f;
    /** It rests on a bank this near its pool (blocks), lying 1 to 2.5 minutes, then not again for 2 to 4 minutes. */
    static final int BANK_REACH = 12, REST_SHORTEST = 1200, REST_LONGEST = 3000, REST_AGAIN = 2400;
    /**
     * Its dung in the water (the river food web's step 6, 2026-11-23, my picks): in its pool by day, afloat or standing
     * (afloat since the user's "yes, add floating": the flick's tail spins at the waterline, eyes and ears kept out),
     * one rest in this many ends in a dung flick (about one every 2 to 3 minutes); the tail spreads it from tick {@link #DUNG_SPREAD_FROM} to {@link #DUNG_SPREAD_TO}
     * of the clip (the clip's propeller), this far behind its middle (blocks, at size 1: just past its rump); the cloud
     * hangs in the water this long (ticks), and barbs and tilapia this near it come to pick at it ({@code DungPickGoal}).
     */
    static final int DUNG_IN_POOL = 4, DUNG_SPREAD_FROM = 4, DUNG_SPREAD_TO = 24, DUNG_CLOUD = 240;
    static final double DUNG_BEHIND = 2.7, DUNG_FISH_REACH = 8.0;
    /** The dung cloud's colour (a murky olive brown, my pick). */
    static final org.joml.Vector3f DUNG_COLOUR = new org.joml.Vector3f(0.36F, 0.30F, 0.14F);
    /** The cool hours it rests on the bank (the day's ticks: 6:30 to 10:00 and 14:00 to 17:30). */
    static final int MORNING_FROM = 500, MORNING_TO = 4000, AFTERNOON_FROM = 8000, AFTERNOON_TO = 11500;
    /** Hippo grass mats this near its pool are eaten (blocks); after a meal it waits 2 to 5 minutes. */
    static final int NIBBLE_REACH = 8, NIBBLE_AGAIN = 2400;
    /** How hard it bites (5 hearts) and how far its bite reaches past its snout (blocks). */
    static final float BITE_DAMAGE = 10.0F;
    static final double BITE_REACH = 0.9;
    /** A survival player this near gets the warning yawn, this near (or staying) the charge (blocks from its middle). */
    static final double WARN_REACH = 9.0, CHARGE_REACH = 5.5;
    /** A grown crocodile this near a bull (blocks from his middle: about 3 from his snout) gets his yawn, and charged if it stays; he gives up once it is this far. */
    static final double CROC_REACH = 5.5, CROC_GIVE_UP = 8.0;
    /** After biting a crocodile a bull lets crocodiles be this long (ticks: a minute), whether or not it got away (BUGS 273). */
    static final int CROC_CALM = 1200;
    /** A charge gives up when its target is this far, or after this many ticks; then it waits this long before another. */
    static final double CHARGE_GIVE_UP = 16.0;
    static final int CHARGE_TICKS = 240, CHARGE_REST = 200, WARN_AGAIN = 200;
    /** How far from its pool it grazes at night (blocks), and how near it wanders in its pool by day (a tight pod, open water round it). */
    static final int GRAZE_NEAR = 6, GRAZE_FAR = 24, POOL_WANDER = 5;
    /** Water at least this deep (blocks) is a pool it may rest in. */
    public static final int POOL_DEPTH = 2;
    /** Drawn this far off (blocks): pods are few and big, seen across the river. */
    static final double DRAW_DISTANCE = 128.0;

    /**
     * Its moves made once with their clips (the Animal Maker Engine): up and down a block, and out of the shallows onto a
     * bank a block up, from its move file ({@code assets/antcolonies/block_moves/hippo.json}, written by
     * {@code python tools/hippo_models.py game}). All three may start or end in shallow water ({@link BlockMoves.Move#wades}):
     * down off a bank into the shallows, out of them onto a bank.
     */
    public static final BlockMoves MOVES = BlockMoves.of(HippoEntity.class, SavannaMod.MODID, "hippo");
    public static final int UP = MOVES.index("up"), DOWN = MOVES.index("down"), OUT = MOVES.index("out");
    /** How far out from its middle line its feet stand (model pixels, the leg sockets) and its body's half width. */
    static final double FEET_OUT = 9.6, BODY_HALF = 15.0;

    /** The pool it lives in (by day it rests there, at night it grazes round it), or null until it finds one. */
    @Nullable
    BlockPos pool;
    /** Who it is angry at (hurt it, or ignored its warning), and until when (its tick). */
    @Nullable
    LivingEntity angryAt;
    int angryUntil;
    /** Ticks before it may charge again, or mind a crocodile again. */
    int chargeWait, crocWait;
    /**
     * Whom it has warned with a yawn, and until when (its tick) that one is charged without another yawn (review R9-13:
     * one timer for everybody, so a second visitor was bitten with no warning). Not saved.
     */
    final java.util.Map<java.util.UUID, Integer> warned = new java.util.HashMap<>();
    /** Its tick before which it does not try for home again (no way found), and does not search for lawns again (none found). Review R9-16. */
    int homeNotBefore, lawnsNotBefore;
    /** Ticks before it may rest on the bank again, or go to eat hippo grass again. */
    int restWait, nibbleWait;
    /** Its pod's grazing lawns (their middles, from its pool; saved), the one it grazes tonight, and where it left the water for it. */
    final List<BlockPos> lawns = new java.util.ArrayList<>();
    @Nullable
    BlockPos lawn, landing;
    /** How many lawns it gave up as out of its reach (rolled into the lawns' dice, so the next ones differ). */
    int lawnsDropped;
    /** The bank spot it is going to rest at, or rests at (null: none). */
    @Nullable
    BlockPos restAt;
    /** The blocks under its two foot lines last tick (a path wears where a foot comes onto a new block). */
    private final BlockPos[] lastFoot = new BlockPos[2];
    /** The game tick its doing started (for its clips' lengths and moments). */
    int doingSince;
    /** The last tick it moved, and how far a tick, eased (for the clips). */
    private int movedTick = -100;
    private double stride;
    static final int WALK_HOLD = 3;
    /** It was afloat last tick (coming up from the bed it flicks its ears). */
    private boolean wasAfloat;
    /** Its dung cloud in the water (the river food web's step 6), until when (game time); null: none. */
    @Nullable
    Vec3 dungAt;
    long dungUntil;
    @Nullable
    private BlockMoves.Play climb;
    /** Drawing only: the time its move is drawn at, for its clip and its place. */
    private final com.antcolonies.entity.body.MoveClock moveClock = new com.antcolonies.entity.body.MoveClock();
    @Nullable
    private CompoundTag climbRead;
    /** The body's clip player (the move's clock is read from it by the model). */
    @Nullable
    public SteadyController<HippoEntity> bodyClips;
    /** Drawing only (HippoModel): whether it was drawn on a move, how far its drawing is still shifted from its place after a hand over (blocks), where it was drawn and when. */
    public boolean drawnClimbing;
    public double shiftX, shiftY, shiftZ, drawnX, drawnY, drawnZ;
    public long drawnNanos;
    /**
     * Where its eyes go (2026-11-21, the user's ask; drawn only, {@link HippoModel}): a player within 16 blocks (6
     * crouching), a crocodile within 14 even lying still (the danger, watched first), or any other animal stirring
     * within 10; never another hippo. Hippos graze, so there is no prey to watch. My picks.
     */
    public final Attention attention = new Attention(16.0, HippoEntity::catchesEye);

    private static double catchesEye(Entity seen, double distance, boolean moving) {
        if (seen instanceof HippoEntity) {
            return 0;
        }
        if (seen instanceof Player player) {
            return Attention.playerInReach(player, distance, 16.0, 6.0) ? 0.5 : 0;
        }
        if (seen instanceof CrocodileEntity) {
            return distance < 14.0 ? 0.4 : 0;
        }
        return moving && distance < 10.0 ? 1.0 : 0;
    }
    /** Where its head is turned (degrees from its heading) and when last drawn: drawing only (HippoModel). */
    public double headAim;
    public long lookNanos;
    private final AnimalLife life;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * Its hit boxes along the drawn body ({@link com.antcolonies.animal.HitChain}, 2026-11-22; my picks measured on its
     * model): the head with its wide muzzle (1.35 to 2.5 ahead), the front of the body and the rump: its walking box
     * is only the middle of a body 5 blocks long, so a swing or an arrow at what you see lands.
     */
    private final com.antcolonies.animal.HitChain chain = new com.antcolonies.animal.HitChain(this,
            new com.antcolonies.animal.HitChain.Link("front", 1.3, 0.2, 1.6F, 1.5F),
            new com.antcolonies.animal.HitChain.Link("head", 1.95, 0.7, 1.2F, 1.1F),
            new com.antcolonies.animal.HitChain.Link("rump", -1.5, 0.15, 1.7F, 1.6F));

    public HippoEntity(EntityType<? extends HippoEntity> type, Level level) {
        super(type, level);
        setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1);   // its hit boxes take the ids right after its own
        life = new AnimalLife(this, DATA_LIFE);
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
        getNavigation().setCanFloat(false);           // heavy: it walks the bed, never swims the surface on a route
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, BITE_DAMAGE).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.ARMOR, 6.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new TerritoryGoal(this));
        goalSelector.addGoal(1, new BankRestGoal(this));
        goalSelector.addGoal(2, new HomeGoal(this));
        goalSelector.addGoal(3, new NibbleGoal(this));
        goalSelector.addGoal(4, new GrazeGoal(this));
        goalSelector.addGoal(5, new PoolGoal(this));
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
        builder.define(DATA_CLIMB, new CompoundTag());
        builder.define(DATA_CHARGING, false);
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

    /** Ticks since its doing started. */
    int doingFor() {
        return tickCount - doingSince;
    }

    public boolean charging() {
        return entityData.get(DATA_CHARGING);
    }

    void setCharging(boolean on) {
        entityData.set(DATA_CHARGING, on);
    }

    public float ownSize() {
        return life.ownSize();
    }

    /** Where its jaws close: the snout's front, a little over the ground. */
    Vec3 jawPoint() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double ahead = JAWS_AHEAD * getScale();
        return new Vec3(getX() - Mth.sin(yaw) * ahead, getY() + 0.7 * getScale(), getZ() + Mth.cos(yaw) * ahead);
    }

    /** Whether an entity is at its jaws (its box within reach of the snout). */
    boolean atJaws(Entity e) {
        return e.getBoundingBox().inflate(BITE_REACH * getScale()).contains(jawPoint())
                || e.distanceToSqr(jawPoint()) < Mth.square(BITE_REACH * getScale() + e.getBbWidth() * 0.5);
    }

    /** Faces a place (a little at a time). */
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

    /** Afloat: in water and off the bed. */
    public boolean afloat() {
        return isInWater() && !onGround();
    }

    /** The surface of the water over its feet (blocks), or NaN if it is not in water. */
    double surface() {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY() + 0.2, getZ()).mutable();
        if (!level().getFluidState(p).is(FluidTags.WATER)) {
            return Double.NaN;
        }
        while (level().getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < getY() + 16) {
            p.move(0, 1, 0);
        }
        return p.getY() + level().getFluidState(p).getHeight(level(), p);
    }

    // --- moves (the Animal Maker Engine, as the monitor's) ----------------------------------------------------------------

    /** Its move now, or null (both sides). */
    @Nullable
    public BlockMoves.Play climb() {
        CompoundTag tag = entityData.get(DATA_CLIMB);
        if (tag != climbRead) {
            climbRead = tag;
            climb = BlockMoves.Play.of(tag);
        }
        return climb;
    }

    /** Whether a move is on and past turning square on the spot (its clip plays). */
    public boolean moveClipOn() {
        BlockMoves.Play c = climb();
        return c != null && !c.turning(level().getGameTime());
    }

    /**
     * Drawing only: how far into its move's clip it is drawn now (ticks of the clip, 0 while it turns or eases in), or
     * below 0 when not on a move. From the move's own time, as its clip ({@link BlockMoves#drawnTicks}).
     */
    public double climbClipTicks(float partialTick) {
        BlockMoves.Play c = climb();
        return c == null ? -1.0 : MOVES.drawnTicks(c, moveClock, level().getGameTime(), partialTick);
    }

    /** The body clip player's clock: a move's clip plays at the move's own time since its start ({@link SteadyController.Clock}). */
    private double climbClock(String clip, double tick) {
        BlockMoves.Play c = climb();
        if (c == null || !clip.equals(MOVES.clip(c))) {
            return -1.0;
        }
        return MOVES.drawnTicks(c, moveClock, level().getGameTime(), tick);
    }

    /**
     * Starts a move when its route goes a block up or down within its next few squares and it has come to the move's
     * start: the step's face or the edge that far ahead of its middle (its box is two blocks wide, so the face is read
     * from the ground ahead of it, not from the route's squares), heading near enough at it, with room. Up out of the
     * shallows onto a dry bank is the out move; up a step under the water, or a bank too high, is left to Minecraft's
     * own step. Calm only (a charge runs straight at its target, the old way), and not just hurt.
     */
    private boolean startMove() {
        if (isPassenger() || isDeadOrDying() || hurtTime > 0 || doing() != CALM || charging() || !onGround()) {
            return false;
        }
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        int last = Math.min(path.getNodeCount() - 2, path.getNextNodeIndex() + 4);
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i <= last; i++) {
            Node a = path.getNode(i), b = path.getNode(i + 1);
            if (b.y == a.y) {
                continue;
            }
            int dx = Integer.signum(b.x - a.x), dz = Integer.signum(b.z - a.z);
            if (Math.abs(b.y - a.y) != 1 || Math.abs(dx) + Math.abs(dz) != 1 || a.y != floor) {
                return false;
            }
            boolean up = b.y > a.y;
            boolean alongX = dx != 0;
            int sign = dx + dz;
            double face = faceAhead(alongX, sign, floor, up);
            if (Double.isNaN(face)) {
                return false;
            }
            int move = up ? (isInWater() ? OUT : UP) : DOWN;
            BlockMoves.Play c = moveAt(alongX, sign, face, floor, move);
            if (c != null) {
                entityData.set(DATA_CLIMB, c.save());
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * The step's face (going up) or the edge (going down) ahead of its middle along its way, read from the ground under
     * its middle line within 4 blocks: the first column a block higher (its block at its feet whole), or the first whose
     * ground is gone (going down). NaN if there is none.
     */
    private double faceAhead(boolean alongX, int sign, int floor, boolean up) {
        double along = alongX ? getX() : getZ(), across = alongX ? getZ() : getX();
        int from = Mth.floor(along);
        for (int k = 0; k <= 4; k++) {
            int col = from + sign * k;
            BlockPos at = alongX ? new BlockPos(col, floor, Mth.floor(across)) : new BlockPos(Mth.floor(across), floor, col);
            boolean edge = up ? full(at) : !full(at.below());
            if (edge) {
                return sign > 0 ? col : col + 1;
            }
        }
        return Double.NaN;
    }

    /**
     * The move toward the face (or edge) at {@code face}, if its middle has come to the move's start and there is room;
     * else null. Coming at it more than {@link BlockMoves#SQUARE} degrees off square it first turns square on the spot.
     */
    @Nullable
    private BlockMoves.Play moveAt(boolean alongX, int sign, double face, int floor, int move) {
        double scale = MODEL_SCALE * getScale(), back = MOVES.move(move).aheadPx * scale / 16.0;
        double dist = (face - (alongX ? getX() : getZ())) * sign;
        // not there yet, or come too close to fit the move (a route turning up the step right at it: the old step)
        if (dist > back + 0.1 || dist < back - 0.5) {
            return null;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(alongX ? 0 : sign, alongX ? sign : 0)) - 90.0F;
        int turn = BlockMoves.turnFor(yBodyRot, yaw);
        if (turn < 0) {
            return null;
        }
        double across = alongX ? getZ() : getX();
        boolean room = move == DOWN ? roomDown(alongX, sign, face, floor, across, scale)
                : roomUp(alongX, sign, face, floor, across, scale, move == OUT);
        if (!room) {
            return null;
        }
        double along = face - back * sign;
        return MOVES.start(this, move, alongX ? along : across, getY(), alongX ? across : along, yaw, turn);
    }

    /**
     * Room to climb a block (or out of the shallows): a whole block under both feet lines for three blocks past the face,
     * dry and open over it (two blocks: its body), and open over the lower ground in front of the face where it rears.
     * Out of the shallows the water's surface is no higher than the bank's top (a bank, not a step under the water).
     */
    private boolean roomUp(boolean alongX, int sign, double face, int y, double across, double scale, boolean out) {
        double feet = FEET_OUT * scale / 16.0, half = BODY_HALF * scale / 16.0;
        for (double side : new double[] {across - feet, across + feet}) {
            for (double ahead : new double[] {0.5, 1.5, 2.5}) {
                BlockPos step = cell(alongX, face + ahead * sign, side, y);
                if (!full(step) || !dryOpen(step.above()) || !dryOpen(step.above(2))) {
                    return false;
                }
            }
        }
        for (double side : new double[] {across - half, across, across + half}) {
            for (double ahead : new double[] {-2.5, -1.5, -0.5}) {
                BlockPos low = cell(alongX, face + ahead * sign, side, y);
                if (!open(low) || !open(low.above()) || !open(low.above(2))) {
                    return false;
                }
            }
            for (double ahead : new double[] {0.5, 1.5, 2.5, 3.5}) {
                BlockPos top = cell(alongX, face + ahead * sign, side, y + 1);
                if (!dryOpen(top) || !dryOpen(top.above())) {
                    return false;
                }
            }
        }
        if (out) {
            // wading: the water round its legs at most a block deep (its bed one block under the bank's top)
            BlockPos at = BlockPos.containing(getX(), y + 1, getZ());
            return !level().getFluidState(at).is(FluidTags.WATER);
        }
        return !isInWater();
    }

    /**
     * Room to climb down a block: the top it leaves whole under both feet lines, and past the edge for three blocks a
     * whole block a block lower under both feet, open (the shallows' water no deeper than that block: wading in) and open
     * over it for its body.
     */
    private boolean roomDown(boolean alongX, int sign, double face, int y, double across, double scale) {
        double feet = FEET_OUT * scale / 16.0, half = BODY_HALF * scale / 16.0;
        for (double side : new double[] {across - feet, across + feet}) {
            if (!full(cell(alongX, face - 0.5 * sign, side, y - 1))) {
                return false;
            }
            for (double ahead : new double[] {0.5, 1.5, 2.5}) {
                BlockPos low = cell(alongX, face + ahead * sign, side, y - 2);
                if (!full(low) || !open(low.above()) || !dryOpen(low.above(2))) {
                    return false;
                }
            }
        }
        for (double side : new double[] {across - half, across, across + half}) {
            for (double ahead : new double[] {0.5, 1.5, 2.5, 3.5}) {
                BlockPos at = cell(alongX, face + ahead * sign, side, y - 1);
                if (!open(at) || !dryOpen(at.above()) || !dryOpen(at.above(2))) {
                    return false;
                }
            }
        }
        return !isInWater();
    }

    private boolean full(BlockPos pos) {
        return level().getBlockState(pos).isCollisionShapeFullBlock(level(), pos);
    }

    private boolean open(BlockPos pos) {
        return level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    private boolean dryOpen(BlockPos pos) {
        return open(pos) && level().getFluidState(pos).isEmpty();
    }

    private static BlockPos cell(boolean alongX, double along, double across, int y) {
        return alongX ? new BlockPos(Mth.floor(along), y, Mth.floor(across)) : new BlockPos(Mth.floor(across), y, Mth.floor(along));
    }

    /**
     * Moves it on along its move this tick ({@link BlockMoves#step}). Ends it once done or cut short (dead, the step gone,
     * a block put in its way); after a move it stands a moment unless its route goes straight on up or down another.
     */
    private boolean climbOn(BlockMoves.Play c) {
        BlockMoves.Step step = MOVES.step(this, c, MODEL_SCALE * getScale() / 16.0, !hillAhead());
        if (step == BlockMoves.Step.ON) {
            return true;
        }
        entityData.set(DATA_CLIMB, new CompoundTag());
        if (!getNavigation().isDone()) {
            getNavigation().recomputePath();
        }
        return false;
    }

    /** Whether its route goes on up or down another block within the next few squares (a hill: no rest between the moves). */
    private boolean hillAhead() {
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int floor = Mth.floor(getY() + 1.0E-3);
        for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 5); i++) {
            if (path.getNode(i).y != floor) {
                return true;
            }
        }
        return false;
    }

    /** A hurt cuts a move short: set down where it is, and its anger takes over. */
    private void cutMove() {
        if (climb() != null) {
            BlockMoves.cut(this);
            entityData.set(DATA_CLIMB, new CompoundTag());
        }
    }

    /**
     * On a move the move places it. In water it walks the bed at its full pace (not Minecraft's slow wading), bounding
     * up a step of the bed; still, it rises to float with its eyes and back out where the water is deep enough
     * ({@link #tick}).
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && !level().isClientSide) {
            BlockMoves.Play c = climb();
            if (c != null ? climbOn(c) : startMove()) {
                if (c == null) {
                    climbOn(climb());
                }
                return;
            }
        }
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed() * 0.9F, travelVector);
            double before = getY();
            move(MoverType.SELF, getDeltaMovement());
            Vec3 v = getDeltaMovement();
            v = new Vec3(v.x * 0.6, v.y, v.z * 0.6);
            if (horizontalCollision && isFree(v.x, v.y + 1.1 - getY() + before, v.z)) {
                v = new Vec3(v.x, 0.35, v.z);          // up a step of the bed
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
        double dx = getX() - xo, dz = getZ() - zo, moved = Math.sqrt(dx * dx + dz * dz);
        if (moved > 0.004) {
            movedTick = tickCount;
            stride += (moved - stride) * 0.5;
        }
        if (level().isClientSide) {
            splashes();
            attention.tick(this);
            return;
        }
        if (chargeWait > 0) {
            chargeWait--;
        }
        if (crocWait > 0) {
            crocWait--;
        }
        if (restWait > 0) {
            restWait--;
        }
        if (nibbleWait > 0) {
            nibbleWait--;
        }
        if (angryAt != null && (tickCount > angryUntil || !angryAt.isAlive())) {
            angryAt = null;
        }
        // a doing's clip played out: back to calm
        byte d = doing();
        if (d == YAWN && doingFor() >= YAWN_TICKS || d == EAR_FLICK && doingFor() >= EAR_TICKS || d == DUNG && doingFor() >= DUNG_TICKS
                || d == BITE && doingFor() >= BITE_TICKS || d == RISE && doingFor() >= RISE_TICKS) {
            setDoing(CALM);
        }
        wearPath();
        dungInWater();
        // in the water: walking, it sinks to the bed and bounds along it; still, it rises to float with its eyes, ears
        // and back out where the water is deep enough, and stands on the bed where it is not
        if (isInWater() && climb() == null) {
            double surface = surface();
            boolean going = !getNavigation().isDone() || charging() || walking();
            Vec3 v = getDeltaMovement();
            if (going || Double.isNaN(surface)) {
                setDeltaMovement(v.x, Math.min(v.y, 0.0) - 0.03, v.z);
            } else {
                double want = surface - AFLOAT * getScale();
                setDeltaMovement(v.x, Mth.clamp((want - getY()) * 0.1, -0.03, 0.03) + v.y * 0.5, v.z);
            }
            boolean nowAfloat = afloat() && !going;
            if (nowAfloat && !wasAfloat && doing() == CALM && random.nextInt(2) == 0) {
                setDoing(EAR_FLICK);                    // come up: the ears spin the water off
            }
            wasAfloat = nowAfloat;
        } else {
            wasAfloat = false;
        }
    }

    /**
     * Its dung in the water (the river food web's step 6): while its dung flick's tail spins in the water it sprays a
     * brown cloud behind it, with splashes where the tail breaks the surface; the cloud hangs there a while, murking the
     * water, and draws the barbs and tilapia near to pick at it ({@link #dungNear}). Looks only: nothing grows from it.
     */
    private void dungInWater() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        long now = level().getGameTime();
        int t = doingFor();
        if (doing() == DUNG && isInWater() && t >= DUNG_SPREAD_FROM && t <= DUNG_SPREAD_TO) {
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double back = DUNG_BEHIND * getScale();
            double x = getX() + Mth.sin(yaw) * back, z = getZ() - Mth.cos(yaw) * back;
            double s = surface();
            double y = Double.isNaN(s) ? getY() + 0.8 * getScale() : Math.max(getY() + 0.3, s - 0.7);
            if (!level().getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
                return;                                 // its rump to the bank: the dung lands on the shore, no cloud
            }
            if (dungAt == null || t == DUNG_SPREAD_FROM) {
                dungAt = new Vec3(x, y, z);
            }
            dungUntil = now + DUNG_CLOUD;
            if (t % 2 == 0) {
                double spread = 0.3 + 0.05 * (t - DUNG_SPREAD_FROM);       // the tail's spray widens as it spins
                server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(DUNG_COLOUR, 1.4F), x, y, z, 6,
                        spread, 0.25, spread, 0.0);
                if (!Double.isNaN(s)) {
                    server.sendParticles(ParticleTypes.SPLASH, x, s + 0.05, z, 3, 0.4, 0.0, 0.4, 0.1);
                }
            }
            if (t == DUNG_SPREAD_FROM) {
                playSound(SoundEvents.GENERIC_SPLASH, 0.5F, 1.4F);
            }
            return;
        }
        if (dungAt == null) {
            return;
        }
        if (now > dungUntil || !level().getFluidState(BlockPos.containing(dungAt)).is(FluidTags.WATER)) {
            dungAt = null;
        } else if (now % 10 == 0) {                         // the cloud lingers, murking the water
            server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(DUNG_COLOUR, 1.2F), dungAt.x, dungAt.y, dungAt.z, 3,
                    1.0, 0.3, 1.0, 0.0);
        }
    }

    /** The dung cloud in the water nearest a fish, within {@code reach} blocks of it, or none (the food web's step 6). */
    @Nullable
    public static Vec3 dungNear(Entity fish, double reach) {
        Vec3 best = null;
        double bestDist = reach * reach;
        long now = fish.level().getGameTime();
        for (HippoEntity h : fish.level().getEntitiesOfClass(HippoEntity.class, fish.getBoundingBox().inflate(reach + DUNG_BEHIND + 1))) {
            if (h.dungAt == null || now > h.dungUntil) {
                continue;
            }
            double d = h.dungAt.distanceToSqr(fish.position());
            if (d <= bestDist) {
                best = h.dungAt;
                bestDist = d;
            }
        }
        return best;
    }

    /** Drawing only: a splash where it goes into the water off a move, and as it surfaces. */
    private void splashes() {
        if (isInWater() && walking() && stride > 0.04 * getScale() && random.nextInt(3) == 0) {
            double s = surface();
            if (!Double.isNaN(s) && s - getY() < 1.4 * getScale()) {
                float yaw = yBodyRot * Mth.DEG_TO_RAD;
                double ahead = 1.6 * getScale();
                level().addParticle(ParticleTypes.SPLASH, getX() - Mth.sin(yaw) * ahead + (random.nextDouble() - 0.5) * 1.6,
                        s + 0.05, getZ() + Mth.cos(yaw) * ahead + (random.nextDouble() - 0.5) * 1.6, 0.0, 0.1, 0.0);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            cutMove();
            if (doing() == REST) {
                setDoing(RISE);                         // up off the bank at once
            }
            if (isAlive() && source.getEntity() instanceof LivingEntity by && by != this
                    && !(by instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p))) {
                angryAt = by;                           // whoever hurt it is charged at once
                angryUntil = tickCount + 400;
                chargeWait = 0;
            }
        }
        return hurt;
    }

    /** Its bite lands (the crushing hit of the bite clip): hard, and it shoves the target away. */
    void biteHits(LivingEntity target) {
        if (target.isAlive() && atJaws(target) && target.hurt(damageSources().mobAttack(this), BITE_DAMAGE)) {
            playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 0.8F);
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            target.knockback(1.2, Mth.sin(yaw), -Mth.cos(yaw));
        }
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return false;          // it holds its breath for minutes; in the game it comes up when still
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.9F;
    }

    /** Its spawn spot is its pool's water: only blocks and mobs stand in its way. */
    @Override
    public boolean checkSpawnObstruction(net.minecraft.world.level.LevelReader level) {
        return level.isUnobstructed(this);
    }

    @Override
    public int getNoActionTime() {
        return 0;
    }

    /** Kept for good, as Minecraft's villagers: the pod a player left is there when they come back (BUGS 271). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Drawn whenever any of its body is in view: its box is only the barrel of its body, so its head and rump (2.5 blocks
     * out from its middle) blinked away at the screen's edge with the box (BUGS 271).
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double s = getScale();
        return getBoundingBox().inflate(1.7 * s, 0.4 * s, 1.7 * s);
    }

    /** Joins a pod resting in the pool at {@code pool} (new land's pods: the bull's spot). */
    public void joinPod(BlockPos pool) {
        setPool(pool);
    }

    /** Its pool is here now: its lawns go with the old one (found again from the new pool). */
    void setPool(BlockPos pool) {
        this.pool = pool.immutable();
        lawns.clear();
        lawnsNotBefore = 0;
    }

    /** Its lawns, and the landing it left the water at tonight (for tests). */
    public List<BlockPos> lawnsKnown() {
        return List.copyOf(lawns);
    }

    @Nullable
    public BlockPos landing() {
        return landing;
    }

    /** Its pool (for tests). */
    @Nullable
    public BlockPos pool() {
        return pool;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        double reach = DRAW_DISTANCE * getViewScale();
        return distanceSqr < reach * reach;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION) {
            // spawned somewhere in its pool's water: set down on the bed under it
            BlockPos.MutableBlockPos p = blockPosition().mutable();
            while (p.getY() > level.getMinBuildHeight() && !level.getBlockState(p.below()).isFaceSturdy(level, p.below(),
                    net.minecraft.core.Direction.UP)) {
                p.move(0, -1, 0);
            }
            moveTo(getX(), p.getY(), getZ(), random.nextFloat() * 360.0F, 0.0F);
        }
        life.setUp(false, level.getRandom());
        life.apply();
        if (pool == null && isInWater()) {
            pool = blockPosition();
        }
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        life.save(tag);
        if (pool != null) {
            tag.put("Pool", NbtUtils.writeBlockPos(pool));
        }
        if (led) {
            tag.putBoolean("Led", true);
        }
        if (!lawns.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (BlockPos l : lawns) {
                list.add(NbtUtils.writeBlockPos(l));
            }
            tag.put("Lawns", list);
        }
        if (lawnsDropped > 0) {
            tag.putInt("LawnsDropped", lawnsDropped);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life.load(tag);
        pool = NbtUtils.readBlockPos(tag, "Pool").orElse(null);
        led = tag.getBoolean("Led");             // the film camera's walkers and game tests ("Led:1b")
        lawns.clear();
        lawnsDropped = tag.getInt("LawnsDropped");
        if (tag.get("Lawns") instanceof net.minecraft.nbt.ListTag list) {
            for (net.minecraft.nbt.Tag t : list) {
                if (t instanceof net.minecraft.nbt.IntArrayTag a && a.size() == 3) {
                    lawns.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
                }
            }
        }
        life.apply();
    }

    /** How deep the water is in the column at pos (blocks of water from the surface down to the bed). */
    static int depthAt(LevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < pos.getY() + 16) {
            p.move(0, 1, 0);
        }
        int depth = 0;
        while (level.getFluidState(p).is(FluidTags.WATER) && depth < 16) {
            depth++;
            p.move(0, -1, 0);
        }
        return depth;
    }

    /** A pool spot near a place: water at least {@link #POOL_DEPTH} deep (its bed to stand on), else any water; null if none within reach. */
    @Nullable
    BlockPos poolNear(BlockPos from, int reach) {
        Level level = level();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < 40; i++) {
            int x = from.getX() + random.nextInt(2 * reach + 1) - reach, z = from.getZ() + random.nextInt(2 * reach + 1) - reach;
            int top = com.antcolonies.colony.GroundTop.y(level, x, z) - 1; // through any tree crown (review R7-30)
            BlockPos at = new BlockPos(x, top, z);
            if (!level.getFluidState(at).is(FluidTags.WATER)) {
                continue;
            }
            int depth = depthAt(level, at);
            BlockPos bed = at.below(depth - 1);
            double d = bed.distSqr(from) - (depth >= POOL_DEPTH ? 400 : 0);
            if (d < bestD) {
                bestD = d;
                best = bed;
            }
        }
        return best;
    }

    /** A grass spot to graze on land, between {@link #GRAZE_NEAR} and {@link #GRAZE_FAR} blocks from its pool; null if none found. */
    @Nullable
    BlockPos grassNear(BlockPos home) {
        return grassNear(home, random);
    }

    /** {@link #grassNear(BlockPos)} rolling its own dice (the lawns: from the pool, so pod mates find the same ones). */
    @Nullable
    BlockPos grassNear(BlockPos home, RandomSource dice) {
        for (int i = 0; i < 30; i++) {
            double a = dice.nextDouble() * Math.PI * 2, r = GRAZE_NEAR + dice.nextDouble() * (GRAZE_FAR - GRAZE_NEAR);
            BlockPos stand = grassStand(home.getX() + Mth.floor(Math.cos(a) * r), home.getZ() + Mth.floor(Math.sin(a) * r), home.getY(), 8);
            if (stand != null) {
                return stand;
            }
        }
        return null;
    }

    /** Where it would stand on grass (or soil) in the column x, z within {@code dy} of {@code y} ({@link Grazing#grassStand}). */
    @Nullable
    private BlockPos grassStand(int x, int z, int y, int dy) {
        return Grazing.grassStand(level(), x, z, y, dy);
    }

    /**
     * Its pod's lawns (2026-11-23): found once from its pool with dice rolled from the pool's place, so every hippo of a
     * pod (sharing the bull's pool) finds the same {@link #LAWNS} places, at least {@link #LAWN_APART} apart, and walks
     * the same paths to them; kept with it.
     */
    List<BlockPos> lawns() {
        if (lawns.isEmpty() && pool != null && tickCount >= lawnsNotBefore) {
            RandomSource dice = RandomSource.create(pool.asLong() ^ 0x4C41574E5EL ^ (long) lawnsDropped * 0x9E3779B97F4A7C15L);
            for (int i = 0; i < 12 && lawns.size() < LAWNS; i++) {
                BlockPos c = grassNear(pool, dice);
                if (c != null && lawns.stream().allMatch(l -> horizontalSqr(l, c) >= LAWN_APART * LAWN_APART)) {
                    lawns.add(c);
                }
            }
            if (lawns.isEmpty()) {
                lawnsNotBefore = tickCount + 2400; // none in reach: the same search gives the same answer, so not again for 2 minutes
            }
        }
        return lawns;
    }

    private static double horizontalSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Whether it stands on one of its lawns (or the lawn's growing edge). */
    boolean onLawn() {
        BlockPos at = blockPosition();
        int reach = LAWN_REACH + LAWN_GROW;
        return lawns.stream().anyMatch(l -> horizontalSqr(l, at) <= reach * reach);
    }

    /**
     * A spot to graze on a lawn: grass within {@link #LAWN_REACH} of its middle with a plant to eat within 2 blocks,
     * else the same out to {@link #LAWN_GROW} farther (a lawn cropped bare grows at its edge), else any grass on it
     * (grazing it keeps it short); null if none.
     */
    @Nullable
    BlockPos grazeSpot(BlockPos lawn) {
        BlockPos any = null;
        for (int reach : new int[] {LAWN_REACH, LAWN_REACH + LAWN_GROW}) {
            for (int i = 0; i < 12; i++) {
                BlockPos stand = grassStand(lawn.getX() + random.nextInt(2 * reach + 1) - reach,
                        lawn.getZ() + random.nextInt(2 * reach + 1) - reach, lawn.getY(), 3);
                if (stand == null) {
                    continue;
                }
                if (plantNear(stand, 2) != null) {
                    return stand;
                }
                if (any == null && reach == LAWN_REACH) {
                    any = stand;
                }
            }
        }
        return any;
    }

    /**
     * Where it leaves the water for a lawn and comes back to it: the dry bank spot within {@link #LANDING_REACH} of its
     * pool, beside the water and at most a block over it, nearest the lawn (the same every night, so its feet wear a
     * path from there); null if none.
     */
    @Nullable
    BlockPos landingFor(BlockPos lawn) {
        int w = waterTop();
        if (w == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -LANDING_REACH; dx <= LANDING_REACH; dx++) {
            for (int dz = -LANDING_REACH; dz <= LANDING_REACH; dz++) {
                BlockPos stand = bankStand(pool.getX() + dx, pool.getZ() + dz, w);
                if (stand == null || !waterWithin(stand.getX(), stand.getZ(), w, 1)) {
                    continue;
                }
                double d = horizontalSqr(stand, lawn);
                if (d < bestD) {
                    bestD = d;
                    best = stand;
                }
            }
        }
        return best;
    }

    /** The top block of its pool's water (its y), or {@link Integer#MIN_VALUE} if it has no pool of water or its land is not loaded ({@link Grazing#waterTop}). */
    int waterTop() {
        return Grazing.waterTop(level(), pool);
    }

    /** Where it would stand on dry bank in the column x, z: level with the water's top {@code w} or a block over ({@link Grazing#bankStand}). */
    @Nullable
    private BlockPos bankStand(int x, int z, int w) {
        return Grazing.bankStand(level(), x, z, w);
    }

    /** Whether water's top lies at level {@code w} within {@code reach} columns of x, z. */
    private boolean waterWithin(int x, int z, int w, int reach) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if ((dx != 0 || dz != 0) && level().getFluidState(new BlockPos(x + dx, w, z + dz)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An edible plant at its jaws (the block at the ground there, or the one over it; {@link Grazing#plantAt}), or null. */
    @Nullable
    BlockPos plantAtJaws() {
        Vec3 jaw = jawPoint();
        return Grazing.plantAt(level(), jaw.x, getY(), jaw.z);
    }

    /** The nearest edible plant standing on the ground within {@code reach} columns of {@code stand} ({@link Grazing#plantNear}), or null. */
    @Nullable
    BlockPos plantNear(BlockPos stand, int reach) {
        return Grazing.plantNear(level(), stand, reach);
    }

    /**
     * Eats a grass plant at its jaws, if any: grazed short, not pulled up (2026-11-23, the lawns): tall grass and large
     * ferns are cropped to short grass and ferns, a tall green savanna tuft to a short one, and a short plant to the
     * ground (the grass block under it stays; {@link Grazing#crop}).
     */
    void cropGrass() {
        Grazing.crop(this, plantAtJaws(), true);
    }

    /**
     * Its feet wear a path (2026-11-23): walking on land off its lawns, each block of grass (or soil) one of its two foot
     * lines comes onto turns to path {@link #PATH_WEAR} of the time, the plant on it trampled. Its nights' trips from the
     * same landing to the same lawns wear the same way into a path in a few nights.
     */
    private void wearPath() {
        if (!onGround() || isInWater() || doing() != CALM || !walking() || climb() != null) {
            lastFoot[0] = lastFoot[1] = null;
            return;
        }
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double out = FEET_OUT * MODEL_SCALE * getScale() / 16.0;
        for (int side = 0; side < 2; side++) {
            double s = side == 0 ? out : -out;
            BlockPos under = BlockPos.containing(getX() + Mth.cos(yaw) * s, getY() - 0.5, getZ() + Mth.sin(yaw) * s);
            if (under.equals(lastFoot[side])) {
                continue;
            }
            lastFoot[side] = under;
            if (random.nextFloat() < PATH_WEAR && !onLawn()) {
                wear(under);
            }
        }
    }

    /** Wears one block to path: grass or soil with open air (or a plant) over it. */
    private void wear(BlockPos under) {
        Level level = level();
        BlockState ground = level.getBlockState(under);
        if (!(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.COARSE_DIRT))
                || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, this)) {
            return;
        }
        BlockPos over = under.above();
        BlockState plant = level.getBlockState(over);
        if (!plant.isAir()) {
            if (!plant.canBeReplaced() || !plant.getFluidState().isEmpty()) {
                return;
            }
            level.destroyBlock(over, false, this);     // trampled
        }
        level.setBlock(under, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** The hippos round it (pod mates, and any other in reach), for the bank rest. */
    private List<HippoEntity> othersNear(double reach) {
        return level().getEntitiesOfClass(HippoEntity.class, getBoundingBox().inflate(reach, 8.0, reach), m -> m != this && m.isAlive());
    }

    /**
     * A spot on the bank to rest at (2026-11-23): dry ground within {@link #BANK_REACH} of its pool, level with the
     * water's top or a block over, within 3 blocks of the water, flat and open for 3 by 3 round it; beside a hippo
     * already resting or going to (2 to 4 blocks off: they lie side by side, touching) if it can, else the nearest to the
     * pool; never where another lies or is going. Null if none.
     */
    @Nullable
    BlockPos restSpot() {
        int w = waterTop();
        if (w == Integer.MIN_VALUE) {
            return null;
        }
        List<HippoEntity> others = othersNear(BANK_REACH + 8.0);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -BANK_REACH; dx <= BANK_REACH; dx++) {
            for (int dz = -BANK_REACH; dz <= BANK_REACH; dz++) {
                if (dx * dx + dz * dz > BANK_REACH * BANK_REACH) {
                    continue;
                }
                BlockPos stand = bankStand(pool.getX() + dx, pool.getZ() + dz, w);
                if (stand == null || !waterWithin(stand.getX(), stand.getZ(), w, 3) || !flatRoom(stand)) {
                    continue;
                }
                double score = dx * dx + dz * dz;
                boolean taken = false;
                for (HippoEntity o : others) {
                    BlockPos lies = o.restAt != null ? o.restAt : o.doing() == REST ? o.blockPosition() : null;
                    if (lies == null) {
                        continue;
                    }
                    double d = horizontalSqr(lies, stand);
                    if (d < 4.0) {
                        taken = true;
                    } else if (d <= 16.0) {
                        score -= 1000;                  // beside a resting mate
                    }
                }
                if (!taken && score < bestScore) {
                    bestScore = score;
                    best = stand;
                }
            }
        }
        return best;
    }

    /** Whether the 3 by 3 round a stand spot is flat dry ground with room over it for its body. */
    private boolean flatRoom(BlockPos stand) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = stand.offset(dx, 0, dz);
                if (!full(p.below()) || !dryOpen(p) || !dryOpen(p.above())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The cool hours of the day when it may rest on the bank: morning and late afternoon, no rain. */
    boolean coolHours() {
        if (heldCool != null) {
            return heldCool;
        }
        if (night() || level().isRaining()) {
            return false;
        }
        long t = level().getDayTime() % 24000L;
        return t >= MORNING_FROM && t < MORNING_TO || t >= AFTERNOON_FROM && t < AFTERNOON_TO;
    }

    /** A hippo grass mat to eat within {@link #NIBBLE_REACH} of its pool, on its water's top (nearest it), or null. */
    @Nullable
    BlockPos grassMatNear() {
        int w = waterTop();
        if (w == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -NIBBLE_REACH; dx <= NIBBLE_REACH; dx++) {
            for (int dz = -NIBBLE_REACH; dz <= NIBBLE_REACH; dz++) {
                BlockPos p = new BlockPos(pool.getX() + dx, w + 1, pool.getZ() + dz);
                if (!level().hasChunkAt(p) || !edibleMat(level().getBlockState(p))) {
                    continue;
                }
                double d = distanceToSqr(Vec3.atCenterOf(p));
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /** A hippo grass mat it eats: any but a dying one. */
    static boolean edibleMat(BlockState s) {
        return s.getBlock() instanceof com.antcolonies.savanna.block.HippoGrassBlock
                && s.getValue(com.antcolonies.savanna.block.HippoGrassBlock.STAGE) != com.antcolonies.savanna.block.HippoGrassClumpBlock.DYING;
    }

    /** A hippo grass mat its jaws reach (within a block and a third of its snout, across), or null. */
    @Nullable
    BlockPos matAtJaws() {
        Vec3 jaw = jawPoint();
        int w = waterTop();
        if (w == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos best = null;
        double bestD = 1.35 * 1.35;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = BlockPos.containing(jaw.x + dx, w + 1, jaw.z + dz);
                double ex = p.getX() + 0.5 - jaw.x, ez = p.getZ() + 0.5 - jaw.z, d = ex * ex + ez * ez;
                if (d < bestD && edibleMat(level().getBlockState(p))) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    /**
     * Led by a game test or the film camera: its own day and night goals (home, graze, pool) are off, so only where it is
     * sent and its territory move it.
     */
    public boolean led;
    /** For tests: night or day held whatever the clock (null: the clock). */
    @Nullable
    public Boolean heldNight;
    /** For tests: the bank's cool hours held on or off whatever the clock (null: the clock). */
    @Nullable
    public Boolean heldCool;
    /** For tests: its next rest in its pool ends in a dung flick, whatever the dice (cleared once it flicks). */
    public boolean dungNext;

    /** Where it was last sent and whether a route was found (for tests' messages). */
    @Nullable
    BlockPos sentTo;
    boolean routeFound;

    /** Sends it somewhere on its own legs (its goals), noting where for tests' messages. */
    boolean sendTo(BlockPos to, double speed) {
        sentTo = to;
        routeFound = getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, speed);
        return routeFound;
    }

    /** What it is about, for tests' messages. */
    public String about() {
        Path p = getNavigation().getPath();
        return "pool " + pool + ", sent to " + sentTo + " (route " + routeFound + "), route now "
                + (p == null ? "none" : p.getNextNodeIndex() + "/" + p.getNodeCount() + " to " + p.getTarget() + (p.canReach() ? "" : " (can't reach)"))
                + ", on ground " + onGround() + ", afloat " + afloat() + ", night " + night();
    }

    /** Night: when hippos graze (the shared day clock, as the crocodile's and monitor's). */
    boolean night() {
        return heldNight != null ? heldNight : !level().isDay();
    }

    // --- the goals -----------------------------------------------------------------------------------------------------

    /**
     * Its territory: a survival player coming within {@link #WARN_REACH} blocks gets a yawn as a warning (facing them);
     * still within {@link #CHARGE_REACH} after it, or coming nearer, or whoever hurt it, is charged: it runs at them and
     * bites (the crushing hit at the clip's {@link #BITE_HIT}) until they are {@link #CHARGE_GIVE_UP} blocks off or
     * {@link #CHARGE_TICKS} have passed; then it rests a while before another. A grown crocodile is minded by bulls only,
     * and only within {@link #CROC_REACH}: the yawn first, then, if it stays, a bluff charge that ends once it is
     * {@link #CROC_GIVE_UP} off, with one bite at most, after which he lets crocodiles be for {@link #CROC_CALM} ticks
     * even if one could not get away (real pods let crocodiles bask beside them; BUGS 272, 273).
     */
    static class TerritoryGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private LivingEntity target;
        private boolean charge, bitten;
        private int ticks, repath;

        TerritoryGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (h.climb() != null || h.doing() == REST || h.doing() == RISE) {
                return false;                       // lying on the bank it gets up first (its bank rest sees the danger)
            }
            if (h.angryAt != null && h.angryAt.isAlive()) {
                target = h.angryAt;
                charge = true;
                return true;
            }
            if (h.chargeWait > 0 || h.getRandom().nextInt(3) != 0) {
                return false;
            }
            LivingEntity p = h.life().isMale() && h.crocWait == 0 ? nearestCroc() : null;
            if (p == null) {
                p = h.level().getNearestPlayer(h.getX(), h.getY(), h.getZ(), WARN_REACH * h.getScale(),
                        e -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(e));
            }
            if (p == null) {
                return false;
            }
            target = p;
            boolean warned = h.warned.getOrDefault(p.getUUID(), 0) > h.tickCount;
            charge = warned && p.distanceTo(h) < chargeReach(p);
            return charge || !warned; // warned and keeping off: left alone; never warned: the yawn first
        }

        @Nullable
        private LivingEntity nearestCroc() {
            double reach = CROC_REACH * h.getScale();
            CrocodileEntity best = null;
            for (CrocodileEntity c : h.level().getEntitiesOfClass(CrocodileEntity.class, h.getBoundingBox().inflate(reach))) {
                if (c.isAlive() && c.getScale() > 0.5F && c.distanceTo(h) < reach && (best == null || c.distanceTo(h) < best.distanceTo(h))) {
                    best = c;
                }
            }
            return best;
        }

        /** Within this it charges a target that stayed after the warning (a crocodile: right up to him). */
        private double chargeReach(LivingEntity t) {
            return (t instanceof CrocodileEntity ? CROC_REACH : CHARGE_REACH) * h.getScale();
        }

        /** Past this (blocks) it gives up even on what hurt it (its follow range; the user's call 2026-11-26, review R9-24). */
        private static final double ANGRY_GIVE_UP = 32.0;

        /** Past this a charge gives up (a crocodile: once it has backed off). */
        private double giveUp(LivingEntity t) {
            return (t instanceof CrocodileEntity ? CROC_GIVE_UP : CHARGE_GIVE_UP) * h.getScale();
        }

        @Override
        public void start() {
            ticks = 0;
            repath = 0;
            bitten = false;
            h.getNavigation().stop();
            if (!charge) {
                h.setDoing(YAWN);                   // the warning: the great yawn, facing them
                h.playSound(SoundEvents.RAVAGER_ROAR, 0.7F, 1.4F);
                h.warned.values().removeIf(until -> until <= h.tickCount);
                if (target != null) {
                    h.warned.put(target.getUUID(), h.tickCount + WARN_AGAIN);
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (target == null || !target.isAlive() || h.isDeadOrDying()
                    || target instanceof Player p && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(p)) {
                return false;
            }
            if (!charge) {
                return h.doing() == YAWN || ticks < YAWN_TICKS;
            }
            if (bitten && target instanceof CrocodileEntity && h.doing() != BITE) {
                return false;                           // one bite sees a crocodile off
            }
            // (what hurt it is charged from as far as it follows anything: review R9-15, a hippo shot from 17 blocks
            // started its charge and ended it at the next check, the territory's 16 blocks)
            return ticks < CHARGE_TICKS && target.distanceTo(h) < (h.angryAt == target ? ANGRY_GIVE_UP : giveUp(target));
        }

        @Override
        public void tick() {
            ticks++;
            if (!charge) {
                h.face(target.getX(), target.getZ(), 6.0F);
                h.getLookControl().setLookAt(target);
                if (ticks >= YAWN_TICKS - 2 && target.distanceTo(h) < chargeReach(target)) {
                    charge = true;                  // stayed near after the warning
                    ticks = 0;
                    h.setDoing(CALM);
                }
                return;
            }
            h.getLookControl().setLookAt(target);
            if (h.doing() == BITE) {
                h.face(target.getX(), target.getZ(), 10.0F);
                if (h.doingFor() == BITE_HIT) {
                    h.biteHits(target);
                    bitten = true;
                }
                return;
            }
            h.setCharging(true);
            if (h.atJaws(target)) {
                h.getNavigation().stop();
                h.setDoing(BITE);
                h.playSound(SoundEvents.RAVAGER_ROAR, 0.5F, 1.8F);
                return;
            }
            if (--repath <= 0) {
                repath = 8;
                h.getNavigation().moveTo(target, CHARGE_SPEED);
            }
        }

        @Override
        public void stop() {
            h.setCharging(false);
            if (h.doing() == YAWN || h.doing() == BITE) {
                h.setDoing(CALM);
            }
            if (bitten && target instanceof CrocodileEntity) {
                h.crocWait = CROC_CALM;                 // one bite, then he lets crocodiles be a while
            }
            if (charge) {
                h.chargeWait = CHARGE_REST;
                if (h.angryAt == target) {
                    h.angryAt = null;
                }
            }
            h.getNavigation().stop();
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * By day out of the water (dawn after the night's grazing, or after a rest on the bank): back to its pool, or the
     * nearest water deep enough; from its lawn by the landing it left the water at (its path home).
     */
    static class HomeGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private BlockPos to;
        private int ticks;
        private boolean viaLanding;

        HomeGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (h.led || h.night() || h.isInWater() || h.climb() != null || h.tickCount < h.homeNotBefore || h.getRandom().nextInt(10) != 0) {
                return false;
            }
            BlockPos from = h.pool != null ? h.pool : h.blockPosition();
            to = h.poolNear(from, h.pool != null ? 6 : 24);
            return to != null;
        }

        @Override
        public void start() {
            ticks = 0;
            h.setDoing(CALM);
            viaLanding = h.landing != null && horizontalSqr(h.landing, h.blockPosition()) > 9.0
                    && h.pool != null && horizontalSqr(h.landing, h.pool) <= 32 * 32;
            h.sendTo(viaLanding ? h.landing : to, STROLL * 1.2);
        }

        @Override
        public boolean canContinueToUse() {
            return !h.isInWater() && ticks < 1200 && (!h.getNavigation().isDone() || h.climb() != null || viaLanding);
        }

        @Override
        public void tick() {
            ticks++;
            if (viaLanding && (horizontalSqr(h.landing, h.blockPosition()) <= 6.25 || ticks > 800)) {
                viaLanding = false;                     // at its landing: down into the water
                h.sendTo(to, STROLL * 1.2);
            }
            if (ticks % 100 == 0 && h.getNavigation().isDone() && h.climb() == null) {
                h.sendTo(viaLanding ? h.landing : to, STROLL * 1.2);
            }
        }

        @Override
        public void stop() {
            if (h.isInWater()) {
                h.landing = null;
            } else if (h.getNavigation().isDone() && h.climb() == null) {
                h.homeNotBefore = h.tickCount + 200; // its way home failed or ended short (fenced in, a pit): not again for 10 seconds
            }
        }
    }

    /**
     * In the cool of the morning and the late afternoon ({@link #coolHours}) a hippo in its pool now and then hauls out
     * onto the bank ({@link #restSpot}) and lies down there ({@code rest_down}, {@code rest}) for 1 to 2.5 minutes, pod
     * mates side by side; it gets up ({@code rest_up}) when its time is up, the cool hours end, a survival player comes
     * within {@link #WARN_REACH}, or it is hurt, and its home goal takes it back into the water. Real hippos haul out to
     * bask on sand bars and banks in the cooler hours; in the heat of the day they keep to the water (2026-11-23, my picks).
     */
    static class BankRestGoal extends Goal {
        private final HippoEntity h;
        private int ticks, left;
        private boolean lying;
        private float face;

        BankRestGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (h.led || h.restWait > 0 || !h.isInWater() || h.climb() != null || h.doing() != CALM || h.charging()
                    || h.getRandom().nextInt(100) != 0 || !h.coolHours()) {
                return false;
            }
            h.restAt = h.restSpot();
            if (h.restAt == null) {
                h.restWait = 600;                       // no bank here: look again in half a minute
            }
            return h.restAt != null;
        }

        @Override
        public void start() {
            ticks = 0;
            lying = false;
            h.sendTo(h.restAt, STROLL);
        }

        @Override
        public boolean canContinueToUse() {
            if (h.restAt == null || h.isDeadOrDying()) {
                return false;
            }
            if (h.doing() == RISE) {
                return true;                            // its getting up plays out
            }
            return lying ? h.doing() == REST : ticks < 1200 && h.coolHours() && h.angryAt == null;
        }

        @Override
        public void tick() {
            ticks++;
            if (h.doing() == RISE) {
                return;
            }
            if (!lying) {
                if (h.climb() != null) {
                    return;
                }
                if (!h.isInWater() && h.onGround() && horizontalSqr(h.restAt, h.blockPosition()) <= 1.5) {
                    lying = true;
                    h.getNavigation().stop();
                    left = REST_SHORTEST + h.getRandom().nextInt(REST_LONGEST - REST_SHORTEST);
                    face = h.getYRot();
                    for (HippoEntity o : h.othersNear(5.0)) {
                        if (o.doing() == REST) {
                            face = o.getYRot();         // lies the way its neighbour lies
                            break;
                        }
                    }
                    h.setDoing(REST);
                } else if (ticks % 60 == 0 && h.getNavigation().isDone()) {
                    h.sendTo(h.restAt, STROLL);
                }
                return;
            }
            float yaw = Mth.approachDegrees(h.getYRot(), face, 3.0F);
            h.setYRot(yaw);
            h.yBodyRot = yaw;
            h.yHeadRot = yaw;
            Vec3 v = h.getDeltaMovement();
            h.setDeltaMovement(0.0, v.y, 0.0);
            if (--left <= 0 || !h.coolHours() || h.hurtTime > 0 || h.angryAt != null || h.tickCount % 10 == 0 && playerNear()) {
                h.setDoing(RISE);
            }
        }

        private boolean playerNear() {
            return h.level().getNearestPlayer(h.getX(), h.getY(), h.getZ(), WARN_REACH * h.getScale(),
                    e -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(e) && EntitySelector.LIVING_ENTITY_STILL_ALIVE.test(e)) != null;
        }

        @Override
        public void stop() {
            if (h.doing() == REST) {
                h.setDoing(RISE);
            }
            h.restAt = null;
            h.restWait = REST_AGAIN + h.getRandom().nextInt(REST_AGAIN);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * Now and then, in the water by day or before going out at night, it wades to a hippo grass mat near its pool
     * ({@link #grassMatNear}) and eats there: it stops as soon as a mat is at its snout and grazes, head dunked, two or
     * three bites, each eating the mat its jaws reach (the patch grows back from its clump). Then it waits 2 to 5 minutes
     * ({@code docs/plans/papyrus-and-hippo-grass.md} step 5; 2026-11-23, my picks).
     */
    static class NibbleGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private BlockPos mat;
        private int ticks, bites;

        NibbleGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (h.led || h.nibbleWait > 0 || !h.isInWater() || h.climb() != null || h.doing() != CALM || h.charging()
                    || h.getRandom().nextInt(100) != 0 || h.pool == null) {
                return false;
            }
            mat = h.grassMatNear();
            if (mat == null) {
                h.nibbleWait = 600;                     // none near: look again in half a minute
            }
            return mat != null;
        }

        @Override
        public void start() {
            ticks = 0;
            bites = 2 + h.getRandom().nextInt(2);
            h.sendTo(mat.below(), STROLL);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 900 && bites > 0 && h.isInWater() && h.angryAt == null;
        }

        @Override
        public void tick() {
            ticks++;
            if (h.doing() == GRAZE) {
                if (h.doingFor() > 0 && h.doingFor() % GRAZE_LOOP == GRAZE_LOOP / 2) {
                    BlockPos at = h.matAtJaws();
                    if (at != null && net.neoforged.neoforge.event.EventHooks.canEntityGrief(h.level(), h)) {
                        h.level().destroyBlock(at, false, h);
                    }
                    bites--;
                }
                return;
            }
            if (h.doing() != CALM || h.climb() != null) {
                return;
            }
            BlockPos at = h.matAtJaws();
            if (at != null) {
                h.getNavigation().stop();
                h.face(at.getX() + 0.5, at.getZ() + 0.5, 30.0F);
                h.setDoing(GRAZE);
                return;
            }
            if (ticks % 40 == 0) {
                BlockPos next = h.grassMatNear();
                if (next == null) {
                    bites = 0;                          // eaten or gone
                } else if (!next.equals(mat) || h.getNavigation().isDone()) {
                    mat = next;
                    h.sendTo(mat.below(), STROLL);
                }
            }
        }

        @Override
        public void stop() {
            if (h.doing() == GRAZE) {
                h.setDoing(CALM);
            }
            h.getNavigation().stop();
            h.nibbleWait = NIBBLE_AGAIN + h.getRandom().nextInt(NIBBLE_AGAIN * 3 / 2);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * At night: out of the water to graze the land round its pool. It grazes one of its pod's lawns ({@link #lawns}; with
     * none found, any grass spot {@link #GRAZE_NEAR} to {@link #GRAZE_FAR} blocks off): out of the water at the lawn's
     * landing ({@link #landingFor}), along its path to a spot on the lawn ({@link #grazeSpot}), and grazes there for 20 to
     * 40 seconds (a plant at its jaws cropped each loop or two), a few steps on to the next plant when none is left at its
     * jaws, now and then flicking its dung; at dawn the home goal takes it back by the same landing.
     */
    static class GrazeGoal extends Goal {
        private final HippoEntity h;
        @Nullable
        private BlockPos spot;
        private int left, ticks;
        private boolean viaLanding, grazed;

        GrazeGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (h.led || !h.night() || h.climb() != null || h.getRandom().nextInt(10) != 0) {
                return false;
            }
            if (h.pool == null && h.isInWater()) {
                h.setPool(h.blockPosition());
            }
            List<BlockPos> lawns = h.lawns();
            if (!lawns.isEmpty()) {
                h.lawn = lawns.get(h.getRandom().nextInt(lawns.size()));
                spot = h.grazeSpot(h.lawn);
            } else {
                h.lawn = null;
                spot = h.grassNear(h.pool != null ? h.pool : h.blockPosition());
            }
            return spot != null;
        }

        @Override
        public void start() {
            left = 400 + h.getRandom().nextInt(400);
            ticks = 0;
            h.setDoing(CALM);
            viaLanding = false;
            grazed = false;
            if (h.isInWater() && h.lawn != null) {
                BlockPos l = h.landingFor(h.lawn);
                if (l != null) {
                    h.landing = l;
                    viaLanding = true;
                }
            }
            h.sendTo(viaLanding ? h.landing : spot, STROLL);
        }

        @Override
        public boolean canContinueToUse() {
            return h.night() && left > 0 && ticks < 1600;
        }

        @Override
        public void tick() {
            ticks++;
            if (viaLanding) {
                // out of the water at its landing first: the same way every night
                if (h.climb() == null && !h.isInWater() && horizontalSqr(h.landing, h.blockPosition()) <= 6.25 || ticks > 800) {
                    viaLanding = false;
                    h.sendTo(spot, STROLL);
                } else if (ticks % 100 == 0 && h.getNavigation().isDone() && h.climb() == null) {
                    h.sendTo(h.landing, STROLL);
                }
                return;
            }
            boolean there = h.getNavigation().isDone() && h.climb() == null && !h.isInWater();
            if (!there && h.doing() == CALM && h.climb() == null && !h.isInWater() && h.onLawn() && h.plantAtJaws() != null) {
                h.getNavigation().stop();               // on its lawn with a plant at its snout: graze here
                there = true;
            }
            if (!there) {
                if (ticks % 100 == 0 && h.getNavigation().isDone() && h.climb() == null) {
                    h.sendTo(spot, STROLL);
                }
                return;
            }
            left--;
            grazed = true;
            if (h.doing() == CALM) {
                if (h.getRandom().nextInt(600) == 0) {
                    h.setDoing(DUNG);
                } else {
                    h.setDoing(GRAZE);
                }
            }
            if (h.doing() == GRAZE && h.doingFor() > 0) {
                int t = h.doingFor() % GRAZE_LOOP;
                if (t == GRAZE_LOOP / 2 && h.getRandom().nextBoolean()) {
                    h.cropGrass();
                } else if (t == 0 && h.plantAtJaws() == null) {
                    // nothing left at its snout: a few steps on to the nearest plant
                    Vec3 jaw = h.jawPoint();
                    BlockPos next = h.plantNear(BlockPos.containing(jaw.x, h.getY() + 0.2, jaw.z), 4);
                    if (next != null) {
                        spot = next;
                        h.setDoing(CALM);
                        h.sendTo(next, STROLL);
                    }
                }
            }
        }

        @Override
        public void stop() {
            if (h.doing() == GRAZE) {
                h.setDoing(CALM);
            }
            if (!grazed && ticks >= 1600 && h.lawn != null && h.lawns.remove(h.lawn)) {
                h.lawnsDropped++;                       // a lawn it cannot get to (across the river, walled off): another is found
            }
        }

        /** Every tick: stepped every other tick (Minecraft's default) the loop's crop tick never came, so nothing was cropped. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /**
     * By day in the water: it rests afloat (or on the bed in the shallows), and every half minute to a minute and a half
     * wanders to another spot of its pool along the bed, within {@link #POOL_WANDER} of its middle, so the pod stays
     * packed together; now and then it yawns. Its pool moves only when the pool itself is gone (the pod's members
     * drifting each to its own pool spread a pod over half a river, BUGS 272).
     */
    static class PoolGoal extends Goal {
        private final HippoEntity h;
        private int restFor;

        PoolGoal(HippoEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !h.led && !h.night() && h.isInWater() && h.climb() == null;
        }

        @Override
        public void start() {
            restFor = 200 + h.getRandom().nextInt(600);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (!h.getNavigation().isDone()) {
                return;
            }
            if (h.doing() == CALM && --restFor <= 0) {
                if (h.pool == null || h.poolGone()) {
                    h.setPool(h.blockPosition());       // its pool dried or filled in: here is its pool now
                }
                if (h.dungNext || h.getRandom().nextInt(DUNG_IN_POOL) == 0) {
                    h.dungNext = false;
                    h.setDoing(DUNG);                   // afloat or standing: the tail spins its dung into the water
                } else if (h.getRandom().nextInt(4) == 0) {
                    h.setDoing(YAWN);
                    h.playSound(SoundEvents.RAVAGER_AMBIENT, 0.6F, 0.6F);
                } else {
                    BlockPos to = h.poolNear(h.pool, POOL_WANDER);
                    if (to != null) {
                        h.sendTo(to, STROLL);
                    }
                }
                restFor = 200 + h.getRandom().nextInt(1200);
            }
        }
    }

    /** Whether its pool is gone: no water left at it. */
    boolean poolGone() {
        // (land not loaded is not "gone": a hippo led far off never loads its pool, review R9-17; the same judge as waterTop, R9-18)
        return pool != null && level().isLoaded(pool) && waterTop() == Integer.MIN_VALUE;
    }

    /** How deep the water is where it stands (blocks). */
    int depthHere() {
        return depthAt(level(), BlockPos.containing(getX(), getY() + 0.2, getZ()));
    }

    // --- clips -----------------------------------------------------------------------------------------------------------

    /** Clips named by the film camera ({@code film:<clip>}, a still one). */
    private static final java.util.Map<String, RawAnimation> FILM_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("charge");
    private static final RawAnimation FLOAT = RawAnimation.begin().thenLoop("float");
    private static final RawAnimation BED_WALK = RawAnimation.begin().thenLoop("underwater_walk");
    private static final RawAnimation YAWN_CLIP = RawAnimation.begin().thenPlayAndHold("yawn");
    private static final RawAnimation BITE_CLIP = RawAnimation.begin().thenPlayAndHold("bite");
    private static final RawAnimation EAR_CLIP = RawAnimation.begin().thenPlayAndHold("ear_flick");
    private static final RawAnimation DUNG_CLIP = RawAnimation.begin().thenPlayAndHold("dung_flick");
    private static final RawAnimation GRAZE_CLIP = RawAnimation.begin().thenLoop("graze");
    private static final RawAnimation REST_CLIP = RawAnimation.begin().thenPlay("rest_down").thenLoop("rest");
    private static final RawAnimation RISE_CLIP = RawAnimation.begin().thenPlayAndHold("rest_up");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    /** Every move's clip, by its name ({@link #MOVES}; made as first played). */
    private static final java.util.Map<String, RawAnimation> MOVE_CLIPS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        bodyClips = new SteadyController<>(this, "body", MOVES.lead(), this::animate).clock(HippoEntity::climbClock);
        bodyClips.setCustomInstructionKeyframeHandler(note -> {
        });         // the moves' clips' notes (their path) are for the tools only
        controllers.add(bodyClips);
    }

    /** Whether it walks (drawing only): it moved within the last {@link #WALK_HOLD} ticks. */
    public boolean walking() {
        return tickCount - movedTick <= WALK_HOLD;
    }

    /** Whether the water is over its back where it stands (drawing only: it bounds along the bed, not wades). */
    private boolean underWater() {
        double s = surface();
        return !Double.isNaN(s) && s - getY() > 1.5 * getScale();
    }

    private PlayState animate(AnimationState<HippoEntity> state) {
        AnimationController<HippoEntity> player = state.getController();
        player.setAnimationSpeed(1.0);
        net.minecraft.network.chat.Component film = getCustomName();
        if (film != null && film.getString().startsWith("film:")) {
            return state.setAndContinue(FILM_CLIPS.computeIfAbsent(film.getString().substring(5), c -> RawAnimation.begin().thenLoop(c)));
        }
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        BlockMoves.Play c = climb();
        if (c != null && !c.turning(level().getGameTime())) {     // a move wins over everything else it does
            return state.setAndContinue(MOVE_CLIPS.computeIfAbsent(MOVES.clip(c), n -> RawAnimation.begin().thenPlayAndHold(n)));
        }
        if (c != null) {                        // turning square to the edge on the spot first: its feet step round
            return state.setAndContinue(WALK);
        }
        switch (doing()) {
            case YAWN:
                return state.setAndContinue(YAWN_CLIP);
            case BITE:
                return state.setAndContinue(BITE_CLIP);
            case EAR_FLICK:
                return state.setAndContinue(EAR_CLIP);
            case DUNG:
                return state.setAndContinue(DUNG_CLIP);
            case GRAZE:
                return state.setAndContinue(GRAZE_CLIP);
            case REST:
                return state.setAndContinue(REST_CLIP);
            case RISE:
                return state.setAndContinue(RISE_CLIP);
            default:
                break;
        }
        double size = getScale();
        if (walking() && isInWater() && underWater()) {
            player.setAnimationSpeed(Mth.clamp(stride / (0.025 * size), 0.6, 2.0));
            return state.setAndContinue(BED_WALK);
        }
        if (afloat() && !walking()) {
            return state.setAndContinue(FLOAT);
        }
        if (walking()) {
            if (charging() && stride > CHARGE_DRAWN * size) {
                player.setAnimationSpeed(Mth.clamp(stride / (CHARGE_CLIP_SPEED * size), 0.6, 3.0));
                return state.setAndContinue(CHARGE);
            }
            player.setAnimationSpeed(Mth.clamp(stride / (WALK_CLIP_SPEED * size), 0.5, 3.0));
            return state.setAndContinue(WALK);
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
}
