package com.antcolonies.species;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

/**
 * One section of the species files read by the mod whose parts use it (Project Apex part 3, T1,
 * {@code docs/plans/apex-termites.md}): the termite mound's numbers by the savanna mod, the harvester's door by the
 * desert mod, and so on. The framework's {@link SpeciesProfile} reads only the shared numbers; a species mod signs up
 * its own sections at start ({@link #signUp}) and asks a profile for them ({@link SpeciesProfile#section}). The species
 * files keep their layout: a section is found by its path in the file ({@code "nest.galleries"} is the
 * {@code galleries} object inside {@code nest}), so a new species' numbers need no edit in the framework.
 *
 * <p>Several readers may read the same object (each takes only the names it knows): the framework's nest layout reads
 * the garden's room counts, the savanna mod the whole garden.
 */
public final class SpeciesSection<T> {
    private static final Map<String, SpeciesSection<?>> ALL = new LinkedHashMap<>();

    private final String path;
    private final List<String> keys;
    private final Codec<T> codec;

    private SpeciesSection(String path, Codec<T> codec) {
        this.path = path;
        this.keys = List.of(path.split("\\."));
        this.codec = codec;
    }

    /**
     * Signs up a section: the object at {@code path} in every species file (names joined by dots) is read with
     * {@code codec} when the files load. Two sections at one path stop the game with a message.
     */
    public static synchronized <T> SpeciesSection<T> signUp(String path, Codec<T> codec) {
        if (ALL.containsKey(path)) {
            throw new IllegalStateException("two species file sections signed up at " + path);
        }
        SpeciesSection<T> section = new SpeciesSection<>(path, codec);
        ALL.put(path, section);
        return section;
    }

    /** Every section signed up, in the order signed up. */
    public static synchronized Collection<SpeciesSection<?>> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(ALL.values()));
    }

    /** Where it is in a species file ({@code "nest.galleries"}). */
    public String path() {
        return path;
    }

    /** The object at this path in a species file, if the file has one. */
    public Optional<JsonElement> find(JsonElement file) {
        JsonElement at = file;
        for (String key : keys) {
            if (!(at instanceof JsonObject object) || !object.has(key)) {
                return Optional.empty();
            }
            at = object.get(key);
        }
        return Optional.of(at);
    }

    /** Reads this section of a species file: empty if the file has none, an error if it is there but broken. */
    DataResult<Optional<T>> read(JsonElement file) {
        Optional<JsonElement> found = find(file);
        if (found.isEmpty()) {
            return DataResult.success(Optional.empty());
        }
        return codec.parse(JsonOps.INSTANCE, found.get()).map(Optional::of).mapError(error -> path + ": " + error);
    }

    @Override
    public String toString() {
        return path;
    }
}
