package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaMod;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import terrablender.worldgen.IExtendedNoiseGeneratorSettings;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/**
 * TerraBlender's surface layer skipped where it changes nothing (loading time, 2026-11-10).
 *
 * <p>TerraBlender wraps every world's surface recipe (grass, sand, stone kinds) in a chooser that lets each biome mod
 * bring its own recipe: for every block of every column it looks up the block's biome, picks that biome's mod's recipe,
 * and falls back to the world's own. The biome look-up for every block is dear: measured in a new savanna world, 18
 * percent of all the world builder's time (three quarters of the surface step, which Minecraft does one chunk at a
 * time). With no biome mod bringing a recipe of its own, the only recipe in the chooser is TerraBlender's copy of
 * Minecraft's, and the chooser gives the same block as the world's own recipe would.
 *
 * <p>So when a world loads, the chooser is looked at: if every recipe in it is, written out, exactly the world's own
 * recipe, the chooser is switched off for that world's land ({@link IExtendedNoiseGeneratorSettings#setRuleCategory}
 * with nothing), and Minecraft asks for a biome only where a rule needs it, as it does without TerraBlender. The same
 * land, block for block. With Biomes O' Plenty or any mod that adds a surface recipe the recipes differ and nothing is
 * touched. Anything unexpected (another TerraBlender, a recipe that cannot be written out): nothing is touched.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public final class PlainSurface {
    private PlainSurface() {
    }

    /** After TerraBlender has set its chooser up (it does so before any level loads). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return;
        }
        try {
            NoiseGeneratorSettings settings = generator.generatorSettings().value();
            if (!(settings.surfaceRule() instanceof NamespacedSurfaceRuleSource chooser)
                    || !((Object) settings instanceof IExtendedNoiseGeneratorSettings extended)) {
                return; // no chooser on this world (or already switched off: several levels may share one recipe)
            }
            JsonElement own = written(chooser.base());
            if (own == null) {
                return;
            }
            for (var recipe : chooser.sources().entrySet()) {
                if (!own.equals(written(recipe.getValue()))) {
                    SavannaMod.LOGGER.info("[plain surface] {}: the surface recipe for '{}' biomes differs from the world's own,"
                            + " TerraBlender's chooser is kept", level.dimension().location(), recipe.getKey());
                    return;
                }
            }
            extended.setRuleCategory(null);
            SavannaMod.LOGGER.info("[plain surface] {}: no biome mod brings a surface recipe of its own, TerraBlender's chooser is"
                    + " skipped (the same land, made sooner)", level.dimension().location());
        } catch (RuntimeException | LinkageError unexpected) {
            SavannaMod.LOGGER.warn("[plain surface] {}: left as it is ({})", level.dimension().location(), unexpected.toString());
        }
    }

    /** A recipe written out as text-like data, to compare two recipes; null if it cannot be written. */
    private static JsonElement written(SurfaceRules.RuleSource recipe) {
        return SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, recipe).result().orElse(null);
    }
}
