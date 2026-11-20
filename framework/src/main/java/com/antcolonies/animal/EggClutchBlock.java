package com.antcolonies.animal;

import com.antcolonies.colony.Watchers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The base of every clutch of eggs (the animal life kit, {@code docs/plans/animal-life-cycle.md}): eggs are a block,
 * cheap and saved with the land, with nothing ticking. Minecraft's random ticks, which only reach land near a player,
 * ask the clutch's clock ({@link ClutchClock}) now and then: old enough, watched and with room for its kind, it hatches
 * into young of its mother's kind; long past hatching it is left as empty shells ({@link #toShells}). A species makes
 * its own clutch blocks on this, with their own look, block entity type ({@link EggClutchBlockEntity}) and hatching.
 */
public abstract class EggClutchBlock extends Block implements EntityBlock {
    /** How near a watcher must be for a clutch to hatch (blocks, sideways). */
    public static final int WATCHED_WITHIN = 48;

    protected EggClutchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch)) {
            return;
        }
        EntityType<?> kind = clutch.motherKind();
        long hatchTicks = kind == null ? 0 : AnimalLives.get(kind).hatchTicks();
        boolean watched = Watchers.near(level, pos, WATCHED_WITHIN);
        boolean room = kind != null && roomFor(level, pos, kind);
        switch (ClutchClock.decide(level.getGameTime(), clutch.laid(), hatchTicks, watched, room)) {
            case HATCH -> hatch(level, pos, state, clutch, kind, random);
            case SHELLS -> toShells(level, pos, state);
            case WAIT -> {
            }
        }
    }

    /** Whether its kind has room here: fewer than its local limit near, its spawning group under its cap. */
    protected boolean roomFor(ServerLevel level, BlockPos pos, EntityType<?> kind) {
        int near = level.getEntities(kind, new AABB(pos).inflate(RoomToBreed.REACH), Entity::isAlive).size();
        return near < AnimalLives.get(kind).localLimit() && RoomToBreed.groupHasRoom(level, kind.getCategory(), new ChunkPos(pos));
    }

    /**
     * Hatches: each egg becomes a young animal of its mother's kind at the clutch, then the block is what a clutch
     * leaves ({@link #toShells}). A species may let its young out its own way (carried, in water).
     */
    protected void hatch(ServerLevel level, BlockPos pos, BlockState state, EggClutchBlockEntity clutch, EntityType<?> kind, RandomSource random) {
        for (int i = 0; i < clutch.count(); i++) {
            Entity young = kind.create(level);
            if (young == null) {
                break;
            }
            young.moveTo(pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY(), pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    random.nextFloat() * 360, 0);
            if (young instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.BREEDING, null);
            }
            if (young instanceof HasLife animal) {
                animal.life().setUp(true, random);
                animal.life().setAge(0);
            }
            level.addFreshEntity(young);
        }
        toShells(level, pos, state);       // nothing left but the shells
    }

    /**
     * Eaten eggs (the animal life kit, step 3): an animal takes up to this many eggs off the clutch here. Whether it took
     * any. With none left the clutch is gone as if long past hatching ({@link #toShells}).
     */
    public static boolean eat(ServerLevel level, BlockPos pos, int eggs) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EggClutchBlock clutchBlock) || !(level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch)
                || clutch.count() <= 0) {
            return false;
        }
        if (clutch.takeEggs(eggs)) {
            clutchBlock.toShells(level, pos, state);
        }
        return true;
    }

    /** What is left of a clutch long past hatching: here nothing (a species may leave a shells block). */
    protected void toShells(ServerLevel level, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
    }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);
}
