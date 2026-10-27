package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.FallenBranchBlock;
import com.antcolonies.savanna.block.BracketFungusBlock;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.tree.RotMarks;
import com.antcolonies.savanna.tree.Forest;
import com.antcolonies.savanna.tree.TreeFall;
import com.antcolonies.savanna.tree.TreeLife;
import com.antcolonies.savanna.tree.TreeStage;
import com.antcolonies.savanna.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The giant trees growing in play (M7.5 step 4, {@link Forest}): a tree grows a step a day and only adds wood, never
 * into a player's block, and a tree cut through at its foot leaves the book; bone meal grows a seedling and a young tree
 * but never past young; seedlings come up after rain, scattered, not under a crown; little trees die (a seedling
 * vanishes, a young tree leaves a fallen branch); and a tree away for many days catches up at once. Each test makes
 * its own days (the test world's clock stands still) and forgets its trees when done.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class TreeGrowthTests {
    private static final int MIDDLE = 32;

    /** A seed whose tree, from this age on day {@code base}, lives (or, {@code dies}, dies on exactly the last day) for so many days. */
    static long seedFor(double age, long base, int days, boolean dies) {
        for (long seed = 1; seed < 1_000_000; seed++) {
            double a = age, speed = TreeLife.speed(seed);
            int died = -1;
            for (int d = 1; d <= days; d++) {
                double next = TreeLife.grow(a, speed);
                if (TreeLife.dice(seed, base + d).nextDouble() < TreeLife.deathChance(a, next)) {
                    died = d;
                    break;
                }
                a = next;
            }
            if (dies ? died == days : died < 0) {
                return seed;
            }
        }
        throw new IllegalStateException("no seed found");
    }

    /** How many blocks of a tree's shape at its placed age (above the ground) are in the world. */
    private static int blocksThere(ServerLevel level, Forest.Tree t) {
        return count(level, t, true);
    }

    /** How many blocks of a tree's shape at its placed age (above the ground) are missing (the arena's ceiling aside). */
    private static int missing(ServerLevel level, Forest.Tree t) {
        return count(level, t, false);
    }

    private static int count(ServerLevel level, Forest.Tree t, boolean there) {
        TreePlacer.Placed shape = TreePlacer.placed(t.kind, t.seed, t.placedAge, t.stage);
        int n = 0;
        for (int i = 0; i < shape.size(); i++) {
            if (shape.ys()[i] < 0) {
                continue;
            }
            BlockState state = level.getBlockState(t.foot.offset(shape.xs()[i], shape.ys()[i], shape.zs()[i]));
            boolean is = state.is(shape.states()[i].getBlock());
            n += there ? (is ? 1 : 0) : (is || state.is(Blocks.BARRIER) ? 0 : 1);
        }
        return n;
    }

    /** Open grassland over the whole 65-block arena (it starts empty): grass on 2 blocks of dirt. */
    private static void grassland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < 65; x++) {
            for (int z = 0; z < 65; z++) {
                BlockPos top = helper.absolutePos(new BlockPos(x, SURFACE - 1, z));
                level.setBlock(top.below(2), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(top.below(), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

    /** The blocks from 4 over to 4 under a place, for a failure's message. */
    private static String column(ServerLevel level, BlockPos pos) {
        StringBuilder out = new StringBuilder();
        for (int y = 4; y >= -4; y--) {
            out.append(y).append('=').append(level.getBlockState(pos.above(y)).getBlock().getDescriptionId().replace("block.", "")).append(' ');
        }
        return out.toString();
    }

    /** The trees in the book with their foot in this test's arena. */
    private static List<Forest.Tree> inArena(GameTestHelper helper, Forest forest, BlockPos middle) {
        List<Forest.Tree> out = new ArrayList<>();
        for (Forest.Tree t : forest.near(middle, 48)) {
            if (helper.getBounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(t.foot))) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * A young umbrella thorn grows a step every day for 12 days: older and bigger each day, its trunk and log wood never
     * gone, and a player's glass block where its new wood or leaves would go is left alone. Then its trunk is cut through
     * at the foot and the next day it is no longer in the book. Also times the work of a day's step, young and grown.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aTreeGrowsAStepADayAroundAPlayersBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        int days = 12;
        long seed = seedFor(0.2, base, days, false);
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 0.2, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 0.2, "the young tree was not placed: " + t.placedAge);
        // The player's block: where the tree will have a block in 12 days but has none now.
        double end = 0.2;
        for (int d = 0; d < days; d++) {
            end = TreeLife.grow(end, TreeLife.speed(seed));
        }
        TreePlacer.Placed first = TreePlacer.placed("umbrella_thorn", seed, 0.2, TreeStage.LIVING);
        TreePlacer.Placed last = TreePlacer.placed("umbrella_thorn", seed, TreeLife.shapeAge(end), TreeStage.LIVING);
        java.util.Set<Long> before = new java.util.HashSet<>();
        for (int i = 0; i < first.size(); i++) {
            before.add(BlockPos.asLong(first.xs()[i], first.ys()[i], first.zs()[i]));
        }
        BlockPos glass = null;
        for (int i = 0; i < last.size() && glass == null; i++) {
            if (last.ys()[i] > 2 && !before.contains(BlockPos.asLong(last.xs()[i], last.ys()[i], last.zs()[i]))) {
                glass = foot.offset(last.xs()[i], last.ys()[i], last.zs()[i]);
            }
        }
        helper.assertTrue(glass != null, "the tree does not grow in 12 days");
        level.setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
        List<BlockPos> logs = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            if (first.states()[i].getBlock() instanceof RotatedPillarBlock && first.ys()[i] >= 0) {
                logs.add(foot.offset(first.xs()[i], first.ys()[i], first.zs()[i]));
            }
        }
        int blocks = blocksThere(level, t);
        double age = t.age;
        long worst = 0;
        for (int d = 1; d <= days; d++) {
            long start = System.nanoTime();
            forest.update(level, t, base + d);
            worst = Math.max(worst, System.nanoTime() - start);
            helper.assertTrue(forest.at(foot) == t, "the tree died on day " + d + " (its seed should live)");
            helper.assertTrue(t.age > age, "day " + d + ": the tree did not grow");
            helper.assertTrue(t.placedAge == TreeLife.shapeAge(t.age), "day " + d + ": its shape in the world is behind");
            age = t.age;
            for (BlockPos log : logs) {
                helper.assertTrue(level.getBlockState(log).is(TreeBlocks.TREE_WOOD), "day " + d + ": log wood gone at " + log);
            }
        }
        int grownTo = blocksThere(level, t);
        helper.assertTrue(grownTo > blocks + 20, "the tree hardly grew: " + blocks + " blocks to " + grownTo);
        helper.assertTrue(level.getBlockState(glass).is(Blocks.GLASS), "the tree grew into the player's glass block");
        // A grown tree's step, timed once its two shapes are worked out (the first time costs the shape maker).
        long shapes = System.nanoTime();
        TreePlacer.placed("umbrella_thorn", 77, 1.0, TreeStage.LIVING);
        TreePlacer.placed("umbrella_thorn", 77, 1.01, TreeStage.LIVING);
        shapes = System.nanoTime() - shapes;
        SavannaMod.LOGGER.info("[trees] a young tree's day: worst {} ms; a grown umbrella thorn's two shapes worked out in {} ms",
                String.format("%.2f", worst / 1e6), String.format("%.1f", shapes / 1e6));
        // Cut through at the foot: the book forgets it the next day.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockState there = level.getBlockState(foot.offset(x, 0, z));
                if (there.is(TreeBlocks.TREE_WOOD)) {
                    level.setBlockAndUpdate(foot.offset(x, 0, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        forest.update(level, t, base + days + 1);
        helper.assertTrue(forest.at(foot) == null, "a tree cut through at its foot is still in the book");
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * A seedling planted (a camel thorn) grows with bone meal: a young tree after a few, and never past the young look
     * however much more it gets (bone meal then does nothing). Bone meal on a grown tree does nothing either.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void boneMealGrowsLittleTreesButNotPastYoung(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        level.setBlockAndUpdate(foot, TreeBlocks.CAMEL_THORN.seedling().get().defaultBlockState());
        Forest.Tree t = forest.addSeedling("camel_thorn", foot, 4242, base, true);
        int meals = 0;
        while (t.age < TreeLife.YOUNG && meals < 10) {
            helper.assertTrue(forest.boneMeal(level, foot), "bone meal did nothing to the seedling");
            meals++;
        }
        helper.assertTrue(meals >= 2 && meals <= 5, "the seedling became young after " + meals + " bone meals");
        helper.assertTrue(level.getBlockState(foot).is(TreeBlocks.TREE_WOOD), "no stem at the young tree's foot: " + level.getBlockState(foot));
        while (forest.boneMeal(level, foot) && meals < 100) {
            meals++;
        }
        helper.assertTrue(t.age == TreeLife.YOUNG_END, "bone meal stopped at age " + t.age + ", not the end of young");
        helper.assertTrue(blocksThere(level, t) > 30, "the young tree is only " + blocksThere(level, t) + " blocks");
        // A grown one: bone meal does nothing.
        BlockPos other = helper.absolutePos(new BlockPos(8, SURFACE, 8));
        Forest.Tree grown = forest.add(new Forest.Tree("umbrella_thorn", other, 5, 1.0, TreeStage.LIVING, 1.0, base));
        helper.assertTrue(!forest.boneMeal(level, other), "bone meal grew a grown tree");
        helper.assertTrue(grown.age == 1.0, "bone meal changed a grown tree's age");
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * Seedlings come up the morning after rain over open savanna, scattered across the land, and none after a dry day;
     * none under a tree's crown or beside its foot. Seedlings of rain days missed while away come up (or have died)
     * when the land is caught up.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void seedlingsComeUpAfterRainScattered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level) + 5000; // rain days of their own, far from other tests'
        BlockPos corner = helper.absolutePos(new BlockPos(0, SURFACE, 0));
        BlockPos far = helper.absolutePos(new BlockPos(64, SURFACE + 1, 64));
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level),
                "fillbiome " + corner.getX() + " " + (corner.getY() - 2) + " " + corner.getZ() + " " + far.getX() + " " + far.getY() + " "
                        + far.getZ() + " minecraft:savanna");
        BlockPos probe = helper.absolutePos(new BlockPos(5, SURFACE, 5));
        helper.assertTrue(forest.sproutSite(level, probe.getX(), probe.getZ()) != null, "open savanna ground is no place for a seedling: biome "
                + level.getBiome(probe).unwrapKey().map(k -> k.location().toString()).orElse("?") + ", column " + column(level, probe));
        // A young tree with its crown in the middle: no seedling under it.
        BlockPos parent = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree tree = forest.add(new Forest.Tree("umbrella_thorn", parent, seedFor(0.4, base, 0, false), 0.4, TreeStage.LIVING, -1, base));
        forest.update(level, tree, base);
        int count = 0;
        for (int d = 0; d < 40; d++) {
            boolean rain = d % 2 == 0;
            if (rain) {
                forest.rainedOn(base + d);
            }
            int came = forest.sproutIn(level, corner.getX(), corner.getZ(), base + d, base + d + 1);
            helper.assertTrue(rain || came == 0, came + " seedlings after a dry day");
            count += came;
        }
        List<Forest.Tree> seedlings = inArena(helper, forest, parent);
        seedlings.remove(tree);
        helper.assertTrue(count >= 5, "only " + count + " seedlings after 20 days of rain");
        helper.assertTrue(seedlings.size() == count, count + " came up but " + seedlings.size() + " are in the book");
        double spread = 0;
        for (Forest.Tree s : seedlings) {
            helper.assertTrue(level.getBlockState(s.foot).getBlock() == TreeBlocks.KINDS.stream().filter(k -> k.name().equals(s.kind))
                    .findFirst().orElseThrow().seedling().get(), "no seedling block at " + s.foot);
            helper.assertTrue(!s.foot.closerThan(parent, 4), "a seedling at the parent's foot");
            for (int y = 1; y < 24; y++) {
                helper.assertTrue(!level.getBlockState(s.foot.above(y)).is(TreeBlocks.TREE_LEAVES), "a seedling under a crown at " + s.foot);
            }
            spread += Math.sqrt(s.foot.distSqr(parent));
        }
        helper.assertTrue(spread / seedlings.size() > 12, "the seedlings are bunched round the tree: on average " + spread / seedlings.size() + " blocks off");
        // Away: land last brought up to date 30 days ago gets the seedlings of the rain days since, which have lived
        // since they came up (most of them died).
        forest.forgetInside(helper.getBounds());
        tree = forest.add(new Forest.Tree("umbrella_thorn", parent, tree.seed, 0.4, TreeStage.LIVING, 0.4, base + 40));
        for (int d = 40; d < 70; d++) {
            forest.rainedOn(base + d);
        }
        int before = forest.sprouted, deaths = forest.deaths;
        forest.sproutIn(level, corner.getX(), corner.getZ(), base + 40, base + 70);
        int came = forest.sprouted - before, died = forest.deaths - deaths;
        int living = inArena(helper, forest, parent).size() - 1;
        helper.assertTrue(came >= 5, "only " + came + " seedlings for 30 rain days away");
        helper.assertTrue(died > came / 2, "of " + came + " seedlings away only " + died + " died");
        helper.assertTrue(living == came - died, "the book holds " + living + " of the " + (came - died) + " that lived");
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * Little trees die: a seedling whose day has come vanishes, a young tree whose day has come is taken away and
     * leaves a fallen branch. Both leave the book.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void littleTreesDieAndAYoungOneLeavesABranch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        BlockPos seedlingFoot = helper.absolutePos(new BlockPos(16, SURFACE, 16));
        Forest.Tree seedling = forest.add(new Forest.Tree("umbrella_thorn", seedlingFoot, seedFor(0, base, 2, true), 0, TreeStage.LIVING, -1, base));
        forest.update(level, seedling, base);
        helper.assertTrue(level.getBlockState(seedlingFoot).is(TreeBlocks.UMBRELLA_THORN.seedling().get()), "the seedling did not come up");
        forest.update(level, seedling, base + 1);
        helper.assertTrue(forest.at(seedlingFoot) == seedling, "the seedling died a day early");
        forest.update(level, seedling, base + 2);
        helper.assertTrue(forest.at(seedlingFoot) == null, "the dead seedling is still in the book");
        helper.assertTrue(level.getBlockState(seedlingFoot).isAir(), "the dead seedling is still there: " + level.getBlockState(seedlingFoot));

        BlockPos youngFoot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree young = forest.add(new Forest.Tree("camel_thorn", youngFoot, seedFor(0.25, base, 3, true), 0.25, TreeStage.LIVING, -1, base));
        forest.update(level, young, base);
        int blocks = blocksThere(level, young);
        helper.assertTrue(blocks > 20, "the young tree was not placed");
        forest.update(level, young, base + 3);
        helper.assertTrue(forest.at(youngFoot) == null, "the dead young tree is still in the book");
        helper.assertTrue(blocksThere(level, young) == 0, blocksThere(level, young) + " blocks of the dead young tree are left");
        int branches = 0;
        for (BlockPos pos : BlockPos.betweenClosed(youngFoot.offset(-4, -1, -4), youngFoot.offset(4, 2, 4))) {
            if (level.getBlockState(pos).getBlock() instanceof FallenBranchBlock) {
                branches++;
            }
        }
        helper.assertTrue(branches > 0, "the dead young tree left no fallen branch; column " + column(level, youngFoot) + " east " + column(level, youngFoot.east(2)));
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * A young tree whose land was away for 15 days catches up at once when it is next looked at: it has lived every day
     * (the same age as if it had grown day by day) and its whole shape at that age is in the world. A record saved and
     * loaded is the same tree.
     */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aTreeAwayCatchesUpAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        int days = 15;
        long seed = seedFor(0.1, base, days, false);
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("baobab", foot, seed, 0.1, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        double expected = 0.1;
        for (int d = 0; d < days; d++) {
            expected = TreeLife.grow(expected, TreeLife.speed(seed));
        }
        forest.update(level, t, base + days);
        helper.assertTrue(Math.abs(t.age - expected) < 1e-9, "caught up to age " + t.age + ", not " + expected);
        helper.assertTrue(t.placedAge == TreeLife.shapeAge(expected), "its shape in the world is at " + t.placedAge);
        TreePlacer.Placed shape = TreePlacer.placed("baobab", seed, t.placedAge, TreeStage.LIVING);
        helper.assertTrue(shape.size() > 30, "the caught-up baobab is only " + shape.size() + " blocks");
        helper.assertTrue(missing(level, t) == 0, missing(level, t) + " blocks of the caught-up tree missing");
        Forest.Tree copy = Forest.Tree.load(t.save());
        helper.assertTrue(copy.kind.equals(t.kind) && copy.foot.equals(t.foot) && copy.seed == t.seed && copy.age == t.age
                && copy.placedAge == t.placedAge && copy.stage == t.stage && copy.lastDay == t.lastDay, "a saved and loaded tree differs");
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    // ---------------------------------------------------------------- step 5: death and the fall

    /** How many blocks round a tree's foot (30 each way sideways, minY to maxY over it) are like this. */
    private static int countAround(ServerLevel level, BlockPos foot, int minY, int maxY, java.util.function.Predicate<BlockState> like) {
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (like.test(level.getBlockState(pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z)))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static boolean livingLeaves(BlockState s) {
        return s.getBlock() instanceof com.antcolonies.savanna.block.TreeLeavesBlock && !deadLeaves(s);
    }

    private static boolean deadLeaves(BlockState s) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_dead_leaves");
    }

    /** Blocks like this within {@code r} of the foot, from {@code minY} to {@code maxY} over it. */
    private static int countNear(ServerLevel level, BlockPos foot, int r, int minY, int maxY, java.util.function.Predicate<BlockState> like) {
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (like.test(level.getBlockState(pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z)))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Clears a big tree's blocks over the ground (a test with sky access would leave them standing over the next test). */
    private static void clearAbove(GameTestHelper helper, BlockPos foot) {
        ServerLevel level = helper.getLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                for (int y = 0; y <= 50; y++) {
                    pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z);
                    BlockState there = level.getBlockState(pos);
                    if (!there.isAir() && !there.is(Blocks.BARRIER)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static boolean deadWood(BlockState s) {
        return s.is(TreeBlocks.BLEACHED_WOOD.get()) || s.is(TreeBlocks.BLEACHED_LOG.get()) || s.is(TreeBlocks.PEELING_BLEACHED_LOG.get())
                || s.is(TreeBlocks.BLEACHED_BRANCH.get());
    }

    private static boolean livingWood(BlockState s) {
        return s.is(TreeBlocks.TREE_WOOD) && !deadWood(s);
    }

    private static boolean leaves(BlockState s) {
        return s.is(TreeBlocks.TREE_LEAVES);
    }

    /**
     * Step 5: a grown umbrella thorn that has lived its lifespan (75 to 125 days) dies of old age, and its dead stages
     * follow day by day as decided: the day it dies it still stands as it was (withering), then a bare silver snag (no
     * leaves, no living wood), termite mud climbing it, the limbs broken off and lying round it, and the fall: nothing
     * stands above the stump and the hollow log lies on the ground. A fallen log then stays in the book as it is.
     */
    @GameTest(template = "arena", timeoutTicks = 300, skyAccess = true)
    public static void aGrownTreeDiesOfOldAgeAndFalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long seed = 11;
        com.antcolonies.savanna.tree.TreeKind kind = TreePlacer.kind("umbrella_thorn");
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 1.0, "the grown tree was not placed");
        int leafCount = countAround(level, foot, 0, 40, TreeGrowthTests::leaves);
        helper.assertTrue(leafCount > 300, "the grown tree has only " + leafCount + " leaves");
        // Step 6b: a grown tree sheds dead branches under its own crown, at most 3 lying there; young trees none.
        helper.assertTrue(com.antcolonies.savanna.tree.BranchShedding.chance(0.3) == 0 && com.antcolonies.savanna.tree.BranchShedding.chance(1.0) >= 0.3
                && com.antcolonies.savanna.tree.BranchShedding.chance(com.antcolonies.savanna.tree.TreeShapes.ANCIENT) >= 0.99, "the shedding chances");
        TreePlacer.Placed upright = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.LIVING);
        int dropped = 0;
        for (int i = 0; i < 12; i++) {
            if (com.antcolonies.savanna.tree.BranchShedding.shed(level, foot, upright, new java.util.Random(i), false)) {
                dropped++;
            }
        }
        int lying = com.antcolonies.savanna.tree.BranchShedding.lyingUnder(level, foot, upright);
        helper.assertTrue(dropped >= 1 && lying == dropped && lying <= com.antcolonies.savanna.tree.BranchShedding.MAX_UNDER_TREE,
                "the grown tree dropped " + dropped + " branches, " + lying + " lie under it");
        for (BlockPos pos : BlockPos.betweenClosed(foot.offset(upright.minX(), -2, upright.minZ()), foot.offset(upright.maxX(), 2, upright.maxZ()))) {
            if (level.getBlockState(pos).getBlock() instanceof com.antcolonies.savanna.block.FallenBranchBlock) {
                helper.assertTrue(level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP),
                        "a shed branch does not lie on the ground at " + pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // cleared for the rest of the test
            }
        }
        double life = TreeLife.lifespan(kind, seed);
        helper.assertTrue(life >= 75 && life <= 125, "an umbrella thorn's lifespan is " + life);
        // It has lived nearly all its days: it dies of old age on the next one.
        t.lived = life - 0.5;
        forest.update(level, t, base + 1);
        helper.assertTrue(t.dead() && t.died == base + 1 && t.cause.equals("age"), "it did not die of old age: died " + t.died + " " + t.cause);
        helper.assertTrue(t.stage == TreeStage.LIVING && countAround(level, foot, 0, 40, TreeGrowthTests::leaves) == leafCount,
                "the moment it died it should still stand as it was: " + t.stage.id);
        // Step 5b: through the day it dies, the leaves turn brown and drop bit by bit.
        forest.update(level, t, base + 1, base + 1.5);
        int green = countAround(level, foot, 0, 40, TreeGrowthTests::livingLeaves), brown = countAround(level, foot, 0, 40, TreeGrowthTests::deadLeaves);
        helper.assertTrue(t.stage == TreeStage.LIVING && t.placedStep == TreeLife.STEPS / 2, "half a day after death: " + t.stage.id + " step " + t.placedStep);
        helper.assertTrue(green < leafCount && brown > 0 && green + brown < leafCount,
                "half a day after death: " + green + " green and " + brown + " brown leaves of " + leafCount);
        forest.update(level, t, base + 1, base + 1.95);
        int still = countAround(level, foot, 0, 40, TreeGrowthTests::leaves);
        helper.assertTrue(still == 0, "the day it died is nearly over and " + still + " leaves still hang");
        // A chicken under a limb that will break off (step 5b: a falling limb hits what is under it).
        net.minecraft.world.entity.animal.Chicken chicken = null;
        TreePlacer.Placed mudShape = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.MUD);
        TreePlacer.Placed shedShape = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.SHED);
        java.util.Set<Long> kept = new java.util.HashSet<>();
        for (int i = 0; i < shedShape.size(); i++) {
            kept.add(BlockPos.asLong(shedShape.xs()[i], shedShape.ys()[i], shedShape.zs()[i]));
        }
        for (int i = 0; i < mudShape.size() && chicken == null; i++) {
            BlockPos under = foot.offset(mudShape.xs()[i], 0, mudShape.zs()[i]);
            if (mudShape.ys()[i] < 8 || kept.contains(BlockPos.asLong(mudShape.xs()[i], mudShape.ys()[i], mudShape.zs()[i]))
                    || !level.getBlockState(under).isAir() || !level.getBlockState(under.above()).isAir() || Math.abs(mudShape.xs()[i]) + Math.abs(mudShape.zs()[i]) < 6) {
                continue;
            }
            chicken = net.minecraft.world.entity.EntityType.CHICKEN.create(level);
            chicken.moveTo(under.getX() + 0.5, under.getY(), under.getZ() + 0.5, 0, 0);
            chicken.setNoAi(true);
            level.addFreshEntity(chicken);
        }
        helper.assertTrue(chicken != null, "no place under a limb that breaks off");
        java.util.EnumSet<TreeStage> seen = java.util.EnumSet.noneOf(TreeStage.class);
        int snagWoodHigh = -1, firstMud = -1, lastMud = -1, lastHigh = Integer.MAX_VALUE, highChanges = 0;
        TreeStage before = TreeStage.LIVING;
        for (double time = base + 2; time <= base + 26; time += 0.25) {
            forest.update(level, t, (long) Math.floor(time), time);
            double daysDead = time - t.died;
            TreeStage want = TreeLife.deadStage(kind, seed, daysDead);
            int step = TreeLife.deadStep(kind, seed, daysDead);
            if (want == TreeStage.FALLEN && daysDead < TreeLife.stageFrom(kind, seed, TreeStage.FALLEN) + TreeLife.RAIN_WAIT) {
                // Step 5c: due to fall, it stands shed waiting for a storm (none in the test) for up to 3 days.
                want = TreeStage.SHED;
                step = TreeLife.fullStep(TreeStage.SHED);
            }
            double at = daysDead;
            helper.assertTrue(t.stage == want && t.placedStage == want && t.placedStep == step, at + " days after death: " + t.stage.id + " placed "
                    + t.placedStage.id + " step " + t.placedStep + ", not " + want.id + " step " + step);
            if (before == TreeStage.MUD && want != TreeStage.MUD) {
                helper.assertTrue(lastMud > 20 && firstMud < lastMud, "the mud did not climb: " + firstMud + " plates at first, " + lastMud + " at the end");
            }
            if (before == TreeStage.SHED && want != TreeStage.SHED) {
                helper.assertTrue(highChanges >= 3, "the limbs did not break off one by one: the wood up high changed " + highChanges + " times");
            }
            before = want;
            if (want == TreeStage.MUD) {
                int mud = countAround(level, foot, 0, 30, b -> b.is(com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get()));
                firstMud = firstMud < 0 ? mud : firstMud;
                lastMud = mud;
            }
            if (want == TreeStage.SHED) {
                int high = countAround(level, foot, 12, 40, TreeGrowthTests::deadWood);
                helper.assertTrue(high <= lastHigh, "dead wood up high grew back while shedding: " + high + " after " + lastHigh);
                highChanges += high < lastHigh && lastHigh != Integer.MAX_VALUE ? 1 : 0;
                lastHigh = high;
            }
            if (!seen.add(want)) {
                continue;
            }
            // The first look at each stage.
            switch (want) {
                case SNAG -> {
                    int left = countAround(level, foot, 0, 40, TreeGrowthTests::leaves);
                    helper.assertTrue(left == 0, "the snag still has " + left + " leaves");
                    int living = countAround(level, foot, 0, 40, TreeGrowthTests::livingWood);
                    helper.assertTrue(living == 0, "the snag still has " + living + " living wood blocks");
                    snagWoodHigh = countAround(level, foot, 12, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(snagWoodHigh > 50, "the snag has only " + snagWoodHigh + " dead wood blocks up high");
                }
                case SHED -> {
                    int high = countAround(level, foot, 12, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(high <= snagWoodHigh, "more dead wood up high than the snag had: " + high + " after " + snagWoodHigh);
                }
                case FALLEN -> {
                    helper.assertTrue(!chicken.isAlive() || chicken.getHealth() < chicken.getMaxHealth(), "the chicken under a breaking limb was not hit");
                    chicken.discard();
                    int fallen = countAround(level, foot, 0, 1, b -> b.getBlock() instanceof FallenBranchBlock);
                    helper.assertTrue(lastHigh < snagWoodHigh, "the limbs did not break off: " + lastHigh + " dead wood blocks up high, the snag had " + snagWoodHigh);
                    helper.assertTrue(fallen > 0 || seen.contains(TreeStage.FALLEN), "no fallen branches round the shed tree");
                    int standing = countAround(level, foot, 9, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(standing == 0, standing + " dead wood blocks still stand above the stump");
                    int log = countAround(level, foot, 0, 8, b -> deadWood(b) || b.is(TreeBlocks.ROTTEN_WOOD.get()));
                    helper.assertTrue(log > 150, "the fallen log is only " + log + " blocks");
                    // The stump: wood round the foot (an old one is hollow, frass in the middle).
                    int stump = countNear(level, foot, 4, 0, 2, b -> b.is(TreeBlocks.TREE_WOOD));
                    helper.assertTrue(stump > 4, "no stump at the foot (" + stump + " wood blocks): " + column(level, foot));
                    // Step 5c: it fell toward its heavier side, where most of the shed tree's wood hung.
                    // (A snag whose weight sits right over its stump may go any way; TreeFallTest checks many trees.)
                    double[] heavy = side(shedShape, 4, 0), lies = side(TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.FALLEN), 0, 3);
                    double a = Math.atan2(heavy[1], heavy[0]), b = Math.atan2(lies[1], lies[0]);
                    double off = Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b)));
                    helper.assertTrue(Math.hypot(heavy[0], heavy[1]) < 1 || off < Math.toRadians(45),
                            "it fell " + Math.round(Math.toDegrees(off)) + " degrees away from its heavier side");
                }
                default -> {
                }
            }
        }
        helper.assertTrue(seen.containsAll(java.util.List.of(TreeStage.SNAG, TreeStage.MUD, TreeStage.SHED, TreeStage.FALLEN)),
                "stages seen: " + seen);
        helper.assertTrue(forest.at(foot) == t, "the fallen log left the book");
        clearAbove(helper, foot);
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * Step 5c, the fall in sight: a shed umbrella thorn creaks, then its standing wood swings over drawn by block
     * displays (the blocks taken away), and crashes: the hollow log lies where the shape puts it. On the way it crushes
     * what is under it: a chicken where the log comes down dies, a glass block in its path is smashed, a stone block stays
     * (the log has a gap there). A fallen tree stays fallen.
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aFallingTreeCrushesWhatIsUnderIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long seed = 11;
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 1.0, TreeStage.SHED, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedStage == TreeStage.SHED && t.placedAge == 1.0, "the shed tree was not placed: " + t.placedStage.id);
        int standingHigh = countAround(level, foot, 9, 40, TreeGrowthTests::deadWood);
        helper.assertTrue(standingHigh > 50, "the shed tree has only " + standingHigh + " dead wood blocks up high");
        TreePlacer.Placed shed = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.SHED);
        TreePlacer.Placed lying = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.FALLEN);
        // Places on the log's line, out past the crown's middle: a chicken, a glass block and a stone block.
        java.util.Set<Long> standing = new java.util.HashSet<>();
        for (int i = 0; i < shed.size(); i++) {
            standing.add(BlockPos.asLong(shed.xs()[i], shed.ys()[i], shed.zs()[i]));
        }
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < lying.size(); i++) {
            int x = lying.xs()[i], y = lying.ys()[i], z = lying.zs()[i];
            BlockPos at = foot.offset(x, y, z);
            if (y == 1 && x * x + z * z >= 64 && x * x + z * z <= 225 && !standing.contains(BlockPos.asLong(x, y, z))
                    && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                boolean apart = true;
                for (BlockPos o : spots) {
                    apart &= o.distManhattan(at) >= 3;
                }
                if (apart) {
                    spots.add(at);
                }
            }
        }
        helper.assertTrue(spots.size() >= 3, "only " + spots.size() + " places on the log's line");
        BlockPos chickenAt = spots.get(0), glass = spots.get(1), stone = spots.get(2);
        net.minecraft.world.entity.animal.Chicken chicken = net.minecraft.world.entity.EntityType.CHICKEN.create(level);
        chicken.moveTo(chickenAt.getX() + 0.5, chickenAt.getY(), chickenAt.getZ() + 0.5, 0, 0);
        chicken.setNoAi(true);
        level.addFreshEntity(chicken);
        level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        // It comes down now, in sight (a test has no player: the fall is started as the testing command does).
        helper.assertTrue(forest.fellNow(level, t), "the shed tree would not fall");
        helper.assertTrue(!forest.fellNow(level, t), "a falling tree was made to fall again");
        helper.runAfterDelay(TreeFall.CREAK - 5, () -> {
            int high = countAround(level, foot, 9, 40, TreeGrowthTests::deadWood);
            helper.assertTrue(high == standingHigh, "the tree went before its creak was over: " + high + " of " + standingHigh);
        });
        helper.runAfterDelay(TreeFall.CREAK + 10, () -> {
            int high = countAround(level, foot, 9, 40, TreeGrowthTests::deadWood);
            helper.assertTrue(high == 0, high + " standing wood blocks left while it falls (they should be displays now)");
            int drawn = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new net.minecraft.world.phys.AABB(foot).inflate(40),
                    d -> d.getTags().contains(TreeFall.TAG)).size();
            helper.assertTrue(drawn > 50, "only " + drawn + " displays draw the falling tree");
            helper.assertTrue(forest.falling(t), "the tree is not falling");
        });
        helper.runAfterDelay(TreeFall.CREAK + TreeFall.DROP + 10, () -> {
            helper.assertTrue(!forest.falling(t) && t.placedStage == TreeStage.FALLEN && !t.toppling, "it did not land: " + t.placedStage.id);
            int drawn = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, new net.minecraft.world.phys.AABB(foot).inflate(40),
                    d -> d.getTags().contains(TreeFall.TAG)).size();
            helper.assertTrue(drawn == 0, drawn + " displays left after the crash");
            int log = countAround(level, foot, 0, 8, b -> deadWood(b) || b.is(TreeBlocks.ROTTEN_WOOD.get()));
            helper.assertTrue(log > 150, "the fallen log is only " + log + " blocks");
            helper.assertTrue(!chicken.isAlive(), "the chicken under the falling tree lived");
            helper.assertTrue(!level.getBlockState(glass).is(Blocks.GLASS), "the glass in its way was not smashed");
            helper.assertTrue(level.getBlockState(stone).is(Blocks.STONE), "the stone in its way was smashed");
            // Fallen it stays, whatever the clock says.
            forest.update(level, t, Forest.today(level));
            helper.assertTrue(t.stage == TreeStage.FALLEN && t.placedStage == TreeStage.FALLEN, "it stood up again: " + t.stage.id);
            chicken.discard();
            clearAbove(helper, foot);
            forest.forgetInside(helper.getBounds());
            helper.succeed();
        });
    }

    /**
     * The user's report 2026-10-19: fallen giants on a slope stood on walls of wood or floated. A fallen log lies on the
     * land (TreePlacer.rest): fitted over made-up land (down a slope, up one, across one, over a cliff) no part of it goes
     * down to meet the ground, every column of it sits on the ground (none hangs, none sinks more than MAX_SINK) and keeps
     * its full thickness (the user's rule, 2026-10-19), and its fallen branches lie on the ground. Then
     * a real tree falls down a real hill (its foot on the top) and up into a real bowl: no walls, little floating.
     */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aFallenTreeLiesOnTheSlopeDownhill(GameTestHelper helper) {
        BlockPos origin = BlockPos.ZERO;
        for (String kind : new String[] {"umbrella_thorn", "camel_thorn"}) {
            for (long seed = 1; seed <= 4; seed++) {
                TreePlacer.Placed flat = TreePlacer.placed(kind, seed, seed % 2 == 0 ? 1.0 : 1.2, TreeStage.FALLEN);
                helper.assertTrue(flat.lying() != null, kind + " " + seed + ": a fallen tree with no lying");
                double[] way = side(flat, 0, 3);
                double len = Math.hypot(way[0], way[1]), dx = way[0] / len, dz = way[1] / len;
                String[] names = {"down", "up", "across", "cliff"};
                TreePlacer.Ground[] lands = {
                    (x, z) -> -(int) Math.floor(Math.max(0, x * dx + z * dz) * 0.6),
                    (x, z) -> (int) Math.floor(Math.max(0, x * dx + z * dz) * 0.5),
                    (x, z) -> (int) Math.floor((-x * dz + z * dx) * 0.7),
                    (x, z) -> x * dx + z * dz > 12 ? -10 : 0,
                };
                for (int l = 0; l < lands.length; l++) {
                    String what = kind + " seed " + seed + " " + names[l];
                    TreePlacer.Ground land = lands[l];
                    TreePlacer.Placed lies = TreePlacer.rest(flat, origin, land);
                    helper.assertTrue(lies.lying() == null && TreePlacer.rest(lies, origin, land) == lies, what + ": fitted twice");
                    int[] slice = flat.lying().slice();
                    // Each column of the log: its underside and its height, before and after.
                    java.util.Map<Long, int[]> cols = new java.util.HashMap<>();
                    for (int i = 0; i < flat.size(); i++) {
                        int g = land.freeY(flat.xs()[i], flat.zs()[i]);
                        if (slice[i] == TreePlacer.LOOSE) {
                            helper.assertTrue(lies.ys()[i] == flat.ys()[i] + g, what + ": a loose piece not on the ground");
                        }
                        if (slice[i] < 0) {
                            continue;
                        }
                        helper.assertTrue(!lies.holds()[i], what + ": a lying block goes down to meet the ground");
                        long key = ((long) slice[i] << 40) ^ ((long) (flat.xs()[i] & 0xFFFFF) << 20) ^ (flat.zs()[i] & 0xFFFFF);
                        int[] c = cols.computeIfAbsent(key, k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, g});
                        c[0] = Math.min(c[0], flat.ys()[i]);
                        c[1] = Math.max(c[1], flat.ys()[i]);
                        c[2] = Math.min(c[2], lies.ys()[i]);
                        c[3] = Math.max(c[3], lies.ys()[i]);
                    }
                    helper.assertTrue(!cols.isEmpty(), what + ": no log");
                    for (int[] c : cols.values()) {
                        helper.assertTrue(c[3] - c[2] == c[1] - c[0], what + ": a column of the log lost its thickness");
                        helper.assertTrue(c[2] <= c[4], what + ": a column of the log hangs " + (c[2] - c[4]) + " over the ground");
                        helper.assertTrue(c[2] >= c[4] - TreePlacer.MAX_SINK, what + ": a column of the log sank " + (c[4] - c[2]) + " into the ground");
                    }
                }
            }
        }
        fallOnLand(helper, true);
    }

    /** The log falling up into a bowl (see {@link #aFallenTreeLiesOnTheSlopeDownhill}). */
    @GameTest(template = "arena", timeoutTicks = 400, skyAccess = true)
    public static void aFallenTreeLiesOnTheSlopeUphill(GameTestHelper helper) {
        fallOnLand(helper, false);
    }

    /**
     * A shed umbrella thorn on a hill's top ({@code hill}) or a bowl's floor falls in sight: once down, no column of its
     * log stands taller than the log is thick (no wall of wood), and none hangs over the ground (the user's rule).
     */
    private static void fallOnLand(GameTestHelper helper, boolean hill) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        for (int x = 0; x < 65; x++) {
            for (int z = 0; z < 65; z++) {
                double r = Math.hypot(x - MIDDLE, z - MIDDLE);
                int h = hill ? (int) Math.max(0, 12 - Math.floor(Math.max(0, r - 5) * 0.6)) : (int) Math.min(12, Math.floor(Math.max(0, r - 6) * 0.5));
                for (int y = 0; y < h; y++) {
                    BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE + y, z));
                    level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 2);
                    level.setBlock(at, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
            }
        }
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long seed = 11;
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE + (hill ? 12 : 0), MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 1.0, TreeStage.SHED, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedStage == TreeStage.SHED, "the shed tree was not placed");
        if (hill) {
            // The broken limbs lying round the standing dead tree, down the hill: on the ground, not on walls of wood.
            int pieces = 0;
            for (int x = -30; x <= 30; x++) {
                for (int z = -30; z <= 30; z++) {
                    if (x * x + z * z <= 49) {
                        continue;
                    }
                    int n = 0;
                    for (int y = -14; y < 0; y++) {
                        BlockState b = level.getBlockState(foot.offset(x, y, z));
                        n += deadWood(b) || b.is(TreeBlocks.ROTTEN_WOOD.get()) ? 1 : 0;
                    }
                    pieces += n;
                    helper.assertTrue(n <= 3, "a broken limb down the hill stands on " + n + " blocks of wood: " + column(level, foot.offset(x, -4, z)));
                }
            }
            helper.assertTrue(pieces > 0, "no broken limbs lie down the hill round the shed tree");
        }
        if (!hill) {
            // docs/BUGS.md 62: beside other tests the bowl's ground sometimes goes from under these three columns before the
            // log lands on them (never alone): the log then hangs there. The first tick it is gone and the tests round it.
            BlockPos[] spots = {foot.offset(5, 3, 19), foot.offset(6, 3, 19), foot.offset(5, 3, 20)};
            boolean[] seen = {false};
            helper.onEachTick(() -> {
                if (seen[0] || helper.getTick() >= TreeFall.CREAK + TreeFall.DROP + 10) {
                    return;
                }
                for (BlockPos spot : spots) {
                    if (level.getBlockState(spot).isAir() || level.getBlockState(spot.above(2)).isAir()) {
                        seen[0] = true;
                        SavannaMod.LOGGER.warn("[bowl-ground] the ground under the log went at tick {} ({}), tests within 40 blocks: {}", helper.getTick(),
                                column(level, spot), com.antcolonies.gametest.TestIsolation.describeTestsNear(spot, 40));
                        return;
                    }
                }
            });
        }
        helper.assertTrue(forest.fellNow(level, t), "the shed tree would not fall");
        helper.runAfterDelay(TreeFall.CREAK + TreeFall.DROP + 10, () -> {
            helper.assertTrue(!forest.falling(t) && t.placedStage == TreeStage.FALLEN, "it did not land: " + t.placedStage.id);
            TreePlacer.Placed flat = TreePlacer.placed("umbrella_thorn", seed, 1.0, TreeStage.FALLEN);
            int thick = flat.maxY() + 2;
            int columns = 0, tall = 0, hanging = 0, wood = 0;
            String worst = "";
            StringBuilder hangs = new StringBuilder();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -30; x <= 30; x++) {
                for (int z = -30; z <= 30; z++) {
                    if (x * x + z * z <= 36) {
                        continue; // the stump
                    }
                    int lowest = Integer.MAX_VALUE, n = 0;
                    for (int y = -16; y <= 30; y++) {
                        pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z);
                        BlockState b = level.getBlockState(pos);
                        if (deadWood(b) || b.is(TreeBlocks.ROTTEN_WOOD.get())) {
                            lowest = Math.min(lowest, y);
                            n++;
                        } else if (b.is(TreeBlocks.FRASS.get())) {
                            lowest = Math.min(lowest, y); // the hollow's floor, rotted to frass: part of the log
                        }
                    }
                    if (n == 0) {
                        continue;
                    }
                    columns++;
                    wood += n;
                    if (n > thick) {
                        tall++;
                        worst = column(level, foot.offset(x, lowest + 4, z));
                    }
                    pos.set(foot.getX() + x, foot.getY() + lowest - 1, foot.getZ() + z);
                    if (level.getBlockState(pos).isAir()) {
                        hanging++;
                        if (hanging <= 6) {
                            hangs.append(" (").append(x).append(' ').append(lowest).append(' ').append(z).append(": ").append(column(level, pos)).append(')');
                        }
                    }
                }
            }
            String where = hill ? "down the hill" : "up the bowl";
            helper.assertTrue(wood > 100, "the log " + where + " is only " + wood + " blocks");
            helper.assertTrue(tall == 0, tall + " columns of the log " + where + " are taller than the log is thick (" + thick + "), e.g. " + worst);
            helper.assertTrue(hanging == 0, hanging + " of " + columns + " columns of the log " + where + " hang over the ground:" + hangs);
            for (int x = 0; x < 65; x++) {
                for (int z = 0; z < 65; z++) {
                    for (int y = 0; y <= 50; y++) {
                        BlockPos at = helper.absolutePos(new BlockPos(x, SURFACE + y, z));
                        if (!level.getBlockState(at).isAir() && !level.getBlockState(at).is(Blocks.BARRIER)) {
                            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
            forest.forgetInside(helper.getBounds());
            helper.succeed();
        });
    }

    /**
     * Step 5d, fire: a fire next to a giant tree chars its bark black and scorches its green leaves brown, and never
     * burns them away. A young tree it touches dies. A grown tree whose trunk burns dies by its own dice for the day
     * (once a day only): an ancient one killed smoulders and its dead stages are black, not silver; a younger grown one
     * the dice spare lives on with a scar.
     */
    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true)
    public static void fireCharsBarkKillsYoungTreesAndSomeGrownOnes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long killedSeed = 0, sparedSeed = 0;
        for (long s = 1; s < 500 && (killedSeed == 0 || sparedSeed == 0); s++) {
            if (killedSeed == 0 && TreeLife.fireKills(s, base, 1.25)) {
                killedSeed = s;
            }
            if (sparedSeed == 0 && !TreeLife.fireKills(s, base, 0.6)) {
                sparedSeed = s;
            }
        }
        helper.assertTrue(TreeLife.smoulders(1.25) && !TreeLife.smoulders(1.0), "only old hollow trunks should smoulder");
        BlockPos oldFoot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        BlockPos sparedFoot = helper.absolutePos(new BlockPos(10, SURFACE, 10));
        BlockPos youngFoot = helper.absolutePos(new BlockPos(54, SURFACE, 12));
        Forest.Tree old = forest.add(new Forest.Tree("umbrella_thorn", oldFoot, killedSeed, 1.25, TreeStage.LIVING, -1, base));
        Forest.Tree spared = forest.add(new Forest.Tree("umbrella_thorn", sparedFoot, sparedSeed, 0.6, TreeStage.LIVING, -1, base));
        Forest.Tree young = forest.add(new Forest.Tree("camel_thorn", youngFoot, 3, 0.25, TreeStage.LIVING, -1, base));
        for (Forest.Tree t : List.of(old, spared, young)) {
            forest.update(level, t, base);
            helper.assertTrue(t.placedAge >= 0, "a tree was not placed");
        }
        // A fire on the grass by each trunk.
        BlockPos sparedBark = lightBy(helper, sparedFoot);
        helper.assertTrue(TreePlacer.isCharred(level.getBlockState(sparedBark)), "the bark by the fire is not charred: " + level.getBlockState(sparedBark));
        helper.assertTrue(spared.living(), "the dice should have spared this tree today");
        lightBy(helper, sparedFoot); // a second fire the same day (on another side): no second roll
        helper.assertTrue(spared.living(), "a second fire the same day rolled again");
        lightBy(helper, youngFoot);
        helper.assertTrue(forest.at(youngFoot) == null, "the young tree in the fire is still in the book");
        int youngLeft = countNear(level, youngFoot, 6, 1, 12, b -> b.is(TreeBlocks.TREE_WOOD) || b.is(TreeBlocks.TREE_LEAVES));
        helper.assertTrue(youngLeft == 0, youngLeft + " blocks of the burnt young tree are left");
        lightBy(helper, oldFoot);
        helper.assertTrue(old.dead() && "fire".equals(old.cause), "the ancient tree was not killed by the fire: " + old.cause);
        helper.assertTrue(forest.smouldering(old), "the ancient hollow trunk does not smoulder");
        // A fire under a green leaf of the spared tree (on a block, as a fire in thin air goes out at once) scorches it.
        BlockPos leaf = null;
        for (BlockPos pos : BlockPos.betweenClosed(sparedFoot.offset(-8, 3, -8), sparedFoot.offset(8, 20, 8))) {
            if (livingLeaves(level.getBlockState(pos)) && level.getBlockState(pos.below()).isAir() && level.getBlockState(pos.below(2)).isAir()) {
                leaf = pos.immutable();
                break;
            }
        }
        helper.assertTrue(leaf != null, "no green leaf over open air on the spared tree");
        level.setBlock(leaf.below(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(leaf.below(), Blocks.FIRE.defaultBlockState(), 3);
        helper.assertTrue(deadLeaves(level.getBlockState(leaf)), "the leaf by the fire was not scorched: " + level.getBlockState(leaf));
        BlockPos scar = sparedBark;
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(TreePlacer.isCharred(level.getBlockState(scar)), "the charred bark burnt away: " + level.getBlockState(scar));
            // The tree killed by fire stands black: its snag's dead wood is charred, none of it silver.
            double snag = TreeLife.stageFrom(TreePlacer.kind("umbrella_thorn"), old.seed, TreeStage.SNAG);
            forest.update(level, old, old.died + (long) Math.ceil(snag) + 1);
            helper.assertTrue(old.placedStage == TreeStage.SNAG, "the burnt tree is not a snag: " + old.placedStage.id);
            int black = countAround(level, oldFoot, 4, 40, TreePlacer::isCharred), silver = countAround(level, oldFoot, 4, 40, TreeGrowthTests::deadWood);
            helper.assertTrue(black > 50 && silver == 0, "the burnt snag has " + black + " charred and " + silver + " silver wood blocks");
            helper.assertTrue(spared.living(), "the spared tree died later");
            clearAbove(helper, oldFoot);
            clearAbove(helper, sparedFoot);
            forest.forgetInside(helper.getBounds());
            helper.succeed();
        });
    }

    /**
     * Step 5e, lightning: a strike near a big tree is pulled to its top; a scar of charred bark runs down one side of the
     * trunk, a fire is lit at its foot, and (by this tree's dice for the day) the strike kills it. A strike beyond the
     * tree's reach stays where it was.
     */
    @GameTest(template = "arena", timeoutTicks = 100, skyAccess = true)
    public static void lightningIsDrawnToABigTreeAndScarsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long seed = 0;
        for (long s = 1; s < 500 && seed == 0; s++) {
            TreeLife.Strike strike = TreeLife.strike(s, base);
            if (strike.lit() && strike.kills() && !TreeLife.fireKills(s, base, 1.0)) {
                seed = s;
            }
        }
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 1.0, "the tree was not placed");
        // Out of reach: it stays.
        BlockPos far = helper.absolutePos(new BlockPos(62, SURFACE, 62));
        net.minecraft.world.entity.LightningBolt missed = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        missed.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(far));
        level.addFreshEntity(missed);
        helper.assertTrue(missed.blockPosition().equals(far), "a strike out of reach was moved to " + missed.blockPosition());
        missed.discard();
        helper.assertTrue(t.living(), "a strike out of reach hurt the tree");
        // Within reach: drawn to the top.
        net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(foot.offset(14, 0, 9)));
        level.addFreshEntity(bolt);
        int up = bolt.blockPosition().getY() - foot.getY();
        int out = (int) Math.hypot(bolt.blockPosition().getX() - foot.getX(), bolt.blockPosition().getZ() - foot.getZ());
        bolt.discard();
        helper.assertTrue(up >= 10 && out <= 30, "the strike was not drawn to the tree's top: " + up + " up, " + out + " out");
        int scar = countNear(level, foot, 5, 0, up, TreePlacer::isCharred);
        helper.assertTrue(scar >= 5, "the scar down the trunk is only " + scar + " charred blocks");
        int fires = countNear(level, foot, 6, 0, 2, b -> b.is(Blocks.FIRE));
        helper.assertTrue(fires > 0, "no fire at the foot of the scar");
        helper.assertTrue(t.dead() && "lightning".equals(t.cause), "the strike did not kill the tree: " + t.cause);
        clearAbove(helper, foot);
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * Step 5f, termites: a grown tree is left alone until a fungus-growing termite colony's reach takes in its foot; from
     * the next day its lower trunk is hollow (rotten wood inside the bark, frass on the floor, a way in at the foot), it
     * ages two days a day, and fire and lightning kill it more surely.
     */
    @GameTest(template = "arena", timeoutTicks = 100, skyAccess = true)
    public static void termitesHollowAGrownTreeAndAgeIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, 5, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge >= 0, "the tree was not placed");
        java.util.function.Predicate<BlockState> rotten = b -> b.is(TreeBlocks.ROTTEN_WOOD.get());
        java.util.function.Predicate<BlockState> frass = b -> b.is(TreeBlocks.FRASS.get());
        double lived = t.lived;
        forest.update(level, t, base + 1);
        helper.assertTrue(!t.termites && t.lived == lived + 1, "with no termites near it aged " + (t.lived - lived) + " days in a day");
        helper.assertTrue(countNear(level, foot, 4, 0, 7, rotten) == 0, "the trunk is hollow with no termites near");
        // A termite colony 12 blocks off: its reach (24) takes in the tree's foot.
        com.antcolonies.colony.Colony colony = com.antcolonies.colony.ColonyManager.get(level)
                .createColony(com.antcolonies.savanna.SavannaSpecies.MACROTERMES, java.util.UUID.randomUUID(), foot.offset(10, 0, 7));
        lived = t.lived;
        forest.update(level, t, base + 2);
        com.antcolonies.colony.ColonyManager.get(level).removeColony(colony.getId());
        helper.assertTrue(t.termites && t.placedTermites, "the termites did not come into the tree");
        // Part 4: termites may carry heart rot in the same day (a hidden rot ages it half a day more).
        double expected = lived + 2 + (t.rotFrom != com.antcolonies.savanna.tree.HeartRot.NONE ? 0.5 : 0);
        helper.assertTrue(t.lived == expected, "a tree with termites aged " + (t.lived - lived) + " days in a day, not 2");
        int lining = countNear(level, foot, 4, 1, 7, rotten), floor = countNear(level, foot, 4, 0, 0, frass);
        helper.assertTrue(lining >= 4 && floor >= 1, "the trunk is not hollow: " + lining + " rotten wood, " + floor + " frass; " + column(level, foot));
        int open = 0;
        for (int y = 1; y <= 5; y++) {
            open += level.getBlockState(foot.above(y)).isAir() ? 1 : 0;
        }
        helper.assertTrue(open >= 3, "the trunk's middle is not hollow: " + column(level, foot.above(4)));
        // Fire and lightning kill it more surely: the same dice, twice the odds or more.
        int byFire = 0, byFireTermites = 0, byStrike = 0, byStrikeTermites = 0;
        for (long day = 0; day < 2000; day++) {
            byFire += TreeLife.fireKills(5, day, 1.0) ? 1 : 0;
            byFireTermites += TreeLife.fireKills(5, day, 1.0, true) ? 1 : 0;
            byStrike += TreeLife.strike(5, day).kills() ? 1 : 0;
            byStrikeTermites += TreeLife.strike(5, day, true).kills() ? 1 : 0;
        }
        helper.assertTrue(byFireTermites > 1.6 * byFire && byStrikeTermites > 1.6 * byStrike,
                "termites did not make fire (" + byFire + " to " + byFireTermites + ") or lightning (" + byStrike + " to " + byStrikeTermites + ") deadlier");
        clearAbove(helper, foot);
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /**
     * Dead wood for the raids, part 4, heart rot: a grown tree with the rot in it shows nothing at first but ages half again
     * as fast; then bracket fungi come out on its bark and it ages twice as fast; then its trunk is hollow (rotten wood
     * inside the bark, air in the middle) with more brackets; a bracket drops when the wood behind it is gone. The looks
     * picked 2026-10-26: the wound shows low on the trunk from the start; brackets come out as knobs and young shelves,
     * later ones grown, dusting the bark under them; a bracket grows from knob to old; a heart rot mark is the tree's own
     * wood and goes with it when it dies or burns.
     */
    @GameTest(template = "arena", timeoutTicks = 100, skyAccess = true)
    public static void heartRotShowsBracketsThenHollowsTheTrunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, 5, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge >= 0, "the tree was not placed");
        java.util.function.Predicate<BlockState> rotten = b -> b.is(TreeBlocks.ROTTEN_WOOD.get());
        java.util.function.Predicate<BlockState> bracket = b -> b.is(TreeBlocks.BRACKET_FUNGUS.get());
        // Hidden rot: nothing to see, half a day more of its life a day.
        t.rotFrom = base + 1;
        double lived = t.lived;
        forest.update(level, t, base + 1);
        helper.assertTrue(t.lived == lived + 1.5, "a tree with hidden rot aged " + (t.lived - lived) + " days in a day, not 1.5");
        helper.assertTrue(countNear(level, foot, 6, 0, 7, bracket) == 0, "brackets showed while the rot was hidden");
        helper.assertTrue(countNear(level, foot, 4, 0, 7, rotten) == 0, "the trunk is hollow while the rot is hidden");
        java.util.function.Predicate<BlockState> wound = b -> RotMarks.mark(b.getBlock()) == RotMarks.Mark.WOUND;
        java.util.function.Predicate<BlockState> spores = b -> RotMarks.mark(b.getBlock()) == RotMarks.Mark.SPORES;
        helper.assertTrue(t.woundShown && countNear(level, foot, 6, 0, 2, wound) == 1,
                "no wound low on the trunk: " + countNear(level, foot, 6, 0, 2, wound) + " (shown " + t.woundShown + ")");
        // Brackets: on the bark, facing out; two days a day.
        t.rotFrom = base + 2 - (long) com.antcolonies.savanna.tree.HeartRot.BRACKETS_FROM;
        lived = t.lived;
        forest.update(level, t, base + 2);
        helper.assertTrue(t.lived == lived + 2, "a tree with brackets aged " + (t.lived - lived) + " days in a day, not 2");
        int shown = countNear(level, foot, 6, 1, 5, bracket);
        helper.assertTrue(shown >= 1 && shown == t.brackets, "no brackets on the bark: " + shown + " (the book says " + t.brackets + ")");
        helper.assertTrue(countNear(level, foot, 4, 0, 7, rotten) == 0, "the trunk is hollow at the bracket stage");
        helper.assertTrue(countNear(level, foot, 6, 1, 5, b -> bracket.test(b) && b.getValue(BracketFungusBlock.AGE) <= BracketFungusBlock.YOUNG) == shown,
                "new brackets are not knobs or young shelves");
        // Hollow: the core rotten and open, more brackets.
        t.rotFrom = base + 3 - (long) com.antcolonies.savanna.tree.HeartRot.HOLLOW_FROM;
        forest.update(level, t, base + 3);
        helper.assertTrue(t.placedRot, "the rot-hollowed shape was not placed");
        int lining = countNear(level, foot, 4, 1, 10, rotten);
        int open = 0;
        for (int y = 1; y <= 5; y++) {
            open += level.getBlockState(foot.above(y)).isAir() ? 1 : 0;
        }
        helper.assertTrue(lining >= 4 && open >= 3, "the trunk is not hollow: " + lining + " rotten wood, " + open + " open; " + column(level, foot.above(4)));
        int more = countNear(level, foot, 6, 1, 5, bracket);
        helper.assertTrue(more > shown, "no more brackets once hollow: " + more);
        helper.assertTrue(countNear(level, foot, 6, 1, 5, b -> bracket.test(b) && b.getValue(BracketFungusBlock.AGE) >= BracketFungusBlock.GROWN) >= 1,
                "brackets out 10 days are none of them grown");
        helper.assertTrue(countNear(level, foot, 6, 0, 5, spores) >= 1, "no spore-dusted bark under a grown bracket");
        helper.assertTrue(countNear(level, foot, 6, 0, 2, wound) == 1, "the wound went when the trunk hollowed");
        // A bracket drops when the wood behind it is gone.
        BlockPos one = null;
        for (BlockPos pos : BlockPos.betweenClosed(foot.offset(-6, 1, -6), foot.offset(6, 5, 6))) {
            if (level.getBlockState(pos).is(TreeBlocks.BRACKET_FUNGUS.get())) {
                one = pos.immutable();
                break;
            }
        }
        BlockPos behind = one.relative(level.getBlockState(one).getValue(BracketFungusBlock.FACING).getOpposite());
        // It grows: a knob turns young, grown, then old, and its spores dust the bark below the wood it grows from.
        level.setBlock(behind.below(), TreeBlocks.UMBRELLA_THORN.wood().get().defaultBlockState(), 3);
        level.setBlock(one, TreeBlocks.BRACKET_FUNGUS.get().aged(level.getBlockState(one).getValue(BracketFungusBlock.FACING), BracketFungusBlock.KNOB, false), 3);
        for (int i = 0; i < 20000 && level.getBlockState(one).getValue(BracketFungusBlock.AGE) < BracketFungusBlock.OLD; i++) {
            level.getBlockState(one).randomTick(level, one, level.getRandom());
        }
        helper.assertTrue(level.getBlockState(one).getValue(BracketFungusBlock.AGE) == BracketFungusBlock.OLD, "a knob never grew old: " + level.getBlockState(one));
        helper.assertTrue(spores.test(level.getBlockState(behind.below())), "a grown bracket left the bark under it clean: " + level.getBlockState(behind.below()));
        level.setBlock(behind, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(level.getBlockState(one).isAir(), "a bracket stayed on with no wood behind it");
        // After a jump of the clock (/time add, nobody near to random-tick them) the brackets catch up (2026-10-26).
        BlockPos knob = null;
        for (BlockPos pos : BlockPos.betweenClosed(foot.offset(-6, 1, -6), foot.offset(6, 5, 6))) {
            if (level.getBlockState(pos).is(TreeBlocks.BRACKET_FUNGUS.get())) {
                knob = pos.immutable();
                break;
            }
        }
        helper.assertTrue(knob != null, "no bracket left to grow");
        level.setBlock(knob, TreeBlocks.BRACKET_FUNGUS.get().aged(level.getBlockState(knob).getValue(BracketFungusBlock.FACING), BracketFungusBlock.KNOB, false), 3);
        forest.update(level, t, base + 12);
        helper.assertTrue(level.getBlockState(knob).getValue(BracketFungusBlock.AGE) == BracketFungusBlock.OLD,
                "a bracket out 19 days did not catch up after the clock jumped: " + level.getBlockState(knob));
        // The hollow tree snaps (2026-10-26): a tall jagged hollow stump, the top lying by it, pulp spilled round.
        int heaps = forest.pulpHeaps;
        forest.kill(t, base + 12, "storm");
        t.fell = true;
        forest.update(level, t, base + 12);
        helper.assertTrue(t.placedStage == TreeStage.FALLEN, "the snapped tree is not lying: " + t.placedStage);
        java.util.function.Predicate<BlockState> wood = b -> b.is(TreeBlocks.TREE_WOOD);
        helper.assertTrue(countNear(level, foot, 3, 3, 3, wood) >= 4, "no stump 3 or more blocks tall: " + column(level, foot.above(3)));
        helper.assertTrue(countNear(level, foot, 1, 1, 3, b -> b.isAir() || b.is(TreeBlocks.FRASS.get())) >= 1, "the stump is not hollow");
        int pulp = countNear(level, foot, 50, -4, 12, b -> b.is(TreeBlocks.PULP_HEAP.get())); // the top may lie 40 out
        helper.assertTrue(pulp >= 3 && forest.pulpHeaps - heaps == pulp, "no pulp round the snapped tree: " + pulp + " (book " + (forest.pulpHeaps - heaps) + ")");
        // Killed by the rot, it does not lie for good (the user, 2026-10-26): the log crumbles to pulp, far end first, and
        // the tree's record ends.
        helper.assertTrue(com.antcolonies.savanna.tree.HeartRot.diedOfRot(t), "the storm-snapped hollow tree did not die of the rot: " + t.cause);
        t.died = (long) Forest.time(level) - 60; // long past its crumbling time
        for (int sec = 0; sec < 2000 && forest.all().contains(t); sec++) {
            forest.crumble(level, t);
        }
        helper.assertTrue(!forest.all().contains(t), "the rotted log never finished crumbling: " + t.crumbled + " columns");
        int woodLeft = countNear(level, foot, 50, 0, 12, wood);
        int pulpAfter = countNear(level, foot, 50, -4, 12, b -> b.is(TreeBlocks.PULP_HEAP.get()));
        if (woodLeft > 0) {
            StringBuilder where = new StringBuilder();
            for (BlockPos pos : BlockPos.betweenClosed(foot.offset(-50, 0, -50), foot.offset(50, 12, 50))) {
                if (wood.test(level.getBlockState(pos)) && where.length() < 600) {
                    BlockPos d = pos.subtract(foot);
                    where.append(" ").append(d.getX()).append(",").append(d.getY()).append(",").append(d.getZ()).append("=")
                            .append(level.getBlockState(pos).getBlock().getDescriptionId().replace("block.antcolonies.", ""))
                            .append("/").append(level.getBlockState(pos.below()).getBlock().getDescriptionId().replace("block.minecraft.", ""));
                }
            }
            helper.fail("wood was left where the rotted log lay: " + woodLeft + where);
        }
        helper.assertTrue(pulpAfter > pulp + 5, "the rotted log left too little pulp: " + pulpAfter + " heaps (" + pulp + " before)");
        // Any tree past its young look can rot, not only the grown (the user's report 2026-10-26); and in new land a
        // share of them already are, more of the older.
        helper.assertTrue(com.antcolonies.savanna.tree.HeartRot.canRot(new Forest.Tree("umbrella_thorn", foot, 7, 0.5, TreeStage.LIVING, -1, base))
                && !com.antcolonies.savanna.tree.HeartRot.canRot(new Forest.Tree("umbrella_thorn", foot, 7, 0.3, TreeStage.LIVING, -1, base))
                && !com.antcolonies.savanna.tree.HeartRot.canRot(new Forest.Tree("baobab", foot, 7, 1.0, TreeStage.LIVING, -1, base)),
                "the wrong trees can rot");
        int[] rotting = new int[2];
        int[] stages = new int[com.antcolonies.savanna.tree.HeartRot.Stage.values().length];
        for (int seed = 0; seed < 400; seed++) {
            for (int i = 0; i < 2; i++) {
                Forest.Tree n = new Forest.Tree("camel_thorn", foot, seed, i == 0 ? 0.5 : com.antcolonies.savanna.tree.TreeShapes.ANCIENT, TreeStage.LIVING, -1, base);
                com.antcolonies.savanna.tree.HeartRot.newLand(n, base);
                rotting[i] += n.rotFrom != com.antcolonies.savanna.tree.HeartRot.NONE ? 1 : 0;
                stages[com.antcolonies.savanna.tree.HeartRot.stage(n, base).ordinal()]++;
            }
        }
        helper.assertTrue(rotting[0] > 60 && rotting[0] < 150 && rotting[1] > 220 && rotting[1] < 310,
                "new land's rotting trees of 400: " + rotting[0] + " young-looking (about 100), " + rotting[1] + " ancient (about 270)");
        // ... at every stage, most far gone (the user, 2026-10-26: "more variety on the trees that are rotted").
        int rotted = rotting[0] + rotting[1];
        helper.assertTrue(stages[1] > rotted / 20 && stages[2] > rotted / 10 && stages[3] > rotted / 2,
                "new land's rotting trees by stage (hidden, brackets, hollow): " + stages[1] + ", " + stages[2] + ", " + stages[3]);
        // Marked wood is the tree's own, and its mark goes with it as the wood dies or burns.
        BlockState umbrellaWound = RotMarks.marked(TreeBlocks.UMBRELLA_THORN.wood().get().defaultBlockState(), RotMarks.Mark.WOUND);
        helper.assertTrue(TreePlacer.same(umbrellaWound, TreeBlocks.UMBRELLA_THORN.wood().get().defaultBlockState()), "wounded wood is not the tree's own");
        helper.assertTrue(RotMarks.keep(umbrellaWound, TreeBlocks.BLEACHED_WOOD.get().defaultBlockState()).is(TreeBlocks.ROT_BARKS.get(2).wound().get()),
                "the wound went when the tree died");
        helper.assertTrue(TreePlacer.charred(umbrellaWound).is(TreeBlocks.ROT_BARKS.get(3).wound().get()), "the wound went in a fire");
        clearAbove(helper, foot);
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }

    /** Lights a fire on open grass next to a tree's trunk (a side not yet burning); returns the trunk block beside it. */
    private static BlockPos lightBy(GameTestHelper helper, BlockPos foot) {
        ServerLevel level = helper.getLevel();
        for (int r = 1; r <= 5; r++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos at = foot.relative(d, r);
                BlockPos bark = at.relative(d.getOpposite());
                BlockState b = level.getBlockState(bark);
                if (level.getBlockState(at).isAir() && b.is(TreeBlocks.TREE_WOOD) && !TreePlacer.isCharred(b)) {
                    level.setBlock(at, Blocks.FIRE.defaultBlockState(), 3);
                    return bark;
                }
            }
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("no open grass next to unburnt bark at " + foot);
    }

    /** The mean place of a shape's wood (and mud on it) from the foot (from {@code minY} up, farther out than {@code minR}). */
    private static double[] side(TreePlacer.Placed shape, int minY, int minR) {
        double sx = 0, sz = 0, n = 0;
        for (int i = 0; i < shape.size(); i++) {
            int x = shape.xs()[i], z = shape.zs()[i];
            BlockState st = shape.states()[i];
            if (shape.ys()[i] >= minY && x * x + z * z > minR * minR && (deadWood(st) || st.is(com.antcolonies.savanna.registry.SavannaBlocks.MUD_CRUST.get()))) {
                sx += x;
                sz += z;
                n++;
            }
        }
        return n == 0 ? new double[] {0, 0} : new double[] {sx / n, sz / n};
    }

    /**
     * Step 5: a dead baobab slumps, becomes a wet fibre heap, a dried heap, and then is gone: nothing of it left and its
     * record ended. And a dead tree of new land (written in as a mud-climbed snag) gets a death day inside that stage, so
     * it goes on from there; a saved and loaded dead tree is the same.
     */
    @GameTest(template = "arena", timeoutTicks = 300, skyAccess = true)
    public static void aDeadBaobabSlumpsIntoAHeapAndIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        grassland(helper);
        Forest.shapesAtOnce = true;
        Forest forest = Forest.get(level);
        long base = Forest.today(level);
        long seed = 21;
        com.antcolonies.savanna.tree.TreeKind kind = TreePlacer.kind("baobab");
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("baobab", foot, seed, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 1.0, "the grown baobab was not placed");
        forest.kill(t, base, "test");
        java.util.EnumSet<TreeStage> seen = java.util.EnumSet.noneOf(TreeStage.class);
        int firstDry = -1, lastDry = Integer.MAX_VALUE;
        for (double time = base + 1; time <= base + 60; time += 0.5) {
            forest.update(level, t, (long) Math.floor(time), time);
            if (forest.at(foot) != t) {
                break;
            }
            double d = time - base;
            TreeStage want = TreeLife.deadStage(kind, seed, d);
            helper.assertTrue(t.stage == want && t.placedStage == want, "day " + d + ": " + t.stage.id + ", not " + want.id);
            if (want == TreeStage.HEAP_DRY) {
                // Step 5b: the dried heap shrinks from the top as it goes.
                int dry = countAround(level, foot, 0, 20, b -> b.is(TreeBlocks.BAOBAB_FIBRE.get()));
                helper.assertTrue(dry <= lastDry, "the dried heap grew: " + dry + " after " + lastDry);
                firstDry = firstDry < 0 ? dry : firstDry;
                lastDry = dry;
            }
            if (seen.add(want) && want == TreeStage.HEAP) {
                int heap = countAround(level, foot, 0, 20, b -> b.is(TreeBlocks.BAOBAB_FIBRE.get()));
                helper.assertTrue(heap > 50, "the fibre heap is only " + heap + " blocks");
                int standing = countAround(level, foot, 12, 40, b -> b.is(TreeBlocks.TREE_WOOD));
                helper.assertTrue(standing == 0, standing + " baobab wood blocks still stand over the heap");
            }
        }
        helper.assertTrue(seen.containsAll(java.util.List.of(TreeStage.SLUMP, TreeStage.HEAP, TreeStage.HEAP_DRY)), "stages seen: " + seen);
        helper.assertTrue(forest.at(foot) == null, "the dried heap was never gone");
        helper.assertTrue(lastDry < firstDry, "the dried heap did not shrink: " + firstDry + " blocks at first, " + lastDry + " at the end");
        int left = countAround(level, foot, 0, 30, b -> b.is(TreeBlocks.BAOBAB_FIBRE.get()) || b.is(TreeBlocks.TREE_WOOD));
        helper.assertTrue(left == 0, left + " blocks of the dead baobab are left");
        // A dead tree of new land: a mud-climbed snag, its death day worked out inside that stage.
        BlockPos other = helper.absolutePos(new BlockPos(10, SURFACE, 10));
        Forest.Tree snag = forest.add(new Forest.Tree("camel_thorn", other, 33, 0.8, TreeStage.MUD, -1, base));
        helper.assertTrue(snag.dead() && snag.died == Forest.DIED_UNKNOWN, "a new land snag is not dead");
        forest.update(level, snag, base);
        helper.assertTrue(snag.died <= base && snag.stage == TreeStage.MUD && snag.placedStage == TreeStage.MUD,
                "the new land snag's death day " + (snag.died - base) + " moved it to " + snag.stage.id);
        Forest.Tree copy = Forest.Tree.load(snag.save());
        helper.assertTrue(copy.died == snag.died && copy.placedStage == snag.placedStage && copy.lived == snag.lived,
                "a saved and loaded dead tree differs");
        clearAbove(helper, foot);
        forest.forgetInside(helper.getBounds());
        helper.succeed();
    }
}
