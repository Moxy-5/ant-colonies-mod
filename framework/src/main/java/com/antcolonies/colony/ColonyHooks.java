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

import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.nav.HiddenWays;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
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

        /** Step 9a: a real forager sets out for the food at {@code plant}: where it leaves the trail (its trip counted), or null. */
        @Nullable
        BlockPos setOut(ServerLevel level, BlockPos plant);

        /** Step 9a: a real searcher sets out to search round {@code toward}: where it leaves the trail (not counted yet), or null. */
        @Nullable
        BlockPos wayOut(ServerLevel level, BlockPos toward);

        /** Step 9a: a forager home with food from {@code from}: where it joins a trail home, or null. */
        @Nullable
        BlockPos wayHome(ServerLevel level, BlockPos from);
    }

    /** How many of the forager numbers may be out now (of {@code foragers}; Gordon's door rule for the harvester ant). */
    @FunctionalInterface
    public interface DoorRule {
        double free(ServerLevel level, int foragers);
    }

    /** The founders beside the queen (the king, in a royal pair). */
    public interface CoFounders {
        /** How many dig beside her (alive as far as the brain knows). */
        int count();

        /** Step 9a: whether this insect is one of them. */
        boolean isOne(AbstractAntEntity insect);

        /** Step 9a: the caste they are counted under in the colony's headcount. */
        String caste();
    }

    /** What castes a young one grows into besides a worker (soldiers): its meal, and the caste wanted for a new one. */
    public interface Castes {
        /** Step 9c: these castes' names (the soldiers' "minor_soldier", "major_soldier"). */
        List<String> castes();

        /** How much a young one growing into this caste (null: a worker) eats before it grows up. */
        int mealFor(@Nullable String caste);

        /**
         * Apex part 3, T1: how often (ticks) a grown member of this caste eats, as a number in the nest too
         * ({@link Meals}); 0: it does not eat (it lives on what it ate as a young one).
         */
        default int mealIntervalTicks(String caste) {
            return 0;
        }

        /** The caste a new young one should grow into, or null (soldiers enough). */
        @Nullable
        String wanted(Level level);

        /** Step 9c: the word a young one growing into this caste is saved under, or null if not one of these castes. */
        @Nullable
        String savedAs(String caste);

        /** Step 9c: the caste saved under this word ({@link #savedAs}), or null if not one of these castes. */
        @Nullable
        String fromSaved(String word);

        /**
         * Step 10: the small number a young one growing into this caste is synced and saved as (1 and up; 0 is a worker),
         * or 0 if not one of these castes. Kept the same for good: saved young are read back by it.
         */
        byte numberOf(String caste);

        /** Step 10: the caste with this number ({@link #numberOf}), or null if not one of these castes. */
        @Nullable
        String casteOf(int number);

        /**
         * How many real mobs of these castes the colony wants now (of a real cap of {@code cap}; {@code watched}: a player
         * near), put into {@code wanted} by caste before the workers; returns how many in all (the workers get the rest).
         */
        int wantReal(ServerLevel level, boolean watched, int cap, Map<String, Integer> wanted);

        /** For the colony list: how many of these castes are numbers in the nest ("; soldiers 3 minor, 1 major"), or "". */
        String inside(Population population);

        /** For {@code /antcolonies population}: as many of them added as so many workers want, and the words for it. */
        default String addFor(int workers, long now) {
            return "";
        }

        /** Step 9a: the real members of these castes the nurses feed mouth to mouth (they ask with {@link com.antcolonies.entity.FedByNurses}). */
        List<? extends Entity> fedByNurses(Level level);
    }

    /** Insects out of the world for a while on hidden trips (they are real ones, coming back). */
    public interface HiddenAway {
        /** How many of this caste are away on a hidden trip. */
        int travelling(String caste);

        /** Whether any of them walks through one of these cells on its way (never wall it in). */
        boolean anyWalksThrough(Set<BlockPos> cells);

        /** Step 9a: the closed ways the colony's insects may walk out of the world on (their routes are planned along them). */
        HiddenWays ways(ServerLevel level);
    }

    /** The structure built over the nest (the termite mound): its blocks, its tunnels and its next block to dig. */
    public interface Structure {
        /** Its blocks and what each is (to the species; shared code only asks which cells are in it). */
        Map<BlockPos, ?> parts(Level level);

        /**
         * Step 9c: its cells that count as inside the home ({@link Nest#isInside}): its hollow cells (once roofed over)
         * into {@code cells}, its built channels (tunnels, hollow spire pieces, mouths) into {@code channels}.
         */
        void addInside(Level level, Set<BlockPos> cells, Set<BlockPos> channels);

        /** Step 9c: whether this block is a channel a member inside it is in (a tunnel block, a hollow spire piece, a mouth). */
        boolean isChannel(BlockState state);

        /** Its tunnels, dug as part of the nest. */
        List<BlockPos> tunnels(Level level);

        /** Its next block for a digger to clear, or null. */
        @Nullable
        BlockPos digTarget(Level level, @Nullable UUID digger);

        /**
         * Apex part 3, T1: the share of walks that may go out or in by one of its mouths (a chimney's mouth, about 1 in
         * 10); on the others the insect takes another way, such as a foraging hole. 1: any walk.
         */
        default float mouthTripChance() {
            return 1.0F;
        }
    }

    /** A food garden in the nest whose blocks age (the fungus comb). */
    public interface Garden {
        /** Its blocks as known now. */
        List<BlockPos> beds(Level level);

        /**
         * Step 9c: puts the ageing owed from while away ({@link NestLedger}) on its beds out of every player's sight, at
         * most {@code most} of each kind; returns what is still owed (none of a kind once no bed is left to take it).
         */
        Ages age(ServerLevel level, List<net.minecraft.world.phys.Vec3> eyes, Ages owed, int most);
    }

    /** Ageing owed to a garden's beds: layers ripened, layers gone old; {@code changed} when any block was changed. */
    public record Ages(int ripened, int aged, boolean changed) {
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

    /**
     * Step 9a: a real forager's search when it searches alone (the harvester ant: brain step 4c, {@code ScoutGoal}): the
     * numbers it searches by, where this trip's search goes, and a search round its own spot found empty.
     */
    public interface LoneSearch {
        /** The numbers of a lone search ({@code food.search}). */
        SpeciesProfile.SearchNumbers settings();

        /** Where this trip's search goes, or null (no open ground was hit); {@code ground} finds open ground. */
        @Nullable
        SearchPlan plan(ServerLevel level, AbstractAntEntity ant, GroundPicker ground);

        /** Nothing at its own spot this time. */
        void emptyAtSpot(UUID ant);
    }

    /** An open cell on the ground {@code distance} blocks out from the nest's middle at {@code angle}, give or take {@code jitter}, or null. */
    @FunctionalInterface
    public interface GroundPicker {
        @Nullable
        BlockPos cell(double angle, double distance, int jitter);
    }

    /**
     * A lone search planned: its centre, its direction (radians from the nest's middle), how far out it starts, how far
     * its way reaches (its drift goes at most the stretch past it), and whether it is round the forager's own spot.
     */
    public record SearchPlan(BlockPos spot, double angle, double from, int reachOfWay, boolean atOwnSpot) {
    }

    /** Step 9a: the scent line a forager called out to a rich patch follows (nest end first), or null. */
    @FunctionalInterface
    public interface CalledOut {
        @Nullable
        List<BlockPos> lineFor(Level level, UUID ant);
    }

    /** Step 9a: a place for a forager's load besides the store (the harvester's mill). */
    public interface Mill {
        /** Whether there is a place for this forager's piece, at the mill or in the store (it claims it). */
        boolean hasPlace(ServerLevel level, UUID ant);

        /** Whether this forager's piece goes to the mill. */
        boolean milling(UUID ant);
    }

    /** Step 9a: where the soil a real digger bites loose goes (the harvester's hill, the termite mound). */
    public interface DugSoil {
        /** Now and then while digging: whether the digger stops (soil waiting to be carried up first, or a rest). */
        default boolean waits(ServerLevel level, AbstractAntEntity digger) {
            return false;
        }

        /** A bite of {@code target}: whether its soil is taken up as a pellet. */
        boolean takesPellet(ServerLevel level, BlockPos target, UUID digger);

        /** The pellet a bite of {@code dug} gives (the termites' own mound blocks give plain mound soil). */
        default BlockState pellet(BlockState dug) {
            return dug;
        }
    }

    /** Step 9b: a part's words in the colony list ({@code /antcolonies list}), in one of its slots ({@link #LIST_STORE}, ...). */
    @FunctionalInterface
    public interface ListLine {
        String line(ServerLevel level);
    }

    /** Step 8c: one more job for the nurses while away (see {@link NestLife}). True if it was wanted now and done. */
    @FunctionalInterface
    public interface NurseJob {
        boolean job(ServerLevel level, NestLife life);
    }

    /**
     * The split, step S2: a web of galleries under the ground round the nest whose holes take turns (the termites'
     * galleries). The shared nest digs its pieces and holes as more foraging tunnels, opens only the holes in turn, and
     * forages and reaches as far as the web; plugged in only while the web is switched on ({@link Colony#underground}).
     */
    public interface Underground {
        /** Whether the web is switched on (the species file's switch, or a test's). */
        boolean isOn();

        /** Its pieces, as foraging tunnels after the nest's own (the pieces out to food first). */
        List<NestPlan.ForagingTunnel> arms();

        /** Whether this hole is the end of one of its pieces. */
        boolean hasPiece(BlockPos hole);

        /** The holes along its tunnels, each as a foraging tunnel up to the block under it. */
        List<NestPlan.ForagingTunnel> holesAlong();

        /** The blocks the diggers look at for this foraging tunnel (a piece's own blocks; none for a hole along a tunnel). */
        List<BlockPos> digCells(NestPlan.ForagingTunnel tunnel);

        /** Whether this hole may be opened this foraging time (its turn, a runway's hole, a tunnel out to food). */
        boolean isInTurn(BlockPos hole);

        /** The holes open to the foragers this foraging time (the numbers graze only round these). */
        List<BlockPos> holesInTurn(Level level);

        /** The numbers brought food in by this hole now. */
        void noteForaged(BlockPos hole, long now);

        /** How far round an open hole (blocks) the foragers graze. */
        int grazeRadius();

        /** The colony's range with the web: its farthest hole and the graze round it (0: none yet). */
        int range();

        /** Where to scan for plants past the nest's own ground, each as {x, z, reach}. */
        List<int[]> scanSpots(Level level);

        /** How many of its pieces were walled off against neighbours. */
        int walledOff();

        /** The colony is back from being away. */
        void cameBack(ServerLevel level);

        /** On return, one step of growth with the builders' time: {pieces, of them toward food, blocks} ({0, 0, 0}: none may grow). */
        int[] growNow(ServerLevel level);

        /** How far from the middle (blocks, sideways) food may lie for a piece to be dug out to it. */
        int foodReach();

        /** A piece out to rich food at {@code food} from the nearest dug gallery, or null: no route. */
        @Nullable
        GalleryPiece pieceTo(ServerLevel level, BlockPos food);

        /** Whether the workers allow this piece now. */
        boolean affords(ServerLevel level, GalleryPiece piece, BlockPos food);

        /** The piece out to food joins the web. */
        void addFoodPiece(ServerLevel level, GalleryPiece piece);
    }

    /**
     * A piece of gallery planned out to rich food: {@code tunnel} from the shaft (its first {@code from} blocks are the
     * gallery it leaves from, {@code parent}'s, or one of the nest's own tunnels when {@code parent} is null).
     */
    public record GalleryPiece(NestPlan.ForagingTunnel tunnel, int from, @Nullable BlockPos parent) {
    }

    /**
     * The split, step S2: galleries dug up under food from below, and what is left of eaten food joined to them (the
     * termites' feeding galleries and mud casts).
     */
    public interface FeedingWays {
        /** The blocks of the food at {@code food} a feeding gallery may reach (the food round it, as far as it spreads). */
        Set<BlockPos> foodBlocks(Level level, BlockPos food, java.util.function.Predicate<BlockPos> isFood);

        /** The feeding gallery from this tunnel up under the food, or empty if it cannot be reached from below. */
        List<BlockPos> plan(Level level, NestPlan.ForagingTunnel tunnel, BlockPos food, TunnelRoute.Ground ground,
                java.util.function.Predicate<BlockPos> isFood);

        /** The cells left of eaten food joined to these feeding gallery cells (inside), each with the gallery cell it eats from. */
        Map<BlockPos, BlockPos> joined(Level level, List<BlockPos> galleryCells, Set<BlockPos> inside);

        /** The split, step S3: the cast left at {@code pos} of a block of food eaten away, open on these sides (the termites' mud cast). */
        net.minecraft.world.level.block.state.BlockState castFor(Level level, BlockPos pos, List<net.minecraft.core.Direction> open);
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
    /** Step 9c: the wall of plates a colony plasters with (the termites' mud crust), for species with crust walls. */
    public static final Kind<com.antcolonies.block.FacedWall> CRUST = Kind.of("crust");
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
    /** Step 9a: a real forager's lone search (the harvester's forager spots). */
    public static final Kind<LoneSearch> LONE_SEARCH = Kind.of("lone_search");
    /** Step 9a: the scent line a called-out forager follows (the harvester's recruits). */
    public static final Kind<CalledOut> CALLED_OUT = Kind.of("called_out");
    /** Step 9a: a place for a load besides the store (the harvester's bakery). */
    public static final Kind<Mill> MILL = Kind.of("mill");
    /** Step 9a: where a real digger's soil goes (the harvester's hill, the termite mound). */
    public static final Kind<DugSoil> DUG_SOIL = Kind.of("dug_soil");
    /** Step 9b, the colony list's slots: after the seed store (the bread). */
    public static final Kind<ListLine> LIST_STORE = Kind.of("list_store");
    /** In place of the store's line (the fungus garden). */
    public static final Kind<ListLine> LIST_FOOD = Kind.of("list_food");
    /** After the eggs and larvae (the soldiers, the alarm). */
    public static final Kind<ListLine> LIST_BROOD = Kind.of("list_brood");
    /** After the food map (the forager spots, the recruits, the trunk trails). */
    public static final Kind<ListLine> LIST_SEARCH = Kind.of("list_search");
    /** After the danger map (the closed holes). */
    public static final Kind<ListLine> LIST_DANGER = Kind.of("list_danger");
    /** After the dead ants (the mound). */
    public static final Kind<ListLine> LIST_BUILDING = Kind.of("list_building");
    /** After the day clock (the patrol, the door, the night soil). */
    public static final Kind<ListLine> LIST_DAY = Kind.of("list_day");
    /** After the holes, the new holes and the runways (the galleries, the hidden trips). */
    public static final Kind<ListLine> LIST_HOLES = Kind.of("list_holes");
    /** The split, step S2: the web of galleries under the ground (the termites'). */
    public static final Kind<Underground> UNDERGROUND = Kind.of("underground");
    /** The split, step S2: feeding galleries up under food, and the mud casts joined to them (the termites'). */
    public static final Kind<FeedingWays> FEEDING_WAYS = Kind.of("feeding_ways");

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
