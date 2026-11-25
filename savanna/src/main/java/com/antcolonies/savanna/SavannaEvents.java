package com.antcolonies.savanna;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.ModEvents;
import com.antcolonies.savanna.block.DryGrassBlock;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.registry.SavannaItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * health, and the giant trees' fire, lightning, falls, days and bone meal.
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
        event.put(SavannaEntities.MATABELE_QUEEN.get(), com.antcolonies.savanna.entity.MatabeleQueenEntity.createAttributes().build());
        event.put(SavannaEntities.TILAPIA.get(), com.antcolonies.savanna.animal.TilapiaEntity.createAttributes().build());
        event.put(SavannaEntities.CATFISH.get(), com.antcolonies.savanna.animal.CatfishEntity.createAttributes().build());
        event.put(SavannaEntities.CROCODILE.get(), com.antcolonies.savanna.animal.CrocodileEntity.createAttributes().build());
        event.put(SavannaEntities.BULL_SHARK.get(), com.antcolonies.savanna.animal.BullSharkEntity.createAttributes().build());
        event.put(SavannaEntities.MONITOR.get(), com.antcolonies.savanna.animal.MonitorEntity.createAttributes().build());
        event.put(SavannaEntities.HIPPO.get(), com.antcolonies.savanna.animal.HippoEntity.createAttributes().build());
        event.put(SavannaEntities.OTTER.get(), com.antcolonies.savanna.animal.OtterEntity.createAttributes().build());
        event.put(SavannaEntities.CRAB.get(), com.antcolonies.savanna.animal.CrabEntity.createAttributes().build());
        event.put(SavannaEntities.SNAIL.get(), com.antcolonies.savanna.animal.SnailEntity.createAttributes().build());
        event.put(SavannaEntities.BARB.get(), com.antcolonies.savanna.animal.BarbEntity.createAttributes().build());
        event.put(SavannaEntities.DISTICHODUS.get(), com.antcolonies.savanna.animal.DistichodusEntity.createAttributes().build());
        event.put(SavannaEntities.TADPOLE.get(), com.antcolonies.savanna.animal.TadpoleEntity.createAttributes().build());
        event.put(SavannaEntities.FLY_CLOUD.get(), com.antcolonies.savanna.animal.FlyCloudEntity.createAttributes().build());
        event.put(SavannaEntities.BULLFROG.get(), com.antcolonies.savanna.animal.BullfrogEntity.createAttributes().build());
    }

    /** Where the river animals may spawn (M9): the tilapia and the catfish in the savanna's rivers, pans and oxbows. */
    @SubscribeEvent
    public static void onSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        event.register(SavannaEntities.TILAPIA.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.TilapiaEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.CATFISH.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.CatfishEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.CROCODILE.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.CrocodileEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.BULL_SHARK.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.BullSharkEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.MONITOR.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.MonitorEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.OTTER.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.OtterEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.CRAB.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.CrabEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.SNAIL.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.SnailEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.BARB.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.BarbEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.DISTICHODUS.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.DistichodusEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.TADPOLE.get(), net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.TadpoleEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.BULLFROG.get(), net.minecraft.world.entity.SpawnPlacementTypes.NO_RESTRICTIONS,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.BullfrogEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(SavannaEntities.FLY_CLOUD.get(), net.minecraft.world.entity.SpawnPlacementTypes.NO_RESTRICTIONS,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, inBigSavanna(com.antcolonies.savanna.animal.FlyCloudEntity::canSpawn),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * The gate ({@link com.antcolonies.savanna.worldgen.BigSavanna}): the world itself spawns no river animal in a savanna
     * too small to live in; and the river zones ({@link com.antcolonies.savanna.worldgen.RiverZones}, 2026-11-21): none in
     * water shut off from the sky, and each kind only up to its share of its group's cap in its stretch of river. Eggs,
     * commands and buckets are not asked. (Hippos are not spawned by the world at all: new land places their pods.)
     */
    private static <T extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> inBigSavanna(
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> rule) {
        return (type, level, reason, pos, random) -> rule.test(type, level, reason, pos, random)
                && (reason != net.minecraft.world.entity.MobSpawnType.NATURAL && reason != net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                        || com.antcolonies.savanna.worldgen.BigSavanna.lives(level.getLevel(), level.getBiome(pos), pos.getX(), pos.getZ())
                                && com.antcolonies.savanna.worldgen.RiverZones.room(type, level, pos));
    }

    /**
     * No vanilla mobs in the savanna (the user, 2026-11-21: room for the ecosystem's own animals): the spawn lists lose
     * them in {@code biome_modifier/no_vanilla_mobs.json} (chickens and armadillos stay), villages and patrols go by biome
     * tags; phantoms come from not sleeping, not from a list, so they are refused here over the savanna and its rivers.
     */
    @SubscribeEvent
    public static void onPhantoms(net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent event) {
        if (event.getEntity().level().getBiome(event.getEntity().blockPosition()).is(com.antcolonies.savanna.worldgen.RiverBankFeature.WATERS)) {
            event.setResult(net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent.Result.DENY);
        }
    }

    /**
     * A falling tree left mid-fall, and lightning drawn to a big tree. (Chickens hunting termite workers, step 6c, were
     * taken out of the game by the user, 2026-11-25.)
     */
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
    }

    /** The giant trees' day (M7.5, {@code Forest}), after the colonies' clock each tick (as before the split). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.antcolonies.savanna.tree.Forest.tick(level);
            long started = System.nanoTime();
            com.antcolonies.savanna.colony.DeadMounds.tick(level);
            long dead = System.nanoTime();
            com.antcolonies.savanna.colony.NewLandMounds.tick(level);
            long newLand = System.nanoTime();
            com.antcolonies.savanna.command.RaidView.tick(level);
            com.antcolonies.debug.Perf.other("dead mounds", dead - started);
            com.antcolonies.debug.Perf.other("new-land mounds", newLand - dead);
            com.antcolonies.debug.Perf.other("raid view", System.nanoTime() - newLand);
        }
    }

    /** M8 raid rework R4: a block dug on a Matabele scent trail (or the ground under it) breaks the trail there. */
    @SubscribeEvent(priority = EventPriority.LOWEST) // (last: a break another mod or a claim stopped is no break; finding 7)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            com.antcolonies.savanna.colony.Raids.blockChangedNear(level, event.getPos());
        }
    }

    /** M8 raid rework R4: a block put on a Matabele scent trail breaks the trail there. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            com.antcolonies.savanna.colony.Raids.blockChangedNear(level, event.getPos());
        }
    }

    /** The raid view forgets a player who leaves, and everyone when the server stops (the raid code review, finding 14). */
    /**
     * Held in a crocodile's jaws there is no stepping off (2026-11-15): the only way out is to hit it till it lets go,
     * or for it to let go itself. Only a dismount is stopped, and only while the crocodile still holds it.
     */
    @SubscribeEvent
    public static void onMount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof com.antcolonies.savanna.animal.CrocodileEntity croc
                && croc.holds(event.getEntityMounting()) && croc.isAlive() && !croc.isRemoved() && event.getEntityMounting().isAlive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        com.antcolonies.savanna.command.RaidView.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.antcolonies.savanna.command.RaidView.clear();
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
            event.accept(SavannaItems.DEAD_COMB);
            event.accept(SavannaItems.PREY_PILE);
            event.accept(SavannaItems.TERMITE_REMAINS);
            event.accept(SavannaItems.PARALYSED_TERMITE);
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
            event.accept(SavannaItems.SILT);
            event.accept(SavannaItems.WET_SILT);
            event.accept(SavannaItems.RIVER_MUD);
            event.accept(SavannaItems.SAVANNA_MUD);
            event.accept(SavannaItems.ALGAE);
            event.accept(SavannaItems.REEDS);
            event.accept(SavannaItems.HIPPO_GRASS);
            event.accept(SavannaItems.HIPPO_GRASS_CLUMP);
            event.accept(SavannaItems.SCRAPS);
            event.accept(SavannaItems.CROC_DROPPINGS);
            event.accept(SavannaItems.CRAB_SHELL);
            event.accept(SavannaItems.TILAPIA_NEST);
            event.accept(SavannaItems.SNAIL_EGGS);
            event.accept(SavannaItems.CATFISH_EGGS);
            event.accept(SavannaItems.FROG_EGGS);
            SavannaItems.RIVER_SAND.forEach(event::accept);
            event.accept(SavannaItems.RIVER_PEBBLES);
            // The giant savanna trees' blocks (M7.5).
            SavannaItems.TREE_ITEMS.forEach(event::accept);
            SavannaItems.WOOD_ITEMS.forEach(items -> items.all().forEach(event::accept));
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.INGREDIENTS, 20, event -> {
            event.accept(SavannaItems.FORAGE);
            event.accept(SavannaItems.FUNGUS_NODULE);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.FOOD_AND_DRINKS, 20, event -> {
            event.accept(SavannaItems.TERMITOMYCES);
            event.accept(SavannaItems.COOKED_TERMITOMYCES);
            event.accept(SavannaItems.FIG);
            event.accept(SavannaItems.DOUM_NUT);
            event.accept(SavannaItems.TILAPIA);
            event.accept(SavannaItems.COOKED_TILAPIA);
            event.accept(SavannaItems.CATFISH);
            event.accept(SavannaItems.COOKED_CATFISH);
        });
        ModEvents.addToCreativeTab(CreativeModeTabs.TOOLS_AND_UTILITIES, 20, event -> event.accept(SavannaItems.TILAPIA_BUCKET));
        ModEvents.addToCreativeTab(CreativeModeTabs.SPAWN_EGGS, 20, event -> {
            event.accept(SavannaItems.TERMITE_QUEEN_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_KING_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_WORKER_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_MINOR_SOLDIER_SPAWN_EGG);
            event.accept(SavannaItems.TERMITE_MAJOR_SOLDIER_SPAWN_EGG);
            event.accept(SavannaItems.MATABELE_MAJOR_SPAWN_EGG);
            event.accept(SavannaItems.MATABELE_MINOR_SPAWN_EGG);
            event.accept(SavannaItems.MATABELE_QUEEN_SPAWN_EGG);
            event.accept(SavannaItems.TILAPIA_SPAWN_EGG);
            event.accept(SavannaItems.CATFISH_SPAWN_EGG);
            event.accept(SavannaItems.CROCODILE_SPAWN_EGG);
            event.accept(SavannaItems.BULL_SHARK_SPAWN_EGG);
            event.accept(SavannaItems.MONITOR_SPAWN_EGG);
            event.accept(SavannaItems.HIPPO_SPAWN_EGG);
            event.accept(SavannaItems.OTTER_SPAWN_EGG);
            event.accept(SavannaItems.CRAB_SPAWN_EGG);
            event.accept(SavannaItems.SNAIL_SPAWN_EGG);
            event.accept(SavannaItems.BARB_SPAWN_EGG);
            event.accept(SavannaItems.DISTICHODUS_SPAWN_EGG);
            event.accept(SavannaItems.TADPOLE_SPAWN_EGG);
            event.accept(SavannaItems.FLY_CLOUD_SPAWN_EGG);
            event.accept(SavannaItems.BULLFROG_SPAWN_EGG);
        });
    }
}
