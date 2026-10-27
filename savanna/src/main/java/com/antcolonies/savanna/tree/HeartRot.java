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
    /** Of the living grown trees in new land, the share already rotting is this times how old (0 just grown, 1 ancient). */
    static final double NEW_LAND_SHARE = 0.6;
    /** A rotting tree of new land got the rot up to this many days before (so some are already hollow). */
    static final double NEW_LAND_DAYS = 30;

    public enum Stage { NONE, HIDDEN, BRACKETS, HOLLOW }

    private HeartRot() {
    }

    /** Whether a tree can get heart rot: a grown acacia (the young ones die of other things; baobabs hollow on their own). */
    public static boolean canRot(Forest.Tree t) {
        return t.age >= TreeShapes.GROWN && !t.treeKind().isBaobab();
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
        double chance = (t.termites ? TERMITE_CHANCE : 0) + OLD_AGE_CHANCE * TreeLife.old(t.age);
        return wound(t, day, chance, TreeLife.dice(t.seed, day * 53 + 29));
    }

    /** A tree of new land, written into the book today: an old one may be rotting already. */
    static void newLand(Forest.Tree t, long today) {
        if (!t.living() || !canRot(t)) {
            return;
        }
        Random dice = TreeLife.dice(t.seed, -21);
        if (dice.nextDouble() < NEW_LAND_SHARE * TreeLife.old(t.age)) {
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
