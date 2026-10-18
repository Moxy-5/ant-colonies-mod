package com.antcolonies.command;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.GiantTermitomycesBlock;
import com.antcolonies.block.MoundPieceBlock;
import com.antcolonies.block.MoundSoil;
import com.antcolonies.block.TermiteMoundBlock;
import com.antcolonies.block.TermitomycesBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.debug.Perf;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.registry.ModBlocks;
import com.antcolonies.species.SpeciesProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.Comparator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
                }))
                // Sets how far the nearest termite queen has swollen (0 = just founded, 100 = full grown), to see her
                // grow without waiting for a big colony. She never shrinks by herself, so the size stays.
                .then(Commands.literal("queensize")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> queenSize(context.getSource(), IntegerArgumentType.getInteger(context, "percent")))))
                // Sets how tall the nearest termite mound may grow (the termites then build up to it), or with "instant"
                // puts the whole mound of that height up at once, to look at its shape.
                .then(Commands.literal("mound")
                        .then(Commands.argument("height", IntegerArgumentType.integer(1, 64))
                                .executes(context -> mound(context.getSource(), IntegerArgumentType.getInteger(context, "height"), false))
                                .then(Commands.literal("instant")
                                        .executes(context -> mound(context.getSource(), IntegerArgumentType.getInteger(context, "height"), true)))))
                // Sets how dry and hard the termite mound soil around you is (0 fresh, 1 setting, 2 dry, 3 old), every
                // finished mound block and cathedral piece within the radius (default 8 blocks), to see the ages
                // without waiting hours.
                // Termitomyces mushrooms round the nearest termite mound: with no number, as if a rain had just ended (as many
                // as its garden sends up, none if the garden is not healthy yet); with a number, that many whatever the
                // garden; with "giant", giants wherever they fit. "grow" moves every mushroom within 16 blocks on one stage
                // (to see them open and wilt without waiting days).
                .then(Commands.literal("mushrooms")
                        .executes(context -> mushrooms(context.getSource(), -1, false))
                        .then(Commands.literal("grow").executes(context -> growMushrooms(context.getSource())))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 32))
                                .executes(context -> mushrooms(context.getSource(), IntegerArgumentType.getInteger(context, "count"), false))
                                .then(Commands.literal("giant")
                                        .executes(context -> mushrooms(context.getSource(), IntegerArgumentType.getInteger(context, "count"), true)))))
                // Every termite soldier within 16 blocks drums (the head bang) for that many seconds (default 3).
                // The nearest termite colony's alarm goes up to that level where the player stands (step 6c), or calms down (0).
                .then(Commands.literal("drum")
                        .executes(context -> drum(context.getSource(), 3))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(context -> drum(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("alarm")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                                .executes(context -> alarm(context.getSource(), IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("age")
                        .then(Commands.argument("age", IntegerArgumentType.integer(0, MoundSoil.OLDEST))
                                .executes(context -> age(context.getSource(), IntegerArgumentType.getInteger(context, "age"), 8))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                        .executes(context -> age(context.getSource(), IntegerArgumentType.getInteger(context, "age"),
                                                IntegerArgumentType.getInteger(context, "radius")))))));
    }

    private static int drum(CommandSourceStack source, int seconds) {
        ServerLevel level = source.getLevel();
        var soldiers = level.getEntitiesOfClass(TermiteSoldierEntity.class, new AABB(BlockPos.containing(source.getPosition())).inflate(16));
        for (TermiteSoldierEntity soldier : soldiers) {
            soldier.drum(seconds * 20);
        }
        int count = soldiers.size();
        source.sendSuccess(() -> Component.literal(count == 0 ? "No termite soldier within 16 blocks."
                : count + " termite soldier" + (count == 1 ? "" : "s") + " drumming for " + seconds + " seconds."), false);
        return count;
    }

    private static int alarm(CommandSourceStack source, int to) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        Colony colony = nearestTermiteColony(level, at);
        if (colony == null || colony.alarm().numbers() == null) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        if (to == 0) {
            colony.alarm().calmForTests();
        } else {
            colony.alarm().raise(level, to, at, source.getEntity());
        }
        source.sendSuccess(() -> Component.literal("Alarm now" + colony.alarm().describe(level).replace(" | ", " ")), false);
        return 1;
    }

    private static int age(CommandSourceStack source, int age, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-radius, -radius, -radius), at.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            boolean finishedSoil = state.getBlock() instanceof TermiteMoundBlock && TermiteMoundBlock.isFinished(state);
            if ((finishedSoil || state.getBlock() instanceof MoundPieceBlock) && state.getValue(MoundSoil.AGE) != age) {
                level.setBlock(pos, state.setValue(MoundSoil.AGE, age), Block.UPDATE_CLIENTS);
                changed++;
            }
        }
        int count = changed;
        String[] names = {"fresh", "setting", "dry", "old"};
        source.sendSuccess(() -> Component.literal(count + " termite mound blocks set to " + names[age] + "."), false);
        return 1;
    }

    private static int mushrooms(CommandSourceStack source, int count, boolean giants) {
        ServerLevel level = source.getLevel();
        Colony colony = nearestTermiteColony(level, BlockPos.containing(source.getPosition()));
        if (colony == null || !colony.garden().exists()) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        int wanted = count > 0 ? count : colony.mushrooms().wanted(level);
        if (wanted == 0) {
            source.sendSuccess(() -> Component.literal("The garden is not healthy yet: it has " + colony.garden().countCombLayers(level)
                    + " layers of comb, mushrooms need " + colony.profile().garden().get().mushroomCombLayers() + ". None came up."), false);
            return 1;
        }
        int sprouted = colony.mushrooms().sprout(level, wanted, giants);
        source.sendSuccess(() -> Component.literal(sprouted + " of " + wanted + " Termitomyces came up round the mound"
                + (giants ? " (giants where they fit)" : "") + ". They grow on random ticks; bone meal or /antcolonies mushrooms grow speeds them up."), false);
        return 1;
    }

    private static int growMushrooms(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        java.util.List<BlockPos> small = new java.util.ArrayList<>();
        java.util.List<BlockPos> giants = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TermitomycesBlock) {
                small.add(pos.immutable());
            } else if (state.getBlock() instanceof GiantTermitomycesBlock && state.getValue(GiantTermitomycesBlock.PART) == GiantTermitomycesBlock.FOOT) {
                giants.add(pos.immutable());
            }
        }
        for (BlockPos pos : small) {
            TermitomycesBlock.growOn(level.getBlockState(pos), level, pos);
        }
        for (BlockPos pos : giants) {
            ModBlocks.GIANT_TERMITOMYCES.get().growOn(level, pos);
        }
        int count = small.size() + giants.size();
        source.sendSuccess(() -> Component.literal(count + " Termitomyces moved on one stage."), false);
        return 1;
    }

    /** The termite colony (one that builds a mound) nearest to {@code at} within 64 blocks, or null. */
    private static Colony nearestTermiteColony(ServerLevel level, BlockPos at) {
        return ColonyManager.get(level).getColonies().stream()
                .filter(c -> c.mound().numbers() != null && c.getCenter().distSqr(at) <= 64 * 64)
                .min(Comparator.comparingDouble(c -> c.getCenter().distSqr(at))).orElse(null);
    }

    private static int mound(CommandSourceStack source, int height, boolean instant) {
        ServerLevel level = source.getLevel();
        Colony colony = nearestTermiteColony(level, BlockPos.containing(source.getPosition()));
        if (colony == null) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        int max = colony.mound().numbers().maxHeight();
        int h = Math.min(height, max);
        if (instant) {
            colony.mound().buildInstantly(level, h);
        } else {
            colony.mound().setStage(level, h);
        }
        ColonyManager.get(level).markChanged();
        String done = instant ? "built at once" : "set: the termites build up to it, one stage after the other";
        source.sendSuccess(() -> Component.literal("Mound stage " + h + " of " + max + " (" + colony.mound().wallsAt(h) + " blocks) " + done
                + (colony.isFounding() ? ". The colony has no workers yet, so nobody builds." : ".")), false);
        return 1;
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
        String caste = com.antcolonies.entity.WorkerAntEntity.CASTE;
        int room = colony.profile().colony().maxWorkers() - colony.getCount(caste);
        int added = Math.max(0, Math.min(workers, room));
        // Every age from just grown up to old (not about to die), spread evenly.
        long now = level.getGameTime();
        long oldest = Math.max(1, Math.round(colony.profile().worker().lifespanTicks() * 0.8));
        for (int i = 0; i < added; i++) {
            colony.population().addNewMembers(caste, now - oldest * i / Math.max(1, added), 1);
        }
        StringBuilder soldiers = new StringBuilder();
        var numbers = colony.soldiers().numbers();
        if (numbers != null) {
            int total = colony.getCount(caste);
            for (TermiteSoldierEntity.Rank rank : TermiteSoldierEntity.Rank.values()) {
                int want = rank == TermiteSoldierEntity.Rank.MINOR ? numbers.wantedMinors(total) : numbers.wantedMajors(total);
                int more = Math.max(0, want - colony.getCount(rank.caste()));
                colony.population().addNewMembers(rank.caste(), now, more);
                soldiers.append(", ").append(more).append(" ").append(rank == TermiteSoldierEntity.Rank.MINOR ? "minor" : "major").append(" soldiers");
            }
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
        String caste = com.antcolonies.entity.WorkerAntEntity.CASTE;
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
                    + (colony.isQueenless() ? " | QUEENLESS: no new eggs, dwindling" : " | queen " + colony.getQueenId().toString().substring(0, 8))
                    + (colony.profile().nest().levelUnlocks().isEmpty() ? ""
                            : " | nest levels open " + Math.min(colony.nest().levelsUnlocked(level), colony.profile().nest().maxLevels()) + " of " + colony.profile().nest().maxLevels())
                    + " | members " + colony.getMemberCount() + colony.population().describe(level) + colony.away().describe(level) + colony.field().describe()
                    + " | " + colony.describeJobs(level)
                    + (colony.profile().food().makesBread()
                            ? " | food " + colony.food().getFood() + " (rich " + colony.food().getRichSeeds() + ", eaten " + colony.food().getSeedsEaten() + ")"
                                    + " | bread " + colony.bakery().getBread() + " (rich " + colony.bakery().getRichBread() + ", milling " + colony.bakery().getMilling() + ")"
                            : colony.garden().exists() ? garden(colony, level)
                            : " | food " + colony.food().getFood() + " pieces in the store")
                    + (colony.profile().laying().isPresent()
                            ? " | queen holds " + colony.queenCare().getQueenBread() + "/" + colony.profile().queen().meal() + " nodules" + nextEgg(colony, level)
                            : " | queen fed " + colony.queenCare().getQueenBread() + "/" + colony.profile().queen().meal() + ", rich " + colony.queenCare().getQueenRich() + "/"
                                    + colony.profile().queen().richMeal() + incubation(colony, level))
                    + " | queen unfed " + unfedMinutes(colony, level) + " of " + colony.profile().queen().starveTicks() / 1200 + " min"
                    + " | eggs " + colony.brood().countEggs(level) + " | larvae " + colony.brood().countLarvae(level)
                    + colony.soldiers().describe(level) + colony.alarm().describe(level) + colony.foodMap().describe() + colony.spots().describe() + colony.recruits().describe(level) + colony.trunkTrails()
.describe(level) + colony.trails().describeMemory(level) + colony.danger().describe(level)
                    + " | dead ants " + colony.refuse().getCorpses(level).size() + " (refuse chamber " + colony.refuse().getRefuseCorpses(level).size() + ")"
                    + mound(colony, level) + foraging(colony, level);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return colonies.size();
    }

    /**
     * " | mound stage 3 (earned 5) of 32, 30/36 blocks finished, earned 41.2 blocks" for colonies that build a mound,
     * otherwise nothing: the stage the termites build now, and the one the colony has earned (they get there one stage
     * at a time).
     */
    private static String mound(Colony colony, ServerLevel level) {
        if (colony.mound().numbers() == null) {
            return "";
        }
        int h = colony.mound().getStage(level);
        return String.format(" | mound stage %d (earned %d) of %d, %d/%d blocks finished, earned %.1f blocks", h, colony.mound().getEarnedStage(level),
                colony.mound().numbers().maxHeight(), colony.mound().countFinished(level), colony.mound().wallsAt(h), colony.mound().getAllowance(level));
    }

    /** " | gardens 2 dug (planned 6), comb 14 layers on 5 blocks, nodules 3, mushrooms ..." for colonies with a fungus garden. */
    private static String garden(Colony colony, ServerLevel level) {
        java.util.Set<net.minecraft.core.BlockPos> cells = new java.util.HashSet<>(colony.nest().getGardenCells(level));
        long dug = colony.nest().getGardens(level).stream().filter(garden -> cells.containsAll(garden.room())).count();
        return " | gardens " + dug + " dug (planned " + colony.nest().getGardens(level).size() + "), comb "
                + colony.garden().countCombLayers(level) + " layers on " + colony.garden().getCombs(level).size() + " blocks, nodules "
                + colony.garden().available(level) + mushrooms(colony, level);
    }

    /** ", mushrooms after rain: 2 (last rain 2 came up)" or ", no mushrooms yet (comb 5 of 8 layers)". */
    private static String mushrooms(Colony colony, ServerLevel level) {
        int wanted = colony.mushrooms().wanted(level);
        int last = colony.mushrooms().getLastFlush();
        String lastRain = last < 0 ? "" : " (last rain " + last + " came up)";
        if (wanted == 0) {
            return ", no mushrooms yet (comb " + colony.garden().countCombLayers(level) + " of "
                    + colony.profile().garden().get().mushroomCombLayers() + " layers)" + lastRain;
        }
        return ", mushrooms after rain: " + wanted + lastRain;
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
                    + colony.patrol().describe(level) + colony.door().describe(level)
                    + (colony.hill().nightSoil().waiting(level) > 0 ? " | night soil in piles: " + colony.hill().nightSoil().waiting(level)
                            + " of at most " + colony.hill().nightSoil().limit(level) + " pellets" : "");
        }
        if (!colony.keepsASchedule() && holes == 0) {
            return "";
        }
        String when = String.join(", ", colony.profile().food().forageAt());
        String line = " | " + (colony.isForagingTime(level) ? "foraging now" : "staying in") + " (out at: " + when + ")";
        if (holes > 0) {
            line += ", holes open " + colony.foragingHoles().countOpen(level) + " of " + colony.foragingHoles().tunnels(level).size(); line += colony.foragingHoles().describeNewHoles(level) + colony.foragingHoles().describeRunways(level) + colony.nest().galleries().describe(level) + colony.hiddenTrips().describe();
            if (colony.profile().food().regrowth().isPresent()) {
                line += ", " + colony.food().barePatches().describe();
            }
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
