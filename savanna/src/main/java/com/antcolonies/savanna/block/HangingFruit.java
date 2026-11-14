package com.antcolonies.savanna.block;

import java.util.Locale;
import java.util.function.Supplier;

import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaItems;
import com.antcolonies.savanna.registry.TreeBlocks;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Fruit that ripens on a floodplain tree in stages, falls and lies on the ground (floodplain trees steps 4b and 6b,
 * 2026-11-14, {@code docs/plans/floodplain-trees.md}): the sycamore fig's bunches and bark figs, the doum palm's nut
 * bunches. One rule for all three, asked by each fruit block's random ticks (cheap, as Minecraft's crops; never through
 * the tree book):
 * <ul>
 *   <li>A bunch goes bare, green, turning (yellow figs, half-ripe nuts), ripe ({@link #STAGE}), then drops its fruit and
 *   is bare again.</li>
 *   <li>A bare bunch buds only while its tree's crop starts ({@link #cropStarts}): each tree has its own place in the
 *   cycle ({@link #PHASE}, set from the tree's seed when it is made), so a whole tree fruits together while its
 *   neighbours along the river are at other stages, as real figs do. A bunch picked or fallen waits for the next crop.
 *   This is the one place the season will be asked once seasons exist (the shared season source; today the crop
 *   comes round by the calendar alone).</li>
 *   <li>Each later stage lasts a while by chance, the time given in game days whatever the random tick speed, so the
 *   bunches of one tree ripen over a few days, not all in one tick.</li>
 *   <li>Ripe fruit falls straight down through leaves and grass and lands on the ground as a {@link FallenFruitBlock}
 *   (a block, not loose items: those vanish in 5 minutes and cost the game more), never in water, on a mound or on
 *   wood.</li>
 * </ul>
 * The timings (my picks, sped up from the real trees): figs bud, ripen in about 6 days and fall after about 1.5 more, a
 * tree cropping every 10 days; doum nuts take about 20 days and hang on 5 more, a palm cropping every 30 days.
 */
public final class HangingFruit {
    /** Bare stalk or spur, green, turning, ripe. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);
    /** The tree's place in its fruiting cycle, in eighths (all of one tree's fruit blocks share it). */
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 7);
    public static final int BARE = 0, GREEN = 1, TURNING = 2, RIPE = 3;
    private static final int PHASES = 8;
    private static final double DAY = 24000;
    /** How far down fruit may fall looking for the ground (a grown fig's lowest bunches hang a few blocks up). */
    private static final int FALL = 40;

    /** The two fruits and their times, all in game days. */
    public enum Fruit implements StringRepresentable {
        FIG(10, 1, 3.5, 2.5, 1.5, 1, () -> SavannaItems.FIG.get(), () -> SavannaItems.GREEN_FIG.get(), () -> TreeBlocks.FALLEN_FIGS.get()),
        DOUM(30, 3, 12, 8, 5, 10, () -> SavannaItems.DOUM_NUT.get(), () -> SavannaItems.GREEN_DOUM_NUT.get(),
                () -> TreeBlocks.FALLEN_DOUM_NUTS.get());

        public static final Codec<Fruit> CODEC = StringRepresentable.fromEnum(Fruit::values);

        /** A tree's whole cycle, and how long at its start a bare bunch may bud. */
        final double cycleDays, budDays;
        /** How long each stage lasts on average. */
        final double greenDays, turningDays, ripeDays;
        /** How long fallen fruit lies fresh, then old (figs rotting, nuts weathered), before it is gone. */
        final double lyingDays;
        private final Supplier<Item> ripe, unripe;
        private final Supplier<Block> fallen;

        Fruit(double cycleDays, double budDays, double greenDays, double turningDays, double ripeDays, double lyingDays,
              Supplier<Item> ripe, Supplier<Item> unripe, Supplier<Block> fallen) {
            this.cycleDays = cycleDays;
            this.budDays = budDays;
            this.greenDays = greenDays;
            this.turningDays = turningDays;
            this.ripeDays = ripeDays;
            this.lyingDays = lyingDays;
            this.ripe = ripe;
            this.unripe = unripe;
            this.fallen = fallen;
        }

        public Item ripe() {
            return ripe.get();
        }

        public Item unripe() {
            return unripe.get();
        }

        public Block fallen() {
            return fallen.get();
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private HangingFruit() {
    }

    /** A tree's phase from its seed (the shapes) or, for a fruit block put by hand, from where it stands. */
    public static int phaseOf(long seed) {
        return (int) Math.floorMod(mix(seed * 0x9E3779B97F4A7C15L + 0x51), (long) PHASES);
    }

    /** Stirs a number's bits (the last step of MurmurHash3), so seeds side by side give unrelated answers. */
    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ h >>> 33;
    }

    /**
     * A tree's own fruit block as it is made (new land, the commands): the tree's place in its cycle and how far its crop
     * has come, both from its seed, the same for all its bunches. My pick: 1 tree in 8 bare, 3 green, 2 turning, 2 ripe,
     * so a riverbank shows every stage; the calendar takes over from there.
     */
    public static BlockState onTree(BlockState fruit, long seed) {
        int roll = (int) Math.floorMod(mix(seed * 0x2545F4914F6CDD1DL + 0x77), 8L);
        int stage = roll == 0 ? BARE : roll <= 3 ? GREEN : roll <= 5 ? TURNING : RIPE;
        return fruit.setValue(PHASE, phaseOf(seed)).setValue(STAGE, stage);
    }

    /** True while a tree in this phase starts a new crop: its bare bunches bud (by the calendar, so /time moves it too). */
    public static boolean cropStarts(Fruit fruit, int phase, long dayTime) {
        long cycle = (long) (fruit.cycleDays * DAY);
        return Math.floorMod(dayTime + phase * cycle / PHASES, cycle) < fruit.budDays * DAY;
    }

    /** The chance on one random tick that something lasting {@code days} on average ends (the random tick speed counted). */
    static double chance(ServerLevel level, double days) {
        int speed = Math.max(1, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
        return Math.min(1, 4096.0 / speed / (days * DAY));
    }

    /** One random tick of a fruit block: bud, ripen a stage, or let the ripe fruit fall. */
    public static void randomTick(Fruit fruit, int ripeMin, int ripeMax, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == BARE) {
            if (cropStarts(fruit, state.getValue(PHASE), level.getDayTime())) {
                level.setBlock(pos, state.setValue(STAGE, GREEN), Block.UPDATE_CLIENTS);
            }
            return;
        }
        double days = stage == GREEN ? fruit.greenDays : stage == TURNING ? fruit.turningDays : fruit.ripeDays;
        if (random.nextDouble() >= chance(level, days)) {
            return;
        }
        if (stage < RIPE) {
            level.setBlock(pos, state.setValue(STAGE, stage + 1), Block.UPDATE_CLIENTS);
        } else {
            fall(fruit, level, pos, Mth.nextInt(random, ripeMin, ripeMax));
            level.setBlock(pos, state.setValue(STAGE, BARE), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Ripe fruit falling from {@code from}: straight down through air, leaves, grass and anything else with nothing to
     * stand on, onto the first solid ground. It lands only on soil (grass, dirt, sand, silt, mud) with a flat top; into
     * water it is carried off, on a mound, wood or stone it rolls away (gone, my pick). Onto fallen fruit of its kind it
     * adds to the heap, up to {@link FallenFruitBlock#MOST}.
     */
    public static void fall(Fruit fruit, ServerLevel level, BlockPos from, int count) {
        Block fallen = fruit.fallen();
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i < FALL && p.getY() > level.getMinBuildHeight(); i++) {
            p.move(Direction.DOWN);
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return;
            }
            if (s.is(fallen)) {
                level.setBlock(p, FallenFruitBlock.more(s, count), Block.UPDATE_ALL);
                return;
            }
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.getCollisionShape(level, p).isEmpty()) {
                continue;
            }
            BlockPos at = p.above();
            BlockState there = level.getBlockState(at);
            if (landsOn(level, p, s) && (there.isAir() || there.canBeReplaced() && there.getFluidState().isEmpty())) {
                level.setBlock(at, FallenFruitBlock.more(fallen.defaultBlockState(), count - 1), Block.UPDATE_ALL);
            }
            return;
        }
    }

    /** Ground fallen fruit may lie on: soil with a flat top. */
    static boolean landsOn(Level level, BlockPos pos, BlockState ground) {
        return ground.isFaceSturdy(level, pos, Direction.UP) && (ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND)
                || ground.is(SavannaBlocks.SILT.get()) || ground.is(SavannaBlocks.WET_SILT.get()) || ground.is(SavannaBlocks.RIVER_MUD.get()));
    }

    /** A player picks a ripe bunch by hand, as Minecraft's sweet berries: the fruit pops out, the bunch is bare. */
    public static InteractionResult pick(Fruit fruit, int ripeMin, int ripeMax, BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(STAGE) != RIPE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Block.popResource(level, pos, new ItemStack(fruit.ripe(), Mth.nextInt(level.random, ripeMin, ripeMax)));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            BlockState bare = state.setValue(STAGE, BARE);
            level.setBlock(pos, bare, Block.UPDATE_CLIENTS);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, bare));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Bone meal on an unripe bunch is left to the bone meal (it moves the bunch on a stage), as with sweet berries. */
    public static ItemInteractionResult useItemOn(ItemStack stack, BlockState state) {
        return state.getValue(STAGE) != RIPE && stack.is(Items.BONE_MEAL) ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Bone meal: one stage on. */
    public static void bonemeal(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(STAGE, Math.min(RIPE, state.getValue(STAGE) + 1)), Block.UPDATE_CLIENTS);
    }
}
