package com.antcolonies.savanna.animal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.GroundTop;
import com.antcolonies.entity.body.Placement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The lions' hunt and feast (lion step 2, 2026-11-25, {@code docs/plans/lion-hunt.md}, part D; checked by two critics).
 * One goal between the temper and the day, each stage one rule and one deadline, the hunt's state kept on the lion
 * ({@link State}) so a short interruption (its temper) resumes it. Every lion reads its mates (never told):
 * <ul>
 * <li><b>Who and when:</b> a pride is hungry when its leader (the first lioness in the pride's order: the elder while she
 * lives) has lived {@link #HUNGRY} ticks since her last meal. She starts a try at dusk (once the pride has drunk, until
 * the herd lies down at 22:00) and at dawn (5:00 to 7:00), picking the prey once: in the nearest herd within
 * {@link #HUNT_REACH} of the den, in ticking land, a calf or teen, else a grown one only from a herd of
 * {@link #HERD_FLOOR} grown or more (the weakest, then the smallest). Her lionesses join; the male and the cubs lie where
 * they are until a kill calls them or the try ends.</li>
 * <li><b>SPREAD</b> (deadline {@link #SPREAD_TICKS}): each hunter trots to its own start spot {@link #START_OUT} from the
 * prey on the den's side, fanned by its rank among the hunters; STALK starts for all when all are there.</li>
 * <li><b>STALK</b> (deadline {@link #STALK_TICKS}): crouched straight at the prey; while any buffalo of the herd within
 * {@link BuffaloEntity#LION_STALK_SEEN} has its head up, it freezes flat (at most {@link #FREEZE_MOST}, then on).
 * Unseen, once its place on the prey is within its leap's reach, it leaps ({@code stalk_leap_bite}; {@link #strike}).</li>
 * <li><b>FIGHT</b> (a sprint of at most {@link #RUSH_TICKS} without a grip): when the herd flees, a mate is fighting, or
 * the stalk ran out: a gallop at the prey; once its place on the prey is within its smack's reach, it leaps out of the
 * gallop ({@code jump_smack}). A strike carries itself (the Motion Studio phase 3, step 4): its clip holds its own
 * travel, and the lion rides the prey from the strike's start, placed along that travel from where it set off to its
 * place at the free spot on the prey nearest it (the haunch, the throat either side, the flanks), so it lands there
 * instead of gliding (BUGS 375). From the strike's hit it clings there, biting (each bite past Minecraft's hit cooldown;
 * a calf knocked down is pinned). A lion hurt by a buffalo lets go and
 * backs off to {@link #BACK_OFF} blocks from it for {@link #BACK_OFF_TICKS}. The prey dead: the kill. The sprint spent:
 * the try is over, it lies down where it is for {@link #LIE_UP}.</li>
 * <li><b>FEAST:</b> any pride member goes to a mate's kill (the carcass with its den, near the kill), takes a place round it
 * by the feast order (males first, then lionesses, cubs last; one pushed back growls), feeds until full (male 6,
 * lioness 4, cub 2 bites, a meat bite every {@link #BITE_EVERY}) or the meat is gone; then its hunger is 0 and it lies
 * by the kill until 16:00 the next day. Its place is a feeding spot on the carcass's drawn body (the Motion Studio phase 3,
 * step 5; the buffalo's spot table): it takes the best spot free for it and keeps it until one of a kind before it takes
 * it; it lies down there ({@code rest_down}), set so its jaw at its bite is on the spot, and plays {@code feed}.</li>
 * </ul>
 * All numbers my picks.
 */
public final class LionHunt {
    /** Hungry after this long since its last meal (lived ticks: 2 game days). */
    public static final int HUNGRY = 48000;
    /** Prey is looked for within this of the den (blocks), and grown prey only in a herd of this many grown or more. */
    static final double HUNT_REACH = 96.0;
    static final int HERD_FLOOR = 5;
    /**
     * The dusk try runs until the herd lies down (22:00); the dawn try from 5:30 (the herd is up: its last stands at about
     * 5:10) to 7:00 (day times).
     */
    static final long DUSK_UNTIL = 16000, DAWN_FROM = 23500, DAWN_UNTIL = 1000;
    /** Start spots this far from the prey (blocks), fanned this far apart (degrees) by rank; reached within this. */
    static final double START_OUT = 20.0, FAN = 35.0, AT_START = 2.0;
    static final int SPREAD_TICKS = 1200, STALK_TICKS = 1800;
    /**
     * The rush: a lion is a sprinter, it gallops at most this long (ticks) without a grip on the prey, then gives up (most
     * rushes from far fail: it closes about 2.5 blocks a second on a fleeing buffalo, so from about 15 blocks or more).
     */
    static final int RUSH_TICKS = 120;
    /** Creeping pace (move control share: about 0.7 blocks a second), and the longest freeze (ticks). */
    static final double STALK_PACE = 0.64;
    static final int FREEZE_MOST = 200;
    /** It freezes this far outside a buffalo's sight (blocks), so it is never caught mid-step. */
    static final double FREEZE_MARGIN = 1.0;
    /** A lion hurt by a buffalo backs off to this far from it (blocks), for this long (ticks). */
    static final double BACK_OFF = 8.0;
    static final int BACK_OFF_TICKS = 60;
    /** A feeder takes a meat bite this often (ticks); full after this many (male, lioness, cub). */
    static final int BITE_EVERY = 200, FILL_MALE = 6, FILL_LIONESS = 4, FILL_CUB = 2;
    /** Full, it lies this far from the carcass (blocks, and a little more by rank). */
    static final double LIE_OUT = 5.0;
    /** A displaced feeder growls this long (ticks). */
    static final int GROWL_TICKS = 40;
    /** Lions look for a mate's carcass this near the kill (blocks), at most this often (ticks). */
    static final double CARCASS_NEAR = 6.0;
    static final int LOOK_EVERY = 20;
    /** A hunter stands by its kill at most this long for its carcass (ticks: the death clip and more). */
    static final int KILL_WAIT = 100;
    /** Its own kill is looked for this far round it too (blocks: found again after a reload, the carcass's den saved). */
    static final double OWN_KILL_REACH = 32.0;
    /** It feeds at a kill only this long after its last meal or more (ticks: back to the leftovers the next day). */
    static final int REJOIN = 6000;
    /** A try over, it lies where it is this long (ticks); the male and the cubs lie this long after the hunt too. */
    static final int LIE_UP = 600;
    /** The clip it feeds with, lying at a carcass's feeding spot (its bite's tick in its spot table). */
    static final String FEED_CLIP = "feed";
    /**
     * Within this of its feeding place (blocks) it lies down, shuffling the rest of the way as it goes down (a quarter of
     * a block over the second its lying down takes).
     */
    static final double AT_PLACE = 0.25;
    /** Within this of a feeding or lying place (blocks) it steps straight onto it. */
    static final double STEP_IN = 2.5;
    /** A feeding or lying place not reached by this long (ticks: in a trunk, up a step) is given up: it stays near. */
    static final int ARRIVE_TICKS = 100;

    private LionHunt() {
    }

    /** Where it is in a hunt. */
    public enum Stage {
        NONE, SPREAD, STALK, FIGHT, WAIT, FEAST
    }

    /** A lion's hunt state (on the lion; only its hunger and its last try are saved). */
    public static final class State {
        int sinceMeal;
        Stage stage = Stage.NONE;
        long tryKey = Long.MIN_VALUE;
        @Nullable
        UUID preyId;
        @Nullable
        BuffaloEntity prey;
        int stageSince, rushSince, frozenSince = -1, waitUntil, arriveBy;
        boolean ready, holding, struck;
        /** The spot of its prey it holds (the prey's spot table's index: {@link BuffaloEntity#SPOTS}). */
        int holdSpot = -1;
        /** Its start spot (worked out at each repath). */
        @Nullable
        Vec3 spot;
        int buffaloHit = -1000;
        @Nullable
        UUID buffaloHitId;
        @Nullable
        Vec3 killAt;
        @Nullable
        UUID feastId;
        @Nullable
        RemainsEntity carcass;
        int ate, lastBite, lookedAt = -1000, growlUntil;
        /** The feeding spot of the carcass it has (the buffalo's spot table's index; -1: none), and whether it lies at it. */
        int feedSpot = -1;
        boolean lyingAt;
        /** Its last shuffle onto its feeding place as it lies down (null: it lies where it got to). */
        @Nullable
        com.antcolonies.entity.body.Strikes.Ride feedSettle;
        boolean full;
        long feastEvening = Long.MIN_VALUE;

        boolean hunting() {
            return stage == Stage.SPREAD || stage == Stage.STALK || stage == Stage.FIGHT;
        }

        boolean holding() {
            return holding && stage == Stage.FIGHT;
        }

        /** Hurt by a buffalo: it lets go and backs off (never angry at it). */
        void hitBy(LionEntity l, BuffaloEntity b) {
            buffaloHit = l.tickCount;
            buffaloHitId = b.getUUID();
            holding = false;
        }

        void save(CompoundTag tag) {
            tag.putInt("SinceMeal", sinceMeal);
            if (tryKey != Long.MIN_VALUE) {
                tag.putLong("HuntTry", tryKey);
            }
            if (feastId != null) {                              // its feast: found again after a reload, its share kept
                tag.putUUID("FeastOf", feastId);
                tag.putInt("Ate", ate);
                tag.putBoolean("Full", full);
                tag.putLong("FeastEvening", feastEvening);
            }
        }

        void load(CompoundTag tag) {
            sinceMeal = tag.getInt("SinceMeal");
            tryKey = tag.contains("HuntTry") ? tag.getLong("HuntTry") : Long.MIN_VALUE;
            if (tag.hasUUID("FeastOf")) {
                feastId = tag.getUUID("FeastOf");
                ate = tag.getInt("Ate");
                full = tag.getBoolean("Full");
                feastEvening = tag.getLong("FeastEvening");
            }
        }

        void to(Stage s, int now) {
            stage = s;
            stageSince = now;
        }

        /** Out of the hunt (the try is over or never was). */
        void endHunt() {
            stage = Stage.NONE;
            preyId = null;
            prey = null;
            holding = false;
            struck = false;
            ready = false;
            spot = null;
            frozenSince = -1;
        }
    }

    /** The try at this day time: dusk (day number times 2) or dawn (the next day's number times 2 plus 1); null outside both. */
    @Nullable
    static Long tryAt(long dayTime, boolean drank) {
        long t = Math.floorMod(dayTime, 24000L);
        if (drank && t >= LionGoals.WATER_FROM && t < DUSK_UNTIL) {
            return Math.floorDiv(dayTime, 24000L) * 2;
        }
        if (t >= DAWN_FROM || t < DAWN_UNTIL) {
            return Math.floorDiv(dayTime + 1000L, 24000L) * 2 + 1;
        }
        return null;
    }

    /** The pride's leader: the first lioness in its order (the elder while she lives); null if none. */
    @Nullable
    static LionEntity leader(LionEntity l) {
        LionEntity best = l.form() == LionEntity.Form.LIONESS ? l : null;
        for (LionEntity m : l.mates()) {
            if (m.form() == LionEntity.Form.LIONESS && m.isAlive() && (best == null || LionEntity.ORDER.compare(m, best) < 0)) {
                best = m;
            }
        }
        return best;
    }

    /** The prey for a try: in the nearest herd to the den, a calf or teen, else a grown one from a herd big enough. */
    @Nullable
    static BuffaloEntity pickPrey(LionEntity leader) {
        if (!(leader.level() instanceof ServerLevel level) || leader.den() == null) {
            return null;
        }
        Vec3 den = Vec3.atBottomCenterOf(leader.den());
        List<BuffaloEntity> all = level.getEntitiesOfClass(BuffaloEntity.class, new AABB(leader.den()).inflate(HUNT_REACH, 24.0, HUNT_REACH),
                b -> b.isAlive() && level.isPositionEntityTicking(b.blockPosition()));
        BuffaloEntity nearest = null;
        for (BuffaloEntity b : all) {
            if (nearest == null || b.distanceToSqr(den) < nearest.distanceToSqr(den)) {
                nearest = b;
            }
        }
        if (nearest == null) {
            return null;
        }
        List<BuffaloEntity> herd = new ArrayList<>();
        for (BuffaloEntity b : all) {
            if (b == nearest || nearest.home() != null && nearest.home().equals(b.home())) {
                herd.add(b);
            }
        }
        BuffaloEntity young = null;
        int grown = 0;
        for (BuffaloEntity b : herd) {
            if (!b.grownUp()) {
                if (young == null || b.distanceToSqr(den) < young.distanceToSqr(den)) {
                    young = b;
                }
            } else {
                grown++;
            }
        }
        if (young != null) {
            return young;
        }
        if (grown < HERD_FLOOR) {
            return null;                                    // a small herd's grown buffalo are never taken
        }
        return herd.stream().filter(BuffaloEntity::grownUp)
                .min(Comparator.<BuffaloEntity>comparingDouble(b -> b.getHealth() / b.getMaxHealth()).thenComparingDouble(Entity::getBbWidth))
                .orElse(null);
    }

    /** Tests: it grips this prey at its rump now, as after a strike, in a fight of its own (no try, no pride needed). */
    public static void gripForTests(LionEntity l, BuffaloEntity prey) {
        State s = l.hunt;
        s.endHunt();
        s.prey = prey;
        s.preyId = prey.getUUID();
        s.tryKey = Long.MIN_VALUE + 1;
        s.to(Stage.FIGHT, l.tickCount);
        s.rushSince = l.tickCount;
        s.holding = true;
        s.holdSpot = BuffaloEntity.SPOTS.index("rump");
        prey.heldBy(l);
    }

    /** Tests: a grip at this spot (by its name), as above. */
    public static void gripForTests(LionEntity l, BuffaloEntity prey, String spot) {
        gripForTests(l, prey);
        l.hunt.holdSpot = BuffaloEntity.SPOTS.index(spot);
    }

    /**
     * Tests: it lets go of its prey and is out of the fight (as when hit or its hunt ends). Only letting go, still in the
     * fight and within a strike's reach, it would strike again at once (step 4).
     */
    public static void letGoForTests(LionEntity l) {
        l.hunt.endHunt();
    }

    // --- the hold: placed on the prey's body (the Motion Studio, phase 3) ---------------------------------------------

    /** Where a holder stands (its feet, flat: the height is the ground's) and its heading. */
    record Place(Vec3 at, float yaw) {
    }

    /**
     * The clip it holds this prey with at this spot now, by the prey's spot table's pairs (the Motion Studio phase 3,
     * step 3: one table, the prey's own clip and size): a standing cow bitten at its haunch and flanks, clamped at its
     * throat, gone down with as it is brought down, held down; a calf pinned. Null: it cannot hold it there.
     */
    @Nullable
    static String holdClip(LionEntity l, BuffaloEntity prey, int spot) {
        return BuffaloEntity.SPOTS.holdClip(l.form().clips, prey.clipName(), spot, prey.spotSize());
    }

    /** Its grip with that clip (its jaw at the bite), from its own spot table; null: it cannot hold the prey there. */
    @Nullable
    public static com.antcolonies.entity.body.Spots.Hold holdOf(LionEntity l, BuffaloEntity prey, int spot) {
        String clip = holdClip(l, prey, spot);
        return clip == null ? null : l.spots().hold(clip);
    }

    /** Where a lion holding this spot stands and faces now: its jaw at its bite on the spot, facing in along the spot's way out. */
    @Nullable
    static Place placeOn(LionEntity l, BuffaloEntity prey, int spot) {
        var hold = holdOf(l, prey, spot);
        if (hold == null || spot < 0 || spot >= BuffaloEntity.SPOTS.count()) {
            return null;
        }
        float yaw = BuffaloEntity.SPOTS.holderYaw(spot, prey.yBodyRot);
        return new Place(com.antcolonies.entity.body.Spots.holderAt(prey.spotAt(spot), yaw, hold, l.getScale(), l.measures.drawAheadPx()), yaw);
    }

    /** The top of the ground at this spot near the prey's feet (a block over them to two under), NaN if none. */
    static double groundAt(net.minecraft.world.level.Level level, double x, double z, double nearY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Mth.floor(nearY + 1.0);
        for (int y = top; y >= top - 3; y--) {
            p.set(Mth.floor(x), y, Mth.floor(z));
            var shape = level.getBlockState(p).getCollisionShape(level, p);
            if (!shape.isEmpty()) {
                return y + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    /**
     * The free spot on the prey nearest it (the prey's spot table's index), or -1: none. A spot is free while no other
     * holder (of any pride) has it and a lion has room to stand there. Holders at two spots never stand through each other
     * (BUGS 377): the spots lie far enough apart, checked offline for every pair ({@code tools/spots.py report}).
     */
    public static int freeSpot(LionEntity l, BuffaloEntity prey) {
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < BuffaloEntity.SPOTS.count(); i++) {
            Place p = placeOn(l, prey, i);
            if (p == null || taken(l, prey, i) || !roomAt(l, prey, p)) {
                continue;
            }
            double gap = HomeGroup.flat(l.position(), p.at().x, p.at().z);
            if (gap < bestGap) {
                best = i;
                bestGap = gap;
            }
        }
        return best;
    }

    /** Whether another holder has this spot. */
    private static boolean taken(LionEntity l, BuffaloEntity prey, int spot) {
        for (Entity e : prey.getPassengers()) {
            if (e instanceof LionEntity m && m != l && m.holdSpot() == spot) {
                return true;
            }
        }
        return false;
    }

    /** Whether a lion has room to stand at a place: ground under it and no block in its body there. */
    static boolean roomAt(LionEntity l, BuffaloEntity prey, Place p) {
        return roomAt(l, prey.getY(), p);
    }

    /** The same, by the height its place is near (a carcass's). */
    static boolean roomAt(LionEntity l, double nearY, Place p) {
        double y = groundAt(l.level(), p.at().x, p.at().z, nearY);
        return !Double.isNaN(y) && l.level().noBlockCollision(l,
                l.getBoundingBox().move(p.at().x - l.getX(), y - l.getY(), p.at().z - l.getZ()).deflate(0.05));
    }

    /**
     * Places a lion on its prey (the prey's {@code positionRider}, on the server and on every screen alike): at its spot,
     * its feet on the ground there. On a strike (step 4) it rides from the strike's start: each tick it is the strike's
     * share of the way (its clip's own travel, from its spot table) from where it set off to its place at the spot, so it
     * stands through its coil, flies, and lands where it aimed at take-off (where its place would be, the prey going on as
     * it went; it never steers in the air), gripping if its place is still near (then eased onto it), else missing; its
     * heading turning with it; after any other grip it settles there over its blend. False when it has no spot to be
     * placed on (not synced to this screen yet).
     */
    static boolean ride(LionEntity l, BuffaloEntity prey, Entity.MoveFunction move) {
        Place p = placeOn(l, prey, l.holdSpot());
        if (p == null) {
            return false;
        }
        var strike = l.strike();
        if (l.settle == null) {
            l.settle = com.antcolonies.entity.body.Strikes.Ride.from(l, l.yBodyRot, strike, l.doingFor());
        }
        double f = l.settle.along(strike, l.doingFor(), LionEntity.BLEND);
        Vec3 preyStep = new Vec3(prey.getX() - prey.xo, 0.0, prey.getZ() - prey.zo);
        if (l.level().isClientSide) {
            l.settle.told(l.strikeWord());                       // hit or miss is the server's word (it sees the prey as it is)
        }
        Vec3 at = l.settle.at(f, l.settle.target(strike, l.doingFor(), f, p.at(), preyStep, l.getScale(), LionEntity.BLEND));
        if (!l.level().isClientSide) {
            l.strikeWord(l.settle.word());
        }
        double ground = groundAt(l.level(), at.x, at.z, prey.getY());
        Placement.seat(l, move, at.x, Double.isNaN(ground) ? l.getY() : ground, at.z, l.settle.yaw(f, p.yaw()));
        return true;
    }

    // --- the feast: lying at the carcass's feeding spots (the Motion Studio, phase 3, step 5) --------------------------

    /** A lion counts as at a carcass within this of it (blocks): only then it holds a feeding spot. */
    static double atCarcass(RemainsEntity carcass) {
        return 1.9 * carcass.size() + 3.0;
    }

    /** Its pair with a carcass (the buffalo's spot table: its feeding spots there, the best first, and its clip); null: none. */
    @Nullable
    static com.antcolonies.entity.body.Spots.Pair feedPair(LionEntity l, RemainsEntity carcass) {
        return BuffaloEntity.SPOTS.pairFor(l.form().clips, RemainsEntity.CARCASS_POSE, BuffaloEntity.MODEL_SCALE * carcass.size());
    }

    /** Where a lion feeding at this spot lies and faces: its jaw at its feed clip's bite on the spot, facing in. */
    @Nullable
    public static Place feedPlace(LionEntity l, RemainsEntity carcass, int spot) {
        var pair = feedPair(l, carcass);
        var hold = pair == null ? null : l.spots().hold(pair.hold());
        if (hold == null || spot < 0 || spot >= BuffaloEntity.SPOTS.count()) {
            return null;
        }
        float yaw = BuffaloEntity.SPOTS.holderYaw(spot, carcass.getYRot());
        return new Place(com.antcolonies.entity.body.Spots.holderAt(carcass.spotAt(spot), yaw, hold, l.getScale(), l.measures.drawAheadPx()), yaw);
    }

    /** The feast order's kinds: males, lionesses, cubs. */
    private static int feastKind(LionEntity l) {
        return l.form().ordinal();
    }

    /**
     * The pride mate that has this feeding spot before it: one of its own kind or of a kind before it in the feast order
     * (males, then lionesses, then cubs), still feeding. Null: none (one after it has it at most: pushed off).
     */
    @Nullable
    static LionEntity feederAt(LionEntity l, RemainsEntity carcass, int spot) {
        for (LionEntity m : l.mates()) {
            if (m != l && m.hunt.stage == Stage.FEAST && m.hunt.carcass == carcass && !m.hunt.full && m.hunt.feedSpot == spot
                    && feastKind(m) <= feastKind(l)) {
                return m;
            }
        }
        return null;
    }

    /** Whether a feeding spot is free for it: none before it has it, and it has room to lie at its place there. */
    static boolean freeToFeed(LionEntity l, RemainsEntity carcass, int spot) {
        Place p = feedPlace(l, carcass, spot);
        return p != null && feederAt(l, carcass, spot) == null && roomAt(l, carcass.getY(), p);
    }

    /** The best feeding spot free for it (its pair's order), or -1. */
    static int bestFeedSpot(LionEntity l, RemainsEntity carcass) {
        var pair = feedPair(l, carcass);
        if (pair == null) {
            return -1;
        }
        for (String name : pair.spots()) {
            int spot = BuffaloEntity.SPOTS.index(name);
            if (freeToFeed(l, carcass, spot)) {
                return spot;
            }
        }
        return -1;
    }

    /**
     * Tests: how far a lion set onto its feeding place is from it (blocks, flat), its heading's fault added (a degree
     * counts as a hundredth of a block); -1: it has no place, or lay down where it got to.
     */
    public static double feedOffForTests(LionEntity l) {
        Place p = l.hunt.carcass == null || l.hunt.feedSettle == null ? null : feedPlace(l, l.hunt.carcass, l.hunt.feedSpot);
        return p == null ? -1 : HomeGroup.flat(l.position(), p.at().x, p.at().z)
                + Math.abs(net.minecraft.util.Mth.wrapDegrees(l.yBodyRot - p.yaw())) / 100.0;
    }

    /** Tests: what {@link #feedOffForTests} is made of: its distance from its feeding place and its heading's fault. */
    public static String feedOffAbout(LionEntity l) {
        Place p = l.hunt.carcass == null ? null : feedPlace(l, l.hunt.carcass, l.hunt.feedSpot);
        return p == null ? "no place" : String.format("%.3f blocks, body %.1f deg, head %.1f deg, speed %.3f",
                HomeGroup.flat(l.position(), p.at().x, p.at().z), net.minecraft.util.Mth.wrapDegrees(l.yBodyRot - p.yaw()),
                net.minecraft.util.Mth.wrapDegrees(l.yHeadRot - p.yaw()), l.getDeltaMovement().horizontalDistance());
    }

    /** Tests: the feeding spot it has (its name), or null. */
    @Nullable
    public static String feedSpotForTests(LionEntity l) {
        return l.hunt.feedSpot < 0 ? null : BuffaloEntity.SPOTS.name(l.hunt.feedSpot);
    }

    // --- the strike: it carries itself (the Motion Studio, phase 3, step 4) ------------------------------------------

    /**
     * The spot a strike would aim at now: the free spot on the prey nearest it, if its place there, where that will be
     * when it lands (the prey going on as it goes now), is within the strike's reach (its clip's own travel at its size).
     * One rule for both strikes: the leap from the stalk (still through its coil) and the smack out of a gallop (on the
     * move from its first tick). -1: no strike now.
     */
    static int strikeSpot(LionEntity l, BuffaloEntity prey, byte doing) {
        var strike = l.strikeOf(doing);
        int spot = strike == null ? -1 : freeSpot(l, prey);
        Place p = spot < 0 ? null : placeOn(l, prey, spot);
        if (p == null) {
            return -1;
        }
        Vec3 moved = new Vec3(prey.getX() - prey.xo, 0.0, prey.getZ() - prey.zo);
        return com.antcolonies.entity.body.Strikes.inReach(l.position(), p.at(), moved, strike, l.getScale()) ? spot : -1;
    }

    /**
     * It sets off on a strike at a spot: it rides the prey from now, placed along the strike's own travel from where it
     * stands to its place at the spot ({@link #ride}); from the strike's hit it grips there ({@code hold}).
     */
    static void setOff(LionEntity l, BuffaloEntity prey, byte doing, int spot) {
        State s = l.hunt;
        l.getNavigation().stop();
        l.setDoing(doing);                                      // (a new ride from here: LionEntity.newStrike)
        s.holdSpot = spot;
        s.holding = true;
        s.struck = false;
        if (l.getVehicle() != prey && !l.startRiding(prey, true)) {
            s.holding = false;
            return;
        }
        l.setHoldSpot(spot);
    }

    /** Tests: where it stands to hold its spot on this prey now (flat), or null. */
    @Nullable
    public static Vec3 placeForTests(LionEntity l, BuffaloEntity prey) {
        Place p = placeOn(l, prey, l.holdSpot());
        return p == null ? null : p.at();
    }

    /**
     * Tests: a strike (its doing) at this prey, in a fight of its own (no try, no pride needed), if its place on the prey
     * is within the strike's reach now (the game's own rule); false: not in reach.
     */
    public static boolean strikeForTests(LionEntity l, BuffaloEntity prey, byte doing) {
        State s = l.hunt;
        s.endHunt();
        s.prey = prey;
        s.preyId = prey.getUUID();
        s.tryKey = Long.MIN_VALUE + 1;
        s.to(Stage.FIGHT, l.tickCount);
        s.rushSince = l.tickCount;
        int spot = strikeSpot(l, prey, doing);
        if (spot < 0) {
            return false;
        }
        setOff(l, prey, doing, spot);
        return true;
    }

    /** The goal: every lion has one (the hunters, the waiters and the feasters). */
    static final class HuntGoal extends Goal {
        private final LionEntity l;
        /** The lion's hunt state: read at the first {@link #canUse} (goals are made in the mob's constructor, before its fields). */
        private State s;
        private int repath;

        HuntGoal(LionEntity l) {
            this.l = l;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (s == null) {
                s = l.hunt;
            }
            if (l.led || l.den() == null || !l.isAlive() || !(l.level() instanceof ServerLevel)) {
                return false;
            }
            decide();
            return s.stage != Stage.NONE || backingOff();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            l.getNavigation().stop();
            byte d = l.doing();
            if (d == LionEntity.STALK || d == LionEntity.FREEZE) {
                l.setDoing(LionEntity.CALM);
            }
            s.holding = false;
        }

        private boolean backingOff() {
            return l.tickCount - s.buffaloHit < BACK_OFF_TICKS;
        }

        // --- deciding its stage ------------------------------------------------------------------------------------------

        /** Works out its stage this tick (each from what it sees: the leader's try and pick, its mates' kills). */
        private void decide() {
            int now = l.tickCount;
            long time = Colony.dayTimeAt(l.level(), l.den());
            // a feast: its own, or a mate's kill
            if (s.stage == Stage.FEAST) {
                if (feastGoesOn(time)) {
                    return;
                }
                s.stage = Stage.NONE;
                if (s.carcass == null || s.carcass.getRemovalReason() != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
                    s.feastId = null;                           // over (its land only unloaded: it goes back when it loads)
                }
                s.carcass = null;
                s.killAt = null;
            }
            // a feast from before a reload (or its land unloaded): back to it once the carcass is there, on its feast's day
            if (s.stage == Stage.NONE && s.feastId != null) {
                if (LionGoals.eveningAt(time) != s.feastEvening) {
                    s.feastId = null;
                } else if (carcass() != null) {
                    s.to(Stage.FEAST, now);
                    s.lastBite = now;
                    s.arriveBy = now + ARRIVE_TICKS;
                    s.feedSpot = -1;
                    s.lyingAt = false;
                    standUp();
                    return;
                }
            }
            // its prey dead: the kill (before any end of the try); gone or in land that does not tick: lost
            if (s.hunting() && s.prey != null) {
                if (s.prey.isDeadOrDying()) {
                    Vec3 at = s.prey.position();
                    s.endHunt();
                    l.getNavigation().stop();
                    s.killAt = at;                              // it stands by while the death plays, then feeds
                    s.to(Stage.WAIT, now);
                    s.waitUntil = now + KILL_WAIT;
                } else if (s.prey.isRemoved() || !((ServerLevel) l.level()).isPositionEntityTicking(s.prey.blockPosition())) {
                    endTry(now);
                }
            }
            if (now - s.lookedAt >= LOOK_EVERY) {
                s.lookedAt = now;
                RemainsEntity kill = s.sinceMeal >= REJOIN ? findKill() : null;
                if (kill != null && kill.bitesLeft() > 0) {
                    joinFeast(kill, time, now);
                    return;
                }
            }
            if (s.stage == Stage.WAIT && now >= s.waitUntil) {
                s.killAt = null;                                // no carcass came, or its lie-up is over
                s.stage = Stage.NONE;
            }
            LionEntity leader = leader(l);
            Long key = leader == null ? null
                    : tryAt(time, l.act() == LionGoals.Act.NIGHT && l.drankEvening() == LionGoals.eveningAt(time));   // dusk once the pride has drunk
            if (key == null) {
                if (s.hunting() && s.stage != Stage.FIGHT && l.doing() != LionEntity.LEAP_BITE) {
                    endTry(now);                                // the window over: a rush (or a leap in the air) runs on to its end
                }
                return;
            }
            if (leader == l) {
                if (s.tryKey != key && !s.hunting() && s.sinceMeal >= HUNGRY && l.temper() == LionGoals.Temper.NONE) {
                    s.tryKey = key;                             // one try a window, whatever comes of it
                    BuffaloEntity prey = pickPrey(l);
                    if (prey != null) {
                        startHunt(prey, now);
                    }
                }
                return;
            }
            boolean prideHunting = leader.hunt.hunting() && leader.hunt.tryKey == key && leader.hunt.prey != null;
            if (l.form() == LionEntity.Form.LIONESS) {
                if (prideHunting && s.tryKey != key) {
                    s.tryKey = key;
                    startHunt(leader.hunt.prey, now);           // joining the leader's hunt: her pick
                } else if (s.hunting() && s.stage != Stage.FIGHT && s.tryKey == leader.hunt.tryKey && !leader.hunt.hunting()
                        && leader.hunt.killAt == null && leader.hunt.stage != Stage.FEAST) {
                    endTry(now);                                // the leader's try is over (not her kill: that calls it to feed)
                }
                return;
            }
            // the male and the cubs: lie where they are while the lionesses hunt (and a while after)
            if (prideHunting) {
                if (s.stage != Stage.WAIT) {
                    s.to(Stage.WAIT, now);
                    s.killAt = null;
                }
                s.waitUntil = now + LIE_UP;
            }
        }

        private void startHunt(BuffaloEntity prey, int now) {
            s.endHunt();
            s.prey = prey;
            s.preyId = prey.getUUID();
            s.to(Stage.SPREAD, now);
            l.getNavigation().stop();
            standUp();
        }

        /** The try is over for it: it lies down where it is for a while ({@link #LIE_UP}). */
        private void endTry(int now) {
            s.endHunt();
            l.getNavigation().stop();
            byte d = l.doing();
            if (d == LionEntity.STALK || d == LionEntity.FREEZE || d == LionEntity.CALM) {
                l.toward(LionEntity.REST);
            }
            s.to(Stage.WAIT, now);
            s.killAt = null;
            s.waitUntil = now + LIE_UP;
        }

        /** To a kill to feed: on its feet, its evening to lie by it the first feaster's (one day for the whole pride). */
        private void joinFeast(RemainsEntity kill, long time, int now) {
            s.endHunt();
            s.carcass = kill;
            s.feastId = kill.getUUID();
            s.ate = 0;
            s.full = false;
            s.feedSpot = -1;
            s.lyingAt = false;
            s.lastBite = now;
            s.arriveBy = now + ARRIVE_TICKS;
            s.feastEvening = LionGoals.eveningAt(time);
            for (LionEntity m : l.mates()) {
                if (m.hunt.stage == Stage.FEAST && kill.getUUID().equals(m.hunt.feastId)) {
                    s.feastEvening = m.hunt.feastEvening;
                    break;
                }
            }
            s.to(Stage.FEAST, now);
            l.getNavigation().stop();
            standUp();
        }

        /**
         * Its pride's kill: a buffalo carcass of its den near where a hunter killed, by a mate feeding, or near itself (null:
         * none). The last finds its own kill again after a reload (the carcass keeps its den).
         */
        @Nullable
        private RemainsEntity findKill() {
            List<AABB> boxes = new ArrayList<>();
            boxes.add(l.getBoundingBox().inflate(OWN_KILL_REACH, 8.0, OWN_KILL_REACH));
            if (s.killAt != null) {
                boxes.add(new AABB(s.killAt, s.killAt).inflate(CARCASS_NEAR));
            }
            for (LionEntity m : l.mates()) {
                if (m.hunt.killAt != null) {
                    boxes.add(new AABB(m.hunt.killAt, m.hunt.killAt).inflate(CARCASS_NEAR));
                }
                if (m.hunt.carcass != null && m.hunt.carcass.isAlive()) {
                    boxes.add(m.hunt.carcass.getBoundingBox().inflate(CARCASS_NEAR));
                }
            }
            for (AABB box : boxes) {
                for (RemainsEntity r : l.level().getEntitiesOfClass(RemainsEntity.class, box,
                        r -> r.isAlive() && r.kind() == RemainsEntity.Kind.BUFFALO && r.bitesLeft() > 0 && l.den().equals(r.killedBy()))) {
                    return r;
                }
            }
            return null;
        }

        /** Its feast's carcass, looked up again by its id when the one it knew was unloaded; null if not there. */
        @Nullable
        private RemainsEntity carcass() {
            if (s.carcass != null && s.carcass.isAlive()) {
                return s.carcass;
            }
            if (s.feastId != null && l.level() instanceof ServerLevel level && level.getEntity(s.feastId) instanceof RemainsEntity r && r.isAlive()) {
                s.carcass = r;
                return r;
            }
            return null;
        }

        private boolean feastGoesOn(long time) {
            if (carcass() == null) {
                return false;
            }
            if (!s.full) {
                if (s.carcass.bitesLeft() > 0) {
                    return true;
                }
                if (s.ate <= 0) {
                    return false;                               // the meat gone before its first bite: still hungry
                }
                eaten();                                        // the meat gone: what it got is its meal
            }
            return LionGoals.eveningAt(time) == s.feastEvening;   // full: it lies by the kill until 16:00 the next day
        }

        // --- what it does ------------------------------------------------------------------------------------------------

        @Override
        public void tick() {
            int now = l.tickCount;
            if (backingOff()) {
                backOff();
                return;
            }
            switch (s.stage) {
                case SPREAD -> spread(now);
                case STALK -> stalk(now);
                case FIGHT -> fight(now);
                case WAIT -> {
                    if (s.killAt != null) {
                        l.getNavigation().stop();
                        standUp();
                    } else {
                        lieDown();
                    }
                }
                case FEAST -> feast(now);
                default -> {
                }
            }
        }

        /** Hurt by a buffalo: it gallops to {@link #BACK_OFF} from it, then stands facing it till the back-off is over. */
        private void backOff() {
            Entity from = s.buffaloHitId != null && l.level() instanceof ServerLevel level ? level.getEntity(s.buffaloHitId) : null;
            if (from == null || !from.isAlive()) {
                s.buffaloHit = -1000;                           // gone: nothing to back off from
                return;
            }
            Vec3 away = l.position().subtract(from.position());
            double len = Math.sqrt(away.x * away.x + away.z * away.z);
            standUp();
            l.getNavigation().stop();
            if (len >= BACK_OFF || len < 1.0E-3) {
                l.face(from.getX(), from.getZ(), 10.0F);
                return;
            }
            Vec3 to = from.position().add(away.x / len * BACK_OFF, 0, away.z / len * BACK_OFF);
            l.getMoveControl().setWantedPosition(to.x, l.getY(), to.z, LionEntity.GALLOP);
        }

        private void standUp() {
            byte d = l.doing();
            if (d == LionEntity.STALK || d == LionEntity.FREEZE || d == LionEntity.MOOD_ALERT || d == LionEntity.MOOD_ANGRY) {
                l.setDoing(LionEntity.CALM);
            }
        }

        /** Whether it is on its feet, getting up first from lying or sitting (its clip map's way up; a sniff or a stare ends). */
        private boolean onFeet() {
            if (!l.upright()) {
                return false;
            }
            byte d = l.doing();
            if (d == LionEntity.MOOD_FLEHMEN || d == LionEntity.MOOD_ALERT) {
                l.setDoing(LionEntity.CALM);
            }
            return true;
        }

        @Nullable
        private BuffaloEntity prey() {
            if (s.prey == null || !s.prey.isAlive()) {
                return null;
            }
            return s.prey;
        }

        /** The hunters of its pride on this try, in the pride's order (itself among them). */
        private List<LionEntity> hunters() {
            List<LionEntity> out = new ArrayList<>();
            out.add(l);
            for (LionEntity m : l.mates()) {
                if (m.hunt.hunting() && m.hunt.tryKey == s.tryKey) {
                    out.add(m);
                }
            }
            out.sort(LionEntity.ORDER);
            return out;
        }

        // SPREAD: to its own start spot, fanned round the prey on the den's side
        private void spread(int now) {
            BuffaloEntity prey = prey();
            if (prey == null) {
                return;                                         // its kill or its loss is worked out in decide
            }
            if (herdAlarmed(prey) || matesFighting()) {
                toFight(now);                                   // seen, or a mate rushes: all rush
                return;
            }
            if (!onFeet()) {
                return;
            }
            List<LionEntity> hunters = hunters();
            boolean allReady = true;
            for (LionEntity h : hunters) {
                allReady &= h == l ? s.ready : h.hunt.ready || h.hunt.stage != Stage.SPREAD;
            }
            if (allReady || now - s.stageSince > SPREAD_TICKS) {
                l.getNavigation().stop();
                s.to(Stage.STALK, now);
                repath = 0;
                return;
            }
            if (--repath <= 0 || s.spot == null) {
                repath = 20;
                s.spot = startSpot(prey, hunters.indexOf(l));
                if (!s.ready) {
                    l.getNavigation().moveTo(s.spot.x, s.spot.y, s.spot.z, LionEntity.TROT);
                }
            }
            if (s.ready || HomeGroup.flat(l.position(), s.spot.x, s.spot.z) <= AT_START) {
                s.ready = true;                                 // there: it waits for its mates, facing the prey
                l.getNavigation().stop();
                l.face(prey.getX(), prey.getZ(), 6.0F);
            }
        }

        /** Its start spot: {@link #START_OUT} from the prey toward the den, turned {@link #FAN} degrees a rank each side. */
        private Vec3 startSpot(BuffaloEntity prey, int rank) {
            Vec3 den = Vec3.atBottomCenterOf(l.den());
            double ax = den.x - prey.getX(), az = den.z - prey.getZ(), a = Math.sqrt(ax * ax + az * az);
            double base = a < 1.0E-3 ? 0 : Math.atan2(az, ax);
            int side = rank % 2 == 1 ? 1 : -1, steps = (rank + 1) / 2;
            double angle = base + Math.toRadians(FAN) * steps * side;
            double x = prey.getX() + Math.cos(angle) * START_OUT, z = prey.getZ() + Math.sin(angle) * START_OUT;
            BlockPos cell = GroundTop.cell(l.level(), Mth.floor(x), Mth.floor(z));
            for (int i = 0; i < 6 && !l.level().getFluidState(cell.below()).isEmpty(); i++) {
                x += (prey.getX() - x) * 0.15;                  // water there: nearer the prey till dry
                z += (prey.getZ() - z) * 0.15;
                cell = GroundTop.cell(l.level(), Mth.floor(x), Mth.floor(z));
            }
            return new Vec3(x, cell.getY(), z);
        }

        // STALK: crouched at the prey, freezing while a head is up near it
        private void stalk(int now) {
            BuffaloEntity prey = prey();
            if (prey == null) {
                return;
            }
            if (!onFeet()) {
                return;
            }
            if (herdAlarmed(prey) || matesFighting() || now - s.stageSince > STALK_TICKS) {
                toFight(now);
                return;
            }
            if (strike(prey, LionEntity.LEAP_BITE, now)) {
                return;
            }
            // watched: any grown buffalo near it with its head up, or coming up soon (it stops a block outside their sight;
            // the same buffalo that would see it: any herd's)
            double reach = BuffaloEntity.LION_STALK_SEEN + FREEZE_MARGIN;
            boolean watched = false;
            for (BuffaloEntity b : l.level().getEntitiesOfClass(BuffaloEntity.class, l.getBoundingBox().inflate(reach, 4.0, reach), BuffaloEntity::isAlive)) {
                if (b.grownUp() && !b.headDownAWhile() && b.distanceTo(l) <= reach) {
                    watched = true;
                    break;
                }
            }
            if (watched && l.has(LionEntity.FREEZE) && (s.frozenSince < 0 || now - s.frozenSince < FREEZE_MOST)) {
                if (s.frozenSince < 0) {
                    s.frozenSince = now;
                }
                l.getNavigation().stop();
                l.getMoveControl().setWantedPosition(l.getX(), l.getY(), l.getZ(), 0.0);
                l.setDoing(LionEntity.FREEZE);
                repath = 0;                                     // on again at once when the heads go down
                return;
            }
            if (!watched) {
                s.frozenSince = -1;
            }
            l.setDoing(LionEntity.STALK);
            if (--repath <= 0) {
                repath = 10;
                l.getNavigation().moveTo(prey, STALK_PACE);
            }
        }

        /** Whether the prey's herd runs (one of them saw a hunter). */
        private static boolean herdAlarmed(BuffaloEntity prey) {
            if (prey.temper() == BuffaloGoals.Temper.FLEE) {
                return true;
            }
            for (BuffaloEntity b : prey.mates()) {
                if (b.temper() == BuffaloGoals.Temper.FLEE) {
                    return true;
                }
            }
            return false;
        }

        private boolean matesFighting() {
            for (LionEntity m : l.mates()) {
                if (m.hunt.stage == Stage.FIGHT && m.hunt.tryKey == s.tryKey) {
                    return true;
                }
            }
            return false;
        }

        private void toFight(int now) {
            standUp();
            s.to(Stage.FIGHT, now);
            s.rushSince = now;
            s.frozenSince = -1;
            repath = 0;
        }

        // FIGHT: the rush, the strike, the hold
        private void fight(int now) {
            BuffaloEntity prey = prey();
            if (prey == null) {
                return;
            }
            if (s.holding) {
                s.rushSince = now;                              // gripping: no sprint is running
                hold(prey, now);                                // from the strike's hit on, the rest of its clip too
                return;
            }
            if (now - s.rushSince > RUSH_TICKS) {
                endTry(now);                                    // the sprint spent with no grip: it gives up
                return;
            }
            if (!onFeet() || l.striking()) {
                return;                                         // (a strike cut short plays out where it was let go)
            }
            if (strike(prey, LionEntity.JUMP_SMACK, now)) {
                return;
            }
            byte d = l.doing();
            if (d == LionEntity.STALK || d == LionEntity.FREEZE) {
                l.setDoing(LionEntity.CALM);
            }
            if (--repath <= 0) {
                repath = 4;
                l.getNavigation().moveTo(prey, LionEntity.GALLOP);
            }
        }

        /** A strike when its place on the prey is within the strike's reach ({@link #strikeSpot}); false: none now. */
        private boolean strike(BuffaloEntity prey, byte doing, int now) {
            int spot = strikeSpot(l, prey, doing);
            if (spot < 0) {
                return false;
            }
            if (s.stage == Stage.STALK) {
                toFight(now);                                   // the leap is the fight's start (the mates rush in)
            }
            setOff(l, prey, doing, spot);
            hold(prey, now);
            return true;
        }

        /**
         * One bite: its hit (past Minecraft's hit cooldown: every holder's bite counts; the killer's pride owns the body).
         * The prey is held, not shoved: no knockback (a pinned calf hopped 0.09 blocks away at every bite, its holder with it).
         */
        private void bite(BuffaloEntity prey) {
            prey.invulnerableTime = 0;
            Vec3 was = prey.getDeltaMovement();
            prey.hurt(l.damageSources().mobAttack(l), (float) l.getAttributeValue(Attributes.ATTACK_DAMAGE));
            prey.setDeltaMovement(was);
            prey.hurtMarked = false;
            l.playSound(net.minecraft.sounds.SoundEvents.CAT_HISS, 0.8F, 0.6F);
        }

        /**
         * Clinging to the prey at its spot, biting (a calf down: pinned). It rides the prey ({@link #ride} places it): its
         * box is never pushed, so its feet never skate while the prey stands still. It lets go when its place has no room
         * to stand (a tree, a drop: the prey pulled it there). Each bite starts at the last one's end, with no calm tick
         * between (BUGS 376); the hit is at the clip's tick in its body plan.
         */
        private void hold(BuffaloEntity prey, int now) {
            if (l.getVehicle() != prey && !l.startRiding(prey, true)) {
                s.holding = false;
                return;
            }
            l.setHoldSpot(s.holdSpot);
            Place p = placeOn(l, prey, s.holdSpot);
            if (p == null || !roomAt(l, prey, p) || !l.level().noBlockCollision(l, l.getBoundingBox().deflate(0.05))) {
                s.holding = false;                              // no room at its spot or where its strike took it: it lets go (and stands where it is)
                return;
            }
            var strike = l.strike();
            if (strike != null && !s.struck) {
                if (l.clipTick() < strike.hit()) {
                    return;                                     // still on its way: not gripping yet
                }
                s.struck = true;                                // the strike's hit: its first bite, and from now it grips
                if (!missed()) {
                    bite(prey);
                    if (!prey.grownUp()) {
                        prey.setDoing(BuffaloEntity.REST_DOWN);   // a calf is knocked down
                    }
                }
            }
            if (missed()) {
                if (!l.striking()) {
                    s.holding = false;                          // landed where it aimed, the prey gone from there: a miss
                }
                return;
            }
            prey.heldBy(l);
            String name = holdClip(l, prey, s.holdSpot);
            byte clip = name == null ? -1 : LionEntity.doingOf(name);
            if (clip < 0 || !l.has(clip)) {
                s.holding = false;                              // no hold of hers fits the prey there
                return;
            }
            if (!l.striking()) {
                // the clip that goes with the prey's own now, switched the tick the prey's changes (passengers tick after
                // their vehicle), so a clip made with the prey's (hold_sink) plays on its clock; a bite played out starts
                // again at once, with no calm tick between (BUGS 376)
                l.setDoing(clip);
            }
            var hold = l.spots().hold(name);
            if (hold != null && l.doing() == clip && hold.bitesAt(l.clipTick())) {
                bite(prey);
            }
        }

        /** Its strike landed away from its place on the prey (the ride decides it at the hit, on every screen alike). */
        private boolean missed() {
            return s.struck && l.settle != null && l.settle.missed();
        }

        // WAIT: the male and the cubs lie where they are while the lionesses hunt
        private void lieDown() {
            l.getNavigation().stop();
            byte d = l.doing();
            if (d == LionEntity.CALM || d == LionEntity.MOOD_FLEHMEN || d == LionEntity.MOOD_ALERT) {
                l.toward(LionEntity.REST);
            }
        }

        // FEAST: to a feeding spot of the carcass, lying there with its jaw on it, feeding till full, then lying by it
        private void feast(int now) {
            RemainsEntity carcass = s.carcass;
            if (carcass == null) {
                return;
            }
            if (s.full) {
                lieByKill(carcass, now);
                return;
            }
            byte d = l.doing();
            boolean at = HomeGroup.flat(l.position(), carcass.getX(), carcass.getZ()) <= atCarcass(carcass);
            if (!at) {
                s.feedSpot = -1;                                // (only those at it hold a spot: the male takes his once he comes)
            } else if (s.feedSpot < 0 ? now - s.lookedAt >= LOOK_EVERY : !freeToFeed(l, carcass, s.feedSpot)) {
                // it keeps its spot while it may have it; with none, or pushed off it by one before it in the feast
                // order (a growl), the best spot free for it
                boolean pushed = s.feedSpot >= 0 && feederAt(l, carcass, s.feedSpot) != null;
                s.lookedAt = now;
                s.feedSpot = bestFeedSpot(l, carcass);
                s.lyingAt = false;
                s.arriveBy = now + ARRIVE_TICKS;
                if (pushed) {
                    s.growlUntil = now + GROWL_TICKS + (d == LionEntity.FEED || d == LionEntity.REST || d == LionEntity.REST_DOWN
                            ? l.doingTicks(LionEntity.REST_UP) : 0);   // (it gets up first)
                }
            }
            Place place = s.feedSpot < 0 ? null : feedPlace(l, carcass, s.feedSpot);
            if (s.lyingAt && place != null && (d == LionEntity.REST_DOWN || d == LionEntity.REST || d == LionEntity.FEED)) {
                feedLying(carcass, place, now);
                return;
            }
            s.lyingAt = false;
            if (!onFeet()) {
                return;                                         // (lying elsewhere, or pushed off its spot: up first)
            }
            if (now < s.growlUntil) {
                l.getNavigation().stop();
                if (d == LionEntity.CALM) {
                    l.setDoing(LionEntity.MOOD_ANGRY);
                }
                l.face(carcass.getX(), carcass.getZ(), 10.0F);
                return;
            }
            if (d == LionEntity.MOOD_ANGRY) {
                l.setDoing(LionEntity.CALM);
            }
            if (place == null) {
                if (at) {
                    l.getNavigation().stop();                   // no spot free for it: it waits by the carcass, watching
                    l.faceYaw(yawTo(l.position(), carcass.position()), 10.0F);
                } else {
                    walkTo(carcass.position(), HomeGroup.flat(l.position(), carcass.getX(), carcass.getZ()) > 12.0 ? LionEntity.TROT : LionEntity.WALK);
                }
                return;
            }
            boolean there = HomeGroup.flat(l.position(), place.at().x, place.at().z) <= AT_PLACE;
            if (!there && now <= s.arriveBy) {
                walkTo(place.at(), LionEntity.WALK);
                return;
            }
            l.getNavigation().stop();
            if (!l.faceYaw(there ? place.yaw() : yawTo(l.position(), carcass.position()), 10.0F)) {
                return;
            }
            // it lies down at its place, shuffling the last bit onto it as it goes down (its place out of reach by its
            // deadline: it lies down where it got to and feeds there)
            s.lyingAt = true;
            s.feedSettle = there ? com.antcolonies.entity.body.Strikes.Ride.from(l, l.yBodyRot, null, 0) : null;
            l.toward(LionEntity.FEED);                          // (lying down first: the clip map's way)
        }

        /**
         * Lying at its feeding spot: set onto its place as it lies down (so its jaw at its bite is on the spot; placed, the
         * framework's {@link Placement}: nothing pushes it off, nothing of its walk moves it on), then the
         * feed clip with no calm tick before it or between its rounds (BUGS 376), a meat bite at the clip's own bite, the
         * tug, once {@link #BITE_EVERY} has passed.
         */
        private void feedLying(RemainsEntity carcass, Place place, int now) {
            Vec3 to = l.position();
            float yaw = l.yBodyRot;
            if (s.feedSettle != null) {
                double f = s.feedSettle.along(null, 0, l.doingTicks(LionEntity.REST_DOWN));
                to = s.feedSettle.at(f, place.at());
                yaw = s.feedSettle.yaw(f, place.yaw());
            }
            l.placement.place(to.x, to.z, yaw);           // (lying where it got to: there; on what is under it)
            if (!l.toward(LionEntity.FEED)) {
                return;                                         // (lying down still: the feed the tick it is down, no rest between)
            }
            var bite = l.spots().hold(FEED_CLIP);
            if (bite == null || !bite.bitesAt(l.clipTick()) || now - s.lastBite < BITE_EVERY) {
                return;
            }
            s.lastBite = now;
            if (carcass.bite(l)) {
                s.ate++;
            }
            if (s.ate >= fill() || carcass.bitesLeft() <= 0 && s.ate > 0) {
                eaten();                                        // (the meat gone before its first bite: still hungry)
            }
        }

        /**
         * To a place: by a route while far, then straight onto it (a route ends on the place's block, up to a block from the
         * point itself).
         */
        private void walkTo(Vec3 place, double pace) {
            if (HomeGroup.flat(l.position(), place.x, place.z) > STEP_IN) {
                if (--repath <= 0) {
                    repath = 10;
                    l.getNavigation().moveTo(place.x, place.y, place.z, pace);
                }
                return;
            }
            l.getNavigation().stop();
            l.getMoveControl().setWantedPosition(place.x, l.getY(), place.z, LionEntity.WALK);
        }

        /** Its meal is over (its share, or the meat gone after a bite or more): not hungry for two days, it lies by the kill. */
        private void eaten() {
            s.full = true;
            s.sinceMeal = 0;
            s.feedSpot = -1;                                    // its spot is free for the next
            s.lyingAt = false;
            s.arriveBy = l.tickCount + ARRIVE_TICKS + l.doingTicks(LionEntity.REST_UP);
            onFeet();                                           // done tearing at it: up from its spot
        }

        private int fill() {
            return switch (l.form()) {
                case MALE -> FILL_MALE;
                case LIONESS -> FILL_LIONESS;
                case CUB -> FILL_CUB;
            };
        }

        /** Full: it lies a few blocks from the kill (by its rank), facing it. */
        private void lieByKill(RemainsEntity carcass, int now) {
            byte d = l.doing();
            if (d == LionEntity.FEED) {
                onFeet();                                       // (full since before a reload: up from its spot)
                return;
            }
            if (l.lying()) {
                return;                                         // (lying by it, or still getting up from its feeding spot)
            }
            Vec3 spot = carcass.position().add(HomeGroup.offset(l.rank(), LIE_OUT + l.rank() * 0.6));
            if (HomeGroup.flat(l.position(), spot.x, spot.z) > 1.0 && now <= s.arriveBy) {   // out of reach: it lies where it got to
                if (d == LionEntity.MOOD_ANGRY) {
                    l.setDoing(LionEntity.CALM);
                }
                walkTo(spot, LionEntity.WALK);
                return;
            }
            l.getNavigation().stop();
            if (d == LionEntity.CALM && l.faceYaw(yawTo(l.position(), carcass.position()), 10.0F)) {
                l.setDoing(LionEntity.REST_DOWN);
            }
        }
    }

    /** The feast order: males first (by id), then the lionesses in the pride's order, the cubs last. */
    static final Comparator<LionEntity> FEAST_ORDER = Comparator.<LionEntity>comparingInt(m -> switch (m.form()) {
        case MALE -> 0;
        case LIONESS -> 1;
        case CUB -> 2;
    }).thenComparing(LionEntity.ORDER);

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0F;
    }
}
