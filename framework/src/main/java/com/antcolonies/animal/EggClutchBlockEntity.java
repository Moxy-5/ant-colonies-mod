package com.antcolonies.animal;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a clutch of eggs remembers (the animal life kit): when it was laid, how many eggs, and its mother's kind. Saved
 * with the land, never ticking. Each species mod signs up the block entity type of its own clutch blocks and passes it
 * here.
 */
public class EggClutchBlockEntity extends BlockEntity {
    private long laid;
    private int count = 1;
    @Nullable
    private ResourceLocation mother;

    public EggClutchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Sets the clutch as just laid by a mother of this kind. */
    public void lay(EntityType<?> motherKind, int eggs, long gameTime) {
        mother = BuiltInRegistries.ENTITY_TYPE.getKey(motherKind);
        count = Math.max(1, eggs);
        laid = gameTime;
        setChanged();
    }

    public long laid() {
        return laid;
    }

    public int count() {
        return count;
    }

    /** Its mother's kind, if known. */
    @Nullable
    public EntityType<?> motherKind() {
        return mother == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(mother).orElse(null);
    }

    /** Takes eggs away (eaten); true when none are left. */
    public boolean takeEggs(int eggs) {
        count = Math.max(0, count - eggs);
        setChanged();
        return count == 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Laid", laid);
        tag.putInt("Count", count);
        if (mother != null) {
            tag.putString("Mother", mother.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        laid = tag.getLong("Laid");
        count = tag.contains("Count") ? tag.getInt("Count") : 1;
        mother = tag.contains("Mother") ? ResourceLocation.tryParse(tag.getString("Mother")) : null;
    }
}
