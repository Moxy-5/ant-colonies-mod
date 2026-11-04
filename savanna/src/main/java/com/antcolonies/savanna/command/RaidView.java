package com.antcolonies.savanna.command;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.colony.RaidColumn;
import com.antcolonies.savanna.colony.Raids;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.colony.ScentTrails;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleLoads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * M8 raid rework R4, the raid view ({@code /antcolonies raid show}, a debugging aid): for each player who turned it on,
 * every {@link #EVERY} ticks, particles over the Matabele colonies' scent trails and raiders near them, only that
 * player seeing them. Trail points: green when fresh, darkening to brown as the scent fades, grey once no longer smelt,
 * red where broken; the spot a trail leads to a yellow column. Over each raider's head its state: white marching (the
 * front bigger), grey forming, yellow gathering or regrouping, red fighting, pink collecting or spreading, blue carrying
 * termites, orange casting about at a gap, light green homing; cyan a scout out searching, purple a hurt raider
 * limping home.
 */
public final class RaidView {
    private static final int EVERY = 10;
    /** Colonies this near the player are shown (blocks), and trail points this near. */
    private static final double COLONIES = 128.0;
    private static final double POINTS = 64.0;

    private static final Set<UUID> watching = new HashSet<>();

    private RaidView() {
    }

    /** This player left: the view forgets it. */
    public static void forget(UUID player) {
        watching.remove(player);
    }

    /** The server stops: nobody watches any more (a new world starts with the view off). */
    public static void clear() {
        watching.clear();
    }

    /** Turns the view on or off for this player; true if it is on now. */
    public static boolean toggle(ServerPlayer player) {
        if (!watching.remove(player.getUUID())) {
            watching.add(player.getUUID());
            return true;
        }
        return false;
    }

    /** Draws the view for the players watching in this world (called each tick by the savanna's events). */
    public static void tick(ServerLevel level) {
        if (watching.isEmpty() || level.getGameTime() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (watching.contains(player.getUUID())) {
                draw(level, player);
            }
        }
    }

    private static void draw(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        for (Colony colony : ColonyManager.get(level).getColonies()) {
            if (!MatabeleEntity.SPECIES.equals(colony.getSpecies()) || !colony.getCenter().closerToCenterThan(player.position(), COLONIES)) {
                continue;
            }
            Raids raids = colony.part(SavannaParts.RAIDS).orElse(null);
            if (raids == null) {
                continue;
            }
            for (ScentTrails.Trail trail : raids.trails().all()) {
                boolean alive = trail.alive(raids.trailNumbers(), now);
                double fresh = trail.freshness(raids.trailNumbers(), now);
                java.util.List<BlockPos> points = trail.points();
                for (int i = 0; i < points.size(); i++) {
                    BlockPos p = points.get(i);
                    if (!p.closerToCenterThan(player.position(), POINTS)) {
                        continue;
                    }
                    Vector3f colour = trail.isBroken(i) ? new Vector3f(1.0F, 0.1F, 0.1F) : !alive ? new Vector3f(0.5F, 0.5F, 0.5F)
                            : new Vector3f((float) (0.35 - 0.05 * fresh), (float) (0.3 + 0.7 * fresh), (float) (0.1 + 0.2 * fresh));
                    dust(level, player, p.getX() + 0.5, p.getY() + 0.15, p.getZ() + 0.5, colour, trail.isBroken(i) ? 1.3F : 0.9F);
                }
                BlockPos to = trail.to();
                if (alive && to.closerToCenterThan(player.position(), POINTS)) {
                    for (int up = 0; up < 3; up++) {
                        dust(level, player, to.getX() + 0.5, to.getY() + 0.5 + up * 0.6, to.getZ() + 0.5, new Vector3f(1.0F, 0.9F, 0.1F), 1.2F);
                    }
                }
            }
            RaidColumn column = raids.column();
            if (column != null) {
                for (UUID id : column.members()) {
                    if (level.getEntity(id) instanceof MatabeleEntity ant) {
                        over(level, player, ant, stateColour(column, ant), id.equals(column.leader()) ? 1.6F : 1.0F);
                    }
                }
            }
            for (UUID id : raids.scoutsOut()) {
                if (level.getEntity(id) instanceof MatabeleEntity ant) {
                    over(level, player, ant, new Vector3f(0.1F, 1.0F, 1.0F), 1.3F);
                }
            }
            for (UUID id : raids.limpers()) {
                if (level.getEntity(id) instanceof MatabeleEntity ant) {
                    over(level, player, ant, new Vector3f(0.7F, 0.2F, 1.0F), 1.2F);
                }
            }
        }
    }

    private static Vector3f stateColour(RaidColumn column, MatabeleEntity ant) {
        if (column.isCasting(ant.getUUID())) {
            return new Vector3f(1.0F, 0.55F, 0.0F);
        }
        if (ant.getFightSpot() != null) {
            return new Vector3f(1.0F, 0.0F, 0.0F);
        }
        if (MatabeleLoads.termitesHeldBy(ant) > 0) {
            return new Vector3f(0.2F, 0.4F, 1.0F);
        }
        return switch (column.stage()) {
            case FORMING -> new Vector3f(0.6F, 0.6F, 0.6F);
            case GATHERING, REGROUPING -> new Vector3f(1.0F, 0.9F, 0.1F);
            case ATTACKING -> new Vector3f(1.0F, 0.3F, 0.3F);
            case COLLECTING, SPREADING -> new Vector3f(1.0F, 0.5F, 0.8F);
            case HOMING -> new Vector3f(0.5F, 1.0F, 0.5F);
            default -> new Vector3f(1.0F, 1.0F, 1.0F);
        };
    }

    private static void over(ServerLevel level, ServerPlayer player, Entity ant, Vector3f colour, float scale) {
        if (ant.closerThan(player, POINTS)) {
            dust(level, player, ant.getX(), ant.getY() + ant.getBbHeight() + 0.45, ant.getZ(), colour, scale);
        }
    }

    private static void dust(ServerLevel level, ServerPlayer player, double x, double y, double z, Vector3f colour, float scale) {
        level.sendParticles(player, new DustParticleOptions(colour, scale), true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
