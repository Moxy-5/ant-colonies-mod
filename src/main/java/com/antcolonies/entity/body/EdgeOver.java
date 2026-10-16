package com.antcolonies.entity.body;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Client, the body only: how an insect is drawn going over an outside edge, up over the top of a wall or over the brink
 * of a drop and head first down its side (2026-10-16, seen in the climbing film by the user and me). A real ant's front
 * legs reach over the edge first and pull; the hard thorax tips over the edge bit by bit, resting across it, from when
 * the front feet get there until the hind feet are over too. The navigator (the legs) changes the surface only when the
 * body's middle reaches the edge, and the usual climbing pose then tips the whole body over in a few ticks: before that
 * the front half stood up in the air above the top of a wall, or stuck out flat over a drop. So near such an edge the
 * drawn body is put across the edge here from the insect's position and the blocks, with the same angles the climbing
 * pose uses (the wall it faces, the tilt, the heading) and where its middle is drawn; outside that stretch the usual
 * pose draws it, and at both ends of the stretch the two agree, so it never jumps.
 * <p>
 * Only the plain cases: a full block's top edge, the body going straight over it (within {@link #SQUARE} degrees),
 * between a floor and a wall. Other edges (under an overhang onto a ceiling, round a pillar's side, a slope's lip, a
 * doorway's rim, a channel) keep the usual pose.
 */
public final class EdgeOver {
    /**
     * How far the front feet reach ahead of the body's middle and the hind feet behind it (model units; the worker
     * model's foot bones, {@code tools/ant_models.py}). The tipping starts when the front feet get to the edge and ends
     * when the hind feet are over it (my picks, to confirm).
     */
    public static final double FRONT_FEET = 3.6;
    public static final double HIND_FEET = 2.85;
    /** Only a body heading within this many degrees of straight over the edge (my pick, to confirm). */
    private static final float SQUARE = 25.0F;

    /**
     * How to draw it: the wall it faces and its tilt and heading (as in the usual climbing pose: tilt 0 lying on the
     * floor, 90 upright on the wall; heading 0 head up, 180 head down), and where the middle of its feet is drawn,
     * from its position (blocks, world axes).
     */
    public record Pose(Direction wall, float tilt, float heading, Vec3 shift) {
    }

    private EdgeOver() {
    }

    /**
     * The pose over an edge, or null if it is not going over one now.
     *
     * @param pos      the insect's position (the bottom middle of its box)
     * @param width    its box's width (blocks)
     * @param height   its box's height (blocks)
     * @param surface  the surface the navigator holds it to ({@link Direction#DOWN} a floor, a side a wall)
     * @param heading  on a wall, where the head points within it (0 up, 180 down)
     * @param yaw      on a floor, the way it faces
     * @param unit     how long one model unit is drawn (blocks)
     * @param lift     how far the usual climbing pose lifts the drawn body up a wall (blocks)
     * @param intoWall how far the usual climbing pose pushes the drawn body in toward a wall (blocks)
     */
    @Nullable
    public static Pose find(Level level, Vec3 pos, float width, float height, Direction surface, float heading, float yaw,
            double unit, double lift, double intoWall) {
        Vec3 middle = pos.add(0.0, height / 2.0, 0.0);
        Body body = new Body(pos, middle, FRONT_FEET * unit, HIND_FEET * unit, width / 2.0, height / 2.0, lift,
                width / 2.0 - intoWall);
        if (surface.getAxis().isHorizontal()) {
            boolean up = Math.abs(Mth.wrapDegrees(heading)) < SQUARE;
            boolean down = Math.abs(Mth.wrapDegrees(heading - 180.0F)) < SQUARE;
            if (!up && !down) {
                return null;
            }
            // The block it holds on to, and the top of its column just above the body.
            Direction out = surface.getOpposite();
            double in = body.halfWidth + 0.05;
            BlockPos hold = BlockPos.containing(middle.x + surface.getStepX() * in, middle.y, middle.z + surface.getStepZ() * in);
            for (int y = hold.getY() - 1; y <= hold.getY() + 1; y++) {
                BlockPos column = new BlockPos(hold.getX(), y, hold.getZ());
                if (isTopEdge(level, column, out)) {
                    return body.over(new Edge(column, out), up);
                }
            }
            return null;
        }
        if (surface != Direction.DOWN) {
            return null;
        }
        Direction ahead = Direction.fromYRot(yaw);
        if (Math.abs(Mth.wrapDegrees(yaw - ahead.toYRot())) > SQUARE) {
            return null;
        }
        BlockPos under = BlockPos.containing(middle.x, pos.y - 0.05, middle.z);
        // Over the brink ahead and down its side, or (just come up a wall) over the top edge behind.
        Edge brink = edgeAlong(level, under, ahead);
        Pose pose = brink == null ? null : body.over(brink, false);
        if (pose == null) {
            Edge behind = edgeAlong(level, under, ahead.getOpposite());
            pose = behind == null ? null : body.over(behind, true);
        }
        return pose;
    }

    /**
     * The top edge of a full block on {@code side} near {@code under}: that block, the next one that way, or the one
     * before (round an edge the navigator moves the body's middle over the open air beside the block for a moment); or
     * null.
     */
    @Nullable
    private static Edge edgeAlong(Level level, BlockPos under, Direction side) {
        for (int k : new int[] {0, 1, -1}) {
            BlockPos column = under.relative(side, k);
            if (isTopEdge(level, column, side)) {
                return new Edge(column, side);
            }
        }
        return null;
    }

    /** Whether {@code column} is a full block with open air on top of it and beside it on {@code side} (an outside edge). */
    private static boolean isTopEdge(Level level, BlockPos column, Direction side) {
        BlockPos beside = column.relative(side);
        return isFull(level, column) && isOpen(level, column.above()) && isOpen(level, beside) && isOpen(level, beside.above());
    }

    private static boolean isFull(Level level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isOpen(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The top edge of a block on one side: {@code out} points out of that side. */
    private record Edge(BlockPos column, Direction out) {
        /** The edge's line, level with the insect's middle along it. */
        Vec3 point(Vec3 middle) {
            double face = out.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? column.get(out.getAxis()) + 1 : column.get(out.getAxis());
            double top = column.getY() + 1;
            return out.getAxis() == Direction.Axis.X ? new Vec3(face, top, middle.z) : new Vec3(middle.x, top, face);
        }
    }

    /** The insect's body and how the usual poses draw it (all blocks). */
    private record Body(Vec3 pos, Vec3 middle, double front, double hind, double halfWidth, double halfHeight, double lift,
            double offWall) {
        /**
         * Its pose over this edge: going up the side and over the top ({@code upAndOver}), or over the top and down the
         * side. The surface it comes from (A) and the one it goes onto (B): it walks along A toward the edge ({@code f})
         * and along B away from it ({@code g}); {@code nA} and {@code nB} point out of them. Its progress over the edge
         * ({@code q}) is how far its box's middle is along A plus along B, measured from the edge: it grows steadily
         * whichever surface the navigator holds it to, round the corner too.
         */
        @Nullable
        Pose over(Edge edge, boolean upAndOver) {
            Vec3 e = edge.point(middle);
            Vec3 out = Vec3.atLowerCornerOf(edge.out().getNormal());
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Vec3 f = upAndOver ? up : out;
            Vec3 g = upAndOver ? out.scale(-1.0) : up.scale(-1.0);
            Vec3 nA = upAndOver ? out : up;
            Vec3 nB = upAndOver ? up : out;
            Vec3 rel = middle.subtract(e);
            double q = rel.dot(f) + rel.dot(g);
            // Where the stretch begins (the front feet at the edge in A's usual pose) and ends (the hind feet over it
            // in B's): on a wall the usual pose draws the middle of the feet {@code lift} up from the box's bottom and
            // {@code offWall} out from the wall; on a floor at the box's bottom, under its middle.
            double begin = upAndOver ? -front - halfWidth + halfHeight - lift : -front - halfHeight;
            double end = upAndOver ? hind + halfHeight : hind + halfWidth - halfHeight + lift;
            if (q <= begin || q >= end) {
                return null;
            }
            double t = (q - begin) / (end - begin);
            // The middle of its feet goes along at an even pace from the front feet's reach before the edge to the
            // hind feet's after it. The thorax lies the way the line from its hind feet (still on A) to its front feet
            // (already over the edge, on B) runs, put across the edge at that angle, off the surface as the poses have it.
            double along = -front + (front + hind) * t;
            double angle = Math.atan2(along + front, hind - along);
            double turned = angle / (Math.PI / 2.0);
            double off = Mth.lerp(turned, upAndOver ? offWall : 0.0, upAndOver ? 0.0 : offWall);
            Vec3 forward = f.scale(Math.cos(angle)).add(g.scale(Math.sin(angle)));
            Vec3 normal = nA.scale(Math.cos(angle)).add(nB.scale(Math.sin(angle)));
            Vec3 feet = e.add(forward.scale(along)).add(normal.scale(off));
            float degrees = (float) (turned * 90.0);
            return new Pose(edge.out().getOpposite(), upAndOver ? 90.0F - degrees : degrees, upAndOver ? 0.0F : 180.0F,
                    feet.subtract(pos));
        }
    }
}
