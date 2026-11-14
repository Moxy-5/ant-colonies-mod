package com.antcolonies.savanna.block;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** A doum palm block drawn from sculpted pieces (floodplain trees step 5): which piece it is. */
public interface DoumPiece {
    Piece piece();

    /** The kinds of piece: a crown of fronds, a bunch of nuts, the seedling, the stemless rosette. */
    enum Piece implements StringRepresentable {
        CROWN, BUNCH, SEEDLING, ROSETTE;

        public static final Codec<Piece> CODEC = StringRepresentable.fromEnum(Piece::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
