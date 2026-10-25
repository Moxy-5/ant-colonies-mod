package com.antcolonies.savanna.command;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.colony.MatabeleColonies;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The Matabele ant's commands (M8 step 1c): {@code /antcolonies matabele <workers>} makes a whole colony where you
 * stand, a queen with that many workers (the user's pick, 2026-10-24: a real queen never founds alone, M9 has the colony
 * split). As many as the colony's real-mob cap come out as real ants round her, majors and minors as the real size
 * spread draws them; the rest are numbers in the brain (they come out in the nest's finished rooms). Step 1d: the colony
 * settles in the nearest free dead termite mound within {@value MatabeleNest#LOOK} blocks (the only home for now, the
 * user's call, 2026-10-24); the ants appear where you stand and walk there. With no such mound the command says so.
 *
 * <p>Step 1e: {@code /antcolonies prey <layers>} lays that many bunches of fresh paralysed termites (about 10 a layer)
 * on the prey pile of the nearest Matabele colony within 64 blocks, standing in for a raid until the raids exist (M8 step 4).
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public class MatabeleCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("antcolonies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("matabele")
                        .then(Commands.argument("workers", IntegerArgumentType.integer(0, 2500))
                                .executes(context -> colony(context.getSource(), IntegerArgumentType.getInteger(context, "workers")))))
                .then(Commands.literal("prey")
                        .then(Commands.argument("layers", IntegerArgumentType.integer(1, 64))
                                .executes(context -> prey(context.getSource(), IntegerArgumentType.getInteger(context, "layers"))))));
    }

    /** Fresh termites onto the nearest Matabele colony's prey pile, as a raid would bring them. */
    private static int prey(CommandSourceStack source, int layers) {
        ServerLevel level = source.getLevel();
        Colony colony = ColonyManager.get(level).findNearest(BlockPos.containing(source.getPosition()), 64, MatabeleEntity.SPECIES);
        PreyPile pile = colony == null ? null : colony.part(SavannaParts.PREY_PILE).orElse(null);
        if (pile == null) {
            source.sendFailure(Component.literal("No Matabele colony within 64 blocks (/antcolonies matabele <workers> makes one)."));
            return 0;
        }
        int laid = pile.stock(level, layers);
        if (laid == 0) {
            source.sendFailure(Component.literal("No room for termites in the queen's room (is the nest settled yet?)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Laid " + laid + " layers of paralysed termites (about " + laid * 10
                + ") in the queen's room; the pile holds " + pile.edible() + " good layers"
                + (laid < layers ? " (no room for the other " + (layers - laid) + ")" : "") + "."), false);
        return laid;
    }

    private static int colony(CommandSourceStack source, int workers) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        MatabeleNest.Site site = MatabeleNest.pick(level, at);
        if (site == null) {
            source.sendFailure(Component.literal("No free dead termite mound with a way in within " + MatabeleNest.LOOK
                    + " blocks of you (/antcolonies newmound dead 30 makes one)."));
            return 0;
        }
        Colony colony = makeColony(level, at, site, workers);
        if (colony == null) {
            source.sendFailure(Component.literal("Could not make the Matabele queen here."));
            return 0;
        }
        int real = colony.getCount(Caste.WORKER) - colony.population().inside(Caste.WORKER);
        source.sendSuccess(() -> Component.literal("A Matabele colony settling in a dead termite mound at " + site.entrance().toShortString()
                + ": the queen and " + colony.getCount(Caste.WORKER) + " workers (" + real
                + " real round you, walking there; the rest inside the brain's numbers, out once the rooms are open)."), false);
        return 1;
    }

    /** {@link #makeColony(ServerLevel, BlockPos, MatabeleNest.Site, int)} in the nearest free dead mound near {@code at}; null if there is none. */
    @Nullable
    public static Colony makeColony(ServerLevel level, BlockPos at, int workers) {
        MatabeleNest.Site site = MatabeleNest.pick(level, at);
        return site == null ? null : makeColony(level, at, site, workers);
    }

    /**
     * A Matabele queen at {@code at} with her colony, settled in {@code site}, and {@code workers} workers (real ones
     * round her up to the real-mob cap, the rest numbers; {@link MatabeleColonies#make}). Null if the queen could not be made.
     */
    public static Colony makeColony(ServerLevel level, BlockPos at, MatabeleNest.Site site, int workers) {
        // Never inside the ground (a dead mound's hump may rise round the spot asked for): up onto its top.
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        }
        return MatabeleColonies.make(level, Vec3.atBottomCenterOf(at), site, workers, true);
    }
}
