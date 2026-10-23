package com.antcolonies.command;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyHooks;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.debug.Perf;
import com.antcolonies.species.SpeciesProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                // Adds that many workers to the nearest colony, as numbers inside its nest, of every age (and, for a
                // species with soldiers, as many soldiers as so many workers want), to see a big colony at once.
                .then(Commands.literal("population")
                        .then(Commands.argument("workers", IntegerArgumentType.integer(1, 1000000))
                                .executes(context -> population(context.getSource(), IntegerArgumentType.getInteger(context, "workers")))))
                // The stress test (performance step 1): the nearest colony keeps this many real mobs out at once (adding
                // workers to its numbers if it has too few), until "stress off" or a restart. "perf" shows the stopwatch.
                .then(Commands.literal("stress")
                        .then(Commands.literal("off").executes(context -> stress(context.getSource(), 0)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                                .executes(context -> stress(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("perf").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(Perf.report()), false);
                    return 1;
                })));
        // (The termites' own commands, mound, mushrooms, drum, alarm, queensize and age: TermiteCommands; the trees: TreeCommands.)
    }

    private static int population(CommandSourceStack source, int workers) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        Colony colony = ColonyManager.get(level).getColonies().stream()
                .filter(c -> c.getCenter().distSqr(at) <= 64 * 64)
                .min(Comparator.comparingDouble(c -> c.getCenter().distSqr(at))).orElse(null);
        if (colony == null) {
            source.sendFailure(Component.literal("No colony within 64 blocks."));
            return 0;
        }
        String caste = com.antcolonies.colony.Caste.WORKER;
        int room = colony.profile().colony().maxWorkers() - colony.getCount(caste);
        int added = Math.max(0, Math.min(workers, room));
        // Every age from just grown up to old (not about to die), spread evenly.
        long now = level.getGameTime();
        long oldest = Math.max(1, Math.round(colony.profile().worker().lifespanTicks() * 0.8));
        for (int i = 0; i < added; i++) {
            colony.population().addNewMembers(caste, now - oldest * i / Math.max(1, added), 1);
        }
        StringBuilder soldiers = new StringBuilder();
        for (ColonyHooks.Castes castes : colony.hooks().all(ColonyHooks.CASTES)) {
            soldiers.append(castes.addFor(colony.getCount(caste), now)); // (the termite soldiers so many workers want)
        }
        ColonyManager.get(level).markChanged();
        String done = added + " workers" + soldiers + " added to the colony at " + colony.getCenter().toShortString()
                + " (inside the nest; they come out a few a second where you can't see)"
                + (added < workers ? ". The colony is full: " + colony.profile().colony().maxWorkers() + " workers at most." : ".");
        source.sendSuccess(() -> Component.literal(done), false);
        return added;
    }

    private static int stress(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        Colony colony = ColonyManager.get(level).getColonies().stream()
                .filter(c -> c.getCenter().distSqr(at) <= 64 * 64)
                .min(Comparator.comparingDouble(c -> c.getCenter().distSqr(at))).orElse(null);
        if (colony == null) {
            source.sendFailure(Component.literal("No colony within 64 blocks."));
            return 0;
        }
        if (count == 0) {
            colony.population().setStress(null);
            source.sendSuccess(() -> Component.literal("Stress test off: the colony goes back to "
                    + colony.profile().colony().realCap() + " real mobs at most (the extra ones go back in where you can't see)."), false);
            return 1;
        }
        // Enough in the numbers to fill the cap (the soldiers come with the workers).
        String caste = com.antcolonies.colony.Caste.WORKER;
        int short_ = count - colony.getCount(caste);
        if (short_ > 0) {
            population(source, short_);
        }
        colony.population().setStress(count);
        source.sendSuccess(() -> Component.literal("Stress test: " + count + " real mobs at most for the colony at "
                + colony.getCenter().toShortString() + ". They come out 10 a second where you can't see. "
                + "Use /antcolonies perf for the stopwatch (it reports every 5 seconds), /antcolonies stress off to end."), false);
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
                    + (colony.isQueenless() ? " | QUEENLESS: no new eggs, dwindling" : " | queen " + colony.getQueenId().toString().substring(0, 8))
                    + (colony.profile().nest().levelUnlocks().isEmpty() ? ""
                            : " | nest levels open " + Math.min(colony.nest().levelsUnlocked(level), colony.profile().nest().maxLevels()) + " of " + colony.profile().nest().maxLevels())
                    + " | members " + colony.getMemberCount() + colony.population().describe(level) + colony.away().describe(level) + colony.field().describe()
                    + " | " + colony.describeJobs(level)
                    + (colony.profile().food().makesBread()
                            ? " | food " + colony.food().getFood() + " (rich " + colony.food().getRichSeeds() + ", eaten " + colony.food().getSeedsEaten() + ")"
                                    + lines(colony, ColonyHooks.LIST_STORE, level)
                            : colony.hooks().first(ColonyHooks.LIST_FOOD) != null ? lines(colony, ColonyHooks.LIST_FOOD, level)
                            : " | food " + colony.food().getFood() + " pieces in the store")
                    + (colony.profile().laying().isPresent()
                            ? " | queen holds " + colony.queenCare().getQueenBread() + "/" + colony.profile().queen().meal() + " nodules" + nextEgg(colony, level)
                            : " | queen fed " + colony.queenCare().getQueenBread() + "/" + colony.profile().queen().meal() + ", rich " + colony.queenCare().getQueenRich() + "/"
                                    + colony.profile().queen().richMeal() + incubation(colony, level))
                    + " | queen unfed " + unfedMinutes(colony, level) + " of " + colony.profile().queen().starveTicks() / 1200 + " min"
                    + " | eggs " + colony.brood().countEggs(level) + " | larvae " + colony.brood().countLarvae(level)
                    + lines(colony, ColonyHooks.LIST_BROOD, level)
                    + colony.foodMap().describe() + lines(colony, ColonyHooks.LIST_SEARCH, level)
                    + colony.trails().describeMemory(level) + colony.danger().describe(level) + lines(colony, ColonyHooks.LIST_DANGER, level)
                    + colony.neighbours().describe(level)
                    + " | dead ants " + colony.refuse().getCorpses(level).size() + " (refuse chamber " + colony.refuse().getRefuseCorpses(level).size() + ")"
                    + lines(colony, ColonyHooks.LIST_BUILDING, level) + foraging(colony, level);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return colonies.size();
    }

    /** The parts' words for one slot of the list, in the parts' order: nothing for a species without such parts. */
    private static String lines(Colony colony, ColonyHooks.Kind<ColonyHooks.ListLine> slot, ServerLevel level) {
        StringBuilder out = new StringBuilder();
        for (ColonyHooks.ListLine line : colony.hooks().all(slot)) {
            out.append(line.line(level));
        }
        return out.toString();
    }

    /** ", next egg in 1:20" while a queen who lays a steady stream can lay, otherwise ", not laying". */
    private static String nextEgg(Colony colony, ServerLevel level) {
        long next = colony.queenCare().getNextStreamEgg();
        if (next == Colony.NOT_SET) {
            return ", not laying";
        }
        long seconds = Math.max(0, next - level.getGameTime()) / 20;
        return String.format(", next egg in %d:%02d", seconds / 60, seconds % 60);
    }

    /** " | foraging now (night, rain), holes open 1 of 2" for colonies that keep a schedule or have foraging holes, otherwise nothing. */
    private static String foraging(Colony colony, ServerLevel level) {
        int holes = colony.profile().nest().foragingHoles();
        java.util.Optional<SpeciesProfile.DayClock> clock = colony.profile().food().dayClock();
        if (clock.isPresent()) {
            SpeciesProfile.DayClock c = clock.get();
            float heat = colony.heat(level);
            int rest = c.restTicks(heat);
            return " | " + (colony.isForagingTime(level) ? "foraging now" : "staying in") + " (it is "
                    + SpeciesProfile.DayClock.clock(colony.timeOfDay(level)) + "; out " + SpeciesProfile.DayClock.clock(c.outFrom())
                    + " to " + SpeciesProfile.DayClock.clock(c.inBy()) + (rest > 0 ? ", resting " + SpeciesProfile.DayClock.clock(c.restStart(heat))
                    + " to " + SpeciesProfile.DayClock.clock(c.restStart(heat) + rest) : ", no midday rest")
                    + String.format(" (biome heat %.1f)", heat) + (c.notInRain() ? ", never in rain" : "") + ")"
                    + lines(colony, ColonyHooks.LIST_DAY, level);
        }
        if (!colony.keepsASchedule() && holes == 0) {
            return "";
        }
        String when = String.join(", ", colony.profile().food().forageAt());
        String line = " | " + (colony.isForagingTime(level) ? "foraging now" : "staying in") + " (out at: " + when + ")";
        if (holes > 0) {
            line += ", holes open " + colony.foragingHoles().countOpen(level) + " of " + colony.foragingHoles().tunnels(level).size(); line += colony.foragingHoles().describeNewHoles(level) + colony.foragingHoles().describeRunways(level) + lines(colony, ColonyHooks.LIST_HOLES, level);
        }
        return line;
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
