package com.antcolonies.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/**
 * A bone's boxes made ready once (2026-11-09, the drawn-only insects' step 3): every face of every box of the bone with
 * its box's own turn already worked in, kept as plain numbers, so drawing the bone is one pass over them with the
 * bone's matrix. GeckoLib works each box's turn out again for every insect every frame (three rotations and two shifts
 * a box, a new matrix and new vectors a corner); the profile of a crowd ({@code tools/film_profile.py}) gave that and
 * the writing of the corners about 38 percent of the drawing time.
 *
 * <p><b>Buried faces are left out</b>: a face with all four corners inside one other box of the same bone can never be
 * seen (the models are built from overlapping boxes, so many are). Only boxes of the same bone count, as they never
 * move against each other; a face lying in another box's surface is kept. This takes the textures to be solid: a model
 * with see-through pixels on a box that hides another's face would show the gap (none of ours has any).
 *
 * <p>The look is the same as GeckoLib's own drawing, corner for corner (its flat-box normal fix included); only the
 * order of the sums differs, by a rounding. {@code -Dantcolonies.plainBoxes=true} draws GeckoLib's way, to compare.
 */
public final class BoneMesh {
    /** GeckoLib's own drawing in place of the ready-made faces (for comparing pictures and timings). */
    public static final boolean PLAIN = Boolean.getBoolean("antcolonies.plainBoxes");
    /** Numbers a face: its normal (3), which of its box's sizes are 0 (1), then four corners of x y z u v. */
    private static final int FACE = 24;
    /** How far inside another box a corner must be to count as buried (model units; a 16th of a block is 1). */
    private static final float INSIDE = 0.001F;
    /**
     * The ready faces of each bone, by the bone itself (two models' bones of one name are equal to GeckoLib). A reload
     * of the models makes new bones, so the old ones are dropped once this many have piled up.
     */
    private static final Map<GeoBone, float[]> MESHES = new IdentityHashMap<>();
    private static final int MOST = 4096;

    private BoneMesh() {
    }

    /** Draws the bone's boxes: what {@code GeoRenderer.renderCubesOfBone} draws for a bone that is not hidden. */
    public static void draw(PoseStack.Pose pose, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
            int colour) {
        float[] mesh = MESHES.get(bone);
        if (mesh == null) {
            if (MESHES.size() >= MOST) {
                MESHES.clear();
            }
            mesh = bake(bone);
            MESHES.put(bone, mesh);
        }
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02(), n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();
        for (int i = 0; i < mesh.length; i += FACE) {
            float x = mesh[i], y = mesh[i + 1], z = mesh[i + 2];
            float nx = n00 * x + n10 * y + n20 * z;
            float ny = n01 * x + n11 * y + n21 * z;
            float nz = n02 * x + n12 * y + n22 * z;
            int flat = (int) mesh[i + 3];
            if (flat != 0) {                    // RenderUtil.fixInvertedFlatCube: a flat box's normals never point away
                if (nx < 0.0F && (flat & 6) != 0) {
                    nx = -nx;
                }
                if (ny < 0.0F && (flat & 5) != 0) {
                    ny = -ny;
                }
                if (nz < 0.0F && (flat & 3) != 0) {
                    nz = -nz;
                }
            }
            for (int v = i + 4; v < i + FACE; v += 5) {
                x = mesh[v];
                y = mesh[v + 1];
                z = mesh[v + 2];
                buffer.addVertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31,
                        m02 * x + m12 * y + m22 * z + m32, colour, mesh[v + 3], mesh[v + 4], packedOverlay, packedLight,
                        nx, ny, nz);
            }
        }
    }

    /** A box of the bone, ready: its own turn as matrices, and its plain bounds before the turn. */
    private record Box(GeoCube cube, Matrix4f turn, Matrix3f normals, Matrix4f back, float[] lo, float[] hi) {
    }

    private static float[] bake(GeoBone bone) {
        List<Box> boxes = new ArrayList<>();
        for (GeoCube cube : bone.getCubes()) {
            PoseStack stack = new PoseStack();  // exactly GeoRenderer.renderCube's own steps, on a clean matrix
            RenderUtil.translateToPivotPoint(stack, cube);
            RenderUtil.rotateMatrixAroundCube(stack, cube);
            RenderUtil.translateAwayFromPivotPoint(stack, cube);
            Matrix4f turn = new Matrix4f(stack.last().pose());
            float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f p = vertex.position();
                    lo[0] = Math.min(lo[0], p.x());
                    lo[1] = Math.min(lo[1], p.y());
                    lo[2] = Math.min(lo[2], p.z());
                    hi[0] = Math.max(hi[0], p.x());
                    hi[1] = Math.max(hi[1], p.y());
                    hi[2] = Math.max(hi[2], p.z());
                }
            }
            boxes.add(new Box(cube, turn, new Matrix3f(stack.last().normal()), new Matrix4f(turn).invert(), lo, hi));
        }
        List<float[]> faces = new ArrayList<>();
        Vector3f point = new Vector3f();
        for (Box box : boxes) {
            int flat = (box.cube().size().x() == 0.0 ? 1 : 0) | (box.cube().size().y() == 0.0 ? 2 : 0)
                    | (box.cube().size().z() == 0.0 ? 4 : 0);
            for (GeoQuad quad : box.cube().quads()) {
                if (quad == null) {
                    continue;
                }
                float[] face = new float[FACE];
                Vector3f normal = box.normals().transform(new Vector3f(quad.normal()));
                face[0] = normal.x();
                face[1] = normal.y();
                face[2] = normal.z();
                face[3] = flat;
                GeoVertex[] vertices = quad.vertices();
                for (int v = 0; v < 4; v++) {
                    box.turn().transformPosition(vertices[v].position(), point);
                    face[4 + v * 5] = point.x();
                    face[5 + v * 5] = point.y();
                    face[6 + v * 5] = point.z();
                    face[7 + v * 5] = vertices[v].texU();
                    face[8 + v * 5] = vertices[v].texV();
                }
                if (!buried(face, box, boxes)) {
                    faces.add(face);
                }
            }
        }
        float[] mesh = new float[faces.size() * FACE];
        for (int i = 0; i < faces.size(); i++) {
            System.arraycopy(faces.get(i), 0, mesh, i * FACE, FACE);
        }
        return mesh;
    }

    /** Whether all four corners of the face lie inside one other box of the bone. */
    private static boolean buried(float[] face, Box own, List<Box> boxes) {
        Vector3f point = new Vector3f();
        for (Box other : boxes) {
            if (other == own) {
                continue;
            }
            boolean inside = true;
            for (int v = 0; v < 4 && inside; v++) {
                other.back().transformPosition(face[4 + v * 5], face[5 + v * 5], face[6 + v * 5], point);
                inside = point.x() > other.lo()[0] + INSIDE && point.x() < other.hi()[0] - INSIDE
                        && point.y() > other.lo()[1] + INSIDE && point.y() < other.hi()[1] - INSIDE
                        && point.z() > other.lo()[2] + INSIDE && point.z() < other.hi()[2] - INSIDE;
            }
            if (inside) {
                return true;
            }
        }
        return false;
    }
}
