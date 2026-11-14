package com.antcolonies.savanna.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The sycamore fig's bark, one look per block (floodplain trees step 3, 2026-11-13, bark look A, the user's pick; the
 * textures from {@code tools/floodplain_tree_art.py}). A living fig is mottled: the cream base bark (drawn twice, mixed at
 * random so the trunk does not repeat), big red-brown patches where the old bark flaked off, and pale patches; a young
 * fig's bark is smooth, pale and greener with small corky dashes. A dead fig's bark goes dull grey and flakes off, then
 * the bare grey wood shows. Every fig wood block (log, wood, branch, root step) carries it, so a dead fig is the same
 * blocks with its dead bark (my pick: fewer blocks than a dead set of its own; the termites' later rules can ask
 * {@link #isDead()}). Broken, a block drops itself plain (mottled).
 */
public enum FigBark implements StringRepresentable {
    MOTTLED("mottled"), PATCH("patch"), PALE("pale"), YOUNG("young"), DEAD("dead"), BARE("bare");

    /** The bark on every fig wood block. */
    public static final EnumProperty<FigBark> BARK = EnumProperty.create("bark", FigBark.class);

    private final String name;

    FigBark(String name) {
        this.name = name;
    }

    /** Whether this is a dead fig's bark: dull and flaking, or the bare wood under it. */
    public boolean isDead() {
        return this == DEAD || this == BARE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
