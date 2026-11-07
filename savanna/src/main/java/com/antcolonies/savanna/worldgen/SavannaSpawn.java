package com.antcolonies.savanna.worldgen;

import com.antcolonies.savanna.SavannaConfig;
import com.antcolonies.savanna.SavannaMod;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Spawn in the savanna (the user's ask, 2026-11-06; {@code docs/DESIGN.md}, "Big savannas in normal worlds with
 * TerraBlender"): when a new normal world is made, the spawn moves from where Minecraft would put it to a spot well
 * inside the nearest savanna (any biome tagged savanna), on dry ground with open sky.
 *
 * <p>How: Minecraft's own spawn point is the start; the biome noise (no chunks) is searched outward for the nearest
 * savanna; round that find the spot farthest from the savanna's edge (my pick: at least {@link #WANT_INSIDE} blocks in,
 * or the deepest within {@link #LOOK_ROUND}) is chosen; then the ground there is read for a dry place under the sky.
 * Only worlds whose biomes come from the noise (normal, large biomes, amplified; {@link MultiNoiseBiomeSource}) are
 * moved: single-biome and flat worlds are left exactly as they were. No savanna in reach: Minecraft's spawn is kept,
 * and the log and the first player's chat say so. Off with {@code spawn_in_savanna = false} in the settings file.
 */
@EventBusSubscriber(modid = SavannaMod.MODID)
public final class SavannaSpawn {
    /** How far in from the savanna's edge the spawn should be, in blocks (my pick: well inside, not on the edge). */
    public static final int WANT_INSIDE = 192;
    /** How far round the nearest savanna find to look for its deep inside, in blocks. */
    static final int LOOK_ROUND = 1024;
    /** The grid of that look, and the rings of the edge test, in blocks. */
    static final int STEP = 64, RING_STEP = 32;
    /** The height the biome noise is read at: just over sea level, the surface biome. */
    public static final int SAMPLE_Y = 80;

    /** What to tell the first player in this game (null: nothing). */
    private static Component pendingMessage;

    private SavannaSpawn() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !SavannaConfig.SPAWN_IN_SAVANNA.get()) {
            return;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator) || !(generator.getBiomeSource() instanceof MultiNoiseBiomeSource)) {
            return; // single-biome, flat and other worlds: exactly as before
        }
        BiomeSource biomes = generator.getBiomeSource();
        if (biomes.possibleBiomes().stream().noneMatch(b -> b.is(BiomeTags.IS_SAVANNA))) {
            return;
        }
        long started = System.nanoTime();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        BlockPos vanilla = sampler.findSpawnPosition();
        int radius = SavannaConfig.SPAWN_SEARCH_RADIUS.get();
        Pair<BlockPos, Holder<Biome>> found = biomes.findBiomeHorizontal(vanilla.getX(), SAMPLE_Y, vanilla.getZ(), radius, 32,
                b -> b.is(BiomeTags.IS_SAVANNA), level.getRandom(), true, sampler);
        if (found == null) {
            say("No savanna within " + radius + " blocks of the normal spawn: the normal spawn is kept.");
            return;
        }
        BlockPos inside = deepInside(biomes, sampler, found.getFirst());
        BlockPos spot = groundNear(level, inside);
        if (spot == null) {
            say("A savanna was found at " + inside.getX() + ", " + inside.getZ() + " but no dry open ground in it: the normal spawn is kept.");
            return;
        }
        ServerLevelData data = event.getSettings();
        data.setSpawn(spot, 0.0F);
        event.setCanceled(true); // Minecraft's own spawn search is skipped (it would move the spawn back)
        if (level.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
            level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(r -> r.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(chest -> chest.value().place(level, generator, level.getRandom(), spot));
        }
        int away = (int) Math.sqrt(spot.distSqr(new BlockPos(vanilla.getX(), spot.getY(), vanilla.getZ())));
        SavannaMod.LOGGER.info("[savanna spawn] spawn moved to {} {} {} in {}, {} blocks from the normal spawn, about {} blocks in from"
                        + " the savanna's edge ({} ms)", spot.getX(), spot.getY(), spot.getZ(),
                level.getBiome(spot).unwrapKey().map(k -> k.location().toString()).orElse("?"), away, depth(biomes, sampler, spot),
                (System.nanoTime() - started) / 1_000_000);
        pendingMessage = Component.literal("Ant & Termite Colonies: you start in a savanna, " + away
                + " blocks from where Minecraft would have put you (turn off: spawn_in_savanna in config/antcolonies-common.toml).");
    }

    /** The first player of this game hears where they were put (once). */
    @SubscribeEvent
    public static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (pendingMessage != null && event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(pendingMessage);
            pendingMessage = null;
        }
    }

    private static void say(String words) {
        SavannaMod.LOGGER.info("[savanna spawn] {}", words);
        pendingMessage = Component.literal("Ant & Termite Colonies: " + words);
    }

    static boolean savannaAt(BiomeSource biomes, Climate.Sampler sampler, int x, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SAMPLE_Y), QuartPos.fromBlock(z), sampler).is(BiomeTags.IS_SAVANNA);
    }

    /** How far in from the savanna's edge a place is (in steps of {@link #RING_STEP}, up to {@link #WANT_INSIDE}); 0 outside. */
    public static int depth(BiomeSource biomes, Climate.Sampler sampler, BlockPos at) {
        if (!savannaAt(biomes, sampler, at.getX(), at.getZ())) {
            return 0;
        }
        for (int r = RING_STEP; r <= WANT_INSIDE; r += RING_STEP) {
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                if (!savannaAt(biomes, sampler, at.getX() + (int) (r * Math.cos(a)), at.getZ() + (int) (r * Math.sin(a)))) {
                    return r - RING_STEP;
                }
            }
        }
        return WANT_INSIDE;
    }

    /** The place round the find farthest in from the savanna's edge (the nearest of the deepest). */
    public static BlockPos deepInside(BiomeSource biomes, Climate.Sampler sampler, BlockPos find) {
        BlockPos best = find;
        int bestDepth = depth(biomes, sampler, find);
        double bestDist = 0;
        for (int dx = -LOOK_ROUND; dx <= LOOK_ROUND && bestDepth < WANT_INSIDE; dx += STEP) {
            for (int dz = -LOOK_ROUND; dz <= LOOK_ROUND; dz += STEP) {
                BlockPos p = find.offset(dx, 0, dz);
                double dist = (double) dx * dx + (double) dz * dz;
                int d = depth(biomes, sampler, p);
                if (d > bestDepth || (d == bestDepth && dist < bestDist)) {
                    best = p;
                    bestDepth = d;
                    bestDist = dist;
                }
            }
        }
        // the search above stops once a place deep enough is found; a nearer one as deep is looked for in rings
        if (bestDepth >= WANT_INSIDE) {
            for (int r = STEP; r * r < bestDist; r += STEP) {
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI / 8;
                    BlockPos p = find.offset((int) (r * Math.cos(a)), 0, (int) (r * Math.sin(a)));
                    if (depth(biomes, sampler, p) >= WANT_INSIDE) {
                        return p;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Dry ground under the open sky in savanna near a place (its chunk, then the chunks round it, out to 3): the top
     * soil or sand block with no water on it, not on a tree's leaves.
     */
    static BlockPos groundNear(ServerLevel level, BlockPos near) {
        ChunkPos middle = new ChunkPos(near);
        for (int ring = 0; ring <= 3; ring++) {
            for (int cx = -ring; cx <= ring; cx++) {
                for (int cz = -ring; cz <= ring; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != ring) {
                        continue;
                    }
                    BlockPos spot = groundIn(level, new ChunkPos(middle.x + cx, middle.z + cz));
                    if (spot != null) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos groundIn(ServerLevel level, ChunkPos chunk) {
        var loaded = level.getChunk(chunk.x, chunk.z);
        for (int i = 0; i < 64; i++) { // from the chunk's middle outward, a few columns
            int x = chunk.getMiddleBlockX() + ((i % 8) - 4) * 2;
            int z = chunk.getMiddleBlockZ() + ((i / 8) - 4) * 2;
            int top = loaded.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            BlockPos ground = new BlockPos(x, top, z);
            BlockPos stand = ground.above();
            BlockState under = level.getBlockState(ground);
            if (!under.getFluidState().isEmpty() || !(under.is(BlockTags.DIRT) || under.is(BlockTags.SAND))) {
                continue;
            }
            if (!level.getBlockState(stand).getFluidState().isEmpty() || !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                    || !level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()) {
                continue;
            }
            // open sky: nothing above (light is not worked out yet while the world is being made, so the height map is read)
            if (stand.getY() >= loaded.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1
                    && level.getBiome(stand).is(BiomeTags.IS_SAVANNA)) {
                return stand;
            }
        }
        return null;
    }
}
