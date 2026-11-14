package com.antcolonies.savanna.worldgen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.antcolonies.savanna.block.DoumBlockEntity;
import com.antcolonies.savanna.block.DoumLook;
import com.antcolonies.savanna.registry.SavannaStructures;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.DoumShape;
import com.antcolonies.savanna.tree.FigShape;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One sycamore fig or doum palm of the floodplain in new land (see {@link FloodplainTreeStructure}): saved as its record
 * (kind, the viewer's seed, age and foot), so each chunk it crosses works out the same tree and puts in its own part of it:
 * into open ground only (air, grass), its roots (below the foot) into soil too, its wood through another tree's leaves,
 * never into water. Not in the tree book: these trees do not grow or die in play yet (step 8).
 */
public class FloodplainTreePiece extends StructurePiece {
    private final FloodplainTreeStructure.Tree tree;

    public FloodplainTreePiece(FloodplainTreeStructure.Tree tree, BoundingBox box) {
        super(SavannaStructures.FLOODPLAIN_TREE_PIECE.get(), 0, box);
        this.tree = tree;
    }

    public FloodplainTreePiece(CompoundTag tag) {
        super(SavannaStructures.FLOODPLAIN_TREE_PIECE.get(), tag);
        tree = new FloodplainTreeStructure.Tree(tag.getString("kind"), tag.getInt("seed"), tag.getString("age"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }

    public FloodplainTreeStructure.Tree tree() {
        return tree;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("kind", tree.kind());
        tag.putInt("seed", tree.seed());
        tag.putString("age", tree.age());
        tag.putInt("x", tree.foot().getX());
        tag.putInt("y", tree.foot().getY());
        tag.putInt("z", tree.foot().getZ());
    }

    /** A tree's blocks round its foot at (0, 0, 0), in order (wood first), and the doum's sculpted pieces' recipes. */
    public record Shape(Long2ObjectLinkedOpenHashMap<BlockState> blocks, Long2ObjectLinkedOpenHashMap<DoumLook> looks) {
        /** The box the tree fills with its foot at {@code foot}. */
        public BoundingBox box(BlockPos foot) {
            int x0 = 0, y0 = 0, z0 = 0, x1 = 0, y1 = 0, z1 = 0;
            for (long k : blocks.keySet()) {
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                x0 = Math.min(x0, x);
                y0 = Math.min(y0, y);
                z0 = Math.min(z0, z);
                x1 = Math.max(x1, x);
                y1 = Math.max(y1, y);
                z1 = Math.max(z1, z);
            }
            return new BoundingBox(foot.getX() + x0, foot.getY() + y0, foot.getZ() + z0,
                    foot.getX() + x1, foot.getY() + y1, foot.getZ() + z1);
        }
    }

    /** Shapes being put in round the players, kept while their chunks are made (each chunk would work it out the same). */
    private static final Map<String, Shape> SHAPES = Collections.synchronizedMap(new LinkedHashMap<>(512, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Shape> eldest) {
            return size() > 256;
        }
    });

    /** The tree's shape (worked out once, kept for its chunks). */
    public static Shape shape(FloodplainTreeStructure.Tree tree) {
        String key = tree.kind() + "|" + tree.seed() + "|" + tree.age();
        Shape kept = SHAPES.get(key);
        if (kept == null) {
            if (tree.kind().equals("fig")) {
                kept = new Shape(FigShape.make(tree.seed(), FigShape.Age.byId(tree.age())).states(), new Long2ObjectLinkedOpenHashMap<>());
            } else {
                DoumShape.Placed placed = DoumShape.make(tree.seed(), DoumShape.Age.byId(tree.age())).placed();
                kept = new Shape(placed.blocks(), placed.looks());
            }
            SHAPES.put(key, kept);
        }
        return kept;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        long start = System.nanoTime();
        try {
            putDown(level, box);
        } finally {
            com.antcolonies.debug.Perf.newLand(com.antcolonies.debug.Perf.NewLand.PLACING, System.nanoTime() - start);
        }
    }

    private void putDown(WorldGenLevel level, BoundingBox box) {
        Shape shape = shape(tree);
        BlockPos foot = tree.foot();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (var e : shape.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            pos.set(foot.getX() + BlockPos.getX(k), foot.getY() + BlockPos.getY(k), foot.getZ() + BlockPos.getZ(k));
            if (!box.isInside(pos)) {
                continue;
            }
            BlockState there = level.getBlockState(pos);
            // open ground, roots into soil too, and wood through a neighbour's leaves (palms in a grove, a palm by a fig:
            // a stem never broken by another tree's crown)
            boolean wood = e.getValue().is(TreeBlocks.TREE_WOOD) && there.is(TreeBlocks.TREE_LEAVES);
            if (!TreePlacer.isOpen(there) && !(BlockPos.getY(k) < 0 && TreePlacer.isSoil(there)) && !wood) {
                continue;
            }
            level.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS);
            DoumLook look = shape.looks().get(k);
            if (look != null && level.getBlockEntity(pos) instanceof DoumBlockEntity piece) {
                piece.setLook(look);
            }
        }
    }
}
