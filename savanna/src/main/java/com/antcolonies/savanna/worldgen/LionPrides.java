package com.antcolonies.savanna.worldgen;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.antcolonies.animal.Sex;
import com.antcolonies.savanna.animal.LionEntity;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.tree.TreeStage;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * New land's lion prides (2026-11-25, {@code docs/plans/lion.md}, step 1): one pan in three ({@link #ONE_IN}, a dice roll
 * from the pan's middle) also gets a pride, laid right after its buffalo herd and kept for good (the world's spawner never
 * makes lions). Pans are about one to every 192 by 192 blocks, so a pride about every 330 blocks of big savanna, rarer than
 * the herds, as real lions are.
 * <ul>
 * <li>A pride: one male (one pride in four two brothers), {@link #LIONESSES_FEWEST} to {@link #LIONESSES_MOST} lionesses,
 * and in two prides out of three {@link #CUBS_FEWEST} to {@link #CUBS_MOST} cubs (one fewer at most with two males): at
 * most 9. The first lioness laid is the elder.</li>
 * <li>Its den: the shade of the living savanna tree nearest the pan whose foot lies {@link #DEN_NEAREST} to
 * {@link #DEN_FARTHEST} from the pan's middle (past the buffalo's farthest grazing), read from the tree starts directly
 * (chunks within {@link #TREE_LOOK} of the pan's, so a tree whose foot is in the ring but whose start lies up to 24 blocks off is seen, still inside the 8 chunks a feature may read; never the references beyond the 3 by 3, which crash worldgen); the den is
 * the foot plus the trunk's half-width plus {@link #FROM_TRUNK}, on the side away from the pan, and the pride lies facing
 * away from the trunk. No such tree: an open spot {@link #OPEN_DEN} from the pan's middle at a dice angle. Only the spot is
 * saved; its ground is checked in game.</li>
 * <li>The lions are laid on spots like the herd's (on grass, the whole body clear, inside the chunks the pan may write),
 * at least {@link #TREE_MARGIN} from the ground a tree will cover, {@link #FROM_BUFFALO} from every buffalo laid there and
 * {@link #APART} from each other, the spots nearest the den first; they then walk to the den. Too few spots: fewer cubs,
 * then fewer lionesses (never fewer than one male and two), else no pride.</li>
 * </ul>
 * All numbers my picks.
 */
public final class LionPrides {
    static final int ONE_IN = 3;
    static final int LIONESSES_FEWEST = 2, LIONESSES_MOST = 4, CUBS_FEWEST = 2, CUBS_MOST = 4;
    static final double DEN_NEAREST = 46.0, DEN_FARTHEST = 64.0, OPEN_DEN = 52.0, FROM_TRUNK = 4.0;
    static final int TREE_LOOK = 6, TREE_MARGIN = 3, EDGE = 3;
    static final double FROM_BUFFALO = 3.0, APART = 4.0;
    private static final long PRIDE_DICE = 0x5DEECE66DL;

    private LionPrides() {
    }

    /** What was laid: how many lions, how many spots there were, its den and the way it lies there, and whether a tree gives the shade. */
    public record Pride(int lions, int spots, @Nullable BlockPos den, float denYaw, boolean tree) {
    }

    /** Whether a pan at this middle has a pride (one in {@link #ONE_IN}, from its middle). */
    public static boolean hasPride(BlockPos panMiddle) {
        return RandomSource.create(panMiddle.asLong() ^ PRIDE_DICE).nextInt(ONE_IN) == 0;
    }

    /** Lays a pride by the pan just laid, if it has one ({@link #hasPride}); the herd's spots are kept clear of. */
    @Nullable
    public static Pride lay(WorldGenLevel level, RandomSource random, PanFeature.Laid pan, int[] bounds, OxbowFeature.Keepout keepout,
                            List<BlockPos> buffalo) {
        if (!hasPride(pan.middle())) {
            return null;
        }
        return layPride(level, random, pan, bounds, keepout, buffalo);
    }

    /** Lays a pride by a pan whatever the dice say (also the game tests' way in, on a built pan). */
    public static Pride layPride(WorldGenLevel level, RandomSource random, PanFeature.Laid pan, int[] bounds, OxbowFeature.Keepout keepout,
                                 List<BlockPos> buffalo) {
        BlockPos middle = pan.middle();
        int low = middle.getY();
        // its den
        BlockPos den = null;
        float denYaw = 0;
        boolean tree = false;
        double bestTree = Double.MAX_VALUE;
        int pcx = middle.getX() >> 4, pcz = middle.getZ() >> 4;
        for (int cx = pcx - TREE_LOOK; cx <= pcx + TREE_LOOK; cx++) {
            for (int cz = pcz - TREE_LOOK; cz <= pcz + TREE_LOOK; cz++) {
                for (StructureStart start : level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS).getAllStarts().values()) {
                    if (start == null || !start.isValid()) {
                        continue;
                    }
                    for (StructurePiece piece : start.getPieces()) {
                        BlockPos foot = null;
                        double half = 1.0;
                        if (piece instanceof GiantTreePiece g && g.pick().stage() == TreeStage.LIVING && !GiantTreePiece.fallenYoung(g.pick())) {
                            foot = g.foot();
                            half = TreePlacer.kind(g.pick().kind()).trunk().radius();
                        } else if (piece instanceof FloodplainTreePiece f) {
                            foot = f.tree().foot();
                        }
                        if (foot == null) {
                            continue;
                        }
                        double dx = foot.getX() - middle.getX(), dz = foot.getZ() - middle.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                        if (d < DEN_NEAREST || d > DEN_FARTHEST || d >= bestTree) {
                            continue;
                        }
                        bestTree = d;
                        double out = half + FROM_TRUNK;
                        den = new BlockPos(Mth.floor(foot.getX() + 0.5 + dx / d * out), foot.getY(), Mth.floor(foot.getZ() + 0.5 + dz / d * out));
                        denYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;     // lying facing away from the trunk
                        tree = true;
                    }
                }
            }
        }
        if (den == null) {
            RandomSource dice = RandomSource.create(middle.asLong() ^ PRIDE_DICE * 31);
            double a = dice.nextDouble() * Math.PI * 2.0;
            den = new BlockPos(Mth.floor(middle.getX() + 0.5 + Math.cos(a) * OPEN_DEN), low, Mth.floor(middle.getZ() + 0.5 + Math.sin(a) * OPEN_DEN));
            denYaw = dice.nextFloat() * 360.0F;
        }
        // who
        int males = random.nextInt(4) == 0 ? 2 : 1;
        int lionesses = LIONESSES_FEWEST + random.nextInt(LIONESSES_MOST - LIONESSES_FEWEST + 1);
        int cubs = random.nextInt(3) < 2 ? CUBS_FEWEST + random.nextInt((males == 2 ? CUBS_MOST - 1 : CUBS_MOST) - CUBS_FEWEST + 1) : 0;
        // where they are laid: spots like the herd's, nearest the den first
        List<BoundingBox> trees = BuffaloHerds.treeReach(level, bounds);
        List<BlockPos> spots = new ArrayList<>();
        for (int x = bounds[0] + EDGE; x <= bounds[2] - EDGE; x++) {
            for (int z = bounds[1] + EDGE; z <= bounds[3] - EDGE; z++) {
                if (nearTree(trees, x, z) || water(level, x, z, low)) {
                    continue;
                }
                BlockPos stand = BuffaloHerds.standSpot(level, x, z, low, keepout);
                if (stand != null && buffalo.stream().allMatch(b -> flat(b, stand) >= FROM_BUFFALO)) {
                    spots.add(stand);
                }
            }
        }
        final BlockPos toward = den;
        spots.sort((a, b) -> Double.compare(flat(a, toward), flat(b, toward)));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos s : spots) {
            if (chosen.size() >= males + lionesses + cubs) {
                break;
            }
            if (chosen.stream().allMatch(q -> flat(q, s) >= APART)) {
                chosen.add(s);
            }
        }
        int room = chosen.size();
        if (room < males + 2) {
            return new Pride(0, spots.size(), den, denYaw, tree);
        }
        cubs = Math.min(cubs, Math.max(0, room - males - lionesses));
        lionesses = Math.min(lionesses, room - males - cubs);
        // the lionesses first (the first the elder), the males, the cubs
        List<EntityType<LionEntity>> who = new ArrayList<>();
        for (int i = 0; i < lionesses; i++) {
            who.add(SavannaEntities.LIONESS.get());
        }
        for (int i = 0; i < males; i++) {
            who.add(SavannaEntities.LION.get());
        }
        for (int i = 0; i < cubs; i++) {
            who.add(SavannaEntities.LION_CUB.get());
        }
        int laid = 0;
        int sinceMeal = random.nextInt(com.antcolonies.savanna.animal.LionHunt.HUNGRY);
        for (int i = 0; i < who.size(); i++) {
            LionEntity l = who.get(i).create(level.getLevel());
            if (l == null) {
                continue;
            }
            BlockPos p = chosen.get(i);
            float yaw = (float) Math.toDegrees(Math.atan2(den.getZ() - p.getZ(), den.getX() - p.getX())) - 90.0F;
            l.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0.0F);
            l.setYBodyRot(yaw);
            l.setYHeadRot(yaw);
            l.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.CHUNK_GENERATION, null);
            l.rollCoat(random);                         // the pride's own dice, not the world's
            l.setSinceMeal(sinceMeal);                  // the whole pride ate together, 0 to 2 days ago
            l.life().setUp(false, l.form() == LionEntity.Form.MALE ? Sex.MALE : l.form() == LionEntity.Form.LIONESS ? Sex.FEMALE : null, random);
            l.life().apply();
            l.setDen(den, denYaw, middle, pan.across());
            if (i == 0) {
                l.setElder(true);
            }
            level.addFreshEntity(l);
            laid++;
        }
        return new Pride(laid, spots.size(), den, denYaw, tree);
    }

    /** Whether x, z is within {@link #TREE_MARGIN} (side on) of the ground a tree will cover (a lion is drawn 4.66 long). */
    private static boolean nearTree(List<BoundingBox> trees, int x, int z) {
        for (BoundingBox b : trees) {
            if (x >= b.minX() - TREE_MARGIN && x <= b.maxX() + TREE_MARGIN && z >= b.minZ() - TREE_MARGIN && z <= b.maxZ() + TREE_MARGIN) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the 5 by 5 columns round x, z holds the pan's water at its level. */
    private static boolean water(WorldGenLevel level, int x, int z, int low) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (level.getFluidState(new BlockPos(x + dx, low, z + dz)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A pride as words for the log. */
    static String words(@Nullable Pride pride) {
        if (pride == null) {
            return "no pride (two pans in three)";
        }
        return pride.lions() <= 0 ? "no room for a pride (" + pride.spots() + " spots)"
                : "a pride of " + pride.lions() + " lions, den at " + pride.den().toShortString() + (pride.tree() ? " in a tree's shade" : " in the open");
    }
}
