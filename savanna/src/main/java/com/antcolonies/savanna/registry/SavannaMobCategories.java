package com.antcolonies.savanna.registry;

import net.minecraft.world.entity.MobCategory;

/**
 * Spawning groups of our own (M9 river life, 2026-11-14). Minecraft limits each group of mobs round a player; its
 * water fish group (cod, salmon, tropical fish) allows 20, which left the savanna rivers with specks of fish. River
 * fish get their own group, added to Minecraft's list through NeoForge's enum extension
 * ({@code META-INF/enumextensions.json}): cap 60 round a player (my pick, to raise after a look in-game if the frame
 * rate allows), friendly, spawned every tick like water mobs, removed past 128 blocks.
 */
public final class SavannaMobCategories {
    private SavannaMobCategories() {}

    private static MobCategory riverFish;

    /** The river fish group (tilapia, the catfish later). */
    public static MobCategory riverFish() {
        if (riverFish == null) {
            riverFish = MobCategory.valueOf("ANTCOLONIES_RIVER_FISH");
        }
        return riverFish;
    }
}
