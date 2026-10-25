package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.entity.AntCorpseEntity;
import com.antcolonies.entity.AntLarvaEntity;
import com.antcolonies.savanna.entity.MatabeleEntity;
import com.antcolonies.savanna.entity.TermiteKingEntity;
import com.antcolonies.savanna.entity.TermiteQueenEntity;
import com.antcolonies.savanna.entity.TermiteSoldierEntity;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.SavannaSpecies;
import com.antcolonies.species.SpeciesBodies;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The termites' mobs: king, queen, worker, soldiers, nymph and the dead (the split, step S4). */
public class SavannaEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SavannaMod.MODID);

    // The body of a dead termite (the same kind of body as a dead ant, drawn pale).
    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> TERMITE_CORPSE =
            ENTITY_TYPES.register("termite_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.15F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("termite_corpse"));

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

    // Matabele ants (M8 step 1, in the game 2026-10-23, the user): a major about 1.7 blocks long, a minor about 0.8 (the
    // model's sizes; the user picks the real ones later). The boxes are the climbing preview's (tools/matabele_climb.py).
    public static final DeferredHolder<EntityType<?>, EntityType<MatabeleEntity>> MATABELE_MAJOR =
            ENTITY_TYPES.register("matabele_major", () -> EntityType.Builder
                    .of(MatabeleEntity::new, MobCategory.CREATURE)
                    .sized(0.4F, 0.3F)
                    .build("matabele_major"));

    public static final DeferredHolder<EntityType<?>, EntityType<MatabeleEntity>> MATABELE_MINOR =
            ENTITY_TYPES.register("matabele_minor", () -> EntityType.Builder
                    .of(MatabeleEntity::new, MobCategory.CREATURE)
                    .sized(0.3F, 0.22F)
                    .build("matabele_minor"));

    // The Matabele queen (M8 step 1c, 2026-10-24): 1.2 times a major (the user's pick), her box the climbing preview's.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.entity.MatabeleQueenEntity>> MATABELE_QUEEN =
            ENTITY_TYPES.register("matabele_queen", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.entity.MatabeleQueenEntity::new, MobCategory.CREATURE)
                    .sized(0.48F, 0.36F)
                    .build("matabele_queen"));

    // M8 step 1c: the Matabele young and dead, for the shared brood and refuse code (no nest to raise young in yet, step
    // 1d; the dead are carried home in step 5). Drawn MATABELE_DRAW_SIZE times a small ant's grub and body.
    public static final float MATABELE_DRAW_SIZE = 2.0F;

    public static final DeferredHolder<EntityType<?>, EntityType<AntLarvaEntity>> MATABELE_LARVA =
            ENTITY_TYPES.register("matabele_larva", () -> EntityType.Builder
                    .<AntLarvaEntity>of(AntLarvaEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.24F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("matabele_larva"));

    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> MATABELE_CORPSE =
            ENTITY_TYPES.register("matabele_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.3F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("matabele_corpse"));

    // Apex step 9c: the species' bodies, for the shared brood and population code (what eggs hatch into, the dead, each
    // caste's mob); the split, S3: with the looks of its young and its dead (the dead: pale and dull, drawn by
    // tools/corpse_textures.py).
    static {
        SpeciesBodies.register(SavannaSpecies.MACROTERMES, new SpeciesBodies(TERMITE_NYMPH, TERMITE_CORPSE,
                Map.of(TermiteWorkerEntity.CASTE, TERMITE_WORKER, TermiteSoldierEntity.Rank.MINOR.caste(), TERMITE_MINOR_SOLDIER,
                        TermiteSoldierEntity.Rank.MAJOR.caste(), TERMITE_MAJOR_SOLDIER),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/termite_nymph.png"),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/termite_corpse.png")));
        // M8 step 1c: the Matabele workers are one caste; each new one gets the major's or the minor's body as often as
        // the real size spread draws each (MatabeleEntity.pickBody).
        SpeciesBodies.register(SavannaSpecies.MATABELE, new SpeciesBodies(MATABELE_LARVA, MATABELE_CORPSE,
                Map.of(MatabeleEntity.CASTE, MATABELE_MAJOR),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_larva.png"),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_corpse.png"),
                Map.of(MatabeleEntity.CASTE, MatabeleEntity::pickBody), MATABELE_DRAW_SIZE));
    }
}
