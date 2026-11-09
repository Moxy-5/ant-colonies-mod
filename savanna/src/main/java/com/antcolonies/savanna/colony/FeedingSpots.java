package com.antcolonies.savanna.colony;

import com.antcolonies.colony.LandReady;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.FoodWays;
import com.antcolonies.savanna.registry.SavannaBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The dead wood for the raids, part 5, the check: the termite feeding spots round a Macrotermes mound, the places a
 * Matabele raid can find termites by day (M8 step 4). Real Macrotermes feed by day only under cover: in a feeding
 * gallery dug up under dead wood (eating it from below, leaving mud casts where it is eaten through, mud plastered over
 * thin wood), or under a runway of mud sheeting from a hole out to food. Counted from the colony's own record of its
 * ways to food ({@link FoodWays}: the tunnels dug out to rich food with their galleries, and the runways); the mud is
 * counted by a small block scan round each spot. A spot is "by day" once its gallery or runway is finished and closed
 * all round, so the food over it can be eaten from inside (the same rule the foragers use).
 */
public final class FeedingSpots {
    /** Mud (casts, crust) within this many blocks of a spot's food or gallery counts for it. */
    private static final int MUD_ROUND = 3;

    private FeedingSpots() {
    }

    /**
     * One feeding spot. {@code kind}: "gallery" (under dead wood) or "runway" (mud sheeting); {@code food}: the food it
     * was dug for; {@code wood}: what that block is now (its name, "gone" if eaten away); {@code fromMiddle}: blocks from
     * the mound's middle, flat; {@code cells}: its gallery or runway cells (planned); {@code casts}, {@code crust}: the
     * mud casts and mud crust blocks round it; {@code byDay}: termites can eat there by day, unseen.
     */
    public record Spot(String kind, BlockPos food, String wood, int fromMiddle, int cells, int casts, int crust, boolean byDay) {
    }

    /** Every feeding spot of the colony, nearest the mound first. */
    public static List<Spot> of(ServerLevel level, Colony colony) {
        List<Spot> spots = new ArrayList<>();
        FoodWays ways = colony.nest().foodWays();
        for (FoodWays.NewTunnel tunnel : ways.getNewTunnels()) {
            if (ways.isSpent(tunnel.tunnel().hole())) {
                continue; // (its food is gone, the hole plugged for good)
            }
            List<BlockPos> gallery = tunnel.gallery() == null ? List.of() : tunnel.gallery();
            spots.add(spot(level, colony, "gallery", tunnel.food(), gallery, galleryByDay(level, colony, tunnel.food(), gallery)));
        }
        for (FoodWays.Runway runway : ways.getRunways()) {
            spots.add(spot(level, colony, "runway", runway.food(), runway.cells(), ways.isRunwayClosed(level, runway)));
        }
        spots.sort(java.util.Comparator.comparingInt(Spot::fromMiddle));
        return spots;
    }

    /** Whether termites can eat at this gallery by day, from inside: its food, or wood over one of its cells. */
    private static boolean galleryByDay(ServerLevel level, Colony colony, BlockPos food, List<BlockPos> gallery) {
        boolean byDay = colony.food().isFeedableFromInside(level, food);
        for (int i = 0; i < gallery.size() && !byDay; i++) {
            BlockPos over = gallery.get(i).above();
            byDay = !level.getBlockState(over).isAir() && colony.food().isFeedableFromInside(level, over);
        }
        return byDay;
    }

    /** A feeding place termites eat at by day out of sight: the food and the gallery or runway cells round it. */
    public record ByDay(BlockPos food, List<BlockPos> cells) {
    }

    /**
     * Raid rework R5: the colony's feeding places fed at by day (the {@link Spot#byDay} ones), with their cells, for the
     * Matabele scouts to smell; no mud count (cheap enough for every scout's trip). A place the colony keeps off
     * ({@link Colony#keepsOff}: it was attacked there, brain step 4g) is left out: the termites have sealed it off and
     * feed elsewhere, so there is nobody there to smell (BUGS 168: scouts scored abandoned spots worth a raid and sent
     * columns to pace about an empty spot). The same question the number raiders ask ({@link RaidColumn}, {@link RaidsAway}).
     */
    public static List<ByDay> byDayPlaces(ServerLevel level, Colony colony) {
        List<ByDay> places = new ArrayList<>();
        FoodWays ways = colony.nest().foodWays();
        for (FoodWays.NewTunnel tunnel : ways.getNewTunnels()) {
            if (ways.isSpent(tunnel.tunnel().hole()) || colony.keepsOff(level, tunnel.food())) {
                continue;
            }
            List<BlockPos> gallery = tunnel.gallery() == null ? List.of() : tunnel.gallery();
            if (galleryByDay(level, colony, tunnel.food(), gallery)) {
                places.add(new ByDay(tunnel.food(), List.copyOf(gallery)));
            }
        }
        for (FoodWays.Runway runway : ways.getRunways()) {
            if (ways.isRunwayClosed(level, runway) && !colony.keepsOff(level, runway.food())) {
                places.add(new ByDay(runway.food(), List.copyOf(runway.cells())));
            }
        }
        return places;
    }

    /**
     * Raid rework R6: a way termites walk out to food: {@code cells} in order from the mound outward (the tunnel from
     * the shaft, then its feeding gallery; or the tunnel to a runway's hole, then the runway), ending by {@code food}.
     */
    public record WayOut(BlockPos food, List<BlockPos> cells) {
    }

    /**
     * Raid rework R6: the colony's ways out to food in use (its tunnels to feeding places not spent, and its runways
     * with the tunnel to their hole), for the Matabele scouts to smell and follow outward. From the colony's own records
     * ({@link FoodWays}, the foraging tunnels), no block scan. Ways to food the colony keeps off are left out, as in
     * {@link #byDayPlaces} (BUGS 168: the gallery is sealed there, no traffic to smell).
     */
    public static List<WayOut> waysOut(ServerLevel level, Colony colony) {
        List<WayOut> ways = new ArrayList<>();
        FoodWays foodWays = colony.nest().foodWays();
        for (FoodWays.NewTunnel tunnel : foodWays.getNewTunnels()) {
            if (foodWays.isSpent(tunnel.tunnel().hole()) || colony.keepsOff(level, tunnel.food())) {
                continue;
            }
            List<BlockPos> cells = new ArrayList<>(tunnel.tunnel().tunnel());
            if (tunnel.gallery() != null) {
                cells.addAll(tunnel.gallery());
            }
            ways.add(new WayOut(tunnel.food(), List.copyOf(cells)));
        }
        List<FoodWays.Runway> runways = foodWays.getRunways();
        if (!runways.isEmpty()) {
            List<com.antcolonies.colony.NestPlan.ForagingTunnel> tunnels = colony.foragingHoles().tunnels(level);
            for (FoodWays.Runway runway : runways) {
                if (colony.keepsOff(level, runway.food())) {
                    continue;
                }
                List<BlockPos> cells = new ArrayList<>();
                for (com.antcolonies.colony.NestPlan.ForagingTunnel tunnel : tunnels) {
                    if (tunnel.hole().equals(runway.hole())) {
                        cells.addAll(tunnel.tunnel());
                        break;
                    }
                }
                cells.addAll(runway.cells());
                ways.add(new WayOut(runway.food(), List.copyOf(cells)));
            }
        }
        return ways;
    }

    /** How many of these spots termites can feed at by day. */
    public static int byDay(List<Spot> spots) {
        return (int) spots.stream().filter(Spot::byDay).count();
    }

    private static Spot spot(ServerLevel level, Colony colony, String kind, BlockPos food, List<BlockPos> cells, boolean byDay) {
        BlockPos middle = colony.getCenter();
        int fromMiddle = (int) Math.round(Math.hypot(food.getX() - middle.getX(), food.getZ() - middle.getZ()));
        Set<BlockPos> looked = new HashSet<>();
        int casts = 0, crust = 0;
        List<BlockPos> round = new ArrayList<>(cells);
        round.add(food);
        for (BlockPos at : round) {
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-MUD_ROUND, -MUD_ROUND, -MUD_ROUND), at.offset(MUD_ROUND, MUD_ROUND, MUD_ROUND))) {
                if (!LandReady.at(level, pos) || !looked.add(pos.immutable())) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.is(SavannaBlocks.MUD_CAST.get())) {
                    casts++;
                } else if (state.is(SavannaBlocks.MUD_CRUST.get())) {
                    crust++;
                }
            }
        }
        return new Spot(kind, food, woodName(level, food), fromMiddle, cells.size(), casts, crust, byDay);
    }

    private static String woodName(ServerLevel level, BlockPos food) {
        if (!LandReady.at(level, food)) {
            return "not loaded";
        }
        BlockState state = level.getBlockState(food);
        if (state.isAir()) {
            return "gone (eaten)";
        }
        return state.getBlock().getName().getString();
    }

    /** How far (blocks, flat) the mound's foot reaches from its middle, or 0 with no mound shape. */
    public static int moundReach(ServerLevel level, Colony colony) {
        Mound mound = colony.part(SavannaParts.MOUND).orElse(null);
        if (mound == null) {
            return 0;
        }
        BlockPos center = colony.getCenter();
        int reach = 0;
        for (BlockPos part : mound.getParts(level).keySet()) {
            reach = Math.max(reach, (int) Math.ceil(Math.hypot(part.getX() - center.getX(), part.getZ() - center.getZ())));
        }
        return reach;
    }

    /** The direction the spot lies from the mound's middle, as a compass word (north, south-east, ...). */
    public static String bearing(Colony colony, BlockPos food) {
        double dx = food.getX() - colony.getCenter().getX(), dz = food.getZ() - colony.getCenter().getZ();
        if (dx == 0 && dz == 0) {
            return "here";
        }
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // (Minecraft: south is +z, east is +x)
        return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
    }
}
