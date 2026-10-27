package com.antcolonies.savanna.command;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.tree.Forest;
import com.antcolonies.savanna.tree.HeartRot;
import com.antcolonies.savanna.tree.TreeLife;
import com.antcolonies.savanna.tree.TreeShapes;
import com.antcolonies.savanna.tree.TreeStage;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
@EventBusSubscriber(modid = SavannaMod.MODID)
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
                        .then(Commands.literal("fell").executes(context -> treeDeath(context.getSource(), true)))
                        // Dead wood for the raids, part 4, for testing: heart rot in the nearest grown acacia, got in so many
                        // days ago (0 hidden, 6 brackets, 16 hollow).
                        .then(Commands.literal("rot").executes(context -> rot(context.getSource(), (int) HeartRot.HOLLOW_FROM))
                                .then(Commands.argument("days", IntegerArgumentType.integer(0, 1000))
                                        .executes(context -> rot(context.getSource(), IntegerArgumentType.getInteger(context, "days")))))));
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

    /** Part 4, for testing: heart rot got into the nearest living grown acacia within 32 blocks {@code days} days ago. */
    private static int rot(CommandSourceStack source, int days) {
        ServerLevel level = source.getLevel();
        Forest forest = Forest.get(level);
        BlockPos at = BlockPos.containing(source.getPosition());
        java.util.List<Forest.Tree> near = forest.near(at, 32);
        near.sort(Comparator.comparingDouble(t -> t.foot.distSqr(at)));
        for (Forest.Tree t : near) {
            if (!t.living() || !HeartRot.canRot(t) || t.placedAge < 0) {
                continue;
            }
            long today = Forest.today(level);
            forest.update(level, t, today); // any days owed first
            t.rotFrom = today - days;
            forest.setDirty();
            forest.update(level, t, today);
            String what = t.kind.replace('_', ' ') + " at " + t.foot.getX() + " " + t.foot.getY() + " " + t.foot.getZ();
            String stage = HeartRot.stage(t, today).name().toLowerCase(java.util.Locale.ROOT);
            source.sendSuccess(() -> Component.literal("Heart rot in the " + what + ", " + days + " days in: " + stage
                    + " (brackets from day " + (int) HeartRot.BRACKETS_FROM + ", hollow from day " + (int) HeartRot.HOLLOW_FROM + ")."), false);
            return 1;
        }
        source.sendFailure(Component.literal("No living grown acacia within 32 blocks."));
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
            String cause = t.cause.isEmpty() ? "" : " (" + (t.cause.equals("age") ? "old age" : t.cause.equals("rot") ? "heart rot" : t.cause)
                    + (t.rotFrom != HeartRot.NONE && !t.cause.equals("rot") ? ", heart rot" : "")
                    + (t.crumbled > 0 ? ", crumbling to pulp" : "") + ")";
            String stage = t.stage == TreeStage.LIVING ? "withering" : t.stage.id;
            out.append(String.format("\n  dead %s at %d %d %d: %s, %s%s", t.kind.replace('_', ' '), t.foot.getX(), t.foot.getY(), t.foot.getZ(),
                    stage, since, cause));
            dead++;
        }
        // Part 4: the nearest living trees with heart rot, and how far along.
        int rotting = 0;
        for (Forest.Tree t : near) {
            if (!t.living() || t.rotFrom == HeartRot.NONE || rotting >= 6) {
                continue;
            }
            out.append(String.format("\n  heart rot in %s at %d %d %d: %s, day %d", t.kind.replace('_', ' '), t.foot.getX(), t.foot.getY(), t.foot.getZ(),
                    HeartRot.stage(t, today).name().toLowerCase(java.util.Locale.ROOT), today - t.rotFrom));
            rotting++;
        }
        source.sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }
}
