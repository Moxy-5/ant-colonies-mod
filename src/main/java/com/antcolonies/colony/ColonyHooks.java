package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.TermiteSoldierEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Colony hooks (Project Apex, colony parts step 8b, {@code docs/plans/apex.md}; the user's OK of 2026-10-22): sockets in
 * the shared code that a species part plugs its own answer or work into, for what is more than a yes/no
 * ({@link ColonyQuestions}) or a piece of news ({@link ColonyNews}): how far the colony reaches, how many foragers may go
 * out, the structure over the nest. The shared code asks {@link #all} (or {@link #first}) of a kind and folds what comes
 * back in its own way, with today's answer when nothing is plugged in (a colony without the part).
 *
 * <p>A part says what it plugs in in its row in {@link ColonyParts} ({@link ColonyPart#plugs}), with a place (lower
 * first, gaps of 10, as with the news). After the split into three mods the framework holds this class and its kinds; a
 * species mod's parts plug into them the same way, and a species mod may make kinds of its own ({@link Kind#of}).
 */
public final class ColonyHooks {
    /** One kind of socket, taking a {@code H} (one of the shapes below, or a species mod's own). */
    public static final class Kind<H> {
        private final String name;

        private Kind(String name) {
            this.name = name;
        }

        /** A new kind of socket called {@code name} (for the logs and the error messages). */
        public static <H> Kind<H> of(String name) {
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

    // --- the shapes of what is plugged in ------------------------------------------------------------------------------

    /** How far out (blocks, sideways from the nest's middle) a part takes the colony's foraging: 0 for not past the food radius. */
    @FunctionalInterface
    public interface Reach {
        int reach();
    }

    /** Lasting trails the colony walks: how long the walk to a plant is by trail, and which columns along them to scan for plants. */
    public interface Trails {
        /** The walk (blocks) to {@code pos} along a trail, or a negative number if no trail leads near it. */
        double walkTo(BlockPos pos);

        /** The columns (x and z from the nest's middle) within {@code side} of the trails, out to {@code radius}. */
        List<int[]> columnsNear(int side, int radius);
    }

    /** How many of the forager numbers may be out now (of {@code foragers}; Gordon's door rule for the harvester ant). */
    @FunctionalInterface
    public interface DoorRule {
        double free(ServerLevel level, int foragers);
    }

    /** How many founders dig beside the queen (the king, in a royal pair). */
    @FunctionalInterface
    public interface CoFounders {
        int count();
    }

    /** What castes a young one grows into besides a worker (soldiers): its meal, and the caste wanted for a new one. */
    public interface Castes {
        /** How much a young one growing into this kind of soldier eats before it grows up. */
        int mealFor(@Nullable TermiteSoldierEntity.Rank rank);

        /** The kind of soldier a new young one should grow into, or null (soldiers enough). */
        @Nullable
        TermiteSoldierEntity.Rank wanted(Level level);

        /**
         * How many real mobs of these castes the colony wants now (of a real cap of {@code cap}; {@code watched}: a player
         * near), put into {@code wanted} by caste before the workers; returns how many in all (the workers get the rest).
         */
        int wantReal(ServerLevel level, boolean watched, int cap, Map<String, Integer> wanted);

        /** For the colony list: how many of these castes are numbers in the nest ("; soldiers 3 minor, 1 major"), or "". */
        String inside(Population population);
    }

    /** Insects out of the world for a while on hidden trips (they are real ones, coming back). */
    public interface HiddenAway {
        /** How many of this caste are away on a hidden trip. */
        int travelling(String caste);

        /** Whether any of them walks through one of these cells on its way (never wall it in). */
        boolean anyWalksThrough(Set<BlockPos> cells);
    }

    /** The structure built over the nest (the termite mound): its blocks, its tunnels and its next block to dig. */
    public interface Structure {
        /** Its blocks and what each is. */
        Map<BlockPos, MoundPlan.Part> parts(Level level);

        /** Its tunnels, dug as part of the nest. */
        List<BlockPos> tunnels(Level level);

        /** Its next block for a digger to clear, or null. */
        @Nullable
        BlockPos digTarget(Level level, @Nullable UUID digger);
    }

    /** A food garden in the nest whose blocks age (the fungus comb): its blocks as known now. */
    @FunctionalInterface
    public interface Garden {
        List<BlockPos> beds(Level level);
    }

    /**
     * Step 8c: a species' share of the building on return ({@link BuildLife}, out of every player's sight, with the
     * builders' time from while away): work before the nest is dug further, where each dug block's soil goes, and work
     * once the nest is dug as far as it may be.
     */
    public interface AwayBuilder {
        /** Before the nest is dug further (the harvester's night soil carried up first). True if some still waits. */
        default boolean first(ServerLevel level, BuildLife build) {
            return false;
        }

        /** Before a block of the nest is dug: false to wait (where its soil goes can be seen now, or is in someone's way). */
        default boolean readyToDig(ServerLevel level, BuildLife build, BlockPos target) {
            return true;
        }

        /** The block was dug with {@code bites} bites: its soil goes where this species takes it (the hill, the mound's soil). */
        default void dug(ServerLevel level, BuildLife build, BlockPos target, int bites) {
        }

        /** Once the nest is dug as far as it may be (the termite mound built up). True if some is left. */
        default boolean after(ServerLevel level, BuildLife build) {
            return false;
        }
    }

    /** Step 8c: a species' own way for its forager numbers to forage, when it searches alone (the harvester ant: brain step 4c). */
    @FunctionalInterface
    public interface NumbersSearch {
        /** This second's foraging of {@code foragers} numbers, through {@code field}'s plants and trip rules. */
        void forage(ServerLevel level, FieldLife field, int foragers);
    }

    /** Step 8c: a species' own share of the nest's life while the colony is away, once a second. True if anything changed. */
    @FunctionalInterface
    public interface AwayWork {
        boolean work(ServerLevel level);
    }

    /** Step 8c: one more job for the nurses while away (see {@link NestLife}). True if it was wanted now and done. */
    @FunctionalInterface
    public interface NurseJob {
        boolean job(ServerLevel level, NestLife life);
    }

    // --- the kinds every colony may plug into (the framework's) ---------------------------------------------------------

    /** Step 8b: how far out the colony forages, past its food radius (the harvester's trunk trails: their longest tip). */
    public static final Kind<Reach> REACH = Kind.of("reach");
    /** Step 8b: the colony's lasting trails (the harvester's trunk trails). */
    public static final Kind<Trails> TRAILS = Kind.of("trails");
    /** Step 8b: how many forager numbers may be out (the harvester's door). */
    public static final Kind<DoorRule> DOOR_RULE = Kind.of("door_rule");
    /** Step 8b: the founders beside the queen (the termite king). */
    public static final Kind<CoFounders> CO_FOUNDERS = Kind.of("co_founders");
    /** Step 8b: the nurses' food, bread or fungus nodules (the first plugged in is the colony's, {@link Colony#nurseFood}). */
    public static final Kind<NurseFood> NURSE_FOOD = Kind.of("nurse_food");
    /** Step 8b: the castes the young grow into besides workers (the termite soldiers). */
    public static final Kind<Castes> CASTES = Kind.of("castes");
    /** Step 8b: insects away on hidden trips (the termites' galleries). */
    public static final Kind<HiddenAway> HIDDEN_AWAY = Kind.of("hidden_away");
    /** Step 8b: the structure over the nest (the termite mound). */
    public static final Kind<Structure> STRUCTURE = Kind.of("structure");
    /** Step 8b: a food garden whose blocks age (the fungus comb). */
    public static final Kind<Garden> GARDEN = Kind.of("garden");
    /** Step 8c: the species' share of the building on return (the harvester's hill, the termite mound). */
    public static final Kind<AwayBuilder> AWAY_BUILDER = Kind.of("away_builder");
    /** Step 8c: the forager numbers searching alone (the harvester's forager spots). */
    public static final Kind<NumbersSearch> NUMBERS_SEARCH = Kind.of("numbers_search");
    /** Step 8c: while away, beside the queen's own life (the termite king's hunger clock). */
    public static final Kind<AwayWork> AWAY_BESIDE_QUEEN = Kind.of("away_beside_queen");
    /** Step 8c: while away, after the nurses' work (the fungus garden growing nodules and ageing). */
    public static final Kind<AwayWork> AWAY_AFTER_NURSES = Kind.of("away_after_nurses");
    /** Step 8c: the nurses feeding another member while away, after the queen and before the young (the termite king). */
    public static final Kind<NurseJob> NURSE_FEEDS = Kind.of("nurse_feeds");
    /** Step 8c: the nurses making more food while away, once nobody is hungry (the harvester's bread milled). */
    public static final Kind<NurseJob> NURSE_MAKES = Kind.of("nurse_makes");

    // --- plugging in and asking ----------------------------------------------------------------------------------------

    private record Plugged(int place, String who, Object hook) {
    }

    private static final Plugged[] NOBODY = new Plugged[0];

    /** What is plugged into each kind, in place order. */
    private final Map<Kind<?>, Plugged[]> plugged = new IdentityHashMap<>();
    /** The same, as the hooks themselves (what {@link #all} hands out, made once on each plugging in). */
    private final Map<Kind<?>, List<?>> hooks = new IdentityHashMap<>();

    /**
     * {@code who} (a part's name) plugs {@code hook} into every {@code kind} of socket from now on, at {@code place}. Two
     * hooks of one kind at one place is a mistake (their order would be a guess).
     */
    public synchronized <H> void plug(Kind<H> kind, int place, String who, H hook) {
        Plugged[] now = plugged.getOrDefault(kind, NOBODY);
        for (Plugged one : now) {
            if (one.place == place) {
                throw new IllegalStateException(who + " and " + one.who + " both plug into " + kind + " at place " + place);
            }
        }
        List<Plugged> more = new ArrayList<>(Arrays.asList(now));
        more.add(new Plugged(place, who, hook));
        more.sort(Comparator.comparingInt(Plugged::place));
        Plugged[] all = more.toArray(NOBODY);
        plugged.put(kind, all);
        hooks.put(kind, Arrays.stream(all).map(Plugged::hook).toList());
    }

    /** Everything plugged into this kind, in place order (empty when nothing is). */
    @SuppressWarnings("unchecked")
    public <H> List<H> all(Kind<H> kind) {
        List<?> all = hooks.get(kind);
        return all == null ? List.of() : (List<H>) all;
    }

    /** The first thing plugged into this kind, or null. */
    @Nullable
    public <H> H first(Kind<H> kind) {
        List<H> all = all(kind);
        return all.isEmpty() ? null : all.get(0);
    }

    /** For the tests and the logs: who plugs into a kind, by name, in place order. */
    public List<String> pluggedInto(Kind<?> kind) {
        return Arrays.stream(plugged.getOrDefault(kind, NOBODY)).map(Plugged::who).toList();
    }
}
