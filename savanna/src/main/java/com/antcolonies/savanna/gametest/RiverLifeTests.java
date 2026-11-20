package com.antcolonies.savanna.gametest;

import static com.antcolonies.savanna.gametest.TermiteMoundTests.SURFACE;

import com.antcolonies.savanna.SavannaMod;
import com.antcolonies.savanna.animal.BarbEntity;
import com.antcolonies.savanna.animal.BullfrogEntity;
import com.antcolonies.savanna.animal.CatfishEntity;
import com.antcolonies.savanna.animal.FlyCloudEntity;
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
        sandBankBlockChecks(helper);
        algaeChecks(helper, level);
    }

    /**
     * The sand banks' blocks (2026-11-16, {@code docs/plans/sand-banks.md} step 2), checked beside the pond: every river
     * sand stands on stone, the crocodile slide keeps the way it was turned, pebbles take a look by position (a row is
     * not all one look), hold water, and wash away when the ground under them goes.
     */
    private static void sandBankBlockChecks(GameTestHelper helper) {
        int y = SURFACE;
        for (int x = 14; x <= 30; x++) {
            for (int z = 2; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE);
            }
        }
        int x = 14;
        for (var tone : java.util.List.of(SavannaBlocks.RED_SAND, SavannaBlocks.GOLDEN_SAND, SavannaBlocks.PALE_SAND)) {
            for (var block : tone.all()) {
                BlockPos at = new BlockPos(x++, y, 2);
                helper.setBlock(at, block.get());
                if (!helper.getBlockState(at).is(block.get())) {
                    helper.fail(block.getId() + " did not stay where it was put");
                }
            }
        }
        BlockPos slide = new BlockPos(14, y, 3);
        helper.setBlock(slide, SavannaBlocks.GOLDEN_SAND.slide().get().defaultBlockState()
                .setValue(com.antcolonies.savanna.block.RiverSand.Slide.AXIS, net.minecraft.core.Direction.Axis.X)
                .setValue(com.antcolonies.savanna.block.RiverSand.Slide.WET, true));
        BlockState turned = helper.getBlockState(slide);
        if (turned.getValue(com.antcolonies.savanna.block.RiverSand.Slide.AXIS) != net.minecraft.core.Direction.Axis.X
                || !turned.getValue(com.antcolonies.savanna.block.RiverSand.Slide.WET)) {
            helper.fail("the crocodile slide lost its turn or its wetness: " + turned);
        }
        var pebbles = SavannaBlocks.RIVER_PEBBLES.get();
        java.util.Set<Integer> looks = new java.util.HashSet<>();
        for (int px = 14; px <= 30; px++) {
            BlockPos at = new BlockPos(px, y, 4);
            BlockState state = pebbles.at(helper.absolutePos(at), false);
            looks.add(state.getValue(com.antcolonies.savanna.block.RiverPebblesBlock.LOOK));
            helper.setBlock(at, state);
            if (!helper.getBlockState(at).is(pebbles)) {
                helper.fail("pebbles did not stay on stone at " + at);
            }
        }
        if (looks.size() < 4) {
            helper.fail("a row of 17 pebble blocks showed only " + looks.size() + " of the 7 looks");
        }
        BlockPos wet = new BlockPos(14, y, 5);
        helper.setBlock(wet, pebbles.at(helper.absolutePos(wet), true));
        if (!helper.getLevel().getFluidState(helper.absolutePos(wet)).is(net.minecraft.tags.FluidTags.WATER)) {
            helper.fail("wet pebbles hold no water");
        }
        helper.setBlock(new BlockPos(14, y - 1, 4), Blocks.AIR);
        if (helper.getBlockState(new BlockPos(14, y, 4)).is(pebbles)) {
            helper.fail("pebbles stayed hanging in the air after the ground under them went");
        }
        for (int cx = 14; cx <= 30; cx++) {                     // cleared, so nothing falls or flows into the pond's checks
            for (int cz = 2; cz <= 6; cz++) {
                helper.setBlock(new BlockPos(cx, y, cz), Blocks.AIR);
            }
        }
    }

    /**
     * The pond: a stone floor and walls, a shelf under 2 blocks of water up to {@link #SHALLOW_TO}, 7 blocks deep past it.
     * The arena is empty air down to the test world's ground about 12 blocks lower, so without the floor the water poured
     * out under the pond and its current dragged the fish down (the catfish test's misses, 2026-11-14).
     */
    private static void buildPond(GameTestHelper helper) {
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                boolean wall = x < X0 || x > X1 || z < Z0 || z > Z1;
                helper.setBlock(new BlockPos(x, SURFACE - 1, z), Blocks.STONE);
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
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH));
        helper.assertBlockProperty(deep, AlgaeBlock.GROWTH, AlgaeBlock.THIN);
        helper.assertBlockProperty(deep, AlgaeBlock.STRANDS, 0);
        // 3. each bite takes 2 strands (a pair, one off each plane), the mat staying lush; with the strands gone the next
        // bite eats the mat down to the bare bed; the bare specks are never taken, and the patch grows back, strands too
        BlockPos absShallow = helper.absolutePos(shallow);
        for (int left = AlgaeBlock.fullStrands(AlgaeBlock.LUSH) - 1; left >= 0; left--) {
            if (!AlgaeBlock.graze(level, absShallow)) {
                helper.fail("nothing to graze with " + (left + 1) + " strand pairs standing");
            }
            helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, left);
            helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        }
        if (!AlgaeBlock.graze(level, absShallow)) {
            helper.fail("nothing to graze on a lush mat with its strands gone");
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.BARE);
        if (AlgaeBlock.graze(level, absShallow)) {
            helper.fail("a bare mat was grazed");
        }
        helper.assertTrue(helper.getBlockState(shallow).getBlock() instanceof AlgaeBlock, "a grazed mat is gone, it cannot grow back");
        for (int i = 0; i < 400; i++) {
            level.getBlockState(absShallow).randomTick(level, absShallow, level.random);
        }
        helper.assertBlockProperty(shallow, AlgaeBlock.GROWTH, AlgaeBlock.LUSH);
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH));
        AlgaeBlock.graze(level, absShallow);
        helper.assertBlockProperty(shallow, AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH) - 1);
        // 4. spreading: the shelf cleared but one lush mat, which seeds the bare bed round it
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.WATER);
            }
        }
        BlockPos seed = new BlockPos(X0 + 2, TOP - 1, Z0 + 3);
        helper.setBlock(seed, SavannaBlocks.ALGAE.get().whole(AlgaeBlock.LUSH));
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
        // 6. new land's old remains (2026-11-15): laid on the bed (the shelf or the deep floor), never afloat, part eaten
        // or bare, each set to its stage's bites
        int remainsLaid = com.antcolonies.savanna.worldgen.RiverRemainsFeature.scatter(level, level.random,
                corner.getX(), corner.getZ(), X1 - X0 + 1, Z1 - Z0 + 1, corner.getY(), 6, at -> true);
        java.util.List<com.antcolonies.savanna.animal.RemainsEntity> lying = level.getEntitiesOfClass(
                com.antcolonies.savanna.animal.RemainsEntity.class, net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(X0, SURFACE, Z0)),
                        helper.absolutePos(new BlockPos(X1, TOP + 1, Z1))));
        if (remainsLaid != 6 || lying.size() != 6) {
            helper.fail("old remains: laid " + remainsLaid + ", found " + lying.size() + " (6 asked)");
        }
        for (com.antcolonies.savanna.animal.RemainsEntity r : lying) {
            BlockPos cell = r.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));    // relativePos turns x and z round
            int bed = cell.getX() <= SHALLOW_TO ? TOP - 1 : SURFACE;
            if (cell.getY() != bed) {
                helper.fail("old remains at " + cell + ", the bed is at " + bed);
            }
            if (com.antcolonies.savanna.animal.RemainsEntity.stageOf(r.kind(), r.bitesLeft()) != r.stage() || r.stage() < 1 || r.stage() > 4) {
                helper.fail("old remains with a bad stage " + r.stage());
            }
            r.discard();
        }
        helper.succeed();
    }

    /**
     * The tilapia (2026-11-14): hungry fish over a shelf of lush algae graze it down; a young fish is small and barred
     * and grows into a grown look; no fish wears the breeding colours away from a nest pit (since step 2 of the animal life
     * cycle only a male holding a pit does: AnimalLifeTests); a bucket keeps a fish's look, youth and size; it spawns
     * only in water.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void tilapiaGrazeGrowUpAndWearTheirLooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        BlockState lush = SavannaBlocks.ALGAE.get().whole(AlgaeBlock.LUSH);
        int start = 0;
        for (int x = X0; x <= SHALLOW_TO; x++) {
            for (int z = Z0; z <= Z1; z++) {
                helper.setBlock(new BlockPos(x, TOP - 1, z), lush);
                start += AlgaeBlock.bitesLeft(lush);
            }
        }
        // the looks, from many fish never put in the world
        int males = 0;
        for (int i = 0; i < 400; i++) {
            TilapiaEntity fish = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
            fish.setUp(false, level.random);
            if (fish.look() == TilapiaEntity.BREEDING) {
                helper.fail("a fish with no nest pit wears the breeding colours");
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
            }
        }
        if (males < 150 || males > 250) {
            helper.fail(males + " males of 400 (about half wanted)");
        }
        // a bucket keeps a fish's look, youth and size
        TilapiaEntity caught = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        caught.setUp(true, level.random);
        ItemStack bucket = caught.getBucketItemStack();
        caught.saveToBucketTag(bucket);
        TilapiaEntity poured = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        poured.setUp(false, level.random);
        poured.loadFromBucketTag(bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA).copyTag());
        if (poured.look() != TilapiaEntity.YOUNG || !poured.isYoung() || Math.abs(poured.ownSize() - caught.ownSize()) > 0.001F
                || Math.abs(poured.getAttributeBaseValue(Attributes.SCALE) - caught.getAttributeBaseValue(Attributes.SCALE)) > 0.001) {
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
        // natural spawning is wired: the placement rule signed up and the savanna waters listing the fish
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.TILAPIA.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the tilapia's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river")))) {
            boolean listed = biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish())
                    .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.TILAPIA.get());
            if (!listed) {
                helper.fail("no tilapia in the spawn list of " + key.location());
            }
        }
        // river fish have their own spawning group and cap, apart from Minecraft's fish; a wild fish stays near a
        // player, goes when left far behind, and counts toward its group's cap
        var group = SavannaEntities.TILAPIA.get().getCategory();
        if (group != com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()
                || group.getMaxInstancesPerChunk() <= net.minecraft.world.entity.MobCategory.WATER_AMBIENT.getMaxInstancesPerChunk()
                || group.isPersistent()) {
            helper.fail("tilapia are not in the river fish group with its own bigger cap: " + group.getName());
        }
        TilapiaEntity wild = new TilapiaEntity(SavannaEntities.TILAPIA.get(), level);
        wild.setUp(false, level.random);
        if (wild.removeWhenFarAway(64.0 * 64.0) || !wild.removeWhenFarAway(200.0 * 200.0)
                || wild.isPersistenceRequired() || wild.requiresCustomPersistence()) {
            helper.fail("a wild tilapia is removed near a player, kept far away, or no longer counts toward the cap");
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
                    left += AlgaeBlock.bitesLeft(state);
                }
            }
            helper.assertTrue(sawGrazing[0] && full - left >= 3, "grazed " + (full - left) + " bites of " + full
                    + (sawGrazing[0] ? "" : ", no fish seen grazing"));
        });
    }

    /**
     * The catfish (2026-11-14): spawns in the river fish group in the savanna's waters, only in water at least 3 deep; a
     * hungry one hunts a wild tilapia in the pond's deep end, strikes with its mouth open and swallows it, and is fed;
     * a tilapia from a bucket (the player's own) is left alone; it is 2 blocks long at the usual size.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aHungryCatfishHuntsAndSwallowsATilapia(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        // wiring: the placement rule, the savanna waters' spawn list, the river fish group
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CATFISH.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the catfish's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.CATFISH.get());
        if (!listed || SavannaEntities.CATFISH.get().getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()) {
            helper.fail("the catfish is not in the savanna's river fish spawn list");
        }
        // never in the shelf's 2 blocks of water (when the pond is in the spawn band), always in the deep end
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE + 3, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (CatfishEntity.canSpawn(SavannaEntities.CATFISH.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a catfish may spawn in 2 blocks of water");
            }
            if (!CatfishEntity.canSpawn(SavannaEntities.CATFISH.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a catfish may not spawn in the deep end");
            }
        }
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 3, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        if (Math.abs(catfish.getAttributeValue(Attributes.SCALE) - 1.0) > 0.001) {
            helper.fail("a spawned catfish's size is " + catfish.getAttributeValue(Attributes.SCALE) + " (1 before its own size is rolled)");
        }
        TilapiaEntity pet = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 2));
        pet.setUp(false, level.random);
        pet.setFromBucket(true);
        TilapiaEntity wild = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1, SURFACE + 4, Z1 - 1));
        wild.setUp(false, level.random);
        wild.setPersistenceRequired();
        boolean[] sawStrike = {false};
        // the catch (2026-11-15): from the moment the tilapia is gone it is held in the catfish's mouth, in its own
        // look, for the catch clip's length, then the mouth is empty again
        int wildLook = wild.look();
        int[] held = {0};
        boolean[] wrongLook = {false};
        // the side it lies on: the side of the tilapia the catfish was on at the catch (0 not caught yet, 1 right, 2 wrong)
        int[] side = {0};
        double[] was = new double[5];
        // the bite-off (2026-11-15): half the catches the jaws shut early, the front half falling away as remains
        boolean[] cut = {false};
        helper.onEachTick(() -> {
            sawStrike[0] |= catfish.isStriking();
            if (!wild.isRemoved()) {
                was[0] = wild.getX();
                was[1] = wild.getZ();
                was[2] = wild.getYRot();
                was[3] = catfish.getX();
                was[4] = catfish.getZ();
            }
            if (catfish.isHolding()) {
                if (side[0] == 0) {
                    double yaw = Math.toRadians(was[2]);
                    double toRight = (was[3] - was[0]) * -Math.cos(yaw) + (was[4] - was[1]) * -Math.sin(yaw);
                    // a catfish nearly dead astern may cross the line in the catch's own tick: only a clear side counts
                    side[0] = Math.abs(toRight) < 0.15 || catfish.heldOnRight() == toRight > 0 ? 1 : 2;
                }
                held[0]++;
                cut[0] = catfish.heldCut();
                wrongLook[0] |= catfish.heldLook() != wildLook || !wild.isRemoved();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(catfish.isAlive(), "the catfish died");
            helper.assertTrue(side[0] != 2, "the caught tilapia lies on the side away from where the catfish came from");
            helper.assertTrue(!wrongLook[0], "the tilapia in the catfish's mouth was not the caught one's look, or the caught one still swam");
            int want = cut[0] ? 42 : 52;
            helper.assertTrue(wild.isRemoved() && !catfish.isHolding() && held[0] >= want - 7 && held[0] <= want + 8,
                    "the caught tilapia was held in the mouth for " + held[0] + " ticks"
                            + (catfish.isHolding() ? ", still held" : "") + " (about " + want + (cut[0] ? ", bitten off" : "") + ", then gone)");
            var halves = level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class, catfish.getBoundingBox().inflate(4.0));
            helper.assertTrue(halves.size() == (cut[0] ? 1 : 0) && (!cut[0] || halves.get(0).kind() == com.antcolonies.savanna.animal.RemainsEntity.Kind.TILAPIA_HALF
                            && halves.get(0).stage() == 1),
                    (cut[0] ? "bitten off, " : "swallowed whole, ") + halves.size() + " remains by the catfish"
                            + (halves.isEmpty() ? "" : " (" + halves.get(0).kind() + ", stage " + halves.get(0).stage() + ")")
                            + " (a bite-off leaves 1 fresh tilapia half, a swallow none)");
            // scraps (2026-11-15): every fish eaten in the water leaves a fresh film of flecks on the bed under it
            int scraps = 0;
            for (BlockPos at : BlockPos.betweenClosed(catfish.blockPosition().offset(-6, -8, -6), catfish.blockPosition().offset(6, 4, 6))) {
                if (level.getBlockState(at).is(com.antcolonies.savanna.registry.SavannaBlocks.SCRAPS.get())) {
                    scraps++;
                }
            }
            helper.assertTrue(scraps >= 1, "no scraps on the bed after the meal (a fish eaten leaves flecks)");
            helper.assertTrue(wild.isRemoved() && sawStrike[0] && !catfish.isHungry(),
                    "the wild tilapia " + (wild.isRemoved() ? "is gone" : "still swims") + (sawStrike[0] ? "" : ", no strike seen")
                            + (catfish.isHungry() ? ", the catfish still hungry" : ""));
            helper.assertTrue(pet.isAlive(), "the bucket tilapia was eaten");
            SavannaMod.LOGGER.info("[catfish] the tilapia was {}", cut[0] ? "bitten off, its front half left" : "swallowed whole");
        });
    }

    /**
     * The catfish comes up for a gulp of air (2026-11-18, the user's ask): a catfish on the bottom of the pond's deep
     * end that wants air swims up, gulps with its body at the surface (its snout out), and goes back down; then it waits
     * 2 to 5 minutes before the next.
     */
    @GameTest(template = "arena", timeoutTicks = 500)
    public static void aCatfishComesUpToGulpAir(GameTestHelper helper) {
        buildPond(helper);
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE, Z0 + 3));
        catfish.setPersistenceRequired();
        catfish.makeWantAir();
        double top = helper.absolutePos(new BlockPos(0, TOP, 0)).getY() + 8 / 9.0;
        double[] gulpY = {Double.NaN};
        boolean[] down = {false};
        StringBuilder path = new StringBuilder();
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (tick[0]++ % 20 == 0) {
                path.append(String.format(java.util.Locale.ROOT, " %.1f", top - catfish.getY()));
            }
            if (catfish.isGulping() && Double.isNaN(gulpY[0])) {
                gulpY[0] = catfish.getY();
            }
            if (!Double.isNaN(gulpY[0]) && !catfish.isGulping() && catfish.getY() < gulpY[0] - 1.0) {
                down[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(gulpY[0]), "the catfish never gulped (depth under the surface every second:" + path + ")");
            helper.assertTrue(top - gulpY[0] < 0.8, "the catfish gulped " + (top - gulpY[0]) + " under the surface");
            helper.assertTrue(down[0], "the catfish did not go back down after its gulp (depths:" + path + ")");
            helper.assertTrue(catfish.isAlive() && catfish.isInWater(), "the catfish is out of the water");
            SavannaMod.LOGGER.info("[catfish] gulped air after {} ticks, {} under the surface; depths every second:{}",
                    tick[0], String.format(java.util.Locale.ROOT, "%.2f", top - gulpY[0]), path);
        });
    }

    /**
     * The school after a hunt (the user's report 2026-11-14: after the catfish struck at them a few times the tilapia
     * huddled in a corner and stopped moving). A school of 8 in the deep end, a hungry catfish; once it has eaten, every
     * tilapia left must still be swimming about: none stands still (moves less than half a block across) for 20 s. The fish are
     * wild (not kept for ever) and no player is counted near, as with the user watching in spectator mode: Minecraft
     * counts only non-spectators near a mob, and a mob with nobody counted near stops wandering after 5 s.
     */
    @GameTest(template = "arena", timeoutTicks = 3200)
    public static void theSchoolSwimsOnAfterAHunt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        java.util.List<TilapiaEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // made by hand: helper.spawn marks a mob kept for ever, and Minecraft zeroes a kept mob's idle clock
            TilapiaEntity t = SavannaEntities.TILAPIA.get().create(level);
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(X1 - 0.5 - i % 3, SURFACE + 2 + i % 3, Z1 - 0.5 - i / 3));
            t.moveTo(at.x, at.y, at.z);
            t.setUp(false, level.random);
            level.addFreshEntity(t);
            school.add(t);   // not kept for ever: as wild fish with only a spectator near (no player counted)
        }
        for (int i = 1; i < school.size(); i++) {
            school.get(i).startFollowing(school.get(0));
        }
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 3, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        int[] fedAt = {-1};
        java.util.Map<TilapiaEntity, net.minecraft.world.phys.Vec3> lastAt = new java.util.HashMap<>();
        java.util.Map<TilapiaEntity, Double> walked = new java.util.HashMap<>();
        int[] tick = {0};
        StringBuilder still = new StringBuilder();
        helper.onEachTick(() -> {
            tick[0]++;
            if (fedAt[0] < 0 && !catfish.isHungry()) {
                fedAt[0] = tick[0];
            }
            if (fedAt[0] < 0 || tick[0] < fedAt[0] + 100) {
                return;
            }
            int since = tick[0] - fedAt[0] - 100;
            for (TilapiaEntity t : school) {
                if (t.isRemoved()) {
                    continue;
                }
                net.minecraft.world.phys.Vec3 was = lastAt.put(t, t.position());
                if (was != null) {
                    walked.merge(t, Math.hypot(was.x - t.getX(), was.z - t.getZ()), Double::sum);   // across: an idle fish sinks
                }
            }
            if (since > 0 && since % 400 == 0) {
                for (TilapiaEntity t : school) {
                    if (t.isRemoved()) {
                        continue;
                    }
                    double w = walked.getOrDefault(t, 0.0);
                    if (w < 0.5 && still.length() < 2000) {
                        String goals = t.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning())
                                .map(g -> g.getGoal().getClass().getSimpleName()).collect(java.util.stream.Collectors.joining(","));
                        still.append(String.format(java.util.Locale.ROOT, " | tick %d: fish %d moved %.2f in 20 s at %s, follower %b, leads %b, nav done %b, idle %d, goals [%s], leader %s %.1f away, path %s",
                                since, school.indexOf(t), w, helper.relativeVec(t.position()), t.isFollower(), t.hasFollowers(),
                                t.getNavigation().isDone(), t.getNoActionTime(), goals,
                                t.followedForTests() == null ? "-" : String.valueOf(school.indexOf(t.followedForTests())),
                                t.followedForTests() == null ? 0.0 : t.distanceTo(t.followedForTests()),
                                t.followedForTests() == null ? "-" : String.valueOf(t.getNavigation().createPath(t.followedForTests(), 0))));
                    }
                    walked.put(t, 0.0);
                }
            }
            if (since >= 2000) {
                if (still.length() > 0) {
                    helper.fail("tilapia stood still after the hunt:" + still);
                } else {
                    helper.succeed();
                }
            }
        });
    }

    /**
     * Sand to bask on (sand banks, 2026-11-16): a crocodile in a long channel beside grass banks, wanting the sun with no
     * company near, swims past the grass to a sand bank 40 blocks off and basks there, never on the grass.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aCrocodileSwimsToASandBankToBask(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int floor = TOP - 4;
        for (int x = 1; x <= 59; x++) {
            for (int z = 4; z <= 26; z++) {
                boolean channel = x >= 2 && x <= 58 && z >= 12 && z <= 17;
                boolean sand = x >= 42 && x <= 54 && z >= 18 && z <= 22;
                for (int y = floor; y <= TOP + 2; y++) {
                    BlockState b;
                    if (y == floor) {
                        b = Blocks.STONE.defaultBlockState();
                    } else if (channel) {
                        b = (y <= TOP ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                    } else if (y < TOP) {
                        b = (sand ? (net.minecraft.world.level.block.Block) SavannaBlocks.GOLDEN_SAND.dry().get() : Blocks.DIRT).defaultBlockState();
                    } else if (y == TOP) {
                        b = sand ? (z == 18 ? SavannaBlocks.GOLDEN_SAND.wet() : SavannaBlocks.GOLDEN_SAND.dry()).get().defaultBlockState()
                                : Blocks.GRASS_BLOCK.defaultBlockState();
                    } else {
                        b = Blocks.AIR.defaultBlockState();
                    }
                    helper.setBlock(new BlockPos(x, y, z), b);
                }
            }
        }
        helper.setDayTime(6000);
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(6, TOP - 1, 14));
        croc.setPersistenceRequired();
        croc.wantSun();
        long[] at = {-1};
        helper.onEachTick(() -> {
            if (at[0] >= 0 || !croc.isBasking() || croc.isInWater() || !croc.onGround()) {
                return;
            }
            BlockState under = level.getBlockState(croc.blockPosition().below());
            if (!under.is(SavannaBlocks.GOLDEN_SAND.dry().get()) && !under.is(SavannaBlocks.GOLDEN_SAND.wet().get())) {
                helper.fail("the crocodile basks on " + under.getBlock().getName().getString() + " at "
                        + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", not on the sand bank");
            }
            at[0] = helper.getTick();
            SavannaMod.LOGGER.info("[crocodile] basking on the sand bank {} ticks after wanting the sun", at[0]);
        });
        helper.succeedWhen(() -> helper.assertTrue(at[0] >= 0, "the crocodile has not basked on the sand bank: at "
                + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", sand spot "
                + (croc.sandAt == null ? "none" : croc.sandAt.subtract(helper.absolutePos(BlockPos.ZERO)))));
    }

    /**
     * Mostly solitary, basking together (2026-11-15, the user's ask): the world spawns no crocodile within
     * {@code SPAWN_APART} blocks of another (an egg or command still may); a crocodile basking on the bank, and a second
     * one in the pond wanting the sun climbs out and lies beside it, side by side and facing the same way.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void crocodilesSpawnApartAndBaskTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 7; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(6000);
        com.antcolonies.savanna.animal.CrocodileEntity first = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 + 4, TOP + 1, Z0 + 4));
        first.setPersistenceRequired();
        first.hungerIn = 100_000;
        first.wantSun();
        BlockPos inPond = helper.absolutePos(new BlockPos(X1 - 2, SURFACE + 3, Z0 + 3));
        if (com.antcolonies.savanna.animal.CrocodileEntity.farFromOthers(level, inPond)) {
            helper.fail("the world may spawn a crocodile " + Math.round(Math.sqrt(first.distanceToSqr(inPond.getCenter()))) + " blocks from another");
        }
        if (!com.antcolonies.savanna.animal.CrocodileEntity.farFromOthers(level, inPond.offset(40, 0, 0))) {
            helper.fail("the world may not spawn a crocodile 40 blocks from another");
        }
        com.antcolonies.savanna.animal.CrocodileEntity second = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 3));
        second.setPersistenceRequired();
        second.hungerIn = 100_000;
        second.sunIn = 100_000;
        boolean[] called = {false};
        helper.onEachTick(() -> {
            if (!called[0] && first.isBasking() && !first.isInWater()) {
                called[0] = true;
                second.wantSun();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(first.isBasking() && !first.isInWater(), "the first crocodile is not basking on the bank");
            helper.assertTrue(second.isBasking() && !second.isInWater() && second.onGround(), "the second crocodile is not basking on the bank: at "
                    + second.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            double apart = Math.sqrt(Math.pow(first.getX() - second.getX(), 2) + Math.pow(first.getZ() - second.getZ(), 2));
            helper.assertTrue(apart > 1.2 && apart < 2.6, "the second crocodile lies " + String.format(java.util.Locale.ROOT, "%.1f", apart)
                    + " blocks from the first (side by side is about 1.7)");
            float turn = Math.abs(net.minecraft.util.Mth.degreesDifference(first.getYRot(), second.getYRot()));
            helper.assertTrue(turn < 20.0F, "the second crocodile faces " + Math.round(turn) + " degrees off the first's way");
        });
    }

    /**
     * The Nile crocodile's first step (2026-11-15): put in the pond's deep end by day and wanting the sun, it swims to
     * the bank, climbs out and basks (the mouth-open pose is the synced basking flag); told it has had enough, it walks
     * back and slides into the water. Its swimming and walking speeds go to the log. Then its life step 3 (2026-11-15):
     * basking again and hit, it rushes back and lunges in within a few seconds, and sinks out of sight while startled;
     * then wanting the sun to rest half out, it lies in the water nose to a bank, basking with its front over the bank.
     */
    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void aCrocodileClimbsOutToBaskAndGoesBackIn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 6; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(6000);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CROCODILE.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the crocodile's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        if (biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverHunter())
                .unwrap().stream().noneMatch(d -> d.type == SavannaEntities.CROCODILE.get())) {
            helper.fail("the crocodile is not in the savanna's river hunter spawn list");
        }
        // the world's own last checks on a natural spawn, in the pond's deep water (BUGS 218: a land mob's check refused water)
        com.antcolonies.savanna.animal.CrocodileEntity trial = new com.antcolonies.savanna.animal.CrocodileEntity(SavannaEntities.CROCODILE.get(), level);
        BlockPos trialAt = helper.absolutePos(new BlockPos(X1 - 2, SURFACE + 1, Z0 + 3));
        trial.moveTo(trialAt.getX() + 0.5, trialAt.getY(), trialAt.getZ() + 0.5, 0, 0);
        helper.assertTrue(level.getFluidState(trialAt).is(net.minecraft.tags.FluidTags.WATER), "the trial spot is not water");
        if (!trial.checkSpawnRules(level, net.minecraft.world.entity.MobSpawnType.NATURAL) || !trial.checkSpawnObstruction(level)) {
            helper.fail("the world would refuse a crocodile spawning in the water");
        }
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X1 - 2, SURFACE + 4, Z0 + 3));
        croc.setPersistenceRequired();
        croc.wantSun();
        // 0 not basked yet; 1 sent back; 2 back in, wanting the sun again; 3 basking again and hit; 4 in, startled; 5 resting half out
        int[] stage = {0};
        long[] hitAt = {0};
        double[] fastest = new double[2];
        helper.onEachTick(() -> {
            double dx = croc.getX() - croc.xo, dy = croc.getY() - croc.yo, dz = croc.getZ() - croc.zo;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int where = croc.isInWater() ? 0 : 1;
            fastest[where] = Math.max(fastest[where], speed);
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (croc.isBasking() && !croc.isInWater() && croc.onGround()) {
                        stage[0] = 1;
                        croc.sunIn = 100_000;        // enough sun
                    }
                }
                case 1 -> {
                    if (!croc.isBasking() && croc.isInWater()) {
                        SavannaMod.LOGGER.info("[crocodile] fastest in water {} blocks a tick, on land {}",
                                String.format(java.util.Locale.ROOT, "%.3f", fastest[0]), String.format(java.util.Locale.ROOT, "%.3f", fastest[1]));
                        stage[0] = 2;
                        croc.wantSun();
                    }
                }
                case 2 -> {
                    if (croc.isBasking() && !croc.isInWater() && croc.onGround()) {
                        stage[0] = 3;
                        hitAt[0] = now;
                        croc.hurt(level.damageSources().generic(), 1.0F);
                        helper.assertTrue(croc.startled > 0 && !croc.isBasking(), "hit while basking, the crocodile was not startled off the bank");
                    }
                }
                case 3 -> {
                    if (croc.isInWater()) {
                        SavannaMod.LOGGER.info("[crocodile] in the water {} ticks after it was hit", now - hitAt[0]);
                        stage[0] = 4;
                    } else if (now - hitAt[0] > 120) {
                        helper.fail("hit on the bank, the crocodile was not back in the water within 6 s: at "
                                + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
                    }
                }
                case 4 -> {
                    if (croc.startled == 0) {
                        stage[0] = 5;
                        croc.wantSunHalfOut();
                    } else if (croc.startled < 30 && croc.getNavigation().isDone() && croc.getEyeY() > croc.getY() + croc.getFluidHeight(net.minecraft.tags.FluidTags.WATER)) {
                        helper.fail("startled in the water, the crocodile did not sink out of sight: its eyes are above the surface");
                    }
                }
                default -> { }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.isAlive(), "the crocodile died");
            helper.assertTrue(stage[0] == 5, "the crocodile is at stage " + stage[0] + " of 5 (0: not basked yet, 2: not out to bask again,"
                    + " 3: not back in after being hit) at " + croc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            float yaw = croc.yBodyRot * net.minecraft.util.Mth.DEG_TO_RAD;
            BlockPos ahead = BlockPos.containing(croc.getX() - net.minecraft.util.Mth.sin(yaw) * 1.0, helper.absolutePos(BlockPos.ZERO).getY() + TOP,
                    croc.getZ() + net.minecraft.util.Mth.cos(yaw) * 1.0);
            helper.assertTrue(croc.isBasking() && croc.isInWater() && !level.getBlockState(ahead).isAir() && level.getFluidState(ahead).isEmpty(),
                    "the crocodile is not resting half out, in the water nose to a bank: basking " + croc.isBasking() + ", in water "
                            + croc.isInWater() + ", ahead " + level.getBlockState(ahead));
        });
    }

    /**
     * The hunt (2026-11-15): a hungry crocodile in the pond and a cow standing at the water's edge on the bank. The
     * first time the cow is made to slip free of the haul: it lives, and the crocodile is back in the water, not hunting. Then the
     * crocodile creeps up, strikes and grabs the cow, hauls it backward into the water (the cow is in the water before
     * the first roll, and the crocodile was moved toward the water while it held it) and death-rolls it until it dies, then is
     * fed and hunts no more. A chicken put at the edge after that is left alone (fed), and once hungry again it is
     * swallowed whole, with no roll.
     */
    /**
     * The crocodile hunts fish (its life step 4, 2026-11-15): a hungry crocodile in the pond with a school of wild
     * tilapia and one from a bucket strikes at the school (a snap to the side or a lunge), catches a wild one, holds it
     * and swallows it whole; its strike calls a second hungry crocodile to the school. Then, hungry again, it takes a
     * catfish, with no death roll: whips it from side to side, tears it in two and gulps the tail half, the front half
     * left as remains (2026-11-15) that sink to the bed, lose a look's stage a third of their bites at a time, and break
     * at a hit. The bucket fish is never taken.
     */
    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void aCrocodileSnapsUpFishAndCallsOthersToTheSchool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        helper.setDayTime(18000);                            // night: no wish to bask
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 2, SURFACE + 5, Z0 + 3));
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;        // no fish darts away: the test is about the catch, not the dice
        com.antcolonies.savanna.animal.CrocodileEntity other = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 2, SURFACE + 5, Z1 - 1));
        other.setPersistenceRequired();
        other.holdOffHunting(100000);
        java.util.List<TilapiaEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TilapiaEntity fish = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 1 - (i % 2), SURFACE + 3 + (i % 3), Z0 + 2 + i));
            fish.setUp(false, level.random);
            fish.setPersistenceRequired();
            school.add(fish);
        }
        TilapiaEntity pet = helper.spawn(SavannaEntities.TILAPIA.get(), new BlockPos(X1 - 2, SURFACE + 4, Z0 + 4));
        pet.setUp(false, level.random);
        pet.setFromBucket(true);
        // 0 hunting the school; 1 the catfish put in, hungry again; 2 done
        int[] stage = {0};
        boolean[] struck = {false}, heldFish = {false}, called = {false}, rolled = {false}, heldCatfish = {false}, shook = {false};
        com.antcolonies.savanna.animal.RemainsEntity[] left = {null};
        int[] tornAt = {0};
        CatfishEntity[] catfish = {null};
        helper.onEachTick(() -> {
            byte jaws = croc.jaws();
            struck[0] |= jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_LEFT
                    || jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SNAP_RIGHT
                    || jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_GAPE;
            called[0] |= other.calledToSchool();
            rolled[0] |= croc.rollAngle(0.0F) != 0.0F;
            helper.assertTrue(pet.isAlive() && !croc.holds(pet), "the crocodile took the bucket tilapia");
            switch (stage[0]) {
                case 0 -> {
                    heldFish[0] |= croc.held() instanceof TilapiaEntity;
                    if (school.stream().anyMatch(TilapiaEntity::isRemoved) && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(struck[0], "a tilapia was eaten with no strike seen");
                        helper.assertTrue(heldFish[0], "the tilapia was never held in the jaws before it was swallowed");
                        helper.assertTrue(called[0], "the strike at the school did not call the other crocodile");
                        helper.assertTrue(!rolled[0], "the tilapia was rolled, not swallowed");
                        SavannaMod.LOGGER.info("[crocodile] swallowed a tilapia at tick {}", helper.getTick());
                        school.forEach(f -> { if (!f.isRemoved()) f.discard(); });
                        catfish[0] = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 2, SURFACE + 3, Z0 + 4));
                        catfish[0].setPersistenceRequired();
                        catfish[0].setNoAi(true);
                        croc.makeHungry();
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    heldCatfish[0] |= croc.holds(catfish[0]);
                    shook[0] |= jaws == com.antcolonies.savanna.animal.CrocodileEntity.JAWS_SHAKE;
                    if (catfish[0].isRemoved() && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(heldCatfish[0], "the catfish was gone but never held in the jaws");
                        helper.assertTrue(shook[0], "the catfish was never shaken before it tore");
                        helper.assertTrue(!rolled[0], "the catfish was rolled, not torn");
                        var found = level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class,
                                croc.getBoundingBox().inflate(4.0));
                        helper.assertTrue(found.size() == 1, "the torn catfish left " + found.size() + " remains, not 1");
                        left[0] = found.get(0);
                        helper.assertTrue(left[0].kind() == com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH_TORN
                                && left[0].stage() == 1, "the remains are " + left[0].kind() + " at stage " + left[0].stage()
                                + ", not the torn catfish's front half, fresh");
                        SavannaMod.LOGGER.info("[crocodile] tore the catfish at tick {}", helper.getTick());
                        tornAt[0] = (int) helper.getTick();
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    com.antcolonies.savanna.animal.RemainsEntity r = left[0];
                    if (r.onGround()) {
                        helper.assertTrue(r.isInWater(), "the remains came to rest at " + r.getY() + ", not on the bed under the water");
                        int all = com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH_TORN.bites;
                        int[] stages = new int[all + 1];
                        for (int i = 0; i < all; i++) {
                            stages[i] = r.stage();
                            helper.assertTrue(r.bite(croc), "bite " + (i + 1) + " of " + all + " found no meat");
                        }
                        stages[all] = r.stage();
                        helper.assertTrue(!r.bite(croc), "a bite took meat from bare bones");
                        String seen = java.util.Arrays.toString(stages);
                        helper.assertTrue(seen.equals("[1, 1, 1, 2, 2, 2, 3, 3, 3, 4]"), "the looks went " + seen + " bite by bite");
                        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
                        r.hurt(level.damageSources().playerAttack(player), 1.0F);
                        level.getServer().getPlayerList().remove(player);   // a player left in the world changes spawning caps for later tests
                        helper.assertTrue(r.isRemoved(), "a player's hit did not break the bones");
                        stage[0] = 3;
                        helper.succeed();
                    } else {
                        helper.assertTrue(helper.getTick() - tornAt[0] < 200, "the remains never came to rest");
                    }
                }
                default -> { }
            }
        });
    }

    @GameTest(template = "arena", timeoutTicks = 4800)
    public static void aHungryCrocodileGrabsDrownsAndRollsItsPrey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        for (int x = X1 + 1; x <= X1 + 6; x++) {            // a bank beside the deep end, level with the pond's rim
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP + 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, TOP + 2, z), Blocks.AIR);
            }
        }
        helper.setDayTime(18000);                            // night: no wish to bask
        com.antcolonies.savanna.animal.CrocodileEntity croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(X0 + 3, SURFACE + 4, Z0 + 4));
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = -1;
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(X1 + 1, TOP + 1, Z0 + 4));
        cow.setNoAi(true);
        boolean[] missed = {false}, rolledYet = {false};
        // -1 (missed[0] false): the strike that misses; 0 stalking; 1 holding; 2 the cow dead, fed; 3 a chicken left alone while fed; 4 hungry again, the chicken swallowed
        int[] stage = {0};
        boolean[] rolled = {false}, rolledChicken = {false};
        long[] at = {0};
        net.minecraft.world.entity.animal.Chicken[] chicken = {null};
        helper.onEachTick(() -> {
            long now = helper.getTick();
            switch (stage[0]) {
                case 0 -> {
                    if (!missed[0]) {
                        helper.assertTrue(cow.isAlive(), "the cow that was to slip free died");
                        if (croc.misses > 0 && croc.isInWater() && now - at[0] > 100) {
                            helper.assertTrue(croc.held() == null && cow.getVehicle() == null, "the cow slipped free but is still in the crocodile's jaws");
                            SavannaMod.LOGGER.info("[crocodile] the cow slipped free and the crocodile was back in the water at tick {}", now);
                            missed[0] = true;
                            croc.strikeLuck = 1;
                            croc.makeHungry();
                        } else if (croc.misses == 0) {
                            at[0] = now;
                        }
                        break;
                    }
                    if (croc.holds(cow)) {
                        SavannaMod.LOGGER.info("[crocodile] grabbed the cow at tick {}, the crocodile in the water: {}, the cow: {}", now,
                                croc.isInWater(), cow.isInWater());
                        stage[0] = 1;
                    } else if (!cow.isAlive()) {
                        stage[0] = 2;       // killed by the bite itself
                    }
                }
                case 1 -> {
                    if (croc.rollAngle(0.0F) != 0.0F) {
                        rolled[0] = true;
                        helper.assertTrue(cow.getVehicle() == croc, "the cow is not in the crocodile's jaws while it rolls");
                        if (!rolledYet[0]) {
                            rolledYet[0] = true;
                            SavannaMod.LOGGER.info("[crocodile] the first roll at tick {}", now);
                            helper.assertTrue(cow.isInWater() && croc.isInWater(), "the cow was rolled before it was hauled into the water: the cow in water "
                                    + cow.isInWater() + ", the crocodile " + croc.isInWater());
                        }
                    }
                    if (!cow.isAlive()) {
                        SavannaMod.LOGGER.info("[crocodile] the cow died at tick {}", now);
                        stage[0] = 2;
                    }
                }
                case 2 -> {
                    if (croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(rolled[0], "the cow died without a death roll");
                        chicken[0] = helper.spawn(net.minecraft.world.entity.EntityType.CHICKEN, new BlockPos(X1 + 1, TOP + 1, Z0 + 6));
                        chicken[0].setNoAi(true);
                        at[0] = now;
                        stage[0] = 3;
                    }
                }
                case 3 -> {
                    helper.assertTrue(chicken[0].isAlive() && !croc.holds(chicken[0]), "a fed crocodile took the chicken");
                    if (now - at[0] > 200) {
                        croc.makeHungry();
                        stage[0] = 4;
                    }
                }
                case 4 -> {
                    if (croc.rollAngle(0.0F) != 0.0F) {
                        rolledChicken[0] = true;
                    }
                    if (chicken[0].isRemoved() && croc.held() == null && !croc.isHungry()) {
                        helper.assertTrue(!rolledChicken[0], "the chicken was rolled, not swallowed");
                        SavannaMod.LOGGER.info("[crocodile] swallowed the chicken at tick {}", now);
                        helper.succeed();
                    }
                }
                default -> { }
            }
        });
    }

    /**
     * The river crab (M9 river life, 2026-11-15): spawns on the bed of shallow savanna water only, in the river fish
     * group; hungry, it walks over the bed to the remains before the algae beside it and picks two bits off them into its
     * mouth (two bites), then, hungry again with the remains gone, picks two pairs of strands off the lush algae.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aHungryCrabPicksRemainsBeforeAlgae(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.CRAB.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the crab's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.CRAB.get());
        if (!listed) {
            helper.fail("the crab is not in the savanna's river bed spawn list");
        }
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP - 1, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (!com.antcolonies.savanna.animal.CrabEntity.canSpawn(SavannaEntities.CRAB.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a crab may not spawn on the shallow shelf");
            }
            if (com.antcolonies.savanna.animal.CrabEntity.canSpawn(SavannaEntities.CRAB.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a crab may spawn under 7 blocks of water");
            }
        }
        BlockPos algae = new BlockPos(X0 + 3, TOP - 1, Z0 + 2);
        helper.setBlock(algae, SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH)
                .setValue(AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH)));
        com.antcolonies.savanna.animal.RemainsEntity remains = com.antcolonies.savanna.animal.RemainsEntity.leave(level,
                com.antcolonies.savanna.animal.RemainsEntity.Kind.TILAPIA, helper.absolutePos(new BlockPos(X0 + 3, TOP - 1, Z1 - 1)).getBottomCenter(), 0, 1.0F);
        if (remains == null) {
            helper.fail("no remains made");
            return;
        }
        int meat = remains.bitesLeft();
        com.antcolonies.savanna.animal.CrabEntity crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 3));
        crab.setPersistenceRequired();
        crab.makeHungry();
        int strands = AlgaeBlock.fullStrands(AlgaeBlock.LUSH);
        boolean[] remainsDone = {false};
        boolean[] sawPick = {false, false};
        helper.onEachTick(() -> {
            byte doing = crab.doing();
            sawPick[0] |= doing == com.antcolonies.savanna.animal.CrabEntity.PICK_MEAT;
            sawPick[1] |= doing == com.antcolonies.savanna.animal.CrabEntity.PICK_ALGAE;
            if (!remainsDone[0] && crab.isFed()) {
                remainsDone[0] = true;
                if (meat - remains.bitesLeft() != 2) {
                    helper.fail("the crab took " + (meat - remains.bitesLeft()) + " bites off the remains (2 in one pick)");
                }
                if (helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) != strands) {
                    helper.fail("the crab picked at the algae before the remains");
                }
                remains.discard();
                crab.makeHungry();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(remainsDone[0] && sawPick[0], "the crab has not picked at the remains: at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            helper.assertTrue(sawPick[1] && crab.isFed(), "the crab has not picked at the algae: at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            helper.assertBlockProperty(algae, AlgaeBlock.STRANDS, strands - 2);
        });
    }

    /**
     * The river snail (M9 river life, 2026-11-16): spawns on the bed of shallow savanna water only, in the river fish
     * group; hungry, it creeps to the lush algae and rasps one bite (a strand) off it, then is fed; a crab coming
     * up beside it makes it pull into its shell, and once the crab is gone it peeks out again.
     */
    @GameTest(template = "arena", timeoutTicks = 2400)
    public static void aSnailGrazesAlgaeAndHidesFromACrab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        if (net.minecraft.world.entity.SpawnPlacements.getPlacementType(SavannaEntities.SNAIL.get())
                != net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER) {
            helper.fail("the snail's spawn placement is not signed up");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        boolean listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings()
                .getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed())
                .unwrap().stream().anyMatch(d -> d.type == SavannaEntities.SNAIL.get());
        if (!listed) {
            helper.fail("the snail is not in the savanna's river bed spawn list");
        }
        BlockPos shelf = helper.absolutePos(new BlockPos(X0 + 1, TOP - 1, Z0 + 1));
        BlockPos deep = helper.absolutePos(new BlockPos(X1 - 1, SURFACE, Z1 - 1));
        if (Math.abs(deep.getY() - level.getSeaLevel()) <= 6) {
            if (!com.antcolonies.savanna.animal.SnailEntity.canSpawn(SavannaEntities.SNAIL.get(), level, MobSpawnType.NATURAL, shelf, level.random)) {
                helper.fail("a snail may not spawn on the shallow shelf");
            }
            if (com.antcolonies.savanna.animal.SnailEntity.canSpawn(SavannaEntities.SNAIL.get(), level, MobSpawnType.NATURAL, deep, level.random)) {
                helper.fail("a snail may spawn under 7 blocks of water");
            }
        }
        BlockPos algae = new BlockPos(X0 + 3, TOP - 1, Z0 + 2);
        helper.setBlock(algae, SavannaBlocks.ALGAE.get().defaultBlockState().setValue(AlgaeBlock.GROWTH, AlgaeBlock.LUSH)
                .setValue(AlgaeBlock.STRANDS, AlgaeBlock.fullStrands(AlgaeBlock.LUSH)));
        int strands = AlgaeBlock.fullStrands(AlgaeBlock.LUSH);
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 2));
        snail.setPersistenceRequired();
        snail.makeHungry();
        // 0 grazing, 1 fed: crab sent, 2 hidden: crab gone, 3 peeked out
        int[] stage = {0};
        boolean[] sawGraze = {false};
        com.antcolonies.savanna.animal.CrabEntity[] crab = {null};
        long[] at = {0};
        helper.onEachTick(() -> {
            byte doing = snail.doing();
            sawGraze[0] |= doing == com.antcolonies.savanna.animal.SnailEntity.GRAZE;
            switch (stage[0]) {
                case 0 -> {
                    if (snail.isFed() && sawGraze[0]) {
                        if (helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) != strands - 1) {
                            helper.fail("the snail took " + (strands - helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS))
                                    + " strands off the algae (1 in one bite)");
                        }
                        BlockPos near = snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).east();
                        crab[0] = helper.spawn(SavannaEntities.CRAB.get(), near);
                        crab[0].setPersistenceRequired();
                        crab[0].setNoAi(true);
                        stage[0] = 1;
                    }
                }
                case 1 -> {
                    if (snail.isHidden()) {
                        crab[0].discard();
                        stage[0] = 2;
                        at[0] = helper.getTick();
                    }
                }
                case 2 -> {
                    if (doing == com.antcolonies.savanna.animal.SnailEntity.PEEK) {
                        if (helper.getTick() - at[0] < 90) {
                            helper.fail("the snail peeked out " + (helper.getTick() - at[0]) + " ticks after the crab left (5 to 10 s)");
                        }
                        stage[0] = 3;
                    }
                }
                default -> { }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(stage[0] == 3, "the snail got to stage " + stage[0]
                + " (0 grazing, 1 hiding from the crab, 2 peeking out) at "
                + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", doing " + snail.doing() + ", fed " + snail.isFed()
                + ", grazed " + sawGraze[0] + ", strands " + helper.getBlockState(algae).getValue(AlgaeBlock.STRANDS) + " of " + strands
                + ", path " + (snail.getNavigation().getPath() == null ? "none" : snail.getNavigation().getPath().getEndNode())));
    }

    /**
     * Crabs eat snails (M9 river life, 2026-11-16): a hungry crab with a snail near it and nothing else to eat walks to
     * it, grabs it (the snail is gone, the crack clip plays), and puts the broken shell down on the bed, holding water,
     * then is fed.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aCrabCracksASnailAndLeavesItsShell(GameTestHelper helper) {
        buildPond(helper);
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(X0 + 1, TOP - 1, Z0 + 2));
        snail.setPersistenceRequired();
        com.antcolonies.savanna.animal.CrabEntity crab = helper.spawn(SavannaEntities.CRAB.get(), new BlockPos(X0 + 4, TOP - 1, Z0 + 2));
        crab.setPersistenceRequired();
        crab.makeHungry();
        boolean[] cracked = {false};
        helper.onEachTick(() -> {
            if (crab.doing() == com.antcolonies.savanna.animal.CrabEntity.CRACK) {
                cracked[0] = true;
                if (snail.isAlive()) {
                    helper.fail("the crab cracks a shell while the snail is still there");
                }
            }
        });
        helper.succeedWhen(() -> {
            BlockPos shell = null;
            for (BlockPos at : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(X0, TOP - 3, Z0)), helper.absolutePos(new BlockPos(X1, TOP + 1, Z1)))) {
                if (helper.getLevel().getBlockState(at).is(SavannaBlocks.CRAB_SHELL.get())) {
                    shell = at.immutable();
                    break;
                }
            }
            helper.assertTrue(cracked[0] && shell != null && crab.isFed(), "cracked " + cracked[0] + ", shell " + shell
                    + ", crab fed " + crab.isFed() + ", snail alive " + snail.isAlive() + " at "
                    + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", crab at "
                    + crab.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + crab.doing());
            BlockState there = helper.getLevel().getBlockState(shell);
            helper.assertTrue(there.getValue(com.antcolonies.savanna.block.CrabShellBlock.WATERLOGGED)
                    && there.getValue(com.antcolonies.savanna.block.CrabShellBlock.AGE) == 0, "the shell is " + there + " (fresh, holding water)");
        });
    }

    // --- the bed dwellers spawning by themselves (2026-11-16) -------------------------------------------------------------
    private static final int[] CHANNEL_DEPTHS = {1, 2, 3, 4, 5, 6, 7, 7, 6, 5, 4, 3, 2, 1}; // a river's cross-section, bank to bank
    private static final int CX0 = 20, CZ0 = 20, CZ1 = 27;

    /**
     * A stretch of river over the arena, its water's top at the world's sea level (where the river animals' height rules
     * let them spawn; the arena itself lies far below): sloping beds from 1 block of water at the banks to 7 in the
     * channel. Built with absolute positions over the arena's own ground, and taken away again by {@link #clearChannel}.
     * Returns the water's top.
     */
    private static int buildChannel(GameTestHelper helper, boolean clear) {
        ServerLevel level = helper.getLevel();
        int top = level.getSeaLevel() - 1;
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        for (int i = -1; i <= CHANNEL_DEPTHS.length; i++) {
            for (int dz = -1; dz <= CZ1 - CZ0 + 1; dz++) {
                boolean wall = i < 0 || i >= CHANNEL_DEPTHS.length || dz < 0 || dz > CZ1 - CZ0;
                int depth = wall ? 0 : CHANNEL_DEPTHS[i];
                for (int y = top - 8; y <= top + 2; y++) {
                    BlockState state = clear ? Blocks.AIR.defaultBlockState()
                            : (y <= top - depth ? Blocks.STONE : y <= top ? Blocks.WATER : Blocks.AIR).defaultBlockState();
                    level.setBlock(new BlockPos(corner.getX() + i, y, corner.getZ() + dz), state, 2);
                }
            }
        }
        return top;
    }

    /**
     * Whether the world itself would spawn this kind here (every check Minecraft's natural spawner makes at a spot but the
     * biome's list and the distance to a player): the placement, the kind's own rule (with the quiet-savanna gate), room
     * for its box, then the mob's own last checks.
     */
    static boolean worldWouldSpawn(ServerLevel level, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, BlockPos at) {
        if (!net.minecraft.world.entity.SpawnPlacements.isSpawnPositionOk(type, level, at)
                || !net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, at, level.random)
                || !level.noCollision(type.getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
            return false;
        }
        net.minecraft.world.entity.Mob trial = type.create(level);
        if (trial == null) {
            return false;
        }
        trial.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boolean ok = net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(trial, level, MobSpawnType.NATURAL);
        trial.discard();
        return ok;
    }

    /**
     * Crabs and snails spawn by themselves on shallow river beds (the user's report 2026-11-16: never seen without an egg).
     * Minecraft's natural spawner draws one random height per try in a column and keeps it; a bed dweller on the old rule
     * passed only at the one height just over the bed. The test counts, over a river's cross-section, the spots each kind
     * passes every world check at (the share of the spawner's draws it can use), and spawns a crab and snails the world's
     * way to see them settle on the bed.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void crabsAndSnailsSpawnOnShallowBedsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var kinds = java.util.List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>of(
                SavannaEntities.TILAPIA.get(), SavannaEntities.CATFISH.get(), SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get());
        // 1. every kind's spots over the channel (the share of the spawner's draws it can use)
        int[] total = new int[kinds.size()];
        int[] crabs = new int[CHANNEL_DEPTHS.length], snails = new int[CHANNEL_DEPTHS.length];
        StringBuilder byDepth = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int[] column = new int[kinds.size()];
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    for (int k = 0; k < kinds.size(); k++) {
                        if (worldWouldSpawn(level, kinds.get(k), at)) {
                            column[k]++;
                            total[k]++;
                        }
                    }
                }
            }
            crabs[i] = column[2];
            snails[i] = column[3];
            if (i < CHANNEL_DEPTHS.length / 2) {
                byDepth.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": crab ").append(column[2]).append(" snail ").append(column[3]);
            }
        }
        SavannaMod.LOGGER.info("[bed spawns] spots the world would spawn at over the channel: tilapia {}, catfish {}, crab {}, snail {};{}",
                total[0], total[1], total[2], total[3], byDepth);
        // 2. the world's own spawn, drawn at the water's top over a shallow bed: set down on the bed
        int[][] trials = {{3, 4}, {1, 2}};             // {column, its depth}: the crab over 4 blocks of water, the snail over 2
        for (int t = 0; t < 2; t++) {
            var type = t == 0 ? SavannaEntities.CRAB.get() : SavannaEntities.SNAIL.get();
            BlockPos drawn = new BlockPos(corner.getX() + trials[t][0], top, corner.getZ() + 3);
            net.minecraft.world.entity.Mob mob = type.create(level);
            mob.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
            if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
                helper.fail("the world refuses a " + type.toShortString() + " at the water's top over a shallow bed");
            }
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
            int bed = top - trials[t][1] + 1;
            if (mob.getBlockY() != bed) {
                helper.fail("a " + type.toShortString() + " spawned by the world stays at y " + mob.getBlockY() + ", not on the bed at " + bed);
            }
            mob.discard();
        }
        buildChannel(helper, true);
        // 3. on the channel's spots: everywhere in the water of a shallow column, never over deeper water
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int want = CHANNEL_DEPTHS[i] <= com.antcolonies.savanna.animal.RiverBed.SHALLOW ? CHANNEL_DEPTHS[i] * (CZ1 - CZ0 + 1) : 0;
            if (crabs[i] != want || snails[i] != want) {
                wrong.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": crab ").append(crabs[i]).append(", snail ")
                        .append(snails[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("bed dwellers spawn at the wrong spots:" + wrong);
        }
        // 4. their own spawning group, listed in the savanna's waters (not the fish group, which the tilapia fill)
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var type : java.util.List.of(SavannaEntities.CRAB.get(), SavannaEntities.SNAIL.get())) {
            if (type.getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverBed()) {
                helper.fail(type.toShortString() + " is in the " + type.getCategory().getName() + " group, not the river bed group");
            }
            for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
                var settings = biomes.getOrThrow(key).getMobSettings();
                if (settings.getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBed()).unwrap().stream().noneMatch(d -> d.type == type)) {
                    helper.fail(type.toShortString() + " is not in " + key.location() + "'s river bed spawn list");
                }
                if (settings.getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverFish()).unwrap().stream().anyMatch(d -> d.type == type)) {
                    helper.fail(type.toShortString() + " is still in " + key.location() + "'s river fish spawn list");
                }
            }
        }
        helper.succeed();
    }

    // --- the Nile monitor (2026-11-17) ---------------------------------------------------------------------------------
    private static final int MX0 = 16, MX1 = 40, MZ0 = 34, MZ1 = 44;    // a stone bank to hunt on, clear of the pond and channel

    /**
     * A hungry Nile monitor goes to a snail, crushes it (the broken shell left where it was) and is fed; hungry again, it
     * takes bites off remains lying on the bank.
     */
    @GameTest(template = "arena", timeoutTicks = 1600)
    public static void aHungryMonitorCrushesASnailAndBitesRemains(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 3, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        monitor.makeHungry();
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 12, TOP + 1, MZ0 + 5));
        snail.setPersistenceRequired();
        BlockPos snailAt = snail.blockPosition();
        com.antcolonies.savanna.animal.RemainsEntity[] remains = {null};
        int[] meat = {0};
        boolean[] sawBite = {false};
        helper.onEachTick(() -> {
            sawBite[0] |= monitor.doing() == com.antcolonies.savanna.animal.MonitorEntity.BITE;
            if (remains[0] == null && !snail.isAlive() && monitor.isFed()) {
                remains[0] = com.antcolonies.savanna.animal.RemainsEntity.leave(level, com.antcolonies.savanna.animal.RemainsEntity.Kind.CATFISH,
                        helper.absolutePos(new BlockPos(MX1 - 4, TOP + 1, MZ0 + 5)).getBottomCenter(), 0, 1.0F);
                if (remains[0] == null) {
                    helper.fail("no remains made");
                    return;
                }
                meat[0] = remains[0].bitesLeft();
                monitor.makeHungry();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!snail.isAlive() && sawBite[0], "the monitor has not eaten the snail: monitor at "
                    + monitor.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + monitor.doing() + ", snail at "
                    + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
            BlockPos shell = null;
            for (BlockPos at : BlockPos.betweenClosed(snailAt.offset(-6, -1, -6), snailAt.offset(6, 2, 6))) {
                if (level.getBlockState(at).is(SavannaBlocks.CRAB_SHELL.get())) {
                    shell = at.immutable();
                    break;
                }
            }
            helper.assertTrue(shell != null, "the crushed snail left no shell");
            helper.assertTrue(remains[0] != null && meat[0] - remains[0].bitesLeft() >= 2 && monitor.isFed(), "the monitor has not fed on the remains: bites "
                    + (remains[0] == null ? "-" : meat[0] - remains[0].bitesLeft()) + ", monitor at "
                    + monitor.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + " doing " + monitor.doing());
        });
    }

    /**
     * A hungry monitor afloat dives for a snail on the bed under it (2026-11-19, the user's report of a monitor stuck in the
     * river: it hung at the surface over a snail 3 blocks down, its jaws never reaching, and picked the same snail again
     * every time it gave up). The report's scene: water 4 deep, the monitor afloat, the snail on the bed 2 blocks across.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aHungryMonitorDivesForASnailOnTheBed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int bed = TOP - 4;
        for (int x = MX0; x <= MX0 + 12; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean wall = x == MX0 || x == MX0 + 12 || z == MZ0 || z == MZ1;
                helper.setBlock(new BlockPos(x, bed, z), Blocks.STONE);
                for (int y = bed + 1; y <= TOP + 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), wall && y <= TOP + 1 ? Blocks.STONE : y <= TOP ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.SnailEntity snail = helper.spawn(SavannaEntities.SNAIL.get(), new BlockPos(MX0 + 5, bed + 1, MZ0 + 5));
        snail.setPersistenceRequired();
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(),
                new net.minecraft.world.phys.Vec3(MX0 + 7.5, TOP + 0.4, MZ0 + 5.5));
        monitor.setPersistenceRequired();
        monitor.makeHungry();
        helper.succeedWhen(() -> helper.assertTrue(!snail.isAlive() && monitor.isFed(), "the monitor has not dived for the snail: monitor at "
                + String.format("%.2f", monitor.getY() - helper.absolutePos(BlockPos.ZERO).getY()) + " up, in water " + monitor.isInWater()
                + ", doing " + monitor.doing() + ", snail at " + snail.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))));
    }

    /**
     * A Nile monitor watches a survival player (2026-11-17, the user's ask): 11 blocks off it stops and faces them; when
     * they step back to 13.5 it trails them back toward 6; when they walk right up to its jaws it hisses, then lunges and
     * bites ({@code bite_player}).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aMonitorWatchesTrailsAndBitesAPlayer(GameTestHelper helper) {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        com.antcolonies.savanna.animal.MonitorEntity monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 2, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        monitor.saveWithoutId(tag);
        tag.putInt("SunIn", 99999);                      // no basking, no hunting: only the player
        tag.putInt("HungryIn", 99999);
        monitor.load(tag);
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        double z = monitor.getZ(), y = monitor.getY();
        player.moveTo(monitor.getX() + 11.0, y, z, 90.0F, 0.0F);
        int[] stage = {0}, at = {0};
        double[] startX = {0};
        boolean[] bit = {false};
        helper.onEachTick(() -> {
            long t = helper.getTick();
            byte doing = monitor.doing();
            bit[0] |= doing == com.antcolonies.savanna.animal.MonitorEntity.BITE_PLAYER;
            if (stage[0] == 0 && doing == com.antcolonies.savanna.animal.MonitorEntity.WATCH) {
                stage[0] = 1;
                at[0] = (int) t;
                startX[0] = monitor.getX();
                player.moveTo(monitor.getX() + 13.5, y, z, 90.0F, 0.0F);
            } else if (stage[0] == 0 && t > 300) {
                helper.fail("the monitor never watched the player: doing " + doing + ", player " + String.format("%.1f", player.distanceTo(monitor)) + " away, survival " + net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player) + ", on ground " + monitor.onGround() + ", in water " + monitor.isInWater() + ", players " + helper.getLevel().players().size());
            } else if (stage[0] == 1 && monitor.getX() - startX[0] > 3.0) {
                stage[0] = 2;
                at[0] = (int) t;
                player.moveTo(monitor.getX() + 2.8, y, monitor.getZ(), 90.0F, 0.0F);
            } else if (stage[0] == 1 && t - at[0] > 200) {
                helper.fail("the monitor did not trail the player: moved " + String.format("%.1f", monitor.getX() - startX[0])
                        + " blocks, doing " + doing + ", player " + String.format("%.1f", player.distanceTo(monitor)) + " away");
            } else if (stage[0] == 2 && bit[0]) {
                helper.succeed();
            } else if (stage[0] == 2 && t - at[0] > 200) {
                helper.fail("the monitor did not bite the player at its jaws: doing " + doing);
            }
        });
    }

    /**
     * Monitors spawn by themselves (the spawning check list): the world's own checks over a river at sea level pass in
     * the water within {@code BANK_REACH} blocks of a bank and nowhere farther out, a spawn is set down on the bank, none
     * comes within {@code SPAWN_APART} blocks of another, and the kind is in its own group's list in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void monitorsSpawnInTheWaterByBanksByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int reach = com.antcolonies.savanna.animal.MonitorEntity.BANK_REACH;
        var monitorType = SavannaEntities.MONITOR.get();
        // 1. its spots over the channel: only in the water near a bank (and the others' for comparison)
        int spots = 0, crocs = 0, crabs = 0;
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                int fromBank = Math.min(Math.min(i + 1, CHANNEL_DEPTHS.length - i), Math.min(dz + 1, CZ1 - CZ0 + 1 - dz));
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    if (worldWouldSpawn(level, monitorType, at)) {
                        spots++;
                        if (fromBank > reach) {
                            wrong.append(" ").append(i).append(",").append(dz).append(" (").append(fromBank).append(" from the bank)");
                        }
                    }
                    crocs += worldWouldSpawn(level, SavannaEntities.CROCODILE.get(), at) ? 1 : 0;
                    crabs += worldWouldSpawn(level, SavannaEntities.CRAB.get(), at) ? 1 : 0;
                }
            }
        }
        SavannaMod.LOGGER.info("[bank spawns] spots the world would spawn at over the channel: monitor {}, crocodile {}, crab {}", spots, crocs, crabs);
        if (spots == 0) {
            helper.fail("the world would spawn a monitor nowhere over the river");
        }
        if (!wrong.isEmpty()) {
            helper.fail("monitors would spawn too far from a bank:" + wrong);
        }
        // 2. the world's own spawn in the water by the bank: set down on the bank
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        net.minecraft.world.entity.Mob mob = monitorType.create(level);
        mob.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        if (!net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(mob, level, MobSpawnType.NATURAL)) {
            helper.fail("the world refuses a monitor in the water by a bank");
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        BlockPos stood = mob.blockPosition();
        if (stood.getY() != top + 1 || !level.getFluidState(stood.below()).isEmpty() || !level.getBlockState(stood.below()).isSolid()) {
            helper.fail("a monitor spawned by the world stands at " + stood.subtract(corner) + ", not on the bank (y " + (top + 1) + ")");
        }
        // 3. none within SPAWN_APART of another
        level.addFreshEntity(mob);
        BlockPos other = new BlockPos(corner.getX() + CHANNEL_DEPTHS.length - 2, top - 1, corner.getZ() + 3);
        if (worldWouldSpawn(level, monitorType, other)) {
            helper.fail("a monitor would spawn " + (int) Math.sqrt(other.distSqr(stood)) + " blocks from another");
        }
        mob.discard();
        buildChannel(helper, true);
        // 4. its own spawning group, listed in the savanna's waters
        if (monitorType.getCategory() != com.antcolonies.savanna.registry.SavannaMobCategories.riverBank()) {
            helper.fail("the monitor is in the " + monitorType.getCategory().getName() + " group, not the river bank group");
        }
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
            if (biomes.getOrThrow(key).getMobSettings().getMobs(com.antcolonies.savanna.registry.SavannaMobCategories.riverBank())
                    .unwrap().stream().noneMatch(d -> d.type == monitorType)) {
                helper.fail("the monitor is not in " + key.location() + "'s river bank spawn list");
            }
        }
        helper.succeed();
    }

    // --- the river barb (2026-11-20) -----------------------------------------------------------------------------------

    /**
     * Barbs spawn by themselves in the shallows (the spawning check list): over a river's cross-section the world would
     * spawn a barb at every water spot of a column 1 to 3 blocks deep and nowhere deeper; barbs are in their own group,
     * river schools (cap 40), listed in the savanna's waters in schools of 8 to 14; the river fish group is down to 45
     * and the tilapia come in groups of 2 to 5 (the user, 2026-11-20).
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void barbsSpawnInTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        int[] barbs = new int[CHANNEL_DEPTHS.length];
        int tilapia = 0;
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    BlockPos at = new BlockPos(corner.getX() + i, y, corner.getZ() + dz);
                    barbs[i] += worldWouldSpawn(level, SavannaEntities.BARB.get(), at) ? 1 : 0;
                    tilapia += worldWouldSpawn(level, SavannaEntities.TILAPIA.get(), at) ? 1 : 0;
                }
            }
        }
        buildChannel(helper, true);
        int total = java.util.Arrays.stream(barbs).sum();
        SavannaMod.LOGGER.info("[barb spawns] spots over the channel: barb {} (by column {}), tilapia {}", total, java.util.Arrays.toString(barbs), tilapia);
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            int want = CHANNEL_DEPTHS[i] <= 3 ? CHANNEL_DEPTHS[i] * (CZ1 - CZ0 + 1) : 0;
            if (barbs[i] != want) {
                wrong.append(" depth ").append(CHANNEL_DEPTHS[i]).append(": ").append(barbs[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("barbs spawn at the wrong spots:" + wrong);
        }
        var school = com.antcolonies.savanna.registry.SavannaMobCategories.riverSchool();
        var fish = com.antcolonies.savanna.registry.SavannaMobCategories.riverFish();
        helper.assertTrue(SavannaEntities.BARB.get().getCategory() == school, "the barb is in the " + SavannaEntities.BARB.get().getCategory().getName() + " group");
        helper.assertTrue(school.getMaxInstancesPerChunk() == 40, "the river school group's cap is " + school.getMaxInstancesPerChunk() + " (40 wanted)");
        helper.assertTrue(fish.getMaxInstancesPerChunk() == 45, "the river fish group's cap is " + fish.getMaxInstancesPerChunk() + " (45 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var river = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SavannaMod.MODID, "savanna_river"));
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.SAVANNA, river)) {
            var settings = biomes.getOrThrow(key).getMobSettings();
            var barb = settings.getMobs(school).unwrap().stream().filter(d -> d.type == SavannaEntities.BARB.get()).findFirst();
            helper.assertTrue(barb.isPresent() && barb.get().minCount == 8 && barb.get().maxCount == 14,
                    "barbs in " + key.location() + "'s river school list: " + barb.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (8 to 14 wanted)");
            var t = settings.getMobs(fish).unwrap().stream().filter(d -> d.type == SavannaEntities.TILAPIA.get()).findFirst();
            helper.assertTrue(t.isPresent() && t.get().minCount == 2 && t.get().maxCount == 5,
                    "tilapia in " + key.location() + "'s river fish list: " + t.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (2 to 5 wanted)");
        }
        helper.succeed();
    }

    /**
     * A hungry hatchling crocodile in the shallows snaps up a barb passing it (young crocodiles' first meal, 2026-11-20)
     * and is fed; a barb is not frightened of a hatchling.
     */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aHatchlingCrocodileSnapsUpABarb(GameTestHelper helper) {
        buildPond(helper);
        var croc = helper.spawn(SavannaEntities.CROCODILE.get(), new BlockPos(3, TOP - 1, 5));
        croc.life().setUp(true, croc.getRandom());
        croc.life().setAge(0);
        croc.load(croc.saveWithoutId(new net.minecraft.nbt.CompoundTag()));       // its size and pace as it loads
        croc.setPersistenceRequired();
        croc.makeHungry();
        croc.strikeLuck = 1;        // the barb never darts off at the snap (the hunt is under test, not the dodge)
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(5, TOP - 1, 5));
        barb.setNoAi(true);
        barb.setPersistenceRequired();
        helper.succeedWhen(() -> {
            helper.assertTrue(croc.isAlive(), "the hatchling died");
            helper.assertTrue(croc.life().isYoung(), "the crocodile is not a hatchling");
            helper.assertTrue(barb.isRemoved() && !croc.isHungry(),
                    "the barb " + (barb.isRemoved() ? "is gone" : "still swims " + String.format("%.1f", barb.distanceTo(croc)) + " blocks off")
                            + (croc.isHungry() ? ", the hatchling still hungry" : ""));
        });
    }

    /**
     * A hungry catfish takes a barb out of a school (a snack, 2026-11-20): one barb is gone, the catfish is fed, and at
     * the strike the others burst outward with their fast start.
     */
    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void aCatfishTakesABarbAndTheSchoolBursts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        CatfishEntity catfish = helper.spawn(SavannaEntities.CATFISH.get(), new BlockPos(X1 - 1, SURFACE + 2, Z0 + 1));
        catfish.setPersistenceRequired();
        catfish.makeHungry();
        java.util.List<BarbEntity> school = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(X1 - 2 - i % 2, SURFACE + 3 + i / 3, Z1 - 1 - i % 3));
            barb.setPersistenceRequired();
            school.add(barb);
        }
        double[] fastest = {0};
        helper.onEachTick(() -> {
            for (BarbEntity b : school) {
                if (!b.isRemoved()) {
                    fastest[0] = Math.max(fastest[0], b.getDeltaMovement().length());
                }
            }
        });
        helper.succeedWhen(() -> {
            long gone = school.stream().filter(BarbEntity::isRemoved).count();
            helper.assertTrue(gone == 1 && !catfish.isHungry() && catfish.isAlive(),
                    gone + " barbs gone" + (catfish.isHungry() ? ", the catfish still hungry" : "") + " (one eaten, the catfish fed)");
            helper.assertTrue(fastest[0] > 0.35, "the school never burst at the strike (fastest barb " + String.format("%.2f", fastest[0]) + " blocks a tick)");
            helper.assertTrue(level.getEntitiesOfClass(com.antcolonies.savanna.animal.RemainsEntity.class, catfish.getBoundingBox().inflate(6)).isEmpty(),
                    "a barb left remains (it is gulped whole)");
        });
    }

    // --- the fly clouds (2026-11-20) -----------------------------------------------------------------------------------

    /**
     * Fly clouds spawn by themselves over the shallows and the banks beside them (the spawning check list): over a
     * river's cross-section the world would spawn one at every spot of a column within 2 blocks of water 1 to 3 deep
     * (the water there or the free block over the top), nowhere over the deep middle; a cloud spawned the world's way is
     * set up to hang 1.7 to 3 blocks over the water with 4 to 12 flies; its own group (cap 6) in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void flyCloudsSpawnOverTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var type = SavannaEntities.FLY_CLOUD.get();
        int[] spots = new int[CHANNEL_DEPTHS.length];
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    spots[i] += worldWouldSpawn(level, type, new BlockPos(corner.getX() + i, y, corner.getZ() + dz)) ? 1 : 0;
                }
            }
        }
        // the world's own spawn, drawn in the water over a shallow bed: set up over the water, full of flies
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        FlyCloudEntity cloud = type.create(level);
        cloud.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
        helper.assertTrue(net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(cloud, level, MobSpawnType.NATURAL),
                "the world refuses a fly cloud in the water over a shallow bed");
        cloud.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
        double over = cloud.getY() - (top + 1);
        int flies = cloud.flies();
        cloud.discard();
        buildChannel(helper, true);
        SavannaMod.LOGGER.info("[fly clouds] spots over the channel by column {}; a new cloud {} blocks over the water with {} flies",
                java.util.Arrays.toString(spots), String.format("%.2f", over), flies);
        helper.assertTrue(over >= 1.65 && over <= 3.05, "a new cloud hangs " + over + " blocks over the water (1.7 to 3 wanted)");
        helper.assertTrue(flies >= 4 && flies <= 12, "a new cloud holds " + flies + " flies (4 to 12 wanted)");
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            boolean nearShallow = false;
            for (int j = Math.max(0, i - 2); j <= Math.min(CHANNEL_DEPTHS.length - 1, i + 2); j++) {
                nearShallow |= CHANNEL_DEPTHS[j] <= 3;
            }
            int want = nearShallow ? (CHANNEL_DEPTHS[i] + 1) * (CZ1 - CZ0 + 1) : 0;
            if (spots[i] != want) {
                wrong.append(" column ").append(i).append(" (depth ").append(CHANNEL_DEPTHS[i]).append("): ").append(spots[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("fly clouds spawn at the wrong spots:" + wrong);
        }
        var group = com.antcolonies.savanna.registry.SavannaMobCategories.flyCloud();
        helper.assertTrue(type.getCategory() == group && group.getMaxInstancesPerChunk() == 6,
                "the fly cloud is in the " + type.getCategory().getName() + " group, cap " + type.getCategory().getMaxInstancesPerChunk() + " (its own, 6 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        helper.assertTrue(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings().getMobs(group).unwrap().stream()
                .anyMatch(d -> d.type == type), "the fly cloud is not in the savanna's fly cloud spawn list");
        helper.succeed();
    }

    /**
     * A fly cloud hangs over the water between about 1.7 and 3 blocks up, wanders slowly round its spot, and cannot be
     * hurt or pushed (only /kill removes it).
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aFlyCloudHangsOverTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildPond(helper);
        FlyCloudEntity cloud = helper.spawn(SavannaEntities.FLY_CLOUD.get(), new BlockPos(4, TOP + 1, 5));
        double water = helper.absolutePos(new BlockPos(0, TOP + 1, 0)).getY();     // the pond's top (the free block over it)
        double[] low = {99}, high = {-99}, farthest = {0};
        net.minecraft.world.phys.Vec3[] home = {null};
        helper.onEachTick(() -> {
            if (cloud.tickCount < 120) {
                return;     // rising to its height first
            }
            double over = cloud.getY() - water;
            low[0] = Math.min(low[0], over);
            high[0] = Math.max(high[0], over);
            if (home[0] == null) {
                home[0] = cloud.home();
            }
            farthest[0] = Math.max(farthest[0], Math.hypot(cloud.getX() - home[0].x, cloud.getZ() - home[0].z));
        });
        helper.runAtTickTime(380, () -> {
            boolean hurt = cloud.hurt(level.damageSources().generic(), 5.0F);
            helper.assertTrue(!hurt && cloud.isAlive() && !cloud.isPickable() && !cloud.isPushable(), "the fly cloud can be hurt, hit or pushed");
            helper.assertTrue(low[0] >= 1.6 && high[0] <= 3.1, String.format("the cloud hung %.2f to %.2f blocks over the water (1.7 to 3 wanted)", low[0], high[0]));
            helper.assertTrue(farthest[0] <= 2.8, String.format("the cloud wandered %.2f blocks from its spot (2.5 at most)", farthest[0]));
            SavannaMod.LOGGER.info("[fly clouds] hung {} to {} over the water, wandered {} from its spot",
                    String.format("%.2f", low[0]), String.format("%.2f", high[0]), String.format("%.2f", farthest[0]));
            helper.succeed();
        });
    }

    // --- the African bullfrog (2026-11-20) -----------------------------------------------------------------------------

    /** A stone bank (the monitor tests' ground) with a pool one block deep in it; returns nothing, the pool at x 6..9, z 3..7. */
    private static void bankWithPool(GameTestHelper helper) {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean pool = x >= MX0 + 6 && x <= MX0 + 9 && z >= MZ0 + 3 && z <= MZ0 + 7;
                helper.setBlock(new BlockPos(x, TOP - 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, TOP, z), pool ? Blocks.WATER : Blocks.STONE);
                for (int y = TOP + 1; y <= TOP + 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static BullfrogEntity frogOnBank(GameTestHelper helper, int x, int z, float yaw) {
        BullfrogEntity frog = helper.spawn(SavannaEntities.BULLFROG.get(), new BlockPos(x, TOP + 1, z));
        frog.setPersistenceRequired();
        frog.setYRot(yaw);
        frog.yBodyRot = yaw;
        frog.yHeadRot = yaw;
        return frog;
    }

    /**
     * Bullfrogs spawn by themselves at the shallows' edges (the spawning check list): over a river's cross-section the
     * world would spawn one at every spot of a column within 2 blocks of water 1 to 3 deep (the water there or the free
     * block over the top), never over the deep middle; one spawned the world's way is set down afloat on the water, a
     * male 0.8 blocks long or a female 0.65 (each 0.9 to 1.1 of that); its own group (cap 12) in the savanna's waters.
     */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void bullfrogsSpawnAtTheShallowsByThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int top = buildChannel(helper, false);
        BlockPos corner = helper.absolutePos(new BlockPos(CX0, 0, CZ0));
        var type = SavannaEntities.BULLFROG.get();
        int[] spots = new int[CHANNEL_DEPTHS.length];
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            for (int dz = 0; dz <= CZ1 - CZ0; dz++) {
                for (int y = top - 8; y <= top + 1; y++) {
                    spots[i] += worldWouldSpawn(level, type, new BlockPos(corner.getX() + i, y, corner.getZ() + dz)) ? 1 : 0;
                }
            }
        }
        BlockPos drawn = new BlockPos(corner.getX() + 1, top - 1, corner.getZ() + 3);
        StringBuilder sizes = new StringBuilder();
        for (int n = 0; n < 6; n++) {
            BullfrogEntity frog = type.create(level);
            frog.moveTo(drawn.getX() + 0.5, drawn.getY(), drawn.getZ() + 0.5, 0, 0);
            helper.assertTrue(net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(frog, level, MobSpawnType.NATURAL),
                    "the world refuses a bullfrog in the water over a shallow bed");
            frog.finalizeSpawn(level, level.getCurrentDifficultyAt(drawn), MobSpawnType.NATURAL, null);
            double length = 0.8 * frog.getScale(), usual = frog.isMale() ? 0.8 : 0.65;
            sizes.append(frog.isMale() ? " male " : " female ").append(String.format("%.2f", length));
            helper.assertTrue(length > usual * 0.89 && length < usual * 1.11, "a " + (frog.isMale() ? "male" : "female") + " " + length + " blocks long");
            helper.assertTrue(Math.abs(frog.getY() - (top + 1 - 0.4)) < 0.01, "a new bullfrog set at y " + frog.getY() + ", not afloat on the water");
            frog.discard();
        }
        buildChannel(helper, true);
        SavannaMod.LOGGER.info("[bullfrog] spots over the channel by column {}; new frogs:{}", java.util.Arrays.toString(spots), sizes);
        StringBuilder wrong = new StringBuilder();
        for (int i = 0; i < CHANNEL_DEPTHS.length; i++) {
            boolean nearShallow = false;
            for (int j = Math.max(0, i - 2); j <= Math.min(CHANNEL_DEPTHS.length - 1, i + 2); j++) {
                nearShallow |= CHANNEL_DEPTHS[j] <= 3;
            }
            int want = nearShallow ? (CHANNEL_DEPTHS[i] + 1) * (CZ1 - CZ0 + 1) : 0;
            if (spots[i] != want) {
                wrong.append(" column ").append(i).append(" (depth ").append(CHANNEL_DEPTHS[i]).append("): ").append(spots[i]).append(", want ").append(want);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("bullfrogs spawn at the wrong spots:" + wrong);
        }
        var group = com.antcolonies.savanna.registry.SavannaMobCategories.bullfrog();
        helper.assertTrue(type.getCategory() == group && group.getMaxInstancesPerChunk() == 12,
                "the bullfrog is in the " + type.getCategory().getName() + " group, cap " + type.getCategory().getMaxInstancesPerChunk() + " (its own, 12 wanted)");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var listed = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA).getMobSettings().getMobs(group).unwrap().stream()
                .filter(d -> d.type == type).findFirst();
        helper.assertTrue(listed.isPresent() && listed.get().minCount == 1 && listed.get().maxCount == 3,
                "bullfrogs in the savanna's list: " + listed.map(d -> d.minCount + " to " + d.maxCount).orElse("none") + " (1 to 3 wanted)");
        helper.succeed();
    }

    /** A hungry bullfrog at the water's edge lunges at a barb in the water in front of it and swallows it. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aHungryBullfrogLungesAtABarb(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 5, MZ0 + 5, -90);
        frog.makeHungry();
        BarbEntity barb = helper.spawn(SavannaEntities.BARB.get(), new BlockPos(MX0 + 6, TOP, MZ0 + 5));
        barb.setNoAi(true);
        barb.setPersistenceRequired();
        boolean[] lunged = {false};
        helper.onEachTick(() -> lunged[0] |= frog.action() == BullfrogEntity.LUNGE);
        helper.succeedWhen(() -> {
            helper.assertTrue(lunged[0], "the bullfrog never lunged (barb " + String.format("%.2f", barb.distanceTo(frog)) + " blocks off)");
            helper.assertTrue(barb.isRemoved() && !frog.isHungry(), "the barb " + (barb.isRemoved() ? "is gone" : "still swims")
                    + (frog.isHungry() ? ", the frog still hungry" : ""));
        });
    }

    /**
     * The long tongue (the user's 2 blocks): a hungry bullfrog under a fly cloud hanging within reach flicks at it and
     * eats flies; another under a cloud hanging 3 blocks up (out of reach) never flicks.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aBullfrogTonguesFliesOnlyFromACloudInReach(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity near = frogOnBank(helper, MX0 + 2, MZ0 + 2, -90);
        BullfrogEntity far = frogOnBank(helper, MX0 + 14, MZ0 + 8, -90);
        near.makeHungry();
        far.makeHungry();
        FlyCloudEntity low = cloudAt(helper, near.position().add(1.0, 1.9, 0));
        FlyCloudEntity high = cloudAt(helper, far.position().add(0.6, 3.5, 0));
        boolean[] flicked = {false, false};
        helper.onEachTick(() -> {
            flicked[0] |= near.action() == BullfrogEntity.TONGUE;
            flicked[1] |= far.action() == BullfrogEntity.TONGUE;
        });
        helper.runAtTickTime(380, () -> {
            helper.assertTrue(flicked[0] && low.flies() < 4, "the frog under the low cloud " + (flicked[0] ? "flicked" : "never flicked")
                    + ", its cloud holds " + low.flies() + " of 4 flies");
            helper.assertTrue(!flicked[1] && high.flies() == 4, "the frog under the cloud 3 blocks up flicked at it (out of reach)");
            SavannaMod.LOGGER.info("[bullfrog] the frog under the low cloud ate {} flies", 4 - low.flies());
            helper.succeed();
        });
    }

    private static FlyCloudEntity cloudAt(GameTestHelper helper, net.minecraft.world.phys.Vec3 at) {
        FlyCloudEntity cloud = SavannaEntities.FLY_CLOUD.get().create(helper.getLevel());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        cloud.saveWithoutId(tag);
        tag.putBoolean("NoAI", true);
        tag.putInt("Flies", 4);
        tag.putInt("Most", 4);
        tag.putInt("Refill", 99999);
        cloud.load(tag);
        cloud.moveTo(at.x, at.y, at.z, 0, 0);
        cloud.setPersistenceRequired();
        helper.getLevel().addFreshEntity(cloud);
        return cloud;
    }

    /** Bold, not shy: a player standing close makes a bullfrog puff up, then lunge and bite (1 heart). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aBullfrogPuffsUpAndBitesAPlayerWhoStaysClose(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 3, MZ0 + 8, -90);
        frog.makeHungry();
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        boolean[] puffed = {false};
        helper.onEachTick(() -> {
            player.moveTo(frog.getX() + 1.2, frog.getY(), frog.getZ(), 90.0F, 0.0F);
            puffed[0] |= frog.action() == BullfrogEntity.PUFF;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(puffed[0], "the bullfrog never puffed up at the player");
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "the bullfrog never bit the player");
            helper.assertTrue(player.getMaxHealth() - player.getHealth() <= 2.01F, "the bite took " + (player.getMaxHealth() - player.getHealth()) + " health (1 heart wanted)");
        });
    }

    /** A Nile monitor coming at a bullfrog on the bank makes it leap into the water (Minecraft's long-jump aim). */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aBullfrogLeapsIntoTheWaterFromAMonitor(GameTestHelper helper) {
        bankWithPool(helper);
        BullfrogEntity frog = frogOnBank(helper, MX0 + 4, MZ0 + 5, -90);
        frog.ateMeal();
        var monitor = helper.spawn(SavannaEntities.MONITOR.get(), new BlockPos(MX0 + 1, TOP + 1, MZ0 + 5));
        monitor.setPersistenceRequired();
        boolean[] leapt = {false}, wet = {false};
        helper.onEachTick(() -> {
            leapt[0] |= frog.action() == BullfrogEntity.LEAP;
            wet[0] |= leapt[0] && frog.isInWater();     // it may flee on out of so small a pool afterwards
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(leapt[0], "the bullfrog never leapt (monitor " + String.format("%.1f", monitor.distanceTo(frog)) + " blocks off)");
            helper.assertTrue(wet[0], "the bullfrog leapt but never came down in the water (at " + frog.blockPosition() + ")");
        });
    }

    /**
     * At midday a bullfrog on soft ground by the water digs in backward (only its back and eyes out) and stays dug; a
     * player coming within 3 blocks brings it out, and once its digging-out clip is over it is a free frog again (a bug
     * found while building: the digging out never ended, 2026-11-20).
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aBullfrogDigsInAtMiddayAndComesOutForAPlayer(GameTestHelper helper) {
        bankWithPool(helper);
        com.antcolonies.gametest.TestClock.still(helper, 12.0);
        for (int x = MX0; x <= MX0 + 5; x++) {
            for (int z = MZ0 + 2; z <= MZ0 + 8; z++) {
                helper.setBlock(new BlockPos(x, TOP, z), Blocks.DIRT);
            }
        }
        BullfrogEntity frog = frogOnBank(helper, MX0 + 4, MZ0 + 5, 90);
        frog.ateMeal();
        net.minecraft.server.level.ServerPlayer player = MatabeleDefenceTests.survivalPlayer(helper);
        player.moveTo(frog.getX() + 9, frog.getY(), frog.getZ() + 6);
        int[] dugAt = {-1}, outAt = {-1};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (dugAt[0] < 0 && frog.action() == BullfrogEntity.DUG) {
                dugAt[0] = (int) tick;
            }
            if (dugAt[0] >= 0 && tick < dugAt[0] + 60) {
                player.moveTo(frog.getX() + 9, frog.getY(), frog.getZ() + 6);       // far off a while: it stays dug
                helper.assertTrue(frog.isDug(), "the bullfrog came out of the ground with nobody near");
            } else if (dugAt[0] >= 0) {
                player.moveTo(frog.getX() + 1.5, frog.getY(), frog.getZ(), 90.0F, 0.0F);
                if (outAt[0] < 0 && !frog.isDug()) {
                    outAt[0] = (int) tick;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(dugAt[0] >= 0, "the bullfrog never dug in at midday on soft ground (action " + frog.action() + ")");
            helper.assertTrue(outAt[0] >= 0, "the bullfrog never finished coming out of the ground for a close player (action " + frog.action() + ")");
            SavannaMod.LOGGER.info("[bullfrog] dug in at tick {}, out at {}", dugAt[0], outAt[0]);
        });
    }
}
