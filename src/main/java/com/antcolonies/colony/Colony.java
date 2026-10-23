package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.species.SpeciesProfile;
import com.antcolonies.species.SpeciesProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * One colony's "brain" data. Species-neutral: the species is an id that points at a species profile ({@link #profile()}).
 * Member counts are stored per caste (mostly virtual, not real mobs).
 *
 * <p>The colony itself holds who it is, its headcount and the workers' roles. Everything else lives in its parts, one
 * per subject. Every colony has the shared ones, such as {@link #nest()} (the underground plan and digging), {@link #walls()}
 * (sealing the nest), {@link #food()} (seeds), {@link #queenCare()} (feeding the queen and her eggs), {@link #brood()} (eggs
 * and larvae), {@link #refuse()} (dead ants), {@link #foragingHoles()} (the termites' ways out to forage) and
 * {@link #population()} (the members that are numbers inside the nest, and which are real mobs). The others belong to
 * some species only (the ant hill and bread, the termite mound, fungus garden, soldiers and alarm, ...): the species file
 * lists them, and code asks for one with {@link #part}, which is empty for a colony whose species does not list it.
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
    /** Tests only: pretend it rains on the nest or not (null: the real weather). Not saved. */
    @Nullable
    private Boolean rainForTests;
    /**
     * Game test server only: what each test's own clock shows at a spot ({@code gametest.TestClock}; the tests share one
     * world and its one sun, but each starts at its own moment). Null in a normal game: the real time of day. Not saved.
     */
    @Nullable
    private static TestTime testTime;
    /**
     * Game test server only: the seed of the dice of a colony at a spot ({@code gametest.TestIsolation}: from its test's
     * name), so a test rolls the same dice whether it runs alone or beside others. Null in a normal game. Not saved.
     */
    @Nullable
    private static java.util.function.Function<BlockPos, Long> testSeeds;
    /** The colony's own dice ({@link #random}). Not saved. */
    @Nullable
    private net.minecraft.util.RandomSource random;
    /** The biome's heat at the nest (its base temperature), for a day clock's midday rest; read once. Not saved. */
    @Nullable
    private Float heat;

    private final Nest nest;
    private final NestWalls walls;
    private final FoodStore food;
    private final FoodMap foodMap;
    private final QueenCare queenCare;
    private final Brood brood;
    private final Refuse refuse;
    private final ForagingHoles foragingHoles;
    private final ForagingTrails trails;
    private final DangerMap danger;
    private final Neighbours neighbours;
    /** When the colony was founded (game time; brain step 4g, step 5: old colonies yield to neighbours). Saved. */
    private long bornAt = NOT_SET;
    private final Population population;
    private final AwayLife away;
    private final NestLedger ledger;
    private final NestLife life;
    private final FieldLife field;
    private final BuildLife building;
    private final WorkerFood workerFood;
    /** The parts this colony has (the shared ones and those its species lists), in the order they are made, saved and loaded. */
    private final List<ColonyPart<?>> parts;
    /** The parts this colony has, by kind (for {@link #part}). */
    private final Map<ColonyParts.Type<?>, Object> byType = new java.util.IdentityHashMap<>();
    /** The parts that tick while the nest is loaded, in their tick order. */
    private final List<ColonyPart<?>> ticking;
    /** The parts that tick while the colony is away, in their tick order. */
    private final List<ColonyPart<?>> tickingAway;
    /** What happened, told to the parts that listen (colony parts step 4, {@link ColonyNews}). */
    private final ColonyNews news = new ColonyNews();
    /** Yes/no questions the parts answer (colony parts steps 6 and 7, {@link ColonyQuestions}). */
    private final ColonyQuestions questions = new ColonyQuestions();
    /** What the parts plug into the shared code's sockets (colony parts step 8b, {@link ColonyHooks}). */
    private final ColonyHooks hooks = new ColonyHooks();
    /** Species and parts whose data in an old save was skipped (logged once each). */
    private static final java.util.Set<String> skippedLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Colony(UUID id, ResourceLocation species, UUID queenId, BlockPos center) {
        this.id = id;
        this.species = species;
        this.queenId = queenId;
        this.center = center;
        // The shared kinds of part and those the species file lists, in the registry's order, which is the order they are
        // made, saved and loaded (ColonyParts; Project Apex, colony parts steps 1 to 3). A kind the species does not list
        // is not made at all: code that means something only for some species asks for it first (see part).
        java.util.Set<String> listed = java.util.Set.copyOf(profile().parts());
        Map<ColonyParts.Type<?>, ColonyPart<?>> made = new LinkedHashMap<>();
        for (ColonyParts.Type<?> type : ColonyParts.all()) {
            if (type.shared() || listed.contains(type.name())) {
                ColonyPart<?> part = type.make(this);
                made.put(type, part);
                byType.put(type, part.part());
            }
        }
        this.nest = made(made, ColonyParts.NEST);
        this.walls = made(made, ColonyParts.NEST_WALLS);
        this.food = made(made, ColonyParts.FOOD_STORE);
        this.foodMap = made(made, ColonyParts.FOOD_MAP);
        this.queenCare = made(made, ColonyParts.QUEEN_CARE);
        this.brood = made(made, ColonyParts.BROOD);
        this.refuse = made(made, ColonyParts.REFUSE);
        this.foragingHoles = made(made, ColonyParts.FORAGING_HOLES);
        this.trails = made(made, ColonyParts.FORAGING_TRAILS);
        this.danger = made(made, ColonyParts.DANGER_MAP);
        this.neighbours = made(made, ColonyParts.NEIGHBOURS);
        this.population = made(made, ColonyParts.POPULATION);
        this.away = made(made, ColonyParts.AWAY_LIFE);
        this.ledger = made(made, ColonyParts.NEST_LEDGER);
        this.life = made(made, ColonyParts.NEST_LIFE);
        this.field = made(made, ColonyParts.FIELD_LIFE);
        this.building = made(made, ColonyParts.BUILD_LIFE);
        this.workerFood = made(made, ColonyParts.WORKER_FOOD);
        this.parts = List.copyOf(made.values());
        this.ticking = ColonyPart.ticking(this.parts);
        this.tickingAway = ColonyPart.tickingAway(this.parts);
        for (ColonyPart<?> part : this.parts) {
            part.signUp(news, questions, hooks); // (each part's row in ColonyParts says what news it hears, what it answers and what it plugs in)
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T made(Map<ColonyParts.Type<?>, ColonyPart<?>> made, ColonyParts.Type<T> type) {
        return ((ColonyPart<T>) made.get(type)).part(); // (a shared kind: every colony has it)
    }

    /**
     * This colony's part of a kind its species may not have (a kind that is not shared, {@link ColonyParts}): the part,
     * or empty when the species file does not list it (a harvester colony has no mound, a termite colony no bakery).
     * Never a do-nothing stand-in: code that only means something for some species asks first (Project Apex, colony
     * parts step 3, "ask before use").
     */
    public <T> Optional<T> part(ColonyParts.Type<T> type) {
        return Optional.ofNullable(type.cast(byType.get(type)));
    }

    /** Whether this colony has a part of this kind (its species lists it, or every colony has it). */
    public boolean has(ColonyParts.Type<?> type) {
        return byType.containsKey(type);
    }

    /**
     * The colony's news: tell it what happened (a member hurt, food found, ...) and the parts that listen hear it, without
     * the teller naming them (colony parts step 4, {@link ColonyNews}).
     */
    public ColonyNews news() {
        return news;
    }

    /**
     * The colony's questions: ask them and the parts that answer them say yes or no, without the one asking naming them
     * (colony parts steps 6 and 7, {@link ColonyQuestions}).
     */
    public ColonyQuestions questions() {
        return questions;
    }

    /**
     * The colony's hooks: the sockets in the shared code its parts plug their own answers and work into (the reach of
     * its trails, its door, the structure over its nest, ...), without the shared code naming them (colony parts step 8b,
     * {@link ColonyHooks}).
     */
    public ColonyHooks hooks() {
        return hooks;
    }

    /**
     * Whether the colony keeps its foragers off {@code pos} for a lasting reason (danger, a neighbour it yields to, where
     * members were attacked): asked of every part ({@link ColonyQuestions#KEEP_OFF}).
     */
    public boolean keepsOff(Level level, BlockPos pos) {
        return questions.ask(level, ColonyQuestions.KEEP_OFF, pos);
    }

    /** The name of the part that keeps the foragers off {@code pos}, or null (for the test messages and the logs). */
    @Nullable
    public String whoKeepsOff(Level level, BlockPos pos) {
        return questions.whoSaysYes(level, ColonyQuestions.KEEP_OFF, pos);
    }

    /** Whether this foraging hole is kept shut now (for danger, termites): asked of every part ({@link ColonyQuestions#HOLE_KEPT_SHUT}). */
    public boolean isHoleKeptShut(Level level, BlockPos hole) {
        return questions.ask(level, ColonyQuestions.HOLE_KEPT_SHUT, hole);
    }

    /** The parts this colony has, in the order they are made, saved and loaded (for the save and load test). */
    public List<ColonyPart<?>> parts() {
        return parts;
    }

    /**
     * Once a second while the colony is away (from {@link AwayLife#tickAway}): the parts that live on as numbers, in
     * their order while away (see the constructor).
     */
    void tickPartsAway(ServerLevel level) {
        for (ColonyPart<?> part : tickingAway) {
            part.tickAway(level);
        }
    }

    /** Whether this colony builds a termite mound (its species has the mound part and the profile's {@code mound} numbers). */
    public boolean buildsMound() {
        return questions.ask(null, ColonyQuestions.BUILDS_OVER_NEST, null);
    }

    /** Whether everyone is kept in the nest now (the alarm at an attacker, {@link Alarm#keepsEveryoneIn}): asked of every part ({@link ColonyQuestions#KEEPS_EVERYONE_IN}). */
    public boolean keepsEveryoneIn(Level level) {
        return questions.ask(level, ColonyQuestions.KEEPS_EVERYONE_IN, null);
    }

    /** Whether the structure over the nest is being built now (a mound, past founding; {@link Mound#isActive}): asked of every part ({@link ColonyQuestions#BUILDING_OVER_NEST}). */
    public boolean buildsOverNestNow() {
        return questions.ask(null, ColonyQuestions.BUILDING_OVER_NEST, null);
    }

    /** Whether the colony is alarmed now ({@link Alarm#level} above 0): asked of every part ({@link ColonyQuestions#ALARMED}, Apex step 9a). */
    public boolean isAlarmed(Level level) {
        return questions.ask(level, ColonyQuestions.ALARMED, null);
    }

    /** Whether this forager must wait in the nest before it sets out (the harvester's door, {@link ColonyQuestions#MUST_WAIT}). */
    public boolean mustWait(Level level, AbstractAntEntity forager) {
        return questions.ask(level, ColonyQuestions.MUST_WAIT, forager);
    }

    /** Whether this forager passes the food by on its way to a patch it was called to ({@link ColonyQuestions#PASSES_BY}). */
    public boolean passesBy(Level level, UUID forager, BlockPos food) {
        return questions.ask(level, ColonyQuestions.PASSES_BY, new ColonyQuestions.FoodFor(forager, food));
    }

    /**
     * Whether there is a place for this forager's piece, which it claims: at the mill if the colony has one (it takes
     * the piece if the store has no room, {@link ColonyHooks#MILL}), otherwise in the store.
     */
    public boolean claimPlaceForAPiece(ServerLevel level, UUID forager) {
        ColonyHooks.Mill mill = hooks.first(ColonyHooks.MILL);
        return mill != null ? mill.hasPlace(level, forager) : food().claimStorePlace(level, forager);
    }

    /** Whether this forager's piece goes to the mill (the store had no room for it). */
    public boolean pieceGoesToMill(UUID forager) {
        ColonyHooks.Mill mill = hooks.first(ColonyHooks.MILL);
        return mill != null && mill.milling(forager);
    }

    /** The colony's lasting trails (the harvester's trunk trails, {@link ColonyHooks#TRAILS}), or null. */
    @Nullable
    public ColonyHooks.Trails lastingTrails() {
        return hooks.first(ColonyHooks.TRAILS);
    }

    /**
     * The blocks of the structure built over the nest (the termite mound, {@link ColonyHooks#STRUCTURE}), each with what
     * it is to its builders; none for a species without one. Shared code only asks which cells are in it.
     */
    public Map<BlockPos, ?> moundParts(Level level) {
        ColonyHooks.Structure structure = hooks.first(ColonyHooks.STRUCTURE);
        return structure == null ? Map.of() : structure.parts(level);
    }

    /** The underground nest: its plan, the next block to dig, and the dug-out cells of each room. */
    public Nest nest() {
        return nest;
    }

    /** The nest walls: openings to seal. */
    public NestWalls walls() {
        return walls;
    }


    /** Seeds: fetching, clipping, storing and taking them. */
    public FoodStore food() {
        return food;
    }

    /** The food plants the colony knows of (found by its workers) and which ones a worker has found but not yet told of. */
    public FoodMap foodMap() {
        return foodMap;
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




    /** Where the foragers have been finding food lately and by which way out they went (the trails the minor soldiers line). */
    public ForagingTrails trails() {
        return trails;
    }





    /** Where the colony's insects were hurt or killed, or saw hostile mobs (brain step 4g; species with {@code colony.danger}). */
    public DangerMap danger() {
        return danger;
    }


    /** Where the colony met neighbouring colonies of its species, and which ways it yields (brain step 4g, step 5). */
    public Neighbours neighbours() {
        return neighbours;
    }

    /**
     * How old the colony is, in game days since it was founded (an older save without a founding time counts from its
     * first load; real colony age is what makes harvester ants yield to neighbours, Gordon and Kulig).
     */
    public double ageDays(Level level) {
        return ageDaysAt(level.getGameTime());
    }

    /** The colony's age at game time {@code now}, in game days (0 before its first tick). */
    public double ageDaysAt(long now) {
        return bornAt == NOT_SET ? 0 : Math.max(0, now - bornAt) / 24000.0;
    }

    /** Game tests only: the colony was founded {@code days} game days ago. */
    public void setAgeForTests(Level level, double days) {
        bornAt = level.getGameTime() - Math.round(days * 24000.0);
    }

    /**
     * Whether a forager may go for food at {@code pos} now: no part says it is not cleared yet today (the harvester's
     * patrol: a patroller came back from that way) and none keeps the foragers off it ({@link #keepsOff}).
     */
    public boolean mayForageAt(Level level, BlockPos pos) {
        return !questions.ask(level, ColonyQuestions.NOT_CLEARED, pos) && !keepsOff(level, pos);
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

    /** The builders among the numbers: what they dig and build while away, done out of sight on return. */
    public BuildLife building() {
        return building;
    }

    /** Where the workers' own meals come from (the real ones and the numbers). */
    public WorkerFood workerFood() {
        return workerFood;
    }

    /**
     * What the nurses feed the queen and the young: fungus nodules for a species with a fungus garden, ant bread
     * otherwise (null only for a species with neither part).
     */
    public NurseFood nurseFood() {
        return hooks.first(ColonyHooks.NURSE_FOOD);
    }

    /** The foraging holes and their tunnels (only species whose profile has {@code nest.foraging_holes}). */
    public ForagingHoles foragingHoles() {
        return foragingHoles;
    }

    /**
     * Whether it is time to forage (the profile's {@code food.day_clock} if it has one: harvester ants by day with a
     * midday rest, never in the rain; otherwise its {@code food.forage_at}: termites at night and in the rain). Outside this time the foragers stay home, the foraging holes are plugged, and a species that
     * keeps to a schedule does no work outside the nest and the mound at all (it stays in, out of the sun). The end of the
     * night already counts as day ({@link #isBeforeDawn}): they head in before the sun is up. Never while the alarm is at
     * 2 or more ({@link Alarm#keepsEveryoneIn}): the foragers hurry home.
     */
    public boolean isForagingTime(net.minecraft.world.level.Level level) {
        if (keepsEveryoneIn(level)) {
            return false;
        }
        Optional<SpeciesProfile.DayClock> clock = profile().food().dayClock();
        if (clock.isPresent()) {
            return clock.get().outAt(timeOfDay(level), heat(level), rainForTests != null ? rainForTests : rainsOnNest(level));
        }
        boolean day = isDay(level);
        return profile().food().foragesNow(day, rainForTests != null ? rainForTests : level.isRaining());
    }

    /** Whether it counts as day at the nest: the sun is up, or it is the end of the night ({@link #isBeforeDawn}). */
    private boolean isDay(net.minecraft.world.level.Level level) {
        if (testTime == null) {
            return level.isDay() || isBeforeDawn(level);
        }
        long time = timeOfDay(level);
        return !level.dimensionType().hasFixedTime() && (isSunUp(level, time) || isBeforeDawn(time));
    }

    /**
     * Whether the sun is up at this time of day, by Minecraft's own sun ({@code Level.updateSkyBrightness}, without the
     * weather): from about 5:28 (23460) to 18:33 (12550). For the test clocks, which keep their own time.
     */
    public static boolean isSunUp(net.minecraft.world.level.Level level, long dayTime) {
        float sun = level.dimensionType().timeOfDay(dayTime);
        double light = 0.5 + 2.0 * net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.cos(sun * (float) (Math.PI * 2)), -0.25, 0.25);
        return (int) ((1.0 - light) * 11.0) < 4;
    }

    /** The time of day for the day clock (see {@link #isForagingTime}). */
    public long timeOfDay(net.minecraft.world.level.Level level) {
        return testTime != null ? testTime.dayTimeAt(level, center) : level.getDayTime();
    }

    /** Game test server only: what each test's clock shows at a spot (Level#getDayTime's scale: 0 is 6:00, 1000 an hour). */
    public interface TestTime {
        long dayTimeAt(net.minecraft.world.level.Level level, BlockPos pos);
    }

    /** Game test server only: the test clocks every colony reads (see {@link #testTime}). */
    public static void setTestTime(@Nullable TestTime time) {
        testTime = time;
    }

    /**
     * The colony's own dice, for its decisions (which seed heap, which way to search, who dies of old age, ...). Each
     * colony rolls its own, never the world's shared dice: on the test server the tests run side by side in one world, and
     * with shared dice every colony's luck changed with whatever its neighbours rolled (the test health plan, 2026-10-16).
     * In a game: seeded from the colony's id; on the test server: from its test (the same dice every run).
     */
    public net.minecraft.util.RandomSource random() {
        if (random == null) {
            Long seed = testSeeds == null ? null : testSeeds.apply(center);
            random = net.minecraft.util.RandomSource.create(seed != null ? seed : id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        }
        return random;
    }

    /** Game test server only: where each colony's dice come from (see {@link #testSeeds}). */
    public static void setTestSeeds(@Nullable java.util.function.Function<BlockPos, Long> seeds) {
        testSeeds = seeds;
    }

    /** The biome's heat at the nest (its base temperature: plains 0.8, badlands, desert and savanna 2.0). */
    public float heat(net.minecraft.world.level.Level level) {
        if (heat == null) {
            heat = level.getBiome(center).value().getBaseTemperature();
        }
        return heat;
    }

    /** Whether rain falls on the nest now (never in a dry biome such as the badlands or the desert). */
    private boolean rainsOnNest(net.minecraft.world.level.Level level) {
        return level.isRaining() && level.getBiome(center).value().hasPrecipitation();
    }

    /** Whether it rains on the nest now (as the colony sees it: a game test may pretend, see {@link #setRainForTests}). */
    public boolean rainsNow(net.minecraft.world.level.Level level) {
        return rainForTests != null ? rainForTests : rainsOnNest(level);
    }

    /** Tests only: pretend the biome at the nest is this hot, for the midday rest's length (null for the real biome). */
    public void setHeatForTests(@Nullable Float heat) {
        this.heat = heat;
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

    /**
     * Tests only: pretend it rains on the nest or not (null: the real weather). Only this colony sees it: the test
     * world's real rain would reach every test running beside it.
     */
    public void setRainForTests(@Nullable Boolean raining) {
        rainForTests = raining;
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

    /**
     * The founder beside the queen (the termite king, {@link ColonyHooks#CO_FOUNDERS}), if he is loaded and alive: he
     * stays beside the queen, so he is looked for round her.
     */
    @Nullable
    public AbstractAntEntity getKing(ServerLevel level) {
        ColonyHooks.CoFounders founder = hooks().first(ColonyHooks.CO_FOUNDERS);
        if (founder == null || getCount(founder.caste()) <= 0) {
            return null;
        }
        AbstractAntEntity queen = getQueen(level);
        net.minecraft.world.phys.AABB around = queen != null ? queen.getBoundingBox().inflate(16)
                : new net.minecraft.world.phys.AABB(center).inflate(32);
        return level.getEntitiesOfClass(AbstractAntEntity.class, around,
                        king -> founder.isOne(king) && king.isAlive() && id.equals(king.getColonyId()))
                .stream().findFirst().orElse(null);
    }

    /** Step 9c: whether this caste is one of the species' own castes beyond the worker (the termite soldiers, {@link ColonyHooks#CASTES}). */
    public boolean isOwnCaste(@Nullable String caste) {
        return caste != null && hooks.all(ColonyHooks.CASTES).stream().anyMatch(castes -> castes.castes().contains(caste));
    }

    /** Step 10: the number a young one growing into this caste is synced and saved as ({@link ColonyHooks.Castes#numberOf}); 0: a worker. */
    public byte casteNumber(@Nullable String caste) {
        if (caste != null) {
            for (ColonyHooks.Castes castes : hooks.all(ColonyHooks.CASTES)) {
                byte number = castes.numberOf(caste);
                if (number != 0) {
                    return number;
                }
            }
        }
        return 0;
    }

    /** Step 10: the caste a young one with this number grows into ({@link #casteNumber}), or null: a worker. */
    @Nullable
    public String casteOfNumber(int number) {
        if (number != 0) {
            for (ColonyHooks.Castes castes : hooks.all(ColonyHooks.CASTES)) {
                String caste = castes.casteOf(number);
                if (caste != null) {
                    return caste;
                }
            }
        }
        return null;
    }

    /** How many founders beside the queen the colony counts (the termite king; none for a lone queen's species). */
    public int coFounderCount() {
        ColonyHooks.CoFounders founder = hooks().first(ColonyHooks.CO_FOUNDERS);
        return founder == null ? 0 : getCount(founder.caste());
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
        return getMemberCount() - coFounderCount() <= 0 && brood().getEggs(level).isEmpty()
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
        if (bornAt == NOT_SET || bornAt > level.getGameTime()) {
            bornAt = level.getGameTime(); // (founded now, or an older save: counted from its first load)
        }
        for (ColonyPart<?> part : ticking) {
            part.tick(level);
        }
        return true;
    }

    /**
     * How far (blocks, sideways from the nest's middle) the colony forages: the profile's food radius (a young colony's
     * reach), or farther once its trunk trails reach farther (long trails: the range is the trails, the user's decision
     * of 2026-10-17, see {@link TrunkTrails}), or once its tunnels do (new holes near food: the reach grows with the
     * colony, see {@code nest.new_holes}).
     */
    public int foragingRadius() {
        int reached = profile().food().radius();
        for (ColonyHooks.Reach reach : hooks.all(ColonyHooks.REACH)) {
            reached = Math.max(reached, reach.reach()); // (the trunk trails' longest tip)
        }
        int radius = reached;
        int galleries = nest.galleries().range();
        if (galleries > 0) {
            // Termites underground (step 2a): the range is the galleries, the farthest hole and the graze round it, in
            // place of the size table.
            return Math.max(radius, galleries);
        }
        return profile().nest().newHoles().map(holes -> Math.max(radius, holes.reach(getCount(Caste.WORKER)))).orElse(radius);
    }

    /**
     * How far (blocks, sideways from the nest's middle) the colony's searchers may get at most: the foraging range plus
     * the stretch of a search drifting outward when nothing is found nearer (brain step 4c, {@code food.search}).
     */
    public int searchReach() {
        return foragingRadius() + profile().food().search().map(SpeciesProfile.SearchNumbers::stretch).orElse(0);
    }

    /** The nest area reaches at least this far sideways (blocks; it was the food radius, 24, before a young harvester colony's reach became 12). */
    private static final int NEST_AREA_LEAST = 24;

    /** The area (around the nest, down to the refuse chamber) where the colony's eggs, larvae and dead ants are looked for. */

    AABB nestArea() {
        int radius = Math.max(NEST_AREA_LEAST, profile().food().radius());
        return new AABB(center).inflate(radius, 0, radius).expandTowards(0, 8, 0).expandTowards(0, -12, 0);
    }

    /** Whether the colony is still being founded: nobody yet but the queen (and her king, for termites). */
    public boolean isFounding() {
        return !isQueenless() && getMemberCount() - coFounderCount() == 0;
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

    /** How many of the colony's real workers are builders now (the numbers inside the nest not counted). */
    public int buildersNow(ServerLevel level) {
        long now = level.getGameTime();
        if (now - rolesWorkedOutAt >= 20 || now < rolesWorkedOutAt) {
            workOutRoles(level, now);
        }
        int builders = 0;
        for (ColonyRole role : roles.values()) {
            if (role == ColonyRole.BUILDER) {
                builders++;
            }
        }
        return builders;
    }

    /** The role of the worker with this id (for tests and the colony list); a builder if it is not loaded. */
    public ColonyRole roleOf(ServerLevel level, UUID ant) {
        return level.getEntity(ant) instanceof AbstractAntEntity worker ? roleOf(level, worker) : roles.getOrDefault(ant, ColonyRole.BUILDER);
    }

    /** How many foragers the colony has now: its real workers whose job is foraging, and the forager numbers inside. */
    public int foragersNow(ServerLevel level) {
        workOutRoles(level, level.getGameTime());
        int real = 0;
        for (ColonyRole role : roles.values()) {
            real += role == ColonyRole.FORAGER ? 1 : 0;
        }
        return real + field.foragersInside(level);
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
        tag.putLong("bornAt", bornAt);
        for (ColonyPart<?> part : parts) {
            part.save(tag);
        }
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
        colony.bornAt = tag.contains("bornAt") ? tag.getLong("bornAt") : NOT_SET;
        for (ColonyPart<?> part : colony.parts) {
            part.load(tag); // (in the order they are made: see the constructor)
        }
        colony.skipUnused(tag);
        return colony;
    }

    /**
     * An older save holds every part, as every colony once had them all: a part this species no longer lists is skipped.
     * Logged once per species and part when the save held more than a fresh part would (left-over defaults are not news).
     * To know such a part's save keys, a throwaway one is made here, only while loading, and never kept.
     */
    private void skipUnused(CompoundTag tag) {
        for (ColonyParts.Type<?> type : ColonyParts.all()) {
            if (has(type)) {
                continue;
            }
            ColonyPart<?> part = type.make(this);
            if (!part.isSaved()) {
                continue;
            }
            CompoundTag fresh = new CompoundTag();
            part.save(fresh); // (never loaded: as new)
            boolean held = false;
            for (String key : fresh.getAllKeys()) {
                held |= tag.contains(key) && !tag.get(key).equals(fresh.get(key));
            }
            if (held && skippedLogged.add("skipped " + species + " " + part.name())) {
                com.antcolonies.AntColoniesMod.LOGGER.info("[parts] a saved {} colony held data for the part {}, which its species file no longer lists: skipped",
                        species, part.name());
            }
        }
    }
}
