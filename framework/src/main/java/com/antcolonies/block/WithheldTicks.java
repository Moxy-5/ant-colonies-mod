package com.antcolonies.block;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.antcolonies.ColonyFrameworkMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The random ticks Minecraft holds back beside a spectator, given to the blocks that age by them (review R5-80). Minecraft
 * gives a chunk random ticks only while a player who is not a spectator is within 128 blocks of it
 * ({@code ServerChunkCache.tickChunks}, {@code ChunkMap.playerIsCloseEnoughForSpawning}, 1.21.1). So beside a player
 * watching in spectator the colonies' leftovers stood still for good: an eaten log's mud cast, the plates of a filled
 * gallery, every crust as fresh as the day it was laid, pulp heaps that never sank, a midden of remains that never
 * weathered. The blocks in {@link #TAG} ({@code data/colonyframework/tags/block/ticks_beside_spectators.json}, each species
 * mod adds its own) get them here, at Minecraft's own pace, in the chunks within 128 blocks of a spectator where no other
 * player stands that near (there the game gives them itself). Nothing else changes beside a spectator: Minecraft's own
 * blocks (crops, grass, leaves) stay as the game has them, and so does every block left out of the tag.
 *
 * <p>Each loaded chunk is looked at once a second, on its own tick of the second (the cost spread over the 20 ticks), and
 * gets the second's random ticks at once: per section that holds a block of the tag, {@code randomTickSpeed} picks a
 * tick, 20 a second, each a random cell of the section ticked if a block of the tag stands there. A section's palette
 * tells cheaply whether it can hold one ({@link LevelChunkSection#maybeHas}), so the sections of plain ground cost almost
 * nothing. With the game rule at 0 nothing is given (the player has stopped random ticks).
 */
public final class WithheldTicks {
    /** The blocks that get the random ticks held back beside a spectator. */
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ColonyFrameworkMod.MODID, "ticks_beside_spectators"));
    /** Minecraft's own reach for random ticks: a player nearer than this (blocks, flat, to the chunk's middle). */
    public static final int GAME_REACH = 128;
    private static final int REACH_CHUNKS = GAME_REACH / 16 + 1;

    /** Its own dice (the world's random is the game's; the colonies' are theirs). */
    private static final RandomSource RANDOM = RandomSource.create();

    private WithheldTicks() {
    }

    /** Every tick, from the level's tick: the chunks whose turn of the second it is get their second's random ticks. */
    public static void tick(ServerLevel level) {
        int speed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        if (speed <= 0) {
            return;
        }
        List<ServerPlayer> players = level.players();
        boolean anySpectator = false;
        for (ServerPlayer player : players) {
            anySpectator |= player.isSpectator();
        }
        if (!anySpectator) {
            return;
        }
        int turn = (int) Math.floorMod(level.getGameTime(), 20L);
        Set<Long> done = new HashSet<>();
        for (ServerPlayer spectator : players) {
            if (!spectator.isSpectator()) {
                continue;
            }
            ChunkPos at = spectator.chunkPosition();
            for (int cx = at.x - REACH_CHUNKS; cx <= at.x + REACH_CHUNKS; cx++) {
                for (int cz = at.z - REACH_CHUNKS; cz <= at.z + REACH_CHUNKS; cz++) {
                    if (Math.floorMod(cx * 7 + cz * 13, 20) != turn || !done.add(ChunkPos.asLong(cx, cz))) {
                        continue;
                    }
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    if (!near(spectator, chunk) || !level.isNaturalSpawningAllowed(chunk) || givenByTheGame(level, chunk)) {
                        continue;
                    }
                    LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
                    if (loaded != null) {
                        give(level, loaded, speed * 20, pos -> true);
                    }
                }
            }
        }
    }

    /**
     * Whether Minecraft itself gives this chunk random ticks now: the game rule is above 0 and a player who is not a
     * spectator is within 128 blocks of the chunk's middle. (Asked by the mound's drying too, {@code MoundDrying}: one
     * judge of where the game ticks.)
     */
    public static boolean givenByTheGame(ServerLevel level, ChunkPos chunk) {
        if (level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) <= 0) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && near(player, chunk)) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(ServerPlayer player, ChunkPos chunk) {
        double dx = player.getX() - chunk.getMiddleBlockX();
        double dz = player.getZ() - chunk.getMiddleBlockZ();
        return dx * dx + dz * dz < (double) GAME_REACH * GAME_REACH;
    }

    /**
     * {@code picks} random ticks for each section of the chunk that may hold a block of the tag, as Minecraft gives them
     * ({@code ServerLevel.tickChunk}): a random cell of the section, ticked if a block of the tag that ticks stands there.
     * Returns how many blocks were ticked. Public for the tests, which tick only the cells of their own scene ({@code only}).
     */
    public static int give(ServerLevel level, LevelChunk chunk, int picks, Predicate<BlockPos> only) {
        LevelChunkSection[] sections = chunk.getSections();
        int ticked = 0;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (!section.isRandomlyTickingBlocks() || !section.maybeHas(state -> state.is(TAG))) {
                continue;
            }
            int x0 = chunk.getPos().getMinBlockX();
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            int z0 = chunk.getPos().getMinBlockZ();
            for (int n = 0; n < picks; n++) {
                int x = RANDOM.nextInt(16);
                int y = RANDOM.nextInt(16);
                int z = RANDOM.nextInt(16);
                BlockState state = section.getBlockState(x, y, z);
                BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
                if (state.is(TAG) && state.isRandomlyTicking() && only.test(pos)) {
                    state.randomTick(level, pos, RANDOM);
                    ticked++;
                }
            }
        }
        return ticked;
    }
}
