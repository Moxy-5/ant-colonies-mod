package com.antcolonies.command;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Debug commands for testing. */
@EventBusSubscriber(modid = AntColoniesMod.MODID)
public class ColonyCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antcolonies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(context -> list(context.getSource()))));
    }

    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var colonies = ColonyManager.get(level).getColonies();
        if (colonies.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No colonies in this dimension."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Colonies in this dimension: " + colonies.size()), false);
        for (Colony colony : colonies) {
            String line = "- " + colony.getId().toString().substring(0, 8)
                    + " | " + colony.getSpecies()
                    + " | center " + colony.getCenter().toShortString()
                    + " | queen " + colony.getQueenId().toString().substring(0, 8)
                    + " | members " + colony.getMemberCount()
                    + " | food " + colony.getFood() + " (rich " + colony.getRichSeeds() + ", eaten " + colony.getSeedsEaten() + ")"
                    + " | bread " + colony.getBread() + " (rich " + colony.getRichBread() + ", milling " + colony.getMilling() + ")"
                    + " | queen fed " + colony.getQueenBread() + "/" + Colony.QUEEN_MEAL + ", rich " + colony.getQueenRich() + "/"
                    + Colony.RICH_MEAL + incubation(colony, level)
                    + " | queen unfed " + unfedMinutes(colony, level) + " of " + Colony.QUEEN_STARVE_TICKS / 1200 + " min"
                    + " | eggs " + colony.countEggs(level) + " | larvae " + colony.countLarvae(level)
                    + " | dead ants " + colony.getCorpses(level).size() + " (refuse chamber " + colony.getRefuseCorpses(level).size() + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return colonies.size();
    }

    /** How many minutes the queen has gone without food she wanted (0 while she is satisfied). */
    private static long unfedMinutes(Colony colony, ServerLevel level) {
        if (colony.getQueenLastFed() == Colony.NOT_SET) {
            return 0;
        }
        return Math.max(0, level.getGameTime() - colony.getQueenLastFed()) / 1200;
    }

    /** " (egg in 3:25)" while the queen carries an egg, otherwise nothing. */
    private static String incubation(Colony colony, ServerLevel level) {
        if (!colony.isIncubating()) {
            return "";
        }
        long seconds = Math.max(0, colony.getEggDue() - level.getGameTime()) / 20;
        String burst = colony.getBurstLeft() > 0 ? ", burst: " + colony.getBurstLeft() + " left" : "";
        return String.format(" (egg in %d:%02d%s)", seconds / 60, seconds % 60, burst);
    }
}
