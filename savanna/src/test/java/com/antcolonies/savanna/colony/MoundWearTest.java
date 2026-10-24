package com.antcolonies.savanna.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.antcolonies.savanna.colony.MoundPlan.Part;
import com.antcolonies.savanna.colony.MoundWear.Column;
import com.antcolonies.savanna.species.SavannaNumbers.MoundNumbers;

import net.minecraft.core.BlockPos;

/**
 * A dead mound's wear (M8 step 1a part 2): the spires come down to the body first, then the body slumps into a low
 * rounded hump that stays; never back up; pieces on top crumble first.
 */
class MoundWearTest {
    private static final MoundNumbers NUMBERS = new MoundNumbers(32, 2.0F, 0.5F, 3, 13);
    private static final long[] SEEDS = {1, 42, -987654321L};

    private static List<BlockPos> built(int h, long seed) {
        List<BlockPos> built = new ArrayList<>();
        for (Map.Entry<BlockPos, Part> entry : MoundPlan.stage(h, NUMBERS, seed).parts().entrySet()) {
            if (entry.getValue() != Part.OPEN) {
                built.add(entry.getKey());
            }
        }
        return built;
    }

    @Test
    void theSpiresGoFirstThenTheBodySlumpsIntoALowHump() {
        for (long seed : SEEDS) {
            List<Column> columns = MoundWear.plan(built(NUMBERS.maxHeight(), seed));
            int top = columns.stream().mapToInt(Column::top).max().orElseThrow();
            int body = columns.stream().mapToInt(Column::body).max().orElseThrow();
            int footprint = (int) columns.stream().filter(c -> !c.isApron()).count();
            int hump = columns.stream().mapToInt(Column::hump).max().orElseThrow();
            String where = "seed " + seed + ": ";
            assertTrue(top >= 28, where + "the full mound stands only " + top);
            assertTrue(footprint >= 100, where + "the mound covers only " + footprint + " columns");
            // The spires (most of the height of a cathedral mound) are not body: the body is well under them.
            assertTrue(body <= top * 0.7, where + "the body " + body + " keeps most of the spires' " + top);
            assertTrue(body >= 6, where + "the body " + body + " is gone with the spires");
            assertEquals(MoundWear.HUMP_MOST, hump, where + "the hump of a full mound");
            java.util.Set<Long> mound = new java.util.HashSet<>();
            for (Column column : columns) {
                if (column.isApron()) {
                    assertEquals(1, column.hump(), where + "the washed soil round the foot is one layer: " + column);
                    continue;
                }
                mound.add(((long) column.dx() << 32) | (column.dz() & 0xFFFFFFFFL));
                assertTrue(column.hump() >= 1 && column.hump() <= column.body() && column.body() <= column.top(),
                        where + "heights out of order: " + column);
            }
            // The ring of washed soil lies round the foot, never on the mound, at most APRON blocks out.
            long apron = columns.stream().filter(Column::isApron).count();
            assertTrue(apron >= 30, where + "only " + apron + " places of washed soil round the foot");
            for (Column column : columns) {
                if (column.isApron()) {
                    assertTrue(!mound.contains(((long) column.dx() << 32) | (column.dz() & 0xFFFFFFFFL)), where + "washed soil on the mound");
                    boolean near = false;
                    for (int i = -MoundWear.APRON; i <= MoundWear.APRON && !near; i++) {
                        for (int j = -MoundWear.APRON; j <= MoundWear.APRON && !near; j++) {
                            near = mound.contains(((long) (column.dx() + i) << 32) | ((column.dz() + j) & 0xFFFFFFFFL));
                        }
                    }
                    assertTrue(near, where + "washed soil far from the foot: " + column);
                }
            }
            // The hump is highest in the middle, lowest at the edge.
            Column middle = columns.stream().filter(c -> !c.isApron()).min((a, b) -> Integer.compare(a.dx() * a.dx() + a.dz() * a.dz(), b.dx() * b.dx() + b.dz() * b.dz())).orElseThrow();
            Column edge = columns.stream().max((a, b) -> Integer.compare(a.dx() * a.dx() + a.dz() * a.dz(), b.dx() * b.dx() + b.dz() * b.dz())).orElseThrow();
            assertEquals(MoundWear.HUMP_MOST, middle.hump(), where + "the middle of the hump");
            assertEquals(1, edge.hump(), where + "the edge of the hump");
        }
    }

    @Test
    void aYoungMoundLeavesALowHump() {
        List<Column> columns = MoundWear.plan(built(4, 7));
        int top = columns.stream().mapToInt(Column::top).max().orElseThrow();
        int hump = columns.stream().mapToInt(Column::hump).max().orElseThrow();
        assertTrue(hump >= 1 && hump <= Math.max(1, top / 3), "a young mound " + top + " tall leaves a hump " + hump);
    }

    @Test
    void eachColumnOnlyGoesDownAndKeepsItsDays() {
        Column column = new Column(0, 0, 30, 10, 4);
        for (double jitter : new double[] {0.85, 1.0, 1.15}) {
            int last = Integer.MAX_VALUE;
            for (double day = 0; day <= 50; day += 0.1) {
                int now = MoundWear.heightAt(column, day, jitter);
                assertTrue(now <= last, "back up on day " + day + ": " + now + " after " + last);
                assertTrue(now >= 4 && now <= 30, "out of range on day " + day + ": " + now);
                last = now;
            }
        }
        assertEquals(30, MoundWear.heightAt(column, 0, 1.0), "stands whole the day it dies");
        assertEquals(30, MoundWear.heightAt(column, -3, 1.0), "the clock set back before its death");
        assertTrue(MoundWear.heightAt(column, 1, 1.0) < 30, "the spire starts to wear on the first day");
        assertEquals(10, MoundWear.heightAt(column, MoundWear.SPIRES_DAYS, 1.0), "the spire is down to the body");
        assertTrue(MoundWear.heightAt(column, 15, 1.0) < 10, "the body slumps");
        assertTrue(MoundWear.heightAt(column, 15, 1.0) > 4, "the body is not a hump yet halfway through");
        assertEquals(4, MoundWear.heightAt(column, MoundWear.HUMP_DAYS, 1.0), "the hump by day 30");
        for (double jitter : new double[] {0.85, 1.15}) {
            assertEquals(4, MoundWear.heightAt(column, 40, jitter), "the hump by day 40 whatever the column's clock");
            assertEquals(30, MoundWear.heightAt(column, 0, jitter));
        }
        assertEquals(4, MoundWear.heightAt(column, 10000, 1.0), "the hump stays for good");
        // A low column (a ramp's foot) that is already hump stays as it is.
        Column low = new Column(12, 0, 1, 1, 1);
        assertEquals(1, MoundWear.heightAt(low, 0, 1.0));
        assertEquals(1, MoundWear.heightAt(low, 100, 1.0));
    }

    @Test
    void piecesHighUpCrumbleFirstWithinTheFirstDays() {
        for (double jitter : new double[] {0.85, 1.0, 1.15}) {
            double top = MoundWear.pieceDay(31, 32, jitter), low = MoundWear.pieceDay(2, 32, jitter);
            assertTrue(top < low, "a spire tip should go before a ramp at the foot: " + top + ", " + low);
            assertTrue(top >= MoundWear.PIECES_FROM && low <= MoundWear.PIECES_TO, "outside the first days: " + top + ", " + low);
        }
        for (long place = 0; place < 1000; place++) {
            double jitter = MoundWear.jitter(place);
            assertTrue(jitter >= 0.85 && jitter <= 1.15, "jitter " + jitter);
            assertEquals(jitter, MoundWear.jitter(place), "the same place, the same jitter");
        }
    }

    @Test
    void theOpeningTakesOffThinSpikesAndKeepsBroadDomes() {
        int[][] heights = new int[15][15];
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 15; z++) {
                heights[x][z] = 8;
            }
        }
        heights[7][7] = 25; // a thin spire
        int[][] opened = MoundWear.opening(heights, MoundWear.SPIRE_RADIUS);
        assertEquals(8, opened[7][7], "the spire comes down to the body");
        assertEquals(8, opened[5][5], "the body under it stays");
    }
}
