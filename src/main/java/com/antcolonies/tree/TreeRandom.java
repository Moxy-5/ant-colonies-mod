package com.antcolonies.tree;

/**
 * The dice of the tree shape maker: the same small random number maker (mulberry32) and block hash as the tree viewer
 * (tools/tree_viewer.template.html), so a seed gives the tree the user saw there. Every number a tree's skeleton needs
 * is drawn in the same order whatever its age, so one seed is one tree at every age.
 */
final class TreeRandom {
    private int state;

    TreeRandom(long seed) {
        state = (int) seed;
    }

    /** The next number from 0 (included) to 1 (not included). */
    double next() {
        state += 0x6D2B79F5;
        int t = (state ^ (state >>> 15)) * (1 | state);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** A number from 0 to 1 fixed for a block and a purpose (s), the same every time. */
    static double hash(int x, int y, int z, int s) {
        int h = x * 374761393 + y * 668265263 + z * 1440670441 + s * 1103515245;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }
}
