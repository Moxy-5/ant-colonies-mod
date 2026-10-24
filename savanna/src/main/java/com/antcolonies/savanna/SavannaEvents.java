package com.antcolonies.savanna;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.ModEvents;
import com.antcolonies.savanna.block.DryGrassBlock;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.entity.goal.HuntTermitesGoal;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The savanna's game events (the split, step S4, out of the shared {@link ModEvents}): the termites' strength and
 * health, chickens hunting termites, and the giant trees' fire, lightning, falls, days and bone meal.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public class SavannaEvents {
    /** Step 5d: a fire appearing next to a giant tree chars its bark and scorches its leaves. */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.antcolonies.savanna.tree.TreeFire.changed(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SavannaEntities.TERMITE_KING.get(), TermiteKingEntity.createAttributes().build());
        event.put(SavannaEntities.TERMITE_QUEEN.get(), TermiteQueenEntity.createAttributes().build());
        event.put(SavannaEntities.TERMITE_WORKER.get(), TermiteWorkerEntity.createAttributes().build());
        event.put(SavannaEntities.TERMITE_MINOR_SOLDIER.get(), TermiteSoldierEntity.createMinorAttributes().build());
        event.put(SavannaEntities.TERMITE_MAJOR_SOLDIER.get(), TermiteSoldierEntity.createMajorAttributes().build());
        event.put(SavannaEntities.MATABELE_MAJOR.get(), com.antcolonies.savanna.entity.MatabeleEntity.createMajorAttributes().build());
        event.put(SavannaEntities.MATABELE_MINOR.get(), com.antcolonies.savanna.entity.MatabeleEntity.createMinorAttributes().build());
    }

    /** Chickens hunt termite workers out on the surface (step 6c; see {@link HuntTermitesGoal}). */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(com.antcolonies.savanna.tree.TreeFall.TAG)) {
            // A falling tree's display left by a stop in the middle of its fall: the tree is placed fallen instead.
            event.setCanceled(true);
            return;
        }
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt
                && !level.getBlockState(bolt.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD)) {
            // Step 5e: a big savanna tree near the strike draws it to its top (a lightning rod still wins).
            net.minecraft.core.BlockPos to = com.antcolonies.savanna.tree.Forest.get(level).lightning(level, bolt.blockPosition());
            if (to != null) {
                bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(to));
            }
        }
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Chicken chicken) {
            chicken.goalSelector.addGoal(3, new HuntTermitesGoal(chicken));
        }
    }

    /** The giant trees' day (M7.5, {@code Forest}), after the colonies' clock each tick (as before the split). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.antcolonies.savanna.tree.Forest.tick(level);
        }
    }

    /** Bone meal on a giant savanna tree's seedling or young tree grows it a day's step (M7.5 step 4, {@code Forest}). */
    @SubscribeEvent
    public static void onUseOnBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getItemStack().is(net.minecraft.world.item.Items.BONE_MEAL)) {
            return;
        }
        if (com.antcolonies.savanna.tree.Forest.get(level).boneMeal(level, event.getPos())) {
            if (!event.getEntity().getAbilities().instabuild) {
                event.getItemStack().shrink(1);
            }
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** A Dry Grass item that places the grass green (it dries out over a few days), named so it can be told apart. */
    private static ItemStack greenGrass(ItemStack stack, String name) {
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DryGrassBlock.GROWTH, DryGrassBlock.Growth.GREEN));
        stack.set(DataComponents.ITEM_NAME, Component.translatable(name));
        return stack;
    }

    /** The savanna's rows in the creative menu, between the harvester's (their places keep today's order). */
    static void signUpCreativeTab() {
        ModEvents.addToCreativeTab(CreativeModeTabs.NATURAL_BLOCKS, 20, event -> {
            event.accept(SavannaItems.FORAGE_PILE);
            event.accept(SavannaItems.FUNGUS_COMB);
            event.accept(SavannaItems.TERMITE_MOUND);
            event.accept(SavannaItems.TERMITE_HOLE);
            event.accept(SavannaItems.TERMITE_SPIRE);
            event.accept(SavannaItems.TERMITE_MOUTH);
            event.accept(SavannaItems.TERMITE_RAMP);
            event.accept(SavannaItems.TERMITE_TUNNEL);
            // Dry Grass placed by hand is dry; the green ones are for watching it dry out.
            event.accept(SavannaItems.DRY_GRASS);
            event.accept(greenGrass(SavannaItems.DRY_GRASS.toStack(), "block.antcolonies.dry_grass.green"));
            event.accept(SavannaItems.TALL_DRY_GRASS);
            event.accept(greenGrass(SavannaItems.TALL_DRY_GRASS.toStack(), "block.antcolonies.tall_dry_grass.green"));
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.NATURAL_BLOCKS, 40, event -> {
            event.accept(SavannaItems.FALLEN_BRANCH);
            event.accept(SavannaItems.DEAD_WOOD);
            event.accept(SavannaItems.MUD_CRUST);
            // The giant savanna trees' blocks (M7.5).
            SavannaItems.TREE_ITEMS.forEach(event::accept);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.INGREDIENTS, 20, event -> {
            event.accept(SavannaItems.FORAGE);
            event.accept(SavannaItems.FUNGUS_NODULE);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.FOOD_AND_DRINKS, 20, event -> {
            event.accept(SavannaItems.TERMITOMYCES);
            event.accept(SavannaItems.COOKED_TERMITOMYCES);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.SPAWN_EGGS, 20, event -> {
            event.accept(SavannaItems.TERMITE_QUEEN_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_KING_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_WORKER_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_MINOR_SOLDIER_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_MAJOR_SOLDIER_SPAWN_EGG);
            event.accept(SavannaItems.MATABELE_MAJOR_SPAWN_EGG);
            event.accept(SavannaItems.MATABELE_MINOR_SPAWN_EGG);
        });
    }
}
