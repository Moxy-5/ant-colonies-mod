package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The colony's soldiers (species with {@code soldiers} in their profile: termites). How many it wants (about 1 in 10
 * of its termites, 3 minors to a major), which nymphs grow into soldiers instead of workers ("on demand": when the
 * colony has fewer than it wants), and where they stand guard (see {@link #posts}): the majors inside the nest, the
 * minors outside at foraging time (beside the ways out and along the foragers' trails) and inside otherwise. When the
 * foraging is over the minors stay out until the workers are in, then go in last ({@link #minorsOut}).
 */
public class Soldiers {
    /**
     * A place a soldier guards: it stands in {@code cell} and faces {@code facing} (a block the way trouble would come
     * from).
     */
    public record Post(BlockPos cell, BlockPos facing, Kind kind) {
    }

    /** Where a post is: inside the nest, outside beside a way out, or outside along a foragers' trail. */
    public enum Kind {
        INSIDE, WAY_OUT, TRAIL
    }

    /** How often (ticks) the posts outside are worked out again (the trails change as the foragers move on). */
    private static final int OUTSIDE_TICKS = 20;

    private final Colony colony;
    /** Post cell -> the soldier holding it. Not saved: after a reload the soldiers simply take their posts again. */
    private final Map<BlockPos, UUID> held = new HashMap<>();
    /** The minors' posts outside, and when they were worked out. */
    private List<Post> outsidePosts = List.of();
    private long outsideAt = Long.MIN_VALUE;
    /** Whether the minors went out this foraging time and have not been called in yet. Not saved. */
    private boolean escorting;
    /** When the foraging time was seen to be over (from then on the minors wait for the workers). */
    private long foragingEndedAt = Colony.NOT_SET;

    /** How many more minors come out while the alarm is up at a break-in (level 3 or more), beyond the ones with posts. */
    private static final int BREACH_MINORS = 6;

    Soldiers(Colony colony) {
        this.colony = colony;
    }

    /** The soldier numbers of this colony's species, or null if it has no soldiers. */
    @Nullable
    public SpeciesProfile.SoldierNumbers numbers() {
        return colony.profile().soldiers().orElse(null);
    }

    /** How many soldiers of this kind the colony has. */
    public int count(Rank rank) {
        return colony.getCount(rank.caste());
    }

    /** How many soldiers of this kind the colony wants now (from how many workers it has, more after a level 4 alarm; none for a species without soldiers). */
    public int wanted(Level level, Rank rank) {
        SpeciesProfile.SoldierNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        // After repeated break-ins (alarm level 4) the colony raises more soldiers for a while.
        int workers = Math.round(colony.getCount(Caste.WORKER) * colony.part(ColonyParts.ALARM).map(alarm -> alarm.soldierBoost(level)).orElse(1.0F));
        return rank == Rank.MINOR ? numbers.wantedMinors(workers) : numbers.wantedMajors(workers);
    }

    /** The nymphs that are growing into soldiers of this kind (the real ones and the numbers). */
    public int nymphsToBe(Level level, Rank rank) {
        int count = 0;
        for (AntLarvaEntity nymph : colony.brood().getLarvae(level)) {
            if (rank.caste().equals(nymph.getGrowsInto(colony))) {
                count++;
            }
        }
        return count + colony.brood().insideGrowingInto(rank.caste());
    }

    /**
     * What a new nymph grows into: a soldier of the kind the colony is short of (soldiers and nymphs already growing
     * into them both count; minors first, they are the first line), or null for a worker.
     */
    @Nullable
    public Rank rankForNewNymph(Level level) {
        for (Rank rank : Rank.values()) {
            if (count(rank) + nymphsToBe(level, rank) < wanted(level, rank)) {
                return rank;
            }
        }
        return null;
    }

    /** How many pieces of food a nymph growing into this (null: a worker) eats before it grows up. */
    public int mealFor(@Nullable Rank rank) {
        SpeciesProfile.SoldierNumbers numbers = numbers();
        if (rank == null || numbers == null) {
            return colony.profile().brood().larvaMeal();
        }
        return rank == Rank.MINOR ? numbers.minorMeal() : numbers.majorMeal();
    }

    /**
     * For {@code /antcolonies population}: as many soldiers added to the numbers as {@code workers} want, and the words
     * for it (", 2 minor soldiers, 1 major soldiers"); nothing without the profile's soldier numbers.
     */
    public String addFor(int workers, long now) {
        SpeciesProfile.SoldierNumbers numbers = numbers();
        if (numbers == null) {
            return "";
        }
        StringBuilder soldiers = new StringBuilder();
        for (Rank rank : Rank.values()) {
            int want = rank == Rank.MINOR ? numbers.wantedMinors(workers) : numbers.wantedMajors(workers);
            int more = Math.max(0, want - colony.getCount(rank.caste()));
            colony.population().addNewMembers(rank.caste(), now, more);
            soldiers.append(", ").append(more).append(" ").append(rank == Rank.MINOR ? "minor" : "major").append(" soldiers");
        }
        return soldiers.toString();
    }

    /** This colony's soldiers around the nest (loaded and alive). */
    public List<TermiteSoldierEntity> getSoldiers(Level level) {
        return level.getEntitiesOfClass(TermiteSoldierEntity.class, colony.nestArea(),
                soldier -> soldier.isAlive() && colony.getId().equals(soldier.getColonyId()));
    }

    // --- guard posts --------------------------------------------------------------------------------------------

    /**
     * Where soldiers of this kind stand guard, most important first; only posts that are dug out, with a floor to stand
     * on, count. Majors (who guard the heart of the nest, like real Macrotermes majors): the royal chamber's doorway (the
     * end of its tunnel, facing back along it), the way in from the shaft (the start of that tunnel, facing the shaft;
     * not the shaft's foot itself, which the workers later dig on down to their galleries), and each garden room's
     * doorway (facing the room it opens into). Minors: while they are out ({@link #minorsOut}) the posts outside first
     * ({@link #outsidePosts}), then the ones inside; inside, one in each foraging tunnel, one block before its end,
     * facing the end under the hole.
     */
    public List<Post> posts(Level level, Rank rank) {
        List<Post> posts = new ArrayList<>();
        Nest nest = colony.nest();
        if (rank == Rank.MAJOR) {
            NestPlan.Chamber royal = null;
            for (NestPlan.Chamber chamber : nest.getPlannedChambers(level)) {
                if (chamber.kind() == NestPlan.Kind.QUEEN) {
                    royal = chamber;
                }
            }
            BlockPos bottom = nest.getShaftBottom(level);
            if (royal != null && bottom != null && !royal.wayIn().isEmpty()) {
                List<BlockPos> wayIn = royal.wayIn();
                BlockPos door = royal.tunnelEnd();
                add(level, posts, door, wayIn.size() >= 2 ? wayIn.get(wayIn.size() - 2) : bottom);
                if (wayIn.size() >= 2) {
                    add(level, posts, wayIn.get(0), bottom);
                }
            }
            for (NestPlan.Chamber garden : nest.getGardens(level)) {
                BlockPos door = garden.cells().get(0);
                BlockPos out = door.offset(Integer.signum(door.getX() - garden.center().getX()), 0,
                        Integer.signum(door.getZ() - garden.center().getZ()));
                add(level, posts, door, out);
            }
        } else {
            if (minorsOut(level)) {
                posts.addAll(outsidePosts(level));
            }
            List<NestPlan.ForagingTunnel> own = nest.ownForagingTunnels(level);
            for (NestPlan.ForagingTunnel tunnel : nest.getForagingTunnels(level)) {
                List<BlockPos> cells = tunnel.tunnel();
                // Holes taking turns (termites underground): the nest's own tunnels are always guarded (its main ways
                // in); out along the arms only the holes in use, not every plugged one.
                if (!own.contains(tunnel) && !nest.galleries().isInTurn(tunnel.hole()) && !ForagingHoles.isOpen(level, tunnel.hole())) {
                    continue;
                }
                if (cells.size() >= 2) {
                    add(level, posts, cells.get(cells.size() - 2), tunnel.end());
                }
            }
        }
        return posts;
    }

    private static void add(Level level, List<Post> posts, BlockPos cell, BlockPos facing) {
        BlockPos floor = cell.below();
        if (Nest.isDugOut(level, cell) && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            posts.add(new Post(cell, facing, Kind.INSIDE));
        }
    }

    // --- the minors outside -------------------------------------------------------------------------------------

    /**
     * Whether the minors are out now: at foraging time (termites: at night and in the rain), and after it for as long
     * as workers are still outside (at most the profile's {@code escort_home_ticks}): the minors go in last, behind the
     * workers.
     */
    public boolean minorsOut(Level level) {
        SpeciesProfile.SoldierNumbers numbers = numbers();
        if (numbers == null) {
            return false;
        }
        if (colony.isForagingTime(level)) {
            escorting = true;
            foragingEndedAt = Colony.NOT_SET;
            return true;
        }
        if (!escorting) {
            return false;
        }
        long now = level.getGameTime();
        if (foragingEndedAt == Colony.NOT_SET || foragingEndedAt > now) {
            foragingEndedAt = now;
        }
        if (now - foragingEndedAt >= numbers.escortHomeTicks() || colony.trails().workersOutside(level) == 0) {
            escorting = false;
        }
        return escorting;
    }

    /**
     * The minors' posts outside (worked out once a second), most important first. For every way out that is open
     * (foraging holes and the open entrance, see {@link ForagingHoles#waysOut}), the one with the busiest trail first
     * (see {@link ForagingTrails#trails}): a post on the ground right beside it, facing out from the nest, then the
     * sentry line along its trail ({@link SentryLine}: one every {@code sentry_spacing} blocks on alternate sides, facing
     * away from the trail). Ways out no forager has used lately come last and have no line. A post must be on solid
     * ground (not leaves), in the open, outside the nest and the mound and not in a cell the mound is to grow into.
     */
    public List<Post> outsidePosts(Level level) {
        long now = level.getGameTime();
        if (outsideAt != Long.MIN_VALUE && now - outsideAt < OUTSIDE_TICKS && now >= outsideAt) {
            return outsidePosts;
        }
        outsideAt = now;
        SpeciesProfile.SoldierNumbers numbers = numbers();
        List<ForagingHoles.WayOut> ways = colony.foragingHoles().waysOut(level);
        if (numbers == null || ways.isEmpty()) {
            outsidePosts = List.of();
            return outsidePosts;
        }
        List<ForagingTrails.Trail> trails = colony.trails().trails(level);
        List<ForagingHoles.WayOut> ordered = new ArrayList<>();
        for (ForagingTrails.Trail trail : trails) {
            ways.stream().filter(way -> way.mouth().equals(trail.exit())).findFirst().ifPresent(ordered::add);
        }
        for (ForagingHoles.WayOut way : ways) {
            if (!ordered.contains(way)) {
                ordered.add(way);
            }
        }
        List<Post> posts = new ArrayList<>();
        Set<BlockPos> taken = new HashSet<>();
        for (ForagingHoles.WayOut way : ordered) {
            BlockPos mouth = way.mouth();
            ForagingTrails.Trail trail = trails.stream().filter(t -> t.exit().equals(mouth)).findFirst().orElse(null);
            Direction out = way.outward() != null ? way.outward()
                    : trail != null ? towards(trail.patch().getX() - mouth.getX(), trail.patch().getZ() - mouth.getZ()) : Direction.NORTH;
            for (Direction side : List.of(out, out.getClockWise(), out.getCounterClockWise(), out.getOpposite())) {
                BlockPos beside = mouth.relative(side);
                BlockPos cell = groundNear(level, beside.getX(), beside.getZ(), mouth.getY(), taken);
                if (cell != null) {
                    posts.add(new Post(cell, cell.relative(out, 3), Kind.WAY_OUT));
                    taken.add(cell);
                    break;
                }
            }
            if (trail == null) {
                continue;
            }
            int y = mouth.getY();
            for (SentryLine.Spot spot : SentryLine.along(mouth.getX(), mouth.getZ(), trail.patch().getX(), trail.patch().getZ(),
                    numbers.sentrySpacing())) {
                BlockPos cell = groundNear(level, spot.x(), spot.z(), y, taken);
                if (cell != null) {
                    posts.add(new Post(cell, new BlockPos(spot.faceX(), cell.getY(), spot.faceZ()), Kind.TRAIL));
                    taken.add(cell);
                    y = cell.getY();
                }
            }
        }
        outsidePosts = List.copyOf(posts);
        return outsidePosts;
    }

    /** The horizontal direction nearest to (dx, dz). */
    private static Direction towards(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** A cell in this column, within 3 blocks up or down of {@code y} (nearest first), where a minor can stand guard outside; null if none. */
    @Nullable
    private BlockPos groundNear(Level level, int x, int z, int y, Set<BlockPos> taken) {
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
            BlockPos cell = new BlockPos(x, y + dy, z);
            if (!taken.contains(cell) && isGuardSpotOutside(level, cell)) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Whether a minor can stand guard here outside: an open cell (no fluid) on solid ground that isn't leaves, outside
     * the nest and the mound, not a foraging hole, and not where the mound is to grow.
     */
    private boolean isGuardSpotOutside(Level level, BlockPos cell) {
        if (!level.isLoaded(cell)) {
            return false;
        }
        BlockState state = level.getBlockState(cell);
        BlockPos floor = cell.below();
        BlockState below = level.getBlockState(floor);
        if (!state.getCollisionShape(level, cell).isEmpty() || !state.getFluidState().isEmpty()
                || !below.isFaceSturdy(level, floor, Direction.UP) || below.is(BlockTags.LEAVES)) {
            return false;
        }
        return !colony.nest().isInside(level, cell) && !colony.foragingHoles().isHole(level, cell)
                && !colony.moundParts(level).containsKey(cell);
    }

    // --- holding posts ------------------------------------------------------------------------------------------

    /**
     * The post this soldier should guard, or null if there is none free: the most important one that is free or its
     * own (not in {@code avoid}: posts it found no way to), which it then holds. So a soldier moves up to a more
     * important post when one comes free (a minor waiting inside when a way out opens, a sentry when its trail moves).
     */
    @Nullable
    public Post claimPost(ServerLevel level, TermiteSoldierEntity soldier, Set<BlockPos> avoid) {
        pruneHolders(level);
        for (Post post : posts(level, soldier.getRank())) {
            UUID holder = held.get(post.cell());
            if ((holder == null || holder.equals(soldier.getUUID())) && !avoid.contains(post.cell())) {
                releasePost(soldier.getUUID());
                held.put(post.cell(), soldier.getUUID());
                return post;
            }
        }
        releasePost(soldier.getUUID());
        return null;
    }

    /**
     * Whether this soldier still holds this post: it is still a post, its own, and no more important post (not in
     * {@code avoid}) is free.
     */
    public boolean holds(ServerLevel level, TermiteSoldierEntity soldier, Post post, Set<BlockPos> avoid) {
        if (!soldier.getUUID().equals(held.get(post.cell()))) {
            return false;
        }
        pruneHolders(level);
        for (Post other : posts(level, soldier.getRank())) {
            if (other.equals(post)) {
                return true;
            }
            if (!held.containsKey(other.cell()) && !avoid.contains(other.cell())) {
                return false; // a more important post is free: it moves up to it
            }
        }
        return false;
    }

    /** Frees the posts of soldiers that are gone (dead, unloaded, in another colony). */
    private void pruneHolders(ServerLevel level) {
        held.values().removeIf(id -> !(level.getEntity(id) instanceof TermiteSoldierEntity holder) || !holder.isAlive()
                || !colony.getId().equals(holder.getColonyId()));
    }

    /** The soldier gives its post up (another soldier may take it). */
    public void releasePost(UUID soldier) {
        held.values().removeIf(soldier::equals);
    }

    /**
     * For the colony list: " | soldiers 1 minor (wants 2), 0 major (wants 1), nymphs growing into soldiers 1, posts held 2
     * (1 beside ways out, 1 on trails)" and, while the minors are out, the trails. Empty for a species without soldiers.
     */
    public String describe(Level level) {
        if (numbers() == null) {
            return "";
        }
        int toBe = nymphsToBe(level, Rank.MINOR) + nymphsToBe(level, Rank.MAJOR);
        int holding = 0;
        for (Rank rank : Rank.values()) {
            for (Post post : posts(level, rank)) {
                holding += held.containsKey(post.cell()) ? 1 : 0;
            }
        }
        String line = " | soldiers " + count(Rank.MINOR) + " minor (wants " + wanted(level, Rank.MINOR) + "), " + count(Rank.MAJOR) + " major (wants "
                + wanted(level, Rank.MAJOR) + "), nymphs growing into soldiers " + toBe + ", posts held " + holding;
        if (!minorsOut(level)) {
            return line + ", minors in";
        }
        int byWays = 0;
        int onTrails = 0;
        for (Post post : outsidePosts(level)) {
            if (held.containsKey(post.cell())) {
                if (post.kind() == Kind.WAY_OUT) {
                    byWays++;
                } else {
                    onTrails++;
                }
            }
        }
        return line + " (" + byWays + " beside ways out, " + onTrails + " on trails)"
                + (colony.isForagingTime(level) ? "" : ", waiting for the workers to come in") + colony.trails().describe(level);
    }

    /**
     * How many real soldiers the colony wants now, by rank (see {@link Population#targets}): watched (a player near), as
     * many as there are posts for them (a few more minors at a break-in), up to a quarter of the real cap; not watched,
     * only while the alarm is up (2 or more: an enemy at the foragers). No more than the colony has, so the workers get
     * the rest of the cap. Returns how many in all.
     */
    int wantReal(ServerLevel level, boolean watched, int cap, Map<String, Integer> wanted) {
        if (numbers() == null) {
            return 0;
        }
        int soldiers = 0;
        int alarm = colony.part(ColonyParts.ALARM).map(part -> part.level(level)).orElse(0);
        int soldierCap = cap / 4;
        for (Rank rank : Rank.values()) {
            int posts = posts(level, rank).size();
            if (rank == Rank.MINOR && alarm >= 3) {
                posts += BREACH_MINORS;
            }
            // (no more than the colony has, so the workers get the rest of the cap)
            int want = watched || alarm >= 2 ? Math.min(Math.min(posts, colony.getCount(rank.caste())), soldierCap - soldiers) : 0;
            wanted.put(rank.caste(), Math.max(0, want));
            soldiers += Math.max(0, want);
        }
        return soldiers;
    }

    /** For the colony list: the soldiers that are numbers in the nest. */
    String inside(Population population) {
        if (numbers() == null) {
            return "";
        }
        return "; soldiers " + population.inside(Rank.MINOR.caste()) + " minor, " + population.inside(Rank.MAJOR.caste()) + " major";
    }
}
