package com.antcolonies.entity.load;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What an insect holds right now (Project Apex part 3, step T3): which row of the load list ({@link LoadKind}) and,
 * for the rows that need one, the soil block or the item (a seed, a piece of bread, forage, a fungus nodule). One
 * synced slot of the insect ({@code AbstractAntEntity.getLoad}), sent to the players as the row's name and its block and
 * item.
 */
public record CarriedLoad(LoadKind kind, @Nullable BlockState block, ItemStack item) {
    public static final CarriedLoad NONE = new CarriedLoad(LoadKind.NONE, null, ItemStack.EMPTY);

    /** A load that is only held (an egg, a dead nestmate). */
    public static CarriedLoad of(LoadKind kind) {
        return new CarriedLoad(kind, null, ItemStack.EMPTY);
    }

    /** A load of soil (a pellet of this block). */
    public static CarriedLoad of(LoadKind kind, BlockState block) {
        return new CarriedLoad(kind, block, ItemStack.EMPTY);
    }

    /** A load that is an item (a seed, bread, forage, a nodule). */
    public static CarriedLoad of(LoadKind kind, ItemStack item) {
        return new CarriedLoad(kind, null, item);
    }

    public boolean isEmpty() {
        return kind == LoadKind.NONE;
    }

    public boolean is(LoadKind other) {
        return kind == other;
    }

    /** The row's name, then the block (0 = none, else its id + 1, as Minecraft's own optional block slot) and the item. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CarriedLoad> STREAM_CODEC = StreamCodec.of(
            (buf, load) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, load.kind.name());
                ByteBufCodecs.VAR_INT.encode(buf, load.block == null ? 0 : Block.getId(load.block) + 1);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, load.item);
            },
            buf -> {
                LoadKind kind = LoadKind.byName(ResourceLocation.STREAM_CODEC.decode(buf));
                int block = ByteBufCodecs.VAR_INT.decode(buf);
                ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                return kind == LoadKind.NONE ? NONE : new CarriedLoad(kind, block == 0 ? null : Block.stateById(block - 1), item);
            });

    /** The synced slot's type (signed up with the game in {@code registry.ModDataSerializers}). */
    public static final EntityDataSerializer<CarriedLoad> SERIALIZER = EntityDataSerializer.forValueType(STREAM_CODEC);
}
