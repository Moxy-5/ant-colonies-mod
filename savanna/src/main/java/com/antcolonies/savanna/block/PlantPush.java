package com.antcolonies.savanna.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;

/**
 * Plants that bend when something big goes through them (2026-11-22, {@code docs/plans/papyrus-and-hippo-grass.md}
 * step 3; the user: "we're gonna need to add animations for them when a big animal or the player goes through them"):
 * the reeds ({@link ReedBlock}) and the hippo grass mats ({@link HippoGrassBlock}).
 *
 * <p>Block models can only tilt in 22.5 degree steps and every block change redraws its chunk section, so the smooth bend
 * is drawn by the client ({@code client/PlantPushClient}). The server only marks a pushed plant with {@link #PUSH}: 0
 * still (the plain model drawn), {@link #PUSHED} while a pusher touches it, {@link #SETTLING} for the spring back after it
 * leaves; while it is not 0 the plain model is hidden and the client draws the bending one. So a push costs two or three
 * block changes, and a reed bed nobody walks through costs nothing.
 *
 * <p>A pusher is a player or an animal at least {@link #BIG} wide (hippo, crocodile, monitor, otter, a player); a push
 * starts only when it moves (a hippo resting in reeds does not set them going), and lasts while it touches the plant.
 * What it touches is its {@link #footprint}: its box, or for an animal drawn much longer than its box ({@link LongBody}:
 * the hippo, crocodile and monitor) its body as drawn, nose to tail along the way it faces. A box pusher marks plants
 * through the blocks' {@code entityInside}; a long body marks what lies under its footprint every other tick while it
 * moves ({@link #onTick}).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.antcolonies.savanna.SavannaMod.MODID)
public final class PlantPush {
    public static final IntegerProperty PUSH = IntegerProperty.create("push", 0, 2);
    public static final int STILL = 0, PUSHED = 1, SETTLING = 2;
    /** The narrowest box that pushes (blocks; a player's 0.6, my pick: smaller animals slip between the stems). */
    public static final float BIG = 0.6F;
    /** How often a pushed plant looks whether its pusher is still there (ticks). */
    public static final int CHECK = 10;
    /** How long a plant stays marked after its pusher leaves, for the client's spring back to settle (ticks; the spring takes about 20). */
    public static final int SETTLE = 30;

    private PlantPush() {
    }

    /** Whether {@code entity} is big enough to push plants aside. */
    public static boolean pusher(Entity entity) {
        return entity.getBbWidth() >= BIG && !entity.isSpectator() && entity.isAlive();
    }

    /** Whether {@code entity} moved sideways since its last tick (the start of a push). */
    public static boolean moving(Entity entity) {
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        return dx * dx + dz * dz > 1.0E-4 || entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
    }

    /** Whether a pusher's footprint touches the column of blocks {@code box}. */
    public static boolean touched(Level level, AABB box) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box.inflate(LONGEST, 1, LONGEST), PlantPush::pusher)) {
            Footprint f = footprint(e);
            if (f.minY < box.maxY && f.maxY > box.minY && f.overlaps(net.minecraft.util.Mth.floor(box.minX), net.minecraft.util.Mth.floor(box.minZ))) {
                return true;
            }
        }
        return false;
    }

    /** The farthest a long body reaches from its position (blocks; a big crocodile's tail), for finding pushers near a plant. */
    static final double LONGEST = 8;

    /**
     * Where a pusher touches the ground's plants: a line from {@code (ax, az)} to {@code (bx, bz)} (its tail to its nose; a
     * box pusher's is a point, its middle) widened by {@code half} each side, from {@code minY} to {@code maxY}.
     */
    public record Footprint(double ax, double az, double bx, double bz, double half, double minY, double maxY, boolean box) {
        /** The nearest point of the line to {@code (x, z)}. */
        public double[] nearest(double x, double z) {
            double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
            double t = len2 < 1.0E-9 ? 0 : net.minecraft.util.Mth.clamp(((x - ax) * dx + (z - az) * dz) / len2, 0, 1);
            return new double[] {ax + t * dx, az + t * dz};
        }

        /** Whether the block column {@code (x, z)} lies under it: inside the box for a box pusher, else within half of the line. */
        public boolean overlaps(int x, int z) {
            if (box) {
                return x + 1 > ax - half && x < ax + half && z + 1 > az - half && z < az + half;
            }
            double[] p = nearest(x + 0.5, z + 0.5);
            double ex = Math.max(Math.abs(p[0] - (x + 0.5)) - 0.5, 0), ez = Math.max(Math.abs(p[1] - (z + 0.5)) - 0.5, 0);
            return ex * ex + ez * ez < half * half;
        }

        public int minX() {
            return net.minecraft.util.Mth.floor(Math.min(ax, bx) - half);
        }

        public int maxX() {
            return net.minecraft.util.Mth.floor(Math.max(ax, bx) + half);
        }

        public int minZ() {
            return net.minecraft.util.Mth.floor(Math.min(az, bz) - half);
        }

        public int maxZ() {
            return net.minecraft.util.Mth.floor(Math.max(az, bz) + half);
        }
    }

    /** The pusher's footprint now (its body's turn for a long body). */
    public static Footprint footprint(Entity e) {
        AABB b = e.getBoundingBox();
        double half = e.getBbWidth() / 2;
        if (e instanceof LongBody body) {
            float yaw = (e instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : e.getYRot()) * net.minecraft.util.Mth.DEG_TO_RAD;
            double fx = -net.minecraft.util.Mth.sin(yaw), fz = net.minecraft.util.Mth.cos(yaw);
            return new Footprint(e.getX() - fx * body.tailBehind(), e.getZ() - fz * body.tailBehind(),
                    e.getX() + fx * body.noseAhead(), e.getZ() + fz * body.noseAhead(), half, b.minY, b.maxY, false);
        }
        return new Footprint(e.getX(), e.getZ(), e.getX(), e.getZ(), half, b.minY, b.maxY, true);
    }

    /** A long body moving marks the plants under its footprint, every other tick (a box pusher's are marked by the blocks). */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!(e instanceof LongBody) || !(e.level() instanceof ServerLevel level) || (e.tickCount & 1) != 0 || !pusher(e) || !moving(e)) {
            return;
        }
        Footprint f = footprint(e);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = f.minX(); x <= f.maxX(); x++) {
            for (int z = f.minZ(); z <= f.maxZ(); z++) {
                if (!f.overlaps(x, z)) {
                    continue;
                }
                for (int y = net.minecraft.util.Mth.floor(f.minY); y <= net.minecraft.util.Mth.floor(f.maxY - 1.0E-7); y++) {
                    at.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
                    if (state.getBlock() instanceof ReedBlock reed && state.getValue(PUSH) != PUSHED) {
                        reed.push(level, at.immutable());
                    } else if (state.getBlock() instanceof HippoGrassBlock mat && state.getValue(PUSH) != PUSHED) {
                        mat.push(level, at.immutable(), state);
                    }
                }
            }
        }
    }

    /** The next step of a pushed plant's scheduled tick: what {@link #PUSH} becomes and when to look again (0 for never). */
    public record Next(int push, int delay) {
    }

    /** Where a plant marked {@code push} goes on its scheduled tick, touched or not. */
    public static Next next(int push, boolean touched) {
        if (touched) {
            return new Next(PUSHED, CHECK);
        }
        return push == PUSHED ? new Next(SETTLING, SETTLE) : new Next(STILL, 0);
    }

    /** Schedules {@code pos}'s next look after {@code delay} ticks. */
    static void lookAgain(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block, int delay) {
        if (delay > 0) {
            level.scheduleTick(pos, block, delay);
        }
    }
}
