package com.antcolonies.season;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The one shared "what season is it" source (CLAUDE.md, "the season comes from one shared place", the user,
 * 2026-10-23). Nothing reads the season, or guesses it from the date, the rain or the heat, on its own: everything asks
 * here, the way colonies read the day and night clock. Until the seasons come (Serene Seasons, the last steps of M9.5)
 * it answers with a fixed default, {@link Season#RAINS} (so nothing that waits for the rains waits), and nothing in the
 * game changes. First asked by the animal life kit (breeding seasons, {@code docs/plans/animal-life-cycle.md}).
 */
public final class Seasons {
    /** The answer while there are no seasons: the rains, the savanna's breeding time (my pick: so nothing waits). */
    public static final Season DEFAULT = Season.RAINS;

    /** A test's own season, or null for the default (tests only). */
    @Nullable
    private static volatile Season forTests;

    private Seasons() {
    }

    /** The two seasons of the tropics, the savanna's year (later: the temperate four, when the seasons mod comes). */
    public enum Season {
        /** The wet season: rain, green grass, most breeding. */
        RAINS,
        /** The dry season. */
        DRY;

        /** Its name in a data file ({@code "rains"}, {@code "dry"}). */
        public String fileName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The season named in a data file, or null if no season has that name. */
        @Nullable
        public static Season fromFile(String name) {
            for (Season season : values()) {
                if (season.fileName().equals(name.toLowerCase(Locale.ROOT))) {
                    return season;
                }
            }
            return null;
        }
    }

    /**
     * The season at this place now. The place is asked for already, because the tropics and the temperate lands will
     * have different years once the seasons come; today every place answers {@link #DEFAULT}.
     */
    public static Season now(Level level, BlockPos pos) {
        Season test = forTests;
        return test != null ? test : DEFAULT;
    }

    /** A test's own season (null puts the default back). Tests only. */
    public static void setForTests(@Nullable Season season) {
        forTests = season;
    }
}
