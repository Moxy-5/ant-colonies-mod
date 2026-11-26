package com.antcolonies.savanna.animal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.antcolonies.animal.Attention;
import com.antcolonies.colony.Colony;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The lion's mind ({@link LionEntity}; {@code docs/plans/lion.md}, step 1, 2026-11-25; fun first, inspired by real lions,
 * every number my pick unless said). Goals by weight: Minecraft's float goal and the shared swim-out (deep water); its
 * temper ({@link TemperGoal}: watch, warn, bluff, attack, calm, and the pride's answer to one of them hurt); its day
 * ({@link DayGoal}: four acts read from the one shared day clock, {@link Colony#dayTimeAt}). Both goals run every tick
 * (clip moments are counted from the lion's own tick) and only say where to walk, at what pace, and which clip to start.
 */
public final class LionGoals {
    private LionGoals() {
    }

    static void register(LionEntity lion, GoalSelector goals) {
        goals.addGoal(0, new FloatGoal(lion));
        goals.addGoal(0, new SwimOutGoal(lion, LionEntity.WALK));
        goals.addGoal(1, new TemperGoal(lion));
        goals.addGoal(2, new LionHunt.HuntGoal(lion));
        goals.addGoal(3, new DayGoal(lion));
    }

    // --- its day's clock ---------------------------------------------------------------------------------------------------

    /** The parts of its day (four acts). */
    public enum Act {
        /** 7:00 (or arriving at the den) to 16:00: lying in the den's shade; the cubs play. */
        REST,
        /** 16:00 to 17:30: waking one by one, stretching, greeting, sitting. */
        WAKING,
        /** 17:30 until all have drunk (21:00 at the latest): to the water in file, drinking, sitting by it. */
        WATER,
        /** After drinking until 7:00: the patrol, three or four points, the last the den. */
        NIGHT
    }

    /** Where the acts begin (Minecraft's day time: 0 is 6:00, 1000 an hour). */
    public static final int REST_FROM = 1000, WAKE_FROM = 10000, WATER_FROM = 11500, NIGHT_BY = 15000;
    /** The patrol's points share the night from 21:00 to 5:00 (ticks of day time). */
    static final int PATROL_SPAN = 8000;
    /** The roar choruses: 18:30, 0:00, 5:30 (day time); one missed is roared within this long after (ticks: 1.5 hours, so waking from a bed at 6:00 hears the dawn's). */
    static final int[] CHORUS = {12500, 18000, 23500};
    static final int CHORUS_WINDOW = 1500;
    /** Each member joins a chorus after its own delay (ticks, rolled from its id): 1 to 5 s; the pride's first starts it. */
    static final int CHORUS_LEAST = 20, CHORUS_SPREAD = 80;
    /** Patrol points lie this far from the den, and up to this much farther (blocks). */
    static final double PATROL_NEAR = 30.0, PATROL_SPREAD = 30.0;
    private static final long PATROL_DICE = 0xC2B2AE3D27D4EB4FL;

    /** The act at this day time, given whether the whole pride has drunk this evening. */
    static Act actAt(long dayTime, boolean allDrank) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t >= REST_FROM && t < WAKE_FROM) {
            return Act.REST;
        }
        if (t >= WAKE_FROM && t < WATER_FROM) {
            return Act.WAKING;
        }
        if (t >= WATER_FROM && t < NIGHT_BY && !allDrank) {
            return Act.WATER;
        }
        return Act.NIGHT;
    }

    /** The evening's number: it goes up at 16:00, so a waking, its drinking and its night share one. */
    public static long eveningAt(long dayTime) {
        return Math.floorDiv(dayTime - WAKE_FROM, 24000L);
    }

    /** How many points a night's patrol has (3 or 4, the last the den), from dice. */
    public static int patrolPoints(BlockPos den, long evening) {
        return 3 + RandomSource.create(den.asLong() ^ evening * PATROL_DICE).nextInt(2);
    }

    /** Which point of its patrol the pride is on at this day time (0 first; the last is the den). */
    public static int patrolPointAt(long dayTime, int points) {
        long t = Math.floorMod(dayTime, 24000L);
        long into = t >= NIGHT_BY ? t - NIGHT_BY : t < REST_FROM ? t + 24000 - NIGHT_BY : 0;   // 17:30 to 21:00 early: the first point
        return (int) Math.min(points - 1, into * points / PATROL_SPAN);
    }

    /** The middle of a patrol point (not the last, which is the den): from dice only, so every mate finds the same one. */
    public static Vec3 patrolPoint(BlockPos den, long evening, int point) {
        return HomeGroup.dicePoint(den, evening * 8 + point, PATROL_DICE, PATROL_NEAR, PATROL_SPREAD);
    }

    /** The latest chorus at or before this day time: its number (counting from the world's start) and its day time. */
    static long[] latestChorus(long dayTime) {
        long day = Math.floorDiv(dayTime, 24000L);
        for (long d = day; d >= day - 1; d--) {
            for (int i = CHORUS.length - 1; i >= 0; i--) {
                long at = d * 24000L + CHORUS[i];
                if (at <= dayTime) {
                    return new long[] {d * CHORUS.length + i, at};
                }
            }
        }
        return new long[] {Long.MIN_VALUE, Long.MIN_VALUE};
    }

    // --- its day -----------------------------------------------------------------------------------------------------------

    /** Mates set off for a new place one after another, this far apart in the pride's order (ticks: a body and a block at a walk). */
    static final int DEPART_GAP = 80;
    /** They wake this far apart (ticks). */
    static final int WAKE_GAP = 50;
    /** More than this far from its place it trots (blocks). */
    static final double TROT_FROM = 16.0;
    /** At its place: this near its middle, or this near with its route done (blocks; a cub nearer). */
    static final double ARRIVE = 1.0, ARRIVE_DONE = 2.0, CUB_ARRIVE = 0.6, CUB_ARRIVE_DONE = 1.2, BED_ARRIVE = 0.4;
    /** After a place it could not reach it waits this long (ticks; review R9-16); land not loaded, this long. */
    static final int UNREACHABLE_WAIT = 600, UNLOADED_WAIT = 40;
    /** Beds at the den: side by side this far apart (blocks), a cub this near its neighbour, the males this much farther out at the row's ends. */
    static final double ADULT_GAP = 1.7, CUB_GAP = 1.0, MALE_OUT = 3.0;
    /** Patrol stops: mates sit round the point on a spiral this many blocks times the square root of their place. */
    static final double SPIRAL = 2.0;
    /** A stand is looked for this far round its spot (blocks), and its ground at most this far over or under the den's. */
    static final double STAND_REACH = 3.0;
    static final int GROUND_DY = 10;
    /** Lying at the den, an adult does something every this many ticks (90 to 200 s); a cub plays every 20 to 40 s. */
    static final int REST_EVENT_LEAST = 1800, REST_EVENT_SPREAD = 2200, PLAY_LEAST = 400, PLAY_SPREAD = 400;
    /** A sit break while resting lasts this long (ticks). */
    static final int SIT_LEAST = 400, SIT_SPREAD = 400;
    /** A male sniffs a patrol point this long (ticks). */
    static final int SNIFF_TICKS = 72;
    /** Greeting: it waits this long at most for its mate to be ready, and to be greeted itself (ticks). */
    static final int GREET_WAIT = 600;
    /** A cub plays at a target this far from it (blocks); a play or a nuzzle needs its target within this reach. */
    static final double PLAY_OFF = 2.0, PLAY_REACH = 10.0;
    /** A cub's whole play, the walk to its spot and being groomed included, at most this long (ticks: 15 s). */
    static final int PLAY_TICKS = 300;
    /** Drinks at the shore. */
    static final int DRINKS_FEWEST = 1, DRINKS_MOST = 2;

    /** Where it means to be for an act: a bed at the den, a sitting stop on the patrol, a shore stand, a play or greeting spot. */
    enum Place {
        BED, STOP, SHORE, PLAY, GREET
    }

    /**
     * Its whole day, one goal of four acts read from the shared clock: resting at the den, waking, drinking, the night's
     * patrol, and the roar chorus whatever the act. Every trip uses one rule: mates set off for the same place one after
     * another in the pride's order ({@link #DEPART_GAP}), each to its own stand, in legs ({@link Trip}), trotting when far;
     * no lion walks to another lion except to greet, nuzzle or play.
     */
    static final class DayGoal extends Goal {
        private final LionEntity l;
        @Nullable
        private Act act;
        private long key = Long.MIN_VALUE, evening;
        private int point, points;
        /** Its place for this part of the day (null until chosen at its turn), what kind, and the way it faces there (NaN: any). */
        @Nullable
        private BlockPos stand;
        private Place place = Place.BED;
        private float standYaw = Float.NaN;
        @Nullable
        private Direction drinkFacing;
        private boolean there;
        private int departAt, waitUntil;
        private final Trip trip = new Trip();
        /** At its place: drinks left, whether a male has sniffed this stop, when it next does something lying, when its sit break ends. */
        private int drinksLeft, nextEventAt, sitUntil = -1, sniffUntil = -1;
        private boolean sniffed, sitBreak;
        /** Waking: its step (0 waiting its turn, 1 choosing whom to greet, 2 going to them, 3 waiting to be greeted, 4 sitting), whom, until when. */
        private int wake, wakeUntil;
        @Nullable
        private LionEntity greetee;
        private boolean greeted;
        /** A cub's play: its step (0 none, 1 going, 2 playing, 3 going back), its target, its clip. */
        private int play;
        @Nullable
        private LionEntity playWith;
        private byte playClip;
        private int playSince;
        /** Answering a mate (a lioness grooming a nuzzling cub), and with what. */
        @Nullable
        private LionEntity answering;
        /** The chorus: when it roars (its own tick; -1 none) and which. */
        private int roarAt = -1;
        private long roarChorus;

        DayGoal(LionEntity l) {
            this.l = l;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !l.led && l.den() != null && l.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        /** Started again (after its temper): back to its place, whatever it was doing there; a cub's play is dropped. */
        @Override
        public void start() {
            there = false;
            trip.drop();
            if (place == Place.PLAY || place == Place.GREET) {
                stand = null;
                place = Place.BED;
            }
            play = 0;
            playWith = null;
            answering = null;
            l.focus = null;
        }

        @Override
        public void stop() {
            l.getNavigation().stop();
            trip.drop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            int now = l.tickCount;
            long time = Colony.dayTimeAt(l.level(), l.den());
            long ev = eveningAt(time);
            Act nowAct = actAt(time, allDrank(ev));
            int nowPoints = patrolPoints(l.den(), ev);
            int nowPoint = nowAct == Act.NIGHT ? patrolPointAt(time, nowPoints) : 0;
            long nowKey = ev * 16 + nowAct.ordinal() * 4 + nowPoint;
            if (nowKey != key) {
                begin(nowAct, ev, nowPoint, nowPoints, nowKey, now);
            }
            l.act = act;
            if (chorus(time, now)) {
                return;
            }
            if (answer(now)) {
                return;
            }
            if (now < waitUntil) {
                return;
            }
            if (act == Act.WAKING) {
                waking(now);
                return;
            }
            if (play > 0) {
                cubPlay(now);
                return;
            }
            if (there) {
                atPlace(now);
            } else {
                go(now);
            }
        }

        /** Whether it and every pride mate here drank this evening (or found nothing to drink). */
        private boolean allDrank(long ev) {
            if (l.drankEvening != ev) {
                return false;
            }
            for (LionEntity m : l.mates()) {
                if (m.drankEvening != ev) {
                    return false;
                }
            }
            return true;
        }

        private void begin(Act newAct, long ev, int newPoint, int newPoints, long newKey, int now) {
            act = newAct;
            evening = ev;
            point = newPoint;
            points = newPoints;
            key = newKey;
            stand = null;
            place = Place.BED;
            standYaw = Float.NaN;
            there = false;
            trip.drop();
            l.getNavigation().stop();                           // not on along its old way while it waits its turn
            waitUntil = 0;
            drinksLeft = 0;
            sniffed = false;
            sitBreak = false;
            sitUntil = -1;
            wake = 0;
            greetee = null;
            greeted = false;
            play = 0;
            playWith = null;
            l.focus = null;
            int rank = l.rank();
            departAt = now + rank * (act == Act.WAKING ? WAKE_GAP : DEPART_GAP);
            nextEventAt = now + eventDelay();
        }

        private int eventDelay() {
            RandomSource r = l.getRandom();
            return l.isCub() ? PLAY_LEAST + r.nextInt(PLAY_SPREAD) : REST_EVENT_LEAST + r.nextInt(REST_EVENT_SPREAD);
        }

        // --- the roar chorus ------------------------------------------------------------------------------------------------

        /**
         * The chorus: at 18:30, 0:00 and 5:30 wherever they are, each member at its own delay (1 to 5 s from its id; the
         * pride's first at once), so the whole pride roars within 6 s; a missed one is roared within
         * {@link #CHORUS_WINDOW} after (a bed's skip to the morning). Lying or sitting, it gets up first; a clip plays out
         * first. Whether it is busy with the chorus now.
         */
        private boolean chorus(long time, int now) {
            byte d = l.doing();
            if (d == LionEntity.ROAR) {
                return true;
            }
            long[] c = latestChorus(time);
            if (l.roaredChorus == Long.MIN_VALUE) {
                l.roaredChorus = c[0];                          // a new lion never roars for a chorus from before it was there
                return false;
            }
            // a chorus it has not roared (a "/time set" either way counts as a new one, never days of silence)
            if (roarAt < 0 && c[0] != l.roaredChorus && time - c[1] <= CHORUS_WINDOW) {
                long bits = l.getUUID().getLeastSignificantBits() ^ l.getUUID().getMostSignificantBits();
                roarAt = now + (l.rank() == 0 ? 0 : CHORUS_LEAST + (int) Math.floorMod(bits, CHORUS_SPREAD + 1L));
                roarChorus = c[0];
            }
            if (roarAt < 0 || now < roarAt) {
                return false;
            }
            if (roarChorus != c[0] || time - c[1] > CHORUS_WINDOW) {
                l.roaredChorus = roarChorus;                    // its window went by (it was fighting): not roared late, alone
                roarAt = -1;
                return false;
            }
            if (d == LionEntity.REST || d == LionEntity.REST_DOWN) {
                l.setDoing(LionEntity.REST_UP);
                return true;
            }
            if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                l.setDoing(LionEntity.SIT_UP);
                return true;
            }
            if (d != LionEntity.CALM && d != LionEntity.MOOD_FLEHMEN) {
                return LionEntity.oneShot(d) && (d == LionEntity.REST_UP || d == LionEntity.SIT_UP);
            }
            l.getNavigation().stop();
            trip.drop();
            l.setDoing(LionEntity.ROAR);
            l.roaredChorus = roarChorus;
            roarAt = -1;
            return true;
        }

        // --- answering a mate -----------------------------------------------------------------------------------------------

        /**
         * A mate's greeting or a cub's nuzzle is answered: a standing adult greeted by a mate (its focus on it, playing its
         * greeting nearby) greets back at once; a lioness nuzzled by a cub gets up if lying and grooms it. Whether it is busy
         * answering now.
         */
        private boolean answer(int now) {
            byte d = l.doing();
            if (answering != null) {
                if (!answering.isAlive() || l.distanceTo(answering) > 6.0) {
                    answering = null;
                    return false;
                }
                if (d == LionEntity.REST_UP || d == LionEntity.SIT_UP || d == LionEntity.CUB_GROOM) {
                    return true;
                }
                if (d == LionEntity.REST || d == LionEntity.REST_DOWN) {
                    l.setDoing(LionEntity.REST_UP);
                    return true;
                }
                if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                    l.setDoing(LionEntity.SIT_UP);
                    return true;
                }
                if (d == LionEntity.CALM) {
                    if (l.faceYaw(yawTo(l.position(), answering.position()), LionEntity.MAX_TURN)) {
                        l.setDoing(LionEntity.CUB_GROOM);
                        answering = null;
                        there = false;                      // then back to its place (it lies down again there)
                    }
                    return true;
                }
                return false;
            }
            for (LionEntity m : l.mates()) {
                if (m.focus != l || l.distanceTo(m) > 6.0) {
                    continue;
                }
                byte md = m.doing();
                if (md == LionEntity.GREET && l.isAdult() && d == LionEntity.CALM && !greeted) {
                    l.getNavigation().stop();
                    l.setDoing(LionEntity.GREET);
                    greeted = true;
                    return true;
                }
                if (md == LionEntity.NUZZLE && l.has(LionEntity.CUB_GROOM) && m.clipTick() >= 0 && m.clipTick() < 10
                        && (d == LionEntity.CALM || d == LionEntity.REST || d == LionEntity.SIT)) {
                    answering = m;
                    return true;
                }
            }
            return false;
        }

        // --- on its way --------------------------------------------------------------------------------------------------

        private void go(int now) {
            byte d = l.doing();
            if (stand == null) {
                if (now < departAt) {
                    return;                                     // lying or sitting on until its turn
                }
                boolean down = d == LionEntity.REST || d == LionEntity.SIT;
                if (!down && !getUp(d)) {
                    return;                                     // lying or sitting it first sees where it goes: it may be there
                }
                if (!choosePlace(now)) {
                    waitUntil = now + UNLOADED_WAIT;           // its land is not loaded: it waits, never another place
                    return;
                }
                if (stand == null) {
                    return;                                     // nothing to go to (no water): the act moves on
                }
            }
            double dist = HomeGroup.flat(l.position(), stand);
            if (d == LionEntity.REST || d == LionEntity.REST_DOWN || d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                if (dist <= arriveDone()) {
                    arrive(now);                               // already at its place (its goal started again)
                } else {
                    getUp(d);
                }
                return;
            }
            if (d != LionEntity.CALM) {
                return;
            }
            if (trip.stand() != stand) {
                trip.to(stand);
            }
            switch (trip.step(l, dist > TROT_FROM ? LionEntity.TROT : place == Place.PLAY || place == Place.GREET ? LionEntity.STEP
                    : LionEntity.WALK, arrive(), arriveDone())) {
                case ARRIVED -> arrive(now);
                case CANNOT_REACH -> cannotReach(now);
                case UNLOADED -> waitUntil = now + UNLOADED_WAIT;
                default -> {
                }
            }
        }

        /** Gets up from lying or sitting; whether it is on its feet (calm) now. */
        private boolean getUp(byte d) {
            if (d == LionEntity.REST || d == LionEntity.REST_DOWN) {
                l.setDoing(LionEntity.REST_UP);
                return false;
            }
            if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                l.setDoing(LionEntity.SIT_UP);
                return false;
            }
            if (d == LionEntity.MOOD_FLEHMEN || d == LionEntity.MOOD_ALERT || d == LionEntity.MOOD_ANGRY) {
                l.setDoing(LionEntity.CALM);
            }
            return l.doing() == LionEntity.CALM;
        }

        /** Near enough its place: a bed exactly (the row's spacing holds only if each lies where it means to), elsewhere looser. */
        private double arrive() {
            return place == Place.BED ? BED_ARRIVE : l.isCub() ? CUB_ARRIVE : ARRIVE;
        }

        private double arriveDone() {
            return place == Place.BED ? BED_ARRIVE : l.isCub() ? CUB_ARRIVE_DONE : ARRIVE_DONE;
        }

        /** A place it cannot reach: it waits {@link #UNREACHABLE_WAIT} by its nearest mate, then tries again. */
        private void cannotReach(int now) {
            trip.drop();
            if (place == Place.PLAY || place == Place.GREET) {
                play = play > 0 ? 3 : 0;                        // a play or greeting spot it cannot reach: given up
                wake = wake == 2 ? 3 : wake;
                stand = null;
                place = Place.BED;
                return;
            }
            waitUntil = now + UNREACHABLE_WAIT;
            List<LionEntity> mates = l.mates();
            if (!mates.isEmpty()) {
                LionEntity m = mates.get(0);
                l.getNavigation().moveTo(m.getX(), m.getY(), m.getZ(), LionEntity.WALK);
            } else {
                l.getNavigation().stop();
            }
        }

        private void arrive(int now) {
            l.getNavigation().stop();
            there = true;
            trip.drop();
            nextEventAt = now + eventDelay();
        }

        // --- its places ------------------------------------------------------------------------------------------------------

        /** Chooses its place for this part of the day; false if its land is not loaded (it waits). */
        private boolean choosePlace(int now) {
            BlockPos den = l.den();
            int rank = l.rank();
            Level level = l.level();
            if (act == Act.WATER) {
                BlockPos w = l.water();
                if (w == null || l.waterAcross() <= 0) {
                    l.drankEvening = evening;                   // nothing to drink: done
                    return true;
                }
                if (!level.hasChunkAt(w)) {
                    return false;
                }
                Shore shore = Shore.find(level, w, l.waterAcross(), rank, this::crowded);
                if (shore == null) {
                    l.drankEvening = evening;                   // a dry pan: done
                    return true;
                }
                setStand(shore.stand(), Place.SHORE, shore.facing().toYRot());
                drinkFacing = shore.facing();
                drinksLeft = DRINKS_FEWEST + l.getRandom().nextInt(DRINKS_MOST - DRINKS_FEWEST + 1);
                return true;
            }
            if (act == Act.NIGHT && point < points - 1) {
                Vec3 middle = patrolPoint(den, evening, point);
                Vec3 off = HomeGroup.offset(rank, SPIRAL * Math.sqrt(rank + 1.0));
                Vec3 spot = middle.add(off);
                if (!level.hasChunkAt(BlockPos.containing(spot))) {
                    return false;
                }
                BlockPos s = standNear(spot.x, spot.z, STAND_REACH, den.getY());
                if (s == null) {
                    s = standNear(middle.x, middle.z, STAND_REACH + 4.0, den.getY());
                }
                setStand(s != null ? s : l.blockPosition(), Place.STOP, yawTo(middle, Vec3.atBottomCenterOf(s != null ? s : l.blockPosition())));
                return true;
            }
            // rest and the night's last point: its bed at the den
            Vec3 bed = bed();
            if (!level.hasChunkAt(BlockPos.containing(bed))) {
                return false;
            }
            BlockPos s = bedStand(bed);
            if (s == null) {
                s = standNear(bed.x, bed.z, STAND_REACH + 4.0, den.getY());  // no room in the row there: the nearest ground to its bed
            }
            setStand(s != null ? s : l.blockPosition(), Place.BED, l.denYaw());
            return true;
        }

        private void setStand(BlockPos s, Place kind, float yaw) {
            stand = s;
            place = kind;
            standYaw = yaw;
        }

        /** Whether a mate's middle (or where it is going) is within its spacing of x, z (for the shore). */
        private boolean crowded(double x, double z) {
            for (LionEntity m : l.mates()) {
                if (HomeGroup.flat(m.position(), x, z) < 2.5) {
                    return true;
                }
            }
            return false;
        }

        /** The nearest dry grass or soil ground within reach of x, z ({@link Grazing#grassStand}); or null. */
        @Nullable
        private BlockPos standNear(double x, double z, double reach, int y) {
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
                    BlockPos s = Grazing.grassStand(l.level(), cx, cz, y, GROUND_DY);
                    if (s != null && clear(s)) {
                        best = s;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        /** Whether its box fits standing at s (its columns clear of blocks and water). */
        private boolean clear(BlockPos s) {
            Vec3 at = Vec3.atBottomCenterOf(s);
            var box = l.getType().getDimensions().makeBoundingBox(at).deflate(0.05);
            return l.level().noBlockCollision(l, box) && !l.level().containsAnyLiquid(box);
        }

        /**
         * Its bed at the den: the pride lies in a row under the shade, one heading for all ({@link LionEntity#denYaw}), side
         * by side: the elder in the middle, the lionesses and cubs out from her by the pride's order (adults
         * {@link #ADULT_GAP} apart, a cub {@link #CUB_GAP} from its neighbour), the males at the row's ends
         * {@link #MALE_OUT} farther out. Worked out by each from its own mate list, so all agree.
         */
        private Vec3 bed() {
            List<LionEntity> pride = new ArrayList<>(l.mates());
            pride.add(l);
            pride.sort(LionEntity.ORDER);
            List<LionEntity> row = new ArrayList<>(), males = new ArrayList<>();
            for (LionEntity m : pride) {
                (m.form() == LionEntity.Form.MALE ? males : row).add(m);
            }
            double right = 0, left = 0, at = 0;
            LionEntity lastRight = null, lastLeft = null;
            for (int i = 0; i < row.size(); i++) {
                LionEntity m = row.get(i);
                double pos;
                if (i == 0) {
                    pos = 0;
                    lastRight = lastLeft = m;
                } else if (i % 2 == 1) {
                    pos = right += gap(lastRight, m);
                    lastRight = m;
                } else {
                    pos = left -= gap(lastLeft, m);
                    lastLeft = m;
                }
                if (m == l) {
                    at = pos;
                }
            }
            for (int i = 0; i < males.size(); i++) {
                double pos = row.isEmpty() && i == 0 ? 0 : i % 2 == 0 ? right + MALE_OUT : left - MALE_OUT;
                if (i % 2 == 0 && !row.isEmpty()) {
                    right = pos;
                } else if (i % 2 == 1) {
                    left = pos;
                }
                if (males.get(i) == l) {
                    at = pos;
                }
            }
            float yaw = l.denYaw() * Mth.DEG_TO_RAD;
            double sideX = Mth.cos(yaw), sideZ = Mth.sin(yaw);       // the pride's left as it lies
            BlockPos den = l.den();
            return new Vec3(den.getX() + 0.5 + sideX * at, den.getY(), den.getZ() + 0.5 + sideZ * at);
        }

        private static double gap(LionEntity a, LionEntity b) {
            return a.isCub() || b.isCub() ? CUB_GAP : ADULT_GAP;
        }

        /** Its bed's stand: flat ground under its whole length there, tried slid forward or back a block or two along the heading; or null. */
        @Nullable
        private BlockPos bedStand(Vec3 bed) {
            float yaw = l.denYaw() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            for (double slide : new double[] {0, 1, -1, 2, -2}) {
                double x = bed.x + fx * slide, z = bed.z + fz * slide;
                BlockPos s = Grazing.grassStand(l.level(), Mth.floor(x), Mth.floor(z), l.den().getY(), GROUND_DY);
                if (s != null && clear(s) && flat(s, fx, fz)) {
                    return s;
                }
            }
            return null;
        }

        /** Whether the ground under its front and hind feet is at its stand's height (a flat bed). */
        private boolean flat(BlockPos s, double fx, double fz) {
            double feet = l.measures.feetPx() / 16.0 * l.getScale();
            for (double along : new double[] {feet, -feet}) {
                BlockPos g = Grazing.grassStand(l.level(), Mth.floor(s.getX() + 0.5 + fx * along), Mth.floor(s.getZ() + 0.5 + fz * along), s.getY(), 0);
                if (g == null) {
                    return false;
                }
            }
            return true;
        }

        // --- at its place ----------------------------------------------------------------------------------------------------

        private void atPlace(int now) {
            byte d = l.doing();
            switch (place) {
                case SHORE -> {
                    if (d != LionEntity.CALM) {
                        return;
                    }
                    if (drinksLeft > 0 && drinkFacing != null) {
                        if (l.faceYaw(drinkFacing.toYRot(), LionEntity.MAX_TURN)) {
                            l.setDoing(LionEntity.DRINK);
                            drinksLeft--;
                        }
                        return;
                    }
                    l.drankEvening = evening;
                    l.setDoing(LionEntity.SIT_DOWN);           // sits by the water until the pride has drunk
                }
                case STOP -> {
                    if (d == LionEntity.MOOD_FLEHMEN) {
                        if (now >= sniffUntil) {
                            l.setDoing(LionEntity.CALM);
                        }
                        return;
                    }
                    if (d != LionEntity.CALM) {
                        return;
                    }
                    if (!Float.isNaN(standYaw) && !l.faceYaw(standYaw, LionEntity.MAX_TURN)) {
                        return;
                    }
                    if (l.form() == LionEntity.Form.MALE && !sniffed) {
                        sniffed = true;                         // the male sniffs the stop (the flehmen face), then sits
                        sniffUntil = now + SNIFF_TICKS;
                        l.setDoing(LionEntity.MOOD_FLEHMEN);
                        return;
                    }
                    l.setDoing(LionEntity.SIT_DOWN);
                }
                default -> atBed(now, d);
            }
        }

        /**
         * At its bed: it lies facing the pride's way; every 90 to 200 s an adult grooms lying, stretches up and lies down
         * again, or gets up for a sit; a cub gets up to play every 20 to 40 s.
         */
        private void atBed(int now, byte d) {
            if (d == LionEntity.REST) {
                if (now < nextEventAt) {
                    return;
                }
                nextEventAt = now + eventDelay();
                if (l.isCub()) {
                    startPlay(now);
                    return;
                }
                int roll = l.getRandom().nextInt(10);
                if (roll < 4) {
                    l.setDoing(LionEntity.GROOM);
                } else if (roll < 7) {
                    l.setDoing(LionEntity.STRETCH_UP);         // up with a yawn, then down again
                } else {
                    sitBreak = true;
                    l.setDoing(LionEntity.REST_UP);
                }
                return;
            }
            if (d == LionEntity.SIT) {
                if (sitUntil >= 0 && now >= sitUntil) {
                    sitUntil = -1;
                    l.setDoing(LionEntity.SIT_UP);
                }
                return;
            }
            if (d != LionEntity.CALM) {
                return;
            }
            if (HomeGroup.flat(l.position(), stand) > arriveDone()) {
                there = false;                                  // moved off (an answer, a roar): back to its bed
                return;
            }
            if (!l.faceYaw(standYaw, LionEntity.MAX_TURN)) {
                return;
            }
            if (sitBreak) {
                sitBreak = false;
                sitUntil = now + SIT_LEAST + l.getRandom().nextInt(SIT_SPREAD);
                l.setDoing(LionEntity.SIT_DOWN);
                return;
            }
            l.setDoing(LionEntity.REST_DOWN);
        }

        // --- a cub's play at the den -----------------------------------------------------------------------------------------

        /** A cub picks one thing: a pounce at a sibling or a lioness's tail, a wrestle beside a sibling, or a nuzzle at a lioness. */
        private void startPlay(int now) {
            List<LionEntity> cubs = new ArrayList<>(), lionesses = new ArrayList<>();
            for (LionEntity m : l.mates()) {
                if (l.distanceTo(m) > PLAY_REACH) {
                    continue;
                }
                if (m.isCub()) {
                    cubs.add(m);
                } else if (m.form() == LionEntity.Form.LIONESS) {
                    lionesses.add(m);
                }
            }
            RandomSource r = l.getRandom();
            int roll = r.nextInt(3);
            if (roll == 0 && !lionesses.isEmpty()) {
                playWith = lionesses.get(0);                    // the nearest
                playClip = LionEntity.NUZZLE;
            } else if (roll == 1 && !cubs.isEmpty()) {
                playWith = cubs.get(0);
                playClip = LionEntity.PLAY_WRESTLE;
            } else if (!cubs.isEmpty() || !lionesses.isEmpty()) {
                playWith = !cubs.isEmpty() && (lionesses.isEmpty() || r.nextBoolean()) ? cubs.get(0) : lionesses.get(0);
                playClip = LionEntity.PLAY_POUNCE;
            } else {
                return;                                         // nobody to play with: it lies on
            }
            play = 1;
            playSince = now;
            l.setDoing(LionEntity.REST_UP);
        }

        /** The spot a cub plays from, and whether it is there and facing its target, then the clip, then back to its bed. */
        private void cubPlay(int now) {
            byte d = l.doing();
            if (playWith == null || !playWith.isAlive()) {
                play = 3;
            }
            if (play == 1 && (now - playSince > PLAY_TICKS || l.distanceTo(playWith) > PLAY_REACH || playWith.temper() != Temper.NONE)) {
                play = 3;                                       // its playmate went off, or too long: back to bed
            }
            if (play == 1) {
                if (d != LionEntity.CALM) {
                    return;
                }
                Vec3 spot = playSpot();
                double dist = HomeGroup.flat(l.position(), spot.x, spot.z);
                if (dist > 0.5) {
                    l.getMoveControl().setWantedPosition(spot.x, l.getY(), spot.z, LionEntity.STEP);
                    if (l.horizontalCollision && l.getRandom().nextInt(40) == 0) {
                        play = 3;                               // something in the way: back to bed
                    }
                    return;
                }
                l.getNavigation().stop();
                Vec3 at = playClip == LionEntity.PLAY_POUNCE ? pounceAt() : playWith.position();
                if (!l.faceYaw(yawTo(l.position(), at), LionEntity.MAX_TURN)) {
                    return;
                }
                l.focus = playWith;
                l.setDoing(playClip);
                play = 2;
                return;
            }
            if (play == 2) {
                if (d == playClip) {
                    return;
                }
                byte pd = playWith.doing();
                if (playClip == LionEntity.NUZZLE && now - playSince <= PLAY_TICKS
                        && (pd == LionEntity.REST_UP || pd == LionEntity.SIT_UP || pd == LionEntity.CUB_GROOM)) {
                    return;                                     // she gets up and grooms it: it stays under her chin
                }
                l.focus = null;
                play = 3;
            }
            // back to its bed
            l.focus = null;
            play = 0;
            playWith = null;
            there = false;
            trip.drop();
        }

        /** Where a cub plays from: a pounce {@link #PLAY_OFF} short of its target; a wrestle beside a sibling; a nuzzle under a lioness's chin, facing her. */
        private Vec3 playSpot() {
            if (playClip == LionEntity.NUZZLE) {
                float yaw = playWith.getYRot() * Mth.DEG_TO_RAD;
                double ahead = playWith.measures.headFront() * playWith.getScale() - 0.5 + l.measures.headFront() * l.getScale();
                return playWith.position().add(-Mth.sin(yaw) * ahead, 0, Mth.cos(yaw) * ahead);
            }
            Vec3 target = playClip == LionEntity.PLAY_POUNCE ? pounceAt() : playWith.position();
            Vec3 from = l.position().subtract(target);
            double len = Math.sqrt(from.x * from.x + from.z * from.z);
            double off = playClip == LionEntity.PLAY_WRESTLE ? 0.9 : PLAY_OFF;
            if (len < 1.0E-3) {
                return target.add(off, 0, 0);
            }
            return target.add(from.x / len * off, 0, from.z / len * off);
        }

        /** What a cub pounces at: a sibling's middle, or a lioness's tail tip behind her. */
        private Vec3 pounceAt() {
            if (playWith.isCub()) {
                return playWith.position();
            }
            float yaw = playWith.getYRot() * Mth.DEG_TO_RAD;
            double back = playWith.measures.feetPx() / 16.0 * playWith.getScale() + 1.2;
            return playWith.position().add(Mth.sin(yaw) * back, 0, -Mth.cos(yaw) * back);
        }

        // --- waking ------------------------------------------------------------------------------------------------------------

        /**
         * Waking, one by one in the pride's order: a stretch up from lying; an adult then greets the adult just before it in
         * the order (both standing, side by side facing opposite ways, heads level about a block apart on the side the clip
         * sweeps to, both playing {@code pride_greet}); a cub nuzzles the nearest lioness; it waits to be greeted by the next
         * (at most {@link #GREET_WAIT}); then it sits facing out from the den.
         */
        private void waking(int now) {
            byte d = l.doing();
            switch (wake) {
                case 0 -> {
                    if (now < departAt) {
                        return;
                    }
                    if (d == LionEntity.REST) {
                        l.setDoing(LionEntity.STRETCH_UP);     // a slow feline wake-up, a yawn, on its feet
                        return;
                    }
                    if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                        l.setDoing(LionEntity.SIT_UP);
                        return;
                    }
                    if (!getUp(d)) {
                        return;
                    }
                    wake = 1;
                }
                case 1 -> {
                    greetee = whomToGreet();
                    wake = greetee == null ? 3 : 2;
                    wakeUntil = now + GREET_WAIT;
                    stand = null;
                }
                case 2 -> greet(now, d);
                case 3 -> {
                    // waits standing to be greeted by the next (an answer turns it to its greeting), then sits
                    if (d != LionEntity.CALM) {
                        return;
                    }
                    if (greeted || now >= wakeUntil || !anyoneToGreetMe()) {
                        wake = 4;
                    }
                }
                default -> {
                    // sits facing out, alert (and sits again after grooming a cub)
                    if (d == LionEntity.CALM && l.faceYaw(l.denYaw(), LionEntity.MAX_TURN)) {
                        l.setDoing(LionEntity.SIT_DOWN);
                    }
                }
            }
        }

        /** An adult greets the adult just before it in the pride's order; a cub nuzzles the nearest lioness; null: nobody. */
        @Nullable
        private LionEntity whomToGreet() {
            if (l.isCub()) {
                for (LionEntity m : l.mates()) {
                    if (m.form() == LionEntity.Form.LIONESS && l.distanceTo(m) <= 16.0) {
                        return m;
                    }
                }
                return null;
            }
            LionEntity before = null;
            for (LionEntity m : l.mates()) {
                if (m.isAdult() && LionEntity.ORDER.compare(m, l) < 0 && (before == null || LionEntity.ORDER.compare(m, before) > 0)) {
                    before = m;
                }
            }
            return before != null && l.distanceTo(before) <= 24.0 ? before : null;
        }

        /** Whether an adult after it in the order is here to greet it. */
        private boolean anyoneToGreetMe() {
            if (l.isCub()) {
                return false;
            }
            for (LionEntity m : l.mates()) {
                if (m.isAdult() && LionEntity.ORDER.compare(m, l) > 0 && l.distanceTo(m) <= 24.0) {
                    return true;
                }
            }
            return false;
        }

        private void greet(int now, byte d) {
            if (greetee == null || !greetee.isAlive() || now >= wakeUntil) {
                wake = 3;
                return;
            }
            if (d != LionEntity.CALM) {
                return;
            }
            byte gd = greetee.doing();
            boolean ready = l.isCub() ? gd == LionEntity.CALM || gd == LionEntity.SIT : gd == LionEntity.CALM;
            if (!ready) {
                l.getNavigation().stop();
                return;                                         // waits for its mate to be up and standing
            }
            Vec3 spot = greetSpot();
            double dist = HomeGroup.flat(l.position(), spot.x, spot.z);
            if (dist > 0.6) {
                BlockPos s = BlockPos.containing(spot.x, greetee.getY(), spot.z);
                if (stand == null || !stand.equals(s)) {
                    stand = s;
                    place = Place.GREET;
                    trip.to(s);
                }
                if (dist <= Trip.STRAIGHT) {
                    l.getMoveControl().setWantedPosition(spot.x, greetee.getY(), spot.z, LionEntity.STEP);
                    if (l.horizontalCollision && l.getRandom().nextInt(60) == 0) {
                        wake = 3;                               // cannot get beside it: given up
                    }
                    return;
                }
                switch (trip.step(l, LionEntity.WALK, 0.6, 1.0)) {
                    case CANNOT_REACH -> wake = 3;
                    default -> {
                    }
                }
                return;
            }
            l.getNavigation().stop();
            float want = l.isCub() ? yawTo(l.position(), greetee.position()) : greetee.getYRot() + 180.0F;
            if (!l.faceYaw(want, LionEntity.MAX_TURN)) {
                return;
            }
            l.focus = greetee;
            l.setDoing(l.isCub() ? LionEntity.NUZZLE : LionEntity.GREET);
            stand = null;
            place = Place.BED;
            wake = 3;
            wakeUntil = now + GREET_WAIT;
        }

        /**
         * Where it stands to greet: facing the opposite way to its mate, heads level about a block apart, on its mate's left
         * (the clip sweeps the head to its own left, so each sweeps toward the other); a cub under a lioness's chin.
         */
        private Vec3 greetSpot() {
            float yaw = greetee.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), leftX = Mth.cos(yaw), leftZ = Mth.sin(yaw);
            if (l.isCub()) {
                double ahead = greetee.measures.headFront() * greetee.getScale() - 0.5 + l.measures.headFront() * l.getScale();
                return greetee.position().add(fx * ahead, 0, fz * ahead);
            }
            double heads = (greetee.measures.headFront() * greetee.getScale() - 0.4) + (l.measures.headFront() * l.getScale() - 0.4);
            return greetee.position().add(fx * heads + leftX * 1.0, 0, fz * heads + leftZ * 1.0);
        }

        static float yawTo(Vec3 from, Vec3 to) {
            return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0F;
        }
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return DayGoal.yawTo(from, to);
    }

    // --- its temper --------------------------------------------------------------------------------------------------------

    /** Its temper's stages (one rule and one deadline each). */
    public enum Temper {
        NONE, WATCH, WARN, BLUFF, ATTACK, CALM, RING, HIDE
    }

    /** A player is noticed this near, crouching this near (blocks); watched up to this long (ticks); stared at again only after coming this much nearer. */
    static final double WATCH_REACH = 24.0, WATCH_CROUCHED = 12.0, NEARER = 3.0;
    static final int WATCH_TICKS = 200;
    /** The pride's nearest adult warns a player this near (blocks); a warning is remembered this long (ticks) and lasts this long. */
    static final double WARN_REACH = 10.0;
    static final int WARN_MEMORY = 600, WARN_TICKS = 50;
    /** A warned player still this near gets the bluff (blocks); it stops this far short (the gap from its head), galloping first from farther than this; at most this long (ticks). */
    static final double BLUFF_REACH = 6.0, BLUFF_STOP = 2.0, BLUFF_RUN_FROM = 3.0;
    static final int BLUFF_TICKS = 100;
    /** A player who closes to this gap from its head is struck (blocks). */
    static final double ATTACK_GAP = 1.0;
    /** A player moving toward it faster than this comes in (blocks a tick: 0.8 a second, a crouch-walk counts), over at least this long (ticks). */
    static final double COMING_IN = 0.04;
    static final int APPROACH_SPAN = 5;
    /** A player moving away faster than this (blocks a tick: 5 a second) within this reach of a lion at them sets off the chase. */
    static final double RUN_AWAY = 0.25, RUN_AWAY_REACH = 12.0;
    /**
     * A player who only came near, or ran, gets this many strikes (a lioness with cubs one more), or this long at a gallop
     * (ticks: 4 s; a lion gains about 1.7 blocks a second on a sprinting player, so one who ran from 6.5 is caught in
     * about 3 s and one from 9.5 gets away; real lions give up a charge after a few seconds).
     */
    static final int STRIKES_NEAR = 1, CHASE_TICKS = 80;
    /** Hurt, it is angry at whoever did it this long from the last hurt (ticks: 8 s). */
    public static final int REVENGE_TICKS = 160;
    /** Its head bead this near its target it strikes; this near at the hit's moment the hit lands (blocks). */
    static final double STRIKE_REACH = 0.25, HIT_SLACK = 0.45;
    /** At a gallop it pounces from this gap and swats from this one (blocks). */
    static final double POUNCE_FROM = 4.0, SWAT_FROM = 1.5;
    /** A target moving away faster than this is fleeing (blocks a tick: 2 a second): no pounce at it. */
    static final double FLEEING = 0.1;
    /** After a strike it backs off and stands its ground this long (ticks, and up to this much more). */
    static final int RECOVER = 20, RECOVER_SPREAD = 10;
    /** Its strikes' shove (Minecraft's knockback strength). */
    public static final double SHOVE = 0.6;
    /** Calming down: standing its ground this long (ticks). */
    static final int CALM_TICKS = 200;
    /** The ring: mates close round the hurt one this far out (blocks), at most this long (ticks); a cub hides at most this long. */
    static final double RING_RADIUS = 4.5;
    static final int RING_TICKS = 400;
    /** A hurt mate's second fighter is a male this near it (blocks), else the nearest lioness. */
    static final double SECOND_REACH = 24.0;
    /** How fast it turns to face what it watches, and what it strikes (degrees a tick). */
    static final float WATCH_TURN = 6.0F, STRIKE_TURN = 12.0F;
    static final int CHASE_REPATH = 4, CHASE_RETRY = 15;

    /**
     * Its temper, one goal of stages, what the moment calls for judged in one place ({@link #call}):
     * <ul>
     * <li>WATCH: a player noticed within {@link #WATCH_REACH} (half crouching): a standing lion stops and faces them
     * ({@code mood_alert}; a cub {@code mood_curious}); a lying or sitting one only lifts its head and stares (drawn).</li>
     * <li>WARN: within {@link #WARN_REACH} of the pride's nearest adult (a lioness of a pride with cubs: of her or the
     * nearest cub): it gets up, growls, {@code mood_angry}; remembered per player.</li>
     * <li>BLUFF: still within {@link #BLUFF_REACH} after the warning: {@code bluff_charge}, stopping {@link #BLUFF_STOP}
     * short; never touches a player standing still. A lioness with cubs skips it.</li>
     * <li>ATTACK: a player closing to {@link #ATTACK_GAP} of its head, coming back in after the bluff, or running (faster
     * than 5 blocks a second within {@link #RUN_AWAY_REACH} of a lion at them: the chase reflex); or a hurt. It gallops
     * and strikes, backing off between strikes; one strike (or 4 s of galloping) for a player who came near or ran, 8 s for
     * a hurt.</li>
     * <li>CALM: {@link #CALM_TICKS} standing its ground, then back to its day.</li>
     * <li>A pride defends: the hurt one and one mate fight; the other adults RING the hurt one facing the attacker; cubs
     * HIDE by a lioness, hissing, lying flat. Each by seeing it, never told.</li>
     * </ul>
     */
    static final class TemperGoal extends Goal {
        private final LionEntity l;
        private Temper stage = Temper.NONE;
        @Nullable
        private LivingEntity target;
        private boolean revenge;
        private int ticks, strikes, galloped, recoverUntil, repath;
        private boolean striking, bluffed;
        @Nullable
        private Vec3 spot;
        @Nullable
        private Call pending;
        private final WarnMemory warned = new WarnMemory(WARN_MEMORY), bluffs = new WarnMemory(WARN_MEMORY), chased = new WarnMemory(WARN_MEMORY);
        /** Where each noticed player was seen last (for whether they themselves come nearer). */
        private final Map<UUID, Seen> seen = new HashMap<>();
        /** Its target's distance, and where its target stood, over the last few ticks (for running away). */
        private final double[] distLog = new double[6];
        private final Vec3[] posLog = new Vec3[6];

        private record Call(Temper stage, LivingEntity target, boolean revenge, @Nullable Vec3 spot) {
        }

        private record Seen(Vec3 at, long time, double toward) {
        }

        TemperGoal(LionEntity l) {
            this.l = l;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!l.isAlive()) {
                return false;
            }
            warned.tidy(l, this::notices);
            bluffs.tidy(l, this::notices);
            chased.tidy(l, this::notices);
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
            return stage != Temper.NONE && l.isAlive();
        }

        @Override
        public void stop() {
            byte d = l.doing();
            if (d == LionEntity.MOOD_ALERT || d == LionEntity.MOOD_ANGRY || d == LionEntity.MOOD_CURIOUS) {
                l.setDoing(LionEntity.CALM);
            }
            l.getNavigation().stop();
            stage = Temper.NONE;
            target = null;
            l.temper = Temper.NONE;
            l.temperOn = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private double followRange() {
            return l.getAttributeValue(Attributes.FOLLOW_RANGE);
        }

        private boolean notices(Player p) {
            return p.isAlive() && !p.isCreative() && !p.isSpectator() && Attention.playerInReach(p, l.distanceTo(p), WATCH_REACH, WATCH_CROUCHED);
        }

        /** How far a player is in the warning's sense: from it, or (a lioness of a pride with cubs) from the nearest cub if nearer. */
        private double threatDistance(Player p) {
            double d = l.distanceTo(p);
            if (l.form() == LionEntity.Form.LIONESS) {
                for (LionEntity m : l.mates()) {
                    if (m.isCub()) {
                        d = Math.min(d, m.distanceTo(p));
                    }
                }
            }
            return d;
        }

        private boolean mother() {
            return l.form() == LionEntity.Form.LIONESS && l.prideHasCubs();
        }

        /**
         * How fast a player themselves comes toward it (blocks a tick, measured over at least {@link #APPROACH_SPAN} ticks;
         * above 0 coming in). Its own steps toward them do not count, and how often it is asked changes nothing.
         */
        private double approach(Player p) {
            long now = l.level().getGameTime();
            Seen s = seen.get(p.getUUID());
            if (s == null || now - s.time() > APPROACH_SPAN * 4L) {
                seen.put(p.getUUID(), new Seen(p.position(), now, 0.0));
                return 0.0;
            }
            if (now - s.time() < APPROACH_SPAN) {
                return s.toward();
            }
            double ax = l.getX() - p.getX(), az = l.getZ() - p.getZ(), a = Math.sqrt(ax * ax + az * az);
            double toward = a < 1.0E-3 ? 0.0 : ((p.getX() - s.at().x) * ax + (p.getZ() - s.at().z) * az) / a / (now - s.time());
            seen.put(p.getUUID(), new Seen(p.position(), now, toward));
            return toward;
        }

        /**
         * The one judge of what the moment calls for, strongest first: its own hurt (attack); a mate hurt (the second
         * fighter attacks, the other adults ring, cubs hide); a player too near the pride's nearest adult (attack, bluff or
         * a warning); a player noticed by one standing (a watch). Null: nothing.
         */
        @Nullable
        private Call call() {
            LivingEntity foe = l.angryAt();
            if (foe != null && l.distanceTo(foe) > followRange()) {
                l.calmDown();                                   // past its follow range: it gives up
                foe = null;
            }
            if (foe != null && l.isAdult()) {
                return new Call(Temper.ATTACK, foe, true, null);
            }
            for (LionEntity m : l.mates()) {
                LivingEntity mateFoe = m.isAlive() ? m.angryAt() : null;
                if (mateFoe == null || m.hurtAt() == null) {
                    continue;
                }
                if (l.isCub()) {
                    return new Call(Temper.HIDE, mateFoe, true, null);
                }
                if (secondFighter(m) == l) {
                    if (l.distanceTo(mateFoe) <= followRange()) {
                        return new Call(Temper.ATTACK, mateFoe, true, null);
                    }
                    continue;
                }
                if (!m.isCub() || l.form() == LionEntity.Form.LIONESS) {
                    return new Call(Temper.RING, mateFoe, true, m.hurtAt().add(HomeGroup.offset(l.rank(), RING_RADIUS)));
                }
            }
            if (foe != null) {
                return new Call(Temper.HIDE, foe, true, null);  // a hurt cub hides
            }
            Player p = l.level().getNearestPlayer(l.getX(), l.getY(), l.getZ(), WATCH_REACH, e -> e instanceof Player q && notices(q));
            if (p == null) {
                return null;
            }
            double d = l.distanceTo(p);
            double nearer = approach(p);
            boolean resting = l.lying() || l.sitting();
            if (l.isCub()) {
                for (LionEntity m : l.mates()) {
                    if (m.isAdult() && m.temperOn == p && (m.temper == Temper.WARN || m.temper == Temper.BLUFF || m.temper == Temper.ATTACK)) {
                        return new Call(Temper.HIDE, p, false, null);
                    }
                }
                return !resting && watchAgain(p, d) && !l.hunting() ? new Call(Temper.WATCH, p, false, null) : null;
            }
            List<LionEntity> adults = new ArrayList<>();
            for (LionEntity m : l.mates()) {
                if (m.isAdult()) {
                    adults.add(m);
                }
            }
            if (HomeGroup.nearestTo(l, adults, p)) {
                double threat = threatDistance(p);
                if (l.headGap(p) < ATTACK_GAP && (!bluffs.warnedLately(l, p) || nearer > COMING_IN)) {
                    return new Call(Temper.ATTACK, p, false, null);
                }
                if (bluffs.warnedLately(l, p) && threat < BLUFF_REACH && nearer > COMING_IN) {
                    return new Call(Temper.ATTACK, p, false, null);         // back in after the bluff
                }
                if (warned.warnedLately(l, p) && threat < BLUFF_REACH && !bluffs.warnedLately(l, p)) {
                    return new Call(mother() ? Temper.ATTACK : Temper.BLUFF, p, false, null);
                }
                if (threat < WARN_REACH && !warned.warnedLately(l, p)) {
                    return new Call(Temper.WARN, p, false, null);
                }
            }
            return !resting && watchAgain(p, d) && !l.hunting() ? new Call(Temper.WATCH, p, false, null) : null;
        }

        /** Watched again only when never watched, or come {@link #NEARER} blocks nearer than when its last watch ended. */
        private boolean watchAgain(Player p, double d) {
            Double was = warned.watchedAt(p);
            return was == null || d <= was - NEARER;
        }

        /**
         * A hurt mate's second fighter: a male within {@link #SECOND_REACH} of it (the nearest to it), else the nearest
         * lioness to it; the male hurt, the nearest lioness. Worked out by each from its own mates, so all agree.
         */
        @Nullable
        private LionEntity secondFighter(LionEntity hurt) {
            List<LionEntity> all = new ArrayList<>(l.mates());
            all.add(l);
            LionEntity best = null;
            double bestD = Double.MAX_VALUE;
            boolean wantMale = hurt.form() != LionEntity.Form.MALE;
            for (int pass = 0; pass < 2 && best == null; pass++) {
                for (LionEntity m : all) {
                    if (m == hurt || !m.isAlive() || m.isCub()) {
                        continue;
                    }
                    boolean male = m.form() == LionEntity.Form.MALE;
                    if (pass == 0 ? !(wantMale && male) : male) {
                        continue;
                    }
                    double d = m.distanceToSqr(hurt);
                    if (pass == 0 && d > SECOND_REACH * SECOND_REACH) {
                        continue;
                    }
                    if (d < bestD - 1.0E-6 || Math.abs(d - bestD) <= 1.0E-6 && best != null && m.getUUID().compareTo(best.getUUID()) < 0) {
                        best = m;
                        bestD = d;
                    }
                }
            }
            return best;
        }

        private static int weight(Temper s, boolean revenge) {
            return switch (s) {
                case ATTACK -> revenge ? 6 : 4;
                case RING, HIDE -> 5;
                case BLUFF, CALM -> 3;
                case WARN -> 2;
                case WATCH -> 1;
                case NONE -> 0;
            };
        }

        private void enter(Call c) {
            stage = c.stage();
            target = c.target();
            revenge = c.revenge();
            spot = c.spot();
            ticks = 0;
            strikes = 0;
            galloped = 0;
            striking = false;
            bluffed = false;
            recoverUntil = 0;
            repath = 0;
            if (l.doing() == LionEntity.MOOD_FLEHMEN) {
                l.setDoing(LionEntity.CALM);                    // any temper ends the sniff (its stages start from calm)
            }
            java.util.Arrays.fill(distLog, target == null ? 0 : l.distanceTo(target));
            java.util.Arrays.fill(posLog, target == null ? l.position() : target.position());
            l.getNavigation().stop();
            l.temper = stage;
            l.temperOn = target;
        }

        private void end() {
            stage = Temper.NONE;
        }

        private boolean valid(@Nullable LivingEntity e) {
            return e != null && e.isAlive() && !e.isRemoved() && e.level() == l.level() && Anger.canBeFoe(e);
        }

        @Override
        public void tick() {
            Call c = call();
            if (c != null && weight(c.stage(), c.revenge()) > weight(stage, revenge)) {
                enter(c);
            }
            ticks++;
            if (target != null) {
                distLog[ticks % distLog.length] = l.distanceTo(target);
                posLog[ticks % posLog.length] = target.position();
            }
            switch (stage) {
                case WATCH -> watch();
                case WARN -> warn();
                case BLUFF -> bluff();
                case ATTACK -> attack();
                case CALM -> calm();
                case RING -> ring();
                case HIDE -> hide();
                default -> {
                }
            }
        }

        /** Whether its target is running away: moving off faster than 5 blocks a second, within {@link #RUN_AWAY_REACH}. */
        private boolean runningAway() {
            if (!(target instanceof Player) || ticks < distLog.length) {
                return false;
            }
            double now = distLog[ticks % distLog.length], before = distLog[(ticks + 1) % distLog.length];
            return now <= RUN_AWAY_REACH && (now - before) / (distLog.length - 1) > RUN_AWAY;
        }

        /**
         * Whether its target itself is moving off (faster than {@link #FLEEING} blocks a tick, away from it): a pounce at a
         * runner lands short, so it runs it down and swats instead. Its own closing in does not count.
         */
        private boolean movingAway() {
            Vec3 now = posLog[ticks % posLog.length], before = posLog[(ticks + 1) % posLog.length];
            double ax = target.getX() - l.getX(), az = target.getZ() - l.getZ(), a = Math.sqrt(ax * ax + az * az);
            if (now == null || before == null || a < 1.0E-3) {
                return false;
            }
            return ((now.x - before.x) * ax + (now.z - before.z) * az) / a / (posLog.length - 1) > FLEEING;
        }

        /** How fast the gap to its target is closing (blocks a tick, over the last few ticks). */
        private double closing() {
            double now = distLog[ticks % distLog.length], before = distLog[(ticks + 1) % distLog.length];
            return Math.max(0.0, (before - now) / (distLog.length - 1));
        }

        /** The chase reflex: off after a player running away (one strike or 4 s), never again at once after its chase. Whether it set off. */
        private boolean chaseIfRunning() {
            if (l.isAdult() && runningAway() && !(target instanceof Player p && chased.warnedLately(l, p))) {
                LivingEntity t = target;
                enter(new Call(Temper.ATTACK, t, false, null));
                return true;
            }
            return false;
        }

        /** Gets up from lying or sitting; whether it is on its feet (calm, or in a mood clip) now. */
        private boolean onFeet() {
            byte d = l.doing();
            if (d == LionEntity.REST || d == LionEntity.REST_DOWN || d == LionEntity.GROOM) {
                l.setDoing(LionEntity.REST_UP);
                return false;
            }
            if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                l.setDoing(LionEntity.SIT_UP);
                return false;
            }
            return d != LionEntity.REST_UP && d != LionEntity.SIT_UP && d != LionEntity.STRETCH_UP;
        }

        /** WATCH: it stands and faces the player; ends when they are no longer noticed or at {@link #WATCH_TICKS}. */
        private void watch() {
            if (!(target instanceof Player p) || !valid(p) || !notices(p) || ticks > WATCH_TICKS || l.lying() || l.sitting()) {
                if (target instanceof Player p) {
                    warned.watched(p, l.distanceTo(p));
                }
                end();
                return;
            }
            byte want = l.isCub() ? LionEntity.MOOD_CURIOUS : LionEntity.MOOD_ALERT;
            byte d = l.doing();
            if (d == LionEntity.CALM) {
                l.getNavigation().stop();
                l.setDoing(want);
            }
            if (d == want || d == LionEntity.CALM) {
                l.face(p.getX(), p.getZ(), WATCH_TURN);
            }
        }

        /** WARN: up, a growl, facing them ({@code mood_angry}) for {@link #WARN_TICKS}; then what the moment calls for. */
        private void warn() {
            if (!valid(target)) {
                end();
                return;
            }
            if (chaseIfRunning()) {
                return;
            }
            if (!onFeet()) {
                return;
            }
            l.getNavigation().stop();
            l.face(target.getX(), target.getZ(), WATCH_TURN * 2);
            byte d = l.doing();
            if (d == LionEntity.CALM || d == LionEntity.MOOD_ALERT) {
                l.setDoing(LionEntity.MOOD_ANGRY);
            }
            if (ticks < WARN_TICKS) {
                return;
            }
            if (target instanceof Player p) {
                warned.warn(l, p);
            }
            Call next = call();
            if (next != null && next.stage() != Temper.WATCH) {
                enter(next);
            } else {
                toCalm();
            }
        }

        /** BLUFF: a short gallop if the lunge cannot reach, then {@code bluff_charge}, stopping {@link #BLUFF_STOP} short; then CALM. */
        private void bluff() {
            if (!valid(target) || ticks > BLUFF_TICKS) {
                toCalm();
                return;
            }
            if (chaseIfRunning()) {
                return;
            }
            if (!onFeet()) {
                return;
            }
            byte d = l.doing();
            double gap = l.headGap(target);
            PathNavigation nav = l.getNavigation();
            if (d == LionEntity.BLUFF) {
                if (gap > BLUFF_STOP + 0.1 && l.clipTick() < 12) {
                    l.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), LionEntity.WALK);
                } else {
                    nav.stop();
                    l.getMoveControl().setWantedPosition(l.getX(), l.getY(), l.getZ(), 0.0);
                }
                return;
            }
            if (bluffed) {
                toCalm();                                       // the bluff played out
                return;
            }
            if (d == LionEntity.MOOD_ANGRY || d == LionEntity.MOOD_ALERT) {
                l.setDoing(LionEntity.CALM);
                d = LionEntity.CALM;
            }
            if (d != LionEntity.CALM) {
                return;
            }
            if (gap > BLUFF_RUN_FROM) {
                chase(LionEntity.TROT);                         // a short run in (a trot, so it never overshoots its stop)
                return;
            }
            nav.stop();
            l.face(target.getX(), target.getZ(), STRIKE_TURN);
            l.setDoing(LionEntity.BLUFF);
            bluffed = true;
            if (target instanceof Player p) {
                bluffs.warn(l, p);
            }
        }

        /**
         * ATTACK: it gallops at its target and strikes ({@link #strike}), backing off half a block and standing its ground
         * between strikes. A hurt's fight ends when its anger does ({@link #call}: its time, or past its follow range); a
         * player who only came near or ran gets {@link #STRIKES_NEAR} (a mother one more), or {@link #CHASE_TICKS} of
         * galloping; then CALM.
         */
        private void attack() {
            if (revenge) {
                Call c = call();
                if (c == null || c.stage() != Temper.ATTACK || !c.revenge()) {
                    toCalm();
                    return;
                }
                target = c.target();
                l.temperOn = target;
            } else if (!valid(target) || l.distanceTo(target) > followRange()) {
                toCalm();
                return;
            }
            if (!onFeet()) {
                return;
            }
            PathNavigation nav = l.getNavigation();
            byte d = l.doing();
            int now = l.tickCount;
            if (isStrike(d)) {
                striking = true;
                int t = l.clipTick();
                if (d == LionEntity.POUNCE && t >= POUNCE_LAUNCH && t < POUNCE_HIT
                        || d == LionEntity.GALLOP_SWAT && t < hitTick(d)) {
                    // carried over the leap, or running on into the swat (it chains from the gallop)
                    l.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), LionEntity.GALLOP);
                } else {
                    nav.stop();
                    l.face(target.getX(), target.getZ(), STRIKE_TURN);
                }
                if (t == hitTick(d) || d == LionEntity.SWIPE_COMBO && t == COMBO_SECOND) {
                    tryHit();
                }
                return;
            }
            if (striking) {
                striking = false;                               // a strike played out: back off and stand its ground
                recoverUntil = now + RECOVER + l.getRandom().nextInt(RECOVER_SPREAD);
                l.setDoing(LionEntity.MOOD_ANGRY);
            }
            if (now < recoverUntil) {
                if (now > recoverUntil - RECOVER + 6) {
                    nav.stop();
                } else {
                    float yaw = l.getYRot() * Mth.DEG_TO_RAD;
                    l.getMoveControl().setWantedPosition(l.getX() + Mth.sin(yaw) * 0.5, l.getY(), l.getZ() - Mth.cos(yaw) * 0.5, LionEntity.STEP);
                }
                l.face(target.getX(), target.getZ(), WATCH_TURN);
                return;
            }
            if (!revenge && (strikes >= allowedStrikes() || galloped > CHASE_TICKS)) {
                if (target instanceof Player p) {
                    bluffs.warn(l, p);                          // dealt with: struck again only if they come in again
                    chased.warn(l, p);                          // and not run after again at once (a lion gives up a chase)
                }
                toCalm();
                return;
            }
            if (d == LionEntity.MOOD_ANGRY || d == LionEntity.MOOD_ALERT) {
                l.setDoing(LionEntity.CALM);
                d = LionEntity.CALM;
            }
            if (d != LionEntity.CALM) {
                return;
            }
            if (touches(STRIKE_REACH)) {
                nav.stop();
                l.setDoing(pickStrike());
                strikes++;
                return;
            }
            double gap = l.headGap(target);
            boolean galloping = l.galloping();
            // the swat on the run: from SWAT_FROM, but only once the gap will have closed by its hit (a runner keeps going)
            if (galloping && gap <= SWAT_FROM && gap - closing() * hitTick(LionEntity.GALLOP_SWAT) <= HIT_SLACK && l.has(LionEntity.GALLOP_SWAT)) {
                l.setDoing(LionEntity.GALLOP_SWAT);
                strikes++;
                return;
            }
            if (galloping && gap <= POUNCE_FROM && gap > SWAT_FROM && !movingAway()) {
                l.setDoing(LionEntity.POUNCE);
                strikes++;
                return;
            }
            chase(LionEntity.GALLOP);
            galloped++;
        }

        private int allowedStrikes() {
            return mother() ? STRIKES_NEAR + 1 : STRIKES_NEAR;
        }

        /** A strike standing: a male's swipes (the heavy club and, only for a hurt, the 1-2 combo), a lioness's swipes and bite. */
        private byte pickStrike() {
            RandomSource r = l.getRandom();
            if (l.form() == LionEntity.Form.MALE) {
                byte[] picks = revenge ? new byte[] {LionEntity.SWIPE, LionEntity.SWIPE_QUICK, LionEntity.BITE, LionEntity.SWIPE_HEAVY, LionEntity.SWIPE_COMBO}
                        : new byte[] {LionEntity.SWIPE, LionEntity.SWIPE_QUICK, LionEntity.SWIPE_HEAVY};
                return picks[r.nextInt(picks.length)];
            }
            byte[] picks = {LionEntity.SWIPE, LionEntity.SWIPE_QUICK, LionEntity.BITE};
            return picks[r.nextInt(picks.length)];
        }

        /** The pounce leaves the ground at its clip's tick 6 and strikes landing at 15; the combo's second hook lands at 19 (Neon's card). */
        static final int POUNCE_LAUNCH = 6, POUNCE_HIT = 15, COMBO_SECOND = 19;

        private static boolean isStrike(byte d) {
            return d >= LionEntity.SWIPE && d <= LionEntity.GALLOP_SWAT;
        }

        /** Each strike's hit moment (its clip's tick; Neon's card). */
        static int hitTick(byte d) {
            return switch (d) {
                case LionEntity.SWIPE, LionEntity.SWIPE_COMBO, LionEntity.GALLOP_SWAT -> 9;
                case LionEntity.SWIPE_QUICK -> 8;
                case LionEntity.SWIPE_HEAVY -> 10;
                case LionEntity.BITE -> 7;
                case LionEntity.POUNCE -> POUNCE_HIT;
                default -> -1;
            };
        }

        private void chase(double pace) {
            PathNavigation nav = l.getNavigation();
            if (--repath > 0) {
                nav.setSpeedModifier(pace);
                return;
            }
            boolean found = nav.moveTo(target, pace);
            Path path = nav.getPath();
            repath = found && path != null && path.canReach() ? CHASE_REPATH : CHASE_RETRY;
        }

        private boolean touches(double slack) {
            return l.headBox().inflate(slack).intersects(target.getBoundingBox());
        }

        private void tryHit() {
            if (touches(HIT_SLACK)) {
                l.hitLands(target);
            }
        }

        private void toCalm() {
            stage = Temper.CALM;
            ticks = 0;
            l.temper = Temper.CALM;
            l.getNavigation().stop();
        }

        /** CALM: standing its ground facing them ({@code mood_angry}, tail lashing) for {@link #CALM_TICKS}, then back to its day. */
        private void calm() {
            if (ticks > CALM_TICKS || !valid(target)) {
                if (target instanceof Player p && valid(p)) {
                    warned.watched(p, l.distanceTo(p));     // not stared at again unless nearer
                }
                end();
                return;
            }
            if (chaseIfRunning()) {
                return;
            }
            if (!onFeet()) {
                return;
            }
            l.getNavigation().stop();
            l.face(target.getX(), target.getZ(), WATCH_TURN);
            if (l.doing() == LionEntity.CALM || l.doing() == LionEntity.MOOD_ALERT) {
                l.setDoing(LionEntity.MOOD_ANGRY);
            }
        }

        /** RING (an adult): it trots to its spot round where its mate was hurt and stands facing the attacker; ends when no mate is angry, or at {@link #RING_TICKS}. */
        private void ring() {
            Call c = call();
            if (c == null || c.stage() != Temper.RING || ticks > RING_TICKS) {
                toCalm();
                return;
            }
            target = c.target();
            spot = c.spot();
            l.temperOn = target;
            if (!onFeet()) {
                return;
            }
            standAt(spot, LionEntity.TROT);
        }

        /** HIDE (a cub): to the nearest lioness's side, a hiss, then flat on the ground; ends when the danger is over. */
        private void hide() {
            Call c = call();
            if (c == null || c.stage() != Temper.HIDE) {           // hidden as long as the danger lasts
                byte d = l.doing();
                if (d == LionEntity.REST || d == LionEntity.REST_DOWN) {
                    l.setDoing(LionEntity.REST_UP);
                }
                end();
                return;
            }
            target = c.target();
            l.temperOn = target;
            LionEntity by = null;
            for (LionEntity m : l.mates()) {
                if (m.form() == LionEntity.Form.LIONESS) {
                    by = m;
                    break;
                }
            }
            Vec3 at = l.position();
            if (by != null) {
                float yaw = by.getYRot() * Mth.DEG_TO_RAD;
                Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
                Vec3 away = target.position().subtract(by.position());
                double s = side.x * away.x + side.z * away.z > 0 ? -1.2 : 1.2;      // her far side from the danger
                at = by.position().add(side.scale(s));
            }
            byte d = l.doing();
            if (d == LionEntity.REST || d == LionEntity.REST_DOWN) {
                if (HomeGroup.flat(l.position(), at.x, at.z) <= 1.5) {
                    return;                                     // flat on the ground by her
                }
                l.setDoing(LionEntity.REST_UP);
                return;
            }
            if (d == LionEntity.REST_UP || d == LionEntity.SIT_UP || d == LionEntity.STRETCH_UP) {
                return;
            }
            if (d == LionEntity.SIT || d == LionEntity.SIT_DOWN) {
                l.setDoing(LionEntity.SIT_UP);
                return;
            }
            if (HomeGroup.flat(l.position(), at.x, at.z) > 1.0) {
                if (d == LionEntity.MOOD_ANGRY || d == LionEntity.MOOD_CURIOUS) {
                    l.setDoing(LionEntity.CALM);
                }
                if (--repath <= 0) {
                    repath = 20;
                    l.getNavigation().moveTo(at.x, at.y, at.z, LionEntity.TROT);
                }
                return;
            }
            l.getNavigation().stop();
            l.face(target.getX(), target.getZ(), WATCH_TURN * 2);
            if (d == LionEntity.CALM) {
                l.setDoing(LionEntity.MOOD_ANGRY);             // the kitten's spit-hiss
            } else if (d == LionEntity.MOOD_ANGRY && l.doingFor() > 40) {
                l.setDoing(LionEntity.REST_DOWN);              // then flat
            }
        }

        /** Trots to a spot and stands there facing its target ({@code mood_angry}). */
        private void standAt(Vec3 where, double pace) {
            PathNavigation nav = l.getNavigation();
            double gap = HomeGroup.flat(l.position(), where.x, where.z);
            byte d = l.doing();
            if (gap > 1.0 && !(gap < 2.0 && nav.isDone() && ticks > 20)) {
                if (d == LionEntity.MOOD_ANGRY || d == LionEntity.MOOD_ALERT) {
                    l.setDoing(LionEntity.CALM);
                }
                if (--repath <= 0) {
                    repath = 20;
                    nav.moveTo(where.x, where.y, where.z, pace);
                }
                return;
            }
            nav.stop();
            l.face(target.getX(), target.getZ(), WATCH_TURN);
            if (l.doing() == LionEntity.CALM) {
                l.setDoing(LionEntity.MOOD_ANGRY);
            }
        }
    }
}
