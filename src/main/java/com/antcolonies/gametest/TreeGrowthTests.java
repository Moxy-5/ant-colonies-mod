package com.antcolonies.gametest;

import static com.antcolonies.gametest.TermiteMoundTests.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.block.FallenBranchBlock;
import com.antcolonies.registry.TreeBlocks;
import com.antcolonies.tree.Forest;
import com.antcolonies.tree.TreeLife;
import com.antcolonies.tree.TreeStage;
import com.antcolonies.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
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
@GameTestHolder(AntColoniesMod.MODID)
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
        AntColoniesMod.LOGGER.info("[trees] a young tree's day: worst {} ms; a grown umbrella thorn's two shapes worked out in {} ms",
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
     * Step 5: a grown umbrella thorn that has lived its lifespan (150 to 250 days) dies of old age, and its dead stages
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
        com.antcolonies.tree.TreeKind kind = TreePlacer.kind("umbrella_thorn");
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("umbrella_thorn", foot, seed, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 1.0, "the grown tree was not placed");
        int leafCount = countAround(level, foot, 0, 40, TreeGrowthTests::leaves);
        helper.assertTrue(leafCount > 300, "the grown tree has only " + leafCount + " leaves");
        double life = TreeLife.lifespan(kind, seed);
        helper.assertTrue(life >= 150 && life <= 250, "an umbrella thorn's lifespan is " + life);
        // It has lived nearly all its days: it dies of old age on the next one.
        t.lived = life - 0.5;
        forest.update(level, t, base + 1);
        helper.assertTrue(t.dead() && t.died == base + 1 && t.cause.equals("age"), "it did not die of old age: died " + t.died + " " + t.cause);
        helper.assertTrue(t.stage == TreeStage.LIVING && countAround(level, foot, 0, 40, TreeGrowthTests::leaves) == leafCount,
                "the day it died it should still stand as it was (withering): " + t.stage.id);
        java.util.EnumSet<TreeStage> seen = java.util.EnumSet.noneOf(TreeStage.class);
        int snagWoodHigh = -1;
        for (int d = 2; d <= 25; d++) {
            forest.update(level, t, base + d);
            TreeStage want = TreeLife.deadStage(kind, seed, d - 1);
            helper.assertTrue(t.stage == want && t.placedStage == want, "day " + (d - 1) + " after death: " + t.stage.id + " placed "
                    + t.placedStage.id + ", not " + want.id);
            if (!seen.add(want)) {
                continue;
            }
            // The first day of each stage.
            switch (want) {
                case SNAG -> {
                    int left = countAround(level, foot, 0, 40, TreeGrowthTests::leaves);
                    helper.assertTrue(left == 0, "the snag still has " + left + " leaves");
                    int living = countAround(level, foot, 0, 40, TreeGrowthTests::livingWood);
                    helper.assertTrue(living == 0, "the snag still has " + living + " living wood blocks");
                    snagWoodHigh = countAround(level, foot, 12, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(snagWoodHigh > 50, "the snag has only " + snagWoodHigh + " dead wood blocks up high");
                }
                case MUD -> {
                    int mud = countAround(level, foot, 0, 30, b -> b.is(com.antcolonies.registry.ModBlocks.MUD_CRUST.get()));
                    helper.assertTrue(mud > 20, "only " + mud + " mud plates on the dead tree");
                }
                case SHED -> {
                    int high = countAround(level, foot, 12, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(high < snagWoodHigh, "the limbs did not break off: " + high + " dead wood blocks up high, the snag had " + snagWoodHigh);
                    int fallen = countAround(level, foot, 0, 1, b -> b.getBlock() instanceof FallenBranchBlock);
                    helper.assertTrue(fallen > 0, "no fallen branches round the shed tree");
                }
                case FALLEN -> {
                    int standing = countAround(level, foot, 9, 40, TreeGrowthTests::deadWood);
                    helper.assertTrue(standing == 0, standing + " dead wood blocks still stand above the stump");
                    int log = countAround(level, foot, 0, 8, b -> deadWood(b) || b.is(TreeBlocks.ROTTEN_WOOD.get()));
                    helper.assertTrue(log > 150, "the fallen log is only " + log + " blocks");
                    // The stump: wood round the foot (an old one is hollow, frass in the middle).
                    int stump = countNear(level, foot, 4, 0, 2, b -> b.is(TreeBlocks.TREE_WOOD));
                    helper.assertTrue(stump > 4, "no stump at the foot (" + stump + " wood blocks): " + column(level, foot));
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
        com.antcolonies.tree.TreeKind kind = TreePlacer.kind("baobab");
        BlockPos foot = helper.absolutePos(new BlockPos(MIDDLE, SURFACE, MIDDLE));
        Forest.Tree t = forest.add(new Forest.Tree("baobab", foot, seed, 1.0, TreeStage.LIVING, -1, base));
        forest.update(level, t, base);
        helper.assertTrue(t.placedAge == 1.0, "the grown baobab was not placed");
        forest.kill(t, base, "test");
        java.util.EnumSet<TreeStage> seen = java.util.EnumSet.noneOf(TreeStage.class);
        for (int d = 1; d <= 60; d++) {
            forest.update(level, t, base + d);
            if (forest.at(foot) != t) {
                break;
            }
            TreeStage want = TreeLife.deadStage(kind, seed, d);
            helper.assertTrue(t.stage == want && t.placedStage == want, "day " + d + ": " + t.stage.id + ", not " + want.id);
            if (seen.add(want) && want == TreeStage.HEAP) {
                int heap = countAround(level, foot, 0, 20, b -> b.is(TreeBlocks.BAOBAB_FIBRE.get()));
                helper.assertTrue(heap > 50, "the fibre heap is only " + heap + " blocks");
                int standing = countAround(level, foot, 12, 40, b -> b.is(TreeBlocks.TREE_WOOD));
                helper.assertTrue(standing == 0, standing + " baobab wood blocks still stand over the heap");
            }
        }
        helper.assertTrue(seen.containsAll(java.util.List.of(TreeStage.SLUMP, TreeStage.HEAP, TreeStage.HEAP_DRY)), "stages seen: " + seen);
        helper.assertTrue(forest.at(foot) == null, "the dried heap was never gone");
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
