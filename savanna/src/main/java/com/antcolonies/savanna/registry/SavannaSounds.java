package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The savanna mod's own sounds (M8 step 2e): the Matabele ant's squeak (made by {@code tools/matabele_squeak.py}; real
 * Megaponera stridulate, loud enough for a person to hear, when held or when the nest is broken into).
 */
public final class SavannaSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SavannaMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MATABELE_SQUEAK = SOUNDS.register("matabele_squeak",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "matabele_squeak")));

    /** A fly cloud's buzz (fly clouds step 2, 2026-11-20; made by {@code tools/fly_buzz.py}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLY_BUZZ = SOUNDS.register("fly_buzz",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "fly_buzz")));

    /** The bullfrog's lowing call and its bark (prey plan step 3, 2026-11-20; made by {@code tools/bullfrog_sounds.py}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLFROG_CALL = SOUNDS.register("bullfrog_call",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "bullfrog_call")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLFROG_BARK = SOUNDS.register("bullfrog_bark",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "bullfrog_bark")));

    private SavannaSounds() {
    }
}
