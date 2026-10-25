package com.antcolonies.savanna.command;

import org.jetbrains.annotations.Nullable;

import com.antcolonies.colony.Caste;
import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.AbstractAntEntity;
import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.savanna.colony.MatabeleColonies;
import com.antcolonies.savanna.colony.MatabeleNest;
import com.antcolonies.savanna.colony.PreyPile;
import com.antcolonies.savanna.colony.SavannaParts;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
 *
 * <p>Step 2b: {@code /antcolonies skirmish <ants>} drops that many Matabele workers (majors and minors by the real size
 * spread) a few blocks from the nearest termite feeding spot within {@value #SKIRMISH_LOOK} blocks (the termites' mud
 * over the wood they eat, else the nearest termite out), and sends them in to fight there for
 * {@value #SKIRMISH_MINUTES} minutes: majors bite the mud open, everyone stings the termites, the soldiers fight back.
 * Standing in for a raid column until step 4. They join the nearest Matabele colony within 32 blocks, if there is one.
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
                .then(Commands.literal("skirmish")
                        .then(Commands.argument("ants", IntegerArgumentType.integer(1, 60))
                                .executes(context -> skirmish(context.getSource(), IntegerArgumentType.getInteger(context, "ants")))))
                .then(Commands.literal("prey")
                        .then(Commands.argument("layers", IntegerArgumentType.integer(1, 64))
                                .executes(context -> prey(context.getSource(), IntegerArgumentType.getInteger(context, "layers")))))
                .then(Commands.literal("loseleg")
                        .executes(context -> loseLeg(context.getSource(), 1))
                        .then(Commands.argument("legs", IntegerArgumentType.integer(1, 6))
                                .executes(context -> loseLeg(context.getSource(), IntegerArgumentType.getInteger(context, "legs")))))
                .then(Commands.literal("clamp")
                        .executes(context -> clamp(context.getSource(), false, -1))
                        .then(Commands.literal("dead")
                                .executes(context -> clamp(context.getSource(), true, -1))
                                .then(Commands.argument("leg", IntegerArgumentType.integer(1, 6))
                                        .executes(context -> clamp(context.getSource(), true,
                                                IntegerArgumentType.getInteger(context, "leg") - 1))))));
    }

    /**
     * M8 step 2c, for testing: a termite major soldier (of no colony) clamps on to a leg of the nearest Matabele within
     * {@value #LOSE_LEG_LOOK} blocks, as if its bite had locked on; with {@code dead}, it dies at once and only its head
     * stays locked on the leg; with a leg (1 to 6, in {@code LostLegs.LEGS}' order) on that leg, for the guard films.
     */
    private static int clamp(CommandSourceStack source, boolean dead, int whichLeg) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        MatabeleEntity ant = level.getEntitiesOfClass(MatabeleEntity.class, new AABB(at, at).inflate(LOSE_LEG_LOOK),
                        a -> a.isAlive() && a.canLoseLegs() && !a.clamp().isOn() && com.antcolonies.entity.body.LostLegs.count(a.lostLegs()) < 6)
                .stream().min(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(at))).orElse(null);
        if (ant == null) {
            source.sendFailure(Component.literal("No Matabele major or minor without a clamp within " + (int) LOSE_LEG_LOOK + " blocks."));
            return 0;
        }
        AbstractAntEntity soldier = com.antcolonies.savanna.registry.SavannaEntities.TERMITE_MAJOR_SOLDIER.get().create(level);
        if (soldier == null) {
            return 0;
        }
        soldier.moveTo(ant.getX(), ant.getY(), ant.getZ(), 0.0F, 0.0F);
        level.addFreshEntity(soldier);
        if (whichLeg >= 0 && !com.antcolonies.entity.body.LostLegs.isGone(ant.lostLegs(), whichLeg)) {
            ant.clamp().clampOn(soldier, whichLeg);
        } else if (!ant.clamp().clampOn(soldier)) {
            soldier.discard();
            source.sendFailure(Component.literal("That Matabele has no legs left to clamp."));
            return 0;
        }
        if (dead) {
            soldier.kill();
            ant.clamp().becomeHead();
        }
        String leg = com.antcolonies.entity.body.LostLegs.LEGS.get(ant.clamp().leg());
        source.sendSuccess(() -> Component.literal((dead ? "A dead termite soldier's head is locked on the " : "A termite major soldier clamped on to the ")
                + leg + " leg of the Matabele at " + ant.blockPosition().toShortString()
                + (dead ? " (a little slower; a nestmate in the nest bites it off)" : " (it crawls home and fights no more; a nestmate's sting leaves the head)")), true);
        return 1;
    }

    /** How far (blocks) the leg command looks for an ant. */
    private static final double LOSE_LEG_LOOK = 16.0;

    /**
     * M8 step 2d, for testing: the nearest Matabele within {@value #LOSE_LEG_LOOK} blocks loses this many more legs, as if
     * termite soldiers bit them off (one at a time, by the dice; the pieces drop beside it).
     */
    private static int loseLeg(CommandSourceStack source, int legs) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        MatabeleEntity ant = level.getEntitiesOfClass(MatabeleEntity.class, new AABB(at, at).inflate(LOSE_LEG_LOOK),
                        a -> a.isAlive() && a.canLoseLegs() && com.antcolonies.entity.body.LostLegs.count(a.lostLegs()) < 6)
                .stream().min(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(at))).orElse(null);
        if (ant == null) {
            source.sendFailure(Component.literal("No Matabele major or minor with legs left within " + (int) LOSE_LEG_LOOK + " blocks."));
            return 0;
        }
        for (int i = 0; i < legs; i++) {
            ant.loseLeg();
        }
        java.util.List<String> gone = com.antcolonies.entity.body.LostLegs.gone(ant.lostLegs());
        source.sendSuccess(() -> Component.literal("The Matabele at " + ant.blockPosition().toShortString() + " has lost "
                + gone.size() + " leg(s): " + String.join(", ", gone) + (ant.isCrippled() ? " (it fights no more and limps home)" : "")), true);
        return 1;
    }

    /** How far (blocks) the skirmish looks for a termite feeding spot, and how long (minutes) the ants fight there. */
    private static final int SKIRMISH_LOOK = 32;
    private static final int SKIRMISH_MINUTES = 5;

    /** Matabele ants dropped near the nearest termite feeding spot, sent to fight there. */
    private static int skirmish(CommandSourceStack source, int ants) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        BlockPos spot = feedingSpot(level, at);
        if (spot == null) {
            source.sendFailure(Component.literal("No termites feeding within " + SKIRMISH_LOOK
                    + " blocks (no termite mud over wood, no termite out)."));
            return 0;
        }
        // A few blocks off, on the surface, so they walk in (the way a raid column arrives).
        RandomSource random = level.getRandom();
        BlockPos drop = spot;
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            BlockPos column = spot.offset((int) Math.round(Math.cos(angle) * 4.0), 0, (int) Math.round(Math.sin(angle) * 4.0));
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(top.getY() - spot.getY()) <= 3 && !level.getBlockState(top.below()).getFluidState().isSource()) {
                drop = top;
                break;
            }
        }
        long until = level.getGameTime() + SKIRMISH_MINUTES * 1200L;
        int made = 0;
        for (int i = 0; i < ants; i++) {
            MatabeleEntity ant = MatabeleEntity.pickBody(random).create(level);
            if (ant == null) {
                continue;
            }
            ant.moveTo(drop.getX() + 0.2 + random.nextDouble() * 0.6, drop.getY(), drop.getZ() + 0.2 + random.nextDouble() * 0.6,
                    random.nextFloat() * 360.0F, 0.0F);
            ant.orderToFight(spot, until);
            level.addFreshEntity(ant);
            made++;
        }
        int count = made;
        BlockPos from = drop;
        source.sendSuccess(() -> Component.literal(count + " Matabele ants at " + from.toShortString()
                + ", sent to fight round the termites at " + spot.toShortString() + " for " + SKIRMISH_MINUTES + " minutes."), false);
        return count;
    }

    /**
     * The nearest termite feeding spot to {@code at}: the nearest termite mud on the surface (sheeting, a cast, mud over
     * wood: where termites eat by day under cover), else the nearest Macrotermes insect out of its mound; null for none.
     */
    @Nullable
    private static BlockPos feedingSpot(ServerLevel level, BlockPos at) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-SKIRMISH_LOOK, -8, -SKIRMISH_LOOK), at.offset(SKIRMISH_LOOK, 8, SKIRMISH_LOOK))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(SavannaBlocks.MUD_CRUST.get()) && !state.is(SavannaBlocks.MUD_CAST.get())) {
                continue;
            }
            double dist = pos.distSqr(at);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        if (best != null) {
            return best;
        }
        return level.getEntitiesOfClass(AbstractAntEntity.class, new AABB(at).inflate(SKIRMISH_LOOK),
                        ant -> SavannaSpecies.MACROTERMES.equals(ant.species()) && ant.isAlive() && !ant.isParalysed())
                .stream().min(java.util.Comparator.comparingDouble(ant -> ant.distanceToSqr(Vec3.atCenterOf(at))))
                .map(ant -> ant.blockPosition()).orElse(null);
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
