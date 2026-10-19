package com.antcolonies;

import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.TermiteKingEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;
import com.antcolonies.registry.ModEntities;
import com.antcolonies.registry.ModItems;

import com.antcolonies.block.DryGrassBlock;
import com.antcolonies.block.GramaGrassBlock;
import com.antcolonies.colony.Colony;
import com.antcolonies.debug.Perf;
import com.antcolonies.worldgen.BranchShedding;
import com.antcolonies.colony.ColonyManager;
import com.antcolonies.entity.goal.GroundShakes;
import com.antcolonies.entity.goal.HuntTermitesGoal;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AntColoniesMod.MODID)
public class ModEvents {
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.WORKER_ANT.get(), WorkerAntEntity.createAttributes().build());
        event.put(ModEntities.QUEEN_ANT.get(), QueenAntEntity.createAttributes().build());
        event.put(ModEntities.TERMITE_KING.get(), TermiteKingEntity.createAttributes().build());
        event.put(ModEntities.TERMITE_QUEEN.get(), TermiteQueenEntity.createAttributes().build());
        event.put(ModEntities.TERMITE_WORKER.get(), TermiteWorkerEntity.createAttributes().build());
        event.put(ModEntities.TERMITE_MINOR_SOLDIER.get(), TermiteSoldierEntity.createMinorAttributes().build());
        event.put(ModEntities.TERMITE_MAJOR_SOLDIER.get(), TermiteSoldierEntity.createMajorAttributes().build());
    }

    /** Chickens hunt termite workers out on the surface (step 6c; see {@link HuntTermitesGoal}). */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Chicken chicken) {
            chicken.goalSelector.addGoal(3, new HuntTermitesGoal(chicken));
        }
    }

    /**
     * One of a colony's insects goes out of range (its chunk stops being tracked, before Minecraft unloads it; one that
     * is killed or put away has a removal reason already): its colony writes down what it is (the brain, part 2, see
     * {@link com.antcolonies.colony.AwayLife#freeze}).
     */
    @SubscribeEvent
    public static void onLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.world.entity.Entity entity = event.getEntity();
        var reason = entity.getRemovalReason();
        if (reason != null && reason != net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            return;
        }
        java.util.UUID colonyId = entity instanceof com.antcolonies.entity.AbstractAntEntity mob ? mob.getColonyId()
                : entity instanceof com.antcolonies.entity.AntEggEntity egg ? egg.getColonyId()
                : entity instanceof com.antcolonies.entity.AntLarvaEntity larva ? larva.getColonyId() : null;
        Colony colony = colonyId == null ? null : ColonyManager.get(level).getColony(colonyId);
        if (colony != null) {
            colony.away().freeze(level, entity);
        }
    }

    /** A player jumping on a termite mound: footsteps, the alarm's first level (see {@code colony.Alarm}). */
    // Every colony's own clock, once a second, whether or not its queen is loaded (or alive).
    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (level.getGameTime() % 20 == 0) {
                boolean timed = Perf.begin(Perf.Part.BRAIN);
                try {
                    ColonyManager.get(level).tickColonies(level);
                } finally {
                    if (timed) {
                        Perf.end();
                    }
                }
            }
            BranchShedding.tick(level);
        }
    }

    /** The stopwatch closes each server tick (see {@link Perf}). */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        Perf.endOfTick(event.getServer());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level) {
            GroundShakes.hard(player); // termites nearby feel it (NoticeGoal)
            for (Colony colony : coloniesNear(level, player.blockPosition())) {
                colony.alarm().footsteps(level, player);
            }
        }
    }

    /** A player landing shakes the ground: termites nearby feel it (see {@code NoticeGoal}). */
    @SubscribeEvent
    public static void onLand(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getDistance() > 0.5F) {
            GroundShakes.hard(player);
        }
    }

    /** A player breaking into a termite mound or nest: a break-in, the alarm's third level (see {@code colony.Alarm}). */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (Colony colony : coloniesNear(level, event.getPos())) {
                colony.alarm().blockBroken(level, event.getPos(), event.getPlayer());
            }
        }
    }

    /** The colonies with an alarm whose ground (the food radius and a bit) this spot is in. */
    private static java.util.List<Colony> coloniesNear(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return ColonyManager.get(level).getColonies().stream()
                .filter(colony -> colony.alarm().numbers() != null)
                .filter(colony -> {
                    double reach = colony.foragingRadius() + 8.0;
                    return colony.getCenter().distSqr(pos) <= reach * reach;
                })
                .toList();
    }

    /** A grama seeds item that places a grown plant at this stage, named so it can be told apart (for testing). */
    private static ItemStack grama(ItemStack stack, GramaGrassBlock.Stage stage, String name) {
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GramaGrassBlock.STAGE, stage));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(name));
        return stack;
    }

    /** A Dry Grass item that places the grass green (it dries out over a few days), named so it can be told apart. */
    private static ItemStack greenGrass(ItemStack stack, String name) {
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DryGrassBlock.GROWTH, DryGrassBlock.Growth.GREEN));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(name));
        return stack;
    }

    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.BURROWED_DIRT);
            event.accept(ModItems.DEEP_SOIL);
            event.accept(ModItems.SEED_PILE);
            event.accept(ModItems.BREAD_PILE);
            event.accept(ModItems.FORAGE_PILE);
            event.accept(ModItems.FUNGUS_COMB);
            event.accept(ModItems.TERMITE_MOUND);
            event.accept(ModItems.TERMITE_HOLE);
            event.accept(ModItems.TERMITE_SPIRE);
            event.accept(ModItems.TERMITE_MOUTH);
            event.accept(ModItems.TERMITE_RAMP);
            event.accept(ModItems.TERMITE_TUNNEL);
            // Dry Grass placed by hand is dry; the green ones are for watching it dry out.
            event.accept(ModItems.DRY_GRASS);
            event.accept(greenGrass(ModItems.DRY_GRASS.toStack(), "block.antcolonies.dry_grass.green"));
            event.accept(ModItems.TALL_DRY_GRASS);
            event.accept(greenGrass(ModItems.TALL_DRY_GRASS.toStack(), "block.antcolonies.tall_dry_grass.green"));
            // Grama: the seeds (planted, they come up young), and grown plants for testing (leaves wait for rain; ripe for the ants).
            event.accept(ModItems.BLUE_GRAMA_SEEDS);
            event.accept(grama(ModItems.BLUE_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.LEAVES, "block.antcolonies.blue_grama.leaves"));
            event.accept(grama(ModItems.BLUE_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.RIPE, "block.antcolonies.blue_grama.ripe"));
            event.accept(ModItems.BLACK_GRAMA_SEEDS);
            event.accept(grama(ModItems.BLACK_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.LEAVES, "block.antcolonies.black_grama.leaves"));
            event.accept(grama(ModItems.BLACK_GRAMA_SEEDS.toStack(), GramaGrassBlock.Stage.RIPE, "block.antcolonies.black_grama.ripe"));
            event.accept(ModItems.FALLEN_BRANCH);
            event.accept(ModItems.DEAD_WOOD);
            event.accept(ModItems.MUD_CRUST);
            // The giant savanna trees' blocks (M7.5).
            ModItems.TREE_ITEMS.forEach(event::accept);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.FORAGE);
            event.accept(ModItems.FUNGUS_NODULE);
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.TERMITOMYCES);
            event.accept(ModItems.COOKED_TERMITOMYCES);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.WORKER_ANT_SPAWN_EGG);
            event.accept(ModItems.QUEEN_ANT_SPAWN_EGG);
            event.accept(ModItems.TERMITE_QUEEN_SPAWN_EGG);
            event.accept(ModItems.TERMITE_KING_SPAWN_EGG);
            event.accept(ModItems.TERMITE_WORKER_SPAWN_EGG);
            event.accept(ModItems.TERMITE_MINOR_SOLDIER_SPAWN_EGG);
            event.accept(ModItems.TERMITE_MAJOR_SOLDIER_SPAWN_EGG);
        }
    }
}
