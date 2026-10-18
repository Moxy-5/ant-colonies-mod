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
     * @param climb    its body's climbing numbers (its body plan's): the tipping starts when its front feet get to the
     *                 edge and ends when its hind feet are over it
     * @param unit     how long one model unit is drawn (blocks)
     * @param lift     how far the usual climbing pose lifts the drawn body up a wall (blocks)
     * @param intoWall how far the usual climbing pose pushes the drawn body in toward a wall (blocks)
     */
    @Nullable
    public static Pose find(Level level, Vec3 pos, float width, float height, Direction surface, float heading, float yaw,
            BodyPlans.Climb climb, double unit, double lift, double intoWall) {
        Vec3 middle = pos.add(0.0, height / 2.0, 0.0);
        Body body = new Body(pos, middle, climb.frontFeet() * unit, climb.hindFeet() * unit, climb.frontEnd() * unit,
                width / 2.0, height / 2.0, lift, width / 2.0 - intoWall);
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
     * The pose round an inside corner, the foot of a wall (2026-10-16, found by the in-game camera's checks): coming down
     * a wall head first onto the floor, or walking up to a wall and onto it. A real ant does not stop and flip there: its
     * front legs reach the new surface first and walk on along it while its hind legs still hold the old one, so the
     * thorax bridges the corner like a ramp, standing up (or lying down) bit by bit as it goes, and it is fully round
     * only once its hind legs have left the old surface. The usual pose instead tipped the whole body over in three
     * ticks once the navigator changed the surface (and the gaster touched the wall as it did). So near the foot of a
     * full block the drawn body is put across the corner: the hind feet on the surface it comes from, the front feet on
     * the one it goes onto, a body's feet apart; how far round it is grows steadily with how far its box has gone round
     * the corner, from its front feet reaching the new surface until its hind feet leave the old one. At both ends it is
     * drawn where the usual poses draw it (a little off the corner, never into it). Null when not at such a corner now.
     *
     * @param drawnWall    the wall the drawn body faces now, or null when it is drawn lying on the floor
     * @param drawnHeading where its head points within that wall (0 up, 180 down)
     * @param walking      whether it is walking now: on the floor the pose begins only for an insect walking up to the
     *                     wall (one standing at it, digging into it say, stays lying on the floor)
     */
    @Nullable
    public static Pose findCorner(Level level, Vec3 pos, float width, float height, Direction surface, float heading,
            float yaw, BodyPlans.Climb climb, double unit, double lift, double intoWall, @Nullable Direction drawnWall, float drawnHeading,
            boolean walking) {
        Vec3 middle = pos.add(0.0, height / 2.0, 0.0);
        Body body = new Body(pos, middle, climb.frontFeet() * unit, climb.hindFeet() * unit, climb.frontEnd() * unit,
                width / 2.0, height / 2.0, lift, width / 2.0 - intoWall);
        Direction wall;
        boolean down;
        if (surface.getAxis().isHorizontal()) {
            wall = surface;
            if (Math.abs(Mth.wrapDegrees(heading)) < SQUARE) {
                down = false;
            } else if (Math.abs(Mth.wrapDegrees(heading - 180.0F)) < SQUARE) {
                down = true;
            } else {
                return null;
            }
        } else if (surface == Direction.DOWN) {
            if (drawnWall != null && drawnWall.getAxis().isHorizontal()
                    && Math.abs(Mth.wrapDegrees(drawnHeading - 180.0F)) < SQUARE) {
                // just come down a wall: still drawn bridging the corner behind it
                wall = drawnWall;
                down = true;
            } else {
                if (drawnWall == null && !walking) {
                    return null;
                }
                wall = Direction.fromYRot(yaw);
                if (Math.abs(Mth.wrapDegrees(yaw - wall.toYRot())) > SQUARE) {
                    return null;
                }
                down = false;
            }
        } else {
            return null;
        }
        // The corner: a full wall block, open air in front of its foot, and a full floor block under that air.
        BlockPos cell = BlockPos.containing(middle.x, pos.y + 0.05, middle.z);
        for (int dy : new int[] {0, -1, 1}) {
            for (int k : new int[] {0, 1, -1}) {
                BlockPos air = cell.offset(0, dy, 0).relative(wall, k);
                BlockPos block = air.relative(wall);
                if (isFull(level, block) && isOpen(level, air) && isFull(level, air.below())) {
                    double face = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? block.get(wall.getAxis()) : block.get(wall.getAxis()) + 1;
                    Vec3 corner = wall.getAxis() == Direction.Axis.X ? new Vec3(face, air.getY(), middle.z)
                            : new Vec3(middle.x, air.getY(), face);
                    return body.round(corner, wall, down, surface.getAxis().isHorizontal() != down);
                }
            }
        }
        return null;
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
    private record Body(Vec3 pos, Vec3 middle, double front, double hind, double head, double halfWidth,
            double halfHeight, double lift, double offWall) {
        /**
         * Its pose round the inside corner at {@code corner} (the foot of {@code wall}, which it faces): coming down the
         * wall onto the floor, or from the floor up onto it. A is the surface it comes from and B the one it goes onto,
         * {@code nA} and {@code nB} pointing out of them; a point is written as its distance off A ({@code xA}) and off
         * B ({@code xB}). Its progress round the corner ({@code s}) is how far its box's middle is out along B less how
         * far it still is up along A, each counted from where the box touches that surface: it grows steadily round the
         * corner whichever surface the navigator holds it to.
         */
        @Nullable
        Pose round(Vec3 corner, Direction wall, boolean down, boolean onB) {
            Vec3 outOfWall = Vec3.atLowerCornerOf(wall.getOpposite().getNormal());
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Vec3 nA = down ? outOfWall : up;
            Vec3 nB = down ? up : outOfWall;
            double cA = down ? halfWidth : halfHeight;
            double cB = down ? halfHeight : halfWidth;
            Vec3 rel = middle.subtract(corner);
            double s = (rel.dot(nA) - cA) - (rel.dot(nB) - cB);
            // A head reaching out past the front feet (a major soldier's, with its long jaws) gets to B first: the
            // front legs reach up for it from then, so the head is never drawn in the block.
            double front = Math.max(this.front, head - cB);
            if (s <= -front && onB && !down) {
                // walking up to the wall, the navigator already holds it to the wall, though its front feet (or head)
                // have not got there yet: it is still drawn lying on the floor, as at the stretch's start, never tipped
                // by the usual pose in one go (coming down, it can't be on the floor this early)
                return new Pose(wall, 0.0F, 0.0F, Vec3.ZERO);
            }
            if (s <= -front || s >= hind) {
                return null;
            }
            // eased in and out: the front feet take hold and the hind feet let go gently, so the tipping starts
            // and ends slowly instead of at full speed (seen as a jump by the camera's checks)
            double t = (s + front) / (front + hind);
            t = t * t * (3.0 - 2.0 * t);
            s = -front + (front + hind) * t;
            // The thorax bridges the corner: hind feet on A, front feet on B, a body's feet apart; it has turned theta
            // from lying along A, and the middle of its feet (between them as the feet lie on the body) is s along.
            double theta = Math.atan2(front, hind) + Math.asin(Mth.clamp(s / Math.sqrt(front * front + hind * hind), -1.0, 1.0));
            double xA = hind * Math.sin(theta);
            double xB = front * Math.cos(theta);
            // where the usual poses draw the feet at the two ends, beyond the bare corner (on a wall lifted up it and
            // a little off it, on the floor under the box's middle), shared out along the way
            xA += down ? Mth.lerp(t, offWall, halfWidth) : Mth.lerp(t, 0.0, lift);
            xB += down ? Mth.lerp(t, lift, 0.0) : Mth.lerp(t, halfWidth, offWall);
            Vec3 feet = corner.add(nA.scale(xA)).add(nB.scale(xB));
            float degrees = (float) Math.toDegrees(theta);
            return new Pose(wall, down ? 90.0F - degrees : degrees, down ? 180.0F : 0.0F, feet.subtract(pos));
        }

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
