package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.species.Species;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The brain, part 1: most of the colony is numbers. The workers and soldiers that are not real mobs are kept here as
 * counts, by caste and by when they grew up (their age, which sets a worker's job and when it dies of old age). They are
 * inside the nest, out of sight; the headcount ({@link Colony#getCount}) is the real mobs and these together.
 *
 * <p>Once a second the colony decides how many of them should be real ({@link #targets}): while a player is near the
 * nest (the profile's {@code watch_radius}) up to {@code real_cap} real mobs (soldiers as many as have a post to guard,
 * the rest workers); with nobody near, a crew of {@code crew} workers keeps working. The user's decision (2026-10-14):
 * the crew is the whole real cap, so a loaded colony works just as before the brain whether a player is near or not
 * (soldiers keep their posts too); a smaller crew in the species file would save work on the server. Then a few
 * a second ({@code swaps_per_second}) come out of the numbers, in a room of the nest no player can see, or go back into
 * them: only one that holds nothing, is after no enemy, is out of every player's sight and is inside the nest or far from
 * every player. So nothing pops in or out in front of the player.
 *
 * <p>Inside, the numbers grow old and die (workers, at the profile's lifespan; an ant's body is laid in the refuse
 * chamber), and a young one hatches straight into them when no more real mobs are wanted and nobody can see it. They
 * eat by the same rules as the real ones, whether a player is near or not (the user's decision, part 2 step 2b; see
 * {@link Meals}), and starve without food.
 *
 * <p>Species-neutral: any caste a mob names in {@link AbstractAntEntity#nestCaste} can go into the numbers.
 */
public class Population {
    /** Ages are kept to the minute: a real mob going in is put with the others that grew up in the same minute. */
    static final long AGE_STEP = 1200;
    /** How far (blocks) from every player a real mob outside the nest must be to go into the numbers while watched. */
    private static final double FAR_FROM_PLAYERS = 24.0;
    /** How far (blocks) a player sees an insect at all (farther than this counts as out of sight). */
    public static final double SIGHT = 64.0;
    /** How many more minors come out while the alarm is up at a break-in (level 3 or more), beyond the ones with posts. */
    private static final int BREACH_MINORS = 6;

    private final Colony colony;
    /** Caste -> (the minute they grew up -> how many). Saved. */
    private final Map<String, TreeMap<Long, Integer>> inside = new LinkedHashMap<>();
    /** When the numbers last ate. Saved. */
    private final Meals meals;
    /** Tests only: where players' eyes are (null: the real players). Not saved. */
    @Nullable
    private List<Vec3> viewersForTests;
    /** Tests only: whether the colony swaps real mobs and numbers (null: always, except on the game test server). */
    @Nullable
    private Boolean onForTests;
    /** Tests only: the crew while nobody is near (null: the profile's). Not saved. */
    @Nullable
    private Integer crewForTests;
    /** From the last second: how many real mobs of each caste there were and how many were wanted. Not saved. */
    private final Map<String, Integer> lastReal = new HashMap<>();
    private final Map<String, Integer> lastWanted = new HashMap<>();
    /** The stress test's real cap (null: none running). Not saved. */
    @Nullable
    private Integer stressCap;
    /** How many a second come out during a stress test. */
    private static final int STRESS_SWAPS = 10;

    Population(Colony colony) {
        this.colony = colony;
        this.meals = new Meals(colony);
    }

    private SpeciesProfile.ColonyNumbers numbers() {
        return colony.profile().colony();
    }

    /** How many real workers keep working while nobody is near. */
    private int crew() {
        if (stressCap != null) {
            return stressCap;
        }
        return crewForTests != null ? crewForTests : numbers().crew();
    }

    /** The most real mobs at once (the stress test's number while it runs). */
    private int realCap() {
        return stressCap != null ? stressCap : numbers().realCap();
    }

    /** How many come out of the numbers or go back in each second (faster during a stress test, to fill up quickly). */
    private int swapsPerSecond() {
        return stressCap != null ? Math.max(numbers().swapsPerSecond(), STRESS_SWAPS) : numbers().swapsPerSecond();
    }

    /**
     * The stress test (performance step 1, {@code /antcolonies stress}): this many real mobs at once instead of the
     * profile's cap, whether a player is near or not; null ends it. Not saved: a restart ends it too.
     */
    public void setStress(@Nullable Integer cap) {
        stressCap = cap;
    }

    @Nullable
    public Integer getStress() {
        return stressCap;
    }

    // --- the numbers --------------------------------------------------------------------------------------------

    /** How many of this caste are in the nest as numbers. */
    public int inside(String caste) {
        TreeMap<Long, Integer> ages = inside.get(caste);
        return ages == null ? 0 : ages.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** The castes that have numbers in the nest (some may be 0). */
    java.util.Set<String> castesInside() {
        return inside.keySet();
    }

    /** How many of all castes are in the nest as numbers. */
    public int insideTotal() {
        int total = 0;
        for (String caste : inside.keySet()) {
            total += inside(caste);
        }
        return total;
    }

    private void putInside(String caste, long grownUpAt, int count) {
        if (count <= 0) {
            return;
        }
        long minute = Math.floorDiv(grownUpAt, AGE_STEP) * AGE_STEP;
        inside.computeIfAbsent(caste, c -> new TreeMap<>()).merge(minute, count, Integer::sum);
    }

    private void takeInside(String caste, long minute, int count) {
        TreeMap<Long, Integer> ages = inside.get(caste);
        if (ages == null) {
            return;
        }
        int left = ages.getOrDefault(minute, 0) - count;
        if (left > 0) {
            ages.put(minute, left);
        } else {
            ages.remove(minute);
        }
    }

    /**
     * New members that are numbers from the start: they join the headcount and are inside (a young one hatching when no
     * more real mobs are wanted, or the {@code /antcolonies population} command).
     */
    public void addNewMembers(String caste, long grownUpAt, int count) {
        colony.setCount(caste, colony.getCount(caste) + count);
        putInside(caste, grownUpAt, count);
    }

    /**
     * A real mob frozen in unloaded land is folded into the numbers (see {@link AwayLife}): it is counted already, so
     * only where it is changes, not the headcount.
     */
    void foldIn(ServerLevel level, String caste, long grownUpAt, long lastMeal) {
        putInside(caste, grownUpAt, 1);
        meals.add(caste, lastMeal, level.getGameTime());
    }

    /** How many of this caste's numbers are of each job by age (young, middle-aged, old), for the colony list. */
    int[] insideByAge(long now) {
        int[] ages = new int[ColonyRole.values().length];
        TreeMap<Long, Integer> workers = inside.get(WorkerAntEntity.CASTE);
        if (workers != null) {
            workers.forEach((minute, count) -> ages[colony.profile().worker().roleForAge(now - minute).ordinal()] += count);
        }
        return ages;
    }

    // --- once a second ------------------------------------------------------------------------------------------

    /** Once a second (from {@link Colony#tick}): the numbers grow old and eat, and real mobs and numbers are swapped. */
    void tick(ServerLevel level) {
        growOld(level);
        meals.tick(level, this);
        if (isOn(level)) {
            direct(level);
        }
    }

    /** Once a second while the colony is away (from {@link AwayLife#tickAway}): the numbers grow old and eat. */
    void tickAway(ServerLevel level) {
        growOld(level);
        meals.tick(level, this);
    }

    /** Whether this colony swaps real mobs and numbers (always in a game; the game tests turn it on where they test it). */
    public boolean isOn(Level level) {
        if (onForTests != null) {
            return onForTests;
        }
        return !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
    }

    /**
     * Workers in the numbers die of old age like the real ones (the profile's {@code lifespan_ticks}, 10% more or less
     * for each): the ones of each minute die off one by one between 90% and 110% of the lifespan. A dead ant is laid in
     * the refuse chamber if there is room (its nestmates carried it there); termites eat their dead in the garden.
     */
    void growOld(ServerLevel level) {
        TreeMap<Long, Integer> workers = inside.get(WorkerAntEntity.CASTE);
        long lifespan = colony.profile().worker().lifespanTicks();
        if (workers == null || lifespan <= 0) {
            return;
        }
        long now = level.getGameTime();
        double from = lifespan * 0.9;
        double until = lifespan * 1.1;
        int died = 0;
        for (Map.Entry<Long, Integer> entry : new ArrayList<>(workers.entrySet())) {
            long age = now - entry.getKey();
            if (age < from) {
                break; // the rest grew up later
            }
            int count = entry.getValue();
            int deaths;
            if (age >= until) {
                deaths = count;
            } else {
                double expected = count * 20.0 / (until - age);
                deaths = (int) expected + (colony.random().nextDouble() < expected - (int) expected ? 1 : 0);
                deaths = Math.min(count, deaths);
            }
            if (deaths > 0) {
                takeInside(WorkerAntEntity.CASTE, entry.getKey(), deaths);
                died += deaths;
            }
        }
        if (died == 0) {
            return;
        }
        colony.setCount(WorkerAntEntity.CASTE, colony.getCount(WorkerAntEntity.CASTE) - died);
        colony.away().note(level, AwayReport.What.OLD_AGE, died);
        buried(level, died);
    }

    /**
     * This many worker numbers have starved (see {@link Meals}): they die, any age, and leave their bodies. The meal
     * list has taken them off already.
     */
    void starved(ServerLevel level, String caste, int count) {
        TreeMap<Long, Integer> ages = inside.get(caste);
        int died = 0;
        while (ages != null && !ages.isEmpty() && died < count) {
            int total = ages.values().stream().mapToInt(Integer::intValue).sum();
            int pick = colony.random().nextInt(total);
            for (Map.Entry<Long, Integer> entry : ages.entrySet()) {
                pick -= entry.getValue();
                if (pick < 0) {
                    takeInside(caste, entry.getKey(), 1);
                    break;
                }
            }
            died++;
        }
        if (died == 0) {
            return;
        }
        colony.setCount(caste, colony.getCount(caste) - died);
        colony.away().note(level, AwayReport.What.STARVED, died);
        com.antcolonies.AntColoniesMod.LOGGER.info("[meals] colony {}: {} {} numbers starved", colony.getId(), died, caste);
        if (WorkerAntEntity.CASTE.equals(caste)) {
            buried(level, died);
        }
    }

    /**
     * One worker number was lost out of sight in danger (brain step 4g, step 4): it dies, any age, and its body lies where
     * it fell (not carried home). False if there was none inside to lose.
     */
    boolean lostOutside(ServerLevel level) {
        String caste = WorkerAntEntity.CASTE;
        TreeMap<Long, Integer> ages = inside.get(caste);
        if (ages == null || ages.isEmpty()) {
            return false;
        }
        int pick = colony.random().nextInt(Math.max(1, ages.values().stream().mapToInt(Integer::intValue).sum()));
        for (Map.Entry<Long, Integer> entry : ages.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                takeInside(caste, entry.getKey(), 1);
                break;
            }
        }
        colony.setCount(caste, colony.getCount(caste) - 1);
        colony.away().note(level, AwayReport.What.LOST_IN_DANGER, 1);
        ColonyManager.get(level).markChanged();
        return true;
    }

    /**
     * Bodies of numbers that died in the nest: laid in the refuse chamber (termites eat theirs), out of sight, now or
     * when the nest is loaded again (never touch unloaded land; see {@link NestLedger}).
     */
    private void buried(ServerLevel level, int died) {
        if (!colony.away().isHere(level)) {
            colony.ledger().addBodies(died);
        } else if (!colony.refuse().eatsOrBuries()) {
            for (int i = 0; i < died; i++) {
                BlockPos spot = colony.refuse().pickCorpseSpot(level, colony.random());
                if (spot == null || !colony.refuse().placeCorpse(level, spot)) {
                    break;
                }
            }
        }
        ColonyManager.get(level).markChanged();
    }

    /**
     * How many real mobs of each caste the colony wants now. Watched (a player near): soldiers as many as there are posts
     * for them (a few more minors at a break-in), up to a quarter of the real cap; the rest of the cap workers. Not
     * watched: the crew of workers, and soldiers only while the alarm is up (2 or more: an enemy at the foragers); a crew
     * as big as the cap is the same as watched.
     */
    Map<String, Integer> targets(ServerLevel level, boolean watched) {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        int cap = realCap();
        watched = watched || crew() >= cap;
        int soldiers = 0;
        if (colony.soldiers().numbers() != null) {
            int alarm = colony.alarm().level(level);
            int soldierCap = cap / 4;
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                int posts = colony.soldiers().posts(level, rank).size();
                if (rank == TermiteSoldierEntity.Rank.MINOR && alarm >= 3) {
                    posts += BREACH_MINORS;
                }
                // (no more than the colony has, so the workers get the rest of the cap)
                int want = watched || alarm >= 2 ? Math.min(Math.min(posts, colony.getCount(rank.caste())), soldierCap - soldiers) : 0;
                wanted.put(rank.caste(), Math.max(0, want));
                soldiers += Math.max(0, want);
            }
        }
        wanted.put(WorkerAntEntity.CASTE, watched ? Math.max(0, cap - soldiers) : Math.min(crew(), cap));
        return wanted;
    }

    /** The real mobs of this colony that may go into the numbers, around the nest (loaded and alive). */
    private List<AbstractAntEntity> realMembers(ServerLevel level) {
        int radius = colony.foragingRadius() + 16;
        AABB area = new AABB(colony.getCenter()).inflate(radius, 32, radius);
        return level.getEntitiesOfClass(AbstractAntEntity.class, area,
                mob -> mob.isAlive() && colony.getId().equals(mob.getColonyId()) && mob.nestCaste() != null);
    }

    /** Where the players near the nest (within the profile's {@code watch_radius}) have their eyes (none: nobody is watching). */
    List<Vec3> viewers(ServerLevel level) {
        return viewers(level, numbers().watchRadius());
    }

    /**
     * Where the eyes are of every player within {@code radius} blocks of the nest (sideways). For what a player could
     * see this is the watch radius and the sight together, so one standing just outside the watch radius does not see an
     * insect pop in or out either.
     */
    private List<Vec3> viewers(ServerLevel level, int radius) {
        if (viewersForTests != null) {
            return viewersForTests;
        }
        BlockPos center = colony.getCenter();
        List<Vec3> eyes = new ArrayList<>();
        for (Player player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double dx = player.getX() - (center.getX() + 0.5);
            double dz = player.getZ() - (center.getZ() + 0.5);
            if (dx * dx + dz * dz <= (double) radius * radius && Math.abs(player.getY() - center.getY()) <= 64) {
                eyes.add(player.getEyePosition());
            }
        }
        return eyes;
    }

    /** Where the eyes are of every player who could see into the nest (the watch radius and the sight together). */
    List<Vec3> viewersInSight(ServerLevel level) {
        return viewers(level, numbers().watchRadius() + (int) SIGHT);
    }

    /** Whether no player can see this point: too far, or a block is in the way. */
    public static boolean outOfSight(Level level, Vec3 point, List<Vec3> eyes) {
        for (Vec3 eye : eyes) {
            if (eye.distanceToSqr(point) > SIGHT * SIGHT) {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(point) < 0.5 * 0.5) {
                return false; // nothing in the way (or only the block it stands against)
            }
        }
        return true;
    }

    /**
     * Whether no player can see the block at {@code pos} (loaded): too far, or another block is in the way. Unlike the
     * point above, a solid block (a hay bale, a log) that the look runs into is seen.
     */
    public static boolean outOfSight(Level level, BlockPos pos, List<Vec3> eyes) {
        Vec3 point = Vec3.atCenterOf(pos);
        for (Vec3 eye : eyes) {
            if (eye.distanceToSqr(point) > SIGHT * SIGHT) {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(eye, point, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK || ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos().equals(pos)
                    || hit.getLocation().distanceToSqr(point) < 0.5 * 0.5) {
                return false;
            }
        }
        return true;
    }

    private void direct(ServerLevel level) {
        List<Vec3> eyes = viewers(level, numbers().watchRadius() + (int) SIGHT);
        Map<String, Integer> wanted = targets(level, !viewers(level).isEmpty());
        List<AbstractAntEntity> real = realMembers(level);
        Map<String, List<AbstractAntEntity>> byCaste = new HashMap<>();
        for (AbstractAntEntity mob : real) {
            byCaste.computeIfAbsent(mob.nestCaste(), c -> new ArrayList<>()).add(mob);
        }
        lastWanted.clear();
        lastWanted.putAll(wanted);
        lastReal.clear();
        byCaste.forEach((caste, mobs) -> lastReal.put(caste, mobs.size()));
        int swaps = swapsPerSecond();
        List<BlockPos> rooms = null;
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : wanted.entrySet()) {
            String caste = entry.getKey();
            List<AbstractAntEntity> mobs = byCaste.getOrDefault(caste, List.of());
            // The ones travelling unseen along the galleries are real ones too, out of the world for a while (HiddenTrips).
            int have = mobs.size() + colony.hiddenTrips().travelling(caste);
            int want = entry.getValue();
            while (have < want && swaps > 0 && inside(caste) > 0) {
                if (rooms == null) {
                    rooms = hiddenRoomCells(level, eyes);
                }
                if (rooms.isEmpty() || !comeOut(level, caste, rooms.get(colony.random().nextInt(rooms.size())))) {
                    break;
                }
                have++;
                swaps--;
                changed = true;
            }
            if (have > want && swaps > 0) {
                for (AbstractAntEntity mob : goingIn(level, mobs, eyes)) {
                    if (have <= want || swaps <= 0) {
                        break;
                    }
                    goIn(level, mob);
                    have--;
                    swaps--;
                    changed = true;
                }
            }
            lastReal.put(caste, have);
        }
        if (changed) {
            ColonyManager.get(level).markChanged();
        }
    }

    /**
     * The real mobs of one caste that may go into the numbers now, the best first: inside the nest before outside,
     * farthest from the players first. Each must hold nothing, be after no enemy, be out of every player's sight and,
     * outside the nest, far from every player.
     */
    private List<AbstractAntEntity> goingIn(ServerLevel level, List<AbstractAntEntity> mobs, List<Vec3> eyes) {
        List<AbstractAntEntity> may = new ArrayList<>();
        Map<AbstractAntEntity, Boolean> in = new HashMap<>();
        Map<AbstractAntEntity, Double> distance = new HashMap<>();
        for (AbstractAntEntity mob : mobs) {
            if (!mob.mayGoIntoNest()) {
                continue;
            }
            boolean insideNest = colony.nest().isInside(level, mob.blockPosition());
            double nearest = Double.MAX_VALUE;
            for (Vec3 eye : eyes) {
                nearest = Math.min(nearest, eye.distanceTo(mob.position()));
            }
            if (!insideNest && nearest < FAR_FROM_PLAYERS) {
                continue;
            }
            if (!outOfSight(level, mob.getEyePosition(), eyes) || !outOfSight(level, mob.position().add(0, 0.05, 0), eyes)) {
                continue;
            }
            may.add(mob);
            in.put(mob, insideNest);
            distance.put(mob, nearest);
        }
        may.sort(Comparator.comparing((AbstractAntEntity mob) -> !in.get(mob)).thenComparing(mob -> -distance.get(mob)));
        return may;
    }

    /** A real mob goes into the numbers: it is gone from the world (no death, no body) and counted inside, at its age. */
    private void goIn(ServerLevel level, AbstractAntEntity mob) {
        long grownUpAt = mob.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET ? level.getGameTime() : mob.getGrownUpAt();
        putInside(mob.nestCaste(), grownUpAt, 1);
        meals.add(mob.nestCaste(), mob.getLastMeal(), level.getGameTime());
        colony.foodMap().wentHome(mob.getUUID());
        colony.spots().intoNumbers(mob.getUUID()); // its spot goes into the numbers' pool (site fidelity)
        mob.discard();
    }

    /**
     * The floor cells of the nest's rooms where one can come out unseen: dug out, a floor to stand on, out of every
     * player's sight. The shaft if no room is dug yet.
     */
    private List<BlockPos> hiddenRoomCells(ServerLevel level, List<Vec3> eyes) {
        List<BlockPos> cells = new ArrayList<>(colony.nest().getAllRoomCells(level));
        if (cells.isEmpty()) {
            BlockPos bottom = colony.nest().getShaftBottom(level);
            if (bottom != null) {
                for (BlockPos cell = bottom; cell.getY() < colony.getCenter().getY(); cell = cell.above()) {
                    cells.add(cell);
                }
            }
        }
        cells.removeIf(cell -> !Nest.isDugOut(level, cell) || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)
                || !outOfSight(level, Vec3.atBottomCenterOf(cell).add(0, 0.2, 0), eyes));
        return cells;
    }

    /** One of this caste comes out of the numbers as a real mob in this cell. False if it could not be made. */
    private boolean comeOut(ServerLevel level, String caste, BlockPos cell) {
        Long minute = pickAge(level, caste);
        EntityType<? extends AbstractAntEntity> type = typeFor(caste);
        if (minute == null || type == null) {
            return false;
        }
        AbstractAntEntity mob = type.create(level);
        if (mob == null) {
            return false;
        }
        mob.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, colony.random().nextFloat() * 360.0F, 0.0F);
        // Somewhere in the minute it grew up, so the ones of the same minute are not all exactly the same age.
        mob.comeOutOfNest(colony, minute + colony.random().nextInt((int) AGE_STEP));
        if (meals.dietOf(caste) != null) {
            mob.setLastMeal(Math.min(level.getGameTime(), meals.take(caste, colony.random(), level.getGameTime())));
        }
        takeInside(caste, minute, 1);
        level.addFreshEntity(mob);
        if (WorkerAntEntity.CASTE.equals(caste) && mob.mayDo(ColonyRole.FORAGER)) {
            colony.spots().outOfNumbers(mob.getUUID()); // a forager takes a spot from the numbers' pool
        }
        return true;

    }

    /**
     * The minute the one coming out grew up: picked at random, more likely where there are more, but not one about to
     * die of old age (a real mob gets its own lifespan, which might already be over) unless only those are left.
     */
    @Nullable
    private Long pickAge(ServerLevel level, String caste) {
        TreeMap<Long, Integer> ages = inside.get(caste);
        if (ages == null || ages.isEmpty()) {
            return null;
        }
        long lifespan = WorkerAntEntity.CASTE.equals(caste) ? colony.profile().worker().lifespanTicks() : 0;
        long now = level.getGameTime();
        Map<Long, Integer> from = ages;
        if (lifespan > 0) {
            Map<Long, Integer> young = new LinkedHashMap<>();
            ages.forEach((minute, count) -> {
                if (now - minute < lifespan * 0.85) {
                    young.put(minute, count);
                }
            });
            if (!young.isEmpty()) {
                from = young;
            }
        }
        int total = from.values().stream().mapToInt(Integer::intValue).sum();
        int pick = colony.random().nextInt(total);
        for (Map.Entry<Long, Integer> entry : from.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return ages.firstKey();
    }

    /** The mob a caste of this colony's species is. */
    @Nullable
    private EntityType<? extends AbstractAntEntity> typeFor(String caste) {
        boolean termite = colony.getSpecies().equals(Species.MACROTERMES);
        if (WorkerAntEntity.CASTE.equals(caste)) {
            return termite ? ModEntities.TERMITE_WORKER.get() : ModEntities.WORKER_ANT.get();
        }
        if (TermiteSoldierEntity.Rank.MINOR.caste().equals(caste)) {
            return ModEntities.TERMITE_MINOR_SOLDIER.get();
        }
        if (TermiteSoldierEntity.Rank.MAJOR.caste().equals(caste)) {
            return ModEntities.TERMITE_MAJOR_SOLDIER.get();
        }
        return null;
    }

    /**
     * Whether a young one of this caste grown up at this spot should go straight into the numbers instead of coming out
     * as a real mob: the colony has all the real mobs of this caste it wants, and no player can see the spot.
     */
    public boolean hatchesIntoNumbers(ServerLevel level, String caste, Vec3 at) {
        if (!isOn(level)) {
            return false;
        }
        return lastReal.getOrDefault(caste, 0) >= lastWanted.getOrDefault(caste, Integer.MAX_VALUE)
                && outOfSight(level, at, viewers(level, numbers().watchRadius() + (int) SIGHT));
    }

    // --- the colony list ----------------------------------------------------------------------------------------

    /** For the colony list: " | real 12 (wants 50), in the nest 340 (young 80, middle 200, old 60)". */
    public String describe(ServerLevel level) {
        int real = colony.getMemberCount() - colony.getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) - insideTotal();
        int wanted = lastWanted.values().stream().mapToInt(Integer::intValue).sum();
        int[] ages = insideByAge(level.getGameTime());
        int hungry = meals.hungry(level.getGameTime());
        return " | real " + real + (isOn(level) ? " (wants " + wanted + (viewers(level).isEmpty() && crew() < numbers().realCap() ? ", nobody near: the crew" : "") + ")" : "")
                + ", in the nest " + insideTotal() + " (workers young " + ages[ColonyRole.NURSE.ordinal()] + ", middle "
                + ages[ColonyRole.BUILDER.ordinal()] + ", old " + ages[ColonyRole.FORAGER.ordinal()] + soldiersInside()
                + (hungry > 0 ? "; hungry " + hungry : "") + ")";
    }

    private String soldiersInside() {
        if (colony.soldiers().numbers() == null) {
            return "";
        }
        return "; soldiers " + inside(TermiteSoldierEntity.Rank.MINOR.caste()) + " minor, " + inside(TermiteSoldierEntity.Rank.MAJOR.caste()) + " major";
    }

    // --- tests ----------------------------------------------------------------------------------------------------

    /** Tests only: pretend players' eyes are here (null: the real players). */
    public void setViewersForTests(@Nullable List<Vec3> eyes) {
        viewersForTests = eyes;
    }

    /** Tests only: swap real mobs and numbers or not (null: as in a game). */
    public void setCrewForTests(@Nullable Integer crew) {
        crewForTests = crew;
    }

    public void setOnForTests(@Nullable Boolean on) {
        onForTests = on;
    }

    /** How many numbers of all castes have gone longer than a meal without one. */
    public int hungry(ServerLevel level) {
        return meals.hungry(level.getGameTime());
    }

    /** Tests only: every number of this caste last ate at this game time. */
    public void setLastAteForTests(ServerLevel level, String caste, long time) {
        meals.keepUp(level, this); // (the meal list catches up with the numbers first)
        meals.setLastAteForTests(caste, time);
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    void save(CompoundTag tag) {
        ListTag list = new ListTag();
        inside.forEach((caste, ages) -> ages.forEach((minute, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("caste", caste);
            entry.putLong("grownUpAt", minute);
            entry.putInt("count", count);
            list.add(entry);
        }));
        tag.put("insideNest", list);
        meals.save(tag);
    }

    void load(CompoundTag tag) {
        inside.clear();
        ListTag list = tag.getList("insideNest", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            putInside(entry.getString("caste"), entry.getLong("grownUpAt"), entry.getInt("count"));
        }
        meals.load(tag);
    }
}
