package com.antcolonies.savanna.registry;

import net.minecraft.world.entity.MobCategory;

/**
 * Spawning groups of our own (M9 river life, 2026-11-14). Minecraft limits each group of mobs round a player; its
 * water fish group (cod, salmon, tropical fish) allows 20, which left the savanna rivers with specks of fish. River
 * fish get their own group, added to Minecraft's list through NeoForge's enum extension
 * ({@code META-INF/enumextensions.json}): cap 45 round a player (60 until the user lowered it, 2026-11-20, to make room
 * for the barbs), friendly, spawned every tick like water mobs, removed past 128 blocks.
 */
public final class SavannaMobCategories {
    private SavannaMobCategories() {}

    private static MobCategory riverFish, riverHunter, riverBed, riverBank, riverSchool, riverGrazer, flyCloud, bullfrog, hippoPod, riverOtter;

    /**
     * The hippos' group (2026-11-21): its own, with no spawn list since the same day (the user: pods placed with new land
     * and kept for good, {@code worldgen/HippoPodFeature}; BUGS 271), so the world's spawner never adds a hippo; kept so
     * hippos count apart from every other kind.
     */
    public static MobCategory hippoPod() {
        if (hippoPod == null) {
            hippoPod = MobCategory.valueOf("ANTCOLONIES_HIPPO_POD");
        }
        return hippoPod;
    }

    /**
     * The otters' group (2026-11-22, the spawning check list): its own, so neither the fish nor the bank's monitors fill
     * its places, cap 2 round a player (one otter alone or a pair, often a mother and her pup: real clawless otters live
     * mostly alone; was cap 6 in families of 2 to 4 until the user found too many, 2026-11-22, "they would destroy the
     * ecosystem"), friendly, removed past 128 blocks (my picks).
     */
    public static MobCategory riverOtter() {
        if (riverOtter == null) {
            riverOtter = MobCategory.valueOf("ANTCOLONIES_RIVER_OTTER");
        }
        return riverOtter;
    }

    /**
     * The bullfrogs' group (prey plan step 3, 2026-11-20, the spawning check list): its own, cap 4 round a player
     * (2026-11-20, the user found too many: was 12, the plan's), friendly, removed past 128 blocks.
     */
    public static MobCategory bullfrog() {
        if (bullfrog == null) {
            bullfrog = MobCategory.valueOf("ANTCOLONIES_BULLFROG");
        }
        return bullfrog;
    }

    /**
     * The fly clouds' group (fly clouds step 2, 2026-11-20, the spawning check list): its own, so neither the fish nor
     * anything else fills its places. Cap 6 round a player (my pick), friendly, removed past 128 blocks.
     */
    public static MobCategory flyCloud() {
        if (flyCloud == null) {
            flyCloud = MobCategory.valueOf("ANTCOLONIES_FLY_CLOUD");
        }
        return flyCloud;
    }

    /**
     * The small schooling fish's group (the barb, 2026-11-20, the spawning check list): a common fish kept near the
     * player, so never in a group with a rarer kind. Cap 40 round a player (my pick), friendly, removed past 128 blocks.
     */
    public static MobCategory riverSchool() {
        if (riverSchool == null) {
            riverSchool = MobCategory.valueOf("ANTCOLONIES_RIVER_SCHOOL");
        }
        return riverSchool;
    }

    /**
     * The big plant-eating fish's group (the distichodus, 2026-11-23, the spawning check list): a fish in groups of 2 to
     * 4, kept near the player, never sharing a cap with the common tilapia or barbs. Cap 8 round a player (the plan's),
     * friendly, removed past 128 blocks.
     */
    public static MobCategory riverGrazer() {
        if (riverGrazer == null) {
            riverGrazer = MobCategory.valueOf("ANTCOLONIES_RIVER_GRAZER");
        }
        return riverGrazer;
    }

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
     * came. Their own group, cap 5 round a player (6 until the user asked for slightly fewer, 2026-11-17), each spawned alone and at least
     * {@code CrocodileEntity.SPAWN_APART} blocks from another crocodile, so they are mostly solitary.
     */
    public static MobCategory riverHunter() {
        if (riverHunter == null) {
            riverHunter = MobCategory.valueOf("ANTCOLONIES_RIVER_HUNTER");
        }
        return riverHunter;
    }

    private static MobCategory tadpole;

    /**
     * The tadpoles' group (the river tie-up, 2026-11-23, the spawning check list): its own, so its schools (6 to 10)
     * never fill the bullfrogs' 4 places or the barbs' 40. Cap 16 round a player (about two schools in the shallows;
     * my pick), friendly, removed past 128 blocks.
     */
    public static MobCategory tadpole() {
        if (tadpole == null) {
            tadpole = MobCategory.valueOf("ANTCOLONIES_TADPOLE");
        }
        return tadpole;
    }

    private static MobCategory bullShark;

    /**
     * The bull shark's group (2026-11-22, the spawning check list): its own, so no common river fish or crocodile fills
     * its places. Cap 2 round a player (the river zones keep one at most in a near-inland stretch), each spawned alone
     * and at least {@code BullSharkEntity.SPAWN_APART} blocks from another, removed past 128 blocks.
     */
    public static MobCategory bullShark() {
        if (bullShark == null) {
            bullShark = MobCategory.valueOf("ANTCOLONIES_BULL_SHARK");
        }
        return bullShark;
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

    /**
     * The bank dwellers' group (the Nile monitor, 2026-11-17, the user's "between uncommon and rare, nearer uncommon"):
     * a group of its own so neither the fish nor the crocodiles fill its places, cap 2 round a player (4 until the user asked for rarer, 2026-11-17), each
     * spawned alone and at least {@code MonitorEntity.SPAWN_APART} blocks from another monitor, removed past 128 blocks.
     */
    public static MobCategory riverBank() {
        if (riverBank == null) {
            riverBank = MobCategory.valueOf("ANTCOLONIES_RIVER_BANK");
        }
        return riverBank;
    }
}
