package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AbstractAntEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Colony questions (Project Apex, colony parts steps 6 and 7, {@code docs/plans/apex.md}; the user's OK of 2026-10-22):
 * yes/no questions a colony asks of its parts without naming them, the way {@link ColonyNews} tells its parts what
 * happened. "May we forage here?" used to be answered by the danger map asking the neighbours, the termites' closed
 * holes and the harvester's trunk trails by name, and by the colony asking the harvester's patrol; now each of those
 * parts answers for itself, and a colony without the part simply has no one saying yes.
 *
 * <p>A question is "yes" when any part answering it says yes; they are asked in place order (lower first, gaps of 10, as
 * with the news) and the first yes ends it, so the cheap answers go first. With nobody answering it is "no", at next to
 * no cost. Answers must only look, never change anything: a question may be asked any number of times, in any order.
 *
 * <p>A part says what it answers in its row in {@link ColonyParts} ({@link ColonyPart#answers}). After the split into
 * three mods the framework holds this class and its kinds below; a species mod's parts answer them the same way, and a
 * species mod may make kinds of its own ({@link Kind#of}).
 */
public final class ColonyQuestions {
    /** One kind of question, about a {@code Q} (a place, a hole, or nothing for a question about the whole colony). */
    public static final class Kind<Q> {
        private final String name;

        private Kind(String name) {
            this.name = name;
        }

        /** A new kind of question called {@code name} (for the logs and the error messages). */
        public static <Q> Kind<Q> of(String name) {
            return new Kind<>(name);
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** How a part answers: yes or no, only looking. */
    @FunctionalInterface
    public interface Answerer<Q> {
        boolean yes(Level level, Q asked);
    }

    // --- the kinds every colony may be asked (the framework's) ---------------------------------------------------------

    /**
     * Step 6: whether the colony keeps its foragers off this place for a lasting reason (danger, a neighbour it yields to,
     * where members were attacked). Asked wherever food is chosen, from outside or from inside, by real insects and by the
     * numbers.
     */
    public static final Kind<BlockPos> KEEP_OFF = Kind.of("keep_off");
    /** Step 6: whether this place is not cleared for foraging yet today (no patroller back from that way). */
    public static final Kind<BlockPos> NOT_CLEARED = Kind.of("not_cleared");
    /** Step 7: whether this foraging hole is kept shut (never opened, plugged at once, no turn, no food fetched through it). */
    public static final Kind<BlockPos> HOLE_KEPT_SHUT = Kind.of("hole_kept_shut");
    /** Step 7: whether any foraging hole is kept shut now (a quick check before asking of each); asked about nothing (null). */
    public static final Kind<Void> ANY_HOLE_KEPT_SHUT = Kind.of("any_hole_kept_shut");
    /** Step 7: whether no gallery may grow through this cell now. */
    public static final Kind<BlockPos> NO_GALLERY_THROUGH = Kind.of("no_gallery_through");

    // Step 8a: the rest of the shared code's yes/no asks of a species part, each now asked of whoever answers it.

    /** Step 8a: whether everyone is kept in the nest now (an alarm at an attacker: the foragers hurry home); asked about nothing (null). */
    public static final Kind<Void> KEEPS_EVERYONE_IN = Kind.of("keeps_everyone_in");
    /** Step 8a: whether the colony builds a structure over its nest (a termite mound); asked about nothing (null). */
    public static final Kind<Void> BUILDS_OVER_NEST = Kind.of("builds_over_nest");
    /** Step 8a: whether that structure is being built now (the species builds one and the colony is past founding); asked about nothing (null). */
    public static final Kind<Void> BUILDING_OVER_NEST = Kind.of("building_over_nest");
    /** Step 8a: whether the builders want this block cleared out (the structure's own soil where it must be hollow). */
    public static final Kind<BlockPos> CLEAR_JOB = Kind.of("clear_job");
    /** Step 8a: whether this cell is one of the tunnels built inside the structure over the nest (it needs no walls of its own). */
    public static final Kind<BlockPos> STRUCTURE_TUNNEL = Kind.of("structure_tunnel");
    /** Step 8a: whether more food is wanted in even with the store full (seeds to mill into bread); asked about nothing (null). */
    public static final Kind<Void> MORE_FOOD_WANTED = Kind.of("more_food_wanted");
    /** Step 8a: whether the colony keeps lasting trails (trunk trails); asked about nothing (null). */
    public static final Kind<Void> KEEPS_TRAILS = Kind.of("keeps_trails");
    /** Step 8a: whether the store is known to hold nothing the workers eat (fungus comb with no old layer as far as the brain knows); asked about nothing (null). */
    public static final Kind<Void> NOTHING_TO_EAT_IN_STORE = Kind.of("nothing_to_eat_in_store");

    // Step 9a: the shared goals' and the ant body's yes/no asks of a species part.

    /** Food a forager might go for, and the forager. */
    public record FoodFor(UUID ant, BlockPos food) {
    }

    /** Step 9a: whether this forager must wait in the nest before it sets out (the harvester's door: not touched enough yet). */
    public static final Kind<AbstractAntEntity> MUST_WAIT = Kind.of("must_wait");
    /** Step 9a: whether this forager passes the food by (it was called out to a richer patch and is on its way there). */
    public static final Kind<FoodFor> PASSES_BY = Kind.of("passes_by");
    /** Step 9a: whether the colony is alarmed now (no grooming, napping or stopping to look at a player); asked about nothing (null). */
    public static final Kind<Void> ALARMED = Kind.of("alarmed");
    /** Step 9a: whether this place is on one of the colony's lasting trails (a neighbour stepping onto it meets the colony). */
    public static final Kind<BlockPos> ON_TRAIL = Kind.of("on_trail");

    /** A member of the colony, and something it might attack. */
    public record Provoked(AbstractAntEntity ant, net.minecraft.world.entity.Entity foe) {
    }

    /**
     * M8 step 2e: whether this member may attack this foe because it provoked the colony (a player who broke into the
     * nest, hit a member, stood in a column or stayed on the entrance), the nest's defence ({@code fight.NestDefence}).
     */
    public static final Kind<Provoked> PROVOKED_BY = Kind.of("provoked_by");

    // --- answering and asking ------------------------------------------------------------------------------------------

    private record Signed(int place, String who, Answerer<?> answerer) {
    }

    private static final Signed[] NOBODY = new Signed[0];

    /** The answerers of each kind, in their place order. */
    private final Map<Kind<?>, Signed[]> answerers = new IdentityHashMap<>();

    /**
     * {@code who} (a part's name) answers every {@code kind} of question from now on, at {@code place} among that kind's
     * answerers. Two answerers of one kind at one place is a mistake (their order would be a guess).
     */
    public synchronized <Q> void answer(Kind<Q> kind, int place, String who, Answerer<Q> answerer) {
        Signed[] now = answerers.getOrDefault(kind, NOBODY);
        for (Signed signed : now) {
            if (signed.place == place) {
                throw new IllegalStateException(who + " and " + signed.who + " both answer " + kind + " at place " + place);
            }
        }
        List<Signed> more = new ArrayList<>(Arrays.asList(now));
        more.add(new Signed(place, who, answerer));
        more.sort(Comparator.comparingInt(Signed::place));
        answerers.put(kind, more.toArray(NOBODY));
    }

    /** Whether anyone answers this kind (an asker with a costly loop can skip it when nobody could say yes). */
    public boolean answers(Kind<?> kind) {
        return answerers.containsKey(kind);
    }

    /** Whether any part says yes, asked in place order (the first yes ends it); no when nobody answers. */
    public <Q> boolean ask(Level level, Kind<Q> kind, Q asked) {
        return whoSaysYes(level, kind, asked) != null;
    }

    /** The name of the first part that says yes, or null (for the test messages and the logs). */
    @Nullable
    @SuppressWarnings("unchecked")
    public <Q> String whoSaysYes(Level level, Kind<Q> kind, Q asked) {
        Signed[] all = answerers.get(kind);
        if (all == null) {
            return null;
        }
        for (Signed signed : all) {
            if (((Answerer<Q>) signed.answerer).yes(level, asked)) {
                return signed.who;
            }
        }
        return null;
    }

    /** For the tests and the logs: the answerers of a kind by name, in the order they are asked. */
    public List<String> answerersOf(Kind<?> kind) {
        return Arrays.stream(answerers.getOrDefault(kind, NOBODY)).map(Signed::who).toList();
    }
}
