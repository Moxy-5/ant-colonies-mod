package com.antcolonies.savanna.command;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.tree.DoumShape;
import com.antcolonies.savanna.tree.FigShape;
import com.antcolonies.savanna.tree.Forest;
import com.antcolonies.savanna.tree.HeartRot;
import com.antcolonies.savanna.tree.TreeLife;
import com.antcolonies.savanna.tree.TreeShapes;
import com.antcolonies.savanna.tree.TreeStage;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
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
                                        .executes(context -> rot(context.getSource(), IntegerArgumentType.getInteger(context, "days")))))
                        // Floodplain trees step 4, for looking: a sycamore fig at an age or dead stage (seed, sapling,
                        // young, mature, grown, dead1, dead2, dead3) where you stand, as the model viewer draws it; a seed
                        // picks the tree (the viewer's seed), else one at random.
                        .then(Commands.literal("fig").then(Commands.argument("age", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(FigShape.Age.values()).map(a -> a.id), builder))
                                .executes(context -> fig(context.getSource(), StringArgumentType.getString(context, "age"), -1))
                                .then(Commands.argument("seed", IntegerArgumentType.integer(1, 1_000_000))
                                        .executes(context -> fig(context.getSource(), StringArgumentType.getString(context, "age"),
                                                IntegerArgumentType.getInteger(context, "seed"))))))
                        // Floodplain trees step 6, for looking: a doum palm at an age or dead stage (seed, rosette, stem,
                        // fork, clump, grown, dead1, dead2, dead3) where you stand, as the model viewer draws it.
                        .then(Commands.literal("doum").then(Commands.argument("age", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(DoumShape.Age.values()).map(a -> a.id), builder))
                                .executes(context -> doum(context.getSource(), StringArgumentType.getString(context, "age"), -1))
                                .then(Commands.argument("seed", IntegerArgumentType.integer(1, 1_000_000))
                                        .executes(context -> doum(context.getSource(), StringArgumentType.getString(context, "age"),
                                                IntegerArgumentType.getInteger(context, "seed"))))))));
    }

    /** Floodplain trees step 4: a fig of this age (the viewer's names) and seed (-1 at random) set down where the player stands. */
    private static int fig(CommandSourceStack source, String ageId, int seed) {
        FigShape.Age age = FigShape.Age.byId(ageId);
        if (age == null) {
            source.sendFailure(Component.literal("No fig age " + ageId + ": seed, sapling, young, mature, grown, dead1, dead2 or dead3."));
            return 0;
        }
        int s = seed > 0 ? seed : 1 + source.getLevel().getRandom().nextInt(10_000);
        BlockPos foot = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        var states = FigShape.make(s, age).states();
        for (var e : states.long2ObjectEntrySet()) {
            level.setBlock(foot.offset(BlockPos.of(e.getLongKey())), e.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        source.sendSuccess(() -> Component.literal("A sycamore fig, " + age.id + ", seed " + s + ": " + states.size() + " blocks."), false);
        return 1;
    }

    /** Floodplain trees step 6: a doum palm of this age (the viewer's names) and seed (-1 at random) set down where the player stands. */
    private static int doum(CommandSourceStack source, String ageId, int seed) {
        DoumShape.Age age = DoumShape.Age.byId(ageId);
        if (age == null) {
            source.sendFailure(Component.literal("No doum age " + ageId + ": seed, rosette, stem, fork, clump, grown, dead1, dead2 or dead3."));
            return 0;
        }
        int s = seed > 0 ? seed : 1 + source.getLevel().getRandom().nextInt(10_000);
        var placed = DoumShape.make(s, age).placed();
        int set = placed.setDown(source.getLevel(), BlockPos.containing(source.getPosition()));
        source.sendSuccess(() -> Component.literal("A doum palm, " + age.id + ", seed " + s + ": " + set + " blocks."), false);
        return 1;
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
