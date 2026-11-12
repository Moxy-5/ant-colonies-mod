package com.antcolonies.savanna.registry;

import java.util.function.Supplier;

import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * The camel thorn and baobab boats: new kinds of Minecraft's own boat, added to its list of boat woods through
 * NeoForge's enum extension ({@code META-INF/enumextensions.json}). Minecraft then makes their models and draws them
 * itself, with the textures at {@code textures/entity/boat/<name>.png} and {@code textures/entity/chest_boat/<name>.png}.
 * This class is read while the game starts, before any mod is loaded, so it only holds lazy suppliers.
 */
public final class SavannaBoats {
    private SavannaBoats() {}

    public static final EnumProxy<Boat.Type> CAMEL_THORN = new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>) () -> TreeBlocks.CAMEL_THORN_PLANKS.get(), "antcolonies:camel_thorn",
            (Supplier<Item>) () -> SavannaItems.CAMEL_THORN_ITEMS.boat().get(),
            (Supplier<Item>) () -> SavannaItems.CAMEL_THORN_ITEMS.chestBoat().get(),
            (Supplier<Item>) () -> Items.STICK, false);

    public static final EnumProxy<Boat.Type> BAOBAB = new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>) () -> TreeBlocks.BAOBAB_PLANKS.get(), "antcolonies:baobab",
            (Supplier<Item>) () -> SavannaItems.BAOBAB_ITEMS.boat().get(),
            (Supplier<Item>) () -> SavannaItems.BAOBAB_ITEMS.chestBoat().get(),
            (Supplier<Item>) () -> Items.STICK, false);
}
