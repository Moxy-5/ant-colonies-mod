package com.antcolonies.savanna.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.antcolonies.animal.AnimalLives;
import com.antcolonies.animal.EggClutchBlock;
import com.antcolonies.animal.EggClutchBlockEntity;
import com.antcolonies.animal.RoomToBreed;
import com.antcolonies.savanna.animal.CrocodileEntity;
import com.antcolonies.savanna.registry.SavannaBlockEntities;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Nile crocodile's nest on a sand bank (M9, animal life cycle step 4, 2026-11-20; the sand viewer's nest the user
 * saw in the life viewer, {@code tools/breeding_art.py}): the mother digs a pit in dry sand above the water, lays,
 * scrapes the sand back over the eggs (a low heap of churned sand, {@link #OPEN} false) and guards it
 * ({@code CrocodileBreeding}). Drawn in the sand's own tone ({@link #TONE}).
 *
 * <p>The kit's clutch ({@link EggClutchBlock}): after the crocodile's hatching days, watched and with room for
 * crocodiles, the eggs chirp ({@link #CHIRPS} over 0; real hatchlings call from inside the eggs and the mother comes):
 * the mother, if she still guards it, digs it open and the hatchlings crawl out ({@link #letOut}) for her to carry to the
 * water. With no mother there, or one that has not come after two more of Minecraft's random ticks (about two minutes),
 * they dig themselves out and make for the water alone. Only as many hatch as the local limit has room for (my pick, the
 * game balancer: a clutch never fills the river past its limit); the rest of the eggs fail, as most real eggs do.
 */
public class CrocodileNestBlock extends EggClutchBlock {
    public static final MapCodec<CrocodileNestBlock> CODEC = simpleCodec(CrocodileNestBlock::new);
    /** Dug open: the pit with the eggs showing (while she lays, and as they hatch). */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    /** The sand's tone it is drawn in. */
    public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);
    /** How many of Minecraft's random ticks the eggs have called on for (0: quiet, not yet hatching). */
    public static final IntegerProperty CHIRPS = IntegerProperty.create("chirps", 0, 3);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2.5, 15);

    /** The river sand's three tones (the blocks' own colour words). */
    public enum Tone implements StringRepresentable {
        RED, GOLDEN, PALE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public CrocodileNestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(TONE, Tone.GOLDEN).setValue(CHIRPS, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, TONE, CHIRPS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EggClutchBlockEntity(SavannaBlockEntities.EGG_CLUTCH.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(pos).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
        return canSurvive(state, level, pos) ? state : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    /** Whether the eggs are calling (old enough, watched, room): the mother comes to dig them out. */
    public static boolean hatching(BlockState state) {
        return state.getBlock() instanceof CrocodileNestBlock && state.getValue(CHIRPS) > 0;
    }

    /** The tone of the sand under a spot (golden where it is no river sand). */
    public static Tone toneUnder(LevelReader level, BlockPos pos) {
        Block below = level.getBlockState(pos.below()).getBlock();
        if (isTone(below, SavannaBlocks.RED_SAND)) {
            return Tone.RED;
        }
        return isTone(below, SavannaBlocks.PALE_SAND) ? Tone.PALE : Tone.GOLDEN;
    }

    private static boolean isTone(Block block, RiverSand.Tone tone) {
        return tone.all().stream().anyMatch(b -> b.get() == block);
    }

    /**
     * Lays a clutch here: the nest dug open with {@code eggs} eggs of this kind in it (she covers it after,
     * {@link #cover}). False if the spot does not hold a nest (no firm ground under it, or water in it).
     */
    public static boolean lay(ServerLevel level, BlockPos pos, EntityType<?> kind, int eggs) {
        BlockState nest = SavannaBlocks.CROCODILE_NEST.get().defaultBlockState().setValue(OPEN, true).setValue(TONE, toneUnder(level, pos));
        if (!level.getBlockState(pos).canBeReplaced() || !nest.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, nest, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch) {
            clutch.lay(kind, eggs, level.getGameTime());
            return true;
        }
        return false;
    }

    /** Scrapes the sand back over the eggs (or digs them open): the nest closed or open. */
    public static void cover(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CrocodileNestBlock && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        }
    }

    /**
     * Old enough, watched and with room: the eggs call (the nest's {@link #CHIRPS} go up). A mother guarding the nest
     * comes and digs them out; with none, or after two more calls unanswered, the hatchlings dig themselves out.
     */
    @Override
    protected void hatch(ServerLevel level, BlockPos pos, BlockState state, EggClutchBlockEntity clutch, EntityType<?> kind, RandomSource random) {
        int chirps = state.getValue(CHIRPS);
        if (chirps < 3 && motherNear(level, pos)) {
            level.setBlock(pos, state.setValue(CHIRPS, chirps + 1), Block.UPDATE_ALL);
            return;
        }
        letOut(level, pos, null);
    }

    /** Whether a mother guarding this nest is near enough to come to it. */
    private static boolean motherNear(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(CrocodileEntity.class, new AABB(pos).inflate(32), c -> c.isAlive() && pos.equals(c.nestAt())).isEmpty();
    }

    /**
     * The eggs hatch now: as many hatchlings as there is room for crawl out of the nest (a mother's young know her),
     * and the nest is gone, only the sand left. The young that came out (none if the nest held no eggs).
     */
    public static List<CrocodileEntity> letOut(ServerLevel level, BlockPos pos, @Nullable CrocodileEntity mother) {
        List<CrocodileEntity> out = new ArrayList<>();
        if (!(level.getBlockEntity(pos) instanceof EggClutchBlockEntity clutch) || !(level.getBlockState(pos).getBlock() instanceof CrocodileNestBlock)) {
            return out;
        }
        EntityType<CrocodileEntity> kind = SavannaEntities.CROCODILE.get();
        int near = level.getEntities(kind, new AABB(pos).inflate(RoomToBreed.REACH), e -> e.isAlive()).size();
        int room = Math.max(1, AnimalLives.get(kind).localLimit() - near);
        int count = Math.min(clutch.count(), room);
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            CrocodileEntity young = kind.create(level);
            if (young == null) {
                break;
            }
            young.moveTo(pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    random.nextFloat() * 360, 0);
            young.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.BREEDING, null);
            young.life().setUp(true, random);
            young.life().setAge(0);
            young.hatchedBy(mother);
            level.addFreshEntity(young);
            out.add(young);
        }
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.8F, 0.8F);
        level.removeBlock(pos, false);
        return out;
    }

    /** The eggs calling, now and then while they hatch (real hatchlings yelp from inside the eggs; my pick of sound). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(CHIRPS) > 0 && random.nextFloat() < 0.35F) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, SoundEvents.FROG_AMBIENT, SoundSource.BLOCKS,
                    0.5F, 1.7F + random.nextFloat() * 0.3F, false);
        }
    }
}
