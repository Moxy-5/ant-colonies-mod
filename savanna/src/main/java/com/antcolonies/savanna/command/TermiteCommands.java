package com.antcolonies.savanna.command;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.GiantTermitomycesBlock;
import com.antcolonies.savanna.block.MoundPieceBlock;
import com.antcolonies.savanna.block.MoundSoil;
import com.antcolonies.savanna.block.TermiteMoundBlock;
import com.antcolonies.savanna.block.TermitomycesBlock;
import com.antcolonies.savanna.colony.Alarm;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.colony.FungusGarden;
import com.antcolonies.savanna.colony.Mound;
import com.antcolonies.savanna.colony.Mushrooms;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;

import com.antcolonies.savanna.species.SavannaNumbers;
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

/**
 * The termites' own debug commands (Apex step 9b: moved out of the shared {@link ColonyCommands}, with the termites
 * in the split), under the same {@code /antcolonies} name: the game joins both into one command.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public class TermiteCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antcolonies")
                .requires(source -> source.hasPermission(2))
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
                // M8 step 1a: the dead termite mounds (a colony's end: /antcolonies kill), how long dead, comb left.
                .then(Commands.literal("deadmounds").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            com.antcolonies.savanna.colony.DeadMounds.get(context.getSource().getLevel()).describe(context.getSource().getLevel())), false);
                    return 1;
                }))
                .then(Commands.literal("newmounds").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            com.antcolonies.savanna.colony.NewLandMounds.get(context.getSource().getLevel()).describeBook()), false);
                    return 1;
                }))
                .then(Commands.literal("newmound")
                        .then(Commands.literal("full").executes(context -> newMound(context.getSource(), "full", 0)))
                        .then(Commands.literal("half").executes(context -> newMound(context.getSource(), "half", 0)))
                        .then(Commands.literal("young").executes(context -> newMound(context.getSource(), "young", 0)))
                        .then(Commands.literal("dead").executes(context -> newMound(context.getSource(), "dead", 1))
                                .then(Commands.argument("days", IntegerArgumentType.integer(0, 400))
                                        .executes(context -> newMound(context.getSource(), "dead", IntegerArgumentType.getInteger(context, "days")))
                                        // M8 step 1f: with a Matabele colony in it, as new land gives most dead mounds.
                                        .then(Commands.literal("matabele")
                                                .executes(context -> newMound(context.getSource(), "dead", IntegerArgumentType.getInteger(context, "days"), true))))))
                .then(Commands.literal("age")
                        .then(Commands.argument("age", IntegerArgumentType.integer(0, MoundSoil.OLDEST))
                                .executes(context -> age(context.getSource(), IntegerArgumentType.getInteger(context, "age"), 8))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                        .executes(context -> age(context.getSource(), IntegerArgumentType.getInteger(context, "age"),
                                                IntegerArgumentType.getInteger(context, "radius")))))));
    }

    /** A mound of new land put in at the player's feet now, as world generation would (M8 step 1b). */
    private static int newMound(CommandSourceStack source, String kind, int days) {
        return newMound(source, kind, days, false);
    }

    private static int newMound(CommandSourceStack source, String kind, int days, boolean ants) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        var pick = com.antcolonies.savanna.colony.NewLandMounds.pickFor(kind, days, level.getRandom().nextLong());
        var book = com.antcolonies.savanna.colony.NewLandMounds.get(level);
        Colony colony = ants ? book.putWithAnts(level, at, pick, java.util.UUID.randomUUID(), true) : book.put(level, at, pick);
        if (colony == null) {
            source.sendFailure(Component.literal("No mound here (water, no soil, or another colony or dead mound within "
                    + com.antcolonies.savanna.colony.NewLandMounds.KEEP_APART + " blocks): see the log."));
            return 0;
        }
        Colony matabele = ants ? book.lastAnts() : null;
        source.sendSuccess(() -> Component.literal("Put in: " + com.antcolonies.savanna.colony.NewLandMounds.describe(pick)
                + (matabele != null ? ", with a Matabele colony of " + matabele.getCount(com.antcolonies.colony.Caste.WORKER) + " workers"
                        : ants ? ", but no Matabele colony (no way in yet)" : "") + "."), false);
        return 1;
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
        Alarm alarm = colony == null ? null : colony.part(SavannaParts.ALARM).filter(part -> part.numbers() != null).orElse(null);
        if (alarm == null) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        if (to == 0) {
            alarm.calmForTests();
        } else {
            alarm.raise(level, to, at, source.getEntity());
        }
        source.sendSuccess(() -> Component.literal("Alarm now" + alarm.describe(level).replace(" | ", " ")), false);
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
        FungusGarden garden = colony == null ? null : colony.part(SavannaParts.FUNGUS_GARDEN).orElse(null);
        Mushrooms mushrooms = colony == null ? null : colony.part(SavannaParts.MUSHROOMS).orElse(null);
        if (garden == null || mushrooms == null) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        int wanted = count > 0 ? count : mushrooms.wanted(level);
        if (wanted == 0) {
            source.sendSuccess(() -> Component.literal("The garden is not healthy yet: it has " + garden.countCombLayers(level)
                    + " layers of comb, mushrooms need " + colony.profile().section(SavannaNumbers.GARDEN).get().mushroomCombLayers() + ". None came up."), false);
            return 1;
        }
        int sprouted = mushrooms.sprout(level, wanted, giants);
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
            SavannaBlocks.GIANT_TERMITOMYCES.get().growOn(level, pos);
        }
        int count = small.size() + giants.size();
        source.sendSuccess(() -> Component.literal(count + " Termitomyces moved on one stage."), false);
        return 1;
    }

    /** The termite colony (one that builds a mound) nearest to {@code at} within 64 blocks, or null. */
    private static Colony nearestTermiteColony(ServerLevel level, BlockPos at) {
        return ColonyManager.get(level).getColonies().stream()
                .filter(c -> c.buildsMound() && c.getCenter().distSqr(at) <= 64 * 64)
                .min(Comparator.comparingDouble(c -> c.getCenter().distSqr(at))).orElse(null);
    }

    private static int mound(CommandSourceStack source, int height, boolean instant) {
        ServerLevel level = source.getLevel();
        Colony colony = nearestTermiteColony(level, BlockPos.containing(source.getPosition()));
        if (colony == null) {
            source.sendFailure(Component.literal("No termite colony within 64 blocks."));
            return 0;
        }
        Mound mound = colony.part(SavannaParts.MOUND).orElseThrow(); // (a colony that builds a mound)
        int max = mound.numbers().maxHeight();
        int h = Math.min(height, max);
        if (instant) {
            mound.buildInstantly(level, h);
        } else {
            mound.setStage(level, h);
        }
        ColonyManager.get(level).markChanged();
        String done = instant ? "built at once" : "set: the termites build up to it, one stage after the other";
        source.sendSuccess(() -> Component.literal("Mound stage " + h + " of " + max + " (" + mound.wallsAt(h) + " blocks) " + done
                + (colony.isFounding() ? ". The colony has no workers yet, so nobody builds." : ".")), false);
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
}
