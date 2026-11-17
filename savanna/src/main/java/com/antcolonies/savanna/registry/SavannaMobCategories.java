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

    private static MobCategory riverFish, riverHunter, riverBed;

    /** The river fish group (tilapia and catfish). */
    public static MobCategory riverFish() {
        if (riverFish == null) {
            riverFish = MobCategory.valueOf("ANTCOLONIES_RIVER_FISH");
        }
        return riverFish;
    }

    /**
     * The river hunters' group (the crocodile, 2026-11-15, the user's ask that crocodiles spawn by themselves): in the
     * river fish group they stood 1 pick in 19 against the tilapia and found the group's 60 places full, so hardly any
     * came. Their own group, cap 6 round a player (my pick), each spawned alone and at least
     * {@code CrocodileEntity.SPAWN_APART} blocks from another crocodile, so they are mostly solitary.
     */
    public static MobCategory riverHunter() {
        if (riverHunter == null) {
            riverHunter = MobCategory.valueOf("ANTCOLONIES_RIVER_HUNTER");
        }
        return riverHunter;
    }

    /**
     * The bed dwellers' group (the crab and the snail, 2026-11-16, BUGS 224: in the river fish group the tilapia, kept
     * near a player, filled the 60 places and took about 9 spawns in 10, so crabs and snails were hardly ever seen). Cap
     * 24 round a player (my pick), friendly, removed past 128 blocks like the fish.
     */
    public static MobCategory riverBed() {
        if (riverBed == null) {
            riverBed = MobCategory.valueOf("ANTCOLONIES_RIVER_BED");
        }
        return riverBed;
    }
}
