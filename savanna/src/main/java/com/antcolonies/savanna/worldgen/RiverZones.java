package com.antcolonies.savanna.worldgen;

import java.util.Map;

import javax.annotation.Nullable;

import com.antcolonies.savanna.SavannaMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * River zones: who lives where along a savanna river (the user, 2026-11-18 and 2026-11-21, {@code docs/DESIGN.md}, "River
 * zones"). The one place that answers "which stretch of river is this": the {@link Zone#COAST coast}, {@link Zone#NEAR
 * near}, {@link Zone#MID mid} or {@link Zone#FAR far inland} river, read from Minecraft's "how far inland" number
 * (continentalness, the same number the rivers' widths and the sand's colours follow, {@link SandBanks}), or the still
 * waters off it: the {@link Zone#OXBOW oxbows} (any water of the floodplain) and the {@link Zone#PAN pans} (any water of the
 * open savanna; split from one "still waters" zone 2026-11-22, oxbows and pans step 5: real pans are rain pools with no
 * river fish, the African bullfrog's, the snails' and the flies').
 *
 * <p>Each river animal has a share of its spawning group's cap in each zone ({@link #SHARES}, my picks, fun first, inspired
 * by real rivers): a share of 1 is the whole cap (no change), 0.25 a quarter of it counted within {@link #LOOK} blocks, 0
 * none. A share, not a chance, because Minecraft's spawner keeps trying until a group's cap is full: a chance would only
 * slow the filling. Spots off the savanna (a game test's arena, a hand-made world) have no zone ({@link Zone#UNKNOWN}) and
 * keep the whole cap, as the quiet-savanna gate leaves them alone. Water not open to the sky (a cave or an underground
 * pocket under the land) is no river at all: nothing spawns in it.
 */
public final class RiverZones {
    /** The highest a still pool's water lies over sea level for its animals to spawn in (the pans up on the open plain; my pick). */
    public static final int HIGHEST_POOL = 24;

    /** A stretch of the savanna's waters. */
    public enum Zone {
        COAST, NEAR, MID, FAR, OXBOW, PAN, UNKNOWN
    }

    /** How far inland, from continentalness: the coast below the first, near inland below the second, mid below the third, far above (the sand's bands). */
    static final double[] INLAND_FROM = {-0.11, 0.03, 0.3};
    /** A zone's share is counted among the animals of the same kind within this many blocks of the spot. */
    public static final int LOOK = 64;
    /**
     * The share of a kind's count the world's own spawner fills when the kind breeds (the user, 2026-11-24, "breeding
     * refills"; review R10-1): the spawner seeds a water up to half, breeding fills the rest up to the kind's local limit
     * ({@code RoomToBreed}). A kind that does not breed (its life file) is spawned up to its whole count.
     */
    public static final float SPAWNER_SHARE = 0.5F;

    public static final ResourceKey<Biome> RIVER = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));

    /**
     * Each kind's share of its group's cap by zone: coast, near, mid, far inland, oxbows, pans (my picks, 2026-11-21; the
     * oxbows keep the old still waters' shares, the pans new, 2026-11-22: none of the river's fish, crocodiles, crabs or
     * monitors; snails and bullfrogs all of theirs).
     * Kinds not listed keep the whole cap everywhere (the fly clouds: flies hang over any water). The hippo is here for the
     * record only: it is placed with new land ({@link PodSites}), never spawned.
     */
    static final Map<String, float[]> SHARES = Map.ofEntries(
            Map.entry("hippo", new float[] {0, 1, 1, 0, 0, 0}),              // slow deep pools of the near and mid rivers (and deep oxbows: placed with the land, OxbowFeature)
            Map.entry("crocodile", new float[] {0.25F, 1, 1, 0.25F, 0.5F, 0}), // most on the wide slow rivers' bars, few at the coast and up the narrow rivers
            Map.entry("catfish", new float[] {1, 1, 1, 0.25F, 1, 0}),        // deep and murky water and the oxbows; few in the clear narrow rivers
            Map.entry("tilapia", new float[] {0.5F, 1, 1, 0.5F, 1, 0}),       // quiet water; nurseries in the oxbows
            Map.entry("barb", new float[] {0.25F, 0.5F, 1, 1, 0.25F, 0}),     // clear running water, most far inland
            Map.entry("distichodus", new float[] {0, 1, 1, 0, 1, 0}),        // the slower edges of the near and mid rivers and the oxbows (2026-11-23)
            Map.entry("crab", new float[] {0.25F, 0.5F, 1, 1, 0.25F, 0}),     // stony beds, most far inland
            Map.entry("snail", new float[] {0.25F, 1, 1, 0.5F, 1, 1}),        // still and slow water, weed and algae
            Map.entry("bullfrog", new float[] {0, 0, 0.1F, 0, 1, 1}),        // the pans and the floodplain's pools; rare on the river
            Map.entry("bull_shark", new float[] {1, 0.5F, 0, 0, 0, 0}),      // up from the sea into the coast and near-inland rivers only
            Map.entry("monitor", new float[] {0.5F, 1, 1, 1, 0.5F, 0}),      // any bank of the river and oxbows, fewer at the coast and the oxbows
            Map.entry("otter", new float[] {0, 0.5F, 1, 1, 1, 0}));          // fresh running water and the oxbows; none at the salty coast or the fishless pans

    private RiverZones() {
    }

    /** The band (0 coast to 3 far inland) of a continentalness reading. */
    public static int band(double continentalness) {
        int band = 0;
        while (band < INLAND_FROM.length && continentalness >= INLAND_FROM[band]) {
            band++;
        }
        return band;
    }

    /** The river zone of a band. */
    public static Zone river(int band) {
        return Zone.values()[Math.max(0, Math.min(3, band))];
    }

    /** The zone of a spot from its biome and its continentalness (asked only for the river). */
    public static Zone zoneOf(Holder<Biome> biome, java.util.function.DoubleSupplier continentalness) {
        if (biome.is(RIVER)) {
            return river(band(continentalness.getAsDouble()));
        }
        if (biome.is(MudSites.FLOODPLAIN)) {
            return Zone.OXBOW;
        }
        return biome.is(RiverBankFeature.WATERS) ? Zone.PAN : Zone.UNKNOWN;
    }

    /** The zone of a spot in a world (by its biome there; the river's band from the world's own continentalness). */
    public static Zone at(LevelAccessor level, BlockPos pos) {
        DensityFunction cont = continentalness(level);
        return zoneOf(level.getBiome(pos), () -> cont == null ? 0.1 : RiverBends.at(cont, pos.getX(), pos.getZ()));
    }

    /** The world's continentalness noise, or null where it cannot be had. */
    @Nullable
    static DensityFunction continentalness(LevelAccessor level) {
        ServerLevel server = level instanceof ServerLevel s ? s : level instanceof ServerLevelAccessor a ? a.getLevel() : null;
        return server == null ? null : server.getChunkSource().randomState().sampler().continentalness();
    }

    /**
     * Whether the water at a spot is open to the sky: its column of water reaches up to the land's surface there (nothing
     * but a plant or a lily pad over it), not a pocket under the ground. True for a spot that is not water.
     */
    public static boolean openToSky(LevelReader level, BlockPos pos) {
        if (!level.getFluidState(pos).is(FluidTags.WATER)) {
            return true;
        }
        BlockPos.MutableBlockPos p = pos.mutable();
        while (level.getFluidState(p.above()).is(FluidTags.WATER) && p.getY() < level.getMaxBuildHeight()) {
            p.move(0, 1, 0);
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) <= p.getY() + 2;
    }

    /** A kind's share of its group's cap in a zone (1 for a kind not listed and for no zone). */
    public static float share(EntityType<?> type, Zone zone) {
        float[] shares = SHARES.get(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
        return shares == null || zone == Zone.UNKNOWN ? 1.0F : shares[zone.ordinal()];
    }

    /**
     * Whether the world may spawn one more of a kind at a spot (asked for the world's own spawns only): water open to the
     * sky, and fewer of the kind within {@link #LOOK} blocks than the spawner's part of its zone's share of its group's
     * cap ({@link #mostBySpawner}).
     */
    public static boolean room(EntityType<?> type, LevelAccessor level, BlockPos pos) {
        if (!openToSky(level, pos)) {
            return false;
        }
        Zone zone = at(level, pos);
        int most = mostBySpawner(type, zone);
        if (most <= 0 || most == Integer.MAX_VALUE) {
            return most > 0;
        }
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(LOOK), e -> e.getType() == type).size() < most;
    }

    /**
     * How many of a kind within {@link #LOOK} blocks the world's own spawner fills a zone up to: none where the kind does
     * not live, no limit of its own (the group's cap alone) for a kind with the whole cap that does not breed, else its
     * zone's share of the cap, halved ({@link #SPAWNER_SHARE}) for a kind that breeds, at least one.
     */
    public static int mostBySpawner(EntityType<?> type, Zone zone) {
        float share = share(type, zone);
        if (share <= 0.0F) {
            return 0;
        }
        boolean breeds = com.antcolonies.animal.AnimalLives.has(type) && com.antcolonies.animal.AnimalLives.get(type).breeds();
        share *= breeds ? SPAWNER_SHARE : 1.0F;
        return share >= 1.0F ? Integer.MAX_VALUE : Math.max(1, Math.round(share * type.getCategory().getMaxInstancesPerChunk()));
    }

    /** Whether a zone has room for one more of a kind with this many of it within {@link #LOOK} blocks. */
    public static boolean allows(EntityType<?> type, Zone zone, int nearby) {
        float share = share(type, zone);
        if (share >= 1.0F || share <= 0.0F) {
            return share > 0.0F;
        }
        return nearby < most(type, zone);
    }

    /** How many of a kind a zone keeps within {@link #LOOK} blocks: its share of its group's cap, at least one. */
    public static int most(EntityType<?> type, Zone zone) {
        return Math.max(1, Math.round(share(type, zone) * type.getCategory().getMaxInstancesPerChunk()));
    }
}
