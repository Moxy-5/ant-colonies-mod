package com.antcolonies.savanna.command;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.MatabeleQueenEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The Matabele ant's commands (M8 step 1c): {@code /antcolonies matabele <workers>} makes a whole colony where you
 * stand, a queen with that many workers (the user's pick, 2026-10-24: a real queen never founds alone, M9 has the colony
 * split). As many as the colony's real-mob cap come out as real ants round her, majors and minors as the real size
 * spread draws them; the rest are numbers in the brain (they come out in the nest's finished rooms). Step 1d: the colony
 * settles in the best site within {@value MatabeleNest#LOOK} blocks (a dead mound, a fallen log, the foot of a tree, else the ground), or
 * the kind named by a last word, {@code mound}, {@code log}, {@code standing} or {@code ground}; the ants appear where you stand and walk
 * there.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public class MatabeleCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antcolonies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("matabele")
                        .then(Commands.argument("workers", IntegerArgumentType.integer(0, 2500))
                                .executes(context -> colony(context.getSource(), IntegerArgumentType.getInteger(context, "workers"), null))
                                .then(Commands.argument("site", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (MatabeleNest.Kind kind : MatabeleNest.Kind.values()) {
                                                builder.suggest(kind.name().toLowerCase(java.util.Locale.ROOT));
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> colony(context.getSource(), IntegerArgumentType.getInteger(context, "workers"),
                                                StringArgumentType.getString(context, "site")))))));
    }

    private static int colony(CommandSourceStack source, int workers, String siteWord) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        MatabeleNest.Kind only = null;
        if (siteWord != null) {
            only = MatabeleNest.Kind.byWord(siteWord);
            if (only == null) {
                source.sendFailure(Component.literal("Unknown site '" + siteWord + "': mound, log, standing or ground."));
                return 0;
            }
        }
        MatabeleNest.Site site = MatabeleNest.pick(level, at, only);
        if (site == null) {
            source.sendFailure(Component.literal("No free site " + only.words + " within " + MatabeleNest.LOOK + " blocks of you."));
            return 0;
        }
        Colony colony = makeColony(level, at, site, workers);
        if (colony == null) {
            source.sendFailure(Component.literal("Could not make the Matabele queen here."));
            return 0;
        }
        int real = colony.getCount(Caste.WORKER) - colony.population().inside(Caste.WORKER);
        source.sendSuccess(() -> Component.literal("A Matabele colony settling " + site.kind().words + " at " + site.entrance().toShortString()
                + ": the queen and " + colony.getCount(Caste.WORKER) + " workers (" + real
                + " real round you, walking there; the rest inside the brain's numbers, out once the rooms are open)."), false);
        return 1;
    }

    /** {@link #makeColony(ServerLevel, BlockPos, MatabeleNest.Site, int)} in the best site near {@code at}. */
    public static Colony makeColony(ServerLevel level, BlockPos at, int workers) {
        return makeColony(level, at, MatabeleNest.pick(level, at, null), workers);
    }

    /**
     * A Matabele queen at {@code at} with her colony, settled in {@code site}, and {@code workers} workers (real ones
     * round her up to the real-mob cap, the rest numbers), every age from just grown up to old, all just fed. Null if
     * the queen could not be made.
     */
    public static Colony makeColony(ServerLevel level, BlockPos at, MatabeleNest.Site site, int workers) {
        MatabeleQueenEntity queen = SavannaEntities.MATABELE_QUEEN.get().create(level);
        if (queen == null) {
            return null;
        }
        ColonyManager manager = ColonyManager.get(level);
        Colony colony = MatabeleNest.settle(level, site, queen.getUUID());
        queen.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, colony.random().nextFloat() * 360.0F, 0.0F);
        queen.becomeQueenOf(colony);
        level.addFreshEntity(queen);
        long now = level.getGameTime();
        long oldest = Math.max(1, Math.round(colony.profile().worker().lifespanTicks() * 0.8));
        int real = Math.min(workers, colony.profile().colony().realCap());
        for (int i = 0; i < workers; i++) {
            long grownUpAt = now - oldest * i / Math.max(1, workers);
            if (i >= real) {
                colony.population().addNewMembers(Caste.WORKER, grownUpAt, 1);
                continue;
            }
            MatabeleEntity ant = MatabeleEntity.pickBody(colony.random()).create(level);
            if (ant == null) {
                continue;
            }
            // Round the queen, 1.5 to 4 blocks out, on the ground.
            float angle = colony.random().nextFloat() * Mth.TWO_PI;
            double distance = 1.5 + colony.random().nextDouble() * 2.5;
            int x = Mth.floor(at.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(at.getZ() + 0.5 + Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ant.moveTo(x + 0.5, y, z + 0.5, colony.random().nextFloat() * 360.0F, 0.0F);
            ant.joinColony(colony);
            ant.setGrownUpAt(grownUpAt);
            ant.setLastMeal(now);
            level.addFreshEntity(ant);
        }
        manager.markChanged();
        return colony;
    }
}
