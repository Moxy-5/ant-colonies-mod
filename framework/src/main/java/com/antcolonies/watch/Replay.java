package com.antcolonies.watch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.antcolonies.colony.Colony;
import com.antcolonies.colony.ColonyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * The raid test bench, round 2, B3 ({@code docs/plans/raid-test-bench.md}): a report ({@link Reports}) put back into a
 * world, so the moment a rule broke can be run again: the blocks of {@code place.nbt}, every insect in it (each keeping
 * its own id, so the colonies still know their queens, raiders and scouts), and the colonies of {@code colonies.nbt} as
 * they were saved, at the very same coordinates (the colonies' saves hold world places all through, so the scene cannot
 * be moved), the game's clock set to the report's moment (they hold game times too) and the weather as it was.
 *
 * <p>Round the box a skirt of plain ground ({@link #SKIRT} blocks) at the height of the box's edge, so an insect walking
 * out of the box walks on, not off a floating island; the box and its skirt get the report's biome.
 *
 * <p>Used by the game tests ({@code ReplayTests}, one per report folder copied into a species mod's
 * {@code data/<mod>/replays/} by {@code tools/report_to_test.py}) and by {@code /antcolonies replay <report>} in a
 * creative world. A replay is the same place, not the same dice: each run gives the colonies fresh dice
 * ({@link Colony#reseedDice}).
 */
public final class Replay {
    private static final Logger LOG = LoggerFactory.getLogger("antcolonies/rules");
    /** The keys round 2 adds to a report's {@code place.nbt} (round 1's reports lack them: read from {@code report.txt}). */
    public static final String GAME_TIME = "ReplayGameTime";
    public static final String DAY_TIME = "ReplayDayTime";
    public static final String RAINING = "ReplayRaining";
    public static final String SPOT = "ReplaySpot";
    public static final String BIOME = "ReplayBiome";
    public static final String DIMENSION = "ReplayDimension";
    /** Plain ground round the box (blocks), at the height of the box's edge. */
    static final int SKIRT = 12;

    private final String name;
    private final CompoundTag place;
    private final CompoundTag colonies;
    private final long gameTime;
    private final long dayTime;
    private final boolean raining;
    private final BlockPos corner;
    private final Vec3i size;
    private final BlockPos spot;
    @Nullable
    private final ResourceLocation biome;
    private final String why;

    private Replay(String name, CompoundTag place, CompoundTag colonies, long gameTime, long dayTime, boolean raining, BlockPos corner,
                   Vec3i size, BlockPos spot, @Nullable ResourceLocation biome, String why) {
        this.name = name;
        this.place = place;
        this.colonies = colonies;
        this.gameTime = gameTime;
        this.dayTime = dayTime;
        this.raining = raining;
        this.corner = corner;
        this.size = size;
        this.spot = spot;
        this.biome = biome;
        this.why = why;
    }

    /** What a replay put into the world, to take away again ({@link #clear}). */
    public record Put(List<UUID> colonies, int insects, List<ChunkPos> forced) {
    }

    // --- reading ---------------------------------------------------------------------------------------------------

    /** A report folder on disk ({@code reports/<name>}, or a replay copied into a mod). */
    public static Replay read(Path dir) throws IOException {
        if (!Files.exists(dir.resolve("place.nbt"))) {
            throw new IOException("the report " + dir + " has no place.nbt (the land was not loaded when it was saved): it cannot be replayed");
        }
        String about = Files.exists(dir.resolve("report.txt")) ? Files.readString(dir.resolve("report.txt"), StandardCharsets.UTF_8)
                : Files.exists(dir.resolve("about.txt")) ? Files.readString(dir.resolve("about.txt"), StandardCharsets.UTF_8) : "";
        try (InputStream place = Files.newInputStream(dir.resolve("place.nbt"));
             InputStream colonies = Files.newInputStream(dir.resolve("colonies.nbt"))) {
            return read(dir.getFileName().toString(), place, colonies, about);
        }
    }

    /** A report from its two files and its words ({@code report.txt}: round 1's reports keep their moment only there). */
    public static Replay read(String name, InputStream placeFile, InputStream coloniesFile, String about) throws IOException {
        CompoundTag place = NbtIo.readCompressed(placeFile, NbtAccounter.unlimitedHeap());
        CompoundTag colonies = NbtIo.readCompressed(coloniesFile, NbtAccounter.unlimitedHeap());
        int[] c = place.getIntArray("ReportCornerXYZ");
        if (c.length != 3) {
            throw new IOException("the report " + name + "'s place.nbt has no corner");
        }
        BlockPos corner = new BlockPos(c[0], c[1], c[2]);
        ListTag sizeTag = place.getList("size", Tag.TAG_INT);
        Vec3i size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        long gameTime;
        long dayTime;
        boolean raining;
        BlockPos spot;
        if (place.contains(GAME_TIME)) {
            gameTime = place.getLong(GAME_TIME);
            dayTime = place.getLong(DAY_TIME);
            raining = place.getBoolean(RAINING);
            int[] s = place.getIntArray(SPOT);
            spot = new BlockPos(s[0], s[1], s[2]);
        } else {
            // Round 1's reports: "When: game tick 2561, day 1, 8:33[, raining]" and "Where: 784, 63, -645 in ..."
            Matcher when = Pattern.compile("When: game tick (\\d+), day (\\d+), (\\d+):(\\d+)(, raining)?").matcher(about);
            Matcher where = Pattern.compile("Where: (-?\\d+), (-?\\d+), (-?\\d+)").matcher(about);
            if (!when.find() || !where.find()) {
                throw new IOException("the report " + name + " has no moment in its place.nbt and none readable in its report.txt");
            }
            gameTime = Long.parseLong(when.group(1));
            long day = Long.parseLong(when.group(2)) - 1;
            int hour = Integer.parseInt(when.group(3));
            int minute = Integer.parseInt(when.group(4));
            dayTime = day * 24000 + Math.floorMod((hour - 6) * 1000L + minute * 1000L / 60, 24000L);
            raining = when.group(5) != null;
            spot = new BlockPos(Integer.parseInt(where.group(1)), Integer.parseInt(where.group(2)), Integer.parseInt(where.group(3)));
        }
        ResourceLocation biome = place.contains(BIOME) ? ResourceLocation.tryParse(place.getString(BIOME)) : null;
        String why = about.lines().skip(1).findFirst().orElse("");
        return new Replay(name, place, colonies, gameTime, dayTime, raining, corner, size, spot, biome, why);
    }

    public String name() {
        return name;
    }

    /** The report's first line of words: what broke, or the player's words. */
    public String why() {
        return why;
    }

    public long gameTime() {
        return gameTime;
    }

    public long dayTime() {
        return dayTime;
    }

    public boolean raining() {
        return raining;
    }

    /** Where the report was made (the stuck insect, the player). */
    public BlockPos spot() {
        return spot;
    }

    /** The box of saved blocks. */
    public AABB box() {
        return new AABB(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + size.getX(), corner.getY() + size.getY(),
                corner.getZ() + size.getZ());
    }

    /** The box and its skirt of plain ground (everything a replay changes). */
    public AABB area() {
        return box().inflate(SKIRT, 0, SKIRT);
    }

    /** The ids of the colonies the report saved. */
    public List<UUID> colonyIds() {
        List<UUID> ids = new ArrayList<>();
        for (String key : colonies.getAllKeys()) {
            try {
                ids.add(UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                // (not a colony)
            }
        }
        return ids;
    }

    /** How many insects and other creatures the place holds. */
    public int creatureCount() {
        return place.getList("entities", Tag.TAG_COMPOUND).size();
    }

    // --- putting it back ------------------------------------------------------------------------------------------

    /**
     * Puts the report back into this world: the clock set to its moment, its chunks loaded and kept running, the blocks,
     * the skirt and biome, the colonies (fresh dice from {@code diceSeed}) and the creatures. Whatever stood there before
     * is replaced (anything of an earlier run of the same replay too).
     */
    public Put put(ServerLevel level, long diceSeed) {
        level.getServer().getWorldData().overworldData().setGameTime(gameTime);
        level.setWeatherParameters(raining ? 0 : 24000, raining ? 24000 : 0, raining, false);
        AABB area = area();
        List<ChunkPos> forced = new ArrayList<>();
        for (int x = (int) Math.floor(area.minX) >> 4; x <= ((int) Math.ceil(area.maxX) - 1) >> 4; x++) {
            for (int z = (int) Math.floor(area.minZ) >> 4; z <= ((int) Math.ceil(area.maxZ) - 1) >> 4; z++) {
                if (level.setChunkForced(x, z, true)) {
                    forced.add(new ChunkPos(x, z));
                }
                level.getChunk(x, z); // (loaded now, made if new)
            }
        }
        removeLeftovers(level, area);

        // The blocks (air too: the whole box is as it was), with no neighbour updates (nothing falls or flows as it goes in).
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), place);
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings().setIgnoreEntities(true), level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        skirt(level);
        fillBiome(level, area);

        // The colonies, as saved.
        List<UUID> put = new ArrayList<>();
        ColonyManager manager = ColonyManager.get(level);
        for (String key : colonies.getAllKeys()) {
            try {
                Colony colony = Colony.load(colonies.getCompound(key));
                colony.reseedDice(diceSeed ^ colony.getId().getMostSignificantBits());
                Watch.forget(colony.getId());
                manager.replaceForTests(colony);
                put.add(colony.getId());
            } catch (RuntimeException e) {
                LOG.warn("[replay] {}: the colony {} could not be loaded (its species' mod missing?), left out", name, key, e);
            }
        }

        // The creatures, each with its own id.
        int insects = 0;
        ListTag entities = place.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag info = entities.getCompound(i);
            CompoundTag nbt = info.getCompound("nbt").copy();
            ListTag rel = info.getList("pos", Tag.TAG_DOUBLE);
            double x = corner.getX() + rel.getDouble(0);
            double y = corner.getY() + rel.getDouble(1);
            double z = corner.getZ() + rel.getDouble(2);
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(x));
            pos.add(DoubleTag.valueOf(y));
            pos.add(DoubleTag.valueOf(z));
            nbt.put("Pos", pos);
            if (nbt.hasUUID("UUID")) {
                Entity old = level.getEntity(nbt.getUUID("UUID"));
                if (old != null) {
                    old.discard();
                }
            }
            Entity entity = EntityType.loadEntityRecursive(nbt, level, e -> {
                e.moveTo(x, y, z, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity != null && level.tryAddFreshEntityWithPassengers(entity)) {
                insects++;
            }
        }
        LOG.info("[replay] {} put back: {} colonies, {} of {} creatures, clock at game tick {} (day time {}){}", name, put.size(), insects,
                entities.size(), gameTime, dayTime, raining ? ", raining" : "");
        return new Put(put, insects, forced);
    }

    /** Takes the replay away: its colonies with every insect of theirs, every creature in the area, the chunks let go. */
    public void clear(ServerLevel level, Put put) {
        ColonyManager manager = ColonyManager.get(level);
        Set<UUID> ids = new HashSet<>(put.colonies());
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            UUID colony = ColonyManager.colonyOf(entity);
            if (colony != null && ids.contains(colony)) {
                gone.add(entity);
            }
        }
        gone.forEach(Entity::discard);
        for (UUID id : put.colonies()) {
            manager.removeColony(id);
            Watch.forget(id);
        }
        removeLeftovers(level, area());
        for (ChunkPos chunk : put.forced()) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    /** Every creature in the area but players, and the report's colonies if an earlier run left them. */
    private void removeLeftovers(ServerLevel level, AABB area) {
        level.getEntities((Entity) null, area.inflate(0, 64, 0), entity -> !(entity instanceof Player)).forEach(Entity::discard);
        ColonyManager manager = ColonyManager.get(level);
        Set<UUID> ids = new HashSet<>(colonyIds());
        List<Entity> theirs = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            UUID colony = ColonyManager.colonyOf(entity);
            if (colony != null && ids.contains(colony)) {
                theirs.add(entity);
            }
        }
        theirs.forEach(Entity::discard);
        ids.forEach(manager::removeColony);
    }

    /**
     * Plain ground round the box: each skirt column filled up to the ground's height at the nearest column of the box's
     * edge (its topmost block that is not air, a plant, leaves or wood), grass on top, air above.
     */
    private void skirt(ServerLevel level) {
        int minX = corner.getX();
        int minZ = corner.getZ();
        int maxX = minX + size.getX() - 1;
        int maxZ = minZ + size.getZ() - 1;
        int bottom = corner.getY();
        int top = bottom + size.getY() - 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = minX - SKIRT; x <= maxX + SKIRT; x++) {
            for (int z = minZ - SKIRT; z <= maxZ + SKIRT; z++) {
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    continue;
                }
                int ground = groundAt(level, Math.clamp(x, minX, maxX), Math.clamp(z, minZ, maxZ), bottom, top);
                for (int y = bottom; y <= top; y++) {
                    BlockState state = y > ground ? Blocks.AIR.defaultBlockState()
                            : y == ground ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : y >= ground - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(at.set(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static int groundAt(ServerLevel level, int x, int z, int bottom, int top) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            BlockState state = level.getBlockState(at.set(x, y, z));
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getCollisionShape(level, at).isEmpty()) {
                continue; // (air, a crown, a trunk, grass and flowers, water: not the ground)
            }
            return y;
        }
        return bottom;
    }

    /** The report's biome over the box and its skirt (a test world is all plains), as {@code /fillbiome} does. */
    private void fillBiome(ServerLevel level, AABB area) {
        if (biome == null) {
            return;
        }
        Holder<Biome> holder = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, biome)).orElse(null);
        if (holder == null) {
            return;
        }
        List<ChunkAccess> chunks = new ArrayList<>();
        for (int x = (int) Math.floor(area.minX) >> 4; x <= ((int) Math.ceil(area.maxX) - 1) >> 4; x++) {
            for (int z = (int) Math.floor(area.minZ) >> 4; z <= ((int) Math.ceil(area.maxZ) - 1) >> 4; z++) {
                chunks.add(level.getChunk(x, z));
            }
        }
        for (ChunkAccess chunk : chunks) {
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
                int x = QuartPos.toBlock(qx);
                int y = QuartPos.toBlock(qy);
                int z = QuartPos.toBlock(qz);
                return area.inflate(4).contains(x + 0.5, Math.clamp(y + 0.5, area.minY, area.maxY - 0.5), z + 0.5) ? holder
                        : chunk.getNoiseBiome(qx, qy, qz);
            }, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }
}
