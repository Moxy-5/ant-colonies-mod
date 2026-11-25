package com.antcolonies.savanna.block;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.entity.AbstractAntEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thorny acacia twig (M7.5): a zigzag twig with pairs of long straight white spines and small hooked thorns, as on a
 * real umbrella thorn. It grows at the edges of the crown and all over young trees (a young acacia is the most thorny, to
 * keep browsers off). Like a sweet berry bush it slows anything big pushing through it and pricks it while it moves;
 * insects are far smaller than the spines and walk between them unharmed.
 */
public class ThornyTwigBlock extends Block {
    public static final MapCodec<ThornyTwigBlock> CODEC = simpleCodec(ThornyTwigBlock::new);
    /** The damage the spines do (its own death message, data/antcolonies/damage_type/thorns.json). */
    public static final ResourceKey<DamageType> THORNS = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "thorns"));
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 16, 14);

    public ThornyTwigBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Minecraft's path planner keeps mobs out of berry bushes and cactus because it knows them by name; any other block
     * that hurts must say so itself, or animals walk straight into young acacias (review R7-29). The insects plan with
     * their own path finder and are not pricked.
     */
    @Override
    public net.minecraft.world.level.pathfinder.PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos,
            @javax.annotation.Nullable net.minecraft.world.entity.Mob mob) {
        return net.minecraft.world.level.pathfinder.PathType.DAMAGE_OTHER;
    }

    /** Whether the spines slow and prick this: anything alive but the insects. */
    public static boolean pricks(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof AbstractAntEntity);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!pricks(entity)) {
            return;
        }
        entity.makeStuckInBlock(state, new Vec3(0.8, 0.75, 0.8));
        if (!level.isClientSide && (Math.abs(entity.getX() - entity.xOld) >= 0.003 || Math.abs(entity.getZ() - entity.zOld) >= 0.003)) {
            entity.hurt(level.damageSources().source(THORNS), 1.0F);
        }
    }
}
