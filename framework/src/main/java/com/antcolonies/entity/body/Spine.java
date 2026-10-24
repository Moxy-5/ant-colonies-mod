package com.antcolonies.entity.body;

import java.util.List;

import net.minecraft.util.Mth;

/**
 * Client, the body only: the spine, "follow the leader" (the edge curve of 2026-10-16, made general for every body on
 * 2026-10-18, the animation upgrade's B3). Going over an edge, onto a wall or a ceiling, up a ramp or round a turn, a
 * real insect's front turns first and the rest of the body follows the same path: an ant's waist bends while its gaster
 * is still on the old surface, a termite's soft abdomen curves along its length over the edge, and so would a
 * centipede's rings or a lizard's tail. Here the front's turns (pitch: up a wall or a ramp; yaw: round a turn) are added
 * up tick by tick. The parts between the body's middle and its hind feet are the thorax's and lean with it; the
 * parts behind the hind feet follow the ground the hind feet walked over (since 2026-10-18, the user's pick: before,
 * the whole rear followed the thorax's earlier tilt, and the thorax tips over an edge from when its front feet get
 * there, so the rear pointed up into the air going head first over a top). So the ground under the hind feet is
 * remembered with the distance walked: the front's own pitch, except across an edge or a corner, where the thorax
 * leans from the old surface to the new one while the hind feet still stand on the old one. Each piece of the spine (the parts behind the
 * thorax, front to back, from the body plan, {@link BodyPlans.Piece}) lies the way the path lay, on average, over the stretch of
 * path it covers behind the body's middle (the up-and-down way: the ground there, or the thorax's line in front of the
 * hind feet). Its bend is how far the piece in front of it lies turned past it, held within the joint's reach. So the bend is the same at any
 * speed, stays if the insect stops half way over an edge, and a turn where it stands turns the whole body (no bend);
 * standing still a second, a side bend eases out (it straightens itself), an up-and-down one stays (its rear still
 * lies over the edge).
 * <p>
 * Nothing here decides anything: the insect hands it how its front turned and how far it moved each tick, and the
 * live layer ({@code client/BodyLanguage}) bends each piece's joints by its share of the bend.
 */
public final class Spine {
    /** How many marks of the path are remembered (enough for the longest body at a walk). */
    private static final int MEMORY = 64;
    /** Along the path: the distance walked at each mark (blocks), the ground's pitch under the hind feet then and the front's yaw. */
    private final double[] pathMark = new double[MEMORY];
    private final float[] pitchMark = new float[MEMORY];
    private final float[] yawMark = new float[MEMORY];
    private int marks;
    private int newestMark;
    private double pathWalked;
    private int standingTicks;
    private float frontPitch;
    private float frontYaw;
    /** Each piece's bend now and a tick ago (degrees; up past the piece behind, and to the right). */
    private float[] pitch = new float[1];
    private float[] pitchO = new float[1];
    private float[] yaw = new float[1];
    private float[] yawO = new float[1];

    /**
     * One tick.
     *
     * @param tipped   how far the front tipped up this tick (degrees)
     * @param lean     how far the front now leans up past the ground under its hind feet (degrees; 0 but across an
     *                 edge or a corner, while the thorax tips from the old surface to the new one)
     * @param turned   how far it turned right this tick (degrees)
     * @param moved    how far it moved (blocks; along a wall too)
     * @param scale    how long one model unit is drawn (blocks)
     * @param restart  just loaded or put down: no bend from where it was
     * @param pieces   the spine's pieces, front to back (the body plan's)
     * @param tightest the tightest curve the path itself takes (model units across; a sharper turn is the body turning
     *                 on the spot)
     * @param hind     how far its hind feet are behind its middle (model units)
     */
    public void tick(float tipped, float lean, float turned, double moved, double scale, boolean restart, List<BodyPlans.Piece> pieces,
            double tightest, double hind) {
        frontPitch += tipped;
        frontYaw += turned;
        if (restart || marks == 0) {
            marks = 0;
        } else {
            // The path itself turns at most so much over the ground walked this tick; the rest of the turn is the body
            // turning where it stands, which turns the rear with it.
            float curve = (float) Math.min(Math.abs(turned), moved / (tightest * scale / 2.0) * Mth.RAD_TO_DEG);
            float onTheSpot = turned - Math.copySign(curve, turned);
            standingTicks = moved < 1.0E-4 ? standingTicks + 1 : 0;
            float ease = standingTicks > 20 ? 0.1F : 0.0F;
            for (int i = 0; i < marks; i++) {
                int k = Math.floorMod(newestMark - i, MEMORY);
                yawMark[k] += onTheSpot + (frontYaw - yawMark[k] - onTheSpot) * ease;
            }
        }
        pathWalked += moved;
        if (marks == 0 || moved >= 1.0E-4) {
            newestMark = (newestMark + 1) % MEMORY;
            marks = Math.min(MEMORY, marks + 1);
        }
        pathMark[newestMark] = pathWalked;
        pitchMark[newestMark] = frontPitch - lean;
        yawMark[newestMark] = frontYaw;
        if (pitch.length != pieces.size()) {
            pitch = new float[pieces.size()];
            pitchO = new float[pieces.size()];
            yaw = new float[pieces.size()];
            yawO = new float[pieces.size()];
        }
        // Each piece lies the way the ground lay where it is now (averaged over its own body, from where it begins: the
        // ant's waist piece by its gaster, its petiole and postpetiole bending it; in front of the hind feet the thorax's
        // line), so at an inside corner (a wall up from the floor, the floor at the foot of a wall) it lies along
        // the old surface and comes round bit by bit as it passes the corner, and over an outside edge it stays on the old
        // surface until it gets there, then hangs down the new one.
        float ahead = frontPitch;
        float aheadYaw = frontYaw;
        for (int i = 0; i < pitch.length; i++) {
            BodyPlans.Piece piece = pieces.get(i);
            double hinge = pathWalked - piece.hinge() * scale;
            float lies = groundUnder(piece.from(), piece.to(), ahead, hind, scale);
            float liesYaw = averageMark(yawMark, pathWalked - piece.to() * scale, hinge);
            float bend = ahead - lies;
            pitchO[i] = pitch[i];
            yawO[i] = yaw[i];
            pitch[i] = Mth.clamp(bend, -piece.maxDown(), piece.maxUp());
            yaw[i] = Mth.clamp(aheadYaw - liesYaw, -piece.maxYaw(), piece.maxYaw());
            ahead -= pitch[i];
            aheadYaw -= yaw[i];
        }
    }

    /** How far the piece in front has tipped up past this piece (degrees, its bend), or 0 for no such piece. */
    public float pitch(int piece, float partialTick) {
        return piece < pitch.length ? Mth.lerp(partialTick, pitchO[piece], pitch[piece]) : 0.0F;
    }

    /** How far the piece in front has turned right past this piece (degrees, its bend), or 0 for no such piece. */
    public float yaw(int piece, float partialTick) {
        return piece < yaw.length ? Mth.lerp(partialTick, yawO[piece], yaw[piece]) : 0.0F;
    }

    /** How many pieces it bends. */
    public int pieces() {
        return pitch.length;
    }

    /** The front's own pitch and yaw so far (degrees; for the in-game camera's log). */
    public float frontPitch() {
        return frontPitch;
    }

    public float frontYaw() {
        return frontYaw;
    }

    /**
     * The up-and-down way a piece lies, from {@code near} to {@code far} behind the middle (model units): in front of the
     * hind feet along the thorax's line ({@code ahead}, no bend there), behind them the way the ground lay where it is
     * now, which the hind feet walked over {@code hind} earlier.
     */
    private float groundUnder(double near, double far, float ahead, double hind, double scale) {
        double behind = far - Math.max(near, hind);
        if (behind <= 0.0) {
            return ahead;
        }
        double along = Math.max(0.0, Math.min(far, hind) - near);
        float ground = averageMark(pitchMark, pathWalked - (far - hind) * scale, pathWalked - (Math.max(near, hind) - hind) * scale);
        return (float) ((along * ahead + behind * ground) / (along + behind));
    }

    /**
     * The front's pose (one of the mark rings) averaged over the stretch of path walked from {@code from} to {@code to}
     * (blocks walked; straight lines between marks, the oldest mark's pose before the remembered path).
     */
    private float averageMark(float[] pose, double from, double to) {
        if (to - from < 1.0E-9) {
            return poseAt(pose, to);
        }
        double sum = 0.0;
        double at = to;
        float poseThere = poseAt(pose, to);
        for (int i = 0; i < marks && at > from; i++) {
            int k = Math.floorMod(newestMark - i, MEMORY);
            if (pathMark[k] >= at) {
                continue;
            }
            double next;
            float poseNext;
            if (pathMark[k] >= from || i == 0) {
                next = Math.max(pathMark[k], from);
                poseNext = pose[k];
            } else {
                // The stretch starts between this mark and the later one: part way.
                int later = Math.floorMod(k + 1, MEMORY);
                double span = pathMark[later] - pathMark[k];
                next = from;
                poseNext = Mth.lerp(span > 1.0E-9 ? (float) ((from - pathMark[k]) / span) : 0.0F, pose[k], pose[later]);
            }
            sum += (at - next) * (poseThere + poseNext) / 2.0;
            at = next;
            poseThere = poseNext;
        }
        if (at > from) {
            sum += (at - from) * poseThere;                // before the remembered path: as the oldest mark
        }
        return (float) (sum / (to - from));
    }

    /** The front's pose (one of the mark rings) at this distance walked (between two marks, part way). */
    private float poseAt(float[] pose, double walked) {
        float there = pose[newestMark];
        for (int i = 0; i < marks; i++) {
            int k = Math.floorMod(newestMark - i, MEMORY);
            there = pose[k];
            if (pathMark[k] <= walked) {
                if (i > 0) {
                    int later = Math.floorMod(k + 1, MEMORY);
                    double span = pathMark[later] - pathMark[k];
                    float f = span > 1.0E-9 ? (float) ((walked - pathMark[k]) / span) : 0.0F;
                    there = Mth.lerp(f, pose[k], pose[later]);
                }
                break;
            }
        }
        return there;
    }
}
