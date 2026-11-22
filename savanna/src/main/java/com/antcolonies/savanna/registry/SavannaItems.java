package com.antcolonies.savanna.registry;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna's items: its blocks', forage, fungus nodules, the mushrooms and the termites' spawn eggs (the split, step S4). */
public class SavannaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SavannaMod.MODID);

    public static final DeferredItem<BlockItem> FORAGE_PILE = ITEMS.registerSimpleBlockItem(SavannaBlocks.FORAGE_PILE);

    public static final DeferredItem<BlockItem> FUNGUS_COMB = ITEMS.registerSimpleBlockItem(SavannaBlocks.FUNGUS_COMB);
    public static final DeferredItem<BlockItem> DEAD_COMB = ITEMS.registerSimpleBlockItem(SavannaBlocks.DEAD_COMB);

    public static final DeferredItem<BlockItem> PREY_PILE = ITEMS.registerSimpleBlockItem(SavannaBlocks.PREY_PILE);
    public static final DeferredItem<BlockItem> TERMITE_REMAINS = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_REMAINS);
    public static final DeferredItem<BlockItem> TERMITE_MOUND = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_MOUND);

    public static final DeferredItem<BlockItem> TERMITE_HOLE = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_HOLE);

    public static final DeferredItem<BlockItem> TERMITE_SPIRE = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_SPIRE);

    public static final DeferredItem<BlockItem> TERMITE_MOUTH = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_MOUTH);

    public static final DeferredItem<BlockItem> TERMITE_RAMP = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_RAMP);

    public static final DeferredItem<BlockItem> TERMITE_TUNNEL = ITEMS.registerSimpleBlockItem(SavannaBlocks.TERMITE_TUNNEL);

    public static final DeferredItem<BlockItem> DRY_GRASS = ITEMS.registerSimpleBlockItem(SavannaBlocks.DRY_GRASS);

    public static final DeferredItem<BlockItem> TALL_DRY_GRASS = ITEMS.registerSimpleBlockItem(SavannaBlocks.TALL_DRY_GRASS);

    public static final DeferredItem<BlockItem> FALLEN_BRANCH = ITEMS.registerSimpleBlockItem(SavannaBlocks.FALLEN_BRANCH);

    public static final DeferredItem<BlockItem> DEAD_WOOD = ITEMS.registerSimpleBlockItem(SavannaBlocks.DEAD_WOOD);

    public static final DeferredItem<BlockItem> MUD_CRUST = ITEMS.registerSimpleBlockItem(SavannaBlocks.MUD_CRUST);

    public static final DeferredItem<BlockItem> SILT = ITEMS.registerSimpleBlockItem(SavannaBlocks.SILT);
    public static final DeferredItem<BlockItem> WET_SILT = ITEMS.registerSimpleBlockItem(SavannaBlocks.WET_SILT);
    public static final DeferredItem<BlockItem> RIVER_MUD = ITEMS.registerSimpleBlockItem(SavannaBlocks.RIVER_MUD);
    public static final DeferredItem<BlockItem> SAVANNA_MUD = ITEMS.registerSimpleBlockItem(SavannaBlocks.SAVANNA_MUD);
    public static final DeferredItem<BlockItem> ALGAE = ITEMS.registerSimpleBlockItem(SavannaBlocks.ALGAE);
    public static final DeferredItem<BlockItem> SCRAPS = ITEMS.registerSimpleBlockItem(SavannaBlocks.SCRAPS);
    public static final DeferredItem<BlockItem> CROC_DROPPINGS = ITEMS.registerSimpleBlockItem(SavannaBlocks.CROC_DROPPINGS);
    public static final DeferredItem<BlockItem> CRAB_SHELL = ITEMS.registerSimpleBlockItem(SavannaBlocks.CRAB_SHELL);
    public static final DeferredItem<BlockItem> TILAPIA_NEST = ITEMS.registerSimpleBlockItem(SavannaBlocks.TILAPIA_NEST);
    public static final DeferredItem<BlockItem> SNAIL_EGGS = ITEMS.registerSimpleBlockItem(SavannaBlocks.SNAIL_EGGS);
    public static final DeferredItem<BlockItem> CATFISH_EGGS = ITEMS.registerSimpleBlockItem(SavannaBlocks.CATFISH_EGGS);
    public static final DeferredItem<BlockItem> CROCODILE_NEST = ITEMS.registerSimpleBlockItem(SavannaBlocks.CROCODILE_NEST);
    /** River sand in its three tones (dry, wet, rippled, crocodile slide each) and river pebbles (sand banks, 2026-11-16). */
    public static final java.util.List<DeferredItem<BlockItem>> RIVER_SAND = java.util.stream.Stream.of(
                    SavannaBlocks.RED_SAND, SavannaBlocks.GOLDEN_SAND, SavannaBlocks.PALE_SAND)
            .flatMap(tone -> tone.all().stream()).map(b -> ITEMS.registerSimpleBlockItem(b)).toList();
    public static final DeferredItem<BlockItem> RIVER_PEBBLES = ITEMS.registerSimpleBlockItem(SavannaBlocks.RIVER_PEBBLES);

    /**
     * A piece of forage: dead plant material a termite cut off a plant (dry grass, a dead bush, dying leaves, a hay
     * bale). What termites carry home to their food store, and what a broken forage pile drops.
     */
    public static final DeferredItem<Item> FORAGE = ITEMS.registerSimpleItem("forage");

    /** A stung, paralysed termite, a piece of the Matabele prey pile (M8 step 1e): what a nurse carries to the queen or a larva. */
    public static final DeferredItem<Item> PARALYSED_TERMITE = ITEMS.registerSimpleItem("paralysed_termite");

    /**
     * A fungus nodule: a small white ball of fungus that grows on the termites' fungus comb. What the nurses feed the
     * termite queen and the nymphs, and what a broken comb with nodules drops.
     */
    public static final DeferredItem<Item> FUNGUS_NODULE = ITEMS.registerSimpleItem("fungus_nodule");

    /**
     * A Termitomyces mushroom, picked round a termite mound after rain: a little food raw (1 drumstick, my pick), a good
     * meal cooked. Not a block item: it is never planted (the mushrooms only come from the termites' gardens).
     */
    public static final DeferredItem<Item> TERMITOMYCES = ITEMS.registerSimpleItem("termitomyces",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build()));

    /** A cooked Termitomyces (furnace, smoker or campfire): 3 drumsticks, about as good as cooked fish (my pick). */
    public static final DeferredItem<Item> COOKED_TERMITOMYCES = ITEMS.registerSimpleItem("cooked_termitomyces",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build()));
    /*
     * The floodplain trees' fruit (steps 4b and 6b, 2026-11-14, my picks). A ripe sycamore fig: small and soft, a little
     * food eaten fast. A ripe doum nut: a hard fruit with a sweet gingerbread-tasting fibrous rind round a stone, more
     * food, chewed slowly. The green ones fall from an unripe bunch broken off: not food.
     */
    public static final DeferredItem<Item> FIG = ITEMS.registerSimpleItem("fig",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).fast().build()));
    public static final DeferredItem<Item> GREEN_FIG = ITEMS.registerSimpleItem("green_fig");
    public static final DeferredItem<Item> DOUM_NUT = ITEMS.registerSimpleItem("doum_nut",
            new Item.Properties().food(new FoodProperties(4, 2.4F, false, 2.4F, java.util.Optional.empty(), java.util.List.of())));
    public static final DeferredItem<Item> GREEN_DOUM_NUT = ITEMS.registerSimpleItem("green_doum_nut");

    /*
     * The Nile tilapia (M9 river life, 2026-11-14, my picks): raw a little food like a cod, cooked a good meal; a bucket
     * carries a living fish (its age, sex, look and size kept).
     */
    public static final DeferredItem<Item> TILAPIA = ITEMS.registerSimpleItem("tilapia",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build()));
    public static final DeferredItem<Item> COOKED_TILAPIA = ITEMS.registerSimpleItem("cooked_tilapia",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
    public static final DeferredItem<Item> TILAPIA_BUCKET = ITEMS.register("tilapia_bucket",
            () -> new net.minecraft.world.item.MobBucketItem(SavannaEntities.TILAPIA.get(), net.minecraft.world.level.material.Fluids.WATER,
                    net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_FISH, new Item.Properties().stacksTo(1)
                            .component(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA, net.minecraft.world.item.component.CustomData.EMPTY)));
    /* The river catfish (M9 river life, 2026-11-14, my picks): a bigger meal than the tilapia; no bucket (too big). */
    public static final DeferredItem<Item> CATFISH = ITEMS.registerSimpleItem("catfish",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.2F).build()));
    public static final DeferredItem<Item> COOKED_CATFISH = ITEMS.registerSimpleItem("cooked_catfish",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.7F).build()));
    // Grey-brown with a cream belly (look A), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> CATFISH_SPAWN_EGG =
            ITEMS.register("catfish_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.CATFISH, 0x423C34, 0xE2D6BA, new Item.Properties()));
    // Dark olive with tan spots (the crocodile's back and side stripe), my pick.
    // Charcoal with yellow spots (the monitor's back and spot rows), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> MONITOR_SPAWN_EGG =
            ITEMS.register("monitor_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.MONITOR, 0x34332B, 0xD6B03A, new Item.Properties()));
    // Grey-purple with a pink belly (the hippo's back and its dusty-pink underside), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> HIPPO_SPAWN_EGG =
            ITEMS.register("hippo_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.HIPPO, 0x4E3E44, 0xC48A8E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CROCODILE_SPAWN_EGG =
            ITEMS.register("crocodile_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.CROCODILE, 0x4A4E26, 0xC8B070, new Item.Properties()));
    // Mottled brown shell with cream claw tips (the user's picture), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> CRAB_SPAWN_EGG =
            ITEMS.register("crab_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.CRAB, 0x5A4632, 0xE6D8B8, new Item.Properties()));
    // Warm brown spiral shell with a cream body (the Garden spiral), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> SNAIL_SPAWN_EGG =
            ITEMS.register("snail_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.SNAIL, 0x8A5A36, 0xE8D8B8, new Item.Properties()));
    // Silver with an orange-red tail tip (the barb's), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> BARB_SPAWN_EGG =
            ITEMS.register("barb_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.BARB, 0xC8CCC4, 0xD8602A, new Item.Properties()));
    // Metallic green with grey (the bottle fly and the house fly), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> FLY_CLOUD_SPAWN_EGG =
            ITEMS.register("fly_cloud_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.FLY_CLOUD, 0x2E6A3A, 0x8A8A86, new Item.Properties()));
    // Olive green with a yellow belly (the bullfrog's), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> BULLFROG_SPAWN_EGG =
            ITEMS.register("bullfrog_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.BULLFROG, 0x4E5E2A, 0xE0BC4A, new Item.Properties()));
    // Olive grey with a red throat (the breeding male's), my pick.
    public static final DeferredItem<DeferredSpawnEggItem> TILAPIA_SPAWN_EGG =
            ITEMS.register("tilapia_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TILAPIA, 0x5E6450, 0xB04A3A, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_KING_SPAWN_EGG =
            ITEMS.register("termite_king_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_KING, 0x42210F, 0xC4923F, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_QUEEN_SPAWN_EGG =
            ITEMS.register("termite_queen_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_QUEEN, 0xEEE2C4, 0x42210F, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_WORKER_SPAWN_EGG =
            ITEMS.register("termite_worker_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_WORKER, 0xECE2C8, 0xD4AC60, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MINOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_minor_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_MINOR_SOLDIER, 0xECE2C8, 0xB25E24, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> TERMITE_MAJOR_SOLDIER_SPAWN_EGG =
            ITEMS.register("termite_major_soldier_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.TERMITE_MAJOR_SOLDIER, 0xB25E24, 0x18100B, new Item.Properties()));

    // The Matabele ants (2026-10-23, the user's colours): the major dark black with dark grey spots, the minor black with grey.
    public static final DeferredItem<DeferredSpawnEggItem> MATABELE_MAJOR_SPAWN_EGG =
            ITEMS.register("matabele_major_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.MATABELE_MAJOR, 0x0B0B0B, 0x2E2E2E, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> MATABELE_MINOR_SPAWN_EGG =
            ITEMS.register("matabele_minor_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.MATABELE_MINOR, 0x1C1C1C, 0x787878, new Item.Properties()));

    // The queen (2026-10-24): black with a deep red spot, my pick, to tell her egg from the workers'.
    public static final DeferredItem<DeferredSpawnEggItem> MATABELE_QUEEN_SPAWN_EGG =
            ITEMS.register("matabele_queen_spawn_egg",
                    () -> new DeferredSpawnEggItem(SavannaEntities.MATABELE_QUEEN, 0x050505, 0x050505, new Item.Properties()));

    /** The items of the giant savanna trees' blocks (M7.5), in creative tab order. */
    public static final List<DeferredItem<BlockItem>> TREE_ITEMS = new ArrayList<>();

    static {
        for (DeferredBlock<? extends Block> block : TreeBlocks.ALL) {
            TREE_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
    }

    /** A wood's signs and boats, the items that are not simply a block's (the user, 2026-11-11). */
    public record WoodItems(DeferredItem<SignItem> sign, DeferredItem<HangingSignItem> hangingSign,
                            DeferredItem<BoatItem> boat, DeferredItem<BoatItem> chestBoat) {
        public List<DeferredItem<? extends Item>> all() {
            return List.of(sign, hangingSign, boat, chestBoat);
        }
    }

    public static final WoodItems CAMEL_THORN_ITEMS = woodItems(TreeBlocks.CAMEL_THORN_SET, () -> SavannaBoats.CAMEL_THORN.getValue());
    public static final WoodItems BAOBAB_ITEMS = woodItems(TreeBlocks.BAOBAB_SET, () -> SavannaBoats.BAOBAB.getValue());
    public static final List<WoodItems> WOOD_ITEMS = List.of(CAMEL_THORN_ITEMS, BAOBAB_ITEMS);

    private static WoodItems woodItems(TreeBlocks.WoodSet set, java.util.function.Supplier<Boat.Type> boatType) {
        String name = set.name();
        var sign = ITEMS.registerItem(name + "_sign",
                p -> new SignItem(p, set.sign().get(), set.wallSign().get()), new Item.Properties().stacksTo(16));
        var hanging = ITEMS.registerItem(name + "_hanging_sign",
                p -> new HangingSignItem(set.hangingSign().get(), set.wallHangingSign().get(), p), new Item.Properties().stacksTo(16));
        var boat = ITEMS.registerItem(name + "_boat", p -> new BoatItem(false, boatType.get(), p), new Item.Properties().stacksTo(1));
        var chestBoat = ITEMS.registerItem(name + "_chest_boat", p -> new BoatItem(true, boatType.get(), p), new Item.Properties().stacksTo(1));
        return new WoodItems(sign, hanging, boat, chestBoat);
    }
}
