package com.antcolonies.savanna.tree;

import java.util.ArrayDeque;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

/**
 * The shape of one tree: which {@link TreePart} is in each block, around its foot at (0, 0, 0) (y = 0 is the first block
 * above the ground, y = -1 the ground itself). Holes are carved spaces nothing may fill (a hollow trunk). Some blocks are
 * marked as ones the tree drops as it grows (a young tree's low side branches, self-pruning), so a growing tree may lose
 * them.
 */
public final class TreeVoxels {
    private final Long2ObjectOpenHashMap<TreePart> parts = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet holes = new LongOpenHashSet();
    private final LongOpenHashSet dropped = new LongOpenHashSet();
    private final LongOpenHashSet overBark = new LongOpenHashSet();
    /** A fallen tree: its lying logs (the trunk first, then its limb stubs), each {start x, z, end x, z}, and its stump's blocks. */
    private java.util.List<double[]> lying;
    private LongOpenHashSet stump;
    /** Pieces lying loose on the ground round a dead tree (broken limbs, fallen branches): they lie on the land under them. */
    private final LongOpenHashSet loose = new LongOpenHashSet();

    public interface Visitor {
        void visit(int x, int y, int z, TreePart part);
    }

    static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    /** Puts a part in a block, unless a part with a higher priority is there or the block is carved out. */
    public void put(int x, int y, int z, TreePart part) {
        if (y < -1) {
            return;
        }
        long k = key(x, y, z);
        if (holes.contains(k)) {
            return;
        }
        TreePart old = parts.get(k);
        if (old == null || part.priority >= old.priority) {
            parts.put(k, part);
            if (dropping) {
                if (old == null) {
                    dropped.add(k);
                }
            } else {
                dropped.remove(k);
            }
        }
    }

    /** While true, every block put into an empty place is one the tree drops as it grows (self-pruning). */
    boolean dropping;

    /** Sets a block whatever is there (null clears it). */
    public void set(int x, int y, int z, TreePart part) {
        long k = key(x, y, z);
        if (part == null) {
            parts.remove(k);
        } else {
            parts.put(k, part);
        }
    }

    /** Carves a block out for good: nothing may be put there afterwards. */
    public void carve(int x, int y, int z) {
        long k = key(x, y, z);
        holes.add(k);
        parts.remove(k);
    }

    public TreePart get(int x, int y, int z) {
        return parts.get(key(x, y, z));
    }

    public TreePart get(long key) {
        return parts.get(key);
    }

    /** Whether this block is one the tree drops as it grows (self-pruning). */
    public boolean isDropped(long key) {
        return dropped.contains(key);
    }

    /** Marks a block of mud as plastered over the bark there (not mud lying on the ground): the wood stays under it. */
    void markOverBark(int x, int y, int z) {
        overBark.add(key(x, y, z));
    }

    /** Whether this mud is plastered over the bark of the tree (so in the world it is dead wood with mud on it). */
    public boolean isOverBark(long key) {
        return overBark.contains(key);
    }

    public int size() {
        return parts.size();
    }

    public LongSet holes() {
        return holes;
    }

    /** Every block of the shape (a copy of the list first, so the visitor may change the shape). */
    public void forEach(Visitor visitor) {
        long[] keys = parts.keySet().toLongArray();
        for (long k : keys) {
            TreePart part = parts.get(k);
            if (part != null) {
                visitor.visit(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k), part);
            }
        }
    }

    /** Every block and its part (no copy: the shape must not change meanwhile). */
    public Iterable<Long2ObjectMap.Entry<TreePart>> entries() {
        return parts.long2ObjectEntrySet();
    }

    /**
     * The blocks joined to the ground (y = 0 or below), face to face through any part of the shape: what stands. A block
     * not in it would float.
     */
    public LongOpenHashSet grounded() {
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long k : parts.keySet()) {
            if (BlockPos.getY(k) <= 0) {
                seen.add(k);
                queue.add(k);
            }
        }
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            long[] next = {key(x + 1, y, z), key(x - 1, y, z), key(x, y + 1, z), key(x, y - 1, z), key(x, y, z + 1), key(x, y, z - 1)};
            for (long n : next) {
                if (parts.containsKey(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    /**
     * A fallen tree (step 5c): the blocks already in the shape are its stump, which stays where it stands; the logs added
     * with {@link #addLog} lie on the ground where it lands (the placer fits them to the land).
     */
    void markStump() {
        lying = new java.util.ArrayList<>();
        stump = new LongOpenHashSet(parts.keySet());
    }

    /** One lying log of a fallen tree, from its start to its end (the trunk first, then the limb stubs from its tip). */
    void addLog(double ax, double az, double bx, double bz) {
        lying.add(new double[] {ax, az, bx, bz});
    }

    /** A fallen tree's lying logs ({start x, z, end x, z}, the trunk first), or null for a tree that is not lying. */
    public java.util.List<double[]> lying() {
        return lying;
    }

    /** The blocks in the shape now (to tell, later, which blocks were added after). */
    LongOpenHashSet keys() {
        return new LongOpenHashSet(parts.keySet());
    }

    /** Every block added since {@code before} is a loose piece on the ground. */
    void markLooseSince(LongOpenHashSet before) {
        for (long k : parts.keySet()) {
            if (!before.contains(k)) {
                loose.add(k);
            }
        }
    }

    /** Whether this block is a loose piece on the ground (see {@link #markLooseSince}). */
    public boolean isLoose(long key) {
        return loose.contains(key);
    }

    /** Whether this block is part of a fallen tree's stump. */
    public boolean isStump(long key) {
        return stump != null && stump.contains(key);
    }

    /** Clears leaves, thorns and pods that ended up cut off from the tree (a few loose leaves at a pad's thin edge). */
    void clearLooseFoliage() {
        LongOpenHashSet standing = grounded();
        parts.long2ObjectEntrySet().removeIf(e -> e.getValue().isFoliage() && !standing.contains(e.getLongKey()));
    }

    /** Clears every block cut off from the ground (crumbs of a rotten log that the galleries cut loose). Returns how many. */
    int clearLoose() {
        LongOpenHashSet standing = grounded();
        int before = parts.size();
        parts.long2ObjectEntrySet().removeIf(e -> !standing.contains(e.getLongKey()));
        return before - parts.size();
    }
}
