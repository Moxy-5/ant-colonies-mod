package com.antcolonies.entity.load;

import com.antcolonies.ColonyFrameworkMod;
import com.antcolonies.registry.ModTags;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The framework's own rows of the load list ({@link LoadKind}), with today's save words: a soil pellet
 * ({@code CarriedBlock}), an item in the jaws (a seed, bread, forage, a fungus nodule: {@code CarriedSeed}), nurse food
 * in the crop to pass on mouth to mouth ({@code CarriedSeed} with {@code FoodInCrop}), an egg ({@code CarryingEgg}) and a
 * dead nestmate ({@code CarryingCorpse}).
 */
public final class FrameworkLoads {
    /** A soil pellet: just lost when its carrier dies. */
    public static final LoadKind SOIL = LoadKind.signUp(new LoadKind(name("soil"),
            (tag, load, registries) -> tag.put("CarriedBlock", NbtUtils.writeBlockState(load.block())),
            (tag, registries) -> tag.contains("CarriedBlock")
                    ? CarriedLoad.of(FrameworkLoads.SOIL, NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("CarriedBlock")))
                    : null,
            (level, colony, carrier, load) -> { }));

    /**
     * An item in the jaws (a seed, a piece of bread, forage, a fungus nodule). When its carrier dies, nurse food is put
     * down as a heap if there is a floor; anything else drops as an item.
     */
    public static final LoadKind ITEM = LoadKind.signUp(new LoadKind(name("item"),
            (tag, load, registries) -> tag.put("CarriedSeed", load.item().save(registries)),
            (tag, registries) -> tag.contains("CarriedSeed") && !tag.getBoolean("FoodInCrop") ? item(FrameworkLoads.ITEM, tag, registries) : null,
            (level, colony, carrier, load) -> {
                if (load.item().is(ModTags.NURSE_FOOD) || load.item().is(ModTags.RICH_NURSE_FOOD)) {
                    colony.nurseFood().putDownWhereItStands(level, carrier);
                } else {
                    carrier.spawnAtLocation(load.item());
                }
            }));

    /** Nurse food a nurse ate to pass on mouth to mouth: it counts as carried, but nothing shows. Eaten: it dies with it. */
    public static final LoadKind CROP = LoadKind.signUp(new LoadKind(name("crop"),
            (tag, load, registries) -> {
                tag.put("CarriedSeed", load.item().save(registries));
                tag.putBoolean("FoodInCrop", true);
            },
            (tag, registries) -> tag.contains("CarriedSeed") && tag.getBoolean("FoodInCrop") ? item(FrameworkLoads.CROP, tag, registries) : null,
            (level, colony, carrier, load) -> { }));

    /** An egg on its way to the nursery: it falls where its carrier died. */
    public static final LoadKind EGG = LoadKind.signUp(LoadKind.flag(name("egg"), "CarryingEgg",
            (level, colony, carrier, load) -> colony.brood().spawnEgg(level, carrier.getX(), carrier.getY(), carrier.getZ())));

    /** A dead nestmate on its way to the refuse chamber: it falls where its carrier died. */
    public static final LoadKind CORPSE = LoadKind.signUp(LoadKind.flag(name("corpse"), "CarryingCorpse",
            (level, colony, carrier, load) -> colony.refuse().spawnCorpse(level, carrier.getX(), carrier.getY(), carrier.getZ())));

    private FrameworkLoads() {
    }

    /** Called from the framework's start, so its rows are in the list before any save is read or any slot synced. */
    public static void signUp() {
    }

    private static ResourceLocation name(String path) {
        return ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, path);
    }

    /** The item saved under {@code CarriedSeed}, or null if it no longer reads as an item (gone from the game). */
    private static CarriedLoad item(LoadKind kind, net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.parse(registries, tag.getCompound("CarriedSeed")).orElse(ItemStack.EMPTY);
        return stack.isEmpty() ? null : CarriedLoad.of(kind, stack);
    }
}
