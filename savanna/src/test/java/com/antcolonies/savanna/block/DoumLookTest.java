package com.antcolonies.savanna.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.antcolonies.savanna.tree.PalmPieces;

import net.minecraft.core.BlockPos;

/**
 * A doum piece placed by hand stands on its own block (floodplain trees step 5): the crown's cap on the block's floor in
 * its middle, a bunch hanging from the block above (where its crown would be), the seedling and rosette on the ground.
 */
class DoumLookTest {
    @Test
    void handPlacedPiecesSitOnTheirBlock() {
        for (BlockPos pos : List.of(BlockPos.ZERO, new BlockPos(17, 64, -230), new BlockPos(-5000, 80, 4321))) {
            DoumLook look = DoumLook.byPlace(pos);
            List<PalmPieces.Bit> crown = look.bits(DoumPiece.Piece.CROWN, HangingFruit.RIPE);
            PalmPieces.Bit cap = crown.stream().filter(b -> b.skin() == PalmPieces.Skin.CAP).findFirst().orElseThrow();
            assertEquals(0.5, cap.x(), 1e-9, "cap across");
            assertEquals(0.5, cap.z(), 1e-9, "cap along");
            assertEquals(0, cap.y() - cap.h() / 32.0, 0.13, "the cap's foot on the block's floor (" + (cap.y() - cap.h() / 32.0) + ")");
            assertTrue(crown.size() > 300, "a crown of " + crown.size() + " boxes");
            for (PalmPieces.Bit b : crown) {
                assertTrue(Math.abs(b.x() - 0.5) < 8 && Math.abs(b.z() - 0.5) < 8 && b.y() > -6 && b.y() < 6, "a crown box far out: " + b);
            }
            int ripeBoxes = look.bits(DoumPiece.Piece.BUNCH, HangingFruit.RIPE).size();
            for (int stage = HangingFruit.BARE; stage <= HangingFruit.RIPE; stage++) {
                List<PalmPieces.Bit> bunch = look.bits(DoumPiece.Piece.BUNCH, stage);
                assertFalse(bunch.isEmpty(), "an empty bunch (a bare one still has its stalks)");
                assertEquals(stage == HangingFruit.BARE, bunch.size() < ripeBoxes, "only a bare stalk has fewer boxes, stage " + stage);
                double top = bunch.stream().mapToDouble(b -> b.y() + b.h() / 32.0).max().orElseThrow();
                double low = bunch.stream().mapToDouble(b -> b.y() - b.h() / 32.0).min().orElseThrow();
                assertTrue(top > 0.8 && top < 1.7, "the bunch's stalk comes down from the block above (its crown's place): " + top);
                assertTrue(low > -2.5, "the bunch hangs no more than two and a half blocks: " + low);
            }
            for (DoumPiece.Piece piece : new DoumPiece.Piece[] {DoumPiece.Piece.SEEDLING, DoumPiece.Piece.ROSETTE}) {
                List<PalmPieces.Bit> bits = look.bits(piece, HangingFruit.RIPE);
                assertFalse(bits.isEmpty(), piece + " empty");
                double low = bits.stream().mapToDouble(b -> b.y() - b.h() / 32.0).min().orElseThrow();
                double lowTop = bits.stream().mapToDouble(b -> b.y() + b.h() / 32.0).min().orElseThrow();
                assertTrue(low < 0.3, piece + " comes out of the ground: its lowest box starts at " + low);
                assertTrue(lowTop > 0, piece + ": a box wholly under the ground, top at " + lowTop);
            }
        }
    }
}
