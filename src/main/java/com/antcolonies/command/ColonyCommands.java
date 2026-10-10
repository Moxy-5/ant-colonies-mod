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
                    + " | members " + colony.getMemberCount();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return colonies.size();
    }
}
