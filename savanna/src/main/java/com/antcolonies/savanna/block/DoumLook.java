package com.antcolonies.savanna.block;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.tree.PalmPieces;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * What one doum palm piece block draws (floodplain trees step 5): the recipe for its crown, bunch, seedling or rosette
 * ({@link PalmPieces}), kept by its {@link DoumBlockEntity} and read by the client's model. A palm grown by the mod
 * (step 6) sets it whole, so the block draws exactly the crown the tree's shape planned: the dice where the crown
 * starts, the middle of the stem's top (t, in the tree's own block grid, as the viewer has it) and the block's own
 * place in that grid (c). A piece placed by a player is {@linkplain #byPlace made from where it stands}.
 *
 * @param grown   set by a growing palm (false: placed by hand, drawn on its own block)
 * @param state   the dice where the crown starts (a seedling's or rosette's seed)
 * @param size    the crown's size (the viewer's: 1 grown, 1.06 old, 0.88 a young stem, 0.95 a fork's arm)
 * @param old     the hanging old fronds under the crown
 * @param dead    a dead crown: every frond brown and hanging, no spear, no fruit
 * @param bunches how many fruit bunches the crown has, -1 for the grown palm's 2 or 3
 * @param bunch   which of the crown's bunches a bunch block draws
 *
 * <p>Fronds lying on the ground ({@link DoumPiece.Piece#FRONDS}, step 6) use the same recipe: state the dice where the
 * frond starts, tx how far out from the clump's middle it lies (the viewer's R), old a frond bleached grey by years (else
 * brown), c the block's place in the tree's grid. The size is kept to the last digit (a double, not a float), so a
 * grown palm's crown is drawn from exactly the numbers the tree's shape used.
 */
public record DoumLook(boolean grown, int state, double tx, double ty, double tz, int cx, int cy, int cz, double size, boolean old,
                       boolean dead, int bunches, int bunch) {

    /** A piece placed by hand: its dice from where it stands, its crown or bunch centred on its own block. */
    public static DoumLook byPlace(BlockPos pos) {
        int seed = (int) (Mth.getSeed(pos) >>> 16);
        return new DoumLook(false, seed, 0, -1, 0, 0, 0, 0, 1, true, false, 0, 0);
    }

    /**
     * The boxes this piece draws, placed in its own block (0 to 1 across; parts reach far out of it). nuts: a bunch's
     * {@link com.antcolonies.savanna.block.HangingFruit} stage (a crown always draws ripe ones; it shows no bunch itself).
     */
    public List<PalmPieces.Bit> bits(DoumPiece.Piece piece, int nuts) {
        List<PalmPieces.Bit> all;
        int keep = -1;
        boolean shift = false;
        switch (piece) {
            case CROWN -> {
                all = PalmPieces.crown(state, tx, ty, tz, size, old, dead, grown ? bunches : 0, HangingFruit.RIPE);
                shift = grown;
            }
            case BUNCH -> {
                if (grown) {
                    all = PalmPieces.crown(state, tx, ty, tz, size, old, dead, bunches, nuts);
                    keep = bunch;
                    shift = true;
                } else {
                    all = PalmPieces.loneBunch(state, (state & 1023) / 1023.0 * Math.PI * 2, nuts);
                    keep = 0;
                }
            }
            case FRONDS -> {
                if (grown) {
                    all = PalmPieces.groundFrond(state, tx, old);
                    shift = true;
                } else { // one laid by hand: its foot on its own block
                    List<PalmPieces.Bit> f = PalmPieces.groundFrond(state, 0, false);
                    all = new ArrayList<>(f.size());
                    PalmPieces.Bit first = f.get(0);
                    for (PalmPieces.Bit b : f) {
                        all.add(new PalmPieces.Bit(b.x() - first.x(), b.y(), b.z() - first.z(), b.w(), b.h(), b.d(), b.rot(), b.skin(), b.r(),
                                b.g(), b.b(), b.leafy(), b.bunch()));
                    }
                }
            }
            case SEEDLING -> all = PalmPieces.seedling(state);
            case ROSETTE -> all = PalmPieces.rosette(state, grown ? size : 0.8);
            default -> throw new IllegalStateException(piece.name());
        }
        double ox = (shift ? cx : 0) - 0.5, oy = (shift ? cy : 0) - 0.5, oz = (shift ? cz : 0) - 0.5;
        // The seedling and the rosette grow out of the ground, as the viewer draws them: their cap sits down in the soil
        // block under them. Boxes wholly in that block would never be seen (only from a cave), so they are left out.
        boolean onGround = piece == DoumPiece.Piece.SEEDLING || piece == DoumPiece.Piece.ROSETTE;
        List<PalmPieces.Bit> out = new ArrayList<>(all.size());
        for (PalmPieces.Bit b : all) {
            if (b.bunch() != keep || onGround && b.y() - oy + b.h() / 32.0 <= 0) {
                continue;
            }
            out.add(new PalmPieces.Bit(b.x() - ox, b.y() - oy, b.z() - oz, b.w(), b.h(), b.d(), b.rot(), b.skin(), b.r(), b.g(), b.b(),
                    b.leafy(), b.bunch()));
        }
        return out;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", state);
        tag.putDouble("tx", tx);
        tag.putDouble("ty", ty);
        tag.putDouble("tz", tz);
        tag.putInt("cx", cx);
        tag.putInt("cy", cy);
        tag.putInt("cz", cz);
        tag.putDouble("size", size);
        tag.putBoolean("old", old);
        tag.putBoolean("dead", dead);
        tag.putInt("bunches", bunches);
        tag.putInt("bunch", bunch);
        return tag;
    }

    static DoumLook load(CompoundTag tag) {
        return new DoumLook(true, tag.getInt("state"), tag.getDouble("tx"), tag.getDouble("ty"), tag.getDouble("tz"), tag.getInt("cx"),
                tag.getInt("cy"), tag.getInt("cz"), tag.getDouble("size"), tag.getBoolean("old"), tag.getBoolean("dead"), tag.getInt("bunches"),
                tag.getInt("bunch"));
    }
}
