package com.antcolonies.colony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteSoldierEntity.Rank;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.species.SpeciesProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The colony's soldiers (species with {@code soldiers} in their profile: termites). How many it wants (about 1 in 10
 * of its termites, 3 minors to a major), which nymphs grow into soldiers instead of workers ("on demand": when the
 * colony has fewer than it wants), and where they stand guard inside the nest (see {@link #posts}).
 */
public class Soldiers {
    /**
     * A place a soldier guards: it stands in {@code cell} and faces {@code facing} (the block next to it, the way
     * trouble would come from).
     */
    public record Post(BlockPos cell, BlockPos facing) {
    }

    private final Colony colony;
    /** Post cell -> the soldier holding it. Not saved: after a reload the soldiers simply take their posts again. */
    private final Map<BlockPos, UUID> held = new HashMap<>();

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

    /** How many soldiers of this kind the colony wants now (from how many workers it has; none for a species without soldiers). */
    public int wanted(Rank rank) {
        SpeciesProfile.SoldierNumbers numbers = numbers();
        if (numbers == null) {
            return 0;
        }
        int workers = colony.getCount(WorkerAntEntity.CASTE);
        return rank == Rank.MINOR ? numbers.wantedMinors(workers) : numbers.wantedMajors(workers);
    }

    /** The nymphs that are growing into soldiers of this kind. */
    public int nymphsToBe(Level level, Rank rank) {
        int count = 0;
        for (AntLarvaEntity nymph : colony.brood().getLarvae(level)) {
            if (nymph.getSoldierRank() == rank) {
                count++;
            }
        }
        return count;
    }

    /**
     * What a new nymph grows into: a soldier of the kind the colony is short of (soldiers and nymphs already growing
     * into them both count; minors first, they are the first line), or null for a worker.
     */
    @Nullable
    public Rank rankForNewNymph(Level level) {
        for (Rank rank : Rank.values()) {
            if (count(rank) + nymphsToBe(level, rank) < wanted(rank)) {
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
     * doorway (facing the room it opens into). Minors (step 6a, inside): in each foraging tunnel, one block before its
     * end, facing the end under the hole (step 6b takes them out through the holes at foraging time).
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
            for (NestPlan.ForagingTunnel tunnel : nest.getForagingTunnels(level)) {
                List<BlockPos> cells = tunnel.tunnel();
                if (cells.size() >= 2) {
                    add(level, posts, cells.get(cells.size() - 2), tunnel.end());
                }
            }
        }
        return posts;
    }

    private static void add(Level level, List<Post> posts, BlockPos cell, BlockPos facing) {
        BlockPos floor = cell.below();
        if (Nest.isDugOut(level, cell) && level.getBlockState(floor).isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)) {
            posts.add(new Post(cell, facing));
        }
    }

    /**
     * The post this soldier should guard, or null if there is none free: the one it already holds if that is still a
     * post, otherwise the first free one (not in {@code avoid}: posts it found no way to), which it then holds.
     */
    @Nullable
    public Post claimPost(ServerLevel level, TermiteSoldierEntity soldier, Set<BlockPos> avoid) {
        held.values().removeIf(id -> !(level.getEntity(id) instanceof TermiteSoldierEntity holder) || !holder.isAlive()
                || !colony.getId().equals(holder.getColonyId()));
        List<Post> posts = posts(level, soldier.getRank());
        for (Post post : posts) {
            if (soldier.getUUID().equals(held.get(post.cell())) && !avoid.contains(post.cell())) {
                return post;
            }
        }
        releasePost(soldier.getUUID());
        for (Post post : posts) {
            if (!held.containsKey(post.cell()) && !avoid.contains(post.cell())) {
                held.put(post.cell(), soldier.getUUID());
                return post;
            }
        }
        return null;
    }

    /** Whether this soldier still holds this post and it is still a post (dug out, part of the plan). */
    public boolean holds(Level level, TermiteSoldierEntity soldier, Post post) {
        return soldier.getUUID().equals(held.get(post.cell())) && posts(level, soldier.getRank()).contains(post);
    }

    /** The soldier gives its post up (another soldier may take it). */
    public void releasePost(UUID soldier) {
        held.values().removeIf(soldier::equals);
    }

    /**
     * For the colony list: " | soldiers 1 minor (wants 2), 0 major (wants 1), nymphs growing into soldiers 1, posts held 1".
     * Empty for a species without soldiers.
     */
    public String describe(Level level) {
        if (numbers() == null) {
            return "";
        }
        int toBe = nymphsToBe(level, Rank.MINOR) + nymphsToBe(level, Rank.MAJOR);
        return " | soldiers " + count(Rank.MINOR) + " minor (wants " + wanted(Rank.MINOR) + "), " + count(Rank.MAJOR) + " major (wants "
                + wanted(Rank.MAJOR) + "), nymphs growing into soldiers " + toBe + ", posts held " + held.size();
    }
}
