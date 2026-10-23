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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Colony news (Project Apex, colony parts step 4, {@code docs/plans/apex.md}; the user's OK of 2026-10-20): how a colony's
 * parts hear what happened without the one telling knowing who listens. An insect hurt by a living thing tells its
 * colony's news once ("a member was hurt here"), and the alarm, the patrol, the danger memory and the forager spots each
 * hear it, if the colony has them; before, the insect named each of those parts itself.
 *
 * <p>The two rules agreed with the user: a listener is called only when its own kind of news comes (nobody checks every
 * tick), and news is only sent while someone listens ({@link #tell} does nothing for a kind with no listener, so a
 * species without those parts pays next to nothing).
 *
 * <p>A part says what it listens to in its row in {@link ColonyParts} ({@link ColonyPart#hears}), with a place: lower
 * places hear first, gaps of 10 as with the tick places, so the order is written down once and a new part slots in
 * between. After the split into three mods the framework holds this class and its kinds below; a species mod's parts sign
 * up to them the same way, and a species mod may make kinds of its own ({@link Kind#of}) for its parts to share.
 */
public final class ColonyNews {
    /** One kind of news, carrying a {@code N} (one of the records below, or a species mod's own). */
    public static final class Kind<N> {
        private final String name;

        private Kind(String name) {
            this.name = name;
        }

        /** A new kind of news called {@code name} (for the logs and the error messages). */
        public static <N> Kind<N> of(String name) {
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

    /** What a listener does with its news. */
    @FunctionalInterface
    public interface Listener<N> {
        void hear(ServerLevel level, N news);
    }

    // --- the kinds every colony may hear (the framework's) ---------------------------------------------------------------

    /** A member was hurt by a living thing not of its colony (a mob, a player, an insect of another colony); {@code killed}: it died of it. */
    public record MemberHurt(AbstractAntEntity member, BlockPos where, LivingEntity by, boolean killed) {
    }

    /** A member died (of anything): it is gone from the colony. Today only worker ants tell it (as before: nothing else listened for the others). */
    public record MemberDied(UUID member) {
    }

    /** A forager found food at {@code where}: it cut a piece off a plant there or picked up a seed. */
    public record FoodFound(UUID finder, BlockPos where) {
    }

    /**
     * A member met another colony of its species at {@code where}: {@code who} is what was met there (one of their insects,
     * their trail, their gallery), so the same meeting is not counted again and again.
     */
    public record NeighbourMet(BlockPos where, UUID other, UUID who) {
    }

    public static final Kind<MemberHurt> MEMBER_HURT = Kind.of("member_hurt");
    public static final Kind<MemberDied> MEMBER_DIED = Kind.of("member_died");
    public static final Kind<FoodFound> FOOD_FOUND = Kind.of("food_found");
    public static final Kind<NeighbourMet> NEIGHBOUR_MET = Kind.of("neighbour_met");

    // Step 8a: the rest of the shared code's tellings to a species part, each now told to whoever hears it.

    /** A real member went into the numbers (it is counted inside the nest now, no longer in the world). */
    public record IntoNumbers(UUID member) {
    }

    /** A member came out of the numbers as a real forager. */
    public record OutOfNumbers(UUID member) {
    }

    /** A piece was taken off the store for milling: {@code rich}, a rich seed (its miller makes rich bread). */
    public record PieceTaken(UUID ant, boolean rich) {
    }

    /** A worker ate the piece it took off the store (so it mills nothing). */
    public record PieceEaten(UUID ant) {
    }

    /** A heap of the store was eaten away to nothing; {@code nodules}: the fungus nodules it still carried, lost with it. */
    public record HeapGone(BlockPos where, int nodules) {
    }

    /** The forager numbers brought a piece home from the plant at {@code plant} (their trip out and back). */
    public record NumbersTrip(BlockPos plant) {
    }

    /** The forager numbers' second: {@code foragers} of them, {@code returned} pieces home, {@code placed} at work on a plant or searching. */
    public record NumbersForaged(int foragers, int returned, double placed) {
    }

    /** How many forager numbers there are this second (before they go out; a species that searches alone trims its spots to them). */
    public record ForagerNumbers(int foragers) {
    }

    /** A block of the nest was dug out (by a real digger or by the builder numbers on return). */
    public record BlockDug(BlockPos where) {
    }

    public static final Kind<IntoNumbers> INTO_NUMBERS = Kind.of("into_numbers");
    public static final Kind<OutOfNumbers> OUT_OF_NUMBERS = Kind.of("out_of_numbers");
    public static final Kind<PieceTaken> PIECE_TAKEN = Kind.of("piece_taken");
    public static final Kind<PieceEaten> PIECE_EATEN = Kind.of("piece_eaten");
    public static final Kind<HeapGone> HEAP_GONE = Kind.of("heap_gone");
    public static final Kind<NumbersTrip> NUMBERS_TRIP = Kind.of("numbers_trip");
    public static final Kind<NumbersForaged> NUMBERS_FORAGED = Kind.of("numbers_foraged");
    public static final Kind<ForagerNumbers> FORAGER_NUMBERS = Kind.of("forager_numbers");
    public static final Kind<BlockDug> BLOCK_DUG = Kind.of("block_dug");

    // Step 9a: the shared goals', the ant body's and the event file's tellings to a species part.

    /** A real forager sets out for food it found and claimed. */
    public record SetOut(AbstractAntEntity ant) {
    }

    /** A real forager sets out to search (searching alone). */
    public record SearchStarted(UUID ant) {
    }

    /**
     * A real forager's search is over (it found food, or not): {@code empty}, it got to its search and came home with
     * nothing, the search having gone out at {@code angle} (radians from the nest's middle); {@code gaveUp}, it searched
     * as long as it may.
     */
    public record SearchOver(AbstractAntEntity ant, boolean empty, double angle, boolean gaveUp) {
    }

    /** A member's look round, every few ticks (a forager walking in with food touches the ants waiting by the entrance). */
    public record MemberRound(AbstractAntEntity member) {
    }

    /** A player jumped (the ground shook) somewhere on the colony's ground. */
    public record PlayerStamped(Player player) {
    }

    /** A block was broken at {@code where} on the colony's ground, by {@code breaker} (may be null). */
    public record BlockBroken(BlockPos where, @Nullable Player breaker) {
    }

    public static final Kind<SetOut> SET_OUT = Kind.of("set_out");
    public static final Kind<SearchStarted> SEARCH_STARTED = Kind.of("search_started");
    public static final Kind<SearchOver> SEARCH_OVER = Kind.of("search_over");
    public static final Kind<MemberRound> MEMBER_ROUND = Kind.of("member_round");
    public static final Kind<PlayerStamped> PLAYER_STAMPED = Kind.of("player_stamped");
    public static final Kind<BlockBroken> BLOCK_BROKEN = Kind.of("block_broken");

    // --- listening and telling -----------------------------------------------------------------------------------------

    private record Signed(int place, String who, Listener<?> listener) {
    }

    private static final Signed[] NOBODY = new Signed[0];

    /** The listeners of each kind, in their place order (a new array on each sign-up, so news told while one signs up is safe). */
    private final Map<Kind<?>, Signed[]> listeners = new IdentityHashMap<>();

    /**
     * {@code who} (a part's name, for the error message) hears every {@code kind} of news from now on, at {@code place}
     * among that kind's listeners. Two listeners of one kind at one place is a mistake (their order would be a guess).
     */
    public synchronized <N> void listen(Kind<N> kind, int place, String who, Listener<N> listener) {
        Signed[] now = listeners.getOrDefault(kind, NOBODY);
        for (Signed signed : now) {
            if (signed.place == place) {
                throw new IllegalStateException(who + " and " + signed.who + " both hear " + kind + " at place " + place);
            }
        }
        List<Signed> more = new ArrayList<>(Arrays.asList(now));
        more.add(new Signed(place, who, listener));
        more.sort(Comparator.comparingInt(Signed::place));
        listeners.put(kind, more.toArray(NOBODY));
    }

    /** Whether anyone listens to this kind (a teller with costly news to make can ask first). */
    public boolean listens(Kind<?> kind) {
        return listeners.containsKey(kind);
    }

    /** Tells every listener of this kind, in place order; nothing at all if nobody listens. */
    @SuppressWarnings("unchecked")
    public <N> void tell(ServerLevel level, Kind<N> kind, N news) {
        Signed[] all = listeners.get(kind);
        if (all == null) {
            return;
        }
        for (Signed signed : all) {
            ((Listener<N>) signed.listener).hear(level, news);
        }
    }

    /** For the tests and the logs: the listeners of a kind by name, in the order they hear it. */
    public List<String> listenersOf(Kind<?> kind) {
        return Arrays.stream(listeners.getOrDefault(kind, NOBODY)).map(Signed::who).toList();
    }
}
