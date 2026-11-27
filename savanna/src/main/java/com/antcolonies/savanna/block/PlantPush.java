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
 * What it touches is its {@link #body}: its box and, for an animal with a hit chain (the hippo, crocodile, monitor and
 * catfish, {@code com.antcolonies.animal.HitChain}), every box along its drawn body, the same boxes a player's hits meet.
 * Its own box marks plants through the blocks' {@code entityInside}; the chain's boxes mark what they cover every other
 * tick while it moves ({@link #onTick}).
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

    /** The animal a box belongs to: itself, or the owner of one of its body's hit boxes. */
    public static Entity owner(Entity entity) {
        return entity instanceof net.neoforged.neoforge.entity.PartEntity<?> part ? part.getParent() : entity;
    }

    /** Whether {@code entity} (or the animal owning it, for a body's hit box) is big enough to push plants aside. */
    public static boolean pusher(Entity entity) {
        Entity owner = owner(entity);
        return owner.getBbWidth() >= BIG && !owner.isSpectator() && owner.isAlive();
    }

    /** Whether {@code entity} moved sideways since its last tick (the start of a push). */
    public static boolean moving(Entity entity) {
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        return dx * dx + dz * dz > 1.0E-4 || entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4;
    }

    /** Whether a pusher's box, or one of its body's hit boxes, touches {@code box} (a plant's column of blocks). */
    public static boolean touched(Level level, AABB box) {
        return !level.getEntities((Entity) null, box, PlantPush::pusher).isEmpty();
    }

    /** The boxes {@code entity} pushes plants with: its own, then each of its body's hit boxes. */
    public static java.util.List<AABB> body(Entity entity) {
        java.util.List<AABB> boxes = new java.util.ArrayList<>();
        boxes.add(entity.getBoundingBox());
        if (entity.isMultipartEntity() && entity.getParts() != null) {
            for (net.minecraft.world.entity.Entity part : entity.getParts()) {
                boxes.add(part.getBoundingBox());
            }
        }
        return boxes;
    }

    /**
     * How far round a pusher's box the client also looks for plants the server has marked (blocks): the server's box is
     * a tick or two ahead of the one the client draws, and a plant stays marked a while after its pusher has gone on.
     */
    public static final double LEAD = 1.0;

    /**
     * The client's one rule of which plants get a spring from a pusher's box, and so are drawn while the server hides
     * their plain model (review R4-12: a marked plant with no spring was drawn by nobody, it vanished): every plant in
     * a block the box overlaps (the blocks the server marks, {@link #eachBlock}), and every marked plant beside it,
     * within {@link #LEAD}.
     *
     * @param x      the plant's block
     * @param midX   the plant's own middle (its block's, with its random offset)
     * @param marked whether the server marks it pushed or settling now
     * @param reach  how far past the box's side a plant's middle may lie and still lean (blocks)
     * @return how much it leans from this box, 0 (it stands upright) to 1; below 0: no spring from this box
     */
    public static double spring(AABB box, int x, int z, double midX, double midZ, boolean marked, double reach) {
        boolean under = x >= net.minecraft.util.Mth.floor(box.minX) && x <= net.minecraft.util.Mth.floor(box.maxX - 1.0E-7)
                && z >= net.minecraft.util.Mth.floor(box.minZ) && z <= net.minecraft.util.Mth.floor(box.maxZ - 1.0E-7);
        if (!under) {
            return marked ? 0.0 : -1.0;
        }
        double dx = midX - (box.minX + box.maxX) / 2, dz = midZ - (box.minZ + box.maxZ) / 2;
        return net.minecraft.util.Mth.clamp(1 - Math.sqrt(dx * dx + dz * dz) / (box.getXsize() / 2 + reach), 0, 1);
    }

    /** Calls {@code each} for every block position {@code box} overlaps. */
    public static void eachBlock(AABB box, java.util.function.Consumer<BlockPos.MutableBlockPos> each) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int x1 = net.minecraft.util.Mth.floor(box.maxX - 1.0E-7), y1 = net.minecraft.util.Mth.floor(box.maxY - 1.0E-7), z1 = net.minecraft.util.Mth.floor(box.maxZ - 1.0E-7);
        for (int x = net.minecraft.util.Mth.floor(box.minX); x <= x1; x++) {
            for (int z = net.minecraft.util.Mth.floor(box.minZ); z <= z1; z++) {
                for (int y = net.minecraft.util.Mth.floor(box.minY); y <= y1; y++) {
                    each.accept(at.set(x, y, z));
                }
            }
        }
    }

    /** A moving animal with a hit chain marks the plants under its body's boxes, every other tick (its own box's are marked by the blocks). */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!e.isMultipartEntity() || !(e.level() instanceof ServerLevel level) || (e.tickCount & 1) != 0 || !pusher(e) || !moving(e)) {
            return;
        }
        java.util.List<AABB> boxes = body(e);
        for (AABB box : boxes.subList(1, boxes.size())) {
            eachBlock(box, at -> {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof ReedBlock reed && state.getValue(PUSH) != PUSHED) {
                    reed.push(level, at.immutable());
                } else if (state.getBlock() instanceof HippoGrassBlock mat && state.getValue(PUSH) != PUSHED) {
                    mat.push(level, at.immutable(), state);
                }
            });
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
