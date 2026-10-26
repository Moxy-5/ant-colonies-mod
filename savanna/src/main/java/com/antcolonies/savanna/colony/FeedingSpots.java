package com.antcolonies.savanna.colony;

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
            boolean byDay = colony.food().isFeedableFromInside(level, tunnel.food());
            for (int i = 0; i < gallery.size() && !byDay; i++) {
                BlockPos over = gallery.get(i).above();
                byDay = !level.getBlockState(over).isAir() && colony.food().isFeedableFromInside(level, over);
            }
            spots.add(spot(level, colony, "gallery", tunnel.food(), gallery, byDay));
        }
        for (FoodWays.Runway runway : ways.getRunways()) {
            spots.add(spot(level, colony, "runway", runway.food(), runway.cells(), ways.isRunwayClosed(level, runway)));
        }
        spots.sort(java.util.Comparator.comparingInt(Spot::fromMiddle));
        return spots;
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
                if (!level.isLoaded(pos) || !looked.add(pos.immutable())) {
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
        if (!level.isLoaded(food)) {
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
