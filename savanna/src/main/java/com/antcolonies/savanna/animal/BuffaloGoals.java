package com.antcolonies.savanna.animal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.antcolonies.animal.Attention;
import com.antcolonies.colony.Colony;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The Cape buffalo's mind ({@link BuffaloEntity}; {@code docs/plans/buffalo.md}, step 1, 2026-11-25; fun first, inspired by
 * real buffalo, every number my pick unless said). Three goals, by weight: Minecraft's float goal (in deep water it swims,
 * floating low); its temper ({@link TemperGoal}: watch, warn, charge, calm, and the herd's answer to one of them hurt);
 * its day ({@link DayGoal}: grazing in the cool hours, drinking at its pan and lying down by it at midday, lying down at
 * night). Both read the one shared day clock ({@link Colony#dayTimeAt}), never the world's sun or weather. Each goal only
 * says where to walk and at what pace (Minecraft's own routes, as the hippo's), and starts the doings that have clips of
 * their own; a clip's moments (a crop, a hit) are counted from the buffalo's own tick, and both goals run every tick
 * (stepped every other tick they missed moments and ran their timers at half speed, review R9-19).
 */
public final class BuffaloGoals {
    private BuffaloGoals() {
    }

    /** Its goals, by weight. */
    static void register(BuffaloEntity buffalo, GoalSelector goals) {
        goals.addGoal(0, new FloatGoal(buffalo));
        goals.addGoal(0, new SwimOutGoal(buffalo, BuffaloEntity.TRAVEL));
        goals.addGoal(1, new TemperGoal(buffalo));
        goals.addGoal(2, new DayGoal(buffalo));
        goals.addGoal(2, new CalfGoal(buffalo));
    }

    // --- its day's clock ---------------------------------------------------------------------------------------------------

    /** The parts of its day. */
    enum Act {
        /** 5:00 to 11:00 and 15:00 to 22:00: grazing in the cool hours. */
        GRAZE,
        /** 11:00 to 15:00: a drink at its pan, then lying down by the water in the heat. */
        MIDDAY,
        /** 22:00 to 5:00: lying down where the evening's grazing ended. */
        NIGHT
    }

    /** Where midday and the deep night begin and end (Minecraft's day time: 0 is 6:00, 1000 an hour). */
    static final int MIDDAY_FROM = 5000, MIDDAY_TO = 9000, NIGHT_FROM = 16000, NIGHT_TO = 23000;
    /** The herd's grazing place lies this far past its pan's middle and half its width, and up to this much farther (blocks). */
    static final double SPOT_NEAR = 8.0, SPOT_SPREAD = 14.0;
    /** Mixed into a grazing place's dice with the bout's number (any big odd number). */
    private static final long SPOT_DICE = 0x9E3779B97F4A7C15L;

    /** The part of the day at this day time. */
    static Act actAt(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t >= MIDDAY_FROM && t < MIDDAY_TO) {
            return Act.MIDDAY;
        }
        if (t >= NIGHT_FROM && t < NIGHT_TO) {
            return Act.NIGHT;
        }
        return Act.GRAZE;
    }

    /**
     * The bout's number at this day time: it goes up at 5:00 and at 15:00, so a morning's grazing and its midday share one
     * number, and an evening's grazing and its night the next.
     */
    public static long boutAt(long dayTime) {
        long t = dayTime + 1000;                // 5:00 is day time 23000, 15:00 is 9000
        return 2 * Math.floorDiv(t, 24000L) + (Math.floorMod(t, 24000L) >= 10000 ? 1 : 0);
    }

    /**
     * The middle of the herd's grazing place for a bout: from dice only (its home and the bout's number), an angle and a
     * distance past the pan's ring, so every mate finds the same place whatever land is loaded, without reading the world.
     */
    public static Vec3 grazeCentre(BlockPos home, int across, long bout) {
        return HomeGroup.dicePoint(home, bout, SPOT_DICE, across / 2.0 + SPOT_NEAR, SPOT_SPREAD);
    }

    /** A mate's own spot round a place: its order in the herd turned by the golden angle, this far out (blocks). */
    static Vec3 offset(int rank, double radius) {
        return HomeGroup.offset(rank, radius);
    }

    private static double flat(Vec3 a, double x, double z) {
        return HomeGroup.flat(a, x, z);
    }

    private static double flat(Vec3 a, BlockPos b) {
        return HomeGroup.flat(a, b);
    }

    // --- its day -----------------------------------------------------------------------------------------------------------

    /** Each mate's spot is this far from the place's middle: grazing, lying down (blocks). */
    static final double GRAZE_RADIUS = 6.0, LIE_RADIUS = 3.0;
    /** Its stand is the nearest dry grass ground this near its spot (blocks). */
    static final double STAND_REACH = 4.0;
    /** Mates' stands keep this far apart, middle to middle (blocks; a body is 3.8 long and 1.6 wide). */
    public static final double SPACE = 3.5;
    /** A stand's ground is at most this far over or under its home's (blocks). */
    static final int GROUND_DY = 8;
    /** Mates set off for a new place one after another, this far apart in their herd's order (ticks). */
    static final int DEPART_GAP = 40;
    /** More than this far behind its herd (blocks) it trots to catch up. */
    static final double TROT_BEHIND = 8.0;
    /** After a place it could not reach it waits this long before trying again (ticks; review R9-16); land not loaded, this long. */
    static final int UNREACHABLE_WAIT = 600, UNLOADED_WAIT = 40;
    /** Graze clips on one stand before a step to the next plant. */
    static final int GRAZES_FEWEST = 2, GRAZES_MOST = 3;
    /** A grazing step goes to a plant this far from its jaws (blocks). */
    static final double STEP_LEAST = 1.0, STEP_MOST = 3.0;
    /** One gap between grazes in this many gets a little thing (an ear flutter, a shake, a snort), a bull's bellow one of those in this many. */
    static final int LITTLE_ONE_IN = 3, BELLOW_ONE_IN = 8;
    /** The plant at its jaws is cropped at this tick of the graze clip (its head down and chewing from about tick 13 to 64). */
    static final int GRAZE_BITE = 30;
    /** Drinks at the shore at midday. */
    static final int DRINKS_FEWEST = 1, DRINKS_MOST = 2;
    /** Lying long, it gets up now and then, every this many ticks (60 to 120 s). */
    static final int UP_SHORTEST = 1200, UP_LONGEST = 2400;
    /** At its stand: this near its middle, or this near with its route done (blocks). */
    static final double ARRIVE = 1.0, ARRIVE_DONE = 2.0;

    /**
     * Its whole day, one goal read from the shared clock. Its place for each part of the day: grazing, a place from dice
     * ({@link #grazeCentre}, new at every bout); at midday its own stand on its pan's shore with the water straight ahead;
     * at night, round where the evening's grazing was. Its own spot round the place by its order in the herd
     * ({@link #offset}), its stand the nearest dry grass ground ({@link Grazing#grassStand}) within {@link #STAND_REACH},
     * never within {@link #SPACE} of a mate's; with none, by its nearest mate.
     *
     * <p>A new part of the day: mates set off one after another ({@link #DEPART_GAP}), a grazing one grazing on and a lying
     * one getting up first; long trips go in legs; one far behind its herd trots. Grazing: 2 or 3 grazes on a stand, a
     * little thing now and then between them, then a step of 1 to 3 blocks to the nearest green plant near its spot; the
     * plant at its jaws is cropped at the chewing part of each graze, tall to short, a short one left. Midday: it turns to
     * the water, drinks once or twice, then lies down; night: it lies down; lying long it gets up now and then and lies down
     * again. A place it cannot reach: it waits 30 s by its nearest mate before trying again. Land not loaded: it waits and
     * never rolls another place (that would split the herd).
     */
    static final class DayGoal extends Goal {
        private final BuffaloEntity b;
        /** The part of the day it is on and for which bout (null: none yet). */
        @Nullable
        private Act act;
        private long bout = Long.MIN_VALUE;
        /** The middle of the place for this part of the day, and its own spot round it (null until it sets off). */
        @Nullable
        private Vec3 centre, spot;
        /** Its stand (null until chosen at its turn to set off), and which way it faces there to drink (null: no water there). */
        @Nullable
        private BlockPos stand;
        @Nullable
        private Direction drinkFacing;
        /** Whether it is at its stand (else on its way). */
        private boolean there;
        /** Whether it grazes on where it is until its turn to set off. */
        private boolean grazeOn;
        /** Its turn to set off, and until when it waits (its own ticks). */
        private int departAt, waitUntil;
        /** Its way to its stand (the shared {@link Trip}: legs, routes, the last few blocks straight). */
        private final Trip trip = new Trip();
        /** What its way to its stand is: a trip, a grazing step (it stops as soon as a plant is at its jaws), a few steps aside. */
        private int going;
        private static final int TRIP = 0, GRAZE_STEP = 1, ASIDE = 2;
        @Nullable
        private Vec3 stepFrom;
        /** The block its jaws were over at its last graze (a step never stops early there again). */
        @Nullable
        private BlockPos grazedAt;
        /** At its stand: graze clips left before a step, drinks left, when it next gets up from lying. */
        private int grazesLeft, drinksLeft, upAt;
        /** Whether the last thing on its stand was a graze (a little thing may follow), and whether it has just got up. */
        private boolean grazed, justUp;

        DayGoal(BuffaloEntity b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !b.led && b.home() != null && b.isAlive() && b.grownUp();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        /**
         * Started again (after its temper): back to its stand, whatever it was doing there, as a trip (a grazing step or a
         * few steps aside cut by a charge must not end where the charge left it: a step's early stop at a plant, or a step
         * it cannot make, would pin its stand there, away from its herd).
         */
        @Override
        public void start() {
            there = false;
            trip.drop();
            going = TRIP;
            stepFrom = null;
        }

        @Override
        public void stop() {
            b.getNavigation().stop();
            trip.drop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            int now = b.tickCount;
            if (b.doing() == BuffaloEntity.GRAZE && b.clipTick() == GRAZE_BITE) {
                grazedAt = BlockPos.containing(b.jawPoint());
                b.cropAtJaws();                         // the chewing part of a graze
            }
            long time = Colony.dayTimeAt(b.level(), b.home());
            Act nowAct = actAt(time);
            long nowBout = boutAt(time);
            if (nowAct != act || nowBout != bout) {
                begin(nowAct, nowBout, now);
            }
            if (now < waitUntil) {
                return;
            }
            if (there) {
                atStand(now);
            } else {
                go(now);
            }
        }

        /** Which part of which bout it is on (mates on the same one keep apart by their stands). */
        private long key() {
            return bout * 4 + (act == null ? 3 : act.ordinal());
        }

        private void begin(Act newAct, long newBout, int now) {
            grazeOn = act == Act.GRAZE && there;
            act = newAct;
            bout = newBout;
            centre = null;
            spot = null;
            stand = null;
            drinkFacing = null;
            there = false;
            trip.drop();
            going = TRIP;
            waitUntil = 0;
            departAt = now + b.rank() * DEPART_GAP;
        }

        // --- on its way --------------------------------------------------------------------------------------------------

        private void go(int now) {
            byte d = b.doing();
            if (stand == null) {
                if (now < departAt) {
                    if (grazeOn && d == BuffaloEntity.CALM && roomy(b.getX(), b.getZ())) {
                        b.setDoing(BuffaloEntity.GRAZE);            // grazing on until its turn to set off
                    }
                    return;
                }
                if (d == BuffaloEntity.REST || d == BuffaloEntity.REST_DOWN) {
                    b.setDoing(BuffaloEntity.REST_UP);              // lying: up first
                    return;
                }
                if (d != BuffaloEntity.CALM) {
                    return;                                         // a clip plays out first, never cut
                }
                if (!chooseStand()) {
                    waitUntil = now + UNLOADED_WAIT;                // its land is not loaded: it waits, never another place
                    return;
                }
            }
            double dist = flat(b.position(), stand);
            if (d == BuffaloEntity.REST || d == BuffaloEntity.REST_DOWN) {
                if (dist <= ARRIVE_DONE) {
                    arrive();                                   // lying at its stand already (its goal started again)
                } else {
                    b.setDoing(BuffaloEntity.REST_UP);
                }
                return;
            }
            if (d != BuffaloEntity.CALM) {
                return;
            }
            if (going == GRAZE_STEP && stepFrom != null && flat(b.position(), stepFrom.x, stepFrom.z) >= STEP_LEAST) {
                BlockPos p = b.plantAtJaws();
                if (p != null && (grazedAt == null || p.getX() != grazedAt.getX() || p.getZ() != grazedAt.getZ())) {
                    arrive();                                       // a new plant at its jaws on the way: it grazes here
                    return;
                }
            }
            if (trip.stand() != stand) {
                trip.to(stand);
            }
            switch (trip.step(b, pace(), ARRIVE, ARRIVE_DONE)) {
                case ARRIVED -> arrive();
                case CANNOT_REACH -> cannotReach(now);
                case UNLOADED -> waitUntil = now + UNLOADED_WAIT;
                default -> {
                }
            }
        }

        /** Its pace: a grazing step or a few steps at the walk clip's own speed, a trip at its travelling walk, a trot when far behind. */
        private double pace() {
            if (going != TRIP) {
                return BuffaloEntity.GRAZE_STEP;
            }
            return behind() > TROT_BEHIND ? BuffaloEntity.TROT : BuffaloEntity.TRAVEL;
        }

        /** How far it is behind its herd: its distance from the place's middle less the herd's mean distance from it. */
        private double behind() {
            if (centre == null) {
                return 0;
            }
            double own = flat(b.position(), centre.x, centre.z), sum = own;
            int n = 1;
            for (BuffaloEntity m : b.mates()) {
                sum += flat(m.position(), centre.x, centre.z);
                n++;
            }
            return own - sum / n;
        }

        /** A place it cannot reach: it waits {@link #UNREACHABLE_WAIT} by its nearest mate, then tries again. */
        private void cannotReach(int now) {
            trip.drop();
            if (going != TRIP) {
                stand = b.blockPosition();                  // a step that cannot be made: it grazes (or lies) where it is
                b.standAt = stand;
                going = TRIP;
                return;
            }
            waitUntil = now + UNREACHABLE_WAIT;
            BlockPos by = byMate();
            if (by != null) {
                b.getNavigation().moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5, BuffaloEntity.TRAVEL);
            } else {
                b.getNavigation().stop();
            }
        }

        private void arrive() {
            b.getNavigation().stop();
            there = true;
            trip.drop();
            if (going == GRAZE_STEP && flat(b.position(), stand) > ARRIVE) {
                stand = b.blockPosition();                  // stopped early with a plant at its jaws
                b.standAt = stand;
            }
            going = TRIP;
            grazesLeft = GRAZES_FEWEST + b.getRandom().nextInt(GRAZES_MOST - GRAZES_FEWEST + 1);
            grazed = false;
        }

        // --- its stand ---------------------------------------------------------------------------------------------------

        /** Chooses its stand for this part of the day; false if its land is not loaded (it waits). */
        private boolean chooseStand() {
            BlockPos home = b.home();
            int rank = b.rank();
            drinkFacing = null;
            drinksLeft = 0;
            if (act == Act.MIDDAY) {
                centre = Vec3.atBottomCenterOf(home);
                if (!b.level().hasChunkAt(home)) {
                    return false;
                }
                BlockPos shore = shoreStand(rank);
                if (shore != null) {
                    setStand(shore);
                    drinksLeft = DRINKS_FEWEST + b.getRandom().nextInt(DRINKS_MOST - DRINKS_FEWEST + 1);
                    return true;
                }
                // no water in its pan (or no pan): it lies down round its home without drinking
                spot = centre.add(offset(rank, b.homeAcross() / 2.0 + LIE_RADIUS));
            } else {
                centre = grazeCentre(home, b.homeAcross(), bout);
                spot = centre.add(offset(rank, act == Act.GRAZE ? GRAZE_RADIUS : LIE_RADIUS));
            }
            int r = Mth.ceil(STAND_REACH);
            if (!b.level().hasChunksAt(BlockPos.containing(spot.x - r, b.getY(), spot.z - r), BlockPos.containing(spot.x + r, b.getY(), spot.z + r))) {
                return false;
            }
            BlockPos s = standNear(spot.x, spot.z, STAND_REACH, home.getY(), GROUND_DY);
            if (s == null) {
                s = byMate();
            }
            if (s == null) {
                // no mate has its stand for this part of the day yet (it goes first): anywhere round the place's middle
                s = standNear(centre.x, centre.z, GRAZE_RADIUS + STAND_REACH, home.getY(), GROUND_DY);
            }
            setStand(s != null ? s : b.blockPosition());
            return true;
        }

        private void setStand(BlockPos s) {
            stand = s;
            b.standAt = s;
            b.standKey = key();
            going = TRIP;
        }

        /**
         * The nearest dry grass ground ({@link Grazing#grassStand}: grass or soil under it, never a mound top, a log or sand)
         * within {@code reach} of x, z, at most {@code dy} over or under {@code y}, not within {@link #SPACE} of a mate's
         * stand; or null.
         */
        @Nullable
        private BlockPos standNear(double x, double z, double reach, int y, int dy) {
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            int r = Mth.ceil(reach);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int cx = Mth.floor(x) + dx, cz = Mth.floor(z) + dz;
                    double ex = cx + 0.5 - x, ez = cz + 0.5 - z, d = ex * ex + ez * ez;
                    if (d > reach * reach || d >= bestD) {
                        continue;
                    }
                    BlockPos s = Grazing.grassStand(b.level(), cx, cz, y, dy);
                    if (s != null && !crowded(cx + 0.5, cz + 0.5)) {
                        best = s;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        /** Whether a mate on the same part of the day means to stand within {@link #SPACE} of x, z. */
        private boolean crowded(double x, double z) {
            long key = key();
            for (BuffaloEntity m : b.mates()) {
                if (m.standKey == key && m.standAt != null && flat(Vec3.atBottomCenterOf(m.standAt), x, z) < SPACE) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether no mate's middle is within {@link #SPACE} of x, z now: it grazes only with room round it (its stand keeps
         * clear of where mates mean to stand, but a route may end up to {@link #ARRIVE_DONE} short of it).
         */
        private boolean roomy(double x, double z) {
            for (BuffaloEntity m : b.mates()) {
                if (m.isAlive() && m.grownUp() && flat(m.position(), x, z) < SPACE) {   // a calf at her side never stops her grazing
                    return false;
                }
            }
            return true;
        }

        /**
         * A stand by its nearest mate with a stand for this same part of the day (beside it, {@link #SPACE} off), or null.
         * Never by a mate's old place: a mate that has not set off yet (it goes later by its order) is about to leave it.
         */
        @Nullable
        private BlockPos byMate() {
            long key = key();
            for (BuffaloEntity m : b.mates()) {             // nearest first
                if (m.standKey == key && m.standAt != null) {
                    BlockPos at = m.standAt;
                    return standNear(at.getX() + 0.5, at.getZ() + 0.5, SPACE + 3.0, at.getY(), 2);
                }
            }
            return null;
        }

        /** Its own stand on its pan's shore (the shared {@link Shore}), setting its spot and the way it faces to drink; null if none. */
        @Nullable
        private BlockPos shoreStand(int rank) {
            Shore shore = Shore.find(b.level(), b.home(), b.homeAcross(), rank, this::crowded);
            if (shore == null) {
                return null;
            }
            spot = shore.spot();
            drinkFacing = shore.facing();
            return shore.stand();
        }

        // --- at its stand ------------------------------------------------------------------------------------------------

        private void atStand(int now) {
            byte d = b.doing();
            if (act == Act.GRAZE) {
                graze(d);
                return;
            }
            // midday and night: a drink first at the shore, then it lies down, getting up now and then
            if (d == BuffaloEntity.REST) {
                if (now >= upAt) {
                    b.setDoing(BuffaloEntity.REST_UP);
                    justUp = true;
                }
                return;
            }
            if (d != BuffaloEntity.CALM) {
                return;
            }
            if (drinksLeft > 0 && drinkFacing != null) {
                if (b.faceYaw(drinkFacing.toYRot(), BuffaloEntity.MAX_TURN)) {
                    b.setDoing(BuffaloEntity.DRINK);            // facing the water: a drink
                    drinksLeft--;
                }
                return;
            }
            if (justUp) {
                justUp = false;
                if (b.getRandom().nextBoolean() || !stepAside()) {
                    b.setDoing(BuffaloEntity.SHAKE_OFF);
                }
                return;
            }
            b.setDoing(BuffaloEntity.REST_DOWN);
            upAt = now + UP_SHORTEST + b.getRandom().nextInt(UP_LONGEST - UP_SHORTEST);
        }

        private void graze(byte d) {
            if (d != BuffaloEntity.CALM) {
                return;                                         // a graze or a little thing plays out, never cut
            }
            if (!roomy(b.getX(), b.getZ()) && stepToNextPlant()) {
                // a mate's middle within SPACE (it stopped short of its stand, or a mate did): a step off before grazing;
                // with nowhere to step it grazes here (two mates must never stand waiting on each other for room)
                return;
            }
            if (grazesLeft > 0) {
                if (grazed && b.getRandom().nextInt(LITTLE_ONE_IN) == 0) {
                    littleThing();
                    grazed = false;
                    return;
                }
                b.setDoing(BuffaloEntity.GRAZE);
                grazesLeft--;
                grazed = true;
                return;
            }
            if (!stepToNextPlant()) {
                grazesLeft = GRAZES_FEWEST;                     // nowhere to step: it grazes on here
            }
        }

        /** Between grazes: an ear flutter, a shake, a snort, or now and then a bull's bellow. */
        private void littleThing() {
            RandomSource r = b.getRandom();
            if (b.life().isMale() && r.nextInt(BELLOW_ONE_IN) == 0) {
                b.setDoing(BuffaloEntity.BELLOW);
                return;
            }
            int roll = r.nextInt(10);
            b.setDoing(roll < 5 ? BuffaloEntity.EAR_FLUTTER : roll < 8 ? BuffaloEntity.SHAKE_OFF : BuffaloEntity.SNORT);
        }

        /**
         * A grazing step: to the nearest green plant {@link #STEP_LEAST} to {@link #STEP_MOST} from its jaws (a tall one,
         * that can still be cropped, before a short one), standing so its jaws reach it (its jaws {@code JAWS_AHEAD} ahead
         * of its middle along the way to the plant), near its spot and clear of its mates' stands; with none, a step to
         * grass ground near it. The stand is always a real step off (at least {@link #STEP_LEAST} past {@link #ARRIVE}
         * from its middle), never under its own body, where it would "arrive" at once and graze the same block for good.
         * False if there is nowhere to step.
         */
        private boolean stepToNextPlant() {
            Level level = b.level();
            Vec3 jaw = b.jawPoint();
            double jawsAhead = BuffaloEntity.JAWS_AHEAD * b.getScale();
            BlockPos best = null;
            double bestD = Double.MAX_VALUE;
            boolean bestTall = false;
            int r = Mth.ceil(STEP_MOST);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // (the block at its jaws, the one it just grazed, is always under STEP_LEAST from them)
                    int x = Mth.floor(jaw.x) + dx, z = Mth.floor(jaw.z) + dz;
                    double fromJaw = flat(jaw, x + 0.5, z + 0.5);
                    if (fromJaw < STEP_LEAST || fromJaw > STEP_MOST) {
                        continue;
                    }
                    BlockPos plant = Grazing.grassStand(level, x, z, b.getBlockY(), 2);
                    if (plant == null) {
                        continue;
                    }
                    BlockState state = level.getBlockState(plant);
                    if (!Grazing.edible(state)) {
                        continue;
                    }
                    boolean tall = state.hasProperty(DoublePlantBlock.HALF);
                    if (bestTall && !tall || tall == bestTall && fromJaw >= bestD) {
                        continue;
                    }
                    // where it stands to have the plant at its jaws: that far short of it, coming from where it is
                    double ux = x + 0.5 - b.getX(), uz = z + 0.5 - b.getZ(), len = Math.sqrt(ux * ux + uz * uz);
                    if (len < 0.5) {
                        continue;
                    }
                    double sx = x + 0.5 - ux / len * jawsAhead, sz = z + 0.5 - uz / len * jawsAhead;
                    if (spot != null && flat(spot, sx, sz) > STAND_REACH + 1.0 || crowded(sx, sz) || !roomy(sx, sz)) {
                        continue;
                    }
                    BlockPos s = Grazing.grassStand(level, Mth.floor(sx), Mth.floor(sz), plant.getY(), 1);
                    if (s == null || flat(b.position(), s) < STEP_LEAST + ARRIVE) {
                        continue;                                   // no ground to stand on there, or no real step
                    }
                    best = s;
                    bestD = fromJaw;
                    bestTall = tall;
                }
            }
            if (best == null) {
                best = groundNear(STEP_LEAST + ARRIVE, STEP_MOST);
            }
            if (best == null) {
                return false;
            }
            stand = best;
            b.standAt = best;
            there = false;
            trip.drop();
            going = GRAZE_STEP;
            stepFrom = b.position();
            return true;
        }

        /** Lying long, up for a few steps: grass ground 1 or 2 blocks of walk off (past {@link #ARRIVE}), near its spot; false if none. */
        private boolean stepAside() {
            BlockPos to = groundNear(ARRIVE + 1.0, ARRIVE + 2.0);
            if (to == null) {
                return false;
            }
            stand = to;
            b.standAt = to;
            there = false;
            trip.drop();
            going = ASIDE;
            return true;
        }

        /** Dry grass ground this far from it (at random; its block's middle at least {@code least}), near its spot and clear of its mates' stands; or null. */
        @Nullable
        private BlockPos groundNear(double least, double most) {
            for (int i = 0; i < 8; i++) {
                double a = b.getRandom().nextDouble() * Math.PI * 2.0, far = least + b.getRandom().nextDouble() * (most - least);
                double x = b.getX() + Math.cos(a) * far, z = b.getZ() + Math.sin(a) * far;
                if (spot != null && flat(spot, x, z) > STAND_REACH + 1.0 || crowded(x, z) || !roomy(x, z)) {
                    continue;
                }
                BlockPos s = Grazing.grassStand(b.level(), Mth.floor(x), Mth.floor(z), b.getBlockY(), 1);
                if (s != null && flat(b.position(), s) >= least) {
                    return s;
                }
            }
            return null;
        }
    }

    // --- its temper --------------------------------------------------------------------------------------------------------

    /** Its temper's stages (as the raid's: one rule and one deadline each). */
    // --- a calf ---------------------------------------------------------------------------------------------------------------

    /** A calf keeps this far to its mother's side (blocks, at her size), and is there within this much (blocks). */
    static final double CALF_BESIDE = 1.8, CALF_THERE = 0.7;
    /** Farther than this from its spot it takes a route, not a straight line (blocks). */
    static final double CALF_ROUTE = 10.0;
    /** One in this many quiet seconds at her side while she grazes, it grazes too. */
    static final int CALF_GRAZE_ONE_IN = 4;

    /**
     * A calf's (or a teen's) day and fear in one (lion step 2, {@code docs/plans/lion-hunt.md}, part A): it keeps at its
     * mother's left side (her herd's nearest cow if she is dead or gone), at her pace, and lies down when she lies, gets up
     * when she gets up, grazes now and then when she grazes. Danger to the herd (her temper on someone, a mate's charge or
     * bunching, or her running from a lion) sends it to her far side from the danger, her body between them. It never
     * warns or fights; grown, the herd's day takes it.
     */
    static final class CalfGoal extends Goal {
        private final BuffaloEntity b;
        private int repath;

        CalfGoal(BuffaloEntity b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !b.led && b.isAlive() && !b.grownUp();
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
            b.getNavigation().stop();
        }

        /** Its mother, else the nearest grown cow of its herd; null if none. */
        @Nullable
        private BuffaloEntity guardian() {
            BuffaloEntity mom = b.mother();
            if (mom != null) {
                return mom;
            }
            for (BuffaloEntity m : b.mates()) {                 // nearest first
                if (m.grownUp() && !m.life().isMale()) {
                    return m;
                }
            }
            return null;
        }

        /** What its guardian guards against now, or null. */
        @Nullable
        private static Entity danger(BuffaloEntity mom) {
            if (mom.temperOn() != null && mom.temper() != Temper.NONE && mom.temper() != Temper.CALM) {
                return mom.temperOn();
            }
            LivingEntity foe = mom.angryAt();
            if (foe != null) {
                return foe;
            }
            for (BuffaloEntity m : mom.mates()) {
                if (m.temperOn() != null && (m.temper() == Temper.CHARGE || m.temper() == Temper.BUNCH)) {
                    return m.temperOn();
                }
            }
            return null;
        }

        @Override
        public void tick() {
            byte d = b.doing();
            if (b.holders() > 0) {
                b.getNavigation().stop();                       // held by a lion: knocked down, it stays down
                if (d != BuffaloEntity.REST && d != BuffaloEntity.REST_DOWN) {
                    b.setDoing(BuffaloEntity.REST_DOWN);
                }
                return;
            }
            BuffaloEntity mom = guardian();
            if (mom == null) {
                return;                                         // alone: it stands (a calf alone is a later step)
            }
            Entity danger = danger(mom);
            float yaw = mom.getYRot() * Mth.DEG_TO_RAD;
            Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));     // her left
            if (danger != null) {
                Vec3 away = mom.position().subtract(danger.position());
                if (side.x * away.x + side.z * away.z < 0) {
                    side = side.scale(-1);                          // her far side from the danger
                }
            }
            double off = CALF_BESIDE * mom.getScale();
            Vec3 spot = mom.position().add(side.scale(off));
            double dist = flat(b.position(), spot.x, spot.z);
            boolean momDown = mom.doing() == BuffaloEntity.REST || mom.doing() == BuffaloEntity.REST_DOWN;
            if (dist > CALF_THERE && !(momDown && dist < CALF_THERE * 2 && b.doing() == BuffaloEntity.REST)) {
                if (d == BuffaloEntity.REST || d == BuffaloEntity.REST_DOWN) {
                    b.setDoing(BuffaloEntity.REST_UP);
                    return;
                }
                if (d != BuffaloEntity.CALM && d != BuffaloEntity.GRAZE) {
                    return;                                     // a clip plays out first
                }
                if (d == BuffaloEntity.GRAZE) {
                    b.setDoing(BuffaloEntity.CALM);
                }
                double pace = mom.stride() > BuffaloEntity.CHARGE_FROM ? BuffaloEntity.CHARGE
                        : dist > 3.0 || mom.stride() > BuffaloEntity.TROT_FROM ? BuffaloEntity.TROT : BuffaloEntity.TRAVEL;
                if (dist > CALF_ROUTE) {
                    if (--repath <= 0) {
                        repath = 20;
                        b.getNavigation().moveTo(spot.x, spot.y, spot.z, pace);
                    }
                } else {
                    b.getNavigation().stop();
                    b.getMoveControl().setWantedPosition(spot.x, mom.getY(), spot.z, pace);
                }
                return;
            }
            b.getNavigation().stop();
            if (d == BuffaloEntity.CALM) {
                if (!b.faceYaw(mom.getYRot(), 6.0F)) {
                    return;
                }
                if (momDown && danger == null) {
                    b.setDoing(BuffaloEntity.REST_DOWN);
                } else if (mom.doing() == BuffaloEntity.GRAZE && danger == null && b.tickCount % 20 == 0
                        && b.getRandom().nextInt(CALF_GRAZE_ONE_IN) == 0) {
                    b.setDoing(BuffaloEntity.GRAZE);
                }
            } else if (d == BuffaloEntity.REST && (!momDown || danger != null)) {
                b.setDoing(BuffaloEntity.REST_UP);
            }
        }
    }

    public enum Temper {
        NONE, WATCH, WARN, CHARGE, CALM, BUNCH, FLEE
    }

    /** A player (not in creative, not a spectator) is noticed this near, crouching this near (blocks); watched up to this long (ticks). */
    static final double WATCH_REACH = 12.0, WATCH_CROUCHED = 6.0;
    static final int WATCH_TICKS = 200;
    /** A lion walking past this near is watched (blocks). */
    static final double LION_WATCH = 12.0;
    /** A held buffalo pulls away from the nearest lion this near (blocks); a buffalo flees a hunter at most this long (ticks). */
    static final double LION_HELD_REACH = 8.0;
    static final int FLEE_TICKS = 100;
    /** A player it watched is watched again only after coming this much nearer (blocks), or sprinting. */
    static final double NEARER = 3.0;
    /** The herd's nearest member warns a player this near (blocks); a warning is remembered this long for that player (ticks), and may take this long. */
    static final double WARN_REACH = 7.0;
    static final int WARN_MEMORY = 600, WARN_TICKS = 100;
    /** It charges a player still this near after its warning, or this near at any time (blocks). */
    static final double CHARGE_WARNED = 5.0, CHARGE_ANY = 3.0;
    /** A charge at a player who only came too near ends after one hit or this long (ticks). */
    static final int CHARGE_TICKS = 160;
    /** Hurt, it is angry at whoever did it this long from the last hurt (ticks: as many hits as it can land in 8 s). */
    public static final int REVENGE_TICKS = 160;
    /**
     * Its run-up butt ({@code charge_headbutt}) starts when its target is its own pace times this many ticks ahead of its
     * head (1.2 s); it runs on until the clip's tick {@link #RUN_UNTIL}, easing off to the hit at {@link #CHARGE_HIT}.
     * A standing butt hits at {@link #HEADBUTT_HIT}, a hook to the side ({@code gore}) at {@link #GORE_HIT}.
     */
    static final int RUN_UP = 24, RUN_UNTIL = 16, CHARGE_HIT = 24, HEADBUTT_HIT = 9, GORE_HIT = 8;
    /**
     * Its head bead this near its target (blocks) it butts standing; this near at the hit's moment, the hit lands (a little
     * more, so a butt started in reach never misses for the target standing still).
     */
    static final double BUTT_REACH = 0.2, HIT_SLACK = 0.35;
    /** A target more than this far off to one side of its head (degrees) gets the hook ({@code gore}). */
    static final float GORE_ANGLE = 20.0F;
    /** Its hit's toss (Minecraft's knockback strength; a player's own is 0.4 a swing). */
    public static final double TOSS = 1.5;
    /** After a charge it stands its ground this long (ticks), pawing the ground at this tick. */
    static final int CALM_TICKS = 200, CALM_PAW = 60;
    /**
     * Cows bunch this far round where their mate was hurt (blocks), this long at most (ticks); in place this near their
     * spot, or this near with their route done (blocks).
     */
    static final double BUNCH_RADIUS = 2.5, BUNCH_THERE = 1.0, BUNCH_CLOSE = 2.0;
    static final int BUNCH_TICKS = 400;
    /** How fast it turns to face what it watches and what it butts (degrees a tick). */
    static final float WATCH_TURN = 6.0F, BUTT_TURN = 12.0F;
    /** A chase's route is renewed this often (ticks); after one that does not reach its target, only after this long. */
    static final int CHASE_REPATH = 4, CHASE_RETRY = 15;

    /**
     * Its temper, one goal of stages (as the raid rewrite's: each stage one rule that ends it and one deadline); what the
     * moment calls for is judged in one place ({@link #call}), which every stage asks.
     * <ul>
     * <li>WATCH: a player noticed within {@link #WATCH_REACH} ({@link Attention#playerInReach}, nearer when crouching): it
     * stops and faces them ({@code mood_alert}) up to {@link #WATCH_TICKS}, then goes back to its day with only its eyes on
     * them, unless they come {@link #NEARER} blocks nearer or sprint. A lying buffalo only watches with its eyes.</li>
     * <li>WARN: within {@link #WARN_REACH} of the herd's nearest member to that player (each works it out from its own
     * mates, ties by id): {@code snort} then {@code paw_ground}, remembered for that player for {@link #WARN_MEMORY}
     * (review R9-13: one timer for all let a second player be charged unwarned).</li>
     * <li>CHARGE: still within {@link #CHARGE_WARNED} after the warning, or within {@link #CHARGE_ANY} at any time: it
     * gallops; with the target its pace times 1.2 s ahead it plays its run-up butt, running on to the clip's tick 16 and
     * easing off to the hit at tick 24; already in reach, a standing butt or a hook. A hit lands only if its head bead
     * touches the target then. A charge at a player who only came too near ends after one hit (a big toss).</li>
     * <li>CALM: {@link #CALM_TICKS} standing its ground ({@code mood_angry}, a {@code paw_ground}), then back to its day.</li>
     * <li>Hurt by anything but another buffalo ({@link BuffaloEntity#angryAt}): it charges whoever did it, as many hits as it
     * lands in {@link #REVENGE_TICKS}, giving up only by that time or past its follow range (32; review R9-15: a hippo
     * shot from 17 blocks never answered). Its herd's bull joins the charge; the cows bunch round where it was hurt and
     * face the attacker (BUNCH); each by seeing a mate is angry, never told.</li>
     * </ul>
     * Never angry at another buffalo; its hits land only on its target. Lying, it gets up first.
     */
    static final class TemperGoal extends Goal {
        private final BuffaloEntity b;
        private Temper stage = Temper.NONE;
        @Nullable
        private LivingEntity target;
        /** A charge at whoever hurt it or its mate (no end at one hit), and whether a hit has landed. */
        private boolean revenge, hit;
        /** Ticks in its stage (counted once it is on its feet), the part of its warning (snort, pawing), ticks to its next route. */
        private int ticks, part, repath;
        /** Bunching: its spot round where its mate was hurt. */
        @Nullable
        private Vec3 bunchAt;
        /** Held, the way it pulls (fixed when first held). */
        @Nullable
        private Vec3 heldAway;
        /** What the moment called for when it was last free (taken up by {@link #start}). */
        @Nullable
        private Call pending;
        /** The players it warned and watched (the shared {@link WarnMemory}). */
        private final WarnMemory memory = new WarnMemory(WARN_MEMORY);

        /** What the moment calls for: a stage, at whom, whether for a hurt, and (bunching) where. */
        private record Call(Temper stage, LivingEntity target, boolean revenge, @Nullable Vec3 bunchAt) {
        }

        TemperGoal(BuffaloEntity b) {
            this.b = b;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!b.isAlive()) {
                return false;
            }
            memory.tidy(b, this::notices);
            pending = call();
            return pending != null;
        }

        @Override
        public void start() {
            if (pending != null) {
                enter(pending);
                pending = null;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return stage != Temper.NONE && b.isAlive();
        }

        @Override
        public void stop() {
            byte d = b.doing();
            if (d == BuffaloEntity.MOOD_ALERT || d == BuffaloEntity.MOOD_ANGRY) {
                b.setDoing(BuffaloEntity.CALM);
            }
            b.getNavigation().stop();
            stage = Temper.NONE;
            target = null;
            b.temper = Temper.NONE;
            b.temperOn = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private double followRange() {
            return b.getAttributeValue(Attributes.FOLLOW_RANGE);
        }

        /**
         * The one judge of what the moment calls for, strongest first: its own hurt (a charge); a mate hurt (the bull joins
         * the charge, a cow bunches); a player too near the herd's nearest member (a charge, or a warning first); a player
         * noticed (a watch). Null: nothing.
         */
        @Nullable
        private Call call() {
            if (!b.grownUp()) {
                return null;                                // a calf never warns or fights: it keeps by its mother (CalfGoal)
            }
            if (b.holders() > 0) {
                LionEntity holder = nearestLion(LION_HELD_REACH, lion -> true);
                if (holder != null) {
                    return new Call(Temper.FLEE, holder, true, null);   // held: it pulls away, never charges its holders (no spinning)
                }
            }
            LivingEntity foe = b.angryAt();
            if (foe != null && b.distanceTo(foe) > followRange()) {
                b.calmDown();                               // past its follow range: it gives up
                foe = null;
            }
            if (foe != null) {
                return new Call(Temper.CHARGE, foe, true, null);
            }
            for (BuffaloEntity m : b.mates()) {             // nearest first
                LivingEntity mateFoe = m.isAlive() ? m.angryAt() : null;
                if (mateFoe == null || m.hurtAt() == null) {
                    continue;
                }
                if (b.life().isMale() || b.motherOf(m)) {
                    if (b.distanceTo(mateFoe) <= followRange()) {
                        return new Call(Temper.CHARGE, mateFoe, true, null);    // the bull, and a hurt calf's mother
                    }
                } else {
                    return new Call(Temper.BUNCH, mateFoe, true, m.hurtAt().add(offset(b.rank(), BUNCH_RADIUS)));
                }
            }
            LionEntity hunter = nearestLion(BuffaloEntity.LION_GALLOP_SEEN, lion -> (lion.stalking() || lion.galloping()) && b.seesLion(lion));
            if (hunter != null) {
                return new Call(Temper.FLEE, hunter, false, null);     // a hunting lion seen: the herd runs (lion step 2)
            }
            Player p = b.level().getNearestPlayer(b.getX(), b.getY(), b.getZ(), WATCH_REACH, e -> e instanceof Player q && notices(q));
            if (p == null) {
                return lionPassing();
            }
            double d = b.distanceTo(p);
            boolean nearest = b.nearestOfHerdTo(p);
            if (nearest && (d < CHARGE_ANY || d < CHARGE_WARNED && warnedLately(p))) {
                return new Call(Temper.CHARGE, p, false, null);
            }
            if (nearest && d < WARN_REACH && !warnedLately(p)) {
                return new Call(Temper.WARN, p, false, null);
            }
            byte doing = b.doing();
            boolean lying = doing == BuffaloEntity.REST || doing == BuffaloEntity.REST_DOWN || doing == BuffaloEntity.REST_UP;
            Double was = memory.watchedAt(p);
            if (!lying && (was == null || d <= was - NEARER || p.isSprinting())) {
                return new Call(Temper.WATCH, p, false, null);
            }
            return lionPassing();                           // a player already watched: a lion walking past still catches its eye
        }

        /**
         * A lion walking past within {@link #LION_WATCH} (the lions, 2026-11-25; the hunt and the mobbing come with their
         * step 2): a buffalo standing stops and faces it ({@code mood_alert}), never warns or charges. Null: none.
         */
        @Nullable
        private Call lionPassing() {
            byte doing = b.doing();
            if (doing == BuffaloEntity.REST || doing == BuffaloEntity.REST_DOWN || doing == BuffaloEntity.REST_UP) {
                return null;
            }
            LionEntity lion = nearestLion(LION_WATCH, l -> l.walking() && b.seesLion(l));
            return lion != null ? new Call(Temper.WATCH, lion, false, null) : null;
        }

        /** The nearest living lion within reach that passes the test, or null. */
        @Nullable
        private LionEntity nearestLion(double reach, java.util.function.Predicate<LionEntity> test) {
            LionEntity best = null;
            double bestD = reach;
            for (LionEntity lion : b.level().getEntitiesOfClass(LionEntity.class, b.getBoundingBox().inflate(reach, 4.0, reach), LionEntity::isAlive)) {
                double d = b.distanceTo(lion);
                if (d <= bestD && test.test(lion)) {
                    best = lion;
                    bestD = d;
                }
            }
            return best;
        }

        /** Whether it notices a player: alive, not in creative, not a spectator, within reach ({@link Attention#playerInReach}). */
        private boolean notices(Player p) {
            return p.isAlive() && !p.isCreative() && !p.isSpectator() && Attention.playerInReach(p, b.distanceTo(p), WATCH_REACH, WATCH_CROUCHED);
        }

        private boolean warnedLately(Player p) {
            return memory.warnedLately(b, p);
        }

        /** How strongly a stage holds: a stronger call cuts in (a hurt over all; a calm is cut only by a hurt). */
        private static int weight(Temper s, boolean revenge) {
            return switch (s) {
                case FLEE -> revenge ? 6 : 2;              // held, pulling away, over all; seeing a hunter, under a hurt mate's call
                case CHARGE -> revenge ? 5 : 3;
                case BUNCH -> 4;
                case CALM -> 3;
                case WARN -> 2;
                case WATCH -> 1;
                case NONE -> 0;
            };
        }

        private void enter(Call c) {
            stage = c.stage();
            target = c.target();
            revenge = c.revenge();
            bunchAt = c.bunchAt();
            ticks = 0;
            part = 0;
            hit = false;
            repath = 0;
            b.getNavigation().stop();
            b.temper = stage;
            b.temperOn = target;
        }

        private void end() {
            stage = Temper.NONE;
        }

        private boolean valid(@Nullable LivingEntity e) {
            return e != null && e.isAlive() && !e.isRemoved() && e.level() == b.level() && !e.isSpectator();
        }

        @Override
        public void tick() {
            byte d = b.doing();
            if (d == BuffaloEntity.REST || d == BuffaloEntity.REST_DOWN) {
                b.setDoing(BuffaloEntity.REST_UP);          // lying: up on its feet first
                return;
            }
            if (d == BuffaloEntity.REST_UP) {
                return;
            }
            Call c = call();
            if (c != null && weight(c.stage(), c.revenge()) > weight(stage, revenge)) {
                enter(c);                                   // a stronger call cuts in
            }
            ticks++;
            switch (stage) {
                case WATCH -> watch();
                case WARN -> warn();
                case CHARGE -> charge();
                case CALM -> calm();
                case BUNCH -> bunch();
                case FLEE -> flee();
                default -> {
                }
            }
        }

        /**
         * FLEE (lion step 2): straight away from the hunting lion at its charge pace, for at most {@link #FLEE_TICKS}, or
         * while it sees one hunting; held, it pulls away for as long as it is held (slowed by its holders,
         * {@link BuffaloEntity#setSpeed}). Calves run at their mothers' sides.
         */
        private void flee() {
            boolean held = b.holders() > 0;
            if (!held) {
                heldAway = null;
            }
            if (!held && (target == null || !target.isAlive() || ticks > FLEE_TICKS)) {
                end();
                return;
            }
            if (!held && ticks % 10 == 0) {
                LionEntity hunter = nearestLion(BuffaloEntity.LION_GALLOP_SEEN, lion -> (lion.stalking() || lion.galloping()) && b.seesLion(lion));
                if (hunter == null) {
                    end();
                    return;
                }
                target = hunter;
                b.temperOn = hunter;
            }
            if (target == null) {
                return;
            }
            byte d = b.doing();
            if (d == BuffaloEntity.MOOD_ALERT || d == BuffaloEntity.MOOD_ANGRY || d == BuffaloEntity.GRAZE) {
                b.setDoing(BuffaloEntity.CALM);
            }
            Vec3 away = b.position().subtract(target.position());
            double len = Math.sqrt(away.x * away.x + away.z * away.z);
            if (held && heldAway != null) {
                away = heldAway;                            // held: on the way it first pulled (away from a lion on its flank
                len = 1.0;                                  // it would turn after it and circle for good)
            } else if (len < 1.0E-3) {
                return;
            } else {
                away = new Vec3(away.x / len, 0, away.z / len);
                len = 1.0;
                heldAway = held ? away : null;
            }
            b.getNavigation().stop();
            b.getMoveControl().setWantedPosition(b.getX() + away.x * 6.0, b.getY(), b.getZ() + away.z * 6.0, BuffaloEntity.CHARGE);
        }

        /** WATCH: it stands and faces the player (or a passing lion); ends when they are no longer noticed or at {@link #WATCH_TICKS}. */
        private void watch() {
            if (target instanceof LionEntity lion) {
                if (!lion.isAlive() || b.distanceTo(lion) > LION_WATCH + 2.0 || ticks > WATCH_TICKS) {
                    end();
                    return;
                }
                if (b.doing() != BuffaloEntity.MOOD_ALERT) {
                    b.setDoing(BuffaloEntity.MOOD_ALERT);
                }
                b.face(lion.getX(), lion.getZ(), WATCH_TURN);
                return;
            }
            if (!(target instanceof Player p) || !valid(p) || !notices(p) || ticks > WATCH_TICKS) {
                if (target instanceof Player p) {
                    memory.watched(p, b.distanceTo(p));
                }
                end();
                return;
            }
            if (b.doing() != BuffaloEntity.MOOD_ALERT) {
                b.setDoing(BuffaloEntity.MOOD_ALERT);
            }
            b.face(target.getX(), target.getZ(), WATCH_TURN);
        }

        /** WARN: a snort, then pawing the ground, facing them; ends when both have played (then what the moment calls for), or at {@link #WARN_TICKS}. */
        private void warn() {
            if (!valid(target) || ticks > WARN_TICKS) {
                end();
                return;
            }
            b.face(target.getX(), target.getZ(), WATCH_TURN);
            if (ticks == 1) {
                b.setDoing(BuffaloEntity.SNORT);
                return;
            }
            if (b.doing() != BuffaloEntity.CALM) {
                return;                                     // the snort or the pawing plays out
            }
            if (part == 0) {
                part = 1;
                b.setDoing(BuffaloEntity.PAW_GROUND);
                return;
            }
            if (target instanceof Player p) {
                memory.warn(b, p);                          // warned: remembered for this player
            }
            Call next = call();
            if (next != null) {
                enter(next);
            } else {
                end();
            }
        }

        /**
         * CHARGE: it gallops at its target. A hurt's charge ends when its anger does ({@link #call} no longer calls for it:
         * its time, or past its follow range); a charge at a player who only came too near ends after one hit, at
         * {@link #CHARGE_TICKS}, or with the target gone or past its follow range. Then CALM.
         */
        private void charge() {
            if (revenge) {
                Call c = call();
                if (c == null || c.stage() != Temper.CHARGE || !c.revenge()) {
                    toCalm();
                    return;
                }
                target = c.target();                        // (hurt again by another: at the newest)
                b.temperOn = target;
            } else if (!valid(target) || target instanceof Player p && (p.isCreative() || p.isSpectator()) || ticks > CHARGE_TICKS
                    || b.distanceTo(target) > followRange() || hit && b.doing() == BuffaloEntity.CALM) {
                toCalm();
                return;
            }
            PathNavigation nav = b.getNavigation();
            byte d = b.doing();
            if (ticks == 1 && d != BuffaloEntity.CALM && d != BuffaloEntity.CHARGE_HEADBUTT && d != BuffaloEntity.HEADBUTT
                    && d != BuffaloEntity.GORE) {
                b.setDoing(BuffaloEntity.CALM);             // whatever it was doing: off at a gallop
                d = BuffaloEntity.CALM;
            }
            if (d == BuffaloEntity.CHARGE_HEADBUTT) {
                int t = b.clipTick();
                if (t < RUN_UNTIL) {
                    chase(BuffaloEntity.CHARGE);
                } else if (t < CHARGE_HIT) {
                    chase(BuffaloEntity.CHARGE * (CHARGE_HIT - t) / (double) (CHARGE_HIT - RUN_UNTIL));     // easing off to the hit
                } else {
                    nav.stop();
                    if (t == CHARGE_HIT) {
                        tryHit();
                    }
                }
                if (nav.isDone()) {
                    // turned by one hand a tick: on a route its move control turns it (HeavyTurn, MAX_TURN); facing as
                    // well would turn it twice in the tick
                    b.face(target.getX(), target.getZ(), BuffaloEntity.MAX_TURN);
                }
                return;
            }
            if (d == BuffaloEntity.HEADBUTT || d == BuffaloEntity.GORE) {
                nav.stop();
                b.face(target.getX(), target.getZ(), BUTT_TURN);
                if (b.clipTick() == (d == BuffaloEntity.GORE ? GORE_HIT : HEADBUTT_HIT)) {
                    tryHit();
                }
                return;
            }
            if (d != BuffaloEntity.CALM) {
                return;
            }
            if (touches(BUTT_REACH)) {
                nav.stop();
                b.setDoing(sideOn() ? BuffaloEntity.GORE : BuffaloEntity.HEADBUTT);     // already in reach: a butt standing
                return;
            }
            double s = b.stride();
            if (s >= BuffaloEntity.CHARGE_FROM * b.getScale() && headGap() <= s * RUN_UP) {
                b.setDoing(BuffaloEntity.CHARGE_HEADBUTT);    // its pace times 1.2 s ahead: the run-up butt, running on into it
            }
            chase(BuffaloEntity.CHARGE);
        }

        /**
         * Runs at its target, its route renewed every {@link #CHASE_REPATH} ticks (its target moves), at this pace; a route
         * that does not reach the target is looked for again only after {@link #CHASE_RETRY} (as Minecraft's own melee
         * goal backs off), so a target it cannot reach never costs a route search a tick.
         */
        private void chase(double pace) {
            PathNavigation nav = b.getNavigation();
            if (--repath > 0) {
                nav.setSpeedModifier(pace);
                return;
            }
            boolean found = nav.moveTo(target, pace);
            Path path = nav.getPath();
            repath = found && path != null && path.canReach() ? CHASE_REPATH : CHASE_RETRY;
        }

        /** Whether its head bead is within this much of its target. */
        private boolean touches(double slack) {
            return b.headBox().inflate(slack).intersects(target.getBoundingBox());
        }

        /** The gap between the front of its head and its target (blocks, flat). */
        private double headGap() {
            double dx = target.getX() - b.getX(), dz = target.getZ() - b.getZ();
            return Math.sqrt(dx * dx + dz * dz) - BuffaloEntity.HEAD_FRONT * b.getScale() - target.getBbWidth() / 2.0;
        }

        /** Whether its target is off to one side of its head (it hooks with a {@code gore} then). */
        private boolean sideOn() {
            float want = (float) Math.toDegrees(Math.atan2(target.getZ() - b.getZ(), target.getX() - b.getX())) - 90.0F;
            return Math.abs(Mth.wrapDegrees(want - b.yBodyRot)) > GORE_ANGLE;
        }

        /** The hit's moment: it lands if its head bead touches the target now. */
        private void tryHit() {
            if (touches(HIT_SLACK)) {
                b.hitLands(target);
                hit = true;
            }
        }

        private void toCalm() {
            stage = Temper.CALM;
            ticks = 0;
            b.temper = Temper.CALM;
            b.getNavigation().stop();
        }

        /** CALM: it stands its ground facing them ({@code mood_angry}, a pawing) for {@link #CALM_TICKS}, then back to its day. */
        private void calm() {
            if (ticks > CALM_TICKS) {
                if (target instanceof Player p && valid(p)) {
                    memory.watched(p, b.distanceTo(p));     // not stared at again unless nearer
                }
                end();
                return;
            }
            if (valid(target)) {
                b.face(target.getX(), target.getZ(), WATCH_TURN);
            }
            if (ticks == CALM_PAW) {
                b.setDoing(BuffaloEntity.PAW_GROUND);
            } else if (b.doing() == BuffaloEntity.CALM) {
                b.setDoing(BuffaloEntity.MOOD_ANGRY);
            }
        }

        /**
         * BUNCH (a cow): it trots to its spot round where its mate was hurt and stands there facing the attacker
         * ({@code mood_angry}); ends when no mate is angry any more ({@link #call}) or at {@link #BUNCH_TICKS}. Then CALM.
         */
        private void bunch() {
            Call c = call();
            if (c == null || c.stage() != Temper.BUNCH || ticks > BUNCH_TICKS) {
                toCalm();
                return;
            }
            target = c.target();
            bunchAt = c.bunchAt();
            b.temperOn = target;
            PathNavigation nav = b.getNavigation();
            double gap = flat(b.position(), bunchAt.x, bunchAt.z);
            // in place: at its spot, or as near as its route got (its spot may be under its mate's body)
            boolean inPlace = gap <= BUNCH_THERE || part > 0 && nav.isDone() && gap < BUNCH_CLOSE;
            if (ticks == 1 || !inPlace && b.doing() != BuffaloEntity.CALM) {
                // whatever it was doing (a watch's mood_alert, a graze, a drink): off at its gait, its hooves never
                // sliding under another clip; mood_angry only once in place
                b.setDoing(BuffaloEntity.CALM);
            }
            if (!inPlace) {
                if (--repath <= 0) {
                    repath = 20;
                    part = 1;
                    nav.moveTo(bunchAt.x, bunchAt.y, bunchAt.z, BuffaloEntity.TROT);
                }
                return;
            }
            nav.stop();
            b.face(target.getX(), target.getZ(), WATCH_TURN);
            if (b.doing() == BuffaloEntity.CALM) {
                b.setDoing(BuffaloEntity.MOOD_ANGRY);
            }
        }
    }
}
