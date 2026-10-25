package com.antcolonies.registry;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's effects: the pain of an ant's sting (M8 step 2e, {@code fight.Pain}): the player walks a quarter
 * slower, and the screen's edge pulses red while it lasts ({@code client.StingPainOverlay}).
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ColonyFrameworkMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> STING_PAIN = EFFECTS.register("sting_pain", () -> new StingPain()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "sting_pain"),
                    -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private ModEffects() {
    }

    /** A harmful effect, dark red. */
    private static final class StingPain extends MobEffect {
        StingPain() {
            super(MobEffectCategory.HARMFUL, 0x9E1B14);
        }
    }
}
