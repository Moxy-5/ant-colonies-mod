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
}
