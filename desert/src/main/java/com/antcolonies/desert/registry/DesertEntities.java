package com.antcolonies.desert.registry;

import com.antcolonies.desert.DesertMod;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.desert.entity.QueenAntEntity;
import com.antcolonies.desert.entity.WorkerAntEntity;
import com.antcolonies.desert.DesertSpecies;
import com.antcolonies.species.SpeciesBodies;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The harvester ant's mobs: the worker, the queen, the larva and the dead (the split, step S4). */
public class DesertEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DesertMod.MODID);

    // Hitbox: 0.3 wide x 0.2 tall (smaller than a vanilla tadpole)
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerAntEntity>> WORKER_ANT =
            ENTITY_TYPES.register("worker_ant", () -> EntityType.Builder
                    .of(WorkerAntEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.2F)
                    .build("worker_ant"));

    // A larva lying in the nursery.
    public static final DeferredHolder<EntityType<?>, EntityType<AntLarvaEntity>> ANT_LARVA =
            ENTITY_TYPES.register("ant_larva", () -> EntityType.Builder
                    .of(AntLarvaEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.15F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("ant_larva"));

    // The body of a dead ant.
    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> ANT_CORPSE =
            ENTITY_TYPES.register("ant_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.15F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("ant_corpse"));

    // Queen is about twice the worker's size.
    public static final DeferredHolder<EntityType<?>, EntityType<QueenAntEntity>> QUEEN_ANT =
            ENTITY_TYPES.register("queen_ant", () -> EntityType.Builder
                    .of(QueenAntEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.4F)
                    .build("queen_ant"));

    // Apex step 9c: the species' bodies, for the shared brood and population code (what eggs hatch into, the dead, each
    // caste's mob); the split, S3: with the looks of its young and its dead.
    static {
        SpeciesBodies.register(DesertSpecies.RED_HARVESTER_ANT, new SpeciesBodies(ANT_LARVA, ANT_CORPSE,
                Map.of(WorkerAntEntity.CASTE, WORKER_ANT),
                ResourceLocation.fromNamespaceAndPath(DesertMod.MODID, "textures/entity/ant_larva.png"),
                ResourceLocation.fromNamespaceAndPath(DesertMod.MODID, "textures/entity/ant_corpse.png")));
    }
}
