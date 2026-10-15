package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.entity.TermiteKingEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * One colony's "brain" data. Species-neutral: the species is an id that points at a species profile ({@link #profile()}).
 * Member counts are stored per caste (mostly virtual, not real mobs).
 *
 * <p>The colony itself holds who it is, its headcount and the workers' roles. Everything else lives in its parts, one
 * per subject: {@link #nest()} (the underground plan and digging), {@link #walls()} (sealing the nest), {@link #hill()},
 * {@link #food()} (seeds), {@link #bakery()} (ant bread), {@link #queenCare()} (feeding the queen and her eggs),
 * {@link #brood()} (eggs and larvae), {@link #refuse()} (dead ants), {@link #mound()} (the termite mound),
 * {@link #foragingHoles()} (the termites' ways out to forage), {@link #garden()} (the termites' fungus garden) and
 * {@link #soldiers()} (the termites' soldiers), {@link #alarm()} (their alarm) and {@link #population()} (the members that
 * are numbers inside the nest, and which are real mobs).
 */
public class Colony {
    /** "No time saved": a hunger clock that has not started yet. */
    public static final long NOT_SET = Long.MIN_VALUE;

    private final UUID id;
    private final ResourceLocation species;
    private final UUID queenId;
    private final BlockPos center;
    private final Map<String, Integer> casteCounts = new LinkedHashMap<>();

    /** The role of each worker: ant -> role, worked out from the ages below. Not saved (the ages are, on the workers). */
    private final Map<UUID, ColonyRole> roles = new HashMap<>();
    /** When each worker known to the colony grew up (the worker's own saved age). Not saved. */
    private final Map<UUID, Long> grownUp = new HashMap<>();
    /** The game time the roles were last worked out. */
    private long rolesWorkedOutAt = Long.MIN_VALUE;
    /** How many workers of unknown age (spawn eggs, older saves) were given one, for the next one's age. Not saved. */
    private int agesGiven;
    /** Whether the workers of this colony get hungry. On, except in some tests. Not saved. */
    private boolean hungerOn = true;
    /** When the queen died (a queenless colony carries on without her and dwindles), or NOT_SET while she lives. Saved. */
    private long queenDiedAt = NOT_SET;
    /** Tests only: forces foraging time on or off (null: the real clock and weather). Not saved. */
    @Nullable
    private Boolean foragingTimeForTests;

    private final Nest nest;
    private final NestWalls walls;
    private final Hill hill;
    private final FoodStore food;
    private final FoodMap foodMap;
    private final Bakery bakery;
    private final QueenCare queenCare;
    private final Brood brood;
    private final Refuse refuse;
    private final Mound mound;
    private final ForagingHoles foragingHoles;
    private final ForagingTrails trails;
    private final FungusGarden garden;
    private final Mushrooms mushrooms;
    private final Soldiers soldiers;
    private final Alarm alarm;
    private final Population population;
    private final AwayLife away;
    private final NestLedger ledger;
    private final NestLife life;
    private final FieldLife field;
    private final WorkerFood workerFood;

    public Colony(UUID id, ResourceLocation species, UUID queenId, BlockPos center) {
        this.id = id;
        this.species = species;
        this.queenId = queenId;
        this.center = center;
        this.nest = new Nest(this);
        this.walls = new NestWalls(this);
        this.hill = new Hill(this);
        this.food = new FoodStore(this);
        this.foodMap = new FoodMap(this);
        this.bakery = new Bakery(this);
        this.queenCare = new QueenCare(this);
        this.brood = new Brood(this);
        this.refuse = new Refuse(this);
        this.mound = new Mound(this);
        this.foragingHoles = new ForagingHoles(this);
        this.trails = new ForagingTrails(this);
        this.garden = new FungusGarden(this);
        this.mushrooms = new Mushrooms(this);
        this.soldiers = new Soldiers(this);
        this.alarm = new Alarm(this);
        this.population = new Population(this);
        this.away = new AwayLife(this);
        this.ledger = new NestLedger(this);
        this.life = new NestLife(this);
        this.field = new FieldLife(this);
        this.workerFood = new WorkerFood(this);
    }

    /** The underground nest: its plan, the next block to dig, and the dug-out cells of each room. */
    public Nest nest() {
        return nest;
    }

    /** The nest walls: openings to seal. */
    public NestWalls walls() {
        return walls;
    }

    /** The hill around the entrance. */
    public Hill hill() {
        return hill;
    }

    /** Seeds: fetching, clipping, storing and taking them. */
    public FoodStore food() {
        return food;
    }

    /** The food plants the colony knows of (found by its workers) and which ones a worker has found but not yet told of. */
    public FoodMap foodMap() {
        return foodMap;
    }

    /** Ant bread: milling, storing and taking it. */
    public Bakery bakery() {
        return bakery;
    }

    /** The queen: feeding her, her eggs and her hunger. */
    public QueenCare queenCare() {
        return queenCare;
    }

    /** Eggs and larvae. */
    public Brood brood() {
        return brood;
    }

    /** Dead ants and the refuse chamber. */
    public Refuse refuse() {
        return refuse;
    }

    /** The termite mound (only species whose profile has a {@code mound} build one; see {@link Mound#numbers}). */
    public Mound mound() {
        return mound;
    }

    /** The fungus garden: comb, nodules (only species whose profile has a {@code garden}). */
    public FungusGarden garden() {
        return garden;
    }

    /** The Termitomyces mushrooms a healthy garden sends up round the mound after rain. */
    public Mushrooms mushrooms() {
        return mushrooms;
    }

    /** Where the foragers have been finding food lately and by which way out they went (the trails the minor soldiers line). */
    public ForagingTrails trails() {
        return trails;
    }

    /** The soldiers: how many the colony wants, which nymphs grow into them, where they stand guard (species with soldiers). */
    public Soldiers soldiers() {
        return soldiers;
    }

    /** The alarm: its level, where the trouble is, and what each soldier does about it (species with an {@code alarm}). */
    public Alarm alarm() {
        return alarm;
    }

    /** The members that are numbers inside the nest, and which ones are real mobs (the brain, part 1). */
    public Population population() {
        return population;
    }

    /** Going away and coming back: the insects frozen in unloaded land, and what is owed to the nest (the brain, part 2). */
    public AwayLife away() {
        return away;
    }

    /** What the counts have changed and the nest's blocks have not caught up with yet (put right out of sight). */
    public NestLedger ledger() {
        return ledger;
    }

    /** Life in the nest as numbers: the young that are numbers, and while away the queen, the nurses and the fungus. */
    public NestLife life() {
        return life;
    }

    /** The foragers among the numbers: what they gather from the plants the colony knows, out of every player's sight. */
    public FieldLife field() {
        return field;
    }

    /** Where the workers' own meals come from (the real ones and the numbers). */
    public WorkerFood workerFood() {
        return workerFood;
    }

    /**
     * What the nurses feed the queen and the young: fungus nodules for a species with a fungus garden, ant bread
     * otherwise.
     */
    public NurseFood nurseFood() {
        return garden.exists() ? garden : bakery;
    }

    /** The foraging holes and their tunnels (only species whose profile has {@code nest.foraging_holes}). */
    public ForagingHoles foragingHoles() {
        return foragingHoles;
    }

    /**
     * Whether it is time to forage (the profile's {@code food.forage_at}: termites at night and in the rain, harvester
     * ants at any time). Outside this time the foragers stay home, the foraging holes are plugged, and a species that
     * keeps to a schedule does no work outside the nest and the mound at all (it stays in, out of the sun). The end of the
     * night already counts as day ({@link #isBeforeDawn}): they head in before the sun is up. Never while the alarm is at
     * 2 or more ({@link Alarm#keepsEveryoneIn}): the foragers hurry home.
     */
    public boolean isForagingTime(net.minecraft.world.level.Level level) {
        if (alarm.keepsEveryoneIn(level)) {
            return false;
        }
        if (foragingTimeForTests != null) {
            return foragingTimeForTests;
        }
        return profile().food().foragesNow(level.isDay() || isBeforeDawn(level), level.isRaining());
    }

    /**
     * The time of day (ticks, 0 to 24000) from which the night counts as over: the last in-game hour or so before the sun
     * comes up (it rises at about 23000; my pick). The user's idea, like real termites: they head in before dawn, so the
     * holes and windows are plugged and everybody is inside when the sun is up.
     */
    public static final long HEAD_IN_FROM = 22000;

    /** Whether it is the end of the night, when a species out at night heads in (see {@link #HEAD_IN_FROM}). */
    public static boolean isBeforeDawn(net.minecraft.world.level.Level level) {
        return !level.dimensionType().hasFixedTime() && isBeforeDawn(level.getDayTime());
    }

    /** Whether this time of day ({@link net.minecraft.world.level.Level#getDayTime}) is the end of the night. */
    public static boolean isBeforeDawn(long dayTime) {
        return dayTime % 24000 >= HEAD_IN_FROM;
    }

    /** Whether this colony's species goes out at some times only (so it stays inside the rest of the time). */
    public boolean keepsASchedule() {
        return !profile().food().forageAt().containsAll(SpeciesProfile.FoodNumbers.ALWAYS);
    }

    /** Tests only: forces foraging time on or off (null for the real clock and weather). */
    public void setForagingTimeForTests(@Nullable Boolean on) {
        foragingTimeForTests = on;
    }

    public UUID getId() {
        return id;
    }

    public ResourceLocation getSpecies() {
        return species;
    }

    /** The numbers this colony runs on, from its species profile (a JSON file, see {@link SpeciesProfiles}). */
    public SpeciesProfile profile() {
        return SpeciesProfiles.get(species);
    }

    public UUID getQueenId() {
        return queenId;
    }

    public BlockPos getCenter() {
        return center;
    }

    /** The queen (of whatever species), if she is loaded and alive. */
    @Nullable
    public AbstractAntEntity getQueen(ServerLevel level) {
        if (isQueenless()) {
            return null;
        }
        return level.getEntity(queenId) instanceof AbstractAntEntity queen && queen.isAlive() ? queen : null;
    }

    /** The king (termites), if he is loaded and alive: he stays beside the queen, so he is looked for round her. */
    @Nullable
    public AbstractAntEntity getKing(ServerLevel level) {
        if (getCount(TermiteKingEntity.CASTE) <= 0) {
            return null;
        }
        AbstractAntEntity queen = getQueen(level);
        net.minecraft.world.phys.AABB around = queen != null ? queen.getBoundingBox().inflate(16)
                : new net.minecraft.world.phys.AABB(center).inflate(32);
        return level.getEntitiesOfClass(TermiteKingEntity.class, around, king -> king.isAlive() && id.equals(king.getColonyId()))
                .stream().findFirst().orElse(null);
    }

    /**
     * Whether the queen has died. Like a real queenless colony, it carries on without her: the workers keep working and
     * raise the eggs and young already there, but no new eggs come, so it dwindles as its workers die of old age, until
     * the nest is empty and abandoned (see {@link #tick}).
     */
    public boolean isQueenless() {
        return queenDiedAt != NOT_SET;
    }

    /** The queen has died. Returns true if the colony carries on without her, false if nobody is left (it is gone). */
    public boolean queenDied(ServerLevel level) {
        queenDiedAt = level.getGameTime();
        return !isEmpty(level);
    }

    /** Nobody left: no workers or soldiers, no eggs or young. */
    private boolean isEmpty(ServerLevel level) {
        return getMemberCount() - getCount(com.antcolonies.entity.TermiteKingEntity.CASTE) <= 0 && brood().getEggs(level).isEmpty()
                && brood().getLarvae(level).isEmpty() && brood().insideCount() == 0;
    }

    /**
     * Once a second for every colony whose middle is loaded (called from {@code ModEvents}, not by the queen, so a
     * colony's own clock keeps going without her): the fungus garden, the mound's leftovers, the mushrooms, the alarm,
     * the food map, the grass growing back and the members that are numbers. A queenless colony with nobody left is abandoned: it ends, and its nest stays in the ground.
     * Returns false once the colony has ended.
     */
    public boolean tick(ServerLevel level) {
        if (isQueenless() && isEmpty(level)) {
            return false;
        }
        away.settle(level);
        garden.grow(level);
        mound.tick(level);
        mushrooms.tick(level);
        alarm.tick(level);
        foodMap.tick(level);
        foragingHoles.tick(level);
        food.barePatches().tick(level);
        population.tick(level);
        life.tick(level, false);
        field.tick(level, false);
        return true;
    }

    /**
     * How far (blocks, sideways from the nest's middle) the colony forages: the profile's food radius, or farther once
     * its tunnels reach farther (new holes near food: the reach grows with the colony, see {@code nest.new_holes}).
     */
    public int foragingRadius() {
        int radius = profile().food().radius();
        return profile().nest().newHoles().map(holes -> Math.max(radius, holes.reach(getCount(com.antcolonies.entity.WorkerAntEntity.CASTE)))).orElse(radius);
    }

    /** The area (around the nest, down to the refuse chamber) where the colony's eggs, larvae and dead ants are looked for. */
    AABB nestArea() {
        int radius = profile().food().radius();
        return new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0).expandTowards(0, -12, 0);
    }

    /** Whether the colony is still being founded: nobody yet but the queen (and her king, for termites). */
    public boolean isFounding() {
        return !isQueenless() && getMemberCount() - getCount(TermiteKingEntity.CASTE) == 0;
    }

    public boolean isHungerOn() {
        return hungerOn;
    }

    public void setHungerOn(boolean on) {
        hungerOn = on;
    }

    /**
     * The role of this worker. A worker's age decides its usual job (see the profile's {@code worker} numbers): a nurse
     * while young, then a builder, then a forager. The colony is flexible: when a job is short-handed (fewer nurses than
     * {@code nurses_from_workers} asks for, or fewer than a third of the others builders or foragers) it moves over the
     * workers nearest in age, so the jobs stay in age order (the youngest nurse, the oldest forage). Worked out once a
     * second, or at once when a new worker turns up.
     */
    public ColonyRole roleOf(ServerLevel level, AbstractAntEntity ant) {
        long now = level.getGameTime();
        if (ant.getGrownUpAt() == AbstractAntEntity.AGE_NOT_SET) {
            ant.setGrownUpAt(now - ageForUnknownWorker());
        }
        Long known = grownUp.put(ant.getUUID(), ant.getGrownUpAt());
        if (known == null || known != ant.getGrownUpAt() || now - rolesWorkedOutAt >= 20 || now < rolesWorkedOutAt) {
            workOutRoles(level, now);
        }
        return roles.getOrDefault(ant.getUUID(), ColonyRole.BUILDER);
    }

    /** The role of the worker with this id (for tests and the colony list); a builder if it is not loaded. */
    public ColonyRole roleOf(ServerLevel level, UUID ant) {
        return level.getEntity(ant) instanceof AbstractAntEntity worker ? roleOf(level, worker) : roles.getOrDefault(ant, ColonyRole.BUILDER);
    }

    /**
     * For the colony list: how many workers are young, middle-aged and old, and how many do each job, such as
     * "ages 2 young, 3 middle, 1 old, jobs 1 nurse, 3 builders, 2 foragers" (only the workers known to the colony).
     */
    public String describeJobs(ServerLevel level) {
        long now = level.getGameTime();
        workOutRoles(level, now);
        int[] ages = new int[ColonyRole.values().length];
        for (long grew : grownUp.values()) {
            ages[profile().worker().roleForAge(now - grew).ordinal()]++;
        }
        int[] jobs = new int[ColonyRole.values().length];
        for (ColonyRole role : roles.values()) {
            jobs[role.ordinal()]++;
        }
        return "ages " + ages[ColonyRole.NURSE.ordinal()] + " young, " + ages[ColonyRole.BUILDER.ordinal()] + " middle, "
                + ages[ColonyRole.FORAGER.ordinal()] + " old, jobs " + jobs[ColonyRole.NURSE.ordinal()] + " nurses, "
                + jobs[ColonyRole.BUILDER.ordinal()] + " builders, " + jobs[ColonyRole.FORAGER.ordinal()] + " foragers";
    }

    /**
     * How old a worker that turned up without an age is made (from a spawn egg, or an older save): a grown-up worker,
     * in the middle of the builder or the forager years, two builders to one forager, so a handful of them shares the
     * jobs out as before ages existed.
     */
    private long ageForUnknownWorker() {
        var numbers = profile().worker();
        long builderYears = Math.max(1, numbers.builderUntilTicks() - numbers.nurseUntilTicks());
        boolean forager = agesGiven++ % 3 == 1;
        return forager ? numbers.builderUntilTicks() + builderYears / 2 : numbers.nurseUntilTicks() + builderYears / 2;
    }

    private void workOutRoles(ServerLevel level, long now) {
        rolesWorkedOutAt = now;
        grownUp.keySet().removeIf(id -> !(level.getEntity(id) instanceof AbstractAntEntity worker && worker.isAlive()
                && getId().equals(worker.getColonyId())));
        // Every worker's age as it is now (one may have changed since it last asked for its job).
        grownUp.replaceAll((id, grew) -> level.getEntity(id) instanceof AbstractAntEntity worker
                && worker.getGrownUpAt() != AbstractAntEntity.AGE_NOT_SET ? worker.getGrownUpAt() : grew);
        List<Map.Entry<UUID, Long>> workers = new ArrayList<>(grownUp.entrySet());
        // Youngest first (grew up last); same age: by id, so the order does not change from one second to the next.
        workers.sort(Comparator.comparing((Map.Entry<UUID, Long> entry) -> -entry.getValue()).thenComparing(Map.Entry::getKey));
        int total = workers.size();
        int[] natural = new int[ColonyRole.values().length];
        for (Map.Entry<UUID, Long> entry : workers) {
            natural[profile().worker().roleForAge(now - entry.getValue()).ordinal()]++;
        }
        int[] counts = shareOut(total, profile().colony().nurses(total), natural);
        roles.clear();
        int index = 0;
        for (ColonyRole role : new ColonyRole[] {ColonyRole.NURSE, ColonyRole.BUILDER, ColonyRole.FORAGER}) {
            for (int i = 0; i < counts[role.ordinal()]; i++) {
                roles.put(workers.get(index++).getKey(), role);
            }
        }
    }

    /**
     * How many workers do each job: as many as their ages say ({@code natural}), but at least {@code nursesWanted}
     * nurses and a third of the rest builders and a third foragers (rounded up; with too few workers for all of that,
     * builders come first, then foragers). A short job takes workers from the job with the most to spare.
     */
    static int[] shareOut(int total, int nursesWanted, int[] natural) {
        int nurse = ColonyRole.NURSE.ordinal(), builder = ColonyRole.BUILDER.ordinal(), forager = ColonyRole.FORAGER.ordinal();
        int minNurses = Math.min(total, nursesWanted);
        int rest = total - minNurses;
        int minBuilders = Math.min(rest, (rest + 2) / 3);
        int minForagers = Math.min(rest - minBuilders, (rest + 2) / 3);
        int[] counts = new int[ColonyRole.values().length];
        counts[nurse] = Math.max(natural[nurse], minNurses);
        counts[forager] = Math.max(natural[forager], minForagers);
        counts[builder] = total - counts[nurse] - counts[forager];
        while (counts[builder] < minBuilders) {
            int spareNurses = counts[nurse] - minNurses;
            int spareForagers = counts[forager] - minForagers;
            if (spareNurses <= 0 && spareForagers <= 0) {
                break;
            }
            counts[spareNurses >= spareForagers ? nurse : forager]--;
            counts[builder]++;
        }
        return counts;
    }

    public int getCount(String caste) {
        return casteCounts.getOrDefault(caste, 0);
    }

    public void setCount(String caste, int count) {
        casteCounts.put(caste, Math.max(0, count));
    }

    /** Total members across all castes (the queen is not counted). */
    public int getMemberCount() {
        return casteCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getCasteCounts() {
        return casteCounts;
    }

    /** Writes the colony to the save. The parts write their own numbers into the same tag (the key names are kept, so old saves load). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("species", species.toString());
        tag.putUUID("queen", queenId);
        tag.putLong("center", center.asLong());
        if (isQueenless()) {
            tag.putLong("queenDiedAt", queenDiedAt);
        }
        CompoundTag castes = new CompoundTag();
        casteCounts.forEach(castes::putInt);
        tag.put("castes", castes);
        food.save(tag);
        bakery.save(tag);
        queenCare.save(tag);
        nest.save(tag);
        mound.save(tag);
        alarm.save(tag);
        foodMap.save(tag);
        population.save(tag);
        brood.save(tag);
        away.save(tag);
        ledger.save(tag);
        life.save(tag);
        field.save(tag);
        garden.save(tag);
        workerFood.save(tag);
        return tag;
    }

    public static Colony load(CompoundTag tag) {
        Colony colony = new Colony(
                tag.getUUID("id"),
                ResourceLocation.parse(tag.getString("species")),
                tag.getUUID("queen"),
                BlockPos.of(tag.getLong("center")));
        CompoundTag castes = tag.getCompound("castes");
        for (String key : castes.getAllKeys()) {
            colony.casteCounts.put(key, castes.getInt(key));
        }
        if (tag.contains("queenDiedAt")) {
            colony.queenDiedAt = tag.getLong("queenDiedAt");
        }
        colony.food.load(tag);
        colony.bakery.load(tag);
        colony.queenCare.load(tag);
        colony.nest.load(tag);
        colony.mound.load(tag);
        colony.alarm.load(tag);
        colony.foodMap.load(tag);
        colony.population.load(tag);
        colony.brood.load(tag);
        colony.away.load(tag);
        colony.ledger.load(tag); // (after the food and the bread: an older save's owed food is counted in)
        colony.life.load(tag);
        colony.field.load(tag);
        colony.garden.load(tag);
        colony.workerFood.load(tag);
        return colony;
    }
}
