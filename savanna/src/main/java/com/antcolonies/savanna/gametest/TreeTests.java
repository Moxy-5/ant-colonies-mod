package com.antcolonies.savanna.gametest;

import com.antcolonies.gametest.TestAnts;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.MIDDLE;
import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.MudCrustBlock;
import com.antcolonies.savanna.block.TreeBranchBlock;
import com.antcolonies.savanna.entity.TermiteWorkerEntity;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.worldgen.TreePlacer;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The giant savanna trees (M7.5). Step 1, the blocks: every one places, branches join wood and leaves, leaves never
 * decay, and thorny twigs prick a big animal pushing through them but never an insect. Step 3, new land: Minecraft's
 * savanna trees are gone from savannas and ours are there as structures; a giant put in chunk by chunk (as world
 * generation does) is the whole tree, its trunk going down where the ground dips under its foot; termite mud on a dead
 * tree stays (it is over wood the termites eat).
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TreeTests {
    /**
     * Every tree block stands where it is placed (on the arena's ground), the leaves do not tick (they never decay), and
     * a branch joins the log, the branch and the leaves beside it but not the plain ground under it.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void treeBlocksPlaceAndBranchesJoin(GameTestHelper helper) {
        int x = 2, z = 2;
        for (DeferredBlock<? extends Block> block : TreeBlocks.ALL) {
            if (x > 60) {
                x = 2; // the next row
                z += 3;
            }
            if (z > 8) {
                helper.fail("too many tree blocks for three rows");
            }
            BlockPos pos = new BlockPos(x, SURFACE, z);
            BlockState state = block.get().defaultBlockState();
            if (block == TreeBlocks.CAMEL_THORN_PODS) {
                helper.setBlock(pos.above(), TreeBlocks.CAMEL_THORN.leaves().get()); // pods hang from the crown
            }
            if (block == TreeBlocks.BRACKET_FUNGUS) {
                helper.setBlock(pos.south(), TreeBlocks.UMBRELLA_THORN.log().get()); // a bracket grows out of the bark behind it
            }
            helper.setBlock(pos, state);
            x += 2;
        }
        for (TreeBlocks.Kind kind : TreeBlocks.KINDS) {
            if (kind.leaves().get().defaultBlockState().isRandomlyTicking()) {
                helper.fail(kind.name() + " leaves tick (they would decay)");
            }
        }
        BlockPos log = new BlockPos(MIDDLE, SURFACE, MIDDLE);
        BlockPos first = log.east();
        BlockPos second = first.east();
        helper.setBlock(log, TreeBlocks.UMBRELLA_THORN.log().get());
        helper.setBlock(first, TreeBlocks.UMBRELLA_THORN.branch().get());
        helper.setBlock(second, TreeBlocks.UMBRELLA_THORN.branch().get());
        helper.setBlock(second.north(), TreeBlocks.UMBRELLA_THORN.leaves().get());
        helper.succeedWhen(() -> {
            for (int i = 0; i < TreeBlocks.ALL.size(); i++) {
                BlockPos pos = new BlockPos(2 + 2 * (i % 30), SURFACE, 2 + 3 * (i / 30)); // 30 to a row, as placed
                Block block = TreeBlocks.ALL.get(i).get();
                helper.assertBlockPresent(block, pos);
            }
            // Blocks set by hand do not join by themselves; a neighbour change does it, as when placed by a player.
            BlockState a = joined(helper, first);
            BlockState b = joined(helper, second);
            check(helper, a, Direction.WEST, true, "the first branch joins the log");
            check(helper, a, Direction.EAST, true, "the first branch joins the second");
            check(helper, a, Direction.DOWN, false, "a branch does not join the ground");
            check(helper, b, Direction.WEST, true, "the second branch joins the first");
            check(helper, b, Direction.NORTH, true, "the second branch joins the leaves");
            check(helper, b, Direction.EAST, false, "a branch does not join the air");
        });
    }

    private static BlockState joined(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockState state = helper.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof TreeBranchBlock branch)) {
            helper.fail("no branch at " + relative);
            return state;
        }
        return branch.joined(helper.getLevel(), pos, state);
    }

    private static void check(GameTestHelper helper, BlockState branch, Direction direction, boolean wanted, String what) {
        if (branch.getValue(TreeBranchBlock.PROPERTY_BY_DIRECTION.get(direction)) != wanted) {
            helper.fail("wrong: " + what);
        }
    }

    /**
     * A pig pushed through a row of thorny twigs is slowed and pricked (it loses health); a termite worker pushed through
     * the same row is never hurt (the spines are far bigger than an insect; a harvester ant until the split, step S4).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void thornyTwigsPrickBigAnimalsButNotInsects(GameTestHelper helper) {
        for (int x = MIDDLE - 4; x <= MIDDLE + 4; x++) {
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE), TreeBlocks.THORNY_TWIG.get());
            helper.setBlock(new BlockPos(x, SURFACE, MIDDLE + 4), TreeBlocks.THORNY_TWIG.get());
        }
        Pig pig = helper.spawn(EntityType.PIG, MIDDLE - 4, SURFACE, MIDDLE);
        TermiteWorkerEntity ant = TestAnts.loneWithAMind(helper, SavannaEntities.TERMITE_WORKER.get(), MIDDLE - 4, SURFACE, MIDDLE + 4);
        float pigHealth = pig.getHealth();
        float antHealth = ant.getHealth();
        helper.onEachTick(() -> {
            // Pushed east along the row, back to the start once at the end.
            for (var mob : new net.minecraft.world.entity.Mob[] {pig, ant}) {
                Vec3 at = mob.position();
                double startX = helper.absoluteVec(new Vec3(MIDDLE - 4 + 0.5, 0, 0)).x;
                if (at.x > startX + 8) {
                    mob.teleportTo(startX, at.y, at.z);
                }
                mob.setDeltaMovement(0.15, mob.getDeltaMovement().y, 0);
            }
            if (ant.getHealth() < antHealth) {
                helper.fail("the thorns hurt an insect");
            }
        });
        helper.runAfterDelay(160, () -> {
            if (pig.getHealth() >= pigHealth) {
                helper.fail("the pig went through the thorns unhurt");
            }
            if (ant.getHealth() < antHealth) {
                helper.fail("the thorns hurt an insect");
            }
            helper.succeed();
        });
    }

    /** Where the new land tests put their tree: the middle of the whole 65-block arena, room for a crown 46 wide. */
    private static final int TREE_MIDDLE = 32;

    /**
     * New land: the savanna has none of Minecraft's savanna trees, rain falls there, and our two structures exist; a grown camel thorn put
     * in chunk by chunk (each chunk only its own part, as world generation does) is the whole tree, and where a pit
     * dips under one side of its foot the trunk goes down to the pit's floor.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void giantTreesStandInNewLand(GameTestHelper helper) {
        var level = helper.getLevel();
        Biome savanna = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.SAVANNA);
        for (var step : savanna.getGenerationSettings().features()) {
            for (var feature : step) {
                String id = feature.unwrapKey().map(k -> k.location().toString()).orElse("");
                if (id.equals("minecraft:trees_savanna") || id.equals("minecraft:trees_windswept_savanna")) {
                    helper.fail("the savanna still grows Minecraft's trees (" + id + ")");
                }
            }
        }
        // It rains in every savanna (Minecraft's never do; real ones have a rainy season), and they stay warm (rain, not snow).
        for (var key : java.util.List.of(Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA)) {
            Biome biome = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(key);
            if (!biome.hasPrecipitation()) {
                helper.fail(key.location() + " never rains");
            }
            if (biome.getBaseTemperature() < 1) {
                helper.fail(key.location() + " is no longer hot: " + biome.getBaseTemperature());
            }
        }
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String name : new String[] {"big_savanna_tree", "young_savanna_tree"}) {
            if (!structures.containsKey(ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, name))) {
                helper.fail("no structure " + name);
            }
        }
        // A pit 3 deep under the east side of the foot.
        for (int x = TREE_MIDDLE + 1; x <= TREE_MIDDLE + 3; x++) {
            for (int z = TREE_MIDDLE - 1; z <= TREE_MIDDLE + 1; z++) {
                for (int y = SURFACE - 3; y < SURFACE; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE, TREE_MIDDLE));
        TreePlacer.Placed tree = TreePlacer.placed("camel_thorn", 5, 1, TreeStage.LIVING);
        BoundingBox box = tree.box(foot);
        long placing = 0, longestChunk = 0;
        int chunks = 0;
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                BoundingBox chunk = new BoundingBox(cx << 4, box.minY(), cz << 4, (cx << 4) + 15, box.maxY(), (cz << 4) + 15);
                long start = System.nanoTime();
                TreePlacer.place(level, tree, foot, chunk, Block.UPDATE_CLIENTS);
                long took = System.nanoTime() - start;
                placing += took;
                longestChunk = Math.max(longestChunk, took);
                chunks++;
            }
        }
        newLandSpeed(helper, placing, longestChunk, chunks);
        int missing = 0, above = 0;
        String first = "";
        for (int i = 0; i < tree.size(); i++) {
            if (tree.ys()[i] < 0 || tree.states()[i].getBlock() instanceof FallenBranchBlock) {
                continue;
            }
            above++;
            BlockPos pos = foot.offset(tree.xs()[i], tree.ys()[i], tree.zs()[i]);
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                continue; // the test arena's ceiling, which the tree grows up through
            }
            if (!level.getBlockState(pos).is(tree.states()[i].getBlock())) {
                if (missing++ == 0) {
                    first = tree.xs()[i] + "," + tree.ys()[i] + "," + tree.zs()[i] + " " + tree.states()[i].getBlock() + " is " + level.getBlockState(pos);
                }
            }
        }
        if (above < 3000) {
            helper.fail("a grown camel thorn of only " + above + " blocks");
        }
        if (missing > 0) {
            helper.fail(missing + " of " + above + " blocks of the tree missing, the first " + first);
        }
        // The trunk over the pit goes down to the pit floor.
        for (int y = SURFACE - 3; y < SURFACE; y++) {
            BlockState there = helper.getBlockState(new BlockPos(TREE_MIDDLE + 1, y, TREE_MIDDLE));
            if (!there.is(TreeBlocks.TREE_WOOD)) {
                helper.fail("the trunk does not reach down into the pit at y " + y + ": " + there);
            }
        }
        newLandLitter(helper, foot);
        helper.succeed();
    }

    /**
     * Step 7: how long new land's trees take. Trees of both structures are picked as new land picks them (the real
     * spread of kinds, ages and dead stages) and worked out from nothing, and the grown camel thorn above was put down
     * chunk by chunk; the times go to the log. The limits are far over what they take (a test server shares the PC with
     * others), so only something badly slower fails: a tree's shape over half a second on average, a chunk's part of a
     * tree over a tenth of a second.
     */
    private static void newLandSpeed(GameTestHelper helper, long placing, long longestChunk, int chunks) {
        RandomSource random = RandomSource.create(7);
        long shaping = 0, longestShape = 0;
        int trees = 0;
        String slowest = "";
        for (String size : new String[] {"big", "young"}) {
            for (int i = 0; i < 30; i++) {
                var pick = com.antcolonies.savanna.worldgen.GiantTreeStructure.pick(size, random);
                if (com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(pick)) {
                    continue;
                }
                long start = System.nanoTime();
                TreePlacer.placed(pick.kind(), pick.seed() + 1_000_000_007L * (i + 1), pick.age(), pick.stage()); // a seed no other test has worked out
                long took = System.nanoTime() - start;
                shaping += took;
                trees++;
                if (took > longestShape) {
                    longestShape = took;
                    slowest = pick.kind() + " age " + pick.age() + " " + pick.stage();
                }
            }
        }
        double shapeMs = shaping / 1e6 / trees, chunkMs = placing / 1e6 / chunks;
        SavannaMod.LOGGER.info("[trees speed] {} new-land trees worked out, {} ms each, longest {} ms ({}); a grown camel thorn put down in {} chunks, {} ms each, longest {} ms",
                trees, String.format("%.1f", shapeMs), String.format("%.1f", longestShape / 1e6), slowest, chunks,
                String.format("%.2f", chunkMs), String.format("%.1f", longestChunk / 1e6));
        if (shapeMs > 500) {
            helper.fail("new land's trees take " + String.format("%.0f", shapeMs) + " ms each to work out (the slowest " + slowest + ")");
        }
        if (chunkMs > 100) {
            helper.fail("putting a grown tree down takes " + String.format("%.0f", chunkMs) + " ms a chunk");
        }
    }

    /**
     * Step 6c: the dead wood new land lays round its trees. Under the grown camel thorn lie fallen branches, on the land
     * (the pit's floor too), never in the trunk; old trees often have a fallen giant limb, baobabs never; a fallen young
     * tree is dead wood only, with its stem.
     */
    private static void newLandLitter(GameTestHelper helper, BlockPos foot) {
        var level = helper.getLevel();
        var pick = new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("camel_thorn", 5, 1, TreeStage.LIVING);
        TreePlacer.Placed litter = com.antcolonies.savanna.worldgen.GiantTreePiece.litter(pick, foot, TreePlacer.ground(level, foot));
        int branch = 0;
        for (int i = 0; i < litter.size(); i++) {
            branch += litter.states()[i].getBlock() instanceof FallenBranchBlock ? 1 : 0;
        }
        if (branch < 2 || branch > 24) {
            helper.fail("new land put " + branch + " blocks of fallen branch under a grown camel thorn (1 to 3 branches 2 or 3 long, more with a limb)");
        }
        int placed = TreePlacer.place(level, litter, foot, litter.box(foot), Block.UPDATE_CLIENTS);
        if (placed == 0) {
            BlockPos first = foot.offset(litter.xs()[0], litter.ys()[0], litter.zs()[0]);
            helper.fail("none of the " + litter.size() + " blocks of dead wood under the camel thorn was put down; the first, "
                    + litter.states()[0] + " at " + litter.xs()[0] + "," + litter.ys()[0] + "," + litter.zs()[0] + ", finds "
                    + level.getBlockState(first) + " over " + level.getBlockState(first.below()));
        }
        for (int i = 0; i < litter.size(); i++) {
            BlockPos pos = foot.offset(litter.xs()[i], litter.ys()[i], litter.zs()[i]);
            BlockState there = level.getBlockState(pos);
            if (there.getBlock() instanceof FallenBranchBlock && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
                helper.fail("a fallen branch of new land floats at " + pos);
            }
        }
        int limbs = 0;
        for (long seed = 0; seed < 40; seed++) {
            if (hasDeadWood(TreePlacer.litter(com.antcolonies.savanna.tree.Litter.underTree(seed, 1.25, 20, 2, false)))) {
                limbs++;
            }
            if (hasDeadWood(TreePlacer.litter(com.antcolonies.savanna.tree.Litter.underTree(seed, 1.25, 20, 2, true)))) {
                helper.fail("a baobab of new land has a fallen giant limb (seed " + seed + ")");
            }
        }
        if (limbs < 20 || limbs == 40) {
            helper.fail(limbs + " of 40 ancient trees have a fallen giant limb under them (most, not all)");
        }
        var young = new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("umbrella_thorn", 3, 0.3, TreeStage.FALLEN);
        if (!com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(young)
                || com.antcolonies.savanna.worldgen.GiantTreePiece.fallenYoung(new com.antcolonies.savanna.worldgen.GiantTreeStructure.Pick("umbrella_thorn", 3, 1, TreeStage.FALLEN))) {
            helper.fail("a fallen young tree of new land is not told from a fallen giant");
        }
        if (!hasDeadWood(com.antcolonies.savanna.worldgen.GiantTreePiece.litter(young, foot, (x, z) -> foot.getY()))) {
            helper.fail("a fallen young tree of new land has no stem");
        }
    }

    /** Whether some of the dead wood is more than fallen branches (a limb or a stem). */
    private static boolean hasDeadWood(TreePlacer.Placed placed) {
        for (int i = 0; i < placed.size(); i++) {
            if (!(placed.states()[i].getBlock() instanceof FallenBranchBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A mud-climbed dead umbrella thorn: termite mud is plastered over the dead bark (Mud Crust plates on bleached wood)
     * and it stays, however long it ticks: it is over wood the termites eat, so it is still needed.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void termiteMudOnADeadTreeStays(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(TREE_MIDDLE, SURFACE, TREE_MIDDLE));
        TreePlacer.Placed tree = TreePlacer.placed("umbrella_thorn", 3, 1, TreeStage.MUD);
        TreePlacer.place(level, tree, foot, tree.box(foot), Block.UPDATE_CLIENTS);
        List<BlockPos> crusts = new ArrayList<>();
        for (int i = 0; i < tree.size(); i++) {
            BlockPos pos = foot.offset(tree.xs()[i], tree.ys()[i], tree.zs()[i]);
            if (MudCrustBlock.isCrust(level.getBlockState(pos))) {
                crusts.add(pos);
            }
        }
        if (crusts.size() < 20) {
            helper.fail("only " + crusts.size() + " mud plates on a mud-climbed tree");
        }
        for (BlockPos pos : crusts) {
            boolean onWood = false;
            BlockState crust = level.getBlockState(pos);
            for (Direction d : Direction.values()) {
                if (crust.getValue(MudCrustBlock.FACES.get(d)) && level.getBlockState(pos.relative(d)).is(TreeBlocks.TERMITE_WOOD)) {
                    onWood = true;
                }
            }
            if (!onWood) {
                helper.fail("mud plate at " + pos + " not on dead wood: " + crust);
            }
        }
        var random = level.getRandom();
        for (int round = 0; round < 40; round++) {
            for (BlockPos pos : crusts) {
                BlockState state = level.getBlockState(pos);
                if (state.is(SavannaBlocks.MUD_CRUST.get())) {
                    state.randomTick(level, pos, random);
                }
            }
        }
        int gone = 0;
        for (BlockPos pos : crusts) {
            gone += MudCrustBlock.isCrust(level.getBlockState(pos)) ? 0 : 1;
        }
        if (gone > 0) {
            helper.fail(gone + " of " + crusts.size() + " mud plates crumbled off the dead tree");
        }
        helper.succeed();
    }
}
