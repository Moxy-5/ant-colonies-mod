package com.antcolonies.savanna.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.antcolonies.AntColoniesMod;
import com.antcolonies.savanna.worldgen.TreePlacer;
import com.mojang.math.Transformation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Step 5c, the fall (docs/DESIGN.md "Death and the fall", the user's picks "real topple" and "crush all"): a dead
 * acacia that has shed its limbs comes down in sight as a real tree does. It creaks and cracks for 2 seconds, then the
 * standing wood swings over on the hinge of its stump toward its heavier side, slowly at first and fast at the end (drawn
 * with block displays, the blocks themselves taken away), and crashes: the fallen hollow log is placed, with a boom,
 * dust and bark bits. Whatever it sweeps through is crushed: anything alive is hit hard (an insect dies, armour helps a
 * player), soft blocks in its way are smashed (they drop), and the ground and hard stone stay.
 * <p>
 * The displays carry a tag; any found when land loads (a stop in the middle of a fall) are thrown away, and the tree,
 * still marked toppling, is placed fallen at once.
 */
public final class TreeFall {
    /** The creak before it goes, and the swing over (ticks; my picks: 2 seconds, 3 seconds). */
    public static final int CREAK = 40, DROP = 60;
    /** How often the displays get a new pose (they glide between poses on the players' screens). */
    static final int KEY = 3;
    /** How hard the falling tree hits what is under it (my pick: 15 hearts before armour; an insect dies). */
    public static final float CRUSH = 30;
    /** At most this many displays draw one tree (the inside of the wood is never seen). */
    static final int MAX_DISPLAYS = 2500;
    /** The tag on the displays. */
    public static final String TAG = AntColoniesMod.MODID + "_tree_fall";
    /** The damage a falling tree does ({@code data/antcolonies/damage_type/falling_tree.json}). */
    public static final ResourceKey<DamageType> FALLING_TREE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(AntColoniesMod.MODID, "falling_tree"));

    private final ServerLevel level;
    private final Forest forest;
    private final Forest.Tree tree;
    private final TreePlacer.Placed from, to;
    private final BlockPos foot;
    /** The hinge it swings over on (the stump's edge on the side it falls to) and the line it swings round. */
    private final Vec3 pivot;
    private final Vector3f axis;
    /** The blocks that swing over (from the foot), and the outer ones of them (drawn, and sweeping what is in the way). */
    private final LongOpenHashSet moving = new LongOpenHashSet();
    private final List<BlockPos> outer = new ArrayList<>();
    private final List<BlockState> outerStates = new ArrayList<>();
    /** Every block of the standing tree (from the foot): never smashed by its own fall. */
    private final LongOpenHashSet own = new LongOpenHashSet();
    private final LongOpenHashSet looked = new LongOpenHashSet();
    private final List<Display.BlockDisplay> displays = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();
    private final double reach;
    private final double fallAz;
    /** How far over it swings: past level, until its top meets the ground. */
    private final double end;
    private int tick;
    private double lastAngle;

    TreeFall(ServerLevel level, Forest forest, Forest.Tree tree, TreePlacer.Placed from, TreePlacer.Placed to) {
        this.level = level;
        this.forest = forest;
        this.tree = tree;
        this.from = from;
        this.to = to;
        this.foot = tree.foot;
        LongOpenHashSet lying = new LongOpenHashSet();
        double sx = 0, sz = 0;
        for (int i = 0; i < to.size(); i++) {
            long k = BlockPos.asLong(to.xs()[i], to.ys()[i], to.zs()[i]);
            lying.add(k);
            int x = to.xs()[i], z = to.zs()[i];
            if (to.ys()[i] >= 0 && x * x + z * z > 9) {
                sx += x;
                sz += z;
            }
        }
        // It falls the way the log lies (the shape put it on the heavier side, DeadWood.heavierSide).
        fallAz = Math.atan2(sz, sx);
        double dx = Math.cos(fallAz), dz = Math.sin(fallAz);
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < from.size(); i++) {
            long k = BlockPos.asLong(from.xs()[i], from.ys()[i], from.zs()[i]);
            own.add(k);
            if (from.ys()[i] >= 2 && !lying.contains(k)) {
                moving.add(k);
                lowest = Math.min(lowest, from.ys()[i]);
            }
        }
        double edge = 0, far = 0;
        for (long k : moving) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            if (y == lowest) {
                edge = Math.max(edge, x * dx + z * dz + 0.5);
            }
        }
        pivot = new Vec3(foot.getX() + 0.5 + dx * edge, foot.getY() + (lowest == Integer.MAX_VALUE ? 2 : lowest), foot.getZ() + 0.5 + dz * edge);
        // Turning by a positive angle round (dz, 0, -dx) takes straight up over to the way it falls.
        axis = new Vector3f((float) dz, 0, (float) -dx).normalize();
        for (int i = 0; i < from.size(); i++) {
            int x = from.xs()[i], y = from.ys()[i], z = from.zs()[i];
            long k = BlockPos.asLong(x, y, z);
            if (!moving.contains(k)) {
                continue;
            }
            far = Math.max(far, centre(x, y, z).length());
            if (outer.size() < MAX_DISPLAYS && !inside(x, y, z)) {
                outer.add(new BlockPos(x, y, z));
                outerStates.add(from.states()[i]);
            }
        }
        reach = far + 1;
        // It swings over until its top meets the log's far end where it lies (lower down a slope, higher up one).
        double tipAlong = 0;
        for (int i = 0; i < to.size(); i++) {
            tipAlong = Math.max(tipAlong, to.xs()[i] * dx + to.zs()[i] * dz);
        }
        int tipY = Integer.MAX_VALUE;
        for (int i = 0; i < to.size(); i++) {
            if (to.xs()[i] * dx + to.zs()[i] * dz >= tipAlong - 3) {
                tipY = Math.min(tipY, to.ys()[i]);
            }
        }
        double land = foot.getY() + (tipY == Integer.MAX_VALUE ? 0 : tipY) + 0.5;
        end = Mth.clamp(Math.PI / 2 + Math.atan((pivot.y - land) / Math.max(4, reach)), Math.PI / 4, Math.PI * 0.9);
    }

    /** Whether a block is shut in on every side by other swinging blocks (never seen, not drawn). */
    private boolean inside(int x, int y, int z) {
        return moving.contains(BlockPos.asLong(x + 1, y, z)) && moving.contains(BlockPos.asLong(x - 1, y, z))
                && moving.contains(BlockPos.asLong(x, y + 1, z)) && moving.contains(BlockPos.asLong(x, y - 1, z))
                && moving.contains(BlockPos.asLong(x, y, z + 1)) && moving.contains(BlockPos.asLong(x, y, z - 1));
    }

    /** A block's middle from the hinge, while it stands. */
    private Vector3f centre(int x, int y, int z) {
        return new Vector3f((float) (foot.getX() + x + 0.5 - pivot.x), (float) (foot.getY() + y + 0.5 - pivot.y), (float) (foot.getZ() + z + 0.5 - pivot.z));
    }

    /** How far over it has swung after so many ticks of falling: slowly at first, fast at the end, as a falling pole. */
    double angle(int t) {
        double f = Mth.clamp(t / (double) DROP, 0, 1);
        return end * f * f;
    }

    /** One tick of the fall. Returns true when it is done (landed, or its land went away). */
    boolean tick() {
        if (!level.hasChunkAt(foot) || !level.hasChunkAt(BlockPos.containing(pivot))) {
            // Its land went away in the middle: the displays went with it (thrown away when it loads), and the tree,
            // still marked toppling, is placed fallen when the land is back.
            displays.clear();
            return true;
        }
        if (tick < CREAK) {
            creak();
        } else if (tick == CREAK) {
            letGo();
        } else {
            int t = tick - CREAK;
            double a = angle(t);
            sweep(lastAngle, a);
            lastAngle = a;
            if (t % KEY == 0 && t < DROP) {
                pose(angle(Math.min(DROP, t + KEY)));
            }
            if (t >= DROP) {
                land();
                return true;
            }
        }
        tick++;
        return false;
    }

    /** The creak: wood groaning and bark bits dropping from the hinge. */
    private void creak() {
        if (tick % 13 == 0) {
            level.playSound(null, pivot.x, pivot.y + 4, pivot.z, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 3f, 0.45f + 0.1f * level.getRandom().nextFloat());
            level.playSound(null, pivot.x, pivot.y + 1, pivot.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2f, 0.5f);
        }
        if (tick % 4 == 0 && !outerStates.isEmpty()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, outerStates.get(0)), pivot.x, pivot.y + 0.5, pivot.z, 6, 0.8, 0.4, 0.8, 0.05);
        }
    }

    /** It goes: the standing wood is taken away and drawn by displays from here on. */
    private void letGo() {
        level.playSound(null, pivot.x, pivot.y + 1, pivot.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 4f, 0.5f);
        for (int i = 0; i < outer.size(); i++) {
            BlockPos rel = outer.get(i);
            Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
            if (d == null) {
                continue;
            }
            d.setPos(foot.getX() + rel.getX(), foot.getY() + rel.getY(), foot.getZ() + rel.getZ());
            d.setBlockState(outerStates.get(i));
            d.setTransformationInterpolationDuration(KEY);
            d.setViewRange(3f);
            d.addTag(TAG);
            level.addFreshEntity(d);
            displays.add(d);
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < from.size(); i++) {
            long k = BlockPos.asLong(from.xs()[i], from.ys()[i], from.zs()[i]);
            if (!moving.contains(k)) {
                continue;
            }
            pos.set(foot.getX() + from.xs()[i], foot.getY() + from.ys()[i], foot.getZ() + from.zs()[i]);
            if (TreePlacer.same(level.getBlockState(pos), from.states()[i])) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        pose(angle(KEY));
    }

    /** Every display glides to where its block is when the tree has swung over so far. */
    private void pose(double a) {
        Quaternionf q = new Quaternionf().rotationAxis((float) a, axis);
        for (Display.BlockDisplay d : displays) {
            // The display stands at its block's corner; turned round the hinge, the corner goes to pivot + q (corner - pivot).
            Vector3f o = new Vector3f((float) (d.getX() - pivot.x), (float) (d.getY() - pivot.y), (float) (d.getZ() - pivot.z));
            Vector3f moved = q.transform(new Vector3f(o)).sub(o);
            d.setTransformation(new Transformation(moved, new Quaternionf(q), null, null));
            d.setTransformationInterpolationDelay(0);
        }
    }

    /** What the swinging wood passes through between two angles: soft blocks smashed, anything alive hit. */
    private void sweep(double a0, double a1) {
        if (a1 <= a0) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Quaternionf q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (BlockPos rel : outer) {
            Vector3f c = centre(rel.getX(), rel.getY(), rel.getZ());
            int n = Math.max(1, (int) Math.ceil((a1 - a0) * c.length()));
            for (int s = 1; s <= n; s++) {
                q.rotationAxis((float) (a0 + (a1 - a0) * s / n), axis);
                q.transform(c, p);
                pos.set(Mth.floor(pivot.x + p.x), Mth.floor(pivot.y + p.y), Mth.floor(pivot.z + p.z));
                if (!looked.add(pos.asLong()) || own.contains(BlockPos.asLong(pos.getX() - foot.getX(), pos.getY() - foot.getY(), pos.getZ() - foot.getZ()))) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && smashes(level, pos, state)) {
                    level.destroyBlock(pos, true);
                }
            }
        }
        // Anything alive in the swept wood is hit, once.
        AABB box = new AABB(pivot, pivot).inflate(reach + 2);
        int n = Math.max(1, (int) Math.ceil((a1 - a0) * reach));
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (hit.contains(e.getUUID())) {
                continue;
            }
            AABB b = e.getBoundingBox();
            Vec3[] points = {new Vec3(b.getCenter().x, b.minY + 0.05, b.getCenter().z), b.getCenter(), new Vec3(b.getCenter().x, b.maxY - 0.05, b.getCenter().z)};
            search:
            for (int s = 1; s <= n; s++) {
                q.rotationAxis((float) -(a0 + (a1 - a0) * s / n), axis);
                for (Vec3 w : points) {
                    // Turned back, where would this point be on the standing tree?
                    q.transform((float) (w.x - pivot.x), (float) (w.y - pivot.y), (float) (w.z - pivot.z), p);
                    long k = BlockPos.asLong(Mth.floor(pivot.x + p.x) - foot.getX(), Mth.floor(pivot.y + p.y) - foot.getY(), Mth.floor(pivot.z + p.z) - foot.getZ());
                    if (moving.contains(k)) {
                        crush(level, e);
                        hit.add(e.getUUID());
                        break search;
                    }
                }
            }
        }
    }

    /** The crash: the displays go, the fallen log is placed, a boom, dust and bark bits. */
    private void land() {
        for (Display.BlockDisplay d : displays) {
            d.discard();
        }
        displays.clear();
        forest.landed(level, tree, from, to);
        crushUnder(level, foot, to, hit); // what lay where the log now lies
        level.playSound(null, pivot.x, pivot.y, pivot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4f, 0.45f);
        level.playSound(null, pivot.x, pivot.y, pivot.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 4f, 0.4f);
        double dx = Math.cos(fallAz), dz = Math.sin(fallAz);
        for (int i = 0; i < reach; i += 2) {
            double x = pivot.x + dx * i, z = pivot.z + dz * i;
            BlockState ground = level.getBlockState(BlockPos.containing(x, foot.getY() - 1, z));
            if (!ground.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, foot.getY() + 0.5, z, 20, 1.5, 0.5, 1.5, 0.15);
            }
            level.sendParticles(ParticleTypes.POOF, x, foot.getY() + 1, z, 4, 1.5, 0.5, 1.5, 0.05);
            if (!outerStates.isEmpty()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, outerStates.get(i % outerStates.size())), x, foot.getY() + 1.5, z, 8, 1, 0.6, 1, 0.1);
            }
        }
    }

    /**
     * What a falling giant smashes on its way (my pick for "crush all"): leaves, plants and anything soft (easier to
     * break than stone); the ground (dirt, sand, gravel, what a shovel digs), stone, wood and fluids stop it.
     */
    static boolean smashes(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.is(BlockTags.LEAVES) || state.canBeReplaced()) {
            return true;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness < 1.5f && !state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /**
     * Where the log comes to lie, soft blocks are smashed first (whether it fell in sight or not), so it lies whole
     * except where the ground or hard stone stopped it.
     */
    static void clearWhereItLies(ServerLevel level, BlockPos foot, TreePlacer.Placed standing, TreePlacer.Placed lying) {
        LongOpenHashSet own = new LongOpenHashSet();
        if (standing != null) {
            for (int i = 0; i < standing.size(); i++) {
                own.add(BlockPos.asLong(standing.xs()[i], standing.ys()[i], standing.zs()[i]));
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < lying.size(); i++) {
            int x = lying.xs()[i], y = lying.ys()[i], z = lying.zs()[i];
            if (y < 0 || own.contains(BlockPos.asLong(x, y, z))) {
                continue;
            }
            pos.set(foot.getX() + x, foot.getY() + y, foot.getZ() + z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !TreePlacer.isOpen(state) && smashes(level, pos, state)) {
                level.destroyBlock(pos, true);
            }
        }
    }

    /** Something alive under the falling tree is hit. */
    static void crush(ServerLevel level, LivingEntity e) {
        e.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALLING_TREE)), CRUSH);
    }

    /** A tree that fell out of sight: anything alive where the log now lies is crushed (step 5c). */
    static void crushUnder(ServerLevel level, BlockPos foot, TreePlacer.Placed lying, Set<UUID> already) {
        LongOpenHashSet blocks = new LongOpenHashSet();
        for (int i = 0; i < lying.size(); i++) {
            if (lying.ys()[i] >= 0) {
                blocks.add(BlockPos.asLong(foot.getX() + lying.xs()[i], foot.getY() + lying.ys()[i], foot.getZ() + lying.zs()[i]));
            }
        }
        AABB box = AABB.of(lying.box(foot)).inflate(1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            BlockPos at = e.blockPosition();
            if (!already.contains(e.getUUID()) && (blocks.contains(at.asLong()) || blocks.contains(at.above().asLong()))) {
                crush(level, e);
            }
        }
    }

    /** The tree this fall draws. */
    public Forest.Tree tree() {
        return tree;
    }
}
