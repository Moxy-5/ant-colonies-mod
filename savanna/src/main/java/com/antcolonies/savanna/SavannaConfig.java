package com.antcolonies.savanna;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The savanna mod's settings file ({@code config/antcolonies-common.toml}), for players and modpacks.
 *
 * <p>Big savannas (2026-11-06, {@code docs/DESIGN.md} "Big savannas in normal worlds with TerraBlender"): a new normal
 * world puts the player in a savanna, on by default; a modpack that sets its own spawn turns it off here.
 */
public final class SavannaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SPAWN_IN_SAVANNA;
    public static final ModConfigSpec.IntValue SPAWN_SEARCH_RADIUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("world");
        SPAWN_IN_SAVANNA = builder
                .comment("A new world (normal, large biomes or amplified) puts its spawn well inside the nearest savanna.",
                        "Single-biome and flat worlds are left alone. Turn off for modpacks that choose their own spawn.")
                .define("spawn_in_savanna", true);
        SPAWN_SEARCH_RADIUS = builder
                .comment("How far (blocks) from the normal spawn to look for a savanna. None found: the normal spawn is kept.")
                .defineInRange("spawn_search_radius", 6400, 256, 32000);
        builder.pop();
        SPEC = builder.build();
    }

    private SavannaConfig() {
    }
}
