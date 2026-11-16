package com.antcolonies.entity.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/**
 * The animal life cycle, step 1 (2026-11-16): the growing-up helper as a quick rule test with no world. A new helper is
 * grown; a young animal's size climbs evenly from its birth size to 1; it asks for a refresh every 200 ticks and on the
 * tick it grows up, never after; its age saves and loads under the animal's own name.
 */
class GrowingUpTest {
    private static final GrowingUp.Plan THREE_DAYS = new GrowingUp.Plan(3 * 24000, 0.5F);

    @Test
    void aNewHelperIsGrown() {
        GrowingUp life = new GrowingUp(THREE_DAYS);
        assertFalse(life.isYoung());
        assertEquals(1.0F, life.grown());
        assertEquals(1.0F, life.size());
        assertFalse(life.tick(), "a grown animal never asks for a refresh");
    }

    @Test
    void theSizeClimbsEvenlyFromBirth() {
        GrowingUp life = new GrowingUp(THREE_DAYS);
        life.setAge(0);
        assertTrue(life.isYoung());
        assertEquals(0.5F, life.size(), 1e-6);
        life.setAge(36000);
        assertEquals(0.5F, life.grown(), 1e-6);
        assertEquals(0.75F, life.size(), 1e-6);
        life.setTicksToGrow(0);
        assertFalse(life.isYoung());
        assertEquals(1.0F, life.size());
    }

    @Test
    void itAsksForARefreshEvery200TicksAndOnGrowingUp() {
        GrowingUp life = new GrowingUp(new GrowingUp.Plan(450, 0.5F));
        life.setAge(0);
        int asks = 0;
        int lastAsk = -1;
        for (int i = 0; i < 1000; i++) {
            if (life.tick()) {
                asks++;
                lastAsk = life.age();
            }
        }
        assertEquals(3, asks, "at 200, 400 and on growing up at 450");
        assertEquals(450, lastAsk);
        assertFalse(life.isYoung());
    }

    @Test
    void theAgeIsKeptBetweenBirthAndGrown() {
        GrowingUp life = new GrowingUp(THREE_DAYS);
        life.setAge(-5);
        assertEquals(0, life.age());
        life.setAge(10 * 24000);
        assertEquals(3 * 24000, life.age());
        life.setTicksToGrow(40);
        assertEquals(3 * 24000 - 40, life.age());
    }

    @Test
    void theAgeSavesAndLoadsUnderTheAnimalsOwnName() {
        GrowingUp life = new GrowingUp(THREE_DAYS);
        life.setAge(1234);
        CompoundTag tag = new CompoundTag();
        life.save(tag, "TilapiaAge");
        assertEquals(1234, tag.getInt("TilapiaAge"));
        GrowingUp back = new GrowingUp(THREE_DAYS);
        assertTrue(back.load(tag, "TilapiaAge"));
        assertEquals(1234, back.age());
        GrowingUp none = new GrowingUp(THREE_DAYS);
        assertFalse(none.load(new CompoundTag(), "TilapiaAge"));
        assertFalse(none.isYoung(), "nothing saved: left as it was");
    }

    @Test
    void aBadPlanIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new GrowingUp.Plan(0, 0.5F));
        assertThrows(IllegalArgumentException.class, () -> new GrowingUp.Plan(100, 0F));
        assertThrows(IllegalArgumentException.class, () -> new GrowingUp.Plan(100, 1.5F));
    }
}
