package com.antcolonies.savanna.tree;

/**
 * Where a giant savanna tree is in its life (M7.5). Acacias (umbrella and camel thorn): living, a dead snag, mud-climbed,
 * limbs shed, fallen. Baobabs: living, slumping, a fibre heap, the heap dried. Names match the tree viewer's.
 */
public enum TreeStage {
    LIVING("living"), SNAG("snag"), MUD("mud"), SHED("shed"), FALLEN("fallen"), SLUMP("slump"), HEAP("heap"), HEAP_DRY("heapdry");

    public final String id;

    TreeStage(String id) {
        this.id = id;
    }

    public static final TreeStage[] ACACIA = {LIVING, SNAG, MUD, SHED, FALLEN};
    public static final TreeStage[] BAOBAB = {LIVING, SLUMP, HEAP, HEAP_DRY};
}
