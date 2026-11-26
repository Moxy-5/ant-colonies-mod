package com.antcolonies.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.Test;

/**
 * Review R1-4: a number of the species file that is divided by (an interval, a half-life) must be 1 or more; a 0 is
 * refused when the file is read, with the field's name, instead of crashing the game a second later.
 */
class PositiveNumbersTest {
    private static final String FOUNDING = "{\"eggs\": 6, \"egg_interval_ticks\": 400, \"feed_interval_ticks\": %d}";

    @Test
    void anIntervalOfNoneIsRefusedWithItsName() {
        var result = SpeciesProfile.Founding.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(String.format(FOUNDING, 0)));
        assertTrue(result.error().isPresent(), "a feed interval of 0 was read");
        assertTrue(result.error().get().message().contains("feed_interval_ticks") || result.error().get().message().contains("0"),
                result.error().get().message());
    }

    @Test
    void aRealIntervalIsRead() {
        var result = SpeciesProfile.Founding.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(String.format(FOUNDING, 200)));
        assertEquals(200, result.result().orElseThrow().feedIntervalTicks());
    }
}
