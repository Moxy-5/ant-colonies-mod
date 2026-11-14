package com.antcolonies.savanna.block;

import com.antcolonies.savanna.registry.SavannaBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Keeps a doum palm piece's recipe ({@link DoumLook}, floodplain trees step 5), saves it and sends it to players, and
 * hands it to the client's model, which draws the piece from it once (the drawing goes into the chunk's picture, so it
 * costs nothing per frame).
 */
public class DoumBlockEntity extends BlockEntity {
    /** What the client's model reads. */
    public static final ModelProperty<DoumLook> LOOK = new ModelProperty<>();

    /** Set by a growing palm; null for a piece placed by hand. */
    private DoumLook look;

    public DoumBlockEntity(BlockPos pos, BlockState state) {
        super(SavannaBlockEntities.DOUM_PIECE.get(), pos, state);
    }

    /** The recipe this piece draws. */
    public DoumLook look() {
        return look != null ? look : DoumLook.byPlace(worldPosition);
    }

    /** A growing palm sets the piece it planned (step 6). */
    public void setLook(DoumLook look) {
        this.look = look;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (look != null) {
            tag.put("look", look.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        look = tag.contains("look") ? DoumLook.load(tag.getCompound("look")) : null;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(LOOK, look()).build();
    }
}
