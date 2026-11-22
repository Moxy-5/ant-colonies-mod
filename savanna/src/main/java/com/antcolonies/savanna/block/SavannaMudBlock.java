package com.antcolonies.savanna.block;

import com.antcolonies.savanna.registry.SavannaTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Savanna mud (the user, 2026-11-21, {@code docs/plans/savanna-mud.md}, section 5; Neon's looks and tints): the mud of the
 * river mud areas at the waterline and of the dry wallows out on the savanna. One block with three settings:
 * <ul>
 * <li>{@link #WETNESS}: cracked, drying, sticky, soupy;</li>
 * <li>{@link #DEPTH}: 1 to 4, how far into a wallow it lies (the rim ring 1, the middle 4): the soupy mud's look (darker
 *     toward the middle, Neon's shallow, mid and deep surfaces) and how slowly it dries. Set when the wallow is made and
 *     kept while it dries, so a dried pit comes back the same after rain. It does not change how far things sink;</li>
 * <li>{@link #TONE}: the soil's colour, olive river silt or the land's own red, golden or pale.</li>
 * </ul>
 *
 * <p><b>Sinking</b> ({@link #sinkPixels}; the user, 2026-11-21: "sink 0.25 to 0.50 depending on how heavy they are"):
 * in soupy mud a creature sinks a quarter block if light (a crab, a bullfrog) up to half a block if heavy (a hippo),
 * by the size of its body; in sticky mud half that; drying and cracked mud is firm flat ground. The block is drawn as a
 * full cube, so the sunk feet are hidden in the mud (as in powder snow). Half a block is less than any creature's step,
 * so nothing is ever trapped. Ants and termites (a few milligrams) do not sink: they walk on the surface, and their legs
 * go round wet mud ({@link #insectCost}).
 *
 * <p><b>Drag</b> ({@link #DRAG}): anything not tagged {@code #antcolonies:mud_adapted} is slowed while its feet are in the
 * mud, the deeper it sinks the slower; the river animals are not. Mobs plan over it as ground (the ants' own legs keep off wet mud).
 *
 * <p><b>Wetting and drying</b> ({@link #randomTick}, as Minecraft's farmland): water within {@link #WATER_REACH} blocks or
 * rain on it makes it one step wetter; otherwise, by chance, one step drier, the shallower the faster, so a wallow cracks
 * from its rim inward and the river mud areas (every block within reach of the river) never dry.
 *
 * <p><b>Light</b>: it takes light in as glass does ({@link #getLightBlock}), so a creature sunk in it is not drawn
 * black (Minecraft lights a mob from the block at its eyes); it still hides its neighbours' faces as dirt does.
 */
public class SavannaMudBlock extends Block implements com.antcolonies.nav.SoftGround {
    public static final EnumProperty<Wetness> WETNESS = EnumProperty.create("wetness", Wetness.class);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", 1, 4);
    public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);

    /** Water this close (blocks, sideways, at its level or one up) keeps it wet, as farmland's 4. */
    public static final int WATER_REACH = 4;
    /**
     * On a random tick (about once a minute a block) mud with no water near and no rain dries one step with a chance of
     * 1 in {@code DRY_BASE + DRY_PER_DEPTH * depth} (my picks): a rim block (depth 1) from soupy to cracked in about one
     * game day, the deep middle (depth 4) in about two and a half.
     */
    static final int DRY_BASE = 3;
    static final int DRY_PER_DEPTH = 3;
    /** How far (pixels) a light creature sinks in soupy mud (a quarter block), and a heavy one (half a block). */
    static final int LIGHT_SINK = 4;
    static final int HEAVY_SINK = 8;
    /**
     * A creature's weight is read from its body's size (width x width x height, in blocks): this little or less is light
     * (a crab 0.11, a bullfrog 0.14), this much or more heavy (a hippo 6.5); between, in steps of a pixel (a monitor 0.27
     * sinks 5 pixels, a crocodile 0.49 and a player 0.65 6, a pig 0.73 7). My picks.
     */
    static final double LIGHT_BODY = 0.1, HEAVY_BODY = 1.0;
    /**
     * Each tick a creature's feet are in the mud its movement is multiplied by 1 minus this for every pixel it sinks
     * (my pick): with Minecraft's ground friction a walker sunk half a block ends up at about half its speed, one sunk a
     * quarter at about 70 percent, in sticky mud 80 to 90.
     */
    static final double DRAG_PER_PIXEL = 0.04;

    /**
     * What one foothold on wet (sticky or soupy) mud adds to an ant's or termite's route (my pick): about 12 blocks of
     * walking, so a column goes round a wallow and crosses only when there is no other way (real ants keep off wet mud).
     */
    static final double WET_INSECT_COST = 12.0;

    /** The floor for each sink in pixels (0 to {@link #HEAVY_SINK}). */
    private static final VoxelShape[] FLOORS = new VoxelShape[HEAVY_SINK + 1];
    static {
        FLOORS[0] = Shapes.block();
        for (int px = 1; px <= HEAVY_SINK; px++) {
            FLOORS[px] = Block.box(0, 0, 0, 16, 16 - px, 16);
        }
    }

    public SavannaMudBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WETNESS, Wetness.STICKY).setValue(DEPTH, 2).setValue(TONE, Tone.OLIVE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WETNESS, DEPTH, TONE);
    }

    /** The mud's state with these settings. */
    public BlockState with(Wetness wetness, int depth, Tone tone) {
        return defaultBlockState().setValue(WETNESS, wetness).setValue(DEPTH, depth).setValue(TONE, tone);
    }

    /**
     * How many pixels this creature sinks in this mud: soupy from {@link #LIGHT_SINK} to {@link #HEAVY_SINK} by its weight,
     * sticky half that, drying and cracked none; an ant or termite none. With no creature (the route planner, the light) a
     * light one's.
     */
    public static int sinkPixels(BlockState state, @Nullable Entity entity) {
        if (entity instanceof com.antcolonies.entity.AbstractAntEntity) {
            return 0;      // an ant or termite weighs a few milligrams: it walks on the mud's surface
        }
        int soupy = entity == null ? LIGHT_SINK : weightSink(entity);
        return switch (state.getValue(WETNESS)) {
            case CRACKED, DRYING -> 0;
            case STICKY -> (soupy + 1) / 2;
            case SOUPY -> soupy;
        };
    }

    /** How many pixels this creature sinks in soupy mud, by its body's size (see {@link #LIGHT_BODY}). */
    static int weightSink(Entity entity) {
        double body = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
        double heavy = Math.clamp((body - LIGHT_BODY) / (HEAVY_BODY - LIGHT_BODY), 0.0, 1.0);
        return LIGHT_SINK + (int) Math.round(heavy * (HEAVY_SINK - LIGHT_SINK));
    }

    /** Ants and termites go round wet mud ({@link #WET_INSECT_COST}); dry mud is plain ground to them. */
    @Override
    public double insectCost(BlockState state) {
        return soft(state) ? WET_INSECT_COST : 0.0;
    }

    /** Whether this mud lets anything sink in (sticky or soupy). */
    public static boolean soft(BlockState state) {
        return state.getValue(WETNESS) == Wetness.STICKY || state.getValue(WETNESS) == Wetness.SOUPY;
    }

    /** What a creature sunk this many pixels has its movement multiplied by each tick. */
    public static double drag(int pixels) {
        return 1.0 - DRAG_PER_PIXEL * pixels;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Entity creature = context instanceof EntityCollisionContext entityContext ? entityContext.getEntity() : null;
        return FLOORS[sinkPixels(state, creature)];
    }

    /** Things set on it (and the light's faces) see a whole block: the mud's surface is at the top. */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** Lets light in, losing one level as leaves do (see the class note). */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 1;
    }

    /** Shades its neighbours' corners as dirt does, whatever it sinks. */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 0.2F;
    }

    /**
     * Planned over as ground, as Minecraft's own mud: a mob's route runs over it and the mob sinks in and is slowed. (A
     * first try marked deep mud as honey, a place to go round; Minecraft's planner never steps up out of honey, so a pig in
     * a wallow's middle could plan no way out. Dropped, 2026-11-21.)
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return switch (state.getValue(WETNESS)) {
            case CRACKED -> SoundType.MUD_BRICKS;
            case DRYING -> SoundType.PACKED_MUD;
            case STICKY, SOUPY -> SoundType.MUD;
        };
    }

    /** Whether this kind of creature walks through mud unslowed (data/antcolonies/tags/entity_type/mud_adapted.json). */
    public static boolean adapted(Entity entity) {
        return entity.getType().is(SavannaTags.MUD_ADAPTED);
    }

    /**
     * Slows a creature whose feet are in this mud, once a tick: only the block under the middle of its feet acts (a
     * player standing over four blocks is not slowed four times). Runs on both sides, so a player's own game slows them
     * the same way the server does.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!soft(state) || adapted(entity) || !pos.equals(BlockPos.containing(entity.getX(), entity.getY() + 1.0E-3, entity.getZ()))) {
            return;
        }
        double drag = drag(sinkPixels(state, entity));
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x * drag, motion.y, motion.z * drag);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = nextWetness(state, level, pos, random);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
            if (next.getValue(WETNESS).ordinal() < state.getValue(WETNESS).ordinal()) {
                liftOnto(level, pos, next);
            }
        }
    }

    /**
     * Mud that firmed up lifts what stood sunk in it onto its new floor (as farmland turning to dirt does), each creature
     * by its own sink, so nothing is left inside the block.
     */
    static void liftOnto(Level level, BlockPos pos, BlockState now) {
        for (Entity entity : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(pos))) {
            double floor = pos.getY() + 1.0 - sinkPixels(now, entity) / 16.0;
            if (entity.getY() < floor && entity.getY() >= pos.getY()) {
                entity.teleportRelative(0.0, floor - entity.getY(), 0.0);
            }
        }
    }

    /** The mud's state after one random tick: wetter by water or rain, else maybe drier. */
    public static BlockState nextWetness(BlockState state, LevelReader level, BlockPos pos, RandomSource random) {
        Wetness wetness = state.getValue(WETNESS);
        if (wetted(level, pos)) {
            return wetness == Wetness.SOUPY ? state : state.setValue(WETNESS, wetness.wetter());
        }
        if (wetness == Wetness.CRACKED || random.nextInt(DRY_BASE + DRY_PER_DEPTH * state.getValue(DEPTH)) != 0) {
            return state;
        }
        return state.setValue(WETNESS, wetness.drier());
    }

    /** Whether water lies within {@link #WATER_REACH} blocks (at the mud's level or one up) or rain falls on it. */
    public static boolean wetted(LevelReader level, BlockPos pos) {
        if (level instanceof Level world && world.isRainingAt(pos.above())) {
            return true;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-WATER_REACH, 0, -WATER_REACH), pos.offset(WATER_REACH, 1, WATER_REACH))) {
            if (level.getFluidState(near).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** How wet the mud is, driest first. */
    public enum Wetness implements StringRepresentable {
        CRACKED("cracked"), DRYING("drying"), STICKY("sticky"), SOUPY("soupy");

        private final String name;

        Wetness(String name) {
            this.name = name;
        }

        public Wetness wetter() {
            return this == SOUPY ? SOUPY : values()[ordinal() + 1];
        }

        public Wetness drier() {
            return this == CRACKED ? CRACKED : values()[ordinal() - 1];
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * The soil's colour (Neon's tint table, approved in the mud studio): olive river silt by the water, the land's own
     * red far inland, golden mid inland and pale near the coast (the sand banks' bands).
     */
    public enum Tone implements StringRepresentable {
        OLIVE("olive", 0x362E21, 0x60543E, 0x9A8E76),
        RED("red", 0x5C2819, 0x8E452B, 0xC07652),
        GOLDEN("golden", 0x5C4623, 0x8E703E, 0xBCA472),
        PALE("pale", 0x544F46, 0x80796C, 0xB2AC9E);

        private final String name;
        private final int wet, mid, dry;

        Tone(String name, int wet, int mid, int dry) {
            this.name = name;
            this.wet = wet;
            this.mid = mid;
            this.dry = dry;
        }

        /**
         * The colour at a dryness of 0 (wet) to 7 (dry), wet to mid over the first half and mid to dry over the second,
         * then scaled by {@code bright} (the mud studio's {@code mudRgb}).
         */
        public int colour(float dryness, float bright) {
            float t = dryness / 7.0F;
            int from = t < 0.5F ? wet : mid, to = t < 0.5F ? mid : dry;
            float f = t < 0.5F ? t * 2 : (t - 0.5F) * 2;
            int rgb = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                float a = (from >> shift) & 0xFF, b = (to >> shift) & 0xFF;
                int c = Math.min(255, Math.round((a + (b - a) * f) * bright));
                rgb |= c << shift;
            }
            return rgb;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * The block's tint as the mud studio drew each look: soupy deep 0 (darkened to 0.8), mid 0.3 (0.9), shallow 0.7,
     * sticky 1.8, drying 4.2, cracked 6.8.
     */
    public static int tint(BlockState state) {
        Tone tone = state.getValue(TONE);
        return switch (state.getValue(WETNESS)) {
            case CRACKED -> tone.colour(6.8F, 1.0F);
            case DRYING -> tone.colour(4.2F, 1.0F);
            case STICKY -> tone.colour(1.8F, 1.0F);
            case SOUPY -> switch (state.getValue(DEPTH)) {
                case 4 -> tone.colour(0.0F, 0.8F);
                case 3 -> tone.colour(0.3F, 0.9F);
                case 2 -> tone.colour(0.7F, 1.0F);
                default -> tone.colour(1.8F, 1.0F);
            };
        };
    }
}
