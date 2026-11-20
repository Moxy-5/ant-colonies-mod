package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.DoumBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The savanna mod's own block entities (floodplain trees step 5: the doum palm's sculpted pieces keep their recipe). */
public final class SavannaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            SavannaMod.MODID);

    /** A doum crown, nut bunch, seedling or rosette: the recipe its model draws. */
    @SuppressWarnings("DataFlowIssue") // Minecraft's own blocks pass null for the data fixer type, as here
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DoumBlockEntity>> DOUM_PIECE = BLOCK_ENTITIES.register("doum_piece",
            () -> BlockEntityType.Builder.of(DoumBlockEntity::new, TreeBlocks.DOUM_CROWN.get(), TreeBlocks.DOUM_NUT_BUNCH.get(),
                    TreeBlocks.DOUM_SEEDLING.get(), TreeBlocks.DOUM_ROSETTE.get(), TreeBlocks.DOUM_FRONDS.get()).build(null));

    /** A clutch of eggs (the animal life kit's {@code EggClutchBlockEntity}): the river snail's eggs and the catfish's eggs on the algae. */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.antcolonies.animal.EggClutchBlockEntity>> EGG_CLUTCH = BLOCK_ENTITIES.register("egg_clutch",
            () -> BlockEntityType.Builder.<com.antcolonies.animal.EggClutchBlockEntity>of(
                    (pos, state) -> new com.antcolonies.animal.EggClutchBlockEntity(SavannaBlockEntities.EGG_CLUTCH.get(), pos, state),
                    SavannaBlocks.SNAIL_EGGS.get(), SavannaBlocks.CATFISH_EGGS.get()).build(null));

    private SavannaBlockEntities() {
    }
}
