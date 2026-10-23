package com.antcolonies.entity.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.util.Mth;

/**
 * The spine (climbing done once for every body, 2026-10-18, {@code docs/plans/climbing-every-body.md}): for the ant's
 * waist it turns exactly as the old waist code did (the user approved the ant's climbing in-game on 2026-10-16), replayed
 * through the same climbs to the last bit; head first over a top the rear lies on the ground the hind feet walked; and a made-up body of 12 segments walked round a turn, up a wall and over its
 * top lies along the path it walked, every segment of it.
 */
class SpineTest {
    /** One tick of a climb: how far the front tipped up and turned right, how far it moved, whether it was put down. */
    private record Step(float tipped, float turned, double moved, boolean restart) {
    }

    @Test
    void theAntsWaistTurnsExactlyAsBefore() {
        // Sideways the spine is the old waist word for word, on every climb (up and down it changed on purpose on
        // 2026-10-18, the user's pick: the rear follows the ground the hind feet walked, not the front's own path).
        List<List<Step>> climbs = List.of(upAWall(), aTurn(), overATop(), overABrink(), stopHalfWayOver(), randomWalk(1),
                randomWalk(2), randomWalk(3));
        for (double drawScale : new double[] {0.65, 1.3}) {
            double scale = drawScale / 16.0;
            for (int c = 0; c < climbs.size(); c++) {
                for (double hind : new double[] {0.0, BodyPlans.WORKER_CLIMB.hindFeet()}) {
                    Spine spine = new Spine();
                    OldWaist old = new OldWaist();
                    int tick = 0;
                    for (Step step : climbs.get(c)) {
                        spine.tick(step.tipped, 0.0F, step.turned, step.moved, scale, step.restart, List.of(BodyPlans.WORKER_WAIST), 3.0, hind);
                        old.tick(step.tipped, step.turned, step.moved, scale, step.restart);
                        for (float partial : new float[] {0.0F, 0.37F, 1.0F}) {
                            String where = "climb " + c + " scale " + drawScale + " hind " + hind + " tick " + tick;
                            assertEquals(Float.floatToIntBits(old.yaw(partial)), Float.floatToIntBits(spine.yaw(0, partial)),
                                    where + ": yaw " + old.yaw(partial) + " was, now " + spine.yaw(0, partial));
                        }
                        tick++;
                    }
                }
            }
        }
    }

    @Test
    void aLongBodyLiesAlongThePathItWalked() {
        // Twelve segments of one unit each behind the thorax, reaching so far that no joint's reach holds them back.
        List<BodyPlans.Piece> rings = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rings.add(new BodyPlans.Piece(List.of("ring_" + i), new float[] {1.0F}, i, i, i + 1, 180.0F, 180.0F));
        }
        double scale = 0.75 / 16.0;
        // The path: a turn to the right, up a wall, over its top onto the floor above, and on (the front's pitch and
        // yaw at each mark, with the distance walked there).
        List<Step> walk = new ArrayList<>();
        walk.add(new Step(0.0F, 0.0F, 0.0, true));
        walk.addAll(straight(25));
        for (int i = 0; i < 9; i++) {
            walk.add(new Step(0.0F, 10.0F, 0.02, false));
        }
        walk.addAll(straight(30));
        walk.addAll(tip(90.0F));
        walk.addAll(straight(40));
        walk.addAll(tip(-90.0F));
        walk.addAll(straight(60));
        List<Double> walked = new ArrayList<>();
        List<Float> pitches = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        Spine spine = new Spine();
        double at = 0.0;
        float pitch = 0.0F;
        float yaw = 0.0F;
        for (Step step : walk) {
            spine.tick(step.tipped, 0.0F, step.turned, step.moved, scale, step.restart, rings, 0.0001, 0.0);
            at += step.moved;
            pitch += step.tipped;
            yaw += step.turned;
            walked.add(at);
            pitches.add(pitch);
            yaws.add(yaw);
            // Each segment lies the way the path lay somewhere along the stretch of it the segment covers now (so it
            // never sticks out past the corner into the block or the air).
            float ahead = pitch;
            float aheadYaw = yaw;
            for (int i = 0; i < rings.size(); i++) {
                ahead -= spine.pitch(i, 1.0F);
                aheadYaw -= spine.yaw(i, 1.0F);
                BodyPlans.Piece ring = rings.get(i);
                float[] range = range(walked, pitches, at - ring.to() * scale, at - ring.hinge() * scale);
                float[] yawRange = range(walked, yaws, at - ring.to() * scale, at - ring.hinge() * scale);
                assertTrue(ahead >= range[0] - 0.01F && ahead <= range[1] + 0.01F,
                        "segment " + i + " at " + at + " lies at " + ahead + ", its stretch of path " + range[0] + " to " + range[1]);
                assertTrue(aheadYaw >= yawRange[0] - 0.01F && aheadYaw <= yawRange[1] + 0.01F,
                        "segment " + i + " at " + at + " turned " + aheadYaw + ", its path " + yawRange[0] + " to " + yawRange[1]);
            }
        }
        // Walked on along the floor above: the whole body lies flat on it, in line.
        for (int i = 0; i < rings.size(); i++) {
            assertEquals(0.0F, spine.pitch(i, 1.0F), 0.01F, "segment " + i + " still bent");
            assertEquals(0.0F, spine.yaw(i, 1.0F), 0.01F, "segment " + i + " still turned");
        }
        // With a real joint's reach, no joint bends past it.
        List<BodyPlans.Piece> stiff = new ArrayList<>();
        for (BodyPlans.Piece ring : rings) {
            stiff.add(new BodyPlans.Piece(ring.joints(), ring.shares(), ring.hinge(), ring.from(), ring.to(), 15.0F, 8.0F));
        }
        Spine held = new Spine();
        for (Step step : walk) {
            held.tick(step.tipped, 0.0F, step.turned, step.moved, scale, step.restart, stiff, 0.0001, 0.0);
            for (int i = 0; i < stiff.size(); i++) {
                assertTrue(Math.abs(held.pitch(i, 1.0F)) <= 15.0F && Math.abs(held.yaw(i, 1.0F)) <= 8.0F, "joint " + i);
            }
        }
    }

    @Test
    void headFirstOverATopTheRearLiesOnTheGroundItWalked() {
        // The user's pick (2026-10-18): going head first over a top, the thorax tips from when its front feet reach the
        // edge until its hind feet are over it, and the parts behind the hind feet lie on the ground the hind feet walked
        // over: on the top, each joint bends toward it as far as it reaches (never up in the air along the thorax's
        // tilt, the old rule), and once over, the body hangs straight down the wall. A soft termite worker's abdomen of
        // 6 segments with a real reach, and the ant's waist.
        List<BodyPlans.Piece> abdomen = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double hinge = 0.37 + 0.51 * i;
            abdomen.add(new BodyPlans.Piece(List.of("abdomen_" + i), new float[] {1.0F}, hinge, hinge, hinge + 0.49, 15.0F, 6.0F));
        }
        record Body(List<BodyPlans.Piece> pieces, double front, double hind) {
        }
        for (Body body : List.of(new Body(abdomen, 2.76, 1.19),
                new Body(List.of(BodyPlans.WORKER_WAIST), BodyPlans.WORKER_CLIMB.frontFeet(), BodyPlans.WORKER_CLIMB.hindFeet()))) {
            List<BodyPlans.Piece> pieces = body.pieces();
            double scale = 0.75 / 16.0;
            double edge = 1.0;
            Spine spine = new Spine();
            Spine old = new Spine();
            double at = 0.0;
            float front = 0.0F;
            float gained = 0.0F;
            for (int t = 0; t < 110; t++) {
                at += 0.02;
                // how far over the thorax is: from its front feet at the edge to its hind feet there
                double over = Mth.clamp((at + body.front() * scale - edge) / ((body.front() + body.hind()) * scale), 0.0, 1.0);
                float now = (float) (-90.0 * over);
                float tipped = now - front;
                front = now;
                boolean posed = over > 0.0 && over < 1.0;
                spine.tick(tipped, posed ? front : 0.0F, 0.0F, 0.02, scale, t == 0, pieces, 3.0, body.hind());
                old.tick(tipped, 0.0F, 0.0F, 0.02, scale, t == 0, pieces, 3.0, body.hind());
                float ahead = front;
                float oldAhead = front;
                for (int i = 0; i < pieces.size(); i++) {
                    BodyPlans.Piece piece = pieces.get(i);
                    float before = ahead;
                    ahead -= spine.pitch(i, 1.0F);
                    oldAhead -= old.pitch(i, 1.0F);
                    String where = "piece " + i + " tick " + t;
                    if (over < 1.0 && piece.hinge() >= body.hind()) {
                        // the hind feet still on the top and this piece wholly behind them: it bends toward the top
                        float toward = Math.copySign(Math.max(0.0F, Math.abs(before) - piece.maxPitch()), before);
                        assertEquals(toward, ahead, 0.01F, where + " lies at " + ahead + " over the top");
                    }
                    if (at - piece.to() * scale > edge + 0.001) {
                        assertEquals(-90.0F, ahead, 0.01F, where + " off the wall");
                    }
                    // never higher into the air than the old rule put it
                    assertTrue(ahead >= oldAhead - 0.01F, where + " now " + ahead + ", the old rule " + oldAhead);
                    if (i == pieces.size() - 1) {
                        gained = Math.max(gained, ahead - oldAhead);
                    }
                }
            }
            assertTrue(at > edge + pieces.get(pieces.size() - 1).to() * scale, "walked far enough");
            // and the old rule raised the rear far more
            assertTrue(gained > 20.0F, "the rear came down only " + gained + " below the old rule");
        }
    }

    /**
     * The lowest and highest the path's pose was over the stretch walked from {@code from} to {@code to} (the marks in
     * it and the ones just before and after it, as the path runs straight between marks).
     */
    private static float[] range(List<Double> walked, List<Float> pose, double from, double to) {
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (int i = 0; i < walked.size(); i++) {
            boolean in = walked.get(i) >= from && walked.get(i) <= to;
            boolean before = walked.get(i) < from && (i + 1 == walked.size() || walked.get(i + 1) >= from);
            boolean after = walked.get(i) > to && (i == 0 || walked.get(i - 1) <= to);
            if (in || before || after) {
                lo = Math.min(lo, pose.get(i));
                hi = Math.max(hi, pose.get(i));
            }
        }
        return new float[] {lo, hi};
    }

    private static List<Step> straight(int ticks) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            out.add(new Step(0.0F, 0.0F, 0.02, false));
        }
        return out;
    }

    /** Tips the front over a corner in three ticks, as the climbing pose does. */
    private static List<Step> tip(float degrees) {
        List<Step> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            out.add(new Step(degrees / 3.0F, 0.0F, 0.02, false));
        }
        return out;
    }

    private static List<Step> start() {
        List<Step> out = new ArrayList<>();
        out.add(new Step(0.0F, 0.0F, 0.0, true));
        out.add(new Step(0.0F, 0.0F, 0.0, true));
        out.addAll(straight(20));
        return out;
    }

    private static List<Step> upAWall() {
        List<Step> out = start();
        out.addAll(tip(90.0F));
        out.addAll(straight(40));
        return out;
    }

    private static List<Step> overATop() {
        List<Step> out = start();
        out.addAll(tip(90.0F));
        out.addAll(straight(30));
        out.addAll(tip(-90.0F));
        out.addAll(straight(40));
        return out;
    }

    private static List<Step> overABrink() {
        List<Step> out = start();
        out.addAll(tip(-90.0F));
        out.addAll(straight(30));
        out.addAll(tip(90.0F));
        out.addAll(straight(30));
        return out;
    }

    private static List<Step> aTurn() {
        List<Step> out = start();
        for (int i = 0; i < 9; i++) {
            out.add(new Step(0.0F, 10.0F, 0.02, false));
        }
        out.addAll(straight(10));
        for (int i = 0; i < 6; i++) {
            out.add(new Step(0.0F, -15.0F, 0.0, false));     // on the spot
        }
        for (int i = 0; i < 40; i++) {
            out.add(new Step(0.0F, 0.0F, 0.0, false));       // standing: the side bend eases out
        }
        out.addAll(straight(20));
        return out;
    }

    private static List<Step> stopHalfWayOver() {
        List<Step> out = start();
        out.add(new Step(-45.0F, 0.0F, 0.02, false));
        out.add(new Step(-45.0F, 0.0F, 0.01, false));
        for (int i = 0; i < 40; i++) {
            out.add(new Step(0.0F, 0.0F, 0.0, false));
        }
        out.addAll(straight(30));
        return out;
    }

    private static List<Step> randomWalk(long seed) {
        Random random = new Random(seed);
        List<Step> out = start();
        for (int i = 0; i < 400; i++) {
            float tipped = random.nextInt(8) == 0 ? (random.nextFloat() - 0.5F) * 60.0F : 0.0F;
            float turned = random.nextInt(4) == 0 ? (random.nextFloat() - 0.5F) * 30.0F : 0.0F;
            double moved = random.nextInt(6) == 0 ? 0.0 : random.nextDouble() * 0.04;
            out.add(new Step(tipped, turned, moved, random.nextInt(150) == 0));
        }
        return out;
    }

    /** The waist as it was (AbstractAntEntity.tickWaist, 2026-10-16 to 2026-10-18), word for word, as the reference. */
    private static final class OldWaist {
        private static final double WAIST_AT = 0.64;
        private static final double GASTER_START = 2.4;
        private static final double GASTER_END = 5.2;
        private static final double WAIST_TIGHTEST = 3.0;
        private static final float WAIST_MAX_PITCH = 60.0F;
        private static final float WAIST_MAX_YAW = 25.0F;
        private static final int WAIST_MEMORY = 64;
        private final double[] pathMark = new double[WAIST_MEMORY];
        private final float[] pitchMark = new float[WAIST_MEMORY];
        private final float[] yawMark = new float[WAIST_MEMORY];
        private int marks;
        private int newestMark;
        private double pathWalked;
        private int standingTicks;
        private float frontPitch;
        private float frontYaw;
        private float waistPitch;
        private float waistPitchO;
        private float waistYaw;
        private float waistYawO;

        void tick(float tipped, float turned, double moved, double scale, boolean restart) {
            frontPitch += tipped;
            frontYaw += turned;
            if (restart || marks == 0) {
                marks = 0;
            } else {
                float curve = (float) Math.min(Math.abs(turned), moved / (WAIST_TIGHTEST * scale / 2.0) * Mth.RAD_TO_DEG);
                float onTheSpot = turned - Math.copySign(curve, turned);
                standingTicks = moved < 1.0E-4 ? standingTicks + 1 : 0;
                float ease = standingTicks > 20 ? 0.1F : 0.0F;
                for (int i = 0; i < marks; i++) {
                    int k = Math.floorMod(newestMark - i, WAIST_MEMORY);
                    yawMark[k] += onTheSpot + (frontYaw - yawMark[k] - onTheSpot) * ease;
                }
            }
            pathWalked += moved;
            if (marks == 0 || moved >= 1.0E-4) {
                newestMark = (newestMark + 1) % WAIST_MEMORY;
                marks = Math.min(WAIST_MEMORY, marks + 1);
            }
            pathMark[newestMark] = pathWalked;
            pitchMark[newestMark] = frontPitch;
            yawMark[newestMark] = frontYaw;
            double waist = pathWalked - WAIST_AT * scale;
            float gasterPitch = averageMark(pitchMark, pathWalked - GASTER_END * scale, waist);
            float waistJointsPitch = averageMark(pitchMark, pathWalked - GASTER_START * scale, waist);
            float gasterYaw = averageMark(yawMark, pathWalked - GASTER_END * scale, waist);
            float bend = Math.max(frontPitch - gasterPitch, Math.min(frontPitch - waistJointsPitch, 0.0F));
            waistPitchO = waistPitch;
            waistYawO = waistYaw;
            waistPitch = Mth.clamp(bend, -WAIST_MAX_PITCH, WAIST_MAX_PITCH);
            waistYaw = Mth.clamp(frontYaw - gasterYaw, -WAIST_MAX_YAW, WAIST_MAX_YAW);
        }

        float pitch(float partialTick) {
            return Mth.lerp(partialTick, waistPitchO, waistPitch);
        }

        float yaw(float partialTick) {
            return Mth.lerp(partialTick, waistYawO, waistYaw);
        }

        private float averageMark(float[] pose, double from, double to) {
            if (to - from < 1.0E-9) {
                return poseAt(pose, to);
            }
            double sum = 0.0;
            double at = to;
            float poseThere = poseAt(pose, to);
            for (int i = 0; i < marks && at > from; i++) {
                int k = Math.floorMod(newestMark - i, WAIST_MEMORY);
                if (pathMark[k] >= at) {
                    continue;
                }
                double next;
                float poseNext;
                if (pathMark[k] >= from || i == 0) {
                    next = Math.max(pathMark[k], from);
                    poseNext = pose[k];
                } else {
                    int later = Math.floorMod(k + 1, WAIST_MEMORY);
                    double span = pathMark[later] - pathMark[k];
                    next = from;
                    poseNext = Mth.lerp(span > 1.0E-9 ? (float) ((from - pathMark[k]) / span) : 0.0F, pose[k], pose[later]);
                }
                sum += (at - next) * (poseThere + poseNext) / 2.0;
                at = next;
                poseThere = poseNext;
            }
            if (at > from) {
                sum += (at - from) * poseThere;
            }
            return (float) (sum / (to - from));
        }

        private float poseAt(float[] pose, double walked) {
            float there = pose[newestMark];
            for (int i = 0; i < marks; i++) {
                int k = Math.floorMod(newestMark - i, WAIST_MEMORY);
                there = pose[k];
                if (pathMark[k] <= walked) {
                    if (i > 0) {
                        int later = Math.floorMod(k + 1, WAIST_MEMORY);
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
}
