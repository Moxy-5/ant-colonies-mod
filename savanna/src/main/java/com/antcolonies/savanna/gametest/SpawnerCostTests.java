package com.antcolonies.savanna.gametest;

import static com.antcolonies.gametest.TestScenes.SURFACE;

import java.util.ArrayList;
import java.util.List;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.worldgen.RiverZones;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What the world's own spawner costs for the mod's spawning groups (review R8-12 and R8-2), measured, not guessed. Run
 * only when named (the {@code stress} category): it puts a stand-in player into the test world.
 *
 * <p>Minecraft tries every group that is under its cap in every chunk round a player on every tick. A try draws one
 * random spot in the chunk; in solid ground it ends there (most tries: the spot's height is drawn from the world's
 * floor up to the surface), in air or water it goes on to up to three looks for a player and a spawn list. The test
 * times both ends of a try for a group with no spawn list in the biome (every one of ours, anywhere but the savanna's
 * waters), and the count of a kind within 64 blocks that {@link RiverZones#room} makes.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class SpawnerCostTests {
    private static final int WARM_UP = 20_000, RUNS = 200_000;
    /** Chunks round one player the spawner tries each tick (within 128 blocks: about 16 by 16). */
    private static final int CHUNKS = 256;

    @GameTest(template = "arena", timeoutTicks = 200, skyAccess = true, batch = "spawner_cost")
    public static void whatTheSpawnerCostsForOurGroups(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(new BlockPos(2, SURFACE + 1, 2));
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        BlockPos solid = helper.absolutePos(new BlockPos(50, SURFACE, 50));
        BlockPos open = solid.above(2);
        level.setBlockAndUpdate(solid, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(open, Blocks.AIR.defaultBlockState());
        List<Entity> made = new ArrayList<>();
        try {
            helper.assertTrue(!level.getSharedSpawnPos().closerThan(open, 40), "the test stands on the world's spawn point: the spawner skips it");
            helper.assertTrue(level.getNearestPlayer(open.getX(), open.getY(), open.getZ(), -1.0, false) != null, "the stand-in player is not in the world");
            ChunkAccess chunk = level.getChunk(open);
            List<MobCategory> ours = new ArrayList<>();
            for (MobCategory category : MobCategory.values()) {
                if (category.getName().startsWith(SavannaMod.MODID + ":")) {
                    ours.add(category);
                }
            }
            int everyTick = (int) ours.stream().filter(c -> !c.isPersistent()).count();
            int before = level.getEntitiesOfClass(Mob.class, new AABB(open).inflate(64)).size();
            double openNanos = time(level, chunk, open, ours), solidNanos = time(level, chunk, solid, ours);
            int after = level.getEntitiesOfClass(Mob.class, new AABB(open).inflate(64)).size();
            helper.assertTrue(after == before, "the tries spawned " + (after - before) + " mobs: not the empty-list case");
            // The count RiverZones.room makes, among 150 mobs: every height (as it is) against the water's heights only.
            for (int i = 0; i < 150; i++) {
                made.add(helper.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4 + i % 12 * 4, SURFACE + 1, 4 + i / 12 * 4)));
            }
            double wide = count(level, open, RiverZones.LOOK), flat = count(level, open, 16);
            SavannaMod.LOGGER.info(String.format("[spawner-cost] %d groups of ours, %d tried every tick. One try with no spawn list: %.0f ns in"
                            + " solid ground, %.0f ns in air or water. Per tick for one player (%d chunks): all solid %.3f ms, all open %.3f ms;"
                            + " land (3%% open) %.3f ms, river (20%% open) %.3f ms.",
                    ours.size(), everyTick, solidNanos, openNanos, CHUNKS, perTick(solidNanos, everyTick), perTick(openNanos, everyTick),
                    perTick(0.97 * solidNanos + 0.03 * openNanos, everyTick), perTick(0.8 * solidNanos + 0.2 * openNanos, everyTick)));
            SavannaMod.LOGGER.info(String.format("[spawner-cost] counting a kind among 150 mobs within 64 blocks: %.1f microseconds at every"
                    + " height, %.1f within 16 up and down.", wide / 1000.0, flat / 1000.0));
        } finally {
            made.forEach(Entity::discard);
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static double perTick(double nanosATry, int groups) {
        return nanosATry * groups * CHUNKS / 1_000_000.0;
    }

    /** Nanoseconds for one try of one group at a spot (the mean over every group of ours). */
    private static double time(ServerLevel level, ChunkAccess chunk, BlockPos pos, List<MobCategory> groups) {
        NaturalSpawner.SpawnPredicate any = (type, at, in) -> true;
        NaturalSpawner.AfterSpawnCallback none = (mob, in) -> { };
        for (int i = 0; i < WARM_UP; i++) {
            NaturalSpawner.spawnCategoryForPosition(groups.get(i % groups.size()), level, chunk, pos, any, none);
        }
        long start = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            NaturalSpawner.spawnCategoryForPosition(groups.get(i % groups.size()), level, chunk, pos, any, none);
        }
        return (System.nanoTime() - start) / (double) RUNS;
    }

    /** Nanoseconds for one count of chickens within {@code LOOK} blocks sideways and {@code upDown} blocks up and down. */
    private static double count(ServerLevel level, BlockPos pos, int upDown) {
        AABB box = new AABB(pos).inflate(RiverZones.LOOK, upDown, RiverZones.LOOK);
        int runs = 5_000, found = 0;
        for (int i = 0; i < 1_000; i++) {
            found += level.getEntitiesOfClass(Mob.class, box, e -> e.getType() == EntityType.CHICKEN).size();
        }
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            found += level.getEntitiesOfClass(Mob.class, box, e -> e.getType() == EntityType.CHICKEN).size();
        }
        long nanos = System.nanoTime() - start;
        return found == 0 ? Double.NaN : nanos / (double) runs;
    }
}
