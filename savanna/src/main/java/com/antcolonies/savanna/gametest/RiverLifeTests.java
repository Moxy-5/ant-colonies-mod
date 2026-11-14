package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.TilapiaEntity;
import com.antcolonies.savanna.block.AlgaeBlock;
import com.antcolonies.savanna.registry.SavannaBlocks;
import com.antcolonies.savanna.registry.SavannaEntities;
import com.antcolonies.savanna.worldgen.RiverAlgaeFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * River life (M9, 2026-11-14): the algae on the river bed, the first link of the food chain. One pond, shallow at one
 * end (2 blocks of water) and deep at the other (7), checks laying, growing by depth, spreading, grazing and breaking.
 * The same pond holds the Nile tilapia, the grazing fish: they graze the shelf's algae down, grow up from young and wear
 * their three looks.
 */
@GameTestHolder(SavannaMod.MODID)
@PrefixGameTestTemplate(false)
public class RiverLifeTests {
    private static final int X0 = 2, X1 = 11, Z0 = 2, Z1 = 9;   // the pond's inside
    private static final int SHALLOW_TO = 6;                   // x up to here: a shelf with 2 blocks of water over it
    private static final int TOP = SURFACE + 6;                // the water's surface

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void algaeGrowsByDepthSpreadsAndIsGrazed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        algaeChecks(helper, level);
    }

    /** The pond: stone walls, a shelf under 2 blocks of water up to {@link #SHALLOW_TO}, 7 blocks deep past it. */
    private static void buildPond(GameTestHelper helper) {
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                boolean wall = x < X0 || x > X1 || z < Z0 || z > Z1;
                for (int y = SURFACE; y <= TOP + 1; y++) {
                    BlockPos at = new BlockPos(x, y, z);
                    if (wall) {
                        helper.setBlock(at, y <= TOP ? Blocks.STONE : Blocks.AIR);
                    } else if (x <= SHALLOW_TO && y <= TOP - 2) {
                        helper.setBlock(at, Blocks.STONE);
                    } else {
                        helper.setBlock(at, y <= TOP ? Blocks.WATER : Blocks.AIR);
                    }
                }
            }
        }
    }

    private static void algaeChecks(GameTestHelper helper, ServerLevel level) {
        // 1. new land's laying: thick or lush on the shelf, never more than thin in the deep
        BlockPos corner = helper.absolutePos(new BlockPos(X0, TOP, Z0));
        int laid = RiverAlgaeFeature.lay(level, corner.getX(), corner.getZ(), X1 - X0 + 1, Z1 - Z0 + 1, corner.getY(), at -> true);
        int shallowThick = 0;
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean shallow = x <= SHALLOW_TO;
                BlockState algae = helper.getBlockState(new BlockPos(x, shallow ? TOP - 1 : SURFACE, z));
                if (!(algae.getBlock() instanceof AlgaeBlock)) {
                    if (shallow) {
                        helper.fail("no algae laid on the shallow shelf at " + x + ", " + z);
                    }
                    continue;
                }
                int g = algae.getValue(AlgaeBlock.GROWTH);
                if (!shallow && g > AlgaeBlock.THIN) {
                    helper.fail("algae " + g + " in 7 blocks of water at " + x + ", " + z + " (thin at most)");
                }
                if (shallow && g >= AlgaeBlock.THICK) {
                    shallowThick++;
                }
            }
        }
        if (laid == 0 || shallowThick < 10) {
            helper.fail("laid " + laid + ", thick or lush on the shelf " + shallowThick);
        }
        // 2. growing: a bare mat in the shallows grows lush, one in the deep only thin
        BlockPos shallow = new BlockPos(X0 + 1, TOP - 1, Z0 + 1), deep = new BlockPos(X1 - 1, SURFACE, Z1 - 1);
        BlockState bare = SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        helper.setBlock(shallow, bare);
        helper.setBlock(deep, bare);
        for (int i = 0; i < 400; i++) {
            for (BlockPos p : new BlockPos[] {shallow, deep}) {
                BlockPos abs = helper.absolutePos(p);
                level.getBlockState(abs).randomTick(level, abs, level.random);
            }
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        helper.assertBlockProperty(deep, AlgaeBlock.GROWTH, AlgaeBlock.THIN);
        // 3. grazing bites a stage off and never takes the last of it
        BlockPos absShallow = helper.absolutePos(shallow);
        for (int i = 0; i < 3; i++) {
            if (!AlgaeBlock.graze(level, absShallow)) {
                helper.fail("nothing to graze after " + i + " bites");
            }
        }
        if (AlgaeBlock.graze(level, absShallow)) {
            helper.fail("a bare mat was grazed");
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        // 4. spreading: the shelf cleared but one lush mat, which seeds the bare bed round it
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.WATER);
            }
        }
        BlockPos seed = new BlockPos(X0 + 2, TOP - 1, Z0 + 3);
        helper.setBlock(seed, bare.setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH));
        BlockPos absSeed = helper.absolutePos(seed);
        for (int i = 0; i < 1000; i++) {
            level.getBlockState(absSeed).randomTick(level, absSeed, level.random);
        }
        int spread = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                if (helper.getBlockState(new BlockPos(x, TOP - 1, z)).getBlock() instanceof AlgaeBlock) {
                    spread++;
                }
            }
        }
        if (spread < 3) {
            helper.fail("the lush mat seeded only " + (spread - 1) + " cells in 1000 ticks");
        }
        // 5. broken, it leaves water
        helper.destroyBlock(seed);
        if (!helper.getBlockState(seed).is(Blocks.WATER)) {
            helper.fail("broken algae left " + helper.getBlockState(seed) + ", not water");
        }
        helper.succeed();
    }

    /**
     * The tilapia (2026-11-14): hungry fish over a shelf of lush algae graze it down; a young fish is small and barred
     * and grows into a grown look; about 1 grown male in 3 wears the breeding colours, never a female; a bucket keeps a
     * fish's look and size; it spawns only in water.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void tilapiaGrazeGrowUpAndWearTheirLooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        BlockState lush = SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        int start = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), lush);
                start += AlgaeBlock.LUSH;
            }
        }
        // the looks, from many fish never put in the world
        int males = 0, breeding = 0;
        for (int i = 0; i < 400; i++) {
            TilapiaEntity fish = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
            fish.setUp(false, level.random);
            if (fish.look() == TilapiaEntity.BREEDING && !fish.isMale()) {
                helper.fail("a female wears the breeding colours");
            }
            if (fish.look() == TilapiaEntity.YOUNG) {
                helper.fail("a grown fish wears the young look");
            }
            float size = (float) fish.getAttributeValue(Attributes.SCALE);
            if (size < 0.84F || size > 1.16F) {
                helper.fail("a grown fish of size " + size);
            }
            if (fish.isMale()) {
                males++;
                if (fish.look() == TilapiaEntity.BREEDING) {
                    breeding++;
                }
            }
        }
        if (males < 150 || breeding < males * 0.2 || breeding > males * 0.47) {
            helper.fail(breeding + " of " + males + " males in breeding colours (about 1 in 3 wanted)");
        }
        // a bucket keeps a fish's look and size
        TilapiaEntity caught = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        do {
            caught.setUp(false, level.random);
        } while (caught.look() != TilapiaEntity.BREEDING);
        ItemStack bucket = caught.getBucketItemStack();
        caught.saveToBucketTag(bucket);
        TilapiaEntity poured = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        poured.setUp(true, level.random);
        poured.loadFromBucketTag(bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA).copyTag());
        if (poured.look() != TilapiaEntity.BREEDING || Math.abs(poured.ownSize() - caught.ownSize()) > 0.001F) {
            helper.fail("out of the bucket: look " + poured.look() + ", size " + poured.ownSize() + " (was " + caught.ownSize() + ")");
        }
        // spawning: in the pond's water (when its height is in the spawn band), never in the air
        BlockPos water = helper.absolutePos(new BlockPos(X1 - 1, SURFACE + 3, Z1 - 1));
        BlockPos air = helper.absolutePos(new BlockPos(X1 - 1, TOP + 1, Z1 - 1));
        if (Math.abs(water.getY() - level.getSeaLevel()) <= 6
                && !TilapiaEntity.canSpawn(SavannaEntities.TILAPIA.get(), level, MobSpawnType.NATURAL, water, level.random)) {
            helper.fail("may not spawn in the pond's water at " + water.toShortString());
        }
        if (TilapiaEntity.canSpawn(SavannaEntities.TILAPIA.get(), level, MobSpawnType.NATURAL, air, level.random)) {
            helper.fail("may spawn in the air over the pond");
        }
        // hungry grown fish in the deep end; a young one about to grow up
        TilapiaEntity[] school = new TilapiaEntity[4];
        for (int i = 0; i < school.length; i++) {
            school[i] = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1 - i, SURFACE + 3, Z0 + 2 + i));
            school[i].setUp(false, level.random);
            school[i].setPersistenceRequired();
            school[i].makeHungry();
        }
        TilapiaEntity young = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1, SURFACE + 4, Z1 - 1));
        young.setUp(true, level.random);
        young.setPersistenceRequired();
        if (young.look() != TilapiaEntity.YOUNG || young.getAttributeValue(Attributes.SCALE) > young.ownSize() * 0.7) {
            helper.fail("a young fish: look " + young.look() + ", size " + young.getAttributeValue(Attributes.SCALE));
        }
        young.setTicksToGrow(40);
        final int full = start;
        boolean[] sawGrazing = {false};
        helper.onEachTick(() -> {
            for (TilapiaEntity fish : school) {
                sawGrazing[0] |= fish.isGrazing();
            }
        });
        helper.runAtTickTime(80, () -> {
            if (young.look() == TilapiaEntity.YOUNG || Math.abs(young.getAttributeValue(Attributes.SCALE) - young.ownSize()) > 0.01) {
                helper.fail("not grown up: look " + young.look() + ", size " + young.getAttributeValue(Attributes.SCALE)
                        + " (own " + young.ownSize() + ")");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 80, "the young fish's check first");
            int left = 0;
            for (int x = X0; x <= SHALLOW_TO; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, TOP - 1, z));
                    if (state.getBlock() instanceof AlgaeBlock) {
                        left += state.getValue(AlgaeBlock.GROWTH);
                    }
                }
            }
            helper.assertTrue(sawGrazing[0] && full - left >= 3, "grazed " + (full - left) + " stages of " + full
                    + (sawGrazing[0] ? "" : ", no fish seen grazing"));
        });
    }
}
