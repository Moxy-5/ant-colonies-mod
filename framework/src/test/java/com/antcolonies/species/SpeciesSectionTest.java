package com.antcolonies.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;

/** Apex part 3, T1: a species mod's own section of the species files is found by its path, once. */
class SpeciesSectionTest {
    private static final SpeciesSection<Integer> DEEP = SpeciesSection.signUp("section_test.inner.size", Codec.INT);

    @Test
    void aSectionIsFoundByItsPathAndReadWithItsCodec() {
        assertEquals(7, DEEP.read(JsonParser.parseString("{\"section_test\": {\"inner\": {\"size\": 7}}}")).getOrThrow().orElseThrow());
        assertTrue(DEEP.read(JsonParser.parseString("{\"section_test\": {}}")).getOrThrow().isEmpty(), "a file without it has none");
        assertTrue(DEEP.read(JsonParser.parseString("{\"section_test\": 3}")).getOrThrow().isEmpty(), "a number is not an object to look in");
        assertTrue(DEEP.read(JsonParser.parseString("{\"section_test\": {\"inner\": {\"size\": \"big\"}}}")).error().isPresent(),
                "there but broken is an error");
        assertTrue(SpeciesSection.all().contains(DEEP));
    }

    @Test
    void twoSectionsAtOnePathStopTheGame() {
        assertThrows(IllegalStateException.class, () -> SpeciesSection.signUp("section_test.inner.size", Codec.INT));
    }
}
