package com.antcolonies.savanna.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.block.HippoGrassBlock;
import com.antcolonies.savanna.block.PlantPush;
import com.antcolonies.savanna.block.ReedBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The bend of pushed plants, drawn by the client (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md} step 3; how
 * and why: {@link PlantPush}). Every tick it looks round each big entity near the camera for reeds and hippo grass mats
 * it touches and gives each a spring: a reed column leans away from the pusher (and a little along its way), up to
 * {@link #MOST_LEAN}, the top most; a mat is pressed under the surface and aside. When the pusher goes, the spring brings
 * it back with a small overshoot in about a second.
 *
 * <p>While the server marks a plant pushed its plain model is hidden, and this draws the plant's own model bent: a reed
 * column curved from its foot (offset by its height squared, so the stalk bows rather than tips as a stick), a mat
 * moved down and aside. A marked plant with no spring yet (the client joined mid-push) is drawn upright.
 */
@EventBusSubscriber(modid = SavannaMod.MODID, value = Dist.CLIENT)
public final class PlantPushClient {
    /** The most a reed column leans (radians; about 40 degrees, the plan's). */
    static final float MOST_LEAN = 0.7F;
    /**
     * How far past the pusher's side a plant's middle may lie and still lean (blocks): half a block, so every plant whose
     * block the pusher's box overlaps (the ones the server marks, {@link PlantPush}) leans, those at the box's edge least.
     */
    static final float REACH = 0.5F;
    /** The spring's pull and damping per tick (my picks: back in about 20 ticks with a small overshoot). */
    static final float PULL = 0.3F, DAMP = 0.42F;
    /** How deep a mat is pressed at most, and how far aside (blocks). */
    static final float MOST_DIP = 0.35F, MOST_ASIDE = 0.25F;
    /** Only entities this near the camera push plants on the client (blocks). */
    static final double NEAR = 64;

    /** A plant's spring: its lean (a reed column: radians toward x and z; a mat: how far pressed, aside and down). */
    static final class Sway {
        final boolean reed;
        float x, z, vx, vz, oldX, oldZ, wantX, wantZ;

        Sway(boolean reed) {
            this.reed = reed;
        }

        void step() {
            oldX = x;
            oldZ = z;
            vx += PULL * (wantX - x) - DAMP * vx;
            vz += PULL * (wantZ - z) - DAMP * vz;
            x += vx;
            z += vz;
        }

        boolean settled() {
            return wantX == 0 && wantZ == 0 && Math.abs(x) + Math.abs(z) + Math.abs(vx) + Math.abs(vz) < 0.002F;
        }
    }

    /** The springs by the plant's foot (a reed column's bottom block, a mat's own block). */
    private static final Map<BlockPos, Sway> SWAYS = new HashMap<>();
    private static ClientLevel swaysOf;

    private PlantPushClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != swaysOf) {
            SWAYS.clear();
            swaysOf = level;
        }
        if (level == null || mc.isPaused() || !level.tickRateManager().runsNormally()) {
            return;                                            // the springs keep the game's time (frozen with /tick freeze)
        }
        for (Sway sway : SWAYS.values()) {
            sway.wantX = 0;
            sway.wantZ = 0;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity entity : level.entitiesForRendering()) {
            if (PlantPush.pusher(entity) && entity.position().distanceToSqr(eye) < NEAR * NEAR) {
                feel(level, entity);
            }
        }
        for (Iterator<Map.Entry<BlockPos, Sway>> it = SWAYS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Sway> e = it.next();
            Sway sway = e.getValue();
            float most = sway.reed ? MOST_LEAN : 1;              // a mat's want is already in blocks, capped below
            float len = Mth.sqrt(sway.wantX * sway.wantX + sway.wantZ * sway.wantZ);
            if (len > most) {
                sway.wantX *= most / len;
                sway.wantZ *= most / len;
            }
            sway.step();
            if (sway.settled() && level.getBlockState(e.getKey()).getOptionalValue(PlantPush.PUSH).orElse(0) == PlantPush.STILL) {
                it.remove();
            }
        }
    }

    /** Adds what {@code entity} does to the plants its box (a little widened) touches. */
    private static void feel(ClientLevel level, Entity entity) {
        AABB box = entity.getBoundingBox().deflate(1.0E-7);     // the blocks the server's push sees (Entity.checkInsideBlocks)
        Vec3 move = entity.getDeltaMovement().multiply(1, 0, 1);
        double mx = 0, mz = 0;
        if (move.lengthSqr() > 1.0E-5) {
            Vec3 m = move.normalize();
            mx = m.x;
            mz = m.z;
        }
        double half = entity.getBbWidth() / 2 + REACH;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        java.util.Set<BlockPos> felt = new java.util.HashSet<>();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                    at.set(x, y, z);
                    BlockState state = level.getBlockState(at);
                    boolean reed = state.getBlock() instanceof ReedBlock;
                    if (!reed && !(state.getBlock() instanceof HippoGrassBlock)) {
                        continue;
                    }
                    BlockPos foot = reed ? ((ReedBlock) state.getBlock()).bottom(level, at) : at.immutable();
                    if (!felt.add(foot)) {
                        continue;                              // one push a column from each pusher
                    }
                    Vec3 off = state.getOffset(level, at);
                    double dx = x + 0.5 + off.x - entity.getX(), dz = z + 0.5 + off.z - entity.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double close = Mth.clamp(1 - d / half, 0, 1);
                    if (close <= 0) {
                        continue;
                    }
                    double ux = d > 1.0E-3 ? dx / d : 0, uz = d > 1.0E-3 ? dz / d : 0;
                    double wx = ux + 0.6 * mx, wz = uz + 0.6 * mz, wl = Math.sqrt(wx * wx + wz * wz);
                    if (wl < 1.0E-3) {
                        continue;
                    }
                    Sway sway = SWAYS.computeIfAbsent(foot, k -> new Sway(reed));
                    double amount = (reed ? MOST_LEAN : 1) * close;
                    sway.wantX += (float) (wx / wl * amount);
                    sway.wantZ += (float) (wz / wl * amount);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SWAYS.isEmpty() || event.getPoseStack() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RenderType.cutout());
        PoseStack pose = event.getPoseStack();
        for (Map.Entry<BlockPos, Sway> e : SWAYS.entrySet()) {
            BlockPos foot = e.getKey();
            BlockState first = level.getBlockState(foot);
            if (first.getOptionalValue(PlantPush.PUSH).orElse(0) == PlantPush.STILL) {
                continue;                                      // the plain model is drawn
            }
            Sway s = e.getValue();
            float lx = Mth.lerp(partial, s.oldX, s.x), lz = Mth.lerp(partial, s.oldZ, s.z);
            Vec3 off = first.getOffset(level, foot);
            if (s.reed) {
                int height = 0;
                while (level.getBlockState(foot.above(height)).getBlock() instanceof ReedBlock) {
                    height++;
                }
                double bx = foot.getX() + 0.5 + off.x - cam.x, by = foot.getY() - cam.y, bz = foot.getZ() + 0.5 + off.z - cam.z;
                Bend bend = new Bend(out, (float) bx, (float) by, (float) bz, height, lx, lz, 0, 0);
                for (int k = 0; k < height; k++) {
                    draw(level, foot.above(k), pose, bend, cam, off);
                }
            } else {
                float len = Mth.sqrt(lx * lx + lz * lz);
                float dip = Math.min(len, 1) * MOST_DIP;
                Bend shift = new Bend(out, 0, 0, 0, 1, 0, 0, len > 1.0E-4 ? lx / len * Math.min(len, 1) * MOST_ASIDE : 0, dip);
                shift.aside(len > 1.0E-4 ? lz / len * Math.min(len, 1) * MOST_ASIDE : 0);
                draw(level, foot, pose, shift, cam, off);
            }
        }
        buffers.endBatch(RenderType.cutout());
    }

    /** Draws the plain model of the plant block at {@code pos} (its own turn and offset) into {@code out}. */
    private static void draw(ClientLevel level, BlockPos pos, PoseStack pose, VertexConsumer out, Vec3 cam, Vec3 off) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(PlantPush.PUSH)) {
            return;
        }
        BlockState plain = state.setValue(PlantPush.PUSH, PlantPush.STILL);
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(plain);
        int light = LevelRenderer.getLightColor(level, pos);
        pose.pushPose();
        pose.translate(pos.getX() - cam.x + off.x, pos.getY() - cam.y, pos.getZ() - cam.z + off.z);
        RandomSource random = RandomSource.create();
        long seed = plain.getSeed(pos);
        for (Direction side : new Direction[] {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            random.setSeed(seed);
            for (BakedQuad quad : model.getQuads(plain, side, random, ModelData.EMPTY, RenderType.cutout())) {
                out.putBulkData(pose.last(), quad, 1, 1, 1, 1, light, OverlayTexture.NO_OVERLAY, false);
            }
        }
        pose.popPose();
    }

    /**
     * Passes vertices on, bent: a reed column's points moved by its lean times the square of their height up the column
     * (out sideways by the sine, down by one less the cosine, so the top swings round as on a stalk), or a mat's moved
     * aside and down. Positions arrive relative to the camera, as the level is drawn.
     */
    static final class Bend implements VertexConsumer {
        private final VertexConsumer out;
        private final float bx, by, bz, height, ux, uz, angle, dip;
        private float asideX, asideZ;

        Bend(VertexConsumer out, float bx, float by, float bz, int height, float leanX, float leanZ, float asideX, float dip) {
            this.out = out;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
            this.height = Math.max(1, height);
            float a = Mth.sqrt(leanX * leanX + leanZ * leanZ);
            this.angle = a;
            this.ux = a > 1.0E-5F ? leanX / a : 0;
            this.uz = a > 1.0E-5F ? leanZ / a : 0;
            this.asideX = asideX;
            this.dip = dip;
        }

        void aside(float z) {
            asideZ = z;
        }

        private float[] bent(float x, float y, float z) {
            float s = Mth.clamp((y - by) / height, 0, 1), s2 = s * s;
            float out = height * Mth.sin(angle) * s2, down = height * (1 - Mth.cos(angle)) * s2;
            return new float[] {x + ux * out + asideX, y - down - dip, z + uz * out + asideZ};
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            float[] p = bent(x, y, z);
            return out.addVertex(p[0], p[1], p[2]);
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            float[] p = bent(x, y, z);
            out.addVertex(p[0], p[1], p[2], color, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return out.setColor(red, green, blue, alpha);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return out.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return out.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return out.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return out.setNormal(x, y, z);
        }
    }
}
