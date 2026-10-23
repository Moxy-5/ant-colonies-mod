package com.antcolonies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;


import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyNews;
import com.antcolonies.debug.Perf;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.GroundShakes;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The framework's game events: the colonies' clock, insects going out of range, the player's footsteps and break-ins
 * told to the colonies near, the stopwatch, and the creative menu's rows (the split, step S4: each species' own events
 * in {@link SavannaEvents} and {@link DesertEvents}).
 */
@EventBusSubscriber(modid = AntColoniesMod.MODID)
public class ModEvents {
    /**
     * One of a colony's insects goes out of range (its chunk stops being tracked, before Minecraft unloads it; one that
     * is killed or put away has a removal reason already): its colony writes down what it is (the brain, part 2, see
     * {@link com.antcolonies.colony.AwayLife#freeze}).
     */
    @SubscribeEvent
    public static void onLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.entity.Entity entity = event.getEntity();
        var reason = entity.getRemovalReason();
        if (reason != null && reason != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            return;
        }
        java.util.UUID colonyId = entity instanceof com.antcolonies.entity.AbstractAntEntity mob ? mob.getColonyId()
                : entity instanceof com.antcolonies.entity.AntEggEntity egg ? egg.getColonyId()
                : entity instanceof com.antcolonies.entity.AntLarvaEntity larva ? larva.getColonyId() : null;
        Colony colony = colonyId == null ? null : ColonyManager.get(level).getColony(colonyId);
        if (colony != null) {
            colony.away().freeze(level, entity);
        }
    }

    /** A player jumping on a termite mound: footsteps, the alarm's first level (see {@code colony.Alarm}). */
    // Every colony's own clock, once a second, whether or not its queen is loaded (or alive).
    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.getGameTime() % 20 == 0) {
                boolean timed = Perf.begin(Perf.Part.BRAIN);
                try {
                    ColonyManager.get(level).tickColonies(level);
                } finally {
                    if (timed) {
                        Perf.end();
                    }
                }
            }
        }
    }

    /** The stopwatch closes each server tick (see {@link Perf}). */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        Perf.endOfTick(event.getServer());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level) {
            GroundShakes.hard(player); // termites nearby feel it (NoticeGoal)
            for (Colony colony : coloniesNear(level, player.blockPosition())) {
                colony.news().tell(level, ColonyNews.PLAYER_STAMPED, new ColonyNews.PlayerStamped(player)); // (the termites' alarm)
            }
        }
    }

    /** A player landing shakes the ground: termites nearby feel it (see {@code NoticeGoal}). */
    @SubscribeEvent
    public static void onLand(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getDistance() > 0.5F) {
            GroundShakes.hard(player);
        }
    }

    /** A player breaking into a termite mound or nest: a break-in, the alarm's third level (see {@code colony.Alarm}). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (Colony colony : coloniesNear(level, event.getPos())) {
                colony.news().tell(level, ColonyNews.BLOCK_BROKEN, new ColonyNews.BlockBroken(event.getPos(), event.getPlayer())); // (the termites' alarm)
            }
        }
    }

    /** The colonies whose ground (the food radius and a bit) this spot is in. */
    private static java.util.List<Colony> coloniesNear(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return ColonyManager.get(level).getColonies().stream()
                .filter(colony -> {
                    double reach = colony.foragingRadius() + 8.0;
                    return colony.getCenter().distSqr(pos) <= reach * reach;
                })
                .toList();
    }

    // --- the creative menu: each species signs up its rows; they are added in the order of their places, so the rows of
    // different species can sit between each other (the split, step S4: the menu kept in today's order) ---------------

    private record TabRows(ResourceKey<CreativeModeTab> tab, int place, Consumer<BuildCreativeModeTabContentsEvent> rows) {
    }

    private static final List<TabRows> TAB_ROWS = new ArrayList<>();

    /**
     * Signs up rows for a creative menu tab ({@code rows} adds them), at {@code place}: rows with a lower place come
     * first. Called while the mod loads.
     */
    public static void addToCreativeTab(ResourceKey<CreativeModeTab> tab, int place, Consumer<BuildCreativeModeTabContentsEvent> rows) {
        TAB_ROWS.add(new TabRows(tab, place, rows));
        TAB_ROWS.sort(Comparator.comparingInt(TabRows::place));
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        for (TabRows rows : TAB_ROWS) {
            if (event.getTabKey() == rows.tab()) {
                rows.rows().accept(event);
            }
        }
    }
}
