package com.antcolonies.savanna.registry;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The savanna's own particles (2026-11-23): the red mist of a kill under water ({@code animal/BloodMist}) and the muddy
 * footprints a player leaves ({@code client/MudFootprints}). How they look is in {@code client/SavannaParticleLooks}.
 */
public final class SavannaParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, SavannaMod.MODID);

    /** A puff of blood clouding the water round a kill (sent by the server). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_MIST = PARTICLE_TYPES.register("blood_mist",
            () -> new SimpleParticleType(false));
    /** A muddy footprint lying flat on the ground (made on each player's own computer). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUD_FOOTPRINT = PARTICLE_TYPES.register("mud_footprint",
            () -> new SimpleParticleType(true));

    private SavannaParticles() {
    }
}
