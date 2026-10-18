package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The patrollers (brain step 4b, part 3; the user's decisions in {@code docs/DESIGN.md}, "4b parts 2 and 3"). Real red
 * harvester ants (<i>Pogonomyrmex barbatus</i>, Deborah Gordon's work): every morning a group of patrollers, workers
 * taken from the nest maintenance workers (the builders), is the first out. They walk over and round the hill and out
 * along the first part of the trails, and come back. Foraging starts only once they are back (Greene and Gordon: with
 * the patrollers taken away, that colony did not forage that day), and the foragers go only out the ways the patrollers
 * came back from. A patroller that meets trouble turns back, that way stays shut, more workers turn out to patrol, and
 * fewer foragers go out.
 *
 * <p><b>Rounds.</b> A round starts each time the day clock opens ({@link Colony#isForagingTime}: at dawn, after the
 * midday rest, after rain) and ends when it shuts; the dawn patrol is the profile's share of the workers, a later one
 * part of that. The ways: the colony's trunk trails, strongest first, then new directions (where the colony knows the
 * most food, so a young colony with no trail yet is not sent the wrong way), at most one per compass point.
 *
 * <p><b>Real ones and numbers.</b> The real workers of the colony patrol as far as they go (builders first), the rest as
 * numbers, which take the walk's time and come back safely unless their way runs through remembered danger where a
 * hostile really is (brain step 4g, step 4: then one can be lost, see {@link #lostOnTheWay}). Real patrollers walk the round themselves ({@code PatrolGoal}, the mind); this part only hands out
 * the jobs and keeps count. The door ({@link Door}) then decides how many foragers go out.
 *
 * <p>Species-neutral: any colony whose profile has {@code food.patrol} (only the harvester ant now). Without one the
 * foragers are never held back.
 */
public class Patrol {
    /** At most this many ways one patrol checks (one per compass point). */
    static final int MOST_WAYS = 8;
    /** A real patroller not seen for this long (its land unloaded, or it went into the numbers) counts as home safely. */
    private static final int OUT_OF_SIGHT_TICKS = 200;
    /** What each number patroller's walk costs besides the blocks: out of the nest, a look round, in again. */
    private static final int TRIP_TICKS = FieldLife.TRIP_TICKS;
    /** How far apart (sideways, blocks) the round over the hill goes from the nest's middle. */
    public static final int HILL_ROUND = Hill.MOUND_RADIUS + 1;
    /** Around the hill: the ground within this of the middle opens once any patroller is back. */
    private static final int HILL_ZONE = HILL_ROUND + 2;

    /** Why a way was shut. */
    public enum Shut {
        DANGER("danger"), LOST("a patroller lost"), NEIGHBOURS("another colony");

        final String label;

        Shut(String label) {
            this.label = label;
        }
    }

    /** One way a patrol checks this round. */
    static final class Way {
        final double angle;
        /** Whether it is a trunk trail (else a new direction). */
        final boolean trail;
        /** How far out (blocks from the nest's middle) its patrollers go. */
        final int reach;
        int sent;
        int back;
        @Nullable
        Shut shut;

        Way(double angle, boolean trail, int reach) {
            this.angle = angle;
            this.trail = trail;
            this.reach = reach;
        }
    }

    /** A real patroller's job this round: the way it checks (-1: only the hill, after danger). */
    public record Job(int way, double angle, int reach) {
        public boolean hillOnly() {
            return way < 0;
        }
    }

    /** A number patroller out: its way (-1: the hill) and when it is back. */
    private record Out(int way, long backAt) {
    }

    /** A way shut for the rest of the day. */
    private record ShutWay(double angle, Shut why) {
    }

    private final Colony colony;
    // --- this round (saved) ---
    private final List<Way> ways = new ArrayList<>();
    private final Map<UUID, Job> real = new HashMap<>();
    /** When each real patroller was last seen loaded. Not saved (starts again at the load). */
    private final Map<UUID, Long> seen = new HashMap<>();
    /** When each real patroller was given its job, until it sets off. Not saved. */
    private final Map<UUID, Long> waitingToStart = new HashMap<>();
    /** Workers passed over this round (busy with something else when their turn came). Not saved. */
    private final java.util.Set<UUID> passedOver = new java.util.HashSet<>();
    /** A patroller busy with something else (feeding the young, say) this long is passed over: another goes. */
    private static final int START_TICKS = 400;
    private final List<Out> numbers = new ArrayList<>();
    private boolean roundOn;
    /** A patroller (real or a number) came back safely this round: the hill is checked. */
    private boolean anyBack;
    /** The danger reaction (more patrollers on the hill) came this round already. */
    private boolean reinforced;
    private long roundStart;
    private long roundClock;
    private int sent;
    private int back;
    private int lost;
    // --- the day (saved) ---
    private long day = Long.MIN_VALUE;
    private int roundsToday;
    private boolean openedToday;
    private boolean dangerToday;
    private final List<ShutWay> shutToday = new ArrayList<>();
    private boolean clockWasOpen;
    /** Tests only: whether this runs at all (null: always, except on the game test server). Not saved. */
    @Nullable
    private Boolean onForTests;

    Patrol(Colony colony) {
        this.colony = colony;
    }

    @Nullable
    private SpeciesProfile.PatrolNumbers numbers() {
        return colony.profile().food().patrol().orElse(null);
    }

    /** Whether this colony sends patrollers before it forages (always in a game; the game tests turn it on where they test it). */
    public boolean isOn(Level level) {
        if (numbers() == null) {
            return false;
        }
        if (onForTests != null) {
            return onForTests;
        }
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
    }

    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    // --- what the foragers ask ----------------------------------------------------------------------------------

    /** Whether the foragers may go out at all now: a patroller is back safely this round (always without patrols). */
    public boolean mayForage(Level level) {
        return !isOn(level) || roundOn && anyBack;
    }

    /**
     * Whether a forager may go for food here now: only once a patroller is back, near the hill (it was walked round) or
     * within the spread of a way a patroller came back from, and never a way shut for today. Always without patrols.
     */
    public boolean isOpen(Level level, BlockPos food) {
        if (!isOn(level)) {
            return true;
        }
        if (!roundOn || !anyBack) {
            return false;
        }
        double angle = angleOf(food);
        double spread = spread();
        for (ShutWay way : shutToday) {
            if (TrunkTrails.difference(way.angle(), angle) <= spread) {
                return false;
            }
        }
        // Round the hill; a small colony's patrollers walk all the near ground a young colony forages (my pick, so the
        // young colony is not slowed: every direction within the patrol's reach).
        boolean small = level instanceof ServerLevel serverLevel && colony.door().isSmallColony(serverLevel);
        if (flat(food) <= (small ? reach() : HILL_ZONE)) {
            return true;
        }
        for (Way way : ways) {
            if (way.back > 0 && way.shut == null && TrunkTrails.difference(way.angle, angle) <= spread) {
                return true;
            }
        }
        return false;
    }

    /** The share of the foragers that may be out today: all of them, or fewer after danger met by a patroller. */
    public float foragerShareToday(Level level) {
        SpeciesProfile.PatrolNumbers numbers = numbers();
        return numbers == null || !isOn(level) || !dangerToday ? 1.0F : numbers.foragersAfterDanger();
    }

    // --- what a real patroller tells -------------------------------------------------------------------------------

    /** This real worker's patrol job this round, or null. */
    @Nullable
    public Job jobOf(UUID ant) {
        return roundOn ? real.get(ant) : null;
    }

    /** This real patroller sets off on its round (it is no longer waiting to be passed over). */
    public void setsOff(UUID ant) {
        waitingToStart.remove(ant);
    }

    /**
     * A real patroller is home. {@code calm}: it came back safely (its way opens and it touches the waiting foragers it
     * passes, see {@link Door}); otherwise it turned back from trouble (already counted).
     */
    public void home(ServerLevel level, AbstractAntEntity ant, boolean calm) {
        Job job = real.remove(ant.getUUID());
        seen.remove(ant.getUUID());
        waitingToStart.remove(ant.getUUID());
        if (job == null) {
            return;
        }
        if (calm) {
            cameBack(level, job.way());
            colony.door().homecomer(level, ant, false);
        }
        ColonyManager.get(level).markChanged();
    }

    /** A real patroller met danger (hurt, or a hostile mob close): it turns back and its way is shut for the day. */
    public void danger(ServerLevel level, AbstractAntEntity ant, String what) {
        Job job = real.get(ant.getUUID());
        if (job == null) {
            return;
        }
        AntColoniesMod.LOGGER.info("[patrol] colony {}: a patroller met danger ({}) at {} and turns back", colony.getId(), what,
                ant.blockPosition().toShortString());
        shut(level, job, Shut.DANGER);
        reactToDanger(level);
        real.put(ant.getUUID(), new Job(Integer.MIN_VALUE, job.angle(), job.reach())); // still out, going home alarmed
    }

    /** A real patroller met another colony's ants or trail: it turns back and that way is shut for the day. */
    public void neighbours(ServerLevel level, AbstractAntEntity ant) {
        Job job = real.get(ant.getUUID());
        if (job == null) {
            return;
        }
        AntColoniesMod.LOGGER.info("[patrol] colony {}: a patroller met another colony at {} and turns back", colony.getId(),
                ant.blockPosition().toShortString());
        shut(level, job, Shut.NEIGHBOURS);
        real.put(ant.getUUID(), new Job(Integer.MIN_VALUE, job.angle(), job.reach()));
    }

    /** Whether this real patroller has turned back from trouble (it goes straight home and opens nothing). */
    public boolean turnedBack(UUID ant) {
        Job job = real.get(ant);
        return job != null && job.way() == Integer.MIN_VALUE;
    }

    /** A worker of this colony died: if it was out patrolling it never comes back, and its way stays shut for the day. */
    public void died(ServerLevel level, UUID ant) {
        Job job = real.remove(ant);
        seen.remove(ant);
        waitingToStart.remove(ant);
        if (job == null || !roundOn) {
            return;
        }
        lost++;
        colony.away().note(level, AwayReport.What.PATROLLERS_LOST, 1);
        AntColoniesMod.LOGGER.info("[patrol] colony {}: a patroller was killed", colony.getId());
        shut(level, job, Shut.LOST);
        reactToDanger(level);
    }

    /**
     * Brain step 4g, step 4: a patrol number checking way {@code way} through remembered danger may be lost out of sight
     * (real patrollers are the ones that meet danger first). Its chance is that of the most dangerous spot along the
     * way's line ({@link DangerMap#lossChance}), and its body lies at that dangerous place. A lost one counts as a killed
     * patroller: its way is shut for the day, more patrol the hill (the caller reacts), fewer forage; the danger memory
     * only learns of it if the body is found. True if it was lost.
     */
    private boolean lostOnTheWay(ServerLevel level, int way) {
        SpeciesProfile.LostNumbers settings = colony.danger().lostNumbers();
        if (settings == null || !settings.patrollers() || colony.danger().isEmpty() || way < 0 || way >= ways.size()) {
            return false;
        }
        Way checked = ways.get(way);
        BlockPos middle = colony.getCenter();
        double worst = 0;
        DangerMap.Place worstPlace = null;
        for (int blocks = HILL_ROUND; blocks <= checked.reach; blocks += 2) {
            BlockPos at = new BlockPos(middle.getX() + (int) Math.floor(Math.cos(checked.angle) * blocks), middle.getY(),
                    middle.getZ() + (int) Math.floor(Math.sin(checked.angle) * blocks));
            DangerMap.Place[] place = new DangerMap.Place[1];
            double chance = colony.danger().lossChance(level, at, place);
            if (chance > worst && place[0] != null) {
                worst = chance;
                worstPlace = place[0];
            }
        }
        if (worstPlace == null || colony.random().nextDouble() >= worst || !colony.danger().lose(level, worstPlace)) {
            return false;
        }
        lost++;
        colony.away().note(level, AwayReport.What.PATROLLERS_LOST, 1);
        AntColoniesMod.LOGGER.info("[patrol] colony {}: a patrol number was lost toward {}", colony.getId(), TrunkTrails.compass(checked.angle));
        shut(level, new Job(way, checked.angle, checked.reach), Shut.LOST);
        return true;
    }

    private void shut(ServerLevel level, Job job, Shut why) {
        if (job.way() >= 0 && job.way() < ways.size()) {
            Way way = ways.get(job.way());
            if (way.shut == null) {
                way.shut = why;
            }
        }
        if (job.way() != Integer.MIN_VALUE && !job.hillOnly()) {
            shutToday.add(new ShutWay(job.angle(), why));
        }
        ColonyManager.get(level).markChanged();
    }

    /**
     * Danger met (Gordon: a disturbance draws more workers out to patrol, and fewer forage): as many again patrol the
     * hill (once a round), and fewer foragers may go out for the rest of the day.
     */
    private void reactToDanger(ServerLevel level) {
        dangerToday = true;
        if (reinforced) {
            return;
        }
        reinforced = true;
        SpeciesProfile.PatrolNumbers numbers = numbers();
        int extra = numbers == null ? 0 : Math.round(sent * numbers.extraOnDanger());
        if (extra > 0) {
            send(level, extra, true);
        }
    }

    // --- once a second ------------------------------------------------------------------------------------------

    /** Once a second, here ({@code away} false, from {@link Colony#tick}) or away (from {@link AwayLife#tickAway}). */
    void tick(ServerLevel level, boolean away) {
        if (!isOn(level)) {
            return;
        }
        long now = level.getGameTime();
        long today = Math.floorDiv(colony.timeOfDay(level), 24000L);
        if (today != day) {
            newDay(level, today);
        }
        boolean open = colony.isForagingTime(level);
        if (open && !clockWasOpen) {
            startRound(level, now);
        } else if (!open && clockWasOpen) {
            endRound(level);
        }
        clockWasOpen = open;
        if (!roundOn) {
            return;
        }
        if (sent == 0 && colony.getCount(WorkerAntEntity.CASTE) > 0) {
            // A round with nobody to send (a founding nest): its first workers patrol as soon as they are out.
            send(level, roundsToday <= 1 ? dawnSize() : laterSize(), false);
        }
        boolean lostNow = false;
        for (Iterator<Out> it = numbers.iterator(); it.hasNext(); ) {
            Out out = it.next();
            if (out.backAt() <= now) {
                it.remove();
                if (lostOnTheWay(level, out.way())) {
                    lostNow = true;
                    continue; // never came back: its way stays shut for the day
                }
                cameBack(level, out.way());
                colony.door().patrollerNumbersHome(1);
            }
        }
        if (lostNow) {
            reactToDanger(level); // (after the walk through the numbers: it may send more out)
        }
        // A patroller still busy with something else after a while is passed over: someone else goes in its place.
        int replace = 0;
        for (Iterator<Map.Entry<UUID, Long>> it = waitingToStart.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() < START_TICKS && now >= entry.getValue()) {
                continue;
            }
            it.remove();
            Job job = real.remove(entry.getKey());
            seen.remove(entry.getKey());
            passedOver.add(entry.getKey());
            if (job != null && job.way() >= 0 && job.way() < ways.size()) {
                ways.get(job.way()).sent--;
            }
            if (job != null) {
                sent--;
                replace++;
            }
        }
        if (replace > 0) {
            send(level, replace, false);
        }
        // Real patrollers not seen for a while (unloaded, or gone into the numbers) are home safely.
        for (Iterator<Map.Entry<UUID, Job>> it = real.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Job> entry = it.next();
            if (level.getEntity(entry.getKey()) instanceof AbstractAntEntity ant && ant.isAlive()) {
                seen.put(entry.getKey(), now);
                continue;
            }
            long last = seen.computeIfAbsent(entry.getKey(), id -> now);
            if (now - last >= OUT_OF_SIGHT_TICKS || now < last) {
                it.remove();
                seen.remove(entry.getKey());
                if (entry.getValue().way() != Integer.MIN_VALUE) {
                    cameBack(level, entry.getValue().way());
                    colony.door().patrollerNumbersHome(1);
                }
            }
        }
    }

    private void newDay(ServerLevel level, long today) {
        if (day != Long.MIN_VALUE && roundsToday > 0 && !openedToday) {
            colony.away().note(level, AwayReport.What.NO_FORAGING_DAYS, 1);
            AntColoniesMod.LOGGER.info("[patrol] colony {}: no patroller came back safely all day, so no foraging", colony.getId());
        }
        day = today;
        roundsToday = 0;
        openedToday = false;
        dangerToday = false;
        shutToday.clear();
        ColonyManager.get(level).markChanged();
    }

    private int workers() {
        return colony.getCount(WorkerAntEntity.CASTE);
    }

    private int dawnSize() {
        SpeciesProfile.PatrolNumbers numbers = numbers();
        return numbers == null ? 0 : numbers.dawnPatrol(workers());
    }

    private int laterSize() {
        SpeciesProfile.PatrolNumbers numbers = numbers();
        return numbers == null ? 0 : numbers.laterPatrol(workers());
    }

    /** The clock has opened: a new round, with its patrol. */
    private void startRound(ServerLevel level, long now) {
        roundsToday++;
        roundOn = true;
        anyBack = false;
        reinforced = false;
        ways.clear();
        real.clear();
        seen.clear();
        waitingToStart.clear();
        passedOver.clear();
        numbers.clear();
        sent = 0;
        back = 0;
        lost = 0;
        roundStart = now;
        roundClock = colony.timeOfDay(level);
        colony.door().shut();
        int size = roundsToday == 1 ? dawnSize() : laterSize();
        if (size > 0) {
            send(level, size, false);
        }
        ColonyManager.get(level).markChanged();
    }

    /** The clock has shut: the round is over; the patrollers still out go home (their jobs are gone). */
    private void endRound(ServerLevel level) {
        roundOn = false;
        ways.clear();
        real.clear();
        seen.clear();
        waitingToStart.clear();
        passedOver.clear();
        numbers.clear();
        colony.door().shut();
        ColonyManager.get(level).markChanged();
    }

    /** Sends {@code count} patrollers out: the real workers first (builders first), the rest as numbers. */
    private void send(ServerLevel level, int count, boolean hillOnly) {
        if (!hillOnly && ways.isEmpty()) {
            chooseWays(level, count);
        }
        long now = level.getGameTime();
        int workers = Math.max(1, workers());
        int inside = colony.population().inside(WorkerAntEntity.CASTE);
        List<AbstractAntEntity> candidates = colony.away().nestLoaded(level) ? candidates(level) : List.of();
        if (candidates.isEmpty() && !passedOver.isEmpty() && inside <= 0) {
            passedOver.clear(); // nobody else: the busy ones get their turn again
            candidates = candidates(level);
        }
        int realWorkers = Math.max(0, workers - inside);
        int realCount = inside <= 0 ? count : Math.round((float) count * realWorkers / workers);
        if (realCount <= 0 && !candidates.isEmpty() && count > 0) {
            realCount = 1;
        }
        realCount = Math.min(realCount, candidates.size());
        int numberCount = Math.min(count - realCount, inside);
        realCount = Math.min(count - numberCount, candidates.size());
        int index = 0;
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < realCount; i++, index++) {
            Job job = jobFor(index, hillOnly);
            real.put(candidates.get(i).getUUID(), job);
            seen.put(candidates.get(i).getUUID(), now);
            waitingToStart.put(candidates.get(i).getUUID(), now);
            if (job.way() >= 0) {
                ways.get(job.way()).sent++;
            }
        }
        for (int i = 0; i < numberCount; i++, index++) {
            Job job = jobFor(index, hillOnly);
            int blocks = (int) Math.round(2 * Math.PI * HILL_ROUND + (job.hillOnly() ? 0 : 2.0 * Math.max(0, job.reach() - HILL_ROUND)));
            long walk = TRIP_TICKS + (long) blocks * FieldLife.TICKS_PER_BLOCK;
            numbers.add(new Out(job.way(), now + Math.round(walk * (0.75 + 0.5 * colony.random().nextDouble()))));
            if (job.way() >= 0) {
                ways.get(job.way()).sent++;
            }
        }
        int total = realCount + numberCount;
        if (total <= 0) {
            return; // nobody to send yet (all busy, or none loaded): tried again next second
        }
        sent += total;
        colony.away().note(level, AwayReport.What.PATROLS, 1);
        for (Way way : ways) {
            where.append(where.isEmpty() ? "" : ", ").append(TrunkTrails.compass(way.angle)).append(way.trail ? " (trail)" : "");
        }
        AntColoniesMod.LOGGER.info("[patrol] colony {}: {} patrollers out at {} ({} real, {} numbers){}", colony.getId(), total,
                SpeciesProfile.DayClock.clock(colony.timeOfDay(level)), realCount, numberCount,
                hillOnly ? " to patrol the hill after danger" : " toward " + (where.isEmpty() ? "the hill only" : where));
        ColonyManager.get(level).markChanged();
    }

    /** The job of the {@code index}th patroller: the ways in turn (every way gets one before any gets two). */
    private Job jobFor(int index, boolean hillOnly) {
        if (hillOnly || ways.isEmpty()) {
            return new Job(-1, 0, HILL_ROUND);
        }
        int way = index % ways.size();
        return new Job(way, ways.get(way).angle, ways.get(way).reach);
    }

    /** The real workers of the colony that may patrol now: grown up, holding nothing, by the nest; builders first. */
    private List<AbstractAntEntity> candidates(ServerLevel level) {
        BlockPos center = colony.getCenter();
        int radius = Math.max(24, colony.profile().food().radius()); // (by the nest: as before a young colony's reach became 12)
        List<AbstractAntEntity> ants = new ArrayList<>(level.getEntitiesOfClass(AbstractAntEntity.class,
                new AABB(center).inflate(radius, 16, radius), ant -> ant.isAlive() && ant.isColonyWorker()
                        && colony.getId().equals(ant.getColonyId()) && !ant.hasFullMandibles() && !real.containsKey(ant.getUUID())
                        && !passedOver.contains(ant.getUUID())));
        Map<UUID, Integer> order = new HashMap<>();
        for (AbstractAntEntity ant : ants) {
            ColonyRole role = colony.roleOf(level, ant);
            order.put(ant.getUUID(), role == ColonyRole.BUILDER ? 0 : role == ColonyRole.FORAGER ? 1 : 2);
        }
        ants.sort(Comparator.comparingInt((AbstractAntEntity ant) -> order.get(ant.getUUID()))
                .thenComparingDouble(ant -> ant.blockPosition().distSqr(center)));
        return ants;
    }

    /**
     * The ways this round checks, at most one per patroller and one per compass point: the trunk trails, strongest
     * first, then new directions, where the colony knows the most food first. Ways shut for today are left out.
     */
    private void chooseWays(ServerLevel level, int patrollers) {
        int wanted = Math.min(MOST_WAYS, patrollers);
        int reach = reach();
        double spread = spread();
        List<TrunkTrails.Trail> trails = new ArrayList<>(colony.trunkTrails().trails());
        trails.sort(Comparator.comparingDouble((TrunkTrails.Trail trail) -> -colony.trunkTrails().strength(level, trail)));
        for (TrunkTrails.Trail trail : trails) {
            if (ways.size() >= wanted) {
                break;
            }
            if (!isShutToday(trail.angle(), spread)) {
                ways.add(new Way(trail.angle(), true, reach));
            }
        }
        if (ways.size() >= wanted) {
            return;
        }
        // New directions: the eight compass points not near a way already, the most food that way first.
        int[] food = new int[MOST_WAYS];
        for (BlockPos plant : colony.food().plants(level)) {
            if (flat(plant) > HILL_ZONE) {
                food[Math.floorMod((int) Math.round(angleOf(plant) / (Math.PI / 4)), MOST_WAYS)]++;
            }
        }
        List<Integer> points = new ArrayList<>();
        for (int i = 0; i < MOST_WAYS; i++) {
            points.add(i);
        }
        java.util.Collections.shuffle(points, new java.util.Random(colony.random().nextLong()));
        points.sort(Comparator.comparingInt((Integer i) -> -food[i]));
        for (int point : points) {
            if (ways.size() >= wanted) {
                break;
            }
            double angle = point * Math.PI / 4;
            boolean near = isShutToday(angle, spread);
            for (Way way : ways) {
                near |= TrunkTrails.difference(way.angle, angle) <= spread;
            }
            if (!near) {
                ways.add(new Way(angle, false, reach));
            }
        }
    }

    private boolean isShutToday(double angle, double spread) {
        for (ShutWay way : shutToday) {
            if (TrunkTrails.difference(way.angle(), angle) <= spread) {
                return true;
            }
        }
        return false;
    }

    /** A patroller of this way (-1: the hill) is back safely. */
    private void cameBack(ServerLevel level, int way) {
        if (way == Integer.MIN_VALUE) {
            return; // it turned back from trouble
        }
        if (way >= 0 && way < ways.size()) {
            ways.get(way).back++;
        }
        if (!anyBack) {
            AntColoniesMod.LOGGER.info("[patrol] colony {}: the first patroller is back at {}: foraging opens", colony.getId(),
                    SpeciesProfile.DayClock.clock(colony.timeOfDay(level)));
        }
        anyBack = true;
        openedToday = true;
        back++;
        ColonyManager.get(level).markChanged();
    }

    /** How far out (blocks from the nest's middle) a patroller goes along its way. */
    private int reach() {
        SpeciesProfile.PatrolNumbers numbers = numbers();
        int reach = numbers == null ? 10 : numbers.reach();
        return Math.max(HILL_ROUND + 1, Math.min(reach, colony.profile().food().radius()));
    }

    /** How far (radians) either side of a way's line counts as that way: the trunk trails' spread. */
    private double spread() {
        return Math.toRadians(colony.profile().food().trails().trunk().map(trunk -> (double) trunk.spreadDegrees()).orElse(35.0));
    }

    private double angleOf(BlockPos pos) {
        return Math.atan2(pos.getZ() - colony.getCenter().getZ(), pos.getX() - colony.getCenter().getX());
    }

    private double flat(BlockPos pos) {
        double dx = pos.getX() - colony.getCenter().getX();
        double dz = pos.getZ() - colony.getCenter().getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // --- the colony list and tests ----------------------------------------------------------------------------------

    /** For the colony list: " | patrol: 8 out at 6:00, 7 back, 1 lost; open: north, east; shut today: west (danger)", or empty. */
    public String describe(Level level) {
        if (!isOn(level)) {
            return "";
        }
        if (!roundOn) {
            return " | patrol: none out (the foragers' clock is shut)" + shutTodayText();
        }
        StringBuilder line = new StringBuilder(" | patrol: ").append(sent).append(" out at ")
                .append(SpeciesProfile.DayClock.clock(roundClock)).append(", ").append(back).append(" back");
        if (lost > 0) {
            line.append(", ").append(lost).append(" lost");
        }
        List<String> open = new ArrayList<>();
        for (Way way : ways) {
            if (way.back > 0 && way.shut == null) {
                open.add(TrunkTrails.compass(way.angle));
            }
        }
        line.append(anyBack ? "; open: " + (open.isEmpty() ? "round the hill only" : String.join(", ", open)) : "; no foraging until one is back");
        if (dangerToday) {
            line.append("; danger today, fewer foragers");
        }
        return line + shutTodayText();
    }

    private String shutTodayText() {
        if (shutToday.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ShutWay way : shutToday) {
            parts.add(TrunkTrails.compass(way.angle()) + " (" + way.why().label + ")");
        }
        return "; shut today: " + String.join(", ", parts);
    }

    /** Whether a round is on (for tests). */
    public boolean isRoundOn() {
        return roundOn;
    }

    /** How many patrollers went out this round, real and numbers (for tests). */
    public int sentThisRound() {
        return sent;
    }

    /** How many patrollers are back safely this round (for tests). */
    public int backThisRound() {
        return back;
    }

    /** How many rounds there have been today (for tests). */
    public int roundsToday() {
        return roundsToday;
    }

    /** The real patrollers out now (for tests). */
    public java.util.Set<UUID> realOut() {
        return java.util.Set.copyOf(real.keySet());
    }

    /** How many number patrollers are out now (for tests). */
    public int numbersOut() {
        return numbers.size();
    }

    /** Whether a patroller met danger today (for tests). */
    public boolean dangerToday() {
        return dangerToday;
    }

    /** The ways this round checks, as compass points, "(trail)" after a trunk trail's, in order (for tests). */
    public List<String> waysForTests() {
        List<String> names = new ArrayList<>();
        for (Way way : ways) {
            names.add(TrunkTrails.compass(way.angle) + (way.trail ? " (trail)" : ""));
        }
        return names;
    }

    /** A patroller of the way nearest this angle is killed, by the same rule as a real one (for tests). */
    public void patrollerKilledForTests(ServerLevel level, double angle) {
        int best = -1;
        for (int i = 0; i < ways.size(); i++) {
            if (best < 0 || TrunkTrails.difference(ways.get(i).angle, angle) < TrunkTrails.difference(ways.get(best).angle, angle)) {
                best = i;
            }
        }
        if (best < 0) {
            return;
        }
        UUID fake = UUID.randomUUID();
        real.put(fake, new Job(best, ways.get(best).angle, ways.get(best).reach));
        died(level, fake);
    }

    /** Every number patroller out is back on the next second (for tests). */
    public void bringNumbersHomeForTests() {
        numbers.replaceAll(out -> new Out(out.way(), 0));
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        all.putBoolean("roundOn", roundOn);
        all.putBoolean("anyBack", anyBack);
        all.putBoolean("reinforced", reinforced);
        all.putLong("roundStart", roundStart);
        all.putLong("roundClock", roundClock);
        all.putInt("sent", sent);
        all.putInt("back", back);
        all.putInt("lost", lost);
        all.putLong("day", day);
        all.putInt("roundsToday", roundsToday);
        all.putBoolean("openedToday", openedToday);
        all.putBoolean("dangerToday", dangerToday);
        all.putBoolean("clockWasOpen", clockWasOpen);
        ListTag wayList = new ListTag();
        for (Way way : ways) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("angle", way.angle);
            entry.putBoolean("trail", way.trail);
            entry.putInt("reach", way.reach);
            entry.putInt("sent", way.sent);
            entry.putInt("back", way.back);
            if (way.shut != null) {
                entry.putString("shut", way.shut.name());
            }
            wayList.add(entry);
        }
        all.put("ways", wayList);
        ListTag realList = new ListTag();
        real.forEach((id, job) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putInt("way", job.way());
            entry.putDouble("angle", job.angle());
            entry.putInt("reach", job.reach());
            realList.add(entry);
        });
        all.put("real", realList);
        ListTag numberList = new ListTag();
        for (Out out : numbers) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("way", out.way());
            entry.putLong("at", out.backAt());
            numberList.add(entry);
        }
        all.put("numbers", numberList);
        ListTag shutList = new ListTag();
        for (ShutWay way : shutToday) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("angle", way.angle());
            entry.putString("why", way.why().name());
            shutList.add(entry);
        }
        all.put("shutToday", shutList);
        tag.put("patrol", all);
    }

    void load(CompoundTag tag) {
        ways.clear();
        real.clear();
        seen.clear();
        numbers.clear();
        shutToday.clear();
        if (!tag.contains("patrol")) {
            return;
        }
        CompoundTag all = tag.getCompound("patrol");
        roundOn = all.getBoolean("roundOn");
        anyBack = all.getBoolean("anyBack");
        reinforced = all.getBoolean("reinforced");
        roundStart = all.getLong("roundStart");
        roundClock = all.getLong("roundClock");
        sent = all.getInt("sent");
        back = all.getInt("back");
        lost = all.getInt("lost");
        day = all.getLong("day");
        roundsToday = all.getInt("roundsToday");
        openedToday = all.getBoolean("openedToday");
        dangerToday = all.getBoolean("dangerToday");
        clockWasOpen = all.getBoolean("clockWasOpen");
        for (Tag t : all.getList("ways", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Way way = new Way(entry.getDouble("angle"), entry.getBoolean("trail"), entry.getInt("reach"));
            way.sent = entry.getInt("sent");
            way.back = entry.getInt("back");
            if (entry.contains("shut")) {
                way.shut = shutNamed(entry.getString("shut"));
            }
            ways.add(way);
        }
        for (Tag t : all.getList("real", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            real.put(entry.getUUID("id"), new Job(entry.getInt("way"), entry.getDouble("angle"), entry.getInt("reach")));
        }
        for (Tag t : all.getList("numbers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            numbers.add(new Out(entry.getInt("way"), entry.getLong("at")));
        }
        for (Tag t : all.getList("shutToday", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            shutToday.add(new ShutWay(entry.getDouble("angle"), shutNamed(entry.getString("why"))));
        }
    }

    private static Shut shutNamed(String name) {
        try {
            return Shut.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Shut.DANGER;
        }
    }
}
