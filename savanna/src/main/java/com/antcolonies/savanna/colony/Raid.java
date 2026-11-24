package com.antcolonies.savanna.colony;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.AwayLife;
import com.antcolonies.colony.AwayReport;
import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.Wounds;
import com.antcolonies.fight.FightDiary;
import com.antcolonies.fight.FightNumbers;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleLoads;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.species.SavannaNumbers;
import com.antcolonies.watch.Watch;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One Matabele raid, from the call at the nest to the last ant home (the raid rewrite, step 3;
 * {@code docs/plans/raid-rewrite.md}, "The raid in play" and "The new design"). Real Megaponera analis (Longhurst and
 * Howse 1979, Hoelldobler et al. 1994, Frank et al. 2017 and 2018, Yusuf 2010): a scout home from termites recruits at
 * the nest, the column follows her line out, halts a little short of the termites and bunches up round her till its
 * tail is in, rushes in (the majors bite the mud open, the minors go in and sting), gathers again where it halted with
 * the stung termites in its jaws, and walks home on the same line.
 *
 * <p><b>One small stage machine.</b> The raid is always in one of seven stages, in this order: {@link Stage#MUSTER},
 * {@link Stage#MARCH_OUT}, {@link Stage#HALT}, {@link Stage#ATTACK}, {@link Stage#REGROUP}, {@link Stage#MARCH_HOME},
 * {@link Stage#DONE}. Every stage has one rule that ends it and one deadline; at its deadline it moves on with whoever
 * is there, and an ant left behind is let go to walk home along the trail by itself ({@link #takeLetGo}). No stage
 * waits on a single ant. From any stage the raid may turn home ({@link #turnHome}: dusk, rain, the scout lost on the
 * way out, the trail lost, land not loaded ahead, nobody at the spot).
 *
 * <p><b>One judge of each question.</b> "Are termites there now" is {@link #termitesThere}, asked before the muster
 * (by {@link Raids}) and again at the halt, and by nobody else. "Is the attack over" is asked only here: no termite
 * stung or gone for in a while ({@link #fightNote}), or its deadline. What the raid stung is its own list
 * ({@link #stung}), so what one stage stings the next can find and carry.
 *
 * <p>The two walks are the kept march ({@link RaidMarch}); the fight is the shared fight ({@code FightGoal}), each
 * raider sent in with a fight order only this raid ends; each raider's mind ({@code MatabeleRaidGoal}) only walks to the
 * place it is told ({@link #orderFor}).
 *
 * <p><b>The diary</b> ({@code [raid]} lines in the colony's black box and the log; the raid chain tests read it): one
 * line as a stage begins ({@code [raid] halt: begins, N ant(s)}), one as it ends with the count and the reason
 * ({@code [raid] halt: ends after S s, N ant(s): the tail is in}), a turn home as a reason starting
 * {@code turns home:}, and last {@code [raid] done: N home, C termite(s) on the pile, H hurt carried, L let go}.
 *
 * <p><b>With or without bodies</b> (the rewrite's step 4). A raid nobody is near goes as numbers ({@link #goesAsNumbers}):
 * the same stages in the same order, each one's time worked out from the way's length and that stage's own rule and
 * deadline ({@link #stepAsNumbers}), the same judge at the halt, the catch by the one rule for raiders going as numbers
 * ({@link #takeHidden}), the same diary lines. A raider out of sight past the colony's away grace goes on as one of
 * the raid's numbers, and when none has a body left the whole raid does ({@link #goOnAsNumbers}); when a watcher comes
 * near, bodies are put at the stage's place and the same raid goes on with them ({@link #takeBodies}). It is saved with
 * the colony ({@link #save}, {@link #load}), so a reload in mid-raid goes on from its stage.
 *
 * <p>Numbers: the species file's {@code raids.column}, {@code raids.carry}, {@code raids.hours_and_sound}, and for the
 * raid as numbers {@code raids.away} (its pace, the attack's length, what the fight costs).
 */
public final class Raid implements FightDiary {
    /** The raid's stages, in order, with the words the diary uses for each. */
    public enum Stage {
        MUSTER("muster"), MARCH_OUT("march out"), HALT("halt"), ATTACK("attack"), REGROUP("regroup"), MARCH_HOME("march home"),
        DONE("done");

        private final String words;

        Stage(String words) {
            this.words = words;
        }

        public String words() {
            return words;
        }
    }

    /**
     * Where a raider should walk now and how fast, or that it should stand where it is ({@code to} null); {@code take}:
     * the stung termite (or hurt nestmate) it is to pick up there, null for none; {@code onTheMove}: a marching place
     * that moves on with the column, walked toward without ever settling at it.
     */
    public record Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable UUID take, boolean onTheMove) {
        public Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt) {
            this(to, pace, lookAt, null);
        }

        public Order(@Nullable BlockPos to, double pace, @Nullable BlockPos lookAt, @Nullable UUID take) {
            this(to, pace, lookAt, take, false);
        }

        /** A place in the moving column on the trail. */
        static Order marching(BlockPos to, double pace, @Nullable BlockPos lookAt) {
            return new Order(to, pace, lookAt, null, true);
        }

        Order slower(double by) {
            return new Order(to, pace * by, lookAt, take, onTheMove);
        }
    }

    /**
     * On the way out (mustering and marching out) a raider climbs this many times its normal climbing speed: up the
     * nest's shaft and down the hump at about a walk, so the column does not pile up on the nest's top (measured in the
     * march test: a major climbs 1.1 blocks a second at the column's pace and walks 2.0 to 2.3). For the look.
     */
    static final double MARCH_CLIMB = 2.0;
    /** A raider picks up a termite this near it (blocks). */
    public static final double PICK_REACH = 1.6;
    /** A raider carries a hurt nestmate only if at least this share of its width (my pick: minors carry minors). */
    private static final double RESCUER_SIZE = 0.8;
    /** A termite a raider found no way to is left to the others this long (ticks; BUGS 76: never searched every tick). */
    private static final int NO_WAY_TICKS = 200;
    /** A bunch spreads this many blocks for the square root of its ants (about 3 ants to a block of ground; my pick). */
    private static final double BUNCH_SPREAD = 0.35;
    /** A straggler, or the scout coming to the muster, hurries at this pace. */
    private static final double HURRY_PACE = 1.15;
    /** A raider counts as at the halting place this near its middle (blocks, flat): the tail is in when all are. */
    private static final double TAIL_IN = RaidMarch.NEAR + 2.0;
    /** A raider farther than this (blocks, flat) from the halting place walks the trail up to it, and is left behind at a deadline. */
    private static final double STRAGGLER_FAR = 10.0;
    /**
     * A raider is home this near the entrance (blocks, flat and up or down): there the column breaks up and each ant goes
     * in by itself (BUGS 85: a dead mound's chimney mouth is an open hole, no cell to stand in).
     */
    private static final double HOME = 8.0;
    /** On the way home an ant this much past the home reach from the entrance leaves the line and walks in (my pick). */
    private static final double STREAM_IN = 4.0;
    /** How often (ticks) the raid works itself out. */
    private static final int EVERY = 5;
    /** A march may last a second for each block from the nest to the spot and half a minute more (the plan's table). */
    private static final long MARCH_PER_BLOCK = 20;
    private static final long MARCH_SPARE = 600;
    /** A member drafted away by another fight longer than this (ticks) is let go (my pick, 30 s). */
    private static final long DRAFT_KEEP = 600;
    /** The raid's clocks wait while it was not worked out for longer than this (ticks: the colony out of sight; my pick). */
    private static final long HOLD_AFTER = 100;
    /** At an empty spot this many ants at the front walk on to it and look round while the rest stay bunched. */
    private static final int LOOKERS = 3;
    /** A raider sent back by the leash fights again once this share of the leash from the spot. */
    private static final double COME_BACK = 0.5;

    private final Colony colony;
    private final SavannaNumbers.ColumnNumbers numbers;
    private final SavannaNumbers.CarryNumbers carry;
    private final Raids.Find find;
    /** The scent trail the raid walks, and the march along it, out and home. */
    private final ScentTrails.Trail trail;
    private final SavannaNumbers.TrailNumbers trailNumbers;
    /** (made again when bodies are put on the trail: a raid that set out in land not loaded knew no ground for its start) */
    private RaidMarch march;
    private Route out;
    private Route back;
    /** The raiders in marching order, the front first, and the same as a set. */
    private final List<UUID> members = new ArrayList<>();
    private final Set<UUID> memberSet = new HashSet<>();
    @Nullable
    private UUID leader;
    /** The whole raid's size, real ants and the numbers who go along. */
    private int size;
    private int numbersAlong;
    /**
     * Whether nobody in the raid has a body now (its colony out of sight, or every raider out of sight past the grace):
     * it goes on as numbers, and where its front is comes from the time: {@code numbersAt} blocks along the stage's
     * way at {@code numbersSince}, walking on at the numbers' pace ({@link #frontAsNumbers}).
     */
    private boolean asNumbers;
    private double numbersAt;
    private long numbersSince;
    /** How far the spot is from the nest (blocks, flat): the marches' deadline is counted from it. */
    private final int blocksOut;
    /** What the judge counted at the spot before the muster. */
    private final Termites counted;

    private Stage stage = Stage.MUSTER;
    private long stageSince;
    private final long startedAt;
    private long updatedAt = -EVERY;
    /** Each raider's place now. */
    private final Map<UUID, Order> orders = new HashMap<>();
    /** Each raider's own number for its place in a bunch (given once, so one ant leaving moves nobody else's place; BUGS 101). */
    private final Map<UUID, Integer> formIndex = new HashMap<>();
    /** When the column left the nest's mouth (game time; -1 while it musters). */
    private long leftAt = -1;
    /** The halt: what the judge found at the spot as the front arrived (null before), and whether the spot was empty. */
    @Nullable
    private Termites atHalt;
    private boolean spotEmpty;
    /** The attack: when it began (-1 not yet), how many were bunched then, and when a termite was last stung or gone for. */
    private long attackedAt = -1;
    private int gatheredAtAttack;
    private long lastActionAt = -1;
    /** The attack: the raiders sent in, and those the leash called back (they fight again once near the spot). */
    private final Set<UUID> sentIn = new HashSet<>();
    private final Set<UUID> returning = new HashSet<>();
    /** The raid's own list of what it stung: found in the attack, carried in the regroup. */
    private final Set<UUID> stung = new LinkedHashSet<>();
    /** Each prey-taker's termite, and the termites nobody found a way to (till when). */
    private final Map<UUID, UUID> taking = new HashMap<>();
    private final Map<UUID, Long> noWay = new HashMap<>();
    /**
     * Rescue (M8 step 5): raiders hurt at the spot (a foe clamped on, or legs gone), out of the raid, standing and
     * calling for help; in the regroup a raider with empty jaws is sent to each, pulls the foe off and carries it home.
     * Those nobody took are let go when the raid leaves the spot, to limp home.
     */
    private final Set<UUID> hurtHere = new LinkedHashSet<>();
    private final Map<UUID, Long> pullingSince = new HashMap<>();
    private int rescued;
    private int hurtAtSpot;
    /** What happened, for the diary, the colony list and the tests. */
    private int cameHome;
    private int carried;
    private int carriers;
    private int letGoCount;
    private String ended = "";
    private final Map<String, Integer> lost = new TreeMap<>();
    /** The termites the raiders going as numbers took from under the mud at the attack ({@link RaidCatch}), and from whom. */
    private int hiddenCatch;
    @Nullable
    private UUID hiddenFrom;
    /** The raiders let go out in the field since the colony last asked ({@link #takeLetGo}), and why. */
    private final List<LetGo> letGo = new ArrayList<>();

    /** One raider let go of the raid, and why. */
    record LetGo(UUID ant, String why) {
    }

    /** Since when each member has been out of sight (kept for the colony's away grace, then it goes on as a number). */
    private final Map<UUID, Long> unseenSince = new HashMap<>();
    /**
     * The termites in each raider's jaws as last seen (only those holding some): one that goes over to the numbers
     * after leaving the world (its land unloaded, or its colony folded in) takes them along, they are not lost.
     */
    private final Map<UUID, Integer> jaws = new HashMap<>();
    /** How many raiders with a body went over to the raid's numbers (out of sight past the grace, or the colony was). */
    private int wentToNumbers;
    /** Since when each member is held by another fight order (drafted by the nest's defence). */
    private final Map<UUID, Long> draftedSince = new HashMap<>();
    /** Whether the raid reached its spot and brought nothing back from it. */
    private boolean foundNothing;
    /** A turn home asked for from outside (dusk, rain), taken at the next update; and why the raid turned home, null if it did not. */
    @Nullable
    private String turnAsked;
    @Nullable
    private String turned;
    private boolean turnedAtEdge;
    /** Sent outside the raid hours by a command: it keeps no hours (no turn at dusk or in rain). */
    private boolean keepsNoHours;
    /** The way home: whether the ant nearest home has been made the front for this walk. */
    private boolean homeLed;
    /** When the column last squeaked at each player near it, and when the walk home was last told in the diary. */
    private final Map<UUID, Long> squeakedAt = new HashMap<>();
    private long homeSaidAt = Long.MIN_VALUE / 2;
    /** The world the raid last worked in (for the time of day in its diary lines). */
    @Nullable
    private ServerLevel lastLevel;
    /** Tests: the members made out of sight by hand, and a raid that is never worked out any more. */
    private final Set<UUID> hiddenForTests = new HashSet<>();
    private boolean hungForTests;

    /**
     * What the judge found at a spot: termite workers seen there ({@code seen}), or none seen but the termite colony's
     * own record says it feeds there now, under cover ({@code underCover}).
     */
    public record Termites(int seen, boolean underCover, boolean byTheLand) {
        public Termites(int seen, boolean underCover) {
            this(seen, underCover, false);
        }

        public boolean any() {
            return seen > 0 || underCover || byTheLand;
        }

        String words() {
            return seen > 0 ? seen + " termite(s) counted there" : underCover ? "0 termite(s) counted there, but their colony feeds there under cover"
                    : byTheLand ? "0 termite(s) counted there, no termite colony on record yet: it hunts by the land" : "0 termite(s) counted there";
        }
    }

    /**
     * How far round the spot (blocks) the raid counts termites and its raiders look for them: the shared fight looks in
     * a box this far each way, and that box lies wholly inside the leash's circle ({@code raids.column.leash}), so no
     * raider is ever sent after a termite the leash would call it back from (about 10 blocks for a leash of 14).
     */
    public static double reach(SavannaNumbers.ColumnNumbers numbers) {
        return numbers.leash() / Math.sqrt(2.0);
    }

    /**
     * <b>The one judge of "termites here now".</b> Where the land is loaded it counts what a player would see: living
     * termite workers, not stung, within the raid's {@link #reach} of the spot (soldiers alone are no reason to raid,
     * and a termite hidden in its own nest past the feeding ways is not at the spot). With none seen it asks the
     * nearest termite colony's own record: a feeding place it feeds at by day within reach that it does not keep off
     * ({@link FeedingSpots#byDayPlaces}; termites under the mud are often numbers, not bodies). A termite colony that
     * lives as numbers itself (nobody near it, or its land not loaded) has no such places on record and no block of
     * its land may be read: its own word is taken, that it lives, has workers and does not keep off the spot (the rule
     * raids out of sight always asked, and the one the soak of step 1 found right). And where the raiders' own colony
     * lives as numbers and no living termite colony is on record in its scouts' reach at all (the termites' land not
     * made yet: their colony is put in when a player first comes near their mound), it hunts by the land
     * ({@link RaidCatch#noTermitesOnRecord}).
     */
    public static Termites termitesThere(ServerLevel level, Colony raiders, BlockPos at) {
        double reach = raiders.profile().section(SavannaNumbers.COLUMN).map(Raid::reach).orElse(10.0);
        boolean loaded = level.isPositionEntityTicking(at);
        if (loaded) {
            int seen = inTheOpen(level, raiders, at, reach).size();
            if (seen > 0) {
                return new Termites(seen, false);
            }
        }
        int termiteReach = raiders.profile().section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT).termiteReach();
        Colony termites = RaidCatch.termitesAt(level, at, termiteReach);
        if (termites == null) {
            return new Termites(0, false, raiders.away().isAway(level) && RaidCatch.noTermitesOnRecord(level, raiders));
        }
        if (termites.keepsOff(level, at)) {
            return new Termites(0, false);
        }
        if (!loaded || !termites.away().isHere(level)) { // (away, or in its grace: its parts stand still, its places are not kept)
            return new Termites(0, true);
        }
        for (FeedingSpots.ByDay place : FeedingSpots.byDayPlaces(level, termites)) {
            if (flat(place.food(), at) <= reach) {
                return new Termites(0, true);
            }
        }
        return new Termites(0, false);
    }

    /** The living termite workers in the open within {@code reach} of a spot: what a player would see there. */
    private static List<AbstractAntEntity> inTheOpen(ServerLevel level, Colony raiders, BlockPos at, double reach) {
        return level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(at).inflate(reach, 6.0, reach),
                insect -> insect.isAlive() && !insect.isParalysed() && !insect.isDowned() && !(insect instanceof MatabeleEntity)
                        && !(insect instanceof TermiteSoldierEntity) && !raiders.getId().equals(insect.getColonyId())
                        && !insect.isSheltered() && flat(insect.blockPosition(), at) <= reach);
    }

    /**
     * A raid to this find along this trail: {@code raiders} the real ants in marching order, the front first, and
     * {@code numbersAlong} more going as numbers. With no real ant at all (a colony nobody is near) the whole raid goes
     * as numbers.
     */
    Raid(ServerLevel level, Colony colony, SavannaNumbers.ColumnNumbers numbers, @Nullable SavannaNumbers.CarryNumbers carry,
         SavannaNumbers.TrailNumbers trailNumbers, Raids.Find find, ScentTrails.Trail scent, List<MatabeleEntity> raiders,
         int numbersAlong, Termites counted, long now) {
        this(raiders.stream().map(Entity::getUUID).toList(), level, colony, numbers, carry, trailNumbers, find, scent, numbersAlong, counted, now);
    }

    private Raid(List<UUID> raiders, ServerLevel level, Colony colony, SavannaNumbers.ColumnNumbers numbers, @Nullable SavannaNumbers.CarryNumbers carry,
                 SavannaNumbers.TrailNumbers trailNumbers, Raids.Find find, ScentTrails.Trail scent, int numbersAlong, Termites counted, long now) {
        this.colony = colony;
        this.numbers = numbers;
        this.carry = carry != null ? carry : SavannaNumbers.CarryNumbers.DEFAULT;
        this.trailNumbers = trailNumbers;
        this.find = find;
        this.trail = scent;
        this.counted = counted;
        this.startedAt = now;
        this.stageSince = now;
        this.numbersSince = now;
        for (UUID raider : raiders) {
            members.add(raider);
            memberSet.add(raider);
            formIndex.put(raider, formIndex.size());
        }
        this.asNumbers = members.isEmpty();
        this.leader = asNumbers ? null : members.get(0);
        this.size = members.size() + numbersAlong;
        this.numbersAlong = numbersAlong;
        this.blocksOut = (int) Math.round(flat(find.at(), colony.getCenter()));
        makeMarch(level);
    }

    /** The march along the raid's trail, and its two ways (out to the halting place, and back). */
    private void makeMarch(ServerLevel level) {
        this.march = new RaidMarch(level, colony, numbers, trailNumbers, trail, find.at(), new RaidMarch.Host() {
            @Override
            public void say(String what) {
                Raid.this.say(what);
            }

            @Override
            public String who(MatabeleEntity ant, Route route) {
                return Raid.this.who(ant, route);
            }

            @Override
            public void staysHome(UUID ant) {
                lost.merge("stayed home", 1, Integer::sum);
                drop(ant);
            }

            @Override
            public boolean formerScoutIn() {
                Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
                return raids != null && members.stream().anyMatch(raids::isVeteran);
            }
        });
        this.out = march.out();
        this.back = march.back();
    }

    private SavannaNumbers.AwayRaidNumbers awayNumbers() {
        return colony.profile().section(SavannaNumbers.AWAY_RAIDS).orElse(SavannaNumbers.AwayRaidNumbers.DEFAULT);
    }

    // ------------------------------------------------------------------------------------------------- the diary

    /** The fight diary: a raider fighting for this raid tells what it does; a termite gone for or stung keeps the attack going. */
    @Override
    public void fightNote(AbstractAntEntity ant, String what) {
        say(RaidGround.shortId(ant.getUUID()) + (ant instanceof MatabeleEntity m && m.hasMajorBody() ? " (major)" : " (minor)") + " " + what);
        // (what the shared fight tells when it finds nothing to do is no work at the spot; everything else is: going for
        // a termite, a grab, a sting, a major picking a breach or biting it open)
        boolean idle = what.startsWith("no foe") || what.startsWith("searched") || what.startsWith("no way") || what.startsWith("chased");
        if (!idle && lastLevel != null && stage == Stage.ATTACK) {
            lastActionAt = lastLevel.getGameTime();
        }
    }

    /** One line in the raid's diary, with the stage now first. */
    void say(String what) {
        Watch.note(lastLevel, colony, "[raid] " + stage.words() + ": " + what);
        SavannaMod.LOGGER.info("[raid] nest {} at {} (tick {}), {}: {}", colony.getCenter().toShortString(),
                RaidGround.clock(colony, lastLevel), lastLevel == null ? -1 : lastLevel.getGameTime(), stage.words(), what);
    }

    /** The diary's first line: the muster begins (said by the colony's raids once the raid is made). */
    void sayStart(ServerLevel level) {
        lastLevel = level;
        say("begins, " + count() + " ant(s) called to " + find.at().toShortString() + ", " + blocksOut + " blocks out, "
                + counted.words() + (leader == null ? "; out of sight" : "; led by " + RaidGround.shortId(leader)) + " (a raid of " + size + ", " + numbersAlong
                + " going as numbers; the march " + Math.round(out.length()) + " blocks along " + out.points.size()
                + " points to the halting place " + out.end().toShortString() + ")");
    }

    /** One raider in the diary: its id, where it is, how far along the march, and what it is doing. */
    private String who(MatabeleEntity ant, Route route) {
        Order order = orders.get(ant.getUUID());
        return RaidGround.shortId(ant.getUUID()) + " at " + ant.blockPosition().toShortString() + ", " + Math.round(march.alongOf(route, ant) * 10) / 10.0
                + " along" + (order == null || order.to() == null ? ", no place" : ", its place " + order.to().toShortString()
                + " (" + Math.round(Math.sqrt(ant.blockPosition().distSqr(order.to())) * 10) / 10.0 + " off)")
                + (colony.nest().isInside(ant.level(), ant.blockPosition()) ? ", inside the nest" : "")
                + ", walking " + !ant.getNavigator().isDone() + ", goals " + ant.runningGoals();
    }

    // ------------------------------------------------------------------------------------------- what others ask

    public Stage stage() {
        return stage;
    }

    public Raids.Find find() {
        return find;
    }

    public ScentTrails.Trail trail() {
        return trail;
    }

    /** The ant in front (null while the raid goes as numbers). */
    @Nullable
    public UUID leader() {
        return leader;
    }

    /** Whether nobody in the raid has a body now: it goes on as numbers, out of sight. */
    public boolean goesAsNumbers() {
        return asNumbers && stage != Stage.DONE;
    }

    /** Whether the raid has (or, done, ended with) nobody with a body: out of sight. */
    public boolean wasOutOfSight() {
        return asNumbers;
    }

    /** The raiders going as numbers now (they are out, not in the nest). */
    public int numbersAlong() {
        return stage == Stage.DONE ? 0 : numbersAlong;
    }

    /** How many raiders with a body went over to the raid's numbers on the way (out of sight past the grace). */
    public int wentToNumbers() {
        return wentToNumbers;
    }

    /** The raiders in the diary's counts: the real ants, or the numbers when nobody has a body. */
    private int count() {
        return asNumbers ? numbersAlong : members.size();
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public boolean has(UUID ant) {
        return memberSet.contains(ant);
    }

    public int size() {
        return size;
    }

    public long startedAt() {
        return startedAt;
    }

    /** When the stage now began (game time; moved on while the raid's clocks waited). */
    public long stageSince() {
        return stageSince;
    }

    /** When the raid was last worked out (game time). */
    public long updatedAt() {
        return updatedAt;
    }

    /** The longest the stage now can last (ticks): its deadline, and a little for the raid working itself out every few ticks. */
    public long stageMostTicks() {
        long most = switch (stage) {
            case MUSTER -> numbers.musterTicks();
            case MARCH_OUT -> marchLimit() + (lastLevel == null ? 0 : march.castTime(lastLevel.getGameTime()));
            case HALT -> numbers.gatherTicks();
            case ATTACK -> numbers.fightTicks();
            case REGROUP -> numbers.regroupTicks();
            case MARCH_HOME -> marchLimit();
            case DONE -> Long.MAX_VALUE / 4;
        };
        return most + EVERY * 2L;
    }

    /** How long a march may last, out or home (ticks): a second a block from the nest to the spot, and half a minute. */
    public long marchLimit() {
        return MARCH_PER_BLOCK * blocksOut + MARCH_SPARE;
    }

    /** The order this raider was last given (null: none, or not a raider now). */
    @Nullable
    public Order orderNow(UUID ant) {
        return orders.get(ant);
    }

    /** Whether this raider casts about at a gap in the trail now. */
    public boolean isCasting(UUID ant) {
        return march.isCasting(ant);
    }

    /** How many gaps in the trail the column crossed, and whether it turned home having lost the trail. */
    public int gapsCrossed() {
        return march.gapsCrossed();
    }

    public boolean lostTrail() {
        return march.lostTrail();
    }

    /** When the column left the nest's mouth (game time; -1 while it musters). */
    public long leftAt() {
        return leftAt;
    }

    /** When the attack began (game time, -1 not yet), and how many raiders were bunched at the halting place then. */
    public long attackedAt() {
        return attackedAt;
    }

    public int gatheredAtAttack() {
        return gatheredAtAttack;
    }

    /** How many raiders came home (left the raid at the entrance). */
    public int cameHome() {
        return cameHome;
    }

    /** How many stung termites the raiders picked up, and how many raiders carried some. */
    public int carried() {
        return carried;
    }

    public int carriers() {
        return carriers;
    }

    /** The termites the raid stung that still lie on the ground (its own list). */
    public Set<UUID> stung() {
        return Collections.unmodifiableSet(stung);
    }

    /** The walk home along the trail (from the halting place to the entrance): raiders let go walk home on it. */
    Route back() {
        return back;
    }

    /** The raiders let go since last asked (the colony's raids send them home along the trail); the list is emptied. */
    List<LetGo> takeLetGo() {
        List<LetGo> gone = List.copyOf(letGo);
        letGo.clear();
        return gone;
    }

    /** Whether the raid reached its spot and brought nothing back from it. */
    public boolean foundNothing() {
        return foundNothing;
    }

    /** Whether the judge found no termite at the spot as the column halted there: the find is forgotten. */
    public boolean spotEmpty() {
        return spotEmpty;
    }

    /** Why the raid turned home early (null if it did not): "dusk", "rain", or the reason in words. */
    @Nullable
    public String turned() {
        return turned;
    }

    /** What turned the raid home early, "dusk" or "rain" ("" for neither). */
    public String turnedBy() {
        return "dusk".equals(turned) || "rain".equals(turned) ? turned : "";
    }

    /** Whether dusk or rain turned this raid home. */
    public boolean turnedAtDusk() {
        return !turnedBy().isEmpty();
    }

    /** Whether it turned home at the edge of land not loaded. */
    public boolean turnedAtEdge() {
        return turnedAtEdge;
    }

    /** Whether this member is out of sight now. */
    public boolean isUnseen(UUID ant) {
        return unseenSince.containsKey(ant);
    }

    /** Whether this member is held by another job's fight order now (drafted by the nest's defence). */
    public boolean isDrafted(UUID ant) {
        return draftedSince.containsKey(ant);
    }

    /** Whether this raider holds the raid's own fight order now (the nest's defence keeps off it). */
    public boolean fightsForTheColumn(AbstractAntEntity ant) {
        return ant.getFightSpot() != null && ant.fightOrderBy() == this;
    }

    /** The halting place, a few blocks short of the find. */
    public BlockPos haltingPlace() {
        return out.end();
    }

    /** The scent line the column walks out on (the entrance end first), as points. */
    public List<BlockPos> route() {
        return out.points;
    }

    /** How far along the march out (blocks from the entrance) this place lies. */
    public double alongOut(BlockPos at) {
        return out.along(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** How far this place is from the scent line the column walks out on (blocks, flat). */
    public double offTrail(BlockPos at) {
        return out.offBy(at.getX() + 0.5, at.getZ() + 0.5);
    }

    /** Tests: how far along the march out an insect stands, from its exact place (blocks, flat). */
    public double alongOut(Vec3 at) {
        return out.along(at.x, at.z);
    }

    /** Tests: how far to the right of the scent line out an insect stands (blocks, flat; left negative). */
    public double sideOfTrail(Vec3 at) {
        double along = out.along(at.x, at.z);
        double[] on = out.at(along);
        double[] d = out.direction(along);
        return (at.x - on[0]) * -d[1] + (at.z - on[1]) * d[0];
    }

    /** The column's numbers (the species file's {@code raids.column}) and the carrying numbers ({@code raids.carry}). */
    public SavannaNumbers.ColumnNumbers columnNumbers() {
        return numbers;
    }

    public SavannaNumbers.CarryNumbers carryNumbers() {
        return carry;
    }

    /** The termites the raiders going as numbers took at the attack (brought home with the raid), and from which colony. */
    public int hiddenCatch() {
        return hiddenCatch;
    }

    @Nullable
    UUID hiddenFrom() {
        return hiddenFrom;
    }

    /** Hurt nestmates carried home so far, and the hurt raiders waiting at the spot for a nestmate. */
    public int rescued() {
        return rescued;
    }

    public Set<UUID> hurtHere() {
        return Collections.unmodifiableSet(hurtHere);
    }

    /** How long a raider stands after each pick-up (ticks). */
    public int pickTicks() {
        return carry.pickTicks();
    }

    /** Why the raid ended, and who left it on the way. */
    public String endedWhy() {
        return ended + (lost.isEmpty() ? "" : ", lost on the way: " + lost);
    }

    /** A hop along the raid's trail ({@link RaidMarch#hopAlong}); null: walk straight. */
    @Nullable
    public BlockPos hopAlong(ServerLevel level, BlockPos from, BlockPos to, double hop) {
        return march.hopAlong(level, from, to, hop);
    }

    // ------------------------------------------------------------------------------------------------ test hooks

    /** Tests: the stage now as if it had begun {@code ticks} earlier. */
    public void ageStageForTests(long ticks) {
        stageSince -= ticks;
    }

    /** Tests: a raid that is never worked out any more, as a hang would leave it. */
    public void hangForTests() {
        hungForTests = true;
    }

    /** Tests: this member counts as out of sight from now on (as if its land stopped ticking), and back in sight. */
    public void hideForTests(UUID ant) {
        if (memberSet.contains(ant)) {
            hiddenForTests.add(ant);
        }
    }

    public void showForTests(UUID ant) {
        hiddenForTests.remove(ant);
    }

    /** Tests: whether this raider waits now at its place in one of the raid's bunches (muster, halt), not fighting. */
    public boolean waitingForTests(AbstractAntEntity ant) {
        if (stage != Stage.MUSTER && stage != Stage.HALT || ant.getFightSpot() != null) {
            return false;
        }
        Order order = orders.get(ant.getUUID());
        return order != null && order.to() != null && flat(ant.blockPosition(), order.to()) <= 2.5;
    }

    /** Tests: a perfect stream's length for this many raiders behind the front (blocks). */
    public double perfectLengthForTests(int raiders) {
        return raiders * numbers.spacing();
    }

    /** Tests: how far (blocks walked) this marching raider was behind its place at the last update (NaN if it has none). */
    public double behindPlaceForTests(UUID id) {
        return march.behindPlace(id);
    }

    /** Tests: where the way out's hump ends (along the trail; 0 for a level start). */
    public double humpEndForTests(ServerLevel level) {
        return march.hillEnd(level, out);
    }

    /** Tests: whether this raider is out of the nest. */
    public boolean isOutForTests(ServerLevel level, MatabeleEntity ant) {
        return march.isOut(level, ant);
    }

    // ------------------------------------------------------------------------------------ with and without bodies

    /** How much of the way out from the nest to its spot the raid's front is now (0 at the nest, 1 at the spot). */
    double shareOut(long now) {
        double along = asNumbers ? frontAsNumbers(now) : march.frontAt();
        return switch (stage) {
            case MUSTER, DONE -> 0.0;
            case MARCH_OUT -> Math.max(0.0, Math.min(1.0, along / Math.max(1.0, out.length())));
            case MARCH_HOME -> Math.max(0.0, Math.min(1.0, 1.0 - along / Math.max(1.0, back.length())));
            default -> 1.0;
        };
    }

    /** The way's length from the entrance to the halting place (blocks along the trail). */
    double wayBlocks() {
        return out.length();
    }

    /**
     * Where the front of a raid going as numbers is on its trail now (the trail's own point, no ground looked for), and
     * where its tail is: the column's length behind the front on a march, the same place in a bunch. A watcher near the
     * front watches the colony, and bodies are put there only where nobody would see them appear ({@code RaidsAway}).
     */
    BlockPos frontPlace(long now) {
        return out.point(shareOut(now) * out.length());
    }

    BlockPos tailPlace(long now) {
        double length = size * numbers.spacing();
        double front = shareOut(now) * out.length();
        return out.point(Math.max(0.0, Math.min(out.length(), stage == Stage.MARCH_OUT ? front - length : stage == Stage.MARCH_HOME ? front + length : front)));
    }

    /**
     * How far along the stage's way (out, or back) the front of a raid going as numbers is now: from where it was when
     * its walk as numbers began, at the numbers' pace ({@code raids.away.pace}, a little under the real column's about
     * 2 blocks a second: out of sight nothing is known of the ground), slower laden on the way home as the real column is.
     */
    private double frontAsNumbers(long now) {
        if (stage != Stage.MARCH_OUT && stage != Stage.MARCH_HOME) {
            return 0.0;
        }
        double pace = awayNumbers().pace() * (stage == Stage.MARCH_HOME && hiddenCatch > 0 ? carry.ladenPace() : 1.0);
        return numbersAt + Math.max(0.1, pace) * Math.max(0, now - numbersSince) / 20.0;
    }

    /** The raid's walk as numbers goes on from here along the stage's way, from now. */
    private void walkFrom(double along, long now) {
        numbersAt = Math.max(0.0, along);
        numbersSince = now;
    }

    /**
     * The raid as numbers: the same stages in the same order, each ended by its own rule with nobody's body to wait
     * for, and never past its deadline. A bunch (the muster, the halt) lasts as long as the rule's least, since every
     * number is at its place at once; a march lasts the way's length at the numbers' pace; the halt asks the one judge
     * as the front arrives, and an empty spot gets the same short look; the attack opens with the catch
     * ({@link #takeHidden}) and lasts {@code raids.away.fight_ticks} (a minute: a watched attack lasts 20 s to the
     * 2-minute deadline; my pick, never past the deadline); the regroup lasts the bunch's least and a pick-up for each
     * termite taken, never past its deadline. Nothing here reads a block: the land may not be loaded.
     */
    private void stepAsNumbers(ServerLevel level, long now) {
        long spent = now - stageSince;
        switch (stage) {
            case MUSTER -> {
                if (spent >= Math.min(numbers.bunchLeastTicks(), numbers.musterTicks())) {
                    leftAt = now;
                    next(Stage.MARCH_OUT, now, "everyone is out (as numbers)");
                    walkFrom(0.0, now);
                }
            }
            case MARCH_OUT -> {
                if (frontAsNumbers(now) >= out.length()) {
                    trail.walked(now);
                    next(Stage.HALT, now, "the front is at the halting place");
                    atHalt = termitesThere(level, colony, find.at());
                    say("the scout looks at the spot: " + atHalt.words());
                } else if (spent >= marchLimit()) {
                    turnHome(List.of(), now, "the march's time is up, " + marchLimit() / 20 + " s for " + blocksOut + " blocks");
                }
            }
            case HALT -> {
                if (atHalt == null) {
                    atHalt = termitesThere(level, colony, find.at());
                }
                if (!atHalt.any()) {
                    if (spent >= numbers.lookTicks()) {
                        spotEmpty = true;
                        foundNothing = true;
                        turnHome(List.of(), now, "no termite at the spot now, after a look round of " + spent / 20 + " s");
                    }
                } else if (spent >= Math.min(numbers.bunchLeastTicks(), numbers.gatherTicks())) {
                    next(Stage.ATTACK, now, "the tail is in");
                    attackedAt = now;
                    lastActionAt = now;
                    takeHidden(level);
                }
            }
            case ATTACK -> {
                if (spent >= Math.min(awayNumbers().fightTicks(), numbers.fightTicks())) {
                    next(Stage.REGROUP, now, "no termite stung or gone for any more; " + hiddenCatch + " taken");
                }
            }
            case REGROUP -> {
                if (spent >= Math.min(numbers.regroupTicks(), numbers.bunchLeastTicks() + (long) carry.pickTicks() * hiddenCatch)) {
                    if (turned == null && hiddenCatch == 0 && carried == 0) {
                        foundNothing = true; // (they were at the spot and bring nothing back)
                    }
                    next(Stage.MARCH_HOME, now, (turned != null ? "turns home: " + turned + "; " : "")
                            + (hiddenCatch > 0 ? "everything stung is picked up" : "nothing to pick up") + " (" + hiddenCatch + " termite(s) with the numbers)");
                    walkFrom(0.0, now);
                }
            }
            case MARCH_HOME -> {
                boolean home = frontAsNumbers(now) >= back.length();
                if (home || spent >= marchLimit()) {
                    trail.walked(now);
                    cameHome += numbersAlong;
                    end(now, home ? "all home" : "its time is up: the rest walk in out of sight");
                }
            }
            default -> {
            }
        }
    }

    /**
     * One raider goes over to the raid's numbers (it is out of sight past the grace, or its whole colony is): it leaves
     * the world if it is there (folded into the colony's numbers, {@code AwayLife}), and what it holds in its jaws goes
     * on with the numbers.
     */
    private void toNumbers(ServerLevel level, UUID id) {
        int held = jaws.getOrDefault(id, 0);
        if (level.getEntity(id) instanceof MatabeleEntity ant) {
            if (ant.fightOrderBy() == this) {
                ant.endFightOrder(this);
            }
            held = MatabeleLoads.termitesHeldBy(ant);
            if (!ant.isAlive() || !colony.away().foldInStray(level, ant)) {
                drop(id); // (dead, downed or stung itself: no number)
                return;
            }
        }
        if (held > 0) {
            carried = Math.max(0, carried - held);
            carriers = Math.max(0, carriers - 1);
            hiddenCatch += held;
        }
        drop(id);
        numbersAlong++;
        wentToNumbers++;
    }

    /**
     * The whole raid goes on as numbers from where it is: its colony went out of sight ({@code Raids.tickAway}), or
     * its last raider with a body did. The same stage, begun when it began; on a march the walk as numbers starts
     * where the front was. The hurt waiting at the spot are let go (they are the colony's own numbers now, or limp
     * home), and what lies stung on the ground is left.
     */
    void goOnAsNumbers(ServerLevel level) {
        if (asNumbers || stage == Stage.DONE) {
            return;
        }
        long now = level.getGameTime();
        lastLevel = level;
        double share = shareOut(now);
        releaseHurt("the raid went out of sight");
        for (UUID id : List.copyOf(members)) {
            toNumbers(level, id);
        }
        if (numbersAlong <= 0) {
            end(now, "nobody left in it");
            return;
        }
        asNumbers = true;
        leader = null;
        orders.clear();
        sentIn.clear();
        returning.clear();
        taking.clear();
        stung.clear();
        noWay.clear();
        march.newWalk();
        walkFrom(stage == Stage.MARCH_HOME ? (1.0 - share) * back.length() : share * out.length(), now);
        updatedAt = now;
        say("goes on as " + numbersAlong + " number(s), out of sight, " + Math.round(share * out.length()) + " of " + Math.round(out.length())
                + " blocks out" + (hiddenCatch > 0 ? ", " + hiddenCatch + " termite(s) with them" : ""));
    }

    /**
     * Bodies for a raid going as numbers (a watcher is near it, {@code RaidsAway.show}): these ants, just out of the
     * colony's numbers, are the raid's real ants from now, the first one in front, and the same raid goes on with them
     * from its stage. What the numbers caught is put in their jaws. Their places: {@code inNest} (the raid is
     * mustering, or has only just left) they stand in the nest's rooms where they came out and the raid musters with
     * them at the entrance as any raid does; on a march they stand on the trail, the front where the numbers' front
     * was and each the column's spacing behind the one ahead; at the spot they stand bunched at the halting place. A
     * raid shown in its attack regroups: its catch is made, the attack as numbers began with it.
     */
    void takeBodies(ServerLevel level, List<MatabeleEntity> ants, boolean inNest, long now) {
        if (!asNumbers || ants.isEmpty() || stage == Stage.DONE) {
            return;
        }
        lastLevel = level;
        double share = shareOut(now);
        makeMarch(level); // (the land is loaded now: the trail's start gets its ground)
        for (MatabeleEntity ant : ants) {
            members.add(ant.getUUID());
            memberSet.add(ant.getUUID());
        }
        leader = members.get(0);
        asNumbers = false;
        numbersAlong = Math.max(0, size - ants.size());
        int left = hiddenCatch;
        for (MatabeleEntity ant : ants) {
            int takes = Math.min(left, ant.termitesItCanCarry());
            if (takes > 0) {
                ant.setLoad(MatabeleLoads.termites(takes));
                carried += takes;
                carriers++;
                left -= takes;
            }
        }
        hiddenCatch = left;
        updatedAt = now - EVERY; // (its clocks go on from now)
        if (inNest) {
            if (stage != Stage.MUSTER) {
                next(Stage.MUSTER, now, "watched now, only just out of the nest: it musters at the entrance with bodies");
            } else {
                say(ants.size() + " ant(s): watched now, the raid as numbers musters with bodies");
            }
            stageSince = now;
            leftAt = -1;
            return;
        }
        if (stage == Stage.ATTACK) {
            next(Stage.REGROUP, now, "watched now: the catch is made (" + carried + " termite(s)), it regroups with bodies");
        }
        boolean marching = stage == Stage.MARCH_OUT || stage == Stage.MARCH_HOME;
        Route route = stage == Stage.MARCH_HOME ? back : out;
        double front = stage == Stage.MARCH_OUT ? share * out.length() : stage == Stage.MARCH_HOME ? (1.0 - share) * back.length() : out.length();
        BlockPos middle = haltMiddle();
        for (int k = 0; k < ants.size(); k++) {
            MatabeleEntity ant = ants.get(k);
            double along = Math.max(0.0, front - k * numbers.spacing());
            BlockPos cell = marching ? null : bunchPlace(level, middle, ant.getUUID(), null);
            if (cell == null) {
                cell = route.place(level, along, 0.0);
            }
            double[] way = route.direction(along);
            float yaw = (float) (Mth.atan2(way[1], way[0]) * Mth.RAD_TO_DEG) - 90.0F;
            ant.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, yaw, 0.0F);
            ant.setYHeadRot(yaw);
            ant.setYBodyRot(yaw);
        }
        march.newWalk();
        if (marching) {
            march.startAt(front);
        }
        say(ants.size() + " ant(s): watched now, the raid as numbers has bodies, "
                + (marching ? "on the trail " + Math.round(front) + " of " + Math.round(route.length()) + " blocks along the way "
                        + (stage == Stage.MARCH_HOME ? "home" : "out") : "bunched at the halting place")
                + (carried > 0 ? ", " + carried + " termite(s) in their jaws" : ""));
    }

    // ------------------------------------------------------------------------------------------------- saving

    /** The raid written down with its colony: its stage and clocks, its find, who is in it, what it stung and carries. */
    void save(CompoundTag tag) {
        tag.putString("stage", stage.name());
        tag.putLong("stageSince", stageSince);
        tag.putLong("startedAt", startedAt);
        tag.putLong("at", find.at().asLong());
        tag.putInt("worth", find.worth());
        tag.put("members", uuids(members));
        tag.putInt("size", size);
        tag.putInt("numbersAlong", numbersAlong);
        // (where the front is: as numbers its place when the walk began and when; with bodies the march's own front)
        tag.putDouble("front", asNumbers ? numbersAt : march.frontAt());
        tag.putLong("frontSince", numbersSince);
        tag.putLong("leftAt", leftAt);
        tag.putLong("attackedAt", attackedAt);
        tag.put("counted", termitesTag(counted));
        if (atHalt != null) {
            tag.put("atHalt", termitesTag(atHalt));
        }
        tag.putBoolean("spotEmpty", spotEmpty);
        tag.putBoolean("foundNothing", foundNothing);
        if (turned != null) {
            tag.putString("turned", turned);
        }
        tag.putBoolean("turnedAtEdge", turnedAtEdge);
        tag.putBoolean("keepsNoHours", keepsNoHours);
        tag.put("stung", uuids(stung));
        tag.put("hurtHere", uuids(hurtHere));
        tag.putIntArray("counts", new int[] {cameHome, carried, carriers, letGoCount, rescued, hurtAtSpot, hiddenCatch, gatheredAtAttack});
        if (hiddenFrom != null) {
            tag.putUUID("hiddenFrom", hiddenFrom);
        }
    }

    /** The spot a raid written down by {@link #save} was going to. */
    static BlockPos savedSpot(CompoundTag tag) {
        return BlockPos.of(tag.getLong("at"));
    }

    static int savedWorth(CompoundTag tag) {
        return tag.getInt("worth");
    }

    private static ListTag uuids(java.util.Collection<UUID> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(NbtUtils.createUUID(id)));
        return list;
    }

    private static CompoundTag termitesTag(Termites termites) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("seen", termites.seen());
        tag.putBoolean("underCover", termites.underCover());
        tag.putBoolean("byTheLand", termites.byTheLand());
        return tag;
    }

    private static Termites termitesOf(CompoundTag tag) {
        return new Termites(tag.getInt("seen"), tag.getBoolean("underCover"), tag.getBoolean("byTheLand"));
    }

    /**
     * A raid read back after a reload, in the stage it was saved in, its clocks as they were (the game's time does not
     * run while the game is shut). Its real ants are in the world again if their land is loaded; one that is not counts
     * as out of sight and goes over to the numbers after the grace, as always. The find and the trail are the colony's
     * own ({@code find}, {@code scent}: found again by the saved spot). Null if what was written cannot be a raid.
     */
    @Nullable
    static Raid load(ServerLevel level, Colony colony, SavannaNumbers.ColumnNumbers numbers, @Nullable SavannaNumbers.CarryNumbers carry,
                     SavannaNumbers.TrailNumbers trailNumbers, Raids.Find find, ScentTrails.Trail scent, CompoundTag tag) {
        Stage stage;
        try {
            stage = Stage.valueOf(tag.getString("stage"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<UUID> members = new ArrayList<>();
        for (Tag one : tag.getList("members", Tag.TAG_INT_ARRAY)) {
            members.add(NbtUtils.loadUUID(one));
        }
        int numbersAlong = Math.max(0, tag.getInt("numbersAlong"));
        if (stage == Stage.DONE || members.isEmpty() && numbersAlong <= 0) {
            return null;
        }
        long now = level.getGameTime();
        Raid raid = new Raid(members, level, colony, numbers, carry, trailNumbers, find, scent, numbersAlong, termitesOf(tag.getCompound("counted")),
                tag.getLong("startedAt"));
        raid.lastLevel = level;
        raid.stage = stage;
        raid.stageSince = tag.getLong("stageSince");
        raid.size = Math.max(members.size() + numbersAlong, tag.getInt("size"));
        raid.numbersAt = tag.getDouble("front");
        raid.numbersSince = tag.getLong("frontSince");
        if (!raid.asNumbers) {
            raid.march.startAt(tag.getDouble("front"));
        }
        raid.leftAt = tag.getLong("leftAt");
        raid.attackedAt = tag.getLong("attackedAt");
        raid.lastActionAt = stage == Stage.ATTACK ? now : -1; // (the quiet clock starts again: who stung what last is not kept)
        raid.atHalt = tag.contains("atHalt") ? termitesOf(tag.getCompound("atHalt")) : null;
        raid.spotEmpty = tag.getBoolean("spotEmpty");
        raid.foundNothing = tag.getBoolean("foundNothing");
        raid.turned = tag.contains("turned") ? tag.getString("turned") : null;
        raid.turnedAtEdge = tag.getBoolean("turnedAtEdge");
        raid.keepsNoHours = tag.getBoolean("keepsNoHours");
        for (Tag one : tag.getList("stung", Tag.TAG_INT_ARRAY)) {
            raid.stung.add(NbtUtils.loadUUID(one));
        }
        for (Tag one : tag.getList("hurtHere", Tag.TAG_INT_ARRAY)) {
            raid.hurtHere.add(NbtUtils.loadUUID(one));
        }
        int[] counts = tag.getIntArray("counts");
        if (counts.length >= 8) {
            raid.cameHome = counts[0];
            raid.carried = counts[1];
            raid.carriers = counts[2];
            raid.letGoCount = counts[3];
            raid.rescued = counts[4];
            raid.hurtAtSpot = counts[5];
            raid.hiddenCatch = counts[6];
            raid.gatheredAtAttack = counts[7];
        }
        raid.hiddenFrom = tag.hasUUID("hiddenFrom") ? tag.getUUID("hiddenFrom") : null;
        raid.say("read back after a reload: " + raid.count() + " ant(s)" + (raid.asNumbers ? " as numbers" : "") + ", "
                + (now - raid.stageSince) / 20 + " s into this stage");
        return raid;
    }

    // ------------------------------------------------------------------------------------------- turning home

    /** Dusk (called by the colony at 19:30 for a raid still out): the raid turns home at its next update. */
    public void callDusk() {
        askTurn("dusk");
    }

    /** Rain began while the raid is out: it turns home the way dusk turns it. */
    public void callRain() {
        askTurn("rain");
    }

    private void askTurn(String why) {
        if (!keepsNoHours && turned == null && turnAsked == null) {
            turnAsked = why;
        }
    }

    /** Sent outside the raid hours by a command: it keeps no hours. */
    public void keepNoHours() {
        keepsNoHours = true;
        turnAsked = null;
    }

    public boolean keepsNoHours() {
        return keepsNoHours;
    }

    /**
     * The one way a raid turns home early, from any stage. Mustering, marching out or halted, it walks home from where
     * it is. In the attack every fight ends at once and the raid regroups, picking up what it stung, then walks home.
     * Regrouping or already on the way home, it carries on.
     */
    private void turnHome(List<MatabeleEntity> ants, long now, String why) {
        if (turned != null || stage == Stage.DONE) {
            return;
        }
        turned = why;
        double share = shareOut(now);
        Stage was = stage;
        turnHomeFrom(ants, now, why);
        if (asNumbers && stage == Stage.MARCH_HOME && was != Stage.MARCH_HOME) {
            walkFrom((1.0 - share) * back.length(), now); // (as numbers: the walk home starts where the front was)
        }
    }

    private void turnHomeFrom(List<MatabeleEntity> ants, long now, String why) {
        switch (stage) {
            case MUSTER -> next(Stage.MARCH_HOME, now, "turns home: " + why);
            case MARCH_OUT, HALT -> {
                next(Stage.MARCH_HOME, now, "turns home: " + why);
                sortHomeward(ants); // (the ant nearest home leads the way back)
                homeLed = true;
            }
            case ATTACK -> {
                endFights(ants);
                next(Stage.REGROUP, now, "turns home: " + why + " (it picks up what it stung first)");
            }
            default -> say("turns home: " + why + " (it was on its way already)");
        }
    }

    // ------------------------------------------------------------------------------------------------ the stages

    /** Where this raider should be now, or null to stand (worked out every few ticks). */
    @Nullable
    public Order orderFor(ServerLevel level, MatabeleEntity ant) {
        RaidCost.begin();
        try {
            update(level);
        } catch (RuntimeException e) {
            // (nothing waits for ever: a raid that cannot be worked out is given up rather than crash the game or hold its raiders)
            SavannaMod.LOGGER.error("[raid] colony {}: the raid's update failed; the raid is given up", colony.getId(), e);
            abandon(level, e.toString());
        } finally {
            RaidCost.end();
        }
        return orders.get(ant.getUUID());
    }

    /** It leaves the raid (home, stayed home, dead). */
    void drop(UUID ant) {
        members.remove(ant);
        memberSet.remove(ant);
        orders.remove(ant);
        sentIn.remove(ant);
        returning.remove(ant);
        taking.remove(ant);
        unseenSince.remove(ant);
        draftedSince.remove(ant);
        jaws.remove(ant);
    }

    /** It leaves the raid out in the field: dropped, and handed to the colony's raids to go home along the trail. */
    private void letGo(UUID ant, String why) {
        drop(ant);
        letGo.add(new LetGo(ant, why));
        letGoCount++;
    }

    /** The raid was not worked out for a while (the colony out of sight): its clocks wait that long. */
    private void holdClocks(long by) {
        if (by <= 0) {
            return;
        }
        stageSince += by;
        numbersSince += by;
        if (attackedAt >= 0) {
            attackedAt += by;
        }
        if (lastActionAt >= 0) {
            lastActionAt += by;
        }
        march.holdClocks(by);
        draftedSince.replaceAll((id, since) -> since + by);
        if (stage == Stage.ATTACK) {
            sentIn.clear(); // (sent in again at the spot, with fresh orders)
        }
    }

    /** The raid's stage and every raider's place, every {@link #EVERY} ticks (asked by the raiders and the colony). */
    void update(ServerLevel level) {
        long now = level.getGameTime();
        if (now - updatedAt < EVERY || stage == Stage.DONE || hungForTests) {
            return;
        }
        long previous = updatedAt;
        updatedAt = now;
        lastLevel = level;
        RaidCost.updated();
        march.newUpdate();
        if (previous >= 0 && now - previous > HOLD_AFTER) {
            say("the raid was not worked out for " + (now - previous) / 20 + " s (the colony's land not ticking): its clocks waited");
            holdClocks(now - previous - EVERY);
        }
        if (asNumbers) {
            if (turnAsked != null) {
                String why = turnAsked;
                turnAsked = null;
                turnHome(List.of(), now, why);
            }
            stepAsNumbers(level, now);
            return;
        }
        int numbersBefore = wentToNumbers;
        List<MatabeleEntity> ants = rollCall(level, now);
        tendHurt(level, now);
        if (ants.isEmpty()) {
            if (!members.isEmpty()) {
                holdClocks(EVERY); // (every member out of sight: the raid waits for them)
                return;
            }
            if (wentToNumbers > numbersBefore) {
                goOnAsNumbers(level); // (the last raider with a body is out of sight past the grace: the raid goes on as numbers)
                return;
            }
            end(now, "nobody left in it");
            return;
        }
        orders.clear();
        if (!memberSet.contains(leader)) {
            boolean wayOut = stage == Stage.MUSTER || stage == Stage.MARCH_OUT;
            leader = nextLeader();
            if (wayOut) {
                // Real Megaponera cannot follow the scout's line out without her (Yusuf 2010, as cited): the raid turns home.
                turnHome(ants, now, "the scout is lost, and nobody else knows the way out");
            }
        }
        squeak(level, ants, now);
        if (turnAsked != null) {
            String why = turnAsked;
            turnAsked = null;
            turnHome(ants, now, why);
        }
        switch (stage) {
            case MUSTER -> muster(level, ants, now);
            case MARCH_OUT -> marchOut(level, ants, now);
            case HALT -> halt(level, ants, now);
            case ATTACK -> attack(level, ants, now);
            case REGROUP -> regroup(level, ants, now);
            case MARCH_HOME -> marchHome(level, ants, now);
            default -> {
            }
        }
    }

    /**
     * Who is with the raid now. One out of sight (its land not loaded or not ticking) is kept for the colony's away
     * grace, then goes on as one of the raid's numbers ({@link #toNumbers}; it was "lost from sight" and let go, and
     * with its leader lost the whole raid was dropped: 14 of 46 raids in the soak of step 1). One hurt at the spot leaves the raid, stands and calls for help. One drafted by
     * another fight keeps its place a while. The dead, the crippled and the downed leave it.
     */
    private List<MatabeleEntity> rollCall(ServerLevel level, long now) {
        List<MatabeleEntity> ants = new ArrayList<>();
        for (UUID id : List.copyOf(members)) {
            Entity entity = level.getEntity(id);
            boolean seen = entity instanceof MatabeleEntity && level.isPositionEntityTicking(entity.blockPosition()) && !hiddenForTests.contains(id);
            if (!seen && (entity == null || entity.isAlive())) {
                long since = unseenSince.computeIfAbsent(id, key -> {
                    say(RaidGround.shortId(key) + " is out of sight: kept in the raid for " + AwayLife.GRACE_TICKS / 20 + " s");
                    return now;
                });
                if (now - since > AwayLife.GRACE_TICKS) {
                    say(RaidGround.shortId(id) + " is out of sight " + (now - since) / 20 + " s: it goes on as one of the raid's numbers");
                    toNumbers(level, id);
                }
                continue;
            }
            if (unseenSince.remove(id) != null) {
                say(RaidGround.shortId(id) + " is in sight again");
            }
            if (entity instanceof MatabeleEntity hurt && atTheSpot() && hurt.needsRescue()) {
                if (hurt.fightOrderBy() == this) {
                    hurt.endFightOrder(this);
                }
                drop(id);
                if (hurtHere.add(id)) {
                    hurtAtSpot++;
                }
                say(RaidGround.shortId(id) + " is hurt (" + (hurt.clamp().isLiving() ? "a foe clamped on" : "legs gone") + "): it stops and calls for help");
                continue;
            }
            if (entity instanceof MatabeleEntity ant && ant.isAlive() && !ant.fightsNoMore() && !ant.isDowned()) {
                ants.add(ant);
                int held = carried > 0 ? MatabeleLoads.termitesHeldBy(ant) : 0;
                if (held > 0) {
                    jaws.put(id, held);
                } else {
                    jaws.remove(id);
                }
                if (ant.getFightSpot() != null && ant.fightOrderBy() != this) {
                    long since = draftedSince.computeIfAbsent(id, key -> {
                        say(RaidGround.shortId(key) + " is called away to fight (it catches up after)");
                        return now;
                    });
                    if (now - since > DRAFT_KEEP && !id.equals(leader)) {
                        lost.merge("kept by another fight", 1, Integer::sum);
                        say(RaidGround.shortId(id) + " leaves the raid: called away to fight for " + (now - since) / 20 + " s");
                        ants.remove(ant);
                        letGo(id, "kept by another fight");
                    }
                } else if (draftedSince.remove(id) != null) {
                    say(RaidGround.shortId(id) + " is back with the raid after fighting");
                }
            } else {
                String why = !(entity instanceof MatabeleEntity gone) ? "gone from the world" : !gone.isAlive() ? "dead"
                        : gone.fightsNoMore() ? "fights no more" : "downed";
                lost.merge(why, 1, Integer::sum);
                say(RaidGround.shortId(id) + " leaves the raid: " + why);
                if (entity instanceof MatabeleEntity ant && ant.isAlive() && ant.fightsNoMore()) {
                    letGo(id, "hurt: " + why); // (it limps home along the trail by itself)
                } else {
                    drop(id);
                }
            }
        }
        return ants;
    }

    /** One stage ends, with the count and the reason, and the next begins. */
    private void next(Stage to, long now, String why) {
        if (to == Stage.MARCH_HOME) {
            releaseHurt("the raid left the spot without it");
        }
        say("ends after " + (now - stageSince) / 20 + " s, " + count() + " ant(s): " + why);
        march.newWalk();
        homeLed = false;
        stage = to;
        stageSince = now;
        say("begins, " + count() + " ant(s)" + (carried > 0 ? ", " + carried + " termite(s) in their jaws" : ""));
    }

    /** The raid is over: the diary's last line. */
    private void end(long now, String why) {
        releaseHurt("the raid ended");
        if (stage != Stage.DONE) {
            say("ends after " + (now - stageSince) / 20 + " s, " + count() + " ant(s): " + why);
        }
        stage = Stage.DONE;
        stageSince = now;
        ended = why;
        say(cameHome + " home, " + carried + " termite(s) on the pile" + (hiddenCatch > 0 ? " and " + hiddenCatch + " by the raiders going as numbers" : "")
                + ", " + rescued + " hurt carried, " + letGoCount + " let go" + (turned != null ? "; it turned home: " + turned : "")
                + (lost.isEmpty() ? "" : "; lost on the way: " + lost));
    }

    /** The raid could not be worked out: it is given up where it is, every fight for it ended and every raider let go. */
    void abandon(ServerLevel level, String why) {
        lastLevel = level;
        for (UUID id : List.copyOf(members)) {
            if (level.getEntity(id) instanceof MatabeleEntity ant && ant.fightOrderBy() == this) {
                ant.endFightOrder(this);
            }
            letGo(id, "the raid was given up");
        }
        end(level.getGameTime(), "given up: " + why);
    }

    /** The front fell: the next in the stream leads on, else the first in the raid's order. */
    private UUID nextLeader() {
        return march.lineOrder().stream().filter(memberSet::contains).findFirst().orElse(members.isEmpty() ? null : members.get(0));
    }

    /**
     * Muster (real: 1 to 5 minutes from the scout's return to the column leaving, Hoelldobler et al. 1994): the scout
     * stands at the trail's start by the entrance and the called ants pour out and bunch behind her, as many as there
     * is level ground for. A real nest opens on flat ground with room for the whole raid; a nest in a dead mound opens
     * at its chimney's top, with room for a few. (Two musters that found room for everyone anyway both failed: sent to
     * the one cell by the entrance, 70 ants stood inside one another; mustered at the mound's foot, the bunch walked
     * there outside the march and its rules, and on a short trail it stood at the termites.) So an ant with no place
     * waits in the nest and pours out behind the column as it leaves ({@link RaidMarch}, as the old raid did since
     * BUGS 101). It ends once every ant with a place is at it, never before {@code bunch_least_ticks} (a bunch one can
     * see) and never after {@code muster_ticks}: then the column leaves with who is there.
     */
    private void muster(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos start = out.place(level, 0.0, 0.0);
        BlockPos ahead = out.point(Math.min(out.length(), 4.0));
        int placed = 0;
        int there = 0;
        for (MatabeleEntity ant : ants) {
            UUID id = ant.getUUID();
            BlockPos place = bunchPlace(level, start, id, ahead);
            if (place != null) {
                placed++;
                // (height counted: right under its place, down the chimney, is not there, BUGS 87)
                if (flat(ant.blockPosition(), place) <= 1.5 && Math.abs(ant.getBlockY() - place.getY()) <= 1) {
                    there++;
                }
            }
            // (straight to its own place, never by way of one shared cell at the entrance; none: it stands where it is)
            orders.put(id, new Order(place, id.equals(leader) ? HURRY_PACE : 1.0, ahead));
        }
        long spent = now - stageSince;
        boolean all = there >= placed;
        if (all && spent >= numbers.bunchLeastTicks() || spent >= numbers.musterTicks()) {
            leftAt = now;
            int waiting = ants.size() - placed;
            next(Stage.MARCH_OUT, now, all
                    ? "everyone with room by the entrance is out (" + placed + (waiting > 0 ? "; " + waiting + " wait in the nest and pour out behind" : "") + ")"
                    : "its time is up: it leaves with the " + there + " out, the rest run after it");
        }
    }

    /**
     * An ant's own place in a bunch round {@code middle} (the muster, the halt): the first of the raid in the middle,
     * the others on a spiral outward, 2 to 3 ants to a block of ground however big the raid (a raid of 10 reaches
     * 2 blocks out from the middle, one of 75 about 4), each keeping its place while others come and go. With
     * {@code ahead} (the muster) only the half behind one who stands in the middle facing {@code ahead}, so nobody
     * starts the march in front of the scout, and only ground level with the middle (within a block), not a mound's
     * side. Null: no ground there to stand on.
     */
    @Nullable
    private BlockPos bunchPlace(ServerLevel level, BlockPos middle, UUID id, @Nullable BlockPos ahead) {
        int n = formIndex.computeIfAbsent(id, k -> formIndex.size());
        if (n == 0) {
            return middle;
        }
        double angle = n * 2.39996;
        double radius = 1.0 + BUNCH_SPREAD * Math.sqrt(n);
        if (ahead != null) {
            double back = Math.atan2(middle.getZ() - ahead.getZ(), middle.getX() - ahead.getX());
            angle = back + (Mth.frac(n * 0.618034) - 0.5) * Math.PI;
            radius = 1.0 + BUNCH_SPREAD * Math.sqrt(2.0 * n);
        }
        BlockPos place = standAt(level, middle.getX() + 0.5 + Math.cos(angle) * radius, middle.getZ() + 0.5 + Math.sin(angle) * radius, middle.getY());
        return place != null && ahead != null && Math.abs(place.getY() - middle.getY()) > 1 ? null : place;
    }

    /**
     * How fast a raider climbs now, against its own pace: about a walk on the way out (up the nest's shaft and down
     * the hump, {@link #MARCH_CLIMB}), else as it will.
     */
    public double climbPace() {
        return stage == Stage.MUSTER || stage == Stage.MARCH_OUT ? MARCH_CLIMB : 1.0;
    }

    /** One walk's step ({@link RaidMarch#step}): true at the route's end. */
    private boolean march(ServerLevel level, List<MatabeleEntity> ants, Route route, long now) {
        return march.step(level, ants, route, leader, stageSince, orders, now);
    }

    /**
     * March out: the kept march along the scout's line, till the front is at the halting place. It turns home where the
     * land ahead is not loaded, where the trail is lost at a wide gap, or when its time is up (time spent casting about
     * at a gap in the trail is not counted).
     */
    private void marchOut(ServerLevel level, List<MatabeleEntity> ants, long now) {
        if (edgeAhead(level, ants)) {
            turnedAtEdge = true;
            turnHome(ants, now, "the land ahead is not loaded");
        } else if (march(level, ants, out, now)) {
            trail.walked(now); // (every raider laid scent on the way out)
            next(Stage.HALT, now, "the front is at the halting place");
            atHalt = termitesThere(level, colony, find.at());
            say("the scout looks at the spot: " + atHalt.words());
        } else if (march.takeTurnBack()) {
            turnHome(ants, now, "the trail is lost at a wide gap and no former scout walks along");
        } else if (now - stageSince - march.castTime(now) >= marchLimit()) {
            turnHome(ants, now, "the march's time is up, " + marchLimit() / 20 + " s for " + blocksOut + " blocks");
        }
    }

    /** Whether the land ahead of the marching front is not loaded within {@code edge_look} blocks along the way out. */
    private boolean edgeAhead(ServerLevel level, List<MatabeleEntity> ants) {
        MatabeleEntity front = ants.stream().filter(ant -> ant.getUUID().equals(leader)).findFirst().orElse(ants.get(0));
        double from = march.alongOf(out, front);
        double look = hoursSound().edgeLook();
        for (double along = from; along <= Math.min(out.length(), from + look); along += 1.0) {
            BlockPos at = out.point(along);
            if (!level.isPositionEntityTicking(at)) {
                say("the land ahead is not loaded at " + at.toShortString() + " (" + Math.round(along) + " of " + Math.round(out.length())
                        + " along the way out)");
                return true;
            }
        }
        return false;
    }

    /**
     * Halt (real: 20 to 50 cm short of the termites the column stops and bunches up in a circle round the scout until
     * all the ants have arrived, Longhurst and Howse 1979): everyone to a place round the halting place. It ends once the
     * column's tail is in, never before {@code bunch_least_ticks} and never after {@code gather_ticks}: then an ant far
     * behind is left behind and walks home. With no termite at the spot (the judge, asked as the front arrived) the
     * front few walk on to it and look round for {@code look_ticks}, then the raid turns home and the find is forgotten.
     */
    private void halt(ServerLevel level, List<MatabeleEntity> ants, long now) {
        long spent = now - stageSince;
        bunch(level, ants);
        if (atHalt != null && !atHalt.any()) {
            int looking = 0;
            for (MatabeleEntity ant : ants) {
                if (looking >= LOOKERS) {
                    break;
                }
                // (round the spot, a new place every 2 s)
                double angle = (looking + now / 40) * 2.39996;
                BlockPos spot = standAt(level, find.at().getX() + 0.5 + Math.cos(angle) * 2.0, find.at().getZ() + 0.5 + Math.sin(angle) * 2.0, find.at().getY());
                if (spot != null) {
                    orders.put(ant.getUUID(), new Order(spot, 1.0, find.at()));
                }
                looking++;
            }
            if (spent >= numbers.lookTicks()) {
                spotEmpty = true;
                foundNothing = true;
                turnHome(ants, now, "no termite at the spot now, after a look round of " + spent / 20 + " s");
            }
            return;
        }
        BlockPos middle = haltMiddle();
        boolean tailIn = ants.stream().allMatch(ant -> draftedSince.containsKey(ant.getUUID()) || flat(ant.blockPosition(), middle) <= TAIL_IN);
        if (!(tailIn && spent >= numbers.bunchLeastTicks()) && spent < numbers.gatherTicks()) {
            return;
        }
        int behind = 0;
        for (MatabeleEntity ant : List.copyOf(ants)) {
            if (draftedSince.containsKey(ant.getUUID()) || flat(ant.blockPosition(), middle) <= STRAGGLER_FAR) {
                continue;
            }
            behind++;
            ants.remove(ant);
            if (colony.nest().isInside(level, ant.blockPosition())) {
                lost.merge("stayed home", 1, Integer::sum);
                drop(ant.getUUID());
            } else {
                lost.merge("left behind", 1, Integer::sum);
                letGo(ant.getUUID(), "left behind: not at the halt in time");
            }
        }
        gatheredAtAttack = (int) ants.stream().filter(ant -> flat(ant.blockPosition(), middle) <= RaidMarch.NEAR).count();
        next(Stage.ATTACK, now, tailIn ? "the tail is in" : "its time is up" + (behind > 0 ? ": " + behind + " not here are left behind" : ""));
        attackedAt = now;
        lastActionAt = now;
        takeHidden(level);
        for (MatabeleEntity ant : ants) {
            sendIn(ant);
        }
    }

    /** The middle of the bunch at the halting place: a little back from the route's end, toward home. */
    private BlockPos haltMiddle() {
        return out.point(Math.max(0.0, out.length() - 1.5));
    }

    /** Everyone to its own place round the halting place, the scout in the middle; one far behind walks the trail up to it. */
    private void bunch(ServerLevel level, List<MatabeleEntity> ants) {
        BlockPos middle = haltMiddle();
        for (MatabeleEntity ant : ants) {
            UUID id = ant.getUUID();
            if (flat(ant.blockPosition(), middle) > STRAGGLER_FAR) {
                double along = march.alongOf(out, ant);
                orders.put(id, new Order(out.place(level, Math.min(out.length(), along + RaidMarch.LEAD_STEP), 0.0), HURRY_PACE, find.at()));
                continue;
            }
            orders.put(id, new Order(bunchPlace(level, middle, id, null), 1.0, find.at()));
        }
    }

    /**
     * Soldiers at a raid that goes as numbers (my picks, set to what the measuring scene of the alarm rework's step 5
     * gave near a player: of a raid of 20 that broke in, 0 to 6 raiders dead, 1 to 14 hurt, 1 to 6 soldiers lost): the
     * share of the termite colony's soldiers that comes to the break, and of those the share that each kill a raider,
     * send one home hurt, are stung and carried home.
     */
    private static final double ANSWERING_SHARE = 0.6;
    private static final double SOLDIER_KILLS = 0.12;
    private static final double SOLDIER_HURTS = 0.3;
    private static final double STUNG_SOLDIER_SHARE = 0.25;

    /** How many of {@code of} by a chance {@code share} each (a coin per ant). */
    private static int dice(RandomSource random, int of, double share) {
        int hit = 0;
        for (int i = 0; i < of; i++) {
            if (random.nextDouble() < share) {
                hit++;
            }
        }
        return hit;
    }

    /**
     * <b>The one rule for the catch of raiders going as numbers</b>, as the attack starts: the few who go along with a
     * watched raid, and every raider of a raid out of sight. They break into the galleries round the spot and take the
     * termites feeding under the mud there ({@link RaidCatch}: about {@code per_raider} each, never more than the
     * termite colony can lose), if the termite colony still feeds there; where the raid hunts by the land (the judge's
     * word at the halt, no termite colony on record yet) a small catch with no colony's count to take it from. Brought
     * home with the raid. With nobody's body at the spot the numbers also stand for the fight itself: they take the
     * termites walking in the open there, if any are bodies (nobody else will sting them), some of the termite colony's
     * soldiers come to the break and are stung, and a few raiders die or come home hurt
     * ({@code raids.away.lost_share}, {@code hurt_share}); with real raiders there, the real ants and soldiers fight
     * that out.
     */
    private void takeHidden(ServerLevel level) {
        if (numbersAlong <= 0) {
            return;
        }
        SavannaNumbers.AwayRaidNumbers away = awayNumbers();
        RandomSource random = colony.random();
        int wanted = RaidCatch.couldCarry(colony, numbersAlong, random);
        int taken = 0;
        if (asNumbers && level.isPositionEntityTicking(find.at())) {
            for (AbstractAntEntity insect : inTheOpen(level, colony, find.at(), reach(numbers))) {
                if (taken >= wanted) {
                    break;
                }
                insect.carriedOff(level);
                taken++;
            }
        }
        int inTheOpen = taken;
        Colony termites = RaidCatch.termitesAt(level, find.at(), away.termiteReach());
        boolean feeding = termites != null && !termites.keepsOff(level, find.at());
        boolean byTheLand = termites == null && atHalt != null && atHalt.byTheLand();
        int most = 0;
        int countLost = 0;
        int answering = 0;
        if (feeding) {
            most = RaidCatch.mostToTake(colony, termites);
            int under = Math.max(0, Math.min(wanted - taken, most));
            countLost = RaidCatch.take(level, termites, under);
            taken += under;
            hiddenFrom = termites.getId();
            if (asNumbers) {
                // the soldiers within earshot come to the break and hold the gallery from inside; about a quarter of
                // them are stung and carried home with the workers (the alarm rework, step 5)
                String minor = TermiteSoldierEntity.Rank.MINOR.caste();
                String major = TermiteSoldierEntity.Rank.MAJOR.caste();
                int soldiers = termites.getCount(minor) + termites.getCount(major);
                answering = soldiers <= 0 ? 0 : Math.max(1, Math.min(12, (int) Math.round(soldiers * ANSWERING_SHARE)));
                int stungSoldiers = dice(random, answering, STUNG_SOLDIER_SHARE);
                if (stungSoldiers > 0) {
                    termites.population().loseToHunters(level, termites.population().inside(minor) > 0 ? minor : major, stungSoldiers,
                            AwayReport.What.TAKEN_BY_RAIDERS);
                    taken += stungSoldiers;
                }
            }
        } else if (byTheLand) {
            taken += Math.max(0, Math.min(wanted - taken, Math.max(1, numbersAlong / 10)));
        }
        hiddenCatch += taken;
        say("the " + numbersAlong + " raiders going as numbers take " + taken + " termite(s)" + (inTheOpen > 0 ? ", " + inTheOpen + " of them in the open" : "")
                + (feeding ? " from under the mud (of " + wanted + " they could carry, at most " + most + "); the termite colony's count lost " + countLost
                        + (asNumbers ? ", " + answering + " soldier(s) came to the break" : "")
                        : byTheLand ? " by the land (no termite colony on record in reach yet)"
                                : termites == null ? " (no termite colony near)" : " (the termites keep off the spot)"));
        if (asNumbers) {
            int dead = Math.min(numbersAlong - 1, dice(random, numbersAlong, away.lostShare()) + dice(random, answering, SOLDIER_KILLS));
            int hurt = Math.min(numbersAlong - dead, dice(random, numbersAlong - dead, away.hurtShare()) + dice(random, answering, SOLDIER_HURTS));
            dead = colony.population().loseInside(level, Caste.WORKER, Math.max(0, dead), AwayReport.What.RAIDERS_LOST);
            colony.away().note(level, AwayReport.What.RAIDERS_HURT, hurt);
            if (dead > 0) {
                numbersAlong = Math.max(1, numbersAlong - dead);
                size = Math.max(1, size - dead);
                lost.merge("dead at the spot", dead, Integer::sum);
            }
            if (dead + hurt > 0) {
                say("the fight cost " + dead + " raider(s) dead and " + hurt + " hurt");
            }
            ColonyManager.get(level).markChanged();
        }
    }

    /** One raider sent in to fight round the find; the order is the raid's own, and only the raid ends it. */
    private void sendIn(MatabeleEntity ant) {
        if (ant.getFightSpot() != null && ant.fightOrderBy() != this) {
            return; // (called away by another fight: sent in once that is over)
        }
        ant.orderToFight(find.at(), attackedAt + numbers.fightTicks(), reach(numbers), this);
        sentIn.add(ant.getUUID());
    }

    /** Every raider's fight for the raid ends now. */
    private void endFights(List<MatabeleEntity> ants) {
        for (MatabeleEntity ant : ants) {
            if (ant.fightOrderBy() == this) {
                ant.endFightOrder(this);
            }
        }
        returning.clear();
    }

    /** What lies stung round the spot goes onto the raid's own list; a new one is work at the spot. */
    private void noteStung(ServerLevel level, long now) {
        double reach = numbers.leash() + 4.0;
        BlockPos at = find.at();
        for (AbstractAntEntity insect : level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(at).inflate(reach, 8.0, reach),
                insect -> insect.isAlive() && insect.isParalysed() && !(insect instanceof MatabeleEntity)
                        && !colony.getId().equals(insect.getColonyId()))) {
            if (stung.add(insect.getUUID())) {
                lastActionAt = now;
            }
        }
    }

    /**
     * Attack (real: 5 to 30 minutes; the majors bite the mud open, the minors go in and sting): every raider at the
     * halting place is sent in with a fight order. One that fights farther than {@code leash} blocks from the spot stops
     * and comes back, and fights again once near. It ends once no termite was stung or gone for in {@code quiet_ticks},
     * never after {@code fight_ticks}; then every fight order ends at once.
     */
    private void attack(ServerLevel level, List<MatabeleEntity> ants, long now) {
        noteStung(level, now);
        bunch(level, ants); // (the places of those not fighting: one whose own fight is over waits where the column halted)
        BlockPos spot = find.at();
        BlockPos middle = haltMiddle();
        double leash = numbers.leash();
        for (MatabeleEntity ant : ants) {
            UUID id = ant.getUUID();
            double off = flat(ant.blockPosition(), spot);
            if (fightsForTheColumn(ant)) {
                if (off > leash) {
                    ant.endFightOrder(this);
                    returning.add(id);
                    say(RaidGround.shortId(id) + " is " + Math.round(off) + " blocks from the spot, past the leash of " + Math.round(leash)
                            + ": it stops fighting and comes back");
                }
            } else if (ant.getFightSpot() == null) {
                if (returning.contains(id)) {
                    if (off <= leash * COME_BACK) {
                        returning.remove(id);
                        sendIn(ant);
                    } else {
                        orders.put(id, new Order(out.place(level, out.length(), 0.0), HURRY_PACE, spot));
                    }
                } else if (!sentIn.contains(id) && flat(ant.blockPosition(), middle) <= TAIL_IN) {
                    sendIn(ant); // (a straggler arriving, or one back from another fight)
                }
            }
        }
        long quiet = now - lastActionAt;
        if (quiet >= hoursSound().quietTicks()) {
            endFights(ants);
            next(Stage.REGROUP, now, "no termite stung or gone for in " + quiet / 20 + " s; " + stung.size() + " lie stung");
        } else if (now - attackedAt >= numbers.fightTicks()) {
            endFights(ants);
            next(Stage.REGROUP, now, "its time is up; " + stung.size() + " lie stung");
        }
    }

    /** The stung termites on the raid's list still to be taken: the termite workers first, then the nearest to the spot. */
    private List<AbstractAntEntity> preyLeft(ServerLevel level, long now) {
        List<AbstractAntEntity> prey = new ArrayList<>();
        BlockPos at = find.at();
        for (UUID id : List.copyOf(stung)) {
            if (!(level.getEntity(id) instanceof AbstractAntEntity insect) || !insect.isAlive() || !insect.isParalysed()) {
                stung.remove(id); // (carried off, dead, or its paralysis over)
            } else if (!insect.isPinned() && noWay.getOrDefault(id, Long.MIN_VALUE) <= now) {
                prey.add(insect);
            }
        }
        prey.sort(Comparator.<AbstractAntEntity>comparingInt(insect -> insect instanceof TermiteSoldierEntity ? 1 : 0)
                .thenComparingDouble(insect -> insect.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
        return prey;
    }

    /** Whether this raider has room in its jaws for another termite (empty, or holding fewer than it can carry). */
    private static boolean hasRoom(MatabeleEntity ant) {
        int held = MatabeleLoads.termitesHeldBy(ant);
        return held > 0 ? held < ant.termitesItCanCarry() : !ant.hasFullMandibles();
    }

    /**
     * Regroup and load (real: the ants gather where they halted, the majors holding the termites; hurt nestmates call
     * and are carried, Frank et al. 2017): a hurt nestmate first, then each raider with room is given the nearest stung
     * termite on the raid's list nobody has, the majors first (a bundle of up to about 10, a minor 1 to 3); the rest
     * bunch at the halting place. It ends once nothing is left to fetch (the list is empty or every jaw is full) and the
     * raiders are back at the halting place, never after {@code regroup_ticks}.
     */
    private void regroup(ServerLevel level, List<MatabeleEntity> ants, long now) {
        noteStung(level, now); // (the last blows land as the attack ends)
        List<AbstractAntEntity> prey = preyLeft(level, now);
        Map<UUID, AbstractAntEntity> left = new HashMap<>();
        for (AbstractAntEntity insect : prey) {
            left.put(insect.getUUID(), insect);
        }
        List<MatabeleEntity> hurt = hurtWaiting(level);
        for (MatabeleEntity one : hurt) {
            left.put(one.getUUID(), one);
        }
        taking.entrySet().removeIf(entry -> !left.containsKey(entry.getValue())); // (taken, dead, or no way: free again)
        Set<UUID> taken = new HashSet<>(taking.values());
        bunch(level, ants); // (the places of those with nothing to fetch)
        List<MatabeleEntity> byJaws = new ArrayList<>(ants);
        byJaws.sort(Comparator.comparing(ant -> !ant.hasMajorBody())); // (the majors take their bundles first)
        boolean anyFetching = false;
        for (MatabeleEntity ant : byJaws) {
            UUID mine = taking.get(ant.getUUID());
            if (mine == null && !ant.hasFullMandibles()) {
                MatabeleEntity nearest = hurt.stream().filter(one -> !taken.contains(one.getUUID())
                                && ant.getBbWidth() >= one.getBbWidth() * RESCUER_SIZE)
                        .min(Comparator.comparingDouble(one -> one.distanceToSqr(ant))).orElse(null);
                if (nearest != null) {
                    mine = nearest.getUUID();
                    taking.put(ant.getUUID(), mine);
                    taken.add(mine);
                    say(RaidGround.shortId(ant.getUUID()) + " goes to fetch hurt " + RaidGround.shortId(mine));
                }
            }
            if (mine == null && hasRoom(ant)) {
                AbstractAntEntity nearest = prey.stream().filter(insect -> !taken.contains(insect.getUUID()))
                        .min(Comparator.comparingDouble(insect -> insect.distanceToSqr(ant))).orElse(null);
                if (nearest != null) {
                    mine = nearest.getUUID();
                    taking.put(ant.getUUID(), mine);
                    taken.add(mine);
                }
            }
            AbstractAntEntity insect = mine == null ? null : left.get(mine);
            if (insect != null) {
                orders.put(ant.getUUID(), new Order(insect.blockPosition(), 1.0, insect.blockPosition(), mine));
                anyFetching = true;
            }
        }
        BlockPos middle = haltMiddle();
        boolean gathered = ants.stream().allMatch(ant -> draftedSince.containsKey(ant.getUUID()) || flat(ant.blockPosition(), middle) <= TAIL_IN);
        long spent = now - stageSince;
        if (!anyFetching && gathered || spent >= numbers.regroupTicks()) {
            taking.clear();
            int leftLying = (int) prey.stream().filter(insect -> insect.isAlive()).count();
            if (turned == null && carried == 0 && hiddenCatch == 0 && hurtAtSpot == 0) {
                foundNothing = true; // (they were at the spot and bring nothing back)
            }
            String why = anyFetching || !gathered ? "its time is up" + (leftLying > 0 ? ", " + leftLying + " stung termite(s) left lying" : "")
                    : leftLying > 0 ? "every jaw is full, " + leftLying + " stung termite(s) left lying" : "everything stung is picked up";
            next(Stage.MARCH_HOME, now, (turned != null ? "turns home: " + turned + "; " : "") + why + " (" + carried + " termite(s) in "
                    + carriers + " ant(s)' jaws)");
        }
    }

    /**
     * A raider at its termite picks it up: the termite leaves the world (and its colony), one more in the raider's
     * jaws. False if it is not there to take (gone, taken, too far, no room).
     */
    public boolean pickUp(ServerLevel level, MatabeleEntity ant, UUID prey) {
        if (hurtHere.contains(prey)) {
            return fetchHurt(level, ant, prey);
        }
        if (!prey.equals(taking.get(ant.getUUID())) || !(level.getEntity(prey) instanceof AbstractAntEntity insect)
                || !insect.isAlive() || !insect.isParalysed() || insect.distanceTo(ant) > PICK_REACH + 0.5 || !hasRoom(ant)) {
            return false;
        }
        int held = MatabeleLoads.termitesHeldBy(ant);
        insect.carriedOff(level);
        ant.setLoad(MatabeleLoads.termites(held + 1));
        taking.remove(ant.getUUID());
        stung.remove(prey);
        carried++;
        if (held == 0) {
            carriers++;
        }
        level.playSound(null, ant.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.15F, 1.6F);
        return true;
    }

    /** The raider found no way to its termite: someone else may try it, a little later. */
    public void noWayTo(ServerLevel level, MatabeleEntity ant, UUID prey) {
        if (prey.equals(taking.get(ant.getUUID()))) {
            taking.remove(ant.getUUID());
        }
        noWay.put(prey, level.getGameTime() + NO_WAY_TICKS);
    }

    /**
     * A raider at the hurt nestmate it was sent for: it pulls a clamped foe off first (the hurt one held still,
     * {@code field_pull_ticks}), then takes it up in its jaws if it has legs gone. Freed and whole, the hurt one walks
     * home by itself. True once it is carried.
     */
    private boolean fetchHurt(ServerLevel level, MatabeleEntity ant, UUID id) {
        if (!id.equals(taking.get(ant.getUUID())) || !(level.getEntity(id) instanceof MatabeleEntity hurt) || !hurt.isAlive()
                || hurt.distanceTo(ant) > PICK_REACH + 0.5) {
            return false;
        }
        long now = level.getGameTime();
        if (hurt.clamp().isOn()) {
            hurt.tendedBy(ant.getUUID(), now + 40);
            FightNumbers.Rescue n = Wounds.numbers(hurt);
            long since = pullingSince.computeIfAbsent(id, key -> now);
            if (now - since < (n != null ? n.fieldPullTicks() : 200)) {
                return false; // (pulling)
            }
            pullingSince.remove(id);
            hurt.clamp().takeOff(level);
            if (hurt.clamp().isOn()) {
                return false;
            }
            say(RaidGround.shortId(ant.getUUID()) + " pulled the foe off hurt " + RaidGround.shortId(id));
        }
        if (hurt.isCarriable() && ant.pickUpHurt(hurt)) {
            hurtHere.remove(id);
            taking.remove(ant.getUUID());
            rescued++;
            say(RaidGround.shortId(ant.getUUID()) + " takes hurt " + RaidGround.shortId(id) + " up to carry it home");
            return true;
        }
        if (!hurt.isCarriable()) {
            hurtHere.remove(id);
            taking.remove(ant.getUUID());
            hurt.callForHelp(0);
            letGo(id, "freed of its foe, it walks home");
        }
        return false;
    }

    /** The hurt raiders still waiting at the spot (alive, still needing help, not carried yet). */
    private List<MatabeleEntity> hurtWaiting(ServerLevel level) {
        List<MatabeleEntity> waiting = new ArrayList<>();
        for (UUID id : hurtHere) {
            if (level.getEntity(id) instanceof MatabeleEntity hurt && hurt.isAlive() && hurt.needsRescue() && !hurt.isPassenger()) {
                waiting.add(hurt);
            }
        }
        return waiting;
    }

    /** Every update: the hurt at the spot keep calling (they stand still); those dead, carried or healed are dropped. */
    private void tendHurt(ServerLevel level, long now) {
        for (UUID id : List.copyOf(hurtHere)) {
            if (!(level.getEntity(id) instanceof MatabeleEntity hurt) || !hurt.isAlive() || hurt.isPassenger()) {
                hurtHere.remove(id);
                pullingSince.remove(id);
                continue;
            }
            if (!hurt.needsRescue()) {
                hurtHere.remove(id);
                pullingSince.remove(id);
                letGo(id, "no longer needs help, it walks home");
                continue;
            }
            hurt.callForHelp(now + EVERY * 3L);
        }
    }

    /** The raid leaves the spot (or ends): the hurt nobody took are let go, to limp home along the trail by themselves. */
    private void releaseHurt(String why) {
        for (UUID id : List.copyOf(hurtHere)) {
            say("hurt " + RaidGround.shortId(id) + " is left: " + why);
            lost.merge("hurt, left at the spot", 1, Integer::sum);
            letGo(id, "hurt, nobody carried it");
        }
        hurtHere.clear();
        pullingSince.clear();
    }

    /** Whether the raid is at the find (halted, attacking or regrouping): where the hurt are fetched. */
    private boolean atTheSpot() {
        return stage == Stage.HALT || stage == Stage.ATTACK || stage == Stage.REGROUP;
    }

    /** The raid's order by where each ant is along the way home, the nearest home in front and leading. */
    private void sortHomeward(List<MatabeleEntity> ants) {
        if (ants.isEmpty()) {
            return;
        }
        Map<UUID, Double> along = new HashMap<>();
        for (MatabeleEntity ant : ants) {
            along.put(ant.getUUID(), march.alongOf(back, ant));
        }
        members.sort(Comparator.comparingDouble(id -> -along.getOrDefault(id, -1.0)));
        leader = members.get(0);
    }

    /**
     * March home (real: the same path both ways, a middle 7.6 minutes against 7 out, Frank et al. 2018): the kept march
     * back along the line, the ant nearest home in front, a little slower laden; near the nest the column breaks up and
     * each ant streams in by itself. It ends once everyone is home, never after the march's time: then whoever is still
     * out is let go and walks home along the trail alone.
     */
    private void marchHome(ServerLevel level, List<MatabeleEntity> ants, long now) {
        BlockPos entrance = colony.getCenter();
        for (MatabeleEntity ant : List.copyOf(ants)) {
            if (flat(ant.blockPosition(), entrance) <= HOME && Math.abs(ant.getY() - entrance.getY()) <= HOME
                    || colony.nest().isInside(level, ant.blockPosition())) {
                drop(ant.getUUID());
                ants.remove(ant);
                cameHome++;
            }
        }
        if (ants.isEmpty() || now - stageSince >= marchLimit()) {
            trail.walked(now); // (they laid scent on the way home too)
            int outStill = ants.size();
            for (UUID id : List.copyOf(members)) {
                letGo(id, "the march home's time ran out");
            }
            end(now, outStill == 0 ? "all home" : "its time is up with " + outStill + " still out: they walk home by themselves");
            return;
        }
        if (!memberSet.contains(leader)) {
            leader = nextLeader();
        }
        if (!homeLed) {
            homeLed = true;
            sortHomeward(ants);
        }
        ants.sort(Comparator.comparingInt(ant -> members.indexOf(ant.getUUID())));
        // Near the nest the column breaks up and every ant streams in by itself, side by side (real raiders crowd into the
        // entrance), to the first cell beside the entrance an ant can stand in (a dead mound's entrance is an open hole).
        BlockPos doorstep = back.place(level, back.length(), 0.0);
        List<MatabeleEntity> line = new ArrayList<>();
        for (MatabeleEntity ant : ants) {
            if (flat(ant.blockPosition(), entrance) <= HOME + STREAM_IN) {
                orders.put(ant.getUUID(), new Order(doorstep, 1.0, null));
            } else {
                line.add(ant);
            }
        }
        if (!line.isEmpty() && march(level, line, back, now)) {
            for (MatabeleEntity ant : line) {
                orders.put(ant.getUUID(), new Order(doorstep, 1.0, null));
            }
        }
        if (carriers > 0) {
            orders.replaceAll((id, order) -> order.slower(carry.ladenPace())); // (laden, the column walks a little slower)
        }
        if (now - homeSaidAt >= RaidMarch.WAIT_SAY_EVERY) {
            homeSaidAt = now;
            MatabeleEntity hindmost = ants.stream().min(Comparator.comparingDouble(ant -> march.alongOf(back, ant))).orElse(null);
            say("on the way home " + (now - stageSince) / 20 + " s: " + cameHome + " in, " + ants.size() + " out; the hindmost "
                    + (hindmost == null ? "?" : who(hindmost, back)));
        }
    }

    private SavannaNumbers.HoursSoundNumbers hoursSound() {
        return colony.profile().section(SavannaNumbers.HOURS_SOUND).orElse(SavannaNumbers.HoursSoundNumbers.DEFAULT);
    }

    /**
     * The squeak (Hoelldobler et al. 1994: a raid column stridulates as it goes, a warning to big animals): for each
     * player within {@code squeak_hear} blocks of a raider, the raider nearest them squeaks, louder and more often the
     * nearer the player. Spectators hear nothing.
     */
    private void squeak(ServerLevel level, List<MatabeleEntity> ants, long now) {
        SavannaNumbers.HoursSoundNumbers sound = hoursSound();
        double hear = sound.squeakHear();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            MatabeleEntity nearest = null;
            double best = hear * hear;
            for (MatabeleEntity ant : ants) {
                double d = ant.distanceToSqr(player);
                if (d <= best) {
                    best = d;
                    nearest = ant;
                }
            }
            if (nearest == null) {
                continue;
            }
            double distance = Math.sqrt(best);
            Long last = squeakedAt.get(player.getUUID());
            if (last != null && now - last < sound.everyAt(distance)) {
                continue;
            }
            squeakedAt.put(player.getUUID(), now);
            nearest.warningSqueak(sound.loudnessAt(distance));
        }
    }

    /** The raid in words (the colony list). */
    public String describe() {
        StringBuilder line = new StringBuilder("; a raid of ").append(size).append(asNumbers ? " out of sight (" : " (").append(members.size()).append(" real out")
                .append(numbersAlong > 0 ? ", " + numbersAlong + " as numbers" : "").append("), ").append(stage.words())
                .append(", to ").append(find.at().toShortString());
        if (hiddenCatch > 0) {
            line.append(", ").append(hiddenCatch).append(" termite(s) taken by the numbers");
        }
        if (carried > 0) {
            line.append(", carrying ").append(carried).append(" termite(s)");
        }
        if (!stung.isEmpty()) {
            line.append(", ").append(stung.size()).append(" lying stung");
        }
        if (!Double.isNaN(march.castingGapWidth())) {
            line.append(", casting about at a gap in the trail of ").append(Math.round(march.castingGapWidth() * 10) / 10.0).append(" blocks");
        }
        if (march.gapsCrossed() > 0) {
            line.append(", ").append(march.gapsCrossed()).append(" gap(s) in the trail crossed");
        }
        if (turned != null) {
            line.append(", turned home: ").append(turned);
        }
        if (lastLevel != null) {
            line.append(", ").append((lastLevel.getGameTime() - stageSince) / 20).append(" s in this stage");
        }
        return line.toString();
    }

    @Nullable
    private static BlockPos standAt(ServerLevel level, double x, double z, int y) {
        return RaidGround.standAt(level, x, z, y);
    }

    private static double flat(BlockPos a, BlockPos b) {
        return RaidGround.flat(a, b);
    }
}
