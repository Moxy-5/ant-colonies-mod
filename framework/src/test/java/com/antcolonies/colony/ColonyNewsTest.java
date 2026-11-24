package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

/**
 * Project Apex, colony parts step 4: the colony news ({@link ColonyNews}). Listeners hear only their own kind, in place
 * order whatever order they signed up in; a kind nobody listens to is not sent; two listeners at one place are refused.
 */
class ColonyNewsTest {
    @Test
    void listenersHearTheirOwnKindInPlaceOrder() {
        ColonyNews news = new ColonyNews();
        List<String> heard = new ArrayList<>();
        news.listen(ColonyNews.BLOCK_DUG, 20, "second", (level, dug) -> heard.add("second " + dug.where().getX()));
        news.listen(ColonyNews.BLOCK_DUG, 10, "first", (level, dug) -> heard.add("first " + dug.where().getX()));
        news.listen(ColonyNews.HEAP_GONE, 10, "heaps", (level, gone) -> heard.add("gone"));
        news.tell(null, ColonyNews.BLOCK_DUG, new ColonyNews.BlockDug(new BlockPos(7, 60, 0)));
        assertEquals(List.of("first 7", "second 7"), heard);
        assertEquals(List.of("first", "second"), news.listenersOf(ColonyNews.BLOCK_DUG));
    }

    @Test
    void newsNobodyListensToIsNotSent() {
        ColonyNews news = new ColonyNews();
        assertFalse(news.listens(ColonyNews.NEIGHBOUR_MET));
        news.tell(null, ColonyNews.NEIGHBOUR_MET, new ColonyNews.NeighbourMet(BlockPos.ZERO, UUID.randomUUID(), UUID.randomUUID()));
        assertTrue(news.listenersOf(ColonyNews.NEIGHBOUR_MET).isEmpty());
        ColonyNews.Kind<String> own = ColonyNews.Kind.of("a species mod's own");
        List<String> heard = new ArrayList<>();
        news.listen(own, 10, "raider", (level, told) -> heard.add(told));
        news.tell(null, own, "termites at the feeding spot");
        assertEquals(List.of("termites at the feeding spot"), heard);
    }

    @Test
    void twoListenersAtOnePlaceAreRefused() {
        ColonyNews news = new ColonyNews();
        news.listen(ColonyNews.HEAP_GONE, 10, "garden", (level, gone) -> { });
        assertThrows(IllegalStateException.class, () -> news.listen(ColonyNews.HEAP_GONE, 10, "mound", (level, gone) -> { }));
    }
}
