package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts registry (Project Apex, colony parts step 2, {@code docs/plans/apex.md}): every kind of colony part by its
 * name, with how to make a colony's own. A <b>shared</b> part is in every colony (the nest, the food store, the brood,
 * ...); any other part is in a colony only when its species file lists it under {@code "parts"} (the termite mound, the
 * harvester's bakery, ...). A name in a species file that nothing signed up stops the species files loading, with the
 * known names in the message ({@link #checkSpecies}).
 *
 * <p>The kinds are signed up here in the order they are made and loaded (the nest ledger loads after the food store and
 * the bakery: an older save's owed food is counted into them); the order a part ticks in is its place, written beside it
 * (see {@link ColonyPart}). After the split into three mods (Apex part 2) the framework keeps this registry and its
 * shared parts, and each species mod signs up its own ({@link #register}).
 *
 * <p>A part's row also says what colony news it hears ({@link ColonyPart#hears}, step 4): the places of each kind of
 * news are its listeners' order (today's order, which the tellers used to call the parts in by hand). And what colony
 * questions it answers ({@link ColonyPart#answers}, steps 6 and 7): "keep off?" is asked of the neighbours, the closed
 * holes, the danger map and the trunk trails in that order (the order the danger map used to ask them in).
 *
 * <p>Which parts each species lists was worked out from each part's own "do I apply?" check (step 2): a part is only
 * listed for a species it does something for today. Until step 3 ("ask before use") every colony still makes every
 * part, so the old getters ({@link Colony#mound()}, ...) keep working; a part its species does not list is never ticked,
 * saved or loaded.
 */
public final class ColonyParts {
    /** One kind of part: its name (as in a species file, a save's log lines and the tests), whether every colony has it, and how it is made. */
    public static final class Type<T> {
        private final String name;
        private final boolean shared;
        private final Function<Colony, T> make;
        private final UnaryOperator<ColonyPart<T>> rows;

        private Type(String name, boolean shared, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
            this.name = name;
            this.shared = shared;
            this.make = make;
            this.rows = rows;
        }

        public String name() {
            return name;
        }

        /** Whether every colony has it (not listed in the species files). */
        public boolean shared() {
            return shared;
        }

        /** {@code part} as a part of this kind (it was made by this kind: see {@link Colony#part}). */
        @SuppressWarnings("unchecked")
        T cast(Object part) {
            return (T) part;
        }

        /** A new part of this kind for {@code colony}, with its ticks, save and load. */
        ColonyPart<T> make(Colony colony) {
            return rows.apply(ColonyPart.of(name, make.apply(colony)));
        }
    }

    private static final Map<String, Type<?>> TYPES = new LinkedHashMap<>();

    public static final Type<Nest> NEST = shared("nest", Nest::new, part -> part
            .ticks(90, (nest, level) -> nest.galleries().tick(level))
            .ticksAway(20, (nest, level) -> nest.galleries().tickAway(level))
            .saves(Nest::save, Nest::load));
    public static final Type<NestWalls> NEST_WALLS = shared("nest_walls", NestWalls::new, part -> part
            .notSaved("the claims on leaks and how far each is sealed start again after a load"));
    public static final Type<Hill> HILL = listed("hill", Hill::new, part -> part
            .ticks(40, (hill, level) -> {
                if (!hill.colony().buildsMound()) {
                    hill.nightSoil().tick(level);
                }
            })
            .saves(Hill::save, Hill::load)
            .sample((hill, level) -> hill.nightSoil().fillForTests(level)));
    public static final Type<FoodStore> FOOD_STORE = shared("food_store", FoodStore::new, part -> part
            .ticks(110, (food, level) -> food.barePatches().tick(level))
            .saves(FoodStore::save, FoodStore::load));
    public static final Type<FoodMap> FOOD_MAP = shared("food_map", FoodMap::new, part -> part
            .ticks(70, FoodMap::tick)
            .saves(FoodMap::save, FoodMap::load)
            .sample((map, level) -> map.learn(new BlockPos(5, 60, 3))));
    public static final Type<Bakery> BAKERY = listed("bakery", Bakery::new, part -> part
            .hears(ColonyNews.PIECE_TAKEN, 10, (bakery, level, news) -> {
                if (news.rich()) {
                    bakery.addRichMiller(news.ant()); // its miller makes rich bread
                }
            })
            .hears(ColonyNews.PIECE_EATEN, 10, (bakery, level, news) -> bakery.removeRichMiller(news.ant())) // eaten, not milled
            .answers(ColonyQuestions.MORE_FOOD_WANTED, 10, (bakery, level, nothing) -> bakery.needsBread((ServerLevel) level)) // bread still to mill
            .saves(Bakery::save, Bakery::load)
            .sample((bakery, level) -> {
                bakery.countIn(true);
                bakery.countIn(false);
            }));
    public static final Type<QueenCare> QUEEN_CARE = shared("queen_care", QueenCare::new, part -> part
            .saves(QueenCare::save, QueenCare::load));
    public static final Type<KingCare> KING_CARE = listed("king_care", KingCare::new, part -> part
            .saves(KingCare::save, KingCare::load));
    public static final Type<Brood> BROOD = shared("brood", Brood::new, part -> part
            .saves(Brood::save, Brood::load));
    public static final Type<Refuse> REFUSE = shared("refuse", Refuse::new, part -> part
            .notSaved("only the workers' claims on dead ants"));
    public static final Type<Mound> MOUND = listed("mound", Mound::new, part -> part
            .ticks(30, Mound::tick)
            .hears(ColonyNews.BLOCK_DUG, 10, (mound, level, news) -> mound.blockDug(level, news.where()))
            .answers(ColonyQuestions.BUILDS_OVER_NEST, 10, (mound, level, nothing) -> mound.numbers() != null)
            .answers(ColonyQuestions.BUILDING_OVER_NEST, 10, (mound, level, nothing) -> mound.isActive())
            .answers(ColonyQuestions.CLEAR_JOB, 10, Mound::isClearJob) // its own soil where it must be hollow
            .answers(ColonyQuestions.STRUCTURE_TUNNEL, 10, (mound, level, pos) -> mound.getGalleries(level).contains(pos))
            .saves(Mound::save, Mound::load)
            .sample(Mound::fillForTests));
    public static final Type<ForagingHoles> FORAGING_HOLES = shared("foraging_holes", ForagingHoles::new, part -> part
            .ticks(80, ForagingHoles::tick)
            .notSaved("claims and short memories, worked out again after a load"));
    public static final Type<ForagingTrails> FORAGING_TRAILS = shared("foraging_trails", ForagingTrails::new, part -> part
            .saves(ForagingTrails::save, ForagingTrails::load)
            .sample(ForagingTrails::fillForTests));
    public static final Type<TrunkTrails> TRUNK_TRAILS = listed("trunk_trails", TrunkTrails::new, part -> part
            .ticks(210, (trunk, level) -> trunk.tick(level, false))
            .ticksAway(80, (trunk, level) -> trunk.tick(level, true))
            .answers(ColonyQuestions.KEEP_OFF, 40, TrunkTrails::keepsOff) // the trail out there passes danger
            .answers(ColonyQuestions.KEEPS_TRAILS, 10, (trunk, level, nothing) -> trunk.keepsTrails())
            .hears(ColonyNews.NUMBERS_TRIP, 10, (trunk, level, news) -> trunk.tripByNumbers(level, news.plant())) // their trip out and back wears the trail too
            .saves(TrunkTrails::save, TrunkTrails::load)
            .sample(TrunkTrails::fillForTests));
    public static final Type<Patrol> PATROL = listed("patrol", Patrol::new, part -> part
            .ticks(140, (patrol, level) -> patrol.tick(level, false))
            .ticksAway(50, (patrol, level) -> patrol.tick(level, true))
            .hears(ColonyNews.MEMBER_HURT, 20, (patrol, level, news) -> patrol.danger(level, news.member(), "hurt by " + news.by().getType().toShortString())) // a patroller turns back
            .hears(ColonyNews.MEMBER_DIED, 10, (patrol, level, news) -> patrol.died(level, news.member())) // a patroller killed never comes back: its way stays shut today
            .answers(ColonyQuestions.NOT_CLEARED, 10, (patrol, level, pos) -> !patrol.isOpen(level, pos)) // no patroller back from that way yet
            .saves(Patrol::save, Patrol::load)
            .sample(Patrol::fillForTests));
    public static final Type<ForagerSpots> FORAGER_SPOTS = listed("forager_spots", ForagerSpots::new, part -> part
            .hears(ColonyNews.MEMBER_HURT, 40, (spots, level, news) -> spots.memberHurt(news.member().getUUID(), news.where())) // hurt near its spot: gives it up
            .hears(ColonyNews.MEMBER_DIED, 20, (spots, level, news) -> spots.died(news.member())) // its spot is lost with it
            .hears(ColonyNews.FOOD_FOUND, 10, (spots, level, news) -> spots.found(news.finder(), news.where())) // its spot now (site fidelity, species that search alone)
            .hears(ColonyNews.INTO_NUMBERS, 10, (spots, level, news) -> spots.intoNumbers(news.member())) // its spot goes into the numbers' pool
            .hears(ColonyNews.OUT_OF_NUMBERS, 10, (spots, level, news) -> spots.outOfNumbers(news.member())) // a forager takes a spot from the numbers' pool
            .hears(ColonyNews.FORAGER_NUMBERS, 10, (spots, level, news) -> spots.trimHeld(news.foragers())) // fewer forager numbers than spots held: some died or came out as real ones
            .saves(ForagerSpots::save, ForagerSpots::load)
            .sample(ForagerSpots::fillForTests));
    public static final Type<Door> DOOR = listed("door", Door::new, part -> part
            .ticks(150, Door::tick)
            .hears(ColonyNews.NUMBERS_FORAGED, 10, (door, level, news) -> door.numbersSecond(level, news.foragers(), news.returned(), news.placed()))
            .saves(Door::save, Door::load)
            .sample(Door::fillForTests));
    public static final Type<Recruits> RECRUITS = listed("recruits", Recruits::new, part -> part
            .ticks(160, Recruits::tick)
            .hears(ColonyNews.MEMBER_DIED, 30, (recruits, level, news) -> recruits.died(news.member()))
            .hears(ColonyNews.FOOD_FOUND, 20, (recruits, level, news) -> recruits.found(level, news.finder(), news.where())) // a rich patch: it lays a scent line home and calls nestmates out
            .notSaved("a scent line fades within minutes"));
    public static final Type<DangerMap> DANGER_MAP = shared("danger_map", DangerMap::new, part -> part
            .ticks(170, DangerMap::tick)
            .hears(ColonyNews.MEMBER_HURT, 30, (danger, level, news) -> danger.hurt(level, news.where(), news.by().getType().toShortString(), news.killed())) // the place is remembered
            .answers(ColonyQuestions.KEEP_OFF, 30, DangerMap::keepsOff) // a dangerous place
            .saves(DangerMap::save, DangerMap::load)
            .sample(DangerMap::fillForTests));
    public static final Type<ClosedHoles> CLOSED_HOLES = listed("closed_holes", ClosedHoles::new, part -> part
            .ticks(180, ClosedHoles::tick)
            .answers(ColonyQuestions.KEEP_OFF, 20, ClosedHoles::keepsOff) // where members were attacked
            .answers(ColonyQuestions.HOLE_KEPT_SHUT, 10, ClosedHoles::isClosed) // closed for danger or left for good
            .answers(ColonyQuestions.ANY_HOLE_KEPT_SHUT, 10, (closed, level, nothing) -> closed.anyClosed(level))
            .answers(ColonyQuestions.NO_GALLERY_THROUGH, 10, ClosedHoles::keepsGalleriesOff) // not toward an attack
            .saves(ClosedHoles::save, ClosedHoles::load)
            .sample(ClosedHoles::fillForTests));
    public static final Type<Neighbours> NEIGHBOURS = shared("neighbours", Neighbours::new, part -> part
            .ticks(190, Neighbours::tick)
            .hears(ColonyNews.NEIGHBOUR_MET, 10, (neighbours, level, news) -> neighbours.met(level, news.where(), news.other(), news.who()))
            .answers(ColonyQuestions.KEEP_OFF, 10, Neighbours::yieldsAt) // past a place the colony yields to a neighbour
            .saves(Neighbours::save, Neighbours::load)
            .sample(Neighbours::fillForTests));
    public static final Type<HiddenTrips> HIDDEN_TRIPS = listed("hidden_trips", HiddenTrips::new, part -> part
            .ticks(100, HiddenTrips::tick)
            .ticksAway(10, HiddenTrips::tickAway)
            .saves(HiddenTrips::save, HiddenTrips::load)
            .sample(HiddenTrips::fillForTests));
    public static final Type<FungusGarden> FUNGUS_GARDEN = listed("fungus_garden", FungusGarden::new, part -> part
            .ticks(20, FungusGarden::grow)
            .hears(ColonyNews.HEAP_GONE, 10, (garden, level, news) -> garden.loseNodules(news.nodules())) // a comb eaten right down: its nodules go with it
            .answers(ColonyQuestions.NOTHING_TO_EAT_IN_STORE, 10, (garden, level, nothing) -> garden.oldKnown() <= 0) // no old layer as far as the brain knows
            .saves(FungusGarden::save, FungusGarden::load)
            .sample(FungusGarden::fillForTests));
    public static final Type<Mushrooms> MUSHROOMS = listed("mushrooms", Mushrooms::new, part -> part
            .ticks(50, Mushrooms::tick)
            .notSaved("only the weather at the last look and the last flush, for the colony list"));
    public static final Type<Soldiers> SOLDIERS = listed("soldiers", Soldiers::new, part -> part
            .notSaved("the soldiers simply take their posts again after a load"));
    public static final Type<Alarm> ALARM = listed("alarm", Alarm::new, part -> part
            .ticks(60, Alarm::tick)
            .hears(ColonyNews.MEMBER_HURT, 10, (alarm, level, news) -> alarm.memberHurt(level, news.by())) // level 2 at the attacker
            .answers(ColonyQuestions.KEEPS_EVERYONE_IN, 10, (alarm, level, nothing) -> alarm.keepsEveryoneIn(level)) // the foragers hurry home
            .saves(Alarm::save, Alarm::load)
            .sample(Alarm::fillForTests));
    public static final Type<Population> POPULATION = shared("population", Population::new, part -> part
            .ticks(120, Population::tick)
            .ticksAway(30, Population::tickAway)
            .saves(Population::save, Population::load));
    public static final Type<AwayLife> AWAY_LIFE = shared("away_life", AwayLife::new, part -> part
            .ticks(10, AwayLife::settle)
            .saves(AwayLife::save, AwayLife::load));
    public static final Type<NestLedger> NEST_LEDGER = shared("nest_ledger", NestLedger::new, part -> part
            .saves(NestLedger::save, NestLedger::load));
    public static final Type<NestLife> NEST_LIFE = shared("nest_life", NestLife::new, part -> part
            .ticks(130, (life, level) -> life.tick(level, false))
            .ticksAway(40, (life, level) -> life.tick(level, true))
            .saves(NestLife::save, NestLife::load));
    public static final Type<FieldLife> FIELD_LIFE = shared("field_life", FieldLife::new, part -> part
            .ticks(200, (field, level) -> field.tick(level, false))
            .ticksAway(60, (field, level) -> field.tick(level, true))
            .saves(FieldLife::save, FieldLife::load)
            .sample(FieldLife::fillForTests));
    public static final Type<BuildLife> BUILD_LIFE = shared("build_life", BuildLife::new, part -> part
            .ticksAway(70, BuildLife::tickAway)
            .saves(BuildLife::save, BuildLife::load));
    public static final Type<WorkerFood> WORKER_FOOD = shared("worker_food", WorkerFood::new, part -> part
            .saves(WorkerFood::save, WorkerFood::load)
            .sample(WorkerFood::fillForTests));

    private ColonyParts() {
    }

    private static <T> Type<T> shared(String name, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
        return register(name, true, make, rows);
    }

    private static <T> Type<T> listed(String name, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
        return register(name, false, make, rows);
    }

    /**
     * Signs up a kind of part: {@code shared} in every colony, or only in species that list {@code name}. Made and loaded
     * after the kinds signed up before it.
     */
    public static synchronized <T> Type<T> register(String name, boolean shared, Function<Colony, T> make, UnaryOperator<ColonyPart<T>> rows) {
        if (TYPES.containsKey(name)) {
            throw new IllegalStateException("two colony parts signed up as " + name);
        }
        Type<T> type = new Type<>(name, shared, make, rows);
        TYPES.put(name, type);
        return type;
    }

    /** Every kind of part, in the order they are made and loaded. */
    public static synchronized Collection<Type<?>> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(TYPES.values()));
    }

    public static synchronized Optional<Type<?>> byName(String name) {
        return Optional.ofNullable(TYPES.get(name));
    }

    /**
     * Checks a species file's {@code parts} list: every name signed up, none shared (every colony has those), none twice.
     * Throws with a clear message otherwise, so the species files stop loading instead of a colony quietly missing a part.
     */
    public static void checkSpecies(ResourceLocation species, List<String> names) {
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            Type<?> type = byName(name).orElse(null);
            if (type == null) {
                List<String> known = all().stream().filter(kind -> !kind.shared()).map(Type::name).toList();
                throw new IllegalStateException("The species file " + species + " lists the part \"" + name
                        + "\", which no mod has signed up. Parts a species can list: " + String.join(", ", known));
            }
            if (type.shared()) {
                throw new IllegalStateException("The species file " + species + " lists the part \"" + name
                        + "\", which every colony has already: take it out of \"parts\"");
            }
            if (!seen.add(name)) {
                throw new IllegalStateException("The species file " + species + " lists the part \"" + name + "\" twice");
            }
        }
    }
}
