package com.antcolonies.command;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.tree.Forest;
import com.antcolonies.tree.TreeLife;
import com.antcolonies.tree.TreeShapes;
import com.antcolonies.tree.TreeStage;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The giant savanna trees' debug commands (the split, step S3: moved out of the shared {@link ColonyCommands}, with the
 * trees in the savanna), under the same {@code /antcolonies} name: the game joins them into one command.
 */
@EventBusSubscriber(modid = AntColoniesMod.MODID)
public class TreeCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antcolonies")
                .requires(source -> source.hasPermission(2))
                // The giant savanna trees in the tree book (M7.5 step 4): how many, and the nearest little ones with their
                // place and age, to find a new seedling after rain or watch a young tree grow.
                // Step 5, for testing: "kill" (the nearest grown tree dies now) and "fell" (the nearest acacia sheds and falls now).
                .then(Commands.literal("trees").executes(context -> trees(context.getSource()))
                        .then(Commands.literal("kill").executes(context -> treeDeath(context.getSource(), false)))
                        .then(Commands.literal("fell").executes(context -> treeDeath(context.getSource(), true)))));
    }

    /** Step 5, for testing: the nearest grown tree within 32 blocks dies now, or (fell) an acacia sheds and falls now. */
    private static int treeDeath(CommandSourceStack source, boolean fell) {
        ServerLevel level = source.getLevel();
        Forest forest = Forest.get(level);
        BlockPos at = BlockPos.containing(source.getPosition());
        java.util.List<Forest.Tree> near = forest.near(at, 32);
        near.sort(Comparator.comparingDouble(t -> t.foot.distSqr(at)));
        for (Forest.Tree t : near) {
            if (t.age < TreeLife.YOUNG_END || t.placedAge < 0 || (fell ? !forest.fellNow(level, t) : !forest.killNow(level, t))) {
                continue;
            }
            String what = t.kind.replace('_', ' ') + " at " + t.foot.getX() + " " + t.foot.getY() + " " + t.foot.getZ();
            source.sendSuccess(() -> Component.literal(fell ? "The " + what + " creaks and falls." : "The " + what + " dies now: watch its leaves wither."), false);
            return 1;
        }
        source.sendFailure(Component.literal(fell ? "No standing grown acacia within 32 blocks." : "No living grown tree within 32 blocks."));
        return 0;
    }

    private static int trees(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Forest forest = Forest.get(level);
        BlockPos at = BlockPos.containing(source.getPosition());
        java.util.List<Forest.Tree> near = forest.near(at, 128);
        near.sort(Comparator.comparingDouble(t -> t.foot.distSqr(at)));
        StringBuilder out = new StringBuilder(String.format("Tree book: %d trees (day %d; so far %d growth steps, %d little trees died, %d seedlings came up, %d grown trees died)",
                forest.size(), Forest.today(level), forest.grownSteps, forest.deaths, forest.sprouted, forest.killed));
        int shown = 0;
        for (Forest.Tree t : near) {
            if (!t.living() || t.age >= TreeShapes.GROWN || shown >= 8) {
                continue;
            }
            String what = t.age < TreeLife.YOUNG ? "seedling" : "young";
            out.append(String.format("\n  %s %s at %d %d %d, age %.2f%s", t.kind.replace('_', ' '), what, t.foot.getX(), t.foot.getY(), t.foot.getZ(),
                    t.age, t.placedAge < 0 ? " (not come up yet)" : ""));
            shown++;
        }
        if (shown == 0) {
            out.append("\n  no seedlings or young trees within 128 blocks");
        }
        // Step 5: the nearest dead trees, their stage and what killed them.
        int dead = 0;
        long today = Forest.today(level);
        for (Forest.Tree t : near) {
            if (!t.dead() || dead >= 6) {
                continue;
            }
            String since = t.died == Forest.DIED_UNKNOWN ? "dead in new land" : "died " + (today - t.died) + " days ago";
            String cause = t.cause.isEmpty() ? "" : " (" + (t.cause.equals("age") ? "old age" : t.cause) + ")";
            String stage = t.stage == TreeStage.LIVING ? "withering" : t.stage.id;
            out.append(String.format("\n  dead %s at %d %d %d: %s, %s%s", t.kind.replace('_', ' '), t.foot.getX(), t.foot.getY(), t.foot.getZ(),
                    stage, since, cause));
            dead++;
        }
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }
}
