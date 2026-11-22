package com.antcolonies.animal;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One hit box of an animal's body chain ({@link HitChain}; 2026-11-22, the user: "my hits aren't really that accurate, is
 * there no way to sculpt a hit box cleverly"): a small box placed on part of the drawn body (a head, a rump, a piece of
 * tail), as the Ender Dragon's parts are. A hit on it (a player's swing, an arrow) is a hit on the animal, a right click
 * is a click on the animal; it is never saved, never moves by itself and never pushes or is pushed. Its size is the
 * animal's own size times the link's.
 */
public class BodyPart extends PartEntity<Mob> {
    public final String name;
    private final float width, height;
    private float sizedFor = -1;

    public BodyPart(Mob owner, String name, float width, float height) {
        super(owner);
        this.name = name;
        this.width = width;
        this.height = height;
        this.noPhysics = true;
        refreshDimensions();
    }

    /** Its own type, not its animal's (NeoForge's parts take the animal's), so a search by the animal's type never finds it. */
    @Override
    public net.minecraft.world.entity.EntityType<?> getType() {
        return com.antcolonies.registry.ModEntities.BODY_PART.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return getParent().isPickable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    @Nullable
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(width, height).scale(getParent().getScale());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Resizes the box when the animal's own size changed (it grows). */
    void sizeToOwner() {
        float scale = getParent().getScale();
        if (scale != sizedFor) {
            sizedFor = scale;
            refreshDimensions();
        }
    }
}
