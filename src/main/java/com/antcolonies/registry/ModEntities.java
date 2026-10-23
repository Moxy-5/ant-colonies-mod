package com.antcolonies.registry;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntEggEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.entity.QueenAntEntity;
import com.antcolonies.entity.TermiteKingEntity;
import com.antcolonies.entity.TermiteQueenEntity;
import com.antcolonies.entity.TermiteSoldierEntity;
import com.antcolonies.entity.TermiteWorkerEntity;
import com.antcolonies.entity.WorkerAntEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AntColoniesMod.MODID);

    // Hitbox: 0.3 wide x 0.2 tall (smaller than a vanilla tadpole)
    public static final DeferredHolder<EntityType<?>, EntityType<WorkerAntEntity>> WORKER_ANT =
            ENTITY_TYPES.register("worker_ant", () -> EntityType.Builder
                    .of(WorkerAntEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.2F)
                    .build("worker_ant"));

    // An egg lying in the nursery.
    public static final DeferredHolder<EntityType<?>, EntityType<AntEggEntity>> ANT_EGG =
            ENTITY_TYPES.register("ant_egg", () -> EntityType.Builder
                    .of(AntEggEntity::new, MobCategory.MISC)
                    .sized(0.12F, 0.16F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("ant_egg"));

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

    // The body of a dead termite (the same kind of body as a dead ant, drawn pale).
    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> TERMITE_CORPSE =
            ENTITY_TYPES.register("termite_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.15F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("termite_corpse"));

    // Queen is about twice the worker's size.
    public static final DeferredHolder<EntityType<?>, EntityType<QueenAntEntity>> QUEEN_ANT =
            ENTITY_TYPES.register("queen_ant", () -> EntityType.Builder
                    .of(QueenAntEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.4F)
                    .build("queen_ant"));

    // Termite king (Macrotermes): about twice a worker's length, dark brown with eyes and wing stubs.
    public static final DeferredHolder<EntityType<?>, EntityType<TermiteKingEntity>> TERMITE_KING =
            ENTITY_TYPES.register("termite_king", () -> EntityType.Builder
                    .of(TermiteKingEntity::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.25F)
                    .build("termite_king"));

    // Termite queen: starts the king's size; her hitbox grows as she swells (TermiteQueenEntity.getDefaultDimensions).
    public static final DeferredHolder<EntityType<?>, EntityType<TermiteQueenEntity>> TERMITE_QUEEN =
            ENTITY_TYPES.register("termite_queen", () -> EntityType.Builder
                    .of(TermiteQueenEntity::new, MobCategory.CREATURE)
                    .sized(0.35F, 0.25F)
                    .build("termite_queen"));

    // Termite worker: about the size of an ant worker.
    public static final DeferredHolder<EntityType<?>, EntityType<TermiteWorkerEntity>> TERMITE_WORKER =
            ENTITY_TYPES.register("termite_worker", () -> EntityType.Builder
                    .of(TermiteWorkerEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.2F)
                    .build("termite_worker"));

    // Termite minor soldier: about a worker's size with a bigger head (a little taller than a worker).
    public static final DeferredHolder<EntityType<?>, EntityType<TermiteSoldierEntity>> TERMITE_MINOR_SOLDIER =
            ENTITY_TYPES.register("termite_minor_soldier", () -> EntityType.Builder
                    .<TermiteSoldierEntity>of((type, level) -> new TermiteSoldierEntity(type, level, TermiteSoldierEntity.Rank.MINOR), MobCategory.CREATURE)
                    .sized(0.3F, 0.22F)
                    .build("termite_minor_soldier"));

    // Termite major soldier: about twice a worker's length, with a huge head.
    public static final DeferredHolder<EntityType<?>, EntityType<TermiteSoldierEntity>> TERMITE_MAJOR_SOLDIER =
            ENTITY_TYPES.register("termite_major_soldier", () -> EntityType.Builder
                    .<TermiteSoldierEntity>of((type, level) -> new TermiteSoldierEntity(type, level, TermiteSoldierEntity.Rank.MAJOR), MobCategory.CREATURE)
                    .sized(0.45F, 0.3F)
                    .build("termite_major_soldier"));

    // A termite nymph: what a termite egg hatches into (the same kind of young as an ant larva, drawn as a small pale termite).
    public static final DeferredHolder<EntityType<?>, EntityType<AntLarvaEntity>> TERMITE_NYMPH =
            ENTITY_TYPES.register("termite_nymph", () -> EntityType.Builder
                    .<AntLarvaEntity>of(AntLarvaEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.12F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("termite_nymph"));

    // Apex step 9c: each species' bodies, for the shared brood and population code (what eggs hatch into, the dead, each caste's mob).
    static {
        com.antcolonies.species.SpeciesBodies.register(com.antcolonies.species.Species.RED_HARVESTER_ANT, new com.antcolonies.species.SpeciesBodies(
                ANT_LARVA, ANT_CORPSE, java.util.Map.of(WorkerAntEntity.CASTE, WORKER_ANT)));
        com.antcolonies.species.SpeciesBodies.register(com.antcolonies.species.Species.MACROTERMES, new com.antcolonies.species.SpeciesBodies(
                TERMITE_NYMPH, TERMITE_CORPSE, java.util.Map.of(TermiteWorkerEntity.CASTE, TERMITE_WORKER,
                        TermiteSoldierEntity.Rank.MINOR.caste(), TERMITE_MINOR_SOLDIER, TermiteSoldierEntity.Rank.MAJOR.caste(), TERMITE_MAJOR_SOLDIER)));
    }
}
