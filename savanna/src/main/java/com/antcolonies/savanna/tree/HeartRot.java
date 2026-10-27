package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.antcolonies.savanna.block.BracketFungusBlock;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.antcolonies.savanna.worldgen.TreePlacer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dead wood for the raids, part 4 (docs/plans/dead-wood-for-raids.md): heart rot, trees rotting from the inside. Real: a
 * trunk's core (heartwood) is already dead wood; heart rot fungi (<i>Ganoderma</i>, <i>Phellinus</i>) get in through
 * wounds (a broken limb, a fire scar, a lightning scar, termite galleries) and eat the core while the tree stays green.
 * Shelf fungi on the bark show it is far along; a hollow tree lives on, weaker, shedding limbs, until it dies young or
 * a storm brings it down. So a tree with fire, lightning and termites round it dies young, an untouched one grows old.
 * <ol>
 * <li><b>Infection</b> ({@link Forest.Tree#rotFrom}, the day it got in): a chance from each wound: a shed limb, a fire on
 * the trunk it lived through, a lightning strike it lived through, termites in it (each day), old age (each day).</li>
 * <li><b>Hidden rot</b> (from day 0): only the wound shows, a sunken scar low on the trunk beaded with gum
 * ({@link RotMarks.Mark#WOUND}); the tree ages half again as fast.</li>
 * <li><b>Brackets</b> (from {@link #BRACKETS_FROM}): shelf fungi on the trunk's bark, growing from a white knob to an old
 * spore-dusted hoof ({@link BracketFungusBlock#AGE}), dusting the bark under them; it ages twice as fast.</li>
 * <li><b>Hollow core</b> (from {@link #HOLLOW_FROM}): the trunk is a shell up to the fork round a core of rotten wood and
 * air, open through one limb hole ({@code TreeShapes.hollowOut}); more brackets; fire and lightning kill it as surely as
 * a termite-hollowed tree; it sheds limbs twice as often, and a thunderstorm may kill it outright.</li>
 * <li><b>The end:</b> its own dead stages (step 5): withering, the snag, the fall, the hollow log.</li>
 * </ol>
 * Baobabs are left out: they hollow with age on their own (already in their shapes). All numbers my picks.
 */
public final class HeartRot {
    /** {@link Forest.Tree#rotFrom}: no heart rot. */
    public static final long NONE = Long.MIN_VALUE;
    /** Days after the rot gets in that the brackets show, and the core is hollow (my picks: "a few days" each). */
    public static final double BRACKETS_FROM = 6, HOLLOW_FROM = 16;
    /** The chance each wound lets the rot in (my picks): a shed limb, a fire lived through, a lightning strike lived through. */
    static final double SHED_CHANCE = 1 / 80.0, FIRE_CHANCE = 0.5, LIGHTNING_CHANCE = 0.75;
    /** The chance a day: termites in the tree (they carry it in), and an ancient tree (by how old, from just grown). */
    static final double TERMITE_CHANCE = 0.1, OLD_AGE_CHANCE = 0.01;
    /** How many brackets show on the bark: once they show, and once hollow. */
    static final int BRACKETS_SHOWN = 2, BRACKETS_HOLLOW = 4;
    /** Each second of a thunderstorm over a living hollow tree: the chance it is killed (my pick: about 2 minutes of thunder). */
    static final int STORM_ONE_IN = 2400;
    /**
     * Of the living grown-looking trees in new land, the share already rotting: {@code NEW_LAND_SHARE} at the end of the
     * young look, rising to {@code NEW_LAND_SHARE_OLD} at ancient (my picks, 2026-10-26; real: most old savanna acacias
     * have some heart rot, as every wound over decades may let it in).
     */
    static final double NEW_LAND_SHARE = 0.25, NEW_LAND_SHARE_OLD = 2 / 3.0;
    /** A rotting tree of new land got the rot up to this many days before (so some are already hollow). */
    static final double NEW_LAND_DAYS = 30;

    public enum Stage { NONE, HIDDEN, BRACKETS, HOLLOW }

    private HeartRot() {
    }

    /**
     * Whether a tree can get heart rot: an acacia past its young look, with a trunk thick enough to have heartwood (the
     * little ones die of other things; baobabs hollow on their own). Until 2026-10-26 only fully grown ones (age 1).
     */
    public static boolean canRot(Forest.Tree t) {
        return t.age >= TreeLife.YOUNG_END && !t.treeKind().isBaobab();
    }

    /** How far from the end of the young look (0) to ancient (1) a tree is. */
    static double maturity(double age) {
        return Math.clamp((age - TreeLife.YOUNG_END) / (TreeShapes.ANCIENT - TreeLife.YOUNG_END), 0, 1);
    }

    /** How far the rot is on this day. */
    public static Stage stage(Forest.Tree t, double day) {
        if (t.rotFrom == NONE) {
            return Stage.NONE;
        }
        double days = day - t.rotFrom;
        return days >= HOLLOW_FROM ? Stage.HOLLOW : days >= BRACKETS_FROM ? Stage.BRACKETS : Stage.HIDDEN;
    }

    /** Whether the trunk is hollow by now (its shape is the rotted one). */
    public static boolean hollow(Forest.Tree t) {
        return stage(t, t.lastDay) == Stage.HOLLOW;
    }

    /** The days of life a rotting tree uses up each day over a sound one's 1. */
    public static double extraAgeing(Forest.Tree t) {
        return switch (stage(t, t.lastDay)) {
            case NONE -> 0;
            case HIDDEN -> 0.5;
            default -> 1;
        };
    }

    /** A wound with this chance of letting the rot in, on this day. Returns whether it got in now. */
    public static boolean wound(Forest.Tree t, long day, double chance, Random dice) {
        if (t.rotFrom != NONE || !t.living() || !canRot(t) || dice.nextDouble() >= chance) {
            return false;
        }
        t.rotFrom = day;
        return true;
    }

    /** Each day a tree lives: the rot may get in with the termites or with old age. */
    static boolean daily(Forest.Tree t, long day) {
        double chance = (t.termites ? TERMITE_CHANCE : 0) + OLD_AGE_CHANCE * maturity(t.age);
        return wound(t, day, chance, TreeLife.dice(t.seed, day * 53 + 29));
    }

    /** A tree of new land, written into the book today: an old one may be rotting already. */
    public static void newLand(Forest.Tree t, long today) {
        if (!t.living() || !canRot(t)) {
            return;
        }
        Random dice = TreeLife.dice(t.seed, -21);
        double share = NEW_LAND_SHARE + (NEW_LAND_SHARE_OLD - NEW_LAND_SHARE) * maturity(t.age);
        if (dice.nextDouble() < share) {
            t.rotFrom = today - (long) (dice.nextDouble() * NEW_LAND_DAYS);
        }
    }

    /** Whether a thunderstorm kills a living hollow tree this second. */
    static boolean stormKills(ServerLevel level, Forest.Tree t) {
        return t.living() && hollow(t) && level.isThundering() && level.getBiome(t.foot).value().hasPrecipitation()
                && level.getRandom().nextInt(STORM_ONE_IN) == 0;
    }

    /** How many brackets should be on the bark by now. */
    static int bracketsWanted(Forest.Tree t) {
        return switch (stage(t, t.lastDay)) {
            case BRACKETS -> BRACKETS_SHOWN;
            case HOLLOW -> BRACKETS_HOLLOW;
            default -> 0;
        };
    }

    /** Days a bracket spends at each age before the next (as {@link BracketFungusBlock}'s growing, my picks). */
    static final double[] DAYS_AT_AGE = {1, 3, 10};

    /** The age of a bracket that came out {@code days} ago (a tree of new land, or one rotting while nobody was near). */
    static int ageAfter(double days) {
        int age = 0;
        double until = 0;
        while (age < DAYS_AT_AGE.length && days >= (until += DAYS_AT_AGE[age])) {
            age++;
        }
        return age;
    }

    /**
     * Puts up to {@code count} brackets on the bark of the tree standing as {@code shape}: on the outside of its lower
     * trunk (1 to 5 blocks up), in air, facing out, as old as brackets out for about {@code days} days (a few days more or
     * less each); grown ones may have tiers and dust the bark under them. Returns how many were placed.
     */
    static int placeBrackets(ServerLevel level, BlockPos foot, TreePlacer.Placed shape, int count, double days, Random dice) {
        List<BlockPos> spots = new ArrayList<>();
        List<Direction> faces = new ArrayList<>();
        for (int i = 0; i < shape.size(); i++) {
            int x = shape.xs()[i], y = shape.ys()[i], z = shape.zs()[i];
            if (y < 1 || y > 5 || x * x + z * z > 25 || !shape.states()[i].is(TreeBlocks.TREE_WOOD)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d.getStepX() * x + d.getStepZ() * z < 0) {
                    continue; // only out from the trunk's middle
                }
                BlockPos wood = foot.offset(x, y, z), at = wood.relative(d);
                if (BracketFungusBlock.holdsOn(level.getBlockState(wood)) && level.getBlockState(at).isAir()) {
                    spots.add(at);
                    faces.add(d);
                }
            }
        }
        int placed = 0;
        while (placed < count && !spots.isEmpty()) {
            int i = dice.nextInt(spots.size());
            BlockPos at = spots.remove(i);
            Direction d = faces.remove(i);
            if (level.getBlockState(at).isAir()) {
                int age = ageAfter(days + (dice.nextDouble() - 0.5) * 6);
                BlockState bracket = TreeBlocks.BRACKET_FUNGUS.get().aged(d, age, dice.nextDouble() < 1 / 3.0);
                level.setBlock(at, bracket, Block.UPDATE_ALL);
                if (age >= BracketFungusBlock.GROWN) {
                    BracketFungusBlock.dust(level, at, bracket);
                }
                placed++;
            }
        }
        return placed;
    }

    /**
     * The brackets on the bark of the tree standing as {@code shape} catch up with the clock: each grows to the age of a
     * bracket out about {@code days} days (a few days more or less each, by where it sits), never younger than it is; a
     * grown one dusts the bark under it. Brackets grow by themselves only while near a player ({@link BracketFungusBlock}'s
     * random ticks), so after the clock jumps ({@code /time add}) or the land was away they are brought up to date here.
     */
    static void ageBrackets(ServerLevel level, BlockPos foot, TreePlacer.Placed shape, double days) {
        for (int i = 0; i < shape.size(); i++) {
            int x = shape.xs()[i], y = shape.ys()[i], z = shape.zs()[i];
            if (y < 1 || y > 5 || x * x + z * z > 25 || !shape.states()[i].is(TreeBlocks.TREE_WOOD)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos at = foot.offset(x, y, z).relative(d);
                BlockState there = level.getBlockState(at);
                if (!there.is(TreeBlocks.BRACKET_FUNGUS.get()) || there.getValue(BracketFungusBlock.FACING) != d) {
                    continue;
                }
                int age = ageAfter(days + (new Random(at.asLong()).nextDouble() - 0.5) * 6);
                if (age > there.getValue(BracketFungusBlock.AGE)) {
                    BlockState grown = there.setValue(BracketFungusBlock.AGE, age);
                    level.setBlock(at, grown, Block.UPDATE_ALL);
                    if (age >= BracketFungusBlock.GROWN) {
                        BracketFungusBlock.dust(level, at, grown);
                    }
                }
            }
        }
    }

    /** Whether a dead tree, now lying, came down snapped at its rotted hollow (its fallen shape is the snapped one). */
    static boolean snapped(Forest.Tree t) {
        return !t.treeKind().isBaobab() && hollow(t);
    }

    /**
     * A hollow tree just snapped and its top landed (lying as {@code lying}): the rotten heartwood spills out as pulp
     * heaps on the ground, 4 to 7 round the stump's foot (2 to 6 layers) and 2 to 4 round the far end of the lying top
     * (1 to 4 layers), my picks. Returns how many heaps.
     */
    static int spillPulp(ServerLevel level, BlockPos foot, TreePlacer.Placed lying, Random dice) {
        double stump = 0, far = 0;
        int farI = -1;
        for (int i = 0; i < lying.size(); i++) {
            if (!lying.states()[i].is(TreeBlocks.TREE_WOOD)) {
                continue;
            }
            double d = Math.hypot(lying.xs()[i], lying.zs()[i]);
            if (lying.ys()[i] == 1 && d < 6) {
                stump = Math.max(stump, d);
            }
            if (d > far) {
                far = d;
                farI = i;
            }
        }
        int heaps = spill(level, foot, stump + 1, stump + 3, 4 + dice.nextInt(4), 2, 6, dice);
        if (farI >= 0 && far > stump + 4) {
            heaps += spill(level, foot.offset(lying.xs()[farI], lying.ys()[farI], lying.zs()[farI]), 0.8, 2.5, 2 + dice.nextInt(3), 1, 4, dice);
        }
        return heaps;
    }

    private static int spill(ServerLevel level, BlockPos centre, double from, double to, int count, int least, int most, Random dice) {
        int placed = 0;
        for (int tries = 0; tries < count * 5 && placed < count; tries++) {
            double a = dice.nextDouble() * Math.PI * 2, d = from + dice.nextDouble() * (to - from);
            int x = centre.getX() + (int) Math.round(Math.cos(a) * d), z = centre.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = centre.getY() + 3; y >= centre.getY() - 4; y--) {
                BlockPos at = new BlockPos(x, y, z);
                BlockState there = level.getBlockState(at);
                if (!there.isAir() && !there.canBeReplaced()) {
                    break; // wood or ground here: no room on this spot
                }
                BlockState under = level.getBlockState(at.below());
                if (under.is(TreeBlocks.TREE_WOOD) || under.is(TreeBlocks.PULP_HEAP.get())) {
                    break; // it falls to the ground, not onto the log
                }
                if (under.isFaceSturdy(level, at.below(), Direction.UP)) {
                    level.setBlock(at, TreeBlocks.PULP_HEAP.get().withLayers(least + dice.nextInt(most - least + 1)), Block.UPDATE_ALL);
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }

    /**
     * The wound where the rot got in: a log of the tree standing as {@code shape} low on its trunk (0 to 2 blocks up),
     * with open air in front of it, turns to its wound look. Returns whether one did (false: no such log, try again later).
     */
    static boolean placeWound(ServerLevel level, BlockPos foot, TreePlacer.Placed shape, Random dice) {
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < shape.size(); i++) {
            int x = shape.xs()[i], y = shape.ys()[i], z = shape.zs()[i];
            if (y < 0 || y > 2 || x * x + z * z > 25) {
                continue;
            }
            BlockPos wood = foot.offset(x, y, z);
            BlockState there = level.getBlockState(wood);
            if (RotMarks.marked(there, RotMarks.Mark.WOUND) == null) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (d.getStepX() * x + d.getStepZ() * z >= 0 && level.getBlockState(wood.relative(d)).isAir()) {
                    spots.add(wood);
                    break;
                }
            }
        }
        if (spots.isEmpty()) {
            return false;
        }
        BlockPos at = spots.get(dice.nextInt(spots.size()));
        level.setBlock(at, RotMarks.marked(level.getBlockState(at), RotMarks.Mark.WOUND), Block.UPDATE_ALL);
        return true;
    }
}
