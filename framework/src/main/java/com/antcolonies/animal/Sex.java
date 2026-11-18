package com.antcolonies.animal;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/** An animal's sex (the animal life kit): male, female, or none for animals that are both (as snails). */
public enum Sex {
    MALE, FEMALE, NONE;

    /** Its name in a save or a summon ({@code "male"}, {@code "female"}, {@code "none"}). */
    public String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The sex of that name, or null. */
    @Nullable
    public static Sex fromFile(String name) {
        for (Sex sex : values()) {
            if (sex.fileName().equals(name.toLowerCase(Locale.ROOT))) {
                return sex;
            }
        }
        return null;
    }

    /** Whether two animals of these sexes can mate: male with female, or two that are both. */
    public boolean matesWith(Sex other) {
        return this == NONE ? other == NONE : other != NONE && other != this;
    }
}
