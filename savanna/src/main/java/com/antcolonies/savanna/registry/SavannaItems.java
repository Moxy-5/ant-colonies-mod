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
    public static final DeferredItem<BlockItem> ALGAE = ITEMS.registerSimpleBlockItem(SavannaBlocks.ALGAE);

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
