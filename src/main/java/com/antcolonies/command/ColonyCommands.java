package com.antcolonies.command;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.TermiteQueenEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                // Sets how far the nearest termite queen has swollen (0 = just founded, 100 = full grown), to see her
                // grow without waiting for a big colony. She never shrinks by herself, so the size stays.
                .then(Commands.literal("queensize")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> queenSize(context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))));
    }

    private static int queenSize(CommandSourceStack source, int percent) {
        Vec3 at = source.getPosition();
        TermiteQueenEntity queen = source.getLevel().getEntitiesOfClass(TermiteQueenEntity.class, new AABB(BlockPos.containing(at)).inflate(32))
                .stream().min(Comparator.comparingDouble(q -> q.distanceToSqr(at))).orElse(null);
        if (queen == null) {
            source.sendFailure(Component.literal("No termite queen within 32 blocks."));
            return 0;
        }
        queen.setSize(percent / 100.0F);
        source.sendSuccess(() -> Component.literal("Termite queen set to " + percent + "% grown."), false);
        return 1;
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
                    + " | food " + colony.food().getFood() + " (rich " + colony.food().getRichSeeds() + ", eaten " + colony.food().getSeedsEaten() + ")"
                    + " | bread " + colony.bakery().getBread() + " (rich " + colony.bakery().getRichBread() + ", milling " + colony.bakery().getMilling() + ")"
                    + " | queen fed " + colony.queenCare().getQueenBread() + "/" + colony.profile().queen().meal() + ", rich " + colony.queenCare().getQueenRich() + "/"
                    + colony.profile().queen().richMeal() + incubation(colony, level)
                    + " | queen unfed " + unfedMinutes(colony, level) + " of " + colony.profile().queen().starveTicks() / 1200 + " min"
                    + " | eggs " + colony.brood().countEggs(level) + " | larvae " + colony.brood().countLarvae(level)
                    + " | dead ants " + colony.refuse().getCorpses(level).size() + " (refuse chamber " + colony.refuse().getRefuseCorpses(level).size() + ")";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return colonies.size();
    }

    /** How many minutes the queen has gone without food she wanted (0 while she is satisfied). */
    private static long unfedMinutes(Colony colony, ServerLevel level) {
        if (colony.queenCare().getQueenLastFed() == Colony.NOT_SET) {
            return 0;
        }
        return Math.max(0, level.getGameTime() - colony.queenCare().getQueenLastFed()) / 1200;
    }

    /** " (egg in 3:25)" while the queen carries an egg, otherwise nothing. */
    private static String incubation(Colony colony, ServerLevel level) {
        if (!colony.queenCare().isIncubating()) {
            return "";
        }
        long seconds = Math.max(0, colony.queenCare().getEggDue() - level.getGameTime()) / 20;
        String burst = colony.queenCare().getBurstLeft() > 0 ? ", burst: " + colony.queenCare().getBurstLeft() + " left" : "";
        return String.format(" (egg in %d:%02d%s)", seconds / 60, seconds % 60, burst);
    }
}
