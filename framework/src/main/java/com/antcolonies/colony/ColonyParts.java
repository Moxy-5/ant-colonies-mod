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
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;

/**
 * The parts registry (Project Apex, colony parts step 2, {@code docs/plans/apex.md}): every kind of colony part by its
 * name, with how to make a colony's own. A <b>shared</b> part is in every colony (the nest, the food store, the brood,
 * ...); any other part is in a colony only when its species file lists it under {@code "parts"} (the termite mound, the
 * harvester's bakery, ...). A name in a species file that nothing signed up stops the species files loading, with the
 * known names in the message ({@link #checkSpecies}).
 *
 * <p>The shared kinds are signed up here, then each species side signs up its own ({@link #register}: the savanna's in
 * {@code SavannaParts}, the desert's in {@code DesertParts}, the split, step S3), in the order they are made and loaded;
 * the order a part ticks in is its place, written beside it (see {@link ColonyPart}), so it does not depend on which
 * file signed a part up.
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
            .saves(Nest::save, Nest::load));
    public static final Type<NestWalls> NEST_WALLS = shared("nest_walls", NestWalls::new, part -> part
            .notSaved("the claims on leaks and how far each is sealed start again after a load"));
    public static final Type<FoodStore> FOOD_STORE = shared("food_store", FoodStore::new, part -> part
            .saves(FoodStore::save, FoodStore::load));
    public static final Type<FoodMap> FOOD_MAP = shared("food_map", FoodMap::new, part -> part
            .ticks(70, FoodMap::tick)
            .saves(FoodMap::save, FoodMap::load)
            .sample((map, level) -> map.learn(new BlockPos(5, 60, 3))));
    public static final Type<QueenCare> QUEEN_CARE = shared("queen_care", QueenCare::new, part -> part
            .saves(QueenCare::save, QueenCare::load));
    public static final Type<Brood> BROOD = shared("brood", Brood::new, part -> part
            .saves(Brood::save, Brood::load));
    public static final Type<Refuse> REFUSE = shared("refuse", Refuse::new, part -> part
            .notSaved("only the workers' claims on dead ants"));
    public static final Type<ForagingHoles> FORAGING_HOLES = shared("foraging_holes", ForagingHoles::new, part -> part
            .ticks(80, ForagingHoles::tick)
            .notSaved("claims and short memories, worked out again after a load"));
    public static final Type<ForagingTrails> FORAGING_TRAILS = shared("foraging_trails", ForagingTrails::new, part -> part
            .saves(ForagingTrails::save, ForagingTrails::load)
            .sample(ForagingTrails::fillForTests));
    public static final Type<DangerMap> DANGER_MAP = shared("danger_map", DangerMap::new, part -> part
            .ticks(170, DangerMap::tick)
            .hears(ColonyNews.MEMBER_HURT, 30, (danger, level, news) -> danger.hurt(level, news.where(), news.by().getType().toShortString(), news.killed(), news.member().getUUID())) // the place is remembered (each member once an attack)
            .answers(ColonyQuestions.KEEP_OFF, 30, DangerMap::keepsOff) // a dangerous place
            .saves(DangerMap::save, DangerMap::load)
            .sample(DangerMap::fillForTests));
    public static final Type<Neighbours> NEIGHBOURS = shared("neighbours", Neighbours::new, part -> part
            .ticks(190, Neighbours::tick)
            .hears(ColonyNews.NEIGHBOUR_MET, 10, (neighbours, level, news) -> neighbours.met(level, news.where(), news.other(), news.who()))
            .answers(ColonyQuestions.KEEP_OFF, 10, Neighbours::yieldsAt) // past a place the colony yields to a neighbour
            .saves(Neighbours::save, Neighbours::load)
            .sample(Neighbours::fillForTests));
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
