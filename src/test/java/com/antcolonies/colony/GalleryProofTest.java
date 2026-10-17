package com.antcolonies.colony;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.antcolonies.nav.AntPathFinder;
import com.antcolonies.nav.CellMap;
import com.antcolonies.nav.Foothold;
import com.antcolonies.nav.Route;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The termites-underground safety net, step 0 (the user's plan of 2026-10-17, {@code docs/plans/termites-underground.md}):
 * proves on hundreds of made-up landscapes (rolling hills, cliffs, stone, ponds, caves) that every foraging tunnel the
 * route planner ({@link TunnelRoute}) plans can really be used, like the mound's "never stuck" proof: the real route
 * finder ({@link AntPathFinder}, with the real search budget) walks a termite from the tunnel's start out of its hole
 * and back, and the tunnel never opens to the open air on a hillside (a cave beside it is allowed: the nest walls
 * plaster it shut, {@link NestWalls}). A failure names the landscape's seed and the block.
 */
class GalleryProofTest {
    private static final int LANDSCAPES = 400;
    private static final int SIZE = 60;

    /** One made-up landscape: the top ground block of each column, stone columns, pond columns and cave cells. */
    private static final class Landscape {
        final int[][] height = new int[2 * SIZE + 1][2 * SIZE + 1];
        final Set<Long> stone = new HashSet<>();
        final Set<Long> water = new HashSet<>();
        final Set<BlockPos> caves = new HashSet<>();
        final Set<BlockPos> dug = new HashSet<>();

        Landscape(long seed) {
            Random random = new Random(seed);
            // Rolling hills: a few random bumps, some steep.
            int bumps = 2 + random.nextInt(5);
            double[][] bump = new double[bumps][4];
            for (double[] b : bump) {
                b[0] = random.nextInt(2 * SIZE) - SIZE;
                b[1] = random.nextInt(2 * SIZE) - SIZE;
                b[2] = 4 + random.nextInt(20); // width
                b[3] = random.nextInt(11) - 4; // height (dips too)
            }
            boolean terraces = random.nextInt(3) == 0;
            for (int x = -SIZE; x <= SIZE; x++) {
                for (int z = -SIZE; z <= SIZE; z++) {
                    double h = 64;
                    for (double[] b : bump) {
                        double d = Math.hypot(x - b[0], z - b[1]) / b[2];
                        h += b[3] * Math.exp(-d * d);
                    }
                    if (terraces) {
                        h = Math.floor(h / 3) * 3; // steps of 3: cliffs the tunnel must go round
                    }
                    height[x + SIZE][z + SIZE] = (int) Math.round(h);
                }
            }
            // Stone outcrops, ponds and caves, away from the nest's start.
            for (int i = random.nextInt(6); i > 0; i--) {
                blob(random, stone, 1 + random.nextInt(4));
            }
            for (int i = random.nextInt(3); i > 0; i--) {
                blob(random, water, 1 + random.nextInt(3));
            }
            for (int i = random.nextInt(5); i > 0; i--) {
                int cx = random.nextInt(2 * SIZE - 10) - SIZE + 5;
                int cz = random.nextInt(2 * SIZE - 10) - SIZE + 5;
                if (Math.abs(cx) + Math.abs(cz) < 6) {
                    continue;
                }
                int depth = 1 + random.nextInt(4);
                int length = 2 + random.nextInt(8);
                Direction way = Direction.from2DDataValue(random.nextInt(4));
                for (int k = 0; k < length; k++) {
                    int x = cx + way.getStepX() * k;
                    int z = cz + way.getStepZ() * k;
                    if (inside(x, z)) {
                        caves.add(new BlockPos(x, top(x, z) - depth, z));
                    }
                }
            }
        }

        private void blob(Random random, Set<Long> into, int radius) {
            int cx = random.nextInt(2 * SIZE) - SIZE;
            int cz = random.nextInt(2 * SIZE) - SIZE;
            if (Math.abs(cx) + Math.abs(cz) < 6 + radius) {
                return;
            }
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (inside(x, z) && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= radius * radius) {
                        into.add(column(x, z));
                    }
                }
            }
        }

        boolean inside(int x, int z) {
            return Math.abs(x) <= SIZE && Math.abs(z) <= SIZE;
        }

        int top(int x, int z) {
            return height[Math.clamp(x, -SIZE, SIZE) + SIZE][Math.clamp(z, -SIZE, SIZE) + SIZE];
        }

        /** Water lies in a pond column's top block (the ground is one lower there). */
        boolean isWater(BlockPos pos) {
            return water.contains(column(pos.getX(), pos.getZ())) && pos.getY() == top(pos.getX(), pos.getZ());
        }

        boolean isSolid(BlockPos pos) {
            if (!inside(pos.getX(), pos.getZ())) {
                return pos.getY() <= 64;
            }
            int top = top(pos.getX(), pos.getZ()) - (water.contains(column(pos.getX(), pos.getZ())) ? 1 : 0);
            return pos.getY() <= top && !caves.contains(pos) && !dug.contains(pos);
        }

        /** Open space above the ground (the sky side of a hill), not a cave or a tunnel. */
        boolean isOutsideAir(BlockPos pos) {
            return !isSolid(pos) && !caves.contains(pos) && !dug.contains(pos);
        }

        TunnelRoute.Ground ground() {
            return new TunnelRoute.Ground() {
                @Override
                public Integer groundTop(int x, int z) {
                    if (!inside(x, z)) {
                        return null;
                    }
                    return top(x, z) - (water.contains(column(x, z)) ? 1 : 0);
                }

                @Override
                public boolean mayDig(BlockPos cell) {
                    if (!isSolid(cell) || stone.contains(column(cell.getX(), cell.getZ()))) {
                        return false;
                    }
                    for (Direction side : Direction.values()) {
                        if (isWater(cell.relative(side))) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                public boolean mayOpenHole(BlockPos hole) {
                    return mayDig(hole) && !isSolid(hole.above()) && !isWater(hole.above());
                }
            };
        }

        CellMap cells() {
            return new CellMap() {
                @Override
                public boolean isOpen(BlockPos pos) {
                    return !isSolid(pos);
                }

                @Override
                public boolean isSolidFace(BlockPos pos, Direction face) {
                    return isSolid(pos);
                }
            };
        }
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    @Test
    void everyPlannedTunnelCanBeWalkedOutAndBackAndNeverOpensToTheSky() {
        int planned = 0;
        int caveSides = 0;
        List<String> failures = new ArrayList<>();
        for (long seed = 1; seed <= LANDSCAPES; seed++) {
            Landscape land = new Landscape(seed);
            Random random = new Random(seed * 31 + 7);
            // The nest's foraging tunnel start: one under the ground at the middle, already dug (with its shaft up).
            BlockPos start = new BlockPos(0, land.top(0, 0) - 1, 0);
            land.dug.add(start);
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = 12 + random.nextInt(39); // 12 to 50 blocks out (the planned reach, about 50)
            int fx = (int) Math.round(Math.cos(angle) * distance);
            int fz = (int) Math.round(Math.sin(angle) * distance);
            BlockPos food = new BlockPos(fx, land.top(fx, fz) + 1, fz);
            TunnelRoute.Plan plan = TunnelRoute.plan(start, food, land.ground(), 50 + 16);
            if (plan == null) {
                continue;
            }
            planned++;
            String where = "landscape " + seed + " (food at " + food.toShortString() + ", hole at " + plan.hole().toShortString() + ")";
            caveSides += check(land, start, plan, where, failures);
        }
        System.out.println("[gallery proof] " + planned + " of " + LANDSCAPES + " landscapes got a tunnel, " + caveSides
                + " cave sides (plastered shut by the nest walls), " + failures.size() + " failures");
        failures.forEach(line -> System.out.println("[gallery proof] " + line));
        if (!failures.isEmpty()) {
            fail(failures.size() + " failures, first ones:\n" + String.join("\n", failures.subList(0, Math.min(15, failures.size()))));
        }
        assertTrue(planned >= LANDSCAPES / 2, "too few landscapes got a tunnel to prove anything: " + planned);
    }

    /**
     * Digs the planned tunnel into the landscape and checks it: never open to the sky (every side of a tunnel block is
     * ground, another tunnel block, the hole, or a cave, which the nest walls plaster shut), and walked out of the hole
     * and back in by the real route finder with the real search budget. Failures are added; the cave sides are counted.
     */
    private static int check(Landscape land, BlockPos start, TunnelRoute.Plan plan, String where, List<String> failures) {
        land.dug.addAll(plan.cells());
        land.dug.add(plan.hole());
        int caveSides = 0;
        for (BlockPos cell : plan.cells()) {
            for (Direction side : Direction.values()) {
                BlockPos next = cell.relative(side);
                if (land.caves.contains(next)) {
                    caveSides++;
                } else if (land.isOutsideAir(next) && !next.equals(plan.hole())) {
                    failures.add(where + ": the tunnel block " + cell.toShortString() + " opens to the open air " + side + " [its column's ground at "
                            + land.top(cell.getX(), cell.getZ()) + ", the next column's at " + land.top(next.getX(), next.getZ()) + "]");
                }
            }
        }
        CellMap map = land.cells();
        BlockPos hole = plan.hole();
        java.util.function.Predicate<Foothold> outside = at -> at.isFloor() && at.cell().getY() == hole.getY() + 1
                && Math.abs(at.cell().getX() - hole.getX()) + Math.abs(at.cell().getZ() - hole.getZ()) == 1;
        Route out = AntPathFinder.find(map, AntPathFinder.footholdsIn(map, start), outside, hole, AntPathFinder.DEFAULT_MAX_EXPANSIONS,
                AntPathFinder.StepCosts.NONE);
        if (out == null) {
            failures.add(where + ": no way out of the hole from the tunnel's start " + start.toShortString());
        } else if (AntPathFinder.find(map, out.end().cell(), start, AntPathFinder.DEFAULT_MAX_EXPANSIONS) == null) {
            failures.add(where + ": no way back in from " + out.end().cell().toShortString());
        }
        return caveSides;
    }

    /**
     * The nest's own two foraging tunnels ({@link TunnelRoute#straight}, 8 blocks out from 2 under the entrance, as
     * {@code Nest} plans them on uneven ground) in all four directions on every landscape: the same checks.
     */
    @Test
    void theNestsOwnTunnelsCanBeWalkedOutAndBackAndNeverOpenToTheSky() {
        int planned = 0;
        List<String> failures = new ArrayList<>();
        for (long seed = 1; seed <= LANDSCAPES; seed++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                Landscape land = new Landscape(seed);
                int entrance = land.top(0, 0);
                for (int y = entrance - 2; y <= entrance; y++) {
                    land.dug.add(new BlockPos(0, y, 0)); // the shaft up to the entrance
                }
                BlockPos start = new BlockPos(0, entrance - 2, 0);
                TunnelRoute.Plan plan = TunnelRoute.straight(start, side, 8, 4, land.ground());
                if (plan == null) {
                    continue;
                }
                planned++;
                check(land, start, plan, "landscape " + seed + " toward " + side + " (hole at " + plan.hole().toShortString() + ")", failures);
            }
        }
        System.out.println("[gallery proof] the nest's own tunnels: " + planned + " of " + 4 * LANDSCAPES + " planned, " + failures.size() + " failures");
        failures.forEach(line -> System.out.println("[gallery proof] " + line));
        if (!failures.isEmpty()) {
            fail(failures.size() + " failures, first ones:\n" + String.join("\n", failures.subList(0, Math.min(15, failures.size()))));
        }
        assertTrue(planned >= 2 * LANDSCAPES, "too few tunnels planned to prove anything: " + planned);
    }
}
