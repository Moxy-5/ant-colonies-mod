package com.antcolonies.block;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Grama grass, the harvester ants' rich seed (the user's decisions, 2026-10-16, docs/DESIGN.md "Custom plants"). Two
 * kinds share this block: blue grama (Bouteloua gracilis, meadows on the plains) and black grama (B. eriopoda, bunches
 * in the badlands, which also grows on sand and red sand).
 *
 * <p>Its life, like real grama: a planted seed comes up {@link Stage#YOUNG} and grows into {@link Stage#LEAVES}; grown
 * plants wait for rain, which sends up seed stalks ({@link Stage#FLOWERING}), so a whole meadow ripens together into
 * {@link Stage#RIPE} (the ants' jackpot) about 2 days later; ripe heads hold seed about 3 days, now and then shedding
 * one on the ground, and then dry off ({@link Stage#DRIED}) and go back to leaves about 2 days later. With no rain for
 * about 5 days a grown plant seeds anyway. The rain is the world's weather, not rain falling on the plant: in Minecraft
 * the badlands never show rain, but real black grama seeds after the desert's summer storms.
 *
 * <p>The ants cut seeds off ripe heads (the species file's food {@code plants}, state {@code stage: ripe}); a head cut
 * bare dries off ({@link #afterLastPiece}), the plant is never broken. Bone meal pushes it a stage on. Breaking a ripe
 * plant gives its seeds (the loot table). It does not spread.
 *
 * <p>Timing (see {@link GramaLife}): a block gets a random tick about every 68 seconds. Each stage counts {@link #AGE} up
 * from 0 to 7 on random ticks with a chance per stage, so a stage takes 8 / chance ticks on average with little spread,
 * and plants that began a stage together end it close together. My picks, to confirm.
 */
public class GramaGrassBlock extends BushBlock implements BonemealableBlock, EatenInStages {
    public static final MapCodec<GramaGrassBlock> CODEC = simpleCodec(properties -> new GramaGrassBlock(properties, false, () -> null));

    /** Where the plant is in its life. */
    public enum Stage implements StringRepresentable {
        YOUNG("young"), LEAVES("leaves"), FLOWERING("flowering"), RIPE("ripe"), DRIED("dried");

        private final String name;

        Stage(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Stage> STAGE = EnumProperty.create("stage", Stage.class);
    /** How far through its stage it is (0 to 7). */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);

    /** Ripe heads shed a seed on a random tick with this chance (my pick: about one every 7 minutes)... */
    static final float SHED_CHANCE = 1.0F / 6.0F;
    /** ...while fewer than this many seeds lie round the plant (kept few, so no lag). */
    static final int MOST_SHED = 2;

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    /** Whether it also grows on sand and red sand (black grama). */
    private final boolean onSand;
    /** The seed item it sheds (blue or black grama seeds). */
    private final Supplier<Item> seeds;

    public GramaGrassBlock(Properties properties, boolean onSand, Supplier<Item> seeds) {
        super(properties);
        this.onSand = onSand;
        this.seeds = seeds;
        registerDefaultState(stateDefinition.any().setValue(STAGE, Stage.YOUNG).setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE, AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(net.minecraft.world.level.block.Blocks.FARMLAND)
                || onSand && state.is(BlockTags.SAND);
    }

    // --- its life -----------------------------------------------------------------------------------------------

    /** One random tick of the plant's life (see {@link GramaLife}): {@code raining} in the world, {@code roll} from 0 to 1. */
    public static BlockState grow(BlockState state, boolean raining, float roll) {
        GramaLife.Step step = GramaLife.step(state.getValue(STAGE), state.getValue(AGE), raining, roll);
        return state.setValue(STAGE, step.stage()).setValue(AGE, step.age());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == Stage.RIPE && random.nextFloat() < SHED_CHANCE) {
            shed(level, pos, random);
        }
        BlockState next = grow(state, level.isRaining(), random.nextFloat());
        if (next != state) {
            level.setBlock(pos, next, 2);
        }
    }

    /** A ripe head drops a seed on the ground under the plant, unless a few already lie there. */
    private void shed(ServerLevel level, BlockPos pos, RandomSource random) {
        Item item = seeds.get();
        if (item == null) {
            return;
        }
        int lying = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.0), e -> e.getItem().is(item))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        if (lying >= MOST_SHED) {
            return;
        }
        ItemEntity seed = new ItemEntity(level, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.4,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, new ItemStack(item));
        seed.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(seed);
    }

    // --- eaten by the ants (see FoodStore) ------------------------------------------------------------------------

    @Override
    public BlockState eatenTo(BlockState state, int cut, int pieces) {
        return state; // (the head looks the same until it is bare)
    }

    @Override
    public int eatenAlready(BlockState state, int pieces) {
        return 0;
    }

    /** A head cut bare dries off, as a shed one does; the plant lives on. */
    @Override
    public BlockState afterLastPiece(BlockState state) {
        return state.setValue(STAGE, Stage.DRIED).setValue(AGE, 0);
    }

    // --- bone meal: a stage on ------------------------------------------------------------------------------------

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(STAGE) != Stage.RIPE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(STAGE, GramaLife.after(state.getValue(STAGE))).setValue(AGE, 0), 2);
    }

    // --- fire: burns like grass, the dry stalks better ------------------------------------------------------------

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(STAGE) == Stage.DRIED ? 100 : 60;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
        return state.getValue(STAGE) == Stage.DRIED ? 60 : 30;
    }
}
