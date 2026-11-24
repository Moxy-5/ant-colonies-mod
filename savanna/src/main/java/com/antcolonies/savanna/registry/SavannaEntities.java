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
import com.antcolonies.savanna.species.InsectSizes;
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

    /**
     * Real sizes (2026-11-05, {@link InsectSizes}): the termites' young and dead shrink as the worker does (their boxes
     * and drawing were made the worker's size), the Matabele's as the major does. The living insects' boxes below are
     * their models' own; each insect's size factor scales them in the game ({@code BodySizes}).
     */
    public static final float TERMITE_SIZE = InsectSizes.TERMITE_WORKER.factor();

    // The body of a dead termite (the same kind of body as a dead ant, drawn pale).
    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> TERMITE_CORPSE =
            ENTITY_TYPES.register("termite_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.25F * TERMITE_SIZE, 0.15F * TERMITE_SIZE)
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
                    .sized(0.2F * TERMITE_SIZE, 0.12F * TERMITE_SIZE)
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

    // The Nile tilapia (M9 river life, 2026-11-14): a river fish about 0.6 blocks long grown (my pick, made
    // bigger than life so it is seen, the user's ask), its box the
    // grown fish's; young and big fish are scaled through their SCALE attribute.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.TilapiaEntity>> TILAPIA =
            ENTITY_TYPES.register("tilapia", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.TilapiaEntity::new, SavannaMobCategories.riverFish())
                    .sized(0.5F, 0.3F)                  // a thin fish 0.6 long, 0.25 tall (was 0.6 x 0.4, taller and wider than drawn; 2026-11-22)
                    .eyeHeight(0.15F)
                    .clientTrackingRange(4)
                    .build("tilapia"));

    // The river barb (M9 river life, the small prey fish, 2026-11-20): drawn 0.25 blocks long (my pick); a small box
    // (my pick), each fish's own size through its SCALE attribute. Its own spawning group, river schools.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.BarbEntity>> BARB =
            ENTITY_TYPES.register("barb", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.BarbEntity::new, SavannaMobCategories.riverSchool())
                    .sized(0.3F, 0.2F)
                    .eyeHeight(0.1F)
                    .clientTrackingRange(4)
                    .build("barb"));

    // The six-banded distichodus (M9 river life, the river's big plant eater; the river food web's step 5, 2026-11-23):
    // drawn 0.8 blocks long grown, a deep flat-sided fish (my pick: the box 0.6 across, 0.35 tall), each fish's own size
    // through its SCALE attribute. Its own spawning group, the river grazers.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.DistichodusEntity>> DISTICHODUS =
            ENTITY_TYPES.register("distichodus", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.DistichodusEntity::new, SavannaMobCategories.riverGrazer())
                    .sized(0.6F, 0.35F)
                    .eyeHeight(0.18F)
                    .clientTrackingRange(4)
                    .build("distichodus"));

    // The bullfrog's tadpole (the river tie-up, 2026-11-23): drawn 0.15 blocks long (the plan's pick), a small box. Its
    // own spawning group, so its schools never fill the bullfrogs' 4 places or the barbs'.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.TadpoleEntity>> TADPOLE =
            ENTITY_TYPES.register("tadpole", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.TadpoleEntity::new, SavannaMobCategories.tadpole())
                    .sized(0.18F, 0.12F)
                    .eyeHeight(0.06F)
                    .clientTrackingRange(4)
                    .build("tadpole"));

    // A cloud of flies (fly clouds step 2, 2026-11-20): one mob for the whole cloud, its flies drawn by its renderer; a
    // small box round its middle (my pick), not collided with, not hurt by fire. Its own spawning group, fly clouds.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.FlyCloudEntity>> FLY_CLOUD =
            ENTITY_TYPES.register("fly_cloud", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.FlyCloudEntity::new, SavannaMobCategories.flyCloud())
                    .sized(0.6F, 0.6F)
                    .eyeHeight(0.3F)
                    .fireImmune()
                    .clientTrackingRange(3)
                    .build("fly_cloud"));

    // The African bullfrog (M9 river life, prey plan step 3, 2026-11-20): a male drawn 0.8 blocks long, a female 0.65 (the
    // user's sizes) through SCALE; its box a male's body (my pick), under a block wide so its routes use 1-block squares.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.BullfrogEntity>> BULLFROG =
            ENTITY_TYPES.register("bullfrog", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.BullfrogEntity::new, SavannaMobCategories.bullfrog())
                    .sized(0.6F, 0.4F)
                    .eyeHeight(0.32F)
                    .clientTrackingRange(6)
                    .build("bullfrog"));

    // The river catfish (M9 river life, the hunter, 2026-11-14): drawn 2 blocks long (the user's pick); its box shorter
    // than the drawn fish (my pick), so it can turn in narrow water; each fish's own size through its SCALE attribute.
    // Under 0.87 wide so even the biggest (1.15) stays under 1: Minecraft plans a swimmer's route in squares of its
    // width plus one, rounded down, and a 2-wide square never fits a corner or a bank (it could not reach prey there).
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.CatfishEntity>> CATFISH =
            ENTITY_TYPES.register("catfish", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.CatfishEntity::new, SavannaMobCategories.riverFish())
                    .sized(0.8F, 0.45F)
                    .eyeHeight(0.25F)
                    .clientTrackingRange(6)
                    .build("catfish"));

    // The Nile crocodile (M9 river life, 2026-11-15): drawn about 5 blocks long (my pick); its box much shorter than the
    // drawn animal and under a block wide at every size (0.8 to 1.1), so its routes use 1-block squares (see the catfish).
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.CrocodileEntity>> CROCODILE =
            ENTITY_TYPES.register("crocodile", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.CrocodileEntity::new, SavannaMobCategories.riverHunter())
                    .sized(0.9F, 0.6F)
                    .eyeHeight(0.5F)
                    .clientTrackingRange(8)
                    .build("crocodile"));

    // The bull shark (M9 river life, 2026-11-22): drawn 5.5 blocks long at size 1 (5.0 to 7.0 by its own size, through
    // SCALE); its box the middle of its body, 1.2 wide and 1.5 tall (my picks, measured on the v14 model: the body 1.05
    // wide, its back 1.48 over its feet), the rest of it covered by its hit chain. Water only.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.BullSharkEntity>> BULL_SHARK =
            ENTITY_TYPES.register("bull_shark", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.BullSharkEntity::new, SavannaMobCategories.bullShark())
                    .sized(1.2F, 1.5F)
                    .eyeHeight(1.0F)
                    .clientTrackingRange(10)
                    .build("bull_shark"));

    // The Nile monitor (M9 river life, 2026-11-17): drawn 4.5 blocks long nose to tail (the user, 2026-11-17; was 6),
    // most of it a thin tail; its box covers the body only, under a block wide, so its routes use 1-block squares.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.MonitorEntity>> MONITOR =
            ENTITY_TYPES.register("monitor", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.MonitorEntity::new, SavannaMobCategories.riverBank())
                    .sized(0.7F, 0.55F)
                    .eyeHeight(0.45F)
                    .clientTrackingRange(8)
                    .build("monitor"));

    // The hippopotamus (M9 savanna life, 2026-11-21): drawn 5 blocks long and 2 tall (a bull, the approved v8 model); its
    // box covers the barrel of its body, about 1.9 wide and 1.8 tall at a bull's size (its own size through SCALE), so
    // it is pushed and hit where it is drawn; its routes are two blocks wide, as the ravager's.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.HippoEntity>> HIPPO =
            ENTITY_TYPES.register("hippo", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.HippoEntity::new, SavannaMobCategories.hippoPod())
                    .sized(1.9F, 1.8F)
                    .eyeHeight(1.5F)
                    .clientTrackingRange(10)
                    .build("hippo"));

    // The African clawless otter (M9 savanna life, 2026-11-22): drawn 2.6 blocks long nose to tail tip, 1 tall at the
    // crown (the approved v6 model); its box covers the body's middle, sleek enough for one-block gaps (Neon's card),
    // its head, rump and tail hit boxes along the rest (HitChain); each otter's own size through SCALE.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.OtterEntity>> OTTER =
            ENTITY_TYPES.register("otter", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.OtterEntity::new, SavannaMobCategories.riverOtter())
                    .sized(0.75F, 0.85F)
                    .eyeHeight(0.7F)
                    .clientTrackingRange(8)
                    .build("otter"));

    // The river crab (M9 river life, the clean-up crew, 2026-11-15): drawn about 0.8 blocks across with its legs and 0.3
    // tall; its box a little narrower than the legs' spread (my pick); each crab's own size through its SCALE attribute.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.CrabEntity>> CRAB =
            ENTITY_TYPES.register("crab", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.CrabEntity::new, SavannaMobCategories.riverBed())
                    .sized(0.6F, 0.3F)
                    .eyeHeight(0.25F)
                    .clientTrackingRange(4)
                    .build("crab"));

    // The river snail (M9 river life, the clean-up crew, 2026-11-16): the Garden spiral, drawn 0.4 blocks long and about
    // 0.3 tall with its shell; a small box (my pick); each snail's own size through its SCALE attribute.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.SnailEntity>> SNAIL =
            ENTITY_TYPES.register("snail", () -> EntityType.Builder
                    .of(com.antcolonies.savanna.animal.SnailEntity::new, SavannaMobCategories.riverBed())
                    .sized(0.3F, 0.25F)
                    .eyeHeight(0.2F)
                    .clientTrackingRange(4)
                    .build("snail"));

    // Remains of a dead fish (M9 river life, 2026-11-15): meat on the bones, lying on the bed; a small box under the drawn
    // fish (a usual catfish 2 blocks long), not a living thing.
    public static final DeferredHolder<EntityType<?>, EntityType<com.antcolonies.savanna.animal.RemainsEntity>> REMAINS =
            ENTITY_TYPES.register("remains", () -> EntityType.Builder
                    .<com.antcolonies.savanna.animal.RemainsEntity>of(com.antcolonies.savanna.animal.RemainsEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.25F)
                    .clientTrackingRange(6)
                    .build("remains"));

    // M8 step 1c: the Matabele young and dead, for the shared brood and refuse code (no nest to raise young in yet, step
    // 1d; the dead are carried home in step 5). Drawn MATABELE_DRAW_SIZE times a small ant's grub and body.
    public static final float MATABELE_DRAW_SIZE = 2.0F * InsectSizes.MATABELE_MAJOR.factor();

    public static final DeferredHolder<EntityType<?>, EntityType<AntLarvaEntity>> MATABELE_LARVA =
            ENTITY_TYPES.register("matabele_larva", () -> EntityType.Builder
                    .<AntLarvaEntity>of(AntLarvaEntity::new, MobCategory.MISC)
                    .sized(0.4F * InsectSizes.MATABELE_MAJOR.factor(), 0.24F * InsectSizes.MATABELE_MAJOR.factor())
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("matabele_larva"));

    public static final DeferredHolder<EntityType<?>, EntityType<AntCorpseEntity>> MATABELE_CORPSE =
            ENTITY_TYPES.register("matabele_corpse", () -> EntityType.Builder
                    .of(AntCorpseEntity::new, MobCategory.MISC)
                    .sized(0.5F * InsectSizes.MATABELE_MAJOR.factor(), 0.3F * InsectSizes.MATABELE_MAJOR.factor())
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
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/termite_corpse.png"), Map.of(), TERMITE_SIZE));
        // M8 step 1c: the Matabele workers are one caste; each new one gets the major's or the minor's body as often as
        // the real size spread draws each (MatabeleEntity.pickBody).
        SpeciesBodies.register(SavannaSpecies.MATABELE, new SpeciesBodies(MATABELE_LARVA, MATABELE_CORPSE,
                Map.of(MatabeleEntity.CASTE, MATABELE_MAJOR),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_larva.png"),
                ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "textures/entity/matabele_corpse.png"),
                Map.of(MatabeleEntity.CASTE, MatabeleEntity::pickBody), MATABELE_DRAW_SIZE));
    }
}
